import request from '@/utils/request'

// 查询调拨审批规则列表
export function listTransferApprovalRule(query) {
  return request({ url: '/inventory/transfer/rule/list', method: 'get', params: query })
}

// 查询调拨审批规则详情
export function getTransferApprovalRule(ruleId) {
  return request({ url: '/inventory/transfer/rule/' + ruleId, method: 'get' })
}

// 新增调拨审批规则
export function addTransferApprovalRule(data) {
  return request({ url: '/inventory/transfer/rule', method: 'post', data })
}

// 修改调拨审批规则
export function updateTransferApprovalRule(data) {
  return request({ url: '/inventory/transfer/rule', method: 'put', data })
}

// 删除调拨审批规则
export function delTransferApprovalRule(ruleId, expectedVersion) {
  return request({
    url: '/inventory/transfer/rule/' + ruleId,
    method: 'delete',
    params: { expectedVersion }
  })
}

// 预览调拨审批规则匹配结果
export function previewTransferApprovalRule(data) {
  return request({ url: '/inventory/transfer/rule/preview', method: 'post', data })
}

// 保存前校验冲突、优先级、样例命中和审批候选人
export function validateTransferApprovalRule(data) {
  return request({ url: '/inventory/transfer/rule/validate', method: 'post', data })
}

// 按目标门店预览四级审批候选人
export function previewTransferApprovalCandidates(ruleId, targetDeptId) {
  return request({
    url: '/inventory/transfer/rule/' + ruleId + '/candidate-preview',
    method: 'post',
    params: { targetDeptId }
  })
}
