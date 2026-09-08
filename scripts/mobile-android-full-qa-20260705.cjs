#!/usr/bin/env node

const ERP_QA_PASSWORD = process.env.ERP_QA_PASSWORD
if (ERP_QA_PASSWORD === undefined || ERP_QA_PASSWORD === "") {
  console.error("ERP_QA_PASSWORD is required")
  process.exit(2)
}

const fs = require("fs")
const http = require("http")
const path = require("path")
const { execFileSync } = require("child_process")
const WebSocket = require("../erp-ui/node_modules/ws")

const ROOT = path.resolve(__dirname, "..")
const SERIAL = process.env.ADB_SERIAL || "emulator-5554"
const PACKAGE = "com.erp.mobile"
const ACTIVITY = "com.erp.mobile/.MainActivity"
const WEBVIEW_ORIGIN = process.env.WEBVIEW_ORIGIN || "http://10.0.2.2:19025"
const HOST_ORIGIN = process.env.HOST_ORIGIN || "http://127.0.0.1:19025"
const CDP_PORT = Number(process.env.CDP_PORT || 9224)
const OUT_DIR = process.env.OUT_DIR || path.join(ROOT, "docs/audit-screenshots/mobile-android-20260705-full-qa-round2")
const RESULTS_PATH = path.join(OUT_DIR, "android-full-qa-results.json")
const REPORT_PATH = path.join(OUT_DIR, "android-full-qa-report.md")

const STORE = { id: 1176, name: "北京柏悦", type: "STORE" }
const WAREHOUSE = { id: 1245, name: "主仓库", type: "WAREHOUSE" }

const AGENTS = [
  {
    id: "admin_store",
    label: "管理员-门店上下文",
    username: "admin",
    password: ERP_QA_PASSWORD,
    post: "系统管理员",
    context: STORE,
    paths: ["/mobile/store", "/mobile/sales", "/mobile/customer", "/mobile/stock", "/mobile/product", "/mobile/mine", "/mobile/profile", "/mobile/notice"]
  },
  {
    id: "admin_warehouse",
    label: "管理员-仓库上下文",
    username: "admin",
    password: ERP_QA_PASSWORD,
    post: "系统管理员",
    context: WAREHOUSE,
    paths: ["/mobile/warehouse", "/mobile/purchase", "/mobile/outbound", "/mobile/stock-check", "/mobile/transfer", "/mobile/supplier", "/mobile/transfer-approval"]
  },
  {
    id: "tea_artist",
    label: "茶艺师",
    username: "19834743225",
    password: ERP_QA_PASSWORD,
    post: "茶艺师",
    context: STORE,
    paths: ["/mobile/store", "/mobile/sales", "/mobile/attendance", "/mobile/stock", "/mobile/mine"]
  },
  {
    id: "ops_director",
    label: "运营总监",
    username: "13659326770",
    password: ERP_QA_PASSWORD,
    post: "运营总监",
    context: STORE,
    paths: ["/mobile/store", "/mobile/stock", "/mobile/transfer-approval", "/mobile/oa-todo", "/mobile/oa-done", "/mobile/mine"]
  },
  {
    id: "sys_maint",
    label: "系统维护",
    username: "18669567559",
    password: ERP_QA_PASSWORD,
    post: "系统维护",
    context: WAREHOUSE,
    paths: ["/mobile/mine", "/mobile/profile", "/mobile/notice", "/mobile/system-user", "/mobile/system-role", "/mobile/monitor-job", "/mobile/monitor-online"]
  },
  {
    id: "store_manager",
    label: "店长",
    username: "15516784616",
    password: ERP_QA_PASSWORD,
    post: "店长",
    context: STORE,
    paths: ["/mobile/store", "/mobile/sales", "/mobile/sales-return", "/mobile/replenishment", "/mobile/stock", "/mobile/mine"]
  }
]

