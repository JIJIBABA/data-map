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
            String text = doc.getFullBody().toString().trim();
            // method_description 列 VARCHAR(512)，超长截断避免入库失败
            if (text.length() > 500) text = text.substring(0, 500);
            return new String[]{ text, "COMMENT" };
        }
        return new String[]{ "", "NONE" };
    }
}
