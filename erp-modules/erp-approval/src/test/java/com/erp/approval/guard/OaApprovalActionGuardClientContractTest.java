package com.erp.approval.guard;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import com.erp.approval.api.constant.ApprovalApiPaths;
import com.erp.approval.api.domain.ApprovalBusinessActionValidationRequest;
import com.erp.approval.guard.client.OaApprovalActionGuardClient;
import com.erp.common.core.constant.SecurityConstants;
import com.erp.common.core.constant.ServiceNameConstants;

@DisplayName("OA 审批动作校验 Feign 契约")
class OaApprovalActionGuardClientContractTest
{
    @Test
    @DisplayName("Feign路径与内部来源头保持固定")
    void shouldUseSharedInnerPathAndSourceHeader() throws Exception
    {
        FeignClient client = OaApprovalActionGuardClient.class
                .getAnnotation(FeignClient.class);
        assertThat(client.value()).isEqualTo(ServiceNameConstants.OA_SERVICE);

        Method method = OaApprovalActionGuardClient.class.getMethod(
                "validateAction",
                ApprovalBusinessActionValidationRequest.class,
                String.class);
        assertThat(method.getAnnotation(PostMapping.class).value())
                .containsExactly(ApprovalApiPaths.VALIDATE_ACTION_FULL);
        Parameter source = method.getParameters()[1];
        assertThat(source.getAnnotation(RequestHeader.class).value())
                .isEqualTo(SecurityConstants.FROM_SOURCE);
    }
}
