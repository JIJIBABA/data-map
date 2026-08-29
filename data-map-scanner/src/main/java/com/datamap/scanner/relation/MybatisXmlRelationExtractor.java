package com.datamap.scanner.relation;

import com.datamap.scanner.model.ScanRelation;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MybatisXmlRelationExtractor {
    // 匹配 JOIN ... tb_user u ON o.user_id = u.user_id
    private static final Pattern JOIN = Pattern.compile(
        "JOIN\\s+(\\w+)\\s+(\\w+)\\s+ON\\s+(\\w+)\\.(\\w+)\\s*=\\s*(\\w+)\\.(\\w+)",
        Pattern.CASE_INSENSITIVE);

    public static List<ScanRelation> extract(Path xmlFile) throws Exception {
        List<ScanRelation> out = new ArrayList<>();
        String text = new String(Files.readAllBytes(xmlFile), java.nio.charset.StandardCharsets.UTF_8);
        Matcher m = JOIN.matcher(text);
        while (m.find()) {
            // 左侧为主表(别名 o)，右侧为被 join 表(别名 u)
            String leftCol = m.group(4);
            String rightTable = m.group(1);
            String rightCol = m.group(6);
            out.add(new ScanRelation(leftCol, rightTable, rightCol, "DIRECT_JOIN", ""));
        }
        return out;
    }
}
