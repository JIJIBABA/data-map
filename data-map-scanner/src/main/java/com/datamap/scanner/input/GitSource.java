package com.datamap.scanner.input;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class GitSource {
    public static void checkout(String repoUrl, String ref, Path dir) throws Exception {
        run(dir.getParent(), "git", "clone", repoUrl, dir.toString());
        run(dir, "git", "checkout", ref);
    }

    /** base...head 之间变更的文件相对路径。 */
    public static List<String> diffFiles(Path dir, String base, String head) throws Exception {
        return run(dir, "git", "diff", "--name-only", base + "..." + head);
    }

    static List<String> run(Path workDir, String... cmd) throws Exception {
        ProcessBuilder pb = new ProcessBuilder(cmd);
        if (workDir != null) pb.directory(workDir.toFile());
        pb.redirectErrorStream(true);
        Process p = pb.start();
        String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        int code = p.waitFor();
        if (code != 0) throw new RuntimeException("git 命令失败(" + code + "): " + String.join(" ", cmd) + "\n" + out);
        List<String> lines = new ArrayList<>();
        for (String line : out.split("\n")) if (!line.trim().isEmpty()) lines.add(line.trim());
        return lines;
    }
}
