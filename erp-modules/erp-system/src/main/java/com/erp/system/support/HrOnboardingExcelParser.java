package com.erp.system.support;

import java.io.IOException;
import java.io.InputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import com.erp.common.core.exception.ServiceException;
import com.erp.system.domain.HrOnboarding;
import com.erp.system.domain.HrOnboardingImportRow;

/** Dedicated parser preserving duplicate headers, source row numbers and display text. */
@Component
public class HrOnboardingExcelParser
{
    static final int MAX_SHEETS = 1;
    static final int MAX_ROWS = 5000;
    static final int MAX_COLUMNS = 64;
    static final int MAX_TEXT_LENGTH = 4096;
    static final int MAX_COMPRESSED_BYTES=10*1024*1024;
    static final int MAX_ZIP_ENTRIES=2000;
    static final long MAX_ZIP_ENTRY_BYTES=20L*1024L*1024L;
    static final long MAX_ZIP_TOTAL_BYTES=50L*1024L*1024L;
    static final long MAX_TOTAL_CELLS=100_000L;
    private static final Set<String> TEXT_ONLY_FIELDS = new HashSet<>(java.util.Arrays.asList(
            "phoneNumber", "idNumber", "bankAccount", "emergencyContactPhone"));
    private final HrOnboardingFieldRegistry registry;
    private final BusinessValidator validator;
    private final DataFormatter formatter = new DataFormatter();

    @Autowired
    public HrOnboardingExcelParser(HrOnboardingFieldRegistry registry,
            HrOnboardingImportPreviewValidator validator)
    {
        this(registry, (BusinessValidator) validator);
    }

    public HrOnboardingExcelParser(HrOnboardingFieldRegistry registry, BusinessValidator validator)
    {
        this.registry = registry;
        this.validator = validator;
    }

