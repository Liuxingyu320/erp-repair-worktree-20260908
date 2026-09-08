<template>
  <section class="attendance-module">
    <el-alert
      v-if="!validStore"
      title="请先切换到需要配置考勤地点的门店"
      description="考勤地点和围栏必须归属当前授权门店。"
      type="warning"
      show-icon
      :closable="false"
      class="context-alert"
    />

    <template v-else>
      <el-card shadow="never" class="search-card oa-filter-card">
        <div class="site-toolbar">
          <div>
            <strong>{{ shopContext.deptName || '当前门店' }}·考勤地点</strong>
            <span>启用后的地点才可用于新建和发布排班。</span>
          </div>
          <div class="site-actions">
            <el-select v-model="statusFilter" size="small" clearable placeholder="全部状态" @change="refresh">
              <el-option label="已启用" value="ENABLED" />
              <el-option label="已停用" value="DISABLED" />
            </el-select>
            <el-button size="small" icon="el-icon-refresh" :loading="loading" @click="refresh">刷新</el-button>
            <el-button
              v-hasPermi="['oa:attendance:site:add']"
              type="primary"
              size="small"
              icon="el-icon-plus"
              @click="openCreate"
            >新增地点</el-button>
          </div>
        </div>
      </el-card>

      <el-card shadow="never" class="table-card oa-table-card">
        <el-table v-loading="loading" :data="sites" row-key="siteId" size="small" border empty-text="当前门店暂无考勤地点">
          <el-table-column prop="siteCode" label="地点编码" min-width="110" />
          <el-table-column prop="siteName" label="地点名称" min-width="130" />
          <el-table-column prop="address" label="地址" min-width="220" show-overflow-tooltip />
          <el-table-column label="坐标" min-width="190">
            <template slot-scope="scope">
              <div>{{ scope.row.coordinateSystem || '-' }}</div>
              <small>{{ coordinateText(scope.row) }}</small>
            </template>
          </el-table-column>
          <el-table-column label="围栏" min-width="130">
            <template slot-scope="scope">
              <div>半径 {{ scope.row.radiusMeters }} 米</div>
              <small>精度 ≤ {{ scope.row.maxAccuracyMeters }} 米</small>
            </template>
          </el-table-column>
          <el-table-column label="状态" width="90" align="center">
            <template slot-scope="scope">
              <el-tag :type="enabled(scope.row) ? 'success' : 'info'" size="mini">
                {{ enabled(scope.row) ? '已启用' : '已停用' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="245" fixed="right">
            <template slot-scope="scope">
              <el-button v-if="canEditSite" type="text" size="mini" @click="openEdit(scope.row)">编辑</el-button>
              <el-button v-hasPermi="['oa:attendance:site:edit']" type="text" size="mini" @click="toggleStatus(scope.row)">
                {{ enabled(scope.row) ? '停用' : '启用' }}
              </el-button>
              <el-button
                v-if="!enabled(scope.row)"
                v-hasPermi="['oa:attendance:site:remove']"
                type="text"
                size="mini"
                class="danger-action"
                @click="removeSite(scope.row)"
              >删除</el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-card>
    </template>

    <el-dialog :title="form.siteId ? '编辑考勤地点' : '新增考勤地点'" :visible.sync="dialogOpen" width="620px" append-to-body @closed="resetForm">
      <el-alert
        v-if="!form.siteId"
        title="新地点保存后默认为停用；核对坐标和围栏后再显式启用。"
        type="info"
        show-icon
        :closable="false"
        class="dialog-alert"
      />
      <el-form ref="form" :model="form" :rules="rules" label-width="112px">
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="地点编码" prop="siteCode">
              <el-input v-model.trim="form.siteCode" maxlength="32" placeholder="如 STORE_001" @input="uppercaseSiteCode" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="地点名称" prop="siteName">
              <el-input v-model.trim="form.siteName" maxlength="64" placeholder="如 门店正门" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="详细地址" prop="address">
          <el-input v-model.trim="form.address" maxlength="255" show-word-limit placeholder="现场可核验的完整地址" />
        </el-form-item>
        <el-row :gutter="16">
          <el-col :span="8">
            <el-form-item label="坐标系" prop="coordinateSystem">
              <el-select v-model="form.coordinateSystem" style="width: 100%">
                <el-option label="GCJ-02" value="GCJ02" />
                <el-option label="WGS-84" value="WGS84" />
                <el-option label="BD-09" value="BD09" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="经度" prop="longitude">
              <el-input-number v-model="form.longitude" :min="-180" :max="180" :precision="7" :step="0.000001" controls-position="right" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="纬度" prop="latitude">
              <el-input-number v-model="form.latitude" :min="-90" :max="90" :precision="7" :step="0.000001" controls-position="right" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="围栏半径" prop="radiusMeters">
              <el-input-number v-model="form.radiusMeters" :min="10" :max="5000" :step="10" controls-position="right" />
              <span class="unit">米</span>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="最大定位误差" prop="maxAccuracyMeters">
              <el-input-number v-model="form.maxAccuracyMeters" :min="5" :max="1000" :step="5" controls-position="right" />
              <span class="unit">米</span>
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="备注" prop="remark">
          <el-input v-model.trim="form.remark" type="textarea" :rows="2" maxlength="500" show-word-limit />
        </el-form-item>
      </el-form>
      <span slot="footer">
        <el-button @click="dialogOpen = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="submit">保存</el-button>
      </span>
    </el-dialog>
  </section>
</template>

<script>
import {
  changeAttendanceSiteStatus,
  createAttendanceSite,
  deleteAttendanceSite,
  getAttendanceSite,
  listAttendanceSites,
  updateAttendanceSite
} from '@/api/oa/attendanceV2'
import { checkPermi } from '@/utils/permission'

function rowsFrom(response) {
  if (response && Array.isArray(response.data)) return response.data
  const payload = response && response.data && typeof response.data === 'object' ? response.data : response
  if (payload && Array.isArray(payload.rows)) return payload.rows
  if (Array.isArray(payload)) return payload
  return []
}

function valueFrom(response) {
  if (response && response.data && !Array.isArray(response.data)) return response.data
  return response || {}
}

function emptyForm(shopId) {
  return {
    siteId: null,
    siteCode: '',
    siteName: '',
    shopId: shopId || null,
    address: '',
    longitude: null,
    latitude: null,
    coordinateSystem: 'GCJ02',
    radiusMeters: 200,
    maxAccuracyMeters: 100,
    rowVersion: null,
    remark: ''
  }
}

export default {
  name: 'AttendanceSiteManagement',
  props: {
    shopContext: { type: Object, default: () => ({}) }
  },
  data() {
    return {
      loading: false,
      saving: false,
      dialogOpen: false,
      statusFilter: '',
      sites: [],
      form: emptyForm(null),
      rules: {
        siteCode: [
          { required: true, message: '请输入地点编码', trigger: 'blur' },
          { pattern: /^[A-Z0-9_-]{2,32}$/, message: '编码须为 2-32 位大写字母、数字、下划线或短横线', trigger: 'blur' }
        ],
        siteName: [{ required: true, message: '请输入地点名称', trigger: 'blur' }],
        address: [{ required: true, message: '请输入详细地址', trigger: 'blur' }],
        coordinateSystem: [{ required: true, message: '请选择坐标系', trigger: 'change' }],
        longitude: [{ required: true, type: 'number', message: '请输入经度', trigger: 'change' }],
        latitude: [{ required: true, type: 'number', message: '请输入纬度', trigger: 'change' }],
        radiusMeters: [{ required: true, type: 'number', message: '请输入围栏半径', trigger: 'change' }],
        maxAccuracyMeters: [{ required: true, type: 'number', message: '请输入最大定位误差', trigger: 'change' }]
      }
    }
  },
  computed: {
    canEditSite() {
      return checkPermi(['oa:attendance:site:query']) && checkPermi(['oa:attendance:site:edit'])
    },
    validStore() {
      return Boolean(this.shopContext && this.shopContext.isStore && this.shopContext.deptId)
    }
  },
  watch: {
    'shopContext.deptId'() {
      this.dialogOpen = false
      this.refresh()
    }
  },
  created() {
    this.refresh()
  },
  methods: {
    enabled(row) {
      return String(row && row.status || '').toUpperCase() === 'ENABLED'
    },
    coordinateText(row) {
      if (row.longitude === null || row.longitude === undefined || row.latitude === null || row.latitude === undefined) return '-'
      return `${row.longitude}, ${row.latitude}`
    },
    refresh() {
      if (!this.validStore) {
        this.sites = []
        return Promise.resolve()
      }
      this.loading = true
      return listAttendanceSites({
        shopId: this.shopContext.deptId,
        status: this.statusFilter || undefined
      }).then(response => {
        this.sites = rowsFrom(response)
      }).catch(error => {
        this.$modal.msgError(error.message || '考勤地点加载失败')
      }).finally(() => { this.loading = false })
    },
    uppercaseSiteCode(value) {
      this.form.siteCode = String(value || '').toUpperCase()
    },
    openCreate() {
      this.form = emptyForm(this.shopContext.deptId)
      this.dialogOpen = true
      this.$nextTick(() => this.$refs.form && this.$refs.form.clearValidate())
    },
    openEdit(row) {
      this.loading = true
      return getAttendanceSite(row.siteId).then(response => {
        const value = valueFrom(response)
        this.form = Object.assign(emptyForm(this.shopContext.deptId), value, {
          shopId: this.shopContext.deptId
        })
        this.dialogOpen = true
        this.$nextTick(() => this.$refs.form && this.$refs.form.clearValidate())
      }).catch(error => {
        this.$modal.msgError(error.message || '考勤地点详情加载失败')
      }).finally(() => { this.loading = false })
    },
    resetForm() {
      if (this.$refs.form) this.$refs.form.resetFields()
      this.form = emptyForm(this.shopContext && this.shopContext.deptId)
    },
    submit() {
      if (this.saving || !this.validStore || !this.$refs.form) return
      this.$refs.form.validate(valid => {
        if (!valid) return
        this.saving = true
        const payload = Object.assign({}, this.form, { shopId: this.shopContext.deptId })
        const request = this.form.siteId
          ? updateAttendanceSite(this.form.siteId, payload)
          : createAttendanceSite(payload)
        request.then(() => {
          this.$modal.msgSuccess(this.form.siteId ? '考勤地点已更新' : '考勤地点已创建，请核对后启用')
          this.dialogOpen = false
          return this.refresh()
        }).catch(error => {
          this.$modal.msgError(error.message || '考勤地点保存失败')
        }).finally(() => { this.saving = false })
      })
    },
    toggleStatus(row) {
      const status = this.enabled(row) ? 'DISABLED' : 'ENABLED'
      const action = status === 'ENABLED' ? '启用' : '停用'
      this.$modal.confirm(`${action}后将只影响后续新建和发布的排班。确认${action}“${row.siteName}”？`, `${action}考勤地点`).then(() => {
        return changeAttendanceSiteStatus(row.siteId, { status, rowVersion: row.rowVersion })
      }).then(() => {
        this.$modal.msgSuccess(`考勤地点已${action}`)
        return this.refresh()
      }).catch(error => {
        if (error && error !== 'cancel' && error !== 'close') this.$modal.msgError(error.message || `考勤地点${action}失败`)
      })
    },
    removeSite(row) {
      this.$modal.confirm(`删除后不可恢复。确认删除未被排班引用的地点“${row.siteName}”？`, '删除考勤地点').then(() => {
        return deleteAttendanceSite(row.siteId, row.rowVersion)
      }).then(() => {
        this.$modal.msgSuccess('考勤地点已删除')
        return this.refresh()
      }).catch(error => {
        if (error && error !== 'cancel' && error !== 'close') this.$modal.msgError(error.message || '考勤地点删除失败')
      })
    }
  }
}
</script>

<style scoped>
.context-alert, .table-card { margin-top: 16px; }
.site-toolbar, .site-actions { display: flex; align-items: center; gap: 12px; }
.site-toolbar { justify-content: space-between; }
.site-toolbar span { margin-left: 12px; color: #8492a6; font-size: 13px; }
.site-actions .el-select { width: 125px; }
.table-card small { color: #8492a6; }
.danger-action { color: #f56c6c; }
.dialog-alert { margin-bottom: 18px; }
.unit { margin-left: 6px; color: #8492a6; }
.el-input-number { width: 100%; }
@media (max-width: 900px) {
  .site-toolbar { align-items: flex-start; flex-direction: column; }
  .site-toolbar span { display: block; margin: 6px 0 0; }
}
</style>
