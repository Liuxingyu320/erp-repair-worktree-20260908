# Transfer Target-Store Position Chain Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Resolve transfer level-4 and level-3 approvers from the target store's real manager/assistant positions, responsibility scope, and post ordering while preserving the two configured trailing approvers.

**Architecture:** `InvTransferApprovalCandidateMapper` exposes four narrow facts: active target-store validity, the active store-manager sort anchor, direct target-store manager/assistant candidates, and higher-position candidates whose responsibility scope covers the target store. `InvTransferApprovalServiceImpl` applies the business policy: manager first, assistant fallback, closest higher sort for level 3, same-level any-one approval, submitter skip, and non-blocking dynamic warnings. Existing node roles and task snapshots remain compatible; only their resolution semantics and labels change.

**Tech Stack:** Java 17, Spring Boot, MyBatis XML, JUnit 5, AssertJ, Maven, Vue 2, Node source-contract tests, MySQL 5.7-compatible SQL.

---

## File responsibility map

- `erp-modules/erp-inventory/src/main/java/com/erp/inventory/mapper/InvTransferApprovalCandidateMapper.java`: declare the three position-chain candidate queries while retaining configured-node queries.
- `erp-modules/erp-inventory/src/main/resources/mapper/inventory/InvTransferApprovalCandidateMapper.xml`: implement direct level-4 scope and inherited level-3 scope as different SQL contracts.
- `erp-modules/erp-inventory/src/main/java/com/erp/inventory/service/impl/InvTransferApprovalServiceImpl.java`: select the level-4 fallback, closest higher level-3 sort, fixed-node exclusions, submitter skip, warnings, and snapshots.
- `erp-modules/erp-inventory/src/main/java/com/erp/inventory/service/impl/InvTransferApprovalRuleServiceImpl.java`: keep both protected dynamic nodes first and give them business-accurate names.
- `erp-ui/src/views/inventory/transfer/rules.vue`: explain the automatic manager/assistant and upper-leader rules without exposing temporary-post selection.
- `sql/erp_inventory_transfer_auto_hierarchy_approval_20260710.sql` and Docker copy: migrate old rules to the revised dynamic node labels while preserving trailing nodes.
- `sql/erp_inventory_transfer_position_chain_preview_20260710.sql`: read-only all-store candidate preview.
- `sql/erp_inventory_transfer_temporary_post_cleanup_20260710.sql`: guarded manual cleanup for the two temporary posts after validation.
- Existing backend and Node tests: lock query scope, chain ordering, fallback, skip, any-one, warning, UI, and migration behavior.

### Task 1: Replace profile hierarchy queries with target-store position queries

**Files:**
- Modify: `erp-modules/erp-inventory/src/test/java/com/erp/inventory/mapper/InventoryMapperBindingTest.java:260-315`
- Modify: `erp-modules/erp-inventory/src/main/java/com/erp/inventory/mapper/InvTransferApprovalCandidateMapper.java:1-22`
- Modify: `erp-modules/erp-inventory/src/main/resources/mapper/inventory/InvTransferApprovalCandidateMapper.xml:1-110`

- [ ] **Step 1: Write the failing mapper contract test**

Replace the profile-text contract with a target-store contract:

```java
@Test
@DisplayName("调拨动态审批按目标门店直接四级和覆盖范围三级查询")
void shouldResolveDynamicApproversByTargetStorePositionChain() throws Exception
{
    Configuration configuration = new Configuration();
    registerAliases(configuration);
    parseMapper(configuration, "mapper/inventory/InvTransferApprovalCandidateMapper.xml");

    String mapperXml = resourceText("mapper/inventory/InvTransferApprovalCandidateMapper.xml");
    assertMapped(configuration, InvTransferApprovalCandidateMapper.class, "selectActivePostSortByCode");
    assertMapped(configuration, InvTransferApprovalCandidateMapper.class, "countActiveTargetStore");
    assertMapped(configuration, InvTransferApprovalCandidateMapper.class, "selectDirectStoreUsersByPostCode");
    assertMapped(configuration, InvTransferApprovalCandidateMapper.class, "selectCoveredHigherPostUsers");
    assertThat(mapperXml)
            .contains(
                    "p.post_code = #{postCode}",
                    "u.dept_id = target_dept.dept_id",
                    "direct_scope.dept_id = target_dept.dept_id",
                    "find_in_set(scope_dept.dept_id, target_dept.ancestors)",
                    "p.post_sort &lt; #{managerPostSort}",
                    "excludedPostCodes",
                    "excludedUserIds",
                    "order by p.post_sort desc, u.user_id")
            .doesNotContain(
                    "selectApprovalOrgProfile",
                    "selectHighestLeaderUsers",
                    "profile.dept_level3_name",
                    "profile.store_name");
}
```

- [ ] **Step 2: Run the mapper test and verify RED**

Run:

```bash
mvn -pl erp-modules/erp-inventory -am \
  -Dtest=InventoryMapperBindingTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: FAIL because the three new statements are not mapped and the old profile queries still exist.

- [ ] **Step 3: Change the mapper interface**

Replace the two profile methods with these signatures and keep `selectUsersByDeptAndPostCode` plus `selectPostCodesByUserId` unchanged:

```java
Integer selectActivePostSortByCode(@Param("postCode") String postCode);

int countActiveTargetStore(@Param("targetDeptId") Long targetDeptId);

List<Map<String, Object>> selectDirectStoreUsersByPostCode(
        @Param("targetDeptId") Long targetDeptId,
        @Param("postCode") String postCode);

List<Map<String, Object>> selectCoveredHigherPostUsers(
        @Param("targetDeptId") Long targetDeptId,
        @Param("managerPostSort") Integer managerPostSort,
        @Param("excludedPostCodes") List<String> excludedPostCodes,
        @Param("excludedUserIds") List<Long> excludedUserIds);
```

- [ ] **Step 4: Implement the direct level-4 SQL**

Add the sort lookup and a direct-scope query. The direct query must accept primary-department membership or an exact `sys_user_shop` row, but must not use ancestor inheritance:

```xml
<select id="selectActivePostSortByCode" resultType="java.lang.Integer">
    select min(p.post_sort)
    from sys_post p
    where p.post_code = #{postCode}
      and p.status = '0'
