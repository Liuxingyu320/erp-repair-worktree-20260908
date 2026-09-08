# 云盘快速完善实施方案（2026-07-13）

## 目标

在不改变现有云盘权限模型、数据结构和默认功能开关的前提下，先用一个短周期完成用户可直接感知的问题闭环，再把断点续传和大规模搜索作为生产级增强实施。最终达到：页面状态不误导、操作可撤销/可恢复、移动端可完成常用管理、浏览器导航符合预期，并具备基础监控和明确验收证据。

## 约束与保护措施

- 不使用虚拟机或 Docker；使用本机服务、现有测试和真实浏览器验证。
- 云盘开关仍默认关闭，仅在本地验证进程临时开启，结束后恢复。
- 保持现有 `/file/drive/**` API 向后兼容。
- 只修改云盘相关文件，不覆盖工作区当前存在的 OA、签署等其他改动。
- 每个阶段先补失败用例，再实现，再跑回归；任何阶段都可以独立回滚。
- “快速完成包”不引入数据库迁移；需要迁移的能力进入生产级增强包。

## 范围与优先级

### 实施状态（2026-07-13）

- A. 快速完成包：已实现并通过最终回归。
- B. 生产级增强包：设计与验收标准已确定；因涉及数据库/对象存储状态机和灰度发布，保留为独立后续实施包。

### A. 快速完成包（推荐先做，约 1 个工作日）

1. 列表状态与搜索/排序/分页完全一致。
2. 桌面端浏览器前进、后退可恢复文件夹位置。
3. 上传任务支持真正取消，失败重试文案明确。
4. 移动端去除重复入口，补齐重命名、移动、移入回收站。
5. 增加文件存储健康检查及云盘关键失败计数。
6. 全量自动化、生产构建、桌面/移动真实流程回归。

### B. 生产级增强包（快速包通过后，约 2–4 个工作日）

1. 分片、断点续传、取消清理和过期任务回收。
2. 真实数据规模下的全盘搜索基准与索引方案。
3. MinIO、弱网、大文件和并发专项验证。

## 有序实施步骤

### 1. 修复查询状态与显示结果不一致

**改动位置**

- `erp-ui/src/views/drive/driveState.js`
- `erp-ui/src/views/drive/index.vue`
- `erp-ui/src/views/mobile/drive/index.vue`
- `erp-ui/test/cloudDriveState.test.js`
- `erp-ui/test/cloudDriveDesktop.test.js`
- `erp-ui/test/mobileCloudDrive.test.js`

**实现方式**

- 将“当前控件条件”“正在请求条件”“已提交显示条件”明确分开。
- 搜索、排序、翻页开始时清空不再匹配的新旧列表，但保留仍正确的空间和面包屑。
- 失败后保持空结果和错误提示，不把旧搜索结果、旧排序结果或旧页码重新放回页面。
- “重新加载/重试”必须使用当前待执行条件，不能回退到旧条件。
- 继续使用请求序号阻止慢响应覆盖新响应。

**验收**

- 搜索、排序和分页请求分别注入 5xx 后，页面不存在与当前条件不符的旧行。
- 连续快速输入两个关键词，最后只允许第二个请求提交结果。
- 桌面和移动端错误态均可直接重试并恢复。

### 2. 完善桌面目录历史

**改动位置**

- `erp-ui/src/views/drive/index.vue`
- `erp-ui/test/cloudDriveDesktop.test.js`

**实现方式**

- 用户主动进入文件夹、返回面包屑、切换空间或视图时使用 `router.push` 形成历史。
- 搜索输入、排序和分页等高频条件继续使用 `router.replace`，避免历史栈被每次输入污染。
- 增加统一的 `applyRouteState`，通过路由更新钩子响应浏览器前进/后退，并设置同步保护防止路由循环。

**验收**

- 连续进入两层文件夹后，浏览器后退两次依次回到上一层和根目录。
- 浏览器前进可以恢复相同空间、目录和列表。
- 搜索连续输入不会产生大量无意义历史记录。

### 3. 上传任务支持取消

**改动位置**

- `erp-ui/src/api/drive/index.js`
- `erp-ui/src/views/drive/index.vue`
- `erp-ui/src/views/drive/components/DriveUploadQueue.vue`
- `erp-ui/src/views/drive/driveState.js`
- `erp-ui/src/views/mobile/drive/index.vue`
- 对应前端测试文件

**实现方式**

- 利用当前 Axios 1.x 的 `AbortController.signal`，为每个上传任务保存独立控制器。
- 上传中显示“取消”；取消后立即停止请求，任务进入“已取消”，不显示为普通失败。
- 组件销毁时取消仍在进行的请求，防止离开页面后后台继续上传。
- 失败重试仍从头开始，并明确提示；断点续传由增强包提供。

**验收**

- 点击取消后 500ms 内网络请求终止，服务端不产生云盘节点且额度不增加。
- 取消一个任务不影响并行队列中的其他任务。
- 离开页面时不残留上传请求。

### 4. 补齐移动端常用管理操作

**改动位置**

- `erp-ui/src/views/mobile/drive/index.vue`
- 新增 `erp-ui/src/views/mobile/drive/components/MobileDriveActionSheet.vue`
- 新增 `erp-ui/src/views/mobile/drive/components/MobileDriveMoveSheet.vue`
- 复用 `erp-ui/src/views/mobile/feature/components/MobileConfirmDialog.vue` 的焦点管理、遮罩和安全区模式
- `erp-ui/test/mobileCloudDrive.test.js`

**实现方式**

