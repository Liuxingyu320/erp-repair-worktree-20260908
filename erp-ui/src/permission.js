import pushRegistration from '@/services/lazyPushRegistration'
import router from './router'
import store from './store'
import { Message, MessageBox } from '@/plugins/element-services'
import NProgress from 'nprogress'
import 'nprogress/nprogress.css'
import { getToken } from '@/utils/auth'
import { clearTodoContextLease, restoreTodoContextLeaseForRoute } from '@/utils/todoContextLease'
import { clearSelectedDept, getSelectedDeptContext, getSelectedDeptType, hasSelectedDeptContext, hasValidatedSelectedDeptContext, setSelectedDept } from '@/utils/shopContext'
import { getMobileRouteAccessInfo, getMobileHomePath, getMobileRouteFeature, isMobileContextOptionalPath, resolveMobileNavigationRedirect } from '@/views/mobile/mobileNavigation'
import { isPathMatch } from '@/utils/validate'
import { isRelogin } from '@/utils/request'
import { requiresInventoryContext } from '@/utils/desktopContextPolicy'
import { getWebSessionStatus, hasSessionCandidate, isCookiePreferredSession } from '@/utils/sessionMode'
const { isMobileClient, isMobileRoutePath } = require('@/utils/clientPlatform')

const {
  PROFILE_COMPLETION_PATH,
  isProfileCompletionPath,
  resolveProfileCompletionRedirect
} = require('@/utils/profileCompletion')

NProgress.configure({ showSpinner: false })

const whiteList = ['/login', '/register']
const mobileContextFreeSystemPaths = new Set(['/401', '/404', '/lock'])

const CREDENTIAL_CHANGE_PATH = '/credential/change-password'
const ROUTE_LOAD_ERROR_MESSAGE = '业务菜单加载失败，登录状态已保留。请检查网络或服务状态后重试。'

let routesGeneratedForSession = false
let routeGenerationPromise = null

const getErrorCode = (error) => {
  const response = error && error.response
  const payload = response && response.data
  const candidates = [error && error.code, payload && payload.code, response && response.status]
  for (const candidate of candidates) {
    if (candidate === undefined || candidate === null || candidate === '') continue
    const code = Number(candidate)
    if (Number.isFinite(code)) return code
  }
  return NaN
}

const getErrorBusinessCode = (error) => {
  const response = error && error.response
  const payload = response && response.data
  return error && error.businessCode || (payload && payload.businessCode) || ''
}

const isAuthenticationError = error => getErrorCode(error) === 401

const isCredentialRestrictionError = (error) => {
  const code = getErrorCode(error)
  const businessCode = getErrorBusinessCode(error)
  return code === 428 || (code === 409 && [
    'CREDENTIAL_CHANGE_REQUIRED',
    'TEMPORARY_CREDENTIAL_EXPIRED'
  ].includes(businessCode))
}

const generateRoutesWithRetry = () => {
  if (routeGenerationPromise) return routeGenerationPromise
  routeGenerationPromise = (async () => {
    while (true) {
      try {
        const accessRoutes = await store.dispatch('GenerateRoutes')
        router.addRoutes(accessRoutes)
        return accessRoutes
      } catch (error) {
        // 身份失效与强制改密均由导航边界处理，不能叠加菜单重试弹窗。
        if (isAuthenticationError(error) || isCredentialRestrictionError(error)) {
          throw error
        }
        const shouldRetry = await MessageBox.confirm(ROUTE_LOAD_ERROR_MESSAGE, '页面加载失败', {
          confirmButtonText: '重试加载',
          cancelButtonText: '稍后重试',
          type: 'error',
          closeOnClickModal: false
        }).then(() => true).catch(() => false)
        if (!shouldRetry) {
          throw error
        }
      }
    }
  })().finally(() => {
    routeGenerationPromise = null
  })
  return routeGenerationPromise
}

const isCredentialRestricted = () => ['TEMPORARY', 'CHANGE_REQUIRED'].includes(store.getters.credentialState)

const getCredentialGuardRedirect = (to) => {
  if (isCredentialRestricted() && to.path !== CREDENTIAL_CHANGE_PATH) {
    clearSelectedDept()
    return { path: CREDENTIAL_CHANGE_PATH, replace: true }
  }
  if (!isCredentialRestricted() && to.path === CREDENTIAL_CHANGE_PATH) {
    return { path: '/select-shop', replace: true }
  }
  return null
}

const enterCredentialChange = (to, next) => {
  NProgress.done()
  if (to.path === CREDENTIAL_CHANGE_PATH) {
    return next()
  }
  return next({ path: CREDENTIAL_CHANGE_PATH, replace: true })
}

const isWhiteList = (to) => {
  return whiteList.some(pattern => isPathMatch(pattern, to.path))
}

const isMobileViewport = () => {
  return isMobileClient()
}

