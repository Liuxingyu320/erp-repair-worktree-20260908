# User Profile Path-Aligned Hierarchy Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make user profile organization fields follow the real department path, align store parents backward into levels 1–3, and derive the department supervisor from the first department below the company.

**Architecture:** Keep `SysUserProfileDerivationService` as the single source of truth used by list, detail, export, and preview flows. Replace nearest-`COMPANY` matching with a validated `GROUP`-anchored path resolver, then use separate helpers for store-backward alignment, non-store-forward alignment, and supervisor selection.

**Tech Stack:** Java 17, Spring Boot, MyBatis domain models, JUnit 5, AssertJ, Maven Surefire

---

## File map

- Modify: `erp-modules/erp-system/src/main/java/com/erp/system/service/impl/SysUserProfileDerivationService.java`
  - Owns path validation, company/store anchors, level mapping, supervisor mapping, and warning generation.
- Modify: `erp-modules/erp-system/src/test/java/com/erp/system/service/impl/SysUserProfileDerivationServiceTest.java`
  - Covers the real production-shaped path, backward and forward alignment, malformed paths, warning behavior, positions, and date regression cases.
- Create: `docs/superpowers/plans/2026-07-10-user-profile-path-aligned-hierarchy.md`
  - Records this TDD execution sequence.

No controller, mapper, API, or Vue file changes are required. Existing callers already invoke the derivation service, and the UI already renders returned values and warning arrays.

### Task 1: Lock the real store path into a failing test

**Files:**
- Modify: `erp-modules/erp-system/src/test/java/com/erp/system/service/impl/SysUserProfileDerivationServiceTest.java`
- Test: `erp-modules/erp-system/src/test/java/com/erp/system/service/impl/SysUserProfileDerivationServiceTest.java`

- [ ] **Step 1: Replace the standard-path test with the approved real-path example**

Use intermediate nodes typed as `COMPANY` so the test proves that only the first business node after `GROUP` is the company anchor:

```java
@Test
@DisplayName("真实门店路径从门店向上对齐并取公司下一级负责人")
void deriveRealStorePathFromGroupAnchor()
{
    List<SysDept> departments = Arrays.asList(
            dept(100L, 0L, "0", "金英灵韵集团", "GROUP", "集团负责人", "0"),
            dept(101L, 100L, "0,100", "金英灵韵", "COMPANY", "公司负责人", "0"),
            dept(102L, 101L, "0,100,101", "浙江区域", "COMPANY", "浙江区域负责人", "0"),
            dept(103L, 102L, "0,100,101,102", "浙江一区", "COMPANY", "浙江一区负责人", "0"),
            dept(104L, 103L, "0,100,101,102,103", "杭州柏悦", "STORE", "门店负责人", "0"));

    SysUserProfile result = service.derive(user(104L), departments, Collections.emptyList(), TODAY);

    assertThat(result.getCompanyName()).isEqualTo("金英灵韵");
    assertThat(result.getDeptLevel1Name()).isNull();
    assertThat(result.getDeptLevel2Name()).isEqualTo("浙江区域");
    assertThat(result.getDeptLevel3Name()).isEqualTo("浙江一区");
    assertThat(result.getStoreName()).isEqualTo("杭州柏悦");
    assertThat(result.getDepartmentSupervisor()).isEqualTo("浙江区域负责人");
    assertThat(result.getDerivedWarnings()).isEmpty();
}
```

- [ ] **Step 2: Run only the new test and verify the current algorithm fails**

Run:

```bash
mvn -pl erp-modules/erp-system -am \
  -Dtest=SysUserProfileDerivationServiceTest#deriveRealStorePathFromGroupAnchor \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: FAIL because the current implementation selects “浙江一区” as the nearest `COMPANY` and excludes the intermediate `COMPANY` nodes from department levels.

- [ ] **Step 3: Commit the red test together with the plan**

```bash
git add docs/superpowers/plans/2026-07-10-user-profile-path-aligned-hierarchy.md \
  erp-modules/erp-system/src/test/java/com/erp/system/service/impl/SysUserProfileDerivationServiceTest.java
