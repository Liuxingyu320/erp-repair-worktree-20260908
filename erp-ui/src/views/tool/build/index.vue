<template>
  <div class="container">
    <div class="left-board">
      <div class="logo-wrapper">
        <div class="logo">
          <img :src="logo" alt="表单生成器标志"> 表单生成器
        </div>
      </div>
      <el-scrollbar class="left-scrollbar">
        <div class="components-list">
          <div class="components-title">
            <svg-icon icon-class="component" />输入型组件
          </div>
          <draggable
            class="components-draggable"
            :list="inputComponents"
            :group="{ name: 'componentsGroup', pull: 'clone', put: false }"
            :clone="cloneComponent"
            draggable=".components-item"
            :sort="false"
            @end="onEnd"
          >
            <div
              v-for="(element, index) in inputComponents" :key="index" class="components-item"
              @click="addComponent(element)"
            >
              <div class="components-body">
                <svg-icon :icon-class="element.tagIcon" />
                {{ element.label }}
              </div>
            </div>
          </draggable>
          <div class="components-title">
            <svg-icon icon-class="component" />选择型组件
          </div>
          <draggable
            class="components-draggable"
            :list="selectComponents"
            :group="{ name: 'componentsGroup', pull: 'clone', put: false }"
            :clone="cloneComponent"
            draggable=".components-item"
            :sort="false"
            @end="onEnd"
          >
            <div
              v-for="(element, index) in selectComponents"
              :key="index"
              class="components-item"
              @click="addComponent(element)"
            >
              <div class="components-body">
                <svg-icon :icon-class="element.tagIcon" />
                {{ element.label }}
              </div>
            </div>
          </draggable>
          <div class="components-title">
            <svg-icon icon-class="component" /> 布局型组件
          </div>
          <draggable
            class="components-draggable" :list="layoutComponents"
            :group="{ name: 'componentsGroup', pull: 'clone', put: false }" :clone="cloneComponent"
            draggable=".components-item" :sort="false" @end="onEnd"
          >
            <div
              v-for="(element, index) in layoutComponents" :key="index" class="components-item"
              @click="addComponent(element)"
            >
              <div class="components-body">
                <svg-icon :icon-class="element.tagIcon" />
                {{ element.label }}
              </div>
            </div>
          </draggable>
        </div>
      </el-scrollbar>
    </div>

    <div class="center-board">
      <div class="design-draft-bar">
        <span role="status" :class="{ 'draft-error': draftError }">{{ draftError || draftStatus }}</span>
        <div>
          <el-button size="mini" @click="persistDesignDraft">保存草稿</el-button>
          <el-button size="mini" @click="openDraftRecovery">恢复草稿</el-button>
          <el-button size="mini" @click="exportDesignDraft">导出草稿</el-button>
          <el-button size="mini" @click="$refs.designDraftFile.click()">导入草稿</el-button>
          <input ref="designDraftFile" type="file" accept=".json,application/json" hidden @change="importDesignDraft">
        </div>
      </div>
      <div class="action-bar">
        <el-button icon="el-icon-download" type="text" @click="download">
          导出vue文件
        </el-button>
        <el-button class="copy-btn-main" icon="el-icon-document-copy" type="text" @click="copy">
          复制代码
        </el-button>
        <el-button class="delete-btn" icon="el-icon-delete" type="text" @click="empty">
          清空
        </el-button>
      </div>
      <el-scrollbar class="center-scrollbar">
        <el-row class="center-board-row" :gutter="formConf.gutter">
          <el-form
            :size="formConf.size"
            :label-position="formConf.labelPosition"
            :disabled="formConf.disabled"
            :label-width="formConf.labelWidth + 'px'"
          >
            <draggable class="drawing-board" :list="drawingList" :animation="340" group="componentsGroup">
              <draggable-item
                v-for="(element, index) in drawingList"
                :key="element.renderKey"
                :drawing-list="drawingList"
                :element="element"
                :index="index"
                :active-id="activeId"
                :form-conf="formConf"
                @activeItem="activeFormItem"
                @copyItem="drawingItemCopy"
                @deleteItem="drawingItemDelete"
              />
            </draggable>
            <div v-show="!drawingList.length" class="empty-info">
              从左侧拖入或点选组件进行表单设计
            </div>
          </el-form>
        </el-row>
      </el-scrollbar>
    </div>

    <right-panel
      :active-data="activeData"
      :form-conf="formConf"
      :show-field="!!drawingList.length"
      @tag-change="tagChange"
    />

    <code-type-dialog
      :visible.sync="dialogVisible"
      title="选择生成类型"
      :show-file-name="showFileName"
      @confirm="generate"
    />
    <el-dialog title="恢复表单设计草稿" :visible.sync="draftRecoveryOpen" width="560px" append-to-body>
      <p>选择要恢复的草稿；当前未保存内容将被替换。</p>
      <el-select v-model="selectedDraftKey" placeholder="选择保存时间" style="width: 100%">
        <el-option v-for="record in savedDrafts" :key="record.key" :value="record.key" :label="draftRecordLabel(record)" />
      </el-select>
      <div slot="footer"><el-button @click="draftRecoveryOpen = false">取消</el-button><el-button type="primary" :disabled="!selectedDraftKey" @click="restoreSelectedDraft">恢复</el-button></div>
    </el-dialog>
    <input id="copyNode" type="hidden">
  </div>
