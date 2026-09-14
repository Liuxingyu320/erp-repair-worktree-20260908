import { getReimbursement, withdrawReimbursement } from '@/api/oa/reimbursement'
import { getSelectedDeptId } from '@/utils/shopContext'
const { createUiOperationScope } = require('@/utils/uiOperationScope')
const { approvalFailureKind, approvalMessage } = require('@/utils/approvalCommandRecovery')
export function createReimbursementWithdrawRecovery(config) {
  return {
    data() { return { withdrawCommand: null, withdrawError: '', withdrawReason: '', withdrawUnknown: false, checkingWithdraw: false, withdrawChecked: false } },
    computed: { withdrawIdentity() { return JSON.stringify({ actor: this.$store.getters.id, session: this.$store.state.user.sessionRevision, route: this.$route && this.$route.fullPath }) } },
    watch: { withdrawIdentity() { this.clearWithdrawContext() } },
    created() { if (typeof window !== 'undefined') window.addEventListener('erp:dept-changed', this.clearWithdrawContext) },
    beforeDestroy() { if (typeof window !== 'undefined') window.removeEventListener('erp:dept-changed', this.clearWithdrawContext); this.withdrawScope().deactivate() },
    deactivated() { this.withdrawScope().deactivate(); this.clearWithdrawContext() },
    activated() { this.withdrawScope().activate() },
    methods: {
      withdrawScope() {
        if (!this._withdrawScope) this._withdrawScope = createUiOperationScope(() => ({ identity: this.withdrawIdentity, dept: getSelectedDeptId(), inactive: this.pageInactive }))
        return this._withdrawScope
      },
      clearWithdrawContext() { this.withdrawScope().invalidate(); this.withdrawCommand = null; this.withdrawUnknown = false; this.withdrawError = ''; this.withdrawReason = ''; this.checkingWithdraw = false; this.withdrawChecked = false; this.acting = false },
      async withdraw(row) {
        if (this.acting || this.withdrawUnknown || this.checkingWithdraw || !row) return
        const id = String(row.reimbursementId || '')
        if (!/^[1-9]\d{0,18}$/.test(id)) return
        const scope = this.withdrawScope(), token = scope.begin('withdraw')
        const current = () => scope.isCurrent(token) && !this.pageInactive
        this.acting = true; this.withdrawError = ''
        let sent = false
        try {
          // Read before asking: list rows may not include the original approval round.
          const response = await getReimbursement(id, { silentError: true })
          if (!current()) return
          const record = response.data || {}
          if (String(record.reimbursementId) !== id || record.status !== 'pending' || !record.approvalInstanceId || !Number.isSafeInteger(Number(record.approvalRound)) || Number(record.approvalRound) < 1) throw Error('当前报销已不在可撤回的审批轮次，请刷新核对')
          const snapshot = { reimbursementId: id, expectedApprovalInstanceId: String(record.approvalInstanceId), expectedApprovalRound: Number(record.approvalRound) }
          const { value } = await this.$prompt('请输入撤回原因', '撤回报销申请', { inputValue: '申请人撤回', inputValidator: value => String(value || '').trim() ? true : '请输入撤回原因' })
          if (!current()) return
          const command = Object.freeze({ ...snapshot, reason: String(value).trim() })
          this.withdrawCommand = command; this.withdrawReason = command.reason; sent = true
          await this.sendWithdrawCommand(command, token)
        } catch (error) {
          if (current() && !sent && error !== 'cancel' && error !== 'close' && !(error && error.notified)) this.withdrawError = approvalMessage(error)
        } finally { if (current()) this.acting = false }
      },
      sendWithdrawCommand(command, token) {
        const scope = this.withdrawScope(), current = () => scope.isCurrent(token) && this.withdrawCommand === command && !this.pageInactive
        return withdrawReimbursement(command.reimbursementId, { reason: command.reason, expectedApprovalInstanceId: command.expectedApprovalInstanceId, expectedApprovalRound: command.expectedApprovalRound }).then(() => {
          if (!current()) return
          this.withdrawCommand = null; this.withdrawUnknown = false; this.withdrawError = ''
          this.$modal.msgSuccess('撤回请求已提交，审批状态会随后更新')
          return config.refresh(this)
        }).catch(error => {
          if (!current()) return
          const kind = approvalFailureKind(error)
          if (kind === 'handled') { this.withdrawCommand = null; return }
          this.withdrawUnknown = kind === 'unknown'; this.withdrawChecked = false
          this.withdrawError = this.withdrawUnknown ? '撤回结果待核对，已保留原报销单、审批轮次和原因，不会自动重新撤回。' : approvalMessage(error)
          if (!this.withdrawUnknown) this.withdrawCommand = null
        })
      },
      checkWithdrawCommand() {
        const command = this.withdrawCommand
        if (!command || !this.withdrawUnknown || this.checkingWithdraw || this.acting) return Promise.resolve()
        const scope = this.withdrawScope(), token = scope.begin('withdraw-check'), current = () => scope.isCurrent(token) && this.withdrawCommand === command
        this.checkingWithdraw = true; this.withdrawChecked = false
        return getReimbursement(command.reimbursementId, { silentError: true }).then(response => {
          if (!current()) return
          const record = response.data || {}
          if (String(record.reimbursementId) !== command.reimbursementId) throw Error('核对返回了不同报销单')
          const sameRound = String(record.approvalInstanceId) === command.expectedApprovalInstanceId && Number(record.approvalRound) === command.expectedApprovalRound
          if (sameRound && record.status === 'pending') {
            this.withdrawChecked = true; this.withdrawError = '原报销仍处于相同审批轮次；可显式重试原撤回请求。'
          } else {
            this.withdrawUnknown = false; this.withdrawCommand = null
            this.withdrawError = (record.status === 'withdrawn' && sameRound ? '已核对：原报销当前已撤回' : '已核对：报销状态或审批轮次已变化，已停止原撤回重试') + '。当前状态不证明由本次操作完成。'
            return config.refresh(this)
          }
        }).catch(error => { if (current() && !(error && error.notified)) this.withdrawError = '撤回结果仍待核对：' + approvalMessage(error) })
          .finally(() => { if (scope.isCurrent(token)) this.checkingWithdraw = false })
      },
      retryWithdrawCommand() {
        const command = this.withdrawCommand
        if (!command || !this.withdrawUnknown || !this.withdrawChecked || this.acting || this.checkingWithdraw) return Promise.resolve()
        const scope = this.withdrawScope(), token = scope.begin('withdraw')
        this.acting = true; this.withdrawChecked = false
        return this.sendWithdrawCommand(command, token).finally(() => { if (scope.isCurrent(token)) this.acting = false })
      }
    }
  }
}
