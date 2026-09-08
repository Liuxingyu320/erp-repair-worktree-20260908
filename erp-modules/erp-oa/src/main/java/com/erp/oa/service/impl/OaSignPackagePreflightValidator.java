package com.erp.oa.service.impl;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import com.erp.common.core.exception.ServiceException;
import com.erp.common.core.utils.StringUtils;
import com.erp.oa.constant.OaOnboardSalaryVersionPolicy;
import com.erp.oa.constant.OaSignScenarioCodes;
import com.erp.oa.constant.OaSignTemplateType;
import com.erp.oa.domain.OaSignPackage;
import com.erp.oa.domain.OaSignTemplate;
import com.erp.system.api.constant.SigningProfileCodes;

/** 替换已生成文件前执行的服务端校验。 */
final class OaSignPackagePreflightValidator
{
    private static final String LABOR = "劳动合同";
    private static final String SERVICE = "劳务合同";
    private static final String SOCIAL_INSURED = "SOCIAL_INSURED";
    private static final String SOCIAL_UNINSURED = "SOCIAL_UNINSURED";
    private static final String EXACT_V7_VERSION = "20260721-v7";
    private static final String EXACT_V7_SOURCE_HASH =
            "1226728ec0e703d513efda83dc5578ee4cdd65cb3e7d0e7cb5afee813f466558";

    private OaSignPackagePreflightValidator()
    {
    }

    static void validate(OaSignPackage signPackage, List<OaSignTemplate> templates)
    {
        validateTemplateScenarios(signPackage, templates);
        validateTemplateCombination(signPackage, templates);
        validateContractTerm(signPackage, templates);
        validateFrozenCompany(signPackage, templates);
        validateRequiredPlaceholders(signPackage, templates);
        validateDateOrder(signPackage);
        validateSalaryTotal(signPackage);
    }

    private static void validateTemplateScenarios(OaSignPackage signPackage,
            List<OaSignTemplate> templates)
    {
        String packageScenario = OaSignScenarioCodes.normalizePackageScenario(
                signPackage == null ? null : signPackage.getScenario());
        if (!OaSignScenarioCodes.isSupported(packageScenario))
        {
            throw new ServiceException("签约场景不受支持");
        }
        if (templates == null || templates.isEmpty())
        {
            throw new ServiceException("签约包未匹配到可用模板");
        }
        for (OaSignTemplate template : templates)
        {
            if (template == null || StringUtils.isBlank(template.getTemplateType()))
            {
                throw new ServiceException("签约包包含未登记模板类型");
            }
            OaSignTemplateType.Option option;
            try
            {
                option = OaSignTemplateType.require(
                        template.getTemplateType().trim().toUpperCase(java.util.Locale.ROOT));
            }
            catch (ServiceException ignored)
            {
                throw new ServiceException("签约包包含未登记模板类型："
                        + template.getTemplateType());
            }
            String templateScenario = OaSignScenarioCodes.normalizePackageScenario(option.getScenario());
            if (!packageScenario.equals(templateScenario))
            {
                throw new ServiceException(scenarioLabel(packageScenario) + "签约包只能使用"
                        + scenarioLabel(packageScenario) + "场景模板");
            }
            validateDeliveryPolicy(template, option);
        }
    }

    private static void validateDeliveryPolicy(OaSignTemplate template,
            OaSignTemplateType.Option option)
    {
        if (!option.isEmployeeVisible()
                && ("Y".equalsIgnoreCase(template.getEmployeeVisible())
                    || "Y".equalsIgnoreCase(template.getReadConfirmationRequired())
                    || "Y".equalsIgnoreCase(template.getEmployeeSignRequired())))
        {
            throw new ServiceException(option.getLabel() + "属于人力资源内部材料，不能发送到员工端");
        }
        if ("Y".equalsIgnoreCase(template.getEmployeeSignRequired())
                && "N".equalsIgnoreCase(template.getReadConfirmationRequired()))
        {
            throw new ServiceException(option.getLabel() + "要求签署时必须先要求阅读确认");
        }
    }

