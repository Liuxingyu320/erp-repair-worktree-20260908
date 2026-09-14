<template>
  <el-dialog :title="`「${noticeTitle}」阅读情况`" :visible.sync="visible" width="760px" top="6vh" append-to-body @opened="focusSearch" @close="handleClose">
    <div class="search-card read-users-search-card">
    <el-form :model="queryParams" ref="queryForm" size="small" :inline="true">
      <el-form-item prop="searchValue">
        <el-input
          ref="searchInput"
          v-model="queryParams.searchValue"
          aria-label="筛选已读用户"
          placeholder="登录账号 / 姓名（仅用于筛选）"
          clearable
          prefix-icon="el-icon-search"
          style="width: 220px;"
          @keyup.enter.native="handleQuery"
          @clear="handleQuery"
        />
      </el-form-item>
      <el-form-item>
        <el-button type="primary" icon="el-icon-search" size="mini" @click="handleQuery">搜索</el-button>
        <el-button icon="el-icon-refresh" size="mini" @click="resetQuery">重置</el-button>
      </el-form-item>
      <el-form-item style="float: right; margin-right: 0;">
        <span class="read-stat">
          <template v-if="summaryReady">总体已读 <strong>{{ readCount }}</strong> / {{ recipientCount }} 人（{{ readRate }}）</template>
          <template v-else>总体阅读统计暂不可用</template>
        </span>
      </el-form-item>
    </el-form>
    </div>
    <div class="table-card read-users-table-card">
    <el-alert v-if="loadError" :title="loadError" type="error" :closable="false" />
    <p>筛选结果 {{ total }} 人</p>
    <el-table v-loading="loading" :data="userList" size="small" stripe height="340px">
      <el-table-column type="index" label="序号" width="55" align="center" />
      <el-table-column label="姓名" prop="nickName" align="center" :show-overflow-tooltip="true" />
      <el-table-column label="所属部门" prop="deptName" align="center" :show-overflow-tooltip="true" />
      <el-table-column label="阅读时间" prop="readTime" align="center" width="160">
        <template slot-scope="scope">
          <span>{{ parseTime(scope.row.readTime) }}</span>
        </template>
      </el-table-column>
    </el-table>
    <pagination v-show="total > 0" :total="total" :page.sync="queryParams.pageNum" :limit.sync="queryParams.pageSize" @pagination="getList" style="padding: 6px 0px;"/>
    </div>
  </el-dialog>
</template>

<script>
import { getSelectedDeptId } from "@/utils/shopContext"
const { createUiOperationScope } = require("@/utils/uiOperationScope")
import { listNoticeReadUsers } from "@/api/system/notice"

export default {
  name: "ReadUsers",
  data() {
    return {
      visible: false,
      loading: false,
      noticeId: undefined,
      noticeTitle: "",
      recipientCount: 0,
      readCount: 0,
      summaryReady: false,
      loadError: "",
      total: 0,
      userList: [],
      queryParams: {
        pageNum: 1,
        pageSize: 10,
        noticeId: undefined,
        searchValue: undefined
      }
    }
  },
  watch: { actorContextKey() { this.handleClose() } },
  created() { window.addEventListener("erp:dept-changed", this.handleClose) },
  deactivated() { this.handleClose(); this.readScope().deactivate() },
  activated() { this.readScope().activate() },
  beforeDestroy() { window.removeEventListener("erp:dept-changed", this.handleClose); this.readScope().deactivate() },
  methods: {
    readScope() {
      if (!this._readScope) this._readScope = createUiOperationScope(() => ({ actor: this.actorContextKey, dept: getSelectedDeptId() }))
      return this._readScope
    },
    open(row) {
      this.handleClose()
      this.noticeId = row.noticeId
      this.noticeTitle = row.noticeTitle
      this.recipientCount = 0
      this.queryParams.noticeId = row.noticeId
      this.queryParams.searchValue = undefined
      this.queryParams.pageNum = 1
      this.visible = true
      this.getList()
    },
    getList() {
      const params = { ...this.queryParams }, scope = this.readScope(), token = scope.begin("readers", params)
      const current = () => this.visible && scope.isCurrent(token) && String(this.noticeId) === String(params.noticeId)
      this.loading = true
      this.userList = []
      this.total = 0
      this.summaryReady = false
      this.loadError = ""
      return listNoticeReadUsers(params, { silentError: true }).then(res => {
        if (!current()) return
        this.userList = res.rows || []
        this.total = Number(res.total) || 0
        const summary = res.summary
        this.summaryReady = Boolean(summary && Number.isFinite(Number(summary.readCount)) && Number.isFinite(Number(summary.recipientCount)))
        this.readCount = this.summaryReady ? Number(summary.readCount) : 0
        this.recipientCount = this.summaryReady ? Number(summary.recipientCount) : 0
      }).catch(error => { if (current()) this.loadError = error && error.message || "阅读情况加载失败，请重试搜索" })
        .finally(() => { if (current()) this.loading = false })
    },
    handleQuery() {
      this.queryParams.pageNum = 1
      this.getList()
    },
    resetQuery() {
      this.resetForm("queryForm")
      this.handleQuery()
    },
    handleClose() {
      this.readScope().invalidate()
      this.visible = false
      this.loading = false
      this.summaryReady = false
      this.readCount = 0
      this.recipientCount = 0
      this.loadError = ""
      this.userList = []
      this.total = 0
      this.queryParams.searchValue = undefined
    },
    focusSearch() {
      this.$nextTick(() => this.$refs.searchInput && this.$refs.searchInput.focus())
    }
  },
  computed: {
    actorContextKey() {
      const store = this.$store || {}
      return String((store.getters || {}).id || "") + ":" + String(((store.state || {}).user || {}).sessionRevision || 0)
    },
    readRate() {
      if (this.recipientCount <= 0) return "无接收人"
      return `${Math.round(this.readCount * 100 / this.recipientCount)}%`
    }
  }
}
</script>

<style scoped>
.read-users-search-card {
  margin-bottom: 12px;
  padding-bottom: 6px;
}

.read-users-table-card {
  padding-bottom: 6px;
}

.read-stat {
  font-size: 13px;
  color: #606266;
  line-height: 28px;
}
.read-stat strong {
  color: var(--erp-primary, #0b6b53);
  font-size: 15px;
  margin: 0 2px;
}
</style>
