# 员工签约/入离调转签约包实施计划

> For agentic workers: Execute this plan step by step. Keep the existing labor contract module working throughout. Do not replace the current labor contract flow in phase 1; add the signing package module beside it, reuse safe utilities where appropriate, and verify each phase with backend tests, frontend source checks, and production build.

## 目标

在保留现有 `OA管理 -> 劳动合同` 模块的基础上，新增 `OA管理 -> 员工签约/入离调转签约包` 模块。

新模块用于管理员维护有限类型的入职、离职、调岗、转正签署文件模板，并按“签约包”一次性发给员工在移动端查看和签署。系统按文件类型校验占位符，不做“任意 docx/xlsx 上传即可签”的无限制上传。

## 现状判断

现有劳动合同模块已经覆盖劳动合同模板、员工选择、发送、移动端签署、签署归档、hash 校验、企业章快照等能力，适合作为劳动合同专用模块继续保留。

`/Users/liuxingyu/Desktop/入离调转/入离调转` 中的文件类型比劳动合同多，包含：

- 入职签署类：`入职承诺书`、`岗位职责确认书`、`员工手册签收确认书`、`薪酬结构确认书`、`劳动合同`、`劳务合同`、`劳务合同签收单`
- 档案/登记类：`员工档案目录`、`应聘登记表`、`背调报告`
- 离职类：`离职证明`

这些文件不应全部走现有劳动合同模块。劳动合同模块的业务语义是“单份劳动合同”，而入离调转文件更适合按员工、场景、文件清单组成“签约包”。

## 产品范围

### 保留现有劳动合同模块

现有入口继续用于劳动合同专用管理：

- 劳动合同模板维护
- 劳动合同发起
- 劳动合同移动端签署
- 劳动合同证据/hash 校验

不在一期把它迁入通用签约包，避免影响现有已上线合同。

### 新增员工签约/入离调转签约包模块

新增桌面端入口：

- `OA管理 -> 员工签约`
- 一级页签建议：
  - `签约包`
  - `模板管理`
  - `签署记录`

新增移动端入口：

- `移动端 -> 我的签约`

员工进入后看到待签署签约包，点入后按文件列表逐份查看，最终一次确认签署全部必签文件。

## 文件类型设计

系统内置有限文件类型，不允许管理员随意创建未知类型。管理员只能在这些类型下上传模板。

建议一期支持：

| 场景 | 文件类型编码 | 文件名称 | 文件格式 | 是否员工签署 | 校验方式 |
| --- | --- | --- | --- | --- | --- |
| 入职 | `ONBOARD_LABOR_CONTRACT` | 劳动合同 | docx | 是 | 必填占位符 |
| 入职 | `ONBOARD_SERVICE_CONTRACT` | 劳务合同 | docx | 是 | 必填占位符 |
| 入职 | `ONBOARD_SERVICE_RECEIPT` | 劳务合同签收单 | docx | 是 | 必填占位符 |
| 入职 | `ONBOARD_COMMITMENT` | 入职承诺书 | docx | 是 | 必填占位符 |
| 入职 | `ONBOARD_POST_DUTY` | 岗位职责确认书 | docx | 是 | 必填占位符 |
| 入职 | `ONBOARD_HANDBOOK_RECEIPT` | 员工手册签收确认书 | docx | 是 | 必填占位符 |
| 入职 | `ONBOARD_SALARY_CONFIRM` | 薪酬结构确认书 | docx | 是 | 必填占位符 |
| 入职 | `ONBOARD_APPLICATION_FORM` | 应聘登记表 | xlsx | 否或是 | xlsx 占位符 |
| 入职 | `ONBOARD_BACKGROUND_CHECK` | 背调报告 | docx | 否 | HR 上传归档 |
| 入职 | `ONBOARD_ARCHIVE_CATALOG` | 员工档案目录 | docx | 否 | HR 上传归档 |
| 离职 | `OFFBOARD_LEAVE_CERTIFICATE` | 离职证明 | docx | 否或确认签收 | 必填占位符 |

岗位职责确认书和薪酬结构确认书有多个版本，版本不作为新类型无限扩张，而是同一文件类型下通过适用规则选择：

- `岗位等级范围`: `2-4`、`5-6`、`7-8`
- `薪酬结构版本`: `A`、`B`
- `用工类型`: `劳动合同`、`劳务合同`
- `社保口径`: `有社保`、`无社保`

