# Desktop Audit Remediation R1 Security and Context Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Close the four confirmed desktop injection boundaries and make warehouse procurement routes consistently require and preserve an organization context.

**Architecture:** Sanitize notice HTML when it crosses the backend write boundary and again immediately before frontend rich-text rendering. Replace the other two HTML-rendering paths with plain text or Vue text interpolation. Keep organization-route classification in one small policy utility so the navigation guard and its tests share an explicit contract.

**Tech Stack:** Java 17, Spring Boot, OWASP Java HTML Sanitizer, Vue 2, Element UI, DOMPurify, Node test runner, JUnit 5, Mockito, Maven.

---

## Scope boundary

This plan implements the independently shippable R1 security and context-policy work on the clean branch `codex/desktop-audit-remediation-r1`.

HR exact server-side pagination is intentionally a separate plan. The audited HR pages and controllers currently exist only as untracked/mixed changes in the original workspace, not in this branch's committed baseline. They must first be integrated as a clean baseline so pagination work can be tested without copying unrelated user changes.

Known baseline exceptions: the full frontend suite currently has two unrelated environment/generated-file failures (`application-local.yml` and the untracked Android generated directory). The R1-targeted suites are green before implementation.

## Task 1: Sanitize notice HTML at the backend write boundary

**Files:**

- Modify: `erp-common/erp-common-core/pom.xml`
- Create: `erp-common/erp-common-core/src/main/java/com/erp/common/core/utils/html/HtmlSanitizer.java`
- Create: `erp-common/erp-common-core/src/test/java/com/erp/common/core/utils/html/HtmlSanitizerTest.java`
- Modify: `erp-modules/erp-system/src/main/java/com/erp/system/service/impl/SysNoticeServiceImpl.java`
- Create: `erp-modules/erp-system/src/test/java/com/erp/system/service/impl/SysNoticeServiceImplTest.java`

- [ ] **Step 1: Write the sanitizer unit tests**

Cover script tags, event-handler attributes, `javascript:` URLs, normal formatting, lists, tables, HTTPS links, null, and blank content. The malicious payload assertions must check both removal of executable content and retention of visible text.

```java
@Test
void removesExecutableMarkupButKeepsVisibleText() {
    String input = "<p onclick=\"alert(1)\">正文<script>alert(2)</script>"
        + "<a href=\"javascript:alert(3)\">链接</a></p>";

    String sanitized = HtmlSanitizer.sanitize(input);

    assertThat(sanitized).contains("正文", "链接");
    assertThat(sanitized).doesNotContain("script", "onclick", "javascript:");
}

@Test
void keepsApprovedRichText() {
    String input = "<h2>标题</h2><p><strong>重点</strong></p>"
        + "<ul><li>一</li></ul><table><tbody><tr><td>值</td></tr></tbody></table>"
        + "<a href=\"https://example.com/help\">帮助</a>";

    assertThat(HtmlSanitizer.sanitize(input))
        .contains("<h2>标题</h2>", "<strong>重点</strong>", "<table>", "https://example.com/help");
}
```

- [ ] **Step 2: Run the sanitizer test and verify the expected RED state**

Run:

```bash
mvn -pl erp-common/erp-common-core -am \
  -Dtest=HtmlSanitizerTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: compilation fails because `HtmlSanitizer` does not exist yet.

- [ ] **Step 3: Add the sanitizer dependency and minimal implementation**

Add `com.googlecode.owasp-java-html-sanitizer:owasp-java-html-sanitizer:20260313.1` to `erp-common-core`.

```java
package com.erp.common.core.utils.html;

import org.owasp.html.PolicyFactory;
import org.owasp.html.Sanitizers;

public final class HtmlSanitizer {
    private static final PolicyFactory NOTICE_POLICY = Sanitizers.BLOCKS
        .and(Sanitizers.FORMATTING)
        .and(Sanitizers.TABLES)
        .and(Sanitizers.LINKS)
        .and(Sanitizers.IMAGES);

    private HtmlSanitizer() {
    }

    public static String sanitize(String html) {
        return html == null ? null : NOTICE_POLICY.sanitize(html);
    }
}
```

- [ ] **Step 4: Re-run the sanitizer test and verify GREEN**

Run the Step 2 command. Expected: all sanitizer cases pass.

- [ ] **Step 5: Write service tests proving insert and update sanitize before persistence**

Use Mockito `ArgumentCaptor<SysNotice>` for both `insertNotice` and `updateNotice`. Assert the mapper sees formatted safe HTML but never sees `<script>`, `onclick`, or `javascript:`.

```java
verify(sysNoticeMapper).insertNotice(noticeCaptor.capture());
assertThat(noticeCaptor.getValue().getNoticeContent())
    .contains("<strong>保留</strong>")
    .doesNotContain("<script", "onclick", "javascript:");
