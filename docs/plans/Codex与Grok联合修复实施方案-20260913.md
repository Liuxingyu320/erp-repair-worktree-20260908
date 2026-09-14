# Codex与Grok联合修复实施方案

日期：2026-09-13。**双方修复设计已收敛。本文件是待实施方案，不是修复完成、部署或生产验收报告。**

方案覆盖57个既有问题项，分成8个责任包：55项业务/操作问题、1项部署条件风险（P06）、1项验证门禁（ROOT-05）；11项P1、45项P2、1项P3。每项只归属一个责任包，共用根因可以合修，但保留各自入口验收。旧48台账、旧16中较新已有处理的11项、旧HR-03和D01—D23不叠加计数。[原联合审查报告](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/修复工作副本_2026-09-08/docs/reports/Codex与Grok联合复核最终报告-20260913.md>)、[完整机器方案](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/修复工作副本_2026-09-08/output/grok-repair-plan-20260913/final-plan.json>)。

[最终轮方案讨论](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/修复工作副本_2026-09-08/output/grok-repair-plan-20260913/design-round4-public-response.md>)；[定稿依据](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/修复工作副本_2026-09-08/output/grok-repair-plan-20260913/plan-finalization.json>)。

## 实施基线和先后顺序

主候选采用较新工作树的实际内容，先建立可恢复快照和隔离实施候选，不直接在两个已有脏树上覆盖。两树HEAD相同不表示代码相同；裸checkout不包含较新既有48项修复。当前独有有效修改要按函数/区块审核带入，差异不自动等于应保留，也不能把较新文件整份覆盖过去。[双树路径与指纹清单](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/修复工作副本_2026-09-08/output/grok-repair-plan-20260913/baseline-difference-manifest.json>)。

已登记受保护生产脏文件：当前60、较新302，当前有32个与较新不同；包含测试/文档的全部git脏文件为120/651，当前52个与较新不同。清单不是全目录备份，忽略产物不在其中；语义取舍尚属B00工作。业务数据库、上传文件和运行配置另行盘点，不能把源码快照当数据备份。

责任包编号不是八阶段串行。B00隔离候选和可信定向回归就绪后，B01会话/权限、B02库存、B03中的ROOT-01/A06、B05中的HR-N08按文件边界并行优先；其余低影响页面不阻塞P1。ROOT-05未解决会阻塞“全量完成/可发布”的声明，不阻塞无关局部修复；Docker缺失不应挡住纯前端错误修正。

| 责任包 | 目标 | 项数 | 前置 | 唯一归属编号 |
|---|---|---:|---|---|
| B00 实施基线与可信验证 | 冻结两脏树的实际内容；较新修复快照为主候选，对照导入当前独有有效变更；修测试契约/夹具/集成入口，保留旧48与D01-D23。 | 1 | 无 | ROOT-05 |
| B01 会话、权限和幂等保护 | P01/P02优先；P06运行边界只读核实可与B00并行。鉴权共享库由同一维护者统一处理，避免混跑旧写入语义。 | 5 | B00 | P01, P02, P05, P06, PLAT-UI02 |
| B02 库存与销售主链路 | 仓库选择与多仓发货合做；单据状态、业务锁、物料引用和补发预留共同保持数量守恒，真实MySQL并发是发布前必需。 | 6 | B00 | INV-N01, INV-N02, INV-N03, INV-N04, INV-N05, INV-N09 |
| B03 切单、切人和迟到响应 | 先做最小共用操作上下文，再按页面接入；先ROOT-01/A06高影响入口，列表、详情、提交分别管理代次，不能一个全局loading阻止新目标。 | 14 | B00 | ROOT-01, ROOT-06, A06, A14, HR-N03, HR-N12, HR-N14, HR-UI01, HR-UI02, S01, S02, S04, PLAT-UI01, MOB-N01 |
| B04 失败反馈与原操作恢复 | 共用结果分类/提示约定，业务查询与持久命令各自实现。云盘新增持久操作，报销/补卡/审批优先现有请求身份恢复；旧返回不关闭新页。 | 7 | B00, B03 | A11, A12, A13, GROK-01, HR-N05, P03, ROOT-02 |
| B05 人事、入职与签约状态 | HR-N08与B01/B02的P1并行优先；未来健康证、旧合同与工资历史保留状态规则。低影响展示不阻塞高影响修复。 | 9 | B00 | HR-N01, HR-N02, HR-N04, HR-N07, HR-N08, HR-N09, HR-N10, HR-N11, HR-N13 |
| B06 库存查询与手机入口 | 身份字段贯通、历史分页、按物料类型筛选、下拉加载和真实统计来源；批量/搜索保留用户已选范围。 | 7 | B00, B03 | INV-N06, INV-N07, INV-N08, INV-N10, INV-UI01, MOB-N02, MOB-N03 |
| B07 系统配置与批量一致性 | 字典缓存/删除、工资导出筛选、cron解析、Quartz可靠同步和版本检查契约；涉及后端事实就不只改提示。 | 8 | B00, B03 | ROOT-03, ROOT-04, ROOT-07, ROOT-08, ROOT-09, P04, P07, S03 |

共用request.js、审批API、操作上下文工具和SQL清单由一个集成负责人串行合入；同页读/写保护不得由不同包互相覆盖。先落最小工具约定，再让页面按已有数据结构适配，不做一次全局请求层替换。机器方案列出跨包共用文件；B00—B07是唯一责任包，WF01—WF06、I-/M-/HR-开头只是局部机制标签，不另计责任包。

## 先把5条常用操作变简单

1. **销售选货时确定仓库，多仓发货按仓继续。** 当前组织本身是合法候选仓时可预填，否则仅候选唯一时自动带出；其他多候选明确选择。保留一张通知含多仓明细、每单一个活动通知，页面按仓分组单仓确认，不强改一仓一通知。历史已提交空仓记录先盘点，不擅自猜仓回填。
2. **导出失败直接重下原批次。** 保留原批次身份，提供本人有权限的历史和下载状态，避免重新筛选、重新建包。生成成功与文件下载完成分开显示。
3. **请求结果不明先核对，失败保留原操作。** 报销、补卡、云盘和审批保留稳定请求身份；显示已完成、明确失败或待核对，不让用户猜测重填。别人处理了任务不等于本次请求成功。
4. **入职Excel直接支持多行填写。** 手机号、证件号、银行卡和紧急电话整列文本；错误定位行列，保留有效行和预览。Excel入口继续启用，其他已有合法入口不被取消。
5. **切单不串资料，处理完可查看下一条待办。** 返回时保留筛选/位置，下一条只做显式查看导航，不自动提交审批；采购退货改原单后旧回包不再换回旧资料。

## 讨论结果与关键取舍

| 主题 | 采用的设计方向与仍要核实的内容 |
|---|---|
| 会话续期 | v1选定单会话原始Redis字节快照CAS；新UUID登录创建另走创建，旧可解析会话直接兼容，不新增全用户世代或强制所有人重登。索引仅辅助，缺成员不能报告批量撤销成功；受限扫描/权威集合核对、上限失败与retainedToken共同验收。全部公共库消费者协调升级，不与旧无条件SET混跑宣称闭环。 |
| 幂等短锁 | owner标识+原子比较释放，保留原成功/失败释放语义。TTL锁不等于持久业务幂等，也不保证所有业务exactly-once。 |
| 云盘未知上传 | 现容量reservation成功后会删除，不能作完成回执。新增薄持久operationId与节点/配额成功同事务保存：SUCCEEDED直接取原node不重传；PROCESSING只核对；一次NOT_OBSERVED不能证明未执行；明确安全失败且旧owner终止才可原ID/原载荷重新申请执行权。 |
| 审批错误与结果 | A11和GROK-01合修契约、分采购/报销PC/H5验收。保留silentError与已通知状态，401/凭据/CSRF/取消分流；按原requestId+任务+操作者+动作核对本次结果，单看任务非PENDING不能报本次成功。 |
| Quartz删除 | 先核实际JobStore与事务管理器：若真实集成证明可同事务则使用；否则采用持久删除意图/可重放同步及有效状态执行守卫。预检或一次afterCommit不足以覆盖崩溃窗口。 |
| 多仓配送 | 保留现有一单一个活动通知、通知含行级多仓快照；前端按仓组织输入并调用现有单仓发货接口。A仓成功/B仓失败分别显示，不擅改成跨仓原子请求或一仓一通知。 |
| 健康证当前项 | 先排除未生效/过期/未批准项；有效窗口内恰有一个current_flag=Y则保留，无此唯一有效当前才按validFrom→reviewedTime→ID降序择优。新证次日成为候选，不等于必定替换仍有效旧证；多标志/非法日期只读盘点。这是本方案保守建议，不冒称早前已确认规则。 |
| 手机工作台口径 | 今日销售仅店铺工作台统计；复用报表count(distinct order_id)、submitted/noticed/delivered状态且至少一条参与筛选的明细delivered_quantity>0、coalesce(order_date,date(create_time))业务日期及严格当前组织范围；本工作台无物料过滤，原报表可选物料/分类条件保留。新增调用显式保留inv:report:list权限，只返回单数，不透传成本/利润。今日销售保持只读指标，不承诺普通销售列表下钻；盘点/退货点击同组织同类型待办。 |
| 旧方案重新发布 | 现有发布窗口一次预览确认，明确本次恢复Vn，后台同plan锁只启指定版本并停其余；不改旧快照、不自动启用所有旧版。预览/确认令牌与回执为拟新增契约，内部文档hash不进入业务UI。 |
| 审批配置检查 | 使用已有具体规则版本检查契约，versionId确定后检查；列表筛选按templateId/runStatus。不能把当前scopeMode表单冒充全组织扫描。 |
| 电脑签约消息 | 先保留packageId提供手机接续/二维码入口；不因入口不便就新开放电脑签署或拆掉现移动守卫。 |
| 环境事实与产品决定 | monitor监听/凭据、Redis/Quartz配置先只读核实；默认分页20、已有统计口径优先复用是常规实现。只有确实改变业务范围/历史事实且无已确认规则时，形成具体可审方案后单独定案，不照搬首轮六个“必须问用户”。 |

历史submitted受控补仓的入口设计已纳入本方案；待形成具体历史单据清单、合法仓库映射和真实执行范围后再确认数据操作，不在未来入口实现前增加一轮确认，也不阻塞新单仓库修复。本次仍仅编制方案。

第一轮提出的云盘reservation复用、令牌世代、Quartz预检、多仓拆通知等建议，经Codex按现有源码反问后缩小或替换；第二轮已讨论关键方向，主任务又明确原始字节CAS、上传成功不重传和健康证保守选择。最后一轮已对完整57项与收敛记录核对，结果以定稿依据为准。[Grok首轮方案](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/修复工作副本_2026-09-08/output/grok-repair-plan-20260913/design-round1-public-response.md>)、[Codex逐点回应](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/修复工作副本_2026-09-08/output/grok-repair-plan-20260913/codex-design-response.md>)、[已整合的显式取舍](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/修复工作副本_2026-09-08/output/grok-repair-plan-20260913/plan-resolutions.json>)、[第三轮最后六点](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/修复工作副本_2026-09-08/output/grok-repair-plan-20260913/design-round3-request.md>)。

最后材料澄清（第四轮）：Grok明确四项均可定案，并撤回ROOT-06“方案漏范围”的判断；没有其他阻断设计分歧。以下为保留的设计与范围，共识不等于实施、上线或穷尽无遗漏。

- ROOT-06的完整JSON原有system/salary/index.vue与方案A/B档位乱序验收；第三轮压缩材料只展示files[:3]导致误解，保留该入口和验收，不新增问题、不缩范围。
- INV-N01分开核心新单修复、受控历史入口的隔离并发测试、具体历史清单的真实执行验收；入口设计已纳入，真实单据/仓库映射/执行清单另确认，不在可逆入口实现前再问一次。
- P04先只读核实实际JobStore/事务边界；可真实证明同事务原子提交才走本地路径，否则走可靠意图/同步及执行守卫，预检不能独自关闭问题。
- MOB-N02今日销售仅服务端确认的STORE工作台计算；WAREHOUSE不调用销售聚合、不返回销售业务值、不新增销售指标；完整报表口径与权限保持。

[第四轮四点澄清](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/修复工作副本_2026-09-08/output/grok-repair-plan-20260913/design-round4-request.md>)。

本次由Codex与Grok完成四轮方案讨论：前两轮比较取舍，第三轮阅读完整57项方案并定向核对源码，第四轮只核上述四点收口。第三轮一次网络失败没有审查结果，不计完成轮次。Grok没有独立重跑本轮全部验收，也没有在方案讨论中再次逐一扫描所有生产源码。[第四轮完整答复](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/修复工作副本_2026-09-08/output/grok-repair-plan-20260913/design-round4-public-response.md>)、[四轮过程清单](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/修复工作副本_2026-09-08/output/grok-repair-plan-20260913/discussion-manifest.json>)。

Grok最后指出平台模块稿P04的步骤顺序需与总稿一致，现已将P01/P03/P04和模块状态对齐既已接受的取舍，没有新增设计选择；对齐前平台稿另存，其他原审查材料保持不变。[对齐前平台提案](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/修复工作副本_2026-09-08/output/grok-repair-plan-20260913/platform-design-before-final-alignment.json>)、[已对齐平台模块稿](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/修复工作副本_2026-09-08/output/grok-repair-plan-20260913/platform-design.json>)。

## 已确认规则与历史数据

以较新实际[使用流程确认清单](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/Grok实施工作副本_20260912/docs/使用流程确认清单-20260912.md>)为规则来源。保留选中项退货/质检、分批到货与未检不可用、采购勾选跨页保留、盘点草稿与失效复核、上传分别重试、未保存离页保护、消息返回位置、普通调岗不发薪酬结构确认书、转正/续签入口、批量排班、统一请假与按HR配置自动计算额度、历史补签保留当时工资。普通调岗不要求重填或额外提示“工资不变”；只有实际调薪按权限走变更。

历史工资、合同终态、空仓销售单、健康证当前标志和文件归属都不能靠新算法自动改写。先只读盘点与异常候选→核对原始来源/引用/事件→形成受控修复清单、前后快照与回退→按另行确认的范围执行。不存在原始依据时显示待核对，不猜号码、工资、仓库或合同终态。

## 57项逐项实施卡

下列设计、验收和回退均为待执行要求。文件链接优先指向较新主候选已有对应文件，仅当前存在的指向当前；它们是改动/复用相关文件，不代表已逐字全文复审。拟新增文件单列，不伪造已有链接。既有审查条件仍有效，P1不是已发生线上事故。

每卡“原触发条件/已接受的条件与边界/原证据层级”均复制此前审查记录；摘录中的“本轮”指原审查轮次。本次方案讨论仅核对材料和必要源码，没有重新运行业务测试。

### 共用机制字典（不是额外责任包）

**I-SALES · I-SALES**

- 共同锁和状态协议→销售仓库默认与确认→多仓分组发货；单一通知模型保留。

**I-MASTER · I-MASTER**

- 引用删除协议及可选字段契约独立落地，再回归已有导入、图片、供应商/OE保护。

**I-TRANSFER · I-TRANSFER**

- 调拨统一根锁顺序→删除互斥→非QC短少补发重新预留；共享已有命令账本和库存预留服务。

**I-READ · I-READ**

- 只读分页、身份关联、分类切换可分别开发，复用查询权限/组织范围。

**M-CONTEXT · M-CONTEXT**

- 授权树与UI节点解耦；工作台沿用现有业务口径；消息能力匹配和手机接力。

**WF01 · 共用异步操作作用域**

- 拟新增 uiOperationScope.js，以 begin(lane,context)/isCurrent(token)/invalidate 支持列表、详情、保存、预览独立代次。context 包括 actorId、现有会话代次、选中组织、页面活跃状态、规范化业务目标及不可变查询/载荷快照。
- 从较新 mixins/systemListRecovery.js 和报销 beginFormRead/captureFormOperation 提炼已有模式；前者尚缺完整会话/写入快照，后者只局部覆盖。先以真实反序 Promise 用例验证后按页面适配，不给每页新造 helper，也不一次改全部 Vue。
- 所有 then/catch/finally、异步 validate、confirm 后必须判断；取消 HTTP 只是节流，不能替代守卫或证明写请求未执行。新目标发新请求，旧 loading 不丢弃新目标；过时写回执可记录到原命令，不修改当前 UI。
- 已有模式：[erp-ui/src/mixins/systemListRecovery.js](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/Grok实施工作副本_20260912/erp-ui/src/mixins/systemListRecovery.js>)（latest-only）。
- 已有模式：[erp-ui/src/views/oa/reimbursement/index.vue](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/Grok实施工作副本_20260912/erp-ui/src/views/oa/reimbursement/index.vue>)（current-and-latest）。
- 已有模式：[erp-ui/src/views/mobile/oa/reimbursement/index.vue](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/Grok实施工作副本_20260912/erp-ui/src/views/mobile/oa/reimbursement/index.vue>)（current-and-latest）。
- 拟新增：`erp-ui/src/utils/uiOperationScope.js`。

**WF02 · 动作错误与未知结果恢复**

- 拟新增 approvalActionRecovery.js 作为 OA 采购/报销薄适配器，复用 todoApprovalActions 的请求号生成与通用错误分类思想。该文件 executeTodoApproval/eligibility 目前仅支持 INV_TRANSFER，不得整段用于 OA；persistentCommand/transferCommandRecovery 也只匹配调拨。
- 审批一个用户意图一次 requestId，冻结 actor/session/组织/businessCode+ID/instance/task/action/reason。UNKNOWN 先只读查询，显式重试必须原 ID 原载荷；改目标/动作/原因即不能沿用。严禁任务ID+动作永久固定 ID，也不能每次点击 Date.now 重新造 ID。
- GET /approval/instances/{id} 现有 actions 含 requestId、operatorUserId、taskId、actionType、actionReason；将请求 reason 按后端相同 trim/空值规则规范化后与 actionReason 比较，并与实例业务绑定全匹配才证明原动作已提交。无日志不是未执行，任务已结束/他人处理不归因自己。当前两副本无专用审批命令状态 GET，不发明已存在的查询接口。
- 持久化只存恢复必需 ID/动作/原意见等最小命令快照并按账号/会话/组织隔离，避免 PII/附件/token；复用 safeStorage 方法并补安全取得 sessionStorage。退出清理，重新登录需重新校验 owner/权限且只读核对，不自动重放。存储拒绝降级内存并明确刷新后的恢复边界。
- 统一请求层保持 silentError 原契约；notified、401、凭据限制和 CSRF 已有处理不重复。确认取消无 POST；已发送写请求的 Axios cancel/timeout/network 是结果未知。HTTP500可能是业务拒绝也可能网关/服务异常，按明确业务错误体与可确认事实分类，不能一概断言未执行。
- 已有文件：[erp-ui/src/utils/todoApprovalActions.js](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/Grok实施工作副本_20260912/erp-ui/src/utils/todoApprovalActions.js>)。
- 已有文件：[erp-ui/src/utils/safeStorage.js](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/Grok实施工作副本_20260912/erp-ui/src/utils/safeStorage.js>)。
- 已有文件：[erp-ui/src/utils/request.js](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/Grok实施工作副本_20260912/erp-ui/src/utils/request.js>)。
- 已有文件：[erp-ui/src/api/approval/monitor.js](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/Grok实施工作副本_20260912/erp-ui/src/api/approval/monitor.js>)。
- 已有文件：[erp-modules/erp-approval/src/main/java/com/erp/approval/service/ApprovalTaskService.java](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/Grok实施工作副本_20260912/erp-modules/erp-approval/src/main/java/com/erp/approval/service/ApprovalTaskService.java>)。
- 已有文件：[erp-modules/erp-approval/src/main/java/com/erp/approval/service/ApprovalMonitorService.java](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/Grok实施工作副本_20260912/erp-modules/erp-approval/src/main/java/com/erp/approval/service/ApprovalMonitorService.java>)。
- 拟新增：`erp-ui/src/utils/approvalActionRecovery.js`。

**WF03 · 待办目标与原操作恢复**

- 规范化路由 business/task/instance 目标，用 todoContextLease 仅恢复导航组织/来源，用 WF01 负责请求所有权。两种职责不混用。
- 处理结果显示已完成/待核对/状态已变化；nextTodoKey/nextTodoRow 仅驱动显式“下一条”导航，不调用 quick-approve 或含写操作的 continueNext；下一条仍要求用户核对。
- 先 ROOT-01 目标一致性与 A14 重载，再联测 A11/A13/GROK-01 失败恢复，避免错误处理准确却审批目标错误。

**WF04 · 整批写入及提交后缓存**

- ROOT-02 采用整店原子保存及持久回执；ROOT-03/04 共用事务与提交后缓存流程。S04 是既有逐项事务语义，不并入整批全回滚。
- 新增数据结构及 API 均先服务后 UI、保留原数据；命令结果查询独立授权。缓存故障与数据库提交结果分开，不宣称 Redis 与 DB 已原子化。

**WF05 · 只读结果契约与可恢复入口**

- ROOT-07 本人导出、S03 总体统计、A12 导出历史/批次查找按现有数据权限新增最小契约；无需通用业务命令平台。
- PLAT-UI02 退出仅待服务器/既有 store 明确完成后解锁；Cookie 模式单独验收，不改变默认 bearer。

**WF06 · Cron 无损编辑及有界预览**

- ROOT-08/09 同一解析/序列化与预览修复批次，纯模型、输入边界、有界循环、真实 Vue fill 和 Quartz 对照共同验证；不新建已不存在的后台 preview API。

**HR-HEALTH · 统一健康证生效/统计/投影文案**

- 统一健康证生效/统计/投影文案

**HR-ASYNC · 同一上下文原则，逐入口保护所有回调，不承诺一个全局loading可替代**

- 同一上下文原则，逐入口保护所有回调，不承诺一个全局loading可替代

**HR-ID · 精确ID契约并供异步快照使用**

- 精确ID契约并供异步快照使用

**HR-RECOVERY · 接通已有补卡幂等与状态恢复**

- 接通已有补卡幂等与状态恢复

**HR-IMPORT · 入职模板整列文本**

- 入职模板整列文本

**HR-SIGN-PUBLISH · 有意图的单版本恢复新匹配**

- 有意图的单版本恢复新匹配

**HR-SIGN-FILES · 文件生命周期与事务失败清理**

- 文件生命周期与事务失败清理

**HR-LEGACY-CONTRACT · 旧合同共享锁/CAS及事件一致**

- 旧合同共享锁/CAS及事件一致


### B00 实施基线与可信验证

#### ROOT-05 · P2 · 较新修复副本尚未满足全量验证门禁

原触发条件：最新普通 Maven test 有 2 个断言失败、4 个执行错误：3 个容器测试缺 Docker；另外涉及测试夹具未模拟新角色保护、旧 SQL 截取断言跨到后续查询、POST policy-preview 新接口与统一元数据审计约束不一致。静态门禁也直接拒绝 3 个 *MysqlTest 混入普通测试。

已接受的条件与边界：保留原报告的触发条件、当前/较新副本对照和证据边界；未新增生产事故或实际环境验收结论。

原证据层级：`local-full-test-and-static-gate-logs`。本轮 Node22/JDK17 全量运行日志；不是沿用此前 package -DskipTests 的结论。当前后端 4304 通过/0失败/0错误/12跳过；较新 4827通过/2失败/4错误/27跳过。

具体改法：

1. 以较新已含旧48修复的实际文件快照作为主要候选，先冻结两副本HEAD、分支、tracked/untracked/deleted清单和哈希，逐项对照当前特有更改与旧48/D01-D23，不从裸HEAD误建缺修复候选。
2. 执行阶段建立隔离候选并按文件/变更块导入受审修复；生成来源台账与差异审阅，绝不整目录覆盖两个既有脏树、不自动提交他人改动。旧HR-03以及旧16已实现11项作为基线回归，不加入57计数。
3. 先复现较新6处测试失败/错误，再分别修审批审计契约、过时字符串断言、角色保护测试夹具及3个MySQL测试入口命名/分组；不删除生产保护、不跳过失败伪装绿色。
4. 每批运行针对修改的行为测试和模块构建；汇总时执行现有scripts/ci/verify.sh，真实DB测试必须进入显式集成配置。原生Redis/MySQL脚本目前是有限专用套件，新场景需接入，不能把旧脚本通过说成覆盖新增并发。

已存在相关文件：

- [scripts/ci/verify.sh](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/Grok实施工作副本_20260912/scripts/ci/verify.sh>)
- [scripts/verify-new-business-it-contract.sh](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/Grok实施工作副本_20260912/scripts/verify-new-business-it-contract.sh>)
- [scripts/verify-repository-hygiene.sh](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/Grok实施工作副本_20260912/scripts/verify-repository-hygiene.sh>)
- [scripts/verify-docker-mysql-bootstrap.sh](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/Grok实施工作副本_20260912/scripts/verify-docker-mysql-bootstrap.sh>)
- [scripts/run-native-mysql-integration-tests.sh](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/Grok实施工作副本_20260912/scripts/run-native-mysql-integration-tests.sh>)
- [scripts/run-native-redis-integration-tests.sh](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/Grok实施工作副本_20260912/scripts/run-native-redis-integration-tests.sh>)
- [pom.xml](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/Grok实施工作副本_20260912/pom.xml>)
- [erp-ui/package.json](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/Grok实施工作副本_20260912/erp-ui/package.json>)

依赖/并行边界：无额外依赖，服从本责任包与共享文件顺序。

验收与失败恢复：

- 来源台账能追踪每个保留/导入/冲突决定，候选具备旧48相关行为和57对应待修基线；两旧树业务文件哈希不变。
- Node22/JDK17下全量前端测试/构建、Maven test、静态门禁无失败，skip单列；并发/恢复进入独立真实服务集成，未跑写未跑。
- 选定候选不是当前2树简单拼包；生成前后端release信息和迁移/回退材料，之后真实浏览器/设备与生产验收分层。

迁移与回退：

- 规划阶段不创建候选/提交/部署。本项是验证门禁而非业务缺陷；实施失败可丢弃单独候选增量，保留两份原始脏树与快照。

产品口径：无需新增产品决定，保留已确认规则。

[原问题证据](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/修复工作副本_2026-09-08/output/full-project-audit-20260913/root/findings.md:74>)；[本项设计来源](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/修复工作副本_2026-09-08/output/grok-repair-plan-20260913/platform-design.json:209>)。


### B01 会话、权限和幂等保护

#### P01 · P1 · 注销或撤销后的令牌可能被在途请求重新写回

原触发条件：在途请求已取LoginUser且需续期；另一请求撤销令牌后，旧请求无条件刷新会话。

已接受的条件与边界：同一 HeaderInterceptor.preHandle 已 getLoginUser 读到会话，且需要续期；另一请求撤销后，原请求在 verifyLoginUserExpire 内无条件 SET。窗口限读取与续期 SET 之间，不是整段长业务请求。真实 Redis GET–DEL–SET 尚未验证；P1 是修复优先级。

原证据层级：`offline-java-method-with-memory-stubs`。双副本真实Java方法配内存会话替身复现撤销后重新出现；未验证真实Redis并发。

具体改法：

1. 拆分首次登录创建与已有会话续期/资料更新。只有认证成功的创建路径可新建随机token会话；verifyToken、setLoginUser等已有会话写入必须使用原子存在/预期版本条件，不再先exists再SET。
2. v1明确采用单会话键“原始序列化字节快照CAS”，不新增全用户世代，也不要求旧会话为修复整体重新登录。读取Redis时保留实际原始字节作为请求内期待值，不能把可变LoginUser重序列化后猜原字节；快照只在服务内部传递，不写入JWT、返回体、日志或会话JSON。续期/资料写回比较原字节仍相同且键存在才更新值和TTL；冲突读回并重新核实上下文，被撤销则停止，绝不盲写旧对象。现有旧格式可解析会话也走原始字节CAS，无需先迁移字段；解析失败保持现有拒绝规则。
3. 逐一覆盖全部refreshToken/setLoginUser调用及delLoginUser、deleteLoginUserByUserKey、deleteLoginUsersByUserId、invalidateUserSessions含retainedToken。删除会话键优先；索引仅辅助定位，残留不鉴权、缺失也不能让批量撤销假成功。批量撤销复用现有受限扫描/权威会话集合核对完整性，超限或读取不完整明确失败；保留retainedToken。续期不得无条件SADD重建索引。新索引策略的性能、TTL、失败后修复与全部撤销路径共同验收。
4. 不做跨slot多键Lua作为v1前提；键/值/脚本参数均使用实际Redis序列化契约且以真实Redis验证。所有公共库消费者一致升级，旧无条件SET节点和新CAS混跑期间不能宣布修复闭环。v1不承诺强制回滚已经通过鉴权且开始执行业务的请求。

已存在相关文件：

- [erp-common/erp-common-security/src/main/java/com/erp/common/security/service/TokenService.java](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/Grok实施工作副本_20260912/erp-common/erp-common-security/src/main/java/com/erp/common/security/service/TokenService.java>)
- [erp-common/erp-common-security/src/main/java/com/erp/common/security/interceptor/HeaderInterceptor.java](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/Grok实施工作副本_20260912/erp-common/erp-common-security/src/main/java/com/erp/common/security/interceptor/HeaderInterceptor.java>)
- [erp-common/erp-common-redis/src/main/java/com/erp/common/redis/service/RedisService.java](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/Grok实施工作副本_20260912/erp-common/erp-common-redis/src/main/java/com/erp/common/redis/service/RedisService.java>)
- [scripts/run-native-redis-integration-tests.sh](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/Grok实施工作副本_20260912/scripts/run-native-redis-integration-tests.sh>)
- [erp-auth/src/main/java/com/erp/auth/controller/TokenController.java](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/Grok实施工作副本_20260912/erp-auth/src/main/java/com/erp/auth/controller/TokenController.java>)
- [erp-modules/erp-system/src/main/java/com/erp/system/controller/SysUserController.java](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/Grok实施工作副本_20260912/erp-modules/erp-system/src/main/java/com/erp/system/controller/SysUserController.java>)
- [erp-modules/erp-system/src/main/java/com/erp/system/controller/SysProfileController.java](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/Grok实施工作副本_20260912/erp-modules/erp-system/src/main/java/com/erp/system/controller/SysProfileController.java>)
- [erp-common/erp-common-redis/src/main/java/com/erp/common/redis/configure/FastJson2JsonRedisSerializer.java](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/Grok实施工作副本_20260912/erp-common/erp-common-redis/src/main/java/com/erp/common/redis/configure/FastJson2JsonRedisSerializer.java>)

依赖/并行边界：B00基线及真实Redis环境

验收与失败恢复：

- 真实隔离Redis：A读取→B删除→A续期，键保持不存在且之后请求拒绝；覆盖各撤销入口、retainedToken、正常创建/续期。
- 两个续期/权限更新交错不把新状态覆盖；CAS失败、Redis断连、索引缺失与残留均按明确失败处理。旧格式会话兼容与多服务滚动版本组合单独验证。
- 旧格式有效会话不强制重登即可续期；CAS冲突不覆盖新权限；批量撤销时用户索引缺失/漏成员/残留、超过扫描上限及retainedToken分别验证，不能报告未实际完成的撤销；性能基线单独核验。

迁移与回退：

- v1不改业务表或会话键布局，不新增世代字段；旧可解析会话同样比较原始Redis字节，保持登录连续性。原始快照仅服务内部短期存在，不能序列化/记录凭据。
- 所有消费erp-common-security的服务协调升级；回退使用保留CAS与owner保护的兼容构建，不能回到已知可复活会话的无条件SET。

产品口径：无需新增产品决定，保留已确认规则。

[原问题证据](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/修复工作副本_2026-09-08/output/full-project-audit-20260913/platform/findings.md:7>)；[本项设计来源](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/修复工作副本_2026-09-08/output/grok-repair-plan-20260913/platform-design.json:5>)。

#### P02 · P1 · 云盘把无关角色的全组织数据范围叠加为云盘管理范围

原触发条件：同人有本部门云盘管理角色及无关业务的全部范围角色，云盘合并所有有效角色范围。

已接受的条件与边界：用户同时具有本部门云盘管理角色与无关业务全组织范围角色，后者被错误合入云盘管理范围；不是所有用户或所有云盘权限均无条件扩大。

原证据层级：`offline-java-method-plus-static-sql`。双副本实际范围类方法配角色mapper替身；SQL静态闭环，未实际访问其他组织云盘。

具体改法：

1. 在角色查询层只合并确实授予该云盘管理权限的有效角色范围，通过角色-菜单/权限关联过滤；继续按当前真实组织身份和有效组织树解析。
2. 保留管理员分支、本部门普通可读规则、自定义角色组织、部门及子部门规则；无关业务ALL角色不扩大云盘范围；配额权限仍独立校验。
3. 复用现有身份/权限语义，不自行新增通配符授权；DriveActor当前为permissions.contains，修改前核对角色菜单实际存储格式与权限停用缓存策略。

已存在相关文件：

- [erp-modules/erp-file/src/main/java/com/erp/file/drive/service/DriveOrganizationScopeService.java](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/Grok实施工作副本_20260912/erp-modules/erp-file/src/main/java/com/erp/file/drive/service/DriveOrganizationScopeService.java>)
- [erp-modules/erp-file/src/main/resources/mapper/drive/DriveOrganizationMapper.xml](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/Grok实施工作副本_20260912/erp-modules/erp-file/src/main/resources/mapper/drive/DriveOrganizationMapper.xml>)
- [erp-modules/erp-file/src/main/java/com/erp/file/drive/mapper/DriveOrganizationMapper.java](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/Grok实施工作副本_20260912/erp-modules/erp-file/src/main/java/com/erp/file/drive/mapper/DriveOrganizationMapper.java>)
- [erp-modules/erp-file/src/main/java/com/erp/file/drive/service/DriveActorResolver.java](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/Grok实施工作副本_20260912/erp-modules/erp-file/src/main/java/com/erp/file/drive/service/DriveActorResolver.java>)

依赖/并行边界：B00

验收与失败恢复：

- 真实mapper集成：云盘DEPT+无关ALL，只管理本部门；两个确实授予云盘权限角色合并正确；CUSTOM、SELF、子组织、停用角色/组织、管理员回归。
- PC/H5直达API均验证读写/整理/删除/配额权限；修复前异常授权数据只读盘点，不自动搬移/删除文件。

迁移与回退：

- 预期无需改表；有效权限范围收紧属于修复。若需历史权限缓存失效，按受影响角色/用户精准处理；回退保持权限过滤，不恢复错误扩大范围。

产品口径：无需新增产品决定，保留已确认规则。

[原问题证据](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/修复工作副本_2026-09-08/output/full-project-audit-20260913/platform/findings.md:15>)；[本项设计来源](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/修复工作副本_2026-09-08/output/grok-repair-plan-20260913/platform-design.json:41>)。

#### P05 · P2 · 通用幂等锁在超时请求结束时可能删除后来请求的锁

原触发条件：A超过锁TTL；B取同键新锁；A失败释放无条件删除B锁，C可与B并行进入。

已接受的条件与边界：保留原报告的触发条件、当前/较新副本对照和证据边界；未新增生产事故或实际环境验收结论。

原证据层级：`offline-java-method-with-memory-stubs`。双副本真实tryAcquire/release方法和内存TTL替身；未跑Redis集群。具体业务可能另有持久幂等。

具体改法：

1. tryAcquire生成不可重复owner并作为锁值，返回持有凭据；释放Lua原子比较owner后删除。AOP只释放本次获取成功的owner，不因异常/迟到finally删除B锁。
2. 保留releaseOnSuccess/releaseOnFailure与TTL的当前语义；不能统一finally释放而取消成功后短期防重。旧常量1锁等待自然过期，新代码不得把旧锁认作自己。
3. 此项最小修复不把TTL锁宣传成业务exactly-once；超过TTL的长操作需既有持久命令/唯一键兜底，必要的续租必须owner校验与上限，不能只无限延长锁。

