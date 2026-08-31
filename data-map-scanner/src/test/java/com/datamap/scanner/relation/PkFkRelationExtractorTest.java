package com.datamap.scanner.relation;

import com.datamap.scanner.model.ScanField;
import com.datamap.scanner.model.ScanRelation;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class PkFkRelationExtractorTest {

    @Test
    public void matchesPkFkBySuffix() {
        Map<String, List<ScanField>> tables = Map.of(
            "tb_a", List.of(new ScanField("pk_param", "", "BIGINT", true, true)),
            "tb_b", List.of(new ScanField("fk_param", "", "BIGINT", false, true))
        );

        Map<String, List<ScanRelation>> relations = PkFkRelationExtractor.extract(tables);

        assertTrue(relations.containsKey("tb_b"), "FK 表应作为源表");
        assertEquals(1, relations.get("tb_b").size());
        ScanRelation r = relations.get("tb_b").get(0);
        assertEquals("fk_param", r.sourceFieldName);
        assertEquals("tb_a", r.targetTableName);
        assertEquals("pk_param", r.targetFieldName);
        assertEquals("DIRECT_JOIN", r.relationType);
    }

    @Test
    public void matchesCaseInsensitively() {
        Map<String, List<ScanField>> tables = Map.of(
            "T_A", List.of(new ScanField("PK_PARAM", "", "BIGINT", true, true)),
            "T_B", List.of(new ScanField("FK_PARAM", "", "BIGINT", false, true))
        );

        Map<String, List<ScanRelation>> relations = PkFkRelationExtractor.extract(tables);

        assertTrue(relations.containsKey("T_B"));
        assertEquals("FK_PARAM", relations.get("T_B").get(0).sourceFieldName);
        assertEquals("T_A", relations.get("T_B").get(0).targetTableName);
        assertEquals("PK_PARAM", relations.get("T_B").get(0).targetFieldName);
    }

    @Test
    public void noMatchWithoutFk() {
        Map<String, List<ScanField>> tables = Map.of(
            "tb_a", List.of(new ScanField("pk_param", "", "BIGINT", true, true)),
            "tb_b", List.of(new ScanField("param", "", "BIGINT", false, true))
        );

        Map<String, List<ScanRelation>> relations = PkFkRelationExtractor.extract(tables);

        assertTrue(relations.isEmpty());
    }
}
