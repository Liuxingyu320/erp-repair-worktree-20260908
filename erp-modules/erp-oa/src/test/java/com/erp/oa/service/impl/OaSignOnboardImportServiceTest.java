package com.erp.oa.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.multipart.MultipartFile;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.erp.common.core.context.SecurityContextHolder;
import com.erp.common.core.domain.R;
import com.erp.common.core.exception.ServiceException;
import com.erp.common.security.shop.ShopScopeService;
import com.erp.oa.api.domain.HrSignBusinessEvent;
import com.erp.oa.constant.OaSignTemplateType;
import com.erp.oa.domain.OaCompanySealConfig;
import com.erp.oa.domain.OaSignOnboardContractSnapshot;
import com.erp.oa.domain.OaSignOnboardDataRequest;
import com.erp.oa.domain.OaSignOnboardImportBatch;
import com.erp.oa.domain.OaSignOnboardImportRow;
import com.erp.oa.domain.OaSignOnboardSendRequest;
import com.erp.oa.domain.OaSignPackage;
import com.erp.oa.domain.OaSignPlanVersion;
import com.erp.oa.domain.OaSignPlanVersionTemplate;
import com.erp.oa.domain.OaSignTask;
import com.erp.oa.domain.dto.OaSignOnboardDataRequestSendRequest;
import com.erp.oa.domain.dto.OaSignOnboardImportRowUpdateRequest;
import com.erp.oa.domain.vo.OaSignDraftDecision;
import com.erp.oa.domain.vo.OaSignOnboardImportRowView;
import com.erp.oa.mapper.OaSignOnboardDataRequestMapper;
import com.erp.oa.mapper.OaSignOnboardImportBatchMapper;
import com.erp.oa.mapper.OaSignOnboardImportRowMapper;
import com.erp.oa.mapper.OaSignOnboardSendRequestMapper;
import com.erp.oa.mapper.OaSignPlanVersionMapper;
import com.erp.oa.mapper.OaSignTaskMapper;
import com.erp.oa.service.rule.OnboardSignScenarioRule;
import com.erp.system.api.RemoteUserService;
import com.erp.system.api.constant.SigningProfileCodes;
import com.erp.system.api.domain.SignCandidateUser;
import com.erp.system.api.domain.SignCandidateUserQuery;
import com.erp.system.api.domain.SysLegalEntity;

class OaSignOnboardImportServiceTest
{
    @AfterEach
    void clearSecurityContext()
    {
        SecurityContextHolder.remove();
    }

    @Test
    void planMissingReasonsAreNeverHiddenByGenerationConfirmations()
    {
        OaSignOnboardImportService service = service();
        Set<String> missing = new LinkedHashSet<>(List.of(
                "historicalSupplementReason", "noExternalContractConfirmation"));
        Set<String> errors = new LinkedHashSet<>();

        service.applyPlanDecisionReasons(List.of("MISSING_POST", "MISSING_JOB_GRADE",
                "MISSING_CONTRACT_DATES", "PLAN_NOT_FOUND"), missing, errors);

        assertThat(missing).contains("historicalSupplementReason", "noExternalContractConfirmation",
                "jobGradeCode", "contractStartDate", "contractEndDate");
        assertThat(errors).containsExactly("MISSING_POST", "PLAN_NOT_FOUND");
    }

    @Test
    void reassignedCurrentHrCanAccessCreatorAuditBatchButFormerHrCannot()
    {
        OaSignOnboardImportBatchMapper batchMapper = mock(OaSignOnboardImportBatchMapper.class);
        OaSignOnboardImportBatch batch = new OaSignOnboardImportBatch();
        batch.setBatchId(31L);
        batch.setCreatedByUserId(101L);
        batch.setShopDeptId(1171L);
        when(batchMapper.selectById(31L)).thenReturn(batch);
        ShopScopeService scope = mock(ShopScopeService.class);
        when(scope.resolveScopeDeptIds(1171L)).thenReturn(List.of(1171L));

        OaSignHrAccessService formerHrAccess = mock(OaSignHrAccessService.class);
        doThrow(new ServiceException("仅当前配置的合同经办人可以访问签约业务"))
                .when(formerHrAccess).requireCurrentHr();
        OaSignOnboardImportService formerHrService = serviceWithOwnership(
                batchMapper, mock(OaSignOnboardImportRowMapper.class),
                formerHrAccess, scope, mock(OaSignTaskMapper.class));
        SecurityContextHolder.setUserId("101");

        assertThatThrownBy(() -> formerHrService.requireHrBatch(31L, 1171L))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("仅当前配置");

        OaSignHrAccessService currentHrAccess = mock(OaSignHrAccessService.class);
        OaSignOnboardImportService currentHrService = serviceWithOwnership(
                batchMapper, mock(OaSignOnboardImportRowMapper.class),
                currentHrAccess, scope, mock(OaSignTaskMapper.class));
        SecurityContextHolder.setUserId("202");

        assertThat(currentHrService.requireHrBatch(31L, 1171L)).isSameAs(batch);
        assertThat(batch.getCreatedByUserId()).isEqualTo(101L);
    }

    @Test
    void boundRowAuthorizationRequiresTaskAssigneeAndFailsClosedOnBindingMismatch()
    {
        OaSignOnboardImportBatchMapper batchMapper = mock(OaSignOnboardImportBatchMapper.class);
        OaSignOnboardImportRowMapper rowMapper = mock(OaSignOnboardImportRowMapper.class);
        ShopScopeService scope = mock(ShopScopeService.class);
        OaSignTaskMapper taskMapper = mock(OaSignTaskMapper.class);
        OaSignOnboardImportService service = serviceWithOwnership(batchMapper, rowMapper,
                mock(OaSignHrAccessService.class), scope, taskMapper);
        SecurityContextHolder.setUserId("202");

        OaSignOnboardImportBatch batch = new OaSignOnboardImportBatch();
        batch.setBatchId(31L);
        batch.setShopDeptId(1171L);
        OaSignOnboardImportRow row = new OaSignOnboardImportRow();
        row.setRowId(1876L);
        row.setBatchId(31L);
        row.setEmployeeId(957L);
        row.setSourceEventVersion(3L);
        row.setTaskId(801L);
        row.setPackageId(901L);
        OaSignTask reassigned = new OaSignTask();
        reassigned.setTaskId(801L);
        reassigned.setScenario("ONBOARD");
        reassigned.setEmployeeId(957L);
        reassigned.setSourceType(OaOnboardSignEventFactory.SOURCE_TYPE);
        reassigned.setSourceBusinessId("1876");
        reassigned.setSourceEventVersion("3");
        reassigned.setPackageId(901L);
        reassigned.setAssignedHrUserId(202L);
        reassigned.setShopDeptId(1171L);
        when(taskMapper.selectOaSignTaskById(801L)).thenReturn(reassigned);
        when(taskMapper.selectCanonicalTaskBySourceEvent(any())).thenReturn(reassigned);

        service.requireCurrentHrTaskOwnership(row, batch);

        reassigned.setAssignedHrUserId(101L);
        assertThatThrownBy(() -> service.requireCurrentHrTaskOwnership(row, batch))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("未分配给当前合同经办人");
        assertThatCode(() -> service.requireValidTaskBinding(row)).doesNotThrowAnyException();
        reassigned.setPackageId(999L);
        assertThatThrownBy(() -> service.requireValidTaskBinding(row))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("绑定不一致");
    }

    @Test
    void unboundRowWithExactCanonicalTaskStillRequiresCurrentTaskOwner()
    {
        OaSignTaskMapper taskMapper = mock(OaSignTaskMapper.class);
        OaSignOnboardImportService service = serviceWithOwnership(
                mock(OaSignOnboardImportBatchMapper.class),
                mock(OaSignOnboardImportRowMapper.class),
                mock(OaSignHrAccessService.class), mock(ShopScopeService.class), taskMapper);
        OaSignOnboardImportRow row = canonicalSourceRow();
        OaSignTask exact = canonicalSourceTask(198L, 202L);
        when(taskMapper.selectCanonicalTaskBySourceEvent(any())).thenReturn(exact);
        SecurityContextHolder.setUserId("101");
        OaSignOnboardImportBatch batch = new OaSignOnboardImportBatch();
        batch.setBatchId(134L);
        batch.setShopDeptId(101L);

        assertThatThrownBy(() -> service.requireCurrentHrTaskOwnership(row, batch))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("未分配给当前合同经办人");

        exact.setAssignedHrUserId(101L);
        assertThatCode(() -> service.requireCurrentHrTaskOwnership(row, batch))
                .doesNotThrowAnyException();

        exact.setShopDeptId(9999L);
        assertThatThrownBy(() -> service.requireCurrentHrTaskOwnership(row, batch))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("不在当前批次门店");
    }

    @Test
    void publicRowUpdateRechecksLockedTaskOwnerAfterCandidateLookup()
    {
        CanonicalOpenTaskEvaluationFixture fixture =
                new CanonicalOpenTaskEvaluationFixture();
        OaSignTask initiallyOwned = canonicalSourceTask(198L, 101L);
        OaSignTask reassigned = canonicalSourceTask(198L, 202L);
        when(fixture.taskMapper.selectCanonicalTaskBySourceEvent(any()))
                .thenReturn(initiallyOwned);
        when(fixture.taskMapper.selectOpenOnboardTaskByEmployeeId(957L))
                .thenReturn(initiallyOwned);
        when(fixture.taskMapper.lockOaSignTaskById(198L)).thenReturn(reassigned);
        OaSignOnboardImportRowUpdateRequest request =
                new OaSignOnboardImportRowUpdateRequest();
        request.setVersion(5L);

        assertThatThrownBy(() -> fixture.service.updateRow(
                134L, 1876L, request, 101L))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("已改派");

        InOrder order = inOrder(fixture.service, fixture.taskMapper);
        order.verify(fixture.service).requireCandidate(957L, 101L);
        order.verify(fixture.taskMapper).lockOaSignTaskById(198L);
        verify(fixture.rowMapper, never()).updateEditableWithVersion(any());
    }

    @Test
    void publicRowUpdateRejectsDuplicateExactSourceTasksBeforeCandidateLookup()
    {
        CanonicalOpenTaskEvaluationFixture fixture =
                new CanonicalOpenTaskEvaluationFixture();
        OaSignTask first = canonicalSourceTask(198L, 101L);
        OaSignTask duplicate = canonicalSourceTask(199L, 101L);
        when(fixture.taskMapper.selectExactTasksBySourceEvent(any()))
                .thenReturn(List.of(first, duplicate));
        when(fixture.taskMapper.selectCanonicalTaskBySourceEvent(any()))
                .thenReturn(first);
        OaSignOnboardImportRowUpdateRequest request =
                new OaSignOnboardImportRowUpdateRequest();
        request.setVersion(5L);

        assertThatThrownBy(() -> fixture.service.updateRow(
                134L, 1876L, request, 101L))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("多个签约任务");

        verify(fixture.service, never()).requireCandidate(anyLong(), anyLong());
        verify(fixture.rowMapper, never()).updateEditableWithVersion(any());
    }

