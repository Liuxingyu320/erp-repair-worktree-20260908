<template>
  <div ref="container" class="resizable-split-pane">
    <section class="resizable-split-pane__main">
      <slot name="main" />
    </section>
    <button
      type="button"
      class="resizable-split-pane__handle"
      aria-label="调整组织授权面板宽度"
      :aria-valuenow="rightWidth"
      :aria-valuemin="minRightWidth"
      :aria-valuemax="effectiveMaxRightWidth"
      role="separator"
      aria-orientation="vertical"
      @mousedown.prevent="startResize"
      @keydown="handleKeydown"
    >
      <span aria-hidden="true" />
    </button>
    <aside class="resizable-split-pane__side" :style="{ width: rightWidth + 'px' }">
      <slot name="side" />
    </aside>
  </div>
</template>

<script>
const readStoredWidth = (key, fallback) => {
  if (!key || typeof window === 'undefined') return fallback
  const value = Number(window.localStorage.getItem(key))
  return Number.isFinite(value) ? value : fallback
}

export default {
  name: "ResizableSplitPane",
  props: {
    defaultRightWidth: { type: Number, default: 360 },
    minRightWidth: { type: Number, default: 320 },
    maxRightWidth: { type: Number, default: 720 },
    minMainWidth: { type: Number, default: 520 },
    storageKey: { type: String, default: "" }
  },
  data() {
    return {
      rightWidth: readStoredWidth(this.storageKey, this.defaultRightWidth),
      containerWidth: 0,
      resizeStartX: 0,
      resizeStartWidth: 0
    }
  },
  computed: {
    effectiveMaxRightWidth() {
      if (!this.containerWidth) return this.maxRightWidth
      return Math.max(this.minRightWidth, Math.min(this.maxRightWidth, this.containerWidth - this.minMainWidth - 12))
    }
  },
  mounted() {
    this.measure()
    this.rightWidth = this.clamp(this.rightWidth)
    window.addEventListener("resize", this.measure)
  },
  beforeDestroy() {
    window.removeEventListener("resize", this.measure)
    this.stopResize()
  },
  methods: {
    measure() {
      this.containerWidth = this.$refs.container ? this.$refs.container.clientWidth : 0
      this.rightWidth = this.clamp(this.rightWidth)
    },
    clamp(width) {
      return Math.round(Math.max(this.minRightWidth, Math.min(this.effectiveMaxRightWidth, Number(width) || this.defaultRightWidth)))
    },
    startResize(event) {
      this.resizeStartX = event.clientX
      this.resizeStartWidth = this.rightWidth
      document.body.classList.add("is-resizing-split-pane")
      document.addEventListener("mousemove", this.resize)
      document.addEventListener("mouseup", this.stopResize)
    },
    resize(event) {
      this.setWidth(this.resizeStartWidth + this.resizeStartX - event.clientX, false)
    },
    stopResize() {
      document.body && document.body.classList.remove("is-resizing-split-pane")
      document.removeEventListener("mousemove", this.resize)
      document.removeEventListener("mouseup", this.stopResize)
      this.persist()
    },
    handleKeydown(event) {
      if (event.key !== "ArrowLeft" && event.key !== "ArrowRight" && event.key !== "Home" && event.key !== "End") return
      event.preventDefault()
      if (event.key === "Home") this.setWidth(this.minRightWidth)
      else if (event.key === "End") this.setWidth(this.effectiveMaxRightWidth)
      else this.setWidth(this.rightWidth + (event.key === "ArrowLeft" ? 16 : -16))
    },
    setWidth(width, persist = true) {
      this.rightWidth = this.clamp(width)
      if (persist) this.persist()
      this.$emit("resize", this.rightWidth)
    },
    persist() {
      if (!this.storageKey || typeof window === "undefined") return
      window.localStorage.setItem(this.storageKey, String(this.rightWidth))
    }
  }
}
</script>

<style lang="scss" scoped>
.resizable-split-pane {
  display: flex;
  align-items: stretch;
  min-width: 0;
}

.resizable-split-pane__main {
  flex: 1 1 auto;
  min-width: 0;
}

.resizable-split-pane__side {
  flex: 0 0 auto;
  min-width: 0;
}

.resizable-split-pane__handle {
  position: relative;
  flex: 0 0 12px;
  width: 12px;
  margin: 0;
  padding: 0;
  border: 0;
  background: transparent;
  cursor: col-resize;
}

.resizable-split-pane__handle::before {
  content: "";
  position: absolute;
  top: 0;
  bottom: 0;
  left: 5px;
  width: 2px;
  background: #dcdfe6;
  transition: background-color 0.2s, width 0.2s;
}

.resizable-split-pane__handle:hover::before,
.resizable-split-pane__handle:focus-visible::before {
  width: 3px;
  background: var(--erp-primary, #0b6b53);
}

.resizable-split-pane__handle:focus-visible {
  outline: 2px solid var(--erp-primary, #0b6b53);
  outline-offset: -2px;
}
</style>

<style>
body.is-resizing-split-pane {
  cursor: col-resize !important;
  user-select: none !important;
}
</style>
