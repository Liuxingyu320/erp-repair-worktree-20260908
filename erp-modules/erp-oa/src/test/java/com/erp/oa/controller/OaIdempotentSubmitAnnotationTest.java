package com.erp.oa.controller;

import static org.assertj.core.api.Assertions.assertThat;

import com.erp.common.security.annotation.IdempotentSubmit;
import com.erp.common.security.annotation.RequiresLogin;
import com.erp.common.security.annotation.RequiresPermissions;
import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;

class OaIdempotentSubmitAnnotationTest
{
    @Test
    void purchaseWriteEndpointsShouldBeIdempotent() throws Exception
    {
        assertIdempotent(OaPurchaseController.class, "save", com.erp.oa.domain.OaPurchase.class,
                jakarta.servlet.http.HttpServletRequest.class);
        assertIdempotent(OaPurchaseController.class, "submit", com.erp.oa.domain.OaPurchase.class,
                jakarta.servlet.http.HttpServletRequest.class);
        assertIdempotent(OaPurchaseController.class, "close", Long.class,
                jakarta.servlet.http.HttpServletRequest.class);
        assertIdempotent(OaPurchaseController.class, "withdraw", Long.class,
                com.erp.oa.domain.dto.OaPurchaseWithdrawRequest.class,
                jakarta.servlet.http.HttpServletRequest.class);
    }

    @Test
    void salaryWriteEndpointsShouldBeIdempotent() throws Exception
    {
        assertIdempotent(OaSalaryController.class, "saveConfig", com.erp.oa.domain.OaSalaryConfig.class,
                jakarta.servlet.http.HttpServletRequest.class);
        assertIdempotent(OaSalaryController.class, "calculate", com.erp.oa.domain.OaSalaryRecord.class,
                jakarta.servlet.http.HttpServletRequest.class);
    }

    @Test
    void personalSalaryListShouldRequireLoginOnly() throws Exception
    {
        assertRequiresLogin(OaSalaryController.class, "myList", com.erp.oa.domain.OaSalaryRecord.class,
                jakarta.servlet.http.HttpServletRequest.class);
    }

    @Test
    void managementSalaryListShouldRequireListPermission() throws Exception
    {
        assertRequiresPermission("oa:salary:list", OaSalaryController.class, "list",
                com.erp.oa.domain.OaSalaryRecord.class, jakarta.servlet.http.HttpServletRequest.class);
    }

    private static void assertIdempotent(Class<?> controllerClass, String methodName, Class<?>... parameterTypes)
            throws Exception
    {
        Method method = controllerClass.getMethod(methodName, parameterTypes);
        IdempotentSubmit annotation = method.getAnnotation(IdempotentSubmit.class);
        assertThat(annotation).isNotNull();
        assertThat(annotation.timeout()).isEqualTo(30);
    }

    private static void assertRequiresLogin(Class<?> controllerClass, String methodName, Class<?>... parameterTypes)
            throws Exception
    {
        Method method = controllerClass.getMethod(methodName, parameterTypes);
        assertThat(method.getAnnotation(RequiresLogin.class)).isNotNull();
        assertThat(method.getAnnotation(RequiresPermissions.class)).isNull();
    }

    private static void assertRequiresPermission(String permission, Class<?> controllerClass, String methodName,
            Class<?>... parameterTypes) throws Exception
    {
        Method method = controllerClass.getMethod(methodName, parameterTypes);
        RequiresPermissions annotation = method.getAnnotation(RequiresPermissions.class);
        assertThat(annotation).isNotNull();
        assertThat(annotation.value()).containsExactly(permission);
    }
}
