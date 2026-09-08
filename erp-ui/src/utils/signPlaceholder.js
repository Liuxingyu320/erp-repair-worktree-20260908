const PLACEHOLDER_LABELS = {
  employeeName: '员工姓名',
  employeeIdCard: '身份证号',
  employeePhone: '手机号码',
  employeeAddress: '现居地址',
  employeeDeptName: '所属部门',
  postName: '岗位名称',
  postLevel: '岗位等级',
  employmentType: '用工类型',
  contractType: '合同类型',
  socialType: '社保类型',
  servicePersonType: '劳务人员类型',
  insuranceType: '保险类型',
  scenario: '签约场景',
  entryDate: '入职日期',
  contractStartDate: '合同开始日期',
  contractEndDate: '合同结束日期',
  previousContractEndDate: '原合同结束日期',
  previousEmploymentType: '原合同类型',
  previousRenewalCount: '原续签次数',
  renewalCount: '续签次数',
  probationStartDate: '试用期开始日期',
  probationEndDate: '试用期结束日期',
  actualRegularizationDate: '实际转正日期',
  transferEffectiveDate: '调岗生效日期',
  beforeDeptName: '调岗前部门',
  afterDeptName: '调岗后部门',
  beforePostName: '调岗前岗位',
  afterPostName: '调岗后岗位',
  workStartDate: '工作开始日期',
  workEndDate: '工作结束日期',
  leaveDate: '离职日期',
  leaveReason: '离职原因',
  offboardingType: '离职类型',
  salarySettlementStatus: '薪资结算状态',
  assetHandoverStatus: '资产交接状态',
  nonCompeteDecision: '竞业限制决定',
  compensationAmount: '补偿金额',
  compensationNote: '补偿说明',
  baseSalary: '基本工资',
  postSalary: '岗位工资',
  fieldAllowance: '外勤补贴',
  performanceSalary: '绩效工资',
  salaryTotal: '薪资合计',
  salaryVersion: '薪资版本',
  companyName: '公司法定全称',
  companyCode: '公司内部编码',
  companyCreditCode: '统一社会信用代码',
  companyAddress: '公司注册地址',
  companyLegalRepresentative: '法定代表人',
  companyPhone: '公司联系电话',
  signDate: '签署日期'
}

export function signPlaceholderLabel(code) {
  return PLACEHOLDER_LABELS[code] || '未登记字段'
}

export function signPlaceholderToken(code) {
  return '${' + signPlaceholderLabel(code) + '}'
}

export default PLACEHOLDER_LABELS
