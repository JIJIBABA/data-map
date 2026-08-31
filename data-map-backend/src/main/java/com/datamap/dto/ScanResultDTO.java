package com.datamap.dto;

import java.util.List;

public class ScanResultDTO {
    private ProjectDTO project;
    private String scanType;
    private List<TableDTO> tables;

    public static class ProjectDTO {
        private String appName;
        private String gitRepoUrl;
        private String gitLocalPath;
        public String getAppName() { return appName; }
        public void setAppName(String appName) { this.appName = appName; }
        public String getGitRepoUrl() { return gitRepoUrl; }
        public void setGitRepoUrl(String gitRepoUrl) { this.gitRepoUrl = gitRepoUrl; }
        public String getGitLocalPath() { return gitLocalPath; }
        public void setGitLocalPath(String gitLocalPath) { this.gitLocalPath = gitLocalPath; }
    }

    public static class TableDTO {
        private String tableName;
        private String tableComment;
        private String schemaName;
        private String dbType;
        private List<FieldDTO> fields;
        private List<RelationDTO> relations;
        private List<UsageScenarioDTO> usageScenarios;

        public String getTableName() { return tableName; }
        public void setTableName(String tableName) { this.tableName = tableName; }
        public String getTableComment() { return tableComment; }
        public void setTableComment(String tableComment) { this.tableComment = tableComment; }
        public String getSchemaName() { return schemaName; }
        public void setSchemaName(String schemaName) { this.schemaName = schemaName; }
        public String getDbType() { return dbType; }
        public void setDbType(String dbType) { this.dbType = dbType; }
        public List<FieldDTO> getFields() { return fields; }
        public void setFields(List<FieldDTO> fields) { this.fields = fields; }
        public List<RelationDTO> getRelations() { return relations; }
        public void setRelations(List<RelationDTO> relations) { this.relations = relations; }
        public List<UsageScenarioDTO> getUsageScenarios() { return usageScenarios; }
        public void setUsageScenarios(List<UsageScenarioDTO> usageScenarios) { this.usageScenarios = usageScenarios; }
    }

    public static class FieldDTO {
        private String fieldName;
        private String fieldComment;
        private String fieldType;
        private Boolean isPk;
        private Boolean isBusinessField;
        public String getFieldName() { return fieldName; }
        public void setFieldName(String fieldName) { this.fieldName = fieldName; }
        public String getFieldComment() { return fieldComment; }
        public void setFieldComment(String fieldComment) { this.fieldComment = fieldComment; }
        public String getFieldType() { return fieldType; }
        public void setFieldType(String fieldType) { this.fieldType = fieldType; }
        public Boolean getIsPk() { return isPk; }
        public void setIsPk(Boolean isPk) { this.isPk = isPk; }
        public Boolean getIsBusinessField() { return isBusinessField; }
        public void setIsBusinessField(Boolean isBusinessField) { this.isBusinessField = isBusinessField; }
    }

    public static class RelationDTO {
        private String sourceFieldName;
        private String targetTableName;
        private String targetFieldName;
        private String relationType;
        private String methodSignature;
        public String getSourceFieldName() { return sourceFieldName; }
        public void setSourceFieldName(String sourceFieldName) { this.sourceFieldName = sourceFieldName; }
        public String getTargetTableName() { return targetTableName; }
        public void setTargetTableName(String targetTableName) { this.targetTableName = targetTableName; }
        public String getTargetFieldName() { return targetFieldName; }
        public void setTargetFieldName(String targetFieldName) { this.targetFieldName = targetFieldName; }
        public String getRelationType() { return relationType; }
        public void setRelationType(String relationType) { this.relationType = relationType; }
        public String getMethodSignature() { return methodSignature; }
        public void setMethodSignature(String methodSignature) { this.methodSignature = methodSignature; }
    }

    public static class UsageScenarioDTO {
        private String fieldName;
        private String operationType;
        private String scenarioDescription;
        private String methodName;
        private String sourceTableName;
        private String sourceApiName;
        private String methodDescription;
        private String descriptionSource;
        private List<CallChainStepDTO> callChain;
        private EntryInfoDTO entry;

        public static class CallChainStepDTO {
            private String className;
            private String methodName;
            private String signature;
            private String layer;
            public String getClassName() { return className; }
            public void setClassName(String v) { this.className = v; }
            public String getMethodName() { return methodName; }
            public void setMethodName(String v) { this.methodName = v; }
            public String getSignature() { return signature; }
            public void setSignature(String v) { this.signature = v; }
            public String getLayer() { return layer; }
            public void setLayer(String v) { this.layer = v; }
        }

        public static class EntryInfoDTO {
            private String type;
            private String apiName;
            private String httpMethod;
            private String path;
            private String queue;
            private String cron;
            public String getType() { return type; }
            public void setType(String v) { this.type = v; }
            public String getApiName() { return apiName; }
            public void setApiName(String v) { this.apiName = v; }
            public String getHttpMethod() { return httpMethod; }
            public void setHttpMethod(String v) { this.httpMethod = v; }
            public String getPath() { return path; }
            public void setPath(String v) { this.path = v; }
            public String getQueue() { return queue; }
            public void setQueue(String v) { this.queue = v; }
            public String getCron() { return cron; }
            public void setCron(String v) { this.cron = v; }
        }

        public String getFieldName() { return fieldName; }
        public void setFieldName(String fieldName) { this.fieldName = fieldName; }
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
        public String getMethodDescription() { return methodDescription; }
        public void setMethodDescription(String v) { this.methodDescription = v; }
        public String getDescriptionSource() { return descriptionSource; }
        public void setDescriptionSource(String v) { this.descriptionSource = v; }
        public List<CallChainStepDTO> getCallChain() { return callChain; }
        public void setCallChain(List<CallChainStepDTO> v) { this.callChain = v; }
        public EntryInfoDTO getEntry() { return entry; }
        public void setEntry(EntryInfoDTO v) { this.entry = v; }
    }

    public ProjectDTO getProject() { return project; }
    public void setProject(ProjectDTO project) { this.project = project; }
    public String getScanType() { return scanType; }
    public void setScanType(String scanType) { this.scanType = scanType; }
    public List<TableDTO> getTables() { return tables; }
    public void setTables(List<TableDTO> tables) { this.tables = tables; }
}
