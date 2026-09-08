# 7月12号本地源码整合 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将本地主工作区中的全部源码、配置、测试、SQL 与文档制作成可审计快照，在用户查看功能差距并明确批准后，安全合并到 `7月12号` 并重跑完整验证。

**Architecture:** 先在 `codex/local-source-integration-20260712` 上按白名单范围建立单一源码快照提交，再通过 Git 三方合并保留 `7月12号` 已有的统一待办、HR 人事和目标侧后续能力。正式 `git merge` 前设置人工批准门；任何目标侧脏改动或业务语义冲突都必须暂停并展示给用户。

**Tech Stack:** Git worktrees、Git pathspec、Maven、Node.js、Vue CLI、Spring Boot、MyBatis、Capacitor、Android、iOS。

---

## 文件与责任边界

**设计与计划：**

- Existing: `docs/superpowers/specs/2026-07-12-local-source-integration-design.md`
- Create: `docs/superpowers/plans/2026-07-12-local-source-integration.md`

**必须进入源码快照的代表性新增文件：**

- 库存通用商品：
  - `erp-modules/erp-inventory/src/main/java/com/erp/inventory/constant/InvItemTypes.java`
  - `erp-modules/erp-inventory/src/main/java/com/erp/inventory/domain/vo/InventoryItemSnapshot.java`
  - `erp-modules/erp-inventory/src/main/java/com/erp/inventory/service/impl/InventoryItemResolver.java`
  - `erp-ui/src/views/inventory/components/InventoryItemSelect.vue`
  - `erp-ui/test/inventoryGenericItemFlow.test.js`
  - `sql/erp_inventory_generic_item_stock_20260710.sql`
- 固定资产月度额度与批量报修：
  - `erp-modules/erp-oa/src/main/java/com/erp/oa/domain/OaFixedAssetQuotaMonth.java`
  - `erp-modules/erp-oa/src/main/java/com/erp/oa/domain/dto/OaFixedAssetRepairBatchItem.java`
  - `erp-modules/erp-oa/src/main/java/com/erp/oa/domain/dto/OaFixedAssetRepairBatchRequest.java`
  - `erp-modules/erp-oa/src/main/java/com/erp/oa/mapper/OaFixedAssetQuotaMonthMapper.java`
  - `erp-modules/erp-oa/src/main/resources/mapper/oa/OaFixedAssetQuotaMonthMapper.xml`
  - `sql/erp_fixed_asset_monthly_quota_20260709.sql`
- 移动端渐进式重构与原生工程：
  - `erp-ui/src/views/mobile/styles/mobileSystem.scss`
  - `erp-ui/src/views/mobile/mobileViewport.js`
  - `erp-ui/src/views/mobile/feature/components/mobileOverlayPortal.js`
  - `erp-ui/src/views/mobile/feature/mobileLineItemPricing.js`
  - `erp-ui/test/mobileProgressiveRedesign.test.js`
  - `erp-ui/test/mobileReplenishmentOverlayRegression.test.js`
  - `erp-ui/android/**`，但排除 `erp-ui/android/gradle/wrapper/gradle-wrapper.jar`
  - `erp-ui/ios/**`

**不得进入源码快照的路径：**

- `docs/audit-screenshots/**`
- `**/*.jar`
- `**/*.pyc`
- `**/__pycache__/**`
- `.codex/**`
- `.claude-flow/**`
- `**/target/**`
- `**/dist/**`
- `**/build/**`
- `**/*.log`
- `**/*.tmp`
- `**/.DS_Store`

## 已确认的干净基线

- `mvn test`：24 个 Reactor 模块全部 `SUCCESS`。
- `npm --prefix erp-ui run test`：103 个测试运行，1 个既有失败。
- 既有失败：`erp-ui/test/mobileAppShell.test.js` 找不到 `erp-ui/android/settings.gradle`。本地源码快照包含该 Android 文件，因此整合后的预期是该失败转为通过。

---

### Task 1: 将计划提交同步到本地源码整合分支

**Files:**

