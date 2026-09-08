# Frontend Dependency Hardening Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Remove the remaining non-framework production dependency vulnerabilities that can be fixed without migrating Vue 2.

**Architecture:** Keep the runtime surface unchanged for users. Add tests that enforce the dependency security gate, then upgrade only ECharts and highlight.js and adapt the code generator preview to the highlight.js v11 API.

**Tech Stack:** Vue 2, Vue CLI 4, webpack 4, npm, ECharts, highlight.js, Node-based repository tests.

---

### Task 1: Dependency Security Gate

**Files:**
- Modify: `erp-ui/test/frontendTestGate.test.js`
- Modify: `erp-ui/package.json`

- [ ] **Step 1: Write the failing test**

Add assertions to `erp-ui/test/frontendTestGate.test.js`:

```js
assert.strictEqual(
  packageJson.scripts["audit:prod"],
  "npm audit --omit=dev --audit-level=moderate --registry=https://registry.npmjs.org",
  "production dependency audit should fail on moderate or higher vulnerabilities"
)

assert.strictEqual(
  packageJson.dependencies.echarts,
  "6.1.0",
  "ECharts should be upgraded past the vulnerable 5.x line"
)

assert.strictEqual(
  packageJson.dependencies["highlight.js"],
  "11.11.1",
  "highlight.js should be upgraded past the vulnerable 9.x line"
)
```

- [ ] **Step 2: Verify the test fails**

Run:

```bash
cd erp-ui && node test/frontendTestGate.test.js
```

Expected: FAIL because `audit:prod` is missing and the vulnerable versions are still present.

- [ ] **Step 3: Add the package script and dependency versions**

In `erp-ui/package.json`, add:

```json
"audit:prod": "npm audit --omit=dev --audit-level=moderate --registry=https://registry.npmjs.org"
```

Set dependencies:

```json
"echarts": "6.1.0",
"highlight.js": "11.11.1"
```

- [ ] **Step 4: Verify the gate passes**

Run:

```bash
cd erp-ui && node test/frontendTestGate.test.js
```

Expected: PASS.

### Task 2: highlight.js v11 Compatibility

**Files:**
- Modify: `erp-ui/src/views/tool/gen/index.vue`
- Modify: `erp-ui/test/frontendTestGate.test.js`

- [ ] **Step 1: Write the failing test**

Add source assertions to `erp-ui/test/frontendTestGate.test.js`:

```js
const generatorSource = fs.readFileSync(path.join(rootDir, "src/views/tool/gen/index.vue"), "utf8")

assert.ok(
  generatorSource.includes('import hljs from "highlight.js/lib/core"'),
  "code generator preview should import the highlight.js v11 core build"
)

assert.ok(
  generatorSource.includes("hljs.highlight(code || \"\", { language, ignoreIllegals: true })"),
  "code generator preview should use the highlight.js v11 highlight signature"
)
```

- [ ] **Step 2: Verify the test fails**

Run:

```bash
cd erp-ui && node test/frontendTestGate.test.js
```

Expected: FAIL because the source still imports `highlight.js/lib/highlight` and calls the old v9 signature.

- [ ] **Step 3: Update the code generator preview**

In `erp-ui/src/views/tool/gen/index.vue`, replace:

```js
import hljs from "highlight.js/lib/highlight"
```

with:

```js
import hljs from "highlight.js/lib/core"
```

Replace:

```js
const result = hljs.highlight(language, code || "", true)
```

with:

```js
const result = hljs.highlight(code || "", { language, ignoreIllegals: true })
```

- [ ] **Step 4: Verify the gate passes**

Run:

```bash
cd erp-ui && node test/frontendTestGate.test.js
```

Expected: PASS.

### Task 3: Full Verification

**Files:**
- Verify: `erp-ui/package.json`
- Verify: `erp-ui/src/views/tool/gen/index.vue`

- [ ] **Step 1: Install upgraded dependencies**

Run:

```bash
cd erp-ui && npm install echarts@6.1.0 highlight.js@11.11.1 --save
```

Expected: npm completes without dependency resolution failure.

- [ ] **Step 2: Run frontend tests**

Run:

```bash
cd erp-ui && npm test
```

Expected: 80+ tests run, 0 failed.

- [ ] **Step 3: Run production build**

Run:

```bash
cd erp-ui && npm run build:prod
```

Expected: build exits 0. Existing bundle size warnings may remain.

- [ ] **Step 4: Run dependency audit**

Run:

```bash
cd erp-ui && npm run audit:prod
```

Expected: exit 0 if only low-severity Vue 2 ecosystem advisories remain.
