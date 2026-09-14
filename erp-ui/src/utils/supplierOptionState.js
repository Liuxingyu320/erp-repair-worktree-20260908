function mergeSelectedSupplier(options, form) {
  const result = (options || []).filter(row => !row._historical)
  const seen = new Set()
  const rows = result.filter(row => {
    const key = row.supplierId == null ? "name:" + row.supplierName : "id:" + row.supplierId
    if (seen.has(key)) return false
    seen.add(key)
    return true
  })
  if (form && form.supplierName && !rows.some(row => row.supplierName === form.supplierName)) {
    rows.unshift({ supplierId: "selected-" + form.supplierName, supplierName: form.supplierName,
      contactPhone: form.supplierPhone, _historical: true })
  }
  return rows
}

function invalidateSupplierOptions(vm) {
  vm.supplierRequestSequence += 1
  vm.supplierLoaded = false
  vm.supplierLoading = false
  vm._supplierLoadPromise = null
  vm.supplierOptions = mergeSelectedSupplier([], vm.form)
  vm.supplierLoadError = ""
}

function loadSupplierOptions(vm, fetch, readContext) {
  if (vm.supplierLoaded) return Promise.resolve(vm.supplierOptions)
  if (vm._supplierLoadPromise) return vm._supplierLoadPromise
  const sequence = ++vm.supplierRequestSequence
  const context = JSON.stringify(readContext())
  const current = () => sequence === vm.supplierRequestSequence && context === JSON.stringify(readContext())
  vm.supplierLoading = true
  vm.supplierLoadError = ""
  vm._supplierLoadPromise = fetch().then(response => {
    if (!current()) return []
    vm.supplierOptions = mergeSelectedSupplier(response.rows || response.data || [], vm.form)
    vm.supplierLoaded = true
    return vm.supplierOptions
  }).catch(() => {
    if (!current()) return []
    vm.supplierLoadError = "供应商加载失败，已保留当前值，请重试"
    vm.supplierLoaded = false
    vm.supplierOptions = mergeSelectedSupplier(vm.supplierOptions, vm.form)
    return vm.supplierOptions
  }).finally(() => {
    if (current()) { vm.supplierLoading = false; vm._supplierLoadPromise = null }
  })
  return vm._supplierLoadPromise
}

module.exports = { mergeSelectedSupplier, invalidateSupplierOptions, loadSupplierOptions }
