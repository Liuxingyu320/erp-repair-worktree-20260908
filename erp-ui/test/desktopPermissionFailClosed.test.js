const assert = require("assert")
const fs = require("fs")
const path = require("path")
const vm = require("vm")
const { compile } = require("vue-template-compiler")

const rootDir = path.resolve(__dirname, "..")

function read(relativePath) {
  return fs.readFileSync(path.resolve(rootDir, relativePath), "utf8")
}

function loadVueComponent(relativePath) {
  const source = read(relativePath)
  const match = source.match(/<script>([\s\S]*?)<\/script>/)
  assert.ok(match, `${relativePath} must contain a script block`)
  const script = match[1]
    .replace(/import\s*\{[\s\S]*?\}\s*from\s*["'][^"']+["']\s*/g, "")
    .replace(/^import .*$/gm, "")
    .replace(/export default/, "module.exports =")
  const sandbox = {
    module: { exports: {} },
    exports: {},
    require: () => ({}),
    process: { env: {} },
    CompanySealManagement: {},
    SignPackageExceptionPanel: {},
    SignPackageRecordPanel: {},
    SignScopeSelector: {},
    templateTypeLabel: value => value,
    Promise,
    Object,
    Array,
    Set,
    String,
    Number,
    Date,
    Math
  }
  sandbox.exports = sandbox.module.exports
  vm.runInNewContext(script, sandbox, { filename: relativePath })
  return sandbox.module.exports
}

function plain(value) {
  return JSON.parse(JSON.stringify(value))
}

function assertSignPackageTemplateIsPermissionGated() {
  const source = read("src/views/oa/signPackage/index.vue")
  const match = source.match(/<template>([\s\S]*?)<\/template>\s*<script>/)
  assert.ok(match, "sign-package page must contain a template")
  const compiled = compile(match[1])
  assert.deepStrictEqual(compiled.errors, [], "sign-package template must compile without errors")

  const root = compiled.ast
  const authorizationGate = (root.children || []).find(node =>
    node.type === 1 && node.tag === "template" && node.if === "pageAuthorized"
  )
  assert.ok(authorizationGate, "sign-package business content must be wrapped by pageAuthorized")

  function containsTag(node, tag, seen = new Set()) {
    if (!node || seen.has(node)) return false
    seen.add(node)
    if (node.type === 1 && node.tag === tag) return true
    return (node.children || []).some(child => containsTag(child, tag, seen)) ||
      (node.ifConditions || []).some(condition => containsTag(condition.block, tag, seen))
  }

  for (const tag of [
    "sign-scope-selector",
    "company-seal-management",
    "sign-package-exception-panel",
    "sign-package-record-panel"
  ]) {
    assert.ok(
      containsTag(authorizationGate, tag),
      `${tag} must not mount outside the page-level authorization gate`
    )
  }
}

function assertPackageRouteIsPermissionGated() {
  const routerSource = read("src/router/index.js")
  const splitMarker = "// 动态路由，基于用户权限动态去加载"
  const markerIndex = routerSource.indexOf(splitMarker)
  assert.ok(markerIndex > 0, "router must retain the dynamic route section")

  const constantSource = routerSource.slice(0, markerIndex)
  const dynamicSource = routerSource.slice(markerIndex)
  assert.ok(
    !constantSource.includes("path: '/oa/sign-task/package'"),
    "sign-package management must not be registered for every authenticated user"
  )
  assert.strictEqual(
    (routerSource.match(/path: '\/oa\/sign-task\/package'/g) || []).length,
    1,
    "sign-package management must have exactly one route registration"
  )
  assert.ok(
    /path: '\/oa\/sign-task\/package'[\s\S]{0,160}permissions: \['oa:signPackage:list'\]/.test(dynamicSource),
    "the hidden sign-package route must require oa:signPackage:list"
  )
}

async function assertSignPackageBootstrapFailsClosed() {
  const component = loadVueComponent("src/views/oa/signPackage/index.vue")

  async function run(auth) {
    const calls = []
    const employeeRequest = Promise.resolve("employees")
    const context = {
      $auth: auth,
      getTemplateTypes() {
        calls.push("templates")
      },
      getList() {
        calls.push("packages")
      },
      loadEmployees() {
        calls.push("employees")
        return employeeRequest
      },
      openPackageFromRoute(request) {
        assert.strictEqual(request, employeeRequest)
        calls.push("deep-link")
      }
    }
    context.hasSignPackagePagePermission =
      component.methods.hasSignPackagePagePermission.bind(context)
    context.bootstrapSignPackagePage =
      component.methods.bootstrapSignPackagePage.bind(context)

    const result = await component.created.call(context)
    return { calls, pageAuthorized: context.pageAuthorized, result }
  }

  for (const [label, auth] of [
    ["missing plugin", undefined],
    ["missing hasPermi", {}],
    ["denied permission", { hasPermi: () => false }],
    ["throwing plugin", { hasPermi: () => { throw new Error("permission state unavailable") } }]
  ]) {
    const outcome = await run(auth)
    assert.deepStrictEqual(
      outcome.calls,
      [],
      `${label} must not initialize templates, packages, employees, or deep links`
    )
    assert.strictEqual(outcome.pageAuthorized, false, `${label} must keep the template gate closed`)
    assert.strictEqual(outcome.result, false, `${label} must fail closed`)
  }

  const allowed = await run({
    hasPermi(permission) {
      return permission === "oa:signPackage:list"
    }
  })
  assert.deepStrictEqual(
    allowed.calls,
    ["templates", "packages", "employees", "deep-link"],
    "authorized users must retain the existing sign-package bootstrap sequence"
  )
  assert.strictEqual(allowed.pageAuthorized, true, "authorized users must open the template gate")
  assert.strictEqual(allowed.result, true)
}

