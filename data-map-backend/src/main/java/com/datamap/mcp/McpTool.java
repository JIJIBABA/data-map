package com.datamap.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.Map;

/**
 * One MCP tool: a name, human description, a JSON-Schema input schema,
 * and an executor that turns the arguments node into a {@link McpToolResult}.
 */
public interface McpTool {

    String name();

    String description();

    /**
     * JSON Schema for the tool's arguments, as a Map suitable for Jackson serialization.
     * Example: {@code {"type":"object","properties":{"fieldId":{"type":"integer"}},"required":["fieldId"]}}.
     */
    Map<String, Object> inputSchema();

    McpToolResult execute(JsonNode arguments);
}
