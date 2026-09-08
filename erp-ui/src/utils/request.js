import axios from 'axios'
import { Notification, MessageBox, Message, Loading } from '@/plugins/element-services'
import store from '@/store'
import { getToken } from '@/utils/auth'
import { getSelectedDeptType, getSelectedInventoryDeptId } from '@/utils/shopContext'
import { getSelectedSignScopeDeptId, SIGN_SCOPE_HEADER } from '@/utils/signScopeContext'
import errorCode from '@/utils/errorCode'
import { blobValidate, tansParams } from "@/utils/common"
import { scheduleTodoMutationRefresh } from '@/utils/todoMutationMatcher'
import { Capacitor } from '@capacitor/core'
import {
  applySessionAuthHeaders,
  hasSessionCandidate,
  isCookiePreferredSession,
  shouldUseSessionCredentials
} from '@/utils/sessionMode'

const { assertRequestNotDuplicate } = require("./requestDeduplicator")
const { resolveApiBaseUrl } = require("./apiBaseUrl")
const { assertTrustedRequestBaseUrl, assertTrustedRequestUrl } = require("./requestSecurity")
const { createSingleFlight } = require("./singleFlight")
const { createTransferCommandRecovery } = require("./transferCommandRecovery")
const { applyInventoryDeptRequestContext } = require("./requestInventoryContext")
const {
  applyPersistentCommandRequestId,
  isPersistentTransferCommand
} = require("./persistentCommand")
const {
  isFileResponse,
  normalizeFileResponse,
  normalizeTransportFailure
} = require("./fileResponse")

let downloadLoadingInstance
// 是否显示重新登录
export let isRelogin = { show: false }
let sessionExpiryPromise = null

const shouldSilenceError = (config) => {
  if (config && config.silentError === true) {
    return true
  }
  const isMobilePageRequest = typeof window !== 'undefined' &&
    window.location &&
    window.location.pathname &&
    window.location.pathname.indexOf('/mobile/') === 0
  return !!isMobilePageRequest && (!config || config.mobileSilentError !== false)
}

const normalizeRequestError = (message, code, response, details = {}) => {
  const error = new Error(message || errorCode['default'])
  error.code = code
  error.response = response
  error.businessCode = details.businessCode
  error.notified = details.notified === true
  error.isFileResponseError = details.isFileResponseError === true
  return error
}

const redirectToLoginEntry = () => {
  if (typeof window !== 'undefined' && window.location) {
    window.location.href = '/index'
  }
}

export const handleExpiredSession = () => {
  if (sessionExpiryPromise) {
    return sessionExpiryPromise
  }
  if (!hasSessionCandidate(getToken())) {
    return Promise.resolve()
  }

  // 会话已经被服务端判定无效时，先同步触发本地清理，再提供跳转选择。
  // 不能等待用户确认后才清理，也不能再携带失效 Token 调用远端退出接口。
  isRelogin.show = true
  sessionExpiryPromise = Promise.resolve()
  if (isCookiePreferredSession()) {
    // 下游业务体 401 可能发生在网关已成功认证之后；此时 Cookie/Redis
    // 仍可能有效。使用禁止递归401处理的单航班注销做尽力撤销。
    void revokeExpiredCookieSession()
  }
  try {
    // 外部推送注销等补偿可能超时；本地状态会在 action 内同步清空，
    // 弹窗和跳转不能被这些尽力清理任务阻塞。
    void Promise.resolve(store.dispatch('FedLogOut')).catch(() => undefined)
  } catch (error) {
    // 即使本地存储或插件同步抛错，也必须继续完成失效提示。
  }
  sessionExpiryPromise = sessionExpiryPromise
    .then(() => MessageBox.confirm(
      '登录状态已过期，本地会话已安全清理。请重新登录后继续操作。',
      '系统提示',
      {
        confirmButtonText: '重新登录',
        cancelButtonText: '留在当前页',
        type: 'warning'
      }
    ))
    .then(redirectToLoginEntry)
    .catch(() => undefined)
    .finally(() => {
      isRelogin.show = false
      sessionExpiryPromise = null
    })

  return sessionExpiryPromise
}

