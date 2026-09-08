<template>
  <div v-loading="loading" class="unified-approval-progress">
    <el-alert
      v-if="error"
      title="统一审批轨迹加载失败"
      description="主单详情已保留，可稍后重试。"
      type="warning"
      :closable="false"
      show-icon
    >
      <el-button slot="default" type="text" size="mini" @click="$emit('retry')">重新加载</el-button>
    </el-alert>
    <el-empty v-else-if="!loading && !instance" description="暂无统一审批记录" :image-size="64" />
    <template v-else-if="instance">
      <el-descriptions :column="3" border size="small" class="approval-summary">
        <el-descriptions-item label="审批引擎">
          <el-tag type="success" size="mini">统一引擎</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="审批状态">
          <el-tag :type="statusType(instance.status)" size="mini">{{ statusLabel(instance.status) }}</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="审批轮次">{{ instance.businessRound || instance.roundNo || '-' }}</el-descriptions-item>
      </el-descriptions>

      <el-table :data="tasks" border size="small" class="approval-task-table">
        <el-table-column label="顺序" prop="nodeOrder" width="70" align="center" />
        <el-table-column label="节点" min-width="140">
          <template slot-scope="scope">{{ scope.row.nodeName || '审批节点' }}</template>
        </el-table-column>
        <el-table-column label="状态" width="110">
          <template slot-scope="scope">
            <el-tag :type="statusType(scope.row.taskStatus || scope.row.status)" size="mini">
              {{ statusLabel(scope.row.taskStatus || scope.row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="候选审批人" min-width="180">
          <template slot-scope="scope">{{ candidateNames(scope.row) }}</template>
        </el-table-column>
        <el-table-column label="实际处理人" min-width="120">
          <template slot-scope="scope">{{ scope.row.operatorName || scope.row.assigneeName || '-' }}</template>
        </el-table-column>
        <el-table-column label="完成时间" min-width="160">
          <template slot-scope="scope">{{ scope.row.completedTime || scope.row.completeTime || '-' }}</template>
        </el-table-column>
      </el-table>

      <template v-if="actions.length">
        <h4>操作记录</h4>
        <el-table :data="actions" border size="small">
          <el-table-column label="时间" prop="createTime" width="165" />
          <el-table-column label="动作" width="100">
            <template slot-scope="scope">{{ statusLabel(scope.row.actionType || scope.row.action) }}</template>
          </el-table-column>
          <el-table-column label="操作人" min-width="120">
            <template slot-scope="scope">{{ scope.row.operatorName || scope.row.operatorId || '系统' }}</template>
          </el-table-column>
          <el-table-column label="意见" min-width="220">
            <template slot-scope="scope">{{ scope.row.actionReason || scope.row.reason || '-' }}</template>
          </el-table-column>
        </el-table>
      </template>
    </template>
  </div>
</template>

<script>
import { statusLabel, statusType } from "@/views/approval/manage/components/approvalUi"

export default {
  name: "UnifiedApprovalProgress",
  props: {
    detail: { type: Object, default: null },
    loading: Boolean,
    error: Boolean
  },
  computed: {
    instance() {
      return this.detail && (this.detail.instance || this.detail) || null
    },
    tasks() {
      const value = this.detail && (this.detail.tasks || this.detail.taskList)
      return Array.isArray(value) ? value : []
    },
    candidates() {
      const value = this.detail && this.detail.candidates
      return Array.isArray(value) ? value : []
    },
    actions() {
      const value = this.detail && (this.detail.actions || this.detail.actionLogs || this.detail.history)
      return Array.isArray(value) ? value : []
    }
  },
  methods: {
    statusLabel,
    statusType,
    candidateNames(task) {
      const nested = Array.isArray(task && task.candidates) ? task.candidates : []
      const related = nested.length ? nested : this.candidates.filter(item =>
        item && String(item.taskId) === String(task && task.taskId)
      )
      const names = related.map(item => item && (
        item.candidateName || item.displayName || item.userName || item.nickName || item.candidateUserName
      )).filter(Boolean)
      if (names.length) return names.join("、")
      const fallback = task && (task.candidateUserNames || task.candidateNames)
      return Array.isArray(fallback) ? fallback.filter(Boolean).join("、") : fallback || "-"
    }
  }
}
</script>

<style lang="scss" scoped>
.unified-approval-progress {
  min-height: 72px;

  .approval-summary {
    margin-bottom: 12px;
  }

  .approval-task-table + h4 {
    margin: 18px 0 10px;
  }
}
</style>
