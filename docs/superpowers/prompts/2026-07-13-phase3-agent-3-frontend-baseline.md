# Agent 3 窗口提示词：前端九项基线门禁

你是 ERP 合同自动化阶段 3 的 Agent 3，只负责当前 9 个前端失败测试及其直接生产根因。请执行修复和验证，不要只做诊断报告。

原始工作树 `/Users/liuxingyu/Desktop/备份/ERP-NEW` 含用户未提交改动，禁止清理、重置、切分支或覆盖。你的分支是 `codex/frontend-baseline-gates-20260713`，worktree 是 `/Users/liuxingyu/.config/superpowers/worktrees/ERP-NEW/frontend-baseline-gates-20260713`。如未创建，使用 `using-git-worktrees` 技能从 `codex/phase3-multi-agent-plan-20260713` 最终 HEAD 创建；如已存在，先核对分支、基点和干净状态。半检出 worktree 不得继续开发，不得使用 `git reset --hard`。

开始前完整阅读：

1. `docs/superpowers/specs/2026-07-13-contract-phase3-multi-agent-delivery-design.md`
2. `docs/superpowers/plans/2026-07-13-frontend-baseline-gates-remediation.md`
3. 总控计划中 Agent 3 文件边界与交付要求。

使用 `executing-plans`、`systematic-debugging`、`test-driven-development` 和 `verification-before-completion` 技能；不要派生子 Agent。固定使用 Codex bundled Node `/Users/liuxingyu/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/bin/node` 所在 PATH，确认版本为 24.14.0。

严格按计划一次修一类根因并独立提交：

1. HeaderNotice 全部已读等待服务端刷新。
2. 移动劳动合同 44px。
3. Capacitor 源码门禁与受控生成门禁分离。
4. 选店生产页彻底移除 preview 组织/绕行。
5. 仓库文案不承诺相机扫码。
6. 头像操作 44px。
7. shell、inventory、profile 三处共享 viewport 生命周期、真实滚动根、mobile-system class、去玻璃/图片覆盖和统一底栏 active 逻辑。
8. 门店退回调拨恢复 `editReplenishment` todo focus。

只允许在 `mobileAppShell.test.js` 中基于 Git ignore + `verify-capacitor-sync.cjs` 证据收窄错误的生成物存在断言；其他失败应修改最小生产代码并保留断言。禁止删除测试、跳过测试、降低 44px 阈值、把示例数据移到另一个生产入口或虚构 camera 能力。

绝不提交 `erp-ui/dist/**`、`android/app/src/main/assets/public/**`、`ios/App/App/public/**`、`google-services.json`、`GoogleService-Info.plist`、`.jks`、`.keystore`、本机缓存。不要修改调岗对话框、离职业务、System/OA Java 或 SQL。

最终必须让九项专项和 `npm test` 的 142 个测试文件全绿，再运行密钥扫描、`npm run build:prod`、`npm run app:sync`、`npm run app:verify`。生成目录必须继续被 Git 忽略。若 release 凭据缺失，只记录非 release 警告，不能伪造文件。

完成后写验证记录、保持 worktree 干净，不自行合并到 Agent 1、不推送、不合并到 `7月12号`。交付格式：

```text
角色：Agent 3 / 前端基线
分支：codex/frontend-baseline-gates-20260713
worktree：
基点：
提交区间：
HEAD：
九项根因 -> 修复提交：
九项专项结果：
npm test 文件数/结果：
Node/npm 版本：
密钥扫描/生产构建：
Capacitor sync/verify：
生成物 git 检查：
已知限制：
git status：
推送/合并：未执行
```