    private static void validateTemplateCombination(OaSignPackage signPackage, List<OaSignTemplate> templates)
    {
        String scenario = OaSignScenarioCodes.normalizeTaskScenario(signPackage.getScenario());
        if (OaSignScenarioCodes.RENEWAL.equals(scenario))
        {
            validateRenewalTemplateCombination(signPackage, templates);
            return;
        }
        if (!OaSignScenarioCodes.ONBOARD.equals(scenario))
        {
            return;
        }
        validateReviewedExactV7DocumentSet(templates);
        Set<String> types = new LinkedHashSet<>();
        for (OaSignTemplate template : templates)
        {
            if (template != null && StringUtils.isNotBlank(template.getTemplateType()))
            {
                types.add(template.getTemplateType().trim().toUpperCase(java.util.Locale.ROOT));
            }
        }
        boolean laborContract = types.contains(OaSignTemplateType.ONBOARD_LABOR_CONTRACT);
        boolean serviceContract = types.contains(OaSignTemplateType.ONBOARD_SERVICE_CONTRACT);
        boolean serviceReceipt = types.contains(OaSignTemplateType.ONBOARD_SERVICE_RECEIPT);
        boolean offerNotice = types.contains(OaSignTemplateType.ONBOARD_OFFER_NOTICE);
        boolean handbook = types.contains(OaSignTemplateType.ONBOARD_HANDBOOK);
        boolean postDuty = types.contains(OaSignTemplateType.ONBOARD_POST_DUTY);
        boolean handbookReceipt = types.contains(OaSignTemplateType.ONBOARD_HANDBOOK_RECEIPT);
        boolean salaryConfirm = types.contains(OaSignTemplateType.ONBOARD_SALARY_CONFIRM);
        String socialType = socialCategory(signPackage.getSocialType());
        if (socialType == null)
        {
            throw new ServiceException("当前入职社保类型暂不支持自动生成合同");
        }
        if (laborContract && (serviceContract || serviceReceipt))
        {
            throw new ServiceException("入职签约包不能同时包含劳动合同和劳务合同材料");
        }
        if (isLaborEmployment(signPackage.getEmploymentType()) && (serviceContract || serviceReceipt))
        {
            throw new ServiceException("当前用工类型为劳动合同，不能发送劳务合同或劳务签收单");
        }
        if (isLaborEmployment(signPackage.getEmploymentType()) && salaryConfirm)
        {
            validateOnboardSalaryVersion(signPackage, templates);
        }
        if (isServiceEmployment(signPackage.getEmploymentType()))
        {
            if (!SOCIAL_UNINSURED.equals(socialType))
            {
                throw new ServiceException("劳务合同仅支持无社保口径自动生成");
            }
            if (laborContract || offerNotice || handbook || postDuty
                    || handbookReceipt || salaryConfirm)
            {
                throw new ServiceException("当前用工类型为劳务合同，不能发送劳动关系专用的录用、手册、岗位或薪酬材料");
            }
            if (serviceContract != serviceReceipt)
            {
                throw new ServiceException("劳务合同与劳务合同签收单必须成套发送");
            }
        }
        if (!isLaborEmployment(signPackage.getEmploymentType())
                && !isServiceEmployment(signPackage.getEmploymentType()))
        {
            throw new ServiceException("当前入职用工类型暂不支持自动生成合同");
        }
    }