    @Test
    void templateFailureReevaluationAcceptsSameCanonicalOpenTaskAndBecomesReady()
    {
        CanonicalOpenTaskEvaluationFixture fixture = new CanonicalOpenTaskEvaluationFixture();
        fixture.row.setErrorCodesJson(fixture.service.json(List.of("TEMPLATE_FILE_MISSING")));
        OaSignTask exact = canonicalSourceTask(198L, 101L);
        when(fixture.taskMapper.selectCanonicalTaskBySourceEvent(any())).thenReturn(exact);
        when(fixture.taskMapper.selectOpenOnboardTaskByEmployeeId(957L)).thenReturn(exact);

        List<String> stickyErrors = ReflectionTestUtils.invokeMethod(
                fixture.service, "stickyErrors", fixture.row);
        ReflectionTestUtils.invokeMethod(fixture.service, "evaluate", fixture.batch,
                fixture.row, fixture.snapshot, fixture.employee, stickyErrors);

        assertThat(fixture.row.getStatus()).isEqualTo("READY_TO_GENERATE");
        assertThat(fixture.service.list(fixture.row.getErrorCodesJson())).isEmpty();
        assertThat(fixture.service.list(fixture.row.getMissingFieldsJson())).isEmpty();
        assertThat(fixture.row.getPlanVersionId()).isEqualTo(11L);
    }

    @Test
    void differentSourceBusinessIdOrEventVersionStillConflictsWithOpenTask()
    {
        CanonicalOpenTaskEvaluationFixture businessFixture =
                new CanonicalOpenTaskEvaluationFixture();
        OaSignTask wrongBusiness = canonicalSourceTask(198L, 101L);
        wrongBusiness.setSourceBusinessId("1875");
        when(businessFixture.taskMapper.selectOpenOnboardTaskByEmployeeId(957L))
                .thenReturn(wrongBusiness);

        businessFixture.evaluate();

        assertThat(businessFixture.service.list(businessFixture.row.getErrorCodesJson()))
                .contains("EXISTING_OPEN_ONBOARD_TASK");
        assertThat(businessFixture.row.getStatus()).isEqualTo("CONFLICT");

        CanonicalOpenTaskEvaluationFixture versionFixture =
                new CanonicalOpenTaskEvaluationFixture();
        OaSignTask wrongVersion = canonicalSourceTask(198L, 101L);
        wrongVersion.setSourceEventVersion("2");
        when(versionFixture.taskMapper.selectOpenOnboardTaskByEmployeeId(957L))
                .thenReturn(wrongVersion);

        versionFixture.evaluate();

        assertThat(versionFixture.service.list(versionFixture.row.getErrorCodesJson()))
                .contains("EXISTING_OPEN_ONBOARD_TASK");
        assertThat(versionFixture.row.getStatus()).isEqualTo("CONFLICT");
    }

    @Test
    void batchDetailHidesBoundAndUnboundExactTasksOutsideOwnerOrOrganization()
    {
        OaSignOnboardImportBatchMapper batchMapper = mock(OaSignOnboardImportBatchMapper.class);
        OaSignOnboardImportRowMapper rowMapper = mock(OaSignOnboardImportRowMapper.class);
        ShopScopeService scope = mock(ShopScopeService.class);
        OaSignTaskMapper taskMapper = mock(OaSignTaskMapper.class);
        OaSignOnboardImportService service = serviceWithOwnership(batchMapper, rowMapper,
                mock(OaSignHrAccessService.class), scope, taskMapper);
        OaSignOnboardImportBatch batch = new OaSignOnboardImportBatch();
        batch.setBatchId(31L);
        batch.setCreatedByUserId(101L);
        batch.setShopDeptId(1171L);
        when(batchMapper.selectById(31L)).thenReturn(batch);
        when(scope.resolveScopeDeptIds(1171L)).thenReturn(List.of(1171L));
        OaSignOnboardImportRow mine = boundRow(71L, 201L, 801L, 901L);
        OaSignOnboardImportRow anotherOwner = boundRow(72L, 202L, 802L, 902L);
        OaSignOnboardImportRow unboundOtherOwner = unboundExactRow(1876L, 957L, 3L);
        OaSignOnboardImportRow unboundWrongShop = unboundExactRow(1877L, 958L, 4L);
        OaSignOnboardImportRow unboundDuplicate = unboundExactRow(1878L, 959L, 5L);
        when(rowMapper.selectByBatchId(31L)).thenReturn(List.of(
                mine, anotherOwner, unboundOtherOwner, unboundWrongShop,
                unboundDuplicate));
        OaSignTask mineTask = exactSourceTask(mine, 801L, 202L, 1171L);
        mineTask.setPackageId(901L);
        OaSignTask anotherTask = exactSourceTask(anotherOwner, 802L, 303L, 1171L);
        anotherTask.setPackageId(902L);
        when(taskMapper.selectOaSignTasksByIds(List.of(801L, 802L)))
                .thenReturn(List.of(mineTask, anotherTask));
        OaSignTask trailingSource = exactSourceTask(
                unboundOtherOwner, 198L, 303L, 1171L);
        trailingSource.setSourceBusinessId("1876 ");
        OaSignTask caseInsensitiveSource = exactSourceTask(
                unboundWrongShop, 199L, 202L, 9999L);
        caseInsensitiveSource.setSourceType(
                OaOnboardSignEventFactory.SOURCE_TYPE.toLowerCase(Locale.ROOT));
        when(taskMapper.selectOaSignTasksBySourceEvents(any())).thenReturn(List.of(
                trailingSource, caseInsensitiveSource,
                exactSourceTask(unboundDuplicate, 200L, 202L, 1171L),
                exactSourceTask(unboundDuplicate, 201L, 303L, 1171L)));
        OaSignTask otherOwnerMetadata = exactSourceTask(mine, 999L, 303L, 1171L);
        otherOwnerMetadata.setStatus("READY_TO_SEND");
        when(taskMapper.selectLatestOnboardTasksByEmployeeIds(List.of(201L)))
                .thenReturn(List.of(otherOwnerMetadata));
        when(taskMapper.selectOpenOnboardTaskByEmployeeId(201L))
                .thenReturn(otherOwnerMetadata);
        SecurityContextHolder.setUserId("202");

        List<OaSignOnboardImportRowView> visible = service.detail(31L, 1171L).getRows();
        assertThat(visible)
                .extracting(OaSignOnboardImportRowView::getRowId)
                .containsExactly(71L);
        assertThat(visible.get(0).getExistingTaskId()).isNull();
        assertThat(visible.get(0).getLatestTaskId()).isNull();
    }

    private OaSignOnboardImportRow boundRow(Long rowId, Long employeeId,
            Long taskId, Long packageId)
    {
        OaSignOnboardImportRow row = new OaSignOnboardImportRow();
        row.setRowId(rowId);
        row.setBatchId(31L);
        row.setEmployeeId(employeeId);
        row.setSourceEventVersion(1L);
        row.setTaskId(taskId);
        row.setPackageId(packageId);
        row.setSnapshotJson("{}");
        row.setErrorCodesJson("[]");
        row.setWarningCodesJson("[]");
        row.setMissingFieldsJson("[]");
        row.setVersion(1L);
        return row;
    }

    private OaSignOnboardImportRow unboundExactRow(Long rowId, Long employeeId,
            Long sourceEventVersion)
    {
        OaSignOnboardImportRow row = boundRow(rowId, employeeId, null, null);
        row.setSourceEventVersion(sourceEventVersion);
        return row;
    }

    private OaSignTask assignedTask(Long taskId, Long packageId, Long assignedHrUserId)
    {
        OaSignTask task = new OaSignTask();
        task.setTaskId(taskId);
        task.setPackageId(packageId);
        task.setAssignedHrUserId(assignedHrUserId);
        task.setShopDeptId(1171L);
        return task;
    }

    private OaSignTask exactSourceTask(OaSignOnboardImportRow row, Long taskId,
            Long assignedHrUserId, Long shopDeptId)
    {
        OaSignTask task = new OaSignTask();
        task.setTaskId(taskId);
        task.setScenario("ONBOARD");
        task.setEmployeeId(row.getEmployeeId());
        task.setSourceType(OaOnboardSignEventFactory.SOURCE_TYPE);
        task.setSourceBusinessId(String.valueOf(row.getRowId()));
        task.setSourceEventVersion(String.valueOf(row.getSourceEventVersion()));
        task.setStatus("NEEDS_DATA");
        task.setAssignedHrUserId(assignedHrUserId);
        task.setShopDeptId(shopDeptId);
        return task;
    }

    private OaSignOnboardImportService serviceWithOwnership(
            OaSignOnboardImportBatchMapper batchMapper,
            OaSignOnboardImportRowMapper rowMapper,
            OaSignHrAccessService access, ShopScopeService scope,
            OaSignTaskMapper taskMapper)
    {
        OaSignOnboardImportService service = new OaSignOnboardImportService(
                mock(OaSignOnboardExcelParser.class), batchMapper, rowMapper,
                mock(OaSignOnboardDataRequestMapper.class), access, scope,
                mock(RemoteUserService.class), mock(OnboardSignScenarioRule.class),
                mock(OaSignPlanVersionMapper.class), taskMapper,
                mock(OaOnboardSignEventFactory.class), mock(OaSignCompanyService.class),
                new ObjectMapper());
        ReflectionTestUtils.setField(service, "enabled", true);
        return service;
    }

