package com.erp.inventory.domain.vo;

/** Browser-safe one-time response for an opaque adjudication basis token. */
public record InvTransferReceiptDiscrepancyAdjudicationBasisIssueVo(
        String basisToken,
        String discrepancyCaseId,
        String caseVersion,
        String selectedOrganizationId,
        String issuedAt,
        String expiresAt,
        String dataSource)
{
    @Override
    public String toString()
    {
        return "InvTransferReceiptDiscrepancyAdjudicationBasisIssueVo["
                + "basisToken=[REDACTED], discrepancyCaseId="
                + discrepancyCaseId + ", caseVersion=" + caseVersion
                + ", selectedOrganizationId=" + selectedOrganizationId
                + ", issuedAt=" + issuedAt + ", expiresAt=" + expiresAt
                + ", dataSource=" + dataSource + "]";
    }
}
