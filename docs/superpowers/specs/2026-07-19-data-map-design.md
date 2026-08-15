# 数据地图系统 — 设计文档

**日期:** 2026-07-19
**状态:** 设计完成，待用户确认

---

## 1. 项目概述

数据地图是一个面向开发人员的数据血缘工具。核心解决两个场景：

- **场景 1 — 路径查询:** 开发中知道表 A 的 PK，需要表 D 的字段信息，系统展示从 A 到 D 的所有关联路径和中间表
- **场景 2 — 字段溯源:** 快速了解某个字段在什么业务场景写入、修改、使用，方便代码理解和问题排查

系统分三部分：**扫描 Agent** 从 Java 项目代码中提取元数据，**后端 SpringBoot** 存储和提供 API，**前端 React** 展示和交互。

---

## 2. 技术栈

| 层 | 技术 |
|---|------|
| 前端框架 | React 18 + TypeScript |
| UI 组件库 | Ant Design 5.x |
| 图可视化 | AntV G6 |
| 构建工具 | Vite |
| 后端框架 | SpringBoot 2.x + JDK 1.8 |
| 持久层 | MyBatis-Plus (数据地图自身) |
| 数据库 | MySQL 8.0 |
| 扫描 Agent | Claude Code Skill + codegraph |
| 项目构建 | Maven (后端) |

---

## 3. 被扫描项目

- **ORM:** MyBatis/MyBatis-Plus (XML Mapper) + JOOQ DSL
- **数据库类型:** MySQL / Oracle
- **项目组织:** 一个 Git 仓库 = 一个项目，项目名称来自配置文件 appname

---

## 4. 系统架构

```
┌─────────────────────────────────────────────────┐
│                   前端 (React)                    │
│   Ant Design Pro + AntV G6 关系图                │
├─────────────────────────────────────────────────┤
│              后端 (SpringBoot 2.x)                │
│  ┌──────────┐ ┌──────────┐ ┌────────────────┐   │
│  │ REST API │ │图遍历引擎│ │ 扫描数据接收层  │   │
│  └──────────┘ └──────────┘ └────────────────┘   │
├─────────────────────────────────────────────────┤
│              MySQL (数据地图自身存储)              │
├─────────────────────────────────────────────────┤
│          扫描 Agent (Claude Code Skill)           │
│  ┌──────────────┐ ┌──────────────────────┐      │
│  │ MyBatis 解析 │ │ JOOQ / Oracle 解析   │      │
│  │ (XML Mapper) │ │ (DSL 代码分析)        │      │
│  └──────────────┘ └──────────────────────┘      │
│          ↓ codegraph 辅助代码索引                 │
├─────────────────────────────────────────────────┤
│           被扫描的源项目 (MyBatis/JOOQ)            │
│           MySQL / Oracle 数据库                   │
└─────────────────────────────────────────────────┘
```

**三大模块职责:**

- **扫描 Agent** — 利用 codegraph 索引代码，解析 MyBatis XML Mapper 和 JOOQ DSL 代码，提取表结构、关联关系、字段使用场景，输出标准化 JSON 通过 API 入库。同时支持 Web 页面触发和 CLI 命令行触发。
- **后端服务** — 接收扫描数据（去重 + 增量 + 人工修改保护）、REST CRUD、BFS 图遍历路径查询
- **前端展示** — 表基本信息管理、表逻辑信息查询、关系图可视化

---

## 5. 数据库设计（MySQL）
账户：root. 密码：12345678

### 5.1 project — 项目信息

| 字段 | 类型 | 说明 |
|------|------|------|
| id | bigint PK | |
| app_name | varchar(128) | 项目 appname（来自配置文件） |
| git_repo_url | varchar(512) | 仓库地址 |
| git_local_path | varchar(512) | 本地仓库路径，扫描入口 |
| description | varchar(512) | |
| created_at | datetime | |
| updated_at | datetime | |

### 5.2 table_info — 表信息

| 字段 | 类型 | 说明 |
|------|------|------|
| id | bigint PK | |
| project_id | bigint FK | |
| table_name | varchar(128) | |
| table_comment | varchar(512) | 表含义 |
| table_comment_manual | tinyint | 是否手工修改，0=否 1=是 |
| schema_name | varchar(64) | schema（Oracle 需要） |
| db_type | varchar(16) | MYSQL / ORACLE |
| created_at | datetime | |
| updated_at | datetime | |

### 5.3 table_field — 字段定义

