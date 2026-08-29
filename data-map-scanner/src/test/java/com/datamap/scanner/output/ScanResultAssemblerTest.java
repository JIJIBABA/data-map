package com.datamap.scanner.output;

import com.datamap.scanner.javac.AnalysisContext;
import com.datamap.scanner.javac.JavaParser;
import com.datamap.scanner.model.ScanResult;
import com.datamap.scanner.model.ScanTable;
import com.datamap.scanner.model.UsageScenario;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

public class ScanResultAssemblerTest {

    private List<Path> files(Path root, String suffix) throws Exception {
        try (Stream<Path> s = Files.walk(root)) {
            return s.filter(p -> p.toString().endsWith(suffix)).collect(Collectors.toList());
        }
    }

    private ScanResult assemble() throws Exception {
        Path root = Paths.get("src/test/resources/fixtures/demo");
        AnalysisContext ctx = JavaParser.parse(files(root, ".java"), "");
        return ScanResultAssembler.assemble(ctx, files(root, ".xml"), "demo", "FULL", null, null);
    }

    @Test
    public void assemblesDemoEndToEnd() throws Exception {
        ScanResult result = assemble();

        assertEquals(1, result.tables.size());
        ScanTable tb = result.tables.get(0);
        assertEquals("tb_order", tb.tableName);

        assertTrue(tb.fields.stream().anyMatch(f -> f.fieldName.equals("order_status")));

        UsageScenario write = tb.usageScenarios.stream()
            .filter(u -> "WRITE".equals(u.operationType) && "order_status".equals(u.fieldName)
                && u.methodName != null && u.methodName.contains("createOrder"))
            .findFirst().orElse(null);
        assertNotNull(write, "应存在 order_status 的 WRITE 场景（createOrder）");

        UsageScenario update = tb.usageScenarios.stream()
            .filter(u -> "UPDATE".equals(u.operationType) && "order_status".equals(u.fieldName)
                && u.methodName != null && u.methodName.contains("cancelOrder"))
            .findFirst().orElse(null);
        assertNotNull(update, "应存在 order_status 的 UPDATE 场景（cancelOrder）");

        assertFalse(update.callChain.isEmpty(), "UPDATE 场景应有调用链");
        assertEquals("CONTROLLER", update.callChain.get(0).layer);
        assertNotNull(update.entry);
        assertEquals("CONTROLLER", update.entry.type);
    }
}
