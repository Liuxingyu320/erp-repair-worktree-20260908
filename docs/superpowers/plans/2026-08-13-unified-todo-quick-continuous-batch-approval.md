# 统一待办快速、连续与批量审批详细实施方案

> 日期：2026-08-13
> 分支：`codex/consolidate-all-20260812`
> 核对基线：`2f5810124b6e589f25ab119129631170462d7dc0`
> 首发对象：当前生产主前端 `erp-ui` 的 PC 端“我的待办”
> 首发业务：门店要货/调拨审批 `INV_TRANSFER_APPROVAL`，同时覆盖旧调拨审批引擎 `LEGACY` 与统一审批引擎 `NATIVE`

## 一、结论

老板要的不是“某个账号代替所有人审批”，而是以下四件事：

1. 每个登录账号只看到当前分配给自己的审批，账号不同，待审批数量可以不同。
2. 在“我的待办”列表直接看到“查看审批”和“通过”：前者直接展示完整审批详情，后者只做紧凑确认。
3. 单条通过后留在同一筛选、同一页、同一位置，自动接上下一条。
4. 可以勾选当前页中符合条件的多条调拨审批，一次确认后批量通过，并清楚看到每条成功或失败的结果。

推荐按“单条快速通过与连续审批先上线，批量通过后开”的顺序交付。第一版批量不新增跨服务的大事务接口，而是复用现有逐条审批接口，由前端以最多 3 个并发、每项独立请求号、每项独立结果的方式执行。这样可以同时复用现有权限、候选人、组织范围、行锁、幂等和审批日志，不会因一条失败回滚已经合法完成的其他审批。

## 二、现状与问题定位

| 老板反馈 | 当前实现 | 本方案处理 |
| --- | --- | --- |
| 李总账号在电脑上批得方便，自己的账号很麻烦 | 后端本来就按当前登录用户候选关系返回待办；不同账号列表不同是正常的 | 不改变审批归属，只在页面明确显示“当前账号、待我审批” |
| 想在每个人账号下显示需要本人审批的全部事项 | 统一待办已按当前账号、候选人和授权组织筛选 | 保留当前安全口径，禁止改成管理员看全员审批池 |
| 每条最好直接点“通过” | `erp-ui/src/views/workbench/todo/index.vue` 当前整行只有“查看审批” | 把整行重构为独立复选框、完整详情入口和紧凑通过按钮 |
| 通过一条后再找其他单很麻烦 | `erp-ui/src/views/inventory/transfer/index.vue` 审批成功后关闭详情并刷新调拨业务列表 | 从待办进入时自动返回原待办，或直接在待办内完成，不再回调拨列表找单 |
| 希望批量通过 | 当前旧引擎和统一审批引擎都只有逐条接口 | 首发由前端安全编排逐条接口，返回部分成功结果；不做跨模块全有或全无事务 |
| 截图仍有“当前组织/全部授权组织”范围开关 | 当前分支 2026-08-12 23:51 的源码已移除该旧开关 | 发布验收必须核对构建 commit 和缓存，防止仍在使用旧静态资源 |

现有安全能力可以复用：

- 旧调拨审批会锁定调拨单和审批任务，并重新校验当前用户、审批节点、禁止自审、组织可见范围和权限。
- 旧调拨写命令通过 `X-Request-Id` 做持久幂等，重复请求不会重复执行同一审批。
- 统一审批通过 `requestId`、任务候选关系、权限快照、乐观锁和动作日志做幂等与并发保护。
- 统一待办的原生审批行已有 `approvalTaskId`，旧调拨审批可由 `transferId + 当前用户` 在服务端锁定当前任务。
- 待办行已有服务端选出的 `contextDeptId`，可作为单次请求的组织上下文；服务端仍会重新验证该组织是否属于当前用户授权范围。

## 三、首发范围与明确不做

### 3.1 首发范围

- PC 端 `erp-ui` 的“我的待办”。
- 仅 `INV_TRANSFER_APPROVAL` 显示“通过”和批量勾选。
- 同时支持 `LEGACY` 与 `NATIVE` 调拨审批。
- 同组织与跨授权门店/仓库待办。
- 单条快速通过、连续处理下一条、详情审批后自动返回、当前页批量通过。
- 通过意见选填；拒绝、驳回、退回继续要求原因。
- 缓存旧数据、权限已变化、任务已处理等异常的明确结果与刷新。

