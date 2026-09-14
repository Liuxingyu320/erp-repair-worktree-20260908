<template>
  <div class="app-container system-management-page">
    <system-page-header
      title="参数设置"
      description="维护系统运行参数与敏感配置，集中查看内置项和业务开关。"
      icon="el-icon-set-up"
      tip="修改参数后仅在确有需要时刷新缓存。"
    />
    <div v-show="showSearch" class="search-card config-search-card">
    <el-form :model="queryParams" ref="queryForm" size="small" :inline="true" label-width="88px">
      <el-form-item label="参数名称" prop="configName">
        <el-input
          v-model="queryParams.configName"
          placeholder="请输入参数名称"
          clearable
          style="width: 240px"
          @keyup.enter.native="handleQuery"
        />
      </el-form-item>
      <el-form-item label="参数键名" prop="configKey">
        <el-input
          v-model="queryParams.configKey"
          placeholder="请输入参数键名"
          clearable
          style="width: 240px"
          @keyup.enter.native="handleQuery"
        />
      </el-form-item>
      <el-form-item label="系统内置" prop="configType">
        <el-select v-model="queryParams.configType" placeholder="系统内置" clearable>
          <el-option
            v-for="dict in dict.type.sys_yes_no"
            :key="dict.value"
            :label="dict.label"
            :value="dict.value"
          />
        </el-select>
      </el-form-item>
      <el-form-item label="配置分组" prop="groupCode">
        <el-select v-model="queryParams.groupCode" placeholder="全部分组" clearable filterable>
          <el-option v-for="item in groupOptions" :key="item.value" :label="item.label" :value="item.value" />
        </el-select>
      </el-form-item>
      <el-form-item label="值类型" prop="valueType">
        <el-select v-model="queryParams.valueType" placeholder="全部类型" clearable>
          <el-option v-for="item in valueTypeOptions" :key="item.value" :label="item.label" :value="item.value" />
        </el-select>
      </el-form-item>
      <el-form-item label="创建时间">
        <el-date-picker
          v-model="dateRange"
          style="width: 240px"
          value-format="yyyy-MM-dd"
          type="daterange"
          range-separator="-"
          start-placeholder="开始日期"
          end-placeholder="结束日期"
        ></el-date-picker>
      </el-form-item>
      <el-form-item>
        <el-button type="primary" icon="el-icon-search" size="mini" @click="handleQuery">搜索</el-button>
        <el-button icon="el-icon-refresh" size="mini" @click="resetQuery">重置</el-button>
      </el-form-item>
    </el-form>
    </div>

    <div class="content-card config-toolbar-card">
    <el-row :gutter="10" class="mb8">
      <el-col :span="1.5">
        <el-button
          type="primary"
          plain
          icon="el-icon-plus"
          size="mini"
          @click="handleAdd"
          v-hasPermi="['system:config:add']"
        >新增</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button
          type="success"
          plain
          icon="el-icon-edit"
          size="mini"
          :disabled="single"
          @click="handleUpdate"
          v-if="$auth && $auth.hasPermiAnd(['system:config:edit', 'system:config:query'])"
          v-hasPermi="['system:config:edit']"
        >修改</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button
          type="danger"
          plain
          icon="el-icon-delete"
          size="mini"
          :disabled="multiple || selectedContainsBuiltIn || !listReady || loading"
          @click="handleDelete"
          v-hasPermi="['system:config:remove']"
        >删除</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button
          type="warning"
          plain
          icon="el-icon-download"
          size="mini"
          @click="handleExport"
          v-hasPermi="['system:config:export']"
        >导出</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button
          type="danger"
          plain
          icon="el-icon-refresh"
          size="mini"
          @click="handleRefreshCache"
          v-hasPermi="['system:config:refresh']"
        >刷新缓存</el-button>
      </el-col>
      <right-toolbar :showSearch.sync="showSearch" @queryTable="getList"></right-toolbar>
    </el-row>
    </div>

    <div class="table-card config-table-card">
    <el-alert v-if="listError" :title="listError" type="error" :closable="false" />
    <el-button v-if="listError" size="mini" @click="getList">重试当前查询</el-button>
    <el-table ref="configTable" v-loading="loading" :data="configList" @selection-change="handleSelectionChange">
      <el-table-column type="selection" width="55" align="center" />
      <el-table-column label="参数主键" align="center" prop="configId" />
      <el-table-column label="参数名称" align="center" prop="configName" :show-overflow-tooltip="true" />
      <el-table-column label="参数键名" align="center" prop="configKey" :show-overflow-tooltip="true" />
      <el-table-column label="参数键值" align="center" prop="configValue" :show-overflow-tooltip="true">
        <template slot-scope="scope">
          <span>{{ scope.row.configValue }}</span>
          <el-tag v-if="scope.row.sensitive" size="mini" type="warning">敏感</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="系统内置" align="center" prop="configType">
        <template slot-scope="scope">
          <dict-tag :options="dict.type.sys_yes_no" :value="scope.row.configType"/>
        </template>
      </el-table-column>
      <el-table-column label="备注" prop="remark" min-width="130" :show-overflow-tooltip="true" />
      <el-table-column label="更新时间" align="center" prop="updateTime" width="170">
        <template slot-scope="scope">
          <span>{{ parseTime(scope.row.updateTime || scope.row.createTime) }}</span>
        </template>
      </el-table-column>
      <el-table-column label="操作" align="center" width="130" fixed="right" class-name="small-padding fixed-width">
        <template slot-scope="scope">
          <el-button
            size="mini"
            type="text"
            icon="el-icon-edit"
            @click="handleUpdate(scope.row)"
            v-if="$auth && $auth.hasPermiAnd(['system:config:edit', 'system:config:query'])"
            v-hasPermi="['system:config:edit']"
          >修改</el-button>
          <el-button
            size="mini"
            type="text"
            icon="el-icon-delete"
            @click="handleDelete(scope.row)"
            :disabled="scope.row.configType === 'Y'"
            v-hasPermi="['system:config:remove']"
          >删除</el-button>
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

    <!-- 添加或修改参数配置对话框 -->
    <el-dialog :title="title" :visible.sync="open" width="640px" append-to-body @opened="focusConfigName">
      <el-form ref="form" :model="form" :rules="rules" label-width="100px">
        <el-alert
          v-if="form.descriptorDescription || unknownBuiltInWarning"
          :title="unknownBuiltInWarning || form.descriptorDescription"
          :type="unknownBuiltInWarning ? 'warning' : 'info'"
          :closable="false"
          show-icon
          class="config-description"
        />
        <el-form-item label="参数名称" prop="configName">
          <el-input ref="configNameInput" v-model="form.configName" placeholder="请输入参数名称" />
        </el-form-item>
        <el-form-item label="参数键名" prop="configKey">
          <el-input
            v-model="form.configKey"
            :disabled="Boolean(form.configId && form.configType === 'Y')"
            placeholder="例如：module.feature.enabled"
            @change="applyKnownDescriptor"
          />
        </el-form-item>
        <el-form-item v-if="form.sensitive" label="替换敏感值">
          <el-checkbox v-model="form.updateSensitiveValue">本次明确更新敏感值</el-checkbox>
          <div class="sensitive-value-tip">未勾选时保持原值；系统不会回显现有敏感值。</div>
        </el-form-item>
        <el-form-item label="参数键值" prop="configValue">
          <el-input
            v-if="form.sensitive"
            v-model="form.configValue"
            :type="form.sensitive ? 'password' : 'textarea'"
            :show-password="form.sensitive"
            :disabled="form.sensitive && !form.updateSensitiveValue"
            :placeholder="form.sensitive ? (form.updateSensitiveValue ? '请输入新的敏感值' : '留空表示保持原值') : '请输入参数键值'"
          />
          <template v-else-if="configValueType === 'boolean'">
            <el-switch v-if="hasBooleanValue" v-model="booleanConfigValue" active-text="开启" inactive-text="关闭" active-value="true" inactive-value="false" />
            <el-radio-group v-else v-model="form.configValue"><el-radio label="true">开启</el-radio><el-radio label="false">关闭</el-radio></el-radio-group>
          </template>
          <el-select v-else-if="configValueType === 'enum' && enumOptions.length" v-model="form.configValue" placeholder="请选择参数值" class="config-value-control">
            <el-option v-for="option in enumOptions" :key="option" :label="option" :value="option" />
          </el-select>
          <el-input v-else-if="configValueType === 'integer' || configValueType === 'decimal'" v-model="form.configValue" type="number" :step="configValueType === 'integer' ? '1' : 'any'" :min="numericRules.min" :max="numericRules.max" :placeholder="valuePlaceholder">
            <template v-if="valueUnit" slot="append">{{ valueUnit }}</template>
          </el-input>
          <el-input v-else v-model="form.configValue" type="textarea" :rows="configValueType === 'json' ? 5 : 2" :placeholder="valuePlaceholder" />
          <div v-if="!form.sensitive && numericRangeHint" class="sensitive-value-tip">{{ numericRangeHint }}</div>
        </el-form-item>
        <el-form-item label="系统内置" prop="configType">
          <el-radio-group v-model="form.configType" :disabled="form.configId != null && form.configType === 'Y'">
            <el-radio
              v-for="dict in dict.type.sys_yes_no"
              :key="dict.value"
              :label="dict.value"
            >{{dict.label}}</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="备注" prop="remark">
          <el-input v-model="form.remark" type="textarea" placeholder="请输入内容" />
        </el-form-item>
      </el-form>
      <div slot="footer" class="dialog-footer">
        <el-button type="primary" @click="submitForm">确 定</el-button>
        <el-button @click="cancel">取 消</el-button>
      </div>
    </el-dialog>
  </div>
