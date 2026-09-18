package com.datamap.scanner.usage;

import com.datamap.scanner.callgraph.CallGraph;
import com.datamap.scanner.javac.AnalysisContext;
import com.sun.source.tree.*;
import com.sun.source.util.TreeScanner;

import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.VariableElement;
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
        if (names.contains("updateById") || names.contains("update") || names.contains("updateBatchById")) return "UPDATE";
        if (names.contains("deleteById") || names.contains("delete")
                || names.contains("removeById") || names.contains("remove")) return "DELETE";
        return "UNRESOLVED";
    }

    private static final Set<String> MAPPER_METHODS = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
        "insert","save","updateById","update","updateBatchById","deleteById","delete",
        "removeById","remove","selectById","selectOne","selectList","getById")));

    /** 扫描单个方法方法体的 mapper 方法名（仅当接收者为 Mapper/Dao 类型时计入）。 */
    private static Set<String> mapperCallsIn(ExecutableElement method, AnalysisContext ctx) {
        Set<String> names = new HashSet<>();
        Tree tree = ctx.trees.getTree(method);
        if (!(tree instanceof MethodTree)) return names;
        BlockTree body = ((MethodTree) tree).getBody();
        if (body == null) return names;
        CompilationUnitTree cu = ctx.trees.getPath(method).getCompilationUnit();
        body.accept(new TreeScanner<Void, Void>() {
            @Override public Void visitMethodInvocation(MethodInvocationTree node, Void p) {
                ExpressionTree select = node.getMethodSelect();
                if (select instanceof MemberSelectTree) {
                    MemberSelectTree mst = (MemberSelectTree) select;
                    String simple = mst.getIdentifier().toString();
                    if (MAPPER_METHODS.contains(simple) && isMapperReceiver(mst.getExpression(), cu, ctx)) {
                        names.add(simple);
                    }
                }
                return super.visitMethodInvocation(node, p);
            }
        }, null);
        return names;
    }

    /** 接收者表达式解析为 Mapper/Dao 类型的字段或变量时才认定是 mapper 调用。 */
    private static boolean isMapperReceiver(ExpressionTree receiver, CompilationUnitTree cu, AnalysisContext ctx) {
        if (receiver == null) return false;
        Element e = ctx.resolve(cu, receiver);
        if (!(e instanceof VariableElement)) return false;
        String type = ((VariableElement) e).asType().toString().toLowerCase();
        return type.endsWith("mapper") || type.endsWith("dao");
    }
}
