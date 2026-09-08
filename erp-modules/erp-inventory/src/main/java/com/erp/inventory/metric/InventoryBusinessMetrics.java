package com.erp.inventory.metric;

import java.util.Locale;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.stereotype.Component;
import io.micrometer.core.instrument.MeterRegistry;

/** Low-cardinality metrics for the 20260713 inventory business flows. */
@Component
public class InventoryBusinessMetrics
{
    private static final Set<String> DISCREPANCY_OUTCOMES = Set.of(
            "created", "reship", "return_source", "accept_actual",
            "pending_qc", "write_off", "other");
    private static final Set<String> CUSTOMER_WRITE_OUTCOMES = Set.of(
            "create_success", "update_success", "record_success",
            "archive_success", "idempotent_replay",
            "optimistic_conflict", "other");
    private static final Set<String> LEGACY_OPERATIONS = Set.of(
            "list", "query", "create", "update", "delete", "export",
            "other");

    private final MeterRegistry registry;
    private final AtomicLong customerScopeDeniedCount = new AtomicLong();
    private final AtomicLong customerOptimisticConflictCount = new AtomicLong();
    private final AtomicLong legacyCustomerApiCount = new AtomicLong();

    public InventoryBusinessMetrics(MeterRegistry registry)
    {
        this.registry = registry;
    }

    public void recordTransferDiscrepancy(String outcome)
    {
        increment("erp.inventory.transfer_discrepancy.total", "outcome",
                bounded(outcome, DISCREPANCY_OUTCOMES, "other"));
    }

    public void recordCustomerCardWrite(String outcome)
    {
        String normalized = bounded(outcome, CUSTOMER_WRITE_OUTCOMES,
                "other");
        if ("optimistic_conflict".equals(normalized))
        {
            customerOptimisticConflictCount.incrementAndGet();
        }
        increment("erp.inventory.customer_card.write.total", "outcome",
                normalized);
    }

    public void recordCustomerCardScopeDenied()
    {
        customerScopeDeniedCount.incrementAndGet();
        try
        {
            registry.counter("erp.inventory.customer_card.scope_denied.total")
                    .increment();
        }
        catch (RuntimeException ignored)
        {
            // Telemetry must never alter access control.
        }
    }

    public void recordLegacyCustomerApi(String operation)
    {
        legacyCustomerApiCount.incrementAndGet();
        increment("erp.inventory.customer.legacy_api.total", "operation",
                bounded(operation, LEGACY_OPERATIONS, "other"));
    }

    public Map<String, Long> customerOpsSummary()
    {
        Map<String, Long> values = new LinkedHashMap<>();
        values.put("scopeDeniedCount", customerScopeDeniedCount.get());
        values.put("optimisticConflictCount",
                customerOptimisticConflictCount.get());
        values.put("legacyApiCallCount", legacyCustomerApiCount.get());
        return values;
    }

    private void increment(String name, String tag, String value)
    {
        try
        {
            registry.counter(name, tag, value).increment();
        }
        catch (RuntimeException ignored)
        {
            // Metrics backends are best-effort.
        }
    }

    private static String bounded(String value, Set<String> allowed,
            String fallback)
    {
        String normalized = value == null ? ""
                : value.trim().toLowerCase(Locale.ROOT);
        return allowed.contains(normalized) ? normalized : fallback;
    }
}
