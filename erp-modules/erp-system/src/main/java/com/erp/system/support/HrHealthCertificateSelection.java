package com.erp.system.support;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import com.erp.system.domain.vo.HrHealthCertificateVo;

/** Mirrors chosenCertificateId SQL; used only while holding the employee lock for cache updates. */
public final class HrHealthCertificateSelection
{
    private HrHealthCertificateSelection() { }

    public static LocalDate starts(HrHealthCertificateVo row)
    {
        return row.getValidFrom() == null ? row.getIssuedDate() : row.getValidFrom();
    }

    public static boolean validDates(HrHealthCertificateVo row)
    {
        return row != null && starts(row) != null && row.getIssuedDate() != null
                && row.getExpiresOn() != null && !starts(row).isAfter(row.getExpiresOn())
                && !row.getIssuedDate().isAfter(row.getExpiresOn());
    }

    public static boolean effective(HrHealthCertificateVo row, LocalDate day)
    {
        return validDates(row) && "APPROVED".equals(row.getReviewStatus())
                && "0".equals(row.getDelFlag()) && !starts(row).isAfter(day)
                && !row.getExpiresOn().isBefore(day);
    }

    public static HrHealthCertificateVo choose(List<HrHealthCertificateVo> rows, LocalDate day)
    {
        if (rows == null || rows.isEmpty()) return null;
        List<HrHealthCertificateVo> valid = rows.stream().filter(row -> validDates(row)
                && "APPROVED".equals(row.getReviewStatus()) && "0".equals(row.getDelFlag())).toList();
        List<HrHealthCertificateVo> current = valid.stream().filter(row -> effective(row,day)
                && "Y".equals(row.getCurrentFlag())).toList();
        if (current.size() == 1) return current.get(0);
        Comparator<HrHealthCertificateVo> latest = Comparator.comparing(HrHealthCertificateSelection::starts)
                .thenComparing(HrHealthCertificateVo::getReviewedTime, Comparator.nullsFirst(Comparator.naturalOrder()))
                .thenComparing(HrHealthCertificateVo::getCertificateId, Comparator.nullsFirst(Comparator.naturalOrder()));
        HrHealthCertificateVo effective = valid.stream().filter(row -> effective(row,day)).max(latest).orElse(null);
        if (effective != null) return effective;
        HrHealthCertificateVo expired = valid.stream().filter(row -> row.getExpiresOn().isBefore(day)).max(latest).orElse(null);
        if (expired != null) return expired;
        return valid.stream().filter(row -> starts(row).isAfter(day))
                .min(Comparator.comparing(HrHealthCertificateSelection::starts).thenComparing(latest.reversed())).orElse(null);
    }
}
