# 数据地图系统 — 实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 基于设计文档实现数据地图系统：SpringBoot 后端 + React 前端，提供数据血缘路径查询和字段溯源功能。

**Architecture:** SpringBoot 2.x + MyBatis-Plus 后端提供 REST API，React 18 + Ant Design + AntV G6 前端展示，MySQL 存储元数据。分三大模块：项目管理、表信息管理、路径查询。

**Tech Stack:** Java 8, SpringBoot 2.7.x, MyBatis-Plus 3.5.x, MySQL 8.0, React 18, TypeScript, Ant Design 5.x, AntV G6 5.x, Vite 5.x

## Global Constraints

- Java 8 兼容（JDK 1.8）
- MySQL 连接：localhost:3306, root/<用户提供>
- 前端端口 5173，后端端口 8080
- 所有 API 返回统一格式 `{code, message, data}`
- MyBatis-Plus 作为 ORM，逻辑删除使用 is_deleted 字段

---

## Task 1: 数据库初始化

**Files:**
- Create: `data-map-backend/src/main/resources/sql/init.sql`
- Create: `data-map-backend/src/main/resources/application.yml`

- [ ] **Step 1: 创建数据库和表结构 SQL**

创建 `data-map-backend/src/main/resources/sql/init.sql`：

```sql
CREATE DATABASE IF NOT EXISTS data_map DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;

USE data_map;

CREATE TABLE IF NOT EXISTS project (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    app_name VARCHAR(128) NOT NULL,
    git_repo_url VARCHAR(512),
    git_local_path VARCHAR(512),
    description VARCHAR(512),
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_app_name (app_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS table_info (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    project_id BIGINT NOT NULL,
    table_name VARCHAR(128) NOT NULL,
    table_comment VARCHAR(512),
    table_comment_manual TINYINT DEFAULT 0,
    schema_name VARCHAR(64),
    db_type VARCHAR(16),
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_project_table (project_id, table_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS table_field (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    table_id BIGINT NOT NULL,
    field_name VARCHAR(128) NOT NULL,
    field_comment VARCHAR(512),
    field_type VARCHAR(64),
    is_pk TINYINT DEFAULT 0,
    is_business_field TINYINT DEFAULT 1,
    field_comment_manual TINYINT DEFAULT 0,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_table_field (table_id, field_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS table_relation (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    project_id BIGINT NOT NULL,
    source_table_id BIGINT NOT NULL,
    source_field_name VARCHAR(128) NOT NULL,
    target_table_id BIGINT NOT NULL,
    target_field_name VARCHAR(128) NOT NULL,
    relation_type VARCHAR(32),
    method_signature VARCHAR(512),
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS field_usage_scenario (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    field_id BIGINT NOT NULL,
    table_id BIGINT NOT NULL,
    operation_type VARCHAR(16),
    scenario_description VARCHAR(512),
    method_name VARCHAR(256),
    source_table_name VARCHAR(128),
    source_api_name VARCHAR(256),
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS scan_record (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    project_id BIGINT NOT NULL,
    scan_type VARCHAR(16),
    table_names TEXT,
    status VARCHAR(16),
    error_msg TEXT,
    started_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    completed_at DATETIME
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

- [ ] **Step 2: 执行 SQL 初始化**

用 mysql 命令行执行 init.sql 创建数据库和表。

- [ ] **Step 3: Commit**

---

## Task 2: SpringBoot 后端项目骨架

**Files:**
- Create: `data-map-backend/pom.xml`
- Create: `data-map-backend/src/main/java/com/datamap/DataMapApplication.java`
- Create: `data-map-backend/src/main/java/com/datamap/common/Result.java`
- Create: `data-map-backend/src/main/java/com/datamap/common/PageResult.java`
- Create: `data-map-backend/src/main/resources/application.yml`

- [ ] **Step 1: 创建 pom.xml**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-parent</artifactId>
        <version>2.7.18</version>
        <relativePath/>
    </parent>

    <groupId>com.datamap</groupId>
    <artifactId>data-map-backend</artifactId>
    <version>1.0.0</version>
    <packaging>jar</packaging>
    <name>Data Map Backend</name>

    <properties>
        <java.version>1.8</java.version>
        <mybatis-plus.version>3.5.5</mybatis-plus.version>
    </properties>

    <dependencies>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId>
        </dependency>
        <dependency>
            <groupId>com.baomidou</groupId>
            <artifactId>mybatis-plus-boot-starter</artifactId>
            <version>${mybatis-plus.version}</version>
        </dependency>
        <dependency>
            <groupId>com.mysql</groupId>
            <artifactId>mysql-connector-j</artifactId>
            <scope>runtime</scope>
        </dependency>
        <dependency>
            <groupId>org.projectlombok</groupId>
            <artifactId>lombok</artifactId>
            <optional>true</optional>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-test</artifactId>
            <scope>test</scope>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
            </plugin>
        </plugins>
    </build>
</project>
```

- [ ] **Step 2: 创建 SpringBoot 启动类**

`DataMapApplication.java`:

```java
package com.datamap;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@MapperScan("com.datamap.mapper")
public class DataMapApplication {
    public static void main(String[] args) {
        SpringApplication.run(DataMapApplication.class, args);
    }
}
```

- [ ] **Step 3: 创建统一响应类**

`common/Result.java`:

```java
package com.datamap.common;

public class Result<T> {
    private int code;
    private String message;
    private T data;

    private Result() {}

    public static <T> Result<T> ok(T data) {
        Result<T> r = new Result<>();
        r.code = 0;
        r.message = "success";
        r.data = data;
        return r;
    }

    public static <T> Result<T> ok() {
        return ok(null);
    }

    public static <T> Result<T> fail(int code, String message) {
        Result<T> r = new Result<>();
        r.code = code;
        r.message = message;
        return r;
    }

    public int getCode() { return code; }
    public void setCode(int code) { this.code = code; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
    public T getData() { return data; }
    public void setData(T data) { this.data = data; }
}
```

- [ ] **Step 4: 创建 application.yml**

```yaml
server:
  port: 8080

spring:
  datasource:
    url: jdbc:mysql://localhost:3306/data_map?useUnicode=true&characterEncoding=utf-8&useSSL=false&serverTimezone=Asia/Shanghai
    username: root
    password: <用户提供的密码>
    driver-class-name: com.mysql.cj.jdbc.Driver

mybatis-plus:
  mapper-locations: classpath*:/mapper/**/*.xml
  global-config:
    db-config:
      id-type: auto
  configuration:
    map-underscore-to-camel-case: true
    log-impl: org.apache.ibatis.logging.stdout.StdOutImpl
```

- [ ] **Step 5: 验证项目可启动**

```bash
cd data-map-backend && JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk1.8.0_371.jdk/Contents/Home mvn spring-boot:run
```

- [ ] **Step 6: Commit**

---

## Task 3: Entity 实体类

**Files:**
- Create: `data-map-backend/src/main/java/com/datamap/entity/Project.java`
- Create: `data-map-backend/src/main/java/com/datamap/entity/TableInfo.java`
- Create: `data-map-backend/src/main/java/com/datamap/entity/TableField.java`
- Create: `data-map-backend/src/main/java/com/datamap/entity/TableRelation.java`
- Create: `data-map-backend/src/main/java/com/datamap/entity/FieldUsageScenario.java`
- Create: `data-map-backend/src/main/java/com/datamap/entity/ScanRecord.java`

- [ ] **Step 1: 创建 Project 实体**

`entity/Project.java`:

```java
package com.datamap.entity;

import com.baomidou.mybatisplus.annotation.*;
import java.time.LocalDateTime;

@TableName("project")
public class Project {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String appName;
    private String gitRepoUrl;
    private String gitLocalPath;
    private String description;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getAppName() { return appName; }
    public void setAppName(String appName) { this.appName = appName; }
    public String getGitRepoUrl() { return gitRepoUrl; }
    public void setGitRepoUrl(String gitRepoUrl) { this.gitRepoUrl = gitRepoUrl; }
    public String getGitLocalPath() { return gitLocalPath; }
    public void setGitLocalPath(String gitLocalPath) { this.gitLocalPath = gitLocalPath; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
```

(Note: Using manual getter/setter instead of Lombok @Data to ensure Java 8 compatibility without compiler plugins)

- [ ] **Step 2: 创建 TableInfo 实体**

`entity/TableInfo.java`:

```java
package com.datamap.entity;

import com.baomidou.mybatisplus.annotation.*;
import java.time.LocalDateTime;

@TableName("table_info")
public class TableInfo {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long projectId;
    private String tableName;
    private String tableComment;
    private Integer tableCommentManual;
    private String schemaName;
    private String dbType;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getProjectId() { return projectId; }
    public void setProjectId(Long projectId) { this.projectId = projectId; }
    public String getTableName() { return tableName; }
    public void setTableName(String tableName) { this.tableName = tableName; }
    public String getTableComment() { return tableComment; }
    public void setTableComment(String tableComment) { this.tableComment = tableComment; }
    public Integer getTableCommentManual() { return tableCommentManual; }
    public void setTableCommentManual(Integer tableCommentManual) { this.tableCommentManual = tableCommentManual; }
    public String getSchemaName() { return schemaName; }
    public void setSchemaName(String schemaName) { this.schemaName = schemaName; }
    public String getDbType() { return dbType; }
    public void setDbType(String dbType) { this.dbType = dbType; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
```

- [ ] **Step 3: 创建 TableField 实体**

`entity/TableField.java`:

```java
package com.datamap.entity;

import com.baomidou.mybatisplus.annotation.*;
import java.time.LocalDateTime;

@TableName("table_field")
public class TableField {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tableId;
    private String fieldName;
    private String fieldComment;
    private String fieldType;
    private Integer isPk;
    private Integer isBusinessField;
    private Integer fieldCommentManual;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getTableId() { return tableId; }
    public void setTableId(Long tableId) { this.tableId = tableId; }
    public String getFieldName() { return fieldName; }
    public void setFieldName(String fieldName) { this.fieldName = fieldName; }
    public String getFieldComment() { return fieldComment; }
    public void setFieldComment(String fieldComment) { this.fieldComment = fieldComment; }
    public String getFieldType() { return fieldType; }
    public void setFieldType(String fieldType) { this.fieldType = fieldType; }
    public Integer getIsPk() { return isPk; }
    public void setIsPk(Integer isPk) { this.isPk = isPk; }
    public Integer getIsBusinessField() { return isBusinessField; }
    public void setIsBusinessField(Integer isBusinessField) { this.isBusinessField = isBusinessField; }
    public Integer getFieldCommentManual() { return fieldCommentManual; }
    public void setFieldCommentManual(Integer fieldCommentManual) { this.fieldCommentManual = fieldCommentManual; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
```

- [ ] **Step 4: 创建 TableRelation 实体**

`entity/TableRelation.java`:

```java
package com.datamap.entity;

import com.baomidou.mybatisplus.annotation.*;
import java.time.LocalDateTime;

@TableName("table_relation")
public class TableRelation {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long projectId;
    private Long sourceTableId;
    private String sourceFieldName;
    private Long targetTableId;
    private String targetFieldName;
    private String relationType;
    private String methodSignature;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getProjectId() { return projectId; }
    public void setProjectId(Long projectId) { this.projectId = projectId; }
    public Long getSourceTableId() { return sourceTableId; }
    public void setSourceTableId(Long sourceTableId) { this.sourceTableId = sourceTableId; }
    public String getSourceFieldName() { return sourceFieldName; }
    public void setSourceFieldName(String sourceFieldName) { this.sourceFieldName = sourceFieldName; }
    public Long getTargetTableId() { return targetTableId; }
    public void setTargetTableId(Long targetTableId) { this.targetTableId = targetTableId; }
    public String getTargetFieldName() { return targetFieldName; }
    public void setTargetFieldName(String targetFieldName) { this.targetFieldName = targetFieldName; }
    public String getRelationType() { return relationType; }
    public void setRelationType(String relationType) { this.relationType = relationType; }
    public String getMethodSignature() { return methodSignature; }
    public void setMethodSignature(String methodSignature) { this.methodSignature = methodSignature; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
```

- [ ] **Step 5: 创建 FieldUsageScenario 实体**

`entity/FieldUsageScenario.java`:

```java
package com.datamap.entity;

import com.baomidou.mybatisplus.annotation.*;
import java.time.LocalDateTime;

@TableName("field_usage_scenario")
public class FieldUsageScenario {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long fieldId;
    private Long tableId;
    private String operationType;
    private String scenarioDescription;
    private String methodName;
    private String sourceTableName;
    private String sourceApiName;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getFieldId() { return fieldId; }
    public void setFieldId(Long fieldId) { this.fieldId = fieldId; }
    public Long getTableId() { return tableId; }
    public void setTableId(Long tableId) { this.tableId = tableId; }
    public String getOperationType() { return operationType; }
    public void setOperationType(String operationType) { this.operationType = operationType; }
    public String getScenarioDescription() { return scenarioDescription; }
    public void setScenarioDescription(String scenarioDescription) { this.scenarioDescription = scenarioDescription; }
    public String getMethodName() { return methodName; }
    public void setMethodName(String methodName) { this.methodName = methodName; }
    public String getSourceTableName() { return sourceTableName; }
    public void setSourceTableName(String sourceTableName) { this.sourceTableName = sourceTableName; }
    public String getSourceApiName() { return sourceApiName; }
    public void setSourceApiName(String sourceApiName) { this.sourceApiName = sourceApiName; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
```

- [ ] **Step 6: 创建 ScanRecord 实体**

`entity/ScanRecord.java`:

```java
package com.datamap.entity;

import com.baomidou.mybatisplus.annotation.*;
import java.time.LocalDateTime;

@TableName("scan_record")
public class ScanRecord {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long projectId;
    private String scanType;
    private String tableNames;
    private String status;
    private String errorMsg;
    private LocalDateTime startedAt;
    private LocalDateTime completedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getProjectId() { return projectId; }
    public void setProjectId(Long projectId) { this.projectId = projectId; }
    public String getScanType() { return scanType; }
    public void setScanType(String scanType) { this.scanType = scanType; }
    public String getTableNames() { return tableNames; }
    public void setTableNames(String tableNames) { this.tableNames = tableNames; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getErrorMsg() { return errorMsg; }
    public void setErrorMsg(String errorMsg) { this.errorMsg = errorMsg; }
    public LocalDateTime getStartedAt() { return startedAt; }
    public void setStartedAt(LocalDateTime startedAt) { this.startedAt = startedAt; }
    public LocalDateTime getCompletedAt() { return completedAt; }
    public void setCompletedAt(LocalDateTime completedAt) { this.completedAt = completedAt; }
}
```

- [ ] **Step 7: Commit**

---

## Task 4: Mapper 层 + MetaObjectHandler

**Files:**
- Create: `data-map-backend/src/main/java/com/datamap/mapper/ProjectMapper.java`
- Create: `data-map-backend/src/main/java/com/datamap/mapper/TableInfoMapper.java`
- Create: `data-map-backend/src/main/java/com/datamap/mapper/TableFieldMapper.java`
- Create: `data-map-backend/src/main/java/com/datamap/mapper/TableRelationMapper.java`
- Create: `data-map-backend/src/main/java/com/datamap/mapper/FieldUsageScenarioMapper.java`
- Create: `data-map-backend/src/main/java/com/datamap/mapper/ScanRecordMapper.java`
- Create: `data-map-backend/src/main/java/com/datamap/common/MetaObjectHandlerConfig.java`

- [ ] **Step 1: 创建 6 个 Mapper 接口**

`mapper/ProjectMapper.java`:

```java
package com.datamap.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.datamap.entity.Project;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ProjectMapper extends BaseMapper<Project> {
}
```

其他 5 个 Mapper 同理，分别对应 TableInfoMapper, TableFieldMapper, TableRelationMapper, FieldUsageScenarioMapper, ScanRecordMapper。

- [ ] **Step 2: 创建 MetaObjectHandler 自动填充时间**

`common/MetaObjectHandlerConfig.java`:

```java
package com.datamap.common;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import org.apache.ibatis.reflection.MetaObject;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
public class MetaObjectHandlerConfig implements MetaObjectHandler {

    @Override
    public void insertFill(MetaObject metaObject) {
        this.strictInsertFill(metaObject, "createdAt", LocalDateTime.class, LocalDateTime.now());
        this.strictInsertFill(metaObject, "updatedAt", LocalDateTime.class, LocalDateTime.now());
    }

    @Override
    public void updateFill(MetaObject metaObject) {
        this.strictUpdateFill(metaObject, "updatedAt", LocalDateTime.class, LocalDateTime.now());
    }
}
```

- [ ] **Step 3: Commit**

---

## Task 5: DTO 层

**Files:**
- Create: `data-map-backend/src/main/java/com/datamap/dto/ScanResultDTO.java`
- Create: `data-map-backend/src/main/java/com/datamap/dto/PathQueryRequest.java`
- Create: `data-map-backend/src/main/java/com/datamap/dto/PathResult.java`
- Create: `data-map-backend/src/main/java/com/datamap/dto/TableRelationVO.java`
- Create: `data-map-backend/src/main/java/com/datamap/dto/FieldSearchResult.java`

- [ ] **Step 1: 创建 ScanResultDTO (接收 Agent 扫描结果)**

`dto/ScanResultDTO.java`:

```java
package com.datamap.dto;

import java.util.List;

public class ScanResultDTO {
    private ProjectDTO project;
    private String scanType;
    private List<TableDTO> tables;

    public static class ProjectDTO {
        private String appName;
        private String gitRepoUrl;
        private String gitLocalPath;
        public String getAppName() { return appName; }
        public void setAppName(String appName) { this.appName = appName; }
        public String getGitRepoUrl() { return gitRepoUrl; }
        public void setGitRepoUrl(String gitRepoUrl) { this.gitRepoUrl = gitRepoUrl; }
        public String getGitLocalPath() { return gitLocalPath; }
        public void setGitLocalPath(String gitLocalPath) { this.gitLocalPath = gitLocalPath; }
    }

    public static class TableDTO {
        private String tableName;
        private String tableComment;
        private String schemaName;
        private String dbType;
        private List<FieldDTO> fields;
        private List<RelationDTO> relations;
        private List<UsageScenarioDTO> usageScenarios;

        public String getTableName() { return tableName; }
        public void setTableName(String tableName) { this.tableName = tableName; }
        public String getTableComment() { return tableComment; }
        public void setTableComment(String tableComment) { this.tableComment = tableComment; }
        public String getSchemaName() { return schemaName; }
        public void setSchemaName(String schemaName) { this.schemaName = schemaName; }
        public String getDbType() { return dbType; }
        public void setDbType(String dbType) { this.dbType = dbType; }
        public List<FieldDTO> getFields() { return fields; }
        public void setFields(List<FieldDTO> fields) { this.fields = fields; }
        public List<RelationDTO> getRelations() { return relations; }
        public void setRelations(List<RelationDTO> relations) { this.relations = relations; }
        public List<UsageScenarioDTO> getUsageScenarios() { return usageScenarios; }
        public void setUsageScenarios(List<UsageScenarioDTO> usageScenarios) { this.usageScenarios = usageScenarios; }
    }

    public static class FieldDTO {
        private String fieldName;
        private String fieldComment;
        private String fieldType;
        private Boolean isPk;
        private Boolean isBusinessField;
        public String getFieldName() { return fieldName; }
        public void setFieldName(String fieldName) { this.fieldName = fieldName; }
        public String getFieldComment() { return fieldComment; }
        public void setFieldComment(String fieldComment) { this.fieldComment = fieldComment; }
        public String getFieldType() { return fieldType; }
        public void setFieldType(String fieldType) { this.fieldType = fieldType; }
        public Boolean getIsPk() { return isPk; }
        public void setIsPk(Boolean isPk) { this.isPk = isPk; }
        public Boolean getIsBusinessField() { return isBusinessField; }
        public void setIsBusinessField(Boolean isBusinessField) { this.isBusinessField = isBusinessField; }
    }

    public static class RelationDTO {
        private String sourceFieldName;
        private String targetTableName;
        private String targetFieldName;
        private String relationType;
        private String methodSignature;
        public String getSourceFieldName() { return sourceFieldName; }
        public void setSourceFieldName(String sourceFieldName) { this.sourceFieldName = sourceFieldName; }
        public String getTargetTableName() { return targetTableName; }
        public void setTargetTableName(String targetTableName) { this.targetTableName = targetTableName; }
        public String getTargetFieldName() { return targetFieldName; }
        public void setTargetFieldName(String targetFieldName) { this.targetFieldName = targetFieldName; }
        public String getRelationType() { return relationType; }
        public void setRelationType(String relationType) { this.relationType = relationType; }
        public String getMethodSignature() { return methodSignature; }
        public void setMethodSignature(String methodSignature) { this.methodSignature = methodSignature; }
    }

    public static class UsageScenarioDTO {
        private String fieldName;
        private String operationType;
        private String scenarioDescription;
        private String methodName;
        private String sourceTableName;
        private String sourceApiName;
        public String getFieldName() { return fieldName; }
        public void setFieldName(String fieldName) { this.fieldName = fieldName; }
        public String getOperationType() { return operationType; }
        public void setOperationType(String operationType) { this.operationType = operationType; }
        public String getScenarioDescription() { return scenarioDescription; }
        public void setScenarioDescription(String scenarioDescription) { this.scenarioDescription = scenarioDescription; }
        public String getMethodName() { return methodName; }
        public void setMethodName(String methodName) { this.methodName = methodName; }
        public String getSourceTableName() { return sourceTableName; }
        public void setSourceTableName(String sourceTableName) { this.sourceTableName = sourceTableName; }
        public String getSourceApiName() { return sourceApiName; }
        public void setSourceApiName(String sourceApiName) { this.sourceApiName = sourceApiName; }
    }

    public ProjectDTO getProject() { return project; }
    public void setProject(ProjectDTO project) { this.project = project; }
    public String getScanType() { return scanType; }
    public void setScanType(String scanType) { this.scanType = scanType; }
    public List<TableDTO> getTables() { return tables; }
    public void setTables(List<TableDTO> tables) { this.tables = tables; }
}
```

