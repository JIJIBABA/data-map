package com.datamap.scanner.entry;

import com.datamap.scanner.javac.AnalysisContext;
import com.datamap.scanner.javac.JavaParser;
import org.junit.jupiter.api.Test;
import java.nio.file.*;
import java.util.*;
import java.util.stream.*;
import static org.junit.jupiter.api.Assertions.*;

public class EntryPointResolverTest {
    private Set<EntryPoint> resolve() throws Exception {
        Path root = Paths.get("src/test/resources/fixtures/demo");
        List<Path> files;
        try (Stream<Path> s = Files.walk(root)) {
            files = s.filter(p -> p.toString().endsWith(".java")).collect(Collectors.toList());
        }
        return EntryPointResolver.resolve(JavaParser.parse(files, ""));
    }

    @Test
    public void findsControllerEntryWithPath() throws Exception {
        Set<EntryPoint> entries = resolve();
        assertEquals(1, entries.size());
        EntryPoint ep = entries.iterator().next();
        assertEquals("CONTROLLER", ep.info.type);
        assertEquals("/api/order/cancel", ep.info.path);
        assertEquals("POST", ep.info.httpMethod);
    }
}
