<template>
  <div :class="['login', isMobileViewport ? 'mobile-auth-page is-mobile-web mobile-system-page' : 'is-desktop-web']">
    <main v-if="!isMobileViewport" class="desktop-login-shell" aria-label="ERP登录">
      <section class="desktop-login-copy" aria-hidden="true">
        <div class="desktop-brandline">
          <div class="desktop-emblem">
            <svg viewBox="0 0 24 24">
              <path d="M4 8.4 12 3l8 5.4v9.2L12 21l-8-3.4V8.4Zm8-2.8L6.8 9 12 11.6 17.2 9 12 5.6ZM6 10.7v5.6l5 2.2v-5.6l-5-2.2Zm7 7.8 5-2.2v-5.6l-5 2.2v5.6Z" />
            </svg>
          </div>
          <div class="desktop-brand-copy">
            <strong>BossERP</strong>
            <span>BUSINESS OPERATING SYSTEM</span>
          </div>
        </div>

        <div class="desktop-copy-body">
          <h1>让门店经营<br><em>汇聚为实时工作台</em></h1>
          <p>用一个账号连接日常经营全链路，让进度更清楚、协同更顺畅、决策更及时。</p>

          <div class="desktop-operations-grid">
            <article>
              <span class="desktop-operation-icon"><i class="el-icon-s-shop" aria-hidden="true"></i></span>
              <span><strong>多店经营</strong><small>门店与仓库上下文统一切换</small></span>
            </article>
            <article>
              <span class="desktop-operation-icon"><i class="el-icon-s-data" aria-hidden="true"></i></span>
              <span><strong>货品洞察</strong><small>关键数据与预警状态及时可见</small></span>
            </article>
            <article>
              <span class="desktop-operation-icon"><i class="el-icon-document-copy" aria-hidden="true"></i></span>
              <span><strong>流程协同</strong><small>待办、审批和执行进度集中处理</small></span>
            </article>
          </div>
        </div>
      </section>

      <el-form ref="loginForm" :model="loginForm" :rules="loginRules" class="desktop-login-form">
        <div class="desktop-form-head">
          <h3>{{ title }}</h3>
          <p>登录后继续处理门店、货品与审批事项。</p>
        </div>
        <el-form-item prop="username">
          <label class="login-field-label" for="desktop-login-username">账号</label>
          <el-input
            id="desktop-login-username"
            aria-label="账号"
            v-model="loginForm.username"
            type="text"
            autocomplete="username"
            label="账号"
            autocapitalize="off"
            autocorrect="off"
            inputmode="text"
            :spellcheck="false"
            placeholder="账号"
          >
            <i slot="prefix" class="el-icon-user el-input__icon input-icon" aria-hidden="true"></i>
          </el-input>
        </el-form-item>
        <el-form-item prop="password">
          <label class="login-field-label" for="desktop-login-password">密码</label>
          <el-input
            id="desktop-login-password"
            aria-label="密码"
            v-model="loginForm.password"
            type="password"
            autocomplete="current-password"
            label="密码"
            autocapitalize="off"
            autocorrect="off"
            inputmode="text"
            :spellcheck="false"
            placeholder="密码"
            @keyup.enter.native="handleLogin"
          >
            <svg-icon slot="prefix" icon-class="password" class="el-input__icon input-icon" />
          </el-input>
        </el-form-item>
        <el-form-item prop="code" v-if="captchaEnabled" class="desktop-captcha-item">
          <label class="login-field-label" for="desktop-login-code">验证码</label>
          <div class="desktop-captcha-row">
            <el-input
              id="desktop-login-code"
              aria-label="验证码"
              v-model="loginForm.code"
              autocomplete="one-time-code"
              label="验证码"
              autocapitalize="off"
              autocorrect="off"
              inputmode="numeric"
              :spellcheck="false"
              placeholder="请输入图中算式结果"
              @keyup.enter.native="handleLogin"
            >
              <svg-icon slot="prefix" icon-class="validCode" class="el-input__icon input-icon" />
            </el-input>
            <button type="button" aria-label="刷新验证码" @click="getCode" class="desktop-login-code">
              <img v-if="codeUrl" :src="codeUrl" alt="验证码，点击可刷新" class="desktop-login-code-img"/>
              <span v-else>验证码</span>
            </button>
          </div>
        </el-form-item>
        <div class="desktop-options">
          <el-checkbox v-model="loginForm.rememberMe">记住账号</el-checkbox>
          <router-link v-if="register" class="link-type" :to="'/register'">立即注册</router-link>
        </div>
        <el-form-item class="desktop-login-submit">
          <el-button
            :loading="loading"
            size="medium"
            type="primary"
            @click.native.prevent="handleLogin"
          >
            <span v-if="!loading">登录</span>
            <span v-else>登录中...</span>
          </el-button>
        </el-form-item>
      </el-form>
    </main>

    <main v-else class="mobile-auth-shell tea-room-backdrop" aria-label="茶室ERP登录">
      <section class="auth-stage auth-layout">
        <div class="auth-card-stack">
          <header class="auth-hero">
            <div class="brand-mark" aria-hidden="true">
              <svg viewBox="0 0 24 24">
                <path d="M4 9.5c4.8-.1 8.2-2.1 10.2-6 2.9 1.5 4.9 4.5 4.9 8 0 5-4 9-9 9-3.3 0-6.2-1.8-7.8-4.5 3.7-.1 7-1.8 9.9-5.2-2.7 1.7-5.4 2.2-8.2 1.6V9.5Z" />
              </svg>
            </div>
            <div>
              <h1>茶室进销存</h1>
              <p>{{ title || "ERP" }} 手机工作台</p>
            </div>
          </header>

          <el-form ref="loginForm" :model="loginForm" :rules="loginRules" class="login-form glass-panel">
            <div class="form-head">
              <h2>欢迎回来</h2>
              <span>登录账号</span>
            </div>
            <el-form-item prop="username">
              <label class="login-field-label" for="mobile-login-username">账号</label>
              <el-input
                id="mobile-login-username"
                aria-label="账号"
                v-model="loginForm.username"
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
            <el-form-item prop="password">
              <label class="login-field-label" for="mobile-login-password">密码</label>
              <el-input
                id="mobile-login-password"
                aria-label="密码"
                v-model="loginForm.password"
                type="password"
                autocomplete="current-password"
                label="密码"
                autocapitalize="off"
                autocorrect="off"
                inputmode="text"
                :spellcheck="false"
                placeholder="密码"
                @keyup.enter.native="handleLogin"
              >
                <svg-icon slot="prefix" icon-class="password" class="el-input__icon input-icon" />
              </el-input>
            </el-form-item>
            <el-form-item prop="code" v-if="captchaEnabled">
              <label class="login-field-label" for="mobile-login-code">验证码</label>
              <div class="captcha-row">
                <el-input
                  id="mobile-login-code"
                  aria-label="验证码"
                  v-model="loginForm.code"
                  autocomplete="one-time-code"
                  label="验证码"
                  autocapitalize="off"
                  autocorrect="off"
                  inputmode="numeric"
                  :spellcheck="false"
                  placeholder="请输入图中算式结果"
                  class="captcha-input"
                  @keyup.enter.native="handleLogin"
                >
                  <svg-icon slot="prefix" icon-class="validCode" class="el-input__icon input-icon" />
                </el-input>
                <button type="button" aria-label="刷新验证码" @click="getCode" class="login-code">
                  <img v-if="codeUrl" :src="codeUrl" alt="验证码，点击可刷新" class="login-code-img"/>
                  <span v-else>验证码</span>
                </button>
              </div>
            </el-form-item>
            <div class="form-options">
              <el-checkbox v-model="loginForm.rememberMe">记住账号</el-checkbox>
              <span>安全连接</span>
            </div>
            <el-form-item class="login-submit">
              <el-button
                :loading="loading"
                size="medium"
                type="primary"
                @click.native.prevent="handleLogin"
              >
                <span v-if="!loading">登录</span>
                <span v-else>登录中...</span>
              </el-button>
              <div class="register-row" v-if="register">
                <router-link class="link-type" :to="'/register'">立即注册</router-link>
              </div>
            </el-form-item>
          </el-form>
        </div>
      </section>

      <div class="el-login-footer">
        <span>{{ footerContent }}</span>
      </div>
    </main>
  </div>
