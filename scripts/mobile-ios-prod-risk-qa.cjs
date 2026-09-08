#!/usr/bin/env node

const ERP_QA_PASSWORD = process.env.ERP_QA_PASSWORD
if (ERP_QA_PASSWORD === undefined || ERP_QA_PASSWORD === "") {
  console.error("ERP_QA_PASSWORD is required")
  process.exit(2)
}

const fs = require("fs")
const http = require("http")
const path = require("path")
const { spawn, execFileSync } = require("child_process")

const ROOT = path.resolve(__dirname, "..")
const UI_ORIGIN = process.env.UI_ORIGIN || "http://127.0.0.1:19025"
const API_ORIGIN = process.env.API_ORIGIN || "http://127.0.0.1:8080"
const DB = process.env.DB || "BossERP_NEW"
const RUN_ID = "RISK-" + new Date().toISOString().replace(/[-:T.Z]/g, "").slice(0, 14)
const OUT_DIR = process.env.OUT_DIR || path.join("/tmp", "erp-risk-qa-" + RUN_ID)
const RESULTS_PATH = path.join(OUT_DIR, "risk-results.json")
const REPORT_PATH = path.join(OUT_DIR, "risk-report.md")
const DRIVER_PORT = Number(process.env.SAFARI_DRIVER_PORT || 7065)

const STORE = { id: 1176, name: "北京柏悦", type: "STORE" }
const WAREHOUSE = { id: 1245, name: "主仓库", type: "WAREHOUSE" }

const DEVICES = [
  { name: "iPhone 17 Pro", udid: "E95C9B74-FCB5-4E8C-B8C0-E42AFEED353D", agent: "admin_store", username: "admin", password: ERP_QA_PASSWORD, context: STORE, paths: ["/mobile/store", "/mobile/sales", "/mobile/stock", "/mobile/mine", "/mobile/profile"] },
  { name: "iPhone 17 Pro Max", udid: "DB875AB2-F9DB-406E-83EF-230CBC3D2C2C", agent: "admin_warehouse", username: "admin", password: ERP_QA_PASSWORD, context: WAREHOUSE, paths: ["/mobile/warehouse", "/mobile/outbound", "/mobile/purchase", "/mobile/stock-check", "/mobile/transfer"] },
  { name: "iPhone 17e", udid: "406D44B8-9026-4426-A161-ABE6273DE506", agent: "tea_artist", username: "19834743225", password: ERP_QA_PASSWORD, context: STORE, paths: ["/mobile/store", "/mobile/attendance", "/mobile/sales", "/mobile/mine"] },
  { name: "iPhone Air", udid: "8B2B6153-2749-445A-A825-31470BA6CFFC", agent: "ops_director", username: "13659326770", password: ERP_QA_PASSWORD, context: STORE, paths: ["/mobile/store", "/mobile/stock", "/mobile/transfer-approval", "/mobile/oa-todo", "/mobile/mine"] },
  { name: "iPad mini (A17 Pro)", udid: "CBC05123-7557-4433-BEED-AB631F6A4051", agent: "sys_maint", username: "18669567559", password: ERP_QA_PASSWORD, context: null, paths: ["/mobile/mine", "/mobile/profile", "/mobile/notice", "/mobile/system-user"] }
]

fs.mkdirSync(OUT_DIR, { recursive: true })

function sleep(ms) {
  return new Promise(resolve => setTimeout(resolve, ms))
}

function sh(command, args, options = {}) {
  return execFileSync(command, args, Object.assign({
    cwd: ROOT,
    encoding: "utf8",
    stdio: ["ignore", "pipe", "pipe"],
    maxBuffer: 50 * 1024 * 1024
  }, options))
}

function mysqlScript(sql) {
  return execFileSync("mysql", ["-uroot", DB], {
    cwd: ROOT,
    input: sql,
    encoding: "utf8",
    stdio: ["pipe", "pipe", "pipe"],
    maxBuffer: 50 * 1024 * 1024
  }).trim()
}

function safeName(value) {
  return String(value || "").replace(/[^a-zA-Z0-9._-]+/g, "_").replace(/^_+|_+$/g, "")
}

