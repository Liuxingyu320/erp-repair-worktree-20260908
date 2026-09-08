# 手机端五项优化灰度发布与回退手册

日期：2026-07-28
发布标识：`mobile-frontend-optimization-20260728`
当前状态：`CONDITIONAL READY`
执行策略：人工审批、分包发布、逐级灰度、禁止自动生产发布

## 1. 使用范围

本手册覆盖五个按顺序发布的逻辑包：

1. A：供应商后端数据范围统一。
2. B：高危依赖链修复。
3. C：入职负责人搜索选择器。
4. D：移动首屏性能优化。
5. E：HttpOnly Cookie、CSRF 与统一 401 会话迁移。

显式源文件范围位于：

- `scripts/mobile-frontend-optimization-release-files-20260728.list`

机器可读清单位于：

- `scripts/mobile-frontend-optimization-release-20260728.json`

本手册不授权生产发布。生产执行还需要独立的变更审批、发布负责人、环境配置快照、不可变构建产物和 SHA-256。

## 2. 角色与停止权

一次变更窗口至少明确以下责任人，不允许同一人同时承担全部角色：

| 角色 | 责任 |
|---|---|
| 发布负责人 | 决定每一级 Go/No-Go，记录开始、结束和回退时间 |
| 后端负责人 | Inventory、System、Auth、Gateway 部署与健康检查 |
| 前端负责人 | 纯净安装、构建、静态资源发布、Web 会话开关 |
| QA 负责人 | 账号矩阵、业务流程、手机尺寸、Cookie/CSRF 验收 |
| 监控负责人 | 401/403/5xx、登录刷新、性能和 Native 指标 |
| 回退执行人 | 保管上一版本产物，独立执行或复核回退 |

任何负责人都拥有停止扩容权。触发硬阈值时不需要等待完整观察窗结束。

## 3. 发布前硬门禁

以下每项必须有证据，任一缺失即 `NO-GO`：

1. 代码来自干净、不可变、已评审提交；不得直接打包当前脏工作区。
2. 后端 JAR、前端静态包和上一版本回退包均记录 SHA-256。
3. 完整自动化、生产构建、包体预算和依赖审计通过。
4. 测试/生产入口使用 HTTPS，反向代理正确传递 Host、Origin 与 Cookie。
5. Auth、Gateway 最终生效配置快照已从配置中心导出，配置修订号完全一致。
6. `allowed-origins` 为非空 HTTPS 精确来源列表；不得使用 `*`、通配子域或动态反射。
7. 会话 Cookie 为 `ERP_SESSION; Secure; HttpOnly`，CSRF Cookie 为脚本可读的 `XSRF-TOKEN`。
8. Cookie Web 模式启用 CSRF 双提交和 Origin 校验。
9. 总部管理员、门店负责人、普通员工、无权限账号完成组织边界 UAT。
10. Native iOS/Android Bearer 登录、刷新、退出与关键 API 通过；Native 存在期间后端保持 `dual`。
11. 401/403/5xx、登录、刷新、白屏、前端错误、P95、Native 登录均有实时看板和告警。
12. 上一版本回退产物可用，DNS/CDN/缓存回退方式已演练。

使用下列无密钥配置门禁校验发布参数：

```sh
DEPLOY_ENV=staging \
RELEASE_PHASE=web-cookie-canary \
AUTH_SESSION_MODE=dual \
GATEWAY_SESSION_MODE=dual \
WEB_SESSION_MODE=cookie \
AUTH_CONFIG_REVISION=<approved-revision> \
GATEWAY_CONFIG_REVISION=<approved-revision> \
COOKIE_NAME=ERP_SESSION \
CSRF_COOKIE_NAME=XSRF-TOKEN \
COOKIE_SECURE=true \
COOKIE_HTTP_ONLY=true \
CSRF_COOKIE_HTTP_ONLY=false \
COOKIE_SAME_SITE=Lax \
CSRF_ENABLED=true \
REQUIRE_ORIGIN=true \
PUBLIC_ORIGIN=https://<exact-host> \
ALLOWED_ORIGINS=https://<exact-host> \
NATIVE_BEARER_ENABLED=true \
EFFECTIVE_CONFIG_SNAPSHOT_RECORDED=true \
MULTI_ACCOUNT_UAT_PASSED=true \
ROLLBACK_ARTIFACT_READY=true \
MONITORING_READY=true \
sh scripts/verify-mobile-session-canary-config.sh
```

