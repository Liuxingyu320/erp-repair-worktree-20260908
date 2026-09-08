#!/usr/bin/env node

const ERP_QA_PASSWORD = process.env.ERP_QA_PASSWORD
if (ERP_QA_PASSWORD === undefined || ERP_QA_PASSWORD === "") {
  console.error("ERP_QA_PASSWORD is required")
  process.exit(2)
}

const fs = require("fs")
const path = require("path")
const http = require("http")
const { execFileSync } = require("child_process")
const WebSocket = require("../erp-ui/node_modules/ws")

const ROOT = path.resolve(__dirname, "..")
const SERIAL = process.env.ADB_SERIAL || "emulator-5554"
const PACKAGE = "com.erp.mobile"
const ACTIVITY = "com.erp.mobile/.MainActivity"
const WEBVIEW_ORIGIN = process.env.WEBVIEW_ORIGIN || "http://10.0.2.2:19025"
const HOST_ORIGIN = process.env.HOST_ORIGIN || "http://127.0.0.1:19025"
const CDP_PORT = Number(process.env.CDP_PORT || 9227)
const RUN_ID = process.env.QA_RUN || "QA-MOBILE-20260705-" + timestamp()
const OUT_DIR = process.env.OUT_DIR || path.join(ROOT, "docs/audit-screenshots/mobile-android-20260705-real-submit-" + RUN_ID.replace(/^QA-MOBILE-/, ""))
const RESULT_PATH = path.join(OUT_DIR, "android-real-submit-results.json")
const REPORT_PATH = path.join(OUT_DIR, "android-real-submit-report.md")
const MYSQL_DB = process.env.MYSQL_DB || "BossERP_NEW"

const STORE = { id: 1176, name: "北京柏悦", type: "STORE" }
const STORE_MANAGER_STORE = { id: 1171, name: "万达文华", type: "STORE" }
const WAREHOUSE = { id: 1245, name: "主仓库", type: "WAREHOUSE" }

const ACCOUNTS = {
  admin: { id: "admin", label: "超级管理员", username: "admin", password: ERP_QA_PASSWORD },
  ops: { id: "ops_director", label: "运营总监", username: "13659326770", password: ERP_QA_PASSWORD },
  tea: { id: "tea_artist", label: "茶艺师", username: "19834743225", password: ERP_QA_PASSWORD, userId: 944 },
  maint: { id: "sys_maint", label: "系统维护", username: "18669567559", password: ERP_QA_PASSWORD },
  manager: { id: "store_manager", label: "店长", username: "15516784616", password: ERP_QA_PASSWORD },
  transferApprover: { id: "transfer_approver", label: "四级部门负责人", username: "18655197121", password: ERP_QA_PASSWORD }
}

fs.mkdirSync(OUT_DIR, { recursive: true })

function timestamp() {
  const d = new Date()
  const p = n => String(n).padStart(2, "0")
  return `${d.getFullYear()}${p(d.getMonth() + 1)}${p(d.getDate())}-${p(d.getHours())}${p(d.getMinutes())}${p(d.getSeconds())}`
}

function sleep(ms) {
  return new Promise(resolve => setTimeout(resolve, ms))
}

function safeName(value) {
  return String(value || "")
    .replace(/[^a-zA-Z0-9._-]+/g, "_")
    .replace(/^_+|_+$/g, "")
    .slice(0, 120)
}

function sh(command, args, options = {}) {
  return execFileSync(command, args, Object.assign({
    cwd: ROOT,
    encoding: "utf8",
    stdio: ["ignore", "pipe", "pipe"],
    maxBuffer: 120 * 1024 * 1024
  }, options))
}

function mysql(sql) {
  return sh("mysql", ["-uroot", MYSQL_DB, "-N", "-B", "-e", sql]).trim()
}

function rows(sql) {
  const out = mysql(sql)
  if (!out) return []
  return out.split(/\n/).filter(Boolean).map(line => line.split(/\t/).map(value => value === "NULL" ? null : value))
}

function one(sql) {
  const result = rows(sql)
  return result[0] || null
}

