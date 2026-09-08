# 用户员工档案字段扩展设计

## 目标

将用户管理从当前的账号基础信息扩展为可维护的员工档案，覆盖新增/编辑、详情、列表展示、Excel 导入模板、Excel 导入、Excel 导出。

本次字段范围为：

姓名、工号、所属公司、1级部门、2级部门、3级部门、4级门店、职位、职级、手机号、部门主管、直属主管、员工状态、人员类别、性别、出生日期、证件类型、证件号码、血型、户口所在地、现居住地址、第一学历、第一学位、毕业时间、第一学历毕业学校、第一学历所学专业、最高学历、最高学位、最高学历毕业时间、最高学历毕业学校、最高学历所学专业、政治面貌、备注、婚姻状况、国籍、是否外籍、民族、健康状况、紧急联系人、与紧急联系人关系、紧急联系人电话、招聘渠道、办公电话、邮箱、参加工作时间、工龄、入职时间、试用期、计划转正日期、实际转正日期、司龄、本岗位任职日期、现合同起始日、现合同到期日、合同期限、续签次数、工作所在地、工作所在城市级别、考勤方式、户口性质、社保缴纳地、公积金缴纳地、离职时间、开户银行、银行卡号、法人单位。

## 设计原则

- `sys_user` 继续只承担登录账号、手机号、邮箱、性别、主部门、状态等系统用户职责。
- 新增 `sys_user_profile` 一对一扩展表承载人事档案字段，避免把登录权限表膨胀成档案表。
- 组织层级和职位优先从现有 `sys_dept`、`sys_user_post`、`sys_post` 派生，避免部门或岗位改名后出现多处不一致。
- 员工状态和账号状态分离：`sys_user.status` 表示能否登录，`sys_user_profile.employee_status` 表示在职、试用、离职等人事状态。
- 证件号码和银行卡号属于敏感字段，普通列表和详情默认脱敏；编辑回显、导出完整值必须受 `system:user:edit` 或 `system:user:export` 权限约束。

## 字段归属

### 复用 `sys_user`

| 展示字段 | 当前字段 | 说明 |
| --- | --- | --- |
| 姓名 | `sys_user.nick_name` | 用户管理中改为员工姓名文案，但后端字段保持兼容。 |
| 手机号 | `sys_user.phonenumber` | 保留现有唯一性校验。 |
| 性别 | `sys_user.sex` | 继续使用现有字典 `sys_user_sex`。 |
| 邮箱 | `sys_user.email` | 继续使用现有邮箱校验。 |
| 备注 | `sys_user.remark` | 不再塞员工类型、入职日期等结构化信息。 |

### 从组织/岗位派生

| 展示字段 | 来源 | 说明 |
| --- | --- | --- |
| 所属公司 | 主部门路径中最近的公司级节点 | 优先使用 `dept_type = COMPANY`，无类型时按路径层级回退。 |
| 1级部门 | 主部门路径第 1 个业务层级 | 由 `sys_dept.parent_id/ancestors` 解析。 |
| 2级部门 | 主部门路径第 2 个业务层级 | 由主部门路径解析。 |
| 3级部门 | 主部门路径第 3 个业务层级 | 由主部门路径解析。 |
| 4级门店 | 主部门路径中的门店节点 | 优先使用 `dept_type = STORE`，无类型时按路径末级回退。 |
| 职位 | `sys_user_post -> sys_post.post_name` | 多岗位时按 `post_sort, post_name` 拼接。 |
| 工龄 | `work_start_date` 计算 | 只展示/导出，不编辑存储。 |
| 司龄 | `entry_date` 计算 | 只展示/导出，不编辑存储。 |

### 新增 `sys_user_profile`

新增一对一扩展表：

