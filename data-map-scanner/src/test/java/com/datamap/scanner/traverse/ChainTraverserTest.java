package com.datamap.scanner.traverse;

import com.datamap.scanner.callgraph.CallGraph;
import com.datamap.scanner.callgraph.CallGraphBuilder;
import com.datamap.scanner.entry.EntryPoint;
import com.datamap.scanner.entry.EntryPointResolver;
import com.datamap.scanner.javac.AnalysisContext;
import com.datamap.scanner.javac.JavaParser;
import org.junit.jupiter.api.Test;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import java.nio.file.*;
import java.util.*;
import java.util.stream.*;

import static org.junit.jupiter.api.Assertions.*;

public class ChainTraverserTest {
    private AnalysisContext ctx() throws Exception {
        Path root = Paths.get("src/test/resources/fixtures/demo");
        List<Path> files;
        try (Stream<Path> s = Files.walk(root)) {
            files = s.filter(p -> p.toString().endsWith(".java")).collect(Collectors.toList());
        }
        return JavaParser.parse(files, "");
    }

    private ExecutableElement method(AnalysisContext ctx, String className, String methodName) {
        TypeElement te = ctx.elements.getTypeElement(className);
        for (javax.lang.model.element.Element e : ctx.elements.getAllMembers(te)) {
            if (e instanceof ExecutableElement && e.getSimpleName().contentEquals(methodName))
                return (ExecutableElement) e;
        }
        return null;
    }

    @Test
    public void tracesCancelOrderUpToController() throws Exception {
        AnalysisContext ctx = ctx();
        CallGraph g = CallGraphBuilder.build(ctx);
        Set<ExecutableElement> entries = EntryPointResolver.resolve(ctx).stream()
            .map(ep -> ep.method).collect(Collectors.toSet());
        ExecutableElement cancel = method(ctx, "com.example.service.impl.OrderServiceImpl", "cancelOrder");
        List<List<ExecutableElement>> paths = ChainTraverser.traverse(cancel, g, entries, 10);
        assertFalse(paths.isEmpty());
        List<ExecutableElement> first = paths.get(0);
        assertTrue(first.get(0).getEnclosingElement().toString().contains("OrderController"));
        assertTrue(first.get(first.size() - 1).getSimpleName().contentEquals("cancelOrder"));
    }
}
