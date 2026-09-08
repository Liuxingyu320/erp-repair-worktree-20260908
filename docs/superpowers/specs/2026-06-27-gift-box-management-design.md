# 礼盒管理独立模块设计

## 背景

系统现有商品管理使用 `inv_product`、`inv_product_category`、`inv_stock`，并在“仓库管理”和“进销存管理”中复用部分库存页面。礼盒不是现有商品的组合装，也不需要独立供应商体系，因此礼盒应作为独立业务对象存在，避免混入商品表后影响商品选择、商品库存、采购、销售和报表。

## 目标

- 新增独立礼盒分类、礼盒档案、礼盒库存管理。
- 供应商继续使用现有供应商档案，不新增礼盒供应商表。
- 仓库管理中放置：礼盒分类、礼盒管理、礼盒库存管理。
- 进销存管理中放置：礼盒库存管理。
- 礼盒数据与商品数据隔离，商品页面和商品选择器不自动出现礼盒。
- 礼盒分类和礼盒档案作为共享目录数据，由仓库管理入口维护；礼盒库存作为组织库存数据，按具体仓库或店铺隔离。

## 非目标

- 第一期不接入礼盒采购单、销售单、调拨单、发货通知和库存流水页面。
- 第一期不做“礼盒由商品组成”的套装拆分或扣减商品库存。
- 第一期不把礼盒加入现有商品报表。
- 第一期不新建礼盒供应商体系。

## 数据模型

### `inv_gift_category`

礼盒分类表，只服务礼盒模块。

主要字段：

- `category_id`：主键。
- `parent_id`、`ancestors`：分类树。
- `category_name`：分类名称。
- `category_code`：分类编码。
- `order_num`：排序。
- `catalog_dept_id`：目录归属组织。第一期按当前选中仓库的集团祖先归属，作为集团共享礼盒分类。
- `status`：`0` 正常，`1` 停用。
- `del_flag`：`0` 存在，`2` 删除。
- `create_by`、`create_time`、`update_by`、`update_time`、`remark`。

分类编码沿用项目默认分类编码规则：按分类名称取拼音大写首字母；重复时使用小写；再重复时追加数字。

### `inv_gift_box`

礼盒档案表，对应用户给出的礼盒字段。

主要字段：

- `gift_id`：主键。
- `gift_code`：礼盒编码，自动生成。
- `category_id`：礼盒分类 ID。
- `gift_name`：产品名称。
- `grade`：等级。
- `spec`：规格。
- `product_description`：产品描述。
- `replenishment_unit`：补货单位。
- `guide_price_1`：指导售价 1。
- `guide_price_2`：指导售价 2。
- `image_url`：礼盒图片 URL。
- `catalog_dept_id`：目录归属组织。与礼盒分类一致，作为集团共享礼盒档案。
- `status`：`0` 正常，`1` 停用。
- `del_flag`：`0` 存在，`2` 删除。
- `create_by`、`create_time`、`update_by`、`update_time`、`remark`。

导入导出的业务列为：

`序号、上线分类、产品名称、等级、规格、产品描述、补货单位、指导售价1、指导售价2、备注、礼盒图片`

其中 `序号` 只用于导入导出展示，不入库；`上线分类` 匹配 `inv_gift_category.category_name`；`礼盒图片` 导入为图片 URL，页面新增/编辑时通过现有文件上传能力写入 `image_url`。

礼盒编码格式：

`LH-分类编码-6位数字`

示例：

- `LH-HC-483920`
- `LH-GH-027361`

生成逻辑使用现有商品编码工具的随机 6 位数字策略，但在 `inv_gift_box` 内做唯一性检查。

### `inv_gift_stock`

礼盒库存表，独立于 `inv_stock`。

主要字段：

- `stock_id`：主键。
- `gift_id`：礼盒 ID。
- `owner_dept_id`：库存所属组织。仓库入口中必须是仓库部门；进销存入口中必须是店铺部门。
- `current_quantity`：当前库存。
- `locked_quantity`：锁定库存，第一期保留字段，默认 `0`。
- `available_quantity`：可用库存，第一期等于当前库存减锁定库存。
- `cost_price`：成本价。
- `total_cost`：库存总成本。
- `version`：乐观锁版本。
- `last_in_time`、`last_out_time`：最后入库/出库时间。
- `create_by`、`create_time`、`update_by`、`update_time`、`remark`。

唯一约束：

- `gift_id + owner_dept_id`

库存归属规则：

- 仓库管理 / 礼盒库存管理：只允许查看和调整当前仓库组织的礼盒库存。
- 进销存管理 / 礼盒库存管理：只允许查看和调整当前店铺组织的礼盒库存。

## 菜单与权限

### 仓库管理

新增菜单：

- `礼盒分类`
  - 路径建议：`gift-category`
  - 组件建议：`inventory/gift/category/index`
  - 权限：`inv:giftCategory:list`
  - 仅允许在当前选中组织为 `WAREHOUSE` 时维护，接口必须校验部门类型。

- `礼盒管理`
  - 路径建议：`gift`
  - 组件建议：`inventory/gift/index`
  - 权限：`inv:gift:list`
  - 仅允许在当前选中组织为 `WAREHOUSE` 时维护，接口必须校验部门类型。

