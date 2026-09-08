# 手机网页端补全实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:executing-plans` or `superpowers:subagent-driven-development` to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**生成日期：** 2026-06-12  
**范围：** 现有 Vue 2 手机网页端 `/mobile/*`，不是原生 App 或小程序。  
**目标：** 让手机网页端从“能看列表、能点快捷动作”升级为“能独立完成门店、仓库、OA 的核心业务闭环”。

---

## 现状结论

现有移动端已经不是空壳：

- `/mobile/store`、`/mobile/warehouse`、`/mobile/inventory` 已有工作台页面。
- `/mobile/sales`、`/mobile/purchase`、`/mobile/stock`、`/mobile/stock-check`、`/mobile/transfer`、`/mobile/oa-purchase`、`/mobile/oa-todo` 等路由已经接到通用功能页。
- `erp-ui/src/views/mobile/feature/featureService.js` 已经复用库存、OA、系统模块的列表和详情接口。
- `erp-ui/src/views/mobile/feature/featureActionService.js` 已经接入提交、确认、收货、发货、审批、打卡、已读等动作。
- 后端库存和 OA 的 save/submit/list/detail/action 接口大部分已存在，首期不需要先做一套移动端专用后端。

关键缺口：

- 手机端只有客户、供应商、商品、分类、薪资方案、调拨规则等少量资料表单，销售、采购、OA 采购申请、退货、盘点、调拨没有完整手机表单。
- 多数库存变动动作只支持“全部收货”“全部发货”“收取第一批”，没有手机端数量录入、批次选择、备注和确认明细。
- OA 审批和调拨审批用固定意见，缺少审批意见输入，调拨也缺少驳回入口。
- 商品、客户、供应商、仓库、分类等选择还容易退化成手填 ID，不适合真实手机使用。
- `/mobile/mine` 暴露了系统管理、监控、定时任务等大量后台入口，和首期移动端定位不一致，应默认收敛。

---

## 分期策略

### P0：手机端业务可用闭环

目标是让普通员工、仓库人员、审批人能在手机浏览器完成日常动作。

- OA 采购申请：新建、保存草稿、提交、查看详情、待办审批、驳回、审批意见。
- 考勤：上班打卡、下班签退、我的打卡记录、异常提示。
- 库存查询：按当前店铺/仓库、商品关键词、库存状态查询，查看库存详情和流水。
- 采购入库：查看采购单、质检结果录入、按明细部分或全部收货。
- 销售出库：查看销售单、生成发货通知、按发货通知部分或全部出库。
- 盘点：创建盘点单、录入实盘数量、查看差异、确认或取消盘点。
- 调拨：查看调拨单、审批通过/驳回、填写意见、按明细出库、选择发货批次收货。
- 移动端默认导航：只保留工作台、库存/业务、OA、消息、我的；系统管理和监控只给管理员折叠展示。

### P1：手机端开单和资料维护

目标是让店长、采购、销售人员能在手机端创建核心单据。

- 销售单：新建、编辑草稿、商品明细、客户选择、保存草稿、保存并提交。
- 采购单：新建、编辑草稿、供应商选择、商品明细、保存草稿、保存并提交。
- 销售退货：从销售单或手动选择客户和商品创建，提交、确认、取消。
- 采购退货：从采购单或手动选择供应商和商品创建，提交、确认、取消。
- 客户、供应商、商品、分类：补齐选择器、必填校验、状态字典、避免手填 ID。
- 通知公告、个人资料、工资查询：按移动端卡片和详情页规范补齐。

### P2：增强能力和低频管理

目标是提高效率，但不阻塞首期业务上线。

- 商品导入导出在手机端只保留管理员入口，普通用户不显示。
- 调拨规则、薪资方案等配置项只作为管理员移动管理入口。
- 库存调整、批次/库位、扫码、PDA、物流单号、签收回单后续再做。
- 通用 workflow、钉钉同步、采购计划、经营报表不纳入本轮手机网页端计划。

---

## 前端实施结构

### Task 1：补齐移动端覆盖测试

- [x] 新增 `erp-ui/test/mobileFeatureRouteCoverage.test.js`。
- [x] 断言 P0 路由存在：`/mobile/oa-purchase`、`/mobile/oa-todo`、`/mobile/attendance`、`/mobile/stock`、`/mobile/purchase`、`/mobile/outbound`、`/mobile/stock-check`、`/mobile/transfer`。
- [x] 断言普通移动入口不把系统管理、代码生成、监控、定时任务作为默认一级入口。
- [x] 新增 `erp-ui/test/mobileFeatureFormConfig.test.js`。
- [x] 断言 P0/P1 表单配置覆盖 `oaPurchase`、`sales`、`purchase`、`salesReturn`、`purchaseReturn`、`stockCheck`、`transfer`。
- [x] 新增 `erp-ui/test/mobileFeatureActionPayloads.test.js`。
- [x] 断言收货、发货、盘点录入、调拨出库/收货、OA 审批、调拨审批都支持用户输入 payload，而不是只能走固定默认值。
- [x] 从 `erp-ui` 执行 `node test/mobileFeatureRouteCoverage.test.js`、`node test/mobileFeatureFormConfig.test.js`、`node test/mobileFeatureActionPayloads.test.js`。

