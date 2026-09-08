# 手机端综合审计（2026-07-10）

## 结论

当前手机端的登录、组织切换、门店工作台、仓库工作台、销售、采购和出库主页面基本可用，生产构建也能成功，但暂不建议直接作为“稳定版”发布。当前至少有 1 个会让业务流程卡死的前端路由故障，另有扫码按钮未真正接入摄像头、必填客户为空时无补救入口、测试数据混入业务列表、手机端资料维护不完整等问题。

本轮优先级：1 个 P0、4 个 P1、8 个 P2。

## 审计范围

- 本地实际运行版本：`http://127.0.0.1:1026`
- 主要视口：390 × 844；补充验证：320 × 568
- 角色与上下文：管理员、测试门店、主仓库
- 实走流程：登录、组织选择、门店工作台、销售列表、新建销售、商品选择、放弃修改、库存、销售退货、我的、个人资料、仓库工作台、采购入库、销售出库
- 静态检查：路由、移动表单、扫码、权限边界、认证存储、部署头、依赖漏洞
- 未执行会真实写入业务数据的“保存草稿/保存并提交/出库确认”等操作

## P0：会卡住主流程

### M-01 库存切换到退货时，地址变化但页面仍停在库存

- 复现步骤：销售页打开新建表单 → 添加明细 → 取消并确认放弃 → 进入库存 → 点击底部“退货”。
- 实际结果：URL 已变为 `/mobile/sales-return`，页面标题、搜索条件和内容仍是库存；控制台出现 `NotFoundError: insertBefore ... node ... is not a child of this node`。手动刷新后退货页才恢复。
- 影响：用户认为已经进入退货模块，实际操作的仍是库存界面；主流程被卡住，且状态错乱容易误操作。
- 证据：[10-return-route-stuck.jpg](audit-screenshots/mobile-audit-20260710/10-return-route-stuck.jpg)、[11-return-after-refresh.jpg](audit-screenshots/mobile-audit-20260710/11-return-after-refresh.jpg)。
- 最可能根因：`mobileOverlayStack.js:8-14` 把 Vue 管理的弹层 DOM 直接 `appendChild` 到 `body`，释放时只恢复 body 状态，没有还原节点父级；随后多个移动路由复用同一个 `MobileFeaturePage`，Vue 2 在跨路由补丁更新时找不到预期父节点。根路由视图 `App.vue:3` 也没有路由 key 来隔离实例。
- 建议：先写一条“打开弹层并关闭后，从库存切到退货”的浏览器回归测试，再替换手工 DOM 搬移方案或确保卸载前还原节点；同时评估给根移动路由视图增加稳定 route key。

## P1：发布前应处理

### M-02 “扫码”只是占位按钮，没有摄像头扫码能力

- `MobileEntityPicker.vue:131-138` 和 `MobileLineItemsEditor.vue:471-477` 点击后只提示“当前环境暂不支持摄像头扫码”，没有调用 Capacitor/相机/条码识别能力。
- 影响：采购收货、销售开单、盘点、补货等高频仓储动作无法完成预期的手机扫码，只能手输或外接扫码枪。
- 建议：在原生壳接入 Capacitor 条码扫描插件；网页端保留手输，并把按钮文案按能力检测显示为“扫码”或“输入条码”。

### M-03 新建销售在当前门店无客户可选，也没有“新建客户”补救入口

- 客户是必填项，但测试门店显示“暂无可选数据”；表单内只能搜索，没有创建客户或跳转客户资料的动作。
- 影响：新门店或客户资料未预建时，销售开单会直接卡住。
- 证据：[05-sales-form.jpg](audit-screenshots/mobile-audit-20260710/05-sales-form.jpg)。
- 建议：空状态说明原因，并提供“新建客户”或“去客户资料”入口；返回表单时自动带回刚创建的客户。

### M-04 库存页继承了销售页的列表标题和搜索字段

- 库存页配置本应显示“库存提醒”和“输入商品名称/编码搜索”，实际显示“销售待处理”“输入单号搜索”“单号/标题”。
- 影响：用户无法判断当前搜索对象，也会误以为库存页加载了销售数据。
- 证据：[09-stock-page.jpg](audit-screenshots/mobile-audit-20260710/09-stock-page.jpg)。
- 判断：这是 M-01 同类的跨路由局部更新失败，而不是库存配置写错；`mobileRouteDefinitions.js:99-118` 和 `featureSearchConfigs.js:4` 的库存配置本身正确。

### M-05 当前业务列表混入大量 QA/MOBILE 测试记录

- 采购、出库和商品选择器中可见大量 `QA-IOS-*`、`QA-MOBILE-*`、`MOBILE-QA-*` 数据。
- 影响：真实员工难以识别有效单据；如果当前数据库对应准生产/生产环境，则属于发布阻断的数据隔离问题。
- 证据：[15-purchase-list.jpg](audit-screenshots/mobile-audit-20260710/15-purchase-list.jpg)、[16-outbound-list.jpg](audit-screenshots/mobile-audit-20260710/16-outbound-list.jpg)。
- 建议：上线前运行现有远端测试数据审计脚本，并按环境标识或租户彻底隔离自动化测试数据。

## P2：体验与完整性问题

### M-06 组织页“进入工作台”可能实际返回原页面

- 从个人资料页切换门店/仓库后，按钮写“进入工作台”，但代码会保留 `redirect` 并返回个人资料页（`select-shop/index.vue:157,312-317`）。
- 建议：按钮按结果显示“确认并返回”或“进入工作台”；或者统一始终进入对应工作台。

