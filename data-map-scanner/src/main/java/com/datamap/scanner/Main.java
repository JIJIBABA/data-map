package com.datamap.scanner;

import com.datamap.scanner.input.GitSource;
import com.datamap.scanner.input.SourceCollector;
import com.datamap.scanner.javac.AnalysisContext;
import com.datamap.scanner.javac.JavaParser;
import com.datamap.scanner.model.ScanResult;
import com.datamap.scanner.output.JsonWriter;
import com.datamap.scanner.output.ScanResultAssembler;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

public class Main {
    public static void main(String[] args) throws Exception {
        Map<String, String> opts = parse(args);
        String table = opts.get("table");
        Path root;
        Set<String> changedFiles = null;
        String scanType;

        if (opts.containsKey("repo")) {
            Path tmp = Files.createTempDirectory("scanner-repo");
            String diff = opts.get("diff");
            if (diff != null) {
                String[] parts = diff.split("\\.\\.\\.");
                GitSource.checkout(opts.get("repo"), parts[1], tmp);
                changedFiles = new HashSet<>(GitSource.diffFiles(tmp, parts[0], parts[1]));
                scanType = "DIFF";
            } else {
                GitSource.checkout(opts.get("repo"), opts.getOrDefault("ref", "HEAD"), tmp);
                scanType = "FULL";
            }
            root = tmp;
        } else {
            root = Paths.get(opts.getOrDefault("path", "."));
            scanType = table != null ? "TABLE" : "FULL";
        }

        AnalysisContext ctx = JavaParser.parse(SourceCollector.javaFiles(root), "");
        ScanResult result = ScanResultAssembler.assemble(ctx, SourceCollector.xmlFiles(root),
            "demo", scanType, table, changedFiles);

        String out = opts.getOrDefault("o", "scan-result.json");
        JsonWriter.write(result, Paths.get(out));
        System.out.println("扫描完成: " + out + " (" + result.tables.size() + " 张表)");

        if (opts.containsKey("submit")) {
            int code = com.datamap.scanner.persist.ApiSubmitter.submit(result, opts.get("submit"));
            System.out.println("POST " + opts.get("submit") + " -> " + code);
        }
        if (opts.containsKey("db")) {
            String[] parts = opts.get("db").split(";");
            com.datamap.scanner.persist.JdbcWriter.write(result, parts[0], parts[1], parts[2]);
            System.out.println("JDBC 直连写入完成");
        }
    }

    private static Map<String, String> parse(String[] args) {
        Map<String, String> opts = new HashMap<>();
        for (int i = 0; i < args.length; i++) {
            String a = args[i];
            if (a.startsWith("--")) {
                String key = a.substring(2);
                if (i + 1 < args.length && !args[i + 1].startsWith("--")) {
                    opts.put(key, args[++i]);
                } else {
                    opts.put(key, "true");
                }
            } else if (a.startsWith("-") && a.length() > 1) {
                String key = a.substring(1);
                if (i + 1 < args.length && !args[i + 1].startsWith("-")) {
                    opts.put(key, args[++i]);
                } else {
                    opts.put(key, "true");
                }
            }
        }
        return opts;
    }
}