已存在相关文件：

- [erp-common/erp-common-security/src/main/java/com/erp/common/security/service/IdempotentSubmitService.java](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/Grok实施工作副本_20260912/erp-common/erp-common-security/src/main/java/com/erp/common/security/service/IdempotentSubmitService.java>)
- [erp-common/erp-common-security/src/main/java/com/erp/common/security/aspect/IdempotentSubmitAspect.java](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/Grok实施工作副本_20260912/erp-common/erp-common-security/src/main/java/com/erp/common/security/aspect/IdempotentSubmitAspect.java>)
- [erp-common/erp-common-security/src/main/java/com/erp/common/security/annotation/IdempotentSubmit.java](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/Grok实施工作副本_20260912/erp-common/erp-common-security/src/main/java/com/erp/common/security/annotation/IdempotentSubmit.java>)
- [erp-common/erp-common-redis/src/main/java/com/erp/common/redis/service/RedisService.java](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/Grok实施工作副本_20260912/erp-common/erp-common-redis/src/main/java/com/erp/common/redis/service/RedisService.java>)

依赖/并行边界：B00

验收与失败恢复：

- 真实Redis A持锁→过期→B获新锁→A失败释放→C仍不能获得；正确owner可释放，失败释放/Redis异常仅记录且TTL兜底。
- 同请求正常成功、注解两种release策略、无权限/跨组织key隔离，以及业务自身幂等与长期耗时反例均回归。

迁移与回退：

- 无业务表迁移；新旧AOP混跑旧节点仍会无条件DEL，发布时需统一替换该公共库消费方并验证；兼容回退构建必须保留owner比较，不能恢复漏洞。

产品口径：无需新增产品决定，保留已确认规则。

[原问题证据](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/修复工作副本_2026-09-08/output/full-project-audit-20260913/platform/findings.md:39>)；[本项设计来源](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/修复工作副本_2026-09-08/output/grok-repair-plan-20260913/platform-design.json:129>)。

#### P06 · P1 · monitor 匿名放行整个实例管理与代理命名空间

原触发条件：使用现有monitor安全配置启动且访问者能连接时，实例查询/删除/代理命名空间匿名放行。

已接受的条件与边界：monitor 配置对实例/代理命名空间匿名放行是源码事实；普通 compose 端口绑定 127.0.0.1，ecs-host 的 host 网络不证明 server.address=0.0.0.0 或公网可达。真实监听、Nacos、外层认证、安全组及 actuator 范围待核实，保留部署条件项。

原证据层级：`static-config-and-matching-dependency-source`。项目安全配置与匹配版本官方依赖源码闭环；无HTTP探测，外层认证/网络/actuator配置未确认。

具体改法：

1. 先只读核实生产monitor有效配置、实际绑定、网关/安全组、Nacos、注册凭据及被代理actuator范围，分别记录，不以host网络推断公网暴露。核实可与其他开发并行。
2. 源码默认最小开放：登录页/必要静态资源与最小健康端点；实例查询/删除和actuator代理要求管理员，客户端注册使用单独受控身份与准确HTTP方法/路径。
3. 为浏览器管理操作恢复CSRF保护；仅对服务注册的确切机器调用设计例外。注册凭据先配到客户端再收紧，管理与注册权限不混用；不把认证材料写入报告。

已存在相关文件：

- [erp-visual/erp-monitor/src/main/java/com/erp/modules/monitor/config/WebSecurityConfigurer.java](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/Grok实施工作副本_20260912/erp-visual/erp-monitor/src/main/java/com/erp/modules/monitor/config/WebSecurityConfigurer.java>)
- [docker/docker-compose.yml](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/Grok实施工作副本_20260912/docker/docker-compose.yml>)
- [docker/docker-compose.ecs-host.yml](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/Grok实施工作副本_20260912/docker/docker-compose.ecs-host.yml>)

依赖/并行边界：B00可并行的只读环境核验

验收与失败恢复：

- 隔离环境匿名列表/删除/代理拒绝，管理员可管理，注册身份只做允许动作，合法客户端注册/心跳连续；反向代理路径和CSRF反例。
- 部署后只读核验实际监听和认证边界；未核验前保持部署条件状态，不写已证实公网漏洞或已生产解决。

迁移与回退：

- 默认不改业务表；外部配置与凭据先后顺序单独发布清单。若注册异常回退客户端/兼容配置或保持网络隔离，不能重新匿名开放整个管理空间。

产品口径：无需新增产品决定，保留已确认规则。

[原问题证据](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/修复工作副本_2026-09-08/output/full-project-audit-20260913/platform/findings.md:47>)；[本项设计来源](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/修复工作副本_2026-09-08/output/grok-repair-plan-20260913/platform-design.json:155>)。

#### PLAT-UI02 · P2 · Cookie会话模式下，锁屏页退出失败仍先解除锁定并回到首页

原触发条件：仅当构建启用VUE_APP_WEB_SESSION_MODE=cookie，已有有效Cookie会话且已锁屏；在锁屏页点“退出重新登录”，服务端登出在撤销前返回503或因网络失败未完成。两个受审副本.env.production均默认bearer，该条件分支默认不启用，未核实线上Cookie模式。

已接受的条件与边界：仅启用 Cookie 构建、已有有效会话并锁屏、登出在撤销前失败时触发。两副本默认 production 配置为 bearer；未核实生产 Cookie 构建，不能扩展到默认部署。

原证据层级：`real-source-offline-probe-plus-stated-static-boundary`。实际lock.vue.goLogin先dispatch(lock/unlockScreen)，然后LogOut；实际Store Cookie登出失败保留会话并reject，页面finally却replace(/login)。实际permission.beforeEach见会话仍authenticated把/login转到/，本地isLock已为false。两副本真实页面/Store方法及完整路由beforeEach回调配API桩，输出sessionAuthenticated=true、screenLocked=false、localSessionClearCount=0、logoutWarningCount=1、actualLoginGuardRedirect=/。模拟启用Cookie配置，无真实Cookie、登录、登出或浏览器访问；Bearer分支会清本地会话，不扩大到默认部署。

具体改法：

1. 锁屏“退出重新登录”先调用现有 LogOut；仅其完成成功（含当前 store 已处理的 401 失效会话）后执行 unlock 并导航登录。finally 只恢复本次按钮忙状态，不能导航或解锁。
2. Cookie 登出 503/网络失败且服务端未撤销时保留 lock 状态与当前路由，提示可重试；尊重现有全局 notified 避免双提示。禁止以 FedLogOut/删除 JS token 假装撤销 HttpOnly Cookie。
3. 冻结当前会话代次并禁用双击；旧会话退出回包不能解锁后来登录的新会话。Bearer 原有最佳努力清理语义保持，测试两模式而非全局强制 Cookie。

已存在相关文件：

- [erp-ui/src/views/lock.vue](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/Grok实施工作副本_20260912/erp-ui/src/views/lock.vue>)
- [erp-ui/src/store/modules/user.js](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/Grok实施工作副本_20260912/erp-ui/src/store/modules/user.js>)
- [erp-ui/src/store/modules/lock.js](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/Grok实施工作副本_20260912/erp-ui/src/store/modules/lock.js>)
- [erp-ui/src/utils/sessionMode.js](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/Grok实施工作副本_20260912/erp-ui/src/utils/sessionMode.js>)
- [erp-ui/src/permission.js](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/Grok实施工作副本_20260912/erp-ui/src/permission.js>)

依赖/并行边界：WF01；WF05

验收与失败恢复：

- Cookie 模式有效且锁定，撤销前 503/断网：仍锁定，不导航首页/登录，显示可重试且按钮恢复。
- Cookie 正常成功与已失效 401 可到登录；重试成功后才解锁。
- Bearer 成功与既有失败清理契约不回归；双击仅一轮退出。
- 登出过程中会话代次变化，旧 finally/成功不改新会话锁状态。

迁移与回退：

- 无数据库迁移，不改变构建模式默认值；当前两副本默认 bearer。按实际部署模式单独验收，本计划不宣称线上 Cookie 分支启用。

产品口径：无需新增产品决定，保留已确认规则。

[原问题证据](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/修复工作副本_2026-09-08/output/full-project-audit-20260913/platform/final-ui-findings.md:29>)；[本项设计来源](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/修复工作副本_2026-09-08/output/grok-repair-plan-20260913/workflow-design.json:657>)。


### B02 库存与销售主链路

#### INV-N01 · P1 · PC 新销售单无法生成发货通知

原触发条件：PC新建销售未写入明细出库仓库，保存提交后生成发货通知被必填仓库校验拒绝。

已接受的条件与边界：保留原报告的触发条件、当前/较新副本对照和证据边界；未新增生产事故或实际环境验收结论。

原证据层级：`offline-vue-method-plus-static-chain`。双副本真实Vue方法输出空warehouseId；请求、保存和通知服务静态闭环。移动端另有必填仓库，不扩大到H5。

保留规则：

- 单据按当前业务组织权限执行；仓库候选不替代服务端授权。
- 销售仓库确认不等于出库；保留发货通知和后续发货确认。
- 保留较新H5字段、图片和D11未保存输入保护，不整页替换。

具体改法：

1. PC销售表单增加默认出库仓库与每行仓库显示，复用WarehouseSelect，固定传purpose=deliverySource及当前授权组织scopeDeptId；候选只取该接口正式返回的有效仓库，保存端继续验证WAREHOUSE类型和可见范围。
2. 当前组织本身是候选仓库时预填当前仓库；否则仅当有效候选唯一时预填；多候选不擅自按第一个、商品属主、客户或历史偏好决定。上次选择可作为显式建议，但须仍在当次候选内。
3. 新增物料继承默认仓库；批量“应用到未指定行”只填空行，已有逐行仓库保持不变。默认值、行归属和按仓数量在保存/提交摘要中可见，用户点击现有保存并提交或确认生成通知才发生业务动作，不新增自动审批/发货。
4. 草稿允许暂未补全仓库以保留录入；正式提交在服务端要求所有明细有合法仓库，校验错误明确指出行名。PC/H5使用同一服务端约束，保留商品/礼盒销售、OE不得销售及正数量/正售价、真实客户档案规则。
5. 历史submitted缺仓单目前没有合法常规编辑入口。受控“补全仓库并生成通知”设计纳入本方案；只接受空仓明细ID与仓库映射、原单版本，在现有组织/操作权限校验下锁定销售单，再验证仍submitted、无活动通知/出库且版本和内容未变，禁止修改价格/数量/客户/已填仓行。同一事务补仓并调用现有通知快照逻辑、保留业务审计。具体历史单据、仓库映射与真实执行范围形成清单后另行确认；本次仅规划，不阻塞未来入口实现或新单仓库修复，更不能发布时批量SQL猜仓。
6. 表单保存/仓库加载都冻结组织、目标订单和会话；权限变化或候选失败时保留录入与解释，不以空数组自动清除已填有效值。

已存在相关文件：

- [erp-ui/src/views/inventory/sales/index.vue](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/Grok实施工作副本_20260912/erp-ui/src/views/inventory/sales/index.vue>)
- [erp-ui/src/views/inventory/components/WarehouseSelect.vue](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/Grok实施工作副本_20260912/erp-ui/src/views/inventory/components/WarehouseSelect.vue>)
- [erp-ui/src/api/inventory/sales.js](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/Grok实施工作副本_20260912/erp-ui/src/api/inventory/sales.js>)
- [erp-ui/src/api/inventory/deliveryNotice.js](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/Grok实施工作副本_20260912/erp-ui/src/api/inventory/deliveryNotice.js>)
- [erp-ui/src/views/mobile/feature/mobileFormConfigs.js](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/Grok实施工作副本_20260912/erp-ui/src/views/mobile/feature/mobileFormConfigs.js>)
- [erp-ui/src/views/mobile/feature/mobileFormPayloads.js](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/Grok实施工作副本_20260912/erp-ui/src/views/mobile/feature/mobileFormPayloads.js>)
- [erp-modules/erp-system/src/main/java/com/erp/system/service/impl/SysDeptServiceImpl.java](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/Grok实施工作副本_20260912/erp-modules/erp-system/src/main/java/com/erp/system/service/impl/SysDeptServiceImpl.java>)
- [erp-modules/erp-inventory/src/main/java/com/erp/inventory/controller/InvSalesController.java](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/Grok实施工作副本_20260912/erp-modules/erp-inventory/src/main/java/com/erp/inventory/controller/InvSalesController.java>)
- [erp-modules/erp-inventory/src/main/java/com/erp/inventory/controller/InvDeliveryNoticeController.java](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/Grok实施工作副本_20260912/erp-modules/erp-inventory/src/main/java/com/erp/inventory/controller/InvDeliveryNoticeController.java>)
- [erp-modules/erp-inventory/src/main/java/com/erp/inventory/service/impl/InvSalesServiceImpl.java](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/Grok实施工作副本_20260912/erp-modules/erp-inventory/src/main/java/com/erp/inventory/service/impl/InvSalesServiceImpl.java>)
- [erp-modules/erp-inventory/src/main/java/com/erp/inventory/service/impl/InvDeliveryNoticeServiceImpl.java](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/Grok实施工作副本_20260912/erp-modules/erp-inventory/src/main/java/com/erp/inventory/service/impl/InvDeliveryNoticeServiceImpl.java>)
- [erp-modules/erp-inventory/src/main/resources/mapper/inventory/InvSalesDetailMapper.xml](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/Grok实施工作副本_20260912/erp-modules/erp-inventory/src/main/resources/mapper/inventory/InvSalesDetailMapper.xml>)

拟新增：`erp-modules/erp-inventory/src/main/java/com/erp/inventory/domain/dto/InvSalesWarehouseRepairRequest.java`。

可复用机制：WarehouseSelect.vue已有purpose/scopeDeptId；SysDeptServiceImpl.selectWarehouseList已有deliverySource，H5 sales已有同用途必填warehouseId和applyOrderWarehouseDefaults。；InvDeliveryNoticeServiceImpl.createNotice已有原销售单ForUpdate、有效通知检查和行级仓库快照。

依赖/并行边界：I-SALES基础锁/版本由INV-N04先落地；INV-N02与本项联调多仓通知。

验收与失败恢复：

- [页面] 单一有效仓自动带出，用户看到仓名后一次确认即可提交并生成通知；多候选必须显式选择；新增/删除明细不覆盖其他行仓库。
- [页面+接口] A/B逐行仓销售可保存和生成通知；草稿空仓可保存，正式提交空仓定位具体行；OE销售、停用/越范围仓库、过期候选仍拒绝。
- 【受控历史入口—隔离测试】[MySQL双事务] 使用独立测试库构造历史submitted缺仓样本，补仓与创建通知/取消/另一次补仓竞争，只能一种合法结果；受影响行数、订单版本、仓库快照一致；失败补仓不留半张通知。此为受控历史入口自身的必要并发验收，不需要真实历史清单或生产写入授权，不阻塞核心新单功能的实现、隔离验收及独立交付。
- [页面弱网] 候选失败后重试、切组织后旧响应迟到、保存时切下一单均不丢新输入；历史单修复失败保留映射但必须重新核对状态。

历史数据执行验收（形成具体清单并确认真实执行范围后执行）：

- 【真实历史执行—独立门禁】先只读盘点待补仓历史单，列明原单/明细ID、现有状态/版本、活动通知/出库及仓库授权范围；无可靠原始依据的项保留待核对，不猜仓。
- 形成可审具体单据清单、逐行合法仓库映射和明确真实执行范围，取得针对该清单的批准后才执行；此确认只针对历史数据写入，不重复确认已纳入方案的受控入口实现。
- 执行时重新校验submitted、无活动通知/出库、组织/操作权限、行版本及内容未变，并在根锁与同一事务内补仓/生成通知/保留审计；冲突项跳过或中止后重新核对，不能凭批准时快照忽略当前状态。
- 逐笔核对批准清单、写入回执、前后快照及生成通知；错误或未知结果先按原单/通知回执核对，不盲目重复写。
- 执行前准备具体备份与可逆回退方案，执行后保留完整审计；已经发货或产生后续业务事实时不能简单清空仓库/删除通知，应停止并走对应受控业务纠正。真实历史执行未获清单批准时不执行，不阻塞核心新单或受控入口的本地实现和隔离验收。

迁移与回退：

- 新建单路径不要求新仓库字段DDL，明细warehouse_id已存在；原单版本迁移跟INV-N04。
- 受控补仓入口纳入本方案，具体历史单据、仓库映射和真实执行范围形成可审清单后确认；确认前仅只读盘点，不执行历史数据补仓、不自动取消重建或批量填写默认仓。本次仅规划，不阻塞未来入口实现或新单修复。
- API与前端按兼容顺序发布；回退应用保留已补仓事实及通知快照，不把合法warehouse_id清回NULL。

产品口径：无待决定的入口设计：新单预填、提交校验及受控补仓已纳入本方案。仅具体历史单据清单、仓库映射和真实执行范围形成可审材料后另行确认，不自动处理历史数据；本次仅规划，不阻塞未来入口实现或新单修复。

[原问题证据](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/修复工作副本_2026-09-08/output/full-project-audit-20260913/inventory/findings.md:9>)；[本项设计来源](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/修复工作副本_2026-09-08/output/grok-repair-plan-20260913/inventory-design.json:68>)。

#### INV-N02 · P2 · 多仓库发货通知默认提交全部行而必定被拒绝

原触发条件：已有A/B多仓销售通知，发货选A时仍默认提交所有仓库明细数量。

已接受的条件与边界：保留原报告的触发条件、当前/较新副本对照和证据边界；未新增生产事故或实际环境验收结论。

原证据层级：`offline-vue-method-plus-static-chain`。两副本Vue方法均提交跨仓行；后端仓库一致性拒绝为静态链路，未真实发货。

保留规则：

- 保留分批/部分发货和行仓快照不可被发货端重写。
- 保留授权范围、数量上界、库存/成本与跨店调拨规则；不自动替换缺货仓库。

具体改法：

1. 保留一张销售配送通知可含多个仓库明细的现有模型：resolveNoticeWarehouseId在混仓时明确返回null；头warehouseId只是整单单仓快捷值，真实归属是明细warehouseId。无需拆成一仓一通知，否则还会改动每销售单仅一个活动通知的hasActiveNotice约束。
2. openDeliver保留每行warehouseId/warehouseName及稳定detailId；用notice明细建立仓库分组，每组显示剩余行数/数量，当前可操作仓库默认激活，唯一可发仓免再选择。
3. 提交只构造当前仓库组中数量>0的items；后端assertNoticeDetailWarehouse继续是最终防线。切组保留按detailId存储的部分发货输入，不将另一仓的数清零或丢弃后再要求重录。
4. 每组提供“本仓全部剩余”和一次确认；展示仓库、行、数量后调用现有单仓发货接口。不要把多仓包装成未经设计的跨仓原子请求，A成功/B失败明确各组结果。
5. 读取新的服务端通知状态后重新计算各组剩余量；明确失败可原地重试，响应未知只查询核对通知/出库结果，禁止自动重放发货。没有可判定结果时保持未确认提示。

已存在相关文件：

- [erp-ui/src/views/inventory/deliveryNotice/index.vue](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/Grok实施工作副本_20260912/erp-ui/src/views/inventory/deliveryNotice/index.vue>)
- [erp-ui/src/api/inventory/deliveryNotice.js](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/Grok实施工作副本_20260912/erp-ui/src/api/inventory/deliveryNotice.js>)
- [erp-modules/erp-inventory/src/main/java/com/erp/inventory/service/impl/InvDeliveryNoticeServiceImpl.java](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/Grok实施工作副本_20260912/erp-modules/erp-inventory/src/main/java/com/erp/inventory/service/impl/InvDeliveryNoticeServiceImpl.java>)
- [erp-modules/erp-inventory/src/main/java/com/erp/inventory/domain/InvDeliveryNoticeDetail.java](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/Grok实施工作副本_20260912/erp-modules/erp-inventory/src/main/java/com/erp/inventory/domain/InvDeliveryNoticeDetail.java>)
- [erp-modules/erp-inventory/src/main/resources/mapper/inventory/InvDeliveryNoticeDetailMapper.xml](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/Grok实施工作副本_20260912/erp-modules/erp-inventory/src/main/resources/mapper/inventory/InvDeliveryNoticeDetailMapper.xml>)
- [erp-modules/erp-inventory/src/main/java/com/erp/inventory/domain/dto/InvDeliverRequest.java](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/Grok实施工作副本_20260912/erp-modules/erp-inventory/src/main/java/com/erp/inventory/domain/dto/InvDeliverRequest.java>)

可复用机制：后端已生成行级warehouseId；单次deliverNotice只允许一个仓库并逐行检查归属；已有通知ForUpdate及库存检查可沿用，锁顺序由INV-N04统一。

依赖/并行边界：INV-N01构建销售多仓样本；INV-N04先统一通知发货/取消锁顺序。

验收与失败恢复：

- [页面] 两仓各两行，选A只提交A；切B再回A仍保留A手动部分数量；0数量行不发送。
- [MySQL+接口] A成功B库存不足，A出库仅一次、B无部分写；刷新只展示B待处理。伪造A请求夹B行仍全事务拒绝。
- [MySQL双事务] 同仓两操作员同时发出最后数量不得超发；与通知取消竞争的结果符合INV-N04状态协议。
- [页面弱网] 成功回包丢失时不自动再次发货；加载状态失败显示“未确认”而不是把已发组当未发；新打开的另一通知不被旧响应覆盖。

迁移与回退：

- 仅前端分组和返回字段确认，无必要DDL；不改变现有通知/库存历史。
- 前后端兼容旧单仓请求；回退前端保留服务端仓库校验和已经形成的出库记录。

产品口径：无；一仓一次确认沿用现有API事务边界，多仓全部一键自动发货不在本批。

[原问题证据](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/修复工作副本_2026-09-08/output/full-project-audit-20260913/inventory/findings.md:19>)；[本项设计来源](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/修复工作副本_2026-09-08/output/grok-repair-plan-20260913/inventory-design.json:140>)。

#### INV-N03 · P1 · 删除有库存的 OE/礼盒主数据导致后续物料操作失去入口

原触发条件：有库存或业务引用的OE/礼盒被删除后，后续解析器只读取未删除主数据。

已接受的条件与边界：保留原报告的触发条件、当前/较新副本对照和证据边界；未新增生产事故或实际环境验收结论。

原证据层级：`in-memory-sql-plus-static-chain`。真实soft-delete SQL在内存执行；库存残留及业务操作后果来自源码。未实际删除生产物料。

保留规则：

- 商品已有保护仅作为引用范围参照，不冒充OE/礼盒已修。
- 较新INV-F1供应商改名/删除OE引用保护及固定资产购买参考完整性不回退。
- 有历史引用不硬删，不把“停用”扩展成清除历史。

具体改法：

1. OE/礼盒删除改为与商品相同的业务引用保护：按(itemType,itemId)统计库存行（含零数量）、库存流水、采购/退货、销售/退货、配送、调拨/发收批次、盘点及相应预留/历史事实；OE额外保留固定资产配置引用检查。不要只看当前库存>0。
2. 服务端批删先规范化去重、按物料类型/ID固定顺序锁定全部主档，完成存在/权限/全部引用检查后一次删除；任一被引用则整批不删。返回可见范围内的引用类别/数量与安全跳转，超范围历史仅给受引用提示，不泄露其他组织单据。
3. 复用较新lockOe/lockGift和ForUpdate mapper。为新增业务引用的解析入口增加同一主档行锁协议：引用创建在主档锁后复核del_flag/status，持锁到引用提交；删除引用探测使用锁后新可见读，避免MySQL RR旧快照。方案实现采用独立READ_COMMITTED删除事务边界且拒绝从已有事务内部绕过，该事务锁定后读取引用计数；所有引用写路径必须在主档锁内建立引用，否则不得通过并发验收。
4. 删除事务只锁主档、不反向锁业务父单；其他业务保留“业务父单→主档→库存”顺序，主档单元批量按类型/ID排序，防止主档删除与单据锁形成环。对会新建引用的所有InventoryItemResolver/固定资产入口建立明确调用清单后接入。
5. 有引用时推荐停用，保留历史可读；存量已被误删主档先输出库存/历史引用清单，经人工逐条选择恢复为停用状态，复用原ID，不重建同名新ID或伪造主数据。

已存在相关文件：

- [erp-modules/erp-inventory/src/main/java/com/erp/inventory/service/impl/InvGiftServiceImpl.java](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/Grok实施工作副本_20260912/erp-modules/erp-inventory/src/main/java/com/erp/inventory/service/impl/InvGiftServiceImpl.java>)
- [erp-modules/erp-inventory/src/main/java/com/erp/inventory/service/impl/InvOeServiceImpl.java](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/Grok实施工作副本_20260912/erp-modules/erp-inventory/src/main/java/com/erp/inventory/service/impl/InvOeServiceImpl.java>)
- [erp-modules/erp-inventory/src/main/java/com/erp/inventory/service/impl/InvProductServiceImpl.java](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/Grok实施工作副本_20260912/erp-modules/erp-inventory/src/main/java/com/erp/inventory/service/impl/InvProductServiceImpl.java>)
- [erp-modules/erp-inventory/src/main/java/com/erp/inventory/service/impl/InvSupplierServiceImpl.java](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/Grok实施工作副本_20260912/erp-modules/erp-inventory/src/main/java/com/erp/inventory/service/impl/InvSupplierServiceImpl.java>)
- [erp-modules/erp-inventory/src/main/java/com/erp/inventory/mapper/InvGiftMapper.java](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/Grok实施工作副本_20260912/erp-modules/erp-inventory/src/main/java/com/erp/inventory/mapper/InvGiftMapper.java>)
- [erp-modules/erp-inventory/src/main/java/com/erp/inventory/mapper/InvOeMapper.java](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/Grok实施工作副本_20260912/erp-modules/erp-inventory/src/main/java/com/erp/inventory/mapper/InvOeMapper.java>)
- [erp-modules/erp-inventory/src/main/resources/mapper/inventory/InvProductMapper.xml](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/Grok实施工作副本_20260912/erp-modules/erp-inventory/src/main/resources/mapper/inventory/InvProductMapper.xml>)
- [erp-modules/erp-inventory/src/main/resources/mapper/inventory/InvGiftMapper.xml](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/Grok实施工作副本_20260912/erp-modules/erp-inventory/src/main/resources/mapper/inventory/InvGiftMapper.xml>)
- [erp-modules/erp-inventory/src/main/resources/mapper/inventory/InvOeMapper.xml](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/Grok实施工作副本_20260912/erp-modules/erp-inventory/src/main/resources/mapper/inventory/InvOeMapper.xml>)
- [erp-modules/erp-inventory/src/main/java/com/erp/inventory/service/impl/InventoryItemResolver.java](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/Grok实施工作副本_20260912/erp-modules/erp-inventory/src/main/java/com/erp/inventory/service/impl/InventoryItemResolver.java>)
- [erp-ui/src/views/inventory/oe/index.vue](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/Grok实施工作副本_20260912/erp-ui/src/views/inventory/oe/index.vue>)
- [erp-ui/src/views/inventory/gift/index.vue](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/Grok实施工作副本_20260912/erp-ui/src/views/inventory/gift/index.vue>)

拟新增：`erp-modules/erp-inventory/src/main/java/com/erp/inventory/service/impl/InvCatalogDeletionService.java`；`erp-modules/erp-inventory/src/main/java/com/erp/inventory/domain/vo/InvCatalogReferenceSummary.java`。

可复用机制：较新OE/礼盒编辑/导入已有lockOe/lockGift；商品countBusinessReferenceByProductId覆盖库存/历史单据；供应商INV-F1已演示主档锁与引用保护。

依赖/并行边界：与INV-N06/INV-UI01共享OE/礼盒文件，分小补丁按锁/引用→字段更新→选择器顺序合入。；跨模块固定资产新增OE引用入口由root组配合；必须先列齐引用写入清单，不靠一个count方法宣称并发安全。

验收与失败恢复：

- [MySQL] 无引用可删；只有零库存行、只有库存流水、历史采购/调拨/盘点、预留或固定资产配置的主档均拒绝删除；批量中一条被引用时全部保留。
- [MySQL双事务] 删除持有主档锁与新增采购/调拨/固定资产引用交错：新增先提交则删除看见引用而拒绝；删除先提交则新引用复核主档后失败回滚；不能形成新孤儿。
- [MySQL隔离] 在默认RR外层调用该删除服务必须被禁止或显式走独立新事务；READ_COMMITTED锁后引用查询确实看到先提交记录。多ID反序请求无死锁或受控重试不部分删除。
- [页面] 引用提示可定位本组织相关业务并提供停用；权限不足不展示其他组织业务细节；失败保留筛选和选中项。

迁移与回退：

- 先做只读孤儿/引用分布盘点并确认各itemType索引；必要补索引采用新增SQL，按真实表/索引检查后再定DDL，不在方案阶段臆造已建索引。
- 恢复已删历史需要单独确认名单、备份原del_flag/status及审计，不自动修复生产。
- 回退仅回应用，不删除新增引用保护数据；已恢复停用主档保持可追溯，不能随代码回退再次删掉。

产品口径：无阻塞：已有商品规则是“被库存或历史单据引用则停用”。存量恢复的具体名单属于执行前数据确认，不重新讨论业务规则。

[原问题证据](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/修复工作副本_2026-09-08/output/full-project-audit-20260913/inventory/findings.md:27>)；[本项设计来源](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/修复工作副本_2026-09-08/output/grok-repair-plan-20260913/inventory-design.json:200>)。

#### INV-N04 · P1 · 销售单编辑/取消与提交/生成发货通知不共享行锁

原触发条件：销售取消/编辑普通读旧状态，另一事务提交或创建通知后，旧动作仍按ID更新。

已接受的条件与边界：服务和 SQL 缺少共同锁/状态防线的并发链路成立；尚无隔离 MySQL 两事务交错实测，不能称已证实线上账单错乱。

原证据层级：`static-concurrency-chain`。双副本锁、状态更新、通知/发货服务静态交错分析；没有真实MySQL双事务复现。

保留规则：

- 保留较新销售退货源单优先锁、按历史已退量算可退量和成本冲回。
- 不把静态并发风险描述成已经出现生产错账。

具体改法：

1. 统一销售相关互斥顺序：按销售orderId升序锁原销售单→相关通知/销售退货头→各自明细/调拨根（涉及多根按ID排序）→库存按稳定stockId顺序。沿用较新销售退货lockScopedReturn先锁原单的规则，不新建一把仅保护某个接口的Redis锁。
2. saveDraft、submitSales、cancelSales在同一事务内锁原单并锁后检查状态/范围；通知create/deliver/cancel必须同步采用销售单优先。已知noticeId先无锁读取不可变salesOrderId用于定位，锁原销售单→通知后再比对归属，归属变化即中止；recalcSalesOrderStatus只在正确锁顺序内调用，不允许先持通知/库存再反向获取销售锁。
3. 状态DML带expectedStatus，检查受影响行数。为跨标签页同状态内容覆盖增加销售头version与API返回版本，PC/H5提交expectedVersion；保存草稿、提交、取消、仓库补全/状态重算按协议推进版本，不接受过期输入静默覆盖。
4. 版本不匹配或单据已被通知/发货后，返回明确冲突对象与当前状态，保留本地输入，提供刷新比较后重新确认；不要自动再保存过期明细。
5. 保持通知快照不被后续销售编辑改写，取消仅允许现有规则允许状态且无应保留的有效发货效果；不把正在发货的通知强制取消。调用链审查包含销售退货和销售生成调拨，回归其已有成本/预留修复。

已存在相关文件：

- [erp-modules/erp-inventory/src/main/java/com/erp/inventory/service/impl/InvSalesServiceImpl.java](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/Grok实施工作副本_20260912/erp-modules/erp-inventory/src/main/java/com/erp/inventory/service/impl/InvSalesServiceImpl.java>)
- [erp-modules/erp-inventory/src/main/java/com/erp/inventory/service/impl/InvDeliveryNoticeServiceImpl.java](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/Grok实施工作副本_20260912/erp-modules/erp-inventory/src/main/java/com/erp/inventory/service/impl/InvDeliveryNoticeServiceImpl.java>)
- [erp-modules/erp-inventory/src/main/java/com/erp/inventory/service/impl/InvSalesReturnServiceImpl.java](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/Grok实施工作副本_20260912/erp-modules/erp-inventory/src/main/java/com/erp/inventory/service/impl/InvSalesReturnServiceImpl.java>)
- [erp-modules/erp-inventory/src/main/java/com/erp/inventory/service/impl/InvTransferServiceImpl.java](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/Grok实施工作副本_20260912/erp-modules/erp-inventory/src/main/java/com/erp/inventory/service/impl/InvTransferServiceImpl.java>)
- [erp-modules/erp-inventory/src/main/java/com/erp/inventory/domain/InvSalesOrder.java](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/Grok实施工作副本_20260912/erp-modules/erp-inventory/src/main/java/com/erp/inventory/domain/InvSalesOrder.java>)
- [erp-modules/erp-inventory/src/main/java/com/erp/inventory/mapper/InvSalesOrderMapper.java](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/Grok实施工作副本_20260912/erp-modules/erp-inventory/src/main/java/com/erp/inventory/mapper/InvSalesOrderMapper.java>)
- [erp-modules/erp-inventory/src/main/resources/mapper/inventory/InvSalesOrderMapper.xml](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/Grok实施工作副本_20260912/erp-modules/erp-inventory/src/main/resources/mapper/inventory/InvSalesOrderMapper.xml>)
- [erp-modules/erp-inventory/src/main/resources/mapper/inventory/InvSalesDetailMapper.xml](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/Grok实施工作副本_20260912/erp-modules/erp-inventory/src/main/resources/mapper/inventory/InvSalesDetailMapper.xml>)
- [erp-ui/src/views/inventory/sales/index.vue](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/Grok实施工作副本_20260912/erp-ui/src/views/inventory/sales/index.vue>)
- [erp-ui/src/views/inventory/deliveryNotice/index.vue](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/Grok实施工作副本_20260912/erp-ui/src/views/inventory/deliveryNotice/index.vue>)
- [erp-ui/src/views/mobile/feature/mobileFormPayloads.js](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/Grok实施工作副本_20260912/erp-ui/src/views/mobile/feature/mobileFormPayloads.js>)
- [erp-ui/src/api/inventory/sales.js](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/Grok实施工作副本_20260912/erp-ui/src/api/inventory/sales.js>)

拟新增：`sql/erp_inventory_sales_revision_20260913.sql`。

可复用机制：销售Mapper已有selectInvSalesOrderByIdForUpdate；通知创建已先锁销售单。；较新InvSalesReturnServiceImpl.lockScopedReturn对旧/新源单排序后锁退货头；当前通知cancelNotice→recalcSalesOrderStatus反向锁需同时调整。

依赖/并行边界：I-SALES首批基础，之后合入INV-N01/INV-N02；与root公共幂等P05修复协调，但业务行锁不能被请求幂等替代。

验收与失败恢复：

- [MySQL双事务] 取消先读submitted、创建通知先获锁，以及反向交错两种顺序：最终不得出现cancelled销售单+有效pending通知。
- [MySQL双事务] 编辑旧draft对并发提交/生成通知，两顺序都应由锁后状态或版本拒绝冲突；过期同状态编辑也不覆盖另一人的已保存明细。
- [MySQL双事务] 通知发货/取消/重算与销售退货确认同时执行，按锁顺序能结束或可识别重试，不出现通知→销售与销售→通知循环；库存、出库、成本、通知/销售状态守恒。
- [页面+兼容] PC/H5都回传版本；409保留输入、显示新状态；旧已打开页面缺版本需引导刷新后继续，不能悄悄当最新版本重试。

迁移与回退：