### Task 2：拆分通用移动功能页

- [x] 保留 `erp-ui/src/views/mobile/feature/index.vue` 作为容器页，不继续把所有配置堆进单文件。
- [x] 新增 `erp-ui/src/views/mobile/feature/featureSearchConfigs.js`，迁移搜索字段配置。
- [x] 新增 `erp-ui/src/views/mobile/feature/mobileFormConfigs.js`，迁移并扩展手机表单配置。
- [x] 新增 `erp-ui/src/views/mobile/feature/mobileFormPayloads.js`，集中处理 save/submit payload 组装。
- [x] 新增 `erp-ui/src/views/mobile/feature/mobileValidation.js`，集中处理必填、数量、金额、上下文校验。
- [x] 新增 `erp-ui/src/views/mobile/feature/components/MobileDetailSheet.vue`。
- [x] 新增 `erp-ui/src/views/mobile/feature/components/MobileFormSheet.vue`。
- [x] 新增 `erp-ui/src/views/mobile/feature/components/MobileActionDialog.vue`。
- [x] 新增 `erp-ui/src/views/mobile/feature/components/MobileLineItemsEditor.vue`。
- [x] 新增 `erp-ui/src/views/mobile/feature/components/MobileEntityPicker.vue`。
- [x] 拆分后保持现有列表、详情、搜索、导出、资料表单行为不回退。

### Task 3：补齐选择器和明细编辑

- [x] `MobileEntityPicker` 支持商品、客户、供应商、分类、店铺/仓库选择。
- [x] 商品选择复用 `listProduct` 和 `categoryTree`，显示商品名称、编码、规格、单位、价格。
- [x] 客户选择复用 `listCustomer`，显示客户名称、联系人、电话、状态。
- [x] 供应商选择复用 `listSupplier` 和 `getSupplierProducts`，显示供应商名称、联系人、可供商品。
- [x] 店铺/仓库选择复用当前选择店铺逻辑和授权树，不允许选择公司或普通组织节点。
- [x] `MobileLineItemsEditor` 支持新增、删除、编辑商品行，字段包含商品、数量、单价、金额、备注。
- [x] 数量和金额在前端即时计算，提交前二次校验数量必须大于 0。
- [x] 普通业务表单不允许用户手填 `productId`、`customerId`、`supplierId`、`warehouseId` 等原始 ID。

### Task 4：P0 业务流补齐

- [x] OA 采购申请：
  - [x] 在 `mobileFormConfigs.js` 增加 `oaPurchase` 表单，字段包含标题、申请原因、期望到货日期、明细、备注。
  - [x] 在 `featureActionService.js` 接入 `savePurchase`、`submitPurchase`，支持保存草稿和保存并提交。
  - [x] `oaTodo` 审批动作打开 `MobileActionDialog`，要求输入审批意见，支持通过和驳回。
- [x] 采购入库：
  - [x] 接入质检弹窗，支持 `passed`、`failed`、备注。
  - [x] 收货动作从“全部收货”升级为明细数量确认，默认带入剩余可收数量，允许修改。
  - [x] 调用 `receivePurchase(orderId, { warehouseId, items })`。
- [x] 销售出库：
  - [x] 生成发货通知后自动刷新列表或跳转到 `/mobile/outbound`。
  - [x] 发货动作从“全部发货”升级为明细数量确认，默认带入剩余可发数量，允许修改。
  - [x] 调用 `deliverDeliveryNotice(noticeId, { warehouseId, items })`。
- [x] 盘点：
  - [x] 增加创建盘点单表单，调用 `createStockCheck`。
  - [x] 增加实盘数量录入，调用 `inputStockCheck(checkId, details)`。
  - [x] 确认盘点前展示账面数、实盘数、差异数。
- [x] 调拨：
  - [x] 审批动作支持通过、驳回、审批意见和可选 taskId。
  - [x] 调拨出库动作从“全部出库”升级为明细数量确认。
  - [x] 调拨收货动作支持选择待收货发货批次，不再固定第一批。
  - [x] 收货动作支持明细数量确认。

### Task 5：P1 开单和退货补齐

- [x] 销售单：
  - [x] 增加 `sales` 表单，字段包含客户、销售日期、仓库/门店、明细、备注。
  - [x] 保存草稿调用 `saveSales`，保存并提交调用 `submitSales`。
  - [x] 详情页支持编辑草稿、提交、取消、生成发货通知。
- [x] 采购单：
  - [x] 增加 `purchase` 表单，字段包含供应商、预计到货日期、仓库、明细、备注。
  - [x] 保存草稿调用 `savePurchase`，保存并提交调用 `submitPurchase`。
  - [x] 详情页支持编辑草稿、提交、质检、收货、取消。
