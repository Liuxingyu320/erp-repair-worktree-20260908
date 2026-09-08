# 手机端五项剩余风险专项实施方案

日期：2026-07-28
状态：方案阶段，未修改代码
范围：供应商数据范围、HttpOnly 会话、高危依赖、移动首屏包体、入职负责人搜索选择器

## 1. 目标

完成以下五项整改，同时保持现有桌面端、移动 Web、Capacitor iOS/Android 和旧客户端兼容：

1. 统一供应商列表与采购选择器的数据范围。
2. 将 Web 会话从脚本可读 Token 分阶段迁移到 HttpOnly Cookie。
3. 修复 `brace-expansion` 两条高危依赖链。
4. 将移动共享冷启动体积从约 433 KiB gzip 分阶段降低到 270 KiB gzip 以内。
5. 将约 160 人的负责人原生下拉替换为可搜索、分页、可过滤的企业级人员选择器。

## 2. 总体原则

- 每个专项独立提交、独立测试、独立回滚。
- 不把供应商范围扩大、依赖升级、Element UI 拆分和认证迁移混在同一个提交。
- 前端过滤不替代后端授权；所有组织、人员和供应商选择在保存时继续由服务端校验。
- HttpOnly 迁移先双轨兼容，再灰度 Web；Native 单独验证，不强行同批切换。
- 现有 `/form-options`、Bearer Header 和旧客户端兼容能力只能在观察期结束后移除。
- 工作区当前存在其他未提交修改，执行前必须建立目标文件基线，不允许 reset、stash 或覆盖用户工作。

## 3. 推荐执行顺序

```text
基线与门禁
  ├─ 2.1 供应商数据范围修复
  ├─ 2.2 brace-expansion 安全热修
  └─ 2.3 HTTP 401 统一失效处理
          ↓
2.4 负责人分页搜索 API 与移动选择器
          ↓
2.5 首屏性能三批拆分
          ↓
3.0 HttpOnly 双轨、灰度与 Cookie-only 迁移
          ↓
联合回归、真机验收、发布
```

供应商、依赖热修相互独立，可并行开发；负责人选择器只涉及 HR 链路，可与 HttpOnly 后端协议设计并行。首屏拆分和 HttpOnly Web 切流都会修改 `main.js`、`request.js`、`user.js` 等入口，不应并行落地。

---

## 4. 专项一：供应商后端数据范围统一

### 4.1 现状

供应商列表链路：

```text
GET /inventory/supplier/list
→ InvSupplierServiceImpl.selectSupplierList()
→ appendRelatedShopScope()
→ selectRelatedDeptIds()
→ 当前组织 + 下级组织 + 祖先组织
```

手机采购选择器链路：

```text
GET /inventory/mobile/options/supplier
→ InvMobileServiceImpl.selectOptions()
→ scopedParams()
→ selectSubDeptIds()
→ 当前组织 + 下级组织
```

祖先公司维护的供应商因此能出现在供应商列表，却不能进入仓库采购选择器。

### 4.2 推荐改动

只修改：

- `erp-modules/erp-inventory/src/main/java/com/erp/inventory/service/impl/InvMobileServiceImpl.java`

方案：

1. 将 `productOptionScopedParams()` 泛化为 `relatedScopedParams()`。
2. `product` 和 `supplier` 共用 `resolveRelatedScopeDeptIds(selectedShopDeptId)`。
3. `customer` 继续使用当前及下级范围，不顺带扩大客户数据。
4. 保留 `checkOptionPermission()` 的 `inv:supplier:list` 精确权限校验。
5. 保留 Mapper 中启用、合作中筛选和空范围 `and 1=0`。
6. 采购保存继续执行 `assertRelatedShopVisible()`，前端选项不作为最终授权。

不修改接口结构、前端调用和 Mapper SQL。

### 4.3 测试

重点文件：

- `InvMobileServiceImplPermissionTest`
- `InvMobileMapperBindingTest`
- 建议新增供应商范围契约测试
- 前端 `mobileEntityService` 契约测试

必须覆盖：

- 仓库可见当前、下级和祖先公司供应商。
- 仓库不可见兄弟门店独占供应商。
- 停用、暂停合作供应商不进入采购选择器。
- 缺 `inv:supplier:list` 时 Mapper 不执行。
- 非法或未授权 `Dept-NumId` 保持拒绝。
- 空数据范围保持 fail-closed。

