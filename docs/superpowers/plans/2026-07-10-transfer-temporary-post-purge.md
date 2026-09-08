# Transfer Temporary Post Purge Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Safely delete all pending approval tasks that reference the temporary transfer posts, detach completed history, repair affected transfer and approval-instance states, and then delete posts `sijifzr` and `sanjifzr`.

**Architecture:** Add one explicit, count-guarded administrative SQL migration separate from the existing conservative cleanup script. The migration snapshots exact IDs into temporary tables, verifies the audited 49/34/13/2 and 30/4 counts, performs every mutation in one transaction, and rolls back on any mismatch. A static Node contract test guards the irreversible behavior; disposable database executions verify both abort and success paths before the real database is backed up and migrated.

**Tech Stack:** MySQL 5.7-compatible SQL, Node.js built-in test/assert tooling, Maven/JUnit approval regression tests, Git.

---

## File map

- Create `sql/erp_inventory_transfer_temporary_post_purge_20260710.sql`: explicit destructive administrative migration with exact-count guards, transaction handling, state repair, history detachment, and final assertions.
- Modify `erp-ui/test/transferApprovalDataScripts.test.js`: static contract for the new script and its required safety properties.
- Preserve `sql/erp_inventory_transfer_temporary_post_cleanup_20260710.sql`: conservative reusable cleanup remains unchanged; it must continue rejecting referenced history.
- Preserve `docker/mysql/db/`: do not place the destructive script in automatic database initialization paths.

### Task 1: Define the destructive migration contract

**Files:**
- Modify: `erp-ui/test/transferApprovalDataScripts.test.js`
- Test: `erp-ui/test/transferApprovalDataScripts.test.js`

- [ ] **Step 1: Add the failing contract test**

Add a test that reads `sql/erp_inventory_transfer_temporary_post_purge_20260710.sql` and requires all of these exact protections:

```js
const purgePath = path.resolve(
  __dirname,
  "../../sql/erp_inventory_transfer_temporary_post_purge_20260710.sql"
)
const purgeSql = fs.readFileSync(purgePath, "utf8")

assert.ok(purgeSql.includes("create procedure purge_transfer_temporary_posts"))
assert.ok(purgeSql.includes("declare exit handler for sqlexception"))
assert.ok(purgeSql.includes("start transaction"))
assert.ok(purgeSql.includes("rollback"))
assert.ok(purgeSql.includes("commit"))
assert.ok(purgeSql.includes("临时岗位任务数量已变化，停止清理"))
assert.ok(purgeSql.includes("待审批临时任务数量已变化，停止清理"))
assert.ok(purgeSql.includes("审核中调拨单数量已变化，停止清理"))
assert.ok(purgeSql.includes("已取消调拨单数量已变化，停止清理"))
assert.ok(purgeSql.includes("temporary_post_purge_reset"))
assert.ok(purgeSql.includes("set transfer_order.status = 'draft'"))
assert.ok(purgeSql.includes("set approval_instance.status = 'closed'"))
assert.ok(purgeSql.includes("delete pending_task"))
assert.ok(purgeSql.includes("history_task.post_id = null"))
assert.ok(purgeSql.includes("history_task.post_code = ''"))
assert.ok(purgeSql.includes("delete user_post"))
assert.ok(purgeSql.includes("delete temporary_post"))
assert.ok(purgeSql.includes("清理后仍存在临时岗位，事务回滚"))
assert.ok(purgeSql.includes("清理后仍存在临时岗位任务引用，事务回滚"))
```

- [ ] **Step 2: Run the test and verify RED**

Run:

```bash
cd erp-ui
node --test test/transferApprovalDataScripts.test.js
```

Expected: FAIL with `ENOENT` for `erp_inventory_transfer_temporary_post_purge_20260710.sql`.

- [ ] **Step 3: Commit the failing contract**

