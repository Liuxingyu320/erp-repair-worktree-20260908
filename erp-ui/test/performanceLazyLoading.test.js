const assert = require("assert")
const fs = require("fs")
const path = require("path")

const root = path.resolve(__dirname, "..")
const read = relativePath => fs.readFileSync(path.join(root, relativePath), "utf8")

const mainSource = read("src/main.js")
const userStoreSource = read("src/store/modules/user.js")
const lazyPushSource = read("src/services/lazyPushRegistration.js")
const requestSource = read("src/utils/request.js")
const downloadPluginSource = read("src/plugins/download.js")
const clipboardSource = read("src/directive/module/clipboard.js")
const appSource = read("src/App.vue")
const settingsSource = read("src/layout/components/Settings/index.vue")

assert.ok(
  mainSource.includes("from '@/services/lazyPushRegistration'") &&
    userStoreSource.includes("from '@/services/lazyPushRegistration'") &&
    !mainSource.includes("from '@/services/pushRegistration'") &&
    !userStoreSource.includes("from '@/services/pushRegistration'"),
  "app startup and user store should use the lazy native-push facade"
)
assert.ok(
  lazyPushSource.includes("Capacitor.isNativePlatform()") &&
    lazyPushSource.includes("webpackChunkName: \"chunk-native-push\"") &&
    lazyPushSource.includes("'@/services/pushRegistration'"),
  "native push implementation should load only after a native session needs it"
)
assert.ok(
  !requestSource.includes("import { saveAs } from 'file-saver'") &&
    requestSource.includes("webpackChunkName: \"chunk-download\""),
  "shared request startup should not synchronously include file-saver"
)
assert.ok(
  !downloadPluginSource.includes("import { saveAs } from 'file-saver'") &&
    downloadPluginSource.includes("webpackChunkName: \"chunk-download\""),
  "download plugin should load file-saver only for a save action"
)
assert.ok(
  !clipboardSource.includes("import Clipboard from 'clipboard'") &&
    clipboardSource.includes("webpackChunkName: \"chunk-clipboard\""),
  "clipboard library should load only when the directive binds"
)
assert.ok(
  !appSource.includes("<theme-picker") &&
    !appSource.includes("components/ThemePicker") &&
    settingsSource.includes("components/ThemePicker"),
  "ThemePicker should live only in the lazy desktop settings drawer"
)

console.log("performance lazy-loading tests passed")