```sql
CREATE TABLE IF NOT EXISTS sys_user_profile (
    profile_id bigint(20) NOT NULL AUTO_INCREMENT COMMENT '档案ID',
    user_id bigint(20) NOT NULL COMMENT '用户ID',
    employee_no varchar(64) DEFAULT '' COMMENT '工号',
    job_grade varchar(64) DEFAULT '' COMMENT '职级',
    department_supervisor varchar(64) DEFAULT '' COMMENT '部门主管',
    direct_supervisor varchar(64) DEFAULT '' COMMENT '直属主管',
    employee_status varchar(32) DEFAULT '' COMMENT '员工状态',
    employee_category varchar(32) DEFAULT '' COMMENT '人员类别',
    birth_date date DEFAULT NULL COMMENT '出生日期',
    id_type varchar(32) DEFAULT '' COMMENT '证件类型',
    id_number varchar(64) DEFAULT '' COMMENT '证件号码',
    blood_type varchar(16) DEFAULT '' COMMENT '血型',
    registered_residence varchar(255) DEFAULT '' COMMENT '户口所在地',
    current_address varchar(255) DEFAULT '' COMMENT '现居住地址',
    first_education varchar(64) DEFAULT '' COMMENT '第一学历',
    first_degree varchar(64) DEFAULT '' COMMENT '第一学位',
    first_graduation_date date DEFAULT NULL COMMENT '毕业时间',
    first_graduation_school varchar(128) DEFAULT '' COMMENT '第一学历毕业学校',
    first_major varchar(128) DEFAULT '' COMMENT '第一学历所学专业',
    highest_education varchar(64) DEFAULT '' COMMENT '最高学历',
    highest_degree varchar(64) DEFAULT '' COMMENT '最高学位',
    highest_graduation_date date DEFAULT NULL COMMENT '最高学历毕业时间',
    highest_graduation_school varchar(128) DEFAULT '' COMMENT '最高学历毕业学校',
    highest_major varchar(128) DEFAULT '' COMMENT '最高学历所学专业',
    political_status varchar(64) DEFAULT '' COMMENT '政治面貌',
    marital_status varchar(32) DEFAULT '' COMMENT '婚姻状况',
    nationality varchar(64) DEFAULT '' COMMENT '国籍',
    foreign_national_flag char(1) DEFAULT 'N' COMMENT '是否外籍 Y是 N否',
    ethnicity varchar(64) DEFAULT '' COMMENT '民族',
    health_status varchar(64) DEFAULT '' COMMENT '健康状况',
    emergency_contact varchar(64) DEFAULT '' COMMENT '紧急联系人',
    emergency_contact_relation varchar(64) DEFAULT '' COMMENT '与紧急联系人关系',
    emergency_contact_phone varchar(32) DEFAULT '' COMMENT '紧急联系人电话',
    recruitment_channel varchar(128) DEFAULT '' COMMENT '招聘渠道',
    office_phone varchar(32) DEFAULT '' COMMENT '办公电话',
    work_start_date date DEFAULT NULL COMMENT '参加工作时间',
    entry_date date DEFAULT NULL COMMENT '入职时间',
    probation_period varchar(32) DEFAULT '' COMMENT '试用期',
    planned_regularization_date date DEFAULT NULL COMMENT '计划转正日期',
    actual_regularization_date date DEFAULT NULL COMMENT '实际转正日期',
    current_position_start_date date DEFAULT NULL COMMENT '本岗位任职日期',
    contract_start_date date DEFAULT NULL COMMENT '现合同起始日',
    contract_end_date date DEFAULT NULL COMMENT '现合同到期日',
    contract_term varchar(64) DEFAULT '' COMMENT '合同期限',
    renewal_count int(11) DEFAULT 0 COMMENT '续签次数',
    work_location varchar(128) DEFAULT '' COMMENT '工作所在地',
    work_city_level varchar(32) DEFAULT '' COMMENT '工作所在城市级别',
    attendance_method varchar(64) DEFAULT '' COMMENT '考勤方式',
    household_type varchar(32) DEFAULT '' COMMENT '户口性质',
    social_security_location varchar(128) DEFAULT '' COMMENT '社保缴纳地',
    housing_fund_location varchar(128) DEFAULT '' COMMENT '公积金缴纳地',
    leave_date date DEFAULT NULL COMMENT '离职时间',
    bank_name varchar(128) DEFAULT '' COMMENT '开户银行',
    bank_account varchar(64) DEFAULT '' COMMENT '银行卡号',
    legal_entity varchar(128) DEFAULT '' COMMENT '法人单位',
    create_by varchar(64) DEFAULT '' COMMENT '创建者',
    create_time datetime DEFAULT NULL COMMENT '创建时间',
    update_by varchar(64) DEFAULT '' COMMENT '更新者',
    update_time datetime DEFAULT NULL COMMENT '更新时间',
    PRIMARY KEY (profile_id),
    UNIQUE KEY uk_sys_user_profile_user (user_id),
    KEY idx_sys_user_profile_employee_no (employee_no),
    KEY idx_sys_user_profile_employee_status (employee_status),
    KEY idx_sys_user_profile_entry_date (entry_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户员工档案扩展表';
```

