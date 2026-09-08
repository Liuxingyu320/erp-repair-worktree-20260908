package com.erp.system.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Param;
import com.erp.system.domain.SysUserDeviceToken;

/**
 * 用户设备推送令牌数据层。
 */
public interface SysUserDeviceTokenMapper
{
    int upsertDeviceToken(SysUserDeviceToken deviceToken);

    int disableDeviceToken(@Param("userId") Long userId, @Param("platform") String platform,
            @Param("tokenHash") String tokenHash);

    int disableByTokenHash(@Param("platform") String platform, @Param("tokenHash") String tokenHash);

    List<SysUserDeviceToken> selectEnabledByUserId(@Param("userId") Long userId);
}
