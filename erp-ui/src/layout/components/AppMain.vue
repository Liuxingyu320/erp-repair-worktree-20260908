<template>
  <section class="app-main" :class="{ 'inventory-module': isInventoryRoute }">
    <!-- ERP routes are high-frequency task navigation, so page changes stay instant. -->
    <keep-alive :include="cachedViews">
      <router-view v-if="!$route.meta.link" :key="key" />
    </keep-alive>
    <iframe-toggle />
    <copyright />
  </section>
</template>

<script>
import copyright from "./Copyright/index"
import iframeToggle from "./IframeToggle/index"

export default {
  name: 'AppMain',
  components: { iframeToggle, copyright },
  computed: {
    cachedViews() {
      return this.$store.state.tagsView.cachedViews
    },
    isInventoryRoute() {
      return /^\/inventory(?:\/|$)/.test(this.$route.path || '')
    },
    key() {
      return this.$route.path
    }
  },
  watch: {
    $route() {
      this.addIframe()
    }
  },
  mounted() {
    this.addIframe()
  },
  methods: {
    addIframe() {
      const { name } = this.$route
      if (name && this.$route.meta.link) {
        this.$store.dispatch('tagsView/addIframeView', this.$route)
      }
    }
  }
}
</script>

<style lang="scss" scoped>
.app-main {
  /* 96 = top toolbar + horizontal menu */
  min-height: calc(100vh - 96px);
  width: 100%;
  position: relative;
  overflow: hidden;
  background: var(--erp-canvas, #f4f5f2);

  &:fullscreen,
  &:-webkit-full-screen,
  &:-moz-full-screen,
  &:-ms-fullscreen {
    background: #fff;
    overflow-y: auto;
  }
}

.fixed-header + .app-main {
  overflow-y: auto;
  scrollbar-gutter: auto;
  height: calc(100vh - 96px);
  min-height: 0px;
}

.app-main:has(.copyright) {
  padding-bottom: 36px;
}

.fixed-header + .app-main {
  margin-top: 96px;
}

.hasTagsView {
  .app-main {
    /* 130 = top navigation + tags-view = 96 + 34 */
    min-height: calc(100vh - 130px);
  }

  .fixed-header + .app-main {
    margin-top: 130px;
    height: calc(100vh - 130px);
    min-height: 0px;
  }
}

/* 移动端fixed-header优化 */
@media screen and (max-width: 991px) {
  .app-main {
    min-height: calc(100vh - 52px);
  }

  .fixed-header + .app-main {
    margin-top: 52px;
    height: calc(100vh - 52px);
    min-height: 0px;
  }

  .hasTagsView {
    .app-main {
      min-height: calc(100vh - 86px);
    }

    .fixed-header + .app-main {
      margin-top: 86px;
      height: calc(100vh - 86px);
      min-height: 0px;
    }
  }

  .fixed-header + .app-main {
    padding-bottom: max(60px, calc(constant(safe-area-inset-bottom) + 40px));
    padding-bottom: max(60px, calc(env(safe-area-inset-bottom) + 40px));
    overscroll-behavior-y: none;
  }

  .hasTagsView .fixed-header + .app-main {
    padding-bottom: max(60px, calc(constant(safe-area-inset-bottom) + 40px));
    padding-bottom: max(60px, calc(env(safe-area-inset-bottom) + 40px));
    overscroll-behavior-y: none;
  }
}

@supports (-webkit-touch-callout: none) {
  @media screen and (max-width: 991px) {
    .fixed-header + .app-main {
      padding-bottom: max(17px, calc(constant(safe-area-inset-bottom) + 10px));
      padding-bottom: max(17px, calc(env(safe-area-inset-bottom) + 10px));
      height: calc(100svh - 52px);
      height: calc(100dvh - 52px);
    }

    .hasTagsView .fixed-header + .app-main {
      padding-bottom: max(17px, calc(constant(safe-area-inset-bottom) + 10px));
      padding-bottom: max(17px, calc(env(safe-area-inset-bottom) + 10px));
      height: calc(100svh - 86px);
      height: calc(100dvh - 86px);
    }
  }
}
</style>

<style lang="scss">
::-webkit-scrollbar {
  width: 8px;
  height: 8px;
}

::-webkit-scrollbar-track {
  background-color: #f2f2f4;
}

::-webkit-scrollbar-thumb {
  border: 2px solid #f2f2f4;
  border-radius: 999px;
  background-color: #b8b8bd;
}

::-webkit-scrollbar-thumb:hover {
  background-color: #8e8e93;
}
</style>
