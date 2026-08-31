package com.datamap.scanner.model;
public class ScanRelation {
    public final String sourceFieldName;
    public final String targetTableName;
    public final String targetFieldName;
    public final String relationType;
    public final String methodSignature;
    public ScanRelation(String sourceFieldName, String targetTableName, String targetFieldName,
                        String relationType, String methodSignature) {
        this.sourceFieldName = sourceFieldName; this.targetTableName = targetTableName;
        this.targetFieldName = targetFieldName; this.relationType = relationType; this.methodSignature = methodSignature;
    }
}
