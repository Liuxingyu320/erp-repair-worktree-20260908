# 调拨审批店铺范围整改 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 修复调拨审批规则管理和调拨审批通过流程中的店铺范围缺失问题，并把后续维护性问题拆到低风险阶段。

**Architecture:** 第一阶段只处理已验证的安全漏洞：规则管理接口必须区分“管理接口授权”和“系统内部规则匹配”；审批通过必须校验当前用户对所选店铺以及调拨单 from/to 部门的可见关系。第二阶段处理可观测性和配置硬化；第三阶段再做重复代码和大文件重构，避免安全修复被大重构污染。

**Tech Stack:** Java, Spring Boot, MyBatis, JUnit 5, AssertJ, Maven, RuoYi-style security context.

---

## Security Boundary Notes

- `matchRule(InvTransferOrder transfer)` is an internal workflow method used by `InvTransferApprovalServiceImpl.createInstanceForSubmit()` after the transfer order has already been validated and saved by `InvTransferServiceImpl`.
- `matchRule` must not read the current user or selected shop header. It matches workflow rules against the transfer document itself through `selectEnabledRulesForMatch(transfer)`.
- Any user-facing rule lookup or preview endpoint must use a scoped method such as `selectRuleList(..., selectedShopDeptId)`, `selectRuleById(..., selectedShopDeptId)`, or `previewRule(..., selectedShopDeptId)`.
- Do not call `matchRule` directly from controllers.

---

## File Map

- Modify: `erp-modules/erp-inventory/src/main/java/com/erp/inventory/controller/InvTransferApprovalRuleController.java`
  - 负责从请求头解析当前选择的店铺/仓库，并传给规则管理 service。
- Modify: `erp-modules/erp-inventory/src/main/java/com/erp/inventory/service/IInvTransferApprovalRuleService.java`
  - 规则管理方法增加 `selectedShopDeptId`；保留 `matchRule(InvTransferOrder transfer)` 作为系统内部匹配入口。
- Modify: `erp-modules/erp-inventory/src/main/java/com/erp/inventory/service/impl/InvTransferApprovalRuleServiceImpl.java`
  - 继承 `InvBaseService`，新增规则 scope 可见/可管理校验。
- Modify: `erp-modules/erp-inventory/src/main/java/com/erp/inventory/service/impl/InvTransferApprovalServiceImpl.java`
  - 继承或复用 `InvBaseService` 的店铺校验能力，在 `approve()` 中校验 selected shop 和调拨单范围。
- Modify: `erp-modules/erp-inventory/src/test/java/com/erp/inventory/service/impl/InvTransferApprovalRuleServiceImplTest.java`
  - 覆盖规则列表、详情、新增、编辑、删除、节点保存的店铺范围校验。
- Modify: `erp-modules/erp-inventory/src/test/java/com/erp/inventory/service/impl/InvTransferApprovalServiceImplTest.java`
  - 覆盖审批通过时的 selected shop 授权和调拨单范围校验。
- Later Modify: `erp-modules/erp-inventory/src/main/resources/mapper/inventory/InvTransferApprovalRuleMapper.xml`
  - 第一阶段使用 service 过滤，后续如果规则数量增长导致分页不准确，再把同一 scope 条件下推到 mapper。
- Later Modify: `erp-common/erp-common-log/src/main/java/com/erp/common/log/aspect/LogAspect.java`
- Later Modify: `erp-common/erp-common-core/src/main/java/com/erp/common/core/utils/bean/BeanUtils.java`
- Later Modify: `erp-common/erp-common-core/src/main/java/com/erp/common/core/utils/file/FileUtils.java`
- Later Modify: `erp-common/erp-common-core/src/main/java/com/erp/common/core/utils/ServletUtils.java`
- Later Modify: `erp-common/erp-common-core/src/main/java/com/erp/common/core/utils/JwtUtils.java`
- Later Modify: `*/src/main/resources/application-dev.yml`, `*/src/main/resources/bootstrap.yml`

---

### Task 1: Add Failing Tests For Approval Rule Scope

**Files:**
- Modify: `erp-modules/erp-inventory/src/test/java/com/erp/inventory/service/impl/InvTransferApprovalRuleServiceImplTest.java`

- [ ] **Step 1: Add security context cleanup and imports**

Add imports:

```java
import org.junit.jupiter.api.AfterEach;
import com.erp.common.core.context.SecurityContextHolder;
```

Add cleanup near the top of the test class:

```java
    @AfterEach
    void tearDown()
    {
        SecurityContextHolder.remove();
    }
```

- [ ] **Step 2: Add tests for non-admin rule management**

Add these tests before helper methods:

