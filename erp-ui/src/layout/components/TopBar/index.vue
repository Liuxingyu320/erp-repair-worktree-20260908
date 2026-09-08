<template>
  <el-menu class="topbar-menu" :default-active="activeMenu" :active-text-color="theme" mode="horizontal">
    <sidebar-item :key="route.path + index" v-for="(route, index) in topMenus" :item="route" :base-path="route.path" />

    <el-submenu index="more" class="el-submenu__hide-arrow menu-tone--violet" v-if="moreRoutes.length > 0">
      <template slot="title">更多菜单</template>
      <sidebar-item :key="route.path + index" v-for="(route, index) in moreRoutes" :item="route" :base-path="route.path" />
    </el-submenu>
  </el-menu>
</template>

<script>
import SidebarItem from '../Sidebar/SidebarItem'

export default {
  components: { SidebarItem },
  data() {
    return {
      // 顶部栏初始数
      visibleNumber: 5
    }
  },
  computed: {
    theme() {
      return this.$store.state.settings.theme
    },
    topMenus() {
      return this.visibleRoutes.slice(0, this.visibleNumber)
    },
    moreRoutes() {
      return this.visibleRoutes.slice(this.visibleNumber)
    },
    visibleRoutes() {
      return this.$store.state.permission.sidebarRouters.filter((f) => !f.hidden)
    },
    // 默认激活的菜单
    activeMenu() {
      const { meta, path } = this.$route
      if (meta.activeMenu) {
        return meta.activeMenu
      }
      return path
    },
  },
  beforeMount() {
    window.addEventListener('resize', this.setVisibleNumber)
  },
  beforeDestroy() {
    window.removeEventListener('resize', this.setVisibleNumber)
  },
  mounted() {
    this.setVisibleNumber()
  },
  methods: {
    // 根据宽度计算设置显示栏数
    setVisibleNumber() {
      const width = document.body.getBoundingClientRect().width
      this.visibleNumber = Math.max(4, parseInt((width - 260) / 118))
    }
  }
}
</script>

<style lang="scss">
.topbar-menu.el-menu--horizontal {
  height: 42px;
  display: flex;
  align-items: center;
  border-bottom: none;
  background: transparent;
}

.topbar-menu.el-menu--horizontal > div {
  height: 42px;
  display: inline-flex;
  align-items: center;
}

.topbar-menu.el-menu--horizontal .el-menu-item,
.topbar-menu.el-menu--horizontal .el-submenu__title {
  position: relative;
  height: 32px !important;
  line-height: 32px !important;
  display: inline-flex;
  align-items: center;
  gap: 6px;
  margin: 0 3px !important;
  padding: 0 12px !important;
  border-bottom: none !important;
  border-radius: 8px;
  color: #6b655f !important;
  font-size: 13px;
  font-weight: 600;
  transition:
    background var(--motion-duration-fast) var(--motion-ease-standard),
    color var(--motion-duration-fast) var(--motion-ease-standard);
}

.topbar-menu.el-menu--horizontal .el-menu-item:hover,
.topbar-menu.el-menu--horizontal .el-submenu:hover > .el-submenu__title {
  color: var(--erp-primary, #252421) !important;
  background: var(--erp-primary-soft, #eeebe5) !important;
}

.topbar-menu.el-menu--horizontal .el-menu-item.is-active,
.topbar-menu.el-menu--horizontal > .el-submenu.is-active > .el-submenu__title {
  color: var(--erp-primary, #252421) !important;
  background: linear-gradient(180deg, #f4f1eb 0%, var(--erp-primary-soft, #eeebe5) 100%) !important;
  font-weight: 700;
  box-shadow: inset 0 0 0 1px rgba(37, 36, 33, 0.06);
}

.topbar-menu.el-menu--horizontal .el-menu-item.is-active::after,
.topbar-menu.el-menu--horizontal > .el-submenu.is-active > .el-submenu__title::after {
  position: absolute;
  right: 12px;
  bottom: -5px;
  left: 12px;
  height: 2px;
  border-radius: 999px;
  background: var(--erp-primary, #252421);
  content: "";
}

.topbar-menu .el-submenu .el-submenu__icon-arrow {
  position: static;
  vertical-align: middle;
  margin-left: 2px;
  margin-top: 0px;
  color: inherit;
}

.topbar-menu .svg-icon {
  width: 15px;
  height: 15px;
  margin-right: 2px !important;
}

.el-menu--horizontal .el-menu--popup {
  min-width: 176px;
  padding: 6px;
  border-radius: 10px;
}

.el-menu--horizontal .el-menu--popup .el-menu-item,
.el-menu--horizontal .el-menu--popup .el-submenu__title {
  height: 34px !important;
  line-height: 34px !important;
  border-radius: 6px;
  color: #4a4641 !important;
  font-size: 13px;
}

.el-menu--horizontal .el-menu--popup .el-menu-item:hover,
.el-menu--horizontal .el-menu--popup .el-submenu__title:hover {
  color: var(--erp-primary, #252421) !important;
  background-color: var(--erp-primary-soft, #eeebe5) !important;
}
</style>
