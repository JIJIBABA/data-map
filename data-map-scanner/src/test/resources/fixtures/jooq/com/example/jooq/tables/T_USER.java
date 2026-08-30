package com.example.jooq.tables;

import org.jooq.TableField;
import org.jooq.UniqueKey;
import org.jooq.impl.TableImpl;

public class T_USER extends TableImpl<TUserRecord> {
    public final TableField<TUserRecord, Long> USER_ID =
        createField("USER_ID", org.jooq.impl.SQLDataType.BIGINT.nullable(false), this, "用户ID");

    public UniqueKey<TUserRecord> getPrimaryKey() {
        return Keys.KEY_T_USER_PRIMARY;
    }
}
