package com.datamap.scanner.usage;

import com.datamap.scanner.entity.EntityResolver;
import com.datamap.scanner.javac.AnalysisContext;
import com.datamap.scanner.javac.JavaParser;
import org.junit.jupiter.api.Test;
import javax.lang.model.element.TypeElement;
import java.nio.file.*;
import java.util.*;
import java.util.stream.*;
import static org.junit.jupiter.api.Assertions.*;

public class FieldAccessCollectorTest {
    private Map<String, List<FieldAccess>> collect() throws Exception {
        Path root = Paths.get("src/test/resources/fixtures/demo");
        List<Path> files;
        try (Stream<Path> s = Files.walk(root)) {
            files = s.filter(p -> p.toString().endsWith(".java")).collect(Collectors.toList());
        }
        AnalysisContext ctx = JavaParser.parse(files, "");
        Map<String, TypeElement> entities = EntityResolver.resolve(ctx);
        return FieldAccessCollector.collect(ctx, entities);
    }

    @Test
    public void findsWriteOfOrderStatus() throws Exception {
        Map<String, List<FieldAccess>> all = collect();
        assertTrue(all.containsKey("tb_order.order_status"));
        List<FieldAccess> accesses = all.get("tb_order.order_status");
        assertEquals(2, accesses.size()); // createOrder 与 cancelOrder 各写一次
        assertTrue(accesses.stream().allMatch(a -> a.kind == FieldAccess.Kind.WRITE));
    }
}