```

- [ ] **Step 6: Run the service test and verify the expected RED state**

Run:

```bash
mvn -pl erp-modules/erp-system -am \
  -Dtest=SysNoticeServiceImplTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: the mapper still receives the raw malicious markup.

- [ ] **Step 7: Sanitize notice content in both write methods**

Immediately before the mapper call in `insertNotice` and `updateNotice`:

```java
notice.setNoticeContent(HtmlSanitizer.sanitize(notice.getNoticeContent()));
```

- [ ] **Step 8: Re-run both backend tests and commit**

```bash
mvn -pl erp-modules/erp-system -am \
  -Dtest=HtmlSanitizerTest,SysNoticeServiceImplTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
git add erp-common/erp-common-core/pom.xml \
  erp-common/erp-common-core/src/main/java/com/erp/common/core/utils/html/HtmlSanitizer.java \
  erp-common/erp-common-core/src/test/java/com/erp/common/core/utils/html/HtmlSanitizerTest.java \
  erp-modules/erp-system/src/main/java/com/erp/system/service/impl/SysNoticeServiceImpl.java \
  erp-modules/erp-system/src/test/java/com/erp/system/service/impl/SysNoticeServiceImplTest.java
git commit -m "fix: sanitize notice html before persistence"
```

## Task 2: Sanitize notice HTML again before rich-text rendering

**Files:**

- Modify: `erp-ui/package.json`
- Modify: `erp-ui/package-lock.json`
- Create: `erp-ui/src/utils/sanitizeHtml.js`
- Modify: `erp-ui/src/layout/components/HeaderNotice/DetailView.vue`
- Create: `erp-ui/test/desktopSecurityBoundary.test.js`

- [ ] **Step 1: Write the frontend notice boundary contract test**

The test must fail unless the detail component imports the sanitizer, binds `v-html` only to a computed sanitized value, and no longer binds raw API content.

```js
assertIncludes(noticeSource, "import { sanitizeRichText } from '@/utils/sanitizeHtml'")
assertIncludes(noticeSource, 'v-html="sanitizedNoticeContent"')
assertNotIncludes(noticeSource, 'v-html="detail.noticeContent"')
```

- [ ] **Step 2: Run the test and verify RED**

```bash
cd erp-ui
node test/desktopSecurityBoundary.test.js
```

Expected: the raw notice binding is reported.

- [ ] **Step 3: Install DOMPurify and implement the rendering boundary**

```bash
cd erp-ui
npm install --save dompurify@3.4.11
```

Create one shared wrapper with the same functional allowlist used by the backend:

```js
import DOMPurify from 'dompurify'

const NOTICE_TAGS = [
  'p', 'br', 'div', 'h1', 'h2', 'h3', 'h4', 'h5', 'h6',
  'b', 'strong', 'i', 'em', 'u', 's', 'strike', 'blockquote',
  'ul', 'ol', 'li', 'table', 'thead', 'tbody', 'tfoot', 'tr', 'th', 'td',
  'a', 'img', 'span'
]

const NOTICE_ATTRIBUTES = ['href', 'title', 'target', 'rel', 'src', 'alt', 'width', 'height']

export function sanitizeRichText(html) {
  return DOMPurify.sanitize(String(html == null ? '' : html), {
    ALLOWED_TAGS: NOTICE_TAGS,
    ALLOWED_ATTR: NOTICE_ATTRIBUTES
  })
}
```

Expose a computed `sanitizedNoticeContent` and use it for the sole rich-text binding.

- [ ] **Step 4: Run the contract test and production build**

```bash
cd erp-ui
node test/desktopSecurityBoundary.test.js
npm run build:prod
```

Expected: contract test passes and webpack resolves DOMPurify successfully.

- [ ] **Step 5: Commit**

```bash
git add erp-ui/package.json erp-ui/package-lock.json \
  erp-ui/src/utils/sanitizeHtml.js \
  erp-ui/src/layout/components/HeaderNotice/DetailView.vue \
  erp-ui/test/desktopSecurityBoundary.test.js
git commit -m "fix: sanitize notice html before rendering"
```

