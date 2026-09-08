<template>
  <section class="system-page-heading"
    :class="'system-page-heading--' + resolvedTone"
    :aria-labelledby="headingId"
  >
    <div class="system-page-heading__identity">
      <span class="system-page-heading__icon" aria-hidden="true">
        <i :class="icon" />
      </span>
      <div class="system-page-heading__copy">
        <div class="system-page-heading__eyebrow">
          <span class="system-page-heading__dot" aria-hidden="true" />
          {{ eyebrow }}
        </div>
        <h1 :id="headingId">{{ title }}</h1>
        <p v-if="description">{{ description }}</p>
      </div>
    </div>

    <div v-if="tip || $slots.actions" class="system-page-heading__aside">
      <slot name="actions" />
      <span v-if="tip" class="system-page-heading__tip">
        <i class="el-icon-info" aria-hidden="true" />
        {{ tip }}
      </span>
    </div>
  </section>
</template>

<script>
export default {
  name: "SystemPageHeader",
  props: {
    title: {
      type: String,
      required: true
    },
    description: {
      type: String,
      default: ""
    },
    eyebrow: {
      type: String,
      default: "系统管理"
    },
    icon: {
      type: String,
      default: "el-icon-setting"
    },
    tip: {
      type: String,
      default: ""
    },
    tone: {
      type: String,
      default: ""
    }
  },
  computed: {
    headingId() {
      return `system-page-heading-${this._uid}`
    },
    resolvedTone() {
      if (this.tone) return this.tone

      const icon = String(this.icon || "").toLowerCase()
      if (/(user|custom|postcard|connection|check)/.test(icon)) return "violet"
      if (/(office|building|shop|dept)/.test(icon)) return "sage"
      if (/(bell|message|notice|key|document-checked)/.test(icon)) return "amber"
      if (/(wallet|money|warning|lock)/.test(icon)) return "rose"
      return "blue"
    }
  }
}
</script>

