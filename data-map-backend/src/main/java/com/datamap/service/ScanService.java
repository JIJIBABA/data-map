package com.datamap.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.datamap.dto.ScanResultDTO;
import com.datamap.entity.*;
import com.datamap.mapper.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDateTime;

@Service
public class ScanService {

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

    @Transactional
    public void receiveScanResult(ScanResultDTO dto) {
        ScanResultDTO.ProjectDTO pd = dto.getProject();
        Project project;
        LambdaQueryWrapper<Project> pqw = new LambdaQueryWrapper<>();
        pqw.eq(Project::getAppName, pd.getAppName());
        project = projectMapper.selectOne(pqw);
        if (project == null) {
            project = new Project();
            project.setAppName(pd.getAppName());
            project.setGitRepoUrl(pd.getGitRepoUrl());
            project.setGitLocalPath(pd.getGitLocalPath());
            projectMapper.insert(project);
        }

        ScanRecord record = new ScanRecord();
        record.setProjectId(project.getId());
        record.setScanType(dto.getScanType());
        record.setScanStatus("RUNNING");
        record.setStartedAt(LocalDateTime.now());
        scanRecordMapper.insert(record);

        try {
            // Phase 1: upsert all tables and fields first
            for (ScanResultDTO.TableDTO td : dto.getTables()) {
                upsertTableAndFields(project.getId(), td);
            }
            // Phase 2: process relations and usage scenarios (all target tables now exist)
            for (ScanResultDTO.TableDTO td : dto.getTables()) {
                processRelationsAndScenarios(project.getId(), td);
            }
            record.setScanStatus("SUCCESS");
        } catch (Exception e) {
            record.setScanStatus("FAILED");
            record.setErrorMsg(e.getMessage());
        }
        record.setCompletedAt(LocalDateTime.now());
        scanRecordMapper.updateById(record);
    }

    private void upsertTableAndFields(Long projectId, ScanResultDTO.TableDTO td) {
        LambdaQueryWrapper<TableInfo> tqw = new LambdaQueryWrapper<>();
        tqw.eq(TableInfo::getProjectId, projectId);
        tqw.eq(TableInfo::getTableName, td.getTableName());
        TableInfo table = tableInfoMapper.selectOne(tqw);

        if (table == null) {
            table = new TableInfo();
            table.setProjectId(projectId);
            table.setTableName(td.getTableName());
            table.setSchemaName(td.getSchemaName());
            table.setDbType(td.getDbType());
            table.setTableComment(td.getTableComment());
            table.setTableCommentManual(0);
            tableInfoMapper.insert(table);
        } else {
            if (table.getTableCommentManual() == null || table.getTableCommentManual() != 1) {
                table.setTableComment(td.getTableComment());
            }
            tableInfoMapper.updateById(table);
        }

        for (ScanResultDTO.FieldDTO fd : td.getFields()) {
            LambdaQueryWrapper<TableField> fqw = new LambdaQueryWrapper<>();
            fqw.eq(TableField::getTableId, table.getId());
            fqw.eq(TableField::getFieldName, fd.getFieldName());
            TableField field = tableFieldMapper.selectOne(fqw);

            if (field == null) {
                field = new TableField();
                field.setTableId(table.getId());
                field.setFieldName(fd.getFieldName());
                field.setFieldComment(fd.getFieldComment());
                field.setFieldType(fd.getFieldType());
                field.setIsPk(fd.getIsPk() != null && fd.getIsPk() ? 1 : 0);
                field.setIsBusinessField(fd.getIsBusinessField() != null && fd.getIsBusinessField() ? 1 : 0);
                field.setFieldCommentManual(0);
                tableFieldMapper.insert(field);
            } else {
                if (field.getFieldCommentManual() == null || field.getFieldCommentManual() != 1) {
                    field.setFieldComment(fd.getFieldComment());
                }
                field.setFieldType(fd.getFieldType());
                field.setIsPk(fd.getIsPk() != null && fd.getIsPk() ? 1 : 0);
                field.setIsBusinessField(fd.getIsBusinessField() != null && fd.getIsBusinessField() ? 1 : 0);
                tableFieldMapper.updateById(field);
            }
        }
    }

    private String serialize(Object o) {
        if (o == null) return null;
        try { return new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(o); }
        catch (Exception e) { return null; }
    }

    private void processRelationsAndScenarios(Long projectId, ScanResultDTO.TableDTO td) {
        LambdaQueryWrapper<TableInfo> tqw = new LambdaQueryWrapper<>();
        tqw.eq(TableInfo::getProjectId, projectId);
        tqw.eq(TableInfo::getTableName, td.getTableName());
        TableInfo table = tableInfoMapper.selectOne(tqw);
        if (table == null) return;

        if (td.getRelations() != null) {
            LambdaQueryWrapper<TableRelation> rqw = new LambdaQueryWrapper<>();
            rqw.eq(TableRelation::getProjectId, projectId);
            rqw.eq(TableRelation::getSourceTableId, table.getId());
            tableRelationMapper.delete(rqw);

            for (ScanResultDTO.RelationDTO rd : td.getRelations()) {
                LambdaQueryWrapper<TableInfo> ttqw = new LambdaQueryWrapper<>();
                ttqw.eq(TableInfo::getProjectId, projectId);
                ttqw.eq(TableInfo::getTableName, rd.getTargetTableName());
                TableInfo targetTable = tableInfoMapper.selectOne(ttqw);

                if (targetTable != null) {
                    TableRelation relation = new TableRelation();
                    relation.setProjectId(projectId);
                    relation.setSourceTableId(table.getId());
                    relation.setSourceFieldName(rd.getSourceFieldName());
                    relation.setTargetTableId(targetTable.getId());
                    relation.setTargetFieldName(rd.getTargetFieldName());
                    relation.setRelationType(rd.getRelationType());
                    relation.setMethodSignature(rd.getMethodSignature());
                    tableRelationMapper.insert(relation);
                }
            }
        }

        if (td.getUsageScenarios() != null) {
            LambdaQueryWrapper<FieldUsageScenario> sqw = new LambdaQueryWrapper<>();
            sqw.eq(FieldUsageScenario::getTableId, table.getId());
            fieldUsageScenarioMapper.delete(sqw);

            for (ScanResultDTO.UsageScenarioDTO us : td.getUsageScenarios()) {
                LambdaQueryWrapper<TableField> fqw = new LambdaQueryWrapper<>();
                fqw.eq(TableField::getTableId, table.getId());
                fqw.eq(TableField::getFieldName, us.getFieldName());
                TableField field = tableFieldMapper.selectOne(fqw);

                FieldUsageScenario scenario = new FieldUsageScenario();
                scenario.setFieldId(field != null ? field.getId() : null);
                scenario.setTableId(table.getId());
                scenario.setOperationType(us.getOperationType());
                scenario.setScenarioDescription(us.getScenarioDescription());
                scenario.setMethodName(us.getMethodName());
                scenario.setSourceTableName(us.getSourceTableName());
                scenario.setSourceApiName(us.getSourceApiName());
                scenario.setMethodDescription(us.getMethodDescription());
                scenario.setDescriptionSource(us.getDescriptionSource());
                scenario.setCallChain(serialize(us.getCallChain()));
                scenario.setEntryInfo(serialize(us.getEntry()));
                fieldUsageScenarioMapper.insert(scenario);
            }
        }
    }
}
