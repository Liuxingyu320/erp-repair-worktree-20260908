<template>
  <el-drawer title="高级筛选" :visible.sync="visible" size="420px" append-to-body :wrapper-closable="false">
    <div class="filter-drawer-body">
      <el-alert title="高级条件可按当前登录用户保存，下次进入自动恢复。" type="info" :closable="false" show-icon />
      <el-form label-width="88px" size="small" class="filter-form">
        <el-form-item label="工号"><el-input v-model="draft.employeeNo" clearable /></el-form-item>
        <el-form-item label="手机号"><el-input v-model="draft.phonenumber" clearable /></el-form-item>
        <el-form-item label="员工状态">
          <el-select v-model="draft.employeeStatus" clearable filterable><el-option v-for="item in employeeStatusOptions" :key="item" :label="item" :value="item" /></el-select>
        </el-form-item>
        <el-form-item label="人员类别">
          <el-select v-model="draft.employeeCategory" clearable filterable allow-create><el-option v-for="item in employeeCategoryOptions" :key="item" :label="item" :value="item" /></el-select>
        </el-form-item>
        <el-form-item label="法人单位"><el-input v-model="draft.legalEntity" clearable /></el-form-item>
        <el-form-item label="工作地"><el-input v-model="draft.workLocation" clearable /></el-form-item>
        <el-form-item label="配置状态">
          <el-select v-model="draft.setupStatus" clearable>
            <el-option label="未分配角色" value="missingRole" />
            <el-option label="未授权管理范围" value="missingShopScope" />
            <el-option label="已完成" value="complete" />
          </el-select>
        </el-form-item>
        <el-form-item label="岗位">
          <el-select v-model="draft.postId" clearable filterable>
            <el-option v-for="item in postOptions" :key="item.postId" :label="item.postName" :value="item.postId" :disabled="item.status == 1" />
          </el-select>
        </el-form-item>
      </el-form>
    </div>
    <div class="filter-drawer-footer">
      <el-button size="small" @click="clear">清空条件</el-button>
      <el-button size="small" type="danger" plain @click="clearSaved">清除已保存</el-button>
      <span class="footer-spacer" />
      <el-button size="small" @click="apply(false)">仅应用</el-button>
      <el-button size="small" type="primary" @click="apply(true)">保存并应用</el-button>
    </div>
  </el-drawer>
</template>

<script>
const EMPTY_FILTERS = {
  employeeNo: undefined,
  phonenumber: undefined,
  employeeStatus: undefined,
  employeeCategory: undefined,
  legalEntity: undefined,
  workLocation: undefined,
  setupStatus: undefined,
  postId: undefined
}

export default {
  name: "UserFilterDrawer",
  props: {
    employeeStatusOptions: { type: Array, default: () => [] },
    employeeCategoryOptions: { type: Array, default: () => [] },
    postOptions: { type: Array, default: () => [] }
  },
  data() {
    return { visible: false, draft: { ...EMPTY_FILTERS } }
  },
  methods: {
    open(filters) {
      this.draft = { ...EMPTY_FILTERS, ...(filters || {}) }
      this.visible = true
    },
    clear() {
      this.draft = { ...EMPTY_FILTERS }
    },
    clearSaved() {
      this.$emit("clear-saved")
      this.$message.success("已清除当前用户保存的高级筛选")
    },
    apply(save) {
      this.$emit("apply", { filters: { ...this.draft }, save })
      this.visible = false
    }
  }
}
</script>

<style lang="scss" scoped>
.filter-drawer-body { padding: 0 20px 84px; }
.filter-form { margin-top: 20px; }
.filter-form .el-select { width: 100%; }
.filter-drawer-footer { position: absolute; left: 0; right: 0; bottom: 0; display: flex; align-items: center; padding: 14px 20px; border-top: 1px solid #ebeef5; background: #fff; }
.footer-spacer { flex: 1; }
</style>
