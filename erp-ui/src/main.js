import Vue from 'vue'

import Cookies from 'js-cookie'

import './assets/styles/element-variables.scss'
import { installElementUI } from '@/plugins/element-ui'

import '@/assets/styles/index.scss' // global css
import '@/assets/styles/common.scss' // common css
import App from './App'
import store from './store'
import router from './router'
import directive from './directive' // directive
import plugins from './plugins' // plugins
import { download } from '@/utils/request'

import './assets/icons' // icon
import './permission' // permission control
import { getDicts } from "@/api/system/dict/data"
import { getConfigKey } from "@/api/system/config"
import { parseTime, resetForm, addDateRange, selectDictLabel, selectDictLabels, handleTree, exportFileName } from "@/utils/common"
// 字典数据组件
import DictData from '@/components/DictData'
import pushRegistration from '@/services/lazyPushRegistration'

pushRegistration.setRouter(router)

// 全局方法挂载
Vue.prototype.getDicts = getDicts
Vue.prototype.getConfigKey = getConfigKey
Vue.prototype.parseTime = parseTime
Vue.prototype.resetForm = resetForm
Vue.prototype.addDateRange = addDateRange
Vue.prototype.selectDictLabel = selectDictLabel
Vue.prototype.selectDictLabels = selectDictLabels
Vue.prototype.download = download
Vue.prototype.handleTree = handleTree
Vue.prototype.exportFileName = exportFileName

// 全局组件按需加载，避免编辑器、上传组件等进入首屏同步包。
Vue.component('DictTag', () => import(/* webpackChunkName: "chunk-global-list-ui" */ '@/components/DictTag'))
Vue.component('Pagination', () => import(/* webpackChunkName: "chunk-global-list-ui" */ '@/components/Pagination'))
Vue.component('RightToolbar', () => import(/* webpackChunkName: "chunk-global-list-ui" */ '@/components/RightToolbar'))
Vue.component('SystemPageHeader', () => import(/* webpackChunkName: "chunk-global-list-ui" */ '@/components/SystemPageHeader'))
Vue.component('DataState', () => import(/* webpackChunkName: "chunk-global-list-ui" */ '@/components/DataState'))
Vue.component('Editor', () => import(/* webpackChunkName: "chunk-editor" */ '@/components/Editor'))
Vue.component('FileUpload', () => import(/* webpackChunkName: "chunk-upload" */ '@/components/FileUpload'))
Vue.component('ImageUpload', () => import(/* webpackChunkName: "chunk-upload" */ '@/components/ImageUpload'))
Vue.component('ImagePreview', () => import(/* webpackChunkName: "chunk-upload" */ '@/components/ImagePreview'))

Vue.use(directive)
Vue.use(plugins)
DictData.install()

/**
 * If you don't want to use mock-server
 * you want to use MockJs for mock api
 * you can execute: mockXHR()
 *
 * Currently MockJs will be used in the production environment,
 * please remove it before going online! ! !
 */

installElementUI(Vue, {
  size: Cookies.get('size') || 'medium' // set element-ui default size
})

Vue.config.productionTip = false

new Vue({
  el: '#app',
  router,
  store,
  render: h => h(App)
})
