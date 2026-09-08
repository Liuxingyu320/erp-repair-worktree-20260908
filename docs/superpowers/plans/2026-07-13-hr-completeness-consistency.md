# HR Completeness Consistency Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make employee profile coverage a single server-owned metric across desktop, mobile, completeness queues, and system todos, while repairing HR todo filters and mobile profile editing.

**Architecture:** Extract the existing 68-field employee-master calculation into a reusable evaluator that returns transparent counts plus missing keys. Employee DTOs, completeness queues, and system todo projections consume that evaluator; onboarding keeps a separate required-field metric. Queue-specific filters are normalized on the server before paging, and every todo route passes canonical organization and business filters.

**Tech Stack:** Java 17, Spring Boot, MyBatis XML, JUnit 5, Mockito, AssertJ, Vue 2, Element UI, Node.js contract tests.

---

## Preconditions and delivery order

- Approved design: `docs/superpowers/specs/2026-07-13-hr-completeness-small-usability-fixes-design.md`.
- Execute tasks in order because later DTO and UI work depends on the evaluator and query contract introduced first.
- Keep the existing masked HR DTO boundary. Do not add raw identity, bank, address, or phone values to list endpoints.
- Do not modify unrelated dirty files or generated JARs.

## File responsibility map

- `HrEmployeeCompletenessEvaluator`: the only formal employee profile coverage calculation.
- `HrEmployeeCompletenessSnapshot`: immutable result containing percent, counts, and missing keys.
- `HrEmployeeListVo` / `HrEmployeeProfileVo`: flat compatibility fields consumed by desktop and mobile.
- `SysTodoCandidateRow` + `SysTodoMapper.xml`: safe projection needed to evaluate todo candidates with the formal evaluator.
- `HrEmployeeQueueFilterService`: converts public boolean queue filters into trusted date bounds.
- Desktop and mobile Vue pages: consume server metrics and canonical route parameters; they do not calculate profile coverage.

### Task 1: Extract the formal employee completeness evaluator

**Files:**
- Create: `erp-modules/erp-system/src/main/java/com/erp/system/support/HrEmployeeCompletenessSnapshot.java`
- Create: `erp-modules/erp-system/src/main/java/com/erp/system/support/HrEmployeeCompletenessEvaluator.java`
- Create: `erp-modules/erp-system/src/test/java/com/erp/system/support/HrEmployeeCompletenessEvaluatorTest.java`
- Modify: `erp-modules/erp-system/src/main/java/com/erp/system/service/impl/HrEmployeeProfileServiceImpl.java:524-535`

- [ ] **Step 1: Write the failing evaluator tests**

```java
package com.erp.system.support;

import static org.assertj.core.api.Assertions.assertThat;
import com.erp.system.api.domain.SysDept;
import com.erp.system.api.domain.SysUser;
import com.erp.system.api.domain.SysUserProfile;
import org.junit.jupiter.api.Test;

class HrEmployeeCompletenessEvaluatorTest
{
    private final HrEmployeeFieldRegistry registry = new HrEmployeeFieldRegistry();
    private final HrEmployeeCompletenessEvaluator evaluator = new HrEmployeeCompletenessEvaluator(registry);

    @Test
    void exposesTransparentCountsAndExcludesOnlyNonApplicableFields()
    {
        SysUser user = employee("正式");
        HrEmployeeCompletenessSnapshot result = evaluator.evaluate(user);

        long tracked = registry.getFields().stream()
                .filter(HrEmployeeFieldRegistry.FieldDefinition::isProfileCompleteness).count();
        assertThat(result.getTrackedFieldCount()).isEqualTo(tracked);
        assertThat(result.getApplicableFieldCount() + result.getNotApplicableFieldCount()).isEqualTo(tracked);
        assertThat(result.getCompletedFieldCount() + result.getMissingFields().size())
                .isEqualTo(result.getApplicableFieldCount());
        assertThat(result.getMissingFields()).contains("email", "firstEducation", "bankAccount")
                .doesNotContain("leaveDate", "probationPeriod", "storeName");
    }

    @Test
    void trialAndDepartedLifecycleFieldsChangeTheApplicableDenominator()
    {
        HrEmployeeCompletenessSnapshot trial = evaluator.evaluate(employee("试用"));
        HrEmployeeCompletenessSnapshot departed = evaluator.evaluate(employee("离职"));

        assertThat(trial.getMissingFields()).contains("probationPeriod", "plannedRegularizationDate")
                .doesNotContain("actualRegularizationDate", "leaveDate");
        assertThat(departed.getMissingFields()).contains("actualRegularizationDate", "leaveDate")
                .doesNotContain("probationPeriod", "plannedRegularizationDate");
    }

    private SysUser employee(String status)
    {
        SysUser user = new SysUser();
        user.setUserId(7L); user.setDeptId(20L); user.setNickName("测试员工");
        user.setPhonenumber("13800138000"); user.setSex("0"); user.setPostNames("店员");
        SysDept dept = new SysDept();
        dept.setDeptId(20L); dept.setDeptName("测试门店"); dept.setDeptType("STORE");
        dept.setStatus("0"); dept.setLeader("主管"); user.setDept(dept);
        SysUserProfile profile = new SysUserProfile();
        profile.setUserId(7L); profile.setEmployeeNo("E007"); profile.setEmployeeStatus(status);
        profile.setEmployeeCategory("全职"); profile.setEntryDate(new java.util.Date());
        user.setProfile(profile);
        return user;
    }
}
```

- [ ] **Step 2: Run the evaluator test and verify it fails**

Run:

```bash
mvn -pl erp-modules/erp-system -am \
  -Dtest=HrEmployeeCompletenessEvaluatorTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: test compilation fails because `HrEmployeeCompletenessEvaluator` and `HrEmployeeCompletenessSnapshot` do not exist.

- [ ] **Step 3: Add the immutable snapshot**

```java
package com.erp.system.support;

import java.util.Collections;
import java.util.List;

public final class HrEmployeeCompletenessSnapshot
{
    private final int completionPercent;
    private final int completedFieldCount;
    private final int applicableFieldCount;
    private final int notApplicableFieldCount;
    private final int trackedFieldCount;
    private final List<String> missingFields;

    public HrEmployeeCompletenessSnapshot(int completionPercent, int completedFieldCount,
            int applicableFieldCount, int notApplicableFieldCount, int trackedFieldCount,
            List<String> missingFields)
    {
        this.completionPercent = completionPercent;
        this.completedFieldCount = completedFieldCount;
        this.applicableFieldCount = applicableFieldCount;
        this.notApplicableFieldCount = notApplicableFieldCount;
        this.trackedFieldCount = trackedFieldCount;
        this.missingFields = Collections.unmodifiableList(List.copyOf(missingFields));
    }

    public int getCompletionPercent() { return completionPercent; }
    public int getCompletedFieldCount() { return completedFieldCount; }
    public int getApplicableFieldCount() { return applicableFieldCount; }
    public int getNotApplicableFieldCount() { return notApplicableFieldCount; }
    public int getTrackedFieldCount() { return trackedFieldCount; }
    public List<String> getMissingFields() { return missingFields; }
}
```

- [ ] **Step 4: Add the reusable evaluator**

```java
package com.erp.system.support;

