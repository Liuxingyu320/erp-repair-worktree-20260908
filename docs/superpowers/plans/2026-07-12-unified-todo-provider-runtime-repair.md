# Unified Todo Provider Runtime Repair Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Restore the OA and inventory unified-todo providers on the `7月12号` branch and make the required database migration safe to validate and apply.

**Architecture:** Normalize every mixed text output at the provider `UNION ALL` boundary, using the MySQL 5.7-compatible `utf8mb4_general_ci` collation. Keep provider failures fail-closed, explicitly collate new stock-check approval tables, converge old approval tables conditionally, make permission updates content-idempotent, validate the migration on a clean cloned database, then back up and migrate the current local runtime database.

**Tech Stack:** Java 17, Spring Boot, MyBatis XML, JUnit 5, AssertJ, MySQL 8 runtime with MySQL 5.7 compatibility, Maven.

## Execution record

- OA organization-column regression: RED `6 tests / 1 failure` (`0` explicit projections instead of `6`), then GREEN `6/6`.
- Inventory organization-column regression: RED `17 tests / 1 failure`, then GREEN `17/17`; presentation-column regression: RED `18 tests / 1 failure`, then GREEN `18/18`; MySQL 5.7 aggregate regression: RED `19 tests / 1 failure`, then GREEN `19/19`.
- Migration-source collation regression: RED `5 tests / 1 failure` (`0` explicit table collations instead of `2`), then GREEN `5/5`. Review follow-up regressions for content idempotence and old-table convergence were RED `7 tests / 2 failures`, then GREEN `7/7`; ordinary and Docker SQL copies remain byte-identical.
- A clean rebuild of clone database `BossERP_stock_state_75c59ee_todo_verify_20260712` accepted the revised migration twice with one stop-marker remark and an unchanged second-run menu timestamp. After both approval tables were deliberately changed to `utf8mb4_0900_ai_ci`, a third execution restored both to `utf8mb4_general_ci` while retaining the expected table, column, index, backup-row, and status invariants.
- Live pre-migration backup: `$HOME/.codex/backups/ERP-NEW/BossERP_stock_state_75c59ee_before_todo_repair_20260712-185758.sql`; SHA-256 `8c8a0f9552d42374888af981c3a087b13e9625e9a665077193a1027133b46c4b`.
- Final provider suites: OA `24/24`, inventory `43/43`; both provider JARs packaged successfully.
- Fresh JAR runtime verification returned business `code: 200` for OA and inventory summary/list endpoints. Safari verification at `/workbench/todo` showed `共 14 条` and no `数据未知：OA、库存` warning.
- Focused implementation commits: `03a1bb8a`, `feb78f72`, `064636bb`, `9af57d8b`, and `09209b93`; design baseline: `1a2a305e`.

---

### Task 1: Add failing mapper collation regression tests

**Files:**
- Modify: `erp-modules/erp-oa/src/test/java/com/erp/oa/mapper/OaTodoMapperBindingTest.java`
- Modify: `erp-modules/erp-inventory/src/test/java/com/erp/inventory/mapper/InvTodoMapperBindingTest.java`

- [x] **Step 1: Add the OA failing test**

```java
@Test
void normalizesEveryOrganizationTextUnionColumnToMysql57Collation() throws Exception
{
    String xml = normalized(text());

    assertThat(count(xml, " as dept_name")).isEqualTo(6);
    assertThat(count(xml, " as dept_type")).isEqualTo(6);
    assertThat(count(xml, "collate utf8mb4_general_ci as dept_name")).isEqualTo(6);
    assertThat(count(xml, "collate utf8mb4_general_ci as dept_type")).isEqualTo(6);
}
```

- [x] **Step 2: Add the inventory failing test**

