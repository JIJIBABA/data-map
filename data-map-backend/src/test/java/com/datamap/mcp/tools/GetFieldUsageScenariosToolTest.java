package com.datamap.mcp.tools;

import com.datamap.entity.FieldUsageScenario;
import com.datamap.mcp.McpArgsException;
import com.datamap.mcp.McpToolResult;
import com.datamap.service.FieldService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class GetFieldUsageScenariosToolTest {

    private final FieldService fieldService = mock(FieldService.class);
    private final GetFieldUsageScenariosTool tool = new GetFieldUsageScenariosTool(fieldService);
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void metadata_isCorrect() {
        assertEquals("get_field_usage_scenarios", tool.name());
        assertTrue(tool.description().toLowerCase().contains("usage"));
        Map<String, Object> schema = tool.inputSchema();
        assertEquals("object", schema.get("type"));
        @SuppressWarnings("unchecked")
        List<String> required = (List<String>) schema.get("required");
        assertTrue(required.contains("fieldId"));
    }

    @Test
    void execute_returnsScenariosAsJson() throws Exception {
        FieldUsageScenario s = new FieldUsageScenario();
        s.setId(1L);
        s.setFieldId(349L);
        s.setOperationType("READ");
        s.setScenarioDescription("查询订单列表");
        when(fieldService.getUsageScenarios(349L)).thenReturn(Collections.singletonList(s));

        JsonNode args = mapper.readTree("{\"fieldId\":349}");
        McpToolResult r = tool.execute(args);

        assertFalse(r.isError());
        JsonNode payload = mapper.readTree(r.getContent().get(0).get("text").asText());
        assertEquals(349, payload.get(0).get("fieldId").asInt());
        assertEquals("READ", payload.get(0).get("operationType").asText());
    }

    @Test
    void execute_emptyResult_returnsJsonEmptyArray() throws Exception {
        when(fieldService.getUsageScenarios(1L)).thenReturn(Collections.emptyList());
        JsonNode args = mapper.readTree("{\"fieldId\":1}");
        McpToolResult r = tool.execute(args);
        assertEquals("[]", r.getContent().get(0).get("text").asText());
    }

    @Test
    void execute_missingFieldId_throws() throws Exception {
        JsonNode args = mapper.readTree("{}");
        assertThrows(McpArgsException.class, () -> tool.execute(args));
    }
}
