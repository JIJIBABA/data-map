package com.datamap.scanner.callgraph;

import com.datamap.scanner.javac.AnalysisContext;
import com.sun.source.tree.ClassTree;
import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.Tree;

import javax.lang.model.element.Element;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import java.util.*;

public class SpringInterfaceBinder {
    /** 找出仓库内每个接口的唯一实现类。 */
    public static Map<TypeElement, TypeElement> bind(AnalysisContext ctx) {
        Map<TypeElement, List<TypeElement>> impls = new HashMap<>();
        for (CompilationUnitTree cu : ctx.units) {
            for (Tree decl : cu.getTypeDecls()) {
                if (!(decl instanceof ClassTree)) continue;
                Element e = ctx.resolve(cu, decl);
                if (!(e instanceof TypeElement)) continue;
                TypeElement cls = (TypeElement) e;
                if (cls.getKind().isInterface() || cls.getModifiers().contains(Modifier.ABSTRACT)) continue;
                for (TypeElement iface : interfacesOf(ctx, cls)) {
                    impls.computeIfAbsent(iface, k -> new ArrayList<>()).add(cls);
                }
            }
        }
        Map<TypeElement, TypeElement> bind = new HashMap<>();
        for (Map.Entry<TypeElement, List<TypeElement>> en : impls.entrySet()) {
            if (en.getValue().size() == 1) bind.put(en.getKey(), en.getValue().get(0));
        }
        return bind;
    }

    private static List<TypeElement> interfacesOf(AnalysisContext ctx, TypeElement cls) {
        List<TypeElement> out = new ArrayList<>();
        for (javax.lang.model.type.TypeMirror t : cls.getInterfaces()) {
            Element e = ctx.types.asElement(t);
            if (e instanceof TypeElement) out.add((TypeElement) e);
        }
        return out;
    }
}
