<template>
  <section
    class="data-state"
    :class="`data-state--${type}`"
    :role="stateRole"
    :aria-live="type === 'error' ? 'assertive' : 'polite'"
    :aria-busy="type === 'loading' ? 'true' : 'false'"
  >
    <span class="data-state__icon" aria-hidden="true">
      <i :class="resolvedIcon" />
    </span>
    <h3>{{ title }}</h3>
    <p v-if="description">{{ description }}</p>
    <div v-if="$slots.default" class="data-state__actions">
      <slot />
    </div>
  </section>
</template>

<script>
const TYPE_ICONS = {
  empty: "el-icon-document",
  error: "el-icon-warning-outline",
  loading: "el-icon-loading"
}

export default {
  name: "DataState",
  props: {
    type: {
      type: String,
      default: "empty",
      validator: value => ["empty", "error", "loading"].includes(value)
    },
    title: {
      type: String,
      required: true
    },
    description: {
      type: String,
      default: ""
    },
    icon: {
      type: String,
      default: ""
    }
  },
  computed: {
    resolvedIcon() {
      return this.icon || TYPE_ICONS[this.type] || TYPE_ICONS.empty
    },
    stateRole() {
      return this.type === "error" ? "alert" : "status"
    }
  }
}
</script>

<style lang="scss" scoped>
.data-state {
  display: flex;
  width: min(100%, 620px);
  min-height: 178px;
  margin: 0 auto;
  padding: 28px 24px;
  align-items: center;
  justify-content: center;
  flex-direction: column;
  color: var(--erp-text, #17211d);
  text-align: center;
}

.data-state__icon {
  display: inline-flex;
  width: 48px;
  height: 48px;
  align-items: center;
  justify-content: center;
  margin-bottom: 12px;
  color: var(--erp-primary, #0b6b53);
  font-size: 22px;
  border: 1px solid var(--erp-primary-muted, #d6e9e1);
  border-radius: 14px;
  background: var(--erp-primary-soft, #e7f2ed);
}

.data-state h3 {
  margin: 0;
  color: inherit;
  font-size: 15px;
  font-weight: 700;
  line-height: 1.45;
}

.data-state p {
  max-width: 560px;
  margin: 7px 0 0;
  color: var(--erp-text-secondary, #66736d);
  font-size: 13px;
  line-height: 1.65;
}

.data-state__actions {
  display: flex;
  align-items: center;
  justify-content: center;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 16px;
}

.data-state--error .data-state__icon {
  color: var(--erp-danger, #c4322b);
  border-color: #efc8c4;
  background: #fff0ee;
}

.data-state--loading .data-state__icon {
  color: var(--erp-info, #2866b1);
  border-color: #c9ddf4;
  background: #edf4fd;
}

@media (min-width: 992px) and (max-height: 820px) {
  .data-state {
    min-height: 150px;
    padding: 20px;
  }

  .data-state__icon {
    width: 42px;
    height: 42px;
    margin-bottom: 9px;
  }

  .data-state__actions {
    margin-top: 12px;
  }
}
</style>
