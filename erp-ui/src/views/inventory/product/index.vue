<template>
  <div class="app-container product-page">
    <inventory-page-hero
      title="商品管理"
      eyebrow="仓库资料"
      description="统一维护商品编码、分类、规格与价格，让采购、库存与销售始终使用同一份主数据。"
      scope-text="统一商品资料"
      icon="el-icon-goods"
      :features="['商品主档', '分类体系', '成本价格']"
    />
    <div class="product-layout" :class="{ 'is-category-collapsed': categoryCollapsed }">
      <aside class="category-panel">
        <el-card shadow="never" class="category-card">
          <div slot="header" class="card-header">
            <span>商品分类</span>
            <div class="card-actions">
              <el-tooltip content="刷新分类" placement="top">
                <el-button v-hasPermi="['inv:category:list', 'inv:category:tree']" type="text" size="mini" icon="el-icon-refresh" @click="loadCategories">刷新</el-button>
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
            placeholder="搜索分类"
            class="category-search"
          />
          <el-tree
            ref="categoryTree"
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
            <div>筛选到 {{ total }} 个商品</div>
          </div>
        </el-card>
      </aside>

      <main class="product-main">
        <div class="collapsed-category-action">
          <el-button size="mini" icon="el-icon-d-arrow-right" @click="toggleCategoryPanel(false)">展开分类</el-button>
        </div>
        <el-card shadow="never" class="search-card mb12">
          <el-form :model="queryParams" inline size="small" class="query-form">
            <el-form-item label="商品名称">
              <el-input v-model="queryParams.productName" placeholder="商品名称" clearable @keyup.enter.native="handleQuery" />
            </el-form-item>
            <el-form-item label="商品编码">
              <el-input v-model="queryParams.productCode" placeholder="商品编码" clearable @keyup.enter.native="handleQuery" />
            </el-form-item>
            <el-form-item label="等级">
              <el-input v-model="queryParams.grade" placeholder="等级" clearable @keyup.enter.native="handleQuery" />
            </el-form-item>
            <el-form-item label="供应商">
              <el-input v-model="queryParams.supplierName" placeholder="供应商名称" clearable @keyup.enter.native="handleQuery" />
            </el-form-item>
            <el-form-item label="状态">
              <el-select v-model="queryParams.status" clearable placeholder="全部" @change="handleQuery">
                <el-option label="正常" value="0" />
                <el-option label="停用" value="1" />
              </el-select>
            </el-form-item>
            <el-form-item class="query-actions">
              <el-button v-hasPermi="['inv:product:list']" type="primary" size="mini" icon="el-icon-search" @click="handleQuery">搜索</el-button>
              <el-button size="mini" icon="el-icon-refresh-left" @click="resetQuery">重置</el-button>
              <el-button v-if="canOperateWarehouseProduct" v-hasPermi="['inv:product:add']" size="mini" icon="el-icon-plus" @click="openForm(null)">新增商品</el-button>
              <el-button v-if="canOperateWarehouseProduct" v-hasPermi="['inv:product:import']" size="mini" icon="el-icon-upload2" @click="handleImport">导入</el-button>
              <el-button v-if="canOperateWarehouseProduct" v-hasPermi="['inv:product:export']" size="mini" icon="el-icon-download" @click="handleExport">导出</el-button>
              <el-button v-if="canOperateWarehouseProduct" v-hasPermi="['inv:product:import']" size="mini" icon="el-icon-document" @click="downloadTemplate">下载模板</el-button>
            </el-form-item>
          </el-form>
          <div v-if="canOperateWarehouseProduct" class="metric-row">
            <div v-for="item in metrics" :key="item.label" class="metric-card">
              <div class="metric-value">{{ item.value }}</div>
              <div class="metric-label">{{ item.label }}</div>
            </div>
          </div>
        </el-card>

        <el-card shadow="never" class="table-card">
          <div slot="header" class="card-header">
            <div class="table-title">
              <span>{{ selectedCategoryName }}</span>
            </div>
            <el-button v-hasPermi="['inv:product:list']" type="text" size="mini" icon="el-icon-refresh" @click="getList">刷新</el-button>
          </div>
          <el-table ref="productTable" v-loading="loading" :data="list" size="small" stripe>
            <template v-if="isProductReadonlyMode">
              <el-table-column type="index" :index="readonlyRowIndex" label="序号" width="70" align="center" />
              <el-table-column label="上线分类" min-width="150" show-overflow-tooltip>
                <template slot-scope="scope">
                  <span>{{ displayText(getProductCategoryName(scope.row)) }}</span>
                </template>
              </el-table-column>
              <el-table-column label="产品类别名称" prop="productName" min-width="180" show-overflow-tooltip />
              <el-table-column label="等级" prop="grade" width="90" show-overflow-tooltip />
              <el-table-column label="规格" prop="spec" min-width="130" show-overflow-tooltip />
              <el-table-column label="产品描述" prop="productDescription" min-width="240" show-overflow-tooltip>
                <template slot-scope="scope">
                  <span>{{ displayText(scope.row.productDescription) }}</span>
                </template>
              </el-table-column>
              <el-table-column label="补货单位" prop="unit" width="100" />
              <el-table-column label="参考成本价" prop="costPrice" width="120" align="right">
                <template slot-scope="scope">
                  <span class="price-text">{{ formatMoney(scope.row.costPrice) }}</span>
                </template>
              </el-table-column>
            </template>
            <template v-else>
              <el-table-column label="商品编码" prop="productCode" width="140" show-overflow-tooltip />
              <el-table-column label="商品名称" prop="productName" min-width="180" show-overflow-tooltip />
              <el-table-column label="商品分类" min-width="170" show-overflow-tooltip>
                <template slot-scope="scope">
                  <div class="category-cell">
                    <span class="category-cell-name">{{ getProductCategoryName(scope.row) }}</span>
                    <span v-if="getProductCategoryPath(scope.row)" class="category-cell-path">{{ getProductCategoryPath(scope.row) }}</span>
                  </div>
                </template>
              </el-table-column>
              <el-table-column label="等级" prop="grade" width="90" show-overflow-tooltip />
              <el-table-column label="规格" prop="spec" width="120" show-overflow-tooltip />
              <el-table-column label="单位" prop="unit" width="70" />
              <el-table-column v-if="canViewCostFields" label="采购价" prop="purchasePrice" width="100" align="right">
                <template slot-scope="scope">
                  <span class="price-text">{{ formatMoney(scope.row.purchasePrice) }}</span>
                </template>
              </el-table-column>
              <el-table-column label="成本价" prop="costPrice" width="100" align="right">
                <template slot-scope="scope">
                  <span class="price-text">{{ formatMoney(scope.row.costPrice) }}</span>
                </template>
              </el-table-column>
              <el-table-column label="供应商" prop="supplierName" min-width="150" show-overflow-tooltip />
              <el-table-column label="状态" prop="status" width="80">
                <template slot-scope="scope">
                  <el-tag :type="scope.row.status === '0' ? 'success' : 'danger'" size="mini">
                    {{ scope.row.status === '0' ? '正常' : '停用' }}
                  </el-tag>
                </template>
              </el-table-column>
              <el-table-column v-if="canOperateWarehouseProduct" label="操作" width="210" fixed="right">
                <template slot-scope="scope">
                  <el-button v-hasPermi="['inv:product:query']" type="text" size="mini" icon="el-icon-view" @click="openDetail(scope.row)">详情</el-button>
                  <el-button v-hasPermi="['inv:product:edit']" type="text" size="mini" icon="el-icon-edit" @click="openForm(scope.row)">编辑</el-button>
                  <el-button v-hasPermi="['inv:product:remove']" type="text" size="mini" icon="el-icon-delete" class="danger-action" @click="handleDelete(scope.row)">删除</el-button>
                </template>
              </el-table-column>
            </template>
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

    <el-drawer
      :title="drawerTitle"
      :visible.sync="drawerOpen"
      size="min(560px, 100vw)"
      custom-class="product-drawer"
      append-to-body
      :close-on-click-modal="false"
    >
      <div class="drawer-body">
        <div v-if="detailMode" class="readonly-form">
          <div class="detail-hero">
            <div class="detail-hero-icon">
              <i class="el-icon-goods" />
            </div>
            <div class="detail-hero-main">
              <div class="detail-hero-eyebrow">商品主档</div>
              <div class="detail-hero-title">{{ displayText(form.productName) }}</div>
              <div class="detail-hero-meta">
                <span>{{ displayText(form.productCode) }}</span>
                <span>{{ displayText(getProductCategoryName(form)) }}</span>
                <span>{{ displayText(form.spec) }}</span>
              </div>
            </div>
            <el-tag
              class="detail-status"
              :type="form.status === '1' ? 'info' : 'success'"
              size="small"
              effect="plain"
            >
              {{ form.status === "1" ? "已停用" : "正常使用" }}
            </el-tag>
          </div>

          <section class="detail-section">
            <div class="detail-section-heading">
              <span class="detail-section-icon"><i class="el-icon-document" /></span>
              <div>
                <div class="detail-section-title">基础资料</div>
                <div class="detail-section-caption">商品身份与分类信息</div>
              </div>
            </div>
            <div class="detail-grid">
              <div class="detail-item">
                <span class="detail-label">商品名称</span>
                <span class="detail-value">{{ displayText(form.productName) }}</span>
              </div>
              <div class="detail-item">
                <span class="detail-label">商品编码</span>
                <span class="detail-value">{{ displayText(form.productCode) }}</span>
              </div>
              <div class="detail-item is-wide">
                <span class="detail-label">所属分类</span>
                <span class="detail-value">{{ displayText(getProductCategoryPath(form) || getProductCategoryName(form)) }}</span>
              </div>
              <div class="detail-item">
                <span class="detail-label">等级</span>
                <span class="detail-value">{{ displayText(form.grade) }}</span>
              </div>
              <div class="detail-item">
                <span class="detail-label">内部茶种名</span>
                <span class="detail-value">{{ displayText(form.internalTeaName) }}</span>
              </div>
              <div class="detail-item">
                <span class="detail-label">状态</span>
                <span class="detail-value">{{ form.status === "1" ? "停用" : "正常" }}</span>
              </div>
              <div class="detail-item is-wide">
                <span class="detail-label">备注</span>
                <span class="detail-value">{{ displayText(form.remark) }}</span>
              </div>
            </div>
          </section>

          <section class="detail-section">
            <div class="detail-section-heading">
              <span class="detail-section-icon is-amber"><i class="el-icon-coin" /></span>
              <div>
                <div class="detail-section-title">价格规格</div>
                <div class="detail-section-caption">采购、销售与库存参考</div>
              </div>
            </div>
            <div class="detail-grid">
              <div class="detail-item">
                <span class="detail-label">规格型号</span>
                <span class="detail-value">{{ displayText(form.spec) }}</span>
              </div>
              <div class="detail-item">
                <span class="detail-label">尺寸</span>
                <span class="detail-value">{{ displayText(form.size) }}</span>
              </div>
              <div class="detail-item">
                <span class="detail-label">计量单位</span>
                <span class="detail-value">{{ displayText(form.unit) }}</span>
              </div>
              <div v-if="canViewCostFields" class="detail-item">
                <span class="detail-label">采购参考价</span>
                <span class="detail-value price-text">{{ formatMoney(form.purchasePrice) }}</span>
              </div>
              <div class="detail-item">
                <span class="detail-label">售价250g</span>
                <span class="detail-value price-text">{{ formatMoney(form.salePrice250g) }}</span>
              </div>
              <div class="detail-item">
                <span class="detail-label">售价500g</span>
                <span class="detail-value price-text">{{ displaySalePrice500g() }}</span>
              </div>
              <div class="detail-item">
                <span class="detail-label">销售参考价</span>
                <span class="detail-value price-text">{{ formatMoney(form.salesPrice) }}</span>
              </div>
              <div class="detail-item">
                <span class="detail-label">参考成本价</span>
                <span class="detail-value price-text">{{ formatMoney(form.costPrice) }}</span>
              </div>
              <div class="detail-item">
                <span class="detail-label">库存下限</span>
                <span class="detail-value">{{ formatMoney(form.safetyStockMin) }}</span>
              </div>
              <div class="detail-item">
                <span class="detail-label">库存上限</span>
                <span class="detail-value">{{ formatMoney(form.safetyStockMax) }}</span>
              </div>
            </div>
          </section>

          <section class="detail-section">
            <div class="detail-section-heading">
              <span class="detail-section-icon is-violet"><i class="el-icon-picture-outline" /></span>
              <div>
                <div class="detail-section-title">图文素材</div>
                <div class="detail-section-caption">商品描述与图片资源</div>
              </div>
            </div>
            <div class="detail-grid">
              <div class="detail-item is-wide">
                <span class="detail-label">产品描述</span>
                <span class="detail-value detail-text">{{ displayText(form.productDescription) }}</span>
              </div>
              <div class="detail-media-gallery is-wide">
                <div v-for="item in productImageItems" :key="item.key" class="detail-media-card" :class="{ 'is-empty': !item.url }">
                  <span class="detail-media-label">{{ item.label }}</span>
                  <el-image
                    v-if="item.url"
                    :src="item.url"
                    fit="cover"
                    :preview-src-list="productImagePreviewUrls"
                  />
                  <div v-else class="detail-media-empty">
                    <i class="el-icon-picture-outline" />
                    <span>暂未上传</span>
                  </div>
                </div>
              </div>
            </div>
          </section>

          <section class="detail-section">
            <div class="detail-section-heading">
              <span class="detail-section-icon is-green"><i class="el-icon-office-building" /></span>
              <div>
                <div class="detail-section-title">供应商信息</div>
                <div class="detail-section-caption">采购来源与联系方式</div>
              </div>
            </div>
            <div class="detail-grid">
              <div class="detail-item">
                <span class="detail-label">供应商名称</span>
                <span class="detail-value">{{ displayText(form.supplierName) }}</span>
              </div>
              <div class="detail-item">
                <span class="detail-label">供应商电话</span>
                <span class="detail-value">{{ displayText(form.supplierPhone) }}</span>
              </div>
              <div class="detail-item is-wide">
                <span class="detail-label">采购备注</span>
                <span class="detail-value detail-text">{{ displayText(form.supplierRemark) }}</span>
              </div>
            </div>
          </section>
        </div>

        <el-form v-else ref="formRef" :model="form" :rules="rules" label-width="108px" size="small">
          <el-tabs v-model="activeTab">
            <el-tab-pane label="基础资料" name="base">
              <el-form-item label="商品名称" prop="productName">
                <el-input v-model="form.productName" maxlength="128" placeholder="请输入商品名称" />
              </el-form-item>
              <el-form-item label="商品编码" prop="productCode">
                <el-input v-model="form.productCode" disabled placeholder="保存后按所属分类自动生成" />
                <div class="form-tip">格式：分类编码-随机数字，分类变化时系统会重新生成。</div>
              </el-form-item>
              <el-form-item label="所属分类" prop="categoryId">
                <el-select v-model="form.categoryId" filterable placeholder="请先在分类管理配置，再选择分类" class="full-width">
                  <el-option
                    v-for="c in categoryOptions"
                    :key="c.categoryId"
                    :label="c.categoryFullPath"
                    :value="c.categoryId"
                    :disabled="c.status !== '0'"
                  />
                </el-select>
              </el-form-item>
              <el-form-item label="等级">
                <el-input v-model="form.grade" maxlength="32" placeholder="如：一级、特级" />
              </el-form-item>
              <el-form-item label="内部茶种名">
                <el-input v-model="form.internalTeaName" maxlength="128" placeholder="请输入内部茶种名" />
              </el-form-item>
              <el-form-item label="状态">
                <el-radio-group v-model="form.status">
                  <el-radio label="0">正常</el-radio>
                  <el-radio label="1">停用</el-radio>
                </el-radio-group>
              </el-form-item>
              <el-form-item label="备注">
                <el-input v-model="form.remark" type="textarea" :rows="3" maxlength="500" show-word-limit placeholder="请输入备注" />
              </el-form-item>
            </el-tab-pane>

            <el-tab-pane label="价格规格" name="price">
              <el-form-item label="规格型号">
                <el-input v-model="form.spec" maxlength="256" placeholder="请输入规格型号" />
              </el-form-item>
              <el-form-item label="尺寸">
                <el-input v-model="form.size" maxlength="64" placeholder="请输入尺寸" />
              </el-form-item>
              <el-form-item label="计量单位">
                <el-input v-model="form.unit" maxlength="32" placeholder="如：斤、罐、盒" />
              </el-form-item>
              <el-form-item v-if="canViewCostFields" label="采购参考价">
                <el-input-number v-model="form.purchasePrice" :min="0" :precision="2" class="full-width" />
              </el-form-item>
              <el-form-item label="售价250g">
                <el-input-number v-model="form.salePrice250g" :min="0" :precision="2" class="full-width" />
              </el-form-item>
              <el-form-item label="售价500g">
                <el-input-number v-model="form.salePrice500g" :min="0" :precision="2" class="full-width" />
              </el-form-item>
              <el-form-item label="销售参考价">
                <el-input-number v-model="form.salesPrice" :min="0" :precision="2" class="full-width" />
              </el-form-item>
              <el-form-item label="参考成本价">
                <el-input-number v-model="form.costPrice" :min="0" :precision="2" class="full-width" />
              </el-form-item>
              <el-row :gutter="12">
                <el-col :span="12">
                  <el-form-item label="库存下限">
                    <el-input-number v-model="form.safetyStockMin" :min="0" :precision="2" class="full-width" />
                  </el-form-item>
                </el-col>
                <el-col :span="12">
                  <el-form-item label="库存上限">
                    <el-input-number v-model="form.safetyStockMax" :min="0" :precision="2" class="full-width" />
                  </el-form-item>
                </el-col>
              </el-row>
            </el-tab-pane>

            <el-tab-pane label="图文素材" name="media">
              <el-form-item label="产品描述">
                <el-input v-model="form.productDescription" type="textarea" :rows="5" maxlength="2000" show-word-limit placeholder="请输入产品描述" />
              </el-form-item>
              <el-alert
                class="image-upload-alert"
                title="点击图片区域即可上传，支持 JPG、PNG、JPEG，每项最多 1 张、单张不超过 5MB。"
                type="info"
                :closable="false"
                show-icon
              />
              <div class="product-image-upload-grid">
                <el-form-item label="主图" class="product-image-upload-item">
                  <image-upload v-model="form.imageUrl" action="/inventory/image/upload" accept=".jpg,.jpeg,.png,image/jpeg,image/png" :limit="1" :file-size="5" :drag="false" :delete-on-remove="false" :is-show-tip="false" />
                </el-form-item>
                <el-form-item label="外包装图" class="product-image-upload-item">
                  <image-upload v-model="form.packageImageUrl" action="/inventory/image/upload" accept=".jpg,.jpeg,.png,image/jpeg,image/png" :limit="1" :file-size="5" :drag="false" :delete-on-remove="false" :is-show-tip="false" />
                </el-form-item>
                <el-form-item label="干茶图" class="product-image-upload-item">
                  <image-upload v-model="form.dryTeaImageUrl" action="/inventory/image/upload" accept=".jpg,.jpeg,.png,image/jpeg,image/png" :limit="1" :file-size="5" :drag="false" :delete-on-remove="false" :is-show-tip="false" />
                </el-form-item>
                <el-form-item label="茶汤图" class="product-image-upload-item">
                  <image-upload v-model="form.teaSoupImageUrl" action="/inventory/image/upload" accept=".jpg,.jpeg,.png,image/jpeg,image/png" :limit="1" :file-size="5" :drag="false" :delete-on-remove="false" :is-show-tip="false" />
                </el-form-item>
                <el-form-item label="叶底图" class="product-image-upload-item">
                  <image-upload v-model="form.leafBottomImageUrl" action="/inventory/image/upload" accept=".jpg,.jpeg,.png,image/jpeg,image/png" :limit="1" :file-size="5" :drag="false" :delete-on-remove="false" :is-show-tip="false" />
                </el-form-item>
                <el-form-item label="补充图片" class="product-image-upload-item">
                  <image-upload v-model="form.extraImageUrl" action="/inventory/image/upload" accept=".jpg,.jpeg,.png,image/jpeg,image/png" :limit="1" :file-size="5" :drag="false" :delete-on-remove="false" :is-show-tip="false" />
                </el-form-item>
              </div>
            </el-tab-pane>

            <el-tab-pane label="供应商信息" name="supplier">
              <el-form-item label="供应商名称" prop="supplierName">
                <el-select
                  v-model="form.supplierName"
                  filterable
                  clearable
                  class="full-width"
                  placeholder="请先在供应商管理维护，再选择供应商"
                  :loading="supplierLoading"
                  @change="handleSupplierChange"
                  @visible-change="opened => opened && ensureSupplierOptions()"
                >
                  <el-option
                    v-for="supplier in supplierOptions"
                    :key="supplier.supplierId"
                    :label="formatSupplierLabel(supplier)"
                    :value="supplier.supplierName"
                    :disabled="supplier._historical === true"
                  >
                    <div class="supplier-option">
                      <span class="supplier-option-name">{{ supplier.supplierName }}</span>
                      <span class="supplier-option-meta">{{ supplier.supplierCode || "无编码" }} · {{ supplier.contactPhone || "无电话" }}</span>
                    </div>
                  </el-option>
                </el-select>
                <div v-if="supplierLoadError" role="alert">{{ supplierLoadError }} <el-button type="text" :disabled="supplierLoading" @click="loadSuppliers">重试</el-button></div>
                <div class="form-tip">没有选项时，请先到供应商管理新增并启用供应商。</div>
              </el-form-item>
              <el-form-item label="供应商电话">
                <el-input v-model="form.supplierPhone" disabled placeholder="选择供应商后自动带出" />
              </el-form-item>
              <el-form-item label="采购备注">
                <el-input v-model="form.supplierRemark" type="textarea" :rows="5" maxlength="500" show-word-limit placeholder="请输入采购备注" />
              </el-form-item>
            </el-tab-pane>
          </el-tabs>
        </el-form>
        <div class="drawer-footer">
          <el-button size="small" @click="drawerOpen = false">{{ detailMode ? "关闭" : "取消" }}</el-button>
          <el-button v-if="!detailMode && canOperateWarehouseProduct" v-hasPermi="['inv:product:add','inv:product:edit']" type="primary" size="small" :loading="saveLoading" @click="doSave">保存</el-button>
        </div>
      </div>
    </el-drawer>

    <el-dialog title="导入商品数据" :visible.sync="importOpen" width="520px" append-to-body :close-on-click-modal="false">
      <el-alert
        type="info"
        :closable="false"
        show-icon
        title="支持普通商品模板，也支持茶叶分类 Excel；茶叶 Excel 会自动识别上线分类、等级、规格、250g/500g 售价、供应商和内部茶种名。"
        class="import-alert"
      />
      <el-form :model="importForm" label-width="100px">
        <el-form-item label="上传文件">
          <el-upload
            ref="uploadRef"
            :action="''"
            :auto-upload="false"
            :limit="1"
            accept=".xlsx,.xls"
            :on-change="onFileChange"
            :on-remove="onFileRemove"
            :file-list="importForm.fileList"
          >
            <el-button size="small" icon="el-icon-upload2">选择 Excel 文件</el-button>
            <span slot="tip" class="upload-tip">仅支持 .xlsx / .xls 格式</span>
          </el-upload>
        </el-form-item>
        <el-form-item label="更新模式">
          <el-checkbox v-model="importForm.updateSupport">已存在时更新数据</el-checkbox>
        </el-form-item>
      </el-form>
      <div slot="footer">
        <el-button @click="importOpen = false">取消</el-button>
        <el-button v-if="canOperateWarehouseProduct" v-hasPermi="['inv:product:import']" type="primary" :loading="importLoading" @click="doImport">确认导入</el-button>
      </div>
    </el-dialog>
  </div>
