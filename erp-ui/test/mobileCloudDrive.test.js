const assert = require('assert')
const fs = require('fs')
const path = require('path')
const vm = require('vm')
const babel = require('../node_modules/@babel/core')

const rootDir = path.resolve(__dirname, '..')
const navigation = require('../src/views/mobile/mobileNavigation')
const routeDefinitions = require('../src/views/mobile/mobileRouteDefinitions')

const {
  MOBILE_ROUTES,
  getMobileBottomNav,
  getMobileContextProfile,
  getMobileQuickActions,
  getMobileRouteAccessDecision,
  getMobileRouteRequiredPermissions,
  isMobileBottomNavItemActive,
  isMobileContextOptionalPath,
  isMobileRouteAllowedForPermissions
} = navigation

function readFile(relativePath) {
  return fs.readFileSync(path.join(rootDir, relativePath), 'utf8')
}

function esm(defaultValue, named = {}) {
  return Object.assign({ __esModule: true, default: defaultValue }, named)
}

function loadUserStoreForDriveLifecycle() {
  const filename = path.join(rootDir, 'src/store/modules/user.js')
  const transformed = babel.transformSync(fs.readFileSync(filename, 'utf8'), {
    filename,
    babelrc: false,
    configFile: false,
    sourceType: 'module',
    plugins: [require.resolve('../node_modules/@babel/plugin-transform-modules-commonjs')]
  }).code
  const module = { exports: {} }
  const modules = {
    '@/store': esm({ dispatch: () => Promise.resolve() }),
    '@/router': esm({ currentRoute: { path: '/' }, replace: () => Promise.resolve() }),
    '@/plugins/cache': esm({ session: { set() {} } }),
    '@/plugins/element-services': esm(undefined, {
      MessageBox: { confirm: () => Promise.resolve() }
    }),
    '@/api/login': esm(undefined, {
      login: () => new Promise(() => {}),
      logout: () => Promise.resolve(),
      getInfo: () => Promise.resolve({}),
      refreshToken: () => Promise.resolve({ data: 3600 })
    }),
    '@/utils/auth': esm(undefined, {
      getToken: () => '',
      setToken() {},
      setExpiresIn() {},
      removeToken() {},
      removeExpiresIn() {}
    }),
    '@/utils/shopContext': esm(undefined, { clearSelectedDept() {} }),
    '@/utils/signScopeContext': esm(undefined, { clearSelectedSignScope() {} }),
    '@/utils/passwordResetReminder': esm(undefined, {
      getPasswordResetRoute: () => '/profile',
      resetPasswordResetReminderState() {},
      setPendingPasswordResetReminder() {},
      showPendingPasswordResetReminderIfReady() {}
    }),
    '@/utils/validate': esm(undefined, {
      isEmpty: value => value === undefined || value === null || value === ''
    }),
    '@/utils/mobileHrQueueState': esm(undefined, { clearMobileHrQueueStateCache() {} }),
    '@/assets/images/profile.jpg': esm('profile.jpg'),
    '@/services/lazyPushRegistration': esm({
      initialize: () => Promise.resolve(),
      disable: () => Promise.resolve()
    }),
    '@/utils/sessionMode': esm(undefined, {
      isCookiePreferredSession: () => false,
      setWebSessionStatus() {},
      subscribeWebSessionStatus() { return () => {} }
    })
  }
  vm.runInNewContext(transformed, {
    module,
    exports: module.exports,
    require(request) {
      if (Object.prototype.hasOwnProperty.call(modules, request)) return modules[request]
      throw new Error(`unexpected user-store dependency: ${request}`)
    },
    Promise,
    Object,
    Array,
    String,
    Error,
    setTimeout,
    clearTimeout
  }, { filename })
  return module.exports.default || module.exports
}

assert.strictEqual(MOBILE_ROUTES.drive, '/mobile/drive', 'mobile cloud drive should have a stable route')

