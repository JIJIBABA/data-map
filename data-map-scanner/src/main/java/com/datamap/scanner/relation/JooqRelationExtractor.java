package com.datamap.scanner.relation;

import com.datamap.scanner.javac.AnalysisContext;
import com.datamap.scanner.model.ScanRelation;
import javax.lang.model.element.TypeElement;
import java.util.List;
import java.util.Map;

public class JooqRelationExtractor {
    // 初版：返回空。JOOQ 项目接入时补全（.join().on() 调用链提取）。
    public static List<ScanRelation> extract(AnalysisContext ctx, Map<String, TypeElement> entities) {
        return List.of();
    }
}
