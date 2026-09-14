# Codex与Grok联合复核最终报告

日期：2026-09-13。**两轮联合审阅已结束，没有未解决的关键评价分歧；这不是修复完成或上线验收报告。** 本次保留原56项，新增GROK-01/P2，共57个待处理问题项：55项业务/操作问题、1项部署条件风险（P06）、1项验证门禁问题（ROOT-05）；优先级为11项P1、45项P2、1项P3。P1表示修复优先级，不表示已经发生线上事故。

57按问题项及业务入口统计，不代表57个独立技术根因，也不代表已穷尽所有问题。采购审批静默与报销A11共享技术根因，可合并实施，需分别验收。旧48项台账、D01—D23业务决定、旧16项中较新已有对应处理的11项，以及旧员工分页HR-03，均不直接叠加。[第二轮计数与结论](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/修复工作副本_2026-09-08/output/grok-peer-review-20260913/round2-public-response.md:35>)；[57项机器清单](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/修复工作副本_2026-09-08/output/grok-peer-review-20260913/final-dispositions.json>)。

## 讨论方法与证据边界

Codex先按生命周期检查当前和较新工作树、编制覆盖矩阵、运行本地测试及有界方法探针，并进行部分已登录Safari只读页面检查；随后把51项本轮问题与A06/A11/A12/A13/A14这5项旧保留问题交给真正的本地Grok Build只读会话。第一轮Grok对56行给出42项“支持”、14项“需限定条件”，另提出采购审批静默和旧分页对照。Codex逐条回应争点、独立复现新增问题；第二轮Grok接受收紧，原56无整项撤回或改级。[第一轮原文](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/修复工作副本_2026-09-08/output/grok-peer-review-20260913/round1-public-response.md>)、[第一轮56行提取](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/修复工作副本_2026-09-08/output/grok-peer-review-20260913/round1-dispositions.json>)、[Codex回应证据](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/修复工作副本_2026-09-08/output/grok-peer-review-20260913/codex-response-evidence.md>)、[第二轮原文](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/修复工作副本_2026-09-08/output/grok-peer-review-20260913/round2-public-response.md>)。

Grok第一轮工具记录直接打开过当前98个、较新5个生产文件，共同相对路径4个；这是已完成read_file的去重文件数，部分行读取也只计一次。材料阅读、grep和同哈希对照另算，不能写成Grok逐项独立读完两副本的全部文件。第二轮针对争点补读，不能倒算成第一轮全面复审。Grok没有复跑既有探针、新增112次采购探针或全量测试，没有访问业务数据库或生产。[直接阅读统计](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/修复工作副本_2026-09-08/output/grok-peer-review-20260913/grok-direct-reading-summary.json>)、[Grok更正阅读范围](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/修复工作副本_2026-09-08/output/grok-peer-review-20260913/round2-public-response.md:43>)。

两个工作树HEAD相同但有不同既有未提交内容，以文件内容和指纹区分；“较新”指历史命名为Grok实施工作副本的目录，不表示本次由Grok修改。此次仅生成审查材料与测试/探针输出，没有业务源码修改、数据库迁移、部署或生产交易。本次讨论前后对当前2549个、较新2618个生产文件进行指纹核对，两者均0改动、0缺失；该结果限受保护生产文件，不表示全目录无输出变化或两工作树的历史dirty差异为零。[讨论后源文件完整性](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/修复工作副本_2026-09-08/output/grok-peer-review-20260913/source-integrity-after.json>)。原覆盖矩阵区分登记、定向阅读、链路核对、方法探针和实际页面观察；262个Vue有阅读凭据不表示每个分支、样式或设备均已验证。[原51项完整报告](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/修复工作副本_2026-09-08/docs/reports/全项目流程与操作便利性全面复查-20260913.md:11>)、[逐入口覆盖矩阵](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/修复工作副本_2026-09-08/output/full-project-audit-20260913/coverage-consolidated.json>)、[覆盖说明](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/修复工作副本_2026-09-08/output/full-project-audit-20260913/覆盖说明.md>)。

## 争点的最终处理

