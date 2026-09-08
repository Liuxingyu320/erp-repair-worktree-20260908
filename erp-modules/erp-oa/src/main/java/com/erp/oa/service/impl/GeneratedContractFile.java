package com.erp.oa.service.impl;

public class GeneratedContractFile
{
    private final String docxUrl;
    private final String pdfUrl;
    private final String sha256;
    private final String signatureFileUrl;
    private final String certificateFileUrl;
    private final String certificateSha256;

    public GeneratedContractFile(String docxUrl, String pdfUrl, String sha256, String signatureFileUrl)
    {
        this(docxUrl, pdfUrl, sha256, signatureFileUrl, null, null);
    }

    public GeneratedContractFile(String docxUrl, String pdfUrl, String sha256, String signatureFileUrl,
            String certificateFileUrl, String certificateSha256)
    {
        this.docxUrl = docxUrl;
        this.pdfUrl = pdfUrl;
        this.sha256 = sha256;
        this.signatureFileUrl = signatureFileUrl;
        this.certificateFileUrl = certificateFileUrl;
        this.certificateSha256 = certificateSha256;
    }

    public String getDocxUrl()
    {
        return docxUrl;
    }

    public String getPdfUrl()
    {
        return pdfUrl;
    }

    public String getSha256()
    {
        return sha256;
    }

    public String getSignatureFileUrl()
    {
        return signatureFileUrl;
    }

    public String getCertificateFileUrl()
    {
        return certificateFileUrl;
    }

    public String getCertificateSha256()
    {
        return certificateSha256;
    }
}
