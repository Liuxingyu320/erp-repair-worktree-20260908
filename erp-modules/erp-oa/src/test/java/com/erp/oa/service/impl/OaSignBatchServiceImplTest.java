package com.erp.oa.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import jakarta.validation.Validation;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;
import com.erp.common.core.constant.SecurityConstants;
import com.erp.common.core.domain.R;
import com.erp.common.core.exception.ServiceException;
import com.erp.common.security.shop.ShopScopeService;
import com.erp.oa.constant.OaSignPackageStatus;
import com.erp.oa.domain.OaSignPackage;
import com.erp.oa.domain.OaSignPlan;
import com.erp.oa.domain.OaSignTemplate;
import com.erp.oa.domain.dto.OaSignBatchCreateDraftsRequest;
import com.erp.oa.domain.dto.OaSignBatchCreateDraftsResult;
import com.erp.oa.domain.dto.OaSignBatchPreviewRequest;
import com.erp.oa.domain.dto.OaSignBatchPreviewRow;
import com.erp.oa.domain.dto.OaSignBatchTemplateOption;
import com.erp.oa.mapper.OaSignPackageMapper;
import com.erp.oa.mapper.OaSignPlanMapper;
import com.erp.oa.service.IOaSignPackageService;
import com.erp.oa.service.IOaSignPlanService;
import com.erp.system.api.RemoteUserService;
import com.erp.system.api.domain.SignCandidateUser;
import com.erp.system.api.domain.SignCandidateUserQuery;

@DisplayName("员工签约批量服务")
class OaSignBatchServiceImplTest
{
    @Test
    @DisplayName("批量预览按方案岗位获取候选员工并标记缺失字段")
    void shouldPreviewCandidatesByPlanPostAndMarkMissingFields()
    {
        OaSignBatchServiceImpl service = batchService();
        IOaSignPlanService planService = (IOaSignPlanService) ReflectionTestUtils.getField(service, "planService");
        OaSignPlanMapper planMapper = (OaSignPlanMapper) ReflectionTestUtils.getField(service, "planMapper");
        RemoteUserService remoteUserService = (RemoteUserService) ReflectionTestUtils.getField(service, "remoteUserService");
        ArgumentCaptor<SignCandidateUserQuery> queryCaptor = ArgumentCaptor.forClass(SignCandidateUserQuery.class);

        OaSignPlan plan = enabledPlan();
        plan.setPostName("项目总监");
        when(planService.getPlanDetail(20L, 1171L)).thenReturn(plan);
        when(planMapper.selectActiveTemplatesByPlanId(20L)).thenReturn(Collections.singletonList(template(10L)));
        SignCandidateUser incompleteCandidate = candidate(960L, "zhangsan", "张三", "13800000000");
        incompleteCandidate.setIdNumber(null);
        when(remoteUserService.listSignCandidates(queryCaptor.capture(), eq(SecurityConstants.INNER)))
                .thenReturn(R.ok(Collections.singletonList(incompleteCandidate)));

        OaSignBatchPreviewRequest request = new OaSignBatchPreviewRequest();
        request.setPlanId(20L);
        request.setPostName("其它岗位");
        request.setDeptId(9999L);
        request.setKeyword("张");

        List<OaSignBatchPreviewRow> rows = service.preview(request, 1171L);

        assertThat(queryCaptor.getValue().getPostName()).isEqualTo("项目总监");
        assertThat(queryCaptor.getValue().getDeptId()).isEqualTo(1171L);
        assertThat(queryCaptor.getValue().getKeyword()).isEqualTo("张");
        assertThat(queryCaptor.getValue().getLimit()).isEqualTo(500);
        assertThat(rows).hasSize(1);
        OaSignBatchPreviewRow row = rows.get(0);
        assertThat(row.getSelected()).isTrue();
        assertThat(row.getEmployeeId()).isEqualTo(960L);
        assertThat(row.getEmployeeNameSnapshot()).isEqualTo("张三");
        assertThat(row.getEmployeePhoneSnapshot()).isEqualTo("13800000000");
        assertThat(row.getPostNameSnapshot()).isEqualTo("项目总监");
        assertThat(row.getScenario()).isEqualTo("onboard");
        assertThat(row.getTemplates()).extracting(OaSignBatchTemplateOption::getTemplateId).containsExactly(10L);
        assertThat(row.getTemplates().get(0).getClass()).isEqualTo(OaSignBatchTemplateOption.class);
        assertThat(Arrays.stream(row.getTemplates().get(0).getClass().getDeclaredFields()).map(Field::getName))
                .contains("templateId", "templateType", "templateName", "sortOrder")
                .doesNotContain("fileUrl", "fileHash", "requiredPlaceholders", "optionalPlaceholders");
        assertThat(row.getMissingFields()).contains("身份证号");
        assertThat(row.getCreatable()).isFalse();
        assertThat(row.getSkipReason()).isEqualTo("资料不完整");
    }

