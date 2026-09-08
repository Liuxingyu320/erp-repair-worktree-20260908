<template>
  <mobile-hr-shell
    title="人事工作台"
    :loading="loading"
    :error="error"
    @retry="loadSummary"
  >
    <section class="summary-grid" aria-label="今日入职概览">
      <article class="summary-tile">
        <span>今日到岗</span>
        <strong>{{ summary.todayArrivalCount }}</strong>
      </article>
      <article class="summary-tile">
        <span>待确认</span>
        <strong>{{ summary.pendingConfirmCount }}</strong>
      </article>
    </section>

    <section class="task-section" aria-labelledby="today-task-heading">
      <div class="section-heading">
        <div>
          <p>今日待办</p>
          <h2 id="today-task-heading">需要跟进的入职事项</h2>
        </div>
        <span>{{ todayTasks.length }} 项</span>
      </div>

      <div v-if="!todayTasks.length" class="empty-state">
        <strong>今天暂无待办</strong>
        <p>新的到岗或确认任务会显示在这里。</p>
      </div>
      <ul v-else class="task-list" role="list">
        <li v-for="task in todayTasks" :key="task.onboardingId" role="listitem">
          <button
            v-if="canQuery"
            class="task-row"
            type="button"
            :aria-label="taskAriaLabel(task)"
            @click="openTask(task)"
          >
            <span class="task-main">
              <strong>{{ task.employeeName || "未命名员工" }}</strong>
              <span class="action-badge">{{ currentActionLabel(task.currentAction) }}</span>
            </span>
            <span class="task-meta">
              <span>{{ task.phoneNumberMasked || "手机未填写" }}</span>
              <span>{{ task.positionName || "岗位未填写" }}</span>
            </span>
            <span class="task-meta">
              <span>{{ organizationLabel(task) }}</span>
              <span>预计 {{ task.expectedEntryDate || "待定" }}</span>
            </span>
          </button>
          <div v-else class="task-row is-readonly" :aria-label="taskAriaLabel(task)">
            <span class="task-main">
              <strong>{{ task.employeeName || "未命名员工" }}</strong>
              <span class="action-badge">{{ currentActionLabel(task.currentAction) }}</span>
            </span>
            <span class="task-meta"><span>{{ task.phoneNumberMasked || "手机未填写" }}</span><span>{{ task.positionName || "岗位未填写" }}</span></span>
            <span class="task-meta"><span>{{ organizationLabel(task) }}</span><span>预计 {{ task.expectedEntryDate || "待定" }}</span></span>
            <small>暂无详情查看权限，仅展示待办信息</small>
          </div>
        </li>
      </ul>
    </section>

    <template #footer>
      <div class="workbench-actions">
        <button v-if="canAdd" class="primary-action" type="button" @click="openCreate">
          新建入职
        </button>
        <button v-if="canList" class="secondary-action" type="button" @click="openAll">
          全部入职
        </button>
      </div>
    </template>
  </mobile-hr-shell>
</template>

<script>
import { getHrOnboardingSummary } from "@/api/hr/onboarding"
import { checkPermi } from "@/utils/permission"
import MobileHrShell from "./components/MobileHrShell"
import { mobileHrErrorMessage } from "./mobileHrError"

const EMPTY_SUMMARY = () => ({
  todayArrivalCount: 0,
  pendingConfirmCount: 0,
  todayTasks: []
})

const ACTION_LABELS = {
  EDIT: "补充资料",
  MARK_READY: "标记就绪",
  RETURN_TO_DRAFT: "退回草稿",
  CONFIRM: "确认入职",
  CANCEL: "取消入职",
  RESTORE: "恢复入职"
}

