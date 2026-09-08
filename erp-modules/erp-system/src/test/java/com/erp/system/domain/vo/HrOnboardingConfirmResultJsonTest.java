package com.erp.system.domain.vo;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Date;
import java.time.Instant;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("入职确认结果 JSON 契约")
class HrOnboardingConfirmResultJsonTest
{
    @Test
    @DisplayName("oneTimePasswordExpiresAt 序列化为带时区的 ISO-8601")
    void oneTimePasswordExpiresAtShouldSerializeAsIso8601WithTimezone() throws Exception
    {
        HrOnboardingConfirmResult result = new HrOnboardingConfirmResult();
        result.setOnboardingId(42L);
        result.setUserId(100L);
        result.setEmployeeNo("E000042");
        result.setAccountStatus("ENABLED");
        result.setOneTimePassword("OnceOnlySecret");
        result.setOneTimePasswordExpiresAt(Date.from(Instant.parse("2026-07-12T00:00:00Z")));
        result.setReplayed(false);

        ObjectMapper mapper = new ObjectMapper().disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        String json = mapper.writeValueAsString(result);

        assertThat(json).contains("\"oneTimePasswordExpiresAt\":\"2026-07-12T00:00:00Z\"");
        assertThat(json).contains("\"oneTimePassword\":\"OnceOnlySecret\"");

        HrOnboardingConfirmResult parsed = mapper.readValue(json, HrOnboardingConfirmResult.class);
        assertThat(parsed.getOneTimePasswordExpiresAt())
                .isEqualTo(Date.from(Instant.parse("2026-07-12T00:00:00Z")));
    }

    @Test
    @DisplayName("未设置过期时间时不输出假时间戳")
    void nullExpiryShouldSerializeAsNull() throws Exception
    {
        HrOnboardingConfirmResult result = new HrOnboardingConfirmResult();
        result.setOnboardingId(1L);
        result.setReplayed(true);

        ObjectMapper mapper = new ObjectMapper().disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        String json = mapper.writeValueAsString(result);

        assertThat(json).contains("\"oneTimePasswordExpiresAt\":null");
    }
}
