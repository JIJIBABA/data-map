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

public class JooqTableResolverTest {
    private AnalysisContext ctx() throws Exception {
        Path root = Paths.get("src/test/resources/fixtures/jooq");
        List<Path> files;
        try (Stream<Path> s = Files.walk(root)) {
            files = s.filter(p -> p.toString().endsWith(".java")).collect(Collectors.toList());
        }
        return JavaParser.parse(files, "");
    }

    @Test
    public void resolvesJooqTableByTableImplExtends() throws Exception {
        Map<String, TypeElement> map = JooqTableResolver.resolve(ctx());
        assertTrue(map.containsKey("T_ORDER"), "should discover T_ORDER table");
        assertEquals("T_ORDER", map.get("T_ORDER").getSimpleName().toString());
    }
}
