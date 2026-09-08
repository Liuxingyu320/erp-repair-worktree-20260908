# Attendance and Payroll Exception Guard Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Mark unsigned-out attendance as an explicit exception and block salary recalculation before any existing salary data is deleted when the target month contains unresolved attendance facts.

**Architecture:** Introduce one fact-first attendance status policy shared by attendance reads and salary preflight. Salary exposes a read-only structured preflight endpoint, but `calculateSalary` repeats the same server validation inside the transaction before delete/insert. The attendance query gets one boolean exception filter, and the existing salary page links directly to the same shop/month exception queue.

**Tech Stack:** Java 17, Spring Boot, Spring transactions, MyBatis XML, JUnit 5, AssertJ, Vue 2, Element UI, Node.js contract tests.

---

## Preconditions and scope boundary

- Approved design: `docs/superpowers/specs/2026-07-13-attendance-payroll-exception-guard-design.md`.
- This plan is independent of the two HR employee-profile plans and may be executed in a separate branch.
- Do not add shift scheduling, leave approval, overtime approval, correction/clock-repair actions, payroll locking, or payment flows.
- Never infer or write a missing checkout time. The guard only identifies the problem and preserves current salary data.
- Do not modify unrelated dirty files or generated JARs.

## File responsibility map

- `OaAttendanceStatusPolicy`: canonical status constants, fact-first read normalization, completed-work classification, and exception reason.
- `OaAttendanceRecord.exceptionOnly`: public query flag; SQL applies it before pagination.
- `OaSalaryAttendancePreflightVo`: structured, size-bounded result used by both the API and transaction guard.
- `OaSalaryServiceImpl.preflightAttendance`: the only salary attendance preflight implementation.
- Salary UI: starts preflight and displays/link-routes only; it cannot bypass the server guard.

### Task 1: Introduce fact-first attendance status normalization

**Files:**
- Create: `erp-modules/erp-oa/src/main/java/com/erp/oa/support/OaAttendanceStatusPolicy.java`
- Create: `erp-modules/erp-oa/src/test/java/com/erp/oa/support/OaAttendanceStatusPolicyTest.java`
- Modify: `erp-modules/erp-oa/src/main/java/com/erp/oa/domain/OaAttendanceRecord.java`
- Modify: `erp-modules/erp-oa/src/main/java/com/erp/oa/service/impl/OaAttendanceServiceImpl.java`
- Create: `erp-modules/erp-oa/src/test/java/com/erp/oa/service/impl/OaAttendanceServiceImplTest.java`

- [ ] **Step 1: Create the two new package directories**

Run:

```bash
mkdir -p erp-modules/erp-oa/src/main/java/com/erp/oa/support \
  erp-modules/erp-oa/src/test/java/com/erp/oa/support
```

Expected: both directories exist; no source file has been written yet.

- [ ] **Step 2: Write the failing status-policy tests**

```java
package com.erp.oa.support;

import static org.assertj.core.api.Assertions.assertThat;
import java.util.Date;
import org.junit.jupiter.api.Test;
import com.erp.oa.domain.OaAttendanceRecord;

class OaAttendanceStatusPolicyTest
{
    private final OaAttendanceStatusPolicy policy=new OaAttendanceStatusPolicy();

    @Test void missingCheckoutOverridesHistoricalNormalStatus()
    {
        OaAttendanceRecord row=record("normal",new Date(),null);
        assertThat(policy.normalizedStatus(row)).isEqualTo(OaAttendanceStatusPolicy.PENDING_CHECKOUT);
        assertThat(policy.exceptionReason(row)).isEqualTo("未签退");
    }

    @Test void recognizesOnlyTheFourCompletedWorkStatuses()
    {
        Date now=new Date();
        assertThat(policy.isCompletedWork(record("normal",now,now))).isTrue();
        assertThat(policy.isCompletedWork(record("late",now,now))).isTrue();
        assertThat(policy.isCompletedWork(record("early",now,now))).isTrue();
        assertThat(policy.isCompletedWork(record("late_and_early",now,now))).isTrue();
        assertThat(policy.isCompletedWork(record("leave",now,now))).isFalse();
        assertThat(policy.exceptionReason(record("mystery",now,now))).isEqualTo("考勤状态异常");
    }

    private OaAttendanceRecord record(String status,Date in,Date out)
    {OaAttendanceRecord row=new OaAttendanceRecord();row.setStatus(status);row.setCheckInTime(in);row.setCheckOutTime(out);return row;}
}
```