    private static void validateReviewedExactV7DocumentSet(List<OaSignTemplate> templates)
    {
        boolean exactV7Labor = templates.stream().filter(Objects::nonNull)
                .anyMatch(template -> OaSignTemplateType.ONBOARD_LABOR_CONTRACT
                        .equalsIgnoreCase(StringUtils.trim(template.getTemplateType()))
                        && EXACT_V7_VERSION.equals(StringUtils.trim(
                                template.getTemplateVersion()))
                        && EXACT_V7_SOURCE_HASH.equalsIgnoreCase(StringUtils.trim(
                                template.getFileHash())));
        if (!exactV7Labor)
        {
            return;
        }
        Map<String, Long> totalCounts = templates.stream().filter(Objects::nonNull)
                .map(OaSignTemplate::getTemplateType)
                .filter(StringUtils::isNotBlank)
                .map(value -> value.trim().toUpperCase(java.util.Locale.ROOT))
                .collect(java.util.stream.Collectors.groupingBy(
                        value -> value, LinkedHashMap::new,
                        java.util.stream.Collectors.counting()));
        Map<String, Long> visibleCounts = templates.stream().filter(Objects::nonNull)
                .filter(template -> {
                    OaSignTemplateType.Option option = OaSignTemplateType.require(
                            template.getTemplateType().trim().toUpperCase(
                                    java.util.Locale.ROOT));
                    return option.isEmployeeVisible()
                            && !"N".equalsIgnoreCase(StringUtils.trim(
                                    template.getEmployeeVisible()));
                })
                .map(template -> template.getTemplateType().trim()
                        .toUpperCase(java.util.Locale.ROOT))
                .collect(java.util.stream.Collectors.groupingBy(
                        value -> value, LinkedHashMap::new,
                        java.util.stream.Collectors.counting()));
        Set<String> allowed = Set.of(
                OaSignTemplateType.ONBOARD_COMMITMENT,
                OaSignTemplateType.ONBOARD_LABOR_CONTRACT,
                OaSignTemplateType.ONBOARD_HANDBOOK,
                OaSignTemplateType.ONBOARD_HANDBOOK_RECEIPT,
                OaSignTemplateType.ONBOARD_SALARY_CONFIRM,
                OaSignTemplateType.ONBOARD_CONFIDENTIAL_NONCOMPETE);
        long handbookTotal = totalCounts.getOrDefault(OaSignTemplateType.ONBOARD_HANDBOOK, 0L)
                + totalCounts.getOrDefault(
                        OaSignTemplateType.ONBOARD_HANDBOOK_RECEIPT, 0L);
        long handbookVisible = visibleCounts.getOrDefault(
                OaSignTemplateType.ONBOARD_HANDBOOK, 0L)
                + visibleCounts.getOrDefault(
                        OaSignTemplateType.ONBOARD_HANDBOOK_RECEIPT, 0L);
        boolean complete = totalCounts.keySet().stream().allMatch(allowed::contains)
                && exactVisibleCount(totalCounts, visibleCounts,
                        OaSignTemplateType.ONBOARD_COMMITMENT)
                && exactVisibleCount(totalCounts, visibleCounts,
                        OaSignTemplateType.ONBOARD_LABOR_CONTRACT)
                && handbookTotal == 1L && handbookVisible == 1L
                && exactVisibleCount(totalCounts, visibleCounts,
                        OaSignTemplateType.ONBOARD_SALARY_CONFIRM)
                && totalCounts.getOrDefault(
                        OaSignTemplateType.ONBOARD_CONFIDENTIAL_NONCOMPETE, 0L) <= 1L
                && totalCounts.getOrDefault(
                        OaSignTemplateType.ONBOARD_CONFIDENTIAL_NONCOMPETE, 0L)
                        == visibleCounts.getOrDefault(
                                OaSignTemplateType.ONBOARD_CONFIDENTIAL_NONCOMPETE, 0L);
        if (!complete)
        {
            throw new ServiceException("exact-v7劳动合同必须使用审定的员工可见文档集合");
        }
    }

    private static boolean exactVisibleCount(Map<String, Long> totalCounts,
            Map<String, Long> visibleCounts, String templateType)
    {
        return totalCounts.getOrDefault(templateType, 0L) == 1L
                && visibleCounts.getOrDefault(templateType, 0L) == 1L;
    }

    private static String socialCategory(String value)
    {
        return OaOnboardSalaryVersionPolicy.normalizeSocialType(value);
    }

    private static void validateContractTerm(OaSignPackage signPackage,
            List<OaSignTemplate> templates)
    {
        if (!hasTemplateType(templates, OaSignTemplateType.ONBOARD_LABOR_CONTRACT))
        {
            return;
        }
        String code = contractTermCode(signPackage);
        if (code == null)
        {
            throw new ServiceException("签约资料不完整：合同期限类型（影响：劳动合同）");
        }
        if (!SigningProfileCodes.CONTRACT_TERMS.contains(code))
        {
            throw new ServiceException("合同期限类型仅支持固定期限或无固定期限");
        }
    }

    private static boolean hasTemplateType(List<OaSignTemplate> templates, String expected)
    {
        return templates != null && templates.stream()
                .filter(Objects::nonNull)
                .map(OaSignTemplate::getTemplateType)
                .filter(StringUtils::isNotBlank)
                .anyMatch(value -> expected.equalsIgnoreCase(value.trim()));
    }