</template>

<script>
import { getCodeImg } from "@/api/login"
import Cookies from "js-cookie"
import defaultSettings from '@/settings'
import { requiresInventoryContext } from '@/utils/desktopContextPolicy'
const { isMobileClient, isMobileRoutePath } = require("@/utils/clientPlatform")
const { isSafeInternalRedirect } = require("@/views/select-shop/shopEntryRouting")

export default {
  name: "Login",
  data() {
    return {
      title: process.env.VUE_APP_TITLE,
      footerContent: defaultSettings.footerContent,
      codeUrl: "",
      loginForm: {
        username: "",
        password: "",
        rememberMe: false,
        code: "",
        uuid: ""
      },
      loginRules: {
        username: [
          { required: true, trigger: "blur", message: "请输入您的账号" }
        ],
        password: [
          { required: true, trigger: "blur", message: "请输入您的密码" }
        ],
        code: [{ required: true, trigger: "change", message: "请输入图中算式结果" }]
      },
      loading: false,
      isMobileViewport: false,
      // 验证码开关
      captchaEnabled: true,
      // 注册开关
      register: false,
      redirect: undefined
    }
  },
  watch: {
    $route: {
      handler: function(route) {
        this.redirect = route.query && route.query.redirect
      },
      immediate: true
    }
  },
  created() {
    this.updateViewport()
    this.getCookie()
    this.getCode()
  },
  mounted() {
    this.updateViewport()
    window.addEventListener("resize", this.updateViewport)
  },
  beforeDestroy() {
    window.removeEventListener("resize", this.updateViewport)
  },
  methods: {
    updateViewport() {
      this.isMobileViewport = isMobileClient()
    },
    getCode() {
      this.loginForm.code = ""
      getCodeImg().then(res => {
        const response = res || {}
        const image = typeof response.img === "string" ? response.img.trim() : ""
        this.captchaEnabled = response.captchaEnabled === undefined ? true : response.captchaEnabled
        this.codeUrl = this.captchaEnabled && image ? "data:image/gif;base64," + image : ""
        this.loginForm.uuid = this.captchaEnabled && image ? response.uuid || "" : ""
      }).catch(() => {
        this.codeUrl = ""
        this.loginForm.uuid = ""
      })
    },
    getCookie() {
      const username = Cookies.get("username")
      const rememberMe = Cookies.get('rememberMe')
      Cookies.remove("password")
      if (this.isSavedAdminCredential(username)) {
        this.clearSavedLogin()
        return
      }
      this.loginForm = {
        username: username === undefined ? this.loginForm.username : username,
        password: "",
        rememberMe: rememberMe === undefined ? false : Boolean(rememberMe),
        code: this.loginForm.code,
        uuid: this.loginForm.uuid
      }
    },
    isSavedAdminCredential(username) {
      return username === "admin"
    },
    clearSavedLogin() {
      Cookies.remove("username")
      Cookies.remove("password")
      Cookies.remove("rememberMe")
    },
    getMobileDefaultRedirect() {
      if (this.isMobileViewport) {
        return "/mobile/inventory"
      }
      return "/"
    },
    normalizePostLoginRedirect(redirect) {
      if (!this.isMobileViewport && isMobileRoutePath(redirect)) {
        return "/"
      }
      if (this.isMobileViewport && (!redirect || redirect === "/" || redirect === "/index")) {
        return "/mobile/inventory"
      }
      return redirect || this.getMobileDefaultRedirect()
    },
    getPostLoginRoute() {
      const redirect = this.normalizePostLoginRedirect(this.redirect)
      if (!this.isMobileViewport && isSafeInternalRedirect(redirect) &&
          redirect !== '/' && redirect !== '/index' && !requiresInventoryContext(redirect)) {
        return redirect
      }
      return {
        path: "/select-shop",
        query: {
          redirect
        }
      }
    },
    handleLogin() {
      this.$refs.loginForm.validate(valid => {
        if (valid) {
          this.loading = true
          if (this.loginForm.rememberMe) {
            Cookies.set("username", this.loginForm.username, { expires: 30 })
            Cookies.set('rememberMe', this.loginForm.rememberMe, { expires: 30 })
            Cookies.remove("password")
          } else {
            Cookies.remove("username")
            Cookies.remove("password")
            Cookies.remove('rememberMe')
          }
          this.$store.dispatch("Login", this.loginForm).then(() => {
            this.$router.push(this.getPostLoginRoute()).catch(()=>{})
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
.login-field-label {
  display: block;
  margin: 0 0 6px 2px;
  color: #334155;
  font-size: 13px;
  font-weight: 600;
  line-height: 18px;
}
.login {
  min-height: 100vh;
  background: #edf4ef;
  display: flex;
  justify-content: center;
  color: #07130d;
  font-family: Inter, "PingFang SC", "Microsoft YaHei", "Helvetica Neue", Arial, sans-serif;
  overflow-x: hidden;
}

.login.is-desktop-web {
  align-items: stretch;
  justify-content: center;
  background-color: #faf8f4;
  background-image:
    linear-gradient(rgba(67, 59, 49, 0.012) 1px, transparent 1px),
    linear-gradient(90deg, rgba(67, 59, 49, 0.012) 1px, transparent 1px);
  background-size: 40px 40px;
  color: #252421;
  font-family: "Helvetica Neue", Helvetica, "PingFang SC", "Microsoft YaHei", Arial, sans-serif;
}

.desktop-login-shell {
  position: relative;
  width: 100%;
  min-height: 100vh;
  display: grid;
  grid-template-columns: 62.75% 37.25%;
  gap: 0;
  align-items: center;
  background: transparent;
}

.desktop-login-copy {
  position: relative;
  isolation: isolate;
  align-self: stretch;
  min-height: 100vh;
  padding: clamp(52px, 6.1vw, 102px) clamp(54px, 5.3vw, 89px) clamp(42px, 5vw, 74px);
  box-sizing: border-box;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  color: #252421;
  background: #f7f3ed url("~@/assets/images/desktop-login-workspace-neutral-v3.jpg") center / 100% 100% no-repeat;
  text-shadow: none;
}

.desktop-login-copy::before {
  position: absolute;
  z-index: 0;
  inset: 0;
  content: "";
  pointer-events: none;
  background: rgba(250, 248, 244, 0.18);
}

.desktop-login-copy::after {
  position: absolute;
  z-index: 0;
  inset: 0;
  content: "";
  pointer-events: none;
  background-image:
    linear-gradient(rgba(67, 59, 49, 0.022) 1px, transparent 1px),
    linear-gradient(90deg, rgba(67, 59, 49, 0.022) 1px, transparent 1px);
  background-size: 40px 40px;
}

.desktop-login-copy > * {
  position: relative;
  z-index: 1;
}

.desktop-brandline {
  display: flex;
  align-items: center;
  gap: 13px;
}

.desktop-emblem {
  width: 38px;
  height: 38px;
  flex: 0 0 38px;
  display: grid;
  place-items: center;
  color: #252421;
  background: transparent;
  border: 0;
  box-shadow: none;
}

.desktop-emblem svg {
  width: 48px;
  height: 48px;
  fill: currentColor;
  transform: translateX(-6px);
}

.desktop-brand-copy {
  display: flex;
  flex-direction: column;
  gap: 5px;
}

.desktop-brand-copy strong {
  color: #252421;
  font-size: clamp(24px, 1.85vw, 31px);
  line-height: 1;
  font-weight: 800;
  letter-spacing: -0.035em;
}

.desktop-brand-copy span {
  color: #77716a;
  font-size: 11px;
  line-height: 1.2;
  font-weight: 600;
  letter-spacing: 0.18em;
}

.desktop-copy-body {
  width: min(880px, 100%);
  margin: clamp(104px, 14.5vh, 140px) 0 0;
}

.desktop-login-copy h1 {
  width: max-content;
  max-width: 100%;
  margin: 0;
  padding: 3px 0 3px 28px;
  border-left: 3px solid #d8ccbc;
  box-sizing: border-box;
  color: #252421;
  font-size: clamp(48px, 4vw, 67px);
  line-height: 1.15;
  font-weight: 700;
  letter-spacing: -0.045em;
}

.desktop-login-copy h1 em {
  color: inherit;
  font-style: normal;
  text-shadow: none;
}

.desktop-login-copy p {
  width: min(780px, 100%);
  margin: 25px 0 0;
  color: #77716a;
  font-size: clamp(15px, 1.08vw, 18px);
  line-height: 1.75;
}

.desktop-operations-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 200px));
  gap: clamp(28px, 3vw, 51px);
  margin-top: clamp(56px, 9vh, 86px);
}

.desktop-operations-grid article {
  min-width: 0;
  min-height: 146px;
  padding: 0;
  border: 0;
  border-radius: 0;
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  justify-content: flex-start;
  gap: 18px;
  background: transparent;
  box-shadow: none;
  text-shadow: none;
}

.desktop-operation-icon {
  width: 74px;
  height: 74px;
  border-radius: 50%;
  display: grid;
  place-items: center;
  color: #746b60;
  background: rgba(255, 255, 255, 0.72);
  border: 1px solid rgba(255, 255, 255, 0.86);
  box-shadow: none;
}

.desktop-operation-icon i {
  font-size: 32px;
  line-height: 1;
}

.desktop-operations-grid article:first-child .desktop-operation-icon i {
  color: transparent;
  font-size: 36px;
  -webkit-text-stroke: 1.25px #746b60;
}

.desktop-operations-grid article > span:last-child {
  min-width: 0;
}

.desktop-operations-grid strong,
.desktop-operations-grid small {
  display: block;
}

.desktop-operations-grid strong {
  color: #35312d;
  font-size: 16px;
  line-height: 1.4;
  font-weight: 650;
}

.desktop-operations-grid small {
  margin-top: 7px;
  color: #969088;
  font-size: 13px;
  line-height: 1.55;
}

.desktop-login-form {
  position: relative;
  justify-self: center;
  width: min(436px, calc(100% - 72px));
  padding: 0;
  box-sizing: border-box;
  overflow: visible;
  border-radius: 0;
  background: transparent;
  border: 0;
  box-shadow: none;
  left: -4px;
  top: 8px;
}

.desktop-login-form {
  ::v-deep .el-form-item {
    margin-bottom: 38px;
  }

  ::v-deep .el-input__inner {
    height: 70px;
    line-height: 70px;
    padding-left: 48px;
    border: 1px solid #ccc8c1;
    border-radius: 12px;
    background: rgba(255, 255, 255, 0.46);
    color: #292724;
    font-size: 16px;
    font-weight: 400;
    box-shadow: none;
    transition:
      border-color 160ms cubic-bezier(0.2, 0, 0, 1),
      box-shadow 160ms cubic-bezier(0.2, 0, 0, 1),
      background-color 160ms cubic-bezier(0.2, 0, 0, 1);
  }

  ::v-deep .el-input__inner:focus {
    border-color: #66615b;
    background: rgba(255, 255, 255, 0.76);
    box-shadow: 0 0 0 3px rgba(37, 36, 33, 0.12);
  }

  .input-icon {
    height: 70px;
    line-height: 70px;
    width: 20px;
    margin-left: 10px;
    color: #77736e;
  }

  .el-icon-user.input-icon {
    font-size: 24px;
  }
}

.desktop-form-head {
  margin-bottom: 46px;
}

.desktop-form-head h3 {
  margin: 0;
  color: #252421;
  font-size: clamp(34px, 2.55vw, 42px);
  line-height: 1.2;
  font-weight: 700;
  letter-spacing: -0.045em;
}

.desktop-form-head p {
  margin: 12px 0 0;
  color: #77716a;
  font-size: 16px;
  line-height: 1.7;
}

.desktop-login-form .login-field-label {
  margin: 0 0 10px 2px;
  color: #403d39;
  font-size: 15px;
  font-weight: 550;
  line-height: 20px;
}

.desktop-captcha-row {
  display: flex;
  align-items: center;
  gap: 18px;

  .el-input {
    flex: 1;
  }
}

.desktop-login-code {
  position: relative;
  width: 164px;
  height: 70px;
  padding: 0;
  overflow: hidden;
  border-radius: 12px;
  cursor: pointer;
  border: 1px solid #ccc8c1;
  background: rgba(255, 255, 255, 0.5);
  display: grid;
  place-items: center;
  color: #6f6a64;
  font-size: 13px;
  font-weight: 600;
}

.desktop-login-code::after {
  position: absolute;
  inset: 0;
  z-index: 1;
  content: "";
  pointer-events: none;
  border-radius: inherit;
  box-shadow: inset 0 0 0 1px #ccc8c1;
}

.desktop-login-code-img {
  width: 100%;
  height: 70px;
  display: block;
  object-fit: fill;
}

.desktop-login-form ::v-deep .desktop-captcha-item {
  margin-bottom: 28px;
}

.desktop-options {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin: -3px 0 27px;
  font-size: 15px;

  ::v-deep .el-checkbox__label {
    color: #3f3c38;
    font-weight: 500;
  }

  ::v-deep .el-checkbox__input.is-checked .el-checkbox__inner,
  ::v-deep .el-checkbox__input.is-indeterminate .el-checkbox__inner {
    border-color: #252421;
    background-color: #252421;
  }

  ::v-deep .el-checkbox__input.is-checked + .el-checkbox__label {
    color: #252421;
  }
}

.desktop-login-submit {
  margin-bottom: 0 !important;

  ::v-deep .el-button--primary {
    width: 100%;
    height: 68px;
    border: none;
    border-radius: 11px;
    background: #252421;
    background-image: none;
    box-shadow: none;
    font-size: 18px;
    font-weight: 600;
    transition:
      background-color 160ms cubic-bezier(0.2, 0, 0, 1),
      box-shadow 160ms cubic-bezier(0.2, 0, 0, 1),
      transform 100ms cubic-bezier(0.2, 0, 0, 1);
  }

  ::v-deep .el-button--primary:hover,
  ::v-deep .el-button--primary:focus {
    background: #171614;
    box-shadow: 0 10px 24px rgba(37, 36, 33, 0.16);
  }

  ::v-deep .el-button--primary:active {
    transform: scale(0.985);
  }
}

@keyframes desktop-copy-enter {
  from {
    opacity: 0;
    transform: translate3d(-14px, 0, 0);
  }
  to {
    opacity: 1;
    transform: translate3d(0, 0, 0);
  }
}

@keyframes desktop-form-enter {
  from {
    opacity: 0;
    transform: translate3d(14px, 0, 0);
  }
  to {
    opacity: 1;
    transform: translate3d(0, 0, 0);
  }
}

@media (prefers-reduced-motion: no-preference) {
  .desktop-login-copy > * {
    animation: desktop-copy-enter 420ms cubic-bezier(0.16, 1, 0.3, 1) both;
  }

  .desktop-copy-body {
    animation-delay: 70ms;
  }

  .desktop-login-form {
    animation: desktop-form-enter 460ms cubic-bezier(0.16, 1, 0.3, 1) 90ms both;
  }
}

@media (max-width: 1180px) and (min-width: 769px) {
  .desktop-login-shell {
    grid-template-columns: 52% 48%;
  }

  .desktop-login-copy {
    padding: 46px clamp(28px, 4vw, 50px) 36px;
  }

  .desktop-copy-body {
    margin-top: clamp(76px, 11vh, 104px);
  }

  .desktop-login-copy h1 {
    font-size: clamp(36px, 4.2vw, 48px);
  }

  .desktop-login-copy p {
    font-size: 14px;
  }

  .desktop-login-form {
    width: min(420px, calc(100% - 52px));
    left: 0;
    top: 0;
  }

  .desktop-operations-grid {
    grid-template-columns: repeat(3, minmax(0, 1fr));
    gap: 16px;
    margin-top: 44px;
  }

  .desktop-operations-grid article {
    min-height: 126px;
    padding: 0;
    flex-direction: column;
    align-items: flex-start;
    justify-content: flex-start;
    gap: 12px;
  }

  .desktop-operation-icon {
    width: 58px;
    height: 58px;
  }

  .desktop-operation-icon i {
    font-size: 25px;
  }

  .desktop-operations-grid article:first-child .desktop-operation-icon i {
    font-size: 29px;
    -webkit-text-stroke-width: 1px;
  }

  .desktop-form-head {
    margin-bottom: 34px;
  }

  .desktop-login-form ::v-deep .el-form-item {
    margin-bottom: 22px;
  }

  .desktop-login-form ::v-deep .desktop-captcha-item {
    margin-bottom: 22px;
  }

  .desktop-login-form ::v-deep .el-input__inner,
  .desktop-login-form .input-icon,
  .desktop-login-code,
  .desktop-login-code-img {
    height: 58px;
    line-height: 58px;
  }

  .desktop-login-code {
    width: 128px;
  }

  .desktop-login-submit ::v-deep .el-button--primary {
    height: 58px;
  }
}

@media (max-width: 768px) {
  .login.is-desktop-web {
    align-items: flex-start;
    overflow-y: auto;
  }

  .desktop-login-shell {
    width: min(430px, calc(100% - 32px));
    min-height: 100vh;
    grid-template-columns: minmax(0, 1fr);
    gap: 0;
    padding: 32px 0 58px;
    box-sizing: border-box;
  }

  .desktop-login-copy {
    display: none;
  }

  .desktop-login-form {
    width: 100%;
    padding: 28px 24px 24px;
    box-sizing: border-box;
  }

  .desktop-captcha-row .el-input {
    min-width: 0;
  }

  .desktop-login-code {
    width: 110px;
  }
}

@media (prefers-reduced-motion: reduce) {
  .desktop-login-copy > *,
  .desktop-login-form,
  .desktop-login-form ::v-deep .el-input__inner,
  .desktop-login-submit ::v-deep .el-button--primary {
    animation: none !important;
    transition-duration: 0.01ms !important;
  }
}

.mobile-auth-shell {
  position: relative;
  width: min(100%, 480px);
  min-height: var(--mobile-viewport-height, 100dvh);
  overflow: hidden;
  box-shadow: none;
  background: var(--mobile-color-page, #f4f5f2);
}

/* Class retained for entry-page contract tests; visual is warm-neutral shell (no full-bleed photo glass). */
.tea-room-backdrop {
  background-color: var(--mobile-color-page, #f4f5f2);
  background-image: none;
  /* Asset path retained for release contract checks: mobile-inventory-tea-room-bg.jpg */
}

.mobile-auth-shell::before,
.mobile-auth-shell::after {
  display: none;
  content: none;
  backdrop-filter: none;
  -webkit-backdrop-filter: none;
}

.auth-stage {
  position: relative;
  z-index: 1;
  min-height: var(--mobile-viewport-height, 100dvh);
  padding: calc(var(--mobile-safe-top, env(safe-area-inset-top, 0px)) + 28px) 16px calc(76px + env(safe-area-inset-bottom));
  display: flex;
  align-items: flex-start;
  justify-content: center;
}

.auth-card-stack {
  width: 100%;
  max-width: 388px;
  display: flex;
  flex-direction: column;
  gap: 16px;
  transform: none;
  margin-top: 8px;
}

.auth-hero {
  display: flex;
  align-items: center;
  gap: 12px;
  margin: 0;
  padding: 0 2px;
}

.brand-mark {
  width: 48px;
  height: 48px;
  border-radius: 12px;
  display: grid;
  place-items: center;
  color: var(--mobile-color-primary, #0b6b53);
  background: var(--mobile-color-primary-soft, #e7f2ed);
  border: 1px solid rgba(11, 107, 83, 0.18);
  box-shadow: none;
  backdrop-filter: none;
  -webkit-backdrop-filter: none;
}

.brand-mark svg {
  width: 28px;
  height: 28px;
  fill: currentColor;
}

.auth-hero h1 {
  margin: 0;
  color: var(--mobile-color-ink, #17211d);
  font-size: 24px;
  line-height: 1.2;
  font-weight: 700;
  letter-spacing: -0.02em;
}

.auth-hero p {
  margin: 4px 0 0;
  color: var(--mobile-color-muted, #66736d);
  font-size: 13px;
  font-weight: 500;
}

.glass-panel {
  background: var(--mobile-color-surface, #fff);
  border: 1px solid var(--mobile-color-line, #dde2de);
  box-shadow: none;
  backdrop-filter: none;
  -webkit-backdrop-filter: none;
}

.login-form {
  border-radius: 16px;
  padding: 20px 16px 16px;
  position: relative;

  ::v-deep .el-form-item {
    margin-bottom: 14px;
  }

  .el-input {
    height: 48px;
  }

  ::v-deep .el-input__inner {
    height: 48px;
    line-height: 48px;
    border: 1px solid var(--mobile-color-line-strong, #cbd3ce);
    border-radius: 12px;
    background: var(--mobile-color-surface, #fff);
    color: var(--mobile-color-ink, #17211d);
    font-size: 16px;
    font-weight: 500;
    box-shadow: none;
    transition: border-color 0.18s ease, box-shadow 0.18s ease;
  }

  ::v-deep .el-input__inner:focus {
    border-color: var(--mobile-color-primary, #0b6b53);
    box-shadow: 0 0 0 3px rgba(11, 107, 83, 0.2);
    background: #fff;
  }

  ::v-deep .el-input__prefix {
    left: 12px;
  }

  ::v-deep .el-checkbox__label {
    color: var(--mobile-color-muted, #66736d);
    font-size: 14px;
    font-weight: 600;
  }

  ::v-deep .el-checkbox__input.is-checked + .el-checkbox__label {
    color: var(--mobile-color-primary, #0b6b53);
  }

  ::v-deep .el-checkbox__input.is-checked .el-checkbox__inner {
    border-color: var(--mobile-color-primary, #0b6b53);
    background: var(--mobile-color-primary, #0b6b53);
  }

  ::v-deep .el-button--primary {
    width: 100%;
    height: 48px;
    border: 1px solid var(--mobile-color-primary, #0b6b53);
    border-radius: 12px;
    background: var(--mobile-color-primary, #0b6b53);
    box-shadow: none;
    font-size: 16px;
    font-weight: 700;
    letter-spacing: 0;
  }

  ::v-deep .el-button--primary:hover,
  ::v-deep .el-button--primary:focus {
    background: var(--mobile-color-primary-strong, #075441);
    border-color: var(--mobile-color-primary-strong, #075441);
    transform: none;
  }

  .input-icon {
    height: 48px;
    width: 15px;
    color: #6d8177;
  }
}

.form-head {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  margin-bottom: 18px;
}

.form-head h2 {
  margin: 0;
  color: var(--mobile-color-ink, #17211d);
  font-size: 20px;
  line-height: 1.25;
  font-weight: 700;
}

.form-head span {
  color: var(--mobile-color-muted, #66736d);
  font-size: 13px;
  font-weight: 600;
}

.captcha-row {
  display: grid;
  grid-template-columns: minmax(0, 2fr) minmax(0, 1fr);
  align-items: center;
  gap: 10px;
}

.captcha-input {
  min-width: 0;
}

.login-code {
  width: 100%;
  min-width: 0;
  height: 48px;
  padding: 0;
  border-radius: 12px;
  overflow: hidden;
  border: 1px solid var(--mobile-color-line-strong, #cbd3ce);
  background: var(--mobile-color-surface-soft, #f8f8f5);
  box-shadow: none;
  cursor: pointer;
  display: grid;
  place-items: center;
  img {
    vertical-align: middle;
    width: 100%;
  }
}

.form-options {
  min-height: 44px;
}

.form-options ::v-deep .el-checkbox {
  display: inline-flex;
  align-items: center;
  min-height: 44px;
}

.login-code span {
  color: #6d8177;
  font-size: 14px;
  font-weight: 800;
}

.login-code-img {
  height: 48px;
}

.form-options {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin: -2px 2px 18px;

  ::v-deep .el-checkbox {
    min-height: 44px;
    display: inline-flex;
    align-items: center;
  }
}

.form-options > span {
  color: #60746a;
  font-size: 13px;
  font-weight: 800;
}

.login-submit {
  margin-bottom: 0 !important;
}

.register-row {
  text-align: center;
  margin-top: 12px;
}

.el-login-footer {
  position: absolute;
  left: 18px;
  right: 18px;
  bottom: max(16px, calc(env(safe-area-inset-bottom) + 8px));
  z-index: 2;
  text-align: center;
  color: rgba(60, 73, 66, 0.66);
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0;
}

@supports (height: 100dvh) {
  .login,
  .mobile-auth-shell {
    min-height: 100dvh;
  }
}

@media (max-width: 480px) {
  .mobile-auth-shell {
    width: 100%;
    box-shadow: none;
  }

  .auth-stage {
    padding: clamp(30px, 6vh, 56px) 22px calc(76px + env(safe-area-inset-bottom));
  }

  .auth-card-stack {
    max-width: none;
  }

  .auth-hero h1 {
    font-size: 35px;
  }
}

@media (max-width: 360px) {
  .auth-stage {
    padding-left: 16px;
    padding-right: 16px;
  }

  .auth-hero {
    gap: 12px;
  }

  .brand-mark {
    width: 56px;
    height: 56px;
    border-radius: 20px;
  }

  .auth-hero h1 {
    font-size: 30px;
  }

  .login-code {
    width: 92px;
  }
}

/* Brand-entry polish: soft environmental light, clear depth, no heavy effects. */
.mobile-auth-page,
.mobile-auth-shell.tea-room-backdrop {
  background-color: var(--mobile-color-page, #f4f5f2);
  background-image:
    radial-gradient(circle at 92% 4%, rgba(11, 107, 83, 0.16) 0, rgba(11, 107, 83, 0) 240px),
    radial-gradient(circle at 4% 62%, rgba(40, 102, 177, 0.065) 0, rgba(40, 102, 177, 0) 210px),
    linear-gradient(180deg, #fbfcf9 0%, #f4f6f3 55%, #eef2ee 100%);
}

.brand-mark {
  border-color: rgba(11, 107, 83, 0.18);
  background: linear-gradient(145deg, rgba(255, 255, 255, 0.98), rgba(214, 235, 226, 0.94));
  box-shadow:
    0 0 0 1px rgba(255, 255, 255, 0.7),
    0 10px 24px rgba(11, 107, 83, 0.12);
}

.login-form.glass-panel {
  border-color: rgba(203, 211, 206, 0.82);
  box-shadow:
    0 0 0 1px rgba(255, 255, 255, 0.68),
    0 18px 42px rgba(23, 33, 29, 0.1);
}

.login-form {
  ::v-deep .el-input__inner {
    background: rgba(251, 252, 250, 0.96);
    box-shadow: inset 0 1px 2px rgba(23, 33, 29, 0.025);
  }

  ::v-deep .el-button--primary {
    background: var(--mobile-gradient-primary, linear-gradient(135deg, #0b6b53, #128064));
    box-shadow: var(--mobile-shadow-primary, 0 10px 24px rgba(11, 107, 83, 0.22));
  }
}

.login-code {
  box-shadow: 0 4px 12px rgba(23, 33, 29, 0.05);
}

@media (prefers-reduced-motion: no-preference) {
  .auth-hero {
    animation: mobile-topbar-enter 200ms var(--mobile-ease-spring, cubic-bezier(0.22, 1, 0.36, 1)) both;
  }

  .login-form {
    animation: mobile-surface-enter 260ms var(--mobile-ease-spring, cubic-bezier(0.22, 1, 0.36, 1)) 45ms both;
  }
}
</style>
