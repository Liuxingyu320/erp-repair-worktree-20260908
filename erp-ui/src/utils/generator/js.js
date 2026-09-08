import { exportDefault, titleCase } from '@/utils/index'
import { trigger } from './config'
import { serializeRegExpPattern, serializeScriptString, serializeScriptValue } from './regexp'
import { assertSafeIdentifier } from './security'

const units = {
  KB: '1024',
  MB: '1024 / 1024',
  GB: '1024 / 1024 / 1024'
}
let confGlobal
const inheritAttrs = {
  file: '',
  dialog: 'inheritAttrs: false,'
}


export function makeUpJs(conf, type) {
  confGlobal = conf = JSON.parse(JSON.stringify(conf))
  assertSafeIdentifier(conf.formRef, '表单引用名')
  assertSafeIdentifier(conf.formModel, '表单模型名')
  assertSafeIdentifier(conf.formRules, '表单规则名')
  const dataList = []
  const ruleList = []
  const optionsList = []
  const propsList = []
  const methodList = mixinMethod(type)
  const uploadVarList = []

  conf.fields.forEach(el => {
    buildAttributes(el, dataList, ruleList, optionsList, methodList, propsList, uploadVarList)
  })

  const script = buildexport(
    conf,
    type,
    dataList.join('\n'),
    ruleList.join('\n'),
    optionsList.join('\n'),
    uploadVarList.join('\n'),
    propsList.join('\n'),
    methodList.join('\n')
  )
  confGlobal = null
  return script
}

function buildAttributes(el, dataList, ruleList, optionsList, methodList, propsList, uploadVarList) {
  if (el.vModel !== undefined) assertSafeModelName(el.vModel)
  buildData(el, dataList)
  buildRules(el, ruleList)

  if (el.options && el.options.length) {
    buildOptions(el, optionsList)
    if (el.dataType === 'dynamic') {
      const model = `${el.vModel}Options`
      const options = titleCase(model)
      buildOptionMethod(`get${options}`, model, methodList)
    }
  }

  if (el.props && el.props.props) {
    buildProps(el, propsList)
  }

  if (el.action && el.tag === 'el-upload') {
    const model = assertSafeModelName(el.vModel)
    uploadVarList.push(
      `${serializeScriptString(`${model}Action`)}: ${serializeScriptString(el.action)},
      ${serializeScriptString(`${model}fileList`)}: [],`
    )
    methodList.push(buildBeforeUpload(el))
    if (!el['auto-upload']) {
      methodList.push(buildSubmitUpload(el))
    }
  }

  if (el.children) {
    el.children.forEach(el2 => {
      buildAttributes(el2, dataList, ruleList, optionsList, methodList, propsList, uploadVarList)
    })
  }
}

function assertSafeModelName(value) {
  return assertSafeIdentifier(value, '字段模型名')
}

function mixinMethod(type) {
  const list = []; const
    minxins = {
      file: confGlobal.formBtns ? {
        submitForm: `submitForm() {
        this.$refs[${serializeScriptString(confGlobal.formRef)}].validate(valid => {
          if(!valid) return
          // TODO 提交表单
        })
      },`,
        resetForm: `resetForm() {
        this.$refs[${serializeScriptString(confGlobal.formRef)}].resetFields()
      },`
      } : null,
      dialog: {
        onOpen: 'onOpen() {},',
        onClose: `onClose() {
        this.$refs[${serializeScriptString(confGlobal.formRef)}].resetFields()
      },`,
        close: `close() {
        this.$emit('update:visible', false)
      },`,
        handleConfirm: `handleConfirm() {
        this.$refs[${serializeScriptString(confGlobal.formRef)}].validate(valid => {
          if(!valid) return
          this.close()
        })
      },`
      }
    }

  const methods = minxins[type]
  if (methods) {
    Object.keys(methods).forEach(key => {
      list.push(methods[key])
    })
  }

  return list
}

function buildData(conf, dataList) {
  if (conf.vModel === undefined) return
  const model = assertSafeModelName(conf.vModel)
  const defaultValue = serializeScriptValue(conf.defaultValue)
  dataList.push(`${serializeScriptString(model)}: ${defaultValue},`)
}

