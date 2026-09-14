<template>
  <div class="app-container oa-workspace-page sign-package-page">
    <template v-if="pageAuthorized">
    <section class="oa-page-hero">
      <div class="oa-hero__copy">
        <span class="oa-hero__eyebrow">OA 协同 · 签署资料</span>
        <div class="oa-hero__title-row">
          <span class="oa-hero__icon"><i class="el-icon-collection" /></span>
          <div>
            <h1>签约资料维护</h1>
            <p>集中管理签约包、签约方案、文件模板、公司印章与签署记录。</p>
          </div>
        </div>
      </div>
      <div class="oa-hero__aside">
        <span>签约包记录</span>
        <strong>{{ total }} 份</strong>
        <small>方案与模板按需加载</small>
      </div>
    </section>

    <section v-if="activeTab === 'plan'" class="sign-scope-toolbar">
      <i class="el-icon-user-solid" />
      <span>HR 统一方案库，适用于全部签约组织，无需切换组织。</span>
    </section>
    <section v-else class="sign-scope-toolbar">
      <sign-scope-selector @ready="handleSignScopeReady" @change="handleSignScopeChange" />
      <span>签约范围独立于库存门店上下文，可选有权公司或门店。</span>
    </section>

    <el-tabs v-model="activeTab" type="border-card" class="oa-tabs-shell">
      <el-tab-pane label="签约包" name="package">
        <div class="toolbar-row">
          <el-form :model="queryParams" inline size="small">
            <el-form-item label="员工">
              <el-input v-model="queryParams.employeeNameSnapshot" clearable placeholder="姓名" @keyup.enter.native="getList" />
            </el-form-item>
            <el-form-item label="场景">
              <el-select v-model="queryParams.scenario" clearable placeholder="全部">
                <el-option v-for="item in packageScenarioFilterOptions" :key="item.value" :label="item.label" :value="item.value" />
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
          <div class="toolbar-actions">
            <el-button v-if="emergencyCreateEnabled" type="primary" size="mini" icon="el-icon-plus" @click="handleAddPackage" v-hasPermi="['oa:signPackage:add']">应急新建签约包</el-button>
            <el-button v-if="emergencyCreateEnabled" type="success" size="mini" icon="el-icon-copy-document" @click="handleOpenBatch" v-hasPermi="['oa:signPackage:add']">应急批量建包</el-button>
          </div>
        </div>

        <el-table v-loading="loading" :data="packageList" border size="small" :empty-text="packageEmptyText">
          <el-table-column label="签约包编号" prop="packageId" width="106" />
          <el-table-column label="员工" prop="employeeNameSnapshot" min-width="110" />
          <el-table-column label="店铺/部门" prop="shopDeptName" min-width="130" />
          <el-table-column label="场景" prop="scenario" width="90">
            <template slot-scope="scope">{{ scenarioLabel(scope.row.scenario) }}</template>
          </el-table-column>
          <el-table-column label="用工类型" width="110">
            <template slot-scope="scope">{{ dictionaryLabel(scope.row.employmentType) }}</template>
          </el-table-column>
          <el-table-column label="文件数" width="80">
            <template slot-scope="scope">{{ scope.row.documentCount != null ? scope.row.documentCount : (scope.row.documents ? scope.row.documents.length : '-') }}</template>
          </el-table-column>
          <el-table-column label="状态" prop="status" width="108">
            <template slot-scope="scope">
              <el-tag size="mini" :type="statusTagType(scope.row.status)">{{ packageStatusLabel(scope.row) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="发送时间" width="160">
            <template slot-scope="scope">{{ displaySignDateTime(scope.row.sentTime) }}</template>
          </el-table-column>
          <el-table-column label="首次签名时间" width="160">
            <template slot-scope="scope">{{ displaySignDateTime(scope.row.initialSignedTime || scope.row.signatureSampleTime || scope.row.signedTime) }}</template>
          </el-table-column>
          <el-table-column label="操作" fixed="right" width="360">
            <template slot-scope="scope">
              <el-button type="text" size="mini" icon="el-icon-view" @click="handleDetail(scope.row)" v-hasPermi="['oa:signPackage:query']">详情</el-button>
              <el-button v-if="scope.row.status === 'draft'" type="text" size="mini" icon="el-icon-edit" @click="handleEditPackage(scope.row)" v-hasPermi="['oa:signPackage:add']">编辑</el-button>
              <el-button v-if="scope.row.status === 'draft'" type="text" size="mini" icon="el-icon-s-promotion" @click="handleSendPackage(scope.row)" v-hasPermi="['oa:signPackage:send']">发送</el-button>
              <el-button v-if="isPreparedSignatureFirstFinalPackage(scope.row)" type="text" size="mini" icon="el-icon-s-promotion" @click="openPreparedFinalTask(scope.row)" v-hasPermi="['oa:signTask:send']">预览并发送最终文件</el-button>
              <el-button v-else-if="scope.row.status === 'pending_company'" type="text" size="mini" icon="el-icon-s-claim" @click="handleOpenFinalize(scope.row)" v-hasPermi="['oa:signTask:batchFinalize', 'oa:signPackage:send']">{{ pendingCompanyActionLabel(scope.row) }}</el-button>
              <el-button v-if="['draft', 'pending_sign', 'part_viewed', 'pending_company', 'pending_final_confirm'].includes(scope.row.status)" type="text" size="mini" icon="el-icon-close" @click="handleVoidPackage(scope.row)" v-hasPermi="['oa:signPackage:void']">撤回</el-button>
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

      <el-tab-pane label="签约方案" name="plan">
        <div class="toolbar-row">
          <el-form :model="planQuery" inline size="small">
            <el-form-item label="方案">
              <el-input v-model="planQuery.planName" clearable placeholder="方案名" @keyup.enter.native="getPlans" />
            </el-form-item>
            <el-form-item label="岗位">
              <el-input v-model="planQuery.postName" clearable placeholder="岗位" @keyup.enter.native="getPlans" />
            </el-form-item>
            <el-form-item label="状态">
              <el-select v-model="planQuery.status" clearable placeholder="全部">
                <el-option label="启用" value="0" />
                <el-option label="停用" value="1" />
              </el-select>
            </el-form-item>
            <el-form-item>
              <el-button type="primary" size="mini" icon="el-icon-search" @click="getPlans">查询</el-button>
              <el-button size="mini" icon="el-icon-refresh" @click="resetPlanQuery">重置</el-button>
            </el-form-item>
          </el-form>
          <el-button type="primary" size="mini" icon="el-icon-plus" @click="handleAddPlan" v-hasPermi="['oa:signPackage:template']">新增方案</el-button>
        </div>

        <el-table v-loading="planLoading" :data="planList" border size="small" :empty-text="planEmptyText">
          <el-table-column label="方案编号" prop="planId" width="88" />
          <el-table-column label="方案名" prop="planName" min-width="170" show-overflow-tooltip />
          <el-table-column label="场景" prop="scenario" width="90">
            <template slot-scope="scope">{{ scenarioLabel(scope.row.scenario) }}</template>
          </el-table-column>
          <el-table-column label="适用岗位" prop="postName" min-width="120" show-overflow-tooltip />
          <el-table-column label="公司确定方式" min-width="160">
            <template>员工首次签名后按部门识别</template>
          </el-table-column>
          <el-table-column label="用工类型" width="110">
            <template slot-scope="scope">{{ dictionaryLabel(scope.row.employmentType) }}</template>
          </el-table-column>
          <el-table-column label="状态" prop="status" width="90">
            <template slot-scope="scope">
              <el-tag size="mini" :type="planStatusTagType(scope.row.status)">{{ planStatusLabel(scope.row.status) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="模板数" width="80">
            <template slot-scope="scope">{{ scope.row.templates ? scope.row.templates.length : 0 }}</template>
          </el-table-column>
          <el-table-column label="截止提醒" width="110">
            <template slot-scope="scope">{{ reminderPolicyLabel(scope.row.reminderPolicyJson) }}</template>
          </el-table-column>
          <el-table-column label="排序" prop="sortOrder" width="80" />
          <el-table-column label="操作" fixed="right" width="170">
            <template slot-scope="scope">
              <el-button type="text" size="mini" icon="el-icon-edit" @click="handleEditPlan(scope.row)" v-hasPermi="['oa:signPackage:template']">编辑</el-button>
              <el-button type="text" size="mini" icon="el-icon-upload2" @click="handlePublishPlan(scope.row)" v-hasPermi="['oa:signPackage:template']">发布</el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>

      <el-tab-pane label="模板管理" name="template">
        <div class="toolbar-row">
          <el-form :model="templateQuery" inline size="small">
            <el-form-item label="类型">
              <el-select v-model="templateQuery.templateType" clearable filterable placeholder="全部">
              <el-option v-for="item in templateTypeOptions" :key="item.code" :label="item.label" :value="item.code" />
              </el-select>
            </el-form-item>
            <el-form-item label="状态">
              <el-select v-model="templateQuery.status" clearable placeholder="全部">
                <el-option label="启用" value="0" />
                <el-option label="停用" value="1" />
              </el-select>
            </el-form-item>
            <el-form-item>
              <el-button type="primary" size="mini" icon="el-icon-search" @click="getTemplates">查询</el-button>
            </el-form-item>
          </el-form>
          <el-button type="primary" size="mini" icon="el-icon-plus" @click="handleAddTemplate" v-hasPermi="['oa:signPackage:template']">新增模板</el-button>
        </div>

        <el-alert title="模板保存时会校验文件、必填占位符及员工可见/阅读/签署策略；内部归档材料不会出现在员工端。" type="info" show-icon :closable="false" />
        <el-table v-loading="templateLoading" :data="templateList" border size="small" class="template-table" :empty-text="templateEmptyText">
          <el-table-column label="模板编号" prop="templateId" width="86" />
          <el-table-column label="模板名称" prop="templateName" min-width="180" />
          <el-table-column label="文件类型" prop="templateType" min-width="160">
            <template slot-scope="scope">{{ templateTypeLabel(scope.row.templateType) }}</template>
          </el-table-column>
          <el-table-column label="版本" width="120">
            <template slot-scope="scope">{{ versionLabel(scope.row.templateVersion) }}</template>
          </el-table-column>
          <el-table-column label="适用规则" min-width="180">
            <template slot-scope="scope">
              {{ scope.row.employmentType ? dictionaryLabel(scope.row.employmentType) : '全部用工' }} /
              {{ scope.row.socialType ? dictionaryLabel(scope.row.socialType) : '全部社保' }} /
              {{ scope.row.postLevelScope || '全部等级' }}
            </template>
          </el-table-column>
          <el-table-column label="员工可见" prop="employeeVisible" width="88">
            <template slot-scope="scope">
              <el-tag size="mini" :type="scope.row.employeeVisible === 'N' ? 'info' : 'success'">{{ scope.row.employeeVisible === 'N' ? '内部' : '可见' }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="阅读确认" prop="readConfirmationRequired" width="88">
            <template slot-scope="scope">
              <el-tag size="mini" :type="scope.row.readConfirmationRequired === 'Y' ? 'warning' : 'info'">{{ scope.row.readConfirmationRequired === 'Y' ? '需要' : '无需' }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="员工签署" prop="employeeSignRequired" width="88">
            <template slot-scope="scope">
              <el-tag size="mini" :type="scope.row.employeeSignRequired === 'Y' ? 'success' : 'info'">{{ scope.row.employeeSignRequired === 'Y' ? '是' : '否' }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="企业章" prop="companySealRequired" width="88">
            <template slot-scope="scope">
              <el-tag size="mini" :type="scope.row.companySealRequired === 'Y' ? 'warning' : 'info'">
                {{ scope.row.companySealRequired === 'Y' ? '需要' : (scope.row.companySealRequired === 'N' ? '无需' : '待配置') }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="状态" prop="status" width="90">
            <template slot-scope="scope">
              <el-tag size="mini" :type="scope.row.status === '0' ? 'success' : 'info'">{{ scope.row.status === '0' ? '启用' : '停用' }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="文件" min-width="180">
            <template slot-scope="scope">
              <template v-if="scope.row.fileUrl">
                <el-button type="text" size="mini" icon="el-icon-view" @click="handlePreviewTemplate(scope.row)">在线预览</el-button>
                <el-button type="text" size="mini" icon="el-icon-download" @click="handleDownloadTemplate(scope.row)">下载</el-button>
              </template>
              <span v-else>-</span>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="110">
            <template slot-scope="scope">
              <el-button type="text" size="mini" icon="el-icon-edit" @click="handleEditTemplate(scope.row)" v-hasPermi="['oa:signPackage:template']">编辑</el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>

      <el-tab-pane v-if="canViewCompanySealManagement" label="公司与印章" name="company" lazy>
        <company-seal-management />
      </el-tab-pane>

      <el-tab-pane label="签约合同" name="record">
        <sign-package-record-panel
          :sign-package="detail"
          @open-original="openGeneratedDocument"
          @open-signed="openSignedDocument"
          @open-final="openFinalDocument"
          @open-certificate="openGeneratedCertificate"
        />
      </el-tab-pane>
    </el-tabs>

    <el-dialog :title="packageTitle" :visible.sync="packageOpen" width="960px" append-to-body>
      <el-form ref="packageForm" :model="packageForm" :rules="packageRules" label-width="118px" size="small">
        <el-alert
          v-if="!packageForm.taskId"
          title="应急建包属于非生命周期任务来源，必须填写原因；正常签约请从任务中心发起。"
          type="warning"
          show-icon
          :closable="false"
          class="detail-block"
        />
        <el-form-item v-if="!packageForm.taskId" label="应急原因" prop="remark">
          <el-input v-model.trim="packageForm.remark" type="textarea" maxlength="450" show-word-limit />
        </el-form-item>
        <el-row :gutter="12">
          <el-col :xs="24" :sm="12">
            <el-form-item label="员工账号" prop="employeeId">
              <el-select v-model="packageForm.employeeId" filterable placeholder="选择员工" style="width: 100%" :disabled="!!packageForm.packageId || !!packageForm.taskId" @change="handleEmployeeChange">
                <el-option
                  v-for="user in employeeOptions"
                  :key="user.userId"
                  :label="user.nickName ? user.nickName + ' / ' + user.userName : user.userName"
                  :value="user.userId"
                />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :xs="24" :sm="12">
            <el-form-item label="员工姓名" prop="employeeNameSnapshot">
              <el-input v-model="packageForm.employeeNameSnapshot" />
            </el-form-item>
          </el-col>
          <el-col :xs="24" :sm="12">
            <el-form-item label="手机号" prop="employeePhoneSnapshot">
              <el-input v-model="packageForm.employeePhoneSnapshot" maxlength="32" />
            </el-form-item>
          </el-col>
          <el-col :xs="24" :sm="12">
            <el-form-item label="身份证号" prop="employeeIdCardSnapshot">
              <el-input v-model="packageForm.employeeIdCardSnapshot" maxlength="32" />
            </el-form-item>
          </el-col>
          <el-col :xs="24" :sm="12">
            <el-form-item label="住址">
              <el-input v-model="packageForm.employeeAddressSnapshot" maxlength="200" />
            </el-form-item>
          </el-col>
          <el-col :xs="24" :sm="12">
            <el-form-item label="场景" prop="scenario">
              <el-select v-model="packageForm.scenario" style="width: 100%" :disabled="!!packageForm.taskId" @change="handlePackageScenarioChange">
                <el-option v-for="item in packageScenarioOptions" :key="item.value" :label="item.label" :value="item.value" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-alert
              title="此处不提前确定合同公司。员工完成首次手写签名后，系统会按员工归属部门自动识别公司，再由经办人确认公司并选择对应印章。"
              type="info"
              show-icon
              :closable="false"
            />
          </el-col>
          <el-col :xs="24" :sm="12">
            <el-form-item label="法律主体">
              <el-input
                v-model="packageForm.legalEntityNameSnapshot"
                :disabled="!!packageForm.taskId"
                maxlength="160"
                placeholder="优先从员工档案带入"
              />
              <div class="form-tip">
                主体ID：{{ packageForm.legalEntityIdSnapshot || '未维护' }}；合同公司名称不会再使用门店名称代替。
              </div>
            </el-form-item>
          </el-col>
          <el-col :xs="24" :sm="12">
            <el-form-item label="用工类型">
              <el-select v-model="packageForm.employmentType" clearable style="width: 100%" @change="handlePackageEmploymentTypeChange">
                <el-option label="劳动合同" value="劳动合同" />
                <el-option label="劳务合同" value="劳务合同" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :xs="24" :sm="12">
            <el-form-item label="社保口径">
              <el-select v-model="packageForm.socialType" clearable style="width: 100%" @change="handlePackageSocialTypeChange">
                <el-option label="有社保" value="有社保" />
                <el-option label="无社保" value="无社保" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col v-if="showPackageServiceFields" :xs="24" :sm="12">
            <el-form-item label="劳务人员类型">
              <el-select v-model="packageForm.servicePersonType" clearable style="width: 100%">
                <el-option label="在校实习生" value="在校实习生" />
                <el-option label="退休返聘" value="退休返聘" />
                <el-option label="其他劳务人员" value="其他劳务人员" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col v-if="showPackageServiceFields" :xs="24" :sm="12">
            <el-form-item label="保险类型">
              <el-select v-model="packageForm.insuranceType" clearable style="width: 100%">
                <el-option label="商业意外保险" value="商业意外保险" />
                <el-option label="雇主责任险" value="雇主责任险" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :xs="24" :sm="12">
            <el-form-item label="岗位">
              <el-input v-model="packageForm.postNameSnapshot" />
            </el-form-item>
          </el-col>
          <el-col :xs="24" :sm="8">
            <el-form-item label="岗位等级">
              <el-input v-model="packageForm.postLevelSnapshot" @change="matchTemplates" />
            </el-form-item>
          </el-col>
          <el-col :xs="24" :sm="8">
            <el-form-item label="薪酬版本">
              <el-input v-if="packageOnboardLabor" :value="packageForm.salaryVersion ? `${packageForm.salaryVersion}版` : '请先选择社保口径'" disabled />
              <el-select v-else v-model="packageForm.salaryVersion" clearable style="width: 100%" @change="matchTemplates">
                <el-option label="A版" value="A" />
                <el-option label="B版" value="B" />
              </el-select>
              <div v-if="packageOnboardLabor" class="form-tip">系统固定按社保口径匹配：有社保 B 版，无社保 A 版。</div>
            </el-form-item>
          </el-col>
          <el-col v-if="showPackageEntryDate" :xs="24" :sm="8">
            <el-form-item label="入职日期">
              <el-date-picker v-model="packageForm.entryDate" value-format="yyyy-MM-dd" type="date" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col v-if="showPackageContractFields" :xs="24" :sm="12">
            <el-form-item label="合同开始">
              <el-date-picker v-model="packageForm.contractStartDate" value-format="yyyy-MM-dd" type="date" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col v-if="showPackageContractFields" :xs="24" :sm="12">
            <el-form-item label="合同结束">
              <el-date-picker v-model="packageForm.contractEndDate" value-format="yyyy-MM-dd" type="date" style="width: 100%" />
            </el-form-item>
          </el-col>
          <template v-if="showPackageRenewalFields">
            <el-col :xs="24" :sm="12">
              <el-form-item label="原合同结束">
                <el-date-picker
                  v-model="packageForm.previousContractEndDate"
                  value-format="yyyy-MM-dd"
                  type="date"
                  :disabled="!!packageForm.taskId"
                  style="width: 100%"
                />
              </el-form-item>
            </el-col>
            <el-col :xs="24" :sm="12">
              <el-form-item label="原合同类型">
                <el-select v-model="packageForm.previousEmploymentType" :disabled="!!packageForm.taskId" style="width: 100%">
                  <el-option label="劳动合同" value="劳动合同" />
                  <el-option label="劳务合同" value="劳务合同" />
                </el-select>
              </el-form-item>
            </el-col>
            <el-col :xs="24" :sm="12">
              <el-form-item label="原续签次数">
                <el-input-number v-model="packageForm.previousRenewalCount" :min="0" :precision="0" :disabled="!!packageForm.taskId" style="width: 100%" />
              </el-form-item>
            </el-col>
            <el-col :xs="24" :sm="12">
              <el-form-item label="本次续签次数">
                <el-input-number v-model="packageForm.renewalCount" :min="1" :precision="0" :disabled="!!packageForm.taskId" style="width: 100%" />
                <div class="form-tip">必须等于原续签次数 + 1；主体或合同类型变化时系统会阻止直接发送。</div>
              </el-form-item>
            </el-col>
          </template>
          <el-col v-if="showPackageProbationFields && packageForm.employmentType === '劳动合同'" :xs="24" :sm="12">
            <el-form-item label="试用期开始">
              <el-date-picker v-model="packageForm.probationStartDate" value-format="yyyy-MM-dd" type="date" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col v-if="showPackageProbationFields && packageForm.employmentType === '劳动合同'" :xs="24" :sm="12">
            <el-form-item label="试用期结束">
              <el-date-picker v-model="packageForm.probationEndDate" value-format="yyyy-MM-dd" type="date" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col v-if="showPackageSalaryFields" :xs="24" :sm="6">
            <el-form-item label="底薪">
              <el-input-number v-model="packageForm.baseSalary" :min="0" :precision="2" controls-position="right" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col v-if="showPackageSalaryFields" :xs="24" :sm="6">
            <el-form-item label="岗位津贴">
              <el-input-number v-model="packageForm.postSalary" :min="0" :precision="2" controls-position="right" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col v-if="showPackageSalaryFields" :xs="24" :sm="6">
            <el-form-item label="综合驻外补贴">
              <el-input-number v-model="packageForm.fieldAllowance" :min="0" :precision="2" controls-position="right" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col v-if="showPackageSalaryFields" :xs="24" :sm="6">
            <el-form-item label="绩效工资">
              <el-input-number v-model="packageForm.performanceSalary" :min="0" :precision="2" controls-position="right" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col v-if="showPackageSalaryFields" :xs="24" :sm="6">
            <el-form-item label="综合工资合计">
              <el-input-number v-model="packageForm.salaryTotal" :min="0" :precision="2" controls-position="right" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col v-if="showPackageOffboardFields" :xs="24" :sm="8">
            <el-form-item label="离职日期">
              <el-date-picker v-model="packageForm.leaveDate" value-format="yyyy-MM-dd" type="date" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col v-if="showPackageOffboardFields" :xs="24" :sm="16">
            <el-form-item label="离职原因">
              <el-input v-model="packageForm.leaveReason" maxlength="200" />
            </el-form-item>
          </el-col>
        </el-row>

        <el-divider content-position="left">文件清单</el-divider>
        <el-table :data="matchedTemplateList" border size="mini">
          <el-table-column label="文件类型" prop="templateType" min-width="170">
            <template slot-scope="scope">{{ templateTypeLabel(scope.row.templateType) }}</template>
          </el-table-column>
          <el-table-column label="模板名称" prop="templateName" min-width="180" />
          <el-table-column label="员工可见" prop="employeeVisible" width="86">
            <template slot-scope="scope">{{ scope.row.employeeVisible === 'N' ? '内部' : '可见' }}</template>
          </el-table-column>
          <el-table-column label="需阅读" prop="readConfirmationRequired" width="80">
            <template slot-scope="scope">{{ scope.row.readConfirmationRequired === 'Y' ? '是' : '否' }}</template>
          </el-table-column>
          <el-table-column label="必签" prop="employeeSignRequired" width="80">
            <template slot-scope="scope">{{ scope.row.employeeSignRequired === 'Y' ? '是' : '否' }}</template>
          </el-table-column>
          <el-table-column label="版本" width="120">
            <template slot-scope="scope">{{ versionLabel(scope.row.templateVersion) }}</template>
          </el-table-column>
        </el-table>
      </el-form>
      <div slot="footer">
        <el-button @click="packageOpen = false">取 消</el-button>
        <el-button :loading="matching" @click="matchTemplates">匹配模板</el-button>
        <el-button type="primary" :loading="savingPackage" @click="submitPackage" v-hasPermi="['oa:signPackage:add']">保 存</el-button>
      </div>
    </el-dialog>

    <el-dialog v-if="emergencyCreateEnabled" title="应急批量按岗位生成草稿" :visible.sync="batchOpen" width="1120px" append-to-body>
      <el-form ref="batchForm" :model="batchForm" :rules="batchRules" inline size="small" class="batch-form">
        <el-form-item label="签约方案" prop="planId">
          <el-select v-model="batchForm.planId" filterable placeholder="选择方案" style="width: 240px" @change="handleBatchPlanChange">
            <el-option
              v-for="plan in batchPlanOptions"
              :key="plan.planId"
              :label="plan.planName + (plan.postName ? ' / ' + plan.postName : '')"
              :value="plan.planId"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="应急原因" prop="emergencyReason">
          <el-input v-model.trim="batchForm.emergencyReason" maxlength="450" placeholder="说明为何不从生命周期任务发起" style="width: 300px" />
        </el-form-item>
        <el-form-item label="岗位">
          <el-input v-model="batchForm.postName" clearable placeholder="通用方案可筛岗位" style="width: 180px" />
        </el-form-item>
        <el-form-item label="关键词">
          <el-input v-model="batchForm.keyword" clearable placeholder="姓名/账号/手机号" style="width: 200px" @keyup.enter.native="handlePreviewBatch" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" icon="el-icon-search" :loading="batchPreviewLoading" @click="handlePreviewBatch">预览</el-button>
        </el-form-item>
      </el-form>

      <el-table :data="batchPreviewRows" border size="small" max-height="420" class="batch-table" :empty-text="batchEmptyText">
        <el-table-column label="选择" width="64" align="center">
          <template slot-scope="scope">
            <el-checkbox v-model="scope.row.selected" :disabled="scope.row.creatable === false" />
          </template>
        </el-table-column>
        <el-table-column label="员工" min-width="120">
          <template slot-scope="scope">
            <div>{{ scope.row.employeeNameSnapshot || '-' }}</div>
            <div class="cell-subtext">{{ scope.row.deptNameSnapshot || '-' }}</div>
          </template>
        </el-table-column>
        <el-table-column label="合同公司" min-width="170">
          <template>首次签名后按部门识别</template>
        </el-table-column>
        <el-table-column label="手机号" prop="employeePhoneSnapshot" width="130" />
        <el-table-column label="岗位" prop="postNameSnapshot" min-width="120" show-overflow-tooltip />
        <el-table-column label="匹配结果" min-width="170" show-overflow-tooltip>
          <template slot-scope="scope">
            <el-tag
              v-if="scope.row.packageMatchName"
              size="mini"
              :type="scope.row.packageMatchName === '不可自动匹配' ? 'danger' : 'success'"
            >{{ scope.row.packageMatchName }}</el-tag>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column label="合同/社保" min-width="150">
          <template slot-scope="scope">
            <div>{{ dictionaryLabel(scope.row.employmentType) }} / {{ dictionaryLabel(scope.row.socialType) }}</div>
            <div class="cell-subtext">职级 {{ dictionaryLabel(scope.row.postLevelSnapshot) }}</div>
          </template>
        </el-table-column>
        <el-table-column label="入职/合同日期" min-width="180">
          <template slot-scope="scope">
            <div>入职：{{ scope.row.entryDate || '-' }}</div>
            <div class="cell-subtext">{{ scope.row.contractStartDate || '-' }} 至 {{ scope.row.contractEndDate || '-' }}</div>
          </template>
        </el-table-column>
        <el-table-column label="身份证号" width="180">
          <template slot-scope="scope">
            <el-input v-model="scope.row.employeeIdCardSnapshot" size="mini" maxlength="32" @input="refreshBatchRowState(scope.row)" />
          </template>
        </el-table-column>
        <el-table-column label="地址" min-width="190">
          <template slot-scope="scope">
            <el-input v-model="scope.row.employeeAddressSnapshot" size="mini" maxlength="200" />
          </template>
        </el-table-column>
        <el-table-column label="缺失字段" min-width="150">
          <template slot-scope="scope">
            <el-tag
              v-for="field in scope.row.missingFields || []"
              :key="field"
              size="mini"
              type="danger"
              class="field-tag"
            >{{ field }}</el-tag>
            <span v-if="!scope.row.missingFields || !scope.row.missingFields.length">-</span>
          </template>
        </el-table-column>
        <el-table-column label="模板数" width="80">
          <template slot-scope="scope">{{ scope.row.templates ? scope.row.templates.length : 0 }}</template>
        </el-table-column>
        <el-table-column label="状态" width="130">
          <template slot-scope="scope">
            <el-tag size="mini" :type="scope.row.creatable === false ? 'danger' : 'success'">{{ scope.row.creatable === false ? scope.row.skipReason || '不可创建' : '可创建草稿' }}</el-tag>
          </template>
        </el-table-column>
      </el-table>

      <div slot="footer">
        <el-button @click="batchOpen = false">取 消</el-button>
        <el-button :loading="batchPreviewLoading" @click="handlePreviewBatch">刷新预览</el-button>
        <el-button type="primary" :loading="batchCreating" @click="handleCreateDrafts" v-hasPermi="['oa:signPackage:add']">创建草稿</el-button>
      </div>
    </el-dialog>

    <el-dialog :title="planTitle" :visible.sync="planOpen" width="920px" append-to-body>
      <el-form ref="planForm" :model="planForm" :rules="planRules" label-width="118px" size="small">
        <el-alert
          title="签约方案不预先绑定公司。员工完成首次手写签名后，系统按归属部门识别公司，再由经办人确认公司和对应印章。"
          type="info"
          show-icon
          :closable="false"
          class="detail-block"
        />
        <el-row :gutter="12">
          <el-col :xs="24" :sm="12">
            <el-form-item label="方案名" prop="planName">
              <el-input v-model="planForm.planName" maxlength="120" />
            </el-form-item>
          </el-col>
          <el-col :xs="24" :sm="12">
            <el-form-item label="适用岗位">
              <el-input v-model="planForm.postName" maxlength="120" placeholder="如项目总监" />
            </el-form-item>
          </el-col>
          <el-col :xs="24" :sm="12">
            <el-form-item label="场景" prop="scenario">
              <el-select v-model="planForm.scenario" style="width: 100%" @change="handlePlanScenarioChange">
                <el-option v-for="item in packageScenarioOptions" :key="item.value" :label="item.label" :value="item.value" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :xs="24" :sm="12">
            <el-form-item label="用工类型">
              <el-select v-model="planForm.employmentType" clearable style="width: 100%" @change="handlePlanSocialTypeChange">
                <el-option label="劳动合同" value="劳动合同" />
                <el-option label="劳务合同" value="劳务合同" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :xs="24" :sm="12">
            <el-form-item label="签署期限(天)">
              <el-input-number v-model="planForm.signDeadlineDays" :min="1" :max="365" :precision="0" style="width: 100%" @change="handleReminderDeadlineChange" />
            </el-form-item>
          </el-col>
          <el-col :xs="24" :sm="12">
            <el-form-item label="截止提醒">
              <el-radio-group v-model="planForm.reminderEnabled">
                <el-radio :label="false">关闭</el-radio>
                <el-radio :label="true">开启</el-radio>
              </el-radio-group>
            </el-form-item>
          </el-col>
          <el-col v-if="planForm.reminderEnabled" :xs="24" :sm="12">
            <el-form-item label="提前提醒">
              <el-select v-model="planForm.reminderDaysBefore" multiple collapse-tags style="width: 100%" placeholder="选择提前天数">
                <el-option v-for="day in reminderDayOptions" :key="day" :label="`提前 ${day} 天`" :value="day" />
              </el-select>
              <div class="form-tip">提前天数不能超过签署期限；提醒随发布版本冻结，空策略不会推断默认提醒。</div>
            </el-form-item>
          </el-col>
          <el-col :xs="24" :sm="12">
            <el-form-item label="社保口径">
              <el-select v-model="planForm.socialType" clearable style="width: 100%" @change="handlePlanSocialTypeChange">
                <el-option label="有社保" value="有社保" />
                <el-option label="无社保" value="无社保" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :xs="24" :sm="12">
            <el-form-item label="劳务人员类型">
              <el-select v-model="planForm.servicePersonType" clearable style="width: 100%">
                <el-option label="在校实习生" value="在校实习生" />
                <el-option label="退休返聘" value="退休返聘" />
                <el-option label="其他劳务人员" value="其他劳务人员" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :xs="24" :sm="12">
            <el-form-item label="保险类型">
              <el-select v-model="planForm.insuranceType" clearable style="width: 100%">
                <el-option label="商业意外保险" value="商业意外保险" />
                <el-option label="雇主责任险" value="雇主责任险" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :xs="24" :sm="6">
            <el-form-item label="岗位等级">
              <el-input v-model="planForm.postLevelSnapshot" maxlength="32" />
            </el-form-item>
          </el-col>
          <el-col :xs="24" :sm="6">
            <el-form-item label="薪酬版本">
              <el-input v-if="planOnboardLabor" :value="planForm.salaryVersion ? `${planForm.salaryVersion}版` : '请先选择社保口径'" disabled />
              <el-select v-else v-model="planForm.salaryVersion" clearable style="width: 100%" @change="prunePlanTemplateSelection">
                <el-option label="A版" value="A" />
                <el-option label="B版" value="B" />
              </el-select>
              <div v-if="planOnboardLabor" class="form-tip">系统固定按社保口径匹配：有社保 B 版，无社保 A 版。</div>
            </el-form-item>
          </el-col>
          <el-col :xs="24" :sm="6">
            <el-form-item label="状态">
              <el-radio-group v-model="planForm.status">
                <el-radio label="0">启用</el-radio>
                <el-radio label="1">停用</el-radio>
              </el-radio-group>
            </el-form-item>
          </el-col>
          <el-col :xs="24" :sm="6">
            <el-form-item label="排序">
              <el-input-number v-model="planForm.sortOrder" :min="0" controls-position="right" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col v-if="showPlanEntryDate" :xs="24" :sm="8">
            <el-form-item label="入职日期">
              <el-date-picker v-model="planForm.entryDate" value-format="yyyy-MM-dd" type="date" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col v-if="showPlanContractFields" :xs="24" :sm="8">
            <el-form-item label="合同开始">
              <el-date-picker v-model="planForm.contractStartDate" value-format="yyyy-MM-dd" type="date" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col v-if="showPlanContractFields" :xs="24" :sm="8">
            <el-form-item label="合同结束">
              <el-date-picker v-model="planForm.contractEndDate" value-format="yyyy-MM-dd" type="date" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col v-if="showPlanProbationFields" :xs="24" :sm="12">
            <el-form-item label="试用期开始">
              <el-date-picker v-model="planForm.probationStartDate" value-format="yyyy-MM-dd" type="date" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col v-if="showPlanProbationFields" :xs="24" :sm="12">
            <el-form-item label="试用期结束">
              <el-date-picker v-model="planForm.probationEndDate" value-format="yyyy-MM-dd" type="date" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col v-if="showPlanSalaryFields" :xs="24" :sm="6">
            <el-form-item label="底薪">
              <el-input-number v-model="planForm.baseSalary" :min="0" :precision="2" controls-position="right" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col v-if="showPlanSalaryFields" :xs="24" :sm="6">
            <el-form-item label="岗位津贴">
              <el-input-number v-model="planForm.postSalary" :min="0" :precision="2" controls-position="right" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col v-if="showPlanSalaryFields" :xs="24" :sm="6">
            <el-form-item label="综合驻外补贴">
              <el-input-number v-model="planForm.fieldAllowance" :min="0" :precision="2" controls-position="right" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col v-if="showPlanSalaryFields" :xs="24" :sm="6">
            <el-form-item label="绩效工资">
              <el-input-number v-model="planForm.performanceSalary" :min="0" :precision="2" controls-position="right" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col v-if="showPlanSalaryFields" :xs="24" :sm="6">
            <el-form-item label="综合工资合计">
              <el-input-number v-model="planForm.salaryTotal" :min="0" :precision="2" controls-position="right" style="width: 100%" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="绑定模板">
          <el-select v-model="planTemplateIds" multiple filterable collapse-tags placeholder="选择启用模板" style="width: 100%">
            <el-option
              v-for="template in filteredPlanTemplateOptions"
              :key="template.templateId"
              :label="template.templateName + ' / ' + templateTypeLabel(template.templateType)"
              :value="template.templateId"
            />
          </el-select>
          <div class="form-tip">仅显示当前场景可绑定的模板；切换场景会自动清除不兼容选择。</div>
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="planForm.remark" type="textarea" maxlength="500" show-word-limit />
        </el-form-item>
      </el-form>
      <div slot="footer">
        <el-button @click="planOpen = false">取 消</el-button>
        <el-button type="primary" :loading="savingPlan" @click="submitPlan" v-hasPermi="['oa:signPackage:template']">保 存</el-button>
      </div>
    </el-dialog>

    <el-dialog title="发布方案版本前检查" :visible.sync="publishOpen" width="760px" append-to-body
      :show-close="!publishing" :close-on-click-modal="!publishing" :close-on-press-escape="!publishing">
      <el-alert v-if="publishError" :title="publishError" type="error" show-icon :closable="false" class="detail-block" />
      <div v-if="publishPreviewLoading" class="form-tip">正在核对方案内容与当前启用版本…</div>
      <el-alert v-if="publishPreview" :title="publishPreview.message" :type="publishPreview.action === 'RESTORED' ? 'warning' : 'info'"
        show-icon :closable="false" class="detail-block" />
      <div v-if="publishPreview && publishPreview.activeVersions.length" class="form-tip">
        当前启用：{{ publishPreview.activeVersions.map(version => 'V' + version.versionNo).join('、') }}
      </div>
      <template v-if="publishDetail">
        <el-alert
          title="新发布或恢复版本仅用于后续新任务，已有签包保持原版本；本次不会自动发送。"
          type="info"
          show-icon
          :closable="false"
          class="detail-block"
        />
        <el-descriptions :column="2" border size="small" class="detail-block">
          <el-descriptions-item label="方案">{{ publishDetail.planName }}</el-descriptions-item>
          <el-descriptions-item label="场景">{{ scenarioLabel(publishDetail.scenario) }}</el-descriptions-item>
          <el-descriptions-item label="公司确定方式">首次签名后按部门识别</el-descriptions-item>
          <el-descriptions-item label="适用范围">全部签约组织</el-descriptions-item>
          <el-descriptions-item label="文件统计">
            员工可见 {{ publishSummary.employeeVisible }} / 人力资源内部 {{ publishSummary.internal }}
          </el-descriptions-item>
          <el-descriptions-item label="签署文件">{{ publishSummary.signing }}</el-descriptions-item>
          <el-descriptions-item label="盖章文件">{{ publishSummary.sealing }}</el-descriptions-item>
        </el-descriptions>
        <el-alert
          v-if="publishBlockingReasons.length"
          :title="'暂不能发布：' + publishBlockingReasons.join('；')"
          type="error"
          show-icon
          :closable="false"
          class="detail-block"
        />
        <el-table :data="publishTemplateRows" border size="small" max-height="300">
          <el-table-column label="文件" prop="templateName" min-width="180" show-overflow-tooltip />
          <el-table-column label="类型" min-width="190" show-overflow-tooltip>
            <template slot-scope="scope">{{ templateTypeLabel(scope.row.templateType) }}</template>
          </el-table-column>
          <el-table-column label="员工可见" width="90">
            <template slot-scope="scope">{{ scope.row.employeeVisible === 'Y' ? '是' : '否' }}</template>
          </el-table-column>
          <el-table-column label="需阅读" width="80">
            <template slot-scope="scope">{{ scope.row.readConfirmationRequired === 'Y' ? '是' : '否' }}</template>
          </el-table-column>
          <el-table-column label="需签名" width="80">
            <template slot-scope="scope">{{ scope.row.employeeSignRequired === 'Y' ? '是' : '否' }}</template>
          </el-table-column>
          <el-table-column label="需盖章" width="80">
            <template slot-scope="scope">{{ scope.row.companySealRequired === 'Y' ? '是' : (scope.row.companySealRequired === 'N' ? '否' : '未配置') }}</template>
          </el-table-column>
        </el-table>
        <div class="form-tip">点击发布后，服务端还会校验模板场景、文件哈希、必填占位符、签名定位和企业章定位。</div>
      </template>
      <div slot="footer">
        <el-button :disabled="publishing" @click="publishOpen = false">取 消</el-button>
        <el-button v-if="publishError" :loading="publishPreviewLoading" :disabled="publishing" @click="handlePublishPlan({ planId: publishTargetId })">刷新预览并核对</el-button>
        <el-button type="primary" :loading="publishing" :disabled="!publishPreview || publishPreviewLoading || publishBlockingReasons.length > 0" @click="confirmPublishPlan">
          {{ publishPreview && publishPreview.action === 'RESTORED' ? '确认恢复该版本' : '确认发布' }}
        </el-button>
      </div>
    </el-dialog>

    <el-dialog :title="templateTitle" :visible.sync="templateOpen" width="720px" append-to-body>
      <el-form ref="templateForm" :model="templateForm" :rules="templateRules" label-width="124px" size="small">
        <el-form-item label="文件类型" prop="templateType">
          <el-select v-model="templateForm.templateType" filterable placeholder="选择固定文件类型" style="width: 100%" @change="handleTemplateTypeChange">
            <el-option v-for="item in templateTypeOptions" :key="item.code" :label="item.label" :value="item.code" />
          </el-select>
        </el-form-item>
        <el-form-item label="模板名称" prop="templateName">
          <el-input v-model="templateForm.templateName" maxlength="120" />
        </el-form-item>
        <el-row :gutter="12">
          <el-col :xs="24" :sm="12">
            <el-form-item label="版本">
              <el-input v-model="templateForm.templateVersion" maxlength="32" />
            </el-form-item>
          </el-col>
          <el-col :xs="24" :sm="12">
            <el-form-item label="场景">
              <el-input :value="scenarioLabel(templateForm.scenario)" disabled />
            </el-form-item>
          </el-col>
          <el-col v-if="showEmploymentType" :xs="24" :sm="12">
            <el-form-item label="用工类型">
              <el-select v-model="templateForm.employmentType" clearable style="width: 100%">
                <el-option label="劳动合同" value="劳动合同" />
                <el-option label="劳务合同" value="劳务合同" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col v-if="showSocialType" :xs="24" :sm="12">
            <el-form-item label="社保口径">
              <el-select v-model="templateForm.socialType" clearable style="width: 100%">
                <el-option label="有社保" value="有社保" />
                <el-option label="无社保" value="无社保" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col v-if="showPostLevelScope" :xs="24" :sm="12">
            <el-form-item label="岗位等级范围">
              <el-input v-model="templateForm.postLevelScope" placeholder="如 2-4、5-6、7-8" />
            </el-form-item>
          </el-col>
          <el-col v-if="showSalaryVersion" :xs="24" :sm="12">
            <el-form-item label="薪酬版本">
              <el-select v-model="templateForm.salaryVersion" clearable style="width: 100%">
                <el-option label="甲版" value="A" />
                <el-option label="乙版" value="B" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="模板文件" prop="fileUrl">
          <file-upload v-model="templateForm.fileUrl" :limit="1" :file-size="20" :file-type="selectedTemplateFileTypes" />
          <div class="form-tip">当前类型必填占位符：{{ requiredPlaceholdersText || '-' }}</div>
        </el-form-item>
        <el-form-item label="员工可见">
          <el-radio-group v-model="templateForm.employeeVisible" :disabled="templateVisibilityLocked" @change="handleTemplateVisibilityChange">
            <el-radio label="Y">员工可见</el-radio>
            <el-radio label="N">仅人力资源内部</el-radio>
          </el-radio-group>
          <div v-if="templateVisibilityLocked" class="form-tip">该文件类型固定为人力资源内部材料，不会发送到员工端。</div>
        </el-form-item>
        <el-form-item label="阅读确认">
          <el-radio-group v-model="templateForm.readConfirmationRequired" :disabled="templateForm.employeeVisible !== 'Y' || templateForm.employeeSignRequired === 'Y'">
            <el-radio label="Y">需要</el-radio>
            <el-radio label="N">不需要</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="员工签署">
          <el-radio-group v-model="templateForm.employeeSignRequired" :disabled="templateForm.employeeVisible !== 'Y'" @change="handleTemplateSignRequiredChange">
            <el-radio label="Y">需要</el-radio>
            <el-radio label="N">不需要</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="企业章" prop="companySealRequired">
          <el-radio-group v-model="templateForm.companySealRequired" :disabled="templateForm.employeeVisible !== 'Y'" @change="handleTemplateSealRequiredChange">
            <el-radio label="Y">需要盖章</el-radio>
            <el-radio label="N">无需盖章</el-radio>
          </el-radio-group>
          <div class="form-tip">员工可见文件必须显式选择，系统不会根据坐标猜测是否盖章。</div>
        </el-form-item>
        <el-form-item v-if="templateForm.companySealRequired === 'Y'" label="印章定位" prop="companySealPositionJson">
          <el-input
            v-model.trim="templateForm.companySealPositionJson"
            type="textarea"
            :rows="4"
            maxlength="500"
            show-word-limit
            placeholder='{"mode":"APPENDED_CONFIRMATION_PAGE"}'
          />
          <div class="form-tip">APPENDED_CONFIRMATION_PAGE 会把员工签名、企业章和校验证据放在同一张追加确认页，不依赖 Word 页码或坐标。LAST_PAGE 用于渲染后最后一页；固定页可用 PLACED。</div>
        </el-form-item>
        <el-form-item label="状态">
          <el-radio-group v-model="templateForm.status">
            <el-radio label="0">启用</el-radio>
            <el-radio label="1">停用</el-radio>
          </el-radio-group>
          <div v-if="!templateForm.templateId" class="form-tip">新模板首次保存固定为停用；复核文件、占位符和公司名称占位符后，再次编辑方可启用。</div>
        </el-form-item>
      </el-form>
      <div slot="footer">
        <el-button @click="templateOpen = false">取 消</el-button>
        <el-button type="primary" :loading="savingTemplate" @click="submitTemplate" v-hasPermi="['oa:signPackage:template']">保 存</el-button>
      </div>
    </el-dialog>

    <el-dialog title="选择公司并加盖印章" :visible.sync="finalizeOpen" width="720px" append-to-body>
      <div v-loading="finalizeLoading">
        <el-alert
          v-if="automaticCompany"
          :title="'系统根据部门“' + (automaticCompany.sourceDeptName || '员工归属部门') + '”识别为：' + automaticCompany.legalEntityName"
          type="success"
          show-icon
          :closable="false"
          class="detail-block"
        />
        <el-alert
          v-else
          title="当前部门尚未绑定公司，请手工选择；建议随后到“部门管理”补充部门与公司的绑定。"
          type="warning"
          show-icon
          :closable="false"
          class="detail-block"
        />
        <el-alert
          v-if="hasExcelCompanyRecommendation"
          :title="'Excel 建议公司：' + finalizeOptions.recommendedLegalEntityName"
          :description="excelCompanyRecommendationDescription"
          type="info"
          show-icon
          :closable="false"
          class="detail-block"
        />
        <el-alert
          v-if="excelCompanyRecommendationMismatch"
          title="当前选择的法律主体与 Excel 建议公司不一致，请 HR 核对后再生成最终合同。"
          type="warning"
          show-icon
          :closable="false"
          class="detail-block"
        />
        <el-form ref="finalizeForm" :model="finalizeForm" :rules="finalizeRules" label-width="110px" size="small">
          <el-form-item label="合同公司" prop="legalEntityId">
            <el-select v-model="finalizeForm.legalEntityId" filterable placeholder="请选择公司" style="width:100%" @change="handleFinalizeCompanyChange">
              <el-option v-for="company in finalizeCompanies" :key="company.legalEntityId" :label="company.legalEntityName" :value="company.legalEntityId" />
            </el-select>
          </el-form-item>
          <el-descriptions v-if="selectedFinalizeCompany" :column="2" border size="small" class="detail-block">
            <el-descriptions-item label="公司法定全称">{{ selectedFinalizeCompany.legalEntityName }}</el-descriptions-item>
            <el-descriptions-item label="统一社会信用代码">{{ selectedFinalizeCompany.unifiedSocialCreditCode || '未维护' }}</el-descriptions-item>
            <el-descriptions-item label="法定代表人">{{ selectedFinalizeCompany.legalRepresentative || '未维护' }}</el-descriptions-item>
            <el-descriptions-item label="联系电话">{{ selectedFinalizeCompany.contactPhone || '未维护' }}</el-descriptions-item>
            <el-descriptions-item label="注册地址" :span="2">{{ selectedFinalizeCompany.registeredAddress || '未维护' }}</el-descriptions-item>
          </el-descriptions>
          <el-form-item v-if="finalizeRequiresSeal" label="合同印章" prop="sealId">
            <el-select v-model="finalizeForm.sealId" placeholder="请选择该公司印章" style="width:100%" :disabled="!finalizeForm.legalEntityId">
              <el-option v-for="seal in finalizeSeals" :key="seal.sealId" :label="seal.sealName + (seal.isDefault === 'Y' ? '（默认）' : '')" :value="seal.sealId" />
            </el-select>
            <div v-if="finalizeForm.legalEntityId && !finalizeLoading && !finalizeSeals.length" class="form-tip form-tip-danger">
              该公司尚无可用合同印章，请先到“公司与印章”页签配置。
            </div>
          </el-form-item>
          <el-alert
            v-else
            title="当前文件快照均明确为无需盖章，本次只固定合同公司信息。"
            type="info"
            show-icon
            :closable="false"
            class="detail-block"
          />
          <el-form-item v-if="companyWasManuallyChanged" label="改选说明">
            <el-input v-model.trim="finalizeForm.correctionReason" type="textarea" :rows="2" maxlength="500" show-word-limit placeholder="可填写改选公司的原因；留空时系统会自动记录为人工更正" />
          </el-form-item>
          <el-alert
            :title="finalizeRequiresSeal
              ? '生成最终合同后，员工原手写签名会原样沿用，员工只需阅读并确认公司及印章，不会再次签名。'
              : '生成最终合同后，员工原手写签名会原样沿用，员工只需阅读并确认合同公司，不会再次签名。'"
            type="info"
            show-icon
            :closable="false"
          />
        </el-form>
      </div>
      <div slot="footer">
        <el-button @click="finalizeOpen = false">取消</el-button>
        <el-button v-if="finalizeRequiresSeal && finalizeForm.legalEntityId && !finalizeSeals.length" @click="goCompanyManagement">配置公司印章</el-button>
        <el-button type="primary" :loading="finalizeSaving" :disabled="!finalizeForm.legalEntityId || (finalizeRequiresSeal && !finalizeForm.sealId)" @click="submitFinalize">生成最终合同</el-button>
      </div>
    </el-dialog>

    <el-drawer title="签约包详情" :visible.sync="detailOpen" size="760px" append-to-body>
      <div v-if="detail" class="detail-drawer">
        <el-steps :active="packageStatusStep(detail)" finish-status="success" simple>
          <el-step title="草稿" />
          <el-step title="已发送" />
          <el-step title="首次签名" />
          <el-step title="公司盖章" />
          <el-step title="员工确认" />
        </el-steps>
        <div class="detail-actions">
          <el-button type="primary" size="mini" icon="el-icon-circle-check" :loading="verificationLoading" @click="handleVerify(false)">验证文件</el-button>
          <el-button v-if="isPreparedSignatureFirstFinalPackage(detail)" type="success" size="mini" icon="el-icon-s-promotion" @click="openPreparedFinalTask(detail)" v-hasPermi="['oa:signTask:send']">预览并发送最终文件</el-button>
          <el-button v-else-if="detail.status === 'pending_company'" type="success" size="mini" icon="el-icon-s-claim" @click="handleOpenFinalize(detail)" v-hasPermi="['oa:signTask:batchFinalize', 'oa:signPackage:send']">{{ pendingCompanyActionLabel(detail) }}</el-button>
        </div>
        <sign-package-exception-panel
          v-if="['refused', 'expired'].includes(detail.status) || detail.resolutionStatus"
          :sign-package="detail"
          class="detail-block"
        />
        <el-alert
          v-if="verificationResult"
          :title="verificationMessage(verificationResult.status)"
          :type="verificationAlertType(verificationResult.status)"
          show-icon
          :closable="false"
          class="detail-block"
        />
        <el-collapse v-if="canViewTechnicalEvidence" v-model="technicalEvidenceOpen" class="detail-block">
          <el-collapse-item title="技术证据（仅系统管理员）" name="technical">
            <el-button size="mini" :loading="verificationLoading" @click="handleVerify(true)">加载技术证据</el-button>
            <el-table v-if="verificationResult && verificationResult.technicalEvidence" :data="verificationResult.technicalEvidence" border size="mini" class="technical-evidence-table">
              <el-table-column label="文件类型" width="160"><template slot-scope="scope">{{ evidenceTypeLabel(scope.row.evidenceType) }}</template></el-table-column>
              <el-table-column label="归档路径" prop="fileUrl" min-width="220" show-overflow-tooltip />
              <el-table-column label="登记校验值" prop="expectedHash" min-width="220" show-overflow-tooltip />
              <el-table-column label="实际校验值" prop="actualHash" min-width="220" show-overflow-tooltip />
            </el-table>
          </el-collapse-item>
        </el-collapse>
        <el-descriptions :column="2" border size="small" class="detail-block">
          <el-descriptions-item label="员工">{{ detail.employeeNameSnapshot }}</el-descriptions-item>
          <el-descriptions-item label="状态">{{ packageStatusLabel(detail) }}</el-descriptions-item>
          <el-descriptions-item label="场景">{{ scenarioLabel(detail.scenario) }}</el-descriptions-item>
          <el-descriptions-item label="用工">{{ dictionaryLabel(detail.employmentType) }}</el-descriptions-item>
          <el-descriptions-item label="岗位">{{ detail.postNameSnapshot || '-' }}</el-descriptions-item>
          <el-descriptions-item label="社保">{{ dictionaryLabel(detail.socialType) }}</el-descriptions-item>
          <el-descriptions-item label="合同公司">{{ detail.legalEntityNameSnapshot || '员工首次签名后确定' }}</el-descriptions-item>
          <el-descriptions-item label="合同印章">{{ detail.sealNameSnapshot || '员工首次签名后确定' }}</el-descriptions-item>
          <el-descriptions-item v-if="detail.scenario === 'renewal' || detail.scenario === 'renew'" label="续签次数">
            {{ detail.previousRenewalCount == null ? '-' : detail.previousRenewalCount }} → {{ detail.renewalCount == null ? '-' : detail.renewalCount }}
          </el-descriptions-item>
        </el-descriptions>
        <el-table :data="detail.documents || []" border size="small" class="detail-block">
          <el-table-column label="文件清单" prop="documentName" min-width="180" />
          <el-table-column label="类型" prop="templateType" min-width="160">
            <template slot-scope="scope">{{ templateTypeLabel(scope.row.templateType) }}</template>
          </el-table-column>
          <el-table-column label="必签" prop="employeeSignRequired" width="70">
            <template slot-scope="scope">{{ scope.row.employeeSignRequired === 'Y' ? '是' : '否' }}</template>
          </el-table-column>
          <el-table-column label="已读" prop="readConfirmed" width="70">
            <template slot-scope="scope">{{ scope.row.readConfirmed === 'Y' ? '是' : '否' }}</template>
          </el-table-column>
          <el-table-column label="已签" prop="signed" width="70">
            <template slot-scope="scope">{{ scope.row.signed === 'Y' ? '是' : '否' }}</template>
          </el-table-column>
          <el-table-column label="文件" min-width="210">
            <template slot-scope="scope">
              <el-link v-if="scope.row.reviewPdfUrl || scope.row.generatedPdfUrl || scope.row.generatedFileUrl" type="primary" @click="openGeneratedDocument(scope.row)">阅读原文</el-link>
              <el-link v-if="scope.row.signedPdfUrl" type="success" @click="openSignedDocument(scope.row)">已签文件</el-link>
              <el-link v-if="scope.row.finalPdfUrl" type="success" @click="openFinalDocument(scope.row)">最终合同</el-link>
              <el-button
                v-if="canExportFinalDocument(scope.row)"
                type="text"
                :loading="finalExportingDocumentId === String(scope.row.documentId)"
                :disabled="finalExportingDocumentId === String(scope.row.documentId)"
                @click="exportFinalDocument(scope.row)"
              >导出签章展示版</el-button>
              <span v-if="canExportFinalDocument(scope.row)" class="final-export-hint">签章展示版用于查看签名和盖章位置，原最终归档不变</span>
              <el-link v-if="scope.row.certificateFileUrl" type="primary" @click="openGeneratedCertificate(scope.row)">签署证明</el-link>
            </template>
          </el-table-column>
        </el-table>
      </div>
    </el-drawer>
    </template>
    <el-empty v-else description="当前账号无权访问签约资料维护" :image-size="82" />
  </div>
</template>

<script>
import {
  createSignPackageDrafts,
  createSignPackage,
  downloadFinalSignPackageDocument,
  downloadFinalSignPackageDocumentExport,
  downloadSignPackageCertificate,
  downloadSignPackageDocument,
  downloadSignedSignPackageDocument,
  downloadSignTemplateFile,
  finalizeSignPackage,
  getSignPlan,
  getSignPackage,
  getSignPackageCompanyOptions,
  listSignPlans,
  listSignPackages,
  listSignTemplateTypes,
  listSignTemplates,
  matchSignTemplates,
  publishSignPlan,
  previewPublishSignPlan,
  previewSignPackageBatch,
  previewSignTemplateFile,
  saveSignPlan,
  saveSignTemplate,
  sendSignPackage,
  updateSignPackage,
  updateSignPlan,
  verifySignPackage,
  voidSignPackage
} from "@/api/oa/signPackage"
import CompanySealManagement from "./CompanySealManagement.vue"
import SignPackageExceptionPanel from "./SignPackageExceptionPanel.vue"
import SignPackageRecordPanel from "./SignPackageRecordPanel.vue"
import SignScopeSelector from "@/components/SignScopeSelector"
import { listUser } from "@/api/system/user"
import { getSignTask } from "@/api/oa/signTask"
import { getBusinessEmptyText } from "@/utils/businessEmptyState"
import { blobValidate } from "@/utils/common"
import { checkPermi } from "@/utils/permission"
import { getSelectedSignScopeContext, getSelectedSignScopeDeptId } from "@/utils/signScopeContext"
import {
  SIGN_PACKAGE_STATUS_LABELS,
  signDictionaryLabel,
  signPackageStatusLabel
} from "@/utils/signDictionary"
import { signPlaceholderToken } from "@/utils/signPlaceholder"
const { createUiOperationScope } = require("@/utils/uiOperationScope")
const { signVersionLabel } = require("@/utils/signDisplayText")
const { formatSignDateTimeWithSeconds } = require("@/utils/signDateTime")
const {
  isValidSignImagePlacementJson,
  normalizeSignImagePlacementJson
} = require("@/utils/signPlacementPolicy")
const { validatePdfBlob } = require("@/utils/signPackageFileGate")
const {
  SIGN_PACKAGE_SCENARIO_OPTIONS,
  packageScenarioHasContractDates,
  packageScenarioHasEntryDate,
  packageScenarioHasProbationDates,
  packageScenarioHasSalary,
  normalizePackageScenario,
  signScenarioLabel
} = require("@/utils/signScenario")

const {
  DEFAULT_TEMPLATE_TYPE_OPTIONS,
  decorateTemplateTypeOption,
  isInternalTemplateType,
  isLaborEmploymentTemplateType,
  isPostLevelScopedTemplateType,
  isSalaryVersionTemplateType,
  isServiceEmploymentTemplateType
} = require("./signTemplateCatalog")

const SIGN_EMERGENCY_CREATE_ENABLED =
  String(process.env.VUE_APP_SIGN_EMERGENCY_CREATE_ENABLED || "false").toLowerCase() === "true"

export default {
  name: "OaSignPackage",
  components: { CompanySealManagement, SignPackageExceptionPanel, SignPackageRecordPanel, SignScopeSelector },
  data() {
    return {
      pageAuthorized: false,
      activeTab: "package",
      loading: false,
      templateLoading: false,
      planLoading: false,
      matching: false,
      savingPackage: false,
      savingTemplate: false,
      savingPlan: false,
      batchPreviewLoading: false,
      batchCreating: false,
      total: 0,
      packageList: [],
      templateList: [],
      planList: [],
      planTemplateOptions: [],
      batchPlanOptions: [],
      batchPreviewRows: [],
      templateTypeOptions: DEFAULT_TEMPLATE_TYPE_OPTIONS,
      packageScenarioOptions: SIGN_PACKAGE_SCENARIO_OPTIONS,
      packageScenarioFilterOptions: [
        ...SIGN_PACKAGE_SCENARIO_OPTIONS,
        { value: "change", label: "合同变更" }
      ],
      employeeOptions: [],
      matchedTemplateList: [],
      queryParams: { pageNum: 1, pageSize: 10 },
      templateQuery: {},
      planQuery: {},
      batchForm: { planId: null, postName: "", keyword: "", emergencyReason: "" },
      packageOpen: false,
      templateOpen: false,
      planOpen: false,
      publishOpen: false,
      batchOpen: false,
      detailOpen: false,
      finalizeOpen: false,
      finalizeLoading: false,
      finalizeSaving: false,
      finalizePackage: null,
      finalizeOptions: {},
      finalizeCompanies: [],
      finalizeSeals: [],
      finalizeForm: {
        legalEntityId: null,
        sealId: null,
        correctionReason: "",
        requestId: "",
        expectedVersion: null,
        expectedTaskVersion: null,
        signingSequence: "SIGNATURE_FIRST"
      },
      packageTitle: "新建签约包",
      templateTitle: "新增模板",
      planTitle: "新增方案",
      publishDetail: null,
      publishPreview: null,
      publishTargetId: "",
      publishPreviewLoading: false,
      publishError: "",
      publishing: false,
      detail: null,
      verificationLoading: false,
      finalExportingDocumentId: null,
      verificationResult: null,
      technicalEvidenceOpen: [],
      packageForm: this.defaultPackageForm(),
      templateForm: this.defaultTemplateForm(),
      planForm: this.defaultPlanForm(),
      planTemplateIds: [],
      statusOptions: Object.entries(SIGN_PACKAGE_STATUS_LABELS)
        .map(([value, label]) => ({ value, label })),
      packageRules: {
        employeeId: [{ required: true, message: "请选择员工", trigger: "change" }],
        employeeNameSnapshot: [{ required: true, message: "请输入员工姓名", trigger: "blur" }],
        employeePhoneSnapshot: [{ required: true, message: "请输入手机号", trigger: "blur" }],
        employeeIdCardSnapshot: [{ required: true, message: "请输入身份证号", trigger: "blur" }],
        scenario: [{ required: true, message: "请选择场景", trigger: "change" }]
      },
      finalizeRules: {
        legalEntityId: [{ required: true, message: "请选择合同公司", trigger: "change" }]
      },
      templateRules: {
        templateType: [{ required: true, message: "请选择文件类型", trigger: "change" }],
        templateName: [{ required: true, message: "请输入模板名称", trigger: "blur" }],
        fileUrl: [{ required: true, message: "请上传模板文件", trigger: "change" }],
        companySealRequired: [{ required: true, message: "请明确选择是否需要企业章", trigger: "change" }]
      },
      planRules: {
        planName: [{ required: true, message: "请输入方案名", trigger: "blur" }],
        scenario: [{ required: true, message: "请选择场景", trigger: "change" }],
        legalEntityId: [{ required: true, message: "请输入稳定法律主体ID", trigger: "change" }],
        legalEntityName: [{ required: true, message: "请输入法律主体名称", trigger: "blur" }]
      },
      batchRules: {
        planId: [{ required: true, message: "请选择签约方案", trigger: "change" }],
        emergencyReason: [{ required: true, message: "请填写应急建包原因", trigger: "blur" }]
      }
    }
  },
  computed: {
    reminderDayOptions() {
      const deadlineDays = Number(this.planForm.signDeadlineDays)
      const maximum = Number.isInteger(deadlineDays) && deadlineDays > 0
        ? Math.min(30, deadlineDays) : 0
      return Array.from({ length: maximum }, (_, index) => index + 1)
    },
    selectedTemplateType() {
      return this.templateTypeOptions.find(item => item.code === this.templateForm.templateType) || null
    },
    filteredPlanTemplateOptions() {
      const planScenario = normalizePackageScenario(this.planForm.scenario)
      if (!planScenario) return []
      return this.planTemplateOptions.filter(template => {
        const type = this.templateTypeOptions.find(item => item.code === template.templateType)
        if (!type || normalizePackageScenario(type.scenario) !== planScenario) return false
        if (planScenario === "onboard" && this.planForm.employmentType === "劳动合同" &&
          template.templateType === "ONBOARD_SALARY_CONFIRM" && this.planForm.salaryVersion) {
          return String(template.salaryVersion || "").toUpperCase() ===
            String(this.planForm.salaryVersion).toUpperCase()
        }
        return true
      })
    },
    publishTemplateRows() {
      const bindings = (this.publishDetail && this.publishDetail.templates) || []
      return bindings.map(binding => binding.template || binding).filter(Boolean)
    },
    publishSummary() {
      return this.publishTemplateRows.reduce((summary, template) => {
        if (template.employeeVisible === "N") summary.internal += 1
        else summary.employeeVisible += 1
        if (template.employeeSignRequired === "Y") summary.signing += 1
        if (template.companySealRequired === "Y") summary.sealing += 1
        return summary
      }, { employeeVisible: 0, internal: 0, signing: 0, sealing: 0 })
    },
    publishBlockingReasons() {
      const detail = this.publishDetail
      if (!detail) return ["方案信息尚未加载"]
      const reasons = []
      if (detail.status !== "0") reasons.push("源方案已停用")
      if (!this.publishTemplateRows.length) reasons.push("未绑定模板")
      if (!this.publishTemplateRows.some(template => template.employeeSignRequired === "Y")) {
        reasons.push("没有需要员工签署的文件")
      }
      this.publishTemplateRows.filter(template => template.employeeVisible === "Y").forEach(template => {
        const label = template.templateName || this.templateTypeLabel(template.templateType)
        if (!["Y", "N"].includes(template.companySealRequired)) {
          reasons.push(label + "未明确选择是否需要企业章")
        } else if (template.companySealRequired === "Y" &&
          !this.isValidCompanySealPlacement(template.companySealPositionJson)) {
          reasons.push(label + "缺少有效的印章定位")
        } else if (template.companySealRequired === "N" && template.companySealPositionJson) {
          reasons.push(label + "无需盖章但仍携带印章坐标")
        }
      })
      return reasons
    },
    templateVisibilityLocked() {
      return this.selectedTemplateType && (this.selectedTemplateType.employeeVisible === false ||
        isInternalTemplateType(this.selectedTemplateType.code))
    },
    packageEmptyText() {
      return getBusinessEmptyText("signPackage", "missingBaseline")
    },
    templateEmptyText() {
      return getBusinessEmptyText("signTemplate", "missingBaseline")
    },
    planEmptyText() {
      return "暂无签约方案"
    },
    batchEmptyText() {
      return "请选择签约方案后预览"
    },
    requiredPlaceholders() {
      return this.selectedTemplateType ? this.selectedTemplateType.requiredPlaceholders || [] : []
    },
    requiredPlaceholdersText() {
      return this.requiredPlaceholders.map(signPlaceholderToken).join("、")
    },
    selectedTemplateFileTypes() {
      return this.selectedTemplateType && this.selectedTemplateType.fileFormat === "xlsx" ? ["xlsx"] : ["docx"]
    },
    showEmploymentType() {
      return isLaborEmploymentTemplateType(this.templateForm.templateType) ||
        isServiceEmploymentTemplateType(this.templateForm.templateType)
    },
    showSocialType() {
      return this.templateForm.templateType === "ONBOARD_LABOR_CONTRACT"
    },
    showPostLevelScope() {
      return isPostLevelScopedTemplateType(this.templateForm.templateType)
    },
    showSalaryVersion() {
      return isSalaryVersionTemplateType(this.templateForm.templateType)
    },
    showPackageEntryDate() {
      return packageScenarioHasEntryDate(this.packageForm.scenario)
    },
    showPackageContractFields() {
      return packageScenarioHasContractDates(this.packageForm.scenario)
    },
    showPackageRenewalFields() {
      return normalizePackageScenario(this.packageForm.scenario) === "renewal"
    },
    showPackageProbationFields() {
      return packageScenarioHasProbationDates(this.packageForm.scenario)
    },
    showPackageServiceFields() {
      return this.packageForm.employmentType === "劳务合同"
    },
    packageOnboardLabor() {
      return normalizePackageScenario(this.packageForm.scenario) === "onboard" &&
        this.packageForm.employmentType === "劳动合同"
    },
    showPackageSalaryFields() {
      return packageScenarioHasSalary(this.packageForm.scenario)
    },
    showPackageOffboardFields() {
      return this.packageForm.scenario === "offboard"
    },
    showPlanEntryDate() {
      return packageScenarioHasEntryDate(this.planForm.scenario)
    },
    showPlanContractFields() {
      return packageScenarioHasContractDates(this.planForm.scenario)
    },
    showPlanProbationFields() {
      return packageScenarioHasProbationDates(this.planForm.scenario)
    },
    planOnboardLabor() {
      return normalizePackageScenario(this.planForm.scenario) === "onboard" &&
        this.planForm.employmentType === "劳动合同"
    },
    showPlanSalaryFields() {
      return packageScenarioHasSalary(this.planForm.scenario)
    },
    canViewTechnicalEvidence() {
      return checkPermi(["oa:signTask:technicalEvidence"])
    },
    canViewCompanySealManagement() {
      return checkPermi(["oa:signCompany:list"])
    },
    emergencyCreateEnabled() {
      return SIGN_EMERGENCY_CREATE_ENABLED
    },
    automaticCompany() {
      return this.finalizeOptions.automaticCandidate || null
    },
    selectedFinalizeCompany() {
      return this.finalizeCompanies.find(company =>
        String(company.legalEntityId) === String(this.finalizeForm.legalEntityId)) || null
    },
    companyWasManuallyChanged() {
      return !!this.finalizeForm.legalEntityId && (!this.automaticCompany ||
        String(this.automaticCompany.legalEntityId) !== String(this.finalizeForm.legalEntityId))
    },
    hasExcelCompanyRecommendation() {
      return !!String(this.finalizeOptions.recommendedLegalEntityName || "").trim()
    },
    excelCompanyRecommendationDescription() {
      const details = []
      if (this.finalizeOptions.recommendedLegalRepresentative) {
        details.push(`法定代表人：${this.finalizeOptions.recommendedLegalRepresentative}`)
      }
      if (this.finalizeOptions.recommendedRegisteredAddress) {
        details.push(`注册地址：${this.finalizeOptions.recommendedRegisteredAddress}`)
      }
      details.push("仅供核对，不会自动新建、修改或切换公司。")
      return details.join("；")
    },
    excelCompanyRecommendationMismatch() {
      if (!this.hasExcelCompanyRecommendation || !this.selectedFinalizeCompany) return false
      const normalize = value => String(value || "").replace(/\s+/g, "").toLowerCase()
      return normalize(this.selectedFinalizeCompany.legalEntityName) !==
        normalize(this.finalizeOptions.recommendedLegalEntityName)
    },
    finalizeRequiresSeal() {
      return this.finalizeOptions.companySealRequired !== false
    }
  },
  created() {
    return this.bootstrapSignPackagePage()
  },
  beforeDestroy() {
    if (this._publishOperationScope) this._publishOperationScope.deactivate()
  },
  deactivated() {
    if (this._publishOperationScope) this._publishOperationScope.deactivate()
    this.publishOpen = false
  },
  activated() {
    if (this._publishOperationScope) this._publishOperationScope.activate()
  },
  watch: {
    publishOpen(value) {
      if (!value && this._publishOperationScope) this._publishOperationScope.invalidate()
    },
    "$store.state.user.sessionRevision"() {
      if (this._publishOperationScope) this._publishOperationScope.invalidate()
      this.publishOpen = false
    },
    "$route.fullPath"() {
      if (this._publishOperationScope) this._publishOperationScope.invalidate()
      this.publishOpen = false
    },
    activeTab(newValue) {
      if (newValue === "template") {
        this.getTemplates()
      } else if (newValue === "plan") {
        this.getPlans()
        this.ensurePlanTemplateOptions()
      }
    }
  },
  methods: {
    hasSignPackagePagePermission() {
      if (!this.$auth || typeof this.$auth.hasPermi !== "function") {
        return false
      }
      try {
        return this.$auth.hasPermi("oa:signPackage:list") === true
      } catch (error) {
        return false
      }
    },
    bootstrapSignPackagePage() {
      this.pageAuthorized = this.hasSignPackagePagePermission()
      if (!this.pageAuthorized) {
        return Promise.resolve(false)
      }
      this.getTemplateTypes()
      this.getList()
      const employeeRequest = this.loadEmployees()
      this.openPackageFromRoute(employeeRequest)
      return Promise.resolve(true)
    },
    displaySignDateTime(value) {
      return formatSignDateTimeWithSeconds(value)
    },
    handleSignScopeReady(option) {
      if (!option || !option.deptId) return
      this.queryParams.pageNum = 1
      this.getList()
    },
    handleSignScopeChange() {
      this.packageOpen = false
      this.planOpen = false
      this.publishOpen = false
      this.detailOpen = false
      this.finalizeOpen = false
      this.detail = null
      this.verificationResult = null
      if (this.activeTab === "record") this.activeTab = "package"
      this.queryParams.pageNum = 1
      this.getList()
    },
    openPackageFromRoute(employeeRequest = Promise.resolve()) {
      const packageId = String(this.$route.query.packageId || '')
      if (/^[1-9]\d{0,18}$/.test(packageId)) {
        getSignPackage(packageId).then(response => {
          const signPackage = response.data || { packageId }
          if (this.$route.query.action === 'finalize' || signPackage.status === 'pending_company') {
            this.handleOpenFinalize(signPackage)
          } else if (signPackage.status === 'draft') {
            this.handleEditPackage(signPackage)
          } else {
            this.handleDetail(signPackage)
          }
        }).catch(() => {
          this.$modal.msgError('签约包资料加载失败，请返回合同签约中心后重试')
        })
        return
      }
      const taskId = String(this.$route.query.taskId || '')
      if (!/^[1-9]\d{0,18}$/.test(taskId)) return
      Promise.all([getSignTask(taskId), employeeRequest.catch(() => null)]).then(([response]) => {
        const detail = response.data || {}
        const task = detail.task || {}
        if (task.packageId) {
          const signPackage = Object.assign({}, detail.signPackage || {
            packageId: task.packageId,
            status: task.status
          }, {
            taskId: task.taskId || (detail.signPackage && detail.signPackage.taskId),
            taskSourceType: task.sourceType || task.taskSourceType || ''
          })
          if (task.status === 'PENDING_COMPANY') this.handleOpenFinalize(signPackage)
          else if (signPackage.status === 'draft') this.handleEditPackage(signPackage)
          else this.handleDetail(signPackage)
          return
        }
        this.packageTitle = "创建任务草稿并补资料"
        this.packageForm = Object.assign({}, this.defaultPackageForm(), {
          taskId: task.taskId,
          employeeId: task.employeeId,
          scenario: String(task.scenario || 'onboard').toLowerCase(),
          shopDeptId: task.shopDeptId,
          signDeadline: task.signDeadline
        })
        const employee = this.employeeOptions.find(item => item.userId === task.employeeId)
        if (employee) {
          this.handleEmployeeChange(task.employeeId)
        } else if (task.employeeId) {
          this.employeeOptions.unshift({ userId: task.employeeId, userName: `员工 #${task.employeeId}` })
        }
        this.matchedTemplateList = []
        this.packageOpen = true
        this.$nextTick(() => this.$refs.packageForm && this.$refs.packageForm.clearValidate())
        this.matchTemplates()
      }).catch(() => {
        this.$modal.msgError('任务资料加载失败，请返回签约中心后重试')
      })
    },
    defaultPackageForm() {
      return {
        scenario: "onboard",
        employmentType: "劳动合同",
        socialType: "有社保",
        salaryVersion: "B",
        status: "draft"
      }
    },
    defaultTemplateForm() {
      return {
        templateType: "ONBOARD_LABOR_CONTRACT",
        templateName: "",
        templateVersion: "",
        scenario: "onboard",
        fileUrl: "",
        employeeVisible: "Y",
        readConfirmationRequired: "Y",
        employeeSignRequired: "Y",
        companySealRequired: "",
        companySealPositionJson: "",
        status: "1",
        sortOrder: 100
      }
    },
    defaultPlanForm() {
      return {
        scenario: "onboard",
        employmentType: "劳动合同",
        socialType: "有社保",
        salaryVersion: "B",
        signDeadlineDays: 7,
        reminderEnabled: false,
        reminderDaysBefore: [1],
        status: "0",
        sortOrder: 100
      }
    },
    getList() {
      this.loading = true
      listSignPackages(this.queryParams).then(response => {
        this.packageList = response.rows || []
        this.total = response.total || 0
      }).finally(() => {
        this.loading = false
      })
    },
    resetQuery() {
      this.queryParams = { pageNum: 1, pageSize: 10 }
      this.getList()
    },
    getTemplates() {
      this.templateLoading = true
      listSignTemplates(this.templateQuery).then(response => {
        this.templateList = response.data || []
      }).finally(() => {
        this.templateLoading = false
      })
    },
    getPlans() {
      this.planLoading = true
      return listSignPlans(this.planQuery).then(response => {
        this.planList = response.data || []
      }).finally(() => {
        this.planLoading = false
      })
    },
    resetPlanQuery() {
      this.planQuery = {}
      this.getPlans()
    },
    ensurePlanTemplateOptions() {
      return listSignTemplates({ status: "0" }).then(response => {
        this.planTemplateOptions = response.data || []
        this.prunePlanTemplateSelection()
      })
    },
    getTemplateTypes() {
      listSignTemplateTypes().then(response => {
        if (response.data && response.data.length) {
          this.templateTypeOptions = response.data.map(decorateTemplateTypeOption)
        }
        this.handleTemplateTypeChange(this.templateForm.templateType)
      })
    },
    loadEmployees() {
      return listUser({ pageNum: 1, pageSize: 200, status: "0" }, { silentError: true }).then(response => {
        this.employeeOptions = response.rows || []
      }).catch(() => {
        this.employeeOptions = []
      })
    },
    handleAddPackage() {
      if (!this.emergencyCreateEnabled) {
        this.$modal.msgWarning("应急建包入口未启用，请从人事生命周期任务发起签约")
        return
      }
      if (!getSelectedSignScopeDeptId()) {
        this.$modal.msgWarning("新建签约包前，请先选择公司或门店签约组织")
        return
      }
      const shopContext = getSelectedSignScopeContext()
      this.packageTitle = "新建签约包"
      this.packageForm = Object.assign({}, this.defaultPackageForm(), {
        shopDeptId: shopContext.deptId,
        shopDeptName: shopContext.deptName
      })
      this.matchedTemplateList = []
      this.packageOpen = true
      this.$nextTick(() => this.$refs.packageForm && this.$refs.packageForm.clearValidate())
      this.matchTemplates()
    },
    handleEditPackage(row) {
      const openDraft = data => {
        this.packageTitle = "编辑签约包"
        this.packageForm = Object.assign({}, this.defaultPackageForm(), data, { packageId: row.packageId })
        this.syncOnboardSalaryVersion(this.packageForm)
        this.matchedTemplateList = []
        this.packageOpen = true
        this.$nextTick(() => this.$refs.packageForm && this.$refs.packageForm.clearValidate())
        this.loadPackageTemplatePreview(this.packageForm)
      }
      getSignPackage(row.packageId).then(response => {
        openDraft(response.data || row)
      }).catch(() => {
        openDraft(row)
      })
    },
    loadPackageTemplatePreview(signPackage) {
      if (signPackage.sourcePlanId) {
        getSignPlan(signPackage.sourcePlanId).then(response => {
          const templates = (response.data && response.data.templates) || []
          this.matchedTemplateList = templates.map(item => item.template || item)
        }).catch(() => {
          this.matchedTemplateList = []
        })
        return
      }
      this.matchTemplates()
    },
    handleEmployeeChange(userId) {
      const user = this.employeeOptions.find(item => item.userId === userId)
      if (!user) return
      const profile = user.profile || {}
      this.packageForm.employeeNameSnapshot = user.nickName || user.userName
      this.packageForm.employeePhoneSnapshot = user.phonenumber || ""
      this.packageForm.employeeIdCardSnapshot = user.idCard || user.idCardNo || ""
      this.packageForm.deptIdSnapshot = user.deptId || (user.dept && user.dept.deptId)
      this.packageForm.deptNameSnapshot = user.dept && user.dept.deptName
      this.packageForm.employeeAddressSnapshot = user.address || ""
      this.packageForm.postNameSnapshot = user.postName || user.postNames || ""
      const previousLegalEntityId = user.legalEntityId || profile.legalEntityId || null
      this.packageForm.legalEntityIdSnapshot = null
      this.packageForm.legalEntityCodeSnapshot = ""
      this.packageForm.legalEntityNameSnapshot = ""
      if (normalizePackageScenario(this.packageForm.scenario) === "renewal") {
        const previousCount = user.renewalCount != null ? user.renewalCount : profile.renewalCount
        this.packageForm.previousContractEndDate = user.contractEndDate || profile.contractEndDate || null
        this.packageForm.previousEmploymentType = user.contractType || profile.contractType ||
          this.packageForm.employmentType
        this.packageForm.previousLegalEntityIdSnapshot = previousLegalEntityId
        this.packageForm.previousRenewalCount = previousCount == null ? null : Number(previousCount)
        this.packageForm.renewalCount = previousCount == null ? null : Number(previousCount) + 1
      }
    },
    clearHiddenScenarioFields(form) {
      if (!packageScenarioHasEntryDate(form.scenario)) {
        form.entryDate = null
      }
      if (!packageScenarioHasContractDates(form.scenario)) {
        form.contractStartDate = null
        form.contractEndDate = null
      }
      if (normalizePackageScenario(form.scenario) !== "renewal") {
        form.previousContractEndDate = null
        form.previousEmploymentType = null
        form.previousLegalEntityIdSnapshot = null
        form.previousRenewalCount = null
        form.renewalCount = null
      }
      if (!packageScenarioHasProbationDates(form.scenario)) {
        form.probationStartDate = null
        form.probationEndDate = null
      }
      if (!packageScenarioHasSalary(form.scenario)) {
        form.baseSalary = null
        form.postSalary = null
        form.fieldAllowance = null
        form.performanceSalary = null
        form.salaryTotal = null
      }
      if (form.scenario !== "offboard") {
        form.leaveDate = null
        form.leaveReason = null
      }
    },
    handlePackageScenarioChange() {
      this.clearHiddenScenarioFields(this.packageForm)
      this.initializeRenewalDefaults(this.packageForm)
      this.syncOnboardSalaryVersion(this.packageForm)
      this.matchTemplates()
    },
    handlePackageEmploymentTypeChange() {
      this.initializeRenewalDefaults(this.packageForm)
      this.syncOnboardSalaryVersion(this.packageForm)
      this.matchTemplates()
    },
    handlePackageSocialTypeChange() {
      this.syncOnboardSalaryVersion(this.packageForm)
      this.matchTemplates()
    },
    handlePlanSocialTypeChange() {
      this.syncOnboardSalaryVersion(this.planForm)
      const removed = this.prunePlanTemplateSelection()
      if (removed > 0) {
        this.$modal.msgWarning("已清除与当前薪酬版本不匹配的薪酬结构确认书")
      }
    },
    syncOnboardSalaryVersion(form) {
      if (!form || normalizePackageScenario(form.scenario) !== "onboard") return
      if (form.employmentType !== "劳动合同") {
        form.salaryVersion = null
        return
      }
      const socialType = String(form.socialType || "").trim().toUpperCase()
      form.salaryVersion = socialType === "有社保" || socialType === "SOCIAL_INSURED"
        ? "B" : socialType === "无社保" || socialType === "SOCIAL_UNINSURED" ? "A" : null
    },
    initializeRenewalDefaults(form) {
      if (normalizePackageScenario(form.scenario) !== "renewal") return
      if (!form.previousEmploymentType) form.previousEmploymentType = form.employmentType || null
      if (form.previousLegalEntityIdSnapshot == null) {
        form.previousLegalEntityIdSnapshot = form.legalEntityIdSnapshot || null
      }
      if (form.previousRenewalCount != null && form.renewalCount == null) {
        form.renewalCount = Number(form.previousRenewalCount) + 1
      }
    },
    handlePlanScenarioChange() {
      this.clearHiddenScenarioFields(this.planForm)
      this.syncOnboardSalaryVersion(this.planForm)
      const removed = this.prunePlanTemplateSelection()
      if (removed > 0) {
        this.$modal.msgWarning("已清除与当前场景不兼容的模板")
      }
    },
    publishOperationScope() {
      if (!this._publishOperationScope) {
        this._publishOperationScope = createUiOperationScope(() => {
          const user = (this.$store && this.$store.state && this.$store.state.user) || {}
          return { actorId: String(user.id || ""), sessionRevision: user.sessionRevision || 0,
            deptId: String(getSelectedSignScopeDeptId() || "") }
        })
      }
      return this._publishOperationScope
    },
    handlePublishPlan(row) {
      const planId = String((row && row.planId) || "")
      if (!/^[1-9]\d*$/.test(planId)) return Promise.resolve(null)
      const scope = this.publishOperationScope()
      scope.invalidate()
      this.publishTargetId = planId
      this.publishDetail = null
      this.publishPreview = null
      this.publishError = ""
      this.publishing = false
      this.publishPreviewLoading = true
      this.publishOpen = true
      const operation = scope.begin("publish-preview", planId)
      const current = () => this.publishOpen && scope.isCurrent(operation, this.publishTargetId)
      return Promise.all([getSignPlan(planId), previewPublishSignPlan(planId)]).then(([detailResponse, previewResponse]) => {
        if (!current()) return null
        const detail = detailResponse && detailResponse.data
        const preview = previewResponse && previewResponse.data
        if (!detail || String(detail.planId) !== planId || !preview || String(preview.planId) !== planId ||
            !preview.previewToken || !Array.isArray(preview.activeVersions)) {
          throw new Error("发布预览结果暂未确认，请刷新预览核对")
        }
        this.publishDetail = detail
        this.publishPreview = preview
        return preview
      }).catch(error => {
        if (current()) this.publishError = (error && error.message) || "发布预览失败，请保留当前方案后重试"
        return null
      }).finally(() => {
        if (current()) this.publishPreviewLoading = false
      })
    },
    confirmPublishPlan() {
      if (this.publishing || !this.publishDetail || !this.publishPreview || this.publishBlockingReasons.length) return Promise.resolve(null)
      const planId = this.publishTargetId
      const preview = this.publishPreview
      const scope = this.publishOperationScope()
      const operation = scope.begin("publish-write", planId)
      const current = () => this.publishOpen && scope.isCurrent(operation, this.publishTargetId)
      this.publishing = true
      this.publishError = ""
      return publishSignPlan(planId, { previewToken: preview.previewToken, restoreVersionId: preview.restoreVersionId }).then(response => {
        if (!current()) return null
        const receipt = (response && response.data) || {}
        if (String(receipt.planId) !== planId || receipt.matchingStatus !== "ENABLED" ||
            !["PUBLISHED", "UNCHANGED", "RESTORED"].includes(receipt.action) ||
            (preview.targetVersionId != null && String(receipt.versionId) !== String(preview.targetVersionId))) {
          throw new Error("发布结果暂未确认，请刷新预览核对当前启用版本")
        }
        const labels = { PUBLISHED: "发布成功", UNCHANGED: "版本内容未变化，保持启用", RESTORED: "已恢复历史版本" }
        this.$modal.msgSuccess(`${labels[receipt.action]}：V${receipt.versionNo}`)
        this.publishing = false
        this.publishOpen = false
        this.getPlans()
        return receipt
      }).catch(error => {
        if (current()) this.publishError = (error && error.message) || "发布结果暂未确认，请刷新预览核对，已有输入仍保留"
        return null
      }).finally(() => {
        if (current()) this.publishing = false
      })
    },
    prunePlanTemplateSelection() {
      const allowedIds = new Set(this.filteredPlanTemplateOptions.map(item => item.templateId))
      const before = this.planTemplateIds.length
      this.planTemplateIds = this.planTemplateIds.filter(templateId => allowedIds.has(templateId))
      return before - this.planTemplateIds.length
    },
    matchTemplates() {
      this.matching = true
      return matchSignTemplates(this.packageForm).then(response => {
        this.matchedTemplateList = response.data || []
      }).finally(() => {
        this.matching = false
      })
    },
    submitPackage() {
      this.clearHiddenScenarioFields(this.packageForm)
      this.$refs.packageForm.validate(valid => {
        if (!valid) return
        this.syncOnboardSalaryVersion(this.packageForm)
        if (normalizePackageScenario(this.packageForm.scenario) === "onboard" &&
          this.packageForm.employmentType === "劳动合同" && !this.packageForm.salaryVersion) {
          this.$modal.msgError("请选择明确的社保口径")
          return
        }
        if (!this.packageForm.taskId && !String(this.packageForm.remark || "").trim()) {
          this.$modal.msgError("应急建包必须填写非生命周期任务来源原因")
          return
        }
        this.savingPackage = true
        const request = this.packageForm.packageId
          ? updateSignPackage(this.packageForm.packageId, this.packageForm)
          : createSignPackage(this.packageForm)
        const taskId = this.packageForm.taskId
        request.then(() => {
          this.$modal.msgSuccess("保存成功")
          this.packageOpen = false
          this.getList()
          if (taskId) {
            this.$router.replace({ path: '/oa/sign-task', query: { taskId: String(taskId) } }).catch(() => {})
          }
        }).finally(() => {
          this.savingPackage = false
        })
      })
    },
    handleAddPlan() {
      this.planTitle = "新增方案"
      this.planForm = this.defaultPlanForm()
      this.planTemplateIds = []
      this.planOpen = true
      this.ensurePlanTemplateOptions()
      this.$nextTick(() => this.$refs.planForm && this.$refs.planForm.clearValidate())
    },
    handleEditPlan(row) {
      this.planTitle = "编辑方案"
      Promise.all([this.ensurePlanTemplateOptions(), getSignPlan(row.planId)]).then(([, response]) => {
        const plan = response.data || row
        const reminder = this.parseReminderPolicy(plan.reminderPolicyJson)
        this.planForm = Object.assign({}, this.defaultPlanForm(), plan, reminder)
        this.syncOnboardSalaryVersion(this.planForm)
        this.planTemplateIds = this.extractPlanTemplateIds(plan)
        const removed = this.prunePlanTemplateSelection()
        this.planOpen = true
        if (removed > 0) {
          this.$modal.msgWarning("原方案包含跨场景或未登记模板，已从本次编辑中移除")
        }
        this.$nextTick(() => this.$refs.planForm && this.$refs.planForm.clearValidate())
      })
    },
    extractPlanTemplateIds(plan) {
      return (plan.templates || []).map(item => item.templateId || (item.template && item.template.templateId)).filter(Boolean)
    },
    buildPlanPayload() {
      this.prunePlanTemplateSelection()
      const payload = Object.assign({}, this.planForm)
      this.clearHiddenScenarioFields(payload)
      payload.legalEntityId = null
      payload.legalEntityName = null
      delete payload.shopDeptId
      delete payload.shopDeptName
      const reminderDays = Array.from(new Set((payload.reminderDaysBefore || [])
        .map(day => Number(day))))
        .filter(day => Number.isInteger(day) && day >= 1 && day <= 30)
        .sort((left, right) => left - right)
      payload.reminderPolicyJson = payload.reminderEnabled
        ? JSON.stringify({ daysBefore: reminderDays })
        : "{}"
      delete payload.reminderEnabled
      delete payload.reminderDaysBefore
      payload.templates = this.planTemplateIds.map((templateId, index) => ({
        templateId,
        sortOrder: (index + 1) * 10
      }))
      return payload
    },
    submitPlan() {
      this.$refs.planForm.validate(valid => {
        if (!valid) return
        this.syncOnboardSalaryVersion(this.planForm)
        if (normalizePackageScenario(this.planForm.scenario) === "onboard" &&
          this.planForm.employmentType === "劳动合同" && !this.planForm.salaryVersion) {
          this.$modal.msgError("请选择明确的社保口径")
          return
        }
        if (!this.planTemplateIds.length) {
          this.$modal.msgError("请选择绑定模板")
          return
        }
        if (this.planForm.reminderEnabled && !(this.planForm.reminderDaysBefore || []).length) {
          this.$modal.msgError("开启截止提醒后至少选择一个提前天数")
          return
        }
        if (this.planForm.reminderEnabled && (this.planForm.reminderDaysBefore || [])
          .some(day => Number(day) > Number(this.planForm.signDeadlineDays))) {
          this.$modal.msgError("提前提醒天数不能超过签署期限")
          return
        }
        const payload = this.buildPlanPayload()
        this.savingPlan = true
        const request = payload.planId ? updateSignPlan(payload) : saveSignPlan(payload)
        request.then(() => {
          this.$modal.msgSuccess("保存成功")
          this.planOpen = false
          this.getPlans()
        }).finally(() => {
          this.savingPlan = false
        })
      })
    },
    handleReminderDeadlineChange(value) {
      const deadlineDays = Number(value)
      if (!Number.isInteger(deadlineDays) || deadlineDays < 1) return
      this.planForm.reminderDaysBefore = (this.planForm.reminderDaysBefore || [])
        .filter(day => Number(day) <= deadlineDays)
    },
    handleOpenBatch() {
      if (!this.emergencyCreateEnabled) {
        this.$modal.msgWarning("应急批量建包入口未启用")
        return
      }
      this.batchForm = { planId: null, postName: "", keyword: "", emergencyReason: "" }
      this.batchPreviewRows = []
      this.batchOpen = true
      this.loadBatchPlanOptions().then(() => {
        if (!this.batchForm.planId && this.batchPlanOptions.length) {
          this.batchForm.planId = this.batchPlanOptions[0].planId
          this.handleBatchPlanChange(this.batchForm.planId)
        }
      })
      this.$nextTick(() => this.$refs.batchForm && this.$refs.batchForm.clearValidate())
    },
    loadBatchPlanOptions() {
      return listSignPlans({ status: "0" }).then(response => {
        this.batchPlanOptions = response.data || []
      })
    },
    handleBatchPlanChange(planId) {
      const plan = this.batchPlanOptions.find(item => item.planId === planId)
      this.batchForm.postName = plan && plan.postName ? plan.postName : ""
      this.batchPreviewRows = []
    },
    handlePreviewBatch() {
      this.$refs.batchForm.validate(valid => {
        if (!valid) return
        this.batchPreviewLoading = true
        previewSignPackageBatch(this.batchForm).then(response => {
          this.batchPreviewRows = (response.data || []).map(row => Object.assign({}, row, {
            selected: row.selected !== false && row.creatable !== false
          }))
        }).finally(() => {
          this.batchPreviewLoading = false
        })
      })
    },
    refreshBatchRowState(row) {
      const fields = (row.missingFields || []).filter(field => field !== "身份证号")
      if (!row.employeeIdCardSnapshot) {
        fields.push("身份证号")
      }
      row.missingFields = Array.from(new Set(fields))
      if (row.missingFields.length && !row.skipReason) {
        row.skipReason = "资料不完整"
      }
      if (!row.missingFields.length && row.skipReason === "资料不完整") {
        row.skipReason = ""
      }
      row.creatable = !row.missingFields.length && !row.skipReason
      row.selected = row.creatable
    },
    handleCreateDrafts() {
      this.$refs.batchForm.validate(valid => {
        if (!valid) return
        this.batchCreating = true
        createSignPackageDrafts({
          planId: this.batchForm.planId,
          emergencyReason: this.batchForm.emergencyReason,
          rows: this.batchPreviewRows
        }).then(response => {
          const result = response.data || {}
          const createdCount = result.createdCount || 0
          const skippedCount = result.skippedCount || 0
          this.$modal.msgSuccess("已创建" + createdCount + "个草稿，跳过" + skippedCount + "行")
          this.getList()
          this.activeTab = "package"
          this.batchPreviewRows = result.skippedRows || []
          this.batchOpen = this.batchPreviewRows.length > 0
        }).finally(() => {
          this.batchCreating = false
        })
      })
    },
    handleSendPackage(row) {
      this.$modal.confirm("确认先发送给员工手写签名？此时合同公司和印章尚未最终确定，员工签名后再由您补充。 ").then(() => {
        return sendSignPackage(row.packageId)
      }).then(() => {
        this.$modal.msgSuccess("发送成功")
        this.getList()
      })
    },
    handleOpenFinalize(row) {
      if (!row || !row.packageId) return Promise.resolve(null)
      if (this.isPreparedSignatureFirstFinalPackage(row)) {
        this.openPreparedFinalTask(row)
        return Promise.resolve(row)
      }
      if (!this.isSignatureFirstWaitingCompanyPackage(row)) {
        this.openGenericCompanyFinalization(row)
        return Promise.resolve(row)
      }
      const knownSourceType = String(row.taskSourceType || row.sourceType || '').trim().toUpperCase()
      if (knownSourceType === 'MANUAL_SIGN_EXCEL_IMPORT') {
        this.openSignatureFirstCompanyWork(row)
        return Promise.resolve(row)
      }
      const taskId = String(row.taskId || '')
      if (!/^[1-9]\d{0,18}$/.test(taskId)) {
        this.$modal.msgError('签约任务关联不完整，请刷新后重试')
        return Promise.resolve(null)
      }
      return getSignTask(taskId).then(response => {
        const detail = response.data || {}
        const task = detail.task || {}
        const latestPackage = Object.assign({}, row, detail.signPackage || {}, {
          taskId: task.taskId || taskId,
          taskSourceType: task.sourceType || task.taskSourceType || '',
          taskVersion: task.version
        })
        if (this.isPreparedSignatureFirstFinalPackage(latestPackage)) {
          this.openPreparedFinalTask(latestPackage)
        } else if (this.isExcelSignatureFirstCompanyWork(latestPackage)) {
          this.openSignatureFirstCompanyWork(latestPackage)
        } else {
          this.openGenericCompanyFinalization(latestPackage)
        }
        return latestPackage
      }).catch(() => {
        this.$modal.msgError('签约任务状态加载失败，请刷新后重试')
        return null
      })
    },
    openGenericCompanyFinalization(row) {
      const expectedVersion = Number(row && row.version)
      const expectedTaskVersion = Number(row && row.taskVersion)
      if (!Number.isSafeInteger(expectedVersion) || expectedVersion < 0 ||
          !Number.isSafeInteger(expectedTaskVersion) || expectedTaskVersion < 0) {
        this.$modal.msgError('签约包或任务版本已变化，请刷新后重试')
        return
      }
      this.finalizePackage = row
      this.finalizeOpen = true
      this.finalizeLoading = true
      this.finalizeOptions = {}
      this.finalizeCompanies = []
      this.finalizeSeals = []
      this.finalizeForm = {
        legalEntityId: null,
        sealId: null,
        correctionReason: "",
        requestId: this.createRequestId('package-finalize'),
        expectedVersion,
        expectedTaskVersion,
        signingSequence: String(row.signingSequence || 'SIGNATURE_FIRST').trim().toUpperCase()
      }
      getSignPackageCompanyOptions(row.packageId).then(response => {
        this.applyFinalizeOptions(response.data || {}, true)
      }).finally(() => {
        this.finalizeLoading = false
      })
    },
    openSignatureFirstCompanyWork(row) {
      const packageId = String(row && row.packageId || '')
      if (!/^[1-9]\d{0,18}$/.test(packageId)) {
        this.$modal.msgError('签约包标识无效，请刷新后重试')
        return
      }
      this.detailOpen = false
      this.$router.push({
        path: '/oa/sign-task',
        query: { companyWorkPackageId: packageId }
      }).catch(() => {})
    },
    openPreparedFinalTask(row) {
      const taskId = String(row && row.taskId || '')
      if (!/^[1-9]\d{0,18}$/.test(taskId)) {
        this.$modal.msgError('签约任务关联不完整，请刷新后重试')
        return
      }
      this.detailOpen = false
      this.$router.push({ path: '/oa/sign-task', query: { taskId } }).catch(() => {})
    },
    applyFinalizeOptions(options, initialize) {
      this.finalizeOptions = Object.assign({}, this.finalizeOptions, options)
      this.finalizeCompanies = options.legalEntities || this.finalizeCompanies || []
      this.finalizeSeals = options.seals || []
      if (initialize) {
        const automatic = options.automaticCandidate
        this.finalizeForm.legalEntityId = automatic ? automatic.legalEntityId : null
      }
      this.finalizeForm.sealId = options.companySealRequired === false ? null
        : (options.recommendedSealId ||
          (this.finalizeSeals.length === 1 ? this.finalizeSeals[0].sealId : null))
      this.$nextTick(() => this.$refs.finalizeForm && this.$refs.finalizeForm.clearValidate())
    },
    handleFinalizeCompanyChange(legalEntityId) {
      this.finalizeForm.sealId = null
      this.finalizeSeals = []
      if (!legalEntityId || !this.finalizePackage) return
      this.finalizeLoading = true
      getSignPackageCompanyOptions(this.finalizePackage.packageId, legalEntityId).then(response => {
        this.applyFinalizeOptions(response.data || {}, false)
      }).finally(() => {
        this.finalizeLoading = false
      })
    },
    submitFinalize() {
      this.$refs.finalizeForm.validate(valid => {
        if (!valid || !this.finalizePackage) return
        if (this.finalizeRequiresSeal && !this.finalizeForm.sealId) {
          this.$modal.msgError("请选择该公司的合同印章")
          return
        }
        this.finalizeSaving = true
        finalizeSignPackage(this.finalizePackage.packageId, this.finalizeForm).then(response => {
          const preparedPackage = response.data || {}
          this.$modal.msgSuccess('最终合同已生成但尚未发送，请逐份预览后显式发送；员工只确认文件，不再签名')
          this.finalizeOpen = false
          this.getList()
          if (this.detail && String(this.detail.packageId) === String(this.finalizePackage.packageId)) {
            this.detail = preparedPackage
          }
          if (preparedPackage.taskId) this.openPreparedFinalTask(preparedPackage)
        }).finally(() => {
          this.finalizeSaving = false
        })
      })
    },
    goCompanyManagement() {
      this.finalizeOpen = false
      this.activeTab = "company"
    },
    handleVoidPackage(row) {
      this.$prompt("请输入撤回原因", "撤回签约包", {
        inputType: "textarea",
        inputValidator: value => !!value,
        inputErrorMessage: "撤回原因不能为空"
      }).then(({ value }) => {
        return voidSignPackage(row.packageId, { voidReason: value })
      }).then(() => {
        this.$modal.msgSuccess("已撤回")
        this.getList()
      })
    },
    handleDetail(row) {
      getSignPackage(row.packageId).then(response => {
        this.detail = response.data
        this.verificationResult = null
        this.technicalEvidenceOpen = []
        this.detailOpen = true
        this.activeTab = "record"
      })
    },
    handleVerify(includeTechnical) {
      if (!this.detail) return
      if (includeTechnical && !this.canViewTechnicalEvidence) return
      this.verificationLoading = true
      verifySignPackage(this.detail.packageId, includeTechnical).then(response => {
        this.verificationResult = response.data
      }).finally(() => {
        this.verificationLoading = false
      })
    },
    verificationMessage(status) {
      return {
        VERIFIED: "文件完整，验真通过",
        MISMATCH: "文件与签署记录不一致",
        FILE_MISSING: "文件缺失，暂时无法验证",
        LEGACY_LIMITED: "历史合同，仅支持旧版验真"
      }[status] || "文件与签署记录不一致"
    },
    verificationAlertType(status) {
      return {
        VERIFIED: "success",
        MISMATCH: "error",
        FILE_MISSING: "warning",
        LEGACY_LIMITED: "info"
      }[status] || "error"
    },
    evidenceTypeLabel(type) {
      return {
        TEMPLATE_SOURCE: "模板原文件",
        RENDERED_SOURCE: "首次生成文件",
        REVIEW_PDF: "首次阅读文件",
        SIGNATURE_SAMPLE: "本任务手写签名样本",
        SIGNATURE_IMAGE: "员工签名图片",
        SIGNED_PDF: "首次签名文件",
        SIGN_CERTIFICATE: "签署证明",
        COMPANY_SEAL: "公司印章图片",
        FINAL_RENDERED_SOURCE: "最终生成文件",
        FINAL_REVIEW_PDF: "最终阅读文件",
        FINAL_SIGNED_PDF: "最终合同"
      }[type] || "其他签约文件"
    },
    openGeneratedDocument(document) {
      if (!this.detail || !document) return
      this.openBlobFile(() => downloadSignPackageDocument(this.detail.packageId, document.documentId))
    },
    openSignedDocument(document) {
      if (!this.detail || !document || !document.signedPdfUrl) return
      this.openBlobFile(() => downloadSignedSignPackageDocument(this.detail.packageId, document.documentId))
    },
    openFinalDocument(document) {
      if (!this.detail || !document || !document.finalPdfUrl) return
      this.openBlobFile(() => downloadFinalSignPackageDocument(this.detail.packageId, document.documentId))
    },
    canExportFinalDocument(document) {
      return !!this.detail && this.detail.status === "signed" &&
        String(this.detail.finalConfirmationStatus || "").toUpperCase() === "CONFIRMED" &&
        !!document && document.templateType === "ONBOARD_LABOR_CONTRACT" &&
        (!!document.finalArchivePdfUrl || document.finalFileAvailable === true)
    },
    exportFinalDocument(document) {
      if (!this.detail || !this.canExportFinalDocument(document)) return Promise.resolve(false)
      const documentId = String(document.documentId)
      if (this.finalExportingDocumentId === documentId) return Promise.resolve(false)
      this.finalExportingDocumentId = documentId
      return downloadFinalSignPackageDocumentExport(this.detail.packageId, document.documentId)
        .then(async blob => {
          const fileBlob = blob instanceof Blob ? blob : new Blob([blob], { type: "application/pdf" })
          if (!(await validatePdfBlob(fileBlob))) throw new Error("INVALID_FINAL_ARCHIVE_PDF")
          await this.$download.saveAs(fileBlob, this.finalExportFileName(document))
          return true
        })
        .catch(() => {
          this.$message.error("最终合同导出失败，请稍后重试")
          return false
        })
        .finally(() => {
          if (this.finalExportingDocumentId === documentId) this.finalExportingDocumentId = null
        })
    },
    finalExportFileName(document) {
      const safePart = (value, fallback) => {
        const cleaned = String(value == null ? "" : value).trim()
          .replace(/[\\/:*?"<>|\r\n\x00-\x1F]+/g, "_")
        return cleaned && cleaned !== "." && cleaned !== ".." ? cleaned : fallback
      }
      return `${safePart(this.detail && this.detail.packageNo, "签约包")}-${safePart(this.detail && this.detail.employeeNameSnapshot, "员工")}-${safePart(document && document.documentName, "合同")}-签章展示版.pdf`
    },
    openGeneratedCertificate(document) {
      if (!this.detail || !document) return
      this.openBlobFile(() => downloadSignPackageCertificate(this.detail.packageId, document.documentId))
    },
    openBlobFile(loader, errorMessage) {
      const target = window.open("", "_blank")
      return loader().then(blob => {
        const url = URL.createObjectURL(new Blob([blob], { type: blob.type || "application/pdf" }))
        if (target) {
          target.location.href = url
        } else {
          window.open(url, "_blank")
        }
        setTimeout(() => URL.revokeObjectURL(url), 60000)
      }).catch(() => {
        if (target) target.close()
        if (errorMessage) this.$message.error(errorMessage)
      })
    },
    handlePreviewTemplate(row) {
      if (!row || !row.templateId) return Promise.resolve()
      return this.openBlobFile(
        async () => {
          const blob = await previewSignTemplateFile(row.templateId)
          if (!await validatePdfBlob(blob)) throw new Error("INVALID_TEMPLATE_PREVIEW_PDF")
          return blob
        },
        "模板在线预览失败，请下载后查看"
      )
    },
    handleDownloadTemplate(row) {
      if (!row || !row.templateId) return Promise.resolve()
      return downloadSignTemplateFile(row.templateId).then(blob => {
        if (!blobValidate(blob)) return this.$download.printErrMsg(blob)
        const fileBlob = blob instanceof Blob ? blob : new Blob([blob])
        this.$download.saveAs(fileBlob, this.templateDownloadFileName(row))
      }).catch(() => {
        this.$message.error("模板文件下载失败，请稍后重试")
      })
    },
    templateDownloadFileName(row) {
      const persistedName = String(row && row.fileName || "").trim()
      if (persistedName) return persistedName.replace(/[\\/\r\n]+/g, "_")
      const type = this.templateTypeOptions.find(item => item.code === (row && row.templateType)) || {}
      const extension = String(type.fileFormat || "docx").toLowerCase()
      const baseName = String(row && row.templateName || "签约模板").replace(/[\\/\r\n]+/g, "_")
      return `${baseName}.${extension}`
    },
    handleAddTemplate() {
      this.templateTitle = "新增模板"
      this.templateForm = this.defaultTemplateForm()
      this.handleTemplateTypeChange(this.templateForm.templateType)
      this.templateOpen = true
      this.$nextTick(() => this.$refs.templateForm && this.$refs.templateForm.clearValidate())
    },
    handleEditTemplate(row) {
      this.templateTitle = "编辑模板"
      const type = this.templateTypeOptions.find(item => item.code === row.templateType) || {}
      const employeeVisible = row.employeeVisible || (type.employeeVisible === false ? "N" : "Y")
      const employeeSignRequired = row.employeeSignRequired || (type.employeeSignRequired ? "Y" : "N")
      const readConfirmationRequired = row.readConfirmationRequired ||
        (employeeSignRequired === "Y" || type.readConfirmationRequired ? "Y" : "N")
      const companySealRequired = ["Y", "N"].includes(row.companySealRequired)
        ? row.companySealRequired : (employeeVisible === "Y" ? "" : "N")
      this.templateForm = Object.assign({}, this.defaultTemplateForm(), row, {
        employeeVisible,
        readConfirmationRequired,
        employeeSignRequired,
        companySealRequired,
        companySealPositionJson: row.companySealPositionJson || ""
      })
      this.templateOpen = true
    },
    handleTemplateTypeChange(templateType) {
      const type = this.templateTypeOptions.find(item => item.code === templateType)
      if (!type) return
      this.templateForm.scenario = type.scenario
      this.templateForm.employeeVisible = type.employeeVisible === false ? "N" : "Y"
      this.templateForm.readConfirmationRequired = type.readConfirmationRequired ? "Y" : "N"
      this.templateForm.employeeSignRequired = type.employeeSignRequired ? "Y" : "N"
      this.templateForm.companySealRequired = type.employeeVisible === false ? "N" : ""
      this.templateForm.companySealPositionJson = ""
      if (isLaborEmploymentTemplateType(templateType)) {
        this.templateForm.employmentType = "劳动合同"
      } else if (isServiceEmploymentTemplateType(templateType)) {
        this.templateForm.employmentType = "劳务合同"
        this.templateForm.socialType = ""
      } else {
        this.templateForm.employmentType = ""
        this.templateForm.socialType = ""
      }
      if (!isPostLevelScopedTemplateType(templateType)) {
        this.templateForm.postLevelScope = ""
      }
      if (!isSalaryVersionTemplateType(templateType)) {
        this.templateForm.salaryVersion = ""
      }
      if (!this.templateForm.templateName) {
        this.templateForm.templateName = type.label
      }
    },
    handleTemplateVisibilityChange(value) {
      if (value === "N") {
        this.templateForm.readConfirmationRequired = "N"
        this.templateForm.employeeSignRequired = "N"
        this.templateForm.companySealRequired = "N"
        this.templateForm.companySealPositionJson = ""
        return
      }
      const type = this.selectedTemplateType || {}
      this.templateForm.employeeSignRequired = type.employeeSignRequired ? "Y" : "N"
      this.templateForm.readConfirmationRequired =
        (type.readConfirmationRequired || type.employeeSignRequired) ? "Y" : "N"
      this.templateForm.companySealRequired = ""
      this.templateForm.companySealPositionJson = ""
    },
    handleTemplateSignRequiredChange(value) {
      if (value === "Y") {
        this.templateForm.employeeVisible = "Y"
        this.templateForm.readConfirmationRequired = "Y"
      }
    },
    handleTemplateSealRequiredChange(value) {
      if (value === "Y") {
        this.templateForm.employeeVisible = "Y"
      } else {
        this.templateForm.companySealPositionJson = ""
      }
    },
    isValidCompanySealPlacement(value) {
      return isValidSignImagePlacementJson(value)
    },
    normalizeTemplatePlacementPolicy() {
      if (this.templateForm.employeeVisible !== "Y") {
        this.templateForm.companySealRequired = "N"
        this.templateForm.companySealPositionJson = ""
        return true
      }
      if (!["Y", "N"].includes(this.templateForm.companySealRequired)) {
        this.$modal.msgError("请明确选择该文件是否需要企业章")
        return false
      }
      if (this.templateForm.companySealRequired === "N") {
        this.templateForm.companySealPositionJson = ""
        return true
      }
      const normalizedPlacement = normalizeSignImagePlacementJson(
        this.templateForm.companySealPositionJson
      )
      if (!normalizedPlacement) {
        this.$modal.msgError("印章定位必须是 APPENDED_CONFIRMATION_PAGE，或字段完整且坐标有效的 LAST_PAGE / PLACED JSON")
        return false
      }
      this.templateForm.companySealPositionJson = normalizedPlacement
      return true
    },
    submitTemplate() {
      this.$refs.templateForm.validate(valid => {
        if (!valid) return
        if (this.templateForm.employeeVisible !== "Y") {
          this.templateForm.readConfirmationRequired = "N"
          this.templateForm.employeeSignRequired = "N"
        } else if (this.templateForm.employeeSignRequired === "Y") {
          this.templateForm.readConfirmationRequired = "Y"
        }
        if (!this.normalizeTemplatePlacementPolicy()) return
        const payload = Object.assign({}, this.templateForm, {
          requiredPlaceholders: this.requiredPlaceholders.join(",")
        })
        this.savingTemplate = true
        saveSignTemplate(payload).then(() => {
          this.$modal.msgSuccess("保存成功")
          this.templateOpen = false
          this.getTemplates()
        }).finally(() => {
          this.savingTemplate = false
        })
      })
    },
    templateTypeLabel(type) {
      const option = this.templateTypeOptions.find(item => item.code === type)
      return option ? option.label : "未登记文件类型"
    },
    dictionaryLabel(value) {
      return signDictionaryLabel(value)
    },
    versionLabel(value) {
      return signVersionLabel(value)
    },
    scenarioLabel(scenario) {
      return signScenarioLabel(scenario)
    },
    parseReminderPolicy(reminderPolicyJson) {
      try {
        const policy = reminderPolicyJson ? JSON.parse(reminderPolicyJson) : {}
        const days = Array.isArray(policy.daysBefore)
          ? policy.daysBefore.map(day => Number(day))
            .filter(day => Number.isInteger(day) && day >= 1 && day <= 30)
          : []
        return {
          reminderEnabled: days.length > 0,
          reminderDaysBefore: days.length ? Array.from(new Set(days)).sort((a, b) => a - b) : [1]
        }
      } catch (error) {
        return { reminderEnabled: false, reminderDaysBefore: [1] }
      }
    },
    reminderPolicyLabel(reminderPolicyJson) {
      const reminder = this.parseReminderPolicy(reminderPolicyJson)
      return reminder.reminderEnabled ? reminder.reminderDaysBefore.join("、") + " 天前" : "关闭"
    },
    createRequestId(prefix) {
      if (typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function') return crypto.randomUUID()
      return `sign-${prefix}-${Date.now()}-${Math.random().toString(16).slice(2)}`
    },
    isSignatureFirstWaitingCompanyPackage(row) {
      return !!row && String(row.status || '').trim().toLowerCase() === 'pending_company' &&
        String(row.signingSequence || '').trim().toUpperCase() === 'SIGNATURE_FIRST' &&
        String(row.finalConfirmationStatus || '').trim().toUpperCase() === 'WAITING_COMPANY'
    },
    isExcelSignatureFirstCompanyWork(row) {
      return this.isSignatureFirstWaitingCompanyPackage(row) &&
        String(row.taskSourceType || row.sourceType || '').trim().toUpperCase() === 'MANUAL_SIGN_EXCEL_IMPORT'
    },
    isPreparedSignatureFirstFinalPackage(row) {
      return !!row && String(row.status || '').trim().toLowerCase() === 'pending_company' &&
        String(row.signingSequence || '').trim().toUpperCase() === 'SIGNATURE_FIRST' &&
        String(row.finalConfirmationStatus || '').trim().toUpperCase() === 'PREPARED_NOT_SENT' &&
        !!String(row.finalDocumentVersion || '').trim() &&
        /^[0-9a-f]{64}$/i.test(String(row.finalDocumentRootHash || '').trim())
    },
    pendingCompanyActionLabel(row) {
      return this.isSignatureFirstWaitingCompanyPackage(row)
        ? '继续选择公司和印章'
        : '选择公司并盖章'
    },
    packageStatusLabel(row) {
      if (this.isPreparedSignatureFirstFinalPackage(row)) return '最终文件已生成，待发送'
      if (this.isSignatureFirstWaitingCompanyPackage(row)) return '唯一签名已完成，待选公司和印章'
      return this.statusLabel(row && row.status)
    },
    packageStatusStep(row) {
      return this.isPreparedSignatureFirstFinalPackage(row) ? 3 : this.statusStep(row && row.status)
    },
    statusLabel(status) {
      return signPackageStatusLabel(status)
    },
    planStatusLabel(status) {
      return status === "1" ? "停用" : "启用"
    },
    planStatusTagType(status) {
      return status === "1" ? "info" : "success"
    },
    statusTagType(status) {
      return {
        draft: "info",
        pending_sign: "warning",
        part_viewed: "warning",
        pending_company: "warning",
        pending_final_confirm: "warning",
        signed: "success",
        refused: "danger",
        expired: "danger",
        voided: "info",
        failed: "danger"
      }[status] || "info"
    },
    statusStep(status) {
      return {
        draft: 0,
        pending_sign: 1,
        part_viewed: 1,
        pending_company: 2,
        pending_final_confirm: 3,
        signed: 5
      }[status] || 0
    }
  }
}
</script>

<style scoped>
.sign-scope-toolbar {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px 16px;
  margin: 14px 0;
  padding: 10px 12px;
  border: 1px solid #dbeafe;
  border-radius: 12px;
  background: #eff6ff;
  color: #64748b;
  font-size: 12px;
}

.sign-package-page .toolbar-row {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 12px;
}

.template-table {
  margin-top: 12px;
}

.toolbar-actions {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
  justify-content: flex-end;
}

.batch-form {
  margin-bottom: 10px;
}

.batch-table {
  width: 100%;
}

.field-tag {
  margin-right: 4px;
  margin-bottom: 4px;
}

.cell-subtext {
  color: #909399;
  font-size: 12px;
  line-height: 18px;
}

.form-tip {
  margin-top: 6px;
  color: #909399;
  font-size: 12px;
  line-height: 1.5;
}

.form-tip-danger {
  color: #f56c6c;
}

.detail-drawer {
  padding: 0 16px 20px;
}

.detail-block {
  margin-top: 14px;
}

.detail-actions {
  display: flex;
  justify-content: flex-end;
  margin-top: 14px;
}

.detail-drawer .el-link + .el-link {
  margin-left: 10px;
}

.technical-evidence-table {
  margin-top: 10px;
}

@media (max-width: 768px) {
  .sign-package-page .toolbar-row {
    display: block;
  }
}
</style>
