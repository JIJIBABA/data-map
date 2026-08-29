# 数据地图确定性扫描器 — 设计文档

**日期:** 2026-08-29
**状态:** 设计完成，待用户确认

---

## 1. 背景与目标

现有 `scan-project` skill（`~/.claude/skills/scan-project/SKILL.md`）用 grep + Claude 语义阅读提取元数据。其中调用链追踪（Step 5d「追踪写入来源」、`sourceApiName`/`sourceTableName`）靠 LLM 读代码推断，无法保证 100% 准确。

**目标：** 用确定性静态分析（javac 编译器 API）替换 LLM 扫描，做到源码级引用 100% 准确，覆盖表发现、字段提取、关联关系、字段使用场景、调用链五块。**结构性元数据（字段/方法/链路/入口）彻底移除 LLM 依赖**；仅「方法含义」这类描述文字可选 LLM 兜底（见 §5.6）。

---

## 2. 范围

### 2.1 在范围内

- 表发现、字段定义提取、表关联关系提取、字段使用场景提取、调用链追踪
- 输入方式：本地目录、git 仓库 + ref（branch/tag/commit）、差异扫描（diff 两个 ref）
- 落库方式：POST 后端 API + JDBC 直连 MySQL（两者都支持，默认 POST 后端）
- ORM 覆盖：MyBatis/MyBatis-Plus（XML Mapper）+ JOOQ DSL（与现有 skill 一致）

### 2.2 不在范围内（YAGNI）

- 反射 / 运行时动态代理的解析（标记 `UNRESOLVED`，绝不猜）
- 追踪到外部依赖（第三方 jar）内部——外部符号即边界，停止
- 非 Java 项目
- 手工修改保护、增量 upsert 逻辑本身（后端 `ScanService` 已有，扫描器复用）
- 前端展示改动（不在本次设计内，仅后端数据模型扩展预留字段）

---

## 3. 技术选型

**javac 编译器 API**（`com.sun.source.*`、`JavacTool`、`JavacTask`、`Elements`/`Types`/`Trees`）。

| 方案 | 结论 |
|------|------|
| JavaParser + JavaSymbolSolver | 符号解析近似，复杂泛型/重载/接口实现会出错，达不到 100% |
| javac 编译器 API | **编译器级符号解析**，重载/继承/override/泛型/lambda 权威解析，源码级唯一能接近 100% 的路线 |
| Soot / ASM（字节码） | 需先编译目标项目，构建失败即失效；Spring/泛型/lambda 配置重，工程化脆 |

javac API 是 Error Prone、NullAway、Checker Framework、IDE「Find Usages」底层同款方案，成熟可靠。

> 运行要求：扫描器运行 JDK ≥ 目标项目源码语言级别（扫 Java 8 用 JDK 11/17 均可；扫 Java 17 需 JDK 17+）。

---

## 4. 架构与流水线

```
输入（--path | --repo+--ref | --diff base...head）
   │  git clone/checkout/diff 或直接读本地目录
   ▼
① 源文件收集      .java / *Mapper.xml / *.sql
   ▼
② javac 解析引擎  全量 .java + classpath → attributed AST + 符号表
   ▼
③ 实体/字段索引   @TableName → 表 ↔ 实体类；字段名/类型/PK/注释
   ▼
④ 关联关系提取   XML resultMap/association/collection/JOIN · JOOQ .join().on() · MP 条件构造
   ▼
⑤ 字段访问提取   每字段 setter/getter/直接读写（含 Lombok 合成）
   ▼
⑥ 调用图构建     方法 → 调用者；@Autowired/构造注入 接口→实现绑定
   ▼
⑦ 入口识别       Controller / MQ 消费者 / 定时任务
   ▼
⑧ 链路遍历       从字段访问方法向上到入口，收集完整路径
   ▼
⑨ 操作类型判定   READ/WRITE/UPDATE/DELETE（结合 Mapper 绑定与 receiver 来源）
   ▼
⑩ 输出/落库      富化 JSON → 文件 | POST 后端 | JDBC 直连
   │
⑪ 描述富化(可选)  独立 LLM 步骤：仅对 descriptionSource=NONE 的项生成描述并标记 AI
```

---

## 5. 模块设计

### 5.1 源文件收集与输入

- `--path <dir>`：直接扫本地目录。
- `--repo <url> --ref <branch/tag/commit>`：`git clone` 到临时目录并 checkout 指定 ref。
- `--diff <base>...<head>`：`git diff --name-only base...head` 得变更文件集 `D`（`.java`、`.xml`、`.sql`）。
- 收集三类文件：`.java`（源码）、`*Mapper.xml`（MyBatis）、`*.sql`（DDL，仅用于补注释）。

