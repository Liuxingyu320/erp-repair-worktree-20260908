<template>
  <el-alert type="info" :closable="false" show-icon style="margin-bottom:16px">
    <template slot="title">此入口仅保留历史记录。当前薪资与新合同请使用“人事管理 → 员工档案 → 批量处理入职合同”。</template>
    <el-button v-hasPermi="['hr:employee:list']" type="text" @click="$router.push('/hr/employee')">前往员工档案</el-button>
  </el-alert>
</template>
<script>export default { name: 'LegacySalaryNotice' }</script>
