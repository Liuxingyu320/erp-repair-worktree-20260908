# Transfer Approval Progress Visibility Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Let authorized store senders see the real transfer approval node, candidate approvers, actual decisions, and prior approval rounds on desktop and mobile.

**Architecture:** Keep transfer list payloads light by batch-enriching each page with an `approvalSummary`, and expose full history through `GET /inventory/transfer/{transferId}/approval-track`. The backend owns authorization, safe display-name resolution, round construction, and node-state normalization; desktop and mobile render the same read model without parsing rule snapshots.

**Tech Stack:** Java 25, Spring Boot, Apache Shiro permission annotations, MyBatis XML, Fastjson2, Vue 2.6, Element UI, Node contract tests, Maven/JUnit 5/AssertJ.

---

## File map

- Create `erp-modules/erp-inventory/src/main/java/com/erp/inventory/domain/vo/InvTransferApprovalSummary.java`: lightweight list read model.
- Create `erp-modules/erp-inventory/src/main/java/com/erp/inventory/domain/vo/InvTransferApprovalTrack.java`: complete track response with nested round and node DTOs.
- Modify transfer instance/task/status-log/candidate mappers and XML: add read-only history, batch summary, log, and safe-name queries.
- Modify `InvTransferApprovalServiceImpl`: build summaries and normalized multi-round tracks.
- Modify `InvTransferServiceImpl`: harden selected-organization validation, enrich lists, and authorize track reads.
- Modify `InvTransferController`: expose the read endpoint under existing detail permissions.
- Create `erp-ui/src/views/inventory/transfer/components/TransferApprovalProgress.vue`: shared desktop timeline.
- Modify desktop transfer processing and records pages: list summaries, track loading, real approval timeline, and “业务履约进度” naming.
- Modify mobile feature service/mapper/styles: load the same track and render it before line items.
- Add focused backend and Node tests before each implementation increment.

## Task 1: Backend approval read models and mapper contracts

**Files:**

- Create: `erp-modules/erp-inventory/src/main/java/com/erp/inventory/domain/vo/InvTransferApprovalSummary.java`
- Create: `erp-modules/erp-inventory/src/main/java/com/erp/inventory/domain/vo/InvTransferApprovalTrack.java`
- Modify: `erp-modules/erp-inventory/src/main/java/com/erp/inventory/domain/InvTransferOrder.java`
- Modify: `erp-modules/erp-inventory/src/main/java/com/erp/inventory/mapper/InvTransferApprovalInstanceMapper.java`
- Modify: `erp-modules/erp-inventory/src/main/java/com/erp/inventory/mapper/InvTransferApprovalTaskMapper.java`
- Modify: `erp-modules/erp-inventory/src/main/java/com/erp/inventory/mapper/InvTransferStatusLogMapper.java`
- Modify: `erp-modules/erp-inventory/src/main/java/com/erp/inventory/mapper/InvTransferApprovalCandidateMapper.java`
- Modify: `erp-modules/erp-inventory/src/main/resources/mapper/inventory/InvTransferApprovalInstanceMapper.xml`
- Modify: `erp-modules/erp-inventory/src/main/resources/mapper/inventory/InvTransferApprovalTaskMapper.xml`
- Modify: `erp-modules/erp-inventory/src/main/resources/mapper/inventory/InvTransferStatusLogMapper.xml`
- Modify: `erp-modules/erp-inventory/src/main/resources/mapper/inventory/InvTransferApprovalCandidateMapper.xml`
- Test: `erp-modules/erp-inventory/src/test/java/com/erp/inventory/mapper/InventoryMapperBindingTest.java`

- [ ] **Step 1: Write failing mapper contract assertions**

Add assertions that require the five read paths and forbid raw snapshot columns in the summary query:

