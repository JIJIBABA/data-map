package com.datamap.mcp.tools;

import com.datamap.dto.PathQueryRequest;
import com.datamap.mcp.McpArgs;
import com.datamap.mcp.McpTool;
import com.datamap.mcp.McpToolResult;
import com.datamap.service.QueryService;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/** MCP tool wrapping {@link QueryService#findPath(PathQueryRequest)}. */
@Component
public class FindPathTool implements McpTool {

    private final QueryService queryService;

    public FindPathTool(QueryService queryService) {
        this.queryService = queryService;
    }

    @Override public String name() { return "find_path"; }
    @Override public String description() {
        return "Find join paths between two tables (by id) within a project. "
                + "Returns up to 10 shortest paths as node/edge lists.";
    }
    @Override public Map<String, Object> inputSchema() {
        Map<String, Object> startTableId = prop("integer", "The source table id.");
        Map<String, Object> targetTableId = prop("integer", "The target table id.");
        Map<String, Object> projectId = prop("integer", "Optional project id to scope the graph.");
        Map<String, Object> startFieldName = prop("string", "Optional source field name to pin the join.");
        Map<String, Object> targetFieldName = prop("string", "Optional target field name to pin the join.");
        Map<String, Object> props = new HashMap<>();
        props.put("startTableId", startTableId);
        props.put("targetTableId", targetTableId);
        props.put("projectId", projectId);
        props.put("startFieldName", startFieldName);
        props.put("targetFieldName", targetFieldName);
        Map<String, Object> schema = new HashMap<>();
        schema.put("type", "object");
        schema.put("properties", props);
        schema.put("required", Arrays.asList("startTableId", "targetTableId"));
        return schema;
    }
    private Map<String, Object> prop(String type, String desc) {
        Map<String, Object> p = new HashMap<>();
        p.put("type", type);
        p.put("description", desc);
        return p;
    }
    @Override public McpToolResult execute(JsonNode arguments) {
        PathQueryRequest req = new PathQueryRequest();
        req.setStartTableId(McpArgs.requireLong(arguments, "startTableId"));
        req.setTargetTableId(McpArgs.requireLong(arguments, "targetTableId"));
        req.setProjectId(McpArgs.optionalLong(arguments, "projectId"));
        req.setStartFieldName(McpArgs.optionalString(arguments, "startFieldName"));
        req.setTargetFieldName(McpArgs.optionalString(arguments, "targetFieldName"));
        return McpToolResult.fromPayload(queryService.findPath(req));
    }
}
