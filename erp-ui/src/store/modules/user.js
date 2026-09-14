import store from '@/store'
import router from '@/router'
import cache from '@/plugins/cache'
import { MessageBox } from '@/plugins/element-services'
import { login, logout, getInfo, refreshToken } from '@/api/login'
import { getToken, setToken, setExpiresIn, removeToken, removeExpiresIn } from '@/utils/auth'
import { clearSelectedDept } from '@/utils/shopContext'
import { clearSelectedSignScope } from '@/utils/signScopeContext'
import {
  getPasswordResetRoute,
  resetPasswordResetReminderState,
  setPendingPasswordResetReminder,
  showPendingPasswordResetReminderIfReady
} from '@/utils/passwordResetReminder'
import { isEmpty } from "@/utils/validate"
import { clearMobileHrQueueStateCache } from '@/utils/mobileHrQueueState'
import defAva from '@/assets/images/profile.jpg'
import pushRegistration from '@/services/lazyPushRegistration'
import {
  isCookiePreferredSession,
  setWebSessionStatus,
  subscribeWebSessionStatus
} from '@/utils/sessionMode'

const defaultBusinessFeatures = () => ({
  attendanceV2: false,
  healthCertificate: false,
  storeReturn: false,
  transferDiscrepancy: false,
  customerServiceCard: false,
  systemManagementUxV2: false,
  noticeWorkflow: false
})

const normalizeBusinessFeatures = features => Object.keys(defaultBusinessFeatures()).reduce((result, key) => {
  result[key] = !!features && features[key] === true
  return result
}, {})

const isCredentialChangeRequired = state => state === 'TEMPORARY' || state === 'CHANGE_REQUIRED'

let credentialRedirectPending = false
const SESSION_CLEANUP_TIMEOUT_MS = 1500

function settleBestEffort(task) {
  return new Promise(resolve => {
    let settled = false
    const finish = () => {
      if (settled) return
      settled = true
      clearTimeout(timeoutId)
      resolve()
    }
    const timeoutId = setTimeout(finish, SESSION_CLEANUP_TIMEOUT_MS)
    try {
      Promise.resolve(task()).then(finish, finish)
    } catch (e) {
      finish()
    }
  })
}

function clearLocalSession(commit, options = {}) {
  // This boundary runs after confirmed Cookie logout, or local credential invalidation.
  // Never unlock while an unconfirmed Cookie logout is still pending/failed.
  try {
    void Promise.resolve(store.dispatch('lock/unlockScreen')).catch(() => {})
  } catch (e) {
    // Optional persistence failures must not prevent credential cleanup.
  }
  clearSessionContextsBestEffort()
  commit('SET_TOKEN', '')
  commit('SET_EXPIRES_IN', '')
  commit('SET_ID', '')
  commit('SET_DEPT_ID', '')
  commit('SET_NAME', '')
  commit('SET_NICK_NAME', '')
  commit('SET_AVATAR', '')
  commit('SET_ROLES', [])
  commit('SET_PERMISSIONS', [])
  commit('SET_DRIVE_ENABLED', false)
  commit('SET_BUSINESS_FEATURES', null)
  commit('SET_PROFILE_COMPLETION_REQUIRED', false)
  commit('SET_PROFILE_MISSING_FIELDS', [])
  commit('SET_CREDENTIAL_STATE', 'ACTIVE')
  commit('SET_TEMPORARY_PASSWORD_EXPIRES_AT', '')
  commit('SET_CREDENTIAL_BUSINESS_CODE', '')
  commit('SET_SYSTEM_BUILD', null)
  removeToken()
  removeExpiresIn()
  if (options.updateWebStatus !== false) {
    setWebSessionStatus('anonymous', options.broadcast !== false)
  }
}

function clearSelectedDeptBestEffort() {
  try {
    clearSelectedDept()
  } catch (e) {
    // Organization storage failures must never block an authentication boundary.
  }
}

