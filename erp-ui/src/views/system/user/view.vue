<template>
  <el-drawer
    ref="userDetailDrawer"
    :visible.sync="visible"
    direction="rtl"
    size="860px"
    append-to-body
    :before-close="handleClose"
    custom-class="user-detail-drawer"
    @opened="syncDrawerAccessibility"
  >
    <div slot="title" class="drawer-title">
      <span class="drawer-title__icon"><i class="el-icon-user" /></span>
      <span class="drawer-title__copy">
        <small>USER PROFILE</small>
        <strong>用户信息详情</strong>
        <span>账号信息、组织岗位与员工档案</span>
      </span>
    </div>

    <div v-loading="loading" class="drawer-content">
      <el-alert v-if="loadError" :title="loadError" type="error" :closable="false" />
      <el-alert v-if="piiError" :title="piiError" type="warning" :closable="false" />
      <el-button v-if="loadError || piiError" size="mini" @click="retryDetail">重新加载</el-button>
      <template v-if="info && info.userId">
        <section class="profile-hero">
          <div class="profile-avatar" aria-hidden="true">{{ userInitial }}</div>
          <div class="profile-identity">
            <span class="profile-eyebrow">用户账号 · {{ info.userName || '-' }}</span>
            <h2>{{ info.nickName || '未命名用户' }}</h2>
            <div class="profile-meta">
              <span><i class="el-icon-office-building" />{{ departmentName }}</span>
              <span><i class="el-icon-s-custom" />{{ postNames || '无岗位' }}</span>
              <span><i class="el-icon-key" />{{ roleNames || '无角色' }}</span>
            </div>
          </div>
          <span class="account-badge" :class="{ 'is-disabled': info.status !== '0' }">
            <i :class="info.status === '0' ? 'el-icon-circle-check' : 'el-icon-warning-outline'" />
            {{ info.status === '0' ? '账号正常' : '账号停用' }}
          </span>
        </section>

        <section class="detail-section">
          <div class="section-heading">
            <span class="section-heading__icon"><i class="el-icon-postcard" /></span>
            <div>
              <h3>基本信息</h3>
              <p>登录身份与当前权限配置</p>
            </div>
          </div>
          <div class="info-grid info-grid--two">
            <div v-for="item in basicInfoItems" :key="item.label" class="info-card" :class="{ 'is-wide': item.wide }">
              <span class="info-label">{{ item.label }}</span>
              <span v-if="item.status" class="status-pill" :class="{ 'is-disabled': info.status !== '0' }">
                {{ item.value }}
              </span>
              <strong v-else class="info-value">{{ item.value }}</strong>
            </div>
          </div>
        </section>

        <section v-if="profileSections.length" class="detail-section">
          <div class="section-heading">
            <span class="section-heading__icon is-profile"><i class="el-icon-notebook-2" /></span>
            <div>
              <h3>员工档案</h3>
              <p>按业务主题分组查看完整人员资料</p>
            </div>
            <span v-if="!piiVisible" class="privacy-note"><i class="el-icon-lock" /> 敏感信息已隐藏</span>
          </div>

          <div v-for="section in profileSections" :key="section.title" class="profile-group">
            <div class="profile-group__title">
              <span>{{ section.title }}</span>
              <small>{{ section.items.length }} 项</small>
            </div>
            <div class="info-grid info-grid--three">
              <div
                v-for="item in section.items"
                :key="item.key"
                class="info-card info-card--plain"
                :class="{ 'is-span-two': item.span === 12 }"
              >
                <span class="info-label">{{ item.label }}</span>
                <strong class="info-value">{{ profileValue(item.key) }}</strong>
              </div>
            </div>
          </div>
        </section>

        <section class="detail-section detail-section--muted">
          <div class="section-heading">
            <span class="section-heading__icon is-record"><i class="el-icon-time" /></span>
            <div>
              <h3>系统记录</h3>
              <p>账号创建、更新与最近访问信息</p>
            </div>
          </div>
          <div class="info-grid info-grid--two">
            <div v-for="item in recordItems" :key="item.label" class="info-card info-card--plain" :class="{ 'is-wide': item.wide }">
              <span class="info-label">{{ item.label }}</span>
              <strong class="info-value">{{ item.value }}</strong>
            </div>
          </div>
        </section>
      </template>

      <div v-else class="drawer-empty">
        <span><i class="el-icon-user" /></span>
        <strong>{{ loading ? '正在整理用户资料' : '暂无用户资料' }}</strong>
        <p>{{ loading ? '请稍候，系统正在加载信息。' : '请关闭抽屉后重新选择用户。' }}</p>
      </div>
    </div>
  </el-drawer>
