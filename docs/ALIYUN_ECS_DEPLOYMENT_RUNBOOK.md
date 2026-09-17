# 阿里云 ECS 生产部署与移动端发布 Runbook

> 面向接手本项目的新窗口或 Agent。生产操作前先阅读 [生产发布与回滚标准](releases/production-release-standard.md) 的公共门禁，再读取本文中本次操作对应的章节；安卓打包、首次迁移和历史故障章节按需读取。同一任务中已读且未变更的章节无需重复阅读。只读排查不要求预读全部发布流程。
>
> 最后更新：2026-07-16

> **2026-09-14 用户确认的存储规则：**后续项目存储统一放到数据盘，图片上传必须经过压缩处理。涉及上传、图片处理、文件目录、持久化或部署存储配置时，阅读[项目存储与图片上传规则](项目存储与图片上传规则.md)的相关章节。下文系统盘发布目录属于历史布局，不能继续作为新增存储的目标；`deploy-host` 已在源码改用数据盘并增加启动门禁；线上迁移仍待切换。具体候选、恢复演练、维护范围和回退步骤见[存储迁移与图片压缩实施说明](存储迁移与图片压缩实施说明_20260914.md)，不得把源码修复当作线上生效。

## 1. 适用范围

本文用于把当前工作区代码滚动更新到**已经运行 ERP 的阿里云 ECS**。它不是新服务器从零初始化手册。

当前生产环境采用宿主机方式运行：Nginx 对外提供网页，Java 服务由 `erp-new@*.service` 管理，MySQL 和 Redis 数据保留在服务器。正常发布使用：

```text
scripts/aliyun_ecs_deploy_helper.py deploy-host
```

不要把旧的 Docker Compose 路径 `deploy` 当作日常发布入口。`deploy-host-patch`（包括 `--frontend-only`）已失败关闭：它的 raw overlay 无法证明前端与全部 JAR 同源，任何紧急修复也必须构建 canonical archive 并使用 `deploy-host`。

### 固定环境信息

| 项目 | 当前值 |
| --- | --- |
| 仓库根目录 | `/Users/liuxingyu/Desktop/备份/ERP-NEW` |
| ECS 区域 | `cn-beijing` |
| ECS 实例 ID | `i-2ze2pb4mhep0g9rmecpw` |
| 当前公网地址 | `http://8.152.199.39` |
| 生产数据库 | `BossERP_stock_state_75c59ee` |
| 当前发布软链接 | `/opt/erp-new` |
| 历史发布目录 | `/opt/erp-new-YYYYMMDDHHMMSS/docker` |
| Systemd 目标服务 | `gateway auth monitor system job oa inventory file approval`，共 9 个 Java 服务 |
| 最后一次已核验基线 | 2026-07-06：`/opt/erp-new-20260706154438/docker` |

最后一次已核验基线只用于定位和回滚参考。工作区在 2026-07-10、2026-07-11 又有大量修改，不能把 7 月 6 日的压缩包或 APK 当作最新版本。

### 当前交接状态

- 工作区有较多未提交修改，接手 Agent 必须基于当前文件重新构建，不能假定 Git HEAD 等于待发布内容。
- 当前电脑默认 Node 为 25，超出项目声明的 `>=22 <25`；前端构建前必须切到 Node 22、23 或 24。
- 2026-07-11 新鲜执行 `node erp-ui/test/mobileNativeProductionHardening.test.js` 时，因 `erp-ui/ios/App/App/config 3.xml` 与 `config.xml` 重复而失败。`npm run app:sync` 中的原生配置清理脚本应移除重复生成文件；安卓或 iOS 打包前必须在支持的 Node 版本下运行 `app:sync`，再运行 `app:verify` 并确认退出码为 0。
- 当前生产环境没有 HTTPS，正式安卓业务请求仍处于阻塞状态；Web 部署不受此项影响。

## 2. 当前客户端架构

| 客户端 | 使用方式 | 更新方式 |
| --- | --- | --- |
| 电脑 | 浏览器打开生产地址 | 部署网页和后端即可 |
| iPhone | Safari 打开生产地址 | 部署网页和后端即可，不安装 APK |
| 安卓 | Capacitor APK，前端资源打包在 APK 内 | 前端变化需要重新生成并安装 APK；纯后端兼容更新通常不需要 |

当前安卓生产代码有两个强制条件：

1. APK 内置最新 `dist`，生产模式不会远程加载 `CAPACITOR_SERVER_URL`。
2. 原生应用必须通过 `VUE_APP_NATIVE_API_ORIGIN` 访问绝对 HTTPS 地址；HTTP 会被代码拒绝。

因此，当前只有 `http://8.152.199.39` 时：

- 电脑、iPhone 和安卓浏览器网页可以继续使用。
- 正式安卓 APK 的真实业务请求尚不具备发布条件，必须先配置域名和 HTTPS。
- `erp-ui/build/mobile-packages/` 里的 7 月 6 日 APK 是旧架构、旧代码产物，不应继续作为最新版分发。

## 3. 生产发布硬性规则

1. **绝不把 AccessKey、数据库密码、JWT 密钥、签名密码写入本文、Git、命令参数、日志或压缩包。** 部署脚本会通过隐藏终端输入读取 AccessKey。
2. 开始前执行 `git status --short`，确认本次究竟要发布哪些本地修改。工作区不一定干净，不能丢弃用户已有改动。
3. 每次从当前源码重新构建。不要复用根目录旧的 `erp-aliyun-deploy-*.tar.gz`，也不要复用旧 APK。
4. 日常代码发布不得顺便覆盖部门、用户、角色、岗位、门店授权、商品或供应商数据。
5. 不得批量执行 `sql/*.sql`。每个版本必须明确列出要执行的增量 SQL、影响表、备份表和验证语句。
6. 代码软链接可以回滚，但已经执行的数据库 SQL 不会自动回滚。数据库写入前必须备份。
7. 发布包必须排除本地 `docker/.env`、数据库目录、上传文件、缓存和日志。
8. 任一步测试、构建、上传、服务检查或业务冒烟失败，都停止发布并保留原始输出，不得把失败描述为成功。
9. 用户曾在对话中发送过 AccessKey。该密钥应在本次交接后轮换，文档中不保留其明文。

