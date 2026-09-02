package com.datamap.mcp;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class McpJsonRpcTest {

    @Test
    void result_wrapsPayloadWithIdAndJsonrpc() {
        ObjectNode payload = JsonNodeFactory.instance.objectNode().put("ok", true);
        ObjectNode resp = McpJsonRpc.result("42", payload);
        assertEquals("2.0", resp.get("jsonrpc").asText());
        assertEquals("42", resp.get("id").asText());
        assertTrue(resp.get("result").get("ok").asBoolean());
        assertNull(resp.get("error"));
    }

    @Test
    void error_withId_includesCodeAndMessage() {
        ObjectNode resp = McpJsonRpc.error("7", -32601, "method not found");
        assertEquals("2.0", resp.get("jsonrpc").asText());
        assertEquals("7", resp.get("id").asText());
        assertEquals(-32601, resp.get("error").get("code").asInt());
        assertEquals("method not found", resp.get("error").get("message").asText());
        assertNull(resp.get("result"));
    }

    @Test
    void error_nullId_emitsNullId() {
        ObjectNode resp = McpJsonRpc.error(-32700, "parse error");
        assertTrue(resp.get("id").isNull());
        assertEquals(-32700, resp.get("error").get("code").asInt());
    }
}
