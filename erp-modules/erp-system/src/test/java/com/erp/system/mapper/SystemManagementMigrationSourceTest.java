package com.erp.system.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class SystemManagementMigrationSourceTest
{
    @Test
    void employeeLifecyclePermissionsAreDeployableAuditedAndRollbackSafe() throws Exception
    {
        Path root = repositoryRoot();
        Path forward = root.resolve("sql/erp_system_management_hardening_20260713.sql");
        Path mirror = root.resolve("docker/mysql/db/erp_system_management_hardening_20260713.sql");
        String rollback = Files.readString(
                root.resolve("sql/erp_system_management_hardening_rollback_20260713.sql"));
        String audit = Files.readString(root.resolve("scripts/audit-system-management-migration.sh"));

        byte[] forwardBytes = Files.readAllBytes(forward);
        assertThat(Files.exists(mirror)).as("manual migration must stay out of automatic Docker bootstrap").isFalse();

        String migration = new String(forwardBytes, java.nio.charset.StandardCharsets.UTF_8);
        assertThat(migration).contains(
                "'员工续签确认'",
                "'hr:employee:renewal'",
                "'员工转正确认'",
                "'hr:employee:regularize'",
                "仅补目录，不默认授予任何普通角色",
                "ALTER TABLE inv_transfer_approval_rule ADD COLUMN version int NOT NULL DEFAULT 1");
        assertThat(rollback).contains("'hr:employee:renewal'", "'hr:employee:regularize'");
        assertThat(audit).contains(
                "UNION ALL SELECT 'hr:employee:renewal'",
                "UNION ALL SELECT 'hr:employee:regularize'",
                "inv_transfer_approval_rule.version definition");
    }

    private static Path repositoryRoot()
    {
        Path root = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        while (root.getParent() != null && !Files.exists(root.resolve("sql")))
        {
            root = root.getParent();
        }
        return root;
    }
}
