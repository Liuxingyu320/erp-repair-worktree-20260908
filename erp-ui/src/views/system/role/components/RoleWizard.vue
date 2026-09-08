<template>
  <el-dialog
    title="新增角色向导"
    :visible="visible"
    width="760px"
    append-to-body
    :close-on-click-modal="false"
    @open="handleOpen"
    @close="handleClose"
    @closed="$emit('closed')"
  >
    <el-steps :active="activeStep" finish-status="success" align-center class="role-wizard__steps">
      <el-step title="基本信息" description="定义角色身份" />
      <el-step title="菜单权限" description="选择可用功能" />
      <el-step title="数据范围" description="限制可见数据" />
      <el-step title="确认创建" description="核对最终影响" />
    </el-steps>

    <div v-loading="optionsLoading" class="role-wizard__body">
      <el-form
        v-show="activeStep === 0"
        ref="basicForm"
        :model="form"
        :rules="rules"
        label-width="110px"
      >
        <el-form-item label="角色名称" prop="roleName">
          <el-input ref="roleNameInput" v-model.trim="form.roleName" maxlength="30" show-word-limit placeholder="例如：门店负责人" />
        </el-form-item>
        <el-form-item label="权限字符" prop="roleKey">
          <el-input v-model.trim="form.roleKey" maxlength="100" show-word-limit placeholder="例如：store_manager" />
          <div class="role-wizard__field-help">
            权限字符会被接口和代码长期引用，创建后应保持稳定；建议使用小写英文、数字和下划线。
          </div>
        </el-form-item>
        <el-form-item label="显示顺序" prop="roleSort">
          <el-input-number v-model="form.roleSort" controls-position="right" :min="0" :max="999999" />
        </el-form-item>
        <el-form-item label="初始状态">
          <el-radio-group v-model="form.status">
            <el-radio label="0">启用</el-radio>
            <el-radio label="1">停用</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model.trim="form.remark" type="textarea" :rows="3" maxlength="500" show-word-limit placeholder="说明角色用途和适用人员" />
        </el-form-item>
      </el-form>

      <section v-show="activeStep === 1" aria-label="菜单权限">
        <el-alert
          title="父子联动开启时，选择子项会保留必要的上级目录；最终写入数量以本页统计为准。"
          type="info"
          :closable="false"
          show-icon
          class="role-wizard__alert"
        />
        <div class="role-wizard__tree-toolbar">
          <el-checkbox v-model="menuExpand" @change="toggleTreeExpand('menuTree', $event)">展开全部</el-checkbox>
          <el-checkbox v-model="menuNodeAll" @change="toggleTreeAll('menuTree', menuOptions, $event)">全选</el-checkbox>
          <el-checkbox v-model="form.menuCheckStrictly" @change="handleMenuLinkChange">父子联动</el-checkbox>
        </div>
        <div class="role-wizard__stats" aria-live="polite">
          <el-tag size="small">合计 {{ menuStats.total }}</el-tag>
          <el-tag size="small" type="info">目录 {{ menuStats.directory }}</el-tag>
          <el-tag size="small" type="success">菜单 {{ menuStats.menu }}</el-tag>
          <el-tag size="small" type="warning">按钮 {{ menuStats.button }}</el-tag>
        </div>
        <el-tree
          ref="menuTree"
          class="tree-border role-wizard__tree"
          :data="menuOptions"
          show-checkbox
          node-key="id"
          :check-strictly="!form.menuCheckStrictly"
          :props="treeProps"
          empty-text="暂无可分配菜单"
          @check="syncMenuSelection"
        />
      </section>

      <section v-show="activeStep === 2" aria-label="数据范围">
        <el-alert
          title="默认使用“本部门及以下”，避免新角色意外获得全量数据。自定义范围只允许选择当前管理员有权管理的部门。"
          type="warning"
          :closable="false"
          show-icon
          class="role-wizard__alert"
        />
        <el-radio-group v-model="form.dataScope" class="role-wizard__scope-options" @change="handleDataScopeChange">
          <el-radio-button v-for="item in dataScopeOptions" :key="item.value" :label="item.value">
            {{ item.label }}
          </el-radio-button>
        </el-radio-group>
        <div v-if="form.dataScope === '2'" class="role-wizard__custom-scope">
          <div class="role-wizard__tree-toolbar">
            <el-checkbox v-model="deptExpand" @change="toggleTreeExpand('deptTree', $event)">展开全部</el-checkbox>
            <el-checkbox v-model="deptNodeAll" @change="toggleTreeAll('deptTree', deptOptions, $event)">全选</el-checkbox>
            <el-checkbox v-model="form.deptCheckStrictly">父子联动</el-checkbox>
            <span class="role-wizard__selected-count">已选 {{ selectedDeptIds.length }} 个部门</span>
          </div>
          <el-tree
            ref="deptTree"
            class="tree-border role-wizard__tree"
            :data="deptOptions"
            show-checkbox
            default-expand-all
            node-key="id"
            :check-strictly="!form.deptCheckStrictly"
            :props="treeProps"
            empty-text="暂无可选部门"
            @check="syncDeptSelection"
          />
        </div>
      </section>

      <section v-show="activeStep === 3" aria-label="确认创建">
        <el-alert title="请确认权限和数据范围。创建后可继续添加成员，成员分配不会与本次创建混在一个事务中。" type="info" :closable="false" show-icon />
        <el-descriptions :column="2" border class="role-wizard__summary">
          <el-descriptions-item label="角色名称">{{ form.roleName }}</el-descriptions-item>
          <el-descriptions-item label="权限字符">{{ form.roleKey }}</el-descriptions-item>
          <el-descriptions-item label="显示顺序">{{ form.roleSort }}</el-descriptions-item>
          <el-descriptions-item label="初始状态">{{ form.status === '0' ? '启用' : '停用' }}</el-descriptions-item>
          <el-descriptions-item label="菜单权限" :span="2">
            目录 {{ menuStats.directory }}、菜单 {{ menuStats.menu }}、按钮 {{ menuStats.button }}，共 {{ menuStats.total }} 项
          </el-descriptions-item>
          <el-descriptions-item label="数据范围" :span="2">
            {{ selectedDataScopeLabel }}<span v-if="form.dataScope === '2'">（{{ selectedDeptIds.length }} 个部门）</span>
          </el-descriptions-item>
          <el-descriptions-item v-if="form.remark" label="备注" :span="2">{{ form.remark }}</el-descriptions-item>
        </el-descriptions>
      </section>
    </div>

    <div slot="footer" class="dialog-footer">
      <el-button @click="handleCloseRequest">取 消</el-button>
      <el-button v-if="activeStep > 0" :disabled="submitting" @click="activeStep -= 1">上一步</el-button>
      <el-button v-if="activeStep < 3" type="primary" :disabled="optionsLoading" @click="handleNext">下一步</el-button>
      <el-button v-else type="primary" :loading="submitting" @click="submitRole">确认创建</el-button>
    </div>
  </el-dialog>
