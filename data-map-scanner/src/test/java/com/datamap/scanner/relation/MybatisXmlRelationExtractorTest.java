package com.datamap.scanner.relation;

import com.datamap.scanner.model.ScanRelation;
import org.junit.jupiter.api.Test;
import java.nio.file.Paths;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

public class MybatisXmlRelationExtractorTest {
    @Test
    public void extractsJoinRelation() throws Exception {
        List<ScanRelation> rels = MybatisXmlRelationExtractor.extract(
            Paths.get("src/test/resources/fixtures/demo/mapper/OrderMapper.xml"));
        assertEquals(1, rels.size());
        assertEquals("user_id", rels.get(0).sourceFieldName);
        assertEquals("tb_user", rels.get(0).targetTableName);
        assertEquals("user_id", rels.get(0).targetFieldName);
        assertEquals("DIRECT_JOIN", rels.get(0).relationType);
    }
}
