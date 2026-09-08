# 企业云盘实施验证记录

验证日期：2026-07-12
实施分支：`codex/cloud-drive`

## Phase 1：安全存储与空间基础

结论：**PASS**。阶段 1 的数据源、数据库结构、私有本地存储、实时身份、三类空间权限、额度原语和空间接口均已实现；云盘默认保持关闭，现有公共附件接口行为未改变。

### 1. Maven 回归

执行：

```bash
mvn -pl erp-modules/erp-file -am test
```

结果：`BUILD SUCCESS`，共 76 项测试通过、0 失败、0 错误、0 跳过：

- `erp-common-core`：24 项；
- `erp-common-security`：18 项；
- `erp-modules-file`：34 项。

阶段聚焦命令另覆盖 32 项云盘与旧文件接口契约测试，结果同为 0 失败、0 错误。运行期间仍会输出项目基线已有的 JDK 三字母时区、`Unsafe` 和测试预期上下文启动警告，不影响测试结论。

### 2. SQL 可重复执行与部署副本

在临时库 `codex_cloud_drive_20260712_task1` 中连续两次执行：

```bash
mysql -h127.0.0.1 -P3306 -uroot codex_cloud_drive_20260712_task1 \
  -e "source /private/tmp/ERP-NEW-cloud-drive/sql/erp_cloud_drive_20260711.sql"
```

两次均成功。重复执行后的检查结果：

```text
drive_node           1
drive_operation_log  1
drive_space          1
COMPANY:ROOT         1
```

三个 `information_schema.tables` 匹配项各一张，唯一公司空间仍为一行。临时库验证后已删除。

部署副本检查：

```bash
cmp sql/erp_cloud_drive_20260711.sql \
  docker/mysql/db/erp_cloud_drive_20260711.sql
```

结果：退出码 0，两份脚本字节一致。

### 3. 私有存储与旧公共上传回归

- `LocalDriveStorageProviderTest` 验证私有目录的 `put/open/exists/delete` 和 `../public/escape.txt` 目录穿越拒绝；本地实现使用同目录 `.part` 文件完成替换；
- `ResourcesConfigTest` 验证匿名静态资源仍只映射 `/file/public/**`；
- `LocalSysFileServiceImplTest` 验证旧公共上传仍写入 `public/`；
- `SysFileControllerTest` 验证旧接口仍分别只要求 `file:upload` 与 `file:delete`；
- 在 `ResourcesConfig.java` 和云盘控制器中检索 `private/drive|storageKey` 无匹配，控制器未暴露物理存储键。

### 4. Phase 1 提交

```text
df837f29 feat(file): enable cloud drive persistence
11fe5226 feat(file): add cloud drive persistence model
d9f0bf05 feat(file): add private drive storage
de141260 feat(file): enforce drive space permissions
0647c851 feat(file): expose cloud drive spaces
```

### 5. 阶段边界

Phase 1 只开放空间列表和额度调整基础接口，并以 `DRIVE_ENABLED=false` 安全关闭。文件上传、浏览、预览/下载、重命名、移动、回收站、MinIO 私有桶、桌面端和移动端入口属于后续阶段，在对应验证完成前不得开启生产开关。

## Phase 2：文件生命周期与鉴权接口

结论：**PASS**。阶段 2 的上传补偿、文件夹与分页浏览、全空间搜索、安全内容读取、重命名、同空间移动、最近使用、回收站恢复/清理、MinIO 私有存储实现、启动完整性校验和网关路由均已完成。真实 API 验收使用隔离数据库、隔离端口和本地私有存储，未修改或停止工作区原有服务。

### 1. 后端回归门禁

执行：

```bash
mvn -pl erp-modules/erp-file -am test
mvn -pl erp-gateway -am test
```

结果均为 `BUILD SUCCESS`：

