package com.erp.inventory.controller;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import com.erp.common.log.annotation.Log;
import com.erp.common.log.enums.BusinessType;
import com.erp.common.security.annotation.IdempotentSubmit;
import com.erp.inventory.annotation.PersistentCommand;

@DisplayName("inventory 控制器幂等边界")
class InvControllerIdempotentBoundaryTest
{
    private static final List<Class<?>> INVENTORY_CONTROLLERS = List.of(
            InvStockCheckController.class,
            InvTransferApprovalRuleController.class,
            InvProductCategoryController.class,
            InvProductController.class,
            InvDeliveryNoticeController.class,
            InvStockController.class,
            InvSalesController.class,
            InvPurchaseReturnController.class,
            InvCustomerController.class,
            InvSalesReturnController.class,
            InvSupplierController.class,
            InvPurchaseController.class,
            InvTransferController.class,
            InvMobileController.class,
            InvReportController.class);

    private static final Set<BusinessType> HIGH_RISK_BUSINESS_TYPES = EnumSet.of(
            BusinessType.INSERT, BusinessType.UPDATE, BusinessType.DELETE, BusinessType.IMPORT);

    private static final Set<String> HIGH_RISK_METHOD_NAMES = Set.of(
            "add", "edit", "remove", "save", "submit", "create", "input", "confirm", "cancel", "delete",
            "receive", "receiveShipment", "deliver", "approve", "qualityCheck", "adjust", "importData");

    @Test
    @DisplayName("高风险库存写接口必须启用后端幂等保护")
    void highRiskInventoryWritesShouldRequireBackendIdempotency()
    {
        List<String> missingMethods = INVENTORY_CONTROLLERS.stream()
                .flatMap(controller -> Arrays.stream(controller.getDeclaredMethods()))
                .filter(InvControllerIdempotentBoundaryTest::isHighRiskWriteEndpoint)
                .filter(method -> method.getAnnotation(IdempotentSubmit.class) == null
                        && method.getAnnotation(PersistentCommand.class) == null)
                .map(InvControllerIdempotentBoundaryTest::formatMethod)
                .sorted()
                .toList();

        assertThat(missingMethods)
                .as("high-risk inventory write endpoints should require backend idempotency")
                .isEmpty();
    }

    private static boolean isHighRiskWriteEndpoint(Method method)
    {
        if (!hasWriteMapping(method) || isKnownNonMutatingEndpoint(method))
        {
            return false;
        }
        Log log = method.getAnnotation(Log.class);
        return method.getAnnotation(DeleteMapping.class) != null
                || HIGH_RISK_METHOD_NAMES.contains(method.getName())
                || (log != null && HIGH_RISK_BUSINESS_TYPES.contains(log.businessType()));
    }

    private static boolean hasWriteMapping(Method method)
    {
        return method.getAnnotation(PostMapping.class) != null
                || method.getAnnotation(PutMapping.class) != null
                || method.getAnnotation(DeleteMapping.class) != null;
    }

    private static boolean isKnownNonMutatingEndpoint(Method method)
    {
        return method.getDeclaringClass().equals(InvSalesController.class) && method.getName().equals("deliver");
    }

    private static String formatMethod(Method method)
    {
        return method.getDeclaringClass().getSimpleName() + "." + method.getName();
    }
}
