import Vue from 'vue'
import Router from 'vue-router'

const { settleNavigation } = require('./navigationPromise')

Vue.use(Router)

/* Layout */
const Layout = () => import(/* webpackChunkName: "chunk-desktop-layout" */ '@/layout')
const { mobileRouteDefinitions } = require('@/views/mobile/mobileRouteDefinitions')

/**
 * Note: 路由配置项
 *
 * hidden: true                     // 当设置 true 的时候该路由不会再侧边栏出现 如401，login等页面，或者如一些编辑页面/edit/1
 * alwaysShow: true                 // 当你一个路由下面的 children 声明的路由大于1个时，自动会变成嵌套的模式--如组件页面
 *                                  // 只有一个时，会将那个子路由当做根路由显示在侧边栏--如引导页面
 *                                  // 若你想不管路由下面的 children 声明的个数都显示你的根路由
 *                                  // 你可以设置 alwaysShow: true，这样它就会忽略之前定义的规则，一直显示根路由
 * redirect: noRedirect             // 当设置 noRedirect 的时候该路由在面包屑导航中不可被点击
 * name:'router-name'               // 设定路由的名字，一定要填写不然使用<keep-alive>时会出现各种问题
 * query: '{"id": 1, "name": "ry"}' // 访问路由的默认传递参数
 * roles: ['admin', 'common']       // 访问路由的角色权限
 * permissions: ['a:a:a', 'b:b:b']  // 访问路由的菜单权限
 * meta : {
    noCache: true                   // 如果设置为true，则不会被 <keep-alive> 缓存(默认 false)
    title: 'title'                  // 设置该路由在侧边栏和面包屑中展示的名字
    icon: 'svg-name'                // 设置该路由的图标，对应路径src/assets/icons/svg
    breadcrumb: false               // 如果设置为false，则不会在breadcrumb面包屑中显示
    activeMenu: '/system/user'      // 当路由设置了该属性，则会高亮相对应的侧边栏。
  }
 */

// 公共路由
export const constantRoutes = [
  {
    path: '/redirect',
    component: Layout,
    hidden: true,
    children: [
      {
        path: '/redirect/:path(.*)',
        component: () => import('@/views/redirect')
      }
    ]
  },
  {
    path: '/login',
    component: () => import('@/views/login'),
    hidden: true
  },
  {
    path: '/register',
    component: () => import('@/views/register'),
    hidden: true
  },
  {
    path: '/credential/change-password',
    component: () => import('@/views/credential/change-password'),
    hidden: true,
    meta: { title: '必须修改密码' }
  },
  ...mobileRouteDefinitions,
  {
    path: '/sign-package-handoff',
    component: Layout,
    hidden: true,
    children: [{
      path: '',
      name: 'SignPackageHandoff',
      component: () => import('@/views/signPackageHandoff/index'),
      meta: { title: '手机继续签约', noCache: true }
    }]
  },
  {
    path: '/complete-profile',
    component: () => import('@/views/profile-completion/index'),
    hidden: true,
    meta: { title: '入职资料补全' }
  },
  {
    path: '/select-shop',
    component: () => import('@/views/select-shop/index'),
    hidden: true,
    meta: { title: '选择店铺/仓库' }
  },
  {
    path: '/404',
    component: () => import('@/views/error/404'),
    hidden: true
  },
  {
    path: '/401',
    component: () => import('@/views/error/401'),
    hidden: true
  },
  {
    path: '',
    component: Layout,
    redirect: 'index',
    children: [
      {
        path: 'index',
        component: () => import('@/views/index'),
        name: 'Index',
        meta: { title: '首页', icon: 'dashboard', affix: true }
      }
    ]
  },
  {
    path: '/lock',
    component: () => import('@/views/lock'),
    hidden: true,
    meta: { title: '锁定屏幕' }
  },
  {
    path: '/workbench',
    component: Layout,
    hidden: true,
    children: [
      {
        path: 'todo',
        component: () => import('@/views/workbench/todo'),
        name: 'UnifiedTodoCenter',
        meta: { title: '我的待办' }
      }
    ]
  },
  {
    path: '/user',
    component: Layout,
    hidden: true,
    redirect: 'noredirect',
    children: [
      {
        path: 'profile',
        component: () => import('@/views/system/user/profile/index'),
        name: 'Profile',
        meta: { title: '个人中心', icon: 'user' }
      }
    ]
  },
  {
    path: '/system/user-notification',
    component: Layout,
    hidden: true,
    children: [
      {
        path: '',
        component: () => import('@/views/system/userNotification/index'),
        name: 'UserNotification',
        meta: { title: '我的消息', icon: 'message' }
      }
    ]
  }
]

