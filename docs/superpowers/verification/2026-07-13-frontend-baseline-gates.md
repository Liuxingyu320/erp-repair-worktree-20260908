# Agent 3 前端九项基线门禁验证记录

- 日期：2026-07-13（Asia/Shanghai）
- 角色：Agent 3 / 前端基线
- 分支：`codex/frontend-baseline-gates-20260713`
- worktree：`/Users/liuxingyu/.config/superpowers/worktrees/ERP-NEW/frontend-baseline-gates-20260713`
- 基点：`d330cb56b053e00206436a65e1193eee2a0e506d`（`codex/phase3-multi-agent-plan-20260713`）
- 实现提交区间：`f3b01794^..34aa6011`
- Node：`v24.14.0`
- npm：`11.11.0`

所有命令均将 `/Users/liuxingyu/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/bin` 放在 `PATH` 首位执行。

## 根因、修复与提交

| 门禁 | 根因与生产修复 | 提交 |
| --- | --- | --- |
| HeaderNotice 全部已读 | 点击流程未等待全部已读请求及服务端权威列表刷新；增加并发锁，依次等待 `markNoticeReadAll()` 与 `loadNoticeTop()`，失败时保留现有状态 | `f3b01794` |
| 移动劳动合同 44px | 返回、章节链接、证书和确认输入等交互目标小于 44px；补齐最小触控尺寸 | `8f22175a` |
| Capacitor 门禁分层 | 稳定源码测试错误要求 Git 忽略的同步生成物预先存在；改为检查 ignore 规则与 `verify-capacitor-sync.cjs` 的受控生成证据 | `3bfcf972` |
| 选店生产数据隔离 | 生产入口仍包含 `previewDeptTree`、预览绕行及库存 demo 绕行，路由定义还保留生产种子数据；全部从生产路径移除 | `6edeb7a5` |
| 仓库相机承诺 | 工作台文案承诺尚未实现的相机扫码能力；改为真实可用的到货确认语义 | `a41fb1dc` |
| 移动头像 44px | 头像操作按钮最小高度为 34px；提升至 44px | `8f003a6e` |
| 移动 viewport/滚动根/视觉基线 | shell、inventory、profile 及被同一共享契约覆盖的 contract、signPackage 未统一真实滚动根、viewport 生命周期、系统 class、底栏 active 与面板滚动边界；接入共享生命周期和导航判断，移除玻璃/图片覆盖并修正签署面板键盘与滚动布局 | `98ae0a0b`、`34aa6011` |
| 调拨退回移动编辑动作 | STORE 场景未按目标门店恢复 `editReplenishment`，移动 feature 页也没有精确消费 todo focus；按目标部门判定补货编辑，并复用共享 focus 契约定位精确业务行 | `91ab6ce4` |
| 九项兼容门禁收口 | 移除种子数据后显露库存权限声明缺失，隔离 VM 也需要 viewport 依赖降级；补充显式库存权限和安全 fallback，不改业务断言 | `34aa6011` |

除 `erp-ui/test/mobileAppShell.test.js` 按计划收窄 Capacitor 生成物存在断言外，未删除、跳过或降低其他测试契约；未修改调岗、离职、System/OA Java 或 SQL。

## 验证结果

### 九项专项

以下专项逐项执行，全部退出码为 0：

```text
node test/headerNoticeReadAll.test.js
node test/laborContractModule.test.js
node test/mobileAppShell.test.js
node test/mobileAuthEntryPages.test.js
node test/mobileInventoryWorkbench.test.js
node test/mobileProductionDataIsolation.test.js
node test/mobileProfileMaintenance.test.js
node test/mobileProgressiveRedesign.test.js
node test/unifiedTodoBusinessFocus.test.js
```

### 前端全量与安全门禁

| 命令 | 结果 |
| --- | --- |
| `npm test` | `front-end node tests: 142 run, 0 failed` |
| `node scripts/scan-frontend-secrets.cjs` | `frontend secret scan passed` |
| `npm run build:prod` | 成功；仅保留既有体积提示：入口约 1.68 MiB，高于建议 1.66 MiB |

### Capacitor

| 命令 | 结果 |
| --- | --- |
| `npm run app:sync` | Android/iOS 同步成功，`Capacitor bundled web assets match dist` |
| `npm run app:verify` | 成功；内部前端全量仍为 `142 run, 0 failed`，同步资产校验通过 |

非 release 环境缺少 `android/app/google-services.json` 与 `ios/App/App/GoogleService-Info.plist`，验证按设计仅给出警告，未伪造或提交凭据。

同步命令短暂改写了以下跟踪生成文件，核对后已恢复到分支版本，未纳入提交：

```text
erp-ui/android/app/capacitor.build.gradle
erp-ui/android/capacitor.settings.gradle
erp-ui/ios/App/CapApp-SPM/Package.swift
```

以下同步产物继续由 Git 忽略，且没有跟踪文件：

```text
erp-ui/dist/**
erp-ui/android/app/src/main/assets/public/**
erp-ui/ios/App/App/public/**
```

## 最终边界检查

- `git diff --check d330cb56..HEAD`：通过。
- 实现差异中无 `dist`、Android/iOS `public`、Google 服务配置、`.jks`、`.keystore` 或本机缓存。
- 生产密钥扫描通过。
- 未推送，未合并到 Agent 1，未合并到 `7月12号`。

worktree 使用受控 sparse checkout；由于共享对象库在完整展开时发生 `mmap failed: Operation timed out`，全量前端测试所需且与基点差异为零的后端/脚本测试输入从另一干净 worktree 只读补齐。该操作未改变索引、提交边界或验证对象。
