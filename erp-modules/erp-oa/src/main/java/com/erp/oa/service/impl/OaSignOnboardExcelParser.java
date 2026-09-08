package com.erp.oa.service.impl;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.util.NumberToTextConverter;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import com.erp.common.core.exception.ServiceException;
import com.erp.oa.domain.OaSignOnboardContractSnapshot;
import com.erp.system.api.constant.SigningProfileCodes;

/** Strict parser for the HR maintained xlsx sheet named exactly {@code 签约数据}. */
@Component
public class OaSignOnboardExcelParser
{
    public static final String SHEET_NAME = "签约数据";
    public static final long MAX_FILE_SIZE = 10L * 1024L * 1024L;
    public static final int MAX_ROWS = 500;

    private static final List<String> HEADERS = List.of(
            "序号", "姓名", "社保类型", "员工岗位", "城市等级", "签约公司", "法定代表人", "注册地",
            "合同类型", "员工职级", "工作地", "身份证号", "家庭住址", "联系电话", "劳动合同期限形式",
            "劳动合同起始日期", "劳动合同结束日期", "试用期开始日期", "试用期结束日期", "工时制度",
            "综合工资", "底薪", "综合岗位津贴", "综合驻外补贴", "月度绩效津贴", "备注");
    private static final Set<String> REQUIRED_HEADERS = Set.of(
            "姓名", "社保类型", "员工岗位", "城市等级", "合同类型", "员工职级", "工作地",
            "身份证号", "联系电话", "劳动合同期限形式", "劳动合同起始日期", "劳动合同结束日期",
            "工时制度", "综合工资", "底薪", "综合岗位津贴", "综合驻外补贴", "月度绩效津贴");
    private static final Set<String> REQUIRED_VALUES = Set.of(
            "姓名", "社保类型", "员工岗位", "城市等级", "合同类型", "员工职级", "工作地",
            "身份证号", "联系电话", "劳动合同期限形式", "劳动合同起始日期", "劳动合同结束日期",
            "工时制度", "综合工资", "底薪", "综合岗位津贴", "综合驻外补贴", "月度绩效津贴");

