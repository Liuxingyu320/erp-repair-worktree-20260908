package com.erp.system.service.support;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.Properties;
import org.junit.jupiter.api.Test;
import org.springframework.boot.info.BuildProperties;
import com.erp.system.domain.vo.SysBuildInfoVo;

class SystemBuildInfoProviderTest
{
    @Test
    void exposesOnlySafeReleaseDiagnostics()
    {
        Properties properties = new Properties();
        properties.setProperty("commit", "0123456789abcdef0123456789abcdef01234567");
        properties.setProperty("time", "2026-07-14T06:00:00Z");
        properties.setProperty("version", "3.6.8");
        properties.setProperty("host", "must-not-be-exposed");

        SysBuildInfoVo info = new SystemBuildInfoProvider(new BuildProperties(properties)).current();

        assertThat(info.getCommit()).isEqualTo("0123456789abcdef0123456789abcdef01234567");
        assertThat(info.getBuildTime()).isEqualTo(Instant.parse("2026-07-14T06:00:00Z").toString());
        assertThat(info.getVersion()).isEqualTo("3.6.8");
        assertThat(info).hasNoNullFieldsOrProperties();
        assertThat(info.getClass().getDeclaredFields()).extracting("name")
                .containsExactlyInAnyOrder("commit", "buildTime", "version");
    }

    @Test
    void reportsExplicitUnsetValuesWhenBuildMetadataIsUnavailable()
    {
        SysBuildInfoVo info = new SystemBuildInfoProvider((BuildProperties) null).current();

        assertThat(info.getCommit()).isEqualTo("UNSET");
        assertThat(info.getBuildTime()).isEqualTo("UNSET");
        assertThat(info.getVersion()).isEqualTo("UNSET");
    }
}