```bash
git add erp-ui/test/transferApprovalDataScripts.test.js
git commit -m "test: define transfer temporary post purge"
```

### Task 2: Implement the count-guarded transactional purge

**Files:**
- Create: `sql/erp_inventory_transfer_temporary_post_purge_20260710.sql`
- Test: `erp-ui/test/transferApprovalDataScripts.test.js`

- [ ] **Step 1: Create the migration preflight and ID snapshots**

Create the script with MySQL 5.7-compatible temporary tables and a stored procedure. The implementation must begin with this structure:

```sql
-- 已经用户确认的调拨临时岗位彻底清理脚本。
-- 仅适用于 2026-07-10 审计得到的 49/34/13/2 条任务和 30/4 张单据。
-- 任一数量变化都会回滚；禁止放入 Docker 自动初始化目录。

drop procedure if exists purge_transfer_temporary_posts;
delimiter //
create procedure purge_transfer_temporary_posts()
begin
    declare temporary_post_count int default 0;
    declare referenced_task_count int default 0;
    declare pending_task_count int default 0;
    declare approved_task_count int default 0;
    declare rejected_task_count int default 0;
    declare affected_instance_count int default 0;
    declare running_instance_count int default 0;
    declare submitted_transfer_count int default 0;
    declare cancelled_transfer_count int default 0;
    declare only_temporary_post_user_count int default 0;
    declare active_rule_reference_count int default 0;
    declare remaining_post_count int default 0;
    declare remaining_task_reference_count int default 0;
    declare remaining_user_post_count int default 0;
    declare remaining_pending_task_count int default 0;
    declare detached_history_task_count int default 0;
    declare reset_draft_transfer_count int default 0;
    declare preserved_cancelled_transfer_count int default 0;
    declare closed_instance_count int default 0;
    declare reset_status_log_count int default 0;

    declare exit handler for sqlexception
    begin
        rollback;
        resignal;
    end;

    start transaction;

    drop temporary table if exists tmp_transfer_temporary_posts;
    create temporary table tmp_transfer_temporary_posts (
        post_id bigint not null,
        post_code varchar(64) not null,
        primary key (post_id),
        unique key uk_tmp_transfer_temporary_post_code (post_code)
    ) engine=InnoDB;

    insert into tmp_transfer_temporary_posts (post_id, post_code)
    select post_id, post_code
    from sys_post
    where post_code in ('sijifzr', 'sanjifzr')
    for update;

    select count(*) into temporary_post_count
    from tmp_transfer_temporary_posts;

    if temporary_post_count <> 2 then
        signal sqlstate '45000'
            set message_text = '临时岗位数量不是2，停止清理';
    end if;

    drop temporary table if exists tmp_transfer_temporary_tasks;
    create temporary table tmp_transfer_temporary_tasks (
        task_id bigint not null,
        instance_id bigint not null,
        transfer_id bigint not null,
        task_status varchar(32) not null,
        original_post_code varchar(64),
        primary key (task_id),
        key idx_tmp_transfer_temporary_task_instance (instance_id),
        key idx_tmp_transfer_temporary_task_transfer (transfer_id)
    ) engine=InnoDB;

    insert into tmp_transfer_temporary_tasks (
        task_id, instance_id, transfer_id, task_status, original_post_code
    )
    select distinct task.task_id,
           task.instance_id,
           task.transfer_id,
           task.status,
           temporary_post.post_code
    from inv_transfer_approval_task task
    join tmp_transfer_temporary_posts temporary_post
      on temporary_post.post_id = task.post_id
      or temporary_post.post_code = task.post_code collate utf8mb4_general_ci
    for update;
```

- [ ] **Step 2: Add exact audited-count assertions**

After populating the task snapshot, calculate and assert the approved counts:

```sql
    select count(*) into referenced_task_count
    from tmp_transfer_temporary_tasks;
    select count(*) into pending_task_count
    from tmp_transfer_temporary_tasks where task_status = 'pending';
    select count(*) into approved_task_count
    from tmp_transfer_temporary_tasks where task_status = 'approved';
    select count(*) into rejected_task_count
    from tmp_transfer_temporary_tasks where task_status = 'rejected';

    if referenced_task_count <> 49 then
        signal sqlstate '45000'
            set message_text = '临时岗位任务数量已变化，停止清理';
    end if;
    if pending_task_count <> 34 then
        signal sqlstate '45000'
            set message_text = '待审批临时任务数量已变化，停止清理';
    end if;
    if approved_task_count <> 13 or rejected_task_count <> 2 then
        signal sqlstate '45000'
            set message_text = '已结束临时任务数量已变化，停止清理';
    end if;
```

Create `tmp_transfer_temporary_instances` from the 34 pending tasks and assert the instance and transfer states:

```sql
    drop temporary table if exists tmp_transfer_temporary_instances;
    create temporary table tmp_transfer_temporary_instances (
        instance_id bigint not null,
        transfer_id bigint not null,
        instance_status varchar(32) not null,
        transfer_status varchar(20) not null,
        primary key (instance_id),
        unique key uk_tmp_transfer_temporary_transfer (transfer_id)
    ) engine=InnoDB;

    insert into tmp_transfer_temporary_instances (
        instance_id, transfer_id, instance_status, transfer_status
    )
    select distinct approval_instance.instance_id,
           transfer_order.transfer_id,
           approval_instance.status,
           transfer_order.status
    from tmp_transfer_temporary_tasks temporary_task
    join inv_transfer_approval_instance approval_instance
      on approval_instance.instance_id = temporary_task.instance_id
    join inv_transfer_order transfer_order
      on transfer_order.transfer_id = temporary_task.transfer_id
    where temporary_task.task_status = 'pending'
    for update;

    select count(*) into affected_instance_count
    from tmp_transfer_temporary_instances;
    select count(*) into running_instance_count
    from tmp_transfer_temporary_instances
    where instance_status = 'running';
    select count(*) into submitted_transfer_count
    from tmp_transfer_temporary_instances
    where transfer_status = 'submitted';
    select count(*) into cancelled_transfer_count
    from tmp_transfer_temporary_instances
    where transfer_status = 'cancelled';

    if running_instance_count <> 34 or affected_instance_count <> 34 then
        signal sqlstate '45000'
            set message_text = '运行中审批实例数量已变化，停止清理';
    end if;
    if submitted_transfer_count <> 30 then
        signal sqlstate '45000'
            set message_text = '审核中调拨单数量已变化，停止清理';
    end if;
    if cancelled_transfer_count <> 4 then
        signal sqlstate '45000'
            set message_text = '已取消调拨单数量已变化，停止清理';
    end if;
```

Assert that current rule nodes already have no temporary-post reference:

```sql
    select count(*) into active_rule_reference_count
    from inv_transfer_approval_node approval_node
    join tmp_transfer_temporary_posts temporary_post
      on temporary_post.post_id = approval_node.post_id
      or temporary_post.post_code = approval_node.post_code collate utf8mb4_general_ci;

    if active_rule_reference_count <> 0 then
        signal sqlstate '45000'
            set message_text = '当前审批规则仍引用临时岗位，停止清理';
    end if;
```

Add the existing user safety check:

```sql
    select count(*) into only_temporary_post_user_count
    from sys_user_post temporary_user_post
    join tmp_transfer_temporary_posts temporary_post
      on temporary_post.post_id = temporary_user_post.post_id
    where not exists (
        select 1
        from sys_user_post real_user_post
        join sys_post real_post on real_post.post_id = real_user_post.post_id
        where real_user_post.user_id = temporary_user_post.user_id
          and real_post.post_code not in ('sijifzr', 'sanjifzr')
    );

    if only_temporary_post_user_count <> 0 then
        signal sqlstate '45000'
            set message_text = '存在仅配置临时岗位的用户，停止清理';
    end if;
```

