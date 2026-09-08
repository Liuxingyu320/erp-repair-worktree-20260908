<template>
  <div :class="['mobile-sign-package-page', 'mobile-system-page', { 'sign-panel-expanded': showSignBar && signPanelOpen }]" data-mobile-scroll-root>
    <header class="mobile-sign-header">
      <button type="button" class="back-button" aria-label="返回" @click="goBack"><i class="el-icon-arrow-left" aria-hidden="true" /></button>
      <div>
        <h1>我的签约</h1>
        <p>{{ headerSubtitle }}</p>
      </div>
      <button type="button" class="refresh-button" :disabled="loading || detailLoading" @click="refreshCurrentView">
        {{ loading ? '刷新中' : '刷新' }}
      </button>
    </header>

    <div v-if="pageLevelError" class="state-panel request-error mobile-data-state mobile-data-state--network-error" role="alert">
      <strong class="mobile-data-state__title">{{ pageLevelError.title }}</strong>
      <span class="mobile-data-state__description">{{ pageLevelError.description }}</span>
      <button type="button" class="state-action-button mobile-button mobile-button--secondary" :disabled="loading || detailLoading" @click="handlePageLevelErrorAction">
        {{ pageLevelError.actionLabel }}
      </button>
    </div>

    <div v-if="dataRequestListError && !selectedDataRequest" class="state-panel mobile-data-state mobile-data-state--warning" role="status">
      <strong class="mobile-data-state__title">入职资料签约扩展暂时不可用</strong>
      <span class="mobile-data-state__description">普通签约包仍可正常查看；可稍后重试入职资料功能。</span>
      <button type="button" class="state-action-button mobile-button mobile-button--secondary" :disabled="loading || dataRequestListLoading" @click="retryDataRequestList">
        {{ dataRequestListLoading ? '重试中' : '重试入职资料' }}
      </button>
    </div>

    <main v-if="!selectedPackage && !selectedDataRequest && !pageLevelError" class="package-list">
      <div class="status-tabs mobile-filter-chips" role="tablist" aria-label="签约状态">
        <button
          v-for="tab in statusTabs"
          :key="tab.value"
          type="button"
          :class="{ active: activeStatus === tab.value }"
          :aria-selected="activeStatus === tab.value ? 'true' : 'false'"
          @click="activeStatus = tab.value"
        >
          <span>{{ tab.label }}</span>
          <em>{{ statusCount(tab.value) }}</em>
        </button>
      </div>
      <div v-if="loading" class="state-panel mobile-data-state mobile-data-state--loading" role="status">签约包加载中...</div>
      <div v-else-if="detailLoading" class="state-panel mobile-data-state mobile-data-state--loading" role="status">签约详情加载中...</div>
      <div v-else-if="filteredPackages.length === 0 && filteredDataRequests.length === 0" class="state-panel mobile-data-state mobile-data-state--empty">还没有相关签约任务</div>
      <template v-else>
        <button
          v-for="request in filteredDataRequests"
          :key="'onboard-request-' + request.requestId"
          type="button"
          class="package-row onboard-request-row"
          :disabled="detailLoading"
          @click="openDataRequest(request)"
        >
          <div>
            <strong>{{ dataRequestTitle(request) }}</strong>
            <span>{{ dataRequestSummary(request) }}</span>
            <span>{{ dataRequestProgressSummary(request) }}</span>
            <span v-if="request.reviewReason">HR意见：{{ request.reviewReason }}</span>
          </div>
          <em :class="['status-chip', dataRequestChipClass(request)]">{{ dataRequestStatusLabel(request) }}</em>
        </button>
        <button
          v-for="item in filteredPackages"
          :key="item.packageId"
          type="button"
          class="package-row"
          :disabled="detailLoading"
          @click="openPackage(item)"
        >
          <div>
            <strong>{{ scenarioLabel(item.scenario) }}签约包</strong>
            <span>{{ item.employeeNameSnapshot || '-' }} · {{ item.postNameSnapshot || '-' }}</span>
            <span>{{ documentCount(item) }} 个文件</span>
            <span v-if="packageDataRequest(item)">{{ packageDataRequestProgressSummary(item) }}</span>
            <span>{{ deadlineSummary(item) }}</span>
          </div>
          <em :class="['status-chip', item.status]">{{ statusLabel(item.status) }}</em>
        </button>
      </template>
    </main>

    <main v-else-if="selectedDataRequest" class="onboard-request-detail">
      <mobile-onboard-data-request
        ref="onboardDataRequest"
        embedded
        :request-id="selectedDataRequest.requestId"
        @loaded="handleDataRequestLoaded"
        @updated="handleDataRequestUpdated"
        @back="closeDataRequest"
      />
    </main>

    <main v-else-if="selectedPackage" class="package-detail">
      <section class="detail-card">
        <div class="detail-head">
          <div>
            <h2>{{ scenarioLabel(selectedPackage.scenario) }}签约包</h2>
            <p>{{ selectedPackage.employeeNameSnapshot || '-' }} · {{ dictionaryLabel(selectedPackage.employmentType) }}</p>
          </div>
          <em :class="['status-chip', selectedPackage.status]">{{ statusLabel(selectedPackage.status) }}</em>
        </div>
        <div v-if="!isTerminalStatus(selectedPackage.status)" class="progress-strip" aria-label="签约进度">
          <span
            v-for="step in progressSteps"
            :key="step.value"
            :class="{ active: isProgressStepActive(step.value) }"
          >{{ step.label }}</span>
        </div>
        <dl class="snapshot-grid">
          <div><dt>岗位</dt><dd>{{ selectedPackage.postNameSnapshot || '-' }}</dd></div>
          <div><dt>社保</dt><dd>{{ dictionaryLabel(selectedPackage.socialType) }}</dd></div>
          <div><dt>入职</dt><dd>{{ selectedPackage.entryDate || '-' }}</dd></div>
          <div><dt>签署截止</dt><dd>{{ deadlineSummary(selectedPackage) }}</dd></div>
          <div v-if="!isSignatureFirstWaitingCompany() && selectedPackage.legalEntityNameSnapshot"><dt>合同公司</dt><dd>{{ selectedPackage.legalEntityNameSnapshot }}</dd></div>
          <div v-if="!isSignatureFirstWaitingCompany() && selectedPackage.sealNameSnapshot"><dt>合同印章</dt><dd>{{ selectedPackage.sealNameSnapshot }}</dd></div>
        </dl>
      </section>

      <section v-if="showEmbeddedOnboardDataStage" class="detail-card staged-package-card">
        <div class="section-head staged-package-heading">
          <div>
            <h2>确认入职签约包并签名</h2>
            <p>这是本签约包的员工确认阶段。请核对冻结事实和文件清单，并完成唯一一次手写签名。</p>
          </div>
          <em class="status-chip pending_sign">仅签一次</em>
        </div>
        <mobile-onboard-data-request
          ref="packageOnboardDataRequest"
          embedded
          :request-id="selectedPackageDataRequest.requestId"
          @loaded="handlePackageDataRequestLoaded"
          @updated="handlePackageDataRequestUpdated"
        />
      </section>

      <section v-else-if="isSignatureFirstEmployeeStage" class="detail-card state-panel request-error" role="alert">
        <strong>签约包员工确认阶段加载失败</strong>
        <span>{{ dataRequestListError || '暂未取得本签约包的员工确认资料，请刷新后重试。' }}</span>
        <button type="button" class="state-action-button" :disabled="loading" @click="loadList">重新加载签约包</button>
      </section>

      <section v-if="!isSignatureFirstEmployeeStage && !isSignatureFirstWaitingCompany()" class="detail-card">
        <div class="section-head">
          <h2>文件清单</h2>
          <span>{{ fileListProgressCount }}/{{ fileListProgressTotal }}</span>
        </div>
        <button
          v-for="document in documents"
          :key="document.documentId"
          type="button"
          :class="['document-row', activeDocument && activeDocument.documentId === document.documentId ? 'active' : '']"
          @click="selectDocument(document)"
        >
          <div>
            <strong>{{ document.documentName }}</strong>
            <span>{{ documentPolicyLabel(document) }} · {{ documentProgressLabel(document) }} · {{ documentLoadStateLabel(document) }}</span>
          </div>
          <em>{{ documentStateLabel(document) }}</em>
        </button>
      </section>

      <section v-if="!isSignatureFirstEmployeeStage && !isSignatureFirstWaitingCompany() && activeDocument" class="detail-card file-card">
        <div class="section-head">
          <h2>{{ activeDocument.documentName }}</h2>
          <div class="file-links">
            <button v-if="primaryPreviewPageUrl" type="button" @click="openPrimaryDocumentViewer">打开文件</button>
            <a v-if="finalDocumentFileUrl" :href="finalDocumentFileUrl" target="_blank" rel="noopener">最终归档文件</a>
            <button
              v-if="canExportFinalDocument(activeDocument)"
              type="button"
              :disabled="finalExportingDocumentId === String(activeDocument.documentId)"
              @click="exportFinalDocument(activeDocument)"
            >{{ finalExportingDocumentId === String(activeDocument.documentId) ? '导出中...' : '导出签章展示版' }}</button>
            <a v-if="signedDocumentFileUrl" :href="signedDocumentFileUrl" target="_blank" rel="noopener">首次签名文件</a>
            <a v-if="certificateFileUrl" :href="certificateFileUrl" target="_blank" rel="noopener">签署证明</a>
          </div>
          <p v-if="canExportFinalDocument(activeDocument)" class="final-export-hint">签章展示版用于查看签名和盖章位置，原最终归档不变</p>
        </div>
        <div v-if="fileLoading || previewPageLoading" class="state-panel compact" role="status">文件加载中...</div>
        <div v-else-if="fileError" class="state-panel compact file-error" role="alert">
          <strong>{{ fileError }}</strong>
          <span>请检查网络后重试，文件成功加载前不会记录阅读或确认。</span>
          <button type="button" class="retry-file-button" @click="retryActiveDocumentFiles">重新加载</button>
        </div>
        <div v-else-if="primaryPreviewPageUrl" class="document-preview">
          <img
            :key="primaryDocumentFrameKey"
            ref="primaryDocumentFrame"
            class="document-preview-image"
            :src="primaryPreviewPageUrl"
            :alt="activeDocument.documentName + '第' + previewPageNumber + '页'"
            :data-load-token="primaryFrameLoadContext ? primaryFrameLoadContext.token : ''"
            :data-object-url="primaryPreviewPageUrl"
            @load="handlePrimaryDocumentFrameLoad"
            @error="handlePrimaryDocumentFrameError"
          >
          <span v-if="showPendingFinalWatermark" class="pending-final-watermark" aria-hidden="true">待确认文件</span>
          <div class="preview-page-controls" aria-label="合同翻页">
            <button type="button" :disabled="previewPageNumber <= 1 || previewPageLoading" @click="showPreviousPreviewPage">上一页</button>
            <span v-if="showPendingFinalWatermark">第 {{ previewPageNumber }}/{{ previewPageCount }} 页 · {{ activeDocument.finalReadConfirmed === 'Y' ? '已打开' : '待记录' }}</span>
            <span v-else>第 {{ previewPageNumber }}/{{ previewPageCount }} 页 · 已查看 {{ currentPreviewLoadedPageCount }}/{{ previewPageCount }}</span>
            <button type="button" :disabled="previewPageNumber >= previewPageCount || previewPageLoading" @click="showNextPreviewPage">下一页</button>
          </div>
        </div>
        <div v-if="primaryPreviewPageUrl && !fileLoading && !fileError" class="preview-fallback">
          <span>已使用移动端兼容预览</span>
          <button type="button" @click="openPrimaryDocumentViewer">打开文件</button>
        </div>
        <div v-else-if="!fileLoading && !fileError" class="state-panel compact">当前文件尚未提供</div>
        <div v-if="fileWarnings.length" class="file-warning" role="status">
          <span>{{ fileWarnings.join('；') }}</span>
          <button type="button" @click="retryActiveDocumentFiles">重试附件</button>
        </div>
        <button
          v-if="!isCompanyFirstSequence && requiresReadConfirmation(activeDocument) && (selectedPackage.status === 'pending_sign' || selectedPackage.status === 'part_viewed')"
          type="button"
          class="read-button"
          :disabled="activeDocument.readConfirmed === 'Y' || refusing || nonReadMutationReplayLocked || (readFrozenMutation && !isFrozenReadMutationFor(activeDocument)) || deadlineMissing || deadlineExpired || fileLoading || !currentDocumentPrimaryLoaded || !!readConfirmingDocumentId"
          @click="confirmRead(activeDocument)"
        >
          {{ readButtonLabel(activeDocument) }}
        </button>
        <p
          v-if="isReadErrorFor(activeDocument)"
          class="action-error"
          role="alert"
        >{{ readActionError }}</p>
        <p v-else-if="!requiresReadConfirmation(activeDocument)" class="read-not-required">该文件可直接查看，无需单独确认。</p>
      </section>

      <section v-if="selectedPackage.status === 'pending_company'" class="detail-card waiting-card">
        <h2>唯一一次手写签名已完成</h2>
        <p>正在等待 HR 选择合同公司与有效印章，并生成最终合同。生成后还会由 HR 另行发送最终文件。</p>
        <strong>收到最终文件后只需核对确认，不会再次签名。</strong>
      </section>

      <section v-if="selectedPackage.status === 'pending_final_confirm'" class="detail-card final-confirm-card">
        <h2>核对待确认文件</h2>
        <p>HR 已选择公司与印章并发送最终文件。请逐份打开阅读，核对公司法定全称和印章后完成确认。</p>
        <dl class="snapshot-grid">
          <div><dt>合同公司</dt><dd>{{ selectedPackage.legalEntityNameSnapshot || '未填写' }}</dd></div>
          <div><dt>合同印章</dt><dd>{{ selectedPackage.sealNameSnapshot || '未填写' }}</dd></div>
        </dl>
        <label class="final-confirm-check">
          <input
            v-model="finalConfirmChecked"
            type="checkbox"
            :disabled="!allFinalDocumentsConfirmed || finalConfirming || mutationReplayLocked"
          >
          <span>本人已阅读并核对待确认文件中的公司及印章信息</span>
        </label>
        <p class="final-load-progress">待确认文件已打开并记录 {{ finalReadConfirmedCount }}/{{ documents.length }} 份</p>
        <p v-if="finalReadActionError" class="action-error" role="alert">{{ finalReadActionError }}</p>
        <p v-if="finalConfirmDisabledReason" class="confirm-blocked-reason">{{ finalConfirmDisabledReason }}</p>
        <p v-if="finalConfirmError" class="action-error" role="alert">{{ finalConfirmError }}</p>
        <button type="button" class="primary-button final-confirm-button" :disabled="!!finalConfirmDisabledReason || finalConfirming || refusing || !!signFrozenMutation || !!refuseFrozenMutation" @click="submitFinalConfirm">
          {{ finalConfirming ? '确认中...' : finalConfirmError ? '重试确认文件' : '确认文件' }}
        </button>
        <p class="confirm-help">本次仅确认当前待确认内容，不会要求您重新签名。</p>
      </section>

      <section v-if="selectedPackage.status === 'signed'" class="detail-card evidence-card">
        <h2>最终归档 · 已完成</h2>
        <dl class="snapshot-grid">
          <div><dt>首次签名时间</dt><dd>{{ formatDateTime(firstSignatureTime) }}</dd></div>
          <div><dt>最终确认时间</dt><dd>{{ formatDateTime(selectedPackage.finalConfirmedTime) }}</dd></div>
          <div><dt>文件数</dt><dd>{{ documents.length }}</dd></div>
        </dl>
      </section>

      <section v-if="deadlineExpired && !isTerminalStatus(selectedPackage.status)" class="detail-card terminal-card expired">
        <h2>签署时间已截止</h2>
        <p>当前签约包已超过签署截止时间，阅读确认、手写签名、最终确认和拒签操作已停用。</p>
        <strong>请等待系统更新过期状态，或联系合同经办人。</strong>
      </section>

      <section v-if="deadlineMissing" class="detail-card terminal-card expired">
        <h2>签署期限信息不完整</h2>
        <p>当前签约包缺少服务端冻结的截止时间或期限策略，所有员工签约操作已停用。</p>
        <strong>请联系合同经办人修复，不要继续提交。</strong>
      </section>

      <section v-if="isTerminalStatus(selectedPackage.status)" :class="['detail-card', 'terminal-card', selectedPackage.status]">
        <h2>{{ selectedPackage.status === 'refused' ? '本次签约已拒签' : '本次签约已过期' }}</h2>
        <p>终态记录不会恢复为待签；此前的发送文件、首次签名文件、最终合同和签署证明仅供只读查看。</p>
        <dl class="snapshot-grid">
          <div><dt>终止时间</dt><dd>{{ formatDateTime(selectedPackage.terminalTime) }}</dd></div>
          <div><dt>原签署截止</dt><dd>{{ formatDateTime(selectedPackage.signDeadline) }}</dd></div>
          <div><dt>原因分类</dt><dd>{{ refusalReasonLabel(selectedPackage.terminalReasonCode) }}</dd></div>
          <div><dt>处置状态</dt><dd>{{ resolutionStatusLabel(selectedPackage.resolutionStatus) }}</dd></div>
        </dl>
        <p class="terminal-reason"><strong>原因说明：</strong>{{ selectedPackage.terminalReasonDetail || '未记录说明' }}</p>
      </section>

      <section v-if="canRefuse" class="detail-card refusal-entry-card">
        <h2>对合同内容有异议？</h2>
        <p>可以提交拒签原因。拒签后本版本将永久终止，不能继续签名或确认。</p>
        <button type="button" class="danger-outline-button" :disabled="signing || finalConfirming || refusing || !!signFrozenMutation || !!finalConfirmFrozenMutation || !!readConfirmingDocumentId" @click="openRefusal">
          申请拒签
        </button>
      </section>
    </main>

    <div v-if="primaryViewerOpen" class="pdf-viewer-overlay" role="dialog" aria-modal="true" aria-label="合同文件查看器">
      <header class="pdf-viewer-header">
        <button type="button" @click="closePrimaryDocumentViewer">关闭</button>
        <strong>{{ activeDocument ? activeDocument.documentName : '合同文件' }}</strong>
        <span>{{ previewPageNumber }}/{{ previewPageCount }}</span>
      </header>
      <main class="pdf-viewer-body">
        <div v-if="previewPageLoading" class="state-panel compact" role="status">页面加载中...</div>
        <div v-else-if="fileError" class="state-panel compact file-error" role="alert">
          <strong>{{ fileError }}</strong>
          <button type="button" class="retry-file-button" @click="retryActiveDocumentFiles">重新加载</button>
        </div>
        <div v-else-if="primaryPreviewPageUrl" class="pdf-viewer-page" :style="viewerImageStyle">
          <img
            class="pdf-viewer-image"
            :src="primaryPreviewPageUrl"
            :alt="(activeDocument ? activeDocument.documentName : '合同文件') + '第' + previewPageNumber + '页'"
          >
          <span v-if="showPendingFinalWatermark" class="pending-final-watermark viewer-watermark" aria-hidden="true">待员工最终确认</span>
        </div>
      </main>
      <footer class="pdf-viewer-footer">
        <button type="button" :disabled="previewPageNumber <= 1 || previewPageLoading" @click="showPreviousPreviewPage">上一页</button>
        <button type="button" :disabled="previewZoom <= 1" aria-label="缩小合同" @click="zoomPreviewOut"><i class="el-icon-zoom-out" aria-hidden="true" /></button>
        <span>第 {{ previewPageNumber }}/{{ previewPageCount }} 页 · {{ Math.round(previewZoom * 100) }}%</span>
        <button type="button" :disabled="previewZoom >= 3" aria-label="放大合同" @click="zoomPreviewIn"><i class="el-icon-zoom-in" aria-hidden="true" /></button>
        <button type="button" :disabled="previewPageNumber >= previewPageCount || previewPageLoading" @click="showNextPreviewPage">下一页</button>
      </footer>
    </div>

    <div v-if="refuseOpen" class="mobile-dialog-backdrop" @click.self="closeRefusal">
      <section class="mobile-refusal-dialog" role="dialog" aria-modal="true" aria-labelledby="refusal-dialog-title">
        <div class="mobile-dialog-head">
          <div>
            <h2 id="refusal-dialog-title">提交拒签原因</h2>
            <p>原因将写入本次签约的不可变更记录。</p>
          </div>
          <button type="button" :disabled="refusing" aria-label="关闭拒签对话框" @click="closeRefusal"><i class="el-icon-close" aria-hidden="true" /></button>
        </div>
        <label class="mobile-field">
          <span>原因分类</span>
          <select v-model="refuseForm.reasonCode" :disabled="refusing || !!refuseError || mutationReplayLocked">
            <option value="" disabled>请选择</option>
            <option v-for="option in refusalReasonOptions" :key="option.value" :value="option.value">{{ option.label }}</option>
          </select>
        </label>
        <label class="mobile-field">
          <span>原因说明</span>
          <textarea
            v-model.trim="refuseForm.reasonDetail"
            :disabled="refusing || !!refuseError || mutationReplayLocked"
            maxlength="500"
            rows="4"
            placeholder="请具体说明需要核对或修改的内容"
          ></textarea>
          <small>{{ refuseForm.reasonDetail.length }}/500</small>
        </label>
        <p v-if="refuseError" class="action-error" role="alert">{{ refuseError }}</p>
        <div class="mobile-dialog-actions">
          <button type="button" class="ghost-button" :disabled="refusing" @click="closeRefusal">取消</button>
          <button type="button" class="danger-button" :disabled="refusing || !!signFrozenMutation || !!finalConfirmFrozenMutation" @click="requestRefusal">
            {{ refusing ? '提交中...' : refuseError ? '使用原请求号重试' : '继续并二次确认' }}
          </button>
        </div>
      </section>
    </div>

    <div v-if="showSignBar" :class="['sticky-sign-bar', { expanded: signPanelOpen }]">
      <button v-if="!signPanelOpen" type="button" class="sign-panel-summary" @click="openSignPanel">
        <span>签署面板</span>
        <strong>{{ requiredReadCount }}/{{ requiredDocumentCount }} 已确认</strong>
        <em>{{ signDisabledReason || '可提交签署' }}</em>
      </button>
      <template v-else>
      <div class="sign-panel-head">
        <strong>签署面板</strong>
        <button type="button" @click="signPanelOpen = false">收起</button>
      </div>
      <div class="sign-panel-body">
        <div class="signature-box">
          <canvas
            ref="signatureCanvas"
            :aria-disabled="signing || mutationReplayLocked"
            @pointerdown.prevent="startDraw"
            @pointermove.prevent="draw"
            @pointerup.prevent="finishDraw"
            @pointercancel.prevent="finishDraw"
            @mousedown="startDraw"
            @mousemove="draw"
            @mouseup="finishDraw"
            @mouseleave="finishDraw"
            @touchstart.prevent="startDraw"
            @touchmove.prevent="draw"
            @touchend.prevent="finishDraw"
          ></canvas>
        </div>
        <button type="button" class="ghost-button" :disabled="signing || mutationReplayLocked" @click="clearSignature">清空签名</button>
        <label class="confirm-text-row">
          <span>签名确认语</span>
          <input
            v-model.trim="signConfirmText"
            type="text"
            autocomplete="off"
            :disabled="signing || mutationReplayLocked"
            placeholder="输入：本人确认签署本签约包"
          >
        </label>
        <p v-if="signDisabledReason">{{ signDisabledReason }}</p>
        <p v-if="signError" class="action-error" role="alert">{{ signError }}</p>
      </div>
      <div class="sign-panel-footer">
        <button type="button" class="primary-button" :disabled="!!signDisabledReason || signing || refusing || !!finalConfirmFrozenMutation || !!refuseFrozenMutation || !!readConfirmingDocumentId" @click="submitSign">
          {{ signing ? '提交中...' : signError ? '重试提交签署' : isCompanyFirstSequence ? '手写签名并最终确认' : '一次签署全部必签文件' }}
        </button>
      </div>
      </template>
    </div>
  </div>
