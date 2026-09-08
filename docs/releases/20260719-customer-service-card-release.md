# 客户服务卡 20260719 代码专包发布契约

> 发布 ID：`customer-service-card-code-only-20260719`
> 部署模式：`code-only`
> 当前状态：候选；生产只读预检、门店验收和逐阶段批准完成前，不得宣称已上线。

## 不可突破的发布边界

本批次只交付客户服务卡安全修复。清单固定为 [customer-service-card-release-files-20260719.list](../../scripts/customer-service-card-release-files-20260719.list) 中的 32 个文件，其中 25 个是客户卡源码/测试，7 个是本次独立发布契约。候选提交相对基线的完整差异必须与该清单逐项相等，不能从当前脏工作区直接构建。

`erp-ui/src/views/mobile/feature/mobileFormPayloads.js` 只允许摘取 customer-card 保存时复用 `source.requestKey` 的 hunk，确保移动端重试不生成新幂等键。当前工作区同文件里既存的 `normalizeLineItems`/OE hunk 与客户卡无关，候选分支禁止带入；candidate 门禁会专门拒绝该 hunk。

客户卡四个写端点显式设置 `releaseOnSuccess=true`。Redis 键只承担请求执行期间的并发占位，成功提交后立即释放；注解默认值仍为 `false`，其他没有持久幂等账的接口不会改变原有行为。客户卡的持久幂等由必填且不超过 128 字符的 `requestKey`、数据库唯一约束和按 key 查询原结果共同保证，服务层不得静默生成新 key。即使 Redis 释放失败，TTL 也只是 in-flight 兜底，不能被描述为持久幂等依据。

移动端快速新建客户在打开表单时生成一次 `quickCustomerRequestKey`，提交失败后保留表单和该 key，重试必须复用；`createQuickCustomer` 内禁止再次调用 `Date.now()`。candidate 门禁同时验证实现和 `mobileQuickCustomer.test.js` 的回归覆盖。

移动工作台的“客户资料”入口只允许把权限从 `inv:customer:list` 精确替换为 `inv:customerCard:list`，并由 `customerServiceCardMobileFlow.test.js` 锁定。当前 `MobileWorkbenchShell.vue` 另有数百行 progressive redesign CSS 脏改，与客户卡上线无关；候选门禁要求该文件只有一个 hunk、恰好一删一增，CSS 一律拒绝带入。

共享幂等文件也必须按 hunk 隔离：`IdempotentSubmitAspect.java` 只摘取 `releaseOnSuccess/releaseQuietly`，对应测试只摘取成功释放测试和注解 helper 支持。当前工作区中的 `SignScopeHeaderUtils/isSigningRequest` 及三个签约 scope 测试属于其他发布，生产基线没有只读证据证明已包含，客户卡候选默认拒绝带入。若未来另有已批准发布先行上线，必须先补生产制品指纹证据并重新冻结本契约，不能临时放宽 candidate gate。

门店 allowlist 的 Java 逻辑和两份远端只读脚本必须使用同一严格语义：整值可 trim，按逗号分割时保留尾部空 token，每个 token 只 trim 两端，然后必须匹配 `[1-9][0-9]*` 且十进制值不大于 `9223372036854775807`。`*`、`01`、`1 2`、0/负数、`10,`、`10,,12` 和 Long 溢出均阻断整份列表；脚本不得用 `tr -d '[:space:]'` 吞掉内嵌空格，也不得用数值 sort 归一化前导零或溢出值。该规则同时覆盖参数值、`CUSTOMER_CARD_EXPECTED_SHOPS`、`CUSTOMER_CARD_PILOT_SHOP_ID` 和 `CUSTOMER_CARD_NONPILOT_SHOP_ID`。`blankWildcardAndMalformedAllowlistsDenyEveryShop` 及远端解析契约用例是发布门禁的一部分。

客户卡列表分页不得在 controller 或任何范围校验之前启动。controller 只把请求参数解析成 `CustomerCardPage`，不得 import/call `PageHelper`；service 的 `selectList` 必须先 `requireStoreContext` 并覆盖查询门店，再在 `selectCardList` 紧前调用本地 `startPage`。审计列表还必须在分页前完成可选 `customerId` 的 `assertScopedCard`，随后才在 `selectAuditList` 紧前启动分页。本地 helper 双重收口 `pageNum >= 1`、`1 <= pageSize <= 100`，防止 PageHelper ThreadLocal 在范围检查 mapper 上提前生效。`cardListStartsBoundedPaginationAfterStoreScopeChecks` 和审计测试必须分别证明范围 mapper 时 local page 为空、目标 mapper 时分页有效。