</select>

<select id="countActiveTargetStore" resultType="int">
    select count(1)
    from sys_dept target_dept
    where target_dept.dept_id = #{targetDeptId}
      and target_dept.del_flag = '0'
      and target_dept.status = '0'
      and target_dept.dept_type = 'STORE'
</select>

<select id="selectDirectStoreUsersByPostCode" resultType="map">
    select distinct u.user_id as userId,
           coalesce(nullif(u.nick_name, ''), u.user_name) as userName,
           p.post_sort as postSort
    from sys_user u
    join sys_user_post up on up.user_id = u.user_id
    join sys_post p on p.post_id = up.post_id
    join sys_dept target_dept on target_dept.dept_id = #{targetDeptId}
    left join sys_user_profile profile on profile.user_id = u.user_id
    where u.del_flag = '0'
      and u.status = '0'
      and p.status = '0'
      and p.post_code = #{postCode}
      and target_dept.del_flag = '0'
      and target_dept.status = '0'
      and target_dept.dept_type = 'STORE'
      and (profile.employee_status is null
           or trim(profile.employee_status) = ''
           or trim(profile.employee_status) &lt;&gt; '离职')
      and (
          u.dept_id = target_dept.dept_id
          or exists (
              select 1
              from sys_user_shop direct_scope
              where direct_scope.user_id = u.user_id
                and direct_scope.dept_id = target_dept.dept_id
          )
      )
    order by u.user_id
</select>
```

- [ ] **Step 5: Implement the inherited level-3 SQL**

The level-3 query returns all eligible higher-position rows in closest-first order; Java selects the first sort band:

```xml
<select id="selectCoveredHigherPostUsers" resultType="map">
    select distinct u.user_id as userId,
           coalesce(nullif(u.nick_name, ''), u.user_name) as userName,
           p.post_sort as postSort
    from sys_user u
    join sys_user_post up on up.user_id = u.user_id
    join sys_post p on p.post_id = up.post_id
    join sys_user_shop us on us.user_id = u.user_id
    join sys_dept target_dept on target_dept.dept_id = #{targetDeptId}
    join sys_dept scope_dept on scope_dept.dept_id = us.dept_id
    left join sys_user_profile profile on profile.user_id = u.user_id
    where u.del_flag = '0'
      and u.status = '0'
      and p.status = '0'
      and target_dept.del_flag = '0'
      and target_dept.status = '0'
      and target_dept.dept_type = 'STORE'
      and scope_dept.del_flag = '0'
      and scope_dept.status = '0'
      and scope_dept.dept_type in ('GROUP', 'COMPANY', 'STORE', 'WAREHOUSE')
      and (target_dept.dept_id = scope_dept.dept_id
           or find_in_set(scope_dept.dept_id, target_dept.ancestors))
      and p.post_sort &lt; #{managerPostSort}
      and (profile.employee_status is null
           or trim(profile.employee_status) = ''
           or trim(profile.employee_status) &lt;&gt; '离职')
      <if test="excludedPostCodes != null and excludedPostCodes.size() > 0">
          and p.post_code not in
          <foreach collection="excludedPostCodes" item="postCode" open="(" separator="," close=")">
              #{postCode}
          </foreach>
      </if>
      <if test="excludedUserIds != null and excludedUserIds.size() > 0">
          and u.user_id not in
          <foreach collection="excludedUserIds" item="userId" open="(" separator="," close=")">
              #{userId}
          </foreach>
      </if>
    order by p.post_sort desc, u.user_id
</select>
```

- [ ] **Step 6: Run the mapper test and verify GREEN**

Run the command from Step 2. Expected: all `InventoryMapperBindingTest` tests pass with zero failures.

- [ ] **Step 7: Commit Task 1**

```bash
git add erp-modules/erp-inventory/src/main/java/com/erp/inventory/mapper/InvTransferApprovalCandidateMapper.java \
        erp-modules/erp-inventory/src/main/resources/mapper/inventory/InvTransferApprovalCandidateMapper.xml \
        erp-modules/erp-inventory/src/test/java/com/erp/inventory/mapper/InventoryMapperBindingTest.java
git commit -m "feat: query transfer approvers by target store positions"
```

### Task 2: Resolve manager, assistant fallback, and closest upper leader

**Files:**
- Modify: `erp-modules/erp-inventory/src/test/java/com/erp/inventory/service/impl/InvTransferApprovalServiceImplTest.java:140-345,988-1040`
- Modify: `erp-modules/erp-inventory/src/main/java/com/erp/inventory/service/impl/InvTransferApprovalServiceImpl.java:35-65,651-880,1040-1070`

- [ ] **Step 1: Replace the old profile-based happy-path test with a failing position-chain test**

Use a fake mapper that returns a manager sort of 5, two direct store managers, and higher candidates at sorts 4 and 3:

```java
@Test
@DisplayName("普通员工按目标门店店长和最接近的上级岗位进入固定审批")
void shouldResolveTargetStoreManagerAndClosestHigherLeaderBeforeFixedNodes()
{
    SecurityContextHolder.setUserId("9");
    SecurityContextHolder.setUserName("employee");
    FakeCandidateMapper candidateMapper = new FakeCandidateMapper()
            .managerSort(5)
            .addDirect("dz", leader(101L, "managerA", 5), leader(102L, "managerB", 5))
            .addHigher(leader(201L, "upperA", 4), leader(202L, "tooHigh", 3))
            .add(202L, "yyzj", user(301L, "fixedOne"))
            .add(202L, "zjl", user(401L, "fixedTwo"));
    FakeTaskMapper taskMapper = new FakeTaskMapper();
    InvTransferApprovalServiceImpl service = newService(
            rule(node(1, "四级负责人（店长/店助）", "level4_highest", null),
                    node(2, "三级负责人（店长上一级）", "level3_highest", null),
                    node(3, "固定审批1", "to_leader", "yyzj"),
                    node(4, "固定审批2", "to_leader", "zjl")),
            candidateMapper, new FakeInstanceMapper(), taskMapper,
            new FakeDeptScopeMapper().addUserShop(301L, 202L).addUserShop(401L, 202L));

    service.createInstanceForSubmit(transfer());

    assertThat(taskMapper.tasks).extracting(InvTransferApprovalTask::getCandidateUserIds)
            .containsExactly("101,102", "201", "301", "401");
    assertThat(candidateMapper.directCalls).containsExactly("202:dz");
    assertThat(candidateMapper.lastExcludedPostCodes)
            .contains("dz", "dzzy", "sijifzr", "sanjifzr", "yyzj", "zjl");
    assertThat(candidateMapper.lastExcludedUserIds).containsExactlyInAnyOrder(301L, 401L);
}
```

- [ ] **Step 2: Add failing fallback and fixed-baseline tests**

Add these complete scenarios:

```java
@Test
@DisplayName("目标门店没有店长时店长助理代替四级且三级仍以店长排序为基准")
void shouldUseAssistantOnlyWhenManagerMissingAndKeepManagerSortAnchor()
{
    FakeCandidateMapper candidateMapper = new FakeCandidateMapper()
            .managerSort(5)
            .addDirect("dzzy", leader(110L, "assistant", 6))
            .addHigher(leader(210L, "sameAsManager", 5), leader(211L, "upper", 4));
    FakeTaskMapper taskMapper = new FakeTaskMapper();
    InvTransferApprovalServiceImpl service = dynamicOnlyService(candidateMapper, taskMapper, 999L);

    service.createInstanceForSubmit(transfer());

    assertThat(candidateMapper.directCalls).containsExactly("202:dz", "202:dzzy");
    assertThat(candidateMapper.lastManagerPostSort).isEqualTo(5);
    assertThat(taskMapper.tasks).extracting(InvTransferApprovalTask::getCandidateUserIds)
            .containsExactly("110", "211", "999");
}

