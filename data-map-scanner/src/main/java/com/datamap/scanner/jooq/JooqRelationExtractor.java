package com.datamap.scanner.jooq;

import com.datamap.scanner.javac.AnalysisContext;
import com.datamap.scanner.model.ScanRelation;
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

/**
 * 关联关系提取：识别 .join/.leftJoin/.innerJoin(X).on(A.F.eq(B.F)) 调用链，
 * 产出 A.F -> B.F 的 DIRECT_JOIN 关联（按源表分组）。
 */
public class JooqRelationExtractor {

    public static Map<String, List<ScanRelation>> extract(AnalysisContext ctx, Map<String, TypeElement> tables) {
        Map<String, List<ScanRelation>> result = new HashMap<>();
        for (CompilationUnitTree cu : ctx.units) {
            new TreePathScanner<Void, Void>() {
                @Override
                public Void visitMethodInvocation(MethodInvocationTree node, Void p) {
                    if ("on".equals(methodName(node))) {
                        extractOn(ctx, cu, node, getCurrentPath(), tables, result);
                    }
                    return super.visitMethodInvocation(node, p);
                }
            }.scan(cu, null);
        }
        return result;
    }

    /** on(A.F.eq(B.F))：eq 接收者(左侧字段) + eq 实参(右侧字段)。 */
    private static void extractOn(AnalysisContext ctx, CompilationUnitTree cu,
                                  MethodInvocationTree on, TreePath path,
                                  Map<String, TypeElement> tables,
                                  Map<String, List<ScanRelation>> result) {
        List<? extends ExpressionTree> onArgs = on.getArguments();
        if (onArgs.isEmpty() || !(onArgs.get(0) instanceof MethodInvocationTree)) return;
        MethodInvocationTree eq = (MethodInvocationTree) onArgs.get(0);
        if (!"eq".equals(methodName(eq))) return;

        if (!(eq.getMethodSelect() instanceof MemberSelectTree)) return;
        ExpressionTree leftRef = ((MemberSelectTree) eq.getMethodSelect()).getExpression();
        if (!(leftRef instanceof MemberSelectTree)) return;

        List<? extends ExpressionTree> eqArgs = eq.getArguments();
        if (eqArgs.isEmpty() || !(eqArgs.get(0) instanceof MemberSelectTree)) return;
        MemberSelectTree rightRef = (MemberSelectTree) eqArgs.get(0);

        MemberSelectTree left = (MemberSelectTree) leftRef;
        String sourceTable = resolveTableName(ctx, cu, left.getExpression(), tables);
        String targetTable = resolveTableName(ctx, cu, rightRef.getExpression(), tables);
        if (sourceTable == null || targetTable == null) return;

        String sourceField = left.getIdentifier().toString();
        String targetField = rightRef.getIdentifier().toString();
        String methodSignature = enclosingMethodSignature(ctx, cu, path);
        result.computeIfAbsent(sourceTable, k -> new ArrayList<>())
              .add(new ScanRelation(sourceField, targetTable, targetField, "DIRECT_JOIN", methodSignature));
    }

    /** 表达式解析为 JOOQ 表实例（局部变量/字段/参数）时返回表名，否则 null。 */
    private static String resolveTableName(AnalysisContext ctx, CompilationUnitTree cu,
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

    /** enclosingType + "." + methodSimpleName（向上查找最近 MethodTree）。 */
    private static String enclosingMethodSignature(AnalysisContext ctx, CompilationUnitTree cu, TreePath path) {
        while (path != null) {
            if (path.getLeaf() instanceof MethodTree) {
                Element e = ctx.resolve(cu, path.getLeaf());
                if (e instanceof ExecutableElement) {
                    ExecutableElement method = (ExecutableElement) e;
                    Element enclosing = method.getEnclosingElement();
                    String typeName = enclosing instanceof TypeElement
                            ? ((TypeElement) enclosing).getQualifiedName().toString()
                            : enclosing.getSimpleName().toString();
                    return typeName + "." + method.getSimpleName();
                }
            }
            path = path.getParentPath();
        }
        return "";
    }

    private static String methodName(MethodInvocationTree call) {
        ExpressionTree ms = call.getMethodSelect();
        if (ms instanceof IdentifierTree) return ((IdentifierTree) ms).getName().toString();
        if (ms instanceof MemberSelectTree) return ((MemberSelectTree) ms).getIdentifier().toString();
        return "";
    }
}
