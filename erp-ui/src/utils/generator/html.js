import { trigger } from './config'
import {
  assertSafeIdentifier,
  escapeTemplateAttribute,
  escapeTemplateText,
  serializeTemplateExpression
} from './security'

let confGlobal
let someSpanIsNot24

function staticAttr(name, value) {
  return `:${name}="${serializeTemplateExpression(value)}"`
}

function boundLiteralAttr(name, value) {
  return `:${name}="${serializeTemplateExpression(value)}"`
}

function boundIdentifierAttr(name, value, label) {
  return `:${name}="${escapeTemplateAttribute(assertSafeIdentifier(value, label))}"`
}

export function dialogWrapper(str) {
  return `<el-dialog v-bind="$attrs" v-on="$listeners" @open="onOpen" @close="onClose" title="Dialog Title" append-to-body>
    ${str}
    <div slot="footer">
      <el-button @click="close">取消</el-button>
      <el-button type="primary" @click="handleConfirm">确定</el-button>
    </div>
  </el-dialog>`
}

export function vueTemplate(str) {
  return `<template>
    <div>
      ${str}
    </div>
  </template>`
}

export function vueScript(str) {
  return `<script>
    ${str}
  </script>`
}

export function cssStyle(cssStr) {
  return `<style>
    ${cssStr}
  </style>`
}

function buildFormTemplate(conf, child, type) {
  let labelPosition = ''
  if (conf.labelPosition !== 'right') {
    labelPosition = staticAttr('label-position', conf.labelPosition)
  }
  const formRef = assertSafeIdentifier(conf.formRef, '表单引用名')
  const formModel = assertSafeIdentifier(conf.formModel, '表单模型名')
  const formRules = assertSafeIdentifier(conf.formRules, '表单规则名')
  const disabled = conf.disabled ? ':disabled="true"' : ''
  let str = `<el-form ${staticAttr('ref', formRef)} ${boundIdentifierAttr('model', formModel, '表单模型名')} ${boundIdentifierAttr('rules', formRules, '表单规则名')} ${staticAttr('size', conf.size)} ${disabled} ${staticAttr('label-width', `${conf.labelWidth}px`)} ${labelPosition}>
      ${child}
      ${buildFromBtns(conf, type)}
    </el-form>`
  if (someSpanIsNot24) {
    str = `<el-row ${boundLiteralAttr('gutter', conf.gutter)}>
        ${str}
      </el-row>`
  }
  return str
}

function buildFromBtns(conf, type) {
  let str = ''
  if (conf.formBtns && type === 'file') {
    str = `<el-form-item size="large">
          <el-button type="primary" @click="submitForm">提交</el-button>
          <el-button @click="resetForm">重置</el-button>
        </el-form-item>`
    if (someSpanIsNot24) {
      str = `<el-col :span="24">
          ${str}
        </el-col>`
    }
  }
  return str
}

// span不为24的用el-col包裹
function colWrapper(element, str) {
  if (someSpanIsNot24 || element.span !== 24) {
    return `<el-col ${boundLiteralAttr('span', element.span)}>
      ${str}
    </el-col>`
  }
  return str
}

const layouts = {
  colFormItem(element) {
    let labelWidth = ''
    if (element.labelWidth && element.labelWidth !== confGlobal.labelWidth) {
      labelWidth = staticAttr('label-width', `${element.labelWidth}px`)
    }
    const required = !trigger[element.tag] && element.required ? 'required' : ''
    const tagDom = tags[element.tag] ? tags[element.tag](element) : null
    const prop = element.vModel === undefined
      ? ''
      : staticAttr('prop', assertSafeIdentifier(element.vModel, '字段模型名'))
    let str = `<el-form-item ${labelWidth} ${staticAttr('label', element.label)} ${prop} ${required}>
        ${tagDom}
      </el-form-item>`
    str = colWrapper(element, str)
    return str
  },
  rowFormItem(element) {
    const type = element.type === 'default' ? '' : staticAttr('type', element.type)
    const justify = element.type === 'default' ? '' : staticAttr('justify', element.justify)
    const align = element.type === 'default' ? '' : staticAttr('align', element.align)
    const gutter = element.gutter ? boundLiteralAttr('gutter', element.gutter) : ''
    const children = element.children.map(el => layouts[el.layout](el))
    let str = `<el-row ${type} ${justify} ${align} ${gutter}>
      ${children.join('\n')}
    </el-row>`
    str = colWrapper(element, str)
    return str
  }
}