</template>

<script>
import { listProduct, getProduct, addProduct, updateProduct, delProduct, importData } from "@/api/inventory/product"
import { categoryTree } from "@/api/inventory/category"
import { listSupplier } from "@/api/inventory/supplier"
import { confirmExportAction, confirmTemplateDownload } from "@/utils/exportConfirm"
import { getSelectedDeptContext } from "@/utils/shopContext"

const { mergeSelectedSupplier, invalidateSupplierOptions, loadSupplierOptions } = require("@/utils/supplierOptionState")

export default {
  name: "InvProduct",
  data() {
    return {
      loading: false,
      saveLoading: false,
      importLoading: false,
      supplierLoading: false, supplierLoaded: false, supplierLoadError: "", supplierRequestSequence: 0,
      categoryCollapsed: false,
      drawerOpen: false,
      detailMode: false,
      importOpen: false,
      activeTab: "base",
      categoryKeyword: "",
      categoryLoadError: "",
      categoryTreeData: [{ categoryId: 0, categoryName: "全部商品", categoryFullPath: "全部商品", children: [] }],
      categoryOptions: [],
      categoryMap: {},
      supplierOptions: [],
      categoryProps: { children: "children", label: "categoryName" },
      selectedCategory: null,
      total: 0,
      list: [],
      queryParams: {
        pageNum: 1,
        pageSize: 10,
        productName: undefined,
        productCode: undefined,
        categoryId: undefined,
        grade: undefined,
        supplierName: undefined,
        status: undefined
      },
      form: this.emptyForm(),
      importForm: { fileList: [], uploadFile: null, updateSupport: false },
      rules: {
        productName: [{ required: true, message: "请输入商品名称", trigger: "blur" }],
        categoryId: [
          { required: true, message: "请选择所属分类", trigger: "change" },
          {
            validator: (rule, value, callback) => {
              const categoryId = Number(value)
              if (!value || categoryId <= 0) {
                callback(new Error("请选择有效商品分类"))
                return
              }
              if (!this.categoryMap[categoryId]) {
                callback(new Error("商品分类已不存在，请刷新分类后重新选择"))
                return
              }
              callback()
            },
            trigger: "change"
          }
        ],
        supplierName: [{ required: true, message: "请选择供应商", trigger: "change" }],
        productCode: [{ max: 64, message: "编码不超过64个字符", trigger: "blur" }]
      }
    }
  },
  computed: {
    selectedDeptContext() {
      return getSelectedDeptContext()
    },
    canOperateWarehouseProduct() {
      return this.selectedDeptContext.isWarehouse
    },
    isProductReadonlyMode() {
      return !this.canOperateWarehouseProduct
    },
    drawerTitle() {
      if (this.detailMode) {
        return "商品详情"
      }
      return this.form.productId ? "编辑商品" : "新增商品"
    },
    selectedCategoryName() {
      return this.selectedCategory && this.selectedCategory.categoryId ? this.selectedCategory.categoryFullPath : "全部商品"
    },
    metrics() {
      const normalCount = this.list.filter(item => item.status === "0").length
      const incompleteCount = this.list.filter(item => !item.categoryId || !item.productCode || !item.supplierName || item.salesPrice == null).length
      const supplierCount = new Set(this.list.map(item => item.supplierName).filter(Boolean)).size
      return [
        { label: "商品总数", value: this.total },
        { label: "当前页正常", value: normalCount },
        { label: "当前页待完善", value: incompleteCount },
        { label: "当前页供应商", value: supplierCount }
      ]
    },
    canViewCostFields() {
      return this.$auth && this.$auth.hasPermi("inv:cost:view")
    },
    canMaintainProduct() {
      return this.canOperateWarehouseProduct && (!this.$auth || this.$auth.hasPermiOr(["inv:product:add", "inv:product:edit"]))
    },
    productImageItems() {
      return [
        { key: "imageUrl", label: "主图", url: this.form.imageUrl },
        { key: "packageImageUrl", label: "外包装图", url: this.form.packageImageUrl },
        { key: "dryTeaImageUrl", label: "干茶图", url: this.form.dryTeaImageUrl },
        { key: "teaSoupImageUrl", label: "茶汤图", url: this.form.teaSoupImageUrl },
        { key: "leafBottomImageUrl", label: "叶底图", url: this.form.leafBottomImageUrl },
        { key: "extraImageUrl", label: "补充图片", url: this.form.extraImageUrl }
      ]
    },
    productImagePreviewUrls() {
      return this.productImageItems.map(item => item.url).filter(Boolean)
    }
  },
  watch: {
    "$store.getters.id"() { this.invalidateSupplierCache() },
    "$store.getters.token"() { this.invalidateSupplierCache() },
    "$store.getters.permissions": { deep: true, handler() { this.invalidateSupplierCache() } },
    categoryKeyword(value) {
      if (this.$refs.categoryTree) {
        this.$refs.categoryTree.filter(value)
      }
    }
  },
  created() {
    this._supplierDeptChanged = () => this.invalidateSupplierCache()
    window.addEventListener("erp:dept-changed", this._supplierDeptChanged)
    this.loadCategories()
    this.getList()
  },
  beforeDestroy() {
    window.removeEventListener("erp:dept-changed", this._supplierDeptChanged)
    this.invalidateSupplierCache()
  },
  methods: {
    invalidateSupplierCache() { invalidateSupplierOptions(this) },
    supplierContext() {
      return { actorId: String((this.$store && this.$store.getters.id) || ""),
        deptId: String(getSelectedDeptContext().deptId || ""),
        permissions: (this.$store && this.$store.getters.permissions) || [] }
    },
    emptyForm() {
      return {
        productId: undefined,
        productName: "",
        productCode: "",
        categoryId: undefined,
        categoryName: "",
        grade: "",
        sku: "",
        spec: "",
        size: "",
        unit: "",
        purchasePrice: 0,
        salesPrice: 0,
        salePrice250g: undefined,
        salePrice500g: undefined,
        costPrice: 0,
        safetyStockMin: 0,
        safetyStockMax: 0,
        supplierName: "",
        supplierPhone: "",
        supplierRemark: "",
        internalTeaName: "",
        productDescription: "",
        barcode: "",
        imageUrl: "",
        packageImageUrl: "",
        dryTeaImageUrl: "",
        teaSoupImageUrl: "",
        leafBottomImageUrl: "",
        extraImageUrl: "",
        status: "0",
        remark: ""
      }
    },
    ensureWarehouseProductOperation() {
      if (this.canOperateWarehouseProduct) {
        return true
      }
      if (this.$modal && this.$modal.msgWarning) {
        this.$modal.msgWarning("没有仓库权限，仅可查看商品资料")
      }
      return false
    },
    readonlyRowIndex(index) {
      const pageNum = Number(this.queryParams.pageNum) || 1
      const pageSize = Number(this.queryParams.pageSize) || 10
      return (pageNum - 1) * pageSize + index + 1
    },
    flattenCategories(list) {
      const result = []
      const walk = items => {
        ;(items || []).forEach(item => {
          result.push(item)
          if (item.children && item.children.length) {
            walk(item.children)
          }
        })
      }
      walk(list)
      return result
    },
    decorateCategories(list, parentPath) {
      const pathPrefix = parentPath || []
      return (list || []).map(item => {
        const currentName = item.categoryName || ""
        const currentPath = pathPrefix.concat(currentName)
        const children = this.decorateCategories(item.children || [], currentPath)
        return Object.assign({}, item, {
          categoryFullPath: currentPath.filter(Boolean).join(" / "),
          children: children
        })
      })
    },
    buildCategoryMap(list) {
      const map = {}
      ;(list || []).forEach(item => {
        map[item.categoryId] = item
      })
      return map
    },
    getDefaultCategoryTree(children) {
      return [{ categoryId: 0, categoryName: "全部商品", categoryFullPath: "全部商品", children: children || [] }]
    },
    loadCategories() {
      return categoryTree().then(res => {
        this.categoryLoadError = ""
        const roots = this.decorateCategories(res.data || [])
        this.categoryTreeData = this.getDefaultCategoryTree(roots)
        this.categoryOptions = this.flattenCategories(roots)
        this.categoryMap = this.buildCategoryMap(this.categoryOptions)
        this.$nextTick(() => {
          if (this.$refs.categoryTree) {
            this.$refs.categoryTree.filter(this.categoryKeyword)
            this.$refs.categoryTree.setCurrentKey(this.queryParams.categoryId || 0)
          }
        })
      }).catch(() => {
        this.categoryLoadError = "分类加载失败，请联系管理员授权"
        this.categoryTreeData = this.getDefaultCategoryTree()
        this.categoryOptions = []
        this.categoryMap = {}
        this.$nextTick(() => {
          if (this.$refs.categoryTree) {
            this.$refs.categoryTree.setCurrentKey(0)
          }
        })
      })
    },
    ensureSupplierOptions() {
      if (!this.canMaintainProduct) {
        this.supplierOptions = []
        return Promise.resolve([])
      }
      if (this.supplierLoaded) {
        return Promise.resolve(this.supplierOptions)
      }
      return this.loadSuppliers()
    },
    loadSuppliers() {
      if (!this.canMaintainProduct) { this.invalidateSupplierCache(); return Promise.resolve([]) }
      return loadSupplierOptions(this, () => listSupplier({
        pageNum: 1, pageSize: 1000, status: "0", cooperationStatus: "0"
      }, { silentError: true }), () => this.supplierContext())
    },
    formatSupplierLabel(supplier) {
      if (!supplier) {
        return ""
      }
      const code = supplier.supplierCode ? "（" + supplier.supplierCode + "）" : ""
      return supplier.supplierName + code
    },
    handleSupplierChange(supplierName) {
      const supplier = this.supplierOptions.find(item => item.supplierName === supplierName)
      if (!supplier) {
        this.form.supplierPhone = ""
        return
      }
      this.form.supplierName = supplier.supplierName
      this.form.supplierPhone = supplier.contactPhone || ""
    },
    toggleCategoryPanel(collapsed) {
      if (this.categoryCollapsed === collapsed) {
        return
      }
      this.categoryCollapsed = collapsed
      this.refreshProductTableLayout()
    },
    refreshProductTableLayout() {
      this.$nextTick(() => {
        if (this.$refs.productTable) {
          this.$refs.productTable.doLayout()
        }
      })
      window.setTimeout(() => {
        if (this.$refs.productTable) {
          this.$refs.productTable.doLayout()
        }
      }, 220)
    },
    filterCategoryNode(value, data) {
      if (!value) return true
      const keyword = value.toLowerCase()
      const text = [
        data.categoryName,
        data.categoryCode,
        data.categoryFullPath
      ].filter(Boolean).join(" ").toLowerCase()
      return text.indexOf(keyword) !== -1
    },
    handleCategoryClick(data) {
      this.selectedCategory = data.categoryId ? data : null
      this.queryParams.categoryId = data.categoryId || undefined
      this.queryParams.pageNum = 1
      this.getList()
    },
    handleQuery() {
      this.queryParams.pageNum = 1
      this.getList()
    },
    resetQuery() {
      this.queryParams = {
        pageNum: 1,
        pageSize: this.queryParams.pageSize,
        productName: undefined,
        productCode: undefined,
        categoryId: undefined,
        grade: undefined,
        supplierName: undefined,
        status: undefined
      }
      this.selectedCategory = null
      this.$nextTick(() => {
        if (this.$refs.categoryTree) {
          this.$refs.categoryTree.setCurrentKey(0)
        }
      })
      this.getList()
    },
    getList() {
      this.loading = true
      return listProduct(this.queryParams, { silentError: true }).then(res => {
        this.list = res.rows || []
        this.total = res.total || 0
      }).catch(error => {
        this.list = []
        this.total = 0
        if (this.$modal && this.$modal.msgWarning) {
          this.$modal.msgWarning(this.getRequestErrorMessage(error, "商品列表加载失败，请确认是否有商品资料权限"))
        }
      }).finally(() => {
        this.loading = false
      })
    },
    openForm(row) {
      if (!this.ensureWarehouseProductOperation()) {
        return
      }
      this.detailMode = false
      this.activeTab = "base"
      this.ensureSupplierOptions()
      if (!row) {
        this.loadCategories().finally(() => {
          this.form = this.emptyForm()
          if (this.queryParams.categoryId && this.categoryMap[this.queryParams.categoryId]) {
            this.form.categoryId = this.queryParams.categoryId
          }
          this.drawerOpen = true
          this.$nextTick(() => {
            if (this.$refs.formRef) {
              this.$refs.formRef.clearValidate()
            }
          })
        })
        return
      }
      getProduct(row.productId).then(res => {
        this.form = Object.assign(this.emptyForm(), res.data || {})
        this.drawerOpen = true
        this.$nextTick(() => {
          if (this.$refs.formRef) {
            this.$refs.formRef.clearValidate()
          }
        })
      })
    },
    openDetail(row) {
      if (this.isProductReadonlyMode) {
        return
      }
      if (!row || !row.productId) {
        return
      }
      this.detailMode = true
      this.activeTab = "base"
      getProduct(row.productId).then(res => {
        this.form = Object.assign(this.emptyForm(), row, res.data || {})
        this.drawerOpen = true
      })
    },
    doSave() {
      if (this.detailMode) {
        return
      }
      if (!this.ensureWarehouseProductOperation()) {
        return
      }
      this.$refs.formRef.validate(valid => {
        if (!valid) return
        const categoryId = Number(this.form.categoryId)
        if (!categoryId || categoryId <= 0) {
          this.$modal.msgWarning("请选择有效商品分类")
          return
        }
        if (!this.categoryMap[categoryId]) {
          this.$modal.msgWarning("商品分类已不存在，请刷新分类后重新选择")
          this.loadCategories()
          return
        }
        this.form.categoryId = categoryId
        if (this.form.salePrice500g != null) {
          this.form.salesPrice = this.form.salePrice500g
        } else if (this.form.salePrice250g != null) {
          this.form.salesPrice = this.form.salePrice250g
        }
        this.saveLoading = true
        const api = this.form.productId ? updateProduct : addProduct
        const payload = Object.assign({}, this.form, { productCode: undefined })
        this.stripHiddenCostFields(payload)
        api(payload).then(() => {
          this.$modal.msgSuccess("保存成功")
          this.drawerOpen = false
          this.getList()
          this.loadCategories()
        }).finally(() => {
          this.saveLoading = false
        })
      })
    },
    handleDelete(row) {
      if (!this.ensureWarehouseProductOperation()) {
        return
      }
      const productCode = row.productCode || "-"
      const productName = row.productName || "-"
      this.$modal.confirm("确认删除商品【" + productCode + " / " + productName + "】？").then(() => {
        delProduct(row.productId).then(() => {
          this.$modal.msgSuccess("删除成功")
          this.getList()
        }).catch(error => {
          this.$modal.msgError(this.formatDeleteError(error, "商品删除失败"))
        })
      })
    },
    formatDeleteError(error, fallbackMessage) {
      return this.getRequestErrorMessage(error, fallbackMessage) + "。建议将该数据改为停用，保留历史业务引用。"
    },
    getRequestErrorMessage(error, fallbackMessage) {
      if (!error) {
        return fallbackMessage
      }
      if (typeof error === "string") {
        return error === "error" ? fallbackMessage : error
      }
      if (error.response && error.response.data) {
        return error.response.data.msg || error.response.data.message || fallbackMessage
      }
      return error.msg || error.message || fallbackMessage
    },
    handleExport() {
      if (!this.ensureWarehouseProductOperation()) {
        return
      }
      confirmExportAction(this, {
        moduleName: "商品数据",
        rangeLabel: "当前查询条件下的商品数据",
        filterLabel: this.buildProductExportFilterLabel(),
        sensitiveFields: this.canViewCostFields ? ['采购参考价'] : []
      }).then(() => {
        this.download("inventory/product/export", { ...this.queryParams }, this.exportFileName("商品数据"))
      })
    },
    handleImport() {
      if (!this.ensureWarehouseProductOperation()) {
        return
      }
      this.importForm = { fileList: [], uploadFile: null, updateSupport: false }
      this.importOpen = true
      this.$nextTick(() => {
        if (this.$refs.uploadRef) {
          this.$refs.uploadRef.clearFiles()
        }
      })
    },
    onFileChange(file, fileList) {
      this.importForm.uploadFile = file.raw
      this.importForm.fileList = fileList
    },
    onFileRemove(file, fileList) {
      this.importForm.uploadFile = null
      this.importForm.fileList = fileList
    },
    doImport() {
      if (!this.ensureWarehouseProductOperation()) {
        return
      }
      if (!this.importForm.uploadFile) {
        this.$modal.msgWarning("请选择文件")
        return
      }
      this.importLoading = true
      const formData = new FormData()
      formData.append("file", this.importForm.uploadFile)
      formData.append("updateSupport", this.importForm.updateSupport)
      importData(formData).then(res => {
        this.$modal.msgSuccess(res && res.msg ? res.msg : res || "导入成功")
        this.importOpen = false
        this.getList()
        this.loadCategories()
      }).finally(() => {
        this.importLoading = false
      })
    },
    downloadTemplate() {
      if (!this.ensureWarehouseProductOperation()) {
        return
      }
      confirmTemplateDownload(this, { moduleName: "商品导入模板" }).then(() => {
        this.download("inventory/product/importTemplate", {}, this.exportFileName("商品导入模板"))
      })
    },
    stripHiddenCostFields(payload) {
      if (this.canViewCostFields || !payload) {
        return payload
      }
      delete payload.purchasePrice
      return payload
    },
    buildProductExportFilterLabel() {
      return [
        "商品名称：" + this.displayText(this.queryParams.productName),
        "商品编码：" + this.displayText(this.queryParams.productCode),
        "分类：" + this.selectedCategoryName,
        "供应商：" + this.displayText(this.queryParams.supplierName),
        "状态：" + this.displayText(this.queryParams.status)
      ].join("；")
    },
    getCategoryFullPath(categoryId, fallbackName) {
      const category = this.categoryMap[categoryId]
      if (category && category.categoryFullPath) {
        return category.categoryFullPath
      }
      return fallbackName || "-"
    },
    getProductCategoryName(row) {
      if (row.categoryName) {
        return row.categoryName
      }
      const category = this.categoryMap[row.categoryId]
      return category && category.categoryName ? category.categoryName : "-"
    },
    getProductCategoryPath(row) {
      const fullPath = row.categoryFullPath || this.getCategoryFullPath(row.categoryId, "")
      if (!fullPath || fullPath === "-" || fullPath === row.categoryName) {
        return ""
      }
      return fullPath
    },
    formatMoney(value) {
      if (value === null || value === undefined || value === "") {
        return "-"
      }
      const number = Number(value)
      return Number.isNaN(number) ? value : number.toFixed(2)
    },
    displayText(value) {
      if (value === null || value === undefined || value === "") {
        return "-"
      }
      return value
    },
    displaySalePrice500g() {
      const salePrice500g = this.form.salePrice500g
      const value = salePrice500g === null || salePrice500g === undefined || salePrice500g === "" ? this.form.salesPrice : salePrice500g
      return this.formatMoney(value)
    }
  }
}
</script>