- Existing: `docs/superpowers/specs/2026-07-12-local-source-integration-design.md`
- Existing: `docs/superpowers/plans/2026-07-12-local-source-integration.md`

- [ ] **Step 1: 确认计划 worktree 干净且计划分支只领先一个文档提交**

Run:

```bash
git -C /Users/liuxingyu/.config/superpowers/worktrees/ERP-NEW/local-source-integration-plan-20260712 status --short --branch
git log --oneline codex/local-source-integration-20260712..codex/local-source-integration-plan-20260712
```

Expected: worktree 无文件状态；日志只显示计划文档提交。

- [ ] **Step 2: 确认主工作区位于源码整合分支且原始 HR 快照引用未移动**

Run:

```bash
git -C /Users/liuxingyu/Desktop/备份/ERP-NEW branch --show-current
git -C /Users/liuxingyu/Desktop/备份/ERP-NEW rev-parse codex/hr-workspace-snapshot-20260711
```

Expected: 当前分支为 `codex/local-source-integration-20260712`；原始分支仍为 `410f6bccb597ba0eb97de35a782721e758e18afd`。

- [ ] **Step 3: 仅快进计划提交，不接触工作区源码变化**

Run:

```bash
git -C /Users/liuxingyu/Desktop/备份/ERP-NEW merge --ff-only codex/local-source-integration-plan-20260712
```

Expected: 分支快进一个计划提交；原有已修改和未跟踪文件仍留在工作区，内容不变。

- [ ] **Step 4: 验证设计与计划均已跟踪**

Run:

```bash
git -C /Users/liuxingyu/Desktop/备份/ERP-NEW ls-files \
  docs/superpowers/specs/2026-07-12-local-source-integration-design.md \
  docs/superpowers/plans/2026-07-12-local-source-integration.md
```

Expected: 精确输出上述两个路径。

### Task 2: 按范围暂存本地源码快照

**Files:**

- Modify/Create/Delete: 当前工作区内除明确排除项以外的全部源码、配置、测试、SQL、脚本、文档和运行时静态资源。
- Preserve unstaged: 本计划“不得进入源码快照的路径”中列出的全部文件。

- [ ] **Step 1: 记录暂存前状态与排除项数量**

Run:

```bash
cd /Users/liuxingyu/Desktop/备份/ERP-NEW
git -c core.quotepath=false status --short --untracked-files=all
git -c core.quotepath=false status --porcelain=v1 -uall | awk '
  /^\?\? docs\/audit-screenshots\// {screenshots++}
  /\.jar$/ {jars++}
  /\.pyc$/ {pyc++}
  /__pycache__/ {cache++}
  /^\?\? \.codex\// {codex++}
  /^\?\? \.claude-flow\// {flow++}
  END {printf "screenshots=%d jars=%d pyc=%d pycache=%d codex=%d claude_flow=%d\n", screenshots, jars, pyc, cache, codex, flow}'
```

Expected: 输出所有脏文件及各排除类别计数；不修改 index。

- [ ] **Step 2: 使用排除 pathspec 暂存全部源码与文档**

Run:

```bash
git add -A -- \
  . \
  ':(exclude,glob)docs/audit-screenshots/**' \
  ':(exclude,glob)**/*.jar' \
  ':(exclude,glob)**/*.pyc' \
  ':(exclude,glob)**/__pycache__/**' \
  ':(exclude,glob).codex/**' \
  ':(exclude,glob).claude-flow/**' \
  ':(exclude,glob)**/target/**' \
  ':(exclude,glob)**/dist/**' \
  ':(exclude,glob)**/build/**' \
  ':(exclude,glob)**/*.log' \
  ':(exclude,glob)**/*.tmp' \
  ':(exclude,glob)**/.DS_Store'
```

Expected: 源码、配置、测试、SQL、脚本、文档和原生工程进入 index；排除项保持未暂存或未跟踪。

- [ ] **Step 3: 反向验证没有排除项进入 index**

Run:

```bash
if git diff --cached --name-only | rg '(^docs/audit-screenshots/|\.jar$|\.pyc$|(^|/)__pycache__/|^\.codex/|^\.claude-flow/|(^|/)(target|dist|build)/|\.log$|\.tmp$|(^|/)\.DS_Store$)'; then
  echo 'ERROR: excluded paths are staged'
  exit 1
else
  echo 'OK: no excluded paths are staged'
fi
```

Expected: `OK: no excluded paths are staged`。

- [ ] **Step 4: 验证三组核心功能入口已进入 index**

Run:

```bash
git diff --cached --name-only | rg '^(erp-modules/erp-inventory/src/main/java/com/erp/inventory/(constant/InvItemTypes|domain/vo/InventoryItemSnapshot|service/impl/InventoryItemResolver)\.java|erp-ui/src/views/inventory/components/InventoryItemSelect\.vue|sql/erp_inventory_generic_item_stock_20260710\.sql)$'
git diff --cached --name-only | rg '^(erp-modules/erp-oa/src/main/java/com/erp/oa/(domain/OaFixedAssetQuotaMonth|domain/dto/OaFixedAssetRepairBatch(Item|Request)|mapper/OaFixedAssetQuotaMonthMapper)\.java|erp-modules/erp-oa/src/main/resources/mapper/oa/OaFixedAssetQuotaMonthMapper\.xml|sql/erp_fixed_asset_monthly_quota_20260709\.sql)$'
git diff --cached --name-only | rg '^(erp-ui/src/views/mobile/(styles/mobileSystem\.scss|mobileViewport\.js|feature/components/mobileOverlayPortal\.js|feature/mobileLineItemPricing\.js)|erp-ui/android/settings\.gradle|erp-ui/ios/)'
```

Expected: 三条命令都至少输出一个匹配文件；完整入口文件均可在输出中确认。

### Task 3: 审计并提交本地源码快照

**Files:**

- Commit: Task 2 暂存的全部纳入范围文件。

- [ ] **Step 1: 检查空白错误和暂存规模**

Run:

```bash
git diff --cached --check
git diff --cached --shortstat
git diff --cached --dirstat=files,0
```

Expected: `git diff --cached --check` 无输出；其余命令给出按目录的实际规模。

- [ ] **Step 2: 只按文件名扫描明显私钥和硬编码凭据**

Run:

```bash
matches=$(
  git diff --cached --name-only --diff-filter=ACMR -z |
  xargs -0 rg -I -l --hidden \
    -e '-----BEGIN (RSA |OPENSSH |EC |DSA )?PRIVATE KEY-----' \
    -e 'AKIA[0-9A-Z]{16}' \
    -e '(password|passwd|secret|access[_-]?key|token)[[:space:]]*[:=][[:space:]]*[\x22\x27]?[A-Za-z0-9_+/.=-]{12,}' \
    2>/dev/null || true
)
if [ -n "$matches" ]; then
  printf 'REVIEW_CREDENTIAL_FILES\n%s\n' "$matches"
  exit 1
fi
echo 'OK: no obvious credential files found'
```

Expected: 只输出 `OK: no obvious credential files found`。若输出文件名，停止并人工检查；不得输出匹配行或凭据内容。

- [ ] **Step 3: 运行仓库已有前端凭据扫描测试**

Run:

```bash
node erp-ui/test/frontendSecretScanner.test.js
```

Expected: 测试通过。

- [ ] **Step 4: 检查暂存状态后创建单一源码快照提交**

Run:

```bash
git status --short
git commit -m 'chore: snapshot local source and documentation changes'
```

Expected: 提交成功；提交不包含排除项。

- [ ] **Step 5: 验证提交和剩余脏文件边界**

Run:

```bash
git show --stat --summary --oneline HEAD
git diff-tree --no-commit-id --name-only -r HEAD | rg '(^docs/audit-screenshots/|\.jar$|\.pyc$|(^|/)__pycache__/|^\.codex/|^\.claude-flow/|(^|/)(target|dist|build)/|\.log$|\.tmp$|(^|/)\.DS_Store$)' && exit 1 || true
git status --short --untracked-files=all
```

