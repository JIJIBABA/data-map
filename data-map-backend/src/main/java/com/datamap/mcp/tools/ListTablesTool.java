package com.datamap.mcp.tools;

import com.datamap.mcp.McpArgs;
import com.datamap.mcp.McpTool;
import com.datamap.mcp.McpToolResult;
import com.datamap.service.TableService;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/** MCP tool wrapping {@link TableService#list(Long, String)}. */
@Component
public class ListTablesTool implements McpTool {

    private final TableService tableService;

    public ListTablesTool(TableService tableService) {
        this.tableService = tableService;
    }

    @Override public String name() { return "list_tables"; }
    @Override public String description() {
        return "List tables, optionally filtered by project id and/or table name substring.";
    }
    @Override public Map<String, Object> inputSchema() {
        Map<String, Object> projectId = prop("integer", "Optional project id.");
        Map<String, Object> tableName = prop("string", "Optional table name substring (LIKE).");
        Map<String, Object> props = new HashMap<>();
        props.put("projectId", projectId);
        props.put("tableName", tableName);
        Map<String, Object> schema = new HashMap<>();
        schema.put("type", "object");
        schema.put("properties", props);
        schema.put("required", java.util.Collections.emptyList());
        return schema;
    }
    private Map<String, Object> prop(String type, String desc) {
        Map<String, Object> p = new HashMap<>();
        p.put("type", type);
        p.put("description", desc);
        return p;
    }
    @Override public McpToolResult execute(JsonNode arguments) {
        Long projectId = McpArgs.optionalLong(arguments, "projectId");
        String tableName = McpArgs.optionalString(arguments, "tableName");
        return McpToolResult.fromPayload(tableService.list(projectId, tableName));
    }
}