- 先在测试库加销售头version NOT NULL DEFAULT 0并验证Mapper列映射，再部署返回版本的后端与PC/H5版本传递。写接口强制expectedVersion需确认所有活跃客户端完成切换；兼容窗口只承诺行锁/状态保护，不宣称已解决同状态陈旧编辑。
- 回退不DROP version、不重置已有版本；回退前阻断旧写客户端或保留兼容校验实现，避免已验证并发保护重新消失。
- 不回滚已提交业务单、库存或退货账实事实；异常业务数据另开有备份的修复单。

产品口径：无；版本冲突保留输入并由用户重新确认，不改变既有业务可编辑/可取消状态。

[原问题证据](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/修复工作副本_2026-09-08/output/full-project-audit-20260913/inventory/findings.md:35>)；[本项设计来源](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/修复工作副本_2026-09-08/output/grok-repair-plan-20260913/inventory-design.json:265>)。

#### INV-N05 · P1 · 调拨删除与提交竞争可删除已经冻结库存的单据

原触发条件：调拨删除先读草稿，另一命令完成提交/冻结后，旧删除继续删主单和明细。

已接受的条件与边界：删除/提交需要发生并发交错；缺少共同互斥的源码链路成立，尚无隔离 MySQL 两事务交错实测。

原证据层级：`static-concurrency-chain`。双副本删除、冻结、命令账本与约束静态闭环；未跑真实MySQL并发。

保留规则：

- 允许管理草稿/已取消单的现有方向和组织权限保留；已发生业务事实不通过删除抹除。
- 旧48的调拨草稿版本、预留台账、审批发起/回调/outbox必须保留。

具体改法：

1. deleteTransfer改为先用现有selectInvTransferOrderByIdForUpdate锁调拨头，锁后重新检查组织、方向、draft/cancelled及原版本；提交/草稿保存也在头锁内读状态/版本，CAS保留为第二道防线。
2. 在删除任何子行之前检查当前/历史reservation、shipment、discrepancy等：尚有reserved-consumed-released>0或已形成发收/差异业务事实则拒绝删除；未结冻结不能在删除函数里偷偷释放。已取消但有历史效果引导归档/查看，保留事实。
3. 删除DML附允许状态和expectedVersion，检查命中1行；所有前置检查和明细/主单删除处于同一事务。不存在请求若命令账本已有成功记录由现有replay返回，而不是重复删除失败。
4. 继续复用InvTransferCommandService.deleteDraft→InvTransferCommandExecutor的requestId/fingerprint/result账本，保留源resourceKey；不同requestId由调拨头行锁串行，不给resource_key加会阻断所有后续命令的永久唯一约束。
5. 统一与收发、取消、差异处置相交链的顺序为调拨根头→子单/差异/明细→预留→库存；含销售来源调拨时更上层销售锁由I-SALES优先取得。

已存在相关文件：

- [erp-modules/erp-inventory/src/main/java/com/erp/inventory/service/impl/InvTransferServiceImpl.java](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/Grok实施工作副本_20260912/erp-modules/erp-inventory/src/main/java/com/erp/inventory/service/impl/InvTransferServiceImpl.java>)
- [erp-modules/erp-inventory/src/main/java/com/erp/inventory/service/impl/InvTransferCommandService.java](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/Grok实施工作副本_20260912/erp-modules/erp-inventory/src/main/java/com/erp/inventory/service/impl/InvTransferCommandService.java>)
- [erp-modules/erp-inventory/src/main/java/com/erp/inventory/service/impl/InvTransferCommandExecutor.java](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/Grok实施工作副本_20260912/erp-modules/erp-inventory/src/main/java/com/erp/inventory/service/impl/InvTransferCommandExecutor.java>)
- [erp-modules/erp-inventory/src/main/java/com/erp/inventory/service/impl/InvTransferReservationService.java](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/Grok实施工作副本_20260912/erp-modules/erp-inventory/src/main/java/com/erp/inventory/service/impl/InvTransferReservationService.java>)
- [erp-modules/erp-inventory/src/main/java/com/erp/inventory/mapper/InvTransferOrderMapper.java](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/Grok实施工作副本_20260912/erp-modules/erp-inventory/src/main/java/com/erp/inventory/mapper/InvTransferOrderMapper.java>)
- [erp-modules/erp-inventory/src/main/resources/mapper/inventory/InvTransferOrderMapper.xml](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/Grok实施工作副本_20260912/erp-modules/erp-inventory/src/main/resources/mapper/inventory/InvTransferOrderMapper.xml>)
- [erp-modules/erp-inventory/src/main/resources/mapper/inventory/InvTransferReservationMapper.xml](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/Grok实施工作副本_20260912/erp-modules/erp-inventory/src/main/resources/mapper/inventory/InvTransferReservationMapper.xml>)
- [erp-ui/src/api/inventory/transfer.js](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/Grok实施工作副本_20260912/erp-ui/src/api/inventory/transfer.js>)
- [erp-ui/src/views/inventory/transfer/index.vue](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/Grok实施工作副本_20260912/erp-ui/src/views/inventory/transfer/index.vue>)
- [sql/erp_inventory_transfer_command_20260802.sql](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/Grok实施工作副本_20260912/sql/erp_inventory_transfer_command_20260802.sql>)
- [sql/erp_inventory_transfer_reservation_20260802.sql](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/Grok实施工作副本_20260912/sql/erp_inventory_transfer_reservation_20260802.sql>)

可复用机制：已有调拨version/updateDraftIfVersionMatches、头ForUpdate、reservation版本及命令账本；账本只保证同requestId重放，不能跨不同动作互斥。

依赖/并行边界：I-TRANSFER锁协议先于INV-N09补发接线；与I-SALES销售来源调拨顺序一并审查。

验收与失败恢复：

- [MySQL双事务] 删除旧draft与另requestId提交/冻结交错：删除先赢则提交失败且没有预留/outbox；提交先赢则删除拒绝，主单/明细/冻结完整保留。
- [MySQL] cancelled但仍未结预留、已有发货/差异历史拒删且无部分子行删除；纯未使用草稿可删。
- [MySQL+接口] 相同requestId重复删除返回原成功结果；同ID不同fingerprint拒绝；不同requestId不能绕过同一调拨行锁。
- [页面] 删除冲突保留当前列表与筛选，显示单据已提交/已有冻结等真实原因及查看入口，不提示重复点删除。

迁移与回退：

- 核心复用已有version和台账，无必要新增DDL；实现前核对实际数据库旧迁移已存在。
- 不清空reservation或命令账本来实现回退；存量孤儿预留先只读清单、与单据/审批/出库对账后单独恢复。
- 回退需保留删除守卫或临时隐藏删除写入口，避免恢复已知竞争。

产品口径：无；本项保护已有事实，不重新定义调拨审批和库存释放时机。

[原问题证据](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/修复工作副本_2026-09-08/output/full-project-audit-20260913/inventory/findings.md:43>)；[本项设计来源](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/修复工作副本_2026-09-08/output/grok-repair-plan-20260913/inventory-design.json:328>)。

#### INV-N09 · P1 · 非质检短少选择“安排补发”后未补充冻结库存，原预留耗尽时正常补发受阻

原触发条件：正式非质检短少选择补发，原冻结量已耗尽；减少已发数但未增加补发预留，下一次正常发货报冻结不足。

已接受的条件与边界：限非质检短少选择安排补发、原预留已经耗尽的分支；不扩展到正常已有预留或其他短少处理。

原证据层级：`in-memory-sql-and-static-conservation-chain`。正式PC/API/命令/处理器链和库存守恒；实际mapper SQL内存执行，Java判断建模；无MySQL业务交易。

保留规则：

- 严格限定已复核非质检SHORTAGE→RESHIP；不扩大到PENDING_QC、质检补发、RETURN_SOURCE或未接线V2 effect owner。
- 未检不入可用库存；补发预留不代表自动发货/自动审批。

具体改法：

1. 仅在正式legacy非质检SHORTAGE→RESHIP的plan.newRows上汇总每个transferDetailId本次新增补发量。复用resources.transferReservationService.reserveForReshipment，在同一现有MANDATORY外层事务中先补充冻结/预留，再减少deliveredQuantity、写处置台账、更新差异/主单状态。
2. 守恒目标：补发q使reservation.reserved+=q、stock.locked+=q、stock.available-=q；consumed/released及stock.current、实际出库成本不在差异处置时回退。reserved-consumed-released增加q，之后真实发货q才消费这份预留、扣实际库存并记成本/出库流水。
3. 保持现有外层requestId+fingerprint命令账本和处置requestId精确重放、discrepancy.version CAS；只对newRows执行库存效果。相同请求重放返回原结果，不重复increaseReservation；不同请求ID处理已终结差异应被锁后状态/版本拒绝。
4. 锁顺序对齐I-TRANSFER：先用只读关联定位transferId，锁调拨根→差异并复核归属→明细/预留（稳定排序）→库存。当前processor先锁差异再锁调拨头须同步调整，不能只插一行reserve调用。
5. 补发库存不足或任一版本更新/台账写入失败时整个事务回滚，差异仍待处理、delivered未减、没有新增冻结；UI就近显示物料与缺口，可补库存后重试/按既有允许决策处理，不先提示“处置成功”。
6. 对已经处置成功但漏冻结的历史记录只生成对账建议：以处置ID/明细为修复幂等键，核对现有预留、后续补发和释放后仅补真实缺口；不要重跑旧处置去再次减已发。修复需单独审批/备份及原始审计。

已存在相关文件：

- [erp-modules/erp-inventory/src/main/java/com/erp/inventory/service/impl/InvTransferDiscrepancyProcessor.java](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/Grok实施工作副本_20260912/erp-modules/erp-inventory/src/main/java/com/erp/inventory/service/impl/InvTransferDiscrepancyProcessor.java>)
- [erp-modules/erp-inventory/src/main/java/com/erp/inventory/service/impl/InvTransferWorkflowResources.java](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/Grok实施工作副本_20260912/erp-modules/erp-inventory/src/main/java/com/erp/inventory/service/impl/InvTransferWorkflowResources.java>)
- [erp-modules/erp-inventory/src/main/java/com/erp/inventory/service/impl/InvTransferReservationService.java](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/Grok实施工作副本_20260912/erp-modules/erp-inventory/src/main/java/com/erp/inventory/service/impl/InvTransferReservationService.java>)
- [erp-modules/erp-inventory/src/main/java/com/erp/inventory/service/impl/InvTransferCommandService.java](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/Grok实施工作副本_20260912/erp-modules/erp-inventory/src/main/java/com/erp/inventory/service/impl/InvTransferCommandService.java>)
- [erp-modules/erp-inventory/src/main/java/com/erp/inventory/service/impl/InvTransferCommandExecutor.java](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/Grok实施工作副本_20260912/erp-modules/erp-inventory/src/main/java/com/erp/inventory/service/impl/InvTransferCommandExecutor.java>)
- [erp-modules/erp-inventory/src/main/java/com/erp/inventory/service/impl/InvTransferServiceImpl.java](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/Grok实施工作副本_20260912/erp-modules/erp-inventory/src/main/java/com/erp/inventory/service/impl/InvTransferServiceImpl.java>)
- [erp-modules/erp-inventory/src/main/resources/mapper/inventory/InvTransferReservationMapper.xml](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/Grok实施工作副本_20260912/erp-modules/erp-inventory/src/main/resources/mapper/inventory/InvTransferReservationMapper.xml>)
- [erp-modules/erp-inventory/src/main/resources/mapper/inventory/InvTransferDiscrepancyDispositionMapper.xml](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/Grok实施工作副本_20260912/erp-modules/erp-inventory/src/main/resources/mapper/inventory/InvTransferDiscrepancyDispositionMapper.xml>)
- [erp-modules/erp-inventory/src/main/resources/mapper/inventory/InvTransferDetailMapper.xml](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/Grok实施工作副本_20260912/erp-modules/erp-inventory/src/main/resources/mapper/inventory/InvTransferDetailMapper.xml>)
- [erp-ui/src/views/inventory/transfer/index.vue](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/Grok实施工作副本_20260912/erp-ui/src/views/inventory/transfer/index.vue>)
- [erp-ui/src/api/inventory/transfer.js](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/Grok实施工作副本_20260912/erp-ui/src/api/inventory/transfer.js>)

可复用机制：reserveForReshipment已存在，MANDATORY事务，锁当前round预留/stock并做available与version校验；WorkflowResources已注入可直接使用。；processor已有plan.newRows、精确处置重放和version CAS；保持与外围command事务同成败。

依赖/并行边界：INV-N05锁协议先完成；旧48质检、附件、预留/审批outbox实现作为保留基线。

验收与失败恢复：

- [MySQL主案例] 发10/实收8/短少2，原reserved=consumed=10，源可用足够；处置后reserved=12、consumed=10、remaining=2、current不变；再发2可成功且remaining归0，出库成本只在实际发货扣一次。
- [MySQL失败注入] 库存不足、reserveStock/increaseReservation CAS失败、处置台账插入失败、提交前异常，差异/已发/冻结/预留全回滚。
- [MySQL双事务] 相同请求重试、同requestId不同body、不同requestId同version、处置与取消/发货并发：不双冻结、不双减已发、无负remaining或锁顺序环。
- [MySQL回归] 原计划尚有剩余冻结的部分发货场景，补发仍额外增加q，不挪用原计划份额；RETURN_SOURCE和QC/待检路径保持原规则。
- [页面] 缺货时留在差异界面保留已选决策和凭证，显示缺口；成功后进入本单待补发分组但仍由员工确认发货；响应未知保持同一个requestId及原始body重试，由现有命令账本回放，不另生requestId或要求新增第二套查询/幂等系统。

迁移与回退：

- 正常新请求复用现表和方法，无必需DDL；先核对台账迁移、version字段、数据库事务能力。
- 存量补偿不是正常发布自动迁移；单列清单和修复幂等日志方案，逐笔核对后执行，不能给所有RESHIP简单加quantity。
- 一旦已有新补发冻结，回退应用不能删/归零预留或回退consumed；仍须由正常发货/取消释放完成闭环，必要时暂停RESHIP按钮而保留账本读取。

产品口径：无；补发须冻结足额来源库存、失败不终结差异，沿用既有数量守恒规则。

[原问题证据](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/修复工作副本_2026-09-08/output/full-project-audit-20260913/inventory/findings.md:75>)；[本项设计来源](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/修复工作副本_2026-09-08/output/grok-repair-plan-20260913/inventory-design.json:558>)。


### B03 切单、切人和迟到响应

#### ROOT-01 · P1 · 手机采购申请可显示 A 单却审批 B 单，PC 编辑也有迟到覆盖

原触发条件：A 采购加载未完成时通过待办/通知切到 B，load 因 loading 直接返回；A 返回后读取当前 B 的 approvalInstanceId，导致 A 业务内容与 B 审批任务组合。PC 连续打开 A/B 后迟到 A 回包会覆盖 B 的未保存输入。 前提是 A 可读且 B 为当前用户可处理的 PENDING 任务、实例 RUNNING；未绕过后端候选人/权限校验。

已接受的条件与边界：正式待办默认携带 businessId、approvalTaskId、approvalInstanceId；A 可读且加载未完成时，同 path 切到用户本可处理的 B（PENDING / RUNNING）。A 内容与 B 任务错配，不等于越权。两副本 resolver 内存执行确认参数保留；未执行 Java decorate、Vue DOM 或真实审批。

原证据层级：`real-source-offline-probe-plus-stated-static-boundary`。双副本提取 load/currentTask/canAct/executeAction，实际显示 purchaseId=101、routeId=102，approve 被调用 B-task。PC openForm 也实测迟到覆盖。 B-task 为前端探针哨兵ID，仅证明选错审批API目标；线上本轮所见 OA 采购提交仍未开放，没有生产错批复现。

具体改法：

1. PC openForm 与 H5 load 接入 WF01 操作作用域。H5 将 businessId/taskId/instanceId/组织解析为一次不可变目标；新 B 目标必须发起自己的加载，不能因 A 的 loading 直接返回。采购详情和审批实例均使用这一目标，禁止在 A 回包后读取当时的 B 路由参数。
2. 详情、任务、实例全部读取完成且验证 businessCode/businessId/instanceId/taskId 一致后才开放审批。任务必须属于该实例，实例必须对应所显示采购单；不匹配时显示重新载入入口并禁用动作，不能仅以 taskId 存在作为就绪条件。
3. PC 开单立即失效前一表单代次，迟到成功、失败、finally 不得覆盖 B 的输入、错误、loading 或关闭 B。保存/校验/确认也冻结原单和表单代次，确认后再检查上下文。
4. 审批复用 WF02 的冻结动作和稳定 requestId；业务权限仍由原后端候选人、活动节点、组织等校验。处理后下一条仅显式导航，不能复用会触发快速审批的 continueNext。

已存在相关文件：

- [erp-ui/src/views/oa/purchase/index.vue](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/Grok实施工作副本_20260912/erp-ui/src/views/oa/purchase/index.vue>)
- [erp-ui/src/views/mobile/oa/purchaseApproval/index.vue](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/Grok实施工作副本_20260912/erp-ui/src/views/mobile/oa/purchaseApproval/index.vue>)
- [erp-ui/src/api/oa/purchase.js](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/Grok实施工作副本_20260912/erp-ui/src/api/oa/purchase.js>)
- [erp-ui/src/api/approval/monitor.js](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/Grok实施工作副本_20260912/erp-ui/src/api/approval/monitor.js>)
- [erp-ui/src/api/approval/task.js](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/Grok实施工作副本_20260912/erp-ui/src/api/approval/task.js>)
- [erp-ui/src/utils/todoRouteResolver.js](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/Grok实施工作副本_20260912/erp-ui/src/utils/todoRouteResolver.js>)
- [erp-ui/src/utils/todoActionReturn.js](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/Grok实施工作副本_20260912/erp-ui/src/utils/todoActionReturn.js>)
- [erp-modules/erp-approval/src/main/java/com/erp/approval/service/ApprovalTaskService.java](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/Grok实施工作副本_20260912/erp-modules/erp-approval/src/main/java/com/erp/approval/service/ApprovalTaskService.java>)

依赖/并行边界：WF01；WF02；WF03

验收与失败恢复：

- A 可读且加载未完成，跳同路径 B（B 原本可批）并令 B 先返回：显示/任务/实例/POST 全部为 B；A 的成功、403、超时、finally 均不能改 B。
- 在 A 确认弹框期间切 B，点确认不得发 A 或 B 的新 POST；取消确认无业务失败提示。
- 任务不属于实例、实例不属于业务单、业务参数缺失分别禁用审批；直接伪造参数仍被后台校验。
- PC 开 A 再开 B 并编辑输入，A 后返回不覆盖、不关窗；切组织、登出、deactivated 同样失效。
- 从处理结果点下一条只产生 GET/导航，断言三个审批 POST 调用数均不增加。

迁移与回退：

- 无数据库迁移；按页面适配可回退。不能回退成展示已加载、任务取当前路由的混合目标；若新守卫异常，关闭动作并允许重新读取。

产品口径：无需新增产品决定，保留已确认规则。

[原问题证据](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/修复工作副本_2026-09-08/output/full-project-audit-20260913/root/findings.md:5>)；[本项设计来源](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/修复工作副本_2026-09-08/output/grok-repair-plan-20260913/workflow-design.json:188>)。

#### ROOT-06 · P2 · 请假、补卡及历史工资查询会被旧回包改成不符合当前筛选的结果

原触发条件：快速从审批中切已通过，或从八月工资切九月，后一请求先返回、旧请求后返回。当前条件保留新值，列表被旧结果覆盖；历史薪资方案快速A/B切换的档位明细也缺相同保护。

已接受的条件与边界：保留原报告的触发条件、当前/较新副本对照和证据边界；未新增生产事故或实际环境验收结论。

原证据层级：`real-source-offline-probe-plus-stated-static-boundary`。当前和较新实际 CorrectionManagement.load、LeaveManagement.loadRequests、OaSalary.getList 6次内存探针均输出筛选与列表不一致；历史方案明细为静态链路。

具体改法：

1. 补卡、请假、个人/组织工资列表及历史薪资方案档位读请求接入 WF01 的独立 lane。一次请求冻结全部过滤/分页/月份/方案 ID 和 actor/session/组织上下文；then/catch/finally 一律只认当前令牌。
2. 过滤变化清理分页和不属于当前集合的选择，立即启动新读取；旧 loading 不阻止新月份/新方案。历史方案列表与档位详情各用独立 lane，档位 loadedPlanId 必须匹配显示的方案。
3. 加载失败保留明确标注的上次结果或清空，禁用依赖新筛选的选择操作，并给就地重试；旧请求失败不弹错也不清理新数据。工资当前上下文失权时清理敏感数据，旧账号失权回包不得清理新账号页面。

已存在相关文件：

- [erp-ui/src/views/oa/attendance/components/CorrectionManagement.vue](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/Grok实施工作副本_20260912/erp-ui/src/views/oa/attendance/components/CorrectionManagement.vue>)
- [erp-ui/src/views/oa/attendance/components/LeaveManagement.vue](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/Grok实施工作副本_20260912/erp-ui/src/views/oa/attendance/components/LeaveManagement.vue>)
- [erp-ui/src/views/oa/salary/index.vue](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/Grok实施工作副本_20260912/erp-ui/src/views/oa/salary/index.vue>)
- [erp-ui/src/views/system/salary/index.vue](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/Grok实施工作副本_20260912/erp-ui/src/views/system/salary/index.vue>)
- [erp-ui/src/api/oa/salary.js](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/Grok实施工作副本_20260912/erp-ui/src/api/oa/salary.js>)
- [erp-ui/src/api/system/salaryConfig.js](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/Grok实施工作副本_20260912/erp-ui/src/api/system/salaryConfig.js>)

依赖/并行边界：WF01

验收与失败恢复：

- 八月 A 慢九月 B 快、审批中 A 慢已通过 B 快：列表/total/页码/导出条件始终对应 B。
- A 成功、A 403、A finally 分别晚于 B 开始或结束，不能覆盖 B 或提前关 B loading。
- 方案 A/B 档位乱序、同目标重新加载、切组织/账号/路由和 keep-alive 恢复均正确。
- 新查询失败可按同快照重试，空结果总数为零且分页不保留超界页；失败列表选择不被用于批量操作。

迁移与回退：

- 前端为主，无 DB 迁移；API 可选 config 参数向后兼容。逐入口灰度接入，不全局改 Axios 或重写已验证模块。

产品口径：无需新增产品决定，保留已确认规则。

[原问题证据](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/修复工作副本_2026-09-08/output/full-project-audit-20260913/root/findings.md:91>)；[本项设计来源](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/修复工作副本_2026-09-08/output/grok-repair-plan-20260913/workflow-design.json:345>)。

#### A06 · P1 · PC 采购退货切换原单后被旧响应换回旧单

原触发条件：PC先选择原采购A再改B，B先回、A晚回后，原单/明细恢复A而标题仍B。

已接受的条件与边界：保留原报告的触发条件、当前/较新副本对照和证据边界；未新增生产事故或实际环境验收结论。

原证据层级：`real-vue-offline-probe-plus-static-chain`。三个副本真实组件方法内存复现；后端识别有效A单的结论为静态链路。

具体改法：

1. 采购退货原单读取使用 WF01 source lane，change 原单即推进代次，冻结所选 purchaseOrderId/组织；只有 source 返回 ID 与当前目标一致才设置来源派生字段、行明细和 loadedSourceId。
2. 保留较新副本已有的退货专用 source-orders/source-order API 与 returnSelection 选中行规则，不能从当前旧实现整段拷贝回宽泛采购详情/全行处理。只失效原单派生数据，手填标题/退货原因不被 A 回包覆盖。
3. 来源尚未就绪或 loadedSourceId 不匹配时禁用保存；源单变化若已有退货数量编辑，沿用脏表单确认后换源并明确重新选择数量。保存冻结退货单、原单和实际选中行，确认后/回包再验上下文。

已存在相关文件：

- [erp-ui/src/views/inventory/purchaseReturn/index.vue](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/Grok实施工作副本_20260912/erp-ui/src/views/inventory/purchaseReturn/index.vue>)
- [erp-ui/src/api/inventory/purchaseReturn.js](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/Grok实施工作副本_20260912/erp-ui/src/api/inventory/purchaseReturn.js>)

依赖/并行边界：WF01

验收与失败恢复：

- 先选 A 再选 B，B 快 A 慢：原单、明细、来源状态均为 B，手填标题/原因保持；A 错误/finally 不动 B。
- 新原单未加载、返回 ID 不符、来源权限变化均不能保存混合数据。
- 带已填数量切源取消，保留原单与输入；确认切源后只使用新源可退行。
- 选择 1/3 行只提交这一行，未选行不被默认纳入；现有专用来源权限测试继续通过。

迁移与回退：

- 无迁移；以较新专用来源 API 和选行修复为集成基线。当前副本应移入其依赖，而非退回旧宽泛采购接口。

产品口径：无需新增产品决定，保留已确认规则。

[原问题证据](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/修复工作副本_2026-09-08/docs/reports/项目逻辑与操作便利性复核-20260913.md:46>)；[本项设计来源](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/修复工作副本_2026-09-08/output/grok-repair-plan-20260913/workflow-design.json:691>)。

#### A14 · P2 · 连续打开不同报销待办，详情未随目标更新

原触发条件：从待办连续打开同路径不同报销业务/审批目标，当前仅首次创建打开；较新fullPath只失效旧读而未重新打开目标。

已接受的条件与边界：限 PC 报销同路径不同待办目标，旧读失效不等于打开新目标；现有证据为路由/组件静态链，未做真实浏览器待办 A→B。

原证据层级：`static-route-component-chain`。路由、AppMain复用、组件监听和待办入口静态闭环；未做浏览器实测。

具体改法：

1. PC 报销监听规范化业务目标（reimbursementId/businessId、审批 task/instance、组织），同路径 query 变化显式调用 openRouteTarget；较新 fullPath 失效旧请求逻辑继续保留但不能代替打开新目标。
2. 列表加载与待办详情拆开，列表失败不阻止有明确目标的详情 GET。activated 比对上次已加载目标，不重复弹同一目标也不漏新目标；关闭、deactivated、target 移除统一失效。
3. 有未保存编辑时走既有脏表单确认，冻结将跳转的目标，取消则保留 A；确认后只加载最后明确接受的 B。目标已失权/不存在显示当前目标错误和返回待办，不能继续留 A 却显示 B 路由。
4. 复用 todoRouteParams/todoActionReturn/todoNavigator/contextLease 的上下文返回。下一条按钮只按 nextTodoKey/nextTodoRow 导航，旧 continueNext 若含自动审批须隔离成独立导航函数。

已存在相关文件：

- [erp-ui/src/views/oa/reimbursement/index.vue](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/Grok实施工作副本_20260912/erp-ui/src/views/oa/reimbursement/index.vue>)
- [erp-ui/src/utils/todoRouteParams.js](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/Grok实施工作副本_20260912/erp-ui/src/utils/todoRouteParams.js>)
- [erp-ui/src/utils/todoActionReturn.js](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/Grok实施工作副本_20260912/erp-ui/src/utils/todoActionReturn.js>)
- [erp-ui/src/utils/todoNavigator.js](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/Grok实施工作副本_20260912/erp-ui/src/utils/todoNavigator.js>)
- [erp-ui/src/utils/todoContextLease.js](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/Grok实施工作副本_20260912/erp-ui/src/utils/todoContextLease.js>)
- [erp-ui/src/layout/components/AppMain.vue](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/Grok实施工作副本_20260912/erp-ui/src/layout/components/AppMain.vue>)

依赖/并行边界：WF01；WF03

验收与失败恢复：

- 从待办先开 A 再开同路径 B，不重新创建组件仍显示 B；A 后返回不覆盖。
- keep-alive 离开后携 B 激活、列表加载失败、仅 task/instance 变化均按目标正确更新。
- 脏 A 跳 B 取消保留 A，确认加载 B；确认期间另到 C 不误提交/覆盖。
- B 不存在/失权，清除不再匹配的 A 动作并显示 B 错误；下一条只导航，断言没有审批 POST。

迁移与回退：

- 无数据迁移，不靠把整个 AppMain 改成 fullPath key 强制销毁所有页面；局部目标监听与现有返回上下文兼容。

产品口径：无需新增产品决定，保留已确认规则。

[原问题证据](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/修复工作副本_2026-09-08/docs/reports/项目逻辑与操作便利性复核-20260913.md:54>)；[本项设计来源](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/修复工作副本_2026-09-08/output/grok-repair-plan-20260913/workflow-design.json:854>)。

#### HR-N03 · P2 · 快速切换公司时旧印章响应覆盖新公司，编辑会保存到另一家公司

原触发条件：先打开甲公司印章，甲请求较慢；切到乙公司，乙请求先完成、甲请求后完成；此时在乙公司标题下编辑列表印章。

已接受的条件与边界：保留原报告的触发条件、当前/较新副本对照和证据边界；未新增生产事故或实际环境验收结论。

原证据层级：`real-source-offline-probe-plus-stated-static-boundary`。E3：probes.cjs 调用两副本真实 Vue 方法，内存 API：requestedCompanies=[A,B]，visibleCompany=乙公司，displayedSeal=甲公司章，saveTarget={sealId:sealA,legalEntityId:A}。后端为静态确认。

具体改法：

1. 加载印章前冻结legalEntityId和listGeneration；切公司、关闭、重开、销毁使旧代次失效并立即清掉旧公司可操作行。只有同公司同代次的成功/失败/finally可修改seals/error/loading。
2. 打开编辑前校验row.legalEntityId等于当前列表公司，不用Object.assign允许row覆盖编辑所属公司。保存冻结sealId、legalEntityId、editGeneration和payload；标题也用冻结公司。成功只关闭原编辑器，刷新仍处于原公司的列表；错误只写原编辑器，迟到响应不影响下一公司。
3. 后端保留现有公司和印章归属、HR权限校验；前端请求归属保护不能替代授权。复用既有begin/invalidate/isCurrent模式，按列表读与编辑写分别编号，避免共用一个loading误锁下一操作。

已存在相关文件：

- [erp-ui/src/views/oa/signPackage/CompanySealManagement.vue](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/Grok实施工作副本_20260912/erp-ui/src/views/oa/signPackage/CompanySealManagement.vue>)
- [erp-modules/erp-oa/src/main/java/com/erp/oa/service/impl/OaSignCompanyService.java](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/Grok实施工作副本_20260912/erp-modules/erp-oa/src/main/java/com/erp/oa/service/impl/OaSignCompanyService.java>)
- [erp-ui/src/views/mobile/feature/mobileRouteLoadGuard.js](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/Grok实施工作副本_20260912/erp-ui/src/views/mobile/feature/mobileRouteLoadGuard.js>)

依赖/并行边界：共享异步上下文批次；与S04批量盖章同模式但保持不同验收入口

验收与失败恢复：

- [真实方法/假API] A慢B快、旧A成功/失败/finally、关闭重开A、保存A时切B，B列表/标题/草稿/按钮状态均不被改；传入错公司旧row应在请求前拒绝。
- [浏览器] 公司连点、遮罩/ESC、编辑打开、真实延迟与保存失败恢复，确认保存请求公司与可见冻结标题一致。
- [后端集成] 错配sealId/companyId、失去权限返回失败，原有合法路径正常；不需改业务库结构。

迁移与回退：

- 无数据迁移；历史印章只作只读归属盘点，不自动挪公司。
- 前后端接口兼容，可单独回退UI提交包；恢复旧UI将重现风险，应以切公司入口限制作为临时退路。

产品口径：无须新增产品决定；按已确认规则与本方案实施。

[原问题证据](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/修复工作副本_2026-09-08/output/full-project-audit-20260913/hr/findings.md:56>)；[本项设计来源](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/修复工作副本_2026-09-08/output/grok-repair-plan-20260913/hr-design.json:224>)。

#### HR-N12 · P2 · PC入职状态操作的旧响应覆盖刚切换的另一人详情

原触发条件：对 A 标记就绪、退回草稿、恢复或取消，请求未返回时选择 B；B 详情已加载后 A 状态更新响应成功返回。状态操作 loading 没有阻止左侧选择。

已接受的条件与边界：保留原报告的触发条件、当前/较新副本对照和证据边界；未新增生产事故或实际环境验收结论。

原证据层级：`real-source-offline-probe-plus-stated-static-boundary`。E3：ui-supplement-probes.cjs 实际两副本 performStateAction 方法离线探针：selectedId=202，detailId=101，detailStatus=READY。后端响应类型和事务路径为 E2 交叉确认。

具体改法：

1. 状态动作发出前冻结onboardingId、version、actionSequence和详情/组织代次；同一个request后续成功、失败、冲突刷新和finally只属于这一快照。
2. 允许HR切换查看B；A完成只更新A列表行或提示“A操作已完成”，不能无条件detail=updated。若B仍当前，不调用会把selectedOnboardingId强行改回A的refreshAfterVersionConflict；仅原详情仍当前时读回A。
3. 列表刷新保留当前筛选和选中B；动作busy按目标/代次管理，旧A finally不能解除B新动作的loading。确认框返回后也检查原目标/权限/版本，避免确认A期间切B后发错请求。

已存在相关文件：

- [erp-ui/src/views/hr/onboarding/index.vue](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/Grok实施工作副本_20260912/erp-ui/src/views/hr/onboarding/index.vue>)
- [erp-ui/src/views/hr/onboarding/components/HrOnboardingListPane.vue](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/Grok实施工作副本_20260912/erp-ui/src/views/hr/onboarding/components/HrOnboardingListPane.vue>)
- [erp-modules/erp-system/src/main/java/com/erp/system/controller/HrOnboardingController.java](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/Grok实施工作副本_20260912/erp-modules/erp-system/src/main/java/com/erp/system/controller/HrOnboardingController.java>)

可复用机制：同页loadDetail268-287已有detailRequestSequence+selectedOnboardingId保护，可扩展到写和冲突恢复。

依赖/并行边界：HR-ID精确ID工具与HR-ASYNC共用快照规则

验收与失败恢复：

- [真实方法] A状态操作四种动作分别延迟；切B后A成功/失败/版本冲突/finally均不改B内容、选择、错误和按钮；A同页完成正常刷新。
- [浏览器] 快速点行、分页/筛选、组织变更、确认框、页面销毁/返回；列表总数变化也不强制回旧人。
- [后端契约] 保留版本冲突和allowedActions，不放宽已确认/取消记录状态限制；不需要新业务事务模型。

迁移与回退：

- 无DB迁移；仅修回包归属及刷新策略。旧状态动作结果只能读回，不反向撤销已合法完成动作。

产品口径：无须新增产品决定；按已确认规则与本方案实施。

[原问题证据](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/修复工作副本_2026-09-08/output/full-project-audit-20260913/hr/findings.md:234>)；[本项设计来源](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/修复工作副本_2026-09-08/output/grok-repair-plan-20260913/hr-design.json:961>)。

#### HR-N14 · P2 · 档案保存中关闭并编辑下一人，上一人的成功回调会关掉新的编辑页

原触发条件：手机编辑 A 并保存，写请求等待期间点击仍可用的关闭/取消按钮，再打开 B 编辑；A 保存响应随后成功返回。PC 档案编辑也允许保存中关闭，并有相同未绑定目标的回调。

已接受的条件与边界：保留原报告的触发条件、当前/较新副本对照和证据边界；未新增生产事故或实际环境验收结论。

原证据层级：`real-source-offline-probe-plus-stated-static-boundary`。E3：两副本真实手机 saveProfile 方法探针：savedId=101，currentEditId=202，currentEditorOpen=false。PC 同类入口和回调静态确认，未冒称PC动态复现。

具体改法：

1. 编辑器每次打开/切员工/关闭递增editGeneration；保存冻结employeeId、patch、editGeneration和组织，不在后续回调读当前payload/ref来推断目标。
2. 成功只有同员工同代次编辑器才关闭和清本份草稿；否则仅更新A列表已保存字段/通知结果。PC保存后的getHrEmployee回读也绑定同一代次，不得将A详情写入已开B；失败只投递原表单fieldErrors，旧finally不清新保存loading。
3. 保存等待期间禁用同一编辑器重复提交；关闭/离开可保留原写入完成后的通知，但不能宣称关闭会取消已发送保存。新B草稿独立保存于内存，正常未保存离开按现有草稿规则提醒；不新增一次无意义“确认保存”。

