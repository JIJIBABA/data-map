# 数据地图确定性扫描器 实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 构建一个独立的 Java CLI 扫描器，用 javac 编译器 API 确定性提取表/字段/关联/字段使用场景/调用链，输出 JSON 并可落库（POST 后端或 JDBC 直连）。

**Architecture:** 独立 Maven 项目 `data-map-scanner/`（与 backend/frontend 平级）。javac 编译器 API 解析 `.java` 源码得到权威符号表；XML/DDL 用 DOM 解析；注解按「简单名」语法匹配（不依赖目标项目 classpath）；从字段访问点向上遍历调用图到入口（Controller/MQ/定时任务）。

**Tech Stack:** Java 11（运行 JDK ≥ 目标源码语言级别）、javac Compiler Tree API（`com.sun.source.*` + `javax.lang.model.*`，全公共 API，无 `--add-exports`）、Jackson（JSON）、mysql-connector-j（JDBC）、JUnit 5（测试）、Maven Shade（fat jar）。

**设计文档：** `docs/superpowers/specs/2026-08-29-data-map-scanner-design.md`

## Global Constraints

- 项目目录：`/Applications/project/data-map/data-map-scanner/`，包根 `com.datamap.scanner`。
- 编译目标 `maven.compiler.release=11`；只用 JDK 公共 API（`com.sun.source.*`、`javax.lang.model.*`、`javax.tools.*`），禁止 `com.sun.tools.javac.*` 内部包。
- 注解一律按**简单名语法匹配**（`@TableName`、`@TableId`、`@RestController`、`@PostMapping`、`@Data` 等），注解字符串值从 AST `AnnotationTree` 语法读取——**不要求目标项目的依赖在 classpath 上**。
- 字段访问（setter/getter/直接读写）必须经 `trees.getElement` 解析到本地 `VariableElement`；DML 判定按 Mapper 方法**名称**匹配（MyBatis-Plus 约定：`insert`/`save`→WRITE，`updateById`/`update`→UPDATE，`deleteById`/`delete`→DELETE，`selectById`/`selectOne`/`selectList`/`getById`→READ 来源）。
- 操作类型取值：`WRITE`/`UPDATE`/`READ`/`DELETE`；入口层 `CONTROLLER`/`MQ`/`SCHEDULED`/`SERVICE`/`MAPPER`/`OTHER`；描述来源 `COMMENT`/`AI`/`NONE`。
- 反射/动态代理不可解析时标记 `UNRESOLVED`，绝不猜。
- 每次提交信息结尾加 `Co-Authored-By: Claude <noreply@anthropic.com>`。

---

### Task 1: 项目脚手架与模型类

**Files:**
- Create: `data-map-scanner/pom.xml`
- Create: `data-map-scanner/src/main/java/com/datamap/scanner/model/ScanResult.java`
- Create: `data-map-scanner/src/main/java/com/datamap/scanner/model/ScanProject.java`
- Create: `data-map-scanner/src/main/java/com/datamap/scanner/model/ScanTable.java`
- Create: `data-map-scanner/src/main/java/com/datamap/scanner/model/ScanField.java`
- Create: `data-map-scanner/src/main/java/com/datamap/scanner/model/ScanRelation.java`
- Create: `data-map-scanner/src/main/java/com/datamap/scanner/model/UsageScenario.java`
- Create: `data-map-scanner/src/main/java/com/datamap/scanner/model/CallChainStep.java`
- Create: `data-map-scanner/src/main/java/com/datamap/scanner/model/EntryInfo.java`
- Create: `data-map-scanner/src/main/java/com/datamap/scanner/Main.java`

**Interfaces:**
- Produces: 模型类（后续所有任务共用）。模型类用**公有 final 字段**（Jackson 默认 `PUBLIC_ONLY` 可序列化 public 字段），不可变语义、无 setter。

- [ ] **Step 1: 写 pom.xml**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
  <modelVersion>4.0.0</modelVersion>
  <groupId>com.datamap</groupId>
  <artifactId>data-map-scanner</artifactId>
  <version>1.0.0</version>
  <packaging>jar</packaging>

  <properties>
    <maven.compiler.release>11</maven.compiler.release>
    <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
  </properties>

  <dependencies>
    <dependency>
      <groupId>com.fasterxml.jackson.core</groupId>
      <artifactId>jackson-databind</artifactId>
      <version>2.15.2</version>
    </dependency>
    <dependency>
      <groupId>com.mysql</groupId>
      <artifactId>mysql-connector-j</artifactId>
      <version>8.0.33</version>
    </dependency>
    <dependency>
      <groupId>org.junit.jupiter</groupId>
      <artifactId>junit-jupiter</artifactId>
      <version>5.10.2</version>
      <scope>test</scope>
    </dependency>
  </dependencies>

  <build>
    <plugins>
      <plugin>
        <groupId>org.apache.maven.plugins</groupId>
        <artifactId>maven-compiler-plugin</artifactId>
        <version>3.11.0</version>
      </plugin>
      <plugin>
        <groupId>org.apache.maven.plugins</groupId>
        <artifactId>maven-shade-plugin</artifactId>
        <version>3.5.1</version>
        <executions>
          <execution>
            <phase>package</phase>
            <goals><goal>shade</goal></goals>
            <configuration>
              <transformers>
                <transformer implementation="org.apache.maven.plugins.shade.resource.ManifestResourceTransformer">
                  <mainClass>com.datamap.scanner.Main</mainClass>
                </transformer>
              </transformers>
            </configuration>
          </execution>
        </executions>
      </plugin>
    </plugins>
  </build>
</project>
```

- [ ] **Step 2: 写模型类（9 个文件）**

```java
// ScanResult.java
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
```

```java
// ScanProject.java
package com.datamap.scanner.model;
public class ScanProject {
    public final String appName;
    public final String gitRepoUrl;
    public final String gitLocalPath;
    public ScanProject(String appName, String gitRepoUrl, String gitLocalPath) {
        this.appName = appName; this.gitRepoUrl = gitRepoUrl; this.gitLocalPath = gitLocalPath;
    }
}
```

```java
// ScanTable.java
package com.datamap.scanner.model;
import java.util.List;
public class ScanTable {
    public final String tableName;
    public final String tableComment;
    public final String schemaName;
    public final String dbType;
    public final List<ScanField> fields;
    public final List<ScanRelation> relations;
    public final List<UsageScenario> usageScenarios;
    public ScanTable(String tableName, String tableComment, String schemaName, String dbType,
                     List<ScanField> fields, List<ScanRelation> relations, List<UsageScenario> usageScenarios) {
        this.tableName = tableName; this.tableComment = tableComment; this.schemaName = schemaName;
        this.dbType = dbType; this.fields = fields; this.relations = relations; this.usageScenarios = usageScenarios;
    }
}
```

```java
// ScanField.java
package com.datamap.scanner.model;
public class ScanField {
    public final String fieldName;
    public final String fieldComment;
    public final String fieldType;
    public final boolean pk;
    public final boolean businessField;
    public ScanField(String fieldName, String fieldComment, String fieldType, boolean pk, boolean businessField) {
        this.fieldName = fieldName; this.fieldComment = fieldComment; this.fieldType = fieldType;
        this.pk = pk; this.businessField = businessField;
    }
}
```

```java
// ScanRelation.java
package com.datamap.scanner.model;
public class ScanRelation {
    public final String sourceFieldName;
    public final String targetTableName;
    public final String targetFieldName;
    public final String relationType;
    public final String methodSignature;
    public ScanRelation(String sourceFieldName, String targetTableName, String targetFieldName,
                        String relationType, String methodSignature) {
        this.sourceFieldName = sourceFieldName; this.targetTableName = targetTableName;
        this.targetFieldName = targetFieldName; this.relationType = relationType; this.methodSignature = methodSignature;
    }
}
```

```java
// UsageScenario.java
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
```

```java
// CallChainStep.java
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
```

```java
// EntryInfo.java
package com.datamap.scanner.model;
public class EntryInfo {
    public final String type;
    public final String apiName;
    public final String httpMethod;
    public final String path;
    public final String queue;
    public final String cron;
    public EntryInfo(String type, String apiName, String httpMethod, String path, String queue, String cron) {
        this.type = type; this.apiName = apiName; this.httpMethod = httpMethod; this.path = path;
        this.queue = queue; this.cron = cron;
    }
}
```

```java
// Main.java
package com.datamap.scanner;

public class Main {
    public static void main(String[] args) {
        System.out.println("data-map-scanner (占位，后续任务实现)");
    }
}
```

- [ ] **Step 3: 编译验证**

Run: `cd /Applications/project/data-map/data-map-scanner && mvn -q compile`
Expected: BUILD SUCCESS，无错误。

- [ ] **Step 4: Commit**

```bash
cd /Applications/project/data-map && git add data-map-scanner
git commit -m "feat(scanner): 项目脚手架与模型类

Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

### Task 2: javac 解析引擎 + 测试 fixture

**Files:**
- Create: `data-map-scanner/src/main/java/com/datamap/scanner/javac/JavaParser.java`
- Create: `data-map-scanner/src/main/java/com/datamap/scanner/javac/AnalysisContext.java`
- Test: `data-map-scanner/src/test/java/com/datamap/scanner/javac/JavaParserTest.java`
- Create fixture: `data-map-scanner/src/test/resources/fixtures/demo/com/example/entity/Order.java`
- Create fixture: `data-map-scanner/src/test/resources/fixtures/demo/com/example/mapper/OrderMapper.java`
- Create fixture: `data-map-scanner/src/test/resources/fixtures/demo/com/example/service/OrderService.java`
- Create fixture: `data-map-scanner/src/test/resources/fixtures/demo/com/example/service/impl/OrderServiceImpl.java`
- Create fixture: `data-map-scanner/src/test/resources/fixtures/demo/com/example/controller/OrderController.java`

**Interfaces:**
- Consumes: 无（模型类已就绪）。
- Produces:
  - `class JavaParser` — `public static AnalysisContext parse(List<Path> javaFiles, String classpath)`
  - `class AnalysisContext` — 字段 `public final List<CompilationUnitTree> units; public final Trees trees; public final Elements elements; public final Types types;`，方法 `public Element resolve(CompilationUnitTree cu, Tree node)`。

- [ ] **Step 1: 写测试 fixture（5 个 Java 文件）**

（外部注解 import 会因无 classpath 报错，但 javac 仍 parse+analyze，注解按简单名语法匹配，本地符号照常解析。）

```java
// com/example/entity/Order.java
package com.example.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

@TableName("tb_order")
public class Order {
    @TableId
    private Long orderId;
    private Long userId;
    private String orderStatus;
    private java.time.LocalDateTime createdAt;

    public Long getOrderId() { return orderId; }
    public void setOrderId(Long orderId) { this.orderId = orderId; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getOrderStatus() { return orderStatus; }
    public void setOrderStatus(String orderStatus) { this.orderStatus = orderStatus; }
    public java.time.LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(java.time.LocalDateTime createdAt) { this.createdAt = createdAt; }
}
```

```java
// com/example/mapper/OrderMapper.java
package com.example.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.entity.Order;

public interface OrderMapper extends BaseMapper<Order> {
}
```

```java
// com/example/service/OrderService.java
package com.example.service;

import com.example.entity.Order;

public interface OrderService {
    void createOrder(Order order);
    void cancelOrder(Long orderId);
}
```

```java
// com/example/service/impl/OrderServiceImpl.java
package com.example.service.impl;

import com.example.entity.Order;
import com.example.mapper.OrderMapper;
import com.example.service.OrderService;
import javax.annotation.Resource;

public class OrderServiceImpl implements OrderService {
    @Resource
    private OrderMapper orderMapper;

    @Override
    public void createOrder(Order order) {
        order.setOrderStatus("CREATED");
        orderMapper.insert(order);
    }

    @Override
    public void cancelOrder(Long orderId) {
        Order order = orderMapper.selectById(orderId);
        order.setOrderStatus("CANCELLED");
        orderMapper.updateById(order);
    }
}
```