@Test
@DisplayName("有店长时不再把店长助理加入四级")
void shouldNotMixAssistantWhenManagerExists()
{
    FakeCandidateMapper candidateMapper = new FakeCandidateMapper()
            .managerSort(5)
            .addDirect("dz", leader(101L, "manager", 5))
            .addDirect("dzzy", leader(110L, "assistant", 6))
            .addHigher(leader(201L, "upper", 4));
    FakeTaskMapper taskMapper = new FakeTaskMapper();

    dynamicOnlyService(candidateMapper, taskMapper, 999L).createInstanceForSubmit(transfer());

    assertThat(candidateMapper.directCalls).containsExactly("202:dz");
    assertThat(taskMapper.tasks.get(0).getCandidateUserIds()).isEqualTo("101");
}
```

Define the helper exactly once in the test class:

```java
private static InvTransferApprovalServiceImpl dynamicOnlyService(FakeCandidateMapper candidateMapper,
        FakeTaskMapper taskMapper, Long fixedUserId)
{
    candidateMapper.add(202L, "fixed", user(fixedUserId, "fixedApprover"));
    return newService(
            rule(node(1, "四级负责人（店长/店助）", "level4_highest", null),
                    node(2, "三级负责人（店长上一级）", "level3_highest", null),
                    node(3, "固定审批", "to_leader", "fixed")),
            candidateMapper, new FakeInstanceMapper(), taskMapper,
            new FakeDeptScopeMapper().addUserShop(fixedUserId, 202L));
}
```

- [ ] **Step 3: Run the service test and verify RED**

Run:

```bash
mvn -pl erp-modules/erp-inventory -am \
  -Dtest=InvTransferApprovalServiceImplTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: compilation or assertion failures because production and the fake mapper still use profile hierarchy methods.

- [ ] **Step 4: Implement the position-chain resolver in the existing service**

Replace the profile constants with these stable codes:

```java
private static final String STORE_MANAGER_POST_CODE = "dz";
private static final String STORE_ASSISTANT_POST_CODE = "dzzy";
private static final String TEMP_LEVEL4_POST_CODE = "sijifzr";
private static final String TEMP_LEVEL3_POST_CODE = "sanjifzr";
```

Implement dynamic resolution with fixed-node candidate exclusion:

```java
private DynamicLeaderResolution resolveDynamicLeaders(List<InvTransferApprovalNode> nodes,
        InvTransferOrder transfer)
{
    DynamicLeaderResolution resolution = dynamicNodeFlags(nodes);
    if ((!resolution.hasLevel4Node && !resolution.hasLevel3Node) || transfer.getToDeptId() == null)
    {
        resolution.targetStoreInvalid = transfer.getToDeptId() == null;
        return resolution;
    }
    if (candidateMapper.countActiveTargetStore(transfer.getToDeptId()) == 0)
    {
        resolution.targetStoreInvalid = true;
        return resolution;
    }

    Integer managerSort = candidateMapper.selectActivePostSortByCode(STORE_MANAGER_POST_CODE);
    List<CandidateUser> level4 = distinctCandidates(toCandidateUsers(
            candidateMapper.selectDirectStoreUsersByPostCode(transfer.getToDeptId(), STORE_MANAGER_POST_CODE)));
    if (level4.isEmpty())
    {
        level4 = distinctCandidates(toCandidateUsers(
                candidateMapper.selectDirectStoreUsersByPostCode(transfer.getToDeptId(), STORE_ASSISTANT_POST_CODE)));
    }
    resolution.level4Candidates = level4;

    Set<String> excludedPostCodes = new LinkedHashSet<>(List.of(
            STORE_MANAGER_POST_CODE, STORE_ASSISTANT_POST_CODE,
            TEMP_LEVEL4_POST_CODE, TEMP_LEVEL3_POST_CODE));
    Set<Long> excludedUserIds = new LinkedHashSet<>();
    for (InvTransferApprovalNode node : nodes)
    {
        if (node == null || isDynamicNodeRole(defaultString(node.getNodeRole(), NODE_ROLE_POST)))
        {
            continue;
        }
        if (!StringUtils.isEmpty(node.getPostCode()))
        {
            excludedPostCodes.add(StringUtils.trim(node.getPostCode()));
        }
        resolveConfiguredCandidates(transfer, node).forEach(candidate -> excludedUserIds.add(candidate.userId));
    }

    if (managerSort != null)
    {
        resolution.level3Candidates = closestHigherCandidates(toCandidateUsers(
                candidateMapper.selectCoveredHigherPostUsers(
                        transfer.getToDeptId(), managerSort,
                        new ArrayList<>(excludedPostCodes), new ArrayList<>(excludedUserIds))), managerSort);
    }
    Long submitterId = SecurityUtils.getUserId();
    resolution.submitterIsLevel4Leader = containsCandidate(resolution.level4Candidates, submitterId);
    resolution.submitterIsLevel3Leader = containsCandidate(resolution.level3Candidates, submitterId);
    return resolution;
}
```

