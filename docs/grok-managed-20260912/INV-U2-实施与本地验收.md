# INV-U2 / D08 调拨异常凭证

2026-09-12。根授权 15 路径，原始字节已存 before/ 与 before-manifest.json。9 旧路径、6 新路径。MobileActionDialog.vue 的原始 SHA256 为 d28d87ef36f1f8ea9eed30c13f35b1dc327d47130f16517b17d5b901ae0f4575，与根 INV-U1 accepted-manifest 一致。未修改采购页、盘点页、通用上传组件、全局台账或云盘基础 DTO。

## 实际行为

- PC 调拨按批收货和逐项差异处置、H5 同两条入口改用共用 TransferEvidencePicker。提供拍照、上传文件、浏览云盘空间/目录、当前目录关键词搜索/分页、选择已有文件、文件名、可预览图片缩略图及图片/PDF预览。上传/选择直接沿现有云盘权限与配置，不另开公开 URL 上传通道，也不自动开启云盘或调拨差异开关。
- 新凭证内部统一为 drive:十进制节点ID，多个逗号分隔，每项最多10个；用户不需要填写编号。字符串支持全 Long 正范围，不安全 Number、坏编号、目录、回收节点拒绝。若上传已成功但响应编号不精确，显示结果未确认并阻止提交，不伪装为已关联成功。
- 上传进度、失败重试、取消按单文件隔离。网络未知结果不自动重新上传；提示移除本次上传后在已有附件中查找。移除只撤销本次关联/客户端任务，不删除云盘原文件。A 已上传而 B 仍上传时，移除 A 不会丢 B，未处理的上传会阻止收货/处置提交。
- 行、批次/差异请求、组织、账号、路由和窗口代次组成当前上下文，旧上传、目录/详情读取和预览的迟到响应不写入新上下文。预览只使用带授权的云盘 content API 返回 blob，关闭/移除/离页回收 object URL。
- 历史非规范文字引用只读显示；既有非必填处理可以保留原历史字段，新上传替换为受控节点。残损直接退回/核销必填时，历史文字不再充当新凭证；同一事件循环内从非必填切到必填，父提交方法也直接校验受控引用，不依赖尚未触发的组件 watch。

## 后端边界

新增 InvTransferEvidenceService。ReceiptProcessor 在原发货批次锁、调拨账号/目标仓库范围、请求量与开关校验后，首次库存/差异写入前核验节点。DiscrepancyProcessor 保留原权限范围、原请求回放、版本/状态/数量/成本校验顺序，在构造新处置计划时核验新增节点，随后才执行首次写入。

RemoteFileService 已有 inner binding 接口，不改其合同；DriveBusinessContentController 增加 TRANSFER_EVIDENCE 用途。文件侧仍先解析当前用户，执行原云盘空间读权限、活动文件、存储对象和摘要检查，再用现有 DriveFilePolicy 校验文件名/MIME/大小。目录、回收文件、无权文件、坏对象不会成为新业务凭证；HTML/SVG等活动类型也不因新用途被放开。新用途没有跨云盘授权的业务预览入口。

已成功请求即使附件后来被回收或权限发生变化，完全相同的原请求仍返回原结果，不重复操作库存；改附件载荷的同请求被拒绝。原历史凭证与新凭证的判定不覆盖旧处置记录。

本次没有扩大 V2 明细库存裁决链。PC/H5 原有批次收货与逐项处置 API 仍然执行原开关/审批/库存路线；V2 数据仍按既有拒绝旧入口的保护处理。

## 本地证据

- 前端新增 transferEvidencePicker.test.js：14 项真实 Vue 方法/渲染与真实 API 包装调用通过，0失败/跳过。仅网络、浏览器文件/URL能力由测试桩替代，上传、选择、父提交与引用逻辑均来自实际源文件。frontend-new.log。
- U1 共享质检回归 purchaseQualitySelection.test.js：14 项通过，u1-shared-regression.log。task03 异步读取隔离最终28项通过，task03-final.log。5组既有调拨/云盘断言脚本也通过；不把脚本文件数当成业务方法数。
- Maven 库存：115 方法，0失败/错误/跳过。新 EvidenceService 7、新实际处理器串联6、旧 DiscrepancyProcessor 21、旧 TransferServiceImpl 81。maven-inventory-final.log、inventory-tests.json、inventory-surefire/。
- Maven 文件：17 方法，0失败/错误/跳过。BusinessContentController 3（含新增用途1）、原 DriveContentService 14。maven-u2.log、file-tests.json、file-surefire/。总计132个后端方法，新的行为方法14，不重复累计独立 javac/java 16项预探针。
- 保留两轮非 U2 编译阻断证据：F5 服务接口未完成、F5/F4 两个旧 Fake 未补方法。责任代理补齐后库存正式重跑通过；没有为了通过而修改别人的范围。task03 首次复跑在并行 F5 新采购 API mock 尚未更新时失败，原失败保留 frontend-old.log；由 F5 作者精确接新 mock 后最终28通过。

没有连接任何业务数据库，没有 MySQL 场景、浏览器/真机、岗位、部署或线上验收。全部源码/测试与补丁在 final-files/、final-manifest.json、INV-U2-exact.patch 冻结，根代理后续独立审核。