```java
assertMapperMethod(InvTransferApprovalInstanceMapper.class,
        "selectInstancesByTransferId", Long.class);
assertMapperMethod(InvTransferApprovalTaskMapper.class,
        "selectTasksByInstanceIds", List.class);
assertMapperMethod(InvTransferApprovalTaskMapper.class,
        "selectApprovalSummariesByTransferIds", List.class);
assertMapperMethod(InvTransferStatusLogMapper.class,
        "selectLogsByTransferId", Long.class);
assertMapperMethod(InvTransferApprovalCandidateMapper.class,
        "selectSafeDisplayNamesByUserIds", List.class);
```

- [ ] **Step 2: Run the mapper test and confirm red**

Run:

```bash
mvn -pl erp-modules/erp-inventory -am \
  -Dtest=InventoryMapperBindingTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: FAIL because the read methods and mapped statements do not exist.

- [ ] **Step 3: Add the summary DTO and attach it to transfer rows**

Implement this public JSON shape; the two raw CSV fields are mapper-only and must be ignored by Jackson:

```java
public class InvTransferApprovalSummary implements Serializable
{
    private Long transferId;
    private String state;
    private Integer roundNo;
    private Integer currentNodeOrder;
    private Integer totalNodeCount;
    private String currentNodeName;
    private List<String> currentCandidateDisplayNames = new ArrayList<>();
    private Integer currentCandidateCount;
    private Integer completedNodeCount;
    private String summaryText;

    @JsonIgnore
    private String candidateUserIds;

    @JsonIgnore
    private String candidateUserNames;

    public Long getTransferId() { return transferId; }
    public void setTransferId(Long transferId) { this.transferId = transferId; }
    public String getState() { return state; }
    public void setState(String state) { this.state = state; }
    public Integer getRoundNo() { return roundNo; }
    public void setRoundNo(Integer roundNo) { this.roundNo = roundNo; }
    public Integer getCurrentNodeOrder() { return currentNodeOrder; }
    public void setCurrentNodeOrder(Integer currentNodeOrder) { this.currentNodeOrder = currentNodeOrder; }
    public Integer getTotalNodeCount() { return totalNodeCount; }
    public void setTotalNodeCount(Integer totalNodeCount) { this.totalNodeCount = totalNodeCount; }
    public String getCurrentNodeName() { return currentNodeName; }
    public void setCurrentNodeName(String currentNodeName) { this.currentNodeName = currentNodeName; }
    public List<String> getCurrentCandidateDisplayNames() { return currentCandidateDisplayNames; }
    public void setCurrentCandidateDisplayNames(List<String> names) {
        this.currentCandidateDisplayNames = names == null ? new ArrayList<>() : new ArrayList<>(names);
    }
    public Integer getCurrentCandidateCount() { return currentCandidateCount; }
    public void setCurrentCandidateCount(Integer currentCandidateCount) { this.currentCandidateCount = currentCandidateCount; }
    public Integer getCompletedNodeCount() { return completedNodeCount; }
    public void setCompletedNodeCount(Integer completedNodeCount) { this.completedNodeCount = completedNodeCount; }
    public String getSummaryText() { return summaryText; }
    public void setSummaryText(String summaryText) { this.summaryText = summaryText; }
    public String getCandidateUserIds() { return candidateUserIds; }
    public void setCandidateUserIds(String candidateUserIds) { this.candidateUserIds = candidateUserIds; }
    public String getCandidateUserNames() { return candidateUserNames; }
    public void setCandidateUserNames(String candidateUserNames) { this.candidateUserNames = candidateUserNames; }
}
```

Add `private InvTransferApprovalSummary approvalSummary;` with getter/setter to `InvTransferOrder`.

- [ ] **Step 4: Add the full track DTO**

Implement `InvTransferApprovalTrack` with the following exact fields and explicit JavaBean `getX/setX` methods for every field, because Jackson serializes the response through those accessors:

```java
public class InvTransferApprovalTrack implements Serializable
{
    private Long transferId;
    private String orderNo;
    private String documentStatus;
    private String state;
    private String traceCompleteness;
    private String summaryText;
    private Node currentNode;
    private List<Round> rounds = new ArrayList<>();

