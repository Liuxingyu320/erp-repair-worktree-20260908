<template>
  <el-form ref="form" :model="form" :rules="rules" label-width="110px" class="self-profile-form">
    <section class="profile-section">
      <div class="section-title"><i class="el-icon-user" /><span>基本信息</span></div>
      <el-row :gutter="18">
        <el-col :span="12" :xs="24"><el-form-item label="用户昵称" prop="nickName"><el-input v-model.trim="form.nickName" maxlength="30" /></el-form-item></el-col>
        <el-col :span="12" :xs="24"><el-form-item label="性别"><el-radio-group v-model="form.sex"><el-radio label="0">男</el-radio><el-radio label="1">女</el-radio><el-radio label="2">未设置</el-radio></el-radio-group></el-form-item></el-col>
        <el-col :span="12" :xs="24"><el-form-item label="婚姻状况"><el-select v-model="form.maritalStatus" clearable><el-option v-for="item in maritalOptions" :key="item" :label="item" :value="item" /></el-select></el-form-item></el-col>
        <el-col :span="12" :xs="24"><el-form-item label="民族"><el-input v-model.trim="form.ethnicity" maxlength="64" /></el-form-item></el-col>
        <el-col :span="12" :xs="24"><el-form-item label="政治面貌"><el-input v-model.trim="form.politicalStatus" maxlength="64" /></el-form-item></el-col>
      </el-row>
    </section>

    <section class="profile-section">
      <div class="section-title"><i class="el-icon-phone-outline" /><span>联系与紧急联系人</span></div>
      <el-row :gutter="18">
        <el-col :span="12" :xs="24"><el-form-item label="手机号码" prop="phonenumber"><el-input v-model.trim="form.phonenumber" maxlength="11" /></el-form-item></el-col>
        <el-col :span="12" :xs="24"><el-form-item label="邮箱" prop="email"><el-input v-model.trim="form.email" maxlength="50" /></el-form-item></el-col>
        <el-col :span="24"><el-form-item label="现住地" prop="currentAddress"><el-input v-model.trim="form.currentAddress" type="textarea" :rows="3" maxlength="255" show-word-limit placeholder="请输入现居住地址" /></el-form-item></el-col>
        <el-col :span="8" :xs="24"><el-form-item label="紧急联系人"><el-input v-model.trim="form.emergencyContact" maxlength="64" /></el-form-item></el-col>
        <el-col :span="8" :xs="24"><el-form-item label="与本人关系"><el-input v-model.trim="form.emergencyContactRelation" maxlength="32" /></el-form-item></el-col>
        <el-col :span="8" :xs="24"><el-form-item label="紧急联系电话" prop="emergencyContactPhone"><el-input v-model.trim="form.emergencyContactPhone" maxlength="32" /></el-form-item></el-col>
      </el-row>
    </section>

    <section class="profile-section">
      <div class="section-title">
        <i class="el-icon-postcard" /><span>身份与户籍</span>
        <el-tag size="mini" type="info">只读，变更请联系人事</el-tag>
      </div>
      <div class="readonly-grid">
        <div v-for="item in identityItems" :key="item.key" class="readonly-item"><span>{{ item.label }}</span><strong>{{ display(profile[item.key]) }}</strong></div>
      </div>
    </section>

    <section class="profile-section">
      <div class="section-title"><i class="el-icon-reading" /><span>教育经历</span></div>
      <el-row :gutter="18">
        <el-col :span="8" :xs="24"><el-form-item label="第一学历"><el-input v-model.trim="form.firstEducation" maxlength="64" /></el-form-item></el-col>
        <el-col :span="8" :xs="24"><el-form-item label="第一学位"><el-input v-model.trim="form.firstDegree" maxlength="64" /></el-form-item></el-col>
        <el-col :span="8" :xs="24"><el-form-item label="毕业日期"><el-date-picker v-model="form.firstGraduationDate" type="date" value-format="yyyy-MM-dd" placeholder="选择日期" /></el-form-item></el-col>
        <el-col :span="12" :xs="24"><el-form-item label="毕业学校"><el-input v-model.trim="form.firstGraduationSchool" maxlength="128" /></el-form-item></el-col>
        <el-col :span="12" :xs="24"><el-form-item label="所学专业"><el-input v-model.trim="form.firstMajor" maxlength="128" /></el-form-item></el-col>
        <el-col :span="8" :xs="24"><el-form-item label="最高学历"><el-input v-model.trim="form.highestEducation" maxlength="64" /></el-form-item></el-col>
        <el-col :span="8" :xs="24"><el-form-item label="最高学位"><el-input v-model.trim="form.highestDegree" maxlength="64" /></el-form-item></el-col>
        <el-col :span="8" :xs="24"><el-form-item label="毕业日期"><el-date-picker v-model="form.highestGraduationDate" type="date" value-format="yyyy-MM-dd" placeholder="选择日期" /></el-form-item></el-col>
        <el-col :span="12" :xs="24"><el-form-item label="毕业学校"><el-input v-model.trim="form.highestGraduationSchool" maxlength="128" /></el-form-item></el-col>
        <el-col :span="12" :xs="24"><el-form-item label="所学专业"><el-input v-model.trim="form.highestMajor" maxlength="128" /></el-form-item></el-col>
      </el-row>
    </section>

    <section class="profile-section">
      <div class="section-title"><i class="el-icon-bank-card" /><span>银行与社保</span></div>
      <el-row :gutter="18">
        <el-col :span="12" :xs="24"><el-form-item label="开户银行"><el-input v-model.trim="form.bankName" maxlength="128" /></el-form-item></el-col>
        <el-col :span="12" :xs="24">
          <el-form-item label="更换银行卡号" prop="bankAccount">
            <el-input v-model.trim="form.bankAccount" maxlength="32" show-password placeholder="留空则保持原银行卡号不变" />
            <div class="field-hint">当前：{{ display(profile.bankAccountMasked) }}</div>
          </el-form-item>
        </el-col>
      </el-row>
      <div class="readonly-grid compact-grid">
        <div v-for="item in socialItems" :key="item.key" class="readonly-item"><span>{{ item.label }}</span><strong>{{ display(profile[item.key]) }}</strong></div>
      </div>
    </section>

    <section class="profile-section">
      <div class="section-title"><i class="el-icon-office-building" /><span>任职信息</span><el-tag size="mini" type="info">只读</el-tag></div>
      <div class="readonly-grid">
        <div v-for="item in employmentItems" :key="item.key" class="readonly-item"><span>{{ item.label }}</span><strong>{{ display(profile[item.key]) }}</strong></div>
      </div>
    </section>

    <section class="profile-section">
      <div class="section-title"><i class="el-icon-document" /><span>合同信息</span><el-tag size="mini" type="info">只读</el-tag></div>
      <div class="readonly-grid">
        <div v-for="item in contractItems" :key="item.key" class="readonly-item"><span>{{ item.label }}</span><strong>{{ display(profile[item.key]) }}</strong></div>
      </div>
    </section>

    <div class="profile-actions">
      <el-button type="primary" :loading="saving" @click="submit">保存个人资料</el-button>
      <el-button @click="close">关闭</el-button>
    </div>
  </el-form>