axios.defaults.headers['Content-Type'] = 'application/json;charset=utf-8'
// 创建axios实例
const service = axios.create({
  // axios中请求配置有baseURL选项，表示请求URL公共部分
  baseURL: resolveApiBaseUrl({
    basePath: process.env.VUE_APP_BASE_API,
    nativeOrigin: process.env.VUE_APP_NATIVE_API_ORIGIN,
    isNative: Capacitor.isNativePlatform(),
    production: process.env.NODE_ENV === 'production'
  }),
  // 超时
  timeout: 10000,
  withCredentials: shouldUseSessionCredentials(),
  xsrfCookieName: 'XSRF-TOKEN',
  xsrfHeaderName: 'X-XSRF-TOKEN'
})

// 多个写请求同时因 CSRF 过期返回 403 时，只允许一次恢复/轮换。
// 每个原请求仍通过自己的 __csrfRetry 标记最多重放一次。
const recoverCsrfSession = createSingleFlight(() => service.get('/auth/csrf', {
  silentError: true,
  __csrfRecovery: true
}))

const revokeExpiredCookieSession = createSingleFlight(() => service.delete('/auth/logout', {
  silentError: true,
  suppressSessionExpiry: true,
  __expiredSessionRevocation: true
}).catch(() => undefined))

const responseCode = (payload, fallback) => {
  const rawCode = payload && payload.code
  if (rawCode === undefined || rawCode === null || rawCode === '') {
    return Number(fallback) || fallback || 200
  }
  const numericCode = Number(rawCode)
  return Number.isFinite(numericCode) ? numericCode : rawCode
}

const responseMessage = (payload, code, fallback) => {
  const payloadMessage = payload && (payload.msg || payload.message)
  return payloadMessage || errorCode[code] || fallback || errorCode['default']
}

const rejectResponseFailure = (res, payload, fallbackCode, details = {}) => {
  const code = responseCode(payload, fallbackCode)
  const msg = responseMessage(payload, code, details.message)
  const businessCode = payload && payload.businessCode || details.businessCode
  const silent = shouldSilenceError(res && res.config)
  let notified = false

  if (code === 409 && (businessCode === 'CREDENTIAL_CHANGE_REQUIRED' || businessCode === 'TEMPORARY_CREDENTIAL_EXPIRED')) {
    if (hasSessionCandidate(getToken())) {
      Promise.resolve(store.dispatch('EnforceCredentialChange', {
        credentialState: payload && payload.credentialState,
        businessCode
      })).catch(() => {})
    } else if (!silent) {
      Message({ message: msg, type: 'error' })
      notified = true
    }
  } else if (code === 428) {
    Promise.resolve(store.dispatch('EnforceCredentialChange', {
      credentialState: payload && payload.credentialState || 'CHANGE_REQUIRED',
      businessCode: businessCode || 'CREDENTIAL_CHANGE_REQUIRED'
    })).catch(() => {})
  } else if (code === 401) {
    if (!res || !res.config || res.config.suppressSessionExpiry !== true) {
      void handleExpiredSession()
      notified = true
    }
  } else if (code === 500) {
    if (!silent) {
      Message({ message: msg, type: 'error' })
      notified = true
    }
  } else if (code === 601) {
    if (!silent) {
      Message({ message: msg, type: 'warning' })
      notified = true
    }
  } else if (code !== 200) {
    if (!silent) {
      Notification.error({ title: msg })
      notified = true
    }
  }

  return Promise.reject(normalizeRequestError(msg, code, res, {
    businessCode,
    notified,
    isFileResponseError: details.isFileResponseError
  }))
}

