const assert = require('assert/strict')
const fs = require('fs')
const path = require('path')
const vm = require('vm')
const { spawnSync } = require('child_process')

if (process.argv[2] !== '--case') {
  for (const TZ of ['Asia/Shanghai', 'Asia/Tokyo', 'UTC', 'America/Los_Angeles']) {
    const child = spawnSync(process.execPath, [__filename, '--case'], { encoding: 'utf8', env: { ...process.env, TZ } })
    assert.equal(child.status, 0, `${TZ}: ${child.stdout}${child.stderr}`)
  }
  console.log('attendance capture time tests passed across four timezones')
} else {
  const instant = Date.parse('2026-09-09T01:00:00Z')
  Date.now = () => instant
  const dir = path.resolve(__dirname, '../src/views/mobile/attendance')
  const source = fs.readFileSync(path.join(dir, 'index.vue'), 'utf8').match(/<script>([\s\S]*?)<\/script>/)[1]
    .replace(/import\s+[\s\S]*?\s+from\s+['"][^'"]+['"]\s*/g, '')
    .replace('export default', 'module.exports =')
  let response = { challengeToken: 'a'.repeat(64), scheduleId: 31, punchType: 'IN',
    expiresAt: '2026-09-09T09:03:00', expiresAtUtc: '2026-09-09T01:03:00Z' }
  const sandbox = { module: { exports: {} }, require: id => require(path.resolve(dir, id)),
    Date, Blob, File, Promise, process: { env: {} },
    createPunchChallenge: async () => ({ data: response }),
    getSelectedDeptContext: () => ({ deptId: 101 }),
    MobileAttendanceCorrection: {}, MobileAttendanceLeave: {},
    Capacitor: { isNativePlatform: () => false },
    URL: { createObjectURL: () => 'blob:test-photo', revokeObjectURL() {} } }
  vm.runInNewContext(source, sandbox)
  const component = sandbox.module.exports
  const page = { $store: { getters: { id: 9 } }, $refs: {} }
  for (const [name, method] of Object.entries(component.methods)) page[name] = method.bind(page)
  Object.assign(page, component.data.call(page))
  page.gate = { ready: true, scheduleId: 31, punchType: 'IN' }
  let locationCalls = 0
  page.getHighAccuracyPosition = async () => { locationCalls++; return { latitude: 30, longitude: 120, accuracy: 10 } }
  ;(async () => {
    await page.preparePunch()
    for (let i = 0; i < 6; i++) await Promise.resolve()
    assert.equal(page.pending.expiresAt, response.expiresAtUtc, 'new challenges must use their absolute UTC expiry')
    assert.equal(page.challengeExpired(), false)
    page.acceptPhoto(new File(['fresh photo'], 'camera.jpg', { type: 'image/jpeg', lastModified: instant }))
    assert.equal(page.photoCapturedAt, '2026-09-09T01:00:00.000Z', 'same instant must produce identical upload time')
    Date.now = () => instant + 181000
    assert.equal(page.challengeExpired(), true)
    Date.now = () => instant
    response = { ...response, expiresAtUtc: undefined }
    page.resetPendingCapture()
    await page.preparePunch()
    for (let i = 0; i < 6; i++) await Promise.resolve()
    assert.equal(locationCalls, 1, 'an old server without an absolute expiry must stop before capture')
    assert.equal(page.pending.challengeToken, '')
    assert.match(page.flowError, /时间/)
  })().catch(error => { console.error(error); process.exitCode = 1 })
}