Extract the configured-node half of `resolveCandidates` into `resolveConfiguredCandidates`, and make `resolveCandidates` dispatch dynamic roles before calling it. Use these exact helpers:

```java
private DynamicLeaderResolution dynamicNodeFlags(List<InvTransferApprovalNode> nodes)
{
    DynamicLeaderResolution resolution = new DynamicLeaderResolution();
    List<InvTransferApprovalNode> safeNodes = nodes == null ? new ArrayList<>() : nodes;
    resolution.hasLevel4Node = safeNodes.stream()
            .anyMatch(node -> node != null && NODE_ROLE_LEVEL4_HIGHEST.equals(node.getNodeRole()));
    resolution.hasLevel3Node = safeNodes.stream()
            .anyMatch(node -> node != null && NODE_ROLE_LEVEL3_HIGHEST.equals(node.getNodeRole()));
    return resolution;
}

private List<CandidateUser> resolveConfiguredCandidates(InvTransferOrder transfer,
        InvTransferApprovalNode node)
{
    String postCode = StringUtils.trim(node.getPostCode());
    if (StringUtils.isEmpty(postCode) || isSkippedStoreManagerPostNode(node, postCode))
    {
        return new ArrayList<>();
    }
    Long approvalScopeDeptId = resolveCandidateScopeDeptId(transfer, node);
    if (approvalScopeDeptId == null)
    {
        return new ArrayList<>();
    }
    for (Long deptId : resolveCandidateDeptIds(transfer, node))
    {
        List<CandidateUser> candidates = filterAuthorizedCandidates(
                toCandidateUsers(candidateMapper.selectUsersByDeptAndPostCode(deptId, postCode)),
                approvalScopeDeptId);
        if (!candidates.isEmpty())
        {
            return candidates;
        }
    }
    return new ArrayList<>();
}

private List<CandidateUser> closestHigherCandidates(List<CandidateUser> candidates, Integer managerSort)
{
    Integer closestSort = candidates.stream()
            .map(candidate -> candidate.postSort)
            .filter(java.util.Objects::nonNull)
            .filter(postSort -> managerSort != null && postSort < managerSort)
            .max(Integer::compareTo)
            .orElse(null);
    if (closestSort == null)
    {
        return new ArrayList<>();
    }
    return distinctCandidates(candidates.stream()
            .filter(candidate -> closestSort.equals(candidate.postSort))
            .collect(Collectors.toList()));
}

private List<CandidateUser> distinctCandidates(List<CandidateUser> candidates)
{
    Map<Long, CandidateUser> unique = new LinkedHashMap<>();
    for (CandidateUser candidate : candidates == null ? new ArrayList<CandidateUser>() : candidates)
    {
        if (candidate != null && candidate.userId != null)
        {
            unique.putIfAbsent(candidate.userId, candidate);
        }
    }
    return new ArrayList<>(unique.values());
}

private boolean containsCandidate(List<CandidateUser> candidates, Long userId)
{
    return userId != null && candidates != null
            && candidates.stream().anyMatch(candidate -> userId.equals(candidate.userId));
}
```

`resolveCandidates` returns `dynamicLeaders.level4Candidates` or `level3Candidates` for the two dynamic roles and otherwise returns `resolveConfiguredCandidates(transfer, node)`.

- [ ] **Step 5: Update the fake mapper to the new interface**

Use these fields and methods so the tests record the exact selection policy:

```java
private Integer activeManagerSort;
private int activeTargetStoreCount = 1;
private final Map<String, List<Map<String, Object>>> directUsers = new HashMap<>();
private List<Map<String, Object>> higherUsers = new ArrayList<>();
private final List<String> directCalls = new ArrayList<>();
private List<String> lastExcludedPostCodes = new ArrayList<>();
private List<Long> lastExcludedUserIds = new ArrayList<>();
private Integer lastManagerPostSort;

private FakeCandidateMapper invalidTargetStore()
{
    activeTargetStoreCount = 0;
    return this;
}

private FakeCandidateMapper managerSort(Integer postSort)
{
    activeManagerSort = postSort;
    return this;
}

@SafeVarargs
private final FakeCandidateMapper addDirect(String postCode, Map<String, Object>... users)
{
    directUsers.put(postCode, new ArrayList<>(List.of(users)));
    return this;
}

@SafeVarargs
private final FakeCandidateMapper addHigher(Map<String, Object>... users)
{
    higherUsers = new ArrayList<>(List.of(users));
    return this;
}

@Override
public Integer selectActivePostSortByCode(String postCode)
{
    return activeManagerSort;
}

@Override
public int countActiveTargetStore(Long targetDeptId)
{
    return activeTargetStoreCount;
}

@Override
public List<Map<String, Object>> selectDirectStoreUsersByPostCode(Long targetDeptId, String postCode)
{
    directCalls.add(targetDeptId + ":" + postCode);
    return directUsers.getOrDefault(postCode, Collections.emptyList());
}

@Override
public List<Map<String, Object>> selectCoveredHigherPostUsers(Long targetDeptId, Integer managerPostSort,
        List<String> excludedPostCodes, List<Long> excludedUserIds)
{
    lastManagerPostSort = managerPostSort;
    lastExcludedPostCodes = new ArrayList<>(excludedPostCodes);
    lastExcludedUserIds = new ArrayList<>(excludedUserIds);
    return higherUsers;
}
```

