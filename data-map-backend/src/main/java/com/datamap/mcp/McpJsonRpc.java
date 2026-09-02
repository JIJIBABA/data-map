package com.datamap.mcp;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Builds JSON-RPC 2.0 response objects for the MCP endpoint.
 */
final class McpJsonRpc {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private McpJsonRpc() {}

    /** A successful result response: {jsonrpc:"2.0", id, result}. */
    static ObjectNode result(String id, ObjectNode result) {
        ObjectNode resp = MAPPER.createObjectNode();
        resp.put("jsonrpc", "2.0");
        if (id == null) {
            resp.putNull("id");
        } else {
            resp.put("id", id);
        }
        resp.set("result", result);
        return resp;
    }

    /** An error response with an id: {jsonrpc:"2.0", id, error:{code,message}}. */
    static ObjectNode error(String id, int code, String message) {
        ObjectNode resp = MAPPER.createObjectNode();
        resp.put("jsonrpc", "2.0");
        if (id == null) {
            resp.putNull("id");
        } else {
            resp.put("id", id);
        }
        ObjectNode err = MAPPER.createObjectNode();
        err.put("code", code);
        err.put("message", message);
        resp.set("error", err);
        return resp;
    }

    /** An error response with null id (parse errors / notifications). */
    static ObjectNode error(int code, String message) {
        return error(null, code, message);
    }
}
