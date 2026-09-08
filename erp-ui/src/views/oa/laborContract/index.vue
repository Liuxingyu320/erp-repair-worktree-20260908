<template>
  <div class="app-container oa-workspace-page labor-contract-page">
    <section class="oa-page-hero">
      <div class="oa-hero__copy">
        <span class="oa-hero__eyebrow">OA 协同 · 合同档案</span>
        <div class="oa-hero__title-row">
          <span class="oa-hero__icon"><i class="el-icon-document-checked" /></span>
          <div>
            <h1>劳动合同档案</h1>
            <p>统一查询历史劳动合同、签署状态与可信文件哈希，便于归档追溯和合同验真。</p>
          </div>
        </div>
      </div>
      <div class="oa-hero__aside">
        <span>当前合同记录</span>
        <strong>{{ total }} 份</strong>
        <small>按当前筛选条件统计</small>
      </div>
    </section>

    <el-tabs v-model="activeTab" type="border-card" class="oa-tabs-shell">
      <el-tab-pane label="历史劳动合同" name="contract">
        <div class="toolbar-row">
          <el-form :model="queryParams" inline size="small">
            <el-form-item label="员工">
              <el-input v-model="queryParams.employeeName" clearable placeholder="姓名" @keyup.enter.native="getList" />
            </el-form-item>
            <el-form-item label="社保">
              <el-select v-model="queryParams.socialType" clearable placeholder="全部">
                <el-option label="有社保" value="有社保" />
                <el-option label="无社保" value="无社保" />
              </el-select>
            </el-form-item>
            <el-form-item label="状态">
              <el-select v-model="queryParams.status" clearable placeholder="全部">
                <el-option v-for="item in statusOptions" :key="item.value" :label="item.label" :value="item.value" />
              </el-select>
            </el-form-item>
            <el-form-item>
              <el-button type="primary" size="mini" icon="el-icon-search" @click="getList">查询</el-button>
              <el-button size="mini" icon="el-icon-refresh" @click="resetQuery">重置</el-button>
            </el-form-item>
          </el-form>
          <div>
            <el-button size="mini" icon="el-icon-search" @click="handleVerifyHash" v-hasPermi="['oa:laborContract:query']">合同验真</el-button>
          </div>
        </div>

        <el-table v-loading="loading" :data="contractList" border size="small">
          <el-table-column label="合同编号" prop="contractId" width="88" />
          <el-table-column label="员工" prop="employeeName" min-width="110" />
          <el-table-column label="岗位" prop="postName" min-width="110" />
          <el-table-column label="店铺" prop="shopDeptName" min-width="120" />
          <el-table-column label="社保" prop="socialType" width="92" />
          <el-table-column label="期限" min-width="190">
            <template slot-scope="scope">
              {{ scope.row.contractStartDate || '-' }} 至 {{ scope.row.contractEndDate || '-' }}
            </template>
          </el-table-column>
          <el-table-column label="状态" prop="status" width="108">
            <template slot-scope="scope">
              <el-tag size="mini" :type="statusTagType(scope.row.status)">{{ statusLabel(scope.row.status) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="发送时间" prop="sentTime" width="160" />
          <el-table-column label="签署时间" prop="signedTime" width="160" />
          <el-table-column label="操作" fixed="right" width="150">
            <template slot-scope="scope">
              <el-button type="text" size="mini" icon="el-icon-view" @click="handleDetail(scope.row)" v-hasPermi="['oa:laborContract:query']">详情</el-button>
              <el-button v-if="scope.row.status === 'pending_sign' || scope.row.status === 'signed'" type="text" size="mini" icon="el-icon-document" @click="handlePreview(scope.row)" v-hasPermi="['oa:laborContract:query']">文件</el-button>
            </template>
          </el-table-column>
        </el-table>
        <pagination
          v-show="total > 0"
          :total="total"
          :page.sync="queryParams.pageNum"
          :limit.sync="queryParams.pageSize"
          @pagination="getList"
        />
      </el-tab-pane>

    </el-tabs>

    <el-dialog :title="contractTitle" :visible.sync="contractOpen" width="960px" append-to-body>
      <el-form ref="contractForm" :model="contractForm" :rules="contractRules" label-width="116px" size="small">
        <el-row :gutter="12">
          <el-col :xs="24" :sm="12">
            <el-form-item label="员工账号" prop="employeeId">
              <el-select v-model="contractForm.employeeId" filterable placeholder="选择员工" style="width: 100%" @change="handleEmployeeChange">
                <el-option
                  v-for="user in employeeOptions"
                  :key="user.userId"
                  :label="user.contractOptionLabel || ((user.nickName || user.userName) + ' / ' + user.userName)"
                  :value="user.userId"
                />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :xs="24" :sm="12">
            <el-form-item label="合同标题" prop="contractTitle">
              <el-input v-model="contractForm.contractTitle" maxlength="120" placeholder="如：张三劳动合同" />
            </el-form-item>
          </el-col>
          <el-col :xs="24" :sm="12">
            <el-form-item label="员工姓名" prop="employeeName">
              <el-input v-model="contractForm.employeeName" />
            </el-form-item>
          </el-col>
          <el-col :xs="24" :sm="12">
            <el-form-item label="身份证号" prop="employeeIdCard">
              <el-input v-model="contractForm.employeeIdCard" maxlength="32" />
            </el-form-item>
          </el-col>
          <el-col :xs="24" :sm="12">
            <el-form-item label="手机号" prop="employeePhone">
              <el-input v-model="contractForm.employeePhone" maxlength="32" />
            </el-form-item>
          </el-col>
          <el-col :xs="24" :sm="12">
            <el-form-item label="薪资方案">
              <el-select v-model="contractForm.schemeId" clearable filterable placeholder="选择方案" style="width: 100%" @change="handleSchemeChange">
                <el-option v-for="item in schemeOptions" :key="schemeValue(item)" :label="schemeLabel(item)" :value="schemeValue(item)" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :xs="24" :sm="12">
            <el-form-item label="薪资档位">
              <el-select v-model="contractForm.salaryItemId" clearable filterable placeholder="选择档位" style="width: 100%" @change="handleSalaryItemChange">
                <el-option v-for="item in salaryItemOptions" :key="item.itemId" :label="salaryItemLabel(item)" :value="item.itemId" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :xs="24" :sm="12">
            <el-form-item label="合同模板" prop="templateId">
              <el-select v-model="contractForm.templateId" filterable placeholder="选择模板" style="width: 100%">
                <el-option v-for="item in activeTemplateOptions" :key="item.templateId" :label="item.templateName" :value="item.templateId" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :xs="24" :sm="12">
            <el-form-item label="社保口径" prop="socialType">
              <el-select v-model="contractForm.socialType" placeholder="选择口径" style="width: 100%" @change="syncTemplateBySocial">
                <el-option label="有社保" value="有社保" />
                <el-option label="无社保" value="无社保" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :xs="24" :sm="12">
            <el-form-item label="岗位" prop="postName">
              <el-input v-model="contractForm.postName" />
            </el-form-item>
          </el-col>
          <el-col :xs="24" :sm="12">
            <el-form-item label="合同编号">
              <el-input v-model="contractForm.contractNo" />
            </el-form-item>
          </el-col>
          <el-col :xs="24" :sm="12">
            <el-form-item label="合同开始" prop="contractStartDate">
              <el-date-picker v-model="contractForm.contractStartDate" type="date" value-format="yyyy-MM-dd" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :xs="24" :sm="12">
            <el-form-item label="合同结束" prop="contractEndDate">
              <el-date-picker v-model="contractForm.contractEndDate" type="date" value-format="yyyy-MM-dd" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :xs="24" :sm="12">
            <el-form-item label="试用开始">
              <el-date-picker v-model="contractForm.probationStartDate" type="date" value-format="yyyy-MM-dd" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :xs="24" :sm="12">
            <el-form-item label="试用结束">
              <el-date-picker v-model="contractForm.probationEndDate" type="date" value-format="yyyy-MM-dd" style="width: 100%" />
            </el-form-item>
          </el-col>
        </el-row>

        <el-divider content-position="left">薪资快照</el-divider>
        <el-row :gutter="12">
          <el-col v-for="field in moneyFields" :key="field.key" :xs="24" :sm="8">
            <el-form-item :label="field.label">
              <el-input-number v-model="contractForm[field.key]" :min="0" :precision="2" controls-position="right" style="width: 100%" @change="handleMoneyChange(field.key)" />
            </el-form-item>
          </el-col>
        </el-row>

        <el-row :gutter="12">
          <el-col :xs="24" :sm="12">
            <el-form-item label="住宿约定">
              <el-input v-model="contractForm.accommodationText" type="textarea" :rows="2" maxlength="500" />
            </el-form-item>
          </el-col>
          <el-col :xs="24" :sm="12">
            <el-form-item label="通勤约定">
              <el-input v-model="contractForm.commuteText" type="textarea" :rows="2" maxlength="500" />
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>
      <div slot="footer">
        <el-button @click="contractOpen = false">取 消</el-button>
        <el-button type="primary" :loading="saving" @click="submitContract" v-hasPermi="['oa:laborContract:add']">保 存</el-button>
      </div>
    </el-dialog>

    <el-dialog title="合同详情" :visible.sync="detailOpen" width="880px" append-to-body>
      <el-descriptions v-if="detail" :column="2" border size="small">
        <el-descriptions-item label="员工">{{ detail.employeeName }}</el-descriptions-item>
        <el-descriptions-item label="状态">{{ statusLabel(detail.status) }}</el-descriptions-item>
        <el-descriptions-item label="身份证">{{ detail.employeeIdCard }}</el-descriptions-item>
        <el-descriptions-item label="手机号">{{ detail.employeePhone }}</el-descriptions-item>
        <el-descriptions-item label="岗位">{{ detail.postName }}</el-descriptions-item>
        <el-descriptions-item label="社保">{{ detail.socialType }}</el-descriptions-item>
        <el-descriptions-item label="合同期限">{{ detail.contractStartDate }} 至 {{ detail.contractEndDate }}</el-descriptions-item>
        <el-descriptions-item label="签署版本">{{ detail.documentVersion || '-' }}</el-descriptions-item>
        <el-descriptions-item label="预览哈希">{{ detail.previewFileHash || '-' }}</el-descriptions-item>
        <el-descriptions-item label="归档哈希">{{ detail.archiveFileHash || detail.contractFileHash || '-' }}</el-descriptions-item>
        <el-descriptions-item label="完成证明哈希">{{ detail.certificateFileHash || '-' }}</el-descriptions-item>
      </el-descriptions>
      <el-table v-if="detail && detail.events" :data="detail.events" size="small" border class="event-table">
        <el-table-column label="事件" width="100">
          <template slot-scope="scope">{{ eventTypeLabel(scope.row.eventType) }}</template>
        </el-table-column>
        <el-table-column label="摘要" prop="eventSummary" min-width="180" />
        <el-table-column label="操作者" prop="operatorName" width="110" />
        <el-table-column label="签署网络地址" prop="clientIp" width="130" />
        <el-table-column label="文档哈希" prop="documentHash" min-width="220" show-overflow-tooltip />
        <el-table-column label="前序哈希" prop="prevEventHash" min-width="220" show-overflow-tooltip />
        <el-table-column label="事件哈希" prop="eventHash" min-width="220" show-overflow-tooltip />
        <el-table-column label="时间" prop="createTime" width="160" />
      </el-table>
    </el-dialog>

    <el-dialog title="合同文件验真" :visible.sync="verifyOpen" width="640px" append-to-body>
      <el-form :model="verifyForm" label-width="112px" size="small">
        <el-form-item label="SHA-256 哈希">
          <el-input
            v-model.trim="verifyForm.hash"
            type="textarea"
            :rows="3"
            maxlength="128"
            placeholder="粘贴 PDF、Word 合同或完成证明的 SHA-256 哈希"
          />
        </el-form-item>
      </el-form>
      <el-alert
        v-if="verifyResult"
        :type="verifyResult.matched ? 'success' : 'warning'"
        :closable="false"
        class="verify-result"
      >
        <template slot="title">
          <span v-if="verifyResult.matched">
            已匹配：{{ verifyResult.contractNo || verifyResult.contractId }} / {{ verifyResult.employeeName }} / {{ fileKindLabel(verifyResult.fileKind) }}
          </span>
          <span v-else>未匹配到合同文件</span>
        </template>
      </el-alert>
      <div slot="footer">
        <el-button @click="verifyOpen = false">取 消</el-button>
        <el-button type="primary" :loading="verifyLoading" @click="submitVerifyHash">验 证</el-button>
      </div>
    </el-dialog>

    <el-dialog :title="templateTitle" :visible.sync="templateOpen" width="620px" append-to-body>
      <el-form ref="templateForm" :model="templateForm" :rules="templateRules" label-width="110px" size="small">
        <el-form-item label="模板名称" prop="templateName">
          <el-input v-model="templateForm.templateName" maxlength="100" />
        </el-form-item>
        <el-form-item label="社保口径" prop="socialType">
          <el-select v-model="templateForm.socialType" style="width: 100%">
            <el-option label="有社保" value="有社保" />
            <el-option label="无社保" value="无社保" />
          </el-select>
        </el-form-item>
        <el-form-item label="版本">
          <el-input v-model="templateForm.templateVersion" maxlength="32" />
        </el-form-item>
        <el-form-item label="模板文件" prop="templateFileUrl">
          <file-upload v-model="templateForm.templateFileUrl" :limit="1" :file-size="20" :file-type="['docx']" />
          <div class="form-tip">模板必须包含员工姓名、身份证号、手机号、合同期限和岗位占位符，普通空线 Word 文件无法自动填充。</div>
        </el-form-item>
        <el-form-item label="状态">
          <el-radio-group v-model="templateForm.status">
            <el-radio label="0">启用</el-radio>
            <el-radio label="1">停用</el-radio>
          </el-radio-group>
        </el-form-item>
      </el-form>
      <div slot="footer">
        <el-button @click="templateOpen = false">取 消</el-button>
        <el-button type="primary" :loading="templateSaving" @click="submitTemplate" v-hasPermi="['oa:laborContract:template:add']">保 存</el-button>
      </div>
    </el-dialog>

    <el-dialog title="企业章" :visible.sync="sealOpen" width="560px" append-to-body>
      <el-form :model="sealForm" label-width="96px" size="small">
        <el-form-item label="名称">
          <el-input v-model="sealForm.sealName" maxlength="100" />
        </el-form-item>
        <el-form-item label="图片">
          <image-upload v-model="sealForm.sealImageUrl" :limit="1" :file-size="5" />
        </el-form-item>
        <el-form-item label="状态">
          <el-radio-group v-model="sealForm.status">
            <el-radio label="0">启用</el-radio>
            <el-radio label="1">停用</el-radio>
          </el-radio-group>
        </el-form-item>
      </el-form>
      <div slot="footer">
        <el-button @click="sealOpen = false">取 消</el-button>
        <el-button type="primary" :loading="sealSaving" @click="submitSeal" v-hasPermi="['oa:laborContract:template:add']">保 存</el-button>
      </div>
    </el-dialog>
  </div>
</template>

<script>
import {
  downloadLaborContractFile,
  getLaborContract,
  getLaborContractSeal,
  listLaborContract,
  listLaborContractTemplate,
  previewLaborContract,
  saveLaborContract,
  saveLaborContractSeal,
  saveLaborContractTemplate,
  sendLaborContract,
  verifyLaborContractHash,
  voidLaborContract
} from "@/api/oa/laborContract"
import { listUser } from "@/api/system/user"
import { listSalaryItems, salarySchemeOptions } from "@/api/system/salaryConfig"
import { getSelectedDeptContext, hasValidatedSelectedDeptContext } from "@/utils/shopContext"
const { normalizeProtectedFileBlob } = require("@/utils/protectedFileBlob")

export default {
  name: "OaLaborContract",
  data() {
    return {
      activeTab: "contract",
      loading: false,
      templateLoading: false,
      saving: false,
      templateSaving: false,
      sealSaving: false,
      verifyLoading: false,
      total: 0,
      contractList: [],
      templateList: [],
      employeeOptions: [],
      schemeOptions: [],
      salaryItemOptions: [],
      queryParams: { pageNum: 1, pageSize: 10 },
      templateQuery: {},
      contractOpen: false,
      templateOpen: false,
      detailOpen: false,
      sealOpen: false,
      verifyOpen: false,
      contractTitle: "发起签约",
      templateTitle: "新增模板",
      detail: null,
      verifyForm: { hash: "" },
      verifyResult: null,
      contractForm: this.defaultContractForm(),
      templateForm: this.defaultTemplateForm(),
      sealForm: { sealId: undefined, sealName: "默认企业章", sealImageUrl: "", status: "0" },
      statusOptions: [
        { value: "draft", label: "草稿" },
        { value: "pending_sign", label: "待员工签" },
        { value: "signed", label: "已签" },
        { value: "voided", label: "作废" },
        { value: "expired", label: "过期" }
      ],
      moneyFields: [
        { key: "baseSalary", label: "基本工资" },
        { key: "managementAllowance", label: "管理津贴" },
        { key: "overtimePay", label: "加班费" },
        { key: "rewardAllowance", label: "奖励津贴" },
        { key: "fullAttendanceBonus", label: "全勤奖" },
        { key: "socialSubsidy", label: "社保补贴" },
        { key: "commuteSubsidy", label: "通勤补贴" },
        { key: "totalSalary", label: "工资合计" }
      ],
      contractRules: {
        employeeId: [{ required: true, message: "请选择员工", trigger: "change" }],
        employeeName: [{ required: true, message: "请输入员工姓名", trigger: "blur" }],
        employeeIdCard: [{ required: true, message: "请输入身份证号", trigger: "blur" }],
        employeePhone: [{ required: true, message: "请输入手机号", trigger: "blur" }],
        templateId: [{ required: true, message: "请选择合同模板", trigger: "change" }],
        socialType: [{ required: true, message: "请选择社保口径", trigger: "change" }],
        postName: [{ required: true, message: "请输入岗位", trigger: "blur" }],
        contractStartDate: [{ required: true, message: "请选择合同开始日期", trigger: "change" }],
        contractEndDate: [{ required: true, message: "请选择合同结束日期", trigger: "change" }]
      },
      templateRules: {
        templateName: [{ required: true, message: "请输入模板名称", trigger: "blur" }],
        socialType: [{ required: true, message: "请选择社保口径", trigger: "change" }],
        templateFileUrl: [{ required: true, message: "请上传模板文件", trigger: "change" }]
      }
    }
  },
  computed: {
    activeTemplateOptions() {
      return this.templateList.filter(item => item.status === "0")
    },
    canListLaborContracts() {
      return this.hasPagePermission("oa:laborContract:list")
    },
    canManageLaborContractTemplates() {
      return !this.$auth || this.$auth.hasPermi("oa:laborContract:template:list")
    },
    canLoadLaborContractEmployees() {
      return this.hasPagePermission("oa:laborContract:add") && this.hasPagePermission("system:user:list")
    },
    canLoadSalarySchemes() {
      return this.hasPagePermission("system:salary:query")
    }
  },
  created() {
    this.bootstrapLaborContractPage()
  },
  methods: {
    hasPagePermission(permission) {
      return !this.$auth || this.$auth.hasPermi(permission)
    },
    bootstrapLaborContractPage() {
      this.activeTab = "contract"
      const tasks = []
      if (this.canListLaborContracts) {
        tasks.push(this.safeLoadLaborContractResource(() => this.getList(), "加载历史劳动合同失败"))
      }
      return Promise.all(tasks)
    },
    safeLoadLaborContractResource(loader, fallbackMessage) {
      return Promise.resolve()
        .then(loader)
        .catch(error => {
          this.showLaborContractFriendlyError(error, fallbackMessage)
          return null
        })
    },
    showLaborContractFriendlyError(error, fallbackMessage) {
      const message = this.getFriendlyLaborContractErrorMessage(error, fallbackMessage)
      if (this.$modal && this.$modal.msgWarning) {
        this.$modal.msgWarning(message)
      }
    },
    getFriendlyLaborContractErrorMessage(error, fallbackMessage) {
      const rawMessage = this.extractRequestErrorMessage(error)
      if (!rawMessage) {
        return fallbackMessage
      }
      if (/SQL|Mapper|LIMIT|syntax|SQLException|MyBatis|java\.|Exception|select\s+/i.test(rawMessage)) {
        return fallbackMessage + "，请稍后重试或联系管理员"
      }
      if (/无权限|未授权|403|401|NotPermission/i.test(rawMessage)) {
        return "当前账号无权访问该功能"
      }
      return rawMessage
    },
    extractRequestErrorMessage(error) {
      if (!error) {
        return ""
      }
      if (typeof error === "string") {
        return error === "error" ? "" : error
      }
      if (error.response && error.response.data) {
        return error.response.data.msg || error.response.data.message || ""
      }
      return error.msg || error.message || ""
    },
    defaultContractForm() {
      return {
        contractId: undefined,
        templateId: undefined,
        employeeId: undefined,
        employeeName: "",
        employeeIdCard: "",
        employeePhone: "",
        employeeDeptId: undefined,
        employeeDeptName: "",
        schemeId: undefined,
        salaryItemId: undefined,
        contractNo: "",
        contractTitle: "",
        postName: "",
        socialType: "有社保",
        contractStartDate: "",
        contractEndDate: "",
        probationStartDate: "",
        probationEndDate: "",
        baseSalary: 0,
        managementAllowance: 0,
        overtimePay: 0,
        rewardAllowance: 0,
        fullAttendanceBonus: 0,
        socialSubsidy: 0,
        commuteSubsidy: 0,
        totalSalary: 0,
        accommodationText: "",
        commuteText: ""
      }
    },
    defaultTemplateForm() {
      return {
        templateId: undefined,
        templateName: "",
        socialType: "有社保",
        templateVersion: "",
        templateFileUrl: "",
        status: "0",
        builtIn: "N"
      }
    },
    getList() {
      this.loading = true
      return listLaborContract(this.queryParams).then(res => {
        this.contractList = res.rows || []
        this.total = res.total || 0
      }).catch(error => {
        this.contractList = []
        this.total = 0
        this.showLaborContractFriendlyError(error, "加载签约单失败")
      }).finally(() => {
        this.loading = false
      })
    },
    getTemplates() {
      if (!this.canManageLaborContractTemplates) {
        this.templateList = []
        return Promise.resolve([])
      }
      this.templateLoading = true
      return listLaborContractTemplate(this.templateQuery).then(res => {
        this.templateList = res.data || []
      }).catch(error => {
        this.templateList = []
        this.showLaborContractFriendlyError(error, "加载合同模板失败")
      }).finally(() => {
        this.templateLoading = false
      })
    },
    ensureShopContextForEmployeeOptions(showWarning = true) {
      if (hasValidatedSelectedDeptContext()) {
        return true
      }
      this.employeeOptions = []
      if (showWarning && this.$modal && this.$modal.msgWarning) {
        const context = getSelectedDeptContext()
        const suffix = context.deptName ? `：${context.deptName}` : ""
        this.$modal.msgWarning(`请先选择并验证当前店铺${suffix}`)
      }
      return false
    },
    loadEmployees() {
      if (!this.ensureShopContextForEmployeeOptions(false)) {
        return Promise.resolve([])
      }
      if (!this.canLoadLaborContractEmployees) {
        this.employeeOptions = []
        return Promise.resolve([])
      }
      return listUser({ pageNum: 1, pageSize: 200, status: "0" }, { silentError: true }).then(res => {
        this.employeeOptions = (res.rows || []).map(user => Object.assign({}, user, {
          contractOptionLabel: this.employeeOptionLabel(user)
        }))
      }).catch(error => {
        this.employeeOptions = []
        this.showLaborContractFriendlyError(error, "加载员工选项失败")
      })
    },
    loadSchemes() {
      if (!this.canLoadSalarySchemes) {
        this.schemeOptions = []
        return Promise.resolve([])
      }
      return salarySchemeOptions({ silentError: true }).then(res => {
        this.schemeOptions = res.data || []
      }).catch(error => {
        this.schemeOptions = []
        this.showLaborContractFriendlyError(error, "加载薪资方案失败")
      })
    },
    loadSeal() {
      if (!this.canManageLaborContractTemplates) {
        return Promise.resolve(null)
      }
      return getLaborContractSeal().then(res => {
        if (res.data) {
          this.sealForm = Object.assign(this.sealForm, res.data)
        }
      }).catch(error => {
        this.showLaborContractFriendlyError(error, "加载企业章失败")
      })
    },
    resetQuery() {
      this.queryParams = { pageNum: 1, pageSize: 10 }
      this.getList()
    },
    handleAdd() {
      if (!this.ensureShopContextForEmployeeOptions()) {
        return
      }
      if (!this.canManageLaborContractTemplates) {
        this.$modal.msgWarning("当前账号缺少合同模板权限，暂不能发起签约")
        return
      }
      this.contractTitle = "发起签约"
      this.contractForm = this.defaultContractForm()
      this.syncTemplateBySocial()
      this.contractOpen = true
      this.$nextTick(() => this.$refs.contractForm && this.$refs.contractForm.clearValidate())
    },
    handleEdit(row) {
      this.contractTitle = "编辑签约单"
      getLaborContract(row.contractId).then(res => {
        this.contractForm = Object.assign(this.defaultContractForm(), res.data || {})
        if (this.contractForm.schemeId) {
          this.handleSchemeChange(this.contractForm.schemeId, true)
        }
        this.contractOpen = true
      })
    },
    handleDetail(row) {
      getLaborContract(row.contractId).then(res => {
        this.detail = res.data
        this.detailOpen = true
      })
    },
    handleSend(row) {
      this.$modal.confirm("确认发送给员工签署？").then(() => {
        return sendLaborContract(row.contractId)
      }).then(() => {
        this.$modal.msgSuccess("已发送")
        this.getList()
      })
    },
    handleVoid(row) {
      this.$modal.confirm("确认作废该合同？").then(() => {
        return voidLaborContract(row.contractId)
      }).then(() => {
        this.$modal.msgSuccess("已作废")
        this.getList()
      })
    },
    handlePreview(row) {
      const previewWindow = window.open("about:blank", "_blank")
      if (!previewWindow) {
        this.$modal.msgWarning("浏览器阻止了新窗口，请允许弹出窗口后重试")
        return
      }
      previewLaborContract(row.contractId).then(res => {
        const data = res.data || {}
        const url = data.pdfFileUrl || data.archiveFileUrl || data.previewFileUrl || row.pdfFileUrl || row.archiveFileUrl || row.previewFileUrl
        return this.openFile(url, previewWindow, data.contractId || row.contractId)
      }).catch(() => {
        if (!previewWindow.closed) {
          previewWindow.close()
        }
      })
    },
    handleVerifyHash() {
      this.verifyForm = { hash: "" }
      this.verifyResult = null
      this.verifyOpen = true
    },
    submitVerifyHash() {
      if (!this.verifyForm.hash) {
        this.$modal.msgWarning("请输入文件哈希")
        return
      }
      this.verifyLoading = true
      verifyLaborContractHash({ hash: this.verifyForm.hash }).then(res => {
        this.verifyResult = res.data || { matched: false }
      }).finally(() => {
        this.verifyLoading = false
      })
    },
    submitContract() {
      this.$refs.contractForm.validate(valid => {
        if (!valid) return
        this.saving = true
        saveLaborContract(this.contractForm).then(() => {
          this.$modal.msgSuccess("已保存")
          this.contractOpen = false
          this.getList()
        }).finally(() => {
          this.saving = false
        })
      })
    },
    handleEmployeeChange(userId) {
      const user = this.employeeOptions.find(item => item.userId === userId)
      if (!user) return
      this.contractForm.employeeName = user.nickName || user.userName
      this.contractForm.employeePhone = user.phonenumber || this.contractForm.employeePhone
      this.contractForm.employeeDeptId = user.deptId
      this.contractForm.employeeDeptName = user.dept ? user.dept.deptName : user.deptName
    },
    employeeOptionLabel(user) {
      const name = user.nickName || user.userName
      const account = user.userName && user.userName !== name ? user.userName : ""
      const deptName = user.dept && user.dept.deptName ? user.dept.deptName : user.deptName
      const postName = user.postName || (user.posts && user.posts.length ? user.posts.map(item => item.postName).filter(Boolean).join("、") : "")
      return [name, account, deptName, postName].filter(Boolean).join(" / ")
    },
    handleSchemeChange(schemeId, keepItem) {
      const scheme = this.schemeOptions.find(item => String(this.schemeValue(item)) === String(schemeId))
      if (scheme && scheme.socialType) {
        this.contractForm.socialType = scheme.socialType
        this.syncTemplateBySocial()
      }
      this.salaryItemOptions = []
      if (!schemeId) return
      listSalaryItems(schemeId).then(res => {
        this.salaryItemOptions = res.data || []
        if (!keepItem && this.salaryItemOptions.length === 1) {
          this.contractForm.salaryItemId = this.salaryItemOptions[0].itemId
          this.handleSalaryItemChange(this.contractForm.salaryItemId)
        }
      })
    },
    handleSalaryItemChange(itemId) {
      const item = this.salaryItemOptions.find(row => row.itemId === itemId)
      if (!item) return
      this.contractForm.postName = item.postName || this.contractForm.postName
      this.contractForm.baseSalary = item.baseSalary || 0
      this.contractForm.managementAllowance = item.managementAllowance || 0
      this.contractForm.overtimePay = item.overtimePay || 0
      this.contractForm.rewardAllowance = item.rewardAllowance || 0
      this.contractForm.fullAttendanceBonus = item.fullAttendanceBonus || 0
      this.contractForm.socialSubsidy = item.socialSubsidy || 0
      this.contractForm.commuteSubsidy = item.commuteSubsidy || 0
      this.contractForm.totalSalary = item.totalSalary || 0
      this.recalculateTotal()
    },
    recalculateTotal() {
      const keys = ["baseSalary", "managementAllowance", "overtimePay", "rewardAllowance", "fullAttendanceBonus", "socialSubsidy", "commuteSubsidy"]
      this.contractForm.totalSalary = keys.reduce((sum, key) => sum + Number(this.contractForm[key] || 0), 0)
    },
    handleMoneyChange(key) {
      if (key !== "totalSalary") {
        this.recalculateTotal()
      }
    },
    syncTemplateBySocial() {
      const current = this.templateList.find(item => item.templateId === this.contractForm.templateId)
      if (current && current.socialType === this.contractForm.socialType && current.status === "0") return
      const matched = this.templateList.find(item => item.socialType === this.contractForm.socialType && item.status === "0")
      this.contractForm.templateId = matched ? matched.templateId : undefined
    },
    handleAddTemplate() {
      this.templateTitle = "新增模板"
      this.templateForm = this.defaultTemplateForm()
      this.templateOpen = true
    },
    handleEditTemplate(row) {
      this.templateTitle = "编辑模板"
      this.templateForm = Object.assign(this.defaultTemplateForm(), row)
      this.templateOpen = true
    },
    submitTemplate() {
      this.$refs.templateForm.validate(valid => {
        if (!valid) return
        this.templateSaving = true
        saveLaborContractTemplate(this.templateForm).then(() => {
          this.$modal.msgSuccess("模板已保存")
          this.templateOpen = false
          this.getTemplates()
        }).finally(() => {
          this.templateSaving = false
        })
      })
    },
    submitSeal() {
      if (!this.sealForm.sealName || !this.sealForm.sealImageUrl) {
        this.$modal.msgWarning("请填写企业章名称并上传图片")
        return
      }
      this.sealSaving = true
      saveLaborContractSeal(this.sealForm).then(() => {
        this.$modal.msgSuccess("企业章已保存")
        this.sealOpen = false
        this.loadSeal()
      }).finally(() => {
        this.sealSaving = false
      })
    },
    schemeValue(item) {
      return item.schemeId || item.value
    },
    schemeLabel(item) {
      return item.schemeName || item.label || item.name
    },
    salaryItemLabel(item) {
      return [item.postName, item.gradeName, item.regionName].filter(Boolean).join(" / ") || item.itemName || item.itemId
    },
    statusLabel(status) {
      const item = this.statusOptions.find(row => row.value === status)
      return item ? item.label : "未知合同状态"
    },
    eventTypeLabel(eventType) {
      const labels = {
        create: "创建",
        update: "更新",
        send: "发送签署",
        void: "作废",
        sign: "完成签署",
        verify: "文件验真",
        download: "下载文件"
      }
      return labels[String(eventType || "").toLowerCase()] || "其他合同事件"
    },
    fileKindLabel(fileKind) {
      const labels = {
        preview: "预览文件",
        archive: "归档文件",
        certificate: "完成证明",
        unknown: "未知文件类型"
      }
      return labels[String(fileKind || "").toLowerCase()] || "未知文件类型"
    },
    statusTagType(status) {
      if (status === "signed") return "success"
      if (status === "pending_sign") return "warning"
      if (status === "voided" || status === "expired") return "info"
      return ""
    },
    normalizeFileUrl(url) {
      if (!url) return ""
      if (/^https?:\/\//.test(url)) return url
      if (url.indexOf("classpath:") === 0) return ""
      return process.env.VUE_APP_BASE_API + url
    },
    openFile(url, previewWindow, contractId) {
      const kind = this.fileKindFromUrl(url)
      if (!contractId || !kind) {
        if (previewWindow && !previewWindow.closed) {
          previewWindow.close()
        }
        this.$modal.msgWarning("文件地址不可直接打开")
        return Promise.resolve()
      }
      return downloadLaborContractFile(contractId, kind).then(blob => {
        return normalizeProtectedFileBlob(blob, kind)
      }).then(fileBlob => {
        const target = URL.createObjectURL(fileBlob)
        if (previewWindow) {
          if (previewWindow.closed) {
            URL.revokeObjectURL(target)
            return null
          }
          previewWindow.location.href = target
        } else {
          window.open(target, "_blank", "noopener,noreferrer")
        }
        setTimeout(() => URL.revokeObjectURL(target), 5 * 60 * 1000)
        return target
      }).catch(error => {
        if (previewWindow && !previewWindow.closed) {
          previewWindow.close()
        }
        this.$modal.msgWarning((error && error.message) || "合同文件校验失败")
        return null
      })
    },
    fileKindFromUrl(url) {
      const match = String(url || "").match(/\/download\/\d+\/([^/?#]+)/)
      return match ? match[1] : ""
    }
  }
}
</script>

<style scoped>
.labor-contract-page .toolbar-row {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 12px;
}

.labor-contract-page .el-select {
  width: 150px;
}

.event-table {
  margin-top: 14px;
}

.verify-result {
  margin-top: 12px;
}

.form-tip {
  margin-top: 6px;
  color: #909399;
  font-size: 12px;
  line-height: 1.5;
}

@media (max-width: 768px) {
  .labor-contract-page .toolbar-row {
    display: block;
  }
}
</style>