### M-07 手机个人资料只能看，不能改

- 个人页只有刷新、公告、切换组织、退出；没有修改手机号、邮箱、头像或密码。
- 强制修改密码仍跳到桌面路由 `/user/profile?activeTab=resetPwd`（`passwordResetReminder.js:4`）。
- 证据：[13-profile-page.jpg](audit-screenshots/mobile-audit-20260710/13-profile-page.jpg)。
- 建议：至少补齐修改密码；再按权限开放手机号、邮箱和头像维护。

### M-08 表单底部三个动作纵向堆叠，占用过多内容高度

- “取消 / 保存草稿 / 保存并提交”长期占据约 170px，且两个保存动作视觉权重相同；长明细表单首屏只能看到少量字段。
- 证据：[06-sales-line-item.jpg](audit-screenshots/mobile-audit-20260710/06-sales-line-item.jpg)、[07-product-picker.jpg](audit-screenshots/mobile-audit-20260710/07-product-picker.jpg)。
- 建议：主动作固定一行，次动作放并排按钮或更多菜单；错误信息放在主动作上方。

### M-09 列表入口太靠下

- 采购页需经过标题、5 个快捷按钮和搜索区后才能看到第一条单据；390px 高度首屏完全看不到业务列表。
- 证据：[15-purchase-list.jpg](audit-screenshots/mobile-audit-20260710/15-purchase-list.jpg)。
- 建议：把列表与当前待办前置；低频“导出/采购退货”收进更多菜单。

### M-10 可点击列表不是可聚焦控件

- `feature/index.vue:145` 和 `MobileWorkbenchShell.vue:71` 使用带 `@click` 的 `<article>`，没有 `button`、`tabindex` 或键盘事件。
- 影响：键盘、开关控制和部分读屏用户无法打开详情/待办。
- 建议：改成真实按钮/链接，或补齐 `role="button"`、焦点和 Enter/Space 行为。

### M-11 表单字段标签未与控件关联

- `MobileFormSheet.vue:16-83` 使用 `<div><span>标签</span><input>`，不是 `<label>`；实测销售日期输入框没有可访问名称。
- 建议：使用 `<label for>` 或 `aria-labelledby`，并在必填项上提供可感知的必填提示。

### M-12 登录验证码缺少替代文本与键盘刷新

- 验证码图片无 `alt`，外层是可点击 `<div>`，不能通过键盘触发；账号/密码还设置了 `autocomplete="off"`，不利于密码管理器。
- 证据：[17-mobile-login.jpg](audit-screenshots/mobile-audit-20260710/17-mobile-login.jpg)、[18-login-320px.jpg](audit-screenshots/mobile-audit-20260710/18-login-320px.jpg)。
- 建议：验证码刷新改成按钮并标注“刷新验证码”；账号使用 `autocomplete="username"`，密码使用 `current-password`。

### M-13 视觉层级与对比度仍有小问题

- 组织页背景上的说明文字对比度过低；工作台英雄卡文案在 390px 宽度下换行拥挤；多处 12px 次级文字偏密。
- 证据：[01-select-organization.jpg](audit-screenshots/mobile-audit-20260710/01-select-organization.jpg)、[03-store-workbench.jpg](audit-screenshots/mobile-audit-20260710/03-store-workbench.jpg)、[14-warehouse-workbench.jpg](audit-screenshots/mobile-audit-20260710/14-warehouse-workbench.jpg)。

## 做得好的地方

- 390px 与 320px 视口未发现横向溢出，主要按钮触控尺寸基本达到 44px。
- 门店/仓库上下文清晰，底部导航会随组织类型变化。
- 未保存表单有明确二次确认，能避免误丢数据。
- 空列表、加载、刷新时间、加载更多、权限隐藏等基础状态齐全。
- 后端组织范围会校验用户是否有对应门店/仓库权限，不只依赖前端路由。
- 生产构建成功；生产依赖审计没有中高危依赖漏洞。

## 流程健康度

1. 登录：基本健康；验证码可访问性和密码管理器支持需改进。
2. 组织选择：可用；说明文字对比度低，CTA 结果可能与文案不一致。
3. 门店工作台：可用；首屏信息偏密。
4. 销售列表：可用；空状态清楚。
5. 新建销售：部分阻塞；当前门店无客户且无法在表单内补建，扫码未实现。
6. 商品明细与放弃修改：可用；放弃确认做得好，底部动作区过高。
7. 库存：异常；局部继承销售页文案与搜索字段。
8. 库存 → 退货：失败；页面卡住，需刷新恢复。
9. 退货刷新后：可用。
10. 我的 / 个人资料：可用但功能不完整；不能在手机端修改资料和密码。
11. 仓库工作台：可用；核心数量与待办清楚。
12. 采购入库 / 销售出库：可用；列表被快捷区下压，且当前环境混入测试记录。

## 验证结果与限制

- `npm run build:prod`：通过。
- `npm test`：87 项中 1 项失败，失败原因见安全报告。
- `npm audit --omit=dev`：0 个中高危，4 个低危。
- 未做真实保存、提交、发货、审批、打卡、签署等写操作。
- 未授权摄像头权限，因此没有实测相机；静态代码已确认扫码按钮没有相机实现。
- 未测试弱网、离线、Android/iOS 冷启动、系统返回键、键盘顶起和系统字体放大。
- 截图只能支持可见层面的可访问性判断，不能代表完整 WCAG 合规。