import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;
import com.erp.system.api.domain.SysUser;
import com.erp.system.support.HrEmployeeFieldRegistry.FieldDefinition;

@Component
public class HrEmployeeCompletenessEvaluator
{
    private final HrEmployeeFieldRegistry registry;

    public HrEmployeeCompletenessEvaluator(HrEmployeeFieldRegistry registry)
    {
        this.registry = registry;
    }

    public HrEmployeeCompletenessSnapshot evaluate(SysUser user)
    {
        int tracked = 0;
        int applicable = 0;
        int completed = 0;
        List<String> missing = new ArrayList<>();
        for (FieldDefinition field : registry.getFields())
        {
            if (!field.isProfileCompleteness()) continue;
            tracked++;
            if (!registry.isRequiredForCompleteness(user, field)) continue;
            applicable++;
            if (registry.isCompleteForCompleteness(user, field)) completed++;
            else missing.add(field.getKey());
        }
        int percent = applicable == 0 ? 100 : (int)Math.round(completed * 100.0 / applicable);
        return new HrEmployeeCompletenessSnapshot(percent, completed, applicable,
                tracked - applicable, tracked, missing);
    }
}
```

- [ ] **Step 5: Make the employee service delegate to the evaluator**

Add a field initialized from the already injected registry:

```java
private final HrEmployeeCompletenessEvaluator completenessEvaluator;
```

In the terminal constructor, after assigning `registry`, add:

```java
this.completenessEvaluator = new HrEmployeeCompletenessEvaluator(registry);
```

Replace the private calculator with:

```java
private HrEmployeeCompletenessSnapshot completenessOf(SysUser user)
{
    return completenessEvaluator.evaluate(user);
}
```

Replace internal `.percent` and `.missing` field access with `getCompletionPercent()` and `getMissingFields()`.

- [ ] **Step 6: Run evaluator and employee service tests**

Run:

```bash
mvn -pl erp-modules/erp-system -am \
  -Dtest=HrEmployeeCompletenessEvaluatorTest,HrEmployeeProfileServiceImplTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: all selected tests pass.

- [ ] **Step 7: Commit the evaluator extraction**

```bash
git add erp-modules/erp-system/src/main/java/com/erp/system/support/HrEmployeeCompletenessSnapshot.java \
  erp-modules/erp-system/src/main/java/com/erp/system/support/HrEmployeeCompletenessEvaluator.java \
  erp-modules/erp-system/src/main/java/com/erp/system/service/impl/HrEmployeeProfileServiceImpl.java \
  erp-modules/erp-system/src/test/java/com/erp/system/support/HrEmployeeCompletenessEvaluatorTest.java
git commit -m "refactor: centralize HR profile completeness"
```

### Task 2: Expose transparent profile coverage fields in HR DTOs

**Files:**
- Modify: `erp-modules/erp-system/src/main/java/com/erp/system/domain/vo/HrEmployeeListVo.java`
- Modify: `erp-modules/erp-system/src/main/java/com/erp/system/domain/vo/HrEmployeeProfileVo.java`
- Modify: `erp-modules/erp-system/src/main/java/com/erp/system/service/impl/HrEmployeeProfileServiceImpl.java:416-497`
- Modify: `erp-modules/erp-system/src/main/java/com/erp/system/service/impl/HrCompletenessService.java:115-142`
- Modify: `erp-modules/erp-system/src/test/java/com/erp/system/service/impl/HrEmployeeProfileServiceImplTest.java`
- Modify: `erp-modules/erp-system/src/test/java/com/erp/system/service/impl/HrCompletenessServiceTest.java`

- [ ] **Step 1: Add failing DTO assertions**

In `completenessCountsFormalPostEntryFieldsAndKeepsConditionalFieldsApplicable`, add:

```java
HrEmployeeListVo completeRow = rows.get(1);
assertThat(completeRow.getProfileCompletedFieldCount())
        .isEqualTo(completeRow.getProfileApplicableFieldCount());
assertThat(completeRow.getProfileTrackedFieldCount())
        .isEqualTo(completeRow.getProfileApplicableFieldCount()
                + completeRow.getProfileNotApplicableFieldCount());
assertThat(rows.get(0).getMissingProfileFields()).contains("firstEducation", "bankAccount");

HrEmployeeProfileVo detail = service.get(1L);
assertThat(detail.getProfileCompletionPercent()).isEqualTo(rows.get(0).getProfileCompletionPercent());
assertThat(detail.getProfileApplicableFieldCount()).isEqualTo(rows.get(0).getProfileApplicableFieldCount());
```

In `HrCompletenessServiceTest`, extend the copy assertion:

```java
source.setProfileCompletedFieldCount(41);
source.setProfileApplicableFieldCount(59);
source.setProfileNotApplicableFieldCount(8);
source.setProfileTrackedFieldCount(67);
source.setMissingProfileFields(List.of("bankAccount"));

assertThat(row.getProfileCompletedFieldCount()).isEqualTo(41);
assertThat(row.getProfileApplicableFieldCount()).isEqualTo(59);
assertThat(row.getProfileNotApplicableFieldCount()).isEqualTo(8);
assertThat(row.getProfileTrackedFieldCount()).isEqualTo(67);
assertThat(row.getMissingProfileFields()).containsExactly("bankAccount");
```

- [ ] **Step 2: Run the DTO tests and verify they fail**

Run:

```bash
mvn -pl erp-modules/erp-system -am \
  -Dtest=HrEmployeeProfileServiceImplTest,HrCompletenessServiceTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: compilation fails because the new getters and setters do not exist.

- [ ] **Step 3: Add flat fields to `HrEmployeeListVo`**

```java
private Integer profileCompletedFieldCount;
private Integer profileApplicableFieldCount;
private Integer profileNotApplicableFieldCount;
private Integer profileTrackedFieldCount;
private List<String> missingProfileFields = new ArrayList<>();

public Integer getProfileCompletedFieldCount(){return profileCompletedFieldCount;}
public void setProfileCompletedFieldCount(Integer v){profileCompletedFieldCount=v;}
public Integer getProfileApplicableFieldCount(){return profileApplicableFieldCount;}
public void setProfileApplicableFieldCount(Integer v){profileApplicableFieldCount=v;}
public Integer getProfileNotApplicableFieldCount(){return profileNotApplicableFieldCount;}
public void setProfileNotApplicableFieldCount(Integer v){profileNotApplicableFieldCount=v;}
public Integer getProfileTrackedFieldCount(){return profileTrackedFieldCount;}
public void setProfileTrackedFieldCount(Integer v){profileTrackedFieldCount=v;}
public List<String> getMissingProfileFields(){return missingProfileFields;}
public void setMissingProfileFields(List<String> v)
{missingProfileFields=v==null?new ArrayList<>():new ArrayList<>(v);}
```

- [ ] **Step 4: Add the four count fields to `HrEmployeeProfileVo`**

```java
private Integer profileCompletedFieldCount;
private Integer profileApplicableFieldCount;
private Integer profileNotApplicableFieldCount;
private Integer profileTrackedFieldCount;

