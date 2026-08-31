package com.datamap.scanner.persist;

import com.datamap.scanner.model.ScanField;
import com.datamap.scanner.model.ScanRelation;
import com.datamap.scanner.model.ScanResult;
import com.datamap.scanner.model.ScanTable;
import com.datamap.scanner.model.UsageScenario;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.HashMap;
import java.util.Map;

/**
 * JDBC 直连完整落库，镜像后端 ScanService 的两阶段 upsert 逻辑：
 * Phase1: project / table_info / table_field（先建表与字段，关联依赖目标表已存在）
 * Phase2: table_relation / field_usage_scenario（按表先删后插）
 */
public class JdbcWriter {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    public static void write(ScanResult result, String jdbcUrl, String user, String pass, String appName) throws Exception {
        try (Connection conn = DriverManager.getConnection(jdbcUrl, user, pass)) {
            conn.setAutoCommit(false);
            try {
                // Phase 0: upsert project（按 app_name）
                Long projectId = upsertProject(conn, appName, result.project);

                // Phase 1: upsert 所有表与字段（关联的目标表需先存在）
                Map<String, Long> tableIdByName = new HashMap<>();
                for (ScanTable table : result.tables) {
                    Long tableId = upsertTable(conn, projectId, table);
                    tableIdByName.put(table.tableName, tableId);
                    for (ScanField f : table.fields) {
                        upsertField(conn, tableId, f);
                    }
                }

                // Phase 2: 关联与场景（先删后插）
                for (ScanTable table : result.tables) {
                    Long sourceTableId = tableIdByName.get(table.tableName);
                    if (sourceTableId == null) continue;

                    if (table.relations != null) {
                        deleteRelations(conn, projectId, sourceTableId);
                        for (ScanRelation r : table.relations) {
                            Long targetTableId = tableIdByName.get(r.targetTableName);
                            // 目标表未在本批扫描中：尝试按 project+table_name 兜底解析
                            if (targetTableId == null) {
                                targetTableId = findTableId(conn, projectId, r.targetTableName);
                            }
                            insertRelation(conn, projectId, sourceTableId, r, targetTableId);
                        }
                    }

                    if (table.usageScenarios != null) {
                        deleteScenarios(conn, sourceTableId);
                        for (UsageScenario s : table.usageScenarios) {
                            Long fieldId = findFieldId(conn, sourceTableId, s.fieldName);
                            if (fieldId == null) {
                                // 字段不存在（如表级 DELETE 的 fieldName=""）则跳过，field_id 非空约束
                                System.err.println("JdbcWriter: skip scenario table=" + table.tableName
                                    + " field=" + s.fieldName + " (field_id 未解析，跳过避免孤儿行)");
                                continue;
                            }
                            insertScenario(conn, fieldId, sourceTableId, s);
                        }
                    }
                }
                conn.commit();
            } catch (Exception e) {
                conn.rollback();
                throw e;
            }
        }
    }