本文所有带 `| tee` 的命令都要求当前 shell 已执行 `set -o pipefail`，否则前面的构建或部署失败可能被 `tee` 的退出码掩盖。

## 4. 给新 Agent 的开场指令

可把下面这段直接交给新窗口：

```text
请先阅读 docs/releases/production-release-standard.md 的公共门禁，再按 docs/ALIYUN_ECS_DEPLOYMENT_RUNBOOK.md 中本次“现有 ECS 滚动发布”相关章节执行；已读且未变更的内容复用。
先只做本地和远端只读检查，列出本次代码、SQL 和数据影响范围；不得复用旧压缩包，不得批量执行 SQL，不得导入部门/用户/权限/商品/供应商数据，不得输出或保存 AccessKey。构建、备份、发布、验证和回滚信息都要记录。安卓正式包在 HTTPS 未配置前不得宣称可用。
```

## 5. 发布前准备

### 5.1 建立本次发布变量和记录目录

```bash
set -o pipefail
cd "/Users/liuxingyu/Desktop/备份/ERP-NEW"

export ERP_ROOT="$PWD"
export ALIYUN_REGION="cn-beijing"
export ALIYUN_INSTANCE_ID="i-2ze2pb4mhep0g9rmecpw"
export PUBLIC_ORIGIN="http://8.152.199.39"
export ERP_DATABASE="BossERP_stock_state_75c59ee"
export RELEASE_TAG="$(date +%Y%m%d-%H%M%S)"
export RELEASE_RECORD_DIR="$ERP_ROOT/output/deploy/$RELEASE_TAG"

mkdir -p "$RELEASE_RECORD_DIR"
git rev-parse HEAD | tee "$RELEASE_RECORD_DIR/git-head.txt"
git status --short | tee "$RELEASE_RECORD_DIR/git-status.txt"
git diff --check | tee "$RELEASE_RECORD_DIR/git-diff-check.txt"
```

预期：`git diff --check` 没有空白错误，`git status --short` 无输出。生产候选必须从已提交的清洁工作树构建；存在任何已跟踪或未跟踪变更时都停止。长期工作区中若残留旧 `target`、`dist` 或 `docker/**/jar/*.jar`，应改用新的清洁 checkout/worktree，不得将旧制品带入候选包。

后续步骤应在同一个持久化终端会话中执行，以便复用这些变量。若 Agent 的命令工具每次启动新 shell，必须重新导出同一组变量，并沿用已经生成的 `RELEASE_TAG`；不要再次执行 `date` 生成另一个发布编号。AccessKey 不属于这组变量，不得写入记录文件。

### 5.2 工具版本

后端固定 Java 17 和仓库 Maven Wrapper 3.9.16；安卓 Gradle 固定 Java 21；发布门禁固定 Node 22.23.1 与 npm 10.9.8。

```bash
set -o pipefail
cd "$ERP_ROOT"

export JAVA_HOME="$(/usr/libexec/java_home -v 17)"
export PATH="$JAVA_HOME/bin:$PATH"

java -version
./mvnw -v
node -v
npm -v
python3 --version
```

检查点：

- `java -version` 和 `./mvnw -v` 都必须显示 Java 17，Maven 必须是 3.9.16。当前 shell 若把 Java 带到 25，必须以上述 `JAVA_HOME` 覆盖。
- `node -v` 必须是 `v22.23.1`，`npm -v` 必须是 `10.9.8`；其他版本可用于日常兼容性实验，不得生成发布候选。

### 5.3 创建阿里云部署工具虚拟环境

系统 Python 默认没有 `oss2`，不要安装到全局环境：

```bash
set -o pipefail
cd "$ERP_ROOT"

python3 -m venv /tmp/erp-aliyun-deploy-venv
source /tmp/erp-aliyun-deploy-venv/bin/activate
python -m pip install --upgrade pip
python -m pip install oss2 requests
python -c 'import oss2, requests; print("aliyun deploy dependencies ok")'
```

预期输出：`aliyun deploy dependencies ok`。

RAM 用户至少要能调用 ECS Cloud Assistant 并读写临时 OSS Bucket。当前做法使用 ECS 和 OSS 权限。运行脚本时让脚本提示输入 AccessKey；不要把值写进 Markdown 或粘到命令行参数里。

### 5.4 远端只读基线检查

```bash
set -o pipefail
cd "$ERP_ROOT"
source /tmp/erp-aliyun-deploy-venv/bin/activate

python scripts/aliyun_ecs_deploy_helper.py inspect \
  --region "$ALIYUN_REGION" \
  --instance-id "$ALIYUN_INSTANCE_ID" \
  | tee "$RELEASE_RECORD_DIR/remote-inspect-before.txt"

python scripts/aliyun_ecs_deploy_helper.py run-shell \
  --region "$ALIYUN_REGION" \
  --instance-id "$ALIYUN_INSTANCE_ID" \
  --script-file scripts/remote_deploy_verify.sh \
  --name erp-predeploy-verify \
  --legacy-preflight \
  --timeout 300 \
  | tee "$RELEASE_RECORD_DIR/remote-verify-before.txt"
```

继续发布前必须确认：

- `/opt/erp-new` 指向一个存在的 `.../docker` 目录。
- 当前已登记服务均为 `active`，并记录首次纳管前尚未运行的 `monitor` 或 `approval`；目标发布必须把 9 个服务全部启动成功。
- 80、8080、9100、9200、9201、9203、9204、9205、9206、9300 端口状态与服务清单一致。
- 根页面和 `/prod-api/code` 在发布前可访问。

