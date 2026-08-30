package com.datamap.scanner.jooq;

import javax.lang.model.element.ExecutableElement;

/** JOOQ 字段级访问场景：列名 + 操作类型 + 直接方法（供调用链使用）。 */
public class JooqFieldAccess {
    public final String fieldName;      // 列名，如 "ORDER_STATUS"
    public final String operationType;  // WRITE | UPDATE | READ | DELETE
    public final ExecutableElement method; // 所在（直接）方法

    public JooqFieldAccess(String fieldName, String operationType, ExecutableElement method) {
        this.fieldName = fieldName;
        this.operationType = operationType;
        this.method = method;
    }
}