// request拦截器
service.interceptors.request.use(config => {
  assertTrustedRequestBaseUrl(config.baseURL, service.defaults.baseURL)
  assertTrustedRequestUrl(config.url, service.defaults.baseURL)
  if (config.__transferCommandScope) {
    transferRecovery.assertContext(config, config.__transferCommandScope)
  }
  if (isPersistentTransferCommand(config)) {
    applyPersistentCommandRequestId(config)
  }
  // 是否需要设置 token
  const isToken = (config.headers || {}).isToken === false
  // Cookie 会话即使显式关闭 Bearer，也必须保留双提交 CSRF 头；
  // 这覆盖 logout 等携带旧 token 参数但实际由 HttpOnly Cookie 认证的请求。
  if (isCookiePreferredSession() || !isToken) {
    config.headers = applySessionAuthHeaders(config.headers || {}, getToken())
  }
  if (!isToken) {
    const selectedDeptId = getSelectedInventoryDeptId()
    applyInventoryDeptRequestContext(config, selectedDeptId, getSelectedDeptType())
    const requestPath = String(config.url || '').split('?')[0]
    const isGlobalSignPlanRequest = /^\/oa\/signPackage\/plan(?:\/|$)/.test(requestPath)
    if (/^\/oa\/sign(?:Package|Task)(?:\/|$)/.test(requestPath) && !isGlobalSignPlanRequest) {
      const selectedSignScopeDeptId = getSelectedSignScopeDeptId()
      if (selectedSignScopeDeptId) {
        config.headers[SIGN_SCOPE_HEADER] = selectedSignScopeDeptId
      }
    }
  }
  // get请求映射params参数
  if (config.method === 'get' && config.params) {
    let url = config.url + '?' + tansParams(config.params)
    url = url.slice(0, -1)
    config.params = {}
    config.url = url
  }
  assertRequestNotDuplicate(config)
  return config
}, error => {
    // A rejected request interceptor must remain rejected. Returning nothing
    // here turns setup failures into a resolved `undefined` config and hides
    // the real cause behind a later Axios error.
    return Promise.reject(error)
})

// 响应拦截器
service.interceptors.response.use(async res => {
    if (isFileResponse(res)) {
      try {
        return await normalizeFileResponse(res.data, {
          status: res.status,
          headers: res.headers
        })
      } catch (fileError) {
        return rejectResponseFailure(
          res,
          fileError.payload,
          fileError.code,
          {
            message: fileError.message,
            businessCode: fileError.businessCode,
            isFileResponseError: true
          }
        )
      }
    }
    const code = responseCode(res.data, 200)
    if (code !== 200) {
      return rejectResponseFailure(res, res.data, code)
    }

    if (!res.config || res.config.suppressTodoMutationRefresh !== true) {
      scheduleTodoMutationRefresh(
        res.config && res.config.method,
        res.config && res.config.url,
        store.dispatch.bind(store)
      )
    }
    return res.data
  },
  async error => {
    const statusCode = error && error.response ? error.response.status : ""
    const originalConfig = error && error.config
    const binaryErrorResponse = !!(error && error.response && isFileResponse(error.response))
    const csrfRequired = error && error.response && error.response.headers &&
      String(error.response.headers['x-erp-csrf-required'] || '').toLowerCase() === 'true'
    const method = String(originalConfig && originalConfig.method || 'get').toLowerCase()
    const unsafeMethod = !['get', 'head', 'options'].includes(method)
    if (Number(statusCode) === 403 && csrfRequired && unsafeMethod &&
      isCookiePreferredSession() && originalConfig && !originalConfig.__csrfRetry) {
      originalConfig.__csrfRetry = true
      return recoverCsrfSession().then(() => service.request(originalConfig))
    }
    if (Number(statusCode) === 401 && !binaryErrorResponse) {
      if (!originalConfig || originalConfig.suppressSessionExpiry !== true) {
        void handleExpiredSession()
      }
      return Promise.reject(normalizeRequestError(
        '无效的会话，或者会话已过期，请重新登录。',
        statusCode,
        error.response || error,
        { notified: !originalConfig || originalConfig.suppressSessionExpiry !== true }
      ))
    }

    if (binaryErrorResponse) {
      try {
        await normalizeFileResponse(error.response.data, {
          status: error.response.status,
          headers: error.response.headers
        })
      } catch (fileError) {
        return rejectResponseFailure(
          error.response,
          fileError.payload,
          fileError.code,
          {
            message: fileError.message,
            businessCode: fileError.businessCode,
            isFileResponseError: true
          }
        )
      }
    }

    const responsePayload = error && error.response && error.response.data
    if (responsePayload && typeof responsePayload === 'object' &&
      !(typeof Blob !== 'undefined' && responsePayload instanceof Blob) &&
      (responsePayload.code != null || responsePayload.msg || responsePayload.message ||
        responsePayload.businessCode)) {
      return rejectResponseFailure(error.response, responsePayload, statusCode)
    }

    const normalizedFailure = normalizeTransportFailure(error)
    const silent = shouldSilenceError(error && error.config)
    if (!silent && normalizedFailure.code !== 'REQUEST_CANCELED') {
      Message({ message: normalizedFailure.message, type: 'error', duration: 5 * 1000 })
    }
    return Promise.reject(normalizeRequestError(
      normalizedFailure.message,
      normalizedFailure.code,
      error.response || error,
      { notified: !silent && normalizedFailure.code !== 'REQUEST_CANCELED' }
    ))
  }
)

