export const TODO_DEPT_CHANGED_EVENT = 'erp:dept-changed'

function getWindow() {
  return typeof window === 'undefined' ? null : window
}

export function addTodoRefreshListener(listener) {
  const target = getWindow()
  if (target && typeof target.addEventListener === 'function') {
    target.addEventListener(TODO_DEPT_CHANGED_EVENT, listener)
  }
}

export function removeTodoRefreshListener(listener) {
  const target = getWindow()
  if (target && typeof target.removeEventListener === 'function') {
    target.removeEventListener(TODO_DEPT_CHANGED_EVENT, listener)
  }
}