已存在相关文件：

- [erp-ui/src/views/mobile/hr/employee/index.vue](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/Grok实施工作副本_20260912/erp-ui/src/views/mobile/hr/employee/index.vue>)
- [erp-ui/src/views/hr/components/HrEmployeeList.vue](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/Grok实施工作副本_20260912/erp-ui/src/views/hr/components/HrEmployeeList.vue>)
- [erp-ui/src/views/mobile/hr/components/MobileHrProfileEditor.vue](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/Grok实施工作副本_20260912/erp-ui/src/views/mobile/hr/components/MobileHrProfileEditor.vue>)
- [erp-ui/src/views/hr/components/HrProfileEditDrawer.vue](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/Grok实施工作副本_20260912/erp-ui/src/views/hr/components/HrProfileEditDrawer.vue>)
- [erp-ui/src/api/hr/employee.js](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/Grok实施工作副本_20260912/erp-ui/src/api/hr/employee.js>)

可复用机制：较新mobile已有editorReadEpoch/isCurrentEditorRead，当前/较新PC已有detailRequestSequence；写入不能仅复用共享读序号。

依赖/并行边界：HR-ID与HR-ASYNC公共上下文；与HR-UI02同类但各自PC/H5入口验收

验收与失败恢复：

- [手机+PC真实方法] A保存慢→关A开B→A成功/失败/读回失败/finally：B保持打开、输入/错误/按钮不变；A请求只含A。
- [浏览器] 手机遮罩/返回、PC抽屉关闭、切组织、页面keep-alive、A/B反复打开、同时两保存；原保存成功准确通知但不抢焦点。
- [API] 原字段权限、遮罩字段不覆盖、乐观并发校验保持；真实成功状态与刷新结果可区分，刷新失败不误报保存失败。

迁移与回退：

- 无DB迁移；不自动补偿撤销A已合法保存内容。
- 回退UI时先确保没有在途写/待恢复表单，保留用户草稿；不能以强制全页刷新隐藏异步问题。

产品口径：无须新增产品决定；按已确认规则与本方案实施。

[原问题证据](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/修复工作副本_2026-09-08/output/full-project-audit-20260913/hr/findings.md:279>)；[本项设计来源](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/修复工作副本_2026-09-08/output/grok-repair-plan-20260913/hr-design.json:1115>)。

#### HR-UI01 · P2 · 手机实体搜索的旧响应覆盖新关键词候选，导致错选或重复搜索

原触发条件：在采购供应商等 MobileEntityPicker 中先搜索 A，再输入并搜索 B；B 的请求先完成，A 的请求后完成（成功或失败）。搜索输入、搜索按钮和 Enter 未因旧请求等待而禁止发起新搜索。

已接受的条件与边界：保留原报告的触发条件、当前/较新副本对照和证据边界；未新增生产事故或实际环境验收结论。

原证据层级：`real-source-offline-probe-plus-stated-static-boundary`。当前与 latest 均提取生产 searchOptions/hydrateKeywordFromOptions/selectOption 真实方法。B 先返回时 keyword=供应商B、options=[B]；A 晚成功后 keyword 仍为供应商B、options=[A]，选中产生 input=A；A 晚失败则清空 B 的结果并显示加载失败。父表单 handleEntitySelect 将 option.value 写入对应业务 ID。

具体改法：

1. 普通实体查询引入requestSequence与实体/规范keyword/组织/相关field依赖/form会话快照；handleKeywordInput、entity/context变化、关闭或销毁均使旧请求失效。返回Promise以便调用与验证。
2. 成功、失败、finally只有快照相同才能更新options/loadError/loading/open；旧回包不能恢复已关闭候选。选中option同时校验options的查询代次和当前依赖，不能把旧实体候选写回新字段。
3. 保留已合法选中项及selectedOptionLabel，继续执行用户修改关键字时的selection-cleared；可防抖但不把防抖当并发保护。销售专用SalesReturnSourcePicker、客户快速新建和capability独立状态不回退，复用已有customerCapabilityRequestSequence守卫模式。

已存在相关文件：

- [erp-ui/src/views/mobile/feature/components/MobileEntityPicker.vue](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/Grok实施工作副本_20260912/erp-ui/src/views/mobile/feature/components/MobileEntityPicker.vue>)
- [erp-ui/src/views/mobile/feature/components/MobileFormSheet.vue](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/Grok实施工作副本_20260912/erp-ui/src/views/mobile/feature/components/MobileFormSheet.vue>)
- [erp-ui/src/views/mobile/feature/mobileEntityService.js](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/Grok实施工作副本_20260912/erp-ui/src/views/mobile/feature/mobileEntityService.js>)
- [erp-ui/src/views/mobile/feature/mobileFormConfigs.js](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/Grok实施工作副本_20260912/erp-ui/src/views/mobile/feature/mobileFormConfigs.js>)

可复用机制：同组件customerCapability查询224-242已用sequence+contextKey；sales专用来源已有epoch，应维持。

依赖/并行边界：HR-ASYNC同根思路可共用纯工具，但不重构所有页面；INV物料选项已有保护需保留

验收与失败恢复：

- [方法] 搜索A慢B快，A迟到成功/失败/finally不替换B候选、不擦错误或放开Bloading；输入未点搜索也使A失效，关闭后不自动弹开。
- [方法/浏览器] 同关键字换店/换实体/依赖单据/表单重开，旧选项不可提交；Enter与按钮同时点、IME输入、防抖、快速新建成功回填均正常。
- [PC/H5 API契约] 保留string ID和权限scope；服务不因客户端防抖扩大查询，真实供应商/客户业务提交用选中且可见对象。

迁移与回退：

- 无数据迁移；不清除用户业务单据选择，只有依赖变化导致无效的选择按既有规则提示清除。
- 回退UI不会改变服务器候选数据；禁止用禁止所有搜索操作的长期锁替代失效保护。

产品口径：无须新增产品决定；按已确认规则与本方案实施。

[原问题证据](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/修复工作副本_2026-09-08/output/full-project-audit-20260913/hr/final-ui-findings.md:5>)；[本项设计来源](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/修复工作副本_2026-09-08/output/grok-repair-plan-20260913/hr-design.json:1208>)。

#### HR-UI02 · P2 · 资产报修提交期间可另开草稿，旧成功回调关闭新草稿并导致重填

原触发条件：报修 A 点击上报后等待响应；点击仍可用的取消/关闭，再点新建填写 B；A 成功时无条件 repairOpen=false。随后再次点击新建时 openRepairForm 重置 B。

已接受的条件与边界：保留原报告的触发条件、当前/较新副本对照和证据边界；未新增生产事故或实际环境验收结论。

原证据层级：`real-source-offline-probe-plus-stated-static-boundary`。当前与 latest 的真实 openRepairForm/createRepairAssetRow/submitRepair 方法内存复现：只发出 A 的上报 payload；A 等待时 B 为 repairOpen=true 且描述为草稿B；A 成功后 repairOpen=false，B 仍短暂留内存；下一次新建的描述变为空。repairActionDisabled 只检查门店，不检查 saving。

具体改法：

1. 为报修草稿建立draftGeneration；openRepairForm/关闭/组织变化维护代次，提交冻结shopDeptId、form/items及本次代次。
2. 最小交互修复：发送期间本表单取消/遮罩/ESC/新建按钮禁用，提交前方法也检查saving；但仍必须在成功、失败、额度冲突回查、finally及组件销毁路径验证代次，不能只靠按钮禁用。
3. 失败保留本份故障描述、明细和图片；额度冲突按冻结门店刷新并显示可执行原因，不从后来this.form读取门店。成功只关闭发起的草稿并更新相关列表；如果程序化离开重开了B，也不得关B。未知结果按共同恢复文案标记待核对，不能自动重复上报。

已存在相关文件：

- [erp-ui/src/views/oa/fixedAsset/repair/index.vue](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/Grok实施工作副本_20260912/erp-ui/src/views/oa/fixedAsset/repair/index.vue>)
- [erp-ui/src/api/oa/fixedAsset.js](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/Grok实施工作副本_20260912/erp-ui/src/api/oa/fixedAsset.js>)

可复用机制：现submitRepair已有冻结payload，但回调/额度loadQuota仍读可变表单；扩展上下文即可，不必重写额度服务。

依赖/并行边界：HR-ASYNC；与ROOT-02整店配置不同入口、不重复修复验收

验收与失败恢复：

- [真实方法] A提交后试关/新建被当前saving守卫拦；强制模拟route离开重开B，A成功/失败/finally仍不影响B。
- [浏览器] 取消/遮罩/ESC/新建/路由离开四类入口、网络失败、额度冲突、上传图片与备注保留；失败重试不要求重填，loading恢复仅当前操作。
- [API/隔离环境] 保留后端额度占用与已有批量上报逻辑，不能通过放宽额度或删校验解决UI问题；本项没有证明后台重复上报，不新增这个事故结论。

迁移与回退：

- 无数据库迁移；不撤销已有合法报修。
- 若仅回退UI必须说明迟到回包风险；新增草稿元数据只在内存生命周期管理，不把图片URL/故障描述持久存入长期公共缓存。

产品口径：无须新增产品决定；按已确认规则与本方案实施。

[原问题证据](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/修复工作副本_2026-09-08/output/full-project-audit-20260913/hr/final-ui-findings.md:28>)；[本项设计来源](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/修复工作副本_2026-09-08/output/grok-repair-plan-20260913/hr-design.json:1291>)。

#### S01 · P2 · 公告旧保存响应覆盖后来打开的新草稿

原触发条件：公告A保存等待时关闭并新建B，A回包覆盖当前form并关窗。

已接受的条件与边界：保留原报告的触发条件、当前/较新副本对照和证据边界；未新增生产事故或实际环境验收结论。

原证据层级：`offline-vue-method`。两副本真实submitDraft方法与deferred API；只证明B草稿丢失，后台收到仍是A。

具体改法：

1. 公告新增/编辑、受众预览、保存分别使用 WF01 lane。保存前冻结 noticeId/版本/正文/受众与表单代次，form.validate 回调结束和网络回包都检查同一上下文。
2. 提交中锁定当前草稿关键输入，允许关闭但保留原操作回执；关闭/新建 B 后，A 成功不得覆盖 B、关闭 B 或重置 B saving，A 失败不得给 B 显示保存错误。
3. 已知 ID 的保存丢响应可重新读取该公告并比较版本及冻结字段，不能仅因版本变了就归因本次保存；无法确认时保留草稿并提示核对。新增丢响应未取得 ID 时不得盲目自动重建；本项不宣称现有公告有命令查询 API。
4. 保留现有“先存草稿、发布另确认”的业务规则；保存后就地给明确状态并保留继续编辑入口，减少重新搜索。

已存在相关文件：

- [erp-ui/src/views/system/notice/index.vue](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/Grok实施工作副本_20260912/erp-ui/src/views/system/notice/index.vue>)
- [erp-ui/src/api/system/notice.js](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/Grok实施工作副本_20260912/erp-ui/src/api/system/notice.js>)
- [erp-modules/erp-system/src/main/java/com/erp/system/controller/SysNoticeController.java](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/Grok实施工作副本_20260912/erp-modules/erp-system/src/main/java/com/erp/system/controller/SysNoticeController.java>)
- [erp-modules/erp-system/src/main/java/com/erp/system/service/impl/SysNoticeServiceImpl.java](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/Grok实施工作副本_20260912/erp-modules/erp-system/src/main/java/com/erp/system/service/impl/SysNoticeServiceImpl.java>)

依赖/并行边界：WF01

验收与失败恢复：

- A 保存未回关闭并新建 B，A 成功、失败、finally 均不覆盖/关闭/解锁 B。
- A 异步校验未回时切 B，不发 B 或 A 的错上下文 POST。
- 保存失败正文和受众保留，就地重试；提交后换账号/组织原响应不能写入新上下文。
- 发布仍需现有显式确认；普通保存不发布，取消发布不丢草稿。

迁移与回退：

- 无 DB 迁移；保持原 API 返回契约与发布规则。未取得 ID 的新增未知结果仍提示核对，不能夸称此次只靠前端作用域解决全部创建幂等。

产品口径：无需新增产品决定，保留已确认规则。

[原问题证据](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/修复工作副本_2026-09-08/output/full-project-audit-20260913/platform/supplemental-system-front.md:5>)；[本项设计来源](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/修复工作副本_2026-09-08/output/grok-repair-plan-20260913/workflow-design.json:489>)。

#### S02 · P2 · 参数管理搜索乱序后，列表与当前筛选不一致

原触发条件：参数先查A再查B，B先返回后被A晚回覆盖，筛选仍为B。

已接受的条件与边界：保留原报告的触发条件、当前/较新副本对照和证据边界；未新增生产事故或实际环境验收结论。

原证据层级：`offline-vue-method`。双副本真实getList方法内存复现；API原样传参。

具体改法：

1. 参数管理 getList 接入 WF01；可复用较新 systemListRecovery 的序号与激活恢复实现，但补齐会话代次和冻结筛选，不把其现状称完整写操作守卫。
2. 搜索/重置令页码回到 1 并生成新请求；保留当前请求的 rows/total/loading/error 原子更新。失败显示查询失败和重试入口；保留旧列表时显式标注且禁用基于新条件的批量操作。
3. 刷新只针对当前筛选，分页选择明确清理；从编辑返回按当前条件重新查询，不要求用户重复输入。

已存在相关文件：

- [erp-ui/src/views/system/config/index.vue](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/Grok实施工作副本_20260912/erp-ui/src/views/system/config/index.vue>)
- [erp-ui/src/api/system/config.js](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/Grok实施工作副本_20260912/erp-ui/src/api/system/config.js>)

依赖/并行边界：WF01

验收与失败恢复：

- A 查询慢 B 查询快，A 后到不改变 B rows/total/error/loading。
- A 失败在 B 成功之后、A finally 在 B 未完成时，均不能污染 B。
- 搜索、重置、分页、编辑后刷新与 keep-alive 恢复有一致参数；切账号/组织后旧请求失效。
- 失败可原位重试并保留筛选；删除目标只来自冻结选择。

迁移与回退：

- 无需迁移；仅该入口接入公共守卫，不修改其它参数 API 行为。

产品口径：无需新增产品决定，保留已确认规则。

[原问题证据](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/修复工作副本_2026-09-08/output/full-project-audit-20260913/platform/supplemental-system-front.md:13>)；[本项设计来源](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/修复工作副本_2026-09-08/output/grok-repair-plan-20260913/workflow-design.json:522>)。

#### S04 · P2 · 批量盖章缺少请求会话隔离，已生成结果可以显示在后来改选的公司上

原触发条件：批量盖章提交A公司期间改成B，回包只填结果；或关闭预检换批次后旧预检覆盖新批次。

已接受的条件与边界：保留原报告的触发条件、当前/较新副本对照和证据边界；未新增生产事故或实际环境验收结论。

原证据层级：`offline-vue-method-plus-static-service-check`。两副本两种真实组件方法探针；只证明呈现/请求上下文错配。后台公司印章/版本及不一致重试校验仍存在。

具体改法：

1. 预检用 WF01 独立 lane；关闭/改批次即使旧 previewLoading 为 true 也应加载新批次，旧预检 then/catch/finally 全失效。提交前将选择 taskId/expectedVersion/公司/印章/原因及批次 requestId 冻结为不可变命令。
2. 确认期间和提交期间都禁止批量顶部“设公司/设印章/应用”和逐行修改等能改变原命令的入口；confirm 后复核表单代次。不能仅禁用每行 select 而漏掉顶部操作。
3. 保留现有逐项成功/失败语义。结果绑定原 taskId+版本+公司/印章快照，在原命令结果区显示；不得将 A 公司已生成显示在后来 B 公司草稿上。失败项可以修改后作为新命令，已成功项不重复生成。
4. 复用现有 batch requestId→childRequestId、签约包 FINALIZE_REQUESTED 事件及 payloadHash 重放保护。同一未知项先 GET task/package 核对；当前状态若无法证明原命令只能 UNKNOWN，原 requestId+原逐项载荷安全重试由后端事件验证。现无批量命令 GET，不能把“包已完成”直接认定为本次成功。

已存在相关文件：

- [erp-ui/src/views/oa/signTask/SignTaskBatchFinalizeDialog.vue](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/Grok实施工作副本_20260912/erp-ui/src/views/oa/signTask/SignTaskBatchFinalizeDialog.vue>)
- [erp-ui/src/api/oa/signTask.js](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/Grok实施工作副本_20260912/erp-ui/src/api/oa/signTask.js>)
- [erp-ui/src/api/oa/signPackage.js](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/Grok实施工作副本_20260912/erp-ui/src/api/oa/signPackage.js>)
- [erp-modules/erp-oa/src/main/java/com/erp/oa/controller/OaSignTaskController.java](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/Grok实施工作副本_20260912/erp-modules/erp-oa/src/main/java/com/erp/oa/controller/OaSignTaskController.java>)
- [erp-modules/erp-oa/src/main/java/com/erp/oa/service/impl/OaSignTaskBatchFinalizeService.java](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/Grok实施工作副本_20260912/erp-modules/erp-oa/src/main/java/com/erp/oa/service/impl/OaSignTaskBatchFinalizeService.java>)
- [erp-modules/erp-oa/src/main/java/com/erp/oa/service/impl/OaSignPackageServiceImpl.java](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/Grok实施工作副本_20260912/erp-modules/erp-oa/src/main/java/com/erp/oa/service/impl/OaSignPackageServiceImpl.java>)

依赖/并行边界：WF01；WF02

验收与失败恢复：

- 提交 A 公司期间试用顶部批量应用改 B，控件与方法都拒绝；A 结果绑定 A 冻结快照。
- 确认框期间切批次/关闭/改公司，确认后不提交已变载荷；取消确认不提示错误。
- 关闭预检 A 再打开 B，A 迟到成功/错误/finally 不覆盖 B。
- 三项中一项失败，成功项明确保留，只重试失败/未知项且稳定 childRequestId；改变失败项公司必须新命令。
- 最终生成已提交但响应丢失，原请求重试不生成第二份；另一人不同公司完成时不能归因本次成功。

迁移与回退：

- 不改变现有逐项事务和事件表，不需要本项新增命令表；保留历史 FINAL_CONTRACT_GENERATED 兼容判断。UI 回退时保留后端请求号保护。

产品口径：无需新增产品决定，保留已确认规则。

[原问题证据](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/修复工作副本_2026-09-08/output/full-project-audit-20260913/platform/signing-front-cross-review.md:5>)；[本项设计来源](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/修复工作副本_2026-09-08/output/grok-repair-plan-20260913/workflow-design.json:587>)。

#### PLAT-UI01 · P2 · 用户详情切人时，旧敏感资料回包与新用户的部门、工号拼接

原触发条件：用户管理点击A账号查看详情；A公开详情返回并发起PII读取后关闭，打开B且B详情已完成；最后A的PII返回。需当前账号有公开及PII读取权限，A/B均在后台允许的用户数据范围内。

已接受的条件与边界：保留原报告的触发条件、当前/较新副本对照和证据边界；未新增生产事故或实际环境验收结论。

原证据层级：`real-source-offline-probe-plus-stated-static-boundary`。两个副本实际UserViewDrawer.open/handleClose方法配延迟API回包：lastOpenedUserId=202，却displayedUserId=101、name=A、department=B department、employeeNo=B-employee、idNumber=A-synthetic-identity。open先把getUser结果赋this.info，后续PII闭包不检查userId/打开代次，直接向当前this.info合并。前后端API/DTO/SQL已闭环；没有真实个人信息读取、浏览器DOM、写用户或越权复现。

具体改法：

1. 用户详情弹窗为公开信息和 PII 各建立受同一 openToken/userId 约束的读取。关闭立即清掉 PII/用户显示并使令牌失效，重新打开同人也产生新代次。
2. PII 回包先核对当前 actor/session/组织、目标 userId 和公开详情归属，再只合并允许的敏感字段到该次冻结公开详情；禁止把 A PII merge 到此刻的 this.info。
3. 当前 PII 403 显示“无权查看敏感资料”并保留该用户允许的公开信息；旧 A 的错误/401/403/finally 不得清空或解锁 B。后台公开/PII 各自的数据范围校验保持。

已存在相关文件：

- [erp-ui/src/views/system/user/view.vue](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/Grok实施工作副本_20260912/erp-ui/src/views/system/user/view.vue>)
- [erp-ui/src/api/system/user.js](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/Grok实施工作副本_20260912/erp-ui/src/api/system/user.js>)
- [erp-ui/src/store/modules/user.js](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/Grok实施工作副本_20260912/erp-ui/src/store/modules/user.js>)

依赖/并行边界：WF01

验收与失败恢复：

- A 公开已回、PII 慢，关 A 开 B 且 B 已完成，再回 A PII：B 的姓名、部门、工号及 PII 全部属于 B。
- A PII 403/网络/finally 迟到不污染 B；当前 PII 403 不显示旧值且公开区可用。
- 关闭立即清敏感值，重新打开同人、换组织/账号均不复用前代 PII。
- 有公开权限无 PII 权限、越范围用户均按后端拒绝；修复不扩展可见范围。

迁移与回退：

- 仅展示读取改动，无数据库迁移；不持久化 PII、不把敏感信息写入通用操作日志。

产品口径：无需新增产品决定，保留已确认规则。

[原问题证据](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/修复工作副本_2026-09-08/output/full-project-audit-20260913/platform/final-ui-findings.md:5>)；[本项设计来源](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/修复工作副本_2026-09-08/output/grok-repair-plan-20260913/workflow-design.json:624>)。

#### MOB-N01 · P2 · 异步树控件冷加载误清仍授权的当前组织

原触发条件：缓存组织仍在授权树里，但异步ElTree首次未挂载，页面把ref不存在当未授权并清选择。

已接受的条件与边界：有效组织仍获授权，异步 ElTree 冷加载时才触发；已有热加载反例。局部 Vue 异步组件执行不等于所有真机/生产组织切换均失败。

原证据层级：`local-vue-async-component-probe`。两副本真实Vue2方法/延迟异步组件在最小本地DOM复现，含热加载对照；线上看到相似警告但未锁定所有线上根因。

保留规则：

- 缓存组织不能代替授权查询；无权限组织仍必须清理。
- 公司/组织只展开，实际进入具体STORE/WAREHOUSE；不自动选择多个可选组织。

具体改法：

1. 在授权树请求成功后用既有findDeptNodeById(this.deptTree,cachedId)及isValidInventoryDeptType校验缓存组织，数据层生成selectedDeptId/name/type/path；有效即保留，不依赖$refs是否存在。
2. 把applySelectedDept拆成数据选中与UI高亮/展开/滚动两步。ElTree的hook:mounted/ready后用当前请求epoch和selectedId重放纯视觉同步；ref尚未就绪返回pending而不是unauthorized，不用无界setTimeout轮询。
3. 仅授权数据确定不含该组织、或类型已不再是有效门店/仓库时执行handleInvalidCachedDept。树请求失败/异步chunk失败时保留缓存并显示可重试错误，确认按钮直到数据验证完成才可使用。
4. 树请求携带账号/组织选择会话版本，登录用户变化或离页后旧响应/ready回调不能清新上下文；继续遵循context-free入口和待办临时组织租约的原有恢复规则。

已存在相关文件：

- [erp-ui/src/views/select-shop/index.vue](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/Grok实施工作副本_20260912/erp-ui/src/views/select-shop/index.vue>)
- [erp-ui/src/utils/shopContext.js](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/Grok实施工作副本_20260912/erp-ui/src/utils/shopContext.js>)
- [erp-ui/src/plugins/element-ui.js](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/Grok实施工作副本_20260912/erp-ui/src/plugins/element-ui.js>)
- [erp-ui/src/views/select-shop/shopEntryRouting.js](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/Grok实施工作副本_20260912/erp-ui/src/views/select-shop/shopEntryRouting.js>)
- [erp-ui/src/layout/components/Navbar.vue](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/Grok实施工作副本_20260912/erp-ui/src/layout/components/Navbar.vue>)
- [erp-ui/src/views/mobile/components/MobileWorkbenchShell.vue](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/Grok实施工作副本_20260912/erp-ui/src/views/mobile/components/MobileWorkbenchShell.vue>)

可复用机制：shopContext.js已有字符串ID兼容的findDeptNodeById；既有findUniqueBusinessDept、路由fallback和set/clearSelectedDept照用；无需把ElTree全局改同步加载。

依赖/并行边界：可独立先交付；与root组织/待办lease改动协调，不重置全局上下文体系。

验收与失败恢复：

- [Vue真实生命周期] 缓存83仍在树，冷异步ElTree迟到：清缓存0次、无失权提示，mounted后高亮83；热加载对照同结果。
- [页面慢网络] 首次进入/首页切换、清缓存chunk加载慢、树HTTP失败重试和chunk失败恢复都不额外要求重选有效组织。
- [页面+权限] 真正授权撤销、非法类型、跨账号旧树回包分别清理或丢弃正确上下文；重新进入仍受正式权限校验。
- [真机] iOS/Android浏览器冷缓存与返回/前后台测试视觉同步；不以本地Vue探针替代全设备验收。

迁移与回退：

- 无DDL，不调整用户授权；只变缓存有效性的判定时机。
- 回退不批量清用户当前组织，只失效新增UI-ready状态；当前选择仍经授权数据校验。

产品口径：无；修复条件仅为仍授权组织的冷加载误清。

[原问题证据](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/修复工作副本_2026-09-08/output/full-project-audit-20260913/inventory/mobile-common-supplement.md:7>)；[本项设计来源](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/修复工作副本_2026-09-08/output/grok-repair-plan-20260913/inventory-design.json:719>)。


### B04 失败反馈与原操作恢复

#### A11 · P2 · 报销审批／撤回失败无提示

原触发条件：PC/H5报销审批或撤回接口返回冲突/权限/网络错误，API静默而业务catch为空。

已接受的条件与边界：已有实际动态证据限 PC 同意遇 409；其他报销动作/错误保留静态边界。普通业务、权限或网络失败与取消、401、凭据限制、成功 CSRF 恢复应分别处理。GROK-01 的 112 次采购探针不能替代全部报销入口验收。

原证据层级：`mixed-offline-pc-probe-and-static-other-actions`。PC同意被409拒绝在三个副本方法探针复现无消息；H5/撤回/其它动作和错误类型为静态核对。

具体改法：

1. PC/H5 报销 approve/return/reject 接入 WF02；保留 API silentError:true，在页面处理未通知的业务错误，保留审批意见并展示可行动原因。401/凭据限制走已有统一处理不重复提示，普通 403/409/业务 500 与网络 UNKNOWN 分开。
2. 审批动作冻结 reimbursementId、approvalRound、instanceId、taskId、actor/session/组织、action/reason/requestId；GET /approval/instances/{id} 的 actions 必须与原动作全字段及实例业务绑定匹配才确认本次成功。别人已处理或不同动作仅显示当前状态，不能当作本次成功。
3. 撤回走现有报销 withdraw 服务，拟给 OaReimbursementWithdrawRequest 增加 expectedApprovalInstanceId/expectedApprovalRound 并传至加锁服务校验；当前 DTO 只有 reason。撤回确认之后不得读取已经换单/新轮次的当前实例作为原目标。
4. 撤回超时先 GET 原 reimbursementId，显示已撤回/仍待处理/已变化/未知；只有仍是原实例原轮次才允许显式重试原撤回。没有持久撤回命令回执时不能将“已撤回”断言为本次操作者执行成功。最终失败不清空意见，旧 finally 不解锁 B。

已存在相关文件：

- [erp-ui/src/views/oa/reimbursement/index.vue](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/Grok实施工作副本_20260912/erp-ui/src/views/oa/reimbursement/index.vue>)
- [erp-ui/src/views/mobile/oa/reimbursement/index.vue](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/Grok实施工作副本_20260912/erp-ui/src/views/mobile/oa/reimbursement/index.vue>)
- [erp-ui/src/api/oa/reimbursement.js](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/Grok实施工作副本_20260912/erp-ui/src/api/oa/reimbursement.js>)
- [erp-ui/src/api/approval/task.js](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/Grok实施工作副本_20260912/erp-ui/src/api/approval/task.js>)
- [erp-ui/src/api/approval/monitor.js](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/Grok实施工作副本_20260912/erp-ui/src/api/approval/monitor.js>)
- [erp-ui/src/utils/request.js](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/Grok实施工作副本_20260912/erp-ui/src/utils/request.js>)
- [erp-modules/erp-oa/src/main/java/com/erp/oa/domain/dto/OaReimbursementWithdrawRequest.java](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/Grok实施工作副本_20260912/erp-modules/erp-oa/src/main/java/com/erp/oa/domain/dto/OaReimbursementWithdrawRequest.java>)
- [erp-modules/erp-oa/src/main/java/com/erp/oa/controller/OaReimbursementController.java](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/Grok实施工作副本_20260912/erp-modules/erp-oa/src/main/java/com/erp/oa/controller/OaReimbursementController.java>)
- [erp-modules/erp-oa/src/main/java/com/erp/oa/service/IOaReimbursementService.java](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/Grok实施工作副本_20260912/erp-modules/erp-oa/src/main/java/com/erp/oa/service/IOaReimbursementService.java>)
- [erp-modules/erp-oa/src/main/java/com/erp/oa/service/impl/OaReimbursementServiceImpl.java](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/Grok实施工作副本_20260912/erp-modules/erp-oa/src/main/java/com/erp/oa/service/impl/OaReimbursementServiceImpl.java>)

依赖/并行边界：WF01；WF02；WF03

验收与失败恢复：

- PC/H5 三审批动作及撤回分别返回普通 HTTP/body 403、409、业务 500、网络/超时：提示一次、意见保留、忙状态恢复且 UNKNOWN 不提示肯定未执行。
- 确认前主动取消无提示无 POST；请求已发出后 Axios 取消必须核对结果，不能按用户取消确认静默丢弃。
- 原动作落库丢响应后读到完全匹配 actionlog：确认成功；别人同意、自己不同原因/动作、下一轮实例均不能冒认。
- 撤回请求卡住时表单切 B 或原报销开始新轮次，旧响应/重试不能撤回新实例；后端 expectedInstance/round 校验拒绝。
- body/HTTP 401、凭据 409/428、Cookie CSRF 首次恢复重放控制用例不出现重复提示。

迁移与回退：

- 审批复用现有 actionlog 无迁移。撤回增加条件字段采用后端先兼容、前端发送、观察旧客户端后收紧的分阶段契约；过渡期明确旧客户端仍缺新条件保护，不能宣布全入口验收完成。后端兼容服务保留，不回退为无条件重试。

产品口径：无需新增产品决定，保留已确认规则。

[原问题证据](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/修复工作副本_2026-09-08/docs/reports/项目逻辑与操作便利性复核-20260913.md:48>)；[本项设计来源](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/修复工作副本_2026-09-08/output/grok-repair-plan-20260913/workflow-design.json:726>)。

#### A12 · P2 · 导出下载失败后无法直接重下原批次

原触发条件：服务已建ZIP批次并标记导出，后续下载失败；前端丢局部批次ID，无原批次历史入口。

已接受的条件与边界：保留原报告的触发条件、当前/较新副本对照和证据边界；未新增生产事故或实际环境验收结论。

原证据层级：`real-vue-offline-probe-plus-static-chain`。三个副本真实方法内存探针加导出服务静态链路；未实际建包/下载。

具体改法：

1. 将“生成批次”和“下载原包”拆成两个可恢复阶段。取得 batchId 后立即保存最小批次摘要到页面恢复区，再发 GET 原下载；下载失败保留“重新下载此批次”，不重新 POST 创建、不再次 markExported、不要求改筛选找旧单。
2. 拟新增 GET /oa/reimbursement/finance/exports 历史分页入口（默认我的批次，管理员可按既有管理员规则查），复用现有按 batchId 下载权限，显示批次时间/创建人/数量/包状态。现有服务只有创建和按 ID 下载，没有历史列表，需要新增 mapper/SQL/API/UI。
3. 为创建响应也丢失提供稳定 requestId：拟给现有导出请求/批次记录增加 requestId 与载荷摘要及操作者唯一键；重复同命令返回原批次，改 ID 集合复用请求号拒绝。拟新增按本人 requestId 查询批次结果的只读接口；查不到为 NOT_OBSERVED，允许原命令显式重试，不推定未生成。
4. 历史批次中的 items 已存快照，重下载使用原 ZIP，不按当前业务数据重新拼包。文件确实缺失时明确“原包不可用”并让有权人员另行生成新批次（显式新动作），不伪装同批次恢复。下载按钮提示“已发起下载”，不宣称浏览器磁盘保存成功。
5. 保留当前默认“未导出”列表规则及标记含义=批次已生成；生成后从该列表消失时立即展示批次回执/历史入口，不把“从未导出列表消失”解释为数据删除。

已存在相关文件：

- [erp-ui/src/views/oa/reimbursement/index.vue](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/Grok实施工作副本_20260912/erp-ui/src/views/oa/reimbursement/index.vue>)
- [erp-ui/src/api/oa/reimbursement.js](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/Grok实施工作副本_20260912/erp-ui/src/api/oa/reimbursement.js>)
- [erp-modules/erp-oa/src/main/java/com/erp/oa/controller/OaReimbursementController.java](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/Grok实施工作副本_20260912/erp-modules/erp-oa/src/main/java/com/erp/oa/controller/OaReimbursementController.java>)
- [erp-modules/erp-oa/src/main/java/com/erp/oa/service/OaReimbursementExportService.java](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/Grok实施工作副本_20260912/erp-modules/erp-oa/src/main/java/com/erp/oa/service/OaReimbursementExportService.java>)
- [erp-modules/erp-oa/src/main/java/com/erp/oa/service/impl/OaReimbursementExportServiceImpl.java](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/Grok实施工作副本_20260912/erp-modules/erp-oa/src/main/java/com/erp/oa/service/impl/OaReimbursementExportServiceImpl.java>)
- [erp-modules/erp-oa/src/main/java/com/erp/oa/domain/OaReimbursementExportBatch.java](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/Grok实施工作副本_20260912/erp-modules/erp-oa/src/main/java/com/erp/oa/domain/OaReimbursementExportBatch.java>)
- [erp-modules/erp-oa/src/main/java/com/erp/oa/domain/dto/OaReimbursementExportRequest.java](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/Grok实施工作副本_20260912/erp-modules/erp-oa/src/main/java/com/erp/oa/domain/dto/OaReimbursementExportRequest.java>)
- [erp-modules/erp-oa/src/main/java/com/erp/oa/mapper/OaReimbursementMapper.java](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/Grok实施工作副本_20260912/erp-modules/erp-oa/src/main/java/com/erp/oa/mapper/OaReimbursementMapper.java>)
- [erp-modules/erp-oa/src/main/resources/mapper/oa/OaReimbursementMapper.xml](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/Grok实施工作副本_20260912/erp-modules/erp-oa/src/main/resources/mapper/oa/OaReimbursementMapper.xml>)

拟新增：`erp-modules/erp-oa/src/main/resources/db/migration/erp_oa_reimbursement_export_recovery_20260913.sql`。

依赖/并行边界：WF01；WF02；WF05

验收与失败恢复：

- 创建成功下载 503，当前未导出列表不再显示这些单，但恢复卡片/历史显示原 batchId；重下载不创建第二批次。
- 创建事务已提交且响应丢失，按原 requestId 查询或重复原命令只返回同一批次，标记/批次数不重复。
- 同 requestId 不同报销 ID 集合拒绝；跨操作者/越范围查询和下载按既有规则拒绝。
- 刷新页面、切分页后仍能在历史找到旧批次；旧历史 requestId 为空仍可列出下载。
- 原 ZIP 缺失、权限丢失、浏览器下载取消分别准确提示；创建中切组织/账号，旧回执不挂到新页面。