public Integer getProfileCompletedFieldCount(){return profileCompletedFieldCount;}
public void setProfileCompletedFieldCount(Integer v){profileCompletedFieldCount=v;}
public Integer getProfileApplicableFieldCount(){return profileApplicableFieldCount;}
public void setProfileApplicableFieldCount(Integer v){profileApplicableFieldCount=v;}
public Integer getProfileNotApplicableFieldCount(){return profileNotApplicableFieldCount;}
public void setProfileNotApplicableFieldCount(Integer v){profileNotApplicableFieldCount=v;}
public Integer getProfileTrackedFieldCount(){return profileTrackedFieldCount;}
public void setProfileTrackedFieldCount(Integer v){profileTrackedFieldCount=v;}
```

- [ ] **Step 5: Populate the fields once per mapped row**

Add two overloads in `HrEmployeeProfileServiceImpl`:

```java
private void applyCompleteness(HrEmployeeListVo target, HrEmployeeCompletenessSnapshot value)
{
    target.setProfileCompletionPercent(value.getCompletionPercent());
    target.setProfileCompletedFieldCount(value.getCompletedFieldCount());
    target.setProfileApplicableFieldCount(value.getApplicableFieldCount());
    target.setProfileNotApplicableFieldCount(value.getNotApplicableFieldCount());
    target.setProfileTrackedFieldCount(value.getTrackedFieldCount());
    target.setMissingProfileFields(value.getMissingFields());
}

private void applyCompleteness(HrEmployeeProfileVo target, HrEmployeeCompletenessSnapshot value)
{
    target.setProfileCompletionPercent(value.getCompletionPercent());
    target.setProfileCompletedFieldCount(value.getCompletedFieldCount());
    target.setProfileApplicableFieldCount(value.getApplicableFieldCount());
    target.setProfileNotApplicableFieldCount(value.getNotApplicableFieldCount());
    target.setProfileTrackedFieldCount(value.getTrackedFieldCount());
    target.setMissingProfileFields(value.getMissingFields());
}
```

In `toListVo`, replace the direct percentage setter with:

```java
applyCompleteness(target, completenessOf(user));
```

At the end of `toProfileVo`, replace the two direct setters with:

```java
applyCompleteness(target, completenessOf(user));
```

- [ ] **Step 6: Copy the metrics into `HrEmployeeCompletenessVo`**

In `HrCompletenessService.copy`, after copying `profileCompletionPercent`, add:

```java
target.setProfileCompletedFieldCount(source.getProfileCompletedFieldCount());
target.setProfileApplicableFieldCount(source.getProfileApplicableFieldCount());
target.setProfileNotApplicableFieldCount(source.getProfileNotApplicableFieldCount());
target.setProfileTrackedFieldCount(source.getProfileTrackedFieldCount());
target.setMissingProfileFields(source.getMissingProfileFields());
```

- [ ] **Step 7: Run the DTO tests**

Run:

```bash
mvn -pl erp-modules/erp-system -am \
  -Dtest=HrEmployeeProfileServiceImplTest,HrCompletenessServiceTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: all selected tests pass and the list/detail metrics match.

- [ ] **Step 8: Commit the transparent DTO contract**

```bash
git add erp-modules/erp-system/src/main/java/com/erp/system/domain/vo/HrEmployeeListVo.java \
  erp-modules/erp-system/src/main/java/com/erp/system/domain/vo/HrEmployeeProfileVo.java \
  erp-modules/erp-system/src/main/java/com/erp/system/service/impl/HrEmployeeProfileServiceImpl.java \
  erp-modules/erp-system/src/main/java/com/erp/system/service/impl/HrCompletenessService.java \
  erp-modules/erp-system/src/test/java/com/erp/system/service/impl/HrEmployeeProfileServiceImplTest.java \
  erp-modules/erp-system/src/test/java/com/erp/system/service/impl/HrCompletenessServiceTest.java
git commit -m "feat: expose HR profile coverage counts"
```

### Task 3: Use the formal evaluator for profile-incomplete system todos

**Files:**
- Modify: `erp-modules/erp-system/src/main/java/com/erp/system/domain/vo/SysTodoCandidateRow.java`
- Modify: `erp-modules/erp-system/src/main/resources/mapper/system/SysTodoMapper.xml`
- Modify: `erp-modules/erp-system/src/main/java/com/erp/system/service/impl/SysTodoServiceImpl.java`
- Modify: `erp-modules/erp-system/src/test/java/com/erp/system/service/impl/SysTodoServiceImplTest.java`
- Modify: `erp-modules/erp-system/src/test/java/com/erp/system/mapper/SysTodoMapperBindingTest.java`

- [ ] **Step 1: Change the todo service test to require the formal evaluator**

Replace the mocked `HrProfileCompletenessCalculator` with `HrEmployeeCompletenessEvaluator` and add:

```java
when(evaluator.evaluate(any())).thenAnswer(invocation -> {
    com.erp.system.api.domain.SysUser user = invocation.getArgument(0);
    boolean incomplete = user.getProfile().getBankAccount() == null;
    return new HrEmployeeCompletenessSnapshot(incomplete ? 98 : 100,
            incomplete ? 58 : 59, 59, 8, 67,
            incomplete ? List.of("bankAccount") : List.of());
});
```

Populate a complete candidate and assert only the incomplete row is counted:

```java
SysTodoCandidateRow incomplete = candidate(1L, 10L, "门店A");
SysTodoCandidateRow complete = candidate(2L, 10L, "门店A");
complete.setBankAccount("6222000000000000");
when(mapper.selectScopedTodoCandidates(any(), anySet(), anyList(), anyList()))
        .thenReturn(List.of(incomplete, complete));

TodoSummary summary = service.selectSummary(new TodoQuery(), 10L);
assertThat(summary.getTypeCounts()).containsEntry(SysTodoTypes.HR_PROFILE_INCOMPLETE, 1L);
verify(evaluator, org.mockito.Mockito.times(2)).evaluate(any());
```

- [ ] **Step 2: Extend the mapper source assertion**

```java
assertThat(xml).contains("u.nick_name as employee_name", "u.phonenumber as phone_number",
        "u.email as employee_email", "u.sex as employee_sex", "upc.post_names",
        "d.leader as dept_leader", "d.status as dept_status");
```

- [ ] **Step 3: Run todo tests and verify they fail**

Run:

```bash
mvn -pl erp-modules/erp-system -am \
  -Dtest=SysTodoServiceImplTest,SysTodoMapperBindingTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: compilation or assertions fail because the todo projection and service still use the legacy calculator.

- [ ] **Step 4: Add safe projection fields to `SysTodoCandidateRow`**

```java
private String employeeName;
private String phoneNumber;
private String employeeEmail;
private String employeeSex;
private String employeeRemark;
private String postNames;
private String deptLeader;
private String deptStatus;

