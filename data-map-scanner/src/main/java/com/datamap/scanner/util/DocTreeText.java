package com.datamap.scanner.util;

import com.datamap.scanner.javac.AnalysisContext;
import com.sun.source.doctree.DocCommentTree;
import com.sun.source.doctree.DocTree;
import com.sun.source.util.DocTrees;
import com.sun.source.util.TreePath;

import javax.lang.model.element.Element;
import java.util.List;

/**
 * Javadoc 注释正文提取工具。
 *
 * <p>JDK 9+ 的 {@link DocCommentTree#getFullBody()} 和
 * {@link com.sun.source.util.DocTrees#getDocCommentTree(Element)} 在 JDK 8 均不存在：
 * <ul>
 *   <li>JDK 8 的 {@link DocCommentTree#getBody()} 返回 {@code List<? extends DocTree>}，遍历拼接得正文</li>
 *   <li>JDK 8 的 {@link DocTrees#getDocCommentTree} 只接受 {@link TreePath}，需先用
 *       {@code Trees.getPath(element)} 转换</li>
 * </ul>
 * 本类统一封装，兼容 JDK 8+。
 */
public final class DocTreeText {

    private DocTreeText() {}

    /** 取 Element 的 Javadoc 正文（去首尾空白），无注释返回空串。兼容 JDK 8（Element→TreePath 转换）。 */
    public static String fullBody(Element element, AnalysisContext ctx) {
        if (element == null || ctx == null) return "";
        TreePath path = ctx.trees.getPath(element);
        if (path == null) return "";
        DocTrees docTrees = DocTrees.instance(ctx.task);
        DocCommentTree doc = docTrees.getDocCommentTree(path);
        return fullBody(doc);
    }

    /** 返回 Javadoc 正文（去首尾空白），doc 为 null 或无正文时返回空串。 */
    public static String fullBody(DocCommentTree doc) {
        if (doc == null) return "";
        StringBuilder sb = new StringBuilder();
        List<? extends DocTree> body = doc.getBody();
        if (body != null) {
            for (DocTree t : body) {
                if (t == null) continue;
                String s = t.toString();
                if (s != null) sb.append(s);
            }
        }
        return sb.toString().trim();
    }
}
