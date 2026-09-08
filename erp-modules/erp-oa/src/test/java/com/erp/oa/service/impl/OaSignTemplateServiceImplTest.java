package com.erp.oa.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;
import com.erp.oa.constant.OaSignTemplateType;
import com.erp.oa.domain.OaSignPackage;
import com.erp.oa.domain.OaSignTemplate;
import com.erp.oa.mapper.OaSignTemplateMapper;

@DisplayName("员工签约模板服务")
class OaSignTemplateServiceImplTest
{
    @ParameterizedTest(name = "{0}")
    @MethodSource("regularizationTemplateTypes")
    @DisplayName("上传转正材料时由后端固定场景签署要求和占位符")
    void shouldRecognizeRegularizationTemplateUploads(String type,
            String expectedPlaceholders)
    {
        OaSignTemplateServiceImpl service = templateService();
        OaSignTemplateMapper mapper = (OaSignTemplateMapper) ReflectionTestUtils.getField(
                service, "templateMapper");
        OaSignDocumentService documentService = (OaSignDocumentService)
                ReflectionTestUtils.getField(service, "documentService");
        org.mockito.Mockito.doAnswer(invocation -> {
            invocation.getArgument(0, OaSignTemplate.class).setTemplateId(9L);
            return 1;
        }).when(mapper).insertOaSignTemplate(any());
        OaSignTemplate input = new OaSignTemplate();
        input.setTemplateType(type);
        input.setTemplateName(type);
        input.setFileUrl("/profile/regularize.docx");
        input.setCompanySealRequired("N");
        when(mapper.selectOaSignTemplateById(9L)).thenAnswer(invocation -> input);

        OaSignTemplate saved = service.saveTemplate(input);

        assertThat(saved.getScenario()).isEqualTo("regularize");
        assertThat(saved.getEmployeeVisible()).isEqualTo("Y");
        assertThat(saved.getReadConfirmationRequired()).isEqualTo("Y");
        assertThat(saved.getEmployeeSignRequired()).isEqualTo("Y");
        assertThat(saved.getRequiredPlaceholders()).isEqualTo(expectedPlaceholders);
        verify(documentService).assertTemplateContainsRequiredPlaceholders(
                type, "/profile/regularize.docx");
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("renewalTemplateTypes")
    @DisplayName("上传续签材料时强制使用服务端场景用工类型和占位符")
    void shouldRecognizeRenewalTemplateUploads(String type, String employmentType,
            String expectedPlaceholders)
    {
        OaSignTemplateServiceImpl service = templateService();
        OaSignTemplateMapper mapper = (OaSignTemplateMapper) ReflectionTestUtils.getField(
                service, "templateMapper");
        OaSignDocumentService documentService = (OaSignDocumentService)
                ReflectionTestUtils.getField(service, "documentService");
        org.mockito.Mockito.doAnswer(invocation -> {
            invocation.getArgument(0, OaSignTemplate.class).setTemplateId(19L);
            return 1;
        }).when(mapper).insertOaSignTemplate(any());
        OaSignTemplate input = new OaSignTemplate();
        input.setTemplateType(type);
        input.setTemplateName(type);
        input.setFileUrl("/profile/renewal.docx");
        input.setScenario("onboard");
        input.setEmploymentType("客户端伪造值");
        input.setRequiredPlaceholders("employeeName");
        input.setCompanySealRequired("N");
        when(mapper.selectOaSignTemplateById(19L)).thenAnswer(invocation -> input);

        OaSignTemplate saved = service.saveTemplate(input);

        assertThat(saved.getScenario()).isEqualTo("renewal");
        if (employmentType == null)
        {
            assertThat(saved.getEmploymentType()).isBlank();
        }
        else
        {
            assertThat(saved.getEmploymentType()).isEqualTo(employmentType);
        }
        assertThat(saved.getRequiredPlaceholders()).isEqualTo(expectedPlaceholders);
        assertThat(saved.getEmployeeVisible()).isEqualTo("Y");
        assertThat(saved.getReadConfirmationRequired()).isEqualTo("Y");
        assertThat(saved.getEmployeeSignRequired()).isEqualTo("Y");
        verify(documentService).assertTemplateContainsRequiredPlaceholders(
                type, "/profile/renewal.docx");
    }

    @Test
    @DisplayName("保存劳动合同模板时后端兜底用工类型")
    void shouldNormalizeLaborContractTemplateRules() throws Exception
    {
        OaSignTemplateServiceImpl service = templateService();
        OaSignTemplateMapper mapper = (OaSignTemplateMapper) ReflectionTestUtils.getField(service, "templateMapper");
        ArgumentCaptor<OaSignTemplate> captor = ArgumentCaptor.forClass(OaSignTemplate.class);
        org.mockito.Mockito.doAnswer(invocation -> {
            invocation.getArgument(0, OaSignTemplate.class).setTemplateId(1L);
            return 1;
        }).when(mapper).insertOaSignTemplate(captor.capture());
        when(mapper.selectOaSignTemplateById(1L)).thenAnswer(invocation -> captor.getValue());

        OaSignTemplate template = new OaSignTemplate();
        template.setTemplateType(OaSignTemplateType.ONBOARD_LABOR_CONTRACT);
        template.setTemplateName("劳动合同");
        template.setFileUrl("/profile/labor.docx");
        template.setCompanySealRequired("N");

        OaSignTemplate saved = service.saveTemplate(template);

        assertThat(saved.getEmploymentType()).isEqualTo("劳动合同");
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("laborOnlyOnboardingTemplateTypes")
    @DisplayName("劳动关系专用入职材料不得落入劳务包")
    void shouldNormalizeLaborOnlyOnboardingTemplateRules(String templateType) throws Exception
    {
        OaSignTemplateServiceImpl service = templateService();
        OaSignTemplateMapper mapper = (OaSignTemplateMapper) ReflectionTestUtils.getField(service, "templateMapper");
        ArgumentCaptor<OaSignTemplate> captor = ArgumentCaptor.forClass(OaSignTemplate.class);
        org.mockito.Mockito.doAnswer(invocation -> {
            invocation.getArgument(0, OaSignTemplate.class).setTemplateId(11L);
            return 1;
        }).when(mapper).insertOaSignTemplate(captor.capture());
        when(mapper.selectOaSignTemplateById(11L)).thenAnswer(invocation -> captor.getValue());

        OaSignTemplate template = new OaSignTemplate();
        template.setTemplateType(templateType);
        template.setTemplateName(templateType);
        template.setFileUrl("/profile/labor-only.docx");
        template.setCompanySealRequired("N");

        OaSignTemplate saved = service.saveTemplate(template);

        assertThat(saved.getEmploymentType()).isEqualTo("劳动合同");
    }

    @Test
    @DisplayName("保存劳务模板时后端兜底用工类型并清理社保口径")
    void shouldNormalizeServiceContractTemplateRules() throws Exception
    {
        OaSignTemplateServiceImpl service = templateService();
        OaSignTemplateMapper mapper = (OaSignTemplateMapper) ReflectionTestUtils.getField(service, "templateMapper");
        ArgumentCaptor<OaSignTemplate> captor = ArgumentCaptor.forClass(OaSignTemplate.class);
        org.mockito.Mockito.doAnswer(invocation -> {
            invocation.getArgument(0, OaSignTemplate.class).setTemplateId(2L);
            return 1;
        }).when(mapper).insertOaSignTemplate(captor.capture());
        when(mapper.selectOaSignTemplateById(2L)).thenAnswer(invocation -> captor.getValue());

        OaSignTemplate template = new OaSignTemplate();
        template.setTemplateType(OaSignTemplateType.ONBOARD_SERVICE_CONTRACT);
        template.setTemplateName("劳务合同");
        template.setSocialType("有社保");
        template.setFileUrl("/profile/service.docx");
        template.setCompanySealRequired("N");

        OaSignTemplate saved = service.saveTemplate(template);

        assertThat(saved.getEmploymentType()).isEqualTo("劳务合同");
        assertThat(saved.getSocialType()).isBlank();
    }

    @Test
    @DisplayName("7级及以上保密协议不应被限定为仅劳动合同")
    void shouldKeepConfidentialTemplateAvailableToLaborAndServicePackages() throws Exception
    {
        OaSignTemplateServiceImpl service = templateService();
        OaSignTemplateMapper mapper = (OaSignTemplateMapper) ReflectionTestUtils.getField(
                service, "templateMapper");
        ArgumentCaptor<OaSignTemplate> captor = ArgumentCaptor.forClass(OaSignTemplate.class);
        org.mockito.Mockito.doAnswer(invocation -> {
            invocation.getArgument(0, OaSignTemplate.class).setTemplateId(21L);
            return 1;
        }).when(mapper).insertOaSignTemplate(captor.capture());
        when(mapper.selectOaSignTemplateById(21L)).thenAnswer(invocation -> captor.getValue());

        OaSignTemplate template = new OaSignTemplate();
        template.setTemplateType(OaSignTemplateType.ONBOARD_CONFIDENTIAL_NONCOMPETE);
        template.setTemplateName("保密与竞业限制协议");
        template.setEmploymentType("客户端伪造值");
        template.setPostLevelScope("7级及以上");
        template.setFileUrl("/profile/confidential.docx");
        template.setCompanySealRequired("N");

        OaSignTemplate saved = service.saveTemplate(template);

        assertThat(saved.getEmploymentType()).isBlank();
        assertThat(saved.getPostLevelScope()).isEqualTo("7级及以上");
    }

    private static Stream<Arguments> laborOnlyOnboardingTemplateTypes()
    {
        return Stream.of(
                Arguments.of(OaSignTemplateType.ONBOARD_OFFER_NOTICE),
                Arguments.of(OaSignTemplateType.ONBOARD_HANDBOOK),
                Arguments.of(OaSignTemplateType.ONBOARD_COMMITMENT),
                Arguments.of(OaSignTemplateType.ONBOARD_POST_DUTY),
                Arguments.of(OaSignTemplateType.ONBOARD_HANDBOOK_RECEIPT),
                Arguments.of(OaSignTemplateType.ONBOARD_SALARY_CONFIRM));
    }

    @Test
    @DisplayName("内部归档模板即使客户端伪造策略也强制对员工隐藏")
    void shouldForceInternalTemplateDeliveryPolicy()
    {
        OaSignTemplateServiceImpl service = templateService();
        OaSignTemplateMapper mapper = (OaSignTemplateMapper) ReflectionTestUtils.getField(service, "templateMapper");
        ArgumentCaptor<OaSignTemplate> captor = ArgumentCaptor.forClass(OaSignTemplate.class);
        org.mockito.Mockito.doAnswer(invocation -> {
            invocation.getArgument(0, OaSignTemplate.class).setTemplateId(3L);
            return 1;
        }).when(mapper).insertOaSignTemplate(captor.capture());
        when(mapper.selectOaSignTemplateById(3L)).thenAnswer(invocation -> captor.getValue());

        OaSignTemplate template = new OaSignTemplate();
        template.setTemplateType(OaSignTemplateType.ONBOARD_ARCHIVE_CATALOG);
        template.setTemplateName("员工档案目录");
        template.setFileUrl("/profile/archive.docx");
        template.setEmployeeVisible("Y");
        template.setReadConfirmationRequired("Y");
        template.setEmployeeSignRequired("Y");

        OaSignTemplate saved = service.saveTemplate(template);

        assertThat(saved.getEmployeeVisible()).isEqualTo("N");
        assertThat(saved.getReadConfirmationRequired()).isEqualTo("N");
        assertThat(saved.getEmployeeSignRequired()).isEqualTo("N");
        assertThat(saved.getCompanySealRequired()).isEqualTo("N");
        assertThat(saved.getCompanySealPositionJson()).isNull();
    }

    @Test
    @DisplayName("员工可见模板必须显式声明是否需要企业章")
    void shouldRequireExplicitCompanySealPolicyForVisibleTemplate()
    {
        OaSignTemplateServiceImpl service = templateService();
        OaSignTemplate input = templateWithId(1L);
        input.setCompanySealRequired(null);

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.saveTemplate(input))
                .isInstanceOf(com.erp.common.core.exception.ServiceException.class)
                .hasMessageContaining("企业章要求必须显式配置");
    }

    @Test
    @DisplayName("保存模板时固定且规范化印章定位策略")
    void shouldNormalizeCompanySealPlacementWhenSavingTemplate()
    {
        OaSignTemplateServiceImpl service = templateService();
        OaSignTemplateMapper mapper = (OaSignTemplateMapper) ReflectionTestUtils.getField(
                service, "templateMapper");
        when(mapper.updateOaSignTemplate(any())).thenReturn(1);
        when(mapper.selectOaSignTemplateById(1L)).thenAnswer(invocation -> templateWithId(1L));
        OaSignTemplate input = templateWithId(1L);
        input.setCompanySealRequired("Y");
        input.setCompanySealPositionJson("{ \"height\": 80, \"width\": 80, \"y\": 40,"
                + " \"x\": 420, \"pageNumber\": 1, \"mode\": \"PLACED\" }");
        when(mapper.selectOaSignTemplateById(1L)).thenAnswer(invocation -> input);

        OaSignTemplate saved = service.saveTemplate(input);

        assertThat(saved.getCompanySealPositionJson()).isEqualTo(
                "{\"mode\":\"PLACED\",\"pageNumber\":1,\"x\":420.0,\"y\":40.0,"
                        + "\"width\":80.0,\"height\":80.0}");
        assertThat(saved.getSignaturePositionJson()).isEqualTo(
                OaSignPlacementPolicyService.APPENDED_CONFIRMATION_PAGE);
    }

    @Test
    @DisplayName("保存模板时显式冻结企业章追加确认页策略")
    void shouldFreezeAppendedCompanySealPolicyWhenSavingTemplate()
    {
        OaSignTemplateServiceImpl service = templateService();
        OaSignTemplateMapper mapper = (OaSignTemplateMapper) ReflectionTestUtils.getField(
                service, "templateMapper");
        when(mapper.updateOaSignTemplate(any())).thenReturn(1);
        OaSignTemplate input = templateWithId(1L);
        input.setCompanySealRequired("Y");
        input.setCompanySealPositionJson("{ \"mode\" : \"APPENDED_CONFIRMATION_PAGE\" }");
        when(mapper.selectOaSignTemplateById(1L)).thenAnswer(invocation -> input);

        OaSignTemplate saved = service.saveTemplate(input);

        assertThat(saved.getSignaturePositionJson())
                .isEqualTo(OaSignPlacementPolicyService.APPENDED_CONFIRMATION_PAGE);
        assertThat(saved.getCompanySealPositionJson())
                .isEqualTo(OaSignPlacementPolicyService.APPENDED_CONFIRMATION_PAGE);
    }

    @Test
    @DisplayName("新增模板时无条件用服务端文件摘要覆盖客户端值")
    void shouldOverwriteClientHashWhenCreatingTemplate()
    {
        OaSignTemplateServiceImpl service = templateService();
        OaSignTemplateMapper mapper = (OaSignTemplateMapper) ReflectionTestUtils.getField(service, "templateMapper");
        OaSignDocumentService documentService = (OaSignDocumentService) ReflectionTestUtils.getField(
                service, "documentService");
        when(documentService.calculateFileUrlSha256("/profile/labor.docx")).thenReturn("server-hash");
        org.mockito.Mockito.doAnswer(invocation -> {
            invocation.getArgument(0, OaSignTemplate.class).setTemplateId(1L);
            return 1;
        }).when(mapper).insertOaSignTemplate(any());
        when(mapper.selectOaSignTemplateById(1L)).thenAnswer(invocation -> templateWithId(1L));
        OaSignTemplate input = templateWithId(null);
        input.setFileHash("client-forged-hash");

        service.saveTemplate(input);

        assertThat(input.getFileHash()).isEqualTo("server-hash");
        verify(documentService).calculateFileUrlSha256("/profile/labor.docx");
    }

    @Test
    @DisplayName("新增模板即使客户端请求启用也必须先以停用状态保存")
    void shouldForceNewTemplateDisabledBeforeReview()
    {
        OaSignTemplateServiceImpl service = templateService();
        OaSignTemplateMapper mapper = (OaSignTemplateMapper) ReflectionTestUtils.getField(
                service, "templateMapper");
        org.mockito.Mockito.doAnswer(invocation -> {
            invocation.getArgument(0, OaSignTemplate.class).setTemplateId(1L);
            return 1;
        }).when(mapper).insertOaSignTemplate(any());
        when(mapper.selectOaSignTemplateById(1L)).thenAnswer(invocation -> templateWithId(1L));
        OaSignTemplate input = templateWithId(null);
        input.setStatus("0");

        service.saveTemplate(input);

        assertThat(input.getStatus()).isEqualTo("1");
    }

    @Test
    @DisplayName("模板状态不能由客户端写入未知值")
    void shouldRejectUnknownTemplateStatus()
    {
        OaSignTemplateServiceImpl service = templateService();
        OaSignTemplate input = templateWithId(1L);
        input.setStatus("enabled");

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.saveTemplate(input))
                .isInstanceOf(com.erp.common.core.exception.ServiceException.class)
                .hasMessageContaining("模板状态");
    }

