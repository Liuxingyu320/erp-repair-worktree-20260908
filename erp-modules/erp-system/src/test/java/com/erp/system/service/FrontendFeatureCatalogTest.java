package com.erp.system.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Set;
import org.junit.jupiter.api.Test;

class FrontendFeatureCatalogTest
{
    private final BusinessFeatureGate businessFeatureGate = mock(BusinessFeatureGate.class);

    private final FrontendFeatureCatalog catalog = new FrontendFeatureCatalog(businessFeatureGate);

    @Test
    void publishesOnlyKnownFeaturesWhoseFullPermissionBoundaryIsSatisfied()
    {
        when(businessFeatureGate.isEnabled(BusinessFeatureGate.CUSTOMER_SERVICE_CARD))
                .thenReturn(false);

        assertThat(catalog.enabledFeatures(Set.of(
                "approval:template:list",
                "inv:category:list",
                "inv:supplier:list",
                "inv:product:list",
                "inv:purchase:list",
                "inv:purchase:add",
                "inv:purchase:receive",
                "inv:purchase:qc",
                "inv:purchaseReturn:list",
                "inv:purchaseReturn:add",
                "inventory:transfer:list",
                "inventory:transfer:create",
                "unknown:permission")))
                .contains("workbench.view", "tasks.view", "messages.view",
                        "approval.definition.view", "inventory.transfer.view",
                        "inventory.transfer.create", "inventory.category.view",
                        "inventory.product.view", "inventory.supplier.view",
                        "inventory.purchase.view", "inventory.purchase.create",
                        "inventory.purchase.receive", "inventory.purchase.quality-check",
                        "inventory.purchase-return.view", "inventory.purchase-return.create")
                .doesNotContain("approval.rule.view",
                        "inventory.customer-service-card.view",
                        "inventory.transfer.approve",
                        "unknown.feature");
    }

    @Test
    void purchaseWorkflowFeaturesRequireTheirOwnExactPermissions()
    {
        assertThat(catalog.enabledFeatures(Set.of("inv:purchase:list")))
                .contains("inventory.purchase.view")
                .doesNotContain("inventory.purchase.create", "inventory.purchase.receive",
                        "inventory.purchase.quality-check");
        assertThat(catalog.enabledFeatures(Set.of("inv:purchase:add", "inv:purchase:receive",
                "inv:purchase:qc")))
                .contains("inventory.purchase.create", "inventory.purchase.receive",
                        "inventory.purchase.quality-check")
                .doesNotContain("inventory.purchase.view");
    }

    @Test
    void purchaseReturnFeaturesRequireTheirOwnExactPermissions()
    {
        assertThat(catalog.enabledFeatures(Set.of("inv:purchaseReturn:list")))
                .contains("inventory.purchase-return.view")
                .doesNotContain("inventory.purchase-return.create");
        assertThat(catalog.enabledFeatures(Set.of("inv:purchaseReturn:add")))
                .contains("inventory.purchase-return.create")
                .doesNotContain("inventory.purchase-return.view");
    }

    @Test
    void customerServiceFeatureRequiresBothRolloutGateAndPermission()
    {
        when(businessFeatureGate.isEnabled(BusinessFeatureGate.CUSTOMER_SERVICE_CARD))
                .thenReturn(true);

        assertThat(catalog.enabledFeatures(Set.of("inv:customerCard:list")))
                .contains("inventory.customer-service-card.view");
        assertThat(catalog.enabledFeatures(Set.of()))
                .doesNotContain("inventory.customer-service-card.view");
    }

    @Test
    void wildcardPermissionStillUsesTheExplicitKnownFeatureCatalog()
    {
        when(businessFeatureGate.isEnabled(BusinessFeatureGate.CUSTOMER_SERVICE_CARD))
                .thenReturn(true);

        assertThat(catalog.enabledFeatures(Set.of("*:*:*")))
                .contains("inventory.transfer.receive", "approval.rule.view")
                .doesNotContain("inventory.transfer.discrepancy-adjudication.prototype");
        assertThat(catalog.frontendPermissions(Set.of("*:*:*")))
                .contains("*:*:*", "workbench:view", "tasks:view");
    }
}
