<template>
  <section
    class="inventory-page-hero"
    :class="'inventory-page-hero--' + tone"
    :aria-label="title"
  >
    <div class="inventory-page-hero__grid" aria-hidden="true"></div>

    <div class="inventory-page-hero__copy">
      <div class="inventory-page-hero__eyebrow">
        <span class="inventory-page-hero__eyebrow-dot"></span>
        {{ eyebrow }}
      </div>
      <h1>{{ title }}</h1>
      <p>{{ description }}</p>

      <div class="inventory-page-hero__meta">
        <span v-if="showContext" class="inventory-page-hero__chip inventory-page-hero__chip--context">
          <i :class="contextIcon"></i>
          <strong>{{ contextTypeLabel }}</strong>
          <span>{{ contextName }}</span>
        </span>
        <span class="inventory-page-hero__chip">
          <i class="el-icon-aim"></i>
          {{ scopeText }}
        </span>
      </div>
    </div>

    <div class="inventory-page-hero__visual" aria-hidden="true">
      <div class="inventory-page-hero__icon-shell">
        <span class="inventory-page-hero__icon-glow"></span>
        <i :class="icon"></i>
      </div>
      <div v-if="normalizedFeatures.length" class="inventory-page-hero__feature-list">
        <div
          v-for="(feature, index) in normalizedFeatures"
          :key="feature + index"
          class="inventory-page-hero__feature"
        >
          <span class="inventory-page-hero__feature-index">0{{ index + 1 }}</span>
          <span class="inventory-page-hero__feature-label">{{ feature }}</span>
        </div>
      </div>
    </div>
  </section>
</template>

<script>
import { getSelectedDeptContext } from "@/utils/shopContext"

export default {
  name: "InventoryPageHero",
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
      default: "仓库管理"
    },
    scopeText: {
      type: String,
      default: "按当前组织范围展示"
    },
    icon: {
      type: String,
      default: "el-icon-box"
    },
    tone: {
      type: String,
      default: "indigo"
    },
    features: {
      type: Array,
      default: () => []
    },
    showContext: {
      type: Boolean,
      default: true
    }
  },
  computed: {
    selectedContext() {
      return getSelectedDeptContext() || {}
    },
    contextName() {
      return this.selectedContext.deptName || "未选择业务组织"
    },
    contextTypeLabel() {
      if (this.selectedContext.isWarehouse) return "当前仓库"
      if (this.selectedContext.isStore) return "当前门店"
      return "当前组织"
    },
    contextIcon() {
      return this.selectedContext.isWarehouse ? "el-icon-office-building" : "el-icon-location-outline"
    },
    normalizedFeatures() {
      return this.features.filter(Boolean).slice(0, 3)
    }
  }
}
</script>