Delete `approvalProfiles`, `highestLeaders`, `addApprovalProfile`, and the old profile mapper methods from the fake.

- [ ] **Step 6: Run the service test and verify GREEN**

Run the command from Step 3. Expected: all service tests pass with zero failures.

- [ ] **Step 7: Commit Task 2**

```bash
git add erp-modules/erp-inventory/src/main/java/com/erp/inventory/service/impl/InvTransferApprovalServiceImpl.java \
        erp-modules/erp-inventory/src/test/java/com/erp/inventory/service/impl/InvTransferApprovalServiceImplTest.java
git commit -m "feat: build transfer approval from store position chain"
```

### Task 3: Lock submitter skip, warnings, and tied-candidate behavior

**Files:**
- Modify: `erp-modules/erp-inventory/src/test/java/com/erp/inventory/service/impl/InvTransferApprovalServiceImplTest.java:200-430`
- Modify: `erp-modules/erp-inventory/src/main/java/com/erp/inventory/service/impl/InvTransferApprovalServiceImpl.java:700-750`

- [ ] **Step 1: Add failing submitter-level tests**

Add one test for each confirmed branch:

```java
@Test
@DisplayName("无店长时作为四级候选的店长助理提交会跳过四级")
void shouldSkipLevel4WhenFallbackAssistantSubmits()
{
    SecurityContextHolder.setUserId("110");
    SecurityContextHolder.setUserName("assistant");
    FakeCandidateMapper mapper = new FakeCandidateMapper()
            .managerSort(5)
            .addDirect("dzzy", leader(110L, "assistant", 6))
            .addHigher(leader(210L, "upper", 4));
    FakeTaskMapper tasks = new FakeTaskMapper();

    dynamicOnlyService(mapper, tasks, 999L).createInstanceForSubmit(transfer());

    assertThat(tasks.tasks).extracting(InvTransferApprovalTask::getNodeOrder).containsExactly(2, 3);
}

@Test
@DisplayName("有店长时店长助理提交仍经过店长和上级")
void shouldTreatAssistantAsEmployeeWhenManagerExists()
{
    SecurityContextHolder.setUserId("110");
    SecurityContextHolder.setUserName("assistant");
    FakeCandidateMapper mapper = new FakeCandidateMapper()
            .managerSort(5)
            .addDirect("dz", leader(101L, "manager", 5))
            .addHigher(leader(210L, "upper", 4));
    FakeTaskMapper tasks = new FakeTaskMapper();

    dynamicOnlyService(mapper, tasks, 999L).createInstanceForSubmit(transfer());

    assertThat(tasks.tasks).extracting(InvTransferApprovalTask::getNodeOrder).containsExactly(1, 2, 3);
}

@Test
@DisplayName("三级候选提交会跳过四级和三级")
void shouldSkipBothDynamicNodesWhenUpperLeaderSubmits()
{
    SecurityContextHolder.setUserId("210");
    SecurityContextHolder.setUserName("upper");
    FakeCandidateMapper mapper = new FakeCandidateMapper()
            .managerSort(5)
            .addDirect("dz", leader(101L, "manager", 5))
            .addHigher(leader(210L, "upper", 4));
    FakeTaskMapper tasks = new FakeTaskMapper();

    InvTransferApprovalInstance instance = dynamicOnlyService(mapper, tasks, 999L)
            .createInstanceForSubmit(transfer());

    assertThat(instance.getCurrentNodeOrder()).isEqualTo(3);
    assertThat(tasks.tasks).extracting(InvTransferApprovalTask::getNodeOrder).containsExactly(3);
}

@Test
@DisplayName("目标门店无效时返回单独提醒并保留固定审批")
void shouldWarnSeparatelyWhenTargetStoreIsInvalid()
{
    SecurityContextHolder.setUserId("9");
    SecurityContextHolder.setUserName("employee");
    FakeCandidateMapper mapper = new FakeCandidateMapper().invalidTargetStore();
    FakeTaskMapper tasks = new FakeTaskMapper();

    InvTransferApprovalInstance instance = dynamicOnlyService(mapper, tasks, 999L)
            .createInstanceForSubmit(transfer());

    assertThat(tasks.tasks).extracting(InvTransferApprovalTask::getNodeOrder).containsExactly(3);
    assertThat(approvalWarnings(instance)).containsExactly(
            "调拨目标门店无效，无法匹配四级、三级负责人，本次审批直接进入已配置的固定审批");
}
```

- [ ] **Step 2: Update the three missing-layer tests to the new messages**

Assert exactly:

```java
assertThat(approvalWarnings(instance)).containsExactly(
        "未找到目标门店四级负责人，本次审批从三级负责人开始");

assertThat(approvalWarnings(instance)).containsExactly(
        "未找到目标门店三级负责人，本次审批跳过三级并进入已配置的固定审批");

assertThat(approvalWarnings(instance)).containsExactly(
        "未找到目标门店四级、三级负责人，本次审批直接进入已配置的固定审批");
```

- [ ] **Step 3: Add the tied-candidate progression regression test**

Create a dynamic level-4 task with candidate IDs `101,102`, approve as user `102`, then attempt the same task as `101`:

```java
assertThat(service.canApproveTask(task, 101L)).isTrue();
assertThat(service.canApproveTask(task, 102L)).isTrue();

SecurityContextHolder.setUserId("102");
SecurityContextHolder.setUserName("managerB");
service.approve(approvalRequest(task.getTaskId(), "approve", "同意"), 202L);

assertThat(task.getStatus()).isEqualTo("approved");
assertThat(instance.getCurrentNodeOrder()).isEqualTo(2);

SecurityContextHolder.setUserId("101");
SecurityContextHolder.setUserName("managerA");
assertThatThrownBy(() -> service.approve(
        approvalRequest(task.getTaskId(), "approve", "重复审批"), 202L))
        .isInstanceOf(ServiceException.class)
        .hasMessageContaining("不是待审批状态");
```

Use the existing fake instance, task, order, scope, and status-log mappers so this test exercises the real approval transition rather than only `canApproveTask`.

- [ ] **Step 4: Run the focused test and verify RED**

