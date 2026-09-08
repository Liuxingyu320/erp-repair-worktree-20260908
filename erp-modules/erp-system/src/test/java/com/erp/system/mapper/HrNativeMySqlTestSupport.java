package com.erp.system.mapper;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Locale;

/** Hard stop that keeps native HR tests away from business databases. */
public final class HrNativeMySqlTestSupport
{
    private HrNativeMySqlTestSupport()
    {
    }

    public static void requireIsolatedDatabase(Connection connection)
            throws SQLException
    {
        String database;
        try (Statement statement = connection.createStatement();
                ResultSet rows = statement.executeQuery("select database()"))
        {
            if (!rows.next())
            {
                throw new IllegalStateException(
                        "HR_NATIVE_TEST_DATABASE_REQUIRED: 无法读取当前数据库");
            }
            database = rows.getString(1);
        }
        String normalized = database == null ? ""
                : database.toLowerCase(Locale.ROOT);
        if (!(normalized.contains("test") || normalized.contains("_it_")
                || normalized.contains("rehearsal")))
        {
            throw new IllegalStateException(
                    "HR_NATIVE_TEST_DATABASE_REQUIRED: 仅允许名称包含 test、_it_ 或 rehearsal 的隔离库，当前库="
                            + database);
        }
    }
}
