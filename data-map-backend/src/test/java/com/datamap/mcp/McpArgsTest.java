package com.datamap.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class McpArgsTest {

    private final ObjectMapper mapper = new ObjectMapper();

    private JsonNode json(String s) throws Exception {
        return mapper.readTree(s);
    }

    @Test
    void requireLong_returnsValue_whenPresentAndIntegral() throws Exception {
        JsonNode n = json("{\"fieldId\":349}");
        assertEquals(349L, McpArgs.requireLong(n, "fieldId"));
    }

    @Test
    void requireLong_throws_whenMissing() throws Exception {
        JsonNode n = json("{}");
        McpArgsException ex = assertThrows(McpArgsException.class, () -> McpArgs.requireLong(n, "fieldId"));
        assertTrue(ex.getMessage().contains("fieldId"));
        assertTrue(ex.getMessage().contains("required"));
    }

    @Test
    void requireLong_throws_whenWrongType() throws Exception {
        JsonNode n = json("{\"fieldId\":\"abc\"}");
        assertThrows(McpArgsException.class, () -> McpArgs.requireLong(n, "fieldId"));
    }

    @Test
    void optionalString_returnsNull_whenMissing() throws Exception {
        JsonNode n = json("{}");
        assertNull(McpArgs.optionalString(n, "keyword"));
    }

    @Test
    void optionalString_returnsValue_whenPresent() throws Exception {
        JsonNode n = json("{\"keyword\":\"order\"}");
        assertEquals("order", McpArgs.optionalString(n, "keyword"));
    }

    @Test
    void optionalLong_returnsNull_whenMissing() throws Exception {
        JsonNode n = json("{}");
        assertNull(McpArgs.optionalLong(n, "projectId"));
    }

    @Test
    void optionalLong_throws_whenPresentButWrongType() throws Exception {
        JsonNode n = json("{\"projectId\":\"x\"}");
        assertThrows(McpArgsException.class, () -> McpArgs.optionalLong(n, "projectId"));
    }
}
