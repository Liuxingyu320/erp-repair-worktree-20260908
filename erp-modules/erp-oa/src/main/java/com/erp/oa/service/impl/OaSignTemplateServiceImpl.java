package com.erp.oa.service.impl;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.erp.common.core.utils.StringUtils;
import com.erp.common.security.utils.SecurityUtils;
import com.erp.oa.constant.OaSignTemplateType;
import com.erp.oa.domain.OaSignPackage;
import com.erp.oa.domain.OaSignTemplate;
import com.erp.oa.mapper.OaSignTemplateMapper;
import com.erp.oa.service.IOaSignTemplateService;

@Service
public class OaSignTemplateServiceImpl implements IOaSignTemplateService
{
    private static final Pattern NUMBER_PATTERN = Pattern.compile("(\\d+)");

    @Autowired
    private OaSignTemplateMapper templateMapper;

    @Autowired
    private OaSignDocumentService documentService;

    @Autowired
    private OaSignPlacementPolicyService placementPolicyService;

    @Override
    public List<OaSignTemplateType.Option> listTemplateTypes()
    {
        return OaSignTemplateType.options();
    }

    @Override
    public List<OaSignTemplate> selectTemplateList(OaSignTemplate template)
    {
        return templateMapper.selectOaSignTemplateList(template);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public OaSignTemplate saveTemplate(OaSignTemplate template)
    {
        String templateType = StringUtils.isBlank(template.getTemplateType())
                ? null : template.getTemplateType().trim().toUpperCase(Locale.ROOT);
        OaSignTemplateType.Option type = OaSignTemplateType.require(templateType);
        template.setTemplateType(type.getCode());
        normalizeTemplateRule(template);
        documentService.assertTemplateContainsRequiredPlaceholders(template.getTemplateType(), template.getFileUrl());
        template.setScenario(type.getScenario());
        normalizeStatus(template);
        normalizeDeliveryPolicy(template, type);
        normalizePlacementPolicy(template);
        template.setRequiredPlaceholders(String.join(",", type.getRequiredPlaceholders()));
        template.setFileHash(documentService.calculateFileUrlSha256(template.getFileUrl()));
        if (template.getSortOrder() == null)
        {
            template.setSortOrder(100);
        }
        if (template.getTemplateId() == null)
        {
            template.setCreateBy(SecurityUtils.getUsername());
            templateMapper.insertOaSignTemplate(template);
        }
        else
        {
            template.setUpdateBy(SecurityUtils.getUsername());
            templateMapper.updateOaSignTemplate(template);
        }
        return templateMapper.selectOaSignTemplateById(template.getTemplateId());
    }

    private void normalizeStatus(OaSignTemplate template)
    {
        String status = StringUtils.isBlank(template.getStatus())
                ? null : template.getStatus().trim();
        if (status != null && !"0".equals(status) && !"1".equals(status))
        {
            throw new com.erp.common.core.exception.ServiceException("模板状态必须为启用或停用");
        }
        if (template.getTemplateId() == null)
        {
            // Uploading and enabling in one request bypasses the deliberate review step.
            template.setStatus("1");
        }
        else
        {
            // A blank status on update leaves the existing database value unchanged.
            template.setStatus(status);
        }
    }

    private void normalizeDeliveryPolicy(OaSignTemplate template, OaSignTemplateType.Option type)
    {
        if (!type.isEmployeeVisible())
        {
            template.setEmployeeVisible("N");
            template.setReadConfirmationRequired("N");
            template.setEmployeeSignRequired("N");
            template.setCompanySealRequired("N");
            template.setSignaturePositionJson(null);
            template.setCompanySealPositionJson(null);
            return;
        }
        template.setEmployeeVisible(normalizeYesNo(template.getEmployeeVisible(), "Y", "员工可见"));
        if ("N".equals(template.getEmployeeVisible()))
        {
            template.setReadConfirmationRequired("N");
            template.setEmployeeSignRequired("N");
            template.setCompanySealRequired("N");
            template.setSignaturePositionJson(null);
            template.setCompanySealPositionJson(null);
            return;
        }
        template.setEmployeeSignRequired(normalizeYesNo(template.getEmployeeSignRequired(),
                type.isEmployeeSignRequired() ? "Y" : "N", "员工签署要求"));
        template.setReadConfirmationRequired(normalizeYesNo(template.getReadConfirmationRequired(),
                type.isReadConfirmationRequired() ? "Y" : "N", "阅读确认要求"));
        if ("Y".equals(template.getEmployeeSignRequired()))
        {
            template.setReadConfirmationRequired("Y");
        }
    }

    private void normalizePlacementPolicy(OaSignTemplate template)
    {
        boolean employeeVisible = "Y".equals(template.getEmployeeVisible());
        boolean employeeSignRequired = employeeVisible
                && "Y".equals(template.getEmployeeSignRequired());
        String companySealRequired = placementPolicyService.normalizeCompanySealRequired(
                template.getCompanySealRequired(), employeeVisible);
        template.setCompanySealRequired(companySealRequired);
        template.setSignaturePositionJson(placementPolicyService.freezeSignaturePosition(
                template.getSignaturePositionJson(), employeeSignRequired));
        template.setCompanySealPositionJson(placementPolicyService.freezeCompanySealPosition(
                template.getCompanySealPositionJson(), "Y".equals(companySealRequired)));
    }

    private String normalizeYesNo(String value, String defaultValue, String label)
    {
        String normalized = StringUtils.isBlank(value) ? defaultValue : value.trim().toUpperCase();
        if (!"Y".equals(normalized) && !"N".equals(normalized))
        {
            throw new com.erp.common.core.exception.ServiceException(label + "必须为Y或N");
        }
        return normalized;
    }

    private void normalizeTemplateRule(OaSignTemplate template)
    {
        if (OaSignTemplateType.ONBOARD_LABOR_CONTRACT.equals(template.getTemplateType())
                || OaSignTemplateType.RENEWAL_LABOR_CONTRACT.equals(template.getTemplateType())
                || OaSignTemplateType.ONBOARD_OFFER_NOTICE.equals(template.getTemplateType())
                || OaSignTemplateType.ONBOARD_HANDBOOK.equals(template.getTemplateType())
                || OaSignTemplateType.ONBOARD_COMMITMENT.equals(template.getTemplateType())
                || OaSignTemplateType.ONBOARD_POST_DUTY.equals(template.getTemplateType())
                || OaSignTemplateType.ONBOARD_HANDBOOK_RECEIPT.equals(template.getTemplateType())
                || OaSignTemplateType.ONBOARD_SALARY_CONFIRM.equals(template.getTemplateType()))
        {
            template.setEmploymentType("劳动合同");
        }
        else if (OaSignTemplateType.ONBOARD_SERVICE_CONTRACT.equals(template.getTemplateType())
                || OaSignTemplateType.ONBOARD_SERVICE_RECEIPT.equals(template.getTemplateType())
                || OaSignTemplateType.RENEWAL_SERVICE_CONTRACT.equals(template.getTemplateType()))
        {
            template.setEmploymentType("劳务合同");
            template.setSocialType("");
        }
        else if (OaSignTemplateType.RENEWAL_SALARY_CONFIRM.equals(template.getTemplateType()))
        {
            template.setEmploymentType("");
            template.setSocialType("");
        }
        else if (OaSignTemplateType.ONBOARD_CONFIDENTIAL_NONCOMPETE.equals(template.getTemplateType())
                || OaSignTemplateType.ONBOARD_MINOR_NONSTUDENT_DECLARATION.equals(
                        template.getTemplateType()))
        {
            // 两份材料按个人事实/职级匹配，不应被劳动或劳务类型过滤掉。
            template.setEmploymentType("");
            template.setSocialType("");
        }
    }

    @Override
    public List<OaSignTemplate> matchTemplates(OaSignPackage signPackage)
    {
        List<OaSignTemplate> candidates = templateMapper.selectMatchedActiveTemplates(signPackage);
        if (candidates == null || candidates.isEmpty())
        {
            return candidates;
        }
        List<OaSignTemplate> matched = new ArrayList<>();
        for (OaSignTemplate template : candidates)
        {
            if (matchesPostLevelScope(template.getPostLevelScope(), signPackage.getPostLevelSnapshot()))
            {
                matched.add(template);
            }
        }
        return matched;
    }

    static boolean matchesPostLevelScope(String scope, String postLevel)
    {
        if (StringUtils.isBlank(scope) || StringUtils.isBlank(postLevel))
        {
            return true;
        }
        Integer level = firstNumber(postLevel);
        if (level == null)
        {
            return true;
        }
        String normalized = scope.replace('－', '-')
                .replace('—', '-')
                .replace('～', '-')
                .replace('~', '-')
                .replace("至", "-");
        String[] tokens = normalized.split("[,，、;；\\s]+");
        for (String token : tokens)
        {
            if (StringUtils.isBlank(token))
            {
                continue;
            }
            if (matchesPostLevelToken(token, level))
            {
                return true;
            }
        }
        return false;
    }

    private static boolean matchesPostLevelToken(String token, int level)
    {
        if (token.contains("及以上") || token.contains("以上"))
        {
            Integer min = firstNumber(token);
            return min != null && level >= min;
        }
        if (token.contains("及以下") || token.contains("以下"))
        {
            Integer max = firstNumber(token);
            return max != null && level <= max;
        }
        int rangeIndex = token.indexOf('-');
        if (rangeIndex > -1)
        {
            Integer start = firstNumber(token.substring(0, rangeIndex));
            Integer end = firstNumber(token.substring(rangeIndex + 1));
            if (start == null || end == null)
            {
                return false;
            }
            int min = Math.min(start, end);
            int max = Math.max(start, end);
            return level >= min && level <= max;
        }
        Integer exact = firstNumber(token);
        return exact != null && exact == level;
    }

    private static Integer firstNumber(String value)
    {
        if (StringUtils.isBlank(value))
        {
            return null;
        }
        Matcher matcher = NUMBER_PATTERN.matcher(value);
        return matcher.find() ? Integer.valueOf(matcher.group(1)) : null;
    }
}
