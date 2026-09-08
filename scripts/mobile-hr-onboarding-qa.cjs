#!/usr/bin/env node

/*
 * Real QA requires API_ORIGIN, HR_QA_SESSION_FILE, dynamic fixture IDs, and
 * HR_QA_FORBIDDEN_PII as a non-empty JSON array of exact strings, for example
 * `["<exact fixture value>"]`. Exact values are used only in memory for DOM
 * auditing and are never logged, stored in artifacts, or included in reports.
 * Real writes remain disabled unless HR_QA_ALLOW_MUTATIONS=true and
 * HR_QA_MUTATION_ALLOWLIST is a non-empty JSON array of exact HTTPS
 * method/origin/pathname rules.
 */

const fs = require("fs")
const path = require("path")

const VIEWPORTS = [390, 375, 360, 320]
const STATUSES = ["DRAFT", "READY", "CONFIRMED", "CANCELLED"]
const OUTPUT_ROOT = path.resolve(__dirname, "../artifacts/mobile-hr-onboarding")
const LIST_API_PATTERN = "**/system/hr/onboarding/list**"
const READ_ONLY_METHODS = new Set(["GET", "HEAD", "OPTIONS"])
const CHINESE_ID_PROVINCE_CODES = new Set([
  "11", "12", "13", "14", "15", "21", "22", "23", "31", "32", "33", "34", "35", "36", "37",
  "41", "42", "43", "44", "45", "46", "50", "51", "52", "53", "54", "61", "62", "63", "64", "65",
  "71", "81", "82"
])

function fail(message) {
  const error = new Error(message)
  error.code = message
  throw error
}

function requireHttpsOrigin(name, value) {
  let parsed
  try {
    parsed = new URL(value)
  } catch (error) {
    fail(`${name}_HTTPS_REQUIRED`)
  }
  if (parsed.protocol !== "https:" || !parsed.hostname || parsed.username || parsed.password) {
    fail(`${name}_HTTPS_REQUIRED`)
  }
  return parsed.origin
}

function requirePositiveId(name, value) {
  if (!/^\d+$/.test(String(value || "")) || Number(value) < 1) fail(`${name}_REQUIRED`)
  return String(Number(value))
}

function decideQaRequest({ method, url, mutationOptIn, allowlist }) {
  const normalizedMethod = String(method || "GET").toUpperCase()
  if (READ_ONLY_METHODS.has(normalizedMethod)) return { allowed: true, reason: "read-only" }
  let parsed
  try {
    parsed = new URL(url)
  } catch (error) {
    return { allowed: false, reason: "unapproved-mutation" }
  }
  const exactMatch = Boolean(mutationOptIn) && Array.isArray(allowlist) && allowlist.some(rule =>
    rule && normalizedMethod === String(rule.method || "").toUpperCase() &&
    parsed.origin === rule.origin && parsed.pathname === rule.pathname)
  return exactMatch
    ? { allowed: true, reason: "approved-fixture-mutation" }
    : { allowed: false, reason: "unapproved-mutation" }
}

function parseMutationAllowlist(value, mutationOptIn) {
  if (!mutationOptIn) return []
  if (typeof value !== "string" || !value.trim()) fail("HR_QA_MUTATION_ALLOWLIST_REQUIRED")
  let parsed
  try {
    parsed = JSON.parse(value)
  } catch (error) {
    fail("HR_QA_MUTATION_ALLOWLIST_INVALID")
  }
  if (!Array.isArray(parsed) || !parsed.length) fail("HR_QA_MUTATION_ALLOWLIST_REQUIRED")
  return parsed.map(rule => {
    if (!rule || typeof rule !== "object" || Array.isArray(rule)) fail("HR_QA_MUTATION_ALLOWLIST_INVALID")
    const method = String(rule.method || "").toUpperCase()
    if (!method || READ_ONLY_METHODS.has(method)) fail("HR_QA_MUTATION_ALLOWLIST_INVALID")
    let origin
    try {
      const parsedOrigin = new URL(rule.origin)
      if (parsedOrigin.protocol !== "https:" || parsedOrigin.username || parsedOrigin.password ||
          parsedOrigin.pathname !== "/" || parsedOrigin.search || parsedOrigin.hash) {
        fail("HR_QA_MUTATION_ALLOWLIST_INVALID")
      }
      origin = parsedOrigin.origin
    } catch (error) {
      fail("HR_QA_MUTATION_ALLOWLIST_INVALID")
    }
    const pathname = String(rule.pathname || "")
    if (!pathname.startsWith("/") || pathname.includes("?") || pathname.includes("#") ||
        new URL(pathname, origin).pathname !== pathname) {
      fail("HR_QA_MUTATION_ALLOWLIST_INVALID")
    }
    return { method, origin, pathname }
  })
}

