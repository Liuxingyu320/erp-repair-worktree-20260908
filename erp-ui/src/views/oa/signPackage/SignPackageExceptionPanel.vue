<template>
  <section class="sign-package-exception-panel">
    <h3>异常处置记录</h3>
    <el-descriptions :column="2" border size="small">
      <el-descriptions-item label="终态">{{ statusLabel(signPackage.status) }}</el-descriptions-item>
      <el-descriptions-item label="终止时间">{{ signPackage.terminalTime || '-' }}</el-descriptions-item>
      <el-descriptions-item label="终态原因码">{{ signPackage.terminalReasonCode || '-' }}</el-descriptions-item>
      <el-descriptions-item label="处置状态">{{ resolutionStatusLabel }}</el-descriptions-item>
      <el-descriptions-item label="终态原因" :span="2">{{ signPackage.terminalReasonDetail || '-' }}</el-descriptions-item>
      <el-descriptions-item label="处置原因码">{{ signPackage.resolutionReasonCode || '-' }}</el-descriptions-item>
      <el-descriptions-item label="处置时间">{{ signPackage.resolvedTime || '-' }}</el-descriptions-item>
      <el-descriptions-item label="处置说明" :span="2">{{ signPackage.resolutionReasonDetail || '-' }}</el-descriptions-item>
      <el-descriptions-item label="替代来源包">{{ signPackage.reissueOfPackageId || '-' }}</el-descriptions-item>
      <el-descriptions-item label="替代目标包">{{ signPackage.reissuedToPackageId || '-' }}</el-descriptions-item>
    </el-descriptions>
  </section>
</template>

<script>
import { signPackageStatusLabel } from '@/utils/signDictionary'

export default {
  name: 'SignPackageExceptionPanel',
  props: {
    signPackage: { type: Object, required: true }
  },
  computed: {
    resolutionStatusLabel() {
      return {
        OPEN: '待人工处置',
        CLOSED: '已关闭',
        REISSUED: '已创建替代版本'
      }[this.signPackage.resolutionStatus] || '未记录在线处置'
    }
  },
  methods: {
    statusLabel(value) {
      return signPackageStatusLabel(value)
    }
  }
}
</script>

<style lang="scss" scoped>
.sign-package-exception-panel { margin-top: 16px; }
.sign-package-exception-panel h3 { margin: 0 0 10px; color: #991b1b; font-size: 15px; }
</style>