- [ ] **Step 3: Add a failing attendance service regression**

Create a fake `OaAttendanceRecordMapper` using the same style as `OaSalaryServiceImplTest`. Put a historical row with `status=normal`, a non-null `checkInTime`, and null `checkOutTime` into the fake mapper. Inject it with `ReflectionTestUtils` and assert:

```java
List<OaAttendanceRecord> result=service.selectAllRecords(new OaAttendanceRecord(),201L);
assertThat(result).singleElement().extracting(OaAttendanceRecord::getStatus)
        .isEqualTo("pending_checkout");
```

Add this exact source contract to the test so the write path cannot regress while the mapper fixture covers reads:

```java
String source=java.nio.file.Files.readString(java.nio.file.Path.of(
        "src/main/java/com/erp/oa/service/impl/OaAttendanceServiceImpl.java"));
assertThat(source).contains("record.setStatus(OaAttendanceStatusPolicy.PENDING_CHECKOUT)")
        .doesNotContain("record.setStatus(\"normal\")");
```

- [ ] **Step 4: Run the new tests and verify they fail**

Run:

```bash
mvn -pl erp-modules/erp-oa -am \
  -Dtest=OaAttendanceStatusPolicyTest,OaAttendanceServiceImplTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: policy compilation fails and the service still returns historical missing-checkout rows as `normal`.

- [ ] **Step 5: Add status constants and fact-first policy**

```java
package com.erp.oa.support;

import java.util.Set;
import org.springframework.stereotype.Component;
import com.erp.oa.domain.OaAttendanceRecord;

@Component
public class OaAttendanceStatusPolicy
{
    public static final String PENDING_CHECKOUT="pending_checkout";
    public static final String NORMAL="normal";
    public static final String LATE="late";
    public static final String EARLY="early";
    public static final String LATE_AND_EARLY="late_and_early";
    public static final String ABSENT="absent";
    public static final String LEAVE="leave";
    private static final Set<String> COMPLETED=Set.of(NORMAL,LATE,EARLY,LATE_AND_EARLY);
    private static final Set<String> KNOWN=Set.of(PENDING_CHECKOUT,NORMAL,LATE,EARLY,LATE_AND_EARLY,ABSENT,LEAVE);

    public String normalizedStatus(OaAttendanceRecord row)
    {
        if(row!=null&&row.getCheckInTime()!=null&&row.getCheckOutTime()==null)return PENDING_CHECKOUT;
        return row==null?null:row.getStatus();
    }
    public OaAttendanceRecord normalizeForRead(OaAttendanceRecord row)
    {if(row!=null)row.setStatus(normalizedStatus(row));return row;}
    public boolean isCompletedWork(OaAttendanceRecord row)
    {return row!=null&&row.getCheckOutTime()!=null&&COMPLETED.contains(normalizedStatus(row));}
    public String exceptionReason(OaAttendanceRecord row)
    {
        String status=normalizedStatus(row);
        if(PENDING_CHECKOUT.equals(status))return "未签退";
        if(status==null||!KNOWN.contains(status))return "考勤状态异常";
        return null;
    }
}
```

- [ ] **Step 6: Use the pending status on write and normalize every read path**

In `checkIn`, replace `record.setStatus("normal")` with:

```java
record.setStatus(OaAttendanceStatusPolicy.PENDING_CHECKOUT);
```

Inject `OaAttendanceStatusPolicy`. Apply `normalizeForRead` to the results of `checkIn`, `checkOut`, `getRecordById`, `selectMyRecords`, and `selectAllRecords`. For lists, mutate only the returned DTO instances:

```java
private List<OaAttendanceRecord> normalizeForRead(List<OaAttendanceRecord> rows)
{
    if(rows!=null)rows.forEach(statusPolicy::normalizeForRead);
    return rows;
}
```

The checkout calculation remains the existing source of `normal`, `late`, `early`, and `late_and_early`.

- [ ] **Step 7: Add the new Excel label**

Update `OaAttendanceRecord.status`:

```java
@Excel(name = "状态", readConverterExp = "pending_checkout=未签退,normal=正常,late=迟到,early=早退,late_and_early=迟到+早退,absent=缺勤,leave=请假")
```

- [ ] **Step 8: Run the attendance tests**

Run:

```bash
mvn -pl erp-modules/erp-oa -am \
  -Dtest=OaAttendanceStatusPolicyTest,OaAttendanceServiceImplTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: new check-ins are pending until checkout, and old `normal` rows missing checkout normalize to pending on every read.

