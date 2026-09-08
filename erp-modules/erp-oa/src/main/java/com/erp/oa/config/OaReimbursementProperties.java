package com.erp.oa.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "oa.reimbursement")
public class OaReimbursementProperties
{
    private String storageRoot = "./uploadPath/private/reimbursement";
    private String tempRoot = System.getProperty("java.io.tmpdir")
            + "/erp-reimbursement";
    private long maxInvoiceBytes = 10L * 1024L * 1024L;
    private int maxInvoicesPerClaim = 20;
    private int maxClaimsPerExport = 200;
    private final Ocr ocr = new Ocr();

    public String getStorageRoot()
    {
        return storageRoot;
    }

    public void setStorageRoot(String storageRoot)
    {
        this.storageRoot = storageRoot;
    }

    public String getTempRoot()
    {
        return tempRoot;
    }

    public void setTempRoot(String tempRoot)
    {
        this.tempRoot = tempRoot;
    }

    public long getMaxInvoiceBytes()
    {
        return maxInvoiceBytes;
    }

    public void setMaxInvoiceBytes(long maxInvoiceBytes)
    {
        this.maxInvoiceBytes = maxInvoiceBytes;
    }

    public int getMaxInvoicesPerClaim()
    {
        return maxInvoicesPerClaim;
    }

    public void setMaxInvoicesPerClaim(int maxInvoicesPerClaim)
    {
        this.maxInvoicesPerClaim = maxInvoicesPerClaim;
    }

    public int getMaxClaimsPerExport()
    {
        return maxClaimsPerExport;
    }

    public void setMaxClaimsPerExport(int maxClaimsPerExport)
    {
        this.maxClaimsPerExport = maxClaimsPerExport;
    }

    public Ocr getOcr()
    {
        return ocr;
    }

    public static class Ocr
    {
        private boolean autoRecognize = true;
        private final Cloud cloud = new Cloud();
        private final Local local = new Local();

        public boolean isAutoRecognize()
        {
            return autoRecognize;
        }

        public void setAutoRecognize(boolean autoRecognize)
        {
            this.autoRecognize = autoRecognize;
        }

        public Cloud getCloud()
        {
            return cloud;
        }

        public Local getLocal()
        {
            return local;
        }
    }

    public static class Cloud
    {
        private boolean enabled = true;
        private String provider = "baidu";
        private String apiKey = "";
        private String secretKey = "";
        private String tokenUrl =
                "https://aip.baidubce.com/oauth/2.0/token";
        private String recognizeUrl =
                "https://aip.baidubce.com/rest/2.0/ocr/v1/vat_invoice";
        private int timeoutSeconds = 25;
        private long maxEncodedBytes = 8L * 1024L * 1024L;

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean value) { enabled = value; }
        public String getProvider() { return provider; }
        public void setProvider(String value) { provider = value; }
        public String getApiKey() { return apiKey; }
        public void setApiKey(String value) { apiKey = value; }
        public String getSecretKey() { return secretKey; }
        public void setSecretKey(String value) { secretKey = value; }
        public String getTokenUrl() { return tokenUrl; }
        public void setTokenUrl(String value) { tokenUrl = value; }
        public String getRecognizeUrl() { return recognizeUrl; }
        public void setRecognizeUrl(String value) { recognizeUrl = value; }
        public int getTimeoutSeconds() { return timeoutSeconds; }
        public void setTimeoutSeconds(int value) { timeoutSeconds = value; }
        public long getMaxEncodedBytes() { return maxEncodedBytes; }
        public void setMaxEncodedBytes(long value) { maxEncodedBytes = value; }
    }

    public static class Local
    {
        private boolean enabled = true;
        private String command = "auto";
        private String languages = "chi_sim+eng";
        private int timeoutSeconds = 30;
        private int renderDpi = 250;

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean value) { enabled = value; }
        public String getCommand() { return command; }
        public void setCommand(String value) { command = value; }
        public String getLanguages() { return languages; }
        public void setLanguages(String value) { languages = value; }
        public int getTimeoutSeconds() { return timeoutSeconds; }
        public void setTimeoutSeconds(int value) { timeoutSeconds = value; }
        public int getRenderDpi() { return renderDpi; }
        public void setRenderDpi(int value) { renderDpi = value; }
    }
}