function loadPlaywright() {
  for (const moduleName of ["playwright", "@playwright/test"]) {
    try {
      return require(moduleName)
    } catch (error) {
      if (error && error.code !== "MODULE_NOT_FOUND") throw error
    }
  }
  fail("PLAYWRIGHT_REQUIRED")
}

function ensureAuthenticationInput(sessionFile) {
  if (!sessionFile) fail("HR_QA_SESSION_FILE_REQUIRED")
  const resolved = path.resolve(sessionFile)
  const stat = fs.statSync(resolved, { throwIfNoEntry: false })
  if (!stat || !stat.isFile()) fail("HR_QA_SESSION_FILE_REQUIRED")
  // Windows does not expose POSIX owner/mode semantics. POSIX session files must
  // be owned by the current user and inaccessible to group/other users.
  if (process.platform !== "win32") {
    if ((stat.mode & 0o077) !== 0) fail("HR_QA_SESSION_FILE_PRIVATE_REQUIRED")
    if (typeof process.getuid === "function" && stat.uid !== process.getuid()) {
      fail("HR_QA_SESSION_FILE_PRIVATE_REQUIRED")
    }
  }
  let state
  try {
    state = JSON.parse(fs.readFileSync(resolved, "utf8"))
  } catch (error) {
    fail("HR_QA_SESSION_STATE_REQUIRED")
  }
  if (!state || typeof state !== "object" || Array.isArray(state) ||
      !Array.isArray(state.cookies) || !Array.isArray(state.origins)) {
    fail("HR_QA_SESSION_STATE_REQUIRED")
  }
  const nowSeconds = Date.now() / 1000
  const hasCookieState = state.cookies.some(cookie => {
    if (!cookie || typeof cookie.name !== "string" || !cookie.name.length ||
        typeof cookie.value !== "string" || !cookie.value.length) return false
    if (cookie.expires === undefined || cookie.expires === null || Number(cookie.expires) === -1) return true
    const expires = Number(cookie.expires)
    return Number.isFinite(expires) && expires > nowSeconds
  })
  const hasOriginState = state.origins.some(origin => origin && (
    (Array.isArray(origin.localStorage) && origin.localStorage.some(item => item &&
      typeof item.name === "string" && item.name.length > 0 &&
      typeof item.value === "string" && item.value.length > 0)) ||
    (Array.isArray(origin.indexedDB) && origin.indexedDB.length > 0)
  ))
  if (!hasCookieState && !hasOriginState) fail("HR_QA_SESSION_STATE_REQUIRED")
  return resolved
}

function preparePrivateOutputDir(outputDir) {
  fs.mkdirSync(outputDir, { recursive: true, mode: 0o700 })
  if (process.platform !== "win32") fs.chmodSync(outputDir, 0o700)
  return outputDir
}

function writePrivateFile(filename, contents) {
  fs.writeFileSync(filename, contents, { mode: 0o600 })
  if (process.platform !== "win32") fs.chmodSync(filename, 0o600)
  return filename
}

function parseForbiddenPii(value) {
  if (typeof value !== "string" || !value.trim()) fail("HR_QA_FORBIDDEN_PII_REQUIRED")
  let parsed
  try {
    parsed = JSON.parse(value)
  } catch (error) {
    fail("HR_QA_FORBIDDEN_PII_INVALID")
  }
  if (!Array.isArray(parsed)) fail("HR_QA_FORBIDDEN_PII_INVALID")
  if (!parsed.length) fail("HR_QA_FORBIDDEN_PII_REQUIRED")
  if (parsed.some(item => typeof item !== "string" || !item.trim())) fail("HR_QA_FORBIDDEN_PII_INVALID")
  return parsed.slice()
}