    public ParsedWorkbook parse(MultipartFile file)
    {
        if (file == null || file.isEmpty())
        {
            throw new ServiceException("请选择签约数据Excel文件");
        }
        String filename = file.getOriginalFilename();
        if (filename == null || !filename.toLowerCase(Locale.ROOT).endsWith(".xlsx"))
        {
            throw new ServiceException("仅支持.xlsx格式");
        }
        if (file.getSize() <= 0 || file.getSize() > MAX_FILE_SIZE)
        {
            throw new ServiceException("Excel文件不能超过10MB");
        }
        try
        {
            byte[] bytes = file.getBytes();
            try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(bytes)))
            {
                Sheet sheet = workbook.getSheet(SHEET_NAME);
                if (sheet == null)
                {
                    throw new ServiceException("Excel必须包含名称精确为“签约数据”的sheet");
                }
                Map<String, Integer> columns = headerColumns(sheet.getRow(sheet.getFirstRowNum()));
                List<ParsedRow> rows = parseRows(sheet, columns);
                return new ParsedWorkbook(sha256(bytes), rows);
            }
        }
        catch (ServiceException exception)
        {
            throw exception;
        }
        catch (IOException | RuntimeException exception)
        {
            throw new ServiceException("Excel文件无法解析，请确认文件未损坏且为有效xlsx");
        }
    }

    private Map<String, Integer> headerColumns(Row header)
    {
        if (header == null)
        {
            throw new ServiceException("签约数据sheet缺少表头");
        }
        Map<String, Integer> columns = new LinkedHashMap<>();
        List<String> duplicates = new ArrayList<>();
        DataFormatter formatter = new DataFormatter(Locale.ROOT);
        for (Cell cell : header)
        {
            if (cell.getCellType() == CellType.FORMULA)
            {
                throw new ServiceException("表头不允许使用公式");
            }
            String name = trim(formatter.formatCellValue(cell));
            if (name == null) continue;
            if (columns.putIfAbsent(name, cell.getColumnIndex()) != null)
            {
                duplicates.add(name);
            }
        }
        if (!duplicates.isEmpty())
        {
            throw new ServiceException("签约数据存在重复表头：" + String.join("、", duplicates));
        }
        List<String> missing = REQUIRED_HEADERS.stream().filter(name -> !columns.containsKey(name))
                .sorted().toList();
        if (!missing.isEmpty())
        {
            throw new ServiceException("签约数据缺少表头：" + String.join("、", missing));
        }
        return columns;
    }

    private List<ParsedRow> parseRows(Sheet sheet, Map<String, Integer> columns)
    {
        List<ParsedRow> result = new ArrayList<>();
        DataFormatter formatter = new DataFormatter(Locale.ROOT);
        int headerIndex = sheet.getFirstRowNum();
        for (int index = headerIndex + 1; index <= sheet.getLastRowNum(); index++)
        {
            Row row = sheet.getRow(index);
            if (row == null || blankRow(row, columns.values(), formatter)) continue;
            if (result.size() >= MAX_ROWS)
            {
                throw new ServiceException("签约数据最多500行");
            }
            result.add(parseRow(row, index + 1, columns, formatter));
        }
        if (result.isEmpty())
        {
            throw new ServiceException("签约数据sheet没有可处理的数据行");
        }
        return result;
    }

    private ParsedRow parseRow(Row row, int sourceRowNumber, Map<String, Integer> columns,
            DataFormatter formatter)
    {
        LinkedHashSet<String> errors = new LinkedHashSet<>();
        Map<String, String> values = new LinkedHashMap<>();
        for (String header : HEADERS)
        {
            Integer column = columns.get(header);
            Cell cell = column == null ? null
                    : row.getCell(column, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
            if (cell != null && cell.getCellType() == CellType.FORMULA)
            {
                errors.add("FORMULA_NOT_ALLOWED:" + header);
                values.put(header, null);
                continue;
            }
            String value;
            if ("联系电话".equals(header)) value = phoneCellText(cell, formatter);
            else if ("身份证号".equals(header) && cell != null
                    && cell.getCellType() == CellType.NUMERIC)
            {
                errors.add("IDENTITY_MUST_BE_TEXT");
                value = null;
            }
            else value = cellText(cell, formatter);
            values.put(header, value);
            if (REQUIRED_VALUES.contains(header) && value == null)
            {
                errors.add("MISSING_VALUE:" + header);
            }
        }

        OaSignOnboardContractSnapshot snapshot = new OaSignOnboardContractSnapshot();
        snapshot.setEmployeeName(values.get("姓名"));
        snapshot.setIdNumber(normalizeIdentity(values.get("身份证号")));
        snapshot.setPhone(normalizePhone(values.get("联系电话")));
        snapshot.setCurrentAddress(values.get("家庭住址"));
        snapshot.setAddressSource(values.get("家庭住址") == null ? null : "EXCEL");
        snapshot.setEmployeePost(values.get("员工岗位"));
        snapshot.setCityLevel(values.get("城市等级"));
        snapshot.setRecommendedCompany(values.get("签约公司"));
        snapshot.setLegalRepresentative(values.get("法定代表人"));
        snapshot.setRegisteredAddress(values.get("注册地"));
        snapshot.setWorkLocation(values.get("工作地"));
        snapshot.setWorkSchedule(values.get("工时制度"));
        snapshot.setRemarks(values.get("备注"));

        snapshot.setContractTypeCode(contractType(values.get("合同类型"), errors));
        snapshot.setSocialTypeCode(socialType(values.get("社保类型"), errors));
        snapshot.setContractTermCode(contractTerm(values.get("劳动合同期限形式"), errors));
        snapshot.setJobGradeCode(grade(values.get("员工职级"), errors));
        snapshot.setContractStartDate(date(row, columns.get("劳动合同起始日期"),
                values.get("劳动合同起始日期"), "CONTRACT_START", errors));
        snapshot.setContractEndDate(date(row, columns.get("劳动合同结束日期"),
                values.get("劳动合同结束日期"), "CONTRACT_END", errors));
        snapshot.setProbationStartDate(date(row, columns.get("试用期开始日期"),
                values.get("试用期开始日期"), "PROBATION_START", errors));
        snapshot.setProbationEndDate(date(row, columns.get("试用期结束日期"),
                values.get("试用期结束日期"), "PROBATION_END", errors));

        snapshot.setSalaryTotal(money(values.get("综合工资"), "SALARY_TOTAL", errors));
        snapshot.setBaseSalary(money(values.get("底薪"), "BASE_SALARY", errors));
        snapshot.setPostSalary(money(values.get("综合岗位津贴"), "POST_SALARY", errors));
        snapshot.setFieldAllowance(money(values.get("综合驻外补贴"), "FIELD_ALLOWANCE", errors));
        snapshot.setPerformanceSalary(money(values.get("月度绩效津贴"),
                "PERFORMANCE_SALARY", errors));
        validateIdentity(snapshot, errors);
        validateDates(snapshot, errors);
        validateSalary(snapshot, errors);
        if (SigningProfileCodes.SERVICE_CONTRACT.equals(snapshot.getContractTypeCode())
                && SigningProfileCodes.SOCIAL_INSURED.equals(snapshot.getSocialTypeCode()))
        {
            errors.add("UNSUPPORTED_COMBINATION");
        }
        String canonical = canonical(values, snapshot);
        return new ParsedRow(sourceRowNumber, sha256(canonical.getBytes(java.nio.charset.StandardCharsets.UTF_8)),
                snapshot, new ArrayList<>(errors));
    }

    private void validateIdentity(OaSignOnboardContractSnapshot value, Set<String> errors)
    {
        if (value.getIdNumber() == null || !value.getIdNumber().matches("[0-9]{17}[0-9X]"))
            errors.add("INVALID_ID_NUMBER");
        if (value.getPhone() == null || !value.getPhone().matches("1[0-9]{10}"))
            errors.add("INVALID_PHONE");
    }

    private void validateDates(OaSignOnboardContractSnapshot value, Set<String> errors)
    {
        if (value.getContractStartDate() != null && value.getContractEndDate() != null
                && !value.getContractEndDate().isAfter(value.getContractStartDate()))
            errors.add("INVALID_CONTRACT_DATES");
        if ((value.getProbationStartDate() == null) != (value.getProbationEndDate() == null))
            errors.add("INVALID_PROBATION_DATES");
        if (value.getProbationStartDate() != null && value.getProbationEndDate() != null
                && (value.getProbationEndDate().isBefore(value.getProbationStartDate())
                || value.getContractStartDate() != null
                && value.getProbationStartDate().isBefore(value.getContractStartDate())
                || value.getContractEndDate() != null
                && value.getProbationEndDate().isAfter(value.getContractEndDate())))
            errors.add("INVALID_PROBATION_DATES");
    }

    private void validateSalary(OaSignOnboardContractSnapshot value, Set<String> errors)
    {
        List<BigDecimal> parts = List.of(nullableZero(value.getBaseSalary()),
                nullableZero(value.getPostSalary()), nullableZero(value.getFieldAllowance()),
                nullableZero(value.getPerformanceSalary()));
        if (value.getSalaryTotal() == null || value.getBaseSalary() == null
                || value.getPostSalary() == null || value.getFieldAllowance() == null
                || value.getPerformanceSalary() == null) return;
        if (value.getSalaryTotal().signum() <= 0 || parts.stream().anyMatch(v -> v.signum() < 0))
        {
            errors.add("INVALID_SALARY");
            return;
        }
        BigDecimal calculated = parts.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        if (calculated.compareTo(value.getSalaryTotal()) != 0)
            errors.add("INVALID_SALARY_TOTAL");
    }

    private String contractType(String raw, Set<String> errors)
    {
        if (raw == null) return null;
        if (SigningProfileCodes.LABOR_CONTRACT.equalsIgnoreCase(raw) || "劳动合同".equals(raw))
            return SigningProfileCodes.LABOR_CONTRACT;
        if (SigningProfileCodes.SERVICE_CONTRACT.equalsIgnoreCase(raw) || "劳务合同".equals(raw)
                || "劳务协议".equals(raw))
            return SigningProfileCodes.SERVICE_CONTRACT;
        errors.add("INVALID_CONTRACT_TYPE");
        return null;
    }

    private String socialType(String raw, Set<String> errors)
    {
        if (raw == null) return null;
        if (SigningProfileCodes.SOCIAL_INSURED.equalsIgnoreCase(raw) || "有".equals(raw)
                || "有社保".equals(raw)) return SigningProfileCodes.SOCIAL_INSURED;
        if (SigningProfileCodes.SOCIAL_UNINSURED.equalsIgnoreCase(raw) || "无".equals(raw)
                || "无社保".equals(raw)) return SigningProfileCodes.SOCIAL_UNINSURED;
        errors.add("INVALID_SOCIAL_TYPE");
        return null;
    }

    private String contractTerm(String raw, Set<String> errors)
    {
        if (raw == null) return null;
        if (SigningProfileCodes.FIXED_TERM.equalsIgnoreCase(raw) || "固定期限".equals(raw))
            return SigningProfileCodes.FIXED_TERM;
        if (SigningProfileCodes.OPEN_ENDED.equalsIgnoreCase(raw) || "无固定期限".equals(raw))
            return SigningProfileCodes.OPEN_ENDED;
        errors.add("INVALID_CONTRACT_TERM");
        return null;
    }

    private String grade(String raw, Set<String> errors)
    {
        String value = trim(raw == null ? null : raw.replace("级", ""));
        if (value == null || !value.matches("[2-9]"))
        {
            errors.add("INVALID_JOB_GRADE");
            return null;
        }
        return value;
    }

    private LocalDate date(Row row, Integer column, String text, String code, Set<String> errors)
    {
        if (text == null || column == null) return null;
        Cell cell = row.getCell(column, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
        try
        {
            if (cell != null && cell.getCellType() == CellType.NUMERIC
                    && DateUtil.isCellDateFormatted(cell))
            {
                return cell.getDateCellValue().toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
            }
            String normalized = text.replace('/', '-').replace('.', '-');
            for (DateTimeFormatter formatter : List.of(DateTimeFormatter.ISO_LOCAL_DATE,
                    DateTimeFormatter.ofPattern("yyyy-M-d")))
            {
                try { return LocalDate.parse(normalized, formatter); }
                catch (DateTimeParseException ignored) { }
            }
        }
        catch (RuntimeException ignored) { }
        errors.add("INVALID_DATE:" + code);
        return null;
    }

    private BigDecimal money(String raw, String code, Set<String> errors)
    {
        if (raw == null) return null;
        try
        {
            String normalized = raw.replace(",", "").replace("￥", "").trim();
            BigDecimal value = new BigDecimal(normalized).setScale(2, RoundingMode.UNNECESSARY);
            if (value.signum() < 0) errors.add("INVALID_MONEY:" + code);
            return value;
        }
        catch (RuntimeException exception)
        {
            errors.add("INVALID_MONEY:" + code);
            return null;
        }
    }

    private boolean blankRow(Row row, Iterable<Integer> columns, DataFormatter formatter)
    {
        for (Integer column : columns)
        {
            Cell cell = row.getCell(column, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
            if (cell != null && (cell.getCellType() == CellType.FORMULA
                    || trim(formatter.formatCellValue(cell)) != null)) return false;
        }
        return true;
    }

    private String cellText(Cell cell, DataFormatter formatter)
    {
        if (cell == null) return null;
        return trim(formatter.formatCellValue(cell));
    }

    private String phoneCellText(Cell cell, DataFormatter formatter)
    {
        if (cell == null) return null;
        if (cell.getCellType() == CellType.NUMERIC)
        {
            return trim(NumberToTextConverter.toText(cell.getNumericCellValue()));
        }
        return cellText(cell, formatter);
    }

    private String normalizeIdentity(String raw)
    {
        return raw == null ? null : raw.replace(" ", "").toUpperCase(Locale.ROOT);
    }

    private String normalizePhone(String raw)
    {
        if (raw == null) return null;
        String value = raw.replace(" ", "").replace("-", "");
        if (value.endsWith(".0")) value = value.substring(0, value.length() - 2);
        return value;
    }

    private String canonical(Map<String, String> values, OaSignOnboardContractSnapshot snapshot)
    {
        StringBuilder result = new StringBuilder();
        for (String header : HEADERS)
        {
            result.append(header).append('=').append(values.get(header)).append('\n');
        }
        result.append("contractTypeCode=").append(snapshot.getContractTypeCode()).append('\n')
                .append("socialTypeCode=").append(snapshot.getSocialTypeCode()).append('\n')
                .append("contractTermCode=").append(snapshot.getContractTermCode()).append('\n')
                .append("jobGradeCode=").append(snapshot.getJobGradeCode());
        return result.toString();
    }

    private static BigDecimal nullableZero(BigDecimal value)
    {
        return value == null ? BigDecimal.ZERO : value;
    }

    private static String trim(String value)
    {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    static String sha256(byte[] bytes)
    {
        try
        {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(bytes);
            StringBuilder hex = new StringBuilder(64);
            for (byte value : digest) hex.append(String.format(Locale.ROOT, "%02x", value));
            return hex.toString();
        }
        catch (NoSuchAlgorithmException exception)
        {
            throw new IllegalStateException("SHA-256不可用", exception);
        }
    }

    public static final class ParsedWorkbook
    {
        private final String fileSha256;
        private final List<ParsedRow> rows;

        ParsedWorkbook(String fileSha256, List<ParsedRow> rows)
        {
            this.fileSha256 = fileSha256;
            this.rows = List.copyOf(rows);
        }

        public String getFileSha256() { return fileSha256; }
        public List<ParsedRow> getRows() { return Collections.unmodifiableList(rows); }
    }

    public static final class ParsedRow
    {
        private final int sourceRowNumber;
        private final String rowHash;
        private final OaSignOnboardContractSnapshot snapshot;
        private final List<String> errors;

        ParsedRow(int sourceRowNumber, String rowHash, OaSignOnboardContractSnapshot snapshot,
                List<String> errors)
        {
            this.sourceRowNumber = sourceRowNumber;
            this.rowHash = rowHash;
            this.snapshot = snapshot;
            this.errors = List.copyOf(errors);
        }

        public int getSourceRowNumber() { return sourceRowNumber; }
        public String getRowHash() { return rowHash; }
        public OaSignOnboardContractSnapshot getSnapshot() { return snapshot; }
        public List<String> getErrors() { return Collections.unmodifiableList(errors); }
    }
}
