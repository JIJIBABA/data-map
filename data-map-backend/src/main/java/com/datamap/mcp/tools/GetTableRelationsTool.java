package com.datamap.mcp.tools;

import com.datamap.mcp.McpArgs;
import com.datamap.mcp.McpTool;
import com.datamap.mcp.McpToolResult;
import com.datamap.service.RelationService;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/** MCP tool wrapping {@link RelationService#getRelations(Long)}. */
@Component
public class GetTableRelationsTool implements McpTool {

    private final RelationService relationService;

    public GetTableRelationsTool(RelationService relationService) {
        this.relationService = relationService;
    }

    @Override public String name() { return "get_table_relations"; }
    @Override public String description() {
        return "Get the relation graph (nodes + edges + details) for a given table, "
                + "including all transitively related tables.";
    }
    @Override public Map<String, Object> inputSchema() {
        Map<String, Object> tableId = prop("integer", "The table id.");
        Map<String, Object> props = new HashMap<>();
        props.put("tableId", tableId);
        Map<String, Object> schema = new HashMap<>();
        schema.put("type", "object");
        schema.put("properties", props);
        schema.put("required", Arrays.asList("tableId"));
        return schema;
    }
    private Map<String, Object> prop(String type, String desc) {
        Map<String, Object> p = new HashMap<>();
        p.put("type", type);
        p.put("description", desc);
        return p;
    }
    @Override public McpToolResult execute(JsonNode arguments) {
        long tableId = McpArgs.requireLong(arguments, "tableId");
        return McpToolResult.fromPayload(relationService.getRelations(tableId));
    }
}
