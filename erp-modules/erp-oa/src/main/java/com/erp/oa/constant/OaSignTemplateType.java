package com.erp.oa.constant;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.erp.common.core.exception.ServiceException;
import com.erp.common.core.utils.StringUtils;

public final class OaSignTemplateType
{
    public static final String ONBOARD_LABOR_CONTRACT = "ONBOARD_LABOR_CONTRACT";
    public static final String ONBOARD_SERVICE_CONTRACT = "ONBOARD_SERVICE_CONTRACT";
    public static final String ONBOARD_SERVICE_RECEIPT = "ONBOARD_SERVICE_RECEIPT";
    public static final String ONBOARD_COMMITMENT = "ONBOARD_COMMITMENT";
    public static final String ONBOARD_POST_DUTY = "ONBOARD_POST_DUTY";
    public static final String ONBOARD_HANDBOOK_RECEIPT = "ONBOARD_HANDBOOK_RECEIPT";
    public static final String ONBOARD_HANDBOOK = "ONBOARD_HANDBOOK";
    public static final String ONBOARD_SALARY_CONFIRM = "ONBOARD_SALARY_CONFIRM";
    public static final String ONBOARD_APPLICATION_FORM = "ONBOARD_APPLICATION_FORM";
    public static final String ONBOARD_BACKGROUND_CHECK = "ONBOARD_BACKGROUND_CHECK";
    public static final String ONBOARD_ARCHIVE_CATALOG = "ONBOARD_ARCHIVE_CATALOG";
    public static final String ONBOARD_OFFER_NOTICE = "ONBOARD_OFFER_NOTICE";
    public static final String ONBOARD_CONFIDENTIAL_NONCOMPETE = "ONBOARD_CONFIDENTIAL_NONCOMPETE";
    public static final String ONBOARD_MINOR_NONSTUDENT_DECLARATION =
            "ONBOARD_MINOR_NONSTUDENT_DECLARATION";
    public static final String REGULARIZE_CONFIRMATION = "REGULARIZE_CONFIRMATION";
    public static final String REGULARIZE_POST_DUTY = "REGULARIZE_POST_DUTY";
    public static final String REGULARIZE_SALARY_CONFIRM = "REGULARIZE_SALARY_CONFIRM";
    public static final String TRANSFER_CONFIRMATION = "TRANSFER_CONFIRMATION";
    public static final String TRANSFER_POST_DUTY = "TRANSFER_POST_DUTY";
    public static final String TRANSFER_SALARY_CONFIRM = "TRANSFER_SALARY_CONFIRM";
    public static final String RENEWAL_LABOR_CONTRACT = "RENEWAL_LABOR_CONTRACT";
    public static final String RENEWAL_SERVICE_CONTRACT = "RENEWAL_SERVICE_CONTRACT";
    public static final String RENEWAL_SALARY_CONFIRM = "RENEWAL_SALARY_CONFIRM";
    public static final String OFFBOARD_CONFIRMATION = "OFFBOARD_CONFIRMATION";
    public static final String OFFBOARD_HANDOVER = "OFFBOARD_HANDOVER";
    public static final String OFFBOARD_SETTLEMENT = "OFFBOARD_SETTLEMENT";
    public static final String OFFBOARD_CONFIDENTIALITY_NONCOMPETE = "OFFBOARD_CONFIDENTIALITY_NONCOMPETE";
    public static final String OFFBOARD_TERMINATION_NOTICE = "OFFBOARD_TERMINATION_NOTICE";
    public static final String OFFBOARD_LEAVE_CERTIFICATE = "OFFBOARD_LEAVE_CERTIFICATE";

    private static final List<String> CONTRACT_BASE = placeholders(
            "employeeName", "employeeIdCard", "employeePhone", "employeeAddress",
            "companyName", "companyAddress", "companyLegalRepresentative",
            "contractTermSelection", "contractTermFixedMark", "contractTermOpenEndedMark",
            "contractStartDate", "contractEndDate", "probationStartDate", "probationEndDate",
            "postName", "baseSalary", "signDate");

    private static final Map<String, Option> OPTIONS = new LinkedHashMap<>();

