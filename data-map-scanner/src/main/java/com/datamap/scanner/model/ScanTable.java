package com.datamap.scanner.model;
import java.util.List;
public class ScanTable {
    public final String tableName;
    public final String tableComment;
    public final String schemaName;
    public final String dbType;
    public final List<ScanField> fields;
    public final List<ScanRelation> relations;
    public final List<UsageScenario> usageScenarios;
    public ScanTable(String tableName, String tableComment, String schemaName, String dbType,
                     List<ScanField> fields, List<ScanRelation> relations, List<UsageScenario> usageScenarios) {
        this.tableName = tableName; this.tableComment = tableComment; this.schemaName = schemaName;
        this.dbType = dbType; this.fields = fields; this.relations = relations; this.usageScenarios = usageScenarios;
    }
}
