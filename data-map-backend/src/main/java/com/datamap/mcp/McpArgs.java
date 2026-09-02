package com.datamap.mcp;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * Hand-rolled argument accessors for {@link McpTool} implementations.
 * Throws {@link McpArgsException} on missing keys or type mismatches.
 */
final class McpArgs {

    private McpArgs() {}

    /** A required integer argument. */
    static long requireLong(JsonNode node, String key) {
        JsonNode v = node.get(key);
        if (v == null || v.isNull()) {
            throw new McpArgsException("Missing required argument '" + key + "'");
        }
        if (!v.isIntegralNumber()) {
            throw new McpArgsException("Argument '" + key + "' must be an integer");
        }
        return v.asLong();
    }

    /** An optional integer argument; null when absent. */
    static Long optionalLong(JsonNode node, String key) {
        JsonNode v = node.get(key);
        if (v == null || v.isNull()) {
            return null;
        }
        if (!v.isIntegralNumber()) {
            throw new McpArgsException("Argument '" + key + "' must be an integer");
        }
        return v.asLong();
    }

    /** An optional string argument; null when absent. */
    static String optionalString(JsonNode node, String key) {
        JsonNode v = node.get(key);
        if (v == null || v.isNull()) {
            return null;
        }
        if (!v.isTextual()) {
            throw new McpArgsException("Argument '" + key + "' must be a string");
        }
        return v.asText();
    }
}