```java
// com/example/controller/OrderController.java
package com.example.controller;

import com.example.service.OrderService;
import javax.annotation.Resource;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/order")
public class OrderController {
    @Resource
    private OrderService orderService;

    @PostMapping("/cancel")
    public void cancel(@RequestParam Long orderId) {
        orderService.cancelOrder(orderId);
    }
}
```

- [ ] **Step 2: 写 javac 引擎实现**

```java
// AnalysisContext.java
package com.datamap.scanner.javac;

import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.Tree;
import com.sun.source.util.JavacTask;
import com.sun.source.util.TreePath;
import com.sun.source.util.Trees;
import javax.lang.model.element.Element;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import java.util.ArrayList;
import java.util.List;

public class AnalysisContext {
    public final List<CompilationUnitTree> units;
    public final Trees trees;
    public final Elements elements;
    public final Types types;

    public AnalysisContext(JavacTask task, Iterable<? extends CompilationUnitTree> units) {
        this.trees = Trees.instance(task);
        this.elements = task.getElements();
        this.types = task.getTypes();
        this.units = new ArrayList<>();
        for (CompilationUnitTree u : units) this.units.add(u);
    }

    public Element resolve(CompilationUnitTree cu, Tree node) {
        return trees.getElement(TreePath.getPath(cu, node));
    }
}
```

```java
// JavaParser.java
package com.datamap.scanner.javac;

import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.util.JavacTask;
import javax.tools.*;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class JavaParser {
    public static AnalysisContext parse(List<Path> javaFiles, String classpath) {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) throw new IllegalStateException("未找到 JDK 编译器（需用 JDK 运行，不能用 JRE）");
        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        try (StandardJavaFileManager fm = compiler.getStandardFileManager(diagnostics, Locale.ROOT, StandardCharsets.UTF_8)) {
            List<File> files = new ArrayList<>();
            for (Path p : javaFiles) files.add(p.toFile());
            Iterable<? extends JavaFileObject> units = fm.getJavaFileObjectsFromFiles(files);
            List<String> options = new ArrayList<>();
            options.add("-classpath"); options.add(classpath == null ? "" : classpath);
            options.add("-proc:none");
            JavacTask task = (JavacTask) compiler.getTask(null, fm, diagnostics, options, null, units);
            Iterable<? extends CompilationUnitTree> parsed = task.parse();
            task.analyze(); // 类型归因；错误收集到 diagnostics，不影响已解析的本地符号
            return new AnalysisContext(task, parsed);
        } catch (Exception e) {
            throw new RuntimeException("javac 解析失败: " + e.getMessage(), e);
        }
    }
}
```

- [ ] **Step 3: 写测试**

```java
// JavaParserTest.java
package com.datamap.scanner.javac;

import com.sun.source.tree.CompilationUnitTree;
import org.junit.jupiter.api.Test;
import javax.lang.model.element.Element;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

public class JavaParserTest {

    private AnalysisContext parseFixtures() throws Exception {
        Path root = Paths.get("src/test/resources/fixtures/demo");
        List<Path> files;
        try (Stream<Path> s = Files.walk(root)) {
            files = s.filter(p -> p.toString().endsWith(".java")).collect(Collectors.toList());
        }
        return JavaParser.parse(files, "");
    }

    @Test
    public void parsesAllFixtureUnits() throws Exception {
        AnalysisContext ctx = parseFixtures();
        assertEquals(5, ctx.units.size());
    }

    @Test
    public void resolvesLocalFieldElement() throws Exception {
        AnalysisContext ctx = parseFixtures();
        // 找 Order 类里 userId 字段，验证能通过元素名找到（字段在本地可解析）
        boolean found = false;
        for (CompilationUnitTree cu : ctx.units) {
            if (cu.getSourceFile().getName().endsWith("Order.java")) {
                for (Element e : ctx.elements.getAllMembers(
                        (javax.lang.model.element.TypeElement) cu.getTypeDecls().get(0))) {
                    if (e.getSimpleName().contentEquals("userId")) { found = true; }
                }
            }
        }
        assertTrue(found);
    }
}
```

- [ ] **Step 4: 运行测试验证通过**

Run: `cd /Applications/project/data-map/data-map-scanner && mvn -q test`
Expected: `Tests run: 2, Failures: 0`，BUILD SUCCESS。

- [ ] **Step 5: Commit**

```bash
cd /Applications/project/data-map && git add data-map-scanner
git commit -m "feat(scanner): javac 解析引擎与测试 fixture

Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

### Task 3: 实体识别 + 字段提取

**Files:**
- Create: `data-map-scanner/src/main/java/com/datamap/scanner/entity/EntityResolver.java`
- Create: `data-map-scanner/src/main/java/com/datamap/scanner/entity/JdbcTypeMapper.java`
- Create: `data-map-scanner/src/main/java/com/datamap/scanner/entity/CommonFieldFilter.java`
- Create: `data-map-scanner/src/main/java/com/datamap/scanner/entity/FieldExtractor.java`
- Test: `data-map-scanner/src/test/java/com/datamap/scanner/entity/EntityResolverTest.java`
- Test: `data-map-scanner/src/test/java/com/datamap/scanner/entity/FieldExtractorTest.java`

**Interfaces:**
- Consumes: `AnalysisContext`（Task 2）。
- Produces:
  - `class EntityResolver` — `public static Map<String, TypeElement> resolve(AnalysisContext ctx)`，返回 `表名 -> 实体 TypeElement`。
  - `class JdbcTypeMapper` — `public static String map(String javaTypeName)`。
  - `class CommonFieldFilter` — `public static boolean isBusiness(String fieldName)`。
  - `class FieldExtractor` — `public static List<ScanField> extract(TypeElement entity, AnalysisContext ctx)`。

- [ ] **Step 1: 写 EntityResolver（注解简单名匹配 + 语法读值）**

```java
// EntityResolver.java
package com.datamap.scanner.entity;

import com.datamap.scanner.javac.AnalysisContext;
import com.sun.source.tree.AnnotationTree;
import com.sun.source.tree.AssignmentTree;
import com.sun.source.tree.ClassTree;
import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.LiteralTree;
import com.sun.source.tree.Tree;

import javax.lang.model.element.TypeElement;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class EntityResolver {
    /** 从 @TableName("tb_order") 语法读取表名，无需解析注解类型。 */
    public static Map<String, TypeElement> resolve(AnalysisContext ctx) {
        Map<String, TypeElement> result = new HashMap<>();
        for (CompilationUnitTree cu : ctx.units) {
            for (Tree decl : cu.getTypeDecls()) {
                if (!(decl instanceof ClassTree)) continue;
                ClassTree cls = (ClassTree) decl;
                String tableName = tableName(cls);
                if (tableName == null) continue;
                TypeElement te = (TypeElement) ctx.elements.getTypeElement(
                        binaryName(cu, cls.getSimpleName().toString()));
                result.put(tableName, te);
            }
        }
        return result;
    }

    private static String tableName(ClassTree cls) {
        if (cls.getModifiers() == null || cls.getModifiers().getAnnotations() == null) return null;
        for (AnnotationTree ann : cls.getModifiers().getAnnotations()) {
            String simple = simpleName(ann.getAnnotationType().toString());
            if (!"TableName".equals(simple)) continue;
            List<? extends ExpressionTree> args = ann.getArguments();
            if (!args.isEmpty()) {
                ExpressionTree first = args.get(0);
                if (first instanceof AssignmentTree) first = ((AssignmentTree) first).getExpression();
                if (first instanceof LiteralTree) return String.valueOf(((LiteralTree) first).getValue());
            }
        }
        return null;
    }

    static String simpleName(String qualified) {
        int i = qualified.lastIndexOf('.');
        return i >= 0 ? qualified.substring(i + 1) : qualified;
    }

    static String binaryName(CompilationUnitTree cu, String typeName) {
        CharSequence pkg = cu.getPackageName();
        return pkg == null || pkg.length() == 0 ? typeName : pkg + "." + typeName;
    }
}
```

- [ ] **Step 2: 写 JdbcTypeMapper 与 CommonFieldFilter**

```java
// JdbcTypeMapper.java
package com.datamap.scanner.entity;

public class JdbcTypeMapper {
    public static String map(String javaTypeName) {
        String t = javaTypeName;
        int dot = t.lastIndexOf('.');
        if (dot >= 0) t = t.substring(dot + 1);
        switch (t) {
            case "String": return "VARCHAR";
            case "Long": case "long": case "Integer": case "int": return "BIGINT";
            case "BigDecimal": return "DECIMAL";
            case "Date": case "LocalDateTime": return "DATETIME";
            case "Boolean": case "boolean": return "TINYINT";
            case "byte": return "BLOB";
            case "Double": case "double": return "DOUBLE";
            case "Float": case "float": return "FLOAT";
            default: return "VARCHAR";
        }
    }
}
```

```java
// CommonFieldFilter.java
package com.datamap.scanner.entity;

import java.util.Set;

public class CommonFieldFilter {
    private static final Set<String> COMMON = Set.of(
        "pk","id","created_at","create_time","updated_at","update_time","deleted_at","delete_time",
        "is_deleted","create_user","create_by","update_user","update_by","delete_user","delete_by",
        "version","tenant_id");

    public static boolean isBusiness(String fieldName) {
        return !COMMON.contains(fieldName);
    }
}
```

- [ ] **Step 3: 写 FieldExtractor（驼峰转下划线 + @TableId 判 PK + javadoc 取注释）**

```java
// FieldExtractor.java
package com.datamap.scanner.entity;

import com.datamap.scanner.javac.AnalysisContext;
import com.datamap.scanner.model.ScanField;
import com.sun.source.doctree.DocCommentTree;
import com.sun.source.tree.AnnotationTree;
import com.sun.source.tree.VariableTree;
import com.sun.source.util.DocTrees;

import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import java.util.ArrayList;
import java.util.List;

public class FieldExtractor {
    public static List<ScanField> extract(TypeElement entity, AnalysisContext ctx) {
        List<ScanField> fields = new ArrayList<>();
        DocTrees docTrees = DocTrees.instance(ctx.trees);
        for (VariableElement field : fieldsOf(entity, ctx)) {
            if (field.getKind() != javax.lang.model.element.ElementKind.FIELD) continue;
            if (field.getModifiers().contains(javax.lang.model.element.Modifier.STATIC)) continue;
            String column = camelToSnake(field.getSimpleName().toString());
            boolean pk = hasAnnotation(ctx, field, "TableId");
            String comment = "";
            DocCommentTree doc = docTrees.getDocCommentTree(field);
            if (doc != null) comment = doc.getFullBody().toString().trim();
            String jdbcType = JdbcTypeMapper.map(field.asType().toString());
            fields.add(new ScanField(column, comment, jdbcType, pk, CommonFieldFilter.isBusiness(column)));
        }
        return fields;
    }

    private static List<VariableElement> fieldsOf(TypeElement entity, AnalysisContext ctx) {
        List<VariableElement> list = new ArrayList<>();
        for (javax.lang.model.element.Element e : ctx.elements.getAllMembers(entity)) {
            if (e instanceof VariableElement) list.add((VariableElement) e);
        }
        return list;
    }

    static boolean hasAnnotation(AnalysisContext ctx, javax.lang.model.element.Element e, String simpleAnnoName) {
        for (javax.lang.model.element.AnnotationMirror m : e.getAnnotationMirrors()) {
            String q = m.getAnnotationType().toString();
            if (EntityResolver.simpleName(q).equals(simpleAnnoName)) return true;
        }
        return false;
    }

    static String camelToSnake(String camel) {
        StringBuilder sb = new StringBuilder();
        for (char c : camel.toCharArray()) {
            if (Character.isUpperCase(c)) { sb.append('_').append(Character.toLowerCase(c)); }
            else sb.append(c);
        }
        return sb.toString();
    }
}
```

- [ ] **Step 4: 写测试**

```java
// EntityResolverTest.java
package com.datamap.scanner.entity;

import com.datamap.scanner.javac.AnalysisContext;
import com.datamap.scanner.javac.JavaParser;
import org.junit.jupiter.api.Test;
import javax.lang.model.element.TypeElement;
import java.nio.file.*;
import java.util.*;
import java.util.stream.*;