## 后端设计

### Domain 和 DTO

- 新增 `SysUserProfile` domain，对应 `sys_user_profile`。
- `SysUser` 增加 `profile`、派生展示字段：
  - `companyName`
  - `deptLevel1Name`
  - `deptLevel2Name`
  - `deptLevel3Name`
  - `storeName`
  - `positionNames`
  - `workYears`
  - `companyYears`
- `idNumber` 和 `bankAccount` 使用现有敏感字段脱敏能力；列表查询默认返回脱敏值，编辑接口返回完整值。

### Mapper

- 新增 `SysUserProfileMapper` 和 `SysUserProfileMapper.xml`，提供按 `user_id` 查询、批量查询、插入、更新、删除。
- `SysUserMapper.selectUserList` 左连接 `sys_user_profile`，返回列表常用字段和派生组织字段。
- `SysUserMapper.selectUserById` 返回用户基础信息、角色、岗位和完整档案。
- `insertUser` 成功后插入 profile；`updateUser` 同步 upsert profile；删除用户时逻辑删除用户并保留 profile，便于审计。

### 组织路径派生

新增后端辅助逻辑 `SysUserOrgPathResolver`：

- 输入主部门 `dept_id` 和已加载的部门路径。
- 输出所属公司、1级部门、2级部门、3级部门、4级门店。
- 优先使用 `dept_type` 判断公司和门店；缺失类型时按路径层级稳定回退。
- 导入时如果 Excel 给出了公司/部门/门店名称，按完整路径匹配 `sys_dept`，匹配不到时返回明确错误，不自动创建部门。

### 员工状态规则

- `employee_status` 可取值建议：在职、试用、正式、离职、停薪留职、待入职。
- `sys_user.status` 仍是账号状态：0 正常、1 停用。
- 当用户在编辑页把员工状态改为离职并填写离职时间时，后端同时将账号状态置为停用，防止离职员工继续登录。
- 账号状态从停用改回正常时，不自动修改员工状态，避免把离职人员误恢复为在职。

### 导入导出

- 用户导入模板扩展为完整员工档案模板。
- 导入支持通过以下方式定位已有用户：
  1. `user_name` 或 `phonenumber` 等于手机号。
  2. 工号匹配 `sys_user_profile.employee_no`。
  3. 用户账号匹配。
- 导入组织字段时优先根据公司/部门/门店完整路径定位 `dept_id`；如果只传一个部门编号，保留现有 `deptId` 导入方式。
- 导出包含全部字段；工龄、司龄、组织层级、职位为派生值。
- 现有 `scripts/erp_employee_importer.py` 改为写入 `sys_user_profile`，不再把员工类型、入职时间、外部 UserID 塞到 `remark`。外部 UserID 不在本次页面字段中，如仍需保留，可追加为导入脚本内部备注或后续扩展字段。

## 前端设计

### 用户列表

列表默认展示高频字段：

- 姓名
- 工号
- 所属公司
- 4级门店
- 职位
- 职级
- 手机号
- 员工状态
- 人员类别
- 入职时间
- 账号状态

其他字段放入右上角列显隐配置，不默认全部铺开，避免表格不可用。

搜索条件增加：

- 姓名
- 工号
- 手机号
- 所属公司/部门/门店树
- 职位
- 员工状态
- 人员类别
- 入职时间范围
- 离职时间范围

### 新增/编辑

用户弹窗改为分组表单或 Tabs：