function js(value) {
  return JSON.stringify(value)
}

function percentile(values, p) {
  if (!values.length) return null
  const sorted = values.slice().sort((a, b) => a - b)
  const index = Math.ceil((p / 100) * sorted.length) - 1
  return sorted[Math.max(0, Math.min(sorted.length - 1, index))]
}

function summarizeDurations(records) {
  const durations = records.filter(r => Number.isFinite(r.ms)).map(r => r.ms)
  const failures = records.filter(r => !r.ok)
  return {
    total: records.length,
    ok: records.length - failures.length,
    failed: failures.length,
    errorRate: records.length ? Number((failures.length / records.length).toFixed(4)) : 1,
    p50: percentile(durations, 50),
    p90: percentile(durations, 90),
    p95: percentile(durations, 95),
    max: durations.length ? Math.max(...durations) : null,
    failures: failures.slice(0, 5).map(r => ({ status: r.status, code: r.code, msg: r.msg, ms: r.ms }))
  }
}

async function httpJson(url, options = {}) {
  const controller = new AbortController()
  const timeout = setTimeout(() => controller.abort(), options.timeoutMs || 15000)
  const started = Date.now()
  try {
    const response = await fetch(url, Object.assign({}, options, { signal: controller.signal }))
    const text = await response.text()
    let data = null
    try {
      data = text ? JSON.parse(text) : null
    } catch (_) {
      data = { raw: text.slice(0, 500) }
    }
    const code = data && Object.prototype.hasOwnProperty.call(data, "code") ? data.code : response.status
    return {
      ok: response.ok && (code === 200 || code === "200"),
      status: response.status,
      code,
      msg: data && (data.msg || data.message || data.error),
      data,
      ms: Date.now() - started
    }
  } catch (error) {
    return {
      ok: false,
      status: "ERR",
      code: "ERR",
      msg: error && (error.message || String(error)),
      data: null,
      ms: Date.now() - started
    }
  } finally {
    clearTimeout(timeout)
  }
}

async function loginApi(username, password) {
  const captcha = await httpJson(API_ORIGIN + "/code", { timeoutMs: 10000 })
  if (!captcha.ok) {
    throw new Error("captcha failed: " + JSON.stringify(captcha))
  }
  const rawCode = sh("redis-cli", ["--raw", "GET", "captcha_codes:" + captcha.data.uuid]).trim()
  const code = JSON.parse(rawCode)
  const result = await httpJson(API_ORIGIN + "/auth/login", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ username, password, code, uuid: captcha.data.uuid }),
    timeoutMs: 10000
  })
  if (!result.ok) {
    throw new Error("login failed for " + username + ": " + JSON.stringify(result).slice(0, 600))
  }
  const data = result.data.data || result.data
  const token = data.access_token || data.token || data.accessToken
  if (!token) {
    throw new Error("missing token for " + username + ": " + JSON.stringify(result.data).slice(0, 600))
  }
  return { token, expiresIn: data.expires_in || data.expiresIn || 7200 }
}

function authHeaders(auth, deptId) {
  const headers = { Authorization: "Bearer " + auth.token }
  if (deptId) headers["Dept-NumId"] = String(deptId)
  return headers
}

function bootDevice(device) {
  try {
    sh("xcrun", ["simctl", "boot", device.udid])
  } catch (_) {}
  try {
    sh("xcrun", ["simctl", "bootstatus", device.udid, "-b"], { timeout: 30000 })
  } catch (_) {}
}

function screenshot(device, name) {
  const file = path.join(OUT_DIR, safeName(device.agent + "_" + name) + ".png")
  sh("xcrun", ["simctl", "io", device.udid, "screenshot", file])
  return file
}

async function pageScreenshot(session, device, name) {
  const file = path.join(OUT_DIR, safeName(device.agent + "_" + name) + ".png")
  const res = await session.request("GET", "/screenshot")
  const encoded = res.value || (res && res.data)
  if (!encoded) {
    return screenshot(device, name)
  }
  fs.writeFileSync(file, Buffer.from(encoded, "base64"))
  return file
}