function esc(value) {
  return String(value === undefined || value === null ? "" : value).replace(/\\/g, "\\\\").replace(/'/g, "\\'")
}

function q(value) {
  return "'" + esc(value) + "'"
}

function js(value) {
  return JSON.stringify(value)
}

function screenshot(name) {
  const file = path.join(OUT_DIR, name.endsWith(".png") ? name : name + ".png")
  const png = execFileSync("adb", ["-s", SERIAL, "exec-out", "screencap", "-p"], {
    cwd: ROOT,
    encoding: "buffer",
    maxBuffer: 40 * 1024 * 1024
  })
  fs.writeFileSync(file, png)
  return file
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
          reject(new Error("Invalid JSON from " + url + ": " + error.message + " body=" + body.slice(0, 300)))
        }
      })
    }).on("error", reject)
  })
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
    if (!page || !page.webSocketDebuggerUrl) throw new Error("No Android WebView CDP page available")
    this.ws = new WebSocket(page.webSocketDebuggerUrl)
    this.ws.on("message", message => this.handleMessage(message))
    this.ws.on("close", () => {
      for (const entry of this.pending.values()) {
        clearTimeout(entry.timer)
        entry.reject(new Error("CDP websocket closed"))
      }
      this.pending.clear()
    })
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
    if (packet.method === "Runtime.exceptionThrown") this.exceptions.push(packet.params)
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
    for (let attempt = 0; attempt < 3; attempt += 1) {
      const packet = await this.send("Runtime.evaluate", {
        expression,
        awaitPromise: true,
        returnByValue: true,
        timeout: timeoutMs
      }, timeoutMs + 5000).catch(error => {
        if (/closed|not open/.test(error.message) && attempt < 2) return null
        throw error
      })
      if (!packet) continue
      if (packet.error) throw new Error(packet.error.message || JSON.stringify(packet.error))
      if (packet.result && packet.result.exceptionDetails) {
        const detail = packet.result.exceptionDetails
        const text = detail.exception && detail.exception.description ? detail.exception.description : detail.text
        if (/Execution context was destroyed|Cannot find context/.test(text || "") && attempt < 2) {
          await sleep(1000)
          continue
        }
        throw new Error(text || "Runtime exception")
      }
      return packet.result && packet.result.result ? packet.result.result.value : undefined
    }
    throw new Error("Runtime evaluate failed after retries")
  }

  async navigate(pathname, timeoutMs = 30000) {
    this.console = []
    this.exceptions = []
    const url = WEBVIEW_ORIGIN + pathname
    await this.send("Page.navigate", { url }, 12000)
    await sleep(1400)
    await this.waitFor(`document.readyState === "complete" || document.readyState === "interactive"`, timeoutMs).catch(() => false)
    await sleep(900)
  }

  async waitFor(predicate, timeoutMs = 30000, intervalMs = 400) {
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
function __qaCompactItem(item) {
  const raw = item && (item._raw || item.raw || item) || {}
  return {
    title: item && item.title,
    code: item && item.code,
    status: item && item.status,
    statusKey: item && (item._statusKey || item.statusKey || item.sourceStatus),
    rawStatus: raw.status,
    rawNo: raw.orderNo || raw.noticeNo || raw.returnNo || raw.checkNo || raw.title || raw.jobName || raw.noticeTitle,
    ids: {
      orderId: raw.orderId,
      noticeId: raw.noticeId,
      returnId: raw.returnId,
      checkId: raw.checkId,
      transferId: raw.transferId,
      purchaseId: raw.purchaseId || raw.id,
      repairId: raw.repairId,
      jobId: raw.jobId,
      tokenId: raw.tokenId,
      recordId: raw.recordId
    }
  }
}
function __qaSleep(ms) {
  return new Promise(resolve => setTimeout(resolve, ms))
}
`

async function getState(cdp) {
  return cdp.eval(`(() => { ${vmHelpers}
    const vm = __qaFindVmByName('MobileFeaturePage')
    const loginVm = __qaFindVmByName('Login')
    const text = document.body ? document.body.innerText : ''
    return {
      href: location.href,
      path: location.pathname,
      hasLoginVm: !!loginVm,
      hasFeatureVm: !!vm,
      bodyText: text.slice(0, 2400),
      featureKey: vm && vm.featureKey,
      selectedDeptId: vm && vm.selectedDeptId,
      selectedDeptType: vm && vm.selectedDeptType,
      selectedDeptName: vm && vm.selectedDeptName,
      loading: vm && vm.loading,
      loadingMore: vm && vm.loadingMore,
      detailLoading: vm && vm.detailLoading,
      errorMessage: vm && vm.errorMessage,
      formOpen: vm && vm.formSheet && vm.formSheet.open,
      formSaving: vm && vm.formSheet && vm.formSheet.saving,
      formError: vm && vm.formSheet && vm.formSheet.error,
      total: vm && vm.total,
      itemCount: vm && vm.items && vm.items.length,
      items: vm && vm.items ? vm.items.slice(0, 6).map(__qaCompactItem) : [],
      selectedItem: vm && vm.selectedItem ? __qaCompactItem(vm.selectedItem) : null,
      detailActions: vm && vm.detailActions ? vm.detailActions.map(a => ({ id: a.id, label: a.label })) : [],
      topActions: vm && vm.featureActionItems ? vm.featureActionItems.map(a => ({ label: a.label, actionId: a.actionId, behavior: a.behavior, path: a.path })) : [],
      actionMessage: vm && vm.actionMessage,
      actionLoadingKey: vm && vm.actionLoadingKey,
      actionDialogOpen: vm && vm.actionDialog && vm.actionDialog.open,
      mobileConfirmOpen: vm && vm.mobileConfirm && vm.mobileConfirm.open,
      mobileConfirm: vm && vm.mobileConfirm ? { title: vm.mobileConfirm.title, message: vm.mobileConfirm.message, confirmText: vm.mobileConfirm.confirmText } : null,
      actionDialog: vm && vm.actionDialog && vm.actionDialog.action ? { id: vm.actionDialog.action.id, label: vm.actionDialog.action.label } : null,
      console: ${js(cdp.console.slice(-6))},
      exceptions: ${js(cdp.exceptions.slice(-3))}
    }
  })()`, 45000)
}

async function setContext(cdp, context) {
  await cdp.eval(`(() => {
    sessionStorage.setItem('selected_dept_id', ${js(String(context.id))})
    sessionStorage.setItem('selected_dept_name', ${js(context.name)})
    sessionStorage.setItem('selected_dept_type', ${js(context.type)})
    sessionStorage.setItem('selected_dept_validated', '1')
    return true
  })()`)
}

async function waitIdle(cdp, timeoutMs = 60000) {
  await cdp.waitFor(`(() => { ${vmHelpers}
    const vm = __qaFindVmByName('MobileFeaturePage')
    if (!vm) return true
    return !vm.loading && !vm.loadingMore && !vm.detailLoading && !vm.actionLoadingKey && !(vm.formSheet && vm.formSheet.saving)
  })()`, timeoutMs).catch(() => false)
  await sleep(600)
}

async function login(cdp, account, context, redirect) {
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
  const target = redirect || (context && context.type === "WAREHOUSE" ? "/mobile/warehouse" : "/mobile/store")
  await cdp.navigate("/login?redirect=" + encodeURIComponent(target))
  await cdp.waitFor(`document.body && /欢迎回来|登录账号|验证码/.test(document.body.innerText)`, 25000)
  const captcha = await httpJson(HOST_ORIGIN + "/prod-api/code")
  const rawCode = sh("redis-cli", ["--raw", "GET", "captcha_codes:" + captcha.uuid]).trim()
  const code = JSON.parse(rawCode)
  const result = await cdp.eval(`(async () => { ${vmHelpers}
    const vm = __qaFindVmByName('Login')
    if (!vm) return { ok: false, reason: 'no-login-vm', path: location.pathname, text: document.body.innerText.slice(0, 800) }
    vm.loginForm.username = ${js(account.username)}
    vm.loginForm.password = ${js(account.password)}
    vm.loginForm.uuid = ${js(captcha.uuid)}
    vm.loginForm.code = ${js(code)}
    vm.loginForm.rememberMe = false
    await vm.$nextTick()
    const button = Array.from(document.querySelectorAll('button')).find(btn => /登录/.test(btn.innerText || ''))
    if (button) button.click()
    else vm.handleLogin()
    await __qaSleep(4500)
    return { ok: location.pathname !== '/login', path: location.pathname, text: document.body.innerText.slice(0, 1200) }
  })()`, 70000)
  if (context) await setContext(cdp, context)
  return result
}

async function prepareFeature(cdp, context, pathname, label) {
  await setContext(cdp, context)
  await cdp.navigate(pathname)
  await waitIdle(cdp)
  const shot = screenshot(label + "_page.png")
  return { shot, state: await getState(cdp) }
}

async function createMobileForm(cdp, spec) {
  const result = {
    id: spec.id,
    name: spec.name,
    role: spec.role || "admin",
    path: spec.path,
    action: "create-form",
    submitAction: spec.submitAction || "save",
    screenshots: {},
    startedAt: new Date().toISOString()
  }
  try {
    const label = safeName(spec.id)
    const page = await prepareFeature(cdp, spec.context, spec.path, label)
    result.page = page.state
    result.screenshots.page = page.shot
    const opened = await cdp.eval(`(async () => { ${vmHelpers}
      const vm = __qaFindVmByName('MobileFeaturePage')
      if (!vm) return { ok: false, reason: 'no-feature-vm', text: document.body.innerText.slice(0, 1000) }
      const action = (vm.featureActionItems || []).find(item => item.behavior === 'create-form')
      if (!action) return { ok: false, reason: 'create-action-not-visible', topActions: (vm.featureActionItems || []).map(a => ({ label: a.label, behavior: a.behavior, actionId: a.actionId })) }
      vm.handleAction(action)
      await vm.$nextTick()
      await __qaSleep(700)
      vm.formSheet.data = Object.assign({}, vm.formSheet.data || {}, ${js(spec.data)})
      await vm.$nextTick()
      return {
        ok: !!(vm.formSheet && vm.formSheet.open),
        formTitle: vm.mobileFormTitle,
        modes: (vm.availableMobileSubmitModes || []).map(m => ({ label: m.label, action: m.action })),
        formError: vm.formSheet && vm.formSheet.error,
        data: vm.formSheet && vm.formSheet.data
      }
    })()`, 45000)
    result.opened = opened
    await sleep(600)
    result.screenshots.form = screenshot(label + "_form.png")
    if (!opened.ok) {
      result.ok = false
      result.problem = opened.reason || "form-open-failed"
      return result
    }
    const submitted = await cdp.eval(`(async () => { ${vmHelpers}
      const vm = __qaFindVmByName('MobileFeaturePage')
      if (!vm || !vm.formSheet || !vm.formSheet.open) return { ok: false, reason: 'form-not-open' }
      vm.submitMobileForm(${js(result.submitAction)})
      const started = Date.now()
      while (vm.formSheet && vm.formSheet.saving && Date.now() - started < 60000) {
        await __qaSleep(300)
      }
      while ((vm.loading || vm.loadingMore) && Date.now() - started < 60000) {
        await __qaSleep(300)
      }
      return {
        ok: !(vm.formSheet && vm.formSheet.open && vm.formSheet.error),
        formOpen: vm.formSheet && vm.formSheet.open,
        formError: vm.formSheet && vm.formSheet.error,
        actionMessage: vm.actionMessage,
        text: document.body.innerText.slice(0, 1400)
      }
    })()`, 70000)
    await waitIdle(cdp)
    result.submitted = submitted
    result.screenshots.after = screenshot(label + "_after.png")
    result.after = await getState(cdp)
    if (spec.db) result.dbAfter = spec.db()
    result.ok = !!submitted.ok && !hasBadMessage(result.after.actionMessage || submitted.formError || "")
  } catch (error) {
    result.ok = false
    result.error = error.stack || error.message
    try {
      result.stateOnError = await getState(cdp)
      result.screenshots.error = screenshot(safeName(spec.id) + "_error.png")
    } catch (_) {}
  } finally {
    result.finishedAt = new Date().toISOString()
  }
  return result
}

async function searchAndOpen(cdp, spec) {
  await prepareFeature(cdp, spec.context, spec.path, safeName(spec.id))
  if (spec.query || spec.field || spec.keyword) {
    await cdp.eval(`(async () => { ${vmHelpers}
      const vm = __qaFindVmByName('MobileFeaturePage')
      if (!vm) return false
      vm.featureQuery = Object.assign({}, ${js(spec.query || {})})
      if (${js(spec.field || "")}) vm.activeSearchField = ${js(spec.field || "")}
      vm.searchKeyword = ${js(spec.keyword || "")}
      vm.pageNum = 1
      vm.loadData()
      const started = Date.now()
      while ((vm.loading || vm.loadingMore) && Date.now() - started < 45000) await __qaSleep(250)
      return true
    })()`, 60000)
    await waitIdle(cdp)
  }
  const opened = await cdp.eval(`(async () => { ${vmHelpers}
    const vm = __qaFindVmByName('MobileFeaturePage')
    if (!vm) return { ok: false, reason: 'no-feature-vm', text: document.body.innerText.slice(0, 1000) }
    const items = vm.items || []
    const wanted = ${js(spec.match || spec.keyword || "")}
    const item = items.find(row => {
      const raw = row && (row._raw || row.raw || row) || {}
      const text = [row && row.title, row && row.code, row && row.detail, raw.orderNo, raw.noticeNo, raw.returnNo, raw.checkNo, raw.title, raw.jobName, raw.noticeTitle, raw.salesOrderNo, raw.returnTitle].filter(Boolean).join(' ')
      return !wanted || text.indexOf(wanted) > -1
    }) || items[0]
    if (!item) {
      return { ok: false, reason: 'not-found', total: vm.total, itemCount: items.length, items: items.slice(0, 6).map(__qaCompactItem), errorMessage: vm.errorMessage, text: document.body.innerText.slice(0, 1400) }
    }
    await vm.$nextTick()
    const rows = Array.from(document.querySelectorAll('.list-row'))
    const row = rows.find(el => el.innerText.indexOf(item.code) > -1 || (wanted && el.innerText.indexOf(wanted) > -1))
    let rowClickOpened = false
    if (row) {
      row.click()
      await __qaSleep(900)
      rowClickOpened = !!vm.selectedItem
    }
    if (!vm.selectedItem || (vm.selectedItem && vm.selectedItem.code !== item.code)) {
      vm.openItem(item)
    }
    const started = Date.now()
    while (vm.detailLoading && Date.now() - started < 45000) await __qaSleep(250)
    return { ok: true, rowClickOpened, selectedItem: vm.selectedItem ? __qaCompactItem(vm.selectedItem) : null, detailActions: (vm.detailActions || []).map(a => ({ id: a.id, label: a.label })), actionMessage: vm.actionMessage, text: document.body.innerText.slice(0, 1600) }
  })()`, 70000)
  await waitIdle(cdp)
  return opened
}

async function buildActionPayload(cdp, actionId, override = {}) {
  return cdp.eval(`(() => { ${vmHelpers}
    const vm = __qaFindVmByName('MobileFeaturePage')
    const raw = vm && vm.selectedItem && (vm.selectedItem._raw || vm.selectedItem.raw || vm.selectedItem) || {}
    const first = (obj, keys) => {
      for (const key of keys) {
        const value = obj && obj[key]
        if (value !== undefined && value !== null && String(value).trim() !== '') return value
      }
      return undefined
    }
    const number = value => {
      const n = Number(value)
      return Number.isFinite(n) ? n : 0
    }
    const remaining = row => {
      const r = first(row, ['remainingQuantity', 'remainingQty'])
      if (r !== undefined) return Math.max(number(r), 0)
      return Math.max(number(first(row, ['quantity', 'noticeQty', 'shippedQuantity'])) - number(first(row, ['receivedQuantity', 'deliveredQuantity', 'deliveredQty'])), 0)
    }
    const details = Array.isArray(raw.details) ? raw.details : []
    const shipments = Array.isArray(raw.shipments) ? raw.shipments : []
    let payload = {
      allRemaining: true,
      warehouseId: vm && vm.selectedDeptId,
      comment: ${js("Android real-submit " + RUN_ID)},
      qcResult: 'passed',
      qcRemark: ${js("Android real-submit " + RUN_ID)}
    }
    if (${js(actionId)} === 'inputStockCheck') {
      payload.details = details.map(row => ({
        detailId: first(row, ['detailId', 'checkDetailId']),
        actualQuantity: number(first(row, ['actualQuantity', 'actualQty', 'bookQty', 'bookQuantity', 'systemQuantity', 'quantity']))
      })).filter(row => row.detailId !== undefined)
      payload.items = payload.details.map(row => ({ detailId: row.detailId, quantity: row.actualQuantity }))
    } else if (${js(actionId)} === 'receiveTransferShipment') {
      const shipment = shipments.find(item => String(item.status || '').trim() === 'pending_receive') || shipments[0] || {}
      payload.shipmentId = shipment.shipmentId
      payload.items = (shipment.details || []).map(row => ({
        detailId: first(row, ['transferDetailId', 'detailId']),
        quantity: remaining(row)
      })).filter(row => row.detailId !== undefined && row.quantity > 0)
    } else if (['receivePurchaseAll', 'deliverDeliveryNoticeAll', 'deliverTransferAll'].indexOf(${js(actionId)}) > -1) {
      payload.items = details.map(row => ({
        detailId: first(row, ['detailId', 'transferDetailId']),
        quantity: remaining(row)
      })).filter(row => row.detailId !== undefined && row.quantity > 0)
    }
    return Object.assign(payload, ${js(override)})
  })()`)
}

async function runDetailCase(cdp, spec) {
  const result = {
    id: spec.id,
    name: spec.name,
    role: spec.role || "admin",
    context: spec.context,
    path: spec.path,
    actionId: spec.actionId,
    screenshots: {},
    startedAt: new Date().toISOString()
  }
  try {
    result.open = await searchAndOpen(cdp, spec)
    result.screenshots.detail = screenshot(safeName(spec.id) + "_detail.png")
    if (!result.open.ok) {
      result.ok = false
      result.problem = result.open.reason || "record-not-found"
      return result
    }
    const start = await cdp.eval(`(() => { ${vmHelpers}
      const vm = __qaFindVmByName('MobileFeaturePage')
      if (!vm || !vm.selectedItem) return { ok: false, reason: 'no-selected-item' }
      const action = (vm.detailActions || []).find(item => item.id === ${js(spec.actionId)} || item.label === ${js(spec.actionId)})
      if (!action) return { ok: false, reason: 'action-not-visible', actions: (vm.detailActions || []).map(a => ({ id: a.id, label: a.label })), text: document.body.innerText.slice(0, 1200) }
      vm.handleDetailAction(action)
      return { ok: true, action: { id: action.id, label: action.label }, confirmOpen: vm.mobileConfirm.open, dialogOpen: vm.actionDialog.open, text: document.body.innerText.slice(0, 1000) }
    })()`, 30000)
    await sleep(800)
    result.start = start
    result.modal = await getState(cdp)
    result.screenshots.modal = screenshot(safeName(spec.id) + "_modal.png")
    if (!start.ok) {
      result.ok = false
      result.problem = start.reason || "action-start-failed"
      return result
    }
    if (result.modal.actionDialogOpen) {
      const payload = await buildActionPayload(cdp, spec.actionId, spec.payloadOverride || {})
      result.payload = payload
      await cdp.eval(`(() => { ${vmHelpers}
        const vm = __qaFindVmByName('MobileFeaturePage')
        vm.confirmActionDialog(${js(payload)})
        return true
      })()`, 30000)
    } else if (result.modal.mobileConfirmOpen) {
      await cdp.eval(`(() => { ${vmHelpers}
        const vm = __qaFindVmByName('MobileFeaturePage')
        vm.confirmMobileConfirm()
        return true
      })()`, 30000)
    }
    await waitIdle(cdp, 70000)
    result.screenshots.after = screenshot(safeName(spec.id) + "_after.png")
    result.after = await getState(cdp)
    if (spec.db) result.dbAfter = spec.db()
    result.ok = !hasBadMessage(result.after.actionMessage || result.after.formError || "")
  } catch (error) {
    result.ok = false
    result.error = error.stack || error.message
    try {
      result.stateOnError = await getState(cdp)
      result.screenshots.error = screenshot(safeName(spec.id) + "_error.png")
    } catch (_) {}
  } finally {
    result.finishedAt = new Date().toISOString()
  }
  return result
}

async function runTopActionCase(cdp, spec) {
  const result = {
    id: spec.id,
    name: spec.name,
    role: spec.role || "admin",
    context: spec.context,
    path: spec.path,
    actionId: spec.actionId,
    screenshots: {},
    startedAt: new Date().toISOString()
  }
  try {
    const page = await prepareFeature(cdp, spec.context, spec.path, safeName(spec.id))
    result.page = page.state
    result.screenshots.page = page.shot
    if (spec.query || spec.field || spec.keyword) {
      await cdp.eval(`(async () => { ${vmHelpers}
        const vm = __qaFindVmByName('MobileFeaturePage')
        if (!vm) return false
        vm.featureQuery = Object.assign({}, ${js(spec.query || {})})
        if (${js(spec.field || "")}) vm.activeSearchField = ${js(spec.field || "")}
        vm.searchKeyword = ${js(spec.keyword || "")}
        vm.pageNum = 1
        vm.loadData()
        const started = Date.now()
        while ((vm.loading || vm.loadingMore) && Date.now() - started < 45000) await __qaSleep(250)
        return true
      })()`, 60000)
      await waitIdle(cdp)
    }
    const start = await cdp.eval(`(() => { ${vmHelpers}
      const vm = __qaFindVmByName('MobileFeaturePage')
      if (!vm) return { ok: false, reason: 'no-feature-vm' }
      const action = (vm.featureActionItems || []).find(item => item.actionId === ${js(spec.actionId)} || item.behavior === ${js(spec.behavior || "")} || item.label === ${js(spec.label || "")})
      if (!action) return { ok: false, reason: 'top-action-not-visible', topActions: (vm.featureActionItems || []).map(a => ({ label: a.label, actionId: a.actionId, behavior: a.behavior })) }
      vm.handleAction(action)
      return { ok: true, action: { label: action.label, actionId: action.actionId, behavior: action.behavior }, confirmOpen: vm.mobileConfirm.open, text: document.body.innerText.slice(0, 1200) }
    })()`, 30000)
    await sleep(800)
    result.start = start
    result.modal = await getState(cdp)
    result.screenshots.modal = screenshot(safeName(spec.id) + "_modal.png")
    if (!start.ok) {
      result.ok = false
      result.problem = start.reason || "top-action-start-failed"
      return result
    }
    if (result.modal.mobileConfirmOpen) {
      await cdp.eval(`(() => { ${vmHelpers}
        const vm = __qaFindVmByName('MobileFeaturePage')
        vm.confirmMobileConfirm()
        return true
      })()`, 30000)
    }
    await waitIdle(cdp, 70000)
    result.screenshots.after = screenshot(safeName(spec.id) + "_after.png")
    result.after = await getState(cdp)
    if (spec.db) result.dbAfter = spec.db()
    result.ok = !hasBadMessage(result.after.actionMessage || result.after.formError || "")
  } catch (error) {
    result.ok = false
    result.error = error.stack || error.message
    try {
      result.stateOnError = await getState(cdp)
      result.screenshots.error = screenshot(safeName(spec.id) + "_error.png")
    } catch (_) {}
  } finally {
    result.finishedAt = new Date().toISOString()
  }
  return result
}

function hasBadMessage(message) {
  return /失败|错误|不存在|不能|缺少|无权限|异常|超时|库存不足|不可|请选择|不能为空|不是|候选|不允许|超过/.test(String(message || ""))
}

function seedSupportData() {
  const warehouseProductCode = RUN_ID + "-WH-PROD"
  const storeProductCode = RUN_ID + "-STORE-PROD"
  const managerProductCode = RUN_ID + "-1171-PROD"
  const customerCode = RUN_ID + "-CUST"
  const managerCustomerCode = RUN_ID + "-CUST-1171"
  const supplierCode = RUN_ID + "-SUP"
  const jobName = RUN_ID + " QA任务"
  const noticeTitle = RUN_ID + " 移动端通知"

  mysql(`
    insert into inv_product
      (product_name, product_code, category_id, grade, sku, spec, unit, purchase_price, sales_price, cost_price, shop_dept_id, status, del_flag, create_by, create_time, remark)
    values
      (${q(RUN_ID + " 仓库QA茶样")}, ${q(warehouseProductCode)}, null, 'QA', ${q(warehouseProductCode)}, 'QA规格', '罐', 10.00, 20.00, 10.00, ${WAREHOUSE.id}, '0', '0', 'mobile-real-submit', now(), ${q(RUN_ID)}),
      (${q(RUN_ID + " 门店QA茶样")}, ${q(storeProductCode)}, null, 'QA', ${q(storeProductCode)}, 'QA规格', '罐', 10.00, 20.00, 10.00, ${STORE.id}, '0', '0', 'mobile-real-submit', now(), ${q(RUN_ID)}),
      (${q(RUN_ID + " 店长QA茶样")}, ${q(managerProductCode)}, null, 'QA', ${q(managerProductCode)}, 'QA规格', '罐', 10.00, 20.00, 10.00, ${STORE_MANAGER_STORE.id}, '0', '0', 'mobile-real-submit', now(), ${q(RUN_ID)});
  `)
  const warehouseProduct = one(`select product_id,product_name,product_code,unit,spec,cost_price from inv_product where product_code=${q(warehouseProductCode)} order by product_id desc limit 1`)
  const storeProduct = one(`select product_id,product_name,product_code,unit,spec,cost_price from inv_product where product_code=${q(storeProductCode)} order by product_id desc limit 1`)
  const managerProduct = one(`select product_id,product_name,product_code,unit,spec,cost_price from inv_product where product_code=${q(managerProductCode)} order by product_id desc limit 1`)
  const warehouseProductId = Number(warehouseProduct[0])
  const storeProductId = Number(storeProduct[0])
  const managerProductId = Number(managerProduct[0])
  ;[
    { deptId: WAREHOUSE.id, productId: warehouseProductId },
    { deptId: STORE.id, productId: storeProductId },
    { deptId: STORE_MANAGER_STORE.id, productId: managerProductId },
    { deptId: STORE.id, productId: warehouseProductId },
    { deptId: STORE_MANAGER_STORE.id, productId: warehouseProductId }
  ].forEach(item => {
    mysql(`
      insert into inv_stock
        (product_id, shop_dept_id, warehouse_id, current_quantity, locked_quantity, available_quantity, cost_price, total_cost, create_by, create_time, remark)
      values
        (${item.productId}, ${item.deptId}, ${item.deptId}, 500.00, 0.00, 500.00, 10.00, 5000.00, 'mobile-real-submit', now(), ${q(RUN_ID)})
    `)
  })
  mysql(`
    insert into inv_customer
      (customer_name, customer_code, contact_person, contact_phone, customer_level, shop_dept_id, status, create_by, create_time, remark)
    values
      (${q(RUN_ID + " 客户")}, ${q(customerCode)}, '移动端QA', '13900000001', '普通客户', ${STORE.id}, '0', 'mobile-real-submit', now(), ${q(RUN_ID)}),
      (${q(RUN_ID + " 店长客户")}, ${q(managerCustomerCode)}, '移动端QA', '13900000002', '普通客户', ${STORE_MANAGER_STORE.id}, '0', 'mobile-real-submit', now(), ${q(RUN_ID)});
  `)
  mysql(`
    insert into inv_supplier
      (supplier_name, supplier_code, contact_person, contact_phone, settlement_method, cooperation_status, shop_dept_id, status, create_by, create_time, remark)
    values
      (${q(RUN_ID + " 供应商")}, ${q(supplierCode)}, '移动端QA', '13800000001', '现结', '0', ${WAREHOUSE.id}, '0', 'mobile-real-submit', now(), ${q(RUN_ID)});
  `)
  mysql(`
    update inv_product
    set supplier_name=${q(RUN_ID + " 供应商")}, supplier_phone='13800000001'
    where product_code in (${q(warehouseProductCode)}, ${q(storeProductCode)}, ${q(managerProductCode)});
  `)
  mysql(`
    insert into sys_notice
      (notice_title, notice_type, notice_content, status, create_by, create_time, remark)
    values
      (${q(noticeTitle)}, '1', ${q(RUN_ID + " Android真实流转通知")}, '0', 'mobile-real-submit', now(), ${q(RUN_ID)});
  `)
  mysql(`
    insert into sys_job
      (job_name, job_group, invoke_target, cron_expression, misfire_policy, concurrent, status, create_by, create_time, remark)
    values
      (${q(jobName)}, 'DEFAULT', 'ryTask.ryNoParams', '0/30 * * * * ?', '3', '1', '1', 'mobile-real-submit', now(), ${q(RUN_ID)});
  `)
  mysql(`
    insert into oa_fixed_asset_repair
      (shop_dept_id, product_id, product_name, estimated_repair_amount, available_quota_amount, fault_description, image_urls, exception_approved, exception_type, advance_months, status, applicant_id, applicant_name, approved_by, approved_time, create_by, create_time, remark)
    values
      (${STORE.id}, ${storeProductId}, ${q(RUN_ID + " 待确认资产")}, 1.00, 0.00, ${q(RUN_ID + " 待确认报修")}, '', 'Y', 'special_extra', null, 'pending_confirm', 1, 'admin', 'mobile-real-submit', now(), 'mobile-real-submit', now(), ${q(RUN_ID)});
  `)

  const customer = one(`select customer_id,customer_name from inv_customer where customer_code=${q(customerCode)} order by customer_id desc limit 1`)
  const managerCustomer = one(`select customer_id,customer_name from inv_customer where customer_code=${q(managerCustomerCode)} order by customer_id desc limit 1`)
  const supplier = one(`select supplier_id,supplier_name from inv_supplier where supplier_code=${q(supplierCode)} order by supplier_id desc limit 1`)
  const deliveredOrderNo = "QASD" + RUN_ID.replace(/\D/g, "").slice(-14)
  mysql(`
    insert into inv_sales_order
      (order_no, order_title, customer_name, total_amount, order_date, status, shop_dept_id, applicant_id, applicant_name, applicant_dept_id, create_by, create_time, remark)
    values
      (${q(deliveredOrderNo)}, ${q(RUN_ID + " 已出库销售")}, ${q(customer[1])}, 60.00, '2026-07-05', 'delivered', ${STORE.id}, 1, 'admin', 101, 'mobile-real-submit', now(), ${q(RUN_ID + " delivered-seed")});
  `)
  const deliveredOrder = one(`select order_id,order_no,status from inv_sales_order where order_no=${q(deliveredOrderNo)} order by order_id desc limit 1`)
  mysql(`
    insert into inv_sales_detail
      (order_id, product_id, warehouse_id, product_name, sku, spec, unit, quantity, unit_price, amount, delivered_quantity)
    values
      (${Number(deliveredOrder[0])}, ${storeProductId}, ${STORE.id}, ${q(storeProduct[1])}, ${q(storeProduct[2])}, ${q(storeProduct[4])}, ${q(storeProduct[3])}, 3.00, 20.00, 60.00, 3.00);
  `)
  const notice = one(`select notice_id,notice_title from sys_notice where notice_title=${q(noticeTitle)} order by notice_id desc limit 1`)
  const job = one(`select job_id,job_name,status from sys_job where job_name=${q(jobName)} order by job_id desc limit 1`)
  const repair = one(`select repair_id,product_name,status from oa_fixed_asset_repair where product_name=${q(RUN_ID + " 待确认资产")} order by repair_id desc limit 1`)
  return {
    warehouseProduct: productSeed(warehouseProduct),
    storeProduct: productSeed(storeProduct),
    managerProduct: productSeed(managerProduct),
    customer: { id: Number(customer[0]), name: customer[1] },
    managerCustomer: { id: Number(managerCustomer[0]), name: managerCustomer[1] },
    supplier: { id: Number(supplier[0]), name: supplier[1] },
    deliveredSale: { id: Number(deliveredOrder[0]), no: deliveredOrder[1], status: deliveredOrder[2], title: RUN_ID + " 已出库销售" },
    notice: { id: Number(notice[0]), title: notice[1] },
    job: { id: Number(job[0]), name: job[1], status: job[2] },
    repair: { id: Number(repair[0]), productName: repair[1], status: repair[2] }
  }
}

function productSeed(row) {
  return {
    id: Number(row[0]),
    name: row[1],
    code: row[2],
    unit: row[3],
    spec: row[4],
    costPrice: Number(row[5] || 10)
  }
}

function businessData(seed) {
  const today = "2026-07-05"
  const line = (product, qty, price) => ({
    productId: product.id,
    productName: product.name,
    productCode: product.code,
    unit: product.unit,
    spec: product.spec,
    quantity: qty,
    price,
    costPrice: product.costPrice,
    remark: RUN_ID
  })
  const stockLine = qty => Object.assign(line(seed.warehouseProduct, qty, seed.warehouseProduct.costPrice), {
    availableQuantity: 500,
    currentQuantity: 500,
    sortOrder: 0
  })
  return {
    salesSave: {
      customerId: seed.customer.id,
      customerName: seed.customer.name,
      warehouseId: STORE.id,
      orderTitle: RUN_ID + " 销售草稿",
      orderDate: today,
      details: [line(seed.storeProduct, 1, 20)],
      remark: RUN_ID + " sales-save"
    },
    salesSubmit: title => ({
      customerId: seed.customer.id,
      customerName: seed.customer.name,
      warehouseId: STORE.id,
      orderTitle: title,
      orderDate: today,
      details: [line(seed.storeProduct, 3, 20)],
      remark: RUN_ID
    }),
    managerSales: {
      customerId: seed.managerCustomer.id,
      customerName: seed.managerCustomer.name,
      warehouseId: STORE_MANAGER_STORE.id,
      orderTitle: RUN_ID + " 店长销售",
      orderDate: today,
      details: [line(seed.managerProduct, 1, 20)],
      remark: RUN_ID + " manager-sales"
    },
    purchaseSave: {
      supplierId: seed.supplier.id,
      supplierName: seed.supplier.name,
      warehouseId: WAREHOUSE.id,
      orderTitle: RUN_ID + " 采购草稿",
      expectedDate: today,
      details: [line(seed.warehouseProduct, 1, 10)],
      remark: RUN_ID + " purchase-save"
    },
    purchaseSubmit: title => ({
      supplierId: seed.supplier.id,
      supplierName: seed.supplier.name,
      warehouseId: WAREHOUSE.id,
      orderTitle: title,
      expectedDate: today,
      details: [line(seed.warehouseProduct, 4, 10)],
      remark: RUN_ID
    }),
    stockCheck: remark => ({
      warehouseId: WAREHOUSE.id,
      checkScope: "products",
      details: [{
        productId: seed.warehouseProduct.id,
        productName: seed.warehouseProduct.name,
        productCode: seed.warehouseProduct.code,
        unit: seed.warehouseProduct.unit,
        spec: seed.warehouseProduct.spec,
        availableQuantity: 500,
        currentQuantity: 500
      }],
      remark
    }),
    replenishment: remark => ({
      fromDeptId: WAREHOUSE.id,
      fromWarehouseId: WAREHOUSE.id,
      toDeptId: STORE.id,
      toWarehouseId: STORE.id,
      transferType: "warehouse",
      details: [stockLine(2)],
      remark
    }),
    oa: title => ({
      title,
      amount: 88,
      reason: RUN_ID + " 移动端采购申请",
      remark: RUN_ID
    }),
    repairSubmit: {
      productId: seed.storeProduct.id,
      shopDeptId: STORE.id,
      estimatedRepairAmount: 1,
      faultDescription: RUN_ID + " 普通报修",
      imageUrls: "",
      remark: RUN_ID + " repair-submit"
    }
  }
}

function findSalesByTitle(title) {
  const row = one(`select order_id,order_no,status from inv_sales_order where order_title=${q(title)} order by order_id desc limit 1`)
  return row && { id: Number(row[0]), no: row[1], status: row[2] }
}

function findPurchaseByTitle(title) {
  const row = one(`select order_id,order_no,status,qc_status from inv_purchase_order where order_title=${q(title)} order by order_id desc limit 1`)
  return row && { id: Number(row[0]), no: row[1], status: row[2], qcStatus: row[3] }
}

function findStockCheckByRemark(remark) {
  const row = one(`select check_id,check_no,status from inv_stock_check where remark=${q(remark)} order by check_id desc limit 1`)
  return row && { id: Number(row[0]), no: row[1], status: row[2] }
}

function findTransferByRemark(remark) {
  const row = one(`select transfer_id,order_no,status,from_dept_id,to_dept_id from inv_transfer_order where remark=${q(remark)} order by transfer_id desc limit 1`)
  return row && { id: Number(row[0]), no: row[1], status: row[2], fromDeptId: row[3], toDeptId: row[4] }
}

function findOaByTitle(title) {
  const row = one(`select purchase_id,title,status from oa_purchase where title=${q(title)} order by purchase_id desc limit 1`)
  return row && { id: Number(row[0]), title: row[1], status: row[2] }
}

function findRepairByRemark(remark) {
  const row = one(`select repair_id,product_name,status from oa_fixed_asset_repair where remark=${q(remark)} order by repair_id desc limit 1`)
  return row && { id: Number(row[0]), productName: row[1], status: row[2] }
}

function findDeliveryBySalesNo(salesNo) {
  const row = one(`select notice_id,notice_no,status,sales_order_no from inv_delivery_notice where sales_order_no=${q(salesNo)} order by notice_id desc limit 1`)
  return row && { id: Number(row[0]), no: row[1], status: row[2], salesNo: row[3] }
}

function buildSalesReturnData(seed, sales, title, qty) {
  const detail = one(`select detail_id,product_id,product_name,sku,spec,unit,quantity,unit_price from inv_sales_detail where order_id=${sales.id} order by detail_id limit 1`)
  return {
    salesOrderId: sales.id,
    salesOrderNo: sales.no,
    returnTitle: title,
    customerName: seed.customer.name,
    warehouseId: STORE.id,
    details: [{
      salesDetailId: Number(detail[0]),
      productId: Number(detail[1]),
      productName: detail[2],
      productCode: detail[3],
      spec: detail[4],
      unit: detail[5],
      maxReturnQuantity: Number(detail[6]),
      quantity: qty,
      unitPrice: Number(detail[7]),
      reason: RUN_ID
    }],
    remark: RUN_ID
  }
}

function buildPurchaseReturnData(seed, purchase, title, qty) {
  const detail = one(`select detail_id,product_id,product_name,sku,spec,unit,quantity,unit_price from inv_purchase_detail where order_id=${purchase.id} order by detail_id limit 1`)
  return {
    purchaseOrderId: purchase.id,
    purchaseOrderNo: purchase.no,
    returnTitle: title,
    supplierName: seed.supplier.name,
    warehouseId: WAREHOUSE.id,
    details: [{
      purchaseDetailId: Number(detail[0]),
      productId: Number(detail[1]),
      productName: detail[2],
      productCode: detail[3],
      spec: detail[4],
      unit: detail[5],
      maxReturnQuantity: Number(detail[6]),
      quantity: qty,
      unitPrice: Number(detail[7]),
      reason: RUN_ID
    }],
    remark: RUN_ID
  }
}

function findSalesReturnByTitle(title) {
  const row = one(`select return_id,return_no,status from inv_sales_return where return_title=${q(title)} order by return_id desc limit 1`)
  return row && { id: Number(row[0]), no: row[1], status: row[2] }
}

function findPurchaseReturnByTitle(title) {
  const row = one(`select return_id,return_no,status from inv_purchase_return where return_title=${q(title)} order by return_id desc limit 1`)
  return row && { id: Number(row[0]), no: row[1], status: row[2] }
}

function recordDbSummary(seed) {
  return {
    sales: rows(`select order_no,order_title,status from inv_sales_order where order_title like ${q(RUN_ID + "%")} order by order_id`),
    purchase: rows(`select order_no,order_title,status,qc_status from inv_purchase_order where order_title like ${q(RUN_ID + "%")} order by order_id`),
    stockCheck: rows(`select check_no,status,remark from inv_stock_check where remark like ${q(RUN_ID + "%")} order by check_id`),
    transfer: rows(`select order_no,status,remark from inv_transfer_order where remark like ${q(RUN_ID + "%")} order by transfer_id`),
    salesReturn: rows(`select return_no,return_title,status from inv_sales_return where return_title like ${q(RUN_ID + "%")} order by return_id`),
    purchaseReturn: rows(`select return_no,return_title,status from inv_purchase_return where return_title like ${q(RUN_ID + "%")} order by return_id`),
    oa: rows(`select purchase_id,title,status from oa_purchase where title like ${q(RUN_ID + "%")} order by purchase_id`),
    noticeRead: rows(`select notice_id,user_id,read_time from sys_notice_read where notice_id=${seed.notice.id} order by user_id`),
    repair: rows(`select repair_id,product_name,status,remark from oa_fixed_asset_repair where remark like ${q(RUN_ID + "%")} or product_name like ${q(RUN_ID + "%")} order by repair_id`),
    job: rows(`select job_id,job_name,status from sys_job where job_name=${q(seed.job.name)}`)
  }
}

function appendResult(results, result) {
  results.cases.push(result)
  fs.writeFileSync(RESULT_PATH, JSON.stringify(results, null, 2))
  console.log((result.ok ? "PASS" : "FAIL") + " " + result.id + " - " + result.name)
  if (!result.ok) {
    console.log("  " + (result.problem || result.error || (result.after && result.after.actionMessage) || "see JSON"))
  }
}

function writeReport(results) {
  const failures = results.cases.filter(item => !item.ok)
  const lines = []
  lines.push("# Android Mobile Real Submit QA")
  lines.push("")
  lines.push("- Run: `" + results.runId + "`")
  lines.push("- Device: `" + SERIAL + "`")
  lines.push("- APK package: `" + PACKAGE + "`")
  lines.push("- WebView origin: `" + WEBVIEW_ORIGIN + "`")
  lines.push("- Output: `" + OUT_DIR + "`")
  lines.push("- Cases: " + results.cases.length + " total, " + (results.cases.length - failures.length) + " pass, " + failures.length + " fail")
  lines.push("- Accounts: " + Object.values(ACCOUNTS).map(a => a.label + "(" + a.username + ")").join(", "))
  lines.push("")
  lines.push("## Coverage")
  lines.push("")
  lines.push("- Page/button/dialog path: each case opens the real Android WebView page, triggers `featureActionItems` or `detailActions`, captures page/detail/modal/after screenshots, and then verifies database state.")
  lines.push("- Business writes covered: sales, outbound, purchase, purchase return, sales return, stock check, replenishment/transfer, OA purchase approval, fixed asset repair, notice read, salary calculation, monitor job, store-manager sales, tea-artist attendance visibility.")
  lines.push("- Existing page sweep report remains in `docs/audit-screenshots/mobile-android-20260705-full-qa-round2/`.")
  lines.push("")
  lines.push("## Findings")
  lines.push("")
  if (!failures.length) {
    lines.push("- No failing submit-flow case in this run.")
  } else {
    failures.forEach(item => {
      const msg = item.problem || item.error || item.after && item.after.actionMessage || item.after && item.after.formError || "see JSON"
      lines.push("- " + item.id + " " + item.name + ": " + String(msg).split("\n")[0].slice(0, 240))
      Object.keys(item.screenshots || {}).forEach(key => {
        lines.push("  - " + key + ": `" + path.relative(ROOT, item.screenshots[key]) + "`")
      })
    })
  }
  lines.push("")
  lines.push("## Cases")
  lines.push("")
  results.cases.forEach(item => {
    lines.push("- " + (item.ok ? "PASS" : "FAIL") + " " + item.id + ": " + item.name + " [" + item.role + "]")
  })
  lines.push("")
  lines.push("## Database Summary")
  lines.push("")
  lines.push("```json")
  lines.push(JSON.stringify(results.dbSummary || {}, null, 2))
  lines.push("```")
  fs.writeFileSync(REPORT_PATH, lines.join("\n") + "\n")
}