## Task 3: Render Excel import results as text, not HTML

**Files:**

- Create: `erp-ui/src/utils/importResult.js`
- Modify: `erp-ui/src/components/ExcelImportDialog/index.vue`
- Modify: `erp-ui/test/desktopSecurityBoundary.test.js`

- [ ] **Step 1: Add failing behavior and source-boundary tests**

Load the pure formatter with `vm` and cover legacy `<br>` messages, structured counts, null responses, and malicious spreadsheet values. The source test must reject `dangerouslyUseHTMLString: true`.

```js
assert.strictEqual(
  formatImportResult({ msg: '成功 1 条<br/>失败：<img src=x onerror=alert(1)>' }),
  '成功 1 条\n失败：<img src=x onerror=alert(1)>'
)
assertNotIncludes(importSource, 'dangerouslyUseHTMLString: true')
assertIncludes(importSource, 'formatImportResult(response)')
```

The malicious tag remains literal text intentionally; Element UI must not parse it as markup.

- [ ] **Step 2: Run and verify RED**

```bash
cd erp-ui
node test/desktopSecurityBoundary.test.js
```

- [ ] **Step 3: Implement a pure formatter and safe alert**

```js
export function formatImportResult(response) {
  const payload = response && typeof response === 'object' ? response : {}
  const result = payload.data && typeof payload.data === 'object' ? payload.data : null
  if (result && (result.successCount != null || result.failureCount != null)) {
    const lines = [
      `导入完成：成功 ${Number(result.successCount || 0)} 条，失败 ${Number(result.failureCount || 0)} 条`
    ]
    if (Array.isArray(result.errors) && result.errors.length) {
      lines.push(...result.errors.map(item => String(item)))
    }
    return lines.join('\n')
  }
  return String(payload.msg || '导入完成').replace(/<br\s*\/?>/gi, '\n')
}
```

Call `$alert(formatImportResult(response), '导入结果', { dangerouslyUseHTMLString: false })`.

- [ ] **Step 4: Re-run the test and commit**

```bash
cd erp-ui
node test/desktopSecurityBoundary.test.js
git add src/utils/importResult.js src/components/ExcelImportDialog/index.vue test/desktopSecurityBoundary.test.js
git commit -m "fix: render import results as plain text"
```

## Task 4: Replace HeaderSearch HTML injection with text segments

**Files:**

- Create: `erp-ui/src/utils/textHighlight.js`
- Modify: `erp-ui/src/components/HeaderSearch/index.vue`
- Modify: `erp-ui/test/desktopSecurityBoundary.test.js`

- [ ] **Step 1: Add failing highlighter behavior and template tests**

Test case-insensitive repeated matches, regex punctuation, empty keywords, and hostile source text such as `<img src=x onerror=alert(1)>`. Assert the template contains no `v-html`.

```js
assert.deepStrictEqual(
  normalize(splitHighlightText('<img>X</img>', 'img')),
  [
    { text: '<', highlighted: false },
    { text: 'img', highlighted: true },
    { text: '>X</', highlighted: false },
    { text: 'img', highlighted: true },
    { text: '>', highlighted: false }
  ]
)
assertNotIncludes(searchSource, 'v-html=')
```

- [ ] **Step 2: Run and verify RED**

```bash
cd erp-ui
node test/desktopSecurityBoundary.test.js
```

- [ ] **Step 3: Implement index-based segmentation without HTML or regex generation**

```js
export function splitHighlightText(value, keyword) {
  const text = String(value == null ? '' : value)
  const query = String(keyword == null ? '' : keyword)
  if (!query) return [{ text, highlighted: false }]

  const segments = []
  const haystack = text.toLocaleLowerCase()
  const needle = query.toLocaleLowerCase()
  let cursor = 0
  let match = haystack.indexOf(needle)
  while (match !== -1) {
    if (match > cursor) segments.push({ text: text.slice(cursor, match), highlighted: false })
    segments.push({ text: text.slice(match, match + query.length), highlighted: true })
    cursor = match + query.length
    match = haystack.indexOf(needle, cursor)
  }
  if (cursor < text.length) segments.push({ text: text.slice(cursor), highlighted: false })
  return segments.length ? segments : [{ text, highlighted: false }]
}
```

Render each segment with Vue interpolation:

```vue
<span
  v-for="(segment, segmentIndex) in highlightSegments(item.path)"
  :key="segmentIndex"
  :class="{ highlight: segment.highlighted }"
>{{ segment.text }}</span>
```

