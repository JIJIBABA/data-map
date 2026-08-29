package com.datamap.scanner.model;
public class ScanField {
    public final String fieldName;
    public final String fieldComment;
    public final String fieldType;
    public final boolean pk;
    public final boolean businessField;
    public ScanField(String fieldName, String fieldComment, String fieldType, boolean pk, boolean businessField) {
        this.fieldName = fieldName; this.fieldComment = fieldComment; this.fieldType = fieldType;
        this.pk = pk; this.businessField = businessField;
    }
}
