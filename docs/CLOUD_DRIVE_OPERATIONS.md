# 企业云盘部署与恢复手册

本手册适用于 `erp-modules-file` 中的企业云盘。云盘元数据保存在 MySQL 的 `drive_space`、`drive_node`、`drive_operation_log`，对象保存在独立的本地私有目录或专用 MinIO 桶中。任何环境均应先以 `DRIVE_ENABLED=false` 部署和迁移，完成检查后再受控开启。

## 1. 不可破坏的运行约束

- 本地模式的生产目录固定为 `/data/erp-new-data/uploadPath/private/drive`，不得通过 Nginx alias、静态资源路由或公共附件目录暴露；文件只能经鉴权后的 `/file/drive/**` API 读取。
- 本地模式只支持一个 `erp-modules-file` 实例。只有所有实例挂载同一个具备 POSIX 一致性、原子 rename 和可靠文件锁语义的私有卷时才可多实例；横向扩容前应切到 MinIO。
- MinIO 必须使用独立私有桶，默认 `erp-drive-private`，不能与旧附件桶 `MINIO_BUCKET_NAME` 相同，也不能配置匿名读策略。
- `DRIVE_STORAGE_TYPE=minio` 与 `MINIO_ENABLED=true` 必须同时设置。缺少端点、访问凭据、专用桶或私有策略时，文件服务应启动失败，不能绕过启动探针。
- 100 MiB 是云盘业务文件上限；115343360 字节和 Nginx 的 110 MiB 是 multipart 传输余量，不改变旧附件上传的既有限制。
- 关闭功能只切断入口，不删除云盘表、菜单记录或存储对象。

## 2. 上线前检查

在部署主机设置非敏感变量；数据库和 MinIO 密钥只从部署系统的秘密存储注入，不写入仓库、前端 `.env*`、命令历史或工单正文。

```bash
export COMPOSE_FILE=docker/docker-compose.yml
export DRIVE_ROOT=/data/erp-new-data/uploadPath/private/drive
export DRIVE_REQUEST_BYTES=115343360
export DRIVE_UPLOAD_CONCURRENCY=4
```

检查和初始化私有目录。目录应只允许文件服务运行账户与备份账户访问。

```bash
install -d -m 0700 "$DRIVE_ROOT"
test -d "$DRIVE_ROOT"
test -w "$DRIVE_ROOT"
df -h "$DRIVE_ROOT"
df -i "$DRIVE_ROOT"
```

启动容器后再从容器内检查挂载与 JVM multipart 临时空间：

```bash
docker compose -f "$COMPOSE_FILE" exec erp-modules-file sh -c \
  'test -w /data/erp-new-data/uploadPath/private/drive && df -h /data/erp-new-data/uploadPath/private/drive /tmp && df -i /data/erp-new-data/uploadPath/private/drive /tmp'
```

`/tmp` 和对象卷都必须留出并发上传空间。最低临时空间预算为 `DRIVE_UPLOAD_CONCURRENCY × DRIVE_REQUEST_BYTES`，还要叠加应用日志、待清理回收站和备份窗口余量。生产告警建议：可用空间低于 20% 预警、低于 10% 阻断新上传并升级处理。

## 3. 数据库迁移与幂等核验

保持 `DRIVE_ENABLED=false`，先备份数据库，再连续执行迁移两次。以下变量由秘密存储或交互式 shell 注入；不要把真实值写入脚本。

```bash
export DRIVE_DB_HOST=127.0.0.1
export DRIVE_DB_PORT=3306
export DRIVE_DB_USER=replace_with_deploy_user
export DRIVE_DB_NAME=replace_with_database
export DRIVE_MIGRATION=sql/erp_cloud_drive_20260711.sql

MYSQL_PWD="$DRIVE_DB_PASSWORD" mysql \
  -h "$DRIVE_DB_HOST" -P "$DRIVE_DB_PORT" -u "$DRIVE_DB_USER" \
  "$DRIVE_DB_NAME" < "$DRIVE_MIGRATION"
MYSQL_PWD="$DRIVE_DB_PASSWORD" mysql \
  -h "$DRIVE_DB_HOST" -P "$DRIVE_DB_PORT" -u "$DRIVE_DB_USER" \
  "$DRIVE_DB_NAME" < "$DRIVE_MIGRATION"
```

