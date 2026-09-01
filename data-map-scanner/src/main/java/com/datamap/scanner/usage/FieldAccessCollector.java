package com.datamap.scanner.usage;

import com.datamap.scanner.entity.AliasFieldResolver;
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
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import java.util.*;

public class FieldAccessCollector {
    /**
     * @param aliasMap 由 {@link AliasFieldResolver#resolve} 产出的「DTO类型#snake字段 -> 表名.snake字段」别名映射；
     *                 可为空（向后兼容旧调用方）。接收者非表实体时回退查表用。
     */
    public static Map<String, List<FieldAccess>> collect(AnalysisContext ctx, Map<String, List<TypeElement>> entities,
                                                          Map<String, String> aliasMap) {
        // 实体 TypeElement -> 表名 的反向索引（覆盖同表多实体），供接收者类型推断使用
        Map<TypeElement, String> entityToTable = com.datamap.scanner.entity.EntityResolver.reverseIndex(entities);
        Map<String, String> aliases = aliasMap == null ? Collections.emptyMap() : aliasMap;

        Map<String, List<FieldAccess>> result = new HashMap<>();
        for (CompilationUnitTree cu : ctx.units) {
            new TreePathScanner<Void, Void>() {
                @Override public Void visitMethodInvocation(MethodInvocationTree node, Void p) {
                    Element e = ctx.resolve(cu, node);
                    if (e instanceof ExecutableElement) {
                        ExecutableElement method = (ExecutableElement) e;
                        String name = method.getSimpleName().toString();
                        if (name.startsWith("set") && name.length() > 3 && method.getParameters().size() == 1) {
                            recordByMethod(ctx, cu, node, method, name.substring(3), FieldAccess.Kind.WRITE, result, entityToTable, aliases);
                        } else if (name.startsWith("get") && name.length() > 3 && method.getParameters().isEmpty()) {
                            recordByMethod(ctx, cu, node, method, name.substring(3), FieldAccess.Kind.READ, result, entityToTable, aliases);
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
                            recordByReceiver(ctx, cu, node, prop, kind, result, entityToTable, aliases);
                        }
                    }
                    return super.visitMethodInvocation(node, p);
                }

                /** 原路径：方法符号已解析（显式 getter/setter，如 JOOQ Record）。 */
                private void recordByMethod(AnalysisContext ctx, CompilationUnitTree cu, Tree node,
                                    ExecutableElement method, String prop, FieldAccess.Kind kind,
                                    Map<String, List<FieldAccess>> result, Map<TypeElement, String> entityToTable,
                                    Map<String, String> aliases) {
                    String fieldName = camelToSnake(prop);
                    String table = tableOf(method.getEnclosingElement(), entityToTable);
                    // 接收者非表实体时，尝试别名回退：用方法所属类全名 + 字段查别名表
                    if (table == null && !aliases.isEmpty()) {
                        String aliasKey = method.getEnclosingElement().toString() + "#" + fieldName;
                        String aliased = aliases.get(aliasKey);
                        if (aliased != null) {
                            String aliasField = aliased.substring(aliased.indexOf('.') + 1);
                            ExecutableElement caller = enclosingMethod(ctx, cu, node);
                            if (caller == null) return;
                            result.computeIfAbsent(aliased, k -> new ArrayList<>())
                                  .add(new FieldAccess(aliasField, kind, caller));
                        }
                        return;
                    }
                    if (table == null) return;
                    ExecutableElement caller = enclosingMethod(ctx, cu, node);
                    if (caller == null) return;
                    result.computeIfAbsent(table + "." + fieldName, k -> new ArrayList<>())
                          .add(new FieldAccess(fieldName, kind, caller));
                }

                /** 回退路径：方法符号未解析（Lombok getter/setter）。用接收者声明类型推断实体表。 */
                private void recordByReceiver(AnalysisContext ctx, CompilationUnitTree cu, Tree node,
                                    String prop, FieldAccess.Kind kind,
                                    Map<String, List<FieldAccess>> result, Map<TypeElement, String> entityToTable,
                                    Map<String, String> aliases) {
                    if (!(node instanceof MethodInvocationTree)) return;
                    ExpressionTree sel = ((MethodInvocationTree) node).getMethodSelect();
                    if (!(sel instanceof MemberSelectTree)) return;
                    ExpressionTree receiver = ((MemberSelectTree) sel).getExpression();
                    if (receiver == null) return;
                    String fieldName = camelToSnake(prop);
                    String table = tableOfReceiver(ctx, cu, receiver, entityToTable);
                    // 接收者非表实体时，尝试别名回退：用接收者声明类型全名 + 字段查别名表
                    if (table == null && !aliases.isEmpty()) {
                        TypeElement recvType = AliasFieldResolver.receiverType(ctx,
                                TreePath.getPath(cu, receiver));
                        if (recvType != null) {
                            String aliasKey = recvType.getQualifiedName().toString() + "#" + fieldName;
                            String aliased = aliases.get(aliasKey);
                            if (aliased != null) {
                                String aliasField = aliased.substring(aliased.indexOf('.') + 1);
                                ExecutableElement caller = enclosingMethod(ctx, cu, node);
                                if (caller == null) return;
                                result.computeIfAbsent(aliased, k -> new ArrayList<>())
                                      .add(new FieldAccess(aliasField, kind, caller));
                            }
                        }
                        return;
                    }
                    if (table == null) return;
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

    private static String tableOf(Element enclosing, Map<TypeElement, String> entityToTable) {
        if (!(enclosing instanceof TypeElement)) return null;
        return entityToTable.get(enclosing);
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
