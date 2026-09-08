#!/usr/bin/env node

const fs = require("fs")
const path = require("path")
const http = require("http")
const { execFileSync } = require("child_process")
const WebSocket = require("../erp-ui/node_modules/ws")

const ROOT = path.resolve(__dirname, "..")
const OUT_DIR = path.join(ROOT, "docs/audit-screenshots/mobile-android-20260704-real-flow")
const RESULT_PATH = path.join(OUT_DIR, "real-flow-results.json")
const REPORT_PATH = path.join(OUT_DIR, "real-flow-report.md")
const QA = "QA-MOBILE-20260704-223708"
const HOST = "http://192.168.182.145:1025"
const CDP_JSON = "http://127.0.0.1:9222/json"
const SERIAL = "emulator-5554"

fs.mkdirSync(OUT_DIR, { recursive: true })

function sleep(ms) {
  return new Promise(resolve => setTimeout(resolve, ms))
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
          reject(new Error("Invalid JSON from " + url + ": " + error.message))
        }
      })
    }).on("error", reject)
  })
}

function sh(command, args, options = {}) {
  return execFileSync(command, args, Object.assign({
    cwd: ROOT,
    encoding: "utf8",
    stdio: ["ignore", "pipe", "pipe"]
  }, options))
}

function mysql(sql) {
  return sh("mysql", ["-uroot", "BossERP_NEW", "-N", "-B", "-e", sql]).trim()
}

function screenshot(name) {
  const file = path.join(OUT_DIR, name.endsWith(".png") ? name : name + ".png")
  const png = execFileSync("adb", ["-s", SERIAL, "exec-out", "screencap", "-p"], {
    cwd: ROOT,
    encoding: "buffer",
    maxBuffer: 20 * 1024 * 1024
  })
  fs.writeFileSync(file, png)
  return file
}

class Cdp {
  constructor() {
    this.ws = null
    this.nextId = 1
    this.pending = new Map()
  }

  async connect() {
    const pages = await httpJson(CDP_JSON)
    const page = pages.find(item => item.type === "page") || pages[0]
    if (!page || !page.webSocketDebuggerUrl) {
      throw new Error("No WebView CDP page is available")
    }
    this.ws = new WebSocket(page.webSocketDebuggerUrl)
    this.ws.on("message", message => {
      const packet = JSON.parse(message)
      if (packet.id && this.pending.has(packet.id)) {
        const entry = this.pending.get(packet.id)
        clearTimeout(entry.timer)
        this.pending.delete(packet.id)
        entry.resolve(packet)
      }
    })
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
  }

  async reconnect() {
    try {
      if (this.ws) this.ws.close()
    } catch (_) {}
    this.ws = null
    this.pending.clear()
    await this.connect()
  }