function findPiiCategories(values, forbiddenPii) {
  const categories = new Set()
  const sources = Array.isArray(values) ? values.map(value => normalizePiiText(value)) : []
  for (const text of sources) {
    const numberRuns = extractStructuredNumberRuns(text).map(compactDigitsAndX)
    if (numberRuns.some(candidate => /^1[3-9]\d{9}$/.test(candidate))) categories.add("phone")
    const idShapedCandidates = new Set(numberRuns.filter(candidate => isChineseIdShape(candidate)))
    const validIds = new Set(Array.from(idShapedCandidates).filter(candidate => isValidChineseId(candidate)))
    if (validIds.size) categories.add("chinese-id")
    const accountCandidates = numberRuns.filter(candidate => /^[2-6]\d{15,18}$/.test(candidate))
    if (accountCandidates.some(candidate => !idShapedCandidates.has(candidate) && isValidLuhn(candidate))) {
      categories.add("bank-or-account")
    }
    const compactText = compactExactText(text)
    if ((forbiddenPii || []).some(value => value && compactText.includes(compactExactText(value)))) {
      categories.add("forbidden-exact")
    }
  }
  return Array.from(categories).sort()
}

function normalizePiiText(value) {
  return String(value || "").normalize("NFKC").replace(/[‐‑‒–—―−﹘﹣－]/g, "-")
}

function compactExactText(value) {
  return normalizePiiText(value).replace(/\s+/g, "")
}

function compactDigitsAndX(value) {
  return normalizePiiText(value).replace(/[\s-]/g, "").toUpperCase()
}

function extractStructuredNumberRuns(text) {
  return normalizePiiText(text).match(/\d(?:[\s-]*[\dXx])*/g) || []
}

function isValidCalendarDate(value) {
  if (!/^\d{8}$/.test(value)) return false
  const year = Number(value.slice(0, 4))
  const month = Number(value.slice(4, 6))
  const day = Number(value.slice(6, 8))
  if (year < 1800 || year > new Date().getFullYear() || month < 1 || month > 12 || day < 1 || day > 31) return false
  const date = new Date(Date.UTC(year, month - 1, day))
  return date.getUTCFullYear() === year && date.getUTCMonth() === month - 1 && date.getUTCDate() === day &&
    date.getTime() <= Date.now()
}

function isValidChineseId(value) {
  const candidate = compactDigitsAndX(value)
  if (!isChineseIdShape(candidate)) return false
  if (/^\d{15}$/.test(candidate)) return true
  const weights = [7, 9, 10, 5, 8, 4, 2, 1, 6, 3, 7, 9, 10, 5, 8, 4, 2]
  const checks = "10X98765432"
  const sum = candidate.slice(0, 17).split("").reduce((total, digit, index) => total + Number(digit) * weights[index], 0)
  return checks[sum % 11] === candidate[17]
}

function isChineseIdShape(value) {
  const candidate = compactDigitsAndX(value)
  if (!CHINESE_ID_PROVINCE_CODES.has(candidate.slice(0, 2))) return false
  if (/^\d{15}$/.test(candidate)) {
    return candidate.slice(12, 15) !== "000" && isValidCalendarDate(`19${candidate.slice(6, 12)}`)
  }
  return /^\d{17}[\dX]$/.test(candidate) && candidate.slice(14, 17) !== "000" &&
    isValidCalendarDate(candidate.slice(6, 14))
}

function isValidLuhn(value) {
  if (!/^\d{16,19}$/.test(value)) return false
  let sum = 0
  let doubled = false
  for (let index = value.length - 1; index >= 0; index -= 1) {
    let digit = Number(value[index])
    if (doubled) {
      digit *= 2
      if (digit > 9) digit -= 9
    }
    sum += digit
    doubled = !doubled
  }
  return sum % 10 === 0
}

function assertRenderedContentIsMasked(values, scenario, forbiddenPii) {
  const categories = findPiiCategories(values, forbiddenPii)
  if (categories.length) fail(`UNMASKED_RENDERED_FIXTURE:${scenario}:${categories.join(",")}`)
}

function assertRenderedTextIsMasked(text, scenario) {
  assertRenderedContentIsMasked([text], scenario, [])
}

async function collectRenderedAuditValues(page) {
  const visibleText = await page.locator("body").innerText()
  const controls = await page.locator('input:not([type="password"]):visible, textarea:visible, [contenteditable]:not([contenteditable="false"]):visible')
    .evaluateAll(elements => elements.map(element => {
      if (element.isContentEditable) return element.innerText || element.textContent || ""
      return element.value || ""
    }))
  const valueAttributes = await page.locator("[value]:visible").evaluateAll(elements => elements
    .filter(element => !(element.tagName === "INPUT" && String(element.type).toLowerCase() === "password"))
    .map(element => element.getAttribute("value") || ""))
  return [visibleText].concat(controls, valueAttributes)
}

