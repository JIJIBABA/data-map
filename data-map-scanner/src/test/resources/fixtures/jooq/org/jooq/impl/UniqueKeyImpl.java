package org.jooq.impl;

import org.jooq.TableField;
import org.jooq.UniqueKey;

public class UniqueKeyImpl<R> implements UniqueKey<R> {
    public UniqueKeyImpl(String name, TableField<R, ?>... fields) {
    }
}