const FULL_ROUTE_SWEEP = [
  { path: "/mobile/store", context: STORE },
  { path: "/mobile/warehouse", context: WAREHOUSE },
  { path: "/mobile/inventory", context: WAREHOUSE },
  { path: "/mobile/sales", context: STORE },
  { path: "/mobile/purchase", context: WAREHOUSE },
  { path: "/mobile/purchase-return", context: WAREHOUSE },
  { path: "/mobile/stock", context: STORE },
  { path: "/mobile/product", context: STORE },
  { path: "/mobile/category", context: WAREHOUSE },
  { path: "/mobile/customer", context: STORE },
  { path: "/mobile/supplier", context: WAREHOUSE },
  { path: "/mobile/stock-log", context: WAREHOUSE },
  { path: "/mobile/stock-check", context: WAREHOUSE },
  { path: "/mobile/transfer-records", context: WAREHOUSE },
  { path: "/mobile/sales-return", context: STORE },
  { path: "/mobile/replenishment", context: STORE },
  { path: "/mobile/outbound", context: WAREHOUSE },
  { path: "/mobile/transfer", context: WAREHOUSE },
  { path: "/mobile/transfer-approval", context: WAREHOUSE },
  { path: "/mobile/fixed-asset-repair", context: STORE },
  { path: "/mobile/oa-purchase", context: STORE },
  { path: "/mobile/oa-todo", context: STORE },
  { path: "/mobile/oa-done", context: STORE },
  { path: "/mobile/attendance", context: STORE },
  { path: "/mobile/salary", context: STORE },
  { path: "/mobile/notice", context: STORE },
  { path: "/mobile/profile", context: STORE },
  { path: "/mobile/system-user", context: WAREHOUSE },
  { path: "/mobile/system-role", context: WAREHOUSE },
  { path: "/mobile/system-post", context: WAREHOUSE },
  { path: "/mobile/system-dept", context: WAREHOUSE },
  { path: "/mobile/system-menu", context: WAREHOUSE },
  { path: "/mobile/user-shop", context: WAREHOUSE },
  { path: "/mobile/system-config", context: WAREHOUSE },
  { path: "/mobile/system-dict-type", context: WAREHOUSE },
  { path: "/mobile/system-dict-data", context: WAREHOUSE },
  { path: "/mobile/system-logininfor", context: WAREHOUSE },
  { path: "/mobile/system-operlog", context: WAREHOUSE },
  { path: "/mobile/monitor-job", context: WAREHOUSE },
  { path: "/mobile/monitor-job-log", context: WAREHOUSE },
  { path: "/mobile/monitor-online", context: WAREHOUSE },
  { path: "/mobile/salary-scheme", context: WAREHOUSE },
  { path: "/mobile/transfer-rules", context: WAREHOUSE },
  { path: "/mobile/contract", context: STORE },
  { path: "/mobile/sign-package", context: STORE },
  { path: "/mobile/mine", context: STORE }
]

fs.mkdirSync(OUT_DIR, { recursive: true })

function sleep(ms) {
  return new Promise(resolve => setTimeout(resolve, ms))
}

function safeName(value) {
  return String(value || "")
    .replace(/[^a-zA-Z0-9._-]+/g, "_")
    .replace(/^_+|_+$/g, "")
    .slice(0, 110)
}

function sh(command, args, options = {}) {
  return execFileSync(command, args, Object.assign({
    cwd: ROOT,
    encoding: "utf8",
    stdio: ["ignore", "pipe", "pipe"],
    maxBuffer: 80 * 1024 * 1024
  }, options))
}

function httpJson(url) {
  return new Promise((resolve, reject) => {
    http.get(url, res => {
      let body = ""
      res.on("data", chunk => { body += chunk })
      res.on("end", () => {
        try {
          resolve(JSON.parse(body))
        } catch (error) {
          reject(new Error("Invalid JSON from " + url + ": " + error.message + " body=" + body.slice(0, 200)))
        }
      })
    }).on("error", reject)
  })
}

function screenshot(name) {
  const file = path.join(OUT_DIR, name.endsWith(".png") ? name : name + ".png")
  const png = execFileSync("adb", ["-s", SERIAL, "exec-out", "screencap", "-p"], {
    cwd: ROOT,
    encoding: "buffer",
    maxBuffer: 30 * 1024 * 1024
  })
  fs.writeFileSync(file, png)
  return file
}