`--legacy-preflight` 只用于第一次纳管 `monitor`、`approval` 和共享上传目录前的**只读检查**：允许旧环境暂时缺少这两个新服务、旧 release manifest 和共享目录，但不会放宽原 7 个服务或 HTTP 检查。发布后验证不得带此参数。

如果发布前生产环境已经异常，先记录异常并诊断；不要用新版本覆盖现场后再判断原因。

## 6. 确认本次 SQL 和数据范围

### 6.1 日常发布默认不导入业务数据

下面这些数据不属于普通代码发布：

- 部门、组织机构、用户、角色、岗位、用户角色、用户岗位、菜单权限。
- 门店授权和用户门店关系。
- 商品、商品分类、供应商和库存主数据。

只有用户明确要求数据迁移，并且已经生成差异报告、备份和回滚方案时，才能运行对应导入工具。不要把 `aliyun_employee_data_import_helper.py` 或历史员工导入包放进日常发布流程。

### 6.2 已知生产 SQL 基线

截至 2026-07-06，以下 SQL 已在生产执行并核验：

- `sql/erp_inventory_transfer_reference_amount_20260701.sql`
- `sql/erp_oa_sign_package_20260702.sql`
- `sql/erp_inventory_store_transfer_receive_permission_20260703.sql`
- `sql/erp_oa_purchase_permission_alignment_20260704.sql`

2026-07-09 和 2026-07-10 新增的 SQL 在本文编写时**没有统一确认已在生产执行**。新 Agent 必须根据当前代码、设计文档和远端表结构逐个确认，不能仅根据文件日期猜测。

### 6.3 建立本次迁移清单

执行下面命令收集候选文件，但输出不代表全部都要运行：

```bash
set -o pipefail
cd "$ERP_ROOT"

find sql -maxdepth 1 -type f -name '*.sql' -print | sort \
  | tee "$RELEASE_RECORD_DIR/all-local-sql.txt"

git status --short -- sql \
  | tee "$RELEASE_RECORD_DIR/changed-sql.txt"
```

在发布记录中为每个拟执行 SQL 写明：

| 字段 | 必填内容 |
| --- | --- |
| SQL 文件 | 精确相对路径 |
| 为什么需要 | 对应代码或功能 |
| 执行时机 | 代码发布前或发布后 |
| 影响表 | SQL 会读写的全部生产表 |
| 备份表 | 传给 `--backup-table` 的表 |
| 幂等性 | 是否可以重复执行 |
| 验证 SQL | 执行后应得到的列、表或记录数量 |
| 回滚方式 | 恢复备份或反向 SQL |

没有完整清单时，不执行新增 SQL。

## 7. 构建当前代码

### 7.1 后端测试和打包

```bash
set -o pipefail
cd "$ERP_ROOT"

export JAVA_HOME="$(/usr/libexec/java_home -v 17)"
export PATH="$JAVA_HOME/bin:$PATH"
export BUILD_COMMIT="$(git rev-parse HEAD)"
export BUILD_TIME="$(date -u +%Y-%m-%dT%H:%M:%SZ)"

./mvnw clean verify -Dbuild.commit="$BUILD_COMMIT" \
  | tee "$RELEASE_RECORD_DIR/maven-verify.txt"
```

预期：Reactor 最终为 `BUILD SUCCESS`。失败时停止，不要改用旧 JAR。

检查所有部署 JAR 都是本次构建生成且非空：

```bash
set -o pipefail
cd "$ERP_ROOT"

for jar in \
  erp-gateway/target/erp-gateway.jar \
  erp-auth/target/erp-auth.jar \
  erp-visual/erp-monitor/target/erp-visual-monitor.jar \
  erp-modules/erp-system/target/erp-modules-system.jar \
  erp-modules/erp-file/target/erp-modules-file.jar \
  erp-modules/erp-job/target/erp-modules-job.jar \
  erp-modules/erp-oa/target/erp-modules-oa.jar \
  erp-modules/erp-inventory/target/erp-modules-inventory.jar \
  erp-modules/erp-approval/target/erp-modules-approval.jar; do
  test -s "$jar" || { echo "missing jar: $jar" >&2; exit 1; }
  ls -lh "$jar"
done | tee "$RELEASE_RECORD_DIR/backend-artifacts.txt"
```

必须使用 `clean`。历史 `target/classes` 残留副本曾导致 Spring Boot JAR 重新打包异常。

### 7.2 前端测试和生产构建

```bash
set -o pipefail
cd "$ERP_ROOT/erp-ui"

node -e 'if (process.versions.node !== "22.23.1") process.exit(1); console.log(process.version)'
test "$(npm --version)" = "10.9.8"
npm ci
npm run test \
  | tee "$RELEASE_RECORD_DIR/frontend-tests.txt"
```

预期：

- `npm run test` 退出码为 0。
- 此节只运行验收测试；生产前端必须由下一节的唯一发布入口重新构建并注入同一版本身份。

2026-07-11 的验证记录曾记载完整前端门禁有 2 个历史失败。若当前候选仍能复现，必须列出准确测试名和影响并修复；不得将历史记录或单次批准用作跳过发布门禁的依据。发布条件统一以 docs/releases/production-release-standard.md 为准。

### 7.3 使用唯一生产入口构建不可变候选包

```bash
set -o pipefail
cd "$ERP_ROOT"
export RELEASE_ID="erp-$RELEASE_TAG"
export RELEASE_OUTPUT_DIR="$RELEASE_RECORD_DIR/candidate"

scripts/release/build-release.sh \
  --release-id "$RELEASE_ID" \
  --git-commit "$BUILD_COMMIT" \
  --build-time "$BUILD_TIME" \
  --output-dir "$RELEASE_OUTPUT_DIR" \
  | tee "$RELEASE_RECORD_DIR/build-release.txt"

export DEPLOY_PACKAGE="$RELEASE_OUTPUT_DIR/$RELEASE_ID.tar.gz"
test -s "$DEPLOY_PACKAGE"
```

