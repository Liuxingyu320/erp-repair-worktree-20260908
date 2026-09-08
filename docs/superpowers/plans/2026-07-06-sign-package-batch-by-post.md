# Sign Package Batch By Post Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a岗位签约方案 workflow that generates multiple employee signing-package drafts by post, then lets operators review each draft before sending.

**Architecture:** Add a small plan layer on top of the existing `OaSignPackage` flow. Plans store default package fields and a fixed template list; batch creation creates only `draft` packages and existing send/sign/download flows continue to own document generation and evidence. Employee candidates come from the system service through an internal Feign API, keeping OA from depending directly on system mappers.

**Tech Stack:** Spring Boot microservices, MyBatis XML mappers, OpenFeign, Vue 2 + Element UI, Node assertion tests, JUnit 5 + Mockito.

---

## File Structure

Backend API/system:

- Create `erp-api/erp-api-system/src/main/java/com/erp/system/api/domain/SignCandidateUser.java`: compact candidate DTO returned to OA.
- Create `erp-api/erp-api-system/src/main/java/com/erp/system/api/domain/SignCandidateUserQuery.java`: internal candidate query DTO.
- Modify `erp-api/erp-api-system/src/main/java/com/erp/system/api/RemoteUserService.java`: add internal candidate lookup.
- Modify `erp-api/erp-api-system/src/main/java/com/erp/system/api/factory/RemoteUserFallbackFactory.java`: fallback for candidate lookup.
- Modify `erp-modules/erp-system/src/main/java/com/erp/system/controller/SysUserController.java`: add `@InnerAuth` candidate endpoint.
- Modify `erp-modules/erp-system/src/main/resources/mapper/system/SysUserMapper.xml`: support exact `params.postName` filtering.
- Modify `erp-modules/erp-system/src/test/java/com/erp/system/service/impl/SysUserServiceImplTest.java` or add a focused source-contract test if mapper-level tests are not available.

Backend OA:

- Create `erp-modules/erp-oa/src/main/java/com/erp/oa/domain/OaSignPlan.java`: plan defaults.
- Create `erp-modules/erp-oa/src/main/java/com/erp/oa/domain/OaSignPlanTemplate.java`: plan-template binding.
- Create `erp-modules/erp-oa/src/main/java/com/erp/oa/domain/dto/OaSignBatchPreviewRequest.java`: preview query.
- Create `erp-modules/erp-oa/src/main/java/com/erp/oa/domain/dto/OaSignBatchPreviewRow.java`: preview row.
- Create `erp-modules/erp-oa/src/main/java/com/erp/oa/domain/dto/OaSignBatchCreateDraftsRequest.java`: create-drafts request.
- Create `erp-modules/erp-oa/src/main/java/com/erp/oa/domain/dto/OaSignBatchCreateDraftsResult.java`: create-drafts result.
- Create `erp-modules/erp-oa/src/main/java/com/erp/oa/mapper/OaSignPlanMapper.java`.
- Create `erp-modules/erp-oa/src/main/resources/mapper/oa/OaSignPlanMapper.xml`.
- Create `erp-modules/erp-oa/src/main/java/com/erp/oa/service/IOaSignPlanService.java`.
- Create `erp-modules/erp-oa/src/main/java/com/erp/oa/service/IOaSignBatchService.java`.
- Create `erp-modules/erp-oa/src/main/java/com/erp/oa/service/impl/OaSignPlanServiceImpl.java`.
- Create `erp-modules/erp-oa/src/main/java/com/erp/oa/service/impl/OaSignBatchServiceImpl.java`.
- Modify `erp-modules/erp-oa/src/main/java/com/erp/oa/domain/OaSignPackage.java`: add `sourcePlanId` and `sourcePlanName`.
- Modify `erp-modules/erp-oa/src/main/java/com/erp/oa/mapper/OaSignPackageMapper.java` and XML: persist source plan fields.
- Modify `erp-modules/erp-oa/src/main/java/com/erp/oa/service/IOaSignPackageService.java`: add draft update.
- Modify `erp-modules/erp-oa/src/main/java/com/erp/oa/service/impl/OaSignPackageServiceImpl.java`: draft update and plan-template send branch.
- Modify `erp-modules/erp-oa/src/main/java/com/erp/oa/controller/OaSignPackageController.java`: plan, batch, and draft update endpoints.
- Add `sql/erp_oa_sign_plan_20260706.sql` and `docker/mysql/db/erp_oa_sign_plan_20260706.sql`.

Frontend:

- Modify `erp-ui/src/api/oa/signPackage.js`: add plan, batch, and draft update API calls.
- Modify `erp-ui/src/views/oa/signPackage/index.vue`: add plan tab, batch dialog, preview table, draft edit entry.
- Modify `erp-ui/test/signPackageModule.test.js`: source-contract coverage for new UI/API surface.

Tests:

- Create `erp-modules/erp-oa/src/test/java/com/erp/oa/service/impl/OaSignPlanServiceImplTest.java`.
- Create `erp-modules/erp-oa/src/test/java/com/erp/oa/service/impl/OaSignBatchServiceImplTest.java`.
- Modify `erp-modules/erp-oa/src/test/java/com/erp/oa/service/impl/OaSignPackageServiceImplTest.java`.
- Modify `erp-modules/erp-oa/src/test/java/com/erp/oa/mapper/OaMapperBindingTest.java`.

---

