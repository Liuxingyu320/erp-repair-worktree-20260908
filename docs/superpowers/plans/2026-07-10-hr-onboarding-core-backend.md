# HR Onboarding Core Backend Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build the independent HR onboarding record, server-side rules, transactional onboarding confirmation, import preview, employee-master hardening, permissions, and shared APIs required by desktop and mobile clients.

**Architecture:** Keep `SysUser + SysUserProfile` as the formal employee/account aggregate, but introduce a separate `hr_onboarding` aggregate that exists before an account. All state transitions, completeness, conflict handling, masking, and confirmation live in `erp-system`; desktop and mobile consume the same DTOs and never reimplement the state machine.

**Tech Stack:** Java 17, Spring Boot, MyBatis XML, MySQL 5.7/8.0-compatible SQL, Apache POI, JUnit 5, Mockito, existing ERP data-scope/log/security modules.

---

## Scope and execution order

This is plan 1 of 3. Complete it before:

1. `2026-07-10-hr-onboarding-desktop.md`
2. `2026-07-10-hr-onboarding-mobile.md`

The current HR controllers, pages, and tests are untracked working-tree files. Before editing, run `git status --short` and preserve their contents. Include planned HR files explicitly in each commit; do not overwrite or discard unrelated working-tree changes.

## File structure

Database:

- Create `sql/erp_user_hr_onboarding_20260710.sql` — forward-only onboarding schema, menu permissions, indexes, and system parameters.
- Create `docker/mysql/db/erp_user_hr_onboarding_20260710.sql` — byte-identical Docker migration copy.
- Modify `sql/erp_user_employee_profile_20260706.sql` — add `direct_supervisor_user_id` to fresh installs.
- Modify `docker/mysql/db/erp_user_employee_profile_20260706.sql` — mirror fresh-install change.
- Create `scripts/hr-onboarding-position-config-readiness.sql` — read-only go-live report for active post/category pairs without configuration.

Domain and DTOs:

- Create `erp-modules/erp-system/src/main/java/com/erp/system/domain/HrOnboarding.java`.
- Create `erp-modules/erp-system/src/main/java/com/erp/system/domain/HrOnboardingOperationLog.java`.
- Create `erp-modules/erp-system/src/main/java/com/erp/system/domain/HrOnboardingPositionConfig.java`.
- Create `erp-modules/erp-system/src/main/java/com/erp/system/domain/HrOnboardingImportBatch.java`.
- Create `erp-modules/erp-system/src/main/java/com/erp/system/domain/HrOnboardingImportRow.java`.
- Create `erp-modules/erp-system/src/main/java/com/erp/system/domain/HrSensitiveAccessLog.java`.
- Create `erp-modules/erp-system/src/main/java/com/erp/system/domain/vo/HrOnboardingQuery.java`.
- Create `erp-modules/erp-system/src/main/java/com/erp/system/domain/vo/HrOnboardingListVo.java`.
- Create `erp-modules/erp-system/src/main/java/com/erp/system/domain/vo/HrOnboardingDetailVo.java`.
- Create `erp-modules/erp-system/src/main/java/com/erp/system/domain/vo/HrOnboardingCompletionVo.java`.
- Create `erp-modules/erp-system/src/main/java/com/erp/system/domain/vo/HrOnboardingSummaryVo.java`.
- Create `erp-modules/erp-system/src/main/java/com/erp/system/domain/vo/HrOnboardingCreateRequest.java`.
- Create `erp-modules/erp-system/src/main/java/com/erp/system/domain/vo/HrOnboardingUpdateRequest.java`.
- Create `erp-modules/erp-system/src/main/java/com/erp/system/domain/vo/HrOnboardingVersionRequest.java`.
- Create `erp-modules/erp-system/src/main/java/com/erp/system/domain/vo/HrOnboardingCancelRequest.java`.
- Create `erp-modules/erp-system/src/main/java/com/erp/system/domain/vo/HrOnboardingConflictVo.java`.
- Create `erp-modules/erp-system/src/main/java/com/erp/system/exception/HrOnboardingValidationException.java`.
- Create `erp-modules/erp-system/src/main/java/com/erp/system/domain/vo/HrOnboardingConfirmRequest.java`.
- Create `erp-modules/erp-system/src/main/java/com/erp/system/domain/vo/HrOnboardingConfirmResult.java`.
- Create `erp-modules/erp-system/src/main/java/com/erp/system/domain/vo/HrOnboardingImportPreviewVo.java`.
- Create `erp-modules/erp-system/src/main/java/com/erp/system/domain/vo/HrOnboardingImportConfirmRequest.java`.
- Create `erp-modules/erp-system/src/main/java/com/erp/system/domain/vo/HrEmployeeProfileVo.java`.
- Create `erp-modules/erp-system/src/main/java/com/erp/system/domain/vo/HrEmployeeSummaryVo.java`.

Persistence:

- Create `erp-modules/erp-system/src/main/java/com/erp/system/mapper/HrOnboardingMapper.java` and `src/main/resources/mapper/system/HrOnboardingMapper.xml`.
- Create `erp-modules/erp-system/src/main/java/com/erp/system/mapper/HrOnboardingOperationLogMapper.java` and matching XML.
- Create `erp-modules/erp-system/src/main/java/com/erp/system/mapper/HrOnboardingPositionConfigMapper.java` and matching XML.
- Create `erp-modules/erp-system/src/main/java/com/erp/system/mapper/HrOnboardingImportMapper.java` and matching XML.
- Create `erp-modules/erp-system/src/main/java/com/erp/system/mapper/HrSensitiveAccessLogMapper.java` and matching XML.
- Modify `erp-api/erp-api-system/src/main/java/com/erp/system/api/domain/SysUserProfile.java`.
- Modify `erp-modules/erp-system/src/main/java/com/erp/system/mapper/SysUserProfileMapper.java` and matching XML.
- Modify `erp-modules/erp-system/src/main/java/com/erp/system/mapper/SysUserMapper.java` and matching XML only for scoped conflict lookup and summary queries.

Services and support:

- Create `erp-modules/erp-system/src/main/java/com/erp/system/service/IHrOnboardingService.java`.
- Create `erp-modules/erp-system/src/main/java/com/erp/system/service/IHrOnboardingImportService.java`.
- Create `erp-modules/erp-system/src/main/java/com/erp/system/service/IHrOnboardingPositionConfigService.java`.
- Create `erp-modules/erp-system/src/main/java/com/erp/system/service/IHrEmployeeProfileService.java`.
- Create `erp-modules/erp-system/src/main/java/com/erp/system/service/impl/HrOnboardingServiceImpl.java`.
- Create `erp-modules/erp-system/src/main/java/com/erp/system/service/impl/HrOnboardingAccessService.java`.
- Create `erp-modules/erp-system/src/main/java/com/erp/system/service/impl/HrOnboardingConfirmationService.java`.
- Create `erp-modules/erp-system/src/main/java/com/erp/system/service/impl/HrOnboardingConflictService.java`.
- Create `erp-modules/erp-system/src/main/java/com/erp/system/service/impl/HrOnboardingRuleService.java`.
- Create `erp-modules/erp-system/src/main/java/com/erp/system/service/impl/HrOnboardingImportServiceImpl.java`.
- Create `erp-modules/erp-system/src/main/java/com/erp/system/service/impl/HrOnboardingImportRowProcessor.java`.
- Create `erp-modules/erp-system/src/main/java/com/erp/system/service/impl/HrOnboardingPositionConfigServiceImpl.java`.
- Create `erp-modules/erp-system/src/main/java/com/erp/system/service/impl/HrEmployeeProfileServiceImpl.java`.
- Create `erp-modules/erp-system/src/main/java/com/erp/system/support/HrOnboardingExcelParser.java`.
- Create `erp-modules/erp-system/src/main/java/com/erp/system/support/HrOnboardingFieldRegistry.java`.
- Create `erp-modules/erp-system/src/main/java/com/erp/system/support/HrEmployeeFieldRegistry.java`.
- Create `erp-modules/erp-system/src/main/java/com/erp/system/support/HrSensitiveFieldMasker.java`.
- Create `erp-modules/erp-system/src/main/java/com/erp/system/support/HrOnboardingNoGenerator.java`.
- Create `erp-modules/erp-system/src/main/java/com/erp/system/support/HrEmployeeNoGenerator.java`.

