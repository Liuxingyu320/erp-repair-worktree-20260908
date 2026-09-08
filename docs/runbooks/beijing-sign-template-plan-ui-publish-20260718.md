# 北京区域 0716 签约模板/方案 UI 发布清单

## 本次发布口径

- 旧的 `20260714-v3-draft` 7/11/12 三文件套餐不满足新入职门禁，不得继续发布。
- 目标是已生成但尚未发布的 `20260718-v5-draft` 候选包。在 HR/法务逐份审核、上传并在系统中完整登记前，保持停用，不创建或发布方案。
- 模板 ID 由系统保存后生成；清单和只读审计均按“类型 + 版本 + 文件 SHA-256”识别，禁止猜测或手填 ID。
- 员工签名统一使用 `{"mode":"APPENDED_CONFIRMATION_PAGE"}`；需要企业章的文件也使用追加确认页。
- 员工选择与灰度仍按已审批的业务组织执行；签约方案本身属于 HR 全局方案库，`shopDeptId` 必须为保留值 `0`，不得按门店复制方案。
- 发布只能由已配置 HR 在登录后的网页中完成；禁止直接 SQL 写签约业务表，自动发布脚本继续禁用。

## 点击发布前必须满足

1. OA 运行 JAR 已上线，只读审计核对的关键 class SHA-256 与待发布包完全一致。
2. `oa_sign_task.open_onboard_employee_id` 生成列和 `uk_oa_sign_task_open_onboard_employee` 唯一索引完整，且不存在同一员工多个开放 ONBOARD 任务。
3. 下表 9 个 v5 文件全部完成 HR/法务审核、系统登记和物理文件哈希核对；缺任意一个立即停止。
4. `ONBOARD_COMMITMENT`、`ONBOARD_CONFIDENTIAL_NONCOMPETE`、`ONBOARD_MINOR_NONSTUDENT_DECLARATION` 的用工类型留空，保证劳动/劳务共用；保密竞业的岗位等级范围必须为 `7级及以上`。如当前 UI 不能完整维护这两项，不得以空值代替，继续阻断发布。
5. 段继康（`user_id=940`）有显式 `1157` 组织授权，已重新登录，并具有模板、方案和发起签约权限。
6. 工作簿中 B1、B3 两条劳务数据的劳务人员类型和保险类型已由 HR 确认，不得由员工填写、留空或猜测；对外只保留标准化类别代码、可重算规则 SHA-256、使用注册 key ID `onboard-hr-qa-workbook-hmac-20260718-v1` 的工作簿 `HMAC-SHA256` 和脱敏聚合根，不携带姓名或原始行字段。HMAC 的密钥由证据保管人持有，不得写入仓库或证据 JSON。

## 候选模板登记与复核

| 类型 | v5 文件 | SHA-256 | 用工/条件 | 企业章 |
|---|---|---|---|---|
| `ONBOARD_COMMITMENT` | `05_ONBOARD_COMMITMENT.docx` | `9cc4474fa78b6e9707cb87316d87023ab6b654b31e6488c4bbd857edd47b5816` | 劳动/劳务共用 | 不需要 |
| `ONBOARD_LABOR_CONTRACT` | `09_ONBOARD_LABOR_CONTRACT.docx` | `fdbe2df12eb3704370126e09f2c26fd7cd09e0a06db1e0fe960f42f9513f2118` | 劳动合同 | 需要 |
| `ONBOARD_HANDBOOK_RECEIPT` | `10_ONBOARD_HANDBOOK_RECEIPT.docx` | `65675b6182ee5c9cdd6650f94341cfccd4b23e69dfbde87c806cd41aee2f4b66` | 劳动合同 | 不需要 |
| `ONBOARD_SALARY_CONFIRM` | `11_ONBOARD_SALARY_CONFIRM_A.docx` | `c3341591b4806fcc5909315040a2e174da0eaa90245169cf74aba735d5ad8551` | 劳动合同/薪酬 A | 不需要 |
| `ONBOARD_SALARY_CONFIRM` | `12_ONBOARD_SALARY_CONFIRM_B.docx` | `e2ba862e63fc8e41485fc26348e354fc568126247f9676571690a030f3957c1c` | 劳动合同/薪酬 B | 不需要 |
| `ONBOARD_SERVICE_CONTRACT` | `13_ONBOARD_SERVICE_CONTRACT.docx` | `3794b2a1f54b734fd36acd49dfd6e7920892a290744de6fc34e19d9ef51eb5b4` | 劳务合同 | 需要 |
| `ONBOARD_SERVICE_RECEIPT` | `14_ONBOARD_SERVICE_RECEIPT.docx` | `4c72f4ec27b75aa070df051c736b316dae1c9752550ef46b9d670ede887c6a13` | 劳务合同 | 不需要 |
| `ONBOARD_CONFIDENTIAL_NONCOMPETE` | `15_ONBOARD_CONFIDENTIAL_NONCOMPETE.docx` | `0ab9894b08ad4b91ea1b5b1cd3b2c07332c5ef6483453af525069bee7999ba40` | 劳动/劳务共用，`7级及以上` | 需要 |
| `ONBOARD_MINOR_NONSTUDENT_DECLARATION` | `16_ONBOARD_MINOR_NONSTUDENT_DECLARATION.docx` | `264b642c03c9699ac234d1642e4da06d77f4c10bba5d6e2cffc5e65fca2031c9` | 劳动/劳务共用，系统仅对 `16<=年龄<18 + NON_STUDENT` 生效 | 不需要 |

