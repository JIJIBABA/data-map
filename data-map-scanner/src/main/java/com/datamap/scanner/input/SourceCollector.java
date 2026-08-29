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
            s.filter(p -> p.toString().endsWith(suffix)
                    && !p.toString().contains("/target/"))
             .forEach(out::add);
        }
        return out;
    }
}
