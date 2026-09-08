package com.erp.oa.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import com.erp.common.core.exception.ServiceException;
import com.erp.oa.domain.OaSignOnboardContractSnapshot;
import com.erp.oa.domain.OaSignOnboardImportRow;
import com.erp.oa.domain.OaSignPackage;
import com.erp.oa.domain.OaSignTask;
import com.erp.oa.mapper.OaSignOnboardImportRowMapper;
import com.erp.oa.mapper.OaSignPackageMapper;
import com.erp.oa.service.IOaSignTaskService;
import com.erp.system.api.constant.SigningProfileCodes;
import com.fasterxml.jackson.databind.ObjectMapper;

@DisplayName("合同签约中心原模板导出")
class OaSignTaskExportServiceTest
{
    private IOaSignTaskService signTaskService;
    private OaSignPackageMapper signPackageMapper;
    private OaSignOnboardImportRowMapper importRowMapper;
    private ObjectMapper objectMapper;
    private OaSignTaskExportService service;

    @BeforeEach
    void setUp()
    {
        signTaskService = mock(IOaSignTaskService.class);
        signPackageMapper = mock(OaSignPackageMapper.class);
        importRowMapper = mock(OaSignOnboardImportRowMapper.class);
        objectMapper = new ObjectMapper().findAndRegisterModules();
        service = new OaSignTaskExportService(signTaskService, signPackageMapper,
                importRowMapper, objectMapper);
    }

