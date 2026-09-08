# ERP 手机端美化验收证据清单（运行时复验版）

> 依据：设计方案与控件台账（2026-08-01）
> **结论层级见 §0。未提交、未推送。**

## 0. 验收结论（签署口径）

| 验收层级 | 判断 |
|---|---|
| 本轮指定实现修正 + 静态源码门禁 + 测试/构建 | **已完成** |
| 设计契约资料（HTML/令牌渲染示意） | **已产出，但不可替代真实页面** |
| 真实 ERP dist 匿名页面四档截图 | **已完成**（登录、注册；Chrome 移动仿真，非真机） |
| 真实 ERP 登录态业务路由截图 | **未完成**（缺验收账号/会话） |
| 20 类关键业务状态真实渲染 | **未完成**（无登录态账号） |
| 四档 CSS 视口响应式门禁 | **已完成**（320/354/375/390；真实 dist + CDP） |
| 真机/真实键盘/系统安全区 | **未完成** |
| 角色权限/组织/动作冒烟 | **未完成** |
| **最终发布验收签字** | **不具备条件** |

**正式结论：**

> 本轮指定实现修正、静态源码门禁、测试/构建以及登录/注册四档真实 dist 截图已完成；八类登录态 After、20 类真实业务状态、角色权限和真机键盘/安全区仍待验收，因此**暂不具备最终发布验收签字条件**。

说明：`npm test` 输出中的 `257 run` 是**测试文件数**，不是断言/用例数。

---

## 1. 测试与构建

```
front-end node tests: 257 run, 0 failed   # = 257 个 *.test.js 文件
production build: DONE, bundle budget passed (~1.243 MiB)
runtime evidence: capture_completed=73, responsive_capture_valid=true, reduced_motion_valid=true
anonymous guard observation: 64/64 redirected to login
```

本轮运行命令：

- `npm test`
- 带构建元数据执行 `npm run build:prod`
- `npm run evidence:mobile-runtime`

历史蓝色样式文件已删除（零引用后）：

- `erp-ui/src/assets/styles/mobile-system.scss`（已删）
- `erp-ui/src/views/styles/mobileSystem.scss`（已删）

本轮额外源码冲突清理：

- `todo/index.vue`：去掉渐变背景与 blur 玻璃卡
- `inventory/index.vue`：去掉 glass 渐变/模糊、茶室背景图装饰

本轮验收缺陷修正：

- `unifiedTodoMobile.test.js`：安全区契约兼容令牌包装，不再错误要求源码出现裸 `env(...)`
- `register.vue`：页脚改为正常文档流；320×568 下可纵向滚动，页脚不再覆盖注册按钮
- `login.vue`、`register.vue`：验证码接口无图或失败时显示文字占位，不再生成 `base64,undefined` 的损坏图片
- 新增 `scripts/capture-mobile-runtime-evidence.cjs`：从当前 `dist` 可复现生成四档匿名页截图及无会话路由记录
- 新增全局动效系统：120/180/240ms 令牌、指针输入反馈、高频路由即时更新、Element UI 弹层短进入/弱退出
- 新增 `motionDesignSystem.test.js`：全源禁止 `transition: all`，并阻止全局动效层使用布局属性动画或漏掉减少动态效果门禁

---

## 2. 证据类型分层（避免误签）

| 类型 | 路径 | 能否用于最终签字 |
|---|---|---|
| A. 真机 Before 基线 | `baseline/*` | 仅作 before 对照 |
| B. 设计契约 HTML/PNG（硬编码 CSS） | `after/00-*`、`after/compare/*`、`after/page-states/*`、`after/viewports/*` | **否** — 非 Vue 路由 |
| C. 真实 ERP dist 运行时截图（移动 UA + CSS 设备视口） | `after/runtime-erp-mobile-ua/*` | **部分** — 可证明匿名登录/注册与无会话守卫；不能证明登录态业务页或真机 |
| D. 真实 ERP dist（桌面 UA） | `after/runtime-erp/*` | 证明桌面/移动分流，**不作为手机 After** |

`generate_evidence_assets.py` 生成的是 **B 类** 契约图；此前若将其写成“八类 After 已完成”，**判定过宽，现予收回**。

---

## 3. 真实 ERP 运行时截图结果（C 类）

生成命令：`cd erp-ui && npm run evidence:mobile-runtime`
脚本：`erp-ui/scripts/capture-mobile-runtime-evidence.cjs`
方法：本地启动当前 `dist`，使用 Chrome DevTools Protocol 设置 CSS 设备视口与 iPhone UA 后截图
目录：`after/runtime-erp-mobile-ua/`
机器清单：`MANIFEST.json`；简表：`MANIFEST.txt`；守卫明细：`route-runtime-matrix.json`

### 3.1 已真实渲染（可匿名）

| 路由 | 结果 | 代表文件 |
|---|---|---|
| `/login` | 暖白移动壳、茶绿主按钮、验证码降级占位 | `login-vp-320x568.png` … `login-vp-390x844.png` |
| `/register` | 注册表单、茶绿主按钮、流式页脚 | `register-vp-320x568.png` … `register-vp-390x844.png` |

