import { getSelectedDeptId } from "@/utils/shopContext"

// System lists share one request lifetime; stale reads must not own the spinner.
export default {
  data() {
    return { listRequestId: 0, listPageActive: true, listResumeNeeded: false, listLoadedContext: "", loadError: "" }
  },
  activated() {
    this.listPageActive = true
    if (this.listResumeNeeded || (this.listLoadedContext && !this.isLoadedListContextCurrent())) {
      this.listResumeNeeded = false
      const retry = this.retryList || this.getList
      if (retry) retry.call(this)
    }
  },
  deactivated() { this.invalidateSystemList() },
  beforeDestroy() { this.invalidateSystemList() },
  methods: {
    invalidateSystemList() {
      this.listResumeNeeded = this.loading || Boolean(this.saving) || Boolean(this.loadError)
      this.listPageActive = false
      this.listRequestId += 1
      this.loading = false
      if (typeof this.saving === "boolean") this.saving = false
    },
    listContext() {
      return JSON.stringify([this.$route && this.$route.fullPath,
        this.$store && this.$store.getters && this.$store.getters.id, getSelectedDeptId()])
    },
    isLoadedListContextCurrent() {
      return this.listPageActive && Boolean(this.listLoadedContext) && this.listLoadedContext === this.listContext()
    },
    requireLoadedListContext() {
      if (this.isLoadedListContextCurrent()) return true
      this.loadError = "账号或组织已变化，请重新加载"
      return false
    },
    async runSystemListRequest(read, apply) {
      const requestId = ++this.listRequestId, context = this.listContext()
      const current = () => this.listPageActive && requestId === this.listRequestId && context === this.listContext()
      this.loading = true
      this.loadError = ""
      try {
        const result = await read(current)
        if (!current()) return null
        apply(result)
        this.listLoadedContext = context
        return result
      } catch (error) {
        if (current()) this.loadError = error && error.message || "加载失败，请重试"
        return null
      } finally {
        if (this.listPageActive && requestId === this.listRequestId) {
          this.loading = false
          if (context !== this.listContext()) this.loadError = "访问范围已变化，请重新加载"
        }
      }
    }
  }
}
