# HR Humanized Records and Fields Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace raw HR audit codes, UTC date values, internal IDs, and English missing-field keys with structured, localized, actionable employee experiences.

**Architecture:** Extend stable backend DTOs at the source boundary, normalize employee date-only Map values before JSON serialization, and keep frontend presentation rules in the existing HR field configuration modules. UI components consume structured data and never infer or expose sensitive before/after values.

**Tech Stack:** Java 17, Spring Boot, Jackson, Vue 2, Element UI, Node contract tests, JUnit 5.

---

### Task 1: Structure onboarding operation logs and suppress no-op logs

**Files:**
- Modify: `erp-modules/erp-system/src/main/java/com/erp/system/domain/vo/HrOnboardingDetailVo.java`
- Modify: `erp-modules/erp-system/src/main/java/com/erp/system/service/impl/HrOnboardingServiceImpl.java`
- Modify: `erp-modules/erp-system/src/test/java/com/erp/system/service/impl/HrOnboardingServiceImplTest.java`

- [ ] Add failing tests proving `OperationLogVo` exposes `fromStatus`, `toStatus`, and a safe `List<String> changedFieldKeys`, and proving a version-only update does not invoke the mapper update or insert a log.
- [ ] Run `mvn -pl erp-modules/erp-system -am -Dtest=HrOnboardingServiceImplTest -Dsurefire.failIfNoSpecifiedTests=false test` and confirm the new assertions fail.
- [ ] Populate structured log properties in `toLogVo`; split the stored comma list while dropping blank keys.
- [ ] Return the current scoped detail before mapper update when `applyPatch` returns an empty list.
- [ ] Re-run the targeted test and commit with `fix: structure hr onboarding operation logs`.

### Task 2: Normalize employee detail names and date-only values

**Files:**
- Modify: `erp-modules/erp-system/src/main/java/com/erp/system/domain/vo/HrEmployeeProfileVo.java`
- Modify: `erp-modules/erp-system/src/main/java/com/erp/system/service/impl/HrEmployeeProfileServiceImpl.java`
- Modify: `erp-modules/erp-system/src/test/java/com/erp/system/service/impl/HrEmployeeProfileServiceImplTest.java`

- [ ] Add failing tests using a fixed `Date` at `2026-06-15T16:00:00Z`; assert the `fields.entryDate` value is `2026-06-16`, `departmentName` is resolved, and `postNames` contains the resolved position name rather than IDs.
- [ ] Run the targeted service test and confirm RED.
- [ ] Add `departmentName` and `postNames` to the stable DTO.
- [ ] Convert registry `Date` values to `Asia/Shanghai` `yyyy-MM-dd` strings before placing them in `fields` and `profile` maps.
- [ ] Resolve department and post display names from the already scoped/derived user object and registry.
- [ ] Re-run targeted tests and commit with `fix: normalize hr employee display values`.

### Task 3: Localize and group employee fields

**Files:**
- Modify: `erp-ui/src/views/hr/components/hrFieldConfig.js`
- Modify: `erp-ui/src/views/hr/components/HrEmployeeList.vue`
- Modify: `erp-ui/src/views/hr/components/HrProfileDetailDrawer.vue`
- Modify: `erp-ui/test/hrEmployeeMasterFields.test.js`
- Modify: `erp-ui/test/hrWorkbenchUx.test.js`

- [ ] Add failing executable tests for `profileFieldLabel`, `formatProfileDisplayValue`, and `groupMissingProfileFields`; cover `sex=0`, ISO entry dates, `deptId/postIds` rejection, Chinese labels, and derived/system grouping.
- [ ] Add source contracts requiring grouped missing cards, counts, edit action, “系统待完善”, and resolved organization/post fields.
- [ ] Run both Node tests and confirm RED.
- [ ] Build the label index from `DETAIL_GROUPS`, rename `社保户籍` to `社保公积金`, and replace numeric department/post fields with `departmentName/postNames`.
- [ ] Format dates, sex, foreign-national flags, arrays, and empty values through one pure formatter used by `HrEmployeeList`.
- [ ] Replace `missingFields.join("、")` with grouped actionable and system-derived sections; expose edit from the actionable section.
- [ ] Re-run tests and commit with `feat: humanize hr employee profile details`.

### Task 4: Present onboarding logs as business actions

**Files:**
- Modify: `erp-ui/src/views/hr/onboarding/onboardingFieldConfig.js`
- Modify: `erp-ui/src/views/hr/onboarding/components/HrOnboardingDetailPane.vue`
- Modify: `erp-ui/test/hrOnboardingWorkbench.test.js`

- [ ] Add failing tests for `presentOnboardingLog`: localized operation type/status/field labels, GMT+8 compact time, safe sensitive-field wording, and blank-log filtering.
- [ ] Require newest-first display, initial limit 5, expandable history, and a disabled-confirm explanation.
- [ ] Run the onboarding workbench test and confirm RED.
- [ ] Implement the pure presenter and render localized log cards/timeline entries without raw backend summaries.
- [ ] Show only five recent records initially, add a keyboard-accessible expand/collapse button, and explain the disabled confirmation action.
- [ ] Re-run targeted tests and commit with `feat: humanize hr onboarding activity`.

### Task 5: Regression and real-page QA

**Files:**
- Modify: `design-qa.md`
- Create: `docs/audit-screenshots/hr-humanized-records-20260712/*.png` (QA evidence, do not stage)

- [ ] Run backend targeted tests for onboarding and employee services, then `mvn -pl erp-modules/erp-system -am test` with the Colima Testcontainers socket.
- [ ] Run desktop HR Node tests, mobile access boundary tests, and `npm --prefix erp-ui run build:prod` with the supported Node runtime.
- [ ] In the real signed-in browser, inspect the same onboarding record and employee `userId=940`; verify no raw codes, ISO date-only values, IDs, arrays, or English missing keys remain.
- [ ] Capture onboarding logs plus employee overview and organization tabs at desktop widths; update `design-qa.md` with `final result: passed` only when P0/P1/P2 issues are closed.
- [ ] Run `git diff --check`, stage only source/tests/report, and commit with `test: verify humanized hr records`.
