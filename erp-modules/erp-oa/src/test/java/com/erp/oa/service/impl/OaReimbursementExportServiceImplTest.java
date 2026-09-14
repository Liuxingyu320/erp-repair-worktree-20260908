package com.erp.oa.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import com.erp.common.core.constant.SecurityConstants;
import com.erp.common.core.context.SecurityContextHolder;
import com.erp.common.security.shop.ShopScopeService;
import com.erp.oa.config.OaReimbursementProperties;
import com.erp.oa.domain.OaReimbursement;
import com.erp.oa.domain.OaReimbursementExportBatch;
import com.erp.oa.domain.OaReimbursementInvoice;
import com.erp.oa.domain.OaReimbursementItem;
import com.erp.oa.domain.dto.OaReimbursementExportRequest;
import com.erp.oa.mapper.OaReimbursementMapper;
import com.erp.system.api.model.LoginUser;

@DisplayName("报销会计资料包导出")
class OaReimbursementExportServiceImplTest
{
    @TempDir
    Path workDir;

    private OaReimbursementMapper mapper;
    private ShopScopeService shopScopeService;
    private OaReimbursementFileStorageService fileStorage;
    private OaReimbursementExportServiceImpl service;
    private OaReimbursement claim;
    private OaReimbursementInvoice invoice;
    private byte[] invoiceBytes;

    @BeforeEach
    void setUp()
    {
        loginAsFinance();
        mapper = mock(OaReimbursementMapper.class);
        shopScopeService = mock(ShopScopeService.class);
        OaReimbursementProperties properties =
                new OaReimbursementProperties();
        properties.setStorageRoot(workDir.resolve("private").toString());
        properties.setTempRoot(workDir.resolve("temp").toString());
        fileStorage = new OaReimbursementFileStorageService(properties);
        service = new OaReimbursementExportServiceImpl(mapper,
                shopScopeService, fileStorage, properties);
        arrangeApprovedClaim();
    }

    @AfterEach
    void tearDown()
    {
        SecurityContextHolder.remove();
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    @DisplayName("ZIP同时包含可打开的三表Excel和原始发票")
    void shouldCreateTraceableWorkbookAndOriginalAttachment()
            throws Exception
    {
        OaReimbursementExportRequest request =
                new OaReimbursementExportRequest();
        request.setReimbursementIds(List.of(17L));
        request.setRequestId("workbook-command-1");
        com.erp.oa.domain.OaReimbursementExportCommand command = new com.erp.oa.domain.OaReimbursementExportCommand();
        when(mapper.claimExportCommand(eq(9L), eq(request.getRequestId()), any())).thenAnswer(call -> { command.setPayloadHash(call.getArgument(2)); return 1; });
        when(mapper.lockExportCommand(9L, request.getRequestId())).thenReturn(command);
        when(mapper.completeExportCommand(eq(9L), eq(request.getRequestId()), any(), eq(91L))).thenReturn(1);

        OaReimbursementExportBatch batch =
                service.createExport(request, 20L);

        assertThat(batch.getBatchId()).isEqualTo(91L);
        assertThat(batch.getReimbursementCount()).isEqualTo(1);
        assertThat(batch.getItemCount()).isEqualTo(1);
        assertThat(batch.getInvoiceCount()).isEqualTo(1);
        assertThat(batch.getTotalAmount())
                .isEqualByComparingTo("88.60");
        assertThat(batch.getArchiveSha256()).hasSize(64);

        Path archive = fileStorage.resolve(batch.getArchivePath());
        try (ZipFile zip = new ZipFile(archive.toFile()))
        {
            ZipEntry workbookEntry = zip.stream()
                    .filter(entry -> entry.getName().endsWith(".xlsx"))
                    .findFirst().orElseThrow();
            ZipEntry invoiceEntry = zip.stream()
                    .filter(entry -> entry.getName().startsWith(
                            "发票附件/BX202607300001/"))
                    .findFirst().orElseThrow();

            assertThat(zip.getInputStream(invoiceEntry).readAllBytes())
                    .isEqualTo(invoiceBytes);
            try (XSSFWorkbook workbook = new XSSFWorkbook(
                    zip.getInputStream(workbookEntry)))
            {
                List<String> sheetNames = new ArrayList<>();
                workbook.sheetIterator().forEachRemaining(
                        sheet -> sheetNames.add(sheet.getSheetName()));
                assertThat(sheetNames)
                        .containsExactly("报销单", "费用明细", "发票附件");
                assertThat(workbook.getSheet("报销单").getRow(1)
                        .getCell(2).getStringCellValue())
                        .isEqualTo("'=SUM(1,1)");
                assertThat(workbook.getSheet("费用明细").getRow(1)
                        .getCell(6).getNumericCellValue())
                        .isEqualTo(88.60d);
                assertThat(workbook.getSheet("发票附件").getRow(1)
                        .getCell(22).getStringCellValue())
                        .isEqualTo(invoiceEntry.getName());
                assertThat(workbook.getSheet("发票附件").getRow(1)
                        .getCell(11).getStringCellValue())
                        .isEqualTo("12345678");
                assertThat(workbook.getSheet("发票附件").getRow(1)
                        .getCell(19).getNumericCellValue())
                        .isEqualTo(88.60d);
            }
        }
    }

    @Test
    void durableSameCommandReturnsOriginalArchiveAndChangedPayloadIsRejected()
    {
        com.erp.oa.domain.OaReimbursementExportCommand claim = new com.erp.oa.domain.OaReimbursementExportCommand();
        claim.setActorId(9L); claim.setRequestId("export-command-1");
        when(mapper.claimExportCommand(eq(9L), eq("export-command-1"), any()))
                .thenAnswer(call -> { if (claim.getPayloadHash() == null) claim.setPayloadHash(call.getArgument(2)); return 1; });
        when(mapper.lockExportCommand(9L, "export-command-1")).thenReturn(claim);
        when(mapper.completeExportCommand(eq(9L), eq("export-command-1"), any(), eq(91L)))
                .thenAnswer(call -> { claim.setBatchId(91L); return 1; });
        OaReimbursementExportRequest request = new OaReimbursementExportRequest();
        request.setRequestId("export-command-1"); request.setReimbursementIds(List.of(17L));
        OaReimbursementExportBatch original = service.createExport(request, 20L);
        when(mapper.selectExportByCommand(9L, "export-command-1")).thenReturn(original);
        assertThat(service.createExport(request, 20L)).isSameAs(original);
        org.mockito.Mockito.verify(mapper, org.mockito.Mockito.times(1)).insertExportBatch(any());
        org.mockito.Mockito.verify(mapper, org.mockito.Mockito.times(1)).markExported(any(), any());
        request.setReimbursementIds(List.of(17L, 18L));
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.createExport(request, 20L)).hasMessageContaining("不同的报销集合或组织");
        assertThat(service.exportByRequestId("export-command-1")).isSameAs(original);
        assertThat(original.getArchiveStatus()).isEqualTo("AVAILABLE");
    }

