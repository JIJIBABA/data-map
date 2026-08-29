package com.datamap.scanner.usage;

import javax.lang.model.element.ExecutableElement;

public class FieldAccess {
    public enum Kind { READ, WRITE }
    public final String fieldName;
    public final Kind kind;
    public final ExecutableElement method;
    public FieldAccess(String fieldName, Kind kind, ExecutableElement method) {
        this.fieldName = fieldName; this.kind = kind; this.method = method;
    }
}