- [ ] **Step 4: Run security test and build, then commit**

```bash
cd erp-ui
node test/desktopSecurityBoundary.test.js
npm run build:prod
git add src/utils/textHighlight.js src/components/HeaderSearch/index.vue test/desktopSecurityBoundary.test.js
git commit -m "fix: render search highlights as text segments"
```

## Task 5: Make procurement routes consistently context-gated

**Files:**

- Create: `erp-ui/src/utils/desktopContextPolicy.js`
- Modify: `erp-ui/src/permission.js`
- Create: `erp-ui/test/desktopContextPolicy.test.js`

- [ ] **Step 1: Write failing route-policy tests**

The tests must prove these routes require validated context:

- `/inventory/purchase`
- `/inventory/purchaseReturn`
- `/cangku/purchase`
- `/cangku/purchaseReturn`
- nested/detail/query variants under those prefixes

They must also prove unrelated OA pages and `/select-shop` do not get classified as inventory-context routes.

```js
assert.strictEqual(requiresInventoryContext('/cangku/purchase'), true)
assert.strictEqual(requiresInventoryContext('/cangku/purchaseReturn/detail/12'), true)
assert.strictEqual(requiresInventoryContext('/oa/purchase'), false)
```

- [ ] **Step 2: Run and verify RED**

```bash
cd erp-ui
node test/desktopContextPolicy.test.js
```

- [ ] **Step 3: Extract and extend the policy**

Create `requiresInventoryContext(path)` with segment-aware exact-or-child matching. Include existing stock, transfer, inventory, and mobile prefixes plus the two warehouse procurement roots. Import it into `permission.js` and replace the local prefix array/function.

Do not change redirect behavior: `shouldSelectShop` must continue sending `to.fullPath` as the `redirect` query, and `SelectShop.getConfirmRedirect()` must continue restoring it.

- [ ] **Step 4: Run context/security regression tests**

```bash
cd erp-ui
node test/desktopContextPolicy.test.js
node test/shopContextUx.test.js
node test/desktopContextUx.test.js
node test/desktopSecurityBoundary.test.js
```

- [ ] **Step 5: Commit**

```bash
git add erp-ui/src/utils/desktopContextPolicy.js erp-ui/src/permission.js erp-ui/test/desktopContextPolicy.test.js
git commit -m "fix: require context for procurement routes"
```

## Task 6: R1 verification and HR handoff

**Files:**

- Modify: this plan file (checkboxes/results only)
- Create later: `docs/superpowers/plans/2026-07-10-desktop-audit-remediation-r1-hr-pagination.md`

- [ ] **Step 1: Run focused frontend regression suites**

```bash
cd erp-ui
node --test \
  test/authSecurityHardening.test.js \
  test/desktopAuditRemediation.test.js \
  test/desktopAuditRepair.test.js \
  test/desktopContextUx.test.js \
  test/desktopContextPolicy.test.js \
  test/desktopSecurityBoundary.test.js \
  test/nginxStaticCaching.test.js \
  test/systemManagementUx.test.js
```

- [ ] **Step 2: Run backend regression suites**

```bash
mvn -pl erp-modules/erp-system -am \
  -Dtest=HtmlSanitizerTest,SysNoticeServiceImplTest,SysNoticeControllerTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

- [ ] **Step 3: Build the desktop frontend**

```bash
cd erp-ui
npm run build:prod
```

- [ ] **Step 4: Run the full frontend suite and document only known baseline exceptions**

```bash
cd erp-ui
npm test
```

Expected baseline exceptions remain limited to the missing untracked local YAML and Android generated directory. Any new failure blocks completion.

- [ ] **Step 5: Perform manual desktop smoke checks**

Start the app in a representative local environment and verify:

1. A notice with normal headings, links, lists, tables, and images remains readable.
2. A notice containing script/event-handler/javascript-URL payloads cannot execute.
3. An import result containing HTML-like spreadsheet text is displayed literally.
4. Header search highlights hostile-looking menu text without creating DOM elements.
5. Direct navigation to procurement routes without context goes to organization selection and returns to the original full path after selection.

- [ ] **Step 6: Prepare the separate HR pagination plan**

Identify the clean commit/branch containing the audited HR pages and controllers. Integrate that baseline without copying the original dirty workspace wholesale, then write the HR query/pagination plan with backend query tests and frontend request/total tests before changing implementation.
