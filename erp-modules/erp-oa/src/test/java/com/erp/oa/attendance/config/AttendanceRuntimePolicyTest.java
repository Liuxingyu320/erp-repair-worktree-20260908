package com.erp.oa.attendance.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import org.junit.jupiter.api.Test;
import com.erp.common.core.constant.SecurityConstants;
import com.erp.common.core.domain.R;
import com.erp.system.api.RemoteConfigService;

class AttendanceRuntimePolicyTest
{
    @Test
    void readsSeededKeysAndBoundsInvalidValues()
    {
        AttendanceV2Properties properties = new AttendanceV2Properties();
        RemoteConfigService remote = mock(RemoteConfigService.class);
        when(remote.getConfigKey(AttendanceRuntimePolicy.CHALLENGE_TTL_KEY,
                SecurityConstants.INNER)).thenReturn(R.ok("240"));
        when(remote.getConfigKey(AttendanceRuntimePolicy.PHOTO_MAX_BYTES_KEY,
                SecurityConstants.INNER)).thenReturn(R.ok("6000000"));
        AttendanceRuntimePolicy policy = new AttendanceRuntimePolicy(
                properties, remote);
        assertThat(policy.challengeTtlSeconds()).isEqualTo(240);
        assertThat(policy.maxPhotoBytes()).isEqualTo(6000000);

        when(remote.getConfigKey(AttendanceRuntimePolicy.CHALLENGE_TTL_KEY,
                SecurityConstants.INNER)).thenReturn(R.ok("999999"));
        assertThat(policy.challengeTtlSeconds())
                .isEqualTo(properties.getChallengeTtlSeconds());
    }
}