### Task 1: Database, Domains, And Mapper Binding

**Files:**
- Create: `sql/erp_oa_sign_plan_20260706.sql`
- Create: `docker/mysql/db/erp_oa_sign_plan_20260706.sql`
- Create: `erp-modules/erp-oa/src/main/java/com/erp/oa/domain/OaSignPlan.java`
- Create: `erp-modules/erp-oa/src/main/java/com/erp/oa/domain/OaSignPlanTemplate.java`
- Create: `erp-modules/erp-oa/src/main/java/com/erp/oa/mapper/OaSignPlanMapper.java`
- Create: `erp-modules/erp-oa/src/main/resources/mapper/oa/OaSignPlanMapper.xml`
- Modify: `erp-modules/erp-oa/src/main/java/com/erp/oa/domain/OaSignPackage.java`
- Modify: `erp-modules/erp-oa/src/main/java/com/erp/oa/mapper/OaSignPackageMapper.java`
- Modify: `erp-modules/erp-oa/src/main/resources/mapper/oa/OaSignPackageMapper.xml`
- Modify: `erp-modules/erp-oa/src/test/java/com/erp/oa/mapper/OaMapperBindingTest.java`

- [ ] **Step 1: Write mapper binding test first**

Add assertions to `OaMapperBindingTest`:

```java
@Test
@DisplayName("员工签约方案Mapper声明完整")
void shouldBindSignPlanMappers() throws Exception
{
    Configuration configuration = new Configuration();
    parseMapper(configuration, "mapper/oa/OaSignPlanMapper.xml");
    parseMapper(configuration, "mapper/oa/OaSignPackageMapper.xml");

    assertMapped(configuration, OaSignPlanMapper.class, "insertOaSignPlan");
    assertMapped(configuration, OaSignPlanMapper.class, "updateOaSignPlan");
    assertMapped(configuration, OaSignPlanMapper.class, "selectOaSignPlanById");
    assertMapped(configuration, OaSignPlanMapper.class, "selectOaSignPlanList");
    assertMapped(configuration, OaSignPlanMapper.class, "deletePlanTemplatesByPlanId");
    assertMapped(configuration, OaSignPlanMapper.class, "batchInsertPlanTemplates");
    assertMapped(configuration, OaSignPlanMapper.class, "selectPlanTemplatesByPlanId");
    assertMapped(configuration, OaSignPlanMapper.class, "selectActiveTemplatesByPlanId");
    assertMapped(configuration, OaSignPackageMapper.class, "selectOpenPackageByEmployeeAndPlan");
}
```

- [ ] **Step 2: Run mapper test and confirm it fails**

Run from `erp-modules`:

```bash
mvn -pl erp-oa -Dtest=OaMapperBindingTest#shouldBindSignPlanMappers test
```

Expected: FAIL because `OaSignPlanMapper.xml` and mapper statements do not exist yet.

- [ ] **Step 3: Add SQL schema**

Create identical SQL files in `sql/` and `docker/mysql/db/`:

```sql
CREATE TABLE IF NOT EXISTS oa_sign_plan (
    plan_id bigint(20) NOT NULL AUTO_INCREMENT COMMENT '签约方案ID',
    plan_name varchar(120) NOT NULL COMMENT '方案名称',
    scenario varchar(32) NOT NULL DEFAULT 'onboard' COMMENT '签约场景',
    post_name varchar(64) DEFAULT NULL COMMENT '适用岗位',
    employment_type varchar(32) DEFAULT NULL COMMENT '用工类型',
    social_type varchar(20) DEFAULT NULL COMMENT '社保口径',
    service_person_type varchar(32) DEFAULT NULL COMMENT '劳务人员类型',
    insurance_type varchar(64) DEFAULT NULL COMMENT '保险类型',
    post_level_snapshot varchar(32) DEFAULT NULL COMMENT '岗位等级快照',
    salary_version varchar(20) DEFAULT NULL COMMENT '薪酬版本',
    entry_date varchar(10) DEFAULT NULL COMMENT '入职日期',
    contract_start_date varchar(10) DEFAULT NULL COMMENT '合同开始日期',
    contract_end_date varchar(10) DEFAULT NULL COMMENT '合同结束日期',
    probation_start_date varchar(10) DEFAULT NULL COMMENT '试用期开始日期',
    probation_end_date varchar(10) DEFAULT NULL COMMENT '试用期结束日期',
    base_salary decimal(16,2) DEFAULT NULL COMMENT '基本工资',
    post_salary decimal(16,2) DEFAULT NULL COMMENT '岗位工资',
    field_allowance decimal(16,2) DEFAULT NULL COMMENT '综合驻外补贴',
    salary_total decimal(16,2) DEFAULT NULL COMMENT '工资合计',
    shop_dept_id bigint(20) DEFAULT NULL COMMENT '所属店铺/组织ID',
    status char(1) NOT NULL DEFAULT '0' COMMENT '状态（0启用 1停用）',
    sort_order int(11) DEFAULT 100 COMMENT '排序',
    create_by varchar(64) DEFAULT '' COMMENT '创建者',
    create_time datetime DEFAULT NULL COMMENT '创建时间',
    update_by varchar(64) DEFAULT '' COMMENT '更新者',
    update_time datetime DEFAULT NULL COMMENT '更新时间',
    remark varchar(500) DEFAULT NULL COMMENT '备注',
    PRIMARY KEY (plan_id),
    KEY idx_oa_sign_plan_shop (shop_dept_id, status),
    KEY idx_oa_sign_plan_post (post_name, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='OA员工签约方案';

CREATE TABLE IF NOT EXISTS oa_sign_plan_template (
    id bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键',
    plan_id bigint(20) NOT NULL COMMENT '签约方案ID',
    template_id bigint(20) NOT NULL COMMENT '签约模板ID',
    template_type varchar(64) NOT NULL COMMENT '模板类型',
    sort_order int(11) DEFAULT 100 COMMENT '排序',
    create_time datetime DEFAULT NULL COMMENT '创建时间',
    PRIMARY KEY (id),
    KEY idx_oa_sign_plan_template_plan (plan_id, sort_order),
    KEY idx_oa_sign_plan_template_template (template_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='OA签约方案模板';

SET @erp_db = DATABASE();
SET @sql = IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = @erp_db AND TABLE_NAME = 'oa_sign_package' AND COLUMN_NAME = 'source_plan_id') = 0,
    'ALTER TABLE oa_sign_package ADD COLUMN source_plan_id bigint(20) DEFAULT NULL COMMENT ''来源签约方案ID'' AFTER shop_dept_name',
    'DO 0');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = @erp_db AND TABLE_NAME = 'oa_sign_package' AND COLUMN_NAME = 'source_plan_name') = 0,
    'ALTER TABLE oa_sign_package ADD COLUMN source_plan_name varchar(120) DEFAULT NULL COMMENT ''来源签约方案名称快照'' AFTER source_plan_id',
    'DO 0');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
```

