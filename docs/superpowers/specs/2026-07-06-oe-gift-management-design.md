# OE 器皿与礼盒管理设计

## 背景

项目已有商品管理、商品分类、库存、采购、销售和固定资产维修模块。用户提供的 `春茶礼盒2026定版.xlsx` 包含两张业务表：

- `OE配置表`：OE 器皿资料，字段包括上线分类、产品类别名称、物品名称、产品编号、产品描述、订货单位、成本价、供应商名称、手机、图片。
- `春茶礼盒`：礼盒资料，字段包括上线分类、产品名称、等级、规格、产品描述、补货单位、指导售价1、指导售价2、备注、礼盒图片。

OE 器皿和礼盒都不是库存商品，第一期不参与入库、出库、盘点、库存报表、采购单、销售单和调拨单。OE 器皿需要和 OA 固定资产配置、固定资产维修上报关联；礼盒只作为资料档案维护和查询。

## 目标

- 新增独立的 OE 分类和 OE 器皿档案管理，页面体验与商品管理保持一致。
- 新增独立的礼盒分类和礼盒档案管理，页面体验与商品管理保持一致。
- 仓库管理提供 OE/礼盒维护入口；进销存管理提供 OE/礼盒只读查询入口。
- 固定资产配置从“选择商品”改为“只选择 OE 器皿”。
- 固定资产维修上报只能选择当前店铺已配置的 OE 固定资产。
- OE 供应商复用现有商品管理供应商档案，不新增 OE 供应商表。
- 进销存只读查询可以查看 OE 成本价、礼盒指导售价，用于门店提成核对。
- 历史固定资产配置、额度、维修数据按用户确认的 C 方案处理：备份后清空重建。

## 非目标

- 不把 OE 器皿或礼盒写入 `inv_product`。
- 不为 OE 器皿或礼盒新增库存表、库存调整、库存流水、入库、出库、盘点能力。
- 不让 OE 器皿或礼盒出现在现有商品选择器、采购、销售、库存、盘点页面。
- 不新增礼盒供应商、礼盒成本价或礼盒进价字段。
- 不迁移历史固定资产商品到 OE 档案；旧固定资产业务数据备份后清空。

## 模块边界

### 商品管理

商品管理保持现状。现有 `inv_product`、`inv_product_category`、`inv_stock` 和相关业务单据不接收 OE 或礼盒数据。

### OE 管理

OE 是独立档案，不承载库存。仓库管理负责维护 OE 分类和 OE 器皿资料；进销存管理只提供 OE 资料查询。固定资产配置和维修上报引用 OE 器皿。

### 礼盒管理

礼盒是独立档案，不承载库存。仓库管理负责维护礼盒分类和礼盒资料；进销存管理只提供礼盒资料查询。

### 固定资产管理

固定资产配置仍归 OA 固定资产模块管理，但资产选择来源改为 OE 器皿。维修上报仍使用现有额度、异常批准、确认上报流程，但资产字段改为 OE。

## 数据模型

### `inv_oe_category`

OE 分类表，结构与商品分类保持一致但独立存储。

- `category_id`：主键。
- `parent_id`：父分类 ID。
- `ancestors`：祖级列表。
- `category_name`：分类名称，对应 Excel `上线分类`。
- `category_code`：分类编码，按商品分类编码规则生成。
- `order_num`：排序。
- `status`：`0` 正常，`1` 停用。
- `del_flag`：`0` 存在，`2` 删除。
- `create_by`、`create_time`、`update_by`、`update_time`、`remark`。

### `inv_oe_item`

OE 器皿档案表。

