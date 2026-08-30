package com.datamap.scanner.jooq;

import com.datamap.scanner.javac.AnalysisContext;
import com.datamap.scanner.javac.JavaParser;
import com.datamap.scanner.model.ScanField;
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

public class JooqFieldExtractorTest {
    private AnalysisContext ctx() throws Exception {
        Path root = Paths.get("src/test/resources/fixtures/jooq");
        List<Path> files;
        try (Stream<Path> s = Files.walk(root)) {
            files = s.filter(p -> p.toString().endsWith(".java")).collect(Collectors.toList());
        }
        return JavaParser.parse(files, "");
    }

    @Test
    public void extractsJooqFieldsWithTypeCommentAndPk() throws Exception {
        AnalysisContext ctx = ctx();
        Map<String, TypeElement> tables = JooqTableResolver.resolve(ctx);
        TypeElement tOrder = tables.get("T_ORDER");
        List<ScanField> fields = JooqFieldExtractor.extract(tOrder, ctx);

        assertEquals(2, fields.size());

        ScanField seqNo = fields.stream().filter(f -> f.fieldName.equals("SEQUENCE_NO")).findFirst().get();
        assertEquals("BIGINT", seqNo.fieldType);
        assertEquals("顺序号", seqNo.fieldComment);
        assertTrue(seqNo.pk, "SEQUENCE_NO should be PK");
        assertTrue(seqNo.businessField);

        ScanField orderStatus = fields.stream().filter(f -> f.fieldName.equals("ORDER_STATUS")).findFirst().get();
        assertEquals("VARCHAR", orderStatus.fieldType);
        assertEquals("状态", orderStatus.fieldComment);
        assertFalse(orderStatus.pk, "ORDER_STATUS should not be PK");
    }
}
