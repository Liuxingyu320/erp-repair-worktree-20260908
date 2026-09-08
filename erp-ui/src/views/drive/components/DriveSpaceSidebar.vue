<template>
  <aside class="drive-sidebar" aria-label="云盘空间导航">
    <div class="drive-sidebar__heading">
      <span class="drive-sidebar__heading-icon" aria-hidden="true"><i class="el-icon-collection" /></span>
      <span class="drive-sidebar__heading-copy">
        <span class="drive-sidebar__eyebrow">企业云盘</span>
        <strong>文件空间</strong>
      </span>
      <span class="drive-sidebar__count" :aria-label="`${spaces.length} 个文件空间`">{{ spaces.length }}</span>
    </div>

    <nav class="drive-sidebar__nav" aria-label="文件空间">
      <button
        v-for="space in spaces"
        :key="space.spaceId"
        type="button"
        class="drive-sidebar__item"
        :class="{ 'is-active': activeView === 'files' && Number(activeSpaceId) === Number(space.spaceId), 'is-over-quota': space.overQuota }"
        :aria-current="activeView === 'files' && Number(activeSpaceId) === Number(space.spaceId) ? 'page' : null"
        :disabled="loading"
        @click="$emit('select-space', space.spaceId)"
      >
        <span class="drive-sidebar__icon" aria-hidden="true">
          <i :class="spaceIcon(space.spaceType)" />
        </span>
        <span class="drive-sidebar__copy">
          <span class="drive-sidebar__label">{{ space.spaceName || spaceLabel(space.spaceType) }}</span>
          <span
            class="drive-sidebar__capacity"
            :aria-label="capacityLabel(space)"
            :title="capacityLabel(space)"
          >
            {{ formatBytes(space.usedBytes) }} / {{ formatBytes(space.quotaBytes) }}
          </span>
          <span class="drive-sidebar__usage" aria-hidden="true">
            <span :style="{ width: usagePercent(space) + '%' }" />
          </span>
          <span v-if="space.quotaSourceLabel" class="drive-sidebar__source">{{ space.quotaSourceLabel }}</span>
          <span v-if="space.overQuota" class="drive-sidebar__over">需清理 {{ formatBytes(space.overQuotaBytes) }}</span>
          <span v-if="space.writeBlockedMessage" class="drive-sidebar__blocked" :title="space.writeBlockedMessage">{{ space.writeBlockedMessage }}</span>
        </span>
      </button>
    </nav>

    <div class="drive-sidebar__divider" />

    <span class="drive-sidebar__section-label">快捷访问</span>
    <nav class="drive-sidebar__nav" aria-label="快捷视图">
      <button
        v-for="item in specialViews"
        :key="item.view"
        type="button"
        class="drive-sidebar__item drive-sidebar__item--compact"
        :class="{ 'is-active': activeView === item.view }"
        :aria-current="activeView === item.view ? 'page' : null"
        :disabled="loading || !activeSpaceId || isSpecialDisabled(item)"
        @click="$emit('select-view', item.view)"
      >
        <span class="drive-sidebar__icon" aria-hidden="true"><i :class="item.icon" /></span>
        <span class="drive-sidebar__label">{{ item.label }}</span>
      </button>
    </nav>
  </aside>
</template>

<script>
const { formatBytes } = require('../driveState')

export default {
  name: 'DriveSpaceSidebar',
  props: {
    spaces: { type: Array, default: () => [] },
    activeSpaceId: { type: [Number, String], default: null },
    activeView: { type: String, default: 'files' },
    loading: { type: Boolean, default: false }
  },
  data() {
    return {
      specialViews: [
        { view: 'recent', label: '最近使用', icon: 'el-icon-time' },
        { view: 'trash', label: '回收站', icon: 'el-icon-delete' }
      ]
    }
  },
  computed: {
    activeSpace() {
      return this.spaces.find(space => Number(space.spaceId) === Number(this.activeSpaceId)) || null
    }
  },
  methods: {
    formatBytes,
    spaceLabel(spaceType) {
      // 数据类型继续兼容 DEPARTMENT，原“部门盘”在用户界面统一称为组织盘。
      return {
        PERSONAL: '我的文件',
        COMPANY: '公司公共盘',
        DEPARTMENT: '组织盘'
      }[spaceType] || '文件空间'
    },
    spaceIcon(spaceType) {
      return {
        PERSONAL: 'el-icon-user',
        COMPANY: 'el-icon-office-building',
        DEPARTMENT: 'el-icon-folder-opened'
      }[spaceType] || 'el-icon-folder'
    },
    capacityLabel(space) {
      return `${space.spaceName || this.spaceLabel(space.spaceType)}：已使用 ${Number(space.usedBytes || 0)} 字节，额度 ${Number(space.quotaBytes || 0)} 字节`
    },
    usagePercent(space) {
      const quota = Number(space && space.quotaBytes || 0)
      const used = Number(space && space.usedBytes || 0)
      if (quota <= 0 || used <= 0) return 0
      return Math.min(100, Math.max(0, Math.round(used / quota * 100)))
    },
    isSpecialDisabled(item) {
      return item.view === 'trash' && (!this.activeSpace || !(this.activeSpace.canCleanup == null ? this.activeSpace.canWrite : this.activeSpace.canCleanup))
    }
  }
}
</script>