| 争点 | 联合结论及必须保留的范围 |
|---|---|
| ROOT-01采购对象混淆 | 正式待办默认携带三种业务/审批ID；同路径复用和迟到回包链路可达。需A可读、B本就是当前用户可处理的PENDING任务且实例RUNNING。不表述为越权或生产错批；两副本resolver内存验证未执行Java decorate、DOM或真实审批。[第二轮A](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/修复工作副本_2026-09-08/output/grok-peer-review-20260913/round2-public-response.md:11>) |
| P01令牌撤销竞态 | 窗口限同一preHandle中读到会话之后、续期SET之前；P1保留。内存会话替身不能证明线上复活，仍需真实Redis GET–DEL–SET交错。[第二轮E](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/修复工作副本_2026-09-08/output/grok-peer-review-20260913/round2-public-response.md:55>) |
| P06监控配置 | 匿名放行配置保留为部署条件项。普通compose绑127.0.0.1；host网络不等于公网监听或公网可达。server.address、外层认证、安全组、Nacos待查。[第二轮A](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/修复工作副本_2026-09-08/output/grok-peer-review-20260913/round2-public-response.md:13>) |
| P04任务删除与回滚 | 真实Java方法加内存调度器/模拟DB回滚支持风险；注释配置不证明运行JobStore。实际Spring/Quartz事务协调仍待验证。[第二轮A](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/修复工作副本_2026-09-08/output/grok-peer-review-20260913/round2-public-response.md:14>) |
| HR-N04长ID | DDL为BIGINT AUTO_INCREMENT；超过 Number.MAX_SAFE_INTEGER（2^53−1），且 Number 转换发生舍入时触发；并非所有更大 ID 都会变值。生产最大编号未查。[第二轮A](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/修复工作副本_2026-09-08/output/grok-peer-review-20260913/round2-public-response.md:15>) |
| HR-N08重新发布 | 源方案启用、参与指纹的所有内容相同，命中旧停用V1时假成功；V1仍停用不会参与匹配。V2仅仍启用且满足匹配条件时才可能继续使用。[Codex限定](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/修复工作副本_2026-09-08/output/grok-peer-review-20260913/codex-response-evidence.md>)、[第二轮F](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/修复工作副本_2026-09-08/output/grok-peer-review-20260913/round2-public-response.md:75>) |
| HR-N09文件清理、HR-N10旧合同 | 前者是多文件后续生成失败、循环后才登记清理的静态链路，未真实故障注入，整包硬删除另有清理；后者须旧pending_sign数据与保留API并发，PC已隐藏作废按钮，旧数据数量未知。[第二轮F](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/修复工作副本_2026-09-08/output/grok-peer-review-20260913/round2-public-response.md:75>) |
| 其他限制 | INV-N04/N05尚无真实MySQL交错；ROOT-07仍在授权组织范围；P02需混合角色；PLAT-UI02仅Cookie构建和撤销前登出失败，两副本默认bearer。GROK-01、A11不得把401/凭据限制/成功CSRF恢复/主动取消都称为失败静默。[第二轮B/F](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/修复工作副本_2026-09-08/output/grok-peer-review-20260913/round2-public-response.md:20>) |
| Excel与业务规则 | 入职Excel入口继续启用，不是唯一入口；历史补签不覆盖现行工资；普通调岗默认不改工资、不发薪酬结构确认书，实际调薪走工资变更；未检不入可用库存，待办快捷操作不自动审批。[更正及确认](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/修复工作副本_2026-09-08/output/grok-peer-review-20260913/round2-public-response.md:16>)、[第二轮操作规则](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/修复工作副本_2026-09-08/output/grok-peer-review-20260913/round2-public-response.md:82>) |

## 全部57项及原证据

表中“源码”指静态链路，“内存方法/SQL”使用真实抽取方法或SQL并配显式替身，具体限制保留在原证据；“本地日志”不等于线上验收。当前/较新状态、触发条件和第一轮原文逐项保存在机器清单。所有项本轮均未实施修复；有条件项按上节及原报告理解。