### 5.2 javac 解析引擎

- 用 `JavacTool`/`JavacTask` 加载全部 `.java`，配置外部 classpath（Maven/Gradle 依赖 jar，尽力收集；缺失不影响，外部符号即边界）。
- 产出 attributed AST（`Tree`）+ `Elements`/`Types`/`Trees`，建立符号表：类、方法（`MethodSymbol`）、字段（`VariableElement`）。
- 遍历用 `TreeScanner`，解析方法调用（`MethodInvocationTree`→`MethodSymbol`）与字段访问（`MemberSelectTree`/`IdentifierTree`→`Symbol`）。

### 5.3 实体识别（表发现）

- 主：`@TableName("tb_order")` → 表名。
- 兜底：`@Entity`/`@Table`，或类名转 snake_case。
- JOOQ：`extends TableImpl` 的生成类，从类字段 `TableField` 取字段。

### 5.4 字段提取

- 字段名（Java 字段名 → 列名：`@TableField` 显式 > 驼峰转下划线）。
- 类型映射（Java→JDBC）：String→VARCHAR、Long/Integer/int→BIGINT、BigDecimal→DECIMAL、Date/LocalDateTime→DATETIME、Boolean→TINYINT、byte[]→BLOB、Double→DOUBLE、Float→FLOAT。
- PK：`@TableId` → `isPk=true`。
- 注释：字段 javadoc / `@ApiModelProperty` / DDL `COMMENT`。
- 通用字段排除（`isBusinessField=false`）：`pk, id, created_at, create_time, updated_at, update_time, deleted_at, delete_time, is_deleted, create_user, create_by, update_user, update_by, delete_user, delete_by, version, tenant_id`。

### 5.5 关联关系提取

- MyBatis XML：`<resultMap>` + `<association>`/`<collection>` 的 property→column 映射；`<select>` 中 `JOIN ... ON o.a = u.b` → `DIRECT_JOIN`。
- JOOQ：`.from(A).join(B).on(A.X.eq(B.Y))` 调用链 → `DIRECT_JOIN`。
- MyBatis-Plus：`LambdaQueryWrapper` 的 `.eq(Entity::getX, ...)` 条件字段 → 关联线索。
- 输出 `RelationDTO`（sourceFieldName/targetTableName/targetFieldName/relationType/methodSignature）。

### 5.6 字段使用场景提取

- 遍历全项目 AST，找对实体字段的访问点，解析到字段 `VariableElement`：
  - READ：getter 调用 / 字段读。
  - WRITE/UPDATE/DELETE：见 §6 操作类型判定。
- Lombok：`@Data`/`@Getter`/`@Setter` 注解的实体，合成 getter/setter 方法符号，使字段访问不遗漏。
- 方法含义 `methodDescription`：**注释优先**——从直接方法的 javadoc/行注释提取（确定性）；无注释时留空，由**独立的可选 LLM 富化步骤**兜底生成，并标记 `descriptionSource=AI`。LLM 富化是扫描器之外的独立步骤（保持扫描器纯确定性），只影响描述文字，不影响结构（字段/方法/链路/入口）。

### 5.7 调用图构建 + 接口绑定

- 方法 → 调用者 map：每个 `MethodInvocationTree` 解析出的 `MethodSymbol`（含继承/override 解析）反查调用者集合。
- 接口→实现绑定：对 `@Autowired` 字段 / 构造注入参数，若类型是接口且有仓库内唯一实现类，绑定到实现；多实现则全部列出（保守）。
- MyBatis Mapper 接口：方法无 Java 实现（实现是 XML），其作为「叶子/边界」保留，用于 DML 判定（§6）。

### 5.8 入口识别（遍历停止点）

- **HTTP Controller**：`@RestController`/`@Controller` + 方法上 `@RequestMapping`/`@GetMapping`/`@PostMapping`/`@PutMapping`/`@DeleteMapping`。
- **MQ 消费者**：`@RabbitListener`/`@KafkaListener`/`@RocketMQMessageListener`/`@JmsListener`。
- **定时任务**：`@Scheduled`/Quartz Job/`@XxlJob`。

### 5.9 链路遍历

- 从字段访问方法 M 出发，沿调用者 map 向上 BFS/DFS，收集所有到达入口的路径。
- 去重（同一路径不重复）、最大深度 10 层。
- 每条路径存为有序数组：入口 → … → M。

---

