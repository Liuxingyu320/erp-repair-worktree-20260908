package com.erp.system.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.erp.common.log.annotation.Log;
import com.erp.common.log.enums.BusinessType;
import com.erp.common.security.annotation.RequiresPermissions;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;
import com.erp.system.service.IHrEmployeeProfileService;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.ArrayList;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import com.erp.system.domain.vo.HrEmployeeListVo;
import com.erp.system.domain.vo.HrEmployeeQuery;
import com.erp.system.service.impl.HrEmployeeQueueFilterService;

class HrEmployeeControllerContractTest
{
    @Test
    void listSummaryAndExportNormalizeReminderQueueFilters()
    {
        IHrEmployeeProfileService employees=mock(IHrEmployeeProfileService.class);
        HrEmployeeQueueFilterService filters=mock(HrEmployeeQueueFilterService.class);
        PagingAwareEmployeeController controller=new PagingAwareEmployeeController();
        ReflectionTestUtils.setField(controller,"employeeService",employees);
        ReflectionTestUtils.setField(controller,"queueFilterService",filters);
        HrEmployeeQuery query=new HrEmployeeQuery();query.setContractDue(true);
        org.mockito.Mockito.when(filters.prepare(query)).thenReturn(query);
        org.mockito.Mockito.when(employees.list(query)).thenReturn(new ArrayList<>());

        controller.list(query);
        controller.summary(query);
        MockHttpServletResponse response=new MockHttpServletResponse();
        controller.export(response,query);

        verify(filters,org.mockito.Mockito.times(3)).prepare(query);
        verify(employees,org.mockito.Mockito.times(2)).list(query);
        verify(employees).summary(query);
        assertThat(response.getContentAsByteArray()).isNotEmpty();
    }

    @Test
    void completenessFilteredListSkipsDatabasePaginationAndReturnsExactManualPageTotal()
    {
        IHrEmployeeProfileService service=mock(IHrEmployeeProfileService.class);
        HrEmployeeQueueFilterService filters=mock(HrEmployeeQueueFilterService.class);
        PagingAwareEmployeeController controller=new PagingAwareEmployeeController();
        ReflectionTestUtils.setField(controller,"employeeService",service);
        ReflectionTestUtils.setField(controller,"queueFilterService",filters);
        HrEmployeeQuery query=new HrEmployeeQuery();query.setCompletenessStatus("INCOMPLETE");
        org.mockito.Mockito.when(filters.prepare(query)).thenReturn(query);
        ArrayList<HrEmployeeListVo> filtered=new ArrayList<>();
        for(long id=1;id<=12;id++){HrEmployeeListVo row=new HrEmployeeListVo();row.setUserId(id);filtered.add(row);}
        org.mockito.Mockito.when(service.list(query)).thenReturn(filtered);
        MockHttpServletRequest request=new MockHttpServletRequest();request.setParameter("pageNum","2");
        request.setParameter("pageSize","5");RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        try
        {
            var result=controller.list(query);
            assertThat(controller.pageHelperStarted).isFalse();
            assertThat(result.getTotal()).isEqualTo(12);
            assertThat(result.getRows()).extracting(row->((HrEmployeeListVo)row).getUserId())
                    .containsExactly(6L,7L,8L,9L,10L);
        }
        finally{RequestContextHolder.resetRequestAttributes();}
    }

    @Test
    void employeePatchRevealAndSensitiveExportUseExactPermissionsAndPiiSafeLogs() throws Exception
    {
        Method patch = HrEmployeeProfileController.class.getMethod("edit", Long.class, java.util.Map.class);
        assertThat(patch.getAnnotation(PatchMapping.class).value()).containsExactly("/{userId}");
        assertThat(patch.getAnnotation(RequiresPermissions.class).value()).containsExactly("hr:employee:edit");
        assertSafeLog(patch, BusinessType.UPDATE);

        Method initialize=HrEmployeeProfileController.class.getMethod("initializeProfile",Long.class);
        assertThat(initialize.getAnnotation(PostMapping.class).value())
                .containsExactly("/{userId}/profile/initialize");
        assertThat(initialize.getAnnotation(RequiresPermissions.class).value())
                .containsExactly("hr:employee:edit");
        assertThat(initialize.getAnnotation(Log.class).title()).isEqualTo("建立员工档案");
        assertSafeLog(initialize,BusinessType.INSERT);

        Method reveal = HrEmployeeProfileController.class.getMethod("reveal", Long.class,
                com.erp.system.domain.vo.HrSensitiveRevealRequest.class, jakarta.servlet.http.HttpServletRequest.class);
        assertThat(reveal.getAnnotation(PostMapping.class).value()).containsExactly("/{userId}/sensitive/reveal");
        assertThat(reveal.getAnnotation(RequiresPermissions.class).value()).containsExactly("hr:employee:sensitive:view");
        assertSafeLog(reveal, BusinessType.OTHER);

        Method export = HrEmployeeProfileController.class.getMethod("exportSensitive",
                com.erp.system.domain.vo.HrSensitiveExportRequest.class, jakarta.servlet.http.HttpServletRequest.class,
                jakarta.servlet.http.HttpServletResponse.class);
        assertThat(export.getAnnotation(PostMapping.class).value()).containsExactly("/export-sensitive");
        assertThat(export.getAnnotation(RequiresPermissions.class).value()).containsExactly("hr:employee:export:sensitive");
        assertSafeLog(export, BusinessType.EXPORT);
        String source=source("HrEmployeeProfileController.java");
        assertThat(source).contains("HrSensitiveExportArtifact artifact=employeeService.exportSensitive",
                "recordSensitiveExportDeliveryFailure", "catch(IOException deliveryFailure)")
                .doesNotContain("new XSSFWorkbook()","setCellValue(value==null");
    }

