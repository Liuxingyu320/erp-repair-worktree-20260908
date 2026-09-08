<template>
  <div class="mobile-profile-page mobile-system-page">
    <main class="mobile-profile-shell mobile-system-scroll" aria-label="手机个人资料" data-mobile-scroll-root>
      <header class="profile-header mobile-system-topbar">
        <div>
          <span>账号设置</span>
          <h1>个人资料</h1>
        </div>
        <button type="button" @click="goHome">返回工作台</button>
      </header>

      <section class="profile-card profile-summary mobile-system-panel">
        <div class="avatar-wrap">
          <img :src="profileAvatar" alt="当前用户头像">
          <button type="button" :disabled="isPreviewMode || avatarSaving" @click="chooseAvatar">
            {{ isPreviewMode ? "预览模式" : avatarSaving ? "上传中" : "更换头像" }}
          </button>
          <input ref="avatarInput" hidden type="file" accept="image/*" :disabled="isPreviewMode || avatarSaving" @change="uploadSelectedAvatar">
        </div>
        <div class="profile-identity">
          <h2>{{ user.nickName || user.userName || "当前用户" }}</h2>
          <p>{{ user.userName || "-" }}</p>
          <span>{{ organizationText }}</span>
          <small>{{ roleGroup || "当前账号" }}</small>
        </div>
      </section>

      <section v-if="isPreviewMode" class="profile-card preview-banner">
        <strong>演示/预览资料</strong>
        <span>可以检查手机布局和表单切换，但资料、头像和密码不会写入。</span>
      </section>

      <section class="profile-shortcuts" aria-label="账号快捷操作">
        <button type="button" @click="goNotice">通知公告</button>
        <button type="button" @click="goSelectShop">{{ switchContextLabel }}</button>
        <button type="button" class="danger" :disabled="isPreviewMode" @click="logoutMobile">退出登录</button>
      </section>

      <nav class="profile-tabs" aria-label="个人资料设置">
        <button :class="{ active: activeSection === 'profile' }" type="button" @click="setSection('profile')">基本资料</button>
        <button :class="{ active: activeSection === 'reset-password' }" type="button" @click="setSection('reset-password')">修改密码</button>
      </nav>

      <section v-if="loading" class="profile-card profile-state">资料加载中...</section>
      <section v-else-if="loadError" class="profile-card profile-state error" role="alert">
        <p>{{ loadError }}</p>
        <button type="button" @click="loadProfile">重新加载</button>
      </section>

      <form v-else-if="activeSection === 'profile'" class="profile-card profile-form" novalidate @submit.prevent="saveProfile">
        <h2 class="mobile-section-title">基本信息</h2>
        <label for="mobile-profile-nickname">
          <span>用户昵称</span>
          <input id="mobile-profile-nickname" v-model.trim="profileForm.nickName" type="text" maxlength="30" autocomplete="name" :disabled="isPreviewMode">
          <small v-if="profileErrors.nickName" class="field-error">{{ profileErrors.nickName }}</small>
        </label>
        <h2 class="mobile-section-title">联系与紧急联系人</h2>
        <label for="mobile-profile-phone">
          <span>手机号码</span>
          <input id="mobile-profile-phone" v-model.trim="profileForm.phonenumber" type="tel" maxlength="11" autocomplete="tel" inputmode="tel" :disabled="isPreviewMode">
          <small v-if="profileErrors.phonenumber" class="field-error">{{ profileErrors.phonenumber }}</small>
        </label>
        <label for="mobile-profile-email">
          <span>邮箱</span>
          <input id="mobile-profile-email" v-model.trim="profileForm.email" type="email" maxlength="50" autocomplete="email" inputmode="email" :disabled="isPreviewMode">
          <small v-if="profileErrors.email" class="field-error">{{ profileErrors.email }}</small>
        </label>
        <label for="mobile-profile-current-address">
          <span>现住地</span>
          <textarea
            id="mobile-profile-current-address"
            v-model.trim="profileForm.currentAddress"
            maxlength="255"
            rows="3"
            autocomplete="street-address"
            placeholder="请输入现居住地址"
            :disabled="isPreviewMode"
          />
          <small v-if="profileErrors.currentAddress" class="field-error">{{ profileErrors.currentAddress }}</small>
        </label>
        <label for="mobile-profile-emergency-contact">
          <span>紧急联系人</span>
          <input id="mobile-profile-emergency-contact" v-model.trim="profileForm.emergencyContact" type="text" maxlength="64" :disabled="isPreviewMode">
        </label>
        <label for="mobile-profile-emergency-relation">
          <span>与本人关系</span>
          <input id="mobile-profile-emergency-relation" v-model.trim="profileForm.emergencyContactRelation" type="text" maxlength="32" :disabled="isPreviewMode">
        </label>
        <label for="mobile-profile-emergency-phone">
          <span>紧急联系电话</span>
          <input id="mobile-profile-emergency-phone" v-model.trim="profileForm.emergencyContactPhone" type="tel" maxlength="32" :disabled="isPreviewMode">
          <small v-if="profileErrors.emergencyContactPhone" class="field-error">{{ profileErrors.emergencyContactPhone }}</small>
        </label>
        <fieldset>
          <legend>性别</legend>
          <label class="radio-field"><input v-model="profileForm.sex" type="radio" value="0" :disabled="isPreviewMode"> 男</label>
          <label class="radio-field"><input v-model="profileForm.sex" type="radio" value="1" :disabled="isPreviewMode"> 女</label>
          <label class="radio-field"><input v-model="profileForm.sex" type="radio" value="2" :disabled="isPreviewMode"> 未设置</label>
        </fieldset>
        <label for="mobile-profile-marital-status">
          <span>婚姻状况</span>
          <select id="mobile-profile-marital-status" v-model="profileForm.maritalStatus" :disabled="isPreviewMode">
            <option value="">未填写</option><option>未婚</option><option>已婚</option><option>离异</option><option>丧偶</option>
          </select>
        </label>
        <label for="mobile-profile-ethnicity"><span>民族</span><input id="mobile-profile-ethnicity" v-model.trim="profileForm.ethnicity" maxlength="64" :disabled="isPreviewMode"></label>
        <label for="mobile-profile-political-status"><span>政治面貌</span><input id="mobile-profile-political-status" v-model.trim="profileForm.politicalStatus" maxlength="64" :disabled="isPreviewMode"></label>

        <h2 class="mobile-section-title">身份与户籍 <small>只读</small></h2>
        <dl class="mobile-readonly-list">
          <div v-for="item in identityItems" :key="item.key"><dt>{{ item.label }}</dt><dd>{{ displayProfileValue(profileData[item.key]) }}</dd></div>
        </dl>

        <h2 class="mobile-section-title">教育经历</h2>
        <label v-for="item in educationInputs" :key="item.key" :for="'mobile-' + item.key">
          <span>{{ item.label }}</span>
          <input :id="'mobile-' + item.key" v-model.trim="profileForm[item.key]" :type="item.type || 'text'" :maxlength="item.maxlength" :disabled="isPreviewMode">
        </label>

        <h2 class="mobile-section-title">银行与社保</h2>
        <label for="mobile-profile-bank-name"><span>开户银行</span><input id="mobile-profile-bank-name" v-model.trim="profileForm.bankName" maxlength="128" :disabled="isPreviewMode"></label>
        <label for="mobile-profile-bank-account">
          <span>更换银行卡号</span>
          <input id="mobile-profile-bank-account" v-model.trim="profileForm.bankAccount" type="password" maxlength="32" inputmode="numeric" placeholder="留空则保持不变" :disabled="isPreviewMode">
          <small>当前：{{ displayProfileValue(profileData.bankAccountMasked) }}</small>
          <small v-if="profileErrors.bankAccount" class="field-error">{{ profileErrors.bankAccount }}</small>
        </label>
        <dl class="mobile-readonly-list">
          <div v-for="item in socialItems" :key="item.key"><dt>{{ item.label }}</dt><dd>{{ displayProfileValue(profileData[item.key]) }}</dd></div>
        </dl>

        <h2 class="mobile-section-title">任职信息 <small>只读</small></h2>
        <dl class="mobile-readonly-list">
          <div v-for="item in employmentItems" :key="item.key"><dt>{{ item.label }}</dt><dd>{{ displayProfileValue(profileData[item.key]) }}</dd></div>
        </dl>

        <h2 class="mobile-section-title">合同信息 <small>只读</small></h2>
        <dl class="mobile-readonly-list">
          <div v-for="item in contractItems" :key="item.key"><dt>{{ item.label }}</dt><dd>{{ displayProfileValue(profileData[item.key]) }}</dd></div>
        </dl>
        <p v-if="profileMessage" :class="['form-message', profileMessageType]" role="alert">{{ profileMessage }}</p>
        <button class="primary full" type="submit" :disabled="isPreviewMode || profileSaving">
          {{ profileSaving ? "保存中..." : "保存基本资料" }}
        </button>
      </form>

      <form v-else class="profile-card profile-form" novalidate @submit.prevent="savePassword">
        <label for="mobile-profile-old-password">
          <span>旧密码</span>
          <input id="mobile-profile-old-password" v-model="passwordForm.oldPassword" type="password" autocomplete="current-password" :disabled="isPreviewMode">
          <small v-if="passwordErrors.oldPassword" class="field-error">{{ passwordErrors.oldPassword }}</small>
        </label>
        <label for="mobile-profile-new-password">
          <span>新密码</span>
          <input id="mobile-profile-new-password" v-model="passwordForm.newPassword" type="password" autocomplete="new-password" :disabled="isPreviewMode">
          <small v-if="passwordErrors.newPassword" class="field-error">{{ passwordErrors.newPassword }}</small>
        </label>
        <label for="mobile-profile-confirm-password">
          <span>确认新密码</span>
          <input id="mobile-profile-confirm-password" v-model="passwordForm.confirmPassword" type="password" autocomplete="new-password" :disabled="isPreviewMode">
          <small v-if="passwordErrors.confirmPassword" class="field-error">{{ passwordErrors.confirmPassword }}</small>
        </label>
        <p class="password-hint">密码长度为 8–20 位，并遵循当前系统密码复杂度要求。</p>
        <p v-if="passwordMessage" :class="['form-message', passwordMessageType]" role="alert">{{ passwordMessage }}</p>
        <button class="primary full" type="submit" :disabled="isPreviewMode || passwordSaving">
          {{ passwordSaving ? "修改中..." : "修改密码" }}
        </button>
      </form>
    </main>

    <nav class="mobile-profile-bottom-nav mobile-system-bottom-nav" aria-label="手机底部导航">
      <button v-for="item in bottomNav" :key="item.label" :class="{ active: isBottomNavItemActive(item) }" type="button" @click="openNavigation(item)">
        <svg-icon :icon-class="item.icon" />
        <span>{{ item.label }}</span>
      </button>
    </nav>
  </div>