    static
    {
        register(ONBOARD_LABOR_CONTRACT, "劳动合同", "onboard", "docx", true, CONTRACT_BASE);
        register(ONBOARD_SERVICE_CONTRACT, "劳务合同", "onboard", "docx", true,
                placeholders("employeeName", "employeeIdCard", "employeePhone", "employeeAddress",
                        "companyName", "companyAddress", "companyLegalRepresentative",
                        "servicePersonType", "contractStartDate", "contractEndDate", "postName",
                        "baseSalary", "signDate"));
        register(ONBOARD_SERVICE_RECEIPT, "劳务合同签收单", "onboard", "docx", true,
                placeholders("employeeName", "employeeIdCard", "servicePersonType", "baseSalary",
                        "postSalary", "fieldAllowance", "performanceSalary", "salaryTotal",
                        "insuranceType", "signDate"));
        register(ONBOARD_COMMITMENT, "入职承诺书", "onboard", "docx", true,
                placeholders("employeeName", "employeeIdCard", "signDate"));
        register(ONBOARD_POST_DUTY, "岗位职责确认书", "onboard", "docx", true,
                placeholders("employeeName", "postName", "postLevel", "signDate"));
        register(ONBOARD_HANDBOOK_RECEIPT, "员工手册签收确认书", "onboard", "docx", true,
                placeholders("employeeName", "employeeIdCard", "signDate"));
        register(ONBOARD_HANDBOOK, "员工手册", "onboard", "docx", true, true, false,
                placeholders());
        register(ONBOARD_SALARY_CONFIRM, "薪酬结构确认书", "onboard", "docx", true,
                placeholders("employeeName", "employeeIdCard", "baseSalary", "postSalary",
                        "fieldAllowance", "performanceSalary", "salaryTotal", "signDate"));
        register(ONBOARD_APPLICATION_FORM, "应聘登记表", "onboard", "xlsx", false, false, false,
                placeholders("employeeName", "employeeIdCard", "employeePhone", "entryDate", "postName"));
        register(ONBOARD_BACKGROUND_CHECK, "背调报告", "onboard", "docx", false, false, false,
                placeholders("employeeName", "postName"));
        register(ONBOARD_ARCHIVE_CATALOG, "员工档案目录", "onboard", "docx", false, false, false,
                placeholders("employeeName", "employeeDeptName", "entryDate", "postName"));
        register(ONBOARD_OFFER_NOTICE, "录用通知书", "onboard", "docx", true,
                placeholders("employeeName", "employeeIdCard", "employeePhone", "employeeAddress",
                        "employeeDeptName", "postName", "entryDate", "probationStartDate",
                        "probationEndDate", "baseSalary", "postSalary", "fieldAllowance",
                        "performanceSalary", "salaryTotal", "signDate"));
        register(ONBOARD_CONFIDENTIAL_NONCOMPETE, "保密与竞业限制协议", "onboard", "docx", true,
                placeholders("employeeName", "employeeIdCard", "employeePhone", "employeeAddress",
                        "postName", "postLevel", "contractStartDate", "signDate"));
        register(ONBOARD_MINOR_NONSTUDENT_DECLARATION, "非在校生及未成年工入职声明书",
                "onboard", "docx", true,
                placeholders("employeeName", "employeeIdCard", "incomeStartYearMonth", "signDate"));
        register(REGULARIZE_CONFIRMATION, "转正确认书", "regularize", "docx", true,
                placeholders("employeeName", "employeeIdCard", "actualRegularizationDate",
                        "postName", "postLevel", "signDate"));
        register(REGULARIZE_POST_DUTY, "转正岗位职责确认书", "regularize", "docx", true,
                placeholders("employeeName", "postName", "postLevel",
                        "actualRegularizationDate", "signDate"));
        register(REGULARIZE_SALARY_CONFIRM, "转正薪资确认书", "regularize", "docx", true,
                placeholders("employeeName", "employeeIdCard", "actualRegularizationDate",
                        "baseSalary", "postSalary", "fieldAllowance", "performanceSalary",
                        "salaryTotal", "salaryVersion", "signDate"));
        register(TRANSFER_CONFIRMATION, "调岗确认书", "transfer", "docx", true,
                placeholders("employeeName", "employeeIdCard", "transferEffectiveDate",
                        "beforeDeptName", "afterDeptName", "beforePostName", "afterPostName",
                        "signDate"));
        register(TRANSFER_POST_DUTY, "调岗岗位职责确认书", "transfer", "docx", true,
                placeholders("employeeName", "afterDeptName", "afterPostName", "postLevel",
                        "transferEffectiveDate", "signDate"));
        register(TRANSFER_SALARY_CONFIRM, "调岗薪资确认书", "transfer", "docx", true,
                placeholders("employeeName", "employeeIdCard", "transferEffectiveDate",
                        "baseSalary", "postSalary", "fieldAllowance", "performanceSalary",
                        "salaryTotal", "salaryVersion", "signDate"));
        register(RENEWAL_LABOR_CONTRACT, "劳动合同续签协议", "renewal", "docx", true,
                placeholders("employeeName", "employeeIdCard", "employeePhone", "employeeAddress",
                        "companyName", "previousContractEndDate", "previousEmploymentType",
                        "previousRenewalCount", "renewalCount", "contractStartDate", "contractEndDate",
                        "postName", "baseSalary", "signDate"));
        register(RENEWAL_SERVICE_CONTRACT, "劳务协议续签书", "renewal", "docx", true,
                placeholders("employeeName", "employeeIdCard", "employeePhone", "employeeAddress",
                        "companyName", "previousContractEndDate", "previousEmploymentType",
                        "previousRenewalCount", "renewalCount", "servicePersonType",
                        "contractStartDate", "contractEndDate", "postName", "baseSalary", "signDate"));
        register(RENEWAL_SALARY_CONFIRM, "续签薪酬确认书", "renewal", "docx", true,
                placeholders("employeeName", "employeeIdCard", "companyName",
                        "previousRenewalCount", "renewalCount", "contractStartDate",
                        "contractEndDate", "baseSalary", "postSalary", "fieldAllowance",
                        "performanceSalary", "salaryTotal", "salaryVersion", "signDate"));
        register(OFFBOARD_CONFIRMATION, "离职确认书", "offboard", "docx", true,
                placeholders("employeeName", "employeeIdCard", "entryDate", "leaveDate",
                        "offboardingType", "leaveReason", "signDate"));
        register(OFFBOARD_HANDOVER, "离职交接确认书", "offboard", "docx", true,
                placeholders("employeeName", "leaveDate", "postName",
                        "assetHandoverStatus", "signDate"));
        register(OFFBOARD_SETTLEMENT, "离职结算确认书", "offboard", "docx", true,
                placeholders("employeeName", "employeeIdCard", "leaveDate",
                        "salarySettlementStatus", "compensationAmount",
                        "compensationNote", "signDate"));
        register(OFFBOARD_CONFIDENTIALITY_NONCOMPETE, "离职保密与竞业确认书",
                "offboard", "docx", true,
                placeholders("employeeName", "employeeIdCard", "leaveDate",
                        "nonCompeteDecision", "signDate"));
        register(OFFBOARD_TERMINATION_NOTICE, "解除终止通知书", "offboard", "docx", false,
                placeholders("employeeName", "employeeIdCard", "entryDate", "leaveDate",
                        "offboardingType", "leaveReason", "companyName", "signDate"));
        register(OFFBOARD_LEAVE_CERTIFICATE, "离职证明", "offboard", "docx", false,
                placeholders("employeeName", "employeeIdCard", "entryDate", "leaveDate",
                        "postName", "companyName", "signDate"));
    }

