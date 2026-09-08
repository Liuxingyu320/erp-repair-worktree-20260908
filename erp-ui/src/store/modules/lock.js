import { clearMobileHrQueueStateCache } from '@/utils/mobileHrQueueState'
import {
  getSafeLocalStorage,
  readJsonStorageValue,
  readStringStorageValue,
  safeSetStorageValue
} from '@/utils/safeStorage'

const LOCK_KEY = 'screen-lock'
const LOCK_PATH_KEY = 'screen-lock-path'
const DEFAULT_LOCK_PATH = '/index'

function readLockState() {
  return readJsonStorageValue(
    getSafeLocalStorage(),
    LOCK_KEY,
    false,
    value => typeof value === 'boolean'
  )
}

function readLockPath() {
  return readStringStorageValue(
    getSafeLocalStorage(),
    LOCK_PATH_KEY,
    DEFAULT_LOCK_PATH,
    value => value.startsWith('/') && value.length <= 4096
  )
}

const lock = {
  namespaced: true,
  state: {
    isLock: readLockState(),
    lockPath: readLockPath()
  },
  mutations: {
    SET_LOCK(state, status) {
      state.isLock = status
      safeSetStorageValue(getSafeLocalStorage(), LOCK_KEY, JSON.stringify(status))
    },
    SET_LOCK_PATH(state, path) {
      state.lockPath = path
      safeSetStorageValue(getSafeLocalStorage(), LOCK_PATH_KEY, path)
    }
  },
  actions: {
    // 锁定屏幕，同时记录当前路径
    lockScreen({ commit }, currentPath) {
      clearMobileHrQueueStateCache()
      commit('SET_LOCK_PATH', currentPath || '/index')
      commit('SET_LOCK', true)
    },
    // 解锁屏幕，清除路径
    unlockScreen({ commit }) {
      commit('SET_LOCK', false)
      commit('SET_LOCK_PATH', DEFAULT_LOCK_PATH)
    }
  }
}

export default lock
