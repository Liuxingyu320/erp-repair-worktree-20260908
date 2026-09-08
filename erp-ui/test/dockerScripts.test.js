const assert = require('assert')
const fs = require('fs')
const path = require('path')

const rootDir = path.resolve(__dirname, '../..')
const copyScript = fs.readFileSync(path.join(rootDir, 'docker/copy.sh'), 'utf8')
const deployScript = fs.readFileSync(path.join(rootDir, 'docker/deploy.sh'), 'utf8')
const mysqlDbDir = path.join(rootDir, 'docker/mysql/db')
const sqlDir = path.join(rootDir, 'sql')
const localSeedSql = path.join(rootDir, 'docker/mysql/seed/00_BossERP_stock_state_75c59ee.sql')
const productionSqlSources = fs.readdirSync(mysqlDbDir)
  .filter(file => file.endsWith('.sql'))
  .map(file => fs.readFileSync(path.join(mysqlDbDir, file), 'utf8'))
  .join('\n')

assert.ok(copyScript.includes('set -eu'), 'copy.sh should fail fast on missing files or failed commands')
assert.ok(copyScript.includes('BASE_DIR='), 'copy.sh should resolve paths relative to the script')
assert.ok(copyScript.includes('copy_sql_files'), 'copy.sh should copy the available sql directory instead of hard-coded old dumps')
assert.ok(!copyScript.includes('ry_20260402.sql'), 'copy.sh should not reference removed ry_20260402.sql')
assert.ok(!copyScript.includes('ry_config_20260311.sql'), 'copy.sh should not reference removed ry_config_20260311.sql')
assert.ok(!copyScript.includes('dist/**'), 'copy.sh should avoid shell-specific recursive globbing')
assert.ok(copyScript.includes('copy_jar "$ROOT_DIR/erp-modules/erp-oa/target/erp-modules-oa.jar"'), 'copy.sh should package the OA module through the canonical JAR guard')
assert.ok(copyScript.includes('copy_jar "$ROOT_DIR/erp-modules/erp-inventory/target/erp-modules-inventory.jar"'), 'copy.sh should package the inventory module through the canonical JAR guard')
assert.ok(deployScript.includes('compose_cmd()'), 'deploy.sh should choose docker compose or docker-compose at runtime')
assert.ok(deployScript.includes('$(compose_cmd) up -d erp-mysql erp-redis erp-nacos'), 'deploy.sh base should use the compose command helper')
assert.ok(deployScript.includes('$(compose_cmd) up -d') && [
  'erp-nginx',
  'erp-gateway',
  'erp-auth',
  'erp-modules-system',
  'erp-modules-oa',
  'erp-modules-inventory',
  'erp-modules-approval'
].every(service => deployScript.includes(service)),
'deploy.sh modules should start every required remediated module through the helper')
assert.ok(!fs.existsSync(path.join(sqlDir, 'backup_inv_management_before_tea_import_20260608.sql')), 'historical inventory backup SQL should not be shipped from sql/')
assert.ok(!fs.existsSync(path.join(mysqlDbDir, 'backup_inv_management_before_tea_import_20260608.sql')), 'historical inventory backup SQL should not be part of MySQL init scripts')
;[
  '测试商品-已修改',
  'TEST-001',
  '校验测试商品',
  'VAL-001',
  'Phase6测试商品'
].forEach(token => {
  assert.ok(!productionSqlSources.includes(token), `production MySQL init SQL should not include ${token}`)
})

if (fs.existsSync(localSeedSql)) {
  const localSeedSource = fs.readFileSync(localSeedSql, 'utf8')
  ;[
    '云岫',
    '青炉',
    'SO-MOBILE',
    'PO-MOBILE',
    'MOBILE-READY',
    '测试商品-已修改',
    'TEST-001',
    '校验测试商品',
    'VAL-001',
    'Phase6测试商品',
    'qa_audit_',
    'ux_store_',
    'ux_warehouse'
  ].forEach(token => {
    assert.ok(!localSeedSource.includes(token), `local ignored seed SQL should not include ${token}`)
  })
}