    @Test
    @DisplayName("沿用浙江数据模板并追加签约状态且导出当前筛选的全部结果")
    void shouldReuseProvidedTemplateAndAppendSigningStatus() throws Exception
    {
        OaSignTask filter = new OaSignTask();
        filter.setScenario("ONBOARD");
        OaSignTask signed = task(193L, 301L, "SIGNED", "王淑娴");
        OaSignTask pending = task(194L, 302L, "PENDING_SIGN", "李晓");
        when(signTaskService.selectTaskList(filter, 88L)).thenReturn(List.of(signed, pending));

        OaSignPackage signedPackage = signPackage(301L, "王淑娴");
        signedPackage.setLegalEntityNameSnapshot("舟山茗汇文化传播有限公司");
        signedPackage.setLegalRepresentativeSnapshot("杜翠香");
        signedPackage.setLegalEntityAddressSnapshot("浙江省舟山市嵊泗县测试路9号");
        signedPackage.setEmployeeIdCardSnapshot("340823200501053729");
        signedPackage.setEmployeePhoneSnapshot("19516670059");
        signedPackage.setEmployeeAddressSnapshot("浙江省杭州市萧山区测试地址");
        signedPackage.setPostNameSnapshot("茶艺师");
        signedPackage.setPostLevelSnapshot("3");
        signedPackage.setEmploymentType(SigningProfileCodes.LABOR_CONTRACT);
        signedPackage.setSocialType(SigningProfileCodes.SOCIAL_UNINSURED);
        signedPackage.setContractTermCodeSnapshot(SigningProfileCodes.FIXED_TERM);
        signedPackage.setContractStartDate("2026-07-01");
        signedPackage.setContractEndDate("2029-07-01");
        signedPackage.setProbationStartDate("2026-07-01");
        signedPackage.setProbationEndDate("2026-10-01");
        signedPackage.setSalaryTotal(new BigDecimal("5000.00"));
        signedPackage.setBaseSalary(new BigDecimal("3300.00"));
        signedPackage.setPostSalary(BigDecimal.ZERO.setScale(2));
        signedPackage.setFieldAllowance(new BigDecimal("1500.00"));
        signedPackage.setPerformanceSalary(new BigDecimal("200.00"));

        OaSignPackage pendingPackage = signPackage(302L, "李晓");
        when(signPackageMapper.selectOaSignPackagesByIds(anyList()))
                .thenReturn(List.of(signedPackage, pendingPackage));

        OaSignOnboardContractSnapshot signedSnapshot = snapshot("王淑娴");
        signedSnapshot.setCityLevel("2级");
        signedSnapshot.setWorkLocation("杭州");
        signedSnapshot.setWorkSchedule("标准工时制");
        signedSnapshot.setRemarks("原模板数据");
        OaSignOnboardContractSnapshot pendingSnapshot = snapshot("李晓");
        OaSignOnboardImportRow signedRow = importRow(193L, signedSnapshot);
        OaSignOnboardImportRow pendingRow = importRow(194L, pendingSnapshot);
        when(importRowMapper.selectByTaskIds(anyList()))
                .thenReturn(List.of(pendingRow, signedRow));

        byte[] result = service.export(filter, 88L);
        verify(signTaskService).selectTaskList(filter, 88L);
        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(result)))
        {
            var sheet = workbook.getSheet("签约数据");
            assertThat(sheet).isNotNull();
            assertThat(sheet.getLastRowNum()).isEqualTo(2);
            DataFormatter formatter = new DataFormatter();
            List<String> headers = new ArrayList<>();
            for (int column = 0; column <= 26; column++)
                headers.add(formatter.formatCellValue(sheet.getRow(0).getCell(column)));
            List<String> expectedHeaders = new ArrayList<>(
                    OaSignTaskExportService.TEMPLATE_HEADERS);
            expectedHeaders.add("签约状态");
            assertThat(headers).containsExactlyElementsOf(expectedHeaders);

            assertThat(formatter.formatCellValue(sheet.getRow(1).getCell(1)))
                    .isEqualTo("王淑娴");
            assertThat(formatter.formatCellValue(sheet.getRow(1).getCell(2)))
                    .isEqualTo("无");
            assertThat(formatter.formatCellValue(sheet.getRow(1).getCell(4)))
                    .isEqualTo("2级");
            assertThat(formatter.formatCellValue(sheet.getRow(1).getCell(5)))
                    .isEqualTo("舟山茗汇文化传播有限公司");
            assertThat(formatter.formatCellValue(sheet.getRow(1).getCell(11)))
                    .isEqualTo("340823200501053729");
            assertThat(formatter.formatCellValue(sheet.getRow(1).getCell(13)))
                    .isEqualTo("19516670059");
            assertThat(DateUtil.isCellDateFormatted(sheet.getRow(1).getCell(15))).isTrue();
            assertThat(sheet.getRow(1).getCell(20).getNumericCellValue()).isEqualTo(5000d);
            assertThat(formatter.formatCellValue(sheet.getRow(1).getCell(25)))
                    .isEqualTo("原模板数据");
            assertThat(formatter.formatCellValue(sheet.getRow(1).getCell(26)))
                    .isEqualTo("已签约");
            assertThat(formatter.formatCellValue(sheet.getRow(2).getCell(26)))
                    .isEqualTo("待签署");
            assertThat(sheet.getRow(2).getCell(2).getCellType())
                    .isEqualTo(org.apache.poi.ss.usermodel.CellType.BLANK);
            assertThat(sheet.getRow(2).getCell(25).getCellType())
                    .isEqualTo(org.apache.poi.ss.usermodel.CellType.BLANK);
            assertThat(formatter.formatCellValue(sheet.getRow(1).getCell(1)))
                    .doesNotContain("王淑娴王淑娴");
            assertThat(sheet.getRow(0).getCell(26).getCellStyle())
                    .isEqualTo(sheet.getRow(0).getCell(25).getCellStyle());
            assertThat(sheet.getRow(1).getCell(26).getCellStyle())
                    .isEqualTo(sheet.getRow(1).getCell(25).getCellStyle());
            assertThat(sheet.getRow(0).getLastCellNum()).isEqualTo((short) 27);
            assertThat(sheet.getRow(2).getLastCellNum()).isEqualTo((short) 27);
        }
    }

    @Test
    @DisplayName("内置资源与聊天提供的原始模板字节一致")
    void shouldEmbedExactProvidedTemplateBytes() throws Exception
    {
        byte[] encoded;
        try (var input = new ClassPathResource(OaSignTaskExportService.TEMPLATE_RESOURCE)
                .getInputStream())
        {
            encoded = input.readAllBytes();
        }
        byte[] template = Base64.getDecoder().decode(
                new String(encoded, StandardCharsets.US_ASCII).replaceAll("\\s+", ""));
        assertThat(hex(MessageDigest.getInstance("SHA-256").digest(template)))
                .isEqualTo("feda8d70e38aca8c48f80782577dad65489040cb522fedeb36bdb55ba17cf802");
        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(template)))
        {
            assertThat(workbook.getNumberOfSheets()).isEqualTo(1);
            assertThat(workbook.getSheetName(0)).isEqualTo("签约数据");
            assertThat(workbook.getSheetAt(0).getRow(0).getLastCellNum())
                    .isEqualTo((short) 26);
        }
    }

    @Test
    @DisplayName("空结果和超出500条都不生成误导性文件")
    void shouldRejectEmptyAndOversizedExports()
    {
        OaSignTask filter = new OaSignTask();
        assertThatThrownBy(() -> service.export(filter, null))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("请先选择签约组织");
        verifyNoInteractions(signTaskService, signPackageMapper, importRowMapper);

        when(signTaskService.selectTaskList(filter, 88L)).thenReturn(List.of());
        assertThatThrownBy(() -> service.export(filter, 88L))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("没有可导出");
        verifyNoInteractions(signPackageMapper, importRowMapper);

        List<OaSignTask> oversized = new ArrayList<>();
        for (long id = 1; id <= 501; id++) oversized.add(task(id, id, "SIGNED", "员工" + id));
        when(signTaskService.selectTaskList(filter, 88L)).thenReturn(oversized);
        assertThatThrownBy(() -> service.export(filter, 88L))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("超过500条");
        verifyNoInteractions(signPackageMapper, importRowMapper);
    }

    @Test
    @DisplayName("正好500条仍可成功导出且每条都带签约状态")
    void shouldExportExactlyFiveHundredRows() throws Exception
    {
        OaSignTask filter = new OaSignTask();
        List<OaSignTask> tasks = new ArrayList<>();
        for (long id = 1; id <= 500; id++)
            tasks.add(task(id, id, id == 500 ? "PENDING_SIGN" : "SIGNED", "员工" + id));
        when(signTaskService.selectTaskList(filter, 88L)).thenReturn(tasks);
        when(signPackageMapper.selectOaSignPackagesByIds(anyList())).thenReturn(List.of());
        when(importRowMapper.selectByTaskIds(anyList())).thenReturn(List.of());

        byte[] result = service.export(filter, 88L);

        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(result)))
        {
            var sheet = workbook.getSheet("签约数据");
            DataFormatter formatter = new DataFormatter();
            assertThat(sheet.getLastRowNum()).isEqualTo(500);
            assertThat(formatter.formatCellValue(sheet.getRow(1).getCell(26))).isEqualTo("已签约");
            assertThat(formatter.formatCellValue(sheet.getRow(500).getCell(26))).isEqualTo("待签署");
        }
    }

    @Test
    @DisplayName("Excel签约状态列使用约定业务口径")
    void shouldUseBusinessSigningLabels()
    {
        Map<String, String> expected = Map.ofEntries(
                Map.entry("NEW", "新任务"),
                Map.entry("VALIDATING", "校验中"),
                Map.entry("NEEDS_DATA", "待补资料"),
                Map.entry("DRAFT_CREATED", "草稿已生成"),
                Map.entry("WAITING_HR_CONFIRM", "待确认"),
                Map.entry("READY_TO_SEND", "待发送"),
                Map.entry("SENDING", "发送中"),
                Map.entry("FAILED", "处理失败"),
                Map.entry("PENDING_SIGN", "待签署"),
                Map.entry("VIEWED", "已查看未签"),
                Map.entry("PENDING_COMPANY", "待选公司和印章"),
                Map.entry("PENDING_FINAL_CONFIRM", "待确认文件"),
                Map.entry("SIGNED", "已签约"),
                Map.entry("REFUSED", "已拒签"),
                Map.entry("EXPIRED", "已逾期"),
                Map.entry("CANCELLED", "已取消"),
                Map.entry("NO_ACTION", "无需签约"));
        expected.forEach((raw, label) ->
                assertThat(OaSignTaskExportService.statusLabel(raw)).isEqualTo(label));
    }

    private OaSignTask task(Long taskId, Long packageId, String status, String employeeName)
    {
        OaSignTask task = new OaSignTask();
        task.setTaskId(taskId);
        task.setPackageId(packageId);
        task.setEmployeeId(taskId + 1000);
        task.setEmployeeName(employeeName);
        task.setStatus(status);
        return task;
    }

    private OaSignPackage signPackage(Long packageId, String employeeName)
    {
        OaSignPackage signPackage = new OaSignPackage();
        signPackage.setPackageId(packageId);
        signPackage.setEmployeeNameSnapshot(employeeName);
        return signPackage;
    }

    private OaSignOnboardContractSnapshot snapshot(String employeeName)
    {
        OaSignOnboardContractSnapshot snapshot = new OaSignOnboardContractSnapshot();
        snapshot.setEmployeeName(employeeName);
        return snapshot;
    }

    private OaSignOnboardImportRow importRow(Long taskId,
            OaSignOnboardContractSnapshot snapshot) throws Exception
    {
        OaSignOnboardImportRow row = new OaSignOnboardImportRow();
        row.setTaskId(taskId);
        row.setSnapshotJson(objectMapper.writeValueAsString(snapshot));
        return row;
    }

    private String hex(byte[] bytes)
    {
        StringBuilder value = new StringBuilder(bytes.length * 2);
        for (byte item : bytes) value.append(String.format("%02x", item));
        return value.toString();
    }
}