// 动态路由，基于用户权限动态去加载
export const dynamicRoutes = [
  {
    path: '/oa/sign-task/package',
    component: Layout,
    hidden: true,
    permissions: ['oa:signPackage:list'],
    children: [
      {
        path: '',
        component: () => import('@/views/oa/signPackage/index'),
        name: 'SignTaskPackageDraft',
        meta: { title: '签约资料维护', activeMenu: '/oa/sign-task' }
      }
    ]
  },
  {
    path: '/system/user-auth',
    component: Layout,
    hidden: true,
    permissions: ['system:user:authRole'],
    children: [
      {
        path: 'role/:userId(\\d+)',
        component: () => import('@/views/system/user/authRole'),
        name: 'AuthRole',
        meta: { title: '分配角色', activeMenu: '/system/user' }
      }
    ]
  },
  {
    path: '/system/role-auth',
    component: Layout,
    hidden: true,
    permissions: ['system:role:authUser'],
    children: [
      {
        path: 'user/:roleId(\\d+)',
        component: () => import('@/views/system/role/authUser'),
        name: 'AuthUser',
        meta: { title: '分配用户', activeMenu: '/system/role' }
      }
    ]
  },
  {
    path: '/system/dict-data',
    component: Layout,
    hidden: true,
    permissions: ['system:dict:list'],
    children: [
      {
        path: 'index/:dictId(\\d+)',
        component: () => import('@/views/system/dict/data'),
        name: 'Data',
        meta: { title: '字典数据', activeMenu: '/system/dict' }
      }
    ]
  },
  {
    path: '/monitor/job-log',
    component: Layout,
    hidden: true,
    permissions: ['monitor:job:list'],
    children: [
      {
        path: 'index/:jobId(\\d+)',
        component: () => import('@/views/monitor/job/log'),
        name: 'JobLog',
        meta: { title: '调度日志', activeMenu: '/monitor/job' }
      }
    ]
  },
  {
    path: '/inventory/stock-log',
    component: Layout,
    hidden: true,
    permissions: ['inv:stock:log'],
    children: [
      {
        path: '',
        component: () => import('@/views/inventory/stock/log'),
        name: 'InventoryStockLog',
        meta: { title: '库存变动日志', activeMenu: '/inventory/stock' }
      }
    ]
  },
  {
    path: '/inventory/report',
    component: Layout,
    hidden: true,
    permissions: ['inv:report:list'],
    children: [
      {
        path: '',
        component: () => import('@/views/inventory/report/index'),
        name: 'InvReport',
        meta: { title: '报表中心', activeMenu: '/inventory/report' }
      }
    ]
  }
]

// 防止连续点击多次路由报错
let routerPush = Router.prototype.push
let routerReplace = Router.prototype.replace
const isDuplicatedNavigation = error => Router.isNavigationFailure(
  error,
  Router.NavigationFailureType.duplicated
)
// push
Router.prototype.push = function push(location) {
  return settleNavigation(routerPush.call(this, location), isDuplicatedNavigation)
}
// replace
Router.prototype.replace = function push(location) {
  return settleNavigation(routerReplace.call(this, location), isDuplicatedNavigation)
}

export default new Router({
  mode: 'history', // 去掉url中的#
  scrollBehavior: () => ({ y: 0 }),
  routes: constantRoutes
})