执行核验：第一条应返回 9600–9603 各一行；第二条不得返回任何重复；公司空间计数必须为 1；迁移临时过程计数必须为 0。

```bash
MYSQL_PWD="$DRIVE_DB_PASSWORD" mysql \
  -h "$DRIVE_DB_HOST" -P "$DRIVE_DB_PORT" -u "$DRIVE_DB_USER" \
  "$DRIVE_DB_NAME" <<'SQL'
SELECT menu_id, menu_name, parent_id, order_num, path, component, perms
FROM sys_menu
WHERE menu_id BETWEEN 9600 AND 9603
ORDER BY menu_id;

SELECT menu_id, COUNT(*) AS copies
FROM sys_menu
WHERE menu_id BETWEEN 9600 AND 9603
GROUP BY menu_id
HAVING COUNT(*) <> 1;

SELECT COUNT(*) AS company_space_count
FROM drive_space
WHERE space_key = 'COMPANY:ROOT';

SELECT COUNT(*) AS migration_procedure_count
FROM information_schema.routines
WHERE routine_schema = DATABASE()
  AND routine_name = 'migrate_cloud_drive_menu';
SQL
```

迁移要求存在且仅存在一个启用的根菜单 `path='oa'`。任何稳定 ID、`drive` 路径或 `drive:*:manage` 权限冲突都会主动失败；遇到冲突时停止上线并调查，不能改掉 `SIGNAL` 或临时换 ID 继续执行。

## 4. 角色授权

根菜单 9600 对应 `drive:access`。9601、9602、9603 分别是公司盘、部门盘和额度管理权限。普通员工角色只授予 9600；管理权限只授予经过审批、明确点名的角色，不能按 `role_id=1` 或角色名称模糊匹配。

先把占位符替换为本环境真实且唯一的 `role_key`，查询确认每个键只命中一条有效角色，再执行授权：

```sql
SELECT role_id, role_key, role_name
FROM sys_role
WHERE role_key IN (
  'REPLACE_WITH_EMPLOYEE_ROLE_KEY',
  'REPLACE_WITH_COMPANY_MANAGER_ROLE_KEY',
  'REPLACE_WITH_DEPARTMENT_MANAGER_ROLE_KEY',
  'REPLACE_WITH_QUOTA_MANAGER_ROLE_KEY'
)
  AND status = '0'
  AND del_flag = '0';

INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
SELECT role_id, 9600
FROM sys_role
WHERE role_key = 'REPLACE_WITH_EMPLOYEE_ROLE_KEY'
  AND status = '0' AND del_flag = '0';

INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
SELECT role.role_id, ids.menu_id
FROM sys_role role
JOIN (
  SELECT 9600 AS menu_id
  UNION ALL SELECT 9601
) ids
WHERE role.role_key = 'REPLACE_WITH_COMPANY_MANAGER_ROLE_KEY'
  AND role.status = '0' AND role.del_flag = '0';

INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
SELECT role.role_id, ids.menu_id
FROM sys_role role
JOIN (
  SELECT 9600 AS menu_id
  UNION ALL SELECT 9602
) ids
WHERE role.role_key = 'REPLACE_WITH_DEPARTMENT_MANAGER_ROLE_KEY'
  AND role.status = '0' AND role.del_flag = '0';

INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
SELECT role.role_id, ids.menu_id
FROM sys_role role
JOIN (
  SELECT 9600 AS menu_id
  UNION ALL SELECT 9603
) ids
WHERE role.role_key = 'REPLACE_WITH_QUOTA_MANAGER_ROLE_KEY'
  AND role.status = '0' AND role.del_flag = '0';

SELECT role.role_key, GROUP_CONCAT(role_menu.menu_id ORDER BY role_menu.menu_id) AS drive_menu_ids
FROM sys_role role
JOIN sys_role_menu role_menu ON role_menu.role_id = role.role_id
WHERE role_menu.menu_id BETWEEN 9600 AND 9603
GROUP BY role.role_key
ORDER BY role.role_key;
```

