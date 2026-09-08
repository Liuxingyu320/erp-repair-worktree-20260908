<template>
  <div class="app-container">
    <div class="search-card online-search-card">
    <el-form :model="queryParams" ref="queryForm" size="small" :inline="true" label-width="68px">
      <el-form-item label="登录地址" prop="ipaddr">
        <el-input
          v-model="queryParams.ipaddr"
          placeholder="请输入登录地址"
          clearable
          @keyup.enter.native="handleQuery"
        />
      </el-form-item>
      <el-form-item label="用户名称" prop="userName">
        <el-input
          v-model="queryParams.userName"
          placeholder="请输入用户名称"
          clearable
          @keyup.enter.native="handleQuery"
        />
      </el-form-item>
      <el-form-item>
        <el-button type="primary" icon="el-icon-search" size="mini" @click="handleQuery">搜索</el-button>
        <el-button icon="el-icon-refresh" size="mini" @click="resetQuery">重置</el-button>
      </el-form-item>

    </el-form>
    </div>

    <div class="table-card online-table-card">
    <el-alert
      v-if="loadError"
      class="online-session-alert"
      title="在线状态暂不可用，请稍后重试"
      type="error"
      :closable="false"
      show-icon
    />
    <el-alert
      v-if="sessionNotice"
      class="online-session-alert"
      :title="sessionNotice"
      type="warning"
      :closable="false"
      show-icon
    />
    <el-table
      v-loading="loading"
      :data="list"
      style="width: 100%;"
    >
      <el-table-column label="序号" type="index" align="center">
        <template slot-scope="scope">
          <span>{{(queryParams.pageNum - 1) * queryParams.pageSize + scope.$index + 1}}</span>
        </template>
      </el-table-column>
      <el-table-column label="会话编号" align="center" prop="tokenId" :show-overflow-tooltip="true">
        <template slot-scope="scope">
          <span>{{ maskToken(scope.row.tokenId) }}</span>
        </template>
      </el-table-column>
      <el-table-column label="登录名称" align="center" prop="userName" :show-overflow-tooltip="true" />
      <el-table-column label="主机" align="center" prop="ipaddr" :show-overflow-tooltip="true" />
      <el-table-column label="登录时间" align="center" prop="loginTime" width="180">
        <template slot-scope="scope">
          <span>{{ parseTime(scope.row.loginTime) }}</span>
        </template>
      </el-table-column>
      <el-table-column label="操作" align="center" class-name="small-padding fixed-width">
        <template slot-scope="scope">
          <el-button
            size="mini"
            type="text"
            icon="el-icon-delete"
            :disabled="scope.row.currentSession"
            :title="scope.row.currentSession ? '当前会话请使用退出登录' : '强制结束该会话'"
            @click="handleForceLogout(scope.row)"
            v-hasPermi="['monitor:online:forceLogout']"
          >{{ scope.row.currentSession ? '当前会话' : '强退' }}</el-button>
        </template>
      </el-table-column>
    </el-table>

    <pagination
      v-show="total>0"
      :total="total"
      :page.sync="queryParams.pageNum"
      :limit.sync="queryParams.pageSize"
      @pagination="getList"
    />
    </div>
  </div>
</template>

<script>
import { list, forceLogout } from "@/api/monitor/online";

export default {
  name: "Online",
  data() {
    return {
      // 遮罩层
      loading: true,
      // 总条数
      total: 0,
      // 表格数据
      list: [],
      loadError: false,
      scanTruncated: false,
      scanLimit: 0,
      invalidSessionCount: 0,
      // 查询参数
      queryParams: {
        pageNum: 1,
        pageSize: 10,
        ipaddr: undefined,
        userName: undefined
      }
    };
  },
  computed: {
    sessionNotice() {
      const notices = [];
      if (this.scanTruncated) {
        notices.push(`在线会话超过安全扫描上限（${this.scanLimit} 条），当前结果可能不完整，请增加筛选条件`);
      }
      if (this.invalidSessionCount > 0) {
        notices.push(`已跳过 ${this.invalidSessionCount} 条过期或异常会话`);
      }
      return notices.join("；");
    }
  },
  created() {
    this.getList();
  },
  methods: {
    /** 查询登录日志列表 */
    getList() {
      this.loading = true;
      this.loadError = false;
      list(this.queryParams).then(response => {
        this.list = response.rows || [];
        this.total = response.total;
        this.scanTruncated = Boolean(response.truncated);
        this.scanLimit = Number(response.scanLimit || 0);
        this.invalidSessionCount = Number(response.invalidSessionCount || 0);
      }).catch(() => {
        this.list = [];
        this.total = 0;
        this.scanTruncated = false;
        this.invalidSessionCount = 0;
        this.loadError = true;
      }).finally(() => {
        this.loading = false;
      });
    },
    /** 搜索按钮操作 */
    handleQuery() {
      this.queryParams.pageNum = 1;
      this.getList();
    },
    /** 重置按钮操作 */
    resetQuery() {
      this.resetForm("queryForm");
      this.handleQuery();
    },
    /** 强退按钮操作 */
    handleForceLogout(row) {
      if (row.currentSession) {
        this.$modal.msgWarning("当前会话请使用退出登录");
        return;
      }
      const loginTime = this.parseTime(row.loginTime) || "未知";
      const message = `确认强退用户“${row.userName || "未知"}”的会话？登录 IP：${row.ipaddr || "未知"}，登录时间：${loginTime}`;
      this.$modal.confirm(message).then(function() {
        return forceLogout(row.tokenId);
      }).then(() => {
        this.getList();
        this.$modal.msgSuccess("强退成功");
      }).catch(() => {});
    },
    maskToken(tokenId) {
      if (!tokenId) return "-";
      if (tokenId.length <= 12) return tokenId;
      return `${tokenId.slice(0, 6)}…${tokenId.slice(-6)}`;
    }
  }
};
</script>

<style lang="scss" scoped>
.online-search-card {
  margin-bottom: 12px;
  padding-bottom: 8px;
}

.online-table-card {
  padding-bottom: 8px;
}

.online-session-alert {
  margin-bottom: 12px;
}
</style>
