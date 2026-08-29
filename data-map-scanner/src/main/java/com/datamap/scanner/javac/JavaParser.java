package com.datamap.scanner.javac;

import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.util.JavacTask;
import javax.tools.*;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class JavaParser {
    public static AnalysisContext parse(List<Path> javaFiles, String classpath) {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) throw new IllegalStateException("未找到 JDK 编译器（需用 JDK 运行，不能用 JRE）");
        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        try (StandardJavaFileManager fm = compiler.getStandardFileManager(diagnostics, Locale.ROOT, StandardCharsets.UTF_8)) {
            List<File> files = new ArrayList<>();
            for (Path p : javaFiles) files.add(p.toFile());
            Iterable<? extends JavaFileObject> units = fm.getJavaFileObjectsFromFiles(files);
            List<String> options = new ArrayList<>();
            options.add("-classpath"); options.add(classpath == null ? "" : classpath);
            options.add("-proc:none");
            JavacTask task = (JavacTask) compiler.getTask(null, fm, diagnostics, options, null, units);
            Iterable<? extends CompilationUnitTree> parsed = task.parse();
            task.analyze(); // 类型归因；错误收集到 diagnostics，不影响已解析的本地符号
            return new AnalysisContext(task, parsed);
        } catch (Exception e) {
            throw new RuntimeException("javac 解析失败: " + e.getMessage(), e);
        }
    }
}