// 通用下载方法
export function download(url, params, filename, config) {
  downloadLoadingInstance = Loading.service({ text: "正在下载数据，请稍候", spinner: "el-icon-loading", background: "rgba(0, 0, 0, 0.7)", })
  return service.post(url, params, {
    transformRequest: [(params) => { return tansParams(params) }],
    headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
    responseType: 'blob',
    ...config
  }).then(async (data) => {
    const isBlob = blobValidate(data)
    if (isBlob) {
      const blob = new Blob([data])
      const { saveAs } = await import(
        /* webpackChunkName: "chunk-download" */
        'file-saver'
      )
      saveAs(blob, filename)
    } else {
      const resText = await data.text()
      const rspObj = JSON.parse(resText)
      const errMsg = errorCode[rspObj.code] || rspObj.msg || errorCode['default']
      Message.error(errMsg)
    }
    downloadLoadingInstance.close()
  }).catch((r) => {
    console.error(r)
    if (!r || r.notified !== true) {
      Message.error(r && r.message ? r.message : '下载文件出现错误，请联系管理员！')
    }
  }).finally(() => {
    if (downloadLoadingInstance) {
      downloadLoadingInstance.close()
      downloadLoadingInstance = null
    }
  })
}

const transferRecovery = createTransferCommandRecovery({
  storage: () => typeof sessionStorage === 'undefined' ? null : sessionStorage,
  context: () => ({ actor: store.getters.id, dept: getSelectedInventoryDeptId() }),
  transport: config => service(config),
  confirmRecovery: () => MessageBox.confirm(
    '上一笔调拨的结果尚未确认，本次内容也已变化。是否先按上次提交的内容核对结果？本次修改暂不提交。',
    '核对上次调拨', { confirmButtonText: '核对上次操作', cancelButtonText: '返回', type: 'warning' }
  ).then(() => true, () => false),
  consumeRecovered: result => {
    const data = result && result.data || {}
    const number = data.transferNo || data.shipmentNo || ''
    return MessageBox.alert(
      '上次调拨操作已确认' + (number ? '，单号：' + number : '') + '。本次修改尚未提交，请先刷新单据列表核对，再进行下一步操作。',
      '上次操作已确认', { confirmButtonText: '已了解', type: 'success', dangerouslyUseHTMLString: false }
    )
  },
  status: (requestId, deptId) => service.get(
    '/inventory/transfer/commands/' + encodeURIComponent(requestId) + '/status',
    { inventoryDeptId: deptId, silentError: true }
  ).then(result => result && result.data)
})

export const acknowledgeTransferCommand = result => transferRecovery.acknowledge(result)

export default function request(config) {
  return transferRecovery.run(config)
}
