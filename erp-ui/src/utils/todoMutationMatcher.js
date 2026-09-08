const TODO_MUTATION_RULES = Object.freeze([
  ["POST", /^\/inventory\/purchase\/submit$/],
  ["POST", /^\/inventory\/purchase\/(?:receive|qc)\/\d+$/],
  ["DELETE", /^\/inventory\/purchase\/(?:delete\/)?\d+$/],
  ["POST", /^\/inventory\/sales\/submit$/],
  ["DELETE", /^\/inventory\/sales\/\d+$/],
  ["POST", /^\/inventory\/deliveryNotice\/(?:create|deliver)\/\d+$/],
  ["DELETE", /^\/inventory\/deliveryNotice\/\d+$/],
  ["POST", /^\/inventory\/transfer\/(?:submit|approve)$/],
  ["POST", /^\/inventory\/transfer\/\d+\/withdraw$/],
  ["POST", /^\/inventory\/transfer\/(?:deliver|receive)\/\d+$/],
  ["POST", /^\/inventory\/transfer\/shipment\/receive\/\d+$/],
  ["DELETE", /^\/inventory\/transfer\/\d+$/],
  ["POST", /^\/inventory\/stockCheck\/(?:submit|restart|cancel)\/\d+$/],
  ["POST", /^\/inventory\/stockCheck\/\d+\/withdraw$/],
  ["POST", /^\/inventory\/stockCheck\/approval\/\d+\/(?:approve|reject)$/],
  ["DELETE", /^\/inventory\/stockCheck\/[\d,]+$/],
  ["POST", /^\/inventory\/(?:purchaseReturn|salesReturn)\/submit$/],
  ["POST", /^\/inventory\/(?:purchaseReturn|salesReturn)\/confirm\/\d+$/],
  ["DELETE", /^\/inventory\/(?:purchaseReturn|salesReturn)\/\d+$/],
  ["POST", /^\/inventory\/stock\/adjust$/],
  ["POST", /^\/oa\/purchase\/submit$/],
  ["POST", /^\/oa\/purchase\/\d+\/(?:close|withdraw)$/],
  ["POST", /^\/approval\/tasks\/\d+\/(?:approve|return|reject)$/],
  ["POST", /^\/system\/hr\/health-certificate\/me\/\d+\/withdraw$/],
  ["POST", /^\/oa\/fixedAsset\/repair\/submit$/],
  ["POST", /^\/oa\/fixedAsset\/repair\/\d+\/confirm$/],
  ["POST", /^\/oa\/laborContract\/send$/],
  ["POST", /^\/oa\/laborContract\/\d+\/void$/],
  ["POST", /^\/oa\/laborContract\/mobile\/\d+\/sign$/],
  ["POST", /^\/oa\/signPackage\/\d+\/(?:send|void|finalize)$/],
  ["POST", /^\/oa\/signPackage\/mobile\/\d+\/(?:read\/\d+|sign|refuse|final-confirm)$/],
  ["POST", /^\/oa\/signTask\/\d+\/(?:revalidate|send|retry|cancel|resolve|remind)$/],
  ["POST", /^\/oa\/signTask\/\d+\/notification\/retry$/],
  ["PUT", /^\/oa\/signTask\/onboard\/import\/\d+\/rows\/\d+$/],
  ["POST", /^\/oa\/signTask\/onboard\/import\/\d+\/(?:data-request\/send|generate)$/],
  ["POST", /^\/oa\/signTask\/onboard\/data-request\/\d+\/(?:review|submit)$/],
  ["POST", /^\/system\/hr\/employee$/],
  ["PUT", /^\/system\/hr\/employee$/],
  ["POST", /^\/system\/hr\/onboarding\/\d+\/(?:confirm|cancel)$/],
  ["POST", /^\/system\/hr\/import\/confirm$/],
  ["PUT", /^\/system\/user\/changeStatus$/]
])

function normalizeMethod(method) {
  return typeof method === "string" ? method.trim().toUpperCase() : ""
}

function requestPath(url) {
  if (typeof url !== "string") return ""
  return url.trim().split(/[?#]/, 1)[0]
}

function matchTodoMutation(method, url) {
  const normalizedMethod = normalizeMethod(method)
  const path = requestPath(url)
  if (!normalizedMethod || !path) return false
  return TODO_MUTATION_RULES.some(([allowedMethod, pattern]) =>
    allowedMethod === normalizedMethod && pattern.test(path)
  )
}

function scheduleTodoMutationRefresh(method, url, dispatch) {
  if (!matchTodoMutation(method, url) || typeof dispatch !== "function") return false
  Promise.resolve()
    .then(() => dispatch("todo/invalidateAfterMutation"))
    .catch(() => {})
  return true
}

module.exports = {
  TODO_MUTATION_RULES,
  matchTodoMutation,
  scheduleTodoMutationRefresh
}
