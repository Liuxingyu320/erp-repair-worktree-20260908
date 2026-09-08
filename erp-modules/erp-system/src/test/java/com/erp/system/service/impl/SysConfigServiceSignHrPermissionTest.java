package com.erp.system.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.any;

import java.util.Arrays;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.erp.common.redis.service.RedisService;
import com.erp.common.core.exception.ServiceException;
import com.erp.system.api.domain.SysUser;
import com.erp.system.config.SysConfigDescriptorRegistry;
import com.erp.system.domain.SysConfig;
import com.erp.system.mapper.SysConfigMapper;
import com.erp.system.mapper.SysUserMapper;
import com.erp.system.service.support.SysConfigSensitivityPolicy;

@ExtendWith(MockitoExtension.class)
@DisplayName("唯一HR配置权限同步")
class SysConfigServiceSignHrPermissionTest
{
    @Mock
    private SysConfigMapper configMapper;

    @Mock
    private RedisService redisService;

    @Mock
    private SysUserMapper userMapper;

    @Spy
    private SysConfigDescriptorRegistry configDescriptorRegistry = new SysConfigDescriptorRegistry(new ObjectMapper());

    @InjectMocks
    private SysConfigServiceImpl service;

    @Test
    @DisplayName("系统写路径可显式锁定唯一HR状态单例")
    void mapperShouldExposeSignHrStateLock()
    {
        assertThat(Arrays.stream(SysConfigMapper.class.getMethods()).map(method -> method.getName()))
                .contains("lockSignHrState", "selectConfiguredSignHrUserId");
    }

    @Test
    @DisplayName("新增唯一HR配置后同步其现有角色权限")
    void shouldSyncPermissionsAfterHrConfigInsert()
    {
        SysConfig config = config(7L, "sign.hr.user-id", "88");
        when(userMapper.selectUserById(88L)).thenReturn(activeUser(88L));
        when(configMapper.insertConfig(config)).thenReturn(1);

        service.insertConfig(config);

        InOrder order = inOrder(configMapper, userMapper);
        order.verify(configMapper).lockSignHrState();
        order.verify(userMapper).selectUserById(88L);
        order.verify(configMapper).insertConfig(config);
        order.verify(configMapper).syncSignHrPermissions();
    }

    @Test
    @DisplayName("更换唯一HR后撤销旧托管授权并授予新HR")
    void shouldSyncPermissionsAfterHrConfigUpdate()
    {
        SysConfig existing = config(7L, "sign.hr.user-id", "77");
        SysConfig update = config(7L, "sign.hr.user-id", "88");
        when(configMapper.selectConfigById(7L)).thenReturn(existing);
        when(userMapper.selectUserById(88L)).thenReturn(activeUser(88L));
        when(configMapper.updateConfig(update)).thenReturn(1);

        service.updateConfig(update);

        InOrder order = inOrder(configMapper, userMapper);
        order.verify(configMapper).lockSignHrState();
        order.verify(configMapper).selectConfigById(7L);
        order.verify(userMapper).selectUserById(88L);
        order.verify(configMapper).updateConfig(update);
        order.verify(configMapper).syncSignHrPermissions();
    }

    @Test
    @DisplayName("修改配置缺少版本时拒绝覆盖")
    void shouldRejectUpdateWithoutExpectedVersion()
    {
        SysConfig update = config(7L, "ordinary.key", "new");
        update.setVersion(null);

        assertThatThrownBy(() -> service.updateConfig(update))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("版本不能为空");

        verify(configMapper, never()).updateConfig(update);
    }

    @Test
    @DisplayName("唯一HR配置拒绝非正整数或无效用户")
    void shouldRejectInvalidConfiguredHrBeforeWriting()
    {
        assertThatThrownBy(() -> service.insertConfig(config(7L, "sign.hr.user-id", "0")))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("有效用户");

        SysUser disabled = activeUser(88L);
        disabled.setStatus("1");
        when(userMapper.selectUserById(88L)).thenReturn(disabled);
        assertThatThrownBy(() -> service.insertConfig(config(8L, "sign.hr.user-id", "88")))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("有效用户");

        verify(configMapper, never()).insertConfig(org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("删除配置先锁状态再修改并同步")
    void shouldLockStateBeforeDeletingHrConfig()
    {
        SysConfig existing = config(7L, "sign.hr.user-id", "88");
        existing.setConfigType("N");
        when(configMapper.selectConfig(org.mockito.ArgumentMatchers.any())).thenReturn(existing);

        service.deleteConfigByIds(new Long[] { 7L });

        InOrder order = inOrder(configMapper);
        order.verify(configMapper).lockSignHrState();
        order.verify(configMapper).deleteConfigById(7L);
        order.verify(configMapper).syncSignHrPermissions();
    }

    @Test
    @DisplayName("内置参数不能降级为非内置参数")
    void builtInConfigTypeCannotBeDowngraded()
    {
        SysConfig existing = config(7L, "system.safe-setting", "enabled");
        existing.setConfigType("Y");
        SysConfig update = config(7L, "system.safe-setting", "enabled");
        update.setConfigType("N");
        when(configMapper.selectConfigById(7L)).thenReturn(existing);

        assertThatThrownBy(() -> service.updateConfig(update))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("内置参数");

        verify(configMapper, never()).updateConfig(any());
    }

    @Test
    @DisplayName("敏感参数空值保持原值且掩码绝不写入数据库")
    void sensitiveConfigRequiresExplicitReplacement()
    {
        SysConfig existing = config(9L, "sys.mail.password", "stored-secret");
        existing.setConfigType("N");
        SysConfig keep = config(9L, "sys.mail.password", "");
        keep.setConfigType("N");
        when(configMapper.selectConfigById(9L)).thenReturn(existing);
        when(configMapper.updateConfig(keep)).thenReturn(1);

        service.updateConfig(keep);

        assertThat(keep.getConfigValue()).isEqualTo("stored-secret");

        SysConfig masked = config(9L, "sys.mail.password", SysConfigSensitivityPolicy.MASK);
        masked.setConfigType("N");
        masked.setUpdateSensitiveValue(true);
        when(configMapper.selectConfigById(9L)).thenReturn(existing);
        assertThatThrownBy(() -> service.updateConfig(masked))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("掩码");
    }

    @Test
    @DisplayName("通用外部按键读取拒绝敏感参数")
    void externalConfigReadRejectsSensitiveKey()
    {
        assertThatThrownBy(() -> service.selectConfigByKeyForExternal("sys.user.initPassword"))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("敏感参数");
        verify(configMapper, never()).selectConfig(any());
    }

    private SysConfig config(Long id, String key, String value)
    {
        SysConfig config = new SysConfig();
        config.setConfigId(id);
        config.setConfigKey(key);
        config.setConfigValue(value);
        config.setVersion(3);
        return config;
    }

    private SysUser activeUser(Long userId)
    {
        SysUser user = new SysUser();
        user.setUserId(userId);
        user.setStatus("0");
        user.setDelFlag("0");
        return user;
    }
}
