package com.datamap.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.datamap.dto.TableRelationVO;
import com.datamap.entity.*;
import com.datamap.mapper.*;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class RelationService {

    @Resource
    private TableRelationMapper tableRelationMapper;
    @Resource
    private TableInfoMapper tableInfoMapper;

    public TableRelationVO getRelations(Long tableId, String direction) {
        Set<Long> tableIds = new HashSet<>();
        tableIds.add(tableId);

        LambdaQueryWrapper<TableRelation> qw = new LambdaQueryWrapper<>();
        if ("upstream".equals(direction)) {
            collectUpstream(tableId, tableIds, new HashSet<>());
        } else if ("downstream".equals(direction)) {
            collectDownstream(tableId, tableIds, new HashSet<>());
        } else {
            collectAll(tableId, tableIds, new HashSet<>());
        }

        List<TableInfo> tables = tableInfoMapper.selectBatchIds(tableIds);
        Map<Long, String> nameMap = tables.stream()
                .collect(Collectors.toMap(TableInfo::getId, TableInfo::getTableName));

        List<TableRelation> relations;
        if ("upstream".equals(direction)) {
            qw.in(TableRelation::getTargetTableId, tableIds);
            qw.in(TableRelation::getSourceTableId, tableIds);
        } else if ("downstream".equals(direction)) {
            qw.in(TableRelation::getSourceTableId, tableIds);
            qw.in(TableRelation::getTargetTableId, tableIds);
        } else {
            qw.and(w -> w.in(TableRelation::getSourceTableId, tableIds)
                    .or().in(TableRelation::getTargetTableId, tableIds));
        }
        relations = tableRelationMapper.selectList(qw);

        TableRelationVO vo = new TableRelationVO();
        vo.setNodes(tableIds.stream()
                .map(id -> new TableRelationVO.GraphNode(id, nameMap.getOrDefault(id, "")))
                .collect(Collectors.toList()));
        vo.setEdges(relations.stream().map(r -> {
            TableRelationVO.GraphEdge e = new TableRelationVO.GraphEdge();
            e.setSource(r.getSourceTableId());
            e.setTarget(r.getTargetTableId());
            e.setSourceField(r.getSourceFieldName());
            e.setTargetField(r.getTargetFieldName());
            return e;
        }).collect(Collectors.toList()));
        vo.setDetails(relations.stream().map(r -> {
            TableRelationVO.RelationDetail d = new TableRelationVO.RelationDetail();
            d.setSourceTable(nameMap.getOrDefault(r.getSourceTableId(), ""));
            d.setSourceTableId(r.getSourceTableId());
            d.setSourceField(r.getSourceFieldName());
            d.setTargetTable(nameMap.getOrDefault(r.getTargetTableId(), ""));
            d.setTargetTableId(r.getTargetTableId());
            d.setTargetField(r.getTargetFieldName());
            d.setRelationType(r.getRelationType());
            d.setMethodSignature(r.getMethodSignature());
            return d;
        }).collect(Collectors.toList()));

        return vo;
    }

    private void collectUpstream(Long id, Set<Long> result, Set<Long> visited) {
        if (!visited.add(id)) return;
        LambdaQueryWrapper<TableRelation> qw = new LambdaQueryWrapper<>();
        qw.eq(TableRelation::getTargetTableId, id);
        List<TableRelation> relations = tableRelationMapper.selectList(qw);
        for (TableRelation r : relations) {
            result.add(r.getSourceTableId());
            collectUpstream(r.getSourceTableId(), result, visited);
        }
    }

    private void collectDownstream(Long id, Set<Long> result, Set<Long> visited) {
        if (!visited.add(id)) return;
        LambdaQueryWrapper<TableRelation> qw = new LambdaQueryWrapper<>();
        qw.eq(TableRelation::getSourceTableId, id);
        List<TableRelation> relations = tableRelationMapper.selectList(qw);
        for (TableRelation r : relations) {
            result.add(r.getTargetTableId());
            collectDownstream(r.getTargetTableId(), result, visited);
        }
    }

    private void collectAll(Long id, Set<Long> result, Set<Long> visited) {
        if (!visited.add(id)) return;
        LambdaQueryWrapper<TableRelation> qw = new LambdaQueryWrapper<>();
        qw.eq(TableRelation::getSourceTableId, id).or().eq(TableRelation::getTargetTableId, id);
        List<TableRelation> relations = tableRelationMapper.selectList(qw);
        for (TableRelation r : relations) {
            Long next = r.getSourceTableId().equals(id) ? r.getTargetTableId() : r.getSourceTableId();
            result.add(next);
            collectAll(next, result, visited);
        }
    }
}
