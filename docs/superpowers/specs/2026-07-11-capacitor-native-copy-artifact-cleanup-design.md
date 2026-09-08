# Capacitor 原生复制产物清理设计

**日期：** 2026-07-11

**状态：** 已由用户确认

**范围：** `erp-ui/android` 与 `erp-ui/ios` 中的 Capacitor 可再生成产物

## 问题与根因

Android 和 iOS 原生目录中存在 Finder/同步冲突产生的编号副本，例如 `config 3.xml`、`index 3.html`、`public 2`、`static 3` 和 `capacitor-cordova-*-plugins 2`。其中配置与插件文件大多是标准文件的字节级副本，编号 `index` 和整棵 `public` 目录则包含较旧的前端构建。

这些文件不是第二份业务源码，也不应合并回标准文件。现有清理脚本只删除两个配置目录中的 `config*.xml`，没有覆盖可再生成 Web 资源、Gradle 构建目录和 Cordova 插件产物，因此复制目录会残留并污染测试及工作区。

清理后的复验进一步确认了环境根因：仓库所在的 macOS Desktop 带有 `com.apple.file-provider-domain-id`，系统设置已启用 iCloud“桌面与文稿”，`brctl status` 同时显示 File Provider 正在向 `ERP-NEW` 应用云端变更。一次被删除的 `config 2.xml` 保留 11:23 的旧创建/修改时间，却在 20:12 被重新加入目录，证明它由云端旧版本恢复，而不是 Capacitor 再生成。

## 方案比较

### 方案 A：逐个比较后手工合并

能够处理当前文件，但容易把旧 `index` 覆盖到新构建，且下一次同步后还会复发。不采用。

### 方案 B：只增加 `.gitignore`

可以隐藏状态噪声，却不会清除磁盘上的旧资源，也无法保证原生包不引用错误副本。不采用。

### 方案 C：限定生成目录自动清理并重新同步（采用）

把删除范围限制到 Capacitor/Gradle 可再生成目录及明确的 `config` 编号副本。同步和原生校验流程在开始与结束两个边界都执行清理脚本，既清除 File Provider 已恢复的旧副本，也清除运行期间出现的副本；随后由现有资产校验比较 `dist`、Android 和 iOS 的标准 `index.html`。

## 安全边界

允许递归删除名称匹配 Finder 编号副本格式 ` <数字>`、` <数字>.<扩展名>` 的条目，但仅限：

- `android/app/build`
- `android/app/src/main/assets`
- `android/capacitor-cordova-android-plugins`
- `ios/App/App/public`
- `ios/capacitor-cordova-ios-plugins`

允许在父目录删除的完整生成目录副本仅限：

- `android/capacitor-cordova-android-plugins <数字>`
- `ios/capacitor-cordova-ios-plugins <数字>`
- `ios/App/App/public <数字>`

允许删除的配置副本仅限 Android/iOS 标准配置目录中的 `config <数字>.xml`。清理器不得递归扫描 `ios/App/App` 或 `android/app/src/main` 的其他源码区域。

以下内容必须保留：

- `AndroidManifest.xml`、Gradle 工程和 Wrapper
- `Info.plist`、Xcode 工程、Swift 源码和签名设置
- 无编号的 `config.xml`
- 无编号的标准生成目录与资源

## 行为与错误处理

清理脚本应做到幂等：目录不存在时跳过；编号文件和目录存在时删除；无编号条目保持不变。完成后输出删除数量和相对路径摘要，继续执行现有 Cordova 配置加固。任何文件系统错误直接让同步命令失败，不静默忽略。

`app:sync` 与 `app:verify` 必须在流程开始和最终校验前各运行一次清理器。普通 `npm test` 保留只检测、不自动修复的门禁语义，以便持续暴露工作目录再次受到 File Provider 干扰的事实。

代码可以保证原生工作流边界干净，但无法关闭系统级 iCloud 同步。长期最稳妥的工作区应位于未启用 File Provider 的目录（例如 `~/Developer`），该迁移不在本次自动修改范围内。

## 测试与验收

1. 在临时目录构造 Android/iOS 标准文件、编号文件、编号目录和受保护原生文件。
2. 执行真实清理脚本，确认所有限定范围内编号产物被删除。
3. 确认 `Info.plist`、`AndroidManifest.xml`、标准资源和标准配置仍存在。
4. 确认标准 `config.xml` 中的通配远程访问配置仍被加固。
5. 对真实工作区增加同范围门禁，编号产物必须为零。
6. 执行 `npm run app:sync`，随后运行全量测试、生产构建和原生资产一致性校验。

本设计只重新生成原生资源，不打开 IDE、不签名、不安装到设备，也不部署。
