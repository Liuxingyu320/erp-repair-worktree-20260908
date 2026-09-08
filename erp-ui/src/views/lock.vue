<template>
  <div class="lock-container mobile-system-page">
    <canvas ref="particleCanvas" class="particle-bg" aria-hidden="true"></canvas>

    <!-- 时钟 -->
    <div class="lock-time">{{ currentTime }}</div>
    <div class="lock-date">{{ currentDate }}</div>

    <!-- 锁屏卡片 -->
    <div class="lock-card">
      <div class="avatar-wrap">
        <img :src="avatar" :alt="avatarAlt" class="lock-avatar" @error="onAvatarError" />
        <div class="lock-icon" aria-hidden="true"><i class="el-icon-lock" /></div>
      </div>
      <div class="lock-username">{{ nickName }}</div>
      <div class="lock-hint">系统已锁定，请输入密码解锁</div>

      <div class="input-wrap" :class="{ shake: isShaking }">
        <input
          ref="passwordInput"
          v-model="password"
          type="password"
          aria-label="登录密码"
          placeholder="请输入登录密码"
          class="lock-input"
          autocomplete="current-password"
          autocapitalize="off"
          autocorrect="off"
          inputmode="text"
          :spellcheck="false"
          @keydown.enter="handleUnlock"
        />
        <button type="button" class="unlock-btn" aria-label="解锁系统" :disabled="loading" @click="handleUnlock">
          <i v-if="!loading" class="el-icon-arrow-right" aria-hidden="true" />
          <i v-else class="el-icon-loading" aria-hidden="true" />
        </button>
      </div>

      <div v-if="errorMsg" class="error-msg" role="alert">{{ errorMsg }}</div>

      <div class="lock-footer">
        <a href="/login" @click.prevent="goLogin">退出重新登录</a>
      </div>
    </div>
  </div>
</template>

<script>
import { mapGetters } from 'vuex'
import { unlockScreen } from '@/api/login'
import defAva from '@/assets/images/profile.jpg'

