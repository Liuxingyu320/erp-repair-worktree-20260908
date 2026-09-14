// One record per editor instance: concurrent windows never overwrite one another.
export const MAX_DRAFT_BYTES = 1024 * 1024
const forbidden = new Set(['__proto__', 'prototype', 'constructor', 'domProps', 'on', 'nativeOn', 'scopedSlots', 'directives', 'attrs'])

export function parseDesignDraft(text, allowedTags) {
  if (typeof text !== 'string' || text.length > MAX_DRAFT_BYTES) throw new Error('草稿文件过大，最多支持 1 MB')
  const value = JSON.parse(text)
  let visited = 0
  function inspect(node, depth = 0) {
    if (++visited > 50000 || depth > 40) throw new Error('草稿结构过于复杂')
    if (!node || typeof node !== 'object') return
    Object.keys(node).forEach(key => {
      if (forbidden.has(key)) throw new Error('草稿包含不支持的属性')
      inspect(node[key], depth + 1)
    })
  }
  inspect(value)
  if (!value || value.format !== 'erp-form-design' || value.version !== 1 || !Array.isArray(value.fields)
    || !value.formConf || typeof value.formConf !== 'object' || Array.isArray(value.formConf)) throw new Error('请选择本系统导出的表单设计 JSON 草稿')
  for (const key of ['formRef', 'formModel', 'formRules']) {
    if (typeof value.formConf[key] !== 'string') throw new Error('表单字段名称格式无效')
  }
  const ids = new Set()
  let count = 0, maximum = 100
  function options(list) {
    if (!Array.isArray(list)) throw new Error('组件选项无效')
    list.forEach(option => {
      if (!option || typeof option !== 'object' || Array.isArray(option)) throw new Error('组件选项内容无效')
      if (option.children != null) options(option.children)
    })
  }
  function fields(list) {
    if (!Array.isArray(list)) throw new Error('草稿组件列表无效')
    list.forEach(field => {
      if (++count > 1000 || !field || !Number.isSafeInteger(field.formId) || field.formId <= 0 || ids.has(field.formId)) throw new Error('草稿组件编号无效或重复')
      ids.add(field.formId); maximum = Math.max(maximum, field.formId)
      if (field.layout === 'rowFormItem') {
        if (typeof field.componentName !== 'string') throw new Error('布局名称格式无效')
        fields(field.children)
      } else if (field.layout === 'colFormItem' && allowedTags.includes(field.tag)) {
        if (field.vModel != null) {
          if (typeof field.vModel !== 'string') throw new Error('组件字段名称格式无效')
        }
        if (['el-select', 'el-radio-group', 'el-checkbox-group', 'el-cascader'].includes(field.tag)) options(field.options)
        else if (field.options != null) options(field.options)
        if (field.regList != null && (!Array.isArray(field.regList) || field.regList.some(rule => !rule || typeof rule !== 'object' || Array.isArray(rule)))) throw new Error('组件校验规则无效')
        if (field.tag === 'el-cascader' && (!field.props || !field.props.props || typeof field.props.props !== 'object' || Array.isArray(field.props.props))) throw new Error('级联选择配置无效')
      } else throw new Error('草稿含有不支持的组件')
    })
  }
  fields(value.fields)
  // Rebuild exhausted internal identities; business field names and active selection stay intact.
  if (maximum > 1000000000) {
    maximum = 100
    const activeId = value.activeId
    function rekey(list) {
      list.forEach(field => {
        const previous = field.formId
        field.formId = ++maximum
        field.renderKey = field.formId
        if (previous === activeId) value.activeId = field.formId
        if (Array.isArray(field.children)) rekey(field.children)
      })
    }
    rekey(value.fields)
  }
  // Imported ids never choose the next generated id; recompute from the actual tree.
  return { ...value, idGlobal: maximum, componentCount: count }
}

export function designDraftPrefix(actor, department) {
  if (actor == null || String(actor).trim() === '') throw new Error('账号尚未就绪，请重新进入表单设计')
  return 'erp:form-design:v1:' + encodeURIComponent(String(actor)) + ':' + encodeURIComponent(String(department == null ? '' : department)) + ':'
}

export function readDesignDrafts(storage, prefix, allowedTags) {
  const records = [], invalid = []
  for (let i = 0; i < storage.length; i++) {
    const key = storage.key(i)
    if (!key || !key.startsWith(prefix)) continue
    try {
      const draft = parseDesignDraft(storage.getItem(key), allowedTags)
      records.push({ key, draft })
    } catch (error) { invalid.push(key) }
  }
  records.sort((a, b) => String(b.draft.savedAt || '').localeCompare(String(a.draft.savedAt || '')))
  return { records, invalid }
}

export function findDesignField(list, id) {
  for (const field of list) {
    if (field.formId === id) return field
    const found = Array.isArray(field.children) && findDesignField(field.children, id)
    if (found) return found
  }
  return null
}