不得把 Token、Cookie、数据库密码、私钥或配置中心凭据放入命令、日志或证据文件。

## 4. 统一基线

发布前保存至少 30 分钟正常流量基线：

- 登录成功率与刷新成功率。
- Gateway 401、403、5xx 比例。
- CSRF 拒绝次数与写请求总数。
- Web 白屏率、资源加载失败率和前端未捕获错误率。
- API P50/P95/P99。
- 首屏资源体积、请求数、LCP/INP/CLS。
- Native 登录成功率和关键接口成功率。
- 供应商列表、采购供应商选项和负责人查询的成功率与 P95。

同一份看板贯穿发布和回退，禁止在变更期间更换统计口径。

## 5. 分包发布步骤

每个包完成后独立观察、验收和签字。前一包未通过时不得继续。

### 5.1 A：supplier-scope

1. 只部署 Inventory 对应变更。
2. 用总部、仓库、兄弟门店账号只读比较供应商列表与采购选择器 ID 集。
3. 验证当前、下级、祖先公司供应商可见；兄弟组织独占供应商不可见。
4. 验证停用、暂停合作、无权限和空范围均保持拒绝或空结果。
5. 观察 15 分钟，无异常后签字。

回退触发：跨组织越权、采购选项缺失、Inventory 5xx 或查询 P95 明显恶化。

### 5.2 B：dependency-security

1. 从干净目录执行依赖安装，禁止复用未知 `node_modules`。
2. 验证 `brace-expansion`、`minimatch` 解析树和安全审计。
3. 执行完整前端测试、生产构建、Capacitor 验证和包体预算。
4. 只发布由该依赖树生成且已记录 SHA-256 的前端产物。
5. 观察静态资源加载与前端错误 15 分钟。

回退时 `package.json`、lock、兼容补丁和专项测试必须原子回退，禁止只恢复 lock。

### 5.3 C：onboarding-owner-selector

1. 先部署 System 后端，验证旧 `/form-options` 仍兼容。
2. 验证新 `owner-options` 的权限、分页、关键词、部门范围、脱敏与最大页大小。
3. 再发布前端负责人选择器。
4. 在 320 px、390 px 上验证搜索、分页、空态、失败重试、取消、返回和已选回显。
5. 使用同名人员、无手机号、跨组织、无权限账号完成 UAT。
6. 观察 30 分钟。

回退必须先回前端入口，再回后端 API；保存端负责人权限校验不得放宽。

### 5.4 D：first-screen-performance

1. 发布前重新执行生产包体预算。
2. 发布前端产物，确认静态资源采用不可变文件名并正确缓存。
3. 验证 9 个关键移动路由、推送注册、下载、设置和剪贴板按需加载。
4. 检查冷启动请求数、gzip、路由异步块和资源加载失败。
5. 观察 30 分钟。

回退只撤销懒加载、推送拆分和预算对应变更，不得覆盖共享请求安全逻辑。

### 5.5 E-1：后端 dual，Web 仍为 bearer

1. Auth 与 Gateway 使用同一配置修订号、同一变更窗口切到 `dual`。
2. Web 保持 `bearer`，Native 保持 Bearer。
3. 验证登录、刷新、注销、无效令牌、双凭证冲突、CSRF 和 Origin。
4. 验证 Cookie 签发不影响 Bearer 客户端。
5. 观察至少 30 分钟。

禁止出现 Auth 已切换而 Gateway 未切换，或 Gateway 已切换而 Auth 未切换的持续状态。

### 5.6 E-2：Web Cookie 灰度

按以下比例逐级扩容，每一级都重新执行配置校验和业务验收：

| 级别 | Web Cookie 流量 | 最短观察窗 |
|---|---:|---:|
| C1 | 5% | 30 分钟 |
| C2 | 25% | 60 分钟 |
| C3 | 50% | 120 分钟 |
| C4 | 100% | 24 小时重点观察 |

流量分组应稳定绑定到用户或设备，避免同一会话在 Bearer/Cookie 构建之间来回切换。

每一级必须复核：

- 登录 → 首页 → 业务查询 → 提交 → 查看结果 → 退出。
- Cookie GET 不发送 Authorization 仍成功。
- 写请求正确双提交，缺失/错误 CSRF 返回 403。
- 刷新轮换、并发 401 单航班和跨标签退出。
- Cookie/Authorization 冲突返回 401 并清理异常 Cookie。
- 桌面 Web、移动 Web、320/390 px 和 Native App。