const getClientBoundaryRedirect = (path) => {
  if (!isMobileViewport() && isMobileRoutePath(path)) {
    return { path: '/', replace: true }
  }
  return null
}

const isRootEntryPath = (path) => {
  return path === '/' || path === '/index'
}

const getMobileEntryPath = () => {
  return getMobileHomePath(getSelectedDeptType(), store.getters.permissions)
}

const getMobileFeatureState = () => {
  return {
    driveEnabled: store.getters.driveEnabled,
    businessFeatures: store.getters.businessFeatures || {}
  }
}

const normalizeMobileRedirect = (path) => {
  const entryPath = isMobileViewport() && isRootEntryPath(path) ? getMobileEntryPath() : path

  if (!hasValidatedSelectedDeptContext()) {
    if (!isMobileContextOptionalPath(entryPath)) return entryPath
    return getMobileRouteAccessInfo(
      entryPath,
      getSelectedDeptType(),
      store.getters.permissions,
      getMobileFeatureState()
    ).redirect || entryPath
  }

  return getMobileRouteAccessInfo(
    entryPath,
    getSelectedDeptType(),
    store.getters.permissions,
    getMobileFeatureState()
  ).redirect || entryPath
}

const getNormalizedMobileRedirectInfo = (path) => {
  const mobileViewport = isMobileViewport()
  return resolveMobileNavigationRedirect({
    path,
    mobileViewport,
    mobileEntryPath: mobileViewport && isRootEntryPath(path) ? getMobileEntryPath() : path,
    validatedContext: hasValidatedSelectedDeptContext(),
    deptType: getSelectedDeptType(),
    permissions: store.getters.permissions,
    featureState: getMobileFeatureState()
  })
}

const appendMobileRedirectNotice = (query, accessInfo, fromPath) => {
  const nextQuery = Object.assign({}, query || {})
  if (accessInfo && accessInfo.reason) {
    nextQuery.mobileRedirectReason = accessInfo.reason
    nextQuery.mobileRedirectMessage = accessInfo.message
    nextQuery.mobileRedirectFrom = fromPath
  }
  return nextQuery
}

const shouldSelectShop = (path) => {
  if (
    path === '/select-shop' ||
    path === PROFILE_COMPLETION_PATH ||
    path === CREDENTIAL_CHANGE_PATH ||
    mobileContextFreeSystemPaths.has(path)
  ) {
    return false
  }
  if (isMobileContextOptionalPath(path)) {
    return false
  }
  const feature = getMobileRouteFeature(path)
  if (feature && feature.requiresBusinessContext === false) {
    return false
  }
  if (isRootEntryPath(path) && !hasValidatedSelectedDeptContext()) {
    return true
  }
  if (requiresInventoryContext(path) && !hasValidatedSelectedDeptContext()) {
    return true
  }
  return (path.indexOf('/mobile/') === 0 || isMobileViewport()) && !hasSelectedDeptContext()
}

const getProfileCompletionGuardRedirect = (to) => {
  const completionRequired = !!store.getters.profileCompletionRequired
  if (!completionRequired && isProfileCompletionPath(to.path)) {
    const requestedRedirect = to.query && to.query.redirect
    return {
      path: '/select-shop',
      query: requestedRedirect ? { redirect: requestedRedirect } : {},
      replace: true
    }
  }
  const redirect = resolveProfileCompletionRedirect(to.fullPath || to.path, completionRequired)
  if (!redirect) {
    return null
  }
  clearSelectedDept()
  return { path: redirect, replace: true }
}

