package com.datamap.scanner.callgraph;

import com.datamap.scanner.javac.AnalysisContext;
import com.sun.source.tree.*;
import com.sun.source.util.TreePath;
import com.sun.source.util.TreePathScanner;

import javax.lang.model.element.*;
import java.util.*;

public class CallGraphBuilder {
    public static CallGraph build(AnalysisContext ctx) {
        CallGraph g = new CallGraph();
        Map<String, List<ExecutableElement>> byName = indexByName(ctx);
        for (CompilationUnitTree cu : ctx.units) {
            new TreePathScanner<Void, Void>() {
                @Override public Void visitMethodInvocation(MethodInvocationTree node, Void p) {
                    Element e = ctx.resolve(cu, node);
                    ExecutableElement caller = enclosingMethod(ctx, cu, node);
                    if (e instanceof ExecutableElement && caller != null) {
                        ExecutableElement callee = (ExecutableElement) e;
                        for (ExecutableElement target : targets(ctx, callee, byName)) {
                            g.add(caller, target);
                        }
                    }
                    return super.visitMethodInvocation(node, p);
                }
            }.scan(cu, null);
        }
        return g;
    }

    private static Map<String, List<ExecutableElement>> indexByName(AnalysisContext ctx) {
        Map<String, List<ExecutableElement>> m = new HashMap<>();
        for (CompilationUnitTree cu : ctx.units) {
            for (Tree decl : cu.getTypeDecls()) {
                if (!(decl instanceof ClassTree)) continue;
                Element te = ctx.resolve(cu, decl);
                if (!(te instanceof TypeElement)) continue;
                for (Element member : ctx.elements.getAllMembers((TypeElement) te)) {
                    if (member instanceof ExecutableElement) {
                        m.computeIfAbsent(member.getSimpleName().toString(), k -> new ArrayList<>())
                         .add((ExecutableElement) member);
                    }
                }
            }
        }
        return m;
    }

    /** 抽象/接口方法展开到仓库内所有实现；具体方法直接用自身。 */
    private static List<ExecutableElement> targets(AnalysisContext ctx, ExecutableElement callee,
                                                   Map<String, List<ExecutableElement>> byName) {
        Element owner = callee.getEnclosingElement();
        boolean concrete = owner instanceof TypeElement
                && !((TypeElement) owner).getKind().isInterface()
                && !callee.getModifiers().contains(Modifier.ABSTRACT);
        if (concrete) return Collections.singletonList(callee);

        List<ExecutableElement> impls = new ArrayList<>();
        for (ExecutableElement cand : byName.getOrDefault(callee.getSimpleName().toString(), Collections.emptyList())) {
            TypeElement candOwner = (TypeElement) cand.getEnclosingElement();
            if (candOwner.getKind().isInterface()) continue;
            if (ctx.elements.overrides(cand, callee, candOwner)) impls.add(cand);
        }
        return impls.isEmpty() ? Collections.singletonList(callee) : impls;
    }

    private static ExecutableElement enclosingMethod(AnalysisContext ctx, CompilationUnitTree cu, Tree node) {
        TreePath path = TreePath.getPath(cu, node);
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