## 占位符规范

所有需要系统填充的 `docx/xlsx` 模板必须使用明确占位符，不能依赖 Word 空线或人工填写位置。

建议通用占位符：

- `${employeeName}` 员工姓名
- `${employeeIdCard}` 身份证号
- `${employeePhone}` 手机号
- `${employeeDeptName}` 部门/门店
- `${postName}` 岗位
- `${entryDate}` 入职日期
- `${contractStartDate}` 合同开始日期
- `${contractEndDate}` 合同结束日期
- `${companyName}` 公司名称
- `${signDate}` 签署日期

按文件类型增加扩展占位符：

- 薪酬结构确认书：
  - `${baseSalary}`
  - `${postSalary}`
  - `${performanceSalary}`
  - `${salaryTotal}`
- 岗位职责确认书：
  - `${postLevel}`
  - `${postDutyContent}`
- 离职证明：
  - `${leaveDate}`
  - `${leaveReason}`
  - `${workStartDate}`
  - `${workEndDate}`

模板上传时后端读取文件内容，按文件类型校验必填占位符。缺少占位符时拒绝保存，并返回缺少列表。

## 数据模型

### 新表：签约模板

`oa_sign_template`

- `template_id`
- `template_type`
- `template_name`
- `template_version`
- `scenario`
- `employment_type`
- `social_type`
- `post_level_scope`
- `salary_version`
- `file_url`
- `file_name`
- `file_size`
- `file_hash`
- `required_placeholders`
- `optional_placeholders`
- `employee_sign_required`
- `sort_order`
- `status`
- `remark`
- `create_by`
- `create_time`
- `update_by`
- `update_time`

### 新表：签约包

`oa_sign_package`

- `package_id`
- `package_no`
- `employee_id`
- `employee_name_snapshot`
- `employee_phone_snapshot`
- `employee_id_card_snapshot`
- `dept_id_snapshot`
- `dept_name_snapshot`
- `scenario`
- `employment_type`
- `social_type`
- `post_name_snapshot`
- `post_level_snapshot`
- `status`
- `sent_time`
- `viewed_time`
- `signed_time`
- `void_reason`
- `create_by`
- `create_time`
- `update_by`
- `update_time`

状态建议：

- `draft`
- `pending_sign`
- `part_viewed`
- `signed`
- `voided`
- `failed`

### 新表：签约包文件

`oa_sign_package_document`

- `document_id`
- `package_id`
- `template_id`
- `template_type`
- `document_name`
- `template_version_snapshot`
- `source_file_url_snapshot`
- `generated_file_url`
- `generated_pdf_url`
- `signed_file_url`
- `certificate_file_url`
- `file_hash_before_sign`
- `file_hash_after_sign`
- `employee_sign_required`
- `read_confirmed`
- `signed`
- `sort_order`
- `status`
- `error_message`
- `create_time`
- `update_time`

### 新表：签署事件

`oa_sign_event`

- `event_id`
- `package_id`
- `document_id`
- `event_type`
- `operator_user_id`
- `operator_name`
- `operator_role`
- `ip_address`
- `user_agent`
- `event_payload`
- `document_hash`
- `prev_event_hash`
- `event_hash`
- `create_time`

事件类型建议：

- `PACKAGE_CREATED`
- `PACKAGE_SENT`
- `DOCUMENT_VIEWED`
- `DOCUMENT_READ_CONFIRMED`
- `PACKAGE_SIGNED`
- `PACKAGE_VOIDED`
- `DOCUMENT_GENERATE_FAILED`

## 后端实施计划

### 1. 数据库脚本

新增 SQL 文件：

- `sql/employee_sign_package.sql`

内容包括：

- 新建 `oa_sign_template`
- 新建 `oa_sign_package`
- 新建 `oa_sign_package_document`
- 新建 `oa_sign_event`
- 新增菜单权限：
  - `oa:signPackage:list`
  - `oa:signPackage:add`
  - `oa:signPackage:edit`
  - `oa:signPackage:send`
  - `oa:signPackage:void`
  - `oa:signPackage:template`
  - `oa:signPackage:sign`

### 2. 后端领域对象

新增文件：