| 编号 | 级别 | 问题 | 现有证据 | Grok审阅意见 |
|---|---|---|---|---|
| ROOT-01 | P1 | 手机采购申请可显示 A 单却审批 B 单，PC 编辑也有迟到覆盖 | [内存真实方法/源码](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/修复工作副本_2026-09-08/output/full-project-audit-20260913/root/findings.md:5>) | [需限定条件；二轮保留](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/修复工作副本_2026-09-08/output/grok-peer-review-20260913/round1-public-response.md:13>) |
| ROOT-02 | P2 | 固定资产整店保存部分成功后无法原地重试 | [内存真实方法/源码](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/修复工作副本_2026-09-08/output/full-project-audit-20260913/root/findings.md:23>) | [支持；二轮保留](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/修复工作副本_2026-09-08/output/grok-peer-review-20260913/round1-public-response.md:14>) |
| ROOT-03 | P2 | 字典类型批量删除会部分删除后整体报错 | [内存真实方法/源码](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/修复工作副本_2026-09-08/output/full-project-audit-20260913/root/findings.md:40>) | [支持；二轮保留](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/修复工作副本_2026-09-08/output/grok-peer-review-20260913/round1-public-response.md:15>) |
| ROOT-04 | P2 | 字典类型改名后旧键仍从缓存返回旧数据 | [内存真实方法/源码](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/修复工作副本_2026-09-08/output/full-project-audit-20260913/root/findings.md:57>) | [支持；二轮保留](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/修复工作副本_2026-09-08/output/grok-peer-review-20260913/round1-public-response.md:16>) |
| ROOT-05 | P2 | 较新修复副本尚未满足全量验证门禁 | [本地测试/门禁日志](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/修复工作副本_2026-09-08/output/full-project-audit-20260913/root/findings.md:74>) | [支持；二轮保留](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/修复工作副本_2026-09-08/output/grok-peer-review-20260913/round1-public-response.md:17>) |
| ROOT-06 | P2 | 请假、补卡及历史工资查询会被旧回包改成不符合当前筛选的结果 | [内存真实方法/源码](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/修复工作副本_2026-09-08/output/full-project-audit-20260913/root/findings.md:91>) | [支持；二轮保留](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/修复工作副本_2026-09-08/output/grok-peer-review-20260913/round1-public-response.md:18>) |
| ROOT-07 | P2 | 工资页选择“我的”后导出仍会导出当前组织范围的其他员工记录 | [内存真实方法/源码](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/修复工作副本_2026-09-08/output/full-project-audit-20260913/root/findings.md:110>) | [需限定条件；二轮保留](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/修复工作副本_2026-09-08/output/grok-peer-review-20260913/round1-public-response.md:19>) |
| ROOT-08 | P2 | 定时任务表达式重新打开后被错误回填，未主动修改也会改变执行时间 | [内存真实方法/源码](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/修复工作副本_2026-09-08/output/full-project-audit-20260913/root/findings.md:128>) | [支持；二轮保留](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/修复工作副本_2026-09-08/output/grok-peer-review-20260913/round1-public-response.md:20>) |
| ROOT-09 | P2 | 定时任务输入零步长后打开表达式预览，会陷入无界循环 | [内存真实方法/源码](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/修复工作副本_2026-09-08/output/full-project-audit-20260913/root/findings.md:148>) | [支持；二轮保留](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/修复工作副本_2026-09-08/output/grok-peer-review-20260913/round1-public-response.md:21>) |
| INV-N01 | P1 | PC 新销售单无法生成发货通知 | [内存真实方法/源码](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/修复工作副本_2026-09-08/output/full-project-audit-20260913/inventory/findings.md:9>) | [支持；二轮保留](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/修复工作副本_2026-09-08/output/grok-peer-review-20260913/round1-public-response.md:22>) |
| INV-N02 | P2 | 多仓库发货通知默认提交全部行而必定被拒绝 | [内存真实方法/源码](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/修复工作副本_2026-09-08/output/full-project-audit-20260913/inventory/findings.md:19>) | [支持；二轮保留](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/修复工作副本_2026-09-08/output/grok-peer-review-20260913/round1-public-response.md:23>) |
| INV-N03 | P1 | 删除有库存的 OE/礼盒主数据导致后续物料操作失去入口 | [内存SQL/源码](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/修复工作副本_2026-09-08/output/full-project-audit-20260913/inventory/findings.md:27>) | [支持；二轮保留](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/修复工作副本_2026-09-08/output/grok-peer-review-20260913/round1-public-response.md:24>) |
| INV-N04 | P1 | 销售单编辑/取消与提交/生成发货通知不共享行锁 | [源码并发链路](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/修复工作副本_2026-09-08/output/full-project-audit-20260913/inventory/findings.md:35>) | [需限定条件；二轮保留](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/修复工作副本_2026-09-08/output/grok-peer-review-20260913/round1-public-response.md:25>) |
| INV-N05 | P1 | 调拨删除与提交竞争可删除已经冻结库存的单据 | [源码并发链路](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/修复工作副本_2026-09-08/output/full-project-audit-20260913/inventory/findings.md:43>) | [需限定条件；二轮保留](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/修复工作副本_2026-09-08/output/grok-peer-review-20260913/round1-public-response.md:26>) |
| INV-N06 | P2 | OE/礼盒可选文字字段清空后提示成功但旧值保留 | [内存SQL/规范化模拟](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/修复工作副本_2026-09-08/output/full-project-audit-20260913/inventory/findings.md:51>) | [支持；二轮保留](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/修复工作副本_2026-09-08/output/grok-peer-review-20260913/round1-public-response.md:27>) |
| INV-N07 | P2 | 客户服务历史超过100条后，旧记录在所有现有详情入口不可访问 | [内存SQL/源码](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/修复工作副本_2026-09-08/output/full-project-audit-20260913/inventory/findings.md:59>) | [支持；二轮保留](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/修复工作副本_2026-09-08/output/grok-peer-review-20260913/round1-public-response.md:28>) |
| INV-N08 | P2 | 库存预警包含OE/礼盒，却丢失物料身份，无法判断是哪项 | [内存SQL/源码](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/修复工作副本_2026-09-08/output/full-project-audit-20260913/inventory/findings.md:67>) | [支持；二轮保留](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/修复工作副本_2026-09-08/output/grok-peer-review-20260913/round1-public-response.md:29>) |
| INV-N09 | P1 | 非质检短少选择“安排补发”后未补充冻结库存，原预留耗尽时正常补发受阻 | [内存SQL/守恒链路](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/修复工作副本_2026-09-08/output/full-project-audit-20260913/inventory/findings.md:75>) | [需限定条件；二轮保留](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/修复工作副本_2026-09-08/output/grok-peer-review-20260913/round1-public-response.md:30>) |
| INV-N10 | P2 | 库存选OE/礼盒时仍展示商品分类树，分类ID被当成另一类型解释 | [内存真实方法/源码](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/修复工作副本_2026-09-08/output/full-project-audit-20260913/inventory/findings.md:83>) | [支持；二轮保留](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/修复工作副本_2026-09-08/output/grok-peer-review-20260913/round1-public-response.md:31>) |
| MOB-N01 | P2 | 异步树控件冷加载误清仍授权的当前组织 | [局部Vue异步组件](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/修复工作副本_2026-09-08/output/full-project-audit-20260913/inventory/mobile-common-supplement.md:7>) | [需限定条件；二轮保留](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/修复工作副本_2026-09-08/output/grok-peer-review-20260913/round1-public-response.md:32>) |
| MOB-N02 | P2 | 实际手机工作台三类统计长期显示初始化的零 | [内存真实方法/源码](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/修复工作副本_2026-09-08/output/full-project-audit-20260913/inventory/mobile-common-supplement.md:23>) | [支持；二轮保留](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/修复工作副本_2026-09-08/output/grok-peer-review-20260913/round1-public-response.md:33>) |
| MOB-N03 | P2 | 电脑消息中的签约包链接被客户端守卫送回首页 | [内存真实方法/源码](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/修复工作副本_2026-09-08/output/full-project-audit-20260913/inventory/mobile-common-supplement.md:37>) | [支持；二轮保留](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/修复工作副本_2026-09-08/output/grok-peer-review-20260913/round1-public-response.md:34>) |
| HR-N01 | P2 | 未来生效的健康证获批后立即替换当前证件，并显示“有效” | [源码链路](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/修复工作副本_2026-09-08/output/full-project-audit-20260913/hr/findings.md:10>) | [支持；二轮保留](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/修复工作副本_2026-09-08/output/grok-peer-review-20260913/round1-public-response.md:35>) |
| HR-N02 | P2 | 健康证“待审核”指标漏掉统一审批中的记录 | [源码链路](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/修复工作副本_2026-09-08/output/full-project-audit-20260913/hr/findings.md:33>) | [支持；二轮保留](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/修复工作副本_2026-09-08/output/grok-peer-review-20260913/round1-public-response.md:36>) |
| HR-N03 | P2 | 快速切换公司时旧印章响应覆盖新公司，编辑会保存到另一家公司 | [内存真实方法/源码](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/修复工作副本_2026-09-08/output/full-project-audit-20260913/hr/findings.md:56>) | [支持；二轮保留](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/修复工作副本_2026-09-08/output/grok-peer-review-20260913/round1-public-response.md:37>) |
| HR-N04 | P2 | 手机入职页面将超安全整数的记录编号舍入后读取和更新 | [内存真实方法/源码](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/修复工作副本_2026-09-08/output/full-project-audit-20260913/hr/findings.md:78>) | [需限定条件；二轮保留](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/修复工作副本_2026-09-08/output/grok-peer-review-20260913/round1-public-response.md:38>) |
| HR-N05 | P2 | 手机补卡在提交成功但响应丢失后，原页重试被“不可编辑”挡住 | [内存真实方法/源码](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/修复工作副本_2026-09-08/output/full-project-audit-20260913/hr/findings.md:100>) | [需限定条件；二轮保留](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/修复工作副本_2026-09-08/output/grok-peer-review-20260913/round1-public-response.md:39>) |
| HR-N07 | P2 | 官方入职 Excel 模板未整列设置文本格式，正常填多行会被自身校验拒绝 | [内存真实方法/源码](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/修复工作副本_2026-09-08/output/full-project-audit-20260913/hr/findings.md:123>) | [支持；二轮保留](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/修复工作副本_2026-09-08/output/grok-peer-review-20260913/round1-public-response.md:40>) |
| HR-N08 | P1 | 重新发布与历史版本相同的方案会假成功，历史版本仍停用 | [内存真实方法/源码](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/修复工作副本_2026-09-08/output/full-project-audit-20260913/hr/findings.md:144>) | [支持；二轮保留](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/修复工作副本_2026-09-08/output/grok-peer-review-20260913/round1-public-response.md:41>) |
| HR-N09 | P2 | 签约包第二份文件生成失败时，前面已落盘的签名PDF不进入回滚清理 | [源码链路](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/修复工作副本_2026-09-08/output/full-project-audit-20260913/hr/findings.md:167>) | [需限定条件；二轮保留](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/修复工作副本_2026-09-08/output/grok-peer-review-20260913/round1-public-response.md:42>) |
| HR-N10 | P2 | 保留的旧劳动合同签署与作废接口存在状态互相覆盖竞态 | [源码链路](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/修复工作副本_2026-09-08/output/full-project-audit-20260913/hr/findings.md:189>) | [需限定条件；二轮保留](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/修复工作副本_2026-09-08/output/grok-peer-review-20260913/round1-public-response.md:43>) |
| HR-N11 | P3 | 员工详情连续渲染三份当前健康证，重复内容挤占主要操作区域 | [模板源码](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/修复工作副本_2026-09-08/output/full-project-audit-20260913/hr/findings.md:213>) | [支持；二轮保留](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/修复工作副本_2026-09-08/output/grok-peer-review-20260913/round1-public-response.md:44>) |
| HR-N12 | P2 | PC入职状态操作的旧响应覆盖刚切换的另一人详情 | [内存真实方法/源码](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/修复工作副本_2026-09-08/output/full-project-audit-20260913/hr/findings.md:234>) | [支持；二轮保留](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/修复工作副本_2026-09-08/output/grok-peer-review-20260913/round1-public-response.md:45>) |
| HR-N13 | P2 | 手机资料完整度“加载更多”失败后跳过整页员工 | [内存真实方法/源码](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/修复工作副本_2026-09-08/output/full-project-audit-20260913/hr/findings.md:257>) | [支持；二轮保留](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/修复工作副本_2026-09-08/output/grok-peer-review-20260913/round1-public-response.md:46>) |
| HR-N14 | P2 | 档案保存中关闭并编辑下一人，上一人的成功回调会关掉新的编辑页 | [内存真实方法/源码](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/修复工作副本_2026-09-08/output/full-project-audit-20260913/hr/findings.md:279>) | [支持；二轮保留](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/修复工作副本_2026-09-08/output/grok-peer-review-20260913/round1-public-response.md:47>) |
| P01 | P1 | 注销或撤销后的令牌可能被在途请求重新写回 | [内存真实方法/源码](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/修复工作副本_2026-09-08/output/full-project-audit-20260913/platform/findings.md:7>) | [需限定条件；二轮保留](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/修复工作副本_2026-09-08/output/grok-peer-review-20260913/round1-public-response.md:48>) |
| P02 | P1 | 云盘把无关角色的全组织数据范围叠加为云盘管理范围 | [内存真实方法/源码](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/修复工作副本_2026-09-08/output/full-project-audit-20260913/platform/findings.md:15>) | [支持；二轮保留](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/修复工作副本_2026-09-08/output/grok-peer-review-20260913/round1-public-response.md:49>) |
| P03 | P2 | 云盘上传结果未知时，“重试”会生成第二份文件 | [内存真实方法/源码](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/修复工作副本_2026-09-08/output/full-project-audit-20260913/platform/findings.md:23>) | [支持；二轮保留](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/修复工作副本_2026-09-08/output/grok-peer-review-20260913/round1-public-response.md:50>) |
| P04 | P2 | 批量删除定时任务失败后，数据库回滚但部分调度已消失 | [内存真实方法/源码](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/修复工作副本_2026-09-08/output/full-project-audit-20260913/platform/findings.md:31>) | [需限定条件；二轮保留](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/修复工作副本_2026-09-08/output/grok-peer-review-20260913/round1-public-response.md:51>) |
| P05 | P2 | 通用幂等锁在超时请求结束时可能删除后来请求的锁 | [内存真实方法/源码](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/修复工作副本_2026-09-08/output/full-project-audit-20260913/platform/findings.md:39>) | [支持；二轮保留](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/修复工作副本_2026-09-08/output/grok-peer-review-20260913/round1-public-response.md:52>) |
| P06 | P1 | monitor 匿名放行整个实例管理与代理命名空间 | [配置/依赖源码](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/修复工作副本_2026-09-08/output/full-project-audit-20260913/platform/findings.md:47>) | [需限定条件；二轮保留](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/修复工作副本_2026-09-08/output/grok-peer-review-20260913/round1-public-response.md:53>) |
| P07 | P2 | 审批“立即检查”页面发送的参数不符合后台契约，筛选也无效 | [内存真实方法/源码](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/修复工作副本_2026-09-08/output/full-project-audit-20260913/platform/findings.md:55>) | [支持；二轮保留](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/修复工作副本_2026-09-08/output/grok-peer-review-20260913/round1-public-response.md:54>) |
| S01 | P2 | 公告旧保存响应覆盖后来打开的新草稿 | [内存真实方法/源码](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/修复工作副本_2026-09-08/output/full-project-audit-20260913/platform/supplemental-system-front.md:5>) | [支持；二轮保留](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/修复工作副本_2026-09-08/output/grok-peer-review-20260913/round1-public-response.md:55>) |
| S02 | P2 | 参数管理搜索乱序后，列表与当前筛选不一致 | [内存真实方法/源码](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/修复工作副本_2026-09-08/output/full-project-audit-20260913/platform/supplemental-system-front.md:13>) | [支持；二轮保留](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/修复工作副本_2026-09-08/output/grok-peer-review-20260913/round1-public-response.md:56>) |
| S03 | P2 | 公告阅读统计随姓名筛选被改成错误的总体阅读率 | [内存真实方法/源码](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/修复工作副本_2026-09-08/output/full-project-audit-20260913/platform/supplemental-system-front.md:21>) | [支持；二轮保留](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/修复工作副本_2026-09-08/output/grok-peer-review-20260913/round1-public-response.md:57>) |
| S04 | P2 | 批量盖章缺少请求会话隔离，已生成结果可以显示在后来改选的公司上 | [内存真实方法/源码](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/修复工作副本_2026-09-08/output/full-project-audit-20260913/platform/signing-front-cross-review.md:5>) | [支持；二轮保留](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/修复工作副本_2026-09-08/output/grok-peer-review-20260913/round1-public-response.md:58>) |
| INV-UI01 | P2 | OE 首次编辑已有记录后，供应商下拉被占位选项阻断加载 | [内存真实方法/源码](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/修复工作副本_2026-09-08/output/full-project-audit-20260913/inventory/final-ui-findings.md:3>) | [支持；二轮保留](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/修复工作副本_2026-09-08/output/grok-peer-review-20260913/round1-public-response.md:59>) |
| HR-UI01 | P2 | 手机实体搜索的旧响应覆盖新关键词候选，导致错选或重复搜索 | [内存真实方法/源码](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/修复工作副本_2026-09-08/output/full-project-audit-20260913/hr/final-ui-findings.md:5>) | [支持；二轮保留](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/修复工作副本_2026-09-08/output/grok-peer-review-20260913/round1-public-response.md:60>) |
| HR-UI02 | P2 | 资产报修提交期间可另开草稿，旧成功回调关闭新草稿并导致重填 | [内存真实方法/源码](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/修复工作副本_2026-09-08/output/full-project-audit-20260913/hr/final-ui-findings.md:28>) | [支持；二轮保留](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/修复工作副本_2026-09-08/output/grok-peer-review-20260913/round1-public-response.md:61>) |
| PLAT-UI01 | P2 | 用户详情切人时，旧敏感资料回包与新用户的部门、工号拼接 | [内存真实方法/源码](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/修复工作副本_2026-09-08/output/full-project-audit-20260913/platform/final-ui-findings.md:5>) | [支持；二轮保留](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/修复工作副本_2026-09-08/output/grok-peer-review-20260913/round1-public-response.md:62>) |
| PLAT-UI02 | P2 | Cookie会话模式下，锁屏页退出失败仍先解除锁定并回到首页 | [内存真实方法/源码](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/修复工作副本_2026-09-08/output/full-project-audit-20260913/platform/final-ui-findings.md:29>) | [需限定条件；二轮保留](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/修复工作副本_2026-09-08/output/grok-peer-review-20260913/round1-public-response.md:63>) |
| A06 | P1 | PC 采购退货切换原单后被旧响应换回旧单 | [内存真实方法/源码](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/修复工作副本_2026-09-08/docs/reports/项目逻辑与操作便利性复核-20260913.md:46>) | [支持；二轮保留](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/修复工作副本_2026-09-08/output/grok-peer-review-20260913/round1-public-response.md:64>) |
| A11 | P2 | 报销审批／撤回失败无提示 | [PC方法/其他静态](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/修复工作副本_2026-09-08/docs/reports/项目逻辑与操作便利性复核-20260913.md:48>) | [支持；二轮保留](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/修复工作副本_2026-09-08/output/grok-peer-review-20260913/round1-public-response.md:65>) |
| A12 | P2 | 导出下载失败后无法直接重下原批次 | [内存真实方法/源码](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/修复工作副本_2026-09-08/docs/reports/项目逻辑与操作便利性复核-20260913.md:50>) | [支持；二轮保留](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/修复工作副本_2026-09-08/output/grok-peer-review-20260913/round1-public-response.md:66>) |
| A13 | P2 | 手机报销提交结果未知，却提示肯定未提交 | [内存真实方法/源码](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/修复工作副本_2026-09-08/docs/reports/项目逻辑与操作便利性复核-20260913.md:52>) | [支持；二轮保留](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/修复工作副本_2026-09-08/output/grok-peer-review-20260913/round1-public-response.md:67>) |
| A14 | P2 | 连续打开不同报销待办，详情未随目标更新 | [路由/组件源码](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/修复工作副本_2026-09-08/docs/reports/项目逻辑与操作便利性复核-20260913.md:54>) | [支持；二轮保留](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/修复工作副本_2026-09-08/output/grok-peer-review-20260913/round1-public-response.md:68>) |
| GROK-01 | P2 | OA采购审批同意、退回和拒绝失败静默，用户无法判断结果或继续处理 | [84负向+28对照](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/修复工作副本_2026-09-08/output/grok-peer-review-20260913/approval-error-validation.md:3>) | [二轮支持新增P2](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/修复工作副本_2026-09-08/output/grok-peer-review-20260913/round2-public-response.md:22>) |

