<template>
  <div class="app-container system-management-page notice-workflow-page">
    <system-page-header
      title="通知公告"
      description="创建、发布并追踪通知公告，清晰掌握生命周期与接收范围。"
      icon="el-icon-bell"
      tip="已发布内容通过新版本更新，避免改写历史通知。"
    />
    <el-alert
      v-if="!workflowEnabled"
      title="公告发布工作流尚未开放：当前可以安全保存草稿，但不能发布或计划发布。"
      type="warning"
      :closable="false"
      show-icon
      class="notice-rollout-alert"
    />

    <div v-show="showSearch" class="search-card notice-search-card">
      <el-form ref="queryForm" :model="queryParams" size="small" :inline="true" label-width="88px">
        <el-form-item label="公告标题" prop="noticeTitle">
          <el-input v-model="queryParams.noticeTitle" label="搜索公告标题" placeholder="请输入公告标题" clearable @keyup.enter.native="handleQuery" />
        </el-form-item>
        <el-form-item label="类型" prop="noticeType">
          <el-select v-model="queryParams.noticeType" placeholder="公告类型" clearable>
            <el-option v-for="dict in dict.type.sys_notice_type" :key="dict.value" :label="dict.label" :value="dict.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="生命周期" prop="lifecycleStatus">
          <el-select v-model="queryParams.lifecycleStatus" placeholder="全部状态" clearable>
            <el-option v-for="item in lifecycleOptions" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="创建者" prop="createBy">
          <el-input v-model="queryParams.createBy" placeholder="请输入创建者" clearable @keyup.enter.native="handleQuery" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" icon="el-icon-search" size="mini" @click="handleQuery">搜索</el-button>
          <el-button icon="el-icon-refresh" size="mini" @click="resetQuery">重置</el-button>
        </el-form-item>
      </el-form>
    </div>

    <div class="content-card notice-toolbar-card">
      <el-row :gutter="10" class="mb8">
        <el-col :span="1.5">
          <el-button ref="addButton" type="primary" plain icon="el-icon-plus" size="mini" @click="handleAdd($event)" v-hasPermi="['system:notice:add']">新建草稿</el-button>
        </el-col>
        <el-col :span="1.5">
          <el-button type="success" plain icon="el-icon-edit" size="mini" :disabled="single || selectedLifecycle !== 'DRAFT'" @click="handleUpdate(null, $event)" v-hasPermi="['system:notice:edit']">编辑草稿</el-button>
        </el-col>
        <el-col :span="1.5">
          <el-button type="danger" plain icon="el-icon-delete" size="mini" :disabled="multiple || !allSelectedDrafts" @click="handleDelete()" v-hasPermi="['system:notice:remove']">删除草稿</el-button>
        </el-col>
        <right-toolbar :showSearch.sync="showSearch" @queryTable="getList" />
      </el-row>
    </div>

    <div class="table-card notice-table-card">
      <el-table v-loading="loading" :data="noticeList" @selection-change="handleSelectionChange">
        <el-table-column type="selection" width="48" align="center" :selectable="row => row.lifecycleStatus === 'DRAFT'" />
        <el-table-column label="公告" min-width="240">
          <template slot-scope="scope">
            <button type="button" class="notice-title-link" :aria-label="`查看公告：${scope.row.noticeTitle}`" @click="handleViewData(scope.row)">
              {{ scope.row.noticeTitle }}
            </button>
            <div class="notice-row-meta">版本 {{ scope.row.version }} · {{ audienceLabel(scope.row.audienceType) }}</div>
          </template>
        </el-table-column>
        <el-table-column label="类型" align="center" prop="noticeType" width="90">
          <template slot-scope="scope"><dict-tag :options="dict.type.sys_notice_type" :value="scope.row.noticeType" /></template>
        </el-table-column>
        <el-table-column label="生命周期" align="center" width="105">
          <template slot-scope="scope"><el-tag size="small" :type="lifecycleTagType(scope.row.lifecycleStatus)">{{ lifecycleLabel(scope.row.lifecycleStatus) }}</el-tag></template>
        </el-table-column>
        <el-table-column label="发布时间" min-width="165">
          <template slot-scope="scope">
            <span v-if="scope.row.lifecycleStatus === 'SCHEDULED'">计划 {{ scope.row.scheduledPublishTime || '—' }}</span>
            <span v-else>{{ scope.row.publishedTime || '尚未发布' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="有效期" min-width="150">
          <template slot-scope="scope">{{ scope.row.expireTime || '长期有效' }}</template>
        </el-table-column>
        <el-table-column label="接收人数" align="center" prop="recipientCount" width="90" />
        <el-table-column label="创建者" align="center" prop="createBy" width="100" />
        <el-table-column label="操作" align="center" width="340" class-name="small-padding">
          <template slot-scope="scope">
            <el-button v-if="scope.row.lifecycleStatus === 'DRAFT'" size="mini" type="text" icon="el-icon-edit" @click="handleUpdate(scope.row, $event)" v-hasPermi="['system:notice:edit']">编辑</el-button>
            <el-button v-if="scope.row.lifecycleStatus === 'DRAFT' && canPublish && workflowEnabled" size="mini" type="text" icon="el-icon-position" :aria-label="`发布公告：${scope.row.noticeTitle}`" @click="openPublish(scope.row, $event)">发布</el-button>
            <el-button v-if="scope.row.lifecycleStatus === 'SCHEDULED' && canPublish" size="mini" type="text" icon="el-icon-refresh-left" @click="handleCancelSchedule(scope.row)">取消计划</el-button>
            <el-button v-if="scope.row.lifecycleStatus === 'PUBLISHED' && canPublish" size="mini" type="text" icon="el-icon-circle-close" @click="handleOffline(scope.row)">下线</el-button>
            <el-button v-if="['PUBLISHED', 'OFFLINE'].includes(scope.row.lifecycleStatus)" size="mini" type="text" icon="el-icon-document-copy" @click="handleNewVersion(scope.row, $event)" v-hasPermi="['system:notice:edit']">创建新版本</el-button>
            <el-button v-if="['PUBLISHED', 'OFFLINE'].includes(scope.row.lifecycleStatus)" size="mini" type="text" icon="el-icon-user" @click="handleReadUsers(scope.row)" v-hasPermi="['system:notice:list']">阅读用户</el-button>
            <el-button v-if="scope.row.lifecycleStatus === 'DRAFT'" size="mini" type="text" icon="el-icon-delete" @click="handleDelete(scope.row)" v-hasPermi="['system:notice:remove']">删除</el-button>
          </template>
        </el-table-column>
        <template slot="empty">
          <div class="notice-empty-state">
            <i class="el-icon-bell" aria-hidden="true" />
            <p>还没有公告草稿</p>
            <span>公告发布后会显示在页面右上角的“通知公告”铃铛中。</span>
            <el-button type="primary" plain size="small" @click="handleAdd($event)" v-hasPermi="['system:notice:add']">新建公告</el-button>
          </div>
        </template>
      </el-table>
      <pagination v-show="total > 0" :total="total" :page.sync="queryParams.pageNum" :limit.sync="queryParams.pageSize" @pagination="getList" />
    </div>

    <el-dialog
      :title="form.noticeId ? '编辑公告草稿' : '新建公告草稿'"
      :visible.sync="open"
      width="900px"
      top="3vh"
      append-to-body
      destroy-on-close
      :close-on-click-modal="false"
      @opened="focusNoticeTitle"
      @closed="handleDialogClosed"
    >
      <el-form ref="form" :model="form" :rules="rules" label-width="96px">
        <el-row :gutter="16">
          <el-col :span="16">
            <el-form-item label="公告标题" prop="noticeTitle">
              <el-input ref="noticeTitleInput" v-model.trim="form.noticeTitle" label="公告草稿标题" maxlength="50" show-word-limit placeholder="请输入公告标题" @keyup.enter.native="focusNoticeType" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="公告类型" prop="noticeType">
              <el-select ref="noticeTypeSelect" v-model="form.noticeType" placeholder="请选择公告类型" style="width:100%">
                <el-option v-for="dict in dict.type.sys_notice_type" :key="dict.value" :label="dict.label" :value="dict.value" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>

        <el-form-item label="接收范围" prop="audienceType">
          <el-radio-group v-model="form.audienceType" @change="handleAudienceTypeChange">
            <el-radio-button label="ALL">全员</el-radio-button>
            <el-radio-button label="DEPT">组织/门店</el-radio-button>
            <el-radio-button label="ROLE">角色</el-radio-button>
            <el-radio-button label="USER">指定人员</el-radio-button>
            <el-radio-button label="MIXED">混合受众</el-radio-button>
          </el-radio-group>
          <div class="notice-field-help">发布时只选取启用账号并固化接收人快照，之后调岗或角色变化不会改写历史范围。</div>
        </el-form-item>

        <el-form-item v-if="form.audienceType === 'ALL'" label="受众说明">
          <el-alert title="全员：发布时所有启用且未删除的账号。" type="info" :closable="false" show-icon />
        </el-form-item>

        <el-form-item v-if="['DEPT', 'MIXED'].includes(form.audienceType)" label="组织/门店">
          <el-select v-model="selectedDeptIds" multiple filterable collapse-tags placeholder="请选择组织或门店" style="width:100%">
            <el-option v-for="item in audienceOptions.departments" :key="item.id" :label="item.label" :value="item.id">
              <span>{{ item.label }}</span><small class="notice-option-description">{{ item.description }}</small>
            </el-option>
          </el-select>
          <el-checkbox v-model="includeDeptChildren" class="notice-include-children">包含所选组织的下级部门/门店</el-checkbox>
        </el-form-item>

        <el-form-item v-if="['ROLE', 'MIXED'].includes(form.audienceType)" label="角色">
          <el-select v-model="selectedRoleIds" multiple filterable collapse-tags placeholder="请选择角色" style="width:100%">
            <el-option v-for="item in audienceOptions.roles" :key="item.id" :label="item.label" :value="item.id">
              <span>{{ item.label }}</span><small class="notice-option-description">{{ item.description }}</small>
            </el-option>
          </el-select>
        </el-form-item>

        <el-form-item v-if="['USER', 'MIXED'].includes(form.audienceType)" label="指定人员">
          <el-select v-model="selectedUserIds" multiple filterable remote reserve-keyword collapse-tags :remote-method="loadAudienceOptions" placeholder="按姓名、账号或组织搜索" style="width:100%">
            <el-option v-for="item in audienceOptions.users" :key="item.id" :label="item.label" :value="item.id">
              <span>{{ item.label }}</span><small class="notice-option-description">{{ item.description }}</small>
            </el-option>
          </el-select>
        </el-form-item>

        <el-form-item label="受众预览">
          <el-button :loading="previewLoading" icon="el-icon-view" @click="previewAudience">预览接收人数</el-button>
          <span v-if="audiencePreview" class="notice-preview-count" aria-live="polite">预计接收 {{ audiencePreview.recipientCount }} 人（已跨规则去重）</span>
          <div v-if="audiencePreview && audiencePreview.organizationCounts" class="notice-org-breakdown">
            <el-tag v-for="(count, name) in audiencePreview.organizationCounts" :key="name" size="small" type="info">{{ name }} {{ count }} 人</el-tag>
          </div>
        </el-form-item>

        <el-form-item label="到期时间">
          <el-date-picker v-model="form.expireTime" type="datetime" value-format="yyyy-MM-dd HH:mm:ss" placeholder="不填表示长期有效" style="width:280px" />
        </el-form-item>

        <el-form-item label="公告内容" prop="noticeContent">
          <editor v-model="form.noticeContent" :min-height="220" aria-label="公告内容" />
        </el-form-item>

        <el-form-item label="效果预览">
          <el-radio-group v-model="previewDevice" size="mini">
            <el-radio-button label="desktop">桌面</el-radio-button>
            <el-radio-button label="mobile">移动</el-radio-button>
          </el-radio-group>
          <article :class="['notice-content-preview', previewDevice === 'mobile' ? 'is-mobile' : 'is-desktop']" aria-label="公告效果预览">
            <h3>{{ form.noticeTitle || '公告标题' }}</h3>
            <div v-if="form.noticeContent" v-html="safePreviewHtml" />
            <p v-else class="notice-preview-placeholder">公告内容将显示在这里</p>
          </article>
        </el-form-item>
      </el-form>
      <div slot="footer" class="dialog-footer notice-editor-footer">
        <span>保存只会生成草稿，不会向任何人广播。</span>
        <el-button @click="open = false">取 消</el-button>
        <el-button type="primary" :loading="saving" @click="submitDraft">保存草稿</el-button>
      </div>
    </el-dialog>

    <el-dialog
      title="发布确认"
      :visible.sync="publishOpen"
      width="620px"
      append-to-body
      destroy-on-close
      :close-on-click-modal="false"
      @opened="focusPublishMode"
      @closed="handleDialogClosed"
    >
      <el-alert title="发布后将固化接收人快照；已发布内容不能原地覆盖，只能创建新版本。" type="warning" :closable="false" show-icon />
      <el-descriptions v-if="publishTarget" :column="2" border class="publish-summary">
        <el-descriptions-item label="公告标题" :span="2">{{ publishTarget.noticeTitle }}</el-descriptions-item>
        <el-descriptions-item label="预计接收">{{ publishPreview ? publishPreview.recipientCount : 0 }} 人</el-descriptions-item>
        <el-descriptions-item label="当前版本">{{ publishTarget.version }}</el-descriptions-item>
        <el-descriptions-item label="有效期" :span="2">{{ publishForm.expireTime || '长期有效' }}</el-descriptions-item>
      </el-descriptions>
      <el-form ref="publishForm" :model="publishForm" label-width="100px" class="publish-form">
        <el-form-item label="发布方式">
          <el-radio-group ref="publishMode" v-model="publishForm.publishMode">
            <el-radio label="IMMEDIATE">立即发布</el-radio>
            <el-radio label="SCHEDULED">计划发布</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item v-if="publishForm.publishMode === 'SCHEDULED'" label="计划时间">
          <el-date-picker v-model="publishForm.scheduledPublishTime" type="datetime" value-format="yyyy-MM-dd HH:mm:ss" placeholder="请选择未来时间" style="width:100%" />
        </el-form-item>
      </el-form>
      <div slot="footer" class="dialog-footer">
        <el-button @click="publishOpen = false">取 消</el-button>
        <el-button type="primary" :loading="publishing" @click="confirmPublish">{{ publishForm.publishMode === 'SCHEDULED' ? '确认计划发布' : '确认发布' }}</el-button>
      </div>
    </el-dialog>

    <notice-detail-view ref="noticeViewRef" />
    <read-users-dialog ref="readUsersRef" />
  </div>
</template>

<script>
import NoticeDetailView from "@/layout/components/HeaderNotice/DetailView"
import ReadUsersDialog from "./ReadUsers"
import { sanitizeNoticeHtml } from "@/utils/sanitizeNoticeHtml"
import {
  listNotice, getNotice, delNotice, addNotice, updateNotice,
  previewNoticeAudience, getNoticeAudienceOptions, publishNotice,
  cancelNoticeSchedule, offlineNotice, createNoticeVersion
} from "@/api/system/notice"

function defaultForm() {
  return {
    noticeId: undefined,
    noticeTitle: "",
    noticeType: "1",
    noticeContent: "",
    status: "1",
    lifecycleStatus: "DRAFT",
    audienceType: "ALL",
    audiences: [{ targetType: "ALL", targetId: 0, includeChildren: false }],
    expireTime: null,
    version: undefined
  }
}

export default {
  name: "Notice",
  components: { NoticeDetailView, ReadUsersDialog },
  dicts: ["sys_notice_type"],
  data() {
    return {
      loading: true,
      saving: false,
      publishing: false,
      previewLoading: false,
      showSearch: true,
      total: 0,
      ids: [],
      selectedRows: [],
      single: true,
      multiple: true,
      noticeList: [],
      open: false,
      publishOpen: false,
      form: defaultForm(),
      publishTarget: null,
      publishPreview: null,
      publishForm: { publishMode: "IMMEDIATE", scheduledPublishTime: null, expireTime: null },
      audiencePreview: null,
      previewDevice: "desktop",
      selectedDeptIds: [],
      selectedRoleIds: [],
      selectedUserIds: [],
      includeDeptChildren: true,
      audienceOptions: { departments: [], roles: [], users: [] },
      lastTrigger: null,
      queryParams: { pageNum: 1, pageSize: 10, noticeTitle: undefined, createBy: undefined, noticeType: undefined, lifecycleStatus: undefined },
      lifecycleOptions: [
        { value: "DRAFT", label: "草稿" },
        { value: "SCHEDULED", label: "待发布" },
        { value: "PUBLISHED", label: "已发布" },
        { value: "OFFLINE", label: "已下线" }
      ],
      rules: {
        noticeTitle: [{ required: true, message: "公告标题不能为空", trigger: "blur" }],
        noticeType: [{ required: true, message: "公告类型不能为空", trigger: "change" }],
        noticeContent: [{ required: true, message: "公告内容不能为空", trigger: "change" }],
        audienceType: [{ required: true, message: "接收范围不能为空", trigger: "change" }]
      }
    }
  },
  computed: {
    workflowEnabled() {
      return Boolean(this.$store.getters.businessFeatures && this.$store.getters.businessFeatures.noticeWorkflow)
    },
    canPublish() {
      return Boolean(this.$auth && this.$auth.hasPermi("system:notice:publish"))
    },
    selectedLifecycle() {
      return this.selectedRows.length === 1 ? this.selectedRows[0].lifecycleStatus : ""
    },
    allSelectedDrafts() {
      return this.selectedRows.length > 0 && this.selectedRows.every(row => row.lifecycleStatus === "DRAFT")
    },
    safePreviewHtml() {
      return sanitizeNoticeHtml(this.form.noticeContent)
    }
  },
  created() {
    this.getList()
  },
  methods: {
    getList() {
      this.loading = true
      return listNotice(this.queryParams).then(response => {
        this.noticeList = response.rows || []
        this.total = response.total || 0
      }).finally(() => { this.loading = false })
    },
    handleQuery() {
      this.queryParams.pageNum = 1
      this.getList()
    },
    resetQuery() {
      this.resetForm("queryForm")
      this.handleQuery()
    },
    handleSelectionChange(selection) {
      this.selectedRows = selection
      this.ids = selection.map(item => item.noticeId)
      this.single = selection.length !== 1
      this.multiple = selection.length === 0
    },
    captureTrigger(event) {
      const source = event && (event.currentTarget || event.target)
      const target = source && typeof source.closest === "function"
        ? source.closest("button, a[href], input, select, textarea, [tabindex]:not([tabindex='-1'])")
        : null
      if (target && typeof target.focus === "function") this.lastTrigger = target
    },
    handleAdd(event) {
      this.captureTrigger(event)
      this.resetEditor()
      this.open = true
      this.loadAudienceOptions("")
    },
    handleUpdate(row, event) {
      this.captureTrigger(event)
      const noticeId = row && row.noticeId ? row.noticeId : this.ids[0]
      if (!noticeId) return
      getNotice(noticeId).then(response => {
        if (response.data.lifecycleStatus !== "DRAFT") {
          this.$modal.msgWarning("已发布内容不能原地修改，请创建新版本")
          return
        }
        this.setEditorForm(response.data)
        this.open = true
        this.loadAudienceOptions("")
      })
    },
    resetEditor() {
      this.form = defaultForm()
      this.selectedDeptIds = []
      this.selectedRoleIds = []
      this.selectedUserIds = []
      this.includeDeptChildren = true
      this.audiencePreview = null
      this.previewDevice = "desktop"
      this.$nextTick(() => this.$refs.form && this.$refs.form.clearValidate())
    },
    setEditorForm(data) {
      this.resetEditor()
      this.form = Object.assign(defaultForm(), data, { status: "1", lifecycleStatus: "DRAFT" })
      const audiences = Array.isArray(data.audiences) ? data.audiences : []
      this.selectedDeptIds = audiences.filter(item => item.targetType === "DEPT").map(item => item.targetId)
      this.selectedRoleIds = audiences.filter(item => item.targetType === "ROLE").map(item => item.targetId)
      this.selectedUserIds = audiences.filter(item => item.targetType === "USER").map(item => item.targetId)
      const deptRules = audiences.filter(item => item.targetType === "DEPT")
      this.includeDeptChildren = deptRules.length === 0 || deptRules.some(item => item.includeChildren)
    },
    handleAudienceTypeChange() {
      this.audiencePreview = null
    },
    buildAudienceRules() {
      if (this.form.audienceType === "ALL") return [{ targetType: "ALL", targetId: 0, includeChildren: false }]
      const rules = []
      if (["DEPT", "MIXED"].includes(this.form.audienceType)) {
        this.selectedDeptIds.forEach(id => rules.push({ targetType: "DEPT", targetId: id, includeChildren: this.includeDeptChildren }))
      }
      if (["ROLE", "MIXED"].includes(this.form.audienceType)) {
        this.selectedRoleIds.forEach(id => rules.push({ targetType: "ROLE", targetId: id, includeChildren: false }))
      }
      if (["USER", "MIXED"].includes(this.form.audienceType)) {
        this.selectedUserIds.forEach(id => rules.push({ targetType: "USER", targetId: id, includeChildren: false }))
      }
      return rules
    },
    loadAudienceOptions(keyword) {
      return getNoticeAudienceOptions(keyword || undefined).then(response => {
        const selectedUsers = this.audienceOptions.users.filter(item => this.selectedUserIds.includes(item.id))
        const next = response.data || {}
        next.users = [...selectedUsers, ...(next.users || [])].filter((item, index, values) => values.findIndex(v => v.id === item.id) === index)
        this.audienceOptions = Object.assign({ departments: [], roles: [], users: [] }, next)
      })
    },
    previewAudience() {
      const audiences = this.buildAudienceRules()
      if (audiences.length === 0) {
        this.$modal.msgWarning("请至少选择一项接收范围")
        return Promise.reject(new Error("empty audience"))
      }
      this.previewLoading = true
      return previewNoticeAudience({ audienceType: this.form.audienceType, audiences }).then(response => {
        this.audiencePreview = response.data
        return response.data
      }).finally(() => { this.previewLoading = false })
    },
    submitDraft() {
      this.$refs.form.validate(valid => {
        if (!valid) return
        const audiences = this.buildAudienceRules()
        if (audiences.length === 0) {
          this.$modal.msgWarning("请至少选择一项接收范围")
          return
        }
        const payload = Object.assign({}, this.form, { status: "1", lifecycleStatus: "DRAFT", audiences })
        this.saving = true
        const request = payload.noticeId ? updateNotice(payload) : addNotice(payload)
        request.then(response => {
          this.form = response.data || payload
          this.$modal.msgSuccess("草稿保存成功，尚未发布")
          this.open = false
          this.getList()
          this.restoreTriggerFocus()
        }).catch(this.handleWorkflowError).finally(() => { this.saving = false })
      })
    },
    openPublish(row, event) {
      this.captureTrigger(event)
      if (!this.workflowEnabled || !this.canPublish) return
      getNotice(row.noticeId).then(response => {
        const detail = response.data
        return previewNoticeAudience({ audienceType: detail.audienceType, audiences: detail.audiences }).then(preview => {
          if (!preview.data || preview.data.recipientCount <= 0) {
            this.$modal.msgWarning("受众范围内没有启用账号，不能发布")
            return
          }
          this.publishTarget = detail
          this.publishPreview = preview.data
          this.publishForm = { publishMode: "IMMEDIATE", scheduledPublishTime: null, expireTime: detail.expireTime || null }
          this.publishOpen = true
        })
      }).catch(this.handleWorkflowError)
    },
    confirmPublish() {
      if (!this.publishTarget) return
      if (this.publishForm.publishMode === "SCHEDULED" && !this.publishForm.scheduledPublishTime) {
        this.$modal.msgWarning("请选择计划发布时间")
        return
      }
      this.publishing = true
      publishNotice(this.publishTarget.noticeId, {
        version: this.publishTarget.version,
        publishMode: this.publishForm.publishMode,
        scheduledPublishTime: this.publishForm.publishMode === "SCHEDULED" ? this.publishForm.scheduledPublishTime : null,
        expireTime: this.publishForm.expireTime
      }).then(() => {
        this.$modal.msgSuccess(this.publishForm.publishMode === "SCHEDULED" ? "计划发布已设置" : "公告发布成功")
        this.publishOpen = false
        this.getList()
        this.restoreTriggerFocus()
      }).catch(this.handleWorkflowError).finally(() => { this.publishing = false })
    },
    handleCancelSchedule(row) {
      this.$modal.confirm(`取消公告「${row.noticeTitle}」的计划发布并退回草稿吗？`).then(() => cancelNoticeSchedule(row.noticeId, row.version)).then(() => {
        this.$modal.msgSuccess("已取消计划发布")
        this.getList()
      }).catch(error => { if (error && error.response) this.handleWorkflowError(error) })
    },
    handleOffline(row) {
      this.$modal.confirm(`下线公告「${row.noticeTitle}」吗？历史接收人和阅读记录会保留。`).then(() => offlineNotice(row.noticeId, row.version)).then(() => {
        this.$modal.msgSuccess("公告已下线")
        this.getList()
      }).catch(error => { if (error && error.response) this.handleWorkflowError(error) })
    },
    handleNewVersion(row, event) {
      this.captureTrigger(event)
      this.$modal.confirm(`基于「${row.noticeTitle}」创建新草稿版本吗？原版本不会被修改。`).then(() => createNoticeVersion(row.noticeId, row.version)).then(response => {
        this.setEditorForm(response.data)
        this.open = true
        this.loadAudienceOptions("")
        this.getList()
      }).catch(error => { if (error && error.response) this.handleWorkflowError(error) })
    },
    handleDelete(row) {
      const noticeIds = row && row.noticeId ? row.noticeId : this.ids
      this.$modal.confirm("只允许删除草稿。确认删除所选公告草稿吗？").then(() => delNotice(noticeIds)).then(() => {
        this.$modal.msgSuccess("草稿删除成功")
        this.getList()
      }).catch(error => { if (error && error.response) this.handleWorkflowError(error) })
    },
    handleWorkflowError(error) {
      const data = error && error.response ? error.response.data : null
      const message = data && data.msg ? data.msg : "公告操作失败，请刷新后重试"
      if (data && data.businessCode === "NOTICE_VERSION_CONFLICT") this.getList()
      this.$modal.msgWarning(message)
    },
    handleViewData(row) {
      this.$refs.noticeViewRef.open(row)
    },
    handleReadUsers(row) {
      this.$refs.readUsersRef.open(row)
    },
    focusNoticeTitle() {
      this.$nextTick(() => this.$refs.noticeTitleInput && this.$refs.noticeTitleInput.focus())
    },
    focusNoticeType() {
      this.$nextTick(() => this.$refs.noticeTypeSelect && this.$refs.noticeTypeSelect.focus())
    },
    focusPublishMode() {
      this.$nextTick(() => {
        const radio = document.querySelector(".publish-form input[type='radio']")
        if (radio) radio.focus()
      })
    },
    focusTriggerNow() {
      const previous = this.lastTrigger
      const previousIsFocusable = previous && document.documentElement.contains(previous) &&
        previous.matches("button:not([disabled]), a[href], input:not([disabled]), select:not([disabled]), textarea:not([disabled]), [tabindex]:not([tabindex='-1'])")
      const fallback = this.$refs.addButton && this.$refs.addButton.$el
      const target = previousIsFocusable ? previous : fallback
      if (!target || typeof target.focus !== "function") return false
      target.focus()
      return document.activeElement === target
    },
    handleDialogClosed() {
      // Element UI emits `closed` only after the leave transition. Restore focus
      // synchronously here so keyboard users never remain on a hidden footer button.
      this.focusTriggerNow()
      this.$nextTick(() => this.focusTriggerNow())
    },
    restoreTriggerFocus() {
      // The request can finish before the dialog transition does. Focus once now
      // and again after common transition/list-render timings; `closed` remains
      // the authoritative synchronous restoration point.
      this.focusTriggerNow()
      const retryDelays = [0, 100, 350]
      retryDelays.forEach(delay => {
        setTimeout(() => this.$nextTick(() => this.focusTriggerNow()), delay)
      })
    },
    lifecycleLabel(value) {
      const item = this.lifecycleOptions.find(option => option.value === value)
      return item ? item.label : "未知"
    },
    lifecycleTagType(value) {
      return { DRAFT: "info", SCHEDULED: "warning", PUBLISHED: "success", OFFLINE: "danger" }[value] || "info"
    },
    audienceLabel(value) {
      return { ALL: "全员", DEPT: "组织/门店", ROLE: "角色", USER: "指定人员", MIXED: "混合受众" }[value] || "未配置受众"
    }
  }
}
</script>

<style lang="scss" scoped>
.notice-rollout-alert,
.notice-search-card,
.notice-toolbar-card { margin-bottom: 12px; }

.notice-toolbar-card { padding-bottom: 2px; }
.notice-table-card { padding-bottom: 8px; }

.notice-title-link {
  padding: 0;
  border: 0;
  color: #2563eb;
  background: transparent;
  font: inherit;
  font-weight: 600;
  text-align: left;
  cursor: pointer;
}
.notice-title-link:focus-visible { outline: 2px solid var(--erp-primary, #0b6b53); outline-offset: 3px; }
.notice-row-meta { margin-top: 4px; color: #94a3b8; font-size: 12px; }
.notice-field-help { margin-top: 6px; color: #64748b; font-size: 12px; line-height: 18px; }
.notice-include-children { display: block; margin-top: 8px; }
.notice-option-description { float: right; margin-left: 20px; color: #8492a6; }
.notice-preview-count { margin-left: 12px; color: #166534; font-weight: 600; }
.notice-org-breakdown { display: flex; flex-wrap: wrap; gap: 6px; margin-top: 10px; }

.notice-content-preview {
  margin-top: 12px;
  padding: 18px;
  border: 1px solid #dbe3ed;
  border-radius: 10px;
  background: #fff;
  color: #334155;
  transition: width .2s ease;
}
.notice-content-preview.is-desktop { width: 100%; }
.notice-content-preview.is-mobile { width: 360px; max-width: 100%; margin-right: auto; }
.notice-content-preview h3 { margin: 0 0 12px; color: #0f172a; }
.notice-preview-placeholder { color: #94a3b8; }

.notice-editor-footer { display: flex; align-items: center; justify-content: flex-end; gap: 10px; }
.notice-editor-footer > span { margin-right: auto; color: #b26a00; font-size: 12px; }
.publish-summary { margin-top: 16px; }
.publish-form { margin-top: 18px; }

.notice-empty-state { padding: 32px 0; color: #64748b; text-align: center; }
.notice-empty-state i { color: #94a3b8; font-size: 34px; }
.notice-empty-state p { margin: 10px 0 4px; color: #334155; font-weight: 600; }
.notice-empty-state span { display: block; margin-bottom: 14px; font-size: 12px; }

::v-deep .el-dialog__body { max-height: 76vh; overflow-y: auto; }
</style>
