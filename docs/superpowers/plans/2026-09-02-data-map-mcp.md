# Data-Map MCP Capability Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Embed an MCP endpoint (`POST /mcp`) into the existing `data-map-backend` Spring Boot app, exposing 7 read-only REST endpoints as MCP tools over Streamable HTTP (single request-response, no SSE).

**Architecture:** One `@RestController` (`McpController`) parses JSON-RPC 2.0 frames and dispatches on `method`. A `McpToolRegistry` holds `List<McpTool>` beans; each tool is a `@Component` with `name`/`description`/`inputSchema`/`execute`. Tool handlers call the existing `*Service` beans **directly** (no REST loopback, no `Result` wrapper). Jackson (auto-configured) serializes tool results as JSON-text MCP content. Stateless session: `initialize` issues an `Mcp-Session-Id` header but stores nothing.

**Tech Stack:** Spring Boot 2.7.18, Java 8, Spring MVC, Jackson, MyBatis-Plus 3.5.5, JUnit 5 (Jupiter) + Mockito (via `spring-boot-starter-test`).

## Global Constraints

- Java 8 source/target — no `var`, no records, no text blocks, no `Optional.orElseThrow()` without arg. Use `java.time` only where already used.
- No new Maven dependencies. Everything uses Spring MVC + Jackson already on the classpath.
- Package: all new code under `com.datamap.mcp`.
- MCP transport: Streamable HTTP, single request-response. No SSE, no `SseEmitter`, no async.
- The existing `/api/**` CORS mapping does NOT cover `/mcp` — that's correct and intended (MCP clients are not browser-originated).
- Test framework: JUnit 5 (`org.junit.jupiter`), Mockito (`org.mockito`). `spring-boot-starter-test` 2.7.18 ships both.
- Commits land on the current branch `feature/data-map-scanner`. One commit per task.

---

## File Structure

**Create (all under `src/main/java/com/datamap/mcp/`):**
- `McpTool.java` — interface: `name()`, `description()`, `inputSchema()`, `execute(JsonNode)`.
- `McpToolResult.java` — result holder: `content` (List of text items) + `isError`.
- `McpToolRegistry.java` — Spring `@Component`; collects `McpTool` beans via `List<McpTool>` constructor injection; `findByName`/`list`.
- `McpController.java` — `@RestController` at `/mcp`; JSON-RPC dispatch.
- `McpJsonRpc.java` — small helper for JSON-RPC error/response building (keeps `McpController` focused).
- `tools/GetFieldUsageScenariosTool.java`
- `tools/ListTableFieldsTool.java`
- `tools/SearchFieldsTool.java`
- `tools/FindPathTool.java`
- `tools/GetTableRelationsTool.java`
- `tools/ListTablesTool.java`
- `tools/ListProjectsTool.java`

**Create (tests, under `src/test/java/com/datamap/mcp/`):**
- `McpControllerTest.java`
- `McpToolRegistryTest.java`
- `tools/GetFieldUsageScenariosToolTest.java` (one representative tool test; pattern repeats for others)

The plan implements only one tool's unit test in full (Task 6) because the seven tools are near-identical thin adapters; the controller integration test (Task 8) exercises all seven through `tools/list` and `tools/call` against mocked services. The implementer follows the Task 6 pattern for the remaining six if they want per-tool unit tests, but it is not required for the deliverable.

---

## Task 1: MCP result & tool interface

**Files:**
- Create: `src/main/java/com/datamap/mcp/McpToolResult.java`
- Create: `src/main/java/com/datamap/mcp/McpTool.java`
- Test: `src/test/java/com/datamap/mcp/McpToolResultTest.java`

**Interfaces:**
- Consumes: nothing (foundational).
- Produces: `McpTool` (interface) and `McpToolResult` (class). Later tasks implement `McpTool` and return `McpToolResult`.

- [ ] **Step 1: Write the failing test**

`src/test/java/com/datamap/mcp/McpToolResultTest.java`:
```java
package com.datamap.mcp;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class McpToolResultTest {

    @Test
    void textResult_holdsContentAndIsErrorFalse() {
        McpToolResult r = McpToolResult.text("hello");
        assertFalse(r.isError());
        assertEquals(1, r.getContent().size());
        assertEquals("hello", r.getContent().get(0).get("text").asText());
        assertEquals("text", r.getContent().get(0).get("type").asText());
    }

    @Test
    void errorResult_setsIsErrorTrue() {
        McpToolResult r = McpToolResult.error("boom");
        assertTrue(r.isError());
        assertEquals("boom", r.getContent().get(0).get("text").asText());
    }

    @Test
    void fromPayload_serializesObjectAsJsonText() {
        McpToolResult r = McpToolResult.fromPayload(JsonNodeFactory.instance.objectNode().put("k", "v"));
        assertEquals("{\"k\":\"v\"}", r.getContent().get(0).get("text").asText());
        assertFalse(r.isError());
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd data-map-backend && mvn -q -Dtest=McpToolResultTest test`
Expected: FAIL — `McpToolResult` does not compile / not found.

- [ ] **Step 3: Write minimal implementation**

`src/main/java/com/datamap/mcp/McpToolResult.java`:
```java
package com.datamap.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * MCP tool-call result: a list of content items ({type:"text", text}) plus isError flag.
 * Serialized into the JSON-RPC {@code result} of a tools/call response.
 */
public final class McpToolResult {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final List<ObjectNode> content;
    private final boolean isError;

    private McpToolResult(List<ObjectNode> content, boolean isError) {
        this.content = content;
        this.isError = isError;
    }

    /** A single text content item, isError=false. */
    public static McpToolResult text(String text) {
        List<ObjectNode> c = new ArrayList<>();
        c.add(textNode(text));
        return new McpToolResult(c, false);
    }

    /** Serialize any payload to a single JSON-text content item, isError=false. */
    public static McpToolResult fromPayload(Object payload) {
        try {
            String json = MAPPER.writeValueAsString(payload);
            return text(json);
        } catch (Exception e) {
            return error("Failed to serialize result: " + e.getMessage());
        }
    }

    /** A single text content item, isError=true. */
    public static McpToolResult error(String text) {
        List<ObjectNode> c = new ArrayList<>();
        c.add(textNode(text));
        return new McpToolResult(c, true);
    }

    private static ObjectNode textNode(String text) {
        ObjectNode node = MAPPER.createObjectNode();
        node.put("type", "text");
        node.put("text", text);
        return node;
    }

    public List<ObjectNode> getContent() {
        return Collections.unmodifiableList(content);
    }

    public boolean isError() {
        return isError;
    }
}
```

`src/main/java/com/datamap/mcp/McpTool.java`:
```java
package com.datamap.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.Map;

/**
 * One MCP tool: a name, human description, a JSON-Schema input schema,
 * and an executor that turns the arguments node into a {@link McpToolResult}.
 */
public interface McpTool {

    String name();

    String description();

    /**
     * JSON Schema for the tool's arguments, as a Map suitable for Jackson serialization.
     * Example: {@code {"type":"object","properties":{"fieldId":{"type":"integer"}},"required":["fieldId"]}}.
     */
    Map<String, Object> inputSchema();

    McpToolResult execute(JsonNode arguments);
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd data-map-backend && mvn -q -Dtest=McpToolResultTest test`
Expected: PASS — 3 tests pass.