</template>

<script>
import cache from "@/plugins/cache"
import defAva from "@/assets/images/profile.jpg"
import { getSelectedDeptContext } from "@/utils/shopContext"
import { resetPasswordResetReminderState } from "@/utils/passwordResetReminder"
import { getUserProfile, updateUserProfile, uploadAvatar, updateUserPwd } from "@/api/system/user"

const { getMobileBottomNav, getMobileHomePath, isMobileBottomNavItemActive } = require("../mobileNavigation")
const mobileViewport = require("../mobileViewport")
const startMobileViewportSync = typeof mobileViewport.startMobileViewportSync === "function"
  ? mobileViewport.startMobileViewportSync
  : () => false
const stopMobileViewportSync = typeof mobileViewport.stopMobileViewportSync === "function"
  ? mobileViewport.stopMobileViewportSync
  : () => false
const { validateMobileProfile, validateMobilePassword } = require("./mobileProfileValidation")
const { mobileErrorMessage } = require("../mobileErrorMessage")

const MAX_AVATAR_BYTES = 5 * 1024 * 1024
const EDITABLE_PROFILE_KEYS = [
  "currentAddress", "emergencyContact", "emergencyContactRelation", "emergencyContactPhone",
  "maritalStatus", "ethnicity", "politicalStatus", "firstEducation", "firstDegree",
  "firstGraduationDate", "firstGraduationSchool", "firstMajor", "highestEducation",
  "highestDegree", "highestGraduationDate", "highestGraduationSchool", "highestMajor", "bankName"
]