该入口自行执行 Maven `clean verify`（强制运行测试）、前端 `npm ci` 和 `build:prod`，然后直接从本次 `target`/`dist` 在临时目录中组装候选。它会把同一个 `RELEASE_ID` / `GIT_COMMIT` / `BUILD_TIME` 写入前端、全部 JAR、发布清单和 SBOM，并拒绝脏工作树、旧部署产物和缺失制品。该生产入口不提供跳过构建或测试的选项，并要求 `MAVEN_ARGS`、`MAVEN_OPTS`、`JAVA_TOOL_OPTIONS`、`JDK_JAVA_OPTIONS` 与 `_JAVA_OPTIONS` 均未设置，防止从环境注入测试选择器或跳过参数；不得使用 `docker/copy.sh` 加手工 `tar` 的旧流程生成生产包。

如果本次候选必须带自动迁移，迁移 manifest 必须在构建不可变候选时一并声明。例如 2026-08-13 的 system 配置元数据扩展使用：

```bash
scripts/release/build-release.sh \
  --release-id "$RELEASE_ID" \
  --git-commit "$BUILD_COMMIT" \
  --build-time "$BUILD_TIME" \
  --output-dir "$RELEASE_OUTPUT_DIR" \
  --migration-manifest scripts/system-config-metadata-release-20260813.json
```

唯一生产入口会把 manifest、顺序清单和其声明的 SQL 写入候选包，并把迁移发布 ID 与文件 SHA-256 纳入发布清单和整包校验。候选中保留的历史 SQL 只是不可执行的静态资源；部署器只读取 `migrationPlan` 精确列出的 SQL。未在构建时嵌入迁移的候选不能在部署阶段临时附加 SQL；反之，嵌入了迁移的候选也不能按纯代码模式发布。

## 8. 验证候选包和真实生产变量

候选包的唯一合法结构是 `<RELEASE_ID>/docker/...`，同时必须包含 `provenance/SHA256SUMS`、`provenance/release-manifest.json`、`provenance/toolchain.json` 及包内验证器。顶层直接为 `docker/` 的历史包会被 `deploy-host` 在读取 AccessKey 或上传前拒绝。

```bash
cd "$ERP_ROOT"

python3 scripts/release/release_tool.py verify \
  --archive "$DEPLOY_PACKAGE" \
  | tee "$RELEASE_RECORD_DIR/archive-verify.json"

shasum -a 256 "$DEPLOY_PACKAGE" \
  | tee "$RELEASE_RECORD_DIR/package.sha256"

# 受控的本地 docker/.env 必须预先写入同一组非秘密版本值；不要输出其他值：
# RELEASE_ID=$RELEASE_ID、GIT_COMMIT=$BUILD_COMMIT、BUILD_TIME=$BUILD_TIME
# deploy-host 还会在复制服务器旧 .env 后原子写入并逐项复验这三个值。
scripts/release/preflight-release.sh \
  --archive "$DEPLOY_PACKAGE" \
  --evidence "$RELEASE_RECORD_DIR/preflight.json"
```

两个验证器都必须成功，且 `preflight.json` 的 `releaseIdentityCoherent` 必须为 `true`。当前全量包主要体积来自 9 个 Spring Boot JAR，接近 1 GB 属于现有打包方式的已知情况。不要因为上传慢而回退到旧包。

## 9. 执行现有 ECS 滚动发布

### 9.1 了解当前脚本边界

`scripts/aliyun_ecs_deploy_helper.py deploy-host` 会：

1. 把发布包拆分上传到临时私有 OSS Bucket。
2. 让 ECS Cloud Assistant 下载并校验 SHA-256。
3. 在远端使用部署助手生成的 Python 3.6 兼容验证器复验全包哈希、前端与全部 JAR 身份，再创建 `/opt/erp-new-时间戳/<RELEASE_ID>/docker`。
4. 从旧版本保留 `.env`，将旧 `run-erp-service.sh` 保存为 `run-erp-service-legacy.sh`；包内版本化扩展启动器只新增 `monitor` 和 `approval` 映射，原 7 个服务继续委托旧启动器。发布前校验 9 个目标 JAR、Systemd 模板和两层启动器。
5. 要求当前版本的 `uploadPath` 与 `erp/uploadPath` 已经都是指向 `/data/erp-new-data/uploadPath` 的软链接；发现任一发布目录仍含本地文件或两棵目录分叉时立即失败。`deploy-host` 不负责合并两棵分叉文件树。
6. 默认不执行任何数据库 SQL；只有显式提供版本清单、目标库、`--apply-migrations` 和精确发布 ID 确认时，才先备份清单声明的既有表，再按清单顺序执行迁移。
7. 切换 `/opt/erp-new` 软链接，再按 `system job oa inventory file approval monitor auth gateway` 的依赖顺序重启 9 个服务。
8. 对 9 个服务执行逐服务 readiness：Systemd 必须为 `active`，普通服务的 `/actuator/health` 必须返回 `UP`，monitor 首页必须可访问；随后验证 Nginx 首页和 `/prod-api/code`。任一失败都回切代码、停止新服务并恢复发布前实际运行的旧服务集合。

Systemd 模板必须把实例名传给 `/opt/erp-new/run-erp-service.sh`；发布助手会通过目标服务实际启动和 readiness 验证这一点。缺少映射、端口、制品或健康端点时不会输出 `DEPLOY_OK`。

若服务器仍存在迁移遗留的两棵 `uploadPath`，第一次执行正常 `deploy-host` 前必须在维护窗口审阅并执行一次性受审计恢复脚本 `scripts/remote_repair_common_upload_storage_20260826.sh`。该脚本负责冻结清单、按 SHA-256 恢复和合并已确认文件、处理冲突并建立两层兼容链接；其证据通过复核后，正常发布工具只验证统一根和链接，不重复迁移或自动合并。不得把 `deploy-host` 的单目录兼容逻辑当作分叉数据恢复工具。

