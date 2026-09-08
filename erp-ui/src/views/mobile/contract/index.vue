<template>
  <div class="mobile-contract-page mobile-system-page mobile-system-scroll" data-mobile-scroll-root>
    <header class="mobile-contract-header">
      <button type="button" class="back-button" aria-label="返回" @click="goBack"><i class="el-icon-arrow-left" aria-hidden="true" /></button>
      <div>
        <h1>劳动合同</h1>
        <p>{{ selectedContract ? statusLabel(selectedContract.status) : list.length + ' 份' }}</p>
      </div>
      <button type="button" class="refresh-button" @click="loadList">刷新</button>
    </header>

    <main v-if="!selectedContract" class="contract-list">
      <div v-if="loading" class="state-panel">加载中...</div>
      <div v-else-if="list.length === 0" class="state-panel">暂无合同</div>
      <template v-else>
        <button
          v-for="item in list"
          :key="item.contractId"
          type="button"
          class="contract-row"
          @click="openContract(item)"
        >
          <div>
            <strong>{{ item.contractTitle || item.employeeName + ' 劳动合同' }}</strong>
            <span>{{ item.contractStartDate || '-' }} 至 {{ item.contractEndDate || '-' }}</span>
            <span>{{ item.postName || '-' }} · {{ item.socialType || '-' }}</span>
          </div>
          <em :class="['status-chip', item.status]">{{ statusLabel(item.status) }}</em>
        </button>
      </template>
    </main>

    <main v-else class="contract-detail">
      <section class="detail-card">
        <div class="detail-head">
          <div>
            <h2>{{ selectedContract.contractTitle || selectedContract.employeeName + ' 劳动合同' }}</h2>
            <p>{{ selectedContract.contractStartDate || '-' }} 至 {{ selectedContract.contractEndDate || '-' }}</p>
          </div>
          <em :class="['status-chip', selectedContract.status]">{{ statusLabel(selectedContract.status) }}</em>
        </div>
        <dl class="snapshot-grid">
          <div><dt>姓名</dt><dd>{{ selectedContract.employeeName }}</dd></div>
          <div><dt>岗位</dt><dd>{{ selectedContract.postName }}</dd></div>
          <div><dt>社保</dt><dd>{{ selectedContract.socialType }}</dd></div>
          <div><dt>工资</dt><dd>{{ money(selectedContract.totalSalary) }}</dd></div>
          <div><dt>版本</dt><dd>{{ selectedContract.documentVersion || '-' }}</dd></div>
          <div><dt>预览哈希</dt><dd class="hash-text">{{ selectedContract.previewFileHash || '-' }}</dd></div>
        </dl>
      </section>

      <section class="detail-card">
        <div class="section-head">
          <h2>合同文件</h2>
          <a
            v-if="fileUrl"
            :href="fileUrl"
            :download="canInlinePreview ? null : fileDownloadName"
            target="_blank"
            rel="noopener"
          >{{ canInlinePreview ? '打开' : '下载 Word 合同' }}</a>
        </div>
        <iframe v-if="fileUrl && canInlinePreview" title="合同文件预览" class="contract-frame" :src="fileUrl"></iframe>
        <div v-else-if="fileUrl" class="state-panel compact file-preview-fallback">
          <strong>此合同是 Word 文件</strong>
          <span>手机浏览器无法直接预览，请点击右上角“下载 Word 合同”后查看。</span>
        </div>
        <div v-else-if="fileLoading" class="state-panel compact">文件加载中...</div>
        <div v-else-if="fileError" class="state-panel compact file-error" role="alert">
          <span>{{ fileError }}</span>
          <button type="button" @click="loadContractFile">重试</button>
        </div>
        <div v-else class="state-panel compact">暂无文件</div>
      </section>

      <section v-if="selectedContract.status === 'pending_sign'" class="detail-card sign-card">
        <label class="confirm-row">
          <input v-model="confirmed" type="checkbox">
          <span>本人已阅读并确认签署此版本合同</span>
        </label>
        <label class="confirm-text-row">
          <span>二次确认</span>
          <input
            v-model.trim="signConfirmText"
            type="text"
            autocomplete="off"
            placeholder="输入：本人确认签署"
          >
        </label>
        <div class="signature-box">
          <canvas
            ref="signatureCanvas"
            @mousedown="startDraw"
            @mousemove="draw"
            @mouseup="finishDraw"
            @mouseleave="finishDraw"
            @touchstart.prevent="startDraw"
            @touchmove.prevent="draw"
            @touchend.prevent="finishDraw"
          ></canvas>
        </div>
        <div class="sign-actions">
          <button type="button" class="ghost-button" @click="clearSignature">清空</button>
          <button type="button" class="primary-button" :disabled="signing" @click="submitSign">
            {{ signing ? '提交中...' : '提交签署' }}
          </button>
        </div>
      </section>

      <section v-else-if="selectedContract.status === 'signed'" class="detail-card evidence-card">
        <dl class="snapshot-grid">
          <div><dt>签署时间</dt><dd>{{ selectedContract.signedTime || '-' }}</dd></div>
          <div><dt>签署网络地址</dt><dd>{{ selectedContract.signerIp || '-' }}</dd></div>
          <div><dt>归档哈希</dt><dd class="hash-text">{{ selectedContract.archiveFileHash || selectedContract.contractFileHash || '-' }}</dd></div>
          <div><dt>证明哈希</dt><dd class="hash-text">{{ selectedContract.certificateFileHash || '-' }}</dd></div>
        </dl>
        <button
          v-if="selectedContract.certificateFileUrl"
          type="button"
          class="certificate-link"
          @click="openProtectedFile(selectedContract.certificateFileUrl)"
        >下载完成证明</button>
      </section>
    </main>
  </div>