### 3.2 首发明确不做

- 不允许一个账号查看或审批其他人的专属待办。
- 不做“全选全部 48 条”或跨页全选；只选当前已加载页面，单批最多 20 条。
- 不批量驳回、不批量退回；这些动作必须逐单说明原因。
- 不把 OA、报销、盘点、HR 等异构审批同时开放批量；后续逐类型评审后加入白名单。
- 不做无确认的静默单击即通过。快速通过仍有紧凑确认；连续模式下每条都由用户主动点击“通过并下一条”。
- 不在一批审批外面包数据库总事务；每条审批独立成功或失败。
- 不把尚未成为生产主入口的 `erp-ui-next` 作为本次上线阻断项；它在主前端验收后做一致性适配。

## 四、目标交互

### 4.1 页面头部

页面应明确显示：

```text
当前账号：王总    待我审批：48
仅显示分配给当前账号且当前仍可处理的事项
```

账号不同，数量不同是正确结果；不得用“两个账号列表必须一样”作为验收标准。

### 4.2 待办行

当前整行是一个覆盖式 `<button>`，不能在里面再嵌套复选框和按钮。必须调整为语义独立的控件：

```text
[ ]  门店要货待审批：TF202608050001    紧急
     主仓库 → 苏州龙之梦 | 等待 7 天 | 调拨审批
                                      [查看审批] [通过]
```

规则：

- 对可快速处理的调拨审批，“查看审批”在当前页直接打开完整审批详情；其他待办继续使用现有深链与临时组织租约能力。
- “通过”只在服务端提供了明确引擎信息、当前页面不是缓存旧数据、用户仍有要求权限时显示。
- 缓存行不允许快速或批量审批，只显示“缓存数据，请刷新后处理”。
- 复选框只出现在可批量通过的调拨审批上。
- 行正文不再是包含所有控件的大按钮，避免嵌套交互元素和键盘操作冲突。

### 4.3 单条快速通过

点击“通过”后在当前页打开紧凑确认框，不切换全局门店，不进入调拨列表：

- 单号、调出组织、调入组织。
- 申请数量、参考总价；没有成本查看权限时沿用现有脱敏结果。
- 提交人、提交时间、当前审批节点。
- 审批意见选填。
- `取消`、`确认通过`、`通过并处理下一条`。不再提供二次“查看完整详情”入口；完整信息由列表中的“查看审批”直接打开。

确认框打开时，用该待办行的 `contextDeptId` 发起一次只针对该请求的调拨详情查询。查询失败或任务状态已变化时，不允许继续提交，先刷新待办。

### 4.4 连续审批

选择“通过并处理下一条”后：

1. 当前按钮进入提交中，防止重复点击。
2. 只有收到服务端权威成功响应后，才从列表移除当前行。
3. 保留来源、关键词、优先级、页码和滚动位置。
4. 自动载入同一筛选下下一条可快速审批的调拨待办。
5. 焦点落到下一条的“通过并处理下一条”，同时用 `aria-live` 宣告上一条结果。
6. 没有下一条时提示“当前筛选下可审批事项已处理完”。

如果用户先进入了完整详情页再审批，成功后也必须返回原待办路由、恢复原组织和筛选，并优先定位进入前记录的下一条待办；不得继续停在调拨业务列表。

### 4.5 批量通过

勾选后显示固定操作条：

```text
已选 12 条（当前页，最多 20 条）      [取消选择] [批量通过]
```

批量确认框展示选中单号和调拨方向，并明确说明“每条独立审批，部分失败不会撤销其他已成功审批”。确认后：

- 使用选中时的不可变快照，不能边执行边改变选中范围。
- 最多 3 条并发，避免瞬时打满调拨或审批服务。
- 每项有独立稳定请求号，同一项超时重试复用原请求号。
- 登录失效时立即停止派发未开始项；已在途项按响应收口。
- 进度显示“已处理 7/12”。
- 完成后只刷新一次待办，避免 12 次写请求触发 12 次列表刷新。
- 成功项消失；失败项保留并显示安全、可理解的原因。

