package com.datamap.scanner.relation;

import com.datamap.scanner.javac.AnalysisContext;
import com.datamap.scanner.model.ScanRelation;
import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.IdentifierTree;
import com.sun.source.tree.MemberSelectTree;
import com.sun.source.tree.MethodInvocationTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.util.TreePath;
import com.sun.source.util.TreePathScanner;

import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 关联关系：跨实体 setter/getter 赋值。
 * 识别 {@code a.setXxx(b.getYyy())} / {@code a.setXxx(b.isYyy())}，当 a、b 分属不同表时产出
 * {@code 表(a).xxx -> 表(b).yyy} 的 DIRECT_JOIN 关联（方法签名取自所在方法）。
 * 覆盖 MyBatis {@code @TableName} 实体（按类型反查）与 JOOQ POJO/Record（按类型简单名反查）。
 */
public class CrossEntityRelationExtractor {

    public static Map<String, List<ScanRelation>> extract(AnalysisContext ctx,
            Map<String, TypeElement> entities, Map<String, TypeElement> jooqTables) {
        Map<TypeElement, String> entityTableByType = new HashMap<>();
        for (Map.Entry<String, TypeElement> e : entities.entrySet()) {
            entityTableByType.put(e.getValue(), e.getKey());
        }

        Map<String, List<ScanRelation>> result = new HashMap<>();
        for (CompilationUnitTree cu : ctx.units) {
            new TreePathScanner<Void, Void>() {
                @Override
                public Void visitMethodInvocation(MethodInvocationTree node, Void p) {
                    handle(node, ctx, cu, getCurrentPath(), entityTableByType, jooqTables, result);
                    return super.visitMethodInvocation(node, p);
                }
            }.scan(cu, null);
        }
        return result;
    }

    private static void handle(MethodInvocationTree setCall, AnalysisContext ctx, CompilationUnitTree cu,
            TreePath setCallPath, Map<TypeElement, String> entityTableByType,
            Map<String, TypeElement> jooqTables, Map<String, List<ScanRelation>> result) {
        String setter = methodName(setCall);
        if (!setter.startsWith("set") || setter.length() <= 3) return;
        if (setCall.getArguments().size() != 1) return;

        ExpressionTree arg = setCall.getArguments().get(0);
        if (!(arg instanceof MethodInvocationTree)) return;
        MethodInvocationTree getCall = (MethodInvocationTree) arg;
        String getter = methodName(getCall);
        int propLen;
        if (getter.startsWith("get") && getter.length() > 3) {
            propLen = 3;
        } else if (getter.startsWith("is") && getter.length() > 2) {
            propLen = 2;
        } else {
            return;
        }
        if ("getClass".equals(getter)) return; // Object 继承方法，非字段
        if (!getCall.getArguments().isEmpty()) return;

        String sourceTable = tableOfReceiver(setCall, setCallPath, ctx, entityTableByType, jooqTables);
        if (sourceTable == null) return;
        String targetTable = tableOfReceiver(getCall, new TreePath(setCallPath, getCall), ctx, entityTableByType, jooqTables);
        if (targetTable == null || sourceTable.equals(targetTable)) return;

        String sourceField = fieldName(setter.substring(3), jooqTables.containsKey(sourceTable));
        String targetField = fieldName(getter.substring(propLen), jooqTables.containsKey(targetTable));
        String methodSignature = enclosingMethodSignature(ctx, setCallPath);

        result.computeIfAbsent(sourceTable, k -> new ArrayList<>())
              .add(new ScanRelation(sourceField, targetTable, targetField, "DIRECT_JOIN", methodSignature));
    }

    /** 接收者解析：MyBatis 实体按类型反查；JOOQ POJO/Record 按类型简单名反查。 */
    private static String tableOfReceiver(MethodInvocationTree call, TreePath callPath, AnalysisContext ctx,
            Map<TypeElement, String> entityTableByType, Map<String, TypeElement> jooqTables) {
        ExpressionTree ms = call.getMethodSelect();
        if (!(ms instanceof MemberSelectTree)) return null; // 隐式 this 调用暂不处理
        ExpressionTree recv = ((MemberSelectTree) ms).getExpression();
        TreePath recvPath = new TreePath(callPath, recv);

        TypeElement type = receiverType(ctx, recvPath);
        if (type == null) return null;

        String table = entityTableByType.get(type);
        if (table != null) return table;

        String simple = type.getSimpleName().toString();
        if (simple.endsWith("Record")) {
            simple = simple.substring(0, simple.length() - "Record".length());
        }
        String jooqTable = camelToSnake(simple).toUpperCase();
        return jooqTables.containsKey(jooqTable) ? jooqTable : null;
    }

    private static TypeElement receiverType(AnalysisContext ctx, TreePath recvPath) {
        try {
            TypeMirror tm = ctx.trees.getTypeMirror(recvPath);
            if (tm != null && tm.getKind() != TypeKind.ERROR) {
                Element el = ctx.types.asElement(tm);
                if (el instanceof TypeElement) return (TypeElement) el;
            }
        } catch (RuntimeException ignored) {
            // fall through
        }
        Element e = ctx.trees.getElement(recvPath);
        if (e instanceof VariableElement) {
            Element typeEl = ctx.types.asElement(((VariableElement) e).asType());
            if (typeEl instanceof TypeElement) return (TypeElement) typeEl;
        }
        return null;
    }

    private static String fieldName(String prop, boolean upper) {
        String s = camelToSnake(prop);
        return upper ? s.toUpperCase() : s;
    }

    private static String enclosingMethodSignature(AnalysisContext ctx, TreePath path) {
        while (path != null) {
            if (path.getLeaf() instanceof MethodTree) {
                Element e = ctx.trees.getElement(path);
                if (e instanceof ExecutableElement) {
                    ExecutableElement m = (ExecutableElement) e;
                    Element enclosing = m.getEnclosingElement();
                    String typeName = enclosing instanceof TypeElement
                            ? ((TypeElement) enclosing).getQualifiedName().toString()
                            : enclosing.getSimpleName().toString();
                    return typeName + "." + m.getSimpleName();
                }
            }
            path = path.getParentPath();
        }
        return "";
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

    private static String methodName(MethodInvocationTree call) {
        ExpressionTree ms = call.getMethodSelect();
        if (ms instanceof IdentifierTree) return ((IdentifierTree) ms).getName().toString();
        if (ms instanceof MemberSelectTree) return ((MemberSelectTree) ms).getIdentifier().toString();
        return "";
    }
}