    public static class Round implements Serializable
    {
        private Integer roundNo;
        private String status;
        private Date submittedAt;
        private Date finishedAt;
        private List<Node> nodes = new ArrayList<>();
    }

    public static class Node implements Serializable
    {
        private Integer nodeOrder;
        private String nodeName;
        private String postName;
        private String state;
        private List<String> candidateDisplayNames = new ArrayList<>();
        private Integer candidateCount;
        private String approvalModeText;
        private String decision;
        private String actualApproverDisplayName;
        private Date handledAt;
        private String comment;
    }
}
```

- [ ] **Step 5: Add mapper methods and SQL**

Use parameterized `foreach` lists and existing result maps:

```java
List<InvTransferApprovalInstance> selectInstancesByTransferId(@Param("transferId") Long transferId);
List<InvTransferApprovalTask> selectTasksByInstanceIds(@Param("instanceIds") List<Long> instanceIds);
List<InvTransferApprovalSummary> selectApprovalSummariesByTransferIds(@Param("transferIds") List<Long> transferIds);
List<InvTransferStatusLog> selectLogsByTransferId(@Param("transferId") Long transferId);
List<Map<String, Object>> selectSafeDisplayNamesByUserIds(@Param("userIds") List<Long> userIds);
```

The summary query must join only the order's current `approval_instance_id` and current node:

```sql
select o.transfer_id as transferId,
       case
         when o.status = 'cancelled' then 'cancelled'
         when i.status = 'running' then 'in_progress'
         when i.status = 'rejected' then 'rejected'
         when i.status = 'approved' then 'approved'
         when i.status = 'closed' then 'cancelled'
         else 'not_started'
       end as state,
       (select count(1)
          from inv_transfer_approval_instance ri
         where ri.transfer_id = o.transfer_id
           and ri.instance_id &lt;= i.instance_id) as roundNo,
       i.current_node_order as currentNodeOrder,
       t.node_name as currentNodeName,
       t.candidate_user_ids as candidateUserIds,
       t.candidate_user_names as candidateUserNames,
       (select count(distinct all_task.node_order)
          from inv_transfer_approval_task all_task
         where all_task.instance_id = i.instance_id) as totalNodeCount,
       (select count(distinct done_task.node_order)
          from inv_transfer_approval_task done_task
         where done_task.instance_id = i.instance_id
           and done_task.status = 'approved') as completedNodeCount
  from inv_transfer_order o
  left join inv_transfer_approval_instance i
    on i.instance_id = o.approval_instance_id
   and i.transfer_id = o.transfer_id
  left join inv_transfer_approval_task t
    on t.instance_id = i.instance_id
   and t.transfer_id = o.transfer_id
   and t.node_order = i.current_node_order
 where o.transfer_id in (...)
```

The safe-name query returns only `userId` and `coalesce(nullif(nick_name,''), user_name)` as `displayName`; no phone or profile columns.

- [ ] **Step 6: Re-run mapper tests**

Run the command from Step 2.

Expected: PASS.

- [ ] **Step 7: Commit the mapper/read-model slice**

```bash
git add erp-modules/erp-inventory/src/main/java/com/erp/inventory/domain \
        erp-modules/erp-inventory/src/main/java/com/erp/inventory/mapper \
        erp-modules/erp-inventory/src/main/resources/mapper/inventory \
        erp-modules/erp-inventory/src/test/java/com/erp/inventory/mapper/InventoryMapperBindingTest.java