- 文件夹整行保留为唯一“进入”入口，去掉重复“进入”按钮。
- 右侧统一为“下载”或“更多”；“更多”按 `canWrite`、`canDelete` 显示重命名、移动、移入回收站。
- 重命名和删除使用移动端对话框；移动使用带面包屑和分页加载的底部面板。
- 操作成功后刷新当前目录和空间额度；并发版本冲突显示可理解提示并重新加载。

**验收**

- 390×844 下所有触控目标至少 44px，不横向溢出。
- 键盘焦点不会逃出弹层，Esc/返回键可关闭，关闭后焦点回到触发按钮。
- 无权限用户完全看不到管理入口；有权限用户可完成重命名、移动和移入回收站。

### 5. 增加基础运行监控

**改动位置**

- 新增 `erp-modules/erp-file/src/main/java/com/erp/file/drive/health/DriveStorageHealthIndicator.java`
- 新增 `erp-modules/erp-file/src/main/java/com/erp/file/drive/metric/DriveMetrics.java`
- `erp-modules/erp-file/src/main/java/com/erp/file/drive/service/DriveUploadService.java`
- `erp-modules/erp-file/src/main/java/com/erp/file/drive/service/DriveTrashService.java`
- 对应后端测试

**实现方式**

- 云盘启用时用现有 `DriveStorageProvider.validate()` 提供存储健康状态；云盘关闭时不误报故障。
- 对上传成功/失败、存储失败、回收清理失败和补偿删除失败增加计数与耗时指标，不记录文件名、用户信息或存储键。
- 健康检查结果设置短 TTL，避免频繁检查 MinIO 策略造成额外压力。
- 告警阈值由部署环境配置，不在业务代码里写死。

**验收**

- 本地存储目录不可写或 MinIO 不可用时健康状态下降，恢复后自动回到正常。
- 一次成功上传和一次注入失败能产生对应指标，且指标标签不包含敏感或高基数字段。

### 6. 快速包总回归

**自动化命令**

```bash
cd /Users/liuxingyu/Desktop/备份/ERP-NEW/erp-ui
npm test
npm run build:prod

cd /Users/liuxingyu/Desktop/备份/ERP-NEW
mvn -pl erp-modules/erp-file -am test
mvn -pl erp-modules/erp-system -Dtest=CloudDriveMenuSqlSourceTest,SysMenuServiceImplTest,SysUserControllerAuthRoleScopeTest test
mvn -pl erp-gateway -Dtest=FileDriveRouteConfigTest test
```

**真实流程验收**

- 桌面 1440×900：搜索失败、排序失败、翻页失败、后退/前进、上传取消、重试恢复。
- 移动 390×844：进入目录、更多操作、重命名、移动、删除、上传取消、错误恢复。
- 可访问树不出现无名称控件；弹层有名称、焦点循环和关闭恢复。
- 测试数据清理，云盘测试开关恢复默认关闭。

## 生产级增强包

### 7. 分片与断点续传

**API 设计**

- `POST /file/drive/uploads`：创建上传会话并返回 `uploadId`、分片大小和过期时间。
- `PUT /file/drive/uploads/{uploadId}/parts/{partNumber}`：幂等上传分片。
- `GET /file/drive/uploads/{uploadId}`：查询已完成分片，恢复上传。
- `POST /file/drive/uploads/{uploadId}/complete`：校验大小/摘要、提交对象、写入节点和额度。
- `DELETE /file/drive/uploads/{uploadId}`：取消并清理临时对象。

**关键要求**

- 本地存储与 MinIO 使用同一业务状态机，各自实现分片存储和最终提交。
- 上传会话绑定用户、空间、父目录和原始文件元数据，禁止跨用户续传。
- 完成接口幂等；节点写入失败必须清理最终对象并回退额度。
- 增加过期上传定时清理和操作日志，不允许无限占用临时空间。
- 小文件继续兼容当前单请求接口；建议大于 10 MB 自动走分片。

**验收**

- 100 MB 文件在 40%、80% 断网后可从已完成分片继续。
- 重复上传同一分片、重复调用 complete 不产生重复文件或重复额度。
- 取消、过期和完成失败均不残留临时对象。

### 8. 全盘搜索性能专项

**先测后改**

- 在隔离测试库构造 1 万、10 万、100 万节点，记录常用中文关键词的 p50/p95、扫描行数和执行计划。
- 目标：10 万节点单空间下搜索 p95 不高于 300ms；达标则不做高风险迁移。
- 未达标时，根据生产 MySQL 版本和中文分词能力选择 FULLTEXT ngram、独立搜索表或外部搜索服务；不直接用普通 B-tree 冒充对 `%关键词%` 有效的索引。
- 保留权限和空间过滤作为搜索查询的第一边界。

**验收**

- 结果正确、分页稳定、权限不串空间。
- 新旧查询结果对照一致，并有可回滚迁移脚本。

## 发布与回滚

- 快速包不含数据库迁移，可按前端静态资源与文件服务分别回滚。
- 上传取消、移动端管理和健康检查各自独立提交，出现问题可单项撤回。
- 增强包必须使用独立功能开关，例如 `drive.resumable-upload-enabled=false`，先灰度到管理员或测试空间。
- 分片相关表和临时对象清理在回滚前先停止新会话；兼容保留旧上传接口，避免前后端版本不一致中断上传。

## 完成定义

- 所有新增回归测试通过，最终前端 159 个测试文件与文件服务及依赖 194 项测试不得回退。
- 生产构建成功，密钥扫描通过，`git diff --check` 无格式错误。
- 桌面、移动、错误注入、恢复、权限、取消和导航均有真实页面证据。
- 不残留测试节点、临时上传、测试进程或开启的云盘开关。
