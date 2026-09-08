import { Capacitor } from '@capacitor/core'

let router = null
let servicePromise = null

function loadService() {
  if (!servicePromise) {
    servicePromise = import(
      /* webpackChunkName: "chunk-native-push" */
      '@/services/pushRegistration'
    ).then(module => {
      const service = module.default
      service.setRouter(router)
      return service
    }).catch(error => {
      servicePromise = null
      throw error
    })
  }
  return servicePromise
}

export default {
  setRouter(nextRouter) {
    router = nextRouter
    if (servicePromise) {
      void servicePromise.then(service => service.setRouter(nextRouter)).catch(() => {})
    }
  },

  initialize(userId) {
    if (!Capacitor.isNativePlatform()) {
      return Promise.resolve({ registered: false, reason: 'web' })
    }
    return loadService().then(service => service.initialize(userId))
  },

  disable(authToken) {
    if (!servicePromise) {
      return Promise.resolve({ disabled: true, reason: 'not-initialized' })
    }
    return servicePromise.then(service => service.disable(authToken))
  }
}