    @Test
    @DisplayName("更新模板时同样无条件用服务端文件摘要覆盖客户端值")
    void shouldOverwriteClientHashWhenUpdatingTemplate()
    {
        OaSignTemplateServiceImpl service = templateService();
        OaSignTemplateMapper mapper = (OaSignTemplateMapper) ReflectionTestUtils.getField(service, "templateMapper");
        OaSignDocumentService documentService = (OaSignDocumentService) ReflectionTestUtils.getField(
                service, "documentService");
        when(documentService.calculateFileUrlSha256("/profile/labor.docx")).thenReturn("updated-server-hash");
        when(mapper.updateOaSignTemplate(any())).thenReturn(1);
        when(mapper.selectOaSignTemplateById(1L)).thenAnswer(invocation -> templateWithId(1L));
        OaSignTemplate input = templateWithId(1L);
        input.setFileHash("client-forged-hash");

        service.saveTemplate(input);

        assertThat(input.getFileHash()).isEqualTo("updated-server-hash");
        verify(documentService).calculateFileUrlSha256("/profile/labor.docx");
    }

    @Test
    @DisplayName("匹配模板时应按岗位等级范围过滤")
    void shouldFilterMatchedTemplatesByPostLevelRange() throws Exception
    {
        OaSignTemplateServiceImpl service = templateService();
        OaSignTemplateMapper mapper = (OaSignTemplateMapper) ReflectionTestUtils.getField(service, "templateMapper");
        when(mapper.selectMatchedActiveTemplates(any())).thenReturn(Arrays.asList(
                template("通用模板", ""),
                template("2-4级职责", "2-4"),
                template("5-6级职责", "5-6"),
                template("7和8级协议", "7、8")));
        OaSignPackage signPackage = new OaSignPackage();
        signPackage.setPostLevelSnapshot("3");

        assertThat(service.matchTemplates(signPackage))
                .extracting(OaSignTemplate::getTemplateName)
                .containsExactly("通用模板", "2-4级职责");
    }

