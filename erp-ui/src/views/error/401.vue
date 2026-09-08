<template>
  <div class="err-page-container mobile-system-page">
    <main class="error-shell" aria-labelledby="permission-error-title">
      <section class="error-copy">
        <span class="error-code">401</span>
        <h1 id="permission-error-title">当前账号无权访问</h1>
        <p>您可能尚未获得这个页面的权限，或登录状态已经发生变化。可以先返回上一页，也可以回到首页重新选择功能。</p>
        <div class="error-actions">
          <el-button type="primary" class="mobile-button mobile-button--primary" icon="el-icon-s-home" @click="goHome">返回有效入口</el-button>
          <el-button class="error-secondary" icon="el-icon-arrow-left" @click="back">返回上一页</el-button>
        </div>
        <small>如确需使用此功能，请联系管理员为当前账号补充权限。</small>
      </section>
      <figure class="error-illustration">
        <img :src="errGif" width="313" height="428" alt="访问权限受限插图">
      </figure>
    </main>
  </div>
</template>

<script>
import errGif from '@/assets/401_images/401.gif'

export default {
  name: 'Page401',
  data() {
    return {
      errGif: errGif + '?' + +new Date()
    }
  },
  methods: {
    back() {
      if (this.$route.query.noGoBack || window.history.length <= 1) {
        this.goHome()
      } else {
        this.$router.go(-1)
      }
    },
    goHome() {
      this.$router.push({ path: '/' }).catch(() => {})
    }
  }
}
</script>

<style lang="scss" scoped>
.err-page-container {
  min-height: 100vh;
  min-height: 100dvh;
  display: grid;
  place-items: center;
  box-sizing: border-box;
  padding:
    calc(24px + env(safe-area-inset-top))
    calc(20px + env(safe-area-inset-right))
    calc(24px + env(safe-area-inset-bottom))
    calc(20px + env(safe-area-inset-left));
  background: var(--mobile-color-page, #f4f5f2);
}

.error-shell {
  width: min(880px, 100%);
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(220px, 320px);
  align-items: center;
  gap: clamp(28px, 6vw, 72px);
  box-sizing: border-box;
  padding: clamp(28px, 5vw, 56px);
  border: 1px solid var(--mobile-color-line);
  border-radius: 16px;
  background: var(--mobile-color-surface, #fff);
  box-shadow: none;
}

.error-copy {
  min-width: 0;

  h1 {
    margin: 8px 0 14px;
    color: var(--mobile-color-ink);
    font-size: clamp(28px, 5vw, 42px);
    line-height: 1.18;
    overflow-wrap: anywhere;
  }

  p {
    margin: 0;
    color: var(--mobile-color-muted);
    font-size: 15px;
    line-height: 1.75;
  }

  small {
    display: block;
    margin-top: 18px;
    color: var(--mobile-color-subtle);
    font-size: 13px;
    line-height: 1.6;
  }
}

.error-code {
  color: var(--mobile-color-primary);
  font-size: 18px;
  font-weight: 700;
  letter-spacing: 0.12em;
}

.error-actions {
  display: flex;
  flex-direction: column;
  gap: 10px;
  margin-top: 26px;

  ::v-deep .el-button {
    width: 100%;
    min-height: 44px;
    margin: 0;
    border-radius: 12px;
    font-weight: 600;
  }

  ::v-deep .el-button--primary {
    min-height: 48px;
    border-color: var(--mobile-color-primary);
    background: var(--mobile-color-primary);
  }

  ::v-deep .error-secondary {
    color: var(--mobile-color-muted);
    border-color: var(--mobile-color-line-strong);
    background: var(--mobile-color-surface);
  }
}

.error-illustration {
  margin: 0;

  img {
    width: min(100%, 260px);
    height: auto;
    display: block;
    margin: 0 auto;
  }
}

@media (max-width: 640px) {
  .err-page-container {
    place-items: start center;
    padding-top: calc(18px + env(safe-area-inset-top));
  }

  .error-shell {
    grid-template-columns: minmax(0, 1fr);
    gap: 18px;
    padding: 24px 20px;
    border-radius: 20px;
  }

  .error-illustration {
    order: -1;

    img {
      width: min(56vw, 176px);
      max-height: 190px;
      object-fit: contain;
    }
  }

  .error-actions {
    display: grid;
    grid-template-columns: repeat(2, minmax(0, 1fr));

    ::v-deep .el-button {
      width: 100%;
      padding-right: 10px;
      padding-left: 10px;
    }
  }
}

@media (max-width: 340px) {
  .error-actions {
    grid-template-columns: 1fr;
  }
}
</style>
