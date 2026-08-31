package com.datamap.scanner.usage;

import com.datamap.scanner.javac.AnalysisContext;
import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.MethodInvocationTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.Tree;
import com.sun.source.util.TreePath;
import com.sun.source.util.TreePathScanner;

import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import java.util.*;

public class FieldAccessCollector {
    public static Map<String, List<FieldAccess>> collect(AnalysisContext ctx, Map<String, TypeElement> entities) {
        Map<String, List<FieldAccess>> result = new HashMap<>();
        for (CompilationUnitTree cu : ctx.units) {
            new TreePathScanner<Void, Void>() {
                @Override public Void visitMethodInvocation(MethodInvocationTree node, Void p) {
                    Element e = ctx.resolve(cu, node);
                    if (e instanceof ExecutableElement) {
                        ExecutableElement method = (ExecutableElement) e;
                        String name = method.getSimpleName().toString();
                        if (name.startsWith("set") && name.length() > 3 && method.getParameters().size() == 1) {
                            record(ctx, cu, node, method, name.substring(3), FieldAccess.Kind.WRITE, result, entities);
                        } else if (name.startsWith("get") && name.length() > 3 && method.getParameters().isEmpty()) {
                            record(ctx, cu, node, method, name.substring(3), FieldAccess.Kind.READ, result, entities);
                        }
                    }
                    return super.visitMethodInvocation(node, p);
                }

                private void record(AnalysisContext ctx, CompilationUnitTree cu, Tree node,
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
            }.scan(cu, null);
        }
        return result;
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