## 新增GROK-01：采购审批失败静默

PC与H5在可操作的PENDING任务、RUNNING实例上确认同意/退回/拒绝后，共用审批API默认silentError=true；普通失败进入统一处理后未提示，组件末尾空catch吞掉异常。按钮恢复可点，但没有失败原因、详情刷新或结果核对，手机加载错误卡片也不会覆盖审批写失败。后台仍校验活动节点、候选人和当期权限；此项不是绕过权限。

Codex在Node22.23.1抽取真实Vue方法/computed、审批API及完整响应拦截器，使用实际Axios与内存adapter、真实非调拨recovery分支及transport normalizer。两副本×两页面×三动作×七种失败共84次负向复现：HTTP409、HTTP403、HTTP200/body409、body403、body500、网络、超时。另有28次控制：每副本/页面approve的成功、主动取消、body401、HTTP401、凭据409、凭据428、CSRF403恢复成功。112次执行、0断言失败，表示缺陷复现及反例控制成立，不是修复后通过，也不并入原全量测试计数。

会话、消息UI、CSRF恢复结果和刷新均为内存桩；未挂载DOM、未执行前置request拦截器、真实HTTP、后端或数据库。手机pathname初稿写成组件目录形式，已改正式/mobile/oa-purchase-approval并在Node22重跑112次，结果一致。Grok阅读结果与源码后认可，未自行复跑。[完整验证与源行号](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/修复工作副本_2026-09-08/output/grok-peer-review-20260913/approval-error-validation.md>)、[112次结果](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/修复工作副本_2026-09-08/output/grok-peer-review-20260913/approval-error-probe-results.json>)、[第二轮认可与限制](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/修复工作副本_2026-09-08/output/grok-peer-review-20260913/round2-public-response.md:20>)。

