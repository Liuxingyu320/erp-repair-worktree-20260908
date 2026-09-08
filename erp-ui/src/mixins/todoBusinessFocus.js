const {
  buildTodoFocusQuery,
  consumeTodoFocusQuery,
  getTodoFocus,
  getTodoFocusActionIds,
  normalizeTodoFocusRow,
  resolveTodoFocusResult
} = require("../utils/todoBusinessFocus")

function createTodoBusinessFocusMixin(options) {
  const config = options || {}
  const featureKey = config.featureKey || ""
  const actions = config.actions || {}
  const loadFocusedRow = config.loadFocusedRow

  return {
    data() {
      return { todoBusinessFocus: null }
    },
    created() {
      const routeQuery = (this.$route && this.$route.query) || {}
      this.todoBusinessFocus = getTodoFocus(featureKey, routeQuery)
      if (!this.todoBusinessFocus) return
      this.queryParams = Object.assign(
        {},
        this.queryParams || {},
        buildTodoFocusQuery(featureKey, routeQuery),
        { pageNum: 1 }
      )
    },
    methods: {
      loadTodoBusinessList(listLoader) {
        if (this.todoBusinessFocus && typeof loadFocusedRow === "function") {
          return Promise.resolve(loadFocusedRow.call(
            this,
            this.todoBusinessFocus.targetId,
            this.todoBusinessFocus
          )).then(response => {
            const row = response && Object.prototype.hasOwnProperty.call(response, "data")
              ? response.data
              : response
            return row && typeof row === "object"
              ? { rows: [normalizeTodoFocusRow(row, this.todoBusinessFocus)], total: 1 }
              : { rows: [], total: 0 }
          }).catch(error => {
            const status = error && error.response && error.response.status
            const code = error && (error.code || error.response && error.response.data && error.response.data.code)
            if (status === 404 || code === 404) return { rows: [], total: 0 }
            throw error
          })
        }
        return Promise.resolve().then(listLoader)
      },
      handleTodoFocusRows(rows) {
        if (!this.todoBusinessFocus) return Promise.resolve({ status: "none" })
        const focus = this.todoBusinessFocus
        this.todoBusinessFocus = null
        const result = resolveTodoFocusResult(rows, focus)
        const consumed = this.consumeTodoBusinessFocus()

        if (result.status === "handled") {
          this.showTodoBusinessHandled()
          return consumed.then(() => result)
        }
        if (result.status !== "found") return consumed.then(() => result)

        const actionId = getTodoFocusActionIds(focus).find(candidate =>
          typeof actions[candidate] === "function"
        )
        const action = actions[actionId]
        if (typeof action !== "function") {
          this.showTodoBusinessHandled()
          return consumed.then(() => ({ status: "handled", row: null, actionId: result.actionId }))
        }
        return consumed.then(() => {
          action.call(this, result.row, focus)
          return Object.assign({}, result, { actionId })
        })
      },
      consumeTodoBusinessFocus() {
        const routeQuery = (this.$route && this.$route.query) || {}
        if (!routeQuery.todoType || !this.$router || typeof this.$router.replace !== "function") {
          return Promise.resolve()
        }
        return Promise.resolve(this.$router.replace({
          path: this.$route.path,
          query: consumeTodoFocusQuery(routeQuery)
        })).catch(() => {})
      },
      showTodoBusinessHandled() {
        if (this.$modal && typeof this.$modal.msgWarning === "function") {
          this.$modal.msgWarning("事项已处理")
        }
        if (this.$store && typeof this.$store.dispatch === "function") {
          return this.$store.dispatch("todo/refreshSummaries").catch(() => {})
        }
        return Promise.resolve()
      }
    }
  }
}

function createTodoPersonalFocusMixin(options) {
  const config = options || {}
  const featureKey = config.featureKey || ""
  const idKey = config.idKey || ""
  const loadFocusedRow = config.loadFocusedRow
  const openFocusedRow = config.openFocusedRow
  const isActionable = typeof config.isActionable === "function" ? config.isActionable : () => true

  function responseRow(response) {
    return response && Object.prototype.hasOwnProperty.call(response, "data") ? response.data : response
  }

  function isHandledError(error) {
    const status = error && error.response && error.response.status
    const code = error && (error.code || error.response && error.response.data && error.response.data.code)
    const message = error && error.response && error.response.data &&
      (error.response.data.msg || error.response.data.message)
    return status === 404 || code === 404 ||
      /不存在/.test(String(message || error && error.message || ""))
  }

  return {
    data() {
      return { todoPersonalFocus: null }
    },
    methods: {
      initializeTodoPersonalFocus() {
        const routeQuery = (this.$route && this.$route.query) || {}
        this.todoPersonalFocus = getTodoFocus(featureKey, routeQuery)
        return this.todoPersonalFocus ? this.loadTodoPersonalFocus() : this.loadList()
      },
      loadTodoPersonalFocus() {
        const focus = this.todoPersonalFocus
        this.todoPersonalFocus = null
        if (!focus || typeof loadFocusedRow !== "function") return Promise.resolve({ status: "none" })
        this.loading = true
        return Promise.resolve(loadFocusedRow.call(this, focus.targetId, focus)).then(response => {
          const row = responseRow(response)
          if (!row || typeof row !== "object") return this.showTodoPersonalHandled()
          const normalized = normalizeTodoFocusRow(row, focus)
          if (!isActionable.call(this, normalized, focus)) return this.showTodoPersonalHandled()
          return Promise.resolve(openFocusedRow.call(this, normalized, focus)).then(() => ({
            status: "found",
            row: normalized,
            actionId: focus.actionId
          }))
        }).catch(error => {
          if (isHandledError(error)) return this.showTodoPersonalHandled()
          throw error
        }).then(result => this.consumeTodoPersonalFocus().then(() => result)).finally(() => {
          this.loading = false
        })
      },
      recheckTodoPersonalAction(targetId) {
        if (typeof loadFocusedRow !== "function") return Promise.resolve(null)
        return Promise.resolve(loadFocusedRow.call(this, targetId)).then(response => {
          const row = responseRow(response)
          const normalized = row && typeof row === "object" && idKey
            ? Object.assign({}, row, { [idKey]: String(targetId) })
            : row
          if (!normalized || typeof normalized !== "object" || !isActionable.call(this, normalized)) {
            return this.showTodoPersonalHandled().then(() => null)
          }
          return normalized
        }).catch(error => {
          if (isHandledError(error)) return this.showTodoPersonalHandled().then(() => null)
          throw error
        })
      },
      consumeTodoPersonalFocus() {
        const routeQuery = (this.$route && this.$route.query) || {}
        if (!routeQuery.todoType || !this.$router || typeof this.$router.replace !== "function") {
          return Promise.resolve()
        }
        return Promise.resolve(this.$router.replace({
          path: this.$route.path,
          query: consumeTodoFocusQuery(routeQuery)
        })).catch(() => {})
      },
      showTodoPersonalHandled() {
        if (this.$modal && typeof this.$modal.msgWarning === "function") {
          this.$modal.msgWarning("事项已处理")
        }
        if (this.$store && typeof this.$store.dispatch === "function") {
          return this.$store.dispatch("todo/refreshSummaries").catch(() => {})
        }
        return Promise.resolve()
      }
    }
  }
}

module.exports = { createTodoBusinessFocusMixin, createTodoPersonalFocusMixin }
