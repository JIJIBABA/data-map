package com.example.jooq.tables;

import org.jooq.TableField;
import org.jooq.UniqueKey;
import org.jooq.impl.UniqueKeyImpl;

public class Keys {
    public static final UniqueKey<TOrderRecord> KEY_T_ORDER_PRIMARY =
        new UniqueKeyImpl<TOrderRecord>("T_ORDER", T_ORDER.SEQUENCE_NO);
    public static final UniqueKey<TUserRecord> KEY_T_USER_PRIMARY =
        new UniqueKeyImpl<TUserRecord>("T_USER", T_USER.USER_ID);
}