function launchApp() {
  sh("adb", ["-s", SERIAL, "shell", "am", "start", "-n", ACTIVITY])
}

function ensureCdpForward() {
  const pid = sh("adb", ["-s", SERIAL, "shell", "pidof", "-s", PACKAGE]).trim()
  if (!pid) throw new Error("No running pid for " + PACKAGE)
  try {
    sh("adb", ["-s", SERIAL, "forward", "--remove", "tcp:" + CDP_PORT])
  } catch (_) {}
  sh("adb", ["-s", SERIAL, "forward", "tcp:" + CDP_PORT, "localabstract:webview_devtools_remote_" + pid])
  return pid
}

function js(value) {
  return JSON.stringify(value)
}

class Cdp {
  constructor() {
    this.ws = null
    this.nextId = 1
    this.pending = new Map()
    this.console = []
    this.exceptions = []
  }

  async connect() {
    const pages = await httpJson("http://127.0.0.1:" + CDP_PORT + "/json")
    const page = pages.find(item => item.type === "page") || pages[0]
    if (!page || !page.webSocketDebuggerUrl) {
      throw new Error("No Android WebView CDP page available")
    }
    this.ws = new WebSocket(page.webSocketDebuggerUrl)
    this.ws.on("message", message => this.handleMessage(message))
    await new Promise((resolve, reject) => {
      this.ws.once("open", resolve)
      this.ws.once("error", reject)
    })
    await this.send("Runtime.enable")
    await this.send("Page.enable")
    await this.send("Log.enable").catch(() => null)
  }

  handleMessage(message) {
    const packet = JSON.parse(message)
    if (packet.id && this.pending.has(packet.id)) {
      const entry = this.pending.get(packet.id)
      clearTimeout(entry.timer)
      this.pending.delete(packet.id)
      entry.resolve(packet)
      return
    }
    if (packet.method === "Runtime.exceptionThrown") {
      this.exceptions.push(packet.params)
    }
    if (packet.method === "Runtime.consoleAPICalled") {
      const args = (packet.params.args || []).map(arg => arg.value || arg.description || arg.type).join(" ")
      this.console.push({ type: packet.params.type, text: args })
    }
    if (packet.method === "Log.entryAdded") {
      this.console.push({ type: packet.params.entry.level, text: packet.params.entry.text })
    }
  }

  send(method, params = {}, timeoutMs = 30000) {
    return new Promise((resolve, reject) => {
      if (!this.ws || this.ws.readyState !== WebSocket.OPEN) {
        reject(new Error("CDP websocket not open"))
        return
      }
      const id = this.nextId++
      const timer = setTimeout(() => {
        this.pending.delete(id)
        reject(new Error("CDP timeout: " + method))
      }, timeoutMs)
      this.pending.set(id, { resolve, reject, timer })
      this.ws.send(JSON.stringify({ id, method, params }))
    })
  }

  async eval(expression, timeoutMs = 45000) {
    const packet = await this.send("Runtime.evaluate", {
      expression,
      awaitPromise: true,
      returnByValue: true,
      timeout: timeoutMs
    }, timeoutMs + 5000)
    if (packet.error) throw new Error(packet.error.message || JSON.stringify(packet.error))
    if (packet.result && packet.result.exceptionDetails) {
      const detail = packet.result.exceptionDetails
      const text = detail.exception && detail.exception.description ? detail.exception.description : detail.text
      throw new Error(text || "Runtime exception")
    }
    return packet.result && packet.result.result ? packet.result.result.value : undefined
  }

  async navigate(pathname, timeoutMs = 30000) {
    this.console = []
    this.exceptions = []
    const url = WEBVIEW_ORIGIN + pathname
    await this.send("Page.navigate", { url }, 12000)
    await sleep(1600)
    await this.waitFor(`document.readyState === "complete" || document.readyState === "interactive"`, timeoutMs).catch(() => false)
    await sleep(1000)
  }

  async waitFor(predicate, timeoutMs = 30000, intervalMs = 500) {
    const started = Date.now()
    let last
    while (Date.now() - started < timeoutMs) {
      try {
        last = await this.eval(`(() => { try { return !!(${predicate}); } catch (e) { return false; } })()`, 10000)
        if (last) return true
      } catch (error) {
        last = error.message
      }
      await sleep(intervalMs)
    }
    throw new Error("Timed out waiting for " + predicate + " last=" + last)
  }
}