迁移与回退：

- 增量加可空 requestId/hash 和操作者唯一键，旧批次不重建不改导出标记；先数据库/API 后 UI。回退 UI 仍保留原批次下载入口和数据，不能删除原包或回滚 markExported 来制造“未导出”。

产品口径：无需新增产品决定，保留已确认规则。

[原问题证据](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/修复工作副本_2026-09-08/docs/reports/项目逻辑与操作便利性复核-20260913.md:50>)；[本项设计来源](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/修复工作副本_2026-09-08/output/grok-repair-plan-20260913/workflow-design.json:768>)。

#### A13 · P2 · 手机报销提交结果未知，却提示肯定未提交

原触发条件：手机报销已提交受理但丢响应，仍提示肯定尚未提交；原按钮重试先保存被非编辑态拒绝。

已接受的条件与边界：保留原报告的触发条件、当前/较新副本对照和证据边界；未新增生产事故或实际环境验收结论。

原证据层级：`real-vue-offline-probe-plus-static-chain`。三个副本真实组件方法模拟后端pending且响应丢失；保存状态限制静态确认。

具体改法：

1. 手机提交拆为保存草稿与提交原已保存草稿两个阶段，记录 savedId/保存版本/baseRound/targetRound=baseRound+1。提交请求发出后丢响应显示“提交结果待核对”，不再固定说“尚未提交”。
2. 先 GET 原 reimbursementId。现有服务已按 (reimbursementId, approvalRound) 用锁/outbox 处理 PENDING 返回和 SUBMITTING 触发恢复，优先复用；PENDING/相应目标轮次或 SUBMITTING 均显示实际受理进度，不先再次 persistFormDraft。
3. 若读回仍为原可编辑状态且版本/轮次未变，才允许显式重试原 submit 快照。为跨长时间重试避免退回后误发下一轮，给现有 submit 合约增加 expectedBaseRound/expectedVersion，并在锁内区分原轮次重放与新提交；这些额外条件当前不是独立命令查询能力。
4. 草稿保存本身失败、提交明确业务拒绝、提交 UNKNOWN 分别文案和按钮；已保存的附件/输入保留。继续编辑会废弃旧提交意图并生成新上下文，未知期间不能修改后沿用旧提交。使用 WF01 防止路由 A→B、登出或旧 finally 干扰新表单。

已存在相关文件：

- [erp-ui/src/views/mobile/oa/reimbursement/index.vue](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/Grok实施工作副本_20260912/erp-ui/src/views/mobile/oa/reimbursement/index.vue>)
- [erp-ui/src/views/mobile/oa/reimbursement/mobileReimbursementState.js](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/Grok实施工作副本_20260912/erp-ui/src/views/mobile/oa/reimbursement/mobileReimbursementState.js>)
- [erp-ui/src/api/oa/reimbursement.js](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/Grok实施工作副本_20260912/erp-ui/src/api/oa/reimbursement.js>)
- [erp-modules/erp-oa/src/main/java/com/erp/oa/domain/OaReimbursement.java](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/Grok实施工作副本_20260912/erp-modules/erp-oa/src/main/java/com/erp/oa/domain/OaReimbursement.java>)
- [erp-modules/erp-oa/src/main/java/com/erp/oa/controller/OaReimbursementController.java](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/Grok实施工作副本_20260912/erp-modules/erp-oa/src/main/java/com/erp/oa/controller/OaReimbursementController.java>)
- [erp-modules/erp-oa/src/main/java/com/erp/oa/service/impl/OaReimbursementServiceImpl.java](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/Grok实施工作副本_20260912/erp-modules/erp-oa/src/main/java/com/erp/oa/service/impl/OaReimbursementServiceImpl.java>)
- [erp-modules/erp-oa/src/main/java/com/erp/oa/mapper/OaReimbursementMapper.java](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/Grok实施工作副本_20260912/erp-modules/erp-oa/src/main/java/com/erp/oa/mapper/OaReimbursementMapper.java>)
- [erp-modules/erp-oa/src/main/resources/mapper/oa/OaReimbursementMapper.xml](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/Grok实施工作副本_20260912/erp-modules/erp-oa/src/main/resources/mapper/oa/OaReimbursementMapper.xml>)

依赖/并行边界：WF01；WF02；WF03

验收与失败恢复：

- 草稿保存成功、提交已进入 PENDING 但响应丢失：页面回查显示审批中，重试不先保存、不多开一轮。
- SUBMITTING/outbox 待处理时显示受理中；原提交重试不重复插 outbox。
- 提交明确校验拒绝仍保留已保存草稿和原因；保存失败不能提示草稿已保存。
- 旧命令查询时记录已退回并开始下一轮，expectedRound/version 阻止旧提交创建更新轮次。
- 当前 A 提交 UNKNOWN 后打开 B，A 回查/失败/finally 均不动 B；请求发出后的取消不是未提交证据。

迁移与回退：

- 复用现有报销 ID/轮次/outbox，不新增通用命令表；增量预期条件后端先兼容再收紧。保留较新 formConflict/save unknown 守卫，不以本项宣称无 ID 新建草稿也已完全幂等。

产品口径：无需新增产品决定，保留已确认规则。

[原问题证据](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/修复工作副本_2026-09-08/docs/reports/项目逻辑与操作便利性复核-20260913.md:52>)；[本项设计来源](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/修复工作副本_2026-09-08/output/grok-repair-plan-20260913/workflow-design.json:814>)。

#### GROK-01 · P2 · OA采购审批同意、退回和拒绝失败静默，用户无法判断结果或继续处理

原触发条件：已有可见PENDING任务和RUNNING实例，确认审批动作后请求最终返回普通HTTP409/403、业务体409/403/500，或发生网络/超时失败。任务状态或权限可以在页面加载后变化；实际线上待办及角色变化未操作。

已接受的条件与边界：

- HTTP401或业务体401不应写作静默：存在会话候选时，统一FedLogOut清理及MessageBox登录失效提示在空catch之前执行。
- 特殊业务码CREDENTIAL_CHANGE_REQUIRED/TEMPORARY_CREDENTIAL_EXPIRED的409及428有EnforceCredentialChange；不计入普通409静默断言。
- Cookie模式且HTTP403携带x-erp-csrf-required:true会先尝试一次CSRF恢复/重放，恢复成功不属于失败；只有最终失败需核对相应最终错误分支。
- 用户主动取消确认/原因输入属于正常取消，不应显示业务失败。本次动态取消对照针对approve确认；return/reject的取消分支由相同空catch源码闭环。
- 当前后端任务已处理/非候选人/停用或权限改变等ServiceException多数不指定code，返回AjaxResult默认body500；权限码校验异常为body403。409是客户端合法错误分支探针，不断言任务已处理一定返回409。
- 手机load失败有独立error模板；审批写失败不会进入load成功续链，故该加载错误展示不覆盖本问题。
- 仅在显式内存适配器执行；实际Axios响应链、抽取组件/API方法、真实transferRecovery非调拨分支及transport normalizer。会话、消息界面、CSRF恢复和刷新为桩；未挂载浏览器DOM、未执行前置request拦截器和真实后端/数据库。
- 没有生产请求或新增源码修改。既有线上只读观察采购提交未开放，不能据本探针认定生产已发生审批静默事故、错批、越权或重复业务事实。

原证据层级：`real-vue-api-axios-response-interceptor-memory-probe`。

```json
{
  "level": "完整错误处理链源码核对+实际Vue/API/响应拦截器方法内存执行",
  "runtime": "v22.23.1",
  "summary": {
    "total": 112,
    "negativeReproductions": 84,
    "counterexampleControls": 28,
    "assertionFailures": 0
  },
  "failureTypes": [
    "HTTP409(JSON body409)",
    "HTTP403(JSON body403,非CSRF恢复)",
    "HTTP200/body409",
    "HTTP200/body403",
    "HTTP200/body500",
    "Network Error",
    "ECONNABORTED timeout"
  ],
  "controls": [
    "正常成功",
    "主动取消审批确认",
    "HTTP200/body401",
    "HTTP401",
    "凭据限制body409",
    "凭据限制body428",
    "Cookie CSRF403恢复成功后重放成功"
  ],
  "probeScript": "output/grok-peer-review-20260913/approval-error-probes.cjs",
  "probeResults": "output/grok-peer-review-20260913/approval-error-probe-results.json",
  "log": "output/grok-peer-review-20260913/approval-error-probe-node22.log",
  "controlScope": "7种对照在每副本每页面approve动作执行；7类失败在每副本每页面全部3动作执行。",
  "mobilePathname": "/mobile/oa-purchase-approval",
  "routeCorrection": "初始探针pathname写成组件目录形式；已修正为正式路由并重新运行112次。silentError=true及/mobile/前缀判定均不受初始差异影响，正式结果全部一致。"
}
```

具体改法：

1. 采购 PC/H5 approve、return、reject 共用 WF02 审批动作适配器，与 A11 同修复包但独立入口验收。删除 finally 后吞掉所有错误的空 catch，保持 api/approval/task.js 的 silentError:true 契约，不全局关闭。
2. 业务失败显示未通知的可行动原因，意见保留；普通 HTTP/body 403/409、body500、网络和超时按实际终态分支分类。任务已处理多数现有后端为 body500，不能硬编码只认 HTTP409。
3. 网络/超时或发出后取消标记 UNKNOWN，保留稳定原 requestId/原动作快照并 GET 实例 actions 核对。只有 requestId+operator+action+reason+task+instance 和实例业务绑定一致才归因本次成功；仅任务已处理/另人同意不算本次成功。
4. 维持统一 HTTP/body401、凭据变更409/428、Cookie CSRF 首次恢复重放的处理，最终未通知失败由当前页面接管；确认/原因输入前的主动取消无错误提示。WF01 守卫 then/catch/finally 和确认后回调，ROOT-01 确保动作目标就是显示业务。

已存在相关文件：

- [erp-ui/src/views/oa/purchase/index.vue](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/Grok实施工作副本_20260912/erp-ui/src/views/oa/purchase/index.vue>)
- [erp-ui/src/views/mobile/oa/purchaseApproval/index.vue](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/Grok实施工作副本_20260912/erp-ui/src/views/mobile/oa/purchaseApproval/index.vue>)
- [erp-ui/src/api/approval/task.js](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/Grok实施工作副本_20260912/erp-ui/src/api/approval/task.js>)
- [erp-ui/src/api/approval/monitor.js](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/Grok实施工作副本_20260912/erp-ui/src/api/approval/monitor.js>)
- [erp-ui/src/utils/request.js](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/Grok实施工作副本_20260912/erp-ui/src/utils/request.js>)
- [erp-ui/src/utils/fileResponse.js](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/Grok实施工作副本_20260912/erp-ui/src/utils/fileResponse.js>)
- [erp-ui/src/utils/todoApprovalActions.js](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/Grok实施工作副本_20260912/erp-ui/src/utils/todoApprovalActions.js>)
- [erp-modules/erp-approval/src/main/java/com/erp/approval/service/ApprovalTaskService.java](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/Grok实施工作副本_20260912/erp-modules/erp-approval/src/main/java/com/erp/approval/service/ApprovalTaskService.java>)
- [erp-modules/erp-approval/src/main/java/com/erp/approval/service/ApprovalMonitorService.java](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/Grok实施工作副本_20260912/erp-modules/erp-approval/src/main/java/com/erp/approval/service/ApprovalMonitorService.java>)
- [erp-modules/erp-approval/src/main/resources/mapper/approval/ApprovalRuntimeMapper.xml](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/Grok实施工作副本_20260912/erp-modules/erp-approval/src/main/resources/mapper/approval/ApprovalRuntimeMapper.xml>)

依赖/并行边界：WF01；WF02；WF03；ROOT-01；A11

验收与失败恢复：

- 以既有 112 场景探针为回归基线：2 副本/集成基线两页面×三动作×7失败类型各有提示或 UNKNOWN 恢复，不能仅把 Promise 变成 resolve(undefined)。
- 401/凭据限制/CSRF 成功恢复和用户确认取消对照继续正确且不双提示；真实手机路由 /mobile/oa-purchase-approval。
- 原动作已提交丢响应后完全匹配日志确认；无日志保持待核对；别人处理/不同动作不同 reason 不显示本次成功。
- UNKNOWN 原按钮只重试原命令，不生成新时间戳请求号；改变意见必须先结束/核对旧命令并建立新意图。
- 确认后路由 A→B、关窗、切组织/账号以及旧 finally 不影响 B；B 不因旧请求仍 loading 而无法加载。

迁移与回退：

- 无审批数据库迁移，复用既有日志唯一键；只给这些调用方接管错误。若回退页面，至少保留当前页显式错误处理和 UNKNOWN 防误重试，不扩大 silentError 变更到全项目。

产品口径：无需新增产品决定，保留已确认规则。

[原问题证据](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/修复工作副本_2026-09-08/output/grok-peer-review-20260913/approval-error-validation.md:3>)；[本项设计来源](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/修复工作副本_2026-09-08/output/grok-repair-plan-20260913/workflow-design.json:890>)。

#### HR-N05 · P2 · 手机补卡在提交成功但响应丢失后，原页重试被“不可编辑”挡住

原触发条件：员工保存并提交补卡；后端已把状态变为 PENDING，响应在网络上丢失，页面仍保留 DRAFT；员工点原提交按钮重试。

已接受的条件与边界：补卡提交服务端已受理但响应丢失，原按钮重试又先走编辑保存而被状态校验阻止；探针中服务端状态为模拟，真实弱网受理未验收。

原证据层级：`real-source-offline-probe-plus-stated-static-boundary`。E3：真实 saveDraft 调用序列 update(DRAFT)→submit(version2，模拟提交后丢响应)→retry update(PENDING)；输出 backendStatus=PENDING、formStatus=DRAFT、retryError=CORRECTION_STATUS_NOT_EDITABLE。首次创建丢响应为 E2 推导。

具体改法：

1. 创建草稿前生成并保存稳定clientRequestId，连同userId/shopId/表单代次、阶段、已知recordId/version和载荷摘要标记原操作；接通已有GET /corrections/by-client-request/{id}。持久存储只存恢复元数据，原因/完整个人资料保留内存，不写明文长期localStorage。
2. 把保存和提交拆成可恢复阶段：CREATE/UPDATE结果未知先按请求键或已知ID查回；SUBMIT结果未知先GET记录。已PENDING且有实例直接显示审批进度；SUBMITTING读取既有outbox阶段并使用原submit重试发起，不能再先updateDraft。明确DRAFT/RETURNED才允许继续保存原草稿。
3. 查回NOT_FOUND与网络/权限/服务失败分开：只有确定没找到时可用同key、同payload重放创建（即使原事务稍后提交也由唯一键/指纹兜底）；查回本身失败仍显示“结果待核对”，不自动新建。用户改表单需先解决原结果，再为新意图生成新key，不能同key改内容。
4. 恢复过程与公司/用户/表单代次绑定；原操作成功不能关闭新表单。保留后端可编辑状态、版本、schedule归属、同key指纹以及PENDING/SUBMITTING幂等返回，不删保护换取重试通过。

已存在相关文件：

- [erp-ui/src/views/mobile/attendance/MobileAttendanceCorrection.vue](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/Grok实施工作副本_20260912/erp-ui/src/views/mobile/attendance/MobileAttendanceCorrection.vue>)
- [erp-ui/src/api/oa/attendanceV2.js](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/Grok实施工作副本_20260912/erp-ui/src/api/oa/attendanceV2.js>)
- [erp-modules/erp-oa/src/main/java/com/erp/oa/attendance/correction/AttendanceCorrectionService.java](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/Grok实施工作副本_20260912/erp-modules/erp-oa/src/main/java/com/erp/oa/attendance/correction/AttendanceCorrectionService.java>)
- [erp-modules/erp-oa/src/main/java/com/erp/oa/attendance/correction/AttendanceCorrectionController.java](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/Grok实施工作副本_20260912/erp-modules/erp-oa/src/main/java/com/erp/oa/attendance/correction/AttendanceCorrectionController.java>)
- [sql/erp_oa_attendance_client_idempotency_20260822.sql](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/Grok实施工作副本_20260912/sql/erp_oa_attendance_client_idempotency_20260822.sql>)

可复用机制：后端saveDraft 101-155已支持clientRequestId+fingerprint；controller91已开放查询；submit305-324已有PENDING/SUBMITTING重放。

依赖/并行边界：与A13/P03/GROK-01共享未知结果文案/恢复原则，但本项复用补卡自身API

验收与失败恢复：

- [方法] create成功丢响应→原key查回；update成功丢响应→读新版本；submit成功丢响应→读PENDING且不调用update；SUBMITTING重试不新轮次；明确业务失败保留输入。
- [隔离MySQL+审批] 同key并发创建只一记录，同key不同payload拒绝；同submit重放只一审批轮次；网关断响应、审批远端失败、outbox恢复后与业务一致。
- [浏览器] 弱网/刷新后通过恢复元数据找原记录；切店/切人不串结果；取消/关闭重开不被迟到回包改；没有服务确认时不显示“肯定未提交”。

迁移与回退：

- 先只读确认既有client_request_id/指纹字段和唯一索引已部署，不能仅凭源码存在认为线上可用；缺失则按既有幂等扩展SQL单列迁移。
- 回退前端保留请求键元数据和已受理记录；不删除幂等列/索引或回滚审批业务数据。恢复API不应在旧数据无key时补造对应关系。

产品口径：无须新增产品决定；按已确认规则与本方案实施。

[原问题证据](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/修复工作副本_2026-09-08/output/full-project-audit-20260913/hr/findings.md:100>)；[本项设计来源](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/修复工作副本_2026-09-08/output/grok-repair-plan-20260913/hr-design.json:431>)。

#### P03 · P2 · 云盘上传结果未知时，“重试”会生成第二份文件

原触发条件：上传已落库但响应丢失，PC/H5把任务记失败后直接重传，服务端新建同名后缀文件。

已接受的条件与边界：保留原报告的触发条件、当前/较新副本对照和证据边界；未新增生产事故或实际环境验收结论。

原证据层级：`offline-vue-method-plus-static-backend`。四次真实组件方法/状态helper内存复现两次serverWrites；重名持久化为静态证据。

具体改法：

1. 新增持久上传操作契约与结果查询（现有uploadDriveFile没有稳定操作ID/结果端点）。每个用户选中文件生成一次稳定operationId，绑定actor、space、parent、文件身份/长度/摘要；换目标或文件必须显式形成新操作。
2. 复用DriveUploadReservationService的存储预占/补偿及DriveUploadPersistence事务：节点、配额、操作SUCCEEDED和结果nodeId必须同一提交边界。并发同ID只允许一个实际存储写入owner；不同内容同ID明确冲突。
3. PC/H5网络中断进入结果待核对，查询本人的原operationId：SUCCEEDED直接取原nodeId完成，不重传；PROCESSING只核对；NOT_OBSERVED不能证明旧请求不会晚到；只有明确安全失败且原owner已终结才允许同ID/原载荷再次申请执行权。任何同ID晚到请求都必须受持久去重和owner隔离保护。
4. 取消XHR不等于服务端撤销；页面保留任务元数据且不保存凭据，刷新后可查询结果；浏览器无法恢复File对象时仅让用户重选该文件，并核对身份。分片断点续传是后续优化，不是本缺陷最小必需项。

已存在相关文件：

- [erp-ui/src/views/drive/index.vue](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/Grok实施工作副本_20260912/erp-ui/src/views/drive/index.vue>)
- [erp-ui/src/views/mobile/drive/index.vue](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/Grok实施工作副本_20260912/erp-ui/src/views/mobile/drive/index.vue>)
- [erp-ui/src/api/drive/index.js](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/Grok实施工作副本_20260912/erp-ui/src/api/drive/index.js>)
- [erp-modules/erp-file/src/main/java/com/erp/file/drive/service/DriveUploadService.java](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/Grok实施工作副本_20260912/erp-modules/erp-file/src/main/java/com/erp/file/drive/service/DriveUploadService.java>)
- [erp-modules/erp-file/src/main/java/com/erp/file/drive/service/DriveUploadPersistence.java](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/Grok实施工作副本_20260912/erp-modules/erp-file/src/main/java/com/erp/file/drive/service/DriveUploadPersistence.java>)
- [erp-modules/erp-file/src/main/java/com/erp/file/drive/service/DriveUploadReservationService.java](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/Grok实施工作副本_20260912/erp-modules/erp-file/src/main/java/com/erp/file/drive/service/DriveUploadReservationService.java>)

依赖/并行边界：B00；公共结果恢复约定；与A13/HR-N05/GROK-01统一呈现但业务持久表独立

验收与失败恢复：

- 真实DB/存储/HTTP故障注入：提交成功后丢响应、慢旧请求晚到、同ID并发重试、同ID不同文件、取消后完成、服务重启；只能有一个有效节点和一次配额增量。
- PC/H5恢复列表、原目标不串改、无权限无法查询他人operationId；未完成预占/孤儿文件按既有补偿链恢复，不能误删已成功对象。

迁移与回退：

- 预计新增小型操作表/唯一键与查询API，名称和DDL由实施验证后定；迁移必须新增兼容并更新已有SQL清单，保留历史文件不自动合并。
- 后端先兼容可选操作ID，前端功能切换后要求新入口稳定ID；回退时保留操作表/结果查询及未完成任务处理，不能退到无ID重传。

产品口径：无需新增产品决定，保留已确认规则。

[原问题证据](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/修复工作副本_2026-09-08/output/full-project-audit-20260913/platform/findings.md:23>)；[本项设计来源](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/修复工作副本_2026-09-08/output/grok-repair-plan-20260913/platform-design.json:67>)。

#### ROOT-02 · P2 · 固定资产整店保存部分成功后无法原地重试

原触发条件：保存一店资产时先删除取消勾选的旧资产，再逐条保存；后续某行失败，前面删除/新增已经各自提交。页面未吸收新增 configId 也未重建基线，再次保存先重删已不存在的旧行并失败。

已接受的条件与边界：保留原报告的触发条件、当前/较新副本对照和证据边界；未新增生产事故或实际环境验收结论。

原证据层级：`real-source-offline-probe-plus-stated-static-boundary`。双副本实际 saveAssetRows/runAssetRowRequestsSequentially 内存接口探针：首次错误资料不完整；已有删除和新增；二次错误固定资产配置不存在。

具体改法：

1. 将整店“保存”改成一个拟新增 POST /oa/fixedAsset/config/batch-save，冻结 shop/组织、期望快照版本、完整选中行、稳定 requestId。后台先校验全体行和变更权限，再在一个事务中删除取消行、插入/更新选中行并一次重算额度；任一行失败整批回滚，返回具体行错误。
2. 拟新增门店配置范围版本/锁记录和批次命令回执。范围键由服务端实际授权组织+门店组成，解决空店并发新增；所有旧单行写入口同样取范围锁并推进版本。requestId 在操作者+范围下唯一，载荷摘要不同拒绝；已提交同命令返回原完整 configId/版本列表。
3. 拟新增 GET /oa/fixedAsset/config/batch-commands/{requestId}，仅同操作者及当前有权范围可读。丢响应先核对；未确认完成时保留原载荷，仅允许原 requestId 重试。30 秒 @IdempotentSubmit 不能替代持久回执。
4. 页面成功后以服务端完整快照重建 originalRows/configId/版本，保持用户在本店可继续编辑；失败保留编辑内容。提交时冻结店铺/行编辑与删改操作，旧回包用 WF01 隔离。整店删除如仍走多行请求，应同时改用这一事务服务的空集合命令并要求原删除权限。

已存在相关文件：

- [erp-ui/src/views/oa/fixedAsset/config/index.vue](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/Grok实施工作副本_20260912/erp-ui/src/views/oa/fixedAsset/config/index.vue>)
- [erp-ui/src/api/oa/fixedAsset.js](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/Grok实施工作副本_20260912/erp-ui/src/api/oa/fixedAsset.js>)
- [erp-modules/erp-oa/src/main/java/com/erp/oa/controller/OaFixedAssetConfigController.java](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/Grok实施工作副本_20260912/erp-modules/erp-oa/src/main/java/com/erp/oa/controller/OaFixedAssetConfigController.java>)
- [erp-modules/erp-oa/src/main/java/com/erp/oa/service/IOaFixedAssetService.java](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/Grok实施工作副本_20260912/erp-modules/erp-oa/src/main/java/com/erp/oa/service/IOaFixedAssetService.java>)
- [erp-modules/erp-oa/src/main/java/com/erp/oa/service/impl/OaFixedAssetServiceImpl.java](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/Grok实施工作副本_20260912/erp-modules/erp-oa/src/main/java/com/erp/oa/service/impl/OaFixedAssetServiceImpl.java>)
- [erp-modules/erp-oa/src/main/java/com/erp/oa/mapper/OaFixedAssetConfigMapper.java](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/Grok实施工作副本_20260912/erp-modules/erp-oa/src/main/java/com/erp/oa/mapper/OaFixedAssetConfigMapper.java>)
- [erp-modules/erp-oa/src/main/resources/mapper/oa/OaFixedAssetConfigMapper.xml](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/Grok实施工作副本_20260912/erp-modules/erp-oa/src/main/resources/mapper/oa/OaFixedAssetConfigMapper.xml>)

拟新增：`erp-modules/erp-oa/src/main/java/com/erp/oa/domain/dto/OaFixedAssetConfigBatchRequest.java`；`erp-modules/erp-oa/src/main/java/com/erp/oa/service/impl/OaFixedAssetConfigBatchService.java`；`erp-modules/erp-oa/src/main/resources/db/migration/erp_oa_fixed_asset_config_batch_20260913.sql`。

依赖/并行边界：WF01；WF04

验收与失败恢复：

- 原有两行，删除一行+新增两行，第二新增非法：数据库旧集合和额度完全不变；页面列出该行错误。
- 成功后完整 configId/版本回填，连续第二次保存无重复新增或重删不存在行。
- 已提交但丢响应，查询原回执或原 requestId 重试，配置和额度仅生效一次；同 ID 改载荷拒绝。
- 空店两个客户端同时保存，以及旧单行接口与新整店接口并发：锁/版本阻止覆盖，冲突保留用户输入并允许比较新快照。
- 包含越范围配置或取消行但无删除权限时，整批无写入；A 保存后切 B，A finally 不解锁 B。

迁移与回退：

- 先部署增量表与兼容服务，再启用批量 UI；旧配置不重写。回退 UI 时保留新表/回执并让旧入口继续共用范围锁和版本；禁止删除已生效回执。恢复使用修复前备份及按命令审计，不把“回滚代码”等同撤销已提交配置。

产品口径：无需新增产品决定，保留已确认规则。

[原问题证据](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/修复工作副本_2026-09-08/output/full-project-audit-20260913/root/findings.md:23>)；[本项设计来源](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/修复工作副本_2026-09-08/output/grok-repair-plan-20260913/workflow-design.json:230>)。


### B05 人事、入职与签约状态

#### HR-N01 · P2 · 未来生效的健康证获批后立即替换当前证件，并显示“有效”

原触发条件：员工提前办理续证，将生效日填为未来、到期日更晚；该记录经统一审批通过。日期输入和后端只要求到期日不早于办理/生效日，允许此输入。

已接受的条件与边界：保留原报告的触发条件、当前/较新副本对照和证据边界；未新增生产事故或实际环境验收结论。

原证据层级：`E2-static-chain`。E2：前端→校验→审批回调→mapper→档案投影静态链路；未写数据库。

具体改法：

1. 把审批结果APPROVED与时间状态分开；提前续证仍可保存/提交/审批，validFrom空值继续按issuedDate归一化。新增派生状态NOT_YET_EFFECTIVE（已通过、待生效），不把它当审批待审或有效证。
2. “当前有效证”按同一Asia/Shanghai业务日期选择：未删除、APPROVED、validFrom<=业务日且expiresOn>=业务日。若有效窗口内恰有一张current_flag=Y，保留该有效当前证；无此唯一有效标志时，才按validFrom降序→reviewedTime降序→certificateId降序稳定选取。多个当前标志/非法日期进入只读异常清单，不批量猜改。没有有效证时展示已生效过期证及提示；只有未来证则显示当前无有效证和待生效续证。
3. 在现有Clock之上统一计算asOfDate，传给mapper共享的当前证选择SQL；档案投影、列表状态/筛选、汇总、到期提醒与待办调用同一规则，替换各处CURRENT_DATE()和单看current_flag的语义。按分页userIds批量选择，不能逐人N+1读库。
4. current_flag降为兼容缓存：旧审核review与统一审批回调均在既有员工锁和事务内先写审批结果，再只切到当日应选证；不再审批未来证就clear旧证。跨日正确性由日期查询保证，不依赖任务一定准时执行；后台可按同样选择规则幂等校准缓存。保留证书历史、版本、审批事件幂等和受控附件权限。

已存在相关文件：

- [erp-modules/erp-system/src/main/java/com/erp/system/service/impl/HrHealthCertificateServiceImpl.java](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/Grok实施工作副本_20260912/erp-modules/erp-system/src/main/java/com/erp/system/service/impl/HrHealthCertificateServiceImpl.java>)
- [erp-modules/erp-system/src/main/resources/mapper/system/HrHealthCertificateMapper.xml](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/Grok实施工作副本_20260912/erp-modules/erp-system/src/main/resources/mapper/system/HrHealthCertificateMapper.xml>)
- [erp-modules/erp-system/src/main/java/com/erp/system/mapper/HrHealthCertificateMapper.java](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/Grok实施工作副本_20260912/erp-modules/erp-system/src/main/java/com/erp/system/mapper/HrHealthCertificateMapper.java>)
- [erp-modules/erp-system/src/main/java/com/erp/system/service/impl/HrHealthCertificateReminderScanner.java](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/Grok实施工作副本_20260912/erp-modules/erp-system/src/main/java/com/erp/system/service/impl/HrHealthCertificateReminderScanner.java>)
- [erp-modules/erp-system/src/main/java/com/erp/system/service/impl/HrHealthCertificateAccessService.java](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/Grok实施工作副本_20260912/erp-modules/erp-system/src/main/java/com/erp/system/service/impl/HrHealthCertificateAccessService.java>)
- [erp-ui/src/views/hr/healthCertificate/index.vue](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/Grok实施工作副本_20260912/erp-ui/src/views/hr/healthCertificate/index.vue>)
- [erp-ui/src/views/hr/components/HrProfileDetailDrawer.vue](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/Grok实施工作副本_20260912/erp-ui/src/views/hr/components/HrProfileDetailDrawer.vue>)
- [sql/erp_hr_health_certificate_20260713.sql](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/Grok实施工作副本_20260912/sql/erp_hr_health_certificate_20260713.sql>)

可复用机制：较新health:47/57已有BUSINESS_ZONE/Clock；518-534已有员工锁+版本/事件校验；当前379-395有旧审核路径也必须改。；mapper现有current_flag唯一键仅保证缓存唯一；未来生效选择不可只改decorate。

依赖/并行边界：无额外依赖，服从本责任包与共享文件顺序。

验收与失败恢复：

- [单元/Clock] 旧证今天有效、新证明天生效：今天仍选旧证，新证待生效；推进明天新证自动成为有效候选，无需再次审批。若旧证仍是唯一有效current_flag则维持旧证；旧证失效后自动选有效新证。覆盖重叠窗口、无有效current、到期当天、闰日、时区不同和空validFrom。
- [隔离MySQL] 同员工两张证审批并发、旧审核与统一回调竞争、同事件重放：最多一个兼容current_flag，投影/筛选/汇总/提醒选择同一证；SQL执行计划与批量读取无N+1。
- [页面] 员工和HR同时能看到当前证与待生效续证；列表数字可定位一致集合，未来证不会被显示为有效；无有效旧证时文案准确。
- [历史只读盘点] 输出已批准未来证占current、重叠有效证、空日期、非法日期、同人多标志候选数量与匿名分组；方案阶段不查询真实数据，更不自动改历史。

迁移与回退：

- 现有表与valid_from已存在，先考虑无结构迁移；若性能需索引，依据隔离库EXPLAIN新增非破坏性索引并单列迁移。
- 部署先引入统一只读选择与双口径差异日志，再切消费者；历史current_flag校准需审批通过的独立清单、前后快照、版本条件和可逆更新，不删证据。回退旧应用前评估旧current_flag行为，不能单纯回旧算法重新引入未来证提前生效。

产品口径：采用双方方案推荐的保守展示策略：保留唯一仍有效当前证，无有效当前时稳定择优；这不是用户事先确认过的新业务规则。重叠异常先只读盘点，不阻塞未来证不可提前生效的主修复，不自动批量校准历史。

[原问题证据](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/修复工作副本_2026-09-08/output/full-project-audit-20260913/hr/findings.md:10>)；[本项设计来源](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/修复工作副本_2026-09-08/output/grok-repair-plan-20260913/hr-design.json:7>)。

#### HR-N02 · P2 · 健康证“待审核”指标漏掉统一审批中的记录

原触发条件：健康证提交后经过 APPROVAL_SUBMITTING 进入 APPROVAL_PENDING；HR 查看健康证首页待审核数量和最老待审。

已接受的条件与边界：保留原报告的触发条件、当前/较新副本对照和证据边界；未新增生产事故或实际环境验收结论。

原证据层级：`E2-static-chain`。E2：页面、状态迁移和统计 SQL；未查询实际待办数量。

具体改法：

1. 定义不重叠的三个健康证队列：旧审核待处理=PENDING_REVIEW；统一审批中=APPROVAL_PENDING；待发起/发起异常=APPROVAL_SUBMITTING，结合既有outbox状态分出可重试异常。不能把APPROVAL_SUBMITTING伪装成已经有审批人的待审任务。
2. 首页“待审核”兼容总数=PENDING_REVIEW+APPROVAL_PENDING，并提供两个子数；待发起/异常独立展示。最老待审核取相同两状态集合的业务记录时间，明确“自记录创建起”口径；若未来增加真实submittedAt再独立迁移，不把create_time冒称审批节点等待时长。
3. 数字点击设置服务端同名队列筛选，计数与列表复用同一SQL片段和DataScope；按健康证业务记录去重，不按并行审批任务数量累计，不与合同pending_sign/签约包待签统计混合。异常按钮复用现有发起重试入口，不新建审批轮次。

已存在相关文件：

- [erp-modules/erp-system/src/main/java/com/erp/system/service/impl/HrHealthCertificateServiceImpl.java](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/Grok实施工作副本_20260912/erp-modules/erp-system/src/main/java/com/erp/system/service/impl/HrHealthCertificateServiceImpl.java>)
- [erp-modules/erp-system/src/main/resources/mapper/system/HrHealthCertificateMapper.xml](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/Grok实施工作副本_20260912/erp-modules/erp-system/src/main/resources/mapper/system/HrHealthCertificateMapper.xml>)
- [erp-ui/src/views/hr/healthCertificate/index.vue](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/Grok实施工作副本_20260912/erp-ui/src/views/hr/healthCertificate/index.vue>)
- [erp-modules/erp-system/src/main/java/com/erp/system/domain/vo/HrHealthCertificateOpsSummaryVo.java](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/Grok实施工作副本_20260912/erp-modules/erp-system/src/main/java/com/erp/system/domain/vo/HrHealthCertificateOpsSummaryVo.java>)
- [erp-modules/erp-system/src/main/java/com/erp/system/service/impl/HrHealthCertificateApprovalStartOutboxService.java](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/Grok实施工作副本_20260912/erp-modules/erp-system/src/main/java/com/erp/system/service/impl/HrHealthCertificateApprovalStartOutboxService.java>)

可复用机制：HrHealthCertificateAccessService已有相同DataScope；outbox已有APPROVAL_SUBMITTING恢复与状态机。

依赖/并行边界：与HR-N01共享业务日期/统计接口，HR-N11共享状态文案

验收与失败恢复：