function requestErrorMessage(error, fallback) {
  return mobileErrorMessage(error, fallback)
}

export default {
  name: "MobileProfilePage",
  data() {
    return {
      loading: false,
      loadError: "",
      user: {},
      roleGroup: "",
      postGroup: "",
      avatarPreview: "",
      avatarSaving: false,
      activeSection: "profile",
      profileForm: { nickName: "", phonenumber: "", email: "", sex: "2", currentAddress: "", bankAccount: "" },
      profileErrors: {},
      profileSaving: false,
      profileMessage: "",
      profileMessageType: "",
      passwordForm: { oldPassword: "", newPassword: "", confirmPassword: "" },
      passwordErrors: {},
      passwordSaving: false,
      passwordMessage: "",
      passwordMessageType: "",
      educationInputs: [
        { key: "firstEducation", label: "第一学历", maxlength: 64 },
        { key: "firstDegree", label: "第一学位", maxlength: 64 },
        { key: "firstGraduationDate", label: "第一学历毕业日期", type: "date" },
        { key: "firstGraduationSchool", label: "第一学历毕业学校", maxlength: 128 },
        { key: "firstMajor", label: "第一学历专业", maxlength: 128 },
        { key: "highestEducation", label: "最高学历", maxlength: 64 },
        { key: "highestDegree", label: "最高学位", maxlength: 64 },
        { key: "highestGraduationDate", label: "最高学历毕业日期", type: "date" },
        { key: "highestGraduationSchool", label: "最高学历毕业学校", maxlength: 128 },
        { key: "highestMajor", label: "最高学历专业", maxlength: 128 }
      ],
      identityItems: [
        { key: "birthDate", label: "出生日期" }, { key: "idType", label: "证件类型" },
        { key: "idNumberMasked", label: "证件号码" }, { key: "registeredResidenceMasked", label: "户籍地址" },
        { key: "householdType", label: "户口性质" }, { key: "nationality", label: "国籍" }
      ],
      socialItems: [
        { key: "socialType", label: "社保类型" }, { key: "socialSecurityLocation", label: "社保缴纳地" },
        { key: "housingFundLocation", label: "公积金缴纳地" }
      ],
      employmentItems: [
        { key: "employeeNo", label: "工号" }, { key: "companyName", label: "所属公司" },
        { key: "positionNames", label: "职位" }, { key: "jobGrade", label: "职级" },
        { key: "directSupervisor", label: "直属主管" }, { key: "employeeStatus", label: "员工状态" },
        { key: "entryDate", label: "入职日期" }, { key: "workLocation", label: "工作地点" }
      ],
      contractItems: [
        { key: "legalEntity", label: "签约主体" }, { key: "contractType", label: "合同类型" },
        { key: "contractTerm", label: "合同期限" }, { key: "contractStartDate", label: "合同开始日期" },
        { key: "contractEndDate", label: "合同到期日期" }, { key: "probationPeriod", label: "试用期" }
      ]
    }
  },
  computed: {
    selectedContext() {
      return getSelectedDeptContext()
    },
    isPreviewMode() {
      return this.$route.query && this.$route.query.preview === "1"
    },
    userPermissions() {
      return (this.$store && this.$store.getters && this.$store.getters.permissions) || []
    },
    bottomNav() {
      return getMobileBottomNav(this.selectedContext.deptType, this.userPermissions)
    },
    profileAvatar() {
      return this.avatarPreview || (this.$store && this.$store.getters && this.$store.getters.avatar) || this.user.avatar || defAva
    },
    organizationText() {
      const deptName = this.selectedContext.deptName || (this.user.dept && this.user.dept.deptName)
      return deptName || "暂未选择业务组织"
    },
    switchContextLabel() {
      return this.selectedContext.deptType === "WAREHOUSE" ? "切换仓库" : "切换店铺"
    },
    profileData() {
      return (this.user && this.user.profile) || {}
    },
    passwordPolicyType() {
      return cache.session.get("pwrChrtype") || "0"
    }
  },
  watch: {
    "$route.query.mode"() {
      this.applyRouteMode()
    }
  },
  created() {
    try {
      startMobileViewportSync()
      this.applyRouteMode()
      this.loadProfile()
    } catch (e) {
      console.error('Profile page created error:', e)
      this.loadError = '个人资料页面加载失败，请刷新重试'
      this.loading = false
    }
  },
  beforeDestroy() {
    stopMobileViewportSync()
  },
  methods: {
    isBottomNavItemActive(item) {
      return Boolean(item && isMobileBottomNavItemActive(item.path, this.$route.path))
    },
    applyRouteMode() {
      const mode = this.$route.query && this.$route.query.mode
      this.activeSection = mode === "reset-password" ? "reset-password" : "profile"
    },
    setSection(section) {
      this.activeSection = section
      const query = section === "reset-password" ? { mode: "reset-password" } : {}
      if (this.isPreviewMode) query.preview = "1"
      this.$router.replace({ path: "/mobile/profile", query }).catch(() => {})
    },
    loadProfile() {
      try {
        if (this.isPreviewMode) {
          this.applyLoadedProfile(this.previewProfile(), "店铺员工", "移动预览")
          this.loading = false
          this.loadError = ""
          return
        }
        this.loading = true
        this.loadError = ""
        getUserProfile().then(response => {
          this.applyLoadedProfile(
            (response && response.data) || {},
            (response && response.roleGroup) || "",
            (response && response.postGroup) || ""
          )
        }).catch(error => {
          this.loadError = requestErrorMessage(error, "个人资料加载失败，请重试")
        }).finally(() => {
          this.loading = false
        })
      } catch (e) {
        console.error('loadProfile error:', e)
        this.loadError = '个人资料加载失败，请刷新重试'
        this.loading = false
      }
    },
    previewProfile() {
      return {
        userName: "mobile.preview",
        nickName: "手机预览用户",
        phonenumber: "13800000000",
        email: "mobile.preview@example.com",
        sex: "2",
        currentAddress: "上海市浦东新区预览路 1 号",
        profile: {
          currentAddress: "上海市浦东新区预览路 1 号",
          employeeNo: "00001",
          companyName: "预览公司",
          positionNames: "店员",
          contractType: "固定期限"
        },
        dept: { deptName: "预览门店" }
      }
    },
    applyLoadedProfile(user, roleGroup, postGroup) {
      this.user = user || {}
      this.roleGroup = roleGroup || ""
      this.postGroup = postGroup || ""
      const profile = this.user.profile || {}
      const form = {
        nickName: this.user.nickName || "",
        phonenumber: this.user.phonenumber || "",
        email: this.user.email || "",
        sex: this.user.sex === undefined || this.user.sex === null ? "2" : String(this.user.sex),
        bankAccount: ""
      }
      EDITABLE_PROFILE_KEYS.forEach(key => { form[key] = profile[key] || (key === "currentAddress" ? this.user.currentAddress || "" : "") })
      this.profileForm = form
    },
    saveProfile() {
      if (this.isPreviewMode) return
      this.profileErrors = validateMobileProfile(this.profileForm)
      this.profileMessage = ""
      if (Object.keys(this.profileErrors).length) return
      this.profileSaving = true
      const payload = {
        nickName: String(this.profileForm.nickName || "").trim(),
        phonenumber: String(this.profileForm.phonenumber || "").trim(),
        email: String(this.profileForm.email || "").trim(),
        sex: this.profileForm.sex
      }
      EDITABLE_PROFILE_KEYS.forEach(key => { payload[key] = this.profileForm[key] })
      if (this.profileForm.bankAccount) payload.bankAccount = String(this.profileForm.bankAccount).trim()
      updateUserProfile(payload).then(() => {
        this.user = Object.assign({}, this.user, payload, {
          profile: Object.assign({}, this.profileData, payload)
        })
        if (this.$store && this.$store.commit) this.$store.commit("SET_NICK_NAME", payload.nickName)
        this.profileMessageType = "success"
        this.profileMessage = "基本资料已保存"
        this.profileForm.bankAccount = ""
      }).catch(error => {
        this.profileMessageType = "error"
        this.profileMessage = requestErrorMessage(error, "资料保存失败，请重试")
      }).finally(() => {
        this.profileSaving = false
      })
    },
    chooseAvatar() {
      if (!this.isPreviewMode && !this.avatarSaving && this.$refs.avatarInput) this.$refs.avatarInput.click()
    },
    displayProfileValue(value) {
      if (value === undefined || value === null || value === "") return "暂无"
      return String(value).includes("T") ? String(value).slice(0, 10) : value
    },
    uploadSelectedAvatar(event) {
      if (this.isPreviewMode) return
      const input = event && event.target
      const file = input && input.files && input.files[0]
      if (!file) return
      this.profileMessageType = "error"
      if (!file.type || file.type.indexOf("image/") !== 0) {
        this.profileMessage = "请选择 JPG、PNG 等图片文件"
        input.value = ""
        return
      }
      if (file.size > MAX_AVATAR_BYTES) {
        this.profileMessage = "头像文件不能超过 5MB"
        input.value = ""
        return
      }
      const formData = new FormData()
      formData.append("avatarfile", file, file.name)
      this.avatarSaving = true
      this.profileMessage = ""
      uploadAvatar(formData).then(response => {
        this.avatarPreview = response && response.imgUrl ? response.imgUrl : ""
        if (this.avatarPreview && this.$store && this.$store.commit) this.$store.commit("SET_AVATAR", this.avatarPreview)
        this.profileMessageType = "success"
        this.profileMessage = "头像已更新"
      }).catch(error => {
        this.profileMessageType = "error"
        this.profileMessage = requestErrorMessage(error, "头像上传失败，请重试")
      }).finally(() => {
        this.avatarSaving = false
        input.value = ""
      })
    },
    savePassword() {
      if (this.isPreviewMode) return
      this.passwordErrors = validateMobilePassword(this.passwordForm, this.passwordPolicyType)
      this.passwordMessage = ""
      if (Object.keys(this.passwordErrors).length) return
      this.passwordSaving = true
      updateUserPwd(this.passwordForm.oldPassword, this.passwordForm.newPassword).then(() => {
        this.passwordForm = { oldPassword: "", newPassword: "", confirmPassword: "" }
        resetPasswordResetReminderState()
        this.passwordMessageType = "success"
        this.passwordMessage = "密码修改成功"
      }).catch(error => {
        this.passwordMessageType = "error"
        this.passwordMessage = requestErrorMessage(error, "密码修改失败，请重试")
      }).finally(() => {
        this.passwordSaving = false
      })
    },
    goHome() {
      try {
        const homePath = getMobileHomePath(this.selectedContext.deptType, this.userPermissions)
        this.$router.push(homePath).catch(() => {
          location.href = homePath
        })
      } catch (e) {
        location.href = '/workbench' // fallback
      }
    },
    goNotice() {
      this.openPath("/mobile/notice")
    },
    goSelectShop() {
      const query = { redirect: this.$route.fullPath }
      if (this.isPreviewMode) query.preview = "1"
      this.$router.push({ path: "/select-shop", query }).catch(() => {})
    },
    openNavigation(item) {
      if (!item || !item.path) return
      this.openPath({ path: item.path, query: item.query })
    },
    openPath(source) {
      try {
        const target = typeof source === "string"
          ? { path: source, query: {} }
          : { path: source && source.path, query: Object.assign({}, (source && source.query) || {}) }
        if (!target.path) return
        if (this.isPreviewMode) target.query.preview = "1"
        const route = Object.keys(target.query).length ? target : target.path
        this.$router.push(route).catch(() => {})
      } catch (e) {
        console.error('Navigation error:', e)
        location.href = '/workbench'
      }
    },
    logoutMobile() {
      this.$confirm("确定注销并退出系统吗？", "退出登录", {
        confirmButtonText: "退出",
        cancelButtonText: "取消",
        type: "warning"
      }).then(() => this.$store.dispatch("LogOut")).then(() => {
        location.href = "/index"
      }).catch(() => {})
    }
  }
}
</script>