```java
@Test
@DisplayName("所有组织文本事实使用 MySQL 5.7 兼容排序规则")
void organizationTextFactsShouldUseOneExplicitCollation() throws Exception
{
    String xml = normalized(resourceText(MAPPER_XML));

    assertThat(countOccurrences(xml, " as dept_name")).isEqualTo(16);
    assertThat(countOccurrences(xml, " as dept_type")).isEqualTo(16);
    assertThat(countOccurrences(xml,
            "collate utf8mb4_general_ci as dept_name")).isEqualTo(16);
    assertThat(countOccurrences(xml,
            "collate utf8mb4_general_ci as dept_type")).isEqualTo(16);
}
```

After the first runtime verification exposes the second collation boundary, add this separate failing regression test:

```java
@Test
@DisplayName("所有标题和摘要事实使用 MySQL 5.7 兼容排序规则")
void presentationTextFactsShouldUseOneExplicitCollation() throws Exception
{
    String xml = normalized(resourceText(MAPPER_XML));

    assertThat(countOccurrences(xml, " as title")).isEqualTo(16);
    assertThat(countOccurrences(xml, " as summary")).isEqualTo(16);
    assertThat(countOccurrences(xml,
            "collate utf8mb4_general_ci as title")).isEqualTo(16);
    assertThat(countOccurrences(xml,
            "collate utf8mb4_general_ci as summary")).isEqualTo(16);
}
```

Add a MySQL 5.7 aggregate-expression regression test after review identifies the binary result type:

```java
@Test
@DisplayName("风险聚合摘要先转换为 utf8mb4 再应用排序规则")
void riskAggregateSummariesShouldConvertBeforeCollation() throws Exception
{
    String xml = normalized(resourceText(MAPPER_XML));

    assertThat(todoBranch(xml, "INV_OUT_OF_STOCK")).contains(
            "convert(concat('缺货商品：', count(distinct st.product_id), ' 种') using utf8mb4) "
                    + "collate utf8mb4_general_ci as summary");
    assertThat(todoBranch(xml, "INV_LOW_STOCK")).contains(
            "convert(concat('低库存商品：', count(distinct st.product_id), ' 种') using utf8mb4) "
                    + "collate utf8mb4_general_ci as summary");
}
```

- [x] **Step 3: Run both tests and verify RED**

Run:

```bash
mvn -pl erp-modules/erp-oa -am -Dtest=OaTodoMapperBindingTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
mvn -pl erp-modules/erp-inventory -am -Dtest=InvTodoMapperBindingTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: each module fails only because the explicit-collation count is `0` instead of `6` or `16`.

### Task 2: Normalize provider organization columns

**Files:**
- Modify: `erp-modules/erp-oa/src/main/resources/mapper/oa/OaTodoMapper.xml`
- Modify: `erp-modules/erp-inventory/src/main/resources/mapper/inventory/InvTodoMapper.xml`

- [x] **Step 1: Update every OA organization text projection**

For all six `dept_name/dept_type` projection pairs, change the expressions to this form:

```sql
cast(null as char(128)) collate utf8mb4_general_ci as dept_name,
cast(null as char(32)) collate utf8mb4_general_ci as dept_type,
```

or:

```sql
d.dept_name collate utf8mb4_general_ci as dept_name,
d.dept_type collate utf8mb4_general_ci as dept_type,
```

- [x] **Step 2: Update every inventory organization text projection**

For the empty fact and all fifteen inventory facts, normalize all four text output positions. Use:

```sql
cast(null as char(255)) collate utf8mb4_general_ci as title,
cast(null as char(500)) collate utf8mb4_general_ci as summary,
cast(null as char(128)) collate utf8mb4_general_ci as dept_name,
cast(null as char(32)) collate utf8mb4_general_ci as dept_type,
```

For business facts, insert the same collation immediately before every `as title` and `as summary`, without changing the existing expression. The first branch becomes:

```sql
concat('调拨审批：', o.order_no) collate utf8mb4_general_ci as title,
concat_ws(' → ', o.from_dept_name, o.to_dept_name) collate utf8mb4_general_ci as summary,
context_dept.dept_name collate utf8mb4_general_ci as dept_name,
context_dept.dept_type collate utf8mb4_general_ci as dept_type,
```

For the two aggregate risk summaries, use an explicit character-set conversion before collation:

```sql
convert(concat('缺货商品：', count(distinct st.product_id), ' 种') using utf8mb4)
    collate utf8mb4_general_ci as summary,
