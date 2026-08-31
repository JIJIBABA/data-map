package com.datamap.scanner.relation;

import com.datamap.scanner.entity.EntityResolver;
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

public class CrossEntityRelationExtractorTest {

    @Test
    public void extractsSetterGetterRelation() throws Exception {
        Path root = Paths.get("src/test/resources/fixtures/relation");
        List<Path> files;
        try (Stream<Path> s = Files.walk(root)) {
            files = s.filter(p -> p.toString().endsWith(".java")).collect(Collectors.toList());
        }
        AnalysisContext ctx = JavaParser.parse(files, "");
        Map<String, TypeElement> entities = EntityResolver.resolve(ctx);

        Map<String, List<ScanRelation>> relations = CrossEntityRelationExtractor.extract(ctx, entities, Map.of());

        assertTrue(relations.containsKey("tb_order"), "关联应按源表 tb_order 分组");
        List<ScanRelation> orderRels = relations.get("tb_order");
        assertEquals(1, orderRels.size());

        ScanRelation r = orderRels.get(0);
        assertEquals("user_id", r.sourceFieldName);
        assertEquals("tb_user", r.targetTableName);
        assertEquals("id", r.targetFieldName);
        assertEquals("DIRECT_JOIN", r.relationType);
        assertTrue(r.methodSignature.endsWith(".assemble"), "方法签名应指向 OrderAssembler.assemble");
    }
}
