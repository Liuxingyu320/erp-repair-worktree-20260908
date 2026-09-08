package com.erp.system.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import com.erp.system.domain.HrOnboardingImportRow;
import com.erp.system.api.domain.SysDept;
import com.erp.system.api.domain.SysUser;
import com.erp.system.domain.SysPost;
import com.erp.system.domain.vo.HrOnboardingConflictVo;
import com.erp.system.mapper.SysPostMapper;
import com.erp.system.service.IHrOnboardingPositionConfigService;
import com.erp.system.service.impl.HrOnboardingAccessService;
import com.erp.system.service.impl.HrOnboardingConflictService;

class HrOnboardingExcelParserTest
{
    @Test
    void aliasesMapToCanonicalFieldsAndConflictingAliasValuesAreInvalid() throws Exception
    {
        byte[] workbook = workbook(
                new String[] { "姓名", "预计入职日期", "入职日期", "职位", "入职岗位", "岗位职级", "户籍地址" },
                new String[] { "张三", "2026-07-20", "2026-07-21", "店员", "店员", "P2", "福建省厦门市" });

        List<HrOnboardingImportRow> rows = parser(row -> HrOnboardingExcelParser.ValidationOutcome.clean())
                .parse(new ByteArrayInputStream(workbook));

        assertThat(rows).singleElement().satisfies(row -> {
            assertThat(row.getSourceRowNumber()).isEqualTo(2);
            assertThat(row.getPayload().getPositionName()).isEqualTo("店员");
            assertThat(row.getPayload().getJobGrade()).isEqualTo("P2");
            assertThat(row.getPayload().getRegisteredResidence()).isEqualTo("福建省厦门市");
            assertThat(row.getErrorCodeList()).contains("ALIAS_CONFLICT");
            assertThat(row.getCategory()).isEqualTo("INVALID");
        });
    }

    @Test
    void preservesSourceRowsAndLongNumberTextAndDetectsSameFileDuplicates() throws Exception
    {
        byte[] workbook = workbook(
                new String[] { "姓名", "手机号", "证件号码", "银行卡号" },
                new String[] { "甲", "13800138000", "350203199001011234", "6222020200001234567" },
                new String[] { "乙", "13800138000", "350203199001011235", "6222020200001234568" });

        List<HrOnboardingImportRow> rows = parser(row -> HrOnboardingExcelParser.ValidationOutcome.clean())
                .parse(new ByteArrayInputStream(workbook));

        assertThat(rows).extracting(HrOnboardingImportRow::getSourceRowNumber).containsExactly(2, 3);
        assertThat(rows.get(0).getPayload().getPhoneNumber()).isEqualTo("13800138000");
        assertThat(rows.get(0).getPayload().getIdNumber()).isEqualTo("350203199001011234");
        assertThat(rows.get(0).getPayload().getBankAccount()).isEqualTo("6222020200001234567");
        assertThat(rows.get(1).getCategory()).isEqualTo("INVALID");
        assertThat(rows.get(1).getErrorCodeList()).contains("SAME_FILE_DUPLICATE");
    }

    @Test
    void appliesDeterministicSingleCategoryToAllBusinessFindingFamilies() throws Exception
    {
        assertCategory("INVALID", outcomeErrors("ACTIVE_ONBOARDING"));
        assertCategory("INVALID", outcomeErrors("ORGANIZATION_UNRESOLVED", "INVALID_ENUM", "DATE_ORDER_INVALID",
                "PHONE_INVALID", "ID_NUMBER_INVALID", "BANK_ACCOUNT_INVALID", "DICTIONARY_UNRESOLVED",
                "STORE_AMBIGUOUS", "POST_AMBIGUOUS", "SUPERVISOR_AMBIGUOUS", "EXISTING_EMPLOYEE_BLOCKING"));
        assertCategory("WARNING", outcomeWarnings("CANCELLED_ONBOARDING"));
        assertCategory("BINDABLE_ACCOUNT", HrOnboardingExcelParser.ValidationOutcome.bindable(88L, "员工 ****8000"));
        assertCategory("POSSIBLE_DUPLICATE", HrOnboardingExcelParser.ValidationOutcome.similar("姓名相似候选"));
        assertCategory("IMPORTABLE", HrOnboardingExcelParser.ValidationOutcome.clean());
    }

    @Test
    void templateKeepsPhoneIdAndBankColumnsAsText() throws Exception
    {
        try (XSSFWorkbook workbook = parser(row -> HrOnboardingExcelParser.ValidationOutcome.clean()).createTemplate())
        {
            Sheet sheet = workbook.getSheetAt(0);
            Row headers = sheet.getRow(0);
            Row sample = sheet.getRow(1);
            for (String name : Arrays.asList("手机号", "证件号码", "银行卡号"))
            {
                int column = find(headers, name);
                assertThat(sample.getCell(column).getCellType()).isEqualTo(CellType.BLANK);
                assertThat(sample.getCell(column).getCellStyle().getDataFormatString()).isEqualTo("@");
            }
        }
    }

