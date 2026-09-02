package com.datamap.mcp;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import org.junit.jupiter.api.Test;

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
}
