package com.erp.system.testsupport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("原生 MySQL 隔离库安全边界")
class NativeMySqlIntegrationTestSupportTest
{
    @Test
    void localHostsAreRecognized()
    {
        assertThat(NativeMySqlIntegrationTestSupport.isLocalHost("127.0.0.1")).isTrue();
        assertThat(NativeMySqlIntegrationTestSupport.isLocalHost("localhost")).isTrue();
        assertThat(NativeMySqlIntegrationTestSupport.isLocalHost("::1")).isTrue();
        assertThat(NativeMySqlIntegrationTestSupport.isLocalHost("db.example.internal")).isFalse();
    }

    @Test
    void onlyGeneratedIsolationDatabaseNamesAreAccepted()
    {
        NativeMySqlIntegrationTestSupport.validateDatabaseName("erp_it_run_transfer_a1b2c3d4");

        assertThatIllegalArgumentException().isThrownBy(
                () -> NativeMySqlIntegrationTestSupport.validateDatabaseName("erp"));
        assertThatIllegalArgumentException().isThrownBy(
                () -> NativeMySqlIntegrationTestSupport.validateDatabaseName("mysql"));
        assertThatIllegalArgumentException().isThrownBy(
                () -> NativeMySqlIntegrationTestSupport.validateDatabaseName("erp_it_bad-name"));
        assertThatIllegalArgumentException().isThrownBy(
                () -> NativeMySqlIntegrationTestSupport.validateDatabaseName(""));
    }
}
