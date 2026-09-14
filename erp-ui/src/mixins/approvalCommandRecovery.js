import { approveApprovalTask, returnApprovalTask, rejectApprovalTask } from '@/api/approval/task'
import { getApprovalInstance } from '@/api/approval/monitor'
import { getSelectedDeptId } from '@/utils/shopContext'
const { createUiOperationScope } = require('@/utils/uiOperationScope')
const { createApprovalCommand, unwrapApprovalDetail, matchesApprovalCommand, approvalFailureKind, approvalMessage, newApprovalRequestId } = require('@/utils/approvalCommandRecovery')

// Only the four explicit OA approval pages opt in. Global silentError behavior is unchanged.
export function createApprovalCommandRecovery(config) {
  return {
    data() { return { approvalCommand: null, approvalError: '', approvalReason: '', approvalUnknown: false, checkingApproval: false, approvalChecked: false } },
    computed: {
      approvalOperationIdentity() {
        const store = this.$store || {}, user = store.state && store.state.user || {}
        return JSON.stringify({ actor: store.getters && store.getters.id, session: user.sessionRevision,
          target: config.target(this), visible: config.visible(this), path: this.$route && this.$route.fullPath })
      }
    },
    watch: { approvalOperationIdentity() { this.resetApprovalCommandContext() } },
    created() { if (typeof window !== 'undefined') window.addEventListener('erp:dept-changed', this.resetApprovalCommandContext) },
    beforeDestroy() { if (typeof window !== 'undefined') window.removeEventListener('erp:dept-changed', this.resetApprovalCommandContext); this.approvalCommandScope().deactivate() },
    deactivated() { this.approvalCommandScope().deactivate(); this.resetApprovalCommandContext() },
    activated() { this.approvalCommandScope().activate() },
    methods: {
      approvalCommandScope() {
        if (!this._approvalCommandScope) this._approvalCommandScope = createUiOperationScope(() => ({ identity: this.approvalOperationIdentity, dept: getSelectedDeptId() }))
        return this._approvalCommandScope
      },
      resetApprovalCommandContext() {
        this.approvalCommandScope().invalidate(); this.approvalCommand = null; this.approvalUnknown = false
        this.approvalError = ''; this.approvalReason = ''; this.checkingApproval = false; this.approvalChecked = false
        this[config.loading] = false
      },
      runApprovalCommand(action) {
        if (!config.canAct(this) || this[config.loading] || this.approvalUnknown) return Promise.resolve()
        const target = config.target(this), scope = this.approvalCommandScope(), token = scope.begin('approval', target)
        const current = () => config.visible(this) && scope.isCurrent(token, config.target(this))
        this[config.loading] = true; this.approvalError = ''
        let sent = false
        const ask = action === 'approve' ? this.$modal.confirm('确认同意当前申请？', '审批确认').then(() => '')
          : this.$prompt(action === 'return' ? '请输入退回原因' : '请输入拒绝原因', action === 'return' ? '退回修改' : '拒绝申请', {
            inputValue: this.approvalReason, inputValidator: value => String(value || '').trim() ? true : '原因不能为空'
          }).then(({ value }) => String(value).trim())
        return ask.then(comment => {
          if (!current() || !config.canAct(this)) return
          this.approvalReason = comment
          const command = createApprovalCommand(target, action, comment, this.$store.getters.id, newApprovalRequestId())
          this.approvalCommand = command; sent = true
          return this.sendApprovalCommand(command, token)
        }).catch(error => {
          if (current() && !sent && error !== 'cancel' && error !== 'close') this.approvalError = approvalMessage(error)
        }).finally(() => { if (current()) this[config.loading] = false })
      },
      sendApprovalCommand(command, token) {
        const scope = this.approvalCommandScope()
        const current = () => config.visible(this) && scope.isCurrent(token, config.target(this)) && this.approvalCommand === command
        const action = { approve: approveApprovalTask, return: returnApprovalTask, reject: rejectApprovalTask }[command.action]
        return action(command.taskId, { requestId: command.requestId, reason: command.reason }).then(() => {
          if (!current()) return
          this.approvalUnknown = false; this.approvalError = ''; this.approvalCommand = null
          this.$modal.msgSuccess('审批动作已提交')
          return config.success(this, command)
        }).catch(error => {
          if (!current()) return
          const kind = approvalFailureKind(error)
          if (kind === 'handled') { this.approvalCommand = null; return }
          this.approvalUnknown = kind === 'unknown'; this.approvalChecked = false
          this.approvalError = this.approvalUnknown ? '审批结果待核对，意见与原请求号已保留。请核对结果，系统不会自动再次审批。' : approvalMessage(error)
          if (!this.approvalUnknown) this.approvalCommand = null
        })
      },
      checkApprovalCommand() {
        const command = this.approvalCommand
        if (!command || !this.approvalUnknown || this.checkingApproval || this[config.loading]) return Promise.resolve()
        const scope = this.approvalCommandScope(), token = scope.begin('approval-check', config.target(this))
        const current = () => config.visible(this) && scope.isCurrent(token, config.target(this)) && this.approvalCommand === command
        this.checkingApproval = true; this.approvalChecked = false
        return getApprovalInstance(command.instanceId, { silentError: true }).then(response => {
          if (!current()) return
          const detail = unwrapApprovalDetail(response)
          if (matchesApprovalCommand(detail, command)) {
            this.approvalUnknown = false; this.approvalCommand = null; this.approvalError = ''
            this.$modal.msgSuccess('已核对：本次审批动作已成功提交')
            return config.success(this, command)
          }
          const instance = detail.instance || {}
          const bound = String(instance.instanceId) === command.instanceId && String(instance.businessId) === command.businessId && instance.businessCode === command.businessCode
          this.approvalChecked = bound
          this.approvalError = bound ? '当前审批状态：' + (instance.status || '未知') + '。未找到与本次请求、操作者、动作及意见完全一致的记录，不能认定本次成功；可显式重试原动作。' : '核对回包与原业务不一致，请返回待办重新核对，不能重试。'
        }).catch(error => { if (current() && !(error && error.notified)) this.approvalError = '审批结果仍待核对：' + approvalMessage(error) })
          .finally(() => { if (scope.isCurrent(token, config.target(this))) this.checkingApproval = false })
      },
      retryApprovalCommand() {
        const command = this.approvalCommand
        if (!command || !this.approvalUnknown || !this.approvalChecked || this[config.loading] || this.checkingApproval) return Promise.resolve()
        const scope = this.approvalCommandScope(), token = scope.begin('approval', config.target(this))
        this[config.loading] = true; this.approvalChecked = false
        return this.sendApprovalCommand(command, token).finally(() => { if (scope.isCurrent(token, config.target(this))) this[config.loading] = false })
      }
    }
  }
}
