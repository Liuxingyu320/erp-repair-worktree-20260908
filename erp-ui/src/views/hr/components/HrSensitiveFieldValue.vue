<template>
  <span class="hr-sensitive-value">
    <span>{{ displayValue }}</span>
    <el-button
      v-if="canReveal"
      type="text"
      size="mini"
      :loading="revealing"
      v-hasPermi="['hr:employee:sensitive:view']"
      @click="reveal"
    >{{ revealed ? "已查看" : "查看原值" }}</el-button>
    <span v-if="errorMessage" class="hr-sensitive-error">{{ errorMessage }}</span>
  </span>
</template>

<script>
import { revealHrEmployeeSensitiveField } from "@/api/hr/employee"

export default {
  name: "HrSensitiveFieldValue",
  props: {
    maskedValue: {
      type: [String, Number, Boolean],
      default: ""
    },
    field: {
      type: String,
      required: true
    },
    employeeId: {
      type: [String, Number],
      required: true
    }
  },
  data() {
    return {
      revealed: false,
      revealedValue: undefined,
      revealing: false,
      errorMessage: "",
      revealRequestSequence: 0
    }
  },
  computed: {
    canReveal() {
      return Boolean(this.employeeId) && !this.revealed
    },
    displayValue() {
      const value = this.revealed ? this.revealedValue : this.maskedValue
      return value === undefined || value === null || value === "" ? "-" : value
    }
  },
  watch: {
    employeeId() {
      this.resetReveal()
    },
    field() {
      this.resetReveal()
    },
    maskedValue() {
      this.resetReveal()
    }
  },
  methods: {
    resetReveal() {
      this.revealRequestSequence += 1
      this.revealed = false
      this.revealedValue = undefined
      this.revealing = false
      this.errorMessage = ""
    },
    reveal() {
      if (!this.canReveal || this.revealing) return
      const requestSequence = ++this.revealRequestSequence
      const employeeId = this.employeeId
      const field = this.field
      this.revealing = true
      this.errorMessage = ""
      revealHrEmployeeSensitiveField(employeeId, field).then(response => {
        if (!this.isCurrentReveal(requestSequence, employeeId, field)) return
        const result = response && response.data
        if (!result || result.fieldKey !== field || !("value" in result)) {
          throw new Error("敏感字段返回格式无效")
        }
        this.revealedValue = result.value
        this.revealed = true
      }).catch(() => {
        if (!this.isCurrentReveal(requestSequence, employeeId, field)) return
        this.revealedValue = undefined
        this.revealed = false
        this.errorMessage = "查看失败"
      }).finally(() => {
        if (!this.isCurrentReveal(requestSequence, employeeId, field)) return
        this.revealing = false
      })
    },
    isCurrentReveal(requestSequence, employeeId, field) {
      return requestSequence === this.revealRequestSequence &&
        employeeId === this.employeeId && field === this.field
    }
  }
}
</script>

<style lang="scss" scoped>
.hr-sensitive-value {
  display: inline-flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
}

.hr-sensitive-error {
  color: #f56c6c;
  font-size: 12px;
}
</style>
