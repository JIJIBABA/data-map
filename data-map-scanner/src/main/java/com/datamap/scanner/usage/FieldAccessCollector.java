package com.datamap.scanner.usage;

import com.datamap.scanner.javac.AnalysisContext;
import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.IdentifierTree;
import com.sun.source.tree.MemberSelectTree;
import com.sun.source.tree.MethodInvocationTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.Tree;
import com.sun.source.util.TreePath;
import com.sun.source.util.TreePathScanner;

import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;
import java.util.*;

public class FieldAccessCollector {
    public static Map<String, List<FieldAccess>> collect(AnalysisContext ctx, Map<String, TypeElement> entities) {
        // 实体 TypeElement -> 表名 的反向索引，供接收者类型推断使用
        Map<TypeElement, String> entityToTable = new HashMap<>();
        for (Map.Entry<String, TypeElement> e : entities.entrySet()) {
            entityToTable.put(e.getValue(), e.getKey());
        }

        Map<String, List<FieldAccess>> result = new HashMap<>();
        for (CompilationUnitTree cu : ctx.units) {
            new TreePathScanner<Void, Void>() {
                @Override public Void visitMethodInvocation(MethodInvocationTree node, Void p) {
                    Element e = ctx.resolve(cu, node);
                    if (e instanceof ExecutableElement) {
                        ExecutableElement method = (ExecutableElement) e;
                        String name = method.getSimpleName().toString();
                        if (name.startsWith("set") && name.length() > 3 && method.getParameters().size() == 1) {
                            recordByMethod(ctx, cu, node, method, name.substring(3), FieldAccess.Kind.WRITE, result, entities);
                        } else if (name.startsWith("get") && name.length() > 3 && method.getParameters().isEmpty()) {
                            recordByMethod(ctx, cu, node, method, name.substring(3), FieldAccess.Kind.READ, result, entities);
                        }
                    } else {
                        // 方法符号解析失败（典型：Lombok 生成的 getter/setter，-proc:none 下源码无方法定义）
                        // 回退：用方法名前缀 + 接收者声明类型推断实体表
                        String name = node.getMethodSelect() instanceof MemberSelectTree
                            ? ((MemberSelectTree) node.getMethodSelect()).getIdentifier().toString()
                            : node.getMethodSelect().toString();
                        FieldAccess.Kind kind = null;
                        String prop = null;
                        if (name.startsWith("get") && name.length() > 3 && node.getArguments().isEmpty()) {
                            kind = FieldAccess.Kind.READ; prop = name.substring(3);
                        } else if (name.startsWith("set") && name.length() > 3 && node.getArguments().size() == 1) {
                            kind = FieldAccess.Kind.WRITE; prop = name.substring(3);
                        }
                        if (prop != null) {
                            recordByReceiver(ctx, cu, node, prop, kind, result, entityToTable);
                        }
                    }
                    return super.visitMethodInvocation(node, p);
                }

                /** 原路径：方法符号已解析（显式 getter/setter，如 JOOQ Record）。 */
                private void recordByMethod(AnalysisContext ctx, CompilationUnitTree cu, Tree node,
                                    ExecutableElement method, String prop, FieldAccess.Kind kind,
                                    Map<String, List<FieldAccess>> result, Map<String, TypeElement> entities) {
                    String table = tableOf(method.getEnclosingElement(), entities);
                    if (table == null) return;
                    String fieldName = camelToSnake(prop);
                    ExecutableElement caller = enclosingMethod(ctx, cu, node);
                    if (caller == null) return;
                    result.computeIfAbsent(table + "." + fieldName, k -> new ArrayList<>())
                          .add(new FieldAccess(fieldName, kind, caller));
                }

                /** 回退路径：方法符号未解析（Lombok getter/setter）。用接收者声明类型推断实体表。 */
                private void recordByReceiver(AnalysisContext ctx, CompilationUnitTree cu, Tree node,
                                    String prop, FieldAccess.Kind kind,
                                    Map<String, List<FieldAccess>> result, Map<TypeElement, String> entityToTable) {
                    if (!(node instanceof MethodInvocationTree)) return;
                    ExpressionTree sel = ((MethodInvocationTree) node).getMethodSelect();
                    if (!(sel instanceof MemberSelectTree)) return;
                    ExpressionTree receiver = ((MemberSelectTree) sel).getExpression();
                    if (receiver == null) return;
                    String table = tableOfReceiver(ctx, cu, receiver, entityToTable);
                    if (table == null) return;
                    String fieldName = camelToSnake(prop);
                    ExecutableElement caller = enclosingMethod(ctx, cu, node);
                    if (caller == null) return;
                    result.computeIfAbsent(table + "." + fieldName, k -> new ArrayList<>())
                          .add(new FieldAccess(fieldName, kind, caller));
                }
            }.scan(cu, null);
        }
        return result;
    }

    /** 接收者表达式的声明类型若是实体，返回表名。支持变量引用与 this。 */
    private static String tableOfReceiver(AnalysisContext ctx, CompilationUnitTree cu,
                                          ExpressionTree receiver, Map<TypeElement, String> entityToTable) {
        // 仅对简单接收者推断，避免对复杂链式表达式误判
        if (!(receiver instanceof IdentifierTree)) return null;
        Element e = ctx.resolve(cu, receiver);
        if (e instanceof VariableElement) {
            TypeMirror t = ((VariableElement) e).asType();
            return tableOf(t, entityToTable);
        }
        // this 直接指向当前类
        if (e instanceof TypeElement) {
            return entityToTable.get(e);
        }
        return null;
    }

    private static String tableOf(TypeMirror t, Map<TypeElement, String> entityToTable) {
        if (!(t instanceof DeclaredType)) return null;
        Element enc = ((DeclaredType) t).asElement();
        if (enc instanceof TypeElement) return entityToTable.get(enc);
        return null;
    }

    private static String tableOf(Element enclosing, Map<String, TypeElement> entities) {
        if (!(enclosing instanceof TypeElement)) return null;
        for (Map.Entry<String, TypeElement> e : entities.entrySet()) {
            if (e.getValue().equals(enclosing)) return e.getKey();
        }
        return null;
    }

    private static ExecutableElement enclosingMethod(AnalysisContext ctx, CompilationUnitTree cu, Tree node) {
        TreePath path = TreePath.getPath(cu, node);
        while (path != null) {
            if (path.getLeaf() instanceof MethodTree) {
                MethodTree methodTree = (MethodTree) path.getLeaf();
                Element e = ctx.resolve(cu, methodTree);
                if (e instanceof ExecutableElement) return (ExecutableElement) e;
            }
            path = path.getParentPath();
        }
        return null;
    }

    private static String camelToSnake(String camel) {
        StringBuilder sb = new StringBuilder();
        for (char c : camel.toCharArray()) {
            if (Character.isUpperCase(c)) {
                if (sb.length() > 0) sb.append('_');
                sb.append(Character.toLowerCase(c));
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }
}
