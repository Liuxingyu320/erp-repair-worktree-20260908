const crypto = require("crypto")
const fs = require("fs")
const net = require("net")
const os = require("os")
const path = require("path")
const { spawn } = require("child_process")

const uiRoot = path.resolve(__dirname, "..")
const repoRoot = path.resolve(uiRoot, "..")
const evidenceRoot = path.resolve(repoRoot, "docs/evidence/mobile-beautification-20260801")
const outputDir = path.resolve(evidenceRoot, "after/runtime-erp-mobile-ua")
const routeMatrixPath = path.resolve(evidenceRoot, "matrices/route-smoke-matrix.json")
const distIndexPath = path.resolve(uiRoot, "dist/index.html")

const viewports = [
  { width: 320, height: 568 },
  { width: 354, height: 766 },
  { width: 375, height: 667 },
  { width: 390, height: 844 }
]
const captureRoutes = [
  { id: "login", path: "/login" },
  { id: "register", path: "/register" }
]
const protectedPublicRoutes = [
  "/credential/change-password",
  "/complete-profile",
  "/select-shop",
  "/401",
  "/404",
  "/lock"
]
const mobileUserAgent = "Mozilla/5.0 (iPhone; CPU iPhone OS 17_5 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.5 Mobile/15E148 Safari/604.1"

function sha256(buffer) {
  return crypto.createHash("sha256").update(buffer).digest("hex")
}

function pngSize(buffer) {
  if (buffer.length < 24 || buffer.toString("ascii", 1, 4) !== "PNG") {
    throw new Error("captured evidence is not a PNG")
  }
  return { width: buffer.readUInt32BE(16), height: buffer.readUInt32BE(20) }
}

function delay(milliseconds) {
  return new Promise(resolve => setTimeout(resolve, milliseconds))
}

function maxCssTimeMilliseconds(value) {
  return String(value || "0s").split(",").reduce((maximum, item) => {
    const token = item.trim()
    const milliseconds = token.endsWith("ms")
      ? Number.parseFloat(token)
      : Number.parseFloat(token) * 1000
    return Number.isFinite(milliseconds) ? Math.max(maximum, milliseconds) : maximum
  }, 0)
}

async function getFreePort() {
  const server = net.createServer()
  await new Promise((resolve, reject) => {
    server.once("error", reject)
    server.listen(0, "127.0.0.1", resolve)
  })
  const address = server.address()
  await new Promise(resolve => server.close(resolve))
  return address.port
}

function waitForOutput(child, stream, pattern, timeoutMs, label) {
  return new Promise((resolve, reject) => {
    let output = ""
    let settled = false
    const timeout = setTimeout(() => finish(new Error(`${label} did not become ready within ${timeoutMs}ms`)), timeoutMs)

    function finish(error, value) {
      if (settled) return
      settled = true
      clearTimeout(timeout)
      stream.off("data", onData)
      child.off("exit", onExit)
      if (error) reject(error)
      else resolve(value)
    }

    function onData(chunk) {
      output += chunk.toString()
      const match = output.match(pattern)
      if (match) finish(null, match)
    }

    function onExit(code) {
      finish(new Error(`${label} exited before readiness (code ${code}): ${output.slice(-1200)}`))
    }

    stream.on("data", onData)
    child.once("exit", onExit)
  })
}

async function startDistServer() {
  const port = await getFreePort()
  const child = spawn(process.execPath, [path.resolve(__dirname, "serve-dist.cjs")], {
    cwd: uiRoot,
    env: Object.assign({}, process.env, { ERP_UI_PREVIEW_PORT: String(port) }),
    stdio: ["ignore", "pipe", "pipe"]
  })
  await waitForOutput(child, child.stdout, /ERP_UI_DIST_READY\s+(http:\/\/[^\s]+)/, 10000, "dist server")
  return { child, baseUrl: `http://127.0.0.1:${port}` }
}

function findChrome() {
  const candidates = [
    process.env.CHROME_PATH,
    "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome",
    "/Applications/Chromium.app/Contents/MacOS/Chromium",
    "/usr/bin/google-chrome",
    "/usr/bin/chromium",
    "/usr/bin/chromium-browser"
  ].filter(Boolean)
  const executable = candidates.find(candidate => fs.existsSync(candidate))
  if (!executable) {
    throw new Error("Chrome/Chromium not found; set CHROME_PATH to a supported executable")
  }
  return executable
}