### 4.4 验收与回滚

- 同账号、同组织下比较 supplier list 与 mobile option ID 集。
- mobile option 应是列表中“启用且合作中”的子集。
- 无数据库迁移和缓存迁移。
- 回滚只恢复 supplier 分支原来的 `scopedParams()`。

---

## 5. 专项二：高危依赖修复

### 5.1 当前依赖链

```text
@vue/cli-service@4.4.6
→ copy-webpack-plugin@5.1.2
→ minimatch@3.1.5
→ brace-expansion@1.1.14
```

```text
@capacitor/cli@8.4.1
→ rimraf@6.1.3
→ glob@13.0.6
→ minimatch@10.2.5
→ brace-expansion@5.0.7
```

两条均属于开发/构建工具链，不进入浏览器生产运行时，但会影响 CI、构建和本地工具执行。

### 5.2 推荐方案

优先只刷新 lockfile：

```text
brace-expansion 1.1.14 → 1.1.16
brace-expansion 5.0.7  → 5.0.8
```

涉及：

- `erp-ui/package-lock.json`

如果 registry 或 lock 解析无法稳定得到两个安全版本，再修改：

- `erp-ui/package.json`

增加按父依赖或现有主版本限定的双 override。禁止使用全局单值把所有消费者强制到 5.x，因为 `minimatch@3` 依赖 1.x/CJS。

不把 Vue CLI 5 升级与本次安全热修绑定。Capacitor 8.4.2 对齐可作为独立维护批次，不作为修复必要条件。

### 5.3 验收

1. 干净目录运行 `npm ci`。
2. `npm ls brace-expansion --all` 只能解析到 `1.1.16` 和 `5.0.8`。
3. 使用官方 registry 执行依赖审计，确认对应两个 advisory 消失。
4. 为 minimatch 3/10 各做花括号、转义和 glob smoke test。
5. 全量执行 `npm test`、`npm run build:prod`。
6. 在一次性工作树验证 Capacitor CLI、`app:verify` 和 native sync。

现有 `audit:prod --omit=dev` 会漏掉这两条开发依赖链，应新增全依赖安全检查或精确依赖链断言。

### 5.4 回滚

只回滚 `package-lock.json` 以及可选的 `package.json` override；不提交 `dist` 或 native 生成物。

---

## 6. 专项三：入职负责人搜索选择器

### 6.1 现状

当前手机页调用：

```text
GET /system/hr/onboarding/form-options
→ 一次返回全部数据范围内启用用户
→ options.owners
→ MobileOnboardingStepForm 原生 select
```

现有查询只返回 `user_id` 和 `nick_name`，没有关键词、部门、分页、工号和脱敏手机号，无法解决同名识别和 160 人长列表问题。

### 6.2 后端 API

保留原 `/form-options`，新增：

```http
GET /system/hr/onboarding/owner-options
```

参数：

| 参数 | 规则 |
|---|---|
| `keyword` | 最长 64；匹配姓名、工号、手机号 |
| `deptId` | 必须处于当前数据范围 |
| `includeChildren` | 默认 true |
| `pageNum` | 从 1 开始 |
| `pageSize` | 默认 20，最大 50 |
| `userIds` | 用于已选及最近人员回显，最多 6 个 |

返回字段：

- `userId`
- `label`
- `deptId`
- `deptName`
- `deptPath`
- `employeeNo`
- `phoneMasked`

不得返回完整手机号、登录账号、角色明细等无关信息。

后端涉及：

- `HrOnboardingController.java`
- `IHrOnboardingService.java`
- `HrOnboardingServiceImpl.java`
- `HrOnboardingAccessService.java`
- `HrOnboardingMapper.java`
- `HrOnboardingMapper.xml`
- 新增负责人查询与返回 VO

安全要求：

- 沿用 `hr:onboarding:list/add/edit` OR 权限。
- 查询继续经过 `@DataScope`。
- 仅返回启用、未删除用户。
- `keyword`、`deptId`、`userIds` 使用绑定参数。
- 保存仍由 `validateTargets()` 校验负责人。

### 6.3 前端体验

建议新增：

