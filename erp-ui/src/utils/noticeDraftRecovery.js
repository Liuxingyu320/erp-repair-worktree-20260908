function draftFields(draft) {
  const data = draft || {}
  return JSON.stringify({
    noticeId: String(data.noticeId || ''), noticeTitle: String(data.noticeTitle || ''),
    noticeType: String(data.noticeType || ''), noticeContent: String(data.noticeContent || ''),
    audienceType: data.audienceType || '', expireTime: data.expireTime || null,
    audiences: (data.audiences || []).map(a => [a.targetType, String(a.targetId), Boolean(a.includeChildren)]).sort((a, b) => JSON.stringify(a).localeCompare(JSON.stringify(b)))
  })
}
function matchesSavedDraft(expected, actual) {
  if (!expected || !actual || !expected.noticeId || String(actual.noticeId) !== String(expected.noticeId) || actual.lifecycleStatus !== 'DRAFT') return false
  const version = Number(expected.version), currentVersion = Number(actual.version)
  return Number.isSafeInteger(version) && Number.isSafeInteger(currentVersion) && currentVersion === version + 1 && draftFields(expected) === draftFields(actual)
}
function unknownDraftResult(error) {
  const response = error && error.response
  return !response || Number(response.status) >= 500
}
module.exports = { draftFields, matchesSavedDraft, unknownDraftResult }