- `erp-modules/erp-oa/src/main/java/com/erp/oa/domain/OaSignTemplate.java`
- `erp-modules/erp-oa/src/main/java/com/erp/oa/domain/OaSignPackage.java`
- `erp-modules/erp-oa/src/main/java/com/erp/oa/domain/OaSignPackageDocument.java`
- `erp-modules/erp-oa/src/main/java/com/erp/oa/domain/OaSignEvent.java`

新增枚举或常量：

- `erp-modules/erp-oa/src/main/java/com/erp/oa/constant/OaSignTemplateType.java`
- `erp-modules/erp-oa/src/main/java/com/erp/oa/constant/OaSignPackageStatus.java`

### 3. Mapper

新增文件：

- `erp-modules/erp-oa/src/main/java/com/erp/oa/mapper/OaSignTemplateMapper.java`
- `erp-modules/erp-oa/src/main/java/com/erp/oa/mapper/OaSignPackageMapper.java`
- `erp-modules/erp-oa/src/main/java/com/erp/oa/mapper/OaSignPackageDocumentMapper.java`
- `erp-modules/erp-oa/src/main/java/com/erp/oa/mapper/OaSignEventMapper.java`
- `erp-modules/erp-oa/src/main/resources/mapper/oa/OaSignTemplateMapper.xml`
- `erp-modules/erp-oa/src/main/resources/mapper/oa/OaSignPackageMapper.xml`
- `erp-modules/erp-oa/src/main/resources/mapper/oa/OaSignPackageDocumentMapper.xml`
- `erp-modules/erp-oa/src/main/resources/mapper/oa/OaSignEventMapper.xml`

### 4. 模板服务

新增文件：

- `erp-modules/erp-oa/src/main/java/com/erp/oa/service/IOaSignTemplateService.java`
- `erp-modules/erp-oa/src/main/java/com/erp/oa/service/impl/OaSignTemplateServiceImpl.java`

核心能力：

- 查询模板类型字典
- 上传模板
- 按模板类型校验占位符
- 启用/停用模板
- 按员工条件匹配模板
- 防止未知模板类型上传

模板校验应复用或抽取现有劳动合同模板占位符检测逻辑。若现有逻辑只适配 docx，需要扩展 xlsx 读取。

### 5. 签约包服务

新增文件：

- `erp-modules/erp-oa/src/main/java/com/erp/oa/service/IOaSignPackageService.java`
- `erp-modules/erp-oa/src/main/java/com/erp/oa/service/impl/OaSignPackageServiceImpl.java`

核心能力：

- 创建签约包草稿
- 根据场景、用工类型、岗位等级、薪酬版本、社保口径匹配模板
- 生成签约包文件
- 发送签约包
- 移动端获取待签包列表
- 移动端查看签约包详情
- 记录文件查看/阅读确认
- 一次性签署全部必签文件
- 撤回未签署签约包
- 查询签署记录和证据文件

### 6. 文档生成与签章

优先抽取现有：

- `OaLaborContractDocumentService`

新增或扩展为：

- `erp-modules/erp-oa/src/main/java/com/erp/oa/service/impl/OaSignDocumentRenderService.java`
- `erp-modules/erp-oa/src/main/java/com/erp/oa/service/impl/OaSignEvidenceService.java`

原则：

- 模板文件、企业章、员工信息在发送时快照
- 签署时使用快照文件，不重新读取最新模板
- 每份文件保留签署前 hash 和签署后 hash
- 签约包有总事件链，每份文件也有独立证据

### 7. Controller

新增文件：

- `erp-modules/erp-oa/src/main/java/com/erp/oa/controller/OaSignPackageController.java`
- `erp-modules/erp-oa/src/main/java/com/erp/oa/controller/OaSignTemplateController.java`

接口建议：

- `GET /oa/signPackage/list`
- `POST /oa/signPackage`
- `GET /oa/signPackage/{packageId}`
- `POST /oa/signPackage/{packageId}/send`
- `POST /oa/signPackage/{packageId}/void`
- `GET /oa/signPackage/template/list`
- `GET /oa/signPackage/template/types`
- `POST /oa/signPackage/template`
- `PUT /oa/signPackage/template`
- `PUT /oa/signPackage/template/{templateId}/status`
- `GET /oa/signPackage/mobile/list`
- `GET /oa/signPackage/mobile/{packageId}`
- `POST /oa/signPackage/mobile/{packageId}/view/{documentId}`
- `POST /oa/signPackage/mobile/{packageId}/read/{documentId}`
- `POST /oa/signPackage/mobile/{packageId}/sign`