<style scoped lang="scss">
.mobile-profile-page {
  height: var(--mobile-viewport-height, 100dvh);
  min-height: var(--mobile-viewport-height, 100dvh);
  overflow: hidden;
  padding-bottom: 0;
  background: var(--mobile-color-page);
  color: #122019;
  box-sizing: border-box;
}

.mobile-profile-shell {
  width: min(100%, 480px);
  height: var(--mobile-viewport-height, 100dvh);
  margin: 0 auto;
  overflow-y: auto;
  -webkit-overflow-scrolling: touch;
  padding: max(22px, env(safe-area-inset-top)) 18px 0;
  padding-bottom: calc(var(--mobile-bottom-nav-total) + 24px);
  box-sizing: border-box;
}

.profile-header,
.profile-summary,
.profile-shortcuts,
.profile-tabs,
.profile-form,
.profile-state {
  display: flex;
  gap: 12px;
}

.profile-header {
  align-items: center;
  justify-content: space-between;
  margin-bottom: 16px;
}

.profile-header span,
.profile-identity p,
.profile-identity span,
.profile-identity small,
.password-hint {
  color: #60746a;
}

.profile-header span {
  font-size: 13px;
  font-weight: 800;
}

.profile-header h1 {
  margin: 3px 0 0;
  font-size: 28px;
  line-height: 1.15;
}

