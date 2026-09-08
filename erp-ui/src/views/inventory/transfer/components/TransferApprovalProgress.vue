<template>
  <div class="transfer-approval-progress">
    <div v-if="error" class="approval-error">
      <el-alert title="审批进度加载失败" type="warning" show-icon :closable="false" />
      <el-button type="text" icon="el-icon-refresh" @click="$emit('retry')">重试</el-button>
    </div>
    <el-skeleton v-else-if="loading" :rows="3" animated />
    <el-empty
      v-else-if="!track || !track.rounds || !track.rounds.length"
      description="尚未提交审批"
      :image-size="64"
    />
    <template v-else>
      <el-alert
        :title="track.summaryText || '审批进度'"
        :type="trackAlertType"
        show-icon
        :closable="false"
        class="approval-summary-alert"
      />
      <el-alert
        v-if="track.traceCompleteness === 'partial'"
        title="部分历史审批数据不完整，以已展示信息为准"
        type="warning"
        show-icon
        :closable="false"
        class="approval-partial-alert"
      />
      <el-collapse v-model="activeRounds">
        <el-collapse-item
          v-for="round in newestFirstRounds"
          :key="round.roundNo"
          :name="round.roundNo"
          :title="roundTitle(round)"
        >
          <el-timeline v-if="round.nodes && round.nodes.length">
            <el-timeline-item
              v-for="node in round.nodes"
              :key="round.roundNo + '-' + node.nodeOrder"
              :type="nodeType(node.state)"
              :timestamp="node.handledAt || ''"
              :class="'approval-node approval-node--' + node.state"
            >
              <div class="approval-node-title">
                <strong>{{ node.nodeName || '审批节点' }}</strong>
                <el-tag :type="nodeTagType(node.state)" size="mini">{{ nodeStatusText(node.state) }}</el-tag>
              </div>
              <div v-if="node.postName" class="approval-node-line">审批岗位：{{ node.postName }}</div>
              <div v-if="node.candidateDisplayNames && node.candidateDisplayNames.length" class="approval-node-line">
                候选审批人：{{ node.candidateDisplayNames.join('、') }}
              </div>
              <div v-if="node.actualApproverDisplayName" class="approval-node-line">
                实际审批人：{{ node.actualApproverDisplayName }}
              </div>
              <div v-if="node.approvalModeText" class="approval-node-line">审批方式：{{ node.approvalModeText }}</div>
              <div v-if="node.comment" class="approval-node-line approval-comment">审批意见：{{ node.comment }}</div>
            </el-timeline-item>
          </el-timeline>
          <el-empty v-else description="本轮无人工审批节点" :image-size="48" />
        </el-collapse-item>
      </el-collapse>
    </template>
  </div>
</template>

<script>
export default {
  name: "TransferApprovalProgress",
  props: {
    track: { type: Object, default: null },
    loading: { type: Boolean, default: false },
    error: { type: Boolean, default: false }
  },
  data() {
    return { activeRounds: [] }
  },
  computed: {
    newestFirstRounds() {
      return ((this.track && this.track.rounds) || []).slice().sort((a, b) => Number(b.roundNo || 0) - Number(a.roundNo || 0))
    },
    trackAlertType() {
      const state = this.track && this.track.state
      if (state === "approved" || state === "auto_approved") return "success"
      if (state === "rejected" || state === "partial") return "warning"
      if (state === "cancelled") return "info"
      return "info"
    }
  },
  watch: {
    newestFirstRounds: {
      immediate: true,
      handler(rounds) {
        this.activeRounds = rounds.length ? [rounds[0].roundNo] : []
      }
    }
  },
  methods: {
    roundTitle(round) {
      return `第 ${round.roundNo} 轮 · ${this.roundStatusText(round.status)}`
    },
    roundStatusText(status) {
      const map = {
        in_progress: "审批中",
        approved: "已通过",
        rejected: "已驳回",
        cancelled: "已终止",
        closed: "已关闭",
        partial: "数据不完整"
      }
      return map[status] || "审批记录"
    },
    nodeStatusText(state) {
      const map = {
        approved: "已通过",
        current: "当前节点",
        waiting: "等待中",
        rejected: "已驳回",
        terminated: "已终止",
        auto_approved: "自动通过",
        partial: "数据不完整"
      }
      return map[state] || "待确认"
    },
    nodeType(state) {
      const map = { approved: "success", current: "primary", rejected: "danger", auto_approved: "success" }
      return map[state] || "info"
    },
    nodeTagType(state) {
      const map = { approved: "success", current: "", waiting: "info", rejected: "danger", terminated: "info", auto_approved: "success", partial: "warning" }
      return map[state] || "info"
    }
  }
}
</script>

<style lang="scss" scoped>
.approval-error {
  position: relative;
}

.approval-error .el-button {
  position: absolute;
  top: 5px;
  right: 12px;
  z-index: 1;
}

.approval-summary-alert,
.approval-partial-alert {
  margin-bottom: 12px;
}

.approval-node-title {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
  color: #303133;
}

.approval-node-line {
  margin-top: 5px;
  color: #606266;
  font-size: 13px;
  line-height: 1.6;
  overflow-wrap: anywhere;
}

.approval-comment {
  padding: 7px 10px;
  border-radius: 4px;
  background: #f5f7fa;
}
</style>
