import request from '@/utils/request'

// 查询字典类型列表
export function listType(query, options) {
  return request({
    silentError: !!(options && options.silentError === true),
    url: '/system/dict/type/list',
    method: 'get',
    params: query
  })
}

// 查询字典类型详细
export function getType(dictId, options) {
  return request({
    silentError: !!(options && options.silentError === true),
    url: '/system/dict/type/' + dictId,
    method: 'get'
  })
}

// 新增字典类型
export function addType(data, options) {
  return request({
    silentError: !!(options && options.silentError === true),
    url: '/system/dict/type',
    method: 'post',
    data: data
  })
}

// 修改字典类型
export function updateType(data, options) {
  return request({
    silentError: !!(options && options.silentError === true),
    url: '/system/dict/type',
    method: 'put',
    data: data
  })
}

// 删除字典类型
export function delType(dictId, options) {
  return request({
    silentError: !!(options && options.silentError === true),
    url: '/system/dict/type/' + dictId,
    method: 'delete'
  })
}

// 刷新字典缓存
export function refreshCache() {
  return request({
    url: '/system/dict/type/refreshCache',
    method: 'delete'
  })
}

// 获取字典选择框列表
export function optionselect(options) {
  return request({
    silentError: !!(options && options.silentError === true),
    url: '/system/dict/type/optionselect',
    method: 'get'
  })
}