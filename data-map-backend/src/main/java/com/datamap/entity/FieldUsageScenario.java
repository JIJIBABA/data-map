package com.datamap.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.FieldFill;
import java.time.LocalDateTime;

@TableName("field_usage_scenario")
public class FieldUsageScenario {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long fieldId;
    private Long tableId;
    private String operationType;
    private String scenarioDescription;
    private String methodName;
    private String sourceTableName;
    private String sourceApiName;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getFieldId() { return fieldId; }
    public void setFieldId(Long fieldId) { this.fieldId = fieldId; }
    public Long getTableId() { return tableId; }
    public void setTableId(Long tableId) { this.tableId = tableId; }
    public String getOperationType() { return operationType; }
    public void setOperationType(String operationType) { this.operationType = operationType; }
    public String getScenarioDescription() { return scenarioDescription; }
    public void setScenarioDescription(String scenarioDescription) { this.scenarioDescription = scenarioDescription; }
    public String getMethodName() { return methodName; }
    public void setMethodName(String methodName) { this.methodName = methodName; }
    public String getSourceTableName() { return sourceTableName; }
    public void setSourceTableName(String sourceTableName) { this.sourceTableName = sourceTableName; }
    public String getSourceApiName() { return sourceApiName; }
    public void setSourceApiName(String sourceApiName) { this.sourceApiName = sourceApiName; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
