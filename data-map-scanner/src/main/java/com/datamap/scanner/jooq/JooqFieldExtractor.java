package com.datamap.scanner.jooq;

import com.datamap.scanner.entity.CommonFieldFilter;
import com.datamap.scanner.javac.AnalysisContext;
import com.datamap.scanner.model.ScanField;
import com.sun.source.tree.ClassTree;
import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.IdentifierTree;
import com.sun.source.tree.LiteralTree;
import com.sun.source.tree.MemberSelectTree;
import com.sun.source.tree.MethodInvocationTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.NewClassTree;
import com.sun.source.tree.ReturnTree;
import com.sun.source.tree.StatementTree;
import com.sun.source.tree.Tree;
import com.sun.source.tree.VariableTree;
import com.sun.source.util.TreePath;

import javax.lang.model.element.TypeElement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** 字段提取：解析 createField 声明 + getPrimaryKey()→Keys.XXX→UniqueKeyImpl(..., table.FIELD) 的 PK。 */
public class JooqFieldExtractor {
    public static List<ScanField> extract(TypeElement tableClass, AnalysisContext ctx) {
        TreePath path = treePath(tableClass, ctx);
        if (path == null || !(path.getLeaf() instanceof ClassTree)) return Collections.emptyList();
        ClassTree cls = (ClassTree) path.getLeaf();
        CompilationUnitTree cu = path.getCompilationUnit();
        String pkg = cu == null ? null : packageName(cu);

        Set<String> pkFields = pkFields(cls, pkg, ctx);
        List<ScanField> fields = new ArrayList<>();
        for (Tree member : cls.getMembers()) {
            if (!(member instanceof VariableTree)) continue;
            VariableTree var = (VariableTree) member;
            if (!(var.getInitializer() instanceof MethodInvocationTree)) continue;
            MethodInvocationTree call = (MethodInvocationTree) var.getInitializer();
            if (!"createField".equals(methodName(call))) continue;

            String column = stringArg(call, 0);
            if (column == null) continue;
            String fieldType = mapSqlType(sqlTypeName(arg(call, 1)));
            String comment = lastStringArg(call, 1);
            boolean pk = pkFields.contains(var.getName().toString());
            fields.add(new ScanField(column, comment, fieldType, pk, CommonFieldFilter.isBusiness(column)));
        }
        return fields;
    }

    private static Set<String> pkFields(ClassTree tableClass, String pkg, AnalysisContext ctx) {
        Set<String> pk = new HashSet<>();
        String keysConstant = primaryKeyConstant(tableClass);
        if (keysConstant == null) return pk;
        ClassTree keysClass = findKeysClass(pkg, ctx);
        if (keysClass == null) return pk;
        for (Tree member : keysClass.getMembers()) {
            if (!(member instanceof VariableTree)) continue;
            VariableTree var = (VariableTree) member;
            if (!var.getName().toString().equals(keysConstant)) continue;
            if (!(var.getInitializer() instanceof NewClassTree)) continue;
            NewClassTree nc = (NewClassTree) var.getInitializer();
            for (ExpressionTree a : nc.getArguments()) {
                String f = fieldRefName(a);
                if (f != null) pk.add(f);
            }
        }
        return pk;
    }

    private static String primaryKeyConstant(ClassTree cls) {
        for (Tree member : cls.getMembers()) {
            if (!(member instanceof MethodTree)) continue;
            MethodTree m = (MethodTree) member;
            if (!m.getName().toString().equals("getPrimaryKey")) continue;
            if (m.getBody() == null) continue;
            for (StatementTree st : m.getBody().getStatements()) {
                if (!(st instanceof ReturnTree)) continue;
                ExpressionTree expr = ((ReturnTree) st).getExpression();
                if (expr == null) continue;
                String name = constantName(expr);
                if (name != null) return name;
            }
        }
        return null;
    }

    private static String constantName(ExpressionTree expr) {
        if (expr instanceof MemberSelectTree) return ((MemberSelectTree) expr).getIdentifier().toString();
        if (expr instanceof IdentifierTree) return ((IdentifierTree) expr).getName().toString();
        return null;
    }