- [ ] **Step 9: Commit status normalization**

```bash
git add erp-modules/erp-oa/src/main/java/com/erp/oa/support/OaAttendanceStatusPolicy.java \
  erp-modules/erp-oa/src/test/java/com/erp/oa/support/OaAttendanceStatusPolicyTest.java \
  erp-modules/erp-oa/src/main/java/com/erp/oa/domain/OaAttendanceRecord.java \
  erp-modules/erp-oa/src/main/java/com/erp/oa/service/impl/OaAttendanceServiceImpl.java \
  erp-modules/erp-oa/src/test/java/com/erp/oa/service/impl/OaAttendanceServiceImplTest.java
git commit -m "fix: mark unfinished attendance as pending"
```

### Task 2: Add a server-side exception-only attendance queue

**Files:**
- Modify: `erp-modules/erp-oa/src/main/java/com/erp/oa/domain/OaAttendanceRecord.java`
- Modify: `erp-modules/erp-oa/src/main/resources/mapper/oa/OaAttendanceRecordMapper.xml`
- Create: `erp-modules/erp-oa/src/test/java/com/erp/oa/mapper/OaAttendanceRecordMapperBindingTest.java`

- [ ] **Step 1: Write the failing MyBatis binding test**

Parse `mapper/oa/OaAttendanceRecordMapper.xml` with a MyBatis `Configuration`, using the setup from `OaTodoMapperBindingTest`. Build:

```java
OaAttendanceRecord query=new OaAttendanceRecord();
query.setExceptionOnly(true);
query.setBeginDate("2026-07-01");
query.setEndDate("2026-07-31");
BoundSql sql=configuration.getMappedStatement(
        "com.erp.oa.mapper.OaAttendanceRecordMapper.selectOaAttendanceRecordList")
        .getBoundSql(query);
assertThat(sql.getSql()).contains("r.check_out_time is null","r.status not in");
assertThat(sql.getSql()).contains("r.work_date between ? and ?");
```

- [ ] **Step 2: Run the mapper test and verify it fails**

Run:

```bash
mvn -pl erp-modules/erp-oa -am \
  -Dtest=OaAttendanceRecordMapperBindingTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: `exceptionOnly` does not exist and SQL has no exception predicate.

- [ ] **Step 3: Add the query-only boolean**

In `OaAttendanceRecord`:

```java
private Boolean exceptionOnly;
public Boolean getExceptionOnly(){return exceptionOnly;}
public void setExceptionOnly(Boolean exceptionOnly){this.exceptionOnly=exceptionOnly;}
```

- [ ] **Step 4: Add the fact-based SQL predicate before paging**

Inside the existing `<where>`:

```xml
<if test="exceptionOnly == true">
    and (
        (r.check_in_time is not null and r.check_out_time is null)
        or r.status is null
        or r.status not in ('pending_checkout','normal','late','early','late_and_early','absent','leave')
    )
</if>
```

Do not define exception-only filtering in Java after the page has been returned.

- [ ] **Step 5: Run the mapper test**

Run:

```bash
mvn -pl erp-modules/erp-oa -am \
  -Dtest=OaAttendanceRecordMapperBindingTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: date range and exception predicates coexist in the generated SQL and the test passes.

- [ ] **Step 6: Commit the exception queue contract**

```bash
git add erp-modules/erp-oa/src/main/java/com/erp/oa/domain/OaAttendanceRecord.java \
  erp-modules/erp-oa/src/main/resources/mapper/oa/OaAttendanceRecordMapper.xml \
  erp-modules/erp-oa/src/test/java/com/erp/oa/mapper/OaAttendanceRecordMapperBindingTest.java
git commit -m "feat: filter attendance exceptions on the server"
```

