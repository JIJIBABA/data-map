# Data-Map MCP Capability — Design Spec

**Date:** 2026-09-02
**Status:** Design approved (brainstormed 2026-09-02)
**Scope:** Expose all read-only `data-map-backend` REST endpoints as MCP tools, served from an MCP endpoint embedded in the existing Spring Boot 2.7 / Java 8 backend.

---

## 1. Goal

Provide an MCP (Model Context Protocol) capability over HTTP so that an MCP client (Claude Code, Claude Desktop, or any MCP-aware agent) can query the data-map knowledge graph — fields, tables, usage scenarios, relations, join paths — as structured tool calls.

The trigger interface, `GET /api/fields/{id}/usage-scenarios`, is one of seven read-only endpoints packaged as MCP tools.

## 2. Constraints

- **Runtime:** `spring-boot-starter-parent 2.7.18`, `java.version 1.8` (`data-map-backend/pom.xml`). No Spring Boot 3 / Java 17 upgrade — rules out the official Spring AI MCP Server starter.
- **Stack:** MyBatis-Plus 3.5.5, Spring MVC, Jackson (auto-configured). No Kotlin.
- **Deployable:** Single existing `data-map-backend` jar. No separate process.
- **Existing precedent:** The team already runs HTTP-form MCP servers (ai-cr `:8092/mcp`, ai-chains `:8091/mcp`). An HTTP transport is the house style.

## 3. Non-Goals (YAGNI)

- **No SSE / server→client notifications.** All tools are synchronous request/response. The single-request-response ("no-streaming") mode of the Streamable HTTP transport suffices.
- **No real session state.** Tools are stateless. We honor the `initialize` handshake and issue a session id header, but store nothing per session. Any session id (or none) is accepted for `tools/*`. Documented as an intentional trade-off.
- **No write tools.** PUT/DELETE/POST endpoints (comment edits, scan ingestion, project CRUD) are out of scope this round.
- **No auth on the MCP endpoint.** The REST API itself has none today; parity. (Revisit when the REST API gains auth.)
- **No schema-validator library.** Hand-rolled argument validation for ~7 tools with simple types. Revisit if the catalog grows.

## 4. Architecture

A single new `@RestController` mapped at `/mcp` joins the existing controllers in `com.datamap.controller`. It accepts `POST` with a JSON-RPC 2.0 body and dispatches on the `method` field. Handlers call the existing `*Service` beans **directly** — not the REST controllers, not via HTTP loopback — bypassing the `Result` wrapper so tools return the raw payload.

```
MCP client (Claude / any client)
     │  POST /mcp  (JSON-RPC over Streamable HTTP)
     ▼
McpController  ──dispatch by method──▶  McpToolRegistry
     │                                         │
     │  initialize | tools/list | tools/call    │ holds List<McpTool>
     │                                         │ each: name, description, inputSchema, handler
     ▼
tool handler  ──direct call──▶  FieldService / QueryService / TableService / RelationService / ProjectService
     │
     ▼  McpToolResult(content[], isError)
McpController  ── JSON-RPC response ──▶  client
```

### New components (all under `com.datamap.mcp`)

| Component | Responsibility |
|---|---|
| `McpController` | `@RestController` at `/mcp`. Parses JSON-RPC frame, dispatches by method, writes JSON-RPC response. Issues `Mcp-Session-Id` header on `initialize`. |
| `McpToolRegistry` | Spring `@Component`. Collects all `McpTool` beans; provides `findByName`, `list()`. |
| `McpTool` | Interface: `String name()`, `String description()`, `Map<String,Object> inputSchema()`, `McpToolResult execute(JsonNode arguments)`. |
| `McpToolResult` | The MCP tool-result shape: `List<Content> content`, `boolean isError`. `Content` is `{type:"text", text}`. |
| Tool classes | One `@Component` per endpoint, implementing `McpTool`, injected with the relevant Service. |

## 5. Protocol & Transport — Streamable HTTP (single request-response)

`POST /mcp` handles three JSON-RPC methods. We handle only the **request** side (no server→client notifications/requests). Each response is a single JSON-RPC `result` object; no SSE stream.