- [ ] **Step 3: Repair transfer and instance state**

Insert one status log per affected submitted transfer, then reset those transfers:

```sql
    insert into inv_transfer_status_log (
        transfer_id, from_status, to_status, action,
        operator_id, operator_name, reason, create_time
    )
    select affected.transfer_id,
           'submitted',
           'draft',
           'temporary_post_purge_reset',
           null,
           'system',
           '清理临时三级/四级岗位审批任务，退回草稿后按新审批规则重新提交',
           now()
    from tmp_transfer_temporary_instances affected
    where affected.transfer_status = 'submitted';

    update inv_transfer_order transfer_order
    join tmp_transfer_temporary_instances affected
      on affected.transfer_id = transfer_order.transfer_id
     and affected.transfer_status = 'submitted'
    set transfer_order.status = 'draft',
        transfer_order.approval_instance_id = null,
        transfer_order.workflow_instance_id = null,
        transfer_order.submitted_time = null,
        transfer_order.approved_time = null,
        transfer_order.update_by = 'system',
        transfer_order.update_time = now();

    update inv_transfer_order transfer_order
    join tmp_transfer_temporary_instances affected
      on affected.transfer_id = transfer_order.transfer_id
     and affected.transfer_status = 'cancelled'
    set transfer_order.approval_instance_id = null,
        transfer_order.workflow_instance_id = null,
        transfer_order.update_by = 'system',
        transfer_order.update_time = now();

    update inv_transfer_approval_instance approval_instance
    join tmp_transfer_temporary_instances affected
      on affected.instance_id = approval_instance.instance_id
    set approval_instance.status = 'closed',
        approval_instance.update_by = 'system',
        approval_instance.update_time = now(),
        approval_instance.remark = left(concat_ws(
            '；', nullif(trim(approval_instance.remark), ''),
            '临时三级/四级岗位清理，旧审批实例关闭'
        ), 500);
```

- [ ] **Step 4: Delete pending tasks and detach completed history**

Use only the snapshotted task IDs:

```sql
    delete pending_task
    from inv_transfer_approval_task pending_task
    join tmp_transfer_temporary_tasks temporary_task
      on temporary_task.task_id = pending_task.task_id
     and temporary_task.task_status = 'pending';

    update inv_transfer_approval_task history_task
    join tmp_transfer_temporary_tasks temporary_task
      on temporary_task.task_id = history_task.task_id
     and temporary_task.task_status in ('approved', 'rejected')
    set history_task.remark = left(concat_ws(
            '；', nullif(trim(history_task.remark), ''),
            concat('原临时岗位编码=', temporary_task.original_post_code,
                   '，岗位主数据已清理')
        ), 500),
        history_task.post_id = null,
        history_task.post_code = '',
        history_task.update_by = 'system',
        history_task.update_time = now();
```

Do not change `post_name`, `candidate_user_ids`, `candidate_user_names`, `approver_id`, `approver_name`, `approve_time`, `comment`, or `status` for the 15 historical tasks.

- [ ] **Step 5: Delete user links and posts, then assert the result**

