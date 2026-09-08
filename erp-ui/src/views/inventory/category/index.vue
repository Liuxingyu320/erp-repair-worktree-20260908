<template>
  <div class="app-container category-page">
    <inventory-page-hero
      title="商品分类"
      eyebrow="仓库资料"
      description="建立清晰、稳定的商品分类层级，让资料检索、库存盘点与经营分析更加准确。"
      scope-text="统一分类标准"
      icon="el-icon-collection-tag"
      :features="['层级分类', '编码规则', '启停管理']"
    />
    <el-card shadow="never" class="filter-card">
      <el-form :model="queryParams" inline size="small" class="query-form">
        <el-form-item label="分类名称">
          <el-input
            v-model="queryParams.keyword"
            placeholder="名称 / 编码 / 路径"
            clearable
            prefix-icon="el-icon-search"
            @keyup.enter.native="handleQuery"
          />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="queryParams.status" clearable placeholder="全部状态" @change="handleQuery">
            <el-option label="正常" value="0" />
            <el-option label="停用" value="1" />
          </el-select>
        </el-form-item>
        <el-form-item class="query-actions">
          <el-button v-hasPermi="['inv:category:list']" type="primary" size="mini" icon="el-icon-search" @click="handleQuery">搜索</el-button>
          <el-button size="mini" icon="el-icon-refresh-left" @click="resetQuery">重置</el-button>
          <el-button v-hasPermi="['inv:category:add']" size="mini" icon="el-icon-plus" @click="openRootForm">新增根分类</el-button>
          <el-button size="mini" icon="el-icon-sort" @click="toggleExpandAll">{{ isExpandAll ? "收起" : "展开" }}</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card shadow="never" class="category-config-table">
      <div slot="header" class="card-header">
        <div>
          <span class="card-title">分类配置</span>
          <span class="card-subtitle">先配置分类，再在商品管理中引用分类建档</span>
        </div>
        <div class="header-actions">
          <span class="summary-text">{{ summaryText }}</span>
          <el-button v-hasPermi="['inv:category:list']" type="text" size="mini" icon="el-icon-refresh" @click="getTree">刷新</el-button>
        </div>
      </div>

      <el-table
        v-accessible-table="'商品分类列表'"
        v-if="refreshTable"
        v-loading="treeLoading"
        :data="filteredTreeData"
        row-key="categoryId"
        size="small"
        :default-expand-all="isExpandAll"
        :tree-props="{ children: 'children', hasChildren: 'hasChildren' }"
      >
        <el-table-column label="分类名称" prop="categoryName" min-width="220" show-overflow-tooltip>
          <template slot-scope="scope">
            <span class="category-name">{{ scope.row.categoryName || "-" }}</span>
            <el-tag v-if="scope.row.categoryLevel === 1" size="mini" class="level-tag">根分类</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="分类路径" prop="categoryFullPath" min-width="260" show-overflow-tooltip />
        <el-table-column label="分类编码" prop="categoryCode" width="150" show-overflow-tooltip>
          <template slot-scope="scope">
            <span>{{ scope.row.categoryCode || "-" }}</span>
          </template>
        </el-table-column>
        <el-table-column label="排序" prop="orderNum" width="90" align="center" />
        <el-table-column label="状态" prop="status" width="90" align="center">
          <template slot-scope="scope">
            <el-tag :type="scope.row.status === '0' ? 'success' : 'danger'" size="mini">
              {{ scope.row.status === '0' ? '正常' : '停用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="创建时间" prop="createTime" width="170">
          <template slot-scope="scope">
            <span>{{ parseTime(scope.row.createTime) || "-" }}</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="210" fixed="right">
          <template slot-scope="scope">
            <el-button v-hasPermi="['inv:category:add']" type="text" size="mini" icon="el-icon-plus" @click="openChildForm(scope.row)">新增子类</el-button>
            <el-button v-hasPermi="['inv:category:edit']" type="text" size="mini" icon="el-icon-edit" @click="openEditForm(scope.row)">编辑</el-button>
            <el-tooltip :disabled="!hasChildren(scope.row)" content="请先删除或调整子分类，再删除该分类" placement="top">
              <span class="delete-action-wrap">
                <el-button
                  v-hasPermi="['inv:category:remove']"
                  type="text"
                  size="mini"
                  icon="el-icon-delete"
                  class="danger-action"
                  :disabled="hasChildren(scope.row)"
                  @click="handleDelete(scope.row)"
                >删除</el-button>
              </span>
            </el-tooltip>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-drawer
      :title="drawerTitle"
      :visible.sync="open"
      size="min(520px, 100vw)"
      custom-class="category-drawer"
      append-to-body
      :close-on-click-modal="false"
    >
      <div class="drawer-body">
        <el-form ref="formRef" :model="form" :rules="rules" label-width="96px" size="small">
          <el-form-item label="上级分类" prop="parentId">
            <el-select
              v-model="form.parentId"
              filterable
              class="full-width"
              placeholder="请选择上级分类"
              :disabled="!!form.categoryId"
            >
              <el-option label="根分类" :value="0" />
              <el-option
                v-for="item in categoryOptions"
                :key="item.categoryId"
                :label="item.categoryFullPath"
                :value="item.categoryId"
                :disabled="isParentOptionDisabled(item)"
              />
            </el-select>
          </el-form-item>
          <el-form-item label="分类名称" prop="categoryName">
            <el-input v-model="form.categoryName" maxlength="64" placeholder="请输入分类名称" />
          </el-form-item>
          <el-form-item label="分类编码">
            <el-input v-model="form.categoryCode" disabled placeholder="保存后按分类名称自动生成" />
            <div class="form-tip">按分类名称拼音首字母生成；重复时先换小写，再自动追加数字。</div>
          </el-form-item>
          <el-form-item label="显示顺序">
            <el-input-number v-model="form.orderNum" :min="0" class="full-width" />
          </el-form-item>
          <el-form-item label="状态">
            <el-radio-group v-model="form.status">
              <el-radio label="0">正常</el-radio>
              <el-radio label="1">停用</el-radio>
            </el-radio-group>
          </el-form-item>
          <el-form-item label="备注">
            <el-input v-model="form.remark" type="textarea" :rows="3" maxlength="300" show-word-limit placeholder="请输入分类说明" />
          </el-form-item>
        </el-form>
        <div class="drawer-footer">
          <el-button size="small" @click="open = false">取消</el-button>
          <el-button v-hasPermi="['inv:category:add','inv:category:edit']" type="primary" size="small" :loading="saveLoading" @click="doSave">保存</el-button>
        </div>
      </div>
    </el-drawer>
  </div>
</template>

<script>
import { categoryTree, getCategory, addCategory, updateCategory, delCategory } from "@/api/inventory/category"

export default {
  name: "InvCategory",
  data() {
    return {
      treeData: [],
      categoryOptions: [],
      categoryMap: {},
      treeLoading: false,
      saveLoading: false,
      open: false,
      refreshTable: true,
      isExpandAll: true,
      queryParams: {
        keyword: "",
        status: undefined
      },
      form: this.emptyForm(),
      rules: {
        categoryName: [{ required: true, message: "请输入分类名称", trigger: "blur" }]
      }
    }
  },
  computed: {
    drawerTitle() {
      if (this.form.categoryId) {
        return "编辑分类"
      }
      return this.form.parentId ? "新增子分类" : "新增根分类"
    },
    summaryText() {
      return "共 " + this.categoryOptions.length + " 个分类，正常 " + this.enabledCategoryCount + " 个"
    },
    enabledCategoryCount() {
      return this.categoryOptions.filter(item => item.status === "0").length
    },
    filteredTreeData() {
      return this.filterTree(this.treeData)
    }
  },
  created() {
    this.getTree()
  },
  methods: {
    emptyForm() {
      return {
        categoryId: undefined,
        parentId: 0,
        categoryName: "",
        categoryCode: "",
        orderNum: 0,
        status: "0",
        remark: ""
      }
    },
    getTree() {
      this.treeLoading = true
      categoryTree().then(res => {
        const roots = this.decorateTree(res.data || [])
        this.treeData = roots
        this.categoryOptions = this.flattenCategories(roots)
        this.categoryMap = this.buildCategoryMap(this.categoryOptions)
      }).finally(() => {
        this.treeLoading = false
      })
    },
    decorateTree(list, parentPath, parentName, level) {
      const pathPrefix = parentPath || []
      const currentLevel = level || 1
      return (list || []).map(item => {
        const currentName = item.categoryName || ""
        const currentPath = pathPrefix.concat(currentName)
        const children = this.decorateTree(item.children || [], currentPath, currentName, currentLevel + 1)
        return Object.assign({}, item, {
          parentName: currentLevel === 1 ? "根分类" : parentName,
          categoryLevel: currentLevel,
          categoryFullPath: currentPath.filter(Boolean).join(" / "),
          children: children
        })
      })
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
    buildCategoryMap(list) {
      const map = {}
      ;(list || []).forEach(item => {
        map[item.categoryId] = item
      })
      return map
    },
    filterTree(list) {
      const keyword = (this.queryParams.keyword || "").trim().toLowerCase()
      const status = this.queryParams.status
      const matchNode = item => {
        const text = [
          item.categoryName,
          item.categoryCode,
          item.categoryFullPath
        ].filter(Boolean).join(" ").toLowerCase()
        const keywordMatched = !keyword || text.indexOf(keyword) !== -1
        const statusMatched = !status || item.status === status
        return keywordMatched && statusMatched
      }
      const walk = items => {
        const result = []
        ;(items || []).forEach(item => {
          const children = walk(item.children)
          if (matchNode(item) || children.length) {
            result.push(Object.assign({}, item, { children: children }))
          }
        })
        return result
      }
      return walk(list)
    },
    handleQuery() {
      this.refreshTreeTable()
    },
    resetQuery() {
      this.queryParams = {
        keyword: "",
        status: undefined
      }
      this.refreshTreeTable()
    },
    refreshTreeTable() {
      this.refreshTable = false
      this.$nextTick(() => {
        this.refreshTable = true
      })
    },
    toggleExpandAll() {
      this.isExpandAll = !this.isExpandAll
      this.refreshTreeTable()
    },
    openRootForm() {
      this.form = this.emptyForm()
      this.open = true
      this.clearValidate()
    },
    openChildForm(row) {
      this.form = Object.assign(this.emptyForm(), {
        parentId: row.categoryId,
        status: "0"
      })
      this.open = true
      this.clearValidate()
    },
    openEditForm(row) {
      getCategory(row.categoryId).then(res => {
        const data = Object.assign({}, row, res.data || {})
        this.form = Object.assign(this.emptyForm(), {
          categoryId: data.categoryId,
          parentId: data.parentId || 0,
          categoryName: data.categoryName || "",
          categoryCode: data.categoryCode || "",
          orderNum: data.orderNum || 0,
          status: data.status || "0",
          remark: data.remark || ""
        })
        this.open = true
        this.clearValidate()
      })
    },
    clearValidate() {
      this.$nextTick(() => {
        if (this.$refs.formRef) {
          this.$refs.formRef.clearValidate()
        }
      })
    },
    isParentOptionDisabled(item) {
      if (!this.form.categoryId) {
        return false
      }
      return item.categoryId === this.form.categoryId || (item.categoryFullPath || "").indexOf(this.form.categoryName + " / ") === 0
    },
    hasChildren(row) {
      return !!(row.children && row.children.length)
    },
    doSave() {
      this.$refs.formRef.validate(valid => {
        if (!valid) return
        this.saveLoading = true
        const api = this.form.categoryId ? updateCategory : addCategory
        const payload = Object.assign({}, this.form, { categoryCode: undefined })
        api(payload).then(() => {
          this.$modal.msgSuccess("保存成功")
          this.open = false
          this.getTree()
        }).finally(() => {
          this.saveLoading = false
        })
      })
    },
    handleDelete(row) {
      if (this.hasChildren(row)) {
        this.$modal.msgWarning("请先删除或调整子分类，再删除该分类")
        return
      }
      this.$modal.confirm('确认删除分类"' + row.categoryName + '"? 删除后商品将无法继续使用该分类。').then(() => {
        delCategory(row.categoryId).then(() => {
          this.$modal.msgSuccess("删除成功")
          this.getTree()
        }).catch(error => {
          this.$modal.msgError(this.formatDeleteError(error, "分类删除失败"))
        })
      })
    },
    formatDeleteError(error, fallbackMessage) {
      return this.getRequestErrorMessage(error, fallbackMessage) + "。建议将该分类改为停用，保留已建商品引用。"
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
    }
  }
}
</script>

<style lang="scss" scoped>
.category-page {
  .filter-card {
    margin-bottom: 12px;
  }

  .query-form {
    display: flex;
    flex-wrap: wrap;
  }

  .query-actions {
    white-space: nowrap;
  }

  .category-config-table {
    min-height: 540px;
  }

  .card-header {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 12px;
  }

  .card-title {
    font-weight: 600;
  }

  .card-subtitle {
    margin-left: 10px;
    color: #909399;
    font-size: 12px;
  }

  .header-actions {
    display: flex;
    align-items: center;
    gap: 10px;
  }

  .summary-text {
    color: #606266;
    font-size: 12px;
  }

  .category-name {
    font-weight: 500;
  }

  .level-tag {
    margin-left: 8px;
  }

  .danger-action {
    color: #f56c6c;
  }

  .delete-action-wrap {
    display: inline-block;
  }

  .form-tip {
    margin-top: 4px;
    color: #909399;
    font-size: 12px;
    line-height: 18px;
  }
}

.drawer-body {
  padding: 0 20px 20px;

  .full-width {
    width: 100%;
  }

  .drawer-footer {
    display: flex;
    justify-content: flex-end;
    gap: 8px;
    padding-top: 12px;
  }
}

@media (max-width: 768px) {
  .category-page {
    .card-header {
      align-items: flex-start;
      flex-direction: column;
    }

    .card-subtitle {
      display: block;
      margin-left: 0;
      margin-top: 4px;
    }
  }
}

@import "~@/styles/warehouse-management-page.scss";
</style>