- [ ] **Step 4: Add OA domain and mapper declarations**

`OaSignPlan` follows `OaSignPackage` JavaBean style and includes all SQL columns plus `List<OaSignPlanTemplate> templates`.

`OaSignPlanTemplate` fields:

```java
private Long id;
private Long planId;
private Long templateId;
private String templateType;
private Integer sortOrder;
private OaSignTemplate template;
```

`OaSignPlanMapper` methods:

```java
int insertOaSignPlan(OaSignPlan plan);
int updateOaSignPlan(OaSignPlan plan);
OaSignPlan selectOaSignPlanById(Long planId);
List<OaSignPlan> selectOaSignPlanList(OaSignPlan plan);
int deletePlanTemplatesByPlanId(Long planId);
int batchInsertPlanTemplates(List<OaSignPlanTemplate> templates);
List<OaSignPlanTemplate> selectPlanTemplatesByPlanId(Long planId);
List<OaSignTemplate> selectActiveTemplatesByPlanId(Long planId);
```

Add to `OaSignPackage`:

```java
private Long sourcePlanId;
private String sourcePlanName;
```

Add getters and setters using existing file style.

Add to `OaSignPackageMapper`:

```java
OaSignPackage selectOpenPackageByEmployeeAndPlan(@Param("employeeId") Long employeeId, @Param("sourcePlanId") Long sourcePlanId);
```

- [ ] **Step 5: Add MyBatis XML**

`OaSignPlanMapper.xml` must include a result map, `baseColumns`, CRUD statements, template delete/insert/list, and active template lookup joined to `oa_sign_template` with `t.status = '0'`.

`OaSignPackageMapper.xml` must include `source_plan_id` and `source_plan_name` in result map, `baseColumns`, insert, update, and add:

```xml
<select id="selectOpenPackageByEmployeeAndPlan" resultMap="OaSignPackageResult">
    select <include refid="baseColumns"/>
    from oa_sign_package
    where employee_id = #{employeeId}
      and source_plan_id = #{sourcePlanId}
      and status in ('draft', 'pending_sign', 'part_viewed')
    order by package_id desc
    limit 1
</select>
```

- [ ] **Step 6: Run mapper test**

```bash
mvn -pl erp-oa -Dtest=OaMapperBindingTest#shouldBindSignPlanMappers test
```

Expected: PASS.

---

### Task 2: System Service Candidate Employee Lookup

**Files:**
- Create: `erp-api/erp-api-system/src/main/java/com/erp/system/api/domain/SignCandidateUser.java`
- Create: `erp-api/erp-api-system/src/main/java/com/erp/system/api/domain/SignCandidateUserQuery.java`
- Modify: `erp-api/erp-api-system/src/main/java/com/erp/system/api/RemoteUserService.java`
- Modify: `erp-api/erp-api-system/src/main/java/com/erp/system/api/factory/RemoteUserFallbackFactory.java`
- Modify: `erp-modules/erp-system/src/main/java/com/erp/system/controller/SysUserController.java`
- Modify: `erp-modules/erp-system/src/main/resources/mapper/system/SysUserMapper.xml`

- [ ] **Step 1: Write source-contract test**

Add or extend a system test to assert the endpoint and mapper support exact post names. If no lightweight system mapper parser exists, create `erp-modules/erp-system/src/test/java/com/erp/system/mapper/SysUserMapperSourceTest.java`:

```java
package com.erp.system.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import org.apache.ibatis.io.Resources;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("系统用户签约候选查询")
class SysUserMapperSourceTest
{
    @Test
    @DisplayName("用户查询支持按岗位名称过滤")
    void shouldFilterUsersByPostNameForSignCandidates() throws Exception
    {
        String xml = new String(Resources.getResourceAsStream("mapper/system/SysUserMapper.xml").readAllBytes(),
                StandardCharsets.UTF_8);
        assertThat(xml)
                .contains("params.postName")
                .contains("sp.post_name = #{params.postName}");
    }
}
```

