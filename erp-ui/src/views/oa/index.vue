<template>
  <div class="app-container oa-workspace-page oa-overview-page">
    <section class="oa-page-hero">
      <div class="oa-hero__copy">
        <span class="oa-hero__eyebrow">办公协同</span>
        <div class="oa-hero__title-row">
          <span class="oa-hero__icon"><i class="el-icon-s-grid" /></span>
          <div>
            <h1>OA 协同工作台</h1>
            <p>集中处理考勤薪资、采购申请、固定资产与合同签署，让日常协同更清晰、更高效。</p>
          </div>
        </div>
      </div>
      <div class="oa-hero__aside">
        <span>当前可用模块</span>
        <strong>{{ visibleModules.length }} 个</strong>
        <small>入口会按当前账号权限自动展示</small>
      </div>
    </section>

    <el-alert
      title="门店要货请前往“仓储作业 / 调拨作业”，OA 采购申请用于行政采购与费用类协同。"
      type="info"
      :closable="false"
      show-icon
      class="overview-tip"
    />

    <section v-if="visibleModules.length" class="oa-module-grid" aria-label="OA 功能入口">
      <button
        v-for="module in visibleModules"
        :key="module.path"
        type="button"
        class="oa-module-card"
        :class="module.tone"
        :aria-label="`进入${module.title}`"
        @click="openModule(module)"
      >
        <span class="oa-module-card__icon"><i :class="module.icon" /></span>
        <h2>{{ module.title }}</h2>
        <p>{{ module.description }}</p>
        <span class="oa-module-card__link">进入模块 <i class="el-icon-right" /></span>
      </button>
    </section>

    <div v-else class="oa-empty-panel">
      <el-empty description="当前账号暂无 OA 功能权限" :image-size="82" />
    </div>
  </div>
</template>

<script>
export default {
  name: "OaIndex",
  data() {
    return {
      modules: [
        {
          title: "考勤中心",
          description: "管理班次、考勤地点与周排班，员工按已发布排班现场打卡。",
          icon: "el-icon-time",
          tone: "oa-module-card--cyan",
          path: "/oa/attendance-v2",
          permissions: ["oa:attendance:center:list"]
        },
        {
          title: "工资管理",
          description: "按月份查看工资结果，处理计算、参数与导出事项。",
          icon: "el-icon-money",
          tone: "oa-module-card--green",
          path: "/oa/salary",
          permissions: ["oa:salary:query", "oa:salary:list"]
        },
        {
          title: "采购申请",
          description: "创建行政采购草稿，并持续跟踪统一审批进度。",
          icon: "el-icon-shopping-cart-full",
          tone: "oa-module-card--amber",
          path: "/oa/purchase",
          permissions: ["oa:purchase:list", "oa:purchase:add"]
        },
        {
          title: "固定资产配置",
          description: "维护门店固定资产明细、申报比例和可用额度。",
          icon: "el-icon-coin",
          tone: "oa-module-card--violet",
          path: "/oa/fixed-asset/config",
          permissions: ["oa:fixedAsset:config:list", "oa:fixedAsset:config:query"]
        },
        {
          title: "固定资产维修",
          description: "上报资产损坏，核对额度并跟踪仓库补货结果。",
          icon: "el-icon-s-tools",
          tone: "oa-module-card--rose",
          path: "/oa/fixed-asset/repair",
          permissions: ["oa:fixedAsset:repair:list", "oa:fixedAsset:repair:add"]
        },
        {
          title: "劳动合同",
          description: "查询历史劳动合同、签署文件与可信验真信息。",
          icon: "el-icon-document-checked",
          path: "/oa/labor-contract",
          permissions: ["oa:laborContract:list", "oa:laborContract:query"]
        },
        {
          title: "合同签约中心",
          description: "集中处理补资料、发送、盖章和签约异常任务。",
          icon: "el-icon-s-claim",
          tone: "oa-module-card--cyan",
          path: "/oa/sign-task",
          permissions: ["oa:signTask:list", "oa:signTask:query"]
        },
        {
          title: "签约资料维护",
          description: "管理签约包、签约方案、文件模板及公司印章。",
          icon: "el-icon-collection",
          tone: "oa-module-card--green",
          path: "/oa/sign-package",
          permissions: ["oa:signPackage:list", "oa:signPackage:query"]
        }
      ]
    }
  },
  computed: {
    visibleModules() {
      if (!this.$auth || typeof this.$auth.hasPermi !== "function") {
        return []
      }
      try {
        return this.modules.filter(module =>
          module.permissions.some(permission => this.$auth.hasPermi(permission) === true)
        )
      } catch (error) {
        return []
      }
    }
  },
  methods: {
    openModule(module) {
      this.$router.push(module.path).catch(() => {})
    }
  }
}
</script>

<style scoped>
.overview-tip {
  margin-bottom: 16px;
}
</style>
