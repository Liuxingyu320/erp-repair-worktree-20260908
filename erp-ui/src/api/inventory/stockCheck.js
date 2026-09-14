import request from '@/utils/request'

function stockCheckSilentError(config) {
  return !!(config && config.silentError === true)
}

// 查询盘点单列表
export function listStockCheck(query) {
  return request({ url: '/inventory/stockCheck/list', method: 'get', params: query })
}

// 查询盘点单详情
export function getStockCheck(checkId, config) {
  return request({
    url: '/inventory/stockCheck/' + checkId,
    method: 'get',
    silentError: stockCheckSilentError(config)
  })
}

// 新增盘点草稿
export function createStockCheck(data, config) {
  return request({
    url: '/inventory/stockCheck/create',
    method: 'post',
    data: data,
    silentError: stockCheckSilentError(config)
  })
}

// 查询当前库存组织可执行盘点的人员
export function listStockCheckCounterCandidates(params) {
  return request({ url: '/inventory/stockCheck/counter-candidates', method: 'get', params })
}

// 调整盘点责任人与截止时间
export function assignStockCheck(checkId, data) {
  return request({ url: '/inventory/stockCheck/' + checkId + '/assignment', method: 'put', data })
}

// 录入实盘数量
export function inputStockCheck(checkId, data, config) {
  return request({
    url: '/inventory/stockCheck/input/' + checkId,
    method: 'post',
    data: data,
    silentError: stockCheckSilentError(config)
  })
}

// 提交盘点
export function submitStockCheck(checkId, data, config) {
  return request({
    url: '/inventory/stockCheck/submit/' + checkId,
    method: 'post',
    data: data,
    silentError: stockCheckSilentError(config)
  })
}

// 库存快照失效后重新盘点
export function restartStockCheck(checkId) {
  return request({ url: '/inventory/stockCheck/restart/' + checkId, method: 'post' })
}

// 查询待我审批盘点
export function listStockCheckApprovalTodos(query) {
  return request({ url: '/inventory/stockCheck/approval/todo', method: 'get', params: query })
}

// 审批通过
export function approveStockCheck(checkId, data) {
  return request({ url: '/inventory/stockCheck/approval/' + checkId + '/approve', method: 'post', data: data })
}

// 审批驳回
export function rejectStockCheck(checkId, data) {
  return request({ url: '/inventory/stockCheck/approval/' + checkId + '/reject', method: 'post', data: data })
}

// 查询审批轨迹
export function getStockCheckApprovalTrack(checkId) {
  return request({ url: '/inventory/stockCheck/' + checkId + '/approval-track', method: 'get' })
}

// 取消盘点单
export function cancelStockCheck(checkId) {
  return request({ url: '/inventory/stockCheck/cancel/' + checkId, method: 'post' })
}

// 申请人撤回统一审批；业务状态由审批回调异步更新
export function withdrawStockCheck(checkId) {
  return request({ url: '/inventory/stockCheck/' + checkId + '/withdraw', method: 'post' })
}

// 删除盘点草稿
export function deleteStockCheck(checkIds) {
  return request({ url: '/inventory/stockCheck/' + checkIds, method: 'delete' })
}