结果示例：

```text
12 条中：成功 9，未成功 3
- TF...005：已被其他人处理，请刷新
- TF...009：审批权限已变化
- TF...011：网络结果未知，已刷新状态；仍在待办中才可重试
```

## 五、必须保持的安全与一致性不变量

- [ ] 页面展示的是当前登录账号自己的可执行待办，不扩大为全员审批池。
- [ ] 前端的“可通过”只决定是否展示按钮；服务端在每次写请求中仍重新验证状态、候选人、权限、禁止自审和组织范围。
- [ ] 行内审批不得调用 `setSelectedDept`，不得触发 `erp:dept-changed`，不得改变用户当前选中的门店/仓库。
- [ ] 单次请求携带的 `contextDeptId` 只能作为待校验输入；服务端现有 `resolveAndValidateShopDept` 和调拨可见范围仍是权威判断。
- [ ] 旧引擎每项必须带稳定 `X-Request-Id`；统一审批每项必须带稳定请求体 `requestId`。
- [ ] 网络超时不得直接显示成功，也不得换新请求号盲目重试；先用原请求号重试或刷新权威待办状态。
- [ ] 未收到成功响应前不得乐观删除待办，只能禁用按钮并显示处理中。
- [ ] 批量是部分成功模型，不允许把 20 条放进一个跨业务总事务。
- [ ] 缓存旧行、总数未知行中的过期数据不得快速审批。
- [ ] 通过意见选填；拒绝/驳回/退回原因仍必填且不进入批量能力。
- [ ] 任何失败结果不显示 SQL、内部类名或服务堆栈，只展示稳定业务文案和可供排查的请求标识。
- [ ] “查看审批”完整详情入口必须始终保留，作为高风险或信息不足单据的安全核对路径。

## 六、技术方案

### 6.1 由待办提供方明确审批引擎

不要让前端长期依靠“有没有 `approvalTaskId`”猜引擎。两个待办提供方在现有 `routeParams` 中增加向后兼容字段：

```json
{
  "approvalEngine": "LEGACY"
}
```

或：

```json
{
  "approvalEngine": "NATIVE",
  "approvalTaskId": "12345",
  "approvalInstanceId": "67890"
}
```

旧客户端会忽略新增字段；新客户端据此选择执行器。首发快速能力仍采用前端业务白名单，只对白名单中的 `INV_TRANSFER_APPROVAL` 开放，服务端继续做最终授权。

### 6.2 单次请求组织上下文，不切全局组织

为 Axios 请求配置增加内部字段 `inventoryDeptId`：

```js
getTransferDetail(transferId, { inventoryDeptId: row.contextDeptId })
approveTransfer(payload, {
  inventoryDeptId: row.contextDeptId,
  headers: { 'X-Request-Id': requestId }
})
```

请求拦截器只对当前请求写入 `Dept-NumId`，随后删除内部配置字段。没有显式覆盖时，继续使用当前全局组织，保持所有旧调用兼容。显式组织也必须是正整数且只用于 `/inventory/` 请求。

这比临时调用 `setSelectedDept` 安全：后者会发出全局组织变化事件，使待办页和其他业务页重新加载，也会破坏滚动、筛选和连续审批。

### 6.3 统一前端审批执行器

新增纯逻辑注册表 `todoApprovalActions.js`，职责仅包括：

- 判定当前行是否允许快速/批量通过。
- 按 `approvalEngine` 选择旧调拨或统一审批 API。
- 生成并保留稳定请求号。
- 传递每项独立组织上下文。
- 按 HTTP 状态、稳定业务码和刷新后的权威待办状态，将结果归一为 `SUCCESS`、`ALREADY_HANDLED`、`FORBIDDEN`、`CONFLICT`、`UNKNOWN`；没有稳定码时不得只靠易变中文文案猜测，统一落到 `UNKNOWN` 并刷新。
- 提供限并发批处理执行器；默认并发 3，最大项数 20。

旧引擎调用现有：