```java
    @Test
    @DisplayName("非管理员只能看到所选店铺范围内的调拨审批规则")
    void shouldOnlyListRulesInsideSelectedShopScopeForNonAdmin()
    {
        SecurityContextHolder.setUserId("9");
        FakeDeptScopeMapper deptScopeMapper = new FakeDeptScopeMapper()
                .addUserShop(9L, 100L)
                .addScopedDept(100L, 101L);
        InvTransferApprovalRule visibleRule = scopedRule(401L, "dept", 101L, 10);
        InvTransferApprovalRule hiddenRule = scopedRule(402L, "dept", 999L, 20);
        InvTransferApprovalRule globalRule = scopedRule(403L, "all", null, 30);
        InvTransferApprovalRuleServiceImpl service = newService(deptScopeMapper, visibleRule, hiddenRule, globalRule);

        List<InvTransferApprovalRule> rules = service.selectRuleList(new InvTransferApprovalRule(), 100L);

        assertThat(rules).extracting(InvTransferApprovalRule::getRuleId).containsExactly(401L);
    }

    @Test
    @DisplayName("非管理员不能查询店铺范围外的调拨审批规则详情")
    void shouldRejectRuleDetailOutsideSelectedShopScopeForNonAdmin()
    {
        SecurityContextHolder.setUserId("9");
        FakeDeptScopeMapper deptScopeMapper = new FakeDeptScopeMapper()
                .addUserShop(9L, 100L);
        InvTransferApprovalRuleServiceImpl service = newService(deptScopeMapper, scopedRule(402L, "dept", 999L, 20));

        assertThatThrownBy(() -> service.selectRuleById(402L, 100L))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("当前用户无权访问该审批规则");
    }

    @Test
    @DisplayName("非管理员不能保存店铺范围外的调拨审批规则")
    void shouldRejectSavingRuleOutsideSelectedShopScopeForNonAdmin()
    {
        SecurityContextHolder.setUserId("9");
        FakeDeptScopeMapper deptScopeMapper = new FakeDeptScopeMapper()
                .addUserShop(9L, 100L);
        InvTransferApprovalRuleServiceImpl service = newService(deptScopeMapper);
        InvTransferApprovalRule rule = scopedRule(null, "dept", 999L, 10);
        rule.setNodes(Collections.singletonList(node()));

        assertThatThrownBy(() -> service.saveRule(rule, 100L))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("当前用户无权配置该审批规则范围");
    }

    @Test
    @DisplayName("非管理员不能配置全局调拨审批规则")
    void shouldRejectGlobalRuleForNonAdmin()
    {
        SecurityContextHolder.setUserId("9");
        FakeDeptScopeMapper deptScopeMapper = new FakeDeptScopeMapper()
                .addUserShop(9L, 100L);
        InvTransferApprovalRuleServiceImpl service = newService(deptScopeMapper);
        InvTransferApprovalRule rule = scopedRule(null, "all", null, 10);
        rule.setNodes(Collections.singletonList(node()));

        assertThatThrownBy(() -> service.saveRule(rule, 100L))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("非管理员不能配置全局审批规则");
    }

    @Test
    @DisplayName("删除规则前必须校验原规则范围")
    void shouldRejectDeletingRuleOutsideSelectedShopScopeForNonAdmin()
    {
        SecurityContextHolder.setUserId("9");
        FakeDeptScopeMapper deptScopeMapper = new FakeDeptScopeMapper()
                .addUserShop(9L, 100L);
        InvTransferApprovalRuleServiceImpl service = newService(deptScopeMapper, scopedRule(402L, "dept", 999L, 20));

        assertThatThrownBy(() -> service.deleteRuleById(402L, 100L))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("当前用户无权访问该审批规则");
    }
```

- [ ] **Step 3: Update fake mapper to support user scope**

Replace the current `FakeDeptScopeMapper` body with a configurable version:

```java
    private static class FakeDeptScopeMapper implements InvDeptScopeMapper
    {
        private final Set<String> scopedDepts = new HashSet<>();
        private final Set<String> userShops = new HashSet<>();
        private final List<String> scopeChecks = new ArrayList<>();

        private FakeDeptScopeMapper addScopedDept(Long scopeDeptId, Long targetDeptId)
        {
            scopedDepts.add(scopeDeptId + ":" + targetDeptId);
            return this;
        }

        private FakeDeptScopeMapper addUserShop(Long userId, Long deptId)
        {
            userShops.add(userId + ":" + deptId);
            return this;
        }

        @Override
        public List<Long> selectSubDeptIds(Long deptId)
        {
            return Collections.singletonList(deptId);
        }

        @Override
        public List<Long> selectRelatedDeptIds(Long deptId)
        {
            return Collections.singletonList(deptId);
        }

        @Override
        public List<Long> selectAncestorDeptIds(Long deptId)
        {
            return Collections.emptyList();
        }

        @Override
        public int countDeptInScope(Long scopeDeptId, Long targetDeptId)
        {
            String key = scopeDeptId + ":" + targetDeptId;
            scopeChecks.add(key);
            return scopeDeptId != null && targetDeptId != null
                    && (scopeDeptId.equals(targetDeptId) || scopedDepts.contains(key)) ? 1 : 0;
        }

        @Override
        public int countUserShopScope(Long userId, Long deptId)
        {
            return userId != null && deptId != null && userShops.contains(userId + ":" + deptId) ? 1 : 0;
        }

        @Override
        public String selectDeptNameById(Long deptId)
        {
            return "测试组织";
        }

        @Override
        public String selectDeptTypeById(Long deptId)
        {
            return "STORE";
        }
    }
```