本批次 `migrationCount = 0`、功能开关变更数为 0、数据库写入数为 0。历史 18 迁移/10 开关整包不属于本次发布，禁止复用、裁剪或改名带入。生产所需的三张客户服务表、字段、唯一索引、权限对象和存量数据完整性都是只读前置条件；任何缺失都阻断发布，不能在代码部署中补 SQL。

发布压缩包只含从干净候选构建出的运行时代码、前端资源和本清单 JSON。压缩包不得包含：

- 任意 `.sql` 文件；
- 任意路径段为 `mysql` 的目录或文件；
- 任意历史 new-business release/manifest；
- `.env`、数据库数据、上传文件、日志、缓存或密钥。

## 本地冻结与代码专包构建

先在独立干净 worktree/候选分支只应用清单内改动，再执行：

```bash
bash scripts/verify-customer-service-card-release.sh --static
bash scripts/verify-customer-service-card-release.sh \
  --candidate "$BASE_COMMIT" "$CANDIDATE_COMMIT"
```

使用仓库固定的 Java 17、Node 22.23.1 和 npm 10.9.8 跑客户卡 Java/Node 测试及完整构建。候选 SHA 必须显式注入 Maven 和前端构建，任何 JAR 或 `release-info.json` 中的 commit 不一致都会阻断。严禁执行 `docker/copy.sh`，严禁复制既有 `docker/erp`、`docker/nginx`、其中的旧 JAR/dist，严禁复制任何 `docker/mysql` 或既存 release；只允许从干净候选本次产生的 `target` 和 `erp-ui/dist` 直接在 `mktemp` stage 逐项组装完整运行包：