import static org.junit.jupiter.api.Assertions.*;

public class EntityResolverTest {
    private AnalysisContext ctx() throws Exception {
        Path root = Paths.get("src/test/resources/fixtures/demo");
        List<Path> files;
        try (Stream<Path> s = Files.walk(root)) {
            files = s.filter(p -> p.toString().endsWith(".java")).collect(Collectors.toList());
        }
        return JavaParser.parse(files, "");
    }

    @Test
    public void findsOrderEntityByTableName() throws Exception {
        Map<String, TypeElement> map = EntityResolver.resolve(ctx());
        assertTrue(map.containsKey("tb_order"));
        assertEquals("Order", map.get("tb_order").getSimpleName().toString());
    }
}
```

```java
// FieldExtractorTest.java
package com.datamap.scanner.entity;

import com.datamap.scanner.javac.AnalysisContext;
import com.datamap.scanner.javac.JavaParser;
import com.datamap.scanner.model.ScanField;
import org.junit.jupiter.api.Test;
import javax.lang.model.element.TypeElement;
import java.nio.file.*;
import java.util.*;
import java.util.stream.*;

import static org.junit.jupiter.api.Assertions.*;

public class FieldExtractorTest {
    private AnalysisContext ctx() throws Exception {
        Path root = Paths.get("src/test/resources/fixtures/demo");
        List<Path> files;
        try (Stream<Path> s = Files.walk(root)) {
            files = s.filter(p -> p.toString().endsWith(".java")).collect(Collectors.toList());
        }
        return JavaParser.parse(files, "");
    }

    @Test
    public void extractsOrderFieldsWithPkAndBusinessFlag() throws Exception {
        AnalysisContext ctx = ctx();
        TypeElement order = EntityResolver.resolve(ctx).get("tb_order");
        List<ScanField> fields = FieldExtractor.extract(order, ctx);
        assertEquals(4, fields.size());
        ScanField id = fields.stream().filter(f -> f.fieldName.equals("order_id")).findFirst().get();
        assertTrue(id.pk);
        ScanField status = fields.stream().filter(f -> f.fieldName.equals("order_status")).findFirst().get();
        assertTrue(status.businessField);
        assertFalse(fields.stream().filter(f -> f.fieldName.equals("created_at")).findFirst().get().businessField);
    }
}
```

- [ ] **Step 5: 运行测试验证通过**

Run: `cd /Applications/project/data-map/data-map-scanner && mvn -q test`
Expected: `Tests run: 4, Failures: 0`（含 Task 2 的 2 个）。

- [ ] **Step 6: Commit**

```bash
cd /Applications/project/data-map && git add data-map-scanner
git commit -m "feat(scanner): 实体识别与字段提取

Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

### Task 4: 关联关系提取（MyBatis XML + JOOQ）

**Files:**
- Create: `data-map-scanner/src/main/java/com/datamap/scanner/relation/MybatisXmlRelationExtractor.java`
- Create: `data-map-scanner/src/main/java/com/datamap/scanner/relation/JooqRelationExtractor.java`
- Create: `data-map-scanner/src/main/java/com/datamap/scanner/relation/RelationAssembler.java`
- Create fixture: `data-map-scanner/src/test/resources/fixtures/demo/mapper/OrderMapper.xml`
- Test: `data-map-scanner/src/test/java/com/datamap/scanner/relation/MybatisXmlRelationExtractorTest.java`

**Interfaces:**
- Consumes: 模型类（Task 1）。
- Produces:
  - `class MybatisXmlRelationExtractor` — `public static List<ScanRelation> extract(Path xmlFile)`（`sourceTableName` 由调用方补充，见 RelationAssembler）。
  - `class JooqRelationExtractor` — `public static List<ScanRelation> extract(AnalysisContext ctx, Map<String,TypeElement> entities)`（初版可返回空，后续迭代补全）。
  - `class RelationAssembler` — `public static List<ScanRelation> assemble(String sourceTableName, List<ScanRelation> raw)`，填充 `sourceFieldName` 归属。

- [ ] **Step 1: 写 fixture OrderMapper.xml**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE mapper PUBLIC "-//mybatis.org//DTD Mapper 3.0//EN"
  "http://mybatis.org/dtd/mybatis-3-mapper.dtd">
<mapper namespace="com.example.mapper.OrderMapper">
  <select id="selectOrderWithUser" resultType="com.example.entity.Order">
    SELECT o.order_id, o.user_id, o.order_status
    FROM tb_order o
    LEFT JOIN tb_user u ON o.user_id = u.user_id
  </select>
</mapper>
```

- [ ] **Step 2: 写 MybatisXmlRelationExtractor（正则提取 JOIN ... ON a.x = b.y）**

```java
// MybatisXmlRelationExtractor.java
package com.datamap.scanner.relation;

import com.datamap.scanner.model.ScanRelation;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MybatisXmlRelationExtractor {
    // 匹配 JOIN ... tb_user u ON o.user_id = u.user_id
    private static final Pattern JOIN = Pattern.compile(
        "JOIN\\s+(\\w+)\\s+(\\w+)\\s+ON\\s+(\\w+)\\.(\\w+)\\s*=\\s*(\\w+)\\.(\\w+)",
        Pattern.CASE_INSENSITIVE);

    public static List<ScanRelation> extract(Path xmlFile) throws Exception {
        List<ScanRelation> out = new ArrayList<>();
        String text = new String(Files.readAllBytes(xmlFile), java.nio.charset.StandardCharsets.UTF_8);
        Matcher m = JOIN.matcher(text);
        while (m.find()) {
            // 左侧为主表(别名 o)，右侧为被 join 表(别名 u)
            String leftAlias = m.group(3), leftCol = m.group(4);
            String rightTable = m.group(1), rightAlias = m.group(5), rightCol = m.group(6);
            out.add(new ScanRelation(leftCol, rightTable, rightCol, "DIRECT_JOIN", ""));
        }
        return out;
    }
}
```

- [ ] **Step 3: 写 JooqRelationExtractor 与 RelationAssembler**

```java
// JooqRelationExtractor.java
package com.datamap.scanner.relation;

import com.datamap.scanner.javac.AnalysisContext;
import com.datamap.scanner.model.ScanRelation;
import javax.lang.model.element.TypeElement;
import java.util.List;
import java.util.Map;

public class JooqRelationExtractor {
    // 初版：返回空。JOOQ 项目接入时补全（.join().on() 调用链提取）。
    public static List<ScanRelation> extract(AnalysisContext ctx, Map<String, TypeElement> entities) {
        return List.of();
    }
}
```

```java
// RelationAssembler.java
package com.datamap.scanner.relation;

import com.datamap.scanner.model.ScanRelation;
import java.util.ArrayList;
import java.util.List;

public class RelationAssembler {
    public static List<ScanRelation> assemble(String sourceTableName, List<ScanRelation> raw) {
        List<ScanRelation> out = new ArrayList<>();
        for (ScanRelation r : raw) {
            out.add(new ScanRelation(r.sourceFieldName, r.targetTableName, r.targetFieldName,
                    r.relationType, r.methodSignature));
        }
        return out;
    }
}
```

- [ ] **Step 4: 写测试**

```java
// MybatisXmlRelationExtractorTest.java
package com.datamap.scanner.relation;

import com.datamap.scanner.model.ScanRelation;
import org.junit.jupiter.api.Test;
import java.nio.file.Paths;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

public class MybatisXmlRelationExtractorTest {
    @Test
    public void extractsJoinRelation() throws Exception {
        List<ScanRelation> rels = MybatisXmlRelationExtractor.extract(
            Paths.get("src/test/resources/fixtures/demo/mapper/OrderMapper.xml"));
        assertEquals(1, rels.size());
        assertEquals("user_id", rels.get(0).sourceFieldName);
        assertEquals("tb_user", rels.get(0).targetTableName);
        assertEquals("user_id", rels.get(0).targetFieldName);
        assertEquals("DIRECT_JOIN", rels.get(0).relationType);
    }
}
```

- [ ] **Step 5: 运行测试验证通过**

Run: `cd /Applications/project/data-map/data-map-scanner && mvn -q test`
Expected: `Tests run: 5, Failures: 0`。

- [ ] **Step 6: Commit**

```bash
cd /Applications/project/data-map && git add data-map-scanner
git commit -m "feat(scanner): 关联关系提取(MyBatis XML)

Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

### Task 5: 字段访问提取 + Lombok 合成

**Files:**
- Create: `data-map-scanner/src/main/java/com/datamap/scanner/usage/FieldAccess.java`
- Create: `data-map-scanner/src/main/java/com/datamap/scanner/usage/FieldAccessCollector.java`
- Create: `data-map-scanner/src/main/java/com/datamap/scanner/usage/LombokSynthesizer.java`
- Test: `data-map-scanner/src/test/java/com/datamap/scanner/usage/FieldAccessCollectorTest.java`

**Interfaces:**
- Consumes: `AnalysisContext`（Task 2）、`EntityResolver`（Task 3）。
- Produces:
  - `class FieldAccess` — 字段 `public final String fieldName; public final Kind kind; public final ExecutableElement method;`，`enum Kind { READ, WRITE }`。
  - `class FieldAccessCollector` — `public static Map<String, List<FieldAccess>> collect(AnalysisContext ctx, Map<String, TypeElement> entities)`，key 为「表名.字段名」。
  - `class LombokSynthesizer` — `public static boolean hasGetterSetter(TypeElement entity)`（有 `@Data`/`@Getter`/`@Setter` 即 true）。

- [ ] **Step 1: 写 FieldAccess 与 LombokSynthesizer**

```java
// FieldAccess.java
package com.datamap.scanner.usage;

import javax.lang.model.element.ExecutableElement;

public class FieldAccess {
    public enum Kind { READ, WRITE }
    public final String fieldName;
    public final Kind kind;
    public final ExecutableElement method;
    public FieldAccess(String fieldName, Kind kind, ExecutableElement method) {
        this.fieldName = fieldName; this.kind = kind; this.method = method;
    }
}
```

```java
// LombokSynthesizer.java
package com.datamap.scanner.usage;

import com.datamap.scanner.javac.AnalysisContext;
import javax.lang.model.element.TypeElement;

public class LombokSynthesizer {
    /** @Data/@Getter/@Setter 注解的实体，getter/setter 由 Lombok 生成（源码无 setter 方法）。 */
    public static boolean hasGetterSetter(TypeElement entity) {
        for (javax.lang.model.element.AnnotationMirror m : entity.getAnnotationMirrors()) {
            String s = m.getAnnotationType().toString();
            int i = s.lastIndexOf('.');
            String n = i >= 0 ? s.substring(i + 1) : s;
            if (n.equals("Data") || n.equals("Getter") || n.equals("Setter")) return true;
        }
        return false;
    }
}
```

- [ ] **Step 2: 写 FieldAccessCollector（setter/getter 调用解析到字段）**

