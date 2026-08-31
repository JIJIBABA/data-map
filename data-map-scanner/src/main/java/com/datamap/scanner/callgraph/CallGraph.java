package com.datamap.scanner.callgraph;

import javax.lang.model.element.ExecutableElement;
import java.util.*;

public class CallGraph {
    private final Map<ExecutableElement, Set<ExecutableElement>> callers = new HashMap<>();
    private final Map<ExecutableElement, Set<ExecutableElement>> callees = new HashMap<>();

    public void add(ExecutableElement caller, ExecutableElement callee) {
        callers.computeIfAbsent(callee, k -> new LinkedHashSet<>()).add(caller);
        callees.computeIfAbsent(caller, k -> new LinkedHashSet<>()).add(callee);
    }

    public Set<ExecutableElement> callers(ExecutableElement callee) {
        return callers.getOrDefault(callee, Collections.emptySet());
    }

    public Set<ExecutableElement> callees(ExecutableElement caller) {
        return callees.getOrDefault(caller, Collections.emptySet());
    }
}