const composePath = path.join(rootDir, 'docker/docker-compose.yml')
const hostComposePath = path.join(rootDir, 'docker/docker-compose.ecs-host.yml')
const compose = fs.readFileSync(composePath, 'utf8')
const hostCompose = fs.readFileSync(hostComposePath, 'utf8')

const extractService = (source, serviceName) => {
  const marker = `  ${serviceName}:\n`
  const start = source.indexOf(marker)
  assert.notStrictEqual(start, -1, `${serviceName} should exist in Compose`)
  const remaining = source.slice(start + marker.length)
  const nextService = remaining.search(/^  [a-zA-Z0-9][a-zA-Z0-9-]*:\s*$/m)
  return marker + (nextService === -1 ? remaining : remaining.slice(0, nextService))
}

const composeAnchor = compose.slice(0, compose.indexOf('\nservices:'))
const hostComposeAnchor = hostCompose.slice(0, hostCompose.indexOf('\nservices:'))
const composeFileService = extractService(compose, 'erp-modules-file')
const hostComposeFileService = extractService(hostCompose, 'erp-modules-file')

for (const [name, anchor] of [['container Compose', composeAnchor], ['host Compose', hostComposeAnchor]]) {
  assert.ok(anchor.includes('DRIVE_ENABLED: ${DRIVE_ENABLED:-false}'), `${name} should disable cloud drive by default`)
}

for (const [name, service] of [['container file service', composeFileService], ['host file service', hostComposeFileService]]) {
  assert.ok(service.includes('${ERP_UPLOAD_ROOT:?set ERP_UPLOAD_ROOT in .env}:/data/erp-new-data/uploadPath'),
    `${name} should keep uploads in an explicit persistent path outside the release directory`)
  assert.ok(service.includes('FILE_PATH: /data/erp-new-data/uploadPath'),
    `${name} file storage configuration must use the mounted persistent path`)
}

for (const [name, service] of [['container file service', composeFileService], ['host file service', hostComposeFileService]]) {
  assert.ok(service.includes('erp-mysql'), `${name} should depend on MySQL`)

  const expectedEnvironment = [
    'DRIVE_STORAGE_TYPE: ${DRIVE_STORAGE_TYPE:-local}',
    'DRIVE_LOCAL_PATH: /data/erp-new-data/uploadPath/private/drive',
    'DRIVE_MINIO_BUCKET: ${DRIVE_MINIO_BUCKET:-erp-drive-private}',
    'DRIVE_MAX_FILE_SIZE: ${DRIVE_MAX_FILE_SIZE:-104857600}',
    'DRIVE_MAX_REQUEST_SIZE: ${DRIVE_MAX_REQUEST_SIZE:-115343360}',
    'SPRING_SERVLET_MULTIPART_MAX_FILE_SIZE: ${DRIVE_MAX_FILE_SIZE:-104857600}',
    'SPRING_SERVLET_MULTIPART_MAX_REQUEST_SIZE: ${DRIVE_MAX_REQUEST_SIZE:-115343360}',
    'DRIVE_PERSONAL_QUOTA: ${DRIVE_PERSONAL_QUOTA:-2147483648}',
    'DRIVE_DEPARTMENT_QUOTA: ${DRIVE_DEPARTMENT_QUOTA:-21474836480}',
    'DRIVE_COMPANY_QUOTA: ${DRIVE_COMPANY_QUOTA:-107374182400}',
    'DRIVE_TRASH_RETENTION_DAYS: ${DRIVE_TRASH_RETENTION_DAYS:-30}',
    'DRIVE_CLEANUP_CRON: "${DRIVE_CLEANUP_CRON:-0 30 2 * * *}"',
    'DRIVE_CLEANUP_ZONE: ${DRIVE_CLEANUP_ZONE:-Asia/Shanghai}',
    'MINIO_ENABLED: ${MINIO_ENABLED:-false}',
    'MINIO_ENDPOINT: ${MINIO_ENDPOINT:-http://127.0.0.1:9000}',
    'MINIO_ACCESS_KEY: ${MINIO_ACCESS_KEY:-}',
    'MINIO_SECRET_KEY: ${MINIO_SECRET_KEY:-}',
    'MINIO_BUCKET_NAME: ${MINIO_BUCKET_NAME:-erp}'
  ]
  for (const setting of expectedEnvironment) {
    assert.ok(service.includes(setting), `${name} should provide ${setting.split(':')[0]}`)
  }
}