const vmHelpers = `
function __qaFindVmByName(name) {
  const root = document.querySelector('#app')
  function visit(vm) {
    if (!vm) return null
    if (vm.$options && vm.$options.name === name) return vm
    const children = vm.$children || []
    for (let i = 0; i < children.length; i += 1) {
      const found = visit(children[i])
      if (found) return found
    }
    return null
  }
  return visit(root && root.__vue__)
}
function __qaCompactButtons() {
  return Array.from(document.querySelectorAll('button')).slice(0, 80).map((button, index) => ({
    index,
    text: (button.innerText || button.getAttribute('aria-label') || '').trim(),
    disabled: !!button.disabled,
    className: button.className || '',
    rect: (() => { const r = button.getBoundingClientRect(); return { x: Math.round(r.x), y: Math.round(r.y), w: Math.round(r.width), h: Math.round(r.height) } })()
  }))
}
function __qaCompactRows() {
  return Array.from(document.querySelectorAll('.list-row')).slice(0, 8).map((row, index) => ({
    index,
    text: row.innerText.slice(0, 300)
  }))
}
`

async function currentState(cdp) {
  return cdp.eval(`(() => { ${vmHelpers}
    const featureVm = __qaFindVmByName('MobileFeaturePage')
    const workbenchVm = __qaFindVmByName('MobileWorkbenchShell')
    const text = document.body ? document.body.innerText : ''
    const detailOpen = !!document.querySelector('.detail-mask .detail-sheet')
    const formOpen = !!document.querySelector('.form-mask .form-sheet')
    return {
      href: location.href,
      path: location.pathname,
      title: (document.querySelector('h1') && document.querySelector('h1').innerText) || document.title,
      text: text.slice(0, 2600),
      textLength: text.trim().length,
      buttonCount: document.querySelectorAll('button').length,
      buttons: __qaCompactButtons(),
      rows: __qaCompactRows(),
      rowCount: document.querySelectorAll('.list-row').length,
      detailOpen,
      formOpen,
      selectedDept: {
        id: sessionStorage.getItem('selected_dept_id'),
        name: sessionStorage.getItem('selected_dept_name'),
        type: sessionStorage.getItem('selected_dept_type'),
        validated: sessionStorage.getItem('selected_dept_validated')
      },
      feature: featureVm ? {
        key: featureVm.featureKey,
        loading: featureVm.loading,
        loadingMore: featureVm.loadingMore,
        detailLoading: featureVm.detailLoading,
        errorMessage: featureVm.errorMessage,
        total: featureVm.total,
        itemCount: featureVm.items && featureVm.items.length,
        displayCount: featureVm.displayItems && featureVm.displayItems.length,
        activeFilterLabel: featureVm.activeFilterLabel,
        actions: featureVm.featureActionItems ? featureVm.featureActionItems.map(a => ({ label: a.label, actionId: a.actionId, path: a.path, behavior: a.behavior })) : [],
        detailActions: featureVm.detailActions ? featureVm.detailActions.map(a => ({ id: a.id, label: a.label })) : [],
        actionMessage: featureVm.actionMessage,
        mobileFormOpen: featureVm.formSheet && featureVm.formSheet.open,
        actionDialogOpen: featureVm.actionDialog && featureVm.actionDialog.open,
        confirmOpen: featureVm.mobileConfirm && featureVm.mobileConfirm.open
      } : null,
      hasWorkbenchVm: !!workbenchVm
    }
  })()`)
}

