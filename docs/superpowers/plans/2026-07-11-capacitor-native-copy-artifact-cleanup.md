# Capacitor Native Copy Artifact Cleanup Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Remove Finder-style numbered copies from safe Capacitor-generated locations, prevent recurrence, and resync Android/iOS assets without deployment.

**Architecture:** Extend the existing native production-hardening test with a temporary Android/iOS fixture and a real-workspace artifact gate. Expand the existing hardener so it recursively removes numbered entries only inside allowlisted generated roots and exact generated sibling directories, then retain the current Cordova configuration hardening and asset verification chain.

**Tech Stack:** Node.js filesystem APIs, Node `assert`, Vue CLI production build, Capacitor 8.

---

### Task 1: Add a failing generated-artifact regression test

**Files:**
- Modify: `erp-ui/test/mobileNativeProductionHardening.test.js`

- [x] **Step 1: Build an isolated native fixture**

Add temporary Android/iOS directory helpers using `os.tmpdir()`, `fs.mkdtempSync`, `fs.mkdirSync`, `fs.writeFileSync`, and `fs.copyFileSync`. Include standard resources, numbered files, numbered directories, `AndroidManifest.xml`, `Info.plist`, and both standard `config.xml` files.

- [x] **Step 2: Execute the real hardener against the fixture**

Copy `scripts/harden-capacitor-native-config.cjs` into the fixture's `scripts/` directory and run it with:

```js
execFileSync(process.execPath, [fixtureScript], { stdio: "pipe" })
```

- [x] **Step 3: Specify the required result**

Recursively collect names matching:

```js
/ \d+(?=\.|$)/
```

inside allowlisted generated roots, and collect only exact generated siblings/config copies from their parent directories. Assert that the result is empty and that protected/canonical files still exist.

- [x] **Step 4: Verify RED**

Run:

```bash
cd erp-ui
node test/mobileNativeProductionHardening.test.js
```

Expected: FAIL because the current script leaves `public 2`, numbered Web assets, build directories, and plugin outputs in the temporary fixture.

### Task 2: Implement scoped recursive cleanup

**Files:**
- Modify: `erp-ui/scripts/harden-capacitor-native-config.cjs`

- [x] **Step 1: Define the copy-name predicate**

Use the Finder-copy predicate only inside allowlisted generated roots:

```js
const numberedCopyPattern = / \d+(?=\.|$)/
```

- [x] **Step 2: Delete numbered entries recursively**

Walk the generated roots with `fs.readdirSync(directory, { withFileTypes: true })`. When a matching entry is found, delete it with:

```js
fs.rmSync(entryPath, { recursive: entry.isDirectory(), force: true })
```

Do not descend into an entry after deleting it.

- [x] **Step 3: Delete only exact generated sibling copies**

At the Android/iOS parent levels, match only `capacitor-cordova-*-plugins <数字>`, `public <数字>`, and `config <数字>.xml`. Do not recursively scan native source parents.

- [x] **Step 4: Preserve config hardening and report cleanup**

Keep wildcard-access removal for canonical `config.xml`. Print the number of removed artifacts and their paths before the existing completion message.

- [x] **Step 5: Verify the isolated fixture passes**

Run:

```bash
cd erp-ui
node test/mobileNativeProductionHardening.test.js
```

Expected at this intermediate point: the temporary-fixture behavior passes and the real-workspace gate fails by listing current numbered native artifacts.

### Task 3: Clean and regenerate native outputs

**Files:**
- Clean only: allowlisted generated roots and exact copied siblings from the design
- Regenerate: `erp-ui/android/app/src/main/assets`, `erp-ui/ios/App/App/public`, Capacitor plugin outputs

- [x] **Step 1: Run the hardened cleaner on the real workspace**

```bash
cd erp-ui
node scripts/harden-capacitor-native-config.cjs
```

Expected: numbered native artifacts are reported and removed; canonical/native source files remain.

- [x] **Step 2: Verify GREEN before regeneration**

```bash
node test/mobileNativeProductionHardening.test.js
```

Expected: PASS.

- [x] **Step 3: Rebuild and sync without deployment**

```bash
npm run app:sync
```

Expected: production build, Capacitor sync, hardening, and Android/iOS asset verification all exit with code 0.

### Task 4: Full verification and scope review

**Files:**
- Verify: `erp-ui/test/*.test.js`
- Verify: `erp-ui/dist`, Android/iOS standard generated resources

- [x] **Step 1: Run all Node regression tests**

```bash
cd erp-ui
npm test
```

Expected: zero failures.

- [x] **Step 2: Run a fresh production build and native asset verifier**

```bash
npm run build:prod
node scripts/verify-capacitor-sync.cjs
```

Expected: both commands exit with code 0 and both native shells match `dist`.

- [x] **Step 3: Confirm no numbered native artifact remains**

Run the native hardening test again and inspect the allowed roots. Expected: zero copied artifacts.

- [x] **Step 4: Review the diff and protected files**

Run `git diff --check`, inspect only the two modified implementation/test files and the two documentation files, and confirm `AndroidManifest.xml`, `Info.plist`, Xcode/Gradle project files, and canonical `config.xml` remain present.

No commit, signing, device installation, or deployment is performed in this shared dirty worktree.

### Task 5: Harden workflow boundaries against File Provider restores

**Files:**
- Modify: `erp-ui/package.json`
- Modify: `erp-ui/test/mobileNativeProductionHardening.test.js`
- Modify: `docs/superpowers/specs/2026-07-11-capacitor-native-copy-artifact-cleanup-design.md`

- [x] **Step 1: Capture the environmental root cause**

Confirm the restored file retains its old birth/modify timestamps while receiving a new directory-added/change timestamp. Confirm Desktop has `com.apple.file-provider-domain-id`, iCloud Desktop is enabled, and `brctl status` is applying changes under `ERP-NEW`.

- [x] **Step 2: Write and verify the failing workflow test**

Require `app:sync` and `app:verify` to invoke `node scripts/harden-capacitor-native-config.cjs` twice, before native work and before final verification. Run the native hardening test and confirm it fails because the existing scripts do not start with the cleaner.

- [x] **Step 3: Add pre/post cleanup to native workflows**

Set the scripts to:

```json
"app:sync": "node scripts/harden-capacitor-native-config.cjs && npm run build:prod && NODE_ENV=production npx cap sync && node scripts/harden-capacitor-native-config.cjs && node scripts/verify-capacitor-sync.cjs",
"app:verify": "node scripts/harden-capacitor-native-config.cjs && npm run test && node scripts/harden-capacitor-native-config.cjs && node scripts/verify-capacitor-sync.cjs"
```

- [x] **Step 4: Re-run sync, verification, and recurrence checks**

Run `npm run app:sync`, `npm run app:verify`, the direct native test, and a delayed copied-artifact scan. Require zero failures at the workflow boundaries and record any later system-level restore separately from application correctness.