Expected: 快照提交统计可见；第二条命令无匹配；剩余状态只属于排除范围。

### Task 4: 建立本地源码快照验证基线

**Files:**

- Test: Maven 全部模块。
- Test: `erp-ui/test/*.test.js`
- Build: `erp-ui` 生产包。

- [ ] **Step 1: 运行后端完整测试**

Run:

```bash
mvn test
```

Expected: 24 个 Reactor 模块全部 `SUCCESS`；若失败，记录首个失败模块、测试类和异常，不修改源码。

- [ ] **Step 2: 运行前端完整测试**

Run:

```bash
npm --prefix erp-ui run test
```

Expected: 所有测试通过，包括基线中曾因缺少 `erp-ui/android/settings.gradle` 失败的 `mobileAppShell.test.js`；若失败，记录首个失败测试和断言，不修改源码。

- [ ] **Step 3: 运行生产构建**

Run:

```bash
npm --prefix erp-ui run build:prod
```

Expected: Vue 生产构建退出码为 0；体积警告允许记录，但编译错误不允许忽略。

- [ ] **Step 4: 验证测试未产生新的可提交文件**

Run:

```bash
git status --short --untracked-files=all
```

Expected: 状态与 Task 3 Step 5 的排除项边界一致；没有新的源码、锁文件或配置变化。

### Task 5: 生成合并前功能差距并等待用户批准

**Files:**

- Read: `codex/local-source-integration-20260712`
- Read: `7月12号`
- Read only: `/Users/liuxingyu/.config/superpowers/worktrees/ERP-NEW/merge-7月12号`

- [ ] **Step 1: 记录双方提交关系和总体差异**

Run:

```bash
git merge-base 7月12号 codex/local-source-integration-20260712
git rev-list --left-right --count 7月12号...codex/local-source-integration-20260712
git log --left-right --graph --decorate --oneline 7月12号...codex/local-source-integration-20260712
git diff --shortstat 7月12号..codex/local-source-integration-20260712
git diff --dirstat=files,0 7月12号..codex/local-source-integration-20260712
```

Expected: 输出共同祖先、双方独有提交、文件规模和目录分布。

- [ ] **Step 2: 计算双方同时修改的文件**

Run:

```bash
base=$(git merge-base 7月12号 codex/local-source-integration-20260712)
comm -12 \
  <(git diff --name-only "$base"..7月12号 | sort) \
  <(git diff --name-only "$base"..codex/local-source-integration-20260712 | sort)
```

Expected: 输出所有重叠路径；重点核对路由、请求层、库存页面、移动端共享组件及相关测试。

- [ ] **Step 3: 用 merge-tree 预演冲突但不修改 worktree 或分支引用**

Run:

```bash
base=$(git merge-base 7月12号 codex/local-source-integration-20260712)
merge_preview=$(git merge-tree "$base" 7月12号 codex/local-source-integration-20260712) || exit 1
printf '%s\n' "$merge_preview" |
  rg '^(changed in both|added in both|removed in remote|removed in local|CONFLICT|  base|  our|  their)' ||
  echo 'no textual merge conflicts predicted'
```

Expected: 输出潜在冲突分类；命令不创建 merge 状态、不移动任何分支。

- [ ] **Step 4: 按功能列出本地快照新增能力**

Run:

```bash
git diff --name-only 7月12号..codex/local-source-integration-20260712 -- \
  erp-modules/erp-inventory erp-ui/src/views/inventory erp-ui/test/inventoryGenericItemFlow.test.js sql/erp_inventory_generic_item_stock_20260710.sql
git diff --name-only 7月12号..codex/local-source-integration-20260712 -- \
  erp-modules/erp-oa erp-ui/src/views/oa/fixedAsset erp-ui/test/fixedAssetFeature.test.js sql/erp_fixed_asset_monthly_quota_20260709.sql
git diff --name-only 7月12号..codex/local-source-integration-20260712 -- \
  erp-ui/src/views/mobile erp-ui/android erp-ui/ios erp-ui/test
git diff --name-only 7月12号..codex/local-source-integration-20260712 -- \
  scripts docker/mysql/db sql docs
```

