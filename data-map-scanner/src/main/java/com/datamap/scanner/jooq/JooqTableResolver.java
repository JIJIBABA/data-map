package com.datamap.scanner.jooq;

import com.datamap.scanner.javac.AnalysisContext;
import com.sun.source.tree.ClassTree;
import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.IdentifierTree;
import com.sun.source.tree.MemberSelectTree;
import com.sun.source.tree.ParameterizedTypeTree;
import com.sun.source.tree.Tree;

import javax.lang.model.element.TypeElement;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

/** 表发现：extends 子句简单名 == TableImpl 的类即 JOOQ 表类，表名 = 类简单名。 */
public class JooqTableResolver {
    public static Map<String, TypeElement> resolve(AnalysisContext ctx) {
        Map<String, TypeElement> result = new HashMap<>();
        for (CompilationUnitTree cu : ctx.units) {
            forEachClass(cu, cls -> {
                if (!isTableClass(cls)) return;
                String tableName = cls.getSimpleName().toString();
                TypeElement te = (TypeElement) ctx.elements.getTypeElement(
                        binaryName(cu, tableName));
                if (te != null) result.put(tableName, te);
            });
        }
        return result;
    }

    private static boolean isTableClass(ClassTree cls) {
        Tree ext = cls.getExtendsClause();
        if (ext == null) return false;
        return "TableImpl".equals(extendsSimpleName(ext));
    }

    private static String extendsSimpleName(Tree ext) {
        if (ext instanceof ParameterizedTypeTree) {
            return extendsSimpleName(((ParameterizedTypeTree) ext).getType());
        }
        if (ext instanceof IdentifierTree) {
            return ((IdentifierTree) ext).getName().toString();
        }
        if (ext instanceof MemberSelectTree) {
            return ((MemberSelectTree) ext).getIdentifier().toString();
        }
        String s = ext.toString();
        int lt = s.indexOf('<');
        if (lt >= 0) s = s.substring(0, lt);
        return simpleName(s);
    }

    private static void forEachClass(CompilationUnitTree cu, Consumer<ClassTree> fn) {
        for (Tree decl : cu.getTypeDecls()) {
            if (decl instanceof ClassTree) {
                ClassTree cls = (ClassTree) decl;
                fn.accept(cls);
                forEachNested(cls, fn);
            }
        }
    }

    private static void forEachNested(ClassTree cls, Consumer<ClassTree> fn) {
        for (Tree member : cls.getMembers()) {
            if (member instanceof ClassTree) {
                ClassTree nested = (ClassTree) member;
                fn.accept(nested);
                forEachNested(nested, fn);
            }
        }
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
