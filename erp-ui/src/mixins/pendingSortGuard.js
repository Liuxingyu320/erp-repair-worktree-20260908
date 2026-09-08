const MAX_ORDER_NUM = 999999

export default {
  data() {
    return {
      originalOrders: {},
      sortSaving: false,
      sortSaveError: ""
    }
  },
  computed: {
    pendingSortChanges() {
      return this.collectSortChanges()
    },
    pendingSortCount() {
      return this.pendingSortChanges.length
    },
    hasPendingSortChanges() {
      return this.pendingSortCount > 0
    }
  },
  created() {
    if (typeof window !== "undefined") {
      window.addEventListener("beforeunload", this.handlePendingSortBeforeUnload)
    }
  },
  beforeDestroy() {
    if (typeof window !== "undefined") {
      window.removeEventListener("beforeunload", this.handlePendingSortBeforeUnload)
    }
  },
  beforeRouteLeave(to, from, next) {
    if (!this.hasPendingSortChanges) {
      next()
      return
    }
    this.confirmPendingSortDiscard("离开页面将丢失尚未保存的排序，是否继续？")
      .then(() => next())
      .catch(() => next(false))
  },
  methods: {
    getSortRows() {
      return this.sortListField ? (this[this.sortListField] || []) : []
    },
    flattenSortRows(rows) {
      const flattened = []
      const visit = list => (list || []).forEach(row => {
        flattened.push(row)
        visit(row.children)
      })
      visit(rows)
      return flattened
    },
    recordOriginalOrders(rows) {
      const baseline = {}
      this.flattenSortRows(rows || this.getSortRows()).forEach(row => {
        baseline[String(row[this.sortIdField])] = Number(row.orderNum)
      })
      this.originalOrders = baseline
      this.sortSaveError = ""
    },
    collectSortChanges() {
      if (!this.sortIdField) return []
      return this.flattenSortRows(this.getSortRows()).reduce((changes, row) => {
        const id = Number(row[this.sortIdField])
        const expectedOrderNum = this.originalOrders[String(id)]
        const newOrderNum = Number(row.orderNum)
        if (Number.isSafeInteger(id) && id > 0 &&
          Number.isInteger(expectedOrderNum) &&
          String(expectedOrderNum) !== String(newOrderNum)) {
          changes.push({ id, expectedOrderNum, newOrderNum })
        }
        return changes
      }, [])
    },
    isSortItemDirty(row) {
      if (!row || !this.sortIdField) return false
      const id = row[this.sortIdField]
      return String(this.originalOrders[String(id)]) !== String(row.orderNum)
    },
    validatePendingSortChanges() {
      const invalid = this.flattenSortRows(this.getSortRows()).find(row => {
        if (!this.isSortItemDirty(row)) return false
        const value = Number(row.orderNum)
        return !Number.isInteger(value) || value < 0 || value > MAX_ORDER_NUM
      })
      if (invalid) {
        this.$modal.msgWarning("排序值必须是0到999999之间的整数")
        return false
      }
      return true
    },
    buildSortChangeRequest() {
      return { changes: this.collectSortChanges() }
    },
    confirmPendingSortDiscard(message) {
      return this.$modal.confirm(message || "当前排序尚未保存，继续将丢失本地调整，是否继续？")
    },
    runAfterPendingSortConfirmation(action, message) {
      if (!this.hasPendingSortChanges) return Promise.resolve().then(action)
      return this.confirmPendingSortDiscard(message).then(action)
    },
    handleSortRefresh() {
      return this.runAfterPendingSortConfirmation(
        () => this.getList(),
        "刷新列表将丢失尚未保存的排序，是否继续？"
      ).catch(() => {})
    },
    handlePendingSortBeforeUnload(event) {
      if (!this.hasPendingSortChanges) return undefined
      event.preventDefault()
      event.returnValue = ""
      return ""
    },
    handleSortSaveFailure(error) {
      const data = error && error.response ? error.response.data : null
      const conflict = data && data.businessCode === "SORT_CONFLICT"
      const conflictIds = conflict && Array.isArray(data.conflictIds) ? data.conflictIds.slice(0, 20) : []
      this.sortSaveError = conflict
        ? `服务器排序基线已变化，本地修改已保留${conflictIds.length ? `（冲突 ID：${conflictIds.join("、")}）` : ""}。请核对后刷新基线再重试。`
        : "排序保存失败，本地修改已保留，可以直接重试。"
      if (conflict) this.$modal.msgWarning(this.sortSaveError)
      else this.$modal.msgError(this.sortSaveError)
    }
  }
}
