package com.erp.system.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.erp.common.security.config.ApplicationConfig;
import com.erp.system.api.domain.SysLogininfor;
import com.erp.system.api.domain.SysOperLog;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.Date;
import java.util.TimeZone;
import org.junit.jupiter.api.Test;

class SystemLogTimeZoneSerializationTest
{
    @Test
    void shouldSerializeAuditTimesInAsiaShanghaiRegardlessOfJvmDefault()
            throws Exception
    {
        TimeZone previous = TimeZone.getDefault();
        try
        {
            TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
            ObjectMapper objectMapper = new ApplicationConfig().objectMapper();
            Date instant = Date.from(Instant.parse("2026-01-01T00:00:00Z"));

            SysOperLog operLog = new SysOperLog();
            operLog.setOperTime(instant);
            SysLogininfor logininfor = new SysLogininfor();
            logininfor.setAccessTime(instant);

            assertThat(objectMapper.writeValueAsString(operLog))
                    .contains("\"operTime\":\"2026-01-01 08:00:00\"");
            assertThat(objectMapper.writeValueAsString(logininfor))
                    .contains("\"accessTime\":\"2026-01-01 08:00:00\"");
        }
        finally
        {
            TimeZone.setDefault(previous);
        }
    }
}
