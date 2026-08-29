package com.datamap.scanner.callgraph;

import com.datamap.scanner.javac.AnalysisContext;
import com.datamap.scanner.javac.JavaParser;
import org.junit.jupiter.api.Test;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import java.nio.file.*;
import java.util.*;
import java.util.stream.*;

import static org.junit.jupiter.api.Assertions.*;

public class CallGraphBuilderTest {
    private AnalysisContext ctx() throws Exception {
        Path root = Paths.get("src/test/resources/fixtures/demo");
        List<Path> files;
        try (Stream<Path> s = Files.walk(root)) {
            files = s.filter(p -> p.toString().endsWith(".java")).collect(Collectors.toList());
        }
        return JavaParser.parse(files, "");
    }

    @Test
    public void cancelOrderHasCallerInController() throws Exception {
        AnalysisContext ctx = ctx();
        CallGraph g = CallGraphBuilder.build(ctx);
        ExecutableElement cancel = method(ctx, "com.example.service.impl.OrderServiceImpl", "cancelOrder");
        assertNotNull(cancel);
        // controller.cancel 调用了 service.cancelOrder（经接口展开到 impl）
        Set<ExecutableElement> callers = g.callers(cancel);
        assertFalse(callers.isEmpty());
        assertTrue(callers.stream().anyMatch(c ->
            c.getEnclosingElement().toString().contains("OrderController")));
    }

    private ExecutableElement method(AnalysisContext ctx, String className, String methodName) {
        TypeElement te = ctx.elements.getTypeElement(className);
        if (te == null) return null;
        for (javax.lang.model.element.Element e : ctx.elements.getAllMembers(te)) {
            if (e instanceof ExecutableElement && e.getSimpleName().contentEquals(methodName))
                return (ExecutableElement) e;
        }
        return null;
    }
}
