package com.datamap.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class McpToolRegistryTest {

    private McpTool tool(String name) {
        return new McpTool() {
            @Override public String name() { return name; }
            @Override public String description() { return "d"; }
            @Override public Map<String, Object> inputSchema() { return java.util.Collections.emptyMap(); }
            @Override public McpToolResult execute(JsonNode arguments) { return McpToolResult.text("ok"); }
        };
    }

    @Test
    void findByName_returnsPresent_whenRegistered() {
        McpToolRegistry reg = new McpToolRegistry(Arrays.asList(tool("a"), tool("b")));
        Optional<McpTool> found = reg.findByName("b");
        assertTrue(found.isPresent());
        assertEquals("b", found.get().name());
    }

    @Test
    void findByName_returnsEmpty_whenMissing() {
        McpToolRegistry reg = new McpToolRegistry(Arrays.asList(tool("a")));
        assertFalse(reg.findByName("nope").isPresent());
    }

    @Test
    void list_preservesInsertionOrder() {
        McpToolRegistry reg = new McpToolRegistry(Arrays.asList(tool("z"), tool("a"), tool("m")));
        assertEquals(java.util.Arrays.asList("z", "a", "m"),
                reg.list().stream().map(McpTool::name).collect(java.util.stream.Collectors.toList()));
    }
}