button,
input,
textarea,
select {
  min-height: 44px;
  border: 1px solid rgba(28, 50, 38, 0.14);
  border-radius: 12px;
  background: rgba(255, 255, 255, 0.9);
  color: #122019;
  font: inherit;
  box-sizing: border-box;
}

button {
  padding: 0 14px;
  font-weight: 900;
}

.profile-card {
  border: 1px solid rgba(255, 255, 255, 0.76);
  border-radius: 22px;
  background: var(--mobile-color-surface);
  box-shadow: 0 14px 30px rgba(31, 63, 48, 0.1);
  backdrop-filter: none;
  -webkit-backdrop-filter: none;
}

.preview-banner {
  display: grid;
  gap: 4px;
  margin-top: 14px;
  padding: 14px 16px;
}

.preview-banner strong {
  color: #875600;
}

.preview-banner span {
  color: #6f6042;
  font-size: 13px;
  line-height: 1.45;
}

.profile-summary {
  align-items: center;
  padding: 18px;
}

.avatar-wrap {
  flex: 0 0 88px;
  display: grid;
  gap: 8px;
  text-align: center;
}

.avatar-wrap img {
  width: 76px;
  height: 76px;
  margin: 0 auto;
  border: 3px solid rgba(255, 255, 255, 0.92);
  border-radius: 50%;
  object-fit: cover;
  background: #dce9e2;
}