- [SQL/隔离MySQL] 每个旧/新/提交中/驳回/撤回/已通过状态各放合成记录；总数、最老时间、点击列表集合相同，跨组织不混入；同一业务多审批任务不重复计数。
- [审批集成] SUBMITTING→PENDING、发起失败→重试、已通过回调、旧轮次迟到回调期间，队列按最终业务状态变动，不出现无任务的虚假“审批中”。
- [页面] HR一键打开准确子队列；员工无管理权限不显示全组织统计；接口失败保留错误和重试入口而非显示伪零。

迁移与回退：

- 不改历史审批状态，不补造审批实例；VO新增字段保持旧pendingReviewCount兼容。
- 可单独回退前端展示；回退SQL会重新漏计，须保留已修查询或明确运行限制。

产品口径：无须新增产品决定；按已确认规则与本方案实施。

[原问题证据](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/修复工作副本_2026-09-08/output/full-project-audit-20260913/hr/findings.md:33>)；[本项设计来源](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/修复工作副本_2026-09-08/output/grok-repair-plan-20260913/hr-design.json:131>)。

#### HR-N04 · P2 · 手机入职页面将超安全整数的记录编号舍入后读取和更新

原触发条件：入职记录ID超过 JavaScript 安全整数上限，例如路由ID=9007199254740993。该条件未证明在线上数据中已出现。

已接受的条件与边界：现有 DDL 是 BIGINT AUTO_INCREMENT；超过 Number.MAX_SAFE_INTEGER（2^53−1），且 Number 转换发生舍入时触发；并非所有更大 ID 都会变值。生产最大编号未查，不声称当前员工已被错读。

原证据层级：`real-source-offline-probe-plus-stated-static-boundary`。E3：两副本真实 loadDetail：intendedId=9007199254740993，actualGetId=loadedId=9007199254740992，loadError=""；API模拟，无实际资料访问。

具体改法：

1. 抽取精确正十进制ID工具，复用较新employee:48-55规则：字符串格式与Long上限按字长/字典序验证；不经Number/parseInt。仅兼容安全的number输入，已经不安全的number立即拒绝，不能String(已舍入值)假装修复。前导零规范化策略与现有employee保持一致。
2. 手机route.params.id、recordId、详情响应校验、创建回执/导航、更新URL、冲突恢复和保存后目标均使用规范十进制字符串；版本号仍按其独立契约处理。对数组query只按现有firstQueryValue处理，不改变路由业务。
3. 核实并修正入职列表/详情/创建/状态操作等API边界的Long ID序列化，优先在安全VO对应getter用ToStringSerializer（详情继承列表VO），同时盘点confirm/import回执中的入职ID；避免全局把所有数值字符串化。PC/H5/API适配同一ID工具，不能只手机改URL却让JSON响应先丢精度。
4. 边界是超过Number.MAX_SAFE_INTEGER（2^53−1）且转换实际舍入；不声称所有大ID都会改变。后台继续Long、范围权限和原版本校验，数据库ID不重编号。

已存在相关文件：

- [erp-ui/src/views/mobile/hr/onboarding/form.vue](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/Grok实施工作副本_20260912/erp-ui/src/views/mobile/hr/onboarding/form.vue>)
- [erp-ui/src/views/mobile/hr/employee/index.vue](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/Grok实施工作副本_20260912/erp-ui/src/views/mobile/hr/employee/index.vue>)
- [erp-ui/src/views/hr/components/HrEmployeeList.vue](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/Grok实施工作副本_20260912/erp-ui/src/views/hr/components/HrEmployeeList.vue>)
- [erp-ui/src/views/hr/onboarding/index.vue](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/Grok实施工作副本_20260912/erp-ui/src/views/hr/onboarding/index.vue>)
- [erp-ui/src/api/hr/onboarding.js](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/Grok实施工作副本_20260912/erp-ui/src/api/hr/onboarding.js>)
- [../Grok实施工作副本_20260912/erp-ui/src/utils/leaveBalanceUi.js](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/Grok实施工作副本_20260912/erp-ui/src/utils/leaveBalanceUi.js>)
- [erp-modules/erp-system/src/main/java/com/erp/system/domain/vo/HrOnboardingListVo.java](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/Grok实施工作副本_20260912/erp-modules/erp-system/src/main/java/com/erp/system/domain/vo/HrOnboardingListVo.java>)
- [erp-modules/erp-system/src/main/java/com/erp/system/domain/vo/HrOnboardingDetailVo.java](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/Grok实施工作副本_20260912/erp-modules/erp-system/src/main/java/com/erp/system/domain/vo/HrOnboardingDetailVo.java>)
- [erp-modules/erp-system/src/main/java/com/erp/system/controller/HrOnboardingController.java](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/Grok实施工作副本_20260912/erp-modules/erp-system/src/main/java/com/erp/system/controller/HrOnboardingController.java>)

拟新增：`erp-ui/src/utils/exactDecimalId.js`。

可复用机制：较新mobile/hr/employee已有Long上限精确字符串校验；leaveBalanceUi.exactId已拒绝不安全number。

依赖/并行边界：HR-ASYNC操作身份用此工具；不要广泛改金额/版本/页码类型

验收与失败恢复：

- [单元] 1、2^53−1、2^53、9007199254740993、Long.MAX_VALUE字符串原值贯通；0/负数/科学计数/小数/越Long/已不安全number拒绝；相邻两个可读ID绝不归一成一个。
- [序列化契约] 用真实ObjectMapper/MockMvc验证list/detail/create/update/状态回执，JSON原文ID带引号且每条消费者字符串比较成立；历史普通短ID兼容。
- [浏览器+隔离API] 打开/保存/版本冲突后回查合成大ID，请求与回执一致且不会读相邻ID；生产只读核最大编号，未核前不称已发生错档。

迁移与回退：

- 无DB结构/数据迁移；数据库Long及自增规则保留。
- API边界改字符串要前后端成套部署或先双类型兼容再服务端切换；回退只允许仍能精确解析ID的客户端，不能恢复Number转换。

产品口径：无须新增产品决定；按已确认规则与本方案实施。

[原问题证据](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/修复工作副本_2026-09-08/output/full-project-audit-20260913/hr/findings.md:78>)；[本项设计来源](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/修复工作副本_2026-09-08/output/grok-repair-plan-20260913/hr-design.json:295>)。

#### HR-N07 · P2 · 官方入职 Excel 模板未整列设置文本格式，正常填多行会被自身校验拒绝

原触发条件：下载系统入职模板，在样例首行填紧急联系人电话，或下一行以通常方式键入手机号；Excel/WPS 将 General 单元格保存为数字。

已接受的条件与边界：保留原报告的触发条件、当前/较新副本对照和证据边界；未新增生产事故或实际环境验收结论。

原证据层级：`real-source-offline-probe-plus-stated-static-boundary`。E3：现行 Grok 真实 parser/registry 源码 javac 后内存工作簿生成与解析；sample phone=@，next row phone=General，sample emergency=General；Excel row2、row3 都产生 TEXT_CELL_REQUIRED。业务校验器模拟，其余结构校验真实。

具体改法：

1. 用FieldRule.key与已有TEXT_ONLY_FIELDS驱动模板格式，不按中文label手写三个字段。对phoneNumber/idNumber/bankAccount/emergencyContactPhone统一sheet.setDefaultColumnStyle(text @)，样例单元格同样设文本。
2. 在业务允许的最大数据行范围内预设空白录入区的文本样式，并核对不会被解析器误算为超量有效行；列默认覆盖继续往下录入。标题、日期、金额等保留原类型，不给整张表一律文本。
3. 保留TEXT_CELL_REQUIRED及长证件号精度保护；已被Excel数值化且精度丢失的身份证/银行卡不能自动转字符串伪造恢复。预览准确指出行、列和“改文本后重新录入原值”，保留其他正常行与既有逐行确认流程。Excel入口继续启用，下载、权限与大小/压缩包安全限制不变。

已存在相关文件：

- [erp-modules/erp-system/src/main/java/com/erp/system/support/HrOnboardingExcelParser.java](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/Grok实施工作副本_20260912/erp-modules/erp-system/src/main/java/com/erp/system/support/HrOnboardingExcelParser.java>)
- [erp-modules/erp-system/src/main/java/com/erp/system/support/HrOnboardingFieldRegistry.java](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/Grok实施工作副本_20260912/erp-modules/erp-system/src/main/java/com/erp/system/support/HrOnboardingFieldRegistry.java>)
- [erp-ui/src/views/hr/onboarding/components/HrOnboardingImportDialog.vue](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/Grok实施工作副本_20260912/erp-ui/src/views/hr/onboarding/components/HrOnboardingImportDialog.vue>)

依赖/并行边界：HR-N04负责内部记录ID，本项负责Excel业务文本字段；两者不混用

验收与失败恢复：

- [POI真实模板] 4字段默认列/样例/后续行均@，新增行以正常输入解析无TEXT_CELL_REQUIRED；日期仍能按真实日期解析；空白格式区不算业务行。
- [Excel/WPS桌面] 第二行、批量多行键入、紧急电话、带前导零号码和身份证；保存再上传正常。外部粘贴覆盖成数字时应准确提示，不能悄悄改变数据。
- [导入接口] 原重复候选/错误行确认/权限/过量/压缩安全校验仍工作；无真实员工导入。

迁移与回退：

- 无数据库迁移，模板版本可加说明但不废弃旧版合法文本模板。
- 回退模板生成包不改已上传业务记录；对既有精度损坏数据只列只读核对清单，须人工核对原始凭据，禁止自动猜号。

产品口径：无须新增产品决定；按已确认规则与本方案实施。

[原问题证据](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/修复工作副本_2026-09-08/output/full-project-audit-20260913/hr/findings.md:123>)；[本项设计来源](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/修复工作副本_2026-09-08/output/grok-repair-plan-20260913/hr-design.json:525>)。

#### HR-N08 · P1 · 重新发布与历史版本相同的方案会假成功，历史版本仍停用

原触发条件：源方案保持启用：先发布方案V1，再发布变更后的V2（自动停用V1）；将方案及模板等全部内容指纹输入恢复为V1相同值后再次点击发布。源方案启用时，单独停用当前版本再对完全相同指纹内容发布也受影响。

已接受的条件与边界：源方案保持启用且所有参与指纹的内容相同，发布命中已停用旧 V1。V1 仍停用不会重新参与匹配；V2 只有仍启用且满足匹配条件时才可能继续使用。不得写作无条件继续使用 V2。

原证据层级：`real-source-offline-probe-plus-stated-static-boundary`。E3：真实 OaSignPlanVersionServiceImpl.publish＋既有测试夹具/内存mapper，输出 publish returned version=1, matching=DISABLED, writes=0。指纹输入不含状态/时间、匹配SQL已静态复核。

具体改法：

1. 保留(plan_id,version_hash)唯一约束和已发布快照不可变。相同指纹且目标仍ENABLED时返回明确UNCHANGED及实际matchingStatus；相同指纹目标DISABLED时不能再返回普通发布成功，也不能自动启用所有历史版本。
2. 复用现有发布确认窗口，拟新增服务端预览契约：显示源方案仍启用、内容变化摘要、命中的旧V1、当前启用V2及“恢复V1用于新任务，停用本方案其他启用版本，既有签包不变”。用户现有一次确认动作携带拟新增previewToken和指定restoreVersionId。token绑定服务端内容指纹、当时活跃版本、源状态及有效期，确认时重新计算/比较；内部完整性hash继续不作为业务字段展示。这里的预览接口/token/确认请求字段尚不存在，需要一并实现；无需另找历史列表或重复多次确认。
3. 确认后在同一事务按plan行→涉及version稳定顺序加锁，重算指纹并核对预览条件、权限、源方案启用与模板有效；仅启用明确目标版本并停同plan其他启用版本，更新数/读回实际状态不符则回滚。普通发布、恢复和停用也统一锁顺序，不能只给恢复加锁。
4. 回执区分PUBLISHED/UNCHANGED/RESTORED，返回实际活跃versionId/no/matchingStatus；追加记录操作人、目标/前活跃版本、内容hash及时间的安全审计，不修改旧快照、原发布人和原发布时间。已生成任务继续绑定原版本，新匹配才使用恢复版本。

已存在相关文件：

- [erp-modules/erp-oa/src/main/java/com/erp/oa/service/impl/OaSignPlanVersionServiceImpl.java](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/Grok实施工作副本_20260912/erp-modules/erp-oa/src/main/java/com/erp/oa/service/impl/OaSignPlanVersionServiceImpl.java>)
- [erp-modules/erp-oa/src/main/resources/mapper/oa/OaSignPlanVersionMapper.xml](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/Grok实施工作副本_20260912/erp-modules/erp-oa/src/main/resources/mapper/oa/OaSignPlanVersionMapper.xml>)
- [erp-modules/erp-oa/src/main/java/com/erp/oa/service/impl/OaSignPlanVersionFingerprint.java](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/Grok实施工作副本_20260912/erp-modules/erp-oa/src/main/java/com/erp/oa/service/impl/OaSignPlanVersionFingerprint.java>)
- [erp-modules/erp-oa/src/main/java/com/erp/oa/controller/OaSignPackageController.java](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/Grok实施工作副本_20260912/erp-modules/erp-oa/src/main/java/com/erp/oa/controller/OaSignPackageController.java>)
- [erp-modules/erp-oa/src/main/java/com/erp/oa/service/IOaSignPlanVersionService.java](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/Grok实施工作副本_20260912/erp-modules/erp-oa/src/main/java/com/erp/oa/service/IOaSignPlanVersionService.java>)
- [erp-modules/erp-oa/src/main/java/com/erp/oa/domain/vo/OaSignPlanVersionPublishResult.java](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/Grok实施工作副本_20260912/erp-modules/erp-oa/src/main/java/com/erp/oa/domain/vo/OaSignPlanVersionPublishResult.java>)
- [erp-ui/src/api/oa/signPackage.js](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/Grok实施工作副本_20260912/erp-ui/src/api/oa/signPackage.js>)
- [erp-ui/src/views/oa/signPackage/index.vue](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/Grok实施工作副本_20260912/erp-ui/src/views/oa/signPackage/index.vue>)
- [sql/erp_oa_sign_plan_version_20260711.sql](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/Grok实施工作副本_20260912/sql/erp_oa_sign_plan_version_20260711.sql>)

拟新增：`erp-modules/erp-oa/src/main/java/com/erp/oa/domain/dto/OaSignPlanPublishRequest.java`；`erp-modules/erp-oa/src/main/java/com/erp/oa/domain/vo/OaSignPlanPublishPreview.java`。

可复用机制：publish已有lockPlanById、模板锁、指纹、停其他版本方法；UI已有publishOpen/confirmPublishPlan确认窗口；Controller已有受限@Log发布入口。；现OaSignPlanVersionPublishResult仅提供业务回执，声明内部完整性hash不外露；新预览使用内容摘要与校验令牌，不展示内部文件hash。

依赖/并行边界：与HR-N03/S04共享发布弹窗目标代次；与签任务匹配回归合批

验收与失败恢复：

- [服务+mapper] 启用V1→启用V2→内容全部恢复V1：预览指定V1，确认后仅V1 ENABLED，V2 DISABLED，历史快照字节不变；同指纹已启用重试UNCHANGED。
- [隔离MySQL] 两人并发发布/恢复/停用、预览后模板改动、源方案停用、事务中途失败、响应丢失重试：无多个同方案活跃版本、无假成功；冲突明确要求刷新预览。
- [真实匹配/页面] 新任务实际匹配恢复版本，既有V2签包不变；若条件不满足应明确无匹配，不能写无条件继续V2；现发布窗口只需一次确认且显示真实结果。

迁移与回退：

- 当前hash唯一约束支持恢复方案，无需为相同内容强造V3或删除唯一键。可能扩展请求/回执DTO，前后端需兼容部署。
- 只读盘点“源方案启用/目标hash存在但停用”等候选；不批量启用旧版。回退采用明确反向激活命令和审计，已生成任务不改versionId；不得删发布历史。

产品口径：无须新增产品决定；按已确认规则与本方案实施。

[原问题证据](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/修复工作副本_2026-09-08/output/full-project-audit-20260913/hr/findings.md:144>)；[本项设计来源](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/修复工作副本_2026-09-08/output/grok-repair-plan-20260913/hr-design.json:596>)。

#### HR-N09 · P2 · 签约包第二份文件生成失败时，前面已落盘的签名PDF不进入回滚清理

原触发条件：一个签约包需要签多份文件；第一份 generateSignedPdf 已成功 promote 签名和PDF，第二份因PDF损坏/资源错误/存储错误抛异常。

已接受的条件与边界：多文件签署中前一份已经 promote 落盘、后续生成抛错，循环后的回滚清理登记未执行。仅完整源码链路；未做真实文件故障/事务回滚，整包硬删除另有清理，不能写作永远不可清理。

原证据层级：`E2-static-chain`。E2：调用层、落盘层、rollback登记、正常cleanup、硬删除cleanup完整静态对照；没有真实落盘故障/数据库回滚复现，不能声称线上已产生文件。

具体改法：

1. 最小修复先把每份SignedPdfResult的回滚登记移到该份成功生成后立即执行，再加入结果集合；覆盖所有同类多文档循环，不等到整批循环结束。保留事务回调；明确无事务调用时由调用者同步失败清理，不以无同步回调静默通过。
2. 补齐单份部分promote失败：签名先归档、PDF归档失败时，在generateSignedPdf内部记录本次新建且实际promote的路径/Hash，失败按所有权和Hash删除这些未提交归档；finally仍清临时stage。不要只移动循环登记而漏掉没有返回SignedPdfResult的半成品。
3. 复用受管路径校验、UUID新文件名、不可覆盖和discardUncommitted(expectedHash)，清理范围仅本操作新生成的文件，不能删除原阅读PDF、已签文件或另一次成功重试的证据。清理异常不遮住原业务失败，应记录可重试清单并保留精确路径/Hash/操作ID。
4. 正常异常/事务回滚与进程崩溃分开：afterCompletion解决前者；进程被杀前已promote、事务未提交的遗留只作为只读“受管文件-数据库引用”盘点候选。后续修复策略为隔离/留存期/引用复核后按批准清单清理，不承诺靠一次finally解决崩溃恢复，不扫描即删整包。

已存在相关文件：

- [erp-modules/erp-oa/src/main/java/com/erp/oa/service/impl/OaSignPackageServiceImpl.java](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/Grok实施工作副本_20260912/erp-modules/erp-oa/src/main/java/com/erp/oa/service/impl/OaSignPackageServiceImpl.java>)
- [erp-modules/erp-oa/src/main/java/com/erp/oa/service/impl/OaSignPackageFileLifecycle.java](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/Grok实施工作副本_20260912/erp-modules/erp-oa/src/main/java/com/erp/oa/service/impl/OaSignPackageFileLifecycle.java>)
- [erp-modules/erp-oa/src/main/java/com/erp/oa/service/impl/OaSignedPdfService.java](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/Grok实施工作副本_20260912/erp-modules/erp-oa/src/main/java/com/erp/oa/service/impl/OaSignedPdfService.java>)
- [erp-modules/erp-oa/src/main/java/com/erp/oa/service/impl/OaSignFileStorageService.java](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/Grok实施工作副本_20260912/erp-modules/erp-oa/src/main/java/com/erp/oa/service/impl/OaSignFileStorageService.java>)
- [erp-modules/erp-oa/src/main/java/com/erp/oa/domain/vo/SignedPdfResult.java](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/Grok实施工作副本_20260912/erp-modules/erp-oa/src/main/java/com/erp/oa/domain/vo/SignedPdfResult.java>)

可复用机制：FileLifecycle已注册事务afterCompletion；Storage stage UUID、promote不可覆盖、Hash检查discardUncommitted；SignedPdf finally当前只清stage，需补归档部分失败。

依赖/并行边界：HR-N10旧合同文件失败需同类原则，但不能直接假定两代storage API相同

验收与失败恢复：

- [临时真实文件系统] 第二份渲染异常、签名promote成功后PDF promote失败、证书生成失败：本次stage和未提交新归档全部清理，原模板/阅读/旧签文件Hash不变。
- [Spring事务+隔离MySQL+文件] 所有文件生成后DB更新失败、事务回滚、并发签同包/同requestId、重试：原幂等与签名证据一致；成功commit文件保留，回滚只删本操作所有物。
- [故障恢复] 清理删除失败产生明确待清理记录而不吞原错误；进程强杀用只读盘点识别孤儿候选，证明工具不会把有效引用文件当孤儿。
- [PDF页面] 重试成功后实际阅读PDF/签名/证书可打开，签名位置、证据链hash与旧功能一致。

迁移与回退：

- 无需删除或改写历史文档。先只读盘点路径、Hash、DB引用与时间，历史清理另给清单/备份/留存与可恢复隔离方案，绝不根据文件名直接删。
- 回退应用不回退合法签署数据；已隔离文件保留原路径/Hash及恢复映射。服务存储根目录保持既有私有受管根，不引入公开临时链接。

产品口径：无须新增产品决定；按已确认规则与本方案实施。

[原问题证据](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/修复工作副本_2026-09-08/output/full-project-audit-20260913/hr/findings.md:167>)；[本项设计来源](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/修复工作副本_2026-09-08/output/grok-repair-plan-20260913/hr-design.json:734>)。

#### HR-N10 · P2 · 保留的旧劳动合同签署与作废接口存在状态互相覆盖竞态

原触发条件：数据库仍有旧 oa_labor_contract.pending_sign 记录；员工经 /mobile/contract 签署，同时另一有权限请求调用保留的 /laborContract/{id}/void。PC 已隐藏作废按钮，本项是历史数据＋保留API并发条件，不是新合同日常入口。

已接受的条件与边界：需仍有旧 oa_labor_contract.pending_sign 数据，员工使用保留手机签署入口，同时有权限请求调用保留作废 API。PC 已隐藏作废按钮；旧待签数量未查，非新签约包日常流程。

原证据层级：`E2-static-chain`。E2：保留路由/按钮/API→真实服务→mapper静态并发交错分析；未并发调用真实合同接口，未验证线上是否仍有旧待签记录。

具体改法：

1. 只读确认旧pending_sign是否存在及保留API使用范围；PC隐藏作废按钮继续隐藏，不为修竞态重新开放旧流程。员工所有权、管理权限、组织范围、冻结模板/文档hash及二次签署确认保持。
2. 为旧合同新增受限selectByIdForUpdate，sign/void以及其他会改该合同状态的保留入口都使用同一行锁；在锁内重新读取并校验当前状态、权限和文档版本。禁止两个入口各读旧pending_sign后用通用update无条件覆盖。
3. 新增专用状态迁移SQL并检查影响行数：签署WHERE id+pending_sign+预期documentVersion/hash；作废WHERE id+允许原状态。签署胜出后作废必须明确走解除/终止，作废胜出后签署不得生成成功事件；重复已作废保持既有幂等。
4. 第一阶段保持现有事务内文件生成并同合同串行，锁超时返回可重试冲突，避免为缩短锁临时发明跨状态窗口。为生成后SQL失败登记旧文件体系的同步/回滚清理；事件只随成功状态事务提交。较长生成耗时需隔离负载验证后再决定单独预生成/CAS方案。

已存在相关文件：

- [erp-modules/erp-oa/src/main/java/com/erp/oa/service/impl/OaLaborContractServiceImpl.java](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/Grok实施工作副本_20260912/erp-modules/erp-oa/src/main/java/com/erp/oa/service/impl/OaLaborContractServiceImpl.java>)
- [erp-modules/erp-oa/src/main/resources/mapper/oa/OaLaborContractMapper.xml](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/Grok实施工作副本_20260912/erp-modules/erp-oa/src/main/resources/mapper/oa/OaLaborContractMapper.xml>)
- [erp-modules/erp-oa/src/main/java/com/erp/oa/mapper/OaLaborContractMapper.java](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/Grok实施工作副本_20260912/erp-modules/erp-oa/src/main/java/com/erp/oa/mapper/OaLaborContractMapper.java>)
- [erp-modules/erp-oa/src/main/java/com/erp/oa/controller/OaLaborContractController.java](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/Grok实施工作副本_20260912/erp-modules/erp-oa/src/main/java/com/erp/oa/controller/OaLaborContractController.java>)

可复用机制：已有@Transactional、本人/组织校验、签署冻结文档核对；现通用update仅where contract_id，需专用条件更新。

依赖/并行边界：同HR-SIGN-FILES共享验收思路，旧documentService另核实际受管存储后接清理

验收与失败恢复：

- [隔离MySQL两事务] sign先锁/void先锁两种屏障交错，各只能一条终态；签署/作废事件与终态一致，无signedTime+voided状态混存。
- [文件+事务] 归档失败、SQL条件更新0行、事件写失败均回滚并清本次文件；已有历史签署证据不删。
- [权限/页面] 旧本人pending_sign可签，非本人/跨组织拒绝；作废已签仍禁止；零旧待签数据的部署也不新造记录、不称线上已发生竞态。

迁移与回退：

- 通常仅新增mapper锁/CAS方法，无需改表；若现有索引不足按隔离EXPLAIN决定，不给旧合同补签署状态。
- 历史状态矛盾只读盘点，依据原事件/证据人工核对后独立修复，不能用脚本猜最终状态。回退不改合同历史；临时风险控制可暂停旧写入口并保留查询，但是否停用须基于真实旧待签盘点。

产品口径：无须新增产品决定；按已确认规则与本方案实施。

[原问题证据](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/修复工作副本_2026-09-08/output/full-project-audit-20260913/hr/findings.md:189>)；[本项设计来源](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/修复工作副本_2026-09-08/output/grok-repair-plan-20260913/hr-design.json:829>)。

#### HR-N11 · P3 · 员工详情连续渲染三份当前健康证，重复内容挤占主要操作区域

原触发条件：HR 打开任一有 detail 数据的员工档案详情抽屉；三个健康证区块均没有互斥条件。

已接受的条件与边界：保留原报告的触发条件、当前/较新副本对照和证据边界；未新增生产事故或实际环境验收结论。

原证据层级：`E1-template-source`。E1：真实生产 Vue 模板静态确认三个无互斥区块；未运行浏览器渲染或测量实际屏幕滚动距离。

具体改法：

1. 删除前两份重复当前健康证区块，只保留现有语义完整的hr-health-section一份；保留状态、附件、办理/到期字段及档案主要操作位置。
2. 随HR-N01在同一位置增加待生效续证摘要（有才显示），当前证与续证不同状态不得以重复“当前健康证”展示；不改字段权限或附件预览授权。

已存在相关文件：

- [erp-ui/src/views/hr/components/HrProfileDetailDrawer.vue](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/Grok实施工作副本_20260912/erp-ui/src/views/hr/components/HrProfileDetailDrawer.vue>)

依赖/并行边界：HR-N01状态文案一起验收，可先独立去重

验收与失败恢复：

- [模板静态] 当前健康证标题/绑定只一份；有无detail状态均无重复块。
- [浏览器PC] 有证、无证、到期、未来续证各看一次；主要操作不被重复块挤走，键盘焦点、窄窗口和附件入口可用。无需为删除重复模板写镜像实现的单元测试。

迁移与回退：

- 无迁移/数据改变；可独立回退模板，后端新增状态有未知状态回退文案。

产品口径：无须新增产品决定；按已确认规则与本方案实施。

[原问题证据](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/修复工作副本_2026-09-08/output/full-project-audit-20260913/hr/findings.md:213>)；[本项设计来源](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/修复工作副本_2026-09-08/output/grok-repair-plan-20260913/hr-design.json:913>)。

#### HR-N13 · P2 · 手机资料完整度“加载更多”失败后跳过整页员工

原触发条件：资料完整度清单超过一页；第一页20人后点击加载更多，第2页临时网络失败；员工/HR 再点击加载更多。

已接受的条件与边界：保留原报告的触发条件、当前/较新副本对照和证据边界；未新增生产事故或实际环境验收结论。

原证据层级：`real-source-offline-probe-plus-stated-static-boundary`。E3：两副本真实 loadMore/load 离线方法，第2页失败后重试输出 requestedPages=[2,3]、pageAfterRetry=3、missingPage=2。接口和分页基类已有静态确认。

具体改法：

1. 把load(requestedPage)传参冻结，loadMore传pageNum+1但不预加；只有当前有效请求成功后提交pageNum，失败保留已加载行与原页，提供“重试第N页”。
2. 复用较新员工列表的listReadEpoch/dept隔离，补充完整筛选和route上下文；reload/切组织/离开/销毁使旧页失效。append使用requestedPage，不用响应时可变pageNum；旧失败/finally不污染新查询。
3. 不要把旧员工HR-03再报新问题；它仅作为基线修复来源。完整度列表仍保留展开缺失字段状态；新筛选时才清掉不属于该查询的展开状态。

已存在相关文件：

- [erp-ui/src/views/mobile/hr/completeness/index.vue](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/Grok实施工作副本_20260912/erp-ui/src/views/mobile/hr/completeness/index.vue>)
- [erp-ui/src/views/mobile/hr/employee/index.vue](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/Grok实施工作副本_20260912/erp-ui/src/views/mobile/hr/employee/index.vue>)
- [erp-ui/src/api/hr/completeness.js](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/Grok实施工作副本_20260912/erp-ui/src/api/hr/completeness.js>)
- [erp-modules/erp-system/src/main/java/com/erp/system/controller/HrCompletenessController.java](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/Grok实施工作副本_20260912/erp-modules/erp-system/src/main/java/com/erp/system/controller/HrCompletenessController.java>)

可复用机制：较新employee199-230实际requestedPage成功提交；当前旧HR-03[2,3]、较新[2,2]已留档。

依赖/并行边界：先映射较新旧HR-03实现到实施基线，不再计数；与HR-UI01共享读代次模式

验收与失败恢复：

- [方法] page2失败后请求序列[2,2]，成功后pageNum=2；重复点击只有一个在途页；返回空页或total减少也正确终止。
- [方法/浏览器] page2等待时reload/换组织，新page1先回、旧page2后回/失败不追加不覆盖；超过20条合成记录连续浏览无漏/重页。
- [接口] 保留服务器分页和数据范围；不改后端BaseController分页机制或把全表加载到客户端。

迁移与回退：

- 无结构/历史数据迁移；仅修当前浏览状态。回退不删cache或员工数据。

产品口径：无须新增产品决定；按已确认规则与本方案实施。

[原问题证据](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/修复工作副本_2026-09-08/output/full-project-audit-20260913/hr/findings.md:257>)；[本项设计来源](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/修复工作副本_2026-09-08/output/grok-repair-plan-20260913/hr-design.json:1033>)。


### B06 库存查询与手机入口

#### INV-N06 · P2 · OE/礼盒可选文字字段清空后提示成功但旧值保留

原触发条件：将OE/礼盒可选文字明确清空，归一化为null后动态更新SQL跳过该字段。

已接受的条件与边界：保留原报告的触发条件、当前/较新副本对照和证据边界；未新增生产事故或实际环境验收结论。

原证据层级：`in-memory-sql-with-modeled-normalization`。内存SQL复现旧值保留；Java归一化在探针中建模，未运行Java方法。两副本同逻辑。

保留规则：

- 图片上传/删除、编辑会话及供应商OE引用保护已有修复不回退。
- Excel导入保持启用，空白覆盖规则不擅改；活跃固定资产强制字段仍不可清空。

具体改法：

1. 区分JSON未提供字段与用户明确清空：在服务器绑定阶段记录可选字段presence，再按现有normalize转null；Mapper对白名单中“已提供”的字段写NULL/值，未提供的字段不更新。不要把动态SQL所有if一律删掉。
2. OE/礼盒完整编辑请求中可选描述、规格/等级/单位/备注、在现有业务允许下的供应商信息支持明确清空；必填名称/分类、固定资产购买参考完整性及其他既有校验照旧。图片沿用较新imageUrlsText/图片空列表策略，不改回旧imageUrl处理。
3. 状态单独更新或旧客户端部分字段更新不能清掉未提供内容；服务器派生存在标志，不信任客户端任意列名。导入路径继续保留“空白默认不覆盖”的现有有效语义，不从本次UI清空修复推导Excel空格清除全部列。
4. 保存仍在较新lockOe/lockGift中读原记录并合并更新；返回数据库真实结果，原表单在失败时保留。清空后详情/再次编辑必须显示空而非回退旧值。

已存在相关文件：

- [erp-modules/erp-inventory/src/main/java/com/erp/inventory/service/impl/InvOeServiceImpl.java](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/Grok实施工作副本_20260912/erp-modules/erp-inventory/src/main/java/com/erp/inventory/service/impl/InvOeServiceImpl.java>)
- [erp-modules/erp-inventory/src/main/java/com/erp/inventory/service/impl/InvGiftServiceImpl.java](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/Grok实施工作副本_20260912/erp-modules/erp-inventory/src/main/java/com/erp/inventory/service/impl/InvGiftServiceImpl.java>)
- [erp-modules/erp-inventory/src/main/java/com/erp/inventory/domain/InvOeItem.java](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/Grok实施工作副本_20260912/erp-modules/erp-inventory/src/main/java/com/erp/inventory/domain/InvOeItem.java>)
- [erp-modules/erp-inventory/src/main/java/com/erp/inventory/domain/InvGiftBox.java](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/Grok实施工作副本_20260912/erp-modules/erp-inventory/src/main/java/com/erp/inventory/domain/InvGiftBox.java>)
- [erp-modules/erp-inventory/src/main/resources/mapper/inventory/InvOeMapper.xml](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/Grok实施工作副本_20260912/erp-modules/erp-inventory/src/main/resources/mapper/inventory/InvOeMapper.xml>)
- [erp-modules/erp-inventory/src/main/resources/mapper/inventory/InvGiftMapper.xml](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/Grok实施工作副本_20260912/erp-modules/erp-inventory/src/main/resources/mapper/inventory/InvGiftMapper.xml>)
- [erp-modules/erp-inventory/src/main/java/com/erp/inventory/controller/InvOeController.java](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/Grok实施工作副本_20260912/erp-modules/erp-inventory/src/main/java/com/erp/inventory/controller/InvOeController.java>)
- [erp-modules/erp-inventory/src/main/java/com/erp/inventory/controller/InvGiftController.java](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/Grok实施工作副本_20260912/erp-modules/erp-inventory/src/main/java/com/erp/inventory/controller/InvGiftController.java>)
- [erp-ui/src/views/inventory/oe/index.vue](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/Grok实施工作副本_20260912/erp-ui/src/views/inventory/oe/index.vue>)
- [erp-ui/src/views/inventory/gift/index.vue](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/Grok实施工作副本_20260912/erp-ui/src/views/inventory/gift/index.vue>)

拟新增：`erp-modules/erp-inventory/src/main/java/com/erp/inventory/domain/dto/InvOeEditRequest.java`；`erp-modules/erp-inventory/src/main/java/com/erp/inventory/domain/dto/InvGiftEditRequest.java`。

可复用机制：较新编辑/导入已有主档ForUpdate；imageUrlsText非null写入已区分图片显式空值，可借鉴存在语义但不复用为文本字段开关。

依赖/并行边界：与INV-N03共享服务/mapper；先落锁/引用保护再做字段presence，INV-UI01前端改动独立小补丁。

验收与失败恢复：

- [MySQL+Java] 每个允许清空字段分别输入空串/空格/null，落库NULL且再开为空；JSON省略字段保持旧值；校验失败整个更新回滚。
- [接口] 只发status的请求不清规格/单位/图片；不接受恶意providedFields列名；固定资产必填信息清空被现有规则拒绝。
- [文件/测试库] 导入空白仍保留旧文字，勾选更新不等于清空未提供列；图片清空回归旧48测试。
- [页面弱网] 保存失败、关闭A再开B、重试等仍保留相应表单输入且A响应不影响B。

迁移与回退：

- 无业务列DDL；新增DTO/Mapper需同批部署并保留旧部分更新兼容。
- 清空是用户确认后的有效修改，不因代码回退恢复历史旧值；若要纠正误清，必须凭变更审计/备份逐条确认。

产品口径：无；仅修复当前可选字段的明确清空。Excel允许“空白覆盖清空”的新模式不在本批，不重问已确认导入规则。