```sql
    delete user_post
    from sys_user_post user_post
    join tmp_transfer_temporary_posts temporary_post
      on temporary_post.post_id = user_post.post_id;

    delete temporary_post
    from sys_post temporary_post
    join tmp_transfer_temporary_posts selected_post
      on selected_post.post_id = temporary_post.post_id;

    select count(*) into remaining_post_count
    from sys_post
    where post_code in ('sijifzr', 'sanjifzr');

    select count(*) into remaining_task_reference_count
    from inv_transfer_approval_task task
    join tmp_transfer_temporary_posts temporary_post
      on temporary_post.post_id = task.post_id
      or temporary_post.post_code = task.post_code collate utf8mb4_general_ci;

    select count(*) into remaining_user_post_count
    from sys_user_post user_post
    join tmp_transfer_temporary_posts temporary_post
      on temporary_post.post_id = user_post.post_id;

    select count(*) into remaining_pending_task_count
    from inv_transfer_approval_task task
    join tmp_transfer_temporary_tasks selected_task
      on selected_task.task_id = task.task_id
     and selected_task.task_status = 'pending';

    select count(*) into detached_history_task_count
    from inv_transfer_approval_task task
    join tmp_transfer_temporary_tasks selected_task
      on selected_task.task_id = task.task_id
     and selected_task.task_status in ('approved', 'rejected')
    where task.post_id is null
      and coalesce(task.post_code, '') = '';

    select count(*) into reset_draft_transfer_count
    from inv_transfer_order transfer_order
    join tmp_transfer_temporary_instances affected
      on affected.transfer_id = transfer_order.transfer_id
     and affected.transfer_status = 'submitted'
    where transfer_order.status = 'draft'
      and transfer_order.approval_instance_id is null
      and transfer_order.workflow_instance_id is null;

    select count(*) into preserved_cancelled_transfer_count
    from inv_transfer_order transfer_order
    join tmp_transfer_temporary_instances affected
      on affected.transfer_id = transfer_order.transfer_id
     and affected.transfer_status = 'cancelled'
    where transfer_order.status = 'cancelled'
      and transfer_order.approval_instance_id is null
      and transfer_order.workflow_instance_id is null;

    select count(*) into closed_instance_count
    from inv_transfer_approval_instance approval_instance
    join tmp_transfer_temporary_instances affected
      on affected.instance_id = approval_instance.instance_id
    where approval_instance.status = 'closed';

    select count(*) into reset_status_log_count
    from inv_transfer_status_log status_log
    join tmp_transfer_temporary_instances affected
      on affected.transfer_id = status_log.transfer_id
     and affected.transfer_status = 'submitted'
    where status_log.action = 'temporary_post_purge_reset';

    if remaining_post_count <> 0 then
        signal sqlstate '45000'
            set message_text = '清理后仍存在临时岗位，事务回滚';
    end if;
    if remaining_task_reference_count <> 0 then
        signal sqlstate '45000'
            set message_text = '清理后仍存在临时岗位任务引用，事务回滚';
    end if;
    if remaining_user_post_count <> 0 then
        signal sqlstate '45000'
            set message_text = '清理后仍存在临时岗位用户关联，事务回滚';
    end if;
    if remaining_pending_task_count <> 0 then
        signal sqlstate '45000'
            set message_text = '清理后仍存在待审批临时任务，事务回滚';
    end if;
    if detached_history_task_count <> 15 then
        signal sqlstate '45000'
            set message_text = '历史临时任务解除引用数量异常，事务回滚';
    end if;
    if reset_draft_transfer_count <> 30 then
        signal sqlstate '45000'
            set message_text = '审核中调拨单退回草稿数量异常，事务回滚';
    end if;
    if preserved_cancelled_transfer_count <> 4 then
        signal sqlstate '45000'
            set message_text = '已取消调拨单保持状态数量异常，事务回滚';
    end if;
    if closed_instance_count <> 34 then
        signal sqlstate '45000'
            set message_text = '旧审批实例关闭数量异常，事务回滚';
    end if;
    if reset_status_log_count <> 30 then
        signal sqlstate '45000'
            set message_text = '调拨单退回草稿日志数量异常，事务回滚';
    end if;

    commit;
end//
delimiter ;

call purge_transfer_temporary_posts();
drop procedure purge_transfer_temporary_posts;
```

- [ ] **Step 6: Run the static contract and verify GREEN**

Run:

```bash
cd erp-ui
node --test test/transferApprovalDataScripts.test.js
```

Expected: PASS, 1 test file and 0 failures.

- [ ] **Step 7: Commit the migration**

