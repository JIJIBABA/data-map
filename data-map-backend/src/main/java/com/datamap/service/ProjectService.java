package com.datamap.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.datamap.entity.*;
import com.datamap.mapper.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.util.List;

@Service
public class ProjectService {

    @Resource
    private ProjectMapper projectMapper;
    @Resource
    private TableInfoMapper tableInfoMapper;
    @Resource
    private TableFieldMapper tableFieldMapper;
    @Resource
    private TableRelationMapper tableRelationMapper;
    @Resource
    private FieldUsageScenarioMapper fieldUsageScenarioMapper;
    @Resource
    private ScanRecordMapper scanRecordMapper;

    public List<Project> list(String keyword) {
        LambdaQueryWrapper<Project> qw = new LambdaQueryWrapper<>();
        qw.eq(Project::getStatus, 1);
        if (keyword != null && !keyword.isEmpty()) {
            qw.like(Project::getAppName, keyword);
        }
        qw.orderByDesc(Project::getUpdatedAt);
        return projectMapper.selectList(qw);
    }

    public Project getById(Long id) {
        return projectMapper.selectById(id);
    }

    public Project getByAppName(String appName) {
        LambdaQueryWrapper<Project> qw = new LambdaQueryWrapper<>();
        qw.eq(Project::getStatus, 1);
        qw.eq(Project::getAppName, appName);
        return projectMapper.selectOne(qw);
    }

    public Project create(Project project) {
        projectMapper.insert(project);
        return project;
    }

    public Project update(Project project) {
        projectMapper.updateById(project);
        return projectMapper.selectById(project.getId());
    }

    @Transactional
    public void delete(Long id) {
        LambdaQueryWrapper<TableInfo> tQw = new LambdaQueryWrapper<>();
        tQw.eq(TableInfo::getProjectId, id);
        List<TableInfo> tables = tableInfoMapper.selectList(tQw);

        for (TableInfo table : tables) {
            LambdaQueryWrapper<FieldUsageScenario> fQw = new LambdaQueryWrapper<>();
            fQw.eq(FieldUsageScenario::getTableId, table.getId());
            fieldUsageScenarioMapper.delete(fQw);

            LambdaQueryWrapper<TableField> tfQw = new LambdaQueryWrapper<>();
            tfQw.eq(TableField::getTableId, table.getId());
            tableFieldMapper.delete(tfQw);
        }

        LambdaQueryWrapper<TableRelation> rQw = new LambdaQueryWrapper<>();
        rQw.eq(TableRelation::getProjectId, id);
        tableRelationMapper.delete(rQw);

        tableInfoMapper.delete(tQw);

        LambdaQueryWrapper<ScanRecord> sQw = new LambdaQueryWrapper<>();
        sQw.eq(ScanRecord::getProjectId, id);
        scanRecordMapper.delete(sQw);

        projectMapper.deleteById(id);
    }
}
