package com.erp.common.security.handler;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import com.erp.common.core.constant.HttpStatus;
import com.erp.common.core.exception.auth.PasswordChangeRequiredException;
import com.erp.common.core.web.domain.AjaxResult;

class GlobalExceptionHandlerPasswordChangeTest
{
    @Test
    void returnsExplicitPreconditionRequiredResponse()
    {
        AjaxResult result = new GlobalExceptionHandler()
                .handlePasswordChangeRequiredException(new PasswordChangeRequiredException());

        assertThat(result.get(AjaxResult.CODE_TAG)).isEqualTo(HttpStatus.PRECONDITION_REQUIRED);
        assertThat(result.get(AjaxResult.MSG_TAG)).isEqualTo("当前账号必须先修改密码");
    }
}