async function startChrome() {
  const profileDir = await fs.promises.mkdtemp(path.join(os.tmpdir(), "erp-mobile-evidence-"))
  const child = spawn(findChrome(), [
    "--headless=new",
    "--disable-background-networking",
    "--disable-component-update",
    "--disable-default-apps",
    "--disable-extensions",
    "--disable-sync",
    "--metrics-recording-only",
    "--no-default-browser-check",
    "--no-first-run",
    "--remote-debugging-port=0",
    "--window-size=1280,1000",
    `--user-data-dir=${profileDir}`,
    "about:blank"
  ], { stdio: ["ignore", "ignore", "pipe"] })
  const match = await waitForOutput(
    child,
    child.stderr,
    /DevTools listening on (ws:\/\/127\.0\.0\.1:(\d+)\/devtools\/browser\/[^\s]+)/,
    15000,
    "headless Chrome"
  )
  return { child, profileDir, debugPort: Number(match[2]) }
}

class CdpSession {
  constructor(webSocketUrl) {
    this.webSocketUrl = webSocketUrl
    this.nextId = 1
    this.pending = new Map()
    this.socket = null
  }

  async connect() {
    if (typeof WebSocket !== "function") {
      throw new Error("This script requires Node.js with the global WebSocket API (Node 22+)")
    }
    this.socket = new WebSocket(this.webSocketUrl)
    this.socket.addEventListener("message", event => this.onMessage(event.data))
    await new Promise((resolve, reject) => {
      this.socket.addEventListener("open", resolve, { once: true })
      this.socket.addEventListener("error", reject, { once: true })
    })
  }

  onMessage(raw) {
    const message = JSON.parse(typeof raw === "string" ? raw : Buffer.from(raw).toString("utf8"))
    if (!message.id || !this.pending.has(message.id)) return
    const { resolve, reject } = this.pending.get(message.id)
    this.pending.delete(message.id)
    if (message.error) reject(new Error(`${message.error.message} (${message.error.code})`))
    else resolve(message.result || {})
  }

  send(method, params = {}) {
    const id = this.nextId++
    return new Promise((resolve, reject) => {
      this.pending.set(id, { resolve, reject })
      this.socket.send(JSON.stringify({ id, method, params }))
    })
  }

  close() {
    if (this.socket) this.socket.close()
  }
}

async function openPage(debugPort) {
  const response = await fetch(`http://127.0.0.1:${debugPort}/json/new?${encodeURIComponent("about:blank")}`, {
    method: "PUT"
  })
  if (!response.ok) throw new Error(`Chrome page creation failed: HTTP ${response.status}`)
  const target = await response.json()
  const session = new CdpSession(target.webSocketDebuggerUrl)
  await session.connect()
  await session.send("Page.enable")
  await session.send("Runtime.enable")
  await session.send("Network.enable")
  return session
}

async function evaluate(session, expression) {
  const response = await session.send("Runtime.evaluate", {
    expression,
    awaitPromise: true,
    returnByValue: true
  })
  if (response.exceptionDetails) {
    throw new Error(response.exceptionDetails.text || "page evaluation failed")
  }
  return response.result ? response.result.value : undefined
}

async function configureMobileViewport(session, viewport) {
  await session.send("Emulation.setDeviceMetricsOverride", {
    width: viewport.width,
    height: viewport.height,
    deviceScaleFactor: 1,
    mobile: true,
    screenWidth: viewport.width,
    screenHeight: viewport.height,
    screenOrientation: { angle: 0, type: "portraitPrimary" }
  })
  await session.send("Emulation.setTouchEmulationEnabled", { enabled: true, maxTouchPoints: 5 })
  await session.send("Network.setUserAgentOverride", {
    userAgent: mobileUserAgent,
    platform: "iPhone",
    acceptLanguage: "zh-CN,zh;q=0.9"
  })
}

async function waitForStableApp(session, timeoutMs = 12000) {
  const deadline = Date.now() + timeoutMs
  let previousUrl = ""
  let stableReads = 0
  while (Date.now() < deadline) {
    try {
      const state = await evaluate(session, `(() => ({
        readyState: document.readyState,
        hasApp: Boolean(document.querySelector('#app')),
        textLength: (document.body && document.body.innerText || '').trim().length,
        url: location.href
      }))()`)
      if (state && state.readyState === "complete" && state.hasApp && state.textLength > 0) {
        stableReads = state.url === previousUrl ? stableReads + 1 : 0
        previousUrl = state.url
        if (stableReads >= 2) {
          await delay(250)
          return
        }
      }
    } catch (error) {
      // Navigation can temporarily replace the execution context; retry until the deadline.
    }
    await delay(100)
  }
  throw new Error("ERP page did not reach a stable rendered state")
}

