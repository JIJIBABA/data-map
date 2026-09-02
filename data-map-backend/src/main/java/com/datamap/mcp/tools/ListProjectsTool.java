package com.datamap.mcp.tools;

import com.datamap.mcp.McpArgs;
import com.datamap.mcp.McpTool;
import com.datamap.mcp.McpToolResult;
import com.datamap.service.ProjectService;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/** MCP tool wrapping {@link ProjectService#list(String)}. */
@Component
public class ListProjectsTool implements McpTool {

    private final ProjectService projectService;

    public ListProjectsTool(ProjectService projectService) {
        this.projectService = projectService;
    }

    @Override public String name() { return "list_projects"; }
    @Override public String description() {
        return "List projects, optionally filtered by app-name keyword substring.";
    }
    @Override public Map<String, Object> inputSchema() {
        Map<String, Object> keyword = prop("string", "Optional app-name keyword substring (LIKE).");
        Map<String, Object> props = new HashMap<>();
        props.put("keyword", keyword);
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
        String keyword = McpArgs.optionalString(arguments, "keyword");
        return McpToolResult.fromPayload(projectService.list(keyword));
    }
}