```text
POST /inventory/transfer/approve
Dept-NumId: <该待办服务端返回的 contextDeptId>
X-Request-Id: TODOB:<批次标识>:<项标识>

{
  "transferId": 1001,
  "action": "approve",
  "comment": ""
}
```

统一审批调用现有：

```text
POST /approval/tasks/{approvalTaskId}/approve

{
  "requestId": "TODOB:<批次标识>:<项标识>",
  "reason": ""
}
```

首发不新增服务端批量接口。原因是两个动作属于不同微服务/模块和不同事务边界，而现有单项接口已经拥有需要的授权、锁和幂等。若上线数据证明 20 条限并发调用仍无法满足性能，再单独设计两个服务各自的批量接口，不能直接引入跨服务总事务。

### 6.4 请求号与重试

- 快速单条：`TODOQ:<随机会话标识>:<稳定项标识>`。
- 批量：先生成一次 `batchId`，再为每项生成固定请求号；对话框关闭前重试必须复用。
- 请求号只包含字母、数字、点、下划线、冒号或连字符，长度不超过 128，满足旧调拨命令约束。
- 不把手机号、姓名、单据金额等敏感数据写入请求号。
- 网络结果未知时先刷新待办；行仍存在才允许用原请求号重试。

### 6.5 列表刷新收敛

当前响应拦截器会对每次审批写请求触发待办失效。批量调用需要增加内部配置 `suppressTodoMutationRefresh`：

- 单条正常调用维持当前自动失效。
- 批量每项关闭自动失效。
- 批量全部收口后统一执行一次 `todo/invalidateAfterMutation` 和一次当前页强制刷新。
- 用户取消批量时，已经成功的在途请求仍按权威结果计入，未开始项标记取消。

### 6.6 从详情审批后返回

新增短期 `sessionStorage` 返回上下文，记录：

- 原待办 `fullPath`。
- 当前 `todoKey` 和打开时算出的 `nextTodoKey`。
- 原滚动位置。
- 创建/过期时间，建议 30 分钟。

打开详情前写入，导航失败时清除；审批成功后读取并 `router.replace` 回原待办。原有 `todoContextLease` 负责恢复组织，新的返回上下文负责恢复待办位置，二者职责不可混用。返回上下文只接受仓库内部的待办路由，禁止把任意 URL 当返回地址。

### 6.7 批量结果模型

前端内存中的每项结果至少包含：

```js
{
  todoKey,
  businessNo,
  engine,
  requestId,
  status,       // PENDING | RUNNING | SUCCESS | FAILED | UNKNOWN | CANCELED
  errorKind,    // ALREADY_HANDLED | FORBIDDEN | CONFLICT | NETWORK | SESSION
  message
}
```

成功与失败均按原选择顺序显示。页面刷新后不依赖此内存结果作为业务真相；权威真相仍是服务端待办和审批日志。

## 七、实施任务与文件清单

实施按以下顺序进行。前一阶段未通过验收，不开启后一阶段功能开关。

```text
G0 基线与部署指纹
  └─ A 引擎契约和单请求组织上下文
       └─ B 单条快速通过
            ├─ C 连续审批与详情返回
            └─ D 当前页批量通过
                 └─ E 灰度、验收和 erp-ui-next 后续适配
```

### Task G0：冻结基线并先补失败测试

**Inspect/Verify:**

- `erp-ui/src/views/workbench/todo/index.vue`
- `erp-ui/src/views/inventory/transfer/index.vue`
- `erp-ui/src/utils/todoNavigator.js`
- `erp-ui/src/utils/todoContextLease.js`
- `erp-ui/src/utils/request.js`
- `erp-modules/erp-inventory/src/main/java/com/erp/inventory/service/impl/InvTransferApprovalServiceImpl.java`
- `erp-modules/erp-approval/src/main/java/com/erp/approval/service/ApprovalTaskService.java`

**要求：**

- [ ] 保存当前 `git status --short`、后端完整测试、前端测试和生产构建结果。
- [ ] 先写会因当前实现而失败的目标行为测试：待办行应有独立详情/通过控件；详情审批成功应返回待办；批量结束应只刷新一次；显式单请求组织上下文应优先于全局组织且不改变全局状态。
- [ ] 在测试环境打开老板截图对应页面，记录页面显示的 build commit；若仍显示已移除的旧范围开关，先解决静态资源/CDN/浏览器缓存，不在旧包上继续验收。