public String getEmployeeName(){return employeeName;} public void setEmployeeName(String v){employeeName=v;}
public String getPhoneNumber(){return phoneNumber;} public void setPhoneNumber(String v){phoneNumber=v;}
public String getEmployeeEmail(){return employeeEmail;} public void setEmployeeEmail(String v){employeeEmail=v;}
public String getEmployeeSex(){return employeeSex;} public void setEmployeeSex(String v){employeeSex=v;}
public String getEmployeeRemark(){return employeeRemark;} public void setEmployeeRemark(String v){employeeRemark=v;}
public String getPostNames(){return postNames;} public void setPostNames(String v){postNames=v;}
public String getDeptLeader(){return deptLeader;} public void setDeptLeader(String v){deptLeader=v;}
public String getDeptStatus(){return deptStatus;} public void setDeptStatus(String v){deptStatus=v;}
```

- [ ] **Step 5: Extend the candidate SQL without returning new sensitive data to clients**

Add a post-name aggregate and aliases to `selectScopedTodoCandidates`:

```xml
select p.*,
       d.dept_name as dept_name,
       d.dept_type as dept_type,
       d.leader as dept_leader,
       d.status as dept_status,
       u.nick_name as employee_name,
       u.phonenumber as phone_number,
       u.email as employee_email,
       u.sex as employee_sex,
       u.remark as employee_remark,
       upc.post_names,
       u.status as linked_account_status,
       u.del_flag as linked_account_del_flag,
       u.user_name as linked_account_user_name
from sys_user_profile p
inner join sys_dept d on d.dept_id = p.dept_id
left join sys_user u on u.user_id = p.user_id
left join (
    select sup.user_id,
           group_concat(distinct sp.post_name order by sp.post_sort, sp.post_name separator '、') as post_names
    from sys_user_post sup
    inner join sys_post sp on sp.post_id = sup.post_id
    where sp.status = '0'
    group by sup.user_id
) upc on upc.user_id = p.user_id
```

- [ ] **Step 6: Replace the legacy calculator in `SysTodoServiceImpl`**

Use `HrEmployeeCompletenessEvaluator` as the constructor dependency. Replace `isIncomplete` with:

```java
private boolean isIncomplete(SysTodoCandidateRow row)
{
    if ("取消入职".equals(row.getOnboardingStatus()) || "离职".equals(row.getEmployeeStatus())) return false;
    return completenessEvaluator.evaluate(toEmployeeProjection(row)).getCompletionPercent() < 100;
}

private SysUser toEmployeeProjection(SysTodoCandidateRow row)
{
    SysUser user = new SysUser();
    user.setUserId(row.getUserId());
    user.setDeptId(row.getDeptId());
    user.setNickName(row.getEmployeeName());
    user.setPhonenumber(row.getPhoneNumber());
    user.setEmail(row.getEmployeeEmail());
    user.setSex(row.getEmployeeSex());
    user.setRemark(row.getEmployeeRemark());
    user.setPostNames(row.getPostNames());
    user.setProfile(row);
    com.erp.system.api.domain.SysDept dept = new com.erp.system.api.domain.SysDept();
    dept.setDeptId(row.getDeptId());
    dept.setDeptName(row.getDeptName());
    dept.setDeptType(row.getDeptType());
    dept.setLeader(row.getDeptLeader());
    dept.setStatus(row.getDeptStatus());
    user.setDept(dept);
    return user;
}
```

Update constructor calls in the test to pass the mocked evaluator. Do not leave `HrProfileCompletenessCalculator` in the production constructor.

- [ ] **Step 7: Run todo tests**

Run:

```bash
mvn -pl erp-modules/erp-system -am \
  -Dtest=SysTodoServiceImplTest,SysTodoMapperBindingTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: profile-incomplete totals use the formal evaluator and all tests pass.

- [ ] **Step 8: Commit the todo consistency change**

```bash
git add erp-modules/erp-system/src/main/java/com/erp/system/domain/vo/SysTodoCandidateRow.java \
  erp-modules/erp-system/src/main/resources/mapper/system/SysTodoMapper.xml \
  erp-modules/erp-system/src/main/java/com/erp/system/service/impl/SysTodoServiceImpl.java \
  erp-modules/erp-system/src/test/java/com/erp/system/service/impl/SysTodoServiceImplTest.java \
  erp-modules/erp-system/src/test/java/com/erp/system/mapper/SysTodoMapperBindingTest.java
git commit -m "fix: align HR profile todos with profile coverage"
```

### Task 4: Add server-side contract-due and offboard-account filters

**Files:**
- Create: `erp-modules/erp-system/src/main/java/com/erp/system/service/impl/HrEmployeeQueueFilterService.java`
- Create: `erp-modules/erp-system/src/test/java/com/erp/system/service/impl/HrEmployeeQueueFilterServiceTest.java`
- Modify: `erp-modules/erp-system/src/main/java/com/erp/system/domain/vo/HrEmployeeQuery.java`
- Modify: `erp-modules/erp-system/src/main/java/com/erp/system/controller/HrEmployeeProfileController.java:25-62`
- Modify: `erp-modules/erp-system/src/main/resources/mapper/system/SysUserMapper.xml:324-408`
- Modify: `erp-modules/erp-system/src/main/java/com/erp/system/domain/vo/HrEmployeeListVo.java`
- Modify: `erp-modules/erp-system/src/main/java/com/erp/system/service/impl/HrEmployeeProfileServiceImpl.java:400-445`
- Modify: `erp-modules/erp-system/src/test/java/com/erp/system/mapper/HrEmployeeMapperBindingTest.java`
- Modify: `erp-modules/erp-system/src/test/java/com/erp/system/controller/HrEmployeeControllerContractTest.java`

- [ ] **Step 1: Write the fixed-clock filter test**

```java
package com.erp.system.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;
import com.erp.system.domain.vo.HrEmployeeQuery;
import com.erp.system.service.ISysConfigService;

class HrEmployeeQueueFilterServiceTest
{
    @Test
    void derivesTrustedContractWindowAndLeavesNormalQueriesUntouched()
    {
        ISysConfigService config = mock(ISysConfigService.class);
        when(config.selectConfigByKey("todo.contract.warning.days")).thenReturn("30");
        Clock clock = Clock.fixed(Instant.parse("2026-07-13T00:00:00Z"), ZoneId.of("Asia/Shanghai"));
        HrEmployeeQueueFilterService service = new HrEmployeeQueueFilterService(config, clock);
        HrEmployeeQuery query = new HrEmployeeQuery(); query.setContractDue(true);

        service.prepare(query);

        assertThat(query.getContractDueFrom()).isEqualTo(java.time.LocalDate.of(2026, 7, 13));
        assertThat(query.getContractDueTo()).isEqualTo(java.time.LocalDate.of(2026, 8, 12));
    }
}
```

- [ ] **Step 2: Add failing mapper binding assertions**

```java
HrEmployeeQuery queues = new HrEmployeeQuery();
queues.setContractDue(true);
queues.setContractDueFrom(java.time.LocalDate.of(2026, 7, 13));
queues.setContractDueTo(java.time.LocalDate.of(2026, 8, 12));
queues.setOffboardAccountOnly(true);
queues.getParams().put("dataScope", "");
BoundSql queueSql = statement.getBoundSql(queues);
assertThat(queueSql.getSql()).contains("p.contract_end_date >= ?", "p.contract_end_date <= ?",
        "p.employee_status in ('待离职','离职')", "u.status = '0'");
assertThat(queueSql.getParameterMappings()).extracting(ParameterMapping::getProperty)
        .contains("contractDueFrom", "contractDueTo");
```

