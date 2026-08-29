package com.datamap.scanner.usage;

import com.datamap.scanner.javac.AnalysisContext;
import com.sun.source.doctree.DocCommentTree;
import com.sun.source.util.DocTrees;

import javax.lang.model.element.ExecutableElement;

public class MethodDescriptionExtractor {
    public static String[] extract(ExecutableElement method, AnalysisContext ctx) {
        DocTrees docTrees = DocTrees.instance(ctx.task);
        DocCommentTree doc = docTrees.getDocCommentTree(method);
        if (doc != null && !doc.getFullBody().isEmpty()) {
            return new String[]{ doc.getFullBody().toString().trim(), "COMMENT" };
        }
        return new String[]{ "", "NONE" };
    }
}