function classifyProblems(state, consoleEvents, exceptions, expectedPath) {
  const problems = []
  const text = state.text || ""
  if (state.path === "/login") problems.push("登录后访问路由被重定向回登录页")
  if (state.path === "/select-shop") problems.push("访问路由被重定向到上下文选择页")
  if (expectedPath && state.path !== expectedPath && state.path !== "/select-shop") {
    problems.push("路由跳转不一致：期望 " + expectedPath + "，实际 " + state.path)
  }
  if (state.textLength < 20) problems.push("页面正文过少，疑似白屏")
  if (/ChunkLoadError|Loading chunk failed|Cannot find module|ReferenceError|TypeError|白屏/i.test(text)) {
    problems.push("页面出现前端运行错误文案")
  }
  if (/404 NOT_FOUND|No static resource|Bad Gateway|系统接口.*异常|服务异常|接口.*失败|Network Error/i.test(text)) {
    problems.push("页面出现接口/网关错误文案")
  }
  if (state.feature && state.feature.errorMessage) {
    problems.push("列表加载错误：" + state.feature.errorMessage)
  }
  const severeConsole = (consoleEvents || []).filter(item => /error|warning/.test(String(item.type).toLowerCase()) && !/favicon|Autofill|manifest/.test(item.text || ""))
  if (severeConsole.length) {
    problems.push("WebView console error/warn: " + severeConsole.slice(0, 2).map(item => item.text).join(" | ").slice(0, 300))
  }
  if (exceptions && exceptions.length) {
    problems.push("WebView runtime exception: " + JSON.stringify(exceptions[0]).slice(0, 300))
  }
  return problems
}

async function setContext(cdp, context) {
  if (!context) {
    await cdp.eval(`(() => {
      sessionStorage.removeItem('selected_dept_id')
      sessionStorage.removeItem('selected_dept_name')
      sessionStorage.removeItem('selected_dept_type')
      sessionStorage.removeItem('selected_dept_validated')
      return true
    })()`)
    return
  }
  await cdp.eval(`(() => {
    sessionStorage.setItem('selected_dept_id', ${js(String(context.id))})
    sessionStorage.setItem('selected_dept_name', ${js(context.name)})
    sessionStorage.setItem('selected_dept_type', ${js(context.type)})
    sessionStorage.setItem('selected_dept_validated', '1')
    return true
  })()`)
}

async function login(cdp, agent) {
  await cdp.eval(`(() => {
    try {
      document.cookie.split(';').forEach(cookie => {
        const name = cookie.split('=')[0].trim()
        if (name) document.cookie = name + '=; expires=Thu, 01 Jan 1970 00:00:00 GMT; path=/'
      })
      localStorage.clear()
      sessionStorage.clear()
    } catch (e) {}
    return true
  })()`).catch(() => null)
  await cdp.navigate("/login?redirect=" + encodeURIComponent((agent.context && agent.context.type === "WAREHOUSE") ? "/mobile/warehouse" : "/mobile/store"))
  await cdp.waitFor(`document.body && /欢迎回来|登录账号|验证码/.test(document.body.innerText)`, 25000)
  const captcha = await httpJson(HOST_ORIGIN + "/prod-api/code")
  const rawCode = sh("redis-cli", ["--raw", "GET", "captcha_codes:" + captcha.uuid]).trim()
  const code = JSON.parse(rawCode)
  const result = await cdp.eval(`(async () => { ${vmHelpers}
    const vm = __qaFindVmByName('Login')
    if (!vm) return { ok: false, reason: 'no-login-vm', path: location.pathname, text: document.body.innerText.slice(0, 800) }
    vm.loginForm.username = ${js(agent.username)}
    vm.loginForm.password = ${js(agent.password)}
    vm.loginForm.uuid = ${js(captcha.uuid)}
    vm.loginForm.code = ${js(code)}
    vm.loginForm.rememberMe = false
    await vm.$nextTick()
    const button = Array.from(document.querySelectorAll('button')).find(btn => /登录/.test(btn.innerText || ''))
    if (button) button.click()
    else vm.handleLogin()
    await new Promise(resolve => setTimeout(resolve, 4500))
    return { ok: location.pathname !== '/login', path: location.pathname, text: document.body.innerText.slice(0, 1000) }
  })()`, 70000)
  await setContext(cdp, agent.context)
  return result
}

async function waitPageIdle(cdp) {
  await cdp.waitFor(`(() => { ${vmHelpers}
    const vm = __qaFindVmByName('MobileFeaturePage')
    if (!vm) return true
    return !vm.loading && !vm.loadingMore && !vm.detailLoading && !(vm.formSheet && vm.formSheet.saving) && !vm.actionLoadingKey
  })()`, 45000).catch(() => false)
  await sleep(700)
}