    private static String contractTermCode(OaSignPackage signPackage)
    {
        String value = signPackage == null ? null : signPackage.getContractTermCodeSnapshot();
        return StringUtils.isBlank(value) ? null
                : value.trim().toUpperCase(java.util.Locale.ROOT);
    }

    /** A company-pending fact sheet has no entity snapshot; once frozen, all facts fail closed. */
    private static void validateFrozenCompany(OaSignPackage signPackage,
            List<OaSignTemplate> templates)
    {
        if (signPackage == null || signPackage.getLegalEntityIdSnapshot() == null) return;
        LinkedHashSet<String> missing = new LinkedHashSet<>();
        if (StringUtils.isBlank(signPackage.getLegalEntityCodeSnapshot())) missing.add("公司编码");
        if (StringUtils.isBlank(signPackage.getLegalEntityNameSnapshot())
                || signPackage.getLegalEntityNameSnapshot().trim().matches("[0-9]+"))
            missing.add("公司法定全称");
        if (StringUtils.isBlank(signPackage.getLegalEntityCreditCodeSnapshot()))
            missing.add("统一社会信用代码");
        if (StringUtils.isBlank(signPackage.getLegalEntityAddressSnapshot())) missing.add("注册地址");
        if (StringUtils.isBlank(signPackage.getLegalRepresentativeSnapshot())) missing.add("法定代表人");
        if (!missing.isEmpty())
            throw new ServiceException("公司主数据快照不完整：" + String.join("、", missing));
        boolean sealRequired = templates.stream().filter(Objects::nonNull)
                .anyMatch(value -> "Y".equalsIgnoreCase(value.getCompanySealRequired()));
        if (sealRequired && (signPackage.getSealIdSnapshot() == null
                || StringUtils.isBlank(signPackage.getSealImageUrlSnapshot())
                || StringUtils.isBlank(signPackage.getSealImageHashSnapshot())
                || !signPackage.getSealImageHashSnapshot().matches("(?i)[0-9a-f]{64}")))
            throw new ServiceException("公司印章快照不完整或校验值无效");
    }

    private static void validateOnboardSalaryVersion(OaSignPackage signPackage,
            List<OaSignTemplate> templates)
    {
        String required = OaOnboardSalaryVersionPolicy.requiredSalaryVersion(
                signPackage.getSocialType());
        if (required == null)
        {
            throw new ServiceException("入职劳动合同的社保类型无法唯一确定薪酬版本");
        }
        String selected = OaOnboardSalaryVersionPolicy.normalizeSalaryVersion(
                signPackage.getSalaryVersion());
        if (selected == null)
        {
            throw new ServiceException("入职劳动合同签约包未按社保类型冻结薪酬版本" + required);
        }
        if (!required.equals(selected))
        {
            throw new ServiceException("当前社保类型必须匹配薪酬结构确认书（"
                    + required + "版），不允许手工切换为" + selected + "版");
        }
        List<OaSignTemplate> salaryTemplates = templates.stream()
                .filter(template -> template != null
                        && OaSignTemplateType.ONBOARD_SALARY_CONFIRM.equalsIgnoreCase(
                                StringUtils.trim(template.getTemplateType())))
                .toList();
        if (salaryTemplates.size() != 1)
        {
            throw new ServiceException("入职劳动合同签约包必须且只能包含一份薪酬结构确认书");
        }
        String templateVersion = OaOnboardSalaryVersionPolicy.normalizeSalaryVersion(
                salaryTemplates.get(0).getSalaryVersion());
        if (!required.equals(templateVersion))
        {
            throw new ServiceException("当前社保类型应使用" + required
                    + "版，实际匹配的薪酬结构确认书版本不一致");
        }
    }

