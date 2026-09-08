package com.erp.oa.service.impl;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.apache.poi.ss.SpreadsheetVersion;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.util.AreaReference;
import org.apache.poi.ss.util.CellReference;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFTable;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import com.erp.common.core.exception.ServiceException;
import com.erp.oa.domain.OaSignOnboardContractSnapshot;
import com.erp.oa.domain.OaSignOnboardImportRow;
import com.erp.oa.domain.OaSignPackage;
import com.erp.oa.domain.OaSignTask;
import com.erp.oa.mapper.OaSignOnboardImportRowMapper;
import com.erp.oa.mapper.OaSignPackageMapper;
import com.erp.oa.service.IOaSignTaskService;
import com.erp.system.api.constant.SigningProfileCodes;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Exports scoped signing tasks with the exact HR-maintained onboarding workbook
 * as the base template. The export is read-only and never updates task/package
 * state.
 */
@Service
public class OaSignTaskExportService
{
    static final String TEMPLATE_RESOURCE =
            "templates/sign-task-export-template.xlsx.b64";
    static final String SHEET_NAME = "签约数据";
    static final String STATUS_HEADER = "签约状态";
    static final int MAX_EXPORT_ROWS = 500;

    static final List<String> TEMPLATE_HEADERS = List.of(
            "序号", "姓名", "社保类型", "员工岗位", "城市等级", "签约公司", "法定代表人", "注册地",
            "合同类型", "员工职级", "工作地", "身份证号", "家庭住址", "联系电话", "劳动合同期限形式",
            "劳动合同起始日期", "劳动合同结束日期", "试用期开始日期", "试用期结束日期", "工时制度",
            "综合工资", "底薪", "综合岗位津贴", "综合驻外补贴", "月度绩效津贴", "备注");

    private static final int STATUS_COLUMN_INDEX = TEMPLATE_HEADERS.size();
    private static final Map<String, String> STATUS_LABELS = statusLabels();

    private final IOaSignTaskService signTaskService;
    private final OaSignPackageMapper signPackageMapper;
    private final OaSignOnboardImportRowMapper importRowMapper;
    private final ObjectMapper objectMapper;

    public OaSignTaskExportService(IOaSignTaskService signTaskService,
            OaSignPackageMapper signPackageMapper,
            OaSignOnboardImportRowMapper importRowMapper,
            ObjectMapper objectMapper)
    {
        this.signTaskService = signTaskService;
        this.signPackageMapper = signPackageMapper;
        this.importRowMapper = importRowMapper;
        this.objectMapper = objectMapper;
    }

    public byte[] export(OaSignTask filter, Long selectedShopDeptId)
    {
        if (selectedShopDeptId == null || selectedShopDeptId <= 0)
        {
            throw new ServiceException("请先选择签约组织");
        }
        List<OaSignTask> tasks = signTaskService.selectTaskList(filter, selectedShopDeptId);
        if (tasks == null || tasks.isEmpty())
        {
            throw new ServiceException("当前组织和筛选条件下没有可导出的签约任务");
        }
        if (tasks.size() > MAX_EXPORT_ROWS)
        {
            throw new ServiceException("当前筛选结果超过500条，请缩小筛选条件后分批导出");
        }

        Set<Long> taskIds = new LinkedHashSet<>();
        Set<Long> packageIds = new LinkedHashSet<>();
        for (OaSignTask task : tasks)
        {
            if (task.getTaskId() != null) taskIds.add(task.getTaskId());
            if (task.getPackageId() != null) packageIds.add(task.getPackageId());
        }

        Map<Long, OaSignPackage> packages = packagesById(packageIds);
        Map<Long, OaSignOnboardImportRow> importRows = importRowsByTaskId(taskIds);
        return workbook(tasks, packages, importRows);
    }

    private Map<Long, OaSignPackage> packagesById(Set<Long> packageIds)
    {
        Map<Long, OaSignPackage> result = new HashMap<>();
        if (packageIds.isEmpty()) return result;
        for (OaSignPackage signPackage : signPackageMapper.selectOaSignPackagesByIds(
                new ArrayList<>(packageIds)))
        {
            if (signPackage != null && signPackage.getPackageId() != null)
                result.put(signPackage.getPackageId(), signPackage);
        }
        return result;
    }