    @Test
    @DisplayName("岗位等级范围应支持中文顿号和逗号枚举")
    void shouldSupportDelimitedPostLevelScope() throws Exception
    {
        OaSignTemplateServiceImpl service = templateService();
        OaSignTemplateMapper mapper = (OaSignTemplateMapper) ReflectionTestUtils.getField(service, "templateMapper");
        when(mapper.selectMatchedActiveTemplates(any())).thenReturn(Arrays.asList(
                template("7和8级协议", "7、8"),
                template("5或6级职责", "5,6")));
        OaSignPackage signPackage = new OaSignPackage();
        signPackage.setPostLevelSnapshot("8");

        assertThat(service.matchTemplates(signPackage))
                .extracting(OaSignTemplate::getTemplateName)
                .containsExactly("7和8级协议");
    }

    @Test
    @DisplayName("岗位等级范围应支持7级及以上")
    void shouldSupportInclusiveMinimumPostLevelScope()
    {
        assertThat(OaSignTemplateServiceImpl.matchesPostLevelScope("7级及以上", "7")).isTrue();
        assertThat(OaSignTemplateServiceImpl.matchesPostLevelScope("7级及以上", "9")).isTrue();
        assertThat(OaSignTemplateServiceImpl.matchesPostLevelScope("7级及以上", "6")).isFalse();
    }

