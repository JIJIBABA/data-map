package com.datamap.scanner.input;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

public class SourceCollector {
    public static List<Path> javaFiles(Path root) throws Exception {
        return collect(root, ".java");
    }
    public static List<Path> xmlFiles(Path root) throws Exception {
        return collect(root, ".xml");
    }
    private static List<Path> collect(Path root, String suffix) throws Exception {
        List<Path> out = new ArrayList<>();
        try (Stream<Path> s = Files.walk(root)) {
            s.filter(p -> {
                    String path = p.toString().replace('\\', '/');
                    // 跳过构建产物目录与 Maven 测试源码根 src/test/java（避免扫描测试类污染调用链/场景）。
                    // 用「/src/test/java/」精确匹配，保留 src/test/resources 下的测试 fixture
                    // （扫描器自身单测以 fixture 当作被扫应用）。
                    if (path.contains("/target/")) return false;
                    if (path.contains("/src/test/java/")) return false;
                    return path.endsWith(suffix);
                })
             .forEach(out::add);
        }
        return out;
    }
}

