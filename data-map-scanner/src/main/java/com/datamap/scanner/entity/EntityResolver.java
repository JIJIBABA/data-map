package com.datamap.scanner.entity;

import com.datamap.scanner.javac.AnalysisContext;
import com.sun.source.tree.AnnotationTree;
import com.sun.source.tree.AssignmentTree;
import com.sun.source.tree.ClassTree;
import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.LiteralTree;
import com.sun.source.tree.Tree;

import javax.lang.model.element.TypeElement;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class EntityResolver {
    /** 从 @TableName("tb_order") 语法读取表名，无需解析注解类型。 */
    public static Map<String, TypeElement> resolve(AnalysisContext ctx) {
        Map<String, TypeElement> result = new HashMap<>();
        for (CompilationUnitTree cu : ctx.units) {
            for (Tree decl : cu.getTypeDecls()) {
                if (!(decl instanceof ClassTree)) continue;
                ClassTree cls = (ClassTree) decl;
                String tableName = tableName(cls);
                if (tableName == null) continue;
                TypeElement te = (TypeElement) ctx.elements.getTypeElement(
                        binaryName(cu, cls.getSimpleName().toString()));
                result.put(tableName, te);
            }
        }
        return result;
    }

    private static String tableName(ClassTree cls) {
        if (cls.getModifiers() == null || cls.getModifiers().getAnnotations() == null) return null;
        for (AnnotationTree ann : cls.getModifiers().getAnnotations()) {
            String simple = simpleName(ann.getAnnotationType().toString());
            if (!"TableName".equals(simple)) continue;
            List<? extends ExpressionTree> args = ann.getArguments();
            if (!args.isEmpty()) {
                ExpressionTree first = args.get(0);
                if (first instanceof AssignmentTree) first = ((AssignmentTree) first).getExpression();
                if (first instanceof LiteralTree) return String.valueOf(((LiteralTree) first).getValue());
            }
        }
        return null;
    }

    static String simpleName(String qualified) {
        int i = qualified.lastIndexOf('.');
        return i >= 0 ? qualified.substring(i + 1) : qualified;
    }

    static String binaryName(CompilationUnitTree cu, String typeName) {
        ExpressionTree pkg = cu.getPackageName();
        String pkgName = pkg == null ? "" : pkg.toString();
        return pkgName.isEmpty() ? typeName : pkgName + "." + typeName;
    }
}
