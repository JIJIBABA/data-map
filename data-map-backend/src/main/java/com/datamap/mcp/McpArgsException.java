package com.datamap.mcp;

/** Thrown when a tool's arguments fail validation; turned into a JSON-RPC -32602 error. */
public class McpArgsException extends RuntimeException {
    public McpArgsException(String message) {
        super(message);
    }
}