</template>

<script>
import { downloadLaborContractFile, getMyLaborContract, listMyLaborContracts, signMyLaborContract } from "@/api/oa/laborContract"
const { normalizeProtectedFileBlob } = require("@/utils/protectedFileBlob")
const { createTodoPersonalFocusMixin } = require("@/mixins/todoBusinessFocus")
const mobileViewport = require("../mobileViewport")
const { mobileErrorMessage } = require("../mobileErrorMessage")
const startMobileViewportSync = typeof mobileViewport.startMobileViewportSync === "function"
  ? mobileViewport.startMobileViewportSync
  : () => false
const stopMobileViewportSync = typeof mobileViewport.stopMobileViewportSync === "function"
  ? mobileViewport.stopMobileViewportSync
  : () => false

export default {
  name: "MobileLaborContract",
  mixins: [createTodoPersonalFocusMixin({
    featureKey: "laborContract",
    idKey: "contractId",
    loadFocusedRow(contractId) { return getMyLaborContract(contractId) },
    isActionable(contract) { return contract && contract.status === "pending_sign" },
    openFocusedRow(contract) { return this.applyContractDetail(contract) }
  })],
  data() {
    return {
      loading: false,
      fileLoading: false,
      signing: false,
      list: [],
      selectedContract: null,
      fileObjectUrl: "",
      inlineFileOwner: null,
      protectedFileOwners: [],
      fileError: "",
      fileRequestSequence: 0,
      fileLifecycleDestroyed: false,
      popupRequestSequence: 0,
      popupPreviews: [],
      confirmed: false,
      signConfirmText: "",
      drawing: false,
      hasSignature: false,
      lastPoint: null
    }
  },
  computed: {
    fileUrl() {
      return this.fileObjectUrl
    },
    currentFileKind() {
      return this.fileKindFromUrl(this.sourceFileUrl(this.selectedContract))
    },
    canInlinePreview() {
      return ["preview-pdf", "archive-pdf", "certificate"].includes(this.currentFileKind)
    },
    fileDownloadName() {
      const contractId = this.selectedContract && this.selectedContract.contractId
      return `劳动合同-${contractId || "文件"}.docx`
    }
  },
  created() {
    startMobileViewportSync()
    return this.initializeTodoPersonalFocus()
  },
  mounted() {
    window.addEventListener("resize", this.resizeSignatureCanvas)
  },
  beforeDestroy() {
    this.fileLifecycleDestroyed = true
    this.fileRequestSequence += 1
    this.fileLoading = false
    stopMobileViewportSync()
    window.removeEventListener("resize", this.resizeSignatureCanvas)
    this.revokeFileObjectUrl()
    this.popupPreviews.slice().forEach(entry => this.releasePopupPreview(entry, !entry.navigated))
    this.protectedFileOwners.slice().forEach(owner => this.releaseProtectedFileOwner(owner))
  },
  methods: {
    loadList() {
      this.loading = true
      listMyLaborContracts({ pageNum: 1, pageSize: 50 }).then(res => {
        this.list = res.rows || []
      }).finally(() => {
        this.loading = false
      })
    },
    openContract(item) {
      return getMyLaborContract(item.contractId).then(res => this.applyContractDetail(res.data))
    },
    applyContractDetail(contract) {
      this.selectedContract = contract
      this.confirmed = false
      this.signConfirmText = ""
      this.hasSignature = false
      this.loadContractFile()
      if (this.selectedContract && this.selectedContract.status === "pending_sign") {
        this.$nextTick(() => this.initSignatureCanvas())
      }
      return contract
    },
    goBack() {
      if (this.selectedContract) {
        this.fileRequestSequence += 1
        this.fileLoading = false
        this.fileError = ""
        this.selectedContract = null
        this.revokeFileObjectUrl()
        return
      }
      this.$router.back()
    },
    initSignatureCanvas() {
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
      if (this.selectedContract && this.selectedContract.status === "pending_sign" && !this.hasSignature) {
        this.$nextTick(() => this.initSignatureCanvas())
      }
    },
    startDraw(event) {
      const canvas = this.$refs.signatureCanvas
      if (!canvas) return
      this.drawing = true
      this.lastPoint = this.pointerPoint(event, canvas)
      this.drawDot(this.lastPoint)
    },
    draw(event) {
      if (!this.drawing || !this.lastPoint) return
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
      this.hasSignature = false
      this.initSignatureCanvas()
    },
    submitSign() {
      if (!this.fileObjectUrl) {
        this.$modal.msgWarning("合同文件尚未加载，请打开合同文件后再签署")
        return
      }
      if (!this.selectedContract.documentVersion || !this.selectedContract.previewFileHash) {
        this.$modal.msgWarning("合同签署版本未加载，请刷新后重试")
        return
      }
      if (!this.confirmed) {
        this.$modal.msgWarning("请先确认签署")
        return
      }
      if (this.signConfirmText !== "本人确认签署") {
        this.$modal.msgWarning("请输入本人确认签署")
        return
      }
      if (!this.hasSignature) {
        this.$modal.msgWarning("请完成手写签名")
        return
      }
      const canvas = this.$refs.signatureCanvas
      this.signing = true
      const selected = this.selectedContract
      return this.recheckTodoPersonalAction(selected.contractId).then(current => {
        if (!current) return null
        if (String(current.documentVersion || "") !== String(selected.documentVersion || "") ||
          String(current.previewFileHash || "") !== String(selected.previewFileHash || "")) {
          this.applyContractDetail(current)
          this.$modal.msgWarning("合同版本已更新，请重新阅读后签署")
          return null
        }
        this.selectedContract = current
        return signMyLaborContract(selected.contractId, {
          confirmed: true,
          documentVersion: this.selectedContract.documentVersion,
          previewFileHash: this.selectedContract.previewFileHash,
          signConfirmText: this.signConfirmText,
          signatureDataUrl: canvas.toDataURL("image/png")
        })
      }).then(res => {
        if (!res) return
        this.$modal.msgSuccess("签署完成")
        this.selectedContract = res.data
        this.loadContractFile()
        this.loadList()
      }).finally(() => {
        this.signing = false
      })
    },
    statusLabel(status) {
      const map = {
        draft: "草稿",
        pending_sign: "待签",
        signed: "已签",
        voided: "作废",
        expired: "过期"
      }
      return map[status] || (status ? "未知合同状态" : "-")
    },
    money(value) {
      const number = Number(value || 0)
      return number.toFixed(2)
    },
    normalizeFileUrl(url) {
      if (!url) return ""
      if (/^https?:\/\//.test(url)) return url
      if (url.indexOf("classpath:") === 0) return ""
      return process.env.VUE_APP_BASE_API + url
    },
    sourceFileUrl(contract) {
      if (!contract) return ""
      if (contract.status === "signed") {
        return contract.pdfFileUrl || contract.archiveFileUrl || contract.previewFileUrl
      }
      return contract.pdfFileUrl || contract.archiveFileUrl || contract.previewFileUrl
    },
    fileKindFromUrl(url) {
      const match = String(url || "").match(/\/download\/\d+\/([^/?#]+)/)
      return match ? match[1] : ""
    },
    loadContractFile() {
      const sequence = ++this.fileRequestSequence
      this.revokeFileObjectUrl()
      const url = this.sourceFileUrl(this.selectedContract)
      const kind = this.fileKindFromUrl(url)
      const contractId = this.selectedContract && this.selectedContract.contractId
      this.fileError = ""
      if (!contractId || !kind || this.fileLifecycleDestroyed) {
        this.fileLoading = false
        return Promise.resolve(null)
      }
      this.fileLoading = true
      const owner = this.createProtectedFileOwner({
        type: "inline",
        sequence,
        contractId,
        kind
      })
      this.inlineFileOwner = owner
      return this.createProtectedFileObjectUrl(contractId, kind, owner, () => {
        return this.isCurrentInlineFileRequest(sequence, contractId, kind)
      }).then(target => {
        if (!target || !this.isCurrentInlineFileRequest(sequence, contractId, kind)) {
          this.releaseProtectedFileOwner(owner)
          if (this.inlineFileOwner === owner) this.inlineFileOwner = null
          return null
        }
        this.fileObjectUrl = target
        return target
      }).catch(error => {
        this.releaseProtectedFileOwner(owner)
        if (this.inlineFileOwner === owner) this.inlineFileOwner = null
        if (!this.isCurrentInlineFileRequest(sequence, contractId, kind)) return null
        this.fileError = mobileErrorMessage(error, "合同文件加载失败，请重试")
        return null
      }).finally(() => {
        if (this.isCurrentInlineFileRequest(sequence, contractId, kind)) this.fileLoading = false
      })
    },
    isCurrentInlineFileRequest(sequence, contractId, kind) {
      return !this.fileLifecycleDestroyed &&
        sequence === this.fileRequestSequence &&
        String(this.selectedContract && this.selectedContract.contractId) === String(contractId) &&
        this.fileKindFromUrl(this.sourceFileUrl(this.selectedContract)) === kind
    },
    openProtectedFile(url) {
      const kind = this.fileKindFromUrl(url)
      const contractId = this.selectedContract && this.selectedContract.contractId
      if (!contractId || !kind || this.fileLifecycleDestroyed) return Promise.resolve(null)
      const previewWindow = window.open("about:blank", "_blank")
      if (!previewWindow) {
        this.$modal.msgWarning("浏览器阻止了新窗口，请允许弹出窗口后重试")
        return Promise.resolve(null)
      }
      const entry = {
        sequence: ++this.popupRequestSequence,
        contractId,
        kind,
        previewWindow,
        objectUrl: "",
        releaseTimer: null,
        loadListener: null,
        navigated: false,
        released: false,
        urlReleased: false,
        failureNotified: false
      }
      this.popupPreviews.push(entry)
      this.protectedFileOwners.push(entry)
      return this.createProtectedFileObjectUrl(contractId, kind, entry, () => this.isPopupPreviewActive(entry)).then(target => {
        if (!target || !this.isPopupPreviewActive(entry)) {
          this.releasePopupPreview(entry, !entry.navigated)
          return null
        }
        entry.objectUrl = target
        try {
          previewWindow.location.href = target
          entry.navigated = true
          entry.loadListener = () => this.releasePopupPreview(entry, false)
          entry.releaseTimer = setTimeout(entry.loadListener, 30000)
          if (typeof previewWindow.addEventListener === "function") {
            previewWindow.addEventListener("load", entry.loadListener, { once: true })
          }
          return target
        } catch (error) {
          this.notifyPopupPreviewFailure(entry)
          this.releasePopupPreview(entry, true)
          return null
        }
      }).catch(() => {
        if (this.isPopupPreviewActive(entry)) this.notifyPopupPreviewFailure(entry)
        this.releasePopupPreview(entry, true)
        return null
      })
    },
    isPopupPreviewActive(entry) {
      if (this.fileLifecycleDestroyed || !entry || entry.released ||
        this.popupPreviews.indexOf(entry) < 0) return false
      try {
        return !entry.previewWindow.closed
      } catch (error) {
        return false
      }
    },
    releasePopupPreview(entry, closeWindow) {
      if (!entry || entry.released) return
      entry.released = true
      if (entry.releaseTimer) {
        clearTimeout(entry.releaseTimer)
        entry.releaseTimer = null
      }
      if (entry.loadListener && typeof entry.previewWindow.removeEventListener === "function") {
        try {
          entry.previewWindow.removeEventListener("load", entry.loadListener)
        } catch (error) {
          // The popup may already have detached its document.
        }
      }
      entry.loadListener = null
      this.releaseProtectedFileOwner(entry)
      const index = this.popupPreviews.indexOf(entry)
      if (index >= 0) this.popupPreviews.splice(index, 1)
      if (!closeWindow) return
      try {
        if (!entry.previewWindow.closed) entry.previewWindow.close()
      } catch (error) {
        // The protected URL has already been released.
      }
    },
    notifyPopupPreviewFailure(entry) {
      if (!entry || entry.failureNotified || this.fileLifecycleDestroyed) return
      entry.failureNotified = true
      this.$modal.msgWarning("合同文件打开失败，请重试")
    },
    createProtectedFileOwner(source) {
      const owner = Object.assign({ objectUrl: "", urlReleased: false }, source || {})
      this.protectedFileOwners.push(owner)
      return owner
    },
    releaseProtectedFileOwner(owner) {
      if (!owner || owner.urlReleased) return
      owner.urlReleased = true
      if (owner.objectUrl) {
        URL.revokeObjectURL(owner.objectUrl)
        owner.objectUrl = ""
      }
      const index = this.protectedFileOwners.indexOf(owner)
      if (index >= 0) this.protectedFileOwners.splice(index, 1)
    },
    createProtectedFileObjectUrl(contractId, kind, owner, canCreate) {
      return downloadLaborContractFile(contractId, kind).then(blob => {
        if (typeof canCreate === "function" && !canCreate()) return null
        return normalizeProtectedFileBlob(blob, kind)
      }).then(fileBlob => {
        if (!fileBlob || (typeof canCreate === "function" && !canCreate())) return null
        const target = URL.createObjectURL(fileBlob)
        owner.objectUrl = target
        return target
      })
    },
    revokeFileObjectUrl() {
      if (this.inlineFileOwner) {
        this.releaseProtectedFileOwner(this.inlineFileOwner)
        this.inlineFileOwner = null
      } else if (this.fileObjectUrl) {
        URL.revokeObjectURL(this.fileObjectUrl)
      }
      this.fileObjectUrl = ""
    }
  }
}
</script>

<style scoped>
.mobile-contract-page {
  height: var(--mobile-viewport-height, 100dvh);
  min-height: var(--mobile-viewport-height, 100dvh);
  overflow-y: auto;
  background: var(--mobile-color-page);
  color: #1f2937;
  padding: 14px;
}

.mobile-contract-header {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 14px;
}

.mobile-contract-header h1 {
  margin: 0;
  font-size: 22px;
  font-weight: 700;
}

.mobile-contract-header p {
  margin: 2px 0 0;
  color: #64748b;
  font-size: 13px;
}

.back-button,
.refresh-button,
.ghost-button,
.primary-button {
  border: 0;
  border-radius: 8px;
  min-height: 44px;
}

.back-button {
  width: 44px;
  background: #ffffff;
  color: #0f172a;
  font-size: 26px;
  line-height: 1;
  box-shadow: 0 1px 8px rgba(15, 23, 42, 0.08);
}

.refresh-button {
  margin-left: auto;
  padding: 0 12px;
  background: #e0f2fe;
  color: #0369a1;
}

.contract-list,
.contract-detail {
  display: grid;
  gap: 12px;
}

.contract-row,
.detail-card,
.state-panel {
  background: #ffffff;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  box-shadow: 0 1px 10px rgba(15, 23, 42, 0.06);
}

.contract-row {
  width: 100%;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 14px;
  text-align: left;
}

.contract-row strong,
.contract-row span {
  display: block;
}

.contract-row strong {
  font-size: 16px;
  color: #111827;
}

.contract-row span {
  margin-top: 4px;
  color: #64748b;
  font-size: 13px;
}

.status-chip {
  flex: 0 0 auto;
  min-width: 52px;
  text-align: center;
  border-radius: 999px;
  padding: 5px 8px;
  font-style: normal;
  font-size: 12px;
  background: #e5e7eb;
  color: #374151;
}

.status-chip.pending_sign {
  background: #fef3c7;
  color: #92400e;
}

.status-chip.signed {
  background: #dcfce7;
  color: #166534;
}

.status-chip.voided,
.status-chip.expired {
  background: #e5e7eb;
  color: #4b5563;
}

.detail-card {
  padding: 14px;
}

.detail-head,
.section-head,
.sign-actions {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.detail-head h2,
.section-head h2 {
  margin: 0;
  font-size: 17px;
}

.detail-head p {
  margin: 4px 0 0;
  color: #64748b;
  font-size: 13px;
}

.section-head a {
  min-width: 44px;
  min-height: 44px;
  display: inline-flex;
  align-items: center;
  justify-content: flex-end;
  color: var(--mobile-color-primary);
  text-decoration: none;
  font-size: 14px;
}

.snapshot-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px;
  margin: 14px 0 0;
}

.snapshot-grid div {
  min-width: 0;
  background: #f8fafc;
  border-radius: 6px;
  padding: 10px;
}

.snapshot-grid dt {
  color: #64748b;
  font-size: 12px;
}

.snapshot-grid dd {
  margin: 4px 0 0;
  color: #0f172a;
  font-size: 14px;
  word-break: break-all;
}

.hash-text {
  font-size: 12px;
  line-height: 1.45;
}

.certificate-link {
  display: inline-flex;
  min-height: 44px;
  align-items: center;
  margin-top: 12px;
  padding: 0;
  border: 0;
  background: transparent;
  color: #047857;
  font-weight: 600;
  text-decoration: none;
}

.contract-frame {
  width: 100%;
  height: 380px;
  border: 1px solid #e5e7eb;
  border-radius: 6px;
  margin-top: 12px;
  background: #f8fafc;
}

.file-preview-fallback {
  display: grid;
  gap: 6px;
  margin-top: 12px;
  text-align: left;
  line-height: 1.5;
}

.file-preview-fallback strong {
  color: #0f172a;
  font-size: 15px;
}

.file-preview-fallback span {
  color: #64748b;
  font-size: 13px;
}

.confirm-row {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 12px;
  font-size: 14px;
}

.confirm-text-row {
  display: grid;
  gap: 6px;
  margin-bottom: 12px;
  font-size: 14px;
  color: #334155;
}

.confirm-text-row input {
  width: 100%;
  box-sizing: border-box;
  border: 1px solid #cbd5e1;
  border-radius: 6px;
  min-height: 44px;
  padding: 0 10px;
  color: #0f172a;
  background: #ffffff;
}

.signature-box {
  height: 180px;
  border: 1px solid #cbd5e1;
  border-radius: 8px;
  overflow: hidden;
  background: #ffffff;
}

.signature-box canvas {
  width: 100%;
  height: 100%;
  display: block;
  touch-action: none;
}

.sign-actions {
  margin-top: 12px;
}

.ghost-button {
  padding: 0 16px;
  background: var(--mobile-color-page, #f4f5f2);
  color: #334155;
}

.primary-button {
  padding: 0 18px;
  background: var(--mobile-color-primary);
  color: #ffffff;
}

.primary-button:disabled {
  background: #94a3b8;
}

.state-panel {
  padding: 24px 14px;
  text-align: center;
  color: #64748b;
}

.state-panel.compact {
  margin-top: 12px;
  padding: 16px;
}
</style>