```java
// FieldAccessCollector.java
package com.datamap.scanner.usage;

import com.datamap.scanner.javac.AnalysisContext;
import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.MethodInvocationTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.Tree;
import com.sun.source.util.TreePath;
import com.sun.source.util.TreePathScanner;

import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import java.util.*;

public class FieldAccessCollector {
    public static Map<String, List<FieldAccess>> collect(AnalysisContext ctx, Map<String, TypeElement> entities) {
        Map<String, List<FieldAccess>> result = new HashMap<>();
        for (CompilationUnitTree cu : ctx.units) {
            new TreePathScanner<Void, Void>() {
                @Override public Void visitMethodInvocation(MethodInvocationTree node, Void p) {
                    Element e = ctx.resolve(cu, node);
                    if (e instanceof ExecutableElement) {
                        ExecutableElement method = (ExecutableElement) e;
                        String name = method.getSimpleName().toString();
                        if (name.startsWith("set") && name.length() > 3 && method.getParameters().size() == 1) {
                            record(ctx, cu, node, method, name.substring(3), FieldAccess.Kind.WRITE, result, entities);
                        } else if (name.startsWith("get") && name.length() > 3 && method.getParameters().isEmpty()) {
                            record(ctx, cu, node, method, name.substring(3), FieldAccess.Kind.READ, result, entities);
                        }
                    }
                    return super.visitMethodInvocation(node, p);
                }

                private void record(AnalysisContext ctx, CompilationUnitTree cu, Tree node,
                                    ExecutableElement method, String prop, FieldAccess.Kind kind,
                                    Map<String, List<FieldAccess>> result, Map<String, TypeElement> entities) {
                    String table = tableOf(method.getEnclosingElement(), entities);
                    if (table == null) return;
                    String fieldName = camelToSnake(prop);
                    ExecutableElement caller = enclosingMethod(ctx, cu, node);
                    if (caller == null) return;
                    result.computeIfAbsent(table + "." + fieldName, k -> new ArrayList<>())
                          .add(new FieldAccess(fieldName, kind, caller));
                }
            }.scan(cu, null);
        }
        return result;
    }

    private static String tableOf(Element enclosing, Map<String, TypeElement> entities) {
        if (!(enclosing instanceof TypeElement)) return null;
        for (Map.Entry<String, TypeElement> e : entities.entrySet()) {
            if (e.getValue().equals(enclosing)) return e.getKey();
        }
        return null;
    }

    private static ExecutableElement enclosingMethod(AnalysisContext ctx, CompilationUnitTree cu, Tree node) {
        TreePath path = TreePath.getPath(cu, node);
        while (path != null) {
            if (path.getLeaf() instanceof MethodTree) {
                Element e = ctx.trees.getElement(new TreePath(path, ((MethodTree) path.getLeaf()).getBody() == null
                        ? path.getLeaf() : ((MethodTree) path.getLeaf()).getBody()));
                if (e instanceof ExecutableElement) return (ExecutableElement) e;
            }
            path = path.getParentPath();
        }
        return null;
    }

    private static String camelToSnake(String camel) {
        StringBuilder sb = new StringBuilder();
        for (char c : camel.toCharArray()) {
            if (Character.isUpperCase(c)) sb.append('_').append(Character.toLowerCase(c));
            else sb.append(c);
        }
        return sb.toString();
    }
}
```

- [ ] **Step 3: 写测试**

```java
// FieldAccessCollectorTest.java
package com.datamap.scanner.usage;

import com.datamap.scanner.entity.EntityResolver;
import com.datamap.scanner.javac.AnalysisContext;
import com.datamap.scanner.javac.JavaParser;
import org.junit.jupiter.api.Test;
import javax.lang.model.element.TypeElement;
import java.nio.file.*;
import java.util.*;
import java.util.stream.*;
import static org.junit.jupiter.api.Assertions.*;

public class FieldAccessCollectorTest {
    private Map<String, List<FieldAccess>> collect() throws Exception {
        Path root = Paths.get("src/test/resources/fixtures/demo");
        List<Path> files;
        try (Stream<Path> s = Files.walk(root)) {
            files = s.filter(p -> p.toString().endsWith(".java")).collect(Collectors.toList());
        }
        AnalysisContext ctx = JavaParser.parse(files, "");
        Map<String, TypeElement> entities = EntityResolver.resolve(ctx);
        return FieldAccessCollector.collect(ctx, entities);
    }

    @Test
    public void findsWriteOfOrderStatus() throws Exception {
        Map<String, List<FieldAccess>> all = collect();
        assertTrue(all.containsKey("tb_order.order_status"));
        List<FieldAccess> accesses = all.get("tb_order.order_status");
        assertEquals(2, accesses.size()); // createOrder 与 cancelOrder 各写一次
        assertTrue(accesses.stream().allMatch(a -> a.kind == FieldAccess.Kind.WRITE));
    }
}
```

- [ ] **Step 4: 运行测试验证通过**

Run: `cd /Applications/project/data-map/data-map-scanner && mvn -q test`
Expected: `Tests run: 6, Failures: 0`。

- [ ] **Step 5: Commit**

```bash
cd /Applications/project/data-map && git add data-map-scanner
git commit -m "feat(scanner): 字段访问提取(setter/getter)

Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

### Task 6: 调用图构建 + 接口绑定

**Files:**
- Create: `data-map-scanner/src/main/java/com/datamap/scanner/callgraph/CallGraph.java`
- Create: `data-map-scanner/src/main/java/com/datamap/scanner/callgraph/CallGraphBuilder.java`
- Create: `data-map-scanner/src/main/java/com/datamap/scanner/callgraph/SpringInterfaceBinder.java`
- Test: `data-map-scanner/src/test/java/com/datamap/scanner/callgraph/CallGraphBuilderTest.java`

**Interfaces:**
- Consumes: `AnalysisContext`（Task 2）。
- Produces:
  - `class CallGraph` — `public Set<ExecutableElement> callers(ExecutableElement callee)`；`public void add(ExecutableElement caller, ExecutableElement callee)`。
  - `class CallGraphBuilder` — `public static CallGraph build(AnalysisContext ctx)`。
  - `class SpringInterfaceBinder` — `public static Map<TypeElement, TypeElement> bind(AnalysisContext ctx)`（接口 TypeElement → 唯一实现 TypeElement，无唯一实现则不绑定）。

- [ ] **Step 1: 写 CallGraph**

```java
// CallGraph.java
package com.datamap.scanner.callgraph;

import javax.lang.model.element.ExecutableElement;
import java.util.*;

public class CallGraph {
    private final Map<ExecutableElement, Set<ExecutableElement>> callers = new HashMap<>();
    private final Map<ExecutableElement, Set<ExecutableElement>> callees = new HashMap<>();

    public void add(ExecutableElement caller, ExecutableElement callee) {
        callers.computeIfAbsent(callee, k -> new LinkedHashSet<>()).add(caller);
        callees.computeIfAbsent(caller, k -> new LinkedHashSet<>()).add(callee);
    }

    public Set<ExecutableElement> callers(ExecutableElement callee) {
        return callers.getOrDefault(callee, Collections.emptySet());
    }

    public Set<ExecutableElement> callees(ExecutableElement caller) {
        return callees.getOrDefault(caller, Collections.emptySet());
    }
}
```

- [ ] **Step 2: 写 CallGraphBuilder（含 override 展开）**

```java
// CallGraphBuilder.java
package com.datamap.scanner.callgraph;

import com.datamap.scanner.javac.AnalysisContext;
import com.sun.source.tree.*;
import com.sun.source.util.TreePath;
import com.sun.source.util.TreePathScanner;

import javax.lang.model.element.*;
import javax.lang.model.type.TypeMirror;
import java.util.*;

public class CallGraphBuilder {
    public static CallGraph build(AnalysisContext ctx) {
        CallGraph g = new CallGraph();
        Map<String, List<ExecutableElement>> byName = indexByName(ctx);
        for (CompilationUnitTree cu : ctx.units) {
            new TreePathScanner<Void, Void>() {
                @Override public Void visitMethodInvocation(MethodInvocationTree node, Void p) {
                    Element e = ctx.resolve(cu, node);
                    ExecutableElement caller = enclosingMethod(ctx, cu, node);
                    if (e instanceof ExecutableElement && caller != null) {
                        ExecutableElement callee = (ExecutableElement) e;
                        for (ExecutableElement target : targets(ctx, callee, byName)) {
                            g.add(caller, target);
                        }
                    }
                    return super.visitMethodInvocation(node, p);
                }
            }.scan(cu, null);
        }
        return g;
    }

    private static Map<String, List<ExecutableElement>> indexByName(AnalysisContext ctx) {
        Map<String, List<ExecutableElement>> m = new HashMap<>();
        for (CompilationUnitTree cu : ctx.units) {
            for (Tree decl : cu.getTypeDecls()) {
                if (!(decl instanceof ClassTree)) continue;
                Element te = ctx.resolve(cu, decl);
                if (!(te instanceof TypeElement)) continue;
                for (Element member : ctx.elements.getAllMembers((TypeElement) te)) {
                    if (member instanceof ExecutableElement) {
                        m.computeIfAbsent(member.getSimpleName().toString(), k -> new ArrayList<>())
                         .add((ExecutableElement) member);
                    }
                }
            }
        }
        return m;
    }

    /** 抽象/接口方法展开到仓库内所有实现；具体方法直接用自身。 */
    private static List<ExecutableElement> targets(AnalysisContext ctx, ExecutableElement callee,
                                                   Map<String, List<ExecutableElement>> byName) {
        Element owner = callee.getEnclosingElement();
        boolean concrete = owner instanceof TypeElement
                && !((TypeElement) owner).getKind().isInterface()
                && !callee.getModifiers().contains(Modifier.ABSTRACT);
        if (concrete) return List.of(callee);

        List<ExecutableElement> impls = new ArrayList<>();
        for (ExecutableElement cand : byName.getOrDefault(callee.getSimpleName().toString(), List.of())) {
            TypeElement candOwner = (TypeElement) cand.getEnclosingElement();
            if (candOwner.getKind().isInterface()) continue;
            if (ctx.elements.overrides(cand, callee, candOwner)) impls.add(cand);
        }
        return impls.isEmpty() ? List.of(callee) : impls;
    }

    private static ExecutableElement enclosingMethod(AnalysisContext ctx, CompilationUnitTree cu, Tree node) {
        TreePath path = TreePath.getPath(cu, node);
        while (path != null) {
            if (path.getLeaf() instanceof MethodTree) {
                Element e = ctx.resolve(cu, path.getLeaf());
                if (e instanceof ExecutableElement) return (ExecutableElement) e;
            }
            path = path.getParentPath();
        }
        return null;
    }
}
```

- [ ] **Step 3: 写 SpringInterfaceBinder**

```java
// SpringInterfaceBinder.java
package com.datamap.scanner.callgraph;

import com.datamap.scanner.javac.AnalysisContext;
import com.sun.source.tree.ClassTree;
import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.Tree;

import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import java.util.*;

public class SpringInterfaceBinder {
    /** 找出仓库内每个接口的唯一实现类。 */
    public static Map<TypeElement, TypeElement> bind(AnalysisContext ctx) {
        Map<TypeElement, List<TypeElement>> impls = new HashMap<>();
        for (CompilationUnitTree cu : ctx.units) {
            for (Tree decl : cu.getTypeDecls()) {
                if (!(decl instanceof ClassTree)) continue;
                Element e = ctx.resolve(cu, decl);
                if (!(e instanceof TypeElement)) continue;
                TypeElement cls = (TypeElement) e;
                for (TypeElement iface : interfacesOf(ctx, cls)) {
                    impls.computeIfAbsent(iface, k -> new ArrayList<>()).add(cls);
                }
            }
        }
        Map<TypeElement, TypeElement> bind = new HashMap<>();
        for (Map.Entry<TypeElement, List<TypeElement>> en : impls.entrySet()) {
            if (en.getValue().size() == 1) bind.put(en.getKey(), en.getValue().get(0));
        }
        return bind;
    }

    private static List<TypeElement> interfacesOf(AnalysisContext ctx, TypeElement cls) {
        List<TypeElement> out = new ArrayList<>();
        for (javax.lang.model.type.TypeMirror t : cls.getInterfaces()) {
            Element e = ctx.types.asElement(t);
            if (e instanceof TypeElement) out.add((TypeElement) e);
        }
        return out;
    }
}
```

- [ ] **Step 4: 写测试**

```java
// CallGraphBuilderTest.java
package com.datamap.scanner.callgraph;

import com.datamap.scanner.javac.AnalysisContext;
import com.datamap.scanner.javac.JavaParser;
import org.junit.jupiter.api.Test;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import java.nio.file.*;
import java.util.*;
import java.util.stream.*;

import static org.junit.jupiter.api.Assertions.*;

public class CallGraphBuilderTest {
    private AnalysisContext ctx() throws Exception {
        Path root = Paths.get("src/test/resources/fixtures/demo");
        List<Path> files;
        try (Stream<Path> s = Files.walk(root)) {
            files = s.filter(p -> p.toString().endsWith(".java")).collect(Collectors.toList());
        }
        return JavaParser.parse(files, "");
    }

