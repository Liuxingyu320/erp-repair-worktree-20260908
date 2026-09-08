<template>
  <section
    v-if="state && state.type && state.type !== 'ready'"
    :class="rootClass"
    :role="role"
    :aria-live="ariaLive"
  >
    <strong class="mobile-data-state__title">{{ state.title }}</strong>
    <p v-if="state.description" class="mobile-data-state__description">{{ state.description }}</p>
    <div v-if="isLoading" class="mobile-data-state__skeleton" aria-hidden="true">
      <span></span>
      <span></span>
      <span></span>
    </div>
    <slot name="extra" />
    <button
      v-if="showAction"
      class="mobile-button mobile-button--secondary mobile-data-state__action"
      type="button"
      :disabled="actionDisabled"
      @click="$emit('action', state.actionId, state)"
    >
      {{ actionLabel }}
    </button>
    <slot />
  </section>
</template>

<script>
const mobilePageState = require("../mobilePageState")
const resolveMobilePageStateActionLabel = typeof mobilePageState.resolveMobilePageStateActionLabel === "function"
  ? mobilePageState.resolveMobilePageStateActionLabel
  : function resolveFallback(state) {
    return state && state.actionLabel ? state.actionLabel : ""
  }

export default {
  name: "MobileDataState",
  props: {
    state: {
      type: Object,
      default: null
    },
    actions: {
      type: Array,
      default() {
        return []
      }
    },
    compact: {
      type: Boolean,
      default: false
    },
    inline: {
      type: Boolean,
      default: false
    },
    actionDisabled: {
      type: Boolean,
      default: false
    }
  },
  computed: {
    rootClass() {
      const type = this.state && this.state.type ? this.state.type : "empty"
      return [
        "mobile-data-state",
        "mobile-state",
        `mobile-data-state--${type}`,
        {
          "mobile-data-state--compact": this.compact,
          "mobile-data-state--inline": this.inline,
          "mobile-state--error": ["session-expired", "permission-error", "network-error", "error"].includes(type)
        }
      ]
    },
    role() {
      const type = this.state && this.state.type
      if (["session-expired", "permission-error", "network-error", "error"].includes(type)) return "alert"
      if (type === "loading") return "status"
      return "status"
    },
    ariaLive() {
      const type = this.state && this.state.type
      if (["session-expired", "permission-error", "network-error", "error"].includes(type)) return "assertive"
      return "polite"
    },
    showAction() {
      return Boolean(this.state && this.state.actionId && this.state.type !== "loading")
    },
    isLoading() {
      return Boolean(this.state && this.state.type === "loading")
    },
    actionLabel() {
      return resolveMobilePageStateActionLabel(this.state, this.actions)
    }
  }
}
</script>
