<template>
  <div class="popup-result">
    <p class="title">最近 5 次运行时间（本机时区预览）</p>
    <ul class="popup-result-scroll"><li v-for="item in resultList" :key="item">{{ item }}</li></ul>
    <p>保存时由服务器校验，实际执行以服务器时区和任务计划为准。</p>
  </div>
</template>
<script>
const { previewCronExpression, expandField } = require('@/utils/cronExpression')
export default {
  name: 'crontab-result',
  props: ['ex'],
  data() { return { resultList: [], isShow: false } },
  watch: { ex: 'expressionChange' },
  mounted() { this.expressionChange() },
  methods: {
    expressionChange() {
      const result = previewCronExpression(this.ex)
      this.resultList = result.times.map(value => {
        const pad = n => String(n).padStart(2, '0')
        return `${value.getFullYear()}-${pad(value.getMonth() + 1)}-${pad(value.getDate())} ${pad(value.getHours())}:${pad(value.getMinutes())}:${pad(value.getSeconds())}`
      })
      if (result.error) this.resultList.push(result.error)
      this.isShow = true
    },
    // Kept for callers/tests of the former enumerator; never loops without a bound.
    getAverageArr(rule, limit) { return expandField(rule, 0, limit) }
  }
}
</script>