router.beforeEach(async (to, from, next) => {
  NProgress.start()
  const bearerToken = getToken()
  const allowAnonymousCookieEntry = isCookiePreferredSession() &&
    !bearerToken &&
    getWebSessionStatus() !== 'authenticated' &&
    isWhiteList(to)
  if (allowAnonymousCookieEntry) {
    next()
    NProgress.done()
    return
  }
  if (hasSessionCandidate(bearerToken)) {
    restoreTodoContextLeaseForRoute(to, {
      getCurrentContext: getSelectedDeptContext,
      setSelectedDept,
      clearSelectedDept
    })
    to.meta.title && store.dispatch('settings/setTitle', to.meta.title)
    const isLock = store.getters.isLock
    /* has token*/
    if (to.path === '/login') {
      next({ path: isMobileViewport() ? getMobileEntryPath() : '/' })
      NProgress.done()
    } else if (isWhiteList(to)) {
      next()
    } else if (isLock && to.path !== '/lock') {
      next({ path: '/lock' })
      NProgress.done()
    } else if (!isLock && to.path === '/lock') {
      next({ path: '/' })
      NProgress.done()
    } else {
      if (store.getters.roles.length === 0 || !routesGeneratedForSession) {
        isRelogin.show = true
        let ownsReloginState = true
        try {
          // 用户信息与菜单分别处理：只有身份信息失败才退出，临时菜单失败保留登录状态。
          // 路由会话首次初始化时必须刷新用户信息。开发热更新、状态恢复等场景下
          // roles 可能仍有旧值，不能据此跳过资料补全状态的服务端校验。
          routesGeneratedForSession = false
          try {
            await store.dispatch('GetInfo')
          } catch (err) {
            if (!isAuthenticationError(err)) {
              if (isCredentialRestrictionError(err)) {
                return enterCredentialChange(to, next)
              }
              try {
                await store.dispatch('FedLogOut')
              } catch (logoutError) {
                // 本地导航恢复不能被退出接口错误阻塞。
              }
              Message.error(err)
            }
            NProgress.done()
            return next({ path: '/' })
          }
          // GetInfo 结束后把会话过期弹窗的控制权交还请求层，菜单接口的 401 仍能正确处理。
          isRelogin.show = false
          ownsReloginState = false
          const credentialRedirect = getCredentialGuardRedirect(to)
          if (credentialRedirect) {
            NProgress.done()
            return next(credentialRedirect)
          }
          if (isCredentialRestricted() && to.path === CREDENTIAL_CHANGE_PATH) {
            NProgress.done()
            return next()
          }
          try {
            await generateRoutesWithRetry()
          } catch (routeError) {
            if (isCredentialRestrictionError(routeError)) {
              return enterCredentialChange(to, next)
            }
            // 取消重试时终止本次导航；会话级完成标记仍为 false，再次导航会重新进入加载流程。
            if (!isAuthenticationError(routeError)) {
              Message.warning('业务菜单尚未加载，登录状态已保留；可刷新页面后重试。')
            }
            NProgress.done()
            return next(false)
          }
          routesGeneratedForSession = true
          const profileCompletionRedirect = getProfileCompletionGuardRedirect(to)
          if (profileCompletionRedirect) {
            NProgress.done()
            return next(profileCompletionRedirect)
          }
          const clientBoundaryRedirect = getClientBoundaryRedirect(to.path)
          if (clientBoundaryRedirect) {
            NProgress.done()
            return next(clientBoundaryRedirect)
          }
          const mobileRedirectInfo = getNormalizedMobileRedirectInfo(to.path)
          if (mobileRedirectInfo.redirect !== to.path) {
            NProgress.done()
            return next({
              path: mobileRedirectInfo.redirect,
              query: appendMobileRedirectNotice(to.query, mobileRedirectInfo, to.path),
              replace: true
            })
          }
          if (shouldSelectShop(to.path)) {
            NProgress.done()
            return next({ path: '/select-shop', query: { redirect: to.fullPath }, replace: true })
          }
          return next({ ...to, replace: true }) // hack方法 确保addRoutes已完成
        } finally {
          if (ownsReloginState) {
            isRelogin.show = false
          }
        }
      } else {
        const credentialRedirect = getCredentialGuardRedirect(to)
        if (credentialRedirect) {
          next(credentialRedirect)
          NProgress.done()
          return
        }
        const profileCompletionRedirect = getProfileCompletionGuardRedirect(to)
        if (profileCompletionRedirect) {
          next(profileCompletionRedirect)
          NProgress.done()
          return
        }
        const clientBoundaryRedirect = getClientBoundaryRedirect(to.path)
        if (clientBoundaryRedirect) {
          next(clientBoundaryRedirect)
          NProgress.done()
          return
        }
        const mobileRedirectInfo = getNormalizedMobileRedirectInfo(to.path)
        if (mobileRedirectInfo.redirect !== to.path) {
          next({
            path: mobileRedirectInfo.redirect,
            query: appendMobileRedirectNotice(to.query, mobileRedirectInfo, to.path),
            replace: true
          })
          NProgress.done()
          return
        }
        if (shouldSelectShop(to.path)) {
          next({ path: '/select-shop', query: { redirect: to.fullPath } })
          NProgress.done()
          return
        }
        next()
      }
    }
  } else {
    // 没有token
    clearTodoContextLease()
    if (isWhiteList(to)) {
      // 在免登录白名单，直接进入
      next()
    } else {
      const clientBoundaryRedirect = getClientBoundaryRedirect(to.path)
      const redirect = clientBoundaryRedirect
        ? clientBoundaryRedirect.path
        : normalizeMobileRedirect(to.fullPath || to.path)
      next(`/login?redirect=${encodeURIComponent(redirect)}`) // 否则全部重定向到登录页
      NProgress.done()
    }
  }
})

router.afterEach(to => {
  NProgress.done()
  const ready = getToken() && routesGeneratedForSession && !isCredentialRestricted() &&
    !store.getters.profileCompletionRequired &&
    !['/login', '/lock', '/complete-profile', CREDENTIAL_CHANGE_PATH].includes(to.path)
  // Consume queued push destinations only after authentication and navigation guards settle.
  void pushRegistration.resumeNavigation(ready ? store.getters.id : null).catch(() => {})
})