const driveRoute = routeDefinitions.mobileRouteDefinitions.find(route => route.path === MOBILE_ROUTES.drive)
assert.ok(driveRoute, 'mobile cloud drive route should be registered')
assert.ok(
  String(driveRoute.component).includes("@/views/mobile/drive/index"),
  'mobile cloud drive route should lazy-load its dedicated page'
)
assert.deepStrictEqual(
  driveRoute.meta.mobileFeature,
  {
    featureKey: 'drive',
    permissions: ['drive:access'],
    title: '云盘',
    heading: '企业云盘',
    subtitle: '个人文件、公司公共盘和部门资料',
    icon: 'cloud',
    tone: 'blue'
  },
  'mobile cloud drive should expose the confirmed route metadata'
)

assert.deepStrictEqual(
  getMobileRouteRequiredPermissions('/mobile/drive'),
  ['drive:access'],
  'mobile cloud drive should require drive:access'
)
assert.strictEqual(
  isMobileContextOptionalPath('/mobile/drive'),
  true,
  'mobile cloud drive should load without a selected store or warehouse'
)
assert.strictEqual(
  isMobileRouteAllowedForPermissions('/mobile/drive', [], { driveEnabled: true }),
  false,
  'enabled mobile cloud drive should still require its permission'
)
assert.strictEqual(
  isMobileRouteAllowedForPermissions('/mobile/drive', ['drive:access'], { driveEnabled: true }),
  true,
  'enabled mobile cloud drive should allow drive:access'
)
assert.strictEqual(
  isMobileRouteAllowedForPermissions('/mobile/drive', ['*:*:*'], { driveEnabled: false }),
  false,
  'disabled mobile cloud drive should reject wildcard administrators'
)
assert.strictEqual(
  getMobileRouteAccessDecision('/mobile/drive', '', ['*:*:*'], { driveEnabled: false }).path !== '',
  true,
  'disabled direct mobile cloud drive routes should redirect safely'
)

;['STORE', 'WAREHOUSE'].forEach(contextType => {
  const profile = getMobileContextProfile(contextType)
  const driveActions = profile.quickActions.filter(action => action.path === MOBILE_ROUTES.drive)
  assert.strictEqual(driveActions.length, 1, `${contextType} should expose one cloud drive overflow action`)
  assert.deepStrictEqual(driveActions[0].permissions, ['drive:access'])
  assert.strictEqual(driveActions[0].featureFlag, 'drive')
  assert.strictEqual(driveActions[0].placement, 'more')

  assert.ok(
    !getMobileQuickActions(contextType, ['*:*:*'], { driveEnabled: false })
      .some(action => action.path === MOBILE_ROUTES.drive),
    `${contextType} should hide cloud drive while the feature is disabled`
  )
  assert.ok(
    getMobileQuickActions(contextType, ['drive:access'], { driveEnabled: true })
      .some(action => action.path === MOBILE_ROUTES.drive),
    `${contextType} should show cloud drive when enabled and permitted`
  )
  assert.strictEqual(
    getMobileBottomNav(contextType, ['*:*:*'], { driveEnabled: true }).length,
    5,
    `${contextType} bottom navigation should remain exactly five items`
  )
})

const mineRoute = routeDefinitions.mobileRouteDefinitions.find(route => route.path === MOBILE_ROUTES.mine)
const mineDriveActions = mineRoute.meta.mobileFeature.actions.filter(action => action.path === MOBILE_ROUTES.drive)
assert.strictEqual(mineDriveActions.length, 1, 'mine should expose one cloud drive action')
assert.deepStrictEqual(mineDriveActions[0].permissions, ['drive:access'])
assert.strictEqual(mineDriveActions[0].featureFlag, 'drive')
assert.strictEqual(mineDriveActions[0].icon, 'cloud')

assert.strictEqual(typeof isMobileBottomNavItemActive, 'function')
assert.strictEqual(
  isMobileBottomNavItemActive(MOBILE_ROUTES.mine, MOBILE_ROUTES.drive),
  true,
  'mine bottom navigation should remain active inside mobile cloud drive'
)

