import { resolvePushRoute } from './pushRoute'

let router = null
let servicePromise = null

function isNativeRuntime() {
  if (typeof window === 'undefined' || !window.Capacitor) return false
  const capacitor = window.Capacitor
  if (typeof capacitor.isNativePlatform === 'function') {
    return capacitor.isNativePlatform()
  }
  return typeof capacitor.getPlatform === 'function' && capacitor.getPlatform() !== 'web'
}

function loadService() {
  if (!servicePromise) {
    servicePromise = import(
      /* webpackChunkName: "chunk-native-push" */
      './pushRegistrationService'
    ).then(module => {
      const service = module.default
      if (router) service.setRouter(router)
      return service
    }).catch(error => {
      servicePromise = null
      throw error
    })
  }
  return servicePromise
}

const pushRegistration = {
  setRouter(nextRouter) {
    router = nextRouter
    if (servicePromise) {
      servicePromise.then(service => service.setRouter(nextRouter)).catch(() => {})
    }
  },
  initialize(userId) {
    if (!servicePromise && !isNativeRuntime()) {
      return Promise.resolve({ registered: false, reason: 'web' })
    }
    return loadService().then(service => service.initialize(userId))
  },
  disable(authToken) {
    if (!servicePromise && !isNativeRuntime()) {
      return Promise.resolve({ disabled: true, reason: 'web' })
    }
    return loadService().then(service => service.disable(authToken))
  }
}

export { resolvePushRoute }
export default pushRegistration