```bash
export CUSTOMER_CARD_EXPECTED_COMMIT="$CANDIDATE_COMMIT"
[[ "$CUSTOMER_CARD_EXPECTED_COMMIT" =~ ^[0-9a-f]{40}$ ]]
[[ "$CUSTOMER_CARD_EXPECTED_COMMIT" == "$(git rev-parse "$CANDIDATE_COMMIT^{commit}")" ]]
BUILD_TIME="$(date -u +%Y-%m-%dT%H:%M:%SZ)"

./mvnw clean verify -Dbuild.commit="$CUSTOMER_CARD_EXPECTED_COMMIT"
(
  cd erp-ui
  npm ci
  VUE_APP_BUILD_COMMIT="$CUSTOMER_CARD_EXPECTED_COMMIT" \
    VUE_APP_BUILD_TIME="$BUILD_TIME" npm run build:prod
)

PACKAGE_STAGE="$(mktemp -d)"
trap 'rm -rf "$PACKAGE_STAGE"' EXIT
mkdir -p \
  "$PACKAGE_STAGE/docker/erp/auth/jar" \
  "$PACKAGE_STAGE/docker/erp/gateway/jar" \
  "$PACKAGE_STAGE/docker/erp/visual/monitor/jar" \
  "$PACKAGE_STAGE/docker/erp/modules/system/jar" \
  "$PACKAGE_STAGE/docker/erp/modules/file/jar" \
  "$PACKAGE_STAGE/docker/erp/modules/job/jar" \
  "$PACKAGE_STAGE/docker/erp/modules/oa/jar" \
  "$PACKAGE_STAGE/docker/erp/modules/inventory/jar" \
  "$PACKAGE_STAGE/docker/erp/modules/approval/jar" \
  "$PACKAGE_STAGE/docker/erp/modules/gen/jar" \
  "$PACKAGE_STAGE/docker/nginx/conf" \
  "$PACKAGE_STAGE/docker/nginx/html/dist" \
  "$PACKAGE_STAGE/docker/release"

cp erp-auth/target/erp-auth.jar "$PACKAGE_STAGE/docker/erp/auth/jar/erp-auth.jar"
cp erp-gateway/target/erp-gateway.jar "$PACKAGE_STAGE/docker/erp/gateway/jar/erp-gateway.jar"
cp erp-visual/erp-monitor/target/erp-visual-monitor.jar "$PACKAGE_STAGE/docker/erp/visual/monitor/jar/erp-visual-monitor.jar"
cp erp-modules/erp-system/target/erp-modules-system.jar "$PACKAGE_STAGE/docker/erp/modules/system/jar/erp-modules-system.jar"
cp erp-modules/erp-file/target/erp-modules-file.jar "$PACKAGE_STAGE/docker/erp/modules/file/jar/erp-modules-file.jar"
cp erp-modules/erp-job/target/erp-modules-job.jar "$PACKAGE_STAGE/docker/erp/modules/job/jar/erp-modules-job.jar"
cp erp-modules/erp-oa/target/erp-modules-oa.jar "$PACKAGE_STAGE/docker/erp/modules/oa/jar/erp-modules-oa.jar"
cp erp-modules/erp-inventory/target/erp-modules-inventory.jar "$PACKAGE_STAGE/docker/erp/modules/inventory/jar/erp-modules-inventory.jar"
cp erp-modules/erp-approval/target/erp-modules-approval.jar "$PACKAGE_STAGE/docker/erp/modules/approval/jar/erp-modules-approval.jar"
cp erp-modules/erp-gen/target/erp-modules-gen.jar "$PACKAGE_STAGE/docker/erp/modules/gen/jar/erp-modules-gen.jar"

cp docker/erp/auth/dockerfile "$PACKAGE_STAGE/docker/erp/auth/dockerfile"
cp docker/erp/gateway/dockerfile "$PACKAGE_STAGE/docker/erp/gateway/dockerfile"
cp docker/erp/visual/monitor/dockerfile "$PACKAGE_STAGE/docker/erp/visual/monitor/dockerfile"
cp docker/erp/modules/system/dockerfile "$PACKAGE_STAGE/docker/erp/modules/system/dockerfile"
cp docker/erp/modules/file/dockerfile "$PACKAGE_STAGE/docker/erp/modules/file/dockerfile"
cp docker/erp/modules/job/dockerfile "$PACKAGE_STAGE/docker/erp/modules/job/dockerfile"
cp docker/erp/modules/oa/dockerfile "$PACKAGE_STAGE/docker/erp/modules/oa/dockerfile"
cp docker/erp/modules/inventory/dockerfile "$PACKAGE_STAGE/docker/erp/modules/inventory/dockerfile"
cp docker/erp/modules/approval/dockerfile "$PACKAGE_STAGE/docker/erp/modules/approval/dockerfile"
cp docker/erp/modules/gen/dockerfile "$PACKAGE_STAGE/docker/erp/modules/gen/dockerfile"

cp docker/nginx/dockerfile "$PACKAGE_STAGE/docker/nginx/dockerfile"
cp docker/nginx/conf/nginx.conf "$PACKAGE_STAGE/docker/nginx/conf/nginx.conf"
cp docker/nginx/conf/nginx.host.conf "$PACKAGE_STAGE/docker/nginx/conf/nginx.host.conf"
cp -R erp-ui/dist/. "$PACKAGE_STAGE/docker/nginx/html/dist/"
cp docker/run-erp-service.sh "$PACKAGE_STAGE/docker/run-erp-service.sh"
cp scripts/customer-service-card-release-20260719.json \
  "$PACKAGE_STAGE/docker/release/customer-service-card-release-20260719.json"
tar -C "$PACKAGE_STAGE" -czf "$DEPLOY_PACKAGE" docker
CUSTOMER_CARD_EXPECTED_COMMIT="$CUSTOMER_CARD_EXPECTED_COMMIT" \
  bash scripts/verify-customer-service-card-release.sh --archive "$DEPLOY_PACKAGE"
```

归档门禁要求全部 10 个新鲜 JAR、10 个服务 dockerfile、Nginx dockerfile/两份 conf、前端 `index.html`/`release-info.json`、运行脚本和本客户卡 manifest 均存在且非空；逐个读取 JAR 内 `build.commit`，并和前端 commit 一起比对 `CUSTOMER_CARD_EXPECTED_COMMIT`。源码候选仍只能有精确 allowlist 内的变化，因此这些 JAR 不得夹带其他脏工作区代码。归档出现 SQL、mysql、history、`.env`、upload、log、cache、key/secret 或历史发布清单即失败。

## 生产只读预检

代码发布前保持客户服务卡总开关为显式关闭。通过 Cloud Assistant 运行只读脚本；它使用只读事务检查三表及字段/唯一索引、8 个权限对象、在职门店员工基础权限、两个系统参数、显式门店 allowlist、孤儿资料/记录/日志、缺失资料和跨店异常。`invalid_customer_store_scope` 通过 `LEFT JOIN sys_dept` 阻断客户 `shop_dept_id` 为 null/非正数、部门不存在、非 `STORE`、停用或已删除；服务记录与审计日志的跨店比较使用 MySQL null-safe `<=>` 的否定，显式捕获单边 null。任一计数非零立即停止。

```bash
python scripts/aliyun_ecs_deploy_helper.py run-shell \
  --region "$ALIYUN_REGION" \
  --instance-id "$ALIYUN_INSTANCE_ID" \
  --script-file scripts/remote_customer_service_card_preflight_20260719.sh \
  --name customer-card-readonly-preflight-disabled \
  --timeout 300
```

