import { getFixedAssetConfigSnapshot, saveFixedAssetConfigBatch, getFixedAssetConfigBatchCommand } from '@/api/oa/fixedAsset'
import { getSelectedDeptId } from '@/utils/shopContext'
const { createUiOperationScope } = require('@/utils/uiOperationScope')
const { newApprovalRequestId, approvalFailureKind, approvalMessage } = require('@/utils/approvalCommandRecovery')
const clone = value => JSON.parse(JSON.stringify(value))
export default {
  data: () => ({ configReady: false, configReading: false, loadedConfigShop: '', configVersion: null, configBaseline: '', configError: '', pendingConfigCommand: null, configCommandChecked: false, checkingConfigCommand: false, deletingConfig: false, latestConfigSnapshot: null }),
  computed: {
    fixedConfigIdentity() { return JSON.stringify([this.$store.getters.id, this.$store.state.user.sessionRevision, this.$route.fullPath]) },
    configEditingLocked() { return this.saving || this.configReading || !!this.pendingConfigCommand },
    fixedConfigDirty() { return !!this.configBaseline && this.configBaseline !== this.configFormSignature() }
  },
  watch: { fixedConfigIdentity() { this.resetConfigBatchContext() } },
  created() { if (typeof window !== 'undefined') window.addEventListener('erp:dept-changed', this.resetConfigBatchContext) },
  beforeDestroy() { if (typeof window !== 'undefined') window.removeEventListener('erp:dept-changed', this.resetConfigBatchContext); this.fixedConfigScope().deactivate() },
  deactivated() { this.fixedConfigScope().deactivate(); this.saving = false; this.configReading = false; this.deletingConfig = false },
  activated() { this.fixedConfigScope().activate() },
  methods: {
    fixedConfigScope() { if (!this._fixedConfigScope) this._fixedConfigScope = createUiOperationScope(() => ({ identity: this.fixedConfigIdentity, dept: getSelectedDeptId() })); return this._fixedConfigScope },
    resetConfigBatchContext() { this.fixedConfigScope().invalidate(); this.configOpen = false; this.configReady = false; this.configReading = false; this.saving = false; this.deletingConfig = false; this.pendingConfigCommand = null; this.configError = ''; this.latestConfigSnapshot = null; this.checkingConfigCommand = false },
    configFormSignature() { return JSON.stringify({ form: { ...this.form, shopDeptId: this.loadedConfigShop }, rows: this.selectedAssetRows }) },
    openConfigBatch(row) {
      if (this.saving || this.pendingConfigCommand || this.deletingConfig) return Promise.resolve()
      const shop = row && row.shopDeptId || this.queryParams.shopDeptId
      this.configOpen = true
      if (!shop) { this.applyStoreConfigToForm(null, undefined); this.loadedConfigShop = ''; this.configReady = false; this.configVersion = null; this.configBaseline = ''; return Promise.resolve() }
      return this.readConfigSnapshot(String(shop))
    },
    changeConfigShop(shop) {
      if (this.configEditingLocked) { this.form.shopDeptId = this.loadedConfigShop || undefined; return Promise.resolve() }
      if (String(shop || '') === this.loadedConfigShop) return Promise.resolve()
      const scope = this.fixedConfigScope(), token = scope.begin('shop-confirm'), previous = this.loadedConfigShop
      const ask = this.fixedConfigDirty ? this.$modal.confirm('本店尚有未保存修改，确认放弃并读取另一店铺？') : Promise.resolve()
      return ask.then(() => { if (scope.isCurrent(token)) return this.readConfigSnapshot(String(shop || '')) }).catch(error => { if (scope.isCurrent(token)) { this.form.shopDeptId = previous || undefined; if (error !== 'cancel' && error !== 'close') this.configError = approvalMessage(error) } })
    },
    readConfigSnapshot(shop) {
      const scope = this.fixedConfigScope(), token = scope.begin('snapshot')
      this.configReady = false; this.configReading = true; this.configError = ''; this.latestConfigSnapshot = null
      if (!shop) { this.loadedConfigShop = ''; this.configVersion = null; this.configReading = false; this.applyStoreConfigToForm(null, undefined); return Promise.resolve() }
      return getFixedAssetConfigSnapshot(shop).then(response => {
        if (!scope.isCurrent(token)) return
        this.applyConfigSnapshot(response.data, shop)
        return this.loadSelectableOeOptions()
      }).catch(error => { if (scope.isCurrent(token) && !(error && error.notified)) this.configError = approvalMessage(error) })
        .finally(() => { if (scope.isCurrent(token)) this.configReading = false })
    },
    applyConfigSnapshot(snapshot, shop) {
      if (!snapshot || String(snapshot.shopDeptId) !== String(shop) || snapshot.version == null || !Array.isArray(snapshot.rows)) throw Error('店铺配置快照不完整，请重新读取')
      this.applyStoreConfigToForm({ shopDeptId: String(shop), details: snapshot.rows, annualRepairRatio: snapshot.annualRepairRatio, status: snapshot.rows[0] && snapshot.rows[0].status || '0', remark: snapshot.rows[0] && snapshot.rows[0].remark || '' }, String(shop))
      this.loadedConfigShop = String(shop); this.form.shopDeptId = String(shop); this.configVersion = String(snapshot.version); this.configReady = true
      this.configBaseline = this.configFormSignature(); this.latestConfigSnapshot = null
    },
    buildConfigCommand(rows, mode = 'save', snapshot) {
      const shop = snapshot ? String(snapshot.shopDeptId) : this.loadedConfigShop
      const payload = { requestId: newApprovalRequestId(), shopDeptId: shop, expectedVersion: snapshot ? String(snapshot.version) : this.configVersion, annualRepairRatio: snapshot ? snapshot.annualRepairRatio : this.form.annualRepairRatio,
        rows: rows.map(row => ({ configId: row.configId ? String(row.configId) : null, shopDeptId: shop, oeItemId: String(row.oeItemId), assetQuantity: row.assetQuantity, assetUnitPrice: row.assetUnitPrice, status: this.form.status || '0', remark: this.form.remark || '' })) }
      return Object.freeze({ mode, payload: Object.freeze(clone(payload)) })
    },
    saveConfigBatch() {
      if (this.configEditingLocked || !this.configReady || !this.configOpen) return Promise.resolve()
      if (!this.selectedAssetRows.length) { this.$modal.msgWarning('请选择固定资产OE器皿；整店删除请使用列表删除入口'); return Promise.resolve() }
      if (this.selectedAssetRows.some(row => !Number.isFinite(Number(row.assetQuantity)) || Number(row.assetQuantity) <= 0)) { this.$modal.msgWarning('请输入有效的固定资产数量'); return Promise.resolve() }
      const command = this.buildConfigCommand(this.selectedAssetRows), scope = this.fixedConfigScope(), token = scope.begin('write')
      this.saving = true; this.configError = ''
      return new Promise(resolve => this.$refs.configForm.validate(resolve)).then(valid => {
        if (!valid || !scope.isCurrent(token)) return
        this.pendingConfigCommand = command
        return this.sendConfigCommand(command, token)
      }).finally(() => { if (scope.isCurrent(token)) this.saving = false })
    },
    sendConfigCommand(command, token) {
      const scope = this.fixedConfigScope(), current = () => scope.isCurrent(token) && this.pendingConfigCommand === command
      return saveFixedAssetConfigBatch(command.payload).then(response => {
        if (!current()) return
        return this.acceptConfigCommand(response.data, command)
      }).catch(error => {
        if (!current()) return
        const code = error && (error.code || error.response && error.response.data && error.response.data.code)
        const kind = Number(code) >= 500 ? 'unknown' : approvalFailureKind(error)
        if (kind === 'rejected') { this.pendingConfigCommand = null; this.configCommandChecked = false; this.configError = approvalMessage(error) + '；整批未保存，输入已保留，可先查看服务器快照。' }
        else if (kind !== 'handled') { this.configCommandChecked = false; this.configError = '整店保存结果待核对，原请求和完整资产集合已保留，不会自动重复保存。' }
      })
    },
    acceptConfigCommand(result, command) {
      if (!result || result.requestId !== command.payload.requestId || String(result.shopDeptId) !== command.payload.shopDeptId || !Array.isArray(result.rows)) { this.configError = '保存回执不完整，请核对原请求'; this.configCommandChecked = false; return }
      const advanced = result.currentVersion != null && String(result.currentVersion) !== String(result.version)
      if (command.mode === 'save' && !advanced) this.applyConfigSnapshot(result, command.payload.shopDeptId)
      this.pendingConfigCommand = null; this.configCommandChecked = false; this.configError = advanced ? '原请求已完成，但店铺已存在后续修改；当前输入已保留，请查看并比较服务器最新快照。' : ''; this.saving = false; this.deletingConfig = false
      if (advanced) this.configReady = false
      this.$modal.msgSuccess(command.mode === 'delete' ? '该店配置已整批删除' : '该店配置已整批保存，可继续编辑')
      return Promise.resolve(this.getList()).catch(() => {})
    },
    checkConfigCommand() {
      const command = this.pendingConfigCommand
      if (!command || this.checkingConfigCommand || this.saving || this.deletingConfig) return Promise.resolve()
      const scope = this.fixedConfigScope(), token = scope.begin('command-check'), current = () => scope.isCurrent(token) && this.pendingConfigCommand === command
      this.checkingConfigCommand = true; this.configCommandChecked = false
      return getFixedAssetConfigBatchCommand(command.payload.requestId, command.payload.shopDeptId).then(response => {
        if (!current()) return
        const result = response.data || {}
        if (result.state === 'SUCCEEDED') return this.acceptConfigCommand(result.snapshot, command)
        if (result.state === 'NOT_OBSERVED') { this.configCommandChecked = true; this.configError = '暂未观察到原命令回执，不能证明未提交；可显式重试原请求和原资产集合。' }
        else this.configError = '原请求仍未确认，请稍后再核对'
      }).catch(error => { if (current() && !(error && error.notified)) this.configError = '核对失败：' + approvalMessage(error) })
        .finally(() => { if (scope.isCurrent(token)) this.checkingConfigCommand = false })
    },
    retryConfigCommand() {
      const command = this.pendingConfigCommand
      if (!command || !this.configCommandChecked || this.checkingConfigCommand || this.saving) return Promise.resolve()
      const scope = this.fixedConfigScope(), token = scope.begin('write'); this.saving = true; this.configCommandChecked = false
      return this.sendConfigCommand(command, token).finally(() => { if (scope.isCurrent(token)) this.saving = false })
    },
    compareCurrentConfig() {
      if (!this.loadedConfigShop || this.pendingConfigCommand || this.saving) return Promise.resolve()
      const scope = this.fixedConfigScope(), token = scope.begin('compare'), shop = this.loadedConfigShop
      return getFixedAssetConfigSnapshot(shop).then(response => { if (scope.isCurrent(token) && shop === this.loadedConfigShop) this.latestConfigSnapshot = response.data })
        .catch(error => { if (scope.isCurrent(token) && !(error && error.notified)) this.configError = approvalMessage(error) })
    },
    adoptCurrentConfig() {
      const snapshot = this.latestConfigSnapshot
      if (!snapshot || this.pendingConfigCommand || this.saving) return Promise.resolve()
      const scope = this.fixedConfigScope(), token = scope.begin('compare')
      return this.$modal.confirm('确认放弃当前未保存输入，采用下方服务器最新快照？').then(() => { if (scope.isCurrent(token)) this.applyConfigSnapshot(snapshot, snapshot.shopDeptId) }).catch(() => {})
    },
    removeConfigBatch(row) {
      if (!row || !row.shopDeptId || this.saving || this.pendingConfigCommand || this.deletingConfig) return Promise.resolve()
      const scope = this.fixedConfigScope(), token = scope.begin('write'), shop = String(row.shopDeptId); this.deletingConfig = true; this.configError = ''
      return getFixedAssetConfigSnapshot(shop).then(response => {
        if (!scope.isCurrent(token)) return
        const snapshot = response.data
        if (!snapshot || String(snapshot.shopDeptId) !== shop || snapshot.version == null || !Array.isArray(snapshot.rows)) throw Error('店铺快照不完整，请重新读取')
        const command = this.buildConfigCommand([], 'delete', snapshot)
        return this.$modal.confirm(`确认删除店铺 ${row.shopDeptName || shop} 的全部 ${snapshot.rows.length} 条配置？整批同时生效。`).then(() => {
          if (!scope.isCurrent(token)) return
          this.pendingConfigCommand = command; return this.sendConfigCommand(command, token)
        })
      }).catch(error => { if (scope.isCurrent(token) && error !== 'cancel' && error !== 'close' && !(error && error.notified)) this.configError = approvalMessage(error) })
        .finally(() => { if (scope.isCurrent(token)) this.deletingConfig = false })
    }
  }
}