    private OaSignTemplateServiceImpl templateService()
    {
        OaSignTemplateMapper mapper = mock(OaSignTemplateMapper.class);
        OaSignDocumentService documentService = mock(OaSignDocumentService.class);
        when(documentService.calculateFileUrlSha256(any())).thenReturn("hash");
        OaSignTemplateServiceImpl service = new OaSignTemplateServiceImpl();
        ReflectionTestUtils.setField(service, "templateMapper", mapper);
        ReflectionTestUtils.setField(service, "documentService", documentService);
        ReflectionTestUtils.setField(service, "placementPolicyService",
                new OaSignPlacementPolicyService());
        return service;
    }

    private OaSignTemplate template(String name, String postLevelScope)
    {
        OaSignTemplate template = new OaSignTemplate();
        template.setTemplateName(name);
        template.setPostLevelScope(postLevelScope);
        return template;
    }

    private OaSignTemplate templateWithId(Long templateId)
    {
        OaSignTemplate template = new OaSignTemplate();
        template.setTemplateId(templateId);
        template.setTemplateType(OaSignTemplateType.ONBOARD_LABOR_CONTRACT);
        template.setTemplateName("劳动合同");
        template.setFileUrl("/profile/labor.docx");
        template.setCompanySealRequired("N");
        return template;
    }

