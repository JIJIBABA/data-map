package com.datamap.scanner.jooq;

import com.datamap.scanner.javac.AnalysisContext;
import com.datamap.scanner.javac.JavaParser;
import org.junit.jupiter.api.Test;
import javax.lang.model.element.TypeElement;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

public class JooqFieldAccessCollectorTest {
    private Map<String, List<JooqFieldAccess>> collect() throws Exception {
        Path root = Paths.get("src/test/resources/fixtures/jooq");
        List<Path> files;
        try (Stream<Path> s = Files.walk(root)) {
            files = s.filter(p -> p.toString().endsWith(".java")).collect(Collectors.toList());
        }
        AnalysisContext ctx = JavaParser.parse(files, "");
        Map<String, TypeElement> tables = JooqTableResolver.resolve(ctx);
        return JooqFieldAccessCollector.collect(ctx, tables);
    }

    @Test
    public void classifiesFieldAccessOperations() throws Exception {
        Map<String, List<JooqFieldAccess>> all = collect();

        assertTrue(all.containsKey("T_ORDER.ORDER_STATUS"), "should record ORDER_STATUS accesses");
        List<JooqFieldAccess> accesses = all.get("T_ORDER.ORDER_STATUS");
        assertEquals(3, accesses.size());

        assertTrue(accesses.stream().anyMatch(a -> a.operationType.equals("UPDATE")
                && a.method.getSimpleName().toString().equals("update")), "update() should be UPDATE");
        assertTrue(accesses.stream().anyMatch(a -> a.operationType.equals("READ")
                && a.method.getSimpleName().toString().equals("read")), "read() should be READ");
        assertTrue(accesses.stream().anyMatch(a -> a.operationType.equals("WRITE")
                && a.method.getSimpleName().toString().equals("write")), "write() should be WRITE");
    }

    @Test
    public void recordsTableLevelDelete() throws Exception {
        Map<String, List<JooqFieldAccess>> all = collect();

        assertTrue(all.containsKey("T_ORDER."), "should record table-level DELETE");
        List<JooqFieldAccess> deletes = all.get("T_ORDER.");
        assertEquals(1, deletes.size());
        JooqFieldAccess d = deletes.get(0);
        assertEquals("", d.fieldName);
        assertEquals("DELETE", d.operationType);
        assertEquals("delete", d.method.getSimpleName().toString());
    }
}
