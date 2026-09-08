# Transfer Four-Level Approval Optimization Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make every new transfer resolve an ordered target-store approval chain of optional level 4, optional bounded level 3, mandatory operations director, and mandatory general manager, with submitter skip and immutable approval snapshots.

**Architecture:** Introduce stable system node roles and a focused `TransferApprovalCandidateResolver` that owns organization/post candidate selection. Keep approval state transitions in `InvTransferApprovalServiceImpl`, normalize every saved rule to the four-node template, expose a target-store candidate preview, and migrate current rule nodes without rewriting historical instances. Candidate SQL enforces account, employee, post, responsibility-scope, and `inv:transfer:approve` permission constraints.

**Tech Stack:** Java 17, Spring Boot, MyBatis XML, JUnit 5, AssertJ, Maven, Vue 2, Element UI, Node source-contract tests, MySQL 5.7-compatible SQL.

---

## File responsibility map

- `erp-modules/erp-inventory/src/main/java/com/erp/inventory/constant/InvTransferApprovalNodeRoles.java`: stable four-node role and post-code constants.
- `erp-modules/erp-inventory/src/main/java/com/erp/inventory/service/impl/InvTransferApprovalRuleServiceImpl.java`: normalize every saved rule to the locked four-node template and force sequential/no-self defaults.
- `erp-modules/erp-inventory/src/main/java/com/erp/inventory/mapper/InvTransferApprovalCandidateMapper.java`: declare direct level-4, bounded level-3, and fixed high-level candidate facts.
- `erp-modules/erp-inventory/src/main/resources/mapper/inventory/InvTransferApprovalCandidateMapper.xml`: enforce target-store scope, post boundaries, active employee/post state, and approval permission.
- `erp-modules/erp-inventory/src/main/java/com/erp/inventory/service/impl/TransferApprovalCandidateResolver.java`: resolve all four candidate pools and dynamic warnings without changing workflow state.
- `erp-modules/erp-inventory/src/main/java/com/erp/inventory/service/impl/InvTransferApprovalServiceImpl.java`: apply submitter skip, block missing mandatory nodes, create snapshots/tasks, and expose preview data.
- `erp-modules/erp-inventory/src/main/java/com/erp/inventory/domain/vo/InvTransferApprovalPreview.java`: safe rule-preview response for one target store.
- `erp-modules/erp-inventory/src/main/java/com/erp/inventory/service/IInvTransferApprovalService.java`: candidate-preview service contract.
- `erp-modules/erp-inventory/src/main/java/com/erp/inventory/controller/InvTransferApprovalRuleController.java`: scoped preview endpoint.
- `erp-ui/src/api/inventory/transferApprovalRule.js`: preview API client.
- `erp-ui/src/views/inventory/transfer/rules.vue`: locked four-node display and target-store candidate preview.
- `sql/erp_inventory_transfer_four_level_approval_20260713.sql` and Docker mirror: idempotently normalize current rule nodes after mandatory-candidate preflight.
- Existing Java and Node tests: lock rule normalization, mapper boundaries, candidate resolution, submitter skip, preview, UI, and migration behavior.

### Task 1: Lock rule configuration to the four system nodes

**Files:**
- Create: `erp-modules/erp-inventory/src/main/java/com/erp/inventory/constant/InvTransferApprovalNodeRoles.java`
- Modify: `erp-modules/erp-inventory/src/main/java/com/erp/inventory/service/impl/InvTransferApprovalRuleServiceImpl.java`
- Modify: `erp-modules/erp-inventory/src/test/java/com/erp/inventory/service/impl/InvTransferApprovalRuleServiceImplTest.java`

- [x] **Step 1: Write failing rule-template tests**

Replace the two-node normalization assertions with the exact four-node contract:

```java
@Test
@DisplayName("保存规则时固定为四级三级运营总监总经理")
void shouldNormalizeRuleToFourSystemNodes()
{
    SecurityContextHolder.setUserId("9");
    SecurityContextHolder.setUserName("operator");
    InvTransferApprovalRuleServiceImpl service = newService(
            new FakeDeptScopeMapper().addUserShop(9L, 100L));
    InvTransferApprovalRule rule = scopedRule(null, "dept", 100L, 10);
    rule.setApprovalMode("quorum");
    rule.setAllowSelfApprove("1");
    rule.setNodes(List.of(node()));

    service.saveRule(rule, 100L);

    FakeNodeMapper mapper = (FakeNodeMapper) ReflectionTestUtils.getField(service, "nodeMapper");
    assertThat(mapper.lastInserted).extracting(InvTransferApprovalNode::getNodeRole)
            .containsExactly("level4_highest", "level3_highest",
                    "operations_director", "general_manager");
    assertThat(mapper.lastInserted).extracting(InvTransferApprovalNode::getNodeOrder)
            .containsExactly(1, 2, 3, 4);
    assertThat(mapper.lastInserted).extracting(InvTransferApprovalNode::getPostCode)
            .containsExactly(null, null, "yyzj", "zjl");
    assertThat(mapper.lastInserted).extracting(InvTransferApprovalNode::getApprovalMode)
            .containsOnly("any_one");
    assertThat(rule.getApprovalMode()).isEqualTo("all_nodes");
    assertThat(rule.getAllowSelfApprove()).isEqualTo("0");
}
```