- [ ] **Step 2: 创建 PathQueryRequest**

`dto/PathQueryRequest.java`:

```java
package com.datamap.dto;

public class PathQueryRequest {
    private Long projectId;
    private Long startTableId;
    private String startFieldName;
    private Long targetTableId;
    private String targetFieldName;

    public Long getProjectId() { return projectId; }
    public void setProjectId(Long projectId) { this.projectId = projectId; }
    public Long getStartTableId() { return startTableId; }
    public void setStartTableId(Long startTableId) { this.startTableId = startTableId; }
    public String getStartFieldName() { return startFieldName; }
    public void setStartFieldName(String startFieldName) { this.startFieldName = startFieldName; }
    public Long getTargetTableId() { return targetTableId; }
    public void setTargetTableId(Long targetTableId) { this.targetTableId = targetTableId; }
    public String getTargetFieldName() { return targetFieldName; }
    public void setTargetFieldName(String targetFieldName) { this.targetFieldName = targetFieldName; }
}
```

- [ ] **Step 3: 创建 PathResult**

`dto/PathResult.java`:

```java
package com.datamap.dto;

import java.util.List;

public class PathResult {
    private List<PathInfo> paths;

    public static class PathInfo {
        private List<String> nodes;
        private List<EdgeInfo> edges;
        private Integer length;

        public List<String> getNodes() { return nodes; }
        public void setNodes(List<String> nodes) { this.nodes = nodes; }
        public List<EdgeInfo> getEdges() { return edges; }
        public void setEdges(List<EdgeInfo> edges) { this.edges = edges; }
        public Integer getLength() { return length; }
        public void setLength(Integer length) { this.length = length; }
    }

    public static class EdgeInfo {
        private String from;
        private String to;
        private String joinField;

        public String getFrom() { return from; }
        public void setFrom(String from) { this.from = from; }
        public String getTo() { return to; }
        public void setTo(String to) { this.to = to; }
        public String getJoinField() { return joinField; }
        public void setJoinField(String joinField) { this.joinField = joinField; }
    }

    public List<PathInfo> getPaths() { return paths; }
    public void setPaths(List<PathInfo> paths) { this.paths = paths; }
}
```

- [ ] **Step 4: 创建 TableRelationVO (关系图展示用)**

`dto/TableRelationVO.java`:

```java
package com.datamap.dto;

import java.util.List;

public class TableRelationVO {
    private List<GraphNode> nodes;
    private List<GraphEdge> edges;
    private List<RelationDetail> details;

    public static class GraphNode {
        private Long id;
        private String label;
        public GraphNode() {}
        public GraphNode(Long id, String label) { this.id = id; this.label = label; }
        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public String getLabel() { return label; }
        public void setLabel(String label) { this.label = label; }
    }

    public static class GraphEdge {
        private Long source;
        private Long target;
        private String sourceField;
        private String targetField;
        public Long getSource() { return source; }
        public void setSource(Long source) { this.source = source; }
        public Long getTarget() { return target; }
        public void setTarget(Long target) { this.target = target; }
        public String getSourceField() { return sourceField; }
        public void setSourceField(String sourceField) { this.sourceField = sourceField; }
        public String getTargetField() { return targetField; }
        public void setTargetField(String targetField) { this.targetField = targetField; }
    }

    public static class RelationDetail {
        private String sourceTable;
        private Long sourceTableId;
        private String sourceField;
        private String targetTable;
        private Long targetTableId;
        private String targetField;
        private String relationType;
        private String methodSignature;

        public String getSourceTable() { return sourceTable; }
        public void setSourceTable(String sourceTable) { this.sourceTable = sourceTable; }
        public Long getSourceTableId() { return sourceTableId; }
        public void setSourceTableId(Long sourceTableId) { this.sourceTableId = sourceTableId; }
        public String getSourceField() { return sourceField; }
        public void setSourceField(String sourceField) { this.sourceField = sourceField; }
        public String getTargetTable() { return targetTable; }
        public void setTargetTable(String targetTable) { this.targetTable = targetTable; }
        public Long getTargetTableId() { return targetTableId; }
        public void setTargetTableId(Long targetTableId) { this.targetTableId = targetTableId; }
        public String getTargetField() { return targetField; }
        public void setTargetField(String targetField) { this.targetField = targetField; }
        public String getRelationType() { return relationType; }
        public void setRelationType(String relationType) { this.relationType = relationType; }
        public String getMethodSignature() { return methodSignature; }
        public void setMethodSignature(String methodSignature) { this.methodSignature = methodSignature; }
    }

    public List<GraphNode> getNodes() { return nodes; }
    public void setNodes(List<GraphNode> nodes) { this.nodes = nodes; }
    public List<GraphEdge> getEdges() { return edges; }
    public void setEdges(List<GraphEdge> edges) { this.edges = edges; }
    public List<RelationDetail> getDetails() { return details; }
    public void setDetails(List<RelationDetail> details) { this.details = details; }
}
```

- [ ] **Step 5: 创建 FieldSearchResult**

`dto/FieldSearchResult.java`:

```java
package com.datamap.dto;

public class FieldSearchResult {
    private String tableName;
    private Long tableId;
    private String fieldName;
    private String fieldComment;
    private String projectName;

    public String getTableName() { return tableName; }
    public void setTableName(String tableName) { this.tableName = tableName; }
    public Long getTableId() { return tableId; }
    public void setTableId(Long tableId) { this.tableId = tableId; }
    public String getFieldName() { return fieldName; }
    public void setFieldName(String fieldName) { this.fieldName = fieldName; }
    public String getFieldComment() { return fieldComment; }
    public void setFieldComment(String fieldComment) { this.fieldComment = fieldComment; }
    public String getProjectName() { return projectName; }
    public void setProjectName(String projectName) { this.projectName = projectName; }
}
```

- [ ] **Step 6: Commit**

---

## Task 6: Service 层 — 项目管理

**Files:**
- Create: `data-map-backend/src/main/java/com/datamap/service/ProjectService.java`

- [ ] **Step 1: 实现 ProjectService**

`service/ProjectService.java`:

```java
package com.datamap.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.datamap.entity.Project;
import com.datamap.mapper.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.util.List;

@Service
public class ProjectService {

    @Resource
    private ProjectMapper projectMapper;
    @Resource
    private TableInfoMapper tableInfoMapper;
    @Resource
    private TableFieldMapper tableFieldMapper;
    @Resource
    private TableRelationMapper tableRelationMapper;
    @Resource
    private FieldUsageScenarioMapper fieldUsageScenarioMapper;
    @Resource
    private ScanRecordMapper scanRecordMapper;

    public List<Project> list(String keyword) {
        LambdaQueryWrapper<Project> qw = new LambdaQueryWrapper<>();
        if (keyword != null && !keyword.isEmpty()) {
            qw.like(Project::getAppName, keyword);
        }
        qw.orderByDesc(Project::getUpdatedAt);
        return projectMapper.selectList(qw);
    }

    public Project getById(Long id) {
        return projectMapper.selectById(id);
    }

    public Project getByAppName(String appName) {
        LambdaQueryWrapper<Project> qw = new LambdaQueryWrapper<>();
        qw.eq(Project::getAppName, appName);
        return projectMapper.selectOne(qw);
    }

    public Project create(Project project) {
        projectMapper.insert(project);
        return project;
    }

    public Project update(Project project) {
        projectMapper.updateById(project);
        return projectMapper.selectById(project.getId());
    }

    @Transactional
    public void delete(Long id) {
        // 查找项目下所有表
        LambdaQueryWrapper<com.datamap.entity.TableInfo> tQw = new LambdaQueryWrapper<>();
        tQw.eq(com.datamap.entity.TableInfo::getProjectId, id);
        List<com.datamap.entity.TableInfo> tables = tableInfoMapper.selectList(tQw);

        for (com.datamap.entity.TableInfo table : tables) {
            // 删除字段使用场景
            LambdaQueryWrapper<com.datamap.entity.FieldUsageScenario> fQw = new LambdaQueryWrapper<>();
            fQw.eq(com.datamap.entity.FieldUsageScenario::getTableId, table.getId());
            fieldUsageScenarioMapper.delete(fQw);

            // 删除字段
            LambdaQueryWrapper<com.datamap.entity.TableField> tfQw = new LambdaQueryWrapper<>();
            tfQw.eq(com.datamap.entity.TableField::getTableId, table.getId());
            tableFieldMapper.delete(tfQw);
        }

        // 删除关联关系
        LambdaQueryWrapper<com.datamap.entity.TableRelation> rQw = new LambdaQueryWrapper<>();
        rQw.eq(com.datamap.entity.TableRelation::getProjectId, id);
        tableRelationMapper.delete(rQw);

        // 删除表
        tableInfoMapper.delete(tQw);

        // 删除扫描记录
        LambdaQueryWrapper<com.datamap.entity.ScanRecord> sQw = new LambdaQueryWrapper<>();
        sQw.eq(com.datamap.entity.ScanRecord::getProjectId, id);
        scanRecordMapper.delete(sQw);

        // 删除项目
        projectMapper.deleteById(id);
    }
}
```

- [ ] **Step 2: Commit**

---

## Task 7: Service 层 — 表信息管理 + 字段管理

**Files:**
- Create: `data-map-backend/src/main/java/com/datamap/service/TableService.java`
- Create: `data-map-backend/src/main/java/com/datamap/service/FieldService.java`

- [ ] **Step 1: 实现 TableService**

`service/TableService.java`:

```java
package com.datamap.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.datamap.entity.*;
import com.datamap.mapper.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.util.List;

@Service
public class TableService {

    @Resource
    private TableInfoMapper tableInfoMapper;
    @Resource
    private TableFieldMapper tableFieldMapper;
    @Resource
    private TableRelationMapper tableRelationMapper;
    @Resource
    private FieldUsageScenarioMapper fieldUsageScenarioMapper;

    public List<TableInfo> list(Long projectId, String tableName) {
        LambdaQueryWrapper<TableInfo> qw = new LambdaQueryWrapper<>();
        if (projectId != null) {
            qw.eq(TableInfo::getProjectId, projectId);
        }
        if (tableName != null && !tableName.isEmpty()) {
            qw.like(TableInfo::getTableName, tableName);
        }
        qw.orderByDesc(TableInfo::getUpdatedAt);
        return tableInfoMapper.selectList(qw);
    }

    public TableInfo getById(Long id) {
        return tableInfoMapper.selectById(id);
    }

    public TableInfo getByProjectAndName(Long projectId, String tableName) {
        LambdaQueryWrapper<TableInfo> qw = new LambdaQueryWrapper<>();
        qw.eq(TableInfo::getProjectId, projectId);
        qw.eq(TableInfo::getTableName, tableName);
        return tableInfoMapper.selectOne(qw);
    }

    public TableInfo updateComment(Long id, String comment) {
        TableInfo table = tableInfoMapper.selectById(id);
        if (table != null) {
            table.setTableComment(comment);
            table.setTableCommentManual(1);
            tableInfoMapper.updateById(table);
        }
        return table;
    }

    @Transactional
    public void delete(Long id) {
        // 删除使用场景
        LambdaQueryWrapper<FieldUsageScenario> sQw = new LambdaQueryWrapper<>();
        sQw.eq(FieldUsageScenario::getTableId, id);
        fieldUsageScenarioMapper.delete(sQw);

        // 删除字段
        LambdaQueryWrapper<TableField> fQw = new LambdaQueryWrapper<>();
        fQw.eq(TableField::getTableId, id);
        tableFieldMapper.delete(fQw);

        // 删除关联关系（source 或 target）
        LambdaQueryWrapper<TableRelation> rQw = new LambdaQueryWrapper<>();
        rQw.eq(TableRelation::getSourceTableId, id).or().eq(TableRelation::getTargetTableId, id);
        tableRelationMapper.delete(rQw);

        tableInfoMapper.deleteById(id);
    }
}
```

- [ ] **Step 2: 实现 FieldService**

`service/FieldService.java`:

```java
package com.datamap.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.datamap.entity.*;
import com.datamap.mapper.*;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.List;

@Service
public class FieldService {

    @Resource
    private TableFieldMapper tableFieldMapper;
    @Resource
    private FieldUsageScenarioMapper fieldUsageScenarioMapper;

    public List<TableField> listByTableId(Long tableId) {
        LambdaQueryWrapper<TableField> qw = new LambdaQueryWrapper<>();
        qw.eq(TableField::getTableId, tableId);
        qw.orderByAsc(TableField::getIsPk);
        return tableFieldMapper.selectList(qw);
    }

    public TableField updateComment(Long id, String comment) {
        TableField field = tableFieldMapper.selectById(id);
        if (field != null) {
            field.setFieldComment(comment);
            field.setFieldCommentManual(1);
            tableFieldMapper.updateById(field);
        }
        return field;
    }

    public List<FieldUsageScenario> getUsageScenarios(Long fieldId) {
        LambdaQueryWrapper<FieldUsageScenario> qw = new LambdaQueryWrapper<>();
        qw.eq(FieldUsageScenario::getFieldId, fieldId);
        return fieldUsageScenarioMapper.selectList(qw);
    }

    public TableField getByTableAndName(Long tableId, String fieldName) {
        LambdaQueryWrapper<TableField> qw = new LambdaQueryWrapper<>();
        qw.eq(TableField::getTableId, tableId);
        qw.eq(TableField::getFieldName, fieldName);
        return tableFieldMapper.selectOne(qw);
    }
}
```

- [ ] **Step 3: Commit**

---

## Task 8: Service 层 — 关联关系 + 路径查询 + 扫描接收

**Files:**
- Create: `data-map-backend/src/main/java/com/datamap/service/RelationService.java`
- Create: `data-map-backend/src/main/java/com/datamap/service/QueryService.java`
- Create: `data-map-backend/src/main/java/com/datamap/service/ScanService.java`

- [ ] **Step 1: 实现 RelationService (BFS 图遍历 + 关系图)**

`service/RelationService.java`:

```java
package com.datamap.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.datamap.dto.TableRelationVO;
import com.datamap.entity.*;
import com.datamap.mapper.*;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class RelationService {

    @Resource
    private TableRelationMapper tableRelationMapper;
    @Resource
    private TableInfoMapper tableInfoMapper;

    public TableRelationVO getRelations(Long tableId, String direction) {
        Set<Long> tableIds = new HashSet<>();
        tableIds.add(tableId);

        LambdaQueryWrapper<TableRelation> qw = new LambdaQueryWrapper<>();
        if ("upstream".equals(direction)) {
            // BFS upstream: 从当前表出发，找所有 source -> target = 当前表的边
            collectUpstream(tableId, tableIds, new HashSet<>());
        } else if ("downstream".equals(direction)) {
            // BFS downstream: 从当前表出发，找所有 source = 当前表 -> target 的边
            collectDownstream(tableId, tableIds, new HashSet<>());
        } else {
            // all: 双向 BFS
            collectAll(tableId, tableIds, new HashSet<>());
        }

        List<TableInfo> tables = tableInfoMapper.selectBatchIds(tableIds);
        Map<Long, String> nameMap = tables.stream()
                .collect(Collectors.toMap(TableInfo::getId, TableInfo::getTableName));

        List<TableRelation> relations;
        if ("upstream".equals(direction)) {
            qw.in(TableRelation::getTargetTableId, tableIds);
            qw.in(TableRelation::getSourceTableId, tableIds);
        } else if ("downstream".equals(direction)) {
            qw.in(TableRelation::getSourceTableId, tableIds);
            qw.in(TableRelation::getTargetTableId, tableIds);
        } else {
            qw.and(w -> w.in(TableRelation::getSourceTableId, tableIds)
                    .or().in(TableRelation::getTargetTableId, tableIds));
        }
        relations = tableRelationMapper.selectList(qw);

        TableRelationVO vo = new TableRelationVO();
        vo.setNodes(tableIds.stream()
                .map(id -> new TableRelationVO.GraphNode(id, nameMap.getOrDefault(id, "")))
                .collect(Collectors.toList()));
        vo.setEdges(relations.stream().map(r -> {
            TableRelationVO.GraphEdge e = new TableRelationVO.GraphEdge();
            e.setSource(r.getSourceTableId());
            e.setTarget(r.getTargetTableId());
            e.setSourceField(r.getSourceFieldName());
            e.setTargetField(r.getTargetFieldName());
            return e;
        }).collect(Collectors.toList()));
        vo.setDetails(relations.stream().map(r -> {
            TableRelationVO.RelationDetail d = new TableRelationVO.RelationDetail();
            d.setSourceTable(nameMap.getOrDefault(r.getSourceTableId(), ""));
            d.setSourceTableId(r.getSourceTableId());
            d.setSourceField(r.getSourceFieldName());
            d.setTargetTable(nameMap.getOrDefault(r.getTargetTableId(), ""));
            d.setTargetTableId(r.getTargetTableId());
            d.setTargetField(r.getTargetFieldName());
            d.setRelationType(r.getRelationType());
            d.setMethodSignature(r.getMethodSignature());
            return d;
        }).collect(Collectors.toList()));

        return vo;
    }

    private void collectUpstream(Long id, Set<Long> result, Set<Long> visited) {
        if (!visited.add(id)) return;
        LambdaQueryWrapper<TableRelation> qw = new LambdaQueryWrapper<>();
        qw.eq(TableRelation::getTargetTableId, id);
        List<TableRelation> relations = tableRelationMapper.selectList(qw);
        for (TableRelation r : relations) {
            result.add(r.getSourceTableId());
            collectUpstream(r.getSourceTableId(), result, visited);
        }
    }

    private void collectDownstream(Long id, Set<Long> result, Set<Long> visited) {
        if (!visited.add(id)) return;
        LambdaQueryWrapper<TableRelation> qw = new LambdaQueryWrapper<>();
        qw.eq(TableRelation::getSourceTableId, id);
        List<TableRelation> relations = tableRelationMapper.selectList(qw);
        for (TableRelation r : relations) {
            result.add(r.getTargetTableId());
            collectDownstream(r.getTargetTableId(), result, visited);
        }
    }

    private void collectAll(Long id, Set<Long> result, Set<Long> visited) {
        if (!visited.add(id)) return;
        LambdaQueryWrapper<TableRelation> qw = new LambdaQueryWrapper<>();
        qw.eq(TableRelation::getSourceTableId, id).or().eq(TableRelation::getTargetTableId, id);
        List<TableRelation> relations = tableRelationMapper.selectList(qw);
        for (TableRelation r : relations) {
            Long next = r.getSourceTableId().equals(id) ? r.getTargetTableId() : r.getSourceTableId();
            result.add(next);
            collectAll(next, result, visited);
        }
    }
}
```

- [ ] **Step 2: 实现 QueryService (字段搜索 + BFS 路径查询)**

`service/QueryService.java`:

```java
package com.datamap.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.datamap.dto.*;
import com.datamap.entity.*;
import com.datamap.mapper.*;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class QueryService {

    @Resource
    private TableFieldMapper tableFieldMapper;
    @Resource
    private TableInfoMapper tableInfoMapper;
    @Resource
    private TableRelationMapper tableRelationMapper;
    @Resource
    private ProjectMapper projectMapper;

    public List<FieldSearchResult> searchFields(String keyword, Long projectId) {
        LambdaQueryWrapper<TableField> fQw = new LambdaQueryWrapper<>();
        if (keyword != null && !keyword.isEmpty()) {
            fQw.like(TableField::getFieldComment, keyword);
        }
        List<TableField> fields = tableFieldMapper.selectList(fQw);
        if (fields.isEmpty()) return Collections.emptyList();

        Set<Long> tableIds = fields.stream().map(TableField::getTableId).collect(Collectors.toSet());
        Map<Long, TableInfo> tableMap = new HashMap<>();
        Map<Long, Project> projectMap = new HashMap<>();

        List<TableInfo> tables = tableInfoMapper.selectBatchIds(tableIds);
        for (TableInfo t : tables) {
            tableMap.put(t.getId(), t);
            if (projectId == null || t.getProjectId().equals(projectId)) {
                projectMap.putIfAbsent(t.getProjectId(), projectMapper.selectById(t.getProjectId()));
            }
        }

        return fields.stream()
                .filter(f -> {
                    TableInfo t = tableMap.get(f.getTableId());
                    return t != null && (projectId == null || t.getProjectId().equals(projectId));
                })
                .map(f -> {
                    FieldSearchResult r = new FieldSearchResult();
                    TableInfo t = tableMap.get(f.getTableId());
                    r.setTableName(t != null ? t.getTableName() : "");
                    r.setTableId(f.getTableId());
                    r.setFieldName(f.getFieldName());
                    r.setFieldComment(f.getFieldComment());
                    Project p = projectMap.get(t != null ? t.getProjectId() : null);
                    r.setProjectName(p != null ? p.getAppName() : "");
                    return r;
                }).collect(Collectors.toList());
    }

    public PathResult findPath(PathQueryRequest req) {
        // BFS 路径查找，max depth 6
        List<PathResult.PathInfo> paths = new ArrayList<>();
        Map<Long, String> tableNames = new HashMap<>();

        // 加载表名映射
        List<TableInfo> allTables = tableInfoMapper.selectList(
                new LambdaQueryWrapper<TableInfo>().eq(TableInfo::getProjectId, req.getProjectId()));
        for (TableInfo t : allTables) {
            tableNames.put(t.getId(), t.getTableName());
        }

        // 加载所有关联关系，建立邻接表
        List<TableRelation> relations = tableRelationMapper.selectList(
                new LambdaQueryWrapper<TableRelation>().eq(TableRelation::getProjectId, req.getProjectId()));
        Map<Long, List<TableRelation>> adj = new HashMap<>();
        for (TableRelation r : relations) {
            adj.computeIfAbsent(r.getSourceTableId(), k -> new ArrayList<>()).add(r);
        }

        // BFS
        Queue<List<TableRelation>> queue = new LinkedList<>();
        Set<Long> visited = new HashSet<>();
        visited.add(req.getStartTableId());

        // 初始化队列：以起始表出发的所有边
        List<TableRelation> startEdges = adj.getOrDefault(req.getStartTableId(), Collections.emptyList());
        for (TableRelation edge : startEdges) {
            List<TableRelation> path = new ArrayList<>();
            path.add(edge);
            queue.offer(path);
        }

        while (!queue.isEmpty() && paths.size() < 10) {
            List<TableRelation> currentPath = queue.poll();
            if (currentPath.size() > 6) continue;

            TableRelation lastEdge = currentPath.get(currentPath.size() - 1);
            Long currentNode = lastEdge.getTargetTableId();

            if (currentNode.equals(req.getTargetTableId())) {
                // 找到路径
                PathResult.PathInfo pi = new PathResult.PathInfo();
                List<String> nodes = new ArrayList<>();
                nodes.add(tableNames.getOrDefault(req.getStartTableId(), ""));
                List<PathResult.EdgeInfo> edges = new ArrayList<>();
                for (TableRelation r : currentPath) {
                    nodes.add(tableNames.getOrDefault(r.getTargetTableId(), ""));
                    PathResult.EdgeInfo ei = new PathResult.EdgeInfo();
                    ei.setFrom(tableNames.getOrDefault(r.getSourceTableId(), ""));
                    ei.setTo(tableNames.getOrDefault(r.getTargetTableId(), ""));
                    ei.setJoinField(r.getSourceFieldName() + " = " + r.getTargetFieldName());
                    edges.add(ei);
                }
                pi.setNodes(nodes);
                pi.setEdges(edges);
                pi.setLength(currentPath.size());
                paths.add(pi);
            } else {
                List<TableRelation> nextEdges = adj.getOrDefault(currentNode, Collections.emptyList());
                for (TableRelation next : nextEdges) {
                    if (!visited.contains(next.getTargetTableId()) || next.getTargetTableId().equals(req.getTargetTableId())) {
                        List<TableRelation> newPath = new ArrayList<>(currentPath);
                        newPath.add(next);
                        queue.offer(newPath);
                    }
                }
                visited.add(currentNode);
            }
        }

        // 按长度排序
        paths.sort(Comparator.comparingInt(PathResult.PathInfo::getLength));

        PathResult result = new PathResult();
        result.setPaths(paths);
        return result;
    }
}
```

- [ ] **Step 3: 实现 ScanService (接收扫描结果 + 增量保护)**

`service/ScanService.java`:

```java
package com.datamap.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.datamap.dto.ScanResultDTO;
import com.datamap.entity.*;
import com.datamap.mapper.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class ScanService {

    @Resource
    private ProjectMapper projectMapper;
    @Resource
    private TableInfoMapper tableInfoMapper;
    @Resource
    private TableFieldMapper tableFieldMapper;
    @Resource
    private TableRelationMapper tableRelationMapper;
    @Resource
    private FieldUsageScenarioMapper fieldUsageScenarioMapper;
    @Resource
    private ScanRecordMapper scanRecordMapper;

    @Transactional
    public void receiveScanResult(ScanResultDTO dto) {
        // 1. 查找或创建项目
        ScanResultDTO.ProjectDTO pd = dto.getProject();
        Project project;
        LambdaQueryWrapper<Project> pqw = new LambdaQueryWrapper<>();
        pqw.eq(Project::getAppName, pd.getAppName());
        project = projectMapper.selectOne(pqw);
        if (project == null) {
            project = new Project();
            project.setAppName(pd.getAppName());
            project.setGitRepoUrl(pd.getGitRepoUrl());
            project.setGitLocalPath(pd.getGitLocalPath());
            projectMapper.insert(project);
        }

        // 2. 创建扫描记录
        ScanRecord record = new ScanRecord();
        record.setProjectId(project.getId());
        record.setScanType(dto.getScanType());
        record.setStatus("RUNNING");
        record.setStartedAt(LocalDateTime.now());
        scanRecordMapper.insert(record);

        try {
            // 3. 处理每个表
            for (ScanResultDTO.TableDTO td : dto.getTables()) {
                processTable(project.getId(), td);
            }

            record.setStatus("SUCCESS");
        } catch (Exception e) {
            record.setStatus("FAILED");
            record.setErrorMsg(e.getMessage());
        }
        record.setCompletedAt(LocalDateTime.now());
        scanRecordMapper.updateById(record);
    }

    private void processTable(Long projectId, ScanResultDTO.TableDTO td) {
        // 查找或创建表
        LambdaQueryWrapper<TableInfo> tqw = new LambdaQueryWrapper<>();
        tqw.eq(TableInfo::getProjectId, projectId);
        tqw.eq(TableInfo::getTableName, td.getTableName());
        TableInfo table = tableInfoMapper.selectOne(tqw);

        if (table == null) {
            table = new TableInfo();
            table.setProjectId(projectId);
            table.setTableName(td.getTableName());
            table.setSchemaName(td.getSchemaName());
            table.setDbType(td.getDbType());
            table.setTableComment(td.getTableComment());
            table.setTableCommentManual(0);
            tableInfoMapper.insert(table);
        } else {
            // 增量更新：仅当 table_comment_manual != 1 时覆盖
            if (table.getTableCommentManual() == null || table.getTableCommentManual() != 1) {
                table.setTableComment(td.getTableComment());
            }
            tableInfoMapper.updateById(table);
        }

        // 处理字段
        for (ScanResultDTO.FieldDTO fd : td.getFields()) {
            LambdaQueryWrapper<TableField> fqw = new LambdaQueryWrapper<>();
            fqw.eq(TableField::getTableId, table.getId());
            fqw.eq(TableField::getFieldName, fd.getFieldName());
            TableField field = tableFieldMapper.selectOne(fqw);

            if (field == null) {
                field = new TableField();
                field.setTableId(table.getId());
                field.setFieldName(fd.getFieldName());
                field.setFieldComment(fd.getFieldComment());
                field.setFieldType(fd.getFieldType());
                field.setIsPk(fd.getIsPk() != null && fd.getIsPk() ? 1 : 0);
                field.setIsBusinessField(fd.getIsBusinessField() != null && fd.getIsBusinessField() ? 1 : 0);
                field.setFieldCommentManual(0);
                tableFieldMapper.insert(field);
            } else {
                if (field.getFieldCommentManual() == null || field.getFieldCommentManual() != 1) {
                    field.setFieldComment(fd.getFieldComment());
                }
                field.setFieldType(fd.getFieldType());
                field.setIsPk(fd.getIsPk() != null && fd.getIsPk() ? 1 : 0);
                field.setIsBusinessField(fd.getIsBusinessField() != null && fd.getIsBusinessField() ? 1 : 0);
                tableFieldMapper.updateById(field);
            }
        }

        // 处理关联关系：清理当前表相关的 relation 后重写
        if (td.getRelations() != null) {
            LambdaQueryWrapper<TableRelation> rqw = new LambdaQueryWrapper<>();
            rqw.eq(TableRelation::getProjectId, projectId);
            rqw.eq(TableRelation::getSourceTableId, table.getId());
            tableRelationMapper.delete(rqw);

            for (ScanResultDTO.RelationDTO rd : td.getRelations()) {
                // 查找 target table id
                LambdaQueryWrapper<TableInfo> ttqw = new LambdaQueryWrapper<>();
                ttqw.eq(TableInfo::getProjectId, projectId);
                ttqw.eq(TableInfo::getTableName, rd.getTargetTableName());
                TableInfo targetTable = tableInfoMapper.selectOne(ttqw);

                if (targetTable != null) {
                    TableRelation relation = new TableRelation();
                    relation.setProjectId(projectId);
                    relation.setSourceTableId(table.getId());
                    relation.setSourceFieldName(rd.getSourceFieldName());
                    relation.setTargetTableId(targetTable.getId());
                    relation.setTargetFieldName(rd.getTargetFieldName());
                    relation.setRelationType(rd.getRelationType());
                    relation.setMethodSignature(rd.getMethodSignature());
                    tableRelationMapper.insert(relation);
                }
            }
        }

        // 处理使用场景：清理当前表相关的 scenario 后重写
        if (td.getUsageScenarios() != null) {
            LambdaQueryWrapper<FieldUsageScenario> sqw = new LambdaQueryWrapper<>();
            sqw.eq(FieldUsageScenario::getTableId, table.getId());
            fieldUsageScenarioMapper.delete(sqw);

            for (ScanResultDTO.UsageScenarioDTO us : td.getUsageScenarios()) {
                // 查找 field id
                LambdaQueryWrapper<TableField> fqw = new LambdaQueryWrapper<>();
                fqw.eq(TableField::getTableId, table.getId());
                fqw.eq(TableField::getFieldName, us.getFieldName());
                TableField field = tableFieldMapper.selectOne(fqw);

                FieldUsageScenario scenario = new FieldUsageScenario();
                scenario.setFieldId(field != null ? field.getId() : null);
                scenario.setTableId(table.getId());
                scenario.setOperationType(us.getOperationType());
                scenario.setScenarioDescription(us.getScenarioDescription());
                scenario.setMethodName(us.getMethodName());
                scenario.setSourceTableName(us.getSourceTableName());
                scenario.setSourceApiName(us.getSourceApiName());
                fieldUsageScenarioMapper.insert(scenario);
            }
        }
    }
}
```

- [ ] **Step 4: Commit**

---

## Task 9: Controller 层

**Files:**
- Create: `data-map-backend/src/main/java/com/datamap/controller/ProjectController.java`
- Create: `data-map-backend/src/main/java/com/datamap/controller/TableController.java`
- Create: `data-map-backend/src/main/java/com/datamap/controller/FieldController.java`
- Create: `data-map-backend/src/main/java/com/datamap/controller/RelationController.java`
- Create: `data-map-backend/src/main/java/com/datamap/controller/QueryController.java`
- Create: `data-map-backend/src/main/java/com/datamap/controller/ScanController.java`
- Modify: `data-map-backend/src/main/java/com/datamap/DataMapApplication.java` (add Jackson config for Java 8 datetime)

