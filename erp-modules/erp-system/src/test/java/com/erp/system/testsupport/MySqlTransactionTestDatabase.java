package com.erp.system.testsupport;

import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.testcontainers.mysql.MySQLContainer;

/** Shared transaction suites: explicit native profile, or the existing container release gate. */
public final class MySqlTransactionTestDatabase implements BeforeAllCallback, AfterAllCallback
{
    private final String suite;
    private final String propertyPrefix;
    private NativeMySqlIntegrationTestSupport nativeDatabase;
    private MySQLContainer container;

    public MySqlTransactionTestDatabase(String suite, String propertyPrefix)
    {
        this.suite = suite;
        this.propertyPrefix = propertyPrefix;
    }

    private synchronized void start()
    {
        if (nativeDatabase != null || container != null) return;
        if (Boolean.getBoolean("erp.it.native-mysql"))
        {
            // Missing credentials fail here; native mode must never fall back to Docker.
            nativeDatabase = NativeMySqlIntegrationTestSupport.create(suite, propertyPrefix);
        }
        else
        {
            MySQLContainer candidate = new MySQLContainer("mysql:5.7.44")
                    .withDatabaseName("hr_" + suite + "_it")
                    .withUsername("hr_it").withPassword("hr_it_password")
                    .withEnv("MYSQL_INITDB_SKIP_TZINFO", "1")
                    .withCommand("--character-set-server=utf8mb4", "--collation-server=utf8mb4_unicode_ci");
            try
            {
                candidate.start();
                container = candidate;
            }
            catch (RuntimeException failure)
            {
                candidate.stop();
                throw failure;
            }
        }
    }

    public String getJdbcUrl()
    {
        start();
        return nativeDatabase != null ? nativeDatabase.getJdbcUrl() : container.getJdbcUrl();
    }

    public String getUsername()
    {
        start();
        return nativeDatabase != null ? nativeDatabase.getUsername() : container.getUsername();
    }

    public String getPassword()
    {
        start();
        return nativeDatabase != null ? nativeDatabase.getPassword() : container.getPassword();
    }

    public String getExpectedVersionPrefix()
    {
        start();
        return nativeDatabase != null ? nativeDatabase.getServerVersion() : "5.7.44";
    }

    @Override
    public void beforeAll(ExtensionContext context)
    {
        start();
    }

    @Override
    public void afterAll(ExtensionContext context)
    {
        if (nativeDatabase != null) nativeDatabase.close();
        if (container != null) container.stop();
    }
}