- `oe_item_id`：主键。
- `oe_item_code`：OE 编码，对应 Excel `产品编号`；为空时自动生成。
- `category_id`：OE 分类 ID。
- `oe_type_name`：产品类别名称。
- `oe_item_name`：物品名称。
- `item_description`：产品描述。
- `order_unit`：订货单位。
- `cost_price`：成本价。
- `supplier_name`：供应商名称，来自 `inv_supplier`。
- `supplier_phone`：供应商联系电话，来自 `inv_supplier.contact_phone`。
- `image_url`：图片 URL。
- `status`：`0` 正常，`1` 停用。
- `del_flag`：`0` 存在，`2` 删除。
- `keyword`：查询属性，不入库。
- `create_by`、`create_time`、`update_by`、`update_time`、`remark`。

OE 去重规则：

- 有 `oe_item_code` 时优先按编码识别。
- 无编码时按 `category_id + oe_type_name + oe_item_name + item_description` 识别。

OE 编码规则：

- Excel 有 `产品编号` 时保留。
- Excel 无编号或手工新增时，按分类编码生成，格式建议 `OE-分类编码-6位数字`，并在 `inv_oe_item` 内做唯一性检查。

### `inv_gift_category`

礼盒分类表，结构与 OE 分类一致但独立存储。

- `category_id`：主键。
- `parent_id`、`ancestors`、`category_name`、`category_code`、`order_num`。
- `status`、`del_flag`。
- `create_by`、`create_time`、`update_by`、`update_time`、`remark`。

### `inv_gift_box`

礼盒档案表。

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
- `status`：`0` 正常，`1` 停用。
- `del_flag`：`0` 存在，`2` 删除。
- `keyword`：查询属性，不入库。
- `create_by`、`create_time`、`update_by`、`update_time`、`remark`。

礼盒去重规则：

- 按 `category_id + gift_name + grade + spec` 识别同一礼盒。

礼盒编码规则：

- 自动生成，格式建议 `LH-分类编码-6位数字`，并在 `inv_gift_box` 内做唯一性检查。

### 固定资产表改造

历史数据先备份再清空，然后改造字段。

`oa_fixed_asset_config`：

- 将 `product_id` 重命名或迁移为 `oe_item_id`，迁移后业务代码不再读写 `product_id`。
- 列表和导出中展示 `oe_item_code`、`oe_item_name`。
- 资产数量、资产单价、资产金额、年度申报比例、状态等现有字段保持。

`oa_fixed_asset_repair`：

- 将 `product_id` 重命名或迁移为 `oe_item_id`，将 `product_name` 重命名或迁移为 `oe_item_name`，迁移后业务代码不再读写商品字段。
- 维修金额、可用额度快照、故障说明、图片附件、异常批准、状态、申请人和批准人字段保持。

## 供应商规则

OE 供应商必须复用现有供应商管理：

- 不新增 OE 供应商表。
- 手工新增/编辑 OE 时，供应商用选择器选择 `inv_supplier` 里的有效供应商。
- 选中供应商后带出 `supplier_name` 和 `contact_phone`，写入 OE 档案快照字段。
- Excel 导入时，`供应商名称` 按现有供应商档案匹配。
- Excel `手机` 列只作为辅助核对，不覆盖供应商档案手机号。
- 供应商名称存在且有效时，写入标准供应商名称和联系电话。
- 供应商名称不存在、停用或非合作中时，导入该行失败，提示先维护供应商。
- 供应商为空时允许导入为“待完善 OE”，后续编辑时再补供应商。

礼盒没有供应商字段，第一期不关联供应商。

## 菜单和权限

### 仓库管理维护入口

仓库管理下新增四个维护菜单：

- `OE分类`
  - 组件：`inventory/oe/category/index`
  - 权限：`inv:oeCategory:list`、`inv:oeCategory:tree`、`inv:oeCategory:query`、`inv:oeCategory:add`、`inv:oeCategory:edit`、`inv:oeCategory:remove`
- `OE管理`
  - 组件：`inventory/oe/index`
  - 权限：`inv:oe:list`、`inv:oe:query`、`inv:oe:add`、`inv:oe:edit`、`inv:oe:remove`、`inv:oe:import`、`inv:oe:export`、`inv:oe:template`
