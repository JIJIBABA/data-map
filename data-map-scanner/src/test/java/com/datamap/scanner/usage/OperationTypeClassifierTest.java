package com.datamap.scanner.usage;

import com.datamap.scanner.callgraph.CallGraph;
import com.datamap.scanner.callgraph.CallGraphBuilder;
import com.datamap.scanner.entity.EntityResolver;
import com.datamap.scanner.javac.AnalysisContext;
import com.datamap.scanner.javac.JavaParser;
import org.junit.jupiter.api.Test;
import javax.lang.model.element.TypeElement;
import java.nio.file.*;
import java.util.*;
import java.util.stream.*;
import static org.junit.jupiter.api.Assertions.*;

public class OperationTypeClassifierTest {
    private AnalysisContext ctx() throws Exception {
        Path root = Paths.get("src/test/resources/fixtures/demo");
        List<Path> files;
        try (Stream<Path> s = Files.walk(root)) {
            files = s.filter(p -> p.toString().endsWith(".java")).collect(Collectors.toList());
        }
        return JavaParser.parse(files, "");
    }

    @Test
    public void createOrderStatusIsWrite() throws Exception {
        AnalysisContext ctx = ctx();
        CallGraph g = CallGraphBuilder.build(ctx);
        Map<String, TypeElement> entities = EntityResolver.resolve(ctx);
        Map<String, List<FieldAccess>> all = FieldAccessCollector.collect(ctx, entities);
        List<FieldAccess> accesses = all.get("tb_order.order_status");
        FieldAccess create = accesses.stream()
            .filter(a -> a.method.getSimpleName().contentEquals("createOrder")).findFirst().get();
        assertEquals("WRITE", OperationTypeClassifier.classify(create, g, ctx));
    }

    @Test
    public void cancelOrderStatusIsUpdate() throws Exception {
        AnalysisContext ctx = ctx();
        CallGraph g = CallGraphBuilder.build(ctx);
        Map<String, TypeElement> entities = EntityResolver.resolve(ctx);
        Map<String, List<FieldAccess>> all = FieldAccessCollector.collect(ctx, entities);
        List<FieldAccess> accesses = all.get("tb_order.order_status");
        FieldAccess cancel = accesses.stream()
            .filter(a -> a.method.getSimpleName().contentEquals("cancelOrder")).findFirst().get();
        assertEquals("UPDATE", OperationTypeClassifier.classify(cancel, g, ctx));
    }
}
