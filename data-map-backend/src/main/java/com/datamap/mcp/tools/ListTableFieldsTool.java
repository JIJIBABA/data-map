package com.datamap.mcp.tools;

import com.datamap.mcp.McpArgs;
import com.datamap.mcp.McpTool;
import com.datamap.mcp.McpToolResult;
import com.datamap.service.FieldService;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/** MCP tool wrapping {@link FieldService#listByTableId(Long)}. */
@Component
public class ListTableFieldsTool implements McpTool {

    private final FieldService fieldService;

    public ListTableFieldsTool(FieldService fieldService) {
        this.fieldService = fieldService;
    }

    @Override public String name() { return "list_table_fields"; }
    @Override public String description() {
        return "List all fields of a given table (by table id).";
    }
    @Override public Map<String, Object> inputSchema() {
        Map<String, Object> tableId = new HashMap<>();
        tableId.put("type", "integer");
        tableId.put("description", "The table id.");
        Map<String, Object> props = new HashMap<>();
        props.put("tableId", tableId);
        Map<String, Object> schema = new HashMap<>();
        schema.put("type", "object");
        schema.put("properties", props);
        schema.put("required", Arrays.asList("tableId"));
        return schema;
    }
    @Override public McpToolResult execute(JsonNode arguments) {
        long tableId = McpArgs.requireLong(arguments, "tableId");
        return McpToolResult.fromPayload(fieldService.listByTableId(tableId));
    }
}