    private static void validateRenewalTemplateCombination(OaSignPackage signPackage,
            List<OaSignTemplate> templates)
    {
        Set<String> types = new LinkedHashSet<>();
        for (OaSignTemplate template : templates)
        {
            if (template != null && StringUtils.isNotBlank(template.getTemplateType()))
            {
                types.add(template.getTemplateType().trim().toUpperCase(java.util.Locale.ROOT));
            }
        }
        boolean laborContract = types.contains(OaSignTemplateType.RENEWAL_LABOR_CONTRACT);
        boolean serviceContract = types.contains(OaSignTemplateType.RENEWAL_SERVICE_CONTRACT);
        if (!laborContract && !serviceContract)
        {
            throw new ServiceException("续签签约包必须包含续签劳动合同或续签劳务协议");
        }
        if (laborContract && serviceContract)
        {
            throw new ServiceException("续签签约包不能同时包含续签劳动合同和续签劳务协议");
        }
        if (isLaborEmployment(signPackage.getEmploymentType()) && serviceContract)
        {
            throw new ServiceException("当前用工类型为劳动合同，不能发送续签劳务协议");
        }
        if (isServiceEmployment(signPackage.getEmploymentType()) && laborContract)
        {
            throw new ServiceException("当前用工类型为劳务合同，不能发送续签劳动合同");
        }
        if (!isLaborEmployment(signPackage.getEmploymentType())
                && !isServiceEmployment(signPackage.getEmploymentType()))
        {
            throw new ServiceException("当前续签用工类型暂不支持自动生成合同");
        }
        validateRenewalSnapshot(signPackage);
    }

    private static void validateRenewalSnapshot(OaSignPackage signPackage)
    {
        LocalDate previousEnd = date(signPackage.getPreviousContractEndDate(), "原合同结束日期");
        LocalDate currentStart = date(signPackage.getContractStartDate(), "新合同开始日期");
        if (previousEnd == null)
        {
            throw new ServiceException("续签资料不完整：原合同结束日期不能为空");
        }
        if (currentStart == null)
        {
            throw new ServiceException("续签资料不完整：新合同开始日期不能为空");
        }
        if (!currentStart.isAfter(previousEnd))
        {
            throw new ServiceException("新合同开始日期必须晚于原合同结束日期");
        }
        Integer previousCount = signPackage.getPreviousRenewalCount();
        Integer currentCount = signPackage.getRenewalCount();
        if (previousCount == null || previousCount < 0 || currentCount == null
                || currentCount != previousCount + 1)
        {
            throw new ServiceException("续签次数必须在原次数基础上加1");
        }
        if (signPackage.getPreviousLegalEntityIdSnapshot() == null)
        {
            throw new ServiceException("续签资料不完整：原合同公司快照不能为空");
        }
        String previousEmployment = employmentCategory(signPackage.getPreviousEmploymentType());
        String currentEmployment = employmentCategory(signPackage.getEmploymentType());
        if (previousEmployment == null)
        {
            throw new ServiceException("原合同类型不受支持，请转人工复核");
        }
        if (!previousEmployment.equals(currentEmployment))
        {
            throw new ServiceException("续签前后合同类型不一致，请转人工复核");
        }
    }

    private static String employmentCategory(String value)
    {
        if (isLaborEmployment(value))
        {
            return "LABOR";
        }
        if (isServiceEmployment(value))
        {
            return "SERVICE";
        }
        return null;
    }

    private static boolean isLaborEmployment(String value)
    {
        return LABOR.equals(value) || "LABOR_CONTRACT".equalsIgnoreCase(value);
    }

    private static boolean isServiceEmployment(String value)
    {
        return SERVICE.equals(value) || "SERVICE_CONTRACT".equalsIgnoreCase(value);
    }

    private static String scenarioLabel(String scenario)
    {
        return switch (OaSignScenarioCodes.normalizeTaskScenario(scenario))
        {
            case OaSignScenarioCodes.ONBOARD -> "入职";
            case OaSignScenarioCodes.RENEWAL -> "续签";
            case OaSignScenarioCodes.TRANSFER -> "调岗";
            case OaSignScenarioCodes.REGULARIZE -> "转正";
            case OaSignScenarioCodes.OFFBOARD -> "离职";
            default -> "签约";
        };
    }

