<template>
  <hr-employee-list
    title="员工档案"
    subtitle="员工资料、合同、社保和组织岗位档案"
    source="employee"
    mode="employee"
    show-completeness
    show-export
    show-onboard-contract-actions
    :initial-employee-id="routeEmployeeId"
    :initial-action="routeAction"
    :initial-filters="routeFilters"
  />
</template>

<script>
import HrEmployeeList from "../components/HrEmployeeList"

export default {
  name: "HrEmployee",
  components: { HrEmployeeList },
  computed: {
    routeEmployeeId() {
      const raw = this.$route && this.$route.query ? this.$route.query.userId : undefined
      if (raw === undefined || raw === null) return undefined
      const value = String(raw).trim()
      if (!/^\d+$/.test(value)) return undefined
      return value.replace(/^0+/, "") || undefined
    },
    routeAction() {
      const query = (this.$route && this.$route.query) || {}
      return query.action === "initializeProfile" ? "initializeProfile" : undefined
    },
    routeFilters() {
      const query = (this.$route && this.$route.query) || {}
      const positive = value => /^\d+$/.test(String(value || "")) && Number.isSafeInteger(Number(value)) && Number(value) > 0
        ? Number(value)
        : undefined
      const literalTrue = value => value === true || value === "true" ? true : undefined
      return {
        deptId: positive(query.deptId || query.contextDeptId),
        contractDue: literalTrue(query.contractDue),
        offboardAccountOnly: literalTrue(query.offboardAccountOnly)
      }
    }
  }
}
</script>