function assertTaskDrawerNavigationFailsClosed() {
  const source = read("src/views/oa/signTask/SignTaskDetailDrawer.vue")
  assert.ok(
    source.includes('v-if="canCreateDraft && canOpenPackageEditor"') &&
      source.includes("task.packageId && canOpenPackageEditor"),
    "draft creation and data completion controls must require list plus add permission"
  )
  assert.ok(
    source.includes("!isSignatureFirstWaitingCompany && canOpenCompanyFinalization"),
    "package company-finalization controls must include the explicit list-plus-send gate"
  )
  assert.ok(
    source.includes('v-if="isSignatureFirstWaitingCompany"') &&
      source.includes("v-hasPermi=\"['oa:signTask:batchFinalize']\""),
    "signature-first company work must retain its independent batch-finalize permission"
  )

  const component = loadVueComponent("src/views/oa/signTask/SignTaskDetailDrawer.vue")

  function createContext(permissions, options = {}) {
    const routes = []
    let closes = 0
    const context = {
      $auth: {
        hasPermi(permission) {
          if (options.throwPermissionError) {
            throw new Error("permission state unavailable")
          }
          return permissions.includes(permission)
        }
      },
      $router: {
        push(route) {
          routes.push(route)
          return Promise.resolve()
        }
      },
      $modal: { msgWarning() {} },
      task: { taskId: 71, packageId: 81 },
      isSignatureFirstWaitingCompany: true,
      close() {
        closes += 1
      }
    }
    context.hasSignPackageNavigationPermissions =
      component.methods.hasSignPackageNavigationPermissions.bind(context)
    return { context, routes, closes: () => closes }
  }

  const addOnly = createContext(["oa:signPackage:add"])
  component.methods.openPackageEditor.call(addOnly.context)
  assert.deepStrictEqual(addOnly.routes, [], "add without list must not open the package editor")
  assert.strictEqual(addOnly.closes(), 0, "a denied editor action must keep the task drawer open")

  const listAndAdd = createContext(["oa:signPackage:list", "oa:signPackage:add"])
  component.methods.openPackageEditor.call(listAndAdd.context)
  assert.deepStrictEqual(plain(listAndAdd.routes), [{
    path: "/oa/sign-task/package",
    query: { packageId: "81" }
  }], "list plus add must retain package-editor navigation")
  assert.strictEqual(listAndAdd.closes(), 1)

  const sendOnly = createContext(["oa:signPackage:send"])
  component.methods.openCompanyFinalization.call(sendOnly.context)
  assert.deepStrictEqual(sendOnly.routes, [], "send without list must not open company finalization")

  const listAndSend = createContext(["oa:signPackage:list", "oa:signPackage:send"])
  component.methods.openCompanyFinalization.call(listAndSend.context)
  assert.deepStrictEqual(plain(listAndSend.routes), [{
    path: "/oa/sign-task/package",
    query: { packageId: "81", action: "finalize" }
  }], "list plus send must retain company-finalization navigation")

  const finalizeWithoutPackageList = createContext(["oa:signTask:batchFinalize"])
  component.methods.openSignatureFirstCompanyWork.call(finalizeWithoutPackageList.context)
  assert.deepStrictEqual(plain(finalizeWithoutPackageList.routes), [{
    path: "/oa/sign-task",
    query: { companyWorkPackageId: "81" }
  }], "signature-first company work must not require the unrelated sign-package list permission")

  const throwing = createContext(
    ["oa:signPackage:list", "oa:signPackage:add"],
    { throwPermissionError: true }
  )
  component.methods.openPackageEditor.call(throwing.context)
  assert.deepStrictEqual(throwing.routes, [],
    "a permission plugin exception must fail closed at the navigation method boundary")
}

function assertOaOverviewFailsClosed() {
  const component = loadVueComponent("src/views/oa/index.vue")
  const modules = component.data().modules

  assert.deepStrictEqual(
    plain(component.computed.visibleModules.call({ modules })),
    [],
    "OA overview must hide every module when the permission plugin is missing"
  )
  assert.deepStrictEqual(
    plain(component.computed.visibleModules.call({ modules, $auth: {} })),
    [],
    "OA overview must hide every module when hasPermi is unavailable"
  )

  let permissionChecks = 0
  const throwingResult = component.computed.visibleModules.call({
    modules,
    $auth: {
      hasPermi() {
        permissionChecks += 1
        if (permissionChecks === 2) {
          throw new Error("permission state unavailable")
        }
        return true
      }
    }
  })
  assert.deepStrictEqual(
    plain(throwingResult),
    [],
    "OA overview must discard partial results when permission evaluation throws"
  )

  const attendanceOnly = component.computed.visibleModules.call({
    modules,
    $auth: {
      hasPermi(permission) {
        return permission === "oa:attendance:center:list"
      }
    }
  })
  assert.deepStrictEqual(
    plain(attendanceOnly.map(module => module.path)),
    ["/oa/attendance-v2"],
    "OA overview must continue to expose only explicitly authorized modules"
  )
}

Promise.resolve()
  .then(assertSignPackageTemplateIsPermissionGated)
  .then(assertPackageRouteIsPermissionGated)
  .then(assertSignPackageBootstrapFailsClosed)
  .then(assertTaskDrawerNavigationFailsClosed)
  .then(assertOaOverviewFailsClosed)
  .then(() => console.log("desktop permission fail-closed tests passed"))
  .catch(error => {
    console.error(error)
    process.exitCode = 1
  })
