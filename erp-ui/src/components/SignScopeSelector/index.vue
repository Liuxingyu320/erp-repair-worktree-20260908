<template>
  <div class="sign-scope-selector">
    <span v-if="showLabel" class="sign-scope-selector__label">签约组织</span>
    <el-select
      v-model="selectedDeptId"
      :loading="loading"
      :disabled="disabled"
      filterable
      placeholder="请选择签约组织"
      size="small"
      @change="handleChange"
    >
      <el-option
        v-for="option in options"
        :key="String(option.deptId)"
        :label="optionLabel(option)"
        :value="String(option.deptId)"
      />
    </el-select>
    <small v-if="!loading && loadError" class="sign-scope-selector__empty">{{ loadError }}</small>
    <small v-else-if="!loading && !options.length" class="sign-scope-selector__empty">当前账号无可用签约组织</small>
  </div>
</template>

<script>
import { listSignScopeOptions } from "@/api/oa/signPackage"
import { getSelectedDeptContext } from "@/utils/shopContext"
import {
  clearSelectedSignScope,
  getSelectedSignScopeDeptId,
  setSelectedSignScope
} from "@/utils/signScopeContext"

export default {
  name: "SignScopeSelector",
  props: {
    disabled: { type: Boolean, default: false },
    showLabel: { type: Boolean, default: true }
  },
  data() {
    return {
      loading: false,
      loadError: "",
      options: [],
      selectedDeptId: getSelectedSignScopeDeptId() || ""
    }
  },
  created() {
    this.loadOptions()
  },
  methods: {
    loadOptions() {
      this.loading = true
      this.loadError = ""
      return listSignScopeOptions().then(response => {
        const options = Array.isArray(response.data) ? response.data : []
        this.options = this.rootCompanyOptions(options)
        this.initializeSelection()
        this.$emit("ready", this.selectedOption())
        return this.options
      }).catch(error => {
        this.loadError = error && error.message
          ? error.message
          : "签约组织加载失败，请稍后重试"
        this.options = []
        this.selectedDeptId = ""
        clearSelectedSignScope()
        this.$emit("ready", null)
        return []
      }).finally(() => {
        this.loading = false
      })
    },
    initializeSelection() {
      const persistedId = getSelectedSignScopeDeptId()
      let selected = this.options.find(option => String(option.deptId) === String(persistedId))
      if (!selected) {
        const legacy = getSelectedDeptContext()
        if (legacy.isStore) {
          selected = this.options.find(option => String(option.deptId) === String(legacy.deptId))
        }
      }
      if (!selected && this.options.length === 1) selected = this.options[0]
      if (selected) {
        this.selectedDeptId = String(selected.deptId)
        setSelectedSignScope(selected)
      } else {
        this.selectedDeptId = ""
        clearSelectedSignScope()
      }
    },
    handleChange(deptId) {
      const selected = this.options.find(option => String(option.deptId) === String(deptId))
      if (!selected || !setSelectedSignScope(selected)) return
      this.$emit("change", selected)
    },
    selectedOption() {
      return this.options.find(option => String(option.deptId) === String(this.selectedDeptId)) || null
    },
    isCompanyOption(option) {
      return String(option && option.deptType || "").trim().toUpperCase() === "COMPANY"
    },
    rootCompanyOptions(options) {
      const visibleDeptIds = new Set(options.map(option => String(option && option.deptId || "")))
      return options.filter(option => this.isCompanyOption(option) &&
        !visibleDeptIds.has(String(option && option.parentId || "")))
    },
    optionLabel(option) {
      return `公司：${option.deptName || option.deptId}`
    }
  }
}
</script>

<style scoped>
.sign-scope-selector { display: inline-flex; align-items: center; gap: 8px; }
.sign-scope-selector__label { color: #475569; font-size: 13px; font-weight: 600; }
.sign-scope-selector .el-select { width: 220px; }
.sign-scope-selector__empty { color: #c2410c; white-space: nowrap; }
</style>