## 6. 操作类型判定规则（确定性）

| 类型 | 判定 |
|------|------|
| READ | getter 调用 / 字段读 |
| WRITE | setter/字段写，且最终绑定 `<insert>`/`Mapper.insert/save`（或 receiver 为方法内 `new` 的实体） |
| UPDATE | setter/字段写，且绑定 `<update>`/`Mapper.updateById/update`（或 receiver 来自查询结果/方法参数） |
| DELETE | 字段出现在删除流程（`deleteById`/`remove`/`<delete>` 条件或实体） |

判定 WRITE vs UPDATE 的确定顺序：

1. 从直接字段写方法 M 沿调用图向下，找它（直接/间接）调用的 Mapper 方法：`insert`/`save` → WRITE，`updateById`/`update` → UPDATE。
2. 无 Mapper 调用可识别时，按 receiver 来源兜底：`new Entity()` → WRITE；查询结果/方法参数 → UPDATE。
3. 反射/动态绑定无法判定时，标记 `UNRESOLVED`，单独列出，不猜。

---

## 7. 输出结构（JSON）

沿用现有 `ScanResultDTO` 形状（project/tables/fields/relations），`usageScenarios` 富化：

```json
{
  "project": { "appName": "order-system", "gitRepoUrl": "...", "gitLocalPath": "/path" },
  "scanType": "FULL | DIFF",
  "tables": [
    {
      "tableName": "tb_order",
      "tableComment": "订单表",
      "schemaName": "",
      "dbType": "MYSQL",
      "fields": [ { "fieldName": "order_status", "fieldComment": "订单状态", "fieldType": "VARCHAR", "isPk": false, "isBusinessField": true } ],
      "relations": [ { "sourceFieldName": "user_id", "targetTableName": "tb_user", "targetFieldName": "user_id", "relationType": "DIRECT_JOIN", "methodSignature": "com.xx.dao.OrderMapper.selectOrderWithUser" } ],
      "usageScenarios": [
        {
          "fieldName": "order_status",
          "operationType": "UPDATE",
          "methodName": "com.xx.OrderService.cancelOrder",
          "methodDescription": "用户取消订单",
          "descriptionSource": "COMMENT",
          "sourceTableName": "",
          "sourceApiName": "com.xx.OrderController.cancelOrder",
          "callChain": [
            { "className": "com.xx.OrderController", "methodName": "cancelOrder", "signature": "cancelOrder(Long)", "layer": "CONTROLLER" },
            { "className": "com.xx.OrderServiceImpl", "methodName": "cancelOrder", "signature": "cancelOrder(Long)", "layer": "SERVICE" }
          ],
          "entry": { "type": "CONTROLLER", "apiName": "com.xx.OrderController.cancelOrder", "httpMethod": "POST", "path": "/api/order/cancel" }
        }
      ]
    }
  ]
}
```

- `callChain`：有序数组，入口 → 直接方法；`layer` ∈ `CONTROLLER | MQ | SCHEDULED | SERVICE | MAPPER | OTHER`。
- `entry`：按入口类型带不同信息（Controller→httpMethod+path；MQ→queue/topic；定时任务→cron）。

---

## 8. 落库方式

- **`--submit`（默认）**：POST 到现有 `POST /api/scan/result`，后端 `ScanService` 统一写表（复用 upsert + 手工修改保护 + `scan_record`）。后端仅需扩展 DTO/实体字段，**无需新端点**。
- **`--db <jdbc-url> <user> <pass>`**：JDBC 直连 MySQL 写表（不依赖后端进程）。扫描器内复制一份 ID 解析 + upsert 逻辑；此模式为后备，逻辑以 `ScanService` 为准。

**描述富化（独立可选步骤）：** 扫描器只产出确定性结构 + 注释提取的描述（`descriptionSource=COMMENT/NONE`）。对 `NONE` 的项，由扫描器之外的轻量 LLM 步骤（Claude skill 或脚本）生成描述并标记 `descriptionSource=AI`。该步骤不触碰结构字段，结构准确性不受影响。

---

## 9. 后端数据模型变更

`field_usage_scenario` 表：

- 加列 `call_chain`（TEXT，JSON 数组）
- 加列 `entry_info`（TEXT，JSON 对象）
- 加列 `method_description`（VARCHAR 512，或复用 `scenario_description`）
- 加列 `description_source`（VARCHAR 16）：`COMMENT`（注释提取）/ `AI`（LLM 兜底生成）/ `NONE`（无）
- `operation_type` 取值扩展到 `WRITE/UPDATE/READ/DELETE`（需同步 DB 列约束/注释）
- `scan_record.scan_type` 增加 `DIFF` 取值（现有为 `FULL`/`TABLE`）