- `礼盒分类`
  - 组件：`inventory/gift/category/index`
  - 权限：`inv:giftCategory:list`、`inv:giftCategory:tree`、`inv:giftCategory:query`、`inv:giftCategory:add`、`inv:giftCategory:edit`、`inv:giftCategory:remove`
- `礼盒管理`
  - 组件：`inventory/gift/index`
  - 权限：`inv:gift:list`、`inv:gift:query`、`inv:gift:add`、`inv:gift:edit`、`inv:gift:remove`、`inv:gift:import`、`inv:gift:export`、`inv:gift:template`

### 进销存只读入口

进销存管理下新增两个只读查询菜单：

- `OE资料查询`
  - 复用组件：`inventory/oe/index`
  - 路由或菜单 query：`mode=readonly`
  - 菜单权限：`inv:oe:list`
  - 功能权限：`inv:oe:query`、`inv:oe:export`、`inv:oeCategory:tree`
- `礼盒资料查询`
  - 复用组件：`inventory/gift/index`
  - 路由或菜单 query：`mode=readonly`
  - 菜单权限：`inv:gift:list`
  - 功能权限：`inv:gift:query`、`inv:gift:export`、`inv:giftCategory:tree`

只读入口行为：

- 保留搜索、分类筛选、详情、导出。
- 隐藏新增、编辑、删除、导入、下载模板。
- 分类树只作为筛选，不提供分类维护操作。
- 页面标题显示 `OE资料查询` / `礼盒资料查询`。
- OE 查询页展示 `成本价`、供应商名称、供应商电话、图片、描述。
- 礼盒查询页展示 `指导售价1`、`指导售价2`、图片、描述。
- 这些价格字段对查询角色可见，不受商品成本权限 `inv:cost:view` 限制。

### 固定资产权限

固定资产权限沿用现有权限点：

- `oa:fixedAsset:config:*` 控制固定资产配置和异常批准。
- `oa:fixedAsset:repair:*` 控制维修上报和确认。

固定资产配置和维修的数据边界仍按店铺权限控制，用户只能操作有权限的店铺。

## 页面设计

### OE 分类页

基于商品分类页实现：

- 分类树表格。
- 搜索：分类名称、编码、状态。
- 新增根分类、新增子类、编辑、删除、启停。
- 分类编码保存后自动生成，只读展示。

### OE 管理页

基于商品管理页实现：

- 左侧 OE 分类树。
- 右侧筛选、统计、表格。
- 搜索项：物品名称、产品编号、产品类别名称、供应商、状态。
- 表格列：OE 编码、物品名称、上线分类、产品类别名称、订货单位、成本价、供应商、供应商电话、状态。
- 详情抽屉展示产品描述、图片、备注。
- 新增/编辑抽屉按 Excel 字段组织。
- 维护模式提供新增、编辑、删除、导入、导出、下载模板。
- 只读模式只提供列表、筛选、详情、导出。

### 礼盒分类页

基于商品分类页实现：

- 分类树表格。
- 搜索：分类名称、编码、状态。
- 新增根分类、新增子类、编辑、删除、启停。
- 分类编码保存后自动生成，只读展示。

### 礼盒管理页

基于商品管理页实现：

- 左侧礼盒分类树。
- 右侧筛选、统计、表格。
- 搜索项：产品名称、礼盒编码、等级、规格、状态。
- 表格列：礼盒编码、产品名称、上线分类、等级、规格、补货单位、指导售价1、指导售价2、状态。
- 详情抽屉展示产品描述、备注、礼盒图片。
- 新增/编辑抽屉按 Excel 字段组织。
- 维护模式提供新增、编辑、删除、导入、导出、下载模板。
- 只读模式只提供列表、筛选、详情、导出。

### 固定资产配置页

保留现有页面入口和额度展示结构，调整资产选择：

