package com.datamap.scanner.relation;

import com.datamap.scanner.model.ScanField;
import com.datamap.scanner.model.ScanRelation;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 关联关系：pk_/fk_ 命名约定。
 * 表 A 含 {@code pk_X}、表 B 含 {@code fk_X}（去掉前缀、忽略大小写后同名）
 * => {@code B.fk_X -> A.pk_X}（FK 表引用 PK 表）。
 */
public class PkFkRelationExtractor {

    private static final class PkRef {
        final String table;
        final String field;
        PkRef(String table, String field) { this.table = table; this.field = field; }
    }

    public static Map<String, List<ScanRelation>> extract(Map<String, List<ScanField>> tablesByName) {
        // 后缀（小写）-> 持有 pk_<后缀> 的表/字段列表
        Map<String, List<PkRef>> pkBySuffix = new HashMap<>();
        for (Map.Entry<String, List<ScanField>> e : tablesByName.entrySet()) {
            for (ScanField f : e.getValue()) {
                String lower = f.fieldName == null ? "" : f.fieldName.toLowerCase();
                if (lower.startsWith("pk_") && lower.length() > 3) {
                    pkBySuffix.computeIfAbsent(lower.substring(3), k -> new ArrayList<>())
                              .add(new PkRef(e.getKey(), f.fieldName));
                }
            }
        }

        Map<String, List<ScanRelation>> result = new HashMap<>();
        for (Map.Entry<String, List<ScanField>> e : tablesByName.entrySet()) {
            String fkTable = e.getKey();
            for (ScanField f : e.getValue()) {
                String lower = f.fieldName == null ? "" : f.fieldName.toLowerCase();
                if (!lower.startsWith("fk_") || lower.length() <= 3) continue;
                List<PkRef> pks = pkBySuffix.get(lower.substring(3));
                if (pks == null) continue;
                for (PkRef pk : pks) {
                    if (pk.table.equals(fkTable)) continue;
                    result.computeIfAbsent(fkTable, k -> new ArrayList<>())
                          .add(new ScanRelation(f.fieldName, pk.table, pk.field, "DIRECT_JOIN", ""));
                }
            }
        }
        return result;
    }
}
