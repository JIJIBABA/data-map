package com.datamap.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * MCP tool-call result: a list of content items ({type:"text", text}) plus isError flag.
 * Serialized into the JSON-RPC {@code result} of a tools/call response.
 */
public final class McpToolResult {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final List<ObjectNode> content;
    private final boolean isError;

    private McpToolResult(List<ObjectNode> content, boolean isError) {
        this.content = content;
        this.isError = isError;
    }

    /** A single text content item, isError=false. */
    public static McpToolResult text(String text) {
        List<ObjectNode> c = new ArrayList<>();
        c.add(textNode(text));
        return new McpToolResult(c, false);
    }

    /** Serialize any payload to a single JSON-text content item, isError=false. */
    public static McpToolResult fromPayload(Object payload) {
        try {
            String json = MAPPER.writeValueAsString(payload);
            return text(json);
        } catch (Exception e) {
            return error("Failed to serialize result: " + e.getMessage());
        }
    }

    /** A single text content item, isError=true. */
    public static McpToolResult error(String text) {
        List<ObjectNode> c = new ArrayList<>();
        c.add(textNode(text));
        return new McpToolResult(c, true);
    }

    private static ObjectNode textNode(String text) {
        ObjectNode node = MAPPER.createObjectNode();
        node.put("type", "text");
        node.put("text", text);
        return node;
    }

    public List<ObjectNode> getContent() {
        return Collections.unmodifiableList(content);
    }

    public boolean isError() {
        return isError;
    }
}