必须分别处理：普通业务/权限失败显示原因；“任务已处理”等ServiceException通常是响应体code500，不能都写成HTTP409；网络/超时只说明客户端结果未知，不能断言审批未执行。401有统一失效提示、特殊凭据409/428有独立处理、CSRF恢复成功不是失败，用户取消应保持安静。建议采购PC/H5显式处理未通知错误，已处理/权限变化读回当前任务，未知结果先核对并保留原操作；与A11同批修公共契约，分别验收采购和报销，避免仅全局关silentError造成重复提示。

## 旧修复对照：不要重复加数

Grok提出的手机员工列表分页，已映射旧48台账HR-03：当前请求前增加pageNum，第二页失败后再请求第三页，内存结果[2,3]；较新仅成功后提交requestedPage，重试仍请求第二页，结果[2,2]。这是较新已有对应处理、当前未汇入，不能再新增编号，也不把旧16中的11项改成12项。HR-N13是另一张资料完整度列表，两副本仍有问题，不能混用状态。[双副本核对与台账位置](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/修复工作副本_2026-09-08/output/grok-peer-review-20260913/hr-pagination-reconciliation.md>)、[第二轮计数确认](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/修复工作副本_2026-09-08/output/grok-peer-review-20260913/round2-public-response.md:37>)。

