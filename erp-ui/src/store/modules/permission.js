import auth from '@/plugins/auth'
import router, { constantRoutes, dynamicRoutes } from '@/router'
import { getRouters } from '@/api/menu'
import ParentView from '@/components/ParentView'
import InnerLink from '@/layout/components/InnerLink'

const Layout = () => import(/* webpackChunkName: "chunk-desktop-layout" */ '@/layout/index')

const permission = {
  state: {
    routes: [],
    addRoutes: [],
    defaultRoutes: [],
    topbarRouters: [],
    sidebarRouters: []
  },
  mutations: {
    SET_ROUTES: (state, routes) => {
      state.addRoutes = routes
      state.routes = constantRoutes.concat(routes)
    },
    SET_DEFAULT_ROUTES: (state, routes) => {
      state.defaultRoutes = constantRoutes.concat(routes)
    },
    SET_TOPBAR_ROUTES: (state, routes) => {
      state.topbarRouters = routes
    },
    SET_SIDEBAR_ROUTERS: (state, routes) => {
      state.sidebarRouters = routes
    },
  },
  actions: {
    // 生成路由
    async GenerateRoutes({ commit }) {
      // 直接等待并返回结果，确保网络错误、响应格式错误和路由转换错误都能传给导航守卫。
      const res = await getRouters()
      if (!res || !Array.isArray(res.data)) {
        throw new TypeError('业务菜单响应格式无效，请重试加载')
      }
      const sdata = JSON.parse(JSON.stringify(res.data))
      const rdata = JSON.parse(JSON.stringify(res.data))
      const sidebarRoutes = filterAsyncRouter(sdata)
      const rewriteRoutes = filterAsyncRouter(rdata, false, true)
      const asyncRoutes = filterDuplicateDynamicRoutes(filterDynamicRoutes(dynamicRoutes), rewriteRoutes)
      rewriteRoutes.push({ path: '*', redirect: '/404', hidden: true })
      router.addRoutes(asyncRoutes)
      commit('SET_ROUTES', rewriteRoutes)
      commit('SET_SIDEBAR_ROUTERS', constantRoutes.concat(sidebarRoutes))
      commit('SET_DEFAULT_ROUTES', sidebarRoutes)
      commit('SET_TOPBAR_ROUTES', sidebarRoutes)
      return rewriteRoutes
    }
  }
}

// 遍历后台传来的路由字符串，转换为组件对象
function filterAsyncRouter(asyncRouterMap, lastRouter = false, type = false) {
  return asyncRouterMap.filter(route => {
    if (type && route.children) {
      route.children = filterChildren(route.children)
    }
    if (route.component) {
      // Layout ParentView 组件特殊处理
      if (route.component === 'Layout') {
        route.component = Layout
      } else if (route.component === 'ParentView') {
        route.component = ParentView
      } else if (route.component === 'InnerLink') {
        route.component = InnerLink
      } else {
        route.component = loadView(route.component)
      }
    }
    if (route.children != null && route.children && route.children.length) {
      route.children = filterAsyncRouter(route.children, route, type)
    } else {
      delete route['children']
      delete route['redirect']
    }
    return true
  })
}

function filterChildren(childrenMap, lastRouter = false) {
  var children = []
  childrenMap.forEach(el => {
    el.path = lastRouter ? lastRouter.path + '/' + el.path : el.path
    if (el.children && el.children.length && el.component === 'ParentView') {
      children = children.concat(filterChildren(el.children, el))
    } else {
      children.push(el)
    }
  })
  return children
}

// 动态路由遍历，验证是否具备权限
export function filterDynamicRoutes(routes) {
  const res = []
  routes.forEach(route => {
    if (route.permissions) {
      if (auth.hasPermiOr(route.permissions)) {
        res.push(route)
      }
    } else if (route.roles) {
      if (auth.hasRoleOr(route.roles)) {
        res.push(route)
      }
    }
  })
  return res
}

function normalizeRoutePath(path) {
  return (path || '').replace(/\/+/g, '/')
}

function getRouteIdentityPath(route, parentPath = '') {
  const routePath = route && route.path ? route.path : ''
  if (!routePath) {
    return parentPath
  }
  if (routePath.startsWith('/')) {
    return normalizeRoutePath(routePath)
  }
  return normalizeRoutePath(parentPath + '/' + routePath)
}

function collectRouteIdentities(routes, identities = new Set(), parentPath = '') {
  ;(routes || []).forEach(route => {
    const fullPath = getRouteIdentityPath(route, parentPath)
    if (route.name) {
      identities.add('name:' + route.name)
    }
    if (route.path) {
      identities.add('path:' + normalizeRoutePath(route.path))
    }
    if (fullPath) {
      identities.add('path:' + fullPath)
    }
    collectRouteIdentities(route.children || [], identities, fullPath)
  })
  return identities
}

function isSameRouteIdentity(route, identities, parentPath = '') {
  const fullPath = getRouteIdentityPath(route, parentPath)
  return (route.name && identities.has('name:' + route.name)) ||
    (route.path && identities.has('path:' + normalizeRoutePath(route.path))) ||
    (fullPath && identities.has('path:' + fullPath))
}

function filterDuplicateRouteList(routes, identities, parentPath = '') {
  return (routes || []).reduce((result, route) => {
    const fullPath = getRouteIdentityPath(route, parentPath)
    if (isSameRouteIdentity(route, identities, parentPath)) {
      return result
    }
    const nextRoute = Object.assign({}, route)
    if (route.children && route.children.length) {
      const children = filterDuplicateRouteList(route.children, identities, fullPath)
      if (children.length) {
        nextRoute.children = children
      } else {
        delete nextRoute.children
      }
    }
    result.push(nextRoute)
    return result
  }, [])
}

export function filterDuplicateDynamicRoutes(routes, registeredRoutes) {
  const identities = collectRouteIdentities(registeredRoutes)
  return filterDuplicateRouteList(routes, identities)
}

export const loadView = (view) => {
  if (process.env.NODE_ENV === 'development') {
    return (resolve) => require([`@/views/${view}`], resolve)
  } else {
    // 使用 import 实现生产环境的路由懒加载
    return () => import(`@/views/${view}`)
  }
}

export default permission