- [ ] **Step 4: Run tests and verify failure**

Run:

```bash
mvn -f erp-modules/pom.xml -pl erp-inventory -Dtest=InvTransferApprovalRuleServiceImplTest test
```

Expected: compile/test failure because `IInvTransferApprovalRuleService` and implementation do not yet have methods accepting `selectedShopDeptId`.

---

### Task 2: Implement Approval Rule Management Scope

**Files:**
- Modify: `erp-modules/erp-inventory/src/main/java/com/erp/inventory/service/IInvTransferApprovalRuleService.java`
- Modify: `erp-modules/erp-inventory/src/main/java/com/erp/inventory/service/impl/InvTransferApprovalRuleServiceImpl.java`
- Modify: `erp-modules/erp-inventory/src/test/java/com/erp/inventory/service/impl/InvTransferApprovalServiceImplTest.java`

- [ ] **Step 1: Change rule service interface**

Replace the management signatures with scoped versions and keep `matchRule` unchanged:

```java
public interface IInvTransferApprovalRuleService
{
    List<InvTransferApprovalRule> selectRuleList(InvTransferApprovalRule rule, Long selectedShopDeptId);
    InvTransferApprovalRule selectRuleById(Long ruleId, Long selectedShopDeptId);
    InvTransferApprovalRule saveRule(InvTransferApprovalRule rule, Long selectedShopDeptId);
    int deleteRuleById(Long ruleId, Long selectedShopDeptId);
    int saveNodes(Long ruleId, List<InvTransferApprovalNode> nodes, Long selectedShopDeptId);
    int deleteNodesByRuleId(Long ruleId, Long selectedShopDeptId);
    InvTransferApprovalRule previewRule(InvTransferOrder transfer, Long selectedShopDeptId);
    InvTransferApprovalRule matchRule(InvTransferOrder transfer);
}
```

- [ ] **Step 2: Update fake rule service in approval tests**

In `InvTransferApprovalServiceImplTest.FakeRuleService`, update method signatures:

```java
        @Override
        public List<InvTransferApprovalRule> selectRuleList(InvTransferApprovalRule rule, Long selectedShopDeptId)
        {
            return Collections.emptyList();
        }

        @Override
        public InvTransferApprovalRule selectRuleById(Long ruleId, Long selectedShopDeptId)
        {
            return rule;
        }

        @Override
        public InvTransferApprovalRule saveRule(InvTransferApprovalRule rule, Long selectedShopDeptId)
        {
            return rule;
        }

        @Override
        public int deleteRuleById(Long ruleId, Long selectedShopDeptId)
        {
            return 1;
        }

        @Override
        public int saveNodes(Long ruleId, List<InvTransferApprovalNode> nodes, Long selectedShopDeptId)
        {
            return nodes.size();
        }

        @Override
        public int deleteNodesByRuleId(Long ruleId, Long selectedShopDeptId)
        {
            return 1;
        }

        @Override
        public InvTransferApprovalRule previewRule(InvTransferOrder transfer, Long selectedShopDeptId)
        {
            return rule;
        }
```

- [ ] **Step 3: Make rule service inherit inventory scope helpers**

Change class declaration:

```java
public class InvTransferApprovalRuleServiceImpl extends InvBaseService implements IInvTransferApprovalRuleService
```

Remove the local field:

```java
    @Autowired
    private InvDeptScopeMapper deptScopeMapper;
```

because `InvBaseService` already provides protected `deptScopeMapper`.

- [ ] **Step 4: Implement scoped list/detail/save/delete**

Use this shape in `InvTransferApprovalRuleServiceImpl`:

```java
    @Override
    public List<InvTransferApprovalRule> selectRuleList(InvTransferApprovalRule rule, Long selectedShopDeptId)
    {
        List<InvTransferApprovalRule> rules = ruleMapper.selectRuleList(rule);
        List<InvTransferApprovalRule> visibleRules = new ArrayList<>();
        for (InvTransferApprovalRule item : rules)
        {
            if (isRuleScopeVisible(item, selectedShopDeptId))
            {
                visibleRules.add(item);
            }
        }
        return visibleRules;
    }

    @Override
    public InvTransferApprovalRule selectRuleById(Long ruleId, Long selectedShopDeptId)
    {
        InvTransferApprovalRule rule = loadRule(ruleId);
        assertRuleVisible(rule, selectedShopDeptId);
        rule.setNodes(nodeMapper.selectNodesByRuleId(ruleId));
        return rule;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public InvTransferApprovalRule saveRule(InvTransferApprovalRule rule, Long selectedShopDeptId)
    {
        validateRule(rule);
        if (rule.getRuleId() != null)
        {
            assertRuleVisible(loadRule(rule.getRuleId()), selectedShopDeptId);
        }
        assertRuleScopeManageable(rule, selectedShopDeptId);
        applyRuleDefaults(rule);
        if (rule.getRuleId() == null)
        {
            rule.setCreateBy(SecurityUtils.getUsername());
            ruleMapper.insertRule(rule);
        }
        else
        {
            rule.setUpdateBy(SecurityUtils.getUsername());
            ruleMapper.updateRule(rule);
        }
        saveRuleNodes(rule.getRuleId(), rule.getNodes());
        return selectRuleById(rule.getRuleId(), selectedShopDeptId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int deleteRuleById(Long ruleId, Long selectedShopDeptId)
    {
        assertRuleVisible(loadRule(ruleId), selectedShopDeptId);
        nodeMapper.deleteNodesByRuleId(ruleId);
        return ruleMapper.deleteRuleById(ruleId);
    }
```