    @Test
    void convertsRealExcelNumericDateCellsWithoutDisplayTextRoundTrip() throws Exception
    {
        byte[] workbook;
        try (XSSFWorkbook source = new XSSFWorkbook(); ByteArrayOutputStream output = new ByteArrayOutputStream())
        {
            Sheet sheet = source.createSheet("导入");
            Row header = sheet.createRow(0);
            header.createCell(0).setCellValue("姓名");
            header.createCell(1).setCellValue("预计入职日期");
            Row row = sheet.createRow(1);
            row.createCell(0).setCellValue("张三");
            org.apache.poi.ss.usermodel.Cell date = row.createCell(1);
            date.setCellValue(java.sql.Date.valueOf("2026-07-20"));
            org.apache.poi.ss.usermodel.CellStyle style = source.createCellStyle();
            style.setDataFormat(source.createDataFormat().getFormat("m/d/yy"));
            date.setCellStyle(style);
            source.write(output); workbook = output.toByteArray();
        }

        HrOnboardingImportRow row = parser(value -> HrOnboardingExcelParser.ValidationOutcome.clean())
                .parse(new ByteArrayInputStream(workbook)).get(0);

        assertThat(new java.text.SimpleDateFormat("yyyy-MM-dd").format(row.getPayload().getExpectedEntryDate()))
                .isEqualTo("2026-07-20");
        assertThat(row.getErrorCodeList()).doesNotContain("DATE_INVALID");
    }

    @Test
    void rejectsNumericSensitiveAndFormulaCellsWithoutUsingCachedValues() throws Exception
    {
        byte[] workbook;
        try (XSSFWorkbook source = new XSSFWorkbook(); ByteArrayOutputStream output = new ByteArrayOutputStream())
        {
            Sheet sheet = source.createSheet("导入");
            Row header = sheet.createRow(0);
            header.createCell(0).setCellValue("手机号");
            header.createCell(1).setCellValue("证件号码");
            header.createCell(2).setCellValue("姓名");
            Row row = sheet.createRow(1);
            row.createCell(0).setCellValue(13800138000d);
            row.createCell(1).setCellValue(350203199001011234d);
            row.createCell(2).setCellFormula("\"张三\"");
            source.write(output); workbook = output.toByteArray();
        }

        HrOnboardingImportRow row = parser(value -> HrOnboardingExcelParser.ValidationOutcome.clean())
                .parse(new ByteArrayInputStream(workbook)).get(0);

        assertThat(row.getCategory()).isEqualTo("INVALID");
        assertThat(row.getErrorCodeList()).contains("TEXT_CELL_REQUIRED", "FORMULA_NOT_ALLOWED");
        assertThat(row.getPayload().getIdNumber()).isNull();
    }

    @Test
    void enforcesWorkbookSheetRowColumnAndTextCaps() throws Exception
    {
        try (XSSFWorkbook source = new XSSFWorkbook(); ByteArrayOutputStream output = new ByteArrayOutputStream())
        {
            source.createSheet("one").createRow(0).createCell(0).setCellValue("姓名");
            source.createSheet("two"); source.write(output);
            assertThatThrownBy(() -> parser(value -> HrOnboardingExcelParser.ValidationOutcome.clean())
                    .parse(new ByteArrayInputStream(output.toByteArray())))
                    .isInstanceOf(com.erp.common.core.exception.ServiceException.class)
                    .hasMessage("IMPORT_SHEET_LIMIT_EXCEEDED");
        }
        try (XSSFWorkbook source = new XSSFWorkbook(); ByteArrayOutputStream output = new ByteArrayOutputStream())
        {
            Sheet sheet = source.createSheet("one"); Row header = sheet.createRow(0);
            header.createCell(0).setCellValue("姓名"); header.createCell(64).setCellValue("手机号");
            source.write(output);
            assertThatThrownBy(() -> parser(value -> HrOnboardingExcelParser.ValidationOutcome.clean())
                    .parse(new ByteArrayInputStream(output.toByteArray())))
                    .isInstanceOf(com.erp.common.core.exception.ServiceException.class)
                    .hasMessage("IMPORT_COLUMN_LIMIT_EXCEEDED");
        }
        byte[] longText = workbook(new String[] { "姓名" }, new String[] { String.join("", Collections.nCopies(4097, "甲")) });
        assertThatThrownBy(() -> parser(value -> HrOnboardingExcelParser.ValidationOutcome.clean())
                .parse(new ByteArrayInputStream(longText)))
                .isInstanceOf(com.erp.common.core.exception.ServiceException.class)
                .hasMessage("IMPORT_CELL_TEXT_LIMIT_EXCEEDED");

        try (XSSFWorkbook source = new XSSFWorkbook(); ByteArrayOutputStream output = new ByteArrayOutputStream())
        {
            Sheet sheet=source.createSheet("one");sheet.createRow(0).createCell(0).setCellValue("姓名");
            sheet.createRow(5001).createCell(0).setCellValue("超限");source.write(output);
            assertThatThrownBy(()->parser(value->HrOnboardingExcelParser.ValidationOutcome.clean())
                    .parse(new ByteArrayInputStream(output.toByteArray())))
                    .isInstanceOf(com.erp.common.core.exception.ServiceException.class)
                    .hasMessage("IMPORT_ROW_LIMIT_EXCEEDED");
        }
    }