    @Test
    void previewInitializesAllNotNullCountersBeforePersistingBatch()
    {
        OaSignOnboardExcelParser parser = mock(OaSignOnboardExcelParser.class);
        OaSignOnboardImportBatchMapper batchMapper = mock(OaSignOnboardImportBatchMapper.class);
        OaSignOnboardImportRowMapper rowMapper = mock(OaSignOnboardImportRowMapper.class);
        OaSignHrAccessService access = mock(OaSignHrAccessService.class);
        ShopScopeService scope = mock(ShopScopeService.class);
        RemoteUserService remoteUserService = mock(RemoteUserService.class);
        OaSignTaskMapper taskMapper = mock(OaSignTaskMapper.class);
        OaSignOnboardImportService service = new OaSignOnboardImportService(parser, batchMapper,
                rowMapper, mock(OaSignOnboardDataRequestMapper.class), access, scope,
                remoteUserService, mock(OnboardSignScenarioRule.class),
                mock(OaSignPlanVersionMapper.class), taskMapper,
                mock(OaOnboardSignEventFactory.class), mock(OaSignCompanyService.class),
                new ObjectMapper());
        ReflectionTestUtils.setField(service, "enabled", true);
        SecurityContextHolder.setUserId("101");
        SecurityContextHolder.setUserName("uat-hr");

        MultipartFile file = mock(MultipartFile.class);
        when(file.getOriginalFilename()).thenReturn("sign-data.xlsx");
        when(file.getSize()).thenReturn(1024L);
        OaSignOnboardContractSnapshot source = new OaSignOnboardContractSnapshot();
        source.setEmployeeName("张三");
        source.setPhone("13800000000");
        source.setIdNumber("330102199001011234");
        when(parser.parse(file)).thenReturn(new OaSignOnboardExcelParser.ParsedWorkbook(
                "a".repeat(64), List.of(new OaSignOnboardExcelParser.ParsedRow(
                        2, "b".repeat(64), source, List.of()))));
        when(scope.resolveRequiredShopDept(1171L)).thenReturn(1171L);
        when(scope.resolveShopDeptName(1171L)).thenReturn("测试门店");
        SignCandidateUser unexpected = candidate(88L, "张三", "13800000000",
                "330102199001011234");
        when(remoteUserService.listSignCandidates(any(), anyString()))
                .thenReturn(R.ok(List.of(unexpected)));

        AtomicReference<OaSignOnboardImportBatch> inserted = new AtomicReference<>();
        AtomicReference<List<OaSignOnboardImportRow>> insertedRows = new AtomicReference<>();
        when(batchMapper.insertBatch(any())).thenAnswer(invocation -> {
            OaSignOnboardImportBatch batch = invocation.getArgument(0);
            batch.setBatchId(901L);
            inserted.set(batch);
            return 1;
        });
        when(rowMapper.insertRows(any())).thenAnswer(invocation -> {
            List<OaSignOnboardImportRow> rows = invocation.getArgument(0);
            insertedRows.set(rows);
            return rows.size();
        });
        when(batchMapper.selectById(901L)).thenAnswer(invocation -> inserted.get());
        when(rowMapper.selectByBatchId(901L)).thenReturn(List.of());

        service.preview(file, List.of(42L),
                OaSignOnboardImportService.MATCH_MODE_MANUAL_SELECTED, 1171L);

        assertThat(inserted.get()).isNotNull();
        assertThat(inserted.get().getMatchedCount()).isZero();
        assertThat(inserted.get().getExcludedCount()).isZero();
        assertThat(inserted.get().getErrorCount()).isZero();
        assertThat(inserted.get().getWarningCount()).isZero();
        assertThat(inserted.get().getGeneratedCount()).isZero();
        assertThat(insertedRows.get()).noneMatch(row -> Long.valueOf(88L).equals(row.getEmployeeId()));
        assertThat(insertedRows.get()).allMatch(row -> Boolean.FALSE.equals(row.getCompanyDeptConflict()));
    }

    @Test
    void excelOnlyPreviewQueriesExactPhonesInsideScopeAndDoesNotReuseOldPreview()
    {
        OaSignOnboardExcelParser parser = mock(OaSignOnboardExcelParser.class);
        OaSignOnboardImportBatchMapper batchMapper = mock(OaSignOnboardImportBatchMapper.class);
        OaSignOnboardImportRowMapper rowMapper = mock(OaSignOnboardImportRowMapper.class);
        ShopScopeService scope = mock(ShopScopeService.class);
        RemoteUserService remoteUserService = mock(RemoteUserService.class);
        OaSignOnboardImportService service = new OaSignOnboardImportService(parser, batchMapper,
                rowMapper, mock(OaSignOnboardDataRequestMapper.class),
                mock(OaSignHrAccessService.class), scope, remoteUserService,
                mock(OnboardSignScenarioRule.class), mock(OaSignPlanVersionMapper.class),
                mock(OaSignTaskMapper.class), mock(OaOnboardSignEventFactory.class),
                mock(OaSignCompanyService.class), new ObjectMapper());
        ReflectionTestUtils.setField(service, "enabled", true);
        SecurityContextHolder.setUserId("101");
        SecurityContextHolder.setUserName("uat-hr");

        MultipartFile file = mock(MultipartFile.class);
        when(file.getOriginalFilename()).thenReturn("sign-data.xlsx");
        when(file.getSize()).thenReturn(1024L);
        OaSignOnboardContractSnapshot source = identity("张三", "13800000000",
                "330102199001011234");
        when(parser.parse(file)).thenReturn(new OaSignOnboardExcelParser.ParsedWorkbook(
                "c".repeat(64), List.of(parsed(source, 2))));
        when(scope.resolveRequiredShopDept(1171L)).thenReturn(1171L);
        when(scope.resolveShopDeptName(1171L)).thenReturn("测试门店");
        SignCandidateUser departed = candidate(88L, "张三", "13800000000",
                "330102199001011234");
        departed.setEmployeeStatus("离职");
        when(remoteUserService.listSignCandidates(any(), anyString()))
                .thenReturn(R.ok(List.of(departed)));

        AtomicReference<OaSignOnboardImportBatch> inserted = new AtomicReference<>();
        AtomicReference<List<OaSignOnboardImportRow>> insertedRows = new AtomicReference<>();
        when(batchMapper.insertBatch(any())).thenAnswer(invocation -> {
            OaSignOnboardImportBatch batch = invocation.getArgument(0);
            batch.setBatchId(902L);
            inserted.set(batch);
            return 1;
        });
        when(rowMapper.insertRows(any())).thenAnswer(invocation -> {
            List<OaSignOnboardImportRow> rows = invocation.getArgument(0);
            insertedRows.set(rows);
            return rows.size();
        });
        when(batchMapper.selectById(902L)).thenAnswer(invocation -> inserted.get());
        when(rowMapper.selectByBatchId(902L)).thenReturn(List.of());

        service.preview(file, List.of(), OaSignOnboardImportService.MATCH_MODE_EXCEL_PHONE_NAME,
                1171L);

        ArgumentCaptor<SignCandidateUserQuery> query =
                ArgumentCaptor.forClass(SignCandidateUserQuery.class);
        verify(remoteUserService).listSignCandidates(query.capture(), anyString());
        assertThat(query.getValue().getDeptId()).isEqualTo(1171L);
        assertThat(query.getValue().getPhoneNumbers()).containsExactly("13800000000");
        assertThat(query.getValue().getUserIds()).isNull();
        assertThat(query.getValue().getLimit()).isEqualTo(500);
        assertThat(query.getValue().getIncludeInactiveEmployees()).isTrue();
        assertThat(inserted.get().getSelectedCount()).isZero();
        assertThat(inserted.get().getSelectedEmployeeIdsJson()).isEqualTo("[]");
        assertThat(insertedRows.get()).singleElement()
                .satisfies(row -> {
                    assertThat(row.getEmployeeId()).isNull();
                    assertThat(row.getCompanyDeptConflict()).isFalse();
                    assertThat(service.list(row.getErrorCodesJson()))
                            .containsExactly("EMPLOYEE_REHIRE_REQUIRED");
                });
        verify(batchMapper, never()).selectReusable(anyLong(), anyLong(), anyString(),
                anyString(), any());

        when(remoteUserService.listSignCandidates(any(), anyString()))
                .thenReturn(R.ok(Collections.nCopies(500, departed)));
        assertThatThrownBy(() -> service.preview(file, List.of(),
                OaSignOnboardImportService.MATCH_MODE_EXCEL_PHONE_NAME, 1171L))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("候选过多");
        verify(batchMapper, times(1)).insertBatch(any());
    }

    @Test
    void excelOnlyMatchFailsClosedForNamePhoneAndIdentityAmbiguity()
    {
        OaSignOnboardImportService service = service();
        SignCandidateUser candidate = candidate(88L, "李曼", "13800001111",
                "330102199001011234");

        OaSignOnboardImportService.MatchedSource matched = service.matchByPhoneAndName(
                List.of(parsed(identity("李曼", "13800001111", "330102199001011234"), 2)),
                Map.of(88L, candidate)).get(0);
        assertThat(matched.candidate()).isSameAs(candidate);
        assertThat(matched.errors()).isEmpty();

        OaSignOnboardImportService.MatchedSource wrongName = service.matchByPhoneAndName(
                List.of(parsed(identity("李敏", "13800001111", "330102199001011234"), 2)),
                Map.of(88L, candidate)).get(0);
        assertThat(wrongName.candidate()).isNull();
        assertThat(wrongName.errors()).contains("PHONE_NAME_MISMATCH");

        OaSignOnboardImportService.MatchedSource wrongIdentity = service.matchByPhoneAndName(
                List.of(parsed(identity("李曼", "13800001111", "330102199001019999"), 2)),
                Map.of(88L, candidate)).get(0);
        assertThat(wrongIdentity.candidate()).isSameAs(candidate);
        assertThat(wrongIdentity.errors()).contains("EMPLOYEE_IDENTITY_MISMATCH");

        SignCandidateUser duplicate = candidate(89L, "李曼", "13800001111",
                "330102199001011235");
        OaSignOnboardImportService.MatchedSource ambiguous = service.matchByPhoneAndName(
                List.of(parsed(identity("李曼", "13800001111", "330102199001011234"), 2)),
                Map.of(88L, candidate, 89L, duplicate)).get(0);
        assertThat(ambiguous.candidate()).isNull();
        assertThat(ambiguous.errors()).contains("DUPLICATE_PROFILE_PHONE");
    }

    @Test
    void identityChangesAfterPreviewRequireRepreview()
    {
        OaSignOnboardImportService service = service();
        OaSignOnboardContractSnapshot frozen = identity("李曼", "13800001111",
                "330102199001011234");
        SignCandidateUser current = candidate(88L, "李曼", "13800001111",
                "330102199001011234");
        assertThat(service.identityFactsMatch(frozen, current)).isTrue();

        current.setPhonenumber("13900001111");
        assertThat(service.identityFactsMatch(frozen, current)).isFalse();

        current.setPhonenumber("13800001111");
        current.setEmployeeStatus("离职");
        OaSignOnboardImportBatch autoBatch = new OaSignOnboardImportBatch();
        autoBatch.setSelectedCount(0);
        assertThat(service.identityFactsMatch(autoBatch, frozen, current)).isFalse();
    }

