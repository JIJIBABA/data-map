package com.datamap.scanner.model;
import java.util.List;
public class UsageScenario {
    public final String fieldName;
    public final String operationType;
    public final String methodName;
    public final String methodDescription;
    public final String descriptionSource;
    public final String sourceTableName;
    public final String sourceApiName;
    public final List<CallChainStep> callChain;
    public final EntryInfo entry;
    public UsageScenario(String fieldName, String operationType, String methodName, String methodDescription,
                         String descriptionSource, String sourceTableName, String sourceApiName,
                         List<CallChainStep> callChain, EntryInfo entry) {
        this.fieldName = fieldName; this.operationType = operationType; this.methodName = methodName;
        this.methodDescription = methodDescription; this.descriptionSource = descriptionSource;
        this.sourceTableName = sourceTableName; this.sourceApiName = sourceApiName;
        this.callChain = callChain; this.entry = entry;
    }
}