</template>

<script>
import { updateUserProfile } from "@/api/system/user"

const EDITABLE_PROFILE_KEYS = [
  "currentAddress", "emergencyContact", "emergencyContactRelation", "emergencyContactPhone",
  "maritalStatus", "ethnicity", "politicalStatus", "firstEducation", "firstDegree",
  "firstGraduationDate", "firstGraduationSchool", "firstMajor", "highestEducation",
  "highestDegree", "highestGraduationDate", "highestGraduationSchool", "highestMajor", "bankName"
]

export default {
  props: { user: { type: Object, default: () => ({}) } },
  data() {
    return {
      form: {},
      profile: {},
      saving: false,
      maritalOptions: ["未婚", "已婚", "离异", "丧偶"],
      identityItems: [
        { key: "birthDate", label: "出生日期" }, { key: "idType", label: "证件类型" },
        { key: "idNumberMasked", label: "证件号码" }, { key: "registeredResidenceMasked", label: "户籍地址" },
        { key: "householdType", label: "户口性质" }, { key: "nationality", label: "国籍" }
      ],
      socialItems: [
        { key: "socialType", label: "社保类型" }, { key: "socialSecurityType", label: "社保状态" },
        { key: "socialSecurityLocation", label: "社保缴纳地" }, { key: "housingFundLocation", label: "公积金缴纳地" }
      ],
      employmentItems: [
        { key: "employeeNo", label: "工号" }, { key: "positionNo", label: "岗位工号" },
        { key: "companyName", label: "所属公司" }, { key: "deptLevel1Name", label: "一级部门" },
        { key: "deptLevel2Name", label: "二级部门" }, { key: "deptLevel3Name", label: "三级部门" },
        { key: "storeName", label: "门店" }, { key: "positionNames", label: "职位" },
        { key: "jobGrade", label: "职级" }, { key: "directSupervisor", label: "直属主管" },
        { key: "employeeStatus", label: "员工状态" }, { key: "employeeCategory", label: "人员类别" },
        { key: "entryDate", label: "入职日期" }, { key: "workLocation", label: "工作地点" },
        { key: "attendanceMethod", label: "考勤方式" }
      ],
      contractItems: [
        { key: "legalEntity", label: "签约主体" }, { key: "contractType", label: "合同类型" },
        { key: "contractTerm", label: "合同期限" }, { key: "contractStartDate", label: "合同开始日期" },
        { key: "contractEndDate", label: "合同到期日期" }, { key: "renewalCount", label: "续签次数" },
        { key: "probationPeriod", label: "试用期" }, { key: "probationStartDate", label: "试用期开始" },
        { key: "probationEndDate", label: "试用期结束" }, { key: "plannedRegularizationDate", label: "计划转正日期" },
        { key: "actualRegularizationDate", label: "实际转正日期" }
      ],
      rules: {
        nickName: [{ required: true, message: "用户昵称不能为空", trigger: "blur" }],
        email: [
          { required: true, message: "邮箱地址不能为空", trigger: "blur" },
          { type: "email", message: "请输入正确的邮箱地址", trigger: ["blur", "change"] }
        ],
        phonenumber: [
          { required: true, message: "手机号码不能为空", trigger: "blur" },
          { pattern: /^1[3-9][0-9]{9}$/, message: "请输入正确的手机号码", trigger: "blur" }
        ],
        currentAddress: [
          { required: true, message: "现住地不能为空", trigger: "blur" },
          { max: 255, message: "现住地长度不能超过255个字符", trigger: "blur" }
        ],
        emergencyContactPhone: [
          { pattern: /^[0-9+\-\s()]*$/, message: "请输入正确的联系电话", trigger: "blur" }
        ],
        bankAccount: [
          { pattern: /^$|^[0-9]{8,32}$/, message: "银行卡号应为8至32位数字", trigger: "blur" }
        ]
      }
    }
  },
  watch: {
    user: {
      immediate: true,
      deep: true,
      handler(user) {
        const profile = Object.assign({}, (user && user.profile) || {})
        this.profile = profile
        const form = {
          nickName: user.nickName || "",
          phonenumber: user.phonenumber || "",
          email: user.email || "",
          sex: user.sex === undefined || user.sex === null ? "2" : String(user.sex),
          bankAccount: ""
        }
        EDITABLE_PROFILE_KEYS.forEach(key => { form[key] = profile[key] || "" })
        this.form = form
      }
    }
  },
  methods: {
    display(value) {
      if (value === undefined || value === null || value === "") return "暂无"
      return String(value).includes("T") ? String(value).slice(0, 10) : value
    },
    submit() {
      this.$refs.form.validate(valid => {
        if (!valid) return
        const payload = Object.assign({}, this.form)
        if (!payload.bankAccount) delete payload.bankAccount
        this.saving = true
        updateUserProfile(payload).then(() => {
          this.$modal.msgSuccess("个人资料已保存")
          Object.assign(this.user, {
            nickName: payload.nickName,
            phonenumber: payload.phonenumber,
            email: payload.email,
            sex: payload.sex,
            currentAddress: payload.currentAddress
          })
          Object.assign(this.profile, payload)
          this.form.bankAccount = ""
        }).finally(() => { this.saving = false })
      })
    },
    close() { this.$tab.closePage() }
  }
}
</script>

