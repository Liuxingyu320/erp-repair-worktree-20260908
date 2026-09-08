package com.erp.common.log.annotation;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("操作日志安全默认值")
class LogDefaultPolicyTest
{
    @Test
    @DisplayName("新端点默认不保存请求和响应正文")
    void payloadPersistenceShouldBeDisabledByDefault() throws Exception
    {
        assertThat(Log.class.getMethod("isSaveRequestData").getDefaultValue()).isEqualTo(false);
        assertThat(Log.class.getMethod("isSaveResponseData").getDefaultValue()).isEqualTo(false);
        assertThat((String[]) Log.class.getMethod("includeParamNames").getDefaultValue()).isEmpty();
    }
}
