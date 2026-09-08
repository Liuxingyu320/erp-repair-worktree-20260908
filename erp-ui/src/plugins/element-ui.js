import Alert from 'element-ui/packages/alert'
import Breadcrumb from 'element-ui/packages/breadcrumb'
import BreadcrumbItem from 'element-ui/packages/breadcrumb-item'
import Button from 'element-ui/packages/button'
import ButtonGroup from 'element-ui/packages/button-group'
import Card from 'element-ui/packages/card'
import Checkbox from 'element-ui/packages/checkbox'
import CheckboxButton from 'element-ui/packages/checkbox-button'
import CheckboxGroup from 'element-ui/packages/checkbox-group'
import Col from 'element-ui/packages/col'
import ColorPicker from 'element-ui/packages/color-picker'
import Collapse from 'element-ui/packages/collapse'
import CollapseItem from 'element-ui/packages/collapse-item'
import Dialog from 'element-ui/packages/dialog'
import Divider from 'element-ui/packages/divider'
import Drawer from 'element-ui/packages/drawer'
import Dropdown from 'element-ui/packages/dropdown'
import DropdownItem from 'element-ui/packages/dropdown-item'
import DropdownMenu from 'element-ui/packages/dropdown-menu'
import Empty from 'element-ui/packages/empty'
import Form from 'element-ui/packages/form'
import FormItem from 'element-ui/packages/form-item'
import Input from 'element-ui/packages/input'
import InputNumber from 'element-ui/packages/input-number'
import Link from 'element-ui/packages/link'
import Menu from 'element-ui/packages/menu'
import MenuItem from 'element-ui/packages/menu-item'
import Option from 'element-ui/packages/option'
import OptionGroup from 'element-ui/packages/option-group'
import Popover from 'element-ui/packages/popover'
import Progress from 'element-ui/packages/progress'
import Radio from 'element-ui/packages/radio'
import RadioButton from 'element-ui/packages/radio-button'
import RadioGroup from 'element-ui/packages/radio-group'
import Result from 'element-ui/packages/result'
import Row from 'element-ui/packages/row'
import Scrollbar from 'element-ui/packages/scrollbar'
import Select from 'element-ui/packages/select'
import Skeleton from 'element-ui/packages/skeleton'
import SkeletonItem from 'element-ui/packages/skeleton-item'
import Step from 'element-ui/packages/step'
import Steps from 'element-ui/packages/steps'
import Submenu from 'element-ui/packages/submenu'
import Switch from 'element-ui/packages/switch'
import Tag from 'element-ui/packages/tag'
import Tooltip from 'element-ui/packages/tooltip'
import CollapseTransition from 'element-ui/src/transitions/collapse-transition'
import { Loading, Message, MessageBox, Notification } from './element-services'

const components = [
  Alert, Breadcrumb, BreadcrumbItem, Button, ButtonGroup, Card,
  Checkbox, CheckboxButton, CheckboxGroup, Col, ColorPicker, Collapse, CollapseItem,
  Dialog, Divider, Drawer, Dropdown, DropdownItem, DropdownMenu, Empty, Form,
  FormItem, Input, InputNumber, Link, Menu, MenuItem, Option, OptionGroup,
  Popover, Progress, Radio, RadioButton, RadioGroup, Result, Row, Scrollbar,
  Select, Skeleton, SkeletonItem, Step, Steps, Submenu, Switch, Tag, Tooltip,
  CollapseTransition
]

// 数据展示与高级表单组件体积较大，登录页和首页并不使用。保留全局组件 API，
// 但把实现延后到模板首次渲染，避免电脑端首屏同步下载整套管理页组件。
const asyncComponents = {
  ElCascader: () => import(/* webpackChunkName: "chunk-element-form-advanced" */ 'element-ui/packages/cascader'),
  ElDatePicker: () => import(/* webpackChunkName: "chunk-element-form-advanced" */ 'element-ui/packages/date-picker'),
  ElRate: () => import(/* webpackChunkName: "chunk-element-form-advanced" */ 'element-ui/packages/rate'),
  ElSlider: () => import(/* webpackChunkName: "chunk-element-form-advanced" */ 'element-ui/packages/slider'),
  ElTimePicker: () => import(/* webpackChunkName: "chunk-element-form-advanced" */ 'element-ui/packages/time-picker'),
  ElTransfer: () => import(/* webpackChunkName: "chunk-element-form-advanced" */ 'element-ui/packages/transfer'),
  ElUpload: () => import(/* webpackChunkName: "chunk-element-form-advanced" */ 'element-ui/packages/upload'),
  ElDescriptions: () => import(/* webpackChunkName: "chunk-element-data" */ 'element-ui/packages/descriptions'),
  ElDescriptionsItem: () => import(/* webpackChunkName: "chunk-element-data" */ 'element-ui/packages/descriptions-item'),
  ElImage: () => import(/* webpackChunkName: "chunk-element-data" */ 'element-ui/packages/image'),
  ElPagination: () => import(/* webpackChunkName: "chunk-element-data" */ 'element-ui/packages/pagination'),
  ElTabPane: () => import(/* webpackChunkName: "chunk-element-data" */ 'element-ui/packages/tab-pane'),
  ElTable: () => import(/* webpackChunkName: "chunk-element-data" */ 'element-ui/packages/table'),
  ElTableColumn: () => import(/* webpackChunkName: "chunk-element-data" */ 'element-ui/packages/table-column'),
  ElTabs: () => import(/* webpackChunkName: "chunk-element-data" */ 'element-ui/packages/tabs'),
  ElTimeline: () => import(/* webpackChunkName: "chunk-element-data" */ 'element-ui/packages/timeline'),
  ElTimelineItem: () => import(/* webpackChunkName: "chunk-element-data" */ 'element-ui/packages/timeline-item'),
  ElTree: () => import(/* webpackChunkName: "chunk-element-data" */ 'element-ui/packages/tree')
}

function asyncComponent(loader) {
  return () => loader().then(module => module.default || module)
}

const asyncComponentNames = Object.keys(asyncComponents)

export function installElementUI(Vue, options = {}) {
  components.forEach(component => Vue.component(component.name, component))
  asyncComponentNames.forEach(name => Vue.component(name, asyncComponent(asyncComponents[name])))
  Vue.use(Loading.directive)

  Vue.prototype.$ELEMENT = {
    size: options.size || '',
    zIndex: options.zIndex || 2000
  }
  Vue.prototype.$loading = Loading.service
  Vue.prototype.$msgbox = MessageBox
  Vue.prototype.$alert = MessageBox.alert
  Vue.prototype.$confirm = MessageBox.confirm
  Vue.prototype.$prompt = MessageBox.prompt
  Vue.prototype.$notify = Notification
  Vue.prototype.$message = Message
}

export default {
  install: installElementUI
}