function clearSelectedSignScopeBestEffort() {
  try {
    clearSelectedSignScope()
  } catch (e) {
    // Signing-scope storage failures must never block authentication teardown.
  }
}

function clearSessionContextsBestEffort() {
  clearSelectedDeptBestEffort()
  clearSelectedSignScopeBestEffort()
}

function isUnauthorizedError(error) {
  const code = error && (error.code ||
    (error.response && (error.response.status ||
      (error.response.data && error.response.data.code))))
  return Number(code) === 401
}

function runRequiredWithTimeout(task, timeoutMs = 12000) {
  return new Promise((resolve, reject) => {
    let settled = false
    const finish = (handler, value) => {
      if (settled) return
      settled = true
      clearTimeout(timeoutId)
      handler(value)
    }
    const timeoutId = setTimeout(() => {
      const error = new Error('退出请求超时，暂时无法确认退出结果，请检查网络后重试。')
      error.code = 'COOKIE_LOGOUT_TIMEOUT'
      finish(reject, error)
    }, timeoutMs)
    try {
      Promise.resolve(task()).then(
        value => finish(resolve, value),
        error => finish(reject, error)
      )
    } catch (error) {
      finish(reject, error)
    }
  })
}

function cookieLogoutFailureMessage(error) {
  return error && error.code === 'COOKIE_LOGOUT_TIMEOUT'
    ? error.message
    : '暂时无法确认服务器已完成退出，当前登录状态已保留。请检查网络后重试，或关闭页面后联系管理员。'
}

function notifyCookieLogoutFailure(error) {
  const message = cookieLogoutFailureMessage(error)
  if (MessageBox && typeof MessageBox.alert === 'function') {
    void Promise.resolve(MessageBox.alert(message, '退出失败', {
      confirmButtonText: '我知道了',
      type: 'error'
    })).catch(() => {})
  }
}

function logoutSessionSnapshot(state) {
  return { sessionRevision: state.sessionRevision || 0, userId: String(state.id || ''), token: state.token || '' }
}

function requireLogoutSession(state, expected) {
  const current = logoutSessionSnapshot(state)
  if (current.sessionRevision !== expected.sessionRevision || current.userId !== expected.userId || current.token !== expected.token) {
    const error = new Error('登录会话已变化，已忽略之前的退出结果')
    error.code = 'LOGOUT_SESSION_CHANGED'
    error.notified = true
    throw error
  }
}

function confirmPasswordReset(message) {
  MessageBox.confirm(message, '安全提示', { confirmButtonText: '去修改', cancelButtonText: '稍后', type: 'warning' }).then(() => {
    router.push(getPasswordResetRoute()).catch(() => {})
  }).catch(() => {})
}

function getPasswordResetMessage(res) {
  if (res.isDefaultModifyPwd) {
    return '您的密码还是初始密码，请修改密码！'
  }
  if (res.isPasswordExpired) {
    return '您的密码已过期，请尽快修改密码！'
  }
  return ''
}