<style lang="scss" scoped>
.self-profile-form {
  .profile-section { padding: 18px 0; border-bottom: 1px solid #edf2f7; }
  .profile-section:first-child { padding-top: 4px; }
  .section-title { display: flex; align-items: center; gap: 8px; margin-bottom: 18px; color: #1f2937; font-size: 16px; font-weight: 600; }
  .section-title i { color: #2f6d5b; font-size: 18px; }
  .el-select, .el-date-editor.el-input { width: 100%; }
  .readonly-grid { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 12px; }
  .compact-grid { margin-top: 4px; }
  .readonly-item { min-height: 64px; padding: 10px 14px; border-radius: 8px; background: #f8fafc; }
  .readonly-item span { display: block; margin-bottom: 6px; color: #64748b; font-size: 12px; }
  .readonly-item strong { color: #1f2937; font-size: 14px; font-weight: 500; word-break: break-all; }
  .field-hint { color: #94a3b8; font-size: 12px; line-height: 22px; }
  .profile-actions { position: sticky; bottom: 0; z-index: 2; margin: 0 -20px -20px; padding: 14px 20px; border-top: 1px solid #e5e7eb; background: rgba(255,255,255,.96); text-align: right; }
}
@media (max-width: 768px) {
  .self-profile-form {
    .readonly-grid { grid-template-columns: 1fr; }
    .profile-actions { margin-right: 0; margin-left: 0; }
  }
}
</style>