- [ ] **Step 2: Run test and confirm it fails**

```bash
mvn -pl erp-system -Dtest=SysUserMapperSourceTest test
```

Expected: FAIL because `params.postName` is not in the mapper yet.

- [ ] **Step 3: Add candidate DTOs and Feign method**

`SignCandidateUserQuery` fields:

```java
private String postName;
private Long deptId;
private String keyword;
private Long[] userIds;
private Integer limit;
```

`SignCandidateUser` fields:

```java
private Long userId;
private String userName;
private String nickName;
private String phonenumber;
private Long deptId;
private String deptName;
private String postNames;
private String remark;
```

Add to `RemoteUserService`:

```java
@PostMapping("/user/sign-candidates")
R<List<SignCandidateUser>> listSignCandidates(@RequestBody SignCandidateUserQuery query,
        @RequestHeader(SecurityConstants.FROM_SOURCE) String source);
```

Fallback returns `R.fail("获取签约候选员工失败:" + throwable.getMessage())`.

- [ ] **Step 4: Add system endpoint**

In `SysUserController`:

```java
@InnerAuth
@PostMapping("/sign-candidates")
public R<List<SignCandidateUser>> signCandidates(@RequestBody SignCandidateUserQuery query)
{
    SysUser user = new SysUser();
    user.setStatus("0");
    if (query != null)
    {
        user.setDeptId(query.getDeptId());
        user.getParams().put("postName", query.getPostName());
        user.getParams().put("keyword", query.getKeyword());
        user.getParams().put("userIds", query.getUserIds());
    }
    List<SysUser> users = userService.selectUserList(user);
    int limit = query == null || query.getLimit() == null || query.getLimit() <= 0 ? 200 : Math.min(query.getLimit(), 500);
    List<SignCandidateUser> rows = users.stream().limit(limit).map(this::toSignCandidateUser).collect(Collectors.toList());
    return R.ok(rows);
}

private SignCandidateUser toSignCandidateUser(SysUser user)
{
    SignCandidateUser candidate = new SignCandidateUser();
    candidate.setUserId(user.getUserId());
    candidate.setUserName(user.getUserName());
    candidate.setNickName(user.getNickName());
    candidate.setPhonenumber(user.getPhonenumber());
    candidate.setDeptId(user.getDeptId());
    candidate.setDeptName(user.getDept() == null ? null : user.getDept().getDeptName());
    candidate.setPostNames(user.getPostNames());
    candidate.setRemark(user.getRemark());
    return candidate;
}
```

- [ ] **Step 5: Add mapper filters**

In `SysUserMapper.xml` inside `selectUserList`, add:

```xml
<if test="params.postName != null and params.postName != ''">
    AND EXISTS (
        SELECT 1
        FROM sys_user_post sign_sup
             INNER JOIN sys_post sp ON sp.post_id = sign_sup.post_id
        WHERE sign_sup.user_id = u.user_id
          AND sp.status = '0'
          AND sp.post_name = #{params.postName}
    )
</if>
<if test="params.keyword != null and params.keyword != ''">
    AND (
        u.nick_name like concat('%', #{params.keyword}, '%')
        OR u.user_name like concat('%', #{params.keyword}, '%')
        OR u.phonenumber like concat('%', #{params.keyword}, '%')
    )
</if>
<if test="params.userIds != null and params.userIds.length > 0">
    AND u.user_id in
    <foreach collection="params.userIds" item="candidateUserId" open="(" close=")" separator=",">
        #{candidateUserId}
    </foreach>
</if>
```

- [ ] **Step 6: Run system test**

```bash
mvn -pl erp-system -Dtest=SysUserMapperSourceTest test
```

Expected: PASS.

---

### Task 3: Sign Plan Service

**Files:**
- Create: `erp-modules/erp-oa/src/main/java/com/erp/oa/service/IOaSignPlanService.java`
- Create: `erp-modules/erp-oa/src/main/java/com/erp/oa/service/impl/OaSignPlanServiceImpl.java`
- Create: `erp-modules/erp-oa/src/test/java/com/erp/oa/service/impl/OaSignPlanServiceImplTest.java`

- [ ] **Step 1: Write failing service tests**

Tests:

```java
@Test
@DisplayName("保存签约方案时必须绑定启用模板")
void shouldRejectPlanWithoutTemplates()
```

```java
@Test
@DisplayName("保存签约方案时写入方案和模板绑定")
void shouldSavePlanAndTemplateBindings()
```

The second test captures `OaSignPlan` and `List<OaSignPlanTemplate>` and asserts plan name, scenario default `onboard`, `createBy`, and template IDs.

- [ ] **Step 2: Run test and confirm it fails**

```bash
mvn -pl erp-oa -Dtest=OaSignPlanServiceImplTest test
```

Expected: FAIL because service does not exist.

- [ ] **Step 3: Implement service interface**

```java
public interface IOaSignPlanService
{
    List<OaSignPlan> selectPlanList(OaSignPlan plan, Long selectedShopDeptId);
    OaSignPlan getPlanDetail(Long planId, Long selectedShopDeptId);
    OaSignPlan savePlan(OaSignPlan plan, Long selectedShopDeptId);
    OaSignPlan updatePlanStatus(Long planId, String status, Long selectedShopDeptId);
}
```