自动检查结果：

- 8 张 PNG 的像素尺寸、CSS viewport、`clientWidth/clientHeight` 一致
- 四档均无横向溢出
- 注册页表单与页脚重叠面积均为 `0`
- 320×568 为短屏正常纵向滚动，不用固定页脚挤压或遮盖主操作
- 当前本地无验证码服务响应，页面显示“验证码”文字占位；这只证明失败降级，不证明验证码/登录提交链路
- Chrome 模拟 `prefers-reduced-motion: reduce` 时，登录按钮计算样式的过渡/动画最大值均为 `0.01ms`，`reduced_motion_valid=true`

### 3.2 被路由守卫重定向到登录（运行时行为，非目标业务页）

脚本读取 58 条业务路由，并补充 `/401`、`/404`、`/lock`、`/select-shop`、`/complete-profile`、`/credential/change-password` 6 条受保护公共路由。无会话访问共 64 条，当前构建记录为 **64/64 落到登录页**。
这是无会话守卫的运行时事实，不能记为 401/业务页 After 通过；代表截图为 `guard-mobile-store-vp-390x844.png`。

### 3.3 仍未获得的真实截图

- 登录态八类：工作台 / 库存 / 报销 / 云盘 / 签约 / 入职 / 个人中心（除登录壳外）
- 20 类实现的加载/空/错/无权限/弹层**真实组件态**
- 真机软键盘、`visualViewport`、安全区
- 角色 × 组织 × 动作矩阵

---

## 4. 静态设计契约（B 类，仅作规范示意）

| 产物 | 说明 |
|---|---|
| `after/page-states/20-page-states-gallery.*` | 硬编码状态卡示意；**非整页 ERP 渲染**；长画廊曾出现截断问题，不得当作完整状态验收 |
| `after/compare/*-after.*` | 八类视觉目标示意，**非登录态业务页** |
| `after/viewports/*` | 合成容器尺寸示意，**非真实键盘/安全区** |
| `matrices/*.json` | 路由/实现/视口清单 |

`mobileBeautificationAcceptance.test.js` 仍是**源码字符串门禁**，不会自动读取上述 PNG。新增的 `npm run evidence:mobile-runtime` 是独立运行时证据门禁：截图尺寸不一致、横向溢出、注册页脚重叠或减少动态效果运行时降级失败时，命令会失败。它目前尚未并入默认 `npm test`，也不能证明登录态业务渲染。

---

## 5. 路由冒烟（静态）

`matrices/route-smoke-matrix.json`：58 业务 + 8 公共路由在定义/实现中可定位。
运行时新增：58 业务 + 6 受保护公共路由的无会话守卫记录。
**未做**：登录后逐路由权限/组织/动作点验。

---

## 6. 可访问性

| 项 | 状态 |
|---|---|
| 目标目录内非语义 `@click` 静态扫描 | 0（字面规则） |
| 完整键盘操作 / 读屏 / 对比度 / 200% 放大 | **未做** |

---

## 7. 四档视口

| 视口 | 真实 ERP（登录/注册，移动 UA） | 真机 |
|---|---|---|
| 320×568 | 登录/注册均已生成；无横溢；注册页脚不覆盖 | 未做 |
| 354×766 | 登录/注册均已生成；无横溢；注册页脚不覆盖 | 未做 |
| 375×667 | 登录/注册均已生成；无横溢；注册页脚不覆盖 | 未做 |
| 390×844 | 登录/注册均已生成；无横溢；注册页脚不覆盖 | 未做 |

键盘：无真实软键盘证据。

---

## 8. 下一步（进入最终签字前的必做）

1. **提供可登录验收账号**（或会话），覆盖门店/仓库/HR/审批/签约等角色。
2. 在真实会话下对八类页补拍 **登录态 After**，写入 `after/runtime-erp-mobile-ua/authenticated/`。
3. 对 20 类实现分别构造/进入：正常、加载、空、错误、无权限、关键弹层并截图。
4. 真机验证键盘与安全区。
5. 将 `npm run evidence:mobile-runtime` 接入发布验收或 CI，并补充登录态运行时断言。
6. 仅在 1–5 通过后签署“验收扫尾完成”。

---

## 9. 本轮执行动作摘要

- 修正安全区测试契约、注册短屏页脚覆盖与验证码损坏图片降级
- 新增可重复执行的真实 `dist` 四档截图脚本和 npm 命令
- 重新生成 `runtime-erp-mobile-ua`：8 张匿名页视口图、1 张守卫代表图、JSON/TXT 清单及 64 条路由记录
- 旧的不可复现/尺寸异常运行时证据已被当前脚本输出替换
- 明确 B 类契约图、C 类匿名运行时图与登录态/真机验收的边界
- 按生产力工具权重建立全局动效令牌，清零前端源码中的 `transition: all`，并验证运行时减少动态效果

**当前可签署的是：源码改造与静态门禁阶段完成；不可签署：最终发布视觉/运行时验收。**
