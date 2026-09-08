package com.erp.inventory.service.impl;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import com.erp.common.core.web.domain.AjaxResult;
import com.erp.inventory.controller.InvSalesController;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.PostMapping;

@DisplayName("销售控制器契约")
class InvSalesControllerContractTest
{
    @Test
    @DisplayName("旧销售直出库端点保留兼容但必须阻断业务执行")
    void shouldBlockLegacyDirectSalesDeliveryEndpoint() throws Exception
    {
        Method deliverMethod = InvSalesController.class.getDeclaredMethod(
                "deliver", Long.class, jakarta.servlet.http.HttpServletRequest.class);

        PostMapping mapping = deliverMethod.getAnnotation(PostMapping.class);
        assertThat(mapping).isNotNull();
        assertThat(mapping.value()).contains("/deliver/{orderId}");

        InvSalesController controller = new InvSalesController();
        AjaxResult result = controller.deliver(1001L, null);

        assertThat(result.get(AjaxResult.CODE_TAG)).isEqualTo(500);
        assertThat(String.valueOf(result.get(AjaxResult.MSG_TAG))).contains("请通过发货通知执行发货");
    }
}
