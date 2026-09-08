package com.erp.oa.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Method;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import com.erp.oa.domain.OaSignPackage;

class OaSignPackageExpirySchedulerTest
{
    @Test
    void schedulerIsOptInAndUsesOneMinuteDefaultDelay() throws Exception
    {
        ConditionalOnProperty condition = OaSignPackageExpiryScheduler.class
                .getAnnotation(ConditionalOnProperty.class);
        Method method = OaSignPackageExpiryScheduler.class.getMethod("expireDue");
        Scheduled scheduled = method.getAnnotation(Scheduled.class);

        assertThat(condition).isNotNull();
        assertThat(condition.prefix()).isEqualTo("oa.sign.expiry");
        assertThat(condition.name()).containsExactly("enabled");
        assertThat(condition.havingValue()).isEqualTo("true");
        assertThat(condition.matchIfMissing()).isFalse();
        assertThat(scheduled.fixedDelayString())
                .isEqualTo("${oa.sign.expiry.fixed-delay-ms:60000}");
    }

    @Test
    void repositoryConfigKeepsExpiryOffUntilProductionExplicitlyEnablesIt() throws Exception
    {
        try (InputStream input = getClass().getClassLoader().getResourceAsStream("bootstrap.yml"))
        {
            assertThat(input).isNotNull();
            String yaml = new String(input.readAllBytes(), StandardCharsets.UTF_8);
            assertThat(yaml)
                    .contains("enabled: ${OA_SIGN_EXPIRY_ENABLED:false}")
                    .contains("fixed-delay-ms: ${OA_SIGN_EXPIRY_FIXED_DELAY_MS:60000}");
        }
    }

    @Test
    void oneFailedCandidateDoesNotBlockTheRest()
    {
        OaSignPackageLifecycleService service = mock(OaSignPackageLifecycleService.class);
        OaSignPackage first = candidate(1L);
        OaSignPackage second = candidate(2L);
        when(service.selectExpiredCandidates(100)).thenReturn(List.of(first, second));
        when(service.expireOne(first)).thenThrow(new IllegalStateException("broken row"));
        OaSignPackageExpiryScheduler scheduler = new OaSignPackageExpiryScheduler(service);

        scheduler.expireDue();

        verify(service).expireOne(first);
        verify(service).expireOne(second);
    }

    private OaSignPackage candidate(Long packageId)
    {
        OaSignPackage signPackage = new OaSignPackage();
        signPackage.setPackageId(packageId);
        return signPackage;
    }
}
