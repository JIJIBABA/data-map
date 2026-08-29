package com.datamap.scanner.entry;

import com.datamap.scanner.javac.AnalysisContext;
import com.datamap.scanner.model.EntryInfo;
import com.sun.source.tree.*;
import com.sun.source.util.TreePathScanner;

import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import java.util.*;

public class EntryPointResolver {
    public static Set<EntryPoint> resolve(AnalysisContext ctx) {
        Set<EntryPoint> out = new LinkedHashSet<>();
        for (CompilationUnitTree cu : ctx.units) {
            new TreePathScanner<Void, Void>() {
                @Override public Void visitClass(ClassTree node, Void p) {
                    String classAnno = classAnnotation(node);
                    if (classAnno == null) { scan(node.getMembers(), p); return null; }
                    boolean isController = classAnno.equals("RestController") || classAnno.equals("Controller");
                    String basePath = pathValue(node, "RequestMapping");
                    for (Tree member : node.getMembers()) {
                        if (!(member instanceof MethodTree)) continue;
                        MethodTree mt = (MethodTree) member;
                        String methodAnno = methodAnnotation(mt);
                        if (methodAnno == null) continue;
                        ExecutableElement m = methodElement(ctx, cu, mt);
                        if (m == null) continue;
                        if (isController && isHttpMapping(methodAnno)) {
                            String path = basePath + pathValue(mt, methodAnno);
                            String httpMethod = methodAnno.replace("Mapping", "").toUpperCase();
                            out.add(new EntryPoint(m, new EntryInfo("CONTROLLER",
                                className(m), httpMethod, path, null, null)));
                        } else if (isMq(methodAnno)) {
                            out.add(new EntryPoint(m, new EntryInfo("MQ", className(m), null, null, null, null)));
                        } else if (methodAnno.equals("Scheduled")) {
                            out.add(new EntryPoint(m, new EntryInfo("SCHEDULED", className(m), null, null, null, null)));
                        }
                    }
                    return null;
                }
            }.scan(cu, null);
        }
        return out;
    }

    private static boolean isHttpMapping(String anno) {
        return anno.equals("RequestMapping") || anno.equals("GetMapping") || anno.equals("PostMapping")
            || anno.equals("PutMapping") || anno.equals("DeleteMapping");
    }
    private static boolean isMq(String anno) {
        return anno.equals("RabbitListener") || anno.equals("KafkaListener")
            || anno.equals("RocketMQMessageListener") || anno.equals("JmsListener");
    }

    private static String classAnnotation(ClassTree node) {
        if (node.getModifiers() == null || node.getModifiers().getAnnotations() == null) return null;
        for (AnnotationTree a : node.getModifiers().getAnnotations()) return simple(a.getAnnotationType().toString());
        return null;
    }
    private static String methodAnnotation(MethodTree node) {
        if (node.getModifiers() == null || node.getModifiers().getAnnotations() == null) return null;
        for (AnnotationTree a : node.getModifiers().getAnnotations()) return simple(a.getAnnotationType().toString());
        return null;
    }
    private static String pathValue(Tree node, String annoName) {
        List<? extends AnnotationTree> anns;
        if (node instanceof ClassTree) anns = ((ClassTree) node).getModifiers().getAnnotations();
        else anns = ((MethodTree) node).getModifiers().getAnnotations();
        for (AnnotationTree a : anns) {
            if (!simple(a.getAnnotationType().toString()).equals(annoName)) continue;
            for (ExpressionTree arg : a.getArguments()) {
                // javac 将单值注解参数（@Ann("x")）表示为 AssignmentTree（value = "x"）
                ExpressionTree expr = arg instanceof AssignmentTree ? ((AssignmentTree) arg).getExpression() : arg;
                if (expr instanceof LiteralTree) return String.valueOf(((LiteralTree) expr).getValue());
            }
        }
        return "";
    }
    private static String simple(String q) { int i = q.lastIndexOf('.'); return i >= 0 ? q.substring(i + 1) : q; }

    private static ExecutableElement methodElement(AnalysisContext ctx, CompilationUnitTree cu, MethodTree mt) {
        Element e = ctx.resolve(cu, mt);
        return e instanceof ExecutableElement ? (ExecutableElement) e : null;
    }
    private static String className(ExecutableElement m) {
        Element enc = m.getEnclosingElement();
        return enc.toString(); // 全限定类名
    }
}