- [ ] **Step 5: Add helper methods for rule scope semantics**

Add helpers in the same service:

```java
    private InvTransferApprovalRule loadRule(Long ruleId)
    {
        InvTransferApprovalRule rule = ruleMapper.selectRuleById(ruleId);
        if (rule == null)
        {
            throw new ServiceException("审批规则不存在");
        }
        return rule;
    }

    private void assertRuleVisible(InvTransferApprovalRule rule, Long selectedShopDeptId)
    {
        if (!isRuleScopeVisible(rule, selectedShopDeptId))
        {
            throw new ServiceException("当前用户无权访问该审批规则");
        }
    }

    private boolean isRuleScopeVisible(InvTransferApprovalRule rule, Long selectedShopDeptId)
    {
        if (rule == null)
        {
            return false;
        }
        if (SecurityUtils.isAdmin() && (selectedShopDeptId == null || selectedShopDeptId == 0))
        {
            return true;
        }
        Long scopeRoot = resolveAndValidateShopDept(selectedShopDeptId);
        String scopeType = StringUtils.defaultString(rule.getScopeType(), SCOPE_ALL);
        if (SCOPE_ALL.equals(scopeType))
        {
            return SecurityUtils.isAdmin();
        }
        return rule.getScopeId() != null && deptScopeMapper.countDeptInScope(scopeRoot, rule.getScopeId()) > 0;
    }

    private void assertRuleScopeManageable(InvTransferApprovalRule rule, Long selectedShopDeptId)
    {
        if (SecurityUtils.isAdmin() && (selectedShopDeptId == null || selectedShopDeptId == 0))
        {
            return;
        }
        Long scopeRoot = resolveAndValidateShopDept(selectedShopDeptId);
        String scopeType = StringUtils.defaultString(rule.getScopeType(), SCOPE_ALL);
        if (SCOPE_ALL.equals(scopeType))
        {
            if (!SecurityUtils.isAdmin())
            {
                throw new ServiceException("非管理员不能配置全局审批规则");
            }
            return;
        }
        if (rule.getScopeId() == null || deptScopeMapper.countDeptInScope(scopeRoot, rule.getScopeId()) <= 0)
        {
            throw new ServiceException("当前用户无权配置该审批规则范围");
        }
    }
```

- [ ] **Step 6: Scope node-only methods**

Update node-only methods:

```java
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int saveNodes(Long ruleId, List<InvTransferApprovalNode> nodes, Long selectedShopDeptId)
    {
        if (ruleId == null)
        {
            throw new ServiceException("审批规则ID不能为空");
        }
        assertRuleVisible(loadRule(ruleId), selectedShopDeptId);
        validateNodes(nodes);
        return saveRuleNodes(ruleId, nodes);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int deleteNodesByRuleId(Long ruleId, Long selectedShopDeptId)
    {
        assertRuleVisible(loadRule(ruleId), selectedShopDeptId);
        return nodeMapper.deleteNodesByRuleId(ruleId);
    }
```

- [ ] **Step 7: Add scoped preview without changing internal matching**

Add this public method:

```java
    @Override
    public InvTransferApprovalRule previewRule(InvTransferOrder transfer, Long selectedShopDeptId)
    {
        validatePreviewTransferScope(transfer, selectedShopDeptId);
        return matchRule(transfer);
    }
```

Add helpers:

```java
    private void validatePreviewTransferScope(InvTransferOrder transfer, Long selectedShopDeptId)
    {
        if (transfer == null)
        {
            throw new ServiceException("调拨单不能为空");
        }
        Long scopeRoot = resolveAndValidateShopDept(selectedShopDeptId);
        if (isDeptInScope(scopeRoot, transfer.getFromDeptId())
                || isDeptInScope(scopeRoot, transfer.getToDeptId())
                || isDeptInScope(scopeRoot, transfer.getFromWarehouseId())
                || isDeptInScope(scopeRoot, transfer.getToWarehouseId()))
        {
            return;
        }
        throw new ServiceException("当前用户无权预览该调拨单审批规则");
    }

    private boolean isDeptInScope(Long scopeRoot, Long deptId)
    {
        return deptId != null && deptScopeMapper.countDeptInScope(scopeRoot, deptId) > 0;
    }
```

- [ ] **Step 8: Run targeted rule tests**