    @Test
    void phoneAndNameFallbackUsesSystemIdentityForAgeAndConditionalTemplates()
    {
        OaSignOnboardImportService service = service();
        OaSignOnboardContractSnapshot snapshot = new OaSignOnboardContractSnapshot();
        snapshot.setEmployeeName("李曼");
        snapshot.setPhone("13800001111");
        // Excel says the employee is a minor, while the selected System profile is an adult.
        snapshot.setIdNumber("330102201001011234");
        OaSignOnboardExcelParser.ParsedRow parsed = new OaSignOnboardExcelParser.ParsedRow(
                2, "raw-excel-row-hash", snapshot, List.of());
        SignCandidateUser candidate = new SignCandidateUser();
        candidate.setUserId(88L);
        candidate.setNickName("李曼");
        candidate.setPhonenumber("13800001111");
        candidate.setIdNumber("330102199001011234");
        Map<Long, SignCandidateUser> candidates = new LinkedHashMap<>();
        candidates.put(88L, candidate);

        OaSignOnboardImportService.MatchedSource matched = service
                .match(List.of(parsed), candidates).get(0);
        assertThat(matched.matchType()).isEqualTo("PHONE_AND_NAME");
        assertThat(matched.candidate()).isSameAs(candidate);

        service.applyAuthoritativeIdentity(snapshot, matched.candidate());

        assertThat(snapshot.getIdNumber()).isEqualTo("330102199001011234");
        assertThat(parsed.getRowHash()).isEqualTo("raw-excel-row-hash");
    }

    @Test
    void gradeNineMinorNonStudentRequiresBothConditionalFilesInThePublishedPlan()
    {
        OaSignPlanVersionMapper planMapper = mock(OaSignPlanVersionMapper.class);
        when(planMapper.selectTemplatesByVersionId(11L)).thenReturn(List.of(
                template(OaSignTemplateType.ONBOARD_COMMITMENT),
                template(OaSignTemplateType.ONBOARD_LABOR_CONTRACT),
                template(OaSignTemplateType.ONBOARD_HANDBOOK_RECEIPT),
                template(OaSignTemplateType.ONBOARD_SALARY_CONFIRM)));
        OaSignOnboardImportService service = service(planMapper);
        OaSignOnboardContractSnapshot snapshot = new OaSignOnboardContractSnapshot();
        snapshot.setContractTypeCode("LABOR_CONTRACT");
        snapshot.setJobGradeCode("9");
        snapshot.setIdNumber("330102200907181234");
        snapshot.setContractStartDate(LocalDate.of(2026, 7, 18));
        snapshot.setStudentStatus("NON_STUDENT");

        assertThat(service.templateGateErrors(11L, snapshot)).containsExactly(
                "PLAN_MISSING_" + OaSignTemplateType.ONBOARD_CONFIDENTIAL_NONCOMPETE,
                "PLAN_MISSING_" + OaSignTemplateType.ONBOARD_MINOR_NONSTUDENT_DECLARATION);
    }

    @Test
    void signatureRequestPreflightBlocksMissingPlanAndEmptyDocumentList()
    {
        OaSignPlanVersionMapper planMapper = mock(OaSignPlanVersionMapper.class);
        OaSignOnboardImportService service = service(planMapper);
        OaSignOnboardContractSnapshot snapshot = completeLaborSnapshot();
        OaSignOnboardImportRow row = new OaSignOnboardImportRow();

        assertThat(service.signatureRequestBlockers(row, snapshot))
                .containsExactly("PLAN_MISSING");

        row.setPlanVersionId(11L);
        when(planMapper.selectTemplatesByVersionId(11L)).thenReturn(List.of());
        assertThat(service.signatureRequestBlockers(row, snapshot))
                .contains("PLAN_DOCUMENTS_EMPTY",
                        "PLAN_MISSING_" + OaSignTemplateType.ONBOARD_LABOR_CONTRACT,
                        "PLAN_MISSING_" + OaSignTemplateType.ONBOARD_SALARY_CONFIRM);
    }

    @Test
    void signatureRequestPreflightDefersOnlyCompanyFinalizationErrors()
    {
        OaSignPlanVersionMapper planMapper = mock(OaSignPlanVersionMapper.class);
        OaSignOnboardImportService service = service(planMapper);
        OaSignOnboardContractSnapshot labor = completeLaborSnapshot();
        OaSignOnboardImportRow row = new OaSignOnboardImportRow();
        row.setPlanVersionId(11L);
        row.setErrorCodesJson(service.json(List.of(
                "COMPANY_MASTER_DATA_INCOMPLETE", "COMPANY_SEAL_REQUIRES_HR",
                "EMPLOYEE_IDENTITY_MISMATCH", "DUPLICATE_EXCEL_ROW")));
        when(planMapper.selectTemplatesByVersionId(11L)).thenReturn(completeLaborTemplates());

        assertThat(service.signatureRequestBlockers(row, labor))
                .contains("EMPLOYEE_IDENTITY_MISMATCH", "DUPLICATE_EXCEL_ROW")
                .doesNotContain("COMPANY_MASTER_DATA_INCOMPLETE", "COMPANY_SEAL_REQUIRES_HR");

        OaSignOnboardContractSnapshot serviceContract = completeLaborSnapshot();
        serviceContract.setContractTypeCode(SigningProfileCodes.SERVICE_CONTRACT);
        serviceContract.setSocialTypeCode(SigningProfileCodes.SOCIAL_UNINSURED);
        serviceContract.setSalaryVersion("A");
        row.setPlanVersionId(12L);
        row.setErrorCodesJson("[]");
        when(planMapper.selectTemplatesByVersionId(12L)).thenReturn(List.of(
                template(OaSignTemplateType.ONBOARD_COMMITMENT),
                template(OaSignTemplateType.ONBOARD_SERVICE_CONTRACT),
                template(OaSignTemplateType.ONBOARD_SERVICE_RECEIPT)));

        assertThat(service.signatureRequestBlockers(row, serviceContract))
                .contains("SERVICE_PERSON_TYPE_MISSING", "INSURANCE_TYPE_MISSING");
    }

    @Test
    void companyAndSealConflictsKeepGenerationBlockedButAllowSignatureFirstRequest()
    {
        OaSignOnboardImportBatchMapper batchMapper = mock(OaSignOnboardImportBatchMapper.class);
        OaSignOnboardImportRowMapper rowMapper = mock(OaSignOnboardImportRowMapper.class);
        OaSignOnboardDataRequestMapper requestMapper = mock(OaSignOnboardDataRequestMapper.class);
        OaSignPlanVersionMapper planMapper = mock(OaSignPlanVersionMapper.class);
        OaSignTaskMapper taskMapper = mock(OaSignTaskMapper.class);
        OaSignCompanyService companyService = mock(OaSignCompanyService.class);
        OaOnboardSignEventFactory eventFactory = mock(OaOnboardSignEventFactory.class);
        OnboardSignScenarioRule onboardRule = mock(OnboardSignScenarioRule.class);
        ShopScopeService scope = mock(ShopScopeService.class);
        RemoteUserService remoteUserService = mock(RemoteUserService.class);
        OaSignTaskOrchestrator taskOrchestrator = mock(OaSignTaskOrchestrator.class);
        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
        OaSignOnboardImportService service = new OaSignOnboardImportService(
                mock(OaSignOnboardExcelParser.class), batchMapper, rowMapper, requestMapper,
                mock(OaSignHrAccessService.class), scope, remoteUserService,
                onboardRule, planMapper, taskMapper, eventFactory, companyService, objectMapper);
        ReflectionTestUtils.setField(service, "enabled", true);
        ReflectionTestUtils.setField(service, "taskOrchestrator", taskOrchestrator);
        allowNewSendClaim(service);
        SecurityContextHolder.setUserId("101");

        OaSignOnboardImportBatch batch = new OaSignOnboardImportBatch();
        batch.setBatchId(31L); batch.setCreatedByUserId(101L); batch.setShopDeptId(1171L);
        OaSignOnboardContractSnapshot snapshot = completeLaborSnapshot();
        snapshot.setProfileFactsHash("b".repeat(64));
        OaSignOnboardImportRow row = new OaSignOnboardImportRow();
        row.setRowId(71L); row.setBatchId(31L); row.setSourceRowNumber(2);
        row.setEmployeeId(42L); row.setMatchType("EXCEL_PHONE_NAME"); row.setVersion(9L);
        row.setHistoricalSupplement(true); row.setHistoricalReason("历史补签");
        row.setNoExternalContractConfirmed(true);
        SignCandidateUser employee = candidate(42L, "段继康", "13800000000",
                "330102199001011234");

        SysLegalEntity incompleteCompany = new SysLegalEntity();
        incompleteCompany.setLegalEntityId(18L);
        incompleteCompany.setLegalEntityCode("ZS-MH");
        incompleteCompany.setLegalEntityName("舟山茗汇文化传播有限公司");
        incompleteCompany.setLegalRepresentative("杜翠香");
        incompleteCompany.setVersion(3L);
        OaSignCompanyService.CompanyMatchResult companyMatch =
                new OaSignCompanyService.CompanyMatchResult(incompleteCompany, List.of(), null,
                        "EXCEL_AUTO", new BigDecimal("0.6500"), new BigDecimal("0.2500"),
                        new BigDecimal("0.6000"), new BigDecimal("0.0800"), false);
        when(companyService.matchExcelCompany(any(), any(), any(), any()))
                .thenReturn(companyMatch);
        when(companyService.currentMatchPolicyVersion())
                .thenReturn("COMPANY_MATCH_V1_0123456789ab");
        when(companyService.contractMasterMissingFields(incompleteCompany))
                .thenReturn(List.of("统一社会信用代码", "注册地址"));
        when(companyService.recommendContractSeal(18L)).thenReturn(
                new OaSignCompanyService.SealRecommendation(null, List.of(),
                        "HR_REQUIRED_NO_ACTIVE_SEAL"));
        HrSignBusinessEvent stagedEvent = new HrSignBusinessEvent();
        stagedEvent.setAttributes(new LinkedHashMap<>());
        when(eventFactory.create(any(), any(), any(), any(), any(), any()))
                .thenReturn(stagedEvent);
        OaSignDraftDecision decision = new OaSignDraftDecision();
        decision.setAction(OaSignDraftDecision.Action.CREATE_DRAFT);
        decision.setPlanVersionId(11L);
        OaSignPackage draft = new OaSignPackage();
        draft.setSalaryVersion("B");
        decision.setDraftPackage(draft);
        when(onboardRule.decide(any())).thenReturn(decision);
        OaSignPlanVersion version = new OaSignPlanVersion();
        version.setVersionId(11L); version.setVersionHash("plan-v11");
        version.setPlanName("入职劳动合同 B 版");
        when(planMapper.selectPlanVersionById(11L)).thenReturn(version);
        when(planMapper.selectTemplatesByVersionId(11L)).thenReturn(completeLaborTemplates());

        ReflectionTestUtils.invokeMethod(service, "evaluate", batch, row, snapshot, employee,
                List.of());

        assertThat(service.list(row.getErrorCodesJson())).containsExactly(
                "COMPANY_MASTER_DATA_INCOMPLETE", "COMPANY_SEAL_REQUIRES_HR");
        assertThat(row.getStatus()).isEqualTo("CONFLICT");
        assertThat(row.getPlanVersionId()).isEqualTo(11L);
        assertThat(row.getPlanVersionHash()).isEqualTo("plan-v11");
        assertThat(row.getCompanyMatchPolicyVersion())
                .isEqualTo("COMPANY_MATCH_V1_0123456789ab");
        assertThat(service.signatureRequestBlockers(row, service.snapshot(row))).isEmpty();
        OaSignOnboardImportRowView view = ReflectionTestUtils.invokeMethod(service,
                "toView", row, new LinkedHashMap<>(), null, null);
        assertThat(view.getSignatureRequestable()).isTrue();
        assertThat(view.getTemplateNames()).contains("劳动合同",
                "薪酬结构确认书（B版）");

        when(batchMapper.selectById(31L)).thenReturn(batch);
        when(scope.resolveScopeDeptIds(1171L)).thenReturn(List.of(1171L));
        when(rowMapper.selectByBatchId(31L)).thenReturn(List.of(row));
        AtomicReference<OaSignOnboardDataRequest> inserted = new AtomicReference<>();
        when(requestMapper.insertRequest(any())).thenAnswer(invocation -> {
            OaSignOnboardDataRequest value = invocation.getArgument(0);
            value.setRequestId(501L);
            inserted.set(value);
            return 1;
        });
        when(requestMapper.selectById(501L)).thenAnswer(invocation -> inserted.get());
        when(remoteUserService.listSignCandidates(any(), anyString()))
                .thenReturn(R.ok(List.of(employee)));
        OaSignPackage staged = new OaSignPackage();
        staged.setTaskId(91L); staged.setPackageId(90L); staged.setPlanVersionId(11L);
        when(taskOrchestrator.stageSignatureFirstPackageInCurrentTransaction(stagedEvent))
                .thenReturn(staged);
        when(rowMapper.linkStagedPackage(71L, 501L, 91L, 90L, 9L,
                "WAITING_EMPLOYEE_DATA", 9L))
                .thenAnswer(invocation -> {
                    row.setDataRequestId(501L);
                    row.setTaskId(91L);
                    row.setPackageId(90L);
                    row.setStatus("WAITING_EMPLOYEE_DATA");
                    return 1;
                });
        OaSignOnboardDataRequestSendRequest action = new OaSignOnboardDataRequestSendRequest();
        action.setRequestId("signature-first-company-pending");
        action.setSigningSequence("SIGNATURE_FIRST");
        action.setRowIds(List.of(71L));

        service.sendDataRequests(31L, action, 1171L);

        assertThat(inserted.get()).isNotNull();
        assertThat(inserted.get().getSigningSequence()).isEqualTo("SIGNATURE_FIRST");
        verify(rowMapper).linkStagedPackage(71L, 501L, 91L, 90L, 9L,
                "WAITING_EMPLOYEE_DATA", 9L);
        assertThat(stagedEvent.getAttributes())
                .containsEntry("signingSequence", "SIGNATURE_FIRST");
    }

