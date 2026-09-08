# ERP 生产发布工具

本目录实现候选源码、构建产物、生产变量和回滚材料之间的失败关闭式发布契约。工具只生成或验证本地制品，不会启动服务、连接数据库或执行部署。

## 固定发布身份

每次候选必须显式提供同一组值：

- `RELEASE_ID`：3–48 位小写发布编号；
- `GIT_COMMIT`：当前清洁工作树 `HEAD` 的完整 40 位 SHA；
- `BUILD_TIME`：UTC、秒精度 ISO-8601 时间，例如 `2026-07-28T12:00:00Z`。

前端 `release-info.json`、每个 JAR 的 Manifest、JAR 内 `META-INF/erp-release.json`、发布清单、SBOM 和生产 `.env` 必须一致。工具不会把秘密值写入报告。

正式构建固定使用 JDK 17、Node 22–24 和仓库 `./mvnw` 声明的 Maven
3.9.16。`provenance/toolchain.json` 必须记录同一 Maven Wrapper 及其实际
Java 版本；验证器会拒绝使用环境 `mvn` 冒充 Wrapper 的来源证明。归档验证
兼容 Python 3.9 及以上版本。

## 标准流程

1. 从已提交且清洁的候选分支构建：

   ```bash
   scripts/release/build-release.sh \
     --release-id erp-20260728.1 \
     --git-commit "$(git rev-parse HEAD)" \
     --build-time 2026-07-28T12:00:00Z \
     --output-dir /受控制品目录
   ```

2. 第二个及后续版本必须附带已验证的 previous 包：

   ```bash
   scripts/release/build-release.sh \
     --release-id erp-20260728.2 \
     --git-commit "$(git rev-parse HEAD)" \
     --build-time 2026-07-28T13:00:00Z \
     --previous /受控制品目录/erp-20260728.1.tar.gz \
     --output-dir /受控制品目录
   ```

3. 使用真实生产 `.env` 进行脱敏预检。证据 JSON 仅记录变量名及状态：

   ```bash
   scripts/release/preflight-release.sh \
     --archive /受控制品目录/erp-20260728.2.tar.gz \
     --evidence /受控证据目录/preflight.json
   ```

   主入口默认同时校验 `docker-compose.yml` 与
   `docker-compose.ecs-host.yml`。如需校验显式清单，可重复传入
   `--compose-file`；只有全部 Compose、候选包和变量契约通过后，临时
   证据才会原子发布为正式 JSON，任一失败都会移除目标证据。

4. 在不启动服务、不触碰业务数据的临时目录完成 current → previous → current 指针演练：

   ```bash
   python3 scripts/release/release_tool.py rollback-dry-run \
     --current /受控制品目录/erp-20260728.2.tar.gz \
     --previous /受控制品目录/erp-20260728.1.tar.gz \
     --evidence /受控证据目录/rollback-dry-run.json
   ```

## 失败关闭条件

### 入职 Excel 导入永久开启约束

- 前端开发、测试、生产环境都必须设置 `VUE_APP_SIGN_EXCEL_IMPORT_ENABLED=true`；
- 标准生产构建脚本必须再次显式注入该值，不能依赖操作者手工传参；
- OA 服务默认值、Docker 模板和 Compose 回退值都必须为
  `OA_SIGN_EXCEL_IMPORT_ENABLED=true`；
- 发布前后必须确认前端包为开启态、OA 运行环境为开启态，同时继续保留
  `oa:signTask:send` 等登录、权限和组织范围校验；
- 未经明确业务决定，不得关闭或删除该入口。

以下任一情况都必须中止发布：

- 工作树不清洁或传入提交与 `HEAD` 不一致；
- JAR 缺失、名称不标准、同目录存在第二个 JAR；
- Docker 目录残留旧 JAR、旧 `dist`、运行数据或生成目录；
- 前端含副本/备份式文件名、符号链接或不匹配的 commit/build time；
- 生产变量缺失、仍为占位符、profile 非 `prod`，或上传目录位于发布目录内；
- Compose 硬编码 `dev/local/test`，或未插值发布身份及 `ERP_UPLOAD_ROOT`；
- 包内任一文件哈希、JAR 元数据、前端元数据、SPDX SBOM 或来源清单不一致；
- 工具链证明未使用契约指定的 Maven Wrapper、Maven/Java/Node 版本不匹配；
- previous 包缺失、损坏或与 current 使用相同发布编号。

`SHA256SUMS` 覆盖包内除其自身外的所有文件；外部同名 `.SHA256` 文件覆盖完整压缩包。SBOM 在没有 Syft 时使用内置 SPDX 2.3 文件级清单，Syft 可用性及版本记录在 `provenance/toolchain.json`。
