package com.erp.oa.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import com.erp.common.security.annotation.RequiresPermissions;
import com.erp.common.security.annotation.Logical;
import com.erp.common.security.annotation.RequiresLogin;
import com.erp.common.core.constant.SecurityConstants;
import com.erp.common.core.context.SecurityContextHolder;
import com.erp.oa.domain.OaSignPackage;
import com.erp.oa.domain.OaCompanySealConfig;
import com.erp.oa.domain.OaSignPlan;
import com.erp.oa.domain.OaSignPlanTemplate;
import com.erp.oa.domain.OaSignPlanVersion;
import com.erp.oa.domain.OaSignPlanVersionTemplate;
import com.erp.oa.domain.OaSignTemplate;
import com.erp.oa.domain.dto.OaSignDocumentHashRequest;
import com.erp.oa.domain.dto.OaSignDocumentReadRequest;
import com.erp.oa.domain.dto.OaSignFinalConfirmRequest;
import com.erp.oa.domain.dto.OaSignFinalDocumentHashRequest;
import com.erp.oa.domain.dto.OaSignFinalDocumentReadRequest;
import com.erp.oa.domain.dto.OaSignPackageSignRequest;
import com.erp.oa.domain.dto.OaSignPackageRefuseRequest;
import com.erp.oa.domain.vo.OaSignCompanyOptions;
import com.erp.oa.domain.vo.OaSignPackageFile;
import com.erp.oa.mapper.OaCompanySealConfigMapper;
import com.erp.oa.mapper.OaDeptScopeMapper;
import com.erp.oa.service.IOaSignPlanService;
import com.erp.oa.service.IOaSignPlanVersionService;
import com.erp.oa.service.IOaSignPackageService;
import com.erp.oa.service.IOaSignTemplateService;
import com.erp.oa.service.impl.OaSignCompanyService;
import com.erp.oa.service.impl.OaSignDocumentService;
import com.erp.oa.service.impl.OaSignHrAccessService;
import com.erp.oa.service.impl.OaSignVerificationService;
import com.erp.oa.domain.vo.OaSignVerificationResult;
import com.erp.system.api.domain.SysLegalEntity;
import com.erp.system.api.model.LoginUser;
import com.fasterxml.jackson.databind.ObjectMapper;