export default {
  name: 'LockScreen',
  data() {
    return {
      password: '',
      loading: false,
      errorMsg: '',
      isShaking: false,
      currentTime: '',
      currentDate: '',
      timer: null,
      shakeTimer: null,
      animationId: null,
      particlesAnimating: false,
      particles: [],
      resizeHandler: null,
      motionQuery: null,
      motionChangeHandler: null
    }
  },
  computed: {
    ...mapGetters(['avatar', 'nickName']),
    avatarAlt() {
      return this.nickName ? `${this.nickName}的头像` : '当前用户头像'
    }
  },
  mounted() {
    this.startClock()
    this.initParticles()
    this.$nextTick(() => {
      this.$refs.passwordInput && this.$refs.passwordInput.focus()
    })
  },
  beforeDestroy() {
    clearInterval(this.timer)
    clearTimeout(this.shakeTimer)
    this.stopParticleAnimation()
    if (this.resizeHandler) {
      window.removeEventListener('resize', this.resizeHandler)
    }
    if (this.motionQuery && this.motionChangeHandler) {
      if (this.motionQuery.removeEventListener) {
        this.motionQuery.removeEventListener('change', this.motionChangeHandler)
      } else if (this.motionQuery.removeListener) {
        this.motionQuery.removeListener(this.motionChangeHandler)
      }
    }
  },
  methods: {
    onAvatarError(e) {
      if (e.target.src !== defAva) {
        e.target.src = defAva
      }
    },
    startClock() {
      const update = () => {
        const now = new Date()
        const h = String(now.getHours()).padStart(2, '0')
        const m = String(now.getMinutes()).padStart(2, '0')
        const s = String(now.getSeconds()).padStart(2, '0')
        this.currentTime = `${h}:${m}:${s}`
        const days = ['星期日','星期一','星期二','星期三','星期四','星期五','星期六']
        this.currentDate = `${now.getFullYear()}年${now.getMonth()+1}月${now.getDate()}日 ${days[now.getDay()]}`
      }
      update()
      this.timer = setInterval(update, 1000)
    },
    async handleUnlock() {
      if (!this.password) {
        this.showError('请输入密码')
        return
      }
      this.loading = true
      this.errorMsg = ''
      try {
        await unlockScreen(this.password)
        const lockPath = this.$store.getters.lockPath || '/'
        await this.$store.dispatch('lock/unlockScreen')
        this.$router.replace(lockPath)
      } catch (error) {
        this.showError('解锁失败，请检查密码后重试')
        this.password = ''
        this.$refs.passwordInput && this.$refs.passwordInput.focus()
      } finally {
        this.loading = false
      }
    },
    showError(msg) {
      this.errorMsg = msg
      this.isShaking = true
      clearTimeout(this.shakeTimer)
      this.shakeTimer = setTimeout(() => { this.isShaking = false }, 600)
    },
    goLogin() {
      Promise.resolve(this.$store.dispatch('lock/unlockScreen'))
        .catch(() => {})
        .then(() => this.$store.dispatch('LogOut'))
        .finally(() => {
          this.$router.replace('/login').catch(() => {})
        })
    },
    shouldAnimateParticles() {
      const reduceMotion = this.motionQuery
        ? this.motionQuery.matches
        : window.matchMedia && window.matchMedia('(prefers-reduced-motion: reduce)').matches
      return window.innerWidth > 768 && !reduceMotion
    },
    initParticles() {
      const canvas = this.$refs.particleCanvas
      if (!canvas) return
      const ctx = canvas.getContext('2d')
      if (!ctx) return

      this.motionQuery = window.matchMedia
        ? window.matchMedia('(prefers-reduced-motion: reduce)')
        : null

      const refreshParticles = () => {
        this.stopParticleAnimation()
        const width = window.innerWidth
        const height = window.innerHeight
        const pixelRatio = Math.min(window.devicePixelRatio || 1, 2)
        canvas.width = Math.round(width * pixelRatio)
        canvas.height = Math.round(height * pixelRatio)
        canvas.style.width = `${width}px`
        canvas.style.height = `${height}px`
        ctx.setTransform(pixelRatio, 0, 0, pixelRatio, 0, 0)
        ctx.clearRect(0, 0, width, height)
        this.particles = []

        if (!this.shouldAnimateParticles()) return

        const count = Math.min(40, Math.max(24, Math.round(width / 32)))
        for (let i = 0; i < count; i++) {
          this.particles.push({
            x: Math.random() * width,
            y: Math.random() * height,
            r: Math.random() * 1.5 + 0.7,
            dx: (Math.random() - 0.5) * 0.4,
            dy: (Math.random() - 0.5) * 0.4,
            alpha: Math.random() * 0.28 + 0.12
          })
        }
        this.startParticleAnimation(ctx, width, height)
      }

      this.resizeHandler = refreshParticles
      this.motionChangeHandler = refreshParticles
      window.addEventListener('resize', this.resizeHandler)
      if (this.motionQuery) {
        if (this.motionQuery.addEventListener) {
          this.motionQuery.addEventListener('change', this.motionChangeHandler)
        } else if (this.motionQuery.addListener) {
          this.motionQuery.addListener(this.motionChangeHandler)
        }
      }
      refreshParticles()
    },
    startParticleAnimation(ctx, width, height) {
      this.particlesAnimating = true
      const draw = () => {
        if (!this.particlesAnimating) return
        ctx.clearRect(0, 0, width, height)
        this.particles.forEach(p => {
          ctx.beginPath()
          ctx.arc(p.x, p.y, p.r, 0, Math.PI * 2)
          ctx.fillStyle = `rgba(255,255,255,${p.alpha})`
          ctx.fill()
          p.x += p.dx
          p.y += p.dy
          if (p.x < 0 || p.x > width) p.dx *= -1
          if (p.y < 0 || p.y > height) p.dy *= -1
        })
        for (let i = 0; i < this.particles.length; i++) {
          for (let j = i + 1; j < this.particles.length; j++) {
            const a = this.particles[i], b = this.particles[j]
            const dist = Math.hypot(a.x - b.x, a.y - b.y)
            if (dist < 110) {
              ctx.beginPath()
              ctx.moveTo(a.x, a.y)
              ctx.lineTo(b.x, b.y)
              ctx.strokeStyle = `rgba(255,255,255,${0.11 * (1 - dist / 110)})`
              ctx.lineWidth = 0.5
              ctx.stroke()
            }
          }
        }
        this.animationId = requestAnimationFrame(draw)
      }
      draw()
    },
    stopParticleAnimation() {
      this.particlesAnimating = false
      if (this.animationId !== null) {
        cancelAnimationFrame(this.animationId)
        this.animationId = null
      }
    }
  }
}
</script>

