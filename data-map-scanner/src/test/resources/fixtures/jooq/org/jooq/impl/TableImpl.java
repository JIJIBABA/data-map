package org.jooq.impl;

import org.jooq.Table;
import org.jooq.TableField;

public class TableImpl<R> extends Table<R> {
    public TableImpl() {
    }

    public <T> TableField<R, T> createField(String name, SQLDataType type, Table<R> table, String comment) {
        return null;
    }

    public <T> TableField<R, T> createField(String name, SQLDataType type, Table<R> table) {
        return null;
    }
}