    @Test
    @DisplayName("批量创建只为资料完整员工生成草稿")
    void shouldCreateDraftsOnlyForCompleteRows()
    {
        OaSignBatchServiceImpl service = batchService();
        IOaSignPlanService planService = (IOaSignPlanService) ReflectionTestUtils.getField(service, "planService");
        OaSignPlanMapper planMapper = (OaSignPlanMapper) ReflectionTestUtils.getField(service, "planMapper");
        RemoteUserService remoteUserService = (RemoteUserService) ReflectionTestUtils.getField(service, "remoteUserService");
        IOaSignPackageService packageService = (IOaSignPackageService) ReflectionTestUtils.getField(service, "packageService");
        ArgumentCaptor<OaSignPackage> packageCaptor = ArgumentCaptor.forClass(OaSignPackage.class);
        ArgumentCaptor<SignCandidateUserQuery> queryCaptor = ArgumentCaptor.forClass(SignCandidateUserQuery.class);

        when(planService.getPlanDetail(20L, 1171L)).thenReturn(enabledPlan());
        when(planMapper.selectActiveTemplatesByPlanId(20L)).thenReturn(Collections.singletonList(template(10L)));
        SignCandidateUser completeCandidate = candidate(960L, "zhangsan", "张三", "13800000000");
        SignCandidateUser incompleteCandidate = candidate(961L, "lisi", "李四", "13900000000");
        incompleteCandidate.setIdNumber(null);
        when(remoteUserService.listSignCandidates(queryCaptor.capture(), eq(SecurityConstants.INNER)))
                .thenReturn(R.ok(Arrays.asList(completeCandidate, incompleteCandidate)));
        when(packageService.createPackage(packageCaptor.capture(), eq(1171L))).thenReturn(createdPackage(8001L));

        OaSignBatchCreateDraftsRequest request = new OaSignBatchCreateDraftsRequest();
        request.setPlanId(20L);
        request.setRows(Arrays.asList(completeRow(960L), incompleteRow(961L)));

        OaSignBatchCreateDraftsResult result = service.createDrafts(request, 1171L);

        assertThat(queryCaptor.getValue().getDeptId()).isEqualTo(1171L);
        assertThat(queryCaptor.getValue().getPostName()).isEqualTo("项目总监");
        assertThat(queryCaptor.getValue().getUserIds()).containsExactly(960L, 961L);
        assertThat(result.getCreatedCount()).isEqualTo(1);
        assertThat(result.getSkippedCount()).isEqualTo(1);
        assertThat(result.getPackageIds()).containsExactly(8001L);
        assertThat(result.getSkippedRows()).extracting(OaSignBatchPreviewRow::getEmployeeId).containsExactly(961L);
        assertThat(result.getSkippedRows().get(0).getSkipReason()).isEqualTo("资料不完整");
        OaSignPackage draft = packageCaptor.getValue();
        assertThat(draft.getStatus()).isEqualTo(OaSignPackageStatus.DRAFT);
        assertThat(draft.getSourcePlanId()).isEqualTo(20L);
        assertThat(draft.getSourcePlanName()).isEqualTo("项目总监方案");
        assertThat(draft.getShopDeptId()).isEqualTo(1171L);
        assertThat(draft.getLegalEntityIdSnapshot()).isNull();
        assertThat(draft.getLegalEntityNameSnapshot()).isNull();
        assertThat(draft.getEmployeeId()).isEqualTo(960L);
        assertThat(draft.getEmployeeNameSnapshot()).isEqualTo("张三");
        assertThat(draft.getScenario()).isEqualTo("onboard");
        assertThat(draft.getEmploymentType()).isEqualTo("劳动合同");
        assertThat(draft.getContractTermCodeSnapshot()).isEqualTo("FIXED_TERM");
        assertThat(draft.getBaseSalary()).isEqualByComparingTo("10000.00");
    }