function validateKeyboardMeasurements(measurements) {
  const source = measurements || {}
  const visualTop = Number(source.visualTop) || 0
  const visualBottom = visualTop + Number(source.visualHeight || 0)
  if (!source.footer || source.footer.top < visualTop - 1 || source.footer.bottom > visualBottom + 1) {
    fail("HR_QA_STICKY_FOOTER_OUTSIDE_VIEWPORT")
  }
  if (!source.control || source.control.top < visualTop - 1 ||
      source.control.bottom > Math.min(source.footer.top, visualBottom) + 1) {
    fail("HR_QA_FOCUSED_CONTROL_COVERED")
  }
  if (source.bodyScrollWidth > source.viewportWidth) fail("HR_QA_HORIZONTAL_OVERFLOW")
  if (!Array.isArray(source.controls) || !source.controls.length || source.controls.some(control =>
    !control || control.height < 44 || control.width < 44)) {
    fail("HR_QA_TOUCH_TARGET_TOO_SMALL")
  }
  return source
}

function validateKeyboardCoverage(steps) {
  if (!Array.isArray(steps) || steps.length !== 4) fail("HR_QA_FORM_STEP_COVERAGE_INCOMPLETE")
  for (const step of steps) {
    if (!step || !Array.isArray(step.targets) || step.targets.length !== 2) {
      fail("HR_QA_FORM_CONTROL_COVERAGE_INCOMPLETE")
    }
    step.targets.forEach(validateKeyboardMeasurements)
  }
  return steps
}

function validateConfirmationState(state) {
  const source = state || {}
  if (source.loading) fail("HR_QA_CONFIRM_LOADING_NOT_SETTLED")
  if (source.error) fail("HR_QA_CONFIRM_CONFLICT_ERROR")
  if (source.actualDateCount !== 1) fail("HR_QA_CONFIRM_ACTUAL_DATE_MISSING")
  if (source.decisionCount < 2 || source.decisionAreaCount < 1) fail("HR_QA_CONFIRM_DECISION_CONTROLS_MISSING")
  return source
}

function safeName(value) {
  return String(value).replace(/[^a-zA-Z0-9_-]+/g, "-")
}

async function requireVisible(locator, code) {
  try {
    await locator.first().waitFor({ state: "visible", timeout: 5000 })
  } catch (error) {
    fail(code)
  }
}