| 字段 | 类型 | 说明 |
|------|------|------|
| id | bigint PK | |
| table_id | bigint FK | |
| field_name | varchar(128) | |
| field_comment | varchar(512) | |
| field_type | varchar(64) | 数据库类型 |
| is_pk | tinyint | |
| is_business_field | tinyint | 是否业务字段（排除通用字段） |
| field_comment_manual | tinyint | 是否手工修改 |
| created_at | datetime | |
| updated_at | datetime | |

### 5.4 table_relation — 表关联关系

| 字段 | 类型 | 说明 |
|------|------|------|
| id | bigint PK | |
| project_id | bigint FK | |
| source_table_id | bigint FK | 左表 |
| source_field_name | varchar(128) | |
| target_table_id | bigint FK | 右表 |
| target_field_name | varchar(128) | |
| relation_type | varchar(32) | DIRECT_JOIN / INDIRECT |
| method_signature | varchar(512) | 来源方法全路径 |
| created_at | datetime | |

### 5.5 field_usage_scenario — 字段使用场景

| 字段 | 类型 | 说明 |
|------|------|------|
| id | bigint PK | |
| field_id | bigint FK | |
| table_id | bigint FK | |
| operation_type | varchar(16) | WRITE / UPDATE / DELETE |
| scenario_description | varchar(512) | 场景描述 |
| method_name | varchar(256) | 方法全路径 |
| source_table_name | varchar(128) | 写入值来源表 |
| source_api_name | varchar(256) | 写入值来源接口 |
| created_at | datetime | |

### 5.6 scan_record — 扫描记录

| 字段 | 类型 | 说明 |
|------|------|------|
| id | bigint PK | |
| project_id | bigint FK | |
| scan_type | varchar(16) | FULL / TABLE |
| table_names | text | JSON 数组 |
| status | varchar(16) | RUNNING / SUCCESS / FAILED |
| error_msg | text | |
| started_at | datetime | |
| completed_at | datetime | |

### 5.7 增量扫描保护逻辑

1. `table_comment_manual=1` → 不覆盖 table_comment
2. `field_comment_manual=1` → 不覆盖 field_comment
3. 新表/新字段直接插入
4. 旧表/旧字段更新（受上述保护控制）
5. `table_relation` 和 `field_usage_scenario` 按 scan_record 维度清理后重新写入

---

## 6. 扫描 Agent 设计

### 6.1 工作流程

| 步骤 | 操作 | 输入 | 输出 |
|------|------|------|------|
| Step 1 | 项目配置识别 | 仓库路径 | appname, db 类型, ORM 方式, 创建 scan_record |
| Step 2 | 表发现 | codegraph 索引 | 项目下所有表名列表，用户选择 |
| Step 3 | 字段提取 | 表名列表 | 字段定义（名称/类型/注释/是否PK/是否业务字段） |
| Step 4 | 关联关系发现 | 表名列表 | 直接关联（DIRECT_JOIN/INDIRECT）+ 方法签名 |
| Step 5 | 字段场景发现 | 业务字段列表 | WRITE/UPDATE/DELETE 场景 + 来源表/接口 |
| Step 6 | 数据提交 | 标准化 JSON | POST API 入库，更新 scan_record 状态 |

### 6.2 解析能力覆盖

**MyBatis/MyBatis-Plus:**
- XML Mapper 中的 `<resultMap>`, `<association>`, `<collection>` 标签
- SQL 中的 JOIN ON 条件
- 嵌套查询模式：select 语句参数 `#{}` 来自前一步查询结果 → 标记 INDIRECT
- `<insert>`, `<update>`, `<delete>` 标签中出现的字段 → 记录使用场景

**JOOQ:**
- 生成的 Table 类字段定义
- DSL 代码中 `.join().on()` 调用链 → DIRECT_JOIN
- 先 `.fetchOne()/.fetch()` 然后结果作为条件传给另一个查询 → INDIRECT
- `.set()` / `.insert()` 调用 → 记录使用场景

**通用字段排除列表:**
`pk`, `id`, `created_at`, `create_time`, `updated_at`, `update_time`, `deleted_at`, `delete_time`, `is_deleted`, `create_user`, `create_by`, `update_user`, `update_by`, `delete_user`, `delete_by`, `version`, `tenant_id`

### 6.3 标准化 JSON 格式

Agent 输�出通过 `POST /api/scan/result` 提交：

