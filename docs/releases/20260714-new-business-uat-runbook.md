# 新增业务真实角色 UAT 执行手册

本手册对应发布 `new-business-20260714`。UAT 必须在可丢弃的独立数据库、两门店、两仓库和虚构业务数据上执行；源码契约测试、管理员账号冒烟或单门店正常流程都不能替代本手册。

## 1. 安全前提

- 准备普通员工 A/B、店长、直属主管、人事、仓库员工 A/B、仓库负责人和管理员九个独立账号。
- 九个账号必须使用不同的 Playwright `storageState` 文件和不同登录令牌；文件放在仓库外并执行 `chmod 600`。
- UAT 不使用真实客户照片、手机号、健康证附件或生产数据副本。
- Web/API 地址必须是 `qa`、`uat`、`test`、`stage` 或 `dev` 域名；非回环地址必须使用 HTTPS。
- 运行 ID、数据库名、组织 ID 和目标 URL 需要通过环境变量再次逐项确认。脚本拒绝 `erp`、`prod`、`production` 等数据库名。

## 2. 准备登录状态

以下示例使用项目已验证的 Playwright CLI。登录动作由验收人员在打开的浏览器中完成，脚本和配置文件均不保存账号密码。

```bash
export PATH=/opt/homebrew/Cellar/node@22/22.23.1/bin:$PATH
export PWCLI="$HOME/.codex/skills/playwright/scripts/playwright_cli.sh"
mkdir -m 700 /secure/erp-uat

"$PWCLI" -s=employee-a open https://erp-uat.example.test
# 在浏览器中完成人工登录后：
"$PWCLI" -s=employee-a state-save /secure/erp-uat/employee-store-a.json
chmod 600 /secure/erp-uat/employee-store-a.json
"$PWCLI" -s=employee-a close
```

其余八个角色重复上述操作。不要把状态文件复制到项目目录，也不要将其加入发布证据。

## 3. 配置两店两仓

复制示例并修改 URL、组织、门店/仓库 ID 和九个状态文件的绝对路径。`*.local.json` 已被 Git 忽略。

```bash
cp scripts/qa/new-business-uat.example.json scripts/qa/new-business-uat.local.json

export ERP_QA_RUN_ID=uat-20260714-001
export ERP_QA_DATABASE=erp_uat_new_business_20260714
export ERP_QA_ALLOWED_ORG_ID=900001
export ERP_UAT_APPROVE_BASE_URL=https://erp-uat.example.test
```

门店 A/B 和仓库 A/B 的四个部门 ID 必须互不相同。店长 A、普通员工 A 和仓库员工 A 都只能拥有各自测试范围，不能用管理员角色代替负向越权账号。

## 4. 执行 API 与浏览器验收

先运行只读 API 范围、字段和权限探针：

```bash
python3 scripts/qa/run_new_business_api_uat.py \
  --config scripts/qa/new-business-uat.local.json \
  --output output/uat/new-business-api.json
```

再运行真实路由、登录状态和门店上下文浏览器冒烟：

```bash
scripts/qa/run-new-business-browser-uat.sh \
  scripts/qa/new-business-uat.local.json
```

浏览器脚本会先抓取页面快照，再匹配页面文字并保存全页截图。截图和快照只允许包含虚构 UAT 数据，默认保存在已忽略的 `output/playwright/`。

需要写入或并发验证时，在本地配置的 `apiProbes` 中加入固定 UAT 夹具请求。每个写探针都要有独立幂等键；并发边界使用 `parallelBodies`、`minSuccess` 和 `maxSuccess`。写入模式需要双重确认：

```bash
export ERP_UAT_WRITE_APPROVAL="$ERP_QA_RUN_ID"
python3 scripts/qa/run_new_business_api_uat.py \
  --allow-write \
  --config scripts/qa/new-business-uat.local.json \
  --output output/uat/new-business-api-write.json
```

API 证据只记录角色别名、上下文别名、状态码、耗时和响应 SHA-256，不保存响应正文、令牌或业务明细。负向探针必须同时匹配明确错误码和授权错误文案，普通 404/500 不能被误判为越权验证成功。

## 5. 六类必须签字的业务链路

| 场景 | 正向验证 | 负向与一致性验证 |
|---|---|---|
| 健康证 | 本人草稿、提交、人事审核、续证、员工档案和团队到期日一致 | 员工不能操作他人；附件业务接口不能绕过；只能有一个当前已审核证件 |
| 我的团队 | 店长看到本店；直属主管看到直属员工；手机号完整 | 门店 A 账号访问门店 B 被拒绝；响应无工资、银行卡、证件号和住址 |
| OE 额度与发件箱 | 额度内产生维修单、额度事实和唯一发件箱；投递生成唯一库存需求 | 临界并发成功数符合额度；超额不写维修/补货；失败可重试且幂等 |
| OE 补货 | 仓库看到上报内容并生成调拨；实发/实收回写需求 | 仓库 A/B 互不覆盖；错误仓库、重复事件和重复调拨被拒绝 |
| 要货、返仓与差异 | 复用审批、发货、部分收货、差异处理和完成状态 | 方向不可绕过；短少、残损、拒收、待质检数量守恒；差异未结不能完成 |
| 客户服务卡 | 同店员工新增、编辑、追加记录并查看受控照片 | 另一门店的列表、搜索、详情、照片和全部写接口拒绝；财务字段不返回 |

每类场景至少保留一个通过的负向用例。真实 UAT 修复任何问题后，都要重新执行统一发布门禁，不能只重测单页。

## 6. 生成发布阻断证据

复制 `scripts/qa/new-business-uat-evidence.example.json` 到已忽略的本地文件，填入候选提交、两份证据文件 SHA、六类场景验收人与三方签字。示例中的全零哈希和提交不可通过校验。

```bash
export ERP_NEW_BUSINESS_UAT_EVIDENCE="$PWD/scripts/qa/new-business-uat-evidence.local.json"
python3 scripts/verify_new_business_uat_evidence.py \
  --evidence "$ERP_NEW_BUSINESS_UAT_EVIDENCE"
```

完整发布门禁会再次校验候选提交、证据哈希、两店两仓、六类负向用例和签字：

```bash
scripts/verify-new-business-release.sh --full
```

没有真实账号、测试库、API/浏览器产物或签字时，完整门禁必须失败。
