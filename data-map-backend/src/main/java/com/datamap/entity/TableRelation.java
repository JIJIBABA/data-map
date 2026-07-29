package com.datamap.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.FieldFill;
import java.time.LocalDateTime;

@TableName("table_relation")
public class TableRelation {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long projectId;
    private Long sourceTableId;
    private String sourceFieldName;
    private Long targetTableId;
    private String targetFieldName;
    private String relationType;
    private String methodSignature;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getProjectId() { return projectId; }
    public void setProjectId(Long projectId) { this.projectId = projectId; }
    public Long getSourceTableId() { return sourceTableId; }
    public void setSourceTableId(Long sourceTableId) { this.sourceTableId = sourceTableId; }
    public String getSourceFieldName() { return sourceFieldName; }
    public void setSourceFieldName(String sourceFieldName) { this.sourceFieldName = sourceFieldName; }
    public Long getTargetTableId() { return targetTableId; }
    public void setTargetTableId(Long targetTableId) { this.targetTableId = targetTableId; }
    public String getTargetFieldName() { return targetFieldName; }
    public void setTargetFieldName(String targetFieldName) { this.targetFieldName = targetFieldName; }
    public String getRelationType() { return relationType; }
    public void setRelationType(String relationType) { this.relationType = relationType; }
    public String getMethodSignature() { return methodSignature; }
    public void setMethodSignature(String methodSignature) { this.methodSignature = methodSignature; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
