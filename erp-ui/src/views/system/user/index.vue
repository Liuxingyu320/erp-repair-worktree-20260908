<template>
  <div class="app-container system-management-page tree-sidebar-manage-wrap">
    <tree-panel
      title="组织机构"
      :tree-data="deptOptions"
      search-placeholder="搜索部门/区域/店铺"
      storage-key="dept-sidebar-width"
      :defaultExpandAll="false"
      :default-expanded-keys="deptDefaultExpandedKeys"
      :filter-method="filterDeptNode"
      @node-click="handleNodeClick"
      @refresh="getDeptTree"
      ref="deptTreeRef"
    />
    <div class="tree-sidebar-content">
      <div class="content-inner">
        <system-page-header
          title="用户管理"
          description="维护登录账号、人员资料、角色配置与组织授权，快速识别待补全账号。"
          icon="el-icon-user"
        />
        <div v-if="managementUxV2Enabled" class="user-setup-summary" aria-label="账号配置概览">
          <button
            v-for="item in setupSummaryCards"
            :key="item.key"
            type="button"
            class="setup-summary-card"
            :class="['is-' + item.type, { 'is-active': activeSetupSummaryKey === item.key }]"
            :aria-pressed="activeSetupSummaryKey === item.key ? 'true' : 'false'"
            @click="applySetupSummaryFilter(item.key)"
          >
            <span class="setup-summary-card__icon" aria-hidden="true">
              <i :class="item.icon" />
            </span>
            <span class="setup-summary-card__copy">
              <span class="setup-summary-card__label">{{ item.label }}</span>
              <small>{{ item.hint }}</small>
            </span>
            <strong>{{ item.value }}</strong>
          </button>
        </div>
        <div v-show="showSearch" class="search-card user-search-card">
          <el-form :model="queryParams" ref="queryForm" size="small" :inline="true" label-width="72px">
            <el-form-item label="登录账号" prop="userName">
              <el-input v-model="queryParams.userName" placeholder="请输入登录账号" clearable style="width: 220px" @keyup.enter.native="handleQuery" />
            </el-form-item>
            <el-form-item label="姓名" prop="nickName">
              <el-input v-model="queryParams.nickName" placeholder="请输入姓名" clearable style="width: 220px" @keyup.enter.native="handleQuery" />
            </el-form-item>
            <el-form-item label="账号状态" prop="status">
              <el-select v-model="queryParams.status" placeholder="账号状态" clearable style="width: 220px">
                <el-option v-for="dict in dict.type.sys_normal_disable" :key="dict.value" :label="dict.label" :value="dict.value" />
              </el-select>
            </el-form-item>
            <template v-if="showAdvancedFilters">
              <el-form-item label="工号" prop="employeeNo">
                <el-input v-model="queryParams.employeeNo" placeholder="请输入工号" clearable style="width: 220px" @keyup.enter.native="handleQuery" />
              </el-form-item>
              <el-form-item label="手机号" prop="phonenumber">
                <el-input v-model="queryParams.phonenumber" placeholder="请输入手机号" clearable style="width: 220px" @keyup.enter.native="handleQuery" />
              </el-form-item>
              <el-form-item label="员工状态" prop="employeeStatus">
                <el-select v-model="queryParams.employeeStatus" placeholder="请选择员工状态" clearable filterable style="width: 220px">
                  <el-option v-for="item in employeeStatusOptions" :key="item" :label="item" :value="item" />
                </el-select>
              </el-form-item>
              <el-form-item label="人员类别" prop="employeeCategory">
                <el-select v-model="queryParams.employeeCategory" placeholder="请选择人员类别" clearable filterable allow-create style="width: 220px">
                  <el-option v-for="item in employeeCategoryOptions" :key="item" :label="item" :value="item" />
                </el-select>
              </el-form-item>
              <el-form-item label="法人单位" prop="legalEntity">
                <el-input v-model="queryParams.legalEntity" placeholder="请输入法人单位" clearable style="width: 220px" @keyup.enter.native="handleQuery" />
              </el-form-item>
              <el-form-item label="工作地" prop="workLocation">
                <el-input v-model="queryParams.workLocation" placeholder="请输入工作所在地" clearable style="width: 220px" @keyup.enter.native="handleQuery" />
              </el-form-item>
              <el-form-item label="配置状态" prop="setupStatus">
                <el-select v-model="queryParams.setupStatus" placeholder="配置状态" clearable style="width: 240px">
                  <el-option label="未分配角色" value="missingRole" />
                  <el-option label="未授权管理范围" value="missingShopScope" />
                  <el-option label="已完成" value="complete" />
                </el-select>
              </el-form-item>
              <el-form-item label="岗位" prop="postId">
                <el-select v-model="queryParams.postId" placeholder="请选择岗位" clearable style="width: 240px">
                  <el-option v-for="item in postOptions" :key="item.postId" :label="item.postName" :value="item.postId" :disabled="item.status == 1" />
                </el-select>
              </el-form-item>
            </template>
            <el-form-item>
              <el-button type="primary" icon="el-icon-search" size="mini" @click="handleQuery">搜索</el-button>
              <el-button icon="el-icon-refresh" size="mini" @click="resetQuery">重置</el-button>
              <el-button v-if="managementUxV2Enabled" type="text" size="mini" :icon="advancedSearchVisible ? 'el-icon-arrow-up' : 'el-icon-arrow-down'" @click="toggleAdvancedFilters">
                {{ advancedSearchVisible ? '收起高级筛选' : '高级筛选' }}
              </el-button>
            </el-form-item>
          </el-form>
        </div>

        <div class="content-card user-toolbar-card">
          <el-row :gutter="10" class="mb8">
            <el-col :span="1.5">
              <el-button
                ref="addUserButton"
                type="primary"
                plain
                icon="el-icon-plus"
                size="mini"
                aria-haspopup="dialog"
                :aria-expanded="open ? 'true' : 'false'"
                @click="handleAdd"
                v-hasPermi="['system:user:add']"
              >新增</el-button>
            </el-col>
            <el-col :span="1.5">
              <el-button type="success" plain icon="el-icon-edit" size="mini" :disabled="single" @click="handleUpdate" v-hasPermi="['system:user:edit']">修改</el-button>
            </el-col>
            <el-col :span="1.5">
              <el-button type="danger" plain icon="el-icon-delete" size="mini" :disabled="multiple" @click="handleDelete" v-hasPermi="['system:user:remove']">删除</el-button>
            </el-col>
            <el-col :span="1.5">
              <el-button type="info" plain icon="el-icon-upload2" size="mini" @click="handleImport" v-hasPermi="['system:user:import']">导入</el-button>
            </el-col>
            <el-col :span="1.5">
              <el-button type="warning" plain icon="el-icon-download" size="mini" @click="handleExport" v-hasPermi="['system:user:export']">导出</el-button>
            </el-col>
            <el-col v-if="$auth && $auth.hasPermiAnd(['system:user:export', 'system:user:pii:export'])" :span="1.5">
              <el-button type="danger" plain icon="el-icon-lock" size="mini" @click="openPiiExport">导出完整个人信息</el-button>
            </el-col>
            <el-col v-if="managementUxV2Enabled" :span="1.5">
              <el-dropdown size="mini" @command="applyColumnPreset">
                <el-button plain size="mini" icon="el-icon-s-grid">列预设<i class="el-icon-arrow-down el-icon--right" /></el-button>
                <el-dropdown-menu slot="dropdown">
                  <el-dropdown-item v-for="preset in columnPresetOptions" :key="preset.key" :command="preset.key">{{ preset.label }}</el-dropdown-item>
                  <el-dropdown-item divided command="restore-default">恢复默认</el-dropdown-item>
                </el-dropdown-menu>
              </el-dropdown>
            </el-col>
            <right-toolbar ref="userRightToolbar" :showSearch.sync="showSearch" @queryTable="refreshUserPage" :columns="columns" storage-key="system-user-columns-v2"></right-toolbar>
          </el-row>
        </div>

        <div class="table-card user-table-card">
          <el-table v-accessible-table="'用户列表'" v-loading="loading" :data="userList" @selection-change="handleSelectionChange">
            <el-table-column type="selection" width="50" align="center" />
            <el-table-column label="用户编号" align="center" key="userId" prop="userId" v-if="columns.userId.visible" width="90" />
            <el-table-column label="登录账号" align="center" key="userName" v-if="columns.userName.visible" fixed="left" :show-overflow-tooltip="true" min-width="160">
              <template slot-scope="scope">
                <div class="user-account-cell">
                  <span class="user-identity-marker" :class="'is-' + userIdentityTone(scope.row)" aria-hidden="true">{{ userIdentityInitial(scope.row) }}</span>
                  <a class="link-type user-account-link" style="cursor:pointer" @click="handleViewData(scope.row)">{{ scope.row.userName }}</a>
                </div>
              </template>
            </el-table-column>
            <el-table-column label="姓名" align="center" key="nickName" prop="nickName" v-if="columns.nickName.visible" fixed="left" :show-overflow-tooltip="true" min-width="110" />
            <el-table-column label="工号" align="center" key="employeeNo" v-if="columns.employeeNo.visible" min-width="110" :show-overflow-tooltip="true">
              <template slot-scope="scope">{{ profileValue(scope.row, 'employeeNo') }}</template>
            </el-table-column>
            <el-table-column label="岗位工号" align="center" key="positionNo" v-if="columns.positionNo.visible" min-width="145" :show-overflow-tooltip="true">
              <template slot-scope="scope">{{ profileValue(scope.row, 'positionNo') }}</template>
            </el-table-column>
            <el-table-column label="所属公司" align="center" key="companyName" v-if="columns.companyName.visible" min-width="140" :show-overflow-tooltip="true">
              <template slot-scope="scope">{{ profileValue(scope.row, 'companyName') }}</template>
            </el-table-column>
            <el-table-column label="主部门" align="center" key="deptName" prop="dept.deptName" v-if="columns.deptName.visible" :show-overflow-tooltip="true" min-width="130" />
            <el-table-column label="4级门店" align="center" key="storeName" v-if="columns.storeName.visible" min-width="130" :show-overflow-tooltip="true">
              <template slot-scope="scope">{{ profileValue(scope.row, 'storeName') }}</template>
            </el-table-column>
            <el-table-column label="岗位" align="center" key="postNames" prop="postNames" v-if="columns.postNames.visible" :show-overflow-tooltip="true" min-width="140" />
            <el-table-column label="职级" align="center" key="jobGrade" v-if="columns.jobGrade.visible" min-width="100" :show-overflow-tooltip="true">
              <template slot-scope="scope">{{ profileValue(scope.row, 'jobGrade') }}</template>
            </el-table-column>
            <el-table-column label="合同类型" align="center" key="contractType" v-if="columns.contractType.visible" min-width="100" :show-overflow-tooltip="true">
              <template slot-scope="scope">{{ profileValue(scope.row, 'contractType') }}</template>
            </el-table-column>
            <el-table-column label="社保类型" align="center" key="socialType" v-if="columns.socialType.visible" min-width="100" :show-overflow-tooltip="true">
              <template slot-scope="scope">{{ profileValue(scope.row, 'socialType') }}</template>
            </el-table-column>
            <el-table-column label="员工状态" align="center" key="employeeStatus" v-if="columns.employeeStatus.visible" width="100">
              <template slot-scope="scope">
                <el-tag v-if="profileRawValue(scope.row, 'employeeStatus')" size="small" :type="profileRawValue(scope.row, 'employeeStatus') === '离职' ? 'danger' : 'success'">{{ profileRawValue(scope.row, 'employeeStatus') }}</el-tag>
                <el-tag v-if="employeeStatusNeedsNormalization(profileRawValue(scope.row, 'employeeStatus'))" size="mini" type="warning">状态待规范</el-tag>
                <span v-else>-</span>
              </template>
            </el-table-column>
            <el-table-column label="人员类别" align="center" key="employeeCategory" v-if="columns.employeeCategory.visible" min-width="100" :show-overflow-tooltip="true">
              <template slot-scope="scope">{{ profileValue(scope.row, 'employeeCategory') }}</template>
            </el-table-column>
            <el-table-column label="管理范围" align="center" key="shopScopeNames" v-if="columns.shopScopeNames.visible" min-width="180" :show-overflow-tooltip="true">
              <template slot-scope="scope">
                <span>{{ scopeNamesText(scope.row) }}</span>
              </template>
            </el-table-column>
            <el-table-column label="手机号" align="center" key="phonenumber" prop="phonenumber" v-if="columns.phonenumber.visible" width="120" />
            <el-table-column label="账号状态" align="center" key="status" v-if="columns.status.visible" fixed="right" width="100">
              <template slot-scope="scope">
                <el-switch
                  v-model="scope.row.status"
                  active-value="0"
                  inactive-value="1"
                  :aria-label="`${scope.row.userName || '用户'}的账号状态`"
                  @change="handleStatusChange(scope.row)"
                ></el-switch>
              </template>
            </el-table-column>
            <el-table-column label="配置状态" align="center" key="setupStatus" v-if="columns.setupStatus.visible" width="150">
              <template slot-scope="scope">
                <el-tag :type="setupStatusType(scope.row)" size="mini">{{ setupStatusLabel(scope.row) }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="操作" align="center" fixed="right" width="160" class-name="small-padding fixed-width">
              <template slot-scope="scope" v-if="scope.row.userId !== 1">
                <el-button class="user-row-action is-edit" size="mini" type="text" icon="el-icon-edit" @click="handleUpdate(scope.row)" v-hasPermi="['system:user:edit']">修改</el-button>
                <el-button class="user-row-action is-delete" size="mini" type="text" icon="el-icon-delete" @click="handleDelete(scope.row)" v-hasPermi="['system:user:remove']">删除</el-button>
                <el-dropdown size="mini" trigger="click" @command="(command) => handleCommand(command, scope.row)" v-hasPermi="['system:user:edit', 'system:user:resetPwd']">
                  <el-button class="user-row-action is-more" size="mini" type="text" icon="el-icon-d-arrow-right">更多</el-button>
                  <el-dropdown-menu slot="dropdown">
                    <el-dropdown-item command="handleResetPwd" icon="el-icon-key" v-hasPermi="['system:user:resetPwd']">生成临时密码</el-dropdown-item>
                    <el-dropdown-item command="handleAuthRole" icon="el-icon-circle-check" v-hasPermi="['system:user:edit']">分配角色</el-dropdown-item>
                    <el-dropdown-item v-if="canNavigateShopScope" command="handleShopScope" icon="el-icon-office-building">组织授权</el-dropdown-item>
                  </el-dropdown-menu>
                </el-dropdown>
              </template>
            </el-table-column>
          </el-table>
          <pagination v-show="total > 0" :total="total" :page.sync="queryParams.pageNum" :limit.sync="queryParams.pageSize" @pagination="getList" />
        </div>
      </div>
    </div>

    <el-dialog title="导出完整个人信息" :visible.sync="piiExportVisible" width="520px" append-to-body>
      <el-alert
        title="这是高敏感数据导出"
        description="文件包含联系方式、证件、住址、紧急联系人及银行卡等信息。系统会记录导出人、原因、筛选范围、行数和数据指纹，但不会记录文件正文。"
        type="warning"
        :closable="false"
        show-icon
      />
      <el-form label-width="100px" class="pii-export-form">
        <el-form-item label="业务原因" required>
          <el-select v-model="piiExportReasonCode" class="form-full-control">
            <el-option v-for="item in piiReasonOptions" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="二次确认">
          <el-checkbox v-model="piiExportConfirmed">我确认本次导出有明确业务需要，并会按敏感文件要求保管和删除</el-checkbox>
        </el-form-item>
      </el-form>
      <div slot="footer" class="dialog-footer">
        <el-button @click="piiExportVisible = false">取 消</el-button>
        <el-button type="danger" :disabled="!piiExportReasonCode || !piiExportConfirmed" @click="handlePiiExport">确认导出</el-button>
      </div>
    </el-dialog>

    <!-- 添加或修改用户配置对话框 -->
    <el-dialog
      ref="userDialog"
      :title="title"
      :visible.sync="open"
      width="980px"
      append-to-body
      :close-on-press-escape="true"
      @opened="handleUserDialogOpened"
      @closed="handleUserDialogClosed"
    >
      <div v-if="piiSaveFailure" class="pii-save-failure">
        <el-alert
          title="账号已创建、敏感身份信息未保存"
          description="账号基础信息和临时密码已生成；请在十分钟补充窗口内重试保存个人信息。"
          type="error"
          :closable="false"
          show-icon
        />
        <el-button size="mini" type="danger" plain @click="retryPiiAfterCreate">重试保存个人信息</el-button>
      </div>
      <el-form ref="form" :model="form" :rules="rules" label-width="118px" class="user-profile-form">
        <el-tabs v-model="activeProfileTab" class="employee-profile-tabs">
          <el-tab-pane label="账号信息" name="account">
            <el-row :gutter="16">
              <el-col :span="12">
                <el-form-item label="姓名" prop="nickName">
                  <el-input ref="userDialogInitialInput" v-model="form.nickName" placeholder="请输入姓名" maxlength="30" autofocus />
                </el-form-item>
              </el-col>
            </el-row>
            <el-row :gutter="16">
              <el-col :span="24">
                <el-form-item v-if="form.userId == undefined" label="登录账号" prop="userName">
                  <el-input v-model="form.userName" placeholder="请输入登录账号" maxlength="30" />
                </el-form-item>
              </el-col>
            </el-row>
            <el-row v-if="canAccessPiiForm" :gutter="16">
              <el-col :span="12">
                <el-form-item label="手机号" prop="phonenumber">
                  <el-input v-model="form.phonenumber" placeholder="请输入手机号" maxlength="11" :disabled="!canEditPiiForCurrent" />
                </el-form-item>
              </el-col>
              <el-col :span="12">
                <el-form-item label="邮箱" prop="email">
                  <el-input v-model="form.email" placeholder="请输入邮箱" maxlength="50" :disabled="!canEditPiiForCurrent" />
                </el-form-item>
              </el-col>
            </el-row>
            <el-row :gutter="16">
              <el-col v-if="canAccessPiiForm" :span="12">
                <el-form-item label="性别">
                  <el-select v-model="form.sex" placeholder="请选择性别" clearable :disabled="!canEditPiiForCurrent">
                    <el-option v-for="dict in dict.type.sys_user_sex" :key="dict.value" :label="dict.label" :value="dict.value"></el-option>
                  </el-select>
                </el-form-item>
              </el-col>
              <el-col :span="canAccessPiiForm ? 12 : 24">
                <el-form-item label="账号状态">
                  <el-radio-group v-model="form.status">
                    <el-radio v-for="dict in dict.type.sys_normal_disable" :key="dict.value" :label="dict.value">{{ dict.label }}</el-radio>
                  </el-radio-group>
                </el-form-item>
              </el-col>
            </el-row>
            <el-row v-if="canAccessPiiForm" :gutter="16">
              <el-col :span="24">
                <el-form-item label="访问原因">
                  <el-select v-model="piiReasonCode" :disabled="piiLoaded" class="form-full-control">
                    <el-option v-for="item in piiReasonOptions" :key="item.value" :label="item.label" :value="item.value" />
                  </el-select>
                  <div class="derived-field-tip">原因码会写入安全审计；不会记录个人信息正文。</div>
                </el-form-item>
              </el-col>
            </el-row>
            <el-row :gutter="16">
              <el-col :span="24">
                <el-form-item label="备注">
                  <el-input v-model="form.remark" type="textarea" placeholder="请输入备注" :rows="3"></el-input>
                </el-form-item>
              </el-col>
            </el-row>
          </el-tab-pane>

          <el-tab-pane v-if="form.userId" label="员工档案（人事管理维护）" name="organization" disabled>
            <el-alert
              v-if="derivedProfileWarning"
              class="derived-profile-alert"
              :title="derivedProfileWarning"
              type="warning"
              :closable="false"
              show-icon
            />
            <el-row :gutter="16">
              <el-col :span="8"><el-form-item label="员工号"><el-input v-model="form.profile.employeeNo" placeholder="入职确认后自动生成" disabled /></el-form-item></el-col>
              <el-col :span="8"><el-form-item label="岗位工号"><el-input v-model="form.profile.positionNo" placeholder="随岗位自动更新" disabled /></el-form-item></el-col>
              <el-col :span="8">
                <el-form-item label="员工状态">
                  <div v-if="employeeStatusNeedsNormalization(form.profile.employeeStatus)" class="employee-status-warning">
                    <el-input :value="form.profile.employeeStatus" disabled />
                    <el-tag size="mini" type="warning">状态待规范</el-tag>
                  </div>
                  <el-select v-else v-model="form.profile.employeeStatus" placeholder="请选择员工状态" clearable filterable @change="handleEmployeeStatusChange">
                    <el-option v-for="item in employeeStatusOptions" :key="item" :label="item" :value="item" />
                  </el-select>
                </el-form-item>
              </el-col>
              <el-col :span="8">
                <el-form-item label="人员类别">
                  <el-select v-model="form.profile.employeeCategory" placeholder="请选择人员类别" clearable filterable allow-create>
                    <el-option v-for="item in employeeCategoryOptions" :key="item" :label="item" :value="item" />
                  </el-select>
                </el-form-item>
              </el-col>
            </el-row>
            <el-row :gutter="16">
              <el-col :span="8"><el-form-item label="所属公司"><el-input v-model="form.profile.companyName" data-derived-field="companyName" :disabled="true" /><div class="derived-field-tip">根据归属部门自动匹配</div></el-form-item></el-col>
              <el-col :span="8"><el-form-item label="1级部门"><el-input v-model="form.profile.deptLevel1Name" data-derived-field="deptLevel1Name" :disabled="true" /><div class="derived-field-tip">根据归属部门自动匹配</div></el-form-item></el-col>
              <el-col :span="8"><el-form-item label="2级部门"><el-input v-model="form.profile.deptLevel2Name" data-derived-field="deptLevel2Name" :disabled="true" /><div class="derived-field-tip">根据归属部门自动匹配</div></el-form-item></el-col>
            </el-row>
            <el-row :gutter="16">
              <el-col :span="8"><el-form-item label="3级部门"><el-input v-model="form.profile.deptLevel3Name" data-derived-field="deptLevel3Name" :disabled="true" /><div class="derived-field-tip">根据归属部门自动匹配</div></el-form-item></el-col>
              <el-col :span="8"><el-form-item label="4级门店"><el-input v-model="form.profile.storeName" data-derived-field="storeName" :disabled="true" /><div class="derived-field-tip">根据归属部门自动匹配</div></el-form-item></el-col>
              <el-col :span="8"><el-form-item label="职位"><el-input v-model="form.profile.positionNames" data-derived-field="positionNames" :disabled="true" /><div class="derived-field-tip">根据已选岗位自动匹配</div></el-form-item></el-col>
            </el-row>
            <el-row :gutter="16">
              <el-col :span="8"><el-form-item label="职级"><el-input v-model="form.profile.jobGrade" placeholder="请输入职级" /></el-form-item></el-col>
              <el-col :span="8"><el-form-item label="部门主管"><el-input v-model="form.profile.departmentSupervisor" data-derived-field="departmentSupervisor" :disabled="true" /><div class="derived-field-tip">根据归属部门自动匹配</div></el-form-item></el-col>
              <el-col :span="8">
                <el-form-item label="直属主管">
                  <supervisor-user-select
                    v-model="form.profile.directSupervisorUserId"
                    :current-user-id="form.userId"
                    :current-label="form.profile.directSupervisor"
                    @change="handleSupervisorChange"
                  />
                  <div class="derived-field-tip">按实际用户选择，避免同名或手工输入错误。</div>
                </el-form-item>
              </el-col>
            </el-row>
            <el-row :gutter="16">
              <el-col :span="12"><el-form-item label="法人单位"><el-input v-model="form.profile.legalEntity" placeholder="请输入法人单位" /></el-form-item></el-col>
            </el-row>
          </el-tab-pane>

          <el-tab-pane v-if="canAccessPiiForm" label="身份学历" name="identity" :class="{ 'pii-readonly': !canEditPiiForCurrent }">
            <el-row :gutter="16">
              <el-col :span="8"><el-form-item label="出生日期"><el-date-picker v-model="form.profile.birthDate" type="date" value-format="yyyy-MM-dd" placeholder="请选择出生日期" class="form-full-control" /></el-form-item></el-col>
              <el-col :span="8"><el-form-item label="证件类型"><el-select v-model="form.profile.idType" placeholder="请选择证件类型" clearable filterable allow-create><el-option v-for="item in idTypeOptions" :key="item" :label="item" :value="item" /></el-select></el-form-item></el-col>
              <el-col :span="8"><el-form-item label="证件号码"><el-input v-model="form.profile.idNumber" placeholder="请输入证件号码" /></el-form-item></el-col>
            </el-row>
            <el-row :gutter="16">
              <el-col :span="8"><el-form-item label="血型"><el-select v-model="form.profile.bloodType" placeholder="请选择血型" clearable filterable allow-create><el-option v-for="item in bloodTypeOptions" :key="item" :label="item" :value="item" /></el-select></el-form-item></el-col>
              <el-col :span="8"><el-form-item label="婚姻状况"><el-select v-model="form.profile.maritalStatus" placeholder="请选择婚姻状况" clearable filterable allow-create><el-option v-for="item in maritalStatusOptions" :key="item" :label="item" :value="item" /></el-select></el-form-item></el-col>
              <el-col :span="8"><el-form-item label="政治面貌"><el-input v-model="form.profile.politicalStatus" placeholder="请输入政治面貌" /></el-form-item></el-col>
            </el-row>
            <el-row :gutter="16">
              <el-col :span="8"><el-form-item label="国籍"><el-input v-model="form.profile.nationality" placeholder="请输入国籍" /></el-form-item></el-col>
              <el-col :span="8"><el-form-item label="是否外籍"><el-select v-model="form.profile.foreignNationalFlag" placeholder="请选择是否外籍" clearable><el-option label="是" value="是" /><el-option label="否" value="否" /></el-select></el-form-item></el-col>
              <el-col :span="8"><el-form-item label="民族"><el-input v-model="form.profile.ethnicity" placeholder="请输入民族" /></el-form-item></el-col>
            </el-row>
            <el-row :gutter="16">
              <el-col :span="8"><el-form-item label="健康状况"><el-input v-model="form.profile.healthStatus" placeholder="请输入健康状况" /></el-form-item></el-col>
              <el-col :span="8"><el-form-item label="户口所在地"><el-input v-model="form.profile.registeredResidence" placeholder="请输入户口所在地" /></el-form-item></el-col>
              <el-col :span="8"><el-form-item label="现居住地址"><el-input v-model="form.profile.currentAddress" placeholder="请输入现居住地址" /></el-form-item></el-col>
            </el-row>
            <el-row :gutter="16">
              <el-col :span="8"><el-form-item label="第一学历"><el-input v-model="form.profile.firstEducation" placeholder="请输入第一学历" /></el-form-item></el-col>
              <el-col :span="8"><el-form-item label="第一学位"><el-input v-model="form.profile.firstDegree" placeholder="请输入第一学位" /></el-form-item></el-col>
              <el-col :span="8"><el-form-item label="毕业时间"><el-date-picker v-model="form.profile.firstGraduationDate" type="date" value-format="yyyy-MM-dd" placeholder="请选择毕业时间" class="form-full-control" /></el-form-item></el-col>
            </el-row>
            <el-row :gutter="16">
              <el-col :span="12"><el-form-item label="第一学历毕业学校"><el-input v-model="form.profile.firstGraduationSchool" placeholder="请输入毕业学校" /></el-form-item></el-col>
              <el-col :span="12"><el-form-item label="第一学历所学专业"><el-input v-model="form.profile.firstMajor" placeholder="请输入所学专业" /></el-form-item></el-col>
            </el-row>
            <el-row :gutter="16">
              <el-col :span="8"><el-form-item label="最高学历"><el-input v-model="form.profile.highestEducation" placeholder="请输入最高学历" /></el-form-item></el-col>
              <el-col :span="8"><el-form-item label="最高学位"><el-input v-model="form.profile.highestDegree" placeholder="请输入最高学位" /></el-form-item></el-col>
              <el-col :span="8"><el-form-item label="最高学历毕业时间"><el-date-picker v-model="form.profile.highestGraduationDate" type="date" value-format="yyyy-MM-dd" placeholder="请选择毕业时间" class="form-full-control" /></el-form-item></el-col>
            </el-row>
            <el-row :gutter="16">
              <el-col :span="12"><el-form-item label="最高学历毕业学校"><el-input v-model="form.profile.highestGraduationSchool" placeholder="请输入毕业学校" /></el-form-item></el-col>
              <el-col :span="12"><el-form-item label="最高学历所学专业"><el-input v-model="form.profile.highestMajor" placeholder="请输入所学专业" /></el-form-item></el-col>
            </el-row>
          </el-tab-pane>

          <el-tab-pane v-if="canAccessPiiForm" label="联系信息" name="contact" :class="{ 'pii-readonly': !canEditPiiForCurrent }">
            <el-row :gutter="16">
              <el-col :span="8"><el-form-item label="紧急联系人"><el-input v-model="form.profile.emergencyContact" placeholder="请输入紧急联系人" /></el-form-item></el-col>
              <el-col :span="8"><el-form-item label="与紧急联系人关系"><el-input v-model="form.profile.emergencyContactRelation" placeholder="请输入关系" /></el-form-item></el-col>
              <el-col :span="8"><el-form-item label="紧急联系人电话"><el-input v-model="form.profile.emergencyContactPhone" placeholder="请输入紧急联系人电话" /></el-form-item></el-col>
            </el-row>
            <el-row :gutter="16">
              <el-col :span="24"><el-form-item label="办公电话"><el-input v-model="form.profile.officePhone" placeholder="请输入办公电话" /></el-form-item></el-col>
            </el-row>
          </el-tab-pane>

          <el-tab-pane v-if="canAccessPiiForm" label="其他敏感信息" name="contract" :class="{ 'pii-readonly': !canEditPiiForCurrent }">
            <el-row v-if="false" :gutter="16">
              <el-col :span="8"><el-form-item label="参加工作时间"><el-date-picker v-model="form.profile.workStartDate" type="date" value-format="yyyy-MM-dd" placeholder="请选择参加工作时间" class="form-full-control" /></el-form-item></el-col>
              <el-col :span="8"><el-form-item label="工龄"><el-input v-model="form.profile.workYears" data-derived-field="workYears" :disabled="true" /><div class="derived-field-tip">根据日期自动计算</div></el-form-item></el-col>
              <el-col :span="8"><el-form-item label="入职时间"><el-date-picker v-model="form.profile.entryDate" type="date" value-format="yyyy-MM-dd" placeholder="请选择入职时间" class="form-full-control" /></el-form-item></el-col>
            </el-row>
            <el-row v-if="false" :gutter="16">
              <el-col :span="8"><el-form-item label="试用期"><el-input v-model="form.profile.probationPeriod" placeholder="请输入试用期" /></el-form-item></el-col>
              <el-col :span="8"><el-form-item label="计划转正日期"><el-date-picker v-model="form.profile.plannedRegularizationDate" type="date" value-format="yyyy-MM-dd" placeholder="请选择计划转正日期" class="form-full-control" /></el-form-item></el-col>
              <el-col :span="8"><el-form-item label="实际转正日期"><el-date-picker v-model="form.profile.actualRegularizationDate" type="date" value-format="yyyy-MM-dd" placeholder="请选择实际转正日期" class="form-full-control" /></el-form-item></el-col>
            </el-row>
            <el-row v-if="false" :gutter="16">
              <el-col :span="8"><el-form-item label="司龄"><el-input v-model="form.profile.companyYears" data-derived-field="companyYears" :disabled="true" /><div class="derived-field-tip">根据日期自动计算</div></el-form-item></el-col>
              <el-col :span="8"><el-form-item label="本岗位任职日期"><el-date-picker v-model="form.profile.currentPositionStartDate" type="date" value-format="yyyy-MM-dd" placeholder="请选择任职日期" class="form-full-control" /></el-form-item></el-col>
              <el-col :span="8"><el-form-item label="现合同起始日"><el-date-picker v-model="form.profile.contractStartDate" type="date" value-format="yyyy-MM-dd" placeholder="请选择合同起始日" class="form-full-control" /></el-form-item></el-col>
            </el-row>
            <el-row v-if="false" :gutter="16">
              <el-col :span="8"><el-form-item label="现合同到期日"><el-date-picker v-model="form.profile.contractEndDate" type="date" value-format="yyyy-MM-dd" placeholder="请选择合同到期日" class="form-full-control" /></el-form-item></el-col>
              <el-col :span="8">
                <el-form-item label="合同类型">
                  <el-select v-model="form.profile.contractType" placeholder="请选择合同类型" clearable>
                    <el-option v-for="item in contractTypeOptions" :key="item.value" :label="item.label" :value="item.value" />
                  </el-select>
                </el-form-item>
              </el-col>
              <el-col :span="8">
                <el-form-item label="合同期限">
                  <el-select v-model="form.profile.contractTerm" placeholder="请选择合同期限" clearable>
                    <el-option v-for="item in contractTermOptions" :key="item.value" :label="item.label" :value="item.value" />
                  </el-select>
                </el-form-item>
              </el-col>
            </el-row>
            <el-row :gutter="16">
              <el-col :span="24"><el-form-item label="工作所在地"><el-input v-model="form.profile.workLocation" placeholder="请输入工作所在地" /></el-form-item></el-col>
            </el-row>
            <el-row :gutter="16">
              <el-col :span="24"><el-form-item label="户口性质"><el-input v-model="form.profile.householdType" placeholder="请输入户口性质" /></el-form-item></el-col>
            </el-row>
            <el-row :gutter="16">
              <el-col :span="8"><el-form-item label="社保缴纳地"><el-input v-model="form.profile.socialSecurityLocation" placeholder="请输入社保缴纳地" /></el-form-item></el-col>
              <el-col :span="8"><el-form-item label="公积金缴纳地"><el-input v-model="form.profile.housingFundLocation" placeholder="请输入公积金缴纳地" /></el-form-item></el-col>
            </el-row>
            <el-row :gutter="16">
              <el-col :span="8"><el-form-item label="开户银行"><el-input v-model="form.profile.bankName" placeholder="请输入开户银行" /></el-form-item></el-col>
              <el-col :span="8"><el-form-item label="银行卡号"><el-input v-model="form.profile.bankAccount" placeholder="请输入银行卡号" /></el-form-item></el-col>
            </el-row>
          </el-tab-pane>

          <el-tab-pane label="确认提交" name="review">
            <user-form-review
              :form="form"
              :original="originalForm"
              :role-options="roleOptions"
              :post-options="postOptions"
              :dept-options="deptOptions"
            />
          </el-tab-pane>
        </el-tabs>
      </el-form>
      <div slot="footer" class="dialog-footer">
        <span class="wizard-footer-hint">第 {{ activeWizardStep + 1 }} / 5 步</span>
        <el-button v-if="activeWizardStep > 0" @click="previousWizardStep">上一步</el-button>
        <el-button v-if="activeWizardStep < 4" type="primary" @click="nextWizardStep">下一步</el-button>
        <el-button v-else type="primary" @click="submitForm">{{ form.userId ? "保存修改" : "创建用户" }}</el-button>
        <el-button @click="cancel">取 消</el-button>
      </div>
    </el-dialog>

    <el-dialog
      :title="isDefaultPasswordCredential ? '新用户默认密码' : '一次性临时密码'"
      :visible.sync="credentialVisible"
      width="520px"
      append-to-body
      :close-on-click-modal="false"
      @closed="handleCredentialClosed"
    >
      <el-alert
        :title="isDefaultPasswordCredential
          ? '新建账号的固定默认密码为 123456。'
          : '此密码只显示本次，24 小时内有效。关闭后无法再次查询，遗失时只能重新生成。'"
        type="warning"
        :closable="false"
        show-icon
      />
      <div v-if="temporaryCredential" class="temporary-credential-card">
        <div><span>登录账号</span><strong>{{ temporaryCredential.userName }}</strong></div>
        <div>
          <span>{{ isDefaultPasswordCredential ? '默认密码' : '临时密码' }}</span>
          <el-input
            :value="temporaryCredential.temporaryPassword"
            type="text"
            readonly
            autocomplete="off"
            :aria-label="isDefaultPasswordCredential ? '新用户默认密码' : '一次性临时密码'"
          >
            <el-button
              slot="append"
              v-clipboard:copy="temporaryCredential.temporaryPassword"
              v-clipboard:success="handleCredentialCopySuccess"
              v-clipboard:error="handleCredentialCopyError"
              icon="el-icon-document-copy"
            >复制密码</el-button>
          </el-input>
        </div>
        <div v-if="!isDefaultPasswordCredential"><span>失效时间</span><strong>{{ temporaryCredential.expiresAt }}</strong></div>
      </div>
      <p v-if="isDefaultPasswordCredential" class="temporary-credential-warning">该密码长期有效，请仅通过受控渠道告知新用户。</p>
      <p v-else class="temporary-credential-warning">请通过受控渠道交付，不要截图或发送到公开群聊；用户登录后必须立即修改密码。</p>
      <div slot="footer" class="dialog-footer">
        <el-button type="primary" @click="credentialVisible = false">我已安全记录</el-button>
      </div>
    </el-dialog>

    <!-- 用户详情抽屉 -->
    <user-view-drawer ref="userViewRef" />
    <!-- 用户导入对话框 -->
    <excel-import-dialog ref="importUserRef" title="用户导入" action="/system/user/importData" template-action="/system/user/importTemplate" template-file-name="用户导入模板" update-support-label="是否更新已经存在的用户数据" @success="getList" />
    <user-filter-drawer
      ref="userFilterDrawer"
      :employee-status-options="employeeStatusOptions"
      :employee-category-options="employeeCategoryOptions"
      :post-options="postOptions"
      @apply="applyAdvancedFilters"
      @clear-saved="clearSavedAdvancedFilters"
    />
  </div>
</template>

<script>
import { listUser, getUserSetupSummary, getUser, getUserPii, updateUserPii, delUser, addUser, updateUser, previewUserDerivedProfile, resetUserPwd, changeUserStatus, deptTreeSelect } from "@/api/system/user"
import Treeselect from "@riophae/vue-treeselect"
import "@riophae/vue-treeselect/dist/vue-treeselect.css"
import TreePanel from "@/components/TreePanel"
import ExcelImportDialog from "@/components/ExcelImportDialog"
import UserViewDrawer from "./view"
import SupervisorUserSelect from "./components/SupervisorUserSelect"
import UserFilterDrawer from "./components/UserFilterDrawer"
import UserFormReview from "./components/UserFormReview"
import { confirmExportAction } from "@/utils/exportConfirm"
import { buildUserPiiPatch, captureUserPiiSnapshot } from "@/utils/userPiiPatch"
import cache from "@/plugins/cache"
import {
  CONTRACT_TERM_OPTIONS,
  CONTRACT_TYPE_OPTIONS,
  SOCIAL_TYPE_OPTIONS,
  signingOptionsForKey,
  signingProfileLabel
} from "@/views/hr/components/signingProfileOptions"

const USER_FILTER_STORAGE_KEY = "system-user-filters-v2"
const USER_COLUMN_STORAGE_KEY = "system-user-columns-v2"
const USER_COLUMN_DEFAULTS = {
  userId: false,
  userName: true,
  nickName: true,
  employeeNo: false,
  positionNo: false,
  companyName: false,
  deptName: true,
  storeName: false,
  postNames: true,
  jobGrade: false,
  contractType: false,
  socialType: false,
  employeeStatus: false,
  employeeCategory: false,
  shopScopeNames: false,
  phonenumber: false,
  status: true,
  setupStatus: true
}
const USER_COLUMN_PRESETS = {
  account: ["userName", "nickName", "deptName", "postNames", "status", "setupStatus"],
  onboarding: ["userName", "nickName", "employeeNo", "companyName", "deptName", "storeName", "postNames", "employeeStatus", "setupStatus"],
  contract: ["userName", "nickName", "deptName", "contractType", "socialType", "status"],
  organization: ["userName", "nickName", "deptName", "shopScopeNames", "status", "setupStatus"]
}

export default {
  name: "User",
  dicts: ['sys_normal_disable', 'sys_user_sex'],
  components: {
    Treeselect,
    TreePanel,
    ExcelImportDialog,
    UserViewDrawer,
    SupervisorUserSelect,
    UserFilterDrawer,
    UserFormReview
  },
  data() {
    return {
      // 遮罩层
      loading: true,
      // 选中数组
      ids: [],
      // 非单个禁用
      single: true,
      // 非多个禁用
      multiple: true,
      // 显示搜索条件
      showSearch: true,
      advancedSearchVisible: false,
      setupSummary: {
        totalCount: 0,
        activeCount: 0,
        disabledCount: 0,
        missingRoleCount: 0,
        missingShopScopeCount: 0,
        completeCount: 0
      },
      // 总条数
      total: 0,
      // 用户表格数据
      userList: null,
      // 弹出层标题
      title: "",
      // 所有部门树选项
      deptOptions: undefined,
      deptDefaultExpandedKeys: [],
      // 过滤掉已禁用部门树选项
      enabledDeptOptions: undefined,
      // 是否显示弹出层
      open: false,
      userDialogTrigger: null,
      // 员工档案标签页
      activeProfileTab: "account",
      credentialVisible: false,
      temporaryCredential: null,
      credentialNextUser: null,
      piiReasonCode: "BUSINESS_PROCESSING",
      piiExportVisible: false,
      piiExportReasonCode: "",
      piiExportConfirmed: false,
      piiReasonOptions: [
        { value: "BUSINESS_PROCESSING", label: "业务办理" },
        { value: "LEGAL_AUDIT", label: "法务 / 审计" },
        { value: "DATA_SUBJECT_REQUEST", label: "本人请求" },
        { value: "DATA_CORRECTION", label: "数据纠错" }
      ],
      piiLoaded: false,
      piiLoadFailed: false,
      piiInitialValues: null,
      piiSaveFailure: false,
      piiRetryUserId: null,
      piiCreatedUser: null,
      // 日期范围
      dateRange: [],
      // 岗位选项
      postOptions: [],
      // 角色选项
      roleOptions: [],
      derivedPreviewTimer: null,
      derivedPreviewSequence: 0,
      userFormSession: 0,
      employeeStatusOptions: Array(),
      employeeStatusOptionsLoaded: false,
      employeeCategoryOptions: ["全职", "兼职", "实习", "劳务", "退休返聘", "外包"],
      contractTypeOptions: CONTRACT_TYPE_OPTIONS,
      contractTermOptions: CONTRACT_TERM_OPTIONS,
      socialTypeOptions: SOCIAL_TYPE_OPTIONS,
      idTypeOptions: ["身份证", "护照", "港澳通行证", "台胞证", "其他"],
      bloodTypeOptions: ["A型", "B型", "AB型", "O型", "其他", "未知"],
      maritalStatusOptions: ["未婚", "已婚", "离异", "丧偶"],
      // 表单参数
      form: {
        profile: {}
      },
      originalForm: null,
      wizardStepErrors: {},
      // 查询参数
      queryParams: {
        pageNum: 1,
        pageSize: 10,
        userName: undefined,
        nickName: undefined,
        employeeNo: undefined,
        phonenumber: undefined,
        employeeStatus: undefined,
        employeeCategory: undefined,
        legalEntity: undefined,
        workLocation: undefined,
        status: undefined,
        setupStatus: undefined,
        postId: undefined,
        deptId: undefined
      },
      // 列信息
      columns: {
        userId: { label: '用户编号', visible: false },
        userName: { label: '登录账号', visible: true },
        nickName: { label: '姓名', visible: true },
        employeeNo: { label: '工号', visible: false },
        positionNo: { label: '岗位工号', visible: false },
        companyName: { label: '所属公司', visible: false },
        deptName: { label: '主部门', visible: true },
        storeName: { label: '4级门店', visible: false },
        postNames: { label: '岗位', visible: true },
        jobGrade: { label: '职级', visible: false },
        contractType: { label: '合同类型', visible: false },
        socialType: { label: '社保类型', visible: false },
        employeeStatus: { label: '员工状态', visible: false },
        employeeCategory: { label: '人员类别', visible: false },
        shopScopeNames: { label: '管理范围', visible: false },
        phonenumber: { label: '手机号', visible: false },
        status: { label: '账号状态', visible: true },
        setupStatus: { label: '配置状态', visible: true }
      },
      // 表单校验
      rules: {
        userName: [
          { required: true, message: "登录账号不能为空", trigger: "blur" },
          { min: 2, max: 20, message: '登录账号长度必须介于 2 和 20 之间', trigger: 'blur' }
        ],
        nickName: [
          { required: true, message: "姓名不能为空", trigger: "blur" }
        ],
        email: [
          {
            type: "email",
            message: "请输入正确的邮箱地址",
            trigger: ["blur", "change"]
          }
        ],
        phonenumber: [
          {
            pattern: /^1[3|4|5|6|7|8|9][0-9]\d{8}$/,
            message: "请输入正确的手机号码",
            trigger: "blur"
          }
        ],
        deptId: [
          { required: true, message: "归属部门不能为空", trigger: "change" }
        ],
        roleIds: [
          { type: "array", required: true, min: 1, message: "至少选择一个角色", trigger: "change" }
        ]
      }
    }
  },
  computed: {
    isDefaultPasswordCredential() {
      return Boolean(this.temporaryCredential && !this.temporaryCredential.expiresAt)
    },
    managementUxV2Enabled() {
      const features = this.$store && this.$store.state && this.$store.state.user
        ? this.$store.state.user.businessFeatures
        : null
      return Boolean(features && features.systemManagementUxV2)
    },
    showAdvancedFilters() {
      return !this.managementUxV2Enabled || this.advancedSearchVisible
    },
    activeSetupSummaryKey() {
      if (this.queryParams.setupStatus) return this.queryParams.setupStatus
      if (String(this.queryParams.status || "") === "1") return "disabled"
      if (!this.queryParams.status) return "all"
      return ""
    },
    setupSummaryCards() {
      return [
        { key: "all", label: "全部账号", value: this.setupSummary.totalCount, hint: `${this.setupSummary.activeCount || 0} 个启用`, type: "primary", icon: "el-icon-user" },
        { key: "disabled", label: "停用账号", value: this.setupSummary.disabledCount, hint: "点击筛选停用状态", type: "info", icon: "el-icon-circle-close" },
        { key: "missingRole", label: "未分配角色", value: this.setupSummary.missingRoleCount, hint: "无法获得业务权限", type: "danger", icon: "el-icon-warning-outline" },
        { key: "missingShopScope", label: "未授权管理范围", value: this.setupSummary.missingShopScopeCount, hint: "尚未配置组织范围", type: "warning", icon: "el-icon-map-location" },
        { key: "complete", label: "配置完成", value: this.setupSummary.completeCount, hint: "角色与范围均具备", type: "success", icon: "el-icon-circle-check" }
      ]
    },
    columnPresetOptions() {
      return [
        { key: "account", label: "账号管理" },
        { key: "onboarding", label: "入职资料" },
        { key: "contract", label: "合同社保" },
        { key: "organization", label: "组织授权" }
      ]
    },
    canAccessPiiForm() {
      if (!this.$auth) return false
      if (!this.form || this.form.userId === undefined || this.form.userId === null) {
        return this.$auth.hasPermiAnd(["system:user:add", "system:user:pii:edit"])
      }
      return this.$auth.hasPermi("system:user:pii:read") || Boolean(this.piiRetryUserId)
    },
    canEditPiiForCurrent() {
      if (!this.$auth) return false
      if (this.piiRetryUserId) return true
      if (!this.form || this.form.userId === undefined || this.form.userId === null) {
        return this.$auth.hasPermiAnd(["system:user:add", "system:user:pii:edit"])
      }
      return this.piiLoaded && this.$auth.hasPermiAnd(["system:user:edit", "system:user:pii:edit"])
    },
    canNavigateShopScope() {
      return Boolean(this.$auth) && this.$auth.hasPermiAnd(["system:userShop:list", "system:userShop:query", "system:userShop:edit"])
    },
    derivedProfileWarning() {
      const warnings = this.form && this.form.profile && this.form.profile.derivedWarnings
      return Array.isArray(warnings) ? warnings.filter(Boolean).join("；") : ""
    },
    activeWizardStep() {
      if (this.activeProfileTab === "account") return 0
      if (this.activeProfileTab === "organization") return 1
      if (this.activeProfileTab === "roles") return 2
      if (this.activeProfileTab === "review") return 4
      return 3
    },
    selectedRoleOptions() {
      const selected = this.form.roleIds || []
      return this.roleOptions.filter(item => selected.some(id => String(id) === String(item.roleId)))
    },
    activeAdvancedFilterCount() {
      return this.advancedFilterKeys().filter(key => {
        const value = this.queryParams[key]
        return value !== undefined && value !== null && value !== ""
      }).length
    }
  },
  watch: {
    "form.deptId"() {
      this.scheduleDerivedPreview()
    },
    "form.postIds": {
      deep: true,
      handler() {
        this.scheduleDerivedPreview()
      }
    },
    "form.profile.workStartDate"() {
      this.scheduleDerivedPreview()
    },
    "form.profile.entryDate"() {
      this.scheduleDerivedPreview()
    },
    "form.profile.leaveDate"() {
      this.scheduleDerivedPreview()
    },
    "form.profile.employeeStatus"() {
      this.scheduleDerivedPreview()
    },
    open(value) {
      if (!value) this.beginUserFormSession()
    }
  },
  created() {
    this.restoreFilterPreferences()
    this.getList()
    this.getDeptTree()
    this.getPostOptions()
    this.loadEmployeeStatusOptions()
  },
  beforeDestroy() {
    this.beginUserFormSession()
    this.removeUserDialogFocusGuard()
  },
  methods: {
    employeeStatusNeedsNormalization(value) {
      return this.employeeStatusOptionsLoaded && Boolean(value) && !this.employeeStatusOptions.some(item => {
        return item === value || (item && item.value === value)
      })
    },
    beginUserFormSession() {
      this.userFormSession += 1
      this.resetDerivedPreviewLifecycle()
      return this.userFormSession
    },
    isCurrentUserFormSession(session) {
      return session === this.userFormSession
    },
    applyUserFormSession(session, apply) {
      if (!this.isCurrentUserFormSession(session) || typeof apply !== "function") return
      apply()
    },
    cloneUserSaveValue(value) {
      return this.cloneFormValue(value)
    },
    captureUserSaveSnapshot() {
      return {
        session: this.userFormSession,
        userId: this.form && this.form.userId,
        reasonCode: this.piiReasonCode,
        basePayload: this.cloneUserSaveValue(this.buildUserManagePayload()) || {},
        piiPayload: this.cloneUserSaveValue(this.buildPiiPayload())
      }
    },
    scheduleDerivedPreview() {
      if (!this.open || !this.form) return
      const session = this.userFormSession
      clearTimeout(this.derivedPreviewTimer)
      this.derivedPreviewTimer = setTimeout(() => {
        if (!this.isCurrentUserFormSession(session)) return
        this.loadDerivedPreview()
      }, 250)
    },
    loadDerivedPreview() {
      if (!this.open || !this.form) return
      const session = this.userFormSession
      const profile = (this.form && this.form.profile) || {}
      const requestSequence = ++this.derivedPreviewSequence
      const payload = {
        deptId: this.form.deptId,
        postIds: this.cloneUserSaveValue(this.form.postIds || []) || [],
        profile: {
          workStartDate: profile.workStartDate,
          entryDate: profile.entryDate,
          leaveDate: profile.leaveDate,
          employeeStatus: profile.employeeStatus
        }
      }
      previewUserDerivedProfile(payload, { silentError: true }).then(response => {
        if (requestSequence !== this.derivedPreviewSequence || !this.isCurrentUserFormSession(session) || !this.open) return
        this.$set(this.form, "profile", { ...((this.form && this.form.profile) || profile), ...(response.data || {}) })
      }).catch(() => {})
    },
    resetDerivedPreviewLifecycle() {
      clearTimeout(this.derivedPreviewTimer)
      this.derivedPreviewTimer = null
      this.derivedPreviewSequence += 1
    },
    emptyProfile() {
      return {
        employeeNo: undefined,
        positionNo: undefined,
        companyName: undefined,
        deptLevel1Name: undefined,
        deptLevel2Name: undefined,
        deptLevel3Name: undefined,
        storeName: undefined,
        positionNames: undefined,
        jobGrade: undefined,
        departmentSupervisor: undefined,
        directSupervisor: undefined,
        directSupervisorUserId: undefined,
        employeeStatus: undefined,
        employeeCategory: undefined,
        birthDate: undefined,
        idType: undefined,
        idNumber: undefined,
        bloodType: undefined,
        registeredResidence: undefined,
        currentAddress: undefined,
        firstEducation: undefined,
        firstDegree: undefined,
        firstGraduationDate: undefined,
        firstGraduationSchool: undefined,
        firstMajor: undefined,
        highestEducation: undefined,
        highestDegree: undefined,
        highestGraduationDate: undefined,
        highestGraduationSchool: undefined,
        highestMajor: undefined,
        politicalStatus: undefined,
        maritalStatus: undefined,
        nationality: undefined,
        foreignNationalFlag: undefined,
        ethnicity: undefined,
        healthStatus: undefined,
        emergencyContact: undefined,
        emergencyContactRelation: undefined,
        emergencyContactPhone: undefined,
        recruitmentChannel: undefined,
        officePhone: undefined,
        workStartDate: undefined,
        workYears: undefined,
        entryDate: undefined,
        probationPeriod: undefined,
        plannedRegularizationDate: undefined,
        actualRegularizationDate: undefined,
        companyYears: undefined,
        currentPositionStartDate: undefined,
        contractStartDate: undefined,
        contractEndDate: undefined,
        contractType: undefined,
        contractTerm: undefined,
        renewalCount: undefined,
        workLocation: undefined,
        workCityLevel: undefined,
        attendanceMethod: undefined,
        householdType: undefined,
        socialType: undefined,
        socialSecurityLocation: undefined,
        housingFundLocation: undefined,
        leaveDate: undefined,
        bankName: undefined,
        bankAccount: undefined,
        legalEntity: undefined
      }
    },
    ensureFormProfile() {
      if (!this.form.profile) {
        this.$set(this.form, "profile", this.emptyProfile())
      } else {
        this.$set(this.form, "profile", { ...this.emptyProfile(), ...this.form.profile })
      }
    },
    profileValue(row, key) {
      const value = this.profileRawValue(row, key)
      const options = signingOptionsForKey(key)
      if (options) return signingProfileLabel(options, value)
      return value === undefined || value === null || value === "" ? "-" : value
    },
    userIdentityInitial(row) {
      const value = String((row && (row.nickName || row.userName)) || "用").trim()
      return Array.from(value)[0] || "用"
    },
    userIdentityTone(row) {
      const signature = String((row && (row.userId || row.userName || row.nickName)) || "user")
      const hash = Array.from(signature).reduce((total, char) => total + char.charCodeAt(0), 0)
      return ["blue", "violet", "sage", "amber", "rose"][hash % 5]
    },
    profileRawValue(row, key) {
      return row && row.profile ? row.profile[key] : undefined
    },
    restoreFilterPreferences() {
      try {
        const saved = cache.local.getJSON(USER_FILTER_STORAGE_KEY)
        if (!saved || typeof saved !== "object") return
        this.advancedSearchVisible = saved.advancedSearchVisible === true
        const allowed = ["status", "employeeStatus", "employeeCategory", "legalEntity", "workLocation", "setupStatus", "postId"]
        allowed.forEach(key => {
          if (Object.prototype.hasOwnProperty.call(saved, key)) this.queryParams[key] = saved[key]
        })
      } catch (error) {}
    },
    saveFilterPreferences() {
      if (!this.managementUxV2Enabled) return
      const state = { advancedSearchVisible: this.advancedSearchVisible }
      ;["status", "employeeStatus", "employeeCategory", "legalEntity", "workLocation", "setupStatus", "postId"].forEach(key => {
        state[key] = this.queryParams[key]
      })
      cache.local.setJSON(USER_FILTER_STORAGE_KEY, state)
    },
    toggleAdvancedFilters() {
      this.advancedSearchVisible = !this.advancedSearchVisible
      this.saveFilterPreferences()
    },
    applySetupSummaryFilter(key) {
      const mapping = {
        all: { status: undefined, setupStatus: undefined },
        disabled: { status: "1", setupStatus: undefined },
        missingRole: { status: undefined, setupStatus: "missingRole" },
        missingShopScope: { status: undefined, setupStatus: "missingShopScope" },
        complete: { status: undefined, setupStatus: "complete" }
      }
      const filter = mapping[key]
      if (!filter) return
      this.queryParams.status = filter.status
      this.queryParams.setupStatus = filter.setupStatus
      if (filter.setupStatus) this.advancedSearchVisible = true
      this.handleQuery()
    },
    applyColumnPreset(presetKey) {
      const visible = presetKey === "restore-default" ? USER_COLUMN_DEFAULTS : null
      const preset = USER_COLUMN_PRESETS[presetKey]
      if (!visible && !preset) return
      Object.keys(this.columns).forEach(key => {
        this.columns[key].visible = visible ? visible[key] === true : preset.includes(key)
      })
      if (presetKey === "restore-default") {
        cache.local.remove(USER_COLUMN_STORAGE_KEY)
        return
      }
      this.$nextTick(() => {
        if (this.$refs.userRightToolbar) this.$refs.userRightToolbar.saveStorage()
      })
    },
    refreshUserPage() {
      this.getList()
    },
    loadSetupSummary() {
      if (!this.managementUxV2Enabled) return Promise.resolve()
      const query = { ...this.queryParams, pageNum: undefined, pageSize: undefined, status: undefined, setupStatus: undefined }
      return getUserSetupSummary(this.addDateRange(query, this.dateRange)).then(response => {
        this.setupSummary = { ...this.setupSummary, ...(response.data || {}) }
      }).catch(() => {})
    },
    /** 查询用户列表 */
    getList() {
      this.loading = true
      listUser(this.addDateRange(this.queryParams, this.dateRange)).then(response => {
        this.userList = response.rows
        this.total = response.total
        this.loadSetupSummary()
      }).finally(() => {
        this.loading = false
      })
    },
    /** 查询部门下拉树结构 */
    getDeptTree() {
      deptTreeSelect().then(response => {
        const deptTree = this.decorateDeptTree(response.data || [])
        this.deptOptions = deptTree
        this.deptDefaultExpandedKeys = this.getDefaultExpandedDeptKeys(deptTree)
        this.enabledDeptOptions = this.filterDisabledDept(JSON.parse(JSON.stringify(deptTree)))
      })
    },
    decorateDeptTree(deptList, parentPath) {
      const pathPrefix = parentPath || []
      return (deptList || []).map(dept => {
        const currentPath = pathPrefix.concat(dept.label || "")
        const children = this.decorateDeptTree(dept.children || [], currentPath)
        return Object.assign({}, dept, {
          deptPath: currentPath.filter(Boolean).join(" / "),
          deptSearchText: currentPath.filter(Boolean).join(" ").toLowerCase(),
          children
        })
      })
    },
    getDefaultExpandedDeptKeys(deptList, depth) {
      const currentDepth = depth || 1
      const keys = []
      ;(deptList || []).forEach(dept => {
        if (dept.children && dept.children.length && currentDepth <= 3) {
          keys.push(dept.id)
          keys.push(...this.getDefaultExpandedDeptKeys(dept.children, currentDepth + 1))
        }
      })
      return keys
    },
    filterDeptNode(value, data) {
      if (!value) return true
      const keywords = String(value).trim().toLowerCase().split(/\s+/).filter(Boolean)
      if (!keywords.length) return true
      const text = data && data.deptSearchText
        ? data.deptSearchText
        : String(data && data.label ? data.label : "").toLowerCase()
      return keywords.every(keyword => text.indexOf(keyword) !== -1)
    },
    getPostOptions() {
      getUser().then(response => {
        this.postOptions = response.posts || []
        const statuses = Array.isArray(response.employeeStatusOptions)
          ? response.employeeStatusOptions
          : []
        this.employeeStatusOptions = Array.from(new Set(statuses
          .filter(value => value !== undefined && value !== null && String(value).trim())
          .map(String)))
        this.employeeStatusOptionsLoaded = this.employeeStatusOptions.length > 0
      })
    },
    // 过滤禁用的部门
    filterDisabledDept(deptList) {
      return deptList.filter(dept => {
        if (dept.disabled) {
          return false
        }
        if (dept.children && dept.children.length) {
          dept.children = this.filterDisabledDept(dept.children)
        }
        return true
      })
    },
    // 节点单击事件
    handleNodeClick(data) {
      this.queryParams.deptId = data.id
      this.handleQuery()
    },
    // 用户状态修改
    handleStatusChange(row) {
      let text = row.status === "0" ? "启用" : "停用"
      this.$modal.confirm('确认要将用户「' + row.userName + '」' + text + '吗？').then(function() {
        return changeUserStatus(row.userId, row.status)
      }).then(() => {
        this.$modal.msgSuccess(text + "成功")
      }).catch(function() {
        row.status = row.status === "0" ? "1" : "0"
      })
    },
	    setupStatusLabel(row) {
	      if (this.hasUnknownSetupStatus(row)) return "配置状态待同步"
	      if (!row || row.setupStatus === "complete") return "已完成"
	      if (row.setupStatus === "missingRole" || Number(row.roleCount || 0) <= 0) return "未分配角色"
	      if (row.setupStatus === "missingShopScope" || Number(row.shopScopeCount || 0) <= 0) return "未授权管理范围"
	      return "已完成"
	    },
	    scopeNamesText(row) {
	      return row && row.shopScopeNames ? row.shopScopeNames : "-"
	    },
    setupStatusType(row) {
      if (this.hasUnknownSetupStatus(row)) return "info"
      if (!row || row.setupStatus === "complete") return "success"
      if (row.setupStatus === "missingRole") return "danger"
      if (row.setupStatus === "missingShopScope") return "warning"
      return "info"
    },
    hasUnknownSetupStatus(row) {
      return !!row &&
        row.setupStatus === undefined &&
        row.roleCount === undefined &&
        row.shopScopeCount === undefined
    },
    captureUserDialogTrigger(fallbackRef) {
      if (typeof document === "undefined") return
      const active = document.activeElement
      const fallback = this.$refs[fallbackRef]
      const fallbackElement = fallback && fallback.$el ? fallback.$el : fallback
      this.userDialogTrigger = active && active !== document.body
        ? active
        : fallbackElement || null
    },
    getUserDialogElement() {
      const dialog = this.$refs.userDialog
      return dialog && dialog.$el ? dialog.$el.querySelector(".el-dialog") : null
    },
    getUserDialogFocusables() {
      const dialog = this.getUserDialogElement()
      if (!dialog || typeof window === "undefined") return []
      const selector = [
        "a[href]:not([tabindex='-1'])",
        "button:not([disabled]):not([tabindex='-1'])",
        "input:not([disabled]):not([type='hidden']):not([tabindex='-1'])",
        "select:not([disabled]):not([tabindex='-1'])",
        "textarea:not([disabled]):not([tabindex='-1'])",
        "[contenteditable='true']",
        "[tabindex]:not([tabindex='-1'])"
      ].join(",")
      return Array.from(dialog.querySelectorAll(selector)).filter(element => {
        if (element.closest("[aria-hidden='true']")) return false
        const style = window.getComputedStyle(element)
        const rect = element.getBoundingClientRect()
        return style.display !== "none" &&
          style.visibility !== "hidden" &&
          rect.width > 0 &&
          rect.height > 0
      })
    },
    handleUserDialogOpened() {
      const dialog = this.getUserDialogElement()
      if (dialog) dialog.setAttribute("aria-modal", "true")
      this.addUserDialogFocusGuard()
      this.$nextTick(() => {
        setTimeout(() => {
          const input = this.$refs.userDialogInitialInput
          const inputElement = input && input.$el ? input.$el.querySelector("input") : null
          const focusables = this.getUserDialogFocusables()
          const target = inputElement && focusables.includes(inputElement) ? inputElement : focusables[0]
          if (target) target.focus()
        }, 0)
      })
    },
    handleUserDialogDocumentKeydown(event) {
      if (!this.open || event.key !== "Tab") return
      const dialog = this.getUserDialogElement()
      if (!dialog) return
      const focusables = this.getUserDialogFocusables()
      if (!focusables.length) {
        event.preventDefault()
        dialog.setAttribute("tabindex", "-1")
        dialog.focus()
        return
      }
      const first = focusables[0]
      const last = focusables[focusables.length - 1]
      const active = document.activeElement
      if (!dialog.contains(active)) {
        event.preventDefault()
        ;(event.shiftKey ? last : first).focus()
      } else if (event.shiftKey && active === first) {
        event.preventDefault()
        last.focus()
      } else if (!event.shiftKey && active === last) {
        event.preventDefault()
        first.focus()
      }
    },
    addUserDialogFocusGuard() {
      if (typeof document === "undefined") return
      document.removeEventListener("keydown", this.handleUserDialogDocumentKeydown, true)
      document.addEventListener("keydown", this.handleUserDialogDocumentKeydown, true)
    },
    removeUserDialogFocusGuard() {
      if (typeof document === "undefined") return
      document.removeEventListener("keydown", this.handleUserDialogDocumentKeydown, true)
    },
    handleUserDialogClosed() {
      this.removeUserDialogFocusGuard()
      const trigger = this.userDialogTrigger
      this.userDialogTrigger = null
      this.$nextTick(() => {
        setTimeout(() => {
          if (this.open || this.credentialVisible || this.piiExportVisible) return
          if (trigger && document.contains(trigger) && !trigger.disabled) {
            trigger.focus()
          }
        }, 0)
      })
    },
    // 取消按钮
    cancel() {
      this.open = false
      this.reset()
    },
    // 表单重置
    reset() {
      this.beginUserFormSession()
      this.credentialVisible = false
      this.temporaryCredential = null
      this.credentialNextUser = null
      this.activeProfileTab = "account"
      this.piiReasonCode = "BUSINESS_PROCESSING"
      this.piiLoaded = false
      this.piiLoadFailed = false
      this.piiInitialValues = null
      this.piiSaveFailure = false
      this.piiRetryUserId = null
      this.piiCreatedUser = null
      this.form = {
        userId: undefined,
        deptId: undefined,
        userName: undefined,
        nickName: undefined,
        phonenumber: undefined,
        email: undefined,
        sex: undefined,
        status: "0",
        remark: undefined,
        profile: this.emptyProfile(),
        postIds: [],
        roleIds: []
      }
      this.resetForm("form")
    },
    /** 搜索按钮操作 */
    handleQuery() {
      this.queryParams.pageNum = 1
      this.saveFilterPreferences()
      this.getList()
    },
    /** 重置按钮操作 */
    resetQuery() {
      this.dateRange = []
      this.resetForm("queryForm")
      this.queryParams.deptId = undefined
      this.advancedSearchVisible = false
      cache.local.remove(USER_FILTER_STORAGE_KEY)
      this.$refs.deptTreeRef.setCurrentKey(null)
      this.handleQuery()
    },
    // 多选框选中数据
    handleSelectionChange(selection) {
      this.ids = selection.map(item => item.userId)
      this.single = selection.length != 1
      this.multiple = !selection.length
    },
    // 更多操作触发
    handleCommand(command, row) {
      switch (command) {
        case "handleResetPwd":
          this.handleResetPwd(row)
          break
        case "handleAuthRole":
          this.handleAuthRole(row)
          break
        case "handleShopScope":
          this.handleShopScope(row)
          break
        default:
          break
      }
    },
    /** 新增按钮操作 */
    handleAdd() {
      this.captureUserDialogTrigger("addUserButton")
      this.reset()
      const session = this.userFormSession
      getUser(undefined, { silentError: true }).then(response => {
        this.applyUserFormSession(session, () => {
          this.postOptions = response.posts
          this.roleOptions = response.roles
          this.open = true
          this.title = "添加用户"
          this.scheduleDerivedPreview()
        })
      }).catch(() => {
        this.applyUserFormSession(session, () => {
          this.$modal.msgError("用户资料加载失败，请重试")
        })
      })
    },
    /** 修改按钮操作 */
    handleUpdate(row) {
      this.captureUserDialogTrigger()
      this.reset()
      const session = this.userFormSession
      const userId = row.userId || this.ids
      const reasonCode = this.piiReasonCode
      getUser(userId, { silentError: true }).then(response => {
        if (!this.isCurrentUserFormSession(session)) return
        this.form = response.data
        this.ensureFormProfile()
        this.postOptions = response.posts
        this.roleOptions = response.roles
        this.$set(this.form, "postIds", response.postIds)
        this.$set(this.form, "roleIds", response.roleIds)
        const loadPii = this.$auth && this.$auth.hasPermi("system:user:pii:read")
          ? getUserPii(userId, reasonCode, { silentError: true }).then(piiResponse => {
            this.applyUserFormSession(session, () => {
              this.applyPiiToForm(piiResponse.data || {})
              this.piiInitialValues = captureUserPiiSnapshot(this.form)
              this.piiLoaded = true
            })
          }).catch(() => {
            this.applyUserFormSession(session, () => {
              this.piiLoadFailed = true
              this.$modal.msgWarning("个人信息加载失败，当前仅可修改账号基础信息")
            })
          })
          : Promise.resolve()
        return loadPii.then(() => {
          this.applyUserFormSession(session, () => {
            this.open = true
            this.title = "修改用户"
            this.scheduleDerivedPreview()
          })
        })
      }).catch(() => {
        this.applyUserFormSession(session, () => {
          this.$modal.msgError("用户资料加载失败，请重试")
        })
      })
    },
    /** 重置密码按钮操作 */
    handleResetPwd(row) {
      this.$confirm(`将把「${row.userName}」的临时密码设为 123456，旧密码及现有会话会立即失效。是否继续？`, "生成临时密码", {
        confirmButtonText: "确定",
        cancelButtonText: "取消",
        closeOnClickModal: false,
        type: "warning"
      }).then(() => {
        resetUserPwd(row.userId).then(response => {
          this.showTemporaryCredential(response)
        })
      }).catch(() => {})
    },
    /** 分配角色操作 */
    handleAuthRole(row) {
      const userId = row.userId
      this.$router.push("/system/user-auth/role/" + userId)
    },
    /** 组织授权操作 */
    handleShopScope(row) {
      if (!row) return
      this.$router.push({
        path: "/system/shop",
        query: {
          userId: row.userId,
          userName: row.userName
        }
      }).catch(() => {})
    },
    confirmShopScopeAfterCreate(user) {
      if (!this.canNavigateShopScope) {
        return
      }
      this.$confirm("新增成功。是否现在配置该用户可管理的公司、组织、门店或仓库？", "下一步：组织授权", {
        confirmButtonText: "去配置",
        cancelButtonText: "稍后",
        type: "warning"
      }).then(() => {
        this.handleShopScope(user)
      }).catch(() => {})
    },
    /** 提交按钮 */
    submitForm() {
      if (this.piiRetryUserId) {
        this.retryPiiAfterCreate()
        return
      }
      const session = this.userFormSession
      this.$refs["form"].validate(valid => {
        if (!valid || !this.isCurrentUserFormSession(session)) return
        const snapshot = this.captureUserSaveSnapshot()
        if (snapshot.userId === undefined && this.managementUxV2Enabled) {
          this.confirmNewUserSetup(snapshot.basePayload).then(() => {
            if (!this.isCurrentUserFormSession(session)) return
            return this.persistUserForm(snapshot)
          }).catch(() => {})
          return
        }
        this.persistUserForm(snapshot).catch(() => {})
      })
    },
    confirmNewUserSetup(payload) {
      const dept = this.findDeptOption(this.enabledDeptOptions || [], payload.deptId)
      const postNames = this.selectedOptionNames(this.postOptions, payload.postIds, "postId", "postName")
      const roleNames = this.selectedOptionNames(this.roleOptions, payload.roleIds, "roleId", "roleName")
      const summaryRows = [
        `登录账号：${payload.userName || "未填写"}`,
        `姓名：${payload.nickName || "未填写"}`,
        `主部门：${dept ? (dept.label || dept.deptName) : "未选择"}`,
        `岗位：${postNames || "未分配"}`,
        `角色：${roleNames || "未分配"}`,
        `组织授权：账号创建后单独配置${this.canNavigateShopScope ? "（可立即前往）" : ""}`
      ]
      const summary = this.$createElement("div", { class: "new-user-setup-summary" },
        summaryRows.map(row => this.$createElement("div", row)))
      return this.$confirm(summary, "确认新增用户", {
        confirmButtonText: "创建用户",
        cancelButtonText: "返回检查",
        closeOnClickModal: false,
        type: roleNames ? "warning" : "error"
      })
    },
    findDeptOption(nodes, deptId) {
      for (const node of nodes || []) {
        if (String(node.id) === String(deptId)) return node
        const matched = this.findDeptOption(node.children || [], deptId)
        if (matched) return matched
      }
      return null
    },
    selectedOptionNames(options, ids, idKey, nameKey) {
      const selected = new Set((ids || []).map(String))
      return (options || []).filter(item => selected.has(String(item[idKey]))).map(item => item[nameKey]).filter(Boolean).join("、")
    },
    persistUserForm(snapshot) {
      const session = snapshot.session
      const basePayload = this.cloneUserSaveValue(snapshot.basePayload) || {}
      const piiPayload = this.cloneUserSaveValue(snapshot.piiPayload)
      const reasonCode = snapshot.reasonCode
      const targetUserId = snapshot.userId
      if (targetUserId != undefined) {
        return updateUser(basePayload, { silentError: true }).then(() => {
          if (!piiPayload) return null
          return updateUserPii(targetUserId, piiPayload, reasonCode, false, { silentError: true })
            .catch(error => {
              this.applyUserFormSession(session, () => {
                this.$modal.msgError("账号基础信息已保存、PII 未保存，请重试")
              })
              throw error
            })
        }, error => {
          this.applyUserFormSession(session, () => {
            this.$modal.msgError("账号基础信息保存失败，请重试")
          })
          throw error
        }).then(() => {
          this.applyUserFormSession(session, () => {
            this.$modal.msgSuccess("修改成功")
            this.open = false
            this.getList()
          })
        })
      }
      const createdUser = Object.assign({}, basePayload)
      return addUser(basePayload, { silentError: true }).then(response => {
        const credential = response && response.data && response.data.temporaryCredential
        const userId = credential && credential.userId
        const finish = () => {
          this.applyUserFormSession(session, () => {
            this.open = false
            this.getList()
            this.showTemporaryCredential(response, createdUser)
          })
        }
        if (!piiPayload || !userId) {
          finish()
          return
        }
        return updateUserPii(userId, piiPayload, reasonCode, true, { silentError: true })
          .then(finish)
          .catch(() => {
            this.applyUserFormSession(session, () => {
              this.piiSaveFailure = true
              this.piiRetryUserId = userId
              this.piiCreatedUser = Object.assign({}, createdUser, { userId })
              this.$set(this.form, "userId", userId)
              this.title = "账号已创建、PII 未保存"
              this.getList()
              this.showTemporaryCredential(response, null)
              this.$modal.msgError("账号已创建、PII 未保存，请点击重试保存个人信息")
            })
          })
      }, error => {
        this.applyUserFormSession(session, () => {
          this.$modal.msgError("账号创建失败，请重试")
        })
        throw error
      })
    },
    buildUserManagePayload() {
      const fields = ["userId", "deptId", "userName", "nickName", "status", "remark", "postIds", "roleIds"]
      return fields.reduce((payload, field) => {
        if (this.form[field] !== undefined) payload[field] = this.form[field]
        return payload
      }, {})
    },
    buildPiiPayload() {
      if (!this.canEditPiiForCurrent) return null
      const fullSnapshot = Boolean(this.piiRetryUserId) || this.form.userId === undefined || this.form.userId === null
      return buildUserPiiPatch(this.form, this.piiInitialValues, { fullSnapshot })
    },
    applyPiiToForm(pii) {
      ;["email", "phonenumber", "sex"].forEach(field => {
        this.$set(this.form, field, pii[field])
      })
      const profile = { ...(this.form.profile || {}) }
      const baseFields = new Set(["userId", "userName", "nickName", "email", "phonenumber", "sex"])
      Object.keys(pii || {}).forEach(field => {
        if (!baseFields.has(field)) profile[field] = pii[field]
      })
      this.$set(this.form, "profile", profile)
    },
    retryPiiAfterCreate() {
      const session = this.userFormSession
      const userId = this.piiRetryUserId
      const reasonCode = this.piiReasonCode
      const payload = this.cloneUserSaveValue(this.buildPiiPayload())
      const createdUser = this.cloneUserSaveValue(this.piiCreatedUser)
      if (!payload || !userId) return
      updateUserPii(userId, payload, reasonCode, true, { silentError: true }).then(() => {
        this.applyUserFormSession(session, () => {
          this.piiSaveFailure = false
          this.piiRetryUserId = null
          this.open = false
          this.getList()
          this.$modal.msgSuccess("个人信息保存成功")
          if (createdUser) this.confirmShopScopeAfterCreate(createdUser)
        })
      }).catch(() => {
        this.applyUserFormSession(session, () => {
          this.$modal.msgError("PII 仍未保存，请检查字段或联系具备用户修改权限的管理员")
        })
      })
    },
    buildUserChangeRequest() {
      const original = this.originalForm || {}
      const changes = { userId: this.form.userId, profile: {} }
      const changedFields = []
      const rootFields = ["nickName", "deptId", "phonenumber", "email", "sex", "status", "remark", "postIds", "roleIds"]
      rootFields.forEach(field => {
        if (!this.sameUserFormValue(field, original[field], this.form[field])) {
          changedFields.push(field)
          changes[field] = this.cloneFormValue(this.form[field])
        }
      })
      const originalProfile = original.profile || {}
      const currentProfile = this.form.profile || {}
      this.editableProfileFields().forEach(field => {
        if (!this.sameUserFormValue(field, originalProfile[field], currentProfile[field])) {
          changedFields.push(`profile.${field}`)
          changes.profile[field] = this.cloneFormValue(currentProfile[field])
        }
      })
      if (!Object.keys(changes.profile).length) delete changes.profile
      return { userId: this.form.userId, changedFields, changes }
    },
    editableProfileFields() {
      const derivedOrServerFields = new Set([
        "companyName", "deptLevel1Name", "deptLevel2Name", "deptLevel3Name", "storeName",
        "positionNames", "departmentSupervisor", "directSupervisor", "workYears", "companyYears", "contractTerm"
      ])
      return Object.keys(this.emptyProfile()).filter(field => !derivedOrServerFields.has(field))
    },
    sameUserFormValue(field, left, right) {
      if (field === "postIds" || field === "roleIds") {
        const normalize = value => (value || []).map(String).sort()
        return JSON.stringify(normalize(left)) === JSON.stringify(normalize(right))
      }
      const normalize = value => value === "" || value === null ? undefined : value
      return JSON.stringify(normalize(left)) === JSON.stringify(normalize(right))
    },
    cloneFormValue(value) {
      return value === undefined ? null : JSON.parse(JSON.stringify(value))
    },
    /** 删除按钮操作 */
    handleDelete(row) {
      const userIds = row.userId || this.ids
      this.$modal.confirm(this.getUserDeleteConfirmText(row)).then(function() {
        return delUser(userIds)
      }).then(() => {
        this.getList()
        this.$modal.msgSuccess("删除成功")
      }).catch(() => {})
    },
    getUserDeleteConfirmText(row) {
      if (row && row.userId) {
        return `确认删除用户「${row.userName || row.nickName || row.userId}」吗？`
      }
      return `确认删除选中的 ${this.ids.length} 个用户吗？`
    },
    /** 导出按钮操作 */
    handleExport() {
      confirmExportAction(this, {
        moduleName: "用户数据",
        rangeLabel: "当前查询条件下的用户数据",
        filterLabel: this.buildManagementExportFilterLabel("登录账号", this.queryParams.userName)
      }).then(() => {
        this.download(
          'system/user/export',
          this.addDateRange({ ...this.queryParams }, this.dateRange),
          this.exportFileName('用户数据', this.dateRange)
        )
      })
    },
    openPiiExport() {
      if (!this.$auth || !this.$auth.hasPermiAnd(["system:user:export", "system:user:pii:export"])) {
        this.$modal.msgError("缺少完整个人信息导出权限")
        return
      }
      this.piiExportReasonCode = ""
      this.piiExportConfirmed = false
      this.piiExportVisible = true
    },
    handlePiiExport() {
      if (!this.piiExportReasonCode || !this.piiExportConfirmed) {
        this.$modal.msgWarning("请选择业务原因并完成二次确认")
        return
      }
      const params = this.addDateRange({
        ...this.queryParams,
        reasonCode: this.piiExportReasonCode,
        confirmed: true
      }, this.dateRange)
      this.piiExportVisible = false
      this.download(
        "system/user/export-sensitive",
        params,
        this.exportFileName("用户个人敏感信息", this.dateRange)
      )
    },
    buildManagementExportFilterLabel(primaryLabel, primaryValue) {
      const range = this.dateRange && this.dateRange.length === 2 ? this.dateRange.join(" 至 ") : "全部"
      return `${primaryLabel}：${primaryValue || "全部"}；日期：${range}`
    },
    /** 详情按钮操作 */
    handleViewData(row) {
      this.$refs.userViewRef.open(row.userId)
    },
    handleEmployeeStatusChange(value) {
      if (value === "离职") {
        this.form.status = "1"
      }
    },
    handleSupervisorChange(option) {
      this.ensureFormProfile()
      this.$set(this.form.profile, "directSupervisor", option ? (option.nickName || option.userName) : undefined)
    },
    roleDataScopeLabel(value) {
      return {
        "1": "全部数据",
        "2": "自定义部门",
        "3": "本部门",
        "4": "本部门及以下",
        "5": "仅本人"
      }[value] || "未设置"
    },
    goWizardStep(step) {
      const target = Math.max(0, Math.min(4, step))
      this.activeProfileTab = ["account", "organization", "roles", "identity", "review"][target]
    },
    previousWizardStep() {
      this.goWizardStep(this.activeWizardStep - 1)
    },
    nextWizardStep() {
      this.validateWizardStep(this.activeWizardStep).then(valid => {
        if (valid) this.goWizardStep(this.activeWizardStep + 1)
      })
    },
    validateWizardStep(step) {
      const fieldsByStep = {
        0: ["nickName", ...(this.form.userId ? [] : ["userName", "password"]), "phonenumber", "email"],
        1: ["deptId"],
        2: ["roleIds"],
        3: []
      }
      const fields = fieldsByStep[step] || []
      if (!fields.length) {
        this.$set(this.wizardStepErrors, step, false)
        return Promise.resolve(true)
      }
      return Promise.all(fields.map(field => new Promise(resolve => {
        this.$refs.form.validateField(field, message => resolve(!message))
      }))).then(results => {
        const valid = results.every(Boolean)
        this.$set(this.wizardStepErrors, step, !valid)
        return valid
      })
    },
    jumpToFirstInvalidField(invalidFields) {
      const stepByField = {
        nickName: 0,
        userName: 0,
        password: 0,
        phonenumber: 0,
        email: 0,
        deptId: 1,
        roleIds: 2
      }
      const field = Object.keys(invalidFields || {})[0]
      const step = stepByField[field] === undefined ? 3 : stepByField[field]
      this.$set(this.wizardStepErrors, step, true)
      this.goWizardStep(step)
      this.$nextTick(() => this.$message.warning("请先修正当前步骤中的必填项或格式错误"))
    },
    wizardStepStatus(step) {
      if (this.wizardStepErrors[step]) return "error"
      if (step < this.activeWizardStep) return "success"
      if (step === this.activeWizardStep) return "process"
      return "wait"
    },
    advancedFilterKeys() {
      return ["employeeNo", "phonenumber", "employeeStatus", "employeeCategory", "legalEntity", "workLocation", "setupStatus", "postId"]
    },
    advancedFilterStorageKey() {
      const principal = this.$store.getters.id || this.$store.getters.name || "anonymous"
      return `system-user-filters-v2:${principal}`
    },
    advancedFilterSnapshot() {
      return this.advancedFilterKeys().reduce((result, key) => {
        result[key] = this.queryParams[key]
        return result
      }, {})
    },
    restoreAdvancedFilters() {
      try {
        const saved = cache.local.getJSON(this.advancedFilterStorageKey())
        if (saved && typeof saved === "object") Object.assign(this.queryParams, saved)
      } catch (e) {}
    },
    openAdvancedFilters() {
      this.$refs.userFilterDrawer.open(this.advancedFilterSnapshot())
    },
    applyAdvancedFilters({ filters, save }) {
      Object.assign(this.queryParams, filters)
      if (save) cache.local.setJSON(this.advancedFilterStorageKey(), this.advancedFilterSnapshot())
      this.handleQuery()
    },
    clearSavedAdvancedFilters() {
      cache.local.remove(this.advancedFilterStorageKey())
    },
    /** 导入按钮操作 */
    handleImport() {
      this.$refs.importUserRef.open()
    },
    showTemporaryCredential(response, nextUser) {
      const credential = response && response.data && response.data.temporaryCredential
      if (!credential || !credential.temporaryPassword) {
        this.$modal.msgError("操作已完成，但未收到账号密码")
        return
      }
      this.temporaryCredential = {
        userId: credential.userId,
        userName: credential.userName,
        temporaryPassword: credential.temporaryPassword,
        expiresAt: credential.expiresAt
      }
      this.credentialNextUser = nextUser
        ? Object.assign({}, nextUser, { userId: credential.userId, userName: credential.userName })
        : null
      this.credentialVisible = true
    },
    handleCredentialCopySuccess() {
      this.$modal.msgSuccess("临时密码已复制，请通过受控渠道交付")
    },
    handleCredentialCopyError() {
      this.$modal.msgError("复制失败，请手工选中临时密码")
    },
    handleCredentialClosed() {
      const nextUser = this.credentialNextUser
      if (this.temporaryCredential) this.temporaryCredential.temporaryPassword = ""
      this.temporaryCredential = null
      this.credentialNextUser = null
      if (nextUser) this.confirmShopScopeAfterCreate(nextUser)
    }
  }
}
</script>

<style lang="scss" scoped>
.content-inner {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.content-inner > * {
  flex-shrink: 0;
}

.user-search-card {
  padding-bottom: 8px;
}

.user-setup-summary {
  display: grid;
  grid-template-columns: repeat(5, minmax(128px, 1fr));
  gap: 10px;
}

.setup-summary-card {
  --summary-tone: #1473e6;
  --summary-soft: #eef6ff;
  --summary-line: #c9e0ff;
  position: relative;
  display: grid;
  grid-template-columns: 34px minmax(0, 1fr) auto;
  align-items: center;
  gap: 10px;
  min-width: 0;
  padding: 12px 14px;
  overflow: hidden;
  color: #4f555c;
  background: #ffffff;
  border: 1px solid var(--summary-line);
  border-radius: 12px;
  box-shadow: 0 2px 9px rgba(33, 43, 54, 0.04);
  text-align: left;
  cursor: pointer;
  transition: border-color 0.18s ease, box-shadow 0.18s ease, transform 0.18s ease;
}

.setup-summary-card::before {
  position: absolute;
  top: 11px;
  bottom: 11px;
  left: 0;
  width: 3px;
  background: var(--summary-tone);
  border-radius: 0 4px 4px 0;
  content: "";
  opacity: 0.96;
}

.setup-summary-card__icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 34px;
  height: 34px;
  color: var(--summary-tone);
  font-size: 16px;
  background: rgba(255, 255, 255, 0.7);
  border: 1px solid var(--summary-line);
  border-radius: 10px;
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.82);
}