- 文件服务 reactor 共 230 项测试通过、0 失败、0 错误、0 跳过，其中 `erp-modules-file` 188 项；
- 网关 reactor 共 30 项测试通过、0 失败、0 错误、0 跳过，其中 `erp-gateway` 6 项；
- 旧公共附件控制器、公共本地存储和静态资源映射测试继续通过；
- 测试期间的 JDK 三字母时区、`Unsafe`、预期失败上下文及故障注入日志均为既有或测试刻意触发的警告，不影响结论。

真实启动验收同时发现并修复了两个仅在完整 Boot 4 上下文出现的问题：云盘组件改用 Boot 4 默认 Jackson 3 映射器；文件服务启用系统远程日志依赖的 Feign 客户端扫描。新增启动接线测试后，开启云盘的隔离文件服务及网关均能完整启动。

### 2. 真实 API 生命周期验收

启动隔离文件服务和网关后，以一次性脚本执行：

```bash
python3 "$DRIVE_ACCEPTANCE_SCRIPT"
```

脚本显式禁用系统 HTTP 代理，所有请求经隔离网关的 `/file/drive/**` 路由进入隔离文件服务。使用的六个身份均为临时合成账号：同部门的只读基础账号、部门盘管理员、公司盘及额度管理员；另一部门的部门盘管理员；以及分别用于重名和额度并发的两个基础账号。未使用真实员工数据。

通过的生命周期场景：

- 列出个人盘、公司盘和所属部门盘，创建两级操作所需文件夹；
- 上传 PDF，验证分页列表、内联预览、附件下载、详情、重命名、同空间移动和跨目录的全空间搜索；
- 文件移入回收站、恢复到原目录、再次移入回收站并永久清理；永久清理返回 HTTP 202，轮询确认批次从回收站消失且未进入 `PURGE_FAILED`；
- 部门盘管理员上传并清理部门文件，公司盘管理员上传并清理公司文件；基础账号可读取公司盘，但不能写入；
- 个人文件被另一账号读取、部门文件被另一部门读取、基础账号写公司盘三项均返回 HTTP 403 和 `DRIVE_ACCESS_DENIED`；
- 三次授权内容流校验均返回原始字节，预览/下载的 `Content-Disposition` 分别为 `inline`/`attachment`。

验收共初始化 10 个空间：6 个个人盘、3 个部门盘和 1 个公司盘。

### 3. 并发与一致性验收

同名场景使用屏障同时提交两个 4,608 字节的 `parallel-same.txt`：两个请求均成功，最终名称为 `parallel-same.txt` 与 `parallel-same (1).txt`。

额度场景使用屏障同时提交两个不同名称、各 28,672 字节的文件，并将临时个人盘额度设置为 43,008 字节，即可容纳一个但不能容纳两个。结果严格为一次 HTTP 200 和一次 HTTP 507；失败响应业务码为 `DRIVE_QUOTA_EXCEEDED`，空间 `used_bytes` 只增加 28,672 字节，失败上传的物理对象已由补偿流程删除。

生命周期与共享空间文件完成异步清理后执行数据库和私有存储核对，结果：

```text
空间数                         10
仍在元数据中的文件对象          3
私有存储中的文件对象            3
全部空间 used_bytes 合计       37888
API JSON 暴露 storage_key      0
API JSON 暴露 storageKey       0
```

逐空间 `used_bytes` 均等于活动/回收站文件大小之和；三个剩余活动文件的物理对象全部存在，字节长度与数据库一致，重新计算的 SHA-256 与元数据一致；物理对象集合与文件元数据集合完全相等，没有孤儿对象或缺失对象。

证据采集完成后，隔离网关与文件服务已停止，临时数据库、六个登录态和私有对象目录均已删除；工作区原有服务进程保持运行。

### 4. 验收边界

- 本轮真实 API 使用本地私有存储；MinIO 私有桶策略、专用桶、启动失败保护和流关闭由 14 项 MinIO provider 测试覆盖，未依赖外部 MinIO 实例；
- 桌面端和移动端菜单、页面及交互属于后续阶段；
- 生产开关仍须在数据库脚本、私有存储和公司空间均准备完成后再开启，启动校验会对缺项主动失败。

### 5. Phase 2 提交

