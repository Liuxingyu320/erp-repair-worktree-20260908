import { createReimbursementExport, downloadReimbursementExport, listReimbursementExports, getReimbursementExportCommand } from '@/api/oa/reimbursement'
import { getSelectedDeptId } from '@/utils/shopContext'
import { blobValidate } from '@/utils/common'
const { createUiOperationScope } = require('@/utils/uiOperationScope')
const { approvalMessage, newApprovalRequestId } = require('@/utils/approvalCommandRecovery')
// Only an explicit response rejection is definitive for a brand-new command.
// Generic business-code500 (even HTTP200), transport loss and old command replays stay unknown.
function exportFailureKind(error) {
  const response = error && error.response
  const status = Number(response && response.status)
  const code = Number(error && error.code || response && response.data && response.data.code)
  if (status === 401 || code === 401) return 'handled'
  if (!response || !Number.isFinite(status) || status <= 0 || status >= 500 || code >= 500) return 'unknown'
  const rejected = value => [400, 403, 404, 409, 413, 422].includes(value)
  return rejected(status) || status >= 200 && status < 300 && rejected(code) ? 'rejected' : 'unknown'
}
export default {
  data() { return { exportReceipts: [], pendingExportCommand: null, exportRecoveryError: '', exportCommandChecked: false, checkingExport: false,
    exportHistoryVisible: false, exportHistoryLoading: false, exportHistoryError: '', exportHistoryRows: [], exportHistoryTotal: 0, exportHistoryPage: 1, downloadingBatchId: '' } },
  computed: { exportIdentity() { return JSON.stringify({ actor: this.$store.getters.id, session: this.$store.state.user.sessionRevision, finance: this.financeMode }) } },
  watch: { exportIdentity() { this.resetExportRecovery() }, exportHistoryVisible(value) { if (!value) this.exportScope().invalidate('history') } },
  created() { if (typeof window !== 'undefined') window.addEventListener('erp:dept-changed', this.resetExportRecovery) },
  beforeDestroy() { if (typeof window !== 'undefined') window.removeEventListener('erp:dept-changed', this.resetExportRecovery); this.exportScope().deactivate() },
  deactivated() { this.exportScope().deactivate(); this.exporting = false; this.exportHistoryLoading = false; this.downloadingBatchId = '' },
  activated() { this.exportScope().activate() },
  methods: {
    exportScope() { if (!this._exportScope) this._exportScope = createUiOperationScope(() => ({ identity: this.exportIdentity, dept: getSelectedDeptId() })); return this._exportScope },
    resetExportRecovery() { this.exportScope().invalidate(); this.exportReceipts = []; this.pendingExportCommand = null; this.exportRecoveryError = ''; this.exporting = false; this.checkingExport = false; this.exportCommandChecked = false; this.exportHistoryRows = []; this.exportHistoryTotal = 0; this.exportHistoryLoading = false; this.downloadingBatchId = '' },
    exportSelected() {
      if (!this.selectedRows.length || this.exporting || this.pendingExportCommand) return Promise.resolve()
      const ids = Array.from(new Set(this.selectedRows.map(row => String(row.reimbursementId))))
      if (ids.some(id => !/^[1-9]\d{0,18}$/.test(id))) { this.$modal.msgError('所选报销编号无效，请重新选择'); return Promise.resolve() }
      const command = Object.freeze({ requestId: newApprovalRequestId(), reimbursementIds: Object.freeze(ids) })
      const scope = this.exportScope(), token = scope.begin('export')
      this.exporting = true; this.exportRecoveryError = ''
      const repeat = this.selectedRows.some(row => row.exportStatus === 'exported')
      return this.$modal.confirm(repeat ? '所选数据包含已导出报销单，将生成新的可追溯批次。确认继续？' : '确认生成所选报销的会计资料包（含三张 Excel 工作表及发票）？', '导出会计资料').then(() => {
        if (!scope.isCurrent(token)) return
        this.pendingExportCommand = command
        return this.createExportCommand(command, token, true)
      }).catch(error => { if (scope.isCurrent(token) && error !== 'cancel' && error !== 'close' && !(error && error.notified)) this.exportRecoveryError = approvalMessage(error) })
        .finally(() => { if (scope.isCurrent(token)) this.exporting = false })
    },
    createExportCommand(command, token, firstAttempt = false) {
      const scope = this.exportScope(), current = () => scope.isCurrent(token) && this.pendingExportCommand === command
      return createReimbursementExport(command.reimbursementIds, command.requestId).then(response => {
        if (!current()) return
        const batch = response.data || {}
        if (!batch.batchId || batch.requestId !== command.requestId) { this.exportRecoveryError = '创建回执不完整，请先核对原导出请求'; this.exportCommandChecked = false; return }
        this.acceptExportReceipt(batch)
        return this.downloadOriginalExport(batch)
      }).catch(error => {
        if (!current()) return
        const kind = exportFailureKind(error)
        this.exportCommandChecked = false
        if (firstAttempt && kind === 'rejected') {
          this.pendingExportCommand = null
          this.exportRecoveryError = approvalMessage(error) + '；本次导出未受理，请调整选择后重新导出。'
        } else if (kind !== 'handled') {
          this.exportRecoveryError = kind === 'rejected'
            ? approvalMessage(error) + '；此前原导出结果仍待核对，请查询原请求，不能据此认定原批次未生成。'
            : '批次创建结果待核对，原请求号与选中报销单已保留，不会自动生成第二批资料。'
        }
      })
    },
    acceptExportReceipt(batch) {
      this.exportReceipts = [batch, ...this.exportReceipts.filter(row => String(row.batchId) !== String(batch.batchId))].slice(0, 10)
      this.pendingExportCommand = null; this.exportRecoveryError = ''; this.exportCommandChecked = false; this.exporting = false
      this.selectedRows = []
      Promise.resolve(this.loadList()).catch(() => {})
      if (this.exportHistoryVisible) this.loadExportHistory()
    },
    checkExportCommand() {
      const command = this.pendingExportCommand
      if (!command || this.checkingExport || this.exporting) return Promise.resolve()
      const scope = this.exportScope(), token = scope.begin('export-check'), current = () => scope.isCurrent(token) && this.pendingExportCommand === command
      this.checkingExport = true; this.exportCommandChecked = false
      return getReimbursementExportCommand(command.requestId).then(response => {
        if (!current()) return
        const result = response.data || {}
        if (result.state === 'SUCCEEDED' && result.batch && result.batch.requestId === command.requestId && result.batch.batchId) {
          this.acceptExportReceipt(result.batch)
          this.$modal.msgSuccess('已找到原导出批次，请点击原批次下载')
        } else if (result.state === 'NOT_OBSERVED') { this.exportCommandChecked = true; this.exportRecoveryError = '暂未观察到原批次，不能证明未生成；可显式重试相同请求号与原报销集合。' }
        else this.exportRecoveryError = '导出回执无法确认，请稍后再核对'
      }).catch(error => { if (current() && !(error && error.notified)) this.exportRecoveryError = '导出回执查询失败：' + approvalMessage(error) })
        .finally(() => { if (scope.isCurrent(token)) this.checkingExport = false })
    },
    retryExportCommand() {
      const command = this.pendingExportCommand
      if (!command || !this.exportCommandChecked || this.checkingExport || this.exporting) return Promise.resolve()
      const scope = this.exportScope(), token = scope.begin('export')
      this.exporting = true; this.exportCommandChecked = false
      return this.createExportCommand(command, token).finally(() => { if (scope.isCurrent(token)) this.exporting = false })
    },
    downloadOriginalExport(batch) {
      if (!batch || !batch.batchId || this.downloadingBatchId) return Promise.resolve()
      const scope = this.exportScope(), token = scope.begin('download'), id = String(batch.batchId)
      this.downloadingBatchId = id
      return downloadReimbursementExport(id).then(blob => {
        if (!scope.isCurrent(token)) return
        if (!blobValidate(blob)) throw Error('原导出包返回格式异常，请重新下载或核对批次状态')
        this.$download.saveAs(blob, batch.archiveName || `报销会计资料_${batch.batchNo || id}.zip`)
        this.$modal.msgSuccess('已发起原批次下载，请在浏览器下载列表确认')
      }).catch(error => {
        if (scope.isCurrent(token) && !(error && error.notified)) this.exportRecoveryError = `批次 ${batch.batchNo || id} 下载未完成：${approvalMessage(error)}。可重新下载此批次，不会重新生成资料。`
      }).finally(() => { if (scope.isCurrent(token)) this.downloadingBatchId = '' })
    },
    openExportHistory() { this.exportHistoryVisible = true; this.exportHistoryPage = 1; return this.loadExportHistory() },
    loadExportHistory() {
      if (!this.exportHistoryVisible) return Promise.resolve()
      const scope = this.exportScope(), token = scope.begin('history'), current = () => scope.isCurrent(token) && this.exportHistoryVisible
      this.exportHistoryLoading = true; this.exportHistoryError = ''
      return listReimbursementExports({ pageNum: this.exportHistoryPage, pageSize: 10 }).then(response => {
        if (!current()) return
        this.exportHistoryRows = response.rows || []; this.exportHistoryTotal = Number(response.total) || 0
      }).catch(error => { if (current()) this.exportHistoryError = approvalMessage(error) })
        .finally(() => { if (current()) this.exportHistoryLoading = false })
    }
  }
}
