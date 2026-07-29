package com.datamap.dto;

public class FieldSearchResult {
    private String tableName;
    private Long tableId;
    private String fieldName;
    private String fieldComment;
    private String projectName;

    public String getTableName() { return tableName; }
    public void setTableName(String tableName) { this.tableName = tableName; }
    public Long getTableId() { return tableId; }
    public void setTableId(Long tableId) { this.tableId = tableId; }
    public String getFieldName() { return fieldName; }
    public void setFieldName(String fieldName) { this.fieldName = fieldName; }
    public String getFieldComment() { return fieldComment; }
    public void setFieldComment(String fieldComment) { this.fieldComment = fieldComment; }
    public String getProjectName() { return projectName; }
    public void setProjectName(String projectName) { this.projectName = projectName; }
}
