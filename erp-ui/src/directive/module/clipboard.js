/**
* v-clipboard 文字复制剪贴
* Copyright (c) 2021 enterprise-admin
*/

let clipboardConstructorPromise

function loadClipboard() {
  if (!clipboardConstructorPromise) {
    clipboardConstructorPromise = import(
      /* webpackChunkName: "chunk-clipboard" */
      'clipboard'
    ).then(module => module.default || module).catch(error => {
      clipboardConstructorPromise = null
      throw error
    })
  }
  return clipboardConstructorPromise
}

function updateClipboardCallbacks(el, binding) {
  if (binding.arg === 'success') {
    el._vClipBoard_success = binding.value
  } else if (binding.arg === 'error') {
    el._vClipBoard_error = binding.value
  }
}

export default {
  bind(el, binding, vnode) {
    switch (binding.arg) {
      case 'success':
        el._vClipBoard_success = binding.value
        break
      case 'error':
        el._vClipBoard_error = binding.value
        break
      default: {
        const state = {
          active: true,
          action: binding.arg === 'cut' ? 'cut' : 'copy',
          value: binding.value
        }
        el._vClipBoardState = state
        loadClipboard().then(Clipboard => {
          if (!state.active || el._vClipBoardState !== state) return
          const clipboard = new Clipboard(el, {
            text: () => state.value,
            action: () => state.action
          })
          clipboard.on('success', e => {
            const callback = el._vClipBoard_success
            callback && callback(e)
          })
          clipboard.on('error', e => {
            const callback = el._vClipBoard_error
            callback && callback(e)
          })
          el._vClipBoard = clipboard
        }).catch(() => {
          if (!state.active || el._vClipBoardState !== state) return
          const callback = el._vClipBoard_error
          callback && callback(new Error('clipboard module unavailable'))
        })
      }
    }
  },
  update(el, binding) {
    if (binding.arg === 'success' || binding.arg === 'error') {
      updateClipboardCallbacks(el, binding)
      return
    }
    if (el._vClipBoardState) {
      el._vClipBoardState.value = binding.value
      el._vClipBoardState.action = binding.arg === 'cut' ? 'cut' : 'copy'
    }
  },
  unbind(el, binding) {
    if (binding.arg === 'success') {
      delete el._vClipBoard_success
    } else if (binding.arg === 'error') {
      delete el._vClipBoard_error
    } else {
      if (el._vClipBoardState) {
        el._vClipBoardState.active = false
      }
      if (el._vClipBoard) {
        el._vClipBoard.destroy()
      }
      delete el._vClipBoard
      delete el._vClipBoardState
    }
  }
}
