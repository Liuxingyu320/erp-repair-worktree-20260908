# 2026-07-23 Excel 入职合同生产 E2E 证据

## 问题 01：编辑印章时报“配置文件不存在”

- 失败证据：`01-seal-save-config-file-missing-before.png`
- 截图 SHA-256：`976ed71fa83a84b8468fcfad1d820e0a474bd4f335b91b83964e11e7078065f0`
- 生产记录：印章 URL 为 `/prod-api/file/public/...`。
- 物理文件：新上传文件位于共享 `uploadPath/public/2026/07/23/`，大小 55,761 字节。
- 根因：OA 文件解析器只识别 `/file/public/...`，把带网关前缀的 URL 当成 `/prod-api/...` 绝对磁盘路径。
- 代码修复：兼容可配置网关前缀，同时保留公开目录边界与路径穿越防护。
- 单元测试：`OaSignDocumentServiceTest` 30 个测试通过。
- 生产回归：待配置修复部署后补充截图、服务状态和保存结果。