    @Test
    void employeeConfirmationHashIgnoresCompanyButRejectsSalaryAndPlanChanges()
    {
        OaSignPlanVersionMapper planMapper = mock(OaSignPlanVersionMapper.class);
        AtomicReference<List<OaSignPlanVersionTemplate>> templates =
                new AtomicReference<>(completeLaborTemplates());
        when(planMapper.selectTemplatesByVersionId(11L))
                .thenAnswer(invocation -> templates.get());
        OaSignOnboardImportService service = service(planMapper);
        OaSignOnboardContractSnapshot snapshot = completeLaborSnapshot();
        snapshot.setMatchedLegalEntityId(10L);
        snapshot.setMatchedLegalEntityName("原公司");
        snapshot.setMatchedSealId(20L);
        snapshot.setMatchedSealImageHash("seal-one");
        OaSignOnboardImportRow row = new OaSignOnboardImportRow();
        row.setRowId(71L); row.setEmployeeId(42L); row.setPlanVersionId(11L);
        row.setSnapshotJson(service.json(snapshot));
        OaSignOnboardImportService.EmployeeConfirmationSnapshot frozen =
                service.employeeConfirmationSnapshot(row, snapshot);
        OaSignOnboardDataRequest request = new OaSignOnboardDataRequest();
        request.setFactSnapshotJson(frozen.json());
        request.setConfirmationSnapshotVersion(frozen.version());
        request.setConfirmationSnapshotHash(frozen.hash());

        snapshot.setMatchedLegalEntityId(99L);
        snapshot.setMatchedLegalEntityName("后选公司");
        snapshot.setMatchedSealId(88L);
        snapshot.setMatchedSealImageHash("seal-two");
        row.setSnapshotJson(service.json(snapshot));
        service.requireEmployeeConfirmationCurrent(request, row, Map.of());

        snapshot.setSalaryTotal(new BigDecimal("5000"));
        row.setSnapshotJson(service.json(snapshot));
        assertThatThrownBy(() -> service.requireEmployeeConfirmationCurrent(
                request, row, Map.of()))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("需要HR重新发起");

        snapshot.setSalaryTotal(new BigDecimal("9000"));
        row.setSnapshotJson(service.json(snapshot));
        List<OaSignPlanVersionTemplate> renamed = completeLaborTemplates();
        renamed.get(1).setTemplateName("劳动合同-新版");
        templates.set(renamed);
        assertThatThrownBy(() -> service.requireEmployeeConfirmationCurrent(
                request, row, Map.of()))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("计划文件已变化");
    }

    @Test
    void frozenEmployeeConfirmationAcceptsFactsReorderedByJsonStorage() throws Exception
    {
        OaSignPlanVersionMapper planMapper = mock(OaSignPlanVersionMapper.class);
        when(planMapper.selectTemplatesByVersionId(11L)).thenReturn(completeLaborTemplates());
        OaSignOnboardImportService service = service(planMapper);
        OaSignOnboardImportRow row = new OaSignOnboardImportRow();
        row.setPlanVersionId(11L);
        OaSignOnboardImportService.EmployeeConfirmationSnapshot original =
                service.employeeConfirmationSnapshot(row, completeLaborSnapshot());

        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
        Map<String, Object> root = objectMapper.readValue(original.json(),
                new TypeReference<LinkedHashMap<String, Object>>() { });
        @SuppressWarnings("unchecked")
        Map<String, Object> originalFacts = (Map<String, Object>) root.get("facts");
        List<Map.Entry<String, Object>> reversedEntries =
                new ArrayList<>(originalFacts.entrySet());
        Collections.reverse(reversedEntries);
        Map<String, Object> reorderedFacts = new LinkedHashMap<>();
        reversedEntries.forEach(entry -> reorderedFacts.put(entry.getKey(), entry.getValue()));
        root.put("facts", reorderedFacts);

        OaSignOnboardDataRequest request = new OaSignOnboardDataRequest();
        request.setFactSnapshotJson(objectMapper.writeValueAsString(root));
        request.setConfirmationSnapshotVersion(original.version());
        request.setConfirmationSnapshotHash(original.hash());

        OaSignOnboardImportService.EmployeeConfirmationSnapshot restored =
                service.requireFrozenEmployeeConfirmation(request);

        assertThat(restored.hash()).isEqualTo(original.hash());
        assertThat(restored.facts()).isEqualTo(original.facts());
    }

    @Test
    void frozenEmployeeConfirmationStillRejectsTamperingAfterFactsAreReordered()
            throws Exception
    {
        OaSignPlanVersionMapper planMapper = mock(OaSignPlanVersionMapper.class);
        when(planMapper.selectTemplatesByVersionId(11L)).thenReturn(completeLaborTemplates());
        OaSignOnboardImportService service = service(planMapper);
        OaSignOnboardImportRow row = new OaSignOnboardImportRow();
        row.setPlanVersionId(11L);
        OaSignOnboardImportService.EmployeeConfirmationSnapshot original =
                service.employeeConfirmationSnapshot(row, completeLaborSnapshot());

        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
        Map<String, Object> root = objectMapper.readValue(original.json(),
                new TypeReference<LinkedHashMap<String, Object>>() { });
        @SuppressWarnings("unchecked")
        Map<String, Object> originalFacts = (Map<String, Object>) root.get("facts");
        List<Map.Entry<String, Object>> reversedEntries =
                new ArrayList<>(originalFacts.entrySet());
        Collections.reverse(reversedEntries);
        Map<String, Object> reorderedFacts = new LinkedHashMap<>();
        reversedEntries.forEach(entry -> reorderedFacts.put(entry.getKey(), entry.getValue()));
        reorderedFacts.put("salaryTotal", "9999");
        root.put("facts", reorderedFacts);

        OaSignOnboardDataRequest request = new OaSignOnboardDataRequest();
        request.setFactSnapshotJson(objectMapper.writeValueAsString(root));
        request.setConfirmationSnapshotVersion(original.version());
        request.setConfirmationSnapshotHash(original.hash());

        assertThatThrownBy(() -> service.requireFrozenEmployeeConfirmation(request))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("员工确认快照校验不一致");
    }

    @Test
    void batchRowExposesCurrentDataRequestSequenceAndCapturedFact()
    {
        OaSignOnboardDataRequestMapper dataRequestMapper =
                mock(OaSignOnboardDataRequestMapper.class);
        OaSignOnboardImportService service = new OaSignOnboardImportService(
                mock(OaSignOnboardExcelParser.class), mock(OaSignOnboardImportBatchMapper.class),
                mock(OaSignOnboardImportRowMapper.class), dataRequestMapper,
                mock(OaSignHrAccessService.class), mock(ShopScopeService.class),
                mock(RemoteUserService.class), mock(OnboardSignScenarioRule.class),
                mock(OaSignPlanVersionMapper.class), mock(OaSignTaskMapper.class),
                mock(OaOnboardSignEventFactory.class), mock(OaSignCompanyService.class),
                new ObjectMapper().findAndRegisterModules());
        OaSignOnboardImportRow row = new OaSignOnboardImportRow();
        row.setRowId(71L); row.setEmployeeId(42L); row.setDataRequestId(501L);
        row.setSnapshotJson("{}"); row.setErrorCodesJson("[]");
        row.setWarningCodesJson("[]"); row.setMissingFieldsJson("[]");
        OaSignOnboardDataRequest request = new OaSignOnboardDataRequest();
        request.setRequestId(501L); request.setRowId(71L); request.setEmployeeId(42L);
        request.setSigningSequence("SIGNATURE_FIRST"); request.setStatus("COMPLETED");
        request.setSignatureSampleHash("a".repeat(64));
        request.setSignatureSampleTime(new Date());
        when(dataRequestMapper.selectById(501L)).thenReturn(request);

        OaSignOnboardImportRowView view = ReflectionTestUtils.invokeMethod(service,
                "toView", row, new LinkedHashMap<>(), null, null);

        assertThat(view.getDataRequestSigningSequence()).isEqualTo("SIGNATURE_FIRST");
        assertThat(view.getDataRequestSignatureCaptured()).isTrue();
    }