git commit -m "test: define path-aligned user hierarchy"
```

### Task 2: Implement group-anchored organization mapping

**Files:**
- Modify: `erp-modules/erp-system/src/main/java/com/erp/system/service/impl/SysUserProfileDerivationService.java`
- Test: `erp-modules/erp-system/src/test/java/com/erp/system/service/impl/SysUserProfileDerivationServiceTest.java`

- [ ] **Step 1: Define precise warning constants and department-layer types**

Replace the structural set and extend the warning constants:

```java
static final String ORGANIZATION_WARNING = "组织结构待修复";
static final String COMPANY_WARNING = "无法识别所属公司";
static final String HIERARCHY_WARNING = "组织层级超过系统定义";
static final String SUPERVISOR_WARNING = "未配置部门负责人";
static final String DATE_WARNING = "档案日期异常";

private static final Set<String> NON_DEPARTMENT_TYPES = Set.of("GROUP", "STORE", "WAREHOUSE");
```

`COMPANY` must not be in `NON_DEPARTMENT_TYPES`: after the first business node below `GROUP` becomes the company anchor, later `COMPANY` nodes participate as department layers.

- [ ] **Step 2: Replace nearest-company mapping in `derive`**

After `isValidPath(path)` succeeds, use this control flow. Check `groupIndex` before computing the company search start:

```java
int groupIndex = nearestTypeIndex(path, "GROUP");
if (groupIndex < 0)
{
    addWarning(profile, COMPANY_WARNING);
    return profile;
}
int companyIndex = groupIndex + 1;
if (companyIndex >= path.size() || !isDepartmentLayer(path.get(companyIndex)))
{
    addWarning(profile, COMPANY_WARNING);
    return profile;
}

int storeIndex = nearestTypeIndex(path, "STORE");
if (storeIndex >= 0 && storeIndex <= companyIndex)
{
    addWarning(profile, ORGANIZATION_WARNING);
    return profile;
}

int levelEnd = storeIndex > companyIndex ? storeIndex : path.size();
List<SysDept> departmentLayers = departmentLayers(path, companyIndex + 1, levelEnd);

profile.setCompanyName(path.get(companyIndex).getDeptName());
if (storeIndex > companyIndex)
{
    applyStoreAlignedLevels(profile, departmentLayers);
    profile.setStoreName(path.get(storeIndex).getDeptName());
}
else
{
    applyNonStoreLevels(profile, departmentLayers);
}
applyDepartmentSupervisor(profile, departmentLayers);
return profile;
```

- [ ] **Step 3: Add focused layer helpers**

```java
private List<SysDept> departmentLayers(List<SysDept> path, int startInclusive, int endExclusive)
{
    List<SysDept> layers = new ArrayList<>();
    for (int index = Math.max(0, startInclusive); index < Math.min(path.size(), endExclusive); index++)
    {
        SysDept department = path.get(index);
        if (isDepartmentLayer(department))
        {
            layers.add(department);
        }
    }
    return layers;
}

private boolean isDepartmentLayer(SysDept department)
{
    return department != null && !NON_DEPARTMENT_TYPES.contains(normalizeType(department.getDeptType()));
}
```

- [ ] **Step 4: Add store-backward and non-store-forward mapping helpers**

```java
private void applyStoreAlignedLevels(SysUserProfile profile, List<SysDept> layers)
{
    if (layers.size() > 3)
    {
        addWarning(profile, HIERARCHY_WARNING);
    }
    int visibleStart = Math.max(0, layers.size() - 3);
    List<SysDept> visible = layers.subList(visibleStart, layers.size());
    int targetStart = 3 - visible.size();
    profile.setDeptLevel1Name(targetStart == 0 ? nameAt(visible, 0) : null);
    profile.setDeptLevel2Name(targetStart <= 1 ? nameAt(visible, 1 - targetStart) : null);
    profile.setDeptLevel3Name(targetStart <= 2 ? nameAt(visible, 2 - targetStart) : null);
}