const cloudIconPath = 'M6 5h5l2 2h5a3 3 0 0 1 3 3v7a3 3 0 0 1-3 3H6a3 3 0 0 1-3-3V8a3 3 0 0 1 3-3Zm0 2a1 1 0 0 0-1 1v9a1 1 0 0 0 1 1h12a1 1 0 0 0 1-1v-7a1 1 0 0 0-1-1h-5.8l-2-2H6Z'
const workbenchSource = readFile('src/views/mobile/components/MobileWorkbenchShell.vue')
const featureSource = readFile('src/views/mobile/feature/index.vue')
const gettersSource = readFile('src/store/getters.js')
const permissionGuardSource = readFile('src/permission.js')

assert.ok(workbenchSource.includes(`cloud: "${cloudIconPath}"`), 'workbench should render the cloud icon')
assert.ok(featureSource.includes(`cloud: "${cloudIconPath}"`), 'mine actions should render the cloud icon')
assert.ok(
  workbenchSource.includes('driveEnabled') && workbenchSource.includes('getMobileQuickActions'),
  'workbench should pass explicit cloud drive feature state into action filtering'
)
assert.ok(
  featureSource.includes('featureFlag') && featureSource.includes('driveEnabled'),
  'mine actions should hide feature-flagged cloud drive entries while disabled'
)
const userModule = loadUserStoreForDriveLifecycle()
const driveState = JSON.parse(JSON.stringify(userModule.state))
const driveContext = {
  state: driveState,
  commit(type, payload) {
    userModule.mutations[type](driveState, payload)
  }
}
assert.strictEqual(driveState.driveEnabled, false)
userModule.mutations.SET_DRIVE_ENABLED(driveState, true)
assert.strictEqual(driveState.driveEnabled, true)
userModule.actions.Login(driveContext, {
  username: 'tester',
  password: 'secret',
  code: '',
  uuid: ''
})
assert.strictEqual(driveState.driveEnabled, false,
  'login start must reset cloud drive before the next account is authenticated')
userModule.mutations.SET_DRIVE_ENABLED(driveState, true)
userModule.actions.LogOut(driveContext)
assert.strictEqual(driveState.driveEnabled, false,
  'logout must synchronously reset cloud drive state')
assert.ok(gettersSource.includes('driveEnabled: state => state.user.driveEnabled'))
assert.ok(
  permissionGuardSource.includes('driveEnabled: store.getters.driveEnabled') &&
    permissionGuardSource.includes('getMobileFeatureState()') &&
    permissionGuardSource.includes('isMobileContextOptionalPath(entryPath)'),
  'the route guard should enforce explicit feature state even without an organization context'
)

const mobileDriveStatePath = path.join(rootDir, 'src/views/mobile/drive/mobileDriveState.js')
const mobileDrivePagePath = path.join(rootDir, 'src/views/mobile/drive/index.vue')
const mobileDriveActionSheetPath = path.join(rootDir, 'src/views/mobile/drive/components/MobileDriveActionSheet.vue')
const mobileDriveMoveSheetPath = path.join(rootDir, 'src/views/mobile/drive/components/MobileDriveMoveSheet.vue')
const mobileDriveRenameDialogPath = path.join(rootDir, 'src/views/mobile/drive/components/MobileDriveRenameDialog.vue')
const mobileConfirmDialogPath = path.join(rootDir, 'src/views/mobile/feature/components/MobileConfirmDialog.vue')
assert.ok(fs.existsSync(mobileDriveStatePath), 'mobile cloud drive state helpers should exist')
assert.ok(fs.existsSync(mobileDrivePagePath), 'mobile cloud drive page should exist')
assert.ok(fs.existsSync(mobileDriveActionSheetPath), 'mobile cloud drive action sheet should exist')
assert.ok(fs.existsSync(mobileDriveMoveSheetPath), 'mobile cloud drive move sheet should exist')
assert.ok(fs.existsSync(mobileDriveRenameDialogPath), 'mobile cloud drive rename dialog should exist')
assert.ok(fs.existsSync(mobileConfirmDialogPath), 'shared mobile confirmation dialog should exist')