```text
04a3ee27 feat(file): define drive node contracts
70b29df5 feat(file): add drive upload and browse APIs
526ca444 feat(file): secure drive content delivery
78b47493 feat(file): add drive node lifecycle
8950ed3b feat(file): add drive recycle bin cleanup
49d9296f feat(file): complete drive storage providers
4b275439 fix(file): restore Boot 4 startup wiring
```

## Phase 3：桌面端企业云盘

结论：**PASS**。阶段 3 已完成桌面端空间导航、目录与全空间搜索、分页排序、文件夹创建、最多两个并发上传、预览/下载、重命名、移动、最近使用、回收站、额度入口和可访问性边界。桌面页只消费阶段 2 已验收的 `/file/drive/**` 契约，不接触或显示物理存储键。

### 1. 前端自动化门禁

聚焦执行：

```bash
node --test \
  test/cloudDriveApi.test.js \
  test/cloudDriveState.test.js \
  test/cloudDriveDesktop.test.js \
  test/mobileAuthEntryPages.test.js
```

结果：4 项测试文件全部通过、0 失败。云盘契约覆盖空间与节点 API、静默错误交由页面处理、路由状态归一化、容量格式化、上传队列、Blob 错误解析、预览类型、页面操作边界、回收站清理轮询和对象 URL 回收。

完整执行 `npm test` 的结果为 102 项测试文件通过、0 失败；同步修正了一条落后于既有 `silentError` 调用契约的店铺树源码断言，未改变业务实现。`npm run build:prod` 的密钥扫描和 Vue 生产构建均成功，仅保留项目基线已有的两个 bundle-size 警告。

独立代码审查最初发现 5 个 Important：最近访问顺序被前端重排、目录面包屑旧响应竞态、移动弹窗旧响应竞态、原生按钮重复键盘触发，以及高风险状态缺少可执行测试。修复后，最近列表保留后端访问日志顺序；列表与面包屑按完整请求上下文原子提交；移动弹窗校验请求序列及可见性、节点、空间、目录和页码；原生按钮只保留 `click`；上传槽位、请求守卫、对象 URL 和定时器清理均由真实执行的纯函数断言覆盖。复核结果为 3 项聚焦测试通过、0 失败，且无剩余 Critical/Important。

### 2. 桌面视口与交互验收

使用本地开发构建和一次性合成空间/文件响应，在系统 Layout 内检查 1440×900 与 1024×768 两档桌面视口：

- `documentElement.scrollWidth` 均不超过视口宽度，云盘页、内容区和固定操作列没有横向溢出；
- 1024 宽度下工具栏自动换行，空间导航、搜索、排序、新建、上传和固定操作列仍可见可用；
- 搜索“计划”只返回匹配文件并显示逻辑路径；清空状态后可进入“项目资料”，URL 与面包屑同步到对应目录；
- TXT 内容通过 Blob 安全转为纯文本预览，弹窗始终保留关闭与下载动作；
- “最近使用”显示文件所在逻辑路径和“所在位置”动作；“回收站”显示恢复、彻底删除和清空入口；
- 交互通过语义角色和明确的 `aria-label` 定位，文件名按钮与操作按钮可由键盘访问。

验收只使用合成文件元数据和文本字节；临时预览路由、代理、模拟服务及依赖符号链接已全部删除，最终工作树不包含验收专用代码或生成物。

### 3. Phase 3 提交

```text
080b1e64 feat(ui): add cloud drive client contracts
3aca9603 feat(ui): add desktop cloud drive shell
82a5dae9 feat(ui): add cloud drive upload queue
96cc6b80 feat(ui): add drive preview and file actions
b414a528 feat(ui): complete desktop cloud drive
6d9ba8eb test(ui): align shop tree source contract
cc357081 fix(ui): harden cloud drive interactions
```

### 4. 阶段边界

移动端页面与入口、最终桌面一级菜单、`drive.enabled` 的 system/UI 联动、SQL 菜单授权、容器数据源、Nginx 上传参数、备份恢复手册和受控启用仍属于 Phase 4。完成这些上线门禁前，生产环境继续保持 `DRIVE_ENABLED=false`。