- [x] **Step 2: Run the rule tests and verify RED**

```bash
mvn -f erp-modules/pom.xml -pl erp-inventory \
  -Dtest=InvTransferApprovalRuleServiceImplTest test
```

Expected: FAIL because the service currently prepends only two dynamic nodes and preserves editable trailing nodes/modes.

- [x] **Step 3: Add stable role constants**

```java
package com.erp.inventory.constant;

import java.util.Set;

public final class InvTransferApprovalNodeRoles
{
    public static final String LEVEL4_HIGHEST = "level4_highest";
    public static final String LEVEL3_HIGHEST = "level3_highest";
    public static final String OPERATIONS_DIRECTOR = "operations_director";
    public static final String GENERAL_MANAGER = "general_manager";

    public static final String STORE_MANAGER_POST = "dz";
    public static final String STORE_ASSISTANT_POST = "dzzy";
    public static final String OPERATIONS_DIRECTOR_POST = "yyzj";
    public static final String GENERAL_MANAGER_POST = "zjl";

    private static final Set<String> SYSTEM_ROLES = Set.of(
            LEVEL4_HIGHEST, LEVEL3_HIGHEST, OPERATIONS_DIRECTOR, GENERAL_MANAGER);

    private InvTransferApprovalNodeRoles() {}

    public static boolean isSystemRole(String role)
    {
        return SYSTEM_ROLES.contains(role);
    }
}
```

- [x] **Step 4: Normalize rule nodes and modes**

Replace `ensureSystemDynamicNodes` with a method that always returns exactly these nodes:

```java
private List<InvTransferApprovalNode> ensureSystemApprovalNodes()
{
    return new ArrayList<>(List.of(
            systemNode(1, "四级负责人（店长/店助）", LEVEL4_HIGHEST, null, null),
            systemNode(2, "三级负责人（店长与高层之间）", LEVEL3_HIGHEST, null, null),
            systemNode(3, "运营总监", OPERATIONS_DIRECTOR,
                    OPERATIONS_DIRECTOR_POST, "运营总监"),
            systemNode(4, "总经理", GENERAL_MANAGER,
                    GENERAL_MANAGER_POST, "总经理")));
}

private InvTransferApprovalNode systemNode(Integer order, String name, String role,
        String postCode, String postName)
{
    InvTransferApprovalNode node = new InvTransferApprovalNode();
    node.setNodeOrder(order);
    node.setNodeName(name);
    node.setNodeRole(role);
    node.setPostCode(postCode);
    node.setPostName(postName);
    node.setApprovalMode(APPROVAL_ANY_ONE);
    node.setRequiredCount(1);
    return node;
}
```

At the beginning of `saveRule`, call `applyRuleDefaults` and then assign these system nodes before validation. This ensures a caller-supplied obsolete `quorum` mode cannot fail validation before the service replaces it. In `saveNodes`, ignore caller-supplied node content and assign the same template. In `applyRuleDefaults` force:

```java
rule.setApprovalMode(APPROVAL_ALL_NODES);
rule.setRequiredCount(0);
rule.setAllowSelfApprove("0");
```

- [x] **Step 5: Run the rule tests and verify GREEN**

Run the Step 2 command. Expected: all rule-service tests pass.

- [x] **Step 6: Commit Task 1**

```bash
git add erp-modules/erp-inventory/src/main/java/com/erp/inventory/constant/InvTransferApprovalNodeRoles.java \
        erp-modules/erp-inventory/src/main/java/com/erp/inventory/service/impl/InvTransferApprovalRuleServiceImpl.java \
        erp-modules/erp-inventory/src/test/java/com/erp/inventory/service/impl/InvTransferApprovalRuleServiceImplTest.java
git commit -m "feat: lock transfer rules to four approval levels"
```

### Task 2: Enforce bounded hierarchy and approval permission in candidate SQL

**Files:**
- Modify: `erp-modules/erp-inventory/src/main/java/com/erp/inventory/mapper/InvTransferApprovalCandidateMapper.java`
- Modify: `erp-modules/erp-inventory/src/main/resources/mapper/inventory/InvTransferApprovalCandidateMapper.xml`
- Modify: `erp-modules/erp-inventory/src/test/java/com/erp/inventory/mapper/InventoryMapperBindingTest.java`

- [x] **Step 1: Write the failing mapper contract test**