Run:

```bash
mvn -f erp-modules/pom.xml -pl erp-inventory -Dtest=InvTransferApprovalRuleServiceImplTest test
```

Expected: PASS.

---

### Task 3: Pass Selected Shop From Rule Controller

**Files:**
- Modify: `erp-modules/erp-inventory/src/main/java/com/erp/inventory/controller/InvTransferApprovalRuleController.java`

- [ ] **Step 1: Change controller base class and add request parameters**

Change class declaration:

```java
public class InvTransferApprovalRuleController extends InvBaseController
```

Add import:

```java
import javax.servlet.http.HttpServletRequest;
```

Update methods:

```java
    public TableDataInfo list(InvTransferApprovalRule rule, HttpServletRequest request)
    {
        startPage();
        List<InvTransferApprovalRule> list = ruleService.selectRuleList(rule, resolveShopDeptId(request));
        return getDataTable(list);
    }

    public AjaxResult getInfo(@PathVariable("ruleId") Long ruleId, HttpServletRequest request)
    {
        return success(ruleService.selectRuleById(ruleId, resolveShopDeptId(request)));
    }

    public AjaxResult add(@Validated @RequestBody InvTransferApprovalRule rule, HttpServletRequest request)
    {
        return success(ruleService.saveRule(rule, resolveShopDeptId(request)));
    }

    public AjaxResult edit(@Validated @RequestBody InvTransferApprovalRule rule, HttpServletRequest request)
    {
        return success(ruleService.saveRule(rule, resolveShopDeptId(request)));
    }

    public AjaxResult remove(@PathVariable("ruleId") Long ruleId, HttpServletRequest request)
    {
        return toAjax(ruleService.deleteRuleById(ruleId, resolveShopDeptId(request)));
    }
```

- [ ] **Step 2: Scope preview input without changing internal `matchRule`**

Update preview to require selected shop and ensure the preview transfer belongs to the selected scope:

```java
    public AjaxResult preview(@RequestBody InvTransferOrder transfer, HttpServletRequest request)
    {
        Long selectedShopDeptId = resolveShopDeptId(request);
        return success(ruleService.previewRule(transfer, selectedShopDeptId));
    }
```

- [ ] **Step 3: Compile inventory module**

Run:

```bash
mvn -f erp-modules/pom.xml -pl erp-inventory -DskipTests compile
```

Expected: PASS.

---

### Task 4: Add Failing Tests For Approval Action Scope

**Files:**
- Modify: `erp-modules/erp-inventory/src/test/java/com/erp/inventory/service/impl/InvTransferApprovalServiceImplTest.java`

- [ ] **Step 1: Add rejection test for unauthorized selected shop**

Add test:

```java
    @Test
    @DisplayName("审批调拨单前必须校验当前用户可选择该店铺")
    void shouldRejectApprovalWhenUserCannotSelectShop()
    {
        SecurityContextHolder.setUserId("101");
        SecurityContextHolder.setUserName("fromManager");
        FakeInstanceMapper instanceMapper = new FakeInstanceMapper().add(runningInstance("back_to_draft", "1"));
        FakeTaskMapper taskMapper = new FakeTaskMapper().add(pendingTask(700L, 1, "dz", "101"));
        FakeTransferOrderMapper orderMapper = new FakeTransferOrderMapper().add(submittedTransfer("submitter", "DB001"));
        FakeDeptScopeMapper deptScopeMapper = new FakeDeptScopeMapper();
        InvTransferApprovalServiceImpl service = newService(rule(node(1, "发货店长", "from_leader", "dz")),
                new FakeCandidateMapper(), instanceMapper, taskMapper, deptScopeMapper);
        ReflectionTestUtils.setField(service, "transferOrderMapper", orderMapper);

        assertThatThrownBy(() -> service.approve(approvalRequest(700L, "approve", "同意"), 201L))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("当前用户无权选择该店铺");
    }
```

- [ ] **Step 2: Add rejection test for transfer outside selected shop scope**

Add test:

```java
    @Test
    @DisplayName("审批调拨单前必须校验调拨单属于当前店铺范围")
    void shouldRejectApprovalWhenTransferIsOutsideSelectedShopScope()
    {
        SecurityContextHolder.setUserId("101");
        SecurityContextHolder.setUserName("fromManager");
        FakeInstanceMapper instanceMapper = new FakeInstanceMapper().add(runningInstance("back_to_draft", "1"));
        FakeTaskMapper taskMapper = new FakeTaskMapper().add(pendingTask(700L, 1, "dz", "101"));
        InvTransferOrder transfer = submittedTransfer("submitter", "DB001");
        transfer.setFromDeptId(301L);
        transfer.setToDeptId(302L);
        FakeTransferOrderMapper orderMapper = new FakeTransferOrderMapper().add(transfer);
        FakeDeptScopeMapper deptScopeMapper = new FakeDeptScopeMapper()
                .addUserShop(101L, 201L);
        InvTransferApprovalServiceImpl service = newService(rule(node(1, "发货店长", "from_leader", "dz")),
                new FakeCandidateMapper(), instanceMapper, taskMapper, deptScopeMapper);
        ReflectionTestUtils.setField(service, "transferOrderMapper", orderMapper);

        assertThatThrownBy(() -> service.approve(approvalRequest(700L, "approve", "同意"), 201L))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("当前用户无权审批该调拨单");
    }
```