**验收：**能区分“当前基线已有失败”和“本任务新增失败”，禁止通过删除测试或放宽断言制造绿色。

### Task A：补齐引擎契约与单请求组织上下文

**Modify:**

- `erp-modules/erp-inventory/src/main/java/com/erp/inventory/service/impl/InvTodoServiceImpl.java`
- `erp-modules/erp-inventory/src/test/java/com/erp/inventory/service/impl/InvTodoServiceImplTest.java`
- `erp-modules/erp-approval/src/main/java/com/erp/approval/service/ApprovalTodoService.java`
- `erp-modules/erp-approval/src/test/java/com/erp/approval/service/ApprovalTodoServiceTest.java`
- `erp-ui/src/utils/request.js`
- `erp-ui/src/api/inventory/transfer.js`
- `erp-ui/src/api/approval/task.js`
- `erp-ui/test/unifiedTodoMutationRefresh.test.js`

**实现要求：**

- [ ] 旧调拨待办 `routeParams.approvalEngine = LEGACY`。
- [ ] 原生调拨待办 `routeParams.approvalEngine = NATIVE`，保留现有 task/instance ID。
- [ ] Axios 支持只对本请求使用 `inventoryDeptId`，不调用或修改全局 `setSelectedDept`。
- [ ] 显式 `inventoryDeptId` 必须校验为正整数，且不能覆盖非 inventory 请求。
- [ ] `approveTransfer` 接受可选请求配置，并能显式发送稳定 `X-Request-Id`。
- [ ] 统一审批 API 接受可选请求配置，供批量关闭逐项待办刷新。
- [ ] 响应拦截器识别 `suppressTodoMutationRefresh`，默认行为保持不变。

**验收：**

- 旧客户端忽略新字段仍可正常打开详情。
- 快速详情和审批请求使用行内组织，当前页面组织、名称和租约都没有变化。
- 人工伪造未授权部门 ID 仍被服务端拒绝。

### Task B：实现单条快速通过

**Create:**

- `erp-ui/src/utils/todoApprovalActions.js`
- `erp-ui/src/views/workbench/todo/components/TodoQuickApproveDialog.vue`
- `erp-ui/test/unifiedTodoApprovalActions.test.js`

**Modify:**

- `erp-ui/src/views/workbench/todo/index.vue`
- `erp-ui/src/views/mobile/feature/mobileActionPayloads.js`
- `erp-ui/test/unifiedTodoDesktop.test.js`
- `erp-ui/test/desktopTransferApprovalActions.test.js`

**实现要求：**

- [ ] 把覆盖整行的按钮拆成语义独立的详情、通过和选择控件，禁止嵌套按钮。
- [ ] 仅在 `type=INV_TRANSFER_APPROVAL`、引擎有效、权限有效、上下文有效、来源非缓存时显示快速通过。
- [ ] 点击后用单请求组织上下文加载详情预览；加载完成前不能提交。
- [ ] 通过意见选填；共享 payload 规则改为只对拒绝/驳回/退回要求原因，并补齐 PC、移动端回归测试。
- [ ] 统一审批必须使用精确 `approvalTaskId`；旧审批继续由服务端在锁内寻找当前用户任务。
- [ ] 每次动作生成一次请求号并保存在对话框状态，重复点击、超时重试不换号。
- [ ] 成功后先记录结果，再强制刷新当前页；失败不移除行。
- [ ] 提交期间禁用当前行相关按钮，其他行仍可查看但不能再启动同一单据动作。
- [ ] 详情金额继续走现有成本脱敏策略，不在待办层自行拼成本数据。

**验收：**从待办行到权威审批成功最多为“点击通过 + 确认”两步，不发生路由切换或全局组织变化。

### Task C：连续审批和详情审批后返回

**Create:**

- `erp-ui/src/utils/todoActionReturn.js`
- `erp-ui/test/unifiedTodoActionReturn.test.js`

**Modify:**