```java
@Test
@DisplayName("调拨候选人受三级边界和审批权限约束")
void shouldBindBoundedAndAuthorizedTransferApproverQueries() throws Exception
{
    Configuration configuration = new Configuration();
    registerAliases(configuration);
    parseMapper(configuration, "mapper/inventory/InvTransferApprovalCandidateMapper.xml");
    String xml = resourceText("mapper/inventory/InvTransferApprovalCandidateMapper.xml");

    assertMapped(configuration, InvTransferApprovalCandidateMapper.class,
            "selectCoveredHigherPostUsers");
    assertMapped(configuration, InvTransferApprovalCandidateMapper.class,
            "selectCoveredUsersByPostCode");
    assertThat(xml).contains(
            "p.post_sort &gt; #{executiveBoundarySort}",
            "p.post_sort &lt; #{managerPostSort}",
            "m.perms = 'inv:transfer:approve'",
            "p.status = '0'",
            "profile.employee_status",
            "target_dept.dept_id = #{targetDeptId}");
}
```

- [x] **Step 2: Run the mapper test and verify RED**

```bash
mvn -f erp-modules/pom.xml -pl erp-inventory \
  -Dtest=InventoryMapperBindingTest test
```

Expected: FAIL because `executiveBoundarySort`, `selectCoveredUsersByPostCode`, and permission filtering do not exist.

- [x] **Step 3: Change mapper signatures**

```java
List<Map<String, Object>> selectCoveredHigherPostUsers(
        @Param("targetDeptId") Long targetDeptId,
        @Param("managerPostSort") Integer managerPostSort,
        @Param("executiveBoundarySort") Integer executiveBoundarySort,
        @Param("excludedPostCodes") List<String> excludedPostCodes);

List<Map<String, Object>> selectCoveredUsersByPostCode(
        @Param("targetDeptId") Long targetDeptId,
        @Param("postCode") String postCode);
```

Remove `selectUsersByDeptAndPostCode` after Task 3 moves every caller.

- [x] **Step 4: Add permission filtering to all candidate queries**

Add this correlated predicate to direct, bounded, and fixed candidate queries:

```xml
and exists (
    select 1
    from sys_user_role ur
    join sys_role r on r.role_id = ur.role_id
    join sys_role_menu rm on rm.role_id = r.role_id
    join sys_menu m on m.menu_id = rm.menu_id
    where ur.user_id = u.user_id
      and r.del_flag = '0'
      and r.status = '0'
      and m.status = '0'
      and m.perms = 'inv:transfer:approve'
)
```

- [x] **Step 5: Implement bounded and fixed queries**

The level-3 query must include:

```xml
and p.post_sort &gt; #{executiveBoundarySort}
and p.post_sort &lt; #{managerPostSort}
order by p.post_sort desc, u.user_id
```

The fixed-post query must use `targetDeptId` directly, active user/post/employee filters, inherited `sys_user_shop` scope, and the permission predicate:

```xml
<select id="selectCoveredUsersByPostCode" resultType="map">
    select distinct u.user_id as userId,
           coalesce(nullif(u.nick_name, ''), u.user_name) as userName,
           p.post_sort as postSort
    from sys_user u
    join sys_user_post up on up.user_id = u.user_id
    join sys_post p on p.post_id = up.post_id
    join sys_user_shop us on us.user_id = u.user_id
    join sys_dept scope_dept on scope_dept.dept_id = us.dept_id
    join sys_dept target_dept on target_dept.dept_id = #{targetDeptId}
    left join sys_user_profile profile on profile.user_id = u.user_id
    where u.del_flag = '0' and u.status = '0'
      and p.status = '0' and p.post_code = #{postCode}
      and scope_dept.del_flag = '0' and scope_dept.status = '0'
      and target_dept.del_flag = '0' and target_dept.status = '0'
      and target_dept.dept_type = 'STORE'
      and (target_dept.dept_id = scope_dept.dept_id
           or find_in_set(scope_dept.dept_id, target_dept.ancestors))
      and (profile.employee_status is null
           or trim(profile.employee_status) = ''
           or trim(profile.employee_status) &lt;&gt; '离职')
      and exists (
          select 1
          from sys_user_role ur
          join sys_role r on r.role_id = ur.role_id
          join sys_role_menu rm on rm.role_id = r.role_id
          join sys_menu m on m.menu_id = rm.menu_id
          where ur.user_id = u.user_id
            and r.del_flag = '0' and r.status = '0'
            and m.status = '0' and m.perms = 'inv:transfer:approve'
      )
    order by u.user_id
</select>
```

- [x] **Step 6: Run mapper tests and verify GREEN**

Run the Step 2 command. Expected: all mapper-binding tests pass.

- [x] **Step 7: Commit Task 2**

```bash
git add erp-modules/erp-inventory/src/main/java/com/erp/inventory/mapper/InvTransferApprovalCandidateMapper.java \
        erp-modules/erp-inventory/src/main/resources/mapper/inventory/InvTransferApprovalCandidateMapper.xml \
        erp-modules/erp-inventory/src/test/java/com/erp/inventory/mapper/InventoryMapperBindingTest.java
git commit -m "feat: bound and authorize transfer approver queries"
```

### Task 3: Resolve the four candidate pools and apply submitter skip