- [ ] **Step 3: Run the new tests and verify they fail**

Run:

```bash
mvn -pl erp-modules/erp-system -am \
  -Dtest=HrEmployeeQueueFilterServiceTest,HrEmployeeMapperBindingTest,HrEmployeeControllerContractTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: compilation fails because queue fields and the filter service do not exist.

- [ ] **Step 4: Extend `HrEmployeeQuery` with public and trusted fields**

```java
private Boolean contractDue;
private Boolean offboardAccountOnly;
private java.time.LocalDate contractDueFrom;
private java.time.LocalDate contractDueTo;

public Boolean getContractDue(){return contractDue;}
public void setContractDue(Boolean v){contractDue=v;}
public Boolean getOffboardAccountOnly(){return offboardAccountOnly;}
public void setOffboardAccountOnly(Boolean v){offboardAccountOnly=v;}
public java.time.LocalDate getContractDueFrom(){return contractDueFrom;}
public void setContractDueFrom(java.time.LocalDate v){contractDueFrom=v;}
public java.time.LocalDate getContractDueTo(){return contractDueTo;}
public void setContractDueTo(java.time.LocalDate v){contractDueTo=v;}
```

- [ ] **Step 5: Add the queue filter normalizer**

```java
package com.erp.system.service.impl;

import java.time.Clock;
import java.time.LocalDate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.erp.system.domain.vo.HrEmployeeQuery;
import com.erp.system.service.ISysConfigService;

@Service
public class HrEmployeeQueueFilterService
{
    static final String CONTRACT_WARNING_DAYS_KEY = "todo.contract.warning.days";
    static final int DEFAULT_WARNING_DAYS = 30;
    private final ISysConfigService configService;
    private final Clock clock;

    @Autowired
    public HrEmployeeQueueFilterService(ISysConfigService configService)
    { this(configService, Clock.system(ZoneIdHolder.BUSINESS_ZONE)); }

    HrEmployeeQueueFilterService(ISysConfigService configService, Clock clock)
    { this.configService=configService; this.clock=clock; }

    public HrEmployeeQuery prepare(HrEmployeeQuery value)
    {
        HrEmployeeQuery query=value==null?new HrEmployeeQuery():value;
        if(Boolean.TRUE.equals(query.getContractDue()))
        {
            LocalDate today=LocalDate.now(clock);
            query.setContractDueFrom(today);
            query.setContractDueTo(today.plusDays(resolveWarningDays()));
        }
        else {query.setContractDueFrom(null);query.setContractDueTo(null);}
        return query;
    }

    private int resolveWarningDays()
    {
        try
        {
            int value=Integer.parseInt(configService.selectConfigByKey(CONTRACT_WARNING_DAYS_KEY));
            return value>=1&&value<=365?value:DEFAULT_WARNING_DAYS;
        }
        catch(Exception ignored){return DEFAULT_WARNING_DAYS;}
    }

    private static final class ZoneIdHolder
    { private static final java.time.ZoneId BUSINESS_ZONE=java.time.ZoneId.of("Asia/Shanghai"); }
}
```

- [ ] **Step 6: Normalize list, summary, and export queries in the controller**

Add:

```java
@Autowired private HrEmployeeQueueFilterService queueFilterService;
```

Use `query=queueFilterService.prepare(query);` before pagination in `list`, before `summary`, and before normal export. Leave sensitive export on its existing request model; this batch does not add queue booleans to the sensitive-export contract.

- [ ] **Step 7: Add mapper filters before `${params.dataScope}`**

```xml
<if test="contractDue == true">
    and p.contract_end_date &gt;= #{contractDueFrom}
    and p.contract_end_date &lt;= #{contractDueTo}
    and (p.employee_status is null or p.employee_status &lt;&gt; '离职')
</if>
<if test="offboardAccountOnly == true">
    and p.employee_status in ('待离职','离职')
    and u.status = '0'
</if>
```

- [ ] **Step 8: Expose a safe account-enabled boolean**

Add to `HrEmployeeListVo`:

```java
private Boolean accountEnabled;
public Boolean getAccountEnabled(){return accountEnabled;}
public void setAccountEnabled(Boolean v){accountEnabled=v;}
```

Set it in `toListVo`:

```java
target.setAccountEnabled("0".equals(user.getStatus()) && "0".equals(user.getDelFlag()));
```

Copy it in `HrCompletenessService.copy`:

```java
target.setAccountEnabled(source.getAccountEnabled());
```

- [ ] **Step 9: Run filter tests**

Run:

```bash
mvn -pl erp-modules/erp-system -am \
  -Dtest=HrEmployeeQueueFilterServiceTest,HrEmployeeMapperBindingTest,HrEmployeeControllerContractTest,HrCompletenessServiceTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: trusted dates are derived server-side, SQL filters before paging, and tests pass.

- [ ] **Step 10: Commit the server queue filters**

```bash
git add erp-modules/erp-system/src/main/java/com/erp/system/service/impl/HrEmployeeQueueFilterService.java \
  erp-modules/erp-system/src/test/java/com/erp/system/service/impl/HrEmployeeQueueFilterServiceTest.java \
  erp-modules/erp-system/src/main/java/com/erp/system/domain/vo/HrEmployeeQuery.java \
  erp-modules/erp-system/src/main/java/com/erp/system/controller/HrEmployeeProfileController.java \
  erp-modules/erp-system/src/main/resources/mapper/system/SysUserMapper.xml \
  erp-modules/erp-system/src/main/java/com/erp/system/domain/vo/HrEmployeeListVo.java \
  erp-modules/erp-system/src/main/java/com/erp/system/service/impl/HrEmployeeProfileServiceImpl.java \
  erp-modules/erp-system/src/main/java/com/erp/system/service/impl/HrCompletenessService.java \
  erp-modules/erp-system/src/test/java/com/erp/system/mapper/HrEmployeeMapperBindingTest.java \
  erp-modules/erp-system/src/test/java/com/erp/system/controller/HrEmployeeControllerContractTest.java \
  erp-modules/erp-system/src/test/java/com/erp/system/service/impl/HrCompletenessServiceTest.java
git commit -m "fix: filter HR reminder queues on the server"
```

### Task 5: Canonicalize HR todo route parameters and desktop deep links

**Files:**
- Modify: `erp-modules/erp-system/src/main/java/com/erp/system/service/impl/SysTodoServiceImpl.java:280-306`
- Modify: `erp-modules/erp-system/src/test/java/com/erp/system/service/impl/SysTodoServiceImplTest.java`
- Modify: `erp-ui/src/views/hr/employee/index.vue`
- Modify: `erp-ui/src/views/hr/components/HrEmployeeList.vue`
- Modify: `erp-ui/src/views/hr/completeness/index.vue`
- Modify: `erp-ui/src/views/hr/onboarding/index.vue`
- Modify: `erp-ui/test/unifiedTodoRouteResolver.test.js`
- Modify: `erp-ui/test/hrEmployeeMasterFields.test.js`
- Modify: `erp-ui/test/hrOnboardingWorkbench.test.js`