    @Test
    @DisplayName("批量预览自动填充员工档案并匹配入职签约包")
    void shouldFillProfileFieldsAndResolveOnboardingPackageMatch()
    {
        OaSignBatchServiceImpl service = batchService();
        IOaSignPlanService planService = (IOaSignPlanService) ReflectionTestUtils.getField(service, "planService");
        OaSignPlanMapper planMapper = (OaSignPlanMapper) ReflectionTestUtils.getField(service, "planMapper");
        RemoteUserService remoteUserService = (RemoteUserService) ReflectionTestUtils.getField(service, "remoteUserService");

        SignCandidateUser candidate = candidate(960L, "zhangsan", "张三", "13800000000");
        candidate.setIdNumber("110101199001011234");
        candidate.setCurrentAddress("杭州市西湖区文三路1号");
        candidate.setJobGrade("7级");
        candidate.setContractType("LABOR_CONTRACT");
        candidate.setContractTerm("OPEN_ENDED");
        candidate.setSocialType("SOCIAL_INSURED");
        candidate.setEntryDate("2026-07-10");
        candidate.setContractStartDate("2026-07-10");
        candidate.setContractEndDate("2029-07-09");
        candidate.setProbationStartDate("2026-07-10");
        candidate.setProbationEndDate("2026-10-09");
        candidate.setBaseSalary(new BigDecimal("9000.00"));
        candidate.setPostSalary(new BigDecimal("3000.00"));
        candidate.setFieldAllowance(new BigDecimal("500.00"));
        candidate.setPerformanceSalary(new BigDecimal("1500.00"));
        candidate.setSalaryTotal(new BigDecimal("14000.00"));
        candidate.setSalaryVersion("PROFILE-2026A");

        when(planService.getPlanDetail(20L, 1171L)).thenReturn(enabledPlan());
        when(planMapper.selectActiveTemplatesByPlanId(20L)).thenReturn(Collections.singletonList(template(10L)));
        when(remoteUserService.listSignCandidates(any(), eq(SecurityConstants.INNER)))
                .thenReturn(R.ok(Collections.singletonList(candidate)));

        OaSignBatchPreviewRequest request = new OaSignBatchPreviewRequest();
        request.setPlanId(20L);

        List<OaSignBatchPreviewRow> rows = service.preview(request, 1171L);

        OaSignBatchPreviewRow row = rows.get(0);
        assertThat(row.getEmployeeIdCardSnapshot()).isEqualTo("110101199001011234");
        assertThat(row.getEmployeeAddressSnapshot()).isEqualTo("杭州市西湖区文三路1号");
        assertThat(row.getPostLevelSnapshot()).isEqualTo("7级");
        assertThat(row.getEmploymentType()).isEqualTo("LABOR_CONTRACT");
        assertThat(row.getContractTermCodeSnapshot()).isEqualTo("OPEN_ENDED");
        assertThat(row.getSocialType()).isEqualTo("SOCIAL_INSURED");
        assertThat(row.getEntryDate()).isEqualTo("2026-07-10");
        assertThat(row.getContractStartDate()).isEqualTo("2026-07-10");
        assertThat(row.getContractEndDate()).isEqualTo("2029-07-09");
        assertThat(row.getProbationStartDate()).isEqualTo("2026-07-10");
        assertThat(row.getProbationEndDate()).isEqualTo("2026-10-09");
        assertThat(row.getPerformanceSalary()).isEqualByComparingTo("1500.00");
        assertThat(row.getSalaryTotal()).isEqualByComparingTo("14000.00");
        assertThat(row.getSalaryVersion()).isEqualTo("PROFILE-2026A");
        assertThat(row.getPackageMatchCode()).isEqualTo("A3");
        assertThat(row.getPackageMatchName()).isEqualTo("A3 劳动合同有社保 7-8级");
        assertThat(row.getMissingFields()).isEmpty();
        assertThat(row.getCreatable()).isTrue();
    }

    @Test
    @DisplayName("批量预览按模板阻断缺失联系住址和绩效工资")
    void shouldRequireAddressAndPerformanceSalaryWhenTemplateUsesThem()
    {
        OaSignBatchServiceImpl service = batchService();
        IOaSignPlanService planService = (IOaSignPlanService) ReflectionTestUtils.getField(service, "planService");
        OaSignPlanMapper planMapper = (OaSignPlanMapper) ReflectionTestUtils.getField(service, "planMapper");
        RemoteUserService remoteUserService = (RemoteUserService) ReflectionTestUtils.getField(service, "remoteUserService");

        SignCandidateUser candidate = candidate(960L, "zhangsan", "张三", "13800000000");
        candidate.setIdNumber("110101199001011234");
        candidate.setJobGrade("5级");
        candidate.setContractType("劳动合同");
        candidate.setSocialType("有社保");
        candidate.setEntryDate("2026-07-10");
        candidate.setContractStartDate("2026-07-10");
        candidate.setContractEndDate("2029-07-09");
        OaSignTemplate required = template(10L);
        required.setRequiredPlaceholders("employeeName,employeeAddress,performanceSalary");

        when(planService.getPlanDetail(20L, 1171L)).thenReturn(enabledPlan());
        when(planMapper.selectActiveTemplatesByPlanId(20L)).thenReturn(Collections.singletonList(required));
        when(remoteUserService.listSignCandidates(any(), eq(SecurityConstants.INNER)))
                .thenReturn(R.ok(Collections.singletonList(candidate)));

        OaSignBatchPreviewRequest request = new OaSignBatchPreviewRequest();
        request.setPlanId(20L);
        OaSignBatchPreviewRow row = service.preview(request, 1171L).get(0);

        assertThat(row.getCreatable()).isFalse();
        assertThat(row.getMissingFields()).contains(
                "联系住址", "绩效工资", "员工签约路由与所选方案不一致");
    }