    @Test
    void historyDefaultsToActorAndMissingArchiveIsExplicitWithoutRebuilding()
    {
        OaReimbursementExportBatch missing = new OaReimbursementExportBatch();
        missing.setBatchId(91L); missing.setCreatedByUserId(9L);
        missing.setArchivePath("exports/missing.zip");
        when(mapper.selectExportHistory(9L)).thenReturn(List.of(missing));
        assertThat(service.exportHistory(false)).containsExactly(missing);
        assertThat(missing.getArchiveStatus()).isEqualTo("UNAVAILABLE");
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.exportHistory(true)).hasMessageContaining("仅管理员");
        org.mockito.Mockito.verify(mapper, org.mockito.Mockito.never()).selectExportHistory(null);
        org.mockito.Mockito.verify(mapper, org.mockito.Mockito.never()).insertExportBatch(any());
        assertThat(service.exportByRequestId("not-observed")).isNull();
    }

    @Test
    void selectedSetPreconditionsAreExplicitRejectionsButReceiptFailuresStayUnknown()
    {
        var request = new OaReimbursementExportRequest();
        request.setRequestId("explicit-rejection"); request.setReimbursementIds(List.of(17L, 17L));
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.createExport(request, 20L))
                .isInstanceOf(com.erp.common.core.exception.ServiceException.class)
                .satisfies(error -> assertThat(((com.erp.common.core.exception.ServiceException) error).getCode()).isEqualTo(409));
        org.mockito.Mockito.verify(mapper, org.mockito.Mockito.never()).claimExportCommand(any(), any(), any());
        request.setReimbursementIds(List.of(17L));
        var command = new com.erp.oa.domain.OaReimbursementExportCommand();
        when(mapper.claimExportCommand(eq(9L), eq(request.getRequestId()), any()))
                .thenAnswer(call -> {command.setPayloadHash(call.getArgument(2));return 1;});
        when(mapper.lockExportCommand(9L, request.getRequestId())).thenReturn(command);
        when(mapper.selectFinanceListByIds(anyList(), anyList())).thenReturn(List.of());
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.createExport(request, 20L))
                .hasMessageContaining("所选报销单")
                .satisfies(error -> assertThat(((com.erp.common.core.exception.ServiceException) error).getCode()).isEqualTo(409));
        org.mockito.Mockito.verify(mapper, org.mockito.Mockito.never()).insertExportBatch(any());
        when(mapper.selectFinanceListByIds(anyList(), anyList())).thenReturn(List.of(claim));
        when(mapper.completeExportCommand(eq(9L), eq(request.getRequestId()), any(), eq(91L))).thenReturn(0);
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.createExport(request, 20L))
                .hasMessageContaining("回执")
                .satisfies(error -> {
                    var failure = (com.erp.common.core.exception.ServiceException) error;
                    assertThat(failure.getCode()).isNull();
                    var body = new com.erp.common.security.handler.GlobalExceptionHandler()
                            .handleServiceException(failure, new MockHttpServletRequest());
                    assertThat(body.get("code")).isEqualTo(500);
                });
        org.mockito.Mockito.verify(mapper).insertExportBatch(any());
    }

    private void arrangeApprovedClaim()
    {
        claim = new OaReimbursement();
        claim.setReimbursementId(17L);
        claim.setReimbursementNo("BX202607300001");
        claim.setTitle("=SUM(1,1)");
        claim.setPurpose("客户拜访交通费");
        claim.setApplicantId(7L);
        claim.setApplicantName("applicant");
        claim.setApplicantNickName("申请人");
        claim.setApplicantDeptName("销售一部");
        claim.setShopDeptId(20L);
        claim.setShopDeptName("上海店");
        claim.setTotalAmount(new BigDecimal("88.60"));
        claim.setStatus("approved");
        claim.setApprovedTime(new Date());

        OaReimbursementItem item = new OaReimbursementItem();
        item.setItemId(31L);
        item.setReimbursementId(17L);
        item.setExpenseType("交通费");
        item.setExpenseDate(new Date());
        item.setMerchantName("出租车");
        item.setDescription("客户拜访");
        item.setClaimedAmount(new BigDecimal("88.60"));

        invoiceBytes = "%PDF-1.4\noriginal-invoice\n%%EOF"
                .getBytes(StandardCharsets.US_ASCII);
        OaReimbursementFileStorageService.StoredInvoice stored =
                fileStorage.storeInvoice(17L, new MockMultipartFile(
                        "file", "出租车发票.pdf", "application/pdf",
                        invoiceBytes));
        invoice = new OaReimbursementInvoice();
        invoice.setInvoiceId(41L);
        invoice.setReimbursementId(17L);
        invoice.setOriginalName(stored.originalName());
        invoice.setStoredName(stored.storedName());
        invoice.setStoragePath(stored.relativePath());
        invoice.setContentType(stored.contentType());
        invoice.setFileExtension(stored.extension());
        invoice.setFileSize(stored.size());
        invoice.setSha256(stored.sha256());
        invoice.setDuplicateStatus("none");
        invoice.setRecognitionStatus("succeeded");
        invoice.setRecognitionEngine("local");
        invoice.setRecognitionProvider("tesseract");
        invoice.setInvoiceNumber("12345678");
        invoice.setSellerName("出租车公司");
        invoice.setInvoiceTotalAmount(new BigDecimal("88.60"));

        when(shopScopeService.resolveScopeDeptIds(20L))
                .thenReturn(List.of(20L));
        when(mapper.selectFinanceListByIds(
                List.of(17L), List.of(20L)))
                .thenReturn(List.of(claim));
        when(mapper.selectItemsByReimbursementId(17L))
                .thenReturn(List.of(item));
        when(mapper.selectInvoicesByReimbursementId(17L))
                .thenReturn(List.of(invoice));
        when(mapper.insertExportBatch(any()))
                .thenAnswer(invocation -> {
                    OaReimbursementExportBatch value =
                            invocation.getArgument(0);
                    value.setBatchId(91L);
                    return 1;
                });
        when(mapper.insertExportBatchItem(anyLong(), any()))
                .thenReturn(1);
        when(mapper.markExported(anyList(), eq("finance")))
                .thenReturn(1);
    }

    private void loginAsFinance()
    {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(SecurityConstants.AUTHORIZATION_HEADER,
                "Bearer finance-token");
        RequestContextHolder.setRequestAttributes(
                new ServletRequestAttributes(request));
        SecurityContextHolder.setUserId("9");
        SecurityContextHolder.setUserName("finance");
        LoginUser loginUser = new LoginUser();
        loginUser.setUserid(9L);
        loginUser.setUsername("finance");
        loginUser.setPermissions(Set.of(
                OaReimbursementServiceImpl.PERMISSION_FINANCE_EXPORT));
        loginUser.setRoles(Set.of());
        SecurityContextHolder.set(SecurityConstants.LOGIN_USER,
                loginUser);
    }
}
