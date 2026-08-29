package com.datamap.scanner.model;
import java.util.List;

public class ScanResult {
    public final ScanProject project;
    public final String scanType;
    public final List<ScanTable> tables;
    public ScanResult(ScanProject project, String scanType, List<ScanTable> tables) {
        this.project = project; this.scanType = scanType; this.tables = tables;
    }
}
