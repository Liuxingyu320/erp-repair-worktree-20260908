package com.erp.system.service;

import org.springframework.stereotype.Component;
import com.erp.common.core.exception.ServiceException;
import com.erp.system.api.constant.SigningProfileCodes;
import com.erp.system.api.domain.SysUserProfile;
import com.erp.system.support.HrEmployeeStatusCatalog;

/**
 * Normalizes legacy signing profile values at the system write boundary.
 */
@Component
public class SigningProfileNormalizer
{
    public void normalize(SysUserProfile profile)
    {
        if (profile == null)
        {
            return;
        }

        profile.setEmployeeStatus(HrEmployeeStatusCatalog.normalizeForWrite(profile.getEmployeeStatus()));

        String legacyContractType = trimToNull(profile.getContractType());
        String contractTerm = normalizeContractTerm(profile.getContractTerm());
        profile.setContractType(normalizeContractType(legacyContractType));
        if (contractTerm == null && isFixedTermLegacyContract(legacyContractType))
        {
            contractTerm = SigningProfileCodes.FIXED_TERM;
        }
        else if (contractTerm == null && "无固定期限劳动合同".equals(legacyContractType))
        {
            contractTerm = SigningProfileCodes.OPEN_ENDED;
        }
        profile.setContractTerm(contractTerm);
        profile.setSocialType(normalizeSocialType(profile.getSocialType()));
    }

    private String normalizeContractType(String value)
    {
        if (value == null)
        {
            return null;
        }
        if (SigningProfileCodes.isKnownContractType(value))
        {
            return value;
        }
        return switch (value)
        {
            case "固定期限劳动合同", "无固定期限劳动合同", "劳动合同" ->
                    SigningProfileCodes.LABOR_CONTRACT;
            case "劳务协议", "劳务合同" -> SigningProfileCodes.SERVICE_CONTRACT;
            case "实习协议" -> SigningProfileCodes.INTERNSHIP_AGREEMENT;
            case "外包合同" -> SigningProfileCodes.OUTSOURCING_CONTRACT;
            default -> throw new ServiceException("合同类型不受支持: " + value);
        };
    }

    private String normalizeContractTerm(String value)
    {
        String normalized = trimToNull(value);
        if (normalized == null || SigningProfileCodes.isKnownContractTerm(normalized))
        {
            return normalized;
        }
        if ("固定期限".equals(normalized))
        {
            return SigningProfileCodes.FIXED_TERM;
        }
        if ("无固定期限".equals(normalized))
        {
            return SigningProfileCodes.OPEN_ENDED;
        }
        return normalized;
    }

    private String normalizeSocialType(String value)
    {
        String normalized = trimToNull(value);
        if (normalized == null || SigningProfileCodes.isKnownSocialType(normalized))
        {
            return normalized;
        }
        return switch (normalized)
        {
            case "本地社保", "异地社保", "有社保" -> SigningProfileCodes.SOCIAL_INSURED;
            case "无需缴纳", "无社保" -> SigningProfileCodes.SOCIAL_UNINSURED;
            case "劳务派遣" -> SigningProfileCodes.DISPATCHED;
            case "待确认" -> SigningProfileCodes.PENDING_CONFIRMATION;
            default -> throw new ServiceException("社保类型不受支持: " + normalized);
        };
    }

    private boolean isFixedTermLegacyContract(String value)
    {
        return "固定期限劳动合同".equals(value) || "劳动合同".equals(value);
    }

    private String trimToNull(String value)
    {
        if (value == null)
        {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
