package com.erp.oa.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.erp.common.core.exception.ServiceException;
import com.erp.common.security.shop.ShopScopeService;
import com.erp.oa.api.domain.HrEmployeeSigningSnapshot;
import com.erp.oa.constant.OaSignTemplateType;
import com.erp.oa.domain.*;
import com.erp.oa.mapper.*;

class OaTransferSalaryTemplateSelectionTest
{
    private final ObjectMapper json = new ObjectMapper().findAndRegisterModules();
    private OaSignPackageServiceImpl service;
    private OaSignPlanVersionMapper versions;
    private OaSignTaskMapper tasks;
    private OaSignPackage signPackage;
    private OaSignTask task;

    @BeforeEach void setUp() throws Exception
    {
        service = spy(new OaSignPackageServiceImpl());
        versions = mock(OaSignPlanVersionMapper.class); tasks = mock(OaSignTaskMapper.class);
        ReflectionTestUtils.setField(service, "planVersionMapper", versions);
        ReflectionTestUtils.setField(service, "taskMapper", tasks);
        signPackage = new OaSignPackage();
        signPackage.setPackageId(100L); signPackage.setTaskId(200L); signPackage.setEmployeeId(9L);
        signPackage.setPlanVersionId(300L); signPackage.setScenario("TRANSFER");
        signPackage.setBaseSalary(new BigDecimal("5000.00")); signPackage.setPostSalary(new BigDecimal("1000.00"));
        signPackage.setFieldAllowance(BigDecimal.ZERO); signPackage.setPerformanceSalary(BigDecimal.ZERO);
        signPackage.setSalaryTotal(new BigDecimal("6000.00"));
        task = new OaSignTask();
        task.setTaskId(200L); task.setPackageId(100L); task.setEmployeeId(9L); task.setScenario("TRANSFER");
        task.setBeforeSnapshotJson(json.writeValueAsString(snapshot("5000.0", "V1")));
        task.setAfterSnapshotJson(json.writeValueAsString(snapshot("5000.00", "V2")));
        when(tasks.selectOaSignTaskById(200L)).thenReturn(task);
        when(versions.selectTemplatesByVersionId(300L)).thenReturn(List.of(
                template(OaSignTemplateType.TRANSFER_CONFIRMATION), template(OaSignTemplateType.TRANSFER_POST_DUTY),
                template(OaSignTemplateType.TRANSFER_SALARY_CONFIRM)));
    }

    @Test void mixedPlanKeepsOtherDocumentsWithoutSalaryChange()
    {
        assertThat(selected()).extracting(OaSignTemplate::getTemplateType)
                .containsExactly(OaSignTemplateType.TRANSFER_CONFIRMATION, OaSignTemplateType.TRANSFER_POST_DUTY);
        verify(tasks).selectOaSignTaskById(200L);
    }

    @Test void salaryOnlyPlanCannotReintroduceAnUnchangedSalaryDocument()
    {
        when(versions.selectTemplatesByVersionId(300L)).thenReturn(List.of(template(OaSignTemplateType.TRANSFER_SALARY_CONFIRM)));
        assertThat(selected()).isEmpty();
    }

    @Test void componentRedistributionCountsEvenWhenTotalIsUnchanged() throws Exception
    {
        HrEmployeeSigningSnapshot after = snapshot("5100.00", "V1");
        after.setPostSalary(new BigDecimal("900.00")); after.setSalaryTotal(new BigDecimal("6000.00"));
        task.setAfterSnapshotJson(json.writeValueAsString(after));
        signPackage.setBaseSalary(after.getBaseSalary()); signPackage.setPostSalary(after.getPostSalary());
        assertThat(selected()).extracting(OaSignTemplate::getTemplateType).contains(OaSignTemplateType.TRANSFER_SALARY_CONFIRM);
    }

    @Test void missingTaskCannotAuthorizeSalaryTemplate()
    {
        when(tasks.selectOaSignTaskById(200L)).thenReturn(null);
        assertThatThrownBy(this::selected).isInstanceOf(ServiceException.class).hasMessageContaining("冻结快照暂未核实");
    }

    @Test void mismatchedTaskOrEmployeeCannotAuthorizeSalaryTemplate() throws Exception
    {
        task.setPackageId(999L);
        assertThatThrownBy(this::selected).isInstanceOf(ServiceException.class);
        task.setPackageId(100L); task.setEmployeeId(99L);
        assertThatThrownBy(this::selected).isInstanceOf(ServiceException.class);
        task.setEmployeeId(9L);
        HrEmployeeSigningSnapshot forged = snapshot("6000.00", "V2"); forged.setEmployeeId(99L);
        task.setAfterSnapshotJson(json.writeValueAsString(forged));
        assertThatThrownBy(this::selected).isInstanceOf(ServiceException.class);
    }

    @Test void missingMalformedOrIncompleteSnapshotsCannotAuthorizeSalaryTemplate() throws Exception
    {
        for (String snapshot : new String[] { null, "{}", "not-json", "null" })
        {
            task.setBeforeSnapshotJson(snapshot);
            assertThatThrownBy(this::selected).isInstanceOf(ServiceException.class);
        }
    }