发布助手不扫描 `sql/` 或 `docker/mysql/db/`，也不再硬编码数据库名或旧 SQL。迁移执行前会校验候选包声明的迁移发布 ID、manifest SHA、每个 SQL 的 SHA、MySQL 5.7/8.0、备份表存在性和版本级命名锁。候选包与部署命令声明的迁移不完全一致时，会在读取云凭据和远端写入前失败。应用失败可回滚代码软链接，但不会破坏性删除已执行的增量结构。

如果因应急需要使用已经弃用的 Compose 路径，服务器 `.env` 还必须显式包含：

```dotenv
ERP_UPLOAD_ROOT=/data/erp-new-data/uploadPath
FILE_PATH=/data/erp-new-data/uploadPath
SIGN_PACKAGE_STORAGE_ROOT=/data/erp-new-data/uploadPath/private/sign-package
OA_ATTENDANCE_STORAGE_ROOT=/data/erp-new-data/uploadPath/private/attendance
OA_REIMBURSEMENT_STORAGE_ROOT=/data/erp-new-data/uploadPath/private/reimbursement
DRIVE_LOCAL_PATH=/data/erp-new-data/uploadPath/private/drive
OA_SIGN_EXCEL_IMPORT_ENABLED=true
```

`docker/deploy-ecs.sh` 会拒绝任何偏离上述 `/data` 根及固定子目录的配置，并使用 Compose `--wait` 等待全部健康检查。Compose 内 OA 和 file 必须共同把该主机根挂载为同名 `/data/erp-new-data/uploadPath`，禁止再引入 `/home/erp/uploadPath` 或 `./uploadPath` 别名。

### 9.2 发布代码和网页

```bash
set -o pipefail
cd "$ERP_ROOT"
source /tmp/erp-aliyun-deploy-venv/bin/activate

python scripts/aliyun_ecs_deploy_helper.py deploy-host \
  --region "$ALIYUN_REGION" \
  --instance-id "$ALIYUN_INSTANCE_ID" \
  --package "$DEPLOY_PACKAGE" \
  --cleanup-oss \
  | tee "$RELEASE_RECORD_DIR/deploy-host.txt"
```

上述是默认的纯代码发布，不执行任何 SQL，输出会明确显示数据库迁移已跳过。只有发布范围明确包含已批准迁移时，才可在执行前另行加入完整的 `--migration-manifest`、`--apply-migrations`、`--approve-migrations` 和 `--database-name` 参数组。不能只传其中一部分；参数组合不完整会在上传前失败。

2026-08-13 system 配置元数据扩展的完整部署参数组为：

```bash
python scripts/aliyun_ecs_deploy_helper.py deploy-host \
  --region "$ALIYUN_REGION" \
  --instance-id "$ALIYUN_INSTANCE_ID" \
  --package "$DEPLOY_PACKAGE" \
  --migration-manifest scripts/system-config-metadata-release-20260813.json \
  --apply-migrations \
  --approve-migrations system-config-metadata-20260813 \
  --database-name bosserp_stock_state_75c59ee \
  --cleanup-oss \
  | tee "$RELEASE_RECORD_DIR/deploy-host.txt"
```

该迁移只扩展 `sys_config` 和 `sys_user` 的当前代码必需字段，并在执行前备份这两张表；不得用历史整库 hardening SQL 代替它。

AccessKey 通过终端提示输入。正常完成必须出现：

```text
DEPLOY_OK release=/opt/erp-new-... previous=/opt/erp-new-.../docker previous_services=...
```

立即把 `release=`、`previous=` 和 `previous_services=` 记录到发布记录。手工回滚必须使用这个发布前实际运行的服务集合，不能猜测。没有 `DEPLOY_OK` 就视为失败。

默认使用 HTTPS 访问 OSS。如果本机到阿里云 OSS 持续出现 TLS EOF，先重试；确认是网络链路问题后才可临时追加 `--oss-scheme http`。该回退会让短期签名 URL 经 HTTP 传输，风险更高，执行后必须保留 `--cleanup-oss` 并确认临时 Bucket 已删除。

## 10. 执行不属于版本清单的单独增量 SQL

如果第 9 节曾显式带入迁移参数，其 manifest 中的 SQL 不得再用本节工具重复执行。本节只用于经过单独批准、明确不属于任何版本 manifest 的一次性增量 SQL；每个 SQL 单独执行、单独备份、单独验证。

示例结构如下；实际 SQL 文件和备份表必须来自该 SQL 的真实内容：

```bash
set -o pipefail
cd "$ERP_ROOT"
source /tmp/erp-aliyun-deploy-venv/bin/activate

python scripts/aliyun_apply_sql_helper.py \
  --region "$ALIYUN_REGION" \
  --instance-id "$ALIYUN_INSTANCE_ID" \
  --sql sql/本次经过确认的增量脚本.sql \
  --database "$ERP_DATABASE" \
  --backup-table 第一张受影响表 \
  --backup-table 第二张受影响表 \
  --cleanup-oss \
  | tee "$RELEASE_RECORD_DIR/sql-脚本名称.txt"
```

正常完成必须出现：

```text
SQL_APPLY_OK work_dir=/opt/erp-new-data-imports/sql-... backup_dir=/opt/erp-new-data-backups/sql-...
```

注意：

- 不要照抄示例中的中文占位名执行。
- `--backup-table` 可重复；所有会被更新或删除的重要表都应加入。
- 工具会把备份保存为远端 `tables_before.sql.gz`。
- SQL 执行失败后，先保存输出和备份目录，不要立即重跑。先确认脚本是否部分提交、是否幂等。
- 权限 SQL 执行后，不只检查记录数量，还要用真实用户验证角色、岗位、菜单和门店数据范围。

## 11. 发布后自动验证

### 11.1 公网网页和 API

