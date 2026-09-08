import request from '@/utils/request'
import { parseStrEmpty } from "@/utils/common"

// 查询用户列表
export function listUser(query, config) {
  return request({
    url: '/system/user/list',
    method: 'get',
    params: query,
    ...config
  })
}

// 当前数据范围内的账号配置健康汇总
export function getUserSetupSummary(query) {
  return request({
    url: '/system/user/setup-summary',
    method: 'get',
    params: query
  })
}

// 查询用户详细
export function getUser(userId) {
  return request({
    url: '/system/user/' + parseStrEmpty(userId),
    method: 'get'
  })
}

// 受控读取用户个人敏感信息（固定原因码、独立权限和审计）
export function getUserPii(userId, reasonCode) {
  return request({
    url: `/system/user/${userId}/pii`,
    method: 'get',
    params: { reasonCode }
  })
}

// 受控修改用户个人敏感信息；新建后的短窗口使用独立端点
export function updateUserPii(userId, data, reasonCode, afterCreate = false) {
  return request({
    url: `/system/user/${userId}/${afterCreate ? 'pii-after-create' : 'pii'}`,
    method: 'put',
    params: { reasonCode },
    data
  })
}

// 新增用户
export function addUser(data) {
  return request({
    url: '/system/user',
    method: 'post',
    data: data
  })
}

// 修改用户
export function updateUser(data) {
  return request({
    url: '/system/user',
    method: 'put',
    data: data
  })
}

// 仅提交编辑用户时实际发生变化的字段
export function patchUser(userId, data) {
  return request({
    url: `/system/user/${userId}`,
    method: "patch",
    data
  })
}

// 预览根据部门、岗位和日期实时派生的档案字段
export function previewUserDerivedProfile(data) {
  return request({
    url: "/system/user/derived-preview",
    method: "post",
    data
  })
}

// 直属主管等选择器使用的最小用户选项（不返回手机号、证件等敏感字段）
export function listUserOptions(params) {
  return request({
    url: "/system/user/options",
    method: "get",
    params
  })
}

// 删除用户
export function delUser(userId) {
  return request({
    url: '/system/user/' + userId,
    method: 'delete'
  })
}

// 用户密码重置
export function resetUserPwd(userId) {
  const data = { userId }
  return request({
    url: '/system/user/resetPwd',
    method: 'put',
    data: data
  })
}

// 用户状态修改
export function changeUserStatus(userId, status) {
  const data = {
    userId,
    status
  }
  return request({
    url: '/system/user/changeStatus',
    method: 'put',
    data: data
  })
}

// 查询用户个人信息
export function getUserProfile() {
  return request({
    url: '/system/user/profile',
    method: 'get'
  })
}

// 修改用户个人信息
export function updateUserProfile(data) {
  return request({
    url: '/system/user/profile',
    method: 'put',
    data: data
  })
}

// 查询当前账号的登录必填资料完整度
export function getProfileCompletion() {
  return request({
    url: '/system/user/profile/completion',
    method: 'get'
  })
}

// 补全当前账号可自行维护的登录必填资料
export function updateProfileCompletion(data) {
  return request({
    url: '/system/user/profile/completion',
    method: 'put',
    data: data
  })
}

// 用户密码重置
export function updateUserPwd(oldPassword, newPassword) {
  const data = {
    oldPassword,
    newPassword
  }
  return request({
    url: '/system/user/profile/updatePwd',
    method: 'put',
    data: data
  })
}

// 用户头像上传
export function uploadAvatar(data) {
  return request({
    url: '/system/user/profile/avatar',
    method: 'post',
    headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
    data: data
  })
}

// 查询授权角色
export function getAuthRole(userId) {
  return request({
    url: '/system/user/authRole/' + userId,
    method: 'get'
  })
}

// 保存授权角色
export function updateAuthRole(data) {
  return request({
    url: '/system/user/authRole',
    method: 'put',
    params: data
  })
}

// 查询部门下拉树结构
export function deptTreeSelect() {
  return request({
    url: '/system/user/deptTree',
    method: 'get'
  })
}
