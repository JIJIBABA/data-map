package com.datamap.mcp;

/** Thrown when tools/call names a tool not in the registry; -32601. */
public class McpUnknownToolException extends RuntimeException {
    public McpUnknownToolException(String message) {
        super(message);
    }
}
