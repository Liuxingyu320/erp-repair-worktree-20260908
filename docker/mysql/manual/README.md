# Unified approval manual cutover SQL

Files in this directory are deliberately excluded from Docker bootstrap and
the automatic `new-business-20260714` migration manifest.

The automatic release only performs non-destructive expand/schema/seed work,
keeps inventory/OA feature flags disabled, preserves legacy OA/Flowable data,
and leaves legacy inventory routing visible and active.

Before any manual cutover:

1. Back up the affected business and permission tables and record checksums.
2. Run the unified approval validation/coverage checks and resolve every
   blocking issue.
3. Complete role-based API and browser UAT for the target organizations.
4. Approve a maintenance window and an explicit rollback/forward-fix plan.
5. Run `erp_inventory_unified_approval_cutover_20260714.sql` before
   `erp_unified_approval_center_20260714.sql` only for an approved inventory
   cutover; verify feature flags and in-flight legacy instances immediately.

`erp_oa_flowable_test_data_purge_20260714.sql` is destructive test-data cleanup
and must never be used as a production migration. OA production cutover keeps
legacy columns, comments, process history, and rows; the new non-destructive
expand migration adds the unified-approval columns alongside them.
