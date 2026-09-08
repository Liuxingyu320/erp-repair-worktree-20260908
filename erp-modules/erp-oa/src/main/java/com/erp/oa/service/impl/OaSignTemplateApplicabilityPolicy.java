package com.erp.oa.service.impl;

import java.time.LocalDate;
import java.time.Period;
import java.time.format.DateTimeParseException;
import java.util.Locale;
import com.erp.common.core.utils.StringUtils;
import com.erp.oa.constant.OaOnboardSalaryVersionPolicy;
import com.erp.oa.constant.OaSignTemplateType;
import com.erp.oa.domain.OaSignPackage;
import com.erp.oa.domain.OaSignPlanVersionTemplate;
import com.erp.oa.domain.OaSignTemplate;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Shared template applicability rules for interactive package preparation and onboard imports.
 */
final class OaSignTemplateApplicabilityPolicy
{
    private static final ObjectMapper MATCH_MAPPER = new ObjectMapper();

    private OaSignTemplateApplicabilityPolicy()
    {
    }

    static OaSignTemplate fromVersionSnapshot(OaSignPlanVersionTemplate snapshot)
    {
        if (snapshot == null)
        {
            return null;
        }
        OaSignTemplate template = new OaSignTemplate();
        template.setTemplateId(snapshot.getTemplateId());
        template.setTemplateType(snapshot.getTemplateType());
        template.setTemplateName(snapshot.getTemplateName());
        template.setTemplateVersion(snapshot.getTemplateVersion());
        template.setFileUrl(snapshot.getSourceFileUrl());
        template.setFileHash(snapshot.getSourceFileHash());
        template.setRequiredPlaceholders(snapshot.getRequiredPlaceholders());
        template.setEmployeeVisible(snapshot.getEmployeeVisible());
        template.setReadConfirmationRequired(snapshot.getReadConfirmationRequired());
        template.setEmployeeSignRequired(snapshot.getEmployeeSignRequired());
        template.setSignaturePositionJson(snapshot.getSignaturePositionJson());
        template.setCompanySealPositionJson(snapshot.getCompanySealPositionJson());
        template.setCompanySealRequired(snapshot.getCompanySealRequired());
        template.setMatchConditionJson(snapshot.getMatchConditionJson());
        template.setEmploymentType(matchConditionText(
                snapshot.getMatchConditionJson(), "employmentType"));
        template.setSocialType(matchConditionText(
                snapshot.getMatchConditionJson(), "socialType"));
        template.setPostLevelScope(matchConditionText(
                snapshot.getMatchConditionJson(), "postLevelScope"));
        template.setSalaryVersion(matchConditionText(
                snapshot.getMatchConditionJson(), "salaryVersion"));
        template.setSortOrder(snapshot.getSortOrder());
        template.setStatus("0");
        return template;
    }

    static boolean shouldIncludeTemplate(OaSignTemplate template, OaSignPackage signPackage)
    {
        if (template == null || signPackage == null
                || !OaSignTemplateServiceImpl.matchesPostLevelScope(
                        template.getPostLevelScope(), signPackage.getPostLevelSnapshot()))
        {
            return false;
        }
        if (!matchesEmploymentType(template.getEmploymentType(), signPackage.getEmploymentType())
                || !matchesSocialType(template.getSocialType(), signPackage.getSocialType())
                || !matchesSalaryVersion(template.getSalaryVersion(), signPackage.getSalaryVersion()))
        {
            return false;
        }
        if (!OaSignTemplateType.ONBOARD_MINOR_NONSTUDENT_DECLARATION.equals(
                template.getTemplateType()))
        {
            return true;
        }
        int age = ageAtContractStart(signPackage);
        return "NON_STUDENT".equalsIgnoreCase(StringUtils.trim(
                signPackage.getStudentStatusSnapshot()))
                && age >= 16 && age < 18;
    }

    static String matchConditionText(String json, String field)
    {
        if (StringUtils.isBlank(json) || StringUtils.isBlank(field))
        {
            return null;
        }
        try
        {
            JsonNode value = MATCH_MAPPER.readTree(json).get(field);
            return value != null && value.isTextual() && StringUtils.isNotBlank(value.asText())
                    ? value.asText().trim() : null;
        }
        catch (Exception ignored)
        {
            return null;
        }
    }

    private static boolean matchesEmploymentType(String expected, String actual)
    {
        if (StringUtils.isBlank(expected)) return true;
        if (StringUtils.isBlank(actual)) return false;
        String expectedCode = "劳动合同".equals(StringUtils.trim(expected))
                ? "LABOR_CONTRACT" : "劳务合同".equals(StringUtils.trim(expected))
                ? "SERVICE_CONTRACT" : StringUtils.trim(expected).toUpperCase(Locale.ROOT);
        String actualCode = "劳动合同".equals(StringUtils.trim(actual))
                ? "LABOR_CONTRACT" : "劳务合同".equals(StringUtils.trim(actual))
                ? "SERVICE_CONTRACT" : StringUtils.trim(actual).toUpperCase(Locale.ROOT);
        return expectedCode.equals(actualCode);
    }

    private static boolean matchesSocialType(String expected, String actual)
    {
        if (StringUtils.isBlank(expected)) return true;
        String expectedCode = OaOnboardSalaryVersionPolicy.normalizeSocialType(expected);
        String actualCode = OaOnboardSalaryVersionPolicy.normalizeSocialType(actual);
        return expectedCode != null ? expectedCode.equals(actualCode)
                : expected.trim().equalsIgnoreCase(StringUtils.trim(actual));
    }

    private static boolean matchesSalaryVersion(String expected, String actual)
    {
        return StringUtils.isBlank(expected) || StringUtils.isNotBlank(actual)
                && expected.trim().equalsIgnoreCase(actual.trim());
    }

    private static int ageAtContractStart(OaSignPackage signPackage)
    {
        try
        {
            String idCard = StringUtils.trim(signPackage.getEmployeeIdCardSnapshot());
            if (idCard == null || !idCard.matches("[0-9]{17}[0-9Xx]"))
            {
                return -1;
            }
            LocalDate birthDate = LocalDate.parse(idCard.substring(6, 14),
                    java.time.format.DateTimeFormatter.BASIC_ISO_DATE);
            LocalDate referenceDate = StringUtils.isBlank(signPackage.getContractStartDate())
                    ? LocalDate.now() : LocalDate.parse(signPackage.getContractStartDate());
            if (referenceDate.isBefore(birthDate))
            {
                return -1;
            }
            return Period.between(birthDate, referenceDate).getYears();
        }
        catch (DateTimeParseException | IndexOutOfBoundsException ignored)
        {
            return -1;
        }
    }
}