### Task 3: Add a structured salary attendance preflight and transactional guard

**Files:**
- Create: `erp-modules/erp-oa/src/main/java/com/erp/oa/domain/vo/OaSalaryAttendanceIssueVo.java`
- Create: `erp-modules/erp-oa/src/main/java/com/erp/oa/domain/vo/OaSalaryAttendancePreflightVo.java`
- Modify: `erp-modules/erp-oa/src/main/java/com/erp/oa/service/IOaSalaryService.java`
- Modify: `erp-modules/erp-oa/src/main/java/com/erp/oa/service/impl/OaSalaryServiceImpl.java`
- Modify: `erp-modules/erp-oa/src/test/java/com/erp/oa/service/impl/OaSalaryServiceImplTest.java`

- [ ] **Step 1: Add failing preflight and no-delete tests**

Extend `FakeSalaryRecordMapper` with `int deleteCalls` and increment it in `deleteByShopDeptIdAndMonth`. Add:

```java
@Test
void shouldBlockBeforeDeletingSalaryWhenAttendanceHasNoCheckout()
{
    SecurityContextHolder.setUserId("1"); SecurityContextHolder.setUserName("admin");
    FakeSalaryRecordMapper salaries=new FakeSalaryRecordMapper();
    FakeAttendanceMapper attendance=new FakeAttendanceMapper();
    OaAttendanceRecord pending=attendance(11L,"seller01");
    pending.setCheckInTime(new Date()); pending.setCheckOutTime(null); pending.setStatus("normal");
    attendance.records.add(pending);
    FakeSalaryEmployeeMapper employees=new FakeSalaryEmployeeMapper();
    employees.employees.add(employee(11L,"seller01","3000.00"));
    OaSalaryServiceImpl service=salaryService(salaries,attendance,employees);

    OaSalaryAttendancePreflightVo preflight=service.preflightAttendance(201L,"2026-06",201L);
    assertThat(preflight.isBlocked()).isTrue();
    assertThat(preflight.getExceptionRecordCount()).isEqualTo(1);
    assertThat(preflight.getAffectedEmployeeCount()).isEqualTo(1);
    assertThat(preflight.getIssues()).singleElement().satisfies(issue -> {
        assertThat(issue.getUserName()).isEqualTo("seller01");
        assertThat(issue.getReason()).isEqualTo("未签退");
    });
    assertThatThrownBy(() -> service.calculateSalary(201L,"2026-06",201L))
            .isInstanceOf(ServiceException.class).hasMessageContaining("未签退");
    assertThat(salaries.deleteCalls).isZero();
    assertThat(salaries.records).isEmpty();
}
```

Add parameterized coverage for `normal`, `late`, `early`, and `late_and_early` with both timestamps; `leave` and `absent`; and one unknown status that must block. Add a completed-status record with null `workHours` and both timestamps, asserting preflight succeeds and aggregation derives hours in memory. Update the existing `attendance(userId,userName)` test helper to set both `checkInTime` and `checkOutTime` for its normal completed record, so the pre-existing salary-formula test remains a valid completed-attendance fixture.

- [ ] **Step 2: Run the salary service test and verify it fails**

Run:

```bash
mvn -pl erp-modules/erp-oa -am \
  -Dtest=OaSalaryServiceImplTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: DTOs and `preflightAttendance` are absent; the old aggregation counts every non-leave row as work.

- [ ] **Step 3: Add the structured result DTOs**

`OaSalaryAttendanceIssueVo` fields:

```java
private Long recordId;
private Long userId;
private String userName;
private String nickName;
@JsonFormat(pattern="yyyy-MM-dd",timezone="GMT+8") private Date workDate;
private String reason;
```

`OaSalaryAttendancePreflightVo` fields:

```java
private Long shopDeptId;
private String salaryMonth;
private boolean blocked;
private int exceptionRecordCount;
private int affectedEmployeeCount;
private int returnedIssueCount;
private boolean truncated;
private List<OaSalaryAttendanceIssueVo> issues=new ArrayList<>();
```

Provide normal Java getters/setters. The response returns at most 20 issues but counts all matching rows.

- [ ] **Step 4: Add a strict month range and preflight interface**

In `IOaSalaryService`:

```java
OaSalaryAttendancePreflightVo preflightAttendance(Long shopDeptId,String salaryMonth,Long selectedShopDeptId);
```

In the service, parse before querying:

```java
private MonthRange monthRange(String salaryMonth)
{
    try
    {
        java.time.YearMonth month=java.time.YearMonth.parse(salaryMonth,
                java.time.format.DateTimeFormatter.ofPattern("uuuu-MM"));
        java.time.ZoneId zone=java.time.ZoneId.of("Asia/Shanghai");
        return new MonthRange(
                Date.from(month.atDay(1).atStartOfDay(zone).toInstant()),
                Date.from(month.atEndOfMonth().atStartOfDay(zone).toInstant()));
    }
    catch(Exception error){throw new ServiceException("工资月份格式应为yyyy-MM");}
}
```

Use this private immutable record:

```java
private record MonthRange(Date start,Date end){}
```

Populate the same `shopDeptId`, `beginDate`, and `endDate` query used by calculation.

- [ ] **Step 5: Implement one fact-first preflight**

For each attendance row:

```text
checkIn exists + checkOut missing -> 未签退
status null or unknown -> 考勤状态异常
completed work status + workHours missing + either timestamp missing -> 工时数据不完整
completed work status + workHours missing + both timestamps present -> derive non-negative hours in memory
leave or absent -> valid non-work record
pending_checkout -> 未签退
```

Use `OaAttendanceStatusPolicy` for normalized status and completed-work membership. Count distinct non-null `userId` values; for rows without one, use `userName + workDate` as the affected-person grouping key. Sort issues by work date then user name before taking the first 20.

- [ ] **Step 6: Guard calculation before delete and make aggregation explicit**

At the beginning of `calculateSalary`, after resolving the target shop and validating the month, call an internal overload that uses that target and range. If blocked:

```java
throw new ServiceException(preflightMessage(preflight));
```

The message format is:

```text
本月有 3 条考勤异常，涉及 2 名员工，工资尚未计算。请先处理：张三 07-03（未签退）、李四 07-05（考勤状态异常）
```

Keep this call before `salaryMapper.deleteByShopDeptIdAndMonth`. Replace the old `if ("leave") else workDays++` aggregation with explicit branches:

```java
if (OaAttendanceStatusPolicy.LEAVE.equals(status)) agg.leaveDays++;
else if (OaAttendanceStatusPolicy.ABSENT.equals(status)) continue;
else if (statusPolicy.isCompletedWork(r))
{
    agg.workDays++;
    agg.lateTotalMinutes += r.getLateMinutes()!=null?r.getLateMinutes():0;
    agg.earlyTotalMinutes += r.getEarlyMinutes()!=null?r.getEarlyMinutes():0;
    agg.totalWorkHours = agg.totalWorkHours.add(r.getWorkHours());
}
else throw new ServiceException("考勤预检与工资聚合状态不一致");
```

Do not silently ignore unknown or pending records.

- [ ] **Step 7: Run salary tests**

Run:

```bash
mvn -pl erp-modules/erp-oa -am \
  -Dtest=OaAttendanceStatusPolicyTest,OaSalaryServiceImplTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: all abnormal branches block before deletion, completed rows preserve the existing formulas, and leave/absent do not increase work days.

- [ ] **Step 8: Commit the payroll guard**

```bash
git add erp-modules/erp-oa/src/main/java/com/erp/oa/domain/vo/OaSalaryAttendanceIssueVo.java \
  erp-modules/erp-oa/src/main/java/com/erp/oa/domain/vo/OaSalaryAttendancePreflightVo.java \
  erp-modules/erp-oa/src/main/java/com/erp/oa/service/IOaSalaryService.java \
  erp-modules/erp-oa/src/main/java/com/erp/oa/service/impl/OaSalaryServiceImpl.java \
  erp-modules/erp-oa/src/test/java/com/erp/oa/service/impl/OaSalaryServiceImplTest.java
git commit -m "fix: block payroll on attendance exceptions"
```

