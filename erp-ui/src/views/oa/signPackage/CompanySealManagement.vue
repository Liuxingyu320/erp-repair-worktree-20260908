<template>
  <div class="company-seal-management">
    <el-alert
      title="合同发送时不写入最终公司；员工完成首次手写签名后，系统按签约部门识别公司，再由经办人选择该公司的印章。"
      type="info"
      show-icon
      :closable="false"
      class="management-tip"
    />
    <el-alert
      title="首次配置顺序"
      description="先登记公司法定信息和该公司的合同印章，再到部门管理把公司绑定在合适的上级部门；下级部门和门店会自动继承最近上级部门的公司。"
      type="warning"
      show-icon
      :closable="false"
      class="management-tip"
    />

    <div class="management-toolbar">
      <el-form :inline="true" size="small">
        <el-form-item label="公司名称">
          <el-input v-model.trim="query.legalEntityName" clearable placeholder="输入公司法定全称" @keyup.enter.native="loadCompanies" />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="query.status" clearable placeholder="全部">
            <el-option label="启用" value="0" />
            <el-option label="停用" value="1" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" icon="el-icon-search" @click="loadCompanies">查询</el-button>
        </el-form-item>
      </el-form>
      <div class="management-actions">
        <el-button size="small" icon="el-icon-office-building" @click="goDeptBinding" v-hasPermi="['system:dept:list']">部门公司绑定</el-button>
        <el-button type="primary" size="small" icon="el-icon-plus" @click="openCompany()" v-hasPermi="['oa:signCompany:edit']">新增公司</el-button>
      </div>
    </div>

    <el-table v-loading="loading" :data="companies" border size="small" empty-text="暂无公司主体，请先新增公司">
      <el-table-column label="公司法定全称" prop="legalEntityName" min-width="220" show-overflow-tooltip />
      <el-table-column label="统一社会信用代码" prop="unifiedSocialCreditCode" width="190" />
      <el-table-column label="法定代表人" prop="legalRepresentative" width="120" />
      <el-table-column label="注册地址" prop="registeredAddress" min-width="220" show-overflow-tooltip />
      <el-table-column label="联系电话" prop="contactPhone" width="130" />
      <el-table-column label="状态" width="80">
        <template slot-scope="scope">
          <el-tag size="mini" :type="scope.row.status === '0' ? 'success' : 'info'">{{ scope.row.status === '0' ? '启用' : '停用' }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" fixed="right" width="190">
        <template slot-scope="scope">
          <el-button type="text" size="mini" icon="el-icon-edit" @click="openCompany(scope.row)" v-hasPermi="['oa:signCompany:edit']">编辑</el-button>
          <el-button type="text" size="mini" icon="el-icon-s-claim" @click="openSeals(scope.row)" v-hasPermi="['oa:signSeal:list']">管理印章</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog :title="companyForm.legalEntityId ? '编辑公司' : '新增公司'" :visible.sync="companyOpen" width="680px" append-to-body>
      <el-form ref="companyForm" :model="companyForm" :rules="companyRules" label-width="130px" size="small">
        <el-form-item label="公司内部编码" prop="legalEntityCode">
          <el-input v-model.trim="companyForm.legalEntityCode" maxlength="64" placeholder="用于系统内唯一识别，不显示在合同正文" />
        </el-form-item>
        <el-form-item label="公司法定全称" prop="legalEntityName">
          <el-input v-model.trim="companyForm.legalEntityName" maxlength="160" />
        </el-form-item>
        <el-form-item label="统一社会信用代码">
          <el-input v-model.trim="companyForm.unifiedSocialCreditCode" maxlength="32" />
        </el-form-item>
        <el-form-item label="注册地址">
          <el-input v-model.trim="companyForm.registeredAddress" maxlength="255" />
        </el-form-item>
        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item label="法定代表人">
              <el-input v-model.trim="companyForm.legalRepresentative" maxlength="64" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="联系电话">
              <el-input v-model.trim="companyForm.contactPhone" maxlength="32" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="状态">
          <el-radio-group v-model="companyForm.status">
            <el-radio label="0">启用</el-radio>
            <el-radio label="1">停用</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="companyForm.remark" type="textarea" :rows="2" maxlength="500" show-word-limit />
        </el-form-item>
      </el-form>
      <div slot="footer">
        <el-button @click="companyOpen = false">取消</el-button>
        <el-button type="primary" :loading="companySaving" @click="saveCompany" v-hasPermi="['oa:signCompany:edit']">保存</el-button>
      </div>
    </el-dialog>

    <el-dialog :title="selectedCompany ? selectedCompany.legalEntityName + '－合同印章' : '合同印章'" :visible.sync="sealListOpen" width="820px" append-to-body>
      <div class="seal-toolbar">
        <el-alert title="这里只维护合同用印章。经办人选定公司后，只能选择该公司名下处于有效期内的启用印章。" type="warning" show-icon :closable="false" />
        <el-button type="primary" size="small" icon="el-icon-plus" @click="openSeal()" v-hasPermi="['oa:signSeal:edit']">新增印章</el-button>
      </div>
      <el-alert v-if="sealListError" :title="sealListError" type="error" :closable="false" show-icon />
      <el-button v-if="sealListError" size="small" :disabled="sealLoading" @click="loadSeals">重试当前公司</el-button>
      <el-table v-loading="sealLoading" :data="seals" border size="small" empty-text="该公司尚未配置合同印章">
        <el-table-column label="印章名称" prop="sealName" min-width="180" />
        <el-table-column label="默认印章" width="90">
          <template slot-scope="scope">{{ scope.row.isDefault === 'Y' ? '是' : '否' }}</template>
        </el-table-column>
        <el-table-column label="有效期开始" width="160">
          <template slot-scope="scope">{{ parseTime(scope.row.validFrom) || '长期有效' }}</template>
        </el-table-column>
        <el-table-column label="有效期结束" width="160">
          <template slot-scope="scope">{{ parseTime(scope.row.validTo) || '长期有效' }}</template>
        </el-table-column>
        <el-table-column label="状态" width="80">
          <template slot-scope="scope">{{ scope.row.status === '0' ? '启用' : '停用' }}</template>
        </el-table-column>
        <el-table-column label="操作" width="80">
          <template slot-scope="scope"><el-button type="text" size="mini" @click="openSeal(scope.row)" v-hasPermi="['oa:signSeal:edit']">编辑</el-button></template>
        </el-table-column>
      </el-table>
    </el-dialog>

    <el-dialog :title="sealForm.sealId ? '编辑印章' : '新增印章'" :visible.sync="sealOpen" width="640px" append-to-body>
      <el-alert v-if="sealError" :title="sealError" type="error" :closable="false" show-icon />
      <el-form ref="sealForm" :model="sealForm" :rules="sealRules" label-width="110px" size="small">
        <el-form-item label="所属公司">
          <el-input :value="sealForm.legalEntityName || ''" disabled />
        </el-form-item>
        <el-form-item label="印章名称" prop="sealName">
          <el-input v-model.trim="sealForm.sealName" maxlength="100" placeholder="例如：劳动合同专用章" />
        </el-form-item>
        <el-form-item label="印章图片" prop="sealImageUrl">
          <file-upload v-model="sealForm.sealImageUrl" :limit="1" :file-size="5" :file-type="['png', 'jpg', 'jpeg']" />
          <div class="form-tip">支持 PNG、JPG 和 JPEG；建议使用背景透明、边缘清晰的 PNG 图片。保存后如更换图片，会重新登记文件校验值。</div>
        </el-form-item>
        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item label="有效期开始">
              <el-date-picker v-model="sealForm.validFrom" type="datetime" value-format="yyyy-MM-dd HH:mm:ss" placeholder="不填表示不限制" style="width:100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="有效期结束">
              <el-date-picker v-model="sealForm.validTo" type="datetime" value-format="yyyy-MM-dd HH:mm:ss" placeholder="不填表示不限制" style="width:100%" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="默认印章">
          <el-radio-group v-model="sealForm.isDefault">
            <el-radio label="Y">是</el-radio>
            <el-radio label="N">否</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="状态">
          <el-radio-group v-model="sealForm.status">
            <el-radio label="0">启用</el-radio>
            <el-radio label="1">停用</el-radio>
          </el-radio-group>
        </el-form-item>
      </el-form>
      <div slot="footer">
        <el-button @click="sealOpen = false">取消</el-button>
        <el-button type="primary" :loading="sealSaving" @click="saveSeal" v-hasPermi="['oa:signSeal:edit']">保存</el-button>
      </div>
    </el-dialog>
  </div>
</template>

<script>
import { createLegalEntity, listLegalEntities, updateLegalEntity } from '@/api/system/legalEntity'
import { listCompanySeals, saveCompanySeal } from '@/api/oa/signPackage'
import { getSelectedSignScopeDeptId } from '@/utils/signScopeContext'
const { createUiOperationScope } = require('@/utils/uiOperationScope')
const { normalizePositiveDecimalId } = require('@/utils/positiveDecimalId')

export default {
  name: 'CompanySealManagement',
  data() {
    return {
      query: {},
      loading: false,
      companies: [],
      companyOpen: false,
      companySaving: false,
      companyForm: {},
      companyRules: {
        legalEntityCode: [{ required: true, message: '请输入公司内部编码', trigger: 'blur' }],
        legalEntityName: [{ required: true, message: '请输入公司法定全称', trigger: 'blur' }]
      },
      selectedCompany: null,
      sealListOpen: false,
      sealLoading: false,
      sealListError: "",
      sealError: "",
      seals: [],
      sealOpen: false,
      sealSaving: false,
      sealForm: {},
      sealRules: {
        sealName: [{ required: true, message: '请输入印章名称', trigger: 'blur' }],
        sealImageUrl: [{ required: true, message: '请上传印章图片', trigger: 'change' }]
      }
    }
  },
  created() {
    this.loadCompanies()
  },
  beforeDestroy() { this.operationScope().deactivate() },
  deactivated() { this.operationScope().deactivate(); this.sealListOpen = false; this.sealOpen = false },
  activated() { this.operationScope().activate() },
  watch: {
    sealListOpen(value) { if (!value) { this.operationScope().invalidate("seal-list"); this.closeSealEditor(); this.seals = [] } },
    sealOpen(value) { if (!value) this.operationScope().invalidate("seal-edit") },
    "$store.state.user.sessionRevision"() { this.operationScope().invalidate(); this.sealListOpen = false; this.closeSealEditor() },
    "$route.fullPath"() { this.operationScope().invalidate(); this.sealListOpen = false; this.closeSealEditor() }
  },
  methods: {
    operationScope() {
      if (!this._operationScope) this._operationScope = createUiOperationScope(() => {
        const user = this.$store && this.$store.state && this.$store.state.user || {}
        return { actor: String(user.id || ""), session: user.sessionRevision || 0,
          dept: String(getSelectedSignScopeDeptId() || "") }
      })
      return this._operationScope
    },
    closeSealEditor() { this.operationScope().invalidate("seal-edit"); this.sealOpen = false; this.sealSaving = false },
    goDeptBinding() {
      this.$router.push('/system/dept')
    },
    loadCompanies() {
      this.loading = true
      return listLegalEntities(this.query).then(response => {
        this.companies = response.data || []
      }).finally(() => {
        this.loading = false
      })
    },
    openCompany(row) {
      this.companyForm = Object.assign({ status: '0', version: 0 }, row || {})
      this.companyOpen = true
      this.$nextTick(() => this.$refs.companyForm && this.$refs.companyForm.clearValidate())
    },
    saveCompany() {
      this.$refs.companyForm.validate(valid => {
        if (!valid) return
        this.companySaving = true
        const request = this.companyForm.legalEntityId
          ? updateLegalEntity(this.companyForm)
          : createLegalEntity(this.companyForm)
        request.then(() => {
          this.$modal.msgSuccess('公司信息已保存')
          this.companyOpen = false
          this.loadCompanies()
          this.$emit('changed')
        }).finally(() => {
          this.companySaving = false
        })
      })
    },
    openSeals(company) {
      const companyId = normalizePositiveDecimalId(company && company.legalEntityId)
      if (!companyId) return Promise.resolve(null)
      this.operationScope().invalidate("seal-list")
      this.closeSealEditor()
      this.selectedCompany = { ...company, legalEntityId: companyId }
      this.seals = []
      this.sealListOpen = true
      return this.loadSeals()
    },
    loadSeals() {
      const companyId = normalizePositiveDecimalId(this.selectedCompany && this.selectedCompany.legalEntityId)
      if (!companyId || !this.sealListOpen) return Promise.resolve(null)
      const scope = this.operationScope(), operation = scope.begin("seal-list", companyId)
      const current = () => this.sealListOpen && scope.isCurrent(operation,
        normalizePositiveDecimalId(this.selectedCompany && this.selectedCompany.legalEntityId))
      this.sealLoading = true
      this.seals = []
      this.sealListError = ""
      return listCompanySeals(companyId, false).then(response => {
        if (!current()) return null
        const rows = response && response.data
        if (!Array.isArray(rows) || rows.some(row => normalizePositiveDecimalId(row.legalEntityId) !== companyId))
          throw new Error("印章所属公司未确认，请重新加载当前公司")
        this.seals = rows
        return rows
      }).catch(error => {
        if (current()) this.sealListError = error && error.message || "该公司印章加载失败，请重试"
        return null
      }).finally(() => { if (current()) this.sealLoading = false })
    },
    openSeal(row) {
      const companyId = normalizePositiveDecimalId(this.selectedCompany && this.selectedCompany.legalEntityId)
      if (!this.sealListOpen || !companyId || this.sealLoading || this.sealListError) return
      if (row && (normalizePositiveDecimalId(row.legalEntityId) !== companyId ||
          !this.seals.some(seal => String(seal.sealId) === String(row.sealId)))) {
        this.sealListError = "印章不属于当前公司，请重新加载后选择"
        return
      }
      this.operationScope().invalidate("seal-edit")
      this.sealSaving = false
      this.sealError = ""
      this.sealForm = { isDefault: this.seals.length ? 'N' : 'Y', status: '0', ...(row || {}),
        legalEntityId: companyId, legalEntityName: this.selectedCompany.legalEntityName }
      this.sealOpen = true
      const operation = this.operationScope().begin("seal-edit", { companyId, sealId: String(this.sealForm.sealId || "") })
      this.$nextTick(() => {
        if (this.sealOpen && this.operationScope().isCurrent(operation) && this.$refs.sealForm) this.$refs.sealForm.clearValidate()
      })
    },
    saveSeal() {
      if (this.sealSaving || !this.sealOpen || !this.$refs.sealForm) return Promise.resolve(null)
      const payload = { ...this.sealForm }
      const companyId = normalizePositiveDecimalId(payload.legalEntityId)
      if (!companyId || companyId !== normalizePositiveDecimalId(this.selectedCompany && this.selectedCompany.legalEntityId)) return Promise.resolve(null)
      const target = { companyId, sealId: String(payload.sealId || "") }
      const scope = this.operationScope(), operation = scope.begin("seal-edit", target)
      const current = () => this.sealOpen && scope.isCurrent(operation, {
        companyId: normalizePositiveDecimalId(this.sealForm.legalEntityId), sealId: String(this.sealForm.sealId || "") }) &&
        companyId === normalizePositiveDecimalId(this.selectedCompany && this.selectedCompany.legalEntityId)
      this.sealSaving = true
      this.sealError = ""
      return new Promise(resolve => this.$refs.sealForm.validate(valid => {
        if (!current()) { resolve(null); return }
        if (!valid) { this.sealSaving = false; resolve(null); return }
        saveCompanySeal(payload).then(response => {
          if (!current()) return null
          this.$modal.msgSuccess('印章信息已保存')
          this.closeSealEditor()
          return this.loadSeals()
        }).catch(error => {
          if (current()) this.sealError = error && error.message || "印章保存结果待核对，请保留当前输入后重试"
          return null
        }).finally(() => { if (current()) this.sealSaving = false }).then(resolve)
      }))
    }
  }
}
</script>

<style scoped>
.management-tip { margin-bottom: 12px; }
.management-toolbar,
.seal-toolbar {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 12px;
}
.management-actions { display: flex; gap: 8px; }
.seal-toolbar .el-alert { flex: 1; }
.form-tip { margin-top: 6px; color: #909399; line-height: 1.5; }
</style>