<style scoped>
.lock-container {
  position: fixed;
  inset: 0;
  box-sizing: border-box;
  padding:
    calc(24px + env(safe-area-inset-top))
    calc(16px + env(safe-area-inset-right))
    calc(24px + env(safe-area-inset-bottom))
    calc(16px + env(safe-area-inset-left));
  background: var(--mobile-color-page, #f4f5f2);
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  z-index: 9999;
  font-family: Inter, 'PingFang SC', 'Microsoft YaHei', sans-serif;
  overflow: hidden;
  color: var(--mobile-color-ink, #17211d);
}

.particle-bg {
  display: none;
}

.lock-time {
  position: relative;
  z-index: 1;
  font-size: clamp(40px, 7vw, 56px);
  font-weight: 600;
  color: var(--mobile-color-ink, #17211d);
  letter-spacing: 2px;
  text-shadow: none;
  margin-bottom: 6px;
  font-variant-numeric: tabular-nums;
}

.lock-date {
  position: relative;
  z-index: 1;
  font-size: 14px;
  color: var(--mobile-color-muted, #66736d);
  margin-bottom: clamp(20px, 4vh, 36px);
  letter-spacing: 1px;
}

.lock-card {
  position: relative;
  z-index: 1;
  background: var(--mobile-color-surface, #fff);
  backdrop-filter: none;
  -webkit-backdrop-filter: none;
  border: 1px solid var(--mobile-color-line, #dde2de);
  border-radius: 16px;
  padding: 28px 20px 22px;
  width: min(420px, calc(100vw - 32px));
  box-sizing: border-box;
  display: flex;
  flex-direction: column;
  align-items: center;
  box-shadow: none;
}

.avatar-wrap {
  position: relative;
  margin-bottom: 16px;
}

.lock-avatar {
  width: 80px;
  height: 80px;
  border-radius: 50%;
  border: 3px solid rgba(255,255,255,0.3);
  object-fit: cover;
  display: block;
}

.lock-icon {
  position: absolute;
  bottom: -4px;
  right: -4px;
  background: var(--mobile-color-primary-soft, #e7f2ed);
  border-radius: 50%;
  width: 26px;
  height: 26px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--mobile-color-primary, #0b6b53);
  font-size: 13px;
  backdrop-filter: none;
}

.lock-username {
  color: var(--mobile-color-ink, #17211d);
  font-size: 18px;
  font-weight: 700;
  margin-bottom: 6px;
  letter-spacing: 0;
}

.lock-hint {
  color: var(--mobile-color-muted, #66736d);
  font-size: 13px;
  margin-bottom: 24px;
  line-height: 1.55;
  text-align: center;
}

.input-wrap {
  width: 100%;
  min-height: 52px;
  box-sizing: border-box;
  display: flex;
  align-items: center;
  background: var(--mobile-color-surface, #fff);
  border: 1px solid var(--mobile-color-line-strong, #cbd3ce);
  border-radius: 12px;
  padding: 4px 4px 4px 16px;
  transition: border-color 0.18s, box-shadow 0.18s;
}

.input-wrap:focus-within {
  border-color: var(--mobile-color-primary, #0b6b53);
  background: #fff;
  box-shadow: 0 0 0 3px rgba(11, 107, 83, 0.2);
}

.input-wrap.shake {
  animation: shake 0.5s ease;
}

@keyframes shake {
  0%, 100% { transform: translateX(0); }
  20% { transform: translateX(-8px); }
  40% { transform: translateX(8px); }
  60% { transform: translateX(-6px); }
  80% { transform: translateX(6px); }
}

.lock-input {
  flex: 1;
  min-width: 0;
  background: transparent;
  border: none;
  outline: none;
  color: var(--mobile-color-ink, #17211d);
  font-size: 16px;
  padding: 10px 0;
}

.lock-input::placeholder {
  color: var(--mobile-color-subtle, #8a948f);
}

.unlock-btn {
  width: 44px;
  height: 44px;
  min-width: 44px;
  min-height: 44px;
  border-radius: 12px;
  background: var(--mobile-color-primary, #0b6b53);
  border: none;
  color: #fff;
  font-size: 18px;
  cursor: pointer;
  transition: opacity 0.2s;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.unlock-btn:hover:not(:disabled) {
  transform: none;
  background: var(--mobile-color-primary-strong, #075441);
}

.unlock-btn:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.error-msg {
  margin-top: 14px;
  color: var(--mobile-color-danger, #c4322b);
  font-size: 13px;
  text-align: center;
  animation: fadeIn 0.3s ease;
}

@keyframes fadeIn {
  from { opacity: 0; transform: translateY(-4px); }
  to   { opacity: 1; transform: translateY(0); }
}

.lock-footer {
  margin-top: 24px;
}

.lock-footer a {
  min-height: 44px;
  display: inline-flex;
  align-items: center;
  color: var(--mobile-color-muted, #66736d);
  font-size: 13px;
  text-decoration: none;
  transition: color 0.2s;
}

.lock-footer a:hover {
  color: var(--mobile-color-primary, #0b6b53);
}

@media (max-width: 768px) {
  .lock-container {
    justify-content: flex-start;
    overflow-x: hidden;
    overflow-y: auto;
    padding-top: calc(28px + env(safe-area-inset-top));
  }

  .particle-bg {
    display: none;
  }

  .lock-time {
    margin-top: auto;
    font-size: clamp(46px, 15vw, 60px);
    letter-spacing: 2px;
  }

  .lock-date {
    margin-bottom: 24px;
    letter-spacing: 1px;
    text-align: center;
  }

  .lock-card {
    width: min(380px, 100%);
    margin-bottom: auto;
    padding: 28px 22px 22px;
    border-radius: 20px;
  }

  .lock-avatar {
    width: 70px;
    height: 70px;
  }

  .lock-hint {
    margin-bottom: 22px;
  }
}

@media (max-width: 340px), (max-height: 620px) {
  .lock-container {
    padding-top: calc(16px + env(safe-area-inset-top));
    padding-bottom: calc(16px + env(safe-area-inset-bottom));
  }

  .lock-time {
    font-size: 42px;
  }

  .lock-date {
    margin-bottom: 14px;
    font-size: 13px;
  }

  .lock-card {
    padding-top: 20px;
  }

  .avatar-wrap {
    margin-bottom: 10px;
  }

  .lock-avatar {
    width: 58px;
    height: 58px;
  }

  .lock-hint {
    margin-bottom: 16px;
  }
}

@media (prefers-reduced-motion: reduce) {
  .input-wrap.shake,
  .error-msg {
    animation: none;
  }

  .unlock-btn {
    transition: none;
  }
}
</style>
