package com.datamap.scanner.model;
public class EntryInfo {
    public final String type;
    public final String apiName;
    public final String httpMethod;
    public final String path;
    public final String queue;
    public final String cron;
    public EntryInfo(String type, String apiName, String httpMethod, String path, String queue, String cron) {
        this.type = type; this.apiName = apiName; this.httpMethod = httpMethod; this.path = path;
        this.queue = queue; this.cron = cron;
    }
}