function buildRules(conf, ruleList) {
  if (conf.vModel === undefined) return
  const rules = []
  if (trigger[conf.tag]) {
    if (conf.required) {
      const type = Array.isArray(conf.defaultValue) ? 'type: \'array\',' : ''
      let message = Array.isArray(conf.defaultValue) ? `请至少选择一个${conf.vModel}` : conf.placeholder
      if (message === undefined) message = `${conf.label}不能为空`
      rules.push(`{ required: true, ${type} message: ${serializeScriptString(message)}, trigger: ${serializeScriptString(trigger[conf.tag])} }`)
    }
    if (conf.regList && Array.isArray(conf.regList)) {
      conf.regList.forEach(item => {
        const pattern = serializeRegExpPattern(item && item.pattern)
        if (pattern) {
          rules.push(`{ pattern: ${pattern}, message: ${serializeScriptString(item.message == null ? '' : item.message)}, trigger: ${serializeScriptString(trigger[conf.tag])} }`)
        }
      })
    }
    ruleList.push(`${serializeScriptString(assertSafeModelName(conf.vModel))}: [${rules.join(',')}],`)
  }
}

function buildOptions(conf, optionsList) {
  if (conf.vModel === undefined) return
  if (conf.dataType === 'dynamic') { conf.options = [] }
  const str = `${serializeScriptString(`${assertSafeModelName(conf.vModel)}Options`)}: ${serializeScriptValue(conf.options)},`
  optionsList.push(str)
}

function buildProps(conf, propsList) {
  if (conf.dataType === 'dynamic') {
    conf.valueKey !== 'value' && (conf.props.props.value = conf.valueKey)
    conf.labelKey !== 'label' && (conf.props.props.label = conf.labelKey)
    conf.childrenKey !== 'children' && (conf.props.props.children = conf.childrenKey)
  }
  const str = `${serializeScriptString(`${assertSafeModelName(conf.vModel)}Props`)}: ${serializeScriptValue(conf.props.props)},`
  propsList.push(str)
}

function buildBeforeUpload(conf) {
  const model = assertSafeModelName(conf.vModel)
  const unitNum = units[conf.sizeUnit]; let rightSizeCode = ''; let acceptCode = ''; const
    returnList = []
  if (conf.fileSize) {
    const fileSize = Number(conf.fileSize)
    if (!unitNum || !Number.isFinite(fileSize) || fileSize <= 0) throw new TypeError('上传大小配置无效')
    rightSizeCode = `let isRightSize = file.size / ${unitNum} < ${fileSize}
    if(!isRightSize){
      this.$message.error(${serializeScriptString(`文件大小超过 ${fileSize}${conf.sizeUnit}`)})
    }`
    returnList.push('isRightSize')
  }
  if (conf.accept) {
    acceptCode = `let isAccept = new RegExp(${serializeScriptString(conf.accept)}).test(file.type)
    if(!isAccept){
      this.$message.error(${serializeScriptString(`应该选择${conf.accept}类型的文件`)})
    }`
    returnList.push('isAccept')
  }
  const str = `${serializeScriptString(`${model}BeforeUpload`)}(file) {
    ${rightSizeCode}
    ${acceptCode}
    return ${returnList.join('&&')}
  },`
  return returnList.length ? str : ''
}

function buildSubmitUpload(conf) {
  const model = assertSafeModelName(conf.vModel)
  const str = `submitUpload() {
    this.$refs[${serializeScriptString(model)}].submit()
  },`
  return str
}

function buildOptionMethod(methodName, model, methodList) {
  const str = `${serializeScriptString(methodName)}() {
    // TODO 发起请求获取数据
    this[${serializeScriptString(model)}]
  },`
  methodList.push(str)
}

function buildexport(conf, type, data, rules, selectOptions, uploadVar, props, methods) {
  const str = `${exportDefault}{
  ${inheritAttrs[type]}
  components: {},
  props: [],
  data () {
    return {
      ${serializeScriptString(conf.formModel)}: {
        ${data}
      },
      ${serializeScriptString(conf.formRules)}: {
        ${rules}
      },
      ${uploadVar}
      ${selectOptions}
      ${props}
    }
  },
  computed: {},
  watch: {},
  created () {},
  mounted () {},
  methods: {
    ${methods}
  }
}`
  return str
}
