# ERP 生产发布与回滚标准

## 1. 发布目录

生产主机采用不可变版本目录，不允许把构建结果直接覆盖到长期存在的 Docker 源目录。

```text
/opt/erp-new/
  releases/
    <release-id>/       # 只读解包目录
  current -> releases/<release-id>
  previous -> releases/<previous-release-id>
/data/erp-new-data/
  uploadPath/           # 数据盘上的唯一通用文件根
```

`current` 和 `previous` 只能通过同文件系统原子指针切换；解包后的目录不得再次修改。数据库、上传文件、日志和缓存不得放入版本目录。

## 2. 来源链和版本

每个版本只有一个 `RELEASE_ID`、`GIT_COMMIT` 和 `BUILD_TIME`。三者必须同时存在于：

- 生产非秘密变量契约；
- 前端 `release-info.json`；
- 网关及全部服务 JAR Manifest；
- JAR 内 `META-INF/erp-release.json`；
- `provenance/release-manifest.json`；
- `provenance/sbom.spdx.json`；
- 发布前检查证据。

发布只接受清洁 Git 提交。分支名用于追踪但不是版本真源；40 位 commit 和完整制品 SHA-256 才是来源证明。

## 3. 构建和候选包

- JDK 固定为 17；Node 固定在项目声明的 22–24 范围；Maven 必须使用仓库
  `./mvnw` 固定的 3.9.16。实际构建命令、版本及 Maven 所用 Java 均写入
  `toolchain.json`，且由归档验证器按 `release-contract.json` 复核。
- 必须从清洁构建输出生成标准 JAR 名称和前端 `dist`。
- 每个 JAR 目录只允许一个契约声明的标准文件名。
- 发布工具从 Git 跟踪的 Docker 源文件建立新暂存目录，再覆盖本次构建产物；禁止复用旧 `docker/nginx/html/dist` 或旧 JAR。
- 包内 `SHA256SUMS` 覆盖除清单自身外的全部文件；压缩包另有外部 SHA-256。
- 每个候选包含 SPDX 2.3 SBOM、数据库 SQL 哈希清单、工具链证明和回滚计划。
- 同一 `RELEASE_ID` 的压缩包已存在时拒绝覆盖。

## 4. 生产变量

真实值仅保存在受控的 `docker/.env` 或秘密管理系统中，不进入 Git、报告、命令日志或发布包。`docker/.env.example` 是唯一变量声明模板。

`ERP_UPLOAD_ROOT` 与 `FILE_PATH` 必须固定为 `/data/erp-new-data/uploadPath`；签约、考勤、报销和云盘目录只能是该根下的固定私有子目录。OA、file 的 Compose 挂载、Systemd 门禁和真实进程环境必须使用同一绝对路径，禁止退回发布目录相对 `./uploadPath`。预检仅输出变量名和校验状态，不输出任何密码、令牌或连接秘密。

## 5. 发布前门禁

发布管理员必须归档：

1. 候选包外部 SHA-256 与包内全量哈希复验结果；
2. 生产变量和 Compose 脱敏预检；
3. current/previous 两个不可变包；
4. 无业务数据副作用的回滚 dry-run 证据；
5. 最终候选上的自动化、权限、E2E、安全及健康检查结果。

任何一项不完整均为失败关闭，不得人工跳过。

## 6. 回滚原则

应用回滚只切换到已验证的 previous 包。数据库默认只允许前向修复；除非迁移本身附带经过独立审批和演练的回滚脚本，否则不得自动执行逆向 SQL。上传文件位于外部持久目录，应用版本切换不得删除或隐藏部署后新文件。

回滚后必须执行只读健康检查和关键业务冒烟；若失败，恢复 current 指针并进入事故处置。所有指针切换、哈希、负责人和时间均需留证。
