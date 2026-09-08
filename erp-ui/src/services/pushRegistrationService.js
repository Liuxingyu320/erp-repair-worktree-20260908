import { Capacitor } from '@capacitor/core'
import { PushNotifications } from '@capacitor/push-notifications'
import { getToken } from '@/utils/auth'
import {
  disableUserDeviceToken,
  registerUserDeviceToken
} from '@/api/system/userNotification'
import { resolvePushRoute } from './pushRoute'

const APP_ID = 'com.erp.mobile'
const NUMERIC_ID_PATTERN = /^[1-9]\d{0,18}$/
const STEP_TIMEOUT_MS = 5000
const PERMISSION_REQUEST_TIMEOUT_MS = 15000
const INITIALIZE_TIMEOUT_MS = 20000
const DISABLE_TIMEOUT_MS = 1500
const HANDOFF_TIMEOUT_MS = 250

function normalizeNumericId(value) {
  if (value === undefined || value === null) return null
  const normalized = String(value)
  return NUMERIC_ID_PATTERN.test(normalized) ? normalized : null
}

export function createPushRegistrationService(dependencies = {}) {
  const capacitor = dependencies.capacitor || Capacitor
  const pushNotifications = dependencies.pushNotifications || PushNotifications
  const registerDeviceToken = dependencies.registerDeviceToken || registerUserDeviceToken
  const disableDeviceToken = dependencies.disableDeviceToken || disableUserDeviceToken
  const getAuthToken = dependencies.getAuthToken || getToken
  const navigatorObject = dependencies.navigatorObject ||
    (typeof navigator === 'undefined' ? { userAgent: '' } : navigator)
  const AbortControllerClass = dependencies.AbortController ||
    (typeof AbortController === 'undefined' ? null : AbortController)
  const setTimer = dependencies.setTimeout || setTimeout
  const clearTimer = dependencies.clearTimeout || clearTimeout

  let router = dependencies.router || null
  let generation = 0
  let desiredUserId = null
  let listenerHandles = []
  let activeBinding = null
  let permissionDenied = false
  let uploadSequence = 0
  const pendingUploads = new Map()
  const pendingCompensations = new Set()

  function bounded(task, timeoutMs, onLateFulfilled) {
    return new Promise(resolve => {
      let settled = false
      const finish = outcome => {
        if (settled) return
        settled = true
        clearTimer(timer)
        resolve(outcome)
      }
      const timer = setTimer(() => finish({ status: 'timeout' }), timeoutMs)
      try {
        Promise.resolve(task()).then(
          value => {
            if (settled) {
              if (onLateFulfilled) onLateFulfilled(value)
              return
            }
            finish({ status: 'fulfilled', value })
          },
          error => finish({ status: 'rejected', error })
        )
      } catch (error) {
        finish({ status: 'rejected', error })
      }
    })
  }

  function currentOwner() {
    return { generation, userId: desiredUserId }
  }

  function isCurrent(owner) {
    return owner.generation === generation && owner.userId === desiredUserId
  }

  function platformCode() {
    return capacitor.getPlatform() === 'ios' ? 'IOS' : 'ANDROID'
  }

  function takeListenerHandles() {
    const handles = listenerHandles
    listenerHandles = []
    return handles
  }

  function removeHandle(handle) {
    if (!handle || typeof handle.remove !== 'function') return Promise.resolve()
    return bounded(() => handle.remove(), HANDOFF_TIMEOUT_MS).then(() => undefined)
  }

  function removeHandles(handles) {
    return Promise.all((handles || []).map(removeHandle)).then(() => undefined)
  }

  function startBindingDelete(binding, authToken) {
    if (!authToken) {
      return Promise.resolve({ status: 'skipped', reason: 'missing-auth-token' })
    }
    return bounded(() => disableDeviceToken({
      platform: binding.platform,
      token: binding.token
    }, { authToken }), STEP_TIMEOUT_MS)
  }

  function trackCompensation(binding, authToken) {
    const cleanup = startBindingDelete(binding, authToken)
    pendingCompensations.add(cleanup)
    cleanup.finally(() => pendingCompensations.delete(cleanup))
    return cleanup
  }

  function reconcileNativeTransition(action) {
    const owner = currentOwner()
    if (action === 'unregister' && owner.userId) {
      void performNativeTransition('register', owner)
    } else if (action === 'register' && !owner.userId) {
      void performNativeTransition('unregister', owner)
    }
  }

  function performNativeTransition(action, owner) {
    const nativeCall = action === 'register'
      ? () => pushNotifications.register()
      : () => pushNotifications.unregister()
    return bounded(nativeCall, STEP_TIMEOUT_MS, () => {
      reconcileNativeTransition(action)
    }).then(outcome => {
      if (outcome.status === 'fulfilled') reconcileNativeTransition(action)
      return outcome
    })
  }

  async function installListener(owner, name, callback, localHandles) {
    const outcome = await bounded(
      () => pushNotifications.addListener(name, callback),
      STEP_TIMEOUT_MS,
      handle => removeHandle(handle)
    )
    if (outcome.status !== 'fulfilled') return false
    const handle = outcome.value
    if (!handle || typeof handle.remove !== 'function') return false
    if (!isCurrent(owner)) {
      removeHandle(handle)
      return false
    }
    localHandles.push(handle)
    listenerHandles.push(handle)
    return true
  }

  function uploadRegistration(owner, registration) {
    if (!isCurrent(owner) || !registration || !registration.value) return Promise.resolve()
    let authToken
    try {
      authToken = getAuthToken()
    } catch (e) {
      authToken = null
    }
    if (!authToken) {
      return Promise.resolve({ uploaded: false, reason: 'missing-auth-token' })
    }
    const binding = {
      platform: platformCode(),
      token: registration.value,
      appId: APP_ID,
      deviceName: String(navigatorObject.userAgent || '').slice(0, 200)
    }
    const key = `${owner.generation}:${owner.userId}:${binding.token}:${++uploadSequence}`
    const controller = AbortControllerClass ? new AbortControllerClass() : null
    const attempt = {
      key,
      generation: owner.generation,
      userId: owner.userId,
      authToken,
      binding,
      controller,
      invalidated: false,
      settlementCleanupStarted: false
    }
    pendingUploads.set(key, attempt)

    const cleanupInvalidatedSettlement = () => {
      if (attempt.settlementCleanupStarted) return
      attempt.settlementCleanupStarted = true
      // Best effort only: a server that processes POST after this DELETE can
      // still outlive the client-observable request settlement.
      trackCompensation(binding, attempt.authToken)
    }
    let rawUpload
    try {
      rawUpload = isCurrent(owner)
        ? Promise.resolve(registerDeviceToken(binding, {
          authToken: attempt.authToken,
          signal: controller && controller.signal
        }))
        : Promise.resolve({ skipped: true })
    } catch (error) {
      rawUpload = Promise.reject(error)
    }
    attempt.rawOperation = rawUpload
    rawUpload.then(
      value => {
        pendingUploads.delete(key)
        if (value && value.skipped) return
        if (attempt.invalidated || !isCurrent(owner)) {
          cleanupInvalidatedSettlement()
        } else {
          activeBinding = {
            ...binding,
            userId: owner.userId,
            authToken: attempt.authToken
          }
        }
      },
      () => {
        pendingUploads.delete(key)
        if (attempt.invalidated || !isCurrent(owner)) {
          cleanupInvalidatedSettlement()
        }
      }
    )

    const upload = bounded(() => rawUpload, STEP_TIMEOUT_MS).then(outcome => {
      if (outcome.status === 'timeout') {
        attempt.invalidated = true
        pendingUploads.delete(key)
        try {
          if (controller) controller.abort()
        } catch (e) {
          // A late fulfilled POST is still compensated with the attempt token.
        }
        return { uploaded: false, reason: 'upload-timeout' }
      }
      if (outcome.status !== 'fulfilled') {
        return { uploaded: false, reason: 'upload-unavailable' }
      }
      return { uploaded: !(outcome.value && outcome.value.skipped) }
    })
    attempt.operation = upload
    return upload
  }

  async function addListeners(owner) {
    const localHandles = []
    const registration = await installListener(owner, 'registration',
      value => uploadRegistration(owner, value), localHandles)
    if (!registration) return false
    const registrationError = await installListener(owner, 'registrationError', () => {}, localHandles)
    if (!registrationError) return false
    return installListener(owner, 'pushNotificationActionPerformed', action => {
      if (!isCurrent(owner) || !router || typeof router.push !== 'function') return Promise.resolve()
      const route = resolvePushRoute(action && action.notification && action.notification.data)
      return route ? Promise.resolve(router.push(route)).catch(() => {}) : Promise.resolve()
    }, localHandles)
  }

  function validPermission(permission) {
    return permission && typeof permission.receive === 'string'
  }

  async function initializeOwned(owner) {
    await bounded(() => removeHandles(owner.previousHandles), HANDOFF_TIMEOUT_MS)
    if (!isCurrent(owner)) return { registered: false, reason: 'superseded' }
    if (!owner.userId) return { registered: false, reason: 'missing-user' }
    if (!capacitor.isNativePlatform()) return { registered: false, reason: 'web' }

    if (!await addListeners(owner)) {
      if (isCurrent(owner)) {
        await bounded(() => removeHandles(takeListenerHandles()), HANDOFF_TIMEOUT_MS)
        return { registered: false, reason: 'listener-unavailable' }
      }
      return { registered: false, reason: 'superseded' }
    }
    if (!isCurrent(owner)) return { registered: false, reason: 'superseded' }

    const checked = await bounded(() => pushNotifications.checkPermissions(), STEP_TIMEOUT_MS)
    if (!isCurrent(owner)) return { registered: false, reason: 'superseded' }
    if (checked.status !== 'fulfilled' || !validPermission(checked.value)) {
      await bounded(() => removeHandles(takeListenerHandles()), HANDOFF_TIMEOUT_MS)
      return { registered: false, reason: 'permission-unavailable' }
    }
    let permission = checked.value
    if ((permission.receive === 'prompt' || permission.receive === 'prompt-with-rationale') &&
      !permissionDenied) {
      const requested = await bounded(
        () => pushNotifications.requestPermissions(),
        PERMISSION_REQUEST_TIMEOUT_MS
      )
      if (!isCurrent(owner)) return { registered: false, reason: 'superseded' }
      if (requested.status !== 'fulfilled' || !validPermission(requested.value)) {
        await bounded(() => removeHandles(takeListenerHandles()), HANDOFF_TIMEOUT_MS)
        return { registered: false, reason: 'permission-unavailable' }
      }
      permission = requested.value
      if (permission.receive === 'denied') permissionDenied = true
    }
    if (permission.receive !== 'granted') {
      if (permission.receive === 'denied') permissionDenied = true
      await bounded(() => removeHandles(takeListenerHandles()), HANDOFF_TIMEOUT_MS)
      return { registered: false, reason: 'permission-denied' }
    }

    const registered = await performNativeTransition('register', owner)
    if (!isCurrent(owner)) return { registered: false, reason: 'superseded' }
    if (registered.status !== 'fulfilled') {
      await bounded(() => removeHandles(takeListenerHandles()), HANDOFF_TIMEOUT_MS)
      return { registered: false, reason: 'register-unavailable' }
    }
    return { registered: true }
  }

  async function disableOwned(owner) {
    await bounded(() => removeHandles(owner.previousHandles), HANDOFF_TIMEOUT_MS)
    const cleanupOperations = owner.compensations.map(operation =>
      bounded(() => operation, STEP_TIMEOUT_MS))
    cleanupOperations.push(...owner.immediateCleanupOperations)
    const operations = cleanupOperations
    if (capacitor.isNativePlatform()) {
      operations.push(performNativeTransition('unregister', owner))
    }
    await Promise.all(operations)
    return owner.cleanupSkipped
      ? { disabled: true, reason: 'missing-auth-token' }
      : { disabled: true }
  }

  function finishBoundedOperation(rawOperation, timeoutMs, owner, timeoutResult) {
    return bounded(() => rawOperation, timeoutMs).then(outcome => {
      if (outcome.status === 'fulfilled') return outcome.value
      if (outcome.status === 'timeout' && isCurrent(owner)) {
        ++generation
        takeListenerHandles().forEach(handle => removeHandle(handle))
      }
      return timeoutResult
    })
  }

  return {
    setRouter(nextRouter) {
      router = nextRouter
    },
    initialize(userId) {
      const normalizedUserId = normalizeNumericId(userId)
      const owner = {
        generation: ++generation,
        userId: normalizedUserId,
        previousHandles: takeListenerHandles()
      }
      desiredUserId = normalizedUserId
      return finishBoundedOperation(initializeOwned(owner), INITIALIZE_TIMEOUT_MS, owner, {
        registered: false,
        reason: 'initialize-timeout'
      })
    },
    disable(authToken) {
      const exitingUserId = desiredUserId
      const uploads = Array.from(pendingUploads.values())
      let cleanupAuthToken = authToken
      if (cleanupAuthToken === undefined) {
        try {
          cleanupAuthToken = getAuthToken()
        } catch (e) {
          cleanupAuthToken = null
        }
      }
      const owner = {
        generation: ++generation,
        userId: null,
        exitingUserId,
        authToken: cleanupAuthToken,
        previousHandles: takeListenerHandles(),
        binding: activeBinding,
        uploads,
        compensations: Array.from(pendingCompensations),
        immediateCleanupOperations: [],
        cleanupSkipped: false
      }
      desiredUserId = null
      activeBinding = null
      uploads.forEach(attempt => {
        pendingUploads.delete(attempt.key)
        attempt.invalidated = true
        try {
          if (attempt.controller) attempt.controller.abort()
        } catch (e) {
          // Cleanup DELETE still runs with the captured owner credential.
        }
      })
      const cleanupTargets = uploads.map(attempt => ({
        binding: attempt.binding,
        authToken: attempt.userId === exitingUserId
          ? cleanupAuthToken
          : attempt.authToken,
        userId: attempt.userId
      }))
      if (owner.binding && !cleanupTargets.some(target =>
        target.userId === owner.binding.userId &&
        target.binding.platform === owner.binding.platform &&
        target.binding.token === owner.binding.token)) {
        cleanupTargets.push({
          binding: owner.binding,
          authToken: owner.binding.userId === exitingUserId
            ? cleanupAuthToken
            : owner.binding.authToken,
          userId: owner.binding.userId
        })
      }
      owner.cleanupSkipped = cleanupTargets.some(target => !target.authToken)
      owner.immediateCleanupOperations = cleanupTargets.map(target =>
        startBindingDelete(target.binding, target.authToken))
      return finishBoundedOperation(disableOwned(owner), DISABLE_TIMEOUT_MS, owner, {
        disabled: true
      })
    }
  }
}

export default createPushRegistrationService()