Run the Task 2 service-test command. Expected: the new messages or fallback-assistant skip assertions fail before production updates.

- [ ] **Step 5: Implement the exact warning text and preserve level membership skip**

Add `targetStoreInvalid` to `DynamicLeaderResolution`. In `buildDynamicLeaderWarnings`, return the dedicated invalid-target warning before calculating generic missing levels:

```java
if (resolution.targetStoreInvalid)
{
    return new ArrayList<>(List.of(
            "调拨目标门店无效，无法匹配四级、三级负责人，本次审批直接进入已配置的固定审批"));
}
```

Keep `shouldSkipDynamicNode` membership-based:

```java
private boolean shouldSkipDynamicNode(String nodeRole, DynamicLeaderResolution resolution)
{
    if (NODE_ROLE_LEVEL4_HIGHEST.equals(nodeRole))
    {
        return resolution.submitterIsLevel4Leader || resolution.submitterIsLevel3Leader;
    }
    return NODE_ROLE_LEVEL3_HIGHEST.equals(nodeRole) && resolution.submitterIsLevel3Leader;
}
```

Replace the warning strings with the three exact strings from Step 2. Do not remove the rule snapshot or change `any_one`; the existing one-task/CSV-candidates model is the approved same-level or-sign behavior.

- [ ] **Step 6: Run the focused test and verify GREEN**

Run the Task 2 service-test command. Expected: zero failures, including the new repeat-approval regression.

- [ ] **Step 7: Commit Task 3**

```bash
git add erp-modules/erp-inventory/src/main/java/com/erp/inventory/service/impl/InvTransferApprovalServiceImpl.java \
        erp-modules/erp-inventory/src/test/java/com/erp/inventory/service/impl/InvTransferApprovalServiceImplTest.java
git commit -m "test: lock transfer approver skip and fallback behavior"
```

### Task 4: Update protected-node labels, UI explanation, and migration copies

**Files:**
- Modify: `erp-modules/erp-inventory/src/test/java/com/erp/inventory/service/impl/InvTransferApprovalRuleServiceImplTest.java:75-130`
- Modify: `erp-modules/erp-inventory/src/main/java/com/erp/inventory/service/impl/InvTransferApprovalRuleServiceImpl.java:300-335`
- Modify: `erp-ui/test/transferApprovalRules.test.js:1-145`
- Modify: `erp-ui/src/views/inventory/transfer/rules.vue:190-260,420-440`
- Modify: `erp-ui/test/dockerScripts.test.js:20-50`
- Modify: `sql/erp_inventory_transfer_auto_hierarchy_approval_20260710.sql:1-125`
- Modify: `docker/mysql/db/erp_inventory_transfer_auto_hierarchy_approval_20260710.sql:1-125`

- [ ] **Step 1: Write failing backend and Node label assertions**

In the rule-service test, assert the dynamic names:

```java
assertThat(nodeMapper.lastInserted.subList(0, 2))
        .extracting(InvTransferApprovalNode::getNodeName)
        .containsExactly("四级负责人（店长/店助）", "三级负责人（店长上一级）");
```

In `transferApprovalRules.test.js`, require these user-facing strings:

```javascript
assert.ok(source.includes("四级负责人（店长/店助）"))
assert.ok(source.includes("三级负责人（店长上一级）"))
assert.ok(source.includes("店长优先，无店长时由店长助理兜底"))
assert.ok(source.includes("三级按高于店长且最接近的岗位排序匹配"))
```

In `dockerScripts.test.js`, require both migration copies to contain the two new names and `系统自动匹配目标门店岗位审批链`.

- [ ] **Step 2: Run focused backend and Node tests and verify RED**

Run:

```bash
mvn -pl erp-modules/erp-inventory -am \
  -Dtest=InvTransferApprovalRuleServiceImplTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
cd erp-ui && node test/transferApprovalRules.test.js && node test/dockerScripts.test.js
```

Expected: failures because production still says “四级门店最高领导/三级部门最高领导”.

- [ ] **Step 3: Rename backend protected nodes without changing their roles**

Use:

```java
normalized.add(dynamicNode(1, "四级负责人（店长/店助）", NODE_ROLE_LEVEL4_HIGHEST));
normalized.add(dynamicNode(2, "三级负责人（店长上一级）", NODE_ROLE_LEVEL3_HIGHEST));
```

Keep `level4_highest`, `level3_highest`, `any_one`, and `requiredCount = 1` unchanged for compatibility.

- [ ] **Step 4: Update the Vue explanation and dynamic labels**

Use this alert title:

```vue
title="四级按目标门店直接负责人匹配：店长优先，无店长时由店长助理兜底；三级按高于店长且最接近的岗位排序匹配"
```

Create protected nodes with the same new names as the backend. Replace the generic dynamic post-name text with:

```vue
<span v-if="scope.row.nodeRole === 'level4_highest'">店长优先，无店长时由店长助理兜底</span>
<span v-else-if="scope.row.nodeRole === 'level3_highest'">高于店长且最接近的岗位</span>
<el-input v-else v-model="scope.row.postName" size="mini" maxlength="64"/>
```

- [ ] **Step 5: Update both migration copies identically**

Change only dynamic node names, comments, and remarks:

```sql
when ranked.sequence_no = 1 then '四级负责人（店长/店助）'
when ranked.sequence_no = 2 then '三级负责人（店长上一级）'
```

For inserted rows use the same names and set:

```sql
'系统自动匹配目标门店岗位审批链'
```

Retain the existing MySQL 5.7 ranking, first-two-node conversion, post reference clearing, short-rule insertion, and trailing-node preservation.

- [ ] **Step 6: Run focused backend and Node tests and verify GREEN**

Run the Step 2 commands. Expected: all pass, and the root/Docker SQL files remain byte-for-byte equal.

- [ ] **Step 7: Commit Task 4**