    private static void validateRequiredPlaceholders(OaSignPackage signPackage, List<OaSignTemplate> templates)
    {
        Map<String, Object> values = placeholderValues(signPackage);
        Map<String, String> labels = placeholderLabels();
        Map<String, Set<String>> missingImpacts = new LinkedHashMap<>();
        List<String> unsupported = new ArrayList<>();
        for (OaSignTemplate template : templates)
        {
            if (template == null)
            {
                continue;
            }
            List<String> required = requiredPlaceholders(template);
            for (String placeholder : required)
            {
                if ("signDate".equals(placeholder))
                {
                    continue;
                }
                if (!values.containsKey(placeholder))
                {
                    unsupported.add(templateName(template) + "：" + placeholder);
                    continue;
                }
                if (isMissing(values.get(placeholder)))
                {
                    String label = labels.getOrDefault(placeholder, placeholder);
                    missingImpacts.computeIfAbsent(label, ignored -> new LinkedHashSet<>())
                            .add(templateName(template));
                }
            }
        }
        if (!unsupported.isEmpty())
        {
            throw new ServiceException("模板包含系统无法校验的必填占位符：" + String.join("、", unsupported));
        }
        if (!missingImpacts.isEmpty())
        {
            List<String> missing = new ArrayList<>();
            for (Map.Entry<String, Set<String>> entry : missingImpacts.entrySet())
            {
                missing.add(entry.getKey() + "（影响：" + String.join("、", entry.getValue()) + "）");
            }
            throw new ServiceException("签约资料不完整：" + String.join("；", missing));
        }
    }

    private static List<String> requiredPlaceholders(OaSignTemplate template)
    {
        String configured = template.getRequiredPlaceholders();
        if (StringUtils.isBlank(configured))
        {
            // Legacy rows may predate required_placeholders. Newly saved/imported templates
            // always carry the explicit list; do not make an old draft impossible to regenerate.
            return List.of();
        }
        List<String> required = new ArrayList<>();
        for (String item : configured.split(","))
        {
            String normalized = item == null ? null : item.trim();
            if (StringUtils.isNotBlank(normalized))
            {
                required.add(normalized);
            }
        }
        return required;
    }

    private static void validateDateOrder(OaSignPackage signPackage)
    {
        LocalDate contractStart = date(signPackage.getContractStartDate(), "合同开始日期");
        LocalDate contractEnd = date(signPackage.getContractEndDate(), "合同结束日期");
        LocalDate probationStart = date(signPackage.getProbationStartDate(), "试用期开始日期");
        LocalDate probationEnd = date(signPackage.getProbationEndDate(), "试用期结束日期");
        if (contractStart != null && contractEnd != null && contractStart.isAfter(contractEnd))
        {
            throw new ServiceException("合同开始日期不能晚于合同结束日期");
        }
        if (probationStart != null && probationEnd != null && probationStart.isAfter(probationEnd))
        {
            throw new ServiceException("试用期开始日期不能晚于试用期结束日期");
        }
        if (contractStart != null && probationStart != null && probationStart.isBefore(contractStart))
        {
            throw new ServiceException("试用期开始日期不能早于合同开始日期");
        }
        if (contractEnd != null && probationEnd != null && probationEnd.isAfter(contractEnd))
        {
            throw new ServiceException("试用期结束日期不能晚于合同结束日期");
        }
    }

    private static void validateSalaryTotal(OaSignPackage signPackage)
    {
        if (signPackage.getBaseSalary() == null || signPackage.getPostSalary() == null
                || signPackage.getFieldAllowance() == null || signPackage.getPerformanceSalary() == null
                || signPackage.getSalaryTotal() == null)
        {
            return;
        }
        BigDecimal calculated = signPackage.getBaseSalary().add(signPackage.getPostSalary())
                .add(signPackage.getFieldAllowance()).add(signPackage.getPerformanceSalary());
        if (calculated.compareTo(signPackage.getSalaryTotal()) != 0)
        {
            throw new ServiceException("薪资合计应等于基本工资、岗位工资、外勤补贴和绩效工资之和");
        }
    }

    private static LocalDate date(String value, String label)
    {
        if (StringUtils.isBlank(value))
        {
            return null;
        }
        try
        {
            return LocalDate.parse(value.trim());
        }
        catch (DateTimeParseException e)
        {
            throw new ServiceException(label + "格式必须为年-月-日，例如2026-07-14");
        }
    }

    private static boolean isMissing(Object value)
    {
        return value == null || (value instanceof String && StringUtils.isBlank((String) value));
    }

