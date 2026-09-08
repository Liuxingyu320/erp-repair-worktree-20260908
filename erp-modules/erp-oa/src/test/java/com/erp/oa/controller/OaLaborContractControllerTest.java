package com.erp.oa.controller;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import com.erp.common.security.annotation.IdempotentSubmit;
import com.erp.common.security.annotation.RequiresLogin;
import com.erp.oa.domain.OaCompanySealConfig;
import com.erp.oa.domain.OaLaborContract;
import com.erp.oa.domain.OaLaborContractTemplate;
import com.erp.oa.domain.dto.OaLaborContractSignRequest;

@DisplayName("OA劳动合同Controller")
class OaLaborContractControllerTest
{
    @Test
    @DisplayName("写接口必须启用后端幂等保护")
    void writeEndpointsShouldRequireIdempotentSubmit() throws NoSuchMethodException
    {
        assertIdempotent("saveTemplate", OaLaborContractTemplate.class);
        assertIdempotent("saveSeal", OaCompanySealConfig.class);
        assertIdempotent("save", OaLaborContract.class, HttpServletRequest.class);
        assertIdempotent("send", OaLaborContract.class, HttpServletRequest.class);
        assertIdempotent("voidContract", Long.class, HttpServletRequest.class);
        assertIdempotent("mobileSign", Long.class, OaLaborContractSignRequest.class, HttpServletRequest.class);
    }

    @Test
    @DisplayName("合同文件下载接口必须要求登录态")
    void downloadEndpointShouldRequireLogin() throws NoSuchMethodException
    {
        Method method = OaLaborContractController.class.getMethod("download", Long.class, String.class,
                HttpServletRequest.class, HttpServletResponse.class);

        assertThat(method.getAnnotation(RequiresLogin.class)).isNotNull();
    }

    @Test
    @DisplayName("企业章查询必须清理分页上下文避免单条查询被 PageHelper 污染")
    void sealEndpointShouldClearPageContextBeforeSingleRowLookup() throws Exception
    {
        String source = Files.readString(Path.of("src/main/java/com/erp/oa/controller/OaLaborContractController.java"),
                StandardCharsets.UTF_8);
        int clearPageIndex = source.indexOf("PageUtils.clearPage()");
        int lookupIndex = source.indexOf("laborContractService.getActiveSealConfig()");

        assertThat(clearPageIndex).isGreaterThanOrEqualTo(0);
        assertThat(lookupIndex).isGreaterThan(clearPageIndex);
    }

    private void assertIdempotent(String methodName, Class<?>... parameterTypes) throws NoSuchMethodException
    {
        Method method = OaLaborContractController.class.getMethod(methodName, parameterTypes);
        IdempotentSubmit annotation = method.getAnnotation(IdempotentSubmit.class);

        assertThat(annotation)
                .as(methodName + " should reject duplicate submissions")
                .isNotNull();
        assertThat(annotation.timeout())
                .as(methodName + " should use the same duplicate-submit window as inventory writes")
                .isEqualTo(30L);
    }
}
