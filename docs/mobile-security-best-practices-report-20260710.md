# 手机端与共享前端安全审计（2026-07-10）

## 执行摘要

本轮未发现生产依赖中的中高危公告，后端也对门店/仓库范围做了服务端授权校验。但代码库中有 2 个高风险问题需要在合并/发布前处理：公告正文未经净化直接 `v-html` 渲染，存在存储型 XSS 风险；工作区新增了一个包含前端私钥的可逆加解密工具文件，现有安全门禁已经因此失败。另有 JS 可读令牌、敏感 POST 负载缓存到 sessionStorage、部署配置缺少安全响应头等中风险项。

## 高风险

### SEC-01：公告正文可形成存储型 XSS

- Rule ID：VUE-XSS-001
- Severity：High
- Location：`erp-ui/src/layout/components/HeaderNotice/DetailView.vue:45,100-102`
- Evidence：接口返回的 `res.data.noticeContent` 被直接赋给 `detail`，模板使用 `v-html="detail.noticeContent"`，未发现前端净化器。
- Impact：能够写入公告正文的账号或被攻陷的接口可向查看公告的用户执行同源脚本，进而读取 JS 可访问令牌、业务数据或发起用户权限内操作。
- Fix：服务端按业务允许标签做 allowlist 净化；前端在唯一入口使用经过审计的 HTML sanitizer，再交给 `v-html`。
- Mitigation：部署严格 CSP/Trusted Types，限制脚本执行面；不能把 CSP 当作替代净化。
- False positive notes：若服务端已有严格且不可绕过的 HTML allowlist，需要提供实现与测试后才能降级。

### SEC-02：工作区存在包含私钥的前端可逆加解密工具

- Rule ID：VUE-SECRETS-001
- Severity：High（当前未打包，发布风险高）
- Location：`erp-ui/src/utils/jsencrypt.js:1-29`
- Evidence：文件定义前端公钥和私钥并导出 `encrypt/decrypt`；私钥内容在本报告中刻意隐去。文件当前未被跟踪、未被引用，生产包中也未检出相关符号。
- Impact：一旦被导入或提交，所有客户端都能获得私钥，所谓“前端加密”可被任意解密；也可能误导后续密码持久化设计。
- Fix：删除该前端私钥工具；需要加密时只在服务端保管私钥，并优先依赖 TLS 与服务端密码哈希。
- Mitigation：保留现有安全门禁，增加秘密扫描，禁止前端目录出现私钥和 `setPrivateKey`。
- False positive notes：当前文件不在生产 bundle，所以不是已部署的活动泄露；但它已导致 `authSecurityHardening.test.js` 失败。

## 中风险

### SEC-03：认证令牌存放在 JavaScript 可读 Cookie 中

- Rule ID：VUE-AUTH-001 / JS-STORAGE-001
- Severity：Medium
- Location：`erp-ui/src/utils/auth.js:3-16`
- Evidence：`Cookies.set('Admin-Token', token)`，未设置 HttpOnly（前端也无法设置 HttpOnly），代码中也未显式设置 SameSite/Secure。
- Impact：任意同源 XSS 都可以读取 bearer token；共享设备或 WebView 调试面也会扩大令牌暴露范围。
- Fix：迁移为后端设置的 HttpOnly、Secure、SameSite 会话 Cookie，并配套 CSRF 防护；如果短期不能迁移，至少缩短令牌寿命并强化 CSP、净化和轮换。
- Mitigation：前端 Cookie 显式设置合适的 `sameSite`/`secure` 只能减少部分风险，不能防 XSS 读取。
- False positive notes：需结合生产网关的令牌有效期、刷新与吊销策略评估最终风险。

### SEC-04：防重复提交会把完整 POST/PUT 负载写入 sessionStorage

- Rule ID：JS-STORAGE-001 / VUE-HTTP-001
- Severity：Medium
- Location：`erp-ui/src/utils/request.js:64-89`；敏感调用示例 `erp-ui/src/api/login.js:41-45`
- Evidence：拦截器把 `config.data` 完整 `JSON.stringify` 后存到 `sessionObj`；`unlockScreen(password)` 没有关闭该逻辑，因此密码负载可进入 sessionStorage。
- Impact：同源 XSS、调试工具、共享终端或后续第三方脚本可读取最近一次请求的完整敏感负载，包括解锁密码、客户信息或业务备注。
- Fix：只在内存中保存不可逆的请求指纹（method + URL + payload hash + time）；对认证、密码、个人信息与文件接口强制跳过缓存。
- Mitigation：成功/失败后立即清理指纹，并对缓存字段做严格白名单。
- False positive notes：登录接口已显式关闭重复提交缓存，但解锁接口未关闭。

### SEC-05：仓库内 Nginx 配置未体现基础安全响应头

- Rule ID：VUE-HEADERS-001 / JS-CSP-001
- Severity：Medium
- Location：`docker/nginx/conf/nginx.conf:32-60`、`docker/nginx/conf/nginx.host.conf:32-60`
- Evidence：只配置了 Cache-Control；未见 Content-Security-Policy、frame-ancestors/X-Frame-Options、X-Content-Type-Options、Referrer-Policy、Permissions-Policy。
- Impact：XSS 缓解、点击劫持、MIME 嗅探和浏览器能力限制依赖外部边缘配置；若外层未补充，ERP 页面防御纵深不足。
- Fix：在实际生产边缘统一设置并测试这些头；CSP 先 report-only 收集，再逐步收紧脚本源。
- Mitigation：至少先加 `X-Content-Type-Options: nosniff`、合适的 `frame-ancestors`、Referrer-Policy 与 Permissions-Policy。
- False positive notes：外部 CDN/WAF/Nginx 可能已经注入，需在生产 URL 上实测响应头后确认。

## 低风险

### SEC-06：Vue 2 生产依赖存在低危 ReDoS 公告

- Rule ID：VUE-SUPPLY-001
- Severity：Low
- Location：`erp-ui/package.json` / `package-lock.json`
- Evidence：`npm audit --omit=dev` 报告 Vue 2.6.12 受 GHSA-5j4c-8p2g-v4jx 影响；总计 4 个低危、0 个中危、0 个高危、0 个严重。
- Impact：在特定 HTML 解析输入下可能造成正则耗时增加；当前 Vue 2 依赖链没有直接无破坏修复。
- Fix：制定 Vue 3/受维护 UI 组件迁移计划；在迁移前避免让不可信大文本进入相关解析路径。
- Mitigation：限制富文本长度、服务端净化并监控异常耗时。
- False positive notes：实际可利用性取决于是否把攻击者可控内容送入受影响解析函数。

## 已确认的安全优点

- `AbstractShopScopeService.java:12-18` 对非管理员选择的门店/仓库执行服务端用户范围校验，前端 `Dept-NumId` 不是唯一授权边界。
- Capacitor 生产配置关闭明文开发服务器与 Android mixed content，生产日志行为设为 none。
- 登录页不再持久化账号密码 Cookie；当前只记住用户名和“记住”状态。
- 生产构建成功，当前未把未跟踪的私钥工具打入 bundle。
- 生产依赖审计没有中高危公告。

## 验证命令结果

- `npm test`：87 项运行，1 项失败；失败项是 `authSecurityHardening.test.js`，根因是未跟踪的 `src/utils/jsencrypt.js` 存在。
- `npm run build:prod`：通过。
- `npm audit --omit=dev --audit-level=moderate`：4 low / 0 moderate / 0 high / 0 critical。