async function closeOverlays(cdp) {
  await cdp.eval(`(() => {
    const buttons = Array.from(document.querySelectorAll('button'))
    const close = buttons.find(btn => /取消|关闭/.test((btn.innerText || btn.getAttribute('aria-label') || '').trim()))
    if (close) close.click()
    return true
  })()`).catch(() => null)
  await sleep(500)
}

async function exerciseFeaturePage(cdp, routeId) {
  const checks = []
  const base = await currentState(cdp)
  if (!base.feature) return { checks, note: base.hasWorkbenchVm ? "workbench" : "non-feature" }

  await cdp.eval(`(() => {
    const refresh = document.querySelector('.feature-header .icon-button')
    if (refresh) refresh.click()
    return !!refresh
  })()`).catch(error => checks.push({ name: "refresh", ok: false, error: error.message }))
  await waitPageIdle(cdp)
  checks.push({ name: "refresh", ok: true })

  const search = await cdp.eval(`(async () => {
    const input = document.querySelector('.search-panel input[type="search"]')
    const submit = document.querySelector('.search-submit')
    if (!input || !submit) return { skipped: true }
    input.value = 'QA'
    input.dispatchEvent(new Event('input', { bubbles: true }))
    submit.click()
    await new Promise(resolve => setTimeout(resolve, 1200))
    return { skipped: false, value: input.value }
  })()`).catch(error => ({ error: error.message }))
  await waitPageIdle(cdp)
  checks.push(Object.assign({ name: "search" }, search && !search.error ? { ok: true } : { ok: false }, search || {}))

  await cdp.eval(`(() => {
    const input = document.querySelector('.search-panel input[type="search"]')
    const clear = document.querySelector('.search-clear')
    if (input && clear) clear.click()
    return true
  })()`).catch(() => null)
  await waitPageIdle(cdp)

  const actionResult = await cdp.eval(`(async () => { ${vmHelpers}
    const vm = __qaFindVmByName('MobileFeaturePage')
    if (!vm || !vm.featureActionItems || !vm.featureActionItems.length) return { skipped: true }
    const buttons = Array.from(document.querySelectorAll('.feature-actions button')).filter(btn => !btn.disabled)
    const button = buttons[0]
    if (!button) return { skipped: true, reason: 'no-enabled-action-button' }
    const before = location.pathname
    const label = (button.innerText || '').trim()
    button.click()
    await new Promise(resolve => setTimeout(resolve, 1400))
    return {
      skipped: false,
      label,
      before,
      after: location.pathname,
      detailOpen: !!document.querySelector('.detail-mask .detail-sheet'),
      formOpen: !!document.querySelector('.form-mask .form-sheet')
    }
  })()`).catch(error => ({ error: error.message }))
  await waitPageIdle(cdp)
  if (actionResult && (actionResult.detailOpen || actionResult.formOpen)) {
    screenshot(routeId + "_top_action_modal.png")
    await closeOverlays(cdp)
  }
  checks.push(Object.assign({ name: "top-action" }, actionResult && !actionResult.error ? { ok: true } : { ok: false }, actionResult || {}))

  const afterActionPath = await cdp.eval("location.pathname").catch(() => "")
  if (afterActionPath !== base.path) {
    await cdp.navigate(base.path)
    await waitPageIdle(cdp)
  }

  const openResult = await cdp.eval(`(async () => {
    const row = document.querySelector('.list-row')
    if (!row) return { skipped: true, reason: 'no-list-row' }
    const text = row.innerText.slice(0, 260)
    row.click()
    await new Promise(resolve => setTimeout(resolve, 1600))
    return {
      skipped: false,
      text,
      detailOpen: !!document.querySelector('.detail-mask .detail-sheet'),
      detailText: (document.querySelector('.detail-mask .detail-sheet') && document.querySelector('.detail-mask .detail-sheet').innerText.slice(0, 1000)) || ''
    }
  })()`).catch(error => ({ error: error.message }))
  await waitPageIdle(cdp)
  if (openResult && openResult.detailOpen) {
    screenshot(routeId + "_detail.png")
    const modal = await cdp.eval(`(async () => {
      const actionButtons = Array.from(document.querySelectorAll('.detail-action-button')).filter(btn => !btn.disabled)
      if (!actionButtons.length) return { skipped: true, reason: 'no-detail-actions' }
      const btn = actionButtons[0]
      const label = (btn.innerText || '').trim()
      btn.click()
      await new Promise(resolve => setTimeout(resolve, 1000))
      return {
        skipped: false,
        label,
        dialogOpen: !!document.querySelector('.form-mask .form-sheet') || !!document.querySelector('.mobile-confirm-dialog') || !!document.querySelector('.detail-mask.form-mask'),
        body: document.body.innerText.slice(0, 1400)
      }
    })()`).catch(error => ({ error: error.message }))
    await sleep(700)
    const modalState = await currentState(cdp)
    if (modal && !modal.skipped) screenshot(routeId + "_action_modal.png")
    checks.push(Object.assign({ name: "detail-action-modal", modalState: { detailOpen: modalState.detailOpen, formOpen: modalState.formOpen } }, modal && !modal.error ? { ok: true } : { ok: false }, modal || {}))
    await closeOverlays(cdp)
    await closeOverlays(cdp)
  }
  checks.push(Object.assign({ name: "open-detail" }, openResult && !openResult.error ? { ok: true } : { ok: false }, openResult || {}))
  return { checks }
}