class SafariDriver {
  constructor(port) {
    this.port = port
    this.base = "http://127.0.0.1:" + port
    this.proc = null
  }

  async start() {
    this.proc = spawn("safaridriver", ["-p", String(this.port)], { stdio: ["ignore", "pipe", "pipe"] })
    this.proc.stdout.on("data", chunk => fs.appendFileSync(path.join(OUT_DIR, "safaridriver.log"), chunk))
    this.proc.stderr.on("data", chunk => fs.appendFileSync(path.join(OUT_DIR, "safaridriver.log"), chunk))
    const started = Date.now()
    while (Date.now() - started < 10000) {
      try {
        await this.request("GET", "/status")
        return
      } catch (_) {
        await sleep(250)
      }
    }
    throw new Error("safaridriver did not start")
  }

  stop() {
    if (this.proc) {
      this.proc.kill("SIGTERM")
      this.proc = null
    }
  }

  request(method, pathname, body) {
    const payload = body ? JSON.stringify(body) : ""
    return new Promise((resolve, reject) => {
      const req = http.request({
        method,
        hostname: "127.0.0.1",
        port: this.port,
        path: pathname,
        headers: body ? {
          "Content-Type": "application/json",
          "Content-Length": Buffer.byteLength(payload)
        } : {}
      }, res => {
        let text = ""
        res.setEncoding("utf8")
        res.on("data", chunk => { text += chunk })
        res.on("end", () => {
          let data = {}
          if (text) {
            try { data = JSON.parse(text) } catch (_) { data = { value: text } }
          }
          if (res.statusCode >= 400) {
            reject(new Error(method + " " + pathname + " -> " + res.statusCode + " " + text.slice(0, 500)))
            return
          }
          resolve(data)
        })
      })
      req.on("error", reject)
      if (payload) req.write(payload)
      req.end()
    })
  }

  async createSession(device) {
    const res = await this.request("POST", "/session", {
      capabilities: {
        alwaysMatch: {
          browserName: "Safari",
          platformName: "ios",
          "safari:useSimulator": true,
          "safari:deviceUDID": device.udid
        }
      }
    })
    const sid = res.sessionId || (res.value && res.value.sessionId)
    if (!sid) throw new Error("No WebDriver session id: " + JSON.stringify(res))
    return new SafariSession(this, sid)
  }
}

class SafariSession {
  constructor(driver, id) {
    this.driver = driver
    this.id = id
  }

  path(pathname) {
    return "/session/" + this.id + pathname
  }

  request(method, pathname, body) {
    return this.driver.request(method, this.path(pathname), body)
  }

  async quit() {
    try { await this.request("DELETE", "") } catch (_) {}
  }

  async goto(url) {
    await this.request("POST", "/url", { url })
  }

  async execute(script, args = []) {
    const body = /^\s*return\b/.test(script) ? script : "return (" + script + ")"
    const res = await this.request("POST", "/execute/sync", { script: body, args })
    const value = res.value
    if (value && value.__error) throw new Error(value.__error)
    return value
  }

  async executeAsync(source, args = [], timeoutMs = 30000) {
    await this.request("POST", "/timeouts", { script: timeoutMs })
    const script = `
      const done = arguments[arguments.length - 1];
      (async function() {
        ${source}
      })().then(value => done(value)).catch(error => done({ __error: error && (error.stack || error.message) || String(error) }));
    `
    const res = await this.request("POST", "/execute/async", { script, args })
    const value = res.value
    if (value && value.__error) throw new Error(value.__error)
    return value
  }
}

