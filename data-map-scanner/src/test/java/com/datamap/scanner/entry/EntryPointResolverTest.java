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

    @Test
    public void matchesControllerWhenNonMatchingAnnotationComesFirst() throws Exception {
        Path dir = Files.createTempDirectory("entrypoint-resolver");
        Path src = dir.resolve("C.java");
        String code =
            "@interface Marker {}\n" +
            "@interface RestController {}\n" +
            "@interface RequestMapping { String value() default \"\"; }\n" +
            "@interface PostMapping { String value() default \"\"; }\n" +
            "@Marker @RestController @RequestMapping(\"/x\")\n" +
            "class C { @PostMapping(\"/p\") void m() {} }\n";
        Files.writeString(src, code);

        Set<EntryPoint> entries = EntryPointResolver.resolve(JavaParser.parse(List.of(src), ""));

        assertEquals(1, entries.size());
        EntryPoint ep = entries.iterator().next();
        assertEquals("CONTROLLER", ep.info.type);
        assertEquals("/x/p", ep.info.path);
        assertEquals("POST", ep.info.httpMethod);
    }

    @Test
    public void detectsXxlJobEntries() throws Exception {
        Path dir = Files.createTempDirectory("entrypoint-xxljob");
        Path src = dir.resolve("X.java");
        String code =
            "@interface JobHandler { String value() default \"\"; }\n" +
            "@interface XxlJob { String value() default \"\"; }\n" +
            "@JobHandler(\"myHandler\")\n" +
            "class MyJobHandler { public void execute(String param) {} }\n" +
            "class MyTask { @XxlJob(\"myJob\") public void run() {} }\n";
        Files.writeString(src, code);

        Set<EntryPoint> entries = EntryPointResolver.resolve(JavaParser.parse(List.of(src), ""));

        assertEquals(2, entries.size());
        EntryPoint handler = entries.stream().filter(e -> "myHandler".equals(e.info.path)).findFirst().orElse(null);
        assertNotNull(handler);
        assertEquals("SCHEDULED", handler.info.type);
        assertEquals("execute", handler.method.getSimpleName().toString());
        EntryPoint job = entries.stream().filter(e -> "myJob".equals(e.info.path)).findFirst().orElse(null);
        assertNotNull(job);
        assertEquals("SCHEDULED", job.info.type);
    }
}
