const assert = require("assert")
const childProcess = require("child_process")
const fs = require("fs")
const path = require("path")

const rootDir = path.resolve(__dirname, "../..")
const inventoryBootstrapPath = path.join(
  rootDir,
  "erp-modules/erp-inventory/src/main/resources/bootstrap.yml"
)
const gatewayBootstrapPath = path.join(rootDir, "erp-gateway/src/main/resources/bootstrap.yml")
const stackScriptPath = path.join(rootDir, "scripts/local-todo-stack.sh")
const runbookPath = path.join(rootDir, "docs/本机待办联调运行手册-20260713.md")

assert.ok(fs.existsSync(inventoryBootstrapPath), "inventory bootstrap configuration must exist")
assert.ok(fs.existsSync(gatewayBootstrapPath), "gateway bootstrap configuration must exist")
assert.ok(fs.existsSync(stackScriptPath), "local todo stack script must exist")
assert.ok(fs.existsSync(runbookPath), "local todo stack runbook must exist")

const inventoryBootstrap = fs.readFileSync(inventoryBootstrapPath, "utf8")
const localProfileStart = inventoryBootstrap.indexOf("on-profile: local")
assert.notStrictEqual(localProfileStart, -1, "inventory bootstrap must retain a local profile")
const localProfile = inventoryBootstrap.slice(localProfileStart)
assert.ok(
  /instances:\s*[\s\S]*?erp-system:\s*\n\s*- uri: http:\/\/127\.0\.0\.1:9201/.test(localProfile),
  "inventory local discovery must resolve erp-system on 127.0.0.1:9201"
)

const stackScript = fs.readFileSync(stackScriptPath, "utf8")
assert.ok(stackScript.includes("set -Eeuo pipefail"), "stack script must fail safely")
assert.ok(
  stackScript.includes("output/local-todo-stack"),
  "PID and log files must stay in the ignored local runtime directory"
)

for (const [service, variable, environmentName, port] of [
  ["system", "SYSTEM_PORT", "LOCAL_TODO_SYSTEM_PORT", "9201"],
  ["auth", "AUTH_PORT", "LOCAL_TODO_AUTH_PORT", "9200"],
  ["oa", "OA_PORT", "LOCAL_TODO_OA_PORT", "9204"],
  ["inventory", "INVENTORY_PORT", "LOCAL_TODO_INVENTORY_PORT", "9205"],
  ["gateway", "GATEWAY_PORT", "LOCAL_TODO_GATEWAY_PORT", "8080"],
  ["ui", "UI_PORT", "LOCAL_TODO_UI_PORT", "1025"]
]) {
  assert.ok(
    stackScript.includes(`${variable}=\"\${${environmentName}:-${port}}\"`),
    `${service} must retain ${port} as its environment-overridable default port`
  )
  assert.ok(
    stackScript.includes(`${service}) printf '%s\\n' \"$${variable}\"`),
    `${service} must resolve its configured local port`
  )
}

assert.ok(
  stackScript.includes('local-todo-stack-$PORT_PROFILE'),
  "alternate port sets must isolate PID and log ownership from the default runtime"
)
assert.ok(
  stackScript.includes('LOCAL_TODO_GATEWAY_URL="http://127.0.0.1:$GATEWAY_PORT"'),
  "the alternate UI must proxy to the matching gateway port"
)
assert.ok(
  stackScript.includes('"--server.port=$port"'),
  "every Java service must bind its selected local port"
)
assert.ok(
  stackScript.includes("'--security.captcha.enabled=false'"),
  "the isolated local gateway must make automated login acceptance deterministic"
)

const gatewayBootstrap = fs.readFileSync(gatewayBootstrapPath, "utf8")
for (const [environmentName, port] of [
  ["LOCAL_TODO_AUTH_PORT", "9200"],
  ["LOCAL_TODO_SYSTEM_PORT", "9201"],
  ["LOCAL_TODO_OA_PORT", "9204"],
  ["LOCAL_TODO_INVENTORY_PORT", "9205"]
]) {
  assert.ok(
    gatewayBootstrap.includes(`http://127.0.0.1:\${${environmentName}:${port}}`),
    `gateway local route must resolve ${environmentName} with default ${port}`
  )
}
assert.ok(
  gatewayBootstrap.includes("http://localhost:${LOCAL_TODO_UI_PORT:1025}"),
  "gateway local CORS must follow the selected UI port"
)