.avatar-wrap button {
  min-height: 44px;
  padding: 0 8px;
  color: var(--mobile-color-primary);
  font-size: 12px;
}

.profile-identity {
  min-width: 0;
  display: grid;
  gap: 5px;
}

.profile-identity h2,
.profile-identity p {
  margin: 0;
}

.profile-identity h2 {
  font-size: 21px;
}

.profile-identity p,
.profile-identity span,
.profile-identity small {
  overflow-wrap: anywhere;
  font-size: 13px;
}

.profile-shortcuts {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  margin: 14px 0;
}

.profile-shortcuts button {
  min-width: 0;
  padding: 0 8px;
  font-size: 13px;
}

.profile-shortcuts button.danger {
  color: #b42318;
}

.profile-tabs {
  margin-bottom: 14px;
  padding: 4px;
  border-radius: 14px;
  background: rgba(255, 255, 255, 0.48);
}

.profile-tabs button {
  flex: 1;
  border: 0;
  background: transparent;
}

.profile-tabs button.active {
  background: #fff;
  color: var(--mobile-color-primary);
  box-shadow: 0 4px 12px rgba(31, 63, 48, 0.08);
}

.profile-form {
  display: grid;
  padding: 18px;
}

.profile-form > label {
  display: grid;
  gap: 7px;
}

