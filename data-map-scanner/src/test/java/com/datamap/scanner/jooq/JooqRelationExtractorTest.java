package com.datamap.scanner.jooq;

import com.datamap.scanner.javac.AnalysisContext;
import com.datamap.scanner.javac.JavaParser;
import com.datamap.scanner.model.ScanRelation;
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

public class JooqRelationExtractorTest {
    private Map<String, List<ScanRelation>> extract() throws Exception {
        Path root = Paths.get("src/test/resources/fixtures/jooq");
        List<Path> files;
        try (Stream<Path> s = Files.walk(root)) {
            files = s.filter(p -> p.toString().endsWith(".java")).collect(Collectors.toList());
        }
        AnalysisContext ctx = JavaParser.parse(files, "");
        Map<String, TypeElement> tables = JooqTableResolver.resolve(ctx);
        return JooqRelationExtractor.extract(ctx, tables);
    }

    @Test
    public void extractsDirectJoinRelation() throws Exception {
        Map<String, List<ScanRelation>> relations = extract();

        assertTrue(relations.containsKey("T_ORDER"), "should group relations by source table T_ORDER");
        List<ScanRelation> rels = relations.get("T_ORDER");
        assertEquals(1, rels.size());

        ScanRelation r = rels.get(0);
        assertEquals("USER_ID", r.sourceFieldName);
        assertEquals("T_USER", r.targetTableName);
        assertEquals("USER_ID", r.targetFieldName);
        assertEquals("DIRECT_JOIN", r.relationType);
    }
}