管理员迁移授权按 `role_key='admin'` 完成。上线记录必须保存最终角色矩阵，并分别用普通员工、公司盘管理员、部门盘管理员、额度管理员验证最小权限。

## 5. Gateway/Nacos 路由

`local` profile 已由 `erp-gateway/src/main/resources/bootstrap.yml` 提供云盘路由。使用 Nacos 的 `dev` 环境必须单独更新配置中心，不能把本地的 `127.0.0.1:9300` URI 复制到生产。

1. 在 Nacos 中定位当前命名空间、`DEFAULT_GROUP`、Data ID `erp-gateway-dev.yml`，导出原文并记录配置 MD5/版本作为回滚副本。
2. 找到该部署现有的 `erp-file-api`，原样复用它的 `uri` 或服务发现形式。
3. 在 `erp-file-static` 之前插入下列路由，只替换 `<与本环境 erp-file-api 完全相同的 uri>`：

```yaml
- id: erp-file-drive-api
  uri: <与本环境 erp-file-api 完全相同的 uri>
  predicates:
    - Path=/file/drive/**
  filters:
    - StripPrefix=1
```

4. 发布 Data ID，滚动重启 gateway；检查有效路由顺序为 `erp-file-api`、`erp-file-drive-api`、`erp-file-static`。
5. 使用只含测试数据的已授权账号验证，HTTP 200 且响应不经过静态资源路由：

```bash
curl --fail-with-body \
  -H "Authorization: Bearer $DRIVE_TEST_TOKEN" \
  "$ERP_BASE_URL/prod-api/file/drive/spaces"
```

若返回静态文件、404 或未鉴权内容，立即恢复 Nacos 备份并停止开启流程。

## 6. 本地存储模式开启

在 Compose `.env` 或部署系统中设置以下非密钥参数，保持 MinIO 关闭：

```dotenv
DRIVE_ENABLED=true
DRIVE_STORAGE_TYPE=local
DRIVE_LOCAL_PATH=/data/erp-new-data/uploadPath/private/drive
MINIO_ENABLED=false
```

先启动数据库和文件服务，再启动 system；两者必须收到同一个 `DRIVE_ENABLED` 值。

```bash
docker compose -f "$COMPOSE_FILE" config --quiet
docker compose -f "$COMPOSE_FILE" up -d erp-mysql erp-redis
docker compose -f "$COMPOSE_FILE" up -d --force-recreate erp-modules-file erp-modules-system
docker compose -f "$COMPOSE_FILE" logs --since=10m erp-modules-file erp-modules-system
```

文件服务启动探针会验证私有目录可写和 `COMPANY:ROOT` 存在。探针失败时不要通过注释校验、改为公共目录或单独开启 system 来绕过；恢复 `DRIVE_ENABLED=false` 后处理根因。

## 7. 切换到专用 MinIO

以下流程以从本地私有目录迁移为例。先按第 9 节停止新流量并停止文件服务，再创建专用桶。MinIO 凭据只存在于当前安全 shell/秘密存储。

