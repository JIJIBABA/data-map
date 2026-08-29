package com.datamap.scanner.persist;

import com.datamap.scanner.model.ScanResult;
import com.datamap.scanner.model.ScanTable;
import com.datamap.scanner.model.UsageScenario;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;

public class JdbcWriter {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    public static void write(ScanResult result, String jdbcUrl, String user, String pass) throws Exception {
        try (Connection conn = DriverManager.getConnection(jdbcUrl, user, pass)) {
            for (ScanTable table : result.tables) {
                for (UsageScenario s : table.usageScenarios) {
                    try (PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO field_usage_scenario " +
                        "(field_id, table_id, operation_type, scenario_description, method_name, " +
                        " source_table_name, source_api_name, call_chain, entry_info, method_description, description_source) " +
                        "VALUES (NULL, NULL, ?, ?, ?, ?, ?, ?, ?, ?)")) {
                        ps.setString(1, s.operationType);
                        ps.setString(2, s.methodDescription);
                        ps.setString(3, s.methodName);
                        ps.setString(4, s.sourceTableName);
                        ps.setString(5, s.sourceApiName);
                        ps.setString(6, s.callChain == null ? "[]" : MAPPER.writeValueAsString(s.callChain));
                        ps.setString(7, s.entry == null ? "{}" : MAPPER.writeValueAsString(s.entry));
                        ps.setString(8, s.methodDescription);
                        ps.setString(9, s.descriptionSource);
                        ps.executeUpdate();
                    }
                }
            }
        }
    }
}