预检只读，不创建参数、不补权限、不修数据。系统参数页 `erp-ui/src/views/system/config/index.vue` 的 validator 会拒绝 null、空字符串和纯空白的 `configValue`，`SysConfigServiceImpl.requireConfiguredValue` 也在新增/修改入口拒绝空值；两个文件只作为生产基线证据，不纳入本次 32 文件清单。

若 allowlist key 缺失，必须按顺序先确认总开关为显式 `false`，再取得首个试点 active STORE ID 的审批，由有 `system:config:add` 权限的发布管理员通过系统参数 UI 直接把 key 创建为该单个有效 ID，复核成功的“参数管理”操作日志，然后才执行 `disabled` preflight。若总开关不是显式 `false`，或首个试点尚未审批，立即停止，不创建参数。`disabled` 阶段允许该非空 allowlist 存在并会验证它是 active STORE，写能力仍由显式关闭的总开关阻断。严禁提交空字符串、null 或任何占位非法值。

## 代码部署（开关仍关闭）

部署命令故意不传任何数据库迁移参数：

```bash
python scripts/aliyun_ecs_deploy_helper.py deploy-host \
  --region "$ALIYUN_REGION" \
  --instance-id "$ALIYUN_INSTANCE_ID" \
  --package "$DEPLOY_PACKAGE" \
  --cleanup-oss \
  --timeout 1800
```

部署完成后先再次执行 `disabled` 只读预检，再执行只读运行态后检：

```bash
python scripts/aliyun_ecs_deploy_helper.py run-shell \
  --region "$ALIYUN_REGION" \
  --instance-id "$ALIYUN_INSTANCE_ID" \
  --script-file scripts/remote_customer_service_card_postcheck_20260719.sh \
  --name customer-card-postcheck-disabled \
  --timeout 300
```

后检必须确认运行清单仍为 code-only、迁移数和开关变更数均为 0，inventory 服务为 `UP`，首页和验证码入口可达，总开关仍关闭。

## 审计式灰度：单店 → 双店 → 全量

所有参数变更只允许从“系统管理 → 参数设置”完成，禁止直连数据库或手改 Redis。发布管理员每次必须同时保留变更前后截图、审批单、操作人、时间、目标门店 ID，并在“系统管理 → 操作日志”确认标题“参数管理”、操作类型新增/修改、状态成功。请求体按平台策略不入日志，参数行的 `create_by/update_by` 与 `create_time/update_time` 必须和操作日志中的操作人、时间互相印证。

每个阶段变更后，先让该阶段所有试点账号的旧会话失效，再要求用户完全退出并重新登录。仅刷新页面不算重新登录；旧令牌、旧权限或旧门店上下文不得作为验收证据。不得直接删除 Redis 键绕过账号/会话管理审计。

1. **单店**：allowlist 只写一个已批准的 active STORE `dept_id`，再把总开关改为 `true`。会话失效并重新登录后，至少用门店员工和负责人各完成列表、详情、新建、编辑、追加记录、归档限制、审计查看、幂等重放和跨店拒绝；另用一个非 allowlist 门店证明 capability 为 `false`、客户卡保持只读。写请求返回 `FEATURE_DISABLED_FOR_SHOP` 必须在人工受控 UAT 中验证并对前后数据计数，不在只读后检脚本中发送 POST/PUT/DELETE/PATCH。
2. **双店**：单店观察期无异常并签认后，只把 allowlist 扩成两个明确门店 ID。第二店重复正向流程，两店互相执行跨店负向用例；第一店存量数据和审计计数不得漂移。
3. **全量**：双店观察期和对账通过后，allowlist 改为“当前全部 active STORE ID 的显式列表”。`sys_config.config_value` 及参数管理入口的值上限为 500 字符；扩量前必须按最终提交格式计算完整显式 ID 串长度并确认 `<= 500`。若超长，停止全量扩量；已审批的单店/双店阶段可继续运行，但必须另行设计、审批分组方案后才能再扩量。通配符 `*` 永远不属于本契约；新增门店以后必须重新审批并更新列表。

总开关变为 `true` 后，旧客户财务 API 对所有门店统一返回 `FEATURE_REPLACED`；allowlist 只控制新客户服务卡写能力，不是旧 UI/新 UI 双轨开关。试点店 capability 必须为 `true`；非 allowlist 店 capability 必须为 `false` 且客户卡保持只读。