- `MobileOnboardingOwnerPicker.vue`
- `mobileOnboardingOwnerPicker.js`

并修改：

- `src/api/hr/onboarding.js`
- `MobileOnboardingStepForm.vue`
- `mobileOnboardingForm.js`
- `onboarding/form.vue`

交互：

1. 负责人字段移动到“组织岗位”，位于目标组织之后。
2. 点击字段打开底部面板。
3. 默认按目标组织过滤，可切换“全部可管理组织”。
4. 支持姓名、工号、手机号搜索。
5. 每页 20 条，滚动加载并保留显式“加载更多/重试”。
6. 已选人员固定置顶，搜索或分页失败不能丢失。
7. 点击人员后需“确认选择”，关闭面板放弃未确认值。
8. 最近选择每账号、每组织最多 5 个，只保存 userId 和时间戳，30 天过期；打开时重新授权水合。
9. 编辑页已有人员停用或超范围时显示“当前保存值不可选”，未修改不能清空。

不直接复用当前库存域 `MobileEntityPicker.vue`，因为它耦合库存实体和客户快捷创建；可复用其请求所有权、搜索和状态处理思路。

### 6.4 状态与无障碍

必须独立区分：

- Loading
- 搜索无结果
- 当前组织无人员
- 403 无权限
- 网络失败
- 分页失败
- 保存值已不可选

面板使用 dialog + combobox/listbox/option 语义；支持方向键、Enter、Escape、焦点圈定和关闭后焦点返回。触控目标至少 44×44px，200% 文本缩放不得裁切。

### 6.5 验收

- 160 人以上不再产生原生长下拉。
- 首屏最多加载 20 条。
- 快速连续搜索只有最后一次请求能更新结果。
- 姓名、工号、手机号搜索正确，手机号只脱敏展示。
- 组织过滤结果 100% 位于授权范围。
- 编辑已有负责人回显与无变更保存不回归。
- 320/360/390px、软键盘、VoiceOver、TalkBack 和系统返回通过。

---

## 7. 专项四：移动首屏性能

### 7.1 基线

| 指标 | 当前 |
|---|---:|
| app entry raw | 1.492 MiB |
| 当前 raw 预算 | 1.500 MiB |
| 剩余余量 | 约 8 KiB |
| `chunk-elementUI` | 约 525 KiB raw / 132 KiB gzip |
| `chunk-libs` | 约 431 KiB raw / 147 KiB gzip |
| `app.js` | 约 348 KiB raw / 104 KiB gzip |
| `app.css` | 约 260 KiB raw / 40 KiB gzip |
| 共享冷启动含 HTML | 约 433 KiB gzip |

最大同步入口：

- `src/main.js`
- `src/plugins/element-ui.js`
- `src/plugins/index.js`
- `src/directive/index.js`
- `src/services/pushRegistration.js`
- `src/App.vue`
- `src/assets/icons/index.js`
- `src/store/index.js`
- 路由、权限与完整移动元数据

### 7.2 批次 0：固化测量

修改：

- `scripts/bundle-budget.cjs`
- `scripts/check-production-bundle.cjs`
- `test/productionBundleBudget.test.js`
- `test/productionAssetBudget.test.js`

新增 raw + gzip 冷路径预算，记录：

- entry assets
- 共享首启
- 指定移动路由总量
- JS/CSS 请求数

浏览器使用统一冷缓存、Fast 4G、4× CPU，分别测登录页、移动工作台和 `/mobile/customer`，每次构建跑 3 次取中位数。

### 7.3 批次 1：低风险延迟加载

目标：

- entry ≤ 1.30 MiB raw
- 共享冷启 ≤ 380 KiB gzip
- 任一路由分块不回退超过 5%

内容：

- Native push adapter 到实际需要时动态加载。
- download/file-saver 在下载动作触发时导入。
- clipboard 在指令实际使用时导入或局部注册。
- ThemePicker 只在桌面设置入口加载。

### 7.4 批次 2：Element UI 主拆分

目标：

- entry ≤ 1.05 MiB raw
- 共享冷启 ≤ 310 KiB gzip
- 关键移动 route ≤ 160 KiB raw

内容：