const tags = {
  'el-button': el => {
    const disabled = el.disabled ? ':disabled="true"' : ''
    const type = el.type ? staticAttr('type', el.type) : ''
    const icon = el.icon ? staticAttr('icon', el.icon) : ''
    const size = el.size ? staticAttr('size', el.size) : ''
    let child = buildElButtonChild(el)

    if (child) child = `\n${child}\n` // 换行
    return `<${el.tag} ${type} ${icon} ${size} ${disabled}>${child}</${el.tag}>`
  },
  'el-input': el => {
    const {
      disabled, vModel, clearable, placeholder, width
    } = attrBuilder(el)
    const maxlength = el.maxlength ? boundLiteralAttr('maxlength', el.maxlength) : ''
    const showWordLimit = el['show-word-limit'] ? 'show-word-limit' : ''
    const readonly = el.readonly ? 'readonly' : ''
    const prefixIcon = el['prefix-icon'] ? staticAttr('prefix-icon', el['prefix-icon']) : ''
    const suffixIcon = el['suffix-icon'] ? staticAttr('suffix-icon', el['suffix-icon']) : ''
    const showPassword = el['show-password'] ? 'show-password' : ''
    const type = el.type ? staticAttr('type', el.type) : ''
    const autosize = el.autosize && el.autosize.minRows
      ? boundLiteralAttr('autosize', { minRows: el.autosize.minRows, maxRows: el.autosize.maxRows })
      : ''
    let child = buildElInputChild(el)

    if (child) child = `\n${child}\n` // 换行
    return `<${el.tag} ${vModel} ${type} ${placeholder} ${maxlength} ${showWordLimit} ${readonly} ${disabled} ${clearable} ${prefixIcon} ${suffixIcon} ${showPassword} ${autosize} ${width}>${child}</${el.tag}>`
  },
  'el-input-number': el => {
    const { disabled, vModel, placeholder } = attrBuilder(el)
    const controlsPosition = el['controls-position'] ? staticAttr('controls-position', el['controls-position']) : ''
    const min = el.min ? boundLiteralAttr('min', el.min) : ''
    const max = el.max ? boundLiteralAttr('max', el.max) : ''
    const step = el.step ? boundLiteralAttr('step', el.step) : ''
    const stepStrictly = el['step-strictly'] ? 'step-strictly' : ''
    const precision = el.precision ? boundLiteralAttr('precision', el.precision) : ''

    return `<${el.tag} ${vModel} ${placeholder} ${step} ${stepStrictly} ${precision} ${controlsPosition} ${min} ${max} ${disabled}></${el.tag}>`
  },
  'el-select': el => {
    const {
      disabled, vModel, clearable, placeholder, width
    } = attrBuilder(el)
    const filterable = el.filterable ? 'filterable' : ''
    const multiple = el.multiple ? 'multiple' : ''
    let child = buildElSelectChild(el)

    if (child) child = `\n${child}\n` // 换行
    return `<${el.tag} ${vModel} ${placeholder} ${disabled} ${multiple} ${filterable} ${clearable} ${width}>${child}</${el.tag}>`
  },
  'el-radio-group': el => {
    const { disabled, vModel } = attrBuilder(el)
    const size = staticAttr('size', el.size)
    let child = buildElRadioGroupChild(el)

    if (child) child = `\n${child}\n` // 换行
    return `<${el.tag} ${vModel} ${size} ${disabled}>${child}</${el.tag}>`
  },
  'el-checkbox-group': el => {
    const { disabled, vModel } = attrBuilder(el)
    const size = staticAttr('size', el.size)
    const min = el.min ? boundLiteralAttr('min', el.min) : ''
    const max = el.max ? boundLiteralAttr('max', el.max) : ''
    let child = buildElCheckboxGroupChild(el)

    if (child) child = `\n${child}\n` // 换行
    return `<${el.tag} ${vModel} ${min} ${max} ${size} ${disabled}>${child}</${el.tag}>`
  },
  'el-switch': el => {
    const { disabled, vModel } = attrBuilder(el)
    const activeText = el['active-text'] ? staticAttr('active-text', el['active-text']) : ''
    const inactiveText = el['inactive-text'] ? staticAttr('inactive-text', el['inactive-text']) : ''
    const activeColor = el['active-color'] ? staticAttr('active-color', el['active-color']) : ''
    const inactiveColor = el['inactive-color'] ? staticAttr('inactive-color', el['inactive-color']) : ''
    const activeValue = el['active-value'] !== true ? boundLiteralAttr('active-value', el['active-value']) : ''
    const inactiveValue = el['inactive-value'] !== false ? boundLiteralAttr('inactive-value', el['inactive-value']) : ''

    return `<${el.tag} ${vModel} ${activeText} ${inactiveText} ${activeColor} ${inactiveColor} ${activeValue} ${inactiveValue} ${disabled}></${el.tag}>`
  },
  'el-cascader': el => {
    const {
      disabled, vModel, clearable, placeholder, width
    } = attrBuilder(el)
    const model = assertSafeIdentifier(el.vModel, '字段模型名')
    const options = el.options ? boundIdentifierAttr('options', `${model}Options`, '字段选项名') : ''
    const props = el.props ? boundIdentifierAttr('props', `${model}Props`, '字段属性名') : ''
    const showAllLevels = el['show-all-levels'] ? '' : ':show-all-levels="false"'
    const filterable = el.filterable ? 'filterable' : ''
    const separator = el.separator === '/' ? '' : staticAttr('separator', el.separator)

    return `<${el.tag} ${vModel} ${options} ${props} ${width} ${showAllLevels} ${placeholder} ${separator} ${filterable} ${clearable} ${disabled}></${el.tag}>`
  },
  'el-slider': el => {
    const { disabled, vModel } = attrBuilder(el)
    const min = el.min ? boundLiteralAttr('min', el.min) : ''
    const max = el.max ? boundLiteralAttr('max', el.max) : ''
    const step = el.step ? boundLiteralAttr('step', el.step) : ''
    const range = el.range ? 'range' : ''
    const showStops = el['show-stops'] ? ':show-stops="true"' : ''

    return `<${el.tag} ${min} ${max} ${step} ${vModel} ${range} ${showStops} ${disabled}></${el.tag}>`
  },
  'el-time-picker': el => {
    const {
      disabled, vModel, clearable, placeholder, width
    } = attrBuilder(el)
    const startPlaceholder = el['start-placeholder'] ? staticAttr('start-placeholder', el['start-placeholder']) : ''
    const endPlaceholder = el['end-placeholder'] ? staticAttr('end-placeholder', el['end-placeholder']) : ''
    const rangeSeparator = el['range-separator'] ? staticAttr('range-separator', el['range-separator']) : ''
    const isRange = el['is-range'] ? 'is-range' : ''
    const format = el.format ? staticAttr('format', el.format) : ''
    const valueFormat = el['value-format'] ? staticAttr('value-format', el['value-format']) : ''
    const pickerOptions = el['picker-options'] ? boundLiteralAttr('picker-options', el['picker-options']) : ''

    return `<${el.tag} ${vModel} ${isRange} ${format} ${valueFormat} ${pickerOptions} ${width} ${placeholder} ${startPlaceholder} ${endPlaceholder} ${rangeSeparator} ${clearable} ${disabled}></${el.tag}>`
  },
  'el-date-picker': el => {
    const {
      disabled, vModel, clearable, placeholder, width
    } = attrBuilder(el)
    const startPlaceholder = el['start-placeholder'] ? staticAttr('start-placeholder', el['start-placeholder']) : ''
    const endPlaceholder = el['end-placeholder'] ? staticAttr('end-placeholder', el['end-placeholder']) : ''
    const rangeSeparator = el['range-separator'] ? staticAttr('range-separator', el['range-separator']) : ''
    const format = el.format ? staticAttr('format', el.format) : ''
    const valueFormat = el['value-format'] ? staticAttr('value-format', el['value-format']) : ''
    const type = el.type === 'date' ? '' : staticAttr('type', el.type)
    const readonly = el.readonly ? 'readonly' : ''

    return `<${el.tag} ${type} ${vModel} ${format} ${valueFormat} ${width} ${placeholder} ${startPlaceholder} ${endPlaceholder} ${rangeSeparator} ${clearable} ${readonly} ${disabled}></${el.tag}>`
  },
  'el-rate': el => {
    const { disabled, vModel } = attrBuilder(el)
    const max = el.max ? boundLiteralAttr('max', el.max) : ''
    const allowHalf = el['allow-half'] ? 'allow-half' : ''
    const showText = el['show-text'] ? 'show-text' : ''
    const showScore = el['show-score'] ? 'show-score' : ''

    return `<${el.tag} ${vModel} ${allowHalf} ${showText} ${showScore} ${disabled}></${el.tag}>`
  },
  'el-color-picker': el => {
    const { disabled, vModel } = attrBuilder(el)
    const size = staticAttr('size', el.size)
    const showAlpha = el['show-alpha'] ? 'show-alpha' : ''
    const colorFormat = el['color-format'] ? staticAttr('color-format', el['color-format']) : ''

    return `<${el.tag} ${vModel} ${size} ${showAlpha} ${colorFormat} ${disabled}></${el.tag}>`
  },
  'el-upload': el => {
    const model = assertSafeIdentifier(el.vModel, '字段模型名')
    const disabled = el.disabled ? ':disabled=\'true\'' : ''
    const action = el.action ? boundIdentifierAttr('action', `${model}Action`, '上传地址名') : ''
    const multiple = el.multiple ? 'multiple' : ''
    const listType = el['list-type'] !== 'text' ? staticAttr('list-type', el['list-type']) : ''
    const accept = el.accept ? staticAttr('accept', el.accept) : ''
    const name = el.name !== 'file' ? staticAttr('name', el.name) : ''
    const autoUpload = el['auto-upload'] === false ? ':auto-upload="false"' : ''
    const beforeUpload = boundIdentifierAttr('before-upload', `${model}BeforeUpload`, '上传校验方法名')
    const fileList = boundIdentifierAttr('file-list', `${model}fileList`, '上传列表名')
    const ref = staticAttr('ref', model)
    let child = buildElUploadChild(el)

    if (child) child = `\n${child}\n` // 换行
    return `<${el.tag} ${ref} ${fileList} ${action} ${autoUpload} ${multiple} ${beforeUpload} ${listType} ${accept} ${name} ${disabled}>${child}</${el.tag}>`
  }
}