- `erp-ui/src/views/workbench/todo/index.vue`
- `erp-ui/src/utils/todoNavigator.js`
- `erp-ui/src/mixins/todoBusinessFocus.js`
- `erp-ui/src/views/inventory/transfer/index.vue`
- `erp-ui/test/unifiedTodoNavigator.test.js`
- `erp-ui/test/unifiedTodoContextLease.test.js`
- `erp-ui/test/desktopTransferApprovalActions.test.js`

**实现要求：**

- [ ] 打开详情前保存受限返回上下文，包括原 fullPath、当前/下一 todoKey 和滚动位置。
- [ ] 导航失败、上下文无效或超过 30 分钟时安全清理。
- [ ] 从待办进入的调拨审批成功后返回原待办；普通从调拨菜单进入时仍保持当前业务页行为。
- [ ] 返回待办时让现有组织租约完成恢复，不能自己再写一套组织恢复逻辑。
- [ ] 行内选择“通过并处理下一条”时，对话框直接加载下一条；没有下一条时正确结束。
- [ ] 下一条失效时跳过并刷新，不能继续使用旧预览。
- [ ] 用 DOM `ref` 或 `data-todo-key` 恢复焦点；键盘用户可以继续按 Tab/Enter 操作。
- [ ] 页末最后一条处理后，若当前页变空且页码大于 1，回到合法页码；总数未知时不得根据估算总数错误跳页。

**验收：**连续通过 5 条不需要重新输入关键词、不需要返回调拨列表、不需要手工寻找下一条，且原组织上下文最终恢复。

### Task D：实现当前页批量通过

**Create:**

- `erp-ui/src/views/workbench/todo/components/TodoBatchApproveDialog.vue`
- `erp-ui/test/unifiedTodoBatchApproval.test.js`

**Modify:**

- `erp-ui/src/utils/todoApprovalActions.js`
- `erp-ui/src/views/workbench/todo/index.vue`
- `erp-ui/src/utils/request.js`
- `erp-ui/test/unifiedTodoDesktop.test.js`
- `erp-ui/test/unifiedTodoMutationRefresh.test.js`

**实现要求：**

- [ ] 只允许选择当前页的合格调拨审批；翻页、改筛选、切来源或刷新时清空选择。
- [ ] 最大 20 条，超过时阻止确认并解释限制。
- [ ] 确认时冻结待办快照和每项请求号。
- [ ] 限并发为 3；执行器不得使用无限 `Promise.all`。
- [ ] `LEGACY` 和 `NATIVE` 在用户无感的情况下走各自单项接口，可以混在同一批结果中。
- [ ] 每项独立收口；一项 403/409/业务冲突不停止其他项，只有会话失效才停止未派发项。
- [ ] 批量项关闭逐请求待办刷新，全部完成后只做一次失效和一次当前页刷新。
- [ ] 结果未知时刷新权威状态：已不在待办中显示“服务端已处理”；仍存在才保留“可重试”。
- [ ] 成功项清除，失败项保留；失败重试复用原请求号。
- [ ] 批量确认只支持 approve；任何 reject/return action 都由执行器拒绝。
- [ ] 关闭结果框后清理含请求号和单据摘要的内存状态，不写 localStorage。

**验收：**构造 12 条中 9 成功、1 已处理、1 权限变化、1 网络未知，页面必须准确显示 9/3，成功不重复、失败不消失、最终列表与服务端一致。

### Task E：功能开关、灰度和发布门禁

**Modify:**

- `erp-ui/src/settings.js`
- `erp-ui/.env.development`
- `erp-ui/.env.staging`
- `erp-ui/.env.production`
- `erp-ui/scripts/validate-release-build.cjs`
- `erp-ui/test/unifiedTodoReleaseGate.test.js`

**实现要求：**

- [ ] 增加独立开关 `VUE_APP_TODO_QUICK_APPROVE_ENABLED` 和 `VUE_APP_TODO_BATCH_APPROVE_ENABLED`。
- [ ] 两个开关值只能为显式 `true/false`；生产构建不得因拼写错误默认为开启。
- [ ] 先开快速通过，批量保持关闭；快速通过验收后再开批量。
- [ ] 开关关闭时原“查看审批”路径完整可用，不影响普通业务页审批。
- [ ] 发布信息必须显示本次 build commit，防止老板继续命中旧包。
- [ ] CDN/反向代理发布后清理旧 HTML 缓存，静态资源继续使用带 hash 文件名。

