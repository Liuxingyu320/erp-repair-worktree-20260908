package com.erp.oa.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "sign-package")
public class OaSignFileProperties
{
    private final Storage storage = new Storage();
    private final Pdf pdf = new Pdf();

    public Storage getStorage()
    {
        return storage;
    }

    public Pdf getPdf()
    {
        return pdf;
    }

    public static class Storage
    {
        private String rootPath = "./uploadPath/private/sign-package";
        private String tempPath = System.getProperty("java.io.tmpdir") + "/erp-sign-package";
        private String publicPrefix = "/profile/private/sign-package";

        public String getRootPath() { return rootPath; }
        public void setRootPath(String rootPath) { this.rootPath = rootPath; }

        public String getTempPath() { return tempPath; }
        public void setTempPath(String tempPath) { this.tempPath = tempPath; }

        public String getPublicPrefix() { return publicPrefix; }
        public void setPublicPrefix(String publicPrefix) { this.publicPrefix = publicPrefix; }
    }

    public static class Pdf
    {
        private String converterCommand = "libreoffice";
        private int timeoutSeconds = 60;

        public String getConverterCommand() { return converterCommand; }
        public void setConverterCommand(String converterCommand) { this.converterCommand = converterCommand; }

        public int getTimeoutSeconds() { return timeoutSeconds; }
        public void setTimeoutSeconds(int timeoutSeconds) { this.timeoutSeconds = timeoutSeconds; }
    }
}