.profile-form label > span,
.profile-form legend {
  color: #43564c;
  font-size: 13px;
  font-weight: 900;
}

.profile-form input:not([type="radio"]),
.profile-form textarea,
.profile-form select {
  width: 100%;
  padding: 0 12px;
  font-size: 16px;
}

.profile-form textarea {
  min-height: 88px;
  padding-top: 10px;
  resize: vertical;
}

.mobile-section-title {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin: 8px 0 0;
  padding-top: 14px;
  border-top: 1px solid rgba(28, 50, 38, 0.1);
  font-size: 17px;
}

.mobile-section-title:first-child {
  margin-top: 0;
  padding-top: 0;
  border-top: 0;
}

.mobile-section-title small {
  color: #60746a;
  font-size: 12px;
  font-weight: 700;
}

.mobile-readonly-list {
  display: grid;
  gap: 8px;
  margin: 0;
}

.mobile-readonly-list > div {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  padding: 10px 12px;
  border-radius: 10px;
  background: #f4f7f5;
}

.mobile-readonly-list dt {
  color: #60746a;
  font-size: 12px;
}

.mobile-readonly-list dd {
  margin: 0;
  text-align: right;
  overflow-wrap: anywhere;
  font-size: 13px;
  font-weight: 800;
}

fieldset {
  display: flex;
  gap: 14px;
  margin: 0;
  padding: 12px;
  border: 1px solid rgba(28, 50, 38, 0.12);
  border-radius: 12px;
}

