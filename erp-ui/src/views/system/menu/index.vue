<template>
  <div class="app-container system-management-page">
    <system-page-header
      title="菜单管理"
      description="维护导航层级、权限标识与展示顺序，统一各端功能入口。"
      icon="el-icon-menu"
      tip="调整排序后请先保存，再离开或执行其他操作。"
    />
    <div v-show="showSearch" class="search-card menu-search-card">
    <el-form :model="queryParams" ref="queryForm" size="small" :inline="true">
      <el-form-item label="菜单名称" prop="menuName">
        <el-input
          v-model="queryParams.menuName"
          placeholder="请输入菜单名称"
          clearable
          @keyup.enter.native="handleQuery"
        />
      </el-form-item>
      <el-form-item label="状态" prop="status">
        <el-select v-model="queryParams.status" placeholder="菜单状态" clearable>
          <el-option
            v-for="dict in dict.type.sys_normal_disable"
            :key="dict.value"
            :label="dict.label"
            :value="dict.value"
          />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button type="primary" icon="el-icon-search" size="mini" @click="handleQuery">搜索</el-button>
        <el-button icon="el-icon-refresh" size="mini" @click="resetQuery">重置</el-button>
      </el-form-item>
    </el-form>
    </div>

    <div class="content-card menu-toolbar-card">
    <el-row :gutter="10" class="mb8">
      <el-col :span="1.5">
        <el-button
          type="primary"
          plain
          icon="el-icon-plus"
          size="mini"
          @click="handleAdd"
          v-hasPermi="['system:menu:add']"
        >新增</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button
          type="warning"
          plain
          icon="el-icon-check"
          size="mini"
          :disabled="!hasPendingSortChanges || sortSaving"
          :loading="sortSaving"
          @click="handleSaveSort"
          v-hasPermi="['system:menu:edit']"
        >{{ hasPendingSortChanges ? `保存排序（已修改 ${pendingSortCount} 项）` : "保存排序" }}</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button
          type="info"
          plain
          icon="el-icon-sort"
          size="mini"
          @click="toggleExpandAll"
        >展开/折叠</el-button>
      </el-col>
      <right-toolbar :showSearch.sync="showSearch" @queryTable="handleSortRefresh"></right-toolbar>
    </el-row>
    <el-alert v-if="sortSaveError" :title="sortSaveError" type="warning" :closable="false" show-icon class="sort-save-alert" />
    </div>

    <div class="table-card menu-table-card">
    <el-table
      v-if="refreshTable"
      v-loading="loading"
      :data="menuList"
      row-key="menuId"
      :default-expand-all="isExpandAll"
      :tree-props="{children: 'children', hasChildren: 'hasChildren'}"
    >
      <el-table-column prop="menuName" label="菜单名称" :show-overflow-tooltip="true" width="220">
        <template slot-scope="scope">
            <svg-icon :icon-class="scope.row.icon" />
            <span class="ml5">{{ scope.row.menuName }}</span>
        </template>
      </el-table-column>
      <el-table-column prop="menuName" label="类型" :show-overflow-tooltip="true" width="100">
        <template slot-scope="scope">
          <el-tag v-if="scope.row.menuType === 'M' && scope.row.isFrame === '0'" type="danger" size="small">外链</el-tag>
          <el-tag v-else-if="scope.row.menuType === 'M'" type="primary" size="small">目录</el-tag>
          <el-tag v-else-if="scope.row.menuType === 'C' && scope.row.isFrame === '0'" type="danger" size="small">外链</el-tag>
          <el-tag v-else-if="scope.row.menuType === 'C'" type="success" size="small">菜单</el-tag>
          <el-tag v-else-if="scope.row.menuType === 'F'" type="warning" size="small">按钮</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="orderNum" label="排序" width="200">
        <template slot-scope="scope">
          <el-input-number
            v-model="scope.row.orderNum"
            controls-position="right"
            :min="0"
            :max="999999"
            size="mini"
            style="width: 88px"
            :class="{ 'sort-value-dirty': isSortItemDirty(scope.row) }"
          />
        </template>
      </el-table-column>
      <el-table-column prop="perms" label="权限标识" :show-overflow-tooltip="true" />
      <el-table-column prop="component" label="组件路径" :show-overflow-tooltip="true" />
      <el-table-column prop="status" label="状态" width="80">
        <template slot-scope="scope">
          <dict-tag :options="dict.type.sys_normal_disable" :value="scope.row.status" />
        </template>
      </el-table-column>
      <el-table-column label="操作" align="center" class-name="small-padding fixed-width">
        <template slot-scope="scope">
          <el-button
            size="mini"
            type="text"
            icon="el-icon-edit"
            @click="handleUpdate(scope.row)"
            v-hasPermi="['system:menu:edit']"
          >修改</el-button>
          <el-button
            size="mini"
            type="text"
            icon="el-icon-plus"
            @click="handleAdd(scope.row)"
            v-hasPermi="['system:menu:add']"
          >新增</el-button>
          <el-button
            size="mini"
            type="text"
            icon="el-icon-delete"
            @click="handleDelete(scope.row)"
            v-hasPermi="['system:menu:remove']"
          >删除</el-button>
        </template>
      </el-table-column>
    </el-table>
    </div>

    <!-- 添加或修改菜单对话框 -->
    <el-dialog :title="title" :visible.sync="open" width="680px" append-to-body>
      <el-form ref="form" :model="form" :rules="rules" label-width="100px">
        <el-row>
          <el-col :span="24">
            <el-form-item label="上级菜单" prop="parentId">
              <treeselect
                v-model="form.parentId"
                :options="menuOptions"
                :normalizer="normalizer"
                :show-count="true"
                placeholder="选择上级菜单"
              />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row>
          <el-col :span="24">
            <el-form-item label="菜单类型" prop="menuType">
              <el-radio-group v-model="form.menuType">
                <el-radio label="M">目录</el-radio>
                <el-radio label="C">菜单</el-radio>
                <el-radio label="F">按钮</el-radio>
              </el-radio-group>
            </el-form-item>
          </el-col>
        </el-row>
        <el-row>
          <el-col :span="12" v-if="form.menuType != 'F'">
            <el-form-item label="菜单图标" prop="icon">
              <el-popover
                placement="bottom-start"
                width="460"
                trigger="click"
                @show="$refs['iconSelect'].reset()"
              >
                <IconSelect ref="iconSelect" @selected="selected" :active-icon="form.icon" />
                <el-input slot="reference" v-model="form.icon" placeholder="点击选择图标" readonly>
                  <svg-icon
                    v-if="form.icon"
                    slot="prefix"
                    :icon-class="form.icon"
                    style="width: 25px;"
                  />
                  <i v-else slot="prefix" class="el-icon-search el-input__icon" />
                </el-input>
              </el-popover>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="显示排序" prop="orderNum">
              <el-input-number v-model="form.orderNum" controls-position="right" :min="0" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row>
          <el-col :span="12">
            <el-form-item label="菜单名称" prop="menuName">
              <el-input v-model="form.menuName" placeholder="请输入菜单名称" />
            </el-form-item>
          </el-col>
          <el-col :span="12" v-if="form.menuType == 'C'">
            <el-form-item prop="routeName">
              <el-input v-model="form.routeName" placeholder="请输入路由名称" />
              <span slot="label">
                <el-tooltip content="默认不填则和路由地址相同：如地址为：`user`，则名称为`User`（注意：为避免名字的冲突，特殊情况下请自定义，保证唯一性）" placement="top">
                <i class="el-icon-question"></i>
                </el-tooltip>
                路由名称
              </span>
            </el-form-item>
          </el-col>
        </el-row>
        <el-row>
          <el-col :span="12" v-if="form.menuType != 'F'">
            <el-form-item prop="isFrame">
              <span slot="label">
                <el-tooltip content="选择是外链则路由地址需要以`http(s)://`开头" placement="top">
                <i class="el-icon-question"></i>
                </el-tooltip>
                是否外链
              </span>
              <el-radio-group v-model="form.isFrame">
                <el-radio label="0">是</el-radio>
                <el-radio label="1">否</el-radio>
              </el-radio-group>
            </el-form-item>
          </el-col>
          <el-col :span="12" v-if="form.menuType != 'F'">
            <el-form-item prop="path">
              <span slot="label">
                <el-tooltip content="访问的路由地址，如：`user`，如外网地址需内链访问则以`http(s)://`开头" placement="top">
                <i class="el-icon-question"></i>
                </el-tooltip>
                路由地址
              </span>
              <el-input v-model="form.path" placeholder="请输入路由地址" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row>
          <el-col :span="12" v-if="form.menuType == 'C'">
            <el-form-item prop="component">
              <span slot="label">
                <el-tooltip content="访问的组件路径，如：`system/user/index`，默认在`views`目录下" placement="top">
                <i class="el-icon-question"></i>
                </el-tooltip>
                组件路径
              </span>
              <el-input v-model="form.component" placeholder="请输入组件路径" />
            </el-form-item>
          </el-col>
          <el-col :span="12" v-if="form.menuType != 'M'">
            <el-form-item prop="perms">
              <el-input v-model="form.perms" placeholder="请输入权限标识" maxlength="100" />
              <span slot="label">
                <el-tooltip content="控制器中定义的权限字符，如：@PreAuthorize(`@ss.hasPermi('system:user:list')`)" placement="top">
                <i class="el-icon-question"></i>
                </el-tooltip>
                权限字符
              </span>
            </el-form-item>
          </el-col>
        </el-row>
        <el-row>
          <el-col :span="12" v-if="form.menuType == 'C'">
            <el-form-item prop="query">
              <el-input v-model="form.query" placeholder="请输入路由参数" maxlength="255" />
              <span slot="label">
                <el-tooltip content='访问路由的默认传递参数，如：`{"id": 1, "name": "ry"}`' placement="top">
                <i class="el-icon-question"></i>
                </el-tooltip>
                路由参数
              </span>
            </el-form-item>
          </el-col>
          <el-col :span="12" v-if="form.menuType == 'C'">
            <el-form-item prop="isCache">
              <span slot="label">
                <el-tooltip content="选择是则会被`keep-alive`缓存，需要匹配组件的`name`和地址保持一致" placement="top">
                <i class="el-icon-question"></i>
                </el-tooltip>
                是否缓存
              </span>
              <el-radio-group v-model="form.isCache">
                <el-radio label="0">缓存</el-radio>
                <el-radio label="1">不缓存</el-radio>
              </el-radio-group>
            </el-form-item>
          </el-col>
        </el-row>
        <el-row>
          <el-col :span="12" v-if="form.menuType != 'F'">
            <el-form-item prop="visible">
              <span slot="label">
                <el-tooltip content="选择隐藏则路由将不会出现在侧边栏，但仍然可以访问" placement="top">
                <i class="el-icon-question"></i>
                </el-tooltip>
                显示状态
              </span>
              <el-radio-group v-model="form.visible">
                <el-radio
                  v-for="dict in dict.type.sys_show_hide"
                  :key="dict.value"
                  :label="dict.value"
                >{{dict.label}}</el-radio>
              </el-radio-group>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item prop="status">
              <span slot="label">
                <el-tooltip content="选择停用则路由将不会出现在侧边栏，也不能被访问" placement="top">
                <i class="el-icon-question"></i>
                </el-tooltip>
                菜单状态
              </span>
              <el-radio-group v-model="form.status">
                <el-radio
                  v-for="dict in dict.type.sys_normal_disable"
                  :key="dict.value"
                  :label="dict.value"
                >{{dict.label}}</el-radio>
              </el-radio-group>
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>
      <div slot="footer" class="dialog-footer">
        <el-button type="primary" @click="submitForm">确 定</el-button>
        <el-button @click="cancel">取 消</el-button>
      </div>
    </el-dialog>
  </div>
