package com.erp.system.service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;

/**
 * Authoritative allow-list published to the rebuilt frontend.
 *
 * <p>Unknown frontend feature codes never originate here. A feature is
 * published only when its complete permission requirement and rollout gate
 * are satisfied.</p>
 */
@Service
public class FrontendFeatureCatalog
{
    private static final String ALL_PERMISSION = "*:*:*";

    private static final String WORKBENCH_PERMISSION = "workbench:view";

    private static final String TASKS_PERMISSION = "tasks:view";

    private final BusinessFeatureGate businessFeatureGate;

    public FrontendFeatureCatalog(BusinessFeatureGate businessFeatureGate)
    {
        this.businessFeatureGate = businessFeatureGate;
    }

    public Set<String> frontendPermissions(Set<String> backendPermissions)
    {
        Set<String> permissions = new LinkedHashSet<>();
        if (backendPermissions != null)
        {
            backendPermissions.stream()
                    .filter(value -> value != null && !value.isBlank())
                    .forEach(permissions::add);
        }
        permissions.add(WORKBENCH_PERMISSION);
        permissions.add(TASKS_PERMISSION);
        return Collections.unmodifiableSet(permissions);
    }

    public List<String> enabledFeatures(Set<String> backendPermissions)
    {
        Set<String> permissions = frontendPermissions(backendPermissions);
        List<String> features = new ArrayList<>();
        features.add("workbench.view");
        features.add("tasks.view");
        features.add("messages.view");
        addIfPermitted(features, permissions, "oa.purchase.view", "oa:purchase:list");
        addIfPermitted(features, permissions, "approval.definition.view",
                "approval:template:list");
        addIfPermitted(features, permissions, "approval.instance.view",
                "approval:instance:list");
        addIfPermitted(features, permissions, "approval.rule.view",
                "approval:template:list", "approval:template:query");
        addIfPermitted(features, permissions, "approval.validation.view",
                "approval:validation:list");
        if (businessFeatureGate.isEnabled(BusinessFeatureGate.CUSTOMER_SERVICE_CARD))
        {
            addIfPermitted(features, permissions, "inventory.customer-service-card.view",
                    "inv:customerCard:list");
        }
        addIfPermitted(features, permissions, "inventory.sales.view", "inv:sales:list");
        addIfPermitted(features, permissions, "inventory.sales-return.view",
                "inv:salesReturn:list");
        addIfPermitted(features, permissions, "inventory.delivery-notice.view",
                "inv:deliveryNotice:list");
        addIfPermitted(features, permissions, "inventory.purchase.view", "inv:purchase:list");
        addIfPermitted(features, permissions, "inventory.purchase.create", "inv:purchase:add");
        addIfPermitted(features, permissions, "inventory.purchase.receive", "inv:purchase:receive");
        addIfPermitted(features, permissions, "inventory.purchase.quality-check", "inv:purchase:qc");
        addIfPermitted(features, permissions, "inventory.purchase-return.view",
                "inv:purchaseReturn:list");
        addIfPermitted(features, permissions, "inventory.purchase-return.create",
                "inv:purchaseReturn:add");
        addIfPermitted(features, permissions, "inventory.category.view", "inv:category:list");
        addIfPermitted(features, permissions, "inventory.supplier.view", "inv:supplier:list");
        addIfPermitted(features, permissions, "inventory.product.view", "inv:product:list");
        addIfPermitted(features, permissions, "inventory.stock.view", "inv:stock:list");
        addIfPermitted(features, permissions, "inventory.stock-check.view",
                "inv:stockCheck:list");
        addIfPermitted(features, permissions, "inventory.report.view", "inv:report:list");
        addIfPermitted(features, permissions, "inventory.transfer.view",
                "inventory:transfer:list");
        addIfPermitted(features, permissions, "inventory.transfer.create",
                "inventory:transfer:create");
        addIfPermitted(features, permissions, "inventory.transfer.approve",
                "inventory:transfer:approve");
        addIfPermitted(features, permissions, "inventory.transfer.ship",
                "inventory:transfer:deliver");
        addIfPermitted(features, permissions, "inventory.transfer.receive",
                "inventory:transfer:receive");
        return Collections.unmodifiableList(features);
    }

    private void addIfPermitted(List<String> features, Set<String> permissions,
            String feature, String... requiredPermissions)
    {
        if (permissions.contains(ALL_PERMISSION)
                || List.of(requiredPermissions).stream().allMatch(permissions::contains))
        {
            features.add(feature);
        }
    }
}