    @Test
    @DisplayName("批量预览按劳务模板阻断缺失劳务人员类型和保险类型")
    void shouldRequireServiceAndInsuranceTypesWhenTemplateUsesThem()
    {
        OaSignBatchServiceImpl service = batchService();
        IOaSignPlanService planService = (IOaSignPlanService) ReflectionTestUtils.getField(service, "planService");
        OaSignPlanMapper planMapper = (OaSignPlanMapper) ReflectionTestUtils.getField(service, "planMapper");
        RemoteUserService remoteUserService = (RemoteUserService) ReflectionTestUtils.getField(service, "remoteUserService");

        OaSignPlan plan = enabledPlan();
        plan.setServicePersonType(null);
        plan.setInsuranceType(null);
        OaSignTemplate required = template(10L);
        required.setRequiredPlaceholders("employeeName,servicePersonType,insuranceType");
        when(planService.getPlanDetail(20L, 1171L)).thenReturn(plan);
        when(planMapper.selectActiveTemplatesByPlanId(20L)).thenReturn(Collections.singletonList(required));
        when(remoteUserService.listSignCandidates(any(), eq(SecurityConstants.INNER)))
                .thenReturn(R.ok(Collections.singletonList(
                        candidate(960L, "zhangsan", "张三", "13800000000"))));

        OaSignBatchPreviewRequest request = new OaSignBatchPreviewRequest();
        request.setPlanId(20L);
        OaSignBatchPreviewRow row = service.preview(request, 1171L).get(0);

        assertThat(row.getCreatable()).isFalse();
        assertThat(row.getMissingFields()).contains("劳务人员类型", "保险类型");
    }

    @Test
    @DisplayName("批量预览标记劳务合同有社保为不可自动匹配")
    void shouldRejectUnsupportedServiceContractWithSocialInsurance()
    {
        OaSignBatchServiceImpl service = batchService();
        IOaSignPlanService planService = (IOaSignPlanService) ReflectionTestUtils.getField(service, "planService");
        OaSignPlanMapper planMapper = (OaSignPlanMapper) ReflectionTestUtils.getField(service, "planMapper");
        RemoteUserService remoteUserService = (RemoteUserService) ReflectionTestUtils.getField(service, "remoteUserService");

        SignCandidateUser candidate = candidate(960L, "zhangsan", "张三", "13800000000");
        candidate.setIdNumber("110101199001011234");
        candidate.setCurrentAddress("杭州市西湖区文三路1号");
        candidate.setJobGrade("5级");
        candidate.setContractType("劳务合同");
        candidate.setSocialType("有社保");
        candidate.setEntryDate("2026-07-10");
        candidate.setContractStartDate("2026-07-10");
        candidate.setContractEndDate("2029-07-09");

        when(planService.getPlanDetail(20L, 1171L)).thenReturn(enabledPlan());
        when(planMapper.selectActiveTemplatesByPlanId(20L)).thenReturn(Collections.singletonList(template(10L)));
        when(remoteUserService.listSignCandidates(any(), eq(SecurityConstants.INNER)))
                .thenReturn(R.ok(Collections.singletonList(candidate)));

        OaSignBatchPreviewRequest request = new OaSignBatchPreviewRequest();
        request.setPlanId(20L);

        List<OaSignBatchPreviewRow> rows = service.preview(request, 1171L);

        OaSignBatchPreviewRow row = rows.get(0);
        assertThat(row.getPackageMatchCode()).isNull();
        assertThat(row.getPackageMatchName()).isEqualTo("不可自动匹配");
        assertThat(row.getCreatable()).isFalse();
        assertThat(row.getSkipReason()).isEqualTo("不可自动匹配");
        assertThat(row.getMissingFields()).contains("劳务合同+有社保暂不支持自动匹配");
    }

    @Test
    @DisplayName("入职批量匹配与生命周期规则使用同一套九宫格路由")
    void shouldMatchAllOnboardingRouteCodes()
    {
        OaSignBatchServiceImpl service = batchService();
        List<String[]> cases = List.of(
                new String[] { "LABOR_CONTRACT", "SOCIAL_INSURED", "2级", "A1" },
                new String[] { "劳动合同", "有社保", "5级", "A2" },
                new String[] { "LABOR_CONTRACT", "SOCIAL_INSURED", "8级", "A3" },
                new String[] { "劳动合同", "无社保", "3级", "A4" },
                new String[] { "LABOR_CONTRACT", "SOCIAL_UNINSURED", "6级", "A5" },
                new String[] { "劳动合同", "无社保", "7级", "A6" },
                new String[] { "SERVICE_CONTRACT", "SOCIAL_UNINSURED", "4级", "B1" },
                new String[] { "劳务合同", "无社保", "5级", "B2" },
                new String[] { "SERVICE_CONTRACT", "SOCIAL_UNINSURED", "8级", "B3" });

        for (String[] routeCase : cases)
        {
            OaSignBatchPreviewRow row = new OaSignBatchPreviewRow();
            row.setScenario("ONBOARD");
            row.setEmploymentType(routeCase[0]);
            row.setSocialType(routeCase[1]);
            row.setPostLevelSnapshot(routeCase[2]);

            String issue = ReflectionTestUtils.invokeMethod(
                    service, "resolveOnboardingPackageMatch", row);

            assertThat(issue).as(Arrays.toString(routeCase)).isNull();
            assertThat(row.getPackageMatchCode()).as(Arrays.toString(routeCase))
                    .isEqualTo(routeCase[3]);
        }
    }