- `固定资产商品` 文案改为 `OE器皿` 或 `固定资产器皿`。
- 搜索商品接口改为搜索 OE 接口。
- 保存字段从 `productId` 改为 `oeItemId`。
- 选择 OE 后带出 OE 编码、名称、成本价，默认填入资产单价。
- 额度计算继续使用 `asset_amount`。

### 固定资产维修上报页

保留现有页面入口和额度流程，调整资产选择：

- `固定资产` 选项来自当前店铺已配置且启用的 OE 固定资产。
- 提交维修时保存 `oeItemId` 和 `oeItemName` 快照。
- 维修列表和详情展示 OE 器皿名称。

## 移动端与苹果网页端设计

项目当前不是 Android、iPhone 各写一套原生页面，而是 `erp-ui` 的 Vue 移动端运行时统一承载，再通过 Capacitor 打包 Android。`erp-ui/capacitor.config.ts` 已配置 `webDir: 'dist'`、Android/iOS 统一 `ERP-Mobile-App` user agent，iOS 也启用了 `scrollEnabled`。所以本期移动端方案是：

- Android 手机端、苹果网页端、未来 iOS WebView 均复用同一套 `erp-ui/src/views/mobile` 页面。
- 不新增原生 Android Activity 或 iOS ViewController。
- 后续只需要保证 Vue 移动端在 Android WebView 和 iPhone Safari/WebView 下布局、弹层、上传和安全区表现一致。

### 移动端入口

现有移动端由 `mobileRouteDefinitions.js`、`mobileNavigation.js` 和 `feature/*` 配置驱动。新增 OE/礼盒时按现有商品资料入口扩展：

- 在 `erp-ui/src/views/mobile/mobileRouteDefinitions.js` 新增：
  - `/mobile/oe`，`featureKey: 'oe'`，标题 `OE资料查询`。
  - `/mobile/gift`，`featureKey: 'gift'`，标题 `礼盒资料查询`。
- 在 `erp-ui/src/views/mobile/mobileNavigation.js` 新增权限：
  - `oe: ['inv:oe:list']`
  - `gift: ['inv:gift:list']`
- 店铺工作台和仓库工作台都在 `商品资料` 附近增加 `OE资料`、`礼盒资料` 快捷入口。
- 底部导航保持现有工作台、销售/入库、库存、我的结构，不额外塞 OE/礼盒，避免底部导航过载。
- OE/礼盒移动端只做查询、详情、导出，不提供新增、编辑、删除、导入、模板下载。

### 移动端列表和详情

`erp-ui/src/views/mobile/feature/featureService.js` 新增接口接入：

- `listOe`、`getOe` 来自 `@/api/inventory/oe`。
- `listGift`、`getGift` 来自 `@/api/inventory/gift`。
- `fetchMobileFeatureData('oe')` 调 `listOe(query)`。
- `fetchMobileFeatureData('gift')` 调 `listGift(query)`。
- `fetchMobileFeatureDetail('oe')` 调 `getOe(oeItemId)`。
- `fetchMobileFeatureDetail('gift')` 调 `getGift(giftId)`。

OE/礼盒是资料档案，不跟当前门店或仓库库存上下文绑定，所以不加入 `CONTEXT_SCOPED_FEATURES`。页面是否能打开由移动端路由权限和后端接口权限共同控制。

`erp-ui/src/views/mobile/feature/featureMapper.js` 新增映射：

- `mapOeRow`：
  - 标题：`oeItemName`
  - 编码：`oeItemCode`
  - 摘要：`categoryName`、`oeTypeName`、`supplierName`
  - 状态：沿用 `0 正常 / 1 停用`
  - 详情字段：OE 编码、上线分类、产品类别名称、订货单位、成本价、供应商、供应商电话、产品描述、图片、状态
- `mapGiftRow`：
  - 标题：`giftName`
  - 编码：`giftCode`
  - 摘要：`categoryName`、`grade`、`spec`
  - 状态：沿用 `0 正常 / 1 停用`
  - 详情字段：礼盒编码、上线分类、等级、规格、补货单位、指导售价1、指导售价2、产品描述、礼盒图片、备注、状态
