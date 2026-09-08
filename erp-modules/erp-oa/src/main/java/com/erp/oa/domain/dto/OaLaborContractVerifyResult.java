package com.erp.oa.domain.dto;

import java.util.Date;

public class OaLaborContractVerifyResult
{
    private boolean matched;

    private Long contractId;

    private String contractNo;

    private String employeeName;

    private Date signedTime;

    private String fileKind;

    private String documentVersion;

    private String hash;

    public static OaLaborContractVerifyResult unmatched(String hash)
    {
        OaLaborContractVerifyResult result = new OaLaborContractVerifyResult();
        result.setMatched(false);
        result.setHash(hash);
        return result;
    }

    public boolean isMatched() { return matched; }
    public void setMatched(boolean matched) { this.matched = matched; }

    public Long getContractId() { return contractId; }
    public void setContractId(Long contractId) { this.contractId = contractId; }

    public String getContractNo() { return contractNo; }
    public void setContractNo(String contractNo) { this.contractNo = contractNo; }

    public String getEmployeeName() { return employeeName; }
    public void setEmployeeName(String employeeName) { this.employeeName = employeeName; }

    public Date getSignedTime() { return signedTime; }
    public void setSignedTime(Date signedTime) { this.signedTime = signedTime; }

    public String getFileKind() { return fileKind; }
    public void setFileKind(String fileKind) { this.fileKind = fileKind; }

    public String getDocumentVersion() { return documentVersion; }
    public void setDocumentVersion(String documentVersion) { this.documentVersion = documentVersion; }

    public String getHash() { return hash; }
    public void setHash(String hash) { this.hash = hash; }
}