convert(concat('低库存商品：', count(distinct st.product_id), ' 种') using utf8mb4)
    collate utf8mb4_general_ci as summary,
```

- [x] **Step 3: Run both mapper tests and verify GREEN**

Run the two Task 1 Maven commands again.

Expected: both test classes pass with no errors.

### Task 3: Make the stock-check migration deterministic and repeat-safe

**Files:**
- Modify: `erp-modules/erp-inventory/src/test/java/com/erp/inventory/sql/StockCheckApprovalSqlSourceTest.java`
- Modify: `sql/erp_inventory_stock_check_approval_20260710.sql`
- Modify: `docker/mysql/db/erp_inventory_stock_check_approval_20260710.sql`

- [x] **Step 1: Add the failing migration test**

```java
@Test
@DisplayName("审批新表显式使用 MySQL 5.7 兼容排序规则")
void shouldCreateApprovalTablesWithExplicitCompatibleCollation() throws Exception
{
    assertThat(count(sql(),
            "ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci"))
            .isEqualTo(2);
}
```

Add this helper:

```java
private static int count(String value, String needle)
{
    return (value.length() - value.replace(needle, "").length()) / needle.length();
}
```

- [x] **Step 2: Run the migration test and verify RED**

Run:

```bash
mvn -pl erp-modules/erp-inventory -am -Dtest=StockCheckApprovalSqlSourceTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: failure because the explicit-collation count is `0` instead of `2`.

- [x] **Step 3: Update both migration copies**

Change the approval table endings to these exact forms in both SQL copies:

```sql
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='库存盘点审批实例';
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='库存盘点审批任务';
```

- [x] **Step 4: Run the migration test and verify GREEN**

Run the Task 3 Step 2 command again.

Expected: all four existing tests plus the new test pass, including byte equality between ordinary and Docker SQL copies.

- [x] **Step 5: Add review follow-up regression tests**

Add one test that requires a conditional `CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci` for both already-existing approval tables, and one test that rejects an unconditional legacy-permission remark append.

Run the Task 3 Step 2 command.

Expected: seven tests run with exactly the two new tests failing.

- [x] **Step 6: Converge existing tables and guard the legacy remark**

Add a temporary procedure that checks `information_schema.TABLES.TABLE_COLLATION` and only converts an approval table when it is not already `utf8mb4_general_ci`. Update the old `inv:stockCheck:confirm` menu with a `LOCATE`/`CASE` guard and skip the update entirely when status, visibility, updater, and marker are already correct. Apply the same changes to both SQL copies.

- [x] **Step 7: Run the expanded migration tests and verify GREEN**

Run the Task 3 Step 2 command again.

Expected: all seven tests pass and both SQL copies remain byte-identical.

### Task 4: Validate migration twice on a cloned database

**Files:**
- Runtime artifact only: `$HOME/.codex/backups/ERP-NEW/`

- [x] **Step 1: Create a consistent logical dump and clone schema**

```bash
SOURCE_DB=BossERP_stock_state_75c59ee
CLONE_DB=BossERP_stock_state_75c59ee_todo_verify_20260712
BACKUP_DIR="$HOME/.codex/backups/ERP-NEW"
mkdir -p "$BACKUP_DIR"
mysqldump --single-transaction --routines --triggers --events \
  --set-gtid-purged=OFF -uroot "$SOURCE_DB" \
  > "$BACKUP_DIR/${SOURCE_DB}_clone_source_20260712.sql"
mysql -uroot -e "DROP DATABASE IF EXISTS \`$CLONE_DB\`; CREATE DATABASE \`$CLONE_DB\` CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;"
mysql -uroot "$CLONE_DB" < "$BACKUP_DIR/${SOURCE_DB}_clone_source_20260712.sql"
```

