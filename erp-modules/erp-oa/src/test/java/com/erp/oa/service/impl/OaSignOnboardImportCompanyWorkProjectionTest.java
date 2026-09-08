package com.erp.oa.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Date;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.erp.common.core.context.SecurityContextHolder;
import com.erp.common.security.shop.ShopScopeService;
import com.erp.oa.domain.OaSignOnboardContractSnapshot;
import com.erp.oa.domain.OaSignOnboardDataRequest;
import com.erp.oa.domain.OaSignOnboardImportBatch;
import com.erp.oa.domain.OaSignOnboardImportRow;
import com.erp.oa.domain.OaSignPlanVersion;
import com.erp.oa.domain.OaSignPlanVersionTemplate;
import com.erp.oa.domain.OaSignTask;
import com.erp.oa.domain.vo.OaSignOnboardImportRowView;
import com.erp.oa.mapper.OaSignOnboardDataRequestMapper;
import com.erp.oa.mapper.OaSignOnboardImportBatchMapper;
import com.erp.oa.mapper.OaSignOnboardImportRowMapper;
import com.erp.oa.mapper.OaSignPlanVersionMapper;
import com.erp.oa.mapper.OaSignTaskMapper;
import com.erp.oa.service.rule.OnboardSignScenarioRule;
import com.erp.system.api.RemoteUserService;

class OaSignOnboardImportCompanyWorkProjectionTest
{
    @AfterEach
    void tearDown()
    {
        SecurityContextHolder.remove();
    }

    @Test
    void companyWorkProjectionBulkLoadsRelationsAndRejectsBrokenTaskBindings()
            throws Exception
    {
        SecurityContextHolder.setUserId("101");
        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
        OaSignOnboardDataRequestMapper requestMapper =
                mock(OaSignOnboardDataRequestMapper.class);
        OaSignPlanVersionMapper planMapper = mock(OaSignPlanVersionMapper.class);
        OaSignTaskMapper taskMapper = mock(OaSignTaskMapper.class);
        OaSignOnboardImportService service = new OaSignOnboardImportService(
                mock(OaSignOnboardExcelParser.class),
                mock(OaSignOnboardImportBatchMapper.class),
                mock(OaSignOnboardImportRowMapper.class), requestMapper,
                mock(OaSignHrAccessService.class), mock(ShopScopeService.class),
                mock(RemoteUserService.class), mock(OnboardSignScenarioRule.class),
                planMapper, taskMapper, mock(OaOnboardSignEventFactory.class),
                mock(OaSignCompanyService.class), objectMapper);

        OaSignOnboardImportRow bound = row(31L, 71L, 201L, 501L,
                801L, 901L, objectMapper, "张三");
        OaSignOnboardImportRow legacy = row(32L, 72L, 202L, 502L,
                null, null, objectMapper, "李四");
        OaSignOnboardImportRow broken = row(33L, 73L, 203L, 503L,
                803L, 903L, objectMapper, "王五");
        List<OaSignOnboardImportRow> rows = List.of(bound, legacy, broken);

        when(requestMapper.selectSummariesByIds(List.of(501L, 502L, 503L)))
                .thenReturn(List.of(request(bound), request(legacy), request(broken)));
        OaSignTask validTask = exactTask(bound, 801L, 101L, 1171L);
        validTask.setPackageId(901L);
        OaSignTask mismatchedTask = exactTask(broken, 803L, 101L, 1171L);
        mismatchedTask.setPackageId(999L);
        when(taskMapper.selectOaSignTasksByIds(List.of(801L, 803L)))
                .thenReturn(List.of(validTask, mismatchedTask));
        OaSignPlanVersion plan = new OaSignPlanVersion();
        plan.setVersionId(91L);
        plan.setPlanName("标准入职方案");
        when(planMapper.selectPlanVersionsByIds(List.of(91L))).thenReturn(List.of(plan));
        OaSignPlanVersionTemplate contract = template(91L, 1L, "劳动合同");
        OaSignPlanVersionTemplate handbook = template(91L, 2L, "员工手册");
        when(planMapper.selectTemplatesByVersionIds(List.of(91L)))
                .thenReturn(List.of(contract, handbook));

        Map<Long, List<OaSignOnboardImportRowView>> result =
                service.companyWorkRowViewsByBatch(rows, Map.of(
                        31L, batch(31L, 1171L), 32L, batch(32L, 1171L),
                        33L, batch(33L, 1171L)));

        assertThat(result).containsOnlyKeys(31L, 32L);
        assertThat(result.get(31L)).singleElement().satisfies(view -> {
            assertThat(view.getEmployeeName()).isEqualTo("张三");
            assertThat(view.getPlanName()).isEqualTo("标准入职方案");
            assertThat(view.getTemplateNames()).containsExactly("劳动合同", "员工手册");
            assertThat(view.getDataRequestSigningSequence()).isEqualTo("SIGNATURE_FIRST");
            assertThat(view.getDataRequestSignatureCaptured()).isTrue();
            assertThat(view.getTaskId()).isEqualTo(801L);
            assertThat(view.getPackageId()).isEqualTo(901L);
        });
        assertThat(result.get(32L)).singleElement()
                .extracting(OaSignOnboardImportRowView::getEmployeeName)
                .isEqualTo("李四");
        verify(requestMapper).selectSummariesByIds(List.of(501L, 502L, 503L));
        verify(taskMapper).selectOaSignTasksByIds(List.of(801L, 803L));
        verify(planMapper).selectPlanVersionsByIds(List.of(91L));
        verify(planMapper).selectTemplatesByVersionIds(List.of(91L));
        verify(requestMapper, never()).selectById(anyLong());
        verify(taskMapper, never()).selectOaSignTaskById(anyLong());
        verify(planMapper, never()).selectPlanVersionById(anyLong());
        verify(planMapper, never()).selectTemplatesByVersionId(anyLong());
    }

