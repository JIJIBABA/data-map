package com.datamap.scanner.output;

import com.datamap.scanner.model.*;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

public class JsonWriterTest {
    @Test
    public void serializesPublicFields() throws Exception {
        ScanResult r = new ScanResult(new ScanProject("demo", "", ""), "FULL",
            List.of(new ScanTable("tb_order", "", "", "MYSQL", List.of(), List.of(), List.of())));
        String json = JsonWriter.toJson(r);
        assertTrue(json.contains("\"appName\""));
        assertTrue(json.contains("\"tableName\""));
    }
}