async function assertExpectedPage(page, appOrigin, expectedRoute, scenario) {
  const actual = new URL(page.url())
  const expected = new URL(expectedRoute, appOrigin)
  if (actual.origin !== appOrigin || actual.pathname !== expected.pathname ||
      /^\/(?:login|register|auth)(?:\/|$)/.test(actual.pathname)) {
    fail("HR_QA_AUTH_REDIRECT")
  }
  for (const [key, value] of expected.searchParams.entries()) {
    if (actual.searchParams.get(key) !== value) fail(`HR_QA_ROUTE_MISMATCH:${scenario}`)
  }

  await requireVisible(page.locator(".mobile-hr-shell"), `HR_QA_SHELL_MISSING:${scenario}`)
  await requireVisible(page.locator(".mobile-hr-nav"), `HR_QA_NAV_MISSING:${scenario}`)

  const statusLabels = { DRAFT: "草稿", READY: "待确认", CONFIRMED: "已入职", CANCELLED: "已取消" }
  if (scenario === "workbench") {
    await requireVisible(page.getByRole("heading", { name: "人事工作台", exact: true }), "HR_QA_WORKBENCH_TITLE_MISSING")
    await requireVisible(page.locator('[aria-label="今日入职概览"]'), "HR_QA_WORKBENCH_SUMMARY_MISSING")
    await requireVisible(page.getByText("今日到岗", { exact: true }), "HR_QA_WORKBENCH_SUMMARY_MISSING")
    return
  }
  if (Object.prototype.hasOwnProperty.call(statusLabels, scenario)) {
    await requireVisible(page.locator(".mobile-onboarding-list"), `HR_QA_QUEUE_MISSING:${scenario}`)
    const selected = page.locator('.status-tabs [role="tab"][aria-selected="true"]')
    await requireVisible(selected, `HR_QA_STATUS_NOT_SELECTED:${scenario}`)
    if ((await selected.first().innerText()).trim() !== statusLabels[scenario]) fail(`HR_QA_STATUS_NOT_SELECTED:${scenario}`)
    return
  }
  if (scenario === "detail") {
    await requireVisible(page.getByRole("heading", { name: "入职详情", exact: true }), "HR_QA_DETAIL_TITLE_MISSING")
    await requireVisible(page.locator("#onboarding-detail-title"), "HR_QA_DETAIL_HEADING_MISSING")
    return
  }
  if (["create", "create-keyboard"].includes(scenario)) {
    await requireVisible(page.getByRole("heading", { name: "新建入职", exact: true }), `HR_QA_CREATE_TITLE_MISSING:${scenario}`)
    await requireVisible(page.locator(".mobile-form-page .step-form"), `HR_QA_FORM_MISSING:${scenario}`)
    return
  }
  if (["edit", "edit-keyboard"].includes(scenario)) {
    await requireVisible(page.getByRole("heading", { name: "补充入职资料", exact: true }), `HR_QA_EDIT_TITLE_MISSING:${scenario}`)
    await requireVisible(page.locator(".mobile-form-page .step-form"), `HR_QA_FORM_MISSING:${scenario}`)
    return
  }
  if (scenario === "validation") {
    await requireVisible(page.locator(".mobile-form-page .field-error[role=\"alert\"]"), "HR_QA_VALIDATION_ALERT_MISSING")
    return
  }
  if (scenario === "confirmation") {
    await requireVisible(page.locator('[role="dialog"][aria-labelledby="confirm-title"]'), "HR_QA_CONFIRM_DIALOG_MISSING")
    return
  }
  if (scenario === "cancel") {
    await requireVisible(page.locator('[role="dialog"][aria-labelledby="cancel-title"]'), "HR_QA_CANCEL_DIALOG_MISSING")
    return
  }
  if (scenario === "empty") {
    await requireVisible(page.locator(".onboarding-list-state.is-empty"), "HR_QA_EMPTY_STATE_MISSING")
    await requireVisible(page.locator(".onboarding-list-state.is-empty strong").filter({ hasText: "暂无" }), "HR_QA_EMPTY_TEXT_MISSING")
    return
  }
  if (scenario === "loading") {
    await requireVisible(page.locator('[role="status"][aria-busy="true"]').filter({ hasText: "正在加载" }), "HR_QA_LOADING_STATE_MISSING")
    return
  }
  if (scenario === "error") {
    await requireVisible(page.locator('[role="alert"]'), "HR_QA_ERROR_ALERT_MISSING")
    await requireVisible(page.getByRole("button", { name: /重新加载|重试/ }), "HR_QA_ERROR_RETRY_MISSING")
    return
  }
  fail(`HR_QA_SCENARIO_UNKNOWN:${scenario}`)
}

async function measureKeyboardTarget(page, width, field) {
  await field.focus()
  await field.evaluate(element => element.scrollIntoView({ block: "center", inline: "nearest" }))
  await page.waitForTimeout(250)
  const footer = page.locator(".step-form .sticky-footer")
  await requireVisible(footer, "HR_QA_STICKY_FOOTER_MISSING")
  const fieldBox = await field.boundingBox()
  const footerBox = await footer.boundingBox()
  if (!fieldBox || !footerBox) fail("HR_QA_KEYBOARD_MEASUREMENT_MISSING")
  const layout = await page.evaluate(() => {
    const controls = Array.from(document.querySelectorAll(".step-form button, .step-form input, .step-form select, .step-form textarea"))
      .filter(element => {
        const style = window.getComputedStyle(element)
        const rect = element.getBoundingClientRect()
        return style.display !== "none" && style.visibility !== "hidden" && rect.width > 0 && rect.height > 0
      })
      .map(element => {
        const rect = element.getBoundingClientRect()
        return { width: rect.width, height: rect.height }
      })
    return {
      visualTop: window.visualViewport ? window.visualViewport.offsetTop : 0,
      visualHeight: window.visualViewport ? window.visualViewport.height : window.innerHeight,
      viewportWidth: window.innerWidth,
      bodyScrollWidth: Math.max(document.documentElement.scrollWidth, document.body.scrollWidth),
      controls
    }
  })
  const measurements = Object.assign(layout, {
    footer: { top: footerBox.y, bottom: footerBox.y + footerBox.height },
    control: { top: fieldBox.y, bottom: fieldBox.y + fieldBox.height }
  })
  validateKeyboardMeasurements(measurements)
  return measurements
}

