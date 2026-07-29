package com.datamap.dto;

public class PathQueryRequest {
    private Long projectId;
    private Long startTableId;
    private String startFieldName;
    private Long targetTableId;
    private String targetFieldName;

    public Long getProjectId() { return projectId; }
    public void setProjectId(Long projectId) { this.projectId = projectId; }
    public Long getStartTableId() { return startTableId; }
    public void setStartTableId(Long startTableId) { this.startTableId = startTableId; }
    public String getStartFieldName() { return startFieldName; }
    public void setStartFieldName(String startFieldName) { this.startFieldName = startFieldName; }
    public Long getTargetTableId() { return targetTableId; }
    public void setTargetTableId(Long targetTableId) { this.targetTableId = targetTableId; }
    public String getTargetFieldName() { return targetFieldName; }
    public void setTargetFieldName(String targetFieldName) { this.targetFieldName = targetFieldName; }
}