后端在 C4 以后仍保持 `dual`，除非 Native Bearer 已正式下线并完成独立迁移。

## 6. 灰度硬停止阈值

以下为默认硬停止阈值；只能在变更审批前根据稳定基线收紧或明确调整，不能在事故中临时放宽：

| 指标 | 停止/回退条件 |
|---|---|
| Gateway 5xx | 连续 5 分钟 > 1%，或较基线上升 0.5 个百分点 |
| 登录成功率 | 连续 5 分钟 < 98%，或较基线下降 2 个百分点 |
| 刷新失败率 | 连续 5 分钟 > 1% |
| 非预期 401 | 连续 5 分钟 > 基线 1.5 倍且绝对值 > 1% |
| CSRF 403 | 写请求占比 > 0.5%，或较基线 > 2 倍 |
| Web 白屏/致命错误 | 会话占比 > 0.5% |
| 静态资源加载失败 | 请求占比 > 0.5% |
| API P95 | 连续 10 分钟较基线恶化 > 30% |
| Native 登录成功率 | 较基线下降 > 1 个百分点 |
| 数据边界 | 出现任意一例跨组织越权，立即回退 |
| 数据正确性 | 出现任意不可逆错误写入，立即停止并升级事故 |

触发时先冻结扩容，再按第 7 节回退；不要等待多个阈值同时触发。

## 7. 回退总流程

### 7.1 会话迁移紧急回退

1. 冻结 Web Cookie 灰度扩容并记录触发时间、指标和受影响比例。
2. 保持或恢复 Auth、Gateway 为 `dual`；不要先关闭 Bearer。
3. 将 Web 流量或静态产物切回上一版本 Bearer。
4. 使新 Cookie 失效或由应用安全清理，禁止在日志中打印 Cookie 内容。
5. 验证 Web 与 Native 登录、刷新、注销、401 和业务写请求。
6. 指标恢复并稳定 30 分钟后结束紧急状态。
7. 保留日志、配置快照、版本 SHA 和时间线，进入根因分析。

### 7.2 五包逆序回退

如果问题不局限于会话层，按 `E → D → C → B → A` 判断并回退：

- E：后端保持 dual，Web 先回 Bearer；协议代码整体回退。
- D：恢复上一前端产物；仅回退性能 hunk。
- C：先恢复旧前端选择器，再回退 owner-options 后端。
- B：依赖元数据、lock、补丁、测试原子恢复并重新构建。
- A：只恢复供应商 related-scope hunk，不触碰其他 Inventory 改动。

共享文件只能按发布包 hunk 处理，禁止整文件覆盖：

- `erp-ui/src/utils/request.js`
- `erp-ui/src/store/modules/user.js`
- `erp-ui/src/services/pushRegistration.js`
- `erp-ui/src/plugins/download.js`
- `erp-ui/package.json`
- `erp-ui/package-lock.json`
- `erp-ui/.env.*`
- Onboarding Service、Mapper、XML 与表单文件

## 8. 发布完成证据

每一级至少保存：

- 发布标识、提交、构建时间、JDK/Node/npm/Maven 版本。
- 每个不可变产物 SHA-256。
- Auth/Gateway 配置修订号与脱敏后的最终生效配置快照。
- Origin、Cookie 属性、CSRF、Bearer/Native 验收结果。
- 测试账号角色与组织矩阵，不记录密码或 Token。
- 各灰度级别开始/结束时间、流量比例、看板截图和 Go/No-Go 签字。
- 回退产物 SHA、回退演练结果与执行人。

建议记录格式：

| 字段 | 值 |
|---|---|
| releaseId | `mobile-frontend-optimization-20260728` |
| sourceCommit |  |
| backendArtifactSha256 |  |
| frontendArtifactSha256 |  |
| previousArtifactSha256 |  |
| configRevision |  |
| publicOrigin |  |
| canaryLevel |  |
| startedAt/endedAt |  |
| result | GO / NO-GO / ROLLED-BACK |
| releaseOwner / QA / monitor / rollback |  |

## 9. 当前包的明确限制

- 当前仓库工作区包含大量既有未提交改动，不能直接打包。
- 当前清单没有生产候选产物 SHA-256。
- 未取得测试/生产 HTTPS 域名与配置中心最终生效快照。
- 未完成真实多账号、跨组织与 Native 真机环境矩阵。
- 因此当前包只达到“源范围和操作方案可验证”，未达到“可直接生产执行”。