async function setAuthAndContext(session, auth, context) {
  await session.goto(UI_ORIGIN + "/login")
  await sleep(700)
  await session.execute(`(() => {
    document.cookie.split(';').forEach(cookie => {
      const name = cookie.split('=')[0].trim()
      if (name) document.cookie = name + '=; expires=Thu, 01 Jan 1970 00:00:00 GMT; path=/'
    })
    localStorage.clear()
    sessionStorage.clear()
    document.cookie = 'Admin-Token=' + ${js(auth.token)} + '; path=/'
    document.cookie = 'Admin-Expires-In=' + ${js(String(auth.expiresIn || 7200))} + '; path=/'
    if (${js(!!context)}) {
      const ctx = ${js(context || {})}
      sessionStorage.setItem('selected_dept_id', String(ctx.id))
      sessionStorage.setItem('selected_dept_name', ctx.name)
      sessionStorage.setItem('selected_dept_type', ctx.type)
      sessionStorage.setItem('selected_dept_validated', '1')
    }
    return true
  })()`)
}

async function setSelectedContext(session, context) {
  if (!context) return
  await session.execute(`(() => {
    const ctx = ${js(context)}
    sessionStorage.setItem('selected_dept_id', String(ctx.id))
    sessionStorage.setItem('selected_dept_name', ctx.name)
    sessionStorage.setItem('selected_dept_type', ctx.type)
    sessionStorage.setItem('selected_dept_validated', '1')
    return true
  })()`)
}

async function waitAppIdle(session, timeoutMs = 45000) {
  return session.executeAsync(`
    const started = Date.now()
    while (Date.now() - started < ${timeoutMs}) {
      const text = document.body ? document.body.innerText : ''
      const loader = document.querySelector('#loader-wrapper')
      const visibleLoader = loader && getComputedStyle(loader).display !== 'none'
      if (text && text.length > 20 && !visibleLoader && text.indexOf('Loading') === -1) return true
      await new Promise(resolve => setTimeout(resolve, 300))
    }
    return false
  `, [], timeoutMs + 5000)
}

async function pageState(session) {
  return session.execute(`(() => {
    const text = document.body ? document.body.innerText.slice(0, 2500) : ''
    const hasOverlay = /webpack|vite|compiled with problems|runtime error|Loading chunk failed|ChunkLoadError/i.test(text)
    const buttons = Array.from(document.querySelectorAll('button')).filter(btn => {
      const rect = btn.getBoundingClientRect()
      return rect.width > 0 && rect.height > 0
    }).map(btn => (btn.innerText || btn.getAttribute('aria-label') || '').trim()).filter(Boolean).slice(0, 30)
    return {
      url: location.href,
      path: location.pathname,
      title: document.title,
      text,
      hasOverlay,
      buttons,
      assetCount: document.querySelectorAll('script[src],link[href]').length,
      blank: !text || text.length < 20
    }
  })()`)
}

function pageLooksBad(state) {
  const text = String(state && state.text || "")
  if (!state || state.blank || state.hasOverlay) return true
  return /白屏|ChunkLoadError|Loading chunk failed|Cannot find module|TypeError|ReferenceError/.test(text)
}

function isLoginPageState(state) {
  const text = String(state && state.text || "")
  return !!state && (state.path === "/login" || (/欢迎回来/.test(text) && /密码/.test(text) && /验证码/.test(text)))
}

function routeMatchesExpected(state, requestedPath, device) {
  if (!state || isLoginPageState(state)) {
    return false
  }
  if (state.path === requestedPath) {
    return true
  }
  try {
    const url = new URL(state.url)
    if (url.searchParams.get("mobileRedirectReason") === "permission-mismatch") {
      return state.path === "/mobile/store" || state.path === "/mobile/warehouse"
    }
  } catch (_) {}
  return !device.context && requestedPath === "/mobile/system-user" && state.path === "/select-shop"
}

