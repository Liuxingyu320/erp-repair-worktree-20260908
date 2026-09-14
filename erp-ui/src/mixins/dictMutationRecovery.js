import { getType, delType, addType, updateType } from '@/api/system/dict/type'
import { getSelectedDeptId } from '@/utils/shopContext'
const { createUiOperationScope } = require('@/utils/uiOperationScope')
const { approvalFailureKind, approvalMessage } = require('@/utils/approvalCommandRecovery')
function dictionaryFailureKind(error) {
  const kind = approvalFailureKind(error)
  if (kind === 'handled') return kind
  const code = Number(error && (error.code || error.response && error.response.data && error.response.data.code))
  return code >= 500 ? 'unknown' : kind
}
export default {
  data: () => ({ saving: false, deleting: false, editorLoading: false, editorReady: false, dictSaveUnknown: false, mutationError: '', cacheRefreshPending: false, originalDictType: '', deleteUnknownIds: [], checkingDelete: false }),
  computed: { dictIdentity() { return JSON.stringify([this.$store.getters.id, this.$store.state.user.sessionRevision, this.$route.fullPath]) } },
  watch: { dictIdentity() { this.invalidateDictMutation() }, open(value) { if (!value) this.dictMutationScope().invalidate('edit') } },
  created() { if (typeof window !== 'undefined') window.addEventListener('erp:dept-changed', this.invalidateDictMutation) },
  beforeDestroy() { if (typeof window !== 'undefined') window.removeEventListener('erp:dept-changed', this.invalidateDictMutation); this.dictMutationScope().deactivate() },
  deactivated() { this.dictMutationScope().deactivate(); this.open = false; this.saving = false; this.deleting = false },
  activated() { this.dictMutationScope().activate() },
  methods: {
    dictMutationScope() { if (!this._dictMutationScope) this._dictMutationScope = createUiOperationScope(() => ({ identity: this.dictIdentity, dept: getSelectedDeptId() })); return this._dictMutationScope },
    invalidateDictMutation() { this.dictMutationScope().invalidate(); this.open = false; this.form = {}; this.originalDictType = ''; this.saving = false; this.deleting = false; this.editorLoading = false; this.mutationError = ''; this.deleteUnknownIds = []; this.checkingDelete = false; this.ids = [] },
    clearLocalDictKeys(keys) { Array.from(new Set(keys.filter(Boolean))).forEach(key => this.$store.dispatch('dict/removeDict', key)) },
    receiveDictMutation(response, message, keys) {
      this.clearLocalDictKeys(keys)
      this.cacheRefreshPending = !!(response && response.cacheRefreshPending)
      this.mutationError = this.cacheRefreshPending ? `${message}，缓存刷新待重试；可使用“刷新缓存”，请勿重复提交。` : ''
      this.$modal.msgSuccess(message)
    },
    openDictEditor(row) {
      if (this.saving || this.deleting) return Promise.resolve()
      const id = row && row.dictId || (this.ids.length === 1 ? this.ids[0] : null)
      if (!id) return Promise.resolve()
      this.reset(); this.originalDictType = ''; this.open = true; this.title = '修改字典类型'
      const scope = this.dictMutationScope(), token = scope.begin('edit'), current = () => scope.isCurrent(token) && this.open
      this.editorLoading = true; this.editorReady = false; this.dictSaveUnknown = false; this.mutationError = ''
      return getType(id, { silentError: true }).then(response => {
        if (!current()) return
        if (!response.data || String(response.data.dictId) !== String(id)) throw Error('字典已不存在，请刷新列表')
        this.form = response.data; this.editorReady = true; this.originalDictType = response.data.dictType
      }).catch(error => { if (current() && !(error && error.notified)) this.mutationError = approvalMessage(error) })
        .finally(() => { if (current()) this.editorLoading = false })
    },
    saveDictEditor() {
      if (this.saving || this.editorLoading || !this.editorReady || this.dictSaveUnknown || !this.open) return Promise.resolve()
      const payload = JSON.parse(JSON.stringify(this.form)), oldType = this.originalDictType
      const scope = this.dictMutationScope(), token = scope.begin('edit'), current = () => scope.isCurrent(token) && this.open
      this.saving = true; this.mutationError = ''
      return new Promise(resolve => this.$refs.form.validate(resolve)).then(valid => {
        if (!valid || !current()) return
        return (payload.dictId ? updateType(payload, { silentError: true }) : addType(payload, { silentError: true })).then(response => {
          if (!current()) return
          this.receiveDictMutation(response, payload.dictId ? '修改已完成' : '新增已完成', [oldType, payload.dictType])
          this.saving = false; this.open = false; return this.getList()
        })
      }).catch(error => { if (current() && !(error && error.notified)) { this.dictSaveUnknown = dictionaryFailureKind(error) === 'unknown'; this.mutationError = this.dictSaveUnknown ? '保存结果待核对，请先重新读取该字典或列表，当前输入已保留；不要直接重复新增或改名。' : approvalMessage(error) } })
        .finally(() => { if (scope.isCurrent(token)) this.saving = false })
    },
    deleteDictSelection(row) {
      if (this.deleting || this.saving || this.deleteUnknownIds.length) return Promise.resolve()
      const ids = Object.freeze(Array.from(new Set((row && row.dictId ? [row.dictId] : this.ids).map(String))))
      if (!ids.length) return Promise.resolve()
      const keys = this.typeList.filter(item => ids.includes(String(item.dictId))).map(item => item.dictType)
      const scope = this.dictMutationScope(), token = scope.begin('delete'), current = () => scope.isCurrent(token)
      this.deleting = true; this.mutationError = ''; let sent = false
      return this.$modal.confirm(`确认删除所选 ${ids.length} 个字典类型（编号 ${ids.join('、')}）？存在字典项时整批不会删除。`).then(() => {
        if (!current()) return
        sent = true; return delType(ids, { silentError: true }).then(response => {
          if (!current()) return
          this.receiveDictMutation(response, '所选字典已全部删除', keys); this.ids = []; return this.getList()
        })
      }).catch(error => {
        if (!current() || (!sent && (error === 'cancel' || error === 'close'))) return
        if (sent && dictionaryFailureKind(error) === 'unknown') { this.deleteUnknownIds = ids.slice(); this.mutationError = '删除结果待核对，原选中编号已保留。请先读取原编号的当前状态。' }
        else if (!(error && error.notified)) this.mutationError = approvalMessage(error)
      }).finally(() => { if (current()) this.deleting = false })
    },
    checkDeletedDictionaries() {
      if (!this.deleteUnknownIds.length || this.checkingDelete) return Promise.resolve()
      const ids = this.deleteUnknownIds.slice(), scope = this.dictMutationScope(), token = scope.begin('delete-check'), current = () => scope.isCurrent(token)
      this.checkingDelete = true
      return Promise.all(ids.map(id => getType(id, { silentError: true }).then(response => ({ id, exists: !!response.data })))).then(rows => {
        if (!current()) return
        const remaining = rows.filter(row => row.exists).map(row => row.id)
        this.ids = remaining; this.single = remaining.length !== 1; this.multiple = !remaining.length; this.deleteUnknownIds = []
        this.mutationError = `原 ${ids.length} 个编号中，当前仍存在 ${remaining.length} 个、已不存在 ${ids.length - remaining.length} 个；这仅是当前状态，不证明由本次请求完成。可重新选择仍存在项再确认。`
        return this.getList()
      }).catch(error => { if (current() && !(error && error.notified)) this.mutationError = '核对失败，原编号仍保留：' + approvalMessage(error) })
        .finally(() => { if (current()) this.checkingDelete = false })
    }
  }
}
