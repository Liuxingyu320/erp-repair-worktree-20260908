<template>
  <div class="app-container catalog-page oe-catalog-page">
    <inventory-page-hero
      :title="readonlyMode ? 'OE 资料查询' : 'OE 管理'"
      eyebrow="仓库资料"
      description="统一维护 OE 器皿、供应商与成本信息，为资产配置和维修上报提供可靠资料。"
      :scope-text="readonlyMode ? '统一资料查询' : '统一 OE 器皿档案'"
      icon="el-icon-coffee-cup"
      :features="['器皿资料', '供应商', '资产引用']"
    />
    <div class="catalog-layout" :class="{ 'is-category-collapsed': categoryCollapsed }">
      <aside class="category-panel">
        <el-card shadow="never" class="category-card">
          <div slot="header" class="card-header">
            <span>OE分类</span>
            <div class="card-actions">
              <el-tooltip content="刷新分类" placement="top">
                <el-button v-hasPermi="['inv:oeCategory:list', 'inv:oeCategory:tree']" type="text" size="mini" icon="el-icon-refresh" @click="loadCategories">刷新</el-button>
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
            placeholder="搜索OE分类"
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
            <div>筛选到 {{ total }} 条OE资料</div>
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
              <h2>{{ readonlyMode ? "OE资料查询" : "OE管理" }}</h2>
              <p>维护 OE 器皿基础资料和同款购买参考，供固定资产配置和维修上报使用。</p>
            </div>
            <div class="header-actions">
              <el-button v-hasPermi="['inv:oe:list']" size="mini" icon="el-icon-refresh" @click="getList">刷新</el-button>
              <el-button v-if="!readonlyMode" v-hasPermi="['inv:oe:add']" type="primary" size="mini" icon="el-icon-plus" @click="openForm()">新增OE</el-button>
              <el-button v-if="!readonlyMode" v-hasPermi="['inv:oe:import']" size="mini" icon="el-icon-upload2" @click="openImport">导入</el-button>
              <el-button v-if="!readonlyMode" v-hasPermi="['inv:oe:import']" size="mini" icon="el-icon-document" @click="downloadTemplate">下载模板</el-button>
              <el-button v-hasPermi="['inv:oe:export']" size="mini" icon="el-icon-download" @click="handleExport">导出</el-button>
            </div>
          </div>
          <el-form :model="queryParams" inline size="small" class="query-form">
            <el-form-item label="物品名称">
              <el-input v-model="queryParams.oeItemName" placeholder="OE器皿名称" clearable @keyup.enter.native="handleQuery" />
            </el-form-item>
            <el-form-item label="产品编号">
              <el-input v-model="queryParams.oeItemCode" placeholder="产品编号" clearable @keyup.enter.native="handleQuery" />
            </el-form-item>
            <el-form-item label="供应商">
              <el-input v-model="queryParams.supplierName" placeholder="供应商名称" clearable @keyup.enter.native="handleQuery" />
            </el-form-item>
            <el-form-item label="状态">
              <el-select v-model="queryParams.status" clearable placeholder="全部状态" @change="handleQuery">
                <el-option label="正常" value="0" />
                <el-option label="停用" value="1" />
              </el-select>
            </el-form-item>
            <el-form-item label="同款资料">
              <el-select v-model="queryParams.purchaseReferenceStatus" clearable placeholder="全部资料" @change="handleQuery">
                <el-option label="已完善" value="COMPLETE" />
                <el-option label="待完善" value="INCOMPLETE" />
                <el-option label="非固定资产" value="NOT_FIXED_ASSET" />
              </el-select>
            </el-form-item>
            <el-form-item class="query-actions">
              <el-button v-hasPermi="['inv:oe:list']" type="primary" size="mini" icon="el-icon-search" @click="handleQuery">搜索</el-button>
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
              <div class="metric-value reference-value">{{ metricReferenceReady }}</div>
              <div class="metric-label">当前页同款已完善</div>
              <div class="metric-extra">可直接提供购买参考</div>
            </div>
          </div>
        </el-card>

        <el-card shadow="never" class="table-card">
          <div slot="header" class="card-header">
            <div class="table-title">
              <span>{{ selectedCategoryName }}</span>
            </div>
            <el-button v-hasPermi="['inv:oe:list']" type="text" size="mini" icon="el-icon-refresh" @click="getList">刷新</el-button>
          </div>
          <div class="catalog-tabs">
            <el-radio-group :value="queryParams.status || ''" size="mini" @input="handleStatusTabChange">
              <el-radio-button label="">全部OE</el-radio-button>
              <el-radio-button label="0">启用OE</el-radio-button>
              <el-radio-button label="1">停用OE</el-radio-button>
            </el-radio-group>
            <span class="catalog-total">共 {{ total }} 条OE资料</span>
          </div>
          <el-table
            v-loading="loading"
            :data="list"
            size="small"
            class="catalog-table"
            @row-click="openDetail"
          >
            <el-table-column label="产品编号" prop="oeItemCode" width="140" show-overflow-tooltip />
            <el-table-column label="上线分类" prop="categoryName" min-width="150" show-overflow-tooltip />
            <el-table-column label="产品类别名称" prop="oeTypeName" min-width="150" show-overflow-tooltip />
            <el-table-column label="物品名称" prop="oeItemName" min-width="170" show-overflow-tooltip />
            <el-table-column label="订货单位" prop="orderUnit" width="100" />
            <el-table-column label="成本价" prop="costPrice" width="110" align="right">
              <template slot-scope="scope">{{ money(scope.row.costPrice) }}</template>
            </el-table-column>
            <el-table-column label="供应商" prop="supplierName" min-width="150" show-overflow-tooltip />
            <el-table-column label="供应商电话" prop="supplierPhone" width="130" show-overflow-tooltip />
            <el-table-column label="同款资料" prop="purchaseReferenceStatus" width="110" align="center">
              <template slot-scope="scope">
                <el-tag :type="purchaseReferenceStatusType(scope.row.purchaseReferenceStatus)" size="mini">
                  {{ purchaseReferenceStatusLabel(scope.row.purchaseReferenceStatus) }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="状态" prop="status" width="90" align="center">
              <template slot-scope="scope">
                <el-tag :type="statusTagType(scope.row.status)" size="mini">{{ statusLabel(scope.row.status) }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="260" fixed="right" align="center">
              <template slot-scope="scope">
                <el-button v-hasPermi="['inv:oe:query']" type="text" size="mini" icon="el-icon-view" @click.stop="openDetail(scope.row)">详情</el-button>
                <el-button v-if="!readonlyMode" v-hasPermi="['inv:oe:edit']" type="text" size="mini" icon="el-icon-edit" @click.stop="openForm(scope.row)">编辑</el-button>
                <el-button v-if="!readonlyMode" v-hasPermi="['inv:oe:edit']" type="text" size="mini" icon="el-icon-link" @click.stop="openForm(scope.row, true)">同款资料</el-button>
                <el-button v-if="!readonlyMode" v-hasPermi="['inv:oe:remove']" type="text" size="mini" icon="el-icon-delete" class="danger-action" @click.stop="handleDelete(scope.row)">删除</el-button>
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

    <el-dialog title="OE详情" :visible.sync="detailOpen" width="760px" append-to-body>
      <div v-if="detailOe" class="catalog-detail">
        <div class="catalog-detail-image">
          <el-image
            v-if="imageUrl(detailOe)"
            :src="imageUrl(detailOe)"
            fit="cover"
            :preview-src-list="[imageUrl(detailOe)]"
          />
          <div v-else class="catalog-image-empty">暂无图片</div>
        </div>
        <el-descriptions :column="2" border size="small" class="catalog-detail-info">
          <el-descriptions-item label="产品编号">{{ displayText(detailOe.oeItemCode) }}</el-descriptions-item>
          <el-descriptions-item label="物品名称">{{ displayText(detailOe.oeItemName) }}</el-descriptions-item>
          <el-descriptions-item label="上线分类">{{ displayText(detailOe.categoryName) }}</el-descriptions-item>
          <el-descriptions-item label="产品类别名称">{{ displayText(detailOe.oeTypeName) }}</el-descriptions-item>
          <el-descriptions-item label="订货单位">{{ displayText(detailOe.orderUnit) }}</el-descriptions-item>
          <el-descriptions-item label="成本价">{{ money(detailOe.costPrice) }}</el-descriptions-item>
          <el-descriptions-item label="供应商">{{ displayText(detailOe.supplierName) }}</el-descriptions-item>
          <el-descriptions-item label="供应商电话">{{ displayText(detailOe.supplierPhone) }}</el-descriptions-item>
          <el-descriptions-item label="产品描述">{{ displayText(detailOe.itemDescription) }}</el-descriptions-item>
          <el-descriptions-item label="状态">
            <el-tag :type="statusTagType(detailOe.status)" size="mini">{{ statusLabel(detailOe.status) }}</el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="备注">{{ displayText(detailOe.remark) }}</el-descriptions-item>
          <el-descriptions-item label="同款购买链接" :span="2">
            <el-link
              v-if="safePurchaseReferenceUrl(detailOe.purchaseReferenceUrl)"
              :href="safePurchaseReferenceUrl(detailOe.purchaseReferenceUrl)"
              target="_blank"
              rel="noopener noreferrer"
              type="primary"
            >{{ detailOe.purchaseReferenceUrl }}</el-link>
            <span v-else>-</span>
          </el-descriptions-item>
          <el-descriptions-item label="购买说明" :span="2">{{ displayText(detailOe.purchaseReferenceNote) }}</el-descriptions-item>
          <el-descriptions-item label="同款资料维护人">{{ displayText(detailOe.purchaseReferenceUpdatedBy) }}</el-descriptions-item>
          <el-descriptions-item label="同款资料维护时间">{{ formatReferenceTime(detailOe.purchaseReferenceUpdatedTime) }}</el-descriptions-item>
        </el-descriptions>
      </div>
    </el-dialog>

    <el-drawer :title="drawerTitle" :visible.sync="drawerOpen" size="min(560px, 100vw)" append-to-body :close-on-click-modal="false">
      <div class="drawer-body">
        <el-form ref="formRef" :model="form" :rules="rules" label-width="112px" size="small">
          <el-form-item label="上线分类" prop="categoryId">
            <el-select v-model="form.categoryId" filterable class="full-width" placeholder="请选择OE分类">
              <el-option v-for="item in categoryOptions" :key="item.categoryId" :label="item.categoryFullPath" :value="item.categoryId" :disabled="item.status !== '0'" />
            </el-select>
          </el-form-item>
          <el-form-item label="产品编号">
            <el-input v-model="form.oeItemCode" disabled placeholder="保存后自动生成" />
          </el-form-item>
          <el-form-item label="产品类别名称" prop="oeTypeName">
            <el-input v-model="form.oeTypeName" maxlength="128" placeholder="请输入产品类别名称" />
          </el-form-item>
          <el-form-item label="物品名称" prop="oeItemName">
            <el-input v-model="form.oeItemName" maxlength="128" placeholder="请输入物品名称" />
          </el-form-item>
          <el-form-item label="产品描述">
            <el-input v-model="form.itemDescription" type="textarea" :rows="4" maxlength="1000" show-word-limit />
          </el-form-item>
          <el-form-item label="订货单位">
            <el-input v-model="form.orderUnit" maxlength="32" placeholder="如：个、套、只" />
          </el-form-item>
          <el-form-item label="成本价">
            <el-input-number v-model="form.costPrice" :min="0" :precision="2" class="full-width" />
          </el-form-item>
          <el-form-item label="供应商名称" prop="supplierName">
            <el-select v-model="form.supplierName" clearable filterable class="full-width" placeholder="选择供应商" :loading="supplierLoading" @change="handleSupplierChange" @visible-change="loadSuppliersOnOpen">
              <el-option v-for="item in supplierOptions" :key="item.supplierId" :label="supplierLabel(item)" :value="item.supplierName">
                <span>{{ item.supplierName }}</span>
                <span class="option-meta">{{ item.contactPhone || "无电话" }}</span>
              </el-option>
            </el-select>
          </el-form-item>
          <el-form-item label="供应商电话">
            <el-input v-model="form.supplierPhone" disabled placeholder="选择供应商后自动带出" />
          </el-form-item>
          <el-form-item label="图片">
            <image-upload v-model="form.imageUrl" action="/inventory/image/upload" accept=".jpg,.jpeg,.png,image/jpeg,image/png" :limit="1" :file-size="5" :drag="false" :delete-on-remove="false" />
          </el-form-item>
          <div class="drawer-section-heading">
            <div>
              <strong>同款购买参考</strong>
              <p>额度不足时，员工将按这里的图片、规格和链接自行购买，无需再次上报。</p>
            </div>
            <el-tag :type="purchaseReferenceStatusType(formPurchaseReferenceStatus)" size="mini">
              {{ purchaseReferenceStatusLabel(formPurchaseReferenceStatus) }}
            </el-tag>
          </div>
          <el-alert
            v-if="purchaseReferencePolicy.configured === false"
            title="可信购买域名白名单尚未配置，暂不能新增或修改购买参考"
            type="warning"
            :closable="false"
            show-icon
            class="reference-policy-alert"
          />
          <el-alert
            v-else-if="purchaseReferencePolicyError"
            :title="purchaseReferencePolicyError"
            type="warning"
            :closable="false"
            show-icon
            class="reference-policy-alert"
          />
          <el-form-item label="同款购买链接" prop="purchaseReferenceUrl">
            <el-input
              ref="purchaseReferenceUrlInput"
              v-model="form.purchaseReferenceUrl"
              :disabled="referenceInputDisabled"
              maxlength="1000"
              show-word-limit
              placeholder="请输入可信网站的 HTTPS 商品详情页"
              @input="markPurchaseReferenceTouched"
            />
            <div class="form-help">{{ purchaseReferenceHostHint }}</div>
          </el-form-item>
          <el-form-item label="购买说明" prop="purchaseReferenceNote">
            <el-input
              v-model="form.purchaseReferenceNote"
              :disabled="referenceInputDisabled"
              type="textarea"
              :rows="3"
              maxlength="500"
              show-word-limit
              placeholder="填写规格、容量、颜色、型号等购买注意事项"
              @input="markPurchaseReferenceTouched"
            />
          </el-form-item>
          <el-form-item v-if="form.oeItemId" label="最后维护">
            <span class="reference-audit">
              {{ displayText(form.purchaseReferenceUpdatedBy) }} · {{ formatReferenceTime(form.purchaseReferenceUpdatedTime) }}
            </span>
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
          <el-button size="small" @click="drawerOpen = false">取消</el-button>
          <el-button v-if="!readonlyMode" v-hasPermi="['inv:oe:add','inv:oe:edit']" type="primary" size="small" :loading="saving" @click="doSave">保存</el-button>
        </div>
      </div>
    </el-drawer>

    <el-dialog title="导入OE资料" :visible.sync="importOpen" width="520px" :close-on-click-modal="false" append-to-body>
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
        <el-button v-if="!readonlyMode" v-hasPermi="['inv:oe:import']" type="primary" :loading="importing" @click="doImport">确认导入</el-button>
      </div>
    </el-dialog>
  </div>
</template>

<script>
import { listOe, getOe, getOePurchaseReferencePolicy, addOe, updateOe, delOe, importOeData, oeCategoryTree } from "@/api/inventory/oe"
import { listSupplier } from "@/api/inventory/supplier"

export default {
  name: "InvOe",
  data() {
    const validatePurchaseReferenceUrl = (rule, value, callback) => {
      if (!value) return callback()
      let url
      try {
        url = new URL(value)
      } catch (error) {
        return callback(new Error("同款购买链接格式不正确"))
      }
      if (url.protocol !== "https:") return callback(new Error("同款购买链接必须使用 HTTPS"))
      if (!url.pathname || url.pathname === "/") return callback(new Error("请输入具体商品或采购页面，不能填写网站首页"))
      const allowedHosts = this.purchaseReferencePolicy.allowedHosts || []
      if (this.purchaseReferencePolicy.configured === true && !allowedHosts.some(host => url.hostname === host || url.hostname.endsWith("." + host))) {
        return callback(new Error("同款购买链接域名不在可信白名单"))
      }
      callback()
    }
    return {
      loading: false,
      saving: false,
      importing: false,
      supplierLoading: false,
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
      detailOe: null,
      total: 0,
      list: [],
      categoryOptions: [],
      supplierOptions: [],
      purchaseReferencePolicy: { configured: null, allowedHosts: [] },
      purchaseReferencePolicyError: "",
      queryParams: {
        pageNum: 1,
        pageSize: 10,
        categoryId: undefined,
        oeItemName: undefined,
        oeItemCode: undefined,
        supplierName: undefined,
        status: undefined,
        purchaseReferenceStatus: undefined
      },
      form: this.emptyForm(),
      importForm: { fileList: [], uploadFile: null, updateSupport: false },
      rules: {
        categoryId: [{ required: true, message: "请选择OE分类", trigger: "change" }],
        oeItemName: [{ required: true, message: "请输入物品名称", trigger: "blur" }],
        supplierName: [{ required: true, message: "请选择供应商", trigger: "change" }],
        purchaseReferenceUrl: [{ validator: validatePurchaseReferenceUrl, trigger: "blur" }]
      }
    }
  },
  computed: {
    readonlyMode() {
      return this.$route.query.mode === "readonly" || this.$route.query.readonly === "true"
    },
    drawerTitle() {
      return this.form.oeItemId ? "编辑OE" : "新增OE"
    },
    selectedCategoryName() {
      return this.selectedCategory && this.selectedCategory.categoryId ? this.selectedCategory.categoryFullPath : "全部OE"
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
      return this.list.filter(item => Number(item.costPrice || 0) > 0).length
    },
    metricReferenceReady() {
      return this.list.filter(item => item.purchaseReferenceStatus === "COMPLETE").length
    },
    formPurchaseReferenceStatus() {
      if (this.form.purchaseReferenceStatus === "NOT_FIXED_ASSET") return "NOT_FIXED_ASSET"
      const requiredValues = [
        this.form.oeItemCode,
        this.form.oeItemName,
        this.form.itemDescription,
        this.form.orderUnit,
        this.form.imageUrl,
        this.form.purchaseReferenceNote
      ]
      return requiredValues.every(value => String(value || "").trim()) &&
        /^https:\/\//i.test(this.form.purchaseReferenceUrl || "")
        ? "COMPLETE" : "INCOMPLETE"
    },
    referenceInputDisabled() {
      return this.purchaseReferencePolicy.configured === false
    },
    purchaseReferenceHostHint() {
      if (this.purchaseReferencePolicy.configured === false) return "请先配置可信购买域名白名单"
      const hosts = this.purchaseReferencePolicy.allowedHosts || []
      return hosts.length ? "允许域名：" + hosts.join("、") : "仅允许 HTTPS 具体商品页面，保存时由后端再次校验"
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
    this.loadPurchaseReferencePolicy()
    this.getList()
  },
  methods: {
    emptyForm() {
      return {
        oeItemId: undefined,
        oeItemCode: "",
        categoryId: undefined,
        categoryName: "",
        oeTypeName: "",
        oeItemName: "",
        itemDescription: "",
        orderUnit: "",
        costPrice: 0,
        supplierName: "",
        supplierPhone: "",
        imageUrl: "",
        purchaseReferenceUrl: "",
        purchaseReferenceNote: "",
        purchaseReferenceUpdatedBy: "",
        purchaseReferenceUpdatedTime: null,
        purchaseReferenceStatus: "NOT_FIXED_ASSET",
        purchaseReferenceTouched: false,
        status: "0",
        remark: ""
      }
    },
    loadCategories() {
      this.categoryLoading = true
      return oeCategoryTree().then(res => {
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
    loadPurchaseReferencePolicy() {
      this.purchaseReferencePolicyError = ""
      return getOePurchaseReferencePolicy().then(res => {
        this.purchaseReferencePolicy = Object.assign({ configured: false, allowedHosts: [] }, res.data || {})
      }).catch(() => {
        this.purchaseReferencePolicy = { configured: null, allowedHosts: [] }
        this.purchaseReferencePolicyError = "可信域名规则加载失败，最终以保存时的后端校验为准"
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
      return [{ categoryId: 0, categoryName: "全部OE", categoryFullPath: "全部OE", children: children || [] }]
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
      listOe(this.queryParams).then(res => {
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
      this.queryParams = { pageNum: 1, pageSize: 10, categoryId: undefined, oeItemName: undefined, oeItemCode: undefined, supplierName: undefined, status: undefined, purchaseReferenceStatus: undefined }
      this.selectCategoryRoot()
      this.getList()
    },
    openForm(row, focusReference) {
      if (this.readonlyMode) return
      if (row && row.oeItemId) {
        getOe(row.oeItemId).then(res => {
          this.form = Object.assign(this.emptyForm(), res.data || row, { purchaseReferenceTouched: false })
          this.ensureSelectedSupplier()
          this.drawerOpen = true
          if (focusReference) this.focusPurchaseReference()
        })
      } else {
        this.form = this.emptyForm()
        this.ensureSelectedSupplier()
        this.drawerOpen = true
        if (focusReference) this.focusPurchaseReference()
      }
    },
    openDetail(row) {
      this.detailOe = Object.assign(this.emptyForm(), row || {})
      this.detailOpen = true
      getOe(row.oeItemId).then(res => {
        this.detailOe = Object.assign(this.emptyForm(), res.data || row)
      })
    },
    handleStatusTabChange(status) {
      this.queryParams.status = status || undefined
      this.handleQuery()
    },
    focusPurchaseReference() {
      this.$nextTick(() => {
        const input = this.$refs.purchaseReferenceUrlInput
        if (input && typeof input.focus === "function") input.focus()
      })
    },
    markPurchaseReferenceTouched() {
      this.form.purchaseReferenceTouched = true
    },
    doSave() {
      this.$refs.formRef.validate(valid => {
        if (!valid) return
        this.saving = true
        const request = this.form.oeItemId ? updateOe(this.form) : addOe(this.form)
        request.then(() => {
          this.$modal.msgSuccess("保存成功")
          this.drawerOpen = false
          this.getList()
        }).finally(() => {
          this.saving = false
        })
      })
    },
    handleDelete(row) {
      this.$modal.confirm("确认删除OE「" + row.oeItemName + "」？").then(() => delOe(row.oeItemId)).then(() => {
        this.$modal.msgSuccess("删除成功")
        this.getList()
      })
    },
    loadSuppliersOnOpen(open) {
      if (open) this.loadSuppliers()
    },
    loadSuppliers() {
      if (this.supplierOptions.length > 0) return Promise.resolve(this.supplierOptions)
      this.supplierLoading = true
      return listSupplier({ pageNum: 1, pageSize: 1000, status: "0", cooperationStatus: "0" }).then(res => {
        this.supplierOptions = res.rows || []
        return this.supplierOptions
      }).finally(() => {
        this.supplierLoading = false
      })
    },
    ensureSelectedSupplier() {
      if (this.form.supplierName && !this.supplierOptions.some(item => item.supplierName === this.form.supplierName)) {
        this.supplierOptions = [{ supplierId: "selected-" + this.form.supplierName, supplierName: this.form.supplierName, contactPhone: this.form.supplierPhone }].concat(this.supplierOptions)
      }
    },
    handleSupplierChange(supplierName) {
      const supplier = this.supplierOptions.find(item => item.supplierName === supplierName)
      this.form.supplierPhone = supplier ? supplier.contactPhone || "" : ""
    },
    supplierLabel(item) {
      return item.supplierName + (item.supplierCode ? " / " + item.supplierCode : "")
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
      importOeData(data).then(() => {
        this.$modal.msgSuccess("导入成功")
        this.importOpen = false
        this.getList()
      }).finally(() => {
        this.importing = false
      })
    },
    downloadTemplate() {
      this.download("inventory/oe/importTemplate", {}, this.exportFileName("OE导入模板"))
    },
    handleExport() {
      this.download("inventory/oe/export", { ...this.queryParams }, this.exportFileName("OE资料"))
    },
    displayText(value) {
      return value === undefined || value === null || value === "" ? "-" : value
    },
    imageUrl(row) {
      return row && row.imageUrl ? row.imageUrl : ""
    },
    statusLabel(status) {
      return status === "0" ? "正常" : "停用"
    },
    statusTagType(status) {
      return status === "0" ? "success" : "info"
    },
    purchaseReferenceStatusLabel(status) {
      if (status === "COMPLETE") return "已完善"
      if (status === "INCOMPLETE") return "待完善"
      return "非固定资产"
    },
    purchaseReferenceStatusType(status) {
      if (status === "COMPLETE") return "success"
      if (status === "INCOMPLETE") return "warning"
      return "info"
    },
    safePurchaseReferenceUrl(value) {
      return /^https:\/\//i.test(value || "") ? value : ""
    },
    formatReferenceTime(value) {
      return value ? this.parseTime(value, "{y}-{m}-{d} {h}:{i}") : "-"
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

  .price-value,
  .reference-value {
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

  .drawer-section-heading {
    display: flex;
    align-items: flex-start;
    justify-content: space-between;
    gap: 12px;
    margin: 18px 0 14px;
    padding-top: 16px;
    border-top: 1px solid #e5eaf1;
  }

  .drawer-section-heading strong {
    color: #1f2937;
  }

  .drawer-section-heading p {
    margin: 5px 0 0;
    color: #64748b;
    font-size: 12px;
    line-height: 18px;
  }

  .reference-policy-alert {
    margin-bottom: 14px;
  }

  .form-help,
  .reference-audit {
    color: #8492a6;
    font-size: 12px;
    line-height: 18px;
  }

  .full-width {
    width: 100%;
  }

  .option-meta {
    float: right;
    color: #909399;
    font-size: 12px;
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
