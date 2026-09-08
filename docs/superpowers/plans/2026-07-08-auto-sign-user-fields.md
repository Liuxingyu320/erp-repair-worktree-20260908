# 自动签约用户字段补充 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在用户员工档案中补齐 `合同类型` 和 `社保类型`，让后续自动签约可以稳定按合同类型、社保类型、职级匹配入职签约包。

**Architecture:** 字段继续归属 `sys_user_profile`，通过 `SysUserProfile`、MyBatis XML、Excel 注解和用户管理前端表单贯通。当前只补字段传递链路，不实现自动发送任务。

**Tech Stack:** Java, MyBatis XML, JUnit 5, Vue 2, Element UI, Node source tests.

---

### Task 1: Backend Field Contract

**Files:**
- Modify: `erp-api/erp-api-system/src/main/java/com/erp/system/api/domain/SysUserProfile.java`
- Modify: `erp-api/erp-api-system/src/main/java/com/erp/system/api/domain/SysUser.java`
- Modify: `erp-modules/erp-system/src/main/resources/mapper/system/SysUserMapper.xml`
- Modify: `erp-modules/erp-system/src/main/resources/mapper/system/SysUserProfileMapper.xml`
- Test: `erp-modules/erp-system/src/test/java/com/erp/system/mapper/SysUserMapperSourceTest.java`
- Create: `erp-modules/erp-system/src/test/java/com/erp/system/api/domain/SysUserProfileContractTest.java`

- [x] Add failing tests for domain getters/setters, Excel export/import annotation targets, and mapper XML containing `contract_type` and `social_type`.
- [x] Run the focused Maven tests and confirm they fail because the fields are missing.
- [x] Add `contractType` and `socialType` to the domain, Excel annotations, and MyBatis result/select/insert/update mappings.
- [x] Re-run the focused Maven tests and confirm they pass.

### Task 2: SQL Schema

**Files:**
- Modify: `sql/erp_user_employee_profile_20260706.sql`
- Modify: `docker/mysql/db/erp_user_employee_profile_20260706.sql` if the docker copy exists or needs to be created for deployment consistency.

- [x] Add `contract_type varchar(32) DEFAULT '' COMMENT '合同类型'`.
- [x] Add `social_type varchar(32) DEFAULT '' COMMENT '社保类型'`.
- [x] Keep `social_security_location` as “社保缴纳地” and do not reuse it as a yes/no flag.

### Task 3: Frontend User Management

**Files:**
- Modify: `erp-ui/src/views/system/user/index.vue`
- Modify: `erp-ui/src/views/system/user/view.vue`
- Test: `erp-ui/test/systemManagementUx.test.js`

- [x] Add failing source assertions for contract type and social type options, empty profile defaults, edit form controls, and detail view rows.
- [x] Run `node test/systemManagementUx.test.js` in `erp-ui` and confirm it fails.
- [x] Add select controls in the 任职合同 tab using fixed options `劳动合同/劳务合同` and `有社保/无社保`.
- [x] Add detail rows for the two fields.
- [x] Re-run the focused frontend test and confirm it passes.

### Task 4: Verification

- [x] Run backend focused tests for the system module field contract.
- [x] Run frontend focused source test.
- [x] Inspect `git diff` to verify only the intended files changed.
