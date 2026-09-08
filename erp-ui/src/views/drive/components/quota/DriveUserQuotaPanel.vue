<template>
  <section aria-label="个人例外额度">
    <div class="panel-toolbar">
      <el-input v-model.trim="keyword" clearable prefix-icon="el-icon-search" placeholder="姓名、账号、组织或岗位" @keyup.enter.native="search" />
      <el-checkbox v-model="onlyOver" @change="search">只看超额</el-checkbox>
      <el-button icon="el-icon-search" @click="search">查询</el-button>
    </div>
    <el-table :data="users" v-loading="loading" row-key="userId" max-height="520">
      <el-table-column label="用户" min-width="150"><template slot-scope="scope"><strong>{{ scope.row.displayName }}</strong><br><small>{{ scope.row.username }}</small></template></el-table-column>
      <el-table-column label="组织 / 岗位" min-width="190"><template slot-scope="scope">{{ scope.row.deptName || '无直属组织' }}<br><small>{{ (scope.row.postNames || []).join('、') || '无岗位' }}</small></template></el-table-column>
      <el-table-column label="有效额度" min-width="125"><template slot-scope="scope">{{ formatBytes(scope.row.quotaBytes) }}</template></el-table-column>
      <el-table-column label="来源" min-width="120" prop="quotaSourceLabel" />
      <el-table-column label="已使用" min-width="125"><template slot-scope="scope"><span :class="{ danger: scope.row.overQuota }">{{ formatBytes(scope.row.usedBytes) }}</span></template></el-table-column>
      <el-table-column label="操作" width="150"><template slot-scope="scope"><el-button type="text" @click="open(scope.row)">设置例外</el-button><el-button v-if="policy(scope.row)" type="text" class="danger" @click="remove(scope.row)">移除例外</el-button></template></el-table-column>
    </el-table>
    <el-pagination v-if="total > 100" layout="prev, pager, next, total" :total="total" :page-size="100" @current-change="page => $emit('search', { keyword, overQuota: onlyOver || undefined, pageNum: page, pageSize: 100 })" />

    <el-dialog title="设置个人例外额度" :visible.sync="dialogVisible" width="520px" append-to-body>
      <el-form v-if="selected" label-width="110px" @submit.native.prevent>
        <el-form-item label="用户"><strong>{{ selected.displayName }}</strong>（{{ selected.username }}）</el-form-item>
        <el-form-item label="额度（GiB）"><el-input v-model.trim="form.quotaGiB" /></el-form-item>
        <el-form-item label="失效时间"><el-date-picker v-model="form.expireTime" type="datetime" placeholder="长期有效时留空" style="width:100%" /></el-form-item>
        <el-form-item label="调整原因"><el-input v-model.trim="form.reason" maxlength="500" show-word-limit /></el-form-item>
      </el-form>
      <div slot="footer"><el-button @click="dialogVisible=false">取消</el-button><el-button type="primary" :loading="saving" @click="submit">预览影响</el-button></div>
    </el-dialog>
  </section>
</template>

<script>
const { formatBytes } = require('../../driveState')
const { bytesToGiB, gibToBytes } = require('../../quotaState')

export default {
  name: 'DriveUserQuotaPanel',
  props: { users: { type: Array, default: () => [] }, policies: { type: Array, default: () => [] }, total: { type: Number, default: 0 }, loading: { type: Boolean, default: false }, saving: { type: Boolean, default: false } },
  data() { return { keyword: '', onlyOver: false, selected: null, dialogVisible: false, form: {} } },
  methods: {
    formatBytes,
    policy(user) { return this.policies.find(row => row.subjectType === 'USER' && Number(row.subjectId) === Number(user.userId)) || null },
    search() { this.$emit('search', { keyword: this.keyword || undefined, overQuota: this.onlyOver || undefined, pageNum: 1, pageSize: 100 }) },
    open(user) { const existing = this.policy(user); this.selected = user; this.form = { quotaGiB: String(bytesToGiB(existing ? existing.quotaBytes : user.quotaBytes)), expireTime: existing ? existing.expireTime : null, version: existing ? Number(existing.version || 0) : 0, reason: '' }; this.dialogVisible = true },
    submit() {
      const quotaBytes = gibToBytes(this.form.quotaGiB, false)
      if (quotaBytes == null) { this.$message.error('请输入有效 GiB 数值，最多三位小数'); return }
      if (this.form.expireTime && new Date(this.form.expireTime).getTime() <= Date.now()) { this.$message.error('失效时间必须晚于当前时间'); return }
      if (!this.form.reason) { this.$message.error('请填写调整原因'); return }
      const data = { quotaBytes, priority: 0, expireTime: this.form.expireTime || null, version: this.form.version, reason: this.form.reason }
      const subjectId = this.selected.userId
      this.$emit('save-request', { kind: 'policy', subjectType: 'USER', subjectId, data, impact: { changeType: 'PERSONAL_POLICY', subjectType: 'USER', subjectId, ...data } })
    },
    remove(user) {
      const existing = this.policy(user)
      if (!existing) return
      this.$prompt('移除后将回落到岗位或全员默认。请输入原因', '移除个人例外', { inputValidator: value => Boolean(value && String(value).trim()) || '请填写原因' }).then(result => {
        const data = { version: Number(existing.version || 0), reason: String(result.value).trim() }
        this.$emit('save-request', { kind: 'policy-delete', subjectType: 'USER', subjectId: user.userId, data, impact: { changeType: 'PERSONAL_POLICY', subjectType: 'USER', subjectId: user.userId, deletePolicy: true, version: data.version } })
      }).catch(() => {})
    }
  }
}
</script>

<style lang="scss" scoped>
.panel-toolbar { display: flex; align-items: center; gap: 10px; margin-bottom: 14px; }
.panel-toolbar .el-input { max-width: 380px; }
small { color: #8795a8; }
.danger { color: #d93025; }
.el-pagination { margin-top: 14px; text-align: right; }
</style>