    @Test
    void unboundExactTasksAreHiddenFromNonOwnerAndOutsideBatchOrganization()
            throws Exception
    {
        SecurityContextHolder.setUserId("101");
        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
        OaSignTaskMapper taskMapper = mock(OaSignTaskMapper.class);
        OaSignOnboardImportService service = new OaSignOnboardImportService(
                mock(OaSignOnboardExcelParser.class),
                mock(OaSignOnboardImportBatchMapper.class),
                mock(OaSignOnboardImportRowMapper.class),
                mock(OaSignOnboardDataRequestMapper.class),
                mock(OaSignHrAccessService.class), mock(ShopScopeService.class),
                mock(RemoteUserService.class), mock(OnboardSignScenarioRule.class),
                mock(OaSignPlanVersionMapper.class), taskMapper,
                mock(OaOnboardSignEventFactory.class), mock(OaSignCompanyService.class),
                objectMapper);
        OaSignOnboardImportRow reassigned = row(31L, 1876L, 957L, 501L,
                null, null, objectMapper, "员工甲");
        reassigned.setSourceEventVersion(3L);
        OaSignOnboardImportRow wrongShop = row(32L, 1877L, 958L, 502L,
                null, null, objectMapper, "员工乙");
        wrongShop.setSourceEventVersion(4L);
        OaSignTask reassignedTask = exactTask(reassigned, 198L, 202L, 1171L);
        OaSignTask wrongShopTask = exactTask(wrongShop, 199L, 101L, 9999L);
        when(taskMapper.selectOaSignTasksBySourceEvents(any()))
                .thenReturn(List.of(reassignedTask, wrongShopTask));

        Map<Long, List<OaSignOnboardImportRowView>> result =
                service.companyWorkRowViewsByBatch(List.of(reassigned, wrongShop), Map.of(
                        31L, batch(31L, 1171L), 32L, batch(32L, 1171L)));

        assertThat(result).isEmpty();
        verify(taskMapper).selectOaSignTasksBySourceEvents(any());
    }

    private OaSignOnboardImportRow row(Long batchId, Long rowId, Long employeeId,
            Long requestId, Long taskId, Long packageId, ObjectMapper objectMapper,
            String employeeName) throws Exception
    {
        OaSignOnboardContractSnapshot snapshot = new OaSignOnboardContractSnapshot();
        snapshot.setEmployeeName(employeeName);
        snapshot.setMatchedLegalEntityName("示例公司");
        snapshot.setContractTypeCode("LABOR_CONTRACT");
        OaSignOnboardImportRow row = new OaSignOnboardImportRow();
        row.setBatchId(batchId);
        row.setRowId(rowId);
        row.setSourceEventVersion(1L);
        row.setSourceRowNumber(rowId.intValue());
        row.setEmployeeId(employeeId);
        row.setEmployeeNameMasked(employeeName.substring(0, 1) + "**");
        row.setSnapshotJson(objectMapper.writeValueAsString(snapshot));
        row.setStatus("READY_TO_GENERATE");
        row.setErrorCodesJson("[]");
        row.setWarningCodesJson("[]");
        row.setMissingFieldsJson("[]");
        row.setPlanVersionId(91L);
        row.setDataRequestId(requestId);
        row.setTaskId(taskId);
        row.setPackageId(packageId);
        row.setVersion(3L);
        return row;
    }

    private OaSignOnboardDataRequest request(OaSignOnboardImportRow row)
    {
        OaSignOnboardDataRequest request = new OaSignOnboardDataRequest();
        request.setRequestId(row.getDataRequestId());
        request.setRowId(row.getRowId());
        request.setEmployeeId(row.getEmployeeId());
        request.setSigningSequence("SIGNATURE_FIRST");
        request.setStatus("COMPLETED");
        request.setSignatureSampleHash("hash");
        request.setSignatureSampleTime(new Date());
        return request;
    }

    private OaSignTask task(Long taskId, Long packageId)
    {
        OaSignTask task = new OaSignTask();
        task.setTaskId(taskId);
        task.setPackageId(packageId);
        task.setAssignedHrUserId(101L);
        task.setShopDeptId(1171L);
        return task;
    }

    private OaSignTask exactTask(OaSignOnboardImportRow row, Long taskId,
            Long assignedHrUserId, Long shopDeptId)
    {
        OaSignTask task = task(taskId, null);
        task.setScenario("ONBOARD");
        task.setEmployeeId(row.getEmployeeId());
        task.setSourceType(OaOnboardSignEventFactory.SOURCE_TYPE);
        task.setSourceBusinessId(String.valueOf(row.getRowId()));
        task.setSourceEventVersion(String.valueOf(row.getSourceEventVersion()));
        task.setAssignedHrUserId(assignedHrUserId);
        task.setShopDeptId(shopDeptId);
        return task;
    }

    private OaSignOnboardImportBatch batch(Long batchId, Long shopDeptId)
    {
        OaSignOnboardImportBatch batch = new OaSignOnboardImportBatch();
        batch.setBatchId(batchId);
        batch.setShopDeptId(shopDeptId);
        return batch;
    }

    private OaSignPlanVersionTemplate template(Long versionId, Long id, String name)
    {
        OaSignPlanVersionTemplate template = new OaSignPlanVersionTemplate();
        template.setId(id);
        template.setPlanVersionId(versionId);
        template.setTemplateName(name);
        template.setMatchConditionJson("{}");
        return template;
    }
}