```bash
set -o pipefail
cd "$ERP_ROOT"

for path in \
  / \
  /select-shop \
  /mobile/store \
  /prod-api/code \
  /prod-api/auth/passwordPolicy \
  /prod-api/captchaImage; do
  curl -sS -o /dev/null \
    -w '%{http_code} %{size_download} %{time_total} '"$path"'\n' \
    --max-time 15 \
    "$PUBLIC_ORIGIN$path"
done | tee "$RELEASE_RECORD_DIR/public-http-after.txt"
```

预期全部为 HTTP 200；JSON 接口还必须解析正文并确认业务码为 `200`。HTTP 200 携带 `code=500` 仍是失败。若首页 200 但 CSS/JS 出现 404、502 或空响应，继续执行静态资源全量检查，不能只看首页。

### 11.2 远端服务、数据库基线和静态资源

```bash
set -o pipefail
cd "$ERP_ROOT"
source /tmp/erp-aliyun-deploy-venv/bin/activate

python scripts/aliyun_ecs_deploy_helper.py run-shell \
  --region "$ALIYUN_REGION" \
  --instance-id "$ALIYUN_INSTANCE_ID" \
  --script-file scripts/remote_deploy_verify.sh \
  --name erp-deploy-verify \
  --timeout 300 \
  | tee "$RELEASE_RECORD_DIR/remote-verify-after.txt"

python scripts/aliyun_ecs_deploy_helper.py run-shell \
  --region "$ALIYUN_REGION" \
  --instance-id "$ALIYUN_INSTANCE_ID" \
  --script-file scripts/remote_verify_mobile_web_deploy_20260706.sh \
  --name erp-mobile-web-verify \
  --timeout 300 \
  | tee "$RELEASE_RECORD_DIR/mobile-web-verify-after.txt"

python scripts/aliyun_ecs_deploy_helper.py run-shell \
  --region "$ALIYUN_REGION" \
  --instance-id "$ALIYUN_INSTANCE_ID" \
  --script-file scripts/remote_verify_static_assets_20260706.sh \
  --name erp-static-assets-verify \
  --timeout 600 \
  | tee "$RELEASE_RECORD_DIR/static-assets-after.txt"
```

通过标准：

- 9 个 Java 服务全部 `active`，逐服务 readiness 全部通过；`monitor:9100` 与 `approval:9206` 不再是可选项。
- 当前软链接指向本次新 release。
- 当前 `uploadPath` 与 `erp/uploadPath` 都解析为 `/data/erp-new-data/uploadPath`；OA、file 的真实进程环境也必须使用同一根及固定子目录。
- 根页面、手机页面、验证码和密码策略接口均为 200。
- 静态资源输出 `failed=0`。
- Nginx 配置检查通过。
- 每个新增 SQL 的专用验证语句得到预期结果。

`remote_verify_mobile_web_deploy_20260706.sh` 中部分数据库检查是 7 月 6 日功能基线，不代替本次新增迁移的验证。

### 11.3 真实业务冒烟

至少完成以下操作：

1. 电脑浏览器登录，验证码、密码策略和登录接口正常。
2. 选择一个真实门店或仓库进入系统，组织树没有重复或错位。
3. 用普通员工、店长、区域运营总监等不同角色检查菜单和数据范围。
4. 打开组织机构、用户、角色、岗位、店铺授权、商品、供应商页面，确认数量和关系未被代码发布误改。
5. 打开本次修改涉及的业务页面，至少完成新增、保存、详情和列表刷新流程。
6. 如果本次涉及调拨，必须创建测试调拨单并确认保存、发货、收货和库存日志。
7. iPhone Safari 实测登录、选组织和移动工作台。
8. 检查浏览器控制台和 Nginx/Java 日志，没有新出现的 502、CSS chunk、未捕获异常或数据库字段错误。

发布后观察日志：

```bash
set -o pipefail
cd "$ERP_ROOT"
source /tmp/erp-aliyun-deploy-venv/bin/activate

python scripts/aliyun_ecs_deploy_helper.py run-shell \
  --region "$ALIYUN_REGION" \
  --instance-id "$ALIYUN_INSTANCE_ID" \
  --script-file scripts/remote_nginx_recent_502.sh \
  --name erp-nginx-502-after \
  --timeout 300 \
  | tee "$RELEASE_RECORD_DIR/nginx-502-after.txt"

python scripts/aliyun_ecs_deploy_helper.py run-shell \
  --region "$ALIYUN_REGION" \
  --instance-id "$ALIYUN_INSTANCE_ID" \
  --script-file scripts/remote_failure_logs.sh \
  --name erp-failure-logs-after \
  --timeout 300 \
  | tee "$RELEASE_RECORD_DIR/service-errors-after.txt"
```

## 12. 代码回滚

部署命令输出会给出精确的 `previous=` 路径。需要回滚时，把下面的 `PREVIOUS_DOCKER` 设置成该路径，然后通过 Cloud Assistant 执行。

新 Agent 先从 `$RELEASE_RECORD_DIR/deploy-host.txt` 取出 `previous=` 的精确值，再用下面内容创建临时文件 `/tmp/erp-rollback-release.sh`。脚本中的 `PREVIOUS_DOCKER` 必须替换成该精确路径，不能凭时间猜测：

