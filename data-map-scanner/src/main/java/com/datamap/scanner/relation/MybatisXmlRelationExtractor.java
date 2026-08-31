package com.datamap.scanner.relation;

import com.datamap.scanner.model.ScanRelation;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MybatisXmlRelationExtractor {
    // 匹配 JOIN ... tb_user u ON o.user_id = u.user_id
    private static final Pattern JOIN = Pattern.compile(
        "JOIN\\s+(\\w+)\\s+(\\w+)\\s+ON\\s+(\\w+)\\.(\\w+)\\s*=\\s*(\\w+)\\.(\\w+)",
        Pattern.CASE_INSENSITIVE);
    // 匹配 FROM tb_order o，捕获 FROM 子句的左表（关联的源表）
    private static final Pattern FROM = Pattern.compile(
        "\\bFROM\\s+(\\w+)\\s+(\\w+)", Pattern.CASE_INSENSITIVE);

    /** 兼容旧签名：展平返回所有 JOIN 关联（不区分源表）。 */
    public static List<ScanRelation> extract(Path xmlFile) throws Exception {
        List<ScanRelation> out = new ArrayList<>();
        for (List<ScanRelation> rels : extractBySourceTable(xmlFile).values()) {
            out.addAll(rels);
        }
        return out;
    }

    /** 按 FROM 左表（源表）分组返回 JOIN 关联，供结果组装把关联归属到正确源表。 */
    public static Map<String, List<ScanRelation>> extractBySourceTable(Path xmlFile) throws Exception {
        Map<String, List<ScanRelation>> out = new LinkedHashMap<>();
        String text = new String(Files.readAllBytes(xmlFile), StandardCharsets.UTF_8);
        Matcher m = JOIN.matcher(text);
        while (m.find()) {
            // 左侧为主表(别名 o)，右侧为被 join 表(别名 u)
            String leftCol = m.group(4);
            String rightTable = m.group(1);
            String rightCol = m.group(6);
            String sourceTable = sourceTableBefore(text, m.start());
            out.computeIfAbsent(sourceTable, k -> new ArrayList<>())
               .add(new ScanRelation(leftCol, rightTable, rightCol, "DIRECT_JOIN", ""));
        }
        return out;
    }

    /** 取 JOIN 之前最近一个 FROM 子句的左表名（支持同一 XML 多个 SELECT）。 */
    private static String sourceTableBefore(String text, int pos) {
        Matcher fm = FROM.matcher(text.substring(0, pos));
        String source = "";
        while (fm.find()) source = fm.group(1);
        return source;
    }
}
