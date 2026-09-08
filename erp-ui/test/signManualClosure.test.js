const assert = require('assert')
const fs = require('fs')
const path = require('path')

const uiRoot = path.resolve(__dirname, '..')
const read = relative => fs.readFileSync(path.join(uiRoot, relative), 'utf8')

const drawer = read('src/views/oa/signTask/SignTaskDetailDrawer.vue')
const packagePage = read('src/views/oa/signPackage/index.vue')
const dictionary = read('src/utils/signDictionary.js')

;[
  '当前责任人',
  '下一动作',
  '公司主体就绪',
  '合同印章就绪',
  '最近通知状态',
  '业务状态事件',
  '系统自动处理',
  '无待处理责任人',
  '冻结策略明确无需印章',
  '冻结印章策略缺失（需核查）'
].forEach(text => assert.ok(drawer.includes(text), `task drawer should expose ${text}`))

assert.ok(drawer.indexOf('business-event-history') < drawer.indexOf('v-if="canViewTechnicalEvidence"'),
  'business status events must remain visible without technical-evidence permission')
assert.ok(drawer.includes('Object.freeze({') && drawer.includes('taskMutationReplay') &&
  drawer.includes('notificationMutationReplay') && drawer.includes('使用原请求号重试'),
'task and notification mutations should freeze a replayable request after uncertain responses')

;['SignPackageRecordPanel', 'SignPackageExceptionPanel'].forEach(component => {
  assert.ok(packagePage.includes(component), `${component} should be wired into the package page`)
  assert.ok(fs.existsSync(path.join(uiRoot, `src/views/oa/signPackage/${component}.vue`)),
    `${component}.vue should exist`)
})
assert.ok(dictionary.includes('SIGN_PACKAGE_EVENT_LABELS') &&
  dictionary.includes('PACKAGE_REFUSED') && dictionary.includes('PACKAGE_EXPIRED'),
'terminal package events should use the shared event dictionary')

console.log('sign manual closure tests passed')
