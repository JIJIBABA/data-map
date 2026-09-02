package com.datamap.mcp.tools;

import com.datamap.entity.FieldUsageScenario;
import com.datamap.mcp.McpArgs;
import com.datamap.mcp.McpTool;
import com.datamap.mcp.McpToolResult;
import com.datamap.service.FieldService;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * MCP tool wrapping {@link FieldService#getUsageScenarios(Long)}.
 * Returns the field's usage scenarios as a JSON-text content item.
 */
@Component
public class GetFieldUsageScenariosTool implements McpTool {

    private final FieldService fieldService;

    public GetFieldUsageScenariosTool(FieldService fieldService) {
        this.fieldService = fieldService;
    }

    @Override
    public String name() {
        return "get_field_usage_scenarios";
    }

    @Override
    public String description() {
        return "Get all usage scenarios for a given database field: operation type, "
                + "scenario description, method name, source table/API, call chain, and entry info.";
    }

    @Override
    public Map<String, Object> inputSchema() {
        Map<String, Object> fieldId = new HashMap<>();
        fieldId.put("type", "integer");
        fieldId.put("description", "The field id to look up usage scenarios for.");

        Map<String, Object> props = new HashMap<>();
        props.put("fieldId", fieldId);

        Map<String, Object> schema = new HashMap<>();
        schema.put("type", "object");
        schema.put("properties", props);
        schema.put("required", Arrays.asList("fieldId"));
        return schema;
    }

    @Override
    public McpToolResult execute(JsonNode arguments) {
        long fieldId = McpArgs.requireLong(arguments, "fieldId");
        List<FieldUsageScenario> scenarios = fieldService.getUsageScenarios(fieldId);
        return McpToolResult.fromPayload(scenarios);
    }
}
