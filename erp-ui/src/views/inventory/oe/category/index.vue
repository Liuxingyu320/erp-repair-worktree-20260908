<template>
  <div class="app-container catalog-category-page">
    <inventory-page-hero
      title="OE 分类"
      eyebrow="仓库资料"
      description="维护 OE 器皿分类层级与编码规则，为资料建档、资产配置和维修上报提供统一标准。"
      scope-text="统一 OE 分类标准"
      icon="el-icon-collection-tag"
      :features="['分类层级', '编码规则', '资料引用']"
    />
    <el-card shadow="never" class="filter-card">
      <el-form :model="queryParams" inline size="small">
        <el-form-item label="OE分类">
          <el-input v-model="queryParams.keyword" placeholder="分类名称 / 编码" clearable @keyup.enter.native="handleQuery" />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="queryParams.status" clearable placeholder="全部" @change="handleQuery">
            <el-option label="正常" value="0" />
            <el-option label="停用" value="1" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button v-hasPermi="['inv:oeCategory:list']" type="primary" size="mini" icon="el-icon-search" @click="handleQuery">搜索</el-button>
          <el-button size="mini" icon="el-icon-refresh-left" @click="resetQuery">重置</el-button>
          <el-button v-hasPermi="['inv:oeCategory:add']" size="mini" icon="el-icon-plus" @click="openRootForm">新增根分类</el-button>
          <el-button size="mini" icon="el-icon-sort" @click="toggleExpandAll">{{ isExpandAll ? "收起" : "展开" }}</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card shadow="never">
      <div slot="header" class="card-header">
        <div>
          <span class="card-title">OE分类配置</span>
          <span class="card-subtitle">供 OE 管理和 OE 资料查询使用</span>
        </div>
        <el-button v-hasPermi="['inv:oeCategory:list']" type="text" size="mini" icon="el-icon-refresh" @click="getTree">刷新</el-button>
      </div>
      <el-table
        v-if="refreshTable"
        v-loading="loading"
        :data="filteredTreeData"
        row-key="categoryId"
        size="small"
        :default-expand-all="isExpandAll"
        :tree-props="{ children: 'children', hasChildren: 'hasChildren' }"
      >
        <el-table-column label="分类名称" prop="categoryName" min-width="220" show-overflow-tooltip />
        <el-table-column label="分类路径" prop="categoryFullPath" min-width="260" show-overflow-tooltip />
        <el-table-column label="分类编码" prop="categoryCode" width="150" show-overflow-tooltip />
        <el-table-column label="排序" prop="orderNum" width="90" align="center" />
        <el-table-column label="状态" prop="status" width="90" align="center">
          <template slot-scope="scope">
            <el-tag :type="scope.row.status === '0' ? 'success' : 'info'" size="mini">{{ scope.row.status === '0' ? '正常' : '停用' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="220" fixed="right">
          <template slot-scope="scope">
            <el-button v-hasPermi="['inv:oeCategory:add']" type="text" size="mini" icon="el-icon-plus" @click="openChildForm(scope.row)">新增子类</el-button>
            <el-button v-hasPermi="['inv:oeCategory:edit']" type="text" size="mini" icon="el-icon-edit" @click="openEditForm(scope.row)">编辑</el-button>
            <el-button v-hasPermi="['inv:oeCategory:remove']" type="text" size="mini" icon="el-icon-delete" class="danger-action" :disabled="hasChildren(scope.row)" @click="handleDelete(scope.row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-drawer :title="drawerTitle" :visible.sync="open" size="min(520px, 100vw)" append-to-body :close-on-click-modal="false">
      <div class="drawer-body">
        <el-form ref="formRef" :model="form" :rules="rules" label-width="96px" size="small">
          <el-form-item label="上级分类" prop="parentId">
            <el-select v-model="form.parentId" filterable class="full-width" placeholder="请选择上级分类" :disabled="!!form.categoryId">
              <el-option label="根分类" :value="0" />
              <el-option v-for="item in categoryOptions" :key="item.categoryId" :label="item.categoryFullPath" :value="item.categoryId" :disabled="isParentOptionDisabled(item)" />
            </el-select>
          </el-form-item>
          <el-form-item label="分类名称" prop="categoryName">
            <el-input v-model="form.categoryName" maxlength="64" placeholder="请输入OE分类名称" />
          </el-form-item>
          <el-form-item label="分类编码">
            <el-input v-model="form.categoryCode" disabled placeholder="保存后自动生成" />
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
            <el-input v-model="form.remark" type="textarea" :rows="3" maxlength="300" show-word-limit />
          </el-form-item>
        </el-form>
        <div class="drawer-footer">
          <el-button size="small" @click="open = false">取消</el-button>
          <el-button v-hasPermi="['inv:oeCategory:add','inv:oeCategory:edit']" type="primary" size="small" :loading="saving" @click="doSave">保存</el-button>
        </div>
      </div>
    </el-drawer>
  </div>
</template>

<script>
import { oeCategoryTree, getOeCategory, addOeCategory, updateOeCategory, delOeCategory } from "@/api/inventory/oe"

export default {
  name: "InvOeCategory",
  data() {
    return {
      loading: false,
      saving: false,
      open: false,
      refreshTable: true,
      isExpandAll: true,
      treeData: [],
      categoryOptions: [],
      queryParams: { keyword: "", status: undefined },
      form: this.emptyForm(),
      rules: {
        categoryName: [{ required: true, message: "请输入OE分类名称", trigger: "blur" }]
      }
    }
  },
  computed: {
    drawerTitle() {
      if (this.form.categoryId) return "编辑OE分类"
      return this.form.parentId ? "新增OE子分类" : "新增OE根分类"
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
      return { categoryId: undefined, parentId: 0, categoryName: "", categoryCode: "", orderNum: 0, status: "0", remark: "" }
    },
    getTree() {
      this.loading = true
      oeCategoryTree().then(res => {
        const roots = this.decorateTree(res.data || [])
        this.treeData = roots
        this.categoryOptions = this.flattenCategories(roots)
      }).finally(() => {
        this.loading = false
      })
    },
    decorateTree(list, parentPath, parentName, level) {
      const pathPrefix = parentPath || []
      const currentLevel = level || 1
      return (list || []).map(item => {
        const currentPath = pathPrefix.concat(item.categoryName || "")
        return Object.assign({}, item, {
          parentName: currentLevel === 1 ? "根分类" : parentName,
          categoryLevel: currentLevel,
          categoryFullPath: currentPath.filter(Boolean).join(" / "),
          children: this.decorateTree(item.children || [], currentPath, item.categoryName || "", currentLevel + 1)
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
    filterTree(list) {
      const keyword = (this.queryParams.keyword || "").trim().toLowerCase()
      const status = this.queryParams.status
      const matchSelf = item => {
        const text = [item.categoryName, item.categoryCode, item.categoryFullPath].filter(Boolean).join(" ").toLowerCase()
        return (!keyword || text.indexOf(keyword) > -1) && (!status || item.status === status)
      }
      return (list || []).map(item => {
        const children = this.filterTree(item.children || [])
        return Object.assign({}, item, { children })
      }).filter(item => matchSelf(item) || (item.children && item.children.length))
    },
    handleQuery() {
      this.refreshTable = false
      this.$nextTick(() => {
        this.refreshTable = true
      })
    },
    resetQuery() {
      this.queryParams = { keyword: "", status: undefined }
      this.handleQuery()
    },
    toggleExpandAll() {
      this.refreshTable = false
      this.isExpandAll = !this.isExpandAll
      this.$nextTick(() => {
        this.refreshTable = true
      })
    },
    openRootForm() {
      this.form = this.emptyForm()
      this.open = true
    },
    openChildForm(row) {
      this.form = Object.assign(this.emptyForm(), { parentId: row.categoryId })
      this.open = true
    },
    openEditForm(row) {
      getOeCategory(row.categoryId).then(res => {
        this.form = Object.assign(this.emptyForm(), res.data || row)
        this.open = true
      })
    },
    isParentOptionDisabled(item) {
      return this.form.categoryId && item.categoryId === this.form.categoryId
    },
    hasChildren(row) {
      return row.children && row.children.length > 0
    },
    doSave() {
      this.$refs.formRef.validate(valid => {
        if (!valid) return
        this.saving = true
        const request = this.form.categoryId ? updateOeCategory(this.form) : addOeCategory(this.form)
        request.then(() => {
          this.$modal.msgSuccess("保存成功")
          this.open = false
          this.getTree()
        }).finally(() => {
          this.saving = false
        })
      })
    },
    handleDelete(row) {
      this.$modal.confirm("确认删除该OE分类？").then(() => delOeCategory(row.categoryId)).then(() => {
        this.$modal.msgSuccess("删除成功")
        this.getTree()
      })
    }
  }
}
</script>

<style lang="scss" scoped>
.filter-card {
  margin-bottom: 12px;
}
.card-header,
.drawer-footer {
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
.drawer-body {
  padding: 0 20px 20px;
}
.full-width {
  width: 100%;
}
.danger-action {
  color: #f56c6c;
}
</style>