function attrBuilder(el) {
  const formModel = assertSafeIdentifier(confGlobal.formModel, '表单模型名')
  const model = assertSafeIdentifier(el.vModel, '字段模型名')
  return {
    vModel: `v-model="${escapeTemplateAttribute(`${formModel}.${model}`)}"`,
    clearable: el.clearable ? 'clearable' : '',
    placeholder: el.placeholder ? staticAttr('placeholder', el.placeholder) : '',
    width: el.style && el.style.width ? ':style="{width: \'100%\'}"' : '',
    disabled: el.disabled ? ':disabled=\'true\'' : ''
  }
}

// el-buttin 子级
function buildElButtonChild(conf) {
  const children = []
  if (conf.default) {
    children.push(escapeTemplateText(conf.default))
  }
  return children.join('\n')
}

// el-input innerHTML
function buildElInputChild(conf) {
  const children = []
  if (conf.prepend) {
    children.push(`<template slot="prepend">${escapeTemplateText(conf.prepend)}</template>`)
  }
  if (conf.append) {
    children.push(`<template slot="append">${escapeTemplateText(conf.append)}</template>`)
  }
  return children.join('\n')
}

function buildElSelectChild(conf) {
  const children = []
  if (conf.options && conf.options.length) {
    const model = assertSafeIdentifier(conf.vModel, '字段模型名')
    children.push(`<el-option v-for="(item, index) in ${escapeTemplateAttribute(`${model}Options`)}" :key="index" :label="item.label" :value="item.value" :disabled="item.disabled"></el-option>`)
  }
  return children.join('\n')
}