    private static String templateName(OaSignTemplate template)
    {
        return StringUtils.isNotBlank(template.getTemplateName())
                ? template.getTemplateName() : template.getTemplateType();
    }

    private static Map<String, Object> placeholderValues(OaSignPackage signPackage)
    {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("employeeName", signPackage.getEmployeeNameSnapshot());
        values.put("employeeIdCard", signPackage.getEmployeeIdCardSnapshot());
        values.put("employeePhone", signPackage.getEmployeePhoneSnapshot());
        values.put("employeeAddress", signPackage.getEmployeeAddressSnapshot());
        values.put("employeeDeptName", signPackage.getDeptNameSnapshot());
        values.put("postName", signPackage.getPostNameSnapshot());
        values.put("postLevel", signPackage.getPostLevelSnapshot());
        values.put("servicePersonType", signPackage.getServicePersonType());
        values.put("insuranceType", signPackage.getInsuranceType());
        values.put("entryDate", signPackage.getEntryDate());
        String contractTermCode = contractTermCode(signPackage);
        boolean fixedTerm = SigningProfileCodes.FIXED_TERM.equals(contractTermCode);
        boolean openEnded = SigningProfileCodes.OPEN_ENDED.equals(contractTermCode);
        values.put("contractTermSelection", fixedTerm ? "A" : openEnded ? "B" : null);
        values.put("contractTermFixedMark", fixedTerm ? "☑" : openEnded ? "□" : null);
        values.put("contractTermOpenEndedMark", openEnded ? "☑" : fixedTerm ? "□" : null);
        values.put("contractStartDate", signPackage.getContractStartDate());
        values.put("contractEndDate", signPackage.getContractEndDate());
        values.put("previousContractEndDate", signPackage.getPreviousContractEndDate());
        values.put("previousEmploymentType", signPackage.getPreviousEmploymentType());
        values.put("previousRenewalCount", signPackage.getPreviousRenewalCount());
        values.put("renewalCount", signPackage.getRenewalCount());
        values.put("probationStartDate", signPackage.getProbationStartDate());
        values.put("probationEndDate", signPackage.getProbationEndDate());
        values.put("actualRegularizationDate", signPackage.getActualRegularizationDate());
        values.put("transferEffectiveDate", signPackage.getTransferEffectiveDate());
        values.put("beforeDeptName", signPackage.getBeforeDeptNameSnapshot());
        values.put("afterDeptName", signPackage.getDeptNameSnapshot());
        values.put("beforePostName", signPackage.getBeforePostNameSnapshot());
        values.put("afterPostName", signPackage.getPostNameSnapshot());
        values.put("workStartDate", signPackage.getWorkStartDate());
        values.put("incomeStartYearMonth", signPackage.getIncomeStartYearMonth());
        values.put("workEndDate", signPackage.getWorkEndDate());
        values.put("leaveDate", signPackage.getLeaveDate());
        values.put("leaveReason", signPackage.getLeaveReason());
        values.put("offboardingType", signPackage.getOffboardingType());
        values.put("salarySettlementStatus", signPackage.getSalarySettlementStatus());
        values.put("assetHandoverStatus", signPackage.getAssetHandoverStatus());
        values.put("nonCompeteDecision", signPackage.getNonCompeteDecision());
        values.put("compensationAmount", signPackage.getCompensationAmount());
        values.put("compensationNote", signPackage.getCompensationNote());
        values.put("baseSalary", signPackage.getBaseSalary());
        values.put("postSalary", signPackage.getPostSalary());
        values.put("fieldAllowance", signPackage.getFieldAllowance());
        values.put("performanceSalary", signPackage.getPerformanceSalary());
        values.put("salaryTotal", signPackage.getSalaryTotal());
        values.put("salaryVersion", signPackage.getSalaryVersion());
        values.put("companyName", firstNonBlank(signPackage.getLegalEntityNameSnapshot(),
                signPackage.getRecommendedCompanySnapshot()));
        values.put("companyCode", signPackage.getLegalEntityCodeSnapshot());
        values.put("companyCreditCode", signPackage.getLegalEntityCreditCodeSnapshot());
        values.put("companyAddress", firstNonBlank(signPackage.getLegalEntityAddressSnapshot(),
                signPackage.getRecommendedRegisteredAddressSnapshot()));
        values.put("companyLegalRepresentative", firstNonBlank(
                signPackage.getLegalRepresentativeSnapshot(),
                signPackage.getRecommendedLegalRepresentativeSnapshot()));
        values.put("companyPhone", signPackage.getLegalEntityPhoneSnapshot());
        // Checklist marks are system-derived from the actual matched template
        // set at render time; they are supported placeholders, not employee
        // data that HR must supply during preflight.
        values.put("attachmentDormitoryMark", "SYSTEM_DERIVED");
        values.put("attachmentDutyMark", "SYSTEM_DERIVED");
        values.put("attachmentHandbookMark", "SYSTEM_DERIVED");
        values.put("attachmentSalaryMark", "SYSTEM_DERIVED");
        return values;
    }