    @Test
    public void cancelOrderHasCallerInController() throws Exception {
        AnalysisContext ctx = ctx();
        CallGraph g = CallGraphBuilder.build(ctx);
        ExecutableElement cancel = method(ctx, "com.example.service.impl.OrderServiceImpl", "cancelOrder");
        assertNotNull(cancel);
        // controller.cancel 调用了 service.cancelOrder（经接口展开到 impl）
        Set<ExecutableElement> callers = g.callers(cancel);
        assertFalse(callers.isEmpty());
        assertTrue(callers.stream().anyMatch(c ->
            c.getEnclosingElement().toString().contains("OrderController")));
    }

    private ExecutableElement method(AnalysisContext ctx, String className, String methodName) {
        TypeElement te = ctx.elements.getTypeElement(className);
        if (te == null) return null;
        for (javax.lang.model.element.Element e : ctx.elements.getAllMembers(te)) {
            if (e instanceof ExecutableElement && e.getSimpleName().contentEquals(methodName))
                return (ExecutableElement) e;
        }
        return null;
    }
}
```

- [ ] **Step 5: 运行测试验证通过**

Run: `cd /Applications/project/data-map/data-map-scanner && mvn -q test`
Expected: `Tests run: 7, Failures: 0`。

- [ ] **Step 6: Commit**

```bash
cd /Applications/project/data-map && git add data-map-scanner
git commit -m "feat(scanner): 调用图构建与接口绑定

Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

### Task 7: 入口识别

**Files:**
- Create: `data-map-scanner/src/main/java/com/datamap/scanner/entry/EntryPoint.java`
- Create: `data-map-scanner/src/main/java/com/datamap/scanner/entry/EntryPointResolver.java`
- Test: `data-map-scanner/src/test/java/com/datamap/scanner/entry/EntryPointResolverTest.java`

**Interfaces:**
- Consumes: `AnalysisContext`（Task 2）、`EntryInfo` 模型（Task 1）。
- Produces:
  - `class EntryPoint` — `public final ExecutableElement method; public final EntryInfo info;`
  - `class EntryPointResolver` — `public static Set<EntryPoint> resolve(AnalysisContext ctx)`。

- [ ] **Step 1: 写 EntryPoint**

```java
// EntryPoint.java
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
```

- [ ] **Step 2: 写 EntryPointResolver（Controller/MQ/定时任务 三类，语法匹配）**

```java
// EntryPointResolver.java
package com.datamap.scanner.entry;

import com.datamap.scanner.javac.AnalysisContext;
import com.datamap.scanner.model.EntryInfo;
import com.sun.source.tree.*;
import com.sun.source.util.TreePathScanner;

import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import java.util.*;

public class EntryPointResolver {
    public static Set<EntryPoint> resolve(AnalysisContext ctx) {
        Set<EntryPoint> out = new LinkedHashSet<>();
        for (CompilationUnitTree cu : ctx.units) {
            new TreePathScanner<Void, Void>() {
                @Override public Void visitClass(ClassTree node, Void p) {
                    String classAnno = classAnnotation(node);
                    if (classAnno == null) { scan(node.getMembers(), p); return null; }
                    boolean isController = classAnno.equals("RestController") || classAnno.equals("Controller");
                    String basePath = pathValue(node, "RequestMapping");
                    for (Tree member : node.getMembers()) {
                        if (!(member instanceof MethodTree)) continue;
                        MethodTree mt = (MethodTree) member;
                        String methodAnno = methodAnnotation(mt);
                        if (methodAnno == null) continue;
                        ExecutableElement m = methodElement(ctx, cu, mt);
                        if (m == null) continue;
                        if (isController && isHttpMapping(methodAnno)) {
                            String path = basePath + pathValue(mt, methodAnno);
                            String httpMethod = methodAnno.replace("Mapping", "").toUpperCase();
                            out.add(new EntryPoint(m, new EntryInfo("CONTROLLER",
                                className(m), httpMethod, path, null, null)));
                        } else if (isMq(methodAnno)) {
                            out.add(new EntryPoint(m, new EntryInfo("MQ", className(m), null, null, null, null)));
                        } else if (methodAnno.equals("Scheduled")) {
                            out.add(new EntryPoint(m, new EntryInfo("SCHEDULED", className(m), null, null, null, null)));
                        }
                    }
                    return null;
                }
            }.scan(cu, null);
        }
        return out;
    }

    private static boolean isHttpMapping(String anno) {
        return anno.equals("RequestMapping") || anno.equals("GetMapping") || anno.equals("PostMapping")
            || anno.equals("PutMapping") || anno.equals("DeleteMapping");
    }
    private static boolean isMq(String anno) {
        return anno.equals("RabbitListener") || anno.equals("KafkaListener")
            || anno.equals("RocketMQMessageListener") || anno.equals("JmsListener");
    }

    private static String classAnnotation(ClassTree node) {
        if (node.getModifiers() == null || node.getModifiers().getAnnotations() == null) return null;
        for (AnnotationTree a : node.getModifiers().getAnnotations()) return simple(a.getAnnotationType().toString());
        return null;
    }
    private static String methodAnnotation(MethodTree node) {
        if (node.getModifiers() == null || node.getModifiers().getAnnotations() == null) return null;
        for (AnnotationTree a : node.getModifiers().getAnnotations()) return simple(a.getAnnotationType().toString());
        return null;
    }
    private static String pathValue(Tree node, String annoName) {
        List<? extends AnnotationTree> anns;
        if (node instanceof ClassTree) anns = ((ClassTree) node).getModifiers().getAnnotations();
        else anns = ((MethodTree) node).getModifiers().getAnnotations();
        for (AnnotationTree a : anns) {
            if (!simple(a.getAnnotationType().toString()).equals(annoName)) continue;
            for (ExpressionTree arg : a.getArguments()) {
                if (arg instanceof LiteralTree) return String.valueOf(((LiteralTree) arg).getValue());
            }
        }
        return "";
    }
    private static String simple(String q) { int i = q.lastIndexOf('.'); return i >= 0 ? q.substring(i + 1) : q; }

    private static ExecutableElement methodElement(AnalysisContext ctx, CompilationUnitTree cu, MethodTree mt) {
        Element e = ctx.resolve(cu, mt);
        return e instanceof ExecutableElement ? (ExecutableElement) e : null;
    }
    private static String className(ExecutableElement m) {
        Element enc = m.getEnclosingElement();
        return enc.toString(); // 全限定类名
    }
}
```

- [ ] **Step 3: 写测试**

```java
// EntryPointResolverTest.java
package com.datamap.scanner.entry;

import com.datamap.scanner.javac.AnalysisContext;
import com.datamap.scanner.javac.JavaParser;
import org.junit.jupiter.api.Test;
import java.nio.file.*;
import java.util.*;
import java.util.stream.*;
import static org.junit.jupiter.api.Assertions.*;

public class EntryPointResolverTest {
    private Set<EntryPoint> resolve() throws Exception {
        Path root = Paths.get("src/test/resources/fixtures/demo");
        List<Path> files;
        try (Stream<Path> s = Files.walk(root)) {
            files = s.filter(p -> p.toString().endsWith(".java")).collect(Collectors.toList());
        }
        return EntryPointResolver.resolve(JavaParser.parse(files, ""));
    }

    @Test
    public void findsControllerEntryWithPath() throws Exception {
        Set<EntryPoint> entries = resolve();
        assertEquals(1, entries.size());
        EntryPoint ep = entries.iterator().next();
        assertEquals("CONTROLLER", ep.info.type);
        assertEquals("/api/order/cancel", ep.info.path);
        assertEquals("POST", ep.info.httpMethod);
    }
}
```

- [ ] **Step 4: 运行测试验证通过**

Run: `cd /Applications/project/data-map/data-map-scanner && mvn -q test`
Expected: `Tests run: 8, Failures: 0`。

- [ ] **Step 5: Commit**

```bash
cd /Applications/project/data-map && git add data-map-scanner
git commit -m "feat(scanner): 入口识别(Controller/MQ/定时任务)

Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

### Task 8: 链路遍历

**Files:**
- Create: `data-map-scanner/src/main/java/com/datamap/scanner/traverse/ChainTraverser.java`
- Test: `data-map-scanner/src/test/java/com/datamap/scanner/traverse/ChainTraverserTest.java`

**Interfaces:**
- Consumes: `CallGraph`（Task 6）、`EntryPoint`（Task 7）。
- Produces:
  - `class ChainTraverser` — `public static List<List<ExecutableElement>> traverse(ExecutableElement start, CallGraph graph, Set<ExecutableElement> entries, int maxDepth)`，返回入口→start 的路径列表（每路径首个元素是入口）。

- [ ] **Step 1: 写 ChainTraverser（向上 BFS，回溯路径）**

```java
// ChainTraverser.java
package com.datamap.scanner.traverse;

import com.datamap.scanner.callgraph.CallGraph;
import javax.lang.model.element.ExecutableElement;
import java.util.*;

public class ChainTraverser {
    /** 从 start 向上找所有到达入口的路径。每个路径[0] 是入口，[last] 是 start。 */
    public static List<List<ExecutableElement>> traverse(ExecutableElement start, CallGraph graph,
                                                         Set<ExecutableElement> entries, int maxDepth) {
        List<List<ExecutableElement>> paths = new ArrayList<>();
        Deque<ExecutableElement> stack = new ArrayDeque<>();
        dfs(start, graph, entries, maxDepth, stack, paths, new HashSet<>());
        return paths;
    }

    private static void dfs(ExecutableElement current, CallGraph graph, Set<ExecutableElement> entries,
                            int maxDepth, Deque<ExecutableElement> stack,
                            List<List<ExecutableElement>> paths, Set<ExecutableElement> visiting) {
        stack.push(current);
        if (entries.contains(current)) {
            List<ExecutableElement> path = new ArrayList<>(stack);
            Collections.reverse(path); // 入口在前
            paths.add(path);
        } else if (stack.size() < maxDepth && visiting.add(current)) {
            for (ExecutableElement caller : graph.callers(current)) {
                dfs(caller, graph, entries, maxDepth, stack, paths, visiting);
            }
            visiting.remove(current);
        }
        stack.pop();
    }
}
```

- [ ] **Step 2: 写测试（fixture 中 cancelOrder 的链路应为 Controller.cancel → OrderServiceImpl.cancelOrder）**

```java
// ChainTraverserTest.java
package com.datamap.scanner.traverse;

import com.datamap.scanner.callgraph.CallGraph;
import com.datamap.scanner.callgraph.CallGraphBuilder;
import com.datamap.scanner.entry.EntryPoint;
import com.datamap.scanner.entry.EntryPointResolver;
import com.datamap.scanner.javac.AnalysisContext;
import com.datamap.scanner.javac.JavaParser;
import org.junit.jupiter.api.Test;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import java.nio.file.*;
import java.util.*;
import java.util.stream.*;

import static org.junit.jupiter.api.Assertions.*;

public class ChainTraverserTest {
    private AnalysisContext ctx() throws Exception {
        Path root = Paths.get("src/test/resources/fixtures/demo");
        List<Path> files;
        try (Stream<Path> s = Files.walk(root)) {
            files = s.filter(p -> p.toString().endsWith(".java")).collect(Collectors.toList());
        }
        return JavaParser.parse(files, "");
    }

    private ExecutableElement method(AnalysisContext ctx, String className, String methodName) {
        TypeElement te = ctx.elements.getTypeElement(className);
        for (javax.lang.model.element.Element e : ctx.elements.getAllMembers(te)) {
            if (e instanceof ExecutableElement && e.getSimpleName().contentEquals(methodName))
                return (ExecutableElement) e;
        }
        return null;
    }

