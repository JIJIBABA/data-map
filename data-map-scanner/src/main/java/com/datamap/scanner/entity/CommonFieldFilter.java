package com.datamap.scanner.entity;

import java.util.Set;

public class CommonFieldFilter {
    private static final Set<String> COMMON = Set.of(
        "pk","id","created_at","create_time","updated_at","update_time","deleted_at","delete_time",
        "is_deleted","create_user","create_by","update_user","update_by","delete_user","delete_by",
        "version","tenant_id");

    public static boolean isBusiness(String fieldName) {
        return !COMMON.contains(fieldName);
    }
}
