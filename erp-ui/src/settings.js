module.exports = {
  /**
   * 网页标题
   */
  title: process.env.VUE_APP_TITLE,

  /**
   * 与后端 build-info 对齐的发布诊断信息；生产构建必须由发布脚本显式注入。
   */
  buildCommit: process.env.VUE_APP_BUILD_COMMIT || 'UNSET',
  buildTime: process.env.VUE_APP_BUILD_TIME || 'UNSET',

  /**
   * 20260718 入职合同专用构建的已审批来源。普通构建保持可用，但会显式标记为 UNSET。
   */
  releaseId: process.env.VUE_APP_BUILD_RELEASE_ID || 'UNSET',
  signExcelImportEnabled: String(process.env.VUE_APP_SIGN_EXCEL_IMPORT_ENABLED || 'true').trim().toLowerCase() === 'true',
  approvedPatchSha256: process.env.VUE_APP_BUILD_APPROVED_PATCH_SHA256 || 'UNSET',
  approvedSourceManifestSha256: process.env.VUE_APP_BUILD_APPROVED_SOURCE_MANIFEST_SHA256 || 'UNSET',

  /**
   * 统一待办审批灰度开关。只有显式字符串 true 才启用，任何缺失或拼写错误都安全关闭。
   */
  todoQuickApproveEnabled: process.env.VUE_APP_TODO_QUICK_APPROVE_ENABLED === 'true',
  todoBatchApproveEnabled: process.env.VUE_APP_TODO_BATCH_APPROVE_ENABLED === 'true',

  /**
   * 侧边栏主题 深色主题theme-dark，浅色主题theme-light
   */
  sideTheme: 'theme-dark',

  /**
   * 系统布局配置
   */
  showSettings: true,

  /**
   * 菜单导航模式 1、纯左侧 2、混合（左侧+顶部） 3、纯顶部
   */
  navType: 3,

  /**
   * 是否显示 tagsView
   */
  tagsView: true,

  /**
   * 持久化标签页
   */
  tagsViewPersist: false,

  /**
   * 显示页签图标
   */
  tagsIcon: false,

  /**
   * 标签页样式：card 卡片（默认）、chrome 谷歌浏览器风格
   */
  tagsViewStyle: 'card',

  /**
   * 是否固定头部
   */
  fixedHeader: true,

  /**
   * 是否显示logo
   */
  sidebarLogo: true,

  /**
   * 是否显示动态标题
   */
  dynamicTitle: false,

  /**
   * 是否显示底部版权
   */
  footerVisible: false,

  /**
   * 底部版权文本内容
   */
  footerContent: 'Copyright © 2018-2026 企业管理系统. All Rights Reserved.'
}
