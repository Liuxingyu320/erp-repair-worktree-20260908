<template>
  <header class="navbar top-nav-shell">
    <div class="top-toolbar">
      <div class="toolbar-left">
        <hamburger
          v-if="device==='mobile'"
          id="hamburger-container"
          :is-active="sidebar.opened"
          class="hamburger-container"
          @toggleClick="toggleSideBar"
        />
        <router-link class="brand-link" to="/" :title="systemTitle">
          <span v-if="device!=='mobile'" class="brand-mark" aria-hidden="true">
            <svg viewBox="0 0 24 24">
              <path d="M4 8.4 12 3l8 5.4v9.2L12 21l-8-3.4V8.4Zm8-2.8L6.8 9 12 11.6 17.2 9 12 5.6ZM6 10.7v5.6l5 2.2v-5.6l-5-2.2Zm7 7.8 5-2.2v-5.6l-5 2.2v5.6Z" />
            </svg>
          </span>
          <span v-if="device!=='mobile'" class="brand-title">
            <strong>BossERP</strong>
            <small>BUSINESS OPERATING SYSTEM</small>
          </span>
          <template v-else>
            <span class="brand-mark">ERP</span>
            <span class="brand-title">{{ systemTitle }}</span>
          </template>
        </router-link>
        <breadcrumb v-if="device!=='mobile'" id="breadcrumb-container" class="breadcrumb-container" />
      </div>

      <div class="right-menu">
        <template v-if="device!=='mobile'">
          <el-tooltip content="切换店铺" effect="dark" placement="bottom">
            <div class="right-menu-item hover-effect shop-switch" @click="goSelectShop">
              <i class="el-icon-office-building shop-icon"></i>
              <span class="shop-name">{{ currentShopName }}</span>
            </div>
          </el-tooltip>
          <search id="header-search" class="right-menu-item header-search" />

          <screenfull id="screenfull" class="right-menu-item hover-effect" />

          <el-tooltip content="布局大小" effect="dark" placement="bottom">
            <size-select id="size-select" class="right-menu-item hover-effect" />
          </el-tooltip>

          <el-tooltip content="我的待办" effect="dark" placement="bottom">
            <header-todo id="header-todo" class="right-menu-item hover-effect" />
          </el-tooltip>

          <el-tooltip content="消息通知" effect="dark" placement="bottom">
            <header-notice id="header-notice" class="right-menu-item hover-effect" />
          </el-tooltip>
        </template>

        <el-dropdown class="avatar-container right-menu-item hover-effect" trigger="hover">
          <div class="avatar-wrapper">
            <img :src="avatar" class="user-avatar">
            <span class="user-nickname"> {{ nickName }} </span>
          </div>
          <el-dropdown-menu slot="dropdown">
            <router-link to="/user/profile">
              <el-dropdown-item>个人中心</el-dropdown-item>
            </router-link>
            <el-dropdown-item @click.native="setLayout" v-if="setting">
              <span>布局设置</span>
            </el-dropdown-item>
            <el-dropdown-item @click.native="lockScreen">
              <span>锁定屏幕</span>
            </el-dropdown-item>
            <el-dropdown-item @click.native="versionVisible = true">
              <span>版本信息</span>
            </el-dropdown-item>
            <el-dropdown-item divided @click.native="logout">
              <span>退出登录</span>
            </el-dropdown-item>
          </el-dropdown-menu>
        </el-dropdown>
      </div>
    </div>

    <el-dialog title="版本信息" :visible.sync="versionVisible" width="560px" append-to-body>
      <el-alert
        v-if="versionMismatch"
        title="前后端版本不一致"
        description="当前页面资源与 system 服务不是同一提交，请刷新；若仍存在，请联系运维检查是否混包。"
        type="error"
        :closable="false"
        show-icon
      />
      <div class="version-grid">
        <span class="version-label">前端提交</span><code>{{ frontendBuild.commit }}</code>
        <span class="version-label">system 提交</span><code>{{ systemBuild.commit }}</code>
        <span class="version-label">前端构建时间</span><span>{{ frontendBuild.buildTime }}</span>
        <span class="version-label">待办快捷通过</span><span>{{ frontendBuild.todoQuickApproveEnabled ? '已开启' : '已关闭' }}</span>
        <span class="version-label">待办批量通过</span><span>{{ frontendBuild.todoBatchApproveEnabled ? '已开启' : '已关闭' }}</span>
        <span class="version-label">system 构建时间</span><span>{{ systemBuild.buildTime }}</span>
        <span class="version-label">system 版本</span><span>{{ systemBuild.version }}</span>
      </div>
      <div slot="footer" class="dialog-footer">
        <el-button aria-label="复制版本信息" @click="copyVersionInfo">复制版本信息</el-button>
        <el-button type="primary" @click="versionVisible = false">关 闭</el-button>
      </div>
    </el-dialog>

    <div v-if="device!=='mobile'" class="top-menu-row">
      <top-bar id="topbar-container" class="topbar-container" />
    </div>
  </header>