```bash
git add sql/erp_inventory_transfer_temporary_post_purge_20260710.sql \
  erp-ui/test/transferApprovalDataScripts.test.js
git commit -m "feat: safely purge transfer temporary posts"
```

### Task 3: Verify abort and success paths in disposable databases

**Files:**
- Test: `sql/erp_inventory_transfer_temporary_post_purge_20260710.sql`

- [ ] **Step 1: Create an abort-path clone**

Define and run this exact clone helper:

```bash
clone_purge_database() {
  local target_database="$1"
  mysql -uroot -e "drop database if exists ${target_database}; create database ${target_database} character set utf8mb4 collate utf8mb4_general_ci"
  for table_name in \
    inv_transfer_order \
    inv_transfer_approval_instance \
    inv_transfer_approval_task \
    inv_transfer_status_log \
    inv_transfer_approval_node \
    sys_post \
    sys_user_post
  do
    mysql -uroot -e "create table ${target_database}.${table_name} like BossERP_stock_state_75c59ee.${table_name}; insert into ${target_database}.${table_name} select * from BossERP_stock_state_75c59ee.${table_name}"
  done
}

clone_purge_database codex_transfer_post_purge_abort
```

Insert one extra copied temporary-post task with a new `task_id` so the count becomes 50:

```bash
mysql -uroot codex_transfer_post_purge_abort -e "insert into inv_transfer_approval_task (instance_id,transfer_id,node_order,node_name,post_id,post_code,post_name,candidate_user_ids,candidate_user_names,status,approver_id,approver_name,approve_time,comment,create_by,create_time,update_by,update_time,remark) select instance_id,transfer_id,node_order,node_name,post_id,post_code,post_name,candidate_user_ids,candidate_user_names,status,approver_id,approver_name,approve_time,comment,create_by,create_time,update_by,update_time,remark from inv_transfer_approval_task where post_code in ('sijifzr','sanjifzr') limit 1"
mysql -uroot codex_transfer_post_purge_abort --execute="source /Users/liuxingyu/Desktop/备份/ERP-NEW/sql/erp_inventory_transfer_temporary_post_purge_20260710.sql"
```

Expected: the second command exits 1 with `临时岗位任务数量已变化，停止清理`.

Verify rollback:

```bash
mysql -uroot --batch --skip-column-names codex_transfer_post_purge_abort -e "select concat('posts=',count(*)) from sys_post where post_code in ('sijifzr','sanjifzr'); select concat('submitted=',count(*)) from inv_transfer_order where status='submitted'; select concat('reset_logs=',count(*)) from inv_transfer_status_log where action='temporary_post_purge_reset'"
```

Expected: `posts=2`, the original submitted count, and `reset_logs=0`.

- [ ] **Step 2: Create a clean success-path clone**

Run the clone helper without adding an extra task:

```bash
clone_purge_database codex_transfer_post_purge_success
```

- [ ] **Step 3: Execute the script in the success clone**

```bash
mysql -uroot codex_transfer_post_purge_success \
  --execute="source /Users/liuxingyu/Desktop/备份/ERP-NEW/sql/erp_inventory_transfer_temporary_post_purge_20260710.sql"
```

Expected: exit code 0.

- [ ] **Step 4: Verify every postcondition**

Run aggregate queries that prove:

```text
temporary_posts=0
temporary_user_links=0
temporary_task_references=0
pending_snapshotted_tasks=0
detached_history_tasks=15
reset_draft_transfers=30
preserved_cancelled_transfers=4
closed_instances=34
reset_status_logs=30
```

Also compare the 15 history task audit fields before and after; only `post_id`, `post_code`, `remark`, `update_by`, and `update_time` may differ.

- [ ] **Step 5: Drop both disposable databases**

```bash
mysql -uroot -e "drop database codex_transfer_post_purge_abort; drop database codex_transfer_post_purge_success"
```