每次参数变更后均先运行同阶段只读预检，再运行后检。后检只执行 SELECT 和 GET，不调用任何写方法；非试点写拒绝由人工受控 UAT 和本地代码测试共同验证。后检使用已经完成旧会话失效并重新登录的试点店、非试点店账号，以及一个专用于旧接口探针的 fresh admin/`*:*:*` 账号。普通门店账号可能在权限拦截层就收到明确“无权限”，这同样是旧财务接口的安全拒绝，但不能据此证明业务层执行了 `FEATURE_REPLACED`；因此自动探针只用专用 admin token 严格验证该标记。令牌放在远端 root-only、模式 `400` 或 `600` 的临时文件中；脚本不打印令牌或客户数据。`run-shell` 不透传本地环境变量或脚本位置参数，因此用标准输入给远端脚本增加受控环境前缀。单店示例（双店把阶段改为 `dual`，全量改为 `full`；全量无需非试点账号）：

```bash
# 远端预检对 EXPECTED_SHOPS 执行权威的正整数 Long 解析。
[[ -n "$APPROVED_STORE_IDS" ]]
{
  printf 'export CUSTOMER_CARD_PHASE=single\n'
  printf 'export CUSTOMER_CARD_EXPECTED_SHOPS=%q\n' "$APPROVED_STORE_IDS"
  cat scripts/remote_customer_service_card_preflight_20260719.sh
} | python scripts/aliyun_ecs_deploy_helper.py run-shell \
  --region "$ALIYUN_REGION" \
  --instance-id "$ALIYUN_INSTANCE_ID" \
  --script-file - \
  --name customer-card-readonly-preflight-single \
  --timeout 300
{
  printf 'export CUSTOMER_CARD_PHASE=single\n'
  printf 'export CUSTOMER_CARD_PILOT_SHOP_ID=%q\n' "$PILOT_STORE_ID"
  printf 'export CUSTOMER_CARD_PILOT_TOKEN_FILE=%q\n' "$PILOT_TOKEN_FILE"
  printf 'export CUSTOMER_CARD_LEGACY_ADMIN_TOKEN_FILE=%q\n' "$LEGACY_ADMIN_TOKEN_FILE"
  printf 'export CUSTOMER_CARD_NONPILOT_SHOP_ID=%q\n' "$NONPILOT_STORE_ID"
  printf 'export CUSTOMER_CARD_NONPILOT_TOKEN_FILE=%q\n' "$NONPILOT_TOKEN_FILE"
  cat scripts/remote_customer_service_card_postcheck_20260719.sh
} | python scripts/aliyun_ecs_deploy_helper.py run-shell \
  --region "$ALIYUN_REGION" \
  --instance-id "$ALIYUN_INSTANCE_ID" \
  --script-file - \
  --name customer-card-postcheck-single \
  --timeout 300
```

不得改写脚本 SQL。后检默认要求系统参数变更发生在最近 120 分钟并有同一操作人的成功操作日志，可用同样的受控前缀设置 `CUSTOMER_CARD_AUDIT_WINDOW_MINUTES`；脚本硬上限为 1440 分钟（24 小时），只允许在该上限内按审批缩短或调整，不得放宽到超过 24 小时。

## 停止与回滚条件

出现任一项立即停止扩量：

- 静态门禁、远端预检、运行态后检、服务健康或页面/API 冒烟失败；
- 三表/字段/索引/权限缺失，任意孤儿、缺失 profile、非法客户门店作用域或跨店记录；
- 非 allowlist 门店可以写入，任一门店可以读取/修改其他门店客户；
- 总开关开启后任一门店旧客户财务 API 未返回 `FEATURE_REPLACED`；
- 已归档客户仍可编辑、追加记录或再次归档；
- 同一 request key 产生重复服务记录/审计日志，或重试返回冲突/500；
- 参数变更没有成功操作日志，旧会话未失效，重新登录后权限/门店上下文错误；
- 5xx、登录失败、inventory 重启、慢查询或客户卡错误率相对基线出现持续异常。

回滚第一动作不是删数据，而是由发布管理员通过系统参数页面把总开关改回 `false`，立即复核成功操作日志，然后使已放量账号会话失效并要求重新登录。allowlist 保留最后审批的显式有效列表，不随回滚删除或改成空字符串/null，也不填任何占位非法值。确认写入口关闭后再执行代码回滚：将 `/opt/erp-new` 软链接切回发布前已核验目录，重启并等待 10 个服务全部 `UP`，重载 Nginx，再运行 `disabled` 预检和后检。

本批次没有 SQL，所以不执行数据库反向脚本。灰度期间产生的客户资料、服务记录和审计日志全部保留；禁止删除、截断或手工改写。若旧代码无法读取新增字段，保持开关关闭并前向修复。
