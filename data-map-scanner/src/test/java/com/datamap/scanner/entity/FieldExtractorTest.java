package com.datamap.scanner.entity;

import com.datamap.scanner.javac.AnalysisContext;
import com.datamap.scanner.javac.JavaParser;
import com.datamap.scanner.model.ScanField;
import org.junit.jupiter.api.Test;
import javax.lang.model.element.TypeElement;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

public class FieldExtractorTest {
    private AnalysisContext ctx() throws Exception {
        Path root = Paths.get("src/test/resources/fixtures/demo");
        List<Path> files;
        try (Stream<Path> s = Files.walk(root)) {
            files = s.filter(p -> p.toString().endsWith(".java")).collect(Collectors.toList());
        }
        return JavaParser.parse(files, "");
    }

    @Test
    public void extractsOrderFieldsWithPkAndBusinessFlag() throws Exception {
        AnalysisContext ctx = ctx();
        TypeElement order = EntityResolver.resolve(ctx).get("tb_order").get(0);
        List<ScanField> fields = FieldExtractor.extract(order, ctx);
        assertEquals(4, fields.size());
        ScanField id = fields.stream().filter(f -> f.fieldName.equals("order_id")).findFirst().get();
        assertTrue(id.pk);
        ScanField status = fields.stream().filter(f -> f.fieldName.equals("order_status")).findFirst().get();
        assertTrue(status.businessField);
        assertFalse(fields.stream().filter(f -> f.fieldName.equals("created_at")).findFirst().get().businessField);
    }
}
