package com.datamap.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.datamap.entity.*;
import com.datamap.mapper.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.util.List;

@Service
public class TableService {

    @Resource
    private TableInfoMapper tableInfoMapper;
    @Resource
    private TableFieldMapper tableFieldMapper;
    @Resource
    private TableRelationMapper tableRelationMapper;
    @Resource
    private FieldUsageScenarioMapper fieldUsageScenarioMapper;

    public List<TableInfo> list(Long projectId, String tableName) {
        LambdaQueryWrapper<TableInfo> qw = new LambdaQueryWrapper<>();
        if (projectId != null) {
            qw.eq(TableInfo::getProjectId, projectId);
        }
        if (tableName != null && !tableName.isEmpty()) {
            qw.like(TableInfo::getTableName, tableName);
        }
        qw.orderByDesc(TableInfo::getUpdatedAt);
        return tableInfoMapper.selectList(qw);
    }

    public TableInfo getById(Long id) {
        return tableInfoMapper.selectById(id);
    }

    public TableInfo getByProjectAndName(Long projectId, String tableName) {
        LambdaQueryWrapper<TableInfo> qw = new LambdaQueryWrapper<>();
        qw.eq(TableInfo::getProjectId, projectId);
        qw.eq(TableInfo::getTableName, tableName);
        return tableInfoMapper.selectOne(qw);
    }

    public TableInfo updateComment(Long id, String comment) {
        TableInfo table = tableInfoMapper.selectById(id);
        if (table != null) {
            table.setTableComment(comment);
            table.setTableCommentManual(1);
            tableInfoMapper.updateById(table);
        }
        return table;
    }

    @Transactional
    public void delete(Long id) {
        LambdaQueryWrapper<FieldUsageScenario> sQw = new LambdaQueryWrapper<>();
        sQw.eq(FieldUsageScenario::getTableId, id);
        fieldUsageScenarioMapper.delete(sQw);

        LambdaQueryWrapper<TableField> fQw = new LambdaQueryWrapper<>();
        fQw.eq(TableField::getTableId, id);
        tableFieldMapper.delete(fQw);

        LambdaQueryWrapper<TableRelation> rQw = new LambdaQueryWrapper<>();
        rQw.eq(TableRelation::getSourceTableId, id).or().eq(TableRelation::getTargetTableId, id);
        tableRelationMapper.delete(rQw);

        tableInfoMapper.deleteById(id);
    }
}
