# 扫描器 JOOQ 支持 实现计划

**目标:** 让确定性扫描器支持 JOOQ DSL 项目（`ccbscf-biz-order` 为真实验证对象），补齐表发现、字段提取、字段使用场景、关联关系四块。

**背景:** 现有扫描器只支持 MyBatis-Plus `@TableName` 实体 + XML JOIN。真实项目 `ccbscf-biz-order` 是 JOOQ 3.9.0 项目（236 个 `T_XXX extends TableImpl` 表类），扫描结果为 0 表。

## 已验证的真实模式

1. **表类:** `public class T_TC_ORDER_APPLY extends TableImpl<TTcOrderApplyRecord>`，表名 = 类名。extends 子句简单名 = `TableImpl`。
2. **字段声明:**
   `public final TableField<TTcOrderApplyRecord, Long> SEQUENCE_NO = createField("SEQUENCE_NO", org.jooq.impl.SQLDataType.BIGINT.nullable(false), this, "顺序号");`
   - 列名 = `createField` 第 0 个字符串实参；类型 = 第 1 个实参的 `SQLDataType.XXX`；注释 = 最后一个字符串实参。
3. **PK:** 表类 `getPrimaryKey()` 返回 `Keys.KEY_XXX_PRIMARY`；`Keys.java` 里 `KEY_XXX_PRIMARY = new UniqueKeyImpl<...>(..., table.PK_FIELD1, ...)`。
4. **DSL 操作:**
   - UPDATE: `dsl.update(table).set(table.FIELD, val)`
   - WRITE: `insert(record/pojo)` 或 `dsl.insertInto(table, table.F1, ...).values(...)`
   - READ: `dsl.selectFrom(table).where(table.FIELD.eq(x))`、`table.FIELD.eq(x)`
   - DELETE: `dsl.deleteFrom(table)`
   - JOIN: `.leftJoin(tableB).on(tableA.F.eq(tableB.F))`
5. **Record/Pojo:** `TXxxRecord` 有生成的 `setXxx/getXxx`；领域层 `@Data` POJO 经 `BeanUtils.map` 转换（反射，标记 UNRESOLVED）。

## 任务

### Task J1: 表发现 + 字段提取
- `JooqTableResolver.resolve(AnalysisContext) → Map<String, TypeElement>`（表名 → 表类 TypeElement）：extends 子句简单名 == `TableImpl` 的类。
- `JooqFieldExtractor.extract(TypeElement tableClass, AnalysisContext) → List<ScanField>`：解析 `createField` 字段声明（列名/类型/注释）；PK 从 `getPrimaryKey()`→`Keys.XXX`→`Keys.java` 的 `UniqueKeyImpl(..., table.FIELD)` 解析，找不到则 `isPk=false`。
- 类型映射：`SQLDataType.BIGINT→BIGINT, VARCHAR→VARCHAR, NUMERIC→DECIMAL, INTEGER→INTEGER, TIMESTAMP→DATETIME, BOOLEAN→TINYINT, ...`。

### Task J2: 字段使用场景（操作类型）
- `JooqFieldAccessCollector.collect(AnalysisContext, Map<String,TypeElement>) → Map<String, List<JooqFieldAccess>>`
- `JooqFieldAccess { String fieldName; String operationType; ExecutableElement method; }`
- 识别 `table.FIELD` 引用（`table` 解析为 TableImpl 子类实例，`FIELD` 为该表类的 TableField），按 TreePath 向上语境分类：
  - `.set(FIELD, val)` → UPDATE
  - `insertInto(table, ...FIELD...)` → WRITE
  - `.eq(...)` / `.where(...)` / `.select(...)` / `.selectFrom(...)` → READ
  - `deleteFrom(table)` → DELETE（表级，fieldName 用主键或空）
- 直接方法 = 所在方法（用于调用链）。

### Task J3: 关联关系
- `JooqRelationExtractor.extract(AnalysisContext, Map<String,TypeElement>) → Map<String, List<ScanRelation>>`（按源表）
- 解析 `.join/leftJoin/innerJoin(X).on(A.F.eq(B.F))` → `A.F → B.F` DIRECT_JOIN。

### Task J4: 组装接线
- `ScanResultAssembler.assemble` 同时识别 JOOQ 表 + MyBatis 表，分别走对应提取器；场景/链路/入口共用。
- 运行真实项目验证：表数量 > 0，字段/场景/关联/链路齐全。

## 约束
- 仅 JDK 公共 API；`release=11`；包 `com.datamap.scanner.jooq`。
- commit 尾注 `Co-Authored-By: Claude <noreply@anthropic.com>`。
