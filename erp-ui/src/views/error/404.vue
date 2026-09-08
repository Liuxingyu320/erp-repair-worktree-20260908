<template>
  <div class="http404-container mobile-system-page">
    <main class="http404-shell" aria-labelledby="not-found-title">
      <figure class="pic-404" aria-label="页面未找到插图">
        <img class="pic-404__parent" src="@/assets/404_images/404.png" alt="">
        <img class="pic-404__child left" src="@/assets/404_images/404_cloud.png" alt="">
        <img class="pic-404__child mid" src="@/assets/404_images/404_cloud.png" alt="">
        <img class="pic-404__child right" src="@/assets/404_images/404_cloud.png" alt="">
      </figure>
      <section class="not-found-copy">
        <span class="not-found-code">404</span>
        <h1 id="not-found-title">{{ message }}</h1>
        <p>这个页面可能已被移动、删除，或链接地址有误。请返回上一页继续操作，或回到首页重新进入功能。</p>
        <div class="not-found-actions">
          <router-link to="/" class="return-home">
            <i class="el-icon-s-home" aria-hidden="true" />
            <span>继续工作</span>
          </router-link>
          <el-button class="error-secondary" icon="el-icon-arrow-left" @click="back">返回上一页</el-button>
        </div>
      </section>
    </main>
  </div>
</template>

<script>

export default {
  name: 'Page404',
  computed: {
    message() {
      return '没有找到这个页面'
    }
  },
  methods: {
    back() {
      if (window.history.length <= 1) {
        this.$router.push('/').catch(() => {})
        return
      }
      this.$router.go(-1)
    }
  }
}
</script>

<style lang="scss" scoped>
.wscn-http404-container{
  transform: translate(-50%,-50%);
  position: absolute;
  top: 40%;
  left: 50%;
}
.wscn-http404 {
  position: relative;
  width: 1200px;
  padding: 0 50px;
  overflow: hidden;
  .pic-404 {
    position: relative;
    float: left;
    width: 600px;
    overflow: hidden;
    &__parent {
      width: 100%;
    }
    &__child {
      position: absolute;
      &.left {
        width: 80px;
        top: 17px;
        left: 220px;
        opacity: 0;
        animation-name: cloudLeft;
        animation-duration: 2s;
        animation-timing-function: linear;
        animation-fill-mode: forwards;
        animation-delay: 1s;
      }
      &.mid {
        width: 46px;
        top: 10px;
        left: 420px;
        opacity: 0;
        animation-name: cloudMid;
        animation-duration: 2s;
        animation-timing-function: linear;
        animation-fill-mode: forwards;
        animation-delay: 1.2s;
      }
      &.right {
        width: 62px;
        top: 100px;
        left: 500px;
        opacity: 0;
        animation-name: cloudRight;
        animation-duration: 2s;
        animation-timing-function: linear;
        animation-fill-mode: forwards;
        animation-delay: 1s;
      }
      @keyframes cloudLeft {
        0% {
          top: 17px;
          left: 220px;
          opacity: 0;
        }
        20% {
          top: 33px;
          left: 188px;
          opacity: 1;
        }
        80% {
          top: 81px;
          left: 92px;
          opacity: 1;
        }
        100% {
          top: 97px;
          left: 60px;
          opacity: 0;
        }
      }
      @keyframes cloudMid {
        0% {
          top: 10px;
          left: 420px;
          opacity: 0;
        }
        20% {
          top: 40px;
          left: 360px;
          opacity: 1;
        }
        70% {
          top: 130px;
          left: 180px;
          opacity: 1;
        }
        100% {
          top: 160px;
          left: 120px;
          opacity: 0;
        }
      }
      @keyframes cloudRight {
        0% {
          top: 100px;
          left: 500px;
          opacity: 0;
        }
        20% {
          top: 120px;
          left: 460px;
          opacity: 1;
        }
        80% {
          top: 180px;
          left: 340px;
          opacity: 1;
        }
        100% {
          top: 200px;
          left: 300px;
          opacity: 0;
        }
      }
    }
  }
  .bullshit {
    position: relative;
    float: left;
    width: 300px;
    padding: 30px 0;
    overflow: hidden;
    &__oops {
      font-size: 32px;
      font-weight: bold;
      line-height: 40px;
      color: #1482f0;
      opacity: 0;
      margin-bottom: 20px;
      animation-name: slideUp;
      animation-duration: 0.5s;
      animation-fill-mode: forwards;
    }
    &__headline {
      font-size: 20px;
      line-height: 24px;
      color: #222;
      font-weight: bold;
      opacity: 0;
      margin-bottom: 10px;
      animation-name: slideUp;
      animation-duration: 0.5s;
      animation-delay: 0.1s;
      animation-fill-mode: forwards;
    }
    &__info {
      font-size: 13px;
      line-height: 21px;
      color: grey;
      opacity: 0;
      margin-bottom: 30px;
      animation-name: slideUp;
      animation-duration: 0.5s;
      animation-delay: 0.2s;
      animation-fill-mode: forwards;
    }
    &__return-home {
      display: block;
      float: left;
      width: 110px;
      height: 36px;
      background: #1482f0;
      border-radius: 100px;
      text-align: center;
      color: #ffffff;
      opacity: 0;
      font-size: 14px;
      line-height: 36px;
      cursor: pointer;
      animation-name: slideUp;
      animation-duration: 0.5s;
      animation-delay: 0.3s;
      animation-fill-mode: forwards;
    }
    @keyframes slideUp {
      0% {
        transform: translateY(60px);
        opacity: 0;
      }
      100% {
        transform: translateY(0);
        opacity: 1;
      }
    }
  }
}

