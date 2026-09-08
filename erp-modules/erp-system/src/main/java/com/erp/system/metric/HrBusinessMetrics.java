package com.erp.system.metric;

import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.stereotype.Component;
import io.micrometer.core.instrument.MeterRegistry;

/** Low-cardinality metrics for the new HR business flows. */
@Component
public class HrBusinessMetrics
{
    private static final Set<String> REMINDER_OUTCOMES = Set.of(
            "success", "failure");
    private final MeterRegistry registry;
    private final AtomicLong reminderFailureCount = new AtomicLong();

    public HrBusinessMetrics(MeterRegistry registry)
    {
        this.registry = registry;
    }

    public void recordHealthCertificateReminder(String outcome)
    {
        String normalized = bounded(outcome, REMINDER_OUTCOMES);
        if ("failure".equals(normalized))
        {
            reminderFailureCount.incrementAndGet();
        }
        try
        {
            registry.counter("erp.hr.health_certificate.reminder.total",
                    "outcome", normalized)
                    .increment();
        }
        catch (RuntimeException ignored)
        {
            // Telemetry must never change reminder delivery.
        }
    }

    public long getReminderFailureCount()
    {
        return reminderFailureCount.get();
    }

    private static String bounded(String value, Set<String> allowed)
    {
        return value != null && allowed.contains(value) ? value : "failure";
    }
}