    @Test
    void rejectsUnrecognizedPhysicalDataColumnsAndExpandedZipBeforeBusinessValidation() throws Exception
    {
        java.util.concurrent.atomic.AtomicInteger validations=new java.util.concurrent.atomic.AtomicInteger();
        byte[] extra;
        try(XSSFWorkbook source=new XSSFWorkbook();ByteArrayOutputStream output=new ByteArrayOutputStream())
        {
            Sheet sheet=source.createSheet("导入");sheet.createRow(0).createCell(0).setCellValue("姓名");
            Row row=sheet.createRow(1);row.createCell(0).setCellValue("张三");row.createCell(64).setCellValue("隐藏列");
            source.write(output);extra=output.toByteArray();
        }
        assertThatThrownBy(()->parser(row->{validations.incrementAndGet();return HrOnboardingExcelParser.ValidationOutcome.clean();})
                .parse(new ByteArrayInputStream(extra))).isInstanceOf(com.erp.common.core.exception.ServiceException.class)
                .hasMessage("IMPORT_COLUMN_LIMIT_EXCEEDED");
        assertThat(validations).hasValue(0);

        ByteArrayOutputStream compressed=new ByteArrayOutputStream();
        try(java.util.zip.ZipOutputStream zip=new java.util.zip.ZipOutputStream(compressed))
        {
            zip.putNextEntry(new java.util.zip.ZipEntry("xl/worksheets/sheet1.xml"));
            byte[] block=new byte[8192];for(int i=0;i<2600;i++)zip.write(block);zip.closeEntry();
        }
        assertThat(compressed.size()).isLessThan(10*1024*1024);
        assertThatThrownBy(()->parser(row->{validations.incrementAndGet();return HrOnboardingExcelParser.ValidationOutcome.clean();})
                .parse(new ByteArrayInputStream(compressed.toByteArray())))
                .isInstanceOf(com.erp.common.core.exception.ServiceException.class)
                .hasMessage("IMPORT_ZIP_ENTRY_TOO_LARGE");
        assertThat(validations).hasValue(0);
    }

    @Test
    void registryLengthLimitsAcceptBoundariesAndRejectOverflowBeforePayloadMaterialization() throws Exception
    {
        String name64=String.join("",Collections.nCopies(64,"甲"));
        String name65=name64+"乙";String address255=String.join("",Collections.nCopies(255,"址"));
        String address256=address255+"超";
        byte[] bytes=workbook(new String[]{"姓名","户口所在地"},
                new String[]{name64,address255},new String[]{name65,address256});

        List<HrOnboardingImportRow> rows=parser(row->HrOnboardingExcelParser.ValidationOutcome.clean())
                .parse(new ByteArrayInputStream(bytes));

        assertThat(rows.get(0).getErrorCodeList()).doesNotContain("FIELD_LENGTH_EXCEEDED");
        assertThat(rows.get(0).getPayload().getEmployeeName()).hasSize(64);
        assertThat(rows.get(0).getPayload().getRegisteredResidence()).hasSize(255);
        assertThat(rows.get(1).getCategory()).isEqualTo("INVALID");
        assertThat(rows.get(1).getErrorCodeList()).contains("FIELD_LENGTH_EXCEEDED");
        assertThat(rows.get(1).getPayload().getEmployeeName()).isNull();
        assertThat(rows.get(1).getPayload().getRegisteredResidence()).isNull();
        assertThat(rows.get(1).getRawSourceValues()).containsEntry("employeeName",name65)
                .containsEntry("registeredResidence",address256);
    }

    @Test
    void normalizesEveryDictionaryLabelToCanonicalValueAndRejectsAmbiguity()
    {
        ValidatorFixture fixture = validatorFixture();
        com.erp.system.domain.HrOnboarding row = validRow(); row.setEmployeeCategory("正式");
        fixture.validator.validate(row);
        assertThat(row.getEmployeeCategory()).isEqualTo("FORMAL");

        Map<String,Object> defaults = new LinkedHashMap<>();
        defaults.put("employeeCategory", Arrays.asList(option("正式", "FORMAL"), option("正式", "REGULAR")));
        Map<String,Object> options = new LinkedHashMap<>(); options.put("dictionaryDefaults", defaults);
        org.mockito.Mockito.when(fixture.configs.options()).thenReturn(options);
        row.setEmployeeCategory("正式");
        assertThat(errors(fixture.validator.validate(row))).contains("DICTIONARY_MAPPING_AMBIGUOUS");
    }