    private OaSignTemplateType()
    {
    }

    public static Option require(String code)
    {
        if (StringUtils.isBlank(code) || !OPTIONS.containsKey(code))
        {
            throw new ServiceException("未知模板类型不能上传");
        }
        return OPTIONS.get(code);
    }

    public static List<Option> options()
    {
        return Collections.unmodifiableList(new ArrayList<>(OPTIONS.values()));
    }

    public static List<String> requiredPlaceholders(String code)
    {
        return require(code).getRequiredPlaceholders();
    }

    private static void register(String code, String label, String scenario, String fileFormat,
            boolean employeeSignRequired, List<String> requiredPlaceholders)
    {
        register(code, label, scenario, fileFormat, true, employeeSignRequired, employeeSignRequired,
                requiredPlaceholders);
    }

    private static void register(String code, String label, String scenario, String fileFormat,
            boolean employeeVisible, boolean readConfirmationRequired, boolean employeeSignRequired,
            List<String> requiredPlaceholders)
    {
        OPTIONS.put(code, new Option(code, label, scenario, fileFormat, employeeVisible,
                readConfirmationRequired, employeeSignRequired, requiredPlaceholders));
    }

    private static List<String> placeholders(String... names)
    {
        return Collections.unmodifiableList(Arrays.asList(names));
    }

    public static class Option
    {
        private final String code;
        private final String label;
        private final String scenario;
        private final String fileFormat;
        private final boolean employeeVisible;
        private final boolean readConfirmationRequired;
        private final boolean employeeSignRequired;
        private final List<String> requiredPlaceholders;

        Option(String code, String label, String scenario, String fileFormat, boolean employeeVisible,
                boolean readConfirmationRequired, boolean employeeSignRequired, List<String> requiredPlaceholders)
        {
            this.code = code;
            this.label = label;
            this.scenario = scenario;
            this.fileFormat = fileFormat;
            this.employeeVisible = employeeVisible;
            this.readConfirmationRequired = readConfirmationRequired;
            this.employeeSignRequired = employeeSignRequired;
            this.requiredPlaceholders = requiredPlaceholders;
        }

        public String getCode() { return code; }
        public String getLabel() { return label; }
        public String getScenario() { return scenario; }
        public String getFileFormat() { return fileFormat; }
        public boolean isEmployeeVisible() { return employeeVisible; }
        public boolean isReadConfirmationRequired() { return readConfirmationRequired; }
        public boolean isEmployeeSignRequired() { return employeeSignRequired; }
        public List<String> getRequiredPlaceholders() { return requiredPlaceholders; }
    }
}