<style lang="scss" scoped>
.drive-sidebar {
  width: 248px;
  flex: 0 0 248px;
  min-height: 540px;
  padding: 20px 14px;
  border: 1px solid var(--erp-border, #dde2de);
  border-radius: var(--erp-radius-lg, 16px);
  background: var(--erp-surface, #fff);
  box-shadow: var(--erp-shadow-card, 0 8px 22px rgba(23, 33, 29, 0.055));
}

.drive-sidebar__heading {
  min-height: 58px;
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 0 8px 17px;
  color: #1e2d45;

  strong { font-size: 17px; line-height: 1.2; }
}

.drive-sidebar__heading-icon {
  width: 38px;
  height: 38px;
  flex: 0 0 38px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border-radius: 12px;
  background: var(--erp-primary-soft, #e7f2ed);
  color: var(--erp-primary, #0b6b53);
  font-size: 19px;
}

.drive-sidebar__heading-copy {
  min-width: 0;
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.drive-sidebar__count {
  min-width: 24px;
  height: 24px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border-radius: 8px;
  background: #edf0f7;
  color: #75849b;
  font-size: 11px;
  font-weight: 700;
}

.drive-sidebar__eyebrow {
  color: #8a96a8;
  font-size: 10px;
  font-weight: 600;
  letter-spacing: 0.08em;
}

.drive-sidebar__nav {
  display: flex;
  flex-direction: column;
  gap: 7px;
}

.drive-sidebar__item {
  width: 100%;
  min-height: 62px;
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 11px 10px;
  border: 1px solid transparent;
  border-radius: 13px;
  background: transparent;
  color: #55647a;
  font: inherit;
  text-align: left;
  cursor: pointer;
  transition: border-color var(--motion-duration-fast) var(--motion-ease-standard), background-color var(--motion-duration-fast) var(--motion-ease-standard), color var(--motion-duration-fast) var(--motion-ease-standard), box-shadow var(--motion-duration-fast) var(--motion-ease-standard);

  &:hover:not(:disabled) {
    border-color: #c6ddd3;
    background: #f3f7f5;
    color: var(--erp-primary, #0b6b53);
  }
  &:focus-visible { outline: 3px solid rgba(11, 107, 83, 0.22); outline-offset: 2px; }
  &:disabled { cursor: not-allowed; opacity: 0.55; }
  &.is-active {
    border-color: #9fbfb2;
    background: var(--erp-primary-soft, #e7f2ed);
    color: var(--erp-primary-hover, #075441);
    font-weight: 600;
    box-shadow: inset 3px 0 0 var(--erp-primary, #0b6b53);

    .drive-sidebar__icon { background: #fff; color: var(--erp-primary, #0b6b53); box-shadow: 0 5px 12px rgba(23, 33, 29, 0.08); }
    .drive-sidebar__usage > span { background: var(--erp-primary, #0b6b53); }
  }
  &.is-over-quota { box-shadow: inset 3px 0 #e25353; }
}

.drive-sidebar__item--compact { min-height: 44px; padding-top: 8px; padding-bottom: 8px; }
.drive-sidebar__icon {
  width: 34px;
  height: 34px;
  flex: 0 0 34px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border-radius: 10px;
  background: #f0f3f8;
  color: #6d7d93;
  text-align: center;
  font-size: 16px;
  transition: background-color 0.18s ease, color 0.18s ease, box-shadow 0.18s ease;
}
.drive-sidebar__copy { min-width: 0; display: flex; flex: 1; flex-direction: column; gap: 4px; }
.drive-sidebar__label { overflow: hidden; font-size: 13px; text-overflow: ellipsis; white-space: nowrap; }
.drive-sidebar__capacity { color: #96a2b1; font-size: 11px; font-weight: 400; }
.drive-sidebar__usage {
  width: 100%;
  height: 3px;
  display: block;
  overflow: hidden;
  border-radius: 999px;
  background: #e7ebf2;

  > span { height: 100%; display: block; border-radius: inherit; background: #aab5c4; }
}
.drive-sidebar__source { color: #6f86a1; font-size: 10px; font-weight: 400; }
.drive-sidebar__over { color: #d54848; font-size: 10px; font-weight: 600; }
.drive-sidebar__blocked { color: #aa6d10; font-size: 10px; font-weight: 600; line-height: 1.35; }
.drive-sidebar__divider { height: 1px; margin: 18px 8px 14px; background: #edf0f5; }
.drive-sidebar__section-label { display: block; padding: 0 10px 8px; color: #9aa5b5; font-size: 10px; font-weight: 600; letter-spacing: 0.08em; }

@media (max-width: 1280px) {
  .drive-sidebar { width: 230px; flex-basis: 230px; }
}
</style>
