package com.erp.system.controller;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;

import com.erp.common.core.web.domain.AjaxResult;
import com.erp.common.log.annotation.Log;
import com.erp.common.log.enums.BusinessType;
import com.erp.common.security.annotation.RequiresPermissions;
import com.erp.common.security.annotation.Logical;
import com.erp.system.domain.vo.HrOnboardingCancelRequest;
import com.erp.system.domain.vo.HrOnboardingCreateRequest;
import com.erp.system.domain.vo.HrOnboardingConfirmRequest;
import com.erp.system.domain.vo.HrOnboardingDetailVo;
import com.erp.system.domain.vo.HrOnboardingListVo;
import com.erp.system.domain.vo.HrOnboardingOwnerQuery;
import com.erp.system.domain.vo.HrOnboardingQuery;
import com.erp.system.domain.vo.HrOnboardingSummaryVo;
import com.erp.system.domain.vo.HrOnboardingUpdateRequest;
import com.erp.system.domain.vo.HrOnboardingVersionRequest;
import com.erp.system.exception.HrOnboardingValidationException;
import com.erp.system.service.IHrOnboardingService;

class HrOnboardingControllerTest
{
    @Test
    void endpointsUseDedicatedPermissionsAndExpectedMappings() throws Exception
    {
        assertEndpoint("list", new Class<?>[] { HrOnboardingQuery.class }, GetMapping.class,
                "/list", "hr:onboarding:list");
        assertEndpoint("summary", new Class<?>[] { HrOnboardingQuery.class }, GetMapping.class,
                "/summary", "hr:onboarding:workbench");
        assertEndpoint("create", new Class<?>[] { HrOnboardingCreateRequest.class }, PostMapping.class,
                "", "hr:onboarding:add");
        assertEndpoint("get", new Class<?>[] { Long.class }, GetMapping.class,
                "/{id}", "hr:onboarding:query");
        assertEndpoint("update", new Class<?>[] { Long.class, HrOnboardingUpdateRequest.class }, PutMapping.class,
                "/{id}", "hr:onboarding:edit");
        assertEndpoint("ready", new Class<?>[] { Long.class, HrOnboardingVersionRequest.class }, PostMapping.class,
                "/{id}/ready", "hr:onboarding:ready");
        assertEndpoint("returnToDraft", new Class<?>[] { Long.class, HrOnboardingVersionRequest.class }, PostMapping.class,
                "/{id}/return-to-draft", "hr:onboarding:return");
        assertEndpoint("cancel", new Class<?>[] { Long.class, HrOnboardingCancelRequest.class }, PostMapping.class,
                "/{id}/cancel", "hr:onboarding:cancel");
        assertEndpoint("restore", new Class<?>[] { Long.class, HrOnboardingVersionRequest.class }, PostMapping.class,
                "/{id}/restore", "hr:onboarding:restore");
        assertEndpoint("conflicts", new Class<?>[] { Long.class }, GetMapping.class,
                "/{id}/conflicts", "hr:onboarding:confirm");
        assertEndpoint("confirm", new Class<?>[] { Long.class, HrOnboardingConfirmRequest.class }, PostMapping.class,
                "/{id}/confirm", "hr:onboarding:confirm");

        Method formOptions = HrOnboardingController.class.getMethod(
                "formOptions", Boolean.class, Boolean.class);
        assertThat(formOptions.getAnnotation(GetMapping.class).value()).containsExactly("/form-options");
        RequiresPermissions formOptionPermission = formOptions.getAnnotation(RequiresPermissions.class);
        assertThat(formOptionPermission.value()).containsExactly(
                "hr:onboarding:list", "hr:onboarding:add", "hr:onboarding:edit");
        assertThat(formOptionPermission.logical()).isEqualTo(Logical.OR);

        Method ownerOptions = HrOnboardingController.class.getMethod(
                "ownerOptions", HrOnboardingOwnerQuery.class, Integer.class, Integer.class);
        assertThat(ownerOptions.getAnnotation(GetMapping.class).value()).containsExactly("/owner-options");
        RequiresPermissions ownerPermission = ownerOptions.getAnnotation(RequiresPermissions.class);
        assertThat(ownerPermission.value()).containsExactly(
                "hr:onboarding:list", "hr:onboarding:add", "hr:onboarding:edit");
        assertThat(ownerPermission.logical()).isEqualTo(Logical.OR);
    }

