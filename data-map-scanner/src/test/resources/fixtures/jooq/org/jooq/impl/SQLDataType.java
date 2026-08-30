package org.jooq.impl;

public class SQLDataType {
    public static final SQLDataType BIGINT = new SQLDataType();
    public static final SQLDataType VARCHAR = new SQLDataType();
    public static final SQLDataType NUMERIC = new SQLDataType();
    public static final SQLDataType INTEGER = new SQLDataType();
    public static final SQLDataType TIMESTAMP = new SQLDataType();
    public static final SQLDataType BOOLEAN = new SQLDataType();

    public SQLDataType nullable(boolean nullable) { return this; }
    public SQLDataType length(int length) { return this; }
}