git commit -m "feat: add transfer approval read models"
```

## Task 2: Approval-track service and safe state normalization

**Files:**

- Modify: `erp-modules/erp-inventory/src/main/java/com/erp/inventory/service/IInvTransferApprovalService.java`
- Modify: `erp-modules/erp-inventory/src/main/java/com/erp/inventory/service/impl/InvTransferApprovalServiceImpl.java`
- Modify: `erp-modules/erp-inventory/src/test/java/com/erp/inventory/service/impl/InvTransferApprovalServiceImplTest.java`

- [ ] **Step 1: Add failing service tests**

Add tests using the existing fake mappers for these exact outcomes:

```java
@Test
@DisplayName("审批轨迹区分已通过当前等待节点并隐藏原始账号")
void shouldBuildSafeCurrentApprovalTrack()
{
    InvTransferOrder order = transfer();
    order.setStatus(InvStatusConstants.SUBMITTED);
    order.setApprovalInstanceId(70L);

    InvTransferApprovalTrack track = serviceWithTrack(
            instance(70L, "running", 2),
            List.of(
                    task(701L, 70L, 1, "四级负责人", "approved", "101", "13800138000", 101L, "13800138000"),
                    task(702L, 70L, 2, "三级负责人", "pending", "102,103", "13800138001,李四", null, null),
                    task(703L, 70L, 3, "运营总监", "pending", "104", "王五", null, null)),
            Map.of(101L, "王店长", 102L, "张三", 103L, "李四", 104L, "王五"))
            .selectTrack(order);

    assertThat(track.getState()).isEqualTo("in_progress");
    assertThat(track.getCurrentNode().getNodeName()).isEqualTo("三级负责人");
    assertThat(track.getCurrentNode().getCandidateDisplayNames()).containsExactly("张三", "李四");
    assertThat(track.getRounds().get(0).getNodes())
            .extracting(InvTransferApprovalTrack.Node::getState)
            .containsExactly("approved", "current", "waiting");
    assertThat(track.toString()).doesNotContain("13800138000", "13800138001");
}
```

Add separate tests for:

- rejected first round plus running second round;
- cancelled order overriding a running instance and returning `terminated` nodes;
- approved instance with zero tasks plus an `auto_approve` log returning `auto_approved`;
- running instance with no current task returning `traceCompleteness=partial`;
- list summaries replacing phone-like stored names with resolved display names.

- [ ] **Step 2: Run the focused test and confirm red**

```bash
mvn -pl erp-modules/erp-inventory -am \
  -Dtest=InvTransferApprovalServiceImplTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: FAIL because `selectTrack` and summary enrichment do not exist.

- [ ] **Step 3: Add service interface methods**

```java
InvTransferApprovalTrack selectTrack(InvTransferOrder transfer);
Map<Long, InvTransferApprovalSummary> selectApprovalSummaries(List<Long> transferIds);
```

- [ ] **Step 4: Build safe display names once per response**

Collect candidate IDs and actual approver IDs from all tasks, issue one `selectSafeDisplayNamesByUserIds` query, then map names in candidate-ID order. The fallback rule is exact:

```java
private String safeStoredDisplayName(String stored)
{
    String value = StringUtils.trim(stored);
    if (StringUtils.isEmpty(value)
            || value.matches("^1\\d{10}$")
            || value.contains("@"))
    {
        return "姓名未配置";
    }
    return value;
}
```

Never put candidate IDs, approver IDs, or raw CSV names into the public DTO.

- [ ] **Step 5: Normalize rounds and nodes on the server**

Implement the state precedence in this order:

```java
if (InvStatusConstants.CANCELLED.equals(order.getStatus())) return "terminated";
if ("approved".equals(task.getStatus())) return "approved";
if ("rejected".equals(task.getStatus())) return "rejected";
if (!"running".equals(instance.getStatus())) return "terminated";
if (Objects.equals(task.getNodeOrder(), instance.getCurrentNodeOrder())) return "current";
if (task.getNodeOrder() != null && instance.getCurrentNodeOrder() != null
        && task.getNodeOrder() > instance.getCurrentNodeOrder()) return "waiting";
return "terminated";
```

Generate round numbers from ascending instance order. Parse only node `approvalMode` and `requiredCount` from the stored Fastjson2 snapshot; on parse failure keep the task visible, use `approvalModeText="审批方式未知"`, and mark the response partial.

- [ ] **Step 6: Build track-level states and summaries**

Use these exact public states and copy:

```text
not_started  -> 尚未提交审批
in_progress  -> 当前第 n/m 级，等待 {nodeName} 审批
approved     -> 审批已通过
rejected     -> 上轮已驳回，待修改后重新提交
auto_approved-> 系统自动通过，无需人工审批
cancelled    -> 调拨已取消，审批已终止
partial      -> 审批数据异常，请联系管理员
```

For the latest running round, set `currentNode` to the node whose normalized state is `current`. For an approved instance with no tasks, synthesize one `auto_approved` node using the newest `auto_approve` status log time and reason.

- [ ] **Step 7: Run focused service tests**

Run the command from Step 2.

Expected: PASS.

- [ ] **Step 8: Commit the service slice**

```bash
git add erp-modules/erp-inventory/src/main/java/com/erp/inventory/service \
        erp-modules/erp-inventory/src/test/java/com/erp/inventory/service/impl/InvTransferApprovalServiceImplTest.java
git commit -m "feat: build transfer approval tracks"
```

## Task 3: Secure controller endpoint and list-summary integration

**Files:**

- Modify: `erp-modules/erp-inventory/src/main/java/com/erp/inventory/service/IInvTransferService.java`
- Modify: `erp-modules/erp-inventory/src/main/java/com/erp/inventory/service/impl/InvTransferServiceImpl.java`
- Modify: `erp-modules/erp-inventory/src/main/java/com/erp/inventory/controller/InvTransferController.java`
- Modify: `erp-modules/erp-inventory/src/test/java/com/erp/inventory/service/impl/InvTransferServiceImplTest.java`
- Create: `erp-modules/erp-inventory/src/test/java/com/erp/inventory/controller/InvTransferControllerSourceTest.java`

- [ ] **Step 1: Write failing scope and endpoint tests**

Add a service test proving the selected context is validated before department visibility:

```java
assertThatThrownBy(() -> service.getTransferDetail(10L, 999L))
        .isInstanceOf(ServiceException.class)
        .hasMessageContaining("无权选择该店铺");
```

Add a list test whose fake approval service returns a summary map and assert `selectTransferList` attaches it to the matching order.

Add a source contract test that requires:

```java
assertThat(source).contains("@GetMapping(\"/{transferId}/approval-track\")");
assertThat(source).contains("inv:transfer:query", "inv:transfer:records:query");
assertThat(source).contains("logical = Logical.OR");
```

- [ ] **Step 2: Run focused tests and confirm red**

```bash
mvn -pl erp-modules/erp-inventory -am \
  -Dtest=InvTransferServiceImplTest,InvTransferControllerSourceTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: FAIL because the endpoint, secure scope path, and summary attachment are absent.

- [ ] **Step 3: Harden transfer detail authorization**

Change `assertAndGetScopedTransfer` so it first calls `resolveAndValidateShopDept(selectedShopDeptId)`. Test all four organization columns against the validated root:

```java
Long scopeRoot = resolveAndValidateShopDept(selectedShopDeptId);
if (!isDeptVisible(db.getFromDeptId(), scopeRoot)
        && !isDeptVisible(db.getToDeptId(), scopeRoot)
        && !isDeptVisible(db.getFromWarehouseId(), scopeRoot)
        && !isDeptVisible(db.getToWarehouseId(), scopeRoot))
{
    throw new ServiceException("无权访问该调拨单");
}
```

Remove the fallback that reads a root directly from an unvalidated request header.

- [ ] **Step 4: Expose the service and controller read path**

Add:

```java
InvTransferApprovalTrack getApprovalTrack(Long transferId, Long selectedShopDeptId);
```

The implementation calls the hardened `assertAndGetScopedTransfer`, then `transferApprovalService.selectTrack(order)`.

Controller method:

```java
@RequiresPermissions(value = { "inv:transfer:query", "inv:transfer:records:query" }, logical = Logical.OR)
@GetMapping("/{transferId}/approval-track")
public AjaxResult approvalTrack(@PathVariable Long transferId, HttpServletRequest request)
{
    return success(transferService.getApprovalTrack(transferId, resolveShopDeptId(request)));
}
```

- [ ] **Step 5: Enrich list pages in one batch**

After `selectInvTransferOrderList`, collect page IDs, call `selectApprovalSummaries` once, and attach matching summaries. Empty pages must skip the query.

- [ ] **Step 6: Run focused and module tests**

```bash
mvn -pl erp-modules/erp-inventory -am \
  -Dtest=InvTransferApprovalServiceImplTest,InvTransferServiceImplTest,InvTransferControllerSourceTest,InventoryMapperBindingTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: PASS.

