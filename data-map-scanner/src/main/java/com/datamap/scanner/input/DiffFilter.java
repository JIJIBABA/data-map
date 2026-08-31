package com.datamap.scanner.input;

import com.datamap.scanner.javac.AnalysisContext;
import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.util.TreePath;

import javax.lang.model.element.ExecutableElement;
import java.util.List;
import java.util.Set;

public class DiffFilter {
    /** 直接方法或链上任一方法定义在变更文件里 → 需要重算输出。changedFiles==null 表示全量。 */
    public static boolean affected(ExecutableElement method, List<List<ExecutableElement>> paths,
                                   Set<String> changedFiles, AnalysisContext ctx) {
        if (changedFiles == null) return true;
        if (isChanged(method, changedFiles, ctx)) return true;
        for (List<ExecutableElement> path : paths) {
            for (ExecutableElement m : path) {
                if (isChanged(m, changedFiles, ctx)) return true;
            }
        }
        return false;
    }

    private static boolean isChanged(ExecutableElement method, Set<String> changedFiles, AnalysisContext ctx) {
        TreePath path = ctx.trees.getPath(method);
        if (path == null) return false;
        CompilationUnitTree cu = path.getCompilationUnit();
        String name = cu.getSourceFile().getName();
        for (String f : changedFiles) {
            if (name.endsWith(f) || f.endsWith(name)) return true;
        }
        return false;
    }
}
