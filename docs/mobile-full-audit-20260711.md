# 手机端全量检查与修复报告（2026-07-11）

## 结论

本轮完成了手机 Web 主流程的全量路由检查、窄屏/短屏复验、底部可达性复验、核心表单复验和原生壳构建验证。

- 手机 Web：已修复本轮定位的遮挡、触控尺寸和数据映射缺陷；390×844 与 320×568 下未再发现横向溢出，已复验路由滚动到底后业务控件与底栏重叠为 0。
- 核心表单：销售、调拨、盘点均可滚动到最后字段和提交区，固定操作栏未遮挡表单控件。
- Android/iOS：生产资源已同步，两端 Debug 构建均成功；iPhone 17 Pro 模拟器可安装并启动。
- 唯一外部发布阻塞：现网 `8.152.199.39` 只有 HTTP，尚无可供原生正式包使用的 HTTPS API Origin。代码已改为正式原生环境强制校验 HTTPS，避免把 `/prod-api` 错发到 `capacitor://localhost`，但基础设施补齐前原生正式包不能完成登录业务链路。

## 覆盖范围

### 页面与尺寸

代码中共有 48 个一级 `/mobile/*` 路由定义。

| 范围 | 路由数 | 尺寸 | 结果 |
| --- | ---: | --- | --- |
| 门店上下文 | 40 | 390×844、320×568 | 无横向溢出；修复后可见触控项均不小于 44px |
| 仓库上下文 | 18 | 390×844、320×568 | 无横向溢出；修复后可见触控项均不小于 44px |
| 去重后的真实截图路由 | 46 | 两套尺寸 | 共覆盖 58 个上下文-路由组合 |
| OE、礼盒 | 2 | 映射/详情单元测试 | 恢复路由数据映射；浏览器会话失效后未重复处理验证码，因此未补当前轮登录态截图 |

除列表首屏外，还检查了：登录、组织选择、页面滚动到底、合同详情、签署包空状态、销售新增、调拨新增、库存盘点新增，以及门店/仓库上下文切换。

### 证据

- 原始截图与联系表：`docs/audit-screenshots/mobile-full-20260711/current-run/`
- 修复后截图：`docs/audit-screenshots/mobile-full-20260711/after/`
- 修复后销售页底部：`after/fixed-store-320-sales-bottom.png`
- 修复后个人资料：`after/fixed-store-320-profile.png`
- 合同详情：`after/detail-320-contract-fixed.png`
- 核心表单底部：`after/form-bottom-320-sales.png`、`form-bottom-320-transfer.png`、`form-bottom-320-stock-check.png`
- iOS 启动：`after/ios-iphone17pro-relaunch.png`

## 发现与处理

| 级别 | 问题 | 根因 | 处理 | 状态 |
| --- | --- | --- | --- | --- |
| P1 | 多页面底部按钮被固定导航遮住 | `<details>` 折叠时，作者样式覆盖浏览器默认隐藏规则，内部 `.overflow-feature-actions` 仍显示并溢出容器 | 增加 `.feature-action-overflow:not([open]) > .overflow-feature-actions { display: none; }`；40 条门店和 18 条仓库路由滚底复验 0 重叠 | 已修复 |
| P1 | OE、礼盒页面无正确行映射 | 活跃 mapper 丢失 `oe/gift` 分支、状态和详情字段 | 恢复列表映射、门店/仓库字段隔离、价格和详情字段 | 已修复 |
| P1 | 固定资产报修显示错误字段 | 手机行仍使用维修金额/额度旧模型 | 改为 OE 名称、故障描述、损坏数量、门店 | 已修复 |
| P1 | 盘点状态和审批轨迹缺失 | mapper 缺少 checking/rejected/invalidated 等状态与驳回/库存变化字段 | 恢复状态、日期、差异数量和审批轨迹映射 | 已修复 |
| P1 | 原生正式包 API 相对地址失效 | `/prod-api` 在 Capacitor 内解析到 `capacitor://localhost` | 新增 `resolveApiBaseUrl`；Web 保持相对路径，正式原生要求 `VUE_APP_NATIVE_API_ORIGIN` 为绝对 HTTPS 地址 | 代码已修复，HTTPS 基础设施待补 |
| P2 | “更换头像”触控区仅 34px | 局部样式覆盖全局 44px | 恢复为 44px，320px 实测 76×44 | 已修复 |
| P2 | 合同“打开”链接仅 28×20，确认输入仅 38px | 链接和输入缺少手机触控下限 | “打开”、证明下载、确认输入及头部按钮统一至少 44px | 已修复 |
| P2 | 劳动合同、签署包部分按钮偏小 | 模块私有样式使用 36/38px | 返回、刷新、标签、操作按钮及输入统一至少 44px | 已修复 |
| P2 | 仓库工作台宣称“扫码确认入库” | 原生壳没有相机/扫码插件 | 文案改为真实可用的“确认入库” | 已修复 |
| P2 | Android 构建被重复配置文件阻断 | `config 2.xml` 文件名含空格，Android 资源名非法 | 删除重复文件，并让 hardening 脚本同时清理 Android/iOS 重复 config XML | 已修复 |
| P2 | 前端私钥工具残留 | 未使用的 `jsencrypt.js` 仍包含私钥和解密实现 | 删除未引用文件；前端 secret scan 通过 | 已修复 |

