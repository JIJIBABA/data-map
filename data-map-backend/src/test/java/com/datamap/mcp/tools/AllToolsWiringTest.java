package com.datamap.mcp.tools;

import com.datamap.mcp.McpToolRegistry;
import com.datamap.service.*;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AllToolsWiringTest {

    @Test
    void allSevenToolsAreRegisteredWithExpectedNames() {
        McpToolRegistry reg = new McpToolRegistry(Arrays.asList(
                new GetFieldUsageScenariosTool(mock(FieldService.class)),
                new ListTableFieldsTool(mock(FieldService.class)),
                new SearchFieldsTool(mock(QueryService.class)),
                new FindPathTool(mock(QueryService.class)),
                new GetTableRelationsTool(mock(RelationService.class)),
                new ListTablesTool(mock(TableService.class)),
                new ListProjectsTool(mock(ProjectService.class))
        ));

        java.util.Set<String> names = new java.util.HashSet<>();
        reg.list().forEach(t -> names.add(t.name()));

        assertEquals(7, names.size());
        assertTrue(names.contains("get_field_usage_scenarios"));
        assertTrue(names.contains("list_table_fields"));
        assertTrue(names.contains("search_fields"));
        assertTrue(names.contains("find_path"));
        assertTrue(names.contains("get_table_relations"));
        assertTrue(names.contains("list_tables"));
        assertTrue(names.contains("list_projects"));
    }
}