    @Test
    void normalizesAllNineRoutedDictionaryFields()
    {
        ValidatorFixture fixture=validatorFixture();com.erp.system.domain.HrOnboarding row=validRow();
        row.setEmployeeCategory("正式");row.setSex("男");row.setIdType("身份证");row.setMaritalStatus("未婚");
        row.setEthnicity("汉族");row.setWorkCityLevel("一线");row.setContractType("固定期限");
        row.setSocialType("本地");row.setProbationPeriod("三个月");
        Map<String,Object> defaults=new LinkedHashMap<>();
        defaults.put("employeeCategory",Collections.singletonList(option("正式","FORMAL")));
        defaults.put("sex",Collections.singletonList(option("男","M")));
        defaults.put("idType",Collections.singletonList(option("身份证","ID")));
        defaults.put("maritalStatus",Collections.singletonList(option("未婚","SINGLE")));
        defaults.put("ethnicity",Collections.singletonList(option("汉族","HAN")));
        defaults.put("workCityLevel",Collections.singletonList(option("一线","T1")));
        defaults.put("contractType",Collections.singletonList(option("固定期限","FIXED")));
        defaults.put("socialType",Collections.singletonList(option("本地","LOCAL")));
        defaults.put("probationPeriod",Collections.singletonList(option("三个月","3M")));
        Map<String,Object> options=new LinkedHashMap<>();options.put("dictionaryDefaults",defaults);
        org.mockito.Mockito.when(fixture.configs.options()).thenReturn(options);

        assertThat(errors(fixture.validator.validate(row))).isEmpty();
        assertThat(Arrays.asList(row.getEmployeeCategory(),row.getSex(),row.getIdType(),row.getMaritalStatus(),
                row.getEthnicity(),row.getWorkCityLevel(),row.getContractType(),row.getSocialType(),row.getProbationPeriod()))
                .containsExactly("FORMAL","M","ID","SINGLE","HAN","T1","FIXED","LOCAL","3M");
    }