```json
{
  "project": {
    "appName": "order-system",
    "gitRepoUrl": "git@xxx/order-system.git",
    "gitLocalPath": "/path/to/repo"
  },
  "scanType": "FULL",
  "tables": [
    {
      "tableName": "tb_order",
      "tableComment": "订单表",
      "schemaName": "",
      "dbType": "MYSQL",
      "fields": [
        {
          "fieldName": "order_id",
          "fieldComment": "订单ID",
          "fieldType": "BIGINT",
          "isPk": true,
          "isBusinessField": true
        }
      ],
      "relations": [
        {
          "sourceFieldName": "user_id",
          "targetTableName": "tb_user",
          "targetFieldName": "user_id",
          "relationType": "DIRECT_JOIN",
          "methodSignature": "com.xx.dao.OrderMapper.selectOrderWithUser"
        }
      ],
      "usageScenarios": [
        {
          "fieldName": "order_status",
          "operationType": "UPDATE",
          "scenarioDescription": "用户取消订单时更新订单状态为已取消",
          "methodName": "com.xx.service.OrderService.cancelOrder",
          "sourceTableName": "",
          "sourceApiName": ""
        }
      ]
    }
  ]
}
```

### 6.4 触发方式

- **Web 触发:** 用户选择已配置的项目 → 选择表 → 后端读取 project 表中存储的仓库路径，启动异步扫描
- **CLI 触发:** 命令行直接调用 Agent，连接同一 MySQL 直接入库，无需中间导入

---

## 7. 前端页面设计

### 7.1 菜单结构

```
- 表信息管理 (一级)
  - 表基本信息管理 (二级) → 页面 Tab1
  - 表逻辑信息查询 (二级) → 页面 Tab2
```

### 7.2 表基本信息管理

**搜索区:** 两个输入框，项目名称（模糊搜索 app_name）/ 表名（模糊搜索 table_name）

**结果表格:**

| 项目名称 | 表名 | 表含义 | 操作 |
|---------|------|--------|------|
| order-system | tb_order | 订单表 | [查看] [编辑] [删除] [关联关系] |

- **查看** → 新页面 `/tables/:id`，展示字段定义列表 + 点击下钻展开使用场景子表
- **编辑** → 弹窗修改表含义注释，保存标记 `table_comment_manual=1`
- **删除** → 确认弹窗 → 删除表及级联数据
- **关联关系** → 新页面 `/tables/:id/relations`

### 7.3 表详情页 (查看)

**字段定义列表:**

| 字段名 | 注释 | 类型 | 手工修改 | 操作 |
|--------|------|------|---------|------|
| order_id | 订单ID | BIGINT | 否 | [下钻] [编辑] |

**下钻子表 (点击展开):**

| 操作类型 | 场景描述 | 方法名称 | 写入来源表 | 写入来源接口 |
|---------|---------|---------|-----------|------------|
| WRITE | 下单写入 | OrderService.createOrder | - | /api/user/info |

- **编辑** (字段行) → 弹窗仅可修改字段注释，保存标记 `field_comment_manual=1`
- **编辑** (下钻行) → 可修改场景描述

### 7.4 表关联关系图页

- **顶部:** AntV G6 画布，节点=表名，边=关联字段
- **切换按钮:** `全部` | `仅上游` | `仅下游`
  - 全部：展示与当前表连通的所有表
  - 仅上游：展示到当前表为止的祖先表
  - 仅下游：从当前表开始往下的所有子代表
- **底部表格:** 关联字段明细

| 左表 | 左字段 | 右表 | 右字段 | 关系类型 | 来源方法 |
|------|--------|------|--------|---------|---------|

### 7.5 表逻辑信息查询页

**Tab 1 — 按业务含义搜字段:**
- 输入关键词 → `table_field.field_comment` 模糊匹配
- 输出表：表名 / 字段名 / 备注

**Tab 2 — 路径查询:**
- 输入：起始表名 + 字段名 + 目标表名 + 字段名
- 输出：路径图展示所有可行路径，最短路径高亮，中间每步标注关联字段
- 多路径时列表展示供切换

---

## 8. 后端 API 设计

### 8.1 项目管理

| 方法 | URL | 说明 |
|------|-----|------|
| POST | `/api/projects` | 新增项目 |
| GET | `/api/projects` | 项目列表 |
| GET | `/api/projects?keyword=xxx` | 按 appname 模糊搜索 |
| GET | `/api/projects/{id}` | 项目详情 |
| PUT | `/api/projects/{id}` | 编辑项目 |
| DELETE | `/api/projects/{id}` | 删除项目及级联数据 |

### 8.2 表信息管理