- `resolveActionId` 增加：
  - `oe: ['oeItemId', 'id']`
  - `gift: ['giftId', 'id']`

价格展示规则：

- OE 的 `成本价` 在 OE 移动端列表和详情可见。
- 礼盒的 `指导售价1`、`指导售价2` 在礼盒移动端列表和详情可见。
- 这两个移动端查询页不复用商品成本权限 `inv:cost:view`，只要用户有 `inv:oe:list` 或 `inv:gift:list` 就能看到对应价格。

### 移动端搜索和导出

`erp-ui/src/views/mobile/feature/featureSearchConfigs.js` 增加：

- OE 搜索：关键词、OE 编码、物品名称、产品类别名称、供应商、状态。
- 礼盒搜索：关键词、礼盒编码、产品名称、等级、规格、状态。

`erp-ui/src/views/mobile/feature/mobileExportConfigs.js` 增加：

- OE 导出：`/inventory/oe/export`，权限 `inv:oe:export`，文件名 `OE资料`。
- 礼盒导出：`/inventory/gift/export`，权限 `inv:gift:export`，文件名 `礼盒资料`。

### 移动端固定资产维修

现有移动端固定资产维修表单在 `mobileFormConfigs.js` 中使用 `productId + entity: 'product'`。本期要改为只选择已配置 OE 固定资产：

- 表单字段改为 `oeItemId`。
- 文案改为 `固定资产器皿` 或 `OE器皿`。
- entity 改为 `fixedAssetOe`。
- `mobileEntityService.js` 新增 `fixedAssetOe`：
  - 调用现有固定资产配置列表接口。
  - 查询条件带当前店铺 `shopDeptId` 和启用状态。
  - 选项值为 `oeItemId`，展示 `oeItemName`，副标题展示 `oeItemCode`、资产数量、资产金额或成本价。
- `mobileFormPayloads.js` 提交维修时写 `oeItemId`，不再写 `productId`。
- `featureMapper.js` 固定资产维修列表和详情优先展示 `oeItemName`、`oeItemCode`，不再以商品名作为主字段。

维修上报仍只允许店铺上下文进入，仓库上下文不开放维修上报。

### Android 与苹果网页端验收

Android 验收：

- 构建 Vue 后同步 Capacitor Android 包。
- 在 Android WebView 内验证 `/mobile/oe`、`/mobile/gift`、`/mobile/fixed-asset-repair`。
- 检查列表滚动、搜索弹层、详情弹层、图片上传、相机拍照、导出触发、价格字段可见。

苹果网页端验收：

- 用 iPhone Safari 或 iOS WebView 打开同一移动端 URL。
- 检查底部导航安全区、详情弹层高度、实体选择器弹层、图片上传、软键盘顶起、滚动回弹。
- 重点确认 OE 成本价、礼盒指导售价在列表和详情里不被隐藏。

## 后端接口

### OE 分类

- `GET /oe/category/tree`
- `GET /oe/category/{categoryId}`
- `POST /oe/category`
- `POST /oe/category/update`
- `DELETE /oe/category/{categoryIds}`

网关前缀保持现有模块风格，前端调用为 `/inventory/oe/category/...`。

### OE 档案

- `GET /oe/list`
- `GET /oe/{oeItemId}`
- `POST /oe`
- `POST /oe/update`
- `DELETE /oe/{oeItemIds}`
- `POST /oe/importData`
- `POST /oe/importTemplate`
- `POST /oe/export`

前端调用为 `/inventory/oe/...`。

### 礼盒分类

- `GET /gift/category/tree`
- `GET /gift/category/{categoryId}`
- `POST /gift/category`
- `POST /gift/category/update`
- `DELETE /gift/category/{categoryIds}`

前端调用为 `/inventory/gift/category/...`。