private void applyNonStoreLevels(SysUserProfile profile, List<SysDept> layers)
{
    if (layers.size() > 3)
    {
        addWarning(profile, HIERARCHY_WARNING);
    }
    profile.setDeptLevel1Name(nameAt(layers, 0));
    profile.setDeptLevel2Name(nameAt(layers, 1));
    profile.setDeptLevel3Name(nameAt(layers, 2));
}
```

- [ ] **Step 5: Derive the supervisor only from the company’s first department layer**

Remove `nearestLeader` and replace it with:

```java
private void applyDepartmentSupervisor(SysUserProfile profile, List<SysDept> layers)
{
    if (layers.isEmpty())
    {
        addWarning(profile, SUPERVISOR_WARNING);
        return;
    }
    String leader = layers.get(0).getLeader();
    if (leader == null || leader.isBlank())
    {
        addWarning(profile, SUPERVISOR_WARNING);
        return;
    }
    profile.setDepartmentSupervisor(leader);
}
```

- [ ] **Step 6: Run the real-path test and verify it passes**

Run the Task 1 Maven command again.

Expected: PASS with company “金英灵韵”, levels `null / 浙江区域 / 浙江一区`, store “杭州柏悦”, and supervisor “浙江区域负责人”.

- [ ] **Step 7: Commit the core mapping implementation**

```bash
git add erp-modules/erp-system/src/main/java/com/erp/system/service/impl/SysUserProfileDerivationService.java
git commit -m "feat: align user hierarchy to real paths"
```

### Task 3: Update edge-case and regression tests to the approved semantics

**Files:**
- Modify: `erp-modules/erp-system/src/test/java/com/erp/system/service/impl/SysUserProfileDerivationServiceTest.java`
- Test: `erp-modules/erp-system/src/test/java/com/erp/system/service/impl/SysUserProfileDerivationServiceTest.java`

- [ ] **Step 1: Add a reusable valid managed path for position and duration tests**

```java
private static List<SysDept> managedDepartmentPath()
{
    return Arrays.asList(
            dept(1L, 0L, "0", "星河集团", "GROUP", "集团负责人", "0"),
            dept(2L, 1L, "0,1", "星河公司", "COMPANY", "公司负责人", "0"),
            dept(3L, 2L, "0,1,2", "人事部", "COMPANY", "人事总监", "0"));
}
```

Update position and duration tests to use `user(3L)` and `managedDepartmentPath()` so they do not gain unrelated company or supervisor warnings.

- [ ] **Step 2: Rewrite the non-store and ancestor-store expectations**

For a non-store path `GROUP → COMPANY → 人事部`, assert:

```java
assertThat(result.getCompanyName()).isEqualTo("星河公司");
assertThat(result.getDeptLevel1Name()).isEqualTo("人事部");
assertThat(result.getDeptLevel2Name()).isNull();
assertThat(result.getDeptLevel3Name()).isNull();
assertThat(result.getStoreName()).isNull();
assertThat(result.getDepartmentSupervisor()).isEqualTo("人事总监");
assertThat(result.getDerivedWarnings()).isEmpty();
```

For `GROUP → COMPANY → 华东区 → STORE → 门店运营组`, assert:

```java
assertThat(result.getDeptLevel1Name()).isNull();
assertThat(result.getDeptLevel2Name()).isNull();
assertThat(result.getDeptLevel3Name()).isEqualTo("华东区");
assertThat(result.getStoreName()).isEqualTo("陆家嘴店");
assertThat(result.getDepartmentSupervisor()).isEqualTo("区域主管");
```

- [ ] **Step 3: Assert exact warning behavior**

Add or update tests for these cases:

```java
assertThat(missingGroupResult.getDerivedWarnings())
        .containsExactly(SysUserProfileDerivationService.COMPANY_WARNING);