[原问题证据](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/修复工作副本_2026-09-08/output/full-project-audit-20260913/inventory/findings.md:51>)；[本项设计来源](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/修复工作副本_2026-09-08/output/grok-repair-plan-20260913/inventory-design.json:386>)。

#### INV-N07 · P2 · 客户服务历史超过100条后，旧记录在所有现有详情入口不可访问

原触发条件：客户服务历史超过100条时，详情固定取最近100条且页面无更早记录入口。

已接受的条件与边界：保留原报告的触发条件、当前/较新副本对照和证据边界；未新增生产事故或实际环境验收结论。

原证据层级：`in-memory-sql-plus-static-chain`。实际查询对内存101条样本返回100；API/PC/H5入口静态核对。不是数据库丢失。

保留规则：

- 服务记录追加不可随意覆盖，现有requestKey幂等、客户归属、权限和历史审计保留。

具体改法：

1. 新增受inv:customerCard:query保护的GET service-card/{customerId}/records分页读入口，先复用assertScopedCard再查询记录；与已有追加records POST路径兼容，保留no-store。
2. 最近20条+总数/hasMore，按(service_date DESC,record_id DESC)游标加载更早，支持日期与关键词过滤。游标包含最后两字段及首屏snapshotMaxRecordId；过滤改变清游标，同屏追加新记录不让旧页重复/漏页。
3. 查询复用已有(customer_id,service_date,record_id)复合索引；显式pageSize上限，不以一次返回全部记录替代limit100。记录总数和数据使用同一客户/范围/过滤条件；关键词参数化。
4. PC和手机客户详情接入同一历史区：追加记录成功回显当前客户并保持浏览位置；加载更早失败不前移游标、可原地重试；客户/组织/筛选变化使旧响应失效。旧详情内嵌最近记录为兼容保留，增加截断/更多提示，不静默把旧客户端从100条降成20。
5. 不修改/删除历史记录，不新增未经要求的全历史导出；后续若有导出需求复用同一查询权限和过滤。

已存在相关文件：

- [erp-modules/erp-inventory/src/main/java/com/erp/inventory/controller/InvCustomerController.java](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/Grok实施工作副本_20260912/erp-modules/erp-inventory/src/main/java/com/erp/inventory/controller/InvCustomerController.java>)
- [erp-modules/erp-inventory/src/main/java/com/erp/inventory/service/IInvCustomerServiceCardService.java](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/Grok实施工作副本_20260912/erp-modules/erp-inventory/src/main/java/com/erp/inventory/service/IInvCustomerServiceCardService.java>)
- [erp-modules/erp-inventory/src/main/java/com/erp/inventory/service/impl/InvCustomerServiceCardServiceImpl.java](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/Grok实施工作副本_20260912/erp-modules/erp-inventory/src/main/java/com/erp/inventory/service/impl/InvCustomerServiceCardServiceImpl.java>)
- [erp-modules/erp-inventory/src/main/java/com/erp/inventory/mapper/InvCustomerServiceCardMapper.java](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/Grok实施工作副本_20260912/erp-modules/erp-inventory/src/main/java/com/erp/inventory/mapper/InvCustomerServiceCardMapper.java>)
- [erp-modules/erp-inventory/src/main/resources/mapper/inventory/InvCustomerServiceCardMapper.xml](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/Grok实施工作副本_20260912/erp-modules/erp-inventory/src/main/resources/mapper/inventory/InvCustomerServiceCardMapper.xml>)
- [erp-modules/erp-inventory/src/main/java/com/erp/inventory/domain/vo/InvCustomerServiceCardVo.java](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/Grok实施工作副本_20260912/erp-modules/erp-inventory/src/main/java/com/erp/inventory/domain/vo/InvCustomerServiceCardVo.java>)
- [erp-ui/src/api/inventory/customer.js](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/Grok实施工作副本_20260912/erp-ui/src/api/inventory/customer.js>)
- [erp-ui/src/views/inventory/customer/index.vue](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/Grok实施工作副本_20260912/erp-ui/src/views/inventory/customer/index.vue>)
- [erp-ui/src/views/mobile/customer/index.vue](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/Grok实施工作副本_20260912/erp-ui/src/views/mobile/customer/index.vue>)
- [sql/erp_inventory_customer_service_card_20260713.sql](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/Grok实施工作副本_20260912/sql/erp_inventory_customer_service_card_20260713.sql>)

拟新增：`erp-modules/erp-inventory/src/main/java/com/erp/inventory/domain/vo/InvCustomerServiceRecordQuery.java`；`erp-modules/erp-inventory/src/main/java/com/erp/inventory/domain/vo/InvCustomerServiceRecordPage.java`。

可复用机制：selectById→assertScopedCard已验证客户范围；records按service_date/record_id排序；已有复合索引和追加记录request_key唯一约束。

依赖/并行边界：可独立实施；与HR/移动通用列表分页恢复方案共享request-scope风格，不改通用动作规则。

验收与失败恢复：

- [MySQL+接口] 0/20/21/101/1001条记录、同时间多记录都可完整分页，无重复漏页；不能访问其他组织无权客户；关键词/时间条件与总数一致。
- [MySQL并发] 翻更早时另人新增记录，snapshot与游标保持原结果集；刷新后可见新增；同requestKey追加仍只形成一条。
- [页面弱网] 第二页失败重试仍请求同游标；快速A→B客户/改筛选/切组织旧回包不能混入；返回客户详情保留筛选与位置。
- [兼容] 旧详情调用仍得到原有最近记录，新客户端明确显示“共N条/加载更早”，历史记录内容不被迁移改写。

迁移与回退：

- 先确认现库索引存在，已有schema已含目标复合索引，通常不需DDL；新GET接口/VO为增量。
- 回退新UI/API不删除记录或游标相关业务数据；不改原POST追加契约。

产品口径：无；20是分页大小可配置，不改变记录可见性或保留年限。

[原问题证据](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/修复工作副本_2026-09-08/output/full-project-audit-20260913/inventory/findings.md:59>)；[本项设计来源](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/修复工作副本_2026-09-08/output/grok-repair-plan-20260913/inventory-design.json:445>)。

#### INV-N08 · P2 · 库存预警包含OE/礼盒，却丢失物料身份，无法判断是哪项

原触发条件：库存预警查询包含OE/礼盒，但身份关联仅走商品表，返回名称、编码等为空。

已接受的条件与边界：保留原报告的触发条件、当前/较新副本对照和证据边界；未新增生产事故或实际环境验收结论。

原证据层级：`in-memory-sql-plus-static-chain`。实际SQL内存样本返回物料身份空；页面与服务链静态核对。

保留规则：

- 报告数据范围、成本权限和商品现有安全库存计算保留；不扩大可见组织。
- OE/礼盒参考成本是否敏感仍不是已确认缺陷，不借本项擅改权限。

具体改法：

1. 库存预警返回稳定itemType/itemId/itemCode/itemName/spec/unit/categoryType/categoryId，保留旧productId/productName等兼容字段；主查询参照InvStockMapper已有商品/OE/礼盒条件join，不能只按product_id解析。
2. 每类物料分类与单位按本类型解析；避免三表同数字ID互相串值。新增itemType/itemId筛选及多物料候选，PC表格/明细和导出同一契约显示类型、名称、编码，库存位置/批次继续展示。
3. 保留商品安全库存阈值规则；OE/礼盒当前没有对应阈值维护入口时，将阈值状态明确为“该类阈值规则尚未配置”，不得冒充低库存数量或要求用户去不存在的配置页。非正可用量可按现有空库存事实单独展示，物料身份必须完整。
4. 本批先修身份和过滤的一致性，不新增OE/礼盒阈值字段、自动补货或自动审批。后续跨模块需要低库存待办时以相同类型/阈值状态为依据，不把未知阈值当0。

已存在相关文件：

- [erp-modules/erp-inventory/src/main/resources/mapper/inventory/InvReportMapper.xml](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/Grok实施工作副本_20260912/erp-modules/erp-inventory/src/main/resources/mapper/inventory/InvReportMapper.xml>)
- [erp-modules/erp-inventory/src/main/resources/mapper/inventory/InvStockMapper.xml](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/Grok实施工作副本_20260912/erp-modules/erp-inventory/src/main/resources/mapper/inventory/InvStockMapper.xml>)
- [erp-modules/erp-inventory/src/main/java/com/erp/inventory/service/impl/InvReportServiceImpl.java](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/Grok实施工作副本_20260912/erp-modules/erp-inventory/src/main/java/com/erp/inventory/service/impl/InvReportServiceImpl.java>)
- [erp-modules/erp-inventory/src/main/java/com/erp/inventory/domain/InvStock.java](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/Grok实施工作副本_20260912/erp-modules/erp-inventory/src/main/java/com/erp/inventory/domain/InvStock.java>)
- [erp-modules/erp-inventory/src/main/java/com/erp/inventory/controller/InvReportController.java](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/Grok实施工作副本_20260912/erp-modules/erp-inventory/src/main/java/com/erp/inventory/controller/InvReportController.java>)
- [erp-ui/src/api/inventory/report.js](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/Grok实施工作副本_20260912/erp-ui/src/api/inventory/report.js>)
- [erp-ui/src/views/inventory/report/index.vue](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/Grok实施工作副本_20260912/erp-ui/src/views/inventory/report/index.vue>)
- [erp-ui/src/views/inventory/components/InventoryItemSelect.vue](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/Grok实施工作副本_20260912/erp-ui/src/views/inventory/components/InventoryItemSelect.vue>)

可复用机制：InvStockMapper已有三类物料join、itemType/itemId及分类条件，可抽取受控公共片段或对齐字段而非重新建逐行Resolver查询造成N+1。

依赖/并行边界：与INV-N10统一物料/分类键；MOB-N02低库存展示只消费正确统计，不把未配置规则算真实低库存。

验收与失败恢复：

- [MySQL] product/OE/gift同数字ID、同名不同规格、缺分类/已停用主档、不同仓库/批次样本全部返回正确身份，记录总数/分页不因join倍增。
- [MySQL+接口] 商品低库存/无阈值/零库存行为保持；OE/礼盒明确阈值状态，不能假报商品分类/单位；类型候选、表格和导出一致。
- [页面] 从报告可直接识别物料和仓库，不需转库存页猜ID；加载/筛选失败保留条件并显示错误；多类型切换旧响应不改新表格。
- [权限] 普通用户报告/导出只能看到授权范围，新增元数据不绕过成本裁剪。

迁移与回退：

- 以查询/VO兼容扩展为主，无新增业务表和阈值字段。
- 回退不变库存数量和阈值；若回滚新UI仍保留旧字段别名。

产品口径：无阻塞。若希望OE/礼盒也按自定义安全库存预警，需后续单独决定阈值归属及维护入口；本批不以该问题拖延身份修复。

[原问题证据](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/修复工作副本_2026-09-08/output/full-project-audit-20260913/inventory/findings.md:67>)；[本项设计来源](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/修复工作副本_2026-09-08/output/grok-repair-plan-20260913/inventory-design.json:504>)。

#### INV-N10 · P2 · 库存选OE/礼盒时仍展示商品分类树，分类ID被当成另一类型解释

原触发条件：PC库存选OE/礼盒仍展示商品分类树，点击节点后后台按另一类型解释同一categoryId。

已接受的条件与边界：保留原报告的触发条件、当前/较新副本对照和证据边界；未新增生产事故或实际环境验收结论。

原证据层级：`offline-vue-method-plus-static-chain`。双副本真实Vue方法输出类型与分类树不一致；查询映射静态闭环。

保留规则：

- 继续使用后端InvStockWhere按itemType解释categoryId的现有契约，不擅自合并不同分类体系。

具体改法：

1. 库存页按itemType使用既有categoryTree/oeCategoryTree/giftCategoryTree加载对应树，分类请求缓存键含类型；选中状态保存(type,categoryId)，不把相同数字当同一分类。
2. 切类型同步清itemId/productId/categoryId/选中路径、翻页归1，发起对应分类与库存刷新；在新树就绪前禁止点旧树。tree/list各自带请求序号、组织/类型/筛选快照，旧success/catch/finally不能污染新状态。
3. 全部类型时隐藏单类型分类并清categoryId，只保留“全部库存”，避免现有后台otherwise按商品分类误解释。保留批次/库位等独立筛选，切类型不清无关用户输入。
4. 失败显示所属类型的分类重试入口，保留类型且不偷偷回退商品树；页面缓存/返回时验证缓存类型与当前查询一致。

已存在相关文件：

- [erp-ui/src/views/inventory/stock/index.vue](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/Grok实施工作副本_20260912/erp-ui/src/views/inventory/stock/index.vue>)
- [erp-ui/src/api/inventory/category.js](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/Grok实施工作副本_20260912/erp-ui/src/api/inventory/category.js>)
- [erp-ui/src/api/inventory/oe.js](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/Grok实施工作副本_20260912/erp-ui/src/api/inventory/oe.js>)
- [erp-ui/src/api/inventory/gift.js](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/Grok实施工作副本_20260912/erp-ui/src/api/inventory/gift.js>)
- [erp-modules/erp-inventory/src/main/resources/mapper/inventory/InvStockMapper.xml](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/Grok实施工作副本_20260912/erp-modules/erp-inventory/src/main/resources/mapper/inventory/InvStockMapper.xml>)
- [erp-modules/erp-inventory/src/main/java/com/erp/inventory/service/impl/InvStockServiceImpl.java](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/Grok实施工作副本_20260912/erp-modules/erp-inventory/src/main/java/com/erp/inventory/service/impl/InvStockServiceImpl.java>)

可复用机制：已有三套分类tree API及库存mapper类型分支；复用已有decorateCategories和库存加载错误展示，不新增分类表。

依赖/并行边界：与INV-N08约定同一类型/分类字段；可先独立落前端类型切换保护。

验收与失败恢复：

- [页面+接口] product/OE/gift恰好相同categoryId，分别点击时发正确itemType并显示本类名称/结果；全部类型请求不带categoryId。
- [页面受控异步] 快速product→gift→OE、分类慢/失败回包，旧树及旧库存均不覆盖OE；筛选/翻页/返回仍保持类型与分类一致。
- [MySQL只读集成] 使用三类有冲突ID的数据验证筛选范围，祖先分类及空分类结果正确，授权组织条件仍有效。
- [页面] 切类型自动刷新一次；新树失败原地重试，不要求用户刷新整页或重新录入批次/货位条件。

迁移与回退：

- 纯查询交互，不需要DDL或数据回填；缓存键加版本防止旧本地状态复用。
- 回退只清该页面新分类缓存，不动业务库存。

产品口径：无；全部类型隐藏单类型树属于消除错误筛选的保守默认，不更改分类业务结构。

[原问题证据](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/修复工作副本_2026-09-08/output/full-project-audit-20260913/inventory/findings.md:83>)；[本项设计来源](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/修复工作副本_2026-09-08/output/grok-repair-plan-20260913/inventory-design.json:620>)。

#### INV-UI01 · P2 · OE 首次编辑已有记录后，供应商下拉被占位选项阻断加载

原触发条件：账户有供应商档案查询权限，另有合作中的供应商B；进入OE管理后直接编辑已有供应商A的OE，再打开供应商下拉希望改为B；此前未在空白新建表单加载过供应商列表。

已接受的条件与边界：保留原报告的触发条件、当前/较新副本对照和证据边界；未新增生产事故或实际环境验收结论。

原证据层级：`real-source-offline-probe-plus-stated-static-boundary`。实际Vue openForm→ensureSelectedSupplier→loadSuppliersOnOpen/loadSuppliers 的内存探针，当前与Grok均复现；同源create-first控制组均正常请求1次并可选A/B。

保留规则：

- 不修改供应商名称引用策略，INV-F1已修的主数据引用保护保留；不自动替换用户选择。

具体改法：

1. OE supplierOptions保留选中项占位，但另加supplierLoaded与supplierLoadPromise表达真实请求状态；loadSuppliers不能以数组非空判断加载完成。
2. 首次编辑后打开下拉始终完成一次合作中供应商真实加载；结果按supplierId/供应商标识去重合并当前选中占位，优先真实档案电话/编码。占位只作历史回显，不代表可选新供应商。
3. 加载失败不设loaded=true，保留当前值并在下拉附近提供重试；权限/组织或账号会话变化使旧promise结果失效，清仅候选缓存而不丢未保存表单。
4. 保留较新editorSession/isEditorSession、图片数组和购买参考保存守卫；对选中已停用/非合作供应商可显示历史值，但不能用占位绕过新选项业务约束。

已存在相关文件：

- [erp-ui/src/views/inventory/oe/index.vue](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/Grok实施工作副本_20260912/erp-ui/src/views/inventory/oe/index.vue>)
- [erp-ui/src/api/inventory/supplier.js](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/Grok实施工作副本_20260912/erp-ui/src/api/inventory/supplier.js>)
- [erp-ui/src/views/inventory/product/index.vue](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/Grok实施工作副本_20260912/erp-ui/src/views/inventory/product/index.vue>)

可复用机制：商品页ensureSupplierOptions会加载真实列表可作为操作参照；OE较新编辑会话保护已存在，按原机制处理owner。

依赖/并行边界：独立小补丁；与INV-N06共改OE页时按行合并，不覆盖图片/编辑会话实现。

验收与失败恢复：

- [页面/真实方法] 刚进入页面先编辑A，再开下拉能请求并选B；先新建再编辑对照仍正常；取消编辑后新建仍有完整候选。
- [页面受控异步] 首次失败后重试、两个并发打开只复用一次加载、切账号/组织后旧请求迟到均不污染新选择。
- [接口] 无供应商权限不绕过权限；历史停用供应商只回显、不伪装成有效合作候选；选B电话随真实B档案填入。
- [回归] 已有OE图片上传、清空、购买参考和保存中关闭/新开记录保护全部仍通过。

迁移与回退：

- 无DDL；候选内存状态随组件/缓存版本失效即可。
- 回退只撤前端增量逻辑，不撤较新编辑会话或供应商引用代码。

产品口径：无。

[原问题证据](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/修复工作副本_2026-09-08/output/full-project-audit-20260913/inventory/final-ui-findings.md:3>)；[本项设计来源](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/修复工作副本_2026-09-08/output/grok-repair-plan-20260913/inventory-design.json:671>)。

#### MOB-N02 · P2 · 实际手机工作台三类统计长期显示初始化的零

原触发条件：实际手机门店/仓库首页汇总成功，今日销售、待盘点、待退货仍显示初始化零。

已接受的条件与边界：保留原报告的触发条件、当前/较新副本对照和证据边界；未新增生产事故或实际环境验收结论。

原证据层级：`offline-real-helper-plus-static-contract`。两副本真实profile/mapper执行加后端VO静态核对；未读取真实营业数据。

保留规则：

- 汇总只读，不自动审批、收发或创建单据；统计不能扩大到当前用户无权处理/查看的记录。
- 保留未检库存隔离及D15消息分页/筛选规则。

具体改法：

1. 指标改用稳定metric.key绑定返回字段，label仅负责文案。summary缺字段/接口失败用null和“未获取”展示，禁止保留初始化0；真实返回0才显示0。保留现有待入库、待出库、低库存映射。
2. 待盘点直接复用本用户current-org TodoSummary的INV_STOCK_CHECK_EXECUTE；待退货按工作台类型分别用INV_SALES_RETURN_CONFIRM（门店）或INV_PURCHASE_RETURN_CONFIRM（仓库），避免把审批、草稿或另一流程重复计为可执行任务。
3. 仅店铺/STORE工作台计算并返回今日销售：服务端基于已通过组织授权校验的selectedDeptId查询真实selectedDeptType，确认STORE后才调用销售聚合，不能信任客户端传入类型。WAREHOUSE工作台不调用该销售聚合、不返回今日销售业务值、不新增销售指标，保留现有仓库待收/发/盘点/退货指标；未知类型不得回退当店铺。今日销售单位沿用现有“单”，完整复用IInvReportService.selectReportSummary(query,selectedDeptId).getSalesOrderCount()，不另写按状态简单计数。现有SQL为count(distinct o.order_id)：订单状态submitted/noticed/delivered，且至少一条参与筛选的明细coalesce(d.delivered_quantity,0)>0。仅已提交/已通知但尚无发货的单为0；多条已发明细只计一单；不等于今日新建单或今日出库事件数。
4. 查询由服务端构造InvStock：params.beginTime/endTime为同一个服务端业务日期，回传reportDate供页面说明，沿用现有运行时区配置并在部署验收核对Java与数据库日期一致。ReportOrderScope保留coalesce(o.order_date,date(o.create_time))的闭区间；因此昨天业务日期的单今天首次发货仍不属于今天业务日期的销售单，不擅自改成发货时间。
5. 严格复用prepareReportQuery的范围：先要求并校验selectedDeptId；将shopDeptId和warehouseId写为该组织，移除外部scopeDeptIds，订单按o.shop_dept_id等于当前选择组织统计，不扩展为子树或关联组织。本指标不接受客户端传productId/categoryId等过滤；工作台无物料筛选时二者为空。若复用方法用于已有报表筛选，ReportOrderProductScope仍按d.product_id及商品分类本级/ancestors筛选，不能悄悄删掉过滤条件或把它宣称为独立物料权限。
6. 现有报表入口要求inv:report:list。手机summary虽只要求登录，新增今日销售字段必须在服务层用AuthUtil.hasPermi("inv:report:list")检查同一权限语义（含现有管理员/通配规则），无权限时返回不可用/隐藏而不是0，不以inv:sales:list替代。内部仅向MobileWorkbenchSummary复制salesOrderCount；不得透传原报表VO及其中成本/利润，原成本权限和报表no-store策略保留。
7. summary一次返回selectedDeptId/type、指标值、口径标识、reportDate及必要更新时间；前端校验响应上下文并使用request epoch，切组织/刷新时旧success/failure/finally不得覆盖新工作台。销售统计异常只标该指标未获取，不把整组已成功待办清零。
8. 盘点/退货点击直达同类型、同组织的既有可执行待办；今日销售本批保持只读统计并说明“当日业务日期、已发货销售单数”，不把仅按状态筛选的销售列表冒充同口径下钻明细。复用当前页汇总错误/重试UI，不访问不可达的旧mobile/inventory工作台。

已存在相关文件：

- [erp-ui/src/views/mobile/mobileNavigation.js](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/Grok实施工作副本_20260912/erp-ui/src/views/mobile/mobileNavigation.js>)
- [erp-ui/src/views/mobile/components/MobileWorkbenchShell.vue](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/Grok实施工作副本_20260912/erp-ui/src/views/mobile/components/MobileWorkbenchShell.vue>)
- [erp-ui/src/views/mobile/components/mobileWorkbenchPolicy.js](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/Grok实施工作副本_20260912/erp-ui/src/views/mobile/components/mobileWorkbenchPolicy.js>)
- [erp-ui/src/api/inventory/mobile.js](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/Grok实施工作副本_20260912/erp-ui/src/api/inventory/mobile.js>)
- [erp-modules/erp-inventory/src/main/java/com/erp/inventory/service/impl/InvMobileServiceImpl.java](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/Grok实施工作副本_20260912/erp-modules/erp-inventory/src/main/java/com/erp/inventory/service/impl/InvMobileServiceImpl.java>)
- [erp-modules/erp-inventory/src/main/java/com/erp/inventory/domain/vo/MobileWorkbenchSummary.java](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/Grok实施工作副本_20260912/erp-modules/erp-inventory/src/main/java/com/erp/inventory/domain/vo/MobileWorkbenchSummary.java>)
- [erp-modules/erp-inventory/src/main/java/com/erp/inventory/constant/InvTodoTypes.java](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/Grok实施工作副本_20260912/erp-modules/erp-inventory/src/main/java/com/erp/inventory/constant/InvTodoTypes.java>)
- [erp-modules/erp-inventory/src/main/resources/mapper/inventory/InvTodoMapper.xml](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/Grok实施工作副本_20260912/erp-modules/erp-inventory/src/main/resources/mapper/inventory/InvTodoMapper.xml>)
- [erp-modules/erp-inventory/src/main/resources/mapper/inventory/InvSalesOrderMapper.xml](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/Grok实施工作副本_20260912/erp-modules/erp-inventory/src/main/resources/mapper/inventory/InvSalesOrderMapper.xml>)
- [erp-modules/erp-inventory/src/main/java/com/erp/inventory/service/impl/InvSalesServiceImpl.java](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/Grok实施工作副本_20260912/erp-modules/erp-inventory/src/main/java/com/erp/inventory/service/impl/InvSalesServiceImpl.java>)
- [erp-modules/erp-inventory/src/main/resources/mapper/inventory/InvReportMapper.xml](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/Grok实施工作副本_20260912/erp-modules/erp-inventory/src/main/resources/mapper/inventory/InvReportMapper.xml>)
- [erp-modules/erp-inventory/src/main/java/com/erp/inventory/service/IInvReportService.java](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/Grok实施工作副本_20260912/erp-modules/erp-inventory/src/main/java/com/erp/inventory/service/IInvReportService.java>)
- [erp-modules/erp-inventory/src/main/java/com/erp/inventory/service/impl/InvReportServiceImpl.java](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/Grok实施工作副本_20260912/erp-modules/erp-inventory/src/main/java/com/erp/inventory/service/impl/InvReportServiceImpl.java>)
- [erp-modules/erp-inventory/src/main/java/com/erp/inventory/controller/InvReportController.java](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/Grok实施工作副本_20260912/erp-modules/erp-inventory/src/main/java/com/erp/inventory/controller/InvReportController.java>)
- [erp-modules/erp-inventory/src/main/java/com/erp/inventory/controller/InvMobileController.java](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/Grok实施工作副本_20260912/erp-modules/erp-inventory/src/main/java/com/erp/inventory/controller/InvMobileController.java>)
- [erp-modules/erp-inventory/src/main/java/com/erp/inventory/service/impl/InvBaseService.java](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/Grok实施工作副本_20260912/erp-modules/erp-inventory/src/main/java/com/erp/inventory/service/impl/InvBaseService.java>)
- [erp-modules/erp-inventory/src/main/java/com/erp/inventory/domain/vo/InvReportSummary.java](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/Grok实施工作副本_20260912/erp-modules/erp-inventory/src/main/java/com/erp/inventory/domain/vo/InvReportSummary.java>)
- [erp-common/erp-common-security/src/main/java/com/erp/common/security/shop/AbstractShopScopeService.java](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/Grok实施工作副本_20260912/erp-common/erp-common-security/src/main/java/com/erp/common/security/shop/AbstractShopScopeService.java>)
- [erp-common/erp-common-security/src/main/java/com/erp/common/security/auth/AuthUtil.java](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/Grok实施工作副本_20260912/erp-common/erp-common-security/src/main/java/com/erp/common/security/auth/AuthUtil.java>)

可复用机制：InvMobileServiceImpl已只调用一次todoService.selectSummary(current-org)，TodoTypes已有盘点执行/采购退货确认/销售退货确认；无需另拼三套无权限filter统计。；现有InvReportServiceImpl.selectReportSummary→prepareReportQuery→InvReportMapper.selectReportSummary的salesOrderCount完整复用。日期、已发数量>0、订单去重、当前组织及可选商品/分类SQL条件均不重新定义；控制器的inv:report:list权限须在手机新增调用处显式保留，因为直接调service不会自动执行Controller注解。；shell已有summaryLoading/summaryLoaded/summaryError及重试，可扩展而非显示虚拟成功。

依赖/并行边界：INV-N08影响低库存身份语义；MOB-N02只复用原销售报表计数，不顺带更改报表的物料身份/成本或新增销售下钻。；复用报表完整summary会执行其余聚合，发布前需在实际测试库评估查询成本；若需要优化为专用count，必须抽取共享SQL并证明与原salesOrderCount在相同过滤下等价，不能先复制简化SQL。

验收与失败恢复：

- [MySQL只读基准] 以同一组织、同一beginTime=endTime、无物料过滤调用正式report/summary，今日销售必须等于salesOrderCount。submitted/noticed且全部delivered_quantity为0或null的订单计0；一单多条已发明细计1；其他合法状态且至少一条已发计1；draft/cancelled即使构造已发明细仍计0。
- [MySQL日期] 今天业务日期且已发计入；昨天业务日期今天发货不计；order_date为空则按date(create_time)；跨午夜/回填日期及Java/DB时区场景与正式报表完全一致，不以首次提交/实际出库时间代替。
- [MySQL组织/过滤/权限] 当前A组织仅计A，不计授权子店B或关联仓C；伪造shopDeptId/warehouseId/scopeDeptIds不能扩大。无物料过滤包括原报表可纳入的全部销售明细；共享报表实现的productId、分类子孙过滤仍有效，相关已发明细被过滤后不能靠另一条未发明细计单。仅inv:sales:list无inv:report:list不返回今日销售数；有报表权限但无成本权限只返回单数，不返回成本/利润字段。
- [MySQL待办] 非零盘点/两类退货、全零、仅草稿/审批状态分别与同组织同类型正式待办一致；不要将两类退货混为同一个执行入口。
- [页面] 未获取显示未知，真实0显示0；无报表权限隐藏销售指标；待办指标点击保留同类型/组织，今日销售文案明确是当日业务日期的已发货销售单数。
- [页面受控异步] A组织请求慢于B，A成功/失败/finally均不覆盖B；销售聚合失败只显示本指标未获取，不覆盖已成功待办；重试保持组织/日期一致。
- [MySQL并发只读] 聚合中刚发生发货/取消的变化允许下一次刷新对齐，显示更新时间；比较报表和工作台应冻结业务数据或在同一只读快照取基准，不承诺多次请求跨服务全局快照。
- [页面+服务分支] STORE且有inv:report:list时按完整口径加载今日销售；WAREHOUSE即使具有该权限也不调用销售报表聚合、不返回销售业务值且不新增销售卡片，待收/发/盘点/退货保持。伪造STORE参数不能让仓库进入销售分支；从STORE切WAREHOUSE时迟到销售回包不能添加仓库销售指标；未知组织类型不回退STORE。

迁移与回退：

- VO/接口字段增量，通常无DDL；聚合索引是否足够以真实EXPLAIN为发布前检查，不预先添加猜测索引。
- 回退仍应把无数据指标显示未知/隐藏，不能恢复硬编码零；不修改任何业务单据。

产品口径：无新增用户口径决定：完整复用现有salesOrderCount（包括已发明细>0、distinct订单、业务日期、当前组织/报表过滤）及inv:report:list权限；待盘点/退货复用既有可执行待办。若未来希望“今日新建/提交单数”或“今日发货事件单数”，属于另外的产品口径，不能冒充本次已确认规则。 今日销售范围仅沿用已有店铺/STORE工作台卡片；不为WAREHOUSE增加销售统计或销售能力。

[原问题证据](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/修复工作副本_2026-09-08/output/full-project-audit-20260913/inventory/mobile-common-supplement.md:23>)；[本项设计来源](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/修复工作副本_2026-09-08/output/grok-repair-plan-20260913/inventory-design.json:771>)。

#### MOB-N03 · P2 · 电脑消息中的签约包链接被客户端守卫送回首页

原触发条件：电脑消息点击有效签约包，先标已读再跳mobile路径，设备边界将其送回首页。

已接受的条件与边界：保留原报告的触发条件、当前/较新副本对照和证据边界；未新增生产事故或实际环境验收结论。

原证据层级：`offline-route-method-plus-static-entry`。两副本真实路由/设备识别/守卫方法，Mac与iPhone对照；未调用已读或签署API。

保留规则：

- 员工本人签署/文件权限、包状态守卫和已有手机签署步骤保持；不授予PC管理权限。
- D15筛选、分页、返回位置与已读快照语义不改；本批不开发新的电脑签署能力。

具体改法：

1. resolvePushRoute接受客户端能力参数，OA_SIGN_PACKAGE_SIGN在手机仍返回现有/mobile/sign-package；电脑返回新的登录态签约接力页并完整保留十进制字符串packageId。OA_SIGN_HR_TASK继续正式PC任务入口，未知routeType仍拒绝。
2. 电脑接力页通过已有getMyPackageDetail对应/mobile/{packageId}本人读接口获取可展示状态，给“请用手机继续签署”的二维码与复制链接；二维码只含可信当前站点origin+packageId的正式手机URL，不含token、cookie或绕过本人校验的密钥。不把员工导向需管理权限的/oa/sign-package后台。 不包含密码、身份证件、公开签署PDF或任何可直接访问私密文件的URL；手机登录后继续依现有本人/组织/状态校验。
3. 接力页是明确允许的普通桌面路由，权限守卫保持全局/mobile设备边界；不要简单放开所有手机路由。手机扫码未登录先正常登录并保留redirect/packageId，登录后仍由employeePackage权限核验。
4. 消息点击继续使用较新actionEpoch/currentScope与D15返回筛选/位置，导航失败显示可重试业务入口并保留packageId；保留既有消息点击已读语义，不另设“签完才已读”或自动回未读。受控接力页可用后再按既有点击流程导航。
5. 接力页的状态读取失败原地重试，packageId变化/切账号时失效旧响应；已签、过期、撤销显示服务端真实状态，不承诺可继续签、不自动触发签名或发送提醒。

已存在相关文件：

- [erp-ui/src/services/pushRoute.js](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/Grok实施工作副本_20260912/erp-ui/src/services/pushRoute.js>)
- [erp-ui/src/utils/clientPlatform.js](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/Grok实施工作副本_20260912/erp-ui/src/utils/clientPlatform.js>)
- [erp-ui/src/views/system/userNotification/index.vue](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/Grok实施工作副本_20260912/erp-ui/src/views/system/userNotification/index.vue>)
- [erp-ui/src/router/index.js](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/Grok实施工作副本_20260912/erp-ui/src/router/index.js>)
- [erp-ui/src/permission.js](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/Grok实施工作副本_20260912/erp-ui/src/permission.js>)
- [erp-ui/src/api/oa/signPackage.js](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/Grok实施工作副本_20260912/erp-ui/src/api/oa/signPackage.js>)
- [erp-ui/src/views/mobile/signPackage/index.vue](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/Grok实施工作副本_20260912/erp-ui/src/views/mobile/signPackage/index.vue>)
- [erp-modules/erp-oa/src/main/java/com/erp/oa/controller/OaSignPackageController.java](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/Grok实施工作副本_20260912/erp-modules/erp-oa/src/main/java/com/erp/oa/controller/OaSignPackageController.java>)
- [erp-modules/erp-oa/src/main/java/com/erp/oa/service/impl/OaSignPackageServiceImpl.java](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/Grok实施工作副本_20260912/erp-modules/erp-oa/src/main/java/com/erp/oa/service/impl/OaSignPackageServiceImpl.java>)

拟新增：`erp-ui/src/views/signPackageHandoff/index.vue`。

可复用机制：签约controller已有@RequiresLogin /mobile/{packageId}→getMyPackageDetail→employeePackage本人访问策略；手机页已有packageId观察和状态流程。；较新消息页已有actionEpoch、currentScope、saveViewState和decimalId；保留这些而仅替换能力匹配后的目标。

依赖/并行边界：与HR签约组确认已有本人读取接口返回的最小展示字段；不改变签署服务/模板。；需要项目现有本地二维码依赖或选择固定版本实现；不使用第三方在线生成服务泄露签约URL。

验收与失败恢复：

- [页面] Mac点击签约消息进入保留ID的接力页，手机点击仍进入签署页；HR任务在PC仍直达原任务，返回消息保留过滤与位置。
- [页面+接口] 他人的packageId、超安全整数但合法长ID、非法ID、已撤销/过期/已签包分别按本人权限和真实状态处理，不因接力放宽权限。
- [页面失败] 本人包详情失败、router.push失败、切消息A/B/切账号，保留可恢复入口且旧响应不覆新；已读流程不自动回滚或批量扩大范围。
- [真机] PC二维码→手机未登录→正常登录→同packageId正式签署页，含Safari/Chrome；二维码不含任何会话凭证，不执行实际签署才不能称签署全流程验收。
- [权限与隐私] 扫码原始文本只有受保护业务路由及必要packageId；复制二维码到另一个无权账号仍不能读取签约包或PDF；不开匿名文件访问。