- [x] **Step 2: Apply the migration twice**

```bash
mysql -uroot "$CLONE_DB" < sql/erp_inventory_stock_check_approval_20260710.sql
mysql -uroot "$CLONE_DB" < sql/erp_inventory_stock_check_approval_20260710.sql
```

Expected: both executions exit `0`.

Also assert that the old confirm menu contains exactly `盘点审批升级后停用` after both runs and that its `update_time` is unchanged by the second run.

- [x] **Step 3: Verify clone invariants**

Run:

```bash
mysql -N -B -uroot "$CLONE_DB" <<'SQL'
select count(*) from information_schema.tables
where table_schema = database()
  and table_name in ('inv_stock_check_approval_instance', 'inv_stock_check_approval_task');
select count(*) from information_schema.tables
where table_schema = database()
  and table_name in ('inv_stock_check_approval_instance', 'inv_stock_check_approval_task')
  and table_collation = 'utf8mb4_general_ci';
select count(*) from information_schema.columns
where table_schema = database() and table_name = 'inv_stock_check'
  and column_name in (
    'approval_instance_id', 'approval_round', 'submitted_user_id', 'submitted_by',
    'submitted_time', 'approved_user_id', 'approved_by', 'approved_time',
    'last_reject_reason', 'last_rejected_user_id', 'last_rejected_by',
    'last_rejected_time', 'last_invalid_reason', 'last_invalid_detail_snapshot',
    'last_invalidated_time'
  );
select count(*) from information_schema.statistics
where table_schema = database() and table_name = 'inv_stock_check_approval_instance'
  and index_name = 'uk_inv_stock_check_approval_round';
select count(*) from information_schema.tables
where table_schema = database()
  and table_name in ('inv_stock_check_backup_20260710', 'inv_stock_check_detail_backup_20260710');
SQL
```

Expected output lines: `2`, `2`, `15`, `2`, `2`. The index count is `2` because MySQL reports one row per indexed column.

- [x] **Step 4: Verify the old-table collation upgrade path**

Convert both clone approval tables to `utf8mb4_0900_ai_ci`, run the migration once more, and verify both table collations return to `utf8mb4_general_ci` while the confirm-menu remark remains a single marker.

### Task 5: Run code verification before touching the current database

**Files:** none.

- [x] **Step 1: Run targeted provider suites**

```bash
mvn -pl erp-modules/erp-oa -am \
  -Dtest=OaTodoMapperBindingTest,OaTodoServiceImplTest,OaTodoControllerTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
mvn -pl erp-modules/erp-inventory -am \
  -Dtest=InvTodoMapperBindingTest,InvTodoServiceImplTest,StockCheckApprovalSqlSourceTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

- [x] **Step 2: Build fresh OA and inventory jars**

```bash
mvn -pl erp-modules/erp-oa,erp-modules/erp-inventory -am -DskipTests package
```

Expected: reactor build succeeds.

### Task 6: Back up, migrate and verify the current runtime

**Files:**
- Runtime backup only: `$HOME/.codex/backups/ERP-NEW/`

- [x] **Step 1: Create the final pre-migration backup**

Run:

```bash
STAMP=$(date +%Y%m%d-%H%M%S)
LIVE_BACKUP="$HOME/.codex/backups/ERP-NEW/BossERP_stock_state_75c59ee_before_todo_repair_${STAMP}.sql"
mysqldump --single-transaction --routines --triggers --events \
  --set-gtid-purged=OFF -uroot BossERP_stock_state_75c59ee > "$LIVE_BACKUP"
test -s "$LIVE_BACKUP"
```

Expected: `test -s` exits `0`.

- [x] **Step 2: Apply the migration to the current database**

```bash
mysql -uroot BossERP_stock_state_75c59ee \
  < sql/erp_inventory_stock_check_approval_20260710.sql