  send(method, params = {}, timeoutMs = 30000) {
    return new Promise((resolve, reject) => {
      if (!this.ws || this.ws.readyState !== WebSocket.OPEN) {
        reject(new Error("CDP websocket is not open"))
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
      }, timeoutMs + 5000).catch(async error => {
        if (/closed|not open/.test(error.message) && attempt < 2) {
          await this.reconnect()
          return null
        }
        throw error
      })
      if (!packet) continue
      if (packet.error) {
        throw new Error(packet.error.message || JSON.stringify(packet.error))
      }
      if (packet.result && packet.result.exceptionDetails) {
        const detail = packet.result.exceptionDetails
        const text = detail.exception && detail.exception.description
          ? detail.exception.description
          : detail.text
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

  async navigate(pathname) {
    const url = HOST + pathname
    await this.send("Page.navigate", { url }, 10000)
    await sleep(1800)
    await this.waitFor(`location.pathname === ${JSON.stringify(pathname.split("?")[0])}`, 20000)
    await sleep(1200)
  }

  async waitFor(predicateExpression, timeoutMs = 30000, intervalMs = 500) {
    const started = Date.now()
    let last
    while (Date.now() - started < timeoutMs) {
      try {
        last = await this.eval(`(() => { try { return !!(${predicateExpression}); } catch (e) { return false; } })()`, 10000)
        if (last) return true
      } catch (error) {
        last = error.message
      }
      await sleep(intervalMs)
    }
    throw new Error("Timed out waiting for " + predicateExpression + " (last=" + last + ")")
  }
}

const findVmSource = `
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
    ids: {
      orderId: raw.orderId,
      noticeId: raw.noticeId,
      returnId: raw.returnId,
      checkId: raw.checkId,
      transferId: raw.transferId,
      purchaseId: raw.purchaseId,
      repairId: raw.repairId,
      noticeId2: raw.noticeId,
      jobId: raw.jobId,
      recordId: raw.recordId,
      salaryId: raw.salaryId
    },
    rawStatus: raw.status,
    rawNo: raw.orderNo || raw.noticeNo || raw.returnNo || raw.checkNo || raw.title || raw.jobName
  }
}
`

function js(value) {
  return JSON.stringify(value)
}

async function getState(cdp) {
  return cdp.eval(`(() => { ${findVmSource}
    const vm = __qaFindVmByName('MobileFeaturePage')
    return {
      url: location.href,
      path: location.pathname,
      bodyText: document.body.innerText.slice(0, 1600),
      hasFeatureVm: !!vm,
      featureKey: vm && vm.featureKey,
      selectedDeptId: vm && vm.selectedDeptId,
      selectedDeptType: vm && vm.selectedDeptType,
      selectedDeptName: vm && vm.selectedDeptName,
      loading: vm && vm.loading,
      loadingMore: vm && vm.loadingMore,
      detailLoading: vm && vm.detailLoading,
      errorMessage: vm && vm.errorMessage,
      total: vm && vm.total,
      itemCount: vm && vm.items && vm.items.length,
      items: vm && vm.items ? vm.items.slice(0, 8).map(__qaCompactItem) : [],
      selectedItem: vm && vm.selectedItem ? __qaCompactItem(vm.selectedItem) : null,
      detailActions: vm && vm.detailActions ? vm.detailActions.map(a => ({ id: a.id, label: a.label })) : [],
      topActions: vm && vm.featureActionItems ? vm.featureActionItems.map(a => ({ label: a.label, actionId: a.actionId, behavior: a.behavior, path: a.path })) : [],
      actionMessage: vm && vm.actionMessage,
      actionLoadingKey: vm && vm.actionLoadingKey,
      actionDialogOpen: vm && vm.actionDialog && vm.actionDialog.open,
      mobileConfirmOpen: vm && vm.mobileConfirm && vm.mobileConfirm.open,
      mobileConfirm: vm && vm.mobileConfirm ? { title: vm.mobileConfirm.title, message: vm.mobileConfirm.message, confirmText: vm.mobileConfirm.confirmText } : null,
      actionDialog: vm && vm.actionDialog && vm.actionDialog.action ? { id: vm.actionDialog.action.id, label: vm.actionDialog.action.label } : null
    }
  })()`)
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

async function waitFeatureIdle(cdp) {
  await cdp.waitFor(`(() => { ${findVmSource}
    const vm = __qaFindVmByName('MobileFeaturePage')
    return !!vm && !vm.loading && !vm.loadingMore && !vm.detailLoading
  })()`, 45000)
}

async function login(cdp, username, password, redirect = "/mobile/store") {
  await cdp.eval(`(() => {
    try {
      document.cookie.split(';').forEach(cookie => {
        const name = cookie.split('=')[0].trim()
        if (name) {
          document.cookie = name + '=; expires=Thu, 01 Jan 1970 00:00:00 GMT; path=/'
        }
      })
      localStorage.clear()
      sessionStorage.clear()
    } catch (e) {}
    return true
  })()`).catch(() => {})
  await cdp.navigate("/login?redirect=" + encodeURIComponent(redirect))
  await cdp.waitFor(`(() => { ${findVmSource} return !!__qaFindVmByName('Login') })()`, 20000)
  const captcha = await httpJson("http://127.0.0.1:8080/code")
  const rawCode = sh("redis-cli", ["--raw", "GET", "captcha_codes:" + captcha.uuid]).trim()
  const code = JSON.parse(rawCode)
  const result = await cdp.eval(`(async () => { ${findVmSource}
    const loginVm = __qaFindVmByName('Login')
    if (!loginVm) return { ok: false, reason: 'no-login-vm', url: location.href, text: document.body.innerText.slice(0, 500) }
    loginVm.loginForm.username = ${js(username)}
    loginVm.loginForm.password = ${js(password)}
    loginVm.loginForm.uuid = ${js(captcha.uuid)}
    loginVm.loginForm.code = ${js(code)}
    loginVm.loginForm.rememberMe = false
    loginVm.handleLogin()
    await new Promise(resolve => setTimeout(resolve, 3500))
    return { ok: location.pathname !== '/login', url: location.href, text: document.body.innerText.slice(0, 800) }
  })()`, 60000)
  return result
}

async function prepareFeature(cdp, context, pathname, label) {
  await setContext(cdp, context)
  await cdp.navigate(pathname)
  await waitFeatureIdle(cdp)
  screenshot(label + "_page.png")
  return getState(cdp)
}

async function searchAndOpen(cdp, options) {
  const field = options.field || ""
  const keyword = options.keyword || ""
  const query = options.query || {}
  const match = options.match || keyword
  const result = await cdp.eval(`(async () => { ${findVmSource}
    const vm = __qaFindVmByName('MobileFeaturePage')
    if (!vm) return { ok: false, reason: 'no-feature-vm', text: document.body.innerText.slice(0, 800) }
    vm.featureQuery = Object.assign({}, ${js(query)})
    if (${js(field)}) vm.activeSearchField = ${js(field)}
    vm.searchKeyword = ${js(keyword)}
    vm.pageNum = 1
    vm.loadData()
    const started = Date.now()
    while ((vm.loading || vm.loadingMore) && Date.now() - started < 30000) {
      await new Promise(resolve => setTimeout(resolve, 300))
    }
    const items = vm.items || []
    const wanted = ${js(String(match))}
    const item = items.find(row => {
      const raw = row && (row._raw || row.raw || row) || {}
      const text = [
        row && row.title,
        row && row.code,
        row && row.detail,
        raw.orderNo,
        raw.noticeNo,
        raw.returnNo,
        raw.checkNo,
        raw.title,
        raw.jobName,
        raw.salesOrderNo
      ].filter(Boolean).join(' ')
      return text.indexOf(wanted) > -1
    }) || items[0]
    if (!item) {
      return {
        ok: false,
        reason: 'not-found',
        total: vm.total,
        itemCount: items.length,
        errorMessage: vm.errorMessage,
        items: items.slice(0, 6).map(__qaCompactItem),
        text: document.body.innerText.slice(0, 1200)
      }
    }
    await vm.$nextTick()
    const rows = Array.from(document.querySelectorAll('.list-row'))
    const row = rows.find(el => el.innerText.indexOf(item.code) > -1 || el.innerText.indexOf(wanted) > -1)
    let rowClickOpened = false
    if (row) {
      row.click()
      await new Promise(resolve => setTimeout(resolve, 1200))
      rowClickOpened = !!vm.selectedItem
    }
    if (!vm.selectedItem || (vm.selectedItem && vm.selectedItem.code !== item.code)) {
      vm.openItem(item)
    }
    const startedDetail = Date.now()
    while (vm.detailLoading && Date.now() - startedDetail < 30000) {
      await new Promise(resolve => setTimeout(resolve, 300))
    }
    return {
      ok: true,
      rowClickOpened,
      selectedItem: vm.selectedItem ? __qaCompactItem(vm.selectedItem) : null,
      detailActions: vm.detailActions.map(a => ({ id: a.id, label: a.label })),
      actionMessage: vm.actionMessage,
      total: vm.total,
      itemCount: items.length,
      text: document.body.innerText.slice(0, 1400)
    }
  })()`, 60000)
  return result
}

async function buildPayload(cdp, actionId, override = {}) {
  return cdp.eval(`(() => { ${findVmSource}
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
    const remaining = row => Math.max(number(first(row, ['remainingQuantity', 'remainingQty'])) || (number(first(row, ['quantity', 'noticeQty', 'shippedQuantity'])) - number(first(row, ['receivedQuantity', 'deliveredQuantity', 'deliveredQty']))), 0)
    const details = Array.isArray(raw.details) ? raw.details : []
    const shipments = Array.isArray(raw.shipments) ? raw.shipments : []
    let payload = { allRemaining: true, warehouseId: vm && vm.selectedDeptId, comment: ${js("QA mobile real-flow " + QA)}, qcResult: 'passed', qcRemark: ${js("QA mobile real-flow " + QA)} }
    if (${js(actionId)} === 'inputStockCheck') {
      payload.details = details.map(row => ({
        detailId: first(row, ['detailId', 'checkDetailId']),
        actualQuantity: number(first(row, ['actualQuantity', 'checkQuantity', 'bookQuantity', 'systemQuantity', 'quantity']))
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

async function runDetailAction(cdp, actionId, label, payloadOverride = {}) {
  const start = await cdp.eval(`(() => { ${findVmSource}
    const vm = __qaFindVmByName('MobileFeaturePage')
    if (!vm || !vm.selectedItem) return { ok: false, reason: 'no-selected-item' }
    const action = vm.detailActions.find(item => item.id === ${js(actionId)} || item.label === ${js(actionId)})
    if (!action) return { ok: false, reason: 'action-not-visible', actions: vm.detailActions.map(a => ({ id: a.id, label: a.label })), text: document.body.innerText.slice(0, 1000) }
    vm.handleDetailAction(action)
    return { ok: true, action: { id: action.id, label: action.label }, confirmOpen: vm.mobileConfirm.open, dialogOpen: vm.actionDialog.open, text: document.body.innerText.slice(0, 1000) }
  })()`)
  await sleep(800)
  const modalState = await getState(cdp)
  screenshot(label + "_modal.png")
  if (!start.ok) {
    return Object.assign({ ok: false, after: modalState }, start)
  }
  if (modalState.actionDialogOpen) {
    const payload = await buildPayload(cdp, actionId, payloadOverride)
    await cdp.eval(`(() => { ${findVmSource}
      const vm = __qaFindVmByName('MobileFeaturePage')
      vm.confirmActionDialog(${js(payload)})
      return true
    })()`)
  } else if (modalState.mobileConfirmOpen) {
    await cdp.eval(`(() => { ${findVmSource}
      const vm = __qaFindVmByName('MobileFeaturePage')
      vm.confirmMobileConfirm()
      return true
    })()`)
  }
  await sleep(1200)
  await cdp.waitFor(`(() => { ${findVmSource}
    const vm = __qaFindVmByName('MobileFeaturePage')
    return !!vm && !vm.actionLoadingKey && !vm.loading && !vm.loadingMore && !vm.detailLoading
  })()`, 60000).catch(() => false)
  await sleep(700)
  screenshot(label + "_after.png")
  const after = await getState(cdp)
  const ok = !after.actionMessage || !/失败|错误|不存在|不能|缺少|无权限|异常/.test(after.actionMessage)
  return { ok, start, modal: modalState, after }
}

async function runRecordCase(cdp, results, spec) {
  const result = {
    id: spec.id,
    name: spec.name,
    role: spec.role || "admin",
    context: spec.context,
    path: spec.path,
    actionId: spec.actionId,
    startedAt: new Date().toISOString()
  }
  try {
    result.page = await prepareFeature(cdp, spec.context, spec.path, spec.id)
    result.open = await searchAndOpen(cdp, spec)
    screenshot(spec.id + "_detail.png")
    if (!result.open.ok) {
      result.ok = false
      result.problem = "record-not-found"
    } else {
      if (result.open.rowClickOpened === false) {
        result.rowClickProblem = "list-row-dom-click-did-not-open-detail"
      }
      result.action = await runDetailAction(cdp, spec.actionId, spec.id, spec.payloadOverride || {})
      result.ok = !!(result.action && result.action.ok)
      if (spec.db) result.dbAfter = mysql(spec.db)
    }
  } catch (error) {
    result.ok = false
    result.error = error.stack || error.message
    try {
      result.stateOnError = await getState(cdp)
      screenshot(spec.id + "_error.png")
    } catch (_) {}
  }
  result.finishedAt = new Date().toISOString()
  results.cases.push(result)
  fs.writeFileSync(RESULT_PATH, JSON.stringify(results, null, 2))
  console.log((result.ok ? "PASS" : "FAIL") + " " + spec.id + " - " + spec.name)
  return result
}

async function runFindCase(cdp, results, spec) {
  const result = {
    id: spec.id,
    name: spec.name,
    role: spec.role || "admin",
    context: spec.context,
    path: spec.path,
    startedAt: new Date().toISOString()
  }
  try {
    result.page = await prepareFeature(cdp, spec.context, spec.path, spec.id)
    result.open = await searchAndOpen(cdp, spec)
    screenshot(spec.id + "_result.png")
    result.ok = !!result.open.ok
    if (spec.db) result.dbAfter = mysql(spec.db)
  } catch (error) {
    result.ok = false
    result.error = error.stack || error.message
    try {
      result.stateOnError = await getState(cdp)
      screenshot(spec.id + "_error.png")
    } catch (_) {}
  }
  result.finishedAt = new Date().toISOString()
  results.cases.push(result)
  fs.writeFileSync(RESULT_PATH, JSON.stringify(results, null, 2))
  console.log((result.ok ? "PASS" : "FAIL") + " " + spec.id + " - " + spec.name)
  return result
}

async function runTopActionCase(cdp, results, spec) {
  const result = {
    id: spec.id,
    name: spec.name,
    role: spec.role || "admin",
    context: spec.context,
    path: spec.path,
    actionId: spec.actionId,
    startedAt: new Date().toISOString()
  }
  try {
    result.page = await prepareFeature(cdp, spec.context, spec.path, spec.id)
    if (spec.search) {
      await cdp.eval(`(async () => { ${findVmSource}
        const vm = __qaFindVmByName('MobileFeaturePage')
        vm.featureQuery = Object.assign({}, ${js(spec.search.query || {})})
        vm.activeSearchField = ${js(spec.search.field || "")} || vm.activeSearchField
        vm.searchKeyword = ${js(spec.search.keyword || "")}
        vm.pageNum = 1
        vm.loadData()
        while (vm.loading || vm.loadingMore) await new Promise(resolve => setTimeout(resolve, 250))
        return true
      })()`)
    }
    screenshot(spec.id + "_before.png")
    const start = await cdp.eval(`(() => { ${findVmSource}
      const vm = __qaFindVmByName('MobileFeaturePage')
      if (!vm) return { ok: false, reason: 'no-feature-vm' }
      const action = vm.featureActionItems.find(item => item.actionId === ${js(spec.actionId)} || item.behavior === ${js(spec.behavior || "")} || item.label === ${js(spec.label || "")})
      if (!action) return { ok: false, reason: 'top-action-not-visible', topActions: vm.featureActionItems.map(a => ({ label: a.label, actionId: a.actionId, behavior: a.behavior })) }
      vm.handleAction(action)
      return { ok: true, action: { label: action.label, actionId: action.actionId, behavior: action.behavior }, confirmOpen: vm.mobileConfirm.open, text: document.body.innerText.slice(0, 1000) }
    })()`)
    await sleep(800)
    result.start = start
    result.modal = await getState(cdp)
    screenshot(spec.id + "_modal.png")
    if (start.ok && result.modal.mobileConfirmOpen) {
      await cdp.eval(`(() => { ${findVmSource}
        const vm = __qaFindVmByName('MobileFeaturePage')
        vm.confirmMobileConfirm()
        return true
      })()`)
    }
    await sleep(1500)
    await cdp.waitFor(`(() => { ${findVmSource}
      const vm = __qaFindVmByName('MobileFeaturePage')
      return !!vm && !vm.actionLoadingKey && !vm.loading && !vm.loadingMore
    })()`, 60000).catch(() => false)
    screenshot(spec.id + "_after.png")
    result.after = await getState(cdp)
    if (spec.db) result.dbAfter = mysql(spec.db)
    result.ok = start.ok && (!result.after.actionMessage || !/失败|错误|不存在|不能|缺少|无权限|异常/.test(result.after.actionMessage))
  } catch (error) {
    result.ok = false
    result.error = error.stack || error.message
    try {
      result.stateOnError = await getState(cdp)
      screenshot(spec.id + "_error.png")
    } catch (_) {}
  }
  result.finishedAt = new Date().toISOString()
  results.cases.push(result)
  fs.writeFileSync(RESULT_PATH, JSON.stringify(results, null, 2))
  console.log((result.ok ? "PASS" : "FAIL") + " " + spec.id + " - " + spec.name)
  return result
}

function writeReport(results) {
  const failures = results.cases.filter(item => !item.ok || item.rowClickProblem)
  const lines = []
  lines.push("# Android Mobile Real-Flow QA")
  lines.push("")
  lines.push("- Run: " + results.runId)
  lines.push("- Device: " + SERIAL)
  lines.push("- APK: erp-ui/build/mobile-packages/erp-mobile-android-real-test.apk")
  lines.push("- Output: " + OUT_DIR)
  lines.push("")
  lines.push("## Cases")
  results.cases.forEach(item => {
    lines.push(`- ${item.ok ? "PASS" : "FAIL"} ${item.id}: ${item.name}`)
  })
  lines.push("")
  lines.push("## Findings")
  if (!failures.length) {
    lines.push("- No failing write-flow case in this run.")
  } else {
    failures.forEach(item => {
      const afterMessage = item.action && item.action.after && item.action.after.actionMessage
        ? item.action.after.actionMessage
        : item.after && item.after.actionMessage
          ? item.after.actionMessage
          : item.problem || item.error || item.rowClickProblem || "see JSON"
      lines.push(`- ${item.id}: ${afterMessage}`)
      lines.push(`  - Screenshots: ${path.join(OUT_DIR, item.id + "_detail.png")}, ${path.join(OUT_DIR, item.id + "_modal.png")}, ${path.join(OUT_DIR, item.id + "_after.png")}`)
    })
  }
  fs.writeFileSync(REPORT_PATH, lines.join("\n") + "\n")
}

async function main() {
  const store = { id: 1176, name: "北京柏悦", type: "STORE" }
  const warehouse = { id: 1245, name: "主仓库", type: "WAREHOUSE" }
  const results = {
    runId: QA,
    startedAt: new Date().toISOString(),
    outDir: OUT_DIR,
    cases: []
  }

  const cdp = new Cdp()
  await cdp.connect()

  results.adminLogin = await login(cdp, "admin", "admin123", "/mobile/store")
  screenshot("admin_login_after.png")

  await runRecordCase(cdp, results, {
    id: "01_stock_check_input",
    name: "盘点单录入实盘",
    context: warehouse,
    path: "/mobile/stock-check",
    field: "checkNo",
    keyword: QA + "-SC-IN",
    actionId: "inputStockCheck",
    db: `SELECT check_no,status FROM inv_stock_check WHERE check_no='${QA}-SC-IN'; SELECT detail_id,book_qty,actual_qty FROM inv_stock_check_detail WHERE check_id=19;`
  })
  await runRecordCase(cdp, results, {
    id: "02_stock_check_confirm",
    name: "盘点单确认并写入差异",
    context: warehouse,
    path: "/mobile/stock-check",
    field: "checkNo",
    keyword: QA + "-SC-IN",
    actionId: "confirmStockCheck",
    db: `SELECT check_no,status FROM inv_stock_check WHERE check_no='${QA}-SC-IN';`
  })
  await runRecordCase(cdp, results, {
    id: "03_stock_check_cancel",
    name: "盘点单取消",
    context: warehouse,
    path: "/mobile/stock-check",
    field: "checkNo",
    keyword: QA + "-SC-CAN",
    actionId: "cancelStockCheck",
    db: `SELECT check_no,status FROM inv_stock_check WHERE check_no='${QA}-SC-CAN';`
  })
  await runRecordCase(cdp, results, {
    id: "04_purchase_receive",
    name: "采购单按数量收货",
    context: warehouse,
    path: "/mobile/purchase",
    field: "orderNo",
    keyword: QA + "-PO-RCV",
    actionId: "receivePurchaseAll",
    db: `SELECT order_no,status,qc_status FROM inv_purchase_order WHERE order_no='${QA}-PO-RCV';`
  })
  await runRecordCase(cdp, results, {
    id: "05_purchase_qc",
    name: "采购单质检通过",
    context: warehouse,
    path: "/mobile/purchase",
    field: "orderNo",
    keyword: QA + "-PO-RCV",
    query: {},
    actionId: "qualityCheckPurchase",
    db: `SELECT order_no,status,qc_status FROM inv_purchase_order WHERE order_no='${QA}-PO-RCV';`
  })
  await runRecordCase(cdp, results, {
    id: "06_purchase_cancel",
    name: "采购单取消",
    context: warehouse,
    path: "/mobile/purchase",
    field: "orderNo",
    keyword: QA + "-PO-CAN",
    actionId: "cancelPurchase",
    db: `SELECT order_no,status FROM inv_purchase_order WHERE order_no='${QA}-PO-CAN';`
  })
  await runRecordCase(cdp, results, {
    id: "07_purchase_return_confirm",
    name: "采购退货确认出库",
    context: warehouse,
    path: "/mobile/purchase-return",
    field: "returnNo",
    keyword: QA + "-PR-CF",
    actionId: "confirmPurchaseReturn",
    db: `SELECT return_no,status FROM inv_purchase_return WHERE return_no='${QA}-PR-CF';`
  })
  await runRecordCase(cdp, results, {
    id: "08_purchase_return_cancel",
    name: "采购退货取消",
    context: warehouse,
    path: "/mobile/purchase-return",
    field: "returnNo",
    keyword: QA + "-PR-CAN",
    actionId: "cancelPurchaseReturn",
    db: `SELECT return_no,status FROM inv_purchase_return WHERE return_no='${QA}-PR-CAN';`
  })
  await runRecordCase(cdp, results, {
    id: "09_transfer_approval_approve",
    name: "调拨审批通过",
    context: warehouse,
    path: "/mobile/transfer-approval",
    field: "orderNo",
    keyword: QA + "-TF-AP",
    actionId: "approveTransfer",
    db: `SELECT order_no,status FROM inv_transfer_order WHERE order_no='${QA}-TF-AP';`
  })
  await runRecordCase(cdp, results, {
    id: "10_transfer_approval_reject",
    name: "调拨审批驳回",
    context: warehouse,
    path: "/mobile/transfer-approval",
    field: "orderNo",
    keyword: QA + "-TF-RJ",
    actionId: "rejectTransfer",
    db: `SELECT order_no,status FROM inv_transfer_order WHERE order_no='${QA}-TF-RJ';`
  })
  await runRecordCase(cdp, results, {
    id: "11_transfer_deliver",
    name: "调拨单按数量发货",
    context: warehouse,
    path: "/mobile/transfer",
    field: "orderNo",
    keyword: QA + "-TF-DL",
    query: { statusGroup: "deliverable", direction: "deliver" },
    actionId: "deliverTransferAll",
    db: `SELECT order_no,status FROM inv_transfer_order WHERE order_no='${QA}-TF-DL';`
  })
  await runRecordCase(cdp, results, {
    id: "12_outbound_deliver_warehouse",
    name: "仓库发货通知按数量出库",
    context: warehouse,
    path: "/mobile/outbound",
    field: "noticeNo",
    keyword: QA + "-DN-WH",
    actionId: "deliverDeliveryNoticeAll",
    db: `SELECT notice_no,status FROM inv_delivery_notice WHERE notice_no='${QA}-DN-WH';`
  })
  await runTopActionCase(cdp, results, {
    id: "13_monitor_job_run",
    name: "定时任务执行一次",
    context: warehouse,
    path: "/mobile/monitor-job",
    actionId: "runMonitorJob",
    search: { field: "jobName", keyword: QA + " QA任务", query: {} },
    db: `SELECT job_id,job_name,status FROM sys_job WHERE job_name='${QA} QA任务';`
  })
  await runRecordCase(cdp, results, {
    id: "14_monitor_job_resume",
    name: "定时任务恢复",
    context: warehouse,
    path: "/mobile/monitor-job",
    field: "jobName",
    keyword: QA + " QA任务",
    query: {},
    actionId: "resumeMonitorJob",
    db: `SELECT job_id,job_name,status FROM sys_job WHERE job_name='${QA} QA任务';`
  })
  await runRecordCase(cdp, results, {
    id: "15_monitor_job_pause",
    name: "定时任务暂停",
    context: warehouse,
    path: "/mobile/monitor-job",
    field: "jobName",
    keyword: QA + " QA任务",
    query: {},
    actionId: "pauseMonitorJob",
    db: `SELECT job_id,job_name,status FROM sys_job WHERE job_name='${QA} QA任务';`
  })
  await runRecordCase(cdp, results, {
    id: "16_sales_create_delivery",
    name: "销售单生成发货通知",
    context: store,
    path: "/mobile/sales",
    field: "orderNo",
    keyword: QA + "-SO-GEN",
    actionId: "createDeliveryNotice",
    db: `SELECT order_no,status FROM inv_sales_order WHERE order_no='${QA}-SO-GEN'; SELECT notice_no,shop_dept_id,warehouse_id,status FROM inv_delivery_notice WHERE sales_order_no='${QA}-SO-GEN' ORDER BY notice_id DESC LIMIT 3;`
  })
  await runFindCase(cdp, results, {
    id: "17_generated_notice_visible_in_warehouse_outbound",
    name: "销售生成的发货通知在仓库出库页可见",
    context: warehouse,
    path: "/mobile/outbound",
    field: "salesOrderNo",
    keyword: QA + "-SO-GEN",
    match: QA + "-SO-GEN",
    db: `SELECT notice_no,shop_dept_id,warehouse_id,status FROM inv_delivery_notice WHERE sales_order_no='${QA}-SO-GEN' ORDER BY notice_id DESC LIMIT 5;`
  })
  await runRecordCase(cdp, results, {
    id: "18_sales_cancel",
    name: "销售单取消",
    context: store,
    path: "/mobile/sales",
    field: "orderNo",
    keyword: QA + "-SO-CAN",
    actionId: "cancelSales",
    db: `SELECT order_no,status FROM inv_sales_order WHERE order_no='${QA}-SO-CAN';`
  })
  await runRecordCase(cdp, results, {
    id: "19_sales_return_confirm",
    name: "销售退货确认入库",
    context: store,
    path: "/mobile/sales-return",
    field: "returnNo",
    keyword: QA + "-SR-CF",
    query: { businessType: "return", status: "submitted" },
    actionId: "confirmSalesReturn",
    db: `SELECT return_no,status FROM inv_sales_return WHERE return_no='${QA}-SR-CF';`
  })
  await runRecordCase(cdp, results, {
    id: "20_sales_return_cancel",
    name: "销售退货取消",
    context: store,
    path: "/mobile/sales-return",
    field: "returnNo",
    keyword: QA + "-SR-CAN",
    query: { businessType: "return", status: "submitted" },
    actionId: "cancelSalesReturn",
    db: `SELECT return_no,status FROM inv_sales_return WHERE return_no='${QA}-SR-CAN';`
  })
  await runRecordCase(cdp, results, {
    id: "21_transfer_receive",
    name: "调拨确认收货",
    context: store,
    path: "/mobile/transfer",
    field: "orderNo",
    keyword: QA + "-TF-RC",
    query: { statusGroup: "receivable", direction: "receive" },
    actionId: "receiveTransferShipment",
    db: `SELECT order_no,status FROM inv_transfer_order WHERE order_no='${QA}-TF-RC';`
  })
  await runRecordCase(cdp, results, {
    id: "22_transfer_cancel",
    name: "调拨单取消",
    context: store,
    path: "/mobile/transfer",
    field: "orderNo",
    keyword: QA + "-TF-CAN",
    query: {},
    actionId: "cancelTransfer",
    db: `SELECT order_no,status FROM inv_transfer_order WHERE order_no='${QA}-TF-CAN';`
  })
  await runRecordCase(cdp, results, {
    id: "23_replenishment_submit",
    name: "补货申请提交",
    context: store,
    path: "/mobile/replenishment",
    field: "orderNo",
    keyword: QA + "-TF-SUB",
    query: { transferType: "warehouse", status: "draft" },
    actionId: "submitReplenishment",
    db: `SELECT order_no,status FROM inv_transfer_order WHERE order_no='${QA}-TF-SUB';`
  })
  await runRecordCase(cdp, results, {
    id: "24_outbound_cancel",
    name: "发货通知取消",
    context: warehouse,
    path: "/mobile/outbound",
    field: "noticeNo",
    keyword: QA + "-DN-CAN",
    actionId: "cancelDeliveryNotice",
    db: `SELECT notice_no,status FROM inv_delivery_notice WHERE notice_no='${QA}-DN-CAN';`
  })
  await runRecordCase(cdp, results, {
    id: "25_oa_purchase_submit",
    name: "OA采购申请提交",
    context: store,
    path: "/mobile/oa-purchase",
    field: "title",
    keyword: QA + " OA采购提交",
    query: { status: "draft" },
    actionId: "submitOaPurchase",
    db: `SELECT id,title,status FROM oa_purchase WHERE title='${QA} OA采购提交';`
  })
  await runRecordCase(cdp, results, {
    id: "26_oa_todo_approve",
    name: "OA待办审批通过",
    context: store,
    path: "/mobile/oa-todo",
    field: "title",
    keyword: QA + " OA采购提交",
    query: { status: "submitted" },
    actionId: "approveOaPurchase",
    db: `SELECT id,title,status FROM oa_purchase WHERE title='${QA} OA采购提交';`
  })
  await runRecordCase(cdp, results, {
    id: "27_fixed_asset_repair_confirm",
    name: "固定资产维修确认上报",
    context: store,
    path: "/mobile/fixed-asset-repair",
    field: "productName",
    keyword: QA,
    query: { status: "pending_confirm" },
    actionId: "confirmFixedAssetRepair",
    db: `SELECT repair_id,product_name,status FROM oa_fixed_asset_repair WHERE product_name LIKE '${QA}%';`
  })
  await runTopActionCase(cdp, results, {
    id: "28_salary_calculate",
    name: "工资计算本月",
    context: store,
    path: "/mobile/salary",
    actionId: "calculateSalary",
    db: `SELECT salary_month,user_id,status FROM oa_salary WHERE salary_month='2026-07' AND user_id IN (1,944) ORDER BY user_id;`
  })
  await runRecordCase(cdp, results, {
    id: "29_notice_mark_read",
    name: "通知标记已读",
    context: store,
    path: "/mobile/notice",
    field: "noticeTitle",
    keyword: QA + " 移动端通知",
    query: {},
    actionId: "markNoticeRead",
    db: `SELECT notice_id,notice_title FROM sys_notice WHERE notice_title='${QA} 移动端通知';`
  })
  await runTopActionCase(cdp, results, {
    id: "30_stock_export_confirm",
    name: "库存页导出确认弹窗",
    context: store,
    path: "/mobile/stock",
    behavior: "export",
    label: "导出"
  })

  results.teaLogin = await login(cdp, "19834743225", "123456", "/mobile/attendance")
  screenshot("tea_login_after.png")
  await runTopActionCase(cdp, results, {
    id: "31_attendance_check_in",
    name: "茶艺师上班打卡",
    role: "tea_artist",
    context: store,
    path: "/mobile/attendance",
    actionId: "checkInAttendance",
    db: `SELECT record_id,user_id,work_date,check_in_time,check_out_time FROM oa_attendance WHERE user_id=944 AND work_date='2026-07-04';`
  })
  await runTopActionCase(cdp, results, {
    id: "32_attendance_check_out",
    name: "茶艺师下班打卡",
    role: "tea_artist",
    context: store,
    path: "/mobile/attendance",
    actionId: "checkOutAttendance",
    db: `SELECT record_id,user_id,work_date,check_in_time,check_out_time FROM oa_attendance WHERE user_id=944 AND work_date='2026-07-04';`
  })

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