for (const jarName of [
  "erp-modules-system.jar",
  "erp-auth.jar",
  "erp-modules-oa.jar",
  "erp-modules-inventory.jar",
  "erp-gateway.jar"
]) {
  assert.ok(stackScript.includes(jarName), `stack script must use the exact ${jarName} artifact`)
}

assert.ok(
  stackScript.includes(
    "/Users/liuxingyu/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/bin/node"
  ),
  "front-end runtime must use the pinned native Node binary"
)
assert.ok(
  stackScript.includes("node_modules/@vue/cli-service/bin/vue-cli-service.js"),
  "front-end process identity must include the exact Vue CLI path"
)

for (const forbidden of ["pkill", "killall", "docker"]) {
  assert.ok(!stackScript.includes(forbidden), `stack script must not contain ${forbidden}`)
}

const processCheck = stackScript.indexOf('ps -p "$pid" -o command=')
const gracefulStop = stackScript.indexOf('kill -TERM "$pid"')
assert.ok(processCheck >= 0, "stop logic must inspect the exact PID command")
assert.ok(gracefulStop > processCheck, "stop logic must verify process identity before termination")
assert.ok(
  stackScript.includes("STARTED_THIS_RUN") && stackScript.includes("cleanup_started_this_run"),
  "partial startup failures must only clean up processes started in the current run"
)
assert.ok(
  stackScript.includes('-newer "$jar"') && stackScript.includes("JAR 早于当前源码或构建配置"),
  "preflight must reject stale runtime artifacts before acceptance"
)

for (const command of ["preflight", "start", "status", "health", "logs", "restart", "stop"]) {
  assert.ok(stackScript.includes(`${command})`), `stack script must expose the ${command} command`)
}

for (const endpoint of [
  "/actuator/health",
  "/inventory/todo/summary",
  "/oa/todo/summary",
  "/system/todo/summary"
]) {
  assert.ok(stackScript.includes(endpoint), `health checks must include ${endpoint}`)
}
assert.ok(
  stackScript.includes("TODO_GATEWAY_BEARER_TOKEN"),
  "authenticated business probes must read credentials from an environment variable"
)

const redactionProbe = childProcess.spawnSync(
  "bash",
  [
    "-c",
    '. "$1"; printf \'%s\\n\' "--spring.datasource.password=secret-1 --api-token secret-2 Authorization:Bearer-secret-3 Cookie=session-secret" | redact_log_stream',
    "local-runtime-redaction",
    stackScriptPath
  ],
  { encoding: "utf8" }
)
assert.strictEqual(redactionProbe.status, 0, redactionProbe.stderr)
for (const secret of ["secret-1", "secret-2", "Bearer-secret-3", "session-secret"]) {
  assert.ok(!redactionProbe.stdout.includes(secret), `runtime diagnostics must redact ${secret}`)
}
assert.ok(
  (redactionProbe.stdout.match(/\[REDACTED\]/g) || []).length >= 4,
  "runtime diagnostics must redact assignment, separated CLI, authorization, and cookie values"
)

const alternatePorts = {
  LOCAL_TODO_SYSTEM_PORT: "19201",
  LOCAL_TODO_AUTH_PORT: "19200",
  LOCAL_TODO_OA_PORT: "19204",
  LOCAL_TODO_INVENTORY_PORT: "19205",
  LOCAL_TODO_GATEWAY_PORT: "18080",
  LOCAL_TODO_UI_PORT: "11025"
}
const alternatePortProbe = childProcess.spawnSync(
  "bash",
  [
    "-c",
    '. "$1"; validate_configuration; for service in system auth oa inventory gateway ui; do service_port "$service"; done; printf \'%s\\n\' "$RUNTIME_DIR"',
    "local-runtime-alternate-ports",
    stackScriptPath
  ],
  { encoding: "utf8", env: { ...process.env, ...alternatePorts } }
)
assert.strictEqual(alternatePortProbe.status, 0, alternatePortProbe.stderr)
assert.deepStrictEqual(
  alternatePortProbe.stdout.trim().split("\n").slice(0, 6),
  ["19201", "19200", "19204", "19205", "18080", "11025"],
  "all six services must resolve the selected alternate port set"
)
assert.ok(
  alternatePortProbe.stdout.includes("local-todo-stack-19201-19200-19204-19205-18080-11025"),
  "alternate ports must use a distinct runtime directory"
)

