# ERP 流程断裂型 UX 问题报告

> 当前版本只保留已在代码中验证的问题，剔除了采购、盘点、调拨按钮、OA 模块的误判项。

## 一、已证实问题

### 1.1 仓库下拉仍依赖全量仓库接口

**证据：**
- `SysDeptServiceImpl.selectWarehouseList(Long userId, boolean admin)` 直接返回 `deptMapper.selectWarehouseList()`。
- `WarehouseSelect.vue` 在 `loadWarehouses()` 中调用 `listWarehouseDept()`。

**风险：** 业务选择器先拿到全量仓库，再由前端按页面上下文过滤；接口层仍暴露全部启用仓库，且不同页面无法表达"发货仓库/要货来源仓库/当前仓库"的不同范围语义。

**修复方向：** `/system/dept/warehouse-list` 增加 `purpose` 和 `scopeDeptId`，由后端返回场景化仓库选项。

### 1.2 销售商品列表和保存校验范围不一致

**证据：**
- `InvProductServiceImpl.selectProductList()` 使用 `appendRelatedShopScope()`，范围为当前组织、祖先、子孙。
- `InvSalesServiceImpl.loadProducts()` 使用 `assertShopVisible()`，范围为当前组织、子孙。
- `InvPurchaseServiceImpl` 已使用 `assertRelatedShopVisible()`，采购不属于此问题。

**真实断裂：** 祖先组织共享商品在销售商品下拉中可见，但销售保存时被 `assertShopVisible()` 拒绝。

**修复方向：** 销售保存商品校验改为 `assertRelatedShopVisible()`，与商品下拉和采购保存一致。

### 1.3 调拨写操作缺少本地业务化错误处理

**证据：**
- `transfer/index.vue` 的保存、提交、审批、发货、收货、取消等写操作主要只有 `.then()` 或 `.finally()`。
- 发货/收货已有 `.finally()` 恢复 loading，但缺少本地 `.catch()` 给出当前动作语义。

**修复方向：** 调拨写接口支持 `silentError`，页面 `.catch()` 中展示"保存/提交/审批/发货/收货/取消"对应的业务错误提示。

## 二、已排除误判

| 原判断 | 当前结论 |
|---|---|
| 采购保存同样窄校验 | 采购已使用 `assertRelatedShopVisible()` |
| 盘点列表可见但详情无权 | 列表和详情都使用当前组织及子孙语义 |
| 发货通知页面直接调 `listWarehouseDept()` | 当前通过 `WarehouseSelect` 调用，但组件内部仍调接口 |
| 调拨按钮只判状态 | 当前 `canShip/canReceive` 已判上下文和来源/目标 |
| OA 列表详情范围不一致 | 当前列表和详情范围语义一致，暂不列入修复 |

## 三、实施优先级

| 优先级 | 修复项 |
|---|---|
| P0 | 仓库选项场景化接口 |
| P0 | 调拨写操作本地错误处理 |
| P1 | 销售商品范围对齐 |