</template>

<script>
import { getUser, getUserPii } from '@/api/system/user'
import { getSelectedDeptId } from '@/utils/shopContext'
const { createUiOperationScope } = require('@/utils/uiOperationScope')
// Matches the dedicated SysUserPiiUpdateRequest response contract.
const PII_FIELDS = new Set(["email", "phonenumber", "sex", "birthDate", "idType", "idNumber", "bloodType", "registeredResidence", "currentAddress", "firstEducation", "firstDegree", "firstGraduationDate", "firstGraduationSchool", "firstMajor", "highestEducation", "highestDegree", "highestGraduationDate", "highestGraduationSchool", "highestMajor", "politicalStatus", "maritalStatus", "nationality", "foreignNationalFlag", "ethnicity", "healthStatus", "emergencyContact", "emergencyContactRelation", "emergencyContactPhone", "officePhone", "workLocation", "householdType", "socialSecurityLocation", "housingFundLocation", "bankName", "bankAccount"])
import { signingOptionsForKey, signingProfileLabel } from '@/views/hr/components/signingProfileOptions'

export default {
  name: 'UserViewDrawer',
  dicts: ['sys_user_sex'],
  data() {
    return {
      visible: false,
      loading: false,
      loadError: '',
      piiError: '',
      targetUserId: '',
      info: {},
      piiVisible: false,
      postOptions: [],
      roleOptions: []
    }
  },
  computed: {
    actorContextKey() {
      const store = this.$store || {}, getters = store.getters || {}
      return JSON.stringify([String(getters.id || ''), ((store.state || {}).user || {}).sessionRevision || 0, getters.permissions || []])
    },
    userInitial() {
      const chars = Array.from(String(this.info.nickName || this.info.userName || '用').trim())
      return chars[0] || '用'
    },
    departmentName() {
      return (this.info.dept && this.info.dept.deptName) || '未分配部门'
    },
    sexLabel() {
      return this.selectDictLabel(this.dict.type.sys_user_sex, this.info.sex) || '-'
    },
    postNames() {
      if (!this.postOptions.length) return ''
      const ids = this.info.postIds || []
      return this.postOptions.filter(p => ids.includes(p.postId)).map(p => p.postName).join('、') || ''
    },
    roleNames() {
      if (!this.roleOptions.length) return ''
      const ids = this.info.roleIds || []
      return this.roleOptions.filter(r => ids.includes(r.roleId)).map(r => r.roleName).join('、') || ''
    },
    basicInfoItems() {
      return [
        { label: '姓名', value: this.displayValue(this.info.nickName) },
        { label: '归属部门', value: this.departmentName },
        { label: '手机号', value: this.piiVisible ? this.displayValue(this.info.phonenumber) : '已隐藏' },
        { label: '邮箱', value: this.piiVisible ? this.displayValue(this.info.email) : '已隐藏' },
        { label: '登录账号', value: this.displayValue(this.info.userName) },
        { label: '账号状态', value: this.info.status === '0' ? '正常' : '停用', status: true },
        { label: '岗位', value: this.postNames || '无岗位' },
        { label: '性别', value: this.piiVisible ? this.sexLabel : '已隐藏' },
        { label: '角色', value: this.roleNames || '无角色', wide: true }
      ]
    },
    profileSections() {
      const sections = [
        {
          title: '组织岗位',
          items: [
            { label: '员工号', key: 'employeeNo' }, { label: '岗位工号', key: 'positionNo' }, { label: '员工状态', key: 'employeeStatus' },
            { label: '所属公司', key: 'companyName' }, { label: '人员类别', key: 'employeeCategory' }, { label: '1级部门', key: 'deptLevel1Name' },
            { label: '2级部门', key: 'deptLevel2Name' }, { label: '3级部门', key: 'deptLevel3Name' }, { label: '4级门店', key: 'storeName' },
            { label: '职位', key: 'positionNames' }, { label: '职级', key: 'jobGrade' }, { label: '部门主管', key: 'departmentSupervisor' },
            { label: '直属主管', key: 'directSupervisor' }, { label: '法人单位', key: 'legalEntity' }
          ]
        },
        {
          title: '身份学历',
          items: [
            { label: '出生日期', key: 'birthDate' }, { label: '证件类型', key: 'idType' }, { label: '证件号码', key: 'idNumber' },
            { label: '血型', key: 'bloodType' }, { label: '婚姻状况', key: 'maritalStatus' }, { label: '政治面貌', key: 'politicalStatus' },
            { label: '国籍', key: 'nationality' }, { label: '是否外籍', key: 'foreignNationalFlag' }, { label: '民族', key: 'ethnicity' },
            { label: '健康状况', key: 'healthStatus' }, { label: '户口所在地', key: 'registeredResidence' }, { label: '现居住地址', key: 'currentAddress' },
            { label: '第一学历', key: 'firstEducation' }, { label: '第一学位', key: 'firstDegree' }, { label: '毕业时间', key: 'firstGraduationDate' },
            { label: '第一学历毕业学校', key: 'firstGraduationSchool', span: 12 }, { label: '第一学历所学专业', key: 'firstMajor', span: 12 },
            { label: '最高学历', key: 'highestEducation' }, { label: '最高学位', key: 'highestDegree' }, { label: '最高学历毕业时间', key: 'highestGraduationDate' },
            { label: '最高学历毕业学校', key: 'highestGraduationSchool', span: 12 }, { label: '最高学历所学专业', key: 'highestMajor', span: 12 }
          ]
        },
        {
          title: '联系招聘',
          items: [
            { label: '紧急联系人', key: 'emergencyContact' }, { label: '与紧急联系人关系', key: 'emergencyContactRelation' }, { label: '紧急联系人电话', key: 'emergencyContactPhone' },
            { label: '招聘渠道', key: 'recruitmentChannel' }, { label: '办公电话', key: 'officePhone' }
          ]
        },
        {
          title: '任职合同',
          items: [
            { label: '参加工作时间', key: 'workStartDate' }, { label: '工龄', key: 'workYears' }, { label: '入职时间', key: 'entryDate' },
            { label: '试用期', key: 'probationPeriod' }, { label: '计划转正日期', key: 'plannedRegularizationDate' }, { label: '实际转正日期', key: 'actualRegularizationDate' },
            { label: '司龄', key: 'companyYears' }, { label: '本岗位任职日期', key: 'currentPositionStartDate' }, { label: '现合同起始日', key: 'contractStartDate' },
            { label: '现合同到期日', key: 'contractEndDate' }, { label: '合同类型', key: 'contractType' }, { label: '合同期限', key: 'contractTerm' },
            { label: '续签次数', key: 'renewalCount' }, { label: '工作所在地', key: 'workLocation' }, { label: '工作所在城市级别', key: 'workCityLevel' },
            { label: '考勤方式', key: 'attendanceMethod' }, { label: '户口性质', key: 'householdType' }, { label: '社保类型', key: 'socialType' },
            { label: '社保缴纳地', key: 'socialSecurityLocation' }, { label: '公积金缴纳地', key: 'housingFundLocation' }, { label: '离职时间', key: 'leaveDate' },
            { label: '开户银行', key: 'bankName' }, { label: '银行卡号', key: 'bankAccount' }
          ]
        }
      ]
      return this.piiVisible ? sections : [sections[0]]
    },
    recordItems() {
      return [
        { label: '创建者', value: this.displayValue(this.info.createBy) },
        { label: '创建时间', value: this.displayValue(this.info.createTime) },
        { label: '更新者', value: this.displayValue(this.info.updateBy) },
        { label: '更新时间', value: this.displayValue(this.info.updateTime) },
        { label: '最后登录 IP', value: this.displayValue(this.info.loginIp) },
        { label: '最后登录时间', value: this.displayValue(this.info.loginDate) },
        { label: '备注', value: this.displayValue(this.info.remark), wide: true }
      ]
    }
  },
  watch: {
    actorContextKey() { this.handleClose() },
    visible(value) { if (!value) this.clearDetail() }
  },
  created() { window.addEventListener('erp:dept-changed', this.handleClose) },
  activated() { this.detailScope().activate() },
  deactivated() { this.handleClose(); this.detailScope().deactivate() },
  beforeDestroy() {
    window.removeEventListener('erp:dept-changed', this.handleClose)
    this.detailScope().deactivate()
    this.clearDetail()
  },
  methods: {
    detailScope() {
      if (!this._detailScope) this._detailScope = createUiOperationScope(() => ({ actor: this.actorContextKey, dept: getSelectedDeptId() }))
      return this._detailScope
    },
    clearDetail() {
      this.detailScope().invalidate()
      this.loading = false
      this.info = {}
      this.postOptions = []
      this.roleOptions = []
      this.piiVisible = false
      this.loadError = ''
      this.piiError = ''
      this.targetUserId = ''
    },
    retryDetail() { if (this.targetUserId) return this.open(this.targetUserId) },
    async open(userId) {
      this.clearDetail()
      const target = String(userId || '')
      this.visible = true
      this.targetUserId = target
      if (!/^[1-9]\d{0,18}$/.test(target) || (typeof userId === 'number' && !Number.isSafeInteger(userId))) {
        this.loadError = '用户编号无效，请重新选择'
        return
      }
      const scope = this.detailScope(), token = scope.begin('detail', target)
      const current = () => this.visible && scope.isCurrent(token, this.targetUserId)
      this.loading = true
      let base
      try {
        const res = await getUser(target, { silentError: true })
        if (!current()) return
        if (!res.data || String(res.data.userId) !== target) throw new Error('用户资料已变化，请重新加载')
        base = { ...res.data, profile: { ...(res.data.profile || {}) }, postIds: res.postIds || [], roleIds: res.roleIds || [] }
        this.info = base
        this.postOptions = res.posts || []
        this.roleOptions = res.roles || []
      } catch (error) {
        if (current()) { this.loadError = error && error.message || '用户资料加载失败，请重试'; this.loading = false }
        return
      }
      try {
        if (this.$auth && this.$auth.hasPermi('system:user:pii:read')) {
          const piiRes = await getUserPii(target, 'BUSINESS_PROCESSING', { silentError: true })
          if (!current()) return
          const pii = piiRes.data || {}
          if (String(pii.userId) !== target || String(base.userId) !== target) throw new Error('敏感资料归属已变化，请重新加载')
          const info = { ...base, profile: { ...base.profile } }
          const baseFields = new Set(['email', 'phonenumber', 'sex'])
          Object.keys(pii).forEach(field => {
            if (!PII_FIELDS.has(field)) return
            if (baseFields.has(field)) info[field] = pii[field]
            else info.profile[field] = pii[field]
          })
          this.info = info
          this.piiVisible = true
        }
      } catch (error) {
        if (current()) {
          const code = error && (error.code || (error.response && (error.response.status || (error.response.data || {}).code)))
          this.piiError = Number(code) === 403 ? '无权查看敏感资料' : '敏感资料加载失败，请重试'
        }
      } finally {
        if (current()) this.loading = false
      }
    },
    handleClose() {
      this.visible = false
      this.clearDetail()
    },
    syncDrawerAccessibility() {
      const component = this.$refs.userDetailDrawer
      const dialog = component && component.$el && component.$el.querySelector('.el-drawer[role="dialog"]')
      if (!dialog) return
      dialog.removeAttribute('aria-labelledby')
      dialog.setAttribute('aria-label', '用户信息详情')
    },
    displayValue(value) {
      return value === undefined || value === null || value === '' ? '-' : value
    },
    profileValue(key) {
      const profile = this.info.profile || {}
      const value = profile[key]
      const options = signingOptionsForKey(key)
      if (options) return signingProfileLabel(options, value)
      return this.displayValue(value)
    }
  }
}
</script>