所有文件均必须“员工可见 / 需阅读 / 需签署”，签名定位为追加确认页。先以停用状态保存并记录系统生成的 ID；只有九份都通过复核后才能逐份启用。

## 创建并发布 5 个全局生产方案

进入 **OA 协同 → 签约资料维护 → 签约方案**，直接点“新增方案”。界面必须显示“全部组织”；若要求为方案选择门店则立即停止。通用值：场景=入职，签署期限=7 天，截止提醒=关闭，公司=不绑定，状态=启用。

| 全局方案 | 规范路由 / 用工规则 | 模板类型（按此顺序） |
|---|---|---|
| `入职-A4-劳动合同-无社保-2至4级` | A4 / 劳动/无社保/`2-4`/薪酬 A | 承诺书 → 劳动合同 → 手册签收 → 薪酬 A → 未成年非在校声明（条件） |
| `入职-A5-劳动合同-无社保-5至6级` | A5 / 劳动/无社保/`5-6`/薪酬 A | 承诺书 → 劳动合同 → 手册签收 → 薪酬 A → 未成年非在校声明（条件） |
| `入职-A1-劳动合同-有社保-2至4级` | A1 / 劳动/有社保/`2-4`/薪酬 B | 承诺书 → 劳动合同 → 手册签收 → 薪酬 B → 未成年非在校声明（条件） |
| `入职-B1-在校实习生劳务合同-2至4级` | B1 / 劳务/无社保/`2-4`/HR 已确认劳务身份与保险 | 承诺书 → 劳务合同 → 劳务签收 → 未成年非在校声明（条件） |
| `入职-B3-退休返聘劳务合同-7至9级` | B3 / 劳务/无社保/`7-9`/HR 已确认劳务身份与保险 | 承诺书 → 劳务合同 → 劳务签收 → **7级及以上保密竞业** → 未成年非在校声明（条件） |

保存后先检查模板顺序和数量，再点“发布”。发布弹窗中的适用范围必须是全部组织，签署文件数和盖章文件数必须与上表一致，且不得出现红色阻断理由。任意劳动或劳务方案缺承诺书，或 B3 缺保密竞业，均不得点“确认发布”。

本批次生产路由数量必须固定为 `A4=19、A5=5、A1=1、B1=1、B3=1`。A3 仅用于独立 `isolated-uat` 环境的 7 级劳动案例，可使用 `隔离UAT-` 名称便于人工识别，但技术隔离必须依靠环境和路由校验，不得依赖名称前缀；不得在生产登记或发布。发布后只读审计必须返回 5 个活跃目标路由、0 路由冲突、0 非全局活跃方案、0 生产 A3、0 额外全局活跃路由和 0 无效/冲突规则。生产 27 人无条件基础文档数为 `107`；最终数量为 `107 + 16–18 周岁非在校员工实际命中数`，不得用“方案候选模板数 × 人数”计算。

## 发布后只读验证

由运维人员在 ECS 上执行：

```bash
B1_INSURANCE_TYPE_CODE=<COMMERCIAL_ACCIDENT或EMPLOYER_LIABILITY> \
B3_INSURANCE_TYPE_CODE=<COMMERCIAL_ACCIDENT或EMPLOYER_LIABILITY> \
bash /opt/erp-new/scripts/remote_audit_beijing_sign_publish_readonly_20260718.sh postcheck
```

脚本只执行 `SELECT`/文件哈希/一致性 `mysqldump`，不读取登录会话，不调用写 API，不写业务表，也不执行 SQL 迁移。它会动态发现系统生成的模板 ID，但只接受上表的 v5 类型、文件名、大小和 SHA-256；缺登记、重复登记、套餐不完整或版本快照不一致都会 fail closed。

只有退出码 0 且 `report.json` 中 `ready=true` 才通过技术校验。它不替代 HR/法务审批回执、段继康的页面发布回执和首个真实签约包的人工验收。
