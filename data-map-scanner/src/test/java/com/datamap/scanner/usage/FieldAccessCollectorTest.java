package com.datamap.scanner.usage;

import com.datamap.scanner.entity.AliasFieldResolver;
import com.datamap.scanner.entity.EntityResolver;
import com.datamap.scanner.javac.AnalysisContext;
import com.datamap.scanner.javac.JavaParser;
import org.junit.jupiter.api.Test;
import javax.lang.model.element.TypeElement;
import java.nio.file.*;
import java.util.*;
import java.util.stream.*;
import static org.junit.jupiter.api.Assertions.*;

public class FieldAccessCollectorTest {
    private Map<String, List<FieldAccess>> collect() throws Exception {
        Path root = Paths.get("src/test/resources/fixtures/demo");
        List<Path> files;
        try (Stream<Path> s = Files.walk(root)) {
            files = s.filter(p -> p.toString().endsWith(".java")).collect(Collectors.toList());
        }
        AnalysisContext ctx = JavaParser.parse(files, "");
        Map<String, java.util.List<TypeElement>> entities = EntityResolver.resolve(ctx);
        return FieldAccessCollector.collect(ctx, entities, Map.of());
    }

    @Test
    public void findsWriteOfOrderStatus() throws Exception {
        Map<String, List<FieldAccess>> all = collect();
        assertTrue(all.containsKey("tb_order.order_status"));
        List<FieldAccess> accesses = all.get("tb_order.order_status");
        assertEquals(2, accesses.size()); // createOrder 与 cancelOrder 各写一次
        assertTrue(accesses.stream().allMatch(a -> a.kind == FieldAccess.Kind.WRITE));
    }

    /**
     * 演变场景：表实体字段经 setter 复制到 DTO 后，在 DTO 上读取应归到表字段。
     * 镜像 EdtTencentPartnerServiceImpl 在 DTO 上读 tradeProductMode 的真实场景。
     */
    @Test
    public void attributesDtoAccessToTableFieldViaAlias() throws Exception {
        Path root = Paths.get("src/test/resources/fixtures/alias");
        List<Path> files;
        try (Stream<Path> s = Files.walk(root)) {
            files = s.filter(p -> p.toString().endsWith(".java")).collect(Collectors.toList());
        }
        AnalysisContext ctx = JavaParser.parse(files, "");
        Map<String, java.util.List<TypeElement>> entities = EntityResolver.resolve(ctx);
        Map<String, String> aliases = AliasFieldResolver.resolve(ctx, entities, Map.of());
        Map<String, List<FieldAccess>> all = FieldAccessCollector.collect(ctx, entities, aliases);

        // 直接 DTO 读取应归到 t_collect_info.trade_product_mode
        assertTrue(all.containsKey("t_collect_info.trade_product_mode"),
            "DTO 上的 tradeProductMode 读取应通过别名归到表字段");
        List<FieldAccess> tpm = all.get("t_collect_info.trade_product_mode");
        assertTrue(tpm.stream().anyMatch(a -> a.kind == FieldAccess.Kind.READ
            && a.method.toString().contains("readFromDto")), "应捕获 readFromDto 的读取");
        // 二次演变 DTO2 读取同样归到表字段
        assertTrue(tpm.stream().anyMatch(a -> a.kind == FieldAccess.Kind.READ
            && a.method.toString().contains("readFromDto2")), "应捕获 readFromDto2 的传递读取");
    }
}