    private Map<Long, OaSignOnboardImportRow> importRowsByTaskId(Set<Long> taskIds)
    {
        Map<Long, OaSignOnboardImportRow> result = new HashMap<>();
        if (taskIds.isEmpty()) return result;
        for (OaSignOnboardImportRow row : importRowMapper.selectByTaskIds(
                new ArrayList<>(taskIds)))
        {
            if (row != null && row.getTaskId() != null)
                result.putIfAbsent(row.getTaskId(), row);
        }
        return result;
    }

    private byte[] workbook(List<OaSignTask> tasks,
            Map<Long, OaSignPackage> packages,
            Map<Long, OaSignOnboardImportRow> importRows)
    {
        byte[] template = templateBytes();
        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(template));
                ByteArrayOutputStream output = new ByteArrayOutputStream())
        {
            XSSFSheet sheet = workbook.getSheet(SHEET_NAME);
            if (sheet == null) throw templateError();
            Row header = sheet.getRow(0);
            Row prototype = sheet.getRow(1);
            if (header == null || prototype == null || sheet.getTables().size() > 1)
                throw templateError();
            validateHeaders(header);

            List<CellStyle> dataStyles = dataStyles(prototype);
            short prototypeHeight = prototype.getHeight();
            Cell statusHeader = header.getCell(STATUS_COLUMN_INDEX);
            if (statusHeader == null) statusHeader = header.createCell(STATUS_COLUMN_INDEX);
            statusHeader.setCellStyle(header.getCell(STATUS_COLUMN_INDEX - 1).getCellStyle());
            statusHeader.setCellValue(STATUS_HEADER);

            for (int rowIndex = sheet.getLastRowNum(); rowIndex >= 1; rowIndex--)
            {
                Row existing = sheet.getRow(rowIndex);
                if (existing != null) sheet.removeRow(existing);
            }

            for (int index = 0; index < tasks.size(); index++)
            {
                OaSignTask task = tasks.get(index);
                OaSignPackage signPackage = packages.get(task.getPackageId());
                OaSignOnboardImportRow importRow = importRows.get(task.getTaskId());
                OaSignOnboardContractSnapshot snapshot = snapshot(task, importRow);
                Row row = sheet.createRow(index + 1);
                row.setHeight(prototypeHeight);
                Object[] values = values(index + 1, task, signPackage, snapshot);
                for (int column = 0; column < values.length; column++)
                {
                    Cell cell = row.createCell(column);
                    cell.setCellStyle(dataStyles.get(column));
                    setCellValue(cell, values[column]);
                }
            }

            sheet.setColumnWidth(STATUS_COLUMN_INDEX,
                    Math.max(sheet.getColumnWidth(STATUS_COLUMN_INDEX - 1), 14 * 256));
            if (!sheet.getTables().isEmpty())
            {
                XSSFTable table = sheet.getTables().get(0);
                table.setArea(new AreaReference(new CellReference(0, 0),
                        new CellReference(tasks.size(), STATUS_COLUMN_INDEX),
                        SpreadsheetVersion.EXCEL2007));
            }
            workbook.write(output);
            return output.toByteArray();
        }
        catch (ServiceException exception)
        {
            throw exception;
        }
        catch (IOException | RuntimeException exception)
        {
            throw new ServiceException("签约数据导出失败，请稍后重试");
        }
    }

    private byte[] templateBytes()
    {
        try (var input = new ClassPathResource(TEMPLATE_RESOURCE).getInputStream())
        {
            String encoded = new String(input.readAllBytes(), StandardCharsets.US_ASCII)
                    .replaceAll("\\s+", "");
            return Base64.getDecoder().decode(encoded);
        }
        catch (IOException | IllegalArgumentException exception)
        {
            throw templateError();
        }
    }

    private void validateHeaders(Row header)
    {
        DataFormatter formatter = new DataFormatter(Locale.ROOT);
        for (int index = 0; index < TEMPLATE_HEADERS.size(); index++)
        {
            Cell cell = header.getCell(index);
            String actual = cell == null ? "" : formatter.formatCellValue(cell).trim();
            if (!TEMPLATE_HEADERS.get(index).equals(actual)) throw templateError();
        }
    }

    private List<CellStyle> dataStyles(Row prototype)
    {
        List<CellStyle> result = new ArrayList<>(STATUS_COLUMN_INDEX + 1);
        for (int index = 0; index < STATUS_COLUMN_INDEX; index++)
        {
            Cell cell = prototype.getCell(index);
            if (cell == null) throw templateError();
            result.add(cell.getCellStyle());
        }
        result.add(prototype.getCell(STATUS_COLUMN_INDEX - 1).getCellStyle());
        return result;
    }

    private OaSignOnboardContractSnapshot snapshot(OaSignTask task,
            OaSignOnboardImportRow importRow)
    {
        if (importRow == null || blank(importRow.getSnapshotJson()))
            return new OaSignOnboardContractSnapshot();
        try
        {
            return objectMapper.readValue(importRow.getSnapshotJson(),
                    OaSignOnboardContractSnapshot.class);
        }
        catch (JsonProcessingException exception)
        {
            throw new ServiceException("签约任务" + task.getTaskId()
                    + "的数据快照无法读取，请先核对任务数据");
        }
    }

    private Object[] values(int sequence, OaSignTask task, OaSignPackage signPackage,
            OaSignOnboardContractSnapshot snapshot)
    {
        return new Object[] {
                sequence,
                firstText(value(signPackage, OaSignPackage::getEmployeeNameSnapshot),
                        snapshot.getEmployeeName(), task.getEmployeeName()),
                socialLabel(firstText(value(signPackage, OaSignPackage::getSocialType),
                        snapshot.getSocialTypeCode())),
                firstText(value(signPackage, OaSignPackage::getPostNameSnapshot),
                        snapshot.getEmployeePost()),
                snapshot.getCityLevel(),
                firstText(value(signPackage, OaSignPackage::getLegalEntityNameSnapshot),
                        snapshot.getMatchedLegalEntityName(),
                        value(signPackage, OaSignPackage::getRecommendedCompanySnapshot),
                        snapshot.getRecommendedCompany()),
                firstText(value(signPackage, OaSignPackage::getLegalRepresentativeSnapshot),
                        snapshot.getMatchedLegalRepresentative(),
                        value(signPackage, OaSignPackage::getRecommendedLegalRepresentativeSnapshot),
                        snapshot.getLegalRepresentative()),
                firstText(value(signPackage, OaSignPackage::getLegalEntityAddressSnapshot),
                        snapshot.getMatchedRegisteredAddress(),
                        value(signPackage, OaSignPackage::getRecommendedRegisteredAddressSnapshot),
                        snapshot.getRegisteredAddress()),
                contractTypeLabel(firstText(value(signPackage, OaSignPackage::getEmploymentType),
                        snapshot.getContractTypeCode())),
                jobGradeValue(firstText(value(signPackage, OaSignPackage::getPostLevelSnapshot),
                        snapshot.getJobGradeCode())),
                snapshot.getWorkLocation(),
                firstText(value(signPackage, OaSignPackage::getEmployeeIdCardSnapshot),
                        snapshot.getIdNumber()),
                firstText(value(signPackage, OaSignPackage::getEmployeeAddressSnapshot),
                        snapshot.getCurrentAddress()),
                firstText(value(signPackage, OaSignPackage::getEmployeePhoneSnapshot),
                        snapshot.getPhone()),
                contractTermLabel(firstText(value(signPackage,
                        OaSignPackage::getContractTermCodeSnapshot),
                        snapshot.getContractTermCode())),
                dateValue(value(signPackage, OaSignPackage::getContractStartDate),
                        snapshot.getContractStartDate()),
                dateValue(value(signPackage, OaSignPackage::getContractEndDate),
                        snapshot.getContractEndDate()),
                dateValue(value(signPackage, OaSignPackage::getProbationStartDate),
                        snapshot.getProbationStartDate()),
                dateValue(value(signPackage, OaSignPackage::getProbationEndDate),
                        snapshot.getProbationEndDate()),
                snapshot.getWorkSchedule(),
                amount(value(signPackage, OaSignPackage::getSalaryTotal), snapshot.getSalaryTotal()),
                amount(value(signPackage, OaSignPackage::getBaseSalary), snapshot.getBaseSalary()),
                amount(value(signPackage, OaSignPackage::getPostSalary), snapshot.getPostSalary()),
                amount(value(signPackage, OaSignPackage::getFieldAllowance), snapshot.getFieldAllowance()),
                amount(value(signPackage, OaSignPackage::getPerformanceSalary),
                        snapshot.getPerformanceSalary()),
                snapshot.getRemarks(),
                statusLabel(task.getStatus())
        };
    }

    private <T> T value(OaSignPackage signPackage,
            java.util.function.Function<OaSignPackage, T> getter)
    {
        return signPackage == null ? null : getter.apply(signPackage);
    }

    private BigDecimal amount(BigDecimal frozenValue, BigDecimal importedValue)
    {
        return frozenValue != null ? frozenValue : importedValue;
    }

    private Date dateValue(String frozenValue, LocalDate importedValue)
    {
        LocalDate value = parseDate(frozenValue);
        if (value == null) value = importedValue;
        return value == null ? null
                : Date.from(value.atStartOfDay(ZoneId.systemDefault()).toInstant());
    }

    private LocalDate parseDate(String value)
    {
        String normalized = firstText(value);
        if (normalized == null) return null;
        if (normalized.length() >= 10) normalized = normalized.substring(0, 10);
        try { return LocalDate.parse(normalized); }
        catch (DateTimeParseException ignored) { return null; }
    }

    private Object jobGradeValue(String raw)
    {
        String value = firstText(raw);
        if (value == null) return null;
        String digits = value.toUpperCase(Locale.ROOT).replace("P", "")
                .replace("级", "").trim();
        return digits.matches("[0-9]+") ? Integer.valueOf(digits) : value;
    }

    private String socialLabel(String raw)
    {
        String value = firstText(raw);
        if (value == null) return null;
        return switch (value.toUpperCase(Locale.ROOT))
        {
            case SigningProfileCodes.SOCIAL_INSURED -> "有";
            case SigningProfileCodes.SOCIAL_UNINSURED, "NO_SOCIAL" -> "无";
            case SigningProfileCodes.DISPATCHED -> "劳务派遣";
            case SigningProfileCodes.PENDING_CONFIRMATION -> "待确认";
            default -> value;
        };
    }

    private String contractTypeLabel(String raw)
    {
        String value = firstText(raw);
        if (value == null) return null;
        return switch (value.toUpperCase(Locale.ROOT))
        {
            case SigningProfileCodes.LABOR_CONTRACT -> "劳动合同";
            case SigningProfileCodes.SERVICE_CONTRACT -> "劳务合同";
            case SigningProfileCodes.INTERNSHIP_AGREEMENT -> "实习协议";
            case SigningProfileCodes.OUTSOURCING_CONTRACT -> "外包合同";
            default -> value;
        };
    }

    private String contractTermLabel(String raw)
    {
        String value = firstText(raw);
        if (value == null) return null;
        return switch (value.toUpperCase(Locale.ROOT))
        {
            case SigningProfileCodes.FIXED_TERM -> "固定期限";
            case SigningProfileCodes.OPEN_ENDED -> "无固定期限";
            default -> value;
        };
    }

    static String statusLabel(String raw)
    {
        String value = firstText(raw);
        if (value == null) return "未知状态";
        return STATUS_LABELS.getOrDefault(value.toUpperCase(Locale.ROOT), value);
    }

    private static Map<String, String> statusLabels()
    {
        Map<String, String> labels = new LinkedHashMap<>();
        labels.put("NEW", "新任务");
        labels.put("VALIDATING", "校验中");
        labels.put("NEEDS_DATA", "待补资料");
        labels.put("DRAFT_CREATED", "草稿已生成");
        labels.put("WAITING_HR_CONFIRM", "待确认");
        labels.put("READY_TO_SEND", "待发送");
        labels.put("SENDING", "发送中");
        labels.put("FAILED", "处理失败");
        labels.put("PENDING_SIGN", "待签署");
        labels.put("VIEWED", "已查看未签");
        labels.put("PENDING_COMPANY", "待选公司和印章");
        labels.put("PENDING_FINAL_CONFIRM", "待确认文件");
        labels.put("SIGNED", "已签约");
        labels.put("REFUSED", "已拒签");
        labels.put("EXPIRED", "已逾期");
        labels.put("CANCELLED", "已取消");
        labels.put("NO_ACTION", "无需签约");
        return Map.copyOf(labels);
    }

    private static String firstText(String... values)
    {
        if (values == null) return null;
        for (String value : values)
        {
            if (value != null && !value.trim().isEmpty()) return value.trim();
        }
        return null;
    }

    private boolean blank(String value)
    {
        return value == null || value.trim().isEmpty();
    }

    private void setCellValue(Cell cell, Object value)
    {
        if (value == null)
        {
            cell.setBlank();
        }
        else if (value instanceof BigDecimal decimal)
        {
            cell.setCellValue(decimal.doubleValue());
        }
        else if (value instanceof Number number)
        {
            cell.setCellValue(number.doubleValue());
        }
        else if (value instanceof Date date)
        {
            cell.setCellValue(date);
        }
        else
        {
            cell.setCellValue(String.valueOf(value));
        }
    }

    private ServiceException templateError()
    {
        return new ServiceException("签约数据导出模板不可用，请联系管理员");
    }
}
