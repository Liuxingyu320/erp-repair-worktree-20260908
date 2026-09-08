<template>
  <el-dialog
    title="Excel 入职合同处理"
    :visible.sync="dialogVisible"
    width="1220px"
    append-to-body
    :close-on-click-modal="false"
    :before-close="beforeClose"
    class="onboard-import-dialog"
  >
    <el-steps :active="activeStep" finish-status="success" simple class="onboard-import-steps">
      <el-step title="上传签约数据" />
      <el-step title="核对套餐与缺失资料" />
      <el-step :title="signingSequence === 'SIGNATURE_FIRST' ? '生成最终合同并预览' : '生成并预览'" />
      <el-step :title="signingSequence === 'SIGNATURE_FIRST' ? '发送最终文件' : '发送'" />
    </el-steps>

    <el-alert
      :title="signingSequence === 'SIGNATURE_FIRST'
        ? '员工完成唯一一次签名后，生成最终合同和发送最终文件是两个独立操作：先选择公司与印章并生成、预览，再由 HR 发送给员工确认。'
        : '生成和发送是两个独立操作：先生成 PDF 并核对，再选择单发或群发。'"
      type="info"
      show-icon
      :closable="false"
      class="onboard-import-alert"
    />
    <el-alert
      title="工资以本Excel综合工资为应发基数，底薪及津贴分别存入员工档案；考勤增减另行计算。可单独确认工资入档，生成合同前也会自动入档。"
      type="info" :closable="false" show-icon class="onboard-import-alert"
    />

    <section class="onboard-import-source">
      <div class="onboard-import-scope">
        <sign-scope-selector :disabled="busy" @ready="handleSignScopeReady" @change="handleSignScopeChange" />
        <small>签约组织变更后必须重新预览。</small>
      </div>
      <div class="onboard-import-employees">
        <template v-if="employeeIds.length > 0">
          <span>已限定 <strong>{{ employeeIds.length }}</strong> 人</span>
          <el-tag v-for="employee in employees.slice(0, 8)" :key="employeeKey(employee)" size="mini" effect="plain">
            {{ employee.employeeName || employee.nickName || employee.userName || `员工 #${employeeKey(employee)}` }}
          </el-tag>
          <small v-if="employees.length > 8">另 {{ employees.length - 8 }} 人</small>
          <small>仅核对已选员工，Excel 中其他人员不会生成合同。</small>
        </template>
        <template v-else>
          <span><strong>Excel 自动匹配</strong></span>
          <small>未预选员工，将按完整手机号＋姓名在当前签约组织的授权范围内精确匹配；身份证不一致或存在重复档案时会阻断生成。</small>
        </template>
      </div>
      <div class="onboard-import-upload">
        <el-upload
          ref="upload"
          action="#"
          accept=".xlsx"
          :auto-upload="false"
          :limit="1"
          :file-list="fileList"
          :on-change="handleFileChange"
          :on-remove="handleFileRemove"
          :on-exceed="handleFileExceed"
        >
          <el-button size="small" icon="el-icon-upload2" :disabled="busy">选择 .xlsx 文件</el-button>
          <div slot="tip" class="el-upload__tip">只读取名称精确为“签约数据”的 sheet，不超过 10MB；未预选员工时一次最多100行。
          </div>
        </el-upload>
        <el-button
          type="primary"
          size="small"
          icon="el-icon-search"
          :loading="previewLoading"
          :disabled="!canPreview"
          @click="previewImport"
        >生成预览方案</el-button>
      </div>
    </section>

    <section class="onboard-signing-sequence">
      <div>
        <strong>签署顺序</strong>
        <small>本选择用于接下来发送的员工任务；已发送任务继续按其已冻结的顺序处理。</small>
      </div>
      <el-radio-group v-model="signingSequence" size="small" :disabled="busy">
        <el-radio-button label="COMPANY_FIRST">先选公司，再签完整合同</el-radio-button>
        <el-radio-button label="SIGNATURE_FIRST">先确认签约包并签一次，再选公司</el-radio-button>
      </el-radio-group>
      <el-alert
        :title="signingSequence === 'SIGNATURE_FIRST'
          ? 'HR 先发送入职签约包；员工确认包内冻结事实并完成唯一一次手写签名；HR 再选择公司与印章、生成最终合同并另行发送；员工只确认最终文件，不再签名。'
          : '公司、印章及完整文件先确定；员工逐份打开后一次完成手写签名和最终确认。'"
        type="info"
        :closable="false"
        show-icon
      />
    </section>

    <el-alert
      v-if="operationError"
      title="操作未完成"
      :description="operationError"
      type="error"
      show-icon
      :closable="false"
      class="onboard-import-alert"
    />

    <template v-if="batchId">
      <div class="onboard-import-summary">
        <span>批次 <strong>{{ batchNo || batchId }}</strong></span>
        <span>匹配 <strong>{{ summaryCount('matched') }}</strong></span>
        <span>待补资料 <strong>{{ summaryCount('missing') }}</strong></span>
        <span>可生成 <strong>{{ readyRows.length }}</strong></span>
        <span>已生成 <strong>{{ generatedRows.length }}</strong></span>
        <el-button type="text" size="mini" :loading="refreshLoading" @click="refreshBatch">刷新批次</el-button>
      </div>

      <el-alert
        v-for="(warning, index) in batchWarnings"
        :key="`${warningCode(warning)}-${index}`"
        :title="warningText(warning)"
        type="warning"
        show-icon
        :closable="false"
        class="onboard-import-alert compact"
      />

      <el-alert
        v-if="rehireRequiredRows.length"
        title="检测到离职员工档案"
        :description="`请先到入职管理为这 ${rehireRequiredRows.length} 人创建入职单，并在确认入职时选择“恢复原账号（再入职）”；确认完成后重新上传本 Excel。系统不会为离职员工重复新建账号。`"
        type="warning"
        show-icon
        :closable="false"
        class="onboard-import-alert compact"
      />

      <el-table
        ref="rowTable"
        v-loading="previewLoading || refreshLoading"
        :data="rows"
        border
        size="small"
        max-height="440"
        row-key="rowId"
        empty-text="当前批次暂无导入行"
        @selection-change="handleSelectionChange"
      >
        <el-table-column type="selection" width="48" :selectable="rowSelectable" reserve-selection />
        <el-table-column label="员工" min-width="135">
          <template slot-scope="scope">
            <strong>{{ scope.row.employeeName || `员工 #${scope.row.employeeId || '-'}` }}</strong>
            <div class="cell-subtext">Excel 第 {{ scope.row.sourceRowNumber || '-' }} 行 · {{ matchLabel(scope.row.matchType) }}</div>
          </template>
        </el-table-column>
        <el-table-column label="套餐" min-width="175">
          <template slot-scope="scope">
            <el-tag size="mini" effect="plain">{{ scope.row.routeCode || scope.row.scenarioCode || '待匹配' }}</el-tag>
            <div class="cell-subtext">{{ planLabel(scope.row) }}</div>
            <div v-if="recommendationLines(scope.row).length" class="company-recommendation">
              <strong>Excel 建议（仅核对）</strong>
              <span v-for="line in recommendationLines(scope.row)" :key="line">{{ line }}</span>
            </div>
            <div class="company-match-result">
              <strong>{{ companyMatchLabel(scope.row.companyMatchMode) }}</strong>
              <span v-if="scope.row.matchedLegalEntityName">主数据：{{ scope.row.matchedLegalEntityName }}</span>
              <span v-if="scope.row.matchedUnifiedSocialCreditCode">信用代码：{{ scope.row.matchedUnifiedSocialCreditCode }}</span>
              <span v-if="scope.row.companyMatchScore !== undefined && scope.row.companyMatchScore !== null">
                分值 {{ percentScore(scope.row.companyMatchScore) }}
                <template v-if="scope.row.companySecondScore">· 第二名 {{ percentScore(scope.row.companySecondScore) }}</template>
              </span>
              <span v-if="scope.row.companyDeptConflict" class="is-warning">已按 Excel 匹配公司覆盖部门候选</span>
              <span v-if="scope.row.recommendedSealId">印章：{{ selectedSealName(scope.row) }}</span>
              <span v-else>{{ sealRecommendationLabel(scope.row.sealRecommendationMode) }}</span>
            </div>
            <div v-if="scope.row.contractTypeCode === 'LABOR_CONTRACT'" class="salary-version-result">
              {{ socialTypeLabel(scope.row.socialTypeCode) }} → 薪酬确认书（{{ scope.row.salaryVersion || '-' }}版）
              → {{ salaryTemplateLabel(scope.row) }}
            </div>
            <div v-if="templateNames(scope.row).length" class="template-tags">
              <el-tag v-for="name in templateNames(scope.row)" :key="name" size="mini" type="info">{{ name }}</el-tag>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="缺失资料 / 校验" min-width="250">
          <template slot-scope="scope">
            <div v-if="hasExistingOpenTask(scope.row)" class="existing-task-fact">
              <strong>已有开放的入职签约任务</strong>
              <span>
                任务 #{{ existingTaskFact(scope.row).taskId }}
                <template v-if="existingTaskFact(scope.row).status">· {{ taskStatusLabel(existingTaskFact(scope.row).status) }}</template>
                <template v-if="existingTaskFact(scope.row).sourceType">· {{ taskSourceLabel(existingTaskFact(scope.row).sourceType) }}</template>
              </span>
              <small>本入口不会重复新建，请先核对并处理现有任务。</small>
              <el-button type="text" size="mini" @click="openTaskById(existingTaskFact(scope.row).taskId)">查看现有任务</el-button>
            </div>
            <div v-else-if="showLatestTaskFact(scope.row)" class="latest-task-fact">
              最近历史任务 #{{ latestTaskFact(scope.row).taskId }}
              <template v-if="latestTaskFact(scope.row).status">· {{ taskStatusLabel(latestTaskFact(scope.row).status) }}</template>
            </div>
            <div v-if="rowErrors(scope.row).length" class="validation-lines is-error">
              <span v-for="item in rowErrors(scope.row)" :key="String(item)">{{ issueText(item) }}</span>
            </div>
            <div v-if="rowMissingFields(scope.row).length" class="field-tags">
              <el-tag v-for="field in rowMissingFields(scope.row)" :key="field" size="mini" type="danger" effect="plain">
                {{ fieldLabel(field) }}
              </el-tag>
            </div>
            <div v-if="rowWarnings(scope.row).length" class="validation-lines is-warning">
              <span v-for="item in rowWarnings(scope.row)" :key="String(item)">{{ issueText(item) }}</span>
            </div>
            <span v-if="!rowHasIssues(scope.row)">-</span>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="145">
          <template slot-scope="scope">
            <el-tag size="mini" :type="rowStatusType(scope.row)">{{ rowStatusLabel(scope.row) }}</el-tag>
            <div v-if="scope.row.message" class="cell-subtext">{{ displayMessage(scope.row.message) }}</div>
          </template>
        </el-table-column>
        <el-table-column label="操作" fixed="right" width="230">
          <template slot-scope="scope">
            <el-button type="text" size="mini" :disabled="busy || isExcluded(scope.row)" @click="openRowEditor(scope.row)">
              HR 填写
            </el-button>
            <el-button
              v-if="canRequestEmployeeData(scope.row)"
              type="text"
              size="mini"
              :disabled="busy"
              @click="sendDataRequests([scope.row])"
            >{{ employeeRequestActionLabel(scope.row) }}</el-button>
            <el-button
              v-if="canReviewRow(scope.row)"
              type="text"
              size="mini"
              :disabled="busy"
              @click="openReview(scope.row)"
            >审核资料</el-button>
            <el-button v-if="scope.row.taskId" type="text" size="mini" @click="openTask(scope.row)">预览合同</el-button>
            <el-button
              v-if="isSendable(scope.row)"
              type="text"
              size="mini"
              :loading="sendingRowId === normalizedId(scope.row.rowId)"
              :disabled="busy"
              @click="sendRows([scope.row])"
            >{{ signingSequence === 'SIGNATURE_FIRST' ? '发送最终文件' : '发送' }}</el-button>
          </template>
        </el-table-column>
      </el-table>

      <section class="onboard-import-actions-panel">
        <div>
          <strong>{{ signingSequence === 'SIGNATURE_FIRST' ? '入职签约包发送' : '资料处理' }}</strong>
          <span v-if="signingSequence === 'SIGNATURE_FIRST'">向选中员工发送入职签约包；员工核对冻结事实、补充必要资料，并完成本签约包唯一一次手写签名。</span>
          <span v-else>可将选中的缺失个人事实发给员工补充；员工不能选择劳务人员类型或保险。</span>
        </div>
        <el-button size="small" :disabled="busy || !selectedDataRequestRows.length" @click="sendDataRequests(selectedDataRequestRows)">
          {{ signingSequence === 'SIGNATURE_FIRST' ? '发送入职签约包' : '发送补资任务' }}（{{ selectedDataRequestRows.length }}）
        </el-button>
      </section>

      <section class="onboard-import-confirmation">
        <el-checkbox v-model="confirmation.noExternalContractConfirmed">
          我已逐人核验，不存在已签纸质合同或尚未同步的第三方电子合同
        </el-checkbox>
        <div v-if="historicalSupplementRequired" class="historical-confirmation">
          <el-checkbox v-model="confirmation.historicalSupplementConfirmed">
            我已核验本批次的历史补签日期，并确认保留 Excel 中的合同起止日期
          </el-checkbox>
          <el-input
            v-model.trim="confirmation.historicalSupplementReason"
            type="textarea"
            :rows="2"
            maxlength="450"
            show-word-limit
            placeholder="请填写历史补签原因"
          />
        </div>
        <el-input
          v-if="hasWarningRows"
          v-model.trim="confirmation.warningReason"
          type="textarea"
          :rows="2"
          maxlength="300"
          show-word-limit
          placeholder="本批次存在黄色预警，请填写继续生成的确认原因"
        />
      </section>

      <section v-if="sendResults.length" class="onboard-import-results">
        <strong>发送结果</strong>
        <el-tag
          v-for="result in sendResults"
          :key="normalizedId(result.taskId) || JSON.stringify(result)"
          size="mini"
          :type="result.success === false || ['FAILED', 'ERROR'].includes(String(result.result || '').toUpperCase()) ? 'danger' : 'success'"
        >{{ result.employeeName || result.taskId || '任务' }}：{{ sendResultText(result) }}</el-tag>
      </section>
    </template>

    <div slot="footer" class="onboard-import-footer">
      <el-button :disabled="busy" @click="dialogVisible = false">关 闭</el-button>
      <el-button
        v-if="batchId"
        :loading="archivingSalary"
        :disabled="busy || !selectedRows.some(row => row.employeeId)"
        @click="archiveSalaryRows(selectedRows)"
      >确认选中工资入档</el-button>
      <el-button
        v-if="batchId"
        :loading="generating"
        :disabled="!canGenerateSelected"
        @click="generateRows(selectedReadyRows)"
      >生成选中（{{ selectedReadyRows.length }}）</el-button>
      <el-button
        v-if="batchId"
        type="primary"
        :loading="generating"
        :disabled="!canGenerateAll"
        @click="generateRows(readyRows)"
      >一键生成全部（{{ readyRows.length }}）</el-button>
      <el-button
        v-if="batchId"
        type="success"
        plain
        :loading="sending"
        :disabled="busy || !selectedSendableRows.length"
        @click="sendRows(selectedSendableRows)"
      >{{ signingSequence === 'SIGNATURE_FIRST' ? '发送最终文件' : '发送选中' }}（{{ selectedSendableRows.length }}）</el-button>
      <el-button
        v-if="batchId"
        type="success"
        :loading="sending"
        :disabled="busy || !sendableRows.length"
        @click="sendRows(sendableRows)"
      >{{ signingSequence === 'SIGNATURE_FIRST' ? '一键发送全部最终文件' : '一键发送全部' }}（{{ sendableRows.length }}）</el-button>
    </div>

    <el-dialog
      title="HR 补充与确认"
      :visible.sync="rowEditorOpen"
      width="820px"
      append-to-body
      :close-on-click-modal="false"
    >
      <el-form label-width="128px" size="small">
        <template v-if="editingRow && recommendationLines(editingRow).length">
          <el-alert
            title="Excel 中的公司、法定代表人和注册地址仅作为最终公司选择时的核对信息，不会在此新建或修改公司档案。"
            type="info"
            show-icon
            :closable="false"
            class="onboard-import-alert"
          />
          <el-descriptions :column="1" border size="small" class="recommendation-detail">
            <el-descriptions-item v-if="editingRow.recommendedCompany" label="建议公司">{{ editingRow.recommendedCompany }}</el-descriptions-item>
            <el-descriptions-item v-if="editingRow.recommendedLegalRepresentative" label="建议法定代表人">{{ editingRow.recommendedLegalRepresentative }}</el-descriptions-item>
            <el-descriptions-item v-if="editingRow.recommendedRegisteredAddress" label="建议注册地址">{{ editingRow.recommendedRegisteredAddress }}</el-descriptions-item>
          </el-descriptions>
        </template>
        <el-divider content-position="left">公司与印章主数据</el-divider>
        <el-alert
          title="Excel 仅用于定位公司；合同始终使用公司主数据。低置信或候选接近时必须由 HR 确认。"
          type="info"
          show-icon
          :closable="false"
          class="onboard-import-alert"
        />
        <el-form-item label="合同公司">
          <el-select v-model="rowForm.legalEntityId" filterable placeholder="请选择候选公司" style="width: 100%" @change="rowForm.sealId = ''">
            <el-option
              v-for="item in companyCandidateOptions(editingRow)"
              :key="String(item.legalEntityId)"
              :label="companyCandidateLabel(item)"
              :value="String(item.legalEntityId)"
              :disabled="!!(item.missingMasterFields && item.missingMasterFields.length)"
            />
          </el-select>
          <div v-if="selectedCompanyCandidate" class="candidate-detail">
            <span>法定全称：{{ selectedCompanyCandidate.legalEntityName || '-' }}</span>
            <span>信用代码：{{ selectedCompanyCandidate.unifiedSocialCreditCode || '-' }}</span>
            <span>法定代表人：{{ selectedCompanyCandidate.legalRepresentative || '-' }}</span>
            <span>注册地址：{{ selectedCompanyCandidate.registeredAddress || '-' }}</span>
          </div>
        </el-form-item>
        <el-form-item label="合同印章">
          <el-select v-model="rowForm.sealId" clearable placeholder="唯一有效印章会自动带出" style="width: 100%">
            <el-option
              v-for="item in sealCandidateOptions(editingRow)"
              :key="String(item.sealId)"
              :label="`${item.sealName || '未命名印章'}${item.defaultSeal ? '（默认）' : ''}`"
              :value="String(item.sealId)"
            />
          </el-select>
        </el-form-item>
        <el-divider content-position="left">个人事实</el-divider>
        <el-form-item label="现住址">
          <el-input
            v-model.trim="rowForm.currentAddress"
            maxlength="255"
            placeholder="留空不修改；需要更正时请输入完整现住址"
          />
          <div v-if="editingRow && editingRow.currentAddress" class="cell-subtext">
            当前已保存：{{ editingRow.currentAddress }}（脱敏预览不会被回传）
          </div>
        </el-form-item>
        <el-form-item label="当前是否在校">
          <el-select v-model="rowForm.studentStatus" clearable placeholder="请根据事实选择">
            <el-option label="在校" value="STUDENT" /><el-option label="非在校" value="NON_STUDENT" />
          </el-select>
        </el-form-item>
        <el-form-item v-if="rowForm.studentStatus === 'STUDENT'" label="学校信息"><el-input v-model.trim="rowForm.schoolName" maxlength="100" /></el-form-item>
        <el-form-item label="是否已退休">
          <el-select v-model="rowForm.retirementStatus" clearable placeholder="请根据事实选择">
            <el-option label="已退休" value="RETIRED" /><el-option label="未退休" value="NOT_RETIRED" />
          </el-select>
        </el-form-item>
        <el-form-item label="主要生活来源起始月">
          <el-date-picker v-model="rowForm.incomeStartYearMonth" type="month" value-format="yyyy-MM" placeholder="2-10 声明所需" />
        </el-form-item>
        <el-divider content-position="left">合同、岗位与用工（仅本次签约快照）</el-divider>
        <el-row :gutter="14">
          <el-col :span="12">
            <el-form-item label="合同类型">
              <el-select v-model="rowForm.contractTypeCode" placeholder="请选择" style="width: 100%">
                <el-option v-for="item in contractTypeOptions" :key="item.value" :label="item.label" :value="item.value" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="社保类型">
              <el-select v-model="rowForm.socialTypeCode" placeholder="请选择" style="width: 100%">
                <el-option v-for="item in socialTypeOptions" :key="item.value" :label="item.label" :value="item.value" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="合同期限">
              <el-select v-model="rowForm.contractTermCode" placeholder="请选择" style="width: 100%">
                <el-option v-for="item in contractTermOptions" :key="item.value" :label="item.label" :value="item.value" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12"><el-form-item label="员工岗位"><el-input v-model.trim="rowForm.employeePost" maxlength="128" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="员工职级"><el-input v-model.trim="rowForm.jobGradeCode" maxlength="8" placeholder="例如：7" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="工作地"><el-input v-model.trim="rowForm.workLocation" maxlength="128" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="城市等级"><el-input v-model.trim="rowForm.cityLevel" maxlength="64" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="劳务人员类型"><el-input v-model.trim="rowForm.servicePersonType" maxlength="80" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="保险类型"><el-input v-model.trim="rowForm.insuranceType" maxlength="80" /></el-form-item></el-col>
        </el-row>
        <el-divider content-position="left">合同与试用期日期</el-divider>
        <el-row :gutter="14">
          <el-col :span="12"><el-form-item label="合同开始日"><el-date-picker v-model="rowForm.contractStartDate" type="date" value-format="yyyy-MM-dd" style="width: 100%" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="合同结束日"><el-date-picker v-model="rowForm.contractEndDate" type="date" value-format="yyyy-MM-dd" style="width: 100%" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="试用期开始"><el-date-picker v-model="rowForm.probationStartDate" type="date" value-format="yyyy-MM-dd" style="width: 100%" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="试用期结束"><el-date-picker v-model="rowForm.probationEndDate" type="date" value-format="yyyy-MM-dd" style="width: 100%" /></el-form-item></el-col>
        </el-row>
        <el-divider content-position="left">薪资校验字段</el-divider>
        <el-row :gutter="14">
          <el-col :span="12"><el-form-item label="综合工资"><el-input-number v-model="rowForm.salaryTotal" :min="0" :precision="2" :controls="false" style="width: 100%" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="底薪"><el-input-number v-model="rowForm.baseSalary" :min="0" :precision="2" :controls="false" style="width: 100%" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="岗位津贴"><el-input-number v-model="rowForm.postSalary" :min="0" :precision="2" :controls="false" style="width: 100%" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="驻外补贴"><el-input-number v-model="rowForm.fieldAllowance" :min="0" :precision="2" :controls="false" style="width: 100%" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="绩效津贴"><el-input-number v-model="rowForm.performanceSalary" :min="0" :precision="2" :controls="false" style="width: 100%" /></el-form-item></el-col>
        </el-row>
        <el-divider content-position="left">HR 确认</el-divider>
        <el-form-item label="黄色预警确认">
          <el-input v-model.trim="rowForm.warningReason" type="textarea" :rows="2" maxlength="300" placeholder="有薪资区间等黄色预警时填写确认原因" />
        </el-form-item>
      </el-form>
      <div slot="footer">
        <el-button :disabled="updatingRow" @click="rowEditorOpen = false">取消</el-button>
        <el-button type="primary" :loading="updatingRow" @click="saveRow">保存并重新匹配</el-button>
      </div>
    </el-dialog>

    <el-dialog
      title="审核员工补充资料"
      :visible.sync="reviewOpen"
      width="640px"
      append-to-body
      :close-on-click-modal="false"
    >
      <el-descriptions :column="1" border size="small">
        <el-descriptions-item v-for="item in reviewSubmittedItems" :key="item.key" :label="fieldLabel(item.key)">
          {{ item.value === '' || item.value === undefined || item.value === null ? '-' : item.value }}
        </el-descriptions-item>
      </el-descriptions>
      <el-form label-width="128px" size="small" class="review-form">
        <el-form-item label="劳务人员类型"><el-input v-model.trim="reviewForm.servicePersonType" maxlength="80" /></el-form-item>
        <el-form-item label="保险类型"><el-input v-model.trim="reviewForm.insuranceType" maxlength="80" /></el-form-item>
        <el-form-item label="审核说明"><el-input v-model.trim="reviewForm.reason" type="textarea" :rows="2" maxlength="300" /></el-form-item>
      </el-form>
      <el-alert title="审核通过后，仅个人事实字段会同步员工档案；Excel 合同、薪资和日期不回写档案。" type="info" :closable="false" show-icon />
      <div slot="footer">
        <el-button :disabled="reviewing" @click="reviewOpen = false">取消</el-button>
        <el-button type="danger" plain :loading="reviewing" @click="submitReview('REJECT')">驳回</el-button>
        <el-button type="primary" :loading="reviewing" @click="submitReview('APPROVE')">审核通过</el-button>
      </div>
    </el-dialog>
  </el-dialog>