## 前端实施计划

### 1. API 文件

新增：

- `erp-ui/src/api/oa/signPackage.js`
- `erp-ui/src/api/oa/signTemplate.js`

提供桌面端和移动端统一 API 封装。

### 2. 桌面端签约包页面

新增：

- `erp-ui/src/views/oa/signPackage/index.vue`

功能：

- 签约包列表
- 员工筛选
- 状态筛选
- 场景筛选
- 新建签约包
- 自动匹配模板
- 文件清单预览
- 发送签约包
- 撤回未签包
- 查看签署证据

电脑端优化点：

- 列表只展示必要信息：员工、部门、场景、文件数、状态、发送时间、签署时间
- 详情使用抽屉或弹窗展示文件清单，不把所有文件 URL 堆在表格里
- 状态用步骤条：`草稿 -> 已发送 -> 已查看 -> 已签署/已归档`
- 模板缺失、占位符缺失时给出明确错误，不让管理员发出不完整包

### 3. 桌面端模板管理页面

可作为 `index.vue` 内页签，也可拆分：

- `erp-ui/src/views/oa/signPackage/template.vue`

功能：

- 按文件类型上传模板
- 显示适用规则
- 显示必填占位符清单
- 上传后展示校验结果
- 启用/停用
- 版本管理

模板上传体验：

- 文件类型先选，不允许空类型上传
- 选择文件后立即校验
- 缺少占位符用列表展示
- 明确区分 `员工签署文件` 和 `HR归档文件`

### 4. 移动端我的签约

新增：

- `erp-ui/src/views/mobile/signPackage/index.vue`

功能：

- 待签包列表
- 已签包列表
- 签约包详情
- 文件逐份预览
- 必读文件阅读确认
- 底部固定签署按钮
- 一次性签署全部必签文件

移动端优化点：

- 不让员工面对一堆下载链接，按卡片显示文件清单
- 每份文件状态明确：`未查看`、`已查看`、`已确认`、`已签署`
- 必签文件未全部阅读确认时，底部签署按钮禁用并提示缺少哪几份
- 签署确认文案包含签约包名称、文件数量、员工姓名和身份证后四位
- 签署完成后展示归档状态和证据下载入口

### 5. 路由和菜单

需要检查并修改：

- `erp-ui/src/router/index.js`
- `erp-ui/src/router/mobileRouteDefinitions.js`
- 后台菜单 SQL

新增桌面端菜单：

- `OA管理 / 员工签约`

新增移动端路由：

- `/mobile/sign-package`

## `/Users/liuxingyu/Desktop/入离调转` 文件适配计划

这批原始文件不能直接全部上传到新模块。实施时需要做一次模板适配：

1. 为每个文件归类到系统内置文件类型。
2. 给需要系统填充的 docx/xlsx 增加 `${...}` 占位符。
3. 对不需要员工签署的文件标记为 HR 归档或 HR 上传。
4. 对岗位职责和薪酬结构这类多版本文件配置适用规则。
5. 上传模板时由系统校验占位符。
6. 校验通过后才能用于生成签约包。

建议先适配这些文件：

- `2-6劳动合同.docx`
- `2-7劳务合同.docx`
- `2-8劳务合同签收单.docx`
- `2-3入职承诺书.docx`
- `2-4岗位职责确认书（2-4级）.docx`
- `2-4岗位职责确认书（5-6级）.docx`
- `2-4岗位职责确认书（7-8级）.docx`
- `2-5员工手册签收确认书.docx`
- `2-5薪酬结构确认书（A版）.docx`
- `2-5薪酬结构确认书（B版）.docx`

档案类可放到第二批：

- `0 员工档案目录.docx`
- `1-1应聘登记表.xlsx`
- `1-8背调报告.docx`
- `员工手册.docx`
- `离职证明.docx`

## 测试计划

### 后端测试

新增：

- `erp-modules/erp-oa/src/test/java/com/erp/oa/service/impl/OaSignTemplateServiceImplTest.java`
- `erp-modules/erp-oa/src/test/java/com/erp/oa/service/impl/OaSignPackageServiceImplTest.java`
- `erp-modules/erp-oa/src/test/java/com/erp/oa/controller/OaSignPackageControllerTest.java`