    @Test
    @DisplayName("批量预览拒绝未知的非空合同和社保编码")
    void shouldRejectUnknownNonBlankProfileCodes()
    {
        OaSignBatchServiceImpl service = batchService();
        OaSignBatchPreviewRow unknownContract = new OaSignBatchPreviewRow();
        unknownContract.setScenario("onboard");
        unknownContract.setEmploymentType("TEMP_CONTRACT");
        unknownContract.setSocialType("SOCIAL_INSURED");
        unknownContract.setPostLevelSnapshot("P5");
        OaSignBatchPreviewRow unknownSocial = new OaSignBatchPreviewRow();
        unknownSocial.setScenario("ONBOARD");
        unknownSocial.setEmploymentType("LABOR_CONTRACT");
        unknownSocial.setSocialType("COMMERCIAL_ONLY");
        unknownSocial.setPostLevelSnapshot("5级");

        assertThat((String) ReflectionTestUtils.invokeMethod(
                service, "resolveOnboardingPackageMatch", unknownContract))
                .isEqualTo("合同类型只能是劳动合同或劳务合同");
        assertThat((String) ReflectionTestUtils.invokeMethod(
                service, "resolveOnboardingPackageMatch", unknownSocial))
                .isEqualTo("社保类型只能是有社保或无社保");
        assertThat(unknownContract.getPackageMatchName()).isEqualTo("不可自动匹配");
        assertThat(unknownSocial.getPackageMatchName()).isEqualTo("不可自动匹配");
    }

    @Test
    @DisplayName("批量预览不在首次发送前锁定员工或方案中的公司")
    void shouldDeferCandidateCompanyUntilAfterFirstSignature()
    {
        OaSignBatchServiceImpl service = batchService();
        IOaSignPlanService planService = (IOaSignPlanService) ReflectionTestUtils.getField(service, "planService");
        OaSignPlanMapper planMapper = (OaSignPlanMapper) ReflectionTestUtils.getField(service, "planMapper");
        RemoteUserService remoteUserService = (RemoteUserService) ReflectionTestUtils.getField(service, "remoteUserService");
        SignCandidateUser candidate = candidate(960L, "zhangsan", "张三", "13800000000");
        candidate.setLegalEntityId(999L);
        candidate.setLegalEntity("其他公司");
        candidate.setIdNumber("110101199001011234");
        when(planService.getPlanDetail(20L, 1171L)).thenReturn(enabledPlan());
        when(planMapper.selectActiveTemplatesByPlanId(20L)).thenReturn(Collections.singletonList(template(10L)));
        when(remoteUserService.listSignCandidates(any(), eq(SecurityConstants.INNER)))
                .thenReturn(R.ok(Collections.singletonList(candidate)));
        OaSignBatchPreviewRequest request = new OaSignBatchPreviewRequest();
        request.setPlanId(20L);

        OaSignBatchPreviewRow row = service.preview(request, 1171L).get(0);

        assertThat(row.getCreatable()).isTrue();
        assertThat(row.getMissingFields()).doesNotContain("员工法律主体与方案不一致");
    }

    @Test
    @DisplayName("批量创建跳过同员工同方案未关闭草稿")
    void shouldSkipOpenPackageForSameEmployeeAndPlan()
    {
        OaSignBatchServiceImpl service = batchService();
        IOaSignPlanService planService = (IOaSignPlanService) ReflectionTestUtils.getField(service, "planService");
        OaSignPlanMapper planMapper = (OaSignPlanMapper) ReflectionTestUtils.getField(service, "planMapper");
        OaSignPackageMapper packageMapper = (OaSignPackageMapper) ReflectionTestUtils.getField(service, "packageMapper");
        RemoteUserService remoteUserService = (RemoteUserService) ReflectionTestUtils.getField(service, "remoteUserService");
        IOaSignPackageService packageService = (IOaSignPackageService) ReflectionTestUtils.getField(service, "packageService");

        when(planService.getPlanDetail(20L, 1171L)).thenReturn(enabledPlan());
        when(planMapper.selectActiveTemplatesByPlanId(20L)).thenReturn(Collections.singletonList(template(10L)));
        when(remoteUserService.listSignCandidates(any(), eq(SecurityConstants.INNER)))
                .thenReturn(R.ok(Collections.singletonList(candidate(960L, "zhangsan", "张三", "13800000000"))));
        when(packageMapper.selectOpenPackageByEmployeeAndPlanForUpdate(960L, 20L, 1171L))
                .thenReturn(createdPackage(8001L));

        OaSignBatchCreateDraftsRequest request = new OaSignBatchCreateDraftsRequest();
        request.setPlanId(20L);
        request.setRows(Collections.singletonList(completeRow(960L)));

        OaSignBatchCreateDraftsResult result = service.createDrafts(request, 1171L);

        assertThat(result.getCreatedCount()).isZero();
        assertThat(result.getSkippedCount()).isEqualTo(1);
        assertThat(result.getSkippedRows().get(0).getSkipReason()).isEqualTo("同员工同方案已有未完成签约包");
        verify(packageService, org.mockito.Mockito.never()).createPackage(any(), any());
    }

