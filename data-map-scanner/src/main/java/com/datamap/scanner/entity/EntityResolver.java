package com.datamap.scanner.entity;

import com.datamap.scanner.javac.AnalysisContext;
import com.datamap.scanner.util.DocTreeText;
import com.sun.source.doctree.DocCommentTree;
import com.sun.source.tree.AnnotationTree;
import com.sun.source.tree.AssignmentTree;
import com.sun.source.tree.ClassTree;
import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.LiteralTree;
import com.sun.source.tree.Tree;
import com.sun.source.util.DocTrees;

import javax.lang.model.element.TypeElement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class EntityResolver {
    /**
     * 从 @TableName("tb_order") 语法读取表名，无需解析注解类型。
     * <p>同一张表可能存在多个实体类（如 core 层 CollectInfo 与 infra 层 CollectInfoDO 都映射
     * t_collect_info）——全部保留为 List，避免后者覆盖前者导致另一类实体上的字段访问无法归表。
     * 字段/注释提取取列表首元素，访问归表的 TypeElement→表名 反查需覆盖全部元素。
     */
    public static Map<String, List<TypeElement>> resolve(AnalysisContext ctx) {
        Map<String, List<TypeElement>> result = new HashMap<>();
        for (CompilationUnitTree cu : ctx.units) {
            for (Tree decl : cu.getTypeDecls()) {
                if (!(decl instanceof ClassTree)) continue;
                ClassTree cls = (ClassTree) decl;
                String tableName = tableName(cls);
                if (tableName == null) continue;
                TypeElement te = (TypeElement) ctx.elements.getTypeElement(
                        binaryName(cu, cls.getSimpleName().toString()));
                result.computeIfAbsent(tableName, k -> new ArrayList<>()).add(te);
            }
        }
        return result;
    }

    /**
     * 构建实体 TypeElement -> 表名 的反向索引，覆盖同表的全部实体类。
     * 供字段访问归表、别名解析等按接收者类型反查表名使用。
     */
    public static Map<TypeElement, String> reverseIndex(Map<String, List<TypeElement>> entities) {
        Map<TypeElement, String> rev = new HashMap<>();
        for (Map.Entry<String, List<TypeElement>> e : entities.entrySet()) {
            for (TypeElement te : e.getValue()) rev.put(te, e.getKey());
        }
        return rev;
    }

    /**
     * 提取表实体的类级 Javadoc 作为表注释。
     * MyBatis 生成实体时，类 Javadoc 常为「表注释」（如 {@code 补充指标采集表}）。
     * 与 FieldExtractor 取字段 Javadoc 同机制（DocTrees）。
     */
    public static String tableComment(TypeElement entity, AnalysisContext ctx) {
        if (entity == null) return "";
        try {
            DocTrees docTrees = DocTrees.instance(ctx.task);
            DocCommentTree doc = docTrees.getDocCommentTree(ctx.trees.getPath(entity));
            if (doc != null) {
                String s = DocTreeText.fullBody(doc);
                return s == null ? "" : s;
            }
        } catch (RuntimeException ignored) {
            // 取不到注释时返回空串，不中断扫描
        }
        return "";
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
