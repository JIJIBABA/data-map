package com.datamap.scanner.model;
public class ScanProject {
    public final String appName;
    public final String gitRepoUrl;
    public final String gitLocalPath;
    public ScanProject(String appName, String gitRepoUrl, String gitLocalPath) {
        this.appName = appName; this.gitRepoUrl = gitRepoUrl; this.gitLocalPath = gitLocalPath;
    }
}