.radio-field {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  font-size: 14px;
}

.radio-field input {
  min-height: 20px;
}

.field-error,
.form-message.error,
.profile-state.error {
  color: #b42318;
}

.field-error {
  font-size: 12px;
  font-weight: 800;
}

.form-message,
.password-hint {
  margin: 0;
  font-size: 13px;
  line-height: 1.5;
  font-weight: 800;
}

.form-message.success {
  color: #087a55;
}

button.primary {
  border-color: var(--mobile-color-primary);
  background: var(--mobile-color-primary);
  color: #fff;
}

button.full {
  width: 100%;
}

.profile-state {
  align-items: center;
  justify-content: space-between;
  padding: 18px;
}

.profile-state p {
  margin: 0;
}

.mobile-profile-bottom-nav {
  position: fixed;
  z-index: 100;
  right: 0;
  bottom: 0;
  left: 0;
  display: grid;
  grid-template-columns: repeat(5, minmax(0, 1fr));
  padding: 7px 8px calc(7px + env(safe-area-inset-bottom));
  border-top: 1px solid rgba(28, 50, 38, 0.1);
  background: var(--mobile-color-surface);
  backdrop-filter: none;
  -webkit-backdrop-filter: none;
}

.mobile-profile-bottom-nav button {
  min-width: 0;
  min-height: 50px;
  display: grid;
  place-items: center;
  gap: 2px;
  padding: 3px;
  border: 0;
  background: transparent;
  color: #60746a;
  font-size: 11px;
}

.mobile-profile-bottom-nav button.active {
  color: var(--mobile-color-primary);
}

.mobile-profile-bottom-nav .svg-icon {
  width: 20px;
  height: 20px;
}

button:disabled {
  cursor: not-allowed;
  opacity: 0.58;
}

@media (max-width: 340px) {
  .mobile-profile-shell {
    padding-right: 12px;
    padding-left: 12px;
  }

  .profile-summary {
    align-items: flex-start;
    padding: 14px;
  }

  .avatar-wrap {
    flex-basis: 76px;
  }

  .avatar-wrap img {
    width: 66px;
    height: 66px;
  }
}
</style>
