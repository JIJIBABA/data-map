package com.example.jooq.tables;

import org.jooq.TableField;
import org.jooq.UniqueKey;
import org.jooq.impl.TableImpl;

public class T_ORDER extends TableImpl<TOrderRecord> {
    public final TableField<TOrderRecord, Long> SEQUENCE_NO =
        createField("SEQUENCE_NO", org.jooq.impl.SQLDataType.BIGINT.nullable(false), this, "顺序号");
    public final TableField<TOrderRecord, String> ORDER_STATUS =
        createField("ORDER_STATUS", org.jooq.impl.SQLDataType.VARCHAR, this, "状态");

    public UniqueKey<TOrderRecord> getPrimaryKey() {
        return Keys.KEY_T_ORDER_PRIMARY;
    }
}
