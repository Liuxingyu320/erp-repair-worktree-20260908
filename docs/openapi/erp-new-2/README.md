# ERP-NEW_2 OpenAPI 契约目录

当前状态：**尚未生成、尚未冻结**。

这里不能放静态正则推导的伪 OpenAPI。正式 `manifest.json` 与模块 `*.openapi.json` 必须来自 ERP-NEW_2 完全隔离的本地生成流程，并满足 `docs/erp-new-2-migration/openapi-generation-readiness.md` 的证据门槛。

前端门禁：

- `npm run openapi:check`：缺少 manifest、哈希不符、任意层级出现网络服务器、存在外部 `$ref`、重复 operationId 或受保护原项目标识时失败。
- `npm run openapi:generate`：门禁通过后，才在 `erp-ui-next/src/api/generated/<module>/` 生成类型；不生成运行时 HTTP 客户端。
- 当前执行 `npm run openapi:check` 应明确失败，这是“没有正式契约就拒绝生成”的预期行为。

任何真实凭据、本地端口、内部实例地址或共享环境信息都不得进入此目录。