```bash
git add erp-modules/erp-inventory/src/main/java/com/erp/inventory/service/impl/InvTransferApprovalRuleServiceImpl.java \
        erp-modules/erp-inventory/src/test/java/com/erp/inventory/service/impl/InvTransferApprovalRuleServiceImplTest.java \
        erp-ui/src/views/inventory/transfer/rules.vue \
        erp-ui/test/transferApprovalRules.test.js \
        erp-ui/test/dockerScripts.test.js \
        sql/erp_inventory_transfer_auto_hierarchy_approval_20260710.sql \
        docker/mysql/db/erp_inventory_transfer_auto_hierarchy_approval_20260710.sql
git commit -m "fix: describe target-store position approval nodes"
```

### Task 5: Add all-store preview and guarded temporary-post cleanup

**Files:**
- Create: `sql/erp_inventory_transfer_position_chain_preview_20260710.sql`
- Create: `sql/erp_inventory_transfer_temporary_post_cleanup_20260710.sql`
- Create: `erp-ui/test/transferApprovalDataScripts.test.js`

- [ ] **Step 1: Write the failing SQL safety contract test**

Create `erp-ui/test/transferApprovalDataScripts.test.js`:

```javascript
const assert = require("assert")
const fs = require("fs")
const path = require("path")

const root = path.resolve(__dirname, "../..")
const preview = fs.readFileSync(path.join(root,
  "sql/erp_inventory_transfer_position_chain_preview_20260710.sql"), "utf8")
const cleanup = fs.readFileSync(path.join(root,
  "sql/erp_inventory_transfer_temporary_post_cleanup_20260710.sql"), "utf8")

assert.ok(preview.includes("tmp_transfer_level4_candidates"))
assert.ok(preview.includes("tmp_transfer_level3_candidates"))
assert.ok(preview.includes("p.post_code = 'dz'"))
assert.ok(preview.includes("p.post_code = 'dzzy'"))
assert.ok(preview.includes("max(eligible.post_sort)"))
assert.ok(!/\b(update|delete|insert)\s+(sys_|inv_)/i.test(preview))

assert.ok(cleanup.includes("sijifzr"))
assert.ok(cleanup.includes("sanjifzr"))
assert.ok(cleanup.includes("signal sqlstate '45000'"))
assert.ok(cleanup.includes("inv_transfer_approval_node"))
assert.ok(cleanup.includes("inv_transfer_approval_task"))
assert.ok(cleanup.includes("delete up"))
assert.ok(cleanup.includes("delete p"))

console.log("transfer approval data scripts tests passed")
```

- [ ] **Step 2: Run the contract test and verify RED**

Run:

```bash
cd erp-ui && node test/transferApprovalDataScripts.test.js
```

Expected: FAIL with `ENOENT` because the preview and cleanup scripts do not exist.

- [ ] **Step 3: Create the read-only preview script**

The script may create session temporary tables but must not modify persistent business tables. It must:

```sql
set @preview_rule_id := (
    select r.rule_id
    from inv_transfer_approval_rule r
    where r.document_type = 'transfer' and r.status = '0'
    order by r.priority, r.rule_id
    limit 1
);

set @manager_sort := (
    select min(p.post_sort)
    from sys_post p
    where p.post_code = 'dz' and p.status = '0'
);
```

Then build these temporary tables:

1. `tmp_transfer_preview_stores`: all active, undeleted `STORE` departments.
2. `tmp_transfer_fixed_candidates`: users resolved from non-dynamic nodes of `@preview_rule_id` whose direct or ancestor responsibility scope covers each store.
3. `tmp_transfer_level4_candidates`: direct `dz` users; insert `dzzy` users only for stores with no `dz` row.
4. `tmp_transfer_level3_eligible`: scope-covered user/post rows with `post_sort < @manager_sort`, excluding `dz`, `dzzy`, `sijifzr`, `sanjifzr`, configured fixed post codes, and users in `tmp_transfer_fixed_candidates`.
5. `tmp_transfer_level3_candidates`: join eligible rows to this exact sort selector:

```sql
select eligible.store_id, max(eligible.post_sort) as post_sort
from tmp_transfer_level3_eligible eligible
group by eligible.store_id
```

The final `select` must return one row per store with these aliases:

```sql
store_id,
store_name,
level4_source,
level4_user_ids,
level4_user_names,
level3_post_sort,
level3_user_ids,
level3_user_names,
missing_warning
```

Use `group_concat(distinct ... order by ... separator ',')` for tied candidates. `missing_warning` must use the same three messages as the Java service.

- [ ] **Step 4: Create the guarded cleanup script**

Use a temporary procedure so MySQL 5.7 can abort before destructive statements:

```sql
drop procedure if exists cleanup_transfer_temporary_posts;
delimiter //
create procedure cleanup_transfer_temporary_posts()
begin
    if exists (
        select 1
        from inv_transfer_approval_node n
        join sys_post p on p.post_id = n.post_id or p.post_code = n.post_code
        where p.post_code in ('sijifzr', 'sanjifzr')
    ) then
        signal sqlstate '45000'
            set message_text = '临时岗位仍被调拨审批规则引用，禁止删除';
    end if;

    if exists (
        select 1
        from inv_transfer_approval_task t
        join sys_post p on p.post_id = t.post_id or p.post_code = t.post_code
        where p.post_code in ('sijifzr', 'sanjifzr')
    ) then
        signal sqlstate '45000'
            set message_text = '临时岗位仍被历史审批任务引用，禁止删除';
    end if;

    if exists (
        select 1
        from sys_user_post temporary_up
        join sys_post temporary_post on temporary_post.post_id = temporary_up.post_id
        where temporary_post.post_code in ('sijifzr', 'sanjifzr')
          and not exists (
              select 1
              from sys_user_post real_up
              join sys_post real_post on real_post.post_id = real_up.post_id
              where real_up.user_id = temporary_up.user_id
                and real_post.post_code not in ('sijifzr', 'sanjifzr')
          )
    ) then
        signal sqlstate '45000'
            set message_text = '存在仅配置临时岗位的用户，禁止删除';
    end if;

    start transaction;
    delete up
    from sys_user_post up
    join sys_post p on p.post_id = up.post_id
    where p.post_code in ('sijifzr', 'sanjifzr');

    delete p
    from sys_post p
    where p.post_code in ('sijifzr', 'sanjifzr');
    commit;
end//
delimiter ;

call cleanup_transfer_temporary_posts();
drop procedure cleanup_transfer_temporary_posts;
```