    @Test
    @DisplayName("批量创建使用服务端候选员工身份字段")
    void shouldCreateDraftsWithServerCandidateIdentityFields()
    {
        OaSignBatchServiceImpl service = batchService();
        IOaSignPlanService planService = (IOaSignPlanService) ReflectionTestUtils.getField(service, "planService");
        OaSignPlanMapper planMapper = (OaSignPlanMapper) ReflectionTestUtils.getField(service, "planMapper");
        RemoteUserService remoteUserService = (RemoteUserService) ReflectionTestUtils.getField(service, "remoteUserService");
        IOaSignPackageService packageService = (IOaSignPackageService) ReflectionTestUtils.getField(service, "packageService");
        ArgumentCaptor<OaSignPackage> packageCaptor = ArgumentCaptor.forClass(OaSignPackage.class);

        SignCandidateUser realCandidate = candidate(960L, "server_name", "服务端姓名", "13911112222");
        realCandidate.setDeptId(1171L);
        realCandidate.setDeptName("方案门店");
        realCandidate.setPostNames("项目总监");
        when(planService.getPlanDetail(20L, 1171L)).thenReturn(enabledPlan());
        when(planMapper.selectActiveTemplatesByPlanId(20L)).thenReturn(Collections.singletonList(template(10L)));
        when(remoteUserService.listSignCandidates(any(), eq(SecurityConstants.INNER)))
                .thenReturn(R.ok(Collections.singletonList(realCandidate)));
        when(packageService.createPackage(packageCaptor.capture(), eq(1171L))).thenReturn(createdPackage(8001L));

        OaSignBatchPreviewRow spoofedRow = completeRow(960L);
        spoofedRow.setEmployeeNameSnapshot("前端伪造姓名");
        spoofedRow.setEmployeePhoneSnapshot("13000000000");
        spoofedRow.setDeptIdSnapshot(9999L);
        spoofedRow.setDeptNameSnapshot("其它门店");
        spoofedRow.setPostNameSnapshot("其它岗位");
        spoofedRow.setEmployeeIdCardSnapshot("前端伪造证件号");
        spoofedRow.setEmploymentType("劳务合同");
        spoofedRow.setSocialType("无社保");
        spoofedRow.setBaseSalary(new BigDecimal("1.00"));
        OaSignBatchCreateDraftsRequest request = new OaSignBatchCreateDraftsRequest();
        request.setPlanId(20L);
        request.setRows(Collections.singletonList(spoofedRow));

        OaSignBatchCreateDraftsResult result = service.createDrafts(request, 1171L);

        assertThat(result.getCreatedCount()).isEqualTo(1);
        OaSignPackage draft = packageCaptor.getValue();
        assertThat(draft.getEmployeeNameSnapshot()).isEqualTo("服务端姓名");
        assertThat(draft.getEmployeePhoneSnapshot()).isEqualTo("13911112222");
        assertThat(draft.getDeptIdSnapshot()).isEqualTo(1171L);
        assertThat(draft.getDeptNameSnapshot()).isEqualTo("方案门店");
        assertThat(draft.getPostNameSnapshot()).isEqualTo("项目总监");
        assertThat(draft.getEmployeeIdCardSnapshot()).isEqualTo("110101199001011234");
        assertThat(draft.getEmploymentType()).isEqualTo("劳动合同");
        assertThat(draft.getSocialType()).isEqualTo("有社保");
        assertThat(draft.getBaseSalary()).isEqualByComparingTo("10000.00");
    }

    @Test
    @DisplayName("批量创建候选员工不在方案岗位范围时跳过")
    void shouldSkipRowsMissingFromServerCandidateScope()
    {
        OaSignBatchServiceImpl service = batchService();
        IOaSignPlanService planService = (IOaSignPlanService) ReflectionTestUtils.getField(service, "planService");
        OaSignPlanMapper planMapper = (OaSignPlanMapper) ReflectionTestUtils.getField(service, "planMapper");
        RemoteUserService remoteUserService = (RemoteUserService) ReflectionTestUtils.getField(service, "remoteUserService");
        IOaSignPackageService packageService = (IOaSignPackageService) ReflectionTestUtils.getField(service, "packageService");

        when(planService.getPlanDetail(20L, 1171L)).thenReturn(enabledPlan());
        when(planMapper.selectActiveTemplatesByPlanId(20L)).thenReturn(Collections.singletonList(template(10L)));
        when(remoteUserService.listSignCandidates(any(), eq(SecurityConstants.INNER))).thenReturn(R.ok(Collections.emptyList()));

        OaSignBatchCreateDraftsRequest request = new OaSignBatchCreateDraftsRequest();
        request.setPlanId(20L);
        request.setRows(Collections.singletonList(completeRow(960L)));

        OaSignBatchCreateDraftsResult result = service.createDrafts(request, 1171L);

        assertThat(result.getCreatedCount()).isZero();
        assertThat(result.getSkippedCount()).isEqualTo(1);
        assertThat(result.getSkippedRows().get(0).getSkipReason()).isEqualTo("员工不在方案岗位范围");
        verify(packageService, org.mockito.Mockito.never()).createPackage(any(), any());
    }