- [ ] **Step 1: 创建 ProjectController**

`controller/ProjectController.java`:

```java
package com.datamap.controller;

import com.datamap.common.Result;
import com.datamap.entity.Project;
import com.datamap.service.ProjectService;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.List;

@RestController
@RequestMapping("/api/projects")
public class ProjectController {

    @Resource
    private ProjectService projectService;

    @PostMapping
    public Result<Project> create(@RequestBody Project project) {
        return Result.ok(projectService.create(project));
    }

    @GetMapping
    public Result<List<Project>> list(@RequestParam(required = false) String keyword) {
        return Result.ok(projectService.list(keyword));
    }

    @GetMapping("/{id}")
    public Result<Project> getById(@PathVariable Long id) {
        return Result.ok(projectService.getById(id));
    }

    @PutMapping("/{id}")
    public Result<Project> update(@PathVariable Long id, @RequestBody Project project) {
        project.setId(id);
        return Result.ok(projectService.update(project));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        projectService.delete(id);
        return Result.ok();
    }
}
```

- [ ] **Step 2: 创建 TableController**

`controller/TableController.java`:

```java
package com.datamap.controller;

import com.datamap.common.Result;
import com.datamap.entity.TableInfo;
import com.datamap.service.TableService;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/tables")
public class TableController {

    @Resource
    private TableService tableService;

    @GetMapping
    public Result<List<TableInfo>> list(@RequestParam(required = false) Long projectId,
                                         @RequestParam(required = false) String tableName) {
        return Result.ok(tableService.list(projectId, tableName));
    }

    @GetMapping("/{id}")
    public Result<TableInfo> getById(@PathVariable Long id) {
        return Result.ok(tableService.getById(id));
    }

    @PutMapping("/{id}")
    public Result<TableInfo> update(@PathVariable Long id, @RequestBody Map<String, String> body) {
        return Result.ok(tableService.updateComment(id, body.get("tableComment")));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        tableService.delete(id);
        return Result.ok();
    }
}
```

- [ ] **Step 3: 创建 FieldController**

`controller/FieldController.java`:

```java
package com.datamap.controller;

import com.datamap.common.Result;
import com.datamap.entity.FieldUsageScenario;
import com.datamap.entity.TableField;
import com.datamap.service.FieldService;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class FieldController {

    @Resource
    private FieldService fieldService;

    @GetMapping("/tables/{tableId}/fields")
    public Result<List<TableField>> list(@PathVariable Long tableId) {
        return Result.ok(fieldService.listByTableId(tableId));
    }

    @PutMapping("/fields/{id}")
    public Result<TableField> update(@PathVariable Long id, @RequestBody Map<String, String> body) {
        return Result.ok(fieldService.updateComment(id, body.get("fieldComment")));
    }

    @GetMapping("/fields/{id}/usage-scenarios")
    public Result<List<FieldUsageScenario>> getUsageScenarios(@PathVariable Long id) {
        return Result.ok(fieldService.getUsageScenarios(id));
    }
}
```

- [ ] **Step 4: 创建 RelationController**

`controller/RelationController.java`:

```java
package com.datamap.controller;

import com.datamap.common.Result;
import com.datamap.dto.TableRelationVO;
import com.datamap.service.RelationService;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;

@RestController
@RequestMapping("/api/relations")
public class RelationController {

    @Resource
    private RelationService relationService;

    @GetMapping("/{tableId}")
    public Result<TableRelationVO> getRelations(
            @PathVariable Long tableId,
            @RequestParam(defaultValue = "all") String direction) {
        return Result.ok(relationService.getRelations(tableId, direction));
    }
}
```

- [ ] **Step 5: 创建 QueryController**

`controller/QueryController.java`:

```java
package com.datamap.controller;

import com.datamap.common.Result;
import com.datamap.dto.FieldSearchResult;
import com.datamap.dto.PathQueryRequest;
import com.datamap.dto.PathResult;
import com.datamap.service.QueryService;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.List;

@RestController
@RequestMapping("/api/query")
public class QueryController {

    @Resource
    private QueryService queryService;

    @GetMapping("/search-fields")
    public Result<List<FieldSearchResult>> searchFields(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long projectId) {
        return Result.ok(queryService.searchFields(keyword, projectId));
    }

    @PostMapping("/find-path")
    public Result<PathResult> findPath(@RequestBody PathQueryRequest request) {
        return Result.ok(queryService.findPath(request));
    }
}
```

- [ ] **Step 6: 创建 ScanController**

`controller/ScanController.java`:

```java
package com.datamap.controller;

import com.datamap.common.Result;
import com.datamap.dto.ScanResultDTO;
import com.datamap.service.ScanService;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;

@RestController
@RequestMapping("/api/scan")
public class ScanController {

    @Resource
    private ScanService scanService;

    @PostMapping("/result")
    public Result<Void> receiveScanResult(@RequestBody ScanResultDTO dto) {
        scanService.receiveScanResult(dto);
        return Result.ok();
    }
}
```

- [ ] **Step 7: 添加 CORS 配置**

在 `DataMapApplication.java` 中添加：

```java
package com.datamap;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@SpringBootApplication
@MapperScan("com.datamap.mapper")
public class DataMapApplication {
    public static void main(String[] args) {
        SpringApplication.run(DataMapApplication.class, args);
    }

    @Bean
    public WebMvcConfigurer corsConfigurer() {
        return new WebMvcConfigurer() {
            @Override
            public void addCorsMappings(CorsRegistry registry) {
                registry.addMapping("/api/**")
                        .allowedOrigins("http://localhost:5173")
                        .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS");
            }
        };
    }
}
```

- [ ] **Step 8: 验证后端编译通过**

```bash
cd data-map-backend && JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk1.8.0_371.jdk/Contents/Home mvn compile
```

- [ ] **Step 9: Commit**

---

## Task 10: 前端项目骨架

**Files:**
- Create: `data-map-frontend/package.json`
- Create: `data-map-frontend/vite.config.ts`
- Create: `data-map-frontend/tsconfig.json`
- Create: `data-map-frontend/tsconfig.node.json`
- Create: `data-map-frontend/index.html`
- Create: `data-map-frontend/src/main.tsx`
- Create: `data-map-frontend/src/App.tsx`
- Create: `data-map-frontend/src/services/api.ts`
- Create: `data-map-frontend/src/router/index.tsx`

- [ ] **Step 1: 创建 package.json**

```json
{
  "name": "data-map-frontend",
  "private": true,
  "version": "1.0.0",
  "type": "module",
  "scripts": {
    "dev": "vite",
    "build": "tsc && vite build",
    "preview": "vite preview"
  },
  "dependencies": {
    "react": "^18.3.1",
    "react-dom": "^18.3.1",
    "react-router-dom": "^6.26.0",
    "antd": "^5.20.0",
    "@ant-design/icons": "^5.4.0",
    "@antv/g6": "^5.0.30",
    "axios": "^1.7.3"
  },
  "devDependencies": {
    "@types/react": "^18.3.3",
    "@types/react-dom": "^18.3.0",
    "@vitejs/plugin-react": "^4.3.1",
    "typescript": "^5.5.4",
    "vite": "^5.4.0"
  }
}
```

- [ ] **Step 2: 创建 vite.config.ts**

```typescript
import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
    },
  },
})
```

- [ ] **Step 3: 创建 tsconfig.json**

```json
{
  "compilerOptions": {
    "target": "ES2020",
    "useDefineForClassFields": true,
    "lib": ["ES2020", "DOM", "DOM.Iterable"],
    "module": "ESNext",
    "skipLibCheck": true,
    "moduleResolution": "bundler",
    "allowImportingTsExtensions": true,
    "resolveJsonModule": true,
    "isolatedModules": true,
    "noEmit": true,
    "jsx": "react-jsx",
    "strict": true,
    "noUnusedLocals": false,
    "noUnusedParameters": false
  },
  "include": ["src"]
}
```

- [ ] **Step 4: 创建 index.html**

```html
<!DOCTYPE html>
<html lang="zh-CN">
  <head>
    <meta charset="UTF-8" />
    <meta name="viewport" content="width=device-width, initial-scale=1.0" />
    <title>数据地图</title>
  </head>
  <body>
    <div id="root"></div>
    <script type="module" src="/src/main.tsx"></script>
  </body>
</html>
```

- [ ] **Step 5: 创建 src/main.tsx**

```tsx
import React from 'react'
import ReactDOM from 'react-dom/client'
import App from './App'
import 'antd/dist/reset.css'

ReactDOM.createRoot(document.getElementById('root')!).render(
  <React.StrictMode>
    <App />
  </React.StrictMode>,
)
```

- [ ] **Step 6: 创建 src/App.tsx (布局 + 路由)**

```tsx
import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom'
import { Layout, Menu } from 'antd'
import { TableOutlined, SearchOutlined } from '@ant-design/icons'
import { useNavigate, useLocation } from 'react-router-dom'
import TableInfoManage from './pages/TableInfoManage'
import TableDetail from './pages/TableInfoManage/TableDetail'
import TableRelation from './pages/TableRelation'
import TableLogicQuery from './pages/TableLogicQuery'

const { Sider, Content, Header } = Layout

const menuItems = [
  {
    key: '/tables',
    icon: <TableOutlined />,
    label: '表信息管理',
    children: [
      { key: '/tables/list', label: '表基本信息管理' },
      { key: '/tables/query', label: '表逻辑信息查询' },
    ],
  },
]

function AppLayout() {
  const navigate = useNavigate()
  const location = useLocation()

  const selectedKey = (() => {
    if (location.pathname.startsWith('/tables/list') || location.pathname.startsWith('/tables/') && !location.pathname.startsWith('/tables/query')) return '/tables/list'
    if (location.pathname.startsWith('/tables/query')) return '/tables/query'
    return '/tables/list'
  })()

  return (
    <Layout style={{ minHeight: '100vh' }}>
      <Sider width={220} theme="dark">
        <div style={{ color: '#fff', fontSize: 18, textAlign: 'center', padding: '16px 0', fontWeight: 'bold' }}>
          数据地图
        </div>
        <Menu
          theme="dark"
          mode="inline"
          selectedKeys={[selectedKey]}
          items={menuItems}
          onClick={({ key }) => navigate(key)}
        />
      </Sider>
      <Layout>
        <Content style={{ margin: 16, padding: 24, background: '#fff', borderRadius: 8 }}>
          <Routes>
            <Route path="/" element={<Navigate to="/tables/list" />} />
            <Route path="/tables/list" element={<TableInfoManage />} />
            <Route path="/tables/:id" element={<TableDetail />} />
            <Route path="/tables/:id/relations" element={<TableRelation />} />
            <Route path="/tables/query" element={<TableLogicQuery />} />
          </Routes>
        </Content>
      </Layout>
    </Layout>
  )
}

export default function App() {
  return (
    <BrowserRouter>
      <AppLayout />
    </BrowserRouter>
  )
}
```

