const LONG_MAX = "9223372036854775807"
function evidenceId(value) {
  if (typeof value === "number" && !Number.isSafeInteger(value)) throw Error("附件编号超出安全精度，不能用于提交")
  const id = String(value == null ? "" : value)
  if (!/^[1-9]\d{0,18}$/.test(id) || id.length === 19 && id > LONG_MAX) throw Error("附件编号无效")
  return id
}
function parseEvidence(value) {
  const text = String(value || "")
  if (!text) return { ids: [], legacy: "" }
  const parts = text.split(",")
  if (text.length > 2000 || parts.length > 10) return { ids: [], legacy: text }
  try {
    const ids = parts.map(part => {
      if (!part.startsWith("drive:")) throw Error("legacy")
      return evidenceId(part.slice(6))
    })
    if (new Set(ids).size !== ids.length) throw Error("duplicate")
    return { ids, legacy: "" }
  } catch (_) { return { ids: [], legacy: text } }
}
function encodeEvidence(ids) {
  if (ids.length > 10) throw Error("每项最多选择10个凭证附件")
  return Array.from(new Set(ids.map(evidenceId))).map(id => "drive:" + id).join(",")
}
function evidenceNode(node) {
  if (!node || node.nodeType !== "FILE" || node.status !== "ACTIVE") throw Error("请选择可用文件，目录和回收站文件不能作为凭证")
  return { id: evidenceId(node.nodeId), name: String(node.nodeName || "附件"),
    contentType: String(node.contentType || "").toLowerCase().split(";", 1)[0], canPreview: node.canPreview === true }
}
function hasEvidence(value) { return parseEvidence(value).ids.length > 0 }
module.exports = { evidenceId, parseEvidence, encodeEvidence, evidenceNode, hasEvidence }