const {
  canStartMobileUpload,
  clearMobileUploadForNode,
  createMobileSearchState,
  createMobileUploadState,
  driveMobileErrorMessage,
  formatMobileBytes,
  getMobilePreviewKind,
  getMobileRemainingQuotaBytes,
  getMobileFolderParentId,
  isMobileUploadDestinationCurrent,
  isMobilePreviewable,
  markMobileUploadCanceled,
  markMobileUploadDone,
  markMobileUploadFailed,
  popMobileFolder,
  prepareCapturedFile,
  pushMobileFolder,
  renameMobileUploadNode,
  saveMobileBlob,
  selectInitialDriveSpace,
  updateMobileUploadProgress
} = require(mobileDriveStatePath)

assert.strictEqual(
  selectInitialDriveSpace([
    { spaceId: 1, canRead: false },
    { spaceId: 2, canRead: true },
    { spaceId: 3 }
  ]).spaceId,
  2,
  'mobile drive should select the first readable space'
)
assert.strictEqual(selectInitialDriveSpace([]), null)

const folderStack = pushMobileFolder([], { nodeId: 11, nodeName: '项目' })
const nestedStack = pushMobileFolder(folderStack, { nodeId: 12, nodeName: '合同' })
assert.deepStrictEqual(nestedStack, [
  { nodeId: 11, nodeName: '项目' },
  { nodeId: 12, nodeName: '合同' }
])
assert.strictEqual(getMobileFolderParentId(nestedStack), 12)
assert.deepStrictEqual(popMobileFolder(nestedStack), folderStack)
assert.deepStrictEqual(folderStack, [{ nodeId: 11, nodeName: '项目' }], 'folder helpers should not mutate prior state')

assert.deepStrictEqual(
  createMobileSearchState({ parentId: 12, keyword: '合同', pageNum: 3 }, ''),
  { parentId: 12, keyword: '', pageNum: 1 },
  'clearing whole-space search should return to the same logical directory'
)
assert.strictEqual(formatMobileBytes(1536), '1.5 KB')
assert.strictEqual(
  getMobileRemainingQuotaBytes({ usedBytes: 734003200, quotaBytes: 2147483648 }),
  1413480448,
  'mobile drive should calculate remaining quota from total minus used bytes'
)
assert.strictEqual(
  getMobileRemainingQuotaBytes({ usedBytes: 300, quotaBytes: 200 }),
  0,
  'mobile remaining quota should never become negative'
)
assert.strictEqual(getMobileRemainingQuotaBytes(null), 0)
assert.strictEqual(isMobilePreviewable({ canPreview: true, extension: 'PDF' }), true)
assert.strictEqual(isMobilePreviewable({ canPreview: true, extension: 'docx' }), false)
assert.strictEqual(driveMobileErrorMessage('DRIVE_ACCESS_DENIED'), '无权执行此操作')