async function navigate(session, baseUrl, routePath) {
  const requestedUrl = new URL(routePath, baseUrl).href
  await session.send("Page.navigate", { url: requestedUrl })
  await waitForStableApp(session)
  return requestedUrl
}

async function readPageMetrics(session) {
  return evaluate(session, `(() => {
    const root = document.documentElement
    const body = document.body
    const rect = selector => {
      const node = document.querySelector(selector)
      if (!node) return null
      const box = node.getBoundingClientRect()
      return { left: box.left, right: box.right, top: box.top, bottom: box.bottom, width: box.width, height: box.height }
    }
    const footer = rect('.el-register-footer')
    const primary = rect('.register-submit')
    const overlap = footer && primary
      ? Math.max(0, Math.min(footer.right, primary.right) - Math.max(footer.left, primary.left)) *
        Math.max(0, Math.min(footer.bottom, primary.bottom) - Math.max(footer.top, primary.top))
      : 0
    const captchaImage = document.querySelector('img[alt*="验证码"]')
    const captchaFallback = Array.from(document.querySelectorAll('button[aria-label="刷新验证码"] span'))
      .some(node => node.textContent.trim() === '验证码')
    const scrollWidth = Math.max(root.scrollWidth, body ? body.scrollWidth : 0)
    return {
      requestedViewport: null,
      url: location.href,
      title: document.title,
      innerWidth: innerWidth,
      innerHeight: innerHeight,
      clientWidth: root.clientWidth,
      clientHeight: root.clientHeight,
      scrollWidth,
      scrollHeight: Math.max(root.scrollHeight, body ? body.scrollHeight : 0),
      devicePixelRatio,
      horizontalOverflow: scrollWidth > root.clientWidth + 1,
      registerFooterOverlapArea: overlap,
      captcha: {
        imagePresent: Boolean(captchaImage),
        imageLoaded: Boolean(captchaImage && captchaImage.complete && captchaImage.naturalWidth > 0),
        fallbackVisible: captchaFallback
      }
    }
  })()`)
}

async function capturePng(session, filePath) {
  const response = await session.send("Page.captureScreenshot", {
    format: "png",
    fromSurface: true,
    captureBeyondViewport: false
  })
  const buffer = Buffer.from(response.data, "base64")
  await fs.promises.writeFile(filePath, buffer)
  return { buffer, size: pngSize(buffer), sha256: sha256(buffer) }
}

async function captureAnonymousPages(session, baseUrl) {
  const captures = []
  for (const viewport of viewports) {
    await configureMobileViewport(session, viewport)
    for (const route of captureRoutes) {
      const requestedUrl = await navigate(session, baseUrl, route.path)
      const metrics = await readPageMetrics(session)
      const file = `${route.id}-vp-${viewport.width}x${viewport.height}.png`
      const image = await capturePng(session, path.resolve(outputDir, file))
      captures.push({
        id: route.id,
        route: route.path,
        requestedUrl,
        finalUrl: metrics.url,
        viewport,
        cssViewport: { width: metrics.innerWidth, height: metrics.innerHeight },
        clientViewport: { width: metrics.clientWidth, height: metrics.clientHeight },
        page: {
          scrollWidth: metrics.scrollWidth,
          scrollHeight: metrics.scrollHeight,
          horizontalOverflow: metrics.horizontalOverflow,
          registerFooterOverlapArea: metrics.registerFooterOverlapArea
        },
        captcha: metrics.captcha,
        devicePixelRatio: metrics.devicePixelRatio,
        image: { file, width: image.size.width, height: image.size.height, sha256: image.sha256 }
      })
    }
  }
  return captures
}

async function captureGuardMatrix(session, baseUrl, businessRoutes) {
  const viewport = viewports[viewports.length - 1]
  await configureMobileViewport(session, viewport)
  const requestedRoutes = businessRoutes.concat(protectedPublicRoutes)
  const results = []
  for (const routePath of requestedRoutes) {
    const requestedUrl = await navigate(session, baseUrl, routePath.replace(/:id/g, "1"))
    const state = await readPageMetrics(session)
    const final = new URL(state.url)
    results.push({
      route: routePath,
      requestedUrl,
      finalUrl: state.url,
      finalPath: final.pathname,
      redirectedToLogin: final.pathname === "/login",
      horizontalOverflow: state.horizontalOverflow
    })
  }

  await navigate(session, baseUrl, "/mobile/store")
  const guardImage = await capturePng(session, path.resolve(outputDir, "guard-mobile-store-vp-390x844.png"))
  return {
    viewport,
    results,
    representativeImage: {
      file: "guard-mobile-store-vp-390x844.png",
      width: guardImage.size.width,
      height: guardImage.size.height,
      sha256: guardImage.sha256
    }
  }
}

