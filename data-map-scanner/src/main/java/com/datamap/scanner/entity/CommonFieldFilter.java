package com.datamap.scanner.entity;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public class CommonFieldFilter {
    private static final Set<String> COMMON = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
        "pk","id","created_at","create_time","updated_at","update_time","deleted_at","delete_time",
        "is_deleted","create_user","create_by","update_user","update_by","delete_user","delete_by",
        "version","tenant_id")));

    public static boolean isBusiness(String fieldName) {
        return !COMMON.contains(fieldName);
    }
}