### 礼盒档案

- `GET /gift/list`
- `GET /gift/{giftId}`
- `POST /gift`
- `POST /gift/update`
- `DELETE /gift/{giftIds}`
- `POST /gift/importData`
- `POST /gift/importTemplate`
- `POST /gift/export`

前端调用为 `/inventory/gift/...`。

### 固定资产

固定资产接口路径保持：

- `/oa/fixedAsset/config/...`
- `/oa/fixedAsset/repair/...`

接口字段改为 OE 字段：

- 入参：`oeItemId`。
- 出参：`oeItemId`、`oeItemCode`、`oeItemName`。
- 兼容历史 `productId` 的逻辑不保留，因为历史数据按清空重建处理。

## Excel 导入导出

### OE 导入

来源 sheet：`OE配置表`。

字段映射：

- `上线分类` -> `inv_oe_category.category_name`
- `产品类别名称` -> `oe_type_name`
- `物品名称` -> `oe_item_name`
- `产品编号` -> `oe_item_code`
- `产品描述` -> `item_description`
- `订货单位` -> `order_unit`
- `成本价` -> `cost_price`
- `供应商名称` -> `supplier_name`
- `手机` -> 仅核对，不覆盖供应商档案
- `图片` -> `image_url`

导入规则：

- 合并或空白单元格按上一行向下继承，适配 Excel 的分类和类别写法。
- 分类不存在时自动创建 OE 分类。
- 有产品编号时按产品编号匹配更新。
- 无产品编号时按自然键匹配更新。
- 图片列如果是 URL，保存到 `image_url`。
- 图片列如果是嵌入图片，第一期提示人工上传，不自动抽取。
- 导入错误必须包含 Excel 行号。

### 礼盒导入

来源 sheet：`春茶礼盒`。

字段映射：

- `上线分类` -> `inv_gift_category.category_name`
- `产品名称` -> `gift_name`
- `等级` -> `grade`
- `规格` -> `spec`
- `产品描述` -> `product_description`
- `补货单位` -> `replenishment_unit`
- `指导售价1` -> `guide_price_1`
- `指导售价2` -> `guide_price_2`
- `备注` -> `remark`
- `礼盒图片` -> `image_url`

导入规则：

- 合并或空白单元格按上一行向下继承，适配同礼盒多规格多行。
- `序号` 只展示，不入库。
- 分类不存在时自动创建礼盒分类。
- 按 `分类 + 产品名称 + 等级 + 规格` 匹配更新。
- 图片列如果是 URL，保存到 `image_url`。
- 图片列如果是嵌入图片，第一期提示人工上传，不自动抽取。
- 导入错误必须包含 Excel 行号。

## 固定资产清空重建迁移

新增迁移 SQL，执行顺序：

1. 备份旧固定资产表：
   - `oa_fixed_asset_config_backup_20260706`
   - `oa_fixed_asset_repair_backup_20260706`
   - `oa_fixed_asset_quota_backup_20260706`
   - `oa_fixed_asset_quota_ledger_backup_20260706`
2. 清空旧固定资产业务数据：
   - `oa_fixed_asset_quota_ledger`
   - `oa_fixed_asset_repair`
   - `oa_fixed_asset_quota`
   - `oa_fixed_asset_config`
3. 改造字段：
   - `oa_fixed_asset_config.product_id` 改为 `oe_item_id`。
   - `oa_fixed_asset_repair.product_id/product_name` 改为 `oe_item_id/oe_item_name`。
   - 索引从 product 维度改为 OE 维度。
4. 重新由 OE 固定资产配置生成额度。

迁移 SQL 必须使用事务；如果数据库版本或 DDL 行为不支持完整事务回滚，需要在 SQL 注释中明确执行前必须确认备份表已创建。

## 组织和权限边界