    @Test void packageAmountsMustStillMatchTheFrozenTask()
    {
        signPackage.setBaseSalary(new BigDecimal("6000.00")); signPackage.setSalaryTotal(new BigDecimal("7000.00"));
        assertThatThrownBy(this::selected).isInstanceOf(ServiceException.class);
    }

    @Test void nonSalaryDocumentsDoNotRequireSalaryTaskEvidence()
    {
        when(versions.selectTemplatesByVersionId(300L)).thenReturn(List.of(template(OaSignTemplateType.TRANSFER_CONFIRMATION)));
        signPackage.setTaskId(null);
        assertThat(selected()).hasSize(1);
        verifyNoInteractions(tasks);
    }

    @Test void preparedUnchangedSalaryDocumentIsRejectedBeforeSendWrites()
    {
        OaSignPackageMapper packages = preparedSending();
        assertThatThrownBy(this::sendPrepared).isInstanceOf(ServiceException.class)
                .hasMessageContaining("实际工资未变化");
        verify(packages, never()).markSentWithVersion(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test void preparedActualAdjustmentSendsTheFrozenFilesWithoutRegeneration() throws Exception
    {
        OaSignPackageMapper packages = preparedSending();
        task.setBeforeSnapshotJson(json.writeValueAsString(snapshot("4000.00", "V1")));
        assertThat(sendPrepared()).isSameAs(signPackage);
        verify(packages).markSentWithVersion(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any());
        verifyNoInteractions((OaSignDocumentService) ReflectionTestUtils.getField(service, "documentService"));
    }

    @Test void alreadySentSalaryPackageRetainsTheExistingIdempotentReturn()
    {
        OaSignPackageMapper packages = preparedSending();
        signPackage.setStatus("pending_sign");
        assertThat(sendPrepared()).isSameAs(signPackage);
        verify(packages, never()).markSentWithVersion(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any());
        verifyNoInteractions((OaSignPackageDocumentMapper) ReflectionTestUtils.getField(service, "documentMapper"));
    }

    private OaSignPackageMapper preparedSending()
    {
        OaSignPackageMapper packages = mock(OaSignPackageMapper.class);
        OaSignPackageDocumentMapper documents = mock(OaSignPackageDocumentMapper.class);
        ShopScopeService scope = mock(ShopScopeService.class);
        ReflectionTestUtils.setField(service, "packageMapper", packages);
        ReflectionTestUtils.setField(service, "documentMapper", documents);
        ReflectionTestUtils.setField(service, "eventMapper", mock(OaSignEventMapper.class));
        ReflectionTestUtils.setField(service, "shopScopeService", scope);
        ReflectionTestUtils.setField(service, "signHrAccessService", mock(OaSignHrAccessService.class));
        ReflectionTestUtils.setField(service, "salarySources", mock(OaSignSalarySourceService.class));
        ReflectionTestUtils.setField(service, "documentService", mock(OaSignDocumentService.class));
        signPackage.setShopDeptId(77L); signPackage.setStatus("draft");
        signPackage.setDocumentVersion("SP-100-V1"); signPackage.setVersion(0L);
        when(packages.selectOaSignPackageById(100L)).thenReturn(signPackage);
        when(packages.markSentWithVersion(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any())).thenReturn(1);
        when(scope.resolveRequiredShopDept(77L)).thenReturn(77L);
        OaSignPackageDocument document = new OaSignPackageDocument();
        document.setDocumentId(1L); document.setPackageId(100L); document.setTemplateType(OaSignTemplateType.TRANSFER_SALARY_CONFIRM);
        document.setDocumentVersion("SP-100-V1"); document.setReviewPdfUrl("/synthetic/review.pdf"); document.setReviewPdfHash("synthetic-hash");
        when(documents.selectDocumentsByPackageId(100L)).thenReturn(List.of(document));
        doReturn(signPackage).when(service).getPackageDetail(100L, 77L);
        return packages;
    }

    private OaSignPackage sendPrepared()
    {
        return service.sendPreparedPackage(100L, 200L, "SP-100-V1", 77L,
                Date.from(Instant.parse("2026-09-12T00:00:00Z")), Date.from(Instant.parse("2026-09-19T00:00:00Z")), "PLAN_VERSION", 7);
    }

    @SuppressWarnings("unchecked")
    private List<OaSignTemplate> selected()
    {
        return ReflectionTestUtils.invokeMethod(service, "selectTemplatesForSending", signPackage);
    }

    private OaSignPlanVersionTemplate template(String type)
    {
        OaSignPlanVersionTemplate template = new OaSignPlanVersionTemplate();
        template.setTemplateId(800L); template.setTemplateType(type); template.setTemplateName(type);
        return template;
    }

    private HrEmployeeSigningSnapshot snapshot(String base, String version)
    {
        HrEmployeeSigningSnapshot snapshot = new HrEmployeeSigningSnapshot(); snapshot.setEmployeeId(9L);
        snapshot.setBaseSalary(new BigDecimal(base)); snapshot.setPostSalary(new BigDecimal("1000.00"));
        snapshot.setFieldAllowance(BigDecimal.ZERO); snapshot.setPerformanceSalary(BigDecimal.ZERO);
        snapshot.setSalaryTotal(snapshot.getBaseSalary().add(snapshot.getPostSalary())); snapshot.setSalaryVersion(version);
        return snapshot;
    }
}
