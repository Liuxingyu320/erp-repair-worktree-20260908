<template>
  <div class="inner-link-wrap" :style="'height:' + height" v-loading="loading" element-loading-text="正在加载页面，请稍候！">
    <el-alert
      v-if="externalServiceHint"
      class="external-service-unavailable"
      type="warning"
      show-icon
      :closable="false"
      title="服务未启动或地址未配置"
      :description="externalServiceHint"
    />
    <div v-if="unavailable" class="external-service-unavailable unavailable-panel">
      <div>
        <strong>服务未启动或地址未配置</strong>
        <p>{{ externalServiceHint || "当前外部页面暂时不可用，请确认服务地址后重试。" }}</p>
      </div>
      <el-button v-if="safeSrc" size="mini" type="primary" plain @click="openExternal">新窗口打开</el-button>
    </div>
    <iframe
      :id="iframeId"
      style="width: 100%; height: 100%"
      v-if="safeSrc"
      :src="safeSrc"
      frameborder="no"
    ></iframe>
  </div>
</template>

<script>
const { sanitizeFrameUrl } = require("@/utils/urlSecurity")

export default {
  props: {
    src: {
      type: String,
      default: "/"
    },
    iframeId: {
      type: String
    }
  },
  data() {
    return {
      loading: false,
      unavailable: false,
      loadTimer: null,
      height: document.documentElement.clientHeight - 94.5 + "px;"
    }
  },
  computed: {
    safeSrc() {
      return sanitizeFrameUrl(this.src)
    },
    externalServiceHint() {
      if (!this.src) {
        return ""
      }
      if (!this.safeSrc) {
        return "页面地址不符合安全策略，已阻止加载。"
      }
      if (this.src.indexOf("/swagger-ui/index.html") > -1) {
        return "系统接口文档依赖 Swagger UI 和各服务 API Docs；未启用 SPRINGDOC_API_DOCS_ENABLED 或菜单地址未同步时，请不要按 HTTP 200 判断为可用。"
      }
      if (/^https?:\/\/(localhost|127\.0\.0\.1):(8718|8848|9100)\b/.test(this.src)) {
        return "该入口依赖本机 Sentinel、Nacos 或 Admin 控制台。服务未启动时会显示浏览器错误页，可先启动对应控制台或调整菜单地址。"
      }
      return ""
    }
  },
  mounted() {
    var _this = this
    if (!this.safeSrc) {
      this.unavailable = true
      return
    }
    const iframeId = ("#" + this.iframeId).replace(/\//g, "\\/")
    const iframe = document.querySelector(iframeId)
    if (!iframe) {
      this.unavailable = true
      return
    }
    this.loadTimer = window.setTimeout(function () {
      if (_this.loading && _this.externalServiceHint) {
        _this.unavailable = true
      }
    }, 5000)
    // iframe页面loading控制
    if (iframe.attachEvent) {
      this.loading = true
      iframe.attachEvent("onload", function () {
        _this.loading = false
        window.clearTimeout(_this.loadTimer)
      })
    } else {
      this.loading = true
      iframe.onload = function () {
        _this.loading = false
        window.clearTimeout(_this.loadTimer)
      }
      iframe.onerror = function () {
        _this.loading = false
        _this.unavailable = true
        window.clearTimeout(_this.loadTimer)
      }
    }
  },
  beforeDestroy() {
    window.clearTimeout(this.loadTimer)
  },
  methods: {
    openExternal() {
      if (this.safeSrc) {
        window.open(this.safeSrc, "_blank", "noopener,noreferrer")
      }
    }
  }
}
</script>

<style scoped>
.inner-link-wrap {
  position: relative;
  background: #fff;
}

.external-service-unavailable {
  margin: 12px;
}

.unavailable-panel {
  position: absolute;
  z-index: 2;
  top: 64px;
  left: 50%;
  width: min(520px, calc(100% - 32px));
  transform: translateX(-50%);
  padding: 16px;
  border: 1px solid #f3d19e;
  border-radius: 6px;
  background: #fdf6ec;
  color: #8a4b12;
  box-shadow: 0 12px 30px rgba(15, 23, 42, 0.12);
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
}

.unavailable-panel strong {
  display: block;
  margin-bottom: 6px;
  color: #7c2d12;
}

.unavailable-panel p {
  margin: 0;
  line-height: 1.5;
}
</style>
