<template>
  <div class="app-container catalog-page gift-catalog-page">
    <inventory-page-hero
      :title="readonlyMode ? '礼盒资料查询' : '礼盒管理'"
      eyebrow="仓库资料"
      description="统一维护礼盒规格、补货单位与指导售价，为门店补货和销售核对提供可靠资料。"
      :scope-text="readonlyMode ? '统一资料查询' : '统一礼盒资料'"
      icon="el-icon-present"
      :features="['礼盒资料', '指导售价', '补货引用']"
    />
    <div class="catalog-layout" :class="{ 'is-category-collapsed': categoryCollapsed }">
      <aside class="category-panel">
        <el-card shadow="never" class="category-card">
          <div slot="header" class="card-header">
            <span>礼盒分类</span>
            <div class="card-actions">
              <el-tooltip content="刷新分类" placement="top">
                <el-button v-hasPermi="['inv:giftCategory:list', 'inv:giftCategory:tree']" type="text" size="mini" icon="el-icon-refresh" @click="loadCategories">刷新</el-button>
              </el-tooltip>
              <el-tooltip content="收起分类" placement="top">
                <el-button type="text" size="mini" icon="el-icon-d-arrow-left" @click="toggleCategoryPanel(true)">收起</el-button>
              </el-tooltip>
            </div>
          </div>
          <el-input
            v-model="categoryKeyword"
            size="small"
            clearable
            prefix-icon="el-icon-search"
            placeholder="搜索礼盒分类"
            class="category-search"
          />
          <el-tree
            ref="categoryTree"
            v-loading="categoryLoading"
            class="category-tree"
            :data="categoryTreeData"
            :props="categoryProps"
            node-key="categoryId"
            default-expand-all
            highlight-current
            :expand-on-click-node="false"
            :filter-node-method="filterCategoryNode"
            @node-click="handleCategoryClick"
          >
            <span class="tree-node" slot-scope="{ data }">
              <span class="tree-node-marker"></span>
              <span class="tree-node-label">{{ data.categoryName }}</span>
            </span>
          </el-tree>
          <div class="category-summary">
            <div v-if="categoryLoadError" class="category-error">{{ categoryLoadError }}</div>
            <div>当前分类：{{ selectedCategoryName }}</div>
            <div>筛选到 {{ total }} 条礼盒资料</div>
          </div>
        </el-card>
      </aside>

      <main class="catalog-main">
        <div class="collapsed-category-action">
          <el-button size="mini" icon="el-icon-d-arrow-right" @click="toggleCategoryPanel(false)">展开分类</el-button>
        </div>

        <el-card shadow="never" class="search-card mb12">
          <div slot="header" class="page-header">
            <div>
              <span class="section-eyebrow">仓库资料</span>
              <h2>{{ readonlyMode ? "礼盒资料查询" : "礼盒管理" }}</h2>
              <p>维护礼盒基础资料和指导售价，资料查询页可直接查看提成参考价。</p>
            </div>
            <div class="header-actions">
              <el-button v-hasPermi="['inv:gift:list']" size="mini" icon="el-icon-refresh" @click="getList">刷新</el-button>
              <el-button v-if="!readonlyMode" v-hasPermi="['inv:gift:add']" type="primary" size="mini" icon="el-icon-plus" @click="openForm()">新增礼盒</el-button>
              <el-button v-if="!readonlyMode" v-hasPermi="['inv:gift:import']" size="mini" icon="el-icon-upload2" @click="openImport">导入</el-button>
              <el-button v-if="!readonlyMode" v-hasPermi="['inv:gift:import']" size="mini" icon="el-icon-document" @click="downloadTemplate">下载模板</el-button>
              <el-button v-hasPermi="['inv:gift:export']" size="mini" icon="el-icon-download" @click="handleExport">导出</el-button>
            </div>
          </div>
          <el-form :model="queryParams" inline size="small" class="query-form">
            <el-form-item label="礼盒名称">
              <el-input v-model="queryParams.giftName" placeholder="礼盒名称" clearable @keyup.enter.native="handleQuery" />
            </el-form-item>
            <el-form-item label="礼盒编码">
              <el-input v-model="queryParams.giftCode" placeholder="礼盒编码" clearable @keyup.enter.native="handleQuery" />
            </el-form-item>
            <el-form-item label="等级">
              <el-input v-model="queryParams.grade" placeholder="等级" clearable @keyup.enter.native="handleQuery" />
            </el-form-item>
            <el-form-item label="状态">
              <el-select v-model="queryParams.status" clearable placeholder="全部状态" @change="handleQuery">
                <el-option label="正常" value="0" />
                <el-option label="停用" value="1" />
              </el-select>
            </el-form-item>
            <el-form-item class="query-actions">
              <el-button v-hasPermi="['inv:gift:list']" type="primary" size="mini" icon="el-icon-search" @click="handleQuery">搜索</el-button>
              <el-button size="mini" icon="el-icon-refresh-left" @click="resetQuery">重置</el-button>
            </el-form-item>
          </el-form>
          <div class="metric-row">
            <div class="metric-card">
              <div class="metric-value">{{ metricTotal }}</div>
              <div class="metric-label">资料总数</div>
              <div class="metric-extra">{{ selectedCategoryName }}</div>
            </div>
            <div class="metric-card">
              <div class="metric-value">{{ metricEnabled }}</div>
              <div class="metric-label">当前页启用</div>
              <div class="metric-extra">正常可查询</div>
            </div>
            <div class="metric-card">
              <div class="metric-value muted-value">{{ metricDisabled }}</div>
              <div class="metric-label">当前页停用</div>
              <div class="metric-extra">停用资料</div>
            </div>
            <div class="metric-card">
              <div class="metric-value price-value">{{ metricPriced }}</div>
              <div class="metric-label">当前页有指导价</div>
              <div class="metric-extra">用于提成核对</div>
            </div>
          </div>
        </el-card>

        <el-card shadow="never" class="table-card">
          <div slot="header" class="card-header">
            <div class="table-title">
              <span>{{ selectedCategoryName }}</span>
            </div>
            <el-button v-hasPermi="['inv:gift:list']" type="text" size="mini" icon="el-icon-refresh" @click="getList">刷新</el-button>
          </div>
          <div class="catalog-tabs">
            <el-radio-group :value="queryParams.status || ''" size="mini" @input="handleStatusTabChange">
              <el-radio-button label="">全部礼盒</el-radio-button>
              <el-radio-button label="0">启用礼盒</el-radio-button>
              <el-radio-button label="1">停用礼盒</el-radio-button>
            </el-radio-group>
            <span class="catalog-total">共 {{ total }} 条礼盒资料</span>
          </div>
          <el-table
            v-loading="loading"
            :data="list"
            size="small"
            class="catalog-table"
            @row-click="openDetail"
          >
            <el-table-column label="礼盒编码" prop="giftCode" width="140" show-overflow-tooltip />
            <el-table-column label="上线分类" prop="categoryName" min-width="150" show-overflow-tooltip />
            <el-table-column label="产品名称" prop="giftName" min-width="180" show-overflow-tooltip />
            <el-table-column label="等级" prop="grade" width="100" show-overflow-tooltip />
            <el-table-column label="规格" prop="spec" min-width="140" show-overflow-tooltip />
            <el-table-column label="补货单位" prop="replenishmentUnit" width="100" />
            <el-table-column label="参考成本价" prop="costPrice" width="120" align="right">
              <template slot-scope="scope">{{ money(scope.row.costPrice) }}</template>
            </el-table-column>
            <el-table-column label="指导售价1" prop="guidePrice1" width="120" align="right">
              <template slot-scope="scope">{{ money(scope.row.guidePrice1) }}</template>
            </el-table-column>
            <el-table-column label="指导售价2" prop="guidePrice2" width="120" align="right">
              <template slot-scope="scope">{{ money(scope.row.guidePrice2) }}</template>
            </el-table-column>
            <el-table-column label="供应商" prop="supplierName" min-width="160" show-overflow-tooltip />
            <el-table-column label="状态" prop="status" width="90" align="center">
              <template slot-scope="scope">
                <el-tag :type="statusTagType(scope.row.status)" size="mini">{{ statusLabel(scope.row.status) }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="190" fixed="right" align="center">
              <template slot-scope="scope">
                <el-button v-hasPermi="['inv:gift:query']" type="text" size="mini" icon="el-icon-view" @click.stop="openDetail(scope.row)">详情</el-button>
                <el-button v-if="!readonlyMode" v-hasPermi="['inv:gift:edit']" type="text" size="mini" icon="el-icon-edit" @click.stop="openForm(scope.row)">编辑</el-button>
                <el-button v-if="!readonlyMode" v-hasPermi="['inv:gift:remove']" type="text" size="mini" icon="el-icon-delete" class="danger-action" @click.stop="handleDelete(scope.row)">删除</el-button>
              </template>
            </el-table-column>
          </el-table>
          <pagination
            v-show="total > 0"
            :total="total"
            :page.sync="queryParams.pageNum"
            :limit.sync="queryParams.pageSize"
            @pagination="getList"
          />
        </el-card>
      </main>
    </div>

    <el-dialog title="礼盒详情" :visible.sync="detailOpen" width="760px" append-to-body @close="handleDetailClose">
      <div v-if="detailGift" class="catalog-detail">
        <div class="catalog-detail-image">
          <image-gallery :value="detailGift" />
        </div>
        <el-descriptions :column="2" border size="small" class="catalog-detail-info">
          <el-descriptions-item label="礼盒编码">{{ displayText(detailGift.giftCode) }}</el-descriptions-item>
          <el-descriptions-item label="产品名称">{{ displayText(detailGift.giftName) }}</el-descriptions-item>
          <el-descriptions-item label="上线分类">{{ displayText(detailGift.categoryName) }}</el-descriptions-item>
          <el-descriptions-item label="等级">{{ displayText(detailGift.grade) }}</el-descriptions-item>
          <el-descriptions-item label="规格">{{ displayText(detailGift.spec) }}</el-descriptions-item>
          <el-descriptions-item label="补货单位">{{ displayText(detailGift.replenishmentUnit) }}</el-descriptions-item>
          <el-descriptions-item label="参考成本价">{{ money(detailGift.costPrice) }}</el-descriptions-item>
          <el-descriptions-item label="指导售价1">{{ money(detailGift.guidePrice1) }}</el-descriptions-item>
          <el-descriptions-item label="指导售价2">{{ money(detailGift.guidePrice2) }}</el-descriptions-item>
          <el-descriptions-item label="供应商">{{ displayText(detailGift.supplierName) }}</el-descriptions-item>
          <el-descriptions-item label="产品描述">{{ displayText(detailGift.productDescription) }}</el-descriptions-item>
          <el-descriptions-item label="状态">
            <el-tag :type="statusTagType(detailGift.status)" size="mini">{{ statusLabel(detailGift.status) }}</el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="备注">{{ displayText(detailGift.remark) }}</el-descriptions-item>
        </el-descriptions>
      </div>
    </el-dialog>

    <el-drawer :title="drawerTitle" :visible.sync="drawerOpen" size="min(560px, 100vw)" append-to-body :close-on-click-modal="false" @close="handleEditorClose">
      <div class="drawer-body">
        <div v-if="formLoading" class="editor-status" role="status">资料加载中</div>
        <div v-else-if="formLoadError" class="editor-status" role="alert">
          <div>{{ formLoadError }}</div>
          <el-button type="primary" size="mini" @click="retryEditorLoad">重新加载</el-button>
        </div>
        <el-form v-else-if="formReady" ref="formRef" :model="form" :rules="rules" label-width="112px" size="small">
          <el-form-item label="上线分类" prop="categoryId">
            <el-select v-model="form.categoryId" filterable class="full-width" placeholder="请选择礼盒分类">
              <el-option v-for="item in categoryOptions" :key="item.categoryId" :label="item.categoryFullPath" :value="item.categoryId" :disabled="item.status !== '0'" />
            </el-select>
          </el-form-item>
          <el-form-item label="礼盒编码">
            <el-input v-model="form.giftCode" disabled placeholder="保存后自动生成" />
          </el-form-item>
          <el-form-item label="产品名称" prop="giftName">
            <el-input v-model="form.giftName" maxlength="128" placeholder="请输入礼盒名称" />
          </el-form-item>
          <el-form-item label="等级">
            <el-input v-model="form.grade" maxlength="32" placeholder="请输入等级" />
          </el-form-item>
          <el-form-item label="规格">
            <el-input v-model="form.spec" maxlength="128" placeholder="请输入规格" />
          </el-form-item>
          <el-form-item label="产品描述">
            <el-input v-model="form.productDescription" type="textarea" :rows="4" maxlength="1000" show-word-limit />
          </el-form-item>
          <el-form-item label="补货单位">
            <el-input v-model="form.replenishmentUnit" maxlength="32" placeholder="如：盒、套" />
          </el-form-item>
          <el-form-item label="参考成本价">
            <el-input-number v-model="form.costPrice" :min="0" :precision="2" class="full-width" />
          </el-form-item>
          <el-form-item label="指导售价1">
            <el-input-number v-model="form.guidePrice1" :min="0" :precision="2" class="full-width" />
          </el-form-item>
          <el-form-item label="指导售价2">
            <el-input-number v-model="form.guidePrice2" :min="0" :precision="2" class="full-width" />
          </el-form-item>
          <el-form-item label="供应商">
            <el-input v-model="form.supplierName" maxlength="128" placeholder="请输入供应商名称" />
          </el-form-item>
          <el-form-item label="礼盒图片">
            <image-upload v-if="drawerOpen" v-model="form.imageUrls" action="/inventory/image/upload" accept=".jpg,.jpeg,.png,image/jpeg,image/png" :limit="5" :file-size="5" :drag="true" :delete-on-remove="false" />
          </el-form-item>
          <el-form-item label="状态">
            <el-radio-group v-model="form.status">
              <el-radio label="0">正常</el-radio>
              <el-radio label="1">停用</el-radio>
            </el-radio-group>
          </el-form-item>
          <el-form-item label="备注">
            <el-input v-model="form.remark" type="textarea" :rows="3" maxlength="500" show-word-limit />
          </el-form-item>
        </el-form>
        <div class="drawer-footer">
          <el-button size="small" @click="closeEditor">取消</el-button>
          <el-button v-if="formLoadError" size="small" type="primary" @click="retryEditorLoad">重新加载</el-button>
          <el-button v-if="!readonlyMode && formReady" v-hasPermi="['inv:gift:add','inv:gift:edit']" type="primary" size="small" :loading="saving" @click="doSave">保存</el-button>
        </div>
      </div>
    </el-drawer>

    <el-dialog title="导入礼盒资料" :visible.sync="importOpen" width="520px" :close-on-click-modal="false" append-to-body>
      <el-form :model="importForm" label-width="96px">
        <el-form-item label="Excel文件">
          <el-upload ref="uploadRef" :action="''" :auto-upload="false" :limit="1" accept=".xlsx,.xls" :file-list="importForm.fileList" :on-change="onFileChange" :on-remove="onFileRemove">
            <el-button size="small" icon="el-icon-upload2">选择文件</el-button>
            <span slot="tip" class="upload-tip">支持 .xlsx / .xls</span>
          </el-upload>
        </el-form-item>
        <el-form-item label="更新模式">
          <el-checkbox v-model="importForm.updateSupport">已存在时更新数据</el-checkbox>
        </el-form-item>
      </el-form>
      <div slot="footer">
        <el-button @click="importOpen = false">取消</el-button>
        <el-button v-if="!readonlyMode" v-hasPermi="['inv:gift:import']" type="primary" :loading="importing" @click="doImport">确认导入</el-button>
      </div>
    </el-dialog>
  </div>
</template>

<script>
import ImageGallery from "@/components/ImageGallery"
const { imageUrls } = require("@/utils/imageGallery")
import { listGift, getGift, addGift, updateGift, delGift, importGiftData, giftCategoryTree } from "@/api/inventory/gift"

export default {
  components: { ImageGallery },
  name: "InvGift",
  data() {
    return {
      loading: false,
      saving: false,
      formLoading: false,
      formReady: false,
      formLoadError: "",
      editorSourceRow: null,
      editorSession: 0,
      editorMode: "idle",
      editorTargetId: undefined,
      detailSession: 0,
      detailTargetId: undefined,
      importing: false,
      drawerOpen: false,
      importOpen: false,
      detailOpen: false,
      categoryCollapsed: false,
      categoryKeyword: "",
      categoryLoading: false,
      categoryLoadError: "",
      categoryTreeData: [],
      categoryProps: { children: "children", label: "categoryName" },
      selectedCategory: null,
      detailGift: null,
      total: 0,
      list: [],
      categoryOptions: [],
      queryParams: {
        pageNum: 1,
        pageSize: 10,
        categoryId: undefined,
        giftName: undefined,
        giftCode: undefined,
        grade: undefined,
        status: undefined
      },
      form: this.emptyForm(),
      importForm: { fileList: [], uploadFile: null, updateSupport: false },
      rules: {
        categoryId: [{ required: true, message: "请选择礼盒分类", trigger: "change" }],
        giftName: [{ required: true, message: "请输入礼盒名称", trigger: "blur" }]
      }
    }
  },
  computed: {
    readonlyMode() {
      return this.$route.query.mode === "readonly" || this.$route.query.readonly === "true"
    },
    drawerTitle() {
      return this.editorMode === "edit" || this.form.giftId ? "编辑礼盒" : "新增礼盒"
    },
    selectedCategoryName() {
      return this.selectedCategory && this.selectedCategory.categoryId ? this.selectedCategory.categoryFullPath : "全部礼盒"
    },
    metricTotal() {
      return this.total || 0
    },
    metricEnabled() {
      return this.list.filter(item => item.status === "0").length
    },
    metricDisabled() {
      return this.list.filter(item => item.status === "1").length
    },
    metricPriced() {
      return this.list.filter(item => Number(item.guidePrice1 || 0) > 0 || Number(item.guidePrice2 || 0) > 0).length
    }
  },
  watch: {
    categoryKeyword(value) {
      if (this.$refs.categoryTree) {
        this.$refs.categoryTree.filter(value)
      }
    }
  },
  created() {
    this.loadCategories()
    this.getList()
  },
  methods: {
    emptyForm() {
      return {
        giftId: undefined,
        giftCode: "",
        categoryId: undefined,
        categoryName: "",
        giftName: "",
        grade: "",
        spec: "",
        productDescription: "",
        replenishmentUnit: "",
        costPrice: 0,
        guidePrice1: 0,
        guidePrice2: 0,
        supplierName: "",
        imageUrl: "",
        imageUrls: [],
        status: "0",
        remark: ""
      }
    },
    beginEditorSession(mode, targetId) {
      this.editorSession += 1
      this.editorMode = mode
      this.editorTargetId = targetId
      this.formReady = mode === "create"
      this.formLoading = mode === "edit"
      this.formLoadError = ""
      this.saving = false
      this.drawerOpen = true
      return this.editorSession
    },
    isEditorSession(session, targetId, mode) {
      return this.editorSession === session && this.editorMode === mode && this.editorTargetId === targetId
    },
    invalidateEditor() {
      this.editorSession += 1
      this.editorMode = "idle"
      this.editorTargetId = undefined
      this.editorSourceRow = null
      this.formLoading = false
      this.formReady = false
      this.formLoadError = ""
    },
    closeEditor() {
      this.drawerOpen = false
      if (this.editorMode !== "idle") this.invalidateEditor()
    },
    handleEditorClose() {
      if (this.drawerOpen) return
      if (this.editorMode === "idle") return
      this.invalidateEditor()
    },
    retryEditorLoad() {
      if (this.editorMode !== "edit" || this.editorTargetId == null) return
      this.openForm(this.editorSourceRow || { giftId: this.editorTargetId })
    },
    canSaveEditor() {
      if (this.readonlyMode || this.saving) return false
      if (this.formLoading || this.formLoadError || !this.formReady) return false
      if (this.editorMode === "edit") {
        return this.editorTargetId != null && this.form.giftId === this.editorTargetId
      }
      if (this.editorMode === "create") {
        return !this.form.giftId && this.editorTargetId == null
      }
      return false
    },
    isDetailSession(session, targetId) {
      return this.detailSession === session && this.detailTargetId === targetId
    },
    handleDetailClose() {
      if (this.detailOpen) return
      this.detailSession += 1
      this.detailTargetId = undefined
    },
    loadCategories() {
      this.categoryLoading = true
      return giftCategoryTree().then(res => {
        this.categoryLoadError = ""
        const roots = this.decorateCategories(res.data || [])
        this.categoryTreeData = this.getDefaultCategoryTree(roots)
        this.categoryOptions = this.flattenCategories(roots)
        this.$nextTick(() => {
          if (this.$refs.categoryTree) {
            this.$refs.categoryTree.filter(this.categoryKeyword)
            this.$refs.categoryTree.setCurrentKey(this.queryParams.categoryId || 0)
          }
        })
      }).catch(() => {
        this.categoryLoadError = "分类加载失败，请联系管理员授权"
        this.categoryTreeData = this.getDefaultCategoryTree([])
        this.categoryOptions = []
      }).finally(() => {
        this.categoryLoading = false
      })
    },
    decorateCategories(list, parentPath) {
      const prefix = parentPath || []
      return (list || []).map(item => {
        const currentPath = prefix.concat(item.categoryName || "")
        return Object.assign({}, item, {
          categoryFullPath: currentPath.filter(Boolean).join(" / "),
          children: this.decorateCategories(item.children || [], currentPath)
        })
      })
    },
    flattenCategories(list) {
      const result = []
      const walk = items => {
        ;(items || []).forEach(item => {
          result.push(item)
          walk(item.children || [])
        })
      }
      walk(list)
      return result
    },
    getDefaultCategoryTree(children) {
      return [{ categoryId: 0, categoryName: "全部礼盒", categoryFullPath: "全部礼盒", children: children || [] }]
    },
    toggleCategoryPanel(collapsed) {
      this.categoryCollapsed = collapsed
    },
    filterCategoryNode(value, data) {
      if (!value) return true
      const keyword = String(value).toLowerCase()
      return String(data.categoryName || "").toLowerCase().indexOf(keyword) > -1 ||
        String(data.categoryFullPath || "").toLowerCase().indexOf(keyword) > -1
    },
    handleCategoryClick(data) {
      const categoryId = data && data.categoryId ? data.categoryId : undefined
      this.selectedCategory = categoryId ? data : null
      this.queryParams.categoryId = categoryId
      this.handleQuery()
    },
    selectCategoryRoot() {
      this.selectedCategory = null
      this.$nextTick(() => {
        if (this.$refs.categoryTree) {
          this.$refs.categoryTree.setCurrentKey(0)
        }
      })
    },
    getList() {
      this.loading = true
      listGift(this.queryParams).then(res => {
        this.list = res.rows || []
        this.total = res.total || 0
      }).finally(() => {
        this.loading = false
      })
    },
    handleQuery() {
      this.queryParams.pageNum = 1
      this.getList()
    },
    resetQuery() {
      this.queryParams = { pageNum: 1, pageSize: 10, categoryId: undefined, giftName: undefined, giftCode: undefined, grade: undefined, status: undefined }
      this.selectCategoryRoot()
      this.getList()
    },
    openForm(row) {
      if (this.readonlyMode) return
      const targetId = row && row.giftId ? row.giftId : undefined
      const mode = targetId ? "edit" : "create"
      const session = this.beginEditorSession(mode, targetId)
      this.editorSourceRow = row || null
      if (mode === "create") {
        this.form = this.emptyForm()
        return
      }
      this.form = Object.assign(this.emptyForm(), { giftId: targetId })
      getGift(targetId).then(res => {
        if (!this.isEditorSession(session, targetId, mode)) return
        this.form = Object.assign(this.emptyForm(), res.data || row, { imageUrls: imageUrls(res.data || row) })
        this.formReady = true
        this.formLoading = false
        this.formLoadError = ""
      }).catch(() => {
        if (!this.isEditorSession(session, targetId, mode)) return
        this.formLoading = false
        this.formReady = false
        this.formLoadError = "资料加载失败，请重试"
      })
    },
    openDetail(row) {
      const targetId = row && row.giftId ? row.giftId : undefined
      this.detailSession += 1
      const session = this.detailSession
      this.detailTargetId = targetId
      this.detailGift = Object.assign(this.emptyForm(), row || {}, { imageUrls: imageUrls(row) })
      this.detailOpen = true
      if (!targetId) return
      getGift(targetId).then(res => {
        if (!this.isDetailSession(session, targetId)) return
        this.detailGift = Object.assign(this.emptyForm(), res.data || row, { imageUrls: imageUrls(res.data || row) })
      })
    },
    handleStatusTabChange(status) {
      this.queryParams.status = status || undefined
      this.handleQuery()
    },
    doSave() {
      const session = this.editorSession
      const targetId = this.editorTargetId
      const mode = this.editorMode
      if (!this.canSaveEditor()) return
      const formRef = this.$refs.formRef
      if (!formRef || typeof formRef.validate !== "function") return
      formRef.validate(valid => {
        if (!this.isEditorSession(session, targetId, mode)) return
        if (!valid) return
        if (!this.canSaveEditor()) return
        this.saving = true
        const payload = Object.assign({}, this.form)
        const request = mode === "edit" ? updateGift(payload) : addGift(payload)
        request.then(() => {
          if (!this.isEditorSession(session, targetId, mode)) return
          this.$modal.msgSuccess("保存成功")
          this.closeEditor()
          this.getList()
        }).catch(() => undefined).finally(() => {
          if (!this.isEditorSession(session, targetId, mode)) return
          this.saving = false
        })
      })
    },
    handleDelete(row) {
      this.$modal.confirm("确认删除礼盒「" + row.giftName + "」？").then(() => delGift(row.giftId)).then(() => {
        this.$modal.msgSuccess("删除成功")
        this.getList()
      })
    },
    openImport() {
      if (this.readonlyMode) return
      this.importForm = { fileList: [], uploadFile: null, updateSupport: false }
      this.importOpen = true
    },
    onFileChange(file, fileList) {
      this.importForm.fileList = fileList.slice(-1)
      this.importForm.uploadFile = file.raw
    },
    onFileRemove() {
      this.importForm.fileList = []
      this.importForm.uploadFile = null
    },
    doImport() {
      if (!this.importForm.uploadFile) {
        this.$modal.msgWarning("请选择导入文件")
        return
      }
      const data = new FormData()
      data.append("file", this.importForm.uploadFile)
      data.append("updateSupport", this.importForm.updateSupport)
      this.importing = true
      importGiftData(data).then(() => {
        this.$modal.msgSuccess("导入成功")
        this.importOpen = false
        this.getList()
      }).finally(() => {
        this.importing = false
      })
    },
    downloadTemplate() {
      this.download("inventory/gift/importTemplate", {}, this.exportFileName("礼盒导入模板"))
    },
    handleExport() {
      this.download("inventory/gift/export", { ...this.queryParams }, this.exportFileName("礼盒资料"))
    },
    displayText(value) {
      return value === undefined || value === null || value === "" ? "-" : value
    },
    imageUrl(row) {
      return imageUrls(row)[0] || ""
    },
    statusLabel(status) {
      return status === "0" ? "正常" : "停用"
    },
    statusTagType(status) {
      return status === "0" ? "success" : "info"
    },
    money(value) {
      const numberValue = Number(value || 0)
      return "¥" + numberValue.toLocaleString("zh-CN", { minimumFractionDigits: 2, maximumFractionDigits: 2 })
    }
  }
}
</script>

<style lang="scss" scoped>
.app-container {
  padding: 14px 18px 24px;
  background: linear-gradient(180deg, #f8fafc 0%, #f5f7fb 48%, #f4f6fa 100%);
}

.app-container ::v-deep .el-card {
  border: 1px solid #dde6f2;
  border-radius: 8px;
  box-shadow: 0 1px 2px rgba(15, 23, 42, 0.04);
}

.catalog-page {
  .mb12 {
    margin-bottom: 12px;
  }

  .catalog-layout {
    display: flex;
    width: 100%;
    align-items: stretch;
    gap: 18px;
    min-width: 0;
  }

  ::v-deep .category-card.el-card,
  ::v-deep .search-card.el-card,
  ::v-deep .table-card.el-card {
    border: none !important;
    box-shadow: none !important;
  }

  .category-panel {
    width: 240px;
    min-width: 0;
    display: flex;
    flex: 0 0 240px;
    padding: 0 !important;
    overflow: hidden;
    opacity: 1;
    transition: flex-basis 0.22s ease, width 0.22s ease, opacity 0.16s ease;
  }

  .catalog-main {
    min-width: 0;
    flex: 1 1 auto;
  }

  .collapsed-category-action {
    display: none;
    margin-bottom: 10px;
  }

  .is-category-collapsed {
    gap: 0;

    .category-panel {
      width: 0;
      flex-basis: 0;
      opacity: 0;
      pointer-events: none;
    }

    .collapsed-category-action {
      display: flex;
    }
  }

  .category-card {
    display: flex;
    width: 100%;
    min-height: calc(100vh - 250px);
    flex: 1 1 auto;
    flex-direction: column;
  }

  ::v-deep .category-card .el-card__header {
    padding: 9px 10px;
  }

  ::v-deep .category-card .el-card__body {
    display: flex;
    flex: 1 1 auto;
    flex-direction: column;
    padding: 10px;
  }

  .category-search {
    margin-bottom: 8px;
  }

  ::v-deep .category-search .el-input__inner {
    height: 28px;
    line-height: 28px;
  }

  ::v-deep .category-tree {
    flex: 1 1 auto;
    overflow: auto;
    color: #475569;
    font-size: 12px;
  }

  ::v-deep .category-tree .el-tree-node__content {
    height: 27px;
    margin: 1px 0;
    border-radius: 5px;
  }

  ::v-deep .category-tree .el-tree-node__content:hover {
    background: #f5f7fb;
  }

  ::v-deep .category-tree .is-current > .el-tree-node__content {
    background: #edf5ff;
    color: #1f5fbf;
    font-weight: 600;
  }

  .card-header,
  .page-header,
  .header-actions,
  .drawer-footer {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 12px;
    min-width: 0;
  }

  .card-actions {
    display: inline-flex;
    flex: 0 0 auto;
    align-items: center;
    gap: 6px;
  }

  .card-actions .el-button {
    padding: 0 2px;
    font-size: 12px;
    font-weight: 700;
  }

  .section-eyebrow {
    color: #8a99ad;
    font-size: 12px;
  }

  .page-header h2 {
    margin: 4px 0;
    color: #172033;
    font-size: 20px;
  }

  .page-header p {
    margin: 0;
    color: #64748b;
  }

  .category-summary {
    margin-top: auto;
    padding: 10px 11px;
    border-radius: 8px;
    background: #f7f9fd;
    color: #6d7a8e;
    font-size: 12px;
    line-height: 18px;
  }

  .category-error {
    margin-bottom: 4px;
    color: #d14b45;
    font-weight: 600;
  }

  .tree-node {
    display: flex;
    min-width: 0;
    width: 100%;
    align-items: center;
    gap: 6px;
    padding-right: 8px;
  }

  .tree-node-marker {
    width: 4px;
    height: 4px;
    flex: 0 0 4px;
    border-radius: 50%;
    background: #cbd5e1;
  }

  .tree-node-label {
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  .query-form {
    display: flex;
    flex-wrap: wrap;
  }

  ::v-deep .search-card .el-card__body {
    padding: 18px 22px 16px;
  }

  ::v-deep .query-form .el-form-item {
    margin-right: 14px;
    margin-bottom: 12px;
  }

  ::v-deep .query-form .el-form-item__label {
    color: #344258;
    font-size: 13px;
    font-weight: 700;
    line-height: 34px;
  }

  ::v-deep .query-form .el-input__inner {
    height: 34px;
    line-height: 34px;
    border-radius: 7px;
    font-size: 13px;
  }

  .query-actions {
    white-space: nowrap;
  }

  .metric-row {
    display: grid;
    grid-template-columns: repeat(4, minmax(120px, 1fr));
    gap: 10px;
    margin-top: 2px;
  }

  .metric-card {
    min-height: 68px;
    padding: 10px 12px;
    background: #f8fafc;
    border: 1px solid #eef3f8;
    border-radius: 8px;
  }

  .metric-value {
    color: #0f172a;
    font-size: 21px;
    font-weight: 700;
    line-height: 24px;
    font-variant-numeric: tabular-nums;
    letter-spacing: 0;
  }

  .metric-label {
    margin-top: 2px;
    color: #64748b;
    font-size: 12px;
  }

  .metric-extra {
    margin-top: 3px;
    color: #94a3b8;
    font-size: 12px;
    line-height: 16px;
  }

  .muted-value {
    color: #64748b;
  }

  .price-value {
    color: #0f9f6e;
  }

  ::v-deep .table-card .el-card__body {
    padding: 16px 18px;
  }

  .table-title {
    display: inline-flex;
    min-width: 0;
    align-items: center;
    gap: 8px;
  }

  .table-title span {
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  .catalog-tabs {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 12px;
    min-height: 44px;
    padding-bottom: 12px;
    border-bottom: 1px solid #edf2f7;
  }

  .catalog-tabs ::v-deep .el-radio-button__inner {
    height: 30px;
    min-width: 78px;
    padding: 7px 12px;
    color: #64748b;
    font-size: 12px;
    font-weight: 700;
  }

  .catalog-total {
    color: #8a99ad;
    font-size: 12px;
    white-space: nowrap;
  }

  .catalog-table ::v-deep .el-table__row {
    cursor: pointer;
  }

  .drawer-body {
    padding: 0 20px 20px;
  }

  .editor-status {
    padding: 24px 8px 12px;
    color: #64748b;
    font-size: 13px;
    line-height: 20px;
  }

  .editor-status[role="alert"] {
    color: #d14b45;
    font-weight: 600;
  }

  .editor-status .el-button {
    margin-top: 10px;
  }

  .full-width {
    width: 100%;
  }

  .danger-action {
    color: #f56c6c;
  }

  .upload-tip {
    margin-left: 8px;
    color: #909399;
  }
}

.catalog-detail {
  display: grid;
  grid-template-columns: 180px minmax(0, 1fr);
  gap: 18px;
  align-items: start;
}

.catalog-detail-image {
  width: 180px;
  height: 180px;
  overflow: hidden;
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  background: #f8fafc;
}

.catalog-detail-image ::v-deep .el-image,
.catalog-detail-image ::v-deep img {
  width: 100%;
  height: 100%;
}

.catalog-image-empty {
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #94a3b8;
  font-size: 13px;
  font-weight: 700;
}

.catalog-detail-info {
  min-width: 0;
}

@media (max-width: 992px) {
  .catalog-page {
    .catalog-layout {
      flex-direction: column;
    }

    .category-panel {
      width: 100%;
      flex: 0 0 auto;
      max-height: 900px;
      transition: max-height 0.22s ease, opacity 0.16s ease;
    }

    .is-category-collapsed {
      .category-panel {
        width: 100%;
        max-height: 0;
        flex-basis: auto;
      }
    }

    .category-card {
      min-height: auto;
      margin-bottom: 12px;
    }

    .metric-row {
      grid-template-columns: repeat(2, minmax(120px, 1fr));
    }

    .catalog-tabs,
    .page-header {
      align-items: flex-start;
      flex-direction: column;
    }
  }

  .catalog-detail {
    grid-template-columns: 1fr;
  }

  .catalog-detail-image {
    width: 100%;
    max-width: 220px;
  }
}

@media (max-width: 640px) {
  .catalog-page {
    .metric-row {
      grid-template-columns: 1fr;
    }
  }
}
</style>