    @Test
    public void tracesCancelOrderUpToController() throws Exception {
        AnalysisContext ctx = ctx();
        CallGraph g = CallGraphBuilder.build(ctx);
        Set<ExecutableElement> entries = EntryPointResolver.resolve(ctx).stream()
            .map(ep -> ep.method).collect(Collectors.toSet());
        ExecutableElement cancel = method(ctx, "com.example.service.impl.OrderServiceImpl", "cancelOrder");
        List<List<ExecutableElement>> paths = ChainTraverser.traverse(cancel, g, entries, 10);
        assertFalse(paths.isEmpty());
        List<ExecutableElement> first = paths.get(0);
        assertTrue(first.get(0).getEnclosingElement().toString().contains("OrderController"));
        assertTrue(first.get(first.size() - 1).getSimpleName().contentEquals("cancelOrder"));
    }
}
```

- [ ] **Step 3: 运行测试验证通过**

Run: `cd /Applications/project/data-map/data-map-scanner && mvn -q test`
Expected: `Tests run: 9, Failures: 0`。

- [ ] **Step 4: Commit**

```bash
cd /Applications/project/data-map && git add data-map-scanner
git commit -m "feat(scanner): 调用链向上遍历

Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

### Task 9: 操作类型判定 + 方法含义提取

**Files:**
- Create: `data-map-scanner/src/main/java/com/datamap/scanner/usage/OperationTypeClassifier.java`
- Create: `data-map-scanner/src/main/java/com/datamap/scanner/usage/MethodDescriptionExtractor.java`
- Test: `data-map-scanner/src/test/java/com/datamap/scanner/usage/OperationTypeClassifierTest.java`

**Interfaces:**
- Consumes: `AnalysisContext`（Task 2）、`CallGraph`（Task 6）、`FieldAccess`（Task 5）。
- Produces:
  - `class OperationTypeClassifier` — `public static String classify(FieldAccess access, CallGraph graph, AnalysisContext ctx)`，返回 `WRITE`/`UPDATE`/`READ`/`DELETE` 之一。
  - `class MethodDescriptionExtractor` — `public static String[] extract(ExecutableElement method, AnalysisContext ctx)`，返回 `[description, source]`，source ∈ `COMMENT`/`NONE`。

- [ ] **Step 1: 写 OperationTypeClassifier（DML 名称匹配 + receiver 兜底）**

```java
// OperationTypeClassifier.java
package com.datamap.scanner.usage;

import com.datamap.scanner.callgraph.CallGraph;
import com.datamap.scanner.javac.AnalysisContext;
import com.sun.source.tree.*;
import com.sun.source.util.TreeScanner;

import javax.lang.model.element.ExecutableElement;
import java.util.*;

public class OperationTypeClassifier {
    public static String classify(FieldAccess access, CallGraph graph, AnalysisContext ctx) {
        if (access.kind == FieldAccess.Kind.READ) return "READ";
        // 从 access.method 沿调用图向下(callees) BFS，收集 mapper 方法名
        Set<String> names = new HashSet<>();
        Deque<ExecutableElement> queue = new ArrayDeque<>();
        Set<ExecutableElement> seen = new HashSet<>();
        queue.add(access.method);
        int depth = 0;
        while (!queue.isEmpty() && depth++ <= 5) {
            int size = queue.size();
            for (int i = 0; i < size; i++) {
                ExecutableElement m = queue.poll();
                if (!seen.add(m)) continue;
                names.addAll(mapperCallsIn(m, ctx));
                for (ExecutableElement callee : graph.callees(m)) queue.add(callee);
            }
        }
        if (names.contains("insert") || names.contains("save")) return "WRITE";
        if (names.contains("updateById") || names.contains("update")) return "UPDATE";
        if (names.contains("deleteById") || names.contains("delete")
                || names.contains("removeById") || names.contains("remove")) return "DELETE";
        return "UNRESOLVED";
    }

    private static final Set<String> MAPPER_METHODS = Set.of(
        "insert","save","updateById","update","updateBatchById","deleteById","delete",
        "removeById","remove","selectById","selectOne","selectList","getById");

    /** 扫描单个方法方法体的 mapper 方法名。 */
    private static Set<String> mapperCallsIn(ExecutableElement method, AnalysisContext ctx) {
        Set<String> names = new HashSet<>();
        Tree tree = ctx.trees.getTree(method);
        if (!(tree instanceof MethodTree)) return names;
        BlockTree body = ((MethodTree) tree).getBody();
        if (body == null) return names;
        body.accept(new TreeScanner<Void, Void>() {
            @Override public Void visitMethodInvocation(MethodInvocationTree node, Void p) {
                String name = node.getMethodSelect().toString();
                int i = name.lastIndexOf('.');
                String simple = i >= 0 ? name.substring(i + 1) : name;
                if (MAPPER_METHODS.contains(simple)) names.add(simple);
                return super.visitMethodInvocation(node, p);
            }
        }, null);
        return names;
    }
}
```

- [ ] **Step 2: 写 MethodDescriptionExtractor（javadoc 提取）**

```java
// MethodDescriptionExtractor.java
package com.datamap.scanner.usage;

import com.datamap.scanner.javac.AnalysisContext;
import com.sun.source.doctree.DocCommentTree;
import com.sun.source.util.DocTrees;

import javax.lang.model.element.ExecutableElement;

public class MethodDescriptionExtractor {
    public static String[] extract(ExecutableElement method, AnalysisContext ctx) {
        DocTrees docTrees = DocTrees.instance(ctx.trees);
        DocCommentTree doc = docTrees.getDocCommentTree(method);
        if (doc != null && !doc.getFullBody().isEmpty()) {
            return new String[]{ doc.getFullBody().toString().trim(), "COMMENT" };
        }
        return new String[]{ "", "NONE" };
    }
}
```

- [ ] **Step 3: 写测试**

```java
// OperationTypeClassifierTest.java
package com.datamap.scanner.usage;

import com.datamap.scanner.callgraph.CallGraph;
import com.datamap.scanner.callgraph.CallGraphBuilder;
import com.datamap.scanner.entity.EntityResolver;
import com.datamap.scanner.javac.AnalysisContext;
import com.datamap.scanner.javac.JavaParser;
import org.junit.jupiter.api.Test;
import javax.lang.model.element.TypeElement;
import java.nio.file.*;
import java.util.*;
import java.util.stream.*;
import static org.junit.jupiter.api.Assertions.*;

public class OperationTypeClassifierTest {
    private AnalysisContext ctx() throws Exception {
        Path root = Paths.get("src/test/resources/fixtures/demo");
        List<Path> files;
        try (Stream<Path> s = Files.walk(root)) {
            files = s.filter(p -> p.toString().endsWith(".java")).collect(Collectors.toList());
        }
        return JavaParser.parse(files, "");
    }

    @Test
    public void createOrderStatusIsWrite() throws Exception {
        AnalysisContext ctx = ctx();
        CallGraph g = CallGraphBuilder.build(ctx);
        Map<String, TypeElement> entities = EntityResolver.resolve(ctx);
        Map<String, List<FieldAccess>> all = FieldAccessCollector.collect(ctx, entities);
        List<FieldAccess> accesses = all.get("tb_order.order_status");
        FieldAccess create = accesses.stream()
            .filter(a -> a.method.getSimpleName().contentEquals("createOrder")).findFirst().get();
        assertEquals("WRITE", OperationTypeClassifier.classify(create, g, ctx));
    }

    @Test
    public void cancelOrderStatusIsUpdate() throws Exception {
        AnalysisContext ctx = ctx();
        CallGraph g = CallGraphBuilder.build(ctx);
        Map<String, TypeElement> entities = EntityResolver.resolve(ctx);
        Map<String, List<FieldAccess>> all = FieldAccessCollector.collect(ctx, entities);
        List<FieldAccess> accesses = all.get("tb_order.order_status");
        FieldAccess cancel = accesses.stream()
            .filter(a -> a.method.getSimpleName().contentEquals("cancelOrder")).findFirst().get();
        assertEquals("UPDATE", OperationTypeClassifier.classify(cancel, g, ctx));
    }
}
```

- [ ] **Step 4: 运行测试验证通过**

Run: `cd /Applications/project/data-map/data-map-scanner && mvn -q test`
Expected: `Tests run: 11, Failures: 0`。

- [ ] **Step 5: Commit**

```bash
cd /Applications/project/data-map && git add data-map-scanner
git commit -m "feat(scanner): 操作类型判定与方法含义提取

Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

### Task 10: 结果组装 + JSON 输出 + Main 编排（FULL/TABLE/DIFF）

**Files:**
- Create: `data-map-scanner/src/main/java/com/datamap/scanner/output/ScanResultAssembler.java`
- Create: `data-map-scanner/src/main/java/com/datamap/scanner/output/JsonWriter.java`
- Create: `data-map-scanner/src/main/java/com/datamap/scanner/input/SourceCollector.java`
- Modify: `data-map-scanner/src/main/java/com/datamap/scanner/Main.java`
- Test: `data-map-scanner/src/test/java/com/datamap/scanner/output/JsonWriterTest.java`

**Interfaces:**
- Consumes: Task 3/4/5/6/7/8/9 的全部产物。
- Produces:
  - `class ScanResultAssembler` — `public static ScanResult assemble(AnalysisContext ctx, String appName, String scanType, String tableFilter)`，`tableFilter` 为 null 表示全量。
  - `class JsonWriter` — `public static void write(ScanResult result, Path outFile)`；`public static String toJson(ScanResult result)`。
  - `class SourceCollector` — `public static List<Path> javaFiles(Path root)`、`public static List<Path> xmlFiles(Path root)`。

- [ ] **Step 1: 写 JsonWriter**

```java
// JsonWriter.java
package com.datamap.scanner.output;

import com.datamap.scanner.model.ScanResult;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public class JsonWriter {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    public static String toJson(ScanResult result) throws Exception {
        return MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(result);
    }

    public static void write(ScanResult result, Path outFile) throws Exception {
        Files.write(outFile, toJson(result).getBytes(StandardCharsets.UTF_8));
    }
}
```

- [ ] **Step 2: 写 SourceCollector**

```java
// SourceCollector.java
package com.datamap.scanner.input;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

public class SourceCollector {
    public static List<Path> javaFiles(Path root) throws Exception {
        return collect(root, ".java");
    }
    public static List<Path> xmlFiles(Path root) throws Exception {
        return collect(root, ".xml");
    }
    private static List<Path> collect(Path root, String suffix) throws Exception {
        List<Path> out = new ArrayList<>();
        try (Stream<Path> s = Files.walk(root)) {
            s.filter(p -> p.toString().endsWith(suffix)
                    && !p.toString().contains("/target/"))
             .forEach(out::add);
        }
        return out;
    }
}
```

- [ ] **Step 3: 写 ScanResultAssembler（串联所有提取器，支持 tableFilter）**

```java
// ScanResultAssembler.java
package com.datamap.scanner.output;

import com.datamap.scanner.callgraph.CallGraph;
import com.datamap.scanner.callgraph.CallGraphBuilder;
import com.datamap.scanner.entry.EntryPoint;
import com.datamap.scanner.entry.EntryPointResolver;
import com.datamap.scanner.entity.EntityResolver;
import com.datamap.scanner.entity.FieldExtractor;
import com.datamap.scanner.javac.AnalysisContext;
import com.datamap.scanner.model.*;
import com.datamap.scanner.relation.MybatisXmlRelationExtractor;
import com.datamap.scanner.traverse.ChainTraverser;
import com.datamap.scanner.usage.FieldAccess;
import com.datamap.scanner.usage.FieldAccessCollector;
import com.datamap.scanner.usage.MethodDescriptionExtractor;
import com.datamap.scanner.usage.OperationTypeClassifier;

import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