1. `element-ui.js` 拆成登录/移动核心与桌面/功能组。
2. 异步页面局部注册 Table、Tree、Upload、DatePicker 等重组件。
3. 修改 `vue.config.js` cacheGroups，避免 `chunks: all` 把异步 Element 模块重新合并成初始块。
4. 扩展 `elementUiOnDemand.test.js`，覆盖组件和 Message/MessageBox/Loading/Notification 服务。

### 7.5 批次 3：应用图谱拆分

最终目标：

- entry ≤ 0.90 MiB raw
- 共享冷启 ≤ 270 KiB gzip
- 代表移动冷路径 ≤ 330 KiB gzip
- JS/CSS 请求数 ≤ 8
- 测试档 LCP ≤ 2.5s、TBT ≤ 200ms

内容：

- 分离首启最小路由/权限记录与富业务元数据。
- SVG icon 拆成核心和桌面/功能组。
- 最后才评估动态注册路由专属 Store。

Element UI、路由图谱和 Store 动态化不得放在同一批。

### 7.6 回归与回滚

覆盖：

- 桌面登录、首页、重型表格/弹窗/上传/编辑器。
- 移动登录、工作台、客户、HR 入职。
- 未登录重定向、动态路由恢复、组织选择、退出。
- iOS/Android push 注册、通知跳转、resume、logout disable。
- 冷/暖缓存、异步块失败与恢复。

每批达标后才收紧预算。未达到目标或出现功能回归，只回退当前批次及对应预算调整。

---

## 8. 专项五：HttpOnly 会话迁移

### 8.1 当前问题

当前登录返回 `access_token`，前端写入脚本可读 `Admin-Token` Cookie，并为每个请求拼接 Bearer Header。网关只读取 Authorization。

直接替换为 Cookie 会引入：

- CSRF
- CORS credentials
- 上传/下载直连遗漏
- 401 双入口不一致
- 多标签页状态
- Native WebView 第三方 Cookie 兼容
- Header 与 Cookie 双凭证混淆

因此必须分阶段迁移。

### 8.2 目标协议

- Cookie：`ERP_SESSION`
- `HttpOnly`
- 生产 `Secure`
- `Path=/`
- 同站部署优先 `SameSite=Lax`
- 确需跨站时使用 `SameSite=None; Secure`，同时采用精确 Origin 白名单

CSRF：

- 非 HttpOnly `XSRF-TOKEN`
- 请求头 `X-XSRF-TOKEN`
- Token 与 Redis session UUID 绑定
- GET/HEAD/OPTIONS 豁免
- Cookie 认证的 POST/PUT/PATCH/DELETE 必须验证
- 迁移期纯 Bearer 请求可以豁免
- Header 与 Cookie 同时存在但不一致时直接 401，不能回退

网关读取 Header 或 Cookie，认证成功后向下游统一补标准 Authorization，从而保持现有微服务 `HeaderInterceptor` 不变。

### 8.3 Phase 0：兼容基础

先完成：

1. 统一 HTTP status 401 和 body code 401 的幂等会话失效处理。
2. 增加配置：
   - `auth.session.mode=bearer|dual|cookie`
   - Cookie 属性
   - CSRF 开关
   - 精确 allowed origins
3. 增加 header/cookie/both/mismatch、401、CSRF reject 指标。

主要前端文件：

- `src/utils/request.js`
- `src/store/modules/user.js`
- `src/permission.js`

### 8.4 Phase 1：后端 dual-read / dual-issue

- 登录继续返回旧 access_token，同时 Set-Cookie session 和 XSRF。
- refresh 延长 Redis TTL 并刷新 Cookie Max-Age。
- logout 删除 Redis并清除两个 Cookie。
- `AuthFilter`：Header 优先；无 Header 才读 Cookie；两者不一致直接拒绝。
- Cookie 认证后为下游补 Authorization。
- 增加 Gateway CSRF Filter。
- 删除无实现的 `/csrf` 白名单，新增受认证 `/auth/csrf` 恢复/轮换接口。
- 登录、注册、验证码、密码策略忽略陈旧 Cookie，避免坏 Cookie 阻断重新登录。

主要后端：

- `TokenController`
- `TokenService`
- `SecurityConstants` / `CacheConstants`
- `AuthFilter`
- 新增 CSRF Filter 与配置类
- `erp-gateway/bootstrap.yml` 及生产 Nacos CORS 配置