    @Test
    @DisplayName("批量创建跳过同一批次重复员工")
    void shouldSkipDuplicateEmployeeInSameBatch()
    {
        OaSignBatchServiceImpl service = batchService();
        IOaSignPlanService planService = (IOaSignPlanService) ReflectionTestUtils.getField(service, "planService");
        OaSignPlanMapper planMapper = (OaSignPlanMapper) ReflectionTestUtils.getField(service, "planMapper");
        RemoteUserService remoteUserService = (RemoteUserService) ReflectionTestUtils.getField(service, "remoteUserService");
        IOaSignPackageService packageService = (IOaSignPackageService) ReflectionTestUtils.getField(service, "packageService");

        when(planService.getPlanDetail(20L, 1171L)).thenReturn(enabledPlan());
        when(planMapper.selectActiveTemplatesByPlanId(20L)).thenReturn(Collections.singletonList(template(10L)));
        when(remoteUserService.listSignCandidates(any(), eq(SecurityConstants.INNER)))
                .thenReturn(R.ok(Collections.singletonList(candidate(960L, "zhangsan", "张三", "13800000000"))));
        when(packageService.createPackage(any(), eq(1171L))).thenReturn(createdPackage(8001L));

        OaSignBatchCreateDraftsRequest request = new OaSignBatchCreateDraftsRequest();
        request.setPlanId(20L);
        request.setRows(Arrays.asList(completeRow(960L), completeRow(960L)));

        OaSignBatchCreateDraftsResult result = service.createDrafts(request, 1171L);

        assertThat(result.getCreatedCount()).isEqualTo(1);
        assertThat(result.getSkippedCount()).isEqualTo(1);
        assertThat(result.getSkippedRows().get(0).getSkipReason()).isEqualTo("同一批次重复员工");
        verify(packageService, org.mockito.Mockito.times(1)).createPackage(any(), eq(1171L));
    }

    @Test
    @DisplayName("批量创建校验草稿字段长度失败时跳过")
    void shouldSkipRowsWhenDraftPackageValidationFails()
    {
        OaSignBatchServiceImpl service = batchService();
        IOaSignPlanService planService = (IOaSignPlanService) ReflectionTestUtils.getField(service, "planService");
        OaSignPlanMapper planMapper = (OaSignPlanMapper) ReflectionTestUtils.getField(service, "planMapper");
        RemoteUserService remoteUserService = (RemoteUserService) ReflectionTestUtils.getField(service, "remoteUserService");
        IOaSignPackageService packageService = (IOaSignPackageService) ReflectionTestUtils.getField(service, "packageService");

        SignCandidateUser invalidCandidate = candidate(960L, "zhangsan", "超长姓名".repeat(20), "13800000000");
        when(planService.getPlanDetail(20L, 1171L)).thenReturn(enabledPlan());
        when(planMapper.selectActiveTemplatesByPlanId(20L)).thenReturn(Collections.singletonList(template(10L)));
        when(remoteUserService.listSignCandidates(any(), eq(SecurityConstants.INNER)))
                .thenReturn(R.ok(Collections.singletonList(invalidCandidate)));

        OaSignBatchCreateDraftsRequest request = new OaSignBatchCreateDraftsRequest();
        request.setPlanId(20L);
        request.setRows(Collections.singletonList(completeRow(960L)));

        OaSignBatchCreateDraftsResult result = service.createDrafts(request, 1171L);

        assertThat(result.getCreatedCount()).isZero();
        assertThat(result.getSkippedCount()).isEqualTo(1);
        assertThat(result.getSkippedRows().get(0).getSkipReason()).isEqualTo("资料格式不正确");
        assertThat(result.getSkippedRows().get(0).getMissingFields()).contains("员工姓名长度不能超过64个字符");
        verify(packageService, org.mockito.Mockito.never()).createPackage(any(), any());
    }

    @Test
    @DisplayName("批量预览在候选员工接口失败时抛出异常")
    void shouldRejectPreviewWhenRemoteCandidateServiceFails()
    {
        OaSignBatchServiceImpl service = batchService();
        IOaSignPlanService planService = (IOaSignPlanService) ReflectionTestUtils.getField(service, "planService");
        OaSignPlanMapper planMapper = (OaSignPlanMapper) ReflectionTestUtils.getField(service, "planMapper");
        RemoteUserService remoteUserService = (RemoteUserService) ReflectionTestUtils.getField(service, "remoteUserService");

        when(planService.getPlanDetail(20L, 1171L)).thenReturn(enabledPlan());
        when(planMapper.selectActiveTemplatesByPlanId(20L)).thenReturn(Collections.singletonList(template(10L)));
        when(remoteUserService.listSignCandidates(any(), eq(SecurityConstants.INNER))).thenReturn(R.fail("system down"));

        OaSignBatchPreviewRequest request = new OaSignBatchPreviewRequest();
        request.setPlanId(20L);

        assertThatThrownBy(() -> service.preview(request, 1171L))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("获取签约候选员工失败system down");
    }