public class ScanResultAssembler {
    public static ScanResult assemble(AnalysisContext ctx, List<Path> xmlFiles,
                                      String appName, String scanType, String tableFilter) throws Exception {
        Map<String, TypeElement> entities = EntityResolver.resolve(ctx);
        CallGraph graph = CallGraphBuilder.build(ctx);
        Set<EntryPoint> entries = EntryPointResolver.resolve(ctx);
        Set<ExecutableElement> entryMethods = entries.stream().map(ep -> ep.method).collect(Collectors.toSet());
        Map<String, List<FieldAccess>> accesses = FieldAccessCollector.collect(ctx, entities);

        List<ScanTable> tables = new ArrayList<>();
        for (Map.Entry<String, TypeElement> en : entities.entrySet()) {
            String tableName = en.getKey();
            if (tableFilter != null && !tableName.equals(tableFilter)) continue;
            List<ScanField> fields = FieldExtractor.extract(en.getValue(), ctx);
            List<ScanRelation> relations = new ArrayList<>();
            for (Path xml : xmlFiles) {
                relations.addAll(MybatisXmlRelationExtractor.extract(xml));
            }
            List<UsageScenario> scenarios = new ArrayList<>();
            for (ScanField f : fields) {
                for (FieldAccess a : accesses.getOrDefault(tableName + "." + f.fieldName, List.of())) {
                    String op = OperationTypeClassifier.classify(a, graph, ctx);
                    String[] desc = MethodDescriptionExtractor.extract(a.method, ctx);
                    List<List<ExecutableElement>> paths = ChainTraverser.traverse(a.method, graph, entryMethods, 10);
                    List<CallChainStep> chain = paths.isEmpty() ? List.of() : steps(paths.get(0));
                    EntryInfo entry = paths.isEmpty() ? null : entryOf(paths.get(0).get(0), entries);
                    scenarios.add(new UsageScenario(f.fieldName, op, qualified(a.method), desc[0], desc[1],
                        "", entry == null ? "" : entry.apiName, chain, entry));
                }
            }
            tables.add(new ScanTable(tableName, "", "", "MYSQL", fields, relations, scenarios));
        }
        ScanProject project = new ScanProject(appName, "", "");
        return new ScanResult(project, scanType, tables);
    }

    private static List<CallChainStep> steps(List<ExecutableElement> path) {
        List<CallChainStep> out = new ArrayList<>();
        for (ExecutableElement m : path) {
            out.add(new CallChainStep(m.getEnclosingElement().toString(),
                m.getSimpleName().toString(), m.toString(), "SERVICE"));
        }
        return out;
    }

    private static EntryInfo entryOf(ExecutableElement m, Set<EntryPoint> entries) {
        for (EntryPoint ep : entries) if (ep.method.equals(m)) return ep.info;
        return null;
    }

    private static String qualified(ExecutableElement m) {
        return m.getEnclosingElement().toString() + "." + m.getSimpleName();
    }
}
```

- [ ] **Step 4: 改 Main 编排（参数：--path / --repo --ref / --diff / --table / -o）**

```java
// Main.java
package com.datamap.scanner;

import com.datamap.scanner.input.SourceCollector;
import com.datamap.scanner.javac.AnalysisContext;
import com.datamap.scanner.javac.JavaParser;
import com.datamap.scanner.model.ScanResult;
import com.datamap.scanner.output.JsonWriter;
import com.datamap.scanner.output.ScanResultAssembler;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Main {
    public static void main(String[] args) throws Exception {
        Map<String, String> opts = parse(args);
        String path = opts.getOrDefault("path", ".");
        String table = opts.get("table");
        String scanType = table != null ? "TABLE" : (opts.containsKey("diff") ? "DIFF" : "FULL");
        Path root = Paths.get(path);

        AnalysisContext ctx = JavaParser.parse(SourceCollector.javaFiles(root), "");
        ScanResult result = ScanResultAssembler.assemble(ctx, SourceCollector.xmlFiles(root),
            "demo", scanType, table);

        String out = opts.getOrDefault("o", "scan-result.json");
        JsonWriter.write(result, Paths.get(out));
        System.out.println("扫描完成: " + out + " (" + result.tables.size() + " 张表)");
    }

    private static Map<String, String> parse(String[] args) {
        Map<String, String> opts = new HashMap<>();
        for (int i = 0; i < args.length; i++) {
            String a = args[i];
            if (a.startsWith("--") && i + 1 < args.length && !args[i + 1].startsWith("--")) {
                opts.put(a.substring(2), args[++i]);
            } else if (a.startsWith("--")) {
                opts.put(a.substring(2), "true");
            }
        }
        return opts;
    }
}
```

- [ ] **Step 5: 写 JsonWriter 测试 + 端到端跑一次**

```java
// JsonWriterTest.java
package com.datamap.scanner.output;

import com.datamap.scanner.model.*;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

public class JsonWriterTest {
    @Test
    public void serializesPublicFields() throws Exception {
        ScanResult r = new ScanResult(new ScanProject("demo", "", ""), "FULL",
            List.of(new ScanTable("tb_order", "", "", "MYSQL", List.of(), List.of(), List.of())));
        String json = JsonWriter.toJson(r);
        assertTrue(json.contains("\"appName\""));
        assertTrue(json.contains("\"tableName\""));
    }
}
```

```bash
cd /Applications/project/data-map/data-map-scanner && mvn -q test
cd /Applications/project/data-map/data-map-scanner && mvn -q package -DskipTests
java -jar target/data-map-scanner-1.0.0.jar --path src/test/resources/fixtures/demo -o /tmp/scan.json
cat /tmp/scan.json
```

Expected: `Tests run: 12, Failures: 0`；`/tmp/scan.json` 含 `tb_order`、`order_status`、`usageScenarios`、`callChain`。

- [ ] **Step 6: Commit**

```bash
cd /Applications/project/data-map && git add data-map-scanner
git commit -m "feat(scanner): 结果组装、JSON 输出与 Main 编排

Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

### Task 11: 落库（POST 后端 + JDBC 直连）

**Files:**
- Create: `data-map-scanner/src/main/java/com/datamap/scanner/persist/ApiSubmitter.java`
- Create: `data-map-scanner/src/main/java/com/datamap/scanner/persist/JdbcWriter.java`
- Modify: `data-map-scanner/src/main/java/com/datamap/scanner/Main.java`
- Test: `data-map-scanner/src/test/java/com/datamap/scanner/persist/ApiSubmitterTest.java`

**Interfaces:**
- Consumes: `ScanResult`（Task 1）、`JsonWriter`（Task 10）。
- Produces:
  - `class ApiSubmitter` — `public static int submit(ScanResult result, String apiUrl)`，返回 HTTP 状态码。
  - `class JdbcWriter` — `public static void write(ScanResult result, String jdbcUrl, String user, String pass)`。

- [ ] **Step 1: 写 ApiSubmitter（java.net.http POST JSON）**

```java
// ApiSubmitter.java
package com.datamap.scanner.persist;

import com.datamap.scanner.model.ScanResult;
import com.datamap.scanner.output.JsonWriter;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

public class ApiSubmitter {
    public static int submit(ScanResult result, String apiUrl) throws Exception {
        String json = JsonWriter.toJson(result);
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest req = HttpRequest.newBuilder(URI.create(apiUrl))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(json))
            .build();
        HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
        return resp.statusCode();
    }
}
```

- [ ] **Step 2: 写 JdbcWriter（直连写 usage_scenario 相关表，初版输出 SQL 并执行）**

```java
// JdbcWriter.java
package com.datamap.scanner.persist;

import com.datamap.scanner.model.ScanResult;
import com.datamap.scanner.model.ScanTable;
import com.datamap.scanner.model.UsageScenario;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;

public class JdbcWriter {
    public static void write(ScanResult result, String jdbcUrl, String user, String pass) throws Exception {
        try (Connection conn = DriverManager.getConnection(jdbcUrl, user, pass)) {
            for (ScanTable table : result.tables) {
                for (UsageScenario s : table.usageScenarios) {
                    try (PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO field_usage_scenario " +
                        "(field_id, table_id, operation_type, scenario_description, method_name, " +
                        " source_table_name, source_api_name, call_chain, entry_info, method_description, description_source) " +
                        "VALUES (NULL, NULL, ?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
                        ps.setString(1, s.operationType);
                        ps.setString(2, s.methodDescription);
                        ps.setString(3, s.methodName);
                        ps.setString(4, s.sourceTableName);
                        ps.setString(5, s.sourceApiName);
                        ps.setString(6, s.callChain == null ? "[]" : s.callChain.toString());
                        ps.setString(7, s.entry == null ? "{}" : s.entry.toString());
                        ps.setString(8, s.methodDescription);
                        ps.setString(9, s.descriptionSource);
                        ps.executeUpdate();
                    }
                }
            }
        }
    }
}
```

- [ ] **Step 3: Main 增加 --submit / --db 分支**

在 `Main.main` 输出 JSON 之后追加：

```java
        if (opts.containsKey("submit")) {
            int code = com.datamap.scanner.persist.ApiSubmitter.submit(result, opts.get("submit"));
            System.out.println("POST " + opts.get("submit") + " -> " + code);
        }
        if (opts.containsKey("db")) {
            String[] parts = opts.get("db").split(";");
            com.datamap.scanner.persist.JdbcWriter.write(result, parts[0], parts[1], parts[2]);
            System.out.println("JDBC 直连写入完成");
        }
```

- [ ] **Step 4: 写 ApiSubmitter 测试（用 com.sun.net.httpserver 内嵌 HTTP 断言收到 JSON）**

```java
// ApiSubmitterTest.java
package com.datamap.scanner.persist;

import com.datamap.scanner.model.*;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import java.net.InetSocketAddress;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;

public class ApiSubmitterTest {
    @Test
    public void postsJsonToServer() throws Exception {
        AtomicReference<String> body = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/api/scan/result", ex -> {
            body.set(new String(ex.getRequestBody().readAllBytes()));
            ex.sendResponseHeaders(200, -1); ex.close();
        });
        server.start();
        int port = server.getAddress().getPort();
        ScanResult r = new ScanResult(new ScanProject("demo", "", ""), "FULL", List.of());
        int code = ApiSubmitter.submit(r, "http://localhost:" + port + "/api/scan/result");
        server.stop(0);
        assertEquals(200, code);
        assertTrue(body.get().contains("\"appName\""));
    }
}
```

- [ ] **Step 5: 运行测试验证通过**

Run: `cd /Applications/project/data-map/data-map-scanner && mvn -q test`
Expected: `Tests run: 13, Failures: 0`。

- [ ] **Step 6: Commit**

```bash
cd /Applications/project/data-map && git add data-map-scanner
git commit -m "feat(scanner): 落库(POST 后端 + JDBC 直连)

Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

### Task 12: 后端数据模型变更

**Files:**
- Create: `data-map-backend/src/main/resources/db/migration/V2__scanner_call_chain.sql`
- Modify: `data-map-backend/src/main/java/com/datamap/entity/FieldUsageScenario.java`
- Modify: `data-map-backend/src/main/java/com/datamap/dto/ScanResultDTO.java`
- Modify: `data-map-backend/src/main/java/com/datamap/service/ScanService.java`

**Interfaces:**
- Consumes: 现有后端代码。
- Produces: `field_usage_scenario` 支持新列与 `READ`/`DIFF` 取值；`UsageScenarioDTO` 支持 `callChain`/`entry`/`methodDescription`/`descriptionSource`。

- [ ] **Step 1: 写迁移 SQL**

```sql
ALTER TABLE field_usage_scenario
  ADD COLUMN call_chain TEXT NULL COMMENT '完整调用链 JSON 数组' AFTER source_api_name,
  ADD COLUMN entry_info TEXT NULL COMMENT '入口信息 JSON 对象' AFTER call_chain,
  ADD COLUMN method_description VARCHAR(512) NULL COMMENT '方法含义' AFTER entry_info,
  ADD COLUMN description_source VARCHAR(16) NULL COMMENT 'COMMENT/AI/NONE' AFTER method_description;
```

- [ ] **Step 2: 改 FieldUsageScenario 实体（加 4 字段 + getter/setter）**

在 `sourceApiName` 字段后追加：

```java
    private String callChain;
    private String entryInfo;
    private String methodDescription;
    private String descriptionSource;

    public String getCallChain() { return callChain; }
    public void setCallChain(String callChain) { this.callChain = callChain; }
    public String getEntryInfo() { return entryInfo; }
    public void setEntryInfo(String entryInfo) { this.entryInfo = entryInfo; }
    public String getMethodDescription() { return methodDescription; }
    public void setMethodDescription(String methodDescription) { this.methodDescription = methodDescription; }
    public String getDescriptionSource() { return descriptionSource; }
    public void setDescriptionSource(String descriptionSource) { this.descriptionSource = descriptionSource; }