assertThat(missingLeaderResult.getDepartmentSupervisor()).isNull();
assertThat(missingLeaderResult.getDerivedWarnings())
        .containsExactly(SysUserProfileDerivationService.SUPERVISOR_WARNING);

assertThat(overDepthStoreResult.getCompanyName()).isEqualTo("星河公司");
assertThat(overDepthStoreResult.getDeptLevel1Name()).isEqualTo("二级");
assertThat(overDepthStoreResult.getDeptLevel2Name()).isEqualTo("三级");
assertThat(overDepthStoreResult.getDeptLevel3Name()).isEqualTo("四级");
assertThat(overDepthStoreResult.getStoreName()).isEqualTo("门店");
assertThat(overDepthStoreResult.getDerivedWarnings())
        .containsExactly(SysUserProfileDerivationService.HIERARCHY_WARNING);
```

Give the first layer in the over-depth fixture a nonblank leader so the test isolates the hierarchy warning.

- [ ] **Step 4: Add a non-store over-depth test**

Use `GROUP → COMPANY → 一级 → 二级 → 三级 → 四级`, derive the user at “四级”, and assert the forward mapping remains `一级 / 二级 / 三级` plus `HIERARCHY_WARNING`.

- [ ] **Step 5: Run the full derivation-service test class**

```bash
mvn -pl erp-modules/erp-system -am \
  -Dtest=SysUserProfileDerivationServiceTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: all `SysUserProfileDerivationServiceTest` cases pass with zero failures and zero errors.

- [ ] **Step 6: Commit the completed regression coverage**

```bash
git add erp-modules/erp-system/src/test/java/com/erp/system/service/impl/SysUserProfileDerivationServiceTest.java
git commit -m "test: cover path-aligned hierarchy edges"
```

### Task 4: Verify all user-profile boundaries and inspect the final diff

**Files:**
- Verify: `erp-modules/erp-system/src/main/java/com/erp/system/service/impl/SysUserProfileDerivationService.java`
- Verify: `erp-modules/erp-system/src/test/java/com/erp/system/service/impl/SysUserProfileDerivationServiceTest.java`
- Verify: `erp-modules/erp-system/src/test/java/com/erp/system/service/impl/SysUserServiceImplTest.java`
- Verify: `erp-modules/erp-system/src/test/java/com/erp/system/mapper/SysUserProfileDerivedWriteBoundaryTest.java`
- Verify: `erp-modules/erp-system/src/test/java/com/erp/system/controller/SysUserDerivedPreviewControllerTest.java`

- [ ] **Step 1: Run the focused user-profile backend suite**

```bash
mvn -pl erp-modules/erp-system -am \
  -Dtest=SysUserProfileDerivationServiceTest,SysUserServiceImplTest,SysUserProfileDerivedWriteBoundaryTest,SysUserDerivedPreviewControllerTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: all selected tests pass with zero failures and zero errors.

- [ ] **Step 2: Run the complete system-module test suite**

```bash
mvn -pl erp-modules/erp-system -am test
```

Expected: reactor build success and zero test failures.

- [ ] **Step 3: Check formatting and scope**

```bash
git diff --check
git status --short
git diff --stat 6月13号...HEAD
git diff 6月13号...HEAD -- \
  erp-modules/erp-system/src/main/java/com/erp/system/service/impl/SysUserProfileDerivationService.java \
  erp-modules/erp-system/src/test/java/com/erp/system/service/impl/SysUserProfileDerivationServiceTest.java
```

Expected: no whitespace errors; only the plan, derivation service, and its unit test changed for this feature.

- [ ] **Step 4: Commit any verification-only corrections**

If verification required a correction, stage only the two Java files and commit it:

```bash
git add erp-modules/erp-system/src/main/java/com/erp/system/service/impl/SysUserProfileDerivationService.java \
  erp-modules/erp-system/src/test/java/com/erp/system/service/impl/SysUserProfileDerivationServiceTest.java
git commit -m "fix: harden user hierarchy path mapping"
```

If no correction was required, do not create an empty commit.