<style lang="scss">
.user-detail-drawer {
  max-width: calc(100vw - 24px);
  background: #f4f7fb;
  box-shadow: -22px 0 60px rgba(15, 23, 42, 0.18);

  .el-drawer__header {
    min-height: 78px;
    flex: 0 0 auto;
    align-items: center;
    margin-bottom: 0;
    padding: 14px 22px;
    color: #17243a;
    background: rgba(255, 255, 255, 0.98);
    border-bottom: 1px solid #e7ebf2;
  }

  .el-drawer__header > :first-child { min-width: 0; flex: 1; }

  .el-drawer__close-btn {
    width: 38px;
    height: 38px;
    flex: 0 0 38px;
    display: inline-flex;
    align-items: center;
    justify-content: center;
    margin-left: 14px;
    border-radius: 11px;
    color: #718096;
    transition: color 0.18s ease, background 0.18s ease;
  }

  .el-drawer__close-btn:hover { color: #4658d4; background: #eef1ff; }

  .el-drawer__body {
    min-height: 0;
    flex: 1;
    overflow-x: hidden;
    overflow-y: auto;
    background: #f4f7fb;
    scrollbar-color: #c7cfdd transparent;
    scrollbar-width: thin;
  }

  .el-drawer__body::-webkit-scrollbar { width: 7px; }
  .el-drawer__body::-webkit-scrollbar-track { background: transparent; }
  .el-drawer__body::-webkit-scrollbar-thumb { border-radius: 8px; background: #c7cfdd; }
}

@media (max-width: 700px) {
  .user-detail-drawer { width: 100% !important; max-width: none; }
}
</style>

<style lang="scss" scoped>
.drawer-title { min-width: 0; display: flex; align-items: center; gap: 12px; }

.drawer-title__icon {
  width: 42px;
  height: 42px;
  flex: 0 0 42px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border-radius: 13px;
  background: #5264d9;
  color: #fff;
  box-shadow: 0 10px 22px rgba(70, 85, 205, 0.23);
  font-size: 19px;
}

.drawer-title__copy { min-width: 0; display: flex; flex-direction: column; gap: 2px; }
.drawer-title__copy small { color: #5264d9; font-size: 9px; font-weight: 700; letter-spacing: 0.14em; }
.drawer-title__copy strong { overflow: hidden; color: #1d2b42; font-size: 15px; font-weight: 700; text-overflow: ellipsis; white-space: nowrap; }
.drawer-title__copy > span { color: #8a96a8; font-size: 10px; }

.drawer-content { min-height: 100%; padding: 20px 22px 34px; }

.profile-hero {
  min-height: 132px;
  display: grid;
  grid-template-columns: auto minmax(0, 1fr) auto;
  gap: 16px;
  align-items: center;
  padding: 22px;
  border: 1px solid #dce4f2;
  border-radius: 20px;
  background: #fff;
  box-shadow: 0 16px 38px rgba(35, 48, 82, 0.08);
}

.profile-avatar {
  width: 66px;
  height: 66px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border: 2px solid #fff;
  border-radius: 20px;
  background: #5264d9;
  color: #fff;
  box-shadow: 0 14px 28px rgba(70, 86, 208, 0.25);
  font-size: 27px;
  font-weight: 700;
}

.profile-identity { min-width: 0; }
.profile-identity h2 { margin: 4px 0 9px; color: #1d2b42; font-size: 24px; font-weight: 700; line-height: 1.2; }
.profile-eyebrow { color: #5668d9; font-size: 9px; font-weight: 700; letter-spacing: 0.12em; }
.profile-meta { display: flex; flex-wrap: wrap; gap: 7px 14px; }
.profile-meta span { min-width: 0; display: inline-flex; align-items: center; gap: 5px; color: #68778d; font-size: 11px; }
.profile-meta i { color: #7685dc; }

.account-badge,
.status-pill {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 7px 10px;
  border: 1px solid #c9eadc;
  border-radius: 9px;
  background: #edf8f4;
  color: #278a69;
  font-size: 11px;
  font-weight: 650;
}

.account-badge { align-self: start; }
.account-badge.is-disabled,
.status-pill.is-disabled { color: #718096; border-color: #dde3eb; background: #f3f5f8; }

.detail-section {
  margin-top: 12px;
  padding: 18px;
  border: 1px solid #e1e7f0;
  border-radius: 18px;
  background: #fff;
  box-shadow: 0 12px 30px rgba(35, 48, 82, 0.055);
}

.detail-section--muted { background: #fbfcfe; }

.section-heading { display: flex; align-items: center; gap: 11px; margin-bottom: 14px; }
.section-heading > div { min-width: 0; display: flex; flex-direction: column; gap: 2px; }
.section-heading h3 { margin: 0; color: #26354d; font-size: 14px; font-weight: 700; }
.section-heading p { margin: 0; color: #919cad; font-size: 10px; }

.section-heading__icon {
  width: 36px;
  height: 36px;
  flex: 0 0 36px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border-radius: 11px;
  background: #eef1ff;
  color: #5365d5;
  font-size: 16px;
}

.section-heading__icon.is-profile { color: #248e75; background: #eaf8f3; }
.section-heading__icon.is-record { color: #6c55c7; background: #f2edff; }

.privacy-note {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  margin-left: auto;
  padding: 6px 9px;
  border-radius: 8px;
  background: #f3f5f8;
  color: #7a8798;
  font-size: 10px;
}

.info-grid { display: grid; gap: 8px; }
.info-grid--two { grid-template-columns: repeat(2, minmax(0, 1fr)); }
.info-grid--three { grid-template-columns: repeat(3, minmax(0, 1fr)); }

.info-card {
  min-width: 0;
  min-height: 66px;
  display: flex;
  justify-content: center;
  flex-direction: column;
  gap: 6px;
  padding: 11px 13px;
  border: 1px solid #e7ebf2;
  border-radius: 12px;
  background: #f8f9fc;
}

.info-card--plain { min-height: 62px; background: #fbfcfe; }
.info-card.is-wide { grid-column: 1 / -1; }
.info-card.is-span-two { grid-column: span 2; }
.info-label { color: #929dac; font-size: 10px; line-height: 1.35; }
.info-value { color: #35445b; font-size: 12px; font-weight: 600; line-height: 1.55; overflow-wrap: anywhere; }

.profile-group + .profile-group { margin-top: 16px; padding-top: 16px; border-top: 1px dashed #dce2eb; }
.profile-group__title { display: flex; align-items: center; justify-content: space-between; gap: 12px; margin-bottom: 9px; }
.profile-group__title span { color: #4a596f; font-size: 12px; font-weight: 700; }
.profile-group__title small { padding: 3px 7px; border-radius: 7px; background: #f0f2f7; color: #929cac; font-size: 9px; }

.drawer-empty { min-height: 420px; display: flex; align-items: center; justify-content: center; flex-direction: column; padding: 40px; color: #8c98aa; text-align: center; }
.drawer-empty > span { width: 62px; height: 62px; display: inline-flex; align-items: center; justify-content: center; margin-bottom: 14px; border-radius: 20px; background: #e9edff; color: #6575dc; font-size: 25px; }
.drawer-empty strong { color: #46556b; font-size: 15px; }
.drawer-empty p { margin: 7px 0 0; font-size: 11px; }

@media (max-width: 900px) {
  .info-grid--three { grid-template-columns: repeat(2, minmax(0, 1fr)); }
}

@media (max-width: 620px) {
  .drawer-content { padding: 14px 12px 24px; }
  .profile-hero { grid-template-columns: auto minmax(0, 1fr); padding: 16px; }
  .profile-avatar { width: 54px; height: 54px; border-radius: 17px; font-size: 22px; }
  .profile-identity h2 { font-size: 20px; }
  .account-badge { grid-column: 1 / -1; justify-self: start; }
  .detail-section { padding: 14px; border-radius: 15px; }
  .section-heading { align-items: flex-start; }
  .privacy-note { display: none; }
  .info-grid--two,
  .info-grid--three { grid-template-columns: 1fr; }
  .info-card.is-span-two { grid-column: auto; }
}
</style>
