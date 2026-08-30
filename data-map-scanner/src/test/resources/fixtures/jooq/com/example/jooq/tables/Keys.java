package com.example.jooq.tables;

import org.jooq.TableField;
import org.jooq.UniqueKey;
import org.jooq.impl.UniqueKeyImpl;

public class Keys {
    public static final UniqueKey<TOrderRecord> KEY_T_ORDER_PRIMARY =
        new UniqueKeyImpl<TOrderRecord>("T_ORDER", T_ORDER.SEQUENCE_NO);
}
