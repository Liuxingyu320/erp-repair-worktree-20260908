<template>
  <div class="app-container">
    <el-card shadow="never" class="mb12">
      <el-form :model="queryParams" inline size="small">
        <el-form-item label="规则名称">
          <el-input v-model="queryParams.ruleName" placeholder="规则名称" clearable @keyup.enter.native="getList"/>
        </el-form-item>
        <el-form-item label="调拨类型">
          <el-select v-model="queryParams.transferType" clearable placeholder="全部">
            <el-option v-for="item in transferTypeOptions" :key="item.value" :label="item.label" :value="item.value"/>
          </el-select>
        </el-form-item>
        <el-form-item label="适用范围">
          <el-select v-model="queryParams.scopeType" clearable placeholder="全部">
            <el-option v-for="item in scopeTypeOptions" :key="item.value" :label="item.label" :value="item.value"/>
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="queryParams.status" clearable placeholder="全部">
            <el-option label="启用" value="0"/>
            <el-option label="停用" value="1"/>
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button v-hasPermi="['inv:transfer:rule:list']" type="primary" size="mini" icon="el-icon-search" @click="getList">搜索</el-button>
          <el-button v-hasPermi="['inv:transfer:rule:add']" size="mini" icon="el-icon-plus" @click="openForm(null)">新增规则</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card shadow="never">
      <el-table v-loading="loading" :data="list" size="small">
        <el-table-column label="规则名称" prop="ruleName" min-width="180"/>
        <el-table-column label="调拨类型" prop="transferType" width="110">
          <template slot-scope="scope">{{ optionLabel(transferTypeOptions, scope.row.transferType) }}</template>
        </el-table-column>
        <el-table-column label="适用范围" prop="scopeType" width="110">
          <template slot-scope="scope">{{ optionLabel(scopeTypeOptions, scope.row.scopeType) }}</template>
        </el-table-column>
        <el-table-column label="条件" min-width="160">
          <template slot-scope="scope">{{ conditionText(scope.row) }}</template>
        </el-table-column>
        <el-table-column label="优先级" prop="priority" width="90" align="center">
          <template slot-scope="scope">
            <el-tooltip content="数字越小越优先" placement="top">
              <span>{{ scope.row.priority }}</span>
            </el-tooltip>
          </template>
        </el-table-column>
        <el-table-column label="状态" prop="status" width="90">
          <template slot-scope="scope">
            <el-tag :type="scope.row.status === '0' ? 'success' : 'info'" size="mini">
              {{ scope.row.status === "0" ? "启用" : "停用" }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="更新时间" prop="updateTime" width="160"/>
        <el-table-column label="操作" width="245" fixed="right">
          <template slot-scope="scope">
            <el-button v-hasPermi="['inv:transfer:rule:edit']" type="text" size="mini" icon="el-icon-edit" @click="openForm(scope.row)">编辑</el-button>
            <el-button v-hasPermi="['inv:transfer:rule:query']" type="text" size="mini" icon="el-icon-video-play" @click="openSimulation(scope.row)">模拟</el-button>
            <el-button v-hasPermi="['inv:transfer:rule:query']" type="text" size="mini" icon="el-icon-user" @click="openCandidatePreview(scope.row)">候选人</el-button>
            <el-button v-hasPermi="['inv:transfer:rule:remove']" type="text" size="mini" class="text-danger" icon="el-icon-delete" @click="handleDelete(scope.row)">删除</el-button>
          </template>
        </el-table-column>
        <template slot="empty">
          <div class="empty-state">
            <i class="el-icon-document-add empty-icon" aria-hidden="true"/>
            <div>暂无调拨审批规则</div>
            <el-button v-hasPermi="['inv:transfer:rule:add']" type="primary" size="mini" @click="openForm(null)">创建第一条规则</el-button>
          </div>
        </template>
      </el-table>
      <pagination v-show="total>0" :total="total" :page.sync="queryParams.pageNum" :limit.sync="queryParams.pageSize" @pagination="getList"/>
    </el-card>

    <el-drawer
      ref="ruleDrawer"
      :visible.sync="drawerOpen"
      :title="drawerTitle"
      :with-header="true"
      :wrapper-closable="false"
      :close-on-press-escape="false"
      size="880px"
      append-to-body
      custom-class="rule-wizard-drawer"
      @open="syncDrawerAccessibility"
      @closed="handleDrawerClosed"
    >
      <div slot="title" class="drawer-title">
        <span id="transfer-approval-rule-drawer-title" role="heading" aria-level="2">{{ drawerTitle }}</span>
        <el-tag :type="form.status === '0' ? 'success' : 'info'" size="mini">{{ form.status === "0" ? "启用" : "停用" }}</el-tag>
      </div>

      <div class="drawer-content">
        <el-steps :active="wizardStep" finish-status="success" simple class="wizard-steps">
          <el-step title="基本信息"/>
          <el-step title="匹配条件"/>
          <el-step title="审批链"/>
          <el-step title="校验与模拟"/>
        </el-steps>

        <el-form ref="formRef" :model="form" :rules="rules" label-width="112px" class="wizard-form">
          <section v-show="wizardStep === 0" aria-label="基本信息">
            <el-alert
              title="优先级数字越小越先匹配；启用规则保存前必须通过冲突校验"
              type="info"
              :closable="false"
              show-icon
              class="approval-preview"
            />
            <el-row :gutter="16">
              <el-col :span="12">
                <el-form-item label="规则名称" prop="ruleName">
                  <el-input ref="ruleNameInput" v-model="form.ruleName" maxlength="128" show-word-limit placeholder="请输入规则名称"/>
                </el-form-item>
              </el-col>
              <el-col :span="12">
                <el-form-item label="调拨类型" prop="transferType">
                  <el-select v-model="form.transferType" style="width:100%">
                    <el-option v-for="item in transferTypeOptions" :key="item.value" :label="item.label" :value="item.value"/>
                  </el-select>
                </el-form-item>
              </el-col>
            </el-row>
            <el-row :gutter="16">
              <el-col :span="12">
                <el-form-item label="适用范围" prop="scopeType">
                  <el-select v-model="form.scopeType" style="width:100%" @change="handleScopeTypeChange">
                    <el-option v-for="item in scopeTypeOptions" :key="item.value" :label="item.label" :value="item.value"/>
                  </el-select>
                </el-form-item>
              </el-col>
              <el-col :span="12">
                <el-form-item label="范围组织" prop="scopeId">
                  <treeselect
                    v-model="form.scopeId"
                    :options="deptOptions"
                    :normalizer="normalizer"
                    :disabled="form.scopeType === 'all'"
                    placeholder="请选择部门或区域"
                  />
                </el-form-item>
              </el-col>
            </el-row>
            <el-row :gutter="16">
              <el-col :span="12">
                <el-form-item label="优先级" prop="priority">
                  <el-input-number v-model="form.priority" :min="1" :max="9999" :precision="0" style="width:100%"/>
                </el-form-item>
              </el-col>
              <el-col :span="12">
                <el-form-item label="状态" prop="status">
                  <el-radio-group v-model="form.status">
                    <el-radio label="0">启用</el-radio>
                    <el-radio label="1">停用</el-radio>
                  </el-radio-group>
                </el-form-item>
              </el-col>
            </el-row>
          </section>

          <section v-show="wizardStep === 1" aria-label="匹配条件">
            <el-row :gutter="16">
              <el-col :span="12">
                <el-form-item label="条件类型" prop="conditionType">
                  <el-select v-model="form.conditionType" style="width:100%">
                    <el-option label="不限制" value="none"/>
                    <el-option label="按数量" value="quantity"/>
                    <el-option label="按金额（暂不可选）" value="amount" disabled/>
                  </el-select>
                </el-form-item>
              </el-col>
              <el-col :span="6">
                <el-form-item label="操作符" prop="conditionOperator">
                  <el-select v-model="form.conditionOperator" :disabled="form.conditionType === 'none'" style="width:100%">
                    <el-option label=">=" value=">="/>
                    <el-option label=">" value=">"/>
                    <el-option label="=" value="="/>
                    <el-option label="<=" value="<="/>
                    <el-option label="<" value="<"/>
                  </el-select>
                </el-form-item>
              </el-col>
              <el-col :span="6">
                <el-form-item label="条件值" prop="conditionValue">
                  <el-input-number v-model="form.conditionValue" :disabled="form.conditionType === 'none'" :min="0" :precision="2" style="width:100%"/>
                </el-form-item>
              </el-col>
            </el-row>
            <el-row :gutter="16">
              <el-col :span="12">
                <el-form-item label="驳回动作" prop="rejectAction">
                  <el-select v-model="form.rejectAction" style="width:100%">
                    <el-option v-for="item in rejectActionOptions" :key="item.value" :label="item.label" :value="item.value"/>
                  </el-select>
                </el-form-item>
              </el-col>
              <el-col :span="12">
                <el-form-item label="整体策略">
                  <el-input value="按顺序全部审批（禁止自审）" disabled/>
                </el-form-item>
              </el-col>
            </el-row>
            <el-form-item label="备注" prop="remark">
              <el-input v-model="form.remark" maxlength="500" show-word-limit type="textarea" :rows="4"/>
            </el-form-item>
          </section>

          <section v-show="wizardStep === 2" aria-label="审批链">
            <el-alert
              title="系统固定顺序审批并禁止自审；四级、三级可因缺人向上收敛，运营总监和总经理固定必审"
              type="success"
              :closable="false"
              show-icon
              class="approval-preview"
            />
            <el-alert v-if="approvalPreviewText" :title="approvalPreviewText" type="info" :closable="false" show-icon class="approval-preview"/>
            <el-table :data="form.nodes" size="small" border class="node-table">
              <el-table-column label="顺序" prop="nodeOrder" width="70" align="center"/>
              <el-table-column label="业务节点" prop="nodeName" width="220"/>
              <el-table-column label="候选人来源">
                <template slot-scope="scope">{{ nodeDescription(scope.row) }}</template>
              </el-table-column>
              <el-table-column label="通过方式" width="130">
                <template>任意一人通过</template>
              </el-table-column>
            </el-table>
          </section>

          <section v-show="wizardStep === 3" aria-label="校验与模拟">
            <el-alert
              title="输入一张样例调拨单，系统会同时校验规则冲突、实际命中规则、审批链及候选人来源"
              type="info"
              :closable="false"
              show-icon
              class="approval-preview"
            />
            <el-row :gutter="12">
              <el-col :span="8">
                <el-form-item label="样例来源">
                  <treeselect v-model="simulation.fromDeptId" :options="deptOptions" :normalizer="normalizer" placeholder="可选来源组织"/>
                </el-form-item>
              </el-col>
              <el-col :span="8">
                <el-form-item label="目标门店" required>
                  <treeselect v-model="simulation.toDeptId" :options="deptOptions" :normalizer="normalizer" placeholder="请选择目标门店"/>
                </el-form-item>
              </el-col>
              <el-col :span="8">
                <el-form-item label="样例数量">
                  <el-input-number v-model="simulation.totalQuantity" :min="0" :precision="2" style="width:100%"/>
                </el-form-item>
              </el-col>
            </el-row>
            <div class="validate-actions">
              <el-button type="primary" icon="el-icon-circle-check" :loading="validateLoading" @click="validateConfiguration(false)">运行校验与模拟</el-button>
              <span class="validate-hint">保存时会再次校验，页面结果不能绕过后端约束。</span>
            </div>

            <template v-if="validationResult">
              <el-alert
                v-for="issue in validationResult.blockingIssues || []"
                :key="'block-' + issue"
                :title="issue"
                type="error"
                :closable="false"
                show-icon
                class="approval-preview"
              />
              <el-alert
                v-for="warning in validationResult.warnings || []"
                :key="'warning-' + warning"
                :title="warning"
                type="warning"
                :closable="false"
                show-icon
                class="approval-preview"
              />
              <el-alert
                v-if="validationResult.valid && !(validationResult.warnings || []).length"
                title="校验通过，未发现阻断或警告"
                type="success"
                :closable="false"
                show-icon
                class="approval-preview"
              />

              <el-card shadow="never" class="simulation-result">
                <div slot="header">
                  <span>模拟结果</span>
                  <el-tag :type="validationResult.matched ? 'success' : 'danger'" size="mini">
                    {{ validationResult.matched ? "已命中" : "未命中" }}
                  </el-tag>
                </div>
                <div v-if="validationResult.matched" class="matched-rule">
                  最终规则：{{ validationResult.ruleName || "未命名规则" }}
                  <span v-if="validationResult.ruleId">（ID {{ validationResult.ruleId }}）</span>
                </div>
                <ul v-if="(validationResult.matchReasons || []).length" class="reason-list">
                  <li v-for="reason in validationResult.matchReasons" :key="'match-' + reason">{{ reason }}</li>
                </ul>
                <div v-if="(validationResult.nonMatchReasons || []).length" class="reason-title">本规则未成为最终规则的原因：</div>
                <ul v-if="(validationResult.nonMatchReasons || []).length" class="reason-list warning-text">
                  <li v-for="reason in validationResult.nonMatchReasons" :key="'miss-' + reason">{{ reason }}</li>
                </ul>
              </el-card>

              <el-table v-if="(validationResult.nodes || []).length" :data="validationResult.nodes" size="small" border class="node-table">
                <el-table-column label="顺序" prop="nodeOrder" width="70" align="center"/>
                <el-table-column label="审批节点" prop="nodeName" width="190"/>
                <el-table-column label="候选人来源" prop="candidateSource" min-width="250"/>
                <el-table-column label="状态" width="110">
                  <template slot-scope="scope">
                    <el-tag :type="scope.row.status === 'ready' ? 'success' : scope.row.status === 'blocked' ? 'danger' : 'warning'" size="mini">
                      {{ previewStateLabel(scope.row.status) }}
                    </el-tag>
                  </template>
                </el-table-column>
                <el-table-column label="候选人" min-width="180">
                  <template slot-scope="scope">{{ candidateNames(scope.row) }}</template>
                </el-table-column>
              </el-table>
            </template>
          </section>
        </el-form>
      </div>

      <div class="drawer-footer">
        <el-button @click="drawerOpen = false">取消</el-button>
        <el-button v-if="wizardStep > 0" icon="el-icon-arrow-left" @click="wizardStep -= 1">上一步</el-button>
        <el-button v-if="wizardStep < 3" type="primary" @click="nextStep">下一步<i class="el-icon-arrow-right el-icon--right"/></el-button>
        <el-button
          v-else
          v-hasPermi="['inv:transfer:rule:add','inv:transfer:rule:edit']"
          type="primary"
          icon="el-icon-check"
          :loading="submitLoading"
          @click="doSubmit"
        >校验并保存</el-button>
      </div>
    </el-drawer>

    <el-dialog title="候选人预览" :visible.sync="candidatePreviewOpen" width="900px" append-to-body>
      <el-form inline size="small">
        <el-form-item label="目标门店" required>
          <treeselect
            v-model="candidateTargetDeptId"
            :options="deptOptions"
            :normalizer="normalizer"
            placeholder="请选择要调入的目标门店"
            style="width:320px"
          />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :loading="candidatePreviewLoading" @click="loadCandidatePreview">查询候选人</el-button>
        </el-form-item>
      </el-form>
      <template v-if="candidatePreview">
        <el-alert v-if="candidatePreview.blocked" :title="candidatePreview.blockedReason" type="error" :closable="false" show-icon class="approval-preview"/>
        <el-alert
          v-for="warning in candidatePreview.warnings || []"
          :key="warning"
          :title="warning"
          type="warning"
          :closable="false"
          show-icon
          class="approval-preview"
        />
        <div class="boundary-text">
          三级岗位边界：高层排序 {{ candidatePreview.executiveBoundarySort == null ? "-" : candidatePreview.executiveBoundarySort }}
          &lt; 候选岗位排序 &lt; 店长排序 {{ candidatePreview.managerPostSort == null ? "-" : candidatePreview.managerPostSort }}
        </div>
        <el-table :data="candidatePreview.nodes || []" size="small" border>
          <el-table-column label="顺序" prop="nodeOrder" width="70" align="center"/>
          <el-table-column label="业务节点" prop="nodeName" width="210"/>
          <el-table-column label="状态" width="130">
            <template slot-scope="scope">{{ previewStateLabel(scope.row.state) }}</template>
          </el-table-column>
          <el-table-column label="岗位排序" prop="resolvedPostSort" width="100" align="center"/>
          <el-table-column label="候选人">
            <template slot-scope="scope">
              {{ (scope.row.candidateDisplayNames || []).join("、") || "未找到" }}
              <span v-if="scope.row.candidateCount">（{{ scope.row.candidateCount }}人）</span>
            </template>
          </el-table-column>
        </el-table>
      </template>
      <div slot="footer"><el-button @click="candidatePreviewOpen = false">关闭</el-button></div>
    </el-dialog>
  </div>
</template>

<script>
import Treeselect from "@riophae/vue-treeselect"
import "@riophae/vue-treeselect/dist/vue-treeselect.css"
import {
  listTransferApprovalRule,
  getTransferApprovalRule,
  addTransferApprovalRule,
  updateTransferApprovalRule,
  delTransferApprovalRule,
  validateTransferApprovalRule,
  previewTransferApprovalCandidates
} from "@/api/inventory/transferApprovalRule"
import { listShopTree } from "@/api/system/dept"

export default {
  name: "InvTransferApprovalRule",
  components: { Treeselect },
  data() {
    return {
      loading: false,
      submitLoading: false,
      validateLoading: false,
      list: [],
      total: 0,
      drawerOpen: false,
      wizardStep: 0,
      triggerElement: null,
      validationResult: null,
      deptOptions: [],
      candidatePreviewOpen: false,
      candidatePreviewLoading: false,
      candidatePreviewRuleId: undefined,
      candidateTargetDeptId: undefined,
      candidatePreview: null,
      simulation: this.emptySimulation(),
      queryParams: {
        pageNum: 1,
        pageSize: 10,
        documentType: "transfer",
        ruleName: undefined,
        transferType: undefined,
        scopeType: undefined,
        status: undefined
      },
      form: this.emptyForm(),
      transferTypeOptions: [
        { label: "全部", value: "all" },
        { label: "门店要货", value: "warehouse" },
        { label: "门店返仓", value: "store_return" },
        { label: "异店调货", value: "cross_store" }
      ],
      scopeTypeOptions: [
        { label: "全部", value: "all" },
        { label: "部门", value: "dept" },
        { label: "区域", value: "area" }
      ],
      conditionTypeOptions: [
        { label: "不限制", value: "none" },
        { label: "按数量", value: "quantity" },
        { label: "按金额", value: "amount" }
      ],
      rejectActionOptions: [
        { label: "退回草稿", value: "back_to_draft" },
        { label: "关闭", value: "close" },
        { label: "驳回", value: "rejected" }
      ],
      rules: {
        ruleName: [{ required: true, message: "请输入规则名称", trigger: "blur" }],
        transferType: [{ required: true, message: "请选择调拨类型", trigger: "change" }],
        scopeType: [{ required: true, message: "请选择适用范围", trigger: "change" }],
        scopeId: [{ validator: this.validateScope, trigger: "change" }],
        rejectAction: [{ required: true, message: "请选择驳回动作", trigger: "change" }],
        priority: [{ required: true, message: "请输入优先级", trigger: "blur" }]
      }
    }
  },
  created() {
    this.getList()
    this.loadDeptOptions()
  },
  computed: {
    drawerTitle() {
      return this.form.ruleId ? "编辑审批规则" : "新增审批规则"
    },
    approvalPreviewText() {
      const nodes = this.form && this.form.nodes ? this.form.nodes : []
      const names = nodes
        .slice()
        .sort((a, b) => Number(a.nodeOrder || 0) - Number(b.nodeOrder || 0))
        .map(node => node.postName || node.postCode || node.nodeName)
        .filter(Boolean)
      return names.length ? "审批顺序：" + names.join(" -> ") : ""
    }
  },
  methods: {
    emptySimulation() {
      return {
        fromDeptId: undefined,
        toDeptId: undefined,
        transferType: "cross_store",
        totalQuantity: 0
      }
    },
    emptyForm() {
      return {
        ruleId: undefined,
        ruleName: "",
        documentType: "transfer",
        transferType: "all",
        scopeType: "all",
        scopeId: undefined,
        conditionType: "none",
        conditionOperator: ">=",
        conditionValue: 0,
        approvalMode: "all_nodes",
        requiredCount: 0,
        rejectAction: "back_to_draft",
        allowSelfApprove: "0",
        priority: 10,
        status: "0",
        remark: "",
        warningAcknowledged: false,
        validationTargetDeptId: undefined,
        nodes: this.ensureSystemNodes()
      }
    },
    systemNode(order, role, name, postCode, postName) {
      return {
        nodeOrder: order,
        nodeName: name,
        nodeRole: role,
        postId: undefined,
        postCode: postCode || "",
        postName: postName || "",
        approvalMode: "any_one",
        requiredCount: 1
      }
    },
    ensureSystemNodes() {
      return [
        this.systemNode(1, "level4_highest", "四级负责人（店长/店助）", "", ""),
        this.systemNode(2, "level3_highest", "三级负责人（店长与高层之间）", "", ""),
        this.systemNode(3, "operations_director", "运营总监", "yyzj", "运营总监"),
        this.systemNode(4, "general_manager", "总经理", "zjl", "总经理")
      ]
    },
    nodeDescription(node) {
      return {
        level4_highest: "目标门店店长优先，无店长时由店长助理兜底",
        level3_highest: "高于店长、低于运营总监和总经理，取最接近店长的一档",
        operations_director: "按目标门店负责范围匹配运营总监，固定必审",
        general_manager: "按目标门店负责范围匹配总经理，最终必审"
      }[node.nodeRole] || "-"
    },
    validateScope(rule, value, callback) {
      if (this.form.scopeType !== "all" && !value) {
        callback(new Error("请选择适用范围组织"))
        return
      }
      callback()
    },
    getList() {
      this.loading = true
      listTransferApprovalRule(this.queryParams).then(res => {
        this.list = res.rows || []
        this.total = res.total || 0
      }).finally(() => {
        this.loading = false
      })
    },
    loadDeptOptions() {
      listShopTree().then(res => {
        this.deptOptions = this.handleTree(res.data || [], "deptId")
      })
    },
    normalizer(node) {
      if (node.children && !node.children.length) {
        delete node.children
      }
      return {
        id: node.deptId,
        label: node.deptName,
        children: node.children
      }
    },
    openForm(row, targetStep) {
      this.triggerElement = typeof document === "undefined" ? null : document.activeElement
      this.wizardStep = targetStep || 0
      this.validationResult = null
      this.simulation = this.emptySimulation()
      if (!row) {
        this.form = this.emptyForm()
        this.drawerOpen = true
        this.focusFirstField()
        return
      }
      getTransferApprovalRule(row.ruleId).then(res => {
        const data = res.data || {}
        this.form = Object.assign(this.emptyForm(), data, {
          documentType: "transfer",
          approvalMode: "all_nodes",
          requiredCount: 0,
          allowSelfApprove: "0",
          warningAcknowledged: false,
          nodes: this.ensureSystemNodes()
        })
        this.simulation.toDeptId = ["dept", "to_dept"].includes(this.form.scopeType) ? this.form.scopeId : undefined
        this.simulation.transferType = this.form.transferType === "all" ? "cross_store" : this.form.transferType
        this.simulation.totalQuantity = this.form.conditionValue || 0
        this.drawerOpen = true
        this.focusFirstField()
      })
    },
    openSimulation(row) {
      this.openForm(row, 3)
    },
    focusFirstField() {
      this.$nextTick(() => {
        this.$refs.formRef && this.$refs.formRef.clearValidate()
        const input = this.$refs.ruleNameInput
        if (input && input.focus) input.focus()
      })
    },
    syncDrawerAccessibility() {
      this.$nextTick(() => {
        const component = this.$refs.ruleDrawer
        const drawer = component && component.$refs ? component.$refs.drawer : null
        if (!drawer) return
        drawer.setAttribute("aria-labelledby", "transfer-approval-rule-drawer-title")
      })
    },
    handleDrawerClosed() {
      if (this.triggerElement && this.triggerElement.focus) {
        this.triggerElement.focus()
      }
      this.triggerElement = null
    },
    handleScopeTypeChange(value) {
      if (value === "all") {
        this.form.scopeId = undefined
      } else if (!this.simulation.toDeptId) {
        this.simulation.toDeptId = this.form.scopeId
      }
      this.validationResult = null
    },
    nextStep() {
      this.$refs.formRef.validate(valid => {
        if (!valid || !this.validateBeforeSubmit()) return
        this.wizardStep = Math.min(3, this.wizardStep + 1)
      })
    },
    validateBeforeSubmit() {
      if (this.form.conditionType === "amount") {
        this.$modal.msgError("调拨金额条件暂未支持，请改为不限制或按数量")
        return false
      }
      return true
    },
    buildSubmitForm() {
      return Object.assign({}, this.form, {
        documentType: "transfer",
        approvalMode: "all_nodes",
        requiredCount: 0,
        allowSelfApprove: "0",
        scopeId: this.form.scopeType === "all" ? undefined : this.form.scopeId,
        conditionValue: this.form.conditionType === "none" ? 0 : this.form.conditionValue,
        nodes: this.ensureSystemNodes()
      })
    },
    buildValidationRequest() {
      const payload = this.buildSubmitForm()
      const request = { rule: payload }
      if (this.simulation.fromDeptId || this.simulation.toDeptId) {
        request.transferSample = {
          transferType: this.simulation.transferType || (payload.transferType === "all" ? "cross_store" : payload.transferType),
          fromDeptId: this.simulation.fromDeptId,
          toDeptId: this.simulation.toDeptId,
          totalQuantity: this.simulation.totalQuantity
        }
      }
      return request
    },
    validateConfiguration(forSubmit) {
      if (this.form.status === "0" && !this.simulation.toDeptId) {
        this.$modal.msgError("请选择目标门店，以校验必审候选人")
        return Promise.reject(new Error("TARGET_DEPT_REQUIRED"))
      }
      this.validateLoading = true
      return validateTransferApprovalRule(this.buildValidationRequest()).then(res => {
        this.validationResult = res.data || {}
        if (!forSubmit) {
          if ((this.validationResult.blockingIssues || []).length) {
            this.$modal.msgError("发现阻断问题，请根据红色提示修正")
          } else {
            this.$modal.msgSuccess("规则校验完成")
          }
        }
        return this.validationResult
      }).finally(() => {
        this.validateLoading = false
      })
    },
    doSubmit() {
      this.$refs.formRef.validate(valid => {
        if (!valid || !this.validateBeforeSubmit()) return
        this.submitLoading = true
        this.validateConfiguration(true).then(result => {
          const blockingIssues = result.blockingIssues || []
          if (blockingIssues.length) {
            this.$modal.msgError("存在阻断问题，不能保存")
            return false
          }
          const warnings = result.warnings || []
          if (!warnings.length) return true
          return this.$modal.confirm(
            "检测到 " + warnings.length + " 条警告：\n" + warnings.join("\n") + "\n\n确认仍要保存？"
          ).then(() => true).catch(() => false)
        }).then(confirmed => {
          if (!confirmed) return
          const payload = this.buildSubmitForm()
          payload.warningAcknowledged = true
          payload.validationTargetDeptId = this.simulation.toDeptId
          const action = payload.ruleId ? updateTransferApprovalRule(payload) : addTransferApprovalRule(payload)
          return action.then(() => {
            this.$modal.msgSuccess("操作成功")
            this.drawerOpen = false
            this.getList()
          })
        }).catch(error => {
          if (error && error.message !== "TARGET_DEPT_REQUIRED") throw error
        }).finally(() => {
          this.submitLoading = false
        })
      })
    },
    handleDelete(row) {
      this.$modal.confirm("确认删除审批规则「" + row.ruleName + "」？").then(() => {
        delTransferApprovalRule(row.ruleId, row.version).then(() => {
          this.$modal.msgSuccess("删除成功")
          this.getList()
        })
      })
    },
    openCandidatePreview(row) {
      this.candidatePreviewRuleId = row.ruleId
      this.candidateTargetDeptId = row.scopeType === "dept" ? row.scopeId : undefined
      this.candidatePreview = null
      this.candidatePreviewOpen = true
    },
    loadCandidatePreview() {
      if (!this.candidateTargetDeptId) {
        this.$modal.msgError("请先选择目标门店")
        return
      }
      this.candidatePreviewLoading = true
      previewTransferApprovalCandidates(
        this.candidatePreviewRuleId,
        this.candidateTargetDeptId
      ).then(res => {
        this.candidatePreview = res.data || null
      }).finally(() => {
        this.candidatePreviewLoading = false
      })
    },
    candidateNames(node) {
      const names = (node.candidates || []).map(item => item.userName).filter(Boolean)
      return names.length ? names.join("、") : "未找到"
    },
    previewStateLabel(state) {
      return {
        ready: "候选有效",
        assistant_fallback: "店长助理兜底",
        skipped: "缺失，向上收敛",
        blocked: "缺失，阻止提交"
      }[state] || (state ? "未知候选状态" : "-")
    },
    documentTypeLabel(value) {
      return value === "transfer" ? "调拨单" : "其他单据"
    },
    optionLabel(options, value) {
      const item = options.find(option => option.value === value)
      return item ? item.label : "其他选项"
    },
    conditionText(row) {
      if (!row.conditionType || row.conditionType === "none") return "不限制"
      return this.optionLabel(this.conditionTypeOptions, row.conditionType) + " " + (row.conditionOperator || ">=") + " " + (row.conditionValue || 0)
    }
  }
}
</script>

<style lang="scss" scoped>
.mb12 { margin-bottom: 12px; }
.text-danger { color: #F56C6C; }
.drawer-title {
  display: flex;
  align-items: center;
  gap: 10px;
  font-size: 17px;
  font-weight: 600;
}
.drawer-content {
  padding: 0 20px 88px;
}
.wizard-steps {
  margin-bottom: 22px;
}
.wizard-form {
  min-height: 480px;
}
.node-table {
  margin-top: 10px;
}
.approval-preview {
  margin-bottom: 10px;
}
.drawer-footer {
  position: absolute;
  right: 0;
  bottom: 0;
  left: 0;
  z-index: 2;
  padding: 14px 22px;
  text-align: right;
  background: #fff;
  border-top: 1px solid #e8eaec;
  box-shadow: 0 -4px 12px rgba(0, 0, 0, .04);
}
.validate-actions {
  display: flex;
  align-items: center;
  gap: 12px;
  margin: 4px 0 14px 112px;
}
.validate-hint {
  color: #909399;
  font-size: 12px;
}
.simulation-result {
  margin: 12px 0;
}
.simulation-result ::v-deep .el-card__header {
  padding: 12px 16px;
}
.simulation-result ::v-deep .el-card__header > div {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.matched-rule {
  font-weight: 600;
}
.reason-title {
  margin-top: 8px;
  color: #606266;
}
.reason-list {
  margin: 8px 0 0;
  padding-left: 22px;
  line-height: 1.8;
}
.warning-text {
  color: #E6A23C;
}
.boundary-text {
  margin: 10px 0;
  color: #606266;
}
.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 10px;
  padding: 32px 0;
  color: #909399;
}
.empty-icon {
  font-size: 34px;
}
</style>

<style lang="scss">
.rule-wizard-drawer {
  position: relative;
}
.rule-wizard-drawer .el-drawer__body {
  overflow-y: auto;
}
</style>