async function iosProdSmoke(auths) {
  const driver = new SafariDriver(DRIVER_PORT)
  const routeChecks = []
  await driver.start()
  try {
    for (const device of DEVICES) {
      bootDevice(device)
      const session = await driver.createSession(device)
      try {
        await setAuthAndContext(session, auths[device.agent], device.context)
        for (const routePath of device.paths) {
          const rec = { device: device.name, agent: device.agent, path: routePath }
          try {
            await setSelectedContext(session, device.context)
            await session.goto(UI_ORIGIN + routePath)
            await sleep(1000)
            await waitAppIdle(session, 35000).catch(() => false)
            await sleep(500)
            const state = await pageState(session)
            rec.url = state.url
            rec.title = state.title
            rec.buttons = state.buttons
            rec.assetCount = state.assetCount
            rec.textSample = state.text.slice(0, 500)
            rec.ok = !pageLooksBad(state) && routeMatchesExpected(state, routePath, device)
            rec.screenshot = await pageScreenshot(session, device, "prod_" + safeName(routePath))
          } catch (error) {
            rec.ok = false
            rec.error = error.stack || error.message
            try { rec.screenshot = await pageScreenshot(session, device, "prod_error_" + safeName(routePath)) } catch (_) {}
          }
          routeChecks.push(rec)
          saveResults({ routeChecks })
        }
      } finally {
        await session.quit()
      }
    }
  } finally {
    driver.stop()
  }
  return routeChecks
}

function seedConcurrentTransfer() {
  const sql = `
SET @RUN='${RUN_ID}';
SET @STORE=1176;
SET @WH=1245;
SET @NOW=NOW();
SET @PROD=(SELECT product_id FROM inv_product WHERE del_flag='0' ORDER BY product_id LIMIT 1);
INSERT INTO inv_transfer_order (
  order_no, purchase_id, from_dept_id, from_dept_name, from_warehouse_id, to_dept_id, to_dept_name, to_warehouse_id,
  status, workflow_instance_id, approval_instance_id, submitted_time, approved_time, delivered_time, received_time,
  archived_time, close_reason, total_quantity, total_amount, transfer_type, create_by, create_time, update_by, update_time, remark
) VALUES (
  CONCAT(@RUN,'-TF-CONC'), NULL, @WH, '主仓库', @WH, @STORE, '北京柏悦', @STORE,
  'submitted', NULL, NULL, @NOW, NULL, NULL, NULL,
  NULL, '', 1, 10, 'warehouse', 'qa_risk_seed', @NOW, '', @NOW, @RUN
);
SET @TF=LAST_INSERT_ID();
INSERT INTO inv_transfer_detail (
  transfer_id, product_id, product_name, product_code, quantity, delivered_quantity, received_quantity,
  cost_price, amount, unit, spec, sort_order
) VALUES (
  @TF, @PROD, CONCAT(@RUN,' 并发审批茶样'), CONCAT(@RUN,'-PROD'), 1, 0, 0,
  10, 10, '罐', 'QA规格', 0
);
INSERT INTO inv_transfer_approval_instance (
  transfer_id, rule_id, rule_name, rule_snapshot, status, current_node_order, approval_mode, required_count,
  reject_action, allow_self_approve, create_by, create_time, update_by, update_time, remark
) VALUES (
  @TF, NULL, CONCAT(@RUN,' 并发审批规则'),
  '{"approvalMode":"all_nodes","requiredCount":0,"nodes":[{"nodeOrder":1,"nodeName":"QA管理员审批","postCode":"admin","approvalMode":"any_one","requiredCount":1}]}',
  'running', 1, 'all_nodes', 0, 'back_to_draft', '1', 'qa_risk_seed', @NOW, '', @NOW, @RUN
);
SET @INST=LAST_INSERT_ID();
UPDATE inv_transfer_order SET approval_instance_id=@INST WHERE transfer_id=@TF;
INSERT INTO inv_transfer_approval_task (
  instance_id, transfer_id, node_order, node_name, post_id, post_code, post_name,
  candidate_user_ids, candidate_user_names, status, create_by, create_time, update_by, update_time, remark
) VALUES (
  @INST, @TF, 1, 'QA管理员审批', NULL, 'admin', '管理员',
  '1', 'admin', 'pending', 'qa_risk_seed', @NOW, '', @NOW, @RUN
);
SET @TASK=LAST_INSERT_ID();
SELECT CONCAT(@TF, ',', @TASK, ',', @INST) AS seeded;
`
  const output = mysqlScript(sql)
  const line = output.split(/\r?\n/).filter(Boolean).pop()
  const [transferId, taskId, instanceId] = String(line || "").split(",").map(v => Number(v))
  if (!transferId || !taskId || !instanceId) {
    throw new Error("failed to seed concurrent transfer: " + output)
  }
  return { transferId, taskId, instanceId }
}

