package com.datamap.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.datamap.entity.*;
import com.datamap.mapper.*;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.List;

@Service
public class FieldService {

    @Resource
    private TableFieldMapper tableFieldMapper;
    @Resource
    private FieldUsageScenarioMapper fieldUsageScenarioMapper;

    public List<TableField> listByTableId(Long tableId) {
        LambdaQueryWrapper<TableField> qw = new LambdaQueryWrapper<>();
        qw.eq(TableField::getTableId, tableId);
        qw.orderByAsc(TableField::getIsPk);
        return tableFieldMapper.selectList(qw);
    }

    public TableField updateComment(Long id, String comment) {
        TableField field = tableFieldMapper.selectById(id);
        if (field != null) {
            field.setFieldComment(comment);
            field.setFieldCommentManual(1);
            tableFieldMapper.updateById(field);
        }
        return field;
    }

    public List<FieldUsageScenario> getUsageScenarios(Long fieldId) {
        LambdaQueryWrapper<FieldUsageScenario> qw = new LambdaQueryWrapper<>();
        qw.eq(FieldUsageScenario::getFieldId, fieldId);
        return fieldUsageScenarioMapper.selectList(qw);
    }

    public TableField getByTableAndName(Long tableId, String fieldName) {
        LambdaQueryWrapper<TableField> qw = new LambdaQueryWrapper<>();
        qw.eq(TableField::getTableId, tableId);
        qw.eq(TableField::getFieldName, fieldName);
        return tableFieldMapper.selectOne(qw);
    }
}