- [ ] **Step 1: Add failing backend route-parameter assertions**

In `SysTodoServiceImplTest`, assert the exact params for each HR type:

```java
assertThat(item(SysTodoTypes.HR_PROFILE_INCOMPLETE).getRouteParams())
        .containsEntry("deptId", "10").containsEntry("completenessStatus", "INCOMPLETE");
assertThat(item(SysTodoTypes.HR_ONBOARDING_CONFIRM).getRouteParams())
        .containsEntry("targetDeptId", "10").containsEntry("status", "READY");
assertThat(item(SysTodoTypes.HR_CONTRACT_DUE).getRouteParams())
        .containsEntry("deptId", "10").containsEntry("contractDue", "true");
assertThat(item(SysTodoTypes.HR_OFFBOARD_ACCOUNT).getRouteParams())
        .containsEntry("deptId", "10").containsEntry("offboardAccountOnly", "true");
```

Add a private test helper that finds a recent item by type rather than relying on list order.

- [ ] **Step 2: Add failing frontend route assertions**

In `unifiedTodoRouteResolver.test.js`, add dedicated HR items and assert:

```javascript
assert.deepStrictEqual(hrProfileRoute.location.query, {
  contextDeptId: "20", contextDeptName: "华东仓", contextDeptType: "WAREHOUSE",
  deptId: "20", completenessStatus: "INCOMPLETE", count: "2", affectedCount: "2",
  scopeMode: "current_org", todoType: "HR_PROFILE_INCOMPLETE", businessId: "20"
})
```

In the HR component tests, assert route values are normalized and copied into the first request.

- [ ] **Step 3: Run route tests and verify they fail**

Run:

```bash
mvn -pl erp-modules/erp-system -am \
  -Dtest=SysTodoServiceImplTest -Dsurefire.failIfNoSpecifiedTests=false test
cd erp-ui && node scripts/run-node-tests.cjs \
  unifiedTodoRouteResolver.test.js hrEmployeeMasterFields.test.js hrOnboardingWorkbench.test.js
```

Expected: the provider still emits legacy human-readable status keys and the pages ignore several route filters.

- [ ] **Step 4: Emit canonical params from `SysTodoServiceImpl`**

Replace `addFilterParams` with:

```java
private void addFilterParams(Map<String,String> params,String type,Long deptId)
{
    if(SysTodoTypes.HR_PROFILE_INCOMPLETE.equals(type))
    {params.put("deptId",String.valueOf(deptId));params.put("completenessStatus","INCOMPLETE");}
    else if(SysTodoTypes.HR_ONBOARDING_CONFIRM.equals(type))
    {params.put("targetDeptId",String.valueOf(deptId));params.put("status","READY");}
    else if(SysTodoTypes.HR_CONTRACT_DUE.equals(type))
    {params.put("deptId",String.valueOf(deptId));params.put("contractDue","true");}
    else if(SysTodoTypes.HR_OFFBOARD_ACCOUNT.equals(type))
    {params.put("deptId",String.valueOf(deptId));params.put("offboardAccountOnly","true");}
}
```

Call it with `group.deptId` from `toItem`.

- [ ] **Step 5: Pass validated employee route filters to `HrEmployeeList`**

In `employee/index.vue`, add a computed object:

```javascript
routeFilters() {
  const query = (this.$route && this.$route.query) || {}
  const positive = value => /^\d+$/.test(String(value || "")) && Number(value) > 0 ? Number(value) : undefined
  return {
    deptId: positive(query.deptId || query.contextDeptId),
    contractDue: query.contractDue === true || query.contractDue === "true" || undefined,
    offboardAccountOnly: query.offboardAccountOnly === true || query.offboardAccountOnly === "true" || undefined
  }
}
```

Pass it as `:initial-filters="routeFilters"`. Add an `initialFilters` object prop to `HrEmployeeList`, merge only `deptId`, `contractDue`, and `offboardAccountOnly` into `queryParams` during creation, and watch the prop to reset page 1 and reload when the canonical JSON value changes.

- [ ] **Step 6: Initialize completeness and onboarding pages from route filters**

In completeness data initialization, set:

```javascript
const route = this.$route && this.$route.query ? this.$route.query : {}
this.query = {
  ...defaultQuery(),
  deptId: positiveId(route.deptId || route.contextDeptId),
  completenessStatus: String(route.completenessStatus || "").toUpperCase() === "INCOMPLETE"
    ? "INCOMPLETE" : undefined
}
```

In onboarding, change `defaultQuery` to accept route query and initialize:

```javascript
status: ["DRAFT", "READY", "CONFIRMED", "CANCELLED"].includes(String(route.status || "").toUpperCase())
  ? String(route.status).toUpperCase() : "DRAFT",
targetDeptId: positiveId(route.targetDeptId || route.contextDeptId)
```

Keep the existing `onboardingId` deep-link behavior higher priority than queue status.

- [ ] **Step 7: Run backend and frontend route tests**

Run:

```bash
mvn -pl erp-modules/erp-system -am \
  -Dtest=SysTodoServiceImplTest -Dsurefire.failIfNoSpecifiedTests=false test
cd erp-ui && node scripts/run-node-tests.cjs \
  unifiedTodoRouteResolver.test.js hrEmployeeMasterFields.test.js hrOnboardingWorkbench.test.js
```

Expected: all tests pass and each todo opens the exact server-filtered queue.

- [ ] **Step 8: Commit canonical HR todo focus**

```bash
git add erp-modules/erp-system/src/main/java/com/erp/system/service/impl/SysTodoServiceImpl.java \
  erp-modules/erp-system/src/test/java/com/erp/system/service/impl/SysTodoServiceImplTest.java \
  erp-ui/src/views/hr/employee/index.vue erp-ui/src/views/hr/components/HrEmployeeList.vue \
  erp-ui/src/views/hr/completeness/index.vue erp-ui/src/views/hr/onboarding/index.vue \
  erp-ui/test/unifiedTodoRouteResolver.test.js erp-ui/test/hrEmployeeMasterFields.test.js \
  erp-ui/test/hrOnboardingWorkbench.test.js
git commit -m "fix: focus HR todos on their business queues"
```

### Task 6: Remove mobile completeness recalculation and repair mobile profile editing

**Files:**
- Modify: `erp-ui/src/views/mobile/hr/completeness/index.vue`
- Modify: `erp-ui/src/views/mobile/hr/employee/index.vue`
- Modify: `erp-ui/src/views/mobile/hr/components/MobileHrProfileEditor.vue`
- Modify: `erp-ui/test/unifiedTodoMobile.test.js`
- Modify: `erp-ui/test/hrEmployeeMasterFields.test.js`

- [ ] **Step 1: Add failing mobile behavior tests**

Extend `unifiedTodoMobile.test.js` with source assertions:

