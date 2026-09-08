package com.erp.oa.service.impl;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.DataFormat;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import com.erp.common.core.exception.ServiceException;
import com.erp.common.security.auth.AuthUtil;
import com.erp.common.security.shop.ShopScopeService;
import com.erp.common.security.utils.SecurityUtils;
import com.erp.oa.config.OaReimbursementProperties;
import com.erp.oa.domain.OaReimbursement;
import com.erp.oa.domain.OaReimbursementExportBatch;
import com.erp.oa.domain.OaReimbursementInvoice;
import com.erp.oa.domain.OaReimbursementItem;
import com.erp.oa.domain.dto.OaReimbursementExportRequest;
import com.erp.oa.mapper.OaReimbursementMapper;
import com.erp.oa.service.OaReimbursementExportService;

@Service
public class OaReimbursementExportServiceImpl
        implements OaReimbursementExportService
{
    private static final String CONTENT_TYPE = "application/zip";
    private static final String ATTACHMENT_DIRECTORY = "发票附件";
    private static final DateTimeFormatter BATCH_TIME =
            DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final OaReimbursementMapper mapper;
    private final ShopScopeService shopScopeService;
    private final OaReimbursementFileStorageService fileStorage;
    private final int maxClaimsPerExport;

    public OaReimbursementExportServiceImpl(OaReimbursementMapper mapper,
            ShopScopeService shopScopeService,
            OaReimbursementFileStorageService fileStorage,
            OaReimbursementProperties properties)
    {
        this.mapper = mapper;
        this.shopScopeService = shopScopeService;
        this.fileStorage = fileStorage;
        this.maxClaimsPerExport = properties.getMaxClaimsPerExport();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public OaReimbursementExportBatch createExport(
            OaReimbursementExportRequest request, Long selectedShopDeptId)
    {
        requireExportPermission();
        List<Long> ids = normalizedIds(request);
        List<Long> scopeDeptIds = resolveScope(selectedShopDeptId);
        List<OaReimbursement> reimbursements =
                mapper.selectFinanceListByIds(ids, scopeDeptIds);
        if (reimbursements.size() != ids.size())
        {
            throw new ServiceException(
                    "所选报销单中包含未审批通过、已删除或无权访问的数据，请刷新后重试");
        }
        hydrate(reimbursements);

        String batchNo = newBatchNo();
        Path temporary = fileStorage.createExportTemp(batchNo);
        OaReimbursementFileStorageService.StoredExport stored = null;
        try
        {
            writeArchive(temporary, batchNo, reimbursements);
            stored = fileStorage.promoteExport(temporary, batchNo);
            registerRollbackCleanup(stored.relativePath());
            OaReimbursementExportBatch batch = buildBatch(
                    batchNo, reimbursements, stored);
            if (mapper.insertExportBatch(batch) != 1)
            {
                throw new ServiceException("保存会计导出批次失败");
            }
            for (OaReimbursement reimbursement : reimbursements)
            {
                if (mapper.insertExportBatchItem(batch.getBatchId(),
                        reimbursement) != 1)
                {
                    throw new ServiceException("保存会计导出批次明细失败");
                }
            }
            if (mapper.markExported(ids, SecurityUtils.getUsername())
                    != ids.size())
            {
                throw new ServiceException("更新报销单导出状态失败");
            }
            return batch;
        }
        finally
        {
            deleteTemporaryQuietly(temporary);
        }
    }

    @Override
    public ExportContent exportContent(Long batchId)
    {
        requireExportPermission();
        OaReimbursementExportBatch batch =
                mapper.selectExportBatchById(batchId);
        if (batch == null)
        {
            throw new ServiceException("会计导出批次不存在");
        }
        if (!SecurityUtils.isAdmin()
                && !Objects.equals(batch.getCreatedByUserId(),
                        SecurityUtils.getUserId()))
        {
            throw new ServiceException("只能下载本人创建的会计导出资料包");
        }
        Path path = fileStorage.resolve(batch.getArchivePath());
        try
        {
            return new ExportContent(path, batch.getArchiveName(),
                    CONTENT_TYPE, Files.size(path));
        }
        catch (IOException exception)
        {
            throw new ServiceException("读取会计导出资料包失败")
                    .setDetailMessage(exception.getMessage());
        }
    }

    private List<Long> normalizedIds(OaReimbursementExportRequest request)
    {
        if (request == null || request.getReimbursementIds() == null
                || request.getReimbursementIds().isEmpty())
        {
            throw new ServiceException("请选择需要导出的报销单");
        }
        Set<Long> distinct = new HashSet<>();
        List<Long> ids = new ArrayList<>();
        for (Long id : request.getReimbursementIds())
        {
            if (id == null || id <= 0)
            {
                throw new ServiceException("报销单编号不合法");
            }
            if (!distinct.add(id))
            {
                throw new ServiceException("不能重复选择同一张报销单");
            }
            ids.add(id);
        }
        if (ids.size() > maxClaimsPerExport)
        {
            throw new ServiceException("单次最多导出"
                    + maxClaimsPerExport + "张报销单");
        }
        return ids;
    }

    private List<Long> resolveScope(Long selectedShopDeptId)
    {
        List<Long> scopeDeptIds =
                shopScopeService.resolveScopeDeptIds(selectedShopDeptId);
        if (!SecurityUtils.isAdmin()
                && (scopeDeptIds == null || scopeDeptIds.isEmpty()))
        {
            throw new ServiceException("当前用户没有可导出的店铺数据范围");
        }
        return scopeDeptIds;
    }

    private void hydrate(List<OaReimbursement> reimbursements)
    {
        for (OaReimbursement reimbursement : reimbursements)
        {
            reimbursement.setItems(mapper.selectItemsByReimbursementId(
                    reimbursement.getReimbursementId()));
            reimbursement.setInvoices(mapper.selectInvoicesByReimbursementId(
                    reimbursement.getReimbursementId()));
        }
    }

    private OaReimbursementExportBatch buildBatch(String batchNo,
            List<OaReimbursement> reimbursements,
            OaReimbursementFileStorageService.StoredExport stored)
    {
        OaReimbursementExportBatch batch =
                new OaReimbursementExportBatch();
        batch.setBatchNo(batchNo);
        batch.setReimbursementCount(reimbursements.size());
        batch.setItemCount(reimbursements.stream()
                .mapToInt(value -> value.getItems().size()).sum());
        batch.setInvoiceCount(reimbursements.stream()
                .mapToInt(value -> value.getInvoices().size()).sum());
        batch.setTotalAmount(reimbursements.stream()
                .map(OaReimbursement::getTotalAmount)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add));
        batch.setArchiveName(stored.archiveName());
        batch.setArchivePath(stored.relativePath());
        batch.setArchiveSize(stored.size());
        batch.setArchiveSha256(stored.sha256());
        batch.setCreatedByUserId(SecurityUtils.getUserId());
        batch.setCreatedByName(SecurityUtils.getUsername());
        return batch;
    }

    private void writeArchive(Path target, String batchNo,
            List<OaReimbursement> reimbursements)
    {
        try (ZipOutputStream zip = new ZipOutputStream(
                Files.newOutputStream(target));
                XSSFWorkbook workbook = new XSSFWorkbook())
        {
            WorkbookStyles styles = new WorkbookStyles(workbook);
            writeReimbursementSheet(workbook, styles, batchNo,
                    reimbursements);
            writeItemSheet(workbook, styles, reimbursements);
            writeInvoiceSheet(workbook, styles, reimbursements);

            zip.putNextEntry(new ZipEntry(
                    "报销台账_" + batchNo + ".xlsx"));
            workbook.write(zip);
            zip.closeEntry();

            for (OaReimbursement reimbursement : reimbursements)
            {
                int index = 0;
                for (OaReimbursementInvoice invoice
                        : reimbursement.getInvoices())
                {
                    String relativeArchivePath = invoiceArchivePath(
                            reimbursement, invoice, ++index);
                    zip.putNextEntry(new ZipEntry(relativeArchivePath));
                    Path source = fileStorage.resolve(
                            invoice.getStoragePath());
                    try (InputStream input = Files.newInputStream(source))
                    {
                        input.transferTo(zip);
                    }
                    zip.closeEntry();
                }
            }
        }
        catch (IOException exception)
        {
            throw new ServiceException("生成会计导出资料包失败")
                    .setDetailMessage(exception.getMessage());
        }
    }

    private void writeReimbursementSheet(XSSFWorkbook workbook,
            WorkbookStyles styles, String batchNo,
            List<OaReimbursement> reimbursements)
    {
        Sheet sheet = workbook.createSheet("报销单");
        String[] headers = {
                "导出批次", "报销单号", "报销标题", "申请人", "申请人账号",
                "申请部门", "归属店铺", "报销事由", "总金额", "审批通过时间",
                "发票数量"
        };
        header(sheet, styles, headers);
        int rowIndex = 1;
        for (OaReimbursement value : reimbursements)
        {
            Row row = sheet.createRow(rowIndex++);
            stringCell(row, 0, batchNo);
            stringCell(row, 1, value.getReimbursementNo());
            stringCell(row, 2, value.getTitle());
            stringCell(row, 3, displayApplicant(value));
            stringCell(row, 4, value.getApplicantName());
            stringCell(row, 5, value.getApplicantDeptName());
            stringCell(row, 6, value.getShopDeptName());
            stringCell(row, 7, value.getPurpose());
            numberCell(row, 8, value.getTotalAmount(), styles.money());
            dateCell(row, 9, value.getApprovedTime(), styles.dateTime());
            numberCell(row, 10, BigDecimal.valueOf(
                    value.getInvoices().size()), styles.integer());
        }
        finishSheet(sheet, headers.length, 30);
    }

    private void writeItemSheet(XSSFWorkbook workbook,
            WorkbookStyles styles,
            List<OaReimbursement> reimbursements)
    {
        Sheet sheet = workbook.createSheet("费用明细");
        String[] headers = {
                "报销单号", "明细序号", "费用类型", "费用日期", "商户名称",
                "费用说明", "报销金额"
        };
        header(sheet, styles, headers);
        int rowIndex = 1;
        for (OaReimbursement reimbursement : reimbursements)
        {
            int index = 0;
            for (OaReimbursementItem item : reimbursement.getItems())
            {
                Row row = sheet.createRow(rowIndex++);
                stringCell(row, 0, reimbursement.getReimbursementNo());
                numberCell(row, 1, BigDecimal.valueOf(++index),
                        styles.integer());
                stringCell(row, 2, item.getExpenseType());
                dateCell(row, 3, item.getExpenseDate(), styles.date());
                stringCell(row, 4, item.getMerchantName());
                stringCell(row, 5, item.getDescription());
                numberCell(row, 6, item.getClaimedAmount(),
                        styles.money());
            }
        }
        finishSheet(sheet, headers.length, 35);
    }

    private void writeInvoiceSheet(XSSFWorkbook workbook,
            WorkbookStyles styles,
            List<OaReimbursement> reimbursements)
    {
        Sheet sheet = workbook.createSheet("发票附件");
        String[] headers = {
                "报销单号", "附件序号", "原始文件名", "文件类型",
                "文件大小（字节）", "SHA-256", "重复提示", "识别状态",
                "识别引擎", "发票类型", "发票代码", "发票号码",
                "开票日期", "销售方", "销售方税号", "购买方",
                "购买方税号", "不含税金额", "税额", "价税合计",
                "消费类型", "商品或服务摘要", "资料包内路径"
        };
        header(sheet, styles, headers);
        int rowIndex = 1;
        for (OaReimbursement reimbursement : reimbursements)
        {
            int index = 0;
            for (OaReimbursementInvoice invoice
                    : reimbursement.getInvoices())
            {
                int invoiceIndex = ++index;
                Row row = sheet.createRow(rowIndex++);
                stringCell(row, 0, reimbursement.getReimbursementNo());
                numberCell(row, 1, BigDecimal.valueOf(invoiceIndex),
                        styles.integer());
                stringCell(row, 2, invoice.getOriginalName());
                stringCell(row, 3, invoice.getFileExtension());
                numberCell(row, 4, BigDecimal.valueOf(
                        invoice.getFileSize() == null
                                ? 0L : invoice.getFileSize()),
                        styles.integer());
                stringCell(row, 5, invoice.getSha256());
                stringCell(row, 6, "warning".equals(
                        invoice.getDuplicateStatus())
                                ? "与历史已审批发票重复" : "");
                stringCell(row, 7, invoice.getRecognitionStatus());
                stringCell(row, 8,
                        displayRecognitionEngine(invoice));
                stringCell(row, 9, invoice.getInvoiceType());
                stringCell(row, 10, invoice.getInvoiceCode());
                stringCell(row, 11, invoice.getInvoiceNumber());
                dateCell(row, 12, invoice.getInvoiceDate(), styles.date());
                stringCell(row, 13, invoice.getSellerName());
                stringCell(row, 14, invoice.getSellerTaxNo());
                stringCell(row, 15, invoice.getPurchaserName());
                stringCell(row, 16, invoice.getPurchaserTaxNo());
                numberCell(row, 17, invoice.getAmountWithoutTax(),
                        styles.money());
                numberCell(row, 18, invoice.getTaxAmount(), styles.money());
                numberCell(row, 19, invoice.getInvoiceTotalAmount(),
                        styles.money());
                stringCell(row, 20, invoice.getServiceType());
                stringCell(row, 21, invoice.getCommoditySummary());
                stringCell(row, 22, invoiceArchivePath(
                        reimbursement, invoice, invoiceIndex));
            }
        }
        finishSheet(sheet, headers.length, 45);
    }

    private void header(Sheet sheet, WorkbookStyles styles,
            String[] headers)
    {
        Row row = sheet.createRow(0);
        for (int index = 0; index < headers.length; index++)
        {
            Cell cell = row.createCell(index);
            cell.setCellValue(headers[index]);
            cell.setCellStyle(styles.header());
        }
        sheet.createFreezePane(0, 1);
        sheet.setAutoFilter(new org.apache.poi.ss.util.CellRangeAddress(
                0, 0, 0, headers.length - 1));
    }

    private void finishSheet(Sheet sheet, int columns, int maxWidth)
    {
        for (int index = 0; index < columns; index++)
        {
            sheet.autoSizeColumn(index);
            int limited = Math.min(sheet.getColumnWidth(index) + 512,
                    maxWidth * 256);
            sheet.setColumnWidth(index, Math.max(limited, 10 * 256));
        }
    }

    private void stringCell(Row row, int index, String value)
    {
        row.createCell(index).setCellValue(excelSafe(value));
    }

    private void numberCell(Row row, int index, BigDecimal value,
            CellStyle style)
    {
        Cell cell = row.createCell(index);
        if (value != null)
        {
            cell.setCellValue(value.doubleValue());
        }
        cell.setCellStyle(style);
    }

    private void dateCell(Row row, int index, Date value,
            CellStyle style)
    {
        Cell cell = row.createCell(index);
        if (value != null)
        {
            cell.setCellValue(value);
        }
        cell.setCellStyle(style);
    }

    private String displayApplicant(OaReimbursement value)
    {
        return value.getApplicantNickName() == null
                || value.getApplicantNickName().isBlank()
                        ? value.getApplicantName()
                        : value.getApplicantNickName();
    }

    private String displayRecognitionEngine(
            OaReimbursementInvoice invoice)
    {
        if ("manual".equals(invoice.getRecognitionEngine()))
        {
            return "人工核对";
        }
        if ("cloud".equals(invoice.getRecognitionEngine()))
        {
            return "云端-" + Objects.toString(
                    invoice.getRecognitionProvider(), "");
        }
        if ("local".equals(invoice.getRecognitionEngine()))
        {
            return "本地-" + Objects.toString(
                    invoice.getRecognitionProvider(), "");
        }
        return Objects.toString(invoice.getRecognitionEngine(), "");
    }

    private String invoiceArchivePath(OaReimbursement reimbursement,
            OaReimbursementInvoice invoice, int index)
    {
        String number = safeZipPart(reimbursement.getReimbursementNo(), 64);
        String fileName = safeZipPart(invoice.getOriginalName(), 120);
        return ATTACHMENT_DIRECTORY + "/" + number + "/"
                + String.format(Locale.ROOT, "%02d_", index) + fileName;
    }

    private String safeZipPart(String source, int maxLength)
    {
        String value = source == null ? "未命名" : source;
        value = value.replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}]", "_")
                .replace("..", "_").trim();
        if (value.isEmpty())
        {
            value = "未命名";
        }
        return value.length() <= maxLength
                ? value : value.substring(0, maxLength);
    }

    private String excelSafe(String source)
    {
        if (source == null)
        {
            return "";
        }
        String value = source.length() > 32767
                ? source.substring(0, 32767) : source;
        if (!value.isEmpty() && "=+-@".indexOf(value.charAt(0)) >= 0)
        {
            value = "'" + value;
        }
        return value;
    }

    private String newBatchNo()
    {
        return "BXDC" + LocalDateTime.now().format(BATCH_TIME)
                + UUID.randomUUID().toString().replace("-", "")
                        .substring(0, 8).toUpperCase(Locale.ROOT);
    }

    private void requireExportPermission()
    {
        if (!SecurityUtils.isAdmin()
                && !AuthUtil.hasPermi(
                        OaReimbursementServiceImpl.PERMISSION_FINANCE_EXPORT))
        {
            throw new ServiceException("无权导出报销会计资料");
        }
    }

    private void registerRollbackCleanup(String relativePath)
    {
        if (!TransactionSynchronizationManager.isSynchronizationActive())
        {
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization()
                {
                    @Override
                    public void afterCompletion(int status)
                    {
                        if (status != TransactionSynchronization.STATUS_COMMITTED)
                        {
                            fileStorage.deleteQuietly(relativePath);
                        }
                    }
                });
    }

    private void deleteTemporaryQuietly(Path path)
    {
        try
        {
            if (path != null)
            {
                Files.deleteIfExists(path);
            }
        }
        catch (IOException ignored)
        {
            // Best-effort cleanup for a failed export.
        }
    }

    private record WorkbookStyles(CellStyle header, CellStyle money,
            CellStyle integer, CellStyle date, CellStyle dateTime)
    {
        private WorkbookStyles(XSSFWorkbook workbook)
        {
            this(headerStyle(workbook),
                    formatStyle(workbook, "#,##0.00"),
                    formatStyle(workbook, "0"),
                    formatStyle(workbook, "yyyy-mm-dd"),
                    formatStyle(workbook, "yyyy-mm-dd hh:mm:ss"));
        }

        private static CellStyle headerStyle(XSSFWorkbook workbook)
        {
            CellStyle style = workbook.createCellStyle();
            Font font = workbook.createFont();
            font.setBold(true);
            style.setFont(font);
            style.setFillForegroundColor(
                    org.apache.poi.ss.usermodel.IndexedColors.GREY_25_PERCENT
                            .getIndex());
            style.setFillPattern(
                    org.apache.poi.ss.usermodel.FillPatternType.SOLID_FOREGROUND);
            return style;
        }

        private static CellStyle formatStyle(XSSFWorkbook workbook,
                String format)
        {
            CellStyle style = workbook.createCellStyle();
            DataFormat dataFormat = workbook.createDataFormat();
            style.setDataFormat(dataFormat.getFormat(format));
            return style;
        }
    }
}