const uploadFile = { name: '现场照片.jpg', type: 'image/jpeg', size: 4096 }
const uploadState = createMobileUploadState(uploadFile, 7, 12)
assert.strictEqual(uploadState.file, uploadFile)
assert.strictEqual(uploadState.targetSpaceId, 7)
assert.strictEqual(uploadState.targetParentId, 12)
assert.strictEqual(uploadState.status, 'uploading')
assert.strictEqual(canStartMobileUpload(uploadState), false, 'mobile drive should allow only one active upload')
assert.strictEqual(canStartMobileUpload({ ...uploadState, status: 'failed' }), true)
assert.strictEqual(updateMobileUploadProgress(uploadState, { loaded: 1024, total: 4096 }).progress, 25)
const failedUpload = markMobileUploadFailed(uploadState, '网络中断')
assert.strictEqual(failedUpload.status, 'failed')
assert.strictEqual(failedUpload.file, uploadFile)
assert.strictEqual(failedUpload.targetSpaceId, 7)
assert.strictEqual(failedUpload.targetParentId, 12)
const canceledUpload = markMobileUploadCanceled(uploadState)
assert.strictEqual(canceledUpload.status, 'canceled')
assert.strictEqual(canceledUpload.error, '')
assert.strictEqual(isMobileUploadDestinationCurrent(failedUpload, 7, 12), true)
assert.strictEqual(
  isMobileUploadDestinationCurrent(failedUpload, 7, 99),
  false,
  'navigation during upload should never retarget retry or refresh'
)
const completedUpload = markMobileUploadDone(uploadState, {
  nodeId: 81,
  nodeName: '现场照片.jpg'
})
assert.strictEqual(completedUpload.nodeId, 81)
assert.strictEqual(completedUpload.displayName, '现场照片.jpg')
assert.strictEqual(
  renameMobileUploadNode(completedUpload, 81, '门店现场照片.jpg').displayName,
  '门店现场照片.jpg',
  'renaming a just-uploaded node should update the completion card'
)
assert.strictEqual(
  clearMobileUploadForNode(completedUpload, 81),
  null,
  'moving a just-uploaded node to trash should clear stale completion state'
)

assert.strictEqual(getMobilePreviewKind({ canPreview: true, extension: 'jpg' }), 'image')
assert.strictEqual(getMobilePreviewKind({ canPreview: true, extension: 'PDF' }), 'pdf')
assert.strictEqual(getMobilePreviewKind({ canPreview: true, extension: 'txt' }), 'text')
assert.strictEqual(getMobilePreviewKind({ canPreview: true, extension: 'docx' }), 'unsupported')
assert.strictEqual(getMobilePreviewKind({ canPreview: true, extension: 'heic' }), 'unsupported')

