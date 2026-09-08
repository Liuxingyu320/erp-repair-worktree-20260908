package com.erp.system.controller;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("人事管理控制器源码契约")
class HrPersonnelControllerSourceTest
{
    @Test
    @DisplayName("人事页面菜单应有同权限后端接口")
    void hrMenuRoutesShouldHaveHrPermissionEndpoints() throws Exception
    {
        String employeeController = readSource("HrEmployeeProfileController.java");
        String onboardingController = readSource("HrOnboardingController.java");
        String completenessController = readSource("HrCompletenessController.java");

        assertThat(employeeController)
                .contains("@RequestMapping(\"/hr/employee\")")
                .contains("@RequiresPermissions(\"hr:employee:list\")")
                .contains("@RequiresPermissions(\"hr:employee:query\")")
                .contains("@RequiresPermissions(\"hr:employee:edit\")")
                .contains("@PutMapping")
                .contains("@RequiresPermissions(\"hr:employee:export\")")
                .contains("@RequiresPermissions(\"hr:import:confirm\")")
                .contains("@RequiresPermissions(\"hr:import:template\")");
        assertThat(onboardingController)
                .contains("@RequestMapping(\"/hr/onboarding\")")
                .contains("@RequiresPermissions(\"hr:onboarding:list\")");
        assertThat(completenessController)
                .contains("@RequestMapping(\"/hr/completeness\")")
                .contains("@GetMapping(\"/summary\")")
                .contains("@GetMapping(\"/employees\")")
                .contains("@GetMapping(\"/departments\")")
                .contains("@RequiresPermissions(\"hr:completeness:list\")");
    }

    @Test
    @DisplayName("独立入职接口不得委托通用用户列表")
    void onboardingControllersShouldNotDelegateToGenericUserList() throws Exception
    {
        assertThat(readSource("HrOnboardingController.java"))
                .doesNotContain("ISysUserService")
                .doesNotContain("selectUserList");
        assertThat(readSource("HrOnboardingImportController.java"))
                .doesNotContain("ISysUserService")
                .doesNotContain("selectUserList");
        assertThat(readSource("HrOnboardingPositionConfigController.java"))
                .doesNotContain("ISysUserService")
                .doesNotContain("selectUserList");
    }

    private String readSource(String fileName) throws Exception
    {
        Path root = Paths.get(System.getProperty("user.dir"));
        Path path = root.resolve("src/main/java/com/erp/system/controller").resolve(fileName);
        if (!Files.exists(path))
        {
            path = root.resolve("erp-modules/erp-system/src/main/java/com/erp/system/controller").resolve(fileName);
        }
        return Files.readString(path, StandardCharsets.UTF_8);
    }
}
