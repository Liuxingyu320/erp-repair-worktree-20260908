const assert = require('assert')
const fs = require('fs')
const path = require('path')

const repoRoot = path.resolve(__dirname, '../..')
const read = relative => fs.readFileSync(path.resolve(repoRoot, relative), 'utf8')

const operView = read('erp-ui/src/views/system/operlog/index.vue')
const operApi = read('erp-ui/src/api/system/operlog.js')
const loginView = read('erp-ui/src/views/system/logininfor/index.vue')
const loginApi = read('erp-ui/src/api/system/logininfor.js')
const operMapper = read('erp-modules/erp-system/src/main/resources/mapper/system/SysOperLogMapper.xml')
const loginMapper = read('erp-modules/erp-system/src/main/resources/mapper/system/SysLogininforMapper.xml')
const loginController = read('erp-modules/erp-system/src/main/java/com/erp/system/controller/SysLogininforController.java')
const logAnnotation = read('erp-common/erp-common-log/src/main/java/com/erp/common/log/annotation/Log.java')
const logAspect = read('erp-common/erp-common-log/src/main/java/com/erp/common/log/aspect/LogAspect.java')
const migration = read('sql/erp_system_management_hardening_20260713.sql')
const migrationMirrorPath = path.resolve(repoRoot, 'docker/mysql/db/erp_system_management_hardening_20260713.sql')

const listSelectMatch = operMapper.match(/<select id="selectOperLogSummaryList"[\s\S]*?<\/select>/)
assert(listSelectMatch, 'operation log mapper must expose the summary-only list query')
const listSelect = listSelectMatch[0]

assert(
  listSelect.includes('resultMap="SysOperLogListResult"') && !listSelect.includes('selectOperLogVo'),
  'operation log list must select the summary projection instead of request/response/error bodies'
)
assert(
  operView.includes("v-hasPermi=\"['system:operlog:detail']\"") &&
    operView.includes('getOperlog(row.operId)') &&
    operApi.includes("url: '/system/operlog/' + operId"),
  'operation log bodies must be fetched on demand behind the dedicated detail permission'
)

assert(
  !operView.includes('handleClean') && !operApi.includes('cleanOperlog') &&
    !loginView.includes('handleClean') && !loginApi.includes('cleanLogininfor'),
  'desktop audit pages must not expose all-record clear operations'
)
assert(
  !operMapper.toLowerCase().includes('truncate table') &&
    !loginMapper.toLowerCase().includes('truncate table'),
  'audit mappers must not retain TRUNCATE statements'
)

assert(
  loginApi.includes("url: '/system/logininfor/lock-state'") &&
    loginApi.includes("method: 'post'") &&
    loginApi.includes("encodeURIComponent(userName) + '/unlock'") &&
    !loginApi.includes("url: '/system/logininfor/unlock/'"),
  'the current UI must check real-time lock state and mutate it only through POST'
)
assert(
  loginView.includes('unlockDisabled') &&
    loginView.includes('lockState.locked') &&
    loginView.includes('fetchLockState(this.selectName') &&
    loginView.includes('selection.length === 1 ? selection[0].userName : ""'),
  'unlock must be enabled only for one selected account whose live state is locked'
)
assert(
  loginController.includes('@PostMapping("/{userName}/unlock")') &&
    loginController.includes('@IdempotentSubmit(timeout = 5)') &&
    loginController.includes('@Deprecated(forRemoval = true)') &&
    loginController.includes('checkUserDataScope(targetUser.getUserId())'),
  'backend unlock must use POST, idempotency, scoped target checks, and a marked compatibility endpoint'
)

assert(
  logAnnotation.includes('isSaveResponseData() default false') &&
    logAspect.includes('auditPayloadSanitizer.sanitizeAllowedFields') &&
    !logAspect.includes('参数:{}'),
  'operation logging must default to no response body and never fall back to raw argument logging'
)

assert(!fs.existsSync(migrationMirrorPath), 'manual system management migration must stay out of automatic Docker bootstrap')
assert(
  migration.includes('CREATE TABLE IF NOT EXISTS sys_audit_archive_batch') &&
    migration.includes("'sys.audit.retention.enabled', 'false'") &&
    migration.includes("'system:operlog:detail'") &&
    migration.includes("'system:user:authRole'") &&
    migration.includes("'system:role:authUser'") &&
    migration.includes("'system:config:refresh'") &&
    migration.includes("'system:dict:refresh'") &&
    !migration.toLowerCase().includes('truncate table'),
  'migration must establish archive governance and dedicated high-risk permissions without destructive clear SQL'
)

console.log('systemAuditGovernance assertions passed')