</template>

<script>
import {
  confirmSignPackageFinalDocumentRead,
  confirmSignPackageDocumentRead,
  confirmMyFinalSignPackage,
  downloadMySignPackageCertificate,
  downloadMySignPackageDocument,
  downloadMySignPackageDocumentPreviewPage,
  downloadMyFinalSignPackageDocument,
  downloadMyFinalSignPackageDocumentExport,
  downloadMySignedSignPackageDocument,
  getMySignPackageDocumentPreview,
  getMySignPackage,
  listMySignPackages,
  refuseMySignPackage,
  signMySignPackage
} from "@/api/oa/signPackage"
import { getSignTaskCapabilities, listMyOnboardSignDataRequests } from "@/api/oa/signTask"
import { signDictionaryLabel, signPackageStatusLabel } from "@/utils/signDictionary"
const { createTodoPersonalFocusMixin } = require("@/mixins/todoBusinessFocus")
const { dateTimeValue, formatSignDateTime } = require("@/utils/signDateTime")
const mobileViewport = require("../mobileViewport")
const startMobileViewportSync = typeof mobileViewport.startMobileViewportSync === "function"
  ? mobileViewport.startMobileViewportSync
  : () => false
const stopMobileViewportSync = typeof mobileViewport.stopMobileViewportSync === "function"
  ? mobileViewport.stopMobileViewportSync
  : () => false
const { signScenarioLabel } = require("@/utils/signScenario")
const {
  allFinalDocumentsLoaded,
  allInitialDocumentsLoaded,
  createDocumentFrameLoadContext,
  documentLoadKey,
  hasCompleteFinalMetadata,
  isDocumentLoaded,
  matchesDocumentFrameLoadContext,
  previewPageLoadKey,
  validatePdfBlob,
  requiresReadConfirmation: requiresDocumentRead
} = require("@/utils/signPackageFileGate")
const {
  EMPLOYEE_ACTION_STATUSES,
  REFUSAL_REASON_OPTIONS,
  TERMINAL_PACKAGE_STATUSES,
  createProgressSteps,
  createStatusTabs,
  dataRequestChipClass: resolveDataRequestChipClass,
  dataRequestProgressSummary: resolveDataRequestProgressSummary,
  dataRequestRawStatus: resolveDataRequestRawStatus,
  dataRequestStatusLabel: resolveDataRequestStatusLabel,
  dataRequestStatusTab: resolveDataRequestStatusTab,
  dataRequestSummary: resolveDataRequestSummary,
  dataRequestTitle: resolveDataRequestTitle,
  createDocumentProgressSummary,
  deadlinePassed,
  deadlinePolicyMissing,
  documentLoadStateLabel: resolveDocumentLoadStateLabel,
  documentPolicyLabel: resolveDocumentPolicyLabel,
  documentProgressLabel: resolveDocumentProgressLabel,
  documentStateLabel: resolveDocumentStateLabel,
  freezeDocumentReadMutationEnvelope,
  freezeMutationEnvelope,
  isCompanyFirstSequence: resolveCompanyFirstSequence,
  isProgressStepActive: resolveProgressStepActive,
  isSignatureFirstEmployeeStage: resolveSignatureFirstEmployeeStage,
  isSignatureFirstWaitingCompany: resolveSignatureFirstWaitingCompany,
  isTerminalPackageStatus,
  matchesDocumentReadMutation,
  normalizeNumericBusinessId,
  packageDocuments: resolvePackageDocuments,
  readButtonLabel: resolveReadButtonLabel,
  refusalReasonLabel: resolveRefusalReasonLabel,
  refusalDocumentVersion: resolveRefusalDocumentVersion,
  requestErrorMessage,
  resolutionStatusLabel: resolveResolutionStatusLabel,
  shouldUseFinalDocument: resolveShouldUseFinalDocument,
  statusTabValue: resolveStatusTabValue,
  summarizeDeadline,
  validateRefusal
} = require("./mobileSignPackagePolicy")
const MobileOnboardDataRequest = () => import("../onboardData/index.vue")