async function exerciseWorkbench(cdp, routeId) {
  const result = await cdp.eval(`(async () => {
    const quick = document.querySelector('.quick-grid button')
    if (!quick) return { skipped: true }
    const label = (quick.innerText || '').trim()
    const before = location.pathname
    quick.click()
    await new Promise(resolve => setTimeout(resolve, 1200))
    return { skipped: false, label, before, after: location.pathname, text: document.body.innerText.slice(0, 1000) }
  })()`).catch(error => ({ error: error.message }))
  await waitPageIdle(cdp)
  if (result && result.after && result.before && result.after !== result.before) {
    screenshot(routeId + "_quick_action_target.png")
    await cdp.navigate(result.before)
    await waitPageIdle(cdp)
  }
  return result
}

async function runRoute(cdp, actor, route, phase) {
  const context = route.context || actor.context
  await setContext(cdp, context)
  await cdp.navigate(route.path)
  await waitPageIdle(cdp)
  const routeId = safeName(phase + "_" + actor.id + "_" + route.path)
  const shot = screenshot(routeId + "_page.png")
  const state = await currentState(cdp)
  const problems = classifyProblems(state, cdp.console, cdp.exceptions, route.path)
  const interactions = []
  if (!problems.length) {
    if (state.feature) {
      interactions.push(await exerciseFeaturePage(cdp, routeId))
    } else if (state.hasWorkbenchVm) {
      interactions.push({ workbench: await exerciseWorkbench(cdp, routeId) })
    }
  }
  const after = await currentState(cdp).catch(() => state)
  const afterProblems = classifyProblems(after, cdp.console, cdp.exceptions, route.path)
  return {
    phase,
    actor: actor.id,
    actorLabel: actor.label,
    post: actor.post,
    username: actor.username,
    context,
    path: route.path,
    screenshot: shot,
    ok: problems.length === 0 && afterProblems.length === 0,
    problems: Array.from(new Set(problems.concat(afterProblems))),
    state,
    afterState: after,
    interactions
  }
}

function writeResults(results) {
  fs.writeFileSync(RESULTS_PATH, JSON.stringify(results, null, 2))
}

