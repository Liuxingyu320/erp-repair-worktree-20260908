# 签约包资料校验预览 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 扩展现有签约包批量预览，让系统从员工档案自动填充签约资料、判断资料是否完整，并给出 A1-A6/B1-B3 入职签约包匹配结果。

**Architecture:** 复用 `OA管理 -> 员工签约 -> 批量按岗位生成` 现有接口 `/oa/signPackage/batch/preview`。系统模块的签约候选员工接口增加员工档案字段，OA 批量服务基于候选员工资料填充预览行并校验，前端表格展示匹配结果和缺失原因。

**Tech Stack:** Java, MyBatis XML, JUnit 5, Vue 2, Element UI, Node source tests.

---

### Task 1: 候选员工档案字段

**Files:**
- Modify: `erp-api/erp-api-system/src/main/java/com/erp/system/api/domain/SignCandidateUser.java`
- Modify: `erp-modules/erp-system/src/main/resources/mapper/system/SysUserMapper.xml`
- Test: `erp-modules/erp-system/src/test/java/com/erp/system/mapper/SysUserMapperSourceTest.java`

- [x] Add failing tests requiring `selectSignCandidateUsers` to select and map `id_number`, `current_address`, `job_grade`, `contract_type`, `social_type`, `entry_date`, `contract_start_date`, and `contract_end_date`.
- [x] Run `mvn -Dtest=SysUserMapperSourceTest test` in `erp-modules/erp-system` and confirm it fails.
- [x] Add the fields and accessors to `SignCandidateUser`.
- [x] Join `sys_user_profile` in `selectSignCandidateUsers`, select the profile fields, and map them in `SignCandidateUserResult`.
- [x] Install `erp-api-system` locally and re-run the focused system test.

### Task 2: 后端预览校验和匹配

**Files:**
- Modify: `erp-modules/erp-oa/src/main/java/com/erp/oa/domain/dto/OaSignBatchPreviewRow.java`
- Modify: `erp-modules/erp-oa/src/main/java/com/erp/oa/service/impl/OaSignBatchServiceImpl.java`
- Test: `erp-modules/erp-oa/src/test/java/com/erp/oa/service/impl/OaSignBatchServiceImplTest.java`

- [x] Add failing tests that preview rows are auto-filled from candidate profile fields and return `B3 劳动合同有社保 7-8级`.
- [x] Add failing tests that unsupported `劳务合同 + 有社保` rows are not creatable and explain the reason.
- [x] Add `packageMatchCode` and `packageMatchName` to the preview row DTO.
- [x] Fill preview rows from `SignCandidateUser` profile fields before applying plan defaults.
- [x] For onboarding rows, require department, post, job grade, entry date, contract dates, contract type, and social type.
- [x] Map `合同类型 + 社保类型 + 职级` to A1-A6/B1-B3; mark invalid grades and unsupported combinations as not creatable.
- [x] Re-run the focused OA test.

### Task 3: 前端预览表格展示

**Files:**
- Modify: `erp-ui/src/views/oa/signPackage/index.vue`
- Test: `erp-ui/test/signPackageModule.test.js`

- [x] Add failing source assertions that the batch table displays matching result, contract/social type, job grade, entry date, and contract dates.
- [x] Run `node test/signPackageModule.test.js` in `erp-ui` and confirm it fails.
- [x] Add compact read-only table columns for matching result and auto-filled contract fields.
- [x] Re-run the focused frontend source test.

### Task 4: Verification

- [x] Run `mvn -DskipTests install` in `erp-api/erp-api-system`.
- [x] Run `mvn -Dtest=SysUserMapperSourceTest test` in `erp-modules/erp-system`.
- [x] Run `mvn -Dtest=OaSignBatchServiceImplTest test` in `erp-modules/erp-oa`.
- [x] Run `node test/signPackageModule.test.js` in `erp-ui`.
- [x] Check `git status --short` for touched files and avoid reverting unrelated workspace changes.