</template>

<script>
import { listConfig, getConfig, delConfig, addConfig, updateConfig, refreshCache, listConfigDescriptors } from "@/api/system/config"
import { getSelectedDeptId } from "@/utils/shopContext"
const { createUiOperationScope } = require("@/utils/uiOperationScope")
import { confirmExportAction } from "@/utils/exportConfirm"

export default {
  name: "Config",
  dicts: ['sys_yes_no'],
  data() {
    return {
      // 遮罩层
      loading: true,
      listReady: false,
      listError: "",
      // 选中数组
      ids: [],
      // 非单个禁用
      single: true,
      // 非多个禁用
      multiple: true,
      selectedContainsBuiltIn: false,
      // 显示搜索条件
      showSearch: true,
      // 总条数
      total: 0,
      // 参数表格数据
      configList: [],
      // 代码注册的可信配置描述符
      configDescriptors: [],
      originalSensitive: false,
      valueTypeOptions: [
        { label: "文本", value: "string" },
        { label: "整数", value: "integer" },
        { label: "小数", value: "decimal" },
        { label: "布尔", value: "boolean" },
        { label: "JSON", value: "json" },
        { label: "枚举", value: "enum" },
        { label: "密码/密钥", value: "password" }
      ],
      groupLabels: {
        account: "账号安全",
        appearance: "界面外观",
        audit: "审计治理",
        custom: "自定义",
        file: "文件服务",
        hr: "人事管理",
        inventory: "库存业务",
        security: "系统安全",
        todo: "待办中心"
      },
      // 弹出层标题
      title: "",
      // 是否显示弹出层
      open: false,
      // 日期范围
      dateRange: [],
      // 查询参数
      queryParams: {
        pageNum: 1,
        pageSize: 10,
        configName: undefined,
        configKey: undefined,
        configType: undefined,
        groupCode: undefined,
        valueType: undefined
      },
      // 表单参数
      form: {},
      // 表单校验
      rules: {
        configName: [
          { required: true, message: "参数名称不能为空", trigger: "blur" }
        ],
        configKey: [
          { required: true, message: "参数键名不能为空", trigger: "blur" }
        ],
        configValue: [
          { validator: (rule, value, callback) => {
            this.validateConfigValue(rule, value, callback)
          }, trigger: ["blur", "change"] }
        ]
      }
    }
  },
  computed: {
    actorContextKey() {
      const store = this.$store || {}
      return String((store.getters || {}).id || "") + ":" + String(((store.state || {}).user || {}).sessionRevision || 0)
    },
    descriptorByKey() {
      return (this.configDescriptors || []).find(item => item.configKey === this.form.configKey)
    },
    groupOptions() {
      const values = new Set(Object.keys(this.groupLabels))
      ;(this.configDescriptors || []).forEach(item => values.add(item.groupCode))
      ;(this.configList || []).forEach(item => item.groupCode && values.add(item.groupCode))
      return Array.from(values).sort().map(value => ({ value, label: this.groupLabel(value) }))
    },
    isMetadataLocked() {
      return Boolean(this.descriptorByKey || this.form.configType === "Y")
    },
    existingSensitive() {
      return this.originalSensitive
    },
    configValueType() {
      return this.form.valueType || "string"
    },
    hasBooleanValue() {
      return /^(true|false)$/i.test(String(this.form.configValue == null ? "" : this.form.configValue).trim())
    },
    booleanConfigValue: {
      get() { return String(this.form.configValue).trim().toLowerCase() },
      set(value) { this.$set(this.form, "configValue", value) }
    },
    numericRules() {
      const result = {}
      String(this.form.validationRule || "").split(";").forEach(part => {
        const match = /^\s*(min|max)\s*=\s*([+-]?(?:\d+\.?\d*|\.\d+)(?:[eE][+-]?\d+)?)\s*$/.exec(part)
        if (match) result[match[1]] = match[2]
      })
      return result
    },
    numericRangeHint() {
      if (!["integer", "decimal"].includes(this.configValueType)) return ""
      const { min, max } = this.numericRules
      return min != null && max != null ? "范围：" + min + " 至 " + max : min != null ? "不得小于 " + min : max != null ? "不得大于 " + max : ""
    },
    valueUnit() {
      const match = /单位(?:为|是|：|:)?\s*([^，。；;]+)/.exec(this.form.descriptorDescription || "")
      return match ? match[1].trim() : ""
    },
    enumOptions() {
      const match = String(this.form.validationRule || "").split(";").find(part => part.trim().startsWith("enum="))
      return match ? match.slice(match.indexOf("=") + 1).split(",").map(item => item.trim()).filter(Boolean) : []
    },
    valuePlaceholder() {
      if (this.configValueType === "integer") return "请输入整数"
      if (this.configValueType === "decimal") return "请输入数值"
      return "请输入参数值"
    },
    unknownBuiltInWarning() {
      if (this.form.configType === "Y" && this.form.configKey && !this.descriptorByKey) {
        return "该内置键尚未注册描述符，后端会强制按敏感、不可公开配置处理。"
      }
      return ""
    }
  },
  watch: { actorContextKey() { this.handleConfigContextChanged() } },
  created() {
    window.addEventListener("erp:dept-changed", this.handleConfigContextChanged)
    this.loadDescriptors()
    this.getList()
  },
  activated() {
    this.configScope().activate()
    if (this._refreshConfigOnActivate) { this._refreshConfigOnActivate = false; this.loadDescriptors(); this.getList() }
  },
  deactivated() {
    this.configScope().deactivate()
    this.loading = false
    this.listReady = false
    this.open = false
    this._refreshConfigOnActivate = true
  },
  beforeDestroy() {
    window.removeEventListener("erp:dept-changed", this.handleConfigContextChanged)
    this.configScope().deactivate()
  },
  methods: {
    configScope() {
      if (!this._configScope) this._configScope = createUiOperationScope(() => ({ actor: this.actorContextKey, dept: getSelectedDeptId(), route: this.$route && this.$route.path }))
      return this._configScope
    },
    handleConfigContextChanged() {
      this.configScope().invalidate()
      this.configList = []
      this.configDescriptors = []
      this.open = false
      this.listReady = false
      this.loading = false
      this.handleSelectionChange([])
      if ((this.$store.getters || {}).id) { this.loadDescriptors(); this.getList() }
    },
    loadDescriptors() {
      const scope = this.configScope(), token = scope.begin("descriptors")
      return listConfigDescriptors().then(response => {
        if (scope.isCurrent(token)) this.configDescriptors = response.data || []
      }).catch(() => { if (scope.isCurrent(token)) this.configDescriptors = [] })
    },
    /** 查询参数列表 */
    getList() {
      const query = this.addDateRange(JSON.parse(JSON.stringify(this.queryParams)), [...this.dateRange])
      const scope = this.configScope(), token = scope.begin("list", query)
      this._configListToken = token
      this.loading = true
      this.listReady = false
      this.listError = ""
      this.configList = []
      this.total = 0
      this.handleSelectionChange([])
      if (this.$refs.configTable) this.$refs.configTable.clearSelection()
      return listConfig(query, { silentError: true }).then(response => {
        if (!scope.isCurrent(token)) return
        this.configList = Array.isArray(response.rows) ? response.rows : []
        this.total = Number(response.total) || 0
        this.listReady = true
      }).catch(error => {
        if (scope.isCurrent(token)) this.listError = error && error.message || "查询失败，请重试"
      }).finally(() => { if (scope.isCurrent(token)) this.loading = false })
    },
    // 取消按钮
    cancel() {
      this.open = false
      this.reset()
    },
    // 表单重置
    reset() {
      this.form = {
        configId: undefined,
        configName: undefined,
        configKey: undefined,
        configValue: undefined,
        configType: "Y",
        sensitive: false,
        updateSensitiveValue: false,
        remark: undefined
      }
      this.originalSensitive = false
      this.resetForm("form")
    },
    /** 搜索按钮操作 */
    handleQuery() {
      this.queryParams.pageNum = 1
      this.getList()
    },
    /** 重置按钮操作 */
    resetQuery() {
      this.dateRange = []
      this.resetForm("queryForm")
      this.handleQuery()
    },
    /** 新增按钮操作 */
    handleAdd() {
      this.reset()
      this.open = true
      this.title = "添加参数"
    },
    // 多选框选中数据
    handleSelectionChange(selection) {
      this.ids = selection.map(item => item.configId)
      this.single = selection.length != 1
      this.multiple = !selection.length
      this.selectedContainsBuiltIn = selection.some(item => item.configType === "Y")
    },
    /** 修改按钮操作 */
    handleUpdate(row) {
      this.reset()
      const configId = row.configId || this.ids
      getConfig(configId).then(response => {
        this.form = response.data
        this.$set(this.form, "updateSensitiveValue", false)
        if (this.form.sensitive) this.form.configValue = ""
        this.open = true
        this.title = "修改参数"
      })
    },
    /** 提交按钮 */
    submitForm() {
      this.$refs["form"].validate(valid => {
        if (valid) {
          if (this.form.configId != undefined) {
            updateConfig({ ...this.form, configValue: this.normalizedConfigValue() }).then(() => {
              this.$modal.msgSuccess("修改成功")
              this.open = false
              this.getList()
            })
          } else {
            addConfig({ ...this.form, configValue: this.normalizedConfigValue() }).then(() => {
              this.$modal.msgSuccess("新增成功")
              this.open = false
              this.getList()
            })
          }
        }
      })
    },
    /** 删除按钮操作 */
    handleDelete(row = {}) {
      if (!this.listReady || this.loading || !this.configScope().isCurrent(this._configListToken)) return
      if (row && row.configType === "Y") {
        this.$modal.msgWarning("系统内置参数不能删除")
        return
      }
      if (!row.configId && this.selectedContainsBuiltIn) {
        this.$modal.msgWarning("所选项目包含系统内置参数，不能删除")
        return
      }
      const configIds = row.configId ? [row.configId] : [...this.ids]
      const scope = this.configScope(), token = scope.begin("delete", configIds)
      return this.$modal.confirm('是否确认删除参数编号为"' + configIds + '"的数据项？').then(() => {
        if (!scope.isCurrent(token)) return false
        return delConfig(configIds)
      }).then(result => {
        if (result === false || !scope.isCurrent(token)) return
        this.getList()
        this.$modal.msgSuccess("删除成功")
      }).catch(() => {})
    },
    /** 导出按钮操作 */
    handleExport() {
      confirmExportAction(this, {
        moduleName: "参数数据",
        rangeLabel: "当前查询条件下的参数数据",
        filterLabel: this.buildManagementExportFilterLabel("参数名称", this.queryParams.configName)
      }).then(() => {
        this.download(
          'system/config/export',
          this.addDateRange({ ...this.queryParams }, this.dateRange),
          this.exportFileName('参数数据', this.dateRange)
        )
      })
    },
    buildManagementExportFilterLabel(primaryLabel, primaryValue) {
      const range = this.dateRange && this.dateRange.length === 2 ? this.dateRange.join(" 至 ") : "全部"
      return `${primaryLabel}：${primaryValue || "全部"}；日期：${range}`
    },
    /** 刷新缓存按钮操作 */
    handleRefreshCache() {
      refreshCache().then(() => {
        this.$modal.msgSuccess("刷新成功")
      })
    },
    applyKnownDescriptor() {
      const descriptor = this.configDescriptors.find(item => item.configKey === this.form.configKey)
      if (!descriptor) return
      this.form.groupCode = descriptor.groupCode
      this.form.valueType = descriptor.valueType
      this.form.sensitiveFlag = descriptor.sensitive ? "Y" : "N"
      this.$set(this.form, "sensitive", Boolean(descriptor.sensitive))
      this.$set(this.form, "updateSensitiveValue", false)
      this.form.validationRule = descriptor.validationRule || ""
      this.form.displayOrder = descriptor.displayOrder
      this.form.descriptorDescription = descriptor.description
      if (this.form.sensitiveFlag === "Y") this.form.configValue = ""
    },
    validateConfigValue(rule, value, callback) {
      if (this.form.sensitive && !this.form.updateSensitiveValue) {
        callback()
        return
      }
      if (value === undefined || value === null || String(value).trim() === "") {
        callback(new Error("参数键值不能为空"))
        return
      }
      if (this.form.sensitive) { callback(); return }
      if (this.configValueType === "boolean" && !/^(true|false)$/i.test(String(value).trim())) {
        callback(new Error("请选择开启或关闭"))
        return
      }
      if (this.configValueType === "enum" && this.enumOptions.length && !this.enumOptions.includes(String(value))) {
        callback(new Error("请选择列表中的参数值"))
        return
      }
      if (this.configValueType === "integer" && !/^[+-]?\d+$/.test(String(value).trim())) {
        callback(new Error("请输入合法整数"))
        return
      }
      if (this.configValueType === "decimal" && !/^[+-]?(?:\d+\.?\d*|\.\d+)(?:[eE][+-]?\d+)?$/.test(String(value).trim())) {
        callback(new Error("请输入合法数值"))
        return
      }
      if (["integer", "decimal"].includes(this.configValueType)) {
        const numeric = Number(value), { min, max } = this.numericRules
        if (min != null && numeric < Number(min)) { callback(new Error("参数值不能小于 " + min)); return }
        if (max != null && numeric > Number(max)) { callback(new Error("参数值不能大于 " + max)); return }
      }
      if (this.configValueType === "json") {
        try {
          JSON.parse(String(value))
        } catch (error) {
          callback(new Error("请输入合法 JSON"))
          return
        }
      }
      callback()
    },
    normalizedConfigValue() {
      if (this.form.configValue === undefined || this.form.configValue === null) return ""
      return typeof this.form.configValue === "string" ? this.form.configValue : String(this.form.configValue)
    },
    groupLabel(value) {
      return this.groupLabels[value] || value || "未分组"
    },
    valueTypeLabel(value) {
      const option = this.valueTypeOptions.find(item => item.value === value)
      return option ? option.label : value || "文本"
    },
    focusConfigName() {
      this.$nextTick(() => {
        const input = this.$refs.configNameInput
        if (input && input.focus) input.focus()
      })
    }
  }
}
</script>

<style lang="scss" scoped>
.config-search-card {
  margin-bottom: 12px;
  padding-bottom: 8px;
  box-shadow: 0 8px 20px rgba(15, 23, 42, 0.05);
}

.config-search-card ::v-deep .el-form-item__label {
  white-space: nowrap;
}

.config-toolbar-card {
  margin-bottom: 12px;
  padding-bottom: 2px;
  box-shadow: 0 8px 20px rgba(15, 23, 42, 0.05);
}

.config-table-card {
  padding-bottom: 8px;
  box-shadow: 0 8px 20px rgba(15, 23, 42, 0.06);
}

.sensitive-value-tip {
  color: #909399;
  font-size: 12px;
  line-height: 1.5;
}

.config-value-control {
  width: 100%;
}
</style>