## Phase 4：移动端、最终菜单与受控上线

结论：**PASS**。阶段 4 已补齐手机工作台和“我的”入口、无组织上下文访问、手机浏览/搜索/上传/预览/下载、最终一级菜单、system/file/UI 三端统一开关、稳定权限节点、容器数据源与私有存储参数、Nginx 大文件代理设置，以及配对备份恢复手册。默认开关仍为 `false`，未修改真实生产角色或开启真实环境。

### 1. 后端、前端与原生门禁

执行：

```bash
mvn -pl erp-modules/erp-file -am test
mvn -pl erp-modules/erp-system -am test
mvn -pl erp-gateway -am test
cd erp-ui
npm test
npm run build:prod
npm run app:sync
npm run app:verify
```

结果：

- file reactor `BUILD SUCCESS`，`erp-modules-file` 188 项通过，reactor 合计 230 项，0 失败/错误；
- system reactor `BUILD SUCCESS`，`erp-modules-system` 139 项通过，reactor 合计 183 项，0 失败/错误；
- gateway reactor `BUILD SUCCESS`，`erp-gateway` 6 项通过，reactor 合计 30 项，0 失败/错误；
- 前端全量 103 个测试文件通过，0 失败；生产密钥扫描和 Vue 构建通过，只保留项目基线已有的 2 条 bundle-size 警告；
- Capacitor Android/iOS 同步成功，生成配置在未设置 `CAPACITOR_SERVER_URL` 时不含远程 server，Android `allowMixedContent=false`；同步后的 `app:verify` 再次通过 103 个测试文件；
- 首次前端全量运行因隔离 worktree 缺少 Git 忽略的 `node_modules` 和未复制的原生壳而出现 2 个环境失败。只读复用原工作区依赖、把原生基线复制到隔离 worktree 后重跑全部通过；同步生成物、依赖链接和 `dist` 随后已删除，未提交或回写原工作区。

部署契约执行 `npm test -- dockerScripts.test.js` 通过。以下两条 Compose 安全校验均退出 0，不会把展开后的密钥写入终端输出或可预测临时文件：

```bash
docker compose --env-file .env.example -f docker-compose.yml config --quiet
MYSQL_PASSWORD=compose-validation-only \
  docker compose --env-file .env.example -f docker-compose.ecs-host.yml config --quiet
```

源码契约与安全校验确认两个共享 Java 环境均默认 `DRIVE_ENABLED=false`；两个 file service 均保留私有卷、依赖健康 MySQL，并收到存储类型/路径、独立 MinIO 桶、100/110 MiB 限制、三类额度、回收保留期、cron 和 `Asia/Shanghai`。主 Compose 的三项 dynamic-datasource 环境变量齐全。唯一提示是原文件已有的 Compose `version` 字段废弃警告。

### 2. 默认关闭态证明

自动化契约证明：

- system 的 admin 角色会走全量菜单查询，但 `DRIVE_ENABLED=false` 后仍过滤 `drive` 根菜单及全部 `drive:*` 权限；开启后恰好恢复一份；
- `getInfo.driveEnabled` 直接来自服务端配置，不由角色或 `*:*:*` 推断；
- 手机端即使持有 `*:*:*`，关闭态也隐藏门店/仓库工作台入口和“我的”入口，直接访问 `/mobile/drive` 会安全重定向；
- file 的最高优先级过滤器在权限切面前返回 HTTP 503、`DRIVE_DISABLED` 和 `Cache-Control: no-store`，旧 `/upload` 不受云盘开关影响。

另以恢复演练库和 3 个合成对象启动真实 file service，关闭态探针结果：

```text
HTTP_STATUS       503
BUSINESS_CODE     DRIVE_DISABLED
CACHE_CONTROL     no-store
drive_node rows   3 -> 3
used_bytes sum    136 -> 136
physical objects  3 -> 3
```

因此关闭入口不会修改既有云盘行或对象。