</template>

<script>
import SignScopeSelector from "@/components/SignScopeSelector"
import { getSelectedSignScopeDeptId } from "@/utils/signScopeContext"
import {
  archiveOnboardSignSalary,
  generateOnboardSignImport,
  getOnboardSignDataRequest,
  getOnboardSignImportBatch,
  previewOnboardSignImport,
  reviewOnboardSignDataRequest,
  sendOnboardSignDataRequests,
  sendSignTaskBatch,
  updateOnboardSignImportRow
} from "@/api/oa/signTask"
import { profileFieldLabel } from "./hrFieldConfig"
import { signTaskReasonLabel, signTaskStatusLabel } from "@/utils/signDictionary"

const MAX_BATCH_SIZE = 100
const MAX_FILE_SIZE = 10 * 1024 * 1024
const READY_STATUSES = ["MATCHED", "READY_TO_GENERATE"]
const GENERATED_STATUSES = ["GENERATED", "READY_TO_SEND"]
const SENT_STATUSES = ["SENT", "PARTIAL_SENT"]
const EMPLOYEE_DATA_REQUEST_FIELDS = Object.freeze([
  "currentAddress", "studentStatus", "schoolName", "retirementStatus", "incomeStartYearMonth"
])
const GENERATION_CONFIRMATION_FIELDS = Object.freeze([
  "noExternalContractConfirmation", "historicalSupplementReason", "warningConfirmationReason"
])
const ROW_EDITABLE_FIELDS = Object.freeze([
  "currentAddress", "contractTypeCode", "socialTypeCode", "contractTermCode", "employeePost",
  "jobGradeCode", "workLocation", "cityLevel", "contractStartDate", "contractEndDate",
  "probationStartDate", "probationEndDate", "salaryTotal", "baseSalary", "postSalary",
  "fieldAllowance", "performanceSalary", "servicePersonType", "insuranceType", "studentStatus",
  "schoolName", "retirementStatus", "incomeStartYearMonth", "warningReason",
  "legalEntityId", "sealId"
])
const CONTRACT_TYPE_OPTIONS = Object.freeze([
  { value: "LABOR_CONTRACT", label: "劳动合同" },
  { value: "SERVICE_CONTRACT", label: "劳务合同" }
])
const SOCIAL_TYPE_OPTIONS = Object.freeze([
  { value: "SOCIAL_INSURED", label: "有社保" },
  { value: "SOCIAL_UNINSURED", label: "无社保" }
])
const CONTRACT_TERM_OPTIONS = Object.freeze([
  { value: "FIXED_TERM", label: "固定期限" },
  { value: "OPEN_ENDED", label: "无固定期限" }
])
const MASK_GLYPH_PATTERN = /[*＊•●○◯◎◉◌◍◦∙]/u