</template>

<script>
import { mapGetters } from 'vuex'
import Breadcrumb from '@/components/Breadcrumb'
import TopBar from './TopBar'
import Hamburger from '@/components/Hamburger'
import Screenfull from '@/components/Screenfull'
import SizeSelect from '@/components/SizeSelect'
import Search from '@/components/HeaderSearch'
import HeaderTodo from './HeaderTodo'
import HeaderNotice from './HeaderNotice'
import { listShopTree } from '@/api/system/dept'
import { clearSelectedDept, findDeptNodeById, getSelectedDeptContext, setSelectedDept } from '@/utils/shopContext'
import defaultSettings from '@/settings'

export default {
  components: {
    Breadcrumb,
    TopBar,
    Hamburger,
    Screenfull,
    SizeSelect,
    Search,
    HeaderTodo,
    HeaderNotice
  },
  data() {
    return {
      currentDeptContext: getSelectedDeptContext(),
      versionVisible: false,
      frontendBuild: {
        commit: defaultSettings.buildCommit,
        buildTime: defaultSettings.buildTime,
        todoQuickApproveEnabled: defaultSettings.todoQuickApproveEnabled,
        todoBatchApproveEnabled: defaultSettings.todoBatchApproveEnabled
      }
    }
  },
  computed: {
    ...mapGetters([
      'sidebar',
      'avatar',
      'device',
      'nickName',
      'systemBuild'
    ]),
    setting: {
      get() {
        return this.$store.state.settings.showSettings
      }
    },
    currentShopName() {
      const context = this.currentDeptContext || {}
      if (!context.deptName) {
        return '未选组织'
      }
      const prefix = context.isWarehouse ? '仓库' : context.isStore ? '门店' : '组织'
      return `${prefix}：${context.deptName}`
    },
    systemTitle() {
      return process.env.VUE_APP_TITLE || '企业资源管理系统'
    },
    versionMismatch() {
      const commitPattern = /^[0-9a-f]{40}$/
      return commitPattern.test(this.frontendBuild.commit) &&
        commitPattern.test(this.systemBuild.commit) &&
        this.frontendBuild.commit !== this.systemBuild.commit
    }
  },
  mounted() {
    window.addEventListener('erp:dept-changed', this.handleDeptChanged)
    this.validateCurrentDeptContext()
  },
  beforeDestroy() {
    window.removeEventListener('erp:dept-changed', this.handleDeptChanged)
  },
  methods: {
    validateCurrentDeptContext() {
      const context = getSelectedDeptContext()
      if (!context.deptId) {
        return Promise.resolve(true)
      }
      const requestedDeptId = String(context.deptId)
      return listShopTree({ silentError: true }).then(response => {
        const nodes = Array.isArray(response.data) ? response.data : []
        const current = getSelectedDeptContext()
        if (String(current.deptId || '') !== requestedDeptId) {
          return true
        }
        const authorizedDept = findDeptNodeById(nodes, requestedDeptId)
        if (authorizedDept) {
          const authorizedName = authorizedDept.deptName || ''
          const authorizedType = authorizedDept.deptType || ''
          if (current.deptName !== authorizedName || current.deptType !== String(authorizedType).toUpperCase()) {
            setSelectedDept(authorizedDept.deptId, authorizedName, authorizedType)
            this.currentDeptContext = getSelectedDeptContext()
          }
          return true
        }
        clearSelectedDept()
        this.currentDeptContext = getSelectedDeptContext()
        return false
      }).catch(() => true)
    },
    handleDeptChanged() {
      this.currentDeptContext = getSelectedDeptContext()
    },
    toggleSideBar() {
      this.$store.dispatch('app/toggleSideBar')
    },
    setLayout() {
      this.$emit('setLayout')
    },
    lockScreen() {
      const currentPath = this.$route.fullPath
      this.$store.dispatch('lock/lockScreen', currentPath).then(() => {
        this.$router.push('/lock')
      })
    },
    goSelectShop() {
      const redirect = this.$route.path === '/select-shop' ? '/' : this.$route.fullPath
      this.$router.push({ path: '/select-shop', query: { redirect } }).catch(() => {})
    },
    copyVersionInfo() {
      const text = [
        `frontend.commit=${this.frontendBuild.commit}`,
        `frontend.buildTime=${this.frontendBuild.buildTime}`,
        `frontend.todoQuickApproveEnabled=${this.frontendBuild.todoQuickApproveEnabled}`,
        `frontend.todoBatchApproveEnabled=${this.frontendBuild.todoBatchApproveEnabled}`,
        `system.commit=${this.systemBuild.commit}`,
        `system.buildTime=${this.systemBuild.buildTime}`,
        `system.version=${this.systemBuild.version}`
      ].join('\n')
      const copied = navigator.clipboard && window.isSecureContext
        ? navigator.clipboard.writeText(text)
        : this.copyVersionInfoFallback(text)
      Promise.resolve(copied).then(() => this.$modal.msgSuccess('版本信息已复制'))
        .catch(() => this.$modal.msgError('复制失败，请手工选择版本信息'))
    },
    copyVersionInfoFallback(text) {
      const input = document.createElement('textarea')
      input.value = text
      input.setAttribute('readonly', 'readonly')
      input.style.position = 'fixed'
      input.style.opacity = '0'
      document.body.appendChild(input)
      input.select()
      const copied = document.execCommand('copy')
      document.body.removeChild(input)
      return copied ? Promise.resolve() : Promise.reject(new Error('copy unavailable'))
    },
    logout() {
      this.$confirm('确定注销并退出系统吗？', '提示', {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'warning'
      }).then(() => {
        this.$store.dispatch('LogOut').then(() => {
          location.href = '/index'
        })
      }).catch(() => {})
    }
  }
}
</script>

