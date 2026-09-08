<template>
  <div id="app" v-accessible-shell>
    <router-view :key="routeViewKey" />
  </div>
</template>

<script>
export default {
  name: "App",
  computed: {
    routeViewKey() {
      const path = this.$route && this.$route.path ? this.$route.path : ""
      if (path.indexOf("/mobile/") === 0) return path
      return "desktop-router-view"
    }
  }
}
</script>