async function verifyKeyboardScenario(page, width) {
  await page.setViewportSize({ width, height: 540 })
  const stepMeasurements = []
  for (let stepIndex = 0; stepIndex < 4; stepIndex += 1) {
    const stepButton = page.locator(".step-form .step-progress button").nth(stepIndex)
    await requireVisible(stepButton, "HR_QA_FORM_STEP_CONTROL_MISSING")
    await stepButton.click()
    try {
      await page.waitForFunction(index => {
        const buttons = Array.from(document.querySelectorAll(".step-form .step-progress button"))
        return buttons[index] && buttons[index].getAttribute("aria-current") === "step"
      }, stepIndex, { timeout: 5000 })
    } catch (error) {
      fail("HR_QA_FORM_STEP_NAVIGATION_FAILED")
    }

    const enabledFields = page.locator([
      ".step-form .fields input:not([disabled]):visible",
      ".step-form .fields textarea:not([disabled]):visible",
      ".step-form .fields select:not([disabled]):visible",
      ".step-form .fields button:not([disabled]):visible"
    ].join(", "))
    const fieldCount = await enabledFields.count()
    if (!fieldCount) fail("HR_QA_KEYBOARD_FIELD_MISSING")
    const targets = [enabledFields.first(), enabledFields.nth(fieldCount - 1)]
    const measurements = []
    for (const target of targets) measurements.push(await measureKeyboardTarget(page, width, target))
    stepMeasurements.push({ step: stepIndex + 1, targets: measurements })
  }
  validateKeyboardCoverage(stepMeasurements)
  return { steps: stepMeasurements }
}

