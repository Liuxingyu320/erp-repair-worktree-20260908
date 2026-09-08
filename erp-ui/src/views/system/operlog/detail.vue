<template>
  <el-dialog title="操作日志详细" :visible.sync="visible" width="780px" append-to-body @close="$emit('update:visible', false)">
    <div v-loading="loading" class="detail-wrap">
      <!-- 基本信息 -->
      <div class="detail-card">
        <div class="detail-card-title"><i class="el-icon-info"></i> 基本信息</div>
        <el-row class="detail-row">
          <el-col :span="12">
            <div class="detail-item"><span class="detail-label">操作模块</span><span class="detail-value">{{ form.title }}</span></div>
          </el-col>
          <el-col :span="12">
            <div class="detail-item"><span class="detail-label">业务类型</span><span class="detail-value">{{ typeLabel }}</span></div>
          </el-col>
        </el-row>
        <el-row class="detail-row">
          <el-col :span="12">
            <div class="detail-item"><span class="detail-label">操作时间</span><span class="detail-value">{{ form.operTime }}</span></div>
          </el-col>
          <el-col :span="12">
            <div class="detail-item">
              <span class="detail-label">执行状态</span>
              <el-tag v-if="form.status === 0" type="success" size="small"><i class="el-icon-check"></i> 正常</el-tag>
              <el-tag v-else type="danger" size="small"><i class="el-icon-close"></i> 异常</el-tag>
            </div>
          </el-col>
        </el-row>
      </div>

      <!-- 操作人员 -->
      <div class="detail-card">
        <div class="detail-card-title"><i class="el-icon-user"></i> 操作人员</div>
        <el-row class="detail-row">
          <el-col :span="12">
            <div class="detail-item"><span class="detail-label">操作人员</span><span class="detail-value">{{ form.operName }}</span></div>
          </el-col>
          <el-col :span="12" v-if="form.deptName">
            <div class="detail-item"><span class="detail-label">所属部门</span><span class="detail-value">{{ form.deptName }}</span></div>
          </el-col>
        </el-row>
        <el-row class="detail-row">
          <el-col :span="24">
            <div class="detail-item">
              <span class="detail-label">操作地址</span>
              <span class="detail-value">{{ form.operIp }}&nbsp;&nbsp;<span class="detail-location">{{ form.operLocation }}</span></span>
            </div>
          </el-col>
        </el-row>
      </div>

      <!-- 请求信息 -->
      <div class="detail-card">
        <div class="detail-card-title"><i class="el-icon-sort"></i> 请求信息</div>
        <el-row class="detail-row">
          <el-col :span="24">
            <div class="detail-item">
              <span class="detail-label">请求地址</span>
              <span class="detail-value">
                <span :class="'method-tag method-' + form.requestMethod">{{ form.requestMethod }}</span>
                {{ form.operUrl }}
              </span>
            </div>
          </el-col>
        </el-row>
        <el-row class="detail-row">
          <el-col :span="24">
            <div class="detail-item"><span class="detail-label">操作方法</span><span class="detail-value mono">{{ form.method }}</span></div>
          </el-col>
        </el-row>
        <el-row class="detail-row">
          <el-col :span="12">
            <div class="detail-item"><span class="detail-label">消耗时间</span><span class="detail-value">{{ form.costTime }} 毫秒</span></div>
          </el-col>
        </el-row>
      </div>

      <!-- 请求摘要 -->
      <div class="detail-card">
        <div class="detail-card-title"><i class="el-icon-upload2"></i> 请求摘要</div>
        <div class="code-body">
          <div class="code-wrap">
            <pre class="code-pre">{{ form.requestSummary || '未记录请求正文' }}</pre>
          </div>
        </div>
      </div>

      <!-- 响应摘要 -->
      <div class="detail-card">
        <div class="detail-card-title"><i class="el-icon-download"></i> 响应摘要</div>
        <div class="code-body">
          <div class="code-wrap">
            <pre class="code-pre">{{ form.resultSummary || '未记录响应正文' }}</pre>
          </div>
        </div>
      </div>

      <!-- 异常信息 -->
      <div class="detail-card" v-if="form.status !== 0">
        <div class="detail-card-title error-title"><i class="el-icon-warning"></i> 异常信息</div>
        <div class="error-body">
          <div class="error-msg">{{ form.errorSummary || '操作失败，未记录错误摘要' }}</div>
        </div>
      </div>

    </div>
  </el-dialog>
</template>

<script>
export default {
  name: 'OperlogDetail',
  dicts: ['sys_oper_type'],
  props: {
    visible: { type: Boolean, default: false },
    row: { type: Object, default: () => ({}) },
    loading: { type: Boolean, default: false }
  },
  computed: {
    form() { return this.row || {} },
    typeLabel() { return this.selectDictLabel(this.dict.type.sys_oper_type, this.form.businessType) || '-' }
  }
}
</script>

<style scoped>
.detail-wrap {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.detail-card {
  border: 1px solid #e2e8f0;
  border-radius: 12px;
  background: #ffffff;
  padding: 14px 16px;
}

.detail-card-title {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 14px;
  font-weight: 600;
  color: #334155;
  margin-bottom: 10px;
}

.detail-row + .detail-row {
  margin-top: 6px;
}

.detail-item {
  display: flex;
  align-items: flex-start;
  line-height: 22px;
}

.detail-label {
  width: 80px;
  color: #64748b;
  flex-shrink: 0;
}

.detail-value {
  color: #0f172a;
  word-break: break-all;
}

.detail-location {
  color: #64748b;
}

.mono {
  font-family: "JetBrains Mono", "SFMono-Regular", Consolas, "Liberation Mono", Menlo, monospace;
}

.method-tag {
  display: inline-block;
  border-radius: 999px;
  padding: 1px 8px;
  font-size: 12px;
  line-height: 18px;
  margin-right: 8px;
  color: #ffffff;
  background: #64748b;
}

.method-get { background: #16a34a; }
.method-post { background: #4f46e5; }
.method-put { background: #d97706; }
.method-delete { background: #dc2626; }

.code-body {
  border: 1px solid #e2e8f0;
  border-radius: 10px;
  background: #f8fafc;
}

.code-wrap {
  padding: 10px 12px;
}

.code-pre {
  margin: 0;
  white-space: pre-wrap;
  word-break: break-all;
  line-height: 1.6;
  color: #0f172a;
}

.error-title {
  color: #b91c1c;
}

.error-body {
  border: 1px solid #fecaca;
  border-radius: 10px;
  background: #fef2f2;
  padding: 10px 12px;
}

.error-msg {
  color: #7f1d1d;
  line-height: 1.6;
  white-space: pre-wrap;
  word-break: break-all;
}
</style>