<style lang="scss" scoped>
.system-page-heading {
  --heading-accent: var(--erp-interactive, #1473e6);
  --heading-accent-soft: var(--erp-interactive-soft, #eef6ff);
  --heading-accent-line: var(--erp-interactive-muted, #c9e0ff);
  --heading-companion-soft: var(--erp-accent-amber-soft, #fff7e8);
  position: relative;
  display: flex;
  align-items: center;
  justify-content: space-between;
  isolation: isolate;
  gap: 20px;
  min-height: 84px;
  margin-bottom: 14px;
  padding: 16px 20px;
  overflow: hidden;
  color: var(--erp-text, #17211d);
  background: #ffffff;
  border: 1px solid var(--erp-border, #dde2de);
  border-radius: var(--erp-radius-md, 12px);
  box-shadow: var(--erp-shadow-card, 0 8px 22px rgba(23, 33, 29, 0.055));
}

.system-page-heading::before {
  position: absolute;
  top: 18px;
  bottom: 18px;
  left: 0;
  width: 3px;
  background: linear-gradient(180deg, transparent 0%, var(--heading-accent) 24%, var(--heading-accent) 76%, transparent 100%);
  border-radius: 0 4px 4px 0;
  content: "";
  opacity: 0.94;
}

.system-page-heading::after {
  position: absolute;
  z-index: -1;
  top: -66px;
  right: 34px;
  width: 176px;
  height: 176px;
  border: 1px solid var(--heading-accent-line);
  border-radius: 50%;
  content: "";
  opacity: 0.42;
}

.system-page-heading--sage {
  --heading-accent: var(--erp-accent-sage, #178a58);
  --heading-accent-soft: var(--erp-accent-sage-soft, #ecfbf3);
  --heading-accent-line: #bfead3;
  --heading-companion-soft: var(--erp-accent-amber-soft, #fff7e8);
}

.system-page-heading--amber {
  --heading-accent: var(--erp-accent-amber, #c47400);
  --heading-accent-soft: var(--erp-accent-amber-soft, #fff7e8);
  --heading-accent-line: #f2d39b;
  --heading-companion-soft: var(--erp-accent-rose-soft, #fff0f3);
}

.system-page-heading--rose {
  --heading-accent: var(--erp-accent-rose, #cf3f5a);
  --heading-accent-soft: var(--erp-accent-rose-soft, #fff0f3);
  --heading-accent-line: #f3c3cc;
  --heading-companion-soft: var(--erp-accent-violet-soft, #f5f0ff);
}

.system-page-heading--violet {
  --heading-accent: var(--erp-accent-violet, #7456d8);
  --heading-accent-soft: var(--erp-accent-violet-soft, #f5f0ff);
  --heading-accent-line: #d9ccfb;
  --heading-companion-soft: var(--erp-interactive-soft, #eef6ff);
}

.system-page-heading__identity {
  display: flex;
  align-items: center;
  min-width: 0;
  gap: 14px;
}

.system-page-heading__icon {
  display: inline-flex;
  flex: 0 0 48px;
  align-items: center;
  justify-content: center;
  width: 48px;
  height: 48px;
  color: var(--heading-accent);
  font-size: 22px;
  background: var(--heading-accent-soft);
  border: 1px solid var(--heading-accent-line);
  border-radius: 12px;
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.82), 0 5px 14px rgba(40, 48, 56, 0.055);
}

.system-page-heading__copy {
  min-width: 0;
}

.system-page-heading__eyebrow {
  display: flex;
  align-items: center;
  gap: 7px;
  margin-bottom: 3px;
  color: var(--heading-accent);
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0.08em;
}

.system-page-heading__dot {
  width: 6px;
  height: 6px;
  background: var(--heading-accent);
  border-radius: 50%;
  box-shadow: 0 0 0 4px var(--heading-accent-soft);
}

.system-page-heading h1 {
  margin: 0;
  color: var(--erp-text, #17211d);
  font-size: 22px;
  font-weight: 700;
  line-height: 1.3;
  letter-spacing: -0.02em;
}

.system-page-heading p {
  margin: 4px 0 0;
  overflow: hidden;
  color: var(--erp-text-secondary, #66736d);
  font-size: 13px;
  line-height: 1.55;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.system-page-heading__aside {
  display: flex;
  flex: 0 0 auto;
  align-items: center;
  justify-content: flex-end;
  gap: 10px;
}

.system-page-heading__tip {
  display: inline-flex;
  align-items: center;
  gap: 7px;
  max-width: 520px;
  padding: 8px 11px;
  color: #52605a;
  font-size: 12px;
  line-height: 1.4;
  background: var(--erp-surface-muted, #f8f8f5);
  border: 1px solid var(--erp-border, #dde2de);
  border-radius: 10px;
}

.system-page-heading__tip i {
  color: var(--heading-accent);
}

@media (max-width: 1200px) {
  .system-page-heading {
    align-items: flex-start;
  }

  .system-page-heading__tip {
    max-width: 340px;
  }
}

@media (min-width: 769px) and (max-height: 820px) {
  .system-page-heading {
    min-height: 76px;
    margin-bottom: 10px;
    padding: 12px 18px;
    border-radius: 14px;
  }

  .system-page-heading__identity {
    gap: 12px;
  }

  .system-page-heading__icon {
    flex-basis: 42px;
    width: 42px;
    height: 42px;
    border-radius: 12px;
  }

  .system-page-heading h1 {
    font-size: 20px;
  }

  .system-page-heading p {
    margin-top: 2px;
    line-height: 1.45;
  }
}

@media (max-width: 768px) {
  .system-page-heading {
    flex-direction: column;
    align-items: stretch;
    min-height: 0;
    padding: 14px 16px;
  }

  .system-page-heading__icon {
    flex-basis: 42px;
    width: 42px;
    height: 42px;
    border-radius: 12px;
  }

  .system-page-heading h1 {
    font-size: 20px;
  }

  .system-page-heading p {
    white-space: normal;
  }

  .system-page-heading__aside {
    justify-content: flex-start;
    flex-wrap: wrap;
  }
}
</style>