async function validateReducedMotion(session, baseUrl) {
  await configureMobileViewport(session, viewports[viewports.length - 1])
  await session.send("Emulation.setEmulatedMedia", {
    media: "screen",
    features: [{ name: "prefers-reduced-motion", value: "reduce" }]
  })
  await navigate(session, baseUrl, "/login")
  const computed = await evaluate(session, `(() => {
    const root = document.documentElement
    const rootStyle = getComputedStyle(root)
    const button = document.querySelector('.login-submit .el-button')
    const buttonStyle = button ? getComputedStyle(button) : null
    return {
      mediaMatches: matchMedia('(prefers-reduced-motion: reduce)').matches,
      inputModality: root.getAttribute('data-input-modality'),
      buttonFound: Boolean(button),
      transitionDuration: buttonStyle ? buttonStyle.transitionDuration : '',
      animationDuration: buttonStyle ? buttonStyle.animationDuration : '',
      tokens: {
        fast: rootStyle.getPropertyValue('--motion-duration-fast').trim(),
        base: rootStyle.getPropertyValue('--motion-duration-base').trim(),
        slow: rootStyle.getPropertyValue('--motion-duration-slow').trim()
      }
    }
  })()`)
  const maxTransitionMilliseconds = maxCssTimeMilliseconds(computed.transitionDuration)
  const maxAnimationMilliseconds = maxCssTimeMilliseconds(computed.animationDuration)
  return Object.assign({}, computed, {
    maxTransitionMilliseconds,
    maxAnimationMilliseconds,
    valid: Boolean(
      computed.mediaMatches &&
      computed.buttonFound &&
      computed.inputModality === "keyboard" &&
      computed.tokens.fast === "120ms" &&
      computed.tokens.base === "180ms" &&
      computed.tokens.slow === "240ms" &&
      maxTransitionMilliseconds <= 0.01 &&
      maxAnimationMilliseconds <= 0.01
    )
  })
}

async function writeManifests({ baseUrl, browserVersion, captures, guards, reducedMotion, distSha256 }) {
  const responsiveCaptureValid = captures.every(item =>
    item.cssViewport.width === item.viewport.width &&
    item.cssViewport.height === item.viewport.height &&
    item.clientViewport.width === item.viewport.width &&
    item.image.width === item.viewport.width &&
    item.image.height === item.viewport.height &&
    !item.page.horizontalOverflow &&
    item.page.registerFooterOverlapArea === 0
  )
  const guardRedirectCount = guards.results.filter(item => item.redirectedToLogin).length
  const manifest = {
    generatedAt: new Date().toISOString(),
    classification: "C_REAL_ERP_DIST_MOBILE_UA",
    captureMethod: "Chrome DevTools Protocol Emulation.setDeviceMetricsOverride + Page.captureScreenshot",
    baseUrl,
    distIndexSha256: distSha256,
    browserVersion,
    userAgent: mobileUserAgent,
    captureCompleted: captures.length + guards.results.length + 1,
    responsiveCaptureValid,
    reducedMotionValid: reducedMotion.valid,
    reducedMotion,
    authenticatedBusinessStatesAccepted: 0,
    finalReleaseSignoffReady: false,
    captures,
    routeGuards: {
      checked: guards.results.length,
      redirectedToLogin: guardRedirectCount,
      allRedirectedToLogin: guardRedirectCount === guards.results.length,
      viewport: guards.viewport,
      representativeImage: guards.representativeImage,
      resultsFile: "route-runtime-matrix.json"
    },
    evidenceLimits: [
      "Login and registration are anonymous runtime shells; captcha availability depends on the backend.",
      "Guard redirects do not prove authenticated business-page states.",
      "No physical-device keyboard, visualViewport, notch, gesture bar, role, organization, or action acceptance is included."
    ]
  }
  await fs.promises.writeFile(path.resolve(outputDir, "MANIFEST.json"), JSON.stringify(manifest, null, 2) + "\n")
  await fs.promises.writeFile(path.resolve(outputDir, "route-runtime-matrix.json"), JSON.stringify({
    generatedAt: manifest.generatedAt,
    viewport: guards.viewport,
    results: guards.results
  }, null, 2) + "\n")

  const text = [
    "REAL ERP dist captures with mobile User-Agent",
    `classification=${manifest.classification}`,
    `capture_method=${manifest.captureMethod}`,
    `capture_completed=${manifest.captureCompleted}`,
    `responsive_capture_valid=${manifest.responsiveCaptureValid}`,
    `reduced_motion_valid=${manifest.reducedMotionValid}`,
    `business_route_guards_checked=${guards.results.length}`,
    `route_guards_redirected_to_login=${guardRedirectCount}`,
    `authenticated_business_states_accepted=${manifest.authenticatedBusinessStatesAccepted}`,
    `final_release_signoff_ready=${manifest.finalReleaseSignoffReady}`,
    `dist_index_sha256=${distSha256}`,
    `browser=${browserVersion.Browser || "unknown"}`,
    `protocol=${browserVersion["Protocol-Version"] || "unknown"}`,
    `user_agent=${mobileUserAgent}`,
    "",
    ...captures.map(item => [
      item.image.file,
      `route=${item.route}`,
      `final=${item.finalUrl}`,
      `css=${item.cssViewport.width}x${item.cssViewport.height}`,
      `png=${item.image.width}x${item.image.height}`,
      `dpr=${item.devicePixelRatio}`,
      `overflow=${item.page.horizontalOverflow}`,
      `footer_overlap=${item.page.registerFooterOverlapArea}`,
      `sha256=${item.image.sha256}`
    ].join("\t"))
  ].join("\n") + "\n"
  await fs.promises.writeFile(path.resolve(outputDir, "MANIFEST.txt"), text)
  return manifest
}