### Task 4: Expose preflight and connect salary errors to the attendance queue

**Files:**
- Modify: `erp-modules/erp-oa/src/main/java/com/erp/oa/controller/OaSalaryController.java`
- Modify: `erp-modules/erp-oa/src/test/java/com/erp/oa/controller/OaSalaryControllerTest.java`
- Modify: `erp-ui/src/api/oa/salary.js`
- Modify: `erp-ui/src/views/oa/salary/index.vue`
- Modify: `erp-ui/src/views/oa/attendance/index.vue`
- Create: `erp-ui/test/attendancePayrollGuard.test.js`

- [ ] **Step 1: Add a failing controller contract test**

Extend `FakeSalaryService` to capture preflight arguments and add:

```java
@Test void shouldPreflightAttendanceWithSelectedShopContext()
{
    FakeSalaryService service=new FakeSalaryService();
    OaSalaryController controller=controller(service);
    AjaxResult result=controller.attendancePreflight(202L,"2026-07",requestWithShopHeader(201L));
    assertThat(result.isSuccess()).isTrue();
    assertThat(service.preflightShopDeptId).isEqualTo(202L);
    assertThat(service.preflightSalaryMonth).isEqualTo("2026-07");
    assertThat(service.preflightSelectedShopDeptId).isEqualTo(201L);
}
```

- [ ] **Step 2: Add failing frontend behavior assertions**

The new Node test must assert:

```javascript
assert.ok(salaryApiSource.includes("/oa/salary/attendance-preflight"))
assert.ok(salarySource.includes("preflightSalaryAttendance"))
assert.ok(salarySource.includes("exceptionOnly: \"true\""))
assert.ok(attendanceSource.includes("pending_checkout"))
assert.ok(attendanceSource.includes("仅看异常"))
```

Use a salary VM with a preflight response `{blocked:true,exceptionRecordCount:2,affectedEmployeeCount:1,issues:[...]}`. Verify `calculateSalary` is not called, `list` is unchanged, `calcLoading` returns to false, and the `查看异常考勤` action routes to `/oa/attendance` with `salaryMonth`, `shopDeptId`, and `exceptionOnly=true`.

Use an attendance VM route with `salaryMonth=2026-07&exceptionOnly=true`; verify its first all-records request contains `beginDate=2026-07-01`, `endDate=2026-07-31`, and `exceptionOnly=true`.

- [ ] **Step 3: Run controller and frontend tests and verify they fail**

Run:

```bash
mvn -pl erp-modules/erp-oa -am \
  -Dtest=OaSalaryControllerTest -Dsurefire.failIfNoSpecifiedTests=false test
cd erp-ui && node scripts/run-node-tests.cjs attendancePayrollGuard.test.js
```

Expected: endpoint/API/UI behavior is absent.

- [ ] **Step 4: Add the read-only preflight endpoint**

In `OaSalaryController`:

```java
@RequiresPermissions("oa:salary:calculate")
@GetMapping("/attendance-preflight")
public AjaxResult attendancePreflight(Long shopDeptId,String salaryMonth,HttpServletRequest request)
{
    if(StringUtils.isEmpty(salaryMonth))return error("工资月份不能为空");
    return success(salaryService.preflightAttendance(shopDeptId,salaryMonth,resolveShopDeptId(request)));
}
```

This endpoint has no idempotency annotation and performs no writes.

- [ ] **Step 5: Add the frontend API and preflight-first salary flow**

In `salary.js`:

```javascript
export function preflightSalaryAttendance(params) {
  return request({ url: "/oa/salary/attendance-preflight", method: "get", params })
}
```

Change `doCalculate` to set `calcLoading` before preflight. If not blocked, show the existing overwrite confirmation and then call `calculateSalary`. If blocked, preserve `list` and open a warning confirm containing record count, employee count, and at most five plain-text issue lines. The confirm button is `查看异常考勤`; its handler routes:

```javascript
this.$router.push({
  path: "/oa/attendance",
  query: {
    salaryMonth: this.salaryMonth,
    shopDeptId: String(preflight.shopDeptId || this.salaryConfig.shopDeptId || ""),
    exceptionOnly: "true"
  }
})
```