const mobileDrivePage = readFile('src/views/mobile/drive/index.vue')
const mobileDriveActionSheet = readFile('src/views/mobile/drive/components/MobileDriveActionSheet.vue')
const mobileDriveMoveSheet = readFile('src/views/mobile/drive/components/MobileDriveMoveSheet.vue')
const mobileDriveRenameDialog = readFile('src/views/mobile/drive/components/MobileDriveRenameDialog.vue')
const mobileConfirmDialog = readFile('src/views/mobile/feature/components/MobileConfirmDialog.vue')
const mobileLoadNodesSource = mobileDrivePage.slice(
  mobileDrivePage.indexOf('async loadNodes(reset)'),
  mobileDrivePage.indexOf('handleSpaceChange()')
)
;['listDriveSpaces', 'listDriveNodes', 'getDriveNode', 'getDriveContent'].forEach(apiName => {
  assert.ok(mobileDrivePage.includes(apiName), `mobile cloud drive should use ${apiName}`)
})
assert.ok(
  mobileDrivePage.includes('aria-label="选择云盘空间"') &&
    mobileDrivePage.includes('aria-label="云盘路径"') &&
    mobileDrivePage.includes('type="search"') &&
    mobileDrivePage.includes('class="mobile-drive-list"') &&
    mobileDrivePage.includes('loadMore'),
  'mobile cloud drive should expose space, breadcrumb, search, list and incremental loading controls'
)
assert.ok(
  mobileDrivePage.includes('min-height: 44px') &&
    mobileDrivePage.includes('env(safe-area-inset-bottom)'),
  'mobile cloud drive controls should meet touch target and safe-area requirements'
)
assert.ok(
  mobileDrivePage.includes('剩余 {{ formatMobileBytes(getMobileRemainingQuotaBytes(activeSpace)) }}') &&
    mobileDrivePage.includes('总额度 {{ formatMobileBytes(activeSpace.quotaBytes) }}'),
  'mobile space card should distinguish remaining quota from total quota'
)
assert.ok(
  mobileDrivePage.includes('v-else-if="isMobilePreviewable(node)"') &&
    mobileDrivePage.includes('v-else class="mobile-drive-row__main mobile-drive-row__main--static"'),
  'mobile unsupported files should not expose a dead-end preview button'
)
assert.ok(
  mobileDrivePage.includes('placeholder="搜索当前空间的文件和文件夹"') &&
    mobileDrivePage.includes('aria-label="搜索当前空间的文件和文件夹"'),
  'mobile search copy should state that search is scoped to the active space'
)
assert.strictEqual(
  (mobileDrivePage.match(/tabindex="-1"/g) || []).length,
  2,
  'both mobile hidden file inputs should leave keyboard navigation'
)
assert.strictEqual(
  (mobileDrivePage.match(/aria-hidden="true"/g) || []).length >= 2,
  true,
  'both mobile hidden file inputs should leave the accessibility tree'
)
assert.strictEqual(
  (mobileDrivePage.match(/\shidden(?:\s|>)/g) || []).length,
  2,
  'both mobile file inputs should be natively hidden from the accessibility tree'
)
assert.ok(
  (mobileDrivePage.includes('@click="retryLoad"') || mobileDrivePage.includes('handlePageBlockingAction')) &&
    mobileDrivePage.includes('async retryLoad()') &&
    mobileDrivePage.includes('await this.loadSpaces()') &&
    mobileDrivePage.includes('await this.loadNodes(true)'),
  'mobile retry should recover both initial space loading and later directory loading failures'
)
assert.ok(
  mobileLoadNodesSource.includes('this.clearNodeResults()') &&
    mobileLoadNodesSource.includes('this.nodes = reset ? rows : this.nodes.concat(rows)') &&
    mobileLoadNodesSource.includes('if (!reset && loadMoreFailed)') &&
    mobileLoadNodesSource.includes('this.pageNum = Math.max(1, request.pageNum - 1)') &&
    !mobileLoadNodesSource.includes('previousNodes'),
  'mobile reset requests should clear old rows and failed incremental loads should retry the same page'
)
assert.ok(
  mobileDrivePage.includes("getDriveContent(node.nodeId, 'download')") &&
    mobileDrivePage.includes('saveMobileBlob'),
  'mobile downloads should use authenticated Blob content and the native share/save helper'
)
assert.ok(
  !mobileDrivePage.includes('shopContext') &&
    !mobileDrivePage.includes('listRecentDriveNodes') &&
    !mobileDrivePage.includes('purgeDriveNode') &&
    !mobileDrivePage.includes('emptyDriveTrash') &&
    !mobileDrivePage.includes('updateDriveQuota'),
  'mobile browsing should stay organization-independent and exclude quota and permanent trash administration'
)
assert.ok(
  mobileDrivePage.includes('listDriveTrash') &&
    mobileDrivePage.includes('restoreDriveNode') &&
    mobileDrivePage.includes('撤销删除') &&
    mobileDrivePage.includes("switchView('trash')") &&
    mobileDrivePage.includes('trashSequence') &&
    mobileDrivePage.includes('isCurrentTrashRequest') &&
    mobileDrivePage.includes("this.activeView === 'files'"),
  'mobile cloud drive should expose a recoverable trash view without permanent deletion'
)
assert.ok(
  mobileDrivePage.includes('el-icon-folder') &&
    mobileDrivePage.includes('el-icon-document') &&
    mobileDriveMoveSheet.includes('el-icon-folder') &&
    !mobileDrivePage.includes('⌑') &&
    !mobileDrivePage.includes('▤') &&
    !mobileDriveMoveSheet.includes('⌑'),
  'mobile cloud drive should use the shared icon library instead of font-dependent glyphs'
)
assert.ok(
  mobileDrivePage.includes('renameDriveNode') &&
    mobileDrivePage.includes('moveDriveNode') &&
    mobileDrivePage.includes('trashDriveNode') &&
    mobileDrivePage.includes('MobileDriveActionSheet') &&
    mobileDrivePage.includes('MobileDriveMoveSheet') &&
    mobileDrivePage.includes('MobileDriveRenameDialog') &&
    mobileDrivePage.includes('version: node.version') &&
    mobileDrivePage.includes('version: this.moveNode.version'),
  'mobile users should receive capability-aware rename, move and move-to-trash operations with optimistic versions'
)
assert.ok(
  mobileDrivePage.includes(":aria-label=\"'更多操作 ' + node.nodeName\"") &&
    !mobileDrivePage.includes(":aria-label=\"'进入文件夹 ' + node.nodeName\"") &&
    mobileDriveActionSheet.includes("node.nodeType === 'FILE'") &&
    mobileDriveActionSheet.includes("$emit('download')") &&
    mobileDriveActionSheet.includes('v-if="node && node.canWrite"') &&
    mobileDriveActionSheet.includes('v-if="node && node.canDelete"'),
  'mobile rows should have one folder-open target and a capability-aware action sheet that retains file download'
)
;[mobileDriveActionSheet, mobileDriveMoveSheet, mobileDriveRenameDialog].forEach((source, index) => {
  assert.ok(
    source.includes('role="dialog"') &&
      source.includes('aria-modal="true"') &&
      source.includes('createMobileDialogFocusManager') &&
      source.includes('mountMobileOverlay') &&
      source.includes('releaseMobileOverlay') &&
      source.includes('min-height: 44px') &&
      source.includes('env(safe-area-inset-bottom'),
    `mobile drive management overlay ${index + 1} should trap focus, lock scroll and honor touch/safe-area rules`
  )
})
assert.ok(
  mobileDriveMoveSheet.includes('isSameParentTarget') &&
    mobileDriveMoveSheet.includes('Number(this.node.parentId)') &&
    mobileDriveMoveSheet.includes('if (!reset && loadMoreFailed)') &&
    mobileDriveMoveSheet.includes('this.pageNum = Math.max(1, request.pageNum - 1)') &&
    mobileDriveMoveSheet.includes('当前文件已在所选文件夹') &&
    mobileDriveMoveSheet.includes('createDriveFolder') &&
    mobileDriveMoveSheet.includes('新建目标文件夹'),
  'mobile move should disable a no-op target, retry failed pages and create a target folder in place'
)
assert.ok(
  mobileDrivePage.includes(':busy="operationBusy"') &&
    mobileConfirmDialog.includes('busy: { type: Boolean, default: false }') &&
    mobileConfirmDialog.includes("{{ busy ? '处理中…' : confirmText }}"),
  'mobile destructive confirmation should prevent duplicate actions and show its busy state'
)