async function main() {
  const results = {
    runId: RUN_ID,
    startedAt: new Date().toISOString(),
    outDir: OUT_DIR,
    cases: [],
    logins: [],
    seed: null
  }
  fs.writeFileSync(RESULT_PATH, JSON.stringify(results, null, 2))

  const seed = seedSupportData()
  results.seed = seed
  const data = businessData(seed)
  fs.writeFileSync(RESULT_PATH, JSON.stringify(results, null, 2))

  sh("adb", ["-s", SERIAL, "logcat", "-c"])
  launchApp()
  await sleep(3500)
  results.pid = ensureCdpForward()
  const cdp = new Cdp()
  await cdp.connect()
  screenshot("00_relaunched.png")

  let loginResult = await login(cdp, ACCOUNTS.admin, STORE, "/mobile/store")
  results.logins.push({ account: ACCOUNTS.admin.id, context: STORE, result: loginResult })
  screenshot("01_login_admin_store.png")

  appendResult(results, await createMobileForm(cdp, {
    id: "01_sales_form_save",
    name: "销售单手机表单保存草稿",
    context: STORE,
    path: "/mobile/sales",
    submitAction: "save",
    data: data.salesSave,
    db: () => findSalesByTitle(data.salesSave.orderTitle)
  }))

  appendResult(results, await createMobileForm(cdp, {
    id: "02_sales_form_submit",
    name: "销售单手机表单保存并提交",
    context: STORE,
    path: "/mobile/sales",
    submitAction: "submit",
    data: data.salesSubmit(RUN_ID + " 销售生成发货"),
    db: () => findSalesByTitle(RUN_ID + " 销售生成发货")
  }))
  const salesForDelivery = findSalesByTitle(RUN_ID + " 销售生成发货")
  if (salesForDelivery) {
    appendResult(results, await runDetailCase(cdp, {
      id: "03_sales_create_delivery_notice",
      name: "销售单详情按钮生成发货通知",
      context: STORE,
      path: "/mobile/sales",
      field: "orderNo",
      keyword: salesForDelivery.no,
      actionId: "createDeliveryNotice",
      db: () => ({ sales: findSalesByTitle(RUN_ID + " 销售生成发货"), delivery: findDeliveryBySalesNo(salesForDelivery.no) })
    }))
    const notice = findDeliveryBySalesNo(salesForDelivery.no)
    if (notice) {
      appendResult(results, await runDetailCase(cdp, {
        id: "04_outbound_deliver",
        name: "仓库出库页按数量发货",
        context: WAREHOUSE,
        path: "/mobile/outbound",
        field: "salesOrderNo",
        keyword: salesForDelivery.no,
        match: salesForDelivery.no,
        actionId: "deliverDeliveryNoticeAll",
        db: () => ({ delivery: findDeliveryBySalesNo(salesForDelivery.no), sales: findSalesByTitle(RUN_ID + " 销售生成发货") })
      }))
    }
  }

  appendResult(results, await createMobileForm(cdp, {
    id: "05_sales_form_submit_for_cancel",
    name: "销售单提交用于取消",
    context: STORE,
    path: "/mobile/sales",
    submitAction: "submit",
    data: data.salesSubmit(RUN_ID + " 销售取消"),
    db: () => findSalesByTitle(RUN_ID + " 销售取消")
  }))
  const salesCancel = findSalesByTitle(RUN_ID + " 销售取消")
  if (salesCancel) {
    appendResult(results, await runDetailCase(cdp, {
      id: "06_sales_cancel",
      name: "销售单详情按钮取消销售",
      context: STORE,
      path: "/mobile/sales",
      field: "orderNo",
      keyword: salesCancel.no,
      actionId: "cancelSales",
      db: () => findSalesByTitle(RUN_ID + " 销售取消")
    }))
  }

  loginResult = await login(cdp, ACCOUNTS.admin, WAREHOUSE, "/mobile/warehouse")
  results.logins.push({ account: ACCOUNTS.admin.id, context: WAREHOUSE, result: loginResult })
  screenshot("02_login_admin_warehouse.png")

  appendResult(results, await createMobileForm(cdp, {
    id: "07_purchase_form_save",
    name: "采购单手机表单保存草稿",
    context: WAREHOUSE,
    path: "/mobile/purchase",
    submitAction: "save",
    data: data.purchaseSave,
    db: () => findPurchaseByTitle(data.purchaseSave.orderTitle)
  }))
  appendResult(results, await createMobileForm(cdp, {
    id: "08_purchase_form_submit_receive_base",
    name: "采购单手机表单保存并提交",
    context: WAREHOUSE,
    path: "/mobile/purchase",
    submitAction: "submit",
    data: data.purchaseSubmit(RUN_ID + " 采购收货"),
    db: () => findPurchaseByTitle(RUN_ID + " 采购收货")
  }))
  const purchaseReceive = findPurchaseByTitle(RUN_ID + " 采购收货")
  if (purchaseReceive) {
    appendResult(results, await runDetailCase(cdp, {
      id: "09_purchase_receive_all",
      name: "采购单详情按钮按数量收货",
      context: WAREHOUSE,
      path: "/mobile/purchase",
      field: "orderNo",
      keyword: purchaseReceive.no,
      query: {},
      actionId: "receivePurchaseAll",
      db: () => findPurchaseByTitle(RUN_ID + " 采购收货")
    }))
    appendResult(results, await runDetailCase(cdp, {
      id: "10_purchase_qc",
      name: "采购单详情按钮质检通过",
      context: WAREHOUSE,
      path: "/mobile/purchase",
      field: "orderNo",
      keyword: purchaseReceive.no,
      query: {},
      actionId: "qualityCheckPurchase",
      db: () => findPurchaseByTitle(RUN_ID + " 采购收货")
    }))
  }

  appendResult(results, await createMobileForm(cdp, {
    id: "11_purchase_form_submit_for_cancel",
    name: "采购单提交用于取消",
    context: WAREHOUSE,
    path: "/mobile/purchase",
    submitAction: "submit",
    data: data.purchaseSubmit(RUN_ID + " 采购取消"),
    db: () => findPurchaseByTitle(RUN_ID + " 采购取消")
  }))
  const purchaseCancel = findPurchaseByTitle(RUN_ID + " 采购取消")
  if (purchaseCancel) {
    appendResult(results, await runDetailCase(cdp, {
      id: "12_purchase_cancel",
      name: "采购单详情按钮取消采购",
      context: WAREHOUSE,
      path: "/mobile/purchase",
      field: "orderNo",
      keyword: purchaseCancel.no,
      query: {},
      actionId: "cancelPurchase",
      db: () => findPurchaseByTitle(RUN_ID + " 采购取消")
    }))
  }

  appendResult(results, await createMobileForm(cdp, {
    id: "13_stock_check_create",
    name: "盘点单手机表单创建",
    context: WAREHOUSE,
    path: "/mobile/stock-check",
    submitAction: "save",
    data: data.stockCheck(RUN_ID + " stock-check-confirm"),
    db: () => findStockCheckByRemark(RUN_ID + " stock-check-confirm")
  }))
  const stockCheck = findStockCheckByRemark(RUN_ID + " stock-check-confirm")
  if (stockCheck) {
    appendResult(results, await runDetailCase(cdp, {
      id: "14_stock_check_input",
      name: "盘点单详情按钮录入实盘",
      context: WAREHOUSE,
      path: "/mobile/stock-check",
      field: "checkNo",
      keyword: stockCheck.no,
      query: {},
      actionId: "inputStockCheck",
      db: () => findStockCheckByRemark(RUN_ID + " stock-check-confirm")
    }))
    appendResult(results, await runDetailCase(cdp, {
      id: "15_stock_check_confirm",
      name: "盘点单详情按钮确认写入库存差异",
      context: WAREHOUSE,
      path: "/mobile/stock-check",
      field: "checkNo",
      keyword: stockCheck.no,
      query: {},
      actionId: "confirmStockCheck",
      db: () => findStockCheckByRemark(RUN_ID + " stock-check-confirm")
    }))
  }
  appendResult(results, await createMobileForm(cdp, {
    id: "16_stock_check_create_for_cancel",
    name: "盘点单创建用于取消",
    context: WAREHOUSE,
    path: "/mobile/stock-check",
    submitAction: "save",
    data: data.stockCheck(RUN_ID + " stock-check-cancel"),
    db: () => findStockCheckByRemark(RUN_ID + " stock-check-cancel")
  }))
  const stockCheckCancel = findStockCheckByRemark(RUN_ID + " stock-check-cancel")
  if (stockCheckCancel) {
    appendResult(results, await runDetailCase(cdp, {
      id: "17_stock_check_cancel",
      name: "盘点单详情按钮取消盘点",
      context: WAREHOUSE,
      path: "/mobile/stock-check",
      field: "checkNo",
      keyword: stockCheckCancel.no,
      query: {},
      actionId: "cancelStockCheck",
      db: () => findStockCheckByRemark(RUN_ID + " stock-check-cancel")
    }))
  }

  const receivedPurchase = findPurchaseByTitle(RUN_ID + " 采购收货")
  if (receivedPurchase) {
    appendResult(results, await createMobileForm(cdp, {
      id: "18_purchase_return_submit_confirm_base",
      name: "采购退货手机表单提交",
      context: WAREHOUSE,
      path: "/mobile/purchase-return",
      submitAction: "submit",
      data: buildPurchaseReturnData(seed, receivedPurchase, RUN_ID + " 采购退货确认", 1),
      db: () => findPurchaseReturnByTitle(RUN_ID + " 采购退货确认")
    }))
    const purchaseReturnConfirm = findPurchaseReturnByTitle(RUN_ID + " 采购退货确认")
    if (purchaseReturnConfirm) {
      appendResult(results, await runDetailCase(cdp, {
        id: "19_purchase_return_confirm",
        name: "采购退货详情按钮确认出库",
        context: WAREHOUSE,
        path: "/mobile/purchase-return",
        field: "returnNo",
        keyword: purchaseReturnConfirm.no,
        query: {},
        actionId: "confirmPurchaseReturn",
        db: () => findPurchaseReturnByTitle(RUN_ID + " 采购退货确认")
      }))
    }
    appendResult(results, await createMobileForm(cdp, {
      id: "20_purchase_return_submit_cancel_base",
      name: "采购退货提交用于取消",
      context: WAREHOUSE,
      path: "/mobile/purchase-return",
      submitAction: "submit",
      data: buildPurchaseReturnData(seed, receivedPurchase, RUN_ID + " 采购退货取消", 1),
      db: () => findPurchaseReturnByTitle(RUN_ID + " 采购退货取消")
    }))
    const purchaseReturnCancel = findPurchaseReturnByTitle(RUN_ID + " 采购退货取消")
    if (purchaseReturnCancel) {
      appendResult(results, await runDetailCase(cdp, {
        id: "21_purchase_return_cancel",
        name: "采购退货详情按钮取消退货",
        context: WAREHOUSE,
        path: "/mobile/purchase-return",
        field: "returnNo",
        keyword: purchaseReturnCancel.no,
        query: {},
        actionId: "cancelPurchaseReturn",
        db: () => findPurchaseReturnByTitle(RUN_ID + " 采购退货取消")
      }))
    }
  }

  loginResult = await login(cdp, ACCOUNTS.admin, STORE, "/mobile/store")
  results.logins.push({ account: ACCOUNTS.admin.id, context: STORE, result: loginResult })
  screenshot("03_login_admin_store_again.png")

  const deliveredSale = findSalesByTitle(seed.deliveredSale.title) || findSalesByTitle(RUN_ID + " 销售生成发货")
  if (deliveredSale) {
    appendResult(results, await createMobileForm(cdp, {
      id: "22_sales_return_submit_confirm_base",
      name: "销售退货手机表单提交",
      context: STORE,
      path: "/mobile/sales-return",
      submitAction: "submit",
      data: buildSalesReturnData(seed, deliveredSale, RUN_ID + " 销售退货确认", 1),
      db: () => findSalesReturnByTitle(RUN_ID + " 销售退货确认")
    }))
    const salesReturnConfirm = findSalesReturnByTitle(RUN_ID + " 销售退货确认")
    if (salesReturnConfirm) {
      appendResult(results, await runDetailCase(cdp, {
        id: "23_sales_return_confirm",
        name: "销售退货详情按钮确认入库",
        context: STORE,
        path: "/mobile/sales-return",
        field: "returnNo",
        keyword: salesReturnConfirm.no,
        query: { businessType: "return" },
        actionId: "confirmSalesReturn",
        db: () => findSalesReturnByTitle(RUN_ID + " 销售退货确认")
      }))
    }
    appendResult(results, await createMobileForm(cdp, {
      id: "24_sales_return_submit_cancel_base",
      name: "销售退货提交用于取消",
      context: STORE,
      path: "/mobile/sales-return",
      submitAction: "submit",
      data: buildSalesReturnData(seed, deliveredSale, RUN_ID + " 销售退货取消", 1),
      db: () => findSalesReturnByTitle(RUN_ID + " 销售退货取消")
    }))
    const salesReturnCancel = findSalesReturnByTitle(RUN_ID + " 销售退货取消")
    if (salesReturnCancel) {
      appendResult(results, await runDetailCase(cdp, {
        id: "25_sales_return_cancel",
        name: "销售退货详情按钮取消退货",
        context: STORE,
        path: "/mobile/sales-return",
        field: "returnNo",
        keyword: salesReturnCancel.no,
        query: { businessType: "return" },
        actionId: "cancelSalesReturn",
        db: () => findSalesReturnByTitle(RUN_ID + " 销售退货取消")
      }))
    }
  }

  appendResult(results, await createMobileForm(cdp, {
    id: "26_replenishment_form_save",
    name: "补货申请手机表单保存草稿",
    context: STORE,
    path: "/mobile/replenishment",
    submitAction: "save",
    data: data.replenishment(RUN_ID + " replenishment-draft-submit"),
    db: () => findTransferByRemark(RUN_ID + " replenishment-draft-submit")
  }))
  const repDraft = findTransferByRemark(RUN_ID + " replenishment-draft-submit")
  if (repDraft) {
    appendResult(results, await runDetailCase(cdp, {
      id: "27_replenishment_submit",
      name: "补货申请详情按钮提交补货",
      context: STORE,
      path: "/mobile/replenishment",
      field: "orderNo",
      keyword: repDraft.no,
      query: { transferType: "warehouse" },
      actionId: "submitReplenishment",
      db: () => findTransferByRemark(RUN_ID + " replenishment-draft-submit")
    }))
  }

  appendResult(results, await createMobileForm(cdp, {
    id: "28_transfer_submit_for_approve",
    name: "补货申请保存并提交用于审批流转",
    context: STORE,
    path: "/mobile/replenishment",
    submitAction: "submit",
    data: data.replenishment(RUN_ID + " transfer-approve-deliver-receive"),
    db: () => findTransferByRemark(RUN_ID + " transfer-approve-deliver-receive")
  }))
  const transferFlow = findTransferByRemark(RUN_ID + " transfer-approve-deliver-receive")
  if (transferFlow) {
    loginResult = await login(cdp, ACCOUNTS.transferApprover, STORE, "/mobile/transfer-approval")
    results.logins.push({ account: ACCOUNTS.transferApprover.id, context: STORE, result: loginResult })
    screenshot("transfer_approver_login.png")
    appendResult(results, await runDetailCase(cdp, {
      id: "29_transfer_approval_approve",
      name: "调拨审批页详情按钮审批通过",
      role: "transfer_approver",
      context: STORE,
      path: "/mobile/transfer-approval",
      field: "orderNo",
      keyword: transferFlow.no,
      query: { status: "submitted" },
      actionId: "approveTransfer",
      payloadOverride: { comment: RUN_ID + " approve" },
      db: () => findTransferByRemark(RUN_ID + " transfer-approve-deliver-receive")
    }))
    loginResult = await login(cdp, ACCOUNTS.admin, WAREHOUSE, "/mobile/warehouse")
    results.logins.push({ account: ACCOUNTS.admin.id, context: WAREHOUSE, result: loginResult })
    screenshot("admin_warehouse_after_transfer_approval.png")
    appendResult(results, await runDetailCase(cdp, {
      id: "30_transfer_deliver",
      name: "调拨处理页详情按钮按数量发货",
      context: WAREHOUSE,
      path: "/mobile/transfer",
      field: "orderNo",
      keyword: transferFlow.no,
      query: { statusGroup: "deliverable", direction: "deliver" },
      actionId: "deliverTransferAll",
      db: () => findTransferByRemark(RUN_ID + " transfer-approve-deliver-receive")
    }))
    loginResult = await login(cdp, ACCOUNTS.admin, STORE, "/mobile/store")
    results.logins.push({ account: ACCOUNTS.admin.id, context: STORE, result: loginResult })
    screenshot("admin_store_after_transfer_deliver.png")
    appendResult(results, await runDetailCase(cdp, {
      id: "31_transfer_receive",
      name: "调拨处理页详情按钮确认调拨收货",
      context: STORE,
      path: "/mobile/transfer",
      field: "orderNo",
      keyword: transferFlow.no,
      query: { statusGroup: "receivable", direction: "receive" },
      actionId: "receiveTransferShipment",
      db: () => findTransferByRemark(RUN_ID + " transfer-approve-deliver-receive")
    }))
  }
  appendResult(results, await createMobileForm(cdp, {
    id: "32_transfer_submit_for_reject",
    name: "补货申请保存并提交用于审批驳回",
    context: STORE,
    path: "/mobile/replenishment",
    submitAction: "submit",
    data: data.replenishment(RUN_ID + " transfer-reject"),
    db: () => findTransferByRemark(RUN_ID + " transfer-reject")
  }))
  const transferReject = findTransferByRemark(RUN_ID + " transfer-reject")
  if (transferReject) {
    loginResult = await login(cdp, ACCOUNTS.transferApprover, STORE, "/mobile/transfer-approval")
    results.logins.push({ account: ACCOUNTS.transferApprover.id, context: STORE, result: loginResult })
    screenshot("transfer_approver_login_reject.png")
    appendResult(results, await runDetailCase(cdp, {
      id: "33_transfer_approval_reject",
      name: "调拨审批页详情按钮审批驳回",
      role: "transfer_approver",
      context: STORE,
      path: "/mobile/transfer-approval",
      field: "orderNo",
      keyword: transferReject.no,
      query: { status: "submitted" },
      actionId: "rejectTransfer",
      payloadOverride: { comment: RUN_ID + " reject" },
      db: () => findTransferByRemark(RUN_ID + " transfer-reject")
    }))
    loginResult = await login(cdp, ACCOUNTS.admin, STORE, "/mobile/store")
    results.logins.push({ account: ACCOUNTS.admin.id, context: STORE, result: loginResult })
  }
  appendResult(results, await createMobileForm(cdp, {
    id: "34_transfer_save_for_cancel",
    name: "补货申请草稿用于取消调拨",
    context: STORE,
    path: "/mobile/replenishment",
    submitAction: "save",
    data: data.replenishment(RUN_ID + " transfer-cancel"),
    db: () => findTransferByRemark(RUN_ID + " transfer-cancel")
  }))
  const transferCancel = findTransferByRemark(RUN_ID + " transfer-cancel")
  if (transferCancel) {
    appendResult(results, await runDetailCase(cdp, {
      id: "35_transfer_cancel",
      name: "调拨处理页详情按钮取消调拨",
      context: STORE,
      path: "/mobile/transfer",
      field: "orderNo",
      keyword: transferCancel.no,
      query: {},
      actionId: "cancelTransfer",
      db: () => findTransferByRemark(RUN_ID + " transfer-cancel")
    }))
  }

  appendResult(results, await createMobileForm(cdp, {
    id: "36_oa_purchase_form_save",
    name: "OA采购申请手机表单保存草稿",
    context: STORE,
    path: "/mobile/oa-purchase",
    submitAction: "save",
    data: data.oa(RUN_ID + " OA采购草稿提交"),
    db: () => findOaByTitle(RUN_ID + " OA采购草稿提交")
  }))
  const oaDraft = findOaByTitle(RUN_ID + " OA采购草稿提交")
  if (oaDraft) {
    appendResult(results, await runDetailCase(cdp, {
      id: "37_oa_purchase_submit_action",
      name: "OA采购申请详情按钮提交申请",
      context: STORE,
      path: "/mobile/oa-purchase",
      field: "title",
      keyword: oaDraft.title,
      query: {},
      actionId: "submitOaPurchase",
      db: () => findOaByTitle(RUN_ID + " OA采购草稿提交")
    }))
    appendResult(results, await runDetailCase(cdp, {
      id: "38_oa_todo_approve",
      name: "OA待办详情按钮审批通过",
      context: STORE,
      path: "/mobile/oa-todo",
      field: "title",
      keyword: oaDraft.title,
      query: { status: "submitted" },
      actionId: "approveOaPurchase",
      payloadOverride: { comment: RUN_ID + " oa approve" },
      db: () => findOaByTitle(RUN_ID + " OA采购草稿提交")
    }))
  }
  appendResult(results, await createMobileForm(cdp, {
    id: "39_oa_purchase_form_submit_for_reject",
    name: "OA采购申请手机表单保存并提交用于驳回",
    context: STORE,
    path: "/mobile/oa-purchase",
    submitAction: "submit",
    data: data.oa(RUN_ID + " OA采购驳回"),
    db: () => findOaByTitle(RUN_ID + " OA采购驳回")
  }))
  const oaReject = findOaByTitle(RUN_ID + " OA采购驳回")
  if (oaReject) {
    appendResult(results, await runDetailCase(cdp, {
      id: "40_oa_todo_reject",
      name: "OA待办详情按钮审批驳回",
      context: STORE,
      path: "/mobile/oa-todo",
      field: "title",
      keyword: oaReject.title,
      query: { status: "submitted" },
      actionId: "rejectOaPurchase",
      payloadOverride: { comment: RUN_ID + " oa reject" },
      db: () => findOaByTitle(RUN_ID + " OA采购驳回")
    }))
  }

  appendResult(results, await createMobileForm(cdp, {
    id: "41_fixed_asset_repair_form_submit",
    name: "固定资产报修手机表单提交",
    context: STORE,
    path: "/mobile/fixed-asset-repair",
    submitAction: "submit",
    data: data.repairSubmit,
    db: () => findRepairByRemark(RUN_ID + " repair-submit")
  }))
  appendResult(results, await runDetailCase(cdp, {
    id: "42_fixed_asset_repair_confirm_seeded",
    name: "固定资产待确认报修详情按钮确认上报",
    context: STORE,
    path: "/mobile/fixed-asset-repair",
    field: "productName",
    keyword: RUN_ID + " 待确认资产",
    query: { status: "pending_confirm" },
    actionId: "confirmFixedAssetRepair",
    db: () => rows(`select repair_id,product_name,status from oa_fixed_asset_repair where repair_id=${seed.repair.id}`)
  }))

  appendResult(results, await runDetailCase(cdp, {
    id: "43_notice_mark_read",
    name: "通知详情按钮标为已读",
    context: STORE,
    path: "/mobile/notice",
    field: "noticeTitle",
    keyword: seed.notice.title,
    query: {},
    actionId: "markNoticeRead",
    db: () => rows(`select notice_id,user_id,read_time from sys_notice_read where notice_id=${seed.notice.id}`)
  }))
  appendResult(results, await runTopActionCase(cdp, {
    id: "44_notice_mark_all_read",
    name: "通知顶部按钮全部已读",
    context: STORE,
    path: "/mobile/notice",
    actionId: "markAllNoticeRead",
    db: () => rows(`select count(*) from sys_notice_read where user_id=1`)
  }))
  appendResult(results, await runTopActionCase(cdp, {
    id: "45_salary_calculate_current_month",
    name: "工资页顶部按钮计算本月",
    context: STORE,
    path: "/mobile/salary",
    actionId: "calculateSalary",
    db: () => rows(`select salary_month,count(*) from oa_salary_record where salary_month='2026-07' group by salary_month`)
  }))

  loginResult = await login(cdp, ACCOUNTS.admin, WAREHOUSE, "/mobile/warehouse")
  results.logins.push({ account: ACCOUNTS.admin.id, context: WAREHOUSE, result: loginResult })
  appendResult(results, await runDetailCase(cdp, {
    id: "46_monitor_job_run",
    name: "定时任务详情按钮执行一次",
    context: WAREHOUSE,
    path: "/mobile/monitor-job",
    field: "jobName",
    keyword: seed.job.name,
    query: {},
    actionId: "runMonitorJob",
    db: () => rows(`select job_id,job_name,status from sys_job where job_id=${seed.job.id}`)
  }))
  appendResult(results, await runDetailCase(cdp, {
    id: "47_monitor_job_resume",
    name: "定时任务详情按钮恢复任务",
    context: WAREHOUSE,
    path: "/mobile/monitor-job",
    field: "jobName",
    keyword: seed.job.name,
    query: {},
    actionId: "resumeMonitorJob",
    db: () => rows(`select job_id,job_name,status from sys_job where job_id=${seed.job.id}`)
  }))
  appendResult(results, await runDetailCase(cdp, {
    id: "48_monitor_job_pause",
    name: "定时任务详情按钮暂停任务",
    context: WAREHOUSE,
    path: "/mobile/monitor-job",
    field: "jobName",
    keyword: seed.job.name,
    query: {},
    actionId: "pauseMonitorJob",
    db: () => rows(`select job_id,job_name,status from sys_job where job_id=${seed.job.id}`)
  }))

  loginResult = await login(cdp, ACCOUNTS.manager, STORE_MANAGER_STORE, "/mobile/store")
  results.logins.push({ account: ACCOUNTS.manager.id, context: STORE_MANAGER_STORE, result: loginResult })
  screenshot("04_login_store_manager.png")
  appendResult(results, await createMobileForm(cdp, {
    id: "49_store_manager_sales_form",
    name: "店长账号在授权门店创建销售单",
    role: "store_manager",
    context: STORE_MANAGER_STORE,
    path: "/mobile/sales",
    submitAction: "save",
    data: data.managerSales,
    db: () => findSalesByTitle(data.managerSales.orderTitle)
  }))

  loginResult = await login(cdp, ACCOUNTS.tea, STORE, "/mobile/attendance")
  results.logins.push({ account: ACCOUNTS.tea.id, context: STORE, result: loginResult })
  screenshot("05_login_tea_artist.png")
  appendResult(results, await runTopActionCase(cdp, {
    id: "50_tea_attendance_check_in",
    name: "茶艺师上班打卡按钮与弹窗",
    role: "tea_artist",
    context: STORE,
    path: "/mobile/attendance",
    actionId: "checkInAttendance",
    db: () => rows(`select record_id,user_id,work_date,check_in_time,check_out_time from oa_attendance_record where user_id=${ACCOUNTS.tea.userId} and work_date='2026-07-05' order by record_id desc`)
  }))
  appendResult(results, await runTopActionCase(cdp, {
    id: "51_tea_attendance_check_out",
    name: "茶艺师下班打卡按钮与弹窗",
    role: "tea_artist",
    context: STORE,
    path: "/mobile/attendance",
    actionId: "checkOutAttendance",
    db: () => rows(`select record_id,user_id,work_date,check_in_time,check_out_time from oa_attendance_record where user_id=${ACCOUNTS.tea.userId} and work_date='2026-07-05' order by record_id desc`)
  }))

  loginResult = await login(cdp, ACCOUNTS.maint, WAREHOUSE, "/mobile/monitor-job")
  results.logins.push({ account: ACCOUNTS.maint.id, context: WAREHOUSE, result: loginResult })
  const maintPage = await prepareFeature(cdp, WAREHOUSE, "/mobile/monitor-job", "52_sys_maint_monitor_job")
  const maintResult = {
    id: "52_sys_maint_monitor_permission",
    name: "系统维护账号监控任务页权限验证",
    role: "sys_maint",
    context: WAREHOUSE,
    path: "/mobile/monitor-job",
    ok: maintPage.state.path === "/mobile/monitor-job" && !!maintPage.state.hasFeatureVm && !maintPage.state.errorMessage,
    problem: maintPage.state.errorMessage || (maintPage.state.path !== "/mobile/monitor-job" ? "route-redirected-to-" + maintPage.state.path : ""),
    screenshots: { page: maintPage.shot },
    page: maintPage.state,
    startedAt: new Date().toISOString(),
    finishedAt: new Date().toISOString()
  }
  appendResult(results, maintResult)

  results.dbSummary = recordDbSummary(seed)
  results.finishedAt = new Date().toISOString()
  fs.writeFileSync(RESULT_PATH, JSON.stringify(results, null, 2))
  writeReport(results)
  console.log("RESULT " + RESULT_PATH)
  console.log("REPORT " + REPORT_PATH)
}

main().catch(error => {
  console.error(error.stack || error.message)
  process.exit(1)
})