| 方法 | URL | 说明 |
|------|-----|------|
| GET | `/api/tables?projectId=&tableName=` | 表列表（模糊搜索） |
| GET | `/api/tables/{id}` | 表详情（含字段） |
| PUT | `/api/tables/{id}` | 编辑表含义（仅 comment） |
| DELETE | `/api/tables/{id}` | 删除表及级联数据 |

### 8.3 字段管理

| 方法 | URL | 说明 |
|------|-----|------|
| GET | `/api/tables/{tableId}/fields` | 字段列表 |
| PUT | `/api/fields/{id}` | 编辑字段注释 |
| GET | `/api/fields/{id}/usage-scenarios` | 字段使用场景 |

### 8.4 表关联关系

| 方法 | URL | 说明 |
|------|-----|------|
| GET | `/api/relations/{tableId}?direction=all|upstream|downstream` | 关系图数据 |

### 8.5 表逻辑信息查询

| 方法 | URL | 说明 |
|------|-----|------|
| GET | `/api/query/search-fields?keyword=&projectId=` | 按业务含义搜字段 |
| POST | `/api/query/find-path` | 路径查询 (BFS) |

路径查询请求体：
```json
{
  "projectId": 1,
  "startTableId": 10,
  "startFieldName": "order_id",
  "targetTableId": 20,
  "targetFieldName": "product_name"
}
```

路径查询响应体：
```json
{
  "paths": [
    {
      "nodes": ["tb_order", "tb_order_item", "tb_product"],
      "edges": [
        {"from": "tb_order", "to": "tb_order_item", "joinField": "order_id"},
        {"from": "tb_order_item", "to": "tb_product", "joinField": "product_id"}
      ],
      "length": 2
    }
  ]
}
```

### 8.6 扫描管理

| 方法 | URL | 说明 |
|------|-----|------|
| POST | `/api/scan/trigger` | 触发扫描（异步） |
| POST | `/api/scan/result` | Agent 提交扫描结果 |
| GET | `/api/scan/records?projectId=` | 扫描记录列表 |

---

## 9. 图遍历算法

- `table_relation` 存储所有直接关联（有向边）
- 路径查询用 **BFS**，起始表出发，广度优先搜索目标表
- BFS 天然保证第一个命中路径为最短路径
- 最大搜索深度 = 6 层，避免无效遍历
- 返回最大深度内所有路径，按长度升序排列

---

## 10. 项目目录结构

```
/Applications/data-map/
├── data-map-backend/          # SpringBoot
│   ├── pom.xml
│   └── src/main/java/com/datamap/
│       ├── controller/
│       │   ├── ProjectController.java
│       │   ├── TableController.java
│       │   ├── FieldController.java
│       │   ├── RelationController.java
│       │   ├── QueryController.java
│       │   └── ScanController.java
│       ├── service/
│       ├── mapper/
│       ├── entity/
│       ├── dto/
│       └── common/
│
├── data-map-frontend/         # React
│   ├── package.json
│   ├── vite.config.ts
│   └── src/
│       ├── pages/
│       │   ├── TableInfoManage/
│       │   │   ├── index.tsx           # 列表页
│       │   │   └── TableDetail.tsx     # 表详情页
│       │   ├── TableLogicQuery/
│       │   │   ├── index.tsx           # 含两个 Tab
│       │   │   ├── FieldSearch.tsx     # Tab1
│       │   │   └── PathQuery.tsx       # Tab2
│       │   └── TableRelation/
│       │       └── index.tsx           # 关系图页
│       ├── components/
│       ├── services/
│       └── router/
│
└── docs/
    └── superpowers/
        └── specs/
            └── 2026-07-19-data-map-design.md
```

---

## 11. 设计决策记录

| 决策点 | 选择 | 原因 |
|--------|------|------|
| 前端框架 | React | 用户确认 |
| 源项目 ORM | MyBatis + JOOQ | 用户现有项目技术栈 |
| 源项目数据库 | MySQL + Oracle | 需兼容两种 |
| 关联关系存储 | 仅直接关联 | 结构清晰，路径查询时 BFS 计算 |
| 扫描方式 | Skill + codegraph | AI 语义理解灵活，codegraph 辅助索引 |
| 触发方式 | Web + CLI 双模式 | 灵活性和可视化兼顾 |
| 扫描覆盖 | 增量 + 人工修改保护 | 重扫不覆盖手工修正的内容 |
| 项目标识 | 一个 Git 仓库 = 一个项目 | 按仓库维度管理 |
| 表详情 | 新页面打开 | 独立 URL，信息量大时更适合 |
| 图可视化 | AntV G6 | 专为图可视化设计 |