同步修改：`ScanResultDTO.UsageScenarioDTO`（加 `callChain`/`entry`/`methodDescription`）、`FieldUsageScenario` 实体、`ScanService.receiveScanResult` 的 `processRelationsAndScenarios` 落库逻辑。

---

## 10. 全量扫描 vs 差异扫描

- **全量（FULL）**：checkout 指定 ref → 解析全部 → 输出全部。
- **差异（DIFF）**：`git diff --name-only base...head` 得变更文件集 `D`；在 `head` 上重建调用图（保证正确），但**只重算并输出**「直接字段访问方法定义在 D 中的文件里，或其链上任一方法定义在 D 中的文件里」的实体字段的 usageScenarios，其余不动 → 后端增量更新。

> 差异扫描省的是重算与输出/入库；调用图仍需在 head 上重建（图变了）。这是保证 100% 正确的前提。

---

## 11. 准确性契约

| 档位 | 内容 | 处理 |
|------|------|------|
| ✅ 编译器可解析（100%） | 重载、继承、override、接口实现、泛型、lambda、字段访问 | javac 权威解析 |
| ⚠️ 确定性规则（100%，非猜测） | Lombok getter/setter 合成、MyBatis Mapper 接口→XML、Spring `@Autowired` 接口→实现绑定 | 显式规则实现 |
| ❌ 原理上不可静态解析 | 反射（`Class.forName`/`Method.invoke`）、`BeanUtils.copyProperties`、运行时动态代理 | 标记 `UNRESOLVED`，单独列出，不猜 |

---

## 12. 项目目录结构

```
data-map-scanner/                    # 独立 Maven 项目，与 backend/frontend 平级
├── pom.xml                          # 依赖：JDK tools（javac）、jackson、mysql-connector-j、commons-cli
└── src/main/java/com/datamap/scanner/
    ├── Main.java                    # CLI 入口（参数解析、编排）
    ├── input/                       # SourceCollector、GitSource、LocalSource、DiffResolver
    ├── javac/                       # JavaParser（javac 引擎）、SymbolTable
    ├── entity/                      # EntityResolver、FieldExtractor、JdbcTypeMapper、CommonFieldFilter
    ├── relation/                    # MybatisXmlRelationExtractor、JooqRelationExtractor、RelationAssembler
    ├── usage/                       # FieldAccessCollector、LombokSynthesizer、OperationTypeClassifier
    ├── callgraph/                   # CallGraphBuilder、SpringInterfaceBinder
    ├── entry/                       # EntryPointResolver
    ├── traverse/                    # ChainTraverser
    ├── output/                      # ScanResultAssembler、JsonWriter
    └── persist/                     # ApiSubmitter、JdbcWriter
```

---

## 13. 验收标准

1. **100% 准确**：对样例项目（含重载/继承/接口实现/泛型/lambda/Lombok/Mapper 绑定）人工核验，字段访问与调用链 0 误差；反射/动态代理项被标记 `UNRESOLVED` 而非猜测。
2. **三种输入可用**：`--path`、`--repo --ref`、`--diff base...head` 均能产出 JSON。
3. **两种落库可用**：`--submit` 写后端表、`--db` 直连写表，结果一致。
4. **操作类型四类齐全**：WRITE/UPDATE/READ/DELETE 判定符合 §6 规则。
5. **入口边界正确**：Controller/MQ/定时任务三类入口均被识别为停止点。
6. **完整链路持久化**：`call_chain`/`entry_info`/`method_description`/`description_source` 落库并可被前端读取。
7. **描述来源可区分**：`description_source` 正确标记 `COMMENT`（注释提取）/ `AI`（LLM 兜底）/ `NONE`（无）。

---

## 14. 建议实现分阶段（供 writing-plans 使用）

1. **Phase 1**：javac 解析引擎 + 实体/字段识别 + 关联关系提取（能输出 tables/fields/relations）。
2. **Phase 2**：字段访问提取 + 调用图 + 接口绑定 + 入口识别 + 链路遍历（输出 usageScenarios + callChain + entry）。
3. **Phase 3**：操作类型判定 + 方法含义提取 + Lombok 合成。
4. **Phase 4**：输出 JSON + 落库（POST/JDBC）+ 差异扫描。
5. **Phase 5（可选）**：描述富化独立 LLM 步骤——对 `descriptionSource=NONE` 的项生成描述并标记 `AI`。
