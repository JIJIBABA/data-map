package com.datamap.dto;

import java.util.List;

public class TableRelationVO {
    private List<GraphNode> nodes;
    private List<GraphEdge> edges;
    private List<RelationDetail> details;

    public static class GraphNode {
        private Long id;
        private String label;
        public GraphNode() {}
        public GraphNode(Long id, String label) { this.id = id; this.label = label; }
        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public String getLabel() { return label; }
        public void setLabel(String label) { this.label = label; }
    }

    public static class GraphEdge {
        private Long source;
        private Long target;
        private String sourceField;
        private String targetField;
        public Long getSource() { return source; }
        public void setSource(Long source) { this.source = source; }
        public Long getTarget() { return target; }
        public void setTarget(Long target) { this.target = target; }
        public String getSourceField() { return sourceField; }
        public void setSourceField(String sourceField) { this.sourceField = sourceField; }
        public String getTargetField() { return targetField; }
        public void setTargetField(String targetField) { this.targetField = targetField; }
    }

    public static class RelationDetail {
        private String sourceTable;
        private Long sourceTableId;
        private String sourceField;
        private String targetTable;
        private Long targetTableId;
        private String targetField;
        private String relationType;
        private String methodSignature;

        public String getSourceTable() { return sourceTable; }
        public void setSourceTable(String sourceTable) { this.sourceTable = sourceTable; }
        public Long getSourceTableId() { return sourceTableId; }
        public void setSourceTableId(Long sourceTableId) { this.sourceTableId = sourceTableId; }
        public String getSourceField() { return sourceField; }
        public void setSourceField(String sourceField) { this.sourceField = sourceField; }
        public String getTargetTable() { return targetTable; }
        public void setTargetTable(String targetTable) { this.targetTable = targetTable; }
        public Long getTargetTableId() { return targetTableId; }
        public void setTargetTableId(Long targetTableId) { this.targetTableId = targetTableId; }
        public String getTargetField() { return targetField; }
        public void setTargetField(String targetField) { this.targetField = targetField; }
        public String getRelationType() { return relationType; }
        public void setRelationType(String relationType) { this.relationType = relationType; }
        public String getMethodSignature() { return methodSignature; }
        public void setMethodSignature(String methodSignature) { this.methodSignature = methodSignature; }
    }

    public List<GraphNode> getNodes() { return nodes; }
    public void setNodes(List<GraphNode> nodes) { this.nodes = nodes; }
    public List<GraphEdge> getEdges() { return edges; }
    public void setEdges(List<GraphEdge> edges) { this.edges = edges; }
    public List<RelationDetail> getDetails() { return details; }
    public void setDetails(List<RelationDetail> details) { this.details = details; }
}