</template>

<script>
import { addRole, roleCreateDeptTree } from "@/api/system/role"
import { treeselect as menuTreeselect } from "@/api/system/menu"

function defaultForm() {
  return {
    roleName: "",
    roleKey: "",
    roleSort: 0,
    status: "0",
    dataScope: "4",
    menuIds: [],
    deptIds: [],
    menuCheckStrictly: true,
    deptCheckStrictly: true,
    remark: ""
  }
}

export default {
  name: "RoleWizard",
  props: {
    visible: {
      type: Boolean,
      default: false
    }
  },
  data() {
    return {
      activeStep: 0,
      submitting: false,
      optionsLoading: false,
      form: defaultForm(),
      menuOptions: [],
      deptOptions: [],
      selectedMenuIds: [],
      selectedDeptIds: [],
      menuExpand: false,
      menuNodeAll: false,
      deptExpand: true,
      deptNodeAll: false,
      treeProps: {
        children: "children",
        label: "label",
        disabled: "disabled"
      },
      dataScopeOptions: [
        { value: "4", label: "本部门及以下" },
        { value: "3", label: "本部门" },
        { value: "5", label: "仅本人" },
        { value: "2", label: "自定义" },
        { value: "1", label: "全部数据" }
      ],
      rules: {
        roleName: [{ required: true, message: "角色名称不能为空", trigger: "blur" }],
        roleKey: [{ required: true, message: "权限字符不能为空", trigger: "blur" }],
        roleSort: [{ required: true, message: "显示顺序不能为空", trigger: "change" }]
      }
    }
  },
  computed: {
    menuStats() {
      const byId = {}
      this.flattenNodes(this.menuOptions).forEach(node => { byId[String(node.id)] = node })
      return this.selectedMenuIds.reduce((stats, id) => {
        const node = byId[String(id)] || {}
        if (node.menuType === "M") stats.directory += 1
        if (node.menuType === "C") stats.menu += 1
        if (node.menuType === "F") stats.button += 1
        stats.total += 1
        return stats
      }, { total: 0, directory: 0, menu: 0, button: 0 })
    },
    selectedDataScopeLabel() {
      const selected = this.dataScopeOptions.find(item => item.value === this.form.dataScope)
      return selected ? selected.label : "未选择"
    }
  },
  methods: {
    handleOpen() {
      this.resetWizard()
      this.loadOptions()
      this.$nextTick(() => {
        this.$refs.roleNameInput && this.$refs.roleNameInput.focus()
      })
    },
    handleClose() {
      this.$emit("update:visible", false)
    },
    handleCloseRequest() {
      this.$emit("update:visible", false)
    },
    resetWizard() {
      this.activeStep = 0
      this.submitting = false
      this.form = defaultForm()
      this.selectedMenuIds = []
      this.selectedDeptIds = []
      this.menuExpand = false
      this.menuNodeAll = false
      this.deptExpand = true
      this.deptNodeAll = false
      this.$nextTick(() => {
        this.$refs.basicForm && this.$refs.basicForm.clearValidate()
        this.$refs.menuTree && this.$refs.menuTree.setCheckedKeys([])
        this.$refs.deptTree && this.$refs.deptTree.setCheckedKeys([])
      })
    },
    loadOptions() {
      this.optionsLoading = true
      return Promise.all([menuTreeselect(), roleCreateDeptTree()]).then(([menuResponse, deptResponse]) => {
        this.menuOptions = menuResponse.data || []
        this.deptOptions = deptResponse.data || []
      }).finally(() => {
        this.optionsLoading = false
      })
    },
    flattenNodes(nodes) {
      const flattened = []
      const visit = list => (list || []).forEach(node => {
        flattened.push(node)
        visit(node.children)
      })
      visit(nodes)
      return flattened
    },
    collectCheckedKeys(refName) {
      const tree = this.$refs[refName]
      if (!tree) return []
      return Array.from(new Set([...(tree.getCheckedKeys() || []), ...(tree.getHalfCheckedKeys() || [])]))
    },
    syncMenuSelection() {
      this.$nextTick(() => {
        this.selectedMenuIds = this.collectCheckedKeys("menuTree")
        this.form.menuIds = this.selectedMenuIds.slice()
      })
    },
    syncDeptSelection() {
      this.$nextTick(() => {
        this.selectedDeptIds = this.collectCheckedKeys("deptTree")
        this.form.deptIds = this.selectedDeptIds.slice()
      })
    },
    handleMenuLinkChange() {
      this.syncMenuSelection()
    },
    handleDataScopeChange(value) {
      if (value !== "2") {
        this.selectedDeptIds = []
        this.form.deptIds = []
        this.$nextTick(() => this.$refs.deptTree && this.$refs.deptTree.setCheckedKeys([]))
      }
    },
    toggleTreeExpand(refName, expanded) {
      const tree = this.$refs[refName]
      if (!tree || !tree.store || !tree.store.nodesMap) return
      Object.keys(tree.store.nodesMap).forEach(key => {
        tree.store.nodesMap[key].expanded = expanded
      })
    },
    toggleTreeAll(refName, nodes, checked) {
      const tree = this.$refs[refName]
      if (!tree) return
      tree.setCheckedNodes(checked ? nodes : [])
      if (refName === "menuTree") this.syncMenuSelection()
      if (refName === "deptTree") this.syncDeptSelection()
    },
    handleNext() {
      if (this.activeStep === 0) {
        this.$refs.basicForm.validate(valid => {
          if (valid) this.activeStep = 1
        })
        return
      }
      if (this.activeStep === 1) {
        this.selectedMenuIds = this.collectCheckedKeys("menuTree")
        this.form.menuIds = this.selectedMenuIds.slice()
        this.activeStep = 2
        return
      }
      if (this.activeStep === 2) {
        if (this.form.dataScope === "2") {
          this.selectedDeptIds = this.collectCheckedKeys("deptTree")
          this.form.deptIds = this.selectedDeptIds.slice()
          if (this.selectedDeptIds.length === 0) {
            this.$modal.msgWarning("自定义数据范围至少选择一个部门")
            return
          }
        }
        this.activeStep = 3
      }
    },
    submitRole() {
      const payload = Object.assign({}, this.form, {
        menuIds: this.selectedMenuIds.slice(),
        deptIds: this.form.dataScope === "2" ? this.selectedDeptIds.slice() : []
      })
      this.submitting = true
      addRole(payload).then(response => {
        this.$emit("created", {
          roleId: response.data,
          roleName: payload.roleName
        })
        this.$emit("update:visible", false)
      }).finally(() => {
        this.submitting = false
      })
    }
  }
}
</script>

<style lang="scss" scoped>
.role-wizard__steps {
  margin: 0 0 24px;
}

.role-wizard__body {
  min-height: 390px;
}

.role-wizard__field-help,
.role-wizard__selected-count {
  color: #909399;
  font-size: 12px;
  line-height: 20px;
}

.role-wizard__alert {
  margin-bottom: 14px;
}

.role-wizard__tree-toolbar,
.role-wizard__stats {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 10px;
  margin-bottom: 10px;
}

.role-wizard__tree {
  max-height: 300px;
  overflow: auto;
}

.role-wizard__scope-options {
  display: flex;
  flex-wrap: wrap;
  margin-bottom: 16px;
}

.role-wizard__custom-scope {
  margin-top: 8px;
}

.role-wizard__summary {
  margin-top: 18px;
}
</style>
