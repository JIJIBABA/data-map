package com.datamap.mcp;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class McpControllerTest {

    private McpToolRegistry registry;
    private McpController controller;
    private MockMvc mvc;
    private final ObjectMapper mapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        registry = mock(McpToolRegistry.class);
        controller = new McpController(registry);
        mvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    /** Java-8-compatible ordered key/value map builder (replaces Map.of, which is Java 9+). */
    private static Map<String, Object> map(Object... kv) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            m.put((String) kv[i], kv[i + 1]);
        }
        return m;
    }

    private String rpc(String id, String method, Object params) throws Exception {
        ObjectNode req = mapper.createObjectNode();
        req.put("jsonrpc", "2.0");
        if (id != null) req.put("id", id);
        req.put("method", method);
        if (params != null) req.set("params", mapper.valueToTree(params));
        return req.toString();
    }

    @Test
    void initialize_returnsServerInfoAndToolsCapability() throws Exception {
        mvc.perform(post("/mcp")
                        .contentType("application/json")
                        .content(rpc("1", "initialize", map(
                                "protocolVersion", "2025-06-18"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.jsonrpc").value("2.0"))
                .andExpect(jsonPath("$.id").value("1"))
                .andExpect(jsonPath("$.result.protocolVersion").value("2025-06-18"))
                .andExpect(jsonPath("$.result.capabilities.tools").exists())
                .andExpect(jsonPath("$.result.serverInfo.name").value("data-map-mcp"))
                .andExpect(header().exists("Mcp-Session-Id"));
    }

    @Test
    void notificationsInitialized_returns204NoBody() throws Exception {
        ObjectNode req = mapper.createObjectNode();
        req.put("jsonrpc", "2.0");
        req.putNull("id");
        req.put("method", "notifications/initialized");
        mvc.perform(post("/mcp")
                        .contentType("application/json")
                        .content(req.toString()))
                .andExpect(status().isNoContent());
    }

    @Test
    void toolsList_returnsCatalog() throws Exception {
        McpTool t = mock(McpTool.class);
        when(t.name()).thenReturn("get_field_usage_scenarios");
        when(t.description()).thenReturn("desc");
        when(t.inputSchema()).thenReturn(map("type", "object"));
        when(registry.list()).thenReturn(Arrays.asList(t));

        mvc.perform(post("/mcp")
                        .contentType("application/json")
                        .content(rpc("2", "tools/list", null)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.tools[0].name")
                        .value("get_field_usage_scenarios"))
                .andExpect(jsonPath("$.result.tools[0].inputSchema.type").value("object"));
    }

    @Test
    void toolsCall_dispatchesToTool() throws Exception {
        McpTool t = mock(McpTool.class);
        when(t.name()).thenReturn("get_field_usage_scenarios");
        when(t.execute(any())).thenReturn(McpToolResult.text("[{\"id\":1}]"));
        when(registry.findByName("get_field_usage_scenarios"))
                .thenReturn(java.util.Optional.of(t));

        mvc.perform(post("/mcp")
                        .contentType("application/json")
                        .content(rpc("3", "tools/call", map(
                                "name", "get_field_usage_scenarios",
                                "arguments", map("fieldId", 349)))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.content[0].type").value("text"))
                .andExpect(jsonPath("$.result.content[0].text").value("[{\"id\":1}]"))
                .andExpect(jsonPath("$.result.isError").value(false));
    }

    @Test
    void toolsCall_unknownTool_returnsMinus32601() throws Exception {
        when(registry.findByName("nope")).thenReturn(java.util.Optional.empty());
        mvc.perform(post("/mcp")
                        .contentType("application/json")
                        .content(rpc("4", "tools/call", map(
                                "name", "nope", "arguments", Collections.emptyMap()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.error.code").value(-32601));
    }

    @Test
    void toolsCall_invalidArgs_returnsMinus32602() throws Exception {
        McpTool t = mock(McpTool.class);
        when(t.name()).thenReturn("get_field_usage_scenarios");
        when(t.execute(any())).thenThrow(new McpArgsException("Missing required argument 'fieldId'"));
        when(registry.findByName("get_field_usage_scenarios"))
                .thenReturn(java.util.Optional.of(t));

        mvc.perform(post("/mcp")
                        .contentType("application/json")
                        .content(rpc("5", "tools/call", map(
                                "name", "get_field_usage_scenarios",
                                "arguments", Collections.emptyMap()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.error.code").value(-32602));
    }

    @Test
    void toolsCall_toolThrows_returnsIsErrorResult() throws Exception {
        McpTool t = mock(McpTool.class);
        when(t.name()).thenReturn("get_field_usage_scenarios");
        when(t.execute(any())).thenThrow(new RuntimeException("db down"));
        when(registry.findByName("get_field_usage_scenarios"))
                .thenReturn(java.util.Optional.of(t));

        mvc.perform(post("/mcp")
                        .contentType("application/json")
                        .content(rpc("6", "tools/call", map(
                                "name", "get_field_usage_scenarios",
                                "arguments", map("fieldId", 1)))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.isError").value(true))
                .andExpect(jsonPath("$.result.content[0].text")
                        .value(org.hamcrest.Matchers.containsString("db down")));
    }

    @Test
    void unknownMethod_returnsMinus32601() throws Exception {
        mvc.perform(post("/mcp")
                        .contentType("application/json")
                        .content(rpc("7", "nope", null)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.error.code").value(-32601));
    }

    @Test
    void malformedJson_returnsMinus32700() throws Exception {
        mvc.perform(post("/mcp")
                        .contentType("application/json")
                        .content("{not json"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.error.code").value(-32700));
    }
}