    private OaSignBatchServiceImpl batchService()
    {
        OaSignBatchServiceImpl service = new OaSignBatchServiceImpl();
        ReflectionTestUtils.setField(service, "planService", mock(IOaSignPlanService.class));
        ReflectionTestUtils.setField(service, "planMapper", mock(OaSignPlanMapper.class));
        ReflectionTestUtils.setField(service, "packageMapper", mock(OaSignPackageMapper.class));
        ReflectionTestUtils.setField(service, "packageService", mock(IOaSignPackageService.class));
        ReflectionTestUtils.setField(service, "remoteUserService", mock(RemoteUserService.class));
        ShopScopeService shopScopeService = mock(ShopScopeService.class);
        when(shopScopeService.resolveRequiredShopDept(any())).thenAnswer(invocation -> invocation.getArgument(0));
        ReflectionTestUtils.setField(service, "shopScopeService", shopScopeService);
        ReflectionTestUtils.setField(service, "validator", Validation.buildDefaultValidatorFactory().getValidator());
        return service;
    }

    private OaSignPlan enabledPlan()
    {
        OaSignPlan plan = new OaSignPlan();
        plan.setPlanId(20L);
        plan.setPlanName("项目总监方案");
        plan.setShopDeptId(0L);
        plan.setLegalEntityId(301L);
        plan.setLegalEntityName("上海示例餐饮有限公司");
        plan.setStatus("0");
        plan.setScenario("onboard");
        plan.setPostName("项目总监");
        plan.setEmploymentType("劳动合同");
        plan.setSocialType("有社保");
        plan.setServicePersonType("普通");
        plan.setInsuranceType("五险一金");
        plan.setPostLevelSnapshot("7级");
        plan.setSalaryVersion("2026A");
        plan.setEntryDate("2026-07-10");
        plan.setContractStartDate("2026-07-10");
        plan.setContractEndDate("2029-07-09");
        plan.setProbationStartDate("2026-07-10");
        plan.setProbationEndDate("2026-10-09");
        plan.setBaseSalary(new BigDecimal("10000.00"));
        plan.setPostSalary(new BigDecimal("5000.00"));
        plan.setFieldAllowance(new BigDecimal("800.00"));
        plan.setSalaryTotal(new BigDecimal("15800.00"));
        return plan;
    }

    private OaSignTemplate template(Long templateId)
    {
        OaSignTemplate template = new OaSignTemplate();
        template.setTemplateId(templateId);
        template.setTemplateName("劳动合同");
        template.setTemplateType("onboard_labor_contract");
        template.setFileUrl("/profile/templates/labor.docx");
        template.setFileHash("hash-before");
        template.setRequiredPlaceholders("employeeName");
        return template;
    }

    private SignCandidateUser candidate(Long userId, String userName, String nickName, String phone)
    {
        SignCandidateUser user = new SignCandidateUser();
        user.setUserId(userId);
        user.setUserName(userName);
        user.setNickName(nickName);
        user.setPhonenumber(phone);
        user.setDeptId(300L);
        user.setDeptName("项目部");
        user.setPostNames("项目总监");
        user.setLegalEntityId(301L);
        user.setLegalEntity("上海示例餐饮有限公司");
        user.setIdNumber("110101199001011234");
        user.setContractTerm("FIXED_TERM");
        return user;
    }

    private OaSignBatchPreviewRow completeRow(Long employeeId)
    {
        OaSignBatchPreviewRow row = new OaSignBatchPreviewRow();
        row.setSelected(true);
        row.setEmployeeId(employeeId);
        row.setEmployeeNameSnapshot("张三");
        row.setEmployeePhoneSnapshot("13800000000");
        row.setEmployeeIdCardSnapshot("110101199001011234");
        row.setDeptIdSnapshot(300L);
        row.setDeptNameSnapshot("项目部");
        row.setPostNameSnapshot("项目总监");
        row.setPostLevelSnapshot("7级");
        row.setScenario("onboard");
        row.setEmploymentType("劳动合同");
        row.setContractTermCodeSnapshot("FIXED_TERM");
        row.setSocialType("有社保");
        row.setServicePersonType("普通");
        row.setInsuranceType("五险一金");
        row.setSalaryVersion("2026A");
        row.setEntryDate("2026-07-10");
        row.setContractStartDate("2026-07-10");
        row.setContractEndDate("2029-07-09");
        row.setProbationStartDate("2026-07-10");
        row.setProbationEndDate("2026-10-09");
        row.setBaseSalary(new BigDecimal("10000.00"));
        row.setPostSalary(new BigDecimal("5000.00"));
        row.setFieldAllowance(new BigDecimal("800.00"));
        row.setSalaryTotal(new BigDecimal("15800.00"));
        return row;
    }

    private OaSignBatchPreviewRow incompleteRow(Long employeeId)
    {
        OaSignBatchPreviewRow row = completeRow(employeeId);
        row.setEmployeeIdCardSnapshot(null);
        return row;
    }

    private OaSignPackage createdPackage(Long packageId)
    {
        OaSignPackage signPackage = new OaSignPackage();
        signPackage.setPackageId(packageId);
        return signPackage;
    }
}