**Files:**
- Create: `erp-modules/erp-inventory/src/main/java/com/erp/inventory/service/impl/TransferApprovalCandidateResolver.java`
- Create: `erp-modules/erp-inventory/src/test/java/com/erp/inventory/service/impl/TransferApprovalCandidateResolverTest.java`
- Modify: `erp-modules/erp-inventory/src/main/java/com/erp/inventory/service/impl/InvTransferApprovalServiceImpl.java`
- Modify: `erp-modules/erp-inventory/src/test/java/com/erp/inventory/service/impl/InvTransferApprovalServiceImplTest.java`

- [x] **Step 1: Write failing resolver tests**

```java
@Test
@DisplayName("三级只取高层与店长之间最接近店长的一档")
void shouldResolveClosestLevelInsideExecutiveAndManagerBounds()
{
    FakeCandidateMapper mapper = new FakeCandidateMapper()
            .postSort("dz", 5).postSort("yyzj", 1).postSort("zjl", 1)
            .direct("dz", candidate(10L, "storeManager", 5))
            .higher(candidate(20L, "closest", 4), candidate(21L, "middle", 3))
            .covered("yyzj", candidate(30L, "operationsDirector", 1))
            .covered("zjl", candidate(40L, "generalManager", 1));

    Resolution result = resolver(mapper).resolve(transfer(), fourNodeRule());

    assertThat(mapper.lastExecutiveBoundarySort).isEqualTo(1);
    assertThat(mapper.lastManagerSort).isEqualTo(5);
    assertThat(result.node("level3_highest").candidateUserIds()).containsExactly(20L);
}

@Test
@DisplayName("四级三级缺失时仍保留运营总监总经理")
void shouldKeepMandatoryExecutivesWhenDynamicLevelsMissing()
{
    FakeCandidateMapper mapper = new FakeCandidateMapper()
            .postSort("dz", 5).postSort("yyzj", 1).postSort("zjl", 1)
            .covered("yyzj", candidate(30L, "operationsDirector", 1))
            .covered("zjl", candidate(40L, "generalManager", 1));

    Resolution result = resolver(mapper).resolve(transfer(), fourNodeRule());

    assertThat(result.activeRoles())
            .containsExactly("operations_director", "general_manager");
    assertThat(result.warnings()).containsExactly(
            "未找到目标门店四级、三级负责人，本次审批直接进入运营总监");
}
```

- [x] **Step 2: Run resolver tests and verify RED**

```bash
mvn -f erp-modules/pom.xml -pl erp-inventory \
  -Dtest=TransferApprovalCandidateResolverTest test
```

Expected: FAIL because the resolver class does not exist.

- [x] **Step 3: Implement the focused resolver**

Implement one public method and immutable nested result types:

```java
@Component
public class TransferApprovalCandidateResolver
{
    @Autowired
    private InvTransferApprovalCandidateMapper candidateMapper;

    public Resolution resolve(InvTransferOrder transfer, InvTransferApprovalRule rule)
    {
        if (transfer == null || transfer.getToDeptId() == null
                || candidateMapper.countActiveTargetStore(transfer.getToDeptId()) == 0)
        {
            throw new ServiceException("调拨目标门店无效");
        }
        Integer managerSort = requirePostSort(STORE_MANAGER_POST);
        Integer operationsSort = requirePostSort(OPERATIONS_DIRECTOR_POST);
        Integer generalManagerSort = requirePostSort(GENERAL_MANAGER_POST);
        int executiveBoundary = Math.max(operationsSort, generalManagerSort);

        List<Candidate> level4 = candidates(
                candidateMapper.selectDirectStoreUsersByPostCode(
                        transfer.getToDeptId(), STORE_MANAGER_POST));
        if (level4.isEmpty())
        {
            level4 = candidates(candidateMapper.selectDirectStoreUsersByPostCode(
                    transfer.getToDeptId(), STORE_ASSISTANT_POST));
        }
        List<Candidate> level3 = closestSortBand(candidates(
                candidateMapper.selectCoveredHigherPostUsers(
                        transfer.getToDeptId(), managerSort, executiveBoundary,
                        List.of(STORE_MANAGER_POST, STORE_ASSISTANT_POST,
                                OPERATIONS_DIRECTOR_POST, GENERAL_MANAGER_POST,
                                "sijifzr", "sanjifzr"))));
        List<Candidate> operations = candidates(
                candidateMapper.selectCoveredUsersByPostCode(
                        transfer.getToDeptId(), OPERATIONS_DIRECTOR_POST));
        List<Candidate> generalManagers = candidates(
                candidateMapper.selectCoveredUsersByPostCode(
                        transfer.getToDeptId(), GENERAL_MANAGER_POST));

        return Resolution.of(rule, managerSort, executiveBoundary,
                level4, level3, operations, generalManagers);
    }
}
```

Use these exact nested result APIs so the approval service and preview mapper share one immutable resolution without reaching back into the database:

```java
public record Candidate(Long userId, String displayName, Integer postSort) {}

public record ResolvedNode(InvTransferApprovalNode node,
        List<Candidate> candidates, boolean required, Integer resolvedPostSort)
{
    public ResolvedNode
    {
        candidates = candidates == null ? List.of() : List.copyOf(candidates);
    }
}

public record Resolution(List<ResolvedNode> nodes, List<String> warnings,
        List<String> missingMandatoryRoles)
{
    public Resolution
    {
        nodes = nodes == null ? List.of() : List.copyOf(nodes);
        warnings = warnings == null ? List.of() : List.copyOf(warnings);
        missingMandatoryRoles = missingMandatoryRoles == null
                ? List.of() : List.copyOf(missingMandatoryRoles);
    }

    public ResolvedNode node(String role)
    {
        return nodes.stream()
                .filter(item -> item.node() != null
                        && java.util.Objects.equals(role, item.node().getNodeRole()))
                .findFirst()
                .orElse(null);
    }

    public List<String> activeRoles()
    {
        return nodes.stream()
                .filter(item -> !item.candidates().isEmpty())
                .map(item -> item.node().getNodeRole())
                .toList();
    }
}
```

The constructor must use `List.copyOf`, `node(String)` must compare the node's stable role, and `activeRoles()` must preserve node order. `closestSortBand` chooses the maximum non-null `postSort` and retains every distinct user in that band. No test-only resolution factory is added to production code.

- [x] **Step 4: Add failing submitter-skip and mandatory-node tests**

```java
@Test
@DisplayName("三级负责人发起时从运营总监开始")
void shouldSkipThroughLevel3WhenLevel3CandidateSubmits()
{
    SecurityContextHolder.setUserId("20");
    SecurityContextHolder.setUserName("level3Leader");
    FakeCandidateMapper candidates = standardFourLevelCandidates()
            .higher(candidate(20L, "level3Leader", 4));
    FakeTaskMapper tasks = new FakeTaskMapper();

    fourLevelService(candidates, tasks).createInstanceForSubmit(transfer());

    assertThat(tasks.tasks).extracting(InvTransferApprovalTask::getNodeOrder)
            .containsExactly(3, 4);
}

@Test
@DisplayName("运营总监缺失时阻止提交")
void shouldBlockSubmitWhenOperationsDirectorMissing()
{
    FakeCandidateMapper candidates = standardFourLevelCandidates()
            .covered("yyzj");

    assertThatThrownBy(() -> fourLevelService(
            candidates, new FakeTaskMapper()).createInstanceForSubmit(transfer()))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("目标门店未配置有效运营总监");
}

@Test
@DisplayName("总经理发起调拨时阻止提交")
void shouldBlockSubmitWhenGeneralManagerIsSubmitter()
{
    SecurityContextHolder.setUserId("40");
    SecurityContextHolder.setUserName("generalManager");

    assertThatThrownBy(() -> fourLevelService(
            standardFourLevelCandidates(), new FakeTaskMapper())
            .createInstanceForSubmit(transfer()))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("总经理不能发起");
}
```

In the test class, `standardFourLevelCandidates()` must populate `dz=5`, `yyzj=1`, `zjl=1`, a manager at user 10, level 3 at user 20/post sort 4, operations director at user 30, and general manager at user 40. `fourLevelService(mapper, tasks)` must call the existing `newService` helper with an exact four-node rule and inject a real `TransferApprovalCandidateResolver` whose mapper field is the supplied fake. Keep these helpers private to the test class.

- [x] **Step 5: Integrate resolver into approval execution**

Inject the resolver and replace candidate construction in `createInstanceForSubmit`:

```java
Resolution resolution = candidateResolver.resolve(transfer, rule);
validateMandatoryNodes(resolution, transfer);
int skipThroughOrder = highestSubmitterNodeOrder(
        resolution, SecurityUtils.getUserId());
if (skipThroughOrder >= 4)
{
    throw new ServiceException("总经理不能发起需本人最终审批的调拨单");
}
List<ResolvedNode> activeNodes = resolution.nodes().stream()
        .filter(node -> node.node().getNodeOrder() > skipThroughOrder)
        .filter(node -> !node.candidates().isEmpty())
        .collect(Collectors.toList());
```

Use `activeNodes` to build the snapshot and task rows. Preserve the existing state machine, track rendering, and candidate-ID snapshots. Remove old candidate-resolution helpers only after all tests compile.

- [x] **Step 6: Run resolver and approval tests and verify GREEN**

```bash
mvn -f erp-modules/pom.xml -pl erp-inventory \
  -Dtest=TransferApprovalCandidateResolverTest,InvTransferApprovalServiceImplTest test
```

Expected: both classes pass with zero failures.

- [x] **Step 7: Commit Task 3**

```bash
git add erp-modules/erp-inventory/src/main/java/com/erp/inventory/service/impl/TransferApprovalCandidateResolver.java \
        erp-modules/erp-inventory/src/main/java/com/erp/inventory/service/impl/InvTransferApprovalServiceImpl.java \
        erp-modules/erp-inventory/src/test/java/com/erp/inventory/service/impl/TransferApprovalCandidateResolverTest.java \
        erp-modules/erp-inventory/src/test/java/com/erp/inventory/service/impl/InvTransferApprovalServiceImplTest.java
git commit -m "feat: resolve and enforce transfer approval chain"
```

