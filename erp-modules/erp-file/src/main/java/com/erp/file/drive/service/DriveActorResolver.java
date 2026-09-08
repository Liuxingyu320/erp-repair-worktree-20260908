package com.erp.file.drive.service;

import java.util.Set;
import com.erp.common.security.utils.SecurityUtils;
import com.erp.file.drive.constant.DriveErrorCodes;
import com.erp.file.drive.domain.DriveActor;
import com.erp.file.drive.domain.DriveIdentity;
import com.erp.file.drive.exception.DriveException;
import com.erp.file.drive.mapper.DriveIdentityMapper;
import com.erp.system.api.model.LoginUser;
import org.springframework.stereotype.Service;

/**
 * 合并登录账号与实时组织身份，避免使用令牌中的过期部门。
 */
@Service
public class DriveActorResolver
{
    private final DriveIdentityMapper identityMapper;

    public DriveActorResolver(DriveIdentityMapper identityMapper)
    {
        this.identityMapper = identityMapper;
    }

    public DriveActor resolve()
    {
        LoginUser loginUser = SecurityUtils.getLoginUser();
        if (loginUser == null || loginUser.getUserid() == null)
        {
            throw accessDenied();
        }

        Long userId = loginUser.getUserid();
        DriveIdentity identity = identityMapper.selectCurrentIdentity(userId);
        if (identity == null || !userId.equals(identity.getUserId()))
        {
            throw accessDenied();
        }

        Set<String> permissions = loginUser.getPermissions() == null
                ? Set.of() : loginUser.getPermissions();
        return new DriveActor(userId, identity.getDeptId(), identity.getDeptName(),
                loginUser.getUsername(), permissions, SecurityUtils.isAdmin());
    }

    private static DriveException accessDenied()
    {
        return new DriveException(DriveErrorCodes.DRIVE_ACCESS_DENIED, "无法确认当前登录用户");
    }
}
