<template>
  <div class="register mobile-system-page">
    <el-form ref="registerForm" :model="registerForm" :rules="registerRules" class="register-form">
      <header class="register-head">
        <span class="register-mark" aria-hidden="true"><i class="el-icon-user-solid" /></span>
        <span class="register-eyebrow">创建新账号</span>
        <h1 class="title">{{ title }}</h1>
        <p>填写账号和密码后即可完成注册。</p>
      </header>
      <el-form-item prop="username">
        <el-input
          v-model="registerForm.username"
          type="text"
          autocomplete="username"
          label="账号"
          autocapitalize="off"
          autocorrect="off"
          inputmode="text"
          :spellcheck="false"
          placeholder="账号"
        >
          <svg-icon slot="prefix" icon-class="user" class="el-input__icon input-icon" />
        </el-input>
      </el-form-item>
      <el-form-item prop="password" :rules="registerPwdValidator">
        <el-input
          v-model="registerForm.password"
          type="password"
          autocomplete="new-password"
          label="密码"
          autocapitalize="off"
          autocorrect="off"
          inputmode="text"
          :spellcheck="false"
          placeholder="密码"
          @keyup.enter.native="handleRegister"
        >
          <svg-icon slot="prefix" icon-class="password" class="el-input__icon input-icon" />
        </el-input>
      </el-form-item>
      <el-form-item prop="confirmPassword">
        <el-input
          v-model="registerForm.confirmPassword"
          type="password"
          autocomplete="new-password"
          label="确认密码"
          autocapitalize="off"
          autocorrect="off"
          inputmode="text"
          :spellcheck="false"
          placeholder="确认密码"
          @keyup.enter.native="handleRegister"
        >
          <svg-icon slot="prefix" icon-class="password" class="el-input__icon input-icon" />
        </el-input>
      </el-form-item>
      <el-form-item prop="code" v-if="captchaEnabled">
        <div class="register-captcha-row">
          <el-input
            v-model="registerForm.code"
            autocomplete="one-time-code"
            label="验证码"
            autocapitalize="off"
            autocorrect="off"
            inputmode="numeric"
            :spellcheck="false"
            placeholder="验证码"
            @keyup.enter.native="handleRegister"
          >
            <svg-icon slot="prefix" icon-class="validCode" class="el-input__icon input-icon" />
          </el-input>
          <button type="button" aria-label="刷新验证码" @click="getCode" class="register-code">
            <img v-if="codeUrl" :src="codeUrl" alt="验证码，点击可刷新" class="register-code-img"/>
            <span v-else>验证码</span>
          </button>
        </div>
      </el-form-item>
      <el-form-item class="register-actions">
        <el-button
          :loading="loading"
          size="medium"
          type="primary"
          class="register-submit"
          @click.native.prevent="handleRegister"
        >
          <span v-if="!loading">注册</span>
          <span v-else>注册中...</span>
        </el-button>
        <div class="register-login-row">
          <router-link class="link-type" :to="'/login'">使用已有账户登录</router-link>
        </div>
      </el-form-item>
    </el-form>
    <!--  底部  -->
    <div class="el-register-footer">
      <span>{{ footerContent }}</span>
    </div>
  </div>
</template>

<script>
import { getCodeImg, getPasswordPolicy, register } from "@/api/login"
import passwordRule from "@/utils/passwordRule"
import defaultSettings from '@/settings'

export default {
  mixins: [passwordRule],
  data() {
    return {
      title: process.env.VUE_APP_TITLE,
      footerContent: defaultSettings.footerContent,
      codeUrl: "",
      registerForm: {
        username: "",
        password: "",
        confirmPassword: "",
        code: "",
        uuid: ""
      },
      loading: false,
      captchaEnabled: true
    }
  },
  computed: {
    registerRules() {
      return {
        username: [
          { required: true, trigger: "blur", message: "请输入您的账号" },
          { min: 2, max: 20, message: '用户账号长度必须介于 2 和 20 之间', trigger: 'blur' }
        ],
        confirmPassword: [
          { required: true, message: "请再次输入您的密码", trigger: "blur" },
          {
            validator: (rule, value, callback) => {
              if (this.registerForm.password !== value) {
                callback(new Error("两次输入的密码不一致"))
              } else {
                callback()
              }
            }, trigger: "blur"
          }
        ],
        code: [{ required: true, trigger: "change", message: "请输入验证码" }]
      }
    }
  },
  created() {
    this.getPasswordPolicy()
    this.getCode()
  },
  methods: {
    getPasswordPolicy() {
      getPasswordPolicy().then(response => {
        this.pwdChrType = response.data || '0'
      }).catch(() => {})
    },
    getCode() {
      getCodeImg().then(res => {
        const response = res || {}
        const image = typeof response.img === "string" ? response.img.trim() : ""
        this.captchaEnabled = response.captchaEnabled === undefined ? true : response.captchaEnabled
        this.codeUrl = this.captchaEnabled && image ? "data:image/gif;base64," + image : ""
        this.registerForm.uuid = this.captchaEnabled && image ? response.uuid || "" : ""
      }).catch(() => {
        this.codeUrl = ""
        this.registerForm.uuid = ""
      })
    },
    handleRegister() {
      this.$refs.registerForm.validate(valid => {
        if (valid) {
          this.loading = true
          register(this.registerForm).then(() => {
            const username = this.registerForm.username
            const h = this.$createElement
            this.$alert(h('span', [
              h('span', '恭喜你，您的账号 '),
              h('strong', { style: { color: '#f56c6c' } }, username),
              h('span', ' 注册成功！')
            ]), '系统提示', {
              type: 'success'
            }).then(() => {
              this.$router.push("/login")
            }).catch(() => {})
          }).catch(() => {
            this.loading = false
            if (this.captchaEnabled) {
              this.getCode()
            }
          })
        }
      })
    }
  }
}
</script>

