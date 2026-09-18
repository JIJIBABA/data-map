package com.datamap.scanner.input;

import org.junit.jupiter.api.Test;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

public class GitSourceTest {
    @Test
    public void diffFilesReturnsChangedFile() throws Exception {
        Path repo = Files.createTempDirectory("repo");
        GitSource.run(repo, "git", "init");
        GitSource.run(repo, "git", "config", "user.email", "t@t.com");
        GitSource.run(repo, "git", "config", "user.name", "t");
        Files.write(repo.resolve("A.java"), "class A {}".getBytes(StandardCharsets.UTF_8));
        GitSource.run(repo, "git", "add", ".");
        GitSource.run(repo, "git", "commit", "-m", "c1");
        Files.write(repo.resolve("A.java"), "class A { int x; }".getBytes(StandardCharsets.UTF_8));
        GitSource.run(repo, "git", "commit", "-am", "c2");

        List<String> changed = GitSource.diffFiles(repo, "HEAD~1", "HEAD");
        assertEquals(Collections.singletonList("A.java"), changed);
    }
}