```bash
export DRIVE_MINIO_ALIAS=erp-drive-target
export DRIVE_MINIO_BUCKET=erp-drive-private
mc alias set "$DRIVE_MINIO_ALIAS" "$MINIO_ENDPOINT" "$MINIO_ACCESS_KEY" "$MINIO_SECRET_KEY"
mc mb --ignore-existing "$DRIVE_MINIO_ALIAS/$DRIVE_MINIO_BUCKET"
mc anonymous set none "$DRIVE_MINIO_ALIAS/$DRIVE_MINIO_BUCKET"
mc anonymous get "$DRIVE_MINIO_ALIAS/$DRIVE_MINIO_BUCKET"
```

若 `mc anonymous get` 显示匿名访问，不得继续。生成源对象清单，复制到专用桶，再完整拉回临时目录核对对象数量、总字节和 SHA-256：

```bash
export DRIVE_VERIFY_DIR=/secure/tmp/erp-drive-minio-verify
(cd "$DRIVE_ROOT" && find . -type f ! -name '*.part' -print0 | sort -z | xargs -0 sha256sum) \
  > /secure/tmp/erp-drive-source.sha256
find "$DRIVE_ROOT" -type f ! -name '*.part' | wc -l
du -sb "$DRIVE_ROOT"

mc mirror --overwrite "$DRIVE_ROOT/" "$DRIVE_MINIO_ALIAS/$DRIVE_MINIO_BUCKET/"
rm -rf "$DRIVE_VERIFY_DIR"
install -d -m 0700 "$DRIVE_VERIFY_DIR"
mc mirror "$DRIVE_MINIO_ALIAS/$DRIVE_MINIO_BUCKET/" "$DRIVE_VERIFY_DIR/"
(cd "$DRIVE_VERIFY_DIR" && find . -type f -print0 | sort -z | xargs -0 sha256sum) \
  > /secure/tmp/erp-drive-target.sha256
find "$DRIVE_VERIFY_DIR" -type f | wc -l
du -sb "$DRIVE_VERIFY_DIR"
diff -u /secure/tmp/erp-drive-source.sha256 /secure/tmp/erp-drive-target.sha256
```

只有 `diff` 无输出且对象数量、总字节一致后，才同时切换两个必需开关并注入凭据：

```dotenv
DRIVE_STORAGE_TYPE=minio
DRIVE_MINIO_BUCKET=erp-drive-private
MINIO_ENABLED=true
MINIO_ENDPOINT=https://replace-with-private-minio-endpoint
```

重新创建文件服务，观察启动探针，然后调用 `/file/drive/spaces` 并对三种空间各做一次上传、预览、下载和删除恢复测试。确认成功前保留本地源副本，不要删除。

## 8. 一致性与孤儿对象审计

审计必须在云盘写入已停止的窗口或一致性快照上执行。先导出数据库对象清单；`drive_node.storage_key` 是对象唯一相对键，文件行的 `size_bytes` 与 `sha256` 是核验基准。

```bash
MYSQL_PWD="$DRIVE_DB_PASSWORD" mysql --batch --skip-column-names \
  -h "$DRIVE_DB_HOST" -P "$DRIVE_DB_PORT" -u "$DRIVE_DB_USER" "$DRIVE_DB_NAME" \
  -e "SELECT storage_key, size_bytes, sha256 FROM drive_node WHERE node_type='FILE' AND storage_key IS NOT NULL ORDER BY storage_key" \
  > /secure/tmp/drive-db-objects.tsv

cut -f1 /secure/tmp/drive-db-objects.tsv | LC_ALL=C sort -u > /secure/tmp/drive-db-keys.txt
(cd "$DRIVE_ROOT" && find . -type f ! -name '*.part' -print | sed 's#^./##' | LC_ALL=C sort -u) \
  > /secure/tmp/drive-storage-keys.txt

comm -23 /secure/tmp/drive-db-keys.txt /secure/tmp/drive-storage-keys.txt \
  > /secure/tmp/drive-missing-objects.txt
comm -13 /secure/tmp/drive-db-keys.txt /secure/tmp/drive-storage-keys.txt \
  > /secure/tmp/drive-orphan-objects-dry-run.txt
```

