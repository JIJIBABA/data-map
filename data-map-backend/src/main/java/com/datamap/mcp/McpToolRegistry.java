package com.datamap.mcp;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Holds all {@link McpTool} Spring beans, collected by constructor injection.
 * Provides lookup-by-name for tools/call dispatch and the ordered list for tools/list.
 */
@Component
public class McpToolRegistry {

    private final List<McpTool> tools;

    public McpToolRegistry(List<McpTool> tools) {
        this.tools = tools;
    }

    public Optional<McpTool> findByName(String name) {
        return tools.stream().filter(t -> t.name().equals(name)).findFirst();
    }

    public List<McpTool> list() {
        return tools;
    }
}
