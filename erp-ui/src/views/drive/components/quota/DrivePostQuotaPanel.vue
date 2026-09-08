<template>
  <section aria-label="岗位额度">
    <div class="panel-toolbar"><p>固定优先级：个人例外 ＞ 岗位额度 ＞ 全员默认。</p><el-button type="primary" icon="el-icon-plus" :disabled="!availablePosts.length" @click="openNew">设置岗位额度</el-button></div>
    <el-table :data="visiblePolicies" v-loading="loading" row-key="policyId">
      <el-table-column label="策略对象" min-width="180"><template slot-scope="scope"><strong>{{ scope.row.subjectName || subjectLabel(scope.row) }}</strong><br><small>{{ subjectTypeLabel(scope.row.subjectType) }} · 编号 {{ scope.row.subjectId }}</small></template></el-table-column>
      <el-table-column label="额度" min-width="130"><template slot-scope="scope">{{ formatBytes(scope.row.quotaBytes) }}</template></el-table-column>
      <el-table-column label="覆盖人数" width="90"><template slot-scope="scope">{{ scope.row.subjectType === 'GLOBAL' ? '全员' : postUserCount(scope.row.subjectId) + ' 人' }}</template></el-table-column>
      <el-table-column label="优先级" prop="priority" width="90" />
      <el-table-column label="版本" prop="version" width="80" />
      <el-table-column label="操作" width="140"><template slot-scope="scope"><el-button type="text" @click="open(scope.row)">编辑</el-button><el-button v-if="scope.row.subjectType !== 'GLOBAL'" type="text" class="danger" @click="remove(scope.row)">删除</el-button></template></el-table-column>
    </el-table>

    <el-dialog :title="form.isNew ? '新增岗位额度' : '编辑额度策略'" :visible.sync="dialogVisible" width="520px" append-to-body>
      <el-form label-width="110px" @submit.native.prevent>
        <el-form-item label="策略类型"><el-radio-group v-model="form.subjectType" :disabled="!form.isNew"><el-radio label="POST">岗位</el-radio><el-radio label="GLOBAL">全员默认</el-radio></el-radio-group></el-form-item>
        <el-form-item v-if="form.subjectType === 'POST'" label="岗位">
          <el-select v-model="form.subjectId" :disabled="!form.isNew" filterable style="width:100%" placeholder="选择有效岗位">
            <el-option v-for="post in selectablePosts" :key="post.postId" :label="post.postName + '（' + Number(post.userCount || 0) + '人）'" :value="post.postId" />
          </el-select>
        </el-form-item>
        <el-form-item label="额度（GiB）"><el-input v-model.trim="form.quotaGiB" /></el-form-item>
        <el-form-item label="快速档位"><el-button v-for="level in [1, 2, 3, 5, 10]" :key="level" size="mini" @click="form.quotaGiB=String(level)">{{ level }} GiB</el-button></el-form-item>
        <el-form-item v-if="form.subjectType === 'POST'" label="优先级"><el-input-number v-model="form.priority" :min="0" :max="1000" /></el-form-item>
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
  name: 'DrivePostQuotaPanel',
  props: { policies: { type: Array, default: () => [] }, postOptions: { type: Array, default: () => [] }, loading: { type: Boolean, default: false }, saving: { type: Boolean, default: false } },
  data() { return { dialogVisible: false, form: {} } },
  computed: {
    visiblePolicies() { return this.policies.filter(row => row.subjectType !== 'USER') },
    availablePosts() { return this.postOptions.filter(row => !row.configured) },
    selectablePosts() { return this.form.isNew ? this.availablePosts : this.postOptions }
  },
  methods: {
    formatBytes,
    postUserCount(postId) { const post = this.postOptions.find(row => Number(row.postId) === Number(postId)); return Number(post && post.userCount || 0) },
    subjectTypeLabel(value) { return { GLOBAL: '全员默认', POST: '岗位额度', USER: '个人额度' }[String(value || '').toUpperCase()] || '其他额度策略' },
    subjectLabel(row) { return row.subjectType === 'GLOBAL' ? '全员默认' : `岗位 ${row.subjectId}` },
    open(row) { this.form = { isNew: false, subjectType: row.subjectType, subjectId: String(row.subjectId), quotaGiB: String(bytesToGiB(row.quotaBytes)), priority: Number(row.priority || 0), version: Number(row.version || 0), reason: '' }; this.dialogVisible = true },
    openNew() { this.form = { isNew: true, subjectType: 'POST', subjectId: null, quotaGiB: '2', priority: 0, version: 0, reason: '' }; this.dialogVisible = true },
    submit() {
      const quotaBytes = gibToBytes(this.form.quotaGiB, false)
      const subjectId = this.form.subjectType === 'GLOBAL' ? 0 : Number(this.form.subjectId)
      if (quotaBytes == null || !Number.isSafeInteger(subjectId) || (this.form.subjectType === 'POST' ? subjectId <= 0 : subjectId !== 0)) { this.$message.error('请选择有效岗位并填写额度'); return }
      if (!this.form.reason) { this.$message.error('请填写调整原因'); return }
      const data = { quotaBytes, priority: this.form.subjectType === 'GLOBAL' ? 0 : this.form.priority, expireTime: null, version: this.form.version, reason: this.form.reason }
      this.$emit('save-request', { kind: 'policy', subjectType: this.form.subjectType, subjectId, data, impact: { changeType: 'PERSONAL_POLICY', subjectType: this.form.subjectType, subjectId, ...data } })
    },
    remove(row) {
      this.$prompt('删除后将回落到下一层额度。请输入删除原因', '删除岗位额度', { inputValidator: value => Boolean(value && String(value).trim()) || '请填写原因' }).then(result => {
        const data = { version: Number(row.version || 0), reason: String(result.value).trim() }
        this.$emit('save-request', { kind: 'policy-delete', subjectType: row.subjectType, subjectId: row.subjectId, data, impact: { changeType: 'PERSONAL_POLICY', subjectType: row.subjectType, subjectId: row.subjectId, deletePolicy: true, version: data.version } })
      }).catch(() => {})
    }
  }
}
</script>

<style lang="scss" scoped>
.panel-toolbar { display: flex; align-items: center; justify-content: space-between; gap: 12px; margin-bottom: 14px; }
.panel-toolbar p, small { color: #8795a8; }
.danger { color: #d93025; }
</style>