- `礼盒库存管理`
  - 路径建议：`gift-stock`
  - 组件建议：`inventory/gift/stock/index`
  - 权限：`inv:giftStock:list`
  - 路由元信息或菜单归属用于标记 `context=warehouse`
  - 仅允许在当前选中组织为 `WAREHOUSE` 时查看和调整库存。

### 进销存管理

新增菜单：

- `礼盒库存管理`
  - 路径建议：`gift-stock`
  - 组件建议：`inventory/gift/stock/index`
  - 权限：`inv:giftStock:list`
  - 路由元信息或菜单归属用于标记 `context=store`
  - 仅允许在当前选中组织为 `STORE` 时查看和调整库存。

权限点：

- `inv:giftCategory:list`
- `inv:giftCategory:query`
- `inv:giftCategory:add`
- `inv:giftCategory:edit`
- `inv:giftCategory:remove`
- `inv:gift:list`
- `inv:gift:query`
- `inv:gift:add`
- `inv:gift:edit`
- `inv:gift:remove`
- `inv:gift:import`
- `inv:gift:export`
- `inv:giftStock:list`
- `inv:giftStock:query`
- `inv:giftStock:adjust`
- `inv:giftStock:export`

## 后端接口

### 礼盒分类

控制器建议：`InvGiftCategoryController`

- `GET /gift/category/tree`
- `GET /gift/category/list`
- `GET /gift/category/{categoryId}`
- `POST /gift/category`
- `POST /gift/category/update`
- `DELETE /gift/category/{categoryIds}`

行为：

- 礼盒分类维护必须调用仓库上下文校验，当前选中组织不是 `WAREHOUSE` 时拒绝。
- 新增分类时通过当前仓库解析集团共享目录归属，写入 `catalog_dept_id`。
- 列表和树只返回当前仓库所在集团的共享礼盒分类。
- 删除分类前检查分类下是否存在子分类或礼盒档案。
- 停用分类后，不允许新增或编辑礼盒到该分类。
- 分类树缓存使用独立 key，避免和商品分类缓存混用。

### 礼盒档案

控制器建议：`InvGiftBoxController`

- `GET /gift/list`
- `GET /gift/{giftId}`
- `POST /gift`
- `POST /gift/update`
- `DELETE /gift/{giftIds}`
- `POST /gift/importData`
- `POST /gift/importTemplate`
- `POST /gift/export`

行为：

- 礼盒档案维护必须调用仓库上下文校验，当前选中组织不是 `WAREHOUSE` 时拒绝。
- 新增礼盒时通过当前仓库解析集团共享目录归属，写入 `catalog_dept_id`。
- 列表只返回当前仓库所在集团的共享礼盒档案。
- 新增礼盒必须选择有效礼盒分类。
- 礼盒名称、分类、规格组合用于导入更新时识别同一礼盒。
- 礼盒删除前检查是否存在非零库存；存在非零库存时不允许删除。
- 导出格式按用户给出的 11 列输出。

### 礼盒库存

控制器建议：`InvGiftStockController`

- `GET /gift/stock/list`
- `GET /gift/stock/{stockId}`
- `POST /gift/stock/adjust`
- `POST /gift/stock/export`

调整请求字段：

- `giftId`
- `ownerDeptId`：可不传；服务端以当前选中组织作为库存归属。若前端传入，必须等于当前选中组织。
- `adjustQuantity`
- `costPrice`
- `reason`
- `context`：`warehouse` 或 `store`

行为：

- 仓库入口只能调整仓库部门库存。
- 进销存入口只能调整店铺部门库存。
- 列表和调整必须根据 `context` 调用对应部门类型校验：`warehouse` 调用仓库上下文，`store` 调用店铺上下文。
- 礼盒选择范围来自当前选中组织所在集团的共享礼盒档案。
- 调整后库存不能小于 `locked_quantity`，也不能小于 0。
- 使用 `version` 做乐观锁，避免并发覆盖。
- 第一期库存调整通过操作日志和库存记录备注追踪；不新增独立礼盒库存流水页面。

## 前端页面

### `inventory/gift/category/index.vue`

基于现有商品分类管理模式实现：

- 分类树或表格。
- 新增、编辑、停用、删除。
- 自动生成分类编码，编码展示为只读。

### `inventory/gift/index.vue`

基于现有商品管理页面简化实现：

- 左侧礼盒分类树。
- 右侧礼盒列表、筛选、统计。
- 搜索项：产品名称、礼盒编码、分类、等级、状态。
- 表格列：礼盒编码、产品名称、上线分类、等级、规格、补货单位、指导售价1、指导售价2、状态。
- 详情抽屉展示产品描述、备注、礼盒图片。
- 新增/编辑表单按礼盒字段组织。
- 导入、导出、下载模板。

### `inventory/gift/stock/index.vue`

同一页面复用两个菜单入口：

- 仓库管理入口传入或识别 `context=warehouse`。
- 进销存管理入口传入或识别 `context=store`。

页面能力：