Prepend before-state queries and append after-state queries for `sys_post`, `sys_user_post`, `inv_transfer_approval_node`, and `inv_transfer_approval_task` so execution evidence is visible.

- [ ] **Step 5: Run the SQL contract test and the full Node suite**

Run:

```bash
cd erp-ui && node test/transferApprovalDataScripts.test.js && npm test
```

Expected: both the new script contract and all existing Node tests pass.

- [ ] **Step 6: Commit Task 5**

```bash
git add sql/erp_inventory_transfer_position_chain_preview_20260710.sql \
        sql/erp_inventory_transfer_temporary_post_cleanup_20260710.sql \
        erp-ui/test/transferApprovalDataScripts.test.js
git commit -m "test: add transfer approval data validation scripts"
```

### Task 6: Reconcile superseded docs, verify, migrate local data, and prepare integration

**Files:**
- Modify: `docs/superpowers/specs/2026-07-10-transfer-auto-hierarchy-leader-approval-design.md:1-15`
- Verify: `docs/superpowers/specs/2026-07-10-transfer-target-store-hierarchy-design.md`
- Verify: all files changed in Tasks 1-5

- [ ] **Step 1: Mark the original profile-based design as superseded**

Add this notice below its title:

```markdown
> 已由《调拨目标门店岗位链自动审批修订设计》取代。当前实现不再读取提交人的三级/四级档案文字字段，而是以调拨目标门店、店长/店长助理岗位、负责范围和岗位排序生成动态审批链。
```

Do not rewrite historical detail; the notice prevents two documents from appearing simultaneously authoritative.

- [ ] **Step 2: Run all focused backend tests**

Run:

```bash
mvn -pl erp-modules/erp-inventory -am \
  -Dtest=InvTransferApprovalServiceImplTest,InvTransferApprovalRuleServiceImplTest,InventoryMapperBindingTest,InvTransferServiceImplTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: zero test failures and zero test errors.

- [ ] **Step 3: Run the complete inventory module and frontend verification**

Run:

```bash
mvn -pl erp-modules/erp-inventory -am test
cd erp-ui && npm test && npm run build:prod
```

Expected: Maven exits 0; Node tests pass; the production frontend build exits 0.

- [ ] **Step 4: Back up the local tables before migration**

Run with the confirmed local database name:

```bash
mysqldump -uroot --single-transaction \
  --result-file=/tmp/BossERP_transfer_approval_before_position_chain_20260710.sql \
  BossERP_stock_state_75c59ee \
  inv_transfer_approval_rule inv_transfer_approval_node \
  inv_transfer_approval_instance inv_transfer_approval_task \
  sys_post sys_user_post sys_user_shop
```

Expected: exit 0 and a non-empty backup file at the stated path.

- [ ] **Step 5: Apply the rule migration and run the all-store preview**

Run:

```bash
mysql -uroot BossERP_stock_state_75c59ee \
  --execute="source /Users/liuxingyu/.config/superpowers/worktrees/ERP-NEW/transfer-target-store-hierarchy/sql/erp_inventory_transfer_auto_hierarchy_approval_20260710.sql"

mysql -uroot --table BossERP_stock_state_75c59ee \
  --execute="source /Users/liuxingyu/.config/superpowers/worktrees/ERP-NEW/transfer-target-store-hierarchy/sql/erp_inventory_transfer_position_chain_preview_20260710.sql"
```

Expected: every enabled store appears once; dynamic nodes 1 and 2 have null temporary-post references; trailing fixed nodes retain their original order and post/person configuration. Save the command output as verification evidence in the task report, not as a committed generated file.

- [ ] **Step 6: Exercise representative approval chains**

Use test fixtures or the running local application to submit these target-store cases and inspect `inv_transfer_approval_task` plus returned warnings:

1. Ordinary employee with manager and upper leader: task orders `1,2,3,4`.
2. Target-store manager: task orders `2,3,4`.
3. Store without manager but with assistant; assistant submits: task orders `2,3,4`.
4. Target-store level-3 candidate submits: task orders `3,4`.
5. Missing dynamic level: fixed tasks remain and the approved warning text is returned.
6. Tied same-level candidates: either candidate approves once; the second cannot approve the completed task.

For each case, verify candidate IDs belong to `to_dept_id`, never `from_dept_id` or the submitter's unrelated home store.

- [ ] **Step 7: Run the guarded temporary-post cleanup only after Step 6 passes**

Run:

```bash
mysql -uroot --table BossERP_stock_state_75c59ee \
  --execute="source /Users/liuxingyu/.config/superpowers/worktrees/ERP-NEW/transfer-target-store-hierarchy/sql/erp_inventory_transfer_temporary_post_cleanup_20260710.sql"
```

Expected: the script either removes both temporary posts and their user-post links, or aborts with one explicit guard message. A guard abort is not bypassed; resolve the remaining rule, historical-task, or user-without-real-post reference before retrying.

- [ ] **Step 8: Verify no temporary dependency remains**

Run:

```sql
select post_id, post_code, post_name
from sys_post
where post_code in ('sijifzr', 'sanjifzr');

select rule_id, node_order, node_role, post_id, post_code, post_name
from inv_transfer_approval_node
order by rule_id, node_order;
```

Expected: the temporary-post query returns zero rows after successful cleanup; dynamic nodes are first, have no selected post, and fixed nodes are unchanged after them.

- [ ] **Step 9: Commit documentation reconciliation**

```bash
git add docs/superpowers/specs/2026-07-10-transfer-auto-hierarchy-leader-approval-design.md
git commit -m "docs: mark profile-based transfer approval as superseded"
```

- [ ] **Step 10: Perform final diff and history review**

Run:

```bash
git status --short
git diff --check 0bd00800...HEAD
git log --oneline --decorate 0bd00800..HEAD
```

Expected: no unintended system-profile changes, no whitespace errors, and only the design, plan, inventory approval, UI, migration, validation, and superseded-doc commits are present. After code review and final verification, use the finishing-development-branch workflow to merge into `6月13号`.
