package com.erp.system.service.support;

import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;
import com.erp.common.core.exception.ServiceException;
import com.erp.common.core.utils.StringUtils;
import com.erp.system.domain.SysConfig;
import com.erp.system.domain.vo.SysConfigDetailVo;
import com.erp.system.domain.vo.SysConfigExportVo;
import com.erp.system.domain.vo.SysConfigListVo;

/** Central sensitivity classification and safe projection for sys_config. */
@Component
public class SysConfigSensitivityPolicy
{
    public static final String MASK = "******";

    private static final Set<String> EXPLICIT_SENSITIVE_KEYS = Set.of(
            "sys.user.initpassword",
            "sys.mail.password",
            "sys.sms.secret",
            "sys.storage.access-key",
            "sys.storage.secret-key");

    private static final Pattern SENSITIVE_NAME = Pattern.compile(
            "(^|[._-])(password|passwd|secret|token|credential|private[._-]?key|access[._-]?key|api[._-]?key)([._-]|$)",
            Pattern.CASE_INSENSITIVE);

    public boolean isSensitive(String configKey)
    {
        if (StringUtils.isEmpty(configKey))
        {
            return false;
        }
        String normalized = configKey.trim().toLowerCase(Locale.ROOT);
        return EXPLICIT_SENSITIVE_KEYS.contains(normalized)
                || SENSITIVE_NAME.matcher(normalized).find()
                || normalized.contains("password")
                || normalized.contains("credential");
    }

    public void assertExternalReadAllowed(String configKey)
    {
        if (isSensitive(configKey))
        {
            throw new ServiceException("敏感参数不支持通过通用接口读取");
        }
    }

    public SysConfigListVo toListVo(SysConfig config)
    {
        SysConfigListVo view = new SysConfigListVo();
        copySafeFields(config, view);
        return view;
    }

    public SysConfigDetailVo toDetailVo(SysConfig config)
    {
        if (config == null)
        {
            return null;
        }
        SysConfigDetailVo view = new SysConfigDetailVo();
        copySafeFields(config, view);
        return view;
    }

    public SysConfigExportVo toExportVo(SysConfig config)
    {
        SysConfigExportVo view = new SysConfigExportVo();
        boolean sensitive = isSensitive(config.getConfigKey());
        view.setConfigId(config.getConfigId());
        view.setConfigName(config.getConfigName());
        view.setConfigKey(config.getConfigKey());
        view.setConfigValue(sensitive ? MASK : config.getConfigValue());
        view.setSensitive(sensitive);
        view.setValueConfigured(StringUtils.isNotEmpty(config.getConfigValue()));
        view.setConfigType(config.getConfigType());
        return view;
    }

    private void copySafeFields(SysConfig config, SysConfigListVo view)
    {
        boolean sensitive = isSensitive(config.getConfigKey());
        view.setConfigId(config.getConfigId());
        view.setConfigName(config.getConfigName());
        view.setConfigKey(config.getConfigKey());
        view.setConfigValue(sensitive ? MASK : config.getConfigValue());
        view.setConfigType(config.getConfigType());
        view.setSensitive(sensitive);
        view.setValueConfigured(StringUtils.isNotEmpty(config.getConfigValue()));
        view.setRemark(config.getRemark());
        view.setCreateTime(config.getCreateTime());
        view.setUpdateTime(config.getUpdateTime());
    }
}