迁移与回退：

- 新增前端路由/页无业务DDL；利用既有本人读API，二维码库需锁定依赖并离线打包。
- 回退保留消息原routeParams与packageId，不能改写历史消息为丢ID首页链接；可临时展示可复制手机链接而非静默重定向首页。

产品口径：无阻塞：建议本批维持手机签署，通过电脑接力修复入口。开放桌面正式签署是后续产品能力，不作为本修复前提。

[原问题证据](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/修复工作副本_2026-09-08/output/full-project-audit-20260913/inventory/mobile-common-supplement.md:37>)；[本项设计来源](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/修复工作副本_2026-09-08/output/grok-repair-plan-20260913/inventory-design.json:892>)。


### B07 系统配置与批量一致性

#### ROOT-03 · P2 · 字典类型批量删除会部分删除后整体报错

原触发条件：同时选择一个空字典和一个已被字典项使用的字典，服务逐个校验立即删除且无事务；第一条已删除，第二条抛异常，前端只收到失败且成功分支才刷新。

已接受的条件与边界：保留原报告的触发条件、当前/较新副本对照和证据边界；未新增生产事故或实际环境验收结论。

原证据层级：`real-source-offline-probe-plus-stated-static-boundary`。双副本提取实际 Java deleteDictTypeByIds，内存 mapper 证实 firstDictionaryStillExists=false，batchReportedFailure=true。单方法/类没有事务注解，未进行业务库删除。

具体改法：

1. deleteDictTypeByIds 先去重并按固定顺序锁定所有类型，完整校验存在性和字典项引用，再在同一事务中删除；禁止校验一条就先提交删除一条。
2. 字典项新增/换类型也遵守父类型同一锁顺序并校验存在，避免预检后并发插入造成孤儿；类型/数据 SQL 变更在同修复批次验证。
3. 数据库提交后再清理对应缓存；删除失败不清缓存。缓存清理失败与数据库删除失败分开记录并提示管理端“删除已完成，缓存刷新待重试”，复用现有刷新缓存入口并安排有界重试，不能声称跨 Redis/DB 原子事务。
4. UI 只提交冻结选中 ID，失败显示阻塞字典并保留仍存在的选择；结果未知时先读回这些 ID，明确已删除/仍存在，不能自动扩大到筛选全集。

已存在相关文件：

- [erp-modules/erp-system/src/main/java/com/erp/system/service/impl/SysDictTypeServiceImpl.java](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/Grok实施工作副本_20260912/erp-modules/erp-system/src/main/java/com/erp/system/service/impl/SysDictTypeServiceImpl.java>)
- [erp-modules/erp-system/src/main/java/com/erp/system/service/impl/SysDictDataServiceImpl.java](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/Grok实施工作副本_20260912/erp-modules/erp-system/src/main/java/com/erp/system/service/impl/SysDictDataServiceImpl.java>)
- [erp-modules/erp-system/src/main/java/com/erp/system/mapper/SysDictTypeMapper.java](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/Grok实施工作副本_20260912/erp-modules/erp-system/src/main/java/com/erp/system/mapper/SysDictTypeMapper.java>)
- [erp-modules/erp-system/src/main/resources/mapper/system/SysDictTypeMapper.xml](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/Grok实施工作副本_20260912/erp-modules/erp-system/src/main/resources/mapper/system/SysDictTypeMapper.xml>)
- [erp-modules/erp-system/src/main/resources/mapper/system/SysDictDataMapper.xml](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/Grok实施工作副本_20260912/erp-modules/erp-system/src/main/resources/mapper/system/SysDictDataMapper.xml>)
- [erp-ui/src/views/system/dict/index.vue](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/Grok实施工作副本_20260912/erp-ui/src/views/system/dict/index.vue>)

依赖/并行边界：WF04

验收与失败恢复：

- 选择空字典 A 和被引用字典 B，返回具体 B 错误且 A/B 都保留，反转输入顺序结果相同。
- 故障注入到第二条删除，事务回滚全部；缓存不能提前失效或写入未提交值。
- 并发新增字典项与类型删除不产生孤儿；重复 ID、未知 ID、空数组有确定响应。
- Redis 清理失败不报告数据库回滚，管理端可刷新并核对原 ID；超时重读不误删新增选择。

迁移与回退：

- 无需业务数据迁移；新增锁 SQL 和事务可分支回退，但退回逐条提交会恢复缺陷。缓存单独按已提交 DB 重新加载，不回填过时快照。

产品口径：无需新增产品决定，保留已确认规则。

[原问题证据](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/修复工作副本_2026-09-08/output/full-project-audit-20260913/root/findings.md:40>)；[本项设计来源](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/修复工作副本_2026-09-08/output/grok-repair-plan-20260913/workflow-design.json:274>)。

#### ROOT-04 · P2 · 字典类型改名后旧键仍从缓存返回旧数据

原触发条件：把字典类型从 old_type 改成 new_type，数据库更新后只写新键缓存，没有删除旧键；selectDictDataByType 仍优先返回旧键缓存，业务引用看起来仍有效而后续新键更新不会同步旧键。

已接受的条件与边界：保留原报告的触发条件、当前/较新副本对照和证据边界；未新增生产事故或实际环境验收结论。

原证据层级：`real-source-offline-probe-plus-stated-static-boundary`。双副本提取实际 Java updateDictType，oldTypeStillCachedAfterRename=true；读取方法的缓存优先分支已核对。

具体改法：

1. 在已有类型更新事务中保留 oldType/newType，完成类型和字典项引用更新。事务提交后同时失效旧键、新键；以已提交数据库惰性重建新键，旧键不保留为隐式别名。
2. 同一批次抽出 ROOT-03/04 的 after-commit 缓存失效处理。数据库失败不动缓存；缓存故障记录待刷新类型并有界重试/管理端刷新，响应区分已保存但刷新待处理，避免用户重复改名。
3. 前端成功后移除本地旧、新字典缓存并刷新当前列表；迟到旧类型查询按 WF01 丢弃。服务端旧类型不存在时不得把历史缓存当有效类型继续提供，可在读取入口校验类型有效性或等价版本戳；这一约束必须有并发旧请求回填测试。

已存在相关文件：

- [erp-modules/erp-system/src/main/java/com/erp/system/service/impl/SysDictTypeServiceImpl.java](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/Grok实施工作副本_20260912/erp-modules/erp-system/src/main/java/com/erp/system/service/impl/SysDictTypeServiceImpl.java>)
- [erp-modules/erp-system/src/main/java/com/erp/system/service/impl/SysDictDataServiceImpl.java](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/Grok实施工作副本_20260912/erp-modules/erp-system/src/main/java/com/erp/system/service/impl/SysDictDataServiceImpl.java>)
- [erp-modules/erp-system/src/main/resources/mapper/system/SysDictTypeMapper.xml](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/Grok实施工作副本_20260912/erp-modules/erp-system/src/main/resources/mapper/system/SysDictTypeMapper.xml>)
- [erp-modules/erp-system/src/main/resources/mapper/system/SysDictDataMapper.xml](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/Grok实施工作副本_20260912/erp-modules/erp-system/src/main/resources/mapper/system/SysDictDataMapper.xml>)
- [erp-ui/src/views/system/dict/index.vue](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/Grok实施工作副本_20260912/erp-ui/src/views/system/dict/index.vue>)
- [erp-ui/src/store/modules/dict.js](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/Grok实施工作副本_20260912/erp-ui/src/store/modules/dict.js>)

依赖/并行边界：WF01；WF04；ROOT-03

验收与失败恢复：

- 先缓存 old_type，改为 new_type 后旧类型返回无有效字典，新类型返回完整字典；随后增删新项不复活旧键。
- 更新失败或并发重名，DB/旧有效字典保持一致。
- 改名时有旧查询迟到回填，后续旧键读取仍不能返回有效历史字典。
- 数据库已提交但 Redis 故障，提示准确且可重复刷新；不可提示未保存或自动再次改名。

迁移与回退：

- 无需字典业务数据迁移；保留原改名语义。缓存可从 DB 全量重建作操作回退，不能把旧键恢复为别名。

产品口径：无需新增产品决定，保留已确认规则。

[原问题证据](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/修复工作副本_2026-09-08/output/full-project-audit-20260913/root/findings.md:57>)；[本项设计来源](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/修复工作副本_2026-09-08/output/grok-repair-plan-20260913/workflow-design.json:309>)。

#### ROOT-07 · P2 · 工资页选择“我的”后导出仍会导出当前组织范围的其他员工记录

原触发条件：具有工资导出权限的用户在个人工资视图点击“导出当前查询条件”；请求只带月份/分页，未带视图范围，后台固定调用 selectAllRecords，仅附组织范围。若该范围有其他员工记录，它们也进入导出。

已接受的条件与边界：必须已有工资导出权限，且当前组织范围内有其他员工记录；不超出组织范围。页面“我的”与导出范围不一致，不表述为未授权跨组织读取。

原证据层级：`real-source-offline-probe-plus-stated-static-boundary`。两副本实际 handleExport 探针 selectedScope=my，但请求无 userId/viewScope；Controller.export→selectAllRecords→mapper已闭环，selectMyRecords才写入当前用户ID。未下载真实工资文件。

具体改法：

1. 新增明确的 POST /oa/salary/export/my 导出入口，权限沿用工资导出权限，但服务端固定当前登录 userId 并复用 selectMyRecords 与组织范围；现有 /export 保留组织导出语义与其授权规则。不能相信前端传来的 userId。
2. 前端在打开导出确认之前冻结 viewScope、月份和其余筛选，展示“我的工资/组织工资+月份”，确认后若身份/组织/目标已变则停止并要求重新发起。scope=my 时只调用新入口；不因缺少 all 权限而偷偷退回全组织接口。
3. 导出只去掉分页字段，保留页面实际采用的有效筛选；避免提示当前查询却实际使用确认时的另一组筛选。

已存在相关文件：

- [erp-ui/src/views/oa/salary/index.vue](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/Grok实施工作副本_20260912/erp-ui/src/views/oa/salary/index.vue>)
- [erp-ui/src/api/oa/salary.js](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/Grok实施工作副本_20260912/erp-ui/src/api/oa/salary.js>)
- [erp-modules/erp-oa/src/main/java/com/erp/oa/controller/OaSalaryController.java](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/Grok实施工作副本_20260912/erp-modules/erp-oa/src/main/java/com/erp/oa/controller/OaSalaryController.java>)
- [erp-modules/erp-oa/src/main/java/com/erp/oa/service/impl/OaSalaryServiceImpl.java](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/Grok实施工作副本_20260912/erp-modules/erp-oa/src/main/java/com/erp/oa/service/impl/OaSalaryServiceImpl.java>)
- [erp-modules/erp-oa/src/main/java/com/erp/oa/mapper/OaSalaryRecordMapper.java](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/Grok实施工作副本_20260912/erp-modules/erp-oa/src/main/java/com/erp/oa/mapper/OaSalaryRecordMapper.java>)
- [erp-modules/erp-oa/src/main/resources/mapper/oa/OaSalaryRecordMapper.xml](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/Grok实施工作副本_20260912/erp-modules/erp-oa/src/main/resources/mapper/oa/OaSalaryRecordMapper.xml>)

依赖/并行边界：WF01；WF05

验收与失败恢复：

- 同组织两员工、操作者有导出权限，my 导出只含本人；all 在既有授权范围内包含查询记录。
- 伪造 userId/跨组织参数不能把 my 导出改成他人。
- 打开确认后改月份/组织或退出账号不发送旧上下文请求；主动取消不发下载。
- my/all 页面返回集合与文件记录（不计分页）一致，空结果和下载失败提示可理解。

迁移与回退：

- 先部署新只读导出端点再更新 UI；保留旧端点供旧组织导出客户端。新端点不可用时 my 应明确失败，不能回退到旧 /export。

产品口径：无需新增产品决定，保留已确认规则。

[原问题证据](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/修复工作副本_2026-09-08/output/full-project-audit-20260913/root/findings.md:110>)；[本项设计来源](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/修复工作副本_2026-09-08/output/grok-repair-plan-20260913/workflow-design.json:379>)。

#### ROOT-08 · P2 · 定时任务表达式重新打开后被错误回填，未主动修改也会改变执行时间

原触发条件：有权编辑定时任务的人打开生成表达式：现有周规则6#2，或年份范围2030-2035、步进2030/2。changeRadio把周次/星期反填，年份分支仅改radio而未恢复数值，Vue watcher随即发出改变后的表达式；用户点击确定会回填任务表单。

已接受的条件与边界：保留原报告的触发条件、当前/较新副本对照和证据边界；未新增生产事故或实际环境验收结论。

原证据层级：`real-source-offline-probe-plus-stated-static-boundary`。当前与较新真实Vue2 computed/watch+生产组件方法共6场景：6#2→2#4；2030-2035→2026-2027；2030/2→2026/1。监控任务页handleShowCron→crontabFill→保存实际接线已独立复核。无DOM、HTTP或调度器修改；未证明线上任务被改。

具体改法：

1. WF06 统一 Cron 解析/序列化，读取时构造独立字段模型，初始化结束再允许用户编辑事件输出；computed 不反向修改其他字段。未发生用户编辑时保留原合法表达式，点确定不能生成不同时间规则。
2. 修正星期 weekday#occurrence 的方向，6#2 回填 weekday=6、occurrence=2。年份 range/step 必须恢复起止或起点/步长；现有合法但 UI 无法无损表示的规则保留原字符串并说明使用文本编辑，禁止静默 clamp。
3. 恢复后从组件 fill 接到 job.form.cronExpression，只有明确确定才写回；取消或关闭不动原表单。第五个星期等边界按 Quartz 实际规则支持，不用现有 1..4 clamp 强行改写。

已存在相关文件：

- [erp-ui/src/components/Crontab/index.vue](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/Grok实施工作副本_20260912/erp-ui/src/components/Crontab/index.vue>)
- [erp-ui/src/components/Crontab/week.vue](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/Grok实施工作副本_20260912/erp-ui/src/components/Crontab/week.vue>)
- [erp-ui/src/components/Crontab/year.vue](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/Grok实施工作副本_20260912/erp-ui/src/components/Crontab/year.vue>)
- [erp-ui/src/components/Crontab/result.vue](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/Grok实施工作副本_20260912/erp-ui/src/components/Crontab/result.vue>)
- [erp-ui/src/views/monitor/job/index.vue](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/Grok实施工作副本_20260912/erp-ui/src/views/monitor/job/index.vue>)
- [erp-modules/erp-job/src/main/java/com/erp/job/controller/SysJobController.java](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/Grok实施工作副本_20260912/erp-modules/erp-job/src/main/java/com/erp/job/controller/SysJobController.java>)
- [erp-modules/erp-job/src/main/java/com/erp/job/util/CronUtils.java](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/Grok实施工作副本_20260912/erp-modules/erp-job/src/main/java/com/erp/job/util/CronUtils.java>)

拟新增：`erp-ui/src/utils/cronExpression.js`。

依赖/并行边界：WF06

验收与失败恢复：

- 6#2、2030-2035、2030/2 打开再确定，完整表达式不变；取消/关窗同样不变。
- 星期 1/7、第五次出现、年份边界、UI 无法表达的合法历史规则保持无损或明确拒绝编辑，不自动变值。
- 只修改一个字段，其他五/六字段保持；watcher 初始化无中间 fill。
- 真实 Vue mount+job fill 保存链及服务端 Quartz 下一次执行时间对照，不能只测纯函数拼接。

迁移与回退：

- 无 DB 迁移，不批量改写存量任务 Cron；可回退组件但必须禁止不支持规则的可视化编辑，保留文本和后台校验。

产品口径：无需新增产品决定，保留已确认规则。

[原问题证据](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/修复工作副本_2026-09-08/output/full-project-audit-20260913/root/findings.md:128>)；[本项设计来源](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/修复工作副本_2026-09-08/output/grok-repair-plan-20260913/workflow-design.json:416>)。

#### ROOT-09 · P2 · 定时任务输入零步长后打开表达式预览，会陷入无界循环

原触发条件：在任务表单手填无效表达式0/0 * * * * ?，点生成表达式。打开动作未先校验，预览getAverageArr用step=0进入while(min<=limit)，min永远不变；该计算在浏览器主线程同步运行。

已接受的条件与边界：保留原报告的触发条件、当前/较新副本对照和证据边界；未新增生产事故或实际环境验收结论。

原证据层级：`real-source-offline-probe-plus-stated-static-boundary`。两副本真实生产方法在独立VM的50ms限时执行均被ERR_SCRIPT_EXECUTION_TIMEOUT强制终止，静态循环条件证明不会自行结束。调用方仅在最终保存验证必填，进入生成器无语法预检；未在用户浏览器制造卡死，未改真实定时任务。

具体改法：

1. 进入 Cron 生成器/预览之前先校验字段数、数字格式、有限值和正步长，0、负数、非数字不能进入结果枚举。拒绝时保持原表单文本并指出具体字段，便于用户改正。
2. 预览枚举器自身再次校验输入，用有界 for/最大迭代次数保证所有入口均终止；不要仅依赖按钮 disabled 或后端保存时校验。外层预览总候选次数/年份范围也设上限，失败显示不可预览。
3. 与 ROOT-08 共用解析结果和错误契约；本批不假称已有后端预览端点。服务端已有 CronUtils 的创建/编辑验证继续作为权威保存校验，前端只做即时安全预览。

已存在相关文件：

- [erp-ui/src/components/Crontab/index.vue](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/Grok实施工作副本_20260912/erp-ui/src/components/Crontab/index.vue>)
- [erp-ui/src/components/Crontab/result.vue](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/Grok实施工作副本_20260912/erp-ui/src/components/Crontab/result.vue>)
- [erp-ui/src/views/monitor/job/index.vue](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/Grok实施工作副本_20260912/erp-ui/src/views/monitor/job/index.vue>)
- [erp-modules/erp-job/src/main/java/com/erp/job/controller/SysJobController.java](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/Grok实施工作副本_20260912/erp-modules/erp-job/src/main/java/com/erp/job/controller/SysJobController.java>)
- [erp-modules/erp-job/src/main/java/com/erp/job/util/CronUtils.java](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/Grok实施工作副本_20260912/erp-modules/erp-job/src/main/java/com/erp/job/util/CronUtils.java>)

拟新增：`erp-ui/src/utils/cronExpression.js`。

依赖/并行边界：WF06；ROOT-08

验收与失败恢复：

- 0/0 * * * * ? 打开预览立即给错误且浏览器仍可点击/关闭；测试进程设置超时防止回归测试本身挂死。
- 直接调用 getAverageArr，step 为 0、负数、NaN、Infinity、字符串及巨大范围均有界结束。
- 常用合法表达式预览与 Quartz 在固定时区/固定基准时刻一致；不存在下一次日期明确显示，不长时间循环。
- 无效表达式后改成有效值可再次预览；取消不修改 job 原表达式。

迁移与回退：

- 无迁移；关闭可视化预览作为临时回退仍保留手填和后端校验，不能恢复无界枚举。

产品口径：无需新增产品决定，保留已确认规则。

[原问题证据](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/修复工作副本_2026-09-08/output/full-project-audit-20260913/root/findings.md:148>)；[本项设计来源](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/修复工作副本_2026-09-08/output/grok-repair-plan-20260913/workflow-design.json:453>)。

#### P04 · P2 · 批量删除定时任务失败后，数据库回滚但部分调度已消失

原触发条件：批量定时任务先删存在项的调度，再遇不存在项异常；数据库回滚但调度不恢复。

已接受的条件与边界：数据库事务与实际 Quartz JobStore 未协同回滚时成立。被注释 ScheduleConfig 不能证明运行配置；当前证据是实际 Java 方法、内存调度器、模拟数据库回滚，未做 Spring/Quartz 集成。

原证据层级：`offline-java-method-with-simulated-rollback`。真实Java删除方法与内存调度器；数据库回滚由探针模拟，非Spring事务测试。线上Quartz/Nacos未读。

具体改法：

1. 先只读核实真实运行JobStore、数据源/事务管理器、集群节点与启动init scheduler.clear行为。通过实际集成和故障注入证明能与业务删除同一事务原子提交时，采用现有本地事务路径；未证明时不得凭注释配置作此假设。
2. 两种路径均先整批去重、按固定顺序锁定/校验记录与版本；缺失/冲突在任何副作用前返回可定位结果，保留选中范围。该预检可先实施，但单靠消除NPE不关闭P04。
3. 仅在无法保证同一事务原子提交时，新增持久删除意图/可重放同步：DB提交后由同步器删除Quartz条目，失败和重启可重试且UI显示待同步/失败/已完成，不能仅afterCommit尝试一次。意图绑定jobId/group/revision，过时消息不得删后来重建/恢复的任务。
4. 执行入口核对当前DB有效状态，拒绝已删除/待删除的任务开始执行；已开始业务不承诺强行撤销。两路径都需验证启动、暂停、恢复和多节点兼容，不能误用scheduler.clear清除他人任务。

已存在相关文件：

- [erp-modules/erp-job/src/main/java/com/erp/job/service/SysJobServiceImpl.java](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/Grok实施工作副本_20260912/erp-modules/erp-job/src/main/java/com/erp/job/service/SysJobServiceImpl.java>)
- [erp-modules/erp-job/src/main/java/com/erp/job/mapper/SysJobMapper.java](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/Grok实施工作副本_20260912/erp-modules/erp-job/src/main/java/com/erp/job/mapper/SysJobMapper.java>)
- [erp-modules/erp-job/src/main/resources/mapper/job/SysJobMapper.xml](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/Grok实施工作副本_20260912/erp-modules/erp-job/src/main/resources/mapper/job/SysJobMapper.xml>)
- [erp-modules/erp-job/src/main/java/com/erp/job/util/AbstractQuartzJob.java](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/Grok实施工作副本_20260912/erp-modules/erp-job/src/main/java/com/erp/job/util/AbstractQuartzJob.java>)
- [erp-modules/erp-job/src/main/java/com/erp/job/config/ScheduleConfig.java](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/Grok实施工作副本_20260912/erp-modules/erp-job/src/main/java/com/erp/job/config/ScheduleConfig.java>)

依赖/并行边界：B00；真实JobStore识别

验收与失败恢复：

- 先记录实际JobStore/事务边界。若选同事务路径，真实Spring+MySQL+Quartz下后项不存在、并发删除/修改及提交失败均原子回滚；不可仅Mock证明。
- 若选可靠意图路径，注入提交后同步失败/重启、重复消息、旧意图晚到：当前DB状态与UI准确且最终按原代次同步，不出现永久失调度或错误复活。
- 两路径验证删除前已运行任务的既有语义、暂停/恢复/启动初始化及多节点兼容；浏览器显示处理中/完成/失败与逐项原因。

迁移与回退：

- 先选定真实可证的事务路径。只有可靠意图分支需要新增兼容同步表/状态/版本字段，纳入SQL镜像与顺序清单，不默认先建outbox。
- 回退前核对并完成/接管待同步任务；保留兼容消费者或暂停变更入口。保留审计与回执，不以删除未处理记录代替恢复，也不能只退jar丢失处理中状态。

产品口径：无需新增产品决定，保留已确认规则。

[原问题证据](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/修复工作副本_2026-09-08/output/full-project-audit-20260913/platform/findings.md:31>)；[本项设计来源](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/修复工作副本_2026-09-08/output/grok-repair-plan-20260913/platform-design.json:98>)。

#### P07 · P2 · 审批“立即检查”页面发送的参数不符合后台契约，筛选也无效

原触发条件：审批配置检查面板只传业务/范围，DTO必需版本；筛选字段与后台查询字段也不同。

已接受的条件与边界：保留原报告的触发条件、当前/较新副本对照和证据边界；未新增生产事故或实际环境验收结论。

原证据层级：`offline-vue-payload-plus-static-contract`。双副本真实submitRun载荷探针加API/DTO/SQL静态契约；未启动Bean Validation或HTTP。

具体改法：

1. 选择复用现有按具体规则版本校验契约，不新建业务/全组织扫描语义。版本详情提供一键检查带入versionId；独立面板选择模板和可校验版本，单一确定版本可带出并展示。多版本时必须让用户知道正在检查哪一版。
2. 查询筛选统一templateId/runStatus，提交versionId/validationType；从现有definition API读取规则/版本，不要求用户手抄组织/版本号。不把后台不支持的scopeMode界面继续伪装成有效筛选。
3. 普通失败保留选择、显示一次原因；旧加载/提交返回不覆盖新选择，未找到有效版本显示可操作提示。发布内部校验保持独立权限及原业务规则。

已存在相关文件：

- [erp-ui/src/views/approval/manage/components/ValidationPanel.vue](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/Grok实施工作副本_20260912/erp-ui/src/views/approval/manage/components/ValidationPanel.vue>)
- [erp-ui/src/api/approval/definition.js](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/Grok实施工作副本_20260912/erp-ui/src/api/approval/definition.js>)
- [erp-ui/src/api/approval/validation.js](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/Grok实施工作副本_20260912/erp-ui/src/api/approval/validation.js>)
- [erp-modules/erp-approval/src/main/java/com/erp/approval/controller/ApprovalValidationController.java](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/Grok实施工作副本_20260912/erp-modules/erp-approval/src/main/java/com/erp/approval/controller/ApprovalValidationController.java>)
- [erp-modules/erp-approval/src/main/java/com/erp/approval/domain/dto/ApprovalValidationRequest.java](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/Grok实施工作副本_20260912/erp-modules/erp-approval/src/main/java/com/erp/approval/domain/dto/ApprovalValidationRequest.java>)
- [erp-modules/erp-approval/src/main/resources/mapper/approval/ApprovalValidationMapper.xml](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/Grok实施工作副本_20260912/erp-modules/erp-approval/src/main/resources/mapper/approval/ApprovalValidationMapper.xml>)

依赖/并行边界：B00；公共异步和错误呈现约定

验收与失败恢复：

- 真实HTTP Bean Validation通过且选定version确实校验；templateId/runStatus筛选、空版本、无权限、body错误、切版本迟到回复。
- 浏览器从版本详情一步检查；已有发布校验及列表页旧链接兼容，无自动发布动作。

迁移与回退：

- 预期无需业务表迁移；接口alias ruleVersionId继续兼容。前端回退也需保持有效payload，不回到缺versionId旧提交。

产品口径：无需新增产品决定，保留已确认规则。

[原问题证据](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/修复工作副本_2026-09-08/output/full-project-audit-20260913/platform/findings.md:55>)；[本项设计来源](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/修复工作副本_2026-09-08/output/grok-repair-plan-20260913/platform-design.json:180>)。

#### S03 · P2 · 公告阅读统计随姓名筛选被改成错误的总体阅读率

原触发条件：公告阅读人数按姓名筛选后，筛选total被当作全体已读人数计算总体阅读率。

已接受的条件与边界：保留原报告的触发条件、当前/较新副本对照和证据边界；未新增生产事故或实际环境验收结论。

原证据层级：`offline-computed-getter-plus-static-sql`。真实readRate getter与API/SQL静态闭环；人数为合成样本，不是线上数量。

具体改法：

1. 现有 /system/notice/readUsers/list 保留 rows/total 作为当前姓名筛选分页结果，增量返回 summary.readCount 与 summary.recipientCount；后台使用同一公告/权限/有效接收人规则但不应用姓名条件计算总体统计。现有回包尚无独立总体 readCount，需新增 SQL/响应字段。
2. ReadUsers 显示“筛选结果 N 人”与“总体已读 R/T、阅读率”分开。总体分母沿用公告现有接收人规则，不能把筛选后的 total 当总体已读人数。总接收人数为 0 时显示无接收人，不算 NaN/100%。
3. 公告 ID 与查询条件使用 WF01；切公告/关窗后旧统计、列表和 finally 失效。统计和列表尽量同次请求返回，避免来自两个版本却没有说明。

已存在相关文件：

- [erp-ui/src/views/system/notice/ReadUsers.vue](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/Grok实施工作副本_20260912/erp-ui/src/views/system/notice/ReadUsers.vue>)
- [erp-ui/src/api/system/notice.js](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/Grok实施工作副本_20260912/erp-ui/src/api/system/notice.js>)
- [erp-modules/erp-system/src/main/java/com/erp/system/controller/SysNoticeController.java](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/Grok实施工作副本_20260912/erp-modules/erp-system/src/main/java/com/erp/system/controller/SysNoticeController.java>)
- [erp-modules/erp-system/src/main/java/com/erp/system/service/impl/SysNoticeReadServiceImpl.java](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/Grok实施工作副本_20260912/erp-modules/erp-system/src/main/java/com/erp/system/service/impl/SysNoticeReadServiceImpl.java>)
- [erp-modules/erp-system/src/main/java/com/erp/system/mapper/SysNoticeReadMapper.java](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/Grok实施工作副本_20260912/erp-modules/erp-system/src/main/java/com/erp/system/mapper/SysNoticeReadMapper.java>)
- [erp-modules/erp-system/src/main/resources/mapper/system/SysNoticeReadMapper.xml](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/Grok实施工作副本_20260912/erp-modules/erp-system/src/main/resources/mapper/system/SysNoticeReadMapper.xml>)

依赖/并行边界：WF01；WF05

验收与失败恢复：

- 公告接收 100、已读 40，姓名筛选仅 3：列表 total=3，总体仍 40/100、40%；分页不改变总体。
- 重名、无匹配、0 接收人、接收人失效按既有口径处理；无统计权限不能通过增量字段越权。
- 公告 A/B 与不同姓名乱序，统计和列表均对应当前公告/筛选。
- 旧客户端仍只使用 rows/total；新增字段不会破坏 TableDataInfo JSON 结构。

迁移与回退：

- 只增量扩响应和 SQL，无数据迁移；新 UI 在旧服务缺 summary 时显示“总体统计暂不可用”，不能退回 filtered total 算总体。

产品口径：无需新增产品决定，保留已确认规则。

[原问题证据](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/修复工作副本_2026-09-08/output/full-project-audit-20260913/platform/supplemental-system-front.md:21>)；[本项设计来源](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/修复工作副本_2026-09-08/output/grok-repair-plan-20260913/workflow-design.json:552>)。

## 新增契约、迁移与真实环境门禁

拟新增契约必须在代码评审中列明API/表/字段/索引/迁移、旧客户端兼容、权限、幂等和失败分类；“已有内部方法”不等于已有可调用前端契约。补卡后端by-client-request已存在，前端封装待加；云盘持久operationId、方案发布预览恢复和Quartz条件性outbox属于需要新增的设计，不能在文档中写成已部署。

新增持久状态采用兼容扩展迁移，纳入现有SQL镜像、排序和静态清单。回退不能只换回旧包：操作回执、未完成同步、合法签署文件及历史审批数据需要保留；先停受影响入口或保留兼容消费者，禁止回退到已知鉴权缺陷。

| 证据阶段 | 完成条件 | 不可替代的边界 |
|---|---|---|
| 源码与日志 | 对明确输入/状态读到服务/SQL/回调，链接日志到同一候选版本 | 搜索命中和旧日志不算新补丁验证 |
| 本地测试 | 对改动跑行为/负向回归，记录通过/失败/错误/跳过与版本 | 原采购112次是缺陷复现，不是新修复通过 |
| 真实集成 | 隔离MySQL双事务、Redis真实交错、Quartz失败、文件生成中断与回滚按相关项执行 | 内存mapper/调度器/HTTP桩不算真实事务验证 |
| 浏览器 | PC/H5多角色业务闭环、切单/离开/重开、错误/未知结果、下载恢复 | 当前只有部分线上只读观察，没有业务提交验收 |
| 设备 | Android/iOS相机、位置、网络、锁屏消息和相关权限/凭据 | 前端/原生构建通过不代表设备体验或推送送达 |
| 部署 | 明确目标、兼容迁移、备份恢复演练、回退与版本指纹 | 有备份文件不等于恢复验证，局部发布不等于全部包已部署 |
| 生产验收 | 目标版本上的授权场景与指标观察单列确认 | 集成通过仍可生产未验；不能反写成集成未通过 |

B00需处理既有较新Maven的2失败/4错误及3个容器测试入口问题，保留权限保护和真实契约，不删断言或把skip当通过。原前端308/354测试文件与后端既有结果可作基线参考，新候选必须生成新鲜结果。[既有检查与准确测试计数](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/修复工作副本_2026-09-08/docs/reports/Codex与Grok联合复核最终报告-20260913.md:133>)。

已检查脚本入口，下面仅列计划命令，本轮没有执行：

- `bash scripts/ci/verify.sh --checks-only`：仅仓库/SQL清单/集成入口静态门禁。[脚本](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/Grok实施工作副本_20260912/scripts/ci/verify.sh>)
- `bash scripts/ci/verify.sh`：现有全量Maven test、npm test、前端prod pre/build/post；必须Node22/JDK17候选环境。[脚本](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/Grok实施工作副本_20260912/scripts/ci/verify.sh>)
- `bash scripts/ci/verify.sh --with-mysql`：额外显式Testcontainers MySQL5.7/8.0，Docker可用且新增案例接入后才代表对应并发验证。[脚本](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/Grok实施工作副本_20260912/scripts/ci/verify.sh>)
- `bash scripts/run-native-mysql-integration-tests.sh`：必须已配ERP_IT_MYSQL_HOST/USER等非生产参数；现仅system专用套件，新inventory/file/job用例需补对应入口。[脚本](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/Grok实施工作副本_20260912/scripts/run-native-mysql-integration-tests.sh>)
- `bash scripts/run-native-redis-integration-tests.sh`：必须已配隔离非生产Redis；现system会话治理套件，新P01/P05场景需新增接入；不FLUSHDB。[脚本](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/Grok实施工作副本_20260912/scripts/run-native-redis-integration-tests.sh>)

现有native-MySQL/native-Redis脚本主要覆盖特定模块套件；要新增本次库存/文件/任务/会话用例并接入对应入口，不能调用脚本就声称自动覆盖57项。仅使用明确隔离的非生产参数；不得借验收清空共享Redis或写业务数据库。

## 剩余收敛点与交付方式

- **INV-N01**：受控补仓设计纳入本方案：仅submitted、无有效通知/出库、版本不变、明确选择合法仓库时原子补全并生成通知，保留审计。实施阶段先只读盘点缺仓历史单，形成单据与仓库映射清单后，针对真实执行范围单独确认。 仅具体历史单据清单、仓库映射和真实执行范围，不阻塞受控入口实现及新单仓库修复；本次仅规划；尚未执行真实数据补仓，未获得针对具体历史清单的执行批准；不要求为可逆入口实现重复确认。

每包提交时交付：问题ID与精确文件清单、变更前后指纹、行为变化、失败/并发验收结果、兼容迁移、回退范围和未完成门禁。共享文件由集成负责人串行合并，模块独立回归后再运行整候选全量检查。真实环境缺口只阻塞相关验收和发布，不阻塞无关本地修复。

本轮不改原联合审查报告、不修改业务源码或真实数据。[8包归属源清单](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/修复工作副本_2026-09-08/output/grok-repair-plan-20260913/batch-design.json>)、[57项机器方案](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/修复工作副本_2026-09-08/output/grok-repair-plan-20260913/final-plan.json>)、[方案生成器](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/修复工作副本_2026-09-08/output/grok-repair-plan-20260913/build-final-plan.py>)。

方案期按同一生产文件清单前后复核：当前2549个，0改动、0缺失；较新2618个，0改动、0缺失。此结论只覆盖已登记生产文件，不表示整个目录或既有git脏差异为零。[方案期源码完整性](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/修复工作副本_2026-09-08/output/grok-repair-plan-20260913/source-integrity-after.json>)。

原联合审查报告和57项审查机器清单的SHA-256与冻结值一致；本方案另建材料，未改写原审查结论。[原审查材料完整性](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/修复工作副本_2026-09-08/output/grok-repair-plan-20260913/original-audit-integrity.json>)。