.http404-container {
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

.http404-shell {
  width: min(980px, 100%);
  display: grid;
  grid-template-columns: minmax(280px, 1.25fr) minmax(260px, 0.75fr);
  align-items: center;
  gap: clamp(28px, 6vw, 72px);
  box-sizing: border-box;
  padding: clamp(28px, 5vw, 56px);
  border: 1px solid var(--mobile-color-line);
  border-radius: 16px;
  background: var(--mobile-color-surface, #fff);
  box-shadow: none;
}

.http404-shell .pic-404 {
  position: relative;
  width: 100%;
  margin: 0;
  overflow: hidden;
}

.http404-shell .pic-404__parent {
  width: 100%;
  display: block;
}

.http404-shell .pic-404__child {
  position: absolute;
  opacity: 0.72;
  animation: cloudDrift 2.2s ease-in-out infinite alternate;
}

.http404-shell .pic-404__child.left {
  width: 13%;
  top: 9%;
  left: 14%;
}

.http404-shell .pic-404__child.mid {
  width: 8%;
  top: 5%;
  left: 58%;
  animation-delay: 0.3s;
}

.http404-shell .pic-404__child.right {
  width: 10%;
  top: 31%;
  left: 78%;
  animation-delay: 0.6s;
}

.not-found-copy {
  min-width: 0;
}

.not-found-copy h1 {
  margin: 8px 0 14px;
  color: var(--mobile-color-ink);
  font-size: clamp(28px, 5vw, 42px);
  line-height: 1.18;
  overflow-wrap: anywhere;
}

.not-found-copy p {
  margin: 0;
  color: var(--mobile-color-muted);
  font-size: 15px;
  line-height: 1.75;
}

.not-found-code {
  color: var(--mobile-color-primary);
  font-size: 18px;
  font-weight: 700;
  letter-spacing: 0.12em;
}

.not-found-actions {
  display: flex;
  flex-direction: column;
  gap: 10px;
  margin-top: 26px;
}

.not-found-actions ::v-deep .el-button {
  width: 100%;
  min-height: 44px;
  margin: 0;
  border-radius: 12px;
  font-weight: 600;
}

.not-found-actions ::v-deep .error-secondary {
  color: var(--mobile-color-muted);
  border-color: var(--mobile-color-line-strong);
  background: var(--mobile-color-surface);
}

.return-home {
  min-width: 120px;
  min-height: 48px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 7px;
  box-sizing: border-box;
  padding: 0 18px;
  border-radius: 12px;
  color: #fff;
  background: var(--mobile-color-primary);
  font-size: 14px;
  font-weight: 700;
  text-decoration: none;
}

@keyframes cloudDrift {
  from { transform: translate3d(0, -3px, 0); }
  to { transform: translate3d(12px, 8px, 0); }
}

@media (max-width: 640px) {
  .http404-container {
    place-items: start center;
    padding-top: calc(18px + env(safe-area-inset-top));
  }

  .http404-shell {
    grid-template-columns: minmax(0, 1fr);
    gap: 18px;
    padding: 24px 20px;
    border-radius: 20px;
  }

  .http404-shell .pic-404 {
    width: min(76vw, 300px);
    margin: 0 auto;
  }

  .not-found-actions {
    display: grid;
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .not-found-actions ::v-deep .el-button,
  .not-found-actions .return-home {
    width: 100%;
    padding-right: 10px;
    padding-left: 10px;
  }
}

@media (max-width: 340px) {
  .not-found-actions {
    grid-template-columns: 1fr;
  }
}

@media (prefers-reduced-motion: reduce) {
  .http404-shell .pic-404__child {
    animation: none;
  }
}
</style>