<style lang="scss" scoped>
.navbar {
  min-height: 96px;
  position: relative;
  background: var(--erp-surface, #fffdf9);
  box-shadow: 0 2px 12px rgba(55, 50, 44, 0.045);
  box-sizing: border-box;

  .hamburger-container {
    line-height: 52px;
    height: 52px;
    cursor: pointer;
    transition: background .3s;
    -webkit-tap-highlight-color:transparent;
    display: flex;
    align-items: center;
    flex-shrink: 0;
    margin-right: 6px;

    &:hover {
      background: rgba(37, 36, 33, 0.06);
    }
  }
}

.top-toolbar {
  position: relative;
  isolation: isolate;
  overflow: hidden;
  height: 54px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 16px 0 18px;
  color: var(--erp-text, #242320);
  background: rgba(255, 253, 249, 0.98);
  background-image: linear-gradient(90deg, #fffdf9 0%, #faf7f1 50%, #fffdf9 100%);
  border-bottom: 1px solid var(--erp-border, #ded9d0);
}

.top-toolbar::after {
  position: absolute;
  z-index: -1;
  inset: 0;
  pointer-events: none;
  content: "";
  background-image:
    linear-gradient(rgba(97, 88, 78, 0.025) 1px, transparent 1px),
    linear-gradient(90deg, rgba(97, 88, 78, 0.025) 1px, transparent 1px);
  background-size: 30px 30px;
  opacity: 0.8;
}

.toolbar-left {
  flex: 1;
  min-width: 0;
  display: flex;
  align-items: center;
  gap: 14px;
}

.brand-link {
  min-width: 188px;
  display: inline-flex;
  align-items: center;
  gap: 10px;
  color: var(--erp-text, #242320);
  font-weight: 700;
  white-space: nowrap;
}

.brand-mark {
  width: 32px;
  height: 32px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  color: var(--erp-text, #242320);
  background: transparent;
}

.brand-mark svg {
  width: 28px;
  height: 28px;
  fill: currentColor;
}

.brand-title {
  max-width: 190px;
  display: flex;
  flex-direction: column;
  gap: 2px;
  overflow: hidden;
  text-overflow: ellipsis;
}

.brand-title strong {
  color: var(--erp-text, #242320);
  font-size: 17px;
  font-weight: 800;
  line-height: 1;
  letter-spacing: -0.025em;
}

.brand-title small {
  color: var(--erp-text-muted, #9a958e);
  font-size: 7px;
  font-weight: 650;
  line-height: 1.2;
  letter-spacing: 0.14em;
}

.breadcrumb-container {
  min-width: 0;
  flex-shrink: 1;
}

.breadcrumb-container {
  ::v-deep .app-breadcrumb.el-breadcrumb {
    line-height: 54px;
    font-size: 13px;
  }

  ::v-deep .el-breadcrumb__inner,
  ::v-deep .el-breadcrumb__separator {
    color: var(--erp-text-muted, #9a958e) !important;
  }

  ::v-deep .el-breadcrumb__inner a,
  ::v-deep .el-breadcrumb__inner.is-link {
    color: var(--erp-text-secondary, #78736d) !important;
    font-weight: 600;
  }

  ::v-deep .no-redirect {
    color: var(--erp-text-muted, #9a958e) !important;
  }
}

.top-menu-row {
  height: 42px;
  display: flex;
  align-items: center;
  padding: 0 14px;
  background: var(--erp-surface, #fffdf9);
  background-image: linear-gradient(180deg, #fffdf9 0%, #faf7f2 100%);
  border-bottom: 1px solid var(--erp-border, #ded9d0);
}

.topbar-container {
  flex: 1;
  min-width: 0;
  height: 42px;
}

.right-menu {
  flex: 0 0 auto;
  height: 54px;
  display: flex;
  align-items: center;
  gap: 8px;
  margin-left: 16px;

  &:focus {
    outline: none;
  }

  .right-menu-item {
    height: 32px;
    min-width: 32px;
    display: inline-flex;
    align-items: center;
    justify-content: center;
    padding: 0 9px;
    border: 1px solid var(--erp-border-soft, #ebe6de);
    border-radius: 10px;
    color: var(--erp-text-secondary, #78736d);
    background: rgba(248, 245, 240, 0.74);
    box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.7);
    font-size: 16px;
    vertical-align: middle;

    &.hover-effect {
      cursor: pointer;
      transition:
        background var(--motion-duration-fast) var(--motion-ease-standard),
        border-color var(--motion-duration-fast) var(--motion-ease-standard),
        color var(--motion-duration-fast) var(--motion-ease-standard);

      &:hover {
        color: var(--erp-text, #242320);
        background: var(--erp-primary-soft, #eeebe5);
        border-color: var(--erp-border-strong, #c9c3ba);
      }
    }
  }

  .header-search {
    background: transparent;
    border-color: transparent;
    padding: 0 4px;
  }

  .avatar-container {
    padding: 0 9px;

    .avatar-wrapper {
      display: inline-flex;
      align-items: center;
      gap: 7px;
      cursor: pointer;

      .user-avatar {
        width: 24px;
        height: 24px;
        border-radius: 7px;
        border: 1px solid var(--erp-border, #ded9d0);
      }

      .user-nickname{
        max-width: 90px;
        overflow: hidden;
        text-overflow: ellipsis;
        white-space: nowrap;
        font-size: 13px;
        font-weight: 600;
        color: var(--erp-text, #242320);
      }
    }
  }

  .shop-switch {
    gap: 6px;
    font-size: 13px;
  }

  .shop-icon {
    font-size: 15px;
  }

  .shop-name {
    max-width: 130px;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
    color: var(--erp-text-secondary, #78736d);
    font-size: 13px;
  }
}

.version-grid {
  display: grid;
  grid-template-columns: 132px minmax(0, 1fr);
  gap: 12px 16px;
  margin-top: 16px;
  align-items: start;
}

.version-label {
  color: #64748b;
  font-weight: 600;
}

.version-grid code,
.version-grid span {
  overflow-wrap: anywhere;
}

@media screen and (max-width: 991px) {
  .navbar {
    min-height: 52px;
  }

  .top-toolbar {
    height: 52px;
    padding: 0 12px;
    color: #ffffff;
    background: #123a2f;
    background-image: linear-gradient(105deg, #123a2f 0%, #0b6b53 52%, #123a2f 100%);
    border-bottom-color: rgba(255, 255, 255, 0.08);
  }

  .top-toolbar::after {
    background: linear-gradient(112deg, transparent 12%, rgba(255, 255, 255, 0.055) 46%, transparent 72%);
    opacity: 1;
  }

  .toolbar-left {
    gap: 6px;
  }

  .brand-link {
    min-width: 0;
    color: #ffffff;
  }

  .brand-mark {
    width: 28px;
    height: 28px;
    border: 1px solid rgba(255, 255, 255, 0.58);
    border-radius: 9px;
    color: #075441;
    background: linear-gradient(145deg, #f5fbf8 0%, #cae4d9 100%);
    box-shadow: 0 6px 16px rgba(3, 34, 26, 0.2), inset 0 1px 0 rgba(255, 255, 255, 0.72);
    font-size: 12px;
    font-weight: 800;
  }

  .brand-title {
    max-width: 46vw;
    display: block;
    color: #ffffff;
    font-size: 15px;
    font-weight: 700;
  }

  .right-menu {
    height: 52px;
    gap: 6px;
    margin-left: 8px;

    .right-menu-item {
      height: 30px;
      min-width: 30px;
      padding: 0 7px;
      color: #eaf3ef;
      background: rgba(255, 255, 255, 0.055);
      border-color: rgba(255, 255, 255, 0.14);
    }

    .avatar-container .avatar-wrapper .user-nickname {
      display: none;
    }

    .avatar-container .avatar-wrapper .user-avatar {
      border-color: rgba(255, 255, 255, 0.2);
    }
  }
}
</style>