## 修改方案落地

1. 布局层：保留既有视觉系统和固定底栏结构，只修复折叠内容可见性、底部安全区与 44px 触控下限。
2. 功能层：补齐 mapper 分支、状态、日期、详情与审批信息，不引入假数据或预览绕过。
3. 原生层：Web 与 Capacitor 使用同一包；Web 继续走反向代理相对路径，正式原生必须显式提供 HTTPS API Origin。
4. 回归层：为每项修复增加源码/行为断言，并以 390×844、320×568、表单底部、Android/iOS 构建作为发布门禁。

## 验证结果

- 手机专项测试：11/11 通过。
- 全部非副本 Node 测试：104 通过，2 个历史门禁失败。
  - `frontendTestGate.test.js`：用户目录中仍保留 6 个带空格的重复测试副本；按既有约束未删除。
  - `p0UxHardening.test.js`：旧断言要求非生产预览免登录，和当前另外 3 个测试要求“任何预览都不得绕过认证”冲突；保持更严格的现行策略。
- `npm run build:prod`：成功，前端私钥扫描通过。
- `node scripts/verify-capacitor-sync.cjs`：成功，Android/iOS 资源与 `dist` 一致。
- Android：JDK 21 下 `assembleDebug` 成功，APK 位于 `erp-ui/android/app/build/outputs/apk/debug/app-debug.apk`。
- iOS：iPhone 17 Pro Simulator Debug 构建成功，安装/启动成功。

## 发布前唯一必需外部动作

为正式原生包配置可验证证书的 HTTPS 域名，并在构建时提供：

```bash
VUE_APP_NATIVE_API_ORIGIN=https://你的正式域名 npm run build:prod
NODE_ENV=production npx cap sync
```

该地址必须能访问 `/prod-api/captchaImage`、`/prod-api/login` 和后续业务接口。当前 `http://8.152.199.39/prod-api/captchaImage` 返回 200，但 HTTPS 握手失败，因此不能通过降低 Android/iOS 安全策略来绕过。

## 真机截图追加复核：补货弹层（2026-07-11）

### 截图结论

用户提供的 `IMG_1422.PNG` 至 `IMG_1427.PNG` 暴露了浏览器常规窄屏复验未覆盖的 iOS WebView 组合问题：补货表单中再次打开库存选择器时，子选择器仍处于父表单的滚动与裁切上下文；固定底部导航继续显示；选择器确认栏被裁掉；长物料编码产生横向挤压；错误字段定位会交给浏览器选择滚动祖先。

### 本次处理

| 问题 | 修复 |
| --- | --- |
| 库存选择器被父表单裁切 | 打开后把选择器遮罩真实挂载到 `document.body`，关闭或销毁时恢复挂载位置 |
| 双层滚动导致底部操作不可达 | 选择器改为固定动态视口高度，列表是唯一弹层滚动区，确认栏固定保留在底部 |
| 底部导航遮挡并接收误触 | 表单或库存选择器打开时隐藏底部导航并关闭指针事件 |
| 长编码顶出右侧数量输入 | 内容列使用 `minmax(0, 1fr)`、`min-width: 0` 和任意位置换行 |
| “转到错误字段”滚错容器 | 取消全局 `scrollIntoView`，只计算并滚动 `.form-body` |

### 追加验证

- 新增 `mobileReplenishmentOverlayRegression.test.js`，覆盖顶层挂载、动态视口、单滚动区、确认栏可达、底栏隔离、窄屏长文本和表单内错误定位。
- 补货弹层及关联移动端专项测试：通过。
- 非重复副本测试：106 个执行，105 个通过；唯一失败仍为已知的 `p0UxHardening.test.js` 旧预览免登录断言，与当前严格认证策略冲突。
- `npm run build:prod`：成功，前端 secret scan 通过。
- 本地可启动并编译；浏览器视觉复验被正常登录门禁重定向到登录页，因此没有使用预览免登录或测试数据绕过认证。正式登录态真机复验应以本次六张截图的同一流程作为验收用例。

## 真机截图追加复核：补货草稿详情（2026-07-11）

新截图确认补货草稿详情还有三个遗漏：详情层开启时底部导航仍显示；详情层对 `body` 使用 `touch-action: none`，导致 iOS 内部滚动也被禁用；编辑动作依赖展示对象的非枚举原始字段；真实草稿删除服务没有暴露到控制器和手机端。

本次追加处理：

- 详情层允许纵向触摸滚动，并在打开期间隐藏底部导航、禁止底栏接收点击。
- 完整详情加载成功后单独保存编辑源；进入编辑表单时从该完整详情构建表单，保留已选物料和数量。
- 新增 `DELETE /inventory/transfer/delete/{transferId}` 草稿删除接口，调用既有 `deleteTransfer` 服务；只允许 `draft/cancelled` 状态，权限为 `inv:transfer:remove`。
- 手机草稿详情增加“删除草稿”危险操作，经过二次确认后永久删除并刷新列表。
- 新增 `mobileReplenishmentDraftWorkflow.test.js`，并扩展调拨控制器、动作运行时和动作清单测试。

验证：库存后端模块 268 个测试全部通过；手机补货专项测试全部通过；前端生产构建成功。非重复副本前端测试 107 个执行、106 个通过，唯一失败仍是既有 `p0UxHardening.test.js` 旧预览免登录断言。