function normalizePositiveDecimalId(value) {
  if (value === undefined || value === null) return ""
  const text = String(value).trim()
  if (!/^\d+$/.test(text)) return ""
  return text.replace(/^0+/, "")
}

function parseObject(value) {
  if (!value) return {}
  if (typeof value === "object" && !Array.isArray(value)) return value
  if (typeof value !== "string") return {}
  try {
    const parsed = JSON.parse(value)
    return parsed && typeof parsed === "object" && !Array.isArray(parsed) ? parsed : {}
  } catch (ignored) {
    return {}
  }
}

function isMaskedPreviewValue(value) {
  return typeof value === "string" && MASK_GLYPH_PATTERN.test(value)
}

function samePatchValue(before, after) {
  if (before === after) return true
  return (before === undefined || before === null || before === "") &&
    (after === undefined || after === null || after === "")
}

export default {
  name: "HrSignDataImportDialog",
  components: { SignScopeSelector },
  props: {
    visible: { type: Boolean, default: false },
    employees: { type: Array, default: () => [] }
  },
  data() {
    return {
      activeStep: 0,
      signScopeReady: !!getSelectedSignScopeDeptId(),
      fileList: [],
      selectedFile: null,
      batch: null,
      rows: [],
      selectedRows: [],
      rowSelectionInitialized: false,
      batchWarnings: [],
      previewLoading: false,
      refreshLoading: false,
      generating: false,
      archivingSalary: false,
      sending: false,
      sendingRowId: "",
      operationError: "",
      generateRequestId: "",
      sendRequestId: "",
      sendResults: [],
      signingSequence: "COMPANY_FIRST",
      rowEditorOpen: false,
      editingRow: null,
      updatingRow: false,
      rowForm: this.emptyRowForm(),
      rowOriginalForm: {},
      contractTypeOptions: CONTRACT_TYPE_OPTIONS,
      socialTypeOptions: SOCIAL_TYPE_OPTIONS,
      contractTermOptions: CONTRACT_TERM_OPTIONS,
      reviewOpen: false,
      reviewingRow: null,
      reviewing: false,
      reviewForm: this.emptyReviewForm(),
      confirmation: {
        noExternalContractConfirmed: false,
        historicalSupplementConfirmed: false,
        historicalSupplementReason: "",
        warningReason: ""
      }
    }
  },
  computed: {
    dialogVisible: {
      get() { return this.visible },
      set(value) { this.$emit("update:visible", value) }
    },
    employeeIds() {
      return Array.from(new Set((this.employees || []).map(employee => this.employeeKey(employee)).filter(Boolean)))
    },
    batchId() {
      return normalizePositiveDecimalId(this.batch && (this.batch.batchId || this.batch.id))
    },
    batchNo() {
      return this.batch && (this.batch.batchNo || this.batch.importBatchNo)
    },
    busy() {
      return this.archivingSalary || this.previewLoading || this.refreshLoading || this.generating || this.sending || this.updatingRow || this.reviewing
    },
    canPreview() {
      return !this.busy && this.signScopeReady && !!this.selectedFile && this.employeeIds.length <= MAX_BATCH_SIZE
    },
    historicalSupplementRequired() {
      return this.targetRowsRequireHistoricalSupplement(this.readyRows)
    },
    hasWarningRows() {
      return this.targetRowsRequireWarningReason(this.readyRows)
    },
    readyRows() {
      return this.rows.filter(row => this.isReadyToGenerate(row))
    },
    rehireRequiredRows() {
      return this.rows.filter(row => this.rowErrors(row).some(item =>
        String(typeof item === "string" ? item : (item && item.code) || "").split(":")[0] === "EMPLOYEE_REHIRE_REQUIRED"
      ))
    },
    selectedReadyRows() {
      return this.selectedRows.filter(row => this.isReadyToGenerate(row))
    },
    generatedRows() {
      return this.rows.filter(row => this.isGenerated(row))
    },
    sendableRows() {
      return this.rows.filter(row => this.isSendable(row))
    },
    selectedSendableRows() {
      return this.selectedRows.filter(row => this.isSendable(row))
    },
    selectedDataRequestRows() {
      return this.selectedRows.filter(row => this.canRequestEmployeeData(row))
    },
    generationConfirmed() {
      return this.generationConfirmedFor(this.readyRows)
    },
    canGenerateSelected() {
      return !this.busy && this.selectedReadyRows.length > 0 && this.generationConfirmedFor(this.selectedReadyRows)
    },
    canGenerateAll() {
      return !this.busy && this.readyRows.length > 0 && this.generationConfirmedFor(this.readyRows)
    },
    reviewSubmittedItems() {
      const row = this.reviewingRow || {}
      const values = parseObject(row.employeeSubmittedValues || row.submittedValues || row.dataRequestSubmittedValues)
      ;["currentAddress", "studentStatus", "schoolName", "retirementStatus", "incomeStartYearMonth"].forEach(key => {
        if (values[key] === undefined && row[key] !== undefined) values[key] = row[key]
      })
      return Object.keys(values).map(key => ({ key, value: values[key] }))
    },
    selectedCompanyCandidate() {
      const selected = normalizePositiveDecimalId(this.rowForm.legalEntityId)
      return this.companyCandidateOptions(this.editingRow)
        .find(item => normalizePositiveDecimalId(item.legalEntityId) === selected) || null
    }
  },
  watch: {
    visible(value) {
      if (value) this.prepare()
    },
    employees: {
      deep: true,
      handler(value, previous) {
        if (!this.visible || JSON.stringify(value || []) === JSON.stringify(previous || [])) return
        this.invalidatePreview("已选员工发生变化，请重新预览")
      }
    }
  },
  methods: {
    emptyRowForm() {
      return {
        currentAddress: "",
        contractTypeCode: "",
        socialTypeCode: "",
        contractTermCode: "",
        employeePost: "",
        jobGradeCode: "",
        workLocation: "",
        cityLevel: "",
        contractStartDate: "",
        contractEndDate: "",
        probationStartDate: "",
        probationEndDate: "",
        salaryTotal: null,
        baseSalary: null,
        postSalary: null,
        fieldAllowance: null,
        performanceSalary: null,
        studentStatus: "",
        schoolName: "",
        retirementStatus: "",
        incomeStartYearMonth: "",
        servicePersonType: "",
        insuranceType: "",
        warningReason: "",
        legalEntityId: "",
        sealId: ""
      }
    },
    emptyReviewForm() {
      return { servicePersonType: "", insuranceType: "", reason: "" }
    },
    prepare() {
      this.activeStep = 0
      this.signScopeReady = !!getSelectedSignScopeDeptId()
      this.fileList = []
      this.selectedFile = null
      this.batch = null
      this.rows = []
      this.selectedRows = []
      this.rowSelectionInitialized = false
      this.batchWarnings = []
      this.operationError = ""
      this.sendResults = []
      this.signingSequence = "COMPANY_FIRST"
      this.generateRequestId = this.createRequestId("onboard-generate")
      this.sendRequestId = this.createRequestId("onboard-send")
      this.confirmation = {
        noExternalContractConfirmed: false,
        historicalSupplementConfirmed: false,
        historicalSupplementReason: "",
        warningReason: ""
      }
    },
    employeeKey(employee) {
      return normalizePositiveDecimalId(employee && (employee.employeeId !== undefined ? employee.employeeId : employee.userId))
    },
    normalizedId(value) { return normalizePositiveDecimalId(value) },
    handleSignScopeReady(option) {
      this.signScopeReady = !!(option && option.deptId)
    },
    handleSignScopeChange(option) {
      this.signScopeReady = !!(option && option.deptId)
      this.invalidatePreview("签约组织已变更，请重新预览")
    },
    handleFileChange(file) {
      const raw = file && file.raw
      const name = String((raw && raw.name) || (file && file.name) || "")
      if (!/\.xlsx$/i.test(name)) {
        this.$modal.msgWarning("只能上传 .xlsx 文件")
        this.clearFile()
        return
      }
      if (Number(raw && raw.size) > MAX_FILE_SIZE) {
        this.$modal.msgWarning("签约数据文件不能超过 10MB")
        this.clearFile()
        return
      }
      this.fileList = [file]
      this.selectedFile = raw
      this.invalidatePreview("文件已变更，请生成新的预览方案")
    },
    handleFileRemove() { this.clearFile() },
    handleFileExceed(files) {
      const raw = files && files[0]
      if (!raw) return
      this.handleFileChange({ name: raw.name, raw })
    },
    clearFile() {
      this.fileList = []
      this.selectedFile = null
      if (this.$refs.upload && typeof this.$refs.upload.clearFiles === "function") this.$refs.upload.clearFiles()
      this.invalidatePreview("")
    },
    invalidatePreview(message) {
      this.batch = null
      this.rows = []
      this.selectedRows = []
      this.batchWarnings = []
      this.sendResults = []
      this.activeStep = 0
      this.operationError = message || ""
      this.generateRequestId = this.createRequestId("onboard-generate")
      this.sendRequestId = this.createRequestId("onboard-send")
      this.confirmation.noExternalContractConfirmed = false
      this.confirmation.historicalSupplementConfirmed = false
      this.confirmation.historicalSupplementReason = ""
      this.confirmation.warningReason = ""
    },
    previewImport() {
      if (!this.canPreview) return Promise.resolve(null)
      this.previewLoading = true
      this.operationError = ""
      return previewOnboardSignImport(this.selectedFile, this.employeeIds).then(response => {
        this.applyBatchResponse(response)
        this.activeStep = 1
        return this.rows
      }).catch(error => {
        this.operationError = this.errorMessage(error, "Excel 预览失败，请核对 sheet 名、表头、签约组织及员工资料")
        return null
      }).finally(() => { this.previewLoading = false })
    },
    refreshBatch() {
      if (!this.batchId) return Promise.resolve(null)
      this.refreshLoading = true
      this.operationError = ""
      return getOnboardSignImportBatch(this.batchId).then(response => {
        this.applyBatchResponse(response)
        return this.rows
      }).catch(error => {
        this.operationError = this.errorMessage(error, "批次刷新失败，请勿重复点击生成或发送")
        return null
      }).finally(() => { this.refreshLoading = false })
    },
    applyBatchResponse(response) {
      const payload = response && response.data !== undefined ? response.data : response
      const data = payload && payload.data !== undefined && !Array.isArray(payload.data) ? payload.data : payload
      const batch = data && data.batch ? data.batch : (data || {})
      const sourceRows = Array.isArray(data) ? data
        : (data && Array.isArray(data.rows) ? data.rows
          : (batch && Array.isArray(batch.rows) ? batch.rows
            : (data && Array.isArray(data.items) ? data.items : [])))
      const selectedIds = new Set(this.selectedRows.map(row => normalizePositiveDecimalId(row.rowId || row.id)).filter(Boolean))
      const useDefaultSelection = !this.rowSelectionInitialized
      this.batch = Object.assign({}, batch)
      this.rows = sourceRows.map((row, index) => this.normalizeRow(row, index))
      this.batchWarnings = (data && (data.warnings || data.warningItems)) || batch.warnings || []
      const targetRows = this.rows.filter(row => {
        const rowId = normalizePositiveDecimalId(row.rowId)
        const selected = useDefaultSelection ? this.isReadyToGenerate(row) : selectedIds.has(rowId)
        return selected && this.rowSelectable(row)
      })
      this.selectedRows = targetRows
      this.rowSelectionInitialized = true
      this.$nextTick(() => {
        if (!this.$refs.rowTable) return
        this.$refs.rowTable.clearSelection()
        targetRows.forEach(row => this.$refs.rowTable.toggleRowSelection(row, true))
      })
      if (this.generatedRows.length) this.activeStep = this.sendableRows.length ? 2 : 3
    },
    normalizeRow(row, index) {
      const normalized = Object.assign({}, row)
      const sourceId = row && (row.rowId !== undefined ? row.rowId : row.id)
      normalized.rowId = normalizePositiveDecimalId(sourceId) || `row-${index}`
      ;["employeeId", "taskId", "packageId", "dataRequestId"].forEach(field => {
        const value = normalizePositiveDecimalId(normalized[field])
        if (value) normalized[field] = value
      })
      return normalized
    },
    handleSelectionChange(rows) { this.selectedRows = rows || [] },
    rowSelectable(row) {
      return (!this.isExcluded(row) || this.isSignatureFirstRequestable(row)) && !this.hasExistingOpenTask(row)
    },
    rowStatus(row) { return String((row && (row.status || row.rowStatus || row.validationStatus)) || "MATCHED").toUpperCase() },
    isExcluded(row) { return ["CONFLICT", "EXCLUDED"].includes(this.rowStatus(row)) },
    isReadyToGenerate(row) {
      if (!row || this.isExcluded(row) || this.hasExistingOpenTask(row) || this.rowErrors(row).length || this.blockingRowMissingFields(row).length) return false
      const rowSequence = String(row.dataRequestSigningSequence || "").toUpperCase()
      if (rowSequence === "SIGNATURE_FIRST" && row.dataRequestSignatureCaptured !== true) return false
      if (this.signingSequence === "SIGNATURE_FIRST" &&
        (rowSequence !== "SIGNATURE_FIRST" || row.dataRequestSignatureCaptured !== true)) return false
      if (row.readyToGenerate === true || READY_STATUSES.includes(this.rowStatus(row))) return true
      return this.rowStatus(row) === "NEEDS_HR_DATA" && !!row.planVersionId &&
        this.rowMissingFields(row).length > 0 &&
        this.rowMissingFields(row).every(item => GENERATION_CONFIRMATION_FIELDS.includes(this.issueFieldKey(item)))
    },
    isGenerated(row) {
      return !!row && (row.generated === true || GENERATED_STATUSES.includes(this.rowStatus(row)) || SENT_STATUSES.includes(this.rowStatus(row)))
    },
    isSendable(row) {
      if (!row || !normalizePositiveDecimalId(row.taskId)) return false
      return row.sendable === true || ["GENERATED", "READY_TO_SEND"].includes(this.rowStatus(row))
    },
    canRequestEmployeeData(row) {
      const status = this.rowStatus(row)
      const signatureFirstRequestable = this.isSignatureFirstRequestable(row)
      if (!row || (this.isExcluded(row) && !signatureFirstRequestable) || this.hasExistingOpenTask(row) ||
        !normalizePositiveDecimalId(row.employeeId) || normalizePositiveDecimalId(row.taskId) ||
        ["WAITING_EMPLOYEE_DATA", "PENDING_HR_REVIEW", "GENERATING", "GENERATED", "READY_TO_SEND", "SENT", "PARTIAL_SENT"].includes(status)) return false
      if (this.signingSequence === "SIGNATURE_FIRST") {
        return signatureFirstRequestable && (
          String(row.dataRequestSigningSequence || "").toUpperCase() !== "SIGNATURE_FIRST" ||
          row.dataRequestSignatureCaptured !== true
        )
      }
      return this.employeeDataRequestFields(row).length > 0
    },
    isSignatureFirstRequestable(row) {
      return this.signingSequence === "SIGNATURE_FIRST" && !!row && row.signatureRequestable === true
    },
    canReviewRow(row) {
      return !!row && this.rowStatus(row) === "PENDING_HR_REVIEW" && !!normalizePositiveDecimalId(row.dataRequestId || row.requestId)
    },
    rowErrors(row) { return this.issueList(row && (row.errorCodes || row.errors || row.blockingErrors)) },
    rowWarnings(row) { return this.issueList(row && (row.warningCodes || row.warnings)) },
    rowMissingFields(row) { return this.issueList(row && (row.missingFields || row.missingFieldCodes)) },
    issueFieldKey(value) {
      return typeof value === "string" ? value : (value && (value.field || value.key || value.code)) || ""
    },
    employeeDataRequestFields(row) {
      return Array.from(new Set(this.rowMissingFields(row)
        .map(item => this.issueFieldKey(item))
        .filter(key => EMPLOYEE_DATA_REQUEST_FIELDS.includes(key))))
    },
    employeeRequestActionLabel(row) {
      if (this.signingSequence !== "SIGNATURE_FIRST") return "让员工补充"
      return this.employeeDataRequestFields(row).length ? "发送签约包并补资料" : "发送入职签约包"
    },
    blockingRowMissingFields(row) {
      return this.rowMissingFields(row)
        .filter(item => !GENERATION_CONFIRMATION_FIELDS.includes(this.issueFieldKey(item)))
    },
    targetRowsRequireHistoricalSupplement(targetRows) {
      return (targetRows || []).some(row => row && (
        row.historicalSupplement === true || row.historicalSupplementRequired === true ||
        this.rowMissingFields(row).some(item => this.issueFieldKey(item) === "historicalSupplementReason")
      ))
    },
    targetRowsRequireWarningReason(targetRows) {
      return (targetRows || []).some(row => row && row.warningConfirmed !== true && (
        this.rowWarnings(row).length > 0 ||
        this.rowMissingFields(row).some(item => this.issueFieldKey(item) === "warningConfirmationReason")
      ))
    },
    generationConfirmedFor(targetRows) {
      const rows = (targetRows || []).filter(row => this.isReadyToGenerate(row))
      if (!rows.length || !this.confirmation.noExternalContractConfirmed) return false
      if (this.targetRowsRequireHistoricalSupplement(rows) &&
        (!this.confirmation.historicalSupplementConfirmed || !this.confirmation.historicalSupplementReason.trim())) return false
      return !this.targetRowsRequireWarningReason(rows) || this.confirmation.warningReason.trim().length > 0
    },
    issueList(value) {
      if (Array.isArray(value)) return value
      if (!value) return []
      if (typeof value === "string") {
        try {
          const parsed = JSON.parse(value)
          return Array.isArray(parsed) ? parsed : [value]
        } catch (ignored) {
          return value.split(",").map(item => item.trim()).filter(Boolean)
        }
      }
      return []
    },
    existingTaskFact(row) {
      const nested = row && (row.existingTask || row.existingOpenTask || row.openTask) || {}
      return {
        taskId: normalizePositiveDecimalId(row && (row.existingTaskId || row.openTaskId) || nested.taskId || nested.id),
        status: String(row && (row.existingTaskStatus || row.openTaskStatus) || nested.status || "").trim(),
        sourceType: String(row && (row.existingTaskSourceType || row.openTaskSourceType) || nested.sourceType || "").trim()
      }
    },
    latestTaskFact(row) {
      const nested = row && (row.latestTask || row.latestHistoricalTask) || {}
      return {
        taskId: normalizePositiveDecimalId(row && row.latestTaskId || nested.taskId || nested.id),
        status: String(row && row.latestTaskStatus || nested.status || "").trim(),
        sourceType: String(row && row.latestTaskSourceType || nested.sourceType || "").trim()
      }
    },
    showLatestTaskFact(row) {
      const latestTaskId = this.latestTaskFact(row).taskId
      const currentTaskId = normalizePositiveDecimalId(row && row.taskId)
      return !!latestTaskId && latestTaskId !== currentTaskId
    },
    hasExistingOpenTask(row) { return !!this.existingTaskFact(row).taskId },
    taskStatusLabel(value) { return signTaskStatusLabel(value, "状态未知") },
    taskSourceLabel(value) { return signTaskReasonLabel(value, "来源未知") },
    rowHasIssues(row) {
      return this.hasExistingOpenTask(row) ||
        this.rowErrors(row).length + this.rowWarnings(row).length + this.rowMissingFields(row).length > 0
    },
    rowStatusLabel(row) {
      const status = this.rowStatus(row)
      const requestSequence = String(row && row.dataRequestSigningSequence || this.signingSequence || "").toUpperCase()
      if (status === "WAITING_EMPLOYEE_DATA" && requestSequence === "SIGNATURE_FIRST" &&
        this.employeeDataRequestFields(row).length === 0) return "待确认签约包并签名"
      return {
        MATCHED: "已匹配",
        NEEDS_HR_DATA: "待 HR 补充",
        WAITING_EMPLOYEE_DATA: "待员工补充",
        PENDING_HR_REVIEW: "待 HR 审核",
        READY_TO_GENERATE: "可生成",
        GENERATING: "生成中",
        GENERATED: "已生成",
        READY_TO_SEND: "待发送",
        SENT: "已发送",
        PARTIAL_SENT: "部分发送",
        CONFLICT: "匹配冲突",
        EXCLUDED: "已排除",
        PLAN_CHANGED_REPREVIEW: "方案已变更",
        PROFILE_CHANGED_REPREVIEW: "档案已变更",
        COMPANY_CHANGED_REPREVIEW: "公司或印章已变更",
        GENERATE_FAILED: "生成失败",
        PROFILE_SYNC_FAILED: "档案同步失败",
        REFUSED: "员工已拒签，请新批次重发",
        EXPIRED: "首阶段已逾期，请新批次重发"
      }[status] || "状态待核对"
    },
    rowStatusType(row) {
      const status = this.rowStatus(row)
      if (["SENT"].includes(status)) return "success"
      if (["CONFLICT", "GENERATE_FAILED", "PROFILE_SYNC_FAILED", "REFUSED", "EXPIRED"].includes(status)) return "danger"
      if (["NEEDS_HR_DATA", "WAITING_EMPLOYEE_DATA", "PENDING_HR_REVIEW", "PARTIAL_SENT", "PLAN_CHANGED_REPREVIEW", "PROFILE_CHANGED_REPREVIEW", "COMPANY_CHANGED_REPREVIEW"].includes(status)) return "warning"
      return "info"
    },
    matchLabel(value) {
      return {
        ID_CARD: "证件号匹配",
        ID_NUMBER: "身份证号匹配",
        PHONE: "手机号二次匹配",
        PHONE_AND_NAME: "手机号＋姓名匹配",
        EMPLOYEE_ID: "员工 ID 匹配",
        CONFLICT: "匹配冲突",
        EXTRA: "不在限定员工内",
        MISSING: "Excel 缺少该员工"
      }[String(value || "").toUpperCase()] || "已核对"
    },
    planLabel(row) {
      return row.planVersionName || row.planName || (row.planVersionId ? `方案版本 ${row.planVersionId}` : "待确认方案版本")
    },
    templateNames(row) {
      const values = row && (row.templateNames || row.documentNames || row.expectedDocuments || row.templates)
      if (!Array.isArray(values)) return []
      return values.map(item => typeof item === "string" ? item : (item.templateName || item.documentName || item.name)).filter(Boolean)
    },
    recommendationLines(row) {
      if (!row) return []
      return [
        row.recommendedCompany ? `公司：${row.recommendedCompany}` : "",
        row.recommendedLegalRepresentative ? `法定代表人：${row.recommendedLegalRepresentative}` : "",
        row.recommendedRegisteredAddress ? `注册地址：${row.recommendedRegisteredAddress}` : ""
      ].filter(Boolean)
    },
    companyCandidateOptions(row) {
      return Array.isArray(row && row.companyCandidates) ? row.companyCandidates : []
    },
    sealCandidateOptions(row) {
      return Array.isArray(row && row.sealCandidates) ? row.sealCandidates : []
    },
    companyCandidateLabel(item) {
      if (!item) return "未知公司"
      const score = item.score === undefined || item.score === null ? "" : `·${this.percentScore(item.score)}`
      const invalid = item.missingMasterFields && item.missingMasterFields.length ? "·主数据待补" : ""
      return `${item.legalEntityName || item.legalEntityCode || item.legalEntityId}${score}${invalid}`
    },
    companyMatchLabel(value) {
      const normalized = String(value || "").toUpperCase()
      if (normalized === "EXCEL_AUTO") return "Excel 高置信自动匹配"
      if (normalized === "HR_CONFIRMED") return "HR 已确认公司"
      if (normalized.includes("AMBIGUOUS")) return "候选接近，待 HR 确认"
      if (normalized.includes("LOW_CONFIDENCE")) return "匹配分值不足，待 HR 确认"
      if (normalized.includes("NO_INPUT")) return "Excel 公司信息缺失，待 HR 确认"
      return "待 HR 确认公司"
    },
    sealRecommendationLabel(value) {
      const normalized = String(value || "").toUpperCase()
      if (normalized.includes("NO_ACTIVE")) return "无有效合同印章"
      if (normalized.includes("MULTIPLE")) return "印章不唯一，待 HR 选择"
      return "待匹配合同印章"
    },
    selectedSealName(row) {
      const id = normalizePositiveDecimalId(row && row.recommendedSealId)
      const selected = this.sealCandidateOptions(row)
        .find(item => normalizePositiveDecimalId(item.sealId) === id)
      return selected ? selected.sealName : `#${id || '-'}`
    },
    percentScore(value) {
      const number = Number(value)
      return Number.isFinite(number) ? `${(number * 100).toFixed(1)}%` : "-"
    },
    socialTypeLabel(value) {
      return value === "SOCIAL_INSURED" ? "有社保" : value === "SOCIAL_UNINSURED" ? "无社保" : "社保待确认"
    },
    salaryTemplateLabel(row) {
      return this.templateNames(row).find(name => String(name).includes("薪酬结构确认书")) || "实际模板待匹配"
    },
    openRowEditor(row) {
      this.editingRow = row
      this.rowForm = Object.assign(this.emptyRowForm(), {
        currentAddress: "",
        contractTypeCode: row.contractTypeCode || "",
        socialTypeCode: row.socialTypeCode || "",
        contractTermCode: row.contractTermCode || "",
        employeePost: row.employeePost || "",
        jobGradeCode: row.jobGradeCode || "",
        workLocation: row.workLocation || "",
        cityLevel: row.cityLevel || "",
        contractStartDate: row.contractStartDate || "",
        contractEndDate: row.contractEndDate || "",
        probationStartDate: row.probationStartDate || "",
        probationEndDate: row.probationEndDate || "",
        salaryTotal: row.salaryTotal === undefined ? null : row.salaryTotal,
        baseSalary: row.baseSalary === undefined ? null : row.baseSalary,
        postSalary: row.postSalary === undefined ? null : row.postSalary,
        fieldAllowance: row.fieldAllowance === undefined ? null : row.fieldAllowance,
        performanceSalary: row.performanceSalary === undefined ? null : row.performanceSalary,
        studentStatus: row.studentStatus || "",
        schoolName: row.schoolName || "",
        retirementStatus: row.retirementStatus || "",
        incomeStartYearMonth: row.incomeStartYearMonth || "",
        servicePersonType: row.servicePersonType || "",
        insuranceType: row.insuranceType || "",
        warningReason: row.warningReason || "",
        legalEntityId: normalizePositiveDecimalId(row.matchedLegalEntityId),
        sealId: normalizePositiveDecimalId(row.recommendedSealId)
      })
      this.rowOriginalForm = Object.assign({}, this.rowForm)
      this.rowEditorOpen = true
    },
    buildRowPatch() {
      const payload = { version: this.editingRow && this.editingRow.version }
      ROW_EDITABLE_FIELDS.forEach(field => {
        if (!samePatchValue(this.rowOriginalForm[field], this.rowForm[field])) payload[field] = this.rowForm[field]
      })
      if (Object.prototype.hasOwnProperty.call(payload, "warningReason")) {
        payload.warningConfirmed = !!String(payload.warningReason || "").trim()
      }
      return payload
    },
    saveRow() {
      if (!this.editingRow || !this.batchId) return Promise.resolve(null)
      if (isMaskedPreviewValue(this.rowForm.currentAddress)) {
        this.$modal.msgWarning("现住址不能提交脱敏预览值，请输入完整新地址或留空保持不变")
        return Promise.resolve(null)
      }
      const payload = this.buildRowPatch()
      if (Object.keys(payload).length === 1) {
        this.$modal.msgWarning("没有需要保存的修改")
        return Promise.resolve(null)
      }
      this.updatingRow = true
      this.operationError = ""
      return updateOnboardSignImportRow(this.batchId, this.editingRow.rowId, payload).then(() => {
        this.rowEditorOpen = false
        return this.refreshBatch()
      }).catch(error => {
        this.operationError = this.errorMessage(error, "行资料保存失败")
        return null
      }).finally(() => { this.updatingRow = false })
    },
    sendDataRequests(targetRows) {
      const rowIds = Array.from(new Set((targetRows || []).filter(row => this.canRequestEmployeeData(row)).map(row => row.rowId)))
      if (!rowIds.length || !this.batchId) return Promise.resolve(null)
      this.refreshLoading = true
      this.operationError = ""
      return sendOnboardSignDataRequests(this.batchId, {
        requestId: this.createRequestId("onboard-data-request"),
        rowIds,
        signingSequence: this.signingSequence
      }).then(() => {
        this.$modal.msgSuccess(this.signingSequence === "SIGNATURE_FIRST"
          ? "入职签约包已发送；员工确认并完成唯一一次签名后，将进入待选公司与印章"
          : "补资任务已发送，员工提交后还需 HR 审核")
        return this.refreshBatch()
      }).catch(error => {
        this.operationError = this.errorMessage(error, this.signingSequence === "SIGNATURE_FIRST"
          ? "入职签约包发送失败" : "补资任务发送失败")
        return null
      }).finally(() => { this.refreshLoading = false })
    },
    openReview(row) {
      const requestId = normalizePositiveDecimalId(row && (row.dataRequestId || row.requestId))
      if (!requestId) return Promise.resolve(null)
      this.reviewing = true
      this.operationError = ""
      return getOnboardSignDataRequest(requestId).then(response => {
        const data = response && response.data !== undefined ? response.data : response
        this.reviewingRow = Object.assign({}, row, data || {}, { dataRequestId: requestId })
        this.reviewForm = Object.assign(this.emptyReviewForm(), {
          servicePersonType: this.reviewingRow.servicePersonType || "",
          insuranceType: this.reviewingRow.insuranceType || ""
        })
        this.reviewOpen = true
        return this.reviewingRow
      }).catch(error => {
        this.operationError = this.errorMessage(error, "员工提交的补资详情加载失败")
        return null
      }).finally(() => { this.reviewing = false })
    },
    submitReview(action) {
      const requestId = normalizePositiveDecimalId(this.reviewingRow && (this.reviewingRow.dataRequestId || this.reviewingRow.requestId))
      if (!requestId) return Promise.resolve(null)
      if (action === "REJECT" && !this.reviewForm.reason.trim()) {
        this.$modal.msgWarning("驳回时请填写原因")
        return Promise.resolve(null)
      }
      this.reviewing = true
      this.operationError = ""
      return reviewOnboardSignDataRequest(requestId, {
        action,
        version: this.reviewingRow.dataRequestVersion || this.reviewingRow.version,
        reason: this.reviewForm.reason,
        servicePersonType: this.reviewForm.servicePersonType,
        insuranceType: this.reviewForm.insuranceType
      }).then(() => {
        this.reviewOpen = false
        this.$modal.msgSuccess(action === "APPROVE" ? "审核已通过，正在重新匹配合同套餐" : "已驳回给员工重新填写")
        return this.refreshBatch()
      }).catch(error => {
        this.operationError = this.errorMessage(error, "补资审核失败")
        return null
      }).finally(() => { this.reviewing = false })
    },
    archiveSalaryRows(targetRows) {
      const rows = (targetRows || []).filter(row => row.employeeId)
      if (!rows.length || !this.batchId || this.busy) return Promise.resolve(null)
      this.archivingSalary = true
      this.operationError = ""
      return archiveOnboardSignSalary(this.batchId, rows).then(response => {
        this.$modal.msgSuccess("Excel综合工资和四项明细已存入员工档案")
        return response
      }).catch(error => {
        this.operationError = this.errorMessage(error, "工资入档结果暂未确认，可用相同导入行重试")
        return null
      }).finally(() => { this.archivingSalary = false })
    },
    generateRows(targetRows) {
      const rows = (targetRows || []).filter(row => this.isReadyToGenerate(row))
      const rowIds = Array.from(new Set(rows.map(row => row.rowId)))
      if (!rowIds.length || !this.batchId || !this.generationConfirmedFor(rows)) return Promise.resolve(null)
      const requiresHistoricalReason = this.targetRowsRequireHistoricalSupplement(rows)
      const requiresWarningReason = this.targetRowsRequireWarningReason(rows)
      this.generating = true
      this.operationError = ""
      if (!this.generateRequestId) this.generateRequestId = this.createRequestId("onboard-generate")
      return archiveOnboardSignSalary(this.batchId, rows).then(() => generateOnboardSignImport(this.batchId, {
        requestId: this.generateRequestId,
        batchVersion: this.batch && this.batch.version,
        rowIds,
        noExternalContractConfirmed: true,
        historicalReason: requiresHistoricalReason ? this.confirmation.historicalSupplementReason.trim() : "",
        warningReason: requiresWarningReason ? this.confirmation.warningReason.trim() : ""
      })).then(response => {
        this.generateRequestId = this.createRequestId("onboard-generate")
        this.$modal.msgSuccess(this.signingSequence === "SIGNATURE_FIRST"
          ? "最终合同已生成，本次不会自动发送；请先预览 PDF，再发送最终文件"
          : "合同已生成，本次不会自动发送，请先预览 PDF")
        return this.refreshBatch().then(() => {
          this.activeStep = 2
          return response
        })
      }).catch(error => {
        this.operationError = this.errorMessage(error, "合同生成结果暂未确认，请先刷新批次，不要更换请求号重复点击")
        return null
      }).finally(() => { this.generating = false })
    },
    sendRows(targetRows) {
      const taskIds = Array.from(new Set((targetRows || []).filter(row => this.isSendable(row)).map(row => normalizePositiveDecimalId(row.taskId)).filter(Boolean)))
      if (!taskIds.length) return Promise.resolve(null)
      this.sending = true
      this.sendingRowId = targetRows.length === 1 ? normalizePositiveDecimalId(targetRows[0].rowId) : ""
      this.operationError = ""
      if (!this.sendRequestId) this.sendRequestId = this.createRequestId("onboard-send")
      return sendSignTaskBatch({ requestId: this.sendRequestId, taskIds }).then(response => {
        const payload = response && response.data !== undefined ? response.data : response
        this.sendResults = Array.isArray(payload) ? payload : (payload && (payload.rows || payload.items)) || []
        this.sendRequestId = this.createRequestId("onboard-send")
        this.activeStep = 3
        this.$emit("completed", this.sendResults)
        return this.refreshBatch()
      }).catch(error => {
        this.operationError = this.errorMessage(error, "发送结果暂未确认，请刷新批次后使用原请求号重试")
        return null
      }).finally(() => {
        this.sending = false
        this.sendingRowId = ""
      })
    },
    openTask(row) {
      this.openTaskById(row && row.taskId)
    },
    openTaskById(value) {
      const taskId = normalizePositiveDecimalId(value)
      if (!taskId || taskId.length > 19) return
      const target = this.$router.resolve({ path: "/oa/sign-task", query: { taskId } })
      if (typeof window !== "undefined" && typeof window.open === "function") window.open(target.href, "_blank", "noopener")
    },
    summaryCount(kind) {
      if (kind === "matched") return this.rows.filter(row => !!normalizePositiveDecimalId(row && row.employeeId) &&
        String(row.matchType || "").toUpperCase() !== "MISSING").length
      if (kind === "missing") return this.rows.filter(row => this.blockingRowMissingFields(row).length > 0).length
      return 0
    },
    warningCode(value) { return typeof value === "string" ? value : (value && (value.code || value.warningCode)) || "WARNING" },
    warningText(value) {
      const code = typeof value === "string" ? value : (value && (value.code || value.warningCode))
      const localized = this.issueText(code)
      if (localized !== "数据需核对") return localized
      const message = typeof value === "string" ? value
        : (value && typeof value === "object" ? (value.message || value.text) : "")
      return this.displayMessage(message, "请核对导入警告")
    },
    issueText(value) {
      const rawCode = typeof value === "string" ? value : (value && value.code)
      const parts = String(rawCode || "").split(":")
      const code = parts.shift()
      const detail = parts.join(":")
      const labels = {
        EXISTING_OPEN_ONBOARD_TASK: "已有开放的入职签约任务",
        DUPLICATE_EXCEL_ROW: "同一员工在签约数据中重复出现",
        EMPLOYEE_NOT_AVAILABLE: "当前签约组织内找不到可用的员工档案",
        EXTRA_NOT_SELECTED: "该员工不在本次限定处理范围内",
        EMPLOYEE_PHONE_NOT_FOUND: "当前签约组织的授权范围内未找到该手机号对应的在职员工",
        EMPLOYEE_REHIRE_REQUIRED: "已找到同一员工的离职档案；请先在入职管理选择“恢复原账号（再入职）”，确认后重新上传",
        EMPLOYEE_ACCOUNT_DISABLED: "已找到同一员工档案，但账号仍为停用状态；请先完成账号启用或再入职确认",
        DUPLICATE_PROFILE_PHONE: "该手机号对应多个员工档案，无法安全确认唯一员工",
        PHONE_NAME_MISMATCH: "手机号已存在，但 Excel 姓名与员工档案姓名不一致",
        EMPLOYEE_IDENTITY_MISMATCH: "手机号和姓名已匹配，但 Excel 身份证号与员工档案不一致",
        DUPLICATE_PROFILE_IDENTITY: "该证件号对应多个员工档案，无法安全确认唯一员工",
        ID_MATCH_NAME_MISMATCH: "证件号已匹配，但 Excel 姓名与员工档案姓名不一致",
        IDENTITY_MUST_BE_TEXT: "身份证号必须以文本格式填写",
        INVALID_ID_NUMBER: "身份证号格式不正确",
        INVALID_PHONE: "手机号格式不正确",
        INVALID_CONTRACT_TYPE: "合同类型无效",
        INVALID_SOCIAL_TYPE: "社保类型无效",
        INVALID_CONTRACT_TERM: "合同期限类型无效",
        UNSUPPORTED_COMBINATION: "合同类型与社保类型的组合不受支持",
        OPEN_ENDED_RENDERING_NOT_SUPPORTED: "当前暂不支持生成无固定期限合同",
        INVALID_JOB_GRADE: "员工职级无效，应填写 2 至 9",
        INVALID_CONTRACT_DATES: "合同起止日期无效",
        INVALID_PROBATION_DATES: "试用期日期无效",
        INVALID_SALARY: "薪资数据无效",
        INVALID_SALARY_TOTAL: "薪资合计不正确",
        MISSING_EMPLOYEE_NAME: "员工姓名缺失",
        MISSING_PHONE: "员工手机号缺失",
        MISSING_IDENTITY: "员工证件信息缺失",
        MISSING_ORGANIZATION: "员工组织档案缺失，请先补齐所属组织",
        MISSING_POST: "员工岗位关联缺失，请先在员工档案配置岗位",
        MISSING_JOB_GRADE: "员工职级缺失",
        MISSING_CONTRACT_TYPE: "合同类型缺失",
        MISSING_CONTRACT_TERM: "合同期限类型缺失",
        MISSING_SOCIAL_TYPE: "社保类型缺失",
        MISSING_ENTRY_DATE: "员工入职日期缺失",
        MISSING_CONTRACT_DATES: "合同起止日期缺失",
        INVALID_STUDENT_STATUS: "在校状态无效",
        INVALID_RETIREMENT_STATUS: "退休状态无效",
        INVALID_INCOME_START_YEAR_MONTH: "主要生活来源起始月格式不正确",
        SALARY_OUTSIDE_REFERENCE_RANGE: "综合工资超出套餐参考范围，请 HR 核对",
        PLAN_SERVICE_FACT_MISMATCH: "劳务人员类型或保险类型与套餐不一致",
        PLAN_PREVIEW_FAILED: "签约套餐预览失败，请重新核对资料",
        GENERATION_RECOVERY_SOURCE_MISMATCH: "生成恢复时发现任务来源不一致，请重新预览",
        GENERATE_FAILED: "合同生成失败，请刷新后重试",
        PLAN_CHANGED_REPREVIEW: "签约套餐已变更，请重新预览",
        PROFILE_CHANGED_REPREVIEW: "员工资料已变更，请重新预览",
        COMPANY_CHANGED_REPREVIEW: "合同公司或印章已变更，请重新预览",
        COMPANY_MASTER_CHANGED_REPREVIEW: "公司主数据已变更，请重新预览",
        COMPANY_SEAL_CHANGED_REPREVIEW: "合同印章已变更，请重新预览",
        COMPANY_SELECTION_INVALID: "所选合同公司无效，请重新选择",
        COMPANY_MATCH_REQUIRES_HR: "公司匹配结果不唯一，请 HR 确认合同公司",
        COMPANY_MASTER_DATA_INCOMPLETE: "合同公司主数据不完整，请先补齐公司全称、统一社会信用代码、注册地址和法定代表人",
        COMPANY_SEAL_REQUIRES_HR: "合同印章无法唯一确定，请 HR 选择有效合同印章",
        COMPANY_SEAL_INVALID: "所选合同印章无效、已过期或校验失败，请重新选择",
        SALARY_VERSION_NOT_DERIVABLE: "无法根据社保类型确定薪酬确认书版本",
        SALARY_SOCIAL_MAPPING_CHANGED: "社保类型与薪酬确认书版本不一致，请重新预览",
        PACKAGE_PERSONAL_FACTS_NOT_FROZEN: "员工个人资料尚未确认，暂不能生成合同",
        SEND_PREPARATION_FAILED: "合同发送准备失败",
        PLAN_MISSING_ONBOARD_COMMITMENT: "当前发布的套餐缺少入职承诺书（2-3）",
        PLAN_MISSING_ONBOARD_LABOR_CONTRACT: "当前发布的劳动套餐缺少劳动合同（2-4）",
        PLAN_MISSING_ONBOARD_HANDBOOK_RECEIPT: "当前发布的劳动套餐缺少员工手册签收确认书（2-5）",
        PLAN_MISSING_ONBOARD_SALARY_CONFIRM: "当前发布的劳动套餐缺少薪酬结构确认书（2-6）",
        PLAN_MISSING_ONBOARD_SERVICE_CONTRACT: "当前发布的劳务套餐缺少劳务合同（2-7）",
        PLAN_MISSING_ONBOARD_SERVICE_RECEIPT: "当前发布的劳务套餐缺少劳务合同签收单（2-8）",
        PLAN_MISSING_ONBOARD_CONFIDENTIAL_NONCOMPETE: "当前发布的 7 级及以上套餐缺少保密与竞业限制协议（2-9）",
        PLAN_MISSING_ONBOARD_MINOR_NONSTUDENT_DECLARATION: "当前发布的套餐缺少非在校未成年人声明（2-10）",
        FORMULA_NOT_ALLOWED: "不允许使用公式",
        MISSING_VALUE: "缺少必填值",
        INVALID_DATE: "日期格式不正确",
        INVALID_MONEY: "金额格式不正确"
      }
      if (labels[code]) {
        const detailLabel = detail ? this.fieldLabel(detail) : ""
        return detailLabel && detailLabel !== detail ? `${labels[code]}：${detailLabel}` : labels[code]
      }
      const message = typeof value === "string" ? value
        : (value && typeof value === "object" ? (value.message || value.label) : "")
      return this.displayMessage(message, "数据需核对")
    },
    displayMessage(value, fallback = "数据需核对") {
      if (value === undefined || value === null || String(value).trim() === "") return fallback
      const text = String(value).trim()
      if (/^[A-Z][A-Z0-9_]*(?::[^\s]*)?$/.test(text)) return fallback
      const localized = text
        .replace(/MANUAL_SIGN_EXCEL_IMPORT/g, "签约名单导入")
        .replace(/EXISTING_OPEN_ONBOARD_TASK/g, "已有开放的入职签约任务")
        .replace(/PENDING_FINAL_CONFIRM/g, "待员工确认最终合同")
        .replace(/PENDING_COMPANY/g, "待选公司和印章")
        .replace(/PENDING_SIGN/g, "待员工签署")
        .replace(/READY_TO_SEND/g, "待发送")
        .replace(/WAITING_HR_CONFIRM/g, "待 HR 确认")
        .replace(/GENERATE_FAILED/g, "合同生成失败")
        .replace(/VIEWED/g, "已查看未签")
        .replace(/SIGNED/g, "已完成")
        .replace(/SENDING/g, "发送中")
        .replace(/REFUSED/g, "员工拒签")
        .replace(/EXPIRED/g, "已逾期")
        .replace(/CANCELLED/g, "已取消")
        .replace(/FAILED/g, "处理失败")
        .replace(/[A-Z][A-Z0-9]*_[A-Z0-9_]+/g, "相关状态")
      return /[A-Za-z]/.test(localized) && !/[\u3400-\u9fff]/.test(localized) ? fallback : localized
    },
    sendResultText(result) {
      if (result && result.message) return this.displayMessage(result.message, "已处理")
      const labels = {
        SENT: "发送成功",
        ALREADY_SENT: "此前已发送",
        IN_PROGRESS: "正在发送",
        BLOCKED: "暂不能发送",
        FAILED: "发送失败",
        ERROR: "发送失败"
      }
      return labels[String(result && result.result || "").toUpperCase()] || "已处理"
    },
    fieldLabel(value) {
      const key = typeof value === "string" ? value : (value && (value.field || value.code))
      return {
        currentAddress: "现住址",
        studentStatus: "当前是否在校",
        schoolName: "学校信息",
        retirementStatus: "是否退休",
        incomeStartYearMonth: "主要生活来源起始月",
        contractTypeCode: "合同类型",
        socialTypeCode: "社保类型",
        contractTermCode: "合同期限",
        employeePost: "员工岗位",
        jobGradeCode: "员工职级",
        workLocation: "工作地",
        cityLevel: "城市等级",
        contractStartDate: "合同开始日",
        contractEndDate: "合同结束日",
        probationStartDate: "试用期开始日",
        probationEndDate: "试用期结束日",
        salaryTotal: "综合工资",
        baseSalary: "底薪",
        postSalary: "岗位津贴",
        fieldAllowance: "驻外补贴",
        performanceSalary: "绩效津贴",
        servicePersonType: "劳务人员类型（HR 确定）",
        insuranceType: "保险类型（HR 确定）",
        legalEntityId: "合同公司（HR 确认）",
        sealId: "合同印章（HR 确认）",
        noExternalContractConfirmation: "无外部已签合同确认",
        historicalSupplementReason: "历史补签原因",
        warningConfirmationReason: "黄色预警确认原因"
      }[key] || profileFieldLabel(key || String(value || ""))
    },
    beforeClose(done) {
      if (this.busy) {
        this.$modal.msgWarning("当前操作正在处理，请稍候")
        return
      }
      done()
    },
    errorMessage(error, fallback) {
      const message = error && error.response && error.response.data && error.response.data.msg
        ? error.response.data.msg
        : (error && error.message) || fallback
      return this.displayMessage(message, fallback)
    },
    createRequestId(prefix) {
      if (typeof crypto !== "undefined" && typeof crypto.randomUUID === "function") return crypto.randomUUID()
      return `${prefix}-${Date.now()}-${Math.random().toString(16).slice(2)}`
    }
  }
}
</script>

