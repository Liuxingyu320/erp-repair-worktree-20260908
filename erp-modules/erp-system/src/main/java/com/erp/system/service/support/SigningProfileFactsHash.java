package com.erp.system.service.support;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import com.erp.system.api.domain.SignCandidateUser;
import com.erp.system.api.domain.SysUserProfile;

/** Canonical System-owned optimistic-lock hash for reviewed signing facts. */
public final class SigningProfileFactsHash
{
    private SigningProfileFactsHash()
    {
    }

    public static String of(SignCandidateUser candidate)
    {
        if (candidate == null)
        {
            throw new IllegalArgumentException("Signing candidate must not be null");
        }
        return of(candidate.getCurrentAddress(), candidate.getStudentStatus(), candidate.getSchoolName(),
                candidate.getRetirementStatus(), candidate.getIncomeStartYearMonth());
    }

    public static String of(SysUserProfile profile)
    {
        if (profile == null)
        {
            throw new IllegalArgumentException("User profile must not be null");
        }
        return of(profile.getCurrentAddress(), profile.getStudentStatus(), profile.getSchoolName(),
                profile.getRetirementStatus(), profile.getIncomeStartYearMonth());
    }

    public static String of(String currentAddress, String studentStatus, String schoolName,
            String retirementStatus, String incomeStartYearMonth)
    {
        return sha256(canonical("v1", currentAddress, studentStatus, schoolName,
                retirementStatus, incomeStartYearMonth));
    }

    private static String canonical(String... values)
    {
        StringBuilder result = new StringBuilder();
        for (String value : values)
        {
            if (value == null)
            {
                result.append("-1:");
            }
            else
            {
                result.append(value.length()).append(':').append(value);
            }
            result.append(';');
        }
        return result.toString();
    }

    private static String sha256(String value)
    {
        try
        {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        }
        catch (NoSuchAlgorithmException exception)
        {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }
}