</template>

<script>
import draggable from 'vuedraggable'
import beautifier from 'js-beautify'
import ClipboardJS from 'clipboard'
import render from '@/utils/generator/render'
import RightPanel from './RightPanel'
import { inputComponents, selectComponents, layoutComponents, formConf } from '@/utils/generator/config'
import { beautifierConf, titleCase } from '@/utils/index'
import { makeUpHtml, vueTemplate, vueScript, cssStyle } from '@/utils/generator/html'
import { makeUpJs } from '@/utils/generator/js'
import { makeUpCss } from '@/utils/generator/css'
import { drawingDefaultValue, initDrawingDefaultValue } from '@/utils/generator/drawingDefault'
import { MAX_DRAFT_BYTES, parseDesignDraft, designDraftPrefix, readDesignDrafts, findDesignField } from '@/utils/generator/draft'
import { getSelectedDeptId } from '@/utils/shopContext'
import logo from '@/assets/logo/logo.png'
import CodeTypeDialog from './CodeTypeDialog'
import DraggableItem from './DraggableItem'

let oldActiveId
let tempActiveData
let clipboard = null

export default {
  components: {
    draggable,
    render,
    RightPanel,
    CodeTypeDialog,
    DraggableItem
  },
  data() {
    return {
      logo,
      idGlobal: 100,
      formConf: JSON.parse(JSON.stringify(formConf)),
      inputComponents,
      selectComponents,
      layoutComponents,
      labelWidth: 100,
      drawingList: JSON.parse(JSON.stringify(drawingDefaultValue)),
      drawingData: {},
      activeId: drawingDefaultValue[0].formId,
      drawerVisible: false,
      formData: {},
      dialogVisible: false,
      generateConf: null,
      showFileName: false,
      activeData: {},
      draftScope: '', draftKey: '', draftReady: false, draftApplying: false,
      draftDirty: false, draftRevision: 0, draftTimer: null, draftContentBaseline: '', retiredDesignDrafts: {}, draftPendingRecoveryError: false,
      draftError: '', draftStatus: '设计修改会自动保存到本机',
      savedDrafts: [], draftRecoveryOpen: false, selectedDraftKey: ''
    }
  },
  beforeCreate() {
    initDrawingDefaultValue()
  },
  created() {
    this.initializeDesignDraft()
    // 防止 firefox 下 拖拽 会新打卡一个选项卡
    document.body.ondrop = event => {
      event.preventDefault()
      event.stopPropagation()
    }
  },
  activated() { if (this.currentDraftScope() !== this.draftScope) this.initializeDesignDraft() },
  deactivated() { this.persistDesignDraft() },
  watch: {
    '$store.getters.id'() { this.initializeDesignDraft() },
    drawingList: { deep: true, handler() { this.scheduleDesignDraft() } },
    formConf: { deep: true, handler() { this.scheduleDesignDraft() } },
    'activeData.label': function (val, oldVal) {
      if (this.draftApplying) return
      if (
        this.activeData.placeholder === undefined
        || !this.activeData.tag
        || oldActiveId !== this.activeId
      ) {
        return
      }
      this.activeData.placeholder = this.activeData.placeholder.replace(oldVal, '') + val
    },
    activeId: {
      handler(val) {
        oldActiveId = val
      },
      immediate: true
    }
  },
  mounted() {
    window.addEventListener('beforeunload', this.beforeDraftUnload)
    clipboard = new ClipboardJS('#copyNode', {
      text: trigger => {
        const codeStr = this.generateCode()
        this.$notify({
          title: '成功',
          message: '代码已复制到剪切板，可粘贴。',
          type: 'success'
        })
        return codeStr
      }
    })
    clipboard.on('error', e => {
      this.$message.error('代码复制失败')
    })
  },
  beforeDestroy() {
    this.flushPreviousDesignDraft()
    this.persistDesignDraft()
    clearTimeout(this.draftTimer)
    window.removeEventListener('beforeunload', this.beforeDraftUnload)
    if (clipboard) clipboard.destroy()
  },
  methods: {
    currentDraftScope() {
      try { return designDraftPrefix(this.$store && this.$store.getters.id, getSelectedDeptId()) } catch (error) { return '' }
    },
    designTags() { return [...this.inputComponents, ...this.selectComponents, ...this.layoutComponents].map(item => item.tag).filter(Boolean) },
    designContentSignature() { return JSON.stringify([this.drawingList, this.formConf, this.activeId]) },
    flushPreviousDesignDraft() {
      if (!this.draftReady || !this.draftKey || !this.draftScope || this.draftApplying) return
      if (!this.draftDirty && this.designContentSignature() === this.draftContentBaseline) return
      const text = this.designDraftText()
      try {
        parseDesignDraft(text, this.designTags())
        window.localStorage.setItem(this.draftKey, text)
        this.$delete(this.retiredDesignDrafts, this.draftScope)
        this.draftDirty = false
        this.draftContentBaseline = this.designContentSignature()
      } catch (error) { this.$set(this.retiredDesignDrafts, this.draftScope, text) }
    },
    initializeDesignDraft() {
      this.flushPreviousDesignDraft()
      clearTimeout(this.draftTimer)
      this.draftReady = false
      this.draftScope = this.currentDraftScope()
      this.draftKey = ''
      this.draftRevision += 1
      this.draftDirty = false
      this.draftError = ''
      this.draftPendingRecoveryError = false
      this.savedDrafts = []
      this.draftRecoveryOpen = false
      this.selectedDraftKey = ''
      initDrawingDefaultValue()
      this.applyDesignDraft({ fields: JSON.parse(JSON.stringify(drawingDefaultValue)), formConf: JSON.parse(JSON.stringify(formConf)), idGlobal: 100 })
      try {
        if (!this.draftScope) throw new Error('账号尚未就绪，暂不能保存草稿')
        const id = new Uint32Array(4)
        window.crypto.getRandomValues(id)
        this.draftKey = this.draftScope + Array.from(id, value => value.toString(16).padStart(8, '0')).join('')
        const { records, invalid } = readDesignDrafts(window.localStorage, this.draftScope, this.designTags())
        this.savedDrafts = records
        const pending = this.retiredDesignDrafts[this.draftScope]
        if (pending || records.length) {
          this.applyDesignDraft(pending ? parseDesignDraft(pending, this.designTags()) : records[0].draft)
          this.draftStatus = '已恢复最近草稿；修改会自动保存到本机'
        } else this.draftStatus = '设计修改会自动保存到本机'
        if (invalid.length) this.draftError = '部分旧草稿格式损坏，已保留原记录；当前设计仍可另存或导出'
      } catch (error) { this.draftError = '本机草稿不可用，请导出保留设计：' + error.message }
      if (this.retiredDesignDrafts[this.draftScope]) {
        try { this.applyDesignDraft(parseDesignDraft(this.retiredDesignDrafts[this.draftScope], this.designTags())) }
        catch (error) { this.draftPendingRecoveryError = true; this.draftError = '上次未保存的设计暂不能恢复，请先导出草稿保留原内容：' + error.message }
      }
      this.draftContentBaseline = this.designContentSignature()
      this.$nextTick(() => {
        this.draftReady = true
        if (this.retiredDesignDrafts[this.draftScope] && !this.draftPendingRecoveryError) { this.draftDirty = true; this.persistDesignDraft() }
      })
    },
    applyDesignDraft(draft) {
      this.draftApplying = true
      this.drawingList = JSON.parse(JSON.stringify(draft.fields))
      this.formConf = JSON.parse(JSON.stringify(draft.formConf))
      this.idGlobal = draft.idGlobal || 100
      this.activeData = findDesignField(this.drawingList, draft.activeId) || this.drawingList[0] || {}
      this.activeId = this.activeData.formId || null
      this.$nextTick(() => { this.draftApplying = false })
    },
    designDraftText() {
      return JSON.stringify({ format: 'erp-form-design', version: 1, savedAt: new Date().toISOString(), fields: this.drawingList, formConf: this.formConf, activeId: this.activeId })
    },
    scheduleDesignDraft() {
      if (!this.draftReady || this.draftApplying) return
      this.draftRevision += 1
      this.draftDirty = true
      this.draftStatus = '正在保存草稿…'
      clearTimeout(this.draftTimer)
      this.draftTimer = setTimeout(() => this.persistDesignDraft(), 200)
    },
    persistDesignDraft() {
      clearTimeout(this.draftTimer)
      if (!this.draftReady || this.draftApplying || this.draftPendingRecoveryError) return false
      if (!this.draftScope || this.draftScope !== this.currentDraftScope() || !this.draftKey) {
        this.draftError = '账号或组织已变化，请重新进入表单设计'
        return false
      }
      try {
        const text = this.designDraftText()
        parseDesignDraft(text, this.designTags())
        window.localStorage.setItem(this.draftKey, text)
        this.draftDirty = false
        this.draftContentBaseline = this.designContentSignature()
        this.$delete(this.retiredDesignDrafts, this.draftScope)
        this.draftError = ''
        this.draftStatus = '草稿已保存到本机'
        return true
      } catch (error) {
        this.draftDirty = true
        this.draftError = '草稿未保存，请导出保留设计：' + error.message
        return false
      }
    },
    beforeDraftUnload(event) {
      if (!this.draftDirty && this.designContentSignature() === this.draftContentBaseline && !Object.keys(this.retiredDesignDrafts).length) return
      this.draftDirty = true
      if (this.currentDraftScope() === this.draftScope) this.persistDesignDraft()
      else this.flushPreviousDesignDraft()
      if (this.draftDirty || Object.keys(this.retiredDesignDrafts).length) { event.preventDefault(); event.returnValue = '' }
    },
    draftRecordLabel(record) {
      return (record.draft.savedAt || '未知时间').replace('T', ' ').replace('Z', ' UTC') + ' · ' + record.draft.componentCount + ' 个组件'
    },
    openDraftRecovery() {
      if (this.currentDraftScope() !== this.draftScope) return
      try {
        this.savedDrafts = readDesignDrafts(window.localStorage, this.draftScope, this.designTags()).records
        this.selectedDraftKey = this.savedDrafts.length ? this.savedDrafts[0].key : ''
        this.draftRecoveryOpen = true
      } catch (error) { this.draftError = '读取草稿失败：' + error.message }
    },
    async restoreSelectedDraft() {
      const record = this.savedDrafts.find(item => item.key === this.selectedDraftKey)
      if (!record || this.currentDraftScope() !== this.draftScope) return
      this.draftPendingRecoveryError = false
      this.applyDesignDraft(record.draft)
      this.draftRecoveryOpen = false
      await this.$nextTick()
      this.draftDirty = true
      this.persistDesignDraft()
    },
    exportDesignDraft() {
      if (!this.draftScope || this.currentDraftScope() !== this.draftScope) return
      try {
        const text = this.retiredDesignDrafts[this.draftScope] || this.designDraftText()
        this.$download.saveAs(new Blob([text], { type: 'application/json;charset=utf-8' }), '表单设计草稿.json')
      } catch (error) { this.draftError = '草稿导出失败：' + error.message }
    },
    async importDesignDraft(event) {
      const file = event.target.files && event.target.files[0]
      event.target.value = ''
      if (!file) return
      const scope = this.draftScope, revision = this.draftRevision, signature = this.designContentSignature()
      try {
        if (file.size > MAX_DRAFT_BYTES) throw new Error('草稿文件过大，最多支持 1 MB')
        const draft = parseDesignDraft(await file.text(), this.designTags())
        if (this.currentDraftScope() !== scope || scope !== this.draftScope || revision !== this.draftRevision || signature !== this.designContentSignature()) throw new Error('读取文件期间设计或账号已变化，请重新导入')
        await this.$confirm('导入草稿会替换当前设计，是否继续？', '导入草稿', { type: 'warning' })
        if (this.currentDraftScope() !== scope || scope !== this.draftScope || revision !== this.draftRevision || signature !== this.designContentSignature()) throw new Error('确认期间设计或账号已变化，请重新导入')
        this.draftPendingRecoveryError = false
        this.applyDesignDraft(draft)
        await this.$nextTick()
        this.draftDirty = true
        this.persistDesignDraft()
      } catch (error) { if (error !== 'cancel' && error !== 'close') this.draftError = '草稿导入未完成：' + error.message }
    },
    activeFormItem(element) {
      this.activeData = element
      this.activeId = element.formId
    },
    onEnd(obj, a) {
      if (obj.from !== obj.to) {
        this.activeData = tempActiveData
        this.activeId = this.idGlobal
      }
    },
    addComponent(item) {
      const clone = this.cloneComponent(item)
      this.drawingList.push(clone)
      this.activeFormItem(clone)
    },
    cloneComponent(origin) {
      const clone = JSON.parse(JSON.stringify(origin))
      clone.formId = ++this.idGlobal
      clone.span = this.formConf.span
      clone.renderKey = +new Date() // 改变renderKey后可以实现强制更新组件
      if (!clone.layout) clone.layout = 'colFormItem'
      if (clone.layout === 'colFormItem') {
        clone.vModel = `field${this.idGlobal}`
        clone.placeholder !== undefined && (clone.placeholder += clone.label)
        tempActiveData = clone
      } else if (clone.layout === 'rowFormItem') {
        delete clone.label
        clone.componentName = `row${this.idGlobal}`
        clone.gutter = this.formConf.gutter
        tempActiveData = clone
      }
      return tempActiveData
    },
    AssembleFormData() {
      this.formData = {
        fields: JSON.parse(JSON.stringify(this.drawingList)),
        ...this.formConf
      }
    },
    generate(data) {
      const func = this[`exec${titleCase(this.operationType)}`]
      this.generateConf = data
      func && func(data)
    },
    execRun(data) {
      this.AssembleFormData()
      this.drawerVisible = true
    },
    execDownload(data) {
      const codeStr = this.generateCode()
      const blob = new Blob([codeStr], { type: 'text/plain;charset=utf-8' })
      this.$download.saveAs(blob, data.fileName)
    },
    execCopy(data) {
      document.getElementById('copyNode').click()
    },
    empty() {
      this.$confirm('确定要清空所有组件吗？', '提示', { type: 'warning' }).then(
        () => {
          this.drawingList = []
          this.activeData = {}
          this.activeId = null
        }
      )
    },
    drawingItemCopy(item, parent) {
      let clone = JSON.parse(JSON.stringify(item))
      clone = this.createIdAndKey(clone)
      parent.push(clone)
      this.activeFormItem(clone)
    },
    createIdAndKey(item) {
      item.formId = ++this.idGlobal
      item.renderKey = +new Date()
      if (item.layout === 'colFormItem') {
        item.vModel = `field${this.idGlobal}`
      } else if (item.layout === 'rowFormItem') {
        item.componentName = `row${this.idGlobal}`
      }
      if (Array.isArray(item.children)) {
        item.children = item.children.map(childItem => this.createIdAndKey(childItem))
      }
      return item
    },
    drawingItemDelete(index, parent) {
      parent.splice(index, 1)
      this.$nextTick(() => {
        const len = this.drawingList.length
        if (len) {
          this.activeFormItem(this.drawingList[len - 1])
        }
      })
    },
    generateCode() {
      const { type } = this.generateConf
      this.AssembleFormData()
      const script = vueScript(makeUpJs(this.formData, type))
      const html = vueTemplate(makeUpHtml(this.formData, type))
      const css = cssStyle(makeUpCss(this.formData))
      return beautifier.html(html + script + css, beautifierConf.html)
    },
    download() {
      this.dialogVisible = true
      this.showFileName = true
      this.operationType = 'download'
    },
    run() {
      this.dialogVisible = true
      this.showFileName = false
      this.operationType = 'run'
    },
    copy() {
      this.dialogVisible = true
      this.showFileName = false
      this.operationType = 'copy'
    },
    tagChange(newTag) {
      newTag = this.cloneComponent(newTag)
      newTag.vModel = this.activeData.vModel
      newTag.formId = this.activeId
      newTag.span = this.activeData.span
      delete this.activeData.tag
      delete this.activeData.tagIcon
      delete this.activeData.document
      Object.keys(newTag).forEach(key => {
        if (this.activeData[key] !== undefined
          && typeof this.activeData[key] === typeof newTag[key]) {
          newTag[key] = this.activeData[key]
        }
      })
      this.activeData = newTag
      this.updateDrawingList(newTag, this.drawingList)
    },
    updateDrawingList(newTag, list) {
      const index = list.findIndex(item => item.formId === this.activeId)
      if (index > -1) {
        list.splice(index, 1, newTag)
      } else {
        list.forEach(item => {
          if (Array.isArray(item.children)) this.updateDrawingList(newTag, item.children)
        })
      }
    }
  }
}
</script>