- 搜索项：礼盒名称、礼盒编码、分类、库存状态。
- 表格列：礼盒编码、产品名称、分类、规格、补货单位、当前库存、锁定库存、可用库存、成本价、总成本、所属组织。
- 操作：详情、库存调整、导出。
- 仓库入口标题显示“仓库礼盒库存管理”。
- 进销存入口标题显示“店铺礼盒库存管理”。

## 部门架构和权限

现有部门表 `sys_dept` 使用 `dept_type` 区分 `GROUP`、`COMPANY`、`STORE`、`WAREHOUSE`。库存模块已有 `InvBaseService` 可校验当前用户是否能选择某个组织，并提供 `requireWarehouseContext`、`requireStoreContext`、`assertDeptType` 等方法。礼盒模块必须沿用这些后端校验，不能只依赖前端菜单隐藏。

设计规则：

- `GROUP`、`COMPANY` 是组织结构节点，不直接承载礼盒库存。
- `WAREHOUSE` 可以维护礼盒分类、礼盒档案和仓库礼盒库存。
- `STORE` 只能管理本店礼盒库存，不允许维护礼盒分类和礼盒档案。
- 礼盒分类和礼盒档案是共享目录数据，保存到当前选中仓库的集团祖先 `catalog_dept_id`。这样仓库维护后，同集团下店铺库存页面可以选择和查看同一批礼盒。
- 礼盒库存是组织库存数据，保存到具体 `owner_dept_id`，该字段必须是 `STORE` 或 `WAREHOUSE`。
- 仓库管理 / 礼盒库存管理：`context=warehouse`，后端必须校验当前选中组织是 `WAREHOUSE`，且 `owner_dept_id` 等于当前选中仓库。
- 进销存管理 / 礼盒库存管理：`context=store`，后端必须校验当前选中组织是 `STORE`，且 `owner_dept_id` 等于当前选中店铺。
- 库存调整以当前选中组织作为准入边界，不允许通过请求参数调整其它仓库或店铺的礼盒库存。
- 用户即使在 `sys_user_shop` 中存在 COMPANY 记录，礼盒库存接口也不能把 COMPANY 当作库存组织；接口必须按 `dept_type` 二次校验。
- 管理员如果没有选择具体 `STORE` 或 `WAREHOUSE`，不能执行库存调整；列表可以要求先选择组织，保持和现有库存管理一致。

需要新增或复用的部门范围查询：

- 复用 `countUserShopScope` 校验用户是否能选择当前组织。
- 复用 `selectDeptTypeById` 判断 `STORE/WAREHOUSE`。
- 新增一个“解析集团目录归属”的查询或服务方法：从当前选中 `STORE/WAREHOUSE` 的 `ancestors` 中找到最近的 `GROUP` 祖先，作为 `catalog_dept_id`。当前数据结构下通常是 `100`。

## 导入导出

礼盒导入模板和导出文件列顺序固定为：

`序号、上线分类、产品名称、等级、规格、产品描述、补货单位、指导售价1、指导售价2、备注、礼盒图片`

导入规则：

- 空白分类报错。
- 不存在的分类自动创建礼盒分类，使用默认分类编码规则。
- 同分类、同产品名称、同规格视为同一礼盒；勾选更新时覆盖档案字段。
- 图片列按 URL 保存。

导出规则：

- 按分类和礼盒 ID 排序。
- 可复用商品导出的采购表样式，但标题改为“礼盒资料表”。
- 不导出内部 ID。

## 错误处理

- 分类不存在或停用：提示“礼盒分类不存在或已停用”。
- 礼盒重复：提示“同分类、同规格下已存在该礼盒”。
- 删除存在库存的礼盒：提示“礼盒存在库存，不能删除”。
- 库存调整为负：提示“调整后库存不能为负数”。
- 无组织权限：提示“无权操作该组织礼盒库存”。
- 错误消息应包含导入行号，便于批量导入修正。

## 测试

后端测试：

- 礼盒分类编码生成和重复处理。
- 礼盒新增、编辑、删除校验。
- 礼盒导入：新增、更新、错误行提示。
- 礼盒库存调整：增加、减少、不可为负、不可低于锁定库存。
- 仓库/店铺上下文权限隔离。
- 库存调整请求传入其它 `ownerDeptId` 时被拒绝。
- Mapper SQL 绑定测试。

前端测试：

- 仓库菜单能进入礼盒分类、礼盒管理、礼盒库存管理。
- 进销存菜单只出现礼盒库存管理。
- 礼盒管理表单字段与导入导出字段一致。
- 礼盒库存页面在仓库和店铺入口标题、筛选和权限表现正确。

## 实施顺序

1. 新增数据库迁移 SQL：礼盒分类、礼盒档案、礼盒库存、菜单和权限。
2. 新增后端 domain、mapper、service、controller。
3. 新增礼盒分类页面。
4. 新增礼盒管理页面。
5. 新增礼盒库存管理页面，并按菜单入口区分仓库/店铺上下文。
6. 实现导入、导出、模板下载和图片上传字段。
7. 补齐后端和前端测试。
8. 本地打包重启库存模块，手工验证菜单、CRUD、导入导出和库存调整。
