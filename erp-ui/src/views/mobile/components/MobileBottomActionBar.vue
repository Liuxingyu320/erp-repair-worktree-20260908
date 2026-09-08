<template>
  <footer
    v-if="visible"
    :class="[
      'mobile-bottom-action-bar',
      layoutClass
    ]"
    role="toolbar"
    :aria-label="ariaLabel"
  >
    <button
      v-for="action in secondaryActions"
      :key="action.key || action.label"
      :class="['mobile-button', action.danger ? 'mobile-button--danger' : 'mobile-button--secondary']"
      type="button"
      :disabled="disabled || action.disabled || busy"
      @click="$emit('action', action)"
    >
      {{ busy && action.key === busyKey ? busyText : action.label }}
    </button>
    <button
      v-if="primaryAction"
      class="mobile-button mobile-button--primary"
      type="button"
      :disabled="disabled || primaryAction.disabled || busy"
      @click="$emit('action', primaryAction)"
    >
      {{ busy && (!busyKey || busyKey === primaryAction.key) ? busyText : primaryAction.label }}
    </button>
    <slot />
  </footer>
</template>

<script>
export default {
  name: "MobileBottomActionBar",
  props: {
    primaryAction: {
      type: Object,
      default: null
    },
    secondaryActions: {
      type: Array,
      default() {
        return []
      }
    },
    disabled: {
      type: Boolean,
      default: false
    },
    busy: {
      type: Boolean,
      default: false
    },
    busyKey: {
      type: String,
      default: ""
    },
    busyText: {
      type: String,
      default: "处理中…"
    },
    ariaLabel: {
      type: String,
      default: "页面主操作"
    }
  },
  computed: {
    visible() {
      return Boolean(this.primaryAction || (this.secondaryActions && this.secondaryActions.length) || this.$slots.default)
    },
    layoutClass() {
      const secondaryCount = Array.isArray(this.secondaryActions) ? this.secondaryActions.length : 0
      const hasPrimary = Boolean(this.primaryAction)
      if (hasPrimary && secondaryCount >= 2) return "mobile-bottom-action-bar--triple"
      if (hasPrimary && secondaryCount === 1) return "mobile-bottom-action-bar--split"
      return "mobile-bottom-action-bar--single"
    }
  },
  mounted() {
    document.body.classList.add("mobile-task-mode")
  },
  beforeDestroy() {
    document.body.classList.remove("mobile-task-mode")
  }
}
</script>