旧16中以下11项在较新已有对应实现；这里只记录来源对照，不表示本次重新验证全部修复，更不表示当前或线上已汇入。

| 原编号 | 已有对应处理 | 原证据 |
|---|---|---|
| A01 | 历史补签可能覆盖员工现行工资：较新副本已有历史工资独立来源 | [原报告](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/修复工作副本_2026-09-08/docs/reports/项目逻辑与操作便利性复核-20260913.md:60>) |
| A02 | 采购退货额度包含待检货，可能错扣合格库存：两个实施副本已有按入库事实计算 | [原报告](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/修复工作副本_2026-09-08/docs/reports/项目逻辑与操作便利性复核-20260913.md:62>) |
| A03 | 部分质检成功丢响应后重试可能重复入库：两个实施副本已有持久命令处理 | [原报告](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/修复工作副本_2026-09-08/docs/reports/项目逻辑与操作便利性复核-20260913.md:64>) |
| A04 | 销售退货取消与确认并发可能账单状态不一致：两个实施副本已有锁及状态条件 | [原报告](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/修复工作副本_2026-09-08/docs/reports/项目逻辑与操作便利性复核-20260913.md:66>) |
| A05 | 报销保存迟到回包把 B 内容拼到 A 的编号上：较新副本已有表单代次保护 | [原报告](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/修复工作副本_2026-09-08/docs/reports/项目逻辑与操作便利性复核-20260913.md:68>) |
| A07 | 退货需删无关行；质检默认整批全部合格：较新副本已有勾选范围处理 | [原报告](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/修复工作副本_2026-09-08/docs/reports/项目逻辑与操作便利性复核-20260913.md:70>) |
| A08 | 删除一格排班丢失其它未保存排班：较新副本已有草稿覆盖层 | [原报告](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/修复工作副本_2026-09-08/docs/reports/项目逻辑与操作便利性复核-20260913.md:72>) |
| A09 | 删除请假附件恢复旧原因和旧日期：较新副本已有按字段合并 | [原报告](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/修复工作副本_2026-09-08/docs/reports/项目逻辑与操作便利性复核-20260913.md:74>) |
| A10 | 请假附件成功后详情超时，重试持续版本冲突：较新副本已有版本及队列恢复 | [原报告](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/修复工作副本_2026-09-08/docs/reports/项目逻辑与操作便利性复核-20260913.md:76>) |
| A15 | 报表日期已换，晚回的旧数据覆盖新结果：较新副本已有查询上下文保护 | [原报告](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/修复工作副本_2026-09-08/docs/reports/项目逻辑与操作便利性复核-20260913.md:78>) |
| A16 | 业务按钮可见，但专岗用户被附带权限阻止：两个实施副本已有专用接口接线 | [原报告](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/修复工作副本_2026-09-08/docs/reports/项目逻辑与操作便利性复核-20260913.md:80>) |