    @Test
    void authorizedHrBatchRowsExposeFullNameAndPhoneButKeepOtherIdentityMasked()
            throws Exception
    {
        OaSignOnboardImportService service = service();
        OaSignOnboardContractSnapshot snapshot = identity(
                "马晓红", "13800138000", "330102199001011234");
        snapshot.setCurrentAddress("杭州市余杭区测试路 1 号");
        OaSignOnboardImportRow row = new OaSignOnboardImportRow();
        row.setEmployeeNameMasked("马**");
        row.setPhoneMasked("138****8000");
        row.setIdNumberMasked("3301**********1234");
        row.setAddressMasked("杭州市余杭***");
        row.setSnapshotJson(new ObjectMapper().findAndRegisterModules()
                .writeValueAsString(snapshot));
        row.setErrorCodesJson("[]");
        row.setWarningCodesJson("[]");
        row.setMissingFieldsJson("[]");

        OaSignOnboardImportRowView view = ReflectionTestUtils.invokeMethod(service,
                "toView", row, new LinkedHashMap<>(), null, null);

        assertThat(view.getEmployeeName()).isEqualTo("马晓红");
        assertThat(view.getPhone()).isEqualTo("13800138000");
        assertThat(view.getIdNumber()).isEqualTo("3301**********1234");
        assertThat(view.getCurrentAddress()).isEqualTo("杭州市余杭***");
    }

    @Test
    void signatureFirstRequestPersistsFrozenFactsAndDocumentList()
    {
        OaSignOnboardImportBatchMapper batchMapper = mock(OaSignOnboardImportBatchMapper.class);
        OaSignOnboardImportRowMapper rowMapper = mock(OaSignOnboardImportRowMapper.class);
        OaSignOnboardDataRequestMapper requestMapper = mock(OaSignOnboardDataRequestMapper.class);
        OaSignPlanVersionMapper planMapper = mock(OaSignPlanVersionMapper.class);
        ShopScopeService scope = mock(ShopScopeService.class);
        RemoteUserService remoteUserService = mock(RemoteUserService.class);
        OaSignTaskOrchestrator taskOrchestrator = mock(OaSignTaskOrchestrator.class);
        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
        OaSignOnboardImportService service = new OaSignOnboardImportService(
                mock(OaSignOnboardExcelParser.class), batchMapper, rowMapper, requestMapper,
                mock(OaSignHrAccessService.class), scope, remoteUserService,
                mock(OnboardSignScenarioRule.class), planMapper, mock(OaSignTaskMapper.class),
                new OaOnboardSignEventFactory(), mock(OaSignCompanyService.class),
                objectMapper);
        ReflectionTestUtils.setField(service, "enabled", true);
        ReflectionTestUtils.setField(service, "taskOrchestrator", taskOrchestrator);
        allowNewSendClaim(service);
        SecurityContextHolder.setUserId("101");

        OaSignOnboardImportBatch batch = new OaSignOnboardImportBatch();
        batch.setBatchId(31L); batch.setCreatedByUserId(101L); batch.setShopDeptId(1171L);
        when(batchMapper.selectById(31L)).thenReturn(batch);
        when(scope.resolveScopeDeptIds(1171L)).thenReturn(List.of(1171L));
        when(planMapper.selectTemplatesByVersionId(11L)).thenReturn(completeLaborTemplates());
        OaSignOnboardContractSnapshot snapshot = completeLaborSnapshot();
        snapshot.setProfileFactsHash("b".repeat(64));
        OaSignOnboardImportRow row = new OaSignOnboardImportRow();
        row.setRowId(71L); row.setBatchId(31L); row.setEmployeeId(42L);
        row.setSourceRowNumber(2);
        row.setPlanVersionId(11L); row.setVersion(9L); row.setSnapshotJson(service.json(snapshot));
        row.setErrorCodesJson("[]"); row.setWarningCodesJson("[]");
        row.setMissingFieldsJson("[]"); row.setStatus("READY_TO_GENERATE");
        when(rowMapper.selectByBatchId(31L)).thenReturn(List.of(row));
        AtomicReference<OaSignOnboardDataRequest> inserted = new AtomicReference<>();
        when(requestMapper.insertRequest(any())).thenAnswer(invocation -> {
            OaSignOnboardDataRequest value = invocation.getArgument(0);
            value.setRequestId(501L);
            inserted.set(value);
            return 1;
        });
        when(requestMapper.selectById(501L)).thenAnswer(invocation -> inserted.get());
        SignCandidateUser employee = candidate(42L, snapshot.getEmployeeName(),
                snapshot.getPhone(), snapshot.getIdNumber());
        employee.setDeptId(1171L);
        when(remoteUserService.listSignCandidates(any(), anyString()))
                .thenReturn(R.ok(List.of(employee)));
        OaSignPackage staged = new OaSignPackage();
        staged.setTaskId(91L); staged.setPackageId(90L); staged.setPlanVersionId(11L);
        when(taskOrchestrator.stageSignatureFirstPackageInCurrentTransaction(any()))
                .thenReturn(staged);
        when(rowMapper.linkStagedPackage(71L, 501L, 91L, 90L, 9L,
                "WAITING_EMPLOYEE_DATA", 9L))
                .thenAnswer(invocation -> {
                    row.setDataRequestId(501L);
                    row.setTaskId(91L);
                    row.setPackageId(90L);
                    row.setStatus("WAITING_EMPLOYEE_DATA");
                    return 1;
                });
        OaSignOnboardDataRequestSendRequest action = new OaSignOnboardDataRequestSendRequest();
        action.setRequestId("signature-first-1");
        action.setSigningSequence("SIGNATURE_FIRST");
        action.setRowIds(List.of(71L));

        service.sendDataRequests(31L, action, 1171L);

        assertThat(inserted.get()).isNotNull();
        assertThat(inserted.get().getSigningSequence()).isEqualTo("SIGNATURE_FIRST");
        assertThat(inserted.get().getConfirmationSnapshotVersion())
                .isEqualTo(OaSignOnboardImportService.CONFIRMATION_SNAPSHOT_VERSION);
        assertThat(inserted.get().getConfirmationSnapshotHash()).hasSize(64);
        assertThat(inserted.get().getFactSnapshotJson())
                .contains("\"snapshotVersion\":\"ONBOARD_CONFIRM_V1\"")
                .contains("\"plannedDocumentNames\"")
                .contains("\u52b3\u52a8\u5408\u540c", "\u85aa\u916c\u7ed3\u6784\u786e\u8ba4\u4e66\uff08B\u7248\uff09")
                .doesNotContain("matchedLegalEntityId", "matchedSealId",
                        "matchedSealImageUrl", "profileFactsHash");
        verify(rowMapper).linkStagedPackage(71L, 501L, 91L, 90L, 9L,
                "WAITING_EMPLOYEE_DATA", 9L);
        verify(rowMapper, never()).linkDataRequest(anyLong(), anyLong(), anyString(), anyLong());
        ArgumentCaptor<HrSignBusinessEvent> stagedEvent =
                ArgumentCaptor.forClass(HrSignBusinessEvent.class);
        verify(taskOrchestrator).stageSignatureFirstPackageInCurrentTransaction(
                stagedEvent.capture());
        assertThat(stagedEvent.getValue().getSourceEventVersion()).isEqualTo(9L);
        assertThat(stagedEvent.getValue().getEventId())
                .isEqualTo("OA-ONBOARD-EXCEL:71:9");
        assertThat(stagedEvent.getValue().getAttributes())
                .containsEntry("signingSequence", "SIGNATURE_FIRST");
    }

    @Test
    void stagedFirstPackageDoesNotInflateFinalGeneratedBatchSummary()
    {
        OaSignOnboardImportBatchMapper batchMapper = mock(OaSignOnboardImportBatchMapper.class);
        OaSignOnboardImportRowMapper rowMapper = mock(OaSignOnboardImportRowMapper.class);
        OaSignOnboardImportService service = new OaSignOnboardImportService(
                mock(OaSignOnboardExcelParser.class), batchMapper, rowMapper,
                mock(OaSignOnboardDataRequestMapper.class), mock(OaSignHrAccessService.class),
                mock(ShopScopeService.class), mock(RemoteUserService.class),
                mock(OnboardSignScenarioRule.class), mock(OaSignPlanVersionMapper.class),
                mock(OaSignTaskMapper.class), mock(OaOnboardSignEventFactory.class),
                mock(OaSignCompanyService.class), new ObjectMapper().findAndRegisterModules());
        OaSignOnboardImportRow staged = summaryRow(
                "WAITING_EMPLOYEE_DATA", 91L, 90L);
        OaSignOnboardImportRow generated = summaryRow("GENERATED", 92L, 93L);
        when(rowMapper.selectByBatchId(31L)).thenReturn(List.of(staged, generated));

        service.refreshSummary(31L);

        verify(batchMapper).updateSummary(31L, "PARTIAL_GENERATED",
                2, 0, 0, 0, 1);
    }

    @Test
    void completedRequestCreatesNewRoundWithoutReusingOldApprovalOrHrOnlyFields() throws Exception
    {
        DataRequestFixture fixture = new DataRequestFixture("COMPLETED", "[\"currentAddress\"]");
        when(fixture.dataRequestMapper.insertRequest(any())).thenAnswer(invocation -> {
            OaSignOnboardDataRequest inserted = invocation.getArgument(0);
            inserted.setRequestId(502L);
            return 1;
        });
        when(fixture.rowMapper.linkDataRequest(71L, 502L, "WAITING_EMPLOYEE_DATA", 9L))
                .thenReturn(1);

        fixture.service.sendDataRequests(31L, fixture.sendRequest(), 1171L);

        ArgumentCaptor<OaSignOnboardDataRequest> inserted =
                ArgumentCaptor.forClass(OaSignOnboardDataRequest.class);
        verify(fixture.dataRequestMapper).insertRequest(inserted.capture());
        assertThat(inserted.getValue().getRequestId()).isEqualTo(502L);
        assertThat(inserted.getValue().getProfileSyncRequestId()).isEqualTo("OPS:71:9");
        assertThat(inserted.getValue().getProfileBeforeHash()).isEqualTo("profile-after-first-round");
        assertThat(fixture.objectMapper.readValue(inserted.getValue().getAllowedFieldsJson(), List.class))
                .containsExactly("currentAddress", "studentStatus", "schoolName", "retirementStatus")
                .doesNotContain("servicePersonType", "insuranceType");
        verify(fixture.dataRequestMapper, never()).refreshEmployeeFields(anyLong(), anyString(),
                anyString(), anyString(), anyString(), anyString(), anyString(), anyString(),
                anyLong());
    }

