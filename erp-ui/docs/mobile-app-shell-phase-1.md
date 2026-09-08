# Mobile App Shell Phase 1

## Scope

第一期只新增 Capacitor App 壳，复用现有 Vue 移动端页面和路由，不修改 `src/views/mobile` 页面源码。

当前壳能力：

- iOS 工程：`ios/App/App.xcodeproj`
- Android 工程：`android/`
- App 包名：`com.erp.mobile`
- App 名称：`ERP Mobile`
- 打包 Web 目录：`dist`
- WebView User-Agent 追加：`ERP-Mobile-App`

移动首页分流仍由现有前端逻辑处理：手机视口进入根路径后，会按已选店铺/仓库上下文进入店铺或仓库移动工作台。

## Commands

安装依赖后，在 `erp-ui` 目录执行：

```bash
npm run app:sync
```

这个命令会先执行生产构建，再同步到 iOS 和 Android 工程。

打开 iOS 工程：

```bash
npm run app:open:ios
```

打开 Android 工程：

```bash
npm run app:open:android
```

验证 App 壳配置：

```bash
npm run app:verify
```

## Live Web Debug

内置 `dist` 模式不经过 nginx 或 Vue dev-server 代理；如果接口地址仍是 `/prod-api`，登录验证码等接口会请求到 Capacitor 本地 scheme。需要真实登录预览时，用下面的 `CAPACITOR_SERVER_URL` 模式加载已有 Web 服务。

如果要让原生壳临时加载一个正在运行的前端地址，而不是内置 `dist`，先启动前端：

```bash
npm run dev -- --port 1025
```

然后用局域网地址同步原生配置：

```bash
CAPACITOR_SERVER_URL=http://你的电脑局域网IP:1025 npm run app:sync
```

调试结束后，取消 `CAPACITOR_SERVER_URL`，重新运行：

```bash
npm run app:sync
```

这样会恢复为打包内置 `dist` 的 App 壳。