### 3. 最终菜单迁移双执行

在一次性数据库 `codex_cloud_drive_menu_20260712` 中从现有菜单/角色结构创建隔离副本，连续两次执行 `sql/erp_cloud_drive_20260711.sql`。两次都成功，第二次后的结果：

```text
menu IDs          9600,9601,9602,9603（各 1 条）
root order        OA=10, 云盘=11, 人事=61
admin grants      4（9600,9601,9602,9603）
COMPANY:ROOT      1
temporary proc    0
```

人事根菜单只从 60 平移到 61，没有第二次变为 62。授权按 `role_key='admin'`，没有假设 `role_id=1`。根脚本与 `docker/mysql/db/erp_cloud_drive_20260711.sql` 经 `cmp` 字节一致；验证库已删除。

迁移同时验证 OA 锚点数量以及 ID、path、permission 冲突，冲突时使用 `SIGNAL SQLSTATE '45000'` 停止，不会静默占用其他菜单。独立审查后又补做两组数据库级故障注入：

```text
9600 路由相同但 perms=system:wrong   拒绝，原权限保持不变
9599 含 system:view, drive:access    拒绝，9600 未创建
```

正常库再次连续执行两次仍只得到 9600–9603 各一条。根权限与三项管理权限的冲突检查会识别逗号分隔权限，不会通过组合权限绕过。

### 4. 移动视口与权限矩阵

在 390×844 和 360×800 两档手机视口中，用一次性合成 API 响应验证：

- 页面无横向滚动，长文件名截断，所有主要点击目标不小于 44 px；
- 门店、仓库工作台的云盘位于“更多”，底部导航继续保持 5 项；“我的”中提供第二入口，进入云盘后“我的”保持激活；
- 未选择门店或仓库时可直接进入云盘；目录栈、面包屑、全空间搜索、加载更多和空间切换正常；
- 文件选择、拍照、单任务进度、失败后按原目标重试正常；JPEG/PNG/HEIC/HEIF 拍照文件名会安全补全；
- 图片、PDF、文本预览及不支持类型的系统分享/保存降级正常；关闭预览、切路由和销毁组件都会释放对象 URL。

阶段 2 的真实 API 权限身份与阶段 4 的入口开关合并形成最终角色矩阵：

| 合成身份 | 有效权限 | 验证结果 |
| --- | --- | --- |
| 普通员工 | `drive:access` | 完整管理个人盘；公司盘只读；无共享盘写权限 |
| 本部门管理员 | `drive:access`、`drive:department:manage` | 只管理本部门盘；其他部门读取返回 `DRIVE_ACCESS_DENIED` |
| 公司盘/额度管理员 | `drive:access`、对应 manage 权限 | 公司盘写入与额度管理按独立权限开放 |
| admin / `*:*:*` | 管理员旁路 | 开启态可管理；关闭态仍被 system/file/UI 显式开关阻断 |

真实部署的员工和管理角色键因环境而异，本次没有擅自写入用户业务库。`docs/CLOUD_DRIVE_OPERATIONS.md` 给出按唯一 `role_key` 核验并授权的 SQL，管理权限必须只授予变更单点名角色。

### 5. 13 项验收标准

最终矩阵使用 Phase 2 真实 API、Phase 3 桌面视口和 Phase 4 手机/开关/部署证据联合验收：

1. 桌面一级菜单、手机工作台和“我的”两个入口已注册，并同时受服务端开关及 `drive:access` 控制；
2. `/mobile/drive` 标记为组织上下文可选，无门店/仓库时可访问；
3. 普通员工个人盘完整生命周期通过，共享盘写入被拒绝；
4. 本部门管理与跨部门拒绝已由真实 API 验收；
5. 公司盘/额度管理及管理员旁路由独立权限和关闭态覆盖；
6. 上传、预览、下载、重命名、同空间移动、搜索、删除、恢复和异步清理通过；
7. API/页面未暴露 `storage_key`、`storageKey` 或物理目录；
8. 越权读取、目录穿越、主动内容类型、超额并发和目录循环均由后端测试或真实 API 拒绝；
9. 30 天回收配置、彻底删除与容量释放通过；
10. 上传补偿、清理失败重试、对象/节点/容量一致性通过；
11. 手机浏览、搜索、拍照/文件选择、预览、分享/保存和下载降级通过；
12. 旧公共附件控制器、本地公共存储、静态映射及其余前后端全量回归通过；
13. 后端、前端、生产构建、桌面和手机验收全部通过。