`drive-missing-objects.txt` 非空是数据丢失事故，必须阻止开启并从配对备份恢复。孤儿清单只能 dry-run：逐项计算存储键指纹，与日志中的 `drive_storage_compensation_failed ... keyFingerprint=` 告警关联，再隔离，不得自动删除未匹配对象。

```bash
while IFS= read -r key; do
  printf '%s' "$key" | sha256sum
done < /secure/tmp/drive-orphan-objects-dry-run.txt \
  > /secure/tmp/drive-orphan-key-fingerprints.txt

export DRIVE_QUARANTINE=/secure/quarantine/erp-drive-$(date -u +%Y%m%dT%H%M%SZ)
install -d -m 0700 "$DRIVE_QUARANTINE"
while IFS= read -r key; do
  install -D -m 0600 "$DRIVE_ROOT/$key" "$DRIVE_QUARANTINE/$key"
done < /secure/tmp/drive-orphan-objects-dry-run.txt
```

隔离后由两名操作人复核节点清单、上传补偿告警、请求 ID 和备份可恢复性。只有变更单明确列出每个键并由负责人确认，才可在另一个人工步骤删除原对象；绝不把 `drive-orphan-objects-dry-run.txt` 直接传给 `rm`、`mc rm` 或自动生命周期规则。

空间用量应等于仍存在的所有文件节点大小之和；下列查询正常时不返回行：

```sql
SELECT space.space_id, space.space_key, space.used_bytes,
       COALESCE(SUM(CASE WHEN node.node_type = 'FILE' THEN node.size_bytes ELSE 0 END), 0) AS node_bytes
FROM drive_space space
LEFT JOIN drive_node node ON node.space_id = space.space_id
GROUP BY space.space_id, space.space_key, space.used_bytes
HAVING space.used_bytes <> node_bytes;
```

本地对象再按 `drive-db-objects.tsv` 校验实际大小和 SHA-256；MinIO 模式先用 `mc mirror` 拉到只读临时目录，再运行同一校验。不要直接通过修改 `used_bytes` 消除差异，必须先定位事务、对象或节点缺失的根因。

## 9. 配对备份

数据库和对象必须位于同一恢复点。备份前先在部署系统设置 `DRIVE_ENABLED=false`，同时重建 system 与 file，使菜单、移动入口和 API 都关闭。等待优雅停机完成后检查清理批次：

```bash
docker compose -f "$COMPOSE_FILE" up -d --force-recreate erp-modules-system erp-modules-file

MYSQL_PWD="$DRIVE_DB_PASSWORD" mysql --batch --skip-column-names \
  -h "$DRIVE_DB_HOST" -P "$DRIVE_DB_PORT" -u "$DRIVE_DB_USER" "$DRIVE_DB_NAME" \
  -e "SELECT COUNT(*) FROM drive_node WHERE status='PURGING'"
```

计数必须为 0。非零时停止备份；临时恢复启用但保持外部入口关闭，让清理重试或按告警修复，直到没有 `PURGING`。不能备份半完成的删除批次。

停止文件服务，使用同一 UTC 时间戳生成数据库与对象备份：

```bash
docker compose -f "$COMPOSE_FILE" stop erp-modules-file
export DRIVE_BACKUP_STAMP=$(date -u +%Y%m%dT%H%M%SZ)
export DRIVE_BACKUP_DIR=/secure/backups/erp-drive/$DRIVE_BACKUP_STAMP
install -d -m 0700 "$DRIVE_BACKUP_DIR"

MYSQL_PWD="$DRIVE_DB_PASSWORD" mysqldump \
  -h "$DRIVE_DB_HOST" -P "$DRIVE_DB_PORT" -u "$DRIVE_DB_USER" \
  --single-transaction --routines --triggers --hex-blob "$DRIVE_DB_NAME" \
  > "$DRIVE_BACKUP_DIR/database.sql"

tar --acls --xattrs -C "$(dirname "$DRIVE_ROOT")" \
  -czf "$DRIVE_BACKUP_DIR/drive-objects.tar.gz" "$(basename "$DRIVE_ROOT")"
sha256sum "$DRIVE_BACKUP_DIR/database.sql" "$DRIVE_BACKUP_DIR/drive-objects.tar.gz" \
  > "$DRIVE_BACKUP_DIR/SHA256SUMS"
```