| JSON-RPC `method` | Response |
|---|---|
| `initialize` | `{protocolVersion, capabilities:{tools:{}}, serverInfo:{name:"data-map-mcp", version:"1.0.0"}}`. Also emit an `Mcp-Session-Id` response header (fresh UUID). No per-session state stored. **Protocol version:** echo back the `protocolVersion` the client sends in its `initialize` params if we recognize it; otherwise fall back to our default (`2025-06-18`). The version is negotiated at handshake, not hardcoded into tool logic. (Final default string confirmed against the live spec at implementation time, since this network can't fetch modelcontextprotocol.io.) |
| `notifications/initialized` | (id is null) HTTP `204`, no body. Notifications get no JSON-RPC response. |
| `tools/list` | `{tools: [...]}` — the full catalog, no pagination. |
| `tools/call` | `{name, arguments}` → look up tool, validate args against its `inputSchema`, execute handler, return `result.content[]`. |

Transport specifics:
- Single endpoint, **no SSE**. Responses are plain `application/json`. This is the "no-streaming" mode of Streamable HTTP that Boot 2.7 Spring MVC handles cleanly.
- `McpController` reads the body and parses with the auto-configured `ObjectMapper`.
- Unknown method / malformed JSON → JSON-RPC error (`-32601` method not found, `-32700` parse error). Echo back `id`; for notifications (`id:null`) no response is sent, but `notifications/initialized` gets a `204`.

Session handling — minimal: we generate `Mcp-Session-Id` to satisfy the handshake but track nothing. Any session id (or none) works for a `tools/call`. Documented as an intentional trade-off for stateless tools.

## 6. Tool Catalog

Seven tools, one per read-only endpoint. Names are `snake_case`. Each tool's `inputSchema` is a JSON Schema object; results serialize as JSON-text content (`content:[{type:"text", text:<json-stringified-payload>}]`).

| Tool | Service method | Args | Returns |
|---|---|---|---|
| `get_field_usage_scenarios` | `FieldService.getUsageScenarios(Long)` | `fieldId: integer (required)` | `FieldUsageScenario[]` |
| `list_table_fields` | `FieldService.listByTableId(Long)` | `tableId: integer (required)` | `TableField[]` |
| `search_fields` | `QueryService.searchFields(String, Long)` | `keyword?: string`, `projectId?: integer` | `FieldSearchResult[]` |
| `find_path` | `QueryService.findPath(PathQueryRequest)` | `startTableId: integer (required)`, `targetTableId: integer (required)`, `projectId?: integer`, `startFieldName?: string`, `targetFieldName?: string` | `PathResult` |
| `get_table_relations` | `RelationService.getRelations(Long)` | `tableId: integer (required)` | `TableRelationVO` |
| `list_tables` | `TableService.list(Long, String)` | `projectId?: integer`, `tableName?: string` | `TableInfo[]` |
| `list_projects` | `ProjectService.list(String)` | `keyword?: string` | `Project[]` |

Notes:
- `find_path` arg names match `PathQueryRequest` fields: `targetTableId`/`targetFieldName` (not `endTableId`). Only the two table ids are effectively required; field names narrow a specific join.
- `get_table_relations` returns a single `TableRelationVO`, not a list.
- Handlers do NOT echo the `Result<>` wrapper as text; they return the raw payload directly (the REST controllers were already one-line delegations over the same Services).

## 7. Error Handling

| Case | Behavior |
|---|---|
| Tool throws (DB error, NPE, …) | Caught in `McpController` dispatch. Return `tools/call` **result** with `isError:true` and the message as text content. Per MCP spec, a tool failure is a tool result, not a transport error. |
| Unknown tool name | JSON-RPC error `-32601` (method not found). |
| Missing required arg / wrong type | Validate against the tool's `inputSchema`. Failure → JSON-RPC error `-32602` (invalid params) with a description. |
| Empty result (valid query, no match) | Not an error. Return `content:[{type:"text", text:"[]"}]`. |
| Malformed JSON / not JSON-RPC | JSON-RPC error `-32700` (parse error). |
| Unknown JSON-RPC method | JSON-RPC error `-32601` (method not found). Handled methods: `initialize`, `notifications/initialized`, `tools/list`, `tools/call`. |
| `notifications/initialized` (id null) | HTTP `204`, no body. |
| Duplicate `initialize` | Spec allows re-initialize; return a fresh server-info result + new session id. Stateless, so harmless. |

Argument validation strategy: lightweight and pragmatic — hand-rolled (check required keys present + type via `instanceof`). A ~30-line validator covers the 7 tools with simple types. If the catalog grows, introduce a real `json-schema-validator` library.

## 8. Testing

- **Unit tests** per tool: mock the injected Service, assert the tool maps args correctly and serializes the payload.
- **Unit test** `McpController` dispatch: `initialize` returns server info + session header; `tools/list` returns the catalog; `tools/call` happy path returns content; error paths return the right JSON-RPC error codes (`-32601`, `-32602`, `-32700`).
- `notifications/initialized` → 204.
- Follow the existing test layout under `src/test/java/com/datamap/...` (JUnit 4 via `spring-boot-starter-test`, matching `FieldService`/controller test conventions).

## 9. Open Questions

None blocking. All decisions resolved during brainstorming: scope = all read-only endpoints; transport = Streamable HTTP single request-response; implementation = hand-rolled dispatcher calling Services directly; session = stateless.