**验收：**无需回滚后端或数据库，仅关闭前端开关并重新发布即可恢复原交互；现有详情审批仍可用。

### Task F：`erp-ui-next` 后续一致性适配，不阻断本次生产

当前 `erp-ui-next` 的真实网关尚未成为生产主链路。老前端完成验收后，再把同一业务口径移植到：

- `erp-ui-next/src/pages/tasks/MobileTasksPage.vue`
- `erp-ui-next/src/pages/inventory/transfer/TransferApprovalPage.vue`
- `erp-ui-next/src/domains/inventory/transfer/transfer-approval.http.ts`

要求复用同样的引擎字段、请求号、部分成功语义和批量上限，但不能为了“代码看起来统一”反向改坏已验证的 `erp-ui` 发布。

## 八、测试与验收矩阵

### 8.1 自动化测试场景

| 维度 | 必测场景 |
| --- | --- |
| 账号 | 王总账号、李总账号分别只看到自己的候选任务；列表可以不同 |
| 引擎 | `LEGACY` 单条/连续/批量；`NATIVE` 单条/连续/批量；同一批混合两引擎 |
| 组织 | 当前组织内、跨授权门店、跨授权仓库、伪造未授权组织 |
| 状态 | 正常待审批、已被别人处理、已撤回、已拒绝、节点已变化 |
| 权限 | 正常权限、审批过程中撤权、禁止自审、候选关系变化 |
| 幂等 | 双击、浏览器超时、CSRF 恢复重放、同请求号重复、不同请求复用同请求号 |
| 批量 | 1 条、20 条、超过 20、部分成功、会话中途失效、用户停止未开始项 |
| 页面 | 保留筛选/页码/滚动；最后一条；总数未知；缓存提供方；返回后下一条失效 |
| 可访问性 | Tab 顺序、Enter/Space、焦点恢复、屏幕阅读器状态播报、无嵌套按钮 |
| 隐私 | 无成本权限时金额脱敏；错误不泄露内部异常；请求号不含个人敏感信息 |

### 8.2 推荐验证命令

后端聚焦测试：

