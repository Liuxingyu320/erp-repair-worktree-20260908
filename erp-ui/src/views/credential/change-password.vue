<template>
  <main class="credential-change-page">
    <section class="credential-change-card" aria-labelledby="credential-change-title">
      <div class="credential-change-icon" aria-hidden="true"><i class="el-icon-lock" /></div>
      <h1 id="credential-change-title">必须先修改密码</h1>
      <p v-if="!isExpired" class="credential-change-intro">
        当前账号使用临时密码或已被标记为必须改密。完成修改前，系统不会开放其他业务功能。
      </p>
      <el-alert
        v-if="isExpired"
        title="临时密码已失效，请联系管理员重新生成。"
        type="error"
        :closable="false"
        show-icon
      />
      <el-alert
        v-else
        :title="expiryMessage"
        type="warning"
        :closable="false"
        show-icon
      />

      <el-form v-if="!isExpired" ref="form" :model="form" :rules="formRules" label-position="top" @submit.native.prevent>
        <el-form-item label="当前密码" prop="oldPassword">
          <el-input v-model="form.oldPassword" type="password" show-password autocomplete="current-password" />
        </el-form-item>
        <el-form-item label="新密码" prop="newPassword" :rules="infoPwdValidator">
          <el-input v-model="form.newPassword" type="password" show-password autocomplete="new-password" />
        </el-form-item>
        <el-form-item label="确认新密码" prop="confirmPassword">
          <el-input v-model="form.confirmPassword" type="password" show-password autocomplete="new-password" @keyup.enter.native="submit" />
        </el-form-item>
        <el-button class="credential-primary-action" type="primary" :loading="submitting" @click="submit">修改密码并继续</el-button>
      </el-form>

      <el-button class="credential-logout-action" type="text" @click="logout">退出登录</el-button>
    </section>
  </main>
</template>

<script>
import { updateUserPwd } from '@/api/system/user'
import passwordRule from '@/utils/passwordRule'

export default {
  name: 'CredentialChangePassword',
  mixins: [passwordRule],
  data() {
    return {
      submitting: false,
      form: { oldPassword: '', newPassword: '', confirmPassword: '' }
    }
  },
  computed: {
    isExpired() {
      return this.$store.getters.credentialBusinessCode === 'TEMPORARY_CREDENTIAL_EXPIRED'
    },
    expiryMessage() {
      const expiresAt = this.$store.getters.temporaryPasswordExpiresAt
      return expiresAt ? `临时密码失效时间：${expiresAt}` : '请设置仅由您本人掌握的新密码。'
    },
    formRules() {
      return {
        oldPassword: [{ required: true, message: '当前密码不能为空', trigger: 'blur' }],
        confirmPassword: [
          { required: true, message: '请再次输入新密码', trigger: 'blur' },
          {
            validator: (rule, value, callback) => {
              value === this.form.newPassword ? callback() : callback(new Error('两次输入的密码不一致'))
            },
            trigger: 'blur'
          }
        ]
      }
    }
  },
  beforeDestroy() {
    this.clearPasswords()
  },
  methods: {
    submit() {
      if (this.submitting) return
      this.$refs.form.validate(valid => {
        if (!valid) return
        this.submitting = true
        updateUserPwd(this.form.oldPassword, this.form.newPassword).then(() => {
          this.clearPasswords()
          return this.$store.dispatch('CredentialChangeCompleted')
        }).then(() => this.$store.dispatch('GetInfo')).then(info => {
          if (info && info.profileCompletionRequired === true) {
            this.$modal.msgSuccess('密码修改成功，请继续补全入职资料')
            return this.$router.replace({
              path: '/complete-profile',
              query: { redirect: '/select-shop' }
            })
          }
          this.$modal.msgSuccess('密码修改成功')
          return this.$router.replace('/select-shop')
        }).finally(() => {
          this.submitting = false
        })
      })
    },
    clearPasswords() {
      this.form.oldPassword = ''
      this.form.newPassword = ''
      this.form.confirmPassword = ''
    },
    logout() {
      this.clearPasswords()
      this.$store.dispatch('LogOut').then(() => this.$router.replace('/login')).catch(() => {
        // Cookie 会话只有在服务端确认撤销后才离开当前页；失败提示由 Store 统一提供。
      })
    }
  }
}
</script>

<style lang="scss" scoped>
.credential-change-page {
  min-height: var(--mobile-viewport-height, 100dvh);
  display: grid;
  place-items: center;
  padding:
    calc(var(--mobile-safe-top, env(safe-area-inset-top, 0px)) + 16px)
    16px
    calc(var(--mobile-safe-bottom, env(safe-area-inset-bottom, 0px)) + 16px);
  background: var(--mobile-color-page, #f4f5f2);
}

.credential-change-card {
  width: min(100%, 480px);
  padding: 28px 20px 20px;
  border: 1px solid var(--mobile-color-line, #dde2de);
  border-radius: 16px;
  background: var(--mobile-color-surface, #fff);
  box-shadow: none;
}

.credential-change-icon {
  width: 48px;
  height: 48px;
  display: grid;
  place-items: center;
  margin: 0 auto 16px;
  border-radius: 12px;
  color: #fff;
  background: var(--mobile-color-primary, #0b6b53);
  font-size: 24px;
}

h1 {
  margin: 0;
  text-align: center;
  color: var(--mobile-color-ink, #17211d);
  font-size: 22px;
  font-weight: 700;
}
.credential-change-intro {
  color: var(--mobile-color-muted, #66736d);
  line-height: 1.6;
  font-size: 14px;
}
.el-alert { margin: 16px 0; }
.credential-primary-action {
  width: 100%;
  min-height: 48px !important;
  border-color: var(--mobile-color-primary, #0b6b53) !important;
  background: var(--mobile-color-primary, #0b6b53) !important;
  font-weight: 700;
}
.credential-logout-action {
  width: 100%;
  min-height: 44px;
  margin-top: 8px;
  color: var(--mobile-color-muted, #66736d);
}

::v-deep .el-input__inner {
  min-height: 48px;
  font-size: 16px;
}

@media (max-width: 520px) {
  .credential-change-page { padding: 12px; }
  .credential-change-card { padding: 22px 16px 16px; }
}
</style>