    @Test
    void pendingEmployeeRequestRefreshesChangedWhitelistAndReturnsToEmployeeTodo()
    {
        DataRequestFixture fixture = new DataRequestFixture("PENDING_EMPLOYEE",
                "[\"currentAddress\"]");
        when(fixture.dataRequestMapper.refreshEmployeeFields(501L, fixture.currentAllowedJson(),
                "COMPANY_FIRST", fixture.factSnapshotJson,
                fixture.confirmationVersion, fixture.confirmationHash, "OPS:71:9",
                "profile-after-first-round", 4L)).thenReturn(1);
        when(fixture.rowMapper.linkDataRequest(71L, 501L, "WAITING_EMPLOYEE_DATA", 9L))
                .thenReturn(1);

        fixture.service.sendDataRequests(31L, fixture.sendRequest(), 1171L);

        verify(fixture.dataRequestMapper).refreshEmployeeFields(501L, fixture.currentAllowedJson(),
                "COMPANY_FIRST", fixture.factSnapshotJson,
                fixture.confirmationVersion, fixture.confirmationHash, "OPS:71:9",
                "profile-after-first-round", 4L);
        verify(fixture.rowMapper).linkDataRequest(71L, 501L, "WAITING_EMPLOYEE_DATA", 9L);
        verify(fixture.dataRequestMapper, never()).insertRequest(any());
    }

    @Test
    void submittedRequestWithChangedWhitelistBlocksInsteadOfSilentlyHandling()
    {
        DataRequestFixture fixture = new DataRequestFixture("SUBMITTED", "[\"currentAddress\"]");

        assertThatThrownBy(() -> fixture.service.sendDataRequests(
                31L, fixture.sendRequest(), 1171L))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("先完成或驳回当前轮次");
        verify(fixture.dataRequestMapper, never()).refreshEmployeeFields(anyLong(), anyString(),
                anyString(), anyString(), anyString(), anyString(), anyString(), anyString(),
                anyLong());
        verify(fixture.dataRequestMapper, never()).insertRequest(any());
    }

    private OaSignOnboardImportService service()
    {
        return service(mock(OaSignPlanVersionMapper.class));
    }

    private static OaSignOnboardImportRow summaryRow(String status, Long taskId, Long packageId)
    {
        OaSignOnboardImportRow row = new OaSignOnboardImportRow();
        row.setEmployeeId(taskId);
        row.setMatchType("PHONE_NAME_EXACT");
        row.setTaskId(taskId);
        row.setPackageId(packageId);
        row.setStatus(status);
        row.setErrorCodesJson("[]");
        row.setWarningCodesJson("[]");
        return row;
    }

    private OaSignOnboardImportService service(OaSignPlanVersionMapper planVersionMapper)
    {
        return new OaSignOnboardImportService(mock(OaSignOnboardExcelParser.class),
                mock(OaSignOnboardImportBatchMapper.class),
                mock(OaSignOnboardImportRowMapper.class),
                mock(OaSignOnboardDataRequestMapper.class), mock(OaSignHrAccessService.class),
                mock(ShopScopeService.class), mock(RemoteUserService.class),
                mock(OnboardSignScenarioRule.class), planVersionMapper,
                mock(OaSignTaskMapper.class), mock(OaOnboardSignEventFactory.class),
                mock(OaSignCompanyService.class), new ObjectMapper().findAndRegisterModules());
    }

    private static OaSignOnboardSendRequestMapper allowNewSendClaim(
            OaSignOnboardImportService service)
    {
        OaSignOnboardSendRequestMapper mapper = mock(OaSignOnboardSendRequestMapper.class);
        AtomicReference<OaSignOnboardSendRequest> persisted = new AtomicReference<>();
        when(mapper.claim(any())).thenAnswer(invocation -> {
            OaSignOnboardSendRequest claim = invocation.getArgument(0);
            claim.setOperationId(901L);
            persisted.set(claim);
            return 1;
        });
        when(mapper.selectByRequestId(anyString())).thenAnswer(invocation -> persisted.get());
        ReflectionTestUtils.setField(service, "sendRequestMapper", mapper);
        return mapper;
    }

    @Test
    void dataRequestSendReplaysSameCanonicalPayloadAndRejectsDifferentPayload()
    {
        OaSignOnboardImportService service = service();
        OaSignOnboardSendRequestMapper mapper = mock(OaSignOnboardSendRequestMapper.class);
        AtomicReference<OaSignOnboardSendRequest> persisted = new AtomicReference<>();
        when(mapper.claim(any())).thenAnswer(invocation -> {
            OaSignOnboardSendRequest candidate = invocation.getArgument(0);
            if (persisted.get() == null)
            {
                OaSignOnboardSendRequest first = new OaSignOnboardSendRequest();
                first.setRequestId(candidate.getRequestId());
                first.setBatchId(candidate.getBatchId());
                first.setOperatorUserId(candidate.getOperatorUserId());
                first.setPayloadHash(candidate.getPayloadHash());
                first.setClaimToken(candidate.getClaimToken());
                persisted.set(first);
            }
            return persisted.get() == candidate ? 1 : 2;
        });
        when(mapper.selectByRequestId("send-idempotency-1"))
                .thenAnswer(invocation -> persisted.get());
        ReflectionTestUtils.setField(service, "sendRequestMapper", mapper);
        SecurityContextHolder.setUserId("101");

        Boolean first = ReflectionTestUtils.invokeMethod(service, "claimDataRequestSend",
                "send-idempotency-1", 31L, "COMPANY_FIRST", Set.of(72L, 71L));
        Boolean replay = ReflectionTestUtils.invokeMethod(service, "claimDataRequestSend",
                "send-idempotency-1", 31L, "COMPANY_FIRST", Set.of(71L, 72L));

        assertThat(first).isFalse();
        assertThat(replay).isTrue();
        assertThatThrownBy(() -> ReflectionTestUtils.invokeMethod(service,
                "claimDataRequestSend", "send-idempotency-1", 31L,
                "SIGNATURE_FIRST", Set.of(71L, 72L)))
                .isInstanceOf(ServiceException.class)
                .hasMessage("补资料请求编号已用于不同内容");
    }

    private OaSignOnboardContractSnapshot identity(String name, String phone, String idNumber)
    {
        OaSignOnboardContractSnapshot value = new OaSignOnboardContractSnapshot();
        value.setEmployeeName(name);
        value.setPhone(phone);
        value.setIdNumber(idNumber);
        return value;
    }

    private OaSignOnboardExcelParser.ParsedRow parsed(OaSignOnboardContractSnapshot snapshot,
            int sourceRow)
    {
        return new OaSignOnboardExcelParser.ParsedRow(sourceRow,
                ("row-" + sourceRow).repeat(16).substring(0, 64), snapshot, List.of());
    }

    private SignCandidateUser candidate(Long userId, String name, String phone, String idNumber)
    {
        SignCandidateUser value = new SignCandidateUser();
        value.setUserId(userId);
        value.setNickName(name);
        value.setPhonenumber(phone);
        value.setIdNumber(idNumber);
        return value;
    }

    private OaSignPlanVersionTemplate template(String type)
    {
        OaSignPlanVersionTemplate template = new OaSignPlanVersionTemplate();
        template.setTemplateType(type);
        return template;
    }

    private OaSignOnboardContractSnapshot completeLaborSnapshot()
    {
        OaSignOnboardContractSnapshot snapshot = identity("\u6bb5\u7ee7\u5eb7", "13800000000",
                "330102199001011234");
        snapshot.setCurrentAddress("\u676d\u5dde\u5e02\u4f59\u676d\u533a\u6d4b\u8bd5\u8def 1 \u53f7");
        snapshot.setContractTypeCode(SigningProfileCodes.LABOR_CONTRACT);
        snapshot.setSocialTypeCode(SigningProfileCodes.SOCIAL_INSURED);
        snapshot.setEmployeePost("\u884c\u653f\u7ecf\u7406");
        snapshot.setJobGradeCode("3");
        snapshot.setWorkLocation("\u676d\u5dde\u5e02");
        snapshot.setCityLevel("\u4e00\u7ebf");
        snapshot.setContractTermCode(SigningProfileCodes.FIXED_TERM);
        snapshot.setContractStartDate(LocalDate.of(2026, 6, 16));
        snapshot.setContractEndDate(LocalDate.of(2029, 6, 16));
        snapshot.setProbationStartDate(LocalDate.of(2026, 6, 16));
        snapshot.setProbationEndDate(LocalDate.of(2026, 9, 15));
        snapshot.setWorkSchedule("STANDARD");
        snapshot.setSalaryTotal(new BigDecimal("9000"));
        snapshot.setBaseSalary(new BigDecimal("3300"));
        snapshot.setPostSalary(new BigDecimal("2700"));
        snapshot.setFieldAllowance(new BigDecimal("1000"));
        snapshot.setPerformanceSalary(new BigDecimal("2000"));
        snapshot.setSalaryVersion("B");
        snapshot.setStudentStatus("NON_STUDENT");
        snapshot.setRetirementStatus("NOT_RETIRED");
        snapshot.setIncomeStartYearMonth("2026-06");
        return snapshot;
    }

    private List<OaSignPlanVersionTemplate> completeLaborTemplates()
    {
        OaSignPlanVersionTemplate commitment = template(OaSignTemplateType.ONBOARD_COMMITMENT);
        commitment.setTemplateName("\u5165\u804c\u627f\u8bfa\u4e66");
        OaSignPlanVersionTemplate contract = template(OaSignTemplateType.ONBOARD_LABOR_CONTRACT);
        contract.setTemplateName("\u52b3\u52a8\u5408\u540c");
        OaSignPlanVersionTemplate handbook = template(OaSignTemplateType.ONBOARD_HANDBOOK_RECEIPT);
        handbook.setTemplateName("\u5458\u5de5\u624b\u518c\u7b7e\u6536\u786e\u8ba4\u4e66");
        OaSignPlanVersionTemplate salary = template(OaSignTemplateType.ONBOARD_SALARY_CONFIRM);
        salary.setTemplateName("\u85aa\u916c\u7ed3\u6784\u786e\u8ba4\u4e66\uff08B\u7248\uff09");
        return List.of(commitment, contract, handbook, salary);
    }

    private static OaSignOnboardImportRow canonicalSourceRow()
    {
        OaSignOnboardImportRow row = new OaSignOnboardImportRow();
        row.setRowId(1876L);
        row.setBatchId(134L);
        row.setEmployeeId(957L);
        row.setSourceEventVersion(3L);
        row.setTaskId(null);
        return row;
    }