- [x] 销售退货：
  - [x] 增加 `salesReturn` 表单，支持从销售单带出明细，也支持手动选择客户和商品。
  - [x] 保存草稿、提交、确认、取消复用现有 API。
- [x] 采购退货：
  - [x] 增加 `purchaseReturn` 表单，支持从采购单带出明细，也支持手动选择供应商和商品。
  - [x] 保存草稿、提交、确认、取消复用现有 API。
- [x] 客户、供应商、商品、分类：
  - [x] 将现有手机表单的 ID 输入改为选择器。
  - [x] 统一启停状态、合作状态、客户等级、单位等字典展示。

### Task 6：移动导航和权限收敛

- [x] 调整 `erp-ui/src/views/mobile/mobileNavigation.js`，按当前 `STORE` 或 `WAREHOUSE` 上下文展示不同入口。
- [x] 门店默认入口：销售开单、查库存、销售退货、补货/调拨申请、客户、商品、我的。
- [x] 仓库默认入口：采购入库、发货处理、采购退货、库存盘点、调拨处理、库存流水、供应商。
- [x] OA 默认入口：打卡、我的采购申请、审批待办、审批已办、工资、消息。
- [x] `/mobile/mine` 默认不展示系统用户、角色、菜单、参数、日志、定时任务、在线用户。
- [x] 管理员需要保留移动管理能力时，使用折叠的“后台管理”分组，并按权限控制显示。
- [x] 保持 `erp-ui/src/permission.js` 对手机端已选店铺/仓库上下文的强制校验。

### Task 7：后端补充原则

- [x] 首期优先复用现有接口，不先增加移动端专用 controller。
- [x] 只有当前端无法用现有 list/detail/tree 接口完成选择器时，再补轻量选项接口。
- [x] 本轮未触发新增后端接口；如后续需要，只允许补以下类型：
  - [ ] 商品、客户、供应商、仓库的精简 options 接口。
  - [ ] 手机工作台统计接口，返回待办数、低库存数、待收货数、待发货数。
  - [ ] 当前用户移动端权限/入口接口，避免前端硬编码管理员入口。
- [x] 不在本轮实现通用 workflow、钉钉同步、采购计划、报表分析。

### Task 8：验收和回归

- [x] 从 `erp-ui` 执行所有移动端 Node 测试。
- [x] 从 `erp-ui` 执行 `npm run build:prod`。
- [x] 如涉及后端新增或修改，执行对应模块 Maven 测试；若本机 Maven 仍使用 Java 25 导致 Mockito/Byte Buddy attach 失败，需要切换到 Java 17 后复测。（本轮未改后端，未触发）
- [x] 启动前端，在手机视口检查 375x812、390x844、430x932。（Browser/Chrome 插件不可用；已用 Codex bundled Playwright + Chromium 验证 `/mobile/sales?preview=1` 三个视口、滚动后列表不被底部导航遮挡，并抽查 P0 路由预览均渲染到移动功能页）
- [x] 新增动作运行时契约测试，使用 stub API 覆盖 OA 提交/审批、采购质检/收货、销售出库、盘点录入/确认、调拨审批/出库/批次收货，验证前端会把移动端输入 payload 传给正确 API。
- [ ] 验收路径必须覆盖：
  - [ ] 登录后选择店铺/仓库，再进入对应手机工作台。（需要真实后端服务、账号和业务数据）
  - [ ] 新建 OA 采购申请，保存草稿，提交。（前端表单和 payload 已实现，未跑真实后端端到端）
  - [ ] 审批待办填写意见，通过和驳回各走一次。（前端弹窗和 payload 已实现，未跑真实后端端到端）
  - [ ] 采购单质检，按明细部分收货。（前端弹窗和 payload 已实现，未跑真实后端端到端）
  - [ ] 销售单生成发货通知，按明细部分发货。（前端弹窗和 payload 已实现，未跑真实后端端到端）
  - [ ] 创建盘点单，录入实盘数量，确认盘点。（前端表单、录入弹窗和 payload 已实现，未跑真实后端端到端）
  - [ ] 调拨审批通过/驳回，调拨出库，选择批次收货。（前端弹窗、批次选择和 payload 已实现，未跑真实后端端到端）
  - [x] 手机端普通用户看不到系统管理、监控、定时任务默认入口。

---

## 完成标准

- 手机端核心动作不再只能使用固定默认文案或“全部处理”。
- 所有会影响库存或审批状态的动作都有确认、可读明细、必要备注和后端错误提示。
- 表单不要求普通用户手填原始数据库 ID。
- 门店和仓库上下文贯穿列表、详情、表单和动作。
- P0 路由在真实手机宽度下没有文字溢出、按钮遮挡、底部操作区遮挡明细的问题。
- 移动端首屏聚焦业务人员日常操作，后台管理能力不作为普通移动入口。
