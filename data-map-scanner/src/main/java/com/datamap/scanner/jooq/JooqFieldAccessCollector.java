package com.datamap.scanner.jooq;

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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 字段使用场景收集：识别 table.FIELD 引用（table 解析为 JOOQ 表实例，FIELD 为该表的 TableField），
 * 按 TreePath 向上语境分类为 UPDATE / WRITE / READ / DELETE。
 */
public class JooqFieldAccessCollector {

    /** table.FIELD.xxx(...) 中 xxx 为字段条件方法 => READ。 */
    private static final Set<String> FIELD_READ_METHODS = Set.of(
            "eq", "ne", "lt", "le", "gt", "ge", "in", "like", "notLike", "isNull", "isNotNull");

    /** table.FIELD 作为这些方法的实参 => READ。 */
    private static final Set<String> READ_CONTEXT_METHODS = Set.of(
            "where", "and", "or", "orderBy", "groupBy", "having");

    public static Map<String, List<JooqFieldAccess>> collect(AnalysisContext ctx, Map<String, TypeElement> tables) {
        Map<String, List<JooqFieldAccess>> result = new HashMap<>();
        for (CompilationUnitTree cu : ctx.units) {
            new TreePathScanner<Void, Void>() {
                @Override
                public Void visitMemberSelect(MemberSelectTree mst, Void p) {
                    String table = resolveTable(ctx, cu, mst.getExpression(), tables);
                    if (table != null) {
                        String fieldName = mst.getIdentifier().toString();
                        String op = classify(getCurrentPath(), mst);
                        ExecutableElement method = enclosingMethod(ctx, cu, getCurrentPath());
                        if (method != null) {
                            result.computeIfAbsent(table + "." + fieldName, k -> new ArrayList<>())
                                  .add(new JooqFieldAccess(fieldName, op, method));
                        }
                    }
                    return super.visitMemberSelect(mst, p);
                }

                @Override
                public Void visitMethodInvocation(MethodInvocationTree node, Void p) {
                    String name = methodName(node);
                    if (("deleteFrom".equals(name) || "delete".equals(name)) && !node.getArguments().isEmpty()) {
                        String table = resolveTable(ctx, cu, node.getArguments().get(0), tables);
                        if (table != null) {
                            ExecutableElement method = enclosingMethod(ctx, cu, getCurrentPath());
                            if (method != null) {
                                result.computeIfAbsent(table + ".", k -> new ArrayList<>())
                                      .add(new JooqFieldAccess("", "DELETE", method));
                            }
                        }
                    }
                    return super.visitMethodInvocation(node, p);
                }
            }.scan(cu, null);
        }
        return result;
    }

    /** 表达式解析为 JOOQ 表实例（本地变量/字段/静态字段）时返回表名，否则 null。 */
    private static String resolveTable(AnalysisContext ctx, CompilationUnitTree cu,
                                       ExpressionTree expr, Map<String, TypeElement> tables) {
        Element e = ctx.resolve(cu, expr);
        if (!(e instanceof VariableElement)) return null;
        Element typeEl = ctx.types.asElement(((VariableElement) e).asType());
        if (!(typeEl instanceof TypeElement)) return null;
        for (Map.Entry<String, TypeElement> entry : tables.entrySet()) {
            if (entry.getValue().equals(typeEl)) return entry.getKey();
        }
        return null;
    }

    /** 从 table.FIELD 的 TreePath 向上判定操作类型；无更强信号时默认 READ。 */
    private static String classify(TreePath path, MemberSelectTree mst) {
        TreePath parent = path.getParentPath();
        if (parent == null) return "READ";
        Tree leaf = parent.getLeaf();

        if (leaf instanceof MemberSelectTree) {
            // table.FIELD.xxx(...) —— table.FIELD 是方法接收者
            MemberSelectTree methodSelect = (MemberSelectTree) leaf;
            String name = methodSelect.getIdentifier().toString();
            TreePath gp = parent.getParentPath();
            if (gp != null && gp.getLeaf() instanceof MethodInvocationTree
                    && ((MethodInvocationTree) gp.getLeaf()).getMethodSelect() == methodSelect
                    && FIELD_READ_METHODS.contains(name)) {
                return "READ";
            }
            return "READ";
        }

        if (leaf instanceof MethodInvocationTree) {
            // table.FIELD 直接作为方法实参
            MethodInvocationTree call = (MethodInvocationTree) leaf;
            String name = methodName(call);
            int idx = argIndex(call, mst);
            if ("set".equals(name) && idx == 0) return "UPDATE";
            if ("insertInto".equals(name) && idx >= 1) return "WRITE";
            if ("select".equals(name) && idx == 0) return "READ";
            if (READ_CONTEXT_METHODS.contains(name)) return "READ";
        }

        return "READ";
    }

    private static int argIndex(MethodInvocationTree call, Tree arg) {
        List<? extends ExpressionTree> args = call.getArguments();
        for (int i = 0; i < args.size(); i++) {
            if (args.get(i) == arg) return i;
        }
        return -1;
    }

    private static String methodName(MethodInvocationTree call) {
        ExpressionTree ms = call.getMethodSelect();
        if (ms instanceof IdentifierTree) return ((IdentifierTree) ms).getName().toString();
        if (ms instanceof MemberSelectTree) return ((MemberSelectTree) ms).getIdentifier().toString();
        return "";
    }

    /** 向上查找最近的 MethodTree 并解析为 ExecutableElement。 */
    private static ExecutableElement enclosingMethod(AnalysisContext ctx, CompilationUnitTree cu, TreePath path) {
        while (path != null) {
            if (path.getLeaf() instanceof MethodTree) {
                Element e = ctx.resolve(cu, path.getLeaf());
                if (e instanceof ExecutableElement) return (ExecutableElement) e;
            }
            path = path.getParentPath();
        }
        return null;
    }
}