Expected: 分别得到库存通用商品、固定资产、移动端/原生工程、部署/数据库/文档四组路径清单。

- [ ] **Step 5: 列出 `7月12号` 独有能力**

Run:

```bash
git log --oneline codex/local-source-integration-20260712..7月12号
git diff --name-only codex/local-source-integration-20260712..7月12号 -- \
  erp-common/erp-common-core/src/main/java/com/erp/common/core/domain/todo \
  erp-modules/erp-inventory/src/main/java/com/erp/inventory/service/impl/InvTodoServiceImpl.java \
  erp-modules/erp-oa/src/main/java/com/erp/oa/service/impl/OaTodoServiceImpl.java \
  erp-modules/erp-system/src/main/java/com/erp/system/service/impl/SysTodoServiceImpl.java \
  erp-ui/src/views/workbench/todo erp-ui/src/views/mobile/todo erp-ui/src/views/mobile/hr
```

Expected: 明确列出统一待办、HR 人事/入职以及目标分支其他独有提交和文件。

- [ ] **Step 6: 只读盘点目标 worktree 的在途 HR 改动**

Run:

```bash
git -C /Users/liuxingyu/.config/superpowers/worktrees/ERP-NEW/merge-7月12号 status --short --untracked-files=all
git -C /Users/liuxingyu/.config/superpowers/worktrees/ERP-NEW/merge-7月12号 diff --cached --shortstat
git -C /Users/liuxingyu/.config/superpowers/worktrees/ERP-NEW/merge-7月12号 diff --shortstat
git -C /Users/liuxingyu/.config/superpowers/worktrees/ERP-NEW/merge-7月12号 diff --cached --name-only |
  awk -F/ '{key=$1; if (NF>1) key=key"/"$2; count[key]++} END {for (key in count) print count[key], key}' |
  sort -nr
```

Expected: 只读输出目标侧已暂存、未暂存规模和目录分组；不执行 add、commit、stash、restore 或 reset。

- [ ] **Step 7: 向用户展示差距清单并硬暂停**

展示内容必须包括：

1. 本地快照新增/增强功能；
2. `7月12号` 独有功能；
3. 双方重叠文件与预演冲突；
4. 目标侧在途 HR 改动；
5. 排除项统计；
6. 每组功能的建议保留/组合策略；
7. 本地快照 Maven、前端测试和生产构建结果。

Expected: 在用户明确回复“继续合并”前，不执行 Task 6 及后续步骤。

### Task 6: 获批后准备干净目标 worktree

**Files:**

- Target worktree: `/Users/liuxingyu/.config/superpowers/worktrees/ERP-NEW/merge-7月12号`

- [ ] **Step 1: 再次确认用户批准记录**

Expected: 最近一条用户指令明确允许继续合并；“可以”“继续”必须是对 Task 5 差距清单的直接回复。

- [ ] **Step 2: 验证目标 worktree 已由其所有者处理完在途改动**

Run:

```bash
git -C /Users/liuxingyu/.config/superpowers/worktrees/ERP-NEW/merge-7月12号 status --short --branch --untracked-files=all
```

Expected: 仅输出 `## 7月12号`。若仍有文件状态，停止并请用户决定由谁提交、转移或继续处理；本任务不得擅自操作它们。

- [ ] **Step 3: 目标分支若已前进则重新生成重叠和 merge-tree 结果**

Run:

```bash
git rev-parse 7月12号
base=$(git merge-base 7月12号 codex/local-source-integration-20260712)
comm -12 \
  <(git diff --name-only "$base"..7月12号 | sort) \
  <(git diff --name-only "$base"..codex/local-source-integration-20260712 | sort)
merge_preview=$(git merge-tree "$base" 7月12号 codex/local-source-integration-20260712) || exit 1
printf '%s\n' "$merge_preview" |
  rg '^(changed in both|added in both|removed in remote|removed in local|CONFLICT|  base|  our|  their)' ||
  echo 'no textual merge conflicts predicted'
```

