<template>
  <span :class="['mobile-status-chip', `mobile-status-chip--${resolvedTone}`]" :title="label">
    <span class="mobile-status-chip__text">{{ label }}</span>
  </span>
</template>

<script>
const mobilePageState = require("../mobilePageState")
const resolveMobileStatusTone = typeof mobilePageState.resolveMobileStatusTone === "function"
  ? mobilePageState.resolveMobileStatusTone
  : function fallbackTone() { return "neutral" }

export default {
  name: "MobileStatusChip",
  props: {
    label: {
      type: String,
      default: ""
    },
    tone: {
      type: String,
      default: ""
    },
    status: {
      type: String,
      default: ""
    }
  },
  computed: {
    resolvedTone() {
      if (this.tone) return this.tone
      return resolveMobileStatusTone(this.status || this.label)
    }
  }
}
</script>