for (const setting of [
  'SPRING_DATASOURCE_DYNAMIC_DATASOURCE_MASTER_URL: ${APP_DATASOURCE_URL:?set APP_DATASOURCE_URL in .env}',
  'SPRING_DATASOURCE_DYNAMIC_DATASOURCE_MASTER_USERNAME: ${APP_DATASOURCE_USERNAME:?set APP_DATASOURCE_USERNAME in .env}',
  'SPRING_DATASOURCE_DYNAMIC_DATASOURCE_MASTER_PASSWORD: ${APP_DATASOURCE_PASSWORD:?set APP_DATASOURCE_PASSWORD in .env}'
]) {
  assert.ok(composeFileService.includes(setting), `container file service should provide ${setting.split(':')[0]}`)
}

const frontendEnvironment = ['.env.development', '.env.staging', '.env.production']
  .map(file => fs.readFileSync(path.join(rootDir, 'erp-ui', file), 'utf8'))
  .join('\n')
assert.ok(!/MYSQL_(?:USERNAME|PASSWORD)|MINIO_(?:ACCESS_KEY|SECRET_KEY)/.test(frontendEnvironment), 'frontend environment files must not contain storage credentials')

for (const nginxFile of ['nginx.conf', 'nginx.host.conf']) {
  const nginx = fs.readFileSync(path.join(rootDir, 'docker/nginx/conf', nginxFile), 'utf8')
  const prodApiLocation = nginx.match(/location\s+\/prod-api\/\s*\{([\s\S]*?)\n\s{8}\}/)
  assert.ok(prodApiLocation, `${nginxFile} should contain the /prod-api/ proxy location`)
  for (const directive of [
    'client_max_body_size 110m;',
    'proxy_request_buffering off;',
    'proxy_read_timeout 300s;',
    'proxy_send_timeout 300s;'
  ]) {
    assert.ok(prodApiLocation[1].includes(directive), `${nginxFile} /prod-api/ should contain ${directive}`)
  }
  assert.ok(!nginx.includes('uploadPath/private/drive'), `${nginxFile} must not expose private drive storage as an alias`)
}

const driveOperationsPath = path.join(rootDir, 'docs/CLOUD_DRIVE_OPERATIONS.md')
assert.ok(fs.existsSync(driveOperationsPath), 'cloud drive operations runbook should exist')
const driveOperations = fs.readFileSync(driveOperationsPath, 'utf8')
for (const contract of [
  'DRIVE_STORAGE_TYPE=minio',
  'MINIO_ENABLED=true',
  'PURGING',
  'drive_node.storage_key',
  'erp-gateway-dev.yml',
  'erp-file-drive-api',
  'erp-file-static',
  '/file/drive/spaces',
  'DRIVE_ENABLED=false'
]) {
  assert.ok(driveOperations.includes(contract), `cloud drive runbook should document ${contract}`)
}
assert.ok(
  driveOperations.includes('docker compose -f "$COMPOSE_FILE" config --quiet'),
  'cloud drive runbook should validate Compose without rendering expanded secrets'
)
assert.ok(
  !driveOperations.includes('/tmp/erp-cloud-drive-compose.rendered.yml'),
  'cloud drive runbook must not persist expanded Compose secrets to a predictable file'
)

const transferApprovalMigrationPath = path.join(rootDir, 'sql/erp_inventory_transfer_auto_hierarchy_approval_20260710.sql')
const dockerTransferApprovalMigrationPath = path.join(rootDir, 'docker/mysql/db/erp_inventory_transfer_auto_hierarchy_approval_20260710.sql')
assert.ok(fs.existsSync(transferApprovalMigrationPath), 'root SQL should contain the transfer hierarchy approval migration')
assert.ok(fs.existsSync(dockerTransferApprovalMigrationPath), 'docker SQL should contain the transfer hierarchy approval migration')