<style lang="scss" scoped>
.product-page {
  .mb12 {
    margin-bottom: 12px;
  }

  .product-layout {
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

  aside.category-panel {
    background-color: transparent !important;
  }

  .category-panel {
    width: 240px;
    min-width: 0;
    display: flex;
    flex: 0 0 240px;
    padding: 0 !important;
    background: transparent !important;
    border: none !important;
    box-shadow: none !important;
    overflow: hidden;
    opacity: 1;
    transition: flex-basis 0.22s ease, width 0.22s ease, opacity 0.16s ease;
  }

  .product-main {
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
    margin: 0 !important;
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
    transition: background-color 0.15s ease, color 0.15s ease;
  }

  ::v-deep .category-tree .el-tree-node__content:hover {
    background: #f5f7fb;
  }

  ::v-deep .category-tree .is-current > .el-tree-node__content {
    background: #edf5ff;
    color: #1f5fbf;
    font-weight: 600;
  }

  ::v-deep .category-tree .el-tree-node__expand-icon {
    color: #a8b1c0;
    font-size: 12px;
  }

  .card-header {
    display: flex;
    align-items: center;
    justify-content: space-between;
    min-width: 0;
    gap: 8px;
  }

  .card-header > span {
    flex: 0 0 auto;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
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

  .tree-node {
    display: flex;
    min-width: 0;
    width: 100%;
    align-items: center;
    justify-content: flex-start;
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

  ::v-deep .query-form .el-select {
    width: 180px;
  }

  .query-actions {
    white-space: nowrap;
  }

  .query-actions .el-button {
    height: 32px;
    padding: 7px 12px;
    border-radius: 7px;
    font-size: 13px;
  }

  .metric-row {
    display: grid;
    grid-template-columns: repeat(4, minmax(120px, 1fr));
    gap: 10px;
    margin-top: 2px;
  }

  .metric-card {
    min-height: 56px;
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
  }

  .metric-label {
    margin-top: 2px;
    color: #64748b;
    font-size: 12px;
  }

  .price-text {
    font-variant-numeric: tabular-nums;
  }

  .category-cell {
    display: flex;
    min-width: 0;
    flex-direction: column;
    line-height: 18px;
  }

  .category-cell-name {
    color: #303133;
    font-weight: 500;
  }

  .category-cell-path {
    color: #909399;
    font-size: 12px;
  }

  ::v-deep .table-card .el-table__fixed,
  ::v-deep .table-card .el-table__fixed-right {
    box-shadow: none !important;
  }

  ::v-deep .table-card .el-table__fixed::before,
  ::v-deep .table-card .el-table__fixed-right::before {
    background-color: #eef3f8;
  }

  ::v-deep .el-table__body-wrapper::-webkit-scrollbar {
    height: 8px;
  }

  ::v-deep .el-table__body-wrapper::-webkit-scrollbar-track {
    border-radius: 999px;
    background: #edf2f7;
  }

  ::v-deep .el-table__body-wrapper::-webkit-scrollbar-thumb {
    border-radius: 999px;
    background: #c8d2df;
  }

  .form-tip {
    margin-top: 4px;
    color: #909399;
    font-size: 12px;
    line-height: 18px;
  }

  .supplier-option {
    display: flex;
    min-width: 0;
    flex-direction: column;
    justify-content: center;
    line-height: 18px;
  }

  .supplier-option-name {
    overflow: hidden;
    color: #303133;
    font-weight: 600;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  .supplier-option-meta {
    overflow: hidden;
    color: #909399;
    font-size: 12px;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  .danger-action {
    color: #f56c6c;
  }

  .import-alert {
    margin-bottom: 16px;
  }

  .upload-tip {
    margin-left: 10px;
    color: #909399;
  }
}

.drawer-body {
  padding: 0 22px 28px;

  .full-width {
    width: 100%;
  }

  .readonly-form {
    display: flex;
    flex-direction: column;
    gap: 18px;
  }

  .detail-hero {
    display: flex;
    min-width: 0;
    align-items: center;
    gap: 14px;
    padding: 17px 18px;
    border: 1px solid #dfe9f7;
    border-radius: 12px;
    background: linear-gradient(135deg, #f4f8ff 0%, #fbfdff 58%, #f7fbff 100%);
  }

  .detail-hero-icon {
    display: flex;
    width: 44px;
    height: 44px;
    flex: 0 0 44px;
    align-items: center;
    justify-content: center;
    border-radius: 11px;
    background: #2764d8;
    box-shadow: 0 6px 14px rgba(39, 100, 216, 0.2);
    color: #fff;
    font-size: 21px;
  }

  .detail-hero-main {
    min-width: 0;
    flex: 1 1 auto;
  }

  .detail-hero-eyebrow {
    margin-bottom: 3px;
    color: #7790b2;
    font-size: 11px;
    font-weight: 700;
    letter-spacing: 0.08em;
  }

  .detail-hero-title {
    overflow: hidden;
    color: #12213a;
    font-size: 18px;
    font-weight: 700;
    line-height: 24px;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  .detail-hero-meta {
    display: flex;
    min-width: 0;
    flex-wrap: wrap;
    gap: 5px 13px;
    margin-top: 5px;
    color: #62748d;
    font-size: 12px;
    line-height: 17px;
  }

  .detail-hero-meta span {
    position: relative;
  }

  .detail-hero-meta span + span::before {
    position: absolute;
    top: 7px;
    left: -8px;
    width: 3px;
    height: 3px;
    border-radius: 50%;
    background: #afbdd0;
    content: "";
  }

  .detail-status {
    flex: 0 0 auto;
    border-radius: 999px;
    font-weight: 600;
  }

  .detail-section {
    padding: 16px;
    border: 1px solid #e7edf5;
    border-radius: 12px;
    background: #fff;
    box-shadow: 0 4px 16px rgba(23, 43, 77, 0.045);
  }

  .detail-section-heading {
    display: flex;
    align-items: center;
    gap: 10px;
    margin-bottom: 13px;
  }

  .detail-section-icon {
    display: inline-flex;
    width: 32px;
    height: 32px;
    flex: 0 0 32px;
    align-items: center;
    justify-content: center;
    border-radius: 9px;
    background: #eaf2ff;
    color: #2764d8;
    font-size: 16px;
  }

  .detail-section-icon.is-amber {
    background: #fff4df;
    color: #b87508;
  }

  .detail-section-icon.is-violet {
    background: #f1edff;
    color: #7356c8;
  }

  .detail-section-icon.is-green {
    background: #e8f7f1;
    color: #218566;
  }

  .detail-section-title {
    color: #0f172a;
    font-size: 14px;
    font-weight: 700;
    line-height: 19px;
  }

  .detail-section-caption {
    margin-top: 1px;
    color: #8a98aa;
    font-size: 11px;
    line-height: 15px;
  }

  .detail-grid {
    display: grid;
    grid-template-columns: repeat(2, minmax(0, 1fr));
    gap: 10px 12px;
  }

  .detail-item {
    display: flex;
    min-width: 0;
    flex-direction: column;
    gap: 5px;
    padding: 10px 11px;
    border: 1px solid #e9eef5;
    border-radius: 9px;
    background: #f8fafc;
    transition: border-color 0.16s ease, background-color 0.16s ease;
  }

  .detail-item:hover {
    border-color: #d5e1f0;
    background: #fbfdff;
  }

  .detail-item.is-wide {
    grid-column: 1 / -1;
  }

  .detail-label {
    color: #7b899c;
    font-size: 11px;
    font-weight: 500;
    line-height: 16px;
  }

  .detail-value {
    min-width: 0;
    color: #0f172a;
    font-size: 13px;
    font-weight: 500;
    line-height: 19px;
    overflow-wrap: anywhere;
    word-break: break-word;
  }

  .detail-text {
    color: #35445b;
    font-weight: 400;
    line-height: 21px;
    white-space: pre-wrap;
  }

  .detail-url {
    color: #3156d6;
    font-weight: 400;
  }

  .detail-media-gallery {
    display: grid;
    grid-template-columns: repeat(3, minmax(0, 1fr));
    gap: 10px;
  }

  .detail-media-card {
    position: relative;
    min-width: 0;
    overflow: hidden;
    border: 1px solid #e3eaf3;
    border-radius: 9px;
    background: #f8fafc;
    aspect-ratio: 1 / 0.82;
  }

  .detail-media-label {
    position: absolute;
    z-index: 1;
    top: 7px;
    left: 7px;
    padding: 3px 7px;
    border-radius: 999px;
    background: rgba(15, 23, 42, 0.68);
    color: #fff;
    font-size: 10px;
    font-weight: 600;
    line-height: 15px;
  }

  ::v-deep .detail-media-card .el-image {
    display: block;
    width: 100%;
    height: 100%;
  }

  .detail-media-empty {
    display: flex;
    width: 100%;
    height: 100%;
    align-items: center;
    justify-content: center;
    flex-direction: column;
    gap: 6px;
    color: #a1adbd;
    font-size: 11px;
  }

  .detail-media-empty i {
    font-size: 22px;
  }

  .detail-value.price-text {
    color: #183f86;
    font-size: 14px;
    font-weight: 700;
    font-variant-numeric: tabular-nums;
  }

  .drawer-footer {
    display: flex;
    justify-content: flex-end;
    gap: 8px;
    padding-top: 12px;
  }

  .image-upload-alert {
    margin-bottom: 16px;
  }

  .product-image-upload-grid {
    display: grid;
    grid-template-columns: repeat(2, minmax(0, 1fr));
    gap: 14px;
  }

  ::v-deep .product-image-upload-item.el-form-item {
    margin-bottom: 0;
    padding: 12px;
    border: 1px solid #e6edf5;
    border-radius: 10px;
    background: #f8fafc;
  }

  ::v-deep .product-image-upload-item .el-form-item__label {
    float: none;
    width: auto !important;
    padding: 0 0 8px;
    color: #344258;
    font-weight: 600;
    line-height: 20px;
  }

  ::v-deep .product-image-upload-item .el-form-item__content {
    margin-left: 0 !important;
    line-height: normal;
  }

  ::v-deep .product-image-upload-item .el-upload--picture-card,
  ::v-deep .product-image-upload-item .el-upload-list--picture-card .el-upload-list__item {
    width: 100%;
    height: 118px;
    margin: 0;
    border-radius: 8px;
  }

  ::v-deep .product-image-upload-item .component-upload-image,
  ::v-deep .product-image-upload-item .el-upload,
  ::v-deep .product-image-upload-item .el-upload-list--picture-card {
    width: 100%;
  }

  ::v-deep .product-image-upload-item .el-upload-list--picture-card {
    display: block;
    line-height: 0;
  }

  ::v-deep .product-image-upload-item .el-upload--picture-card {
    line-height: 116px;
  }
}

@media (max-width: 992px) {
  .product-page {
    .product-layout {
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
  }
}

@media (max-width: 640px) {
  .product-page {
    .metric-row {
      grid-template-columns: 1fr;
    }
  }

  .drawer-body {
    padding-right: 14px;
    padding-left: 14px;

    .detail-hero {
      align-items: flex-start;
      padding: 14px;
    }

    .detail-hero-icon {
      width: 38px;
      height: 38px;
      flex-basis: 38px;
    }

    .detail-status {
      position: absolute;
      right: 28px;
    }

    .detail-hero-main {
      padding-right: 66px;
    }

    .detail-section {
      padding: 14px;
    }

    .detail-grid {
      grid-template-columns: 1fr;
    }

    .detail-media-gallery,
    .product-image-upload-grid {
      grid-template-columns: repeat(2, minmax(0, 1fr));
    }
  }
}

@import "~@/styles/warehouse-management-page.scss";
</style>
