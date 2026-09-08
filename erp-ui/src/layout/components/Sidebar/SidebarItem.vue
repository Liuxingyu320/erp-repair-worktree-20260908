<template>
  <div v-if="!item.hidden">
    <template v-if="hasOneShowingChild(item.children,item) && (!onlyOneChild.children||onlyOneChild.noShowingChildren)&&!item.alwaysShow">
      <app-link v-if="onlyOneChild.meta" :to="resolvePath(onlyOneChild.path, onlyOneChild.query)">
        <el-menu-item
          :index="resolvePath(onlyOneChild.path)"
          :class="[{ 'submenu-title-noDropdown': !isNest }, 'menu-tone--' + getMenuTone(onlyOneChild, item)]"
        >
          <item :icon="onlyOneChild.meta.icon||(item.meta&&item.meta.icon)" :title="getMenuTitle(onlyOneChild, item)" />
        </el-menu-item>
      </app-link>
    </template>

    <el-submenu v-else ref="subMenu" :index="resolvePath(item.path)" :class="'menu-tone--' + getMenuTone(item)" popper-append-to-body>
      <template slot="title">
        <item v-if="item.meta" :icon="item.meta && item.meta.icon" :title="getMenuTitle(item)" />
      </template>
      <sidebar-item
        v-for="(child, index) in item.children"
        :key="child.path + index"
        :is-nest="true"
        :item="child"
        :base-path="resolvePath(child.path)"
        class="nest-menu"
      />
    </el-submenu>
  </div>
</template>

<script>
import path from 'path'
import { isExternal } from '@/utils/validate'
import { getSelectedDeptContext } from '@/utils/shopContext'
import Item from './Item'
import AppLink from './Link'
import FixiOSBug from './FixiOSBug'

export default {
  name: 'SidebarItem',
  components: { Item, AppLink },
  mixins: [FixiOSBug],
  props: {
    // route object
    item: {
      type: Object,
      required: true
    },
    isNest: {
      type: Boolean,
      default: false
    },
    basePath: {
      type: String,
      default: ''
    }
  },
  data() {
    this.onlyOneChild = null
    return {}
  },
  computed: {
    selectedDeptContext() {
      const routeKey = this.$route && this.$route.fullPath
      void routeKey
      return getSelectedDeptContext()
    }
  },
  methods: {
    getMenuTone(route, parent) {
      const meta = route && route.meta ? route.meta : {}
      const parentMeta = parent && parent.meta ? parent.meta : {}
      const signature = `${meta.icon || parentMeta.icon || ''} ${meta.title || ''} ${route && route.path ? route.path : ''}`.toLowerCase()
      if (/(user|people|peoples|role|staff|team|hr|system|setting|人事|系统)/.test(signature)) return 'violet'
      if (/(dept|tree|office|company|shop|store|branch|组织|门店)/.test(signature)) return 'sage'
      if (/(goods|product|shopping|cart|inventory|warehouse|purchase|order|money|仓库|进销存|采购|财务)/.test(signature)) return 'amber'
      if (/(warning|notice|bell|audit|log|security|lock|risk|monitor|监控|审计|公告)/.test(signature)) return 'rose'
      return 'blue'
    },
    getMenuTitle(route, parent) {
      const title = route && route.meta ? route.meta.title : ''
      if (this.isStoreCommodityMenu(route, parent)) {
        return '商品资料'
      }
      return title
    },
    isStoreCommodityMenu(route) {
      if (!this.selectedDeptContext.isStore || !route || route.path !== '/cangku') {
        return false
      }
      const showingChildren = (route.children || []).filter(child => !child.hidden)
      if (!showingChildren.length) {
        return false
      }
      return showingChildren.every(child => ['product', 'category', '/cangku/product', '/cangku/category'].includes(child.path))
    },
    hasOneShowingChild(children = [], parent) {
      if (!children) {
        children = []
      }
      const showingChildren = children.filter(item => {
        if (item.hidden) {
          return false
        }
        // Temp set(will be used if only has one showing child)
        this.onlyOneChild = item
        return true
      })

      // When there is only one child router, the child router is displayed by default
      if (showingChildren.length === 1) {
        return true
      }

      // Show parent if there are no child router to display
      if (showingChildren.length === 0) {
        this.onlyOneChild = { ... parent, path: '', noShowingChildren: true }
        return true
      }

      return false
    },
    resolvePath(routePath, routeQuery) {
      if (isExternal(routePath)) {
        return routePath
      }
      if (isExternal(this.basePath)) {
        return this.basePath
      }
      if (routeQuery) {
        let query = JSON.parse(routeQuery)
        return { path: path.resolve(this.basePath, routePath), query: query }
      }
      return path.resolve(this.basePath, routePath)
    }
  }
}
</script>