- [ ] **Step 7: Commit the secure API slice**

```bash
git add erp-modules/erp-inventory/src/main/java/com/erp/inventory/controller \
        erp-modules/erp-inventory/src/main/java/com/erp/inventory/service \
        erp-modules/erp-inventory/src/test/java/com/erp/inventory
git commit -m "feat: expose secure transfer approval progress"
```

## Task 4: Desktop list summaries and shared approval timeline

**Files:**

- Modify: `erp-ui/src/api/inventory/transfer.js`
- Create: `erp-ui/src/views/inventory/transfer/components/TransferApprovalProgress.vue`
- Modify: `erp-ui/src/views/inventory/transfer/index.vue`
- Modify: `erp-ui/src/views/inventory/transfer/records.vue`
- Create: `erp-ui/test/transferApprovalProgressUx.test.js`

- [ ] **Step 1: Add a failing desktop contract test**

Require all of the following:

```javascript
assert.ok(transferApi.includes("getTransferApprovalTrack"))
assert.ok(component.includes("candidateDisplayNames"))
assert.ok(component.includes("actualApproverDisplayName"))
assert.ok(component.includes("approvalModeText"))
assert.ok(indexPage.includes("approvalSummary"))
assert.ok(indexPage.includes("TransferApprovalProgress"))
assert.ok(indexPage.includes("业务履约进度"))
assert.ok(recordsPage.includes("TransferApprovalProgress"))
```

Also require that both detail loaders catch track errors independently from the main detail request.

- [ ] **Step 2: Run the new Node test and confirm red**

```bash
cd erp-ui
node test/transferApprovalProgressUx.test.js
```

Expected: FAIL because the API and component do not exist.

- [ ] **Step 3: Add the track API function**

```javascript
export function getTransferApprovalTrack(transferId) {
  return request({
    url: "/inventory/transfer/" + transferId + "/approval-track",
    method: "get"
  })
}
```

- [ ] **Step 4: Implement the shared desktop component**

The component accepts `track`, `loading`, and `error` props; emits `retry`; and renders:

```vue
<el-alert v-if="error" title="审批进度加载失败" type="warning" show-icon :closable="false">
  <el-button slot="default" type="text" @click="$emit('retry')">重试</el-button>
</el-alert>
<el-skeleton v-else-if="loading" :rows="3" animated />
<el-empty v-else-if="!track || !track.rounds || !track.rounds.length"
          description="尚未提交审批" :image-size="64" />
<el-collapse v-else v-model="activeRounds">
  <el-collapse-item v-for="round in newestFirstRounds" :key="round.roundNo"
                    :name="round.roundNo" :title="roundTitle(round)">
    <el-timeline>
      <el-timeline-item v-for="node in round.nodes" :key="round.roundNo + '-' + node.nodeOrder"
                        :type="nodeType(node.state)" :timestamp="node.handledAt">
        <strong>{{ node.nodeName }}</strong>
        <div>{{ nodeStatusText(node) }}</div>
        <div v-if="node.candidateDisplayNames && node.candidateDisplayNames.length">
          候选审批人：{{ node.candidateDisplayNames.join('、') }}
        </div>
        <div v-if="node.actualApproverDisplayName">实际审批人：{{ node.actualApproverDisplayName }}</div>
        <div v-if="node.approvalModeText">审批方式：{{ node.approvalModeText }}</div>
        <div v-if="node.comment">审批意见：{{ node.comment }}</div>
      </el-timeline-item>
    </el-timeline>
  </el-collapse-item>
</el-collapse>
```