Controllers:

- Replace the working-tree implementation of `HrOnboardingController.java`.
- Create `HrOnboardingExceptionHandler.java`.
- Replace the working-tree implementation of `HrCompletenessController.java`.
- Replace the working-tree implementation of `HrEmployeeProfileController.java`.
- Create `HrOnboardingImportController.java`.
- Create `HrOnboardingPositionConfigController.java`.

## Task 1: Schema and permission contract

**Files:**
- Create: `sql/erp_user_hr_onboarding_20260710.sql`
- Create: `docker/mysql/db/erp_user_hr_onboarding_20260710.sql`
- Modify: `sql/erp_user_employee_profile_20260706.sql`
- Modify: `docker/mysql/db/erp_user_employee_profile_20260706.sql`
- Test: `erp-modules/erp-system/src/test/java/com/erp/system/mapper/HrOnboardingSchemaSourceTest.java`

- [ ] **Step 1: Write the failing schema source test**

```java
package com.erp.system.mapper;

import static org.assertj.core.api.Assertions.assertThat;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class HrOnboardingSchemaSourceTest
{
    @Test
    void migrationDefinesOnboardingAggregateAndPermissions() throws Exception
    {
        Path root = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        while (root.getParent() != null && !Files.exists(root.resolve("sql"))) root = root.getParent();
        String sql = Files.readString(root.resolve("sql/erp_user_hr_onboarding_20260710.sql"));

        assertThat(sql).contains(
                "CREATE TABLE IF NOT EXISTS hr_onboarding",
                "CREATE TABLE IF NOT EXISTS hr_onboarding_operation_log",
                "CREATE TABLE IF NOT EXISTS hr_onboarding_position_config",
                "CREATE TABLE IF NOT EXISTS hr_onboarding_position_config_role",
                "CREATE TABLE IF NOT EXISTS hr_onboarding_import_batch",
                "CREATE TABLE IF NOT EXISTS hr_onboarding_import_row",
                "CREATE TABLE IF NOT EXISTS hr_sensitive_access_log",
                "direct_supervisor_user_id",
                "confirm_idempotency_key",
                "account_configuration_status",
                "uk_hr_onboarding_no",
                "idx_hr_onboarding_status_date",
                "hr:onboarding:workbench",
                "hr:onboarding:confirm",
                "hr:employee:sensitive:view");
    }
}
```

- [ ] **Step 2: Run the test and verify failure**

Run:

```bash
mvn -pl erp-modules/erp-system -am \
  -Dtest=HrOnboardingSchemaSourceTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: FAIL because the migration does not exist.

- [ ] **Step 3: Add the MySQL 5.7/8.0-compatible schema**

Create the seven tables with these exact onboarding columns and the supporting-table contracts below:

```sql
CREATE TABLE IF NOT EXISTS hr_onboarding (
    onboarding_id bigint(20) NOT NULL AUTO_INCREMENT,
    onboarding_no varchar(32) NOT NULL,
    status varchar(16) NOT NULL DEFAULT 'DRAFT',
    version int(11) NOT NULL DEFAULT 0,
    employee_name varchar(64) NOT NULL,
    phone_number varchar(32) NOT NULL,
    employee_no varchar(64) DEFAULT NULL,
    target_dept_id bigint(20) NOT NULL,
    target_store_id bigint(20) DEFAULT NULL,
    target_post_id bigint(20) NOT NULL,
    direct_supervisor_user_id bigint(20) DEFAULT NULL,
    owner_user_id bigint(20) NOT NULL,
    company_name varchar(100) DEFAULT NULL,
    dept_level1_name varchar(100) DEFAULT NULL,
    dept_level2_name varchar(100) DEFAULT NULL,
    dept_level3_name varchar(100) DEFAULT NULL,
    store_name varchar(100) DEFAULT NULL,
    position_name varchar(100) DEFAULT NULL,
    department_supervisor varchar(100) DEFAULT NULL,
    job_grade varchar(64) DEFAULT NULL,
    employee_category varchar(64) NOT NULL,
    sex char(1) DEFAULT NULL,
    birth_date date DEFAULT NULL,
    id_type varchar(64) DEFAULT NULL,
    id_number varchar(64) DEFAULT NULL,
    registered_residence varchar(255) DEFAULT NULL,
    current_address varchar(255) DEFAULT NULL,
    marital_status varchar(32) DEFAULT NULL,
    ethnicity varchar(64) DEFAULT NULL,
    emergency_contact varchar(100) DEFAULT NULL,
    emergency_contact_relation varchar(64) DEFAULT NULL,
    emergency_contact_phone varchar(32) DEFAULT NULL,
    expected_entry_date date NOT NULL,
    actual_entry_date date DEFAULT NULL,
    work_location varchar(100) DEFAULT NULL,
    work_city_level varchar(64) DEFAULT NULL,
    bank_name varchar(100) DEFAULT NULL,
    bank_account varchar(64) DEFAULT NULL,
    contract_type varchar(32) DEFAULT NULL,
    social_type varchar(32) DEFAULT NULL,
    probation_period varchar(64) DEFAULT NULL,
    legal_entity varchar(100) DEFAULT NULL,
    source_type varchar(32) NOT NULL DEFAULT 'MANUAL',
    linked_user_id bigint(20) DEFAULT NULL,
    preferred_conflict_action varchar(32) DEFAULT NULL,
    preferred_bind_user_id bigint(20) DEFAULT NULL,
    account_configuration_status varchar(16) DEFAULT NULL,
    account_risk_code varchar(64) DEFAULT NULL,
    cancel_reason varchar(255) DEFAULT NULL,
    confirmed_by varchar(64) DEFAULT NULL,
    confirmed_time datetime DEFAULT NULL,
    confirm_idempotency_key varchar(64) DEFAULT NULL,
    create_by varchar(64) DEFAULT '',
    create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_by varchar(64) DEFAULT '',
    update_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (onboarding_id),
    UNIQUE KEY uk_hr_onboarding_no (onboarding_no),
    UNIQUE KEY uk_hr_onboarding_confirm_key (confirm_idempotency_key),
    KEY idx_hr_onboarding_status_date (status, expected_entry_date),
    KEY idx_hr_onboarding_phone (phone_number),
    KEY idx_hr_onboarding_id_number (id_number),
    KEY idx_hr_onboarding_target_dept (target_dept_id),
    KEY idx_hr_onboarding_linked_user (linked_user_id),
    KEY idx_hr_onboarding_account_risk (account_configuration_status, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='HR入职单';
```

Define operation-log, position-config, config-role, import-batch, import-row, and sensitive-access-log tables with primary keys, `onboarding_id`/`batch_id`/`employee_user_id` indexes, audit columns, and `payload_json longtext` only for normalized import staging. The position-config table uses one durable row per `(post_id, employee_category)` with `UNIQUE KEY uk_hr_onboarding_position_category (post_id, employee_category)`; disabling updates that row instead of inserting history rows. The sensitive log stores operator user ID/name, employee user ID, field key or export scope, action type, request IP, event time, and result—never the revealed value. Use `CREATE TABLE IF NOT EXISTS`; do not use MySQL-8-only check constraints or JSON functions.

Use the repository's `information_schema + PREPARE` pattern to conditionally add `direct_supervisor_user_id` to `sys_user_profile`. Add the same column to both fresh-install profile scripts.

- [ ] **Step 4: Add forward-only menus, permissions, and parameters**

Insert permissions idempotently under the existing HR menu:

```text
hr:onboarding:workbench
hr:onboarding:list
hr:onboarding:query
hr:onboarding:add
hr:onboarding:edit
hr:onboarding:ready
hr:onboarding:return
hr:onboarding:confirm
hr:onboarding:cancel
hr:onboarding:restore
hr:onboarding:import:preview
hr:onboarding:import:confirm
hr:onboarding:import:template
hr:onboarding:config
hr:employee:sensitive:view
hr:employee:export:sensitive
```

Insert `hr.onboarding.post_entry_due_days=7`, `hr.onboarding.import_retention_days=30`, and `hr.employee.no.prefix=E` into `sys_config` only when the keys do not exist. Do not edit an already-deployed permission patch.

- [ ] **Step 5: Mirror and verify SQL**

Create both migration files in the same `apply_patch` change, then run:

```bash
cmp sql/erp_user_hr_onboarding_20260710.sql docker/mysql/db/erp_user_hr_onboarding_20260710.sql
cmp sql/erp_user_employee_profile_20260706.sql docker/mysql/db/erp_user_employee_profile_20260706.sql
mvn -pl erp-modules/erp-system -am \
  -Dtest=HrOnboardingSchemaSourceTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: both `cmp` commands exit 0 and the test passes.

- [ ] **Step 6: Commit**

```bash
git add sql/erp_user_hr_onboarding_20260710.sql \
  docker/mysql/db/erp_user_hr_onboarding_20260710.sql \
  sql/erp_user_employee_profile_20260706.sql \
  docker/mysql/db/erp_user_employee_profile_20260706.sql \
  erp-modules/erp-system/src/test/java/com/erp/system/mapper/HrOnboardingSchemaSourceTest.java
git commit -m "feat: add hr onboarding schema"
```

## Task 2: Onboarding domain and mapper

**Files:**
- Create: `erp-modules/erp-system/src/main/java/com/erp/system/domain/HrOnboarding.java`
- Create: `erp-modules/erp-system/src/main/java/com/erp/system/domain/HrOnboardingOperationLog.java`
- Create: `erp-modules/erp-system/src/main/java/com/erp/system/domain/HrOnboardingPositionConfig.java`
- Create: `erp-modules/erp-system/src/main/java/com/erp/system/domain/vo/HrOnboardingQuery.java`
- Create: `erp-modules/erp-system/src/main/java/com/erp/system/domain/vo/HrOnboardingListVo.java`
- Create: `erp-modules/erp-system/src/main/java/com/erp/system/domain/vo/HrOnboardingDetailVo.java`
- Create: `erp-modules/erp-system/src/main/java/com/erp/system/mapper/HrOnboardingMapper.java`
- Create: `erp-modules/erp-system/src/main/resources/mapper/system/HrOnboardingMapper.xml`
- Create: `erp-modules/erp-system/src/main/java/com/erp/system/mapper/HrOnboardingOperationLogMapper.java`
- Create: `erp-modules/erp-system/src/main/resources/mapper/system/HrOnboardingOperationLogMapper.xml`
- Test: `erp-modules/erp-system/src/test/java/com/erp/system/mapper/HrOnboardingMapperBindingTest.java`

- [ ] **Step 1: Write the failing mapper contract test**

```java
@Test
void mapperSupportsLockingVersionedUpdatesAndScopedQueries() throws Exception
{
    String xml = new String(Resources.getResourceAsStream("mapper/system/HrOnboardingMapper.xml").readAllBytes(), UTF_8);
    assertThat(xml).contains(
            "selectScopedOnboardingByIdForUpdate",
            "for update",
            "updateOnboardingByVersion",
            "version = version + 1",
            "and version = #{version}",
            "selectOnboardingList",
            "target_dept_id",
            "selectConflictCandidates");
}
```

- [ ] **Step 2: Run the test and verify failure**

Run the Maven command from Task 1 with `-Dtest=HrOnboardingMapperBindingTest`.

Expected: FAIL because the mapper is missing.

- [ ] **Step 3: Add exact status constants and entity fields**

In `HrOnboarding.java`, define:

```java
public static final String STATUS_DRAFT = "DRAFT";
public static final String STATUS_READY = "READY";
public static final String STATUS_CONFIRMED = "CONFIRMED";
public static final String STATUS_CANCELLED = "CANCELLED";
```

Add Java properties matching every `hr_onboarding` column, with `@JsonFormat(pattern="yyyy-MM-dd")` on date fields, and conventional getters/setters. `HrOnboardingQuery` extends `BaseEntity` and adds `onboardingId`, `keyword`, `status`, `targetDeptId`, `targetStoreId`, `employeeCategory`, `ownerUserId`, `expectedEntryDateFrom`, and `expectedEntryDateTo`.

Also add `HrOnboardingPositionConfig` with the configuration fields listed in Task 5. Task 2 introduces the domain type so the rule engine compiles; Task 5 adds its mapper, service, and API. Add skeletal list/detail VOs with only masked-sensitive field names; Task 3 implements their mapping and Task 4 adds workflow/completeness content.

- [ ] **Step 4: Implement mapper methods**

```java
List<HrOnboarding> selectOnboardingList(HrOnboardingQuery query);
HrOnboarding selectScopedOnboardingByIdForUpdate(HrOnboardingQuery query);
List<HrOnboarding> selectConflictCandidates(@Param("phone") String phone,
        @Param("idNumber") String idNumber, @Param("employeeNo") String employeeNo);
int insertOnboarding(HrOnboarding onboarding);
int updateOnboardingByVersion(HrOnboarding onboarding);
int updateOnboardingStatusByVersion(@Param("onboardingId") Long onboardingId,
        @Param("fromStatus") String fromStatus, @Param("toStatus") String toStatus,
        @Param("version") Integer version, @Param("operator") String operator,
        @Param("cancelReason") String cancelReason);
```

The list and scoped-lock SQL join `sys_dept d on d.dept_id = o.target_dept_id`, so service-level `@DataScope(deptAlias="d")` can apply. Keyword search matches employee name, phone, stored/derived post label, and organization label; all other query fields remain server-side predicates. `updateOnboardingByVersion` updates only editable draft fields plus audit/version; it never writes status, source, preferred binding, linked user, account risk, confirmation, or idempotency columns. Those columns are changed only by dedicated import/state/confirmation mapper statements. Add a separate log mapper with `insertOperationLog` and `selectByOnboardingId`.

- [ ] **Step 5: Run the mapper tests**

Run:

```bash
mvn -pl erp-modules/erp-system -am \
  -Dtest=HrOnboardingMapperBindingTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add erp-modules/erp-system/src/main/java/com/erp/system/domain/HrOnboarding.java \
  erp-modules/erp-system/src/main/java/com/erp/system/domain/HrOnboardingOperationLog.java \
  erp-modules/erp-system/src/main/java/com/erp/system/domain/HrOnboardingPositionConfig.java \
  erp-modules/erp-system/src/main/java/com/erp/system/domain/vo/HrOnboardingQuery.java \
  erp-modules/erp-system/src/main/java/com/erp/system/domain/vo/HrOnboardingListVo.java \
  erp-modules/erp-system/src/main/java/com/erp/system/domain/vo/HrOnboardingDetailVo.java \
  erp-modules/erp-system/src/main/java/com/erp/system/mapper/HrOnboardingMapper.java \
  erp-modules/erp-system/src/main/java/com/erp/system/mapper/HrOnboardingOperationLogMapper.java \
  erp-modules/erp-system/src/main/resources/mapper/system/HrOnboardingMapper.xml \
  erp-modules/erp-system/src/main/resources/mapper/system/HrOnboardingOperationLogMapper.xml \
  erp-modules/erp-system/src/test/java/com/erp/system/mapper/HrOnboardingMapperBindingTest.java
git commit -m "feat: add hr onboarding persistence"
```

## Task 3: Rule engine, completeness, and masking

**Files:**
- Create: `erp-modules/erp-system/src/main/java/com/erp/system/domain/vo/HrOnboardingCompletionVo.java`
- Create: `erp-modules/erp-system/src/main/java/com/erp/system/service/impl/HrOnboardingRuleService.java`
- Create: `erp-modules/erp-system/src/main/java/com/erp/system/support/HrOnboardingFieldRegistry.java`
- Create: `erp-modules/erp-system/src/main/java/com/erp/system/support/HrSensitiveFieldMasker.java`
- Test: `erp-modules/erp-system/src/test/java/com/erp/system/service/impl/HrOnboardingRuleServiceTest.java`
- Test: `erp-modules/erp-system/src/test/java/com/erp/system/support/HrOnboardingFieldRegistryTest.java`
- Test: `erp-modules/erp-system/src/test/java/com/erp/system/support/HrSensitiveFieldMaskerTest.java`

- [ ] **Step 1: Write failing rule tests**

```java
@Test
void draftRequiresOnlySevenCreateFields()
{
    HrOnboarding item = new HrOnboarding();
    item.setEmployeeName("测试员工");
    HrOnboardingCompletionVo result = service.evaluateCreate(item);
    assertThat(result.getMissingFields()).containsExactlyInAnyOrder(
            "phoneNumber", "expectedEntryDate", "targetDeptId", "targetPostId",
            "employeeCategory", "ownerUserId");
}

@Test
void readyRequiresIdentityOrganizationAndContactFields()
{
    HrOnboardingCompletionVo result = service.evaluateReady(completeCreateOnlyForStorePost());
    assertThat(result.getMissingFields()).contains("sex", "birthDate", "idType", "idNumber",
            "targetStoreId", "jobGrade", "directSupervisorUserId", "registeredResidence",
            "currentAddress", "maritalStatus", "ethnicity", "emergencyContact",
            "emergencyContactRelation", "emergencyContactPhone", "workLocation",
            "workCityLevel", "legalEntity");
}

@Test
void confirmRequiresReadyStatusAndActualEntryDate()
{
    HrOnboarding item = completeReady();
    item.setStatus(HrOnboarding.STATUS_DRAFT);
    HrOnboardingCompletionVo result = service.evaluateConfirm(item, configuredPosition());
    assertThat(result.getBlockingCodes()).contains("STATUS_NOT_READY", "ACTUAL_ENTRY_DATE_REQUIRED");
}

@Test
void conditionalModesAreExplicitAndMissingConfigIsANonBlockingRisk()
{
    HrOnboardingCompletionVo required = service.evaluateConfirm(completeReady(), requiredConfig());
    assertThat(required.getMissingFields()).contains("contractType", "socialType", "probationPeriod");

    HrOnboardingCompletionVo noConfig = service.evaluateConfirm(completeReady(), null);
    assertThat(noConfig.getBlockingCodes()).doesNotContain("POSITION_CONFIG_MISSING");
    assertThat(noConfig.getRiskCodes()).contains("ACCOUNT_CONFIGURATION_MISSING");
}
```

- [ ] **Step 2: Implement one server-side rule registry**

Create a 35-entry `HrOnboardingFieldRegistry` matching the approved spreadsheet headers. Each entry records backend key, Chinese label, storage/source owner, derived/system-generated flag, masking class, and completeness group. Its test asserts exactly 35 unique headers, including the accepted aliases `入职日期`, `入职岗位`, `岗位职级`, and `户籍地址`, and verifies aliases never create extra business fields.

Use registry-backed `FieldRule` records and explicit methods:

```java
public HrOnboardingCompletionVo evaluateCreate(HrOnboarding item);
public HrOnboardingCompletionVo evaluateReady(HrOnboarding item);
public HrOnboardingCompletionVo evaluateConfirm(HrOnboarding item, HrOnboardingPositionConfig config);
public HrOnboardingCompletionVo evaluateProfile(SysUser user);
```

Return percentage, missing keys, labels, grouped missing fields, blocking codes, risk codes, post-entry due date, and `allowedActions`. Define the only action values as `EDIT`, `MARK_READY`, `RETURN_TO_DRAFT`, `CONFIRM`, `CANCEL`, and `RESTORE`. `NOT_APPLICABLE` satisfies a conditional rule only when it comes from the resolved configuration mode, not from a null field. A missing/disabled position configuration produces the non-blocking `ACCOUNT_CONFIGURATION_MISSING` risk; confirmation then uses the safe disabled-account fallback described in Task 6. The frontend must consume this result; do not export rule arrays to JavaScript.

- [ ] **Step 3: Implement deterministic masking**

```java
public String maskPhone(String value);
public String maskIdNumber(String value);
public String maskBankAccount(String value);
public String maskAddress(String value);
public HrOnboardingListVo toListVo(HrOnboarding source);
public HrOnboardingDetailVo toMaskedDetailVo(HrOnboarding source);
```

The two VOs contain explicit `phoneNumberMasked`, `idNumberMasked`, `bankAccountMasked`, `registeredResidenceMasked`, and `currentAddressMasked` fields rather than raw-value properties. Mobile and default desktop detail consume the same masked DTO; mobile never returns full ID, bank, or address. Do not depend on the generic sensitive serializer because it cannot express `hr:employee:sensitive:view` or audit reveals.

- [ ] **Step 4: Run tests and commit**

Run:

```bash
mvn -pl erp-modules/erp-system -am \
  -Dtest=HrOnboardingFieldRegistryTest,HrOnboardingRuleServiceTest,HrSensitiveFieldMaskerTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: PASS.

Commit the files listed in this task with `git commit -m "feat: add hr onboarding rules"`.

## Task 4: CRUD and state transitions

**Files:**
- Create: `erp-modules/erp-system/src/main/java/com/erp/system/service/IHrOnboardingService.java`
- Create: `erp-modules/erp-system/src/main/java/com/erp/system/service/impl/HrOnboardingServiceImpl.java`
- Create: `erp-modules/erp-system/src/main/java/com/erp/system/service/impl/HrOnboardingAccessService.java`
- Replace: `erp-modules/erp-system/src/main/java/com/erp/system/controller/HrOnboardingController.java`
- Create: `erp-modules/erp-system/src/main/java/com/erp/system/controller/HrOnboardingExceptionHandler.java`
- Modify: `erp-modules/erp-system/src/main/java/com/erp/system/domain/vo/HrOnboardingDetailVo.java`
- Modify: `erp-modules/erp-system/src/main/java/com/erp/system/domain/vo/HrOnboardingListVo.java`
- Create: `erp-modules/erp-system/src/main/java/com/erp/system/domain/vo/HrOnboardingCreateRequest.java`
- Create: `erp-modules/erp-system/src/main/java/com/erp/system/domain/vo/HrOnboardingUpdateRequest.java`
- Create: `erp-modules/erp-system/src/main/java/com/erp/system/domain/vo/HrOnboardingVersionRequest.java`
- Create: `erp-modules/erp-system/src/main/java/com/erp/system/domain/vo/HrOnboardingCancelRequest.java`
- Create: `erp-modules/erp-system/src/main/java/com/erp/system/exception/HrOnboardingValidationException.java`
- Create: `erp-modules/erp-system/src/main/java/com/erp/system/support/HrOnboardingNoGenerator.java`
- Create: `erp-modules/erp-system/src/main/java/com/erp/system/domain/vo/HrOnboardingSummaryVo.java`
- Test: `erp-modules/erp-system/src/test/java/com/erp/system/service/impl/HrOnboardingServiceImplTest.java`
- Test: `erp-modules/erp-system/src/test/java/com/erp/system/service/impl/HrOnboardingAccessServiceTest.java`
- Test: `erp-modules/erp-system/src/test/java/com/erp/system/controller/HrOnboardingControllerTest.java`

- [ ] **Step 1: Write failing state-transition tests**

Cover exactly:

```text
DRAFT -> READY
DRAFT -> CANCELLED
READY -> DRAFT
READY -> CONFIRMED
READY -> CANCELLED
CANCELLED -> DRAFT
```

Assert every other transition throws `ServiceException`, cancel requires a nonblank reason, restore clears the active-row cancel reason while the operation log retains it, and a version mismatch returns `ONBOARDING_VERSION_CONFLICT`. Assert ordinary edit is allowed only in `DRAFT`; `CONFIRMED` is terminal and cannot be edited, transitioned, or deleted.

Also test that an operator cannot get, update, ready, cancel, restore, or lock an onboarding row outside their data scope, and cannot submit an out-of-scope department, store, post, supervisor, or owner on create/update.

- [ ] **Step 2: Define the service contract**

```java
List<HrOnboardingListVo> list(HrOnboardingQuery query);
HrOnboardingDetailVo get(Long onboardingId);
HrOnboardingDetailVo create(HrOnboardingCreateRequest input, String operator);
HrOnboardingDetailVo update(Long onboardingId, HrOnboardingUpdateRequest input, String operator);
HrOnboardingDetailVo markReady(Long onboardingId, Integer version, String operator);
HrOnboardingDetailVo returnToDraft(Long onboardingId, Integer version, String operator);
HrOnboardingDetailVo cancel(Long onboardingId, Integer version, String reason, String operator);
HrOnboardingDetailVo restore(Long onboardingId, Integer version, String operator);
HrOnboardingSummaryVo summary(HrOnboardingQuery query);
```

`HrOnboardingListVo` contains only list-safe values: IDs, name, `phoneNumberMasked`, target organization/post labels, expected date, state, missing count, owner, version, `allowedActions`, and a backend-selected `currentAction`. `HrOnboardingDetailVo` contains those values plus `missingOnboardingFields` and `missingProfileFields` shaped as group-key → `[{ key, label }]`, both completion percentages, post-entry due/overdue data, account-configuration risk codes, and operation logs shaped as `{ operationType, operatorName, operationTime, summary }`. `HrOnboardingSummaryVo` contains `todayArrivalCount`, `pendingConfirmCount`, `accountConfigurationRiskCount`, and a scoped, masked `todayTasks` list. Neither VO exposes raw phone, ID number, bank account, or full address properties.

The create request contains only the seven quick-create fields. The update request whitelists only user-maintained fields and organization/post/supervisor source IDs from the 35-field contract plus `version`; derived company/department/store/post/supervisor labels are recomputed and have no request setters. It also has no setters for status, onboarding number, source, preferred binding, linked user, confirmation data, or audit fields. Sensitive properties use PATCH semantics: omitted means unchanged, an explicit empty value means clear when the field is optional, and values matching known masking placeholders are rejected as `MASKED_VALUE_NOT_ACCEPTED`.

- [ ] **Step 3: Implement scoped reads and optimistic writes**

Annotate public list and summary methods with `@DataScope(deptAlias="d")`. Put authorization in the separately injected `HrOnboardingAccessService`, whose public `findScoped`, `lockScopedForUpdate`, and `validateTargets` methods are invoked through their Spring proxy; never rely on a self-call to an annotated method. `lockScopedForUpdate` passes the aspect-populated `HrOnboardingQuery` into a `SELECT ... FOR UPDATE` query that includes `${params.dataScope}`. Use it for update and every state action, including confirmation in Task 6. Validate create/import target organizations and every update target against scoped departments/stores/posts/supervisors/owners.

Apply `version` to every write. Mark create, update, ready, return-to-draft, cancel, and restore `@Transactional(rollbackFor = Exception.class)` so the business change and operation-log row commit or roll back together. Operation-log summaries record changed field keys, state codes, conflict decisions, and a sanitized cancel reason; never copy phone, ID, bank, address, password, or full request payload values.

`HrOnboardingNoGenerator` produces `OB` + `yyyyMMdd` + the first 12 uppercase characters of `IdUtils.simpleUUID()`. Retry insertion up to three times only for `uk_hr_onboarding_no`; surface `ONBOARDING_NO_COLLISION` after the third collision.

Do not call `SysShopDeptFilterSupport` for the global HR queue; it reflects the last selected inventory context and is not a replacement for role data scope.

- [ ] **Step 4: Replace the controller**

Expose `/list`, `/summary`, `POST /`, `GET /{id}`, `PUT /{id}`, `POST /{id}/ready`, `/return-to-draft`, `/cancel`, and `/restore`, each with its dedicated permission. `/summary` uses `hr:onboarding:workbench` and returns the masked `todayTasks`, so a mobile HR workbench does not also need list/query permission; `/list` and detail retain `hr:onboarding:list` and `hr:onboarding:query`. Apply these exact audit annotations so PII is never copied into operation logs:

Use the existing response envelopes consistently: paged list returns `TableDataInfo { code, msg, rows, total }`; detail, summary, create, edit, and action endpoints return `AjaxResult { code, msg, data }`. Do not return a bare list on desktop and a different envelope on mobile.

```java
@Log(title = "人事入职新建", businessType = BusinessType.INSERT,
        isSaveRequestData = false, isSaveResponseData = false)
@Log(title = "人事入职编辑", businessType = BusinessType.UPDATE,
        isSaveRequestData = false, isSaveResponseData = false)
@Log(title = "人事入职状态", businessType = BusinessType.OTHER,
        isSaveRequestData = false, isSaveResponseData = false)
```

Use the third annotation on ready, return-to-draft, cancel, and restore.

`HrOnboardingValidationException` carries a stable `errorCode`, user message, `Map<String, String> fieldErrors`, and `List<String> blockingCodes`. The system-module advice handles only this exception type and returns those values as top-level `AjaxResult` properties so desktop and mobile can focus the exact field. Add controller tests for this response shape.

- [ ] **Step 5: Run tests and commit**

Run:

```bash
mvn -pl erp-modules/erp-system -am \
  -Dtest=HrOnboardingServiceImplTest,HrOnboardingAccessServiceTest,HrOnboardingControllerTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: PASS.

Commit with `git commit -m "feat: add hr onboarding workflow"`.

## Task 5: Position configuration and form options

**Files:**
- Modify: `erp-modules/erp-system/src/main/java/com/erp/system/domain/HrOnboardingPositionConfig.java`
- Create: `erp-modules/erp-system/src/main/java/com/erp/system/mapper/HrOnboardingPositionConfigMapper.java`
- Create: `erp-modules/erp-system/src/main/resources/mapper/system/HrOnboardingPositionConfigMapper.xml`
- Create: `erp-modules/erp-system/src/main/java/com/erp/system/service/IHrOnboardingPositionConfigService.java`
- Create: `erp-modules/erp-system/src/main/java/com/erp/system/service/impl/HrOnboardingPositionConfigServiceImpl.java`
- Create: `erp-modules/erp-system/src/main/java/com/erp/system/controller/HrOnboardingPositionConfigController.java`
- Modify: `erp-modules/erp-system/src/main/java/com/erp/system/service/IHrOnboardingService.java`
- Modify: `erp-modules/erp-system/src/main/java/com/erp/system/service/impl/HrOnboardingServiceImpl.java`
- Test: `erp-modules/erp-system/src/test/java/com/erp/system/service/impl/HrOnboardingPositionConfigServiceTest.java`
- Create: `scripts/hr-onboarding-position-config-readiness.sql`

- [ ] **Step 1: Write failing configuration tests**

Verify a `(postId, employeeCategory)` pair resolves default role IDs, data-scope strategy, contract/social/probation modes plus defaults, job grade, and account-enable flag. Each mode accepts only `REQUIRED`, `OPTIONAL`, or `NOT_APPLICABLE`; verify duplicate pairs and blank defaults for a `REQUIRED` mode are rejected. When `accountEnabled=true`, require at least one active role and a valid data-scope strategy; confirmation separately requires the matching target dept/store. Disabled-account configurations may deliberately grant none.

- [ ] **Step 2: Implement persistence and API**

Use these fields:

```java
Long configId;
Long postId;
String employeeCategory;
String dataScopeStrategy; // TARGET_STORE, TARGET_DEPT, NONE
String contractTypeMode; // REQUIRED, OPTIONAL, NOT_APPLICABLE
String defaultContractType;
String socialTypeMode; // REQUIRED, OPTIONAL, NOT_APPLICABLE
String defaultSocialType;
String probationPeriodMode; // REQUIRED, OPTIONAL, NOT_APPLICABLE
String defaultProbationPeriod;
String jobGrade;
Boolean accountEnabled;
String status;
Integer version;
List<Long> roleIds;
```

Expose list, detail, create, update, disable, and `GET /options` endpoints under `/hr/onboarding/config` with `hr:onboarding:config`. Config options return active posts, roles, employee categories, and dictionary defaults without requiring `system:role:list` or `system:post:list`. Persist `NOT_APPLICABLE` as the explicit mode; never encode it as a null default.

- [ ] **Step 3: Add HR form metadata**

Add `GET /hr/onboarding/form-options` returning `{ organizations, stores, posts, supervisors, owners, employeeCategories, sexOptions, idTypes, maritalStatuses, ethnicities, workCityLevels, contractTypes, socialTypes, probationPeriods, resolvedPositionDefaults }`. Every option uses `{ label, value }`; defaults include the three rule modes. This endpoint has `hr:onboarding:add` or `hr:onboarding:edit`; do not force HR users to hold `system:user:edit`.

- [ ] **Step 4: Add the go-live readiness query**

Create a read-only SQL report that cross-checks active posts and configured employee categories, lists missing/disabled mappings and missing default roles, and emits no `INSERT`, `UPDATE`, or `DELETE`. Before production cutover, HR must configure every common active pair through the new page; do not invent role or data-scope assignments in a migration.

- [ ] **Step 5: Run tests and commit**

Run:

```bash
mvn -pl erp-modules/erp-system -am \
  -Dtest=HrOnboardingPositionConfigServiceTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: PASS. Commit with `git commit -m "feat: configure hr onboarding positions"`.

## Task 6: Transactional confirmation and account binding

**Files:**
- Create: `erp-modules/erp-system/src/main/java/com/erp/system/service/impl/HrOnboardingConfirmationService.java`
- Create: `erp-modules/erp-system/src/main/java/com/erp/system/service/impl/HrOnboardingConflictService.java`
- Create: `erp-modules/erp-system/src/main/java/com/erp/system/support/HrEmployeeNoGenerator.java`
- Create: `erp-modules/erp-system/src/main/java/com/erp/system/domain/vo/HrOnboardingConfirmRequest.java`
- Create: `erp-modules/erp-system/src/main/java/com/erp/system/domain/vo/HrOnboardingConfirmResult.java`
- Create: `erp-modules/erp-system/src/main/java/com/erp/system/domain/vo/HrOnboardingConflictVo.java`
- Modify: `erp-modules/erp-system/src/main/java/com/erp/system/controller/HrOnboardingController.java`
- Modify: `erp-api/erp-api-system/src/main/java/com/erp/system/api/domain/SysUserProfile.java`
- Modify: `erp-modules/erp-system/src/main/java/com/erp/system/mapper/SysUserMapper.java` and XML
- Modify: `erp-modules/erp-system/src/main/java/com/erp/system/mapper/SysUserProfileMapper.java` and XML
- Modify: `erp-modules/erp-system/src/main/java/com/erp/system/mapper/SysUserRoleMapper.java` and XML
- Modify: `erp-modules/erp-system/src/main/java/com/erp/system/mapper/SysUserPostMapper.java` and XML
- Test: `erp-modules/erp-system/src/test/java/com/erp/system/service/impl/HrOnboardingConfirmationServiceTest.java`

- [ ] **Step 1: Write failing confirmation tests**

Test new-account confirmation, safely binding an existing account, idempotent replay, concurrent same-key confirmation, rollback on profile failure, duplicate ID/phone conflict, missing position config, and a version conflict. Assert:

- same-key replay returns `replayed=true` and `oneTimePassword=null`;
- a different key against `CONFIRMED` returns `ONBOARDING_ALREADY_CONFIRMED`;
- concurrent same-key calls create exactly one user;
- new accounts have a random encrypted password, employee number as username, and `pwdUpdateDate == null`;
- binding preserves the existing username, password, roles, posts, and shops while merging approved onboarding relations;
- missing/disabled config still creates the formal profile, grants no new roles/shops, disables the account, and records `ACCOUNT_CONFIGURATION_MISSING` in the risk queue.

- [ ] **Step 2: Add the request/result contract**

```java
public class HrOnboardingConfirmRequest {
    private Integer version;
    private Date actualEntryDate;
    private String conflictAction; // CREATE_NEW or BIND_EXISTING
    private Long bindUserId;
    private String idempotencyKey;
}

public class HrOnboardingConfirmResult {
    private Long onboardingId;
    private Long userId;
    private String employeeNo;
    private String accountStatus;
    private String oneTimePassword;
    private boolean replayed;
    private List<String> riskCodes;
}
```

`HrOnboardingConflictVo` fixes the preview contract: `conflictType`, `sourceType`, candidate user/onboarding ID, employee number, name, masked phone, department label, `eligibleForBind`, `blocking`, and allowed decisions. `BIND_EXISTING` is accepted only for an eligible candidate returned by the scoped preview. Active employee/account or active-onboarding phone/ID conflicts block `CREATE_NEW`; a cancelled onboarding match is a non-blocking warning.

- [ ] **Step 3: Implement confirmation in one transaction**

Annotate `confirm` with `@Transactional(rollbackFor=Exception.class)` and execute:

1. Call `HrOnboardingAccessService.lockScopedForUpdate`, which applies data scope inside the locking query.
2. Return linked user/employee/account data with `replayed=true` and no password when status is `CONFIRMED` and `confirm_idempotency_key` matches. Reject a different key.
3. Evaluate confirm rules and query conflicts.
4. Validate `bindUserId` against the scoped conflict candidates. Reject `CREATE_NEW` when a blocking active conflict exists.
5. Resolve position config. If missing/disabled, use safe fallback `NONE`, no roles, no shops, disabled account, and risk `ACCOUNT_CONFIGURATION_MISSING`; this risk is non-blocking.
6. For a new account, generate employee number as configured prefix + four-digit year + onboarding ID padded to eight digits. Check both employee number and username; on a legacy collision try suffixes `-01` through `-99`, then return `EMPLOYEE_NO_EXHAUSTED`. Set the chosen value as `userName` and build `SysUser`/`SysUserProfile`, including `directSupervisorUserId`.
7. For an existing eligible account, reuse and validate its employee number when present, otherwise assign a generated employee number. Call `updateHrEmployeeProfile`, not the destructive general `updateUser`.
8. Merge configured role/post relations with existing relations using select-existing + insert-missing mapper methods; never delete existing relationships during binding.
9. For a new account, call the five-argument `userShopService.saveUserShops(userId, configuredShopIds, operator, operatorUserId, SecurityUtils.isAdmin())`. For an existing account, preserve existing shops and insert only validated missing configured shops; do not replace the full set. Never call the deprecated three-argument overload.
10. Write `actualEntryDate` as the formal entry date. Set formal employee status to `试用` and calculate the planned regularization date when the resolved probation mode/value requires probation; otherwise set `正式` and leave the plan date inapplicable. `sys_user`/`sys_user_profile` remain the truth after confirmation; do not duplicate formal status as an editable onboarding field.
11. Version-update onboarding to `CONFIRMED`, store `actual_entry_date`, `linked_user_id`, the idempotency key, `account_configuration_status` (`CONFIGURED` or `MISSING`), risk code, confirmer/time, and log the action.

Generate a 16-character `SecureRandom` password only for a new account, hash with `SecurityUtils.encryptPassword`, return plaintext once, and never persist plaintext or include it in `@Log`. Existing-account binding never changes the password.

- [ ] **Step 4: Expose the endpoint safely**

Add `GET /hr/onboarding/{id}/conflicts` and `POST /hr/onboarding/{id}/confirm` with `hr:onboarding:confirm`. The preview is scoped and masked. Apply this annotation to confirm:

```java
@Log(title = "人事确认入职", businessType = BusinessType.OTHER,
        isSaveRequestData = false, isSaveResponseData = false)
```

- [ ] **Step 5: Run tests and commit**

Run:

```bash
mvn -pl erp-modules/erp-system -am \
  -Dtest=HrOnboardingConfirmationServiceTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: PASS. Commit with `git commit -m "feat: confirm hr onboarding transactionally"`.

## Task 7: Excel preview and confirm

**Files:**
- Create: `erp-modules/erp-system/src/main/java/com/erp/system/domain/HrOnboardingImportBatch.java`
- Create: `erp-modules/erp-system/src/main/java/com/erp/system/domain/HrOnboardingImportRow.java`
- Create: `erp-modules/erp-system/src/main/java/com/erp/system/domain/vo/HrOnboardingImportPreviewVo.java`
- Create: `erp-modules/erp-system/src/main/java/com/erp/system/domain/vo/HrOnboardingImportConfirmRequest.java`
- Create: `erp-modules/erp-system/src/main/java/com/erp/system/mapper/HrOnboardingImportMapper.java` and XML
- Create: `erp-modules/erp-system/src/main/java/com/erp/system/service/IHrOnboardingImportService.java`
- Create: `erp-modules/erp-system/src/main/java/com/erp/system/service/impl/HrOnboardingImportServiceImpl.java`
- Create: `erp-modules/erp-system/src/main/java/com/erp/system/service/impl/HrOnboardingImportRowProcessor.java`
- Modify: `erp-modules/erp-system/src/main/java/com/erp/system/service/IHrOnboardingService.java`
- Modify: `erp-modules/erp-system/src/main/java/com/erp/system/service/impl/HrOnboardingServiceImpl.java`
- Create: `erp-modules/erp-system/src/main/java/com/erp/system/support/HrOnboardingExcelParser.java`
- Create: `erp-modules/erp-system/src/main/java/com/erp/system/controller/HrOnboardingImportController.java`
- Test: `erp-modules/erp-system/src/test/java/com/erp/system/support/HrOnboardingExcelParserTest.java`
- Test: `erp-modules/erp-system/src/test/java/com/erp/system/service/impl/HrOnboardingImportServiceImplTest.java`
- Test: `erp-modules/erp-system/src/test/java/com/erp/system/service/impl/HrOnboardingImportRowProcessorTest.java`

- [ ] **Step 1: Write failing parser tests**

Create workbooks in memory with Apache POI and verify:

- `入职日期` maps to `expectedEntryDate`.
- `入职岗位` maps to `positionName` and `岗位职级` maps to `jobGrade`.
- `户籍地址` maps to `registeredResidence`.
- Canonical and alias headers with different values produce `ALIAS_CONFLICT`.
- Repeated rows, unresolved organization, invalid enum, and long-number cells retain original row numbers and text values.
- Date-order, phone, ID, bank, dictionary, organization/store/post/supervisor ambiguity, active/cancelled onboarding, existing employee/account, and same-file duplicate cases map to the required row category.
- Template cells for phone, ID number, and bank account use Excel text format `@`.

- [ ] **Step 2: Implement the dedicated parser**

Do not use generic `ExcelUtil`; it overwrites duplicate headers and cannot report alias conflicts reliably. `HrOnboardingExcelParser` returns normalized row objects with `sourceRowNumber`, normalized values, warnings, errors, masked candidate summaries, and exactly one category: `IMPORTABLE`, `WARNING`, `INVALID`, `POSSIBLE_DUPLICATE`, or `BINDABLE_ACCOUNT`.

Use deterministic severity: exact active-onboarding and same-file duplicates are `INVALID`; cancelled-onboarding matches are `WARNING`; eligible existing employees/accounts are `BINDABLE_ACCOUNT`; non-exact similarity is `POSSIBLE_DUPLICATE`; all clean rows are `IMPORTABLE`.

- [ ] **Step 3: Implement preview storage and confirmation**

```java
HrOnboardingImportPreviewVo preview(MultipartFile file, String operator);
HrOnboardingImportPreviewVo getBatch(Long batchId);
HrOnboardingImportPreviewVo confirmBatch(Long batchId,
        HrOnboardingImportConfirmRequest request, String operator);
void writeTemplate(HttpServletResponse response);
void writeErrorRows(Long batchId, HttpServletResponse response);
```

`HrOnboardingImportConfirmRequest` contains batch `version` and row decisions shaped as `{ rowId, decision, bindUserId }`. Preview never writes `hr_onboarding`. Preview/get/confirm require the batch creator or an administrator, and returned rows mask phone/ID/bank/address values even though normalized staging retains them for the eventual write. Confirm processes selected valid rows independently, records per-row result, and returns success/failure counts without hiding failures in HTML. For a bindable row, store the approved choice as `preferred_conflict_action`/`preferred_bind_user_id` on the new onboarding record so the later arrival-confirm dialog can prefill it; actual confirmation still re-queries candidates and requires the request to repeat the decision.

`confirmBatch` first atomically changes the batch from `PREVIEWED` to `PROCESSING` by version, so a repeated confirmation returns the stored batch result rather than creating duplicates. It then invokes the separately injected `HrOnboardingImportRowProcessor.process(rowId, decision, operator)` once per selected row. That public method uses `@Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)`, revalidates data scope and the latest business conflicts, calls `IHrOnboardingService.createImported(HrOnboardingImportRow row, String operator)` to reuse the number generator/rules/logging while forcing `sourceType=IMPORT`, and stores that row's result in the same transaction. No controller exposes `createImported`. The outer service catches row failures and continues; never self-call the `REQUIRES_NEW` method.

Require `bindUserId` for `BINDABLE_ACCOUNT`, require an explicit continue decision for `WARNING`/`POSSIBLE_DUPLICATE`, and reject `INVALID`. After all rows finish, persist batch totals/status. Add tests for mixed success/failure, duplicate confirmation, stale conflicts, and an out-of-scope target.

- [ ] **Step 4: Expose import endpoints**

Use `/hr/onboarding/import/preview`, `/{batchId}`, `/{batchId}/confirm`, `/template`, and `/{batchId}/errors`. On preview and confirm apply:

```java
@Log(title = "人事入职导入", businessType = BusinessType.IMPORT,
        isSaveRequestData = false, isSaveResponseData = false)
```

On template and error-row exports use `BusinessType.EXPORT` with the same two logging flags. At the start of each preview, call `deleteExpiredUnconfirmedBatches(cutoff, 1000)` using `hr.onboarding.import_retention_days`; delete child rows and only `PREVIEWED`/`FAILED` batches in one transaction. Never delete `PROCESSING` or confirmed batch summaries. Cover the cutoff/status rules in the import-service test.

- [ ] **Step 5: Run tests and commit**

Run:

```bash
mvn -pl erp-modules/erp-system -am \
  -Dtest=HrOnboardingExcelParserTest,HrOnboardingImportServiceImplTest,HrOnboardingImportRowProcessorTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: PASS. Commit with `git commit -m "feat: preview hr onboarding imports"`.

## Task 8: Employee master, completeness, and sensitive reveal

**Files:**
- Create: `erp-modules/erp-system/src/main/java/com/erp/system/service/IHrEmployeeProfileService.java`
- Create: `erp-modules/erp-system/src/main/java/com/erp/system/service/impl/HrEmployeeProfileServiceImpl.java`
- Create: `erp-modules/erp-system/src/main/java/com/erp/system/support/HrEmployeeFieldRegistry.java`
- Create: `erp-modules/erp-system/src/main/java/com/erp/system/domain/vo/HrEmployeeProfileVo.java`
- Create: `erp-modules/erp-system/src/main/java/com/erp/system/domain/vo/HrEmployeeSummaryVo.java`
- Create: `erp-modules/erp-system/src/main/java/com/erp/system/domain/HrSensitiveAccessLog.java`
- Create: `erp-modules/erp-system/src/main/java/com/erp/system/mapper/HrSensitiveAccessLogMapper.java` and XML
- Replace: `erp-modules/erp-system/src/main/java/com/erp/system/controller/HrEmployeeProfileController.java`
- Replace: `erp-modules/erp-system/src/main/java/com/erp/system/controller/HrCompletenessController.java`
- Modify: `erp-modules/erp-system/src/main/resources/mapper/system/SysUserProfileMapper.xml`
- Modify: `erp-modules/erp-system/src/main/resources/mapper/system/SysUserMapper.xml`
- Test: `erp-modules/erp-system/src/test/java/com/erp/system/service/impl/HrEmployeeProfileServiceImplTest.java`
- Test: `erp-modules/erp-system/src/test/java/com/erp/system/support/HrEmployeeFieldRegistryTest.java`
- Test: `erp-modules/erp-system/src/test/java/com/erp/system/service/impl/HrSensitiveFieldRevealTest.java`

- [ ] **Step 1: Write failing employee-master tests**

Verify summary counts use all scoped rows rather than the current page; DTOs contain employee number and remark; derived organization/position/duration fields cannot be persisted; profile completeness is server-calculated; default list/detail/export is masked; reveal requires `hr:employee:sensitive:view` and writes a traceable audit event.

Build `HrEmployeeFieldRegistryTest` from the approved 66 spreadsheet headers plus `合同类型` and `社保类型`. Assert exactly 68 unique business fields, a storage owner (`SYS_USER`, `SYS_USER_PROFILE`, `RELATION`, or `DERIVED`) for each, editability/complete-group metadata, and no writable derived field. For every writable `SYS_USER_PROFILE` key, assert the insert and update XML both contain its column; assert confirmation mapping covers every onboarding-to-master field.

- [ ] **Step 2: Implement HR-specific DTO queries**

Add employee list, detail, summary, form-options, derived-preview, completeness-by-employee, and completeness-by-department methods. Preserve the existing `SysUserProfileDerivedWriteBoundaryTest` contract: company, department levels, store, position, department supervisor, work years, and company years remain read-only. Use the 68-entry registry for completeness and DTO mapping rather than ad hoc UI arrays. Employee update also uses PATCH semantics, omits untouched sensitive values, rejects masking placeholders, and keeps employee number read-only for formal employees.

Fix `SysUserProfileMapper.xml` so every non-derived editable field is actually inserted/updated, including `direct_supervisor_user_id`, while derived columns remain select-only. On the employee update endpoint use `@Log(title = "人事员工档案", businessType = BusinessType.UPDATE, isSaveRequestData = false, isSaveResponseData = false)` because the payload contains PII.

- [ ] **Step 3: Implement reveal and safe exports**

Default list/detail/export DTOs are masked and the default list has no ID, bank, or address columns. Add `POST /hr/employee/{userId}/sensitive/reveal` with body `{ fieldKey }` and `hr:employee:sensitive:view`; accept only an explicit whitelist. Add `/hr/employee/export-sensitive` with its separate permission. Both first apply employee data scope, then insert `hr_sensitive_access_log` in the same transaction. Reveal audit records employee ID, field key, operator, IP, time, and outcome; export audit records filter summary, requested field set, scoped row count, operator, IP, time, and outcome. Neither audit stores the value. Use `BusinessType.OTHER` for reveal and `BusinessType.EXPORT` for sensitive export, with `isSaveRequestData = false` and `isSaveResponseData = false` on both annotations.

- [ ] **Step 4: Replace completeness endpoints**

Expose `/hr/completeness/summary`, `/employees`, and `/departments`. The employee query accepts `accountConfigurationStatus=MISSING` and returns the scoped account-risk queue with onboarding/employee/config links. Remove browser-page-derived counts from the API contract.

- [ ] **Step 5: Run tests and commit**

Run:

```bash
mvn -pl erp-modules/erp-system -am \
  -Dtest=HrEmployeeProfileServiceImplTest,HrEmployeeFieldRegistryTest,HrSensitiveFieldRevealTest,SysUserProfileDerivedWriteBoundaryTest,SysUserProfileDerivationServiceTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: PASS. Commit with `git commit -m "feat: harden hr employee profiles"`.

## Task 9: Controller, mapper, and transaction verification

**Files:**
- Modify: `HrPersonnelControllerSourceTest.java`
- Modify: `erp-modules/erp-system/pom.xml`
- Create: `erp-modules/erp-system/src/test/resources/application-hr-onboarding-it.yml`
- Create: `erp-modules/erp-system/src/test/resources/hr-onboarding-it-schema.sql`
- Create: `HrOnboardingTransactionTest.java`
- Create: `HrOnboardingApiContractTest.java`

- [ ] **Step 1: Add endpoint and logging contract tests**

Assert every specified endpoint and permission exists, onboarding create/edit/confirm/import and employee-profile update logs disable request/response capture, validation failures expose `errorCode`, `fieldErrors`, and `blockingCodes`, summary contains `todayArrivalCount`, `pendingConfirmCount`, `accountConfigurationRiskCount`, and masked `todayTasks`, `allowedActions` uses only the six fixed action values, and no controller delegates onboarding to `ISysUserService.selectUserList`.

- [ ] **Step 2: Add a MySQL transaction integration test**

Add test-scope `org.testcontainers:testcontainers-junit-jupiter` and `org.testcontainers:testcontainers-mysql` dependencies; Spring Boot 4.0.3 manages the Testcontainers 2.x versions, so do not hardcode them. `application-hr-onboarding-it.yml` disables Nacos/discovery/Redis-dependent startup pieces and points the dynamic master datasource at properties supplied by `@DynamicPropertySource`. `hr-onboarding-it-schema.sql` creates the minimal MySQL 5.7 schema required by the production mappers plus the new migration tables.

`HrOnboardingTransactionTest` uses `@ActiveProfiles("hr-onboarding-it")` and `MySQLContainer("mysql:5.7")`, applies the fixture schema and migration, and seeds two department scopes, two supervisors, one post, one role, one config, and READY onboarding records. Verify successful confirmation creates all associations; inject a profile-write failure and assert no user, profile, role, post, shop, risk, or confirmed row remains. Verify an HR user can confirm in scope, a supervisor cannot read/lock/confirm the other scope, and concurrent same-key confirmation creates one user while same-row different-key confirmation returns the stable conflict.

- [ ] **Step 3: Run targeted backend verification**

```bash
mvn -pl erp-modules/erp-system -am \
  -Dtest=HrOnboardingSchemaSourceTest,HrOnboardingMapperBindingTest,HrOnboardingFieldRegistryTest,HrOnboardingRuleServiceTest,HrSensitiveFieldMaskerTest,HrOnboardingAccessServiceTest,HrOnboardingServiceImplTest,HrOnboardingControllerTest,HrOnboardingPositionConfigServiceTest,HrOnboardingConfirmationServiceTest,HrOnboardingExcelParserTest,HrOnboardingImportServiceImplTest,HrOnboardingImportRowProcessorTest,HrEmployeeFieldRegistryTest,HrEmployeeProfileServiceImplTest,HrSensitiveFieldRevealTest,HrOnboardingApiContractTest,SysUserProfileDerivedWriteBoundaryTest,SysUserProfileDerivationServiceTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: PASS.

Then run the MySQL test explicitly (Docker must be available):

```bash
mvn -pl erp-modules/erp-system -am \
  -Dtest=HrOnboardingTransactionTest \
  -Dspring.profiles.active=hr-onboarding-it \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: PASS; skipping is not acceptable for backend acceptance.

- [ ] **Step 4: Run module regression and SQL mirror check**

```bash
cmp sql/erp_user_hr_onboarding_20260710.sql docker/mysql/db/erp_user_hr_onboarding_20260710.sql
cmp sql/erp_user_employee_profile_20260706.sql docker/mysql/db/erp_user_employee_profile_20260706.sql
mvn -pl erp-modules/erp-system -am test
```

Expected: all three commands exit 0.

- [ ] **Step 5: Commit**

```bash
git add erp-modules/erp-system/pom.xml \
  erp-modules/erp-system/src/test/resources/application-hr-onboarding-it.yml \
  erp-modules/erp-system/src/test/resources/hr-onboarding-it-schema.sql \
  erp-modules/erp-system/src/test/java/com/erp/system/controller/HrPersonnelControllerSourceTest.java \
  erp-modules/erp-system/src/test/java/com/erp/system/controller/HrOnboardingApiContractTest.java \
  erp-modules/erp-system/src/test/java/com/erp/system/service/impl/HrOnboardingTransactionTest.java
git commit -m "test: verify hr onboarding backend"
```

## Backend plan acceptance

- An onboarding record can exist without `sys_user`.
- State transitions, required fields, complete percentages, and actions come from one backend rule service.
- Confirmation is locked, optimistic, idempotent, transactional, and supports explicit account binding.
- Import is preview-first and preserves row-level errors.
- Default responses and mobile responses never expose full sensitive values.
- Employee master derived fields remain read-only, while all legitimate editable fields persist.
- Desktop and mobile can consume stable shared APIs without user-management permissions.