<style lang="scss" scoped>
.onboard-import-steps { margin: -6px 0 14px; }
.onboard-import-alert { margin-bottom: 12px; }
.onboard-import-alert.compact { margin-bottom: 6px; }
.onboard-import-source {
  display: grid;
  grid-template-columns: 1fr;
  gap: 10px;
  margin-bottom: 14px;
  padding: 12px;
  border: 1px solid #e2e8f0;
  border-radius: 12px;
  background: #f8fafc;
}
.onboard-import-scope, .onboard-import-employees, .onboard-import-upload {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px 12px;
}
.onboard-import-scope small, .onboard-import-employees small { color: #64748b; }
.onboard-import-employees strong { color: #4055cf; font-size: 15px; }
.onboard-import-upload { justify-content: space-between; align-items: flex-start; }
.onboard-import-upload ::v-deep .el-upload-list { max-width: 520px; }
.onboard-import-upload ::v-deep .el-upload__tip { margin-top: 4px; color: #64748b; }
.onboard-signing-sequence {
  display: grid;
  gap: 10px;
  margin-bottom: 14px;
  padding: 12px;
  border: 1px solid #dbe3f0;
  border-radius: 12px;
  background: #ffffff;
}
.onboard-signing-sequence > div:first-child { display: flex; align-items: baseline; flex-wrap: wrap; gap: 6px 12px; }
.onboard-signing-sequence small { color: #64748b; }
.onboard-signing-sequence ::v-deep .el-alert { margin: 0; }
.onboard-import-summary {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px 24px;
  margin-bottom: 10px;
  padding: 10px 12px;
  border: 1px solid #dbeafe;
  border-radius: 10px;
  background: #eff6ff;
  color: #475569;
}
.onboard-import-summary strong { color: #334ac0; }
.cell-subtext { margin-top: 4px; color: #8290a5; font-size: 11px; line-height: 1.5; }
.template-tags, .field-tags { display: flex; flex-wrap: wrap; gap: 3px; margin-top: 5px; }
.company-recommendation {
  display: flex;
  flex-direction: column;
  gap: 2px;
  margin-top: 6px;
  padding: 6px 8px;
  border: 1px solid #d8e1f0;
  border-radius: 6px;
  background: #f8fafc;
  color: #596579;
  font-size: 11px;
  line-height: 1.45;
}
.company-recommendation strong { color: #44546a; }
.company-match-result, .candidate-detail {
  display: flex;
  flex-direction: column;
  gap: 2px;
  margin-top: 6px;
  color: #475569;
  font-size: 11px;
  line-height: 1.45;
}
.company-match-result strong { color: #334ac0; }
.company-match-result .is-warning { color: #b7791f; }
.salary-version-result { margin-top: 6px; color: #166534; font-size: 11px; line-height: 1.45; }
.candidate-detail { padding: 7px 9px; border: 1px solid #e2e8f0; border-radius: 6px; background: #f8fafc; }
.recommendation-detail { margin-bottom: 14px; }
.validation-lines { display: flex; flex-direction: column; gap: 3px; font-size: 12px; line-height: 1.45; }
.validation-lines.is-error { color: #c2414b; }
.validation-lines.is-warning { margin-top: 4px; color: #b7791f; }
.existing-task-fact {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 3px;
  margin-bottom: 7px;
  padding: 8px;
  border: 1px solid #f2b8bd;
  border-radius: 7px;
  background: #fff5f5;
  color: #a82431;
  font-size: 12px;
}
.existing-task-fact small { color: #8f3d45; line-height: 1.45; }
.existing-task-fact ::v-deep .el-button { padding: 2px 0 0; color: #a82431; }
.latest-task-fact { margin-bottom: 6px; color: #64748b; font-size: 11px; }
.onboard-import-actions-panel, .onboard-import-confirmation, .onboard-import-results {
  margin-top: 12px;
  padding: 12px 14px;
  border: 1px solid #e2e8f0;
  border-radius: 10px;
}
.onboard-import-actions-panel { display: flex; align-items: center; justify-content: space-between; gap: 14px; }
.onboard-import-actions-panel > div { display: flex; min-width: 0; flex-direction: column; gap: 4px; }
.onboard-import-actions-panel span { color: #64748b; font-size: 12px; }
.onboard-import-confirmation { display: flex; flex-direction: column; gap: 10px; background: #fffbf2; border-color: #f1d7a9; }
.historical-confirmation { display: flex; flex-direction: column; gap: 9px; }
.onboard-import-results { display: flex; align-items: center; flex-wrap: wrap; gap: 8px; }
.onboard-import-footer { display: flex; justify-content: flex-end; flex-wrap: wrap; gap: 8px; }
.onboard-import-footer ::v-deep .el-button + .el-button { margin-left: 0; }
.review-form { margin-top: 14px; }
@media (max-width: 900px) {
  .onboard-import-actions-panel { align-items: stretch; flex-direction: column; }
}
</style>