async function apiLoad(auths) {
  const adminStore = auths.admin_store
  const adminWarehouse = auths.admin_warehouse
  const maxUiKeyword = "极端关键字".repeat(8)
  const endpoints = [
    { name: "mobile_profile", auth: adminStore, deptId: STORE.id, path: "/system/mobile/profile" },
    { name: "store_workbench_summary", auth: adminStore, deptId: STORE.id, path: "/inventory/mobile/workbench/summary" },
    { name: "sales_list_store", auth: adminStore, deptId: STORE.id, path: "/inventory/sales/list?pageNum=1&pageSize=20&status=submitted" },
    { name: "stock_list_store", auth: adminStore, deptId: STORE.id, path: "/inventory/stock/list?pageNum=1&pageSize=20" },
    { name: "product_options_max_ui_keyword", auth: adminStore, deptId: STORE.id, path: "/inventory/mobile/options/product?limit=20&keyword=" + encodeURIComponent(maxUiKeyword) },
    { name: "warehouse_workbench_summary", auth: adminWarehouse, deptId: WAREHOUSE.id, path: "/inventory/mobile/workbench/summary" },
    { name: "delivery_notice_list_warehouse", auth: adminWarehouse, deptId: WAREHOUSE.id, path: "/inventory/deliveryNotice/list?pageNum=1&pageSize=20&status=pending" },
    { name: "stock_check_draft_list", auth: adminWarehouse, deptId: WAREHOUSE.id, path: "/inventory/stockCheck/list?pageNum=1&pageSize=20&status=draft" },
    { name: "transfer_processing_deliverable", auth: adminWarehouse, deptId: WAREHOUSE.id, path: "/inventory/transfer/processing?pageNum=1&pageSize=20&statusGroup=deliverable&direction=deliver" },
    { name: "transfer_approval_todo", auth: adminWarehouse, deptId: WAREHOUSE.id, path: "/inventory/mobile/transfer-approval/todo?pageNum=1&pageSize=20" }
  ]
  const concurrency = 12
  const perEndpoint = 48
  const summaries = []
  const raw = {}
  for (const endpoint of endpoints) {
    let index = 0
    const records = []
    async function worker() {
      while (index < perEndpoint) {
        index += 1
        const url = API_ORIGIN + endpoint.path
        const rec = await httpJson(url, {
          headers: authHeaders(endpoint.auth, endpoint.deptId),
          timeoutMs: 20000
        })
        records.push(rec)
      }
    }
    await Promise.all(Array.from({ length: concurrency }, () => worker()))
    raw[endpoint.name] = records
    summaries.push(Object.assign({ name: endpoint.name, path: endpoint.path }, summarizeDurations(records)))
    saveResults({ apiLoad: { summaries, raw } })
  }
  return { summaries, raw, concurrency, perEndpoint }
}