export default {
  name: "MobileHrWorkbench",
  components: { MobileHrShell },
  data() {
    return {
      summary: EMPTY_SUMMARY(),
      loading: false,
      error: "",
      requestSequence: 0
    }
  },
  computed: {
    todayTasks() {
      return Array.isArray(this.summary.todayTasks) ? this.summary.todayTasks : []
    },
    canAdd() {
      return checkPermi(["hr:onboarding:add"])
    },
    canList() {
      return checkPermi(["hr:onboarding:list"])
    },
    canQuery() {
      return checkPermi(["hr:onboarding:query"])
    }
  },
  created() {
    this.loadSummary()
  },
  beforeDestroy() {
    this.requestSequence += 1
  },
  methods: {
    loadSummary() {
      const sequence = ++this.requestSequence
      this.loading = true
      this.error = ""
      return getHrOnboardingSummary()
        .then(response => {
          if (sequence !== this.requestSequence) return
          const data = response && response.data ? response.data : {}
          this.summary = {
            todayArrivalCount: Number(data.todayArrivalCount) || 0,
            pendingConfirmCount: Number(data.pendingConfirmCount) || 0,
            todayTasks: Array.isArray(data.todayTasks) ? data.todayTasks : []
          }
        })
        .catch(error => {
          if (sequence !== this.requestSequence) return
          this.summary = EMPTY_SUMMARY()
          this.error = mobileHrErrorMessage(error, "暂时无法获取今日人事待办")
        })
        .finally(() => {
          if (sequence === this.requestSequence) this.loading = false
        })
    },
    currentActionLabel(action) {
      return ACTION_LABELS[action] || "查看详情"
    },
    organizationLabel(task) {
      return task.storeName || task.deptLevel3Name || task.deptLevel2Name || task.deptLevel1Name || task.companyName || "组织未填写"
    },
    taskAriaLabel(task) {
      return [
        task.employeeName || "未命名员工",
        task.phoneNumberMasked || "手机未填写",
        this.organizationLabel(task),
        this.currentActionLabel(task.currentAction)
      ].join("，")
    },
    openTask(task) {
      if (!this.canQuery || !task || !/^\d+$/.test(String(task.onboardingId || ""))) return
      this.$router.push({ path: `/mobile/hr/onboarding/${task.onboardingId}` }).catch(() => {})
    },
    openCreate() {
      if (!this.canAdd) return
      this.$router.push({ path: "/mobile/hr/onboarding/create" }).catch(() => {})
    },
    openAll() {
      if (!this.canList) return
      this.$router.push({ path: "/mobile/hr/onboarding" }).catch(() => {})
    }
  }
}
</script>

<style lang="scss" scoped>
.summary-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}

.summary-tile {
  min-height: 112px;
  padding: 18px;
  box-sizing: border-box;
  border: 1px solid rgba(31, 114, 106, 0.1);
  border-radius: 20px;
  background: linear-gradient(150deg, #fff, #edf8f6);
}

.summary-tile span,
.summary-tile strong { display: block; }
.summary-tile span { color: #66807d; font-size: 14px; }
.summary-tile strong { margin-top: 9px; color: #126c66; font-size: 34px; line-height: 1; }

.task-section { margin-top: 20px; }
.section-heading,
.task-main,
.task-meta {
  display: flex;
  align-items: center;
  justify-content: space-between;
  min-width: 0;
  gap: 12px;
}
.section-heading { margin-bottom: 12px; }
.section-heading p { margin: 0; color: #16756f; font-size: 13px; font-weight: 700; }
.section-heading h2 { margin: 3px 0 0; font-size: 18px; }
.section-heading > span { flex: 0 0 auto; color: #708582; font-size: 13px; }

.task-list { padding: 0; margin: 0; overflow: hidden; list-style: none; border: 1px solid #e2ecea; border-radius: 20px; background: #fff; }
.task-list li + li { border-top: 1px solid #e8efee; }
.task-row { display: block; width: 100%; min-height: 44px; padding: 16px; color: #18343b; text-align: left; border: 0; background: transparent; }
.task-row:disabled { cursor: default; opacity: 0.76; }
.task-row.is-readonly { box-sizing: border-box; }
.task-row:not(:disabled):active { background: #eff7f5; }
.task-main strong { min-width: 0; font-size: 17px; overflow-wrap: anywhere; }
.action-badge { flex: 0 0 auto; padding: 4px 9px; color: #126c66; font-size: 12px; font-weight: 700; border-radius: 999px; background: #dff2ef; }
.task-meta { margin-top: 7px; color: #657c79; font-size: 13px; }
.task-meta span { min-width: 0; overflow-wrap: anywhere; }
.task-meta span:last-child { text-align: right; }
.task-row small { display: block; margin-top: 9px; color: #8a7773; }

.empty-state { padding: 32px 20px; color: #657c79; text-align: center; border: 1px solid #e2ecea; border-radius: 20px; background: #fff; }
.empty-state strong, .empty-state p { display: block; }
.empty-state p { margin: 7px 0 0; font-size: 14px; }

.workbench-actions { display: flex; gap: 10px; }
.workbench-actions button { flex: 1; min-height: 48px; padding: 0 14px; font-weight: 700; border-radius: 15px; }
.primary-action { color: #fff; border: 1px solid #16756f; background: #16756f; }
.secondary-action { color: #126c66; border: 1px solid #b9dcd7; background: #edf8f6; }
</style>
