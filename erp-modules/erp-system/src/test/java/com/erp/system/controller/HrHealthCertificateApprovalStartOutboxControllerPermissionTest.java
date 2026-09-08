package com.erp.system.controller;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import com.erp.common.security.annotation.RequiresPermissions;
import com.erp.system.domain.dto.HrHealthCertificateApprovalStartReplayRequest;
import com.erp.system.domain.vo.HrHealthCertificateApprovalStartOutboxQuery;

@DisplayName("健康证审批发起发件箱运维权限")
class HrHealthCertificateApprovalStartOutboxControllerPermissionTest
{
    @Test
    void endpointsUseDedicatedLeastPrivilegePermissions() throws Exception
    {
        assertPermission("list",
                "hr:healthCertificate:approvalStartOutbox:list",
                HrHealthCertificateApprovalStartOutboxQuery.class);
        assertPermission("summary",
                "hr:healthCertificate:approvalStartOutbox:list",
                HrHealthCertificateApprovalStartOutboxQuery.class);
        assertPermission("replay",
                "hr:healthCertificate:approvalStartOutbox:replay",
                Long.class,
                HrHealthCertificateApprovalStartReplayRequest.class);
    }

    private static void assertPermission(String methodName, String expected,
            Class<?>... parameters) throws Exception
    {
        Method method = HrHealthCertificateApprovalStartOutboxController.class
                .getMethod(methodName, parameters);
        RequiresPermissions annotation = method.getAnnotation(
                RequiresPermissions.class);
        assertThat(annotation).as(method.toString()).isNotNull();
        assertThat(annotation.value()).containsExactly(expected);
    }
}
