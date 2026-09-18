package com.datamap.scanner.entity;

import com.datamap.scanner.javac.AnalysisContext;
import com.datamap.scanner.javac.JavaParser;
import org.junit.jupiter.api.Test;

import javax.lang.model.element.TypeElement;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 验证实体演变别名解析：表实体字段 -> DTO（直接 setter←getter）-> DTO2（传递闭合）。
 */
public class AliasFieldResolverTest {

    private Map<String, String> resolve() throws Exception {
        Path root = Paths.get("src/test/resources/fixtures/alias");
        List<Path> files;
        try (Stream<Path> s = Files.walk(root)) {
            files = s.filter(p -> p.toString().endsWith(".java")).collect(Collectors.toList());
        }
        AnalysisContext ctx = JavaParser.parse(files, "");
        Map<String, java.util.List<TypeElement>> entities = EntityResolver.resolve(ctx);
        return AliasFieldResolver.resolve(ctx, entities, Collections.emptyMap());
    }

    @Test
    public void directDtoFieldAliasedToTable() throws Exception {
        Map<String, String> aliases = resolve();
        // CollectInfoDTO.tradeProductMode -> t_collect_info.trade_product_mode
        String aliased = aliases.get("com.example.dto.CollectInfoDTO#trade_product_mode");
        assertNotNull(aliased, "DTO 的 trade_product_mode 字段应被别名解析");
        assertEquals("t_collect_info.trade_product_mode", aliased);
        // product_mode 同样
        assertEquals("t_collect_info.product_mode",
            aliases.get("com.example.dto.CollectInfoDTO#product_mode"));
    }

    @Test
    public void transitiveDto2FieldAliasedToTable() throws Exception {
        Map<String, String> aliases = resolve();
        // CollectInfoDTO2.tradeProductMode 经 DTO 传递闭合 -> t_collect_info.trade_product_mode
        String aliased = aliases.get("com.example.dto.CollectInfoDTO2#trade_product_mode");
        assertNotNull(aliased, "二次演变 DTO2 的字段应经传递闭合被别名解析");
        assertEquals("t_collect_info.trade_product_mode", aliased);
    }
}
