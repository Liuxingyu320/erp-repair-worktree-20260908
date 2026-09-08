<template>
  <el-select
    :value="value"
    filterable
    remote
    clearable
    :remote-method="search"
    :loading="loading"
    placeholder="搜索姓名或登录账号"
    class="supervisor-user-select"
    @visible-change="handleVisibleChange"
    @change="handleChange"
  >
    <el-option
      v-for="item in options"
      :key="item.userId"
      :label="optionLabel(item)"
      :value="item.userId"
    >
      <div class="supervisor-option">
        <span>{{ item.nickName || item.userName }}</span>
        <small>{{ item.userName }} · {{ item.deptName || "未归属部门" }}</small>
      </div>
    </el-option>
  </el-select>
</template>

<script>
import { listUserOptions } from "@/api/system/user"

export default {
  name: "SupervisorUserSelect",
  props: {
    value: { type: [Number, String], default: undefined },
    currentUserId: { type: [Number, String], default: undefined },
    currentLabel: { type: String, default: "" }
  },
  data() {
    return {
      loading: false,
      options: [],
      requestSequence: 0
    }
  },
  watch: {
    value: {
      immediate: true,
      handler(value) {
        if (value && this.currentLabel && !this.options.some(item => String(item.userId) === String(value))) {
          this.options.unshift({ userId: value, nickName: this.currentLabel, userName: "当前主管", deptName: "" })
        }
      }
    }
  },
  methods: {
    optionLabel(item) {
      const name = item.nickName || item.userName || "未命名用户"
      return `${name}（${item.userName || item.userId}）`
    },
    handleVisibleChange(visible) {
      if (visible && this.options.length <= 1) this.search("")
    },
    search(keyword) {
      const sequence = ++this.requestSequence
      this.loading = true
      listUserOptions({ keyword, excludeUserId: this.currentUserId, limit: 50 }).then(response => {
        if (sequence !== this.requestSequence) return
        const rows = (response && response.data) || []
        const current = this.options.find(item => String(item.userId) === String(this.value))
        this.options = current && !rows.some(item => String(item.userId) === String(current.userId))
          ? [current, ...rows]
          : rows
      }).finally(() => {
        if (sequence === this.requestSequence) this.loading = false
      })
    },
    handleChange(userId) {
      const selected = this.options.find(item => String(item.userId) === String(userId)) || null
      this.$emit("input", userId || undefined)
      this.$emit("change", selected)
    }
  }
}
</script>

<style lang="scss" scoped>
.supervisor-user-select { width: 100%; }
.supervisor-option { display: flex; justify-content: space-between; gap: 16px; }
.supervisor-option small { color: #909399; }
</style>
