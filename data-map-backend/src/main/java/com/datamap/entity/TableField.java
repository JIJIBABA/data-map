package com.datamap.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

@TableName("table_field")
public class TableField {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tableId;
    private String fieldName;
    private String fieldComment;
    private String fieldType;
    private Integer isPk;
    private Integer isBusinessField;
    private Integer fieldCommentManual;
    private Integer status;
    @com.baomidou.mybatisplus.annotation.TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    @com.baomidou.mybatisplus.annotation.TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getTableId() { return tableId; }
    public void setTableId(Long tableId) { this.tableId = tableId; }
    public String getFieldName() { return fieldName; }
    public void setFieldName(String fieldName) { this.fieldName = fieldName; }
    public String getFieldComment() { return fieldComment; }
    public void setFieldComment(String fieldComment) { this.fieldComment = fieldComment; }
    public String getFieldType() { return fieldType; }
    public void setFieldType(String fieldType) { this.fieldType = fieldType; }
    public Integer getIsPk() { return isPk; }
    public void setIsPk(Integer isPk) { this.isPk = isPk; }
    public Integer getIsBusinessField() { return isBusinessField; }
    public void setIsBusinessField(Integer isBusinessField) { this.isBusinessField = isBusinessField; }
    public Integer getFieldCommentManual() { return fieldCommentManual; }
    public void setFieldCommentManual(Integer fieldCommentManual) { this.fieldCommentManual = fieldCommentManual; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public Integer getStatus() { return status; }
    public void setStatus(Integer status) { this.status = status; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