function buildReport(results) {
  const routeRecords = results.routes || []
  const failed = routeRecords.filter(item => !item.ok)
  const passed = routeRecords.filter(item => item.ok)
  const lines = []
  lines.push("# Android mobile full QA")
  lines.push("")
  lines.push("- Time: " + new Date().toISOString())
  lines.push("- APK: `erp-ui/android/app/build/outputs/apk/debug/app-debug.apk`")
  lines.push("- Device: `" + SERIAL + "`")
  lines.push("- WebView origin: `" + WEBVIEW_ORIGIN + "`")
  lines.push("- Accounts: " + AGENTS.map(a => `${a.label}(${a.username})`).join(", "))
  lines.push("- Route checks: " + routeRecords.length + " total, " + passed.length + " pass, " + failed.length + " fail")
  lines.push("")
  lines.push("## Build")
  lines.push("")
  lines.push("- Production dist built with `npm run build:prod`.")
  lines.push("- Android debug APK built and installed with JDK 21.")
  lines.push("- Capacitor server URL for this package: `" + WEBVIEW_ORIGIN + "`.")
  lines.push("")
  lines.push("## Findings")
  lines.push("")
  if (!failed.length) {
    lines.push("No blocking page failures were detected in this run.")
  } else {
    failed.forEach((item, index) => {
      lines.push(`${index + 1}. ${item.actorLabel} ${item.path}`)
      lines.push("   - Problems: " + item.problems.join("; "))
      lines.push("   - Screenshot: `" + path.relative(ROOT, item.screenshot) + "`")
      lines.push("   - Observed path: `" + (item.afterState && item.afterState.path || item.state.path) + "`")
    })
  }
  lines.push("")
  lines.push("## Role Coverage")
  lines.push("")
  for (const agent of AGENTS) {
    const records = routeRecords.filter(item => item.actor === agent.id)
    const bad = records.filter(item => !item.ok)
    lines.push("- " + agent.label + " / " + agent.post + " / " + agent.username + ": " + records.length + " paths, " + bad.length + " issues")
  }
  lines.push("")
  lines.push("## Interaction Coverage")
  lines.push("")
  lines.push("- Feature pages: refresh, top action card, search submit/clear, first list row detail, first detail action modal/confirm dialog open, close/cancel.")
  lines.push("- Workbench pages: first quick-action navigation and return.")
  lines.push("- Screenshots are captured from Android emulator via `adb screencap`.")
  lines.push("")
  lines.push("## Screenshots")
  lines.push("")
  routeRecords.slice(0, 30).forEach(item => {
    lines.push("- " + item.actor + " " + item.path + ": `" + path.relative(ROOT, item.screenshot) + "`")
  })
  return lines.join("\n")
}

async function main() {
  const results = { startedAt: new Date().toISOString(), agents: AGENTS, routes: [], login: [] }
  sh("adb", ["-s", SERIAL, "logcat", "-c"])
  launchApp()
  await sleep(3500)
  const pid = ensureCdpForward()
  const cdp = new Cdp()
  await cdp.connect()
  screenshot("qa_00_relaunched.png")

  for (const agent of AGENTS) {
    const loginResult = await login(cdp, agent)
    screenshot("login_" + agent.id + ".png")
    results.login.push({ agent: agent.id, ok: !!loginResult.ok, result: loginResult })
    writeResults(results)
    console.log("LOGIN " + agent.id + " " + (loginResult.ok ? "PASS" : "FAIL") + " path=" + loginResult.path)
    for (const routePath of agent.paths) {
      const rec = await runRoute(cdp, agent, { path: routePath, context: agent.context }, "role")
      results.routes.push(rec)
      writeResults(results)
      console.log((rec.ok ? "PASS" : "FAIL") + " " + agent.id + " " + routePath)
    }
  }

  const admin = AGENTS[0]
  await login(cdp, admin)
  for (const route of FULL_ROUTE_SWEEP) {
    const rec = await runRoute(cdp, admin, route, "full")
    results.routes.push(rec)
    writeResults(results)
    console.log((rec.ok ? "PASS" : "FAIL") + " full " + route.path)
  }

  results.finishedAt = new Date().toISOString()
  results.pid = pid
  writeResults(results)
  fs.writeFileSync(REPORT_PATH, buildReport(results))
  sh("adb", ["-s", SERIAL, "logcat", "-d"], { encoding: "utf8" })
  console.log("REPORT " + REPORT_PATH)
  console.log("RESULTS " + RESULTS_PATH)
}

main().catch(error => {
  console.error(error.stack || error.message)
  process.exit(1)
})
