package com.datamap.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.datamap.dto.*;
import com.datamap.entity.*;
import com.datamap.mapper.*;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class QueryService {

    @Resource
    private TableFieldMapper tableFieldMapper;
    @Resource
    private TableInfoMapper tableInfoMapper;
    @Resource
    private TableRelationMapper tableRelationMapper;
    @Resource
    private ProjectMapper projectMapper;

    public List<FieldSearchResult> searchFields(String keyword, Long projectId) {
        LambdaQueryWrapper<TableField> fQw = new LambdaQueryWrapper<>();
        if (keyword != null && !keyword.isEmpty()) {
            fQw.like(TableField::getFieldComment, keyword);
        }
        List<TableField> fields = tableFieldMapper.selectList(fQw);
        if (fields.isEmpty()) return Collections.emptyList();

        Set<Long> tableIds = fields.stream().map(TableField::getTableId).collect(Collectors.toSet());
        Map<Long, TableInfo> tableMap = new HashMap<>();
        Map<Long, Project> projectMap = new HashMap<>();

        List<TableInfo> tables = tableInfoMapper.selectBatchIds(tableIds);
        for (TableInfo t : tables) {
            tableMap.put(t.getId(), t);
            if (projectId == null || t.getProjectId().equals(projectId)) {
                projectMap.putIfAbsent(t.getProjectId(), projectMapper.selectById(t.getProjectId()));
            }
        }

        return fields.stream()
                .filter(f -> {
                    TableInfo t = tableMap.get(f.getTableId());
                    return t != null && (projectId == null || t.getProjectId().equals(projectId));
                })
                .map(f -> {
                    FieldSearchResult r = new FieldSearchResult();
                    TableInfo t = tableMap.get(f.getTableId());
                    r.setTableName(t != null ? t.getTableName() : "");
                    r.setTableId(f.getTableId());
                    r.setFieldName(f.getFieldName());
                    r.setFieldComment(f.getFieldComment());
                    Project p = projectMap.get(t != null ? t.getProjectId() : null);
                    r.setProjectName(p != null ? p.getAppName() : "");
                    return r;
                }).collect(Collectors.toList());
    }

    public PathResult findPath(PathQueryRequest req) {
        List<PathResult.PathInfo> paths = new ArrayList<>();
        Map<Long, String> tableNames = new HashMap<>();

        List<TableInfo> allTables = tableInfoMapper.selectList(
                new LambdaQueryWrapper<TableInfo>().eq(TableInfo::getProjectId, req.getProjectId()));
        for (TableInfo t : allTables) {
            tableNames.put(t.getId(), t.getTableName());
        }

        List<TableRelation> relations = tableRelationMapper.selectList(
                new LambdaQueryWrapper<TableRelation>().eq(TableRelation::getProjectId, req.getProjectId()));
        // Build bidirectional adjacency: store original + reversed edges
        Map<Long, List<TableRelation>> adj = new HashMap<>();
        for (TableRelation r : relations) {
            adj.computeIfAbsent(r.getSourceTableId(), k -> new ArrayList<>()).add(r);
            // Add reverse edge for bidirectional traversal
            TableRelation rev = new TableRelation();
            rev.setSourceTableId(r.getTargetTableId());
            rev.setTargetTableId(r.getSourceTableId());
            rev.setSourceFieldName(r.getTargetFieldName());
            rev.setTargetFieldName(r.getSourceFieldName());
            rev.setRelationType(r.getRelationType());
            rev.setMethodSignature(r.getMethodSignature());
            adj.computeIfAbsent(r.getTargetTableId(), k -> new ArrayList<>()).add(rev);
        }

        Queue<List<TableRelation>> queue = new LinkedList<>();
        Set<Long> visited = new HashSet<>();
        visited.add(req.getStartTableId());

        List<TableRelation> startEdges = adj.getOrDefault(req.getStartTableId(), Collections.emptyList());
        for (TableRelation edge : startEdges) {
            List<TableRelation> path = new ArrayList<>();
            path.add(edge);
            queue.offer(path);
        }

        while (!queue.isEmpty() && paths.size() < 10) {
            List<TableRelation> currentPath = queue.poll();
            if (currentPath.size() > 6) continue;

            TableRelation lastEdge = currentPath.get(currentPath.size() - 1);
            Long currentNode = lastEdge.getTargetTableId();

            if (currentNode.equals(req.getTargetTableId())) {
                PathResult.PathInfo pi = new PathResult.PathInfo();
                List<String> nodes = new ArrayList<>();
                nodes.add(tableNames.getOrDefault(req.getStartTableId(), ""));
                List<PathResult.EdgeInfo> edges = new ArrayList<>();
                for (TableRelation r : currentPath) {
                    nodes.add(tableNames.getOrDefault(r.getTargetTableId(), ""));
                    PathResult.EdgeInfo ei = new PathResult.EdgeInfo();
                    ei.setFrom(tableNames.getOrDefault(r.getSourceTableId(), ""));
                    ei.setTo(tableNames.getOrDefault(r.getTargetTableId(), ""));
                    ei.setJoinField(r.getSourceFieldName() + " = " + r.getTargetFieldName());
                    edges.add(ei);
                }
                pi.setNodes(nodes);
                pi.setEdges(edges);
                pi.setLength(currentPath.size());
                paths.add(pi);
            } else {
                List<TableRelation> nextEdges = adj.getOrDefault(currentNode, Collections.emptyList());
                for (TableRelation next : nextEdges) {
                    if (!visited.contains(next.getTargetTableId()) || next.getTargetTableId().equals(req.getTargetTableId())) {
                        List<TableRelation> newPath = new ArrayList<>(currentPath);
                        newPath.add(next);
                        queue.offer(newPath);
                    }
                }
                visited.add(currentNode);
            }
        }

        paths.sort(Comparator.comparingInt(PathResult.PathInfo::getLength));

        PathResult result = new PathResult();
        result.setPaths(paths);
        return result;
    }
}