- [ ] **Step 4: Implement service**

`OaSignPlanServiceImpl` rules:

- Resolve shop with `shopScopeService.resolveRequiredShopDept`.
- Default blank `scenario` to `onboard`.
- Default blank `status` to `0`.
- Reject blank `planName` with `ServiceException("方案名称不能为空")`.
- Reject missing templates with `ServiceException("签约方案至少需要绑定一个模板")`.
- For each template binding, load template by ID and reject null or non-active with `ServiceException("签约方案模板不存在或已停用")`.
- Insert or update plan, delete old template rows, insert captured template rows.
- Return detail with template list.

- [ ] **Step 5: Run service tests**

```bash
mvn -pl erp-oa -Dtest=OaSignPlanServiceImplTest test
```

Expected: PASS.

---

### Task 4: Draft Editing And Source Plan Fields

**Files:**
- Modify: `erp-modules/erp-oa/src/main/java/com/erp/oa/service/IOaSignPackageService.java`
- Modify: `erp-modules/erp-oa/src/main/java/com/erp/oa/service/impl/OaSignPackageServiceImpl.java`
- Modify: `erp-modules/erp-oa/src/main/java/com/erp/oa/controller/OaSignPackageController.java`
- Modify: `erp-modules/erp-oa/src/test/java/com/erp/oa/service/impl/OaSignPackageServiceImplTest.java`

- [ ] **Step 1: Write failing draft-edit tests**

Add tests:

```java
@Test
@DisplayName("草稿签约包允许更新快照字段")
void shouldUpdateDraftPackageSnapshots()
```

```java
@Test
@DisplayName("非草稿签约包不允许编辑")
void shouldRejectEditingNonDraftPackage()
```

The first test verifies `packageMapper.updateOaSignPackage` receives the existing `packageId`, updated name/phone/id card/post fields, and `updateBy`.

- [ ] **Step 2: Run focused test and confirm it fails**

```bash
mvn -pl erp-oa -Dtest=OaSignPackageServiceImplTest#shouldUpdateDraftPackageSnapshots test
```

Expected: FAIL because `updateDraftPackage` does not exist.

- [ ] **Step 3: Add service method**

`IOaSignPackageService`:

```java
OaSignPackage updateDraftPackage(Long packageId, OaSignPackage signPackage, Long selectedShopDeptId);
```

`OaSignPackageServiceImpl.updateDraftPackage`:

- Load scoped package with `assertAndGetScopedPackage`.
- Reject unless status is `draft`.
- Preserve `employeeId`, `shopDeptId`, `sourcePlanId`, `sourcePlanName`.
- Update editable snapshot/default fields from request.
- Set `updateBy`.
- Call mapper update.
- Return detail.

Reject message: `仅草稿签约包可以编辑`.

- [ ] **Step 4: Add controller endpoint**

```java
@RequiresPermissions("oa:signPackage:add")
@IdempotentSubmit(timeout = 30)
@Log(title = "员工签约包编辑", businessType = BusinessType.UPDATE)
@PutMapping("/{packageId}")
public AjaxResult update(@PathVariable("packageId") Long packageId, @Validated @RequestBody OaSignPackage signPackage,
        HttpServletRequest request)
{
    return success(signPackageService.updateDraftPackage(packageId, signPackage, resolveShopDeptId(request)));
}
```

- [ ] **Step 5: Run package service tests**

```bash
mvn -pl erp-oa -Dtest=OaSignPackageServiceImplTest test
```

Expected: PASS.

---

### Task 5: Batch Preview And Draft Creation Service

**Files:**
- Create: `erp-modules/erp-oa/src/main/java/com/erp/oa/domain/dto/OaSignBatchPreviewRequest.java`
- Create: `erp-modules/erp-oa/src/main/java/com/erp/oa/domain/dto/OaSignBatchPreviewRow.java`
- Create: `erp-modules/erp-oa/src/main/java/com/erp/oa/domain/dto/OaSignBatchCreateDraftsRequest.java`
- Create: `erp-modules/erp-oa/src/main/java/com/erp/oa/domain/dto/OaSignBatchCreateDraftsResult.java`
- Create: `erp-modules/erp-oa/src/main/java/com/erp/oa/service/IOaSignBatchService.java`
- Create: `erp-modules/erp-oa/src/main/java/com/erp/oa/service/impl/OaSignBatchServiceImpl.java`
- Modify: `erp-modules/erp-oa/src/main/java/com/erp/oa/controller/OaSignPackageController.java`
- Create: `erp-modules/erp-oa/src/test/java/com/erp/oa/service/impl/OaSignBatchServiceImplTest.java`

- [ ] **Step 1: Write failing batch tests**

Tests:

```java
@Test
@DisplayName("批量预览按方案岗位获取候选员工并标记缺失字段")
void shouldPreviewCandidatesAndMissingFields()
```

```java
@Test
@DisplayName("批量创建只为资料完整员工生成草稿")
void shouldCreateDraftsOnlyForCompleteRows()
```

```java
@Test
@DisplayName("批量创建跳过同员工同方案未关闭草稿")
void shouldSkipOpenPackageForSameEmployeeAndPlan()
```

- [ ] **Step 2: Run tests and confirm they fail**

```bash
mvn -pl erp-oa -Dtest=OaSignBatchServiceImplTest test
```

