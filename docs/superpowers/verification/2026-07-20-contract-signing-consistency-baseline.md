# 合同签约一致性修复基线

> 记录时间：2026-07-20
> 分支：`codex/erp-product-optimization`
> 基线提交：`b408b7054a481fac692878fcdc24e7ea5bc9cc68`

## 历史 package-18 产物

此前审阅记录定位过以下文件，但在本轮实施开始时文件已不位于工作区；本轮未执行删除，也不重建或伪造历史产物：

- `uploadPath/private/sign-package/task-none/package-18/SP-18-V1/14d12cd6d1c24638b16712ef7dd91388.pdf`
- `uploadPath/private/sign-package/task-none/package-18/SP-18-V1/bebfbbd3428649d6b9976827c8ac18b7.pdf`
- `uploadPath/private/sign-package/task-none/package-18/SP-18-V2/345f2db6d893448cb7ca14bba866b4ac.pdf`
- `uploadPath/private/sign-package/task-none/package-18/SP-18-V2/a1f60a5fed924592b0af11f4de3ff061.pdf`

员工首次确认页曾展示的 V1 source PDF SHA-256 为：

```text
e1c766aa5808d2b1cb1145a43d82a0b8b8904d0776ff1bc5ddb4324873182c7d
```

该值只作为既有审阅证据，不用于声称当前磁盘文件仍存在或仍可复验。

## 实施约束

- 不覆盖任何现存历史签约 PDF、签名、印章或事件。
- 新模板、新方案和新签约文件使用新版本。
- 发现历史文件缺失时报告缺失，不从截图或哈希反向重建文件。