## 优先减少人工步骤的5项

1. **销售带出有效仓库，多仓按仓生成发货通知。** 保留可见校验，避免保存后才发现缺仓、用户再逐行拆单（INV-N01/N02）。
2. **导出保留原批次并提供本人历史。** 下载失败可直接重下同一批次，显示生成与下载两个状态，避免重新筛选、重新建包（A12）。
3. **结果未知先核对，失败保留输入。** 报销、补卡、云盘与审批使用稳定操作上下文；显示“已完成/明确失败/待核对”，已处理读回，失败原地继续，减少重复保存、重传和猜测重试（A11/A13、HR-N05、P03、GROK-01）。
4. **入职Excel整列文本格式与定位纠错。** 证件号、电话及紧急电话整列处理，错误定位行列并保留预览，减少重新填表；Excel入口继续启用，其他已有入口继续按已确认规则使用（HR-N07）。
5. **切换单据不串资料，待办完成后可继续下一条。** 采购退货切原单、审批待办定位和表单回包绑定原记录；只提供“查看/继续下一条”，不自动审批，减少重复返回列表、搜索和重选（A06、A14、ROOT-01及同类异步项）。

上述为实施方向，不属于已完成优化；无需再次询问已确认业务规则。[双方确认的操作优先项](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/修复工作副本_2026-09-08/output/grok-peer-review-20260913/round2-public-response.md:82>)。

## 测试记录与仍未验收的范围

以下沿用原全面复查留档结果（Node22、JDK17），不是Grok新跑的测试。跳过不算通过，测试文件数不等于用户场景数。