async function extremeChecks(auths) {
  const adminStore = auths.admin_store
  const adminWarehouse = auths.admin_warehouse
  const maxUiKeyword = "超长输入".repeat(8)
  const hugeKeyword = "超长输入".repeat(700)
  const checks = []

  checks.push(Object.assign({ name: "invalid_token_profile", expectation: "401 or controlled auth failure" }, await httpJson(API_ORIGIN + "/system/mobile/profile", {
    headers: { Authorization: "Bearer invalid-risk-token" },
    timeoutMs: 10000
  })))
  checks[checks.length - 1].ok = checks[checks.length - 1].status === 401 || checks[checks.length - 1].code === 401 || checks[checks.length - 1].code === "401"

  checks.push(Object.assign({ name: "huge_page_stock_list", expectation: "no timeout or server crash" }, await httpJson(API_ORIGIN + "/inventory/stock/list?pageNum=1&pageSize=1000", {
    headers: authHeaders(adminStore, STORE.id),
    timeoutMs: 20000
  })))

  checks.push(Object.assign({ name: "max_ui_keyword_product_options", expectation: "frontend-capped keyword should not hit request-line limits" }, await httpJson(API_ORIGIN + "/inventory/mobile/options/product?limit=20&keyword=" + encodeURIComponent(maxUiKeyword), {
    headers: authHeaders(adminStore, STORE.id),
    timeoutMs: 20000
  })))

  const oversizedDirectGet = Object.assign({ name: "oversized_direct_get_rejected", expectation: "malicious oversized direct GET is rejected at request boundary" }, await httpJson(API_ORIGIN + "/inventory/mobile/options/product?limit=20&keyword=" + encodeURIComponent(hugeKeyword), {
    headers: authHeaders(adminStore, STORE.id),
    timeoutMs: 20000
  }))
  oversizedDirectGet.ok = oversizedDirectGet.status === 414 || oversizedDirectGet.code === 414
  checks.push(oversizedDirectGet)

  checks.push(Object.assign({ name: "missing_dept_sales_list", expectation: "controlled scope result without selected dept" }, await httpJson(API_ORIGIN + "/inventory/sales/list?pageNum=1&pageSize=20&status=submitted", {
    headers: authHeaders(adminStore, null),
    timeoutMs: 15000
  })))

  const seeded = seedConcurrentTransfer()
  const body = JSON.stringify({ transferId: seeded.transferId, taskId: seeded.taskId, action: "approve", comment: RUN_ID + " duplicate approve" })
  const duplicates = await Promise.all([
    httpJson(API_ORIGIN + "/inventory/transfer/approve", {
      method: "POST",
      headers: Object.assign({ "Content-Type": "application/json" }, authHeaders(adminWarehouse, WAREHOUSE.id)),
      body,
      timeoutMs: 20000
    }),
    httpJson(API_ORIGIN + "/inventory/transfer/approve", {
      method: "POST",
      headers: Object.assign({ "Content-Type": "application/json" }, authHeaders(adminWarehouse, WAREHOUSE.id)),
      body,
      timeoutMs: 20000
    })
  ])
  const postSql = `
SELECT CONCAT(
  o.status, ',',
  i.status, ',',
  SUM(CASE WHEN t.status='approved' THEN 1 ELSE 0 END), ',',
  SUM(CASE WHEN t.status='pending' THEN 1 ELSE 0 END), ',',
  COUNT(*)
) AS state
FROM inv_transfer_order o
JOIN inv_transfer_approval_instance i ON i.instance_id=${seeded.instanceId}
JOIN inv_transfer_approval_task t ON t.instance_id=i.instance_id
WHERE o.transfer_id=${seeded.transferId}
GROUP BY o.status, i.status;
`
  const postLine = mysqlScript(postSql).split(/\r?\n/).filter(Boolean).pop() || ""
  const [transferStatus, instanceStatus, approvedTasks, pendingTasks, taskCount] = postLine.split(",")
  const successCount = duplicates.filter(r => r.ok).length
  checks.push({
    name: "duplicate_transfer_approve",
    expectation: "one success, one controlled duplicate failure, final state approved once",
    ok: successCount === 1 && transferStatus === "approved" && instanceStatus === "approved" && Number(approvedTasks) === 1 && Number(pendingTasks) === 0 && Number(taskCount) === 1,
    seeded,
    duplicateResponses: duplicates.map(r => ({ ok: r.ok, status: r.status, code: r.code, msg: r.msg, ms: r.ms })),
    finalState: { transferStatus, instanceStatus, approvedTasks: Number(approvedTasks), pendingTasks: Number(pendingTasks), taskCount: Number(taskCount) }
  })

  return checks
}

function saveResults(partial) {
  const current = fs.existsSync(RESULTS_PATH) ? JSON.parse(fs.readFileSync(RESULTS_PATH, "utf8")) : {
    runId: RUN_ID,
    uiOrigin: UI_ORIGIN,
    apiOrigin: API_ORIGIN,
    outDir: OUT_DIR
  }
  fs.writeFileSync(RESULTS_PATH, JSON.stringify(Object.assign(current, partial), null, 2))
}