```javascript
assert.ok(!mobileHrCompletenessSource.includes("const REQUIRED"),
  "mobile completeness must not own a second field list")
assert.ok(mobileHrCompletenessSource.includes("profileCompletionPercent"))
assert.ok(mobileHrCompletenessSource.includes("missingProfileFields"))
assert.ok(mobileHrCompletenessSource.includes("userId: row.userId"))
assert.ok(!mobileHrCompletenessSource.includes("profileId:"))
assert.ok(mobileHrEmployeeSource.includes("getHrEmployee"))
assert.ok(mobileHrEmployeeSource.includes("updateHrEmployee(payload.userId, payload.patch)"))
assert.ok(mobileHrEmployeeSource.includes("accountEnabled"))
```

Add a VM behavior test that passes a server row with `profileCompletionPercent: 73` and verifies `completion(row) === 73`, then submits `{userId:7,patch:{bankName:"测试银行"}}` and verifies the API receives `(7, patch)`.

- [ ] **Step 2: Run mobile tests and verify they fail**

Run:

```bash
cd erp-ui && node scripts/run-node-tests.cjs unifiedTodoMobile.test.js hrEmployeeMasterFields.test.js
```

Expected: assertions fail because the page still owns a 9-field array and calls the update API with one argument.

- [ ] **Step 3: Make mobile completeness a pure server view**

Replace the local helpers with:

```javascript
profile(row) { return (row && row.profile) || row || {} },
completion(row) {
  const value = Number(row && row.profileCompletionPercent)
  return Number.isFinite(value) ? Math.max(0, Math.min(100, Math.round(value))) : 0
},
missingFields(row) {
  const values = Array.isArray(row && row.missingProfileFields) ? row.missingProfileFields : []
  return values.map(profileFieldLabel)
},
coverageText(row) {
  const complete = Number(row && row.profileCompletedFieldCount) || 0
  const applicable = Number(row && row.profileApplicableFieldCount) || 0
  const notApplicable = Number(row && row.profileNotApplicableFieldCount) || 0
  return `已填 ${complete}/适用 ${applicable} · ${notApplicable} 项不适用`
}
```

Import `profileFieldLabel` from `@/views/hr/components/hrFieldConfig`. Request only `completenessStatus: "INCOMPLETE"`, `deptId`, page, and size; do not filter returned rows again.

Use this route payload:

```javascript
query: {
  todoType: "HR_PROFILE_INCOMPLETE",
  userId: row.userId,
  deptId: this.$route.query.deptId || this.$route.query.contextDeptId,
  businessId: this.$route.query.businessId
}
```

- [ ] **Step 4: Load the selected employee detail before editing**

Import `getHrEmployee`. In mobile employee page:

```javascript
openEditor(row) {
  const userId = Number(row && row.userId)
  if (!Number.isSafeInteger(userId) || userId <= 0) return Promise.resolve(null)
  this.message = ""
  return getHrEmployee(userId).then(response => {
    this.editing = response.data || null
    this.editorOpen = Boolean(this.editing)
    return this.editing
  }).catch(error => {
    this.message = (error && error.message) || "员工档案加载失败，请重试"
    return null
  })
}
```

After the first list load, use `this.$route.query.userId` to find the row by `row.userId` and call `openEditor`; remove all `profileId` focus logic.

- [ ] **Step 5: Submit a flat mobile patch with sensitive dirty tracking**

Make `MobileHrProfileEditor` emit this shape:

```javascript
this.$emit("save", {
  userId: this.form.userId,
  patch: this.buildPatch()
})
```

`buildPatch` compares editable non-sensitive fields to the initial snapshot, and includes `phoneNumber`, `emergencyContactPhone`, and `bankAccount` only when the user explicitly changed those replacement inputs. It never submits masked placeholders and never includes `employeeNo`, `employeeStatus`, `profile`, or `userId` inside `patch`.

In the page, use:

```javascript
saveProfile(payload) {
  this.saving = true
  return updateHrEmployee(payload.userId, payload.patch).then(() => {
    this.$message.success("员工资料已保存")
    this.editorOpen = false
    return Promise.all([this.reload(), this.$store.dispatch("todo/refreshSummaries")])
  }).catch(error => {
    this.message = (error && error.message) || "员工资料保存失败，请重试"
  }).finally(() => { this.saving = false })
}
```

Use `row.accountEnabled === true` for the offboard action. Use `row.phoneNumberMasked`, `row.departmentName`, and `row.positionName` for list display instead of fields absent from the safe list DTO.

- [ ] **Step 6: Run mobile tests**

Run:

```bash
cd erp-ui && node scripts/run-node-tests.cjs unifiedTodoMobile.test.js hrEmployeeMasterFields.test.js
```

Expected: server coverage, user-focused editing, correct update arguments, and offboard action assertions pass.

- [ ] **Step 7: Commit the mobile repair**

```bash
git add erp-ui/src/views/mobile/hr/completeness/index.vue \
  erp-ui/src/views/mobile/hr/employee/index.vue \
  erp-ui/src/views/mobile/hr/components/MobileHrProfileEditor.vue \
  erp-ui/test/unifiedTodoMobile.test.js erp-ui/test/hrEmployeeMasterFields.test.js
git commit -m "fix: use server HR coverage on mobile"
```

### Task 7: Separate onboarding required completion from formal profile coverage

**Files:**
- Modify: `erp-modules/erp-system/src/main/java/com/erp/system/domain/vo/HrOnboardingCompletionVo.java`
- Modify: `erp-modules/erp-system/src/main/java/com/erp/system/domain/vo/HrOnboardingDetailVo.java`
- Modify: `erp-modules/erp-system/src/main/java/com/erp/system/service/impl/HrOnboardingRuleService.java:248-299`
- Modify: `erp-modules/erp-system/src/main/java/com/erp/system/service/impl/HrOnboardingServiceImpl.java:385-405`
- Modify: `erp-modules/erp-system/src/test/java/com/erp/system/service/impl/HrOnboardingRuleServiceTest.java`
- Modify: `erp-ui/src/views/hr/onboarding/index.vue`
- Modify: `erp-ui/src/views/hr/onboarding/components/HrOnboardingDetailPane.vue`
- Modify: `erp-ui/src/views/mobile/hr/onboarding/detail.vue`
- Modify: `erp-ui/test/hrOnboardingWorkbench.test.js`
- Modify: `erp-ui/test/unifiedTodoMobile.test.js`

- [ ] **Step 1: Add failing onboarding count tests**

In `HrOnboardingRuleServiceTest`, assert:

```java
HrOnboardingCompletionVo result = service.evaluateReady(completeReadyOnboarding());
assertThat(result.getRequiredFieldCount()).isEqualTo(24);
assertThat(result.getCompletedFieldCount()).isEqualTo(24);
assertThat(result.getPercentage()).isEqualTo(100);

HrOnboarding incomplete = completeReadyOnboarding();
incomplete.setBankName(null);
HrOnboardingCompletionVo partial = service.evaluateReady(incomplete);
assertThat(partial.getRequiredFieldCount()).isEqualTo(24);
assertThat(partial.getCompletedFieldCount()).isEqualTo(23);
```

- [ ] **Step 2: Add failing frontend label assertions**

```javascript
assert.ok(desktopDetailSource.includes("入职必填完成度"))
assert.ok(!desktopDetailSource.includes("<span>资料完整度</span>"))
assert.ok(mobileDetailSource.includes("入职必填完成度"))
assert.ok(!mobileDetailSource.includes('<span>员工档案</span>'))
```