<style rel="stylesheet/scss" lang="scss" scoped>
.register {
  position: relative;
  display: flex;
  flex-direction: column;
  justify-content: flex-start;
  align-items: center;
  min-height: 100vh;
  min-height: 100dvh;
  min-height: var(--mobile-viewport-height, 100dvh);
  box-sizing: border-box;
  padding:
    calc(var(--mobile-safe-top, env(safe-area-inset-top, 0px)) + 24px)
    16px
    calc(var(--mobile-safe-bottom, env(safe-area-inset-bottom, 0px)) + 8px);
  overflow-x: hidden;
  background: var(--mobile-color-page, #f4f5f2);
  /* asset path retained for release contract: mobile-inventory-tea-room-bg.jpg */
}

.register-head {
  text-align: center;
  margin-bottom: 24px;

  p {
    margin: 8px 0 0;
    color: var(--mobile-color-muted);
    font-size: 14px;
    line-height: 1.55;
  }
}

.register-mark {
  width: 52px;
  height: 52px;
  display: grid;
  place-items: center;
  margin: 0 auto 12px;
  border-radius: 17px;
  color: #fff;
  background: var(--mobile-color-primary);
  font-size: 23px;
}

.register-eyebrow {
  color: var(--mobile-color-primary);
  font-size: 12px;
  font-weight: 800;
  letter-spacing: 0.12em;
}

.title {
  margin: 5px 0 0;
  color: var(--mobile-color-ink);
  font-size: 26px;
  line-height: 1.25;
  overflow-wrap: anywhere;
}

.register-form {
  flex: 0 0 auto;
  border: 1px solid var(--mobile-color-line, #dde2de);
  border-radius: 16px;
  background: var(--mobile-color-surface, #fff);
  width: min(400px, 100%);
  box-sizing: border-box;
  padding: 24px 16px 16px;
  box-shadow: none;
  .el-input {
    height: 48px;
  }
  ::v-deep .el-input__inner {
    height: 44px;
    line-height: 44px;
    min-height: 48px;
    border-radius: 12px;
    border-color: var(--mobile-color-line-strong, #cbd3ce);
    color: var(--mobile-color-ink);
    font-size: 16px;
  }
  ::v-deep .el-input__inner:focus {
    border-color: var(--mobile-color-primary);
    box-shadow: var(--mobile-focus-ring);
  }
  .input-icon {
    height: 44px;
    width: 14px;
    margin-left: 2px;
  }

  ::v-deep .el-button {
    min-height: 44px;
    border-radius: 12px;
  }
}

.register-actions {
  width: 100%;
  margin-bottom: 0;
}

.register-submit {
  width: 100%;
  min-height: var(--mobile-primary-control-height) !important;

  &.el-button--primary {
    border-color: var(--mobile-color-primary);
    background: var(--mobile-color-primary);
    font-size: 16px;
    font-weight: 800;
  }
}

.register-login-row {
  display: flex;
  justify-content: center;
  margin-top: 8px;

  a {
    min-height: 44px;
    display: inline-flex;
    align-items: center;
    color: var(--mobile-color-primary);
    font-size: 14px;
    font-weight: 700;
  }
}

.register-captcha-row {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 112px;
  align-items: center;
  gap: 10px;
}
.register-code {
  width: 112px;
  height: 44px;
  padding: 0;
  overflow: hidden;
  border: 1px solid var(--mobile-color-line);
  border-radius: 12px;
  background: #fff;
  color: var(--mobile-color-muted);
  cursor: pointer;
  display: grid;
  place-items: center;
}
.el-register-footer {
  flex: 0 0 auto;
  min-height: 40px;
  line-height: 1.4;
  position: static;
  margin-top: auto;
  padding-top: 12px;
  width: 100%;
  box-sizing: border-box;
  text-align: center;
  color: #4d6158;
  font-family: Arial;
  font-size: 12px;
  letter-spacing: 0;
}
.register-code-img {
  width: 100%;
  height: 44px;
  display: block;
}
@supports (height: 100dvh) {
  .register {
    min-height: 100dvh;
  }
}
@media (max-width: 390px) {
  .register {
    align-items: stretch;
    padding-top: max(16px, var(--mobile-safe-top, env(safe-area-inset-top, 0px)));
    padding-left: 12px;
    padding-right: 12px;
  }

  .register-form {
    padding: 22px 18px 14px;
    border-radius: 20px;
  }
}
@media (max-width: 320px) {
  .register-captcha-row {
    grid-template-columns: minmax(0, 1fr) 96px;
    gap: 8px;
  }

  .register-code {
    width: 96px;
  }
}
</style>