    private static Stream<Arguments> regularizationTemplateTypes()
    {
        return Stream.of(
                Arguments.of("REGULARIZE_CONFIRMATION",
                        "employeeName,employeeIdCard,actualRegularizationDate,postName,postLevel,signDate"),
                Arguments.of("REGULARIZE_POST_DUTY",
                        "employeeName,postName,postLevel,actualRegularizationDate,signDate"),
                Arguments.of("REGULARIZE_SALARY_CONFIRM",
                        "employeeName,employeeIdCard,actualRegularizationDate,baseSalary,postSalary,"
                                + "fieldAllowance,performanceSalary,salaryTotal,salaryVersion,signDate"));
    }

    private static Stream<Arguments> renewalTemplateTypes()
    {
        return Stream.of(
                Arguments.of(OaSignTemplateType.RENEWAL_LABOR_CONTRACT, "劳动合同",
                        "employeeName,employeeIdCard,employeePhone,employeeAddress,"
                                + "companyName,previousContractEndDate,previousEmploymentType,"
                                + "previousRenewalCount,renewalCount,contractStartDate,contractEndDate,"
                                + "postName,baseSalary,signDate"),
                Arguments.of(OaSignTemplateType.RENEWAL_SERVICE_CONTRACT, "劳务合同",
                        "employeeName,employeeIdCard,employeePhone,employeeAddress,companyName,"
                                + "previousContractEndDate,previousEmploymentType,previousRenewalCount,"
                                + "renewalCount,servicePersonType,contractStartDate,contractEndDate,"
                                + "postName,baseSalary,signDate"),
                Arguments.of(OaSignTemplateType.RENEWAL_SALARY_CONFIRM, null,
                        "employeeName,employeeIdCard,companyName,previousRenewalCount,renewalCount,"
                                + "contractStartDate,contractEndDate,baseSalary,postSalary,fieldAllowance,"
                                + "performanceSalary,salaryTotal,salaryVersion,signDate"));
    }
}
