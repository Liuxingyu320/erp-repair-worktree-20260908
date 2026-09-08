package com.erp.oa.controller;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import com.erp.approval.api.constant.ApprovalApiPaths;
import com.erp.approval.api.domain.ApprovalBusinessActionValidationRequest;
import com.erp.common.core.domain.R;
import com.erp.common.security.annotation.InnerAuth;

@DisplayName("OA 审批内部契约")
class OaApprovalCallbackControllerContractTest
{
    @Test
    @DisplayName("审批动作校验使用共享路径并强制InnerAuth")
    void shouldExposeInnerAuthenticatedActionValidationRoute()
            throws Exception
    {
        Class<OaApprovalCallbackController> type =
                OaApprovalCallbackController.class;
        assertThat(type.getAnnotation(RequestMapping.class).value())
                .containsExactly(ApprovalApiPaths.INNER_BASE);

        Method method = type.getMethod("validateAction",
                ApprovalBusinessActionValidationRequest.class);
        assertThat(method.getAnnotation(PostMapping.class).value())
                .containsExactly(ApprovalApiPaths.VALIDATE_ACTION);
        assertThat(method.getAnnotation(InnerAuth.class)).isNotNull();
        assertThat(method.getReturnType()).isEqualTo(R.class);
    }
}