</template>

<script>
import { listMenu, getMenu, delMenu, addMenu, updateMenu, updateMenuSort } from "@/api/system/menu"
import Treeselect from "@riophae/vue-treeselect"
import "@riophae/vue-treeselect/dist/vue-treeselect.css"
import IconSelect from "@/components/IconSelect"
import pendingSortGuard from "@/mixins/pendingSortGuard"

export default {
  name: "Menu",
  mixins: [pendingSortGuard],
  dicts: ['sys_show_hide', 'sys_normal_disable'],
  components: { Treeselect, IconSelect },
  data() {
    return {
      // 遮罩层
      loading: true,
      // 显示搜索条件
      showSearch: true,
      // 菜单表格树数据
      menuList: [],
      // 菜单树选项
      menuOptions: [],
      // 弹出层标题
      title: "",
      // 是否显示弹出层
      open: false,
      // 是否展开，默认全部折叠
      isExpandAll: false,
      // 重新渲染表格状态
      refreshTable: true,
      sortIdField: "menuId",
      sortListField: "menuList",
      // 查询参数
      queryParams: {
        menuName: undefined,
        visible: undefined
      },
      // 表单参数
      form: {},
      // 表单校验
      rules: {
        menuName: [
          { required: true, message: "菜单名称不能为空", trigger: "blur" }
        ],
        orderNum: [
          { required: true, message: "菜单顺序不能为空", trigger: "blur" }
        ],
        path: [
          { required: true, message: "路由地址不能为空", trigger: "blur" }
        ]
      }
    }
  },
  created() {
    this.getList()
  },
  mounted() {
    window.addEventListener("beforeunload", this.handleBeforeUnload)
  },
  beforeDestroy() {
    window.removeEventListener("beforeunload", this.handleBeforeUnload)
  },
  beforeRouteLeave(to, from, next) {
    if (this.changedSortItems.length === 0) {
      next()
      return
    }
    this.$modal.confirm(`还有 ${this.changedSortItems.length} 项排序未保存，确定离开吗？`)
      .then(() => next())
      .catch(() => next(false))
  },
  computed: {
    changedSortItems() {
      const items = []
      const collect = list => {
        ;(list || []).forEach(item => {
          if (String(this.originalOrders[item.menuId]) !== String(item.orderNum)) {
            items.push({ id: item.menuId, orderNum: item.orderNum })
          }
          if (item.children && item.children.length) collect(item.children)
        })
      }
      collect(this.menuList)
      return items
    }
  },
  methods: {
    // 选择图标
    selected(name) {
      this.form.icon = name
    },
    /** 查询菜单列表 */
    getList() {
      this.loading = true
      return listMenu(this.queryParams).then(response => {
        this.menuList = this.handleTree(response.data, "menuId")
        this.recordOriginalOrders(this.menuList)
      }).finally(() => {
        this.loading = false
      })
    },
    /** 转换菜单数据结构 */
    normalizer(node) {
      if (node.children && !node.children.length) {
        delete node.children
      }
      return {
        id: node.menuId,
        label: node.menuName,
        children: node.children
      }
    },
    /** 查询菜单下拉树结构 */
    getTreeselect() {
      listMenu().then(response => {
        this.menuOptions = []
        const menu = { menuId: 0, menuName: '主类目', children: [] }
        menu.children = this.handleTree(response.data, "menuId")
        this.menuOptions.push(menu)
      })
    },
    // 取消按钮
    cancel() {
      this.open = false
      this.reset()
    },
    // 表单重置
    reset() {
      this.form = {
        menuId: undefined,
        parentId: 0,
        menuName: undefined,
        icon: undefined,
        menuType: "M",
        orderNum: undefined,
        isFrame: "1",
        isCache: "0",
        visible: "0",
        status: "0"
      }
      this.resetForm("form")
    },
    /** 搜索按钮操作 */
    handleQuery() {
      return this.runAfterPendingSortConfirmation(
        () => this.getList(),
        "搜索将丢失尚未保存的菜单排序，是否继续？"
      ).catch(() => {})
    },
    /** 重置按钮操作 */
    resetQuery() {
      return this.runAfterPendingSortConfirmation(() => {
        this.resetForm("queryForm")
        return this.getList()
      }, "重置查询将丢失尚未保存的菜单排序，是否继续？").catch(() => {})
    },
    /** 新增按钮操作 */
    handleAdd(row) {
      return this.runAfterPendingSortConfirmation(
        () => this.openMenuEditor(row),
        "打开菜单编辑将可能在提交后刷新列表并丢失未保存排序，是否继续？"
      ).catch(() => {})
    },
    openMenuEditor(row) {
      this.reset()
      this.getTreeselect()
      if (row != null && row.menuId) {
        this.form.parentId = row.menuId
      } else {
        this.form.parentId = 0
      }
      this.open = true
      this.title = "添加菜单"
    },
    /** 展开/折叠操作 */
    toggleExpandAll() {
      this.refreshTable = false
      this.isExpandAll = !this.isExpandAll
      this.$nextTick(() => {
        this.refreshTable = true
      })
    },
    /** 修改按钮操作 */
    handleUpdate(row) {
      return this.runAfterPendingSortConfirmation(
        () => this.openMenuUpdate(row),
        "打开菜单编辑将可能在提交后刷新列表并丢失未保存排序，是否继续？"
      ).catch(() => {})
    },
    openMenuUpdate(row) {
      this.reset()
      this.getTreeselect()
      getMenu(row.menuId).then(response => {
        this.form = response.data
        this.open = true
        this.title = "修改菜单"
      })
    },
    /** 提交按钮 */
    submitForm() {
      this.$refs["form"].validate(valid => {
        if (valid) {
          if (this.form.menuId != undefined) {
            updateMenu(this.form).then(() => {
              this.$modal.msgSuccess("修改成功")
              this.open = false
              this.getList()
            })
          } else {
            addMenu(this.form).then(() => {
              this.$modal.msgSuccess("新增成功")
              this.open = false
              this.getList()
            })
          }
        }
      })
    },
    /** 保存排序 */
    handleSaveSort() {
      if (!this.hasPendingSortChanges || !this.validatePendingSortChanges()) return Promise.resolve()
      this.sortSaving = true
      return updateMenuSort(this.buildSortChangeRequest()).then(() => {
        this.$modal.msgSuccess("排序保存成功")
        this.originalOrders = {}
        this.recordOriginalOrders(this.menuList)
      }).catch(error => {
        this.handleSortSaveFailure(error)
      }).finally(() => {
        this.sortSaving = false
      })
    },
    /** 恢复当前列表中的全部未保存排序 */
    handleUndoSort() {
      const restore = list => {
        ;(list || []).forEach(item => {
          if (Object.prototype.hasOwnProperty.call(this.originalOrders, item.menuId)) {
            item.orderNum = this.originalOrders[item.menuId]
          }
          if (item.children && item.children.length) restore(item.children)
        })
      }
      restore(this.menuList)
      this.$modal.msgSuccess("已撤销未保存的排序修改")
    },
    handleBeforeUnload(event) {
      if (this.changedSortItems.length === 0) return
      event.preventDefault()
      event.returnValue = ""
    },
    /** 删除按钮操作 */
    handleDelete(row) {
      return this.runAfterPendingSortConfirmation(() => {
        return this.$modal.confirm('是否确认删除名称为"' + row.menuName + '"的数据项？').then(() => {
          return delMenu(row.menuId)
        }).then(() => {
          this.getList()
          this.$modal.msgSuccess("删除成功")
        })
      }, "删除菜单成功后将刷新列表并丢失未保存排序，是否继续？").catch(() => {})
    }
  }
}
</script>

<style lang="scss" scoped>
.menu-search-card {
  margin-bottom: 12px;
  padding-bottom: 8px;
}

.menu-toolbar-card {
  margin-bottom: 12px;
  padding-bottom: 2px;
}

.menu-table-card {
  padding-bottom: 8px;
}

.sort-save-alert {
  margin-top: 8px;
}

::v-deep .sort-value-dirty .el-input__inner {
  color: #b26a00;
  background: #fff7e6;
  border-color: #e6a23c;
  font-weight: 600;
}
</style>