Use text plus color for states; latest round is expanded by default and historical rounds are collapsed.

- [ ] **Step 5: Integrate the processing page**

Under the status tag render `row.approvalSummary.summaryText`; use a tooltip containing all current candidates. In `openDetail`, reset track state and load detail plus track concurrently. Track failure sets `approvalTrackError=true` but still opens the detail dialog. Insert the shared component after main descriptions and rename the old divider to “业务履约进度”.

- [ ] **Step 6: Integrate the records page**

Use the same track-loading pattern and shared component. Completed/rejected/cancelled records must show their historical rounds without requiring approve permission.

- [ ] **Step 7: Run the focused test and full frontend tests**

```bash
cd erp-ui
node test/transferApprovalProgressUx.test.js
npm test
```

Expected: the focused test passes; the full suite has no new failures compared with the recorded worktree baseline.

- [ ] **Step 8: Commit the desktop slice**

```bash
git add erp-ui/src/api/inventory/transfer.js \
        erp-ui/src/views/inventory/transfer \
        erp-ui/test/transferApprovalProgressUx.test.js
git commit -m "feat: show transfer approval progress on desktop"
```

## Task 5: Mobile approval summaries and detailed sections

**Files:**

- Modify: `erp-ui/src/views/mobile/feature/featureService.js`
- Modify: `erp-ui/src/views/mobile/feature/featureMapper.js`
- Modify: `erp-ui/src/views/mobile/feature/components/mobileSheet.scss`
- Modify: `erp-ui/test/mobileFeatureMapper.test.js`
- Modify: `erp-ui/test/mobileBackendContract.test.js`

- [ ] **Step 1: Add failing mobile mapper tests**

Map a submitted transfer carrying `approvalSummary` and assert the list card includes the current node. Then map full detail with `approvalTrack` and assert the approval section precedes line items:

```javascript
const mapped = mapMobileFeatureRows("transfer", [{
  transferId: 91,
  orderNo: "TF-91",
  status: "submitted",
  approvalSummary: {
    summaryText: "待三级负责人审批",
    currentCandidateDisplayNames: ["张三", "李四"]
  },
  approvalTrack: {
    state: "in_progress",
    rounds: [{
      roundNo: 1,
      status: "in_progress",
      nodes: [{
        nodeOrder: 1,
        nodeName: "三级负责人",
        state: "current",
        candidateDisplayNames: ["张三", "李四"],
        approvalModeText: "任一人通过即可"
      }]
    }]
  },
  details: [{ productName: "茶叶", quantity: 2 }]
}])[0]

assert.ok(mapped.detail.includes("待三级负责人审批"))
assert.strictEqual(mapped._detailSections[0].title, "审批进度")
assert.ok(mapped._detailSections[0].rows[0].meta.includes("张三、李四"))
```

- [ ] **Step 2: Run mobile focused tests and confirm red**

```bash
cd erp-ui
node test/mobileFeatureMapper.test.js
node test/mobileBackendContract.test.js
```

Expected: FAIL because transfer details do not load or map approval tracks.

- [ ] **Step 3: Load transfer detail and track through one helper**

Import `getTransferApprovalTrack` and add:

```javascript
function resolveTransferDetailWithTrack(id) {
  return Promise.all([
    resolveDetail(getTransferDetail(id)),
    getTransferApprovalTrack(id).then(res => res.data || null).catch(() => ({ loadError: true, rounds: [] }))
  ]).then(([detail, approvalTrack]) => Object.assign({}, detail, { approvalTrack }))
}
```

Use it for `replenishment`, `transfer`, `transferApproval`, and `transferRecords`. Do not call the mobile approval-todo endpoint for sender progress.

- [ ] **Step 4: Add mobile list and detail mapping**

