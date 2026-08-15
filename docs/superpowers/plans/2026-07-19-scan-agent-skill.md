# 扫描 Agent Claude Code Skill — 实现计划

> **For agentic workers:** Use superpowers:subagent-driven-development to implement this plan.

**Goal:** 创建 Claude Code Skill (`scan-project`)，实现 6 步扫描流程：解析 Java 项目的 MyBatis XML Mapper 和 JOOQ DSL 代码，提取表结构、关联关系、字段使用场景，生成 JSON 并 POST 到后端 API。

**Architecture:** 单一 Skill 文件驱动 Claude 执行扫描。Claude 利用代码阅读能力语义解析源码，按 6 步流程逐步提取元数据，最终组装 JSON 提交。

**Tech Stack:** Claude Code Skill (Markdown), Bash (curl for API submission)

## Global Constraints

- Skill 文件放在 `.claude/skills/scan-project.md`
- 支持 MyBatis/MyBatis-Plus XML Mapper + JOOQ DSL 两种 ORM
- 通用字段排除：pk, id, created_at, create_time, updated_at, update_time, deleted_at, delete_time, is_deleted, create_user, create_by, update_user, update_by, delete_user, delete_by, version, tenant_id
- API: POST http://localhost:8080/api/scan/result
- 支持 FULL 和 TABLE 两种扫描模式

---

## Task 1: 创建 scan-project Skill 文件

**Files:**
- Create: `.claude/skills/scan-project.md`

- [ ] **Step 1: 创建 Skill 文件目录**

```bash
mkdir -p /Applications/data-map/.claude/skills
```

- [ ] **Step 2: 编写 Skill 内容**

文件 `.claude/skills/scan-project.md` 包含：
- Skill 元数据（name, description, trigger）
- 6 步扫描流程详细指令
- MyBatis XML 解析规则
- JOOQ DSL 解析规则
- JSON 组装模板
- API 提交命令

- [ ] **Step 3: 验证 Skill 可被加载**

```bash
ls -la .claude/skills/scan-project.md
```

- [ ] **Step 4: 测试 — 用样例项目验证扫描流程**

创建一个小型样例项目（含 MyBatis XML Mapper 和 JOOQ DSL），触发 `/scan-project` 验证是否能正确提取元数据并提交到后端。

---