const duplicatePortProbe = childProcess.spawnSync(
  "bash",
  ["-c", '. "$1"; validate_configuration', "local-runtime-invalid-ports", stackScriptPath],
  {
    encoding: "utf8",
    env: { ...process.env, LOCAL_TODO_SYSTEM_PORT: "19201", LOCAL_TODO_AUTH_PORT: "19201" }
  }
)
assert.notStrictEqual(duplicatePortProbe.status, 0, "duplicate service ports must fail closed")
assert.ok(duplicatePortProbe.stderr.includes("端口必须互不相同"))

const stopRuntimeDir = fs.mkdtempSync(path.join(require("os").tmpdir(), "local-todo-stop-test-"))
try {
  const stopOneProbe = childProcess.spawnSync(
    "bash",
    [
      "-c",
      '. "$1"; RUNTIME_DIR="$2"; main stop inventory',
      "local-runtime-stop-one",
      stackScriptPath,
      stopRuntimeDir
    ],
    { encoding: "utf8", env: { ...process.env, ...alternatePorts } }
  )
  assert.strictEqual(stopOneProbe.status, 0, stopOneProbe.stderr)
  assert.ok(stopOneProbe.stdout.includes("erp-inventory 没有 PID 文件"),
    "stop <service> must be idempotent when the selected service is already stopped")
  for (const unrelated of ["erp-system", "erp-auth", "erp-oa", "erp-gateway", "erp-ui"]) {
    assert.ok(!stopOneProbe.stdout.includes(`${unrelated} 没有 PID 文件`),
      `stop inventory must not attempt to stop ${unrelated}`)
  }

  const unmanagedPortProbe = childProcess.spawnSync(
    "bash",
    [
      "-c",
      '. "$1"; RUNTIME_DIR="$2"; port_is_open(){ return 0; }; print_port_owners(){ :; }; main stop inventory',
      "local-runtime-stop-unmanaged",
      stackScriptPath,
      stopRuntimeDir
    ],
    { encoding: "utf8", env: { ...process.env, ...alternatePorts } }
  )
  assert.strictEqual(unmanagedPortProbe.status, 1,
    "stop <service> must refuse when the port is open without an owned PID file")
  assert.ok(unmanagedPortProbe.stderr.includes("没有 PID 文件但端口"),
    "unmanaged port refusal must explain why no stop signal was sent")

  const invalidStopProbe = childProcess.spawnSync(
    "bash",
    [
      "-c",
      '. "$1"; RUNTIME_DIR="$2"; main stop unknown-provider',
      "local-runtime-stop-invalid",
      stackScriptPath,
      stopRuntimeDir
    ],
    { encoding: "utf8", env: { ...process.env, ...alternatePorts } }
  )
  assert.strictEqual(invalidStopProbe.status, 2,
    "stop with an unknown service must fail closed with usage status 2")
  assert.ok(invalidStopProbe.stderr.includes("必须指定有效服务"),
    "unknown stop targets must report the valid service set")
} finally {
  fs.rmSync(stopRuntimeDir, { recursive: true, force: true })
}

const runbook = fs.readFileSync(runbookPath, "utf8")
for (const command of [
  "preflight",
  "start",
  "status",
  "health",
  "logs inventory",
  "restart inventory",
  "stop inventory",
  "stop"
]) {
  assert.ok(runbook.includes(`local-todo-stack.sh ${command}`), `runbook must document ${command}`)
}
assert.ok(runbook.includes("不使用虚拟机"), "runbook must state the native runtime boundary")
assert.ok(runbook.includes("TODO_GATEWAY_BEARER_TOKEN"), "runbook must document the optional business probe token")
assert.ok(runbook.includes("LOCAL_TODO_GATEWAY_PORT"), "runbook must document alternate local ports")

console.log("local todo native runtime contract tests passed")
