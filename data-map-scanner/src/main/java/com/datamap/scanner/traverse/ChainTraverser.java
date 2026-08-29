package com.datamap.scanner.traverse;

import com.datamap.scanner.callgraph.CallGraph;
import javax.lang.model.element.ExecutableElement;
import java.util.*;

public class ChainTraverser {
    /** 从 start 向上找所有到达入口的路径。每个路径[0] 是入口，[last] 是 start。 */
    public static List<List<ExecutableElement>> traverse(ExecutableElement start, CallGraph graph,
                                                         Set<ExecutableElement> entries, int maxDepth) {
        List<List<ExecutableElement>> paths = new ArrayList<>();
        Deque<ExecutableElement> stack = new ArrayDeque<>();
        dfs(start, graph, entries, maxDepth, stack, paths, new HashSet<>());
        return paths;
    }

    private static void dfs(ExecutableElement current, CallGraph graph, Set<ExecutableElement> entries,
                            int maxDepth, Deque<ExecutableElement> stack,
                            List<List<ExecutableElement>> paths, Set<ExecutableElement> visiting) {
        stack.push(current);
        if (entries.contains(current)) {
            // ArrayDeque 的迭代顺序为 head→tail（push 即 addFirst），
            // 越靠近入口越晚入栈，因此 head 即入口，天然是“入口在前”。
            paths.add(new ArrayList<>(stack));
        } else if (stack.size() < maxDepth && visiting.add(current)) {
            for (ExecutableElement caller : graph.callers(current)) {
                dfs(caller, graph, entries, maxDepth, stack, paths, visiting);
            }
            visiting.remove(current);
        }
        stack.pop();
    }
}