.setup-summary-card__copy {
  display: flex;
  min-width: 0;
  flex-direction: column;
  gap: 3px;
}

.setup-summary-card__label {
  overflow: hidden;
  color: #4b5057;
  font-size: 12px;
  font-weight: 650;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.setup-summary-card strong {
  color: var(--summary-tone) !important;
  font-size: 24px;
  font-variant-numeric: tabular-nums;
  letter-spacing: -0.035em;
}

.setup-summary-card small {
  overflow: hidden;
  color: #737a82;
  font-size: 10px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.setup-summary-card:hover,
.setup-summary-card:focus-visible {
  border-color: var(--summary-tone) !important;
  box-shadow: 0 9px 20px rgba(33, 43, 54, 0.09) !important;
  outline: none;
  transform: translateY(-2px);
}

.setup-summary-card.is-active {
  border-color: var(--summary-tone) !important;
  box-shadow: 0 0 0 2px var(--summary-soft), 0 7px 18px rgba(33, 43, 54, 0.075) !important;
}

.setup-summary-card.is-info {
  --summary-tone: #7456d8;
  --summary-soft: #f5f0ff;
  --summary-line: #d9ccfb;
}

.setup-summary-card.is-danger {
  --summary-tone: #cf3f5a;
  --summary-soft: #fff0f3;
  --summary-line: #f3c3cc;
}

.setup-summary-card.is-warning {
  --summary-tone: #c47400;
  --summary-soft: #fff7e8;
  --summary-line: #f2d39b;
}

.setup-summary-card.is-success {
  --summary-tone: #178a58;
  --summary-soft: #ecfbf3;
  --summary-line: #bfead3;
}

@media (max-width: 1200px) {
  .user-setup-summary {
    grid-template-columns: repeat(3, minmax(150px, 1fr));
  }
}

.user-toolbar-card {
  padding-bottom: 2px;
}

.user-table-card {
  padding-bottom: 8px;
}

.user-account-cell {
  display: inline-flex;
  max-width: 100%;
  align-items: center;
  justify-content: center;
  gap: 8px;
}

.user-identity-marker {
  display: inline-flex;
  flex: 0 0 28px;
  align-items: center;
  justify-content: center;
  width: 28px;
  height: 28px;
  color: #0b5fc2;
  font-size: 12px;
  font-weight: 750;
  background: #eef6ff;
  border: 1px solid #c9e0ff;
  border-radius: 9px;
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.86);
  transition: transform 180ms ease, box-shadow 180ms ease;
}

.user-identity-marker.is-violet {
  color: #6444c2;
  background: #f5f0ff;
  border-color: #d9ccfb;
}

.user-identity-marker.is-sage {
  color: #0b7748;
  background: #ecfbf3;
  border-color: #bfead3;
}

.user-identity-marker.is-amber {
  color: #9f5d00;
  background: #fff7e8;
  border-color: #f2d39b;
}

.user-identity-marker.is-rose {
  color: #b8324b;
  background: #fff0f3;
  border-color: #f3c3cc;
}

.user-account-cell:hover .user-identity-marker {
  box-shadow: 0 5px 12px rgba(44, 55, 67, 0.1);
  transform: scale(1.06) translateY(-1px);
}

.user-account-link {
  overflow: hidden;
  font-weight: 650;
  text-overflow: ellipsis;
  white-space: nowrap;
}

::v-deep .user-row-action {
  margin: 0 1px !important;
  padding: 4px 5px !important;
  transition: color 180ms ease, background 180ms ease, transform 180ms ease;
}

::v-deep .user-row-action.is-edit {
  color: #1473e6 !important;
}

::v-deep .user-row-action.is-delete {
  color: #cf3f5a !important;
}

::v-deep .user-row-action.is-more {
  color: #7456d8 !important;
}

::v-deep .user-row-action.is-edit:hover,
::v-deep .user-row-action.is-edit:focus {
  background: #eef6ff !important;
}

::v-deep .user-row-action.is-delete:hover,
::v-deep .user-row-action.is-delete:focus {
  background: #fff0f3 !important;
}

::v-deep .user-row-action.is-more:hover,
::v-deep .user-row-action.is-more:focus {
  background: #f5f0ff !important;
}

::v-deep .user-row-action:hover,
::v-deep .user-row-action:focus {
  transform: translateY(-1px);
}

.user-profile-form {
  max-height: 62vh;
  overflow-y: auto;
  padding-right: 8px;
}

.temporary-credential-card {
  display: grid;
  gap: 14px;
  margin-top: 18px;
}

.temporary-credential-card > div {
  display: grid;
  grid-template-columns: 90px 1fr;
  align-items: center;
  gap: 12px;
}

.temporary-credential-warning {
  margin: 16px 0 0;
  color: #c45656;
  line-height: 1.6;
}

.employee-profile-tabs {
  min-height: 420px;
}

.employee-profile-tabs ::v-deep > .el-tabs__header {
  display: none;
}

.hr-subnav {
  display: flex;
  justify-content: center;
  margin-bottom: 18px;
}

.selected-role-list {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.wizard-footer-hint {
  float: left;
  color: #909399;
  line-height: 32px;
}

.form-full-control,
.user-profile-form .el-select {
  width: 100%;
}

.derived-profile-alert {
  margin-bottom: 16px;
}

.derived-field-tip {
  color: #909399;
  font-size: 12px;
  line-height: 18px;
  margin-top: 2px;
}

.pii-save-failure {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 14px;
}

.pii-save-failure .el-alert {
  flex: 1;
}

.pii-readonly {
  pointer-events: none;
  opacity: 0.82;
}

@media (prefers-reduced-motion: reduce) {
  .user-identity-marker,
  ::v-deep .user-row-action {
    transition: none !important;
    transform: none !important;
  }
}
</style>