function buildElRadioGroupChild(conf) {
  const children = []
  if (conf.options && conf.options.length) {
    const model = assertSafeIdentifier(conf.vModel, '字段模型名')
    const tag = conf.optionType === 'button' ? 'el-radio-button' : 'el-radio'
    const border = conf.border ? 'border' : ''
    children.push(`<${tag} v-for="(item, index) in ${escapeTemplateAttribute(`${model}Options`)}" :key="index" :label="item.value" :disabled="item.disabled" ${border}>{{item.label}}</${tag}>`)
  }
  return children.join('\n')
}

function buildElCheckboxGroupChild(conf) {
  const children = []
  if (conf.options && conf.options.length) {
    const model = assertSafeIdentifier(conf.vModel, '字段模型名')
    const tag = conf.optionType === 'button' ? 'el-checkbox-button' : 'el-checkbox'
    const border = conf.border ? 'border' : ''
    children.push(`<${tag} v-for="(item, index) in ${escapeTemplateAttribute(`${model}Options`)}" :key="index" :label="item.value" :disabled="item.disabled" ${border}>{{item.label}}</${tag}>`)
  }
  return children.join('\n')
}

function buildElUploadChild(conf) {
  const list = []
  if (conf['list-type'] === 'picture-card') list.push('<i class="el-icon-plus"></i>')
  else list.push(`<el-button size="small" type="primary" icon="el-icon-upload">${escapeTemplateText(conf.buttonText)}</el-button>`)
  if (conf.showTip) {
    list.push(`<div slot="tip" class="el-upload__tip">${escapeTemplateText(`只能上传不超过 ${conf.fileSize}${conf.sizeUnit} 的${conf.accept}文件`)}</div>`)
  }
  return list.join('\n')
}

export function makeUpHtml(conf, type) {
  const htmlList = []
  assertSafeIdentifier(conf.formRef, '表单引用名')
  assertSafeIdentifier(conf.formModel, '表单模型名')
  assertSafeIdentifier(conf.formRules, '表单规则名')
  confGlobal = conf
  someSpanIsNot24 = conf.fields.some(item => item.span !== 24)
  conf.fields.forEach(el => {
    htmlList.push(layouts[el.layout](el))
  })
  const htmlStr = htmlList.join('\n')

  let temp = buildFormTemplate(conf, htmlStr, type)
  if (type === 'dialog') {
    temp = dialogWrapper(temp)
  }
  confGlobal = null
  return temp
}
