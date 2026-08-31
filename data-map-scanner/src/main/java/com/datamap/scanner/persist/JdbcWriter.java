package com.datamap.scanner.persist;

import com.datamap.scanner.model.ScanResult;
import com.datamap.scanner.model.ScanTable;
import com.datamap.scanner.model.UsageScenario;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

public class JdbcWriter {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    public static void write(ScanResult result, String jdbcUrl, String user, String pass) throws Exception {
        try (Connection conn = DriverManager.getConnection(jdbcUrl, user, pass)) {
            for (ScanTable table : result.tables) {
                Long tableId = tableId(conn, table.tableName);
                for (UsageScenario s : table.usageScenarios) {
                    Long fieldId = tableId == null ? null : fieldId(conn, tableId, s.fieldName);
                    if (tableId == null || fieldId == null) {
                        System.err.println("JdbcWriter: skip scenario table=" + table.tableName
                            + " field=" + s.fieldName + " (table_id/field_id 未解析，跳过避免孤儿行)");
                        continue;
                    }
                    try (PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO field_usage_scenario " +
                        "(field_id, table_id, operation_type, scenario_description, method_name, " +
                        " source_table_name, source_api_name, call_chain, entry_info, method_description, description_source) " +
                        "VALUES (?, ?, ?, NULL, ?, ?, ?, ?, ?, ?, ?)")) {
                        ps.setLong(1, fieldId);
                        ps.setLong(2, tableId);
                        ps.setString(3, s.operationType);
                        ps.setString(4, s.methodName);
                        ps.setString(5, s.sourceTableName);
                        ps.setString(6, s.sourceApiName);
                        ps.setString(7, s.callChain == null ? "[]" : MAPPER.writeValueAsString(s.callChain));
                        ps.setString(8, s.entry == null ? "{}" : MAPPER.writeValueAsString(s.entry));
                        ps.setString(9, s.methodDescription);
                        ps.setString(10, s.descriptionSource);
                        ps.executeUpdate();
                    }
                }
            }
        }
    }

    private static Long tableId(Connection conn, String tableName) throws Exception {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT id FROM table_info WHERE table_name = ?")) {
            ps.setString(1, tableName);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getLong(1) : null;
            }
        }
    }

    private static Long fieldId(Connection conn, Long tableId, String fieldName) throws Exception {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT id FROM table_field WHERE table_id = ? AND field_name = ?")) {
            ps.setLong(1, tableId);
            ps.setString(2, fieldName);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getLong(1) : null;
            }
        }
    }
}
