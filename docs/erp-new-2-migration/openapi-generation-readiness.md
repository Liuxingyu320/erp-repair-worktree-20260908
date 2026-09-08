# ERP-NEW_2 OpenAPI 生成准备清单

> 日期：2026-08-03
> 状态：准备就绪度审阅；尚未生成或冻结 OpenAPI
> 约束：不启动服务、不连接 Nacos、MySQL、Redis 或任何共享环境

## 1. 当前证据

- 根 `pom.xml` 声明 SpringDoc 3.0.2，网关使用 WebFlux UI，共享 Swagger 模块使用 WebMVC UI。
- 网关、审批、库存、OA、系统等模块均把 `springdoc.api-docs.enabled` 绑定到 `SPRINGDOC_API_DOCS_ENABLED`，默认值为 `false`。
- 网关按服务发现结果组装 `/{serviceId}/v3/api-docs`，因此正常运行态生成依赖服务启动和发现注册。
- 当前工作树没有已提交的 `.json`、`.yaml` 或 `.yml` OpenAPI 契约文件。
- 阶段 1 滚动静态盘点识别出 631 个 Controller/网关端点，但没有完整请求体、响应体、泛型模型、校验约束或错误结构，不能冒充 SpringDoc 输出。

结论：当前具备“端点目录”，尚不具备可供客户端生成和业务冻结的“版本化 OpenAPI”。

## 2. 禁止的捷径

1. 不得临时连接原项目或共享 Nacos 来发现服务。
2. 不得启动原项目 MySQL、Redis、Nacos、网关或业务模块。
3. 不得把静态正则提取结果改名为 OpenAPI 后宣称契约已冻结。
4. 不得为了生成文档而在生产或共享环境打开匿名 `/v3/api-docs`。
5. 不得把不同模块的同路径操作静默覆盖成一条 operation。
6. 不得在没有模型与错误结构时生成前端客户端并让页面依赖错误类型。

## 3. ERP-NEW_2 隔离生成条件

后续生成必须同时满足：

- 使用 JDK 17、项目 Maven Wrapper 和 ERP-NEW_2 工作树专属本地 Maven 仓库；
- 使用只属于 ERP-NEW_2 的测试配置、临时目录、日志目录和缓存前缀；
- Spring Profile 明确禁止 Nacos 注册、配置拉取以及 MySQL/Redis 自动连接；
- 若某模块无法在无外部依赖条件下建立 Spring 上下文，先补测试替身或构建期文档任务，不得退回共享服务；
- API 文档端点只绑定回环地址，并在任务结束时随生成进程关闭；
- 输出按模块保存到版本化仓库目录，不包含本地端口、令牌、密码或内部实例地址；
- 生成前重新检查端口占用，冲突时只换 ERP-NEW_2 端口，不停止现有进程。

用户当前决定“不使用 Docker”，因此生成方案只能采用隔离的本地构建/测试进程；若无法证明外部连接全部关闭，就停止并报告。

## 4. 目标产物

建议在确认后创建：

```text
docs/openapi/erp-new-2/
├── manifest.json
├── gateway.openapi.json
├── approval.openapi.json
├── inventory.openapi.json
├── oa.openapi.json
├── system.openapi.json
├── file.openapi.json
├── job.openapi.json
└── gen.openapi.json
```

`manifest.json` 至少记录：契约版本、Git commit、模块、服务前缀、生成命令、生成时间、SpringDoc 版本、文件 SHA-256 和已知缺口。文件顺序与 `operationId` 必须稳定，保证重复生成可以审查实际差异。

## 5. 冻结前证据门槛

1. 526 个旧前端调用均映射到一个或多个明确 `operationId`，CR-001～CR-003 的动态分派全部展开。
2. 每个 operation 都有方法、外部路径、权限、请求参数/请求体、成功响应和业务错误证据。
3. 分页、日期、时间、金额、数量、文件和异步任务不再只依赖 `AjaxResult`/`TableDataInfo` 的模糊结构。
4. 13 个后端单边权限和 12 个 SQL 单边权限均有人工结论。
5. 调拨提交、审批、撤回、发货、收货和差异处理具备幂等、并发、数据范围与负向契约。
6. 契约校验能够证明没有指向原项目、共享环境或生产环境的地址与秘密。
7. 用户确认不兼容修复、合并、废弃和业务规则争议后，才可把状态改为“冻结”。

在以上门槛未全部满足前，新前端只能使用 Mock 契约或明确标记为未冻结的适配层草稿。