- [ ] **Step 7: 创建 src/services/api.ts (API 封装)**

```typescript
import axios from 'axios'

const api = axios.create({
  baseURL: '/api',
  timeout: 10000,
})

api.interceptors.response.use(
  (res) => {
    if (res.data.code !== 0) {
      return Promise.reject(new Error(res.data.message || 'Request failed'))
    }
    return res.data
  },
  (err) => Promise.reject(err),
)

// Project APIs
export const projectApi = {
  list: (keyword?: string) => api.get('/projects', { params: { keyword } }),
  create: (data: any) => api.post('/projects', data),
  update: (id: number, data: any) => api.put(`/projects/${id}`, data),
  delete: (id: number) => api.delete(`/projects/${id}`),
}

// Table APIs
export const tableApi = {
  list: (params?: { projectId?: number; tableName?: string }) => api.get('/tables', { params }),
  getById: (id: number) => api.get(`/tables/${id}`),
  update: (id: number, tableComment: string) => api.put(`/tables/${id}`, { tableComment }),
  delete: (id: number) => api.delete(`/tables/${id}`),
}

// Field APIs
export const fieldApi = {
  listByTable: (tableId: number) => api.get(`/tables/${tableId}/fields`),
  update: (id: number, fieldComment: string) => api.put(`/fields/${id}`, { fieldComment }),
  usageScenarios: (fieldId: number) => api.get(`/fields/${fieldId}/usage-scenarios`),
}

// Relation APIs
export const relationApi = {
  getRelations: (tableId: number, direction: string = 'all') =>
    api.get(`/relations/${tableId}`, { params: { direction } }),
}

// Query APIs
export const queryApi = {
  searchFields: (keyword?: string, projectId?: number) =>
    api.get('/query/search-fields', { params: { keyword, projectId } }),
  findPath: (data: any) => api.post('/query/find-path', data),
}

export default api
```

- [ ] **Step 8: 安装依赖**

```bash
cd data-map-frontend && npm install
```

- [ ] **Step 9: Commit**

---

## Task 11: 表基本信息管理页

**Files:**
- Create: `data-map-frontend/src/pages/TableInfoManage/index.tsx`

- [ ] **Step 1: 实现 TableInfoManage 页面**

`pages/TableInfoManage/index.tsx`:

```tsx
import { useEffect, useState } from 'react'
import { Table, Input, Space, Button, Modal, message, Form, Popconfirm } from 'antd'
import { useNavigate } from 'react-router-dom'
import { tableApi, projectApi } from '../../services/api'

export default function TableInfoManage() {
  const navigate = useNavigate()
  const [data, setData] = useState<any[]>([])
  const [loading, setLoading] = useState(false)
  const [projectKeyword, setProjectKeyword] = useState('')
  const [tableNameKeyword, setTableNameKeyword] = useState('')
  const [editModalOpen, setEditModalOpen] = useState(false)
  const [editingRecord, setEditingRecord] = useState<any>(null)
  const [form] = Form.useForm()

  const fetchData = async () => {
    setLoading(true)
    try {
      const res: any = await tableApi.list({ tableName: tableNameKeyword || undefined })
      const tables = res.data || []
      // 如果需要按项目筛选
      if (projectKeyword) {
        const pres: any = await projectApi.list(projectKeyword)
        const projectIds = (pres.data || []).map((p: any) => p.id)
        setData(tables.filter((t: any) => projectIds.includes(t.projectId)))
      } else {
        setData(tables)
      }
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => { fetchData() }, [])

  const handleSearch = () => fetchData()

  const handleEdit = (record: any) => {
    setEditingRecord(record)
    form.setFieldsValue({ tableComment: record.tableComment })
    setEditModalOpen(true)
  }

  const handleDelete = async (id: number) => {
    await tableApi.delete(id)
    message.success('删除成功')
    fetchData()
  }

  const handleEditOk = async () => {
    const values = await form.validateFields()
    await tableApi.update(editingRecord.id, values.tableComment)
    message.success('修改成功')
    setEditModalOpen(false)
    fetchData()
  }

  const columns = [
    { title: '项目ID', dataIndex: 'projectId', key: 'projectId', width: 80 },
    { title: '表名', dataIndex: 'tableName', key: 'tableName', width: 200 },
    { title: '表含义', dataIndex: 'tableComment', key: 'tableComment' },
    {
      title: '操作', key: 'action', width: 320,
      render: (_: any, record: any) => (
        <Space>
          <Button type="link" onClick={() => navigate(`/tables/${record.id}`)}>查看</Button>
          <Button type="link" onClick={() => handleEdit(record)}>编辑</Button>
          <Popconfirm title="确定删除?" onConfirm={() => handleDelete(record.id)}>
            <Button type="link" danger>删除</Button>
          </Popconfirm>
          <Button type="link" onClick={() => navigate(`/tables/${record.id}/relations`)}>关联关系</Button>
        </Space>
      ),
    },
  ]

  return (
    <div>
      <Space style={{ marginBottom: 16 }}>
        <Input.Search
          placeholder="项目名称（模糊搜索）"
          value={projectKeyword}
          onChange={(e) => setProjectKeyword(e.target.value)}
          onSearch={handleSearch}
          style={{ width: 220 }}
        />
        <Input.Search
          placeholder="表名（模糊搜索）"
          value={tableNameKeyword}
          onChange={(e) => setTableNameKeyword(e.target.value)}
          onSearch={handleSearch}
          style={{ width: 220 }}
        />
      </Space>
      <Table columns={columns} dataSource={data} rowKey="id" loading={loading} />

      <Modal
        title="编辑表含义"
        open={editModalOpen}
        onOk={handleEditOk}
        onCancel={() => setEditModalOpen(false)}
      >
        <Form form={form}>
          <Form.Item name="tableComment" label="表含义" rules={[{ required: true }]}>
            <Input />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  )
}
```

- [ ] **Step 2: Commit**

---

## Task 12: 表详情页 (字段 + 使用场景)

**Files:**
- Create: `data-map-frontend/src/pages/TableInfoManage/TableDetail.tsx`

- [ ] **Step 1: 实现 TableDetail 页面**

`pages/TableInfoManage/TableDetail.tsx`:

```tsx
import { useEffect, useState } from 'react'
import { useParams, useNavigate } from 'react-router-dom'
import { Table, Button, Modal, Input, Form, message, Space, Tag } from 'antd'
import { ArrowLeftOutlined } from '@ant-design/icons'
import { fieldApi, tableApi } from '../../services/api'

export default function TableDetail() {
  const { id } = useParams<{ id: string }>()
  const navigate = useNavigate()
  const [tableInfo, setTableInfo] = useState<any>(null)
  const [fields, setFields] = useState<any[]>([])
  const [loading, setLoading] = useState(false)
  const [editFieldModalOpen, setEditFieldModalOpen] = useState(false)
  const [editingField, setEditingField] = useState<any>(null)
  const [form] = Form.useForm()
  const [expandedRows, setExpandedRows] = useState<Set<number>>(new Set())
  const [scenarios, setScenarios] = useState<Record<number, any[]>>({})

  const fetchData = async () => {
    setLoading(true)
    try {
      const res: any = await tableApi.getById(Number(id))
      setTableInfo(res.data)
      const fres: any = await fieldApi.listByTable(Number(id))
      setFields(fres.data || [])
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => { fetchData() }, [id])

  const handleToggleExpand = async (fieldId: number) => {
    const newExpanded = new Set(expandedRows)
    if (newExpanded.has(fieldId)) {
      newExpanded.delete(fieldId)
    } else {
      newExpanded.add(fieldId)
      if (!scenarios[fieldId]) {
        const res: any = await fieldApi.usageScenarios(fieldId)
        setScenarios((prev: any) => ({ ...prev, [fieldId]: res.data || [] }))
      }
    }
    setExpandedRows(newExpanded)
  }

  const handleEditField = (record: any) => {
    setEditingField(record)
    form.setFieldsValue({ fieldComment: record.fieldComment })
    setEditFieldModalOpen(true)
  }

  const handleEditFieldOk = async () => {
    const values = await form.validateFields()
    await fieldApi.update(editingField.id, values.fieldComment)
    message.success('修改成功')
    setEditFieldModalOpen(false)
    fetchData()
  }

  const columns = [
    { title: '字段名', dataIndex: 'fieldName', key: 'fieldName', width: 160 },
    { title: '注释', dataIndex: 'fieldComment', key: 'fieldComment' },
    { title: '类型', dataIndex: 'fieldType', key: 'fieldType', width: 100 },
    {
      title: '手工修改', dataIndex: 'fieldCommentManual', key: 'fieldCommentManual', width: 80,
      render: (v: number) => v === 1 ? <Tag color="orange">是</Tag> : <Tag>否</Tag>,
    },
    {
      title: '操作', key: 'action', width: 160,
      render: (_: any, record: any) => (
        <Space>
          <Button type="link" onClick={() => handleToggleExpand(record.id)}>
            {expandedRows.has(record.id) ? '收起' : '下钻'}
          </Button>
          <Button type="link" onClick={() => handleEditField(record)}>编辑</Button>
        </Space>
      ),
    },
  ]

  if (!tableInfo) return <div>加载中...</div>

  return (
    <div>
      <Space style={{ marginBottom: 16 }}>
        <Button icon={<ArrowLeftOutlined />} onClick={() => navigate('/tables/list')}>返回</Button>
        <span style={{ fontSize: 16, fontWeight: 'bold' }}>
          {tableInfo.tableName} — {tableInfo.tableComment}
        </span>
      </Space>
      <Table
        columns={columns}
        dataSource={fields}
        rowKey="id"
        loading={loading}
        expandable={{
          expandedRowRender: (record) => {
            const list = scenarios[record.id] || []
            if (list.length === 0) return <div style={{ padding: 16, color: '#999' }}>无使用场景数据</div>
            return (
              <Table
                dataSource={list}
                rowKey="id"
                pagination={false}
                size="small"
                columns={[
                  { title: '操作类型', dataIndex: 'operationType', key: 'operationType', width: 80 },
                  { title: '场景描述', dataIndex: 'scenarioDescription', key: 'scenarioDescription' },
                  { title: '方法名称', dataIndex: 'methodName', key: 'methodName' },
                  { title: '写入来源表', dataIndex: 'sourceTableName', key: 'sourceTableName' },
                  { title: '写入来源接口', dataIndex: 'sourceApiName', key: 'sourceApiName' },
                ]}
              />
            )
          },
          expandedRowKeys: Array.from(expandedRows),
          showExpandColumn: false,
        }}
      />

      <Modal
        title="编辑字段注释"
        open={editFieldModalOpen}
        onOk={handleEditFieldOk}
        onCancel={() => setEditFieldModalOpen(false)}
      >
        <Form form={form}>
          <Form.Item name="fieldComment" label="字段注释" rules={[{ required: true }]}>
            <Input />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  )
}
```

- [ ] **Step 2: Commit**