### Task 4: Add scoped target-store candidate preview

**Files:**
- Create: `erp-modules/erp-inventory/src/main/java/com/erp/inventory/domain/vo/InvTransferApprovalPreview.java`
- Modify: `erp-modules/erp-inventory/src/main/java/com/erp/inventory/service/IInvTransferApprovalService.java`
- Modify: `erp-modules/erp-inventory/src/main/java/com/erp/inventory/service/impl/InvTransferApprovalServiceImpl.java`
- Modify: `erp-modules/erp-inventory/src/main/java/com/erp/inventory/controller/InvTransferApprovalRuleController.java`
- Create: `erp-modules/erp-inventory/src/test/java/com/erp/inventory/controller/InvTransferApprovalRuleControllerSourceTest.java`
- Modify: `erp-modules/erp-inventory/src/test/java/com/erp/inventory/service/impl/InvTransferApprovalServiceImplTest.java`

- [x] **Step 1: Write failing preview tests**

```java
@Test
@DisplayName("规则候选预览返回四节点和固定节点阻断原因")
void shouldPreviewFourApprovalNodesForTargetStore()
{
    InvTransferApprovalPreview preview = fourLevelService(
            standardFourLevelCandidates(), new FakeTaskMapper())
            .previewCandidates(6L, 202L, 202L);

    assertThat(preview.getNodes())
            .extracting(InvTransferApprovalPreview.Node::getNodeRole)
            .containsExactly("level4_highest", "level3_highest",
                    "operations_director", "general_manager");
    assertThat(preview.isBlocked()).isFalse();
}
```

The controller source test must reflect `candidatePreview(Long, Long, HttpServletRequest)` and assert `/rule/{ruleId}/candidate-preview` plus `inv:transfer:rule:query`.

- [x] **Step 2: Run preview tests and verify RED**

```bash
mvn -f erp-modules/pom.xml -pl erp-inventory \
  -Dtest=InvTransferApprovalServiceImplTest,InvTransferApprovalRuleControllerSourceTest test
```

Expected: compilation failures because preview types and endpoint do not exist.

- [x] **Step 3: Add the safe preview VO**

Create a mutable JavaBean named `InvTransferApprovalPreview` with these exact outer fields: `Long ruleId`, `String ruleName`, `Long targetDeptId`, `String targetDeptName`, `boolean blocked`, `String blockedReason`, initialized `List<String> warnings`, and initialized `List<Node> nodes`. Add explicit `getX`/`setX` methods for every outer field; use `isBlocked()` for the boolean getter.

Create a public static mutable JavaBean `Node` inside it with these exact fields: `Integer nodeOrder`, `String nodeName`, `String nodeRole`, `Integer resolvedPostSort`, `boolean required`, `String state`, `Integer candidateCount`, and initialized `List<String> candidateDisplayNames`. Add explicit `getX`/`setX` methods for every node field; use `isRequired()` for the boolean getter. Do not use Lombok, and do not expose user IDs, login names, phones, roles, or shop assignments.

- [x] **Step 4: Implement scoped preview**

Add to the interface:

```java
InvTransferApprovalPreview previewCandidates(
        Long ruleId, Long targetDeptId, Long selectedShopDeptId);
```

The service must load the scoped rule, validate `targetDeptId` against `selectedShopDeptId`, call the resolver without self-skip, and map missing fixed roles to `blocked=true` with a specific reason.

Add:

```java
@RequiresPermissions("inv:transfer:rule:query")
@PostMapping("/rule/{ruleId}/candidate-preview")
public AjaxResult candidatePreview(@PathVariable Long ruleId, Long targetDeptId,
        HttpServletRequest request)
{
    return success(approvalService.previewCandidates(
            ruleId, targetDeptId, resolveShopDeptId(request)));
}
```

- [x] **Step 5: Run preview tests and verify GREEN**

Run the Step 2 command. Expected: both classes pass.

- [x] **Step 6: Commit Task 4**

```bash
git add erp-modules/erp-inventory/src/main/java/com/erp/inventory/domain/vo/InvTransferApprovalPreview.java \
        erp-modules/erp-inventory/src/main/java/com/erp/inventory/service/IInvTransferApprovalService.java \
        erp-modules/erp-inventory/src/main/java/com/erp/inventory/service/impl/InvTransferApprovalServiceImpl.java \
        erp-modules/erp-inventory/src/main/java/com/erp/inventory/controller/InvTransferApprovalRuleController.java \
        erp-modules/erp-inventory/src/test/java/com/erp/inventory/controller/InvTransferApprovalRuleControllerSourceTest.java \
        erp-modules/erp-inventory/src/test/java/com/erp/inventory/service/impl/InvTransferApprovalServiceImplTest.java
git commit -m "feat: preview transfer approval candidates by store"
```

### Task 5: Lock the rule page and show candidate preview

