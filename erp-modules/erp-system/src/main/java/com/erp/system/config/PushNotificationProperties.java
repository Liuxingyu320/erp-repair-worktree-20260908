package com.erp.system.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 移动推送配置。私钥和服务账号均只通过外部运行环境提供。
 */
@Component
@ConfigurationProperties(prefix = "push")
public class PushNotificationProperties
{
    private final Fcm fcm = new Fcm();

    private final Apns apns = new Apns();

    public Fcm getFcm()
    {
        return fcm;
    }

    public Apns getApns()
    {
        return apns;
    }

    public static class Fcm
    {
        private boolean enabled;

        private String projectId;

        public boolean isEnabled()
        {
            return enabled;
        }

        public void setEnabled(boolean enabled)
        {
            this.enabled = enabled;
        }

        public String getProjectId()
        {
            return projectId;
        }

        public void setProjectId(String projectId)
        {
            this.projectId = projectId;
        }

        public boolean isComplete()
        {
            return enabled && notBlank(projectId);
        }
    }

    public static class Apns
    {
        private boolean enabled;

        private String authMode = "token";

        private String teamId;

        private String keyId;

        private String bundleId = "com.erp.mobile";

        private String privateKeyPath;

        private String certificatePath;

        private String certificatePassword = "";

        private String environment = "production";

        public boolean isEnabled()
        {
            return enabled;
        }

        public void setEnabled(boolean enabled)
        {
            this.enabled = enabled;
        }

        public String getAuthMode()
        {
            return authMode;
        }

        public void setAuthMode(String authMode)
        {
            this.authMode = authMode;
        }

        public String getTeamId()
        {
            return teamId;
        }

        public void setTeamId(String teamId)
        {
            this.teamId = teamId;
        }

        public String getKeyId()
        {
            return keyId;
        }

        public void setKeyId(String keyId)
        {
            this.keyId = keyId;
        }

        public String getBundleId()
        {
            return bundleId;
        }

        public void setBundleId(String bundleId)
        {
            this.bundleId = bundleId;
        }

        public String getPrivateKeyPath()
        {
            return privateKeyPath;
        }

        public void setPrivateKeyPath(String privateKeyPath)
        {
            this.privateKeyPath = privateKeyPath;
        }

        public String getCertificatePath()
        {
            return certificatePath;
        }

        public void setCertificatePath(String certificatePath)
        {
            this.certificatePath = certificatePath;
        }

        public String getCertificatePassword()
        {
            return certificatePassword;
        }

        public void setCertificatePassword(String certificatePassword)
        {
            this.certificatePassword = certificatePassword;
        }

        public String getEnvironment()
        {
            return environment;
        }

        public void setEnvironment(String environment)
        {
            this.environment = environment;
        }

        public boolean isComplete()
        {
            if (!enabled || !notBlank(bundleId)
                    || !("production".equalsIgnoreCase(environment) || "sandbox".equalsIgnoreCase(environment)))
            {
                return false;
            }
            if ("certificate".equalsIgnoreCase(authMode))
            {
                return notBlank(certificatePath);
            }
            return "token".equalsIgnoreCase(authMode)
                    && notBlank(teamId) && notBlank(keyId) && notBlank(privateKeyPath);
        }
    }

    private static boolean notBlank(String value)
    {
        return value != null && !value.trim().isEmpty();
    }
}
