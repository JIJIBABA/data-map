package com.datamap.mcp;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * MCP endpoint over Streamable HTTP (single request-response, no SSE).
 * Dispatches JSON-RPC 2.0 methods: initialize, notifications/initialized,
 * tools/list, tools/call.
 */
@RestController
public class McpController {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final McpToolRegistry registry;

    public McpController(McpToolRegistry registry) {
        this.registry = registry;
    }

    @PostMapping(value = "/mcp")
    public ResponseEntity<?> handle(@RequestBody(required = false) String body,
                                   HttpServletResponse response) {
        String id = null;
        try {
            JsonNode root = MAPPER.readTree(body == null ? "null" : body);
            JsonNode idNode = root.get("id");
            id = (idNode == null || idNode.isNull()) ? null : idNode.asText();
            String method = root.has("method") ? root.get("method").asText() : null;

            switch (method == null ? "" : method) {
                case "initialize":
                    return ResponseEntity.ok()
                            .header("Mcp-Session-Id", UUID.randomUUID().toString())
                            .body(McpJsonRpc.result(id, initializeResult()));
                case "notifications/initialized":
                    // Notification (id null) — no JSON-RPC response, 204.
                    return ResponseEntity.noContent().build();
                case "tools/list":
                    return ResponseEntity.ok(McpJsonRpc.result(id, toolsListResult()));
                case "tools/call":
                    return ResponseEntity.ok(McpJsonRpc.result(id, toolsCallResult(root)));
                default:
                    return ResponseEntity.ok(McpJsonRpc.error(id, -32601, "Method not found: " + method));
            }
        } catch (McpArgsException ae) {
            return ResponseEntity.ok(McpJsonRpc.error(id, -32602, "Invalid params: " + ae.getMessage()));
        } catch (McpUnknownToolException ue) {
            return ResponseEntity.ok(McpJsonRpc.error(id, -32601, ue.getMessage()));
        } catch (JsonParseException pe) {
            return ResponseEntity.ok(McpJsonRpc.error(-32700, "Parse error"));
        } catch (Exception e) {
            // Fallback for any other malformed-JSON / structural issue.
            return ResponseEntity.ok(McpJsonRpc.error(-32700, "Parse error: " + e.getMessage()));
        }
    }

    private ObjectNode initializeResult() {
        ObjectNode result = MAPPER.createObjectNode();
        result.put("protocolVersion", "2025-06-18");
        ObjectNode caps = MAPPER.createObjectNode();
        caps.put("tools", MAPPER.createObjectNode());
        result.set("capabilities", caps);
        ObjectNode info = MAPPER.createObjectNode();
        info.put("name", "data-map-mcp");
        info.put("version", "1.0.0");
        result.set("serverInfo", info);
        return result;
    }

    private ObjectNode toolsListResult() {
        ObjectNode result = MAPPER.createObjectNode();
        List<ObjectNode> tools = new ArrayList<>();
        for (McpTool t : registry.list()) {
            ObjectNode to = MAPPER.createObjectNode();
            to.put("name", t.name());
            to.put("description", t.description());
            to.set("inputSchema", MAPPER.valueToTree(t.inputSchema()));
            tools.add(to);
        }
        result.set("tools", MAPPER.createArrayNode().addAll(tools));
        return result;
    }

    private ObjectNode toolsCallResult(JsonNode root) {
        JsonNode params = root.path("params");
        String name = params.path("name").asText("");
        Optional<McpTool> opt = registry.findByName(name);
        if (!opt.isPresent()) {
            throw new McpUnknownToolException("Unknown tool: " + name);
        }
        McpTool tool = opt.get();
        JsonNode arguments = params.has("arguments") ? params.get("arguments") : MAPPER.createObjectNode();
        McpToolResult tr;
        try {
            tr = tool.execute(arguments);
        } catch (McpArgsException ae) {
            throw ae; // surfaced as -32602 in handle()
        } catch (RuntimeException re) {
            tr = McpToolResult.error(re.getMessage());
        }
        return toCallResult(tr);
    }

    private ObjectNode toCallResult(McpToolResult tr) {
        ObjectNode result = MAPPER.createObjectNode();
        result.put("isError", tr.isError());
        result.set("content", MAPPER.createArrayNode().addAll(tr.getContent()));
        return result;
    }
}