    private static OaSignTask canonicalSourceTask(Long taskId, Long assignedHrUserId)
    {
        OaSignTask task = new OaSignTask();
        task.setTaskId(taskId);
        task.setScenario("ONBOARD");
        task.setEmployeeId(957L);
        task.setSourceType(OaOnboardSignEventFactory.SOURCE_TYPE);
        task.setSourceBusinessId("1876");
        task.setSourceEventVersion("3");
        task.setStatus("NEEDS_DATA");
        task.setAssignedHrUserId(assignedHrUserId);
        task.setShopDeptId(101L);
        return task;
    }

    private final class CanonicalOpenTaskEvaluationFixture
    {
        private final OaSignOnboardImportBatchMapper batchMapper =
                mock(OaSignOnboardImportBatchMapper.class);
        private final OaSignOnboardImportRowMapper rowMapper =
                mock(OaSignOnboardImportRowMapper.class);
        private final OaSignPlanVersionMapper planMapper =
                mock(OaSignPlanVersionMapper.class);
        private final OaSignTaskMapper taskMapper = mock(OaSignTaskMapper.class);
        private final OaOnboardSignEventFactory eventFactory =
                mock(OaOnboardSignEventFactory.class);
        private final OnboardSignScenarioRule onboardRule =
                mock(OnboardSignScenarioRule.class);
        private final OaSignCompanyService companyService =
                mock(OaSignCompanyService.class);
        private final ShopScopeService scope = mock(ShopScopeService.class);
        private final OaSignOnboardImportService service;
        private final OaSignOnboardImportBatch batch = new OaSignOnboardImportBatch();
        private final OaSignOnboardImportRow row = canonicalSourceRow();
        private final OaSignOnboardContractSnapshot snapshot = completeLaborSnapshot();
        private final SignCandidateUser employee;

        private CanonicalOpenTaskEvaluationFixture()
        {
            ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
            service = spy(new OaSignOnboardImportService(
                    mock(OaSignOnboardExcelParser.class), batchMapper, rowMapper,
                    mock(OaSignOnboardDataRequestMapper.class),
                    mock(OaSignHrAccessService.class), scope,
                    mock(RemoteUserService.class), onboardRule, planMapper, taskMapper,
                    eventFactory, companyService, objectMapper));
            ReflectionTestUtils.setField(service, "enabled", true);
            SecurityContextHolder.setUserId("101");

            batch.setBatchId(134L);
            batch.setShopDeptId(101L);
            batch.setSelectedCount(1);
            snapshot.setContractStartDate(LocalDate.now().plusDays(10));
            snapshot.setContractEndDate(LocalDate.now().plusYears(3));
            snapshot.setProbationStartDate(LocalDate.now().plusDays(10));
            snapshot.setProbationEndDate(LocalDate.now().plusMonths(3));
            snapshot.setProfileFactsHash("b".repeat(64));
            row.setSourceRowNumber(2);
            row.setVersion(5L);
            row.setStatus("GENERATE_FAILED");
            row.setNoExternalContractConfirmed(true);
            row.setHistoricalSupplement(false);
            row.setSnapshotJson(service.json(snapshot));
            row.setErrorCodesJson("[]");
            row.setWarningCodesJson("[]");
            row.setMissingFieldsJson("[]");

            employee = candidate(957L, snapshot.getEmployeeName(), snapshot.getPhone(),
                    snapshot.getIdNumber());
            employee.setDeptId(101L);
            employee.setProfileFactsHash(snapshot.getProfileFactsHash());
            when(batchMapper.selectById(134L)).thenReturn(batch);
            when(scope.resolveScopeDeptIds(101L)).thenReturn(List.of(101L));
            when(rowMapper.selectById(1876L)).thenReturn(row);
            doReturn(employee).when(service).requireCandidate(957L, 101L);

            SysLegalEntity company = new SysLegalEntity();
            company.setLegalEntityId(18L);
            company.setLegalEntityCode("ZS-MH");
            company.setLegalEntityName("舟山茗汇文化传播有限公司");
            company.setUnifiedSocialCreditCode("91330900TEST0018");
            company.setRegisteredAddress("舟山市测试路18号");
            company.setLegalRepresentative("测试法人");
            company.setContactPhone("0580-1234567");
            company.setVersion(3L);
            OaSignCompanyService.CompanyMatchResult companyMatch =
                    new OaSignCompanyService.CompanyMatchResult(company, List.of(), null,
                            "EXCEL_AUTO", new BigDecimal("1.0000"),
                            new BigDecimal("0.0000"), new BigDecimal("0.6000"),
                            new BigDecimal("0.0800"), false);
            when(companyService.matchExcelCompany(any(), any(), any(), any()))
                    .thenReturn(companyMatch);
            when(companyService.currentMatchPolicyVersion())
                    .thenReturn("COMPANY_MATCH_V1_0123456789ab");
            when(companyService.contractMasterMissingFields(company)).thenReturn(List.of());

            OaCompanySealConfig seal = new OaCompanySealConfig();
            seal.setSealId(8L);
            seal.setLegalEntityId(18L);
            seal.setSealName("合同章");
            seal.setSealImageUrl("/seal/8.png");
            seal.setSealImageHash("c".repeat(64));
            when(companyService.recommendContractSeal(18L)).thenReturn(
                    new OaSignCompanyService.SealRecommendation(seal, List.of(seal),
                            "AUTO_UNIQUE"));
            when(companyService.requireContractReadySeal(8L, 18L)).thenReturn(seal);

            HrSignBusinessEvent event = new HrSignBusinessEvent();
            event.setAttributes(new LinkedHashMap<>());
            when(eventFactory.create(any(), any(), any(), any(), any(), any()))
                    .thenReturn(event);
            OaSignDraftDecision decision = new OaSignDraftDecision();
            decision.setAction(OaSignDraftDecision.Action.CREATE_DRAFT);
            decision.setPlanVersionId(11L);
            OaSignPackage draft = new OaSignPackage();
            draft.setSalaryVersion("B");
            decision.setDraftPackage(draft);
            when(onboardRule.decide(event)).thenReturn(decision);
            OaSignPlanVersion version = new OaSignPlanVersion();
            version.setVersionId(11L);
            version.setVersionHash("plan-v11");
            when(planMapper.selectPlanVersionById(11L)).thenReturn(version);
            when(planMapper.selectTemplatesByVersionId(11L))
                    .thenReturn(completeLaborTemplates());
        }

        private void evaluate()
        {
            ReflectionTestUtils.invokeMethod(service, "evaluate", batch, row, snapshot,
                    employee, List.of());
        }
    }

    private final class DataRequestFixture
    {
        private final ObjectMapper objectMapper = new ObjectMapper();
        private final OaSignOnboardImportBatchMapper batchMapper = mock(OaSignOnboardImportBatchMapper.class);
        private final OaSignOnboardImportRowMapper rowMapper = mock(OaSignOnboardImportRowMapper.class);
        private final OaSignOnboardDataRequestMapper dataRequestMapper = mock(OaSignOnboardDataRequestMapper.class);
        private final OaSignOnboardImportService service;
        private final String factSnapshotJson;
        private final String confirmationVersion;
        private final String confirmationHash;

        private DataRequestFixture(String requestStatus, String existingAllowed)
        {
            OaSignHrAccessService access = mock(OaSignHrAccessService.class);
            ShopScopeService scope = mock(ShopScopeService.class);
            OaSignTaskMapper taskMapper = mock(OaSignTaskMapper.class);
            service = new OaSignOnboardImportService(mock(OaSignOnboardExcelParser.class),
                    batchMapper, rowMapper, dataRequestMapper, access, scope,
                    mock(RemoteUserService.class), mock(OnboardSignScenarioRule.class),
                    mock(OaSignPlanVersionMapper.class), taskMapper,
                    mock(OaOnboardSignEventFactory.class), mock(OaSignCompanyService.class),
                    objectMapper);
            ReflectionTestUtils.setField(service, "enabled", true);
            allowNewSendClaim(service);
            SecurityContextHolder.setUserId("101");

            OaSignOnboardImportBatch batch = new OaSignOnboardImportBatch();
            batch.setBatchId(31L);
            batch.setCreatedByUserId(101L);
            batch.setShopDeptId(1171L);
            when(batchMapper.selectById(31L)).thenReturn(batch);
            when(scope.resolveScopeDeptIds(1171L)).thenReturn(List.of(1171L));

            OaSignOnboardContractSnapshot snapshot = new OaSignOnboardContractSnapshot();
            snapshot.setContractTypeCode("SERVICE_CONTRACT");
            snapshot.setProfileFactsHash("profile-after-first-round");
            String sourceSnapshotJson = service.json(snapshot);
            OaSignOnboardImportRow row = new OaSignOnboardImportRow();
            row.setRowId(71L);
            row.setBatchId(31L);
            row.setSourceRowNumber(2);
            row.setEmployeeId(42L);
            row.setMatchType("ID_NUMBER");
            row.setStatus("NEEDS_HR_DATA");
            row.setDataRequestId(501L);
            row.setSnapshotJson(sourceSnapshotJson);
            row.setErrorCodesJson("[]");
            row.setWarningCodesJson("[]");
            row.setMissingFieldsJson("[]");
            row.setVersion(9L);
            OaSignOnboardImportService.EmployeeConfirmationSnapshot confirmation =
                    service.employeeConfirmationSnapshot(row, snapshot);
            factSnapshotJson = confirmation.json();
            confirmationVersion = confirmation.version();
            confirmationHash = confirmation.hash();
            row.setSnapshotJson(service.json(snapshot));
            when(rowMapper.selectByBatchId(31L)).thenReturn(List.of(row));

            OaSignOnboardDataRequest existing = new OaSignOnboardDataRequest();
            existing.setRequestId(501L);
            existing.setRowId(71L);
            existing.setStatus(requestStatus);
            existing.setAllowedFieldsJson(existingAllowed);
            existing.setSigningSequence("COMPANY_FIRST");
            existing.setFactSnapshotJson(factSnapshotJson);
            existing.setConfirmationSnapshotVersion(confirmationVersion);
            existing.setConfirmationSnapshotHash(confirmationHash);
            existing.setVersion(4L);
            when(dataRequestMapper.selectByRowId(71L)).thenReturn(existing);
        }

        private OaSignOnboardDataRequestSendRequest sendRequest()
        {
            OaSignOnboardDataRequestSendRequest request = new OaSignOnboardDataRequestSendRequest();
            request.setRequestId("send-round-2");
            request.setRowIds(List.of(71L));
            return request;
        }

        private String currentAllowedJson()
        {
            return "[\"currentAddress\",\"studentStatus\",\"schoolName\",\"retirementStatus\"]";
        }
    }
}