```

- [x] **Step 3: Restart only matching `7月12号` OA and inventory processes**

Run this from the `merge-7月12号` worktree:

```bash
for port in 9204 9205; do
  pid=$(lsof -tiTCP:$port -sTCP:LISTEN | head -1)
  test -n "$pid"
  lsof -a -p "$pid" -d cwd -Fn | rg -q '/merge-7月12号$'
  kill "$pid"
done
for port in 9204 9205; do
  for attempt in $(seq 1 30); do
    lsof -tiTCP:$port -sTCP:LISTEN >/dev/null 2>&1 || break
    sleep 1
  done
  test -z "$(lsof -tiTCP:$port -sTCP:LISTEN | head -1)"
done
nohup java -Dfile.encoding=UTF-8 -jar erp-modules/erp-oa/target/erp-modules-oa.jar \
  --spring.profiles.active=local > "$HOME/.codex/backups/ERP-NEW/erp-oa-todo-repair.out" 2>&1 &
nohup java -Dfile.encoding=UTF-8 -jar erp-modules/erp-inventory/target/erp-modules-inventory.jar \
  --spring.profiles.active=local > "$HOME/.codex/backups/ERP-NEW/erp-inventory-todo-repair.out" 2>&1 &
for port in 9204 9205; do
  for attempt in $(seq 1 60); do
    lsof -tiTCP:$port -sTCP:LISTEN >/dev/null 2>&1 && break
    sleep 1
  done
  test -n "$(lsof -tiTCP:$port -sTCP:LISTEN | head -1)"
done
```

Expected: the old matching processes stop and both new jars listen within 60 seconds.

- [x] **Step 4: Verify the four provider endpoints**

Acquire a local admin token without printing it, resolve `测试门店`, and call all four endpoints:

```bash
test -n "${ERP_LOCAL_ADMIN_PASSWORD:-}"
login_payload=$(jq -n --arg username admin --arg password "$ERP_LOCAL_ADMIN_PASSWORD" \
  '{username: $username, password: $password}')
login_json=$(curl -sS -H 'Content-Type: application/json' \
  -d "$login_payload" http://127.0.0.1:9200/login)
token=$(jq -r '.data.access_token // empty' <<< "$login_json")
test -n "$token"
shop_json=$(curl -sS -H "Authorization: Bearer $token" \
  http://127.0.0.1:8080/system/dept/shop-tree)
dept_id=$(jq -r '.. | objects | select(.deptName? == "测试门店") | .deptId' \
  <<< "$shop_json" | head -1)
test -n "$dept_id"
for endpoint in \
  '/oa/todo/summary' \
  '/oa/todo/list?scopeMode=current_org&pageNum=1&pageSize=10' \
  '/inventory/todo/summary' \
  '/inventory/todo/list?scopeMode=current_org&pageNum=1&pageSize=10'; do
  response=$(curl -sS -H "Authorization: Bearer $token" \
    -H "Dept-NumId: $dept_id" "http://127.0.0.1:8080$endpoint")
  test "$(jq -r '.code' <<< "$response")" = "200"
done
```

Expected: HTTP 200 and JSON business `code: 200` for every request, with no collation or missing-table message.

### Task 7: Final regression and branch commit

**Files:** all files above plus this plan.

- [x] **Step 1: Run final targeted suites and `git diff --check`**

Expected: all targeted tests pass and no whitespace errors are reported.

- [x] **Step 2: Inspect the final diff for unrelated changes and secrets**

Only the design, plan, two Mapper XML files, three test files, and two mirrored migration SQL files may be included.

- [x] **Step 3: Commit to `7月12号`**

```bash
git add docs/superpowers/specs/2026-07-12-unified-todo-provider-runtime-repair-design.md \
  docs/superpowers/plans/2026-07-12-unified-todo-provider-runtime-repair.md \
  erp-modules/erp-inventory/src/test/java/com/erp/inventory/sql/StockCheckApprovalSqlSourceTest.java \
  sql/erp_inventory_stock_check_approval_20260710.sql \
  docker/mysql/db/erp_inventory_stock_check_approval_20260710.sql
git commit -m "fix(db): make stock approval migration repeat-safe"
```