```bash
./mvnw -pl erp-modules/erp-inventory,erp-modules/erp-approval -am \
  -Dtest='InvTodoServiceImplTest,ApprovalTodoServiceTest,InvTransferApprovalServiceImplTest,ApprovalTaskServiceIdempotencyTest,ApprovalTaskServiceConcurrencyTest' \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

后端完整模块测试：

```bash
./mvnw -pl erp-modules/erp-inventory,erp-modules/erp-approval -am test
```

前端：

```bash
cd erp-ui
npm run test
npm run build:prod
```

`build:prod` 必须在正式发布环境注入现有 build commit、build time 等发布元数据，不得通过放宽 `validate-release-build.cjs` 绕过门禁。

`erp-ui-next` 后续适配完成后再运行：

```bash
cd erp-ui-next
npm run verify
```

## 九、人工 UAT 脚本

1. 用王总账号登录，记录“待我审批”数量、当前组织和 build commit。
2. 用李总账号登录，确认只显示李总自己的任务；数量无需与王总相同。
3. 王总账号选一条旧引擎跨门店调拨，点击“通过”，确认当前全局组织没有变化，审批成功后下一条获得焦点。
4. 选一条统一审批调拨，重复相同流程，检查请求中使用精确 `approvalTaskId`。
5. 从“查看审批”进入后通过，确认自动回到原筛选和滚动位置，原组织恢复。
6. 连续处理 5 条，期间不使用搜索、不返回调拨列表。
7. 选择当前页 10 条混合引擎待办，人工让其中 1 条在另一会话先处理，再执行批量，核对部分成功结果。
8. 在批量执行中制造一次网络超时，确认同一项不换请求号，刷新后不重复审批。
9. 尝试选择缓存旧行、其他审批类型和第 21 条，均应被阻止。
10. 查看审批日志/命令记录，按 `TODOQ:` 或 `TODOB:` 请求号可以关联到每项动作。
11. 硬刷新、退出重登和另一个浏览器验证新静态资源；截图中旧范围开关不得继续出现。

## 十、发布顺序、观测和回滚

### 10.1 发布顺序

1. 测试环境：两个开关都开，跑完整自动化和 UAT。
2. 生产第一阶段：仅打开快速通过和详情返回，批量关闭；先给老板和李总实际使用一轮。
3. 观察至少一个完整业务高峰：错误率、409/权限变化、重复请求、平均审批耗时、待办刷新失败。
4. 生产第二阶段：打开批量，通过上限保持 20、并发保持 3。
5. 一周内不扩大到其他审批类型；先收集真实失败原因和是否需要服务端批量接口的证据。

### 10.2 需要记录的证据

- 前端 build commit 和启用的两个功能开关。
- 单条和批量共同请求号，服务日志只记录标识和结果，不记录敏感详情。
- 旧引擎命令表的幂等重放结果。
- 统一审批动作日志的幂等和候选校验结果。
- 批量成功数、失败数、按错误类型聚合；不要把单据金额写入前端埋点。

### 10.3 回滚

- 首选：关闭批量开关，保留快速通过。
- 次选：关闭快速通过开关，恢复纯“查看审批”。
- 后端只增加向后兼容的 `approvalEngine` 路由字段，无数据库迁移，无需回滚数据。
- 已成功的审批是合法业务事实，不因功能回滚而撤销。
- 不使用 `git reset --hard`、数据库回写或人工改审批状态来“回滚”用户已经完成的审批。

## 十一、主要风险与对应措施

| 风险 | 处理 |
| --- | --- |
| 跨组织行内审批误切全局门店 | 使用单请求 `inventoryDeptId`，禁止调用 `setSelectedDept`，并测试无 `erp:dept-changed` |
| 批量产生刷新风暴 | 每项设置 `suppressTodoMutationRefresh`，批次末只刷新一次 |
| 网络超时导致用户重复通过 | 每项稳定请求号；未知结果先刷新，重试复用原请求号 |
| UI 误判可审批 | UI 只做展示白名单，服务端每项重新校验并锁定 |
| 某项失败拖垮整批 | 限并发、每项独立结果、部分成功；只有登录失效停止未开始项 |
| 用户没有看内容就批量通过 | 仅调拨类型、当前页、最多 20、一次明确确认，并保留“查看审批”完整详情入口 |
| 旧静态资源导致“明明改了但老板没看到” | build commit 门禁、HTML 缓存清理、双账号硬刷新验收 |
| 以后将能力泛化到高风险审批 | 默认关闭；每个业务类型单独做信息充分性和批量资格评审后才进白名单 |

## 十二、工作量建议

按一名熟悉当前项目的开发估算：

- 基线、契约和单请求组织上下文：0.5～1 天。
- 单条快速通过与桌面交互：1～1.5 天。
- 连续审批和详情返回：1 天。
- 批量执行、部分成功和可访问性：1～1.5 天。
- 自动化回归、双账号 UAT、生产灰度：1 天。

合计约 4.5～6 个工作日。可以在第 2～3 天先交付“快速通过 + 审批后返回”，先解决老板最痛的操作；批量在后续 2～3 天完成并单独开关上线。

## 十三、完成定义

- [ ] 王总和李总分别登录时，只看到各自当前可审批任务。
- [ ] 调拨待办行同时有独立“查看审批”和“通过”，无嵌套交互控件。
- [ ] 单条通过不切换全局组织，不跳业务列表，下一条自动接续。
- [ ] 从详情页通过后返回原待办、原筛选、原滚动并恢复原组织。
- [ ] 当前页最多 20 条可批量通过，旧/新引擎混合时结果仍逐项准确。
- [ ] 双击、超时和重试不产生重复审批。
- [ ] 缓存、撤权、已处理、自审禁止、节点变化都 fail closed。
- [ ] 批量完成只触发一次待办刷新，成功项消失、失败项保留。
- [ ] 前后端自动化、生产构建和双账号 UAT 全部通过。
- [ ] 生产页面显示正确 build commit，老板截图中的旧范围界面不再命中。
