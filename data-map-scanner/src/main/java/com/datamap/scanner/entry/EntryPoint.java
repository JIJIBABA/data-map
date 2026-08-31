package com.datamap.scanner.entry;

import com.datamap.scanner.model.EntryInfo;
import javax.lang.model.element.ExecutableElement;

public class EntryPoint {
    public final ExecutableElement method;
    public final EntryInfo info;
    public EntryPoint(ExecutableElement method, EntryInfo info) {
        this.method = method; this.info = info;
    }
}