    @Test
    void readPermissionMatrixKeepsFiltersAvailableWithoutBroadeningSensitiveDetail() throws Exception
    {
        RequiresPermissions list = HrOnboardingController.class
                .getMethod("list", HrOnboardingQuery.class).getAnnotation(RequiresPermissions.class);
        RequiresPermissions options = HrOnboardingController.class
                .getMethod("formOptions", Boolean.class, Boolean.class)
                .getAnnotation(RequiresPermissions.class);
        RequiresPermissions detail = HrOnboardingController.class
                .getMethod("get", Long.class).getAnnotation(RequiresPermissions.class);

        assertThat(list.value()).containsExactly("hr:onboarding:list");
        assertThat(options.value()).containsExactly(
                "hr:onboarding:list", "hr:onboarding:add", "hr:onboarding:edit");
        assertThat(options.logical()).isEqualTo(Logical.OR);
        assertThat(detail.value()).containsExactly("hr:onboarding:query");
    }

    @Test
    void writeAuditAnnotationsNeverSaveRequestOrResponsePii() throws Exception
    {
        assertLog("create", new Class<?>[] { HrOnboardingCreateRequest.class }, BusinessType.INSERT);
        assertLog("update", new Class<?>[] { Long.class, HrOnboardingUpdateRequest.class }, BusinessType.UPDATE);
        assertLog("ready", new Class<?>[] { Long.class, HrOnboardingVersionRequest.class }, BusinessType.OTHER);
        assertLog("returnToDraft", new Class<?>[] { Long.class, HrOnboardingVersionRequest.class }, BusinessType.OTHER);
        assertLog("cancel", new Class<?>[] { Long.class, HrOnboardingCancelRequest.class }, BusinessType.OTHER);
        assertLog("restore", new Class<?>[] { Long.class, HrOnboardingVersionRequest.class }, BusinessType.OTHER);
        assertLog("confirm", new Class<?>[] { Long.class, HrOnboardingConfirmRequest.class }, BusinessType.OTHER);
    }

    @Test
    void nonListEndpointsUseAjaxDataEnvelopeAndSummaryIncludesTasks()
    {
        HrOnboardingDetailVo detail = new HrOnboardingDetailVo();
        HrOnboardingSummaryVo summary = new HrOnboardingSummaryVo();
        HrOnboardingListVo task = new HrOnboardingListVo();
        task.setPhoneNumberMasked("138****8000");
        summary.setTodayTasks(Collections.singletonList(task));
        HrOnboardingController controller = new HrOnboardingController();
        ReflectionTestUtils.setField(controller, "onboardingService", service(detail, summary));

        AjaxResult get = controller.get(1L);
        AjaxResult summaryResult = controller.summary(new HrOnboardingQuery());

        assertThat(get.get("data")).isSameAs(detail);
        assertThat(summaryResult.get("data")).isSameAs(summary);
        assertThat(((HrOnboardingSummaryVo) summaryResult.get("data")).getTodayTasks())
                .singleElement().extracting("phoneNumberMasked").isEqualTo("138****8000");
    }