    // ---- Phase 0: project ----
    private static Long upsertProject(Connection conn, String appName, com.datamap.scanner.model.ScanProject p) throws Exception {
        Long id = selectLong(conn, "SELECT id FROM project WHERE app_name = ?", appName);
        if (id != null) {
            // 已存在则更新 git 信息（保留手工 description）
            try (PreparedStatement ps = conn.prepareStatement(
                    "UPDATE project SET git_repo_url = IFNULL(?, git_repo_url), " +
                    "git_local_path = IFNULL(?, git_local_path) WHERE id = ?")) {
                ps.setString(1, p.gitRepoUrl);
                ps.setString(2, p.gitLocalPath);
                ps.setLong(3, id);
                ps.executeUpdate();
            }
            return id;
        }
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO project (app_name, git_repo_url, git_local_path, status) VALUES (?, ?, ?, 0)",
                Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, appName);
            ps.setString(2, p.gitRepoUrl);
            ps.setString(3, p.gitLocalPath);
            ps.executeUpdate();
            return generatedKey(ps);
        }
    }

    // ---- Phase 1: table_info / table_field ----
    private static Long upsertTable(Connection conn, Long projectId, ScanTable table) throws Exception {
        Long id = selectLong(conn,
                "SELECT id, table_comment_manual FROM table_info WHERE project_id = ? AND table_name = ?",
                projectId, table.tableName);
        if (id != null) {
            // 尊重手工注释：table_comment_manual=1 时不覆盖 table_comment
            try (PreparedStatement ps = conn.prepareStatement(
                    "UPDATE table_info SET schema_name = IFNULL(?, schema_name), " +
                    "db_type = IFNULL(?, db_type), " +
                    "table_comment = CASE WHEN IFNULL(table_comment_manual,0)=1 THEN table_comment ELSE ? END " +
                    "WHERE id = ?")) {
                ps.setString(1, table.schemaName);
                ps.setString(2, table.dbType);
                ps.setString(3, table.tableComment);
                ps.setLong(4, id);
                ps.executeUpdate();
            }
            return id;
        }
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO table_info (project_id, table_name, schema_name, db_type, table_comment, table_comment_manual, status) " +
                "VALUES (?, ?, ?, ?, ?, 0, 0)", Statement.RETURN_GENERATED_KEYS)) {
            ps.setLong(1, projectId);
            ps.setString(2, table.tableName);
            ps.setString(3, table.schemaName);
            ps.setString(4, table.dbType);
            ps.setString(5, table.tableComment);
            ps.executeUpdate();
            return generatedKey(ps);
        }
    }

    private static void upsertField(Connection conn, Long tableId, ScanField f) throws Exception {
        Long id = selectLong(conn,
                "SELECT id, field_comment_manual FROM table_field WHERE table_id = ? AND field_name = ?",
                tableId, f.fieldName);
        int isPk = f.pk ? 1 : 0;
        int isBiz = f.businessField ? 1 : 0;
        if (id != null) {
            try (PreparedStatement ps = conn.prepareStatement(
                    "UPDATE table_field SET field_type = ?, is_pk = ?, is_business_field = ?, " +
                    "field_comment = CASE WHEN IFNULL(field_comment_manual,0)=1 THEN field_comment ELSE ? END " +
                    "WHERE id = ?")) {
                ps.setString(1, f.fieldType);
                ps.setInt(2, isPk);
                ps.setInt(3, isBiz);
                ps.setString(4, f.fieldComment);
                ps.setLong(5, id);
                ps.executeUpdate();
            }
            return;
        }
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO table_field (table_id, field_name, field_comment, field_type, is_pk, is_business_field, field_comment_manual, status) " +
                "VALUES (?, ?, ?, ?, ?, ?, 0, 0)")) {
            ps.setLong(1, tableId);
            ps.setString(2, f.fieldName);
            ps.setString(3, f.fieldComment);
            ps.setString(4, f.fieldType);
            ps.setInt(5, isPk);
            ps.setInt(6, isBiz);
            ps.executeUpdate();
        }
    }

    // ---- Phase 2: table_relation / field_usage_scenario ----
    private static void deleteRelations(Connection conn, Long projectId, Long sourceTableId) throws Exception {
        try (PreparedStatement ps = conn.prepareStatement(
                "DELETE FROM table_relation WHERE project_id = ? AND source_table_id = ?")) {
            ps.setLong(1, projectId);
            ps.setLong(2, sourceTableId);
            ps.executeUpdate();
        }
    }

    private static void insertRelation(Connection conn, Long projectId, Long sourceTableId,
                                       ScanRelation r, Long targetTableId) throws Exception {
        if (targetTableId == null) {
            // 目标表不存在则跳过（避免 target_table_id 全空孤儿行）
            System.err.println("JdbcWriter: skip relation source=" + sourceTableId
                + " field=" + r.sourceFieldName + " target=" + r.targetTableName + " (目标表不存在)");
            return;
        }
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO table_relation (project_id, source_table_id, source_field_name, " +
                "target_table_id, target_field_name, relation_type, method_signature, status) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, 0)")) {
            ps.setLong(1, projectId);
            ps.setLong(2, sourceTableId);
            ps.setString(3, r.sourceFieldName);
            ps.setLong(4, targetTableId);
            ps.setString(5, r.targetFieldName);
            ps.setString(6, r.relationType);
            ps.setString(7, r.methodSignature);
            ps.executeUpdate();
        }
    }

    private static void deleteScenarios(Connection conn, Long tableId) throws Exception {
        try (PreparedStatement ps = conn.prepareStatement(
                "DELETE FROM field_usage_scenario WHERE table_id = ?")) {
            ps.setLong(1, tableId);
            ps.executeUpdate();
        }
    }

    private static void insertScenario(Connection conn, Long fieldId, Long tableId, UsageScenario s) throws Exception {
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO field_usage_scenario " +
                "(field_id, table_id, operation_type, scenario_description, method_name, " +
                " source_table_name, source_api_name, call_chain, entry_info, method_description, description_source, status) " +
                "VALUES (?, ?, ?, NULL, ?, ?, ?, ?, ?, ?, ?, 0)")) {
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

    // ---- helpers ----
    private static Long findTableId(Connection conn, Long projectId, String tableName) throws Exception {
        return selectLong(conn, "SELECT id FROM table_info WHERE project_id = ? AND table_name = ?",
                projectId, tableName);
    }

    private static Long findFieldId(Connection conn, Long tableId, String fieldName) throws Exception {
        return selectLong(conn, "SELECT id FROM table_field WHERE table_id = ? AND field_name = ?",
                tableId, fieldName);
    }

    private static Long selectLong(Connection conn, String sql, Object... args) throws Exception {
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            for (int i = 0; i < args.length; i++) {
                if (args[i] instanceof Long) ps.setLong(i + 1, (Long) args[i]);
                else ps.setString(i + 1, String.valueOf(args[i]));
            }
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getLong(1) : null;
            }
        }
    }

    private static Long generatedKey(PreparedStatement ps) throws Exception {
        try (ResultSet rs = ps.getGeneratedKeys()) {
            return rs.next() ? rs.getLong(1) : null;
        }
    }
}