async function main() {
  const apiOrigin = requireHttpsOrigin("API_ORIGIN", process.env.API_ORIGIN)
  const appOrigin = requireHttpsOrigin("HR_QA_APP_ORIGIN", process.env.HR_QA_APP_ORIGIN || apiOrigin)
  const sessionFile = ensureAuthenticationInput(process.env.HR_QA_SESSION_FILE)
  const detailId = requirePositiveId("HR_QA_DETAIL_ID", process.env.HR_QA_DETAIL_ID)
  const editId = requirePositiveId("HR_QA_EDIT_ID", process.env.HR_QA_EDIT_ID || detailId)
  const confirmId = requirePositiveId("HR_QA_CONFIRM_ID", process.env.HR_QA_CONFIRM_ID || detailId)
  const cancelId = requirePositiveId("HR_QA_CANCEL_ID", process.env.HR_QA_CANCEL_ID || detailId)
  const mutationOptIn = process.env.HR_QA_ALLOW_MUTATIONS === "true"
  const mutationAllowlist = parseMutationAllowlist(process.env.HR_QA_MUTATION_ALLOWLIST, mutationOptIn)
  const forbiddenPii = parseForbiddenPii(process.env.HR_QA_FORBIDDEN_PII)

  const { chromium } = loadPlaywright()
  const runId = new Date().toISOString().replace(/[:.]/g, "-")
  const outputDir = path.join(OUTPUT_ROOT, runId)
  preparePrivateOutputDir(outputDir)

  const browser = await chromium.launch({ headless: true })
  const report = {
    runId,
    viewports: VIEWPORTS,
    statuses: STATUSES,
    readOnly: mutationAllowlist.length === 0,
    mutationApproved: mutationOptIn,
    apiOriginValidated: true,
    authentication: "ephemeral-session-input",
    forbiddenPiiConfigured: forbiddenPii.length > 0,
    approvedMutationRuleCount: mutationAllowlist.length,
    unapprovedMutationAttempts: [],
    captures: [],
    failures: []
  }

  async function recordCapture(page, width, scenario, route, prepare) {
    const entry = { width, scenario, route, ok: false }
    try {
      if (route) await page.goto(new URL(route, appOrigin).toString(), { waitUntil: "domcontentloaded" })
      const measurements = prepare ? await prepare(page) : null
      if (measurements) entry.measurements = measurements
      await page.waitForTimeout(500)
      await assertExpectedPage(page, appOrigin, route, scenario)
      const renderedValues = await collectRenderedAuditValues(page)
      assertRenderedContentIsMasked(renderedValues, scenario, forbiddenPii)
      const filename = `${width}-${safeName(scenario)}.png`
      const screenshot = await page.screenshot({ fullPage: true })
      writePrivateFile(path.join(outputDir, filename), screenshot)
      entry.screenshot = filename
      entry.ok = true
    } catch (error) {
      entry.error = error && error.code ? error.code : String(error && error.message || error)
      report.failures.push({ width, scenario, error: entry.error })
    } finally {
      report.captures.push(entry)
    }
  }

  try {
    for (const width of VIEWPORTS) {
      let validationMockEnabled = false
      const context = await browser.newContext({
        viewport: { width, height: 844 },
        storageState: sessionFile,
        serviceWorkers: "block"
      })
      await context.route("**/*", async route => {
        const request = route.request()
        const original = new URL(request.url())
        const target = appOrigin !== apiOrigin && original.origin === appOrigin && original.pathname.startsWith("/prod-api/")
          ? new URL(original.pathname + original.search, apiOrigin)
          : original
        const isLocalValidationRequest = validationMockEnabled && request.method().toUpperCase() === "POST" &&
          target.origin === apiOrigin && ["/prod-api/system/hr/onboarding", "/system/hr/onboarding"].includes(target.pathname)
        if (isLocalValidationRequest) {
          return route.fulfill({
            status: 200,
            contentType: "application/json",
            body: JSON.stringify({
              code: 400,
              msg: "请检查必填项",
              fieldErrors: { employeeName: "请输入姓名", expectedEntryDate: "请选择预计入职日期" }
            })
          })
        }
        const decision = decideQaRequest({
          method: request.method(),
          url: target.toString(),
          mutationOptIn,
          allowlist: mutationAllowlist
        })
        if (!decision.allowed) {
          report.unapprovedMutationAttempts.push({
            method: request.method().toUpperCase(),
            apiOrigin: target.origin === apiOrigin
          })
          return route.abort("blockedbyclient")
        }
        return target.toString() === original.toString()
          ? route.continue()
          : route.continue({ url: target.toString() })
      })
      const page = await context.newPage()

      await recordCapture(page, width, "workbench", "/mobile/hr")
      for (const status of STATUSES) {
        await recordCapture(page, width, status, `/mobile/hr/onboarding?status=${status}`)
      }
      await recordCapture(page, width, "detail", `/mobile/hr/onboarding/${detailId}`)
      await recordCapture(page, width, "create", "/mobile/hr/onboarding/create")
      await recordCapture(page, width, "edit", `/mobile/hr/onboarding/${editId}/edit`)
      await recordCapture(page, width, "create-keyboard", "/mobile/hr/onboarding/create", currentPage =>
        verifyKeyboardScenario(currentPage, width))
      await page.setViewportSize({ width, height: 844 })
      await recordCapture(page, width, "edit-keyboard", `/mobile/hr/onboarding/${editId}/edit`, currentPage =>
        verifyKeyboardScenario(currentPage, width))
      await page.setViewportSize({ width, height: 844 })

      validationMockEnabled = true
      try {
        await recordCapture(page, width, "validation", "/mobile/hr/onboarding/create", async currentPage => {
          const createButton = currentPage.getByRole("button", { name: "创建入职", exact: true })
          if (!await createButton.count()) fail("HR_QA_VALIDATION_CONTROL_NOT_FOUND")
          await createButton.click()
          await currentPage.waitForTimeout(250)
        })
      } finally {
        validationMockEnabled = false
      }

      await recordCapture(page, width, "confirmation", `/mobile/hr/onboarding/${confirmId}`, async currentPage => {
        const confirmButton = currentPage.getByRole("button", { name: "确认入职", exact: true }).first()
        if (!await confirmButton.count()) fail("HR_QA_CONFIRM_FIXTURE_NOT_READY")
        await confirmButton.click()
        const dialog = currentPage.locator('[role="dialog"][aria-labelledby="confirm-title"]')
        await requireVisible(dialog, "HR_QA_CONFIRM_DIALOG_MISSING")
        try {
          await currentPage.waitForFunction(() => {
            const currentDialog = document.querySelector('[role="dialog"][aria-labelledby="confirm-title"]')
            return currentDialog && !currentDialog.querySelector('.async-state[role="status"]')
          }, null, { timeout: 5000 })
        } catch (error) {
          fail("HR_QA_CONFIRM_LOADING_NOT_SETTLED")
        }
        const state = {
          loading: await dialog.locator('.async-state[role="status"]').count() > 0,
          error: await dialog.locator('.async-state.is-error[role="alert"]').count() > 0,
          actualDateCount: await dialog.locator('input[type="date"]').count(),
          decisionCount: await dialog.locator('fieldset input[type="radio"][value="CREATE_NEW"], fieldset input[type="radio"][value="BIND_EXISTING"]').count(),
          decisionAreaCount: await dialog.getByRole("group", { name: "冲突处理", exact: true }).count()
        }
        validateConfirmationState(state)
      })

      await page.keyboard.press("Escape")
      await recordCapture(page, width, "cancel", `/mobile/hr/onboarding/${cancelId}`, async currentPage => {
        const moreButton = currentPage.getByRole("button", { name: "更多操作", exact: true })
        if (!await moreButton.count()) fail("HR_QA_CANCEL_FIXTURE_NOT_ACTIONABLE")
        await moreButton.click()
        const cancelButton = currentPage.getByRole("button", { name: "取消入职", exact: true })
        if (!await cancelButton.count()) fail("HR_QA_CANCEL_FIXTURE_NOT_ACTIONABLE")
        await cancelButton.click()
        await currentPage.waitForTimeout(250)
      })

      await page.route(LIST_API_PATTERN, route => route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify({ code: 200, rows: [], total: 0 })
      }))
      await recordCapture(page, width, "empty", "/mobile/hr/onboarding?status=DRAFT")
      await page.unroute(LIST_API_PATTERN)

      await page.route(LIST_API_PATTERN, () => new Promise(() => {}))
      await recordCapture(page, width, "loading", "/mobile/hr/onboarding?status=READY")
      await page.unroute(LIST_API_PATTERN)

      await page.route(LIST_API_PATTERN, route => route.abort("failed"))
      await recordCapture(page, width, "error", "/mobile/hr/onboarding?status=READY")
      await page.unroute(LIST_API_PATTERN)

      await context.close()
    }
  } finally {
    await browser.close()
  }

  const jsonPath = path.join(outputDir, "report.json")
  const markdownPath = path.join(outputDir, "report.md")
  writePrivateFile(jsonPath, JSON.stringify(report, null, 2) + "\n")
  const lines = [
    "# Mobile HR onboarding QA",
    "",
    `- Run: ${report.runId}`,
    `- Mode: ${report.readOnly ? "read-only visual capture" : "allowlisted fixture mutation capture"}`,
    `- Mutation approval supplied: ${report.mutationApproved ? "yes" : "no"}`,
    `- Unapproved mutation attempts: ${report.unapprovedMutationAttempts.length}`,
    `- Forbidden exact PII configured: ${report.forbiddenPiiConfigured ? "yes" : "no"}`,
    `- Captures: ${report.captures.filter(item => item.ok).length}/${report.captures.length}`,
    `- Failures: ${report.failures.length}`,
    "",
    "| Width | Scenario | Result | Screenshot |",
    "| ---: | --- | --- | --- |",
    ...report.captures.map(item => `| ${item.width} | ${item.scenario} | ${item.ok ? "PASS" : "FAIL"} | ${item.screenshot || "-"} |`),
    ""
  ]
  writePrivateFile(markdownPath, lines.join("\n"))

  if (report.unapprovedMutationAttempts.length) fail("HR_QA_UNAPPROVED_MUTATION_ATTEMPTED")
  if (report.failures.length) fail("MOBILE_HR_QA_FAILED")
  process.stdout.write(`Mobile HR onboarding QA passed: ${outputDir}\n`)
}

if (require.main === module) {
  main().catch(error => {
    process.stderr.write(`${error && error.code ? error.code : error.message}\n`)
    process.exitCode = 1
  })
}

module.exports = {
  assertExpectedPage,
  assertRenderedContentIsMasked,
  assertRenderedTextIsMasked,
  collectRenderedAuditValues,
  decideQaRequest,
  ensureAuthenticationInput,
  findPiiCategories,
  parseForbiddenPii,
  parseMutationAllowlist,
  preparePrivateOutputDir,
  requireHttpsOrigin,
  requirePositiveId,
  validateConfirmationState,
  validateKeyboardCoverage,
  validateKeyboardMeasurements,
  verifyKeyboardScenario,
  writePrivateFile
}