The dialog uses newline text, not HTML. Put `calcLoading=false` in the final promise `finally`, including preflight rejection and dialog cancellation.

- [ ] **Step 6: Initialize attendance from the exception route and render pending status**

Add a pure `monthDateRange(value)` method that accepts only `yyyy-MM`, computes the last day with `new Date(year, month, 0)`, and returns `[]` for invalid input. During `created`, if the current user can view all attendance and `exceptionOnly=true`, set:

```javascript
this.viewScope = "all"
this.queryParams.exceptionOnly = true
this.dateRange = this.monthDateRange(this.$route.query.salaryMonth)
```

Add an HR-only `el-checkbox` labeled `仅看异常`; toggling it resets page 1 and reloads. Render:

```html
<el-tag v-if="scope.row.status === 'pending_checkout'" type="warning" size="mini">未签退</el-tag>
<el-tag v-else-if="!knownAttendanceStatus(scope.row.status)" type="danger" size="mini">状态异常</el-tag>
```

Date range, exception flag, and the selected shop context stay on the first request. Do not add a correction button.

- [ ] **Step 7: Run controller and frontend tests**

Run:

```bash
mvn -pl erp-modules/erp-oa -am \
  -Dtest=OaSalaryControllerTest,OaSalaryServiceImplTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
cd erp-ui && node scripts/run-node-tests.cjs attendancePayrollGuard.test.js
```

Expected: blocked preflight keeps the salary table unchanged, no calculate call occurs, and the attendance page opens the exact exception month.

- [ ] **Step 8: Commit the linked UI flow**

```bash
git add erp-modules/erp-oa/src/main/java/com/erp/oa/controller/OaSalaryController.java \
  erp-modules/erp-oa/src/test/java/com/erp/oa/controller/OaSalaryControllerTest.java \
  erp-ui/src/api/oa/salary.js erp-ui/src/views/oa/salary/index.vue \
  erp-ui/src/views/oa/attendance/index.vue erp-ui/test/attendancePayrollGuard.test.js
git commit -m "feat: link payroll guard to attendance exceptions"
```

### Task 5: Run the attendance/payroll verification gate

**Files:**
- Test only; do not change production files unless a failure is directly caused by this plan.

- [ ] **Step 1: Run the complete focused backend set**

```bash
mvn -pl erp-modules/erp-oa -am \
  -Dtest=OaAttendanceStatusPolicyTest,OaAttendanceServiceImplTest,OaAttendanceRecordMapperBindingTest,OaSalaryServiceImplTest,OaSalaryControllerTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: BUILD SUCCESS with zero failures and zero errors.

- [ ] **Step 2: Run the complete OA module test suite**

```bash
mvn -pl erp-modules/erp-oa -am test
```

Expected: BUILD SUCCESS. Existing unrelated test failures must be documented and separated from this plan before proceeding.

- [ ] **Step 3: Run frontend contracts and production build**

```bash
cd erp-ui && node scripts/run-node-tests.cjs attendancePayrollGuard.test.js oaPurchaseInteractionConsistency.test.js \
  && npm test \
  && npm run build:prod
```

Expected: focused and full Node suites pass, then the production build exits 0.

- [ ] **Step 4: Perform the transactional acceptance check**

Use a non-production test shop/month with one already calculated salary record and one attendance row whose checkout is null:

```text
1. Record the existing salary record ID and value.
2. Open the attendance list; confirm the row says 未签退.
3. Click 计算本月工资; confirm the message gives employee/date/reason.
4. Query the salary record again; ID and value are unchanged.
5. Click 查看异常考勤; confirm the target shop/month and 仅看异常 are active.
6. Complete or remove the fixture through approved test-data setup, not a new UI repair action.
7. Recalculate; confirm completed statuses aggregate with the existing formula.
```

Expected: the abnormal attempt performs no salary delete/insert, while clean attendance still calculates successfully.

- [ ] **Step 5: Close the verification gate without a generic commit**

If a directly related repair was needed, return to the owning task, rerun its focused test, and amend its commit. Do not create a broad verification-only commit.
