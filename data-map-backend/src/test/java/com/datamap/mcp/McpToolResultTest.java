package com.datamap.mcp;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class McpToolResultTest {

    @Test
    void textResult_holdsContentAndIsErrorFalse() {
        McpToolResult r = McpToolResult.text("hello");
        assertFalse(r.isError());
        assertEquals(1, r.getContent().size());
        assertEquals("hello", r.getContent().get(0).get("text").asText());
        assertEquals("text", r.getContent().get(0).get("type").asText());
    }

    @Test
    void errorResult_setsIsErrorTrue() {
        McpToolResult r = McpToolResult.error("boom");
        assertTrue(r.isError());
        assertEquals("boom", r.getContent().get(0).get("text").asText());
    }

    @Test
    void fromPayload_serializesObjectAsJsonText() {
        McpToolResult r = McpToolResult.fromPayload(JsonNodeFactory.instance.objectNode().put("k", "v"));
        assertEquals("{\"k\":\"v\"}", r.getContent().get(0).get("text").asText());
        assertFalse(r.isError());
    }

    @Test
    void fromPayload_serializesLocalDateTimeAsIso8601() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("createdAt", LocalDateTime.of(2026, 9, 2, 15, 30, 45));
        McpToolResult r = McpToolResult.fromPayload(payload);
        assertFalse(r.isError());
        String json = r.getContent().get(0).get("text").asText();
        assertTrue(json.contains("\"2026-09-02T15:30:45\""),
            "LocalDateTime should serialize as ISO-8601 text, got: " + json);
    }
}