Expected: 使用最新目标提交得到最终重叠和冲突清单；若比 Task 5 新增业务语义冲突，再次展示并等待用户选择。

### Task 7: 三方合并并逐文件解决冲突

**Files:**

- Merge all approved source snapshot paths into `7月12号`.
- Known shared-file candidates include:
  - `erp-ui/src/router/index.js`
  - `erp-ui/src/utils/request.js`
  - `erp-ui/src/views/inventory/purchase/index.vue`
  - `erp-ui/src/views/inventory/sales/index.vue`
  - `erp-ui/src/views/inventory/stock/index.vue`
  - `erp-ui/src/views/inventory/transfer/index.vue`
  - `erp-ui/src/views/mobile/components/MobileWorkbenchShell.vue`
  - `erp-ui/src/views/mobile/contract/index.vue`
  - `erp-ui/src/views/mobile/feature/components/MobileActionDialog.vue`
  - `erp-ui/src/views/mobile/feature/featureActionRuntime.js`
  - `erp-ui/src/views/mobile/feature/featureActionService.js`
  - `erp-ui/src/views/mobile/feature/featureActions.js`
  - `erp-ui/src/views/mobile/feature/index.vue`
  - `erp-ui/src/views/mobile/inventory/index.vue`
  - `erp-ui/src/views/mobile/inventory/workbenchData.js`
  - `erp-ui/src/views/mobile/mobileNavigation.js`
  - `erp-ui/src/views/mobile/mobileRouteDefinitions.js`
  - `erp-ui/src/views/mobile/signPackage/index.vue`
  - `erp-ui/src/views/oa/fixedAsset/repair/index.vue`
  - Inventory/OA service tests reported by Task 5 Step 2.

- [ ] **Step 1: 在目标 worktree 启动无提交合并**

Run:

```bash
git -C /Users/liuxingyu/.config/superpowers/worktrees/ERP-NEW/merge-7月12号 merge \
  --no-ff --no-commit codex/local-source-integration-20260712
```

Expected: Git 暂停在待提交 merge 状态；无冲突则全部变化进入 index，有冲突则明确列出 `UU`、`AA`、`UD` 或 `DU` 路径。

- [ ] **Step 2: 列出并逐个检查冲突三阶段内容**

Run:

```bash
cd /Users/liuxingyu/.config/superpowers/worktrees/ERP-NEW/merge-7月12号
git diff --name-only --diff-filter=U
git diff --name-only --diff-filter=U -z |
while IFS= read -r -d '' path; do
  printf '%s\n' "$path"
  git ls-files -u -- "$path"
done
```

Expected: 每个冲突文件均有明确 stage 记录，包含空格的路径也能完整读取。

- [ ] **Step 3: 按功能组合规则编辑冲突文件**

Resolution rules:

- 路由、导航和权限文件同时保留目标侧统一待办/HR 路由与本地移动端、库存、固定资产路由。
- `request.js` 同时保留目标侧待办/HR 请求行为与本地生产 API、移动端错误处理和数据隔离行为。
- 库存服务与页面保留目标侧待办刷新/聚焦，同时加入通用商品身份 `itemType`、`itemId` 和库存解析器。
- 固定资产服务与页面保留目标侧待办集成，同时加入月度额度、系统计算金额和批量报修。
- 移动端共享组件保留目标侧 HR/待办入口，同时加入 viewport、overlay portal、统一样式与行项目定价。
- 禁止对共享文件整文件执行 `git checkout --ours`、`git checkout --theirs` 或等价覆盖。
- 业务语义无法同时成立时，展示两侧行为和受影响流程，等待用户决定后再编辑。

使用 `apply_patch` 编辑每个冲突文件；不得通过 shell 重定向、整树复制或生成脚本覆盖文件。

Expected: 每个文件都形成组合后的单一实现，无冲突标记。

- [ ] **Step 4: 逐个暂存已解决文件并验证冲突清零**

Run:

