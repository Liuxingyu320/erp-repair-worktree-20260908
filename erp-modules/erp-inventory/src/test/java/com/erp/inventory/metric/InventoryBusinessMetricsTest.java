package com.erp.inventory.metric;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

class InventoryBusinessMetricsTest
{
    @Test
    void allBusinessTagsAreBounded()
    {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        InventoryBusinessMetrics metrics = new InventoryBusinessMetrics(
                registry);

        metrics.recordTransferDiscrepancy("RESHIP");
        metrics.recordCustomerCardWrite("create_success");
        metrics.recordCustomerCardWrite("optimistic_conflict");
        metrics.recordCustomerCardScopeDenied();
        metrics.recordLegacyCustomerApi("secret-customer-name");

        assertThat(registry.get("erp.inventory.transfer_discrepancy.total")
                .tag("outcome", "reship").counter().count()).isEqualTo(1D);
        assertThat(registry.get("erp.inventory.customer_card.write.total")
                .tag("outcome", "create_success").counter().count())
                .isEqualTo(1D);
        assertThat(metrics.customerOpsSummary())
                .containsEntry("scopeDeniedCount", 1L)
                .containsEntry("optimisticConflictCount", 1L)
                .containsEntry("legacyApiCallCount", 1L);
        assertThat(registry.get("erp.inventory.customer_card.scope_denied.total")
                .counter().count()).isEqualTo(1D);
        assertThat(registry.get("erp.inventory.customer.legacy_api.total")
                .tag("operation", "other").counter().count()).isEqualTo(1D);
        assertThat(registry.getMeters()).allMatch(meter -> meter.getId()
                .getTags().stream().noneMatch(tag -> tag.getValue()
                        .contains("secret")));
    }
}