```bash
#!/usr/bin/env bash
set -euo pipefail

PREVIOUS_DOCKER="/opt/erp-new-精确上一版本时间戳/docker"
PREVIOUS_SERVICES="从 deploy-host 输出的 previous active services 精确复制"
START_ORDER="system job oa inventory file approval monitor auth gateway"
test -d "$PREVIOUS_DOCKER"
test -n "$PREVIOUS_SERVICES"

ln -sfn "$PREVIOUS_DOCKER" /opt/erp-new
systemctl daemon-reload

for svc in $START_ORDER; do
  case " $PREVIOUS_SERVICES " in
    *" $svc "*) systemctl restart "erp-new@$svc.service" ;;
  esac
done

for entry in gateway:8080 auth:9200 monitor:9100 system:9201 job:9203 oa:9204 inventory:9205 file:9300 approval:9206; do
  svc="${entry%%:*}"
  port="${entry#*:}"
  case " $PREVIOUS_SERVICES " in
    *" $svc "*) ;;
    *) continue ;;
  esac
  for attempt in $(seq 1 60); do
    if systemctl is-active --quiet "erp-new@$svc.service" \
      && curl -fsS --max-time 4 "http://127.0.0.1:$port/$([[ "$svc" == monitor ]] || printf 'actuator/health')" \
        | grep -Eq '"status"[[:space:]]*:[[:space:]]*"UP"|<html|<!DOCTYPE'; then
      break
    fi
    [[ "$attempt" -lt 60 ]] || { echo "rollback readiness failed: $svc" >&2; exit 1; }
    sleep 3
  done
done

test "$(readlink -f /opt/erp-new/uploadPath)" = "/data/erp-new-data/uploadPath"
test "$(readlink -f /opt/erp-new/erp/uploadPath)" = "/data/erp-new-data/uploadPath"
nginx -t
systemctl reload nginx
curl -fsS --max-time 10 http://127.0.0.1/ >/dev/null
curl -fsS --max-time 10 http://127.0.0.1/prod-api/code \
  | python3 -c 'import json,sys; raise SystemExit(0 if json.load(sys.stdin).get("code") == 200 else 1)'
echo "ROLLBACK_OK current=$(readlink -f /opt/erp-new)"
```

执行方式：

```bash
set -o pipefail
cd "$ERP_ROOT"
source /tmp/erp-aliyun-deploy-venv/bin/activate

python scripts/aliyun_ecs_deploy_helper.py run-shell \
  --region "$ALIYUN_REGION" \
  --instance-id "$ALIYUN_INSTANCE_ID" \
  --script-file /tmp/erp-rollback-release.sh \
  --name erp-release-rollback \
  --timeout 300 \
  | tee "$RELEASE_RECORD_DIR/rollback.txt"
```

回滚后重新执行第 11 节全部验证。

数据库回滚不包含在上述操作中。只有确认受影响表、外键关系和备份文件后，才能在维护窗口恢复 `/opt/erp-new-data-backups/sql-*/tables_before.sql.gz`。数据库恢复属于高风险操作，必须获得用户明确批准。

## 13. 安卓 APK 发布

### 13.1 发布前置条件

正式安卓包必须同时满足：

- 已有备案和解析完成的生产域名。
- 域名已启用有效 HTTPS 证书。
- `https://生产域名/`、`https://生产域名/prod-api/code` 可访问。
- Nginx 已把 HTTP 重定向到 HTTPS，并保留 SPA 和 `/prod-api/` 路由。
- 已配置正式签名 keystore，签名文件和密码不进入 Git。
- `versionCode` 比已分发版本大，`versionName` 与发布版本一致。

缺少任一项时，只能进行本地或内部调试，不能称为正式安装包。

### 13.2 同步安卓内置网页

下面的 URL 必须替换为真实 HTTPS 生产域名：

```bash
set -o pipefail
cd "$ERP_ROOT/erp-ui"

export VUE_APP_NATIVE_API_ORIGIN="https://真实生产域名"
npm run app:sync \
  | tee "$RELEASE_RECORD_DIR/capacitor-sync.txt"
npm run app:verify \
  | tee "$RELEASE_RECORD_DIR/capacitor-verify.txt"
```

确认生产配置：

```bash
cd "$ERP_ROOT/erp-ui"

test -f android/app/src/main/assets/capacitor.config.json
rg '"loggingBehavior": "none"' android/app/src/main/assets/capacitor.config.json
if rg '"server"|"cleartext"' android/app/src/main/assets/capacitor.config.json; then
  echo "production Capacitor config must not contain a remote dev server" >&2
  exit 1
fi
rg -F "$VUE_APP_NATIVE_API_ORIGIN" dist/static/js
```

### 13.3 构建内部测试 APK

```bash
set -o pipefail
cd "$ERP_ROOT/erp-ui/android"

export JAVA_HOME="$(/usr/libexec/java_home -v 21)"
export PATH="$JAVA_HOME/bin:$PATH"

./gradlew clean assembleDebug \
  | tee "$RELEASE_RECORD_DIR/android-debug-build.txt"

test -s app/build/outputs/apk/debug/app-debug.apk
mkdir -p "$ERP_ROOT/erp-ui/build/mobile-packages"
cp app/build/outputs/apk/debug/app-debug.apk \
  "$ERP_ROOT/erp-ui/build/mobile-packages/erp-mobile-android-$RELEASE_TAG-debug.apk"
shasum -a 256 \
  "$ERP_ROOT/erp-ui/build/mobile-packages/erp-mobile-android-$RELEASE_TAG-debug.apk" \
  | tee "$RELEASE_RECORD_DIR/android-debug.sha256"
```

Debug APK 仅供内部测试，不是正式分发包。

### 13.4 构建正式签名 APK

当前 `erp-ui/android/app/build.gradle` 尚未展示正式 `signingConfig`。在签名配置完成前，`assembleRelease` 产物不能直接交付用户。

签名配置完成并安全注入密码后：

```bash
set -o pipefail
cd "$ERP_ROOT/erp-ui/android"

export JAVA_HOME="$(/usr/libexec/java_home -v 21)"
export PATH="$JAVA_HOME/bin:$PATH"

./gradlew clean assembleRelease \
  | tee "$RELEASE_RECORD_DIR/android-release-build.txt"

test -s app/build/outputs/apk/release/app-release.apk
apksigner verify --verbose --print-certs \
  app/build/outputs/apk/release/app-release.apk \
  | tee "$RELEASE_RECORD_DIR/android-release-signature.txt"
```

