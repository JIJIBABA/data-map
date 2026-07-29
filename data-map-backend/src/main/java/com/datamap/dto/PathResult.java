package com.datamap.dto;

import java.util.List;

public class PathResult {
    private List<PathInfo> paths;

    public static class PathInfo {
        private List<String> nodes;
        private List<EdgeInfo> edges;
        private Integer length;

        public List<String> getNodes() { return nodes; }
        public void setNodes(List<String> nodes) { this.nodes = nodes; }
        public List<EdgeInfo> getEdges() { return edges; }
        public void setEdges(List<EdgeInfo> edges) { this.edges = edges; }
        public Integer getLength() { return length; }
        public void setLength(Integer length) { this.length = length; }
    }

    public static class EdgeInfo {
        private String from;
        private String to;
        private String joinField;

        public String getFrom() { return from; }
        public void setFrom(String from) { this.from = from; }
        public String getTo() { return to; }
        public void setTo(String to) { this.to = to; }
        public String getJoinField() { return joinField; }
        public void setJoinField(String joinField) { this.joinField = joinField; }
    }

    public List<PathInfo> getPaths() { return paths; }
    public void setPaths(List<PathInfo> paths) { this.paths = paths; }
}
