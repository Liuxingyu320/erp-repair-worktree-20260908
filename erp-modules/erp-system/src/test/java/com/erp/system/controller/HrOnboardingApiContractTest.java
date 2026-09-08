package com.erp.system.controller;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import com.erp.common.log.annotation.Log;
import com.erp.common.log.enums.BusinessType;
import com.erp.common.security.annotation.RequiresPermissions;
import com.erp.system.domain.vo.HrOnboardingListVo;
import com.erp.system.domain.vo.HrOnboardingSummaryVo;
import com.erp.system.service.impl.HrOnboardingRuleService;

@DisplayName("HR 入职共享 API 契约")
class HrOnboardingApiContractTest
{
    private static final Set<String> ACTIONS = Set.of(
            "EDIT", "MARK_READY", "RETURN_TO_DRAFT", "CONFIRM", "CANCEL", "RESTORE");

    @Test
    @DisplayName("入职、配置和导入端点必须使用独立权限")
    void endpointsShouldExposeDedicatedPermissions() throws Exception
    {
        String onboarding = source("controller/HrOnboardingController.java");
        assertThat(onboarding)
                .contains("@RequestMapping(\"/hr/onboarding\")")
                .contains("@GetMapping(\"/list\")", "@RequiresPermissions(\"hr:onboarding:list\")")
                .contains("@GetMapping(\"/summary\")", "@RequiresPermissions(\"hr:onboarding:workbench\")")
                .contains("@GetMapping(\"/form-options\")")
                .contains("hr:onboarding:add", "hr:onboarding:edit")
                .contains("@PostMapping", "@GetMapping(\"/{id}\")", "@PutMapping(\"/{id}\")")
                .contains("hr:onboarding:query", "hr:onboarding:ready", "hr:onboarding:return")
                .contains("hr:onboarding:cancel", "hr:onboarding:restore")
                .contains("@GetMapping(\"/{id}/conflicts\")", "@PostMapping(\"/{id}/confirm\")")
                .contains("hr:onboarding:confirm");

        String config = source("controller/HrOnboardingPositionConfigController.java");
        assertThat(config).contains("@RequestMapping(\"/hr/onboarding/config\")")
                .contains("@GetMapping(\"/list\")", "@GetMapping(\"/{id}\")", "@PostMapping")
                .contains("@PutMapping(\"/{id}\")", "@PostMapping(\"/{id}/disable\")")
                .contains("@GetMapping(\"/options\")", "hr:onboarding:config");

        RequestMapping importRoot = AnnotatedElementUtils.findMergedAnnotation(
                HrOnboardingImportController.class, RequestMapping.class);
        assertThat(importRoot).as("HrOnboardingImportController class mapping").isNotNull();
        assertThat(importRoot.value()).containsExactly("/hr/onboarding/import");
        assertEndpoint(HrOnboardingImportController.class, "preview", PostMapping.class, "/preview",
                "hr:onboarding:import:preview");
        assertEndpoint(HrOnboardingImportController.class, "get", GetMapping.class, "/{batchId}",
                "hr:onboarding:import:preview");
        assertEndpoint(HrOnboardingImportController.class, "confirm", PostMapping.class, "/{batchId}/confirm",
                "hr:onboarding:import:confirm");
        assertEndpoint(HrOnboardingImportController.class, "template", GetMapping.class, "/template",
                "hr:onboarding:import:template");
        assertEndpoint(HrOnboardingImportController.class, "errors", GetMapping.class, "/{batchId}/errors",
                "hr:onboarding:import:preview");
    }

    @Test
    @DisplayName("PII 写操作日志不得保存请求或响应")
    void piiWriteLogsShouldDisableRequestAndResponseCapture() throws Exception
    {
        assertPiiEndpoint(HrOnboardingController.class, "create", PostMapping.class, "",
                "hr:onboarding:add", BusinessType.INSERT);
        assertPiiEndpoint(HrOnboardingController.class, "update", PutMapping.class, "/{id}",
                "hr:onboarding:edit", BusinessType.UPDATE);
        assertPiiEndpoint(HrOnboardingController.class, "confirm", PostMapping.class, "/{id}/confirm",
                "hr:onboarding:confirm", BusinessType.OTHER);
        assertPiiEndpoint(HrOnboardingImportController.class, "preview", PostMapping.class, "/preview",
                "hr:onboarding:import:preview", BusinessType.IMPORT);
        assertPiiEndpoint(HrOnboardingImportController.class, "confirm", PostMapping.class, "/{batchId}/confirm",
                "hr:onboarding:import:confirm", BusinessType.IMPORT);
        assertPiiEndpoint(HrOnboardingImportController.class, "template", GetMapping.class, "/template",
                "hr:onboarding:import:template", BusinessType.EXPORT);
        assertPiiEndpoint(HrOnboardingImportController.class, "errors", GetMapping.class, "/{batchId}/errors",
                "hr:onboarding:import:preview", BusinessType.EXPORT);
        assertPiiEndpoint(HrEmployeeProfileController.class, "edit", PatchMapping.class, "/{userId}",
                "hr:employee:edit", BusinessType.UPDATE);
        assertPiiEndpoint(HrEmployeeProfileController.class, "editLegacy", PutMapping.class, "/{userId}",
                "hr:employee:edit", BusinessType.UPDATE);
        assertPiiEndpoint(HrEmployeeProfileController.class, "editLegacyRoot", PutMapping.class, "",
                "hr:employee:edit", BusinessType.UPDATE);
    }