MinIO 模式应在文件服务停止后用版本化对象存储快照或 `mc mirror` 到受保护备份桶，并保存对象数量、总字节和回读 SHA-256 清单。备份完成并校验后再启动文件服务；是否重新设为 `DRIVE_ENABLED=true` 必须由变更负责人单独批准。

## 10. 恢复演练与正式恢复

恢复到隔离环境，保持入口关闭。顺序不可交换：

1. 验证 `SHA256SUMS`。
2. 先恢复数据库。
3. 再恢复本地对象归档或 MinIO 专用桶。
4. 运行第 8 节的缺失对象、孤儿对象、大小、SHA-256 和 `used_bytes` 一致性审计。
5. 以合成文件验证三种空间的预览和下载。
6. 所有检查通过后才设置 `DRIVE_ENABLED=true`。

```bash
cd "$DRIVE_BACKUP_DIR"
sha256sum -c SHA256SUMS

MYSQL_PWD="$DRIVE_DB_PASSWORD" mysql \
  -h "$DRIVE_DB_HOST" -P "$DRIVE_DB_PORT" -u "$DRIVE_DB_USER" \
  "$DRIVE_DB_NAME" < database.sql

install -d -m 0700 "$DRIVE_ROOT"
tar --acls --xattrs -C "$(dirname "$DRIVE_ROOT")" -xzf drive-objects.tar.gz
```

记录恢复耗时、备份时间戳、对象数、总字节、审计输出和测试节点 ID。任何不一致都应恢复为 `DRIVE_ENABLED=false` 并保留现场。

## 11. Disable-first 回滚

出现权限越界、存储错误、路由错误或恢复不一致时，第一动作是关闭入口：

```dotenv
DRIVE_ENABLED=false
```

```bash
docker compose -f "$COMPOSE_FILE" up -d --force-recreate erp-modules-system erp-modules-file
```

确认 `getInfo.driveEnabled=false`、桌面和移动入口消失、`/file/drive/spaces` 返回 `DRIVE_DISABLED`，并保存日志和审计输出。不要删除 9600–9603、`drive_*` 表、本地私有目录或 MinIO 桶；修复后沿本手册重新核验再开启。Nacos 路由若需回滚，应使用上线前保存的完整 Data ID 版本。

## 12. 监控与告警

- 本地对象卷、容器 `/tmp`、数据库卷：可用空间低于 20% 告警，低于 10% 阻断上传；同时监控 inode。
- API/日志：按 `DRIVE_STORAGE_UNAVAILABLE`、`DRIVE_STORAGE_OBJECT_MISSING`、上传失败率和 5xx 比例告警。
- 上传补偿：任何 `drive_storage_compensation_failed` 立即告警，并保存 `requestId` 与 `keyFingerprint` 供孤儿审计关联。
- 清理：`PURGE_FAILED` 出现即告警；`PURGING` 持续超过 15 分钟告警；监控 `drive_cleanup_dispatch_failed`、`drive_purge_executor_rejected` 和对象删除失败。
- 容量：按空间监控 `used_bytes / quota_bytes`，并定期运行节点汇总一致性查询。
- 存储：定期抽样回读并核对 `size_bytes`、`sha256`；MinIO 桶策略变化和匿名访问必须告警。

每次告警处置都应保留请求 ID、角色、空间、节点 ID、对象键指纹、时间范围和修复动作，但不得把原始存储凭据或可下载私有对象的临时链接写入日志与工单。