    private static String fieldRefName(ExpressionTree expr) {
        if (expr instanceof MemberSelectTree) return ((MemberSelectTree) expr).getIdentifier().toString();
        if (expr instanceof IdentifierTree) return ((IdentifierTree) expr).getName().toString();
        return null;
    }

    private static ClassTree findKeysClass(String pkg, AnalysisContext ctx) {
        for (CompilationUnitTree cu : ctx.units) {
            if (pkg != null && !pkg.equals(packageName(cu))) continue;
            for (Tree decl : cu.getTypeDecls()) {
                if (decl instanceof ClassTree
                        && "Keys".equals(((ClassTree) decl).getSimpleName().toString())) {
                    return (ClassTree) decl;
                }
            }
        }
        return null;
    }

    private static String packageName(CompilationUnitTree cu) {
        return cu.getPackageName() == null ? "" : cu.getPackageName().toString();
    }

    private static String methodName(MethodInvocationTree call) {
        ExpressionTree ms = call.getMethodSelect();
        if (ms instanceof IdentifierTree) return ((IdentifierTree) ms).getName().toString();
        if (ms instanceof MemberSelectTree) return ((MemberSelectTree) ms).getIdentifier().toString();
        return "";
    }

    private static ExpressionTree arg(MethodInvocationTree call, int index) {
        List<? extends ExpressionTree> args = call.getArguments();
        return index < args.size() ? args.get(index) : null;
    }

    private static String stringArg(MethodInvocationTree call, int index) {
        ExpressionTree a = arg(call, index);
        if (a instanceof LiteralTree) {
            Object v = ((LiteralTree) a).getValue();
            return v == null ? null : v.toString();
        }
        return null;
    }

    private static String lastStringArg(MethodInvocationTree call, int fromIndex) {
        List<? extends ExpressionTree> args = call.getArguments();
        String last = "";
        for (int i = fromIndex; i < args.size(); i++) {
            ExpressionTree a = args.get(i);
            if (a instanceof LiteralTree && ((LiteralTree) a).getValue() instanceof String) {
                last = ((LiteralTree) a).getValue().toString();
            }
        }
        return last;
    }

    private static String sqlTypeName(ExpressionTree expr) {
        if (expr == null) return null;
        if (expr instanceof MemberSelectTree) {
            MemberSelectTree ms = (MemberSelectTree) expr;
            if (isSqlDataType(ms.getExpression())) return ms.getIdentifier().toString();
            return sqlTypeName(ms.getExpression());
        }
        if (expr instanceof MethodInvocationTree) {
            return sqlTypeName(((MethodInvocationTree) expr).getMethodSelect());
        }
        return null;
    }

    private static boolean isSqlDataType(ExpressionTree expr) {
        if (expr == null) return false;
        String s = expr.toString();
        return s.equals("SQLDataType") || s.endsWith(".SQLDataType");
    }

    private static String mapSqlType(String sqlType) {
        if (sqlType == null) return "VARCHAR";
        switch (sqlType) {
            case "BIGINT": return "BIGINT";
            case "VARCHAR": return "VARCHAR";
            case "NUMERIC": return "DECIMAL";
            case "INTEGER": return "INTEGER";
            case "TIMESTAMP": return "DATETIME";
            case "BOOLEAN": return "TINYINT";
            default: return "VARCHAR";
        }
    }

    private static TreePath treePath(TypeElement te, AnalysisContext ctx) {
        try {
            TreePath p = ctx.trees.getPath(te);
            if (p != null) return p;
        } catch (RuntimeException e) {
            // fall through to unit scan
        }
        String qn = te.getQualifiedName().toString();
        for (CompilationUnitTree cu : ctx.units) {
            for (Tree decl : cu.getTypeDecls()) {
                if (decl instanceof ClassTree
                        && JooqTableResolver.binaryName(cu, ((ClassTree) decl).getSimpleName().toString()).equals(qn)) {
                    return TreePath.getPath(cu, decl);
                }
            }
        }
        return null;
    }
}