function buildReport(results) {
  const routeChecks = results.routeChecks || []
  const routePass = routeChecks.filter(r => r.ok).length
  const apiSummaries = results.apiLoad && results.apiLoad.summaries || []
  const extreme = results.extremeChecks || []
  const lines = []
  lines.push("# ERP mobile production risk QA")
  lines.push("")
  lines.push(`- runId: ${RUN_ID}`)
  lines.push(`- UI: ${UI_ORIGIN}`)
  lines.push(`- API: ${API_ORIGIN}`)
  lines.push(`- output: ${OUT_DIR}`)
  lines.push("")
  lines.push("## iOS Safari production bundle smoke")
  lines.push(`- result: ${routePass}/${routeChecks.length} pass`)
  lines.push("")
  lines.push("| agent | device | path | result | screenshot |")
  lines.push("| --- | --- | --- | --- | --- |")
  for (const rec of routeChecks) {
    lines.push(`| ${rec.agent} | ${rec.device} | ${rec.path} | ${rec.ok ? "PASS" : "FAIL"} | ${rec.screenshot || ""} |`)
  }
  lines.push("")
  lines.push("## API load baseline")
  lines.push(`- concurrency: ${results.apiLoad ? results.apiLoad.concurrency : ""}`)
  lines.push(`- requests per endpoint: ${results.apiLoad ? results.apiLoad.perEndpoint : ""}`)
  lines.push("")
  lines.push("| endpoint | total | failed | errorRate | p50 | p90 | p95 | max |")
  lines.push("| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: |")
  for (const item of apiSummaries) {
    lines.push(`| ${item.name} | ${item.total} | ${item.failed} | ${item.errorRate} | ${item.p50} | ${item.p90} | ${item.p95} | ${item.max} |`)
  }
  lines.push("")
  lines.push("## Extreme checks")
  lines.push("")
  lines.push("| check | result | status/code | message |")
  lines.push("| --- | --- | --- | --- |")
  for (const item of extreme) {
    const statusCode = item.status ? `${item.status}/${item.code}` : (item.duplicateResponses ? item.duplicateResponses.map(r => `${r.status}/${r.code}`).join(", ") : "")
    const msg = item.msg || (item.duplicateResponses ? item.duplicateResponses.map(r => r.msg || "").join(" | ") : "")
    lines.push(`| ${item.name} | ${item.ok ? "PASS" : "FAIL"} | ${statusCode} | ${String(msg || "").replace(/\|/g, "/").slice(0, 160)} |`)
  }
  lines.push("")
  lines.push("## Artifacts")
  lines.push(`- results: ${RESULTS_PATH}`)
  lines.push(`- screenshots: ${OUT_DIR}`)
  fs.writeFileSync(REPORT_PATH, lines.join("\n"))
}

async function main() {
  const auths = {}
  for (const device of DEVICES) {
    if (!auths[device.agent]) {
      auths[device.agent] = await loginApi(device.username, device.password)
    }
  }
  saveResults({ authAgents: Object.keys(auths) })

  const routeChecks = await iosProdSmoke(auths)
  saveResults({ routeChecks })

  const api = await apiLoad(auths)
  saveResults({ apiLoad: api })

  const extreme = await extremeChecks(auths)
  const results = JSON.parse(fs.readFileSync(RESULTS_PATH, "utf8"))
  results.extremeChecks = extreme
  fs.writeFileSync(RESULTS_PATH, JSON.stringify(results, null, 2))
  buildReport(results)

  const failedRoutes = routeChecks.filter(r => !r.ok).length
  const failedApi = api.summaries.filter(r => r.failed > 0).length
  const failedExtreme = extreme.filter(r => !r.ok).length
  console.log(JSON.stringify({
    runId: RUN_ID,
    outDir: OUT_DIR,
    report: REPORT_PATH,
    routes: { pass: routeChecks.length - failedRoutes, total: routeChecks.length },
    apiFailedEndpoints: failedApi,
    extreme: { pass: extreme.length - failedExtreme, total: extreme.length }
  }, null, 2))
  if (failedRoutes || failedApi || failedExtreme) {
    process.exitCode = 2
  }
}

main().catch(error => {
  console.error(error.stack || error.message)
  process.exit(1)
})