<style lang='scss'>
.design-draft-bar { padding: 8px 12px; display: flex; flex-wrap: wrap; align-items: center; gap: 8px; border-bottom: 1px solid #e4e7ed; font-size: 12px; }
.design-draft-bar .draft-error { color: #b42318; }
.container .center-board { display: flex; flex-direction: column; }
.container .center-scrollbar { flex: 1; min-height: 0; height: 0; }
.container .center-board > .action-bar { flex-shrink: 0; }

.editor-tabs{
  background: #121315;
  .el-tabs__header{
    margin: 0;
    border-bottom-color: #121315;
    .el-tabs__nav{
      border-color: #121315;
    }
  }
  .el-tabs__item{
    height: 32px;
    line-height: 32px;
    color: #888a8e;
    border-left: 1px solid #121315 !important;
    background: #363636;
    margin-right: 5px;
    user-select: none;
  }
  .el-tabs__item.is-active{
    background: #1e1e1e;
    border-bottom-color: #1e1e1e!important;
    color: #fff;
  }
  .el-icon-edit{
    color: #f1fa8c;
  }
  .el-icon-document{
    color: #a95812;
  }
}

// home
.right-scrollbar {
  .el-scrollbar__view {
    padding: 12px 18px 15px 15px;
  }
}
.left-scrollbar .el-scrollbar__wrap {
  box-sizing: border-box;
  overflow-x: hidden !important;
  margin-bottom: 0 !important;
}
.center-tabs{
  .el-tabs__header{
    margin-bottom: 0!important;
  }
  .el-tabs__item{
    width: 50%;
    text-align: center;
  }
  .el-tabs__nav{
    width: 100%;
  }
}
.reg-item{
  padding: 12px 6px;
  background: #f8f8f8;
  position: relative;
  border-radius: 4px;
  .close-btn{
    position: absolute;
    right: -6px;
    top: -6px;
    display: block;
    width: 16px;
    height: 16px;
    line-height: 16px;
    background: rgba(0, 0, 0, 0.2);
    border-radius: 50%;
    color: #fff;
    text-align: center;
    z-index: 1;
    cursor: pointer;
    font-size: 12px;
    &:hover{
      background: rgba(210, 23, 23, 0.5)
    }
  }
  & + .reg-item{
    margin-top: 18px;
  }
}
.action-bar{
  & .el-button+.el-button {
    margin-left: 15px;
  }
  & i {
    font-size: 20px;
    vertical-align: middle;
    position: relative;
    top: -1px;
  }
}

.custom-tree-node{
  width: 100%;
  font-size: 14px;
  .node-operation{
    float: right;
  }
  i[class*="el-icon"] + i[class*="el-icon"]{
    margin-left: 6px;
  }
  .el-icon-plus{
    color: var(--erp-primary, #0b6b53);
  }
  .el-icon-delete{
    color: #157a0c;
  }
}

.left-scrollbar .el-scrollbar__view{
  overflow-x: hidden;
}

.el-rate{
  display: inline-block;
  vertical-align: text-top;
}
.el-upload__tip{
  line-height: 1.2;
}

$selectedColor: #f6f7ff;
$lighterBlue: #0b6b53;

.container {
  position: relative;
  width: 100%;
  height: 100%;
}

.components-list {
  padding: 8px;
  box-sizing: border-box;
  height: 100%;
  .components-item {
    display: inline-block;
    width: 48%;
    margin: 1%;
    transition: transform 0ms !important;
  }
}
.components-draggable{
  padding-bottom: 20px;
}
.components-title{
  font-size: 14px;
  color: #222;
  margin: 6px 2px;
  .svg-icon{
    color: #666;
    font-size: 18px;
  }
}

.components-body {
  padding: 8px 10px;
  background: $selectedColor;
  font-size: 12px;
  cursor: move;
  border: 1px dashed $selectedColor;
  border-radius: 3px;
  .svg-icon{
    color: #777;
    font-size: 15px;
  }
  &:hover {
    border: 1px dashed #787be8;
    color: #787be8;
    .svg-icon {
      color: #787be8;
    }
  }
}

.left-board {
  width: 260px;
  position: absolute;
  left: 0;
  top: 0;
  height: 100vh;
}
.left-scrollbar{
  height: calc(100vh - 42px);
  overflow: hidden;
}
.center-scrollbar {
  height: calc(100vh - 42px);
  overflow: hidden;
  border-left: 1px solid #f1e8e8;
  border-right: 1px solid #f1e8e8;
  box-sizing: border-box;
}
.center-board {
  height: 100vh;
  width: auto;
  margin: 0 350px 0 260px;
  box-sizing: border-box;
}
.empty-info{
  position: absolute;
  top: 46%;
  left: 0;
  right: 0;
  text-align: center;
  font-size: 18px;
  color: #ccb1ea;
  letter-spacing: 4px;
}
.action-bar{
  position: relative;
  height: 42px;
  text-align: right;
  padding: 0 15px;
  box-sizing: border-box;;
  border: 1px solid #f1e8e8;
  border-top: none;
  border-left: none;
  .delete-btn{
    color: #F56C6C;
  }
}
.logo-wrapper{
  position: relative;
  height: 42px;
  background: #fff;
  border-bottom: 1px solid #f1e8e8;
  box-sizing: border-box;
}
.logo{
  position: absolute;
  left: 12px;
  top: 6px;
  line-height: 30px;
  color: #00afff;
  font-weight: 600;
  font-size: 17px;
  white-space: nowrap;
  > img{
    width: 30px;
    height: 30px;
    vertical-align: top;
  }
  .github{
    display: inline-block;
    vertical-align: sub;
    margin-left: 15px;
    > img{
      height: 22px;
    }
  }
}

.center-board-row {
  padding: 12px 12px 15px 12px;
  box-sizing: border-box;
  & > .el-form {
    // 69 = 12+15+42
    height: calc(100vh - 69px);
  }
}
.drawing-board {
  height: 100%;
  position: relative;
  .components-body {
    padding: 0;
    margin: 0;
    font-size: 0;
  }
  .sortable-ghost {
    position: relative;
    display: block;
    overflow: hidden;
    &::before {
      content: " ";
      position: absolute;
      left: 0;
      right: 0;
      top: 0;
      height: 3px;
      background: rgb(89, 89, 223);
      z-index: 2;
    }
  }
  .components-item.sortable-ghost {
    width: 100%;
    height: 60px;
    background-color: $selectedColor;
  }
  .active-from-item {
    & > .el-form-item{
      background: $selectedColor;
      border-radius: 6px;
    }
    & > .drawing-item-copy, & > .drawing-item-delete{
      display: initial;
    }
    & > .component-name{
      color: $lighterBlue;
    }
  }
  .el-form-item{
    margin-bottom: 15px;
  }
}
.drawing-item{
  position: relative;
  cursor: move;
  &.unfocus-bordered:not(.activeFromItem) > div:first-child  {
    border: 1px dashed #ccc;
  }
  .el-form-item{
    padding: 12px 10px;
  }
}
.drawing-row-item{
  position: relative;
  cursor: move;
  box-sizing: border-box;
  border: 1px dashed #ccc;
  border-radius: 3px;
  padding: 0 2px;
  margin-bottom: 15px;
  .drawing-row-item {
    margin-bottom: 2px;
  }
  .el-col{
    margin-top: 22px;
  }
  .el-form-item{
    margin-bottom: 0;
  }
  .drag-wrapper{
    min-height: 80px;
  }
  &.active-from-item{
    border: 1px dashed $lighterBlue;
  }
  .component-name{
    position: absolute;
    top: 0;
    left: 0;
    font-size: 12px;
    color: #bbb;
    display: inline-block;
    padding: 0 6px;
  }
}
.drawing-item, .drawing-row-item{
  &:hover {
    & > .el-form-item{
      background: $selectedColor;
      border-radius: 6px;
    }
    & > .drawing-item-copy, & > .drawing-item-delete{
      display: initial;
    }
  }
  & > .drawing-item-copy, & > .drawing-item-delete{
    display: none;
    position: absolute;
    top: -10px;
    width: 22px;
    height: 22px;
    line-height: 22px;
    text-align: center;
    border-radius: 50%;
    font-size: 12px;
    border: 1px solid;
    cursor: pointer;
    z-index: 1;
  }
  & > .drawing-item-copy{
    right: 56px;
    border-color: $lighterBlue;
    color: $lighterBlue;
    background: #fff;
    &:hover{
      background: $lighterBlue;
      color: #fff;
    }
  }
  & > .drawing-item-delete{
    right: 24px;
    border-color: #F56C6C;
    color: #F56C6C;
    background: #fff;
    &:hover{
      background: #F56C6C;
      color: #fff;
    }
  }
}
</style>