**Files:**
- Modify: `erp-ui/src/api/inventory/transferApprovalRule.js`
- Modify: `erp-ui/src/views/inventory/transfer/rules.vue`
- Modify: `erp-ui/test/transferApprovalRules.test.js`

- [x] **Step 1: Replace frontend tests with the four-node contract**

```javascript
assert.deepStrictEqual(
  vm.form.nodes.map(node => node.nodeRole),
  ["level4_highest", "level3_highest", "operations_director", "general_manager"]
)
assert.ok(!source.includes("添加审批岗位"))
assert.ok(!source.includes("moveNode("))
assert.ok(source.includes("候选人预览"))
assert.ok(source.includes("高于店长、低于运营总监和总经理"))
```

- [x] **Step 2: Run the rule test and verify RED**

```bash
node erp-ui/scripts/run-node-tests.cjs transferApprovalRules.test.js
```

Expected: FAIL because trailing nodes remain editable and preview is absent.

- [x] **Step 3: Add the API method**

```javascript
export function previewTransferApprovalCandidates(ruleId, targetDeptId) {
  return request({
    url: '/inventory/transfer/rule/' + ruleId + '/candidate-preview',
    method: 'post',
    params: { targetDeptId }
  })
}
```

- [x] **Step 4: Lock the four-node form**

Make `emptyForm()` and `ensureSystemNodes()` return exactly four system nodes. Remove add/delete/move buttons, editable node modes, quorum counts, and the self-approval switch. Force:

```javascript
approvalMode: "all_nodes",
requiredCount: 0,
allowSelfApprove: "0",
nodes: this.ensureSystemNodes()
```

Render descriptions:

```javascript
nodeDescription(node) {
  return {
    level4_highest: "目标门店店长优先，无店长时由店长助理兜底",
    level3_highest: "岗位高于店长、低于运营总监和总经理，取最接近店长的一档",
    operations_director: "按目标门店负责范围匹配运营总监，固定必审",
    general_manager: "按目标门店负责范围匹配总经理，最终必审"
  }[node.nodeRole] || "-"
}
```

- [x] **Step 5: Add the target-store preview dialog**

Require a target store, call `previewTransferApprovalCandidates`, and render node state, resolved sort, safe candidate names/count, warnings, and blocking reason. Never display IDs, login names, or phone numbers.

- [x] **Step 6: Run frontend tests and verify GREEN**

```bash
node erp-ui/scripts/run-node-tests.cjs \
  transferApprovalRules.test.js \
  desktopTransferApprovalActions.test.js
```

Expected: both tests pass.

- [x] **Step 7: Commit Task 5**

```bash
git add erp-ui/src/api/inventory/transferApprovalRule.js \
        erp-ui/src/views/inventory/transfer/rules.vue \
        erp-ui/test/transferApprovalRules.test.js
git commit -m "feat: show locked transfer approval chain preview"
```

### Task 6: Add guarded rule-node migration

**Files:**
- Create: `sql/erp_inventory_transfer_four_level_approval_20260713.sql`
- Create: `docker/mysql/db/erp_inventory_transfer_four_level_approval_20260713.sql`
- Modify: `erp-ui/test/transferApprovalDataScripts.test.js`

- [x] **Step 1: Write the failing migration contract**

```javascript
const rootSql = read("../sql/erp_inventory_transfer_four_level_approval_20260713.sql")
const dockerSql = read("../docker/mysql/db/erp_inventory_transfer_four_level_approval_20260713.sql")
assert.strictEqual(rootSql, dockerSql)
;[
  "start transaction",
  "level4_highest",
  "level3_highest",
  "operations_director",
  "general_manager",
  "yyzj",
  "zjl",
  "inv:transfer:approve",
  "signal sqlstate '45000'",
  "commit"
].forEach(fragment =>
  assert.ok(rootSql.toLowerCase().includes(fragment.toLowerCase()), fragment)
)
assert.ok(!rootSql.includes("inv_transfer_approval_instance"))
assert.ok(!rootSql.includes("inv_transfer_approval_task"))
```

- [x] **Step 2: Run the data-script test and verify RED**

```bash
node erp-ui/scripts/run-node-tests.cjs transferApprovalDataScripts.test.js
```

Expected: FAIL because migration files do not exist.

- [x] **Step 3: Implement the idempotent migration**

The MySQL 5.7-compatible procedure must:

1. Identify enabled transfer rules.
2. Abort if any enabled store lacks an active, scoped, permission-bearing `yyzj` candidate.
3. Abort if any enabled store lacks an active, scoped, permission-bearing `zjl` candidate.
4. Delete only current node definitions for transfer rules.
5. Insert exactly four nodes per rule using the stable roles and `any_one`.
6. Force `approval_mode='all_nodes'`, `required_count=0`, and `allow_self_approve='0'`.
7. Leave instances, tasks, snapshots, orders, and logs unchanged.
8. Commit only after four-node postconditions pass.

- [x] **Step 4: Mirror and compare the scripts**

```bash
cmp sql/erp_inventory_transfer_four_level_approval_20260713.sql \
    docker/mysql/db/erp_inventory_transfer_four_level_approval_20260713.sql
```