| 检查 | 当前副本 | 较新副本 |
|---|---|---|
| 前端全量 | 308个测试文件，0失败 | 354个测试文件，0失败 |
| 后端Maven test | 4316总计：4304通过、0失败、0错误、12跳过 | 4860总计：4827通过、2失败、4错误、27跳过 |
| 前端生产构建及pre/post检查 | 通过 | 通过 |
| 全局仓库/SQL/集成测试入口静态门禁 | 通过 | 3个容器测试命名/入口不符，未通过 |
| 发布工具Python回归 | 17通过 | 17通过 |
| Python/shell/XML静态语法 | 126/163/222通过 | 128/163/227通过 |
| 自动SQL目录顺序/字节一致性 | 110份一致；另1份破坏性SQL仅手动入口 | 122份一致；另1份破坏性SQL仅手动入口 |

较新后端2失败/4错误涉及审批policy-preview元数据审计契约、签约补偿旧字符串断言、用户签约角色保护夹具，以及3个MySQL容器测试混入普通入口且Docker不可用。需要修正真实契约/夹具与集成入口、在隔离环境执行；不得删除权限保护或用跳过充作验收。原生推送发布检查在这两个候选工作树仍因接线/配置/凭据缺口失败，不据此评价另一个独立包装目录。[原测试完整记录及日志链接](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/修复工作副本_2026-09-08/docs/reports/全项目流程与操作便利性全面复查-20260913.md:141>)、[后端新鲜计数](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/修复工作副本_2026-09-08/output/full-project-audit-20260913/root/backend-test-results.json>)。

联合讨论新增2次路由resolver执行、2次员工分页方法执行、采购112次执行，分别留档，不与全量单元测试或相互之间混算。采购84次是缺陷负向复现；Grok的支持意见不新增任何数据库或生产验收证据。

既有Safari检查仅覆盖组织切换、首页、库存筛选、销售表单、审批配置表单、签约方案、入职Excel预览、OA采购开放提示及待办定位；未提交业务。线上页面包与每份本地源码未完成完整映射，不能据局部页面观察把所有本地问题转为生产问题。[线上只读记录](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/修复工作副本_2026-09-08/output/full-project-audit-20260913/browser/observations.md>)。

仍须保留以下不确定性：

- 隔离 MySQL 两事务并发、迁移及实际数据库回滚；本轮未写业务数据库。
- 真实 Redis 撤销/续期 GET–DEL–SET 交错，Quartz 运行 JobStore 及事务协调。
- 生产 Cookie 构建、monitor 实际监听、Nacos、安全组、外层认证和实际 actuator 访问范围。
- 旧 pending_sign 数据数量、入职记录最大 ID；未读取真实人员/客户资料为这些条件背书。
- A14 真浏览器待办 A→B，GROK-01/A11 真浏览器失败提示及完整多角色审批。
- 实际文件生成中途故障与回滚清理、长流程异步并发和全部历史数据状态。
- Android/iOS 真机拍照、位置、弱网重试、锁屏/离线消息、原生签名与推送配置。
- 完整视觉/键盘/读屏/响应式体验、性能和运行监控；入口有阅读凭据不等于全分支穷尽。
- Grok 未独立完成 24 个 sourceHint controller 全文、InvTodoMapper union 各分支，也未复跑原56探针或新112次采购探针。

双方已对“如何评价现有证据”收敛，以上真实环境验收缺口仍开放。[Grok第二轮剩余边界](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/修复工作副本_2026-09-08/output/grok-peer-review-20260913/round2-public-response.md:67>)。

## 下一步实施顺序

1. **固定可回退的实施基线并清理门禁。** 保留现有脏工作树，按文件差异对照较新修复、旧48台账与D01—D23，逐项标记已汇入/未汇入；先解决ROOT-05有效契约、夹具和集成入口，建立可信回归基线。
2. **优先处理高影响P1并分层验收。** 销售仓库、物料删除约束、销售/调拨并发与补发预留、采购错对象/退货切单、方案重新发布、令牌撤销和云盘范围先做；monitor先核实运行边界，再决定配置加固。涉及并发的必须补隔离MySQL/Redis等集成证明。
3. **统一修异步、未知结果和部分成功恢复。** 表单代次、路由目标、查询上下文同时约束成功/失败/finally/确认框/重试；采购与报销静默同批修、各入口单独验收；批量操作显示逐项结果并支持原地继续。
4. **落实5项减步骤优化及剩余P2/P3。** 改善仓库带出、原批次重下、入职模板、待办连续处理，再清理重复展示与低频配置体验；保留Excel、工资历史、普通调岗和库存检验规则。
5. **完成多角色、真实设备及发布前验收。** 实际数据库状态、故障恢复、迁移/恢复演练、浏览器完整业务闭环与Android/iOS体验单独留证。源码确认、本地测试、浏览器/设备、部署和生产验收分别记录；本报告不授权或声称已执行发布。

交付文件：[57项最终清单](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/修复工作副本_2026-09-08/output/grok-peer-review-20260913/final-dispositions.json>)、[可重建报告的生成器](</Users/liuxingyu/Desktop/ERP-NEW_阿里云线上源码备份_2026-09-08_3a6fe91a2/修复工作副本_2026-09-08/output/grok-peer-review-20260913/build-final-report.py>)。原报告、业务源码及真实业务数据保持原状。
