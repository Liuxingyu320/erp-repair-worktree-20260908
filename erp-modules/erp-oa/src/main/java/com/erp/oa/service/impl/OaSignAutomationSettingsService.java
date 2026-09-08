package com.erp.oa.service.impl;

import java.util.List;
import org.springframework.stereotype.Service;
import com.erp.common.core.constant.SecurityConstants;
import com.erp.common.core.domain.R;
import com.erp.common.core.exception.ServiceException;
import com.erp.system.api.RemoteConfigService;
import com.erp.system.api.RemoteUserService;
import com.erp.system.api.domain.SignCandidateUser;
import com.erp.system.api.domain.SignCandidateUserQuery;

@Service
public class OaSignAutomationSettingsService
{
    public static final String HR_USER_ID_KEY = "sign.hr.user-id";
    private static final String HR_CONFIGURATION_ERROR =
            "请配置有效的默认签约任务接收人";

    private final RemoteConfigService configService;
    private final RemoteUserService userService;

    public OaSignAutomationSettingsService(RemoteConfigService configService, RemoteUserService userService)
    {
        this.configService = configService;
        this.userService = userService;
    }

    public Long resolveRequiredHrUserId()
    {
        Long userId = readConfiguredHrUserId();
        SignCandidateUserQuery query = new SignCandidateUserQuery();
        query.setUserIds(new Long[] { userId });
        query.setLimit(1);
        try
        {
            R<List<SignCandidateUser>> response =
                    userService.listSignCandidates(query, SecurityConstants.INNER);
            if (response == null || R.isError(response) || response.getData() == null
                    || response.getData().stream().noneMatch(user -> userId.equals(user.getUserId())))
            {
                throw configurationError();
            }
        }
        catch (ServiceException ex)
        {
            throw ex;
        }
        catch (RuntimeException ex)
        {
            throw configurationError();
        }
        return userId;
    }

    private Long readConfiguredHrUserId()
    {
        try
        {
            R<String> response = configService.getConfigKey(HR_USER_ID_KEY, SecurityConstants.INNER);
            if (response == null || R.isError(response) || response.getData() == null
                    || response.getData().isBlank())
            {
                throw configurationError();
            }
            long userId = Long.parseLong(response.getData().trim());
            if (userId <= 0)
            {
                throw configurationError();
            }
            return userId;
        }
        catch (ServiceException ex)
        {
            throw ex;
        }
        catch (RuntimeException ex)
        {
            throw configurationError();
        }
    }

    private ServiceException configurationError()
    {
        return new ServiceException(HR_CONFIGURATION_ERROR);
    }
}
