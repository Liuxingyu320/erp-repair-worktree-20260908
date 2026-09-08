import store from '@/store'

function authPermission(permission) {
  const all_permission = "*:*:*"
  const permissions = store.getters && Array.isArray(store.getters.permissions)
    ? store.getters.permissions
    : []
  if (permission && permission.length > 0) {
    return permissions.some(v => {
      return all_permission === v || v === permission
    })
  } else {
    return false
  }
}

function authRole(role) {
  const super_admin = "admin"
  const roles = store.getters && Array.isArray(store.getters.roles)
    ? store.getters.roles
    : []
  if (role && role.length > 0) {
    return roles.some(v => {
      return super_admin === v || v === role
    })
  } else {
    return false
  }
}

export default {
  // 验证用户是否具备某权限
  hasPermi(permission) {
    return authPermission(permission)
  },
  // 验证用户是否含有指定权限，只需包含其中一个
  hasPermiOr(permissions) {
    return Array.isArray(permissions) && permissions.length > 0 && permissions.some(item => {
      return authPermission(item)
    })
  },
  // 验证用户是否含有指定权限，必须全部拥有
  hasPermiAnd(permissions) {
    return Array.isArray(permissions) && permissions.length > 0 && permissions.every(item => {
      return authPermission(item)
    })
  },
  // 验证用户是否具备某角色
  hasRole(role) {
    return authRole(role)
  },
  // 验证用户是否含有指定角色，只需包含其中一个
  hasRoleOr(roles) {
    return Array.isArray(roles) && roles.length > 0 && roles.some(item => {
      return authRole(item)
    })
  },
  // 验证用户是否含有指定角色，必须全部拥有
  hasRoleAnd(roles) {
    return Array.isArray(roles) && roles.length > 0 && roles.every(item => {
      return authRole(item)
    })
  }
}