<style lang="scss" scoped>
.inventory-page-hero {
  --hero-accent: #0b6b53;
  --hero-accent-soft: #e7f2ed;
  --hero-accent-line: #bad7cb;
  position: relative;
  display: flex;
  min-height: 116px;
  align-items: center;
  justify-content: space-between;
  gap: 24px;
  margin-bottom: 14px;
  padding: 18px 22px;
  overflow: hidden;
  color: var(--erp-text, #17211d);
  border: 1px solid var(--erp-border, #dde2de);
  border-left: 4px solid var(--hero-accent);
  border-radius: var(--erp-radius-md, 12px);
  background: var(--erp-surface, #ffffff);
  box-shadow: var(--erp-shadow-card, 0 8px 22px rgba(23, 33, 29, 0.055));
  isolation: isolate;
}

.inventory-page-hero::before,
.inventory-page-hero::after {
  display: none;
}

.inventory-page-hero--blue {
  --hero-accent: #2866b1;
  --hero-accent-soft: #edf4fd;
  --hero-accent-line: #c9ddf4;
}

.inventory-page-hero--teal {
  --hero-accent: #0b6b53;
  --hero-accent-soft: #e7f2ed;
  --hero-accent-line: #bad7cb;
}

.inventory-page-hero--amber {
  --hero-accent: #a76505;
  --hero-accent-soft: #fff3dc;
  --hero-accent-line: #edd6a7;
}

.inventory-page-hero--rose {
  --hero-accent: #c4322b;
  --hero-accent-soft: #fff0ee;
  --hero-accent-line: #efc8c4;
}

.inventory-page-hero--slate {
  --hero-accent: #5f6b66;
  --hero-accent-soft: #eef1ef;
  --hero-accent-line: #d2d9d5;
}

.inventory-page-hero__grid {
  display: none;
}

.inventory-page-hero__copy {
  position: relative;
  z-index: 1;
  min-width: 0;
  max-width: 760px;
}

.inventory-page-hero__eyebrow {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 5px;
  color: var(--hero-accent);
  font-size: 11px;
  font-weight: 700;
  line-height: 1.4;
  letter-spacing: 0.16em;
}

.inventory-page-hero__eyebrow-dot {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: var(--hero-accent);
  box-shadow: 0 0 0 4px var(--hero-accent-soft);
}

.inventory-page-hero h1 {
  margin: 0;
  color: var(--erp-text, #17211d);
  font-size: 23px;
  font-weight: 720;
  line-height: 1.28;
  letter-spacing: -0.02em;
}

.inventory-page-hero p {
  max-width: 680px;
  margin: 6px 0 0;
  color: var(--erp-text-secondary, #66736d);
  font-size: 13px;
  line-height: 1.65;
}

.inventory-page-hero__meta {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 13px;
}

.inventory-page-hero__chip {
  display: inline-flex;
  min-height: 28px;
  align-items: center;
  gap: 6px;
  padding: 5px 10px;
  color: #52605a;
  font-size: 11px;
  font-weight: 650;
  line-height: 1;
  border: 1px solid var(--hero-accent-line);
  border-radius: 999px;
  background: var(--hero-accent-soft);
}

.inventory-page-hero__chip i {
  color: var(--hero-accent);
  font-size: 13px;
}

.inventory-page-hero__chip strong {
  color: var(--hero-accent);
  font-weight: 700;
}

.inventory-page-hero__chip--context span {
  padding-left: 2px;
  color: var(--erp-text-secondary, #66736d);
}

.inventory-page-hero__visual {
  position: relative;
  z-index: 1;
  display: flex;
  width: 370px;
  flex: 0 0 auto;
  align-items: center;
  justify-content: flex-end;
  gap: 14px;
}

.inventory-page-hero__icon-shell {
  position: relative;
  display: grid;
  width: 60px;
  height: 60px;
  flex: 0 0 auto;
  place-items: center;
  border: 1px solid var(--hero-accent-line);
  border-radius: 16px;
  background: var(--hero-accent-soft);
}

.inventory-page-hero__icon-shell > i {
  position: relative;
  z-index: 1;
  color: var(--hero-accent);
  font-size: 27px;
}

.inventory-page-hero__icon-glow {
  display: none;
}

.inventory-page-hero__feature-list {
  display: grid;
  width: 286px;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 8px;
}

.inventory-page-hero__feature {
  display: flex;
  min-width: 0;
  min-height: 58px;
  flex-direction: column;
  justify-content: space-between;
  padding: 10px 11px;
  border: 1px solid var(--erp-border, #dde2de);
  border-radius: 10px;
  background: var(--erp-surface-muted, #f8f8f5);
}

.inventory-page-hero__feature-index {
  color: var(--erp-text-muted, #8a948f);
  font-size: 10px;
  font-weight: 800;
  letter-spacing: 0.08em;
}

.inventory-page-hero__feature-label {
  overflow: hidden;
  color: var(--erp-text, #17211d);
  font-size: 12px;
  font-weight: 650;
  line-height: 1.3;
  text-overflow: ellipsis;
  white-space: nowrap;
}

@media (min-width: 769px) and (max-height: 820px) {
  .inventory-page-hero {
    min-height: 116px;
    margin-bottom: 10px;
    padding: 16px 24px;
    border-radius: 16px;
  }

  .inventory-page-hero h1 {
    font-size: 22px;
  }

  .inventory-page-hero p {
    margin-top: 4px;
    line-height: 1.5;
  }

  .inventory-page-hero__meta {
    margin-top: 9px;
  }

  .inventory-page-hero__icon-shell {
    width: 64px;
    height: 64px;
    border-radius: 18px;
  }

  .inventory-page-hero__icon-shell > i {
    font-size: 29px;
  }
}

@media (max-width: 1180px) {
  .inventory-page-hero__visual {
    width: 330px;
  }

  .inventory-page-hero__icon-shell {
    display: none;
  }

  .inventory-page-hero__feature-list {
    width: 300px;
  }
}

@media (max-width: 920px) {
  .inventory-page-hero {
    min-height: 128px;
  }

  .inventory-page-hero__visual {
    display: none;
  }
}

@media (max-width: 767px) {
  .inventory-page-hero {
    min-height: auto;
    margin-bottom: 12px;
    padding: 20px;
    border-radius: 16px;
  }

  .inventory-page-hero h1 {
    font-size: 22px;
  }

  .inventory-page-hero p {
    font-size: 12px;
  }

  .inventory-page-hero__chip--context span {
    max-width: 160px;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }
}
</style>