Add an assertion that linked confirmed records call `getHrEmployee(detail.linkedUserId)` and show `档案覆盖度`, while unlinked records do not render a second profile percentage.

- [ ] **Step 3: Run onboarding tests and verify they fail**

Run:

```bash
mvn -pl erp-modules/erp-system -am \
  -Dtest=HrOnboardingRuleServiceTest,HrOnboardingServiceImplTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
cd erp-ui && node scripts/run-node-tests.cjs hrOnboardingWorkbench.test.js unifiedTodoMobile.test.js
```

Expected: count getters and new labels are missing.

- [ ] **Step 4: Add count fields to onboarding decision and detail DTOs**

In `HrOnboardingCompletionVo`:

```java
private Integer completedFieldCount = 0;
private Integer requiredFieldCount = 0;
public Integer getCompletedFieldCount(){return completedFieldCount;}
public void setCompletedFieldCount(Integer v){completedFieldCount=v;}
public Integer getRequiredFieldCount(){return requiredFieldCount;}
public void setRequiredFieldCount(Integer v){requiredFieldCount=v;}
```

In `evaluateOnboarding`, after collecting missing fields:

```java
result.setRequiredFieldCount(requiredKeys.size());
result.setCompletedFieldCount(requiredKeys.size()-result.getMissingFields().size());
result.setPercentage(percentage(result.getRequiredFieldCount(), result.getMissingFields().size()));
```

In `HrOnboardingDetailVo`, add flat `onboardingCompletedFieldCount` and `onboardingRequiredFieldCount` fields with getters/setters. Populate them from `completion` in `HrOnboardingServiceImpl.toDetailVo`.

- [ ] **Step 5: Render the required count and load formal coverage only for linked employees**

Desktop label:

```html
<span>入职必填完成度</span>
<strong>{{ completionPercent }}%</strong>
<small>已填 {{ detail.onboardingCompletedFieldCount || 0 }}/{{ detail.onboardingRequiredFieldCount || 0 }}</small>
```

In the desktop parent, when a detail response has a valid `linkedUserId` and the user has `hr:employee:query`, call `getHrEmployee(linkedUserId)` and store the result in `linkedEmployeeProfile`. Pass it to the detail pane. Clear it on every selection change and stale request.

In mobile detail, apply the same rule. Replace the current always-visible second percentage with:

```html
<div v-if="linkedEmployeeProfile">
  <div class="progress-label"><span>档案覆盖度</span><strong>{{ safePercent(linkedEmployeeProfile.profileCompletionPercent) }}%</strong></div>
  <small>已填 {{ linkedEmployeeProfile.profileCompletedFieldCount || 0 }}/适用 {{ linkedEmployeeProfile.profileApplicableFieldCount || 0 }}</small>
</div>
```

Do not render the existing 35-field `detail.profileCompletionPercent` as formal employee coverage.

- [ ] **Step 6: Run onboarding tests**

Run:

```bash
mvn -pl erp-modules/erp-system -am \
  -Dtest=HrOnboardingRuleServiceTest,HrOnboardingServiceImplTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
cd erp-ui && node scripts/run-node-tests.cjs hrOnboardingWorkbench.test.js unifiedTodoMobile.test.js
```

Expected: all selected tests pass and no onboarding surface uses the ambiguous label.

- [ ] **Step 7: Commit onboarding metric separation**

```bash
git add erp-modules/erp-system/src/main/java/com/erp/system/domain/vo/HrOnboardingCompletionVo.java \
  erp-modules/erp-system/src/main/java/com/erp/system/domain/vo/HrOnboardingDetailVo.java \
  erp-modules/erp-system/src/main/java/com/erp/system/service/impl/HrOnboardingRuleService.java \
  erp-modules/erp-system/src/main/java/com/erp/system/service/impl/HrOnboardingServiceImpl.java \
  erp-modules/erp-system/src/test/java/com/erp/system/service/impl/HrOnboardingRuleServiceTest.java \
  erp-ui/src/views/hr/onboarding/index.vue \
  erp-ui/src/views/hr/onboarding/components/HrOnboardingDetailPane.vue \
  erp-ui/src/views/mobile/hr/onboarding/detail.vue \
  erp-ui/test/hrOnboardingWorkbench.test.js erp-ui/test/unifiedTodoMobile.test.js
git commit -m "fix: separate onboarding and profile completion"
```

### Task 8: Run the batch-one verification gate

**Files:**
- Test only; do not change production files unless a failure is directly caused by this batch.

- [ ] **Step 1: Run all HR backend tests touched by the plan**

```bash
mvn -pl erp-modules/erp-system -am \
  -Dtest=HrEmployeeCompletenessEvaluatorTest,HrEmployeeFieldRegistryTest,HrEmployeeProfileServiceImplTest,HrCompletenessServiceTest,HrEmployeeQueueFilterServiceTest,HrEmployeeMapperBindingTest,HrEmployeeControllerContractTest,SysTodoServiceImplTest,SysTodoMapperBindingTest,HrOnboardingRuleServiceTest,HrOnboardingServiceImplTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: BUILD SUCCESS with zero failures and zero errors.

- [ ] **Step 2: Run the focused frontend contracts**

```bash
cd erp-ui && node scripts/run-node-tests.cjs \
  hrEmployeeMasterFields.test.js hrOnboardingWorkbench.test.js hrWorkbenchUx.test.js \
  unifiedTodoMobile.test.js unifiedTodoRouteResolver.test.js
```

Expected: five files run, zero failures.

- [ ] **Step 3: Run the complete frontend test suite**

```bash
cd erp-ui && npm test
```

Expected: all Node contract tests pass.

- [ ] **Step 4: Build the production frontend**

```bash
cd erp-ui && npm run build:prod
```

Expected: production build exits 0; existing size warnings may remain, but no compilation error is allowed.

- [ ] **Step 5: Perform the same-employee API acceptance check**

With an authorized HR test account and the seeded incomplete employee whose test ID is `7`, compare:

```text
GET /system/hr/employee/list?userId=7
GET /system/hr/employee/7
GET /system/hr/completeness/employees?userId=7
```

Expected: all three return the same `profileCompletionPercent`, completed/applicable/not-applicable/tracked counts, and missing profile keys.

- [ ] **Step 6: Perform todo focus acceptance checks**

For each HR todo type, open the desktop and mobile target and inspect the first network request.

Expected:

```text
HR_PROFILE_INCOMPLETE -> matching deptId + INCOMPLETE
HR_ONBOARDING_CONFIRM -> matching targetDeptId + READY
HR_CONTRACT_DUE -> matching deptId + contractDue=true
HR_OFFBOARD_ACCOUNT -> matching deptId + offboardAccountOnly=true
```

The returned list total must equal the todo `affectedCount` for the same organization.

- [ ] **Step 7: Close the verification gate without a generic commit**

If no repair was needed, do not create an empty commit. If a directly related repair was needed, return to the task that owns that file, rerun that task's focused test, and amend that task's commit before repeating Steps 1-6. The verification gate itself must not produce a catch-all commit.
