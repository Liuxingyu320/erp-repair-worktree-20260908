# July 12 Branch Merge Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Create branch `7月12号` that preserves the complete committed histories of `codex/hr-workspace-snapshot-20260711` and `6月13号` without modifying or deleting either source branch.

**Architecture:** Start from the HR workspace snapshot commit and merge the June 13 line with an explicit two-parent merge commit. Resolve conflicts by combining independent features: retain the snapshot line's cloud-drive and contract work, while restoring the June 13 line's unified todo and HR personnel/onboarding work. Validate the merged tree with full Maven tests, frontend tests, and a production frontend build.

**Tech Stack:** Git worktrees, Git merge machinery, Maven, Node.js, Vue CLI.

---

## Source and safety invariants

- `codex/hr-workspace-snapshot-20260711` must remain at `410f6bccb597ba0eb97de35a782721e758e18afd`.
- `6月13号` must remain at `87589a34aaf1be33f409fd6ca386fd6e72e65e38`.
- `7月12号` starts from the HR snapshot and receives `6月13号` through a non-fast-forward merge.
- The dirty primary worktree is out of scope; none of its uncommitted files are copied into this branch.
- The pre-merge frontend baseline is 103 tests with one known failure because the committed branches do not contain `erp-ui/android/settings.gradle`; the merge must not introduce additional frontend failures.
- The pre-merge Maven baseline is a successful full reactor build.

### Task 1: Confirm isolated baseline

**Files:**
- Create: `docs/superpowers/plans/2026-07-12-merge-june13-hr-snapshot.md`

- [x] **Step 1: Confirm branch and source identities**

Run:

```bash
git branch --show-current
git rev-parse HEAD
git rev-parse refs/heads/codex/hr-workspace-snapshot-20260711
git rev-parse refs/heads/6月13号
```

Expected: current branch is `7月12号`; current HEAD and the HR snapshot both resolve to `410f6bccb597ba0eb97de35a782721e758e18afd`; the June 13 branch resolves to `87589a34aaf1be33f409fd6ca386fd6e72e65e38`.

- [x] **Step 2: Record baseline verification**

Run:

```bash
mvn test
npm --prefix erp-ui run test
```

Expected: Maven succeeds. Frontend reports exactly the known `mobileAppShell.test.js` missing-native-shell failure and no other failures.

### Task 2: Merge and resolve conflicts

**Files:**
- Modify: files reported by `git diff --name-only --diff-filter=U` after the merge attempt.

- [x] **Step 1: Start a two-parent merge without committing**

Run:

```bash
git merge --no-ff --no-commit refs/heads/6月13号
```

Expected: Git either stages a clean merge or stops with an explicit conflict list.

- [x] **Step 2: Resolve every conflict by feature ownership**

For each conflicted path, inspect both stages:

```bash
git diff --name-only --diff-filter=U
git show :2:<path>
git show :3:<path>
```

Resolution rules:

- Keep cloud-drive and contract-signing additions from the HR snapshot side.
- Keep unified-todo, notice-read-all, HR personnel, HR onboarding, and their tests from the June 13 side.
- In shared router, store, controller, service, mapper, and SQL files, combine both feature registrations and imports; do not accept either side wholesale when it would remove the other feature.
- Preserve all non-conflicting migrations from both sides.
- Remove all conflict markers and stage only the resolved merge tree and this plan.

- [x] **Step 3: Validate merge structure before commit**

Run:

```bash
git diff --name-only --diff-filter=U
git diff --cached --check
git status --short
```

Expected: no unmerged paths, no whitespace errors, and no unrelated generated artifacts staged.

### Task 3: Verify and commit the merged branch

**Files:**
- Test: all Maven and frontend test sources contained in the merged tree.

- [x] **Step 1: Run full backend verification**

Run:

```bash
mvn test
```

Expected: reactor build succeeds with zero test failures.

- [x] **Step 2: Run frontend regression and build verification**

Run:

```bash
npm --prefix erp-ui run test
npm --prefix erp-ui run build:prod
```

Expected: frontend tests have no failures beyond the single documented missing-native-shell baseline; the production build succeeds.

- [x] **Step 3: Create the merge commit**

Run:

```bash
git commit -m "merge: combine July 11 HR snapshot and June 13 line"
```

Expected: a merge commit with first parent `410f6bccb597ba0eb97de35a782721e758e18afd` and second parent `87589a34aaf1be33f409fd6ca386fd6e72e65e38`.

- [x] **Step 4: Verify branch preservation and ancestry**

Run:

```bash
git rev-parse refs/heads/codex/hr-workspace-snapshot-20260711
git rev-parse refs/heads/6月13号
git merge-base --is-ancestor refs/heads/codex/hr-workspace-snapshot-20260711 refs/heads/7月12号
git merge-base --is-ancestor refs/heads/6月13号 refs/heads/7月12号
git show -s --format='%H%n%P%n%s' refs/heads/7月12号
```

Expected: both source branch hashes are unchanged, both are ancestors of `7月12号`, and the target branch points to the verified merge commit.
