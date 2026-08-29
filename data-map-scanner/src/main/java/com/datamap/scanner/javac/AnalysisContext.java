package com.datamap.scanner.javac;

import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.Tree;
import com.sun.source.util.JavacTask;
import com.sun.source.util.TreePath;
import com.sun.source.util.Trees;
import javax.lang.model.element.Element;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import java.util.ArrayList;
import java.util.List;

public class AnalysisContext {
    public final List<CompilationUnitTree> units;
    public final Trees trees;
    public final Elements elements;
    public final Types types;

    public AnalysisContext(JavacTask task, Iterable<? extends CompilationUnitTree> units) {
        this.trees = Trees.instance(task);
        this.elements = task.getElements();
        this.types = task.getTypes();
        this.units = new ArrayList<>();
        for (CompilationUnitTree u : units) this.units.add(u);
    }

    public Element resolve(CompilationUnitTree cu, Tree node) {
        return trees.getElement(TreePath.getPath(cu, node));
    }
}
