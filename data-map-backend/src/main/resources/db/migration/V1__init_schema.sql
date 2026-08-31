-- ============================================================
-- data_map 数据库基础表结构 (V1)
-- 由实体类还原：Project / TableInfo / TableField / TableRelation
--              / ScanRecord / FieldUsageScenario
-- 执行顺序：V1 本文件(建基础表) → V2__scanner_call_chain.sql(ALTER 扩展)
-- V2 之后 field_usage_scenario / scan_record 的列将等于当前实体类定义
-- ============================================================

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ----------------------------
-- project  项目
-- ----------------------------
DROP TABLE IF EXISTS `project`;
CREATE TABLE `project` (
  `id`             BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  `app_name`       VARCHAR(255)     NULL COMMENT '应用名称',
  `git_repo_url`   VARCHAR(512)     NULL COMMENT 'Git 仓库地址',
  `git_local_path` VARCHAR(512)     NULL COMMENT 'Git 本地路径',
  `description`    VARCHAR(512)     NULL COMMENT '描述',
  `status`         INT          NOT NULL DEFAULT 0 COMMENT '状态',
  `created_at`     DATETIME         NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at`     DATETIME         NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '项目';

-- ----------------------------
-- table_info  表信息
-- ----------------------------
DROP TABLE IF EXISTS `table_info`;
CREATE TABLE `table_info` (
  `id`                   BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  `project_id`           BIGINT           NULL COMMENT '项目 ID',
  `table_name`           VARCHAR(255)     NULL COMMENT '表名',
  `table_comment`        VARCHAR(512)     NULL COMMENT '表注释',
  `table_comment_manual` INT              NULL DEFAULT 0 COMMENT '表注释是否手工维护: 0否 1是',
  `schema_name`          VARCHAR(128)     NULL COMMENT 'schema 名',
  `db_type`              VARCHAR(32)      NULL COMMENT '数据库类型',
  `status`               INT          NOT NULL DEFAULT 0 COMMENT '状态',
  `created_at`           DATETIME         NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at`           DATETIME         NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_project_id` (`project_id`),
  KEY `idx_table_name` (`table_name`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '表信息';

-- ----------------------------
-- table_field  字段信息
-- ----------------------------
DROP TABLE IF EXISTS `table_field`;
CREATE TABLE `table_field` (
  `id`                  BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  `table_id`            BIGINT           NULL COMMENT '表 ID',
  `field_name`          VARCHAR(255)     NULL COMMENT '字段名',
  `field_comment`       VARCHAR(512)     NULL COMMENT '字段注释',
  `field_type`          VARCHAR(128)     NULL COMMENT '字段类型',
  `is_pk`               INT              NULL DEFAULT 0 COMMENT '是否主键: 0否 1是',
  `is_business_field`   INT              NULL DEFAULT 0 COMMENT '是否业务字段: 0否 1是',
  `field_comment_manual` INT             NULL DEFAULT 0 COMMENT '字段注释是否手工维护: 0否 1是',
  `status`              INT          NOT NULL DEFAULT 0 COMMENT '状态',
  `created_at`          DATETIME         NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at`          DATETIME         NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_table_id` (`table_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '字段信息';

-- ----------------------------
-- table_relation  表关系
-- ----------------------------
DROP TABLE IF EXISTS `table_relation`;
CREATE TABLE `table_relation` (
  `id`                BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  `project_id`         BIGINT           NULL COMMENT '项目 ID',
  `source_table_id`    BIGINT           NULL COMMENT '源表 ID',
  `source_field_name`  VARCHAR(255)     NULL COMMENT '源字段名',
  `target_table_id`    BIGINT           NULL COMMENT '目标表 ID',
  `target_field_name`  VARCHAR(255)     NULL COMMENT '目标字段名',
  `relation_type`      VARCHAR(32)      NULL COMMENT '关系类型',
  `method_signature`  VARCHAR(512)     NULL COMMENT '方法签名',
  `status`             INT          NOT NULL DEFAULT 0 COMMENT '状态',
  `created_at`         DATETIME         NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_project_id` (`project_id`),
  KEY `idx_source_table_id` (`source_table_id`),
  KEY `idx_target_table_id` (`target_table_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '表关系';

-- ----------------------------
-- scan_record  扫描记录
-- (V2 会对 scan_type 列做 MODIFY VARCHAR(16))
-- ----------------------------
DROP TABLE IF EXISTS `scan_record`;
CREATE TABLE `scan_record` (
  `id`           BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  `project_id`   BIGINT           NULL COMMENT '项目 ID',
  `scan_type`    VARCHAR(16)      NULL COMMENT '扫描类型: FULL/TABLE/DIFF',
  `table_names`  VARCHAR(1024)    NULL COMMENT '扫描表名(逗号分隔)',
  `scan_status`  VARCHAR(32)      NULL COMMENT '扫描状态',
  `status`       INT          NOT NULL DEFAULT 0 COMMENT '状态',
  `error_msg`    TEXT             NULL COMMENT '错误信息',
  `started_at`   DATETIME         NULL COMMENT '开始时间',
  `completed_at` DATETIME         NULL COMMENT '完成时间',
  PRIMARY KEY (`id`),
  KEY `idx_project_id` (`project_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '扫描记录';

-- ----------------------------
-- field_usage_scenario  字段使用场景
-- (V2 会新增 call_chain/entry_info/method_description/description_source 列
--   并 MODIFY operation_type VARCHAR(16))
-- ----------------------------
DROP TABLE IF EXISTS `field_usage_scenario`;
CREATE TABLE `field_usage_scenario` (
  `id`                  BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  `field_id`            BIGINT           NULL COMMENT '字段 ID',
  `table_id`            BIGINT           NULL COMMENT '表 ID',
  `operation_type`      VARCHAR(16)      NULL COMMENT '操作类型: WRITE/UPDATE/READ/DELETE/UNRESOLVED',
  `scenario_description` VARCHAR(1024)   NULL COMMENT '场景描述',
  `method_name`         VARCHAR(512)     NULL COMMENT '方法名',
  `source_table_name`   VARCHAR(255)     NULL COMMENT '来源表名',
  `source_api_name`     VARCHAR(512)     NULL COMMENT '来源 API 名',
  `status`              INT          NOT NULL DEFAULT 0 COMMENT '状态',
  `created_at`          DATETIME         NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_field_id` (`field_id`),
  KEY `idx_table_id` (`table_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '字段使用场景';

SET FOREIGN_KEY_CHECKS = 1;