- [ ] **Step 3: Add success test for visible transfer**

Add test:

```java
    @Test
    @DisplayName("审批人有店铺范围且调拨单命中该范围时允许审批")
    void shouldApproveWhenSelectedShopCanSeeTransfer()
    {
        SecurityContextHolder.setUserId("101");
        SecurityContextHolder.setUserName("fromManager");
        FakeInstanceMapper instanceMapper = new FakeInstanceMapper().add(runningInstance("back_to_draft", "1"));
        FakeTaskMapper taskMapper = new FakeTaskMapper().add(pendingTask(700L, 1, "dz", "101"));
        FakeTransferOrderMapper orderMapper = new FakeTransferOrderMapper().add(submittedTransfer("submitter", "DB001"));
        FakeDeptScopeMapper deptScopeMapper = new FakeDeptScopeMapper()
                .addUserShop(101L, 201L)
                .addScopedDept(201L, 201L);
        InvTransferApprovalServiceImpl service = newService(rule(node(1, "发货店长", "from_leader", "dz")),
                new FakeCandidateMapper(), instanceMapper, taskMapper, deptScopeMapper);
        ReflectionTestUtils.setField(service, "transferOrderMapper", orderMapper);

        service.approve(approvalRequest(700L, "approve", "同意"), 201L);

        assertThat(taskMapper.tasks.get(0).getStatus()).isEqualTo("approved");
    }
```

- [ ] **Step 4: Extend fake dept mapper**

Add `scopedDepts` support to `InvTransferApprovalServiceImplTest.FakeDeptScopeMapper`:

```java
        private final Set<String> scopedDepts = new HashSet<>();

        private FakeDeptScopeMapper addScopedDept(Long scopeDeptId, Long targetDeptId)
        {
            scopedDepts.add(scopeDeptId + ":" + targetDeptId);
            return this;
        }

        @Override
        public int countDeptInScope(Long scopeDeptId, Long targetDeptId)
        {
            return scopeDeptId != null && targetDeptId != null
                    && (scopeDeptId.equals(targetDeptId) || scopedDepts.contains(scopeDeptId + ":" + targetDeptId)) ? 1 : 0;
        }
```

- [ ] **Step 5: Run tests and verify failure**

Run:

```bash
mvn -f erp-modules/pom.xml -pl erp-inventory -Dtest=InvTransferApprovalServiceImplTest test
```

Expected: at least the first two new tests fail because `approve()` does not validate selected shop.

---

### Task 5: Implement Approval Action Shop Scope

**Files:**
- Modify: `erp-modules/erp-inventory/src/main/java/com/erp/inventory/service/impl/InvTransferApprovalServiceImpl.java`

- [ ] **Step 1: Reuse base scope helper**

Change class declaration:

```java
public class InvTransferApprovalServiceImpl extends InvBaseService implements IInvTransferApprovalService
```

Remove the local duplicated field if present:

```java
    @Autowired
    private InvDeptScopeMapper deptScopeMapper;
```

Keep using the protected `deptScopeMapper` inherited from `InvBaseService`.

- [ ] **Step 2: Validate selected shop immediately after transfer lock**

Update `approve()`:

```java
    public void approve(InvTransferApprovalRequest request, Long selectedShopDeptId)
    {
        validateApprovalRequest(request);
        Long userId = SecurityUtils.getUserId();
        String userName = SecurityUtils.getUsername();
        InvTransferOrder transfer = lockSubmittedTransfer(request.getTransferId());
        validateApprovalShopScope(transfer, selectedShopDeptId);
        InvTransferApprovalInstance instance = lockRunningInstance(transfer);
        InvTransferApprovalTask task = lockApprovalTask(request, instance, userId);
        validateApprovalPermission(transfer, instance, task, userId, userName);

        if (ACTION_APPROVE.equals(request.getAction()))
        {
            approveTask(request, task, userId, userName);
            completeNodeIfReady(transfer, instance);
            return;
        }

        rejectTask(request, transfer, instance, task, userId, userName);
    }
```

- [ ] **Step 3: Add transfer visibility helper**

Add helper:

```java
    private void validateApprovalShopScope(InvTransferOrder transfer, Long selectedShopDeptId)
    {
        Long scopeRoot = resolveAndValidateShopDept(selectedShopDeptId);
        if (isTransferVisibleFromScope(scopeRoot, transfer))
        {
            return;
        }
        throw new ServiceException("当前用户无权审批该调拨单");
    }

    private boolean isTransferVisibleFromScope(Long scopeRoot, InvTransferOrder transfer)
    {
        return isDeptInScope(scopeRoot, transfer.getFromDeptId())
                || isDeptInScope(scopeRoot, transfer.getToDeptId())
                || isDeptInScope(scopeRoot, transfer.getFromWarehouseId())
                || isDeptInScope(scopeRoot, transfer.getToWarehouseId());
    }

    private boolean isDeptInScope(Long scopeRoot, Long deptId)
    {
        return deptId != null && deptScopeMapper.countDeptInScope(scopeRoot, deptId) > 0;
    }
```

