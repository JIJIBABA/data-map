package com.datamap.scanner.input;

import org.junit.jupiter.api.Test;
import java.nio.file.*;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

public class GitSourceTest {
    @Test
    public void diffFilesReturnsChangedFile() throws Exception {
        Path repo = Files.createTempDirectory("repo");
        GitSource.run(repo, "git", "init");
        GitSource.run(repo, "git", "config", "user.email", "t@t.com");
        GitSource.run(repo, "git", "config", "user.name", "t");
        Files.writeString(repo.resolve("A.java"), "class A {}");
        GitSource.run(repo, "git", "add", ".");
        GitSource.run(repo, "git", "commit", "-m", "c1");
        Files.writeString(repo.resolve("A.java"), "class A { int x; }");
        GitSource.run(repo, "git", "commit", "-am", "c2");

        List<String> changed = GitSource.diffFiles(repo, "HEAD~1", "HEAD");
        assertEquals(List.of("A.java"), changed);
    }
}
