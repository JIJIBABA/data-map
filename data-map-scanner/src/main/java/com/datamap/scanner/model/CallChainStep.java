package com.datamap.scanner.model;
public class CallChainStep {
    public final String className;
    public final String methodName;
    public final String signature;
    public final String layer;
    public CallChainStep(String className, String methodName, String signature, String layer) {
        this.className = className; this.methodName = methodName; this.signature = signature; this.layer = layer;
    }
}