Expected: exit 0 and no output.

- [x] **Step 5: Run the data-script test and verify GREEN**

Run the Step 2 command. Expected: zero failures.

- [x] **Step 6: Validate only in a disposable schema**

Use the configured local MySQL client connection to copy only the tables needed by the guarded migration, run it in a temporary schema, assert the postconditions, then drop that schema. Do not apply the migration to `BossERP_stock_state_75c59ee` during implementation.

```bash
SOURCE_DB=BossERP_stock_state_75c59ee
TEMP_DB=codex_transfer_four_level_approval_20260713
TABLES="inv_transfer_approval_rule inv_transfer_approval_node sys_dept sys_user sys_user_profile sys_user_post sys_post sys_user_shop sys_user_role sys_role sys_role_menu sys_menu"
mysql -e "drop database if exists ${TEMP_DB}; create database ${TEMP_DB} character set utf8mb4 collate utf8mb4_general_ci"
mysqldump --single-transaction "$SOURCE_DB" $TABLES | mysql "$TEMP_DB"
mysql "$TEMP_DB" < sql/erp_inventory_transfer_four_level_approval_20260713.sql
mysql --batch --skip-column-names "$TEMP_DB" -e "select count(*) from inv_transfer_approval_rule r where r.status='0' and (select count(*) from inv_transfer_approval_node n where n.rule_id=r.rule_id)<>4"
mysql --batch --skip-column-names "$TEMP_DB" -e "select count(*) from inv_transfer_approval_rule where status='0' and (approval_mode<>'all_nodes' or required_count<>0 or allow_self_approve<>'0')"
mysql -e "drop database ${TEMP_DB}"
```

Both assertion queries must print `0`. If the local client needs explicit connection arguments, supply them through the existing client option file or environment without printing credentials.

- [x] **Step 7: Commit Task 6**

```bash
git add sql/erp_inventory_transfer_four_level_approval_20260713.sql \
        docker/mysql/db/erp_inventory_transfer_four_level_approval_20260713.sql \
        erp-ui/test/transferApprovalDataScripts.test.js
git commit -m "feat: migrate transfer rules to four approval levels"
```

### Task 7: Complete focused verification and review

**Files:**
- Review every file changed by Tasks 1-6.
- Update: `docs/superpowers/plans/2026-07-13-transfer-four-level-approval-optimization.md` checkboxes only as tasks complete.

- [x] **Step 1: Run the complete inventory module**

```bash
mvn -f erp-modules/pom.xml -pl erp-inventory test
```

Expected: all tests pass with zero failures and zero errors.

- [ ] **Step 2: Run all frontend Node tests**

```bash
node erp-ui/scripts/run-node-tests.cjs
```

Expected: every listed frontend test runs with zero failures.

Execution note: all 141 scripts were run with the existing workspace dependencies. Eight unrelated baseline assertions remain in notification/mobile files not changed by this branch: `headerNoticeReadAll`, `laborContractModule`, `mobileAppShell`, `mobileAuthEntryPages`, `mobileInventoryWorkbench`, `mobileProductionDataIsolation`, `mobileProfileMaintenance`, and `mobileProgressiveRedesign`. The three transfer-approval frontend tests pass.

- [x] **Step 3: Run source and migration checks**

```bash
git diff --check
git status --short
cmp sql/erp_inventory_transfer_four_level_approval_20260713.sql \
    docker/mysql/db/erp_inventory_transfer_four_level_approval_20260713.sql
```

Expected: no whitespace errors, only intended files changed, and SQL copies identical.

- [x] **Step 4: Check every approved requirement**

- Four system nodes are fixed in order.
- Level 4 uses manager then assistant with direct target-store scope.
- Level 3 satisfies `max(yyzjSort, zjlSort) < candidateSort < managerSort` and picks the closest manager-side band.
- Missing level 4/3 warns and continues upward.
- Missing operations director/general manager blocks submission.
- Submitter skip works through operations director; general-manager submit blocks.
- Candidate queries require `inv:transfer:approve` and valid target-store scope.
- New submissions snapshot candidates; old instances remain unchanged.
- Preview exposes only safe display data and blocking reasons.
- Migration is guarded, mirrored, idempotent, and not applied to the live local database.

- [x] **Step 5: Request code review**

Use the requesting-code-review workflow against the complete branch diff. Address only verified, in-scope findings.

Execution note: the review ran inline because this task does not authorize subagent delegation. It found and fixed candidate display-name leakage and the still-open standalone node-deletion service boundary.

- [x] **Step 6: Commit plan progress if checkbox state changed**

```bash
git add docs/superpowers/plans/2026-07-13-transfer-four-level-approval-optimization.md
git commit -m "docs: record transfer approval implementation progress"
```

## Execution handoff

Implement inline in this isolated worktree with `superpowers:executing-plans`. Do not dispatch subagents unless the user explicitly requests delegation. Stop at checkpoints after Tasks 2, 4, and 6 to review scope and verification evidence before continuing.
