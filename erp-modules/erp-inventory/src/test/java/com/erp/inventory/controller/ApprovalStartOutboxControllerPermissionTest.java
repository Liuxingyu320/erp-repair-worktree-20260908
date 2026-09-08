package com.erp.inventory.controller;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import com.erp.common.security.annotation.RequiresPermissions;
import com.erp.inventory.domain.dto.InvStockCheckApprovalStartReplayRequest;
import com.erp.inventory.domain.dto.InvTransferApprovalStartReplayRequest;
import com.erp.inventory.domain.vo.InvStockCheckApprovalStartOutboxQuery;
import com.erp.inventory.domain.vo.InvTransferApprovalStartOutboxQuery;

@DisplayName("库存审批发起发件箱运维权限")
class ApprovalStartOutboxControllerPermissionTest
{
    @Test
    void transferEndpointsUseDedicatedLeastPrivilegePermissions()
            throws Exception
    {
        assertPermission(InvTransferApprovalStartOutboxController.class,
                "list", "inv:transfer:approvalStartOutbox:list",
                InvTransferApprovalStartOutboxQuery.class);
        assertPermission(InvTransferApprovalStartOutboxController.class,
                "summary", "inv:transfer:approvalStartOutbox:list",
                InvTransferApprovalStartOutboxQuery.class);
        assertPermission(InvTransferApprovalStartOutboxController.class,
                "replay", "inv:transfer:approvalStartOutbox:replay",
                Long.class, InvTransferApprovalStartReplayRequest.class);
    }

    @Test
    void stockCheckEndpointsUseDedicatedLeastPrivilegePermissions()
            throws Exception
    {
        assertPermission(InvStockCheckApprovalStartOutboxController.class,
                "list", "inv:stockCheck:approvalStartOutbox:list",
                InvStockCheckApprovalStartOutboxQuery.class);
        assertPermission(InvStockCheckApprovalStartOutboxController.class,
                "summary", "inv:stockCheck:approvalStartOutbox:list",
                InvStockCheckApprovalStartOutboxQuery.class);
        assertPermission(InvStockCheckApprovalStartOutboxController.class,
                "replay", "inv:stockCheck:approvalStartOutbox:replay",
                Long.class, InvStockCheckApprovalStartReplayRequest.class);
    }

    private static void assertPermission(Class<?> controller,
            String methodName, String expected, Class<?>... parameters)
            throws Exception
    {
        Method method = controller.getMethod(methodName, parameters);
        RequiresPermissions annotation = method.getAnnotation(
                RequiresPermissions.class);
        assertThat(annotation).as(method.toString()).isNotNull();
        assertThat(annotation.value()).containsExactly(expected);
    }
}