- OE 和礼盒维护入口默认分配给仓库管理员、后台维护角色和超级管理员。
- 进销存只读查询入口默认分配给门店、进销存角色和超级管理员。
- 固定资产配置和维修继续使用店铺上下文权限。
- 查询入口可展示价格，不受商品成本权限限制。
- 后端接口不能只依赖前端隐藏按钮；写接口必须检查新增、编辑、删除、导入权限。
- 只读查询权限不能调用写接口。

## 错误处理

- 分类不存在或停用：提示 `分类不存在或已停用`。
- OE 编码重复：提示 `OE编码已存在`。
- OE 自然键重复：提示 `同分类、同类别下已存在该OE器皿`。
- 礼盒自然键重复：提示 `同分类、同等级、同规格下已存在该礼盒`。
- 供应商不存在、停用或非合作中：提示 `供应商不存在、已停用或非合作中，请先在供应商管理维护后再选择`。
- 删除被固定资产配置引用的 OE：提示 `OE器皿已被固定资产配置引用，不能删除，请先停用`。
- 固定资产配置保存未选择 OE：提示 `请选择OE器皿`。
- 固定资产维修选择未配置 OE：提示 `请选择本店已配置的OE固定资产`。

## 测试

后端测试：

- OE 分类编码生成和重复处理。
- OE 档案新增、编辑、删除、停用。
- OE 导入：分类继承、供应商匹配、供应商为空待完善、供应商无效失败、重复更新、错误行号。
- 礼盒导入：分类继承、多规格、重复更新、错误行号。
- 固定资产配置只能保存 OE，不能保存商品。
- 固定资产维修只能选择本店已配置且启用的 OE。
- 被固定资产配置引用的 OE 不允许删除。
- 固定资产清空重建 SQL 包含备份表和清空顺序。
- Mapper XML 绑定测试。

前端测试：

- 仓库管理下出现 OE/礼盒维护入口。
- 进销存管理下出现 OE/礼盒只读查询入口。
- OE/礼盒页面结构与商品管理一致。
- OE 供应商选择器带出手机号。
- OE 只读查询能看到成本价。
- 礼盒只读查询能看到指导售价1、指导售价2。
- 只读查询入口隐藏新增、编辑、删除、导入、模板下载。
- 固定资产配置不再出现商品搜索。
- 固定资产维修只加载当前店铺已配置 OE。
- 商品、采购、销售、库存页面不出现 OE 或礼盒。
- 移动端路由注册 `/mobile/oe`、`/mobile/gift`。
- 店铺和仓库移动工作台按权限显示 OE/礼盒资料入口。
- 移动端 OE/礼盒只显示查询、详情、导出，不显示写操作。
- 移动端 OE 成本价、礼盒指导售价在没有 `inv:cost:view` 时仍可见。
- 移动端固定资产维修表单使用 `oeItemId` 和 `fixedAssetOe` 实体选择器，不再使用 `productId` 商品选择器。
- Android WebView 和 iPhone Safari/WebView 下详情弹层、选择器弹层、图片上传和底部导航无遮挡。

## 实施顺序

1. 新增数据库迁移 SQL：OE/礼盒分类和档案表、菜单权限、固定资产备份清空和字段改造。
2. 新增 OE 后端 domain、mapper、service、controller、Excel parser/exporter。
3. 新增礼盒后端 domain、mapper、service、controller、Excel parser/exporter。
4. 修改固定资产后端 domain、mapper、service、controller 出入参和关联查询。
5. 新增 OE 分类和 OE 管理前端页面。
6. 新增礼盒分类和礼盒管理前端页面。
7. 修改固定资产配置和维修前端页面。
8. 新增移动端 OE/礼盒资料查询路由、工作台入口、列表详情映射、搜索、导出配置。
9. 修改移动端固定资产维修表单和实体选择器，改为选择当前店铺已配置 OE 固定资产。
10. 添加后端、桌面端、移动端测试。
11. 本地构建并验证菜单、权限、导入导出、只读查询、固定资产配置、维修上报、Android 手机端和苹果网页端。