Expected: FAIL because batch service does not exist.

- [ ] **Step 3: Add DTO fields**

`OaSignBatchPreviewRequest`:

```java
private Long planId;
private String postName;
private Long deptId;
private String keyword;
private Long[] employeeIds;
```

`OaSignBatchPreviewRow`:

```java
private Boolean selected;
private Boolean creatable;
private String skipReason;
private List<String> missingFields;
private Long employeeId;
private String employeeNameSnapshot;
private String employeePhoneSnapshot;
private String employeeIdCardSnapshot;
private String employeeAddressSnapshot;
private Long deptIdSnapshot;
private String deptNameSnapshot;
private String postNameSnapshot;
private String postLevelSnapshot;
private String scenario;
private String employmentType;
private String socialType;
private String servicePersonType;
private String insuranceType;
private String salaryVersion;
private String entryDate;
private String contractStartDate;
private String contractEndDate;
private String probationStartDate;
private String probationEndDate;
private BigDecimal baseSalary;
private BigDecimal postSalary;
private BigDecimal fieldAllowance;
private BigDecimal salaryTotal;
private List<OaSignTemplate> templates;
```

`OaSignBatchCreateDraftsRequest`:

```java
private Long planId;
private List<OaSignBatchPreviewRow> rows;
```

`OaSignBatchCreateDraftsResult`:

```java
private Integer createdCount;
private Integer skippedCount;
private List<Long> packageIds;
private List<OaSignBatchPreviewRow> skippedRows;
```

- [ ] **Step 4: Implement service interface**

```java
public interface IOaSignBatchService
{
    List<OaSignBatchPreviewRow> preview(OaSignBatchPreviewRequest request, Long selectedShopDeptId);
    OaSignBatchCreateDraftsResult createDrafts(OaSignBatchCreateDraftsRequest request, Long selectedShopDeptId);
}
```

- [ ] **Step 5: Implement preview**

`OaSignBatchServiceImpl.preview`:

- Resolve shop with `shopScopeService.resolveRequiredShopDept`.
- Load active plan detail.
- Call `remoteUserService.listSignCandidates(query, SecurityConstants.INNER)`.
- For each candidate, create row from candidate + plan defaults.
- `employeeNameSnapshot = nickName` fallback `userName`.
- `postNameSnapshot = request.postName` fallback candidate `postNames` fallback plan `postName`.
- `templates = plan active templates`.
- `missingFields` includes `员工姓名`, `手机号`, `身份证号`, `签约场景`, and `模板清单` when blank.
- `creatable = missingFields.isEmpty()` unless duplicate open package exists.

- [ ] **Step 6: Implement createDrafts**

Rules:

- Reject blank plan ID with `ServiceException("请选择签约方案")`.
- For each selected row, recompute missing fields server-side.
- Skip rows with missing fields and add `skipReason = "资料不完整"`.
- Skip duplicate open package and add `skipReason = "同员工同方案已有未完成签约包"`.
- Insert `OaSignPackage` with status `draft`, `sourcePlanId`, `sourcePlanName`, `shopDeptId`, and row snapshot fields.
- Return created IDs.

- [ ] **Step 7: Add controller endpoints**

```java
@RequiresPermissions("oa:signPackage:add")
@PostMapping("/batch/preview")
public AjaxResult batchPreview(@RequestBody OaSignBatchPreviewRequest request, HttpServletRequest servletRequest)
{
    return success(signBatchService.preview(request, resolveShopDeptId(servletRequest)));
}

@RequiresPermissions("oa:signPackage:add")
@IdempotentSubmit(timeout = 30)
@Log(title = "员工签约包批量生成", businessType = BusinessType.INSERT)
@PostMapping("/batch/createDrafts")
public AjaxResult batchCreateDrafts(@RequestBody OaSignBatchCreateDraftsRequest request, HttpServletRequest servletRequest)
{
    return success(signBatchService.createDrafts(request, resolveShopDeptId(servletRequest)));
}
```

- [ ] **Step 8: Run batch service tests**

```bash
mvn -pl erp-oa -Dtest=OaSignBatchServiceImplTest test
```

Expected: PASS.

---

### Task 6: Send Packages With Plan-Bound Templates

**Files:**
- Modify: `erp-modules/erp-oa/src/main/java/com/erp/oa/service/impl/OaSignPackageServiceImpl.java`
- Modify: `erp-modules/erp-oa/src/test/java/com/erp/oa/service/impl/OaSignPackageServiceImplTest.java`

- [ ] **Step 1: Write failing send test**

Add:

```java
@Test
@DisplayName("来源方案签约包发送时优先使用方案绑定模板")
void shouldSendPlanPackageWithPlanTemplates()
```

Mock package with `sourcePlanId = 20L`; mock `planMapper.selectActiveTemplatesByPlanId(20L)` returning one template; verify `templateMapper.selectMatchedActiveTemplates` is not called.

- [ ] **Step 2: Run test and confirm it fails**

```bash
mvn -pl erp-oa -Dtest=OaSignPackageServiceImplTest#shouldSendPlanPackageWithPlanTemplates test
```

Expected: FAIL because `OaSignPackageServiceImpl` does not inject plan mapper or branch by source plan.

- [ ] **Step 3: Implement send branch**

Add field:

```java
@Autowired
private OaSignPlanMapper planMapper;
```

Replace template lookup in `sendPackage` with:

```java
List<OaSignTemplate> matchedTemplates;
if (signPackage.getSourcePlanId() != null)
{
    matchedTemplates = planMapper.selectActiveTemplatesByPlanId(signPackage.getSourcePlanId());
}
else
{
    matchedTemplates = filterMatchedTemplatesByPostLevel(
            templateMapper.selectMatchedActiveTemplates(signPackage), signPackage.getPostLevelSnapshot());
}
```

For plan packages, still call `filterMatchedTemplatesByPostLevel` to preserve post-level safety:

```java
matchedTemplates = filterMatchedTemplatesByPostLevel(matchedTemplates, signPackage.getPostLevelSnapshot());
```

- [ ] **Step 4: Run package tests**

```bash
mvn -pl erp-oa -Dtest=OaSignPackageServiceImplTest test
```

Expected: PASS.

---

### Task 7: Frontend API And UI

**Files:**
- Modify: `erp-ui/src/api/oa/signPackage.js`
- Modify: `erp-ui/src/views/oa/signPackage/index.vue`
- Modify: `erp-ui/test/signPackageModule.test.js`

- [ ] **Step 1: Write failing frontend source tests**

Extend `apiContracts`:

```js
["listSignPlans", "url: '/oa/signPackage/plan/list'", "method: 'get'"],
["getSignPlan", "url: '/oa/signPackage/plan/' + planId", "method: 'get'"],
["saveSignPlan", "url: '/oa/signPackage/plan'", "method: 'post'"],
["updateSignPlan", "url: '/oa/signPackage/plan'", "method: 'put'"],
["updateSignPackage", "url: '/oa/signPackage/' + packageId", "method: 'put'"],
["previewSignPackageBatch", "url: '/oa/signPackage/batch/preview'", "method: 'post'"],
["createSignPackageDrafts", "url: '/oa/signPackage/batch/createDrafts'", "method: 'post'"]
```

Add UI assertions:

```js
assert.ok(desktopSource.includes("批量按岗位生成"), "desktop page should expose batch by post entry")
assert.ok(desktopSource.includes("签约方案"), "desktop page should expose sign plan management")
assert.ok(desktopSource.includes("batchPreviewRows"), "desktop batch dialog should render preview rows")
assert.ok(desktopSource.includes("missingFields"), "desktop batch dialog should show missing fields")
assert.ok(desktopSource.includes("handleEditPackage"), "desktop page should edit draft packages")
assert.ok(!desktopSource.includes("批量直接发送"), "desktop page should not expose direct batch send")
```

- [ ] **Step 2: Run frontend test and confirm it fails**

```bash
node test/signPackageModule.test.js
```

Expected: FAIL because APIs and UI strings do not exist.

- [ ] **Step 3: Add API methods**

In `signPackage.js`:

```js
export function listSignPlans(params) { return request({ url: '/oa/signPackage/plan/list', method: 'get', params, silentError: true }) }
export function getSignPlan(planId) { return request({ url: '/oa/signPackage/plan/' + planId, method: 'get' }) }
export function saveSignPlan(data) { return request({ url: '/oa/signPackage/plan', method: 'post', data }) }
export function updateSignPlan(data) { return request({ url: '/oa/signPackage/plan', method: 'put', data }) }
export function updateSignPackage(packageId, data) { return request({ url: '/oa/signPackage/' + packageId, method: 'put', data }) }
export function previewSignPackageBatch(data) { return request({ url: '/oa/signPackage/batch/preview', method: 'post', data, silentError: true }) }
export function createSignPackageDrafts(data) { return request({ url: '/oa/signPackage/batch/createDrafts', method: 'post', data }) }
```

- [ ] **Step 4: Add UI state and imports**

Import new API functions. Add data:

```js
planList: [],
planForm: this.defaultPlanForm(),
planOpen: false,
batchOpen: false,
batchForm: { planId: undefined, postName: "", deptId: undefined, keyword: "" },
batchPreviewRows: [],
batchLoading: false,
creatingDrafts: false,
editingPackage: false
```

- [ ] **Step 5: Add plan tab**

Add an `el-tab-pane label="签约方案" name="plan"` with table columns: scheme ID, scheme name, post, employment type, status, template count, action edit. Add plan form with scheme defaults and template multi-select using active template list.

- [ ] **Step 6: Add batch dialog**

Add button beside “新建签约包”:

```html
<el-button type="warning" size="mini" icon="el-icon-document-copy" @click="handleBatchByPost" v-hasPermi="['oa:signPackage:add']">批量按岗位生成</el-button>
```

Batch dialog sections:

- plan select
- post/dept/keyword filters
- preview button
- editable preview table with selected checkbox, employee fields, missing fields tag, template count
- create drafts button disabled when no selected creatable rows

- [ ] **Step 7: Add draft edit flow**

In package table actions, add:

```html
<el-button v-if="scope.row.status === 'draft'" type="text" size="mini" icon="el-icon-edit" @click="handleEditPackage(scope.row)" v-hasPermi="['oa:signPackage:add']">编辑</el-button>
```

Reuse the package dialog. `submitPackage` calls `updateSignPackage` when `packageForm.packageId` exists, otherwise `createSignPackage`.

- [ ] **Step 8: Run frontend test**

```bash
node test/signPackageModule.test.js
```

Expected: PASS.

---

### Task 8: Full Verification And Packaging

**Files:**
- Modify as needed only if verification exposes defects.

