<template>
  <div class="app-container system-management-page salary-config-page">
    <system-page-header
      title="薪资权限配置"
      description="管理薪资方案、档位明细、员工绑定及角色可见范围。"
      icon="el-icon-wallet"
      tip="薪资数据属于敏感信息，请按最小权限原则配置。"
    />
    <el-tabs v-model="activeTab" type="border-card" class="salary-tabs">
      <el-tab-pane label="薪资方案" name="schemes">
        <div class="salary-list-head">
          <div class="salary-filter">
            <el-input
              v-model="schemeQueryParams.schemeName"
              clearable
              size="small"
              placeholder="方案名称"
              prefix-icon="el-icon-search"
              class="salary-filter-input"
              @keyup.enter.native="handleSchemeQuery"
            />
            <el-select
              v-model="schemeQueryParams.socialType"
              clearable
              size="small"
              placeholder="全部社保口径"
              class="salary-filter-select"
            >
              <el-option label="无社保" value="无社保" />
              <el-option label="有社保" value="有社保" />
            </el-select>
            <el-button size="small" type="primary" icon="el-icon-search" @click="handleSchemeQuery">查询</el-button>
            <el-button size="small" icon="el-icon-refresh" @click="resetSchemeQuery">重置</el-button>
          </div>
          <div class="salary-actions">
            <el-button type="primary" size="small" icon="el-icon-plus" @click="handleAddScheme" v-hasPermi="['system:salary:add']">新增方案</el-button>
            <el-button size="small" icon="el-icon-download" @click="handleExportScheme" v-hasPermi="['system:salary:export']">导出</el-button>
          </div>
        </div>

        <el-table
          v-loading="schemeLoading"
          :data="schemeList"
          row-key="schemeId"
          empty-text="暂无薪资方案"
          @row-click="selectScheme"
        >
          <el-table-column label="方案名称" prop="schemeName" min-width="160" />
          <el-table-column label="社保口径" prop="socialType" width="110" />
          <el-table-column label="生效日期" prop="effectiveDate" width="120" />
          <el-table-column label="版本" prop="version" width="80" align="center">
            <template slot-scope="scope"><el-tag size="mini" type="info">v{{ scope.row.version || 1 }}</el-tag></template>
          </el-table-column>
          <el-table-column label="档位数" prop="itemCount" width="90" align="center" />
          <el-table-column label="绑定员工" prop="roleCount" width="100" align="center" />
          <el-table-column label="状态" prop="status" width="90" align="center">
            <template slot-scope="scope">
              <el-tag :type="scope.row.status === '0' ? 'success' : 'info'" size="mini">
                {{ scope.row.status === '0' ? '正常' : '停用' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="280" align="center" fixed="right">
            <template slot-scope="scope">
              <el-button type="text" size="mini" icon="el-icon-tickets" @click.stop="selectScheme(scope.row)">档位</el-button>
              <el-button type="text" size="mini" icon="el-icon-edit" @click.stop="handleEditScheme(scope.row)" v-hasPermi="['system:salary:edit']">编辑</el-button>
              <el-button type="text" size="mini" icon="el-icon-time" @click.stop="handleShowRevisions(scope.row)" v-hasPermi="['system:salary:query']">版本</el-button>
              <el-button type="text" size="mini" icon="el-icon-delete" @click.stop="handleDeleteScheme(scope.row)" v-hasPermi="['system:salary:remove']">删除</el-button>
            </template>
          </el-table-column>
        </el-table>
        <pagination
          v-show="schemeTotal > 0"
          :total="schemeTotal"
          :page.sync="schemeQueryParams.pageNum"
          :limit.sync="schemeQueryParams.pageSize"
          @pagination="getSchemeList"
        />
      </el-tab-pane>

      <el-tab-pane label="档位明细" name="items">
        <div class="salary-list-head">
          <div class="salary-selected">
            <span class="salary-selected-label">当前方案</span>
            <strong>{{ selectedSchemeName }}</strong>
          </div>
          <div class="salary-actions">
            <el-button size="small" icon="el-icon-refresh" :disabled="!selectedScheme" @click="loadSchemeItems">刷新档位</el-button>
            <el-button type="primary" size="small" icon="el-icon-plus" :disabled="!selectedScheme" @click="handleAddSchemeItem" v-hasPermi="['system:salary:add']">新增档位</el-button>
          </div>
        </div>

        <el-table
          v-loading="itemLoading"
          :data="currentSchemeItems"
          empty-text="请选择薪资方案后维护档位"
        >
          <el-table-column label="岗位" prop="postName" min-width="130" />
          <el-table-column label="档位" prop="gradeName" min-width="120" />
          <el-table-column label="地区" prop="regionName" width="120" />
          <el-table-column label="基本工资" prop="baseSalary" width="100" align="right">
            <template slot-scope="scope">{{ formatMoney(scope.row.baseSalary) }}</template>
          </el-table-column>
          <el-table-column label="工资合计" prop="totalSalary" width="110" align="right">
            <template slot-scope="scope">
              <strong>{{ formatMoney(scope.row.totalSalary) }}</strong>
            </template>
          </el-table-column>
          <el-table-column label="排序" prop="itemSort" width="80" align="center" />
          <el-table-column label="操作" width="190" align="center" fixed="right">
            <template slot-scope="scope">
              <el-button type="text" size="mini" icon="el-icon-view" @click="handleViewSchemeItem(scope.row)">详情</el-button>
              <el-button type="text" size="mini" icon="el-icon-edit" @click="handleEditSchemeItem(scope.row)" v-hasPermi="['system:salary:edit']">编辑</el-button>
              <el-button type="text" size="mini" icon="el-icon-delete" @click="handleDeleteSchemeItem(scope.row)" v-hasPermi="['system:salary:remove']">删除</el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>

      <el-tab-pane label="员工绑定" name="user">
        <div class="content-card salary-list-card">
          <div class="salary-list-head">
            <div class="salary-filter">
              <el-input
                v-model="filterUserKeyword"
                clearable
                size="small"
                placeholder="按员工名称筛选"
                prefix-icon="el-icon-search"
                class="salary-filter-input"
              />
              <el-select
                v-model="filterSocialType"
                clearable
                size="small"
                placeholder="全部社保口径"
                class="salary-filter-select"
              >
                <el-option label="无社保" value="无社保" />
                <el-option label="有社保" value="有社保" />
              </el-select>
            </div>
            <el-button
              type="primary"
              size="small"
              icon="el-icon-plus"
              @click="handleAdd"
              v-hasPermi="['system:salary:role']"
            >新增</el-button>
          </div>

          <el-table
            v-loading="loading"
            :data="filteredRules"
            empty-text="暂无员工薪资绑定"
          >
            <el-table-column label="员工" min-width="150">
              <template slot-scope="scope">
                <div>{{ scope.row.nickName || scope.row.userName || "-" }}</div>
                <div class="salary-sub-text">{{ scope.row.userName || "-" }}</div>
              </template>
            </el-table-column>
            <el-table-column label="核算门店" prop="shopDeptName" min-width="130" show-overflow-tooltip />
            <el-table-column label="社保口径" prop="socialType" width="100" />
            <el-table-column label="薪资方案" prop="schemeName" min-width="150" show-overflow-tooltip />
            <el-table-column label="岗位" prop="postName" min-width="110" show-overflow-tooltip />
            <el-table-column label="档位" prop="gradeName" min-width="120" />
            <el-table-column label="地区" prop="regionName" min-width="120" show-overflow-tooltip />
            <el-table-column label="工资合计" prop="totalSalary" width="110" align="right">
              <template slot-scope="scope">
                <strong>{{ formatMoney(scope.row.totalSalary) }}</strong>
              </template>
            </el-table-column>
            <el-table-column label="生效日期" prop="effectiveDate" width="120" />
            <el-table-column label="优先级" prop="priority" width="80" align="center" />
            <el-table-column label="备注" prop="remark" min-width="160" show-overflow-tooltip />
            <el-table-column label="操作" width="130" align="center" fixed="right">
              <template slot-scope="scope">
                <el-button
                  type="text"
                  size="mini"
                  icon="el-icon-edit"
                  @click="handleEdit(scope.row)"
                  v-hasPermi="['system:salary:role']"
                >编辑</el-button>
                <el-button
                  type="text"
                  size="mini"
                  icon="el-icon-delete"
                  @click="handleUnbindRule(scope.row)"
                  v-hasPermi="['system:salary:role']"
                >移除</el-button>
              </template>
            </el-table-column>
          </el-table>
        </div>
      </el-tab-pane>
    </el-tabs>

    <el-dialog :title="schemeDialogTitle" :visible.sync="schemeOpen" width="640px" append-to-body>
      <el-form ref="schemeForm" :model="schemeForm" :rules="schemeRules" label-width="96px" size="small">
        <el-row :gutter="12">
          <el-col :xs="24" :sm="12">
            <el-form-item label="方案名称" prop="schemeName">
              <el-input v-model="schemeForm.schemeName" maxlength="100" placeholder="请输入方案名称" />
            </el-form-item>
          </el-col>
          <el-col :xs="24" :sm="12">
            <el-form-item label="社保口径" prop="socialType">
              <el-radio-group v-model="schemeForm.socialType" size="small">
                <el-radio-button label="无社保">无社保</el-radio-button>
                <el-radio-button label="有社保">有社保</el-radio-button>
              </el-radio-group>
            </el-form-item>
          </el-col>
          <el-col :xs="24" :sm="12">
            <el-form-item label="生效日期" prop="effectiveDate">
              <el-date-picker
                v-model="schemeForm.effectiveDate"
                type="date"
                value-format="yyyy-MM-dd"
                placeholder="请选择生效日期"
                style="width: 100%"
              />
            </el-form-item>
          </el-col>
          <el-col :xs="24" :sm="12">
            <el-form-item label="状态" prop="status">
              <el-radio-group v-model="schemeForm.status" size="small">
                <el-radio-button label="0">正常</el-radio-button>
                <el-radio-button label="1">停用</el-radio-button>
              </el-radio-group>
            </el-form-item>
          </el-col>
          <el-col :xs="24" v-if="schemeForm.schemeId">
            <el-alert
              title="已生效方案的普通修改必须设置未来生效日期；紧急原地修正需要独立权限并填写原因。"
              type="warning"
              :closable="false"
              show-icon
            />
          </el-col>
          <el-col :xs="24">
            <el-form-item label="变更原因" prop="changeReason">
              <el-input v-model="schemeForm.changeReason" type="textarea" :rows="2" maxlength="500" show-word-limit placeholder="说明本次新增或修改原因" />
            </el-form-item>
          </el-col>
          <el-col :xs="24" v-if="schemeForm.schemeId && isEffectiveDate(schemeForm.effectiveDate)" v-hasPermi="['system:salary:emergency']">
            <el-form-item label="紧急修正">
              <el-switch v-model="schemeForm.emergencyCorrection" active-text="允许修正已生效版本" />
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>
      <div slot="footer" class="dialog-footer">
        <el-button @click="schemeOpen = false">取 消</el-button>
        <el-button type="primary" :loading="schemeSaving" @click="handleSaveScheme" v-hasPermi="['system:salary:add','system:salary:edit']">保 存</el-button>
      </div>
    </el-dialog>

    <el-dialog :title="itemDialogTitle" :visible.sync="itemOpen" width="760px" append-to-body>
      <el-form ref="itemForm" :model="itemForm" :rules="itemRules" label-width="96px" size="small">
        <el-row :gutter="12">
          <el-col :xs="24" :sm="12">
            <el-form-item label="岗位" prop="postName">
              <el-input v-model="itemForm.postName" maxlength="64" placeholder="请输入岗位" />
            </el-form-item>
          </el-col>
          <el-col :xs="24" :sm="12">
            <el-form-item label="档位" prop="gradeName">
              <el-input v-model="itemForm.gradeName" maxlength="64" placeholder="请输入档位" />
            </el-form-item>
          </el-col>
          <el-col :xs="24" :sm="12">
            <el-form-item label="地区" prop="regionName">
              <el-input v-model="itemForm.regionName" maxlength="100" placeholder="请输入地区" />
            </el-form-item>
          </el-col>
          <el-col :xs="24" :sm="12">
            <el-form-item label="基本工资" prop="baseSalary">
              <el-input-number
                v-model="itemForm.baseSalary"
                controls-position="right"
                :min="0"
                :step="10"
                style="width: 100%"
                @change="recalculateItemTotal"
              />
            </el-form-item>
          </el-col>
          <el-col
            v-for="field in itemSalaryFields"
            :key="field.prop"
            :xs="24"
            :sm="12"
          >
            <el-form-item :label="field.label" :prop="field.prop">
              <el-input-number
                v-model="itemForm[field.prop]"
                controls-position="right"
                :min="0"
                :step="10"
                style="width: 100%"
                @change="recalculateItemTotal"
              />
            </el-form-item>
          </el-col>
          <el-col :xs="24" :sm="12">
            <el-form-item label="排序">
              <el-input-number v-model="itemForm.itemSort" controls-position="right" :min="0" :step="10" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :xs="24" :sm="12">
            <el-form-item label="工资合计">
              <el-input :value="formatMoney(itemForm.totalSalary)" disabled>
                <template slot="prepend">￥</template>
              </el-input>
            </el-form-item>
          </el-col>
          <el-col :xs="24">
            <el-form-item label="变更原因" prop="changeReason">
              <el-input v-model="itemForm.changeReason" type="textarea" :rows="2" maxlength="500" show-word-limit placeholder="说明本次档位变更原因" />
            </el-form-item>
          </el-col>
          <el-col :xs="24" v-if="selectedScheme && isEffectiveDate(selectedScheme.effectiveDate)" v-hasPermi="['system:salary:emergency']">
            <el-form-item label="紧急修正">
              <el-switch v-model="itemForm.emergencyCorrection" active-text="允许修正已生效方案档位" />
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>
      <div slot="footer" class="dialog-footer">
        <el-button @click="itemOpen = false">取 消</el-button>
        <el-button type="primary" :loading="itemSaving" @click="handleSaveSchemeItem" v-hasPermi="['system:salary:add','system:salary:edit']">保 存</el-button>
      </div>
    </el-dialog>

    <el-dialog :title="dialogTitle" :visible.sync="open" width="760px" append-to-body>
      <el-form
        ref="ruleForm"
        :model="ruleForm"
        :rules="rules"
        label-width="96px"
        size="small"
      >
        <el-row :gutter="12">
          <el-col :xs="24" :sm="12">
            <el-form-item label="员工" prop="userId">
              <el-select
                v-model="ruleForm.userId"
                filterable
                placeholder="请选择员工"
                style="width: 100%"
                :disabled="!!ruleForm.relationId"
                @change="handleFormUserChange"
              >
                <el-option
                  v-for="user in filteredUserOptions"
                  :key="user.userId"
                  :label="formatUserOption(user)"
                  :value="user.userId"
                />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :xs="24" :sm="12">
            <el-form-item label="核算门店" prop="shopDeptId">
              <el-select v-model="ruleForm.shopDeptId" filterable placeholder="请选择核算门店" style="width: 100%" @change="handleFormShopChange">
                <el-option
                  v-for="shop in shopOptionsFlat"
                  :key="shop.deptId"
                  :label="shop.deptName"
                  :value="shop.deptId"
                />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :xs="24" :sm="12">
            <el-form-item label="社保口径" required>
              <el-radio-group v-model="formSocialType" size="small" @change="handleFormSocialTypeChange">
                <el-radio-button label="无社保">无社保</el-radio-button>
                <el-radio-button label="有社保">有社保</el-radio-button>
              </el-radio-group>
            </el-form-item>
          </el-col>
          <el-col :xs="24" :sm="12">
            <el-form-item label="薪资方案" prop="schemeId">
              <el-select v-model="ruleForm.schemeId" filterable placeholder="请选择薪资方案" style="width: 100%" @change="handleFormSchemeChange">
                <el-option
                  v-for="scheme in schemeChoices"
                  :key="scheme.schemeId"
                  :label="scheme.schemeName"
                  :value="scheme.schemeId"
                />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :xs="24" :sm="12">
            <el-form-item label="薪资档位" prop="itemId">
              <el-select v-model="ruleForm.itemId" filterable placeholder="请选择已有档位" style="width: 100%">
                <el-option
                  v-for="item in currentBindingItems"
                  :key="item.itemId"
                  :label="formatItemOption(item)"
                  :value="item.itemId"
                />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :xs="24" :sm="12">
            <el-form-item label="生效日期" prop="effectiveDate">
              <el-date-picker
                v-model="ruleForm.effectiveDate"
                type="date"
                value-format="yyyy-MM-dd"
                placeholder="请选择生效日期"
                style="width: 100%"
              />
            </el-form-item>
          </el-col>
          <el-col :xs="24" :sm="12">
            <el-form-item label="优先级">
              <el-input-number v-model="ruleForm.priority" controls-position="right" :min="0" :step="10" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :xs="24">
            <el-form-item label="备注">
              <el-input
                v-model="ruleForm.remark"
                type="textarea"
                :rows="2"
                placeholder="请输入备注"
              />
            </el-form-item>
          </el-col>
          <el-col :xs="24">
            <div class="salary-preview">
              <div class="salary-preview-title">档位明细预览</div>
              <div v-if="selectedBindingItem" class="salary-preview-grid">
                <div class="salary-preview-item">
                  <span>岗位</span>
                  <strong>{{ selectedBindingItem.postName || "-" }}</strong>
                </div>
                <div class="salary-preview-item">
                  <span>档位</span>
                  <strong>{{ selectedBindingItem.gradeName || "-" }}</strong>
                </div>
                <div class="salary-preview-item">
                  <span>地区</span>
                  <strong>{{ selectedBindingItem.regionName || "-" }}</strong>
                </div>
                <div
                  v-for="field in bindingPreviewFields"
                  :key="field.prop"
                  class="salary-preview-item"
                >
                  <span>{{ field.label }}</span>
                  <strong>{{ formatNullableMoney(selectedBindingItem[field.prop]) }}</strong>
                </div>
                <div class="salary-preview-item salary-preview-total">
                  <span>工资合计</span>
                  <strong>{{ formatMoney(selectedBindingItem.totalSalary) }}</strong>
                </div>
              </div>
              <div v-else class="salary-preview-empty">请选择已有薪资档位</div>
            </div>
          </el-col>
        </el-row>
      </el-form>
      <div slot="footer" class="dialog-footer">
        <el-button @click="open = false">取 消</el-button>
        <el-button
          type="primary"
          :loading="saving"
          @click="handleSaveRule"
          v-hasPermi="['system:salary:role']"
        >保 存</el-button>
      </div>
    </el-dialog>

    <el-drawer title="薪资档位构成" :visible.sync="itemDetailOpen" size="420px" append-to-body>
      <div v-if="itemDetail" class="salary-detail-drawer">
        <el-descriptions :column="1" border size="small">
          <el-descriptions-item label="岗位 / 档位">{{ itemDetail.postName || "-" }} / {{ itemDetail.gradeName || "-" }}</el-descriptions-item>
          <el-descriptions-item label="地区">{{ itemDetail.regionName || "-" }}</el-descriptions-item>
          <el-descriptions-item v-for="field in itemDetailFields" :key="field.prop" :label="field.label">
            ￥{{ formatMoney(itemDetail[field.prop]) }}
          </el-descriptions-item>
          <el-descriptions-item label="工资合计"><strong>￥{{ formatMoney(itemDetail.totalSalary) }}</strong></el-descriptions-item>
        </el-descriptions>
      </div>
    </el-drawer>

    <el-drawer title="薪资方案版本历史" :visible.sync="revisionOpen" size="620px" append-to-body>
      <div class="salary-revision-drawer" v-loading="revisionLoading">
        <el-alert title="恢复历史版本会创建一个新版本，不会覆盖或删除已有修订记录。" type="info" :closable="false" show-icon />
        <el-table :data="revisionList" size="small" empty-text="暂无修订记录">
          <el-table-column label="版本" prop="version" width="72"><template slot-scope="scope">v{{ scope.row.version }}</template></el-table-column>
          <el-table-column label="类型" prop="changeType" width="145" />
          <el-table-column label="原因" prop="changeReason" min-width="180" show-overflow-tooltip />
          <el-table-column label="操作人" prop="createBy" width="100" />
          <el-table-column label="时间" prop="createTime" width="160"><template slot-scope="scope">{{ parseTime(scope.row.createTime) }}</template></el-table-column>
          <el-table-column label="操作" width="105" fixed="right">
            <template slot-scope="scope">
              <el-button
                type="text"
                size="mini"
                :disabled="!revisionScheme || Number(scope.row.version) === Number(revisionScheme.version)"
                @click="handleRollbackRevision(scope.row)"
                v-hasPermi="['system:salary:edit']"
              >恢复为新版本</el-button>
            </template>
          </el-table-column>
        </el-table>
      </div>
    </el-drawer>
  </div>
</template>

<script>
import {
  listSalaryScheme,
  getSalaryScheme,
  addSalaryScheme,
  updateSalaryScheme,
  delSalaryScheme,
  listSalaryItems,
  salarySchemeOptions,
  salaryUserOptions,
  salaryShopTree,
  getUserSalaryBatch,
  saveUserSalary,
  addSalaryItem,
  updateSalaryItem,
  delSalaryItem,
  salaryImpactPreview,
  listSalaryRevisions,
  rollbackSalaryRevision
} from "@/api/system/salaryConfig"
import { confirmExportAction } from "@/utils/exportConfirm"

const SOCIAL_TYPES = {
  noSocial: "无社保",
  social: "有社保"
}

const DEFAULT_REGION = "默认"

export default {
  name: "SystemSalaryConfig",
  data() {
    return {
      activeTab: "schemes",
      loading: false,
      saving: false,
      schemeLoading: false,
      itemLoading: false,
      schemeSaving: false,
      itemSaving: false,
      open: false,
      schemeOpen: false,
      itemOpen: false,
      itemDetailOpen: false,
      itemDetail: null,
      revisionOpen: false,
      revisionLoading: false,
      revisionList: [],
      revisionScheme: null,
      filterUserKeyword: "",
      filterSocialType: "",
      formSocialType: SOCIAL_TYPES.noSocial,
      userOptions: [],
      shopOptions: [],
      schemeOptions: [],
      schemeList: [],
      schemeItems: [],
      schemeTotal: 0,
      selectedScheme: null,
      schemeQueryParams: {
        pageNum: 1,
        pageSize: 10,
        schemeName: undefined,
        socialType: undefined
      },
      userBindings: [],
      schemeForm: this.defaultSchemeForm(),
      itemForm: this.defaultItemForm(),
      ruleForm: this.defaultRuleForm(),
      schemeRules: {
        schemeName: [{ required: true, message: "请输入方案名称", trigger: "blur" }],
        socialType: [{ required: true, message: "请选择社保口径", trigger: "change" }],
        effectiveDate: [{ required: true, message: "请选择生效日期", trigger: "change" }],
        status: [{ required: true, message: "请选择状态", trigger: "change" }],
        changeReason: [{ required: true, message: "请填写变更原因", trigger: "blur" }]
      },
      itemRules: {
        postName: [{ required: true, message: "请输入岗位", trigger: "blur" }],
        regionName: [{ required: true, message: "请输入地区", trigger: "blur" }],
        baseSalary: [{ required: true, message: "请输入基本工资", trigger: "blur" }],
        changeReason: [{ required: true, message: "请填写档位变更原因", trigger: "blur" }]
      },
      rules: {
        userId: [{ required: true, message: "请选择员工", trigger: "change" }],
        shopDeptId: [{ required: true, message: "请选择核算门店", trigger: "change" }],
        schemeId: [{ required: true, message: "请选择薪资方案", trigger: "change" }],
        itemId: [{ required: true, message: "请选择薪资档位", trigger: "change" }],
        effectiveDate: [{ required: true, message: "请选择生效日期", trigger: "change" }]
      }
    }
  },
  computed: {
    userMap() {
      return this.userOptions.reduce((map, user) => {
        map[user.userId] = user
        return map
      }, {})
    },
    filteredUserOptions() {
      if (!this.ruleForm.shopDeptId || !this.userOptions.some(user => Array.isArray(user.shopDeptIds))) {
        return this.userOptions
      }
      return this.userOptions.filter(user => Number(user.deptId) === Number(this.ruleForm.shopDeptId) ||
        (user.shopDeptIds || []).map(Number).indexOf(Number(this.ruleForm.shopDeptId)) !== -1)
    },
    formUser() {
      return this.userMap[this.ruleForm.userId]
    },
    currentScheme() {
      return this.schemeOptions.find(scheme => Number(scheme.schemeId) === Number(this.ruleForm.schemeId)) ||
        this.schemeOptions.find(scheme => scheme.socialType === this.formSocialType)
    },
    schemeChoices() {
      return this.schemeOptions.filter(scheme => !this.formSocialType || scheme.socialType === this.formSocialType)
    },
    currentBindingItems() {
      const scheme = this.schemeOptions.find(item => Number(item.schemeId) === Number(this.ruleForm.schemeId))
      return scheme && scheme.items ? scheme.items : []
    },
    selectedBindingItem() {
      return this.itemMap[this.ruleForm.itemId]
    },
    bindingPreviewFields() {
      if (this.formSocialType === SOCIAL_TYPES.social) {
        return [
          { prop: "baseSalary", label: "基本工资" },
          { prop: "managementAllowance", label: "管理津贴" },
          { prop: "overtimePay", label: "加班费" },
          { prop: "rewardAllowance", label: "奖励津贴" },
          { prop: "fullAttendanceBonus", label: "全勤奖" },
          { prop: "commuteSubsidy", label: "通勤补贴" }
        ]
      }
      return [
        { prop: "baseSalary", label: "基本工资" },
        { prop: "overtimePay", label: "加班费" },
        { prop: "rewardAllowance", label: "奖励津贴" },
        { prop: "fullAttendanceBonus", label: "全勤奖" },
        { prop: "socialSubsidy", label: "社保补贴" }
      ]
    },
    shopOptionsFlat() {
      return this.flattenShopOptions(this.shopOptions)
    },
    selectedSchemeName() {
      if (!this.selectedScheme) {
        return "未选择"
      }
      return `${this.selectedScheme.schemeName || "-"} / ${this.selectedScheme.socialType || "-"}`
    },
    currentSchemeItems() {
      return this.schemeItems
    },
    allItems() {
      return this.schemeOptions.reduce((rows, scheme) => {
        const items = scheme.items || []
        return rows.concat(items.map(item => Object.assign({}, item, {
          schemeId: scheme.schemeId,
          schemeName: scheme.schemeName,
          socialType: scheme.socialType,
          effectiveDate: scheme.effectiveDate
        })))
      }, [])
    },
    itemMap() {
      return this.allItems.reduce((map, item) => {
        map[item.itemId] = item
        return map
      }, {})
    },
    allUserRules() {
      return this.userBindings
        .map(binding => {
          const item = this.itemMap[binding.itemId] || {}
          const user = this.userMap[binding.userId]
          if (!user && !binding.userName) {
            return null
          }
          return Object.assign({}, item, binding, {
            userName: binding.userName || (user ? user.userName : ""),
            nickName: binding.nickName || (user ? user.nickName : ""),
            deptName: binding.deptName || (user ? (user.deptName || (user.dept && user.dept.deptName)) : ""),
            shopDeptName: binding.shopDeptName || this.shopNameById(binding.shopDeptId),
            schemeName: binding.schemeName || item.schemeName,
            socialType: binding.socialType || item.socialType,
            postName: binding.postName || item.postName,
            gradeName: binding.gradeName || item.gradeName,
            regionName: binding.regionName || item.regionName,
            totalSalary: binding.totalSalary !== undefined ? binding.totalSalary : item.totalSalary
          })
        })
        .filter(Boolean)
    },
    filteredRules() {
      const userKeyword = (this.filterUserKeyword || "").trim().toLowerCase()
      return this.allUserRules.filter(item => {
        const userText = `${item.userName || ""} ${item.nickName || ""}`.toLowerCase()
        const matchRole = !userKeyword || userText.indexOf(userKeyword) !== -1
        const matchSocial = !this.filterSocialType || item.socialType === this.filterSocialType
        return matchRole && matchSocial
      })
    },
    itemSalaryFields() {
      return [
        { prop: "managementAllowance", label: "管理津贴" },
        { prop: "overtimePay", label: "加班费" },
        { prop: "rewardAllowance", label: "奖励津贴" },
        { prop: "fullAttendanceBonus", label: "全勤奖" },
        { prop: "socialSubsidy", label: "社保补贴" },
        { prop: "commuteSubsidy", label: "通勤补贴" }
      ]
    },
    itemDetailFields() {
      return [{ prop: "baseSalary", label: "基本工资" }].concat(this.itemSalaryFields)
    },
    schemeDialogTitle() {
      return this.schemeForm.schemeId ? "编辑薪资方案" : "新增薪资方案"
    },
    itemDialogTitle() {
      return this.itemForm.itemId ? "编辑档位明细" : "新增档位明细"
    },
    dialogTitle() {
      return this.ruleForm.relationId ? "编辑员工薪资绑定" : "新增员工薪资绑定"
    }
  },
  created() {
    this.initPage()
    this.getSchemeList()
  },
  methods: {
    defaultSchemeForm() {
      return {
        schemeId: undefined,
        schemeName: "",
        socialType: SOCIAL_TYPES.noSocial,
        effectiveDate: "",
        status: "0",
        version: undefined,
        changeReason: "创建薪资方案",
        emergencyCorrection: false
      }
    },
    defaultItemForm() {
      return {
        itemId: undefined,
        schemeId: undefined,
        postName: "",
        gradeName: "",
        regionName: DEFAULT_REGION,
        baseSalary: 0,
        managementAllowance: 0,
        overtimePay: 0,
        rewardAllowance: 0,
        fullAttendanceBonus: 0,
        socialSubsidy: 0,
        commuteSubsidy: 0,
        totalSalary: 0,
        itemSort: 10,
        schemeVersion: undefined,
        changeReason: "维护薪资档位",
        emergencyCorrection: false
      }
    },
    defaultRuleForm() {
      return {
        relationId: undefined,
        userId: undefined,
        shopDeptId: undefined,
        schemeId: undefined,
        itemId: undefined,
        priority: 10,
        effectiveDate: "",
        endDate: "",
        status: "0",
        remark: ""
      }
    },
    getSchemeList() {
      this.schemeLoading = true
      return listSalaryScheme(this.schemeQueryParams).then(response => {
        const rows = response.rows || []
        this.schemeList = rows
        this.schemeTotal = response.total || rows.length
        const selectedId = this.selectedScheme ? Number(this.selectedScheme.schemeId) : undefined
        this.selectedScheme = rows.find(row => Number(row.schemeId) === selectedId) || rows[0] || null
        if (this.selectedScheme) {
          return this.loadSchemeItems()
        }
        this.schemeItems = []
        return Promise.resolve()
      }).finally(() => {
        this.schemeLoading = false
      })
    },
    refreshSchemeOptions() {
      return salarySchemeOptions().then(response => {
        this.schemeOptions = response.data || []
        this.ensureDefaultScheme()
      })
    },
    handleSchemeQuery() {
      this.schemeQueryParams.pageNum = 1
      this.getSchemeList()
    },
    resetSchemeQuery() {
      this.schemeQueryParams = {
        pageNum: 1,
        pageSize: this.schemeQueryParams.pageSize,
        schemeName: undefined,
        socialType: undefined
      }
      this.getSchemeList()
    },
    handleAddScheme() {
      this.schemeForm = Object.assign(this.defaultSchemeForm(), {
        effectiveDate: this.today()
      })
      this.schemeOpen = true
      this.$nextTick(() => {
        if (this.$refs.schemeForm) {
          this.$refs.schemeForm.clearValidate()
        }
      })
    },
    handleEditScheme(row) {
      getSalaryScheme(row.schemeId).then(response => {
        this.schemeForm = Object.assign(this.defaultSchemeForm(), response.data || row, {
          changeReason: "",
          emergencyCorrection: false
        })
        this.schemeOpen = true
        this.$nextTick(() => {
          if (this.$refs.schemeForm) {
            this.$refs.schemeForm.clearValidate()
          }
        })
      })
    },
    handleSaveScheme() {
      this.$refs.schemeForm.validate(valid => {
        if (!valid) {
          return
        }
        this.schemeSaving = true
        const payload = Object.assign({}, this.schemeForm)
        const previewRequest = {
          operation: payload.schemeId ? "SCHEME_UPDATE" : "SCHEME_CREATE",
          schemeId: payload.schemeId,
          expectedVersion: payload.version,
          effectiveDate: payload.effectiveDate
        }
        this.previewAndConfirm(previewRequest, payload.schemeId ? "保存方案修改" : "创建薪资方案").then(confirmed => {
          if (!confirmed) return null
          return (payload.schemeId ? updateSalaryScheme(payload) : addSalaryScheme(payload)).then(() => {
            this.$modal.msgSuccess("薪资方案已保存")
            this.schemeOpen = false
            return Promise.all([this.getSchemeList(), this.refreshSchemeOptions()])
          })
        }).finally(() => {
          this.schemeSaving = false
        })
      })
    },
    handleDeleteScheme(row) {
      const emergencyCorrection = this.isEffectiveDate(row.effectiveDate)
      if (emergencyCorrection && !this.canEmergencyCorrection()) {
        this.$modal.msgError("已生效薪资方案只能由具有紧急修正权限的管理员删除")
        return
      }
      this.$prompt("删除会保留不可修改的修订快照，请填写删除原因", "删除薪资方案", {
        confirmButtonText: "预览影响",
        cancelButtonText: "取消",
        inputPlaceholder: "必填，最多500字",
        inputValidator: value => Boolean(value && value.trim() && value.trim().length <= 500),
        inputErrorMessage: "请填写500字以内的删除原因"
      }).then(({ value }) => {
        const changeReason = value.trim()
        return this.previewAndConfirm({
          operation: "SCHEME_DELETE",
          schemeId: row.schemeId,
          expectedVersion: row.version,
          effectiveDate: row.effectiveDate
        }, "删除薪资方案 [" + row.schemeName + "]").then(confirmed => {
          if (!confirmed) return false
          return delSalaryScheme(row.schemeId, {
            expectedVersion: row.version,
            changeReason,
            emergencyCorrection
          }).then(() => true)
        })
      }).then(deleted => {
        if (!deleted) return null
        this.$modal.msgSuccess("已删除")
        if (this.selectedScheme && Number(this.selectedScheme.schemeId) === Number(row.schemeId)) {
          this.selectedScheme = null
          this.schemeItems = []
        }
        return Promise.all([this.getSchemeList(), this.refreshSchemeOptions(), this.loadAllUserSalary()])
      }).catch(() => {})
    },
    handleExportScheme() {
      confirmExportAction(this, {
        moduleName: "薪资方案",
        rangeLabel: "当前查询条件下的薪资方案",
        filterLabel: this.buildSalaryExportFilterLabel(),
        sensitiveFields: ["薪资档位", "基本工资", "工资合计"]
      }).then(() => {
        this.download("system/salaryConfig/export", { ...this.schemeQueryParams }, this.exportFileName("薪资方案"))
      })
    },
    buildSalaryExportFilterLabel() {
      return [
        "方案名称：" + (this.schemeQueryParams.schemeName || "全部"),
        "状态：" + (this.schemeQueryParams.status || "全部")
      ].join("；")
    },
    selectScheme(row) {
      this.selectedScheme = row
      this.activeTab = "items"
      this.loadSchemeItems()
    },
    loadSchemeItems() {
      if (!this.selectedScheme || !this.selectedScheme.schemeId) {
        this.schemeItems = []
        return Promise.resolve()
      }
      this.itemLoading = true
      return listSalaryItems(this.selectedScheme.schemeId).then(response => {
        this.schemeItems = response.data || []
      }).finally(() => {
        this.itemLoading = false
      })
    },
    handleAddSchemeItem() {
      if (!this.selectedScheme) {
        this.$modal.msgWarning("请先选择薪资方案")
        return
      }
      this.itemForm = Object.assign(this.defaultItemForm(), {
        schemeId: this.selectedScheme.schemeId,
        schemeVersion: this.selectedScheme.version,
        changeReason: "新增薪资档位"
      })
      this.recalculateItemTotal()
      this.itemOpen = true
      this.$nextTick(() => {
        if (this.$refs.itemForm) {
          this.$refs.itemForm.clearValidate()
        }
      })
    },
    handleEditSchemeItem(row) {
      this.itemForm = Object.assign(this.defaultItemForm(), row, {
        schemeId: row.schemeId || (this.selectedScheme ? this.selectedScheme.schemeId : undefined),
        schemeVersion: this.selectedScheme ? this.selectedScheme.version : undefined,
        changeReason: "",
        emergencyCorrection: false
      })
      this.recalculateItemTotal()
      this.itemOpen = true
      this.$nextTick(() => {
        if (this.$refs.itemForm) {
          this.$refs.itemForm.clearValidate()
        }
      })
    },
    handleSaveSchemeItem() {
      this.$refs.itemForm.validate(valid => {
        if (!valid) {
          return
        }
        this.itemSaving = true
        const payload = this.buildSchemeItemPayload()
        const previewRequest = {
          operation: payload.itemId ? "ITEM_UPDATE" : "ITEM_CREATE",
          schemeId: payload.schemeId,
          itemId: payload.itemId,
          expectedVersion: payload.schemeVersion,
          effectiveDate: this.selectedScheme ? this.selectedScheme.effectiveDate : undefined
        }
        this.previewAndConfirm(previewRequest, payload.itemId ? "保存档位修改" : "新增薪资档位").then(confirmed => {
          if (!confirmed) return null
          return (payload.itemId ? updateSalaryItem(payload) : addSalaryItem(payload)).then(() => {
            this.$modal.msgSuccess("档位明细已保存")
            this.itemOpen = false
            return Promise.all([this.getSchemeList(), this.refreshSchemeOptions(), this.loadAllUserSalary()])
          })
        }).finally(() => {
          this.itemSaving = false
        })
      })
    },
    handleDeleteSchemeItem(row) {
      const expectedVersion = this.selectedScheme ? this.selectedScheme.version : undefined
      const emergencyCorrection = Boolean(this.selectedScheme && this.isEffectiveDate(this.selectedScheme.effectiveDate))
      if (emergencyCorrection && !this.canEmergencyCorrection()) {
        this.$modal.msgError("已生效薪资档位只能由具有紧急修正权限的管理员删除")
        return
      }
      this.$prompt("请填写删除该薪资档位的原因", "删除薪资档位", {
        confirmButtonText: "预览影响",
        cancelButtonText: "取消",
        inputPlaceholder: "必填，最多500字",
        inputValidator: value => Boolean(value && value.trim() && value.trim().length <= 500),
        inputErrorMessage: "请填写500字以内的删除原因"
      }).then(({ value }) => {
        return this.previewAndConfirm({
          operation: "ITEM_DELETE",
          schemeId: row.schemeId || (this.selectedScheme && this.selectedScheme.schemeId),
          itemId: row.itemId,
          expectedVersion,
          effectiveDate: this.selectedScheme && this.selectedScheme.effectiveDate
        }, "删除档位 [" + (row.gradeName || row.postName) + "]").then(confirmed => {
          if (!confirmed) return false
          return delSalaryItem(row.itemId, {
            expectedVersion,
            changeReason: value.trim(),
            emergencyCorrection
          }).then(() => true)
        })
      }).then(deleted => {
        if (!deleted) return null
        this.$modal.msgSuccess("已删除")
        return Promise.all([this.getSchemeList(), this.refreshSchemeOptions(), this.loadAllUserSalary()])
      }).catch(() => {})
    },
    buildSchemeItemPayload() {
      this.recalculateItemTotal()
      return Object.assign({}, this.itemForm, {
        schemeId: this.itemForm.schemeId || (this.selectedScheme ? this.selectedScheme.schemeId : undefined),
        schemeVersion: this.itemForm.schemeVersion || (this.selectedScheme ? this.selectedScheme.version : undefined),
        itemSort: this.itemForm.itemSort || 10
      })
    },
    handleViewSchemeItem(row) {
      this.itemDetail = Object.assign({}, row)
      this.itemDetailOpen = true
    },
    recalculateItemTotal() {
      const keys = ["baseSalary", "managementAllowance", "overtimePay", "rewardAllowance", "fullAttendanceBonus", "socialSubsidy", "commuteSubsidy"]
      this.itemForm.totalSalary = keys.reduce((sum, key) => sum + Number(this.itemForm[key] || 0), 0)
    },
    today() {
      const now = new Date()
      return now.getFullYear() + "-" +
        String(now.getMonth() + 1).padStart(2, "0") + "-" +
        String(now.getDate()).padStart(2, "0")
    },
    isEffectiveDate(value) {
      return Boolean(value && String(value) <= this.today())
    },
    canEmergencyCorrection() {
      return Boolean(this.$auth && this.$auth.hasPermi("system:salary:emergency"))
    },
    previewAndConfirm(request, actionLabel) {
      return salaryImpactPreview(request).then(response => {
        const impact = response.data || {}
        const conflicts = impact.conflicts || []
        if (conflicts.length) {
          this.$modal.msgError(conflicts.join("；"))
          return false
        }
        const parts = [
          actionLabel,
          `影响员工 ${impact.affectedUserCount || 0} 人`,
          `直接绑定 ${impact.directUserCount || 0} 人`,
          `角色继承 ${impact.roleInheritedUserCount || 0} 人`,
          `已生效 ${impact.effectiveNowCount || 0} 人`,
          `未来生效 ${impact.futureEffectiveCount || 0} 人`
        ]
        if (impact.currentVersion) parts.push(`当前版本 v${impact.currentVersion}`)
        if ((impact.warnings || []).length) parts.push(`注意：${impact.warnings.join("；")}`)
        return this.$modal.confirm(parts.join("；") + "。确认继续吗？")
          .then(() => true)
          .catch(() => false)
      })
    },
    handleShowRevisions(row) {
      this.revisionScheme = Object.assign({}, row)
      this.revisionOpen = true
      this.loadRevisions(row.schemeId)
    },
    loadRevisions(schemeId) {
      if (!schemeId) return Promise.resolve()
      this.revisionLoading = true
      return listSalaryRevisions(schemeId).then(response => {
        this.revisionList = response.data || []
      }).finally(() => {
        this.revisionLoading = false
      })
    },
    revisionSnapshot(revision) {
      try {
        return JSON.parse(revision.snapshotJson || "{}")
      } catch (error) {
        return {}
      }
    },
    handleRollbackRevision(revision) {
      const snapshot = this.revisionSnapshot(revision)
      const emergencyCorrection = this.isEffectiveDate(snapshot.effectiveDate)
      if (emergencyCorrection && !this.canEmergencyCorrection()) {
        this.$modal.msgError("恢复已生效版本需要薪资紧急修正权限")
        return
      }
      this.$prompt(`将修订 v${revision.version} 恢复为新版本，请填写原因`, "恢复薪资方案版本", {
        confirmButtonText: "预览影响",
        cancelButtonText: "取消",
        inputPlaceholder: "必填，最多500字",
        inputValidator: value => Boolean(value && value.trim()),
        inputErrorMessage: "请填写回滚原因"
      }).then(({ value }) => {
        return this.previewAndConfirm({
          operation: "SCHEME_UPDATE",
          schemeId: this.revisionScheme.schemeId,
          expectedVersion: this.revisionScheme.version,
          effectiveDate: snapshot.effectiveDate
        }, `恢复修订 v${revision.version}`).then(confirmed => {
          if (!confirmed) return false
          return rollbackSalaryRevision(revision.revisionId, {
            expectedVersion: this.revisionScheme.version,
            reason: value.trim(),
            emergencyCorrection
          }).then(() => true)
        })
      }).then(restored => {
        if (!restored) return null
        this.$modal.msgSuccess("已恢复为新版本")
        return Promise.all([this.getSchemeList(), this.refreshSchemeOptions()]).then(() => {
          const latest = this.schemeList.find(item => Number(item.schemeId) === Number(this.revisionScheme.schemeId))
          if (latest) this.revisionScheme = Object.assign({}, latest)
          return this.loadRevisions(this.revisionScheme.schemeId)
        })
      }).catch(() => {})
    },
    initPage() {
      this.loading = true
      Promise.all([
        this.loadSalaryUsers(),
        salarySchemeOptions(),
        salaryShopTree()
      ]).then(([userResponse, schemeResponse, shopResponse]) => {
        this.schemeOptions = schemeResponse.data || []
        this.shopOptions = shopResponse.data || []
        this.ensureDefaultScheme()
        return this.loadAllUserSalary()
      }).finally(() => {
        this.loading = false
      })
    },
    loadSalaryUsers(query) {
      return salaryUserOptions(query || {}).then(response => {
        this.userOptions = response.data || []
        return this.userOptions
      })
    },
    ensureDefaultScheme() {
      if (!this.currentScheme && this.schemeOptions.length) {
        this.formSocialType = this.schemeOptions[0].socialType || SOCIAL_TYPES.noSocial
      }
    },
    loadAllUserSalary() {
      if (!this.userOptions.length) {
        this.userBindings = []
        return Promise.resolve()
      }
      this.loading = true
      const userIds = this.userOptions.map(user => user.userId)
      return getUserSalaryBatch(userIds).then(response => {
        this.userBindings = response.data || []
      }).finally(() => {
        this.loading = false
      })
    },
    handleAdd() {
      this.formSocialType = this.filterSocialType || SOCIAL_TYPES.noSocial
      this.ensureDefaultScheme()
      const scheme = this.schemeChoices[0]
      this.ruleForm = Object.assign(this.defaultRuleForm(), {
        userId: this.userOptions[0] ? this.userOptions[0].userId : undefined,
        shopDeptId: this.defaultShopDeptId(this.userOptions[0]),
        schemeId: scheme ? scheme.schemeId : undefined,
        effectiveDate: scheme ? scheme.effectiveDate : ""
      })
      this.ruleForm.itemId = this.firstBindingItemId()
      this.open = true
    },
    handleEdit(row) {
      this.formSocialType = row.socialType
      this.ruleForm = Object.assign(this.defaultRuleForm(), row, {
        userId: row.userId,
        shopDeptId: row.shopDeptId || this.defaultShopDeptId(this.userMap[row.userId]),
        schemeId: row.schemeId,
        itemId: row.itemId,
        priority: row.priority || 10,
        effectiveDate: row.effectiveDate || ""
      })
      this.open = true
    },
    handleFormUserChange(userId) {
      this.ruleForm.shopDeptId = this.defaultShopDeptId(this.userMap[userId])
    },
    handleFormShopChange() {
      this.loadSalaryUsers({ shopDeptId: this.ruleForm.shopDeptId }).then(users => {
        const exists = users.some(user => Number(user.userId) === Number(this.ruleForm.userId))
        if (!exists) {
          this.ruleForm.userId = users[0] ? users[0].userId : undefined
        }
      })
    },
    handleFormSocialTypeChange() {
      this.ensureDefaultScheme()
      const scheme = this.schemeChoices[0]
      this.ruleForm.schemeId = scheme ? scheme.schemeId : undefined
      this.ruleForm.itemId = this.firstBindingItemId()
    },
    handleFormSchemeChange() {
      this.ruleForm.itemId = this.firstBindingItemId()
    },
    handleSaveRule() {
      this.$refs.ruleForm.validate(valid => {
        if (!valid) {
          return
        }
        this.saving = true
        const userId = Number(this.ruleForm.userId)
        const bindings = this.buildUserRuleBindings()
        this.previewAndConfirm({
          operation: "USER_BINDING",
          userId,
          userBindings: bindings,
          effectiveDate: this.ruleForm.effectiveDate
        }, "保存员工薪资绑定").then(confirmed => {
          if (!confirmed) return null
          return saveUserSalary(userId, bindings).then(() => {
            this.$modal.msgSuccess("员工薪资绑定已保存")
            this.open = false
            return this.loadAllUserSalary()
          })
        }).finally(() => {
          this.saving = false
        })
      })
    },
    buildUserRuleBindings() {
      const userId = Number(this.ruleForm.userId)
      const relationId = Number(this.ruleForm.relationId || 0)
      const bindings = this.userBindings
        .filter(binding => Number(binding.userId) === userId)
        .filter(binding => !relationId || Number(binding.relationId) !== relationId)
        .map(binding => ({
          shopDeptId: binding.shopDeptId,
          schemeId: binding.schemeId,
          itemId: binding.itemId,
          priority: binding.priority || 10,
          effectiveDate: binding.effectiveDate || "",
          endDate: binding.endDate || "",
          status: binding.status || "0",
          remark: binding.remark || ""
        }))
      bindings.push({
        shopDeptId: this.ruleForm.shopDeptId,
        schemeId: this.ruleForm.schemeId,
        itemId: this.ruleForm.itemId,
        priority: this.ruleForm.priority || 10,
        effectiveDate: this.ruleForm.effectiveDate || "",
        endDate: this.ruleForm.endDate || "",
        status: "0",
        remark: this.ruleForm.remark || ""
      })
      return bindings
    },
    handleUnbindRule(row) {
      const bindings = this.userBindings
          .filter(binding => Number(binding.userId) === Number(row.userId))
          .filter(binding => Number(binding.relationId) !== Number(row.relationId))
          .map(binding => ({
            shopDeptId: binding.shopDeptId,
            schemeId: binding.schemeId,
            itemId: binding.itemId,
            priority: binding.priority || 10,
            effectiveDate: binding.effectiveDate || "",
            endDate: binding.endDate || "",
            status: binding.status || "0",
            remark: binding.remark || ""
          }))
      this.previewAndConfirm({
        operation: "USER_BINDING",
        userId: row.userId,
        userBindings: bindings,
        effectiveDate: row.effectiveDate
      }, "移除该员工的薪资绑定").then(confirmed => {
        if (!confirmed) return false
        return saveUserSalary(row.userId, bindings).then(() => true)
      }).then(saved => {
        if (!saved) return null
        this.$modal.msgSuccess("已移除")
        return this.loadAllUserSalary()
      })
    },
    firstBindingItemId() {
      const items = this.currentBindingItems
      return items.length ? items[0].itemId : undefined
    },
    defaultShopDeptId(user) {
      if (!user) {
        return this.shopOptionsFlat[0] ? this.shopOptionsFlat[0].deptId : undefined
      }
      if (user.deptId) {
        return user.deptId
      }
      return this.shopOptionsFlat[0] ? this.shopOptionsFlat[0].deptId : undefined
    },
    formatUserOption(user) {
      if (!user) {
        return ""
      }
      const name = user.nickName || user.userName || "-"
      const account = user.userName ? ` / ${user.userName}` : ""
      const deptName = user.deptName || (user.dept && user.dept.deptName)
      const dept = deptName ? ` / ${deptName}` : ""
      return `${name}${account}${dept}`
    },
    formatItemOption(item) {
      if (!item) {
        return ""
      }
      return [
        item.postName || "-",
        item.gradeName || "-",
        item.regionName || "-",
        this.formatMoney(item.totalSalary)
      ].join(" / ")
    },
    flattenShopOptions(nodes) {
      return (nodes || []).reduce((rows, node) => {
        if (this.isAuthorizableDept(node)) {
          rows.push({
            deptId: node.deptId,
            deptName: node.deptName
          })
        }
        return rows.concat(this.flattenShopOptions(node.children || []))
      }, [])
    },
    isAuthorizableDept(node) {
      return node && (node.deptType === "STORE" || node.deptType === "WAREHOUSE")
    },
    shopNameById(deptId) {
      const shop = this.shopOptionsFlat.find(item => Number(item.deptId) === Number(deptId))
      return shop ? shop.deptName : ""
    },
    formatMoney(value) {
      return Number(value || 0).toLocaleString("zh-CN", {
        minimumFractionDigits: 0,
        maximumFractionDigits: 2
      })
    },
    formatNullableMoney(value) {
      if (value === null || value === undefined || value === "") {
        return "-"
      }
      return this.formatMoney(value)
    },
    formatOptionalMoney(row, prop) {
      const value = Number(row[prop] || 0)
      if (!value) {
        return ""
      }
      return this.formatMoney(value)
    }
  }
}
</script>

<style scoped>
.salary-config-page {
  background: #f5f7fb;
  min-height: calc(100vh - 84px);
}

.content-card {
  background: #fff;
  border: 1px solid #edf2f8;
  border-radius: 8px;
  box-shadow: 0 12px 28px rgba(15, 23, 42, 0.06);
}

.salary-list-card {
  padding: 16px;
}

.salary-tabs {
  background: #fff;
}

.salary-list-head {
  align-items: center;
  display: flex;
  gap: 12px;
  justify-content: space-between;
  margin-bottom: 14px;
}

.salary-actions {
  align-items: center;
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.salary-filter {
  align-items: center;
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}

.salary-filter-input,
.salary-filter-select {
  width: 180px;
}

.salary-selected {
  align-items: center;
  display: flex;
  gap: 8px;
  min-height: 32px;
}

.salary-selected-label {
  color: #909399;
  font-size: 13px;
}

.salary-sub-text {
  color: #909399;
  font-size: 12px;
  line-height: 18px;
}

.salary-preview {
  background: #f8fafc;
  border: 1px solid #edf2f8;
  border-radius: 6px;
  padding: 12px;
}

.salary-preview-title {
  color: #303133;
  font-weight: 600;
  margin-bottom: 10px;
}

.salary-preview-grid {
  display: grid;
  gap: 10px;
  grid-template-columns: repeat(3, minmax(0, 1fr));
}

.salary-preview-item {
  background: #fff;
  border: 1px solid #edf2f8;
  border-radius: 4px;
  min-width: 0;
  padding: 8px 10px;
}

.salary-preview-item span {
  color: #909399;
  display: block;
  font-size: 12px;
  line-height: 18px;
}

.salary-preview-item strong {
  color: #303133;
  display: block;
  font-size: 14px;
  line-height: 22px;
  overflow-wrap: anywhere;
}

.salary-preview-total strong {
  color: #1677ff;
}

.salary-preview-empty {
  color: #909399;
  font-size: 13px;
}

.salary-detail-drawer,
.salary-revision-drawer {
  padding: 0 20px 24px;
}

.salary-revision-drawer .el-alert {
  margin-bottom: 14px;
}

@media (max-width: 680px) {
  .salary-list-head {
    align-items: stretch;
    flex-direction: column;
  }

  .salary-filter,
  .salary-actions,
  .salary-filter-input,
  .salary-filter-select {
    width: 100%;
  }

  .salary-preview-grid {
    grid-template-columns: 1fr;
  }
}
</style>