assert.ok(
  mobileDrivePage.includes('type="file"') &&
    mobileDrivePage.includes('accept="image/*"') &&
    mobileDrivePage.includes('capture="environment"') &&
    mobileDrivePage.includes('handleFileSelection') &&
    mobileDrivePage.includes('uploadDriveFile'),
  'mobile cloud drive should provide general and rear-camera inputs through one upload flow'
)
assert.ok(
  mobileDrivePage.includes('targetSpaceId') &&
    mobileDrivePage.includes('targetParentId') &&
    mobileDrivePage.includes('retryUpload') &&
    mobileDrivePage.includes('onUploadProgress') &&
    mobileDrivePage.includes('isMobileUploadDestinationCurrent'),
  'mobile upload retry and refresh should retain the original destination with live progress'
)
assert.ok(
  mobileDrivePage.includes('new AbortController()') &&
    mobileDrivePage.includes('controller.signal') &&
    mobileDrivePage.includes('cancelUpload()') &&
    mobileDrivePage.includes('cancelActiveUpload()') &&
    mobileDrivePage.includes('isDriveRequestCanceled(error)') &&
    mobileDrivePage.includes("uploadState.status === 'canceled'"),
  'mobile uploads should expose cancellation, distinguish it from failure and abort on teardown'
)
assert.ok(
  mobileDrivePage.includes("getDriveContent(node.nodeId, 'preview')") &&
    mobileDrivePage.includes('getMobilePreviewKind') &&
    mobileDrivePage.includes('URL.createObjectURL(blob)') &&
    mobileDrivePage.includes('releaseDriveObjectUrl') &&
    mobileDrivePage.includes('beforeRouteLeave') &&
    mobileDrivePage.includes('beforeDestroy') &&
    mobileDrivePage.includes('{{ previewText }}') &&
    !mobileDrivePage.includes('v-html'),
  'mobile preview should render safe text/image/PDF states and clean object URLs on every exit'
)
assert.ok(
  mobileDrivePage.includes('如显示异常，请下载原文件') &&
    mobileDrivePage.includes('@click="downloadNode(previewNode)"'),
  'embedded mobile previews should use accurate fallback copy and retain a download action'
)

