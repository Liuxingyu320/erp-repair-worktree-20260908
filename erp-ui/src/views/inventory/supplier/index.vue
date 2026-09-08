<template>
  <div class="app-container warehouse-page">
    <inventory-page-hero
      title="供应商管理"
      eyebrow="供应协同"
      description="集中维护合作方、联系人、结算方式与供货商品关系，让采购协同更顺畅。"
      scope-text="统一供应商档案"
      icon="el-icon-truck"
      tone="teal"
      :features="['合作档案', '供货商品', '结算信息']"
    />
    <el-card shadow="never" class="mb12">
      <el-form :model="queryParams" inline size="small">
        <el-form-item label="供应商名称">
          <el-input v-model="queryParams.supplierName" placeholder="供应商名称" clearable @keyup.enter.native="getList"/>
        </el-form-item>
        <el-form-item label="供应商编码">
          <el-input v-model="queryParams.supplierCode" placeholder="供应商编码" clearable @keyup.enter.native="getList"/>
        </el-form-item>
        <el-form-item label="合作状态">
          <el-select v-model="queryParams.cooperationStatus" clearable placeholder="全部状态">
            <el-option label="合作中" value="0"/>
            <el-option label="暂停" value="1"/>
            <el-option label="终止" value="2"/>
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="queryParams.status" clearable placeholder="全部">
            <el-option label="正常" value="0"/>
            <el-option label="停用" value="1"/>
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button v-hasPermi="['inv:supplier:list']" type="primary" size="mini" icon="el-icon-search" @click="getList">搜索</el-button>
          <el-button v-hasPermi="['inv:supplier:add']" size="mini" icon="el-icon-plus" @click="openForm(null)">新增供应商</el-button>
          <el-button v-hasPermi="['inv:supplier:export']" size="mini" icon="el-icon-download" @click="handleExport">导出</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card shadow="never">
      <el-table v-accessible-table="'供应商列表'" v-loading="loading" :data="list" size="small">
        <el-table-column label="供应商编码" prop="supplierCode" width="140"/>
        <el-table-column label="供应商名称" prop="supplierName" min-width="160"/>
        <el-table-column label="联系人" prop="contactPerson" width="100"/>
        <el-table-column label="联系电话" prop="contactPhone" width="120"/>
        <el-table-column label="电子邮箱" prop="contactEmail" width="140"/>
        <el-table-column label="结算方式" prop="settlementMethod" width="100"/>
        <el-table-column label="合作状态" prop="cooperationStatus" width="100">
          <template slot-scope="scope">
            <el-tag :type="statusTag(scope.row.cooperationStatus)" size="mini">{{ statusLabel(scope.row.cooperationStatus) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="是否启用" prop="status" width="80">
          <template slot-scope="scope">
            <el-tag :type="scope.row.status === '0' ? 'success' : 'danger'" size="mini">{{ scope.row.status === '0' ? '正常' : '停用' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="创建时间" prop="createTime" width="160"/>
        <el-table-column label="操作" width="230" fixed="right">
          <template slot-scope="scope">
            <el-button v-hasPermi="['inv:supplier:query']" type="text" size="mini" icon="el-icon-view" @click="openDetail(scope.row)">详情</el-button>
            <el-button v-hasPermi="['inv:supplier:edit']" type="text" size="mini" icon="el-icon-edit" @click="openForm(scope.row)">编辑</el-button>
            <el-dropdown
              v-hasPermi="['inv:supplier:edit']"
              class="status-dropdown"
              trigger="click"
              @command="handleCooperationStatusChange(scope.row, $event)"
            >
              <el-button type="text" size="mini" icon="el-icon-refresh" :disabled="statusUpdatingId === scope.row.supplierId">状态</el-button>
              <el-dropdown-menu slot="dropdown">
                <el-dropdown-item command="0" :disabled="scope.row.cooperationStatus === '0'">合作中</el-dropdown-item>
                <el-dropdown-item command="1" :disabled="scope.row.cooperationStatus === '1'">暂停</el-dropdown-item>
                <el-dropdown-item command="2" :disabled="scope.row.cooperationStatus === '2'">终止</el-dropdown-item>
              </el-dropdown-menu>
            </el-dropdown>
            <el-button v-hasPermi="['inv:supplier:remove']" type="text" size="mini" icon="el-icon-delete" class="text-danger" @click="handleDelete(scope.row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <pagination v-show="total>0" :total="total" :page.sync="queryParams.pageNum" :limit.sync="queryParams.pageSize" @pagination="getList"/>
    </el-card>

    <el-dialog
      :title="detailTitle"
      :visible.sync="detailOpen"
      width="940px"
      class="scrollbar"
      append-to-body
      :close-on-click-modal="false"
    >
      <div v-loading="detailLoading">
        <el-descriptions :column="2" border size="small">
          <el-descriptions-item label="供应商名称">{{ displayText(detailForm.supplierName) }}</el-descriptions-item>
          <el-descriptions-item label="供应商编码">{{ displayText(detailForm.supplierCode) }}</el-descriptions-item>
          <el-descriptions-item label="联系人">{{ displayText(detailForm.contactPerson) }}</el-descriptions-item>
          <el-descriptions-item label="联系电话">{{ displayText(detailForm.contactPhone) }}</el-descriptions-item>
          <el-descriptions-item label="结算方式">{{ displayText(detailForm.settlementMethod) }}</el-descriptions-item>
          <el-descriptions-item label="合作状态">{{ statusLabel(detailForm.cooperationStatus) }}</el-descriptions-item>
          <el-descriptions-item label="地址" :span="2">{{ displayText(detailForm.address) }}</el-descriptions-item>
          <el-descriptions-item label="备注" :span="2">{{ displayText(detailForm.remark) }}</el-descriptions-item>
        </el-descriptions>

        <div class="detail-section-header">
          <span>供货商品</span>
          <el-tag size="mini" type="info">{{ supplierProductList.length }} 个</el-tag>
        </div>
        <el-table
          v-loading="supplierProductLoading"
          :data="supplierProductList"
          size="small"
          border
          max-height="360"
          empty-text="暂无供货商品"
        >
          <el-table-column label="供货商品" prop="productName" min-width="150" show-overflow-tooltip/>
          <el-table-column label="商品编码" prop="productCode" width="130" show-overflow-tooltip/>
          <el-table-column label="分类" prop="categoryName" width="110" show-overflow-tooltip/>
          <el-table-column label="规格" prop="spec" width="130" show-overflow-tooltip/>
          <el-table-column label="单位" prop="unit" width="70"/>
          <el-table-column label="采购价" width="90" align="right">
            <template slot-scope="scope">{{ formatMoney(scope.row.purchasePrice) }}</template>
          </el-table-column>
          <el-table-column label="售价500g" width="90" align="right">
            <template slot-scope="scope">{{ formatMoney(scope.row.salePrice500g || scope.row.salesPrice) }}</template>
          </el-table-column>
        </el-table>
      </div>
      <div slot="footer">
        <el-button @click="detailOpen = false">关闭</el-button>
      </div>
    </el-dialog>

    <el-dialog
      :title="formTitle"
      :visible.sync="dialogOpen"
      width="560px"
      class="scrollbar"
      append-to-body
      :close-on-click-modal="false"
    >
      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
        <el-row>
          <el-col :span="12">
            <el-form-item label="供应商名称" prop="supplierName">
              <el-input v-model="form.supplierName" maxlength="128"/>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="供应商编码">
              <el-input v-model="form.supplierCode" maxlength="64"/>
            </el-form-item>
          </el-col>
        </el-row>
        <el-row>
          <el-col :span="12">
            <el-form-item label="联系人">
              <el-input v-model="form.contactPerson" maxlength="64"/>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="联系电话">
              <el-input v-model="form.contactPhone" maxlength="32"/>
            </el-form-item>
          </el-col>
        </el-row>
        <el-row>
          <el-col :span="12">
            <el-form-item label="电子邮箱">
              <el-input v-model="form.contactEmail" maxlength="64"/>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="结算方式">
              <el-select v-model="form.settlementMethod" style="width:100%">
                <el-option label="现结" value="现结"/>
                <el-option label="月结" value="月结"/>
                <el-option label="季结" value="季结"/>
                <el-option label="预付款" value="预付款"/>
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
        <el-row>
          <el-col :span="12">
            <el-form-item label="合作状态">
              <el-radio-group v-model="form.cooperationStatus">
                <el-radio label="0">合作中</el-radio>
                <el-radio label="1">暂停</el-radio>
                <el-radio label="2">终止</el-radio>
              </el-radio-group>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="是否启用">
              <el-radio-group v-model="form.status">
                <el-radio label="0">正常</el-radio>
                <el-radio label="1">停用</el-radio>
              </el-radio-group>
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="地址">
          <el-input v-model="form.address" maxlength="256" type="textarea" :rows="2"/>
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="form.remark" maxlength="500" type="textarea" :rows="2"/>
        </el-form-item>
      </el-form>
      <div slot="footer">
        <el-button @click="dialogOpen = false">取消</el-button>
        <el-button v-hasPermi="['inv:supplier:add','inv:supplier:edit']" type="primary" :loading="submitLoading" @click="doSubmit">确认</el-button>
      </div>
    </el-dialog>
  </div>
</template>

<script>
import { listSupplier, getSupplier, getSupplierProducts, addSupplier, updateSupplier, delSupplier } from "@/api/inventory/supplier"

export default {
  name: "InvSupplier",
  data() {
    return {
      loading: false, submitLoading: false, statusUpdatingId: undefined, total: 0, list: [], dialogOpen: false,
      detailOpen: false, detailLoading: false, supplierProductLoading: false, detailForm: {}, supplierProductList: [],
      queryParams: { pageNum: 1, pageSize: 10, supplierName: undefined, supplierCode: undefined, cooperationStatus: undefined, status: undefined },
      form: { supplierId: undefined, supplierName: "", supplierCode: "", contactPerson: "", contactPhone: "", contactEmail: "", address: "", settlementMethod: "现结", cooperationStatus: "0", status: "0" },
      rules: { supplierName: [{ required: true, message: "供应商名称不能为空", trigger: "blur" }] }
    }
  },
  computed: {
    formTitle() { return this.form.supplierId ? "编辑供应商" : "新增供应商" },
    detailTitle() { return this.detailForm.supplierName ? "供应商详情 - " + this.detailForm.supplierName : "供应商详情" }
  },
  created() { this.getList() },
  methods: {
    getList() {
      this.loading = true
      listSupplier(this.queryParams).then(res => { this.list = res.rows || []; this.total = res.total || 0 }).finally(() => { this.loading = false })
    },
    openForm(row) {
      if (row) {
        getSupplier(row.supplierId).then(res => { this.form = res.data; this.dialogOpen = true })
      } else {
        this.form = { supplierId: undefined, supplierName: "", supplierCode: "", contactPerson: "", contactPhone: "", contactEmail: "", address: "", settlementMethod: "现结", cooperationStatus: "0", status: "0" }
        this.dialogOpen = true
      }
      this.$nextTick(() => { this.$refs.formRef && this.$refs.formRef.clearValidate() })
    },
    openDetail(row) {
      this.detailOpen = true
      this.detailLoading = true
      this.supplierProductLoading = true
      this.detailForm = {}
      this.supplierProductList = []
      Promise.all([getSupplier(row.supplierId), getSupplierProducts(row.supplierId)]).then(([supplierRes, productRes]) => {
        this.detailForm = supplierRes.data || {}
        this.supplierProductList = productRes.data || []
      }).finally(() => {
        this.detailLoading = false
        this.supplierProductLoading = false
      })
    },
    doSubmit() {
      this.$refs.formRef.validate(valid => {
        if (!valid) return
        this.submitLoading = true
        const action = this.form.supplierId ? updateSupplier(this.form) : addSupplier(this.form)
        action.then(() => { this.$modal.msgSuccess("操作成功"); this.dialogOpen = false; this.getList() }).finally(() => { this.submitLoading = false })
      })
    },
    handleDelete(row) {
      this.$modal.confirm("确认删除供应商「" + row.supplierName + "」？").then(() => {
        delSupplier(row.supplierId).then(() => { this.$modal.msgSuccess("删除成功"); this.getList() })
      })
    },
    handleCooperationStatusChange(row, cooperationStatus) {
      const label = this.statusLabel(cooperationStatus)
      if (row.cooperationStatus === cooperationStatus) {
        this.$modal.msgWarning("供应商已是" + label + "状态")
        return
      }
      this.$modal.confirm("确认将供应商「" + row.supplierName + "」合作状态改为" + label + "？").then(() => {
        this.statusUpdatingId = row.supplierId
        updateSupplier(Object.assign({}, row, { cooperationStatus: cooperationStatus })).then(() => {
          this.$modal.msgSuccess("合作状态已更新为" + label)
          this.getList()
        }).finally(() => {
          this.statusUpdatingId = undefined
        })
      })
    },
    handleExport() {
      this.$modal.confirm("确认导出当前查询条件下的供应商数据？").then(() => {
        this.download("inventory/supplier/export", { ...this.queryParams }, this.exportFileName("供应商数据"))
      })
    },
    statusLabel(val) {
      const map = { "0": "合作中", "1": "暂停", "2": "终止" }
      return map[val] || "未知合作状态"
    },
    statusTag(val) {
      const map = { "0": "success", "1": "warning", "2": "danger" }
      return map[val] || ""
    },
    displayText(value) {
      return value === undefined || value === null || value === "" ? "-" : value
    },
    formatMoney(value) {
      if (value === undefined || value === null || value === "") {
        return "-"
      }
      const number = Number(value)
      if (Number.isNaN(number)) {
        return value
      }
      return number.toFixed(2)
    }
  }
}
</script>
<style lang="scss" scoped>
.mb12 { margin-bottom: 12px }
.text-danger { color: #F56C6C }
.status-dropdown { margin: 0 8px }
.detail-section-header {
  align-items: center;
  display: flex;
  justify-content: space-between;
  margin: 16px 0 10px;
  font-size: 14px;
  font-weight: 600;
  color: #303133;
}

@import "~@/styles/warehouse-management-page.scss";
</style>