    @Test
    void productionValidatorPreloadsReferenceDataOncePerPreviewSession() throws Exception
    {
        ValidatorFixture fixture=validatorFixture();
        byte[] bytes=workbook(new String[]{"姓名","所属公司","职位","手机号","预计入职日期","人员类别"},
                new String[]{"甲","华东公司","店员","13800138000","2026-07-20","正式"},
                new String[]{"乙","华东公司","店员","13900139000","2026-07-21","正式"});

        new HrOnboardingExcelParser(new HrOnboardingFieldRegistry(),fixture.validator)
                .parse(new ByteArrayInputStream(bytes));

        org.mockito.Mockito.verify(fixture.access,org.mockito.Mockito.times(1))
                .listScopedDepartments(org.mockito.ArgumentMatchers.any());
        org.mockito.Mockito.verify(fixture.access,org.mockito.Mockito.times(1))
                .listScopedUsers(org.mockito.ArgumentMatchers.any());
        org.mockito.Mockito.verify(fixture.configs,org.mockito.Mockito.times(1)).options();
        org.mockito.Mockito.verify(fixture.conflicts,org.mockito.Mockito.times(1))
                .findImportConflictsBatch(org.mockito.ArgumentMatchers.anyList());
        org.mockito.Mockito.verify(fixture.conflicts,org.mockito.Mockito.never())
                .findImportConflicts(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void validatesEmergencyContactPhoneAndRetainsRawSourceValuesContract() throws Exception
    {
        byte[] bytes = workbook(new String[] { "姓名", "紧急联系人电话", "预计入职日期" },
                new String[] { "张三", "123", "not-a-date" });

        HrOnboardingImportRow row = parser(value -> HrOnboardingExcelParser.ValidationOutcome.clean())
                .parse(new ByteArrayInputStream(bytes)).get(0);

        assertThat(row.getErrorCodeList()).contains("EMERGENCY_PHONE_INVALID", "DATE_INVALID");
        assertThat(Arrays.stream(HrOnboardingImportRow.class.getMethods()).map(java.lang.reflect.Method::getName))
                .contains("getRawSourceValues");
    }

    @Test
    void stagingPayloadCodecUsesVersionedEnvelopeAndReadsLegacyPayload() throws Exception
    {
        Class<?> codec = Class.forName("com.erp.system.support.HrOnboardingImportPayloadCodec");
        Object instance = codec.getConstructor().newInstance();
        com.erp.system.domain.HrOnboarding payload = validRow();
        Map<String,String> raw = new LinkedHashMap<>(); raw.put("expectedEntryDate", "bad-date");
        String encoded = (String) codec.getMethod("encode", com.erp.system.domain.HrOnboarding.class, Map.class)
                .invoke(instance, payload, raw);
        assertThat(encoded).contains("\"schemaVersion\":1", "\"rawSourceValues\"");
        HrOnboardingImportRow envelope = new HrOnboardingImportRow(); envelope.setPayloadJson(encoded);
        codec.getMethod("hydrate", HrOnboardingImportRow.class).invoke(instance, envelope);
        assertThat(envelope.getPayload().getEmployeeName()).isEqualTo("张三");
        assertThat(envelope.getRawSourceValues()).containsEntry("expectedEntryDate", "bad-date");

        HrOnboardingImportRow legacy = new HrOnboardingImportRow();
        legacy.setPayloadJson(com.alibaba.fastjson2.JSON.toJSONString(payload));
        codec.getMethod("hydrate", HrOnboardingImportRow.class).invoke(instance, legacy);
        assertThat(legacy.getPayload().getEmployeeName()).isEqualTo("张三");
    }

    @Test
    void productionValidatorRejectsMissingCreateRequiredFieldsBeforeStagingConfirmation()
    {
        HrOnboardingAccessService access = org.mockito.Mockito.mock(HrOnboardingAccessService.class);
        SysPostMapper posts = org.mockito.Mockito.mock(SysPostMapper.class);
        IHrOnboardingPositionConfigService configs = org.mockito.Mockito.mock(IHrOnboardingPositionConfigService.class);
        HrOnboardingConflictService conflicts = org.mockito.Mockito.mock(HrOnboardingConflictService.class);
        org.mockito.Mockito.when(access.listScopedDepartments(org.mockito.ArgumentMatchers.any()))
                .thenReturn(Collections.emptyList());
        org.mockito.Mockito.when(posts.selectPostList(org.mockito.ArgumentMatchers.any()))
                .thenReturn(Collections.emptyList());
        org.mockito.Mockito.when(configs.options()).thenReturn(dictionaryOptions());

        HrOnboardingExcelParser.ValidationOutcome outcome = new HrOnboardingImportPreviewValidator(
                access, posts, configs, conflicts, () -> 7L).validate(new com.erp.system.domain.HrOnboarding());

        assertThat(errors(outcome)).contains("EMPLOYEE_NAME_REQUIRED", "PHONE_NUMBER_REQUIRED",
                "EXPECTED_ENTRY_DATE_REQUIRED", "EMPLOYEE_CATEGORY_REQUIRED",
                "ORGANIZATION_UNRESOLVED", "POST_UNRESOLVED");
    }

    @Test
    void suppliedDictionaryValueFailsClosedWhenProbationMappingOrOptionsAreMissing()
    {
        ValidatorFixture fixture = validatorFixture();
        com.erp.system.domain.HrOnboarding row = validRow();
        row.setProbationPeriod("三个月");
        Map<String,Object> defaults = new LinkedHashMap<>();
        defaults.put("employeeCategory", Collections.singletonList(option("正式", "FORMAL")));
        Map<String,Object> options = new LinkedHashMap<>(); options.put("dictionaryDefaults", defaults);
        org.mockito.Mockito.when(fixture.configs.options()).thenReturn(options);

        HrOnboardingExcelParser.ValidationOutcome outcome = fixture.validator.validate(row);

        assertThat(errors(outcome)).contains("DICTIONARY_CONFIGURATION_MISSING");
    }

    @Test
    void resolvesDuplicateLeafByCompleteParentPathAndRejectsContradictoryPath()
    {
        ValidatorFixture fixture = validatorFixture();
        SysDept companyA = dept(1L, 0L, "甲公司", "COMPANY", "0");
        SysDept companyB = dept(2L, 0L, "乙公司", "COMPANY", "0");
        SysDept salesA = dept(11L, 1L, "销售部", null, "0,1");
        SysDept salesB = dept(22L, 2L, "销售部", null, "0,2");
        SysDept storeA = dept(111L, 11L, "中心店", "STORE", "0,1,11");
        SysDept storeB = dept(222L, 22L, "中心店", "STORE", "0,2,22");
        org.mockito.Mockito.when(fixture.access.listScopedDepartments(org.mockito.ArgumentMatchers.any()))
                .thenReturn(Arrays.asList(companyA, companyB, salesA, salesB, storeA, storeB));
        com.erp.system.domain.HrOnboarding valid = validRow();
        valid.setCompanyName("乙公司"); valid.setDeptLevel1Name("销售部"); valid.setStoreName("中心店");

        HrOnboardingExcelParser.ValidationOutcome resolved = fixture.validator.validate(valid);

        assertThat(errors(resolved)).doesNotContain("ORGANIZATION_AMBIGUOUS", "ORGANIZATION_CONTRADICTORY");
        assertThat(valid.getTargetDeptId()).isEqualTo(22L);
        assertThat(valid.getTargetStoreId()).isEqualTo(222L);

        com.erp.system.domain.HrOnboarding contradictory = validRow();
        contradictory.setCompanyName("甲公司"); contradictory.setDeptLevel1Name("销售部");
        contradictory.setStoreName("中心店");
        // Remove the matching A-store, leaving a supplied path whose leaf belongs to B.
        org.mockito.Mockito.when(fixture.access.listScopedDepartments(org.mockito.ArgumentMatchers.any()))
                .thenReturn(Arrays.asList(companyA, companyB, salesA, salesB, storeB));
        assertThat(errors(fixture.validator.validate(contradictory)))
                .contains("ORGANIZATION_CONTRADICTORY");
    }

    @Test
    void productionValidatorClassifiesActiveOnboardingInvalidAndCancelledOnboardingWarning()
    {
        ValidatorFixture fixture=validatorFixture();
        HrOnboardingConflictVo active=new HrOnboardingConflictVo();active.setSourceType("ONBOARDING");
        active.setBlocking(true);active.setCandidateOnboardingId(101L);
        org.mockito.Mockito.when(fixture.conflicts.findImportConflicts(org.mockito.ArgumentMatchers.any()))
                .thenReturn(Collections.singletonList(active));
        assertThat(errors(fixture.validator.validate(validRow()))).contains("ACTIVE_ONBOARDING");

        HrOnboardingConflictVo cancelled=new HrOnboardingConflictVo();cancelled.setSourceType("ONBOARDING");
        cancelled.setBlocking(false);cancelled.setCandidateOnboardingId(102L);
        org.mockito.Mockito.when(fixture.conflicts.findImportConflicts(org.mockito.ArgumentMatchers.any()))
                .thenReturn(Collections.singletonList(cancelled));
        HrOnboardingExcelParser.ValidationOutcome outcome=fixture.validator.validate(validRow());
        assertThat(errors(outcome)).isEmpty();
        assertThat(warnings(outcome)).contains("CANCELLED_ONBOARDING");
    }

    @Test
    void productionValidatorResolvesScopedTargetsAndClassifiesLatestBindableAccount() throws Exception
    {
        HrOnboardingAccessService access = org.mockito.Mockito.mock(HrOnboardingAccessService.class);
        SysPostMapper posts = org.mockito.Mockito.mock(SysPostMapper.class);
        IHrOnboardingPositionConfigService configs = org.mockito.Mockito.mock(IHrOnboardingPositionConfigService.class);
        HrOnboardingConflictService conflicts = org.mockito.Mockito.mock(HrOnboardingConflictService.class);
        SysDept department = new SysDept(); department.setDeptId(10L); department.setDeptName("华东公司");
        SysUser supervisor = new SysUser(); supervisor.setUserId(20L); supervisor.setNickName("李主管");
        SysPost post = new SysPost(); post.setPostId(30L); post.setPostName("店员"); post.setStatus("0");
        org.mockito.Mockito.when(access.listScopedDepartments(org.mockito.ArgumentMatchers.any()))
                .thenReturn(Collections.singletonList(department));
        org.mockito.Mockito.when(access.listScopedUsers(org.mockito.ArgumentMatchers.any()))
                .thenReturn(Collections.singletonList(supervisor));
        org.mockito.Mockito.when(posts.selectPostList(org.mockito.ArgumentMatchers.any()))
                .thenReturn(Collections.singletonList(post));
        org.mockito.Mockito.when(configs.options()).thenReturn(dictionaryOptions());
        HrOnboardingConflictVo candidate = new HrOnboardingConflictVo(); candidate.setCandidateUserId(88L);
        candidate.setEligibleForBind(true); candidate.setBlocking(true); candidate.setName("候选员工");
        candidate.setMaskedPhone("138****8000"); candidate.setSourceType("EMPLOYEE_ACCOUNT");
        org.mockito.Mockito.when(conflicts.findImportConflicts(org.mockito.ArgumentMatchers.any()))
                .thenReturn(Collections.singletonList(candidate));
        HrOnboardingImportPreviewValidator validator = new HrOnboardingImportPreviewValidator(
                access, posts, configs, conflicts, () -> 7L);
        byte[] bytes = workbook(new String[] { "姓名", "所属公司", "职位", "直属主管", "手机号", "预计入职日期", "人员类别" },
                new String[] { "张三", "华东公司", "店员", "李主管", "13800138000", "2026-07-20", "正式" });

        HrOnboardingImportRow row = new HrOnboardingExcelParser(new HrOnboardingFieldRegistry(), validator)
                .parse(new ByteArrayInputStream(bytes)).get(0);

        assertThat(row.getCategory()).as(row.getErrorCodeList().toString()).isEqualTo("BINDABLE_ACCOUNT");
        assertThat(row.getPayload().getTargetDeptId()).isEqualTo(10L);
        assertThat(row.getPayload().getTargetPostId()).isEqualTo(30L);
        assertThat(row.getPayload().getDirectSupervisorUserId()).isEqualTo(20L);
        assertThat(row.getPayload().getOwnerUserId()).isEqualTo(7L);
        assertThat(row.getCandidateSummary()).contains("138****8000").doesNotContain("13800138000");
    }

    @Test
    void productionValidatorClassifiesSameNameWithDifferentIdentityAsPossibleDuplicate() throws Exception
    {
        HrOnboardingAccessService access = org.mockito.Mockito.mock(HrOnboardingAccessService.class);
        SysPostMapper posts = org.mockito.Mockito.mock(SysPostMapper.class);
        IHrOnboardingPositionConfigService configs = org.mockito.Mockito.mock(IHrOnboardingPositionConfigService.class);
        HrOnboardingConflictService conflicts = org.mockito.Mockito.mock(HrOnboardingConflictService.class);
        SysDept department = new SysDept(); department.setDeptId(10L); department.setDeptName("华东公司");
        SysPost post = new SysPost(); post.setPostId(30L); post.setPostName("店员"); post.setStatus("0");
        SysUser similar = new SysUser(); similar.setUserId(66L); similar.setNickName("张三");
        org.mockito.Mockito.when(access.listScopedDepartments(org.mockito.ArgumentMatchers.any()))
                .thenReturn(Collections.singletonList(department));
        org.mockito.Mockito.when(access.listScopedUsers(org.mockito.ArgumentMatchers.any()))
                .thenReturn(Collections.singletonList(similar));
        org.mockito.Mockito.when(posts.selectPostList(org.mockito.ArgumentMatchers.any()))
                .thenReturn(Collections.singletonList(post));
        org.mockito.Mockito.when(configs.options()).thenReturn(dictionaryOptions());
        org.mockito.Mockito.when(conflicts.findImportConflicts(org.mockito.ArgumentMatchers.any()))
                .thenReturn(Collections.emptyList());
        org.mockito.Mockito.when(conflicts.findImportConflictsBatch(org.mockito.ArgumentMatchers.anyList()))
                .thenAnswer(call->{java.util.Map<com.erp.system.domain.HrOnboarding,List<HrOnboardingConflictVo>> map=
                        new java.util.IdentityHashMap<>();
                    for(com.erp.system.domain.HrOnboarding item:call.<List<com.erp.system.domain.HrOnboarding>>getArgument(0))
                        map.put(item,Collections.emptyList());return map;});
        byte[] bytes = workbook(new String[] { "姓名", "所属公司", "职位", "手机号", "预计入职日期", "人员类别" },
                new String[] { "张三", "华东公司", "店员", "13900139000", "2026-07-20", "正式" });

        HrOnboardingImportRow row = new HrOnboardingExcelParser(new HrOnboardingFieldRegistry(),
                new HrOnboardingImportPreviewValidator(access, posts, configs, conflicts, () -> 7L))
                .parse(new ByteArrayInputStream(bytes)).get(0);

        assertThat(row.getCategory()).isEqualTo("POSSIBLE_DUPLICATE");
        assertThat(row.getCandidateSummary()).doesNotContain("13900139000");
    }

    private void assertCategory(String expected, HrOnboardingExcelParser.ValidationOutcome outcome) throws Exception
    {
        byte[] bytes = workbook(new String[] { "姓名" }, new String[] { "唯一姓名" });
        assertThat(parser(row -> outcome).parse(new ByteArrayInputStream(bytes)))
                .singleElement().extracting(HrOnboardingImportRow::getCategory).isEqualTo(expected);
    }

    private HrOnboardingExcelParser parser(HrOnboardingExcelParser.BusinessValidator validator)
    {
        return new HrOnboardingExcelParser(new HrOnboardingFieldRegistry(), validator);
    }

    private static HrOnboardingExcelParser.ValidationOutcome outcomeErrors(String... codes)
    {
        return new HrOnboardingExcelParser.ValidationOutcome(Collections.emptyList(), Arrays.asList(codes),
                null, null, null, null);
    }

    private static HrOnboardingExcelParser.ValidationOutcome outcomeWarnings(String... codes)
    {
        return new HrOnboardingExcelParser.ValidationOutcome(Arrays.asList(codes), Collections.emptyList(),
                null, null, null, null);
    }

    @SuppressWarnings("unchecked")
    private static List<String> errors(HrOnboardingExcelParser.ValidationOutcome outcome)
    {
        try
        {
            java.lang.reflect.Field field = outcome.getClass().getDeclaredField("errors");
            field.setAccessible(true); return (List<String>) field.get(outcome);
        }
        catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
    }

    @SuppressWarnings("unchecked")
    private static List<String> warnings(HrOnboardingExcelParser.ValidationOutcome outcome)
    {
        try
        {
            java.lang.reflect.Field field = outcome.getClass().getDeclaredField("warnings");
            field.setAccessible(true); return (List<String>) field.get(outcome);
        }
        catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
    }

    private ValidatorFixture validatorFixture()
    {
        HrOnboardingAccessService access = org.mockito.Mockito.mock(HrOnboardingAccessService.class);
        SysPostMapper posts = org.mockito.Mockito.mock(SysPostMapper.class);
        IHrOnboardingPositionConfigService configs = org.mockito.Mockito.mock(IHrOnboardingPositionConfigService.class);
        HrOnboardingConflictService conflicts = org.mockito.Mockito.mock(HrOnboardingConflictService.class);
        SysDept company = dept(10L, 0L, "华东公司", "COMPANY", "0");
        SysPost post = new SysPost(); post.setPostId(30L); post.setPostName("店员"); post.setStatus("0");
        org.mockito.Mockito.when(access.listScopedDepartments(org.mockito.ArgumentMatchers.any()))
                .thenReturn(Collections.singletonList(company));
        org.mockito.Mockito.when(access.listScopedUsers(org.mockito.ArgumentMatchers.any()))
                .thenReturn(Collections.emptyList());
        org.mockito.Mockito.when(posts.selectPostList(org.mockito.ArgumentMatchers.any()))
                .thenReturn(Collections.singletonList(post));
        org.mockito.Mockito.when(conflicts.findImportConflicts(org.mockito.ArgumentMatchers.any()))
                .thenReturn(Collections.emptyList());
        org.mockito.Mockito.when(conflicts.findImportConflictsBatch(org.mockito.ArgumentMatchers.anyList()))
                .thenAnswer(call->{java.util.Map<com.erp.system.domain.HrOnboarding,List<HrOnboardingConflictVo>> map=
                        new java.util.IdentityHashMap<>();
                    for(com.erp.system.domain.HrOnboarding item:call.<List<com.erp.system.domain.HrOnboarding>>getArgument(0))
                        map.put(item,Collections.emptyList());return map;});
        Map<String,Object> defaults = new LinkedHashMap<>();
        defaults.put("employeeCategory", Collections.singletonList(option("正式", "FORMAL")));
        Map<String,Object> options = new LinkedHashMap<>(); options.put("dictionaryDefaults", defaults);
        org.mockito.Mockito.when(configs.options()).thenReturn(options);
        return new ValidatorFixture(access, configs, conflicts,
                new HrOnboardingImportPreviewValidator(access, posts, configs, conflicts, () -> 7L));
    }

    private static com.erp.system.domain.HrOnboarding validRow()
    {
        com.erp.system.domain.HrOnboarding row = new com.erp.system.domain.HrOnboarding();
        row.setEmployeeName("张三"); row.setPhoneNumber("13800138000");
        row.setExpectedEntryDate(java.sql.Date.valueOf("2026-07-20"));
        row.setEmployeeCategory("FORMAL"); row.setCompanyName("华东公司"); row.setPositionName("店员");
        return row;
    }

    private static Map<String,Object> option(String label,String value)
    {
        Map<String,Object> option = new LinkedHashMap<>(); option.put("label",label); option.put("value",value);
        return option;
    }

    private static Map<String,Object> dictionaryOptions()
    {
        Map<String,Object> defaults=new LinkedHashMap<>();
        defaults.put("employeeCategory",Collections.singletonList(option("正式","FORMAL")));
        Map<String,Object> options=new LinkedHashMap<>();options.put("dictionaryDefaults",defaults);return options;
    }

    private static SysDept dept(Long id,Long parent,String name,String type,String ancestors)
    {
        SysDept dept = new SysDept(); dept.setDeptId(id); dept.setParentId(parent); dept.setDeptName(name);
        dept.setDeptType(type); dept.setAncestors(ancestors); dept.setStatus("0"); return dept;
    }

    private static final class ValidatorFixture
    {
        private final HrOnboardingAccessService access;
        private final IHrOnboardingPositionConfigService configs;
        private final HrOnboardingConflictService conflicts;
        private final HrOnboardingImportPreviewValidator validator;
        private ValidatorFixture(HrOnboardingAccessService access, IHrOnboardingPositionConfigService configs,
                HrOnboardingConflictService conflicts,HrOnboardingImportPreviewValidator validator)
        { this.access=access; this.configs=configs; this.conflicts=conflicts; this.validator=validator; }
    }

    private static int find(Row row, String text)
    {
        for (int i = 0; i < row.getLastCellNum(); i++) if (text.equals(row.getCell(i).getStringCellValue())) return i;
        throw new AssertionError(text);
    }

    private static byte[] workbook(String[] headers, String[]... rows) throws Exception
    {
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream output = new ByteArrayOutputStream())
        {
            Sheet sheet = workbook.createSheet("导入");
            Row header = sheet.createRow(0);
            for (int i = 0; i < headers.length; i++) header.createCell(i, CellType.STRING).setCellValue(headers[i]);
            for (int r = 0; r < rows.length; r++)
            {
                Row row = sheet.createRow(r + 1);
                for (int c = 0; c < rows[r].length; c++)
                    row.createCell(c, CellType.STRING).setCellValue(rows[r][c]);
            }
            workbook.write(output);
            return output.toByteArray();
        }
    }
}