- 账号信息：姓名、用户账号、手机号、邮箱、性别、账号状态、备注。
- 组织岗位：归属部门、岗位、职级、部门主管、直属主管、法人单位。
- 个人信息：出生日期、证件类型、证件号码、血型、户口所在地、现居住地址、婚姻状况、国籍、是否外籍、民族、健康状况、政治面貌。
- 教育信息：第一学历、第一学位、毕业时间、毕业学校、专业、最高学历、最高学位、最高学历毕业时间、最高学历毕业学校、最高学历专业。
- 用工信息：员工状态、人员类别、招聘渠道、参加工作时间、入职时间、试用期、计划转正日期、实际转正日期、本岗位任职日期、合同起止日、合同期限、续签次数、工作所在地、城市级别、考勤方式、社保缴纳地、公积金缴纳地、离职时间。
- 联系与银行：办公电话、紧急联系人、关系、联系电话、开户银行、银行卡号。

工龄和司龄为只读计算结果，不作为输入项。

### 详情抽屉

详情抽屉按同样分组展示完整字段。

- 证件号码默认脱敏。
- 银行卡号默认脱敏。
- 有编辑权限的用户进入编辑页时才回显完整值。

## 数据流

新增用户：

1. 前端提交 `SysUser` 基础字段、角色岗位、`profile`。
2. 后端校验账号、手机号、邮箱唯一性。
3. 写入 `sys_user`。
4. 写入 `sys_user_profile`。
5. 写入角色岗位关联。

修改用户：

1. 后端校验目标用户数据范围、部门范围、角色范围。
2. 更新 `sys_user` 基础字段。
3. upsert `sys_user_profile`。
4. 重建角色岗位关联。
5. 如员工状态为离职，同步停用账号。

列表查询：

1. 现有用户数据范围逻辑先过滤用户。
2. 左连接 profile、部门、岗位聚合。
3. 计算组织层级、工龄、司龄。
4. 返回列表安全字段。

## 错误处理

- 导入时组织路径匹配不到：返回具体行号和完整路径。
- 导入时工号重复：返回涉及的工号和用户。
- 证件号码、银行卡号不做强制格式校验，只限制长度，避免不同证件和银行卡格式被误拒。
- 日期字段支持 `yyyy-MM-dd`；Excel 数字日期由现有 Excel 工具解析。
- 员工状态为离职但未填离职时间时允许保存，但列表标记为离职未填日期，便于补录。

## 测试计划

后端：

- `SysUserServiceImplTest` 增加：新增用户同时保存 profile；修改用户 upsert profile；离职状态同步停用账号。
- `SysUserMapper.xml` 字符串测试增加：列表 SQL 返回 profile 字段、组织层级字段、员工状态筛选条件。
- `SysUserProfileMapper` 增加 mapper binding 测试。
- 导入测试增加：按手机号更新、按工号更新、组织路径匹配失败、工号重复失败。

前端：

- `erp-ui/test/userEmployeeProfileFields.test.js` 覆盖列表字段、搜索字段、表单分组字段、详情字段。
- `erp-ui/test/exportUx.test.js` 扩展断言，确认用户导入模板和导出字段包含员工档案字段。
- 保留现有用户昵称搜索、岗位展示、管理范围展示测试。

脚本：

- `scripts/test_employee_importer.py` 增加断言，确认导入脚本写入 `sys_user_profile`，不再把结构化员工字段拼进 `remark`。

## 迁移策略

1. 新增 SQL 迁移文件 `sql/erp_user_employee_profile_20260706.sql`。
2. SQL 中创建 `sys_user_profile`，并从现有 `sys_user.remark` 尽力回填：
   - `员工类型:` 回填 `employee_category`。
   - `入职:` 回填 `entry_date`，值为 `-` 或空时跳过。
3. 不批量修改历史 `remark`，避免破坏审计痕迹。
4. 部署顺序：数据库迁移、后端、前端。

## 非目标

- 本次不重构组织架构模型。
- 本次不把部门主管、直属主管规范成强制用户 ID 关系，先按姓名文本保存，后续如要做审批链再升级。
- 本次不新建独立员工档案菜单，仍落在系统管理的用户管理中。
- 本次不实现证件号、银行卡号数据库加密，只做接口脱敏和权限控制。
