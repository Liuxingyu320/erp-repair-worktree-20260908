<template>
  <div class="password-change-page">
    <el-card class="password-change-card" shadow="never">
      <div class="password-change-heading">
        <div class="security-icon" aria-hidden="true"><i class="el-icon-lock" /></div>
        <div>
          <h1>首次登录，请先修改密码</h1>
          <p>当前密码是一次性凭据。修改成功后才能进入系统其他功能。</p>
        </div>
      </div>

      <el-alert
        title="为保护账号安全，新密码请勿与临时密码相同，也不要转发给他人。"
        type="warning"
        :closable="false"
        show-icon
      />

      <el-form ref="form" :model="form" :rules="rules" label-position="top" @submit.native.prevent>
        <el-form-item label="当前临时密码" prop="oldPassword">
          <el-input
            v-model="form.oldPassword"
            type="password"
            autocomplete="current-password"
            placeholder="请输入当前临时密码"
            show-password
          />
        </el-form-item>
        <el-form-item label="新密码" prop="newPassword" :rules="infoPwdValidator">
          <el-input
            v-model="form.newPassword"
            type="password"
            autocomplete="new-password"
            placeholder="请输入 8 至 20 位新密码"
            maxlength="20"
            show-password
          />
        </el-form-item>
        <el-form-item label="确认新密码" prop="confirmPassword">
          <el-input
            v-model="form.confirmPassword"
            type="password"
            autocomplete="new-password"
            placeholder="请再次输入新密码"
            maxlength="20"
            show-password
            @keyup.enter.native="submit"
          />
        </el-form-item>
        <el-button class="submit-button" type="primary" :loading="submitting" @click="submit">
          修改密码并继续
        </el-button>
        <el-button class="logout-button" type="text" :disabled="submitting" @click="logout">
          退出当前账号
        </el-button>
      </el-form>
    </el-card>
  </div>
</template>

<script>
import passwordRule from '@/utils/passwordRule'
import { updateUserPwd } from '@/api/system/user'

export default {
  name: 'RequiredPasswordChange',
  mixins: [passwordRule],
  data() {
    return {
      submitting: false,
      form: {
        oldPassword: '',
        newPassword: '',
        confirmPassword: ''
      }
    }
  },
  computed: {
    rules() {
      return {
        oldPassword: [
          { required: true, message: '当前临时密码不能为空', trigger: 'blur' }
        ],
        confirmPassword: [
          { required: true, message: '请再次输入新密码', trigger: 'blur' },
          {
            validator: (rule, value, callback) => {
              if (value !== this.form.newPassword) {
                callback(new Error('两次输入的新密码不一致'))
              } else {
                callback()
              }
            },
            trigger: ['blur', 'change']
          }
        ]
      }
    }
  },
  mounted() {
    this.$nextTick(() => {
      const input = this.$el.querySelector('input')
      if (input) input.focus()
    })
  },
  methods: {
    submit() {
      this.$refs.form.validate(valid => {
        if (!valid || this.submitting) return
        this.submitting = true
        updateUserPwd(this.form.oldPassword, this.form.newPassword)
          .then(() => this.$store.dispatch('GetInfo'))
          .then(info => {
            if (info && info.profileCompletionRequired === true) {
              this.$message.success('密码修改成功，请继续补全入职资料')
              this.$router.replace({
                path: '/complete-profile',
                query: { redirect: '/select-shop' }
              })
              return
            }
            this.$message.success('密码修改成功，请选择工作组织')
            this.$router.replace('/select-shop')
          })
          .finally(() => {
            this.submitting = false
          })
      })
    },
    logout() {
      this.$store.dispatch('LogOut').then(() => {
        this.$router.replace('/login')
      })
    }
  }
}
</script>

<style lang="scss" scoped>
.password-change-page {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 24px;
  background: linear-gradient(145deg, #f4f7fb 0%, #e8eff8 100%);
}

.password-change-card {
  width: 100%;
  max-width: 520px;
  border: 1px solid #dce5f0;
  border-radius: 16px;
}

.password-change-heading {
  display: flex;
  gap: 16px;
  margin-bottom: 20px;

  h1 { margin: 0 0 8px; color: #172033; font-size: 24px; }
  p { margin: 0; color: #5f6b7a; line-height: 1.6; }
}

.security-icon {
  flex: 0 0 48px;
  height: 48px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 14px;
  color: #fff;
  background: #2f6fec;
  font-size: 22px;
}

.el-alert { margin-bottom: 20px; }
.submit-button { width: 100%; min-height: 44px; margin-top: 4px; }
.logout-button { width: 100%; min-height: 44px; margin: 8px 0 0; }

@media (max-width: 600px) {
  .password-change-page { align-items: flex-start; padding: 16px; }
  .password-change-card { margin-top: 6vh; }
  .password-change-heading h1 { font-size: 21px; }
}
</style>