Append `approvalSummary.summaryText` and at most two candidate names to transfer-card detail. Add approval sections before ordinary detail rows. Node row format is exact:

```javascript
{
  title: String(node.nodeOrder || "-") + ". " + safeText(node.nodeName, "审批节点"),
  meta: node.actualApproverDisplayName
    ? "实际审批人：" + node.actualApproverDisplayName + formatHandledTime(node.handledAt)
    : "候选审批人：" + (node.candidateDisplayNames || []).join("、"),
  extra: joinDetail([node.approvalModeText, node.comment ? "意见：" + node.comment : ""]),
  value: approvalNodeStateLabel(node.state)
}
```

Produce one “审批进度” section for the latest round and one `历史审批 · 第 N 轮` section for every older round, newest first.

- [ ] **Step 5: Harden narrow-screen wrapping**

In `mobileSheet.scss`, keep the status value short and make long content wrap:

```scss
.detail-section-row strong,
.detail-section-row span,
.detail-section-row small {
  min-width: 0;
  overflow-wrap: anywhere;
  word-break: break-word;
}
```

At the existing narrow breakpoint, keep `.detail-section-row` single-column so names and comments never force horizontal scrolling.

- [ ] **Step 6: Run focused and full mobile/frontend tests**

```bash
cd erp-ui
node test/mobileFeatureMapper.test.js
node test/mobileBackendContract.test.js
npm test
```

Expected: focused tests pass; no new failures versus baseline.

- [ ] **Step 7: Commit the mobile slice**

```bash
git add erp-ui/src/views/mobile/feature \
        erp-ui/test/mobileFeatureMapper.test.js \
        erp-ui/test/mobileBackendContract.test.js
git commit -m "feat: show transfer approval progress on mobile"
```

## Task 6: End-to-end verification and delivery

**Files:**

- Modify only if verification finds a defect in files already listed above.
- Update checkboxes in this plan as tasks complete.

- [ ] **Step 1: Run all inventory tests**

```bash
mvn -pl erp-modules/erp-inventory -am \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: BUILD SUCCESS.

- [ ] **Step 2: Run all frontend tests**

```bash
cd erp-ui
npm test
```

Expected: no new failures compared with the baseline recorded before implementation.

- [ ] **Step 3: Run the production frontend build**

```bash
cd erp-ui
npm run build:prod
```

Expected: build completes successfully and produces `dist/` without compile errors.

- [ ] **Step 4: Verify API response privacy**

Use a unit-test serialization assertion or a local authenticated request and verify the approval-track JSON does not contain:

```text
candidateUserIds
approverId
taskId
postCode
ruleSnapshot
138xxxxxxxxx
```

Expected: none of these strings occur.

- [ ] **Step 5: Browser-check desktop and mobile layouts**

Verify one submitted, one approved, one rejected/resubmitted, and one cancelled order. Desktop must show a list summary and shared timeline. Mobile widths 320×568, 360×640, and 390px must show all names and comments without horizontal overflow.

- [ ] **Step 6: Review the final diff and commits**

```bash
git status --short
git diff --check
git log --oneline --max-count=8
```

Expected: only intended feature files are modified; no unrelated dirty-worktree files are included; whitespace check passes.

- [ ] **Step 7: Commit verification-only fixes if needed**

```bash
git add erp-modules/erp-inventory/src/main/java/com/erp/inventory \
        erp-modules/erp-inventory/src/main/resources/mapper/inventory \
        erp-modules/erp-inventory/src/test/java/com/erp/inventory \
        erp-ui/src/api/inventory/transfer.js \
        erp-ui/src/views/inventory/transfer \
        erp-ui/src/views/mobile/feature \
        erp-ui/test/transferApprovalProgressUx.test.js \
        erp-ui/test/mobileFeatureMapper.test.js \
        erp-ui/test/mobileBackendContract.test.js
git commit -m "fix: harden transfer approval progress"
```

Skip this commit when verification requires no corrections.