覆盖：

- 未知模板类型拒绝上传
- docx 缺必填占位符拒绝保存
- xlsx 缺必填占位符拒绝保存
- 多版本模板按岗位等级/薪酬版本匹配
- 签约包生成失败时不允许发送
- 已发送签约包使用模板快照
- 员工只能看到自己的签约包
- 未阅读确认不能签署
- 签署后不能普通撤回
- 签署事件 hash 链可校验

### 前端测试

新增或扩展：

- `erp-ui/test/signPackageModule.test.js`
- `erp-ui/test/mobileSignPackageFeatureCompleteness.test.js`

覆盖：

- 桌面端路由/API 存在
- 模板管理有文件类型选择、占位符提示、状态管理
- 签约包页面有模板匹配、文件清单、发送、撤回、证据入口
- 移动端有签约包列表、文件状态、阅读确认、一次签署按钮
- 移动端签署按钮在未读完必签文件时禁用

### 验证命令

后端：

```bash
mvn -pl erp-oa -Dtest=OaSignTemplateServiceImplTest,OaSignPackageServiceImplTest,OaSignPackageControllerTest,OaLaborContractServiceImplTest,OaLaborContractDocumentServiceTest,OaLaborContractControllerTest test
```

前端：

```bash
cd erp-ui
node test/signPackageModule.test.js
node test/mobileSignPackageFeatureCompleteness.test.js
node test/laborContractModule.test.js
node test/mobileOaFeatureCompleteness.test.js
npm run build:prod
```

## 分阶段交付

### 第一阶段：签约包基础能力

- 新增数据库表和菜单权限脚本
- 新增模板类型注册和占位符校验
- 新增签约包创建、生成、发送、撤回
- 新增桌面端签约包列表和模板管理
- 新增移动端签约包列表和详情

交付标准：

- 管理员能维护固定类型模板
- 管理员能创建入职签约包并发送
- 员工移动端能看到待签包和文件清单

### 第二阶段：移动端一次性签署

- 阅读确认
- 一次性签署全部必签文件
- 事件链和证据文件
- 签署完成后归档

交付标准：

- 员工必须看完必签文件才能签
- 签署后每份文件有 hash 和证据
- 后台可查询签署记录

### 第三阶段：模板适配和业务规则完善

- 适配 `/Users/liuxingyu/Desktop/入离调转` 第一批模板
- 增加岗位等级、薪酬版本、用工类型、社保口径匹配
- 增加异常提示和补发/作废流程

交付标准：

- 入职签约包可以自动带出对应模板
- 模板缺失时后台明确提示缺少哪一类
- 原劳动合同模块不受影响

## 风险和约束

- 不建议做“什么文件都能上传并签”的功能。这样会绕过占位符校验、业务分类、证据结构和移动端阅读确认。
- `/Users/liuxingyu/Desktop/入离调转` 的原始文件需要改造成模板后才能上传使用。
- Excel 模板校验要单独处理，不能只沿用 docx 解析。
- 劳动合同如果未来也要纳入签约包，需要做迁移方案；一期先保留现状，降低上线风险。
- 员工手册本体通常不需要签署，建议签署“员工手册签收确认书”，员工手册作为附件阅读。

## 实施顺序清单

- [ ] 创建数据库 SQL 和菜单权限脚本
- [ ] 新增签约模板、签约包、签约包文件、签署事件领域对象
- [ ] 新增 Mapper 和 XML
- [ ] 新增模板类型常量和必填占位符规则
- [ ] 新增模板上传与占位符校验服务
- [ ] 新增签约包创建、模板匹配、文件生成服务
- [ ] 新增发送、撤回、详情、记录查询接口
- [ ] 新增移动端查看、阅读确认、签署接口
- [ ] 新增桌面端 API 封装
- [ ] 新增桌面端签约包页面
- [ ] 新增桌面端模板管理页面
- [ ] 新增移动端我的签约页面
- [ ] 接入路由和菜单
- [ ] 补充后端单元测试和控制器测试
- [ ] 补充前端源码完整性测试
- [ ] 运行后端 Maven 测试
- [ ] 运行前端 Node 测试
- [ ] 运行 `npm run build:prod`
- [ ] 适配 `/Users/liuxingyu/Desktop/入离调转` 第一批模板并上传验证