### 8.5 Phase 2：Web cookie-preferred 灰度

- Axios 使用 credentials。
- Cookie cohort 不再添加 Authorization。
- 组织与签约 Scope Header 不再依赖 `getToken()` 判断。
- 前端改为 `unknown/authenticated/anonymous` 会话状态；冷启动调用 `/auth/session` 或 `getInfo` 探测。
- 登录不落 JWT；logout 完成服务端清理后再清本地状态。
- 自动发送 X-XSRF-TOKEN。
- 多标签页用 BroadcastChannel 同步登录/退出，不传播 Token。
- FileUpload、ImageUpload、Editor、ExcelImport、download 全部改为 credentials。
- Web push 注册不得继续依赖 session JWT 作为退出补偿凭证。
- Native 暂时保持 Bearer。

灰度：1% → 10% → 50% → 100% Web，每档观察登录、刷新、退出、401/403、CSRF、上传下载和 mutation 成功率。

### 8.6 Phase 3：Web cookie-only

- Web 登录响应不再返回 access_token。
- 删除 `Admin-Token` 读写和 Web Bearer 拼接。
- Gateway dual-reader 至少保留“旧 session 最大 TTL 12 小时 + 一个发布观察窗”。
- 收紧生产 CORS，严禁 `credentials=true + *`。

### 8.7 Phase 4：Native 单独决策

验证 iOS/Android Capacitor：

- Cookie jar
- SameSite
- 重启持久化
- HTTPS 域名
- 跨 Origin
- 上传、推送与后台恢复

若不可靠，Native 保留受支持的 Bearer 客户端，或采用 BFF/native session exchange；机器 API 继续使用独立凭证。

### 8.8 安全测试

- Header-only、Cookie-only、同 Token 双凭证、双凭证不一致。
- 坏 Header 不得 fallback 到好 Cookie。
- Redis 会话失效后 401 并清 Cookie。
- Cookie mutation 缺失/错误 CSRF 为 403。
- Origin/Referer 不在白名单拒绝。
- CORS 允许 Origin + credentials；恶意 Origin 不返回 ACAO。
- 登录 → reload → getInfo → mutation → refresh → logout → reload。
- 多标签页、会话过期、并发 refresh/logout。
- 所有上传下载链路与 Native 回归。

### 8.9 回滚

必须先将 Web cohort 切回 Bearer，再停 cookie-primary/CSRF enforcement，最后才考虑关闭 Gateway Cookie 读取。不能先关闭 dual reader。

Cookie cohort 回滚可以要求重新登录；不能为了无感回滚重新把 JWT 持久化到 JS。

---

## 9. 联合发布门禁

每个批次都必须满足：

1. 目标模块单元/契约测试通过。
2. 前端全量测试通过。
3. 对应后端 Maven 模块测试通过。
4. `git diff --check` 通过。
5. 生产构建通过。
6. 无新增 P0/P1 安全问题。
7. 供应商与人员接口完成多角色越权测试。
8. 性能批次达到本批预算后才收紧门禁。
9. HttpOnly 每个灰度档指标稳定后才扩大。
10. 最终完成 iOS/Android 真机回归。

## 10. Agent 分配建议

| Agent | 任务 |
|---|---|
| Backend / Frontend Developer | 供应商范围、负责人 API、HttpOnly 后端与前端适配 |
| UI/UX Optimization | 负责人底部选择器、状态、无障碍与截图验收 |
| QA Testing | 数据越权、依赖链、回归矩阵、灰度验收 |
| Performance | 测量门禁、三批分包和性能对比 |
| Security | Cookie/CSRF/CORS/双凭证威胁模型与发布审核 |

## 11. 完成定义

- 采购表单与供应商列表在相同组织上下文下口径一致。
- `brace-expansion` 两条链均升级到安全版本。
- 负责人支持远程搜索、分页、组织过滤和可靠回显。
- 移动共享冷启动不超过 270 KiB gzip，entry 不超过 0.90 MiB raw。
- Web 不再向 JavaScript 暴露会话 Token。
- 认证、上传、下载、推送、组织上下文和 Native 均完成回归。
- 所有批次有可执行回滚路径和独立验收证据。
