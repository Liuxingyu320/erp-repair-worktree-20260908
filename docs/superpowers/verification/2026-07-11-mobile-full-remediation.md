# 手机端全量修复验证记录（2026-07-11）

## 自动化

| 验证 | 命令/范围 | 结果 |
| --- | --- | --- |
| 手机专项 | 11 个 mobile/contract/sign-package/fixed-asset/OE-gift/native 测试 | PASS 11/11 |
| 全部非副本测试 | `test/*.test.js`，排除文件名带空格的副本 | PASS 104 / FAIL 2（均为已知历史门禁） |
| 前端安全扫描 | `prebuild:prod` | PASS |
| 生产构建 | `npm run build:prod` | PASS |
| Capacitor 同步一致性 | `node scripts/verify-capacitor-sync.cjs` | PASS |
| Android Debug | JDK 21 `./gradlew assembleDebug` | BUILD SUCCESSFUL |
| iOS Simulator Debug | `xcodebuild ... iPhone 17 Pro ... build` | BUILD SUCCEEDED |

## 浏览器实测

| 场景 | 数量 | 390×844 | 320×568 | 底部重叠 |
| --- | ---: | --- | --- | --- |
| 门店上下文 | 40 路由 | 0 横向溢出 | 0 横向溢出、0 小触控项 | 0 |
| 仓库上下文 | 18 路由 | 0 横向溢出 | 0 横向溢出、0 小触控项 | 0 |
| 核心新增表单 | 3 | — | 销售/调拨/盘点最后字段可达 | 0 |
| 合同详情 | 1 | — | “打开”实测 44×44 | 0 |
| 个人资料 | 1 | — | 头像操作实测 76×44 | 0 |

## 修复回归断言

- 折叠“更多操作”关闭时内部 grid 为 `display:none`。
- 销售页 `.feature-stage` 滚到最大值后，业务交互控件与固定底栏交集为空。
- 头像、合同、签署包关键控件至少 44px。
- 固定资产报修使用 OE/故障/数量字段。
- OE/礼盒列表与详情 mapper 存在，并按门店/仓库隐藏供应商信息。
- Web 生产请求继续使用 `/prod-api`；原生生产请求只接受绝对 HTTPS Origin。
- Android/iOS 重复 `config*.xml` 会在同步后被清理。
- 前端源码不再包含私钥材料。

## 已知外部阻塞

正式原生业务请求尚不能连通：当前生产地址只有 HTTP，没有 HTTPS。代码门禁会返回 `NATIVE_API_ORIGIN_REQUIRED`，防止静默请求错误地址或放宽明文传输。Web 手机端不受影响。

---

## 现有页面渐进式大改增量验证（2026-07-11）

### 本轮范围

- 共享移动端令牌、安全区、动态可视视口和底部导航占位。
- 详情、表单、动作对话框、快速新建客户和库存/物料选择器的单一内部滚动体。
- 工作台、库存、通用业务页、个人中心、合同和签约包接入同一中性/翡翠绿视觉系统。
- 采购、盘点、普通调拨草稿补齐编辑、提交和真实删除；销售/采购编辑兼容后端 `unitPrice`、仓库和客户/供应商名称形状。
- 物料默认价格按业务区分：采购优先采购价，销售优先销售价，调拨优先参考成本，避免采购草稿误提交零售价。

### 验证边界

- 本轮不处理部署、Capacitor 同步和原生壳构建。
- 仓库存在 6 个 `test 2.js` 副本和 iOS `config 3.xml`，因属于用户现有工作树，本轮未删除。
- 本地独立浏览器会话没有用户手机上的登录态，正确跳转到登录页。未绕过认证、未伪造后端数据，因此没有生成本轮认证后截图。

### 最终证据

| 验证 | 最终结果 |
| --- | --- |
| 手机端及合同/签约相关测试 | PASS 43 / 43 |
| 全部非副本前端测试（不含原生部署门禁） | 109 项：PASS 107 / FAIL 2 |
| 已知失败 1 | `frontendTestGate.test.js`：现有 6 个 `test 2.js` 副本 |
| 已知失败 2 | `p0UxHardening.test.js`：旧的本地预览路由门禁断言与当前生产数据隔离断言冲突 |
| 前端密钥扫描 | PASS |
| `npm run build:prod` | PASS（仅保留既有 1.76 MiB entrypoint size warning） |
| 本轮文件 `git diff --check` | PASS |

既有原生壳验证记录保留为历史证据，不代表本轮重新执行。
