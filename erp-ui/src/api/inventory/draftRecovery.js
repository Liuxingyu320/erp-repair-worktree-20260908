import request from '@/utils/request'
import store from '@/store'
import { getSelectedInventoryDeptId, getSelectedDeptId } from '@/utils/shopContext'
const { createInventoryDraftRecovery, createIndexedDbReceiveStore } = require('@/utils/inventoryDraftRecovery')
const context = feature => ({ actor: store.getters.id, dept: feature === 'salesReturn' ? getSelectedDeptId() : getSelectedInventoryDeptId() })
let scopeGeneration = 0
if (typeof window !== 'undefined') window.addEventListener('erp:dept-changed', () => { scopeGeneration += 1 })
export const inventoryDraftRecovery = createInventoryDraftRecovery({
  context,
  contextRevision: () => JSON.stringify([scopeGeneration, store.state.user.sessionRevision]),
  storage: createIndexedDbReceiveStore(typeof indexedDB === 'undefined' ? null : indexedDB, 'erp-inventory-drafts-v1'),
  createId: () => {
    if (typeof crypto === 'undefined' || !crypto.getRandomValues) throw new Error('浏览器无法生成安全操作标识')
    return 'draft-' + Array.from(crypto.getRandomValues(new Uint8Array(16)), value => value.toString(16).padStart(2, '0')).join('')
  },
  transport: (record, assertCurrent) => {
    const assertScope = () => { assertCurrent(); const latest = context(record.feature); if (String(latest.actor) !== record.scope.actor || String(latest.dept) !== record.scope.dept) throw new Error('账号或组织已变化，请核对原操作') }
    assertScope()
    return request({
      url: '/inventory/' + record.feature + '/' + record.action, method: 'post', data: record.payload,
      inventoryDeptId: record.scope.dept, silentError: true,
      headers: { 'X-Request-Id': record.requestId, repeatSubmit: false, 'Content-Type': 'application/json' },
      transformRequest: [body => { assertScope(); return typeof body === 'string' ? body : JSON.stringify(body) }]
    })
  }
})

const submitDraft = inventoryDraftRecovery.submit.bind(inventoryDraftRecovery)
inventoryDraftRecovery.submit = (...args) => submitDraft(...args).finally(() => {
  if (typeof window !== 'undefined') window.dispatchEvent(new Event('erp:draft-recovery-changed'))
})
