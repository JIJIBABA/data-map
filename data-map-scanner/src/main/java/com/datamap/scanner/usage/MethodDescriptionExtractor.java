package com.datamap.scanner.usage;

import com.datamap.scanner.javac.AnalysisContext;
import com.datamap.scanner.util.DocTreeText;

import javax.lang.model.element.ExecutableElement;

public class MethodDescriptionExtractor {
    public static String[] extract(ExecutableElement method, AnalysisContext ctx) {
        String text = DocTreeText.fullBody(method, ctx);
        if (!text.isEmpty()) {
            // method_description 列 VARCHAR(512)，超长截断避免入库失败
            if (text.length() > 500) text = text.substring(0, 500);
            return new String[]{ text, "COMMENT" };
        }
        return new String[]{ "", "NONE" };
    }
}