const user = {
  state: {
    token: getToken(),
    id: '',
    sessionRevision: 0,
    deptId: '',
    name: '',
    nickName: '',
    avatar: '',
    roles: [],
    permissions: [],
    driveEnabled: false,
    businessFeatures: defaultBusinessFeatures(),
    profileCompletionRequired: false,
    profileMissingFields: [],
    credentialState: 'ACTIVE',
    temporaryPasswordExpiresAt: '',
    credentialBusinessCode: '',
    systemBuild: { commit: 'UNSET', buildTime: 'UNSET', version: 'UNSET' }
  },

  mutations: {
    BEGIN_LOGIN: state => {
      state.sessionRevision = (state.sessionRevision || 0) + 1
    },
    SET_TOKEN: (state, token) => {
      state.token = token
    },
    SET_EXPIRES_IN: (state, time) => {
      state.expires_in = time
    },
    SET_ID: (state, id) => {
      if (String(state.id) !== String(id)) state.sessionRevision = (state.sessionRevision || 0) + 1
      state.id = id
    },
    SET_DEPT_ID: (state, deptId) => {
      state.deptId = deptId
    },
    SET_NAME: (state, name) => {
      state.name = name
    },
    SET_NICK_NAME: (state, nickName) => {
      state.nickName = nickName
    },
    SET_AVATAR: (state, avatar) => {
      state.avatar = avatar
    },
    SET_ROLES: (state, roles) => {
      state.roles = roles
    },
    SET_PERMISSIONS: (state, permissions) => {
      state.permissions = permissions
    },
    SET_DRIVE_ENABLED: (state, enabled) => {
      state.driveEnabled = enabled === true
    },
    SET_BUSINESS_FEATURES: (state, features) => {
      state.businessFeatures = normalizeBusinessFeatures(features)
    },
    SET_PROFILE_COMPLETION_REQUIRED: (state, required) => {
      state.profileCompletionRequired = required
    },
    SET_PROFILE_MISSING_FIELDS: (state, fields) => {
      state.profileMissingFields = fields
    },
    SET_CREDENTIAL_STATE: (state, credentialState) => {
      state.credentialState = credentialState || 'ACTIVE'
    },
    SET_TEMPORARY_PASSWORD_EXPIRES_AT: (state, expiresAt) => {
      state.temporaryPasswordExpiresAt = expiresAt || ''
    },
    SET_CREDENTIAL_BUSINESS_CODE: (state, businessCode) => {
      state.credentialBusinessCode = businessCode || ''
    },
    SET_SYSTEM_BUILD: (state, build) => {
      state.systemBuild = {
        commit: build && build.commit ? build.commit : 'UNSET',
        buildTime: build && build.buildTime ? build.buildTime : 'UNSET',
        version: build && build.version ? build.version : 'UNSET'
      }
    }
  },

  actions: {
    // 登录
    Login({ commit }, userInfo) {
      // Cookie login has no script token and may not have a user ID until GetInfo.
      // Starting it still supersedes completion permits from a preceding logout.
      commit('BEGIN_LOGIN')
      clearLocalSession(commit, { updateWebStatus: !isCookiePreferredSession() })
      if (isCookiePreferredSession()) {
        setWebSessionStatus('unknown', false)
      }
      clearMobileHrQueueStateCache()
      const username = userInfo.username.trim()
      const password = userInfo.password
      const code = userInfo.code
      const uuid = userInfo.uuid
      return new Promise((resolve, reject) => {
        login(username, password, code, uuid).then(res => {
          clearMobileHrQueueStateCache()
          let data = res.data
          // 每次登录都重新选择店铺，避免沿用上一次登录缓存
          clearSelectedDeptBestEffort()
          resetPasswordResetReminderState()
          if (isCookiePreferredSession()) {
            removeToken()
            commit('SET_TOKEN', '')
            setWebSessionStatus('authenticated')
          } else {
            setToken(data.access_token)
            commit('SET_TOKEN', data.access_token)
          }
          commit('SET_PROFILE_COMPLETION_REQUIRED', false)
          commit('SET_PROFILE_MISSING_FIELDS', [])
          commit('SET_DRIVE_ENABLED', false)
          commit('SET_BUSINESS_FEATURES', null)
          commit('SET_CREDENTIAL_STATE', 'ACTIVE')
          commit('SET_TEMPORARY_PASSWORD_EXPIRES_AT', '')
          commit('SET_CREDENTIAL_BUSINESS_CODE', '')
          commit('SET_SYSTEM_BUILD', null)
          setExpiresIn(data.expires_in)
          commit('SET_EXPIRES_IN', data.expires_in)
          store.dispatch('lock/unlockScreen')
          resolve()
        }).catch(error => {
          clearSelectedDeptBestEffort()
          reject(error)
        })
      })
    },

    // 获取用户信息
    GetInfo({ commit, state }) {
      return new Promise((resolve, reject) => {
        getInfo().then(res => {
          // 普通身份探测不能广播，否则两个标签会在 reload → GetInfo → 广播之间互相触发。
          setWebSessionStatus('authenticated', false)
          const user = res.user
          const avatar = (isEmpty(user.avatar)) ? defAva : user.avatar
          const hasRoles = Array.isArray(res.roles) && res.roles.length > 0
          const roles = hasRoles ? res.roles.slice() : ['ROLE_DEFAULT']
          const permissions = hasRoles && Array.isArray(res.permissions) ? res.permissions.slice() : []
          commit('SET_ROLES', roles)
          commit('SET_PERMISSIONS', permissions)
          commit('SET_ID', user.userId)
          commit('SET_DEPT_ID', user.deptId)
          commit('SET_NAME', user.userName)
          commit('SET_NICK_NAME', user.nickName)
          commit('SET_AVATAR', avatar)
          commit('SET_DRIVE_ENABLED', res.driveEnabled === true)
          commit('SET_BUSINESS_FEATURES', res.businessFeatures)
          commit('SET_PROFILE_COMPLETION_REQUIRED', !!res.profileCompletionRequired)
          commit('SET_PROFILE_MISSING_FIELDS', Array.isArray(res.profileMissingFields) ? res.profileMissingFields : [])
          commit('SET_CREDENTIAL_STATE', res.credentialState)
          commit('SET_TEMPORARY_PASSWORD_EXPIRES_AT', res.temporaryPasswordExpiresAt)
          commit('SET_CREDENTIAL_BUSINESS_CODE', isCredentialChangeRequired(res.credentialState) ? 'CREDENTIAL_CHANGE_REQUIRED' : '')
          commit('SET_SYSTEM_BUILD', res.systemBuild)
          cache.session.set('pwrChrtype', res.pwdChrtype)
          const credentialRestricted = isCredentialChangeRequired(res.credentialState)
          const passwordResetMessage = credentialRestricted ? '' : getPasswordResetMessage(res)
          if (passwordResetMessage) {
            setPendingPasswordResetReminder(passwordResetMessage)
            showPendingPasswordResetReminderIfReady(confirmPasswordReset)
          } else {
            resetPasswordResetReminderState()
          }
          if (credentialRestricted) {
            clearSessionContextsBestEffort()
            resolve(res)
            return
          }
          try {
            Promise.resolve(store.dispatch('todo/start')).catch(() => {})
          } catch (e) {
            // 待办中心不可用不能把已成功的登录转成失败
          }
          try {
            Promise.resolve(pushRegistration.initialize(user.userId)).catch(() => {})
          } catch (e) {
            // Push initialization must never delay or fail an authenticated profile response.
          }
          resolve(res)
        }).catch(error => {
          reject(error)
        })
      })
    },

    // 刷新token
    RefreshToken({commit, state}) {
      return new Promise((resolve, reject) => {
        refreshToken(state.token).then(res => {
          setExpiresIn(res.data)
          commit('SET_EXPIRES_IN', res.data)
          resolve()
        }).catch(error => {
          reject(error)
        })
      })
    },

    EnforceCredentialChange({ commit }, payload) {
      const state = payload && payload.credentialState
      const businessCode = payload && payload.businessCode
      commit('SET_CREDENTIAL_STATE', state || 'CHANGE_REQUIRED')
      commit('SET_CREDENTIAL_BUSINESS_CODE', businessCode || 'CREDENTIAL_CHANGE_REQUIRED')
      clearSessionContextsBestEffort()
      if (router.currentRoute.path === '/credential/change-password' || credentialRedirectPending) {
        return Promise.resolve()
      }
      credentialRedirectPending = true
      return router.replace('/credential/change-password').finally(() => {
        credentialRedirectPending = false
      })
    },

    CredentialChangeCompleted({ commit }) {
      commit('SET_CREDENTIAL_STATE', 'ACTIVE')
      commit('SET_TEMPORARY_PASSWORD_EXPIRES_AT', '')
      commit('SET_CREDENTIAL_BUSINESS_CODE', '')
      resetPasswordResetReminderState()
      return Promise.resolve()
    },
    
    // 退出系统
    LogOut({ commit, state }, options = {}) {
      const originalSession = logoutSessionSnapshot(state)
      const completedResult = () => {
        // clearLocalSession changes the revision itself. Return that exact completion identity,
        // and reject if another login took over while optional cleanup was still finishing.
        const completedSession = logoutSessionSnapshot(state)
        return () => {
          requireLogoutSession(state, completedSession)
          return { completed: true, ...completedSession }
        }
      }
      if (isCookiePreferredSession()) {
        const finishLocalLogout = () => {
          requireLogoutSession(state, originalSession)
          clearMobileHrQueueStateCache()
          const cleanup = [
            settleBestEffort(() => store.dispatch('todo/stop')),
            settleBestEffort(() => pushRegistration.disable())
          ]
          clearLocalSession(commit)
          return Promise.all(cleanup).then(completedResult())
        }
        return runRequiredWithTimeout(() => logout())
          .then(finishLocalLogout)
          .catch(error => {
            requireLogoutSession(state, originalSession)
            // 401 表示服务端会话已失效，网关同时清除了 Cookie，可安全完成本地退出。
            if (isUnauthorizedError(error)) {
              return finishLocalLogout()
            }
            const logoutError = error instanceof Error ? error : new Error('退出未完成')
            if (!logoutError.code) logoutError.code = 'COOKIE_LOGOUT_FAILED'
            if (options && options.failureFeedback === 'inline') {
              // The lock screen covers global dialogs. Let that caller render its visible alert.
              logoutError.inlineMessage = cookieLogoutFailureMessage(logoutError)
            } else if (!logoutError.notified) {
              notifyCookieLogoutFailure(logoutError)
              logoutError.notified = true
            }
            return Promise.reject(logoutError)
          })
      }
      clearMobileHrQueueStateCache()
      const token = state.token
      const pushCleanup = settleBestEffort(() => pushRegistration.disable(token))
      const cleanup = [
        settleBestEffort(() => store.dispatch('todo/stop')),
        pushCleanup
      ]
      pushCleanup.then(() => {
        void settleBestEffort(() => logout(token))
      })
      clearLocalSession(commit)
      return Promise.all(cleanup).then(completedResult())
    },

    // 前端 登出
    FedLogOut({ commit, state }, options = {}) {
      clearMobileHrQueueStateCache()
      const token = state.token
      const cleanup = [
        settleBestEffort(() => store.dispatch('todo/stop')),
        settleBestEffort(() => pushRegistration.disable(token))
      ]
      clearLocalSession(commit, { broadcast: options.broadcast !== false })
      return Promise.all(cleanup).then(() => undefined)
    },

    SyncWebSessionStatus({ commit }, status) {
      if (!isCookiePreferredSession()) return Promise.resolve()
      if (status === 'anonymous') {
        clearMobileHrQueueStateCache()
        const cleanup = [
          settleBestEffort(() => store.dispatch('todo/stop')),
          settleBestEffort(() => pushRegistration.disable())
        ]
        clearLocalSession(commit, { broadcast: false })
        if (router.currentRoute.path !== '/login') {
          void Promise.resolve(router.replace('/login')).catch(() => {})
        }
        return Promise.all(cleanup).then(() => undefined)
      }
      if (status === 'authenticated') {
        // 另一标签登录或切换用户：立即清除本标签旧用户敏感状态，
        // 保留 authenticated 提示并通过刷新重新探测服务端身份。
        clearMobileHrQueueStateCache()
        clearLocalSession(commit, { updateWebStatus: false })
        try {
          void Promise.resolve(store.dispatch('todo/stop')).catch(() => {})
        } catch (e) {
          // reload 是最终边界，不因可选清理失败而阻塞。
        }
        if (typeof window !== 'undefined' && window.location &&
          typeof window.location.reload === 'function') {
          window.location.reload()
        }
      }
      return Promise.resolve()
    }
  }
}

subscribeWebSessionStatus(status => {
  void Promise.resolve(store.dispatch('SyncWebSessionStatus', status)).catch(() => {})
})

export default user