- [ ] **Step 1: Run backend focused tests**

From `erp-modules`:

```bash
mvn -pl erp-system -Dtest=SysUserMapperSourceTest test
mvn -pl erp-oa -Dtest=OaMapperBindingTest,OaSignPlanServiceImplTest,OaSignBatchServiceImplTest,OaSignPackageServiceImplTest test
```

Expected: all PASS.

- [ ] **Step 2: Run frontend source test**

From `erp-ui`:

```bash
node test/signPackageModule.test.js
```

Expected: PASS.

- [ ] **Step 3: Build OA jar**

From `erp-modules`:

```bash
mvn -pl erp-oa -DskipTests package
```

Expected: `erp-modules/erp-oa/target/erp-modules-oa.jar` exists.

- [ ] **Step 4: Copy OA jar to docker runtime**

```bash
cp erp-modules/erp-oa/target/erp-modules-oa.jar docker/erp/modules/oa/jar/erp-modules-oa.jar
```

- [ ] **Step 5: Manual smoke test**

Use admin UI:

1. Open `http://localhost:1025/`.
2. Go to OA -> 员工签约.
3. Create or edit “项目总监方案”, bind at least one enabled onboarding template.
4. Open “批量按岗位生成”, select the plan, preview candidates.
5. Fill missing required fields for one row.
6. Generate drafts.
7. Confirm generated package is `draft`.
8. Open draft edit, save a field change.
9. Send the draft.
10. Employee sees the package in mobile “我的签约”.

Expected: batch action creates draft only; send action generates documents from the plan-bound templates.

- [ ] **Step 6: Commit implementation**

Stage only files touched for this feature. Use:

```bash
git add sql/erp_oa_sign_plan_20260706.sql docker/mysql/db/erp_oa_sign_plan_20260706.sql \
  erp-api/erp-api-system/src/main/java/com/erp/system/api/domain/SignCandidateUser.java \
  erp-api/erp-api-system/src/main/java/com/erp/system/api/domain/SignCandidateUserQuery.java \
  erp-api/erp-api-system/src/main/java/com/erp/system/api/RemoteUserService.java \
  erp-api/erp-api-system/src/main/java/com/erp/system/api/factory/RemoteUserFallbackFactory.java \
  erp-modules/erp-system/src/main/java/com/erp/system/controller/SysUserController.java \
  erp-modules/erp-system/src/main/resources/mapper/system/SysUserMapper.xml \
  erp-modules/erp-system/src/test/java/com/erp/system/mapper/SysUserMapperSourceTest.java \
  erp-modules/erp-oa/src/main/java/com/erp/oa/domain/OaSignPlan.java \
  erp-modules/erp-oa/src/main/java/com/erp/oa/domain/OaSignPlanTemplate.java \
  erp-modules/erp-oa/src/main/java/com/erp/oa/domain/OaSignPackage.java \
  erp-modules/erp-oa/src/main/java/com/erp/oa/domain/dto/OaSignBatchPreviewRequest.java \
  erp-modules/erp-oa/src/main/java/com/erp/oa/domain/dto/OaSignBatchPreviewRow.java \
  erp-modules/erp-oa/src/main/java/com/erp/oa/domain/dto/OaSignBatchCreateDraftsRequest.java \
  erp-modules/erp-oa/src/main/java/com/erp/oa/domain/dto/OaSignBatchCreateDraftsResult.java \
  erp-modules/erp-oa/src/main/java/com/erp/oa/mapper/OaSignPlanMapper.java \
  erp-modules/erp-oa/src/main/java/com/erp/oa/mapper/OaSignPackageMapper.java \
  erp-modules/erp-oa/src/main/resources/mapper/oa/OaSignPlanMapper.xml \
  erp-modules/erp-oa/src/main/resources/mapper/oa/OaSignPackageMapper.xml \
  erp-modules/erp-oa/src/main/java/com/erp/oa/service/IOaSignPlanService.java \
  erp-modules/erp-oa/src/main/java/com/erp/oa/service/IOaSignBatchService.java \
  erp-modules/erp-oa/src/main/java/com/erp/oa/service/IOaSignPackageService.java \
  erp-modules/erp-oa/src/main/java/com/erp/oa/service/impl/OaSignPlanServiceImpl.java \
  erp-modules/erp-oa/src/main/java/com/erp/oa/service/impl/OaSignBatchServiceImpl.java \
  erp-modules/erp-oa/src/main/java/com/erp/oa/service/impl/OaSignPackageServiceImpl.java \
  erp-modules/erp-oa/src/main/java/com/erp/oa/controller/OaSignPackageController.java \
  erp-modules/erp-oa/src/test/java/com/erp/oa/mapper/OaMapperBindingTest.java \
  erp-modules/erp-oa/src/test/java/com/erp/oa/service/impl/OaSignPlanServiceImplTest.java \
  erp-modules/erp-oa/src/test/java/com/erp/oa/service/impl/OaSignBatchServiceImplTest.java \
  erp-modules/erp-oa/src/test/java/com/erp/oa/service/impl/OaSignPackageServiceImplTest.java \
  erp-ui/src/api/oa/signPackage.js erp-ui/src/views/oa/signPackage/index.vue erp-ui/test/signPackageModule.test.js \
  docker/erp/modules/oa/jar/erp-modules-oa.jar
git commit -m "feat: add batch sign package plans"
```