async function runMobileDriveStateTests() {
  class FakeFile {
    constructor(parts, name, options) {
      this.parts = parts
      this.name = name
      this.type = options && options.type
    }
  }

  const blob = new Blob(['mobile-drive'], { type: 'text/plain' })

  const capturedJpeg = { name: 'camera-image', type: 'image/jpeg', size: 5, lastModified: 7 }
  const preparedJpeg = prepareCapturedFile(capturedJpeg, { File: FakeFile, now: () => 1700000000000 })
  assert.strictEqual(preparedJpeg.name, '照片-1700000000000.jpg')
  assert.strictEqual(preparedJpeg.type, 'image/jpeg')
  assert.strictEqual(preparedJpeg.parts[0], capturedJpeg, 'captured-file naming should preserve original bytes')

  const capturedHeic = { name: 'capture', type: 'image/heic', size: 6 }
  assert.strictEqual(
    prepareCapturedFile(capturedHeic, { File: FakeFile, now: () => 1700000000001 }).name,
    '照片-1700000000001.heic'
  )
  const namedCameraFile = { name: 'IMG_0001.png', type: 'image/png', size: 7 }
  assert.strictEqual(
    prepareCapturedFile(namedCameraFile, { File: FakeFile, now: () => 1700000000002 }),
    namedCameraFile,
    'camera files with a valid extension should keep their logical name'
  )
  const unknownCameraFile = { name: 'capture', type: 'application/octet-stream', size: 8 }
  assert.strictEqual(
    prepareCapturedFile(unknownCameraFile, { File: FakeFile, now: () => 1700000000003 }),
    unknownCameraFile,
    'unknown capture types should never be relabelled as an image'
  )

  const shared = []
  const sharedResult = await saveMobileBlob(blob, '说明.txt', {
    File: FakeFile,
    navigator: {
      canShare: payload => Array.isArray(payload.files) && payload.files[0].name === '说明.txt',
      share: payload => {
        shared.push(payload.files[0])
        return Promise.resolve()
      }
    },
    saveAs: () => assert.fail('successful native sharing should not download twice')
  })
  assert.strictEqual(sharedResult, 'shared')
  assert.strictEqual(shared[0].name, '说明.txt')
  assert.strictEqual(shared[0].type, 'text/plain')

  let abortedFallbacks = 0
  const abortedResult = await saveMobileBlob(blob, '说明.txt', {
    File: FakeFile,
    navigator: {
      canShare: () => true,
      share: () => Promise.reject(Object.assign(new Error('cancelled'), { name: 'AbortError' }))
    },
    saveAs: () => { abortedFallbacks += 1 }
  })
  assert.strictEqual(abortedResult, 'cancelled')
  assert.strictEqual(abortedFallbacks, 0, 'closing a native share sheet should remain neutral')

  const downloaded = []
  const fallbackResult = await saveMobileBlob(blob, '说明.txt', {
    File: FakeFile,
    navigator: {
      canShare: () => true,
      share: () => Promise.reject(new Error('platform size cap'))
    },
    saveAs: (value, name) => downloaded.push({ value, name })
  })
  assert.strictEqual(fallbackResult, 'downloaded')
  assert.strictEqual(downloaded.length, 1, 'failed native sharing should fall back exactly once')
  assert.strictEqual(downloaded[0].name, '说明.txt')

  console.log('mobileCloudDrive tests passed')
}

runMobileDriveStateTests().catch(error => {
  console.error(error)
  process.exit(1)
})