    @Test
    void readEndpointsAlwaysPassNonNullQueriesToTheScopedService()
    {
        IHrOnboardingService onboardingService = org.mockito.Mockito.mock(IHrOnboardingService.class);
        org.mockito.Mockito.when(onboardingService.summary(org.mockito.ArgumentMatchers.any()))
                .thenReturn(new HrOnboardingSummaryVo());
        org.mockito.Mockito.when(onboardingService.list(org.mockito.ArgumentMatchers.any()))
                .thenReturn(Collections.emptyList());
        HrOnboardingController controller = new HrOnboardingController();
        ReflectionTestUtils.setField(controller, "onboardingService", onboardingService);

        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(new MockHttpServletRequest()));
        try
        {
            controller.list(null);
            controller.summary(null);

            org.mockito.ArgumentCaptor<HrOnboardingQuery> queries = org.mockito.ArgumentCaptor.forClass(HrOnboardingQuery.class);
            org.mockito.Mockito.verify(onboardingService).list(queries.capture());
            org.mockito.Mockito.verify(onboardingService).summary(queries.capture());
            assertThat(queries.getAllValues()).allSatisfy(query -> assertThat(query).isNotNull());
        }
        finally
        {
            RequestContextHolder.resetRequestAttributes();
        }
    }

    @Test
    void formOptionsKeepDesktopPeopleDefaultsWhileAllowingLeanMobileRequests()
    {
        IHrOnboardingService onboardingService = org.mockito.Mockito.mock(IHrOnboardingService.class);
        org.mockito.Mockito.when(onboardingService.formOptions(
                org.mockito.ArgumentMatchers.anyBoolean(),
                org.mockito.ArgumentMatchers.anyBoolean())).thenReturn(Collections.emptyMap());
        HrOnboardingController controller = new HrOnboardingController();
        ReflectionTestUtils.setField(controller, "onboardingService", onboardingService);

        controller.formOptions(null, null);
        controller.formOptions(false, true);

        org.mockito.InOrder order = org.mockito.Mockito.inOrder(onboardingService);
        order.verify(onboardingService).formOptions(true, true);
        order.verify(onboardingService).formOptions(false, true);
    }

    @Test
    void validationAdviceReturnsStableFieldsAtAjaxTopLevel()
    {
        Map<String, String> fieldErrors = new LinkedHashMap<>();
        fieldErrors.put("phoneNumber", "不能提交脱敏占位值");
        HrOnboardingValidationException failure = new HrOnboardingValidationException(
                "MASKED_VALUE_NOT_ACCEPTED", "请修正字段", fieldErrors,
                Arrays.asList("PHONE_MASKED_PLACEHOLDER"));

        AjaxResult result = new HrOnboardingExceptionHandler().handleValidation(failure);

        assertThat(result.get("code")).isEqualTo(500);
        assertThat(result.get("msg")).isEqualTo("请修正字段");
        assertThat(result.get("errorCode")).isEqualTo("MASKED_VALUE_NOT_ACCEPTED");
        assertThat(result.get("fieldErrors")).isEqualTo(fieldErrors);
        assertThat(result.get("blockingCodes")).isEqualTo(Arrays.asList("PHONE_MASKED_PLACEHOLDER"));
        assertThat(result).doesNotContainKey("data");
    }

    @Test
    void clientVosNeverExposeRawSensitiveProperties()
    {
        assertThat(propertyNames(HrOnboardingListVo.class)).doesNotContain(
                "phoneNumber", "idNumber", "bankAccount", "registeredResidence", "currentAddress");
        assertThat(propertyNames(HrOnboardingDetailVo.class)).doesNotContain(
                "phoneNumber", "idNumber", "bankAccount", "registeredResidence", "currentAddress");
    }

    @Test
    void detailSerializationExposesEditableSafeFieldsAndNeverRawSensitiveFields() throws Exception
    {
        HrOnboardingDetailVo detail = new HrOnboardingDetailVo();
        detail.setJobGrade("P3");
        detail.setEmergencyContact("家属");
        detail.setPhoneNumberMasked("138****8000");
        detail.setIdNumberMasked("3500**********0000");
        detail.setPreferredConflictAction("BIND_EXISTING");
        detail.setPreferredBindUserId(99L);

        String json = new ObjectMapper().writeValueAsString(detail);

        assertThat(json).contains("\"jobGrade\":\"P3\"", "\"emergencyContact\":\"家属\"",
                "\"phoneNumberMasked\":\"138****8000\"", "\"idNumberMasked\":\"3500**********0000\"",
                "\"preferredConflictAction\":\"BIND_EXISTING\"", "\"preferredBindUserId\":99");
        assertThat(json).doesNotContain("\"phoneNumber\":", "\"idNumber\":", "\"bankAccount\":",
                "\"registeredResidence\":", "\"currentAddress\":", "\"emergencyContactPhone\":");
    }

    @Test
    void dateOnlySerializationKeepsTheStoredBusinessDate() throws Exception
    {
        HrOnboardingDetailVo detail = new HrOnboardingDetailVo();
        detail.setExpectedEntryDate(Date.from(Instant.parse("2026-07-12T16:00:00Z")));
        detail.setBirthDate(Date.from(Instant.parse("1995-03-07T16:00:00Z")));
        detail.setPostEntryDueDate(Date.from(Instant.parse("2026-07-19T16:00:00Z")));

        String json = new ObjectMapper().writeValueAsString(detail);

        assertThat(json).contains("\"expectedEntryDate\":\"2026-07-13\"",
                "\"birthDate\":\"1995-03-08\"", "\"postEntryDueDate\":\"2026-07-20\"");
        assertThat(json).doesNotContain("\"expectedEntryDate\":\"2026-07-12\"");
    }

    @Test
    void dateOnlyRequestDtosParseAtTheBusinessTimezoneBoundary() throws Exception
    {
        ObjectMapper mapper = new ObjectMapper();
        long expectedEpochMillis = Instant.parse("2026-07-12T16:00:00Z").toEpochMilli();

        HrOnboardingCreateRequest create = mapper.readValue(
                "{\"expectedEntryDate\":\"2026-07-13\"}", HrOnboardingCreateRequest.class);
        HrOnboardingUpdateRequest update = mapper.readValue(
                "{\"expectedEntryDate\":\"2026-07-13\"}", HrOnboardingUpdateRequest.class);
        HrOnboardingConfirmRequest confirm = mapper.readValue(
                "{\"actualEntryDate\":\"2026-07-13\"}", HrOnboardingConfirmRequest.class);

        assertThat(create.getExpectedEntryDate().getTime()).isEqualTo(expectedEpochMillis);
        assertThat(update.getExpectedEntryDate().getTime()).isEqualTo(expectedEpochMillis);
        assertThat(confirm.getActualEntryDate().getTime()).isEqualTo(expectedEpochMillis);
    }

    private static void assertEndpoint(String name, Class<?>[] args, Class<?> mappingType,
            String path, String permission) throws Exception
    {
        Method method = HrOnboardingController.class.getMethod(name, args);
        RequiresPermissions permissions = method.getAnnotation(RequiresPermissions.class);
        assertThat(permissions).isNotNull();
        assertThat(permissions.value()).containsExactly(permission);
        String[] paths;
        if (mappingType == GetMapping.class) paths = method.getAnnotation(GetMapping.class).value();
        else if (mappingType == PostMapping.class) paths = method.getAnnotation(PostMapping.class).value();
        else paths = method.getAnnotation(PutMapping.class).value();
        if (path.isEmpty()) assertThat(paths).isEmpty();
        else assertThat(paths).containsExactly(path);
    }

    private static void assertLog(String name, Class<?>[] args, BusinessType businessType) throws Exception
    {
        Log log = HrOnboardingController.class.getMethod(name, args).getAnnotation(Log.class);
        assertThat(log).isNotNull();
        assertThat(log.businessType()).isEqualTo(businessType);
        assertThat(log.isSaveRequestData()).isFalse();
        assertThat(log.isSaveResponseData()).isFalse();
    }

    private static java.util.List<String> propertyNames(Class<?> type)
    {
        return Arrays.stream(type.getMethods())
                .map(Method::getName)
                .filter(name -> name.startsWith("get"))
                .map(name -> Character.toLowerCase(name.charAt(3)) + name.substring(4))
                .collect(java.util.stream.Collectors.toList());
    }

    private static IHrOnboardingService service(HrOnboardingDetailVo detail, HrOnboardingSummaryVo summary)
    {
        return (IHrOnboardingService) java.lang.reflect.Proxy.newProxyInstance(
                IHrOnboardingService.class.getClassLoader(), new Class<?>[] { IHrOnboardingService.class },
                (proxy, method, args) -> {
                    if (method.getDeclaringClass() == Object.class) return method.invoke(new Object(), args);
                    if (method.getName().equals("get")) return detail;
                    if (method.getName().equals("summary")) return summary;
                    throw new AssertionError("Unexpected call: " + method.getName());
                });
    }
}