async function terminate(child) {
  if (!child || child.killed) return
  child.kill("SIGTERM")
  await Promise.race([
    new Promise(resolve => child.once("exit", resolve)),
    delay(2000)
  ])
  if (child.exitCode === null) child.kill("SIGKILL")
}

async function main() {
  if (!fs.existsSync(distIndexPath)) throw new Error("dist/index.html is missing; run npm run build:prod first")
  if (!fs.existsSync(routeMatrixPath)) throw new Error(`route matrix is missing: ${routeMatrixPath}`)
  const routeMatrix = JSON.parse(await fs.promises.readFile(routeMatrixPath, "utf8"))
  if (!Array.isArray(routeMatrix.businessRoutes) || routeMatrix.businessRoutes.length !== 58) {
    throw new Error("route matrix must contain exactly 58 business routes")
  }

  await fs.promises.rm(outputDir, { recursive: true, force: true })
  await fs.promises.mkdir(outputDir, { recursive: true })

  let server
  let chrome
  let session
  try {
    server = await startDistServer()
    chrome = await startChrome()
    const versionResponse = await fetch(`http://127.0.0.1:${chrome.debugPort}/json/version`)
    const browserVersion = await versionResponse.json()
    session = await openPage(chrome.debugPort)
    const captures = await captureAnonymousPages(session, server.baseUrl)
    const guards = await captureGuardMatrix(session, server.baseUrl, routeMatrix.businessRoutes)
    const reducedMotion = await validateReducedMotion(session, server.baseUrl)
    const distSha256 = sha256(await fs.promises.readFile(distIndexPath))
    const manifest = await writeManifests({
      baseUrl: server.baseUrl,
      browserVersion,
      captures,
      guards,
      reducedMotion,
      distSha256
    })
    if (!manifest.responsiveCaptureValid) {
      throw new Error("runtime evidence was captured, but viewport/overflow/footer validation failed")
    }
    if (!manifest.reducedMotionValid) {
      throw new Error("runtime evidence was captured, but reduced-motion validation failed")
    }
    console.log(`mobile runtime evidence captured: ${outputDir}`)
    console.log(`capture_completed=${manifest.captureCompleted}`)
    console.log(`responsive_capture_valid=${manifest.responsiveCaptureValid}`)
    console.log(`reduced_motion_valid=${manifest.reducedMotionValid}`)
    console.log(`route_guards_redirected_to_login=${manifest.routeGuards.redirectedToLogin}/${manifest.routeGuards.checked}`)
  } finally {
    if (session) session.close()
    if (chrome) {
      await terminate(chrome.child)
      await fs.promises.rm(chrome.profileDir, { recursive: true, force: true })
    }
    if (server) await terminate(server.child)
  }
}

main().catch(error => {
  console.error(error.stack || error.message)
  process.exitCode = 1
})