export default {
  name: "MobileSignPackage",
  components: { MobileOnboardDataRequest },
  mixins: [createTodoPersonalFocusMixin({
    featureKey: "signPackage",
    idKey: "packageId",
    loadFocusedRow(packageId) { return getMySignPackage(packageId) },
    isActionable(signPackage) {
      return signPackage && EMPLOYEE_ACTION_STATUSES.includes(signPackage.status) &&
        !deadlinePassed(signPackage)
    },
    openFocusedRow(signPackage) { return this.applyPackageDetail(signPackage) }
  })],
  data() {
    return {
      loading: false,
      signing: false,
      finalConfirming: false,
      refusing: false,
      listError: "",
      signCapabilities: null,
      signCapabilitiesResolved: false,
      signCapabilityLoading: false,
      signCapabilityPromise: null,
      excelExtensionError: "",
      detailLoading: false,
      detailError: "",
      detailRetryItem: null,
      list: [],
      dataRequestList: [],
      dataRequestListError: "",
      dataRequestListLoading: false,
      activeStatus: "todo",
      selectedPackage: null,
      selectedDataRequest: null,
      selectedPackageDataRequest: null,
      activeDocument: null,
      documentObjectUrl: "",
      signedDocumentObjectUrl: "",
      finalDocumentObjectUrl: "",
      certificateObjectUrl: "",
      finalExportingDocumentId: "",
      primaryPreviewPageUrl: "",
      previewPageCount: 0,
      previewPageNumber: 0,
      previewPageLoading: false,
      previewZoom: 1,
      primaryViewerOpen: false,
      fileLoading: false,
      fileError: "",
      fileWarnings: [],
      fileRequestSequence: 0,
      primaryFrameLoadContext: null,
      loadedReviewDocumentKeys: {},
      loadedFinalDocumentKeys: {},
      loadedPreviewPageKeys: {},
      signPanelOpen: false,
      signConfirmText: "",
      signRequestId: "",
      signError: "",
      signFrozenMutation: null,
      finalConfirmRequestId: "",
      finalConfirmChecked: false,
      finalConfirmError: "",
      finalConfirmFrozenMutation: null,
      finalReadConfirmingKeys: {},
      finalReadActionError: "",
      refuseOpen: false,
      refuseRequestId: "",
      refuseError: "",
      refuseForm: { reasonCode: "", reasonDetail: "" },
      refuseFrozenMutation: null,
      readConfirmingDocumentId: "",
      readRetryDocumentId: "",
      readActionError: "",
      readFrozenMutation: null,
      drawing: false,
      hasSignature: false,
      lastPoint: null,
      deadlineNow: Date.now(),
      deadlineTimer: null
    }
  },
  computed: {
    pageLevelError() {
      const candidates = []
      if (this.listError) {
        candidates.push({
          source: "list",
          title: this.selectedPackage ? "签约包刷新失败" : "暂时无法加载数据",
          description: this.listError,
          actionLabel: this.selectedPackage ? "重新刷新" : "重新加载",
          actionId: "reload-list"
        })
      }
      if (!this.selectedPackage && !this.selectedDataRequest && this.detailError && !this.listError) {
        candidates.push({
          source: "detail",
          title: "签约详情加载失败",
          description: this.detailError,
          actionLabel: "重新加载详情",
          actionId: "reload-detail"
        })
      }
      if (!this.selectedPackage && !this.selectedDataRequest && this.excelExtensionError && !this.listError) {
        candidates.push({
          source: "excel-capability",
          title: "功能暂不可用",
          description: this.excelExtensionError,
          actionLabel: "重新检查",
          actionId: "reload-capabilities"
        })
      }
      if (!candidates.length) return null
      const text = candidates.map(item => item.description).join(" ")
      if (/登录状态已过期|登录已过期|会话失效|请重新登录|未登录/i.test(text)) {
        return {
          title: "登录状态已过期",
          description: text,
          actionLabel: "重新登录",
          actionId: "relogin"
        }
      }
      if (/权限|无权|未授权|Forbidden/i.test(text)) {
        return {
          title: "你没有查看此内容的权限",
          description: text,
          actionLabel: "返回有效入口",
          actionId: "go-home"
        }
      }
      // Merge same-origin auth/network failures into one recovery card.
      return candidates[0]
    },
    headerSubtitle() {
      if (this.pageLevelError) return this.pageLevelError.title
      if (this.selectedDataRequest) return this.dataRequestStatusLabel(this.selectedDataRequest)
      if (this.selectedPackage) return this.statusLabel(this.selectedPackage.status)
      if (this.listError) return "签约包刷新失败"
      if (this.loading) return "签约包加载中"
      const visiblePackageCount = this.list.filter(item => this.statusTabValue(item.status)).length
      return visiblePackageCount + this.legacyDataRequests.length + " 个签约包"
    },
    excelImportFrontendEnabled() {
      return String(process.env.VUE_APP_SIGN_EXCEL_IMPORT_ENABLED || "true").trim().toLowerCase() === "true"
    },
    excelImportEnabled() {
      return this.excelImportFrontendEnabled &&
        !!(this.signCapabilities && this.signCapabilities.excelImportEnabled === true)
    },
    statusTabs() {
      return createStatusTabs()
    },
    progressSteps() {
      return createProgressSteps()
    },
    sortedPackages() {
      const priority = {
        pending_final_confirm: 0,
        pending_sign: 1,
        part_viewed: 2,
        pending_company: 3,
        refused: 4,
        expired: 5,
        signed: 6,
        voided: 7,
        failed: 8
      }
      return this.list.slice().sort((a, b) => {
        const left = priority[a.status] == null ? 9 : priority[a.status]
        const right = priority[b.status] == null ? 9 : priority[b.status]
        if (left !== right) return left - right
        return String(b.sentTime || b.createTime || "").localeCompare(String(a.sentTime || a.createTime || ""))
      })
    },
    filteredPackages() {
      const tab = this.statusTabs.find(item => item.value === this.activeStatus)
      if (!tab) return this.sortedPackages
      return this.sortedPackages.filter(item => tab.statuses.includes(item.status))
    },
    sortedDataRequests() {
      return this.legacyDataRequests.slice().sort((left, right) => {
        const priority = { REJECTED: 0, PENDING_EMPLOYEE: 1, SUBMITTED: 2, PROFILE_SYNC_FAILED: 3, APPROVED: 4, COMPLETED: 5 }
        const leftStatus = this.dataRequestRawStatus(left)
        const rightStatus = this.dataRequestRawStatus(right)
        const leftPriority = priority[leftStatus] == null ? 9 : priority[leftStatus]
        const rightPriority = priority[rightStatus] == null ? 9 : priority[rightStatus]
        if (leftPriority !== rightPriority) return leftPriority - rightPriority
        return String(right.updateTime || right.submittedTime || "").localeCompare(
          String(left.updateTime || left.submittedTime || "")
        )
      })
    },
    filteredDataRequests() {
      return this.sortedDataRequests.filter(item => this.dataRequestStatusTab(item) === this.activeStatus)
    },
    legacyDataRequests() {
      return this.dataRequestList.filter(item => !this.normalizePackageId(item && item.packageId))
    },
    packageDataRequestsById() {
      return this.dataRequestList.reduce((result, item) => {
        const packageId = this.normalizePackageId(item && item.packageId)
        if (!packageId) return result
        const existing = result[packageId]
        if (!existing || String(item.updateTime || item.submittedTime || "") >=
          String(existing.updateTime || existing.submittedTime || "")) {
          result[packageId] = item
        }
        return result
      }, {})
    },
    showEmbeddedOnboardDataStage() {
      return this.isSignatureFirstEmployeeStage && !!this.selectedPackageDataRequest
    },
    isSignatureFirstEmployeeStage() {
      return resolveSignatureFirstEmployeeStage(this.selectedPackage)
    },
    documents() {
      return resolvePackageDocuments(this.selectedPackage)
    },
    isCompanyFirstSequence() {
      return resolveCompanyFirstSequence(this.selectedPackage)
    },
    documentFileUrl() {
      return this.documentObjectUrl
    },
    certificateFileUrl() {
      return this.certificateObjectUrl
    },
    signedDocumentFileUrl() {
      return this.signedDocumentObjectUrl
    },
    finalDocumentFileUrl() {
      return this.finalDocumentObjectUrl
    },
    primaryDocumentFileUrl() {
      if (this.shouldUseFinalDocument(this.activeDocument)) {
        return this.finalDocumentObjectUrl
      }
      return this.documentObjectUrl
    },
    primaryDocumentFrameKey() {
      return this.primaryFrameLoadContext
        ? this.primaryFrameLoadContext.token
        : "no-primary-sign-document"
    },
    currentDocumentPrimaryLoaded() {
      if (!this.selectedPackage || !this.activeDocument) return false
      const isFinal = this.shouldUseFinalDocument(this.activeDocument)
      return isDocumentLoaded(
        isFinal ? this.loadedFinalDocumentKeys : this.loadedReviewDocumentKeys,
        this.selectedPackage,
        this.activeDocument,
        isFinal ? "final" : "review"
      )
    },
    currentPreviewLoadedPageCount() {
      return this.previewPageViewedCount(
        this.selectedPackage,
        this.activeDocument,
        this.shouldUseFinalDocument(this.activeDocument) ? "final" : "review",
        this.previewPageCount
      )
    },
    viewerImageStyle() {
      return { width: `${Math.round(this.previewZoom * 100)}%` }
    },
    documentProgressSummary() {
      return createDocumentProgressSummary(this.selectedPackage)
    },
    requiredDocuments() {
      return this.documentProgressSummary.requiredDocuments
    },
    requiredReadDocuments() {
      return this.documentProgressSummary.requiredReadDocuments
    },
    requiredDocumentCount() {
      return this.documentProgressSummary.requiredDocumentCount
    },
    requiredReadCount() {
      return this.documentProgressSummary.requiredReadCount
    },
    finalArchiveAvailableCount() {
      return this.documentProgressSummary.finalArchiveAvailableCount
    },
    signedFileAvailableCount() {
      return this.documentProgressSummary.signedFileAvailableCount
    },
    signedArchiveOrFileAvailableCount() {
      return this.documentProgressSummary.signedArchiveOrFileAvailableCount
    },
    fileListProgressCount() {
      return this.documentProgressSummary.fileListProgressCount
    },
    fileListProgressTotal() {
      return this.documentProgressSummary.fileListProgressTotal
    },
    firstSignatureTime() {
      const signPackage = this.selectedPackage || {}
      return signPackage.initialSignedTime || signPackage.signatureSampleTime || signPackage.signedTime || null
    },
    allSigningFilesLoaded() {
      return allInitialDocumentsLoaded(this.loadedReviewDocumentKeys, this.selectedPackage)
    },
    loadedFinalDocumentCount() {
      return this.documents.filter(document =>
        isDocumentLoaded(this.loadedFinalDocumentKeys, this.selectedPackage, document, "final")
      ).length
    },
    allFinalFilesLoaded() {
      return allFinalDocumentsLoaded(this.loadedFinalDocumentKeys, this.selectedPackage)
    },
    finalReadConfirmedCount() {
      return this.documentProgressSummary.finalReadConfirmedCount
    },
    allFinalDocumentsConfirmed() {
      return this.documentProgressSummary.allFinalDocumentsConfirmed
    },
    showPendingFinalWatermark() {
      return !!this.selectedPackage && this.shouldUseFinalDocument(this.activeDocument) &&
        (this.selectedPackage.status === "pending_final_confirm" ||
          (this.isCompanyFirstSequence && ["pending_sign", "part_viewed"].includes(this.selectedPackage.status)))
    },
    refusalReasonOptions() {
      return REFUSAL_REASON_OPTIONS
    },
    mutationReplayLocked() {
      return !!(this.nonReadMutationReplayLocked || this.readFrozenMutation)
    },
    nonReadMutationReplayLocked() {
      return !!(this.signFrozenMutation || this.finalConfirmFrozenMutation || this.refuseFrozenMutation)
    },
    deadlineMissing() {
      return deadlinePolicyMissing(this.selectedPackage)
    },
    deadlineExpired() {
      return !!this.selectedPackage &&
        EMPLOYEE_ACTION_STATUSES.includes(this.selectedPackage.status) &&
        deadlinePassed(this.selectedPackage, this.deadlineNow)
    },
    canRefuse() {
      return !!this.selectedPackage &&
        EMPLOYEE_ACTION_STATUSES.includes(this.selectedPackage.status) &&
        !this.isSignatureFirstEmployeeStage &&
        !this.signFrozenMutation && !this.finalConfirmFrozenMutation &&
        !this.deadlineMissing && !this.deadlineExpired
    },
    finalConfirmDisabledReason() {
      if (!this.selectedPackage || this.selectedPackage.status !== "pending_final_confirm") return ""
      if (this.finalConfirmFrozenMutation) return ""
      if (this.deadlineMissing) return "签署期限策略不完整，请联系经办人处理"
      if (this.deadlineExpired) return "已超过签署截止时间，不能确认文件"
      if (!hasCompleteFinalMetadata(this.selectedPackage)) return "待确认文件信息不完整，请联系经办人处理"
      if (!this.allFinalDocumentsConfirmed) return "请逐份打开待确认文件，等待系统记录成功"
      if (!this.finalConfirmChecked) return "请勾选文件确认内容"
      return ""
    },
    showSignBar() {
      return this.selectedPackage &&
        !this.isSignatureFirstEmployeeStage &&
        this.requiredDocuments.length > 0 &&
        (this.selectedPackage.status === "pending_sign" || this.selectedPackage.status === "part_viewed")
    },
    signDisabledReason() {
      if (!this.showSignBar) return ""
      if (this.signFrozenMutation) return ""
      if (this.deadlineMissing) return "签署期限策略不完整，请联系经办人处理"
      if (this.deadlineExpired) return "已超过签署截止时间，不能继续签署"
      if (this.isCompanyFirstSequence) {
        if (!hasCompleteFinalMetadata(this.selectedPackage) ||
          this.documents.some(document => !document.finalPdfHash)) {
          return "完整待签文件版本信息不完整，请刷新后重试"
        }
        if (!this.allFinalFilesLoaded) return "请逐份打开并成功加载全部完整待签文件"
        if (!this.allFinalDocumentsConfirmed) return "请逐份打开完整待签文件，等待系统记录成功"
      }
      if (!this.selectedPackage.documentVersion ||
        (!this.isCompanyFirstSequence && this.requiredReadDocuments.some(document => !document.reviewPdfHash))) {
        return "签约文件版本信息不完整，请刷新后重试"
      }
      if (!this.isCompanyFirstSequence && !this.allSigningFilesLoaded) return "请逐份打开并成功加载全部需确认文件"
      if (!this.isCompanyFirstSequence && this.requiredReadCount < this.requiredDocumentCount) return "全部需确认文件阅读后才能签署"
      if (this.signConfirmText !== "本人确认签署本签约包") return "请输入本人确认签署本签约包"
      if (!this.hasSignature) return "请完成手写签名"
      return ""
    }
  },
  watch: {
    '$route.query.requestId'(value) {
      const requestId = this.normalizeDataRequestId(value)
      if (!requestId) return
      if (this.selectedDataRequest && String(this.selectedDataRequest.requestId) === requestId) {
        this.consumeDataRequestDeepLink()
        return
      }
      this.openDataRequestDeepLink(requestId)
    },
    '$route.query.packageId'(value) {
      const packageId = this.normalizePackageId(value)
      if (!packageId) return
      if (this.selectedPackage && String(this.selectedPackage.packageId) === packageId) {
        this.consumePackageDeepLink()
        return
      }
      this.openPackageDeepLink(packageId)
    }
  },
  created() {
    startMobileViewportSync()
    return this.ensureSignCapabilities().then(() => this.initializePackageEntry()).catch(error => {
      this.listError = requestErrorMessage(error, "签约包加载失败，请稍后重试")
      return null
    })
  },
  mounted() {
    window.addEventListener("resize", this.resizeSignatureCanvas)
    this.deadlineTimer = window.setInterval(() => {
      this.deadlineNow = Date.now()
    }, 30000)
  },
  beforeDestroy() {
    stopMobileViewportSync()
    window.removeEventListener("resize", this.resizeSignatureCanvas)
    if (this.deadlineTimer) window.clearInterval(this.deadlineTimer)
    this.fileRequestSequence += 1
    this.revokeObjectUrls()
  },
  methods: {
    handlePageLevelErrorAction() {
      const actionId = this.pageLevelError && this.pageLevelError.actionId
      if (actionId === "relogin") {
        this.$store.dispatch("LogOut").then(() => {
          this.$router.push(`/login?redirect=${encodeURIComponent(this.$route.fullPath)}`).catch(() => {})
        }).catch(() => {
          this.$router.push("/login").catch(() => {})
        })
        return
      }
      if (actionId === "go-home") {
        this.goBack()
        return
      }
      if (actionId === "reload-detail") {
        this.retryOpenPackage()
        return
      }
      if (actionId === "reload-capabilities") {
        this.signCapabilitiesResolved = false
        this.signCapabilityPromise = null
        this.excelExtensionError = ""
        return this.ensureSignCapabilities().then(() => this.initializePackageEntry())
      }
      this.loadList()
    },
    ensureSignCapabilities() {
      if (this.signCapabilitiesResolved) return Promise.resolve(this.signCapabilities)
      if (this.signCapabilityPromise) return this.signCapabilityPromise
      this.signCapabilityLoading = true
      this.signCapabilityPromise = Promise.resolve().then(() => getSignTaskCapabilities()).then(response => {
        const data = response && response.data
        this.signCapabilities = data && typeof data === "object" ? data : null
        return this.signCapabilities
      }).catch(() => {
        // 能力接口失败时仅关闭 Excel 扩展，核心签约包仍需正常加载。
        this.signCapabilities = null
        return null
      }).finally(() => {
        this.signCapabilityLoading = false
        this.signCapabilitiesResolved = true
        this.signCapabilityPromise = null
      })
      return this.signCapabilityPromise
    },
    initializePackageEntry() {
      const routeQuery = this.$route && this.$route.query ? this.$route.query : {}
      const requestId = this.normalizeDataRequestId(routeQuery.requestId)
      if (requestId) return this.openDataRequestDeepLink(requestId)
      const packageId = this.normalizePackageId(routeQuery.packageId)
      if (!packageId) return this.initializeTodoPersonalFocus()
      return this.openPackageDeepLink(packageId, true)
    },
    normalizeDataRequestId(value) {
      return normalizeNumericBusinessId(value)
    },
    normalizePackageId(value) {
      return normalizeNumericBusinessId(value)
    },
    openPackageDeepLink(packageId, loadListFirst = false) {
      const normalized = this.normalizePackageId(packageId)
      if (!normalized) return Promise.resolve(null)
      const listRequest = loadListFirst ? this.loadList() : Promise.resolve(true)
      return Promise.resolve(listRequest).then(() => this.openPackage({ packageId: normalized })).then(detail => {
        if (!detail) return null
        return this.consumePackageDeepLink().then(() => detail)
      })
    },
    consumePackageDeepLink() {
      const route = this.$route || {}
      const query = Object.assign({}, route.query || {})
      if (!Object.prototype.hasOwnProperty.call(query, "packageId") ||
        !this.$router || typeof this.$router.replace !== "function") {
        return Promise.resolve()
      }
      delete query.packageId
      return Promise.resolve(this.$router.replace({ path: route.path, query })).catch(() => {})
    },
    openDataRequestDeepLink(requestId) {
      const normalized = this.normalizeDataRequestId(requestId)
      if (!normalized) return Promise.resolve(null)
      return this.ensureSignCapabilities().then(() => {
        if (!this.excelImportEnabled) {
          this.selectedPackage = null
          this.selectedPackageDataRequest = null
          this.selectedDataRequest = null
          this.dataRequestList = []
          this.excelExtensionError = "入职资料签约功能未启用或暂不可用，请联系管理员"
          return this.consumeDataRequestDeepLink().then(() => null)
        }
        this.excelExtensionError = ""
        return this.loadList().then(() => {
          const request = this.dataRequestList.find(item => String(item.requestId) === normalized) || { requestId: normalized }
          const packageId = this.normalizePackageId(request.packageId)
          if (packageId) {
            return this.openPackage({ packageId }).then(detail => {
              if (!detail) return null
              return this.consumeDataRequestDeepLink().then(() => detail)
            })
          }
          this.selectedPackage = null
          this.selectedPackageDataRequest = null
          this.selectedDataRequest = request
          this.activeStatus = this.dataRequestStatusTab(request) || "todo"
          return this.consumeDataRequestDeepLink().then(() => this.selectedDataRequest)
        })
      })
    },
    consumeDataRequestDeepLink() {
      const route = this.$route || {}
      const query = Object.assign({}, route.query || {})
      if (!Object.prototype.hasOwnProperty.call(query, "requestId") ||
        !this.$router || typeof this.$router.replace !== "function") {
        return Promise.resolve()
      }
      delete query.requestId
      if (query.todoType === "OA_SIGN_ONBOARD_DATA_REQUEST") delete query.todoType
      return Promise.resolve(this.$router.replace({ path: route.path, query })).catch(() => {})
    },
    loadList() {
      if (this.loading) return Promise.resolve(false)
      if (!this.signCapabilitiesResolved) {
        return this.ensureSignCapabilities().then(() => this.loadList())
      }
      this.loading = true
      this.listError = ""
      this.dataRequestListError = ""
      const packages = Promise.resolve()
        .then(() => listMySignPackages({ pageNum: 1, pageSize: 50 }))
        .then(res => {
          this.list = res && Array.isArray(res.rows) ? res.rows : []
          return true
        }).catch(error => {
          this.list = []
          this.listError = requestErrorMessage(error, "签约包加载失败，请检查网络后重试")
          return false
      })
      const dataRequests = this.excelImportEnabled
        ? this.loadDataRequestList()
        : Promise.resolve().then(() => {
          this.dataRequestList = []
          return []
        })
      return Promise.all([packages, dataRequests]).then(results => {
        if (this.selectedPackage) this.selectedPackageDataRequest = this.packageDataRequest(this.selectedPackage)
        return results[0]
      }).finally(() => {
        this.loading = false
      })
    },
    loadDataRequestList() {
      if (!this.excelImportEnabled) {
        this.dataRequestList = []
        this.dataRequestListLoading = false
        return Promise.resolve([])
      }
      if (typeof listMyOnboardSignDataRequests !== "function") {
        this.dataRequestList = []
        this.dataRequestListLoading = false
        return Promise.resolve([])
      }
      this.dataRequestListLoading = true
      return Promise.resolve().then(() => listMyOnboardSignDataRequests()).then(response => {
        const data = response && response.data !== undefined ? response.data : response
        const rows = Array.isArray(data) ? data : (data && (data.rows || data.items)) || []
        this.dataRequestList = rows.map(item => Object.assign({}, item, {
          requestId: this.normalizeDataRequestId(item && (item.requestId || item.id))
        })).filter(item => !!item.requestId)
        return this.dataRequestList
      }).catch(error => {
        this.dataRequestList = []
        const status = error && error.response && error.response.status
        if (status !== 404) {
          this.dataRequestListError = requestErrorMessage(error, "入职签约包加载失败，请稍后重试")
        }
        return []
      }).finally(() => {
        this.dataRequestListLoading = false
      })
    },
    retryDataRequestList() {
      if (!this.excelImportEnabled || this.dataRequestListLoading) return Promise.resolve([])
      this.dataRequestListError = ""
      return this.loadDataRequestList()
    },
    openDataRequest(item) {
      const requestId = this.normalizeDataRequestId(item && item.requestId)
      if (!requestId || this.detailLoading) return Promise.resolve(null)
      if (!this.excelImportEnabled) {
        this.excelExtensionError = "入职资料签约功能未启用或暂不可用，请联系管理员"
        return Promise.resolve(null)
      }
      const packageId = this.normalizePackageId(item && item.packageId)
      if (packageId) return this.openPackage({ packageId })
      this.selectedPackage = null
      this.selectedPackageDataRequest = null
      this.selectedDataRequest = Object.assign({}, item, { requestId })
      const statusTab = this.dataRequestStatusTab(this.selectedDataRequest)
      if (statusTab) this.activeStatus = statusTab
      if (!this.$router || typeof this.$router.push !== "function") {
        return Promise.resolve(this.selectedDataRequest)
      }
      return Promise.resolve(this.$router.push({
        path: "/mobile/sign-package",
        query: { requestId }
      })).catch(() => {}).then(() => this.selectedDataRequest)
    },
    handleDataRequestLoaded(detail) {
      const requestId = this.normalizeDataRequestId(detail && detail.requestId)
      if (!requestId || !this.selectedDataRequest ||
        String(this.selectedDataRequest.requestId) !== requestId) return
      this.selectedDataRequest = Object.assign({}, this.selectedDataRequest, detail, { requestId })
      const statusTab = this.dataRequestStatusTab(this.selectedDataRequest)
      if (statusTab) this.activeStatus = statusTab
    },
    handleDataRequestUpdated(detail) {
      this.handleDataRequestLoaded(detail)
      return this.loadList()
    },
    handlePackageDataRequestLoaded(detail) {
      const requestId = this.normalizeDataRequestId(detail && detail.requestId)
      if (!requestId || !this.selectedPackageDataRequest ||
        String(this.selectedPackageDataRequest.requestId) !== requestId) return
      this.selectedPackageDataRequest = Object.assign({}, this.selectedPackageDataRequest, detail, { requestId })
    },
    handlePackageDataRequestUpdated(detail) {
      this.handlePackageDataRequestLoaded(detail)
      const packageId = this.normalizePackageId(this.selectedPackage && this.selectedPackage.packageId)
      if (!packageId) return this.loadList()
      return this.loadList().then(() => this.openPackage({ packageId }))
    },
    closeDataRequest() {
      this.selectedDataRequest = null
      return this.loadList()
    },
    refreshCurrentView() {
      if (this.showEmbeddedOnboardDataStage && this.$refs.packageOnboardDataRequest &&
        typeof this.$refs.packageOnboardDataRequest.refresh === "function") {
        return this.$refs.packageOnboardDataRequest.refresh()
      }
      if (this.selectedDataRequest && this.$refs.onboardDataRequest &&
        typeof this.$refs.onboardDataRequest.refresh === "function") {
        return this.$refs.onboardDataRequest.refresh()
      }
      return this.loadList()
    },
    openPackage(item) {
      if (!item || item.packageId == null || this.detailLoading) return Promise.resolve(null)
      this.selectedDataRequest = null
      this.selectedPackageDataRequest = this.packageDataRequest(item)
      this.detailRetryItem = item
      this.detailError = ""
      this.detailLoading = true
      return Promise.resolve().then(() => getMySignPackage(item.packageId)).then(res => {
        if (!res || !res.data || typeof res.data !== "object") {
          throw new Error("签约详情暂时不可用，请重试")
        }
        const detail = this.applyPackageDetail(res.data)
        this.detailRetryItem = null
        return detail
      }).catch(error => {
        this.detailError = requestErrorMessage(error, "签约详情加载失败，请检查网络后重试")
        return null
      }).finally(() => {
        this.detailLoading = false
      })
    },
    retryOpenPackage() {
      if (!this.detailRetryItem) return Promise.resolve(null)
      return this.openPackage(this.detailRetryItem).then(detail => {
        if (!detail) return null
        return this.consumePackageDeepLink().then(() => detail)
      })
    },
    dismissDetailError() {
      this.detailError = ""
      this.detailRetryItem = null
    },
    applyPackageDetail(signPackage) {
      this.selectedPackage = signPackage
      this.selectedPackageDataRequest = this.packageDataRequest(signPackage)
      const statusTab = this.statusTabValue(signPackage && signPackage.status)
      if (statusTab) this.activeStatus = statusTab
      this.loadedReviewDocumentKeys = {}
      this.loadedFinalDocumentKeys = {}
      this.loadedPreviewPageKeys = {}
      this.fileError = ""
      this.fileWarnings = []
      this.detailError = ""
      this.readConfirmingDocumentId = ""
      this.readRetryDocumentId = ""
      this.readActionError = ""
      this.readFrozenMutation = null
      this.signConfirmText = ""
      this.signRequestId = this.createRequestId()
      this.signError = ""
      this.signFrozenMutation = null
      this.finalConfirmRequestId = this.createRequestId()
      this.finalConfirmChecked = false
      this.finalConfirmError = ""
      this.finalConfirmFrozenMutation = null
      this.finalReadConfirmingKeys = {}
      this.finalReadActionError = ""
      this.refusing = false
      this.refuseOpen = false
      this.refuseRequestId = ""
      this.refuseError = ""
      this.refuseForm = { reasonCode: "", reasonDetail: "" }
      this.refuseFrozenMutation = null
      this.signPanelOpen = false
      this.hasSignature = false
      const documents = signPackage && Array.isArray(signPackage.documents) ? signPackage.documents : []
      this.activeDocument = this.isSignatureFirstWaitingCompany(signPackage)
        ? null : (documents[0] || null)
      this.loadActiveDocumentFiles()
      this.$nextTick(() => this.initSignatureCanvas())
      return signPackage
    },
    selectDocument(document) {
      this.activeDocument = document
      if (!this.finalConfirmFrozenMutation) this.finalConfirmChecked = false
      this.loadActiveDocumentFiles()
    },
    requiresReadConfirmation(document) {
      return requiresDocumentRead(document)
    },
    isSignatureFirstWaitingCompany(signPackage = this.selectedPackage) {
      return resolveSignatureFirstWaitingCompany(signPackage)
    },
    documentPolicyLabel(document) {
      return resolveDocumentPolicyLabel(this.selectedPackage, document)
    },
    documentProgressLabel(document) {
      return resolveDocumentProgressLabel(this.selectedPackage, document)
    },
    documentLoadStateLabel(document) {
      const kind = this.shouldUseFinalDocument(document) ? "final" : "review"
      const loaded = kind === "final" && this.selectedPackage &&
        this.selectedPackage.status === "pending_final_confirm"
        ? false
        : this.isLoadedDocument(document, kind)
      return resolveDocumentLoadStateLabel(this.selectedPackage, document, loaded)
    },
    documentStateLabel(document) {
      return resolveDocumentStateLabel(this.selectedPackage, document)
    },
    readButtonLabel(document) {
      return resolveReadButtonLabel({
        document,
        readConfirmingDocumentId: this.readConfirmingDocumentId,
        readActionError: this.readActionError,
        readRetryDocumentId: this.readRetryDocumentId,
        currentDocumentPrimaryLoaded: this.currentDocumentPrimaryLoaded,
        previewPageCount: this.previewPageCount,
        currentPreviewLoadedPageCount: this.currentPreviewLoadedPageCount
      })
    },
    isReadErrorFor(document) {
      return !!document && !!this.readActionError &&
        String(this.readRetryDocumentId) === String(document.documentId)
    },
    isFrozenReadMutationFor(document) {
      return !!(this.selectedPackage && document && matchesDocumentReadMutation(
        this.readFrozenMutation,
        this.selectedPackage.packageId,
        document.documentId
      ))
    },
    clearDocumentReadMutationReplay() {
      this.readFrozenMutation = null
    },
    confirmRead(document) {
      if (this.readConfirmingDocumentId || this.refusing || this.signFrozenMutation ||
        this.finalConfirmFrozenMutation || this.refuseFrozenMutation) return Promise.resolve(null)
      if (!this.selectedPackage || !document) return Promise.resolve(null)
      if (this.readFrozenMutation && !this.isFrozenReadMutationFor(document)) {
        this.$modal.msgWarning("请先重试上一份文件的阅读确认")
        return Promise.resolve(null)
      }
      if (!this.isLoadedDocument(document, "review")) {
        this.$modal.msgWarning("文件尚未成功加载，请重新加载后再确认阅读")
        return Promise.resolve(null)
      }
      const packageId = this.selectedPackage.packageId
      const documentId = document.documentId
      const pendingDocumentId = String(documentId)
      this.readConfirmingDocumentId = pendingDocumentId
      this.readRetryDocumentId = pendingDocumentId
      this.readActionError = ""
      return Promise.resolve().then(() => this.recheckTodoPersonalAction(packageId)).then(current => {
        if (!current) {
          this.clearDocumentReadMutationReplay()
          return null
        }
        const documents = Array.isArray(current.documents) ? current.documents : []
        const currentDocument = documents.find(item => String(item.documentId) === String(documentId))
        if (!currentDocument) {
          this.clearDocumentReadMutationReplay()
          return this.showTodoPersonalHandled().then(() => null)
        }
        this.selectedPackage = current
        this.activeDocument = currentDocument
        if (!this.isLoadedDocument(currentDocument, "review")) {
          this.$modal.msgWarning("文件版本已更新，请重新加载并阅读最新文件")
          this.loadActiveDocumentFiles()
          return null
        }
        if (currentDocument.readConfirmed === "Y") {
          return { data: current }
        }
        if (!this.readFrozenMutation) {
          const expectedVersion = Number(current.version)
          if (!Number.isSafeInteger(expectedVersion) || expectedVersion < 0) {
            this.$modal.msgWarning("签约包版本不完整，请刷新后重试")
            return null
          }
          this.readFrozenMutation = freezeDocumentReadMutationEnvelope(packageId, documentId, {
            requestId: this.createRequestId(),
            expectedVersion,
            documentVersion: current.documentVersion,
            reviewPdfHash: currentDocument.reviewPdfHash
          })
        }
        const mutation = this.readFrozenMutation
        return confirmSignPackageDocumentRead(mutation.packageId, mutation.documentId,
          mutation.payload)
      }).then(res => {
        if (!res) {
          this.readRetryDocumentId = ""
          return null
        }
        this.selectedPackage = res.data
        const currentId = documentId
        this.activeDocument = this.documents.find(item => String(item.documentId) === String(currentId)) || this.documents[0] || null
        this.readActionError = ""
        this.readRetryDocumentId = ""
        this.clearDocumentReadMutationReplay()
        this.loadActiveDocumentFiles()
        this.$nextTick(() => this.initSignatureCanvas())
        this.$modal.msgSuccess("本次文件阅读已记录")
        return res
      }).catch(error => {
        this.readActionError = requestErrorMessage(error, "阅读确认结果暂未确认，请重试")
        this.showRequestError(this.readActionError)
        return null
      }).finally(() => {
        if (this.readConfirmingDocumentId === pendingDocumentId) {
          this.readConfirmingDocumentId = ""
        }
      })
    },
    clearSignMutationReplay() {
      this.signFrozenMutation = null
      this.signRequestId = ""
      this.signError = ""
      this.signConfirmText = ""
      this.signPanelOpen = false
      this.hasSignature = false
      this.drawing = false
      this.lastPoint = null
    },
    clearFinalConfirmMutationReplay() {
      this.finalConfirmFrozenMutation = null
      this.finalConfirmRequestId = ""
      this.finalConfirmError = ""
      this.finalConfirmChecked = false
    },
    clearRefuseMutationReplay() {
      this.refuseFrozenMutation = null
      this.refuseRequestId = ""
      this.refuseError = ""
      this.refuseOpen = false
      this.refuseForm = { reasonCode: "", reasonDetail: "" }
    },
    openRefusal() {
      if (!this.canRefuse || this.refusing || this.signFrozenMutation ||
        this.finalConfirmFrozenMutation || this.readConfirmingDocumentId) return
      this.signPanelOpen = false
      if (!this.refuseRequestId) this.refuseRequestId = this.createRequestId()
      this.refuseOpen = true
    },
    closeRefusal() {
      if (this.refusing) return
      this.refuseOpen = false
      if (!this.refuseError && !this.refuseFrozenMutation) {
        this.refuseRequestId = ""
        this.refuseForm = { reasonCode: "", reasonDetail: "" }
      }
    },
    refusalValidationMessage() {
      return validateRefusal({
        canRefuse: this.canRefuse,
        reasonCode: this.refuseForm.reasonCode,
        reasonDetail: this.refuseForm.reasonDetail,
        documentVersion: this.refusalDocumentVersion(this.selectedPackage)
      })
    },
    requestRefusal() {
      if (this.refusing || this.signFrozenMutation || this.finalConfirmFrozenMutation) return Promise.resolve(null)
      if (!this.refuseFrozenMutation) {
        const validationMessage = this.refusalValidationMessage()
        if (validationMessage) {
          this.$modal.msgWarning(validationMessage)
          return Promise.resolve(null)
        }
      }
      return this.$modal.confirm(
        "拒签后本版本将永久终止，不能继续签名或确认。确定提交拒签吗？"
      ).then(() => this.submitRefusal()).catch(() => null)
    },
    submitRefusal() {
      if (this.refusing || !this.selectedPackage) return Promise.resolve(null)
      if (!this.refuseFrozenMutation && !this.refuseRequestId) {
        this.refuseRequestId = this.createRequestId()
      }
      const packageId = this.refuseFrozenMutation
        ? this.refuseFrozenMutation.packageId
        : this.selectedPackage.packageId
      this.refusing = true
      this.refuseError = ""
      return getMySignPackage(packageId).then(response => {
        const current = response && response.data
        if (!current || typeof current !== "object") throw new Error("签约详情暂时不可用")
        if (TERMINAL_PACKAGE_STATUSES.includes(current.status)) {
          this.applyPackageDetail(current)
          this.$modal.msgSuccess(current.status === "refused" ? "拒签结果已确认" : "签约包已过期")
          this.loadList()
          return current.status === "refused" ? { data: current, confirmedByRefresh: true } : null
        }
        if (!EMPLOYEE_ACTION_STATUSES.includes(current.status) || deadlinePassed(current, Date.now())) {
          this.applyPackageDetail(current)
          this.$modal.msgWarning("签约包状态已变化，请按当前页面提示处理")
          return null
        }
        this.selectedPackage = current
        if (!this.refuseFrozenMutation) {
          this.refuseFrozenMutation = freezeMutationEnvelope(packageId, {
            requestId: this.refuseRequestId,
            documentVersion: this.refusalDocumentVersion(current),
            reasonCode: this.refuseForm.reasonCode,
            reasonDetail: String(this.refuseForm.reasonDetail || "").trim()
          })
        }
        const mutation = this.refuseFrozenMutation
        return refuseMySignPackage(mutation.packageId, mutation.payload)
      }).then(response => {
        if (!response) return null
        const refusedPackage = response.data
        if (!refusedPackage || refusedPackage.status !== "refused") {
          throw new Error("拒签结果未确认，请使用原请求号重试")
        }
        if (!response.confirmedByRefresh) this.applyPackageDetail(refusedPackage)
        this.clearRefuseMutationReplay()
        if (!response.confirmedByRefresh) this.$modal.msgSuccess("拒签已提交，本版本已转为只读")
        this.loadList()
        return response
      }).catch(error => {
        this.refuseError = requestErrorMessage(
          error,
          "拒签结果暂未确认，已保留原请求号和原因，可安全重试"
        )
        this.showRequestError(this.refuseError)
        return null
      }).finally(() => {
        this.refusing = false
      })
    },
    refusalDocumentVersion(signPackage) {
      return resolveRefusalDocumentVersion(signPackage)
    },
    revokeObjectUrls() {
      this.primaryFrameLoadContext = null
      this.releasePrimaryPreviewPage()
      ;[this.documentObjectUrl, this.signedDocumentObjectUrl, this.finalDocumentObjectUrl, this.certificateObjectUrl].forEach(url => {
        if (url) URL.revokeObjectURL(url)
      })
      this.documentObjectUrl = ""
      this.signedDocumentObjectUrl = ""
      this.finalDocumentObjectUrl = ""
      this.certificateObjectUrl = ""
      this.previewPageCount = 0
      this.previewPageNumber = 0
      this.previewPageLoading = false
      this.previewZoom = 1
      this.primaryViewerOpen = false
    },
    releasePrimaryPreviewPage() {
      if (this.primaryPreviewPageUrl) URL.revokeObjectURL(this.primaryPreviewPageUrl)
      this.primaryPreviewPageUrl = ""
    },
    loadActiveDocumentFiles() {
      const requestSequence = ++this.fileRequestSequence
      this.revokeObjectUrls()
      this.fileError = ""
      this.fileWarnings = []
      if (!this.selectedPackage || !this.activeDocument || this.isSignatureFirstWaitingCompany()) {
        this.fileLoading = false
        return Promise.resolve([])
      }
      const signPackage = this.selectedPackage
      const document = this.activeDocument
      const packageId = signPackage.packageId
      const documentId = document.documentId
      const useFinalFile = this.shouldUseFinalDocument(document)
      const primaryKind = useFinalFile ? "final" : "review"
      this.clearDocumentLoaded(signPackage, document, primaryKind)
      if (useFinalFile && (!document.finalPdfHash || !signPackage.finalDocumentVersion ||
        (signPackage.status === "signed" && !document.finalFileAvailable))) {
        this.fileLoading = false
        this.fileError = "当前签约文件信息不完整，暂时无法加载"
        return Promise.resolve([])
      }
      if (!useFinalFile && this.requiresReadConfirmation(document) &&
        (!document.reviewPdfHash || !signPackage.documentVersion)) {
        this.fileLoading = false
        this.fileError = "签约文件版本信息不完整，暂时无法加载"
        return Promise.resolve([])
      }
      this.fileLoading = true
      const requests = [this.loadPrimaryDocumentPreview(
        requestSequence,
        signPackage,
        document,
        primaryKind
      ).catch(() => {
        if (this.isCurrentDocumentRequest(requestSequence, packageId, documentId)) {
          this.clearDocumentLoaded(signPackage, document, primaryKind)
          this.fileError = useFinalFile
            ? "待确认文件加载失败"
            : "签约文件加载失败"
        }
      }).finally(() => {
        if (this.isCurrentDocumentRequest(requestSequence, packageId, documentId)) {
          this.fileLoading = false
        }
      })]
      if (useFinalFile && signPackage.status === "signed") {
        requests.push(this.loadAuxiliaryDocumentFile(
          "最终归档文件",
          downloadMyFinalSignPackageDocument(packageId, documentId),
          "finalDocumentObjectUrl",
          requestSequence,
          packageId,
          documentId
        ))
        requests.push(this.loadAuxiliaryDocumentFile(
          "首次发送文件",
          downloadMySignPackageDocument(packageId, documentId),
          "documentObjectUrl",
          requestSequence,
          packageId,
          documentId
        ))
      }
      if (document.signedFileAvailable) {
        requests.push(this.loadAuxiliaryDocumentFile(
          "首次签名文件",
          downloadMySignedSignPackageDocument(packageId, documentId),
          "signedDocumentObjectUrl",
          requestSequence,
          packageId,
          documentId
        ))
      }
      if (document.certificateAvailable) {
        requests.push(this.loadAuxiliaryDocumentFile(
          "签署证明",
          downloadMySignPackageCertificate(packageId, documentId),
          "certificateObjectUrl",
          requestSequence,
          packageId,
          documentId
        ))
      }
      return Promise.all(requests)
    },
    loadPrimaryDocumentPreview(requestSequence, signPackage, document, kind) {
      const packageId = signPackage.packageId
      const documentId = document.documentId
      return getMySignPackageDocumentPreview(packageId, documentId, kind).then(response => {
        if (!this.isCurrentDocumentRequest(requestSequence, packageId, documentId)) return false
        const data = response && response.data ? response.data : {}
        const pageCount = Number(data.pageCount)
        if (!Number.isInteger(pageCount) || pageCount < 1 || pageCount > 200) {
          throw new TypeError("合同预览页数无效")
        }
        this.previewPageCount = pageCount
        this.previewPageNumber = 1
        return this.loadPrimaryPreviewPage(1, {
          requestSequence,
          signPackage,
          document,
          kind
        })
      })
    },
    loadPrimaryPreviewPage(pageNumber, options) {
      const signPackage = options && options.signPackage ? options.signPackage : this.selectedPackage
      const document = options && options.document ? options.document : this.activeDocument
      const kind = options && options.kind
        ? options.kind
        : (this.shouldUseFinalDocument(document) ? "final" : "review")
      const requestSequence = options && options.requestSequence != null
        ? options.requestSequence
        : this.fileRequestSequence
      if (!signPackage || !document) return Promise.resolve(false)
      const packageId = signPackage.packageId
      const documentId = document.documentId
      const targetPage = Number(pageNumber)
      if (!Number.isInteger(targetPage) || targetPage < 1 || targetPage > this.previewPageCount) {
        return Promise.resolve(false)
      }
      this.previewPageLoading = true
      this.fileError = ""
      return downloadMySignPackageDocumentPreviewPage(packageId, documentId, targetPage, kind).then(async blob => {
        const imageBlob = blob instanceof Blob ? blob : new Blob([blob], { type: "image/png" })
        if (!(await this.validatePreviewImageBlob(imageBlob))) {
          throw new TypeError("合同预览响应不是有效的 PNG 图片")
        }
        if (!this.isCurrentDocumentRequest(requestSequence, packageId, documentId)) return false
        const objectUrl = URL.createObjectURL(imageBlob)
        if (!this.isCurrentDocumentRequest(requestSequence, packageId, documentId)) {
          URL.revokeObjectURL(objectUrl)
          return false
        }
        const context = createDocumentFrameLoadContext(
          requestSequence,
          signPackage,
          document,
          kind,
          objectUrl,
          targetPage
        )
        if (!context) {
          URL.revokeObjectURL(objectUrl)
          throw new TypeError("签约文件加载上下文不完整")
        }
        this.releasePrimaryPreviewPage()
        this.primaryPreviewPageUrl = objectUrl
        this.primaryFrameLoadContext = context
        this.previewPageNumber = targetPage
        return true
      }).catch(error => {
        if (this.isCurrentDocumentRequest(requestSequence, packageId, documentId)) {
          this.fileError = requestErrorMessage(error, `合同第 ${targetPage} 页加载失败`)
        }
        throw error
      }).finally(() => {
        if (this.isCurrentDocumentRequest(requestSequence, packageId, documentId)) {
          this.previewPageLoading = false
        }
      })
    },
    async validatePreviewImageBlob(blob) {
      if (!(blob instanceof Blob) || blob.size < 8) return false
      const contentType = String(blob.type || "").toLowerCase()
      if (contentType && contentType !== "image/png" && contentType !== "application/octet-stream") return false
      const bytes = new Uint8Array(await blob.slice(0, 8).arrayBuffer())
      const pngSignature = [137, 80, 78, 71, 13, 10, 26, 10]
      return pngSignature.every((value, index) => bytes[index] === value)
    },
    showPreviousPreviewPage() {
      if (this.previewPageLoading || this.previewPageNumber <= 1) return Promise.resolve(false)
      return this.loadPrimaryPreviewPage(this.previewPageNumber - 1).catch(() => false)
    },
    showNextPreviewPage() {
      if (this.previewPageLoading || this.previewPageNumber >= this.previewPageCount) return Promise.resolve(false)
      return this.loadPrimaryPreviewPage(this.previewPageNumber + 1).catch(() => false)
    },
    openPrimaryDocumentViewer() {
      if (!this.primaryPreviewPageUrl) return false
      this.previewZoom = 1
      this.primaryViewerOpen = true
      return true
    },
    closePrimaryDocumentViewer() {
      this.primaryViewerOpen = false
      this.previewZoom = 1
    },
    zoomPreviewIn() {
      this.previewZoom = Math.min(3, this.previewZoom + 0.5)
    },
    zoomPreviewOut() {
      this.previewZoom = Math.max(1, this.previewZoom - 0.5)
    },
    retryActiveDocumentFiles() {
      return this.loadActiveDocumentFiles()
    },
    canExportFinalDocument(document) {
      return !!this.selectedPackage && this.selectedPackage.status === "signed" &&
        String(this.selectedPackage.finalConfirmationStatus || "").toUpperCase() === "CONFIRMED" &&
        !!document && document.templateType === "ONBOARD_LABOR_CONTRACT" &&
        document.finalFileAvailable === true
    },
    exportFinalDocument(document) {
      if (!this.selectedPackage || !this.canExportFinalDocument(document)) return Promise.resolve(false)
      const documentId = String(document.documentId)
      if (this.finalExportingDocumentId === documentId) return Promise.resolve(false)
      this.finalExportingDocumentId = documentId
      return downloadMyFinalSignPackageDocumentExport(this.selectedPackage.packageId, document.documentId)
        .then(async blob => {
          const fileBlob = blob instanceof Blob ? blob : new Blob([blob], { type: "application/pdf" })
          if (!(await validatePdfBlob(fileBlob))) throw new Error("INVALID_FINAL_ARCHIVE_PDF")
          await this.$download.saveAs(fileBlob, this.finalExportFileName(document))
          return true
        })
        .catch(() => {
          this.$modal.msgError("最终合同导出失败，请稍后重试")
          return false
        })
        .finally(() => {
          if (this.finalExportingDocumentId === documentId) this.finalExportingDocumentId = ""
        })
    },
    finalExportFileName(document) {
      const safePart = (value, fallback) => {
        const cleaned = String(value == null ? "" : value).trim()
          .replace(/[\\/:*?"<>|\r\n\x00-\x1F]+/g, "_")
        return cleaned && cleaned !== "." && cleaned !== ".." ? cleaned : fallback
      }
      return `${safePart(this.selectedPackage && this.selectedPackage.packageNo, "签约包")}-${safePart(this.selectedPackage && this.selectedPackage.employeeNameSnapshot, "员工")}-${safePart(document && document.documentName, "合同")}-签章展示版.pdf`
    },
    loadAuxiliaryDocumentFile(label, request, field, requestSequence, packageId, documentId) {
      return request.then(blob => {
        return this.applyDocumentObjectUrl(field, blob, requestSequence, packageId, documentId)
      }).catch(() => {
        if (this.isCurrentDocumentRequest(requestSequence, packageId, documentId)) {
          this.fileWarnings = this.fileWarnings.concat(label + "加载失败")
        }
      })
    },
    shouldUseFinalDocument(document) {
      return resolveShouldUseFinalDocument(this.selectedPackage, document)
    },
    isLoadedDocument(document, kind) {
      const loadedKeys = kind === "final"
        ? this.loadedFinalDocumentKeys
        : this.loadedReviewDocumentKeys
      return isDocumentLoaded(loadedKeys, this.selectedPackage, document, kind)
    },
    markDocumentLoaded(signPackage, document, kind) {
      const key = documentLoadKey(signPackage, document, kind)
      if (!key) return false
      const loadedKeys = kind === "final"
        ? this.loadedFinalDocumentKeys
        : this.loadedReviewDocumentKeys
      this.$set(loadedKeys, key, true)
      return true
    },
    markPreviewPageLoaded(signPackage, document, kind, pageNumber, pageCount) {
      const key = previewPageLoadKey(signPackage, document, kind, pageNumber)
      if (!key) return false
      this.$set(this.loadedPreviewPageKeys, key, true)
      // “打开文件”以当前版本任一已成功渲染的页面为准；逐页浏览不是签署前置条件。
      this.markDocumentLoaded(signPackage, document, kind)
      return true
    },
    clearPreviewPageLoaded(signPackage, document, kind, pageNumber) {
      const key = previewPageLoadKey(signPackage, document, kind, pageNumber)
      if (key) this.$delete(this.loadedPreviewPageKeys, key)
    },
    previewPageViewedCount(signPackage, document, kind, pageCount) {
      const total = Number(pageCount)
      if (!Number.isSafeInteger(total) || total < 1) return 0
      let count = 0
      for (let page = 1; page <= total; page += 1) {
        const key = previewPageLoadKey(signPackage, document, kind, page)
        if (key && this.loadedPreviewPageKeys[key]) count += 1
      }
      return count
    },
    clearDocumentLoaded(signPackage, document, kind) {
      const key = documentLoadKey(signPackage, document, kind)
      if (!key) return
      const loadedKeys = kind === "final"
        ? this.loadedFinalDocumentKeys
        : this.loadedReviewDocumentKeys
      this.$delete(loadedKeys, key)
    },
    isCurrentDocumentRequest(requestSequence, packageId, documentId) {
      return requestSequence === this.fileRequestSequence && this.selectedPackage && this.activeDocument &&
        String(this.selectedPackage.packageId) === String(packageId) &&
        String(this.activeDocument.documentId) === String(documentId)
    },
    async applyDocumentObjectUrl(field, blob, requestSequence, packageId, documentId) {
      const fileBlob = blob instanceof Blob ? blob : new Blob([blob], { type: "application/pdf" })
      if (!(await validatePdfBlob(fileBlob))) {
        throw new TypeError("签约文件响应不是有效的 PDF")
      }
      if (!this.isCurrentDocumentRequest(requestSequence, packageId, documentId)) return false
      const objectUrl = URL.createObjectURL(fileBlob)
      if (!this.isCurrentDocumentRequest(requestSequence, packageId, documentId)) {
        URL.revokeObjectURL(objectUrl)
        return false
      }
      if (this[field]) URL.revokeObjectURL(this[field])
      this[field] = objectUrl
      return true
    },
    isCurrentPrimaryFrameEvent(event) {
      const context = this.primaryFrameLoadContext
      const target = event && (event.currentTarget || event.target)
      if (!context || !target || !target.dataset) return false
      if (this.$refs.primaryDocumentFrame && target !== this.$refs.primaryDocumentFrame) return false
      return matchesDocumentFrameLoadContext(context, {
        requestSequence: this.fileRequestSequence,
        signPackage: this.selectedPackage,
        document: this.activeDocument,
        kind: context.kind,
        pageNumber: this.previewPageNumber,
        objectUrl: this.primaryPreviewPageUrl,
        eventToken: target.dataset.loadToken,
        eventObjectUrl: target.dataset.objectUrl
      })
    },
    handlePrimaryDocumentFrameLoad(event) {
      if (!this.isCurrentPrimaryFrameEvent(event)) return false
      const context = this.primaryFrameLoadContext
      this.fileError = ""
      const loaded = this.markPreviewPageLoaded(
        this.selectedPackage,
        this.activeDocument,
        context.kind,
        context.pageNumber,
        this.previewPageCount
      )
      if (loaded && context.kind === "final") {
        this.recordFinalDocumentOpened(this.selectedPackage, this.activeDocument)
      }
      return loaded
    },
    recordFinalDocumentOpened(signPackage, document) {
      const companyFirstPendingSign = signPackage && signPackage.signingSequence === "COMPANY_FIRST" &&
        ["pending_sign", "part_viewed"].includes(signPackage.status)
      if (!signPackage || !document ||
        (signPackage.status !== "pending_final_confirm" && !companyFirstPendingSign) ||
        document.finalReadConfirmed === "Y") return Promise.resolve(null)
      const key = documentLoadKey(signPackage, document, "final")
      if (!key || this.finalReadConfirmingKeys[key]) return Promise.resolve(null)
      const packageId = signPackage.packageId
      const documentId = document.documentId
      const requestId = this.createRequestId()
      this.$set(this.finalReadConfirmingKeys, key, true)
      this.finalReadActionError = ""
      return confirmSignPackageFinalDocumentRead(packageId, documentId, {
        finalDocumentVersion: signPackage.finalDocumentVersion,
        finalPdfHash: document.finalPdfHash,
        requestId
      }).then(response => {
        const detail = response && response.data
        if (!detail || String(detail.packageId) !== String(packageId)) {
          throw new Error("待确认文件打开记录响应无效")
        }
        const activeDocumentId = this.activeDocument && this.activeDocument.documentId
        this.selectedPackage = detail
        const updatedDocuments = Array.isArray(detail.documents) ? detail.documents : []
        this.activeDocument = updatedDocuments.find(item =>
          String(item.documentId) === String(activeDocumentId)
        ) || updatedDocuments[0] || null
        const confirmedDocument = updatedDocuments.find(item =>
          String(item.documentId) === String(documentId)
        )
        if (!confirmedDocument || confirmedDocument.finalReadConfirmed !== "Y") {
          throw new Error("待确认文件打开记录尚未生效")
        }
        this.markDocumentLoaded(this.selectedPackage, confirmedDocument, "final")
        this.finalReadActionError = ""
        return response
      }).catch(error => {
        this.finalReadActionError = requestErrorMessage(error, "待确认文件打开记录失败，请重新打开该文件")
        return null
      }).finally(() => {
        this.$delete(this.finalReadConfirmingKeys, key)
      })
    },
    handlePrimaryDocumentFrameError(event) {
      if (!this.isCurrentPrimaryFrameEvent(event)) return false
      const context = this.primaryFrameLoadContext
      this.clearPreviewPageLoaded(this.selectedPackage, this.activeDocument, context.kind, context.pageNumber)
      this.clearDocumentLoaded(this.selectedPackage, this.activeDocument, context.kind)
      this.fileError = context.kind === "final"
        ? "待确认文件预览加载失败"
        : "签约文件预览加载失败"
      if (this.primaryPreviewPageUrl === context.objectUrl) {
        URL.revokeObjectURL(context.objectUrl)
        this.primaryPreviewPageUrl = ""
      }
      this.primaryFrameLoadContext = null
      return true
    },
    initSignatureCanvas() {
      if (this.signFrozenMutation || this.finalConfirmFrozenMutation || this.refuseFrozenMutation) return
      const canvas = this.$refs.signatureCanvas
      if (!canvas) return
      const rect = canvas.getBoundingClientRect()
      const ratio = window.devicePixelRatio || 1
      canvas.width = Math.max(1, rect.width * ratio)
      canvas.height = Math.max(1, rect.height * ratio)
      const ctx = canvas.getContext("2d")
      ctx.setTransform(ratio, 0, 0, ratio, 0, 0)
      ctx.fillStyle = "#ffffff"
      ctx.fillRect(0, 0, rect.width, rect.height)
      ctx.strokeStyle = "#111827"
      ctx.lineWidth = 3
      ctx.lineCap = "round"
      ctx.lineJoin = "round"
    },
    resizeSignatureCanvas() {
      if (this.showSignBar && !this.hasSignature) {
        this.$nextTick(() => this.initSignatureCanvas())
      }
    },
    startDraw(event) {
      if (this.signing || this.signFrozenMutation || this.finalConfirmFrozenMutation || this.refuseFrozenMutation) return
      const canvas = this.$refs.signatureCanvas
      if (!canvas) return
      this.drawing = true
      this.lastPoint = this.pointerPoint(event, canvas)
      this.drawDot(this.lastPoint)
    },
    draw(event) {
      if (this.signFrozenMutation || this.finalConfirmFrozenMutation || this.refuseFrozenMutation ||
        !this.drawing || !this.lastPoint) return
      const canvas = this.$refs.signatureCanvas
      const ctx = canvas.getContext("2d")
      const point = this.pointerPoint(event, canvas)
      ctx.beginPath()
      ctx.moveTo(this.lastPoint.x, this.lastPoint.y)
      ctx.lineTo(point.x, point.y)
      ctx.stroke()
      this.lastPoint = point
      this.hasSignature = true
    },
    finishDraw() {
      this.drawing = false
      this.lastPoint = null
    },
    drawDot(point) {
      const canvas = this.$refs.signatureCanvas
      const ctx = canvas.getContext("2d")
      ctx.beginPath()
      ctx.arc(point.x, point.y, 1.5, 0, Math.PI * 2)
      ctx.fillStyle = "#111827"
      ctx.fill()
      this.hasSignature = true
    },
    pointerPoint(event, canvas) {
      const source = event.touches && event.touches.length ? event.touches[0] : event
      const rect = canvas.getBoundingClientRect()
      return {
        x: source.clientX - rect.left,
        y: source.clientY - rect.top
      }
    },
    clearSignature() {
      if (this.signing || this.signFrozenMutation || this.finalConfirmFrozenMutation || this.refuseFrozenMutation) return
      this.hasSignature = false
      this.initSignatureCanvas()
    },
    openSignPanel() {
      this.signPanelOpen = true
      this.$nextTick(() => this.initSignatureCanvas())
    },
    submitSign() {
      if (this.signing || this.refusing || this.finalConfirmFrozenMutation ||
        this.refuseFrozenMutation || this.readConfirmingDocumentId) return Promise.resolve(null)
      this.signError = ""
      if (!this.signFrozenMutation) {
        if (this.signDisabledReason) {
          this.$modal.msgWarning(this.signDisabledReason)
          return Promise.resolve(null)
        }
        if (!this.hasSignature) {
          this.$modal.msgWarning("请完成手写签名")
          return Promise.resolve(null)
        }
        if (!this.signRequestId) this.signRequestId = this.createRequestId()
      }
      const packageId = this.signFrozenMutation
        ? this.signFrozenMutation.packageId
        : this.selectedPackage.packageId
      this.signing = true
      return Promise.resolve().then(() => this.recheckTodoPersonalAction(packageId)).then(current => {
        if (!current) {
          this.clearSignMutationReplay()
          this.loadList()
          return null
        }
        if (current.status !== "pending_sign" && current.status !== "part_viewed") {
          this.selectedPackage = current
          this.clearSignMutationReplay()
          return this.showTodoPersonalHandled().then(() => null)
        }
        const activeDocumentId = this.activeDocument && this.activeDocument.documentId
        this.selectedPackage = current
        this.activeDocument = this.documents.find(document =>
          String(document.documentId) === String(activeDocumentId)
        ) || this.documents[0] || null
        if (!this.signFrozenMutation) {
          const refreshedReason = this.signDisabledReason
          if (refreshedReason) {
            this.$modal.msgWarning(refreshedReason)
            if (!this.allSigningFilesLoaded && this.activeDocument) this.loadActiveDocumentFiles()
            return null
          }
          const canvas = this.$refs.signatureCanvas
          if (!canvas || typeof canvas.toDataURL !== "function") {
            throw new Error("签名图像暂时不可用，请重新打开签署面板")
          }
          const companyFirst = current.signingSequence === "COMPANY_FIRST"
          const payload = {
            documentVersion: current.documentVersion,
            signConfirmText: this.signConfirmText,
            signatureDataUrl: canvas.toDataURL("image/png"),
            requestId: this.signRequestId
          }
          if (companyFirst) {
            payload.finalDocumentVersion = current.finalDocumentVersion
            payload.finalDocumentRootHash = current.finalDocumentRootHash
            payload.finalDocumentHashes = this.documents.map(document => ({
              documentId: document.documentId,
              finalPdfHash: document.finalPdfHash
            }))
          } else {
            payload.documentHashes = this.requiredDocuments.map(document => ({
              documentId: document.documentId,
              reviewPdfHash: document.reviewPdfHash
            }))
          }
          this.signFrozenMutation = freezeMutationEnvelope(packageId, payload)
        }
        const mutation = this.signFrozenMutation
        return signMySignPackage(mutation.packageId, mutation.payload)
      }).then(res => {
        if (!res) return
        this.selectedPackage = res.data
        if (this.activeDocument) {
          const currentId = this.activeDocument.documentId
          this.activeDocument = this.documents.find(item => item.documentId === currentId) || this.documents[0] || null
          this.loadActiveDocumentFiles()
        }
        this.clearSignMutationReplay()
        const status = res.data && res.data.status
        const successMessage = status === "signed"
          ? "手写签名并最终确认完成"
          : status === "pending_company"
            ? "首次手写签名已提交，请等待公司与印章补充"
            : status === "pending_final_confirm"
              ? "签名已提交，请打开待确认文件完成确认"
              : "签署已提交"
        this.$modal.msgSuccess(successMessage)
        this.loadList()
        return res
      }).catch(error => {
        this.signError = requestErrorMessage(error, "签署结果暂未确认，已保留签名和确认内容，可安全重试")
        this.showRequestError(this.signError)
        return null
      }).finally(() => {
        this.signing = false
      })
    },
    submitFinalConfirm() {
      if (this.finalConfirming || this.refusing || this.signFrozenMutation ||
        this.refuseFrozenMutation) return Promise.resolve(null)
      this.finalConfirmError = ""
      if (!this.finalConfirmFrozenMutation) {
        if (this.finalConfirmDisabledReason) {
          this.$modal.msgWarning(this.finalConfirmDisabledReason)
          return Promise.resolve(null)
        }
        const signPackage = this.selectedPackage
        if (!signPackage || !signPackage.finalDocumentVersion || !signPackage.finalDocumentRootHash) {
          this.$modal.msgWarning("待确认文件信息不完整，请刷新后重试")
          return Promise.resolve(null)
        }
        if (!this.finalConfirmRequestId) this.finalConfirmRequestId = this.createRequestId()
      }
      const packageId = this.finalConfirmFrozenMutation
        ? this.finalConfirmFrozenMutation.packageId
        : this.selectedPackage.packageId
      this.finalConfirming = true
      return Promise.resolve().then(() => this.recheckTodoPersonalAction(packageId)).then(current => {
        if (!current) {
          this.clearFinalConfirmMutationReplay()
          this.loadList()
          return null
        }
        if (current.status !== "pending_final_confirm") {
          this.selectedPackage = current
          this.clearFinalConfirmMutationReplay()
          return this.showTodoPersonalHandled().then(() => null)
        }
        const activeDocumentId = this.activeDocument && this.activeDocument.documentId
        this.selectedPackage = current
        this.activeDocument = this.documents.find(document =>
          String(document.documentId) === String(activeDocumentId)
        ) || this.documents[0] || null
        if (!this.finalConfirmFrozenMutation) {
          const refreshedReason = this.finalConfirmDisabledReason
          if (refreshedReason) {
            this.finalConfirmChecked = false
            this.$modal.msgWarning(refreshedReason)
            if (this.activeDocument) this.loadActiveDocumentFiles()
            return null
          }
          this.finalConfirmFrozenMutation = freezeMutationEnvelope(packageId, {
            finalDocumentVersion: current.finalDocumentVersion,
            documentRootHash: current.finalDocumentRootHash,
            confirmationText: "本人已阅读并确认最终合同中的公司及印章信息",
            requestId: this.finalConfirmRequestId
          })
        }
        const mutation = this.finalConfirmFrozenMutation
        return confirmMyFinalSignPackage(mutation.packageId, mutation.payload)
      }).then(response => {
        if (!response) return
        this.selectedPackage = response.data
        this.clearFinalConfirmMutationReplay()
        this.$modal.msgSuccess("最终归档确认完成")
        this.loadList()
        if (this.activeDocument) this.loadActiveDocumentFiles()
        return response
      }).catch(error => {
        this.finalConfirmError = requestErrorMessage(error, "文件确认结果暂未确认，已保留当前确认状态，可安全重试")
        this.showRequestError(this.finalConfirmError)
        return null
      }).finally(() => {
        this.finalConfirming = false
      })
    },
    showRequestError(message) {
      if (this.$modal && typeof this.$modal.msgError === "function") {
        this.$modal.msgError(message)
        return
      }
      if (this.$message && typeof this.$message.error === "function") {
        this.$message.error(message)
      }
    },
    createRequestId() {
      const crypto = window.crypto
      if (crypto && typeof crypto.randomUUID === "function") {
        return crypto.randomUUID()
      }
      const bytes = new Uint8Array(16)
      if (crypto && typeof crypto.getRandomValues === "function") {
        crypto.getRandomValues(bytes)
      } else {
        for (let index = 0; index < bytes.length; index += 1) {
          bytes[index] = Math.floor(Math.random() * 256)
        }
      }
      bytes[6] = (bytes[6] & 0x0f) | 0x40
      bytes[8] = (bytes[8] & 0x3f) | 0x80
      const hex = Array.from(bytes, value => value.toString(16).padStart(2, "0")).join("")
      return [hex.slice(0, 8), hex.slice(8, 12), hex.slice(12, 16),
        hex.slice(16, 20), hex.slice(20)].join("-")
    },
    goBack() {
      if (this.signing || this.finalConfirming || this.refusing || this.readConfirmingDocumentId) {
        this.$modal.msgWarning("当前操作正在提交，请稍候")
        return
      }
      if (this.selectedDataRequest) {
        this.closeDataRequest()
        return
      }
      if (this.selectedPackage) {
        this.fileRequestSequence += 1
        this.selectedPackage = null
        this.selectedPackageDataRequest = null
        this.activeDocument = null
        this.detailLoading = false
        this.detailError = ""
        this.detailRetryItem = null
        this.revokeObjectUrls()
        this.fileLoading = false
        this.fileError = ""
        this.fileWarnings = []
        this.loadedReviewDocumentKeys = {}
        this.loadedFinalDocumentKeys = {}
        this.loadedPreviewPageKeys = {}
        this.signConfirmText = ""
        this.signRequestId = ""
        this.signError = ""
        this.signFrozenMutation = null
        this.finalConfirmRequestId = ""
        this.finalConfirmChecked = false
        this.finalConfirmError = ""
        this.finalConfirmFrozenMutation = null
        this.refusing = false
        this.refuseOpen = false
        this.refuseRequestId = ""
        this.refuseError = ""
        this.refuseForm = { reasonCode: "", reasonDetail: "" }
        this.refuseFrozenMutation = null
        this.readConfirmingDocumentId = ""
        this.readRetryDocumentId = ""
        this.readActionError = ""
        this.readFrozenMutation = null
        this.signPanelOpen = false
        this.hasSignature = false
        return
      }
      this.$router.back()
    },
    statusCount(tabValue) {
      const tab = this.statusTabs.find(item => item.value === tabValue)
      if (!tab) return 0
      return this.list.filter(item => tab.statuses.includes(item.status)).length +
        this.legacyDataRequests.filter(item => this.dataRequestStatusTab(item) === tabValue).length
    },
    dataRequestRawStatus(item) {
      return resolveDataRequestRawStatus(item)
    },
    dataRequestStatusTab(item) {
      return resolveDataRequestStatusTab(item)
    },
    dataRequestStatusLabel(item) {
      return resolveDataRequestStatusLabel(item)
    },
    dataRequestChipClass(item) {
      return resolveDataRequestChipClass(item)
    },
    dataRequestTitle(item) {
      return resolveDataRequestTitle(item)
    },
    dataRequestSummary(item) {
      return resolveDataRequestSummary(item)
    },
    dataRequestProgressSummary(item) {
      return resolveDataRequestProgressSummary(item)
    },
    packageDataRequest(item) {
      const packageId = this.normalizePackageId(item && item.packageId)
      if (!packageId) return null
      const mapped = this.packageDataRequestsById && this.packageDataRequestsById[packageId]
      if (mapped) return mapped
      const requestId = this.normalizeDataRequestId(item &&
        (item.dataRequestId || item.onboardDataRequestId || item.onboardRequestId))
      return requestId ? {
        requestId,
        packageId,
        signingSequence: item.signingSequence,
        status: item.dataRequestStatus || "PENDING_EMPLOYEE"
      } : null
    },
    packageDataRequestProgressSummary(item) {
      const request = this.packageDataRequest(item)
      if (!request) return ""
      const status = this.dataRequestRawStatus(request)
      if (["PENDING_EMPLOYEE", "REJECTED"].includes(status)) {
        return "请确认本签约包并完成唯一一次手写签名"
      }
      if (["SUBMITTED", "APPROVED", "PROFILE_SYNC_FAILED"].includes(status)) {
        return "签约包已确认，正在核验员工提交"
      }
      if (status === "COMPLETED") return "唯一一次签名已完成，等待 HR 选择公司与印章"
      return this.dataRequestProgressSummary(request)
    },
    statusTabValue(status) {
      return resolveStatusTabValue(status)
    },
    documentCount(item) {
      if (!item) return 0
      if (item.documentCount != null) return item.documentCount
      if (item.documents) return item.documents.length
      const request = this.packageDataRequest(item)
      const names = request && (request.plannedDocumentNames || request.documentNames)
      return Array.isArray(names) ? names.length : 0
    },
    formatDateTime(value) {
      return formatSignDateTime(value)
    },
    deadlineSummary(signPackage) {
      return summarizeDeadline(signPackage, this.deadlineNow)
    },
    isTerminalStatus(status) {
      return isTerminalPackageStatus(status)
    },
    refusalReasonLabel(reasonCode) {
      return resolveRefusalReasonLabel(reasonCode)
    },
    resolutionStatusLabel(status) {
      return resolveResolutionStatusLabel(status)
    },
    isProgressStepActive(status) {
      const current = this.selectedPackage ? this.selectedPackage.status : ""
      return resolveProgressStepActive(current, status)
    },
    scenarioLabel(scenario) {
      return signScenarioLabel(scenario)
    },
    dictionaryLabel(value) {
      return signDictionaryLabel(value)
    },
    statusLabel(status) {
      return signPackageStatusLabel(status)
    },
    normalizeFileUrl(url) {
      if (!url) return ""
      if (/^https?:\/\//.test(url) || url.indexOf("blob:") === 0) return url
      if (url.indexOf("classpath:") === 0) return ""
      return process.env.VUE_APP_BASE_API + url
    }
  }
}
</script>

<style scoped>
.mobile-sign-package-page {
  height: var(--mobile-viewport-height, 100dvh);
  min-height: var(--mobile-viewport-height, 100dvh);
  overflow-y: auto;
  padding-bottom: calc(112px + env(safe-area-inset-bottom));
  background: var(--mobile-color-page);
  color: #111827;
}

.mobile-sign-package-page.sign-panel-expanded {
  padding-bottom: calc(112px + env(safe-area-inset-bottom));
}

.mobile-sign-header {
  position: sticky;
  top: 0;
  z-index: 5;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 14px 16px;
  background: #ffffff;
  border-bottom: 1px solid #e5e7eb;
}

.mobile-sign-header h1 {
  margin: 0;
  font-size: 18px;
  line-height: 1.3;
}

.mobile-sign-header p {
  margin: 2px 0 0;
  color: #6b7280;
  font-size: 12px;
}

.back-button,
.refresh-button {
  min-height: 44px;
  border: 0;
  background: transparent;
  color: #374151;
  font-size: 14px;
}

.back-button {
  width: 44px;
  height: 44px;
  border-radius: 17px;
  background: #f3f4f6;
  font-size: 24px;
  line-height: 30px;
}

.refresh-button:disabled,
.package-row:disabled,
.ghost-button:disabled {
  cursor: not-allowed;
  opacity: 0.58;
}

.package-list,
.package-detail {
  padding: 12px;
}

.onboard-request-detail {
  padding: 0;
}

.detail-refresh-error {
  margin: 12px;
}

.status-tabs {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 8px;
  margin-bottom: 12px;
}

.status-tabs button {
  min-width: 0;
  padding: 9px 6px;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  background: #ffffff;
  color: #4b5563;
  font-size: 12px;
}

.status-tabs button.active {
  border-color: #4f46e5;
  color: #312e81;
  background: #eef2ff;
}

.status-tabs span,
.status-tabs em {
  display: block;
  font-style: normal;
  line-height: 1.3;
}

.status-tabs em {
  margin-top: 2px;
  font-weight: 700;
}

.package-row,
.document-row {
  width: 100%;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 14px;
  margin-bottom: 10px;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  background: #ffffff;
  text-align: left;
}

.onboard-request-row {
  align-items: flex-start;
}

.onboard-request-row > div {
  min-width: 0;
}

.package-row strong,
.document-row strong {
  display: block;
  color: #111827;
  font-size: 15px;
  line-height: 1.4;
}

.package-row span,
.document-row span {
  display: block;
  margin-top: 4px;
  color: #6b7280;
  font-size: 12px;
}

.document-row.active {
  border-color: #4f46e5;
}

.document-row em,
.status-chip {
  flex: none;
  padding: 4px 8px;
  border-radius: 999px;
  background: #eef2ff;
  color: #4338ca;
  font-size: 12px;
  font-style: normal;
}

.status-chip.signed {
  background: #ecfdf5;
  color: #047857;
}

.status-chip.voided,
.status-chip.failed,
.status-chip.refused,
.status-chip.expired {
  background: #fef2f2;
  color: #b91c1c;
}

.detail-card {
  margin-bottom: 12px;
  padding: 14px;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  background: #ffffff;
}

.staged-package-card {
  padding: 0;
  overflow: hidden;
  border-color: #a5b4fc;
}

.staged-package-heading {
  margin: 0;
  padding: 14px;
  border-bottom: 1px solid #e0e7ff;
  background: #eef2ff;
}

.staged-package-heading p {
  margin: 4px 0 0;
  color: #4b5563;
  font-size: 12px;
  line-height: 1.6;
}

.detail-head,
.section-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 10px;
}

.progress-strip {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 8px;
  margin: 0 0 12px;
}

.progress-strip span {
  min-width: 0;
  padding: 8px 6px;
  border-radius: 8px;
  background: #f3f4f6;
  color: #6b7280;
  font-size: 12px;
  text-align: center;
}

.progress-strip span.active {
  background: #ecfdf5;
  color: #047857;
}

.file-links {
  display: flex;
  gap: 12px;
  flex: none;
}

.file-links button,
.file-links a {
  min-height: 44px;
  display: inline-flex;
  align-items: center;
  padding: 0;
  border: 0;
  background: transparent;
  color: #4f46e5;
  font: inherit;
  text-decoration: none;
}

.detail-head h2,
.section-head h2 {
  margin: 0;
  font-size: 16px;
  line-height: 1.4;
}

.detail-head p {
  margin: 4px 0 0;
  color: #6b7280;
  font-size: 12px;
}

.snapshot-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px;
  margin: 0;
}

.snapshot-grid div {
  min-width: 0;
}

.snapshot-grid dt {
  color: #6b7280;
  font-size: 12px;
}

.snapshot-grid dd {
  margin: 3px 0 0;
  color: #111827;
  font-size: 14px;
  word-break: break-word;
}

.waiting-card {
  border-color: #fde68a;
  background: #fffbeb;
}

.waiting-card h2,
.final-confirm-card h2 {
  margin: 0 0 8px;
  font-size: 17px;
}

.waiting-card p,
.final-confirm-card > p {
  margin: 0 0 10px;
  color: #4b5563;
  line-height: 1.65;
}

.waiting-card strong {
  color: #92400e;
}

.final-confirm-card {
  border-color: #a5b4fc;
}

.final-confirm-check {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  margin-top: 14px;
  padding: 12px;
  border-radius: 8px;
  background: #eef2ff;
  color: #312e81;
  font-size: 14px;
  line-height: 1.55;
}

.final-confirm-check input {
  width: 18px;
  height: 18px;
  margin-top: 2px;
}

.final-confirm-check input:disabled + span {
  color: #6b7280;
}

.final-load-progress,
.confirm-blocked-reason {
  margin: 8px 0 0 !important;
  font-size: 12px;
  text-align: center;
}

.final-load-progress {
  color: #4b5563 !important;
}

.confirm-blocked-reason {
  color: #b45309 !important;
}

.final-confirm-button {
  margin-top: 12px;
}

.confirm-help {
  margin: 8px 0 0 !important;
  color: #6b7280 !important;
  font-size: 12px;
  text-align: center;
}

.document-preview {
  position: relative;
  width: 100%;
  overflow: hidden;
  border: 1px solid #e5e7eb;
  border-radius: 6px;
  background: #f3f4f6;
}

.pending-final-watermark {
  position: absolute;
  top: 38%;
  left: 50%;
  z-index: 2;
  padding: 10px 18px;
  border: 2px solid rgba(185, 28, 28, 0.42);
  color: rgba(185, 28, 28, 0.5);
  font-size: clamp(20px, 7vw, 42px);
  font-weight: 700;
  letter-spacing: 0.14em;
  line-height: 1;
  white-space: nowrap;
  pointer-events: none;
  transform: translate(-50%, -50%) rotate(-28deg);
  user-select: none;
}

.document-preview-image {
  width: 100%;
  min-height: 180px;
  display: block;
  background: #ffffff;
  object-fit: contain;
}

.preview-page-controls,
.pdf-viewer-footer {
  min-height: 52px;
  display: grid;
  grid-template-columns: 88px minmax(0, 1fr) 88px;
  align-items: center;
  gap: 8px;
  padding: 6px 8px;
  border-top: 1px solid #e5e7eb;
  background: #ffffff;
  color: #374151;
  text-align: center;
}

.preview-page-controls button,
.pdf-viewer-footer button {
  min-height: 44px;
  border: 1px solid #c7d2fe;
  border-radius: 8px;
  background: #eef2ff;
  color: #4338ca;
  font: inherit;
}

.preview-page-controls button:disabled,
.pdf-viewer-footer button:disabled {
  color: #9ca3af;
  background: #f3f4f6;
  border-color: #e5e7eb;
}

.preview-fallback {
  display: flex;
  justify-content: space-between;
  gap: 10px;
  margin-top: 8px;
  color: #6b7280;
  font-size: 12px;
}

.preview-fallback button {
  min-height: 44px;
  padding: 0;
  border: 0;
  background: transparent;
  color: #4f46e5;
  font: inherit;
}

.file-error {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
  color: #991b1b;
  background: #fef2f2;
  border-radius: 8px;
}

.file-error span {
  color: #7f1d1d;
  font-size: 12px;
  line-height: 1.5;
}

.retry-file-button,
.file-warning button {
  min-height: 44px;
  padding: 0 16px;
  border: 1px solid #dc2626;
  border-radius: 8px;
  background: #ffffff;
  color: #b91c1c;
}

.file-warning {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  margin-top: 8px;
  padding: 10px;
  border-radius: 8px;
  background: #fffbeb;
  color: #92400e;
  font-size: 12px;
}

.file-warning button {
  flex: none;
  min-height: 44px;
  border-color: #d97706;
  color: #92400e;
}

.read-button,
.primary-button {
  width: 100%;
  min-height: 44px;
  border: 0;
  border-radius: 8px;
  background: #4f46e5;
  color: #ffffff;
  font-size: 15px;
}

.read-button {
  margin-top: 10px;
}

.read-not-required {
  margin: 10px 0 0;
  color: #6b7280;
  font-size: 13px;
  text-align: center;
}

.action-error {
  margin: 8px 0 0 !important;
  color: #b91c1c !important;
  font-size: 12px;
  line-height: 1.55;
  text-align: center;
}

.read-button:disabled,
.primary-button:disabled {
  background: #c7d2fe;
}

.terminal-card {
  border-color: #fecaca;
  background: #fff7f7;
}

.terminal-card.expired {
  border-color: #fed7aa;
  background: #fffaf3;
}

.terminal-card h2,
.refusal-entry-card h2 {
  margin: 0 0 8px;
  font-size: 16px;
}

.terminal-card p,
.refusal-entry-card p {
  margin: 0 0 10px;
  color: #4b5563;
  font-size: 13px;
  line-height: 1.65;
}

.terminal-card > strong {
  color: #9a3412;
  font-size: 13px;
}

.terminal-reason {
  margin-top: 12px !important;
  padding: 10px;
  border-radius: 8px;
  background: #ffffff;
  overflow-wrap: anywhere;
}

.refusal-entry-card {
  border-color: #fecaca;
}

.danger-outline-button,
.danger-button {
  min-height: 44px;
  padding: 0 16px;
  border-radius: 8px;
  font-size: 14px;
}

.danger-outline-button {
  width: 100%;
  border: 1px solid #dc2626;
  background: #ffffff;
  color: #b91c1c;
}

.danger-button {
  border: 0;
  background: #b91c1c;
  color: #ffffff;
}

.danger-outline-button:disabled,
.danger-button:disabled {
  cursor: not-allowed;
  opacity: 0.58;
}

.pdf-viewer-overlay {
  position: fixed;
  inset: 0;
  z-index: 45;
  display: grid;
  grid-template-rows: auto minmax(0, 1fr) auto;
  background: #111827;
}

.pdf-viewer-header {
  min-height: 56px;
  display: grid;
  grid-template-columns: 72px minmax(0, 1fr) 54px;
  align-items: center;
  gap: 8px;
  padding: calc(6px + env(safe-area-inset-top)) 10px 6px;
  background: #ffffff;
  border-bottom: 1px solid #e5e7eb;
}

.pdf-viewer-header button {
  min-height: 44px;
  border: 0;
  background: transparent;
  color: #4f46e5;
  font: inherit;
  text-align: left;
}

.pdf-viewer-header strong {
  min-width: 0;
  overflow: hidden;
  color: #111827;
  text-align: center;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.pdf-viewer-header span {
  color: #6b7280;
  text-align: right;
}

.pdf-viewer-body {
  min-height: 0;
  overflow: auto;
  -webkit-overflow-scrolling: touch;
  padding: 12px;
  background: #374151;
}

.pdf-viewer-page {
  position: relative;
  min-width: 100%;
  margin: 0 auto;
}

.pdf-viewer-image {
  width: 100%;
  max-width: none;
  height: auto;
  display: block;
  margin: 0 auto;
  background: #ffffff;
  box-shadow: 0 8px 28px rgba(0, 0, 0, 0.28);
}

.viewer-watermark {
  font-size: clamp(24px, 8vw, 54px);
}

.pdf-viewer-footer {
  grid-template-columns: 58px 40px minmax(0, 1fr) 40px 58px;
  gap: 4px;
  padding-bottom: calc(6px + env(safe-area-inset-bottom));
  border-top: 0;
}

.mobile-dialog-backdrop {
  position: fixed;
  inset: 0;
  z-index: 30;
  display: flex;
  align-items: flex-end;
  padding: 16px 12px calc(16px + env(safe-area-inset-bottom));
  background: rgba(15, 23, 42, 0.55);
}

.mobile-refusal-dialog {
  width: min(100%, 520px);
  max-height: calc(var(--mobile-viewport-height, 100dvh) - 48px);
  margin: 0 auto;
  overflow-y: auto;
  padding: 18px;
  border-radius: 16px;
  background: #ffffff;
  box-shadow: 0 18px 48px rgba(15, 23, 42, 0.24);
}

.mobile-dialog-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 16px;
}

.mobile-dialog-head h2 {
  margin: 0;
  font-size: 18px;
}

.mobile-dialog-head p {
  margin: 5px 0 0;
  color: #6b7280;
  font-size: 12px;
  line-height: 1.5;
}

.mobile-dialog-head > button {
  width: 44px;
  min-width: 44px;
  height: 44px;
  border: 0;
  border-radius: 50%;
  background: #f3f4f6;
  color: #4b5563;
  font-size: 24px;
}

.mobile-field {
  display: block;
  margin-bottom: 14px;
}

.mobile-field > span {
  display: block;
  margin-bottom: 6px;
  color: #374151;
  font-size: 13px;
  font-weight: 600;
}

.mobile-field select,
.mobile-field textarea {
  width: 100%;
  box-sizing: border-box;
  border: 1px solid #d1d5db;
  border-radius: 8px;
  background: #ffffff;
  color: #111827;
  font: inherit;
}

.mobile-field select {
  min-height: 44px;
  padding: 0 10px;
}

.mobile-field textarea {
  padding: 10px;
  resize: vertical;
  line-height: 1.55;
}

.mobile-field small {
  display: block;
  margin-top: 4px;
  color: #9ca3af;
  font-size: 11px;
  text-align: right;
}

.mobile-dialog-actions {
  display: grid;
  grid-template-columns: 1fr 1.4fr;
  gap: 10px;
  margin-top: 16px;
}

.mobile-dialog-actions .ghost-button {
  margin: 0;
}

.sticky-sign-bar {
  position: fixed;
  left: 0;
  right: 0;
  bottom: 0;
  z-index: 10;
  padding: 10px 12px calc(10px + env(safe-area-inset-bottom));
  background: #ffffff;
  border-top: 1px solid #e5e7eb;
  box-shadow: 0 -8px 20px rgba(15, 23, 42, 0.08);
}

.sticky-sign-bar.expanded {
  max-height: calc(var(--mobile-viewport-height) - var(--mobile-safe-top) - 16px);
  display: flex;
  flex-direction: column;
  bottom: var(--mobile-keyboard-inset);
  padding: 0;
}

.sign-panel-summary {
  width: 100%;
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: 4px 10px;
  align-items: center;
  border: 0;
  background: transparent;
  color: #111827;
  text-align: left;
}

.sign-panel-summary span {
  font-size: 15px;
  font-weight: 700;
}

.sign-panel-summary strong {
  font-size: 13px;
}

.sign-panel-summary em {
  grid-column: 1 / -1;
  color: #6b7280;
  font-size: 12px;
  font-style: normal;
}

.sign-panel-head {
  flex: 0 0 auto;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 12px;
  border-bottom: 1px solid #e5e7eb;
  background: var(--mobile-color-surface);
}

.sign-panel-head strong {
  font-size: 15px;
}

.sign-panel-head button {
  border: 0;
  background: transparent;
  color: #4f46e5;
  font-size: 13px;
}

.sign-panel-body {
  min-height: 0;
  overflow-y: auto;
  -webkit-overflow-scrolling: touch;
  padding: 10px 12px;
}

.sign-panel-footer {
  flex: 0 0 auto;
  padding: 8px 12px calc(8px + env(safe-area-inset-bottom));
  border-top: 1px solid #e5e7eb;
  background: var(--mobile-color-surface);
}

.signature-box {
  height: clamp(168px, 24dvh, 240px);
  margin-bottom: 8px;
  border: 1px dashed #c7d2fe;
  border-radius: 8px;
  background: #ffffff;
}

@media (max-height: 600px) {
  .signature-box {
    height: 132px;
  }
}

.signature-box canvas {
  width: 100%;
  height: 100%;
  display: block;
  touch-action: none;
}

.ghost-button {
  width: 100%;
  min-height: 44px;
  margin-bottom: 8px;
  border: 1px solid #d1d5db;
  border-radius: 8px;
  background: #ffffff;
  color: #374151;
  font-size: 14px;
}

.confirm-text-row {
  display: grid;
  grid-template-columns: 74px minmax(0, 1fr);
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
  color: #374151;
  font-size: 13px;
}

.confirm-text-row input {
  min-width: 0;
  height: 44px;
  padding: 0 10px;
  border: 1px solid #d1d5db;
  border-radius: 8px;
}

.sticky-sign-bar p {
  margin: 0 0 8px;
  color: #b45309;
  font-size: 12px;
}

.state-panel {
  padding: 40px 12px;
  color: #6b7280;
  text-align: center;
}

.state-panel.request-error {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
  margin: 12px 0;
  padding: 28px 16px;
  border: 1px solid #fecaca;
  border-radius: 8px;
  color: #991b1b;
  background: #fef2f2;
}

.state-panel.request-error span {
  color: #7f1d1d;
  font-size: 13px;
  line-height: 1.55;
}

.state-actions {
  display: flex;
  flex-wrap: wrap;
  justify-content: center;
  gap: 8px;
}

.state-action-button {
  min-height: 44px;
  padding: 0 18px;
  border: 1px solid #dc2626;
  border-radius: 8px;
  color: #ffffff;
  background: #dc2626;
  font: inherit;
}

.state-action-button.secondary {
  color: #991b1b;
  background: #ffffff;
}

.state-panel.compact {
  padding: 24px 12px;
}

.mobile-sign-package-page {
  color: var(--mobile-color-ink);
  background: var(--mobile-color-page);
}

.mobile-sign-header {
  border-bottom-color: var(--mobile-color-line);
  background: var(--mobile-color-surface);
}

.mobile-sign-header p,
.package-row span,
.detail-head p,
.section-head p,
.sign-panel-summary em {
  color: var(--mobile-color-muted);
}

.back-button,
.refresh-button,
.file-links button,
.file-links a,
.preview-fallback button,
.sign-panel-head button {
  color: var(--mobile-color-primary);
}

.status-tabs {
  border-color: var(--mobile-color-line);
  background: var(--mobile-color-primary-soft);
}

.status-tabs button.active {
  border-color: rgba(11, 107, 83, 0.32);
  color: var(--mobile-color-primary);
  background: var(--mobile-color-surface);
}

.package-row,
.document-row,
.detail-card {
  border-color: var(--mobile-color-line);
  border-radius: var(--mobile-radius-lg);
  background: var(--mobile-color-surface);
  box-shadow: none;
}

.status-chip:not(.signed):not(.voided):not(.failed):not(.refused):not(.expired) {
  color: var(--mobile-color-primary);
  background: var(--mobile-color-primary-soft);
}

.document-row.active {
  border-color: var(--mobile-color-primary);
}

.status-tabs button,
.preview-page-controls button,
.pdf-viewer-footer button {
  border-color: var(--mobile-color-line);
  border-radius: var(--mobile-radius-md);
}

.preview-page-controls button:not(:disabled),
.pdf-viewer-footer button:not(:disabled) {
  color: var(--mobile-color-primary);
  background: var(--mobile-color-primary-soft);
}

.progress-strip span.active {
  color: var(--mobile-color-primary);
  background: var(--mobile-color-primary-soft);
}

.read-button,
.primary-button {
  border-radius: var(--mobile-radius-md);
  background: var(--mobile-color-primary);
}

.mobile-field select:focus,
.mobile-field textarea:focus,
.confirm-text-row input:focus {
  border-color: var(--mobile-color-primary);
  outline: 0;
  box-shadow: var(--mobile-focus-ring);
}

.sticky-sign-bar,
.sign-panel-head,
.sign-panel-footer {
  border-color: var(--mobile-color-line);
  background: var(--mobile-color-surface);
}

.mobile-sign-package-page button:focus-visible,
.mobile-sign-package-page a:focus-visible,
.mobile-sign-package-page input:focus-visible,
.mobile-sign-package-page select:focus-visible,
.mobile-sign-package-page textarea:focus-visible {
  outline: 2px solid var(--mobile-color-primary);
  outline-offset: 2px;
}
</style>