### 6. 配对备份/恢复演练

创建两个一次性数据库和本地私有对象目录，仅写入 3 份合成 TXT，分别代表个人盘、部门盘和公司盘。源库先连续应用迁移两次；随后在 file service 停止状态使用 `mysqldump --single-transaction --routines --triggers --hex-blob` 和对象 `tar` 创建同一恢复点，生成 SHA-256 清单，再按“数据库先、对象后”恢复到第二个数据库/目录。

小型数据集的备份、恢复和静态审计命令用时约 0.6 秒。恢复结果：

```text
menus                  4（9600..9603）
spaces                 3
file nodes             3
metadata bytes         136
space used_bytes       136
usage mismatches       0
PURGING                0
object SHA-256 matches 3/3
```

恢复后的真实 file service 开启态约 2.9 秒启动成功，启动探针验证目录和公司空间。合成身份可见 `PERSONAL,DEPARTMENT,COMPANY` 三类空间；3 个对象分别执行 preview/download，共 6 次均 HTTP 200 且字节与恢复文件完全一致，preview 为 `inline`、download 为 `attachment`，同时带 `no-store` 与 `nosniff`。数据库新增 6 条成功读取审计，`used_bytes` 仍无差异。

服务随后优雅停止；两个一次性数据库、Redis 合成登录态、备份和对象目录均已删除。

### 7. 运维边界与回滚

未在本机连接真实外部 MinIO、修改 Nacos Data ID、分配真实员工角色、开启生产开关或做 100 MiB 并发压测。MinIO 专用私有桶、匿名策略拒绝、缺凭据 fail-fast 由 14 项 provider 测试覆盖；Nacos 路由必须复用目标环境现有 `erp-file-api` 的 URI/服务发现形式，不能照抄本地地址。

运行、迁移、角色授权、Nacos、MinIO 迁移、孤儿 dry-run、配对备份恢复、告警和人工确认删除流程见 `docs/CLOUD_DRIVE_OPERATIONS.md`。出现问题时先关闭，不删除数据：

```bash
DRIVE_ENABLED=false docker compose up -d --force-recreate \
  erp-modules-system erp-modules-file
```

关闭后确认 `getInfo.driveEnabled=false`、入口消失、API 返回 `DRIVE_DISABLED`；保留 9600–9603、`drive_*` 表和全部对象以便修复后重新开启。

### 8. 独立代码审查

对 `9c00d7fb..a13462b1` 的移动端、开关、SQL、Compose 和运维流程做独立 Critical/Important 复核。初审发现 2 个 Important：9600 根权限及重复 `drive:access` 未完全 fail-closed；运维手册把展开后的 Compose 写入可预测 `/tmp` 文件。

修复采用 RED→GREEN：`CloudDriveMenuSqlSourceTest` 与 `dockerScripts.test.js` 先分别失败，再在 SQL 冲突校验和 `config --quiet` 实现后通过；真实 MySQL 双执行及两类冲突故障注入也通过。复核确认两份 SQL 字节一致、权限冲突在写入前失败、手册不再落盘展开密钥，当前无剩余 Critical/Important。

### 9. Phase 4 提交

```text
19a6eadc feat(mobile): register cloud drive entrypoints
4101952b feat(mobile): add cloud drive browsing
66a877c1 feat(mobile): add cloud drive upload and preview
eebe68bc feat(system): register gated cloud drive menu
a13462b1 chore(deploy): prepare cloud drive storage and backup
695fb117 fix(deploy): fail closed on cloud drive rollout
```