```bash
while IFS= read -r -d '' path; do
  if [ -e "$path" ] && rg -n '^(<<<<<<<|=======|>>>>>>>)' -- "$path"; then
    printf 'ERROR: conflict markers remain in %s\n' "$path"
    exit 1
  fi
  git add -- "$path"
done < <(git diff --name-only --diff-filter=U -z)
git diff --name-only --diff-filter=U
git diff --cached --check
```

Expected: 每个已审阅的冲突路径被单独暂存；最终冲突列表为空，空白检查无输出。

### Task 8: 定向验证并创建目标合并提交

**Files:**

- Test: inventory、OA、system Maven 模块及其依赖。
- Test: 本地新增功能与目标独有待办/HR 前端测试。

- [ ] **Step 1: 运行后端受影响模块测试**

Run:

```bash
mvn -pl erp-modules/erp-inventory,erp-modules/erp-oa,erp-modules/erp-system -am test
```

Expected: 所有选定模块及依赖 `SUCCESS`。

- [ ] **Step 2: 运行核心本地新增功能测试**

Run:

```bash
node erp-ui/test/inventoryGenericItemFlow.test.js
node erp-ui/test/fixedAssetFeature.test.js
node erp-ui/test/mobileProgressiveRedesign.test.js
node erp-ui/test/mobileReplenishmentOverlayRegression.test.js
node erp-ui/test/mobileAppShell.test.js
```

Expected: 五个测试全部通过。

- [ ] **Step 3: 运行目标独有功能回归测试**

Run:

```bash
node erp-ui/test/unifiedTodoAggregation.test.js
node erp-ui/test/unifiedTodoDesktop.test.js
node erp-ui/test/unifiedTodoMobile.test.js
node erp-ui/test/unifiedTodoStore.test.js
node erp-ui/test/hrPersonnelManagement.test.js
node erp-ui/test/hrMenuPermissions.test.js
```

Expected: 六个测试全部通过。

- [ ] **Step 4: 检查合并边界并创建提交**

Run:

```bash
git diff --name-only --diff-filter=U
git diff --cached --check
git status --short
git commit -m 'merge: integrate local source into July 12 branch'
```

Expected: 无未解决冲突和空白错误；创建具有两个父提交的 merge commit。

- [ ] **Step 5: 验证合并提交结构**

Run:

```bash
git show -s --format='%H%n%P%n%s' HEAD
git merge-base --is-ancestor codex/local-source-integration-20260712 HEAD
```

Expected: 第一条输出当前提交、两个父提交和合并标题；第二条退出码为 0。

### Task 9: 提交后重跑完整验证并报告

**Files:**

- Test: repository-wide Maven tests。
- Test: all frontend Node tests。
- Build: production frontend bundle。

- [ ] **Step 1: 重跑 Maven 完整测试**

Run:

```bash
mvn test
```

Expected: 24 个 Reactor 模块全部 `SUCCESS`，测试失败数为 0。

- [ ] **Step 2: 重跑前端完整测试**

Run:

```bash
npm --prefix erp-ui run test
```

Expected: 全部前端测试通过，`mobileAppShell.test.js` 不再是既有失败。

- [ ] **Step 3: 重跑生产构建**

Run:

```bash
npm --prefix erp-ui run build:prod
```

Expected: 生产构建退出码为 0；无编译错误。

- [ ] **Step 4: 最终状态与排除项验证**

Run:

```bash
git status --short --branch --untracked-files=all
git diff-tree --no-commit-id --name-only -r HEAD^1 HEAD |
  rg '(^docs/audit-screenshots/|\.jar$|\.pyc$|(^|/)__pycache__/|^\.codex/|^\.claude-flow/|(^|/)(target|dist|build)/|\.log$|\.tmp$|(^|/)\.DS_Store$)' && exit 1 || true
```

Expected: 目标 worktree 无文件状态；合并提交不包含排除项。

- [ ] **Step 5: 向用户报告最终证据**

报告必须包括：源码快照提交、目标 merge commit、父提交、文件统计、排除项统计、Maven 结果、前端测试结果、生产构建结果，以及任何仍存在但确认不是本次合并引入的警告。