- [ ] **Step 4: Run approval tests**

Run:

```bash
mvn -f erp-modules/pom.xml -pl erp-inventory -Dtest=InvTransferApprovalServiceImplTest test
```

Expected: PASS.

---

### Task 6: End-To-End Inventory Verification

**Files:**
- No new files.

- [ ] **Step 1: Run targeted security tests**

Run:

```bash
mvn -f erp-modules/pom.xml -pl erp-inventory -Dtest=InvTransferApprovalRuleServiceImplTest,InvTransferApprovalServiceImplTest test
```

Expected: PASS.

- [ ] **Step 2: Compile the module**

Run:

```bash
mvn -f erp-modules/pom.xml -pl erp-inventory -DskipTests compile
```

Expected: PASS.

- [ ] **Step 3: Check whitespace and patch health**

Run:

```bash
git diff --check
```

Expected: no output.

- [ ] **Step 4: Manual review checklist**

Verify:

```text
1. Controller management methods all pass resolveShopDeptId(request).
2. matchRule(InvTransferOrder transfer) remains usable by transfer submit flow without selectedShopDeptId.
3. Non-admin cannot create/edit/delete scope_type=all rules.
4. Non-admin cannot query or delete rules whose scope_id is outside selected shop scope.
5. approve() rejects selected shops the user cannot select.
6. approve() rejects transfer orders unrelated to the selected shop scope.
```

---

### Task 7: Low-Risk Observability Cleanup

**Files:**
- Modify: `erp-common/erp-common-log/src/main/java/com/erp/common/log/aspect/LogAspect.java`
- Modify: `erp-common/erp-common-core/src/main/java/com/erp/common/core/utils/bean/BeanUtils.java`
- Modify: `erp-common/erp-common-core/src/main/java/com/erp/common/core/utils/file/FileUtils.java`
- Modify: `erp-common/erp-common-core/src/main/java/com/erp/common/core/utils/ServletUtils.java`
- Modify: `erp-common/erp-common-core/src/main/java/com/erp/common/core/utils/JwtUtils.java`

- [ ] **Step 1: Replace production `printStackTrace()`**

Use existing logging style in each class. If a class has no logger, add the matching logger declaration, for example:

```java
private static final Logger log = LoggerFactory.getLogger(BeanUtils.class);
private static final Logger log = LoggerFactory.getLogger(FileUtils.class);
private static final Logger log = LoggerFactory.getLogger(ServletUtils.class);
private static final Logger log = LoggerFactory.getLogger(JwtUtils.class);
```

Replace:

```java
e.printStackTrace();
```

with:

```java
log.warn("操作失败", e);
```

Use context-specific messages, for example `文件删除失败` in `FileUtils` and `Bean属性复制失败` in `BeanUtils`.

- [ ] **Step 2: Make JWT ignored exceptions observable**

In `JwtUtils`, replace empty catch blocks:

```java
catch (Exception ignored)
{
    return null;
}
```

with:

```java
catch (Exception ex)
{
    log.debug("读取JWT配置失败", ex);
    return null;
}
```

and:

```java
catch (Exception ex)
{
    log.debug("Spring Environment未就绪，使用静态JWT配置解析", ex);
    return null;
}
```

- [ ] **Step 3: Run common tests**

Run:

```bash
mvn -f erp-common/pom.xml -pl erp-common-core -Dtest=JwtUtilsTest,JwtSecretStartupValidatorTest test
mvn -f erp-common/pom.xml -pl erp-common-log -DskipTests compile
```

Expected: PASS.

---

### Task 8: Dev Configuration Hardening

**Files:**
- Modify: `erp-auth/src/main/resources/bootstrap.yml`
- Modify: `erp-gateway/src/main/resources/bootstrap.yml`
- Modify: `erp-modules/erp-system/src/main/resources/bootstrap.yml`
- Modify: `erp-modules/erp-system/src/main/resources/application-dev.yml`
- Modify: `erp-modules/erp-inventory/src/main/resources/bootstrap.yml`
- Modify: `erp-modules/erp-job/src/main/resources/application-dev.yml`
- Modify: `erp-modules/erp-gen/src/main/resources/application-dev.yml`
- Modify: `erp-modules/erp-oa/src/main/resources/bootstrap.yml`
- Modify: `docs/production-env-example.md`
- Modify: `docs/production-security-checklist.md`

- [ ] **Step 1: Replace blank Redis passwords with environment placeholders**

Use:

```yaml
password: ${REDIS_PASSWORD:}
```

- [ ] **Step 2: Replace blank MySQL passwords with environment placeholders**

Use:

```yaml
username: ${MYSQL_USERNAME:root}
password: ${MYSQL_PASSWORD:}
```