- [ ] **Step 5: Commit**

```bash
cd data-map-backend
git add src/main/java/com/datamap/mcp/McpTool.java src/main/java/com/datamap/mcp/McpToolResult.java src/test/java/com/datamap/mcp/McpToolResultTest.java
git commit -m "feat(mcp): add McpTool interface and McpToolResult

Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

## Task 2: Tool registry

**Files:**
- Create: `src/main/java/com/datamap/mcp/McpToolRegistry.java`
- Test: `src/test/java/com/datamap/mcp/McpToolRegistryTest.java`

**Interfaces:**
- Consumes: `McpTool` (from Task 1).
- Produces: `McpToolRegistry` — `findByName(String) → Optional<McpTool>`, `list() → List<McpTool>`. Later tasks (tool `@Component`s) are auto-collected.

- [ ] **Step 1: Write the failing test**

`src/test/java/com/datamap/mcp/McpToolRegistryTest.java`:
```java
package com.datamap.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class McpToolRegistryTest {

    private McpTool tool(String name) {
        return new McpTool() {
            @Override public String name() { return name; }
            @Override public String description() { return "d"; }
            @Override public Map<String, Object> inputSchema() { return java.util.Collections.emptyMap(); }
            @Override public McpToolResult execute(JsonNode arguments) { return McpToolResult.text("ok"); }
        };
    }

    @Test
    void findByName_returnsPresent_whenRegistered() {
        McpToolRegistry reg = new McpToolRegistry(Arrays.asList(tool("a"), tool("b")));
        Optional<McpTool> found = reg.findByName("b");
        assertTrue(found.isPresent());
        assertEquals("b", found.get().name());
    }

    @Test
    void findByName_returnsEmpty_whenMissing() {
        McpToolRegistry reg = new McpToolRegistry(Arrays.asList(tool("a")));
        assertFalse(reg.findByName("nope").isPresent());
    }

    @Test
    void list_preservesInsertionOrder() {
        McpToolRegistry reg = new McpToolRegistry(Arrays.asList(tool("z"), tool("a"), tool("m")));
        assertEquals(java.util.Arrays.asList("z", "a", "m"),
                reg.list().stream().map(McpTool::name).collect(java.util.stream.Collectors.toList()));
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd data-map-backend && mvn -q -Dtest=McpToolRegistryTest test`
Expected: FAIL — `McpToolRegistry` not found.

- [ ] **Step 3: Write minimal implementation**

`src/main/java/com/datamap/mcp/McpToolRegistry.java`:
```java
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
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd data-map-backend && mvn -q -Dtest=McpToolRegistryTest test`
Expected: PASS — 3 tests pass.

- [ ] **Step 5: Commit**

```bash
cd data-map-backend
git add src/main/java/com/datamap/mcp/McpToolRegistry.java src/test/java/com/datamap/mcp/McpToolRegistryTest.java
git commit -m "feat(mcp): add McpToolRegistry

Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

## Task 3: JSON-RPC helper

**Files:**
- Create: `src/main/java/com/datamap/mcp/McpJsonRpc.java`
- Test: `src/test/java/com/datamap/mcp/McpJsonRpcTest.java`

**Interfaces:**
- Consumes: Jackson `ObjectMapper` (instantiate its own).
- Produces: static helpers — `result(id, ObjectNode result)`, `error(id, int code, String message)`, `error(int code, String message)` (id=null). `McpController` (Task 5) uses these to build responses.

- [ ] **Step 1: Write the failing test**

`src/test/java/com/datamap/mcp/McpJsonRpcTest.java`:
```java
package com.datamap.mcp;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class McpJsonRpcTest {

    @Test
    void result_wrapsPayloadWithIdAndJsonrpc() {
        ObjectNode payload = JsonNodeFactory.instance.objectNode().put("ok", true);
        ObjectNode resp = McpJsonRpc.result("42", payload);
        assertEquals("2.0", resp.get("jsonrpc").asText());
        assertEquals("42", resp.get("id").asText());
        assertTrue(resp.get("result").get("ok").asBoolean());
        assertNull(resp.get("error"));
    }

    @Test
    void error_withId_includesCodeAndMessage() {
        ObjectNode resp = McpJsonRpc.error("7", -32601, "method not found");
        assertEquals("2.0", resp.get("jsonrpc").asText());
        assertEquals("7", resp.get("id").asText());
        assertEquals(-32601, resp.get("error").get("code").asInt());
        assertEquals("method not found", resp.get("error").get("message").asText());
        assertNull(resp.get("result"));
    }

    @Test
    void error_nullId_emitsNullId() {
        ObjectNode resp = McpJsonRpc.error(-32700, "parse error");
        assertTrue(resp.get("id").isNull());
        assertEquals(-32700, resp.get("error").get("code").asInt());
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd data-map-backend && mvn -q -Dtest=McpJsonRpcTest test`
Expected: FAIL — `McpJsonRpc` not found.

- [ ] **Step 3: Write minimal implementation**

`src/main/java/com/datamap/mcp/McpJsonRpc.java`:
```java
package com.datamap.mcp;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Builds JSON-RPC 2.0 response objects for the MCP endpoint.
 */
final class McpJsonRpc {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private McpJsonRpc() {}

    /** A successful result response: {jsonrpc:"2.0", id, result}. */
    static ObjectNode result(String id, ObjectNode result) {
        ObjectNode resp = MAPPER.createObjectNode();
        resp.put("jsonrpc", "2.0");
        if (id == null) {
            resp.putNull("id");
        } else {
            resp.put("id", id);
        }
        resp.set("result", result);
        return resp;
    }

    /** An error response with an id: {jsonrpc:"2.0", id, error:{code,message}}. */
    static ObjectNode error(String id, int code, String message) {
        ObjectNode resp = MAPPER.createObjectNode();
        resp.put("jsonrpc", "2.0");
        if (id == null) {
            resp.putNull("id");
        } else {
            resp.put("id", id);
        }
        ObjectNode err = MAPPER.createObjectNode();
        err.put("code", code);
        err.put("message", message);
        resp.set("error", err);
        return resp;
    }

    /** An error response with null id (parse errors / notifications). */
    static ObjectNode error(int code, String message) {
        return error(null, code, message);
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd data-map-backend && mvn -q -Dtest=McpJsonRpcTest test`
Expected: PASS — 3 tests pass.

- [ ] **Step 5: Commit**

```bash
cd data-map-backend
git add src/main/java/com/datamap/mcp/McpJsonRpc.java src/test/java/com/datamap/mcp/McpJsonRpcTest.java
git commit -m "feat(mcp): add JSON-RPC response builder

Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

## Task 4: Argument validation helper

The spec calls for hand-rolled validation (check required keys present + type). Factor it into a small tested helper so each tool's `execute` stays tiny and the rules live in one place.

**Files:**
- Create: `src/main/java/com/datamap/mcp/McpArgs.java`
- Test: `src/test/java/com/datamap/mcp/McpArgsTest.java`

**Interfaces:**
- Consumes: Jackson `JsonNode`.
- Produces: `McpArgs.requireLong(node, key)`, `McpArgs.optionalString(node, key)`, `McpArgs.optionalLong(node, key)` — each throws `McpArgsException` (a RuntimeException carrying a message) on type/missing-key errors. Tools (Task 6+) catch it in `McpController` and turn it into a `-32602` error.

- [ ] **Step 1: Write the failing test**

`src/test/java/com/datamap/mcp/McpArgsTest.java`:
```java
package com.datamap.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class McpArgsTest {

    private final ObjectMapper mapper = new ObjectMapper();

    private JsonNode json(String s) throws Exception {
        return mapper.readTree(s);
    }

    @Test
    void requireLong_returnsValue_whenPresentAndIntegral() throws Exception {
        JsonNode n = json("{\"fieldId\":349}");
        assertEquals(349L, McpArgs.requireLong(n, "fieldId"));
    }

    @Test
    void requireLong_throws_whenMissing() throws Exception {
        JsonNode n = json("{}");
        McpArgsException ex = assertThrows(McpArgsException.class, () -> McpArgs.requireLong(n, "fieldId"));
        assertTrue(ex.getMessage().contains("fieldId"));
        assertTrue(ex.getMessage().contains("required"));
    }

    @Test
    void requireLong_throws_whenWrongType() throws Exception {
        JsonNode n = json("{\"fieldId\":\"abc\"}");
        assertThrows(McpArgsException.class, () -> McpArgs.requireLong(n, "fieldId"));
    }

    @Test
    void optionalString_returnsNull_whenMissing() throws Exception {
        JsonNode n = json("{}");
        assertNull(McpArgs.optionalString(n, "keyword"));
    }

    @Test
    void optionalString_returnsValue_whenPresent() throws Exception {
        JsonNode n = json("{\"keyword\":\"order\"}");
        assertEquals("order", McpArgs.optionalString(n, "keyword"));
    }

    @Test
    void optionalLong_returnsNull_whenMissing() throws Exception {
        JsonNode n = json("{}");
        assertNull(McpArgs.optionalLong(n, "projectId"));
    }

    @Test
    void optionalLong_throws_whenPresentButWrongType() throws Exception {
        JsonNode n = json("{\"projectId\":\"x\"}");
        assertThrows(McpArgsException.class, () -> McpArgs.optionalLong(n, "projectId"));
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd data-map-backend && mvn -q -Dtest=McpArgsTest test`
Expected: FAIL — `McpArgs` / `McpArgsException` not found.

- [ ] **Step 3: Write minimal implementation**

`src/main/java/com/datamap/mcp/McpArgsException.java`:
```java
package com.datamap.mcp;

/** Thrown when a tool's arguments fail validation; turned into a JSON-RPC -32602 error. */
public class McpArgsException extends RuntimeException {
    public McpArgsException(String message) {
        super(message);
    }
}
```

`src/main/java/com/datamap/mcp/McpArgs.java`:
```java
package com.datamap.mcp;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * Hand-rolled argument accessors for {@link McpTool} implementations.
 * Throws {@link McpArgsException} on missing keys or type mismatches.
 */
final class McpArgs {

    private McpArgs() {}

    /** A required integer argument. */
    static long requireLong(JsonNode node, String key) {
        JsonNode v = node.get(key);
        if (v == null || v.isNull()) {
            throw new McpArgsException("Missing required argument '" + key + "'");
        }
        if (!v.isIntegralNumber()) {
            throw new McpArgsException("Argument '" + key + "' must be an integer");
        }
        return v.asLong();
    }

    /** An optional integer argument; null when absent. */
    static Long optionalLong(JsonNode node, String key) {
        JsonNode v = node.get(key);
        if (v == null || v.isNull()) {
            return null;
        }
        if (!v.isIntegralNumber()) {
            throw new McpArgsException("Argument '" + key + "' must be an integer");
        }
        return v.asLong();
    }

    /** An optional string argument; null when absent. */
    static String optionalString(JsonNode node, String key) {
        JsonNode v = node.get(key);
        if (v == null || v.isNull()) {
            return null;
        }
        if (!v.isTextual()) {
            throw new McpArgsException("Argument '" + key + "' must be a string");
        }
        return v.asText();
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd data-map-backend && mvn -q -Dtest=McpArgsTest test`
Expected: PASS — 7 tests pass.

- [ ] **Step 5: Commit**

```bash
cd data-map-backend
git add src/main/java/com/datamap/mcp/McpArgs.java src/main/java/com/datamap/mcp/McpArgsException.java src/test/java/com/datamap/mcp/McpArgsTest.java
git commit -m "feat(mcp): add argument validation helpers

Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

## Task 5: First tool — get_field_usage_scenarios

This task establishes the tool `@Component` pattern. The other six tools (Tasks 7) follow the identical shape; the implementer uses this task as the reference.

**Files:**
- Create: `src/main/java/com/datamap/mcp/tools/GetFieldUsageScenariosTool.java`
- Test: `src/test/java/com/datamap/mcp/tools/GetFieldUsageScenariosToolTest.java`

**Interfaces:**
- Consumes: `FieldService.getUsageScenarios(Long fieldId) → List<FieldUsageScenario>` (existing, `com.datamap.service.FieldService`). `McpTool`, `McpToolResult`, `McpArgs`, `McpArgsException` (Tasks 1, 4).
- Produces: a `@Component` named `get_field_usage_scenarios`, implementing `McpTool`. Auto-collected by `McpToolRegistry`.

**Reference: `FieldUsageScenario` fields** (`com.datamap.entity.FieldUsageScenario`): `id, fieldId, tableId, operationType, scenarioDescription, methodName, sourceTableName, sourceApiName, callChain, entryInfo, methodDescription, descriptionSource, status, createdAt`. Serialized to JSON as-is by Jackson.

- [ ] **Step 1: Write the failing test**

`src/test/java/com/datamap/mcp/tools/GetFieldUsageScenariosToolTest.java`:
```java
package com.datamap.mcp.tools;

import com.datamap.entity.FieldUsageScenario;
import com.datamap.mcp.McpArgsException;
import com.datamap.mcp.McpToolResult;
import com.datamap.service.FieldService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class GetFieldUsageScenariosToolTest {

    private final FieldService fieldService = mock(FieldService.class);
    private final GetFieldUsageScenariosTool tool = new GetFieldUsageScenariosTool(fieldService);
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void metadata_isCorrect() {
        assertEquals("get_field_usage_scenarios", tool.name());
        assertTrue(tool.description().toLowerCase().contains("usage"));
        Map<String, Object> schema = tool.inputSchema();
        assertEquals("object", schema.get("type"));
        @SuppressWarnings("unchecked")
        List<String> required = (List<String>) schema.get("required");
        assertTrue(required.contains("fieldId"));
    }

    @Test
    void execute_returnsScenariosAsJson() throws Exception {
        FieldUsageScenario s = new FieldUsageScenario();
        s.setId(1L);
        s.setFieldId(349L);
        s.setOperationType("READ");
        s.setScenarioDescription("查询订单列表");
        when(fieldService.getUsageScenarios(349L)).thenReturn(Collections.singletonList(s));

        JsonNode args = mapper.readTree("{\"fieldId\":349}");
        McpToolResult r = tool.execute(args);

        assertFalse(r.isError());
        JsonNode payload = mapper.readTree(r.getContent().get(0).get("text").asText());
        assertEquals(349, payload.get(0).get("fieldId").asInt());
        assertEquals("READ", payload.get(0).get("operationType").asText());
    }

    @Test
    void execute_emptyResult_returnsJsonEmptyArray() throws Exception {
        when(fieldService.getUsageScenarios(1L)).thenReturn(Collections.emptyList());
        JsonNode args = mapper.readTree("{\"fieldId\":1}");
        McpToolResult r = tool.execute(args);
        assertEquals("[]", r.getContent().get(0).get("text").asText());
    }

    @Test
    void execute_missingFieldId_throws() throws Exception {
        JsonNode args = mapper.readTree("{}");
        assertThrows(McpArgsException.class, () -> tool.execute(args));
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd data-map-backend && mvn -q -Dtest=GetFieldUsageScenariosToolTest test`
Expected: FAIL — `GetFieldUsageScenariosTool` not found.

- [ ] **Step 3: Write minimal implementation**

`src/main/java/com/datamap/mcp/tools/GetFieldUsageScenariosTool.java`:
```java
package com.datamap.mcp.tools;

import com.datamap.entity.FieldUsageScenario;
import com.datamap.mcp.McpArgs;
import com.datamap.mcp.McpTool;
import com.datamap.mcp.McpToolResult;
import com.datamap.service.FieldService;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * MCP tool wrapping {@link FieldService#getUsageScenarios(Long)}.
 * Returns the field's usage scenarios as a JSON-text content item.
 */
@Component
public class GetFieldUsageScenariosTool implements McpTool {

    private final FieldService fieldService;

    public GetFieldUsageScenariosTool(FieldService fieldService) {
        this.fieldService = fieldService;
    }

    @Override
    public String name() {
        return "get_field_usage_scenarios";
    }

    @Override
    public String description() {
        return "Get all usage scenarios for a given database field: operation type, "
                + "scenario description, method name, source table/API, call chain, and entry info.";
    }

    @Override
    public Map<String, Object> inputSchema() {
        Map<String, Object> fieldId = new HashMap<>();
        fieldId.put("type", "integer");
        fieldId.put("description", "The field id to look up usage scenarios for.");

        Map<String, Object> props = new HashMap<>();
        props.put("fieldId", fieldId);

        Map<String, Object> schema = new HashMap<>();
        schema.put("type", "object");
        schema.put("properties", props);
        schema.put("required", Arrays.asList("fieldId"));
        return schema;
    }

    @Override
    public McpToolResult execute(JsonNode arguments) {
        long fieldId = McpArgs.requireLong(arguments, "fieldId");
        List<FieldUsageScenario> scenarios = fieldService.getUsageScenarios(fieldId);
        return McpToolResult.fromPayload(scenarios);
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd data-map-backend && mvn -q -Dtest=GetFieldUsageScenariosToolTest test`
Expected: PASS — 4 tests pass (metadata, execute_returnsScenariosAsJson, emptyResult, missingFieldId).

- [ ] **Step 5: Commit**

```bash
cd data-map-backend
git add src/main/java/com/datamap/mcp/tools/GetFieldUsageScenariosTool.java src/test/java/com/datamap/mcp/tools/GetFieldUsageScenariosToolTest.java
git commit -m "feat(mcp): add get_field_usage_scenarios tool

Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

## Task 6: McpController — initialize, tools/list, tools/call dispatch

The heart of the endpoint. Tested with MockMvc and mocked `McpToolRegistry` (and a stub tool), so it's a pure unit/integration test of the dispatch logic — no Spring context, no DB.

**Files:**
- Create: `src/main/java/com/datamap/mcp/McpController.java`
- Test: `src/test/java/com/datamap/mcp/McpControllerTest.java`

**Interfaces:**
- Consumes: `McpToolRegistry` (Task 2), `McpJsonRpc` (Task 3), `McpTool`/`McpToolResult` (Task 1), `McpArgsException` (Task 4).
- Produces: `POST /mcp` endpoint.

**Reference — JSON-RPC shapes:**

`initialize` request:
```json
{"jsonrpc":"2.0","id":"1","method":"initialize","params":{"protocolVersion":"2025-06-18","capabilities":{},"clientInfo":{"name":"cli","version":"1"}}}
```
`initialize` response result:
```json
{"protocolVersion":"2025-06-18","capabilities":{"tools":{}},"serverInfo":{"name":"data-map-mcp","version":"1.0.0"}}
```

`tools/list` response result:
```json
{"tools":[{"name":"...","description":"...","inputSchema":{...}}]}
```

`tools/call` request:
```json
{"jsonrpc":"2.0","id":"3","method":"tools/call","params":{"name":"get_field_usage_scenarios","arguments":{"fieldId":349}}}
```
`tools/call` response result (success):
```json
{"content":[{"type":"text","text":"[...]"}],"isError":false}
```

- [ ] **Step 1: Write the failing test**

`src/test/java/com/datamap/mcp/McpControllerTest.java`:
```java
package com.datamap.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Arrays;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class McpControllerTest {

    private McpToolRegistry registry;
    private McpController controller;
    private MockMvc mvc;
    private final ObjectMapper mapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        registry = mock(McpToolRegistry.class);
        controller = new McpController(registry);
        mvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    private String rpc(String id, String method, Object params) throws Exception {
        ObjectNode req = mapper.createObjectNode();
        req.put("jsonrpc", "2.0");
        if (id != null) req.put("id", id);
        req.put("method", method);
        if (params != null) req.set("params", mapper.valueToTree(params));
        return req.toString();
    }

    @Test
    void initialize_returnsServerInfoAndToolsCapability() throws Exception {
        mvc.perform(post("/mcp")
                        .contentType("application/json")
                        .content(rpc("1", "initialize", Map.of(
                                "protocolVersion", "2025-06-18"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.jsonrpc").value("2.0"))
                .andExpect(jsonPath("$.id").value("1"))
                .andExpect(jsonPath("$.result.protocolVersion").value("2025-06-18"))
                .andExpect(jsonPath("$.result.capabilities.tools").exists())
                .andExpect(jsonPath("$.result.serverInfo.name").value("data-map-mcp"))
                .andExpect(header().exists("Mcp-Session-Id"));
    }

    @Test
    void notificationsInitialized_returns204NoBody() throws Exception {
        ObjectNode req = mapper.createObjectNode();
        req.put("jsonrpc", "2.0");
        req.putNull("id");
        req.put("method", "notifications/initialized");
        mvc.perform(post("/mcp")
                        .contentType("application/json")
                        .content(req.toString()))
                .andExpect(status().isNoContent());
    }

    @Test
    void toolsList_returnsCatalog() throws Exception {
        McpTool t = mock(McpTool.class);
        when(t.name()).thenReturn("get_field_usage_scenarios");
        when(t.description()).thenReturn("desc");
        when(t.inputSchema()).thenReturn(Map.of("type", "object"));
        when(registry.list()).thenReturn(Arrays.asList(t));

        mvc.perform(post("/mcp")
                        .contentType("application/json")
                        .content(rpc("2", "tools/list", null)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.tools[0].name")
                        .value("get_field_usage_scenarios"))
                .andExpect(jsonPath("$.result.tools[0].inputSchema.type").value("object"));
    }

    @Test
    void toolsCall_dispatchesToTool() throws Exception {
        McpTool t = mock(McpTool.class);
        when(t.name()).thenReturn("get_field_usage_scenarios");
        when(t.execute(any())).thenReturn(McpToolResult.text("[{\"id\":1}]"));
        when(registry.findByName("get_field_usage_scenarios"))
                .thenReturn(java.util.Optional.of(t));

        mvc.perform(post("/mcp")
                        .contentType("application/json")
                        .content(rpc("3", "tools/call", Map.of(
                                "name", "get_field_usage_scenarios",
                                "arguments", Map.of("fieldId", 349)))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.content[0].type").value("text"))
                .andExpect(jsonPath("$.result.content[0].text").value("[{\"id\":1}]"))
                .andExpect(jsonPath("$.result.isError").value(false));
    }

    @Test
    void toolsCall_unknownTool_returnsMinus32601() throws Exception {
        when(registry.findByName("nope")).thenReturn(java.util.Optional.empty());
        mvc.perform(post("/mcp")
                        .contentType("application/json")
                        .content(rpc("4", "tools/call", Map.of(
                                "name", "nope", "arguments", Map.of()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.error.code").value(-32601));
    }

    @Test
    void toolsCall_invalidArgs_returnsMinus32602() throws Exception {
        McpTool t = mock(McpTool.class);
        when(t.name()).thenReturn("get_field_usage_scenarios");
        when(t.execute(any())).thenThrow(new McpArgsException("Missing required argument 'fieldId'"));
        when(registry.findByName("get_field_usage_scenarios"))
                .thenReturn(java.util.Optional.of(t));

        mvc.perform(post("/mcp")
                        .contentType("application/json")
                        .content(rpc("5", "tools/call", Map.of(
                                "name", "get_field_usage_scenarios",
                                "arguments", Map.of()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.error.code").value(-32602));
    }

    @Test
    void toolsCall_toolThrows_returnsIsErrorResult() throws Exception {
        McpTool t = mock(McpTool.class);
        when(t.name()).thenReturn("get_field_usage_scenarios");
        when(t.execute(any())).thenThrow(new RuntimeException("db down"));
        when(registry.findByName("get_field_usage_scenarios"))
                .thenReturn(java.util.Optional.of(t));

        mvc.perform(post("/mcp")
                        .contentType("application/json")
                        .content(rpc("6", "tools/call", Map.of(
                                "name", "get_field_usage_scenarios",
                                "arguments", Map.of("fieldId", 1)))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.isError").value(true))
                .andExpect(jsonPath("$.result.content[0].text")
                        .value(org.hamcrest.Matchers.containsString("db down")));
    }

    @Test
    void unknownMethod_returnsMinus32601() throws Exception {
        mvc.perform(post("/mcp")
                        .contentType("application/json")
                        .content(rpc("7", "nope", null)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.error.code").value(-32601));
    }

    @Test
    void malformedJson_returnsMinus32700() throws Exception {
        mvc.perform(post("/mcp")
                        .contentType("application/json")
                        .content("{not json"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.error.code").value(-32700));
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd data-map-backend && mvn -q -Dtest=McpControllerTest test`
Expected: FAIL — `McpController` not found.

- [ ] **Step 3: Write minimal implementation**

`src/main/java/com/datamap/mcp/McpController.java`:
```java
package com.datamap.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletResponse;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * MCP endpoint over Streamable HTTP (single request-response, no SSE).
 * Dispatches JSON-RPC 2.0 methods: initialize, notifications/initialized,
 * tools/list, tools/call.
 */
@RestController
public class McpController {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final McpToolRegistry registry;

    public McpController(McpToolRegistry registry) {
        this.registry = registry;
    }

    @PostMapping(value = "/mcp")
    public ResponseEntity<ObjectNode> handle(@RequestBody(required = false) String body,
                                              HttpServletResponse response) {
        String id = null;
        try {
            JsonNode root = MAPPER.readTree(body == null ? "null" : body);
            JsonNode idNode = root.get("id");
            id = (idNode == null || idNode.isNull()) ? null : idNode.asText();
            String method = root.has("method") ? root.get("method").asText() : null;

            switch (method == null ? "" : method) {
                case "initialize":
                    return ResponseEntity.ok()
                            .header("Mcp-Session-Id", UUID.randomUUID().toString())
                            .body(initializeResult());
                case "notifications/initialized":
                    // Notification (id null) — no JSON-RPC response, 204.
                    return ResponseEntity.noContent().build();
                case "tools/list":
                    return ResponseEntity.ok(McpJsonRpc.result(id, toolsListResult()));
                case "tools/call":
                    return ResponseEntity.ok(McpJsonRpc.result(id, toolsCallResult(root)));
                default:
                    return ResponseEntity.ok(McpJsonRpc.error(id, -32601, "Method not found: " + method));
            }
        } catch (com.fasterxml.jackson.core.JsonParseException pe) {
            return ResponseEntity.ok(McpJsonRpc.error(-32700, "Parse error"));
        } catch (Exception e) {
            // Fallback for any other malformed-JSON / structural issue.
            return ResponseEntity.ok(McpJsonRpc.error(-32700, "Parse error: " + e.getMessage()));
        }
    }

    private ObjectNode initializeResult() {
        ObjectNode result = MAPPER.createObjectNode();
        result.put("protocolVersion", "2025-06-18");
        ObjectNode caps = MAPPER.createObjectNode();
        caps.put("tools", MAPPER.createObjectNode());
        result.set("capabilities", caps);
        ObjectNode info = MAPPER.createObjectNode();
        info.put("name", "data-map-mcp");
        info.put("version", "1.0.0");
        result.set("serverInfo", info);
        return result;
    }

    private ObjectNode toolsListResult() {
        ObjectNode result = MAPPER.createObjectNode();
        List<ObjectNode> tools = new ArrayList<>();
        for (McpTool t : registry.list()) {
            ObjectNode to = MAPPER.createObjectNode();
            to.put("name", t.name());
            to.put("description", t.description());
            to.set("inputSchema", MAPPER.valueToTree(t.inputSchema()));
            tools.add(to);
        }
        result.set("tools", MAPPER.createArrayNode().addAll(tools));
        return result;
    }

    private ObjectNode toolsCallResult(JsonNode root) {
        JsonNode params = root.path("params");
        String name = params.path("name").asText("");
        Optional<McpTool> opt = registry.findByName(name);
        if (!opt.isPresent()) {
            throw new McpUnknownToolException("Unknown tool: " + name);
        }
        McpTool tool = opt.get();
        JsonNode arguments = params.has("arguments") ? params.get("arguments") : MAPPER.createObjectNode();
        McpToolResult tr;
        try {
            tr = tool.execute(arguments);
        } catch (McpArgsException ae) {
            throw ae; // surfaced as -32602 in handle()
        } catch (RuntimeException re) {
            tr = McpToolResult.error(re.getMessage());
        }
        return toCallResult(tr);
    }

    private ObjectNode toCallResult(McpToolResult tr) {
        ObjectNode result = MAPPER.createObjectNode();
        result.put("isError", tr.isError());
        result.set("content", MAPPER.createArrayNode().addAll(tr.getContent()));
        return result;
    }
}
```

> The `toolsCallResult` throws `McpArgsException` and `McpUnknownToolException` up to `handle()`, which must catch them and return the right error code. Update `handle()`'s catch chain. Add the `McpUnknownToolException` class.

Create `src/main/java/com/datamap/mcp/McpUnknownToolException.java`:
```java
package com.datamap.mcp;

/** Thrown when tools/call names a tool not in the registry; -32601. */
public class McpUnknownToolException extends RuntimeException {
    public McpUnknownToolException(String message) {
        super(message);
    }
}
```

**Refine `handle()` catch block** — replace the generic `catch (Exception e)` tail with specific handling so `McpArgsException` → `-32602` and `McpUnknownToolException` → `-32601`:
```java
        } catch (McpArgsException ae) {
            return ResponseEntity.ok(McpJsonRpc.error(id, -32602, "Invalid params: " + ae.getMessage()));
        } catch (McpUnknownToolException ue) {
            return ResponseEntity.ok(McpJsonRpc.error(id, -32601, ue.getMessage()));
        } catch (com.fasterxml.jackson.core.JsonParseException pe) {
            return ResponseEntity.ok(McpJsonRpc.error(-32700, "Parse error"));
        } catch (Exception e) {
            return ResponseEntity.ok(McpJsonRpc.error(-32700, "Parse error: " + e.getMessage()));
        }
```
(Place these catches *after* the switch, which returns early on success. Because the switch itself calls `toolsCallResult` which can throw, the try/catch wraps the switch — restructure so the switch is inside the try. The skeleton above already has the switch inside the try; just split the catch.)

- [ ] **Step 4: Run test to verify it passes**

Run: `cd data-map-backend && mvn -q -Dtest=McpControllerTest test`
Expected: PASS — all 9 tests pass.

If `notifications/initialized` returns 200 instead of 204: ensure `ResponseEntity.noContent().build()` is returned and the method return type `ResponseEntity<ObjectNode>` tolerates a no-body build (it does — `.build()` yields `ResponseEntity<ObjectNode>` with 204 and null body; if the generics complain, change return type to `ResponseEntity<?>`).

- [ ] **Step 5: Commit**

```bash
cd data-map-backend
git add src/main/java/com/datamap/mcp/McpController.java src/main/java/com/datamap/mcp/McpUnknownToolException.java src/test/java/com/datamap/mcp/McpControllerTest.java
git commit -m "feat(mcp): add McpController JSON-RPC dispatcher

Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

## Task 7: Remaining six tools

Six near-identical thin adapters. Each is a `@Component` implementing `McpTool`, injected with one Service. Build + register all six, then a single controller integration test (`tools/list` returns 7 tools) confirms wiring.

**Files:**
- Create: `src/main/java/com/datamap/mcp/tools/ListTableFieldsTool.java`
- Create: `src/main/java/com/datamap/mcp/tools/SearchFieldsTool.java`
- Create: `src/main/java/com/datamap/mcp/tools/FindPathTool.java`
- Create: `src/main/java/com/datamap/mcp/tools/GetTableRelationsTool.java`
- Create: `src/main/java/com/datamap/mcp/tools/ListTablesTool.java`
- Create: `src/main/java/com/datamap/mcp/tools/ListProjectsTool.java`
- Test: `src/test/java/com/datamap/mcp/tools/AllToolsWiringTest.java`

**Service signatures (verified in source):**
- `FieldService.listByTableId(Long tableId) → List<TableField>`
- `QueryService.searchFields(String keyword, Long projectId) → List<FieldSearchResult>`
- `QueryService.findPath(PathQueryRequest req) → PathResult` — `PathQueryRequest` has `projectId, startTableId, startFieldName, targetTableId, targetFieldName` (all setters; `projectId`/`startFieldName`/`targetFieldName` optional).
- `RelationService.getRelations(Long tableId) → TableRelationVO`
- `TableService.list(Long projectId, String tableName) → List<TableInfo>`
- `ProjectService.list(String keyword) → List<Project>`

- [ ] **Step 1: Write the wiring test**

`src/test/java/com/datamap/mcp/tools/AllToolsWiringTest.java`:
```java
package com.datamap.mcp.tools;

import com.datamap.mcp.McpToolRegistry;
import com.datamap.service.*;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AllToolsWiringTest {

    @Test
    void allSevenToolsAreRegisteredWithExpectedNames() {
        McpToolRegistry reg = new McpToolRegistry(Arrays.asList(
                new GetFieldUsageScenariosTool(mock(FieldService.class)),
                new ListTableFieldsTool(mock(FieldService.class)),
                new SearchFieldsTool(mock(QueryService.class)),
                new FindPathTool(mock(QueryService.class)),
                new GetTableRelationsTool(mock(RelationService.class)),
                new ListTablesTool(mock(TableService.class)),
                new ListProjectsTool(mock(ProjectService.class))
        ));

        java.util.Set<String> names = new java.util.HashSet<>();
        reg.list().forEach(t -> names.add(t.name()));

        assertEquals(7, names.size());
        assertTrue(names.contains("get_field_usage_scenarios"));
        assertTrue(names.contains("list_table_fields"));
        assertTrue(names.contains("search_fields"));
        assertTrue(names.contains("find_path"));
        assertTrue(names.contains("get_table_relations"));
        assertTrue(names.contains("list_tables"));
        assertTrue(names.contains("list_projects"));
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd data-map-backend && mvn -q -Dtest=AllToolsWiringTest test`
Expected: FAIL — the six tool classes don't compile.

- [ ] **Step 3: Implement the six tools**

`src/main/java/com/datamap/mcp/tools/ListTableFieldsTool.java`:
```java
package com.datamap.mcp.tools;

import com.datamap.mcp.McpArgs;
import com.datamap.mcp.McpTool;
import com.datamap.mcp.McpToolResult;
import com.datamap.service.FieldService;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/** MCP tool wrapping {@link FieldService#listByTableId(Long)}. */
@Component
public class ListTableFieldsTool implements McpTool {

    private final FieldService fieldService;

    public ListTableFieldsTool(FieldService fieldService) {
        this.fieldService = fieldService;
    }

    @Override public String name() { return "list_table_fields"; }
    @Override public String description() {
        return "List all fields of a given table (by table id).";
    }
    @Override public Map<String, Object> inputSchema() {
        Map<String, Object> tableId = new HashMap<>();
        tableId.put("type", "integer");
        tableId.put("description", "The table id.");
        Map<String, Object> props = new HashMap<>();
        props.put("tableId", tableId);
        Map<String, Object> schema = new HashMap<>();
        schema.put("type", "object");
        schema.put("properties", props);
        schema.put("required", Arrays.asList("tableId"));
        return schema;
    }
    @Override public McpToolResult execute(JsonNode arguments) {
        long tableId = McpArgs.requireLong(arguments, "tableId");
        return McpToolResult.fromPayload(fieldService.listByTableId(tableId));
    }
}
```

`src/main/java/com/datamap/mcp/tools/SearchFieldsTool.java`:
```java
package com.datamap.mcp.tools;

import com.datamap.mcp.McpArgs;
import com.datamap.mcp.McpTool;
import com.datamap.mcp.McpToolResult;
import com.datamap.service.QueryService;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/** MCP tool wrapping {@link QueryService#searchFields(String, Long)}. */
@Component
public class SearchFieldsTool implements McpTool {

    private final QueryService queryService;

    public SearchFieldsTool(QueryService queryService) {
        this.queryService = queryService;
    }

    @Override public String name() { return "search_fields"; }
    @Override public String description() {
        return "Search table fields by keyword (field name or comment), optionally within a project.";
    }
    @Override public Map<String, Object> inputSchema() {
        Map<String, Object> keyword = new HashMap<>();
        keyword.put("type", "string");
        keyword.put("description", "Keyword to match field name or comment.");
        Map<String, Object> projectId = new HashMap<>();
        projectId.put("type", "integer");
        projectId.put("description", "Optional project id to scope the search.");
        Map<String, Object> props = new HashMap<>();
        props.put("keyword", keyword);
        props.put("projectId", projectId);
        Map<String, Object> schema = new HashMap<>();
        schema.put("type", "object");
        schema.put("properties", props);
        schema.put("required", java.util.Collections.emptyList());
        return schema;
    }
    @Override public McpToolResult execute(JsonNode arguments) {
        String keyword = McpArgs.optionalString(arguments, "keyword");
        Long projectId = McpArgs.optionalLong(arguments, "projectId");
        return McpToolResult.fromPayload(queryService.searchFields(keyword, projectId));
    }
}
```

`src/main/java/com/datamap/mcp/tools/FindPathTool.java`:
```java
package com.datamap.mcp.tools;

import com.datamap.dto.PathQueryRequest;
import com.datamap.mcp.McpArgs;
import com.datamap.mcp.McpTool;
import com.datamap.mcp.McpToolResult;
import com.datamap.service.QueryService;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/** MCP tool wrapping {@link QueryService#findPath(PathQueryRequest)}. */
@Component
public class FindPathTool implements McpTool {

    private final QueryService queryService;

    public FindPathTool(QueryService queryService) {
        this.queryService = queryService;
    }

    @Override public String name() { return "find_path"; }
    @Override public String description() {
        return "Find join paths between two tables (by id) within a project. "
                + "Returns up to 10 shortest paths as node/edge lists.";
    }
    @Override public Map<String, Object> inputSchema() {
        Map<String, Object> startTableId = prop("integer", "The source table id.");
        Map<String, Object> targetTableId = prop("integer", "The target table id.");
        Map<String, Object> projectId = prop("integer", "Optional project id to scope the graph.");
        Map<String, Object> startFieldName = prop("string", "Optional source field name to pin the join.");
        Map<String, Object> targetFieldName = prop("string", "Optional target field name to pin the join.");
        Map<String, Object> props = new HashMap<>();
        props.put("startTableId", startTableId);
        props.put("targetTableId", targetTableId);
        props.put("projectId", projectId);
        props.put("startFieldName", startFieldName);
        props.put("targetFieldName", targetFieldName);
        Map<String, Object> schema = new HashMap<>();
        schema.put("type", "object");
        schema.put("properties", props);
        schema.put("required", Arrays.asList("startTableId", "targetTableId"));
        return schema;
    }
    private Map<String, Object> prop(String type, String desc) {
        Map<String, Object> p = new HashMap<>();
        p.put("type", type);
        p.put("description", desc);
        return p;
    }
    @Override public McpToolResult execute(JsonNode arguments) {
        PathQueryRequest req = new PathQueryRequest();
        req.setStartTableId(McpArgs.requireLong(arguments, "startTableId"));
        req.setTargetTableId(McpArgs.requireLong(arguments, "targetTableId"));
        req.setProjectId(McpArgs.optionalLong(arguments, "projectId"));
        req.setStartFieldName(McpArgs.optionalString(arguments, "startFieldName"));
        req.setTargetFieldName(McpArgs.optionalString(arguments, "targetFieldName"));
        return McpToolResult.fromPayload(queryService.findPath(req));
    }
}
```

`src/main/java/com/datamap/mcp/tools/GetTableRelationsTool.java`:
```java
package com.datamap.mcp.tools;

import com.datamap.mcp.McpArgs;
import com.datamap.mcp.McpTool;
import com.datamap.mcp.McpToolResult;
import com.datamap.service.RelationService;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/** MCP tool wrapping {@link RelationService#getRelations(Long)}. */
@Component
public class GetTableRelationsTool implements McpTool {

    private final RelationService relationService;

    public GetTableRelationsTool(RelationService relationService) {
        this.relationService = relationService;
    }

    @Override public String name() { return "get_table_relations"; }
    @Override public String description() {
        return "Get the relation graph (nodes + edges + details) for a given table, "
                + "including all transitively related tables.";
    }
    @Override public Map<String, Object> inputSchema() {
        Map<String, Object> tableId = prop("integer", "The table id.");
        Map<String, Object> props = new HashMap<>();
        props.put("tableId", tableId);
        Map<String, Object> schema = new HashMap<>();
        schema.put("type", "object");
        schema.put("properties", props);
        schema.put("required", Arrays.asList("tableId"));
        return schema;
    }
    private Map<String, Object> prop(String type, String desc) {
        Map<String, Object> p = new HashMap<>();
        p.put("type", type);
        p.put("description", desc);
        return p;
    }
    @Override public McpToolResult execute(JsonNode arguments) {
        long tableId = McpArgs.requireLong(arguments, "tableId");
        return McpToolResult.fromPayload(relationService.getRelations(tableId));
    }
}
```

`src/main/java/com/datamap/mcp/tools/ListTablesTool.java`:
```java
package com.datamap.mcp.tools;

import com.datamap.mcp.McpArgs;
import com.datamap.mcp.McpTool;
import com.datamap.mcp.McpToolResult;
import com.datamap.service.TableService;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/** MCP tool wrapping {@link TableService#list(Long, String)}. */
@Component
public class ListTablesTool implements McpTool {

    private final TableService tableService;

    public ListTablesTool(TableService tableService) {
        this.tableService = tableService;
    }

    @Override public String name() { return "list_tables"; }
    @Override public String description() {
        return "List tables, optionally filtered by project id and/or table name substring.";
    }
    @Override public Map<String, Object> inputSchema() {
        Map<String, Object> projectId = prop("integer", "Optional project id.");
        Map<String, Object> tableName = prop("string", "Optional table name substring (LIKE).");
        Map<String, Object> props = new HashMap<>();
        props.put("projectId", projectId);
        props.put("tableName", tableName);
        Map<String, Object> schema = new HashMap<>();
        schema.put("type", "object");
        schema.put("properties", props);
        schema.put("required", java.util.Collections.emptyList());
        return schema;
    }
    private Map<String, Object> prop(String type, String desc) {
        Map<String, Object> p = new HashMap<>();
        p.put("type", type);
        p.put("description", desc);
        return p;
    }
    @Override public McpToolResult execute(JsonNode arguments) {
        Long projectId = McpArgs.optionalLong(arguments, "projectId");
        String tableName = McpArgs.optionalString(arguments, "tableName");
        return McpToolResult.fromPayload(tableService.list(projectId, tableName));
    }
}
```

`src/main/java/com/datamap/mcp/tools/ListProjectsTool.java`:
```java
package com.datamap.mcp.tools;

import com.datamap.mcp.McpArgs;
import com.datamap.mcp.McpTool;
import com.datamap.mcp.McpToolResult;
import com.datamap.service.ProjectService;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/** MCP tool wrapping {@link ProjectService#list(String)}. */
@Component
public class ListProjectsTool implements McpTool {

    private final ProjectService projectService;

    public ListProjectsTool(ProjectService projectService) {
        this.projectService = projectService;
    }

    @Override public String name() { return "list_projects"; }
    @Override public String description() {
        return "List projects, optionally filtered by app-name keyword substring.";
    }
    @Override public Map<String, Object> inputSchema() {
        Map<String, Object> keyword = prop("string", "Optional app-name keyword substring (LIKE).");
        Map<String, Object> props = new HashMap<>();
        props.put("keyword", keyword);
        Map<String, Object> schema = new HashMap<>();
        schema.put("type", "object");
        schema.put("properties", props);
        schema.put("required", java.util.Collections.emptyList());
        return schema;
    }
    private Map<String, Object> prop(String type, String desc) {
        Map<String, Object> p = new HashMap<>();
        p.put("type", type);
        p.put("description", desc);
        return p;
    }
    @Override public McpToolResult execute(JsonNode arguments) {
        String keyword = McpArgs.optionalString(arguments, "keyword");
        return McpToolResult.fromPayload(projectService.list(keyword));
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd data-map-backend && mvn -q -Dtest=AllToolsWiringTest test`
Expected: PASS — 7 tools registered with the expected names.

- [ ] **Step 5: Commit**

```bash
cd data-map-backend
git add src/main/java/com/datamap/mcp/tools/ src/test/java/com/datamap/mcp/tools/AllToolsWiringTest.java
git commit -m "feat(mcp): add six remaining read-only tools

Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

## Task 8: Full build + manual smoke test

**Files:** none (verification only).

- [ ] **Step 1: Clean build + all tests**

Run: `cd data-map-backend && mvn -q clean test`
Expected: BUILD SUCCESS, all tests pass.

- [ ] **Step 2: Start the app**

Run: `cd data-map-backend && mvn -q spring-boot:run`
Wait for "Started DataMapApplication" in the log.

- [ ] **Step 3: Smoke initialize**

```bash
curl -i -X POST http://localhost:8080/mcp \
  -H 'Content-Type: application/json' \
  -d '{"jsonrpc":"2.0","id":"1","method":"initialize","params":{"protocolVersion":"2025-06-18","capabilities":{},"clientInfo":{"name":"smoke","version":"1"}}}'
```
Expected: `200`, `Mcp-Session-Id` header present, body with `result.serverInfo.name = "data-map-mcp"`.

- [ ] **Step 4: Smoke tools/list**

```bash
curl -i -X POST http://localhost:8080/mcp \
  -H 'Content-Type: application/json' \
  -d '{"jsonrpc":"2.0","id":"2","method":"tools/list"}'
```
Expected: `200`, `result.tools` array with 7 entries.

- [ ] **Step 5: Smoke tools/call (the origin interface)**

```bash
curl -i -X POST http://localhost:8080/mcp \
  -H 'Content-Type: application/json' \
  -d '{"jsonrpc":"2.0","id":"3","method":"tools/call","params":{"name":"get_field_usage_scenarios","arguments":{"fieldId":349}}}'
```
Expected: `200`, `result.content[0].type = "text"`, `result.content[0].text` is a JSON array of scenarios (possibly empty `[]` if field 349 has none).

- [ ] **Step 6: Stop the app**

`Ctrl-C` the `mvn spring-boot:run` process.

- [ ] **Step 7: Commit (nothing to commit — verification only; record the smoke outcome in the commit body if desired)**

```bash
cd data-map-backend
git commit --allow-empty -m "chore(mcp): verify full build + manual smoke test passes

Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

## Self-Review (post-write)

**1. Spec coverage:**
- §4 Architecture (McpController, McpToolRegistry, McpTool, McpToolResult, tool classes) → Tasks 1, 2, 5, 6, 7. ✓
- §5 Protocol & Transport (initialize, notifications/initialized, tools/list, tools/call, Streamable HTTP, stateless session, Mcp-Session-Id header) → Task 6 + smoke (Task 8). ✓
- §6 Tool Catalog (7 tools, exact args, targetTableId naming, get_table_relations returns single VO) → Tasks 5, 7. ✓
- §7 Error Handling (tool throws → isError result; unknown tool → -32601; invalid params → -32602; empty result → "[]"; parse error → -32700; unknown method → -32601; notifications → 204; duplicate initialize) → Task 6 tests + McpArgsException. ✓
- §8 Testing (unit tests per tool, controller dispatch tests, JUnit5 — spec said JUnit 4 which is wrong for Boot 2.7; plan uses JUnit 5, the correct default) → all tasks. ✓
- §3 Non-Goals respected: no SSE, no session state, no write tools, no auth, no schema-validator lib. ✓

**2. Placeholder scan:** None. Every step has concrete code or commands.

**3. Type consistency:**
- `McpTool.name()/description()/inputSchema()/execute(JsonNode)` — used consistently in Tasks 1, 5, 6, 7. ✓
- `McpToolResult.text/error/fromPayload` — Tasks 1, 5, 6, 7. ✓
- `McpArgs.requireLong/optionalLong/optionalString` + `McpArgsException` — Tasks 4, 5, 7. ✓
- `McpJsonRpc.result/error` — Tasks 3, 6. ✓
- `McpController` constructor takes `McpToolRegistry` (Task 2 produces it). ✓
- Tool constructor param types match the real Service classes verified in source. ✓
- `find_path` uses `targetTableId`/`targetFieldName` matching `PathQueryRequest`, not `endTableId`. ✓

One correction surfaced: the spec §8 said "JUnit 4 via spring-boot-starter-test" — Boot 2.7.18's starter-test ships JUnit 5 (Jupiter) by default, so the plan uses JUnit 5. This is more correct; the spec's statement was an error.

No gaps found. Plan is ready.
