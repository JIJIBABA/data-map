package com.datamap.mcp.tools;

import com.datamap.mcp.McpArgs;
import com.datamap.mcp.McpTool;
import com.datamap.mcp.McpToolResult;
import com.datamap.service.QueryService;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/** MCP tool wrapping {@link QueryService#searchFields(String, Long)}. */
@Component
public class SearchFieldsTool implements McpTool {

    private final QueryService queryService;

    public SearchFieldsTool(QueryService queryService) {
        this.queryService = queryService;
    }

    @Override public String name() { return "search_fields"; }
    @Override public String description() {
        return "Search table fields by keyword (field name or comment), optionally within a project.";
    }
    @Override public Map<String, Object> inputSchema() {
        Map<String, Object> keyword = new HashMap<>();
        keyword.put("type", "string");
        keyword.put("description", "Keyword to match field name or comment.");
        Map<String, Object> projectId = new HashMap<>();
        projectId.put("type", "integer");
        projectId.put("description", "Optional project id to scope the search.");
        Map<String, Object> props = new HashMap<>();
        props.put("keyword", keyword);
        props.put("projectId", projectId);
        Map<String, Object> schema = new HashMap<>();
        schema.put("type", "object");
        schema.put("properties", props);
        schema.put("required", java.util.Collections.emptyList());
        return schema;
    }
    @Override public McpToolResult execute(JsonNode arguments) {
        String keyword = McpArgs.optionalString(arguments, "keyword");
        Long projectId = McpArgs.optionalLong(arguments, "projectId");
        return McpToolResult.fromPayload(queryService.searchFields(keyword, projectId));
    }
}
