package com.datamap.scanner.usage;

import com.datamap.scanner.callgraph.CallGraph;
import com.datamap.scanner.javac.AnalysisContext;
import com.sun.source.tree.*;
import com.sun.source.util.TreeScanner;

import javax.lang.model.element.ExecutableElement;
import java.util.*;

public class OperationTypeClassifier {
    public static String classify(FieldAccess access, CallGraph graph, AnalysisContext ctx) {
        if (access.kind == FieldAccess.Kind.READ) return "READ";
        // 从 access.method 沿调用图向下(callees) BFS，收集 mapper 方法名
        Set<String> names = new HashSet<>();
        Deque<ExecutableElement> queue = new ArrayDeque<>();
        Set<ExecutableElement> seen = new HashSet<>();
        queue.add(access.method);
        int depth = 0;
        while (!queue.isEmpty() && depth++ <= 5) {
            int size = queue.size();
            for (int i = 0; i < size; i++) {
                ExecutableElement m = queue.poll();
                if (!seen.add(m)) continue;
                names.addAll(mapperCallsIn(m, ctx));
                for (ExecutableElement callee : graph.callees(m)) queue.add(callee);
            }
        }
        if (names.contains("insert") || names.contains("save")) return "WRITE";
        if (names.contains("updateById") || names.contains("update")) return "UPDATE";
        if (names.contains("deleteById") || names.contains("delete")
                || names.contains("removeById") || names.contains("remove")) return "DELETE";
        return "UNRESOLVED";
    }

    private static final Set<String> MAPPER_METHODS = Set.of(
        "insert","save","updateById","update","updateBatchById","deleteById","delete",
        "removeById","remove","selectById","selectOne","selectList","getById");

    /** 扫描单个方法方法体的 mapper 方法名。 */
    private static Set<String> mapperCallsIn(ExecutableElement method, AnalysisContext ctx) {
        Set<String> names = new HashSet<>();
        Tree tree = ctx.trees.getTree(method);
        if (!(tree instanceof MethodTree)) return names;
        BlockTree body = ((MethodTree) tree).getBody();
        if (body == null) return names;
        body.accept(new TreeScanner<Void, Void>() {
            @Override public Void visitMethodInvocation(MethodInvocationTree node, Void p) {
                String name = node.getMethodSelect().toString();
                int i = name.lastIndexOf('.');
                String simple = i >= 0 ? name.substring(i + 1) : name;
                if (MAPPER_METHODS.contains(simple)) names.add(simple);
                return super.visitMethodInvocation(node, p);
            }
        }, null);
        return names;
    }
}
