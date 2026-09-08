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
          已读 <strong>{{ total }}</strong> / {{ recipientCount }} 人（{{ readRate }}）
        </span>
      </el-form-item>
    </el-form>
    </div>
    <div class="table-card read-users-table-card">
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
  methods: {
    open(row) {
      this.noticeId = row.noticeId
      this.noticeTitle = row.noticeTitle
      this.recipientCount = Number(row.recipientCount) || 0
      this.queryParams.noticeId = row.noticeId
      this.queryParams.searchValue = undefined
      this.queryParams.pageNum = 1
      this.visible = true
      this.getList()
    },
    getList() {
      this.loading = true
      listNoticeReadUsers(this.queryParams).then(res => {
        this.userList = res.rows
        this.total = res.total
      }).finally(() => {
        this.loading = false
      })
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
      this.userList = []
      this.total = 0
      this.queryParams.searchValue = undefined
    },
    focusSearch() {
      this.$nextTick(() => this.$refs.searchInput && this.$refs.searchInput.focus())
    }
  },
  computed: {
    readRate() {
      if (this.recipientCount <= 0) return "0%"
      return `${Math.round(this.total * 100 / this.recipientCount)}%`
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
