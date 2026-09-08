package com.erp.system.metric;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

class HrBusinessMetricsTest
{
    @Test
    void reminderOutcomeIsBounded()
    {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        HrBusinessMetrics metrics = new HrBusinessMetrics(registry);

        metrics.recordHealthCertificateReminder("success");
        metrics.recordHealthCertificateReminder("user-input-secret");

        assertThat(registry.get("erp.hr.health_certificate.reminder.total")
                .tag("outcome", "success").counter().count()).isEqualTo(1D);
        assertThat(registry.get("erp.hr.health_certificate.reminder.total")
                .tag("outcome", "failure").counter().count()).isEqualTo(1D);
        assertThat(metrics.getReminderFailureCount()).isEqualTo(1L);
        assertThat(registry.getMeters()).allMatch(meter -> meter.getId()
                .getTags().stream().noneMatch(tag -> tag.getValue()
                        .contains("secret")));
    }
}