    public List<HrOnboardingImportRow> parse(InputStream input)
    {
        if (input == null) throw new ServiceException("IMPORT_FILE_REQUIRED");
        byte[] compressed=readCompressed(input);
        preflightZip(compressed);
        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(compressed)))
        {
            if(workbook.getNumberOfSheets()>MAX_SHEETS)throw new ServiceException("IMPORT_SHEET_LIMIT_EXCEEDED");
            if (workbook.getNumberOfSheets() == 0) return Collections.emptyList();
            Sheet sheet = workbook.getSheetAt(0);
            if(sheet.getLastRowNum()>MAX_ROWS)throw new ServiceException("IMPORT_ROW_LIMIT_EXCEEDED");
            Row headerRow = sheet.getRow(sheet.getFirstRowNum());
            if (headerRow == null) throw new ServiceException("IMPORT_HEADER_REQUIRED");
            if(headerRow.getLastCellNum()>MAX_COLUMNS)throw new ServiceException("IMPORT_COLUMN_LIMIT_EXCEEDED");
            Map<Integer, Header> headers = headers(headerRow);
            List<HrOnboardingImportRow> result = new ArrayList<>();
            Set<String> identities = new HashSet<>();
            long totalCells=headerRow.getPhysicalNumberOfCells();
            validator.beginPreview();
            try
            {
                for (int index = headerRow.getRowNum() + 1; index <= sheet.getLastRowNum(); index++)
                {
                    Row source = sheet.getRow(index);
                    if(source==null)continue;
                    if(source.getPhysicalNumberOfCells()>MAX_COLUMNS||source.getLastCellNum()>MAX_COLUMNS)
                        throw new ServiceException("IMPORT_COLUMN_LIMIT_EXCEEDED");
                    totalCells+=source.getPhysicalNumberOfCells();
                    if(totalCells>MAX_TOTAL_CELLS)throw new ServiceException("IMPORT_TOTAL_CELL_LIMIT_EXCEEDED");
                    if(blank(source, headers.keySet())) continue;
                    result.add(parseRow(source, headers, identities));
                }
                validator.completePreview(result);
            }
            finally{validator.endPreview();}
            return result;
        }
        catch(ServiceException safe){throw safe;}
        catch (IOException|RuntimeException failure)
        {
            throw new ServiceException("IMPORT_FILE_INVALID");
        }
    }

    private HrOnboardingImportRow parseRow(Row source, Map<Integer, Header> headers, Set<String> identities)
    {
        HrOnboarding payload = new HrOnboarding();
        Map<String, String> firstValue = new LinkedHashMap<>();
        List<String> structuralErrors = new ArrayList<>();
        for (Map.Entry<Integer, Header> entry : headers.entrySet())
        {
            Cell cell=source.getCell(entry.getKey());
            Header header=entry.getValue();
            if(cell!=null && cell.getCellType()==CellType.FORMULA)
            {
                add(structuralErrors,"FORMULA_NOT_ALLOWED");
                firstValue.putIfAbsent(header.key, formulaDisplay(cell));
                continue;
            }
            if(cell!=null && cell.getCellType()==CellType.NUMERIC && TEXT_ONLY_FIELDS.contains(header.key))
            {
                add(structuralErrors,"TEXT_CELL_REQUIRED");
                firstValue.putIfAbsent(header.key, formatter.formatCellValue(cell));
                continue;
            }
            String value = text(cell, header.key);
            if (value == null) continue;
            String previous = firstValue.putIfAbsent(header.key, value);
            if (previous != null && !previous.equals(value) && !structuralErrors.contains("ALIAS_CONFLICT"))
                structuralErrors.add("ALIAS_CONFLICT");
            if(value.codePointCount(0,value.length())>header.rule.getMaxLength())
            {add(structuralErrors,"FIELD_LENGTH_EXCEEDED");continue;}
            if (previous == null) apply(payload, header.key, value, structuralErrors);
        }
        intrinsic(payload, structuralErrors);
        boolean duplicate = false;
        if (payload.getIdNumber() != null && !identities.add("I:" + payload.getIdNumber())) duplicate = true;
        if (payload.getPhoneNumber() != null && !identities.add("P:" + payload.getPhoneNumber())) duplicate = true;
        if (duplicate) structuralErrors.add("SAME_FILE_DUPLICATE");

        ValidationOutcome outcome = validator.validate(payload);
        List<String> errors = new ArrayList<>(structuralErrors);
        errors.addAll(outcome.errors);
        HrOnboardingImportRow row = new HrOnboardingImportRow();
        row.setSourceRowNumber(source.getRowNum() + 1);
        row.setPayload(payload);
        row.setRawSourceValues(firstValue);
        row.setRowStatus("PREVIEWED");
        row.setVersion(0);
        row.setWarningCodeList(outcome.warnings);
        row.setErrorCodeList(errors);
        row.setCandidateType(outcome.candidateType);
        row.setCandidateUserId(outcome.candidateUserId);
        row.setCandidateOnboardingId(outcome.candidateOnboardingId);
        row.setCandidateSummary(outcome.candidateSummary);
        row.setCategory(category(errors, outcome));
        return row;
    }

    private String category(List<String> errors, ValidationOutcome outcome)
    {
        if (!errors.isEmpty()) return "INVALID";
        if (outcome.candidateUserId != null && "ACCOUNT".equals(outcome.candidateType)) return "BINDABLE_ACCOUNT";
        if ("SIMILAR".equals(outcome.candidateType)) return "POSSIBLE_DUPLICATE";
        if (!outcome.warnings.isEmpty()) return "WARNING";
        return "IMPORTABLE";
    }

    public XSSFWorkbook createTemplate()
    {
        XSSFWorkbook workbook = new XSSFWorkbook();
        Sheet sheet = workbook.createSheet("入职导入");
        Row header = sheet.createRow(0);
        Row sample = sheet.createRow(1);
        CellStyle text = workbook.createCellStyle();
        text.setDataFormat(workbook.createDataFormat().getFormat("@"));
        List<HrOnboardingFieldRegistry.FieldRule> fields = registry.getFields();
        for (int i = 0; i < fields.size(); i++)
        {
            String label = fields.get(i).getLabel();
            header.createCell(i, CellType.STRING).setCellValue(label);
            Cell cell = sample.createCell(i, CellType.BLANK);
            if ("手机号".equals(label) || "证件号码".equals(label) || "银行卡号".equals(label))
                cell.setCellStyle(text);
            sheet.setColumnWidth(i, 16 * 256);
        }
        return workbook;
    }

    private Map<Integer, Header> headers(Row source)
    {
        Map<Integer, Header> result = new LinkedHashMap<>();
        for (int index = source.getFirstCellNum(); index < source.getLastCellNum(); index++)
        {
            String label = text(source.getCell(index));
            HrOnboardingFieldRegistry.FieldRule rule = registry.resolveHeader(label);
            if (rule != null) result.put(index, new Header(rule));
        }
        if (result.isEmpty()) throw new ServiceException("IMPORT_HEADER_UNRECOGNIZED");
        return result;
    }

    private boolean blank(Row row, Set<Integer> columns)
    {
        for (Integer column : columns) if (text(row.getCell(column)) != null) return false;
        return true;
    }

    private String text(Cell cell)
    {
        return text(cell, null);
    }

    private String text(Cell cell, String key)
    {
        if (cell == null || cell.getCellType() == CellType.BLANK) return null;
        String value;
        if (cell.getCellType() == CellType.NUMERIC && DateUtil.isCellDateFormatted(cell)
                && ("birthDate".equals(key) || "expectedEntryDate".equals(key)))
            value = new SimpleDateFormat("yyyy-MM-dd").format(cell.getDateCellValue());
        else value = formatter.formatCellValue(cell);
        value = value == null ? null : value.trim();
        if(value!=null && value.length()>MAX_TEXT_LENGTH)throw new ServiceException("IMPORT_CELL_TEXT_LIMIT_EXCEEDED");
        return value == null || value.isEmpty() ? null : value;
    }

    private String formulaDisplay(Cell cell)
    {
        String formula=cell.getCellFormula();
        if(formula==null)return null;
        return formula.length()>MAX_TEXT_LENGTH?formula.substring(0,MAX_TEXT_LENGTH):formula;
    }

    private byte[] readCompressed(InputStream input)
    {
        try(InputStream source=input;ByteArrayOutputStream output=new ByteArrayOutputStream())
        {
            byte[] buffer=new byte[8192];int read,total=0;
            while((read=source.read(buffer))!=-1)
            {total+=read;if(total>MAX_COMPRESSED_BYTES)throw new ServiceException("IMPORT_FILE_SIZE_LIMIT_EXCEEDED");
                output.write(buffer,0,read);}
            byte[] value=output.toByteArray();
            if(value.length<4||value[0]!='P'||value[1]!='K')throw new ServiceException("IMPORT_FILE_INVALID");
            return value;
        }
        catch(IOException failure){throw new ServiceException("IMPORT_FILE_INVALID");}
    }

    private void preflightZip(byte[] compressed)
    {
        int entries=0;long total=0;byte[] buffer=new byte[8192];
        try(ZipInputStream zip=new ZipInputStream(new ByteArrayInputStream(compressed)))
        {
            ZipEntry entry;
            while((entry=zip.getNextEntry())!=null)
            {
                if(++entries>MAX_ZIP_ENTRIES)throw new ServiceException("IMPORT_ZIP_ENTRY_LIMIT_EXCEEDED");
                String name=entry.getName();
                if(name==null||name.startsWith("/")||name.startsWith("\\")||name.contains("../")||name.contains("..\\"))
                    throw new ServiceException("IMPORT_ZIP_PATH_INVALID");
                long entryBytes=0;int read;
                while((read=zip.read(buffer))!=-1)
                {
                    entryBytes+=read;total+=read;
                    if(entryBytes>MAX_ZIP_ENTRY_BYTES)throw new ServiceException("IMPORT_ZIP_ENTRY_TOO_LARGE");
                    if(total>MAX_ZIP_TOTAL_BYTES)throw new ServiceException("IMPORT_ZIP_TOTAL_TOO_LARGE");
                }
                long compressedSize=entry.getCompressedSize();
                if(compressedSize>0&&entryBytes>1024L*1024L&&entryBytes/compressedSize>200)
                    throw new ServiceException("IMPORT_ZIP_RATIO_SUSPICIOUS");
                zip.closeEntry();
            }
            if(entries==0)throw new ServiceException("IMPORT_FILE_INVALID");
        }
        catch(ServiceException safe){throw safe;}
        catch(IOException failure){throw new ServiceException("IMPORT_FILE_INVALID");}
    }

    private void intrinsic(HrOnboarding row, List<String> errors)
    {
        if (row.getPhoneNumber() != null && !row.getPhoneNumber().matches("1[3-9]\\d{9}")) add(errors, "PHONE_INVALID");
        if (row.getEmergencyContactPhone() != null && !row.getEmergencyContactPhone().matches("1[3-9]\\d{9}"))
            add(errors, "EMERGENCY_PHONE_INVALID");
        if (row.getIdNumber() != null && !row.getIdNumber().matches("(?i)\\d{17}[0-9X]")) add(errors, "ID_NUMBER_INVALID");
        if (row.getBankAccount() != null && !row.getBankAccount().matches("\\d{12,30}")) add(errors, "BANK_ACCOUNT_INVALID");
        if (row.getBirthDate() != null && row.getExpectedEntryDate() != null
                && row.getBirthDate().after(row.getExpectedEntryDate())) add(errors, "DATE_ORDER_INVALID");
    }

    private void apply(HrOnboarding row, String key, String value, List<String> errors)
    {
        try
        {
            switch (key)
            {
                case "employeeName": row.setEmployeeName(value); break;
                case "employeeNo": row.setEmployeeNo(value); break;
                case "companyName": row.setCompanyName(value); break;
                case "deptLevel1Name": row.setDeptLevel1Name(value); break;
                case "deptLevel2Name": row.setDeptLevel2Name(value); break;
                case "deptLevel3Name": row.setDeptLevel3Name(value); break;
                case "storeName": row.setStoreName(value); break;
                case "positionName": row.setPositionName(value); break;
                case "jobGrade": row.setJobGrade(value); break;
                case "phoneNumber": row.setPhoneNumber(value); break;
                case "departmentSupervisor": row.setDepartmentSupervisor(value); break;
                case "directSupervisorUserId": row.setDepartmentSupervisor(value); break;
                case "employeeCategory": row.setEmployeeCategory(value); break;
                case "sex": row.setSex(value); break;
                case "birthDate": row.setBirthDate(date(value)); break;
                case "idType": row.setIdType(value); break;
                case "idNumber": row.setIdNumber(value); break;
                case "registeredResidence": row.setRegisteredResidence(value); break;
                case "currentAddress": row.setCurrentAddress(value); break;
                case "maritalStatus": row.setMaritalStatus(value); break;
                case "ethnicity": row.setEthnicity(value); break;
                case "emergencyContact": row.setEmergencyContact(value); break;
                case "emergencyContactRelation": row.setEmergencyContactRelation(value); break;
                case "emergencyContactPhone": row.setEmergencyContactPhone(value); break;
                case "expectedEntryDate": row.setExpectedEntryDate(date(value)); break;
                case "workLocation": row.setWorkLocation(value); break;
                case "workCityLevel": row.setWorkCityLevel(value); break;
                case "bankName": row.setBankName(value); break;
                case "bankAccount": row.setBankAccount(value); break;
                case "contractType": row.setContractType(value); break;
                case "socialType": row.setSocialType(value); break;
                case "probationPeriod": row.setProbationPeriod(value); break;
                case "legalEntity": row.setLegalEntity(value); break;
                default: break;
            }
        }
        catch (ParseException failure) { add(errors, "DATE_INVALID"); }
    }

    private java.util.Date date(String value) throws ParseException
    {
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd");
        format.setLenient(false);
        return format.parse(value);
    }

    private void add(List<String> values, String value) { if (!values.contains(value)) values.add(value); }
    private static final class Header
    {
        private final String key;private final HrOnboardingFieldRegistry.FieldRule rule;
        Header(HrOnboardingFieldRegistry.FieldRule rule){this.rule=rule;this.key=rule.getKey();}
    }

    @FunctionalInterface
    public interface BusinessValidator
    {
        ValidationOutcome validate(HrOnboarding row);
        default void beginPreview(){}
        default void completePreview(List<HrOnboardingImportRow> rows){}
        default void endPreview(){}
    }

    public static class ValidationOutcome
    {
        private final List<String> warnings;
        private final List<String> errors;
        private final String candidateType;
        private final Long candidateUserId;
        private final Long candidateOnboardingId;
        private final String candidateSummary;
        public ValidationOutcome(List<String> warnings, List<String> errors, String candidateType,
                Long candidateUserId, Long candidateOnboardingId, String candidateSummary)
        {
            this.warnings = warnings == null ? Collections.emptyList() : warnings;
            this.errors = errors == null ? Collections.emptyList() : errors;
            this.candidateType = candidateType;
            this.candidateUserId = candidateUserId;
            this.candidateOnboardingId = candidateOnboardingId;
            this.candidateSummary = candidateSummary;
        }
        public static ValidationOutcome clean() { return new ValidationOutcome(null, null, null, null, null, null); }
        public static ValidationOutcome bindable(Long userId, String summary) { return new ValidationOutcome(null, null, "ACCOUNT", userId, null, summary); }
        public static ValidationOutcome similar(String summary) { return new ValidationOutcome(null, null, "SIMILAR", null, null, summary); }
    }
}