```

- [ ] **Step 3: 改 ScanResultDTO.UsageScenarioDTO（加字段）**

在 `sourceApiName` 字段后追加：

```java
        private String methodDescription;
        private String descriptionSource;
        private List<CallChainStepDTO> callChain;
        private EntryInfoDTO entry;

        public static class CallChainStepDTO {
            private String className;
            private String methodName;
            private String signature;
            private String layer;
            public String getClassName() { return className; }
            public void setClassName(String v) { this.className = v; }
            public String getMethodName() { return methodName; }
            public void setMethodName(String v) { this.methodName = v; }
            public String getSignature() { return signature; }
            public void setSignature(String v) { this.signature = v; }
            public String getLayer() { return layer; }
            public void setLayer(String v) { this.layer = v; }
        }
        public static class EntryInfoDTO {
            private String type;
            private String apiName;
            private String httpMethod;
            private String path;
            private String queue;
            private String cron;
            public String getType() { return type; }
            public void setType(String v) { this.type = v; }
            public String getApiName() { return apiName; }
            public void setApiName(String v) { this.apiName = v; }
            public String getHttpMethod() { return httpMethod; }
            public void setHttpMethod(String v) { this.httpMethod = v; }
            public String getPath() { return path; }
            public void setPath(String v) { this.path = v; }
            public String getQueue() { return queue; }
            public void setQueue(String v) { this.queue = v; }
            public String getCron() { return cron; }
            public void setCron(String v) { this.cron = v; }
        }
        public String getMethodDescription() { return methodDescription; }
        public void setMethodDescription(String v) { this.methodDescription = v; }
        public String getDescriptionSource() { return descriptionSource; }
        public void setDescriptionSource(String v) { this.descriptionSource = v; }
        public List<CallChainStepDTO> getCallChain() { return callChain; }
        public void setCallChain(List<CallChainStepDTO> v) { this.callChain = v; }
        public EntryInfoDTO getEntry() { return entry; }
        public void setEntry(EntryInfoDTO v) { this.entry = v; }
```

- [ ] **Step 4: 改 ScanService.processRelationsAndScenarios 落库逻辑（写入新字段）**

在 `fieldUsageScenarioMapper.insert(scenario)` 之前追加：

```java
                scenario.setMethodDescription(us.getMethodDescription());
                scenario.setDescriptionSource(us.getDescriptionSource());
                scenario.setCallChain(serialize(us.getCallChain()));
                scenario.setEntryInfo(serialize(us.getEntry()));
```

并在 `ScanService` 顶部新增一个私有方法：

```java
    private String serialize(Object o) {
        if (o == null) return null;
        try { return new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(o); }
        catch (Exception e) { return null; }
    }
```

（需在 `pom.xml` 确认已引入 jackson-databind，Spring Boot 默认已含。）

- [ ] **Step 5: 编译后端验证**

Run: `cd /Applications/project/data-map/data-map-backend && mvn -q compile`
Expected: BUILD SUCCESS。

- [ ] **Step 6: Commit**

```bash
cd /Applications/project/data-map && git add data-map-backend
git commit -m "feat(backend): 字段使用场景支持完整链路/入口/描述来源

Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

### Task 13: git 输入 + 差异扫描

**Files:**
- Create: `data-map-scanner/src/main/java/com/datamap/scanner/input/GitSource.java`
- Create: `data-map-scanner/src/main/java/com/datamap/scanner/input/DiffFilter.java`
- Modify: `data-map-scanner/src/main/java/com/datamap/scanner/output/ScanResultAssembler.java`
- Modify: `data-map-scanner/src/main/java/com/datamap/scanner/Main.java`
- Test: `data-map-scanner/src/test/java/com/datamap/scanner/input/GitSourceTest.java`

**Interfaces:**
- Consumes: `ScanResultAssembler`（Task 10）。
- Produces:
  - `class GitSource` — `public static void checkout(String repoUrl, String ref, Path dir)`；`public static List<String> diffFiles(Path dir, String base, String head)`。
  - `class DiffFilter` — `public static boolean affected(ExecutableElement method, List<List<ExecutableElement>> paths, Set<String> changedFiles, AnalysisContext ctx)`。

- [ ] **Step 1: 写 GitSource（git CLI 封装）**

```java
// GitSource.java
package com.datamap.scanner.input;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class GitSource {
    public static void checkout(String repoUrl, String ref, Path dir) throws Exception {
        run(dir.getParent(), "git", "clone", repoUrl, dir.toString());
        run(dir, "git", "checkout", ref);
    }

    /** base...head 之间变更的文件相对路径。 */
    public static List<String> diffFiles(Path dir, String base, String head) throws Exception {
        return run(dir, "git", "diff", "--name-only", base + "..." + head);
    }

    static List<String> run(Path workDir, String... cmd) throws Exception {
        ProcessBuilder pb = new ProcessBuilder(cmd);
        if (workDir != null) pb.directory(workDir.toFile());
        pb.redirectErrorStream(true);
        Process p = pb.start();
        String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        int code = p.waitFor();
        if (code != 0) throw new RuntimeException("git 命令失败(" + code + "): " + String.join(" ", cmd) + "\n" + out);
        List<String> lines = new ArrayList<>();
        for (String line : out.split("\n")) if (!line.trim().isEmpty()) lines.add(line.trim());
        return lines;
    }
}
```

- [ ] **Step 2: 写 DiffFilter（方法 → 源文件 → 是否在变更集）**

```java
// DiffFilter.java
package com.datamap.scanner.input;

import com.datamap.scanner.javac.AnalysisContext;
import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.util.TreePath;

import javax.lang.model.element.ExecutableElement;
import java.util.List;
import java.util.Set;

public class DiffFilter {
    /** 直接方法或链上任一方法定义在变更文件里 → 需要重算输出。changedFiles==null 表示全量。 */
    public static boolean affected(ExecutableElement method, List<List<ExecutableElement>> paths,
                                   Set<String> changedFiles, AnalysisContext ctx) {
        if (changedFiles == null) return true;
        if (isChanged(method, changedFiles, ctx)) return true;
        for (List<ExecutableElement> path : paths) {
            for (ExecutableElement m : path) {
                if (isChanged(m, changedFiles, ctx)) return true;
            }
        }
        return false;
    }

    private static boolean isChanged(ExecutableElement method, Set<String> changedFiles, AnalysisContext ctx) {
        TreePath path = ctx.trees.getPath(method);
        if (path == null) return false;
        CompilationUnitTree cu = path.getCompilationUnit();
        String name = cu.getSourceFile().getName();
        for (String f : changedFiles) {
            if (name.endsWith(f) || f.endsWith(name)) return true;
        }
        return false;
    }
}
```

- [ ] **Step 3: 改 ScanResultAssembler——签名加 changedFiles，场景循环里过滤**

签名：

```java
    public static ScanResult assemble(AnalysisContext ctx, List<Path> xmlFiles,
                                      String appName, String scanType, String tableFilter,
                                      Set<String> changedFiles) throws Exception {
```

顶部 import 区追加：`import com.datamap.scanner.input.DiffFilter;`

场景循环里，`paths` 计算之后、`scenarios.add(...)` 之前插入：

```java
                    if (!DiffFilter.affected(a.method, paths, changedFiles, ctx)) continue;
```

- [ ] **Step 4: 重写 Main.java 为最终完整版（path/repo/diff/table/submit/db 全支持）**

```java
// Main.java
package com.datamap.scanner;

import com.datamap.scanner.input.GitSource;
import com.datamap.scanner.input.SourceCollector;
import com.datamap.scanner.javac.AnalysisContext;
import com.datamap.scanner.javac.JavaParser;
import com.datamap.scanner.model.ScanResult;
import com.datamap.scanner.output.JsonWriter;
import com.datamap.scanner.output.ScanResultAssembler;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

public class Main {
    public static void main(String[] args) throws Exception {
        Map<String, String> opts = parse(args);
        String table = opts.get("table");
        Path root;
        Set<String> changedFiles = null;
        String scanType;

        if (opts.containsKey("repo")) {
            Path tmp = Files.createTempDirectory("scanner-repo");
            String diff = opts.get("diff");
            if (diff != null) {
                String[] parts = diff.split("\\.\\.\\.");
                GitSource.checkout(opts.get("repo"), parts[1], tmp);
                changedFiles = new HashSet<>(GitSource.diffFiles(tmp, parts[0], parts[1]));
                scanType = "DIFF";
            } else {
                GitSource.checkout(opts.get("repo"), opts.getOrDefault("ref", "HEAD"), tmp);
                scanType = "FULL";
            }
            root = tmp;
        } else {
            root = Paths.get(opts.getOrDefault("path", "."));
            scanType = table != null ? "TABLE" : "FULL";
        }

        AnalysisContext ctx = JavaParser.parse(SourceCollector.javaFiles(root), "");
        ScanResult result = ScanResultAssembler.assemble(ctx, SourceCollector.xmlFiles(root),
            "demo", scanType, table, changedFiles);

        String out = opts.getOrDefault("o", "scan-result.json");
        JsonWriter.write(result, Paths.get(out));
        System.out.println("扫描完成: " + out + " (" + result.tables.size() + " 张表)");

        if (opts.containsKey("submit")) {
            int code = com.datamap.scanner.persist.ApiSubmitter.submit(result, opts.get("submit"));
            System.out.println("POST " + opts.get("submit") + " -> " + code);
        }
        if (opts.containsKey("db")) {
            String[] parts = opts.get("db").split(";");
            com.datamap.scanner.persist.JdbcWriter.write(result, parts[0], parts[1], parts[2]);
            System.out.println("JDBC 直连写入完成");
        }
    }

    private static Map<String, String> parse(String[] args) {
        Map<String, String> opts = new HashMap<>();
        for (int i = 0; i < args.length; i++) {
            String a = args[i];
            if (a.startsWith("--") && i + 1 < args.length && !args[i + 1].startsWith("--")) {
                opts.put(a.substring(2), args[++i]);
            } else if (a.startsWith("--")) {
                opts.put(a.substring(2), "true");
            }
        }
        return opts;
    }
}
```

- [ ] **Step 5: 写 GitSourceTest（临时 git 仓库两提交，断言 diff 结果）**

```java
// GitSourceTest.java
package com.datamap.scanner.input;

import org.junit.jupiter.api.Test;
import java.nio.file.*;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

public class GitSourceTest {
    @Test
    public void diffFilesReturnsChangedFile() throws Exception {
        Path repo = Files.createTempDirectory("repo");
        GitSource.run(repo, "git", "init");
        GitSource.run(repo, "git", "config", "user.email", "t@t.com");
        GitSource.run(repo, "git", "config", "user.name", "t");
        Files.writeString(repo.resolve("A.java"), "class A {}");
        GitSource.run(repo, "git", "add", ".");
        GitSource.run(repo, "git", "commit", "-m", "c1");
        Files.writeString(repo.resolve("A.java"), "class A { int x; }");
        GitSource.run(repo, "git", "commit", "-am", "c2");

        List<String> changed = GitSource.diffFiles(repo, "HEAD~1", "HEAD");
        assertEquals(List.of("A.java"), changed);
    }
}
```

- [ ] **Step 6: 运行测试验证通过**

Run: `cd /Applications/project/data-map/data-map-scanner && mvn -q test`
Expected: `Tests run: 14, Failures: 0`。

- [ ] **Step 7: Commit**

```bash
cd /Applications/project/data-map && git add data-map-scanner
git commit -m "feat(scanner): git 输入与差异扫描

Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

## 后续（不在本计划内）

- **Phase 5（可选）**：描述富化独立 LLM 步骤——对 `descriptionSource=NONE` 的项生成描述并标记 `AI`（独立 Claude skill，非 Java 代码）。
- **JOOQ 关联提取**：`JooqRelationExtractor` 目前返回空，接入 JOOQ 项目时补全 `.join().on()` 提取。