---

## Task 13: 表关联关系图页

**Files:**
- Create: `data-map-frontend/src/pages/TableRelation/index.tsx`

- [ ] **Step 1: 实现 TableRelation 页面 (AntV G6 + 明细表)**

`pages/TableRelation/index.tsx`:

```tsx
import { useEffect, useRef, useState } from 'react'
import { useParams, useNavigate } from 'react-router-dom'
import { Button, Space, Radio, Table } from 'antd'
import { ArrowLeftOutlined } from '@ant-design/icons'
import { Graph } from '@antv/g6'
import { relationApi, tableApi } from '../../services/api'

export default function TableRelation() {
  const { id } = useParams<{ id: string }>()
  const navigate = useNavigate()
  const containerRef = useRef<HTMLDivElement>(null)
  const graphRef = useRef<Graph | null>(null)
  const [tableInfo, setTableInfo] = useState<any>(null)
  const [direction, setDirection] = useState<string>('all')
  const [details, setDetails] = useState<any[]>([])

  const fetchData = async (dir: string) => {
    try {
      const tres: any = await tableApi.getById(Number(id))
      setTableInfo(tres.data)
      const res: any = await relationApi.getRelations(Number(id), dir)
      const vo = res.data
      setDetails(vo.details || [])

      if (graphRef.current) {
        const data = {
          nodes: (vo.nodes || []).map((n: any) => ({
            id: String(n.id),
            data: { label: n.label },
            style: n.id === Number(id) ? { fill: '#5B8FF9' } : {},
          })),
          edges: (vo.edges || []).map((e: any, idx: number) => ({
            id: `edge-${idx}`,
            source: String(e.source),
            target: String(e.target),
            data: { label: e.sourceField + '=' + e.targetField },
          })),
        }
        graphRef.current.setData(data)
        graphRef.current.render()
      }
    } catch (e) {
      console.error(e)
    }
  }

  useEffect(() => {
    fetchData(direction)
  }, [id])

  useEffect(() => {
    if (!containerRef.current) return
    const graph = new Graph({
      container: containerRef.current,
      width: containerRef.current.clientWidth,
      height: 400,
      data: { nodes: [], edges: [] },
      layout: { type: 'dagre', rankdir: 'LR' },
      node: {
        style: {
          size: 50,
          labelText: (d: any) => d.data?.label || d.id,
          labelPlacement: 'bottom',
        },
      },
      edge: {
        style: {
          endArrow: true,
          labelText: (d: any) => d.data?.label || '',
        },
      },
      behaviors: ['drag-canvas', 'zoom-canvas', 'drag-element'],
    })
    graphRef.current = graph
    graph.render()

    return () => { graph.destroy() }
  }, [])

  const handleDirectionChange = (val: string) => {
    setDirection(val)
    fetchData(val)
  }

  const detailColumns = [
    { title: '左表', dataIndex: 'sourceTable', key: 'sourceTable' },
    { title: '左字段', dataIndex: 'sourceField', key: 'sourceField' },
    { title: '右表', dataIndex: 'targetTable', key: 'targetTable' },
    { title: '右字段', dataIndex: 'targetField', key: 'targetField' },
    { title: '关系类型', dataIndex: 'relationType', key: 'relationType' },
    { title: '来源方法', dataIndex: 'methodSignature', key: 'methodSignature' },
  ]

  return (
    <div>
      <Space style={{ marginBottom: 16 }}>
        <Button icon={<ArrowLeftOutlined />} onClick={() => navigate('/tables/list')}>返回</Button>
        <span style={{ fontSize: 16, fontWeight: 'bold' }}>
          {tableInfo?.tableName} — 关联关系图
        </span>
      </Space>
      <Space style={{ marginBottom: 12 }}>
        <Radio.Group value={direction} onChange={(e) => handleDirectionChange(e.target.value)}>
          <Radio.Button value="all">全部</Radio.Button>
          <Radio.Button value="upstream">仅上游</Radio.Button>
          <Radio.Button value="downstream">仅下游</Radio.Button>
        </Radio.Group>
      </Space>
      <div ref={containerRef} style={{ border: '1px solid #f0f0f0', borderRadius: 8, marginBottom: 16 }} />
      <Table columns={detailColumns} dataSource={details} rowKey={(r: any, idx: number) => `${r.sourceField}-${r.targetField}-${idx}`} />
    </div>
  )
}
```

- [ ] **Step 2: Commit**

---

## Task 14: 表逻辑信息查询页

**Files:**
- Create: `data-map-frontend/src/pages/TableLogicQuery/index.tsx`

- [ ] **Step 1: 实现 TableLogicQuery (字段搜索 + 路径查询)**

`pages/TableLogicQuery/index.tsx`:

```tsx
import { useState } from 'react'
import { Tabs, Input, Table, Button, Form, Select, Space, message, Tag } from 'antd'
import { queryApi, projectApi } from '../../services/api'

function FieldSearch() {
  const [keyword, setKeyword] = useState('')
  const [data, setData] = useState<any[]>([])
  const [loading, setLoading] = useState(false)

  const handleSearch = async () => {
    if (!keyword.trim()) return
    setLoading(true)
    try {
      const res: any = await queryApi.searchFields(keyword)
      setData(res.data || [])
    } finally {
      setLoading(false)
    }
  }

  const columns = [
    { title: '项目', dataIndex: 'projectName', key: 'projectName', width: 150 },
    { title: '表名', dataIndex: 'tableName', key: 'tableName', width: 180 },
    { title: '字段名', dataIndex: 'fieldName', key: 'fieldName', width: 150 },
    { title: '备注', dataIndex: 'fieldComment', key: 'fieldComment' },
  ]

  return (
    <div>
      <Space style={{ marginBottom: 16 }}>
        <Input.Search
          placeholder="输入业务关键词搜索字段"
          value={keyword}
          onChange={(e) => setKeyword(e.target.value)}
          onSearch={handleSearch}
          style={{ width: 320 }}
        />
      </Space>
      <Table columns={columns} dataSource={data} rowKey="fieldName" loading={loading} />
    </div>
  )
}

function PathQuery() {
  const [projects, setProjects] = useState<any[]>([])
  const [tables, setTables] = useState<any[]>([])
  const [projectId, setProjectId] = useState<number | undefined>()
  const [startTableId, setStartTableId] = useState<number | undefined>()
  const [targetTableId, setTargetTableId] = useState<number | undefined>()
  const [startFieldName, setStartFieldName] = useState('')
  const [targetFieldName, setTargetFieldName] = useState('')
  const [paths, setPaths] = useState<any[]>([])
  const [loading, setLoading] = useState(false)

  const loadProjects = async (keyword?: string) => {
    const res: any = await projectApi.list(keyword)
    setProjects(res.data || [])
  }

  const loadTables = async (pid: number) => {
    const { default: api } = await import('../../services/api')
    const { tableApi } = await import('../../services/api')
    const res: any = await tableApi.list({ projectId: pid })
    setTables(res.data || [])
  }

  const handleProjectChange = (val: number) => {
    setProjectId(val)
    loadTables(val)
  }

  const handleSearch = async () => {
    if (!projectId || !startTableId || !targetTableId) {
      message.warning('请填写完整信息')
      return
    }
    setLoading(true)
    try {
      const res: any = await queryApi.findPath({
        projectId,
        startTableId,
        startFieldName,
        targetTableId,
        targetFieldName,
      })
      setPaths(res.data?.paths || [])
    } finally {
      setLoading(false)
    }
  }

  return (
    <div>
      <Space style={{ marginBottom: 16 }} size="middle" wrap>
        <Select
          placeholder="选择项目"
          style={{ width: 180 }}
          showSearch
          onSearch={(v: string) => loadProjects(v)}
          onFocus={() => loadProjects()}
          onChange={handleProjectChange}
          value={projectId}
          filterOption={false}
          options={projects.map((p: any) => ({ label: p.appName, value: p.id }))}
        />
        <Select
          placeholder="起始表"
          style={{ width: 180 }}
          showSearch
          value={startTableId}
          onChange={(v: number) => setStartTableId(v)}
          filterOption={false}
          options={tables.map((t: any) => ({ label: t.tableName, value: t.id }))}
        />
        <Input
          placeholder="起始字段"
          value={startFieldName}
          onChange={(e) => setStartFieldName(e.target.value)}
          style={{ width: 140 }}
        />
        <Select
          placeholder="目标表"
          style={{ width: 180 }}
          showSearch
          value={targetTableId}
          onChange={(v: number) => setTargetTableId(v)}
          options={tables.map((t: any) => ({ label: t.tableName, value: t.id }))}
        />
        <Input
          placeholder="目标字段"
          value={targetFieldName}
          onChange={(e) => setTargetFieldName(e.target.value)}
          style={{ width: 140 }}
        />
        <Button type="primary" onClick={handleSearch} loading={loading}>查询路径</Button>
      </Space>

      {paths.map((path: any, idx: number) => (
        <div key={idx} style={{ marginBottom: 16, padding: 12, border: '1px solid #f0f0f0', borderRadius: 8 }}>
          <Tag color={idx === 0 ? 'blue' : 'default'}>
            {idx === 0 ? '最短路径' : `路径 ${idx + 1}`} (长度: {path.length})
          </Tag>
          <div style={{ marginTop: 8 }}>
            {path.nodes?.join(' → ')}
          </div>
          <div style={{ marginTop: 4, color: '#666', fontSize: 12 }}>
            {path.edges?.map((e: any, ei: number) => (
              <span key={ei}>
                {ei > 0 && ' | '}
                {e.from}.{e.joinField}={e.to}
              </span>
            ))}
          </div>
        </div>
      ))}
      {paths.length === 0 && !loading && (
        <div style={{ color: '#999' }}>暂无结果，请选择项目、起始表和目标表后查询</div>
      )}
    </div>
  )
}

export default function TableLogicQuery() {
  return (
    <Tabs
      items={[
        { key: 'field', label: '按业务含义搜字段', children: <FieldSearch /> },
        { key: 'path', label: '路径查询', children: <PathQuery /> },
      ]}
    />
  )
}
```

- [ ] **Step 2: Commit**

---

## Task 15: 端到端验证

- [ ] **Step 1: 启动后端**

```bash
cd data-map-backend
JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk1.8.0_371.jdk/Contents/Home mvn spring-boot:run
```

验证：`curl http://localhost:8080/api/projects` 返回 `{"code":0,"message":"success","data":[]}`

- [ ] **Step 2: 启动前端**

```bash
cd data-map-frontend
npm run dev
```

验证：浏览器打开 `http://localhost:5173` 能看到数据地图界面

- [ ] **Step 3: 功能验证**

- 访问 http://localhost:5173/tables/list → 表列表页（空数据）
- 访问 http://localhost:5173/tables/query → 逻辑查询页
- POST 测试扫描数据入库 → 验证表数据展示
- 验证路径查询功能

- [ ] **Step 4: Commit**

---