正式包复制到 `erp-ui/build/mobile-packages/`，文件名包含版本号和日期，同时保存 SHA-256。先在至少一台全新安装设备和一台覆盖升级设备上完成登录、选组织、列表、保存和上传测试，再分发。

### 13.5 安卓用户安装

1. 把本次正式签名 APK 发到安卓手机。
2. 在手机上点开 APK。
3. 按系统提示，允许当前来源应用安装未知应用，例如浏览器、微信或文件管理器。
4. 点击安装，完成后打开 `ERP Mobile`。
5. 登录并选择门店或仓库。
6. 若覆盖安装提示签名不一致，说明新旧包使用了不同 keystore。不要让用户反复尝试；确认发布签名后再决定是否卸载旧包。卸载会清除本地登录状态。

## 14. iPhone 和电脑使用方式

- 电脑：浏览器打开生产 HTTPS 域名；在 HTTPS 配置完成前临时使用 `http://8.152.199.39/`。
- iPhone：Safari 打开同一地址，可通过“共享 -> 添加到主屏幕”创建入口。
- iPhone 当前不分发 APK。APK 只适用于安卓。
- 网页更新后，若用户仍看到旧页面，先刷新或清理站点缓存；Nginx 的 `index.html` 必须禁用缓存，带哈希的 CSS/JS 可长期缓存。

## 15. 常见故障

### CSS chunk 404/502，所有页面进不去

检查顺序：

1. `index.html` 引用的 CSS/JS 是否存在于当前 `/opt/erp-new/nginx/html/dist/static`。
2. `/opt/erp-new` 是否指向完整的新 release。
3. Nginx `root` 是否仍为 `/opt/erp-new/nginx/html/dist` 对应目录。
4. 运行 `remote_verify_static_assets_20260706.sh`，要求 `failed=0`。
5. `index.html` 不得长期缓存；静态资源文件名带哈希后可以长期缓存。

### Python 提示 `No module named oss2`

说明没有进入部署虚拟环境：

```bash
source /tmp/erp-aliyun-deploy-venv/bin/activate
python -c 'import oss2, requests; print("ok")'
```

### Maven 使用了 Java 25

```bash
export JAVA_HOME="$(/usr/libexec/java_home -v 17)"
export PATH="$JAVA_HOME/bin:$PATH"
./mvnw -v
```

必须确认 Wrapper 输出 Maven 3.9.16 且 Java version 为 17，再重新 `./mvnw clean verify`。

### Android Gradle 报 `无效的源发行版：21`

```bash
export JAVA_HOME="$(/usr/libexec/java_home -v 21)"
export PATH="$JAVA_HOME/bin:$PATH"
java -version
```

然后重新运行 Gradle。若依赖下载出现 TLS 错误，检查 `~/.gradle/init.d/` 中是否有全局镜像脚本覆盖项目仓库；保存现场后修正镜像或网络，不要把已部分生成的 APK 当作完整成功产物。

### 安卓提示 `NATIVE_API_ORIGIN_REQUIRED`

原因是正式原生应用没有配置 HTTPS API Origin，或配置了 HTTP 地址。配置有效 HTTPS 域名后，使用：

```bash
VUE_APP_NATIVE_API_ORIGIN="https://真实生产域名" npm run app:sync
```

重新构建并安装 APK。不要通过放开明文流量绕过生产门禁。

### 登录失败

依次检查：

- `/prod-api/code`、`/prod-api/captchaImage`、`/prod-api/auth/passwordPolicy` 是否为 200。
- `gateway`、`auth`、`system` 服务是否 active。
- 最近登录日志和 `sys_logininfor`。
- 用户状态、密码是否被数据导入误改。
- Redis 和 JWT 配置是否仍来自服务器原 `.env`。

不要通过重新导入整张 `sys_user` 表解决登录问题。

## 16. 完成标准

只有同时满足以下条件才能向用户报告“部署完成”：

- [ ] 已记录当前 Git HEAD 和工作区修改范围。
- [ ] 后端、前端必要测试和生产构建通过；发布标准要求的门禁完整，无待批准跳过的失败项。
- [ ] 发布包是本次构建生成，SHA-256 已记录，且不含 `.env`、数据、上传文件或日志。
- [ ] 发布包包含版本 manifest；可执行的 `migrationPlan` 只引用该候选显式声明的迁移，manifest、顺序清单和每个 SQL 的 SHA-256 全部匹配。
- [ ] `deploy-host` 输出 `DEPLOY_OK`，新旧 release 路径均已记录。
- [ ] 仅执行了迁移清单中的 SQL，每个 SQL 均有备份目录和验证结果。
- [ ] 9 个 Java 服务全部 active 且逐服务 readiness 通过，公网关键地址全部为 200。
- [ ] `uploadPath` 与 `erp/uploadPath` 均指向 `/data/erp-new-data/uploadPath`，代码回滚前后读取的是同一目录。
- [ ] OA、file 的 `/proc/<MainPID>/environ` 均包含固定的 `ERP_UPLOAD_ROOT`、`FILE_PATH`、签约、考勤、报销和云盘目录。
- [ ] `/prod-api/code` 不仅是 HTTP 200，JSON 业务码也为 `200`；HTTP 200 携带 `code=500` 必须判定失败。
- [ ] 当前所有 CSS/JS 静态资源检查 `failed=0`。
- [ ] 登录、选组织、权限和本次业务流程已用真实角色冒烟。
- [ ] 部门、用户、角色、岗位、店铺授权、商品、供应商数据未被普通代码发布意外改动。
- [ ] 若交付安卓包，HTTPS、版本号、正式签名、全新安装和覆盖升级测试全部通过。
- [ ] AccessKey 未出现在文件、日志或最终回复中，临时 OSS 对象已清理。

最终回复至少包含：新 release 路径、previous 路径、包 SHA-256、执行的 SQL 和备份目录、验证结果、未解决风险；仅在本次交付安卓包时报告安卓包路径与签名状态。