const transferApprovalMigration = fs.readFileSync(transferApprovalMigrationPath, 'utf8')
const dockerTransferApprovalMigration = fs.readFileSync(dockerTransferApprovalMigrationPath, 'utf8')
assert.strictEqual(dockerTransferApprovalMigration, transferApprovalMigration, 'root and docker transfer approval migrations should match')
assert.ok(transferApprovalMigration.includes('tmp_transfer_approval_node_rank'), 'migration should rank existing nodes in a temporary table')
assert.ok(transferApprovalMigration.includes('tmp_transfer_approval_rule_node_count'), 'migration should count existing nodes for every rule')
assert.ok(transferApprovalMigration.includes('sequence_no'), 'migration should identify the first and second node per rule')
assert.ok(transferApprovalMigration.includes("'level4_highest'"), 'migration should mark the first node as level-4 highest leader')
assert.ok(transferApprovalMigration.includes("'level3_highest'"), 'migration should mark the second node as level-3 highest leader')
assert.ok(transferApprovalMigration.includes("'四级负责人（店长/店助）'"), 'migration should describe manager-first level-4 resolution')
assert.ok(transferApprovalMigration.includes("'三级负责人（店长上一级）'"), 'migration should describe the closest upper level-3 resolution')
assert.ok(transferApprovalMigration.includes("'系统自动匹配目标门店岗位审批链'"), 'migration should explain the revised dynamic position chain')
assert.ok(transferApprovalMigration.includes('post_id = null'), 'migration should clear obsolete selected post IDs')
assert.ok(transferApprovalMigration.includes('ranked.sequence_no in (1, 2)'), 'migration should leave nodes after the first two untouched')
assert.ok(transferApprovalMigration.includes('counted.node_count < 2'), 'migration should explicitly handle rules with fewer than two nodes')
assert.ok(transferApprovalMigration.includes('ranked.sequence_no + 2'), 'migration should preserve a lone configured node after the dynamic nodes')
assert.ok(transferApprovalMigration.includes('insert into inv_transfer_approval_node'), 'migration should insert missing dynamic nodes for short rules')
assert.ok(!transferApprovalMigration.toLowerCase().includes('row_number('), 'migration should remain compatible with MySQL 5.7')

const unifiedTodoMigrationPath = path.join(rootDir, 'sql/erp_unified_todo_center_20260710.sql')
const dockerUnifiedTodoMigrationPath = path.join(rootDir, 'docker/mysql/db/erp_unified_todo_center_20260710.sql')
assert.ok(fs.existsSync(unifiedTodoMigrationPath), 'root SQL should contain the unified todo center migration')
assert.ok(fs.existsSync(dockerUnifiedTodoMigrationPath), 'docker SQL should contain the unified todo center migration')

const unifiedTodoMigration = fs.readFileSync(unifiedTodoMigrationPath)
const dockerUnifiedTodoMigration = fs.readFileSync(dockerUnifiedTodoMigrationPath)
assert.deepStrictEqual(dockerUnifiedTodoMigration, unifiedTodoMigration, 'root and docker unified todo migrations should be byte-identical')

const unifiedTodoSql = unifiedTodoMigration.toString('utf8')
const unifiedTodoDefaults = [
  ['todo.approval.urgent.hours', '24'],
  ['todo.contract.warning.days', '30'],
  ['todo.contract.urgent.days', '7'],
  ['todo.summary.recent.limit', '5']
]
for (const [configKey, configValue] of unifiedTodoDefaults) {
  assert.ok(unifiedTodoSql.includes(`'${configKey}'`), `migration should define ${configKey}`)
  assert.ok(unifiedTodoSql.includes(`'${configKey}', '${configValue}'`), `migration should default ${configKey} to ${configValue}`)
}
assert.strictEqual((unifiedTodoSql.match(/where not exists/gi) || []).length, 4, 'migration should guard every default insert')
assert.ok(!unifiedTodoSql.toLowerCase().includes('on duplicate key update'), 'migration should not overwrite existing configuration')
assert.ok(
  unifiedTodoSql.includes('unique_dept.dept_name COLLATE utf8mb4_general_ci = p.store_name COLLATE utf8mb4_general_ci'),
  'legacy HR department backfill should compare names with one explicit collation'
)

console.log('dockerScripts tests passed')