    @Test
    void completenessControllerExposesOnlyServerCalculatedRoutesAndMissingAccountQueue() throws Exception
    {
        String source = source("HrCompletenessController.java");
        assertThat(source).contains("@GetMapping(\"/summary\")", "@GetMapping(\"/employees\")",
                        "@GetMapping(\"/departments\")", "getAccountConfigurationStatus", "\"MISSING\"")
                .doesNotContain("@GetMapping(\"/list\")", "selectUserList");
    }

    @Test
    void offboardingRoutesUseDedicatedPermissionAndPiiSafeAudit() throws Exception
    {
        String source = source("HrEmployeeProfileController.java");
        assertThat(source).contains(
                "@GetMapping(\"/offboard/business-date\")",
                "@PostMapping(\"/{userId}/offboard\")",
                "@RequiresPermissions(\"hr:employee:offboard\")",
                "isSaveRequestData = false",
                "isSaveResponseData = false");
    }

    @Test
    void currentUiUsesCanonicalPatchAndStripsRouteIdFromTheBody() throws Exception
    {
        Method legacy=HrEmployeeProfileController.class.getMethod("editLegacyRoot",java.util.Map.class);
        assertThat(legacy.getAnnotation(PutMapping.class).value()).isEmpty();
        assertThat(legacy.getAnnotation(RequiresPermissions.class).value()).containsExactly("hr:employee:edit");
        assertSafeLog(legacy,BusinessType.UPDATE);
        String api=Files.readString(Paths.get(System.getProperty("user.dir"))
                .resolve("../../erp-ui/src/api/hr/employee.js").normalize(),StandardCharsets.UTF_8);
        assertThat(api).contains("url: `/system/hr/employee/${userId}`", "method: \"patch\"",
                "delete patch.userId");
    }

    @Test
    void legacyRootPutDelegatesTheUnmodifiedCurrentUiPayloadToTheCompatibilityAdapter()
    {
        IHrEmployeeProfileService service=mock(IHrEmployeeProfileService.class);
        HrEmployeeProfileController controller=new HrEmployeeProfileController();
        ReflectionTestUtils.setField(controller,"employeeService",service);
        com.erp.common.core.context.SecurityContextHolder.setUserName("hr-user");
        try
        {
            Map<String,Object> input=new LinkedHashMap<>();input.put("userId",7L);input.put("nickName","新姓名");
            input.put("phonenumber","13900139000");input.put("status","0");
            input.put("profile",Map.of("userId",7L,"jobGrade","P6","bankAccount","62220000"));
            controller.editLegacyRoot(input);
            verify(service).updateLegacy(org.mockito.ArgumentMatchers.eq(input),
                    org.mockito.ArgumentMatchers.eq("hr-user"));
        }
        finally{com.erp.common.core.context.SecurityContextHolder.remove();}
    }

    private void assertSafeLog(Method method, BusinessType type)
    {
        Log log=method.getAnnotation(Log.class);
        assertThat(log).isNotNull();
        assertThat(log.businessType()).isEqualTo(type);
        assertThat(log.isSaveRequestData()).isFalse();
        assertThat(log.isSaveResponseData()).isFalse();
    }

    private String source(String file) throws Exception
    {
        Path root=Paths.get(System.getProperty("user.dir"));
        Path path=root.resolve("src/main/java/com/erp/system/controller").resolve(file);
        if(!Files.exists(path))path=root.resolve("erp-modules/erp-system/src/main/java/com/erp/system/controller").resolve(file);
        return Files.readString(path,StandardCharsets.UTF_8);
    }

    private static class PagingAwareEmployeeController extends HrEmployeeProfileController
    {
        private boolean pageHelperStarted;
        @Override protected void startPage(){pageHelperStarted=true;}
    }
}