    private static String firstNonBlank(String preferred, String fallback)
    {
        return StringUtils.isNotBlank(preferred) ? preferred : fallback;
    }

    private static Map<String, String> placeholderLabels()
    {
        Map<String, String> labels = new LinkedHashMap<>();
        labels.put("employeeName", "员工姓名");
        labels.put("employeeIdCard", "身份证号");
        labels.put("employeePhone", "手机号");
        labels.put("employeeAddress", "联系住址");
        labels.put("employeeDeptName", "部门");
        labels.put("postName", "岗位");
        labels.put("postLevel", "岗位等级");
        labels.put("servicePersonType", "劳务人员类型");
        labels.put("insuranceType", "保险类型");
        labels.put("entryDate", "入职日期");
        labels.put("contractTermSelection", "合同期限选项");
        labels.put("contractTermFixedMark", "固定期限勾选标记");
        labels.put("contractTermOpenEndedMark", "无固定期限勾选标记");
        labels.put("contractStartDate", "合同开始日期");
        labels.put("contractEndDate", "合同结束日期");
        labels.put("previousContractEndDate", "原合同结束日期");
        labels.put("previousEmploymentType", "原合同类型");
        labels.put("previousRenewalCount", "原续签次数");
        labels.put("renewalCount", "本次续签次数");
        labels.put("probationStartDate", "试用期开始日期");
        labels.put("probationEndDate", "试用期结束日期");
        labels.put("actualRegularizationDate", "实际转正日期");
        labels.put("transferEffectiveDate", "调岗生效日期");
        labels.put("beforeDeptName", "调岗前部门");
        labels.put("afterDeptName", "调岗后部门");
        labels.put("beforePostName", "调岗前岗位");
        labels.put("afterPostName", "调岗后岗位");
        labels.put("workStartDate", "在职开始日期");
        labels.put("incomeStartYearMonth", "个人劳动收入主要来源起始年月");
        labels.put("workEndDate", "在职结束日期");
        labels.put("leaveDate", "离职日期");
        labels.put("leaveReason", "离职原因");
        labels.put("offboardingType", "离职类型");
        labels.put("salarySettlementStatus", "薪资结算状态");
        labels.put("assetHandoverStatus", "资产交接状态");
        labels.put("nonCompeteDecision", "竞业限制决定");
        labels.put("compensationAmount", "补偿金额");
        labels.put("compensationNote", "补偿说明");
        labels.put("baseSalary", "基本工资");
        labels.put("postSalary", "岗位工资");
        labels.put("fieldAllowance", "外勤补贴");
        labels.put("performanceSalary", "绩效工资");
        labels.put("salaryTotal", "薪资合计");
        labels.put("salaryVersion", "薪酬版本");
        labels.put("companyName", "公司法定全称");
        labels.put("companyCode", "公司编码");
        labels.put("companyCreditCode", "统一社会信用代码");
        labels.put("companyAddress", "公司注册地址");
        labels.put("companyLegalRepresentative", "法定代表人");
        labels.put("companyPhone", "公司联系电话");
        labels.put("attachmentDormitoryMark", "职工宿舍协议附件标记");
        labels.put("attachmentDutyMark", "岗位职责附件标记");
        labels.put("attachmentHandbookMark", "员工手册附件标记");
        labels.put("attachmentSalaryMark", "薪酬确认书附件标记");
        return labels;
    }
}