    @Test
    @DisplayName("校验失败必须提供移动端和桌面端共享错误结构")
    void validationEnvelopeShouldExposeStableFields() throws Exception
    {
        assertThat(source("controller/HrOnboardingExceptionHandler.java"))
                .contains("HrOnboardingValidationException.class")
                .contains("\"errorCode\"").contains("getErrorCode()")
                .contains("\"fieldErrors\"").contains("getFieldErrors()")
                .contains("\"blockingCodes\"").contains("getBlockingCodes()");
    }

    @Test
    @DisplayName("工作台汇总和任务只包含安全字段")
    void summaryAndTodayTasksShouldBeMasked() throws Exception
    {
        Set<String> summary = fields(HrOnboardingSummaryVo.class);
        assertThat(summary).containsExactlyInAnyOrder(
                "todayArrivalCount", "pendingConfirmCount", "accountConfigurationRiskCount", "todayTasks");

        Set<String> list = fields(HrOnboardingListVo.class);
        assertThat(list).contains("phoneNumberMasked", "allowedActions", "currentAction")
                .doesNotContain("phoneNumber", "idNumber", "bankAccount", "registeredResidence", "currentAddress");
        assertThat(mainResource("mapper/system/HrOnboardingMapper.xml"))
                .contains("phone_number_masked")
                .doesNotContain("task.phone_number,");
    }

    @Test
    @DisplayName("允许动作只能使用六个固定值")
    void allowedActionsShouldUseOnlyFixedValues() throws Exception
    {
        Set<String> declared = Stream.of(HrOnboardingRuleService.class.getDeclaredFields())
                .filter(field -> Modifier.isStatic(field.getModifiers()) && Modifier.isPublic(field.getModifiers()))
                .filter(field -> field.getName().startsWith("ACTION_"))
                .map(this::staticString).collect(Collectors.toSet());
        assertThat(declared).containsExactlyInAnyOrderElementsOf(ACTIONS).hasSize(6);
    }

    private String staticString(Field field)
    {
        try { return (String) field.get(null); }
        catch (IllegalAccessException failure) { throw new AssertionError(failure); }
    }

    private void assertPiiEndpoint(Class<?> controller, String methodName, Class<? extends Annotation> mappingType,
            String path, String permission, BusinessType businessType)
    {
        Method method = assertEndpoint(controller, methodName, mappingType, path, permission);
        Log log = method.getAnnotation(Log.class);
        assertThat(log).as("%s.%s @Log", controller.getSimpleName(), methodName).isNotNull();
        assertThat(log.businessType()).isEqualTo(businessType);
        assertThat(log.isSaveRequestData()).isFalse();
        assertThat(log.isSaveResponseData()).isFalse();
    }

    private Method assertEndpoint(Class<?> controller, String methodName, Class<? extends Annotation> mappingType,
            String path, String permission)
    {
        Method method = Stream.of(controller.getDeclaredMethods())
                .filter(candidate -> candidate.getName().equals(methodName)).findFirst().orElseThrow();
        Annotation mapping = AnnotatedElementUtils.findMergedAnnotation(method, mappingType);
        assertThat(mapping).as("%s.%s mapping", controller.getSimpleName(), methodName).isNotNull();
        if (path.isEmpty()) assertThat(mappingPaths(mapping)).isEmpty();
        else assertThat(mappingPaths(mapping)).containsExactly(path);
        RequiresPermissions required = method.getAnnotation(RequiresPermissions.class);
        assertThat(required).as("%s.%s permission", controller.getSimpleName(), methodName).isNotNull();
        assertThat(required.value()).containsExactly(permission);
        return method;
    }

    private String[] mappingPaths(Annotation mapping)
    {
        if (mapping instanceof GetMapping value) return value.value();
        if (mapping instanceof PostMapping value) return value.value();
        if (mapping instanceof PutMapping value) return value.value();
        if (mapping instanceof PatchMapping value) return value.value();
        throw new AssertionError("unsupported mapping " + mapping.annotationType());
    }

    private Set<String> fields(Class<?> type)
    {
        return Stream.of(type.getDeclaredFields()).filter(field -> !Modifier.isStatic(field.getModifiers()))
                .map(Field::getName).collect(Collectors.toSet());
    }

    private String source(String relative) throws Exception
    {
        Path root = Paths.get(System.getProperty("user.dir"));
        Path main = root.resolve("src/main/java/com/erp/system").resolve(relative).normalize();
        if (!Files.exists(main))
            main = root.resolve("erp-modules/erp-system/src/main/java/com/erp/system").resolve(relative).normalize();
        return Files.readString(main, StandardCharsets.UTF_8);
    }

    private String mainResource(String relative) throws Exception
    {
        Path root = Paths.get(System.getProperty("user.dir"));
        Path resource = root.resolve("src/main/resources").resolve(relative);
        if (!Files.exists(resource))
            resource = root.resolve("erp-modules/erp-system/src/main/resources").resolve(relative);
        return Files.readString(resource, StandardCharsets.UTF_8);
    }
}