@DisplayName("OA员工签约Controller")
class OaSignPackageControllerTest
{
    @Test
    @DisplayName("普通HR模板和方案接口响应深拷贝裁剪文件摘要与params")
    void shouldRedactTemplateHashesFromAllBusinessConfigurationResponses() throws Exception
    {
        OaSignPackageController controller = new OaSignPackageController();
        IOaSignTemplateService templateService = mock(IOaSignTemplateService.class);
        IOaSignPlanService planService = mock(IOaSignPlanService.class);
        OaSignHrAccessService accessService = mock(OaSignHrAccessService.class);
        ReflectionTestUtils.setField(controller, "signTemplateService", templateService);
        ReflectionTestUtils.setField(controller, "signPlanService", planService);
        ReflectionTestUtils.setField(controller, "signHrAccessService", accessService);
        HttpServletRequest request = mock(HttpServletRequest.class);

        OaSignTemplate persistedTemplate = new OaSignTemplate();
        persistedTemplate.setTemplateId(10L);
        persistedTemplate.setTemplateType("onboard_labor_contract");
        persistedTemplate.setTemplateName("劳动合同");
        persistedTemplate.setFileUrl("/profile/labor.docx");
        persistedTemplate.setFileHash("real-template-hash-secret");
        persistedTemplate.setSignaturePositionJson("{\"mode\":\"SIGNATURE_STRATEGY_VISIBLE\"}");
        persistedTemplate.setCompanySealPositionJson("{\"mode\":\"SEAL_STRATEGY_VISIBLE\"}");
        persistedTemplate.setMatchConditionJson("{\"code\":\"MATCH_CONDITION_VISIBLE\"}");
        persistedTemplate.getParams().put("internalTemplateParam", "template-param-secret");
        OaSignPlanTemplate binding = new OaSignPlanTemplate();
        binding.setTemplateId(10L);
        binding.setTemplate(persistedTemplate);
        binding.getParams().put("internalBindingParam", "binding-param-secret");
        OaSignPlan persistedPlan = new OaSignPlan();
        persistedPlan.setPlanId(20L);
        persistedPlan.setPlanName("入职方案");
        persistedPlan.setLegalEntityId(9001L);
        persistedPlan.setLegalEntityName("法律主体业务字段可见");
        persistedPlan.setRuleJson("{\"code\":\"PLAN_RULE_VISIBLE\"}");
        persistedPlan.setDefaultValuesJson("{\"code\":\"PLAN_DEFAULT_VISIBLE\"}");
        persistedPlan.setSignDeadlineDays(7);
        persistedPlan.setReminderPolicyJson("{\"code\":\"REMINDER_VISIBLE\"}");
        persistedPlan.setAutoSendConditionJson("{\"code\":\"AUTO_CONDITION_VISIBLE\"}");
        persistedPlan.setTemplates(List.of(binding));
        persistedPlan.getParams().put("internalPlanParam", "plan-param-secret");

        when(templateService.selectTemplateList(org.mockito.ArgumentMatchers.any()))
                .thenReturn(List.of(persistedTemplate));
        when(templateService.saveTemplate(org.mockito.ArgumentMatchers.any())).thenReturn(persistedTemplate);
        when(templateService.matchTemplates(org.mockito.ArgumentMatchers.any())).thenReturn(List.of(persistedTemplate));
        when(planService.selectPlanList(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
                .thenReturn(List.of(persistedPlan));
        when(planService.getPlanDetail(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
                .thenReturn(persistedPlan);
        when(planService.savePlan(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
                .thenReturn(persistedPlan);

        Object[] responses = {
                controller.templateList(new OaSignTemplate()),
                controller.saveTemplate(new OaSignTemplate()),
                controller.matchTemplates(new OaSignPackage()),
                controller.planList(new OaSignPlan(), request),
                controller.planDetail(20L, request),
                controller.savePlan(new OaSignPlan(), request),
                controller.updatePlan(new OaSignPlan(), request)
        };
        String json = new ObjectMapper().writeValueAsString(responses);

        assertThat(json).doesNotContain("real-template-hash-secret", "template-param-secret",
                "binding-param-secret", "plan-param-secret");
        assertThat(json).contains("法律主体业务字段可见", "PLAN_RULE_VISIBLE", "PLAN_DEFAULT_VISIBLE",
                "REMINDER_VISIBLE", "AUTO_CONDITION_VISIBLE", "SIGNATURE_STRATEGY_VISIBLE",
                "SEAL_STRATEGY_VISIBLE", "MATCH_CONDITION_VISIBLE");
        assertThat(persistedTemplate.getFileHash()).isEqualTo("real-template-hash-secret");
        assertThat(persistedTemplate.getParams()).containsEntry("internalTemplateParam", "template-param-secret");
        assertThat(binding.getTemplate()).isSameAs(persistedTemplate);
        assertThat(persistedPlan.getParams()).containsEntry("internalPlanParam", "plan-param-secret");
    }

    @Test
    @DisplayName("签约包模板匹配接口应归属新增签约包权限")
    void matchTemplatesShouldUsePackageAddPermission() throws NoSuchMethodException
    {
        Method method = OaSignPackageController.class.getMethod("matchTemplates", OaSignPackage.class);
        RequiresPermissions annotation = method.getAnnotation(RequiresPermissions.class);

        assertThat(annotation).isNotNull();
        assertThat(annotation.value()).containsExactly("oa:signPackage:add");
    }

    @Test
    @DisplayName("模板类型字典应允许签约包列表页面读取")
    void templateTypesShouldUsePackageListPermission() throws NoSuchMethodException
    {
        Method method = OaSignPackageController.class.getMethod("templateTypes");
        RequiresPermissions annotation = method.getAnnotation(RequiresPermissions.class);

        assertThat(annotation).isNotNull();
        assertThat(annotation.value()).containsExactly("oa:signPackage:list");
    }

    @Test
    @DisplayName("签约方案保存接口应归属模板维护权限")
    void signPlanSaveEndpointsShouldUseTemplatePermission() throws NoSuchMethodException
    {
        Method save = OaSignPackageController.class.getMethod("savePlan", OaSignPlan.class,
                jakarta.servlet.http.HttpServletRequest.class);
        Method update = OaSignPackageController.class.getMethod("updatePlan", OaSignPlan.class,
                jakarta.servlet.http.HttpServletRequest.class);

        assertThat(save.getAnnotation(RequiresPermissions.class).value()).containsExactly("oa:signPackage:template");
        assertThat(update.getAnnotation(RequiresPermissions.class).value()).containsExactly("oa:signPackage:template");
    }

    @Test
    @DisplayName("公司印章读写接口使用独立最小权限")
    void companySealEndpointsShouldUseIndependentPermissions() throws NoSuchMethodException
    {
        Method list = OaSignPackageController.class.getMethod("companySeals", Long.class,
                boolean.class);
        Method save = OaSignPackageController.class.getMethod("saveCompanySeal",
                OaCompanySealConfig.class);

        assertThat(list.getAnnotation(RequiresPermissions.class).value())
                .containsExactly("oa:signSeal:list");
        assertThat(save.getAnnotation(RequiresPermissions.class).value())
                .containsExactly("oa:signSeal:edit");
    }

    @Test
    @DisplayName("切换公司后复用合同章唯一性规则且不推荐多枚默认章")
    void companyOptionsShouldReuseContractSealRecommendation()
    {
        OaSignPackageController controller = new OaSignPackageController();
        IOaSignPackageService packageService = mock(IOaSignPackageService.class);
        OaDeptScopeMapper deptScopeMapper = mock(OaDeptScopeMapper.class);
        OaCompanySealConfigMapper sealMapper = mock(OaCompanySealConfigMapper.class);
        OaSignCompanyService companyService = new OaSignCompanyService(deptScopeMapper,
                sealMapper, mock(OaSignDocumentService.class));
        OaSignCompanyOptions options = new OaSignCompanyOptions();
        SysLegalEntity legalEntity = new SysLegalEntity();
        legalEntity.setLegalEntityId(9001L);
        OaCompanySealConfig firstDefault = seal(101L, "CONTRACT", "Y");
        OaCompanySealConfig secondDefault = seal(102L, "CONTRACT", "Y");
        OaCompanySealConfig financeSeal = seal(103L, "FINANCE", "N");
        when(packageService.getCompanyOptions(90L, null)).thenReturn(options);
        when(deptScopeMapper.selectActiveLegalEntityById(9001L)).thenReturn(legalEntity);
        when(sealMapper.selectSealsByLegalEntity(9001L, true))
                .thenReturn(List.of(firstDefault, secondDefault, financeSeal));
        ReflectionTestUtils.setField(controller, "signPackageService", packageService);
        ReflectionTestUtils.setField(controller, "signCompanyService", companyService);

        OaSignCompanyOptions result = (OaSignCompanyOptions) controller.companyOptions(
                90L, 9001L, new MockHttpServletRequest()).get("data");

        assertThat(result.getSeals()).containsExactly(firstDefault, secondDefault);
        assertThat(result.getRecommendedSealId()).isNull();
    }

    @Test
    @DisplayName("生产默认关闭自由建包但任务来源建包不受影响")
    void emergencyCreateShouldBeOptInAndRequireAuditedReason()
    {
        OaSignPackageController controller = new OaSignPackageController();
        IOaSignPackageService packageService = mock(IOaSignPackageService.class);
        ReflectionTestUtils.setField(controller, "signPackageService", packageService);
        HttpServletRequest request = mock(HttpServletRequest.class);
        OaSignPackage manual = new OaSignPackage();

        assertThatThrownBy(() -> controller.create(manual, request))
                .isInstanceOf(com.erp.common.core.exception.ServiceException.class)
                .hasMessageContaining("应急建包入口未启用");

        OaSignPackage taskPackage = new OaSignPackage();
        taskPackage.setTaskId(9L);
        when(packageService.createPackage(taskPackage, null)).thenReturn(taskPackage);
        assertThat(controller.create(taskPackage, request).get("data")).isSameAs(taskPackage);

        ReflectionTestUtils.setField(controller, "emergencyCreateEnabled", true);
        assertThatThrownBy(() -> controller.create(manual, request))
                .isInstanceOf(com.erp.common.core.exception.ServiceException.class)
                .hasMessageContaining("应急建包原因不能为空");
        manual.setRemark("纸质审批单已授权补录");
        when(packageService.createPackage(manual, null)).thenReturn(manual);

        controller.create(manual, request);

        assertThat(manual.getRemark()).isEqualTo("非生命周期任务来源：纸质审批单已授权补录");
    }

    @Test
    @DisplayName("发布签约方案使用模板维护权限且当前唯一HR响应不泄露内部hash")
    void publishPlanShouldUseConfigurationPermissionAndRedactHashes() throws Exception
    {
        Method method = OaSignPackageController.class.getMethod("publishPlan", Long.class,
                com.erp.oa.domain.dto.OaSignPlanPublishRequest.class,
                jakarta.servlet.http.HttpServletRequest.class);
        assertThat(method.getAnnotation(RequiresPermissions.class).value())
                .containsExactly("oa:signPackage:template");

        OaSignPackageController controller = new OaSignPackageController();
        IOaSignPlanVersionService versionService = mock(IOaSignPlanVersionService.class);
        OaSignHrAccessService accessService = mock(OaSignHrAccessService.class);
        HttpServletRequest request = mock(HttpServletRequest.class);
        OaSignPlanVersionTemplate template = new OaSignPlanVersionTemplate();
        template.setTemplateType("LABOR_CONTRACT");
        template.setSourceFileHash("source-file-hash-secret");
        OaSignPlanVersion version = new OaSignPlanVersion();
        version.setVersionId(501L);
        version.setPlanId(20L);
        version.setVersionNo(3);
        version.setPublishStatus("PUBLISHED");
        version.setMatchingStatus("ENABLED");
        version.setVersionHash("version-hash-secret");
        version.setTemplates(List.of(template));
        when(versionService.publish(org.mockito.ArgumentMatchers.eq(20L),
                org.mockito.ArgumentMatchers.nullable(Long.class))).thenReturn(version);
        ReflectionTestUtils.setField(controller, "signPlanVersionService", versionService);
        ReflectionTestUtils.setField(controller, "signHrAccessService", accessService);

        String json = new ObjectMapper().writeValueAsString(controller.publishPlan(20L, null, request));

        verify(accessService).requireCurrentHr();
        assertThat(json).contains("501", "PUBLISHED", "ENABLED")
                .doesNotContain("version-hash-secret", "source-file-hash-secret", "versionHash",
                        "sourceFileHash");
    }

    @Test
    void previewAndConfirmationRequireCurrentHrBeforeReadingOrMutatingVersions() throws Exception
    {
        Method preview = OaSignPackageController.class.getMethod("previewPublishPlan", Long.class);
        assertThat(preview.getAnnotation(RequiresPermissions.class).value()).containsExactly("oa:signPackage:template");
        OaSignPackageController controller = new OaSignPackageController();
        IOaSignPlanVersionService service = mock(IOaSignPlanVersionService.class);
        OaSignHrAccessService access = mock(OaSignHrAccessService.class);
        ReflectionTestUtils.setField(controller, "signPlanVersionService", service);
        ReflectionTestUtils.setField(controller, "signHrAccessService", access);
        org.mockito.Mockito.doThrow(new com.erp.common.core.exception.ServiceException("当前唯一HR权限已变化"))
                .when(access).requireCurrentHr();
        assertThatThrownBy(() -> controller.previewPublishPlan(20L)).hasMessageContaining("权限已变化");
        assertThatThrownBy(() -> controller.publishPlan(20L,
                new com.erp.oa.domain.dto.OaSignPlanPublishRequest("00000000-0000-0000-0000-000000000001", 501L),
                mock(HttpServletRequest.class))).hasMessageContaining("权限已变化");
        org.mockito.Mockito.verifyNoInteractions(service);
    }

    @Test
    void confirmationPassesExplicitPreviewContractWithoutFallingBackToLegacyPublish()
    {
        OaSignPackageController controller = new OaSignPackageController();
        IOaSignPlanVersionService service = mock(IOaSignPlanVersionService.class);
        OaSignHrAccessService access = mock(OaSignHrAccessService.class);
        ReflectionTestUtils.setField(controller, "signPlanVersionService", service);
        ReflectionTestUtils.setField(controller, "signHrAccessService", access);
        var request = new com.erp.oa.domain.dto.OaSignPlanPublishRequest("00000000-0000-0000-0000-000000000001", 501L);
        controller.publishPlan(20L, request, mock(HttpServletRequest.class));
        verify(service).confirmPublish(20L, request, null);
        verify(service, never()).publish(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("员工签约文件下载接口必须登录后访问")
    void mobileFileEndpointsShouldRequireLogin() throws NoSuchMethodException
    {
        Method document = OaSignPackageController.class.getMethod("mobileDocumentFile", Long.class, Long.class);
        Method signedDocument = OaSignPackageController.class.getMethod("mobileSignedDocumentFile",
                Long.class, Long.class);
        Method certificate = OaSignPackageController.class.getMethod("mobileCertificateFile", Long.class, Long.class);

        assertThat(document.getAnnotation(RequiresLogin.class)).isNotNull();
        assertThat(signedDocument.getAnnotation(RequiresLogin.class)).isNotNull();
        assertThat(certificate.getAnnotation(RequiresLogin.class)).isNotNull();
    }

    @Test
    @DisplayName("员工待签时原始file端点必须传播禁止下载")
    void mobileRawFileShouldPropagatePendingDownloadRejection()
    {
        OaSignPackageController controller = new OaSignPackageController();
        IOaSignPackageService packageService = mock(IOaSignPackageService.class);
        ReflectionTestUtils.setField(controller, "signPackageService", packageService);
        when(packageService.resolveMyDocumentFile(90L, 51L))
                .thenThrow(new com.erp.common.core.exception.ServiceException(
                        "签约完成前仅支持在线预览，不能下载原始文件"));

        assertThatThrownBy(() -> controller.mobileDocumentFile(90L, 51L))
                .isInstanceOf(com.erp.common.core.exception.ServiceException.class)
                .hasMessageContaining("仅支持在线预览")
                .hasMessageContaining("不能下载原始文件");

        verify(packageService).resolveMyDocumentFile(90L, 51L);
        verify(packageService, never()).resolveMyDocumentPreviewFile(90L, 51L);
    }

    @Test
    @DisplayName("后台签约文件下载接口必须使用签约包查询权限")
    void desktopFileEndpointsShouldUseQueryPermission() throws NoSuchMethodException
    {
        Method document = OaSignPackageController.class.getMethod("documentFile", Long.class, Long.class,
                jakarta.servlet.http.HttpServletRequest.class);
        Method signedDocument = OaSignPackageController.class.getMethod("signedDocumentFile", Long.class, Long.class,
                jakarta.servlet.http.HttpServletRequest.class);
        Method finalDocument = OaSignPackageController.class.getMethod("finalDocumentFile", Long.class, Long.class,
                jakarta.servlet.http.HttpServletRequest.class);
        Method certificate = OaSignPackageController.class.getMethod("certificateFile", Long.class, Long.class,
                jakarta.servlet.http.HttpServletRequest.class);

        assertThat(document.getAnnotation(RequiresPermissions.class).value()).containsExactly("oa:signPackage:query");
        assertThat(signedDocument.getAnnotation(RequiresPermissions.class).value())
                .containsExactly("oa:signPackage:query");
        assertThat(finalDocument.getAnnotation(RequiresPermissions.class).value())
                .containsExactly("oa:signPackage:query");
        assertThat(certificate.getAnnotation(RequiresPermissions.class).value()).containsExactly("oa:signPackage:query");
    }

    @Test
    @DisplayName("HR最终文件端点以内联且不缓存方式返回预览PDF")
    void desktopFinalFileShouldStreamScopedPreviewWithoutCaching()
    {
        OaSignPackageController controller = new OaSignPackageController();
        IOaSignPackageService packageService = mock(IOaSignPackageService.class);
        MockHttpServletRequest request = new MockHttpServletRequest();
        ReflectionTestUtils.setField(controller, "signPackageService", packageService);
        OaSignPackageFile file = new OaSignPackageFile(Path.of("/tmp/final-preview.pdf"),
                "劳动合同-最终合同.pdf", "application/pdf");
        when(packageService.resolveScopedFinalDocumentFile(90L, 51L, null))
                .thenReturn(file);

        ResponseEntity<Resource> response = controller.finalDocumentFile(90L, 51L, request);

        assertThat(response.getHeaders().getCacheControl()).isEqualTo("no-store");
        assertThat(response.getHeaders().getContentDisposition().getType())
                .isEqualTo("inline");
        assertThat(response.getHeaders().getContentType().toString())
                .isEqualTo("application/pdf");
        assertThat(response.getBody()).isNotNull();
        verify(packageService).resolveScopedFinalDocumentFile(90L, 51L, null);
    }

    @Test
    @DisplayName("最终归档导出仅返回附件并保持不缓存，员工和HR入口均受访问注解保护")
    void finalArchiveExportShouldAttachPdfWithoutCaching() throws Exception
    {
        OaSignPackageController controller = new OaSignPackageController();
        IOaSignPackageService packageService = mock(IOaSignPackageService.class);
        MockHttpServletRequest request = new MockHttpServletRequest();
        ReflectionTestUtils.setField(controller, "signPackageService", packageService);
        Path exportPath = Files.createTempFile("final-archive-controller-test-", ".pdf");
        Files.writeString(exportPath, "%PDF-1.7\n%%EOF\n");
        try
        {
            OaSignPackageFile file = new OaSignPackageFile(exportPath,
                    "SP-90-员工-劳动合同-签章展示版.pdf", "application/pdf");
            when(packageService.resolveScopedFinalDocumentExportFile(90L, 51L, null))
                    .thenReturn(file);
            when(packageService.resolveMyFinalDocumentExportFile(90L, 51L))
                    .thenReturn(file);

            ResponseEntity<Resource> desktop = controller.finalDocumentExport(90L, 51L, request);
            ResponseEntity<Resource> mobile = controller.mobileFinalDocumentExport(90L, 51L);

            for (ResponseEntity<Resource> response : List.of(desktop, mobile))
            {
                assertThat(response.getHeaders().getCacheControl()).isEqualTo("no-store");
                assertThat(response.getHeaders().getFirst("X-Content-Type-Options"))
                        .isEqualTo("nosniff");
                assertThat(response.getHeaders().getContentLength()).isEqualTo(Files.size(exportPath));
                assertThat(response.getHeaders().getContentDisposition().getType())
                        .isEqualTo("attachment");
                assertThat(response.getHeaders().getContentDisposition().getFilename())
                        .isEqualTo("SP-90-员工-劳动合同-签章展示版.pdf");
                assertThat(response.getHeaders().getContentType().toString())
                        .isEqualTo("application/pdf");
                assertThat(response.getBody()).isNotNull();
            }
        }
        finally
        {
            Files.deleteIfExists(exportPath);
        }
        verify(packageService).resolveScopedFinalDocumentExportFile(90L, 51L, null);
        verify(packageService).resolveMyFinalDocumentExportFile(90L, 51L);

        Method desktopMethod = OaSignPackageController.class.getMethod("finalDocumentExport",
                Long.class, Long.class, HttpServletRequest.class);
        Method mobileMethod = OaSignPackageController.class.getMethod("mobileFinalDocumentExport",
                Long.class, Long.class);
        assertThat(desktopMethod.getAnnotation(RequiresPermissions.class).value())
                .containsExactly("oa:signPackage:query");
        assertThat(desktopMethod.getAnnotation(GetMapping.class).value())
                .containsExactly("/{packageId}/documents/{documentId}/final-file/export");
        assertThat(mobileMethod.getAnnotation(RequiresLogin.class)).isNotNull();
        assertThat(mobileMethod.getAnnotation(GetMapping.class).value())
                .containsExactly("/mobile/{packageId}/documents/{documentId}/final-file/export");
    }

    @Test
    @DisplayName("签章展示副本在响应流关闭后删除且不影响原归档")
    void derivedExportShouldDeleteOnlyDisposableResponseFileAfterStreaming() throws Exception
    {
        OaSignPackageController controller = new OaSignPackageController();
        IOaSignPackageService packageService = mock(IOaSignPackageService.class);
        MockHttpServletRequest request = new MockHttpServletRequest();
        ReflectionTestUtils.setField(controller, "signPackageService", packageService);
        Path derivedPath = Files.createTempFile("final-export-derived-controller-test-", ".pdf");
        Files.writeString(derivedPath, "%PDF-1.7\n%%EOF\n");
        OaSignPackageFile file = new OaSignPackageFile(derivedPath,
                "SP-90-员工-劳动合同-签章展示版.pdf", "application/pdf", null, true);
        when(packageService.resolveScopedFinalDocumentExportFile(90L, 51L, null))
                .thenReturn(file);

        ResponseEntity<Resource> response = controller.finalDocumentExport(90L, 51L, request);
        assertThat(derivedPath).isRegularFile();
        try (java.io.InputStream input = response.getBody().getInputStream())
        {
            assertThat(input.read()).isEqualTo('%');
        }
        assertThat(derivedPath).doesNotExist();
    }

    @Test
    @DisplayName("员工阅读确认必须提交请求号、预期版本和文件证据")
    void mobileReadShouldAcceptFrozenDocumentEvidence() throws NoSuchMethodException
    {
        Method method = OaSignPackageController.class.getMethod("mobileRead", Long.class, Long.class,
                OaSignDocumentReadRequest.class);

        assertThat(method.getAnnotation(RequiresLogin.class)).isNotNull();
    }

    @Test
    @DisplayName("通用移动签名入口必须传播Excel分阶段签约包的防绕过拒绝")
    void mobileSignShouldPropagateStagedExcelFlowRejection()
    {
        OaSignPackageController controller = new OaSignPackageController();
        IOaSignPackageService packageService = mock(IOaSignPackageService.class);
        OaSignPackageSignRequest signRequest = new OaSignPackageSignRequest();
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("127.0.0.1");
        request.addHeader("User-Agent", "mobile-test");
        ReflectionTestUtils.setField(controller, "signPackageService", packageService);
        when(packageService.signPackage(90L, signRequest, "127.0.0.1", "mobile-test"))
                .thenThrow(new com.erp.common.core.exception.ServiceException(
                        "Excel入职签约包请在合同资料补全入口完成事实确认与唯一一次签名"));

        assertThatThrownBy(() -> controller.mobileSign(90L, signRequest, request))
                .isInstanceOf(com.erp.common.core.exception.ServiceException.class)
                .hasMessageContaining("合同资料补全入口")
                .hasMessageContaining("唯一一次签名");

        verify(packageService).signPackage(90L, signRequest, "127.0.0.1", "mobile-test");
    }

    @Test
    @DisplayName("员工拒签接口必须登录并提交结构化拒签请求")
    void mobileRefuseShouldRequireLoginAndStructuredRequest() throws NoSuchMethodException
    {
        Method method = OaSignPackageController.class.getMethod("mobileRefuse", Long.class,
                OaSignPackageRefuseRequest.class, jakarta.servlet.http.HttpServletRequest.class);

        assertThat(method.getAnnotation(RequiresLogin.class)).isNotNull();
        assertThat(method.getAnnotation(com.erp.common.security.annotation.IdempotentSubmit.class))
                .as("持久 requestId 回放不得被30秒 Redis 拦截")
                .isNull();
        assertThat(method.getAnnotation(PostMapping.class).value())
                .containsExactly("/mobile/{packageId}/refuse");
    }

    @Test
    @DisplayName("有持久载荷回放的签名与最终确认接口成功后释放短期防重锁")
    void durableSignAndFinalWriteEndpointsShouldReleaseInFlightGuardOnSuccess()
            throws NoSuchMethodException
    {
        Method finalConfirm = OaSignPackageController.class.getMethod("mobileFinalConfirm",
                Long.class, OaSignFinalConfirmRequest.class, HttpServletRequest.class);
        Method finalRead = OaSignPackageController.class.getMethod("mobileFinalRead",
                Long.class, Long.class, OaSignFinalDocumentReadRequest.class,
                HttpServletRequest.class);
        Method initialRead = OaSignPackageController.class.getMethod("mobileRead",
                Long.class, Long.class, OaSignDocumentReadRequest.class);
        Method sign = OaSignPackageController.class.getMethod("mobileSign",
                Long.class, OaSignPackageSignRequest.class, HttpServletRequest.class);

        assertThat(finalConfirm.getAnnotation(
                com.erp.common.security.annotation.IdempotentSubmit.class).releaseOnSuccess())
                .isTrue();
        assertThat(finalRead.getAnnotation(
                com.erp.common.security.annotation.IdempotentSubmit.class).releaseOnSuccess())
                .isTrue();
        assertThat(initialRead.getAnnotation(
                com.erp.common.security.annotation.IdempotentSubmit.class).releaseOnSuccess())
                .as("初始阅读已持久requestId，成功后应允许幂等回放")
                .isTrue();
        assertThat(sign.getAnnotation(
                com.erp.common.security.annotation.IdempotentSubmit.class).releaseOnSuccess())
                .as("签名已持久规范载荷hash，成功后应由业务层回放")
                .isTrue();
    }

    @Test
    @DisplayName("业务验真接口允许唯一HR查询权限或技术证据权限只读访问")
    void verificationEndpointShouldAllowPackageQueryOrTechnicalEvidence() throws NoSuchMethodException
    {
        Method verify = OaSignPackageController.class.getMethod("verify", Long.class, boolean.class,
                jakarta.servlet.http.HttpServletRequest.class);

        assertThat(verify.getAnnotation(RequiresPermissions.class)).isNotNull();
        assertThat(verify.getAnnotation(RequiresPermissions.class).value())
                .containsExactly("oa:signPackage:query", "oa:signTask:technicalEvidence");
        assertThat(verify.getAnnotation(RequiresPermissions.class).logical()).isEqualTo(Logical.OR);
    }

    @Test
    @DisplayName("当前唯一HR即使是管理员且请求技术模式也只返回业务验真")
    void currentHrShouldNeverEnableTechnicalVerification() throws Exception
    {
        login(1L, Set.of("oa:signTask:technicalEvidence"));
        try
        {
            OaSignPackageController controller = new OaSignPackageController();
            IOaSignPackageService packageService = mock(IOaSignPackageService.class);
            OaSignVerificationService verificationService = mock(OaSignVerificationService.class);
            OaSignHrAccessService accessService = mock(OaSignHrAccessService.class);
            HttpServletRequest request = mock(HttpServletRequest.class);
            OaSignPackage signPackage = new OaSignPackage();
            signPackage.setPackageId(90L);
            when(packageService.getPackageForVerification(org.mockito.ArgumentMatchers.eq(90L),
                    org.mockito.ArgumentMatchers.nullable(Long.class))).thenReturn(signPackage);
            when(accessService.isCurrentHr()).thenReturn(true);
            when(verificationService.verify(signPackage, false)).thenReturn(businessVerification());
            when(verificationService.verify(signPackage, true)).thenReturn(technicalVerification());
            ReflectionTestUtils.setField(controller, "signPackageService", packageService);
            ReflectionTestUtils.setField(controller, "signVerificationService", verificationService);
            ReflectionTestUtils.setField(controller, "signHrAccessService", accessService);

            String json = new ObjectMapper().writeValueAsString(controller.verify(90L, true, request));

            verify(verificationService).verify(signPackage, false);
            assertThat(json).doesNotContain("expected-hash-secret", "actual-hash-secret", "file-hash-secret");
        }
        finally
        {
            SecurityContextHolder.remove();
            RequestContextHolder.resetRequestAttributes();
        }
    }

    @Test
    @DisplayName("非当前HR具备技术证据权限时可显式读取技术验真")
    void nonCurrentTechnicalReaderShouldRetainTechnicalVerification() throws Exception
    {
        login(2L, Set.of("oa:signTask:technicalEvidence"));
        try
        {
            OaSignPackageController controller = new OaSignPackageController();
            IOaSignPackageService packageService = mock(IOaSignPackageService.class);
            OaSignVerificationService verificationService = mock(OaSignVerificationService.class);
            OaSignHrAccessService accessService = mock(OaSignHrAccessService.class);
            HttpServletRequest request = mock(HttpServletRequest.class);
            OaSignPackage signPackage = new OaSignPackage();
            signPackage.setPackageId(90L);
            when(packageService.getPackageForVerification(org.mockito.ArgumentMatchers.eq(90L),
                    org.mockito.ArgumentMatchers.nullable(Long.class))).thenReturn(signPackage);
            when(accessService.isCurrentHr()).thenReturn(false);
            when(verificationService.verify(signPackage, true)).thenReturn(technicalVerification());
            ReflectionTestUtils.setField(controller, "signPackageService", packageService);
            ReflectionTestUtils.setField(controller, "signVerificationService", verificationService);
            ReflectionTestUtils.setField(controller, "signHrAccessService", accessService);

            String json = new ObjectMapper().writeValueAsString(controller.verify(90L, true, request));

            verify(verificationService).verify(signPackage, true);
            assertThat(json).contains("expected-hash-secret", "actual-hash-secret", "file-hash-secret");
        }
        finally
        {
            SecurityContextHolder.remove();
            RequestContextHolder.resetRequestAttributes();
        }
    }

    @Test
    @DisplayName("员工签署请求公共字段必须携带版本、签名和requestId")
    void mobileSignRequestShouldValidateFrozenDocumentContract()
    {
        OaSignPackageSignRequest request = new OaSignPackageSignRequest();

        try (var factory = Validation.buildDefaultValidatorFactory())
        {
            Set<ConstraintViolation<OaSignPackageSignRequest>> violations = factory.getValidator().validate(request);
            assertThat(violations).extracting(violation -> violation.getPropertyPath().toString())
                    .contains("documentVersion", "signConfirmText", "signatureDataUrl", "requestId")
                    .doesNotContain("documentHashes", "finalDocumentHashes");
        }
    }

    @Test
    @DisplayName("员工签署请求逐文件hash也必须递归校验")
    void mobileSignRequestShouldCascadeValidationToDocumentHashes()
    {
        OaSignPackageSignRequest request = new OaSignPackageSignRequest();
        request.setDocumentVersion("SP-1-V1");
        request.setDocumentHashes(java.util.List.of(new OaSignDocumentHashRequest()));
        request.setSignConfirmText("本人确认签署本签约包");
        request.setSignatureDataUrl("data:image/png;base64,AA==");
        request.setRequestId("request-1");

        try (var factory = Validation.buildDefaultValidatorFactory())
        {
            Set<ConstraintViolation<OaSignPackageSignRequest>> violations = factory.getValidator().validate(request);
            assertThat(violations).extracting(violation -> violation.getPropertyPath().toString())
                    .contains("documentHashes[0].documentId", "documentHashes[0].reviewPdfHash");
        }
    }

    @Test
    @DisplayName("公司先行最终逐文件hash必须递归校验")
    void mobileSignRequestShouldCascadeValidationToFinalDocumentHashes()
    {
        OaSignPackageSignRequest request = new OaSignPackageSignRequest();
        request.setDocumentVersion("SP-1-V1");
        request.setFinalDocumentHashes(java.util.List.of(
                new OaSignFinalDocumentHashRequest()));
        request.setSignConfirmText("本人确认签署本签约包");
        request.setSignatureDataUrl("data:image/png;base64,AA==");
        request.setRequestId("request-1");

        try (var factory = Validation.buildDefaultValidatorFactory())
        {
            Set<ConstraintViolation<OaSignPackageSignRequest>> violations =
                    factory.getValidator().validate(request);
            assertThat(violations).extracting(violation ->
                    violation.getPropertyPath().toString())
                    .contains("finalDocumentHashes[0].documentId",
                            "finalDocumentHashes[0].finalPdfHash");
        }
    }

    private static OaSignVerificationResult businessVerification()
    {
        OaSignVerificationResult result = new OaSignVerificationResult();
        result.setMessage("文件完整，验真通过");
        return result;
    }

    private static OaSignVerificationResult technicalVerification()
    {
        OaSignVerificationResult.TechnicalEvidence evidence = new OaSignVerificationResult.TechnicalEvidence();
        evidence.setFileUrl("file-hash-secret");
        evidence.setExpectedHash("expected-hash-secret");
        evidence.setActualHash("actual-hash-secret");
        OaSignVerificationResult result = businessVerification();
        result.setTechnicalEvidence(List.of(evidence));
        return result;
    }

    private static OaCompanySealConfig seal(Long sealId, String sealType, String isDefault)
    {
        OaCompanySealConfig seal = new OaCompanySealConfig();
        seal.setSealId(sealId);
        seal.setLegalEntityId(9001L);
        seal.setSealType(sealType);
        seal.setIsDefault(isDefault);
        return seal;
    }

    private static void login(Long userId, Set<String> permissions)
    {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(SecurityConstants.AUTHORIZATION_HEADER, "Bearer test-token");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        SecurityContextHolder.setUserId(String.valueOf(userId));
        LoginUser loginUser = new LoginUser();
        loginUser.setUserid(userId);
        loginUser.setPermissions(new java.util.HashSet<>(permissions));
        loginUser.setRoles(new java.util.HashSet<>());
        SecurityContextHolder.set(SecurityConstants.LOGIN_USER, loginUser);
    }
}