- [ ] **Step 3: Document deployment expectation**

Add to `docs/production-security-checklist.md`:

```markdown
- [ ] 生产和预发布环境必须显式配置 `MYSQL_USERNAME`、`MYSQL_PASSWORD`、`REDIS_PASSWORD`，不得依赖 dev/local 空密码默认值。
```

- [ ] **Step 4: Verify YAML still parses by compiling affected modules**

Run:

```bash
mvn -f erp-auth/pom.xml -DskipTests compile
mvn -f erp-gateway/pom.xml -DskipTests compile
mvn -f erp-modules/pom.xml -pl erp-system,erp-inventory,erp-oa -DskipTests compile
```

Expected: PASS.

---

### Task 9: Deferred Refactoring Backlog

**Files:**
- Later Modify: `erp-modules/erp-inventory/src/main/java/com/erp/inventory/service/impl/InvBaseService.java`
- Later Modify: 16 inventory/OA service classes with `assertAndGetScoped*`
- Later Modify: `erp-modules/erp-system/src/main/java/com/erp/system/service/impl/SysMenuServiceImpl.java`
- Later Modify: `erp-modules/erp-system/src/main/java/com/erp/system/service/impl/SysDeptServiceImpl.java`
- Later Modify: `erp-modules/erp-system/src/main/java/com/erp/system/service/impl/SysUserShopServiceImpl.java`
- Later Modify: `erp-modules/erp-inventory/src/main/java/com/erp/inventory/controller/InvTransferController.java`
- Later Modify: `erp-modules/erp-inventory/src/main/java/com/erp/inventory/controller/InvTransferApprovalRuleController.java`

- [ ] **Step 1: Do not mix this with the security patch**

Keep this task for a separate branch/commit after Tasks 1-8 are merged.

- [ ] **Step 2: Extract repeated scoped entity loading only after tests exist**

Recommended helper shape:

```java
protected <T> T assertAndGetScopedEntity(Long id, Long selectedShopDeptId,
        Function<Long, T> loader, Function<T, Long> shopDeptGetter, String notFoundMessage, String noScopeMessage)
{
    T entity = loader.apply(id);
    if (entity == null)
    {
        throw new ServiceException(notFoundMessage);
    }
    assertShopVisible(shopDeptGetter.apply(entity), selectedShopDeptId, noScopeMessage);
    return entity;
}
```

- [ ] **Step 3: Extract tree recursion only after behavior snapshot tests**

Recommended utility shape:

```java
public final class TreeBuildUtils
{
    private TreeBuildUtils() {}

    public static <T, ID> List<T> buildTree(List<T> list, Function<T, ID> idGetter,
            Function<T, ID> parentGetter, BiConsumer<T, List<T>> childrenSetter)
    {
        List<T> roots = new ArrayList<>();
        Set<ID> ids = list.stream().map(idGetter).collect(Collectors.toSet());
        for (T item : list)
        {
            if (!ids.contains(parentGetter.apply(item)))
            {
                attachChildren(list, item, idGetter, parentGetter, childrenSetter);
                roots.add(item);
            }
        }
        return roots.isEmpty() ? list : roots;
    }
}
```

- [ ] **Step 4: Split large classes only when touching related behavior**

Start with `InvTransferApprovalServiceImpl` because it is in the current risk area. Extract snapshot parsing, candidate resolution, and task completion into focused collaborators only after current approval tests pass.

- [ ] **Step 5: Remove fragile `/transfer` controller base-path sharing**

Move approval rule management to an explicit controller base path after frontend routes and API clients are checked. Preserve backward compatibility during one release by keeping the old routes as deprecated aliases or by adding a gateway rewrite.

Recommended final shape:

```java
@RestController
@RequestMapping("/transfer/rule")
public class InvTransferApprovalRuleController extends InvBaseController
{
    @GetMapping("/list")
    public TableDataInfo list(InvTransferApprovalRule rule, HttpServletRequest request)
    {
        startPage();
        return getDataTable(ruleService.selectRuleList(rule, resolveShopDeptId(request)));
    }
}
```

The existing `InvTransferController` keeps:

```java
@RestController
@RequestMapping("/transfer")
public class InvTransferController extends InvBaseController
{
}
```

---

## Execution Order

1. Tasks 1-6: required security remediation for the two confirmed HIGH issues.
2. Tasks 7-8: low-risk cleanup for observability and dev configuration hardening.
3. Task 9: separate refactoring backlog, not part of the security hotfix.

## Final Verification Commands

Run before claiming completion:

```bash
mvn -f erp-modules/pom.xml -pl erp-inventory -Dtest=InvTransferApprovalRuleServiceImplTest,InvTransferApprovalServiceImplTest test
mvn -f erp-modules/pom.xml -pl erp-inventory -DskipTests compile
mvn -f erp-common/pom.xml -pl erp-common-core -Dtest=JwtUtilsTest,JwtSecretStartupValidatorTest test
git diff --check
```

Expected: all Maven commands pass and `git diff --check` has no output.