### Task 4: Back up and migrate the real local database

**Files:**
- Execute: `sql/erp_inventory_transfer_temporary_post_purge_20260710.sql`

- [ ] **Step 1: Re-run the read-only preflight**

Confirm the counts are still exactly 49 total, 34 pending, 13 approved, 2 rejected, 30 submitted transfers, 4 cancelled transfers, and zero users with only temporary posts. Stop if any count differs.

- [ ] **Step 2: Create a dedicated real-database backup**

```bash
mysqldump -uroot --single-transaction \
  --result-file=/tmp/BossERP_before_transfer_temporary_post_purge_20260710.sql \
  BossERP_stock_state_75c59ee \
  inv_transfer_order inv_transfer_approval_instance inv_transfer_approval_task \
  inv_transfer_status_log inv_transfer_approval_rule inv_transfer_approval_node \
  sys_post sys_user_post
```

Run `test -s /tmp/BossERP_before_transfer_temporary_post_purge_20260710.sql` and `ls -lh` to prove the backup is non-empty.

- [ ] **Step 3: Execute the real migration**

```bash
mysql -uroot BossERP_stock_state_75c59ee \
  --execute="source /Users/liuxingyu/Desktop/备份/ERP-NEW/sql/erp_inventory_transfer_temporary_post_purge_20260710.sql"
```

Expected: exit code 0. Do not retry after a failure until the error and transaction state have been inspected.

- [ ] **Step 4: Verify real-database postconditions**

Run the same aggregate checks as the disposable success database. Also verify rule 6 remains:

```text
1 level4_highest dynamic
2 level3_highest dynamic
3 yyzj
4 zjl
```

- [ ] **Step 5: Re-run the target-store candidate preview**

```bash
mysql -uroot --batch --skip-column-names BossERP_stock_state_75c59ee \
  --execute="source /Users/liuxingyu/Desktop/备份/ERP-NEW/sql/erp_inventory_transfer_position_chain_preview_20260710.sql"
```

Expected aggregate remains `stores=81 level4=56 level3=51 both=27 neither=1` unless organization data changed; if it changed, report the new result rather than overriding data.

### Task 5: Final regression, review, and commit state

**Files:**
- Verify: `erp-ui/test/transferApprovalDataScripts.test.js`
- Verify: `erp-modules/erp-inventory/src/test/java/com/erp/inventory/service/impl/InvTransferApprovalServiceImplTest.java`
- Verify: `erp-modules/erp-inventory/src/test/java/com/erp/inventory/service/impl/InvTransferApprovalRuleServiceImplTest.java`
- Verify: `erp-modules/erp-inventory/src/test/java/com/erp/inventory/mapper/InventoryMapperBindingTest.java`

- [ ] **Step 1: Run frontend data-script regression**

```bash
cd erp-ui
node --test test/transferApprovalDataScripts.test.js test/transferApprovalRules.test.js test/dockerScripts.test.js
```

Expected: 3 tests, 0 failures.

- [ ] **Step 2: Run backend approval regression**

```bash
mvn -pl erp-modules/erp-inventory -am \
  -Dtest=InvTransferApprovalServiceImplTest,InvTransferApprovalRuleServiceImplTest,InventoryMapperBindingTest,InvTransferServiceImplTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: 89 tests, 0 failures, `BUILD SUCCESS`.

- [ ] **Step 3: Review the committed diff**

```bash
git diff --check HEAD~2..HEAD
git diff --stat HEAD~2..HEAD
git status --short --branch
```

Confirm only the purge SQL, its test, and the approved design/plan commits belong to this change. Preserve all unrelated user working-tree changes.

- [ ] **Step 4: Record the outcome**

Report the commit hashes, backup path, exact deleted/detached/reset counts, final rule nodes, candidate-preview counts, and test commands. Explicitly state that the 30 reset transfers must be resubmitted to enter the new approval chain.
