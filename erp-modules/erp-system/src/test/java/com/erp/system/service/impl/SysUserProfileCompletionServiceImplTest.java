package com.erp.system.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Collections;
import java.util.Date;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import com.erp.common.core.exception.ServiceException;
import com.erp.common.core.utils.DateUtils;
import com.erp.system.api.domain.SysUser;
import com.erp.system.api.domain.SysUserProfile;
import com.erp.system.config.ProfileCompletionProperties;
import com.erp.system.domain.vo.SysProfileCompletionFieldVo;
import com.erp.system.domain.vo.SysProfileCompletionRequest;
import com.erp.system.domain.vo.SysProfileCompletionVo;
import com.erp.system.mapper.SysUserMapper;
import com.erp.system.mapper.SysUserProfileMapper;
import com.erp.system.service.SigningProfileNormalizer;

@ExtendWith(MockitoExtension.class)
@DisplayName("登录员工资料完整度服务")
class SysUserProfileCompletionServiceImplTest
{
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-07-10T00:00:00Z"), ZoneId.of("UTC"));

    @Mock
    private SysUserMapper userMapper;

    @Mock
    private SysUserProfileMapper profileMapper;

    private SysUserProfileCompletionServiceImpl service;

    @BeforeEach
    void setUp()
    {
        service = new SysUserProfileCompletionServiceImpl(userMapper, profileMapper, CLOCK);
    }

    @Test
    @DisplayName("admin超级管理员始终跳过资料拦截")
    void adminShouldAlwaysBeComplete()
    {
        SysProfileCompletionVo result = service.evaluate(1L);

        assertThat(result.isCompletionRequired()).isFalse();
        assertThat(result.getMissingFields()).isEmpty();
        verify(userMapper, never()).selectUserById(any());
    }

    @Test
    @DisplayName("即使环境误配置关闭门禁，普通账号仍必须检查资料")
    void disabledGateShouldNotBypassNonAdminProfileCheck()
    {
        ProfileCompletionProperties properties = new ProfileCompletionProperties();
        properties.setGateEnabled(false);
        service = new SysUserProfileCompletionServiceImpl(
                userMapper, profileMapper, CLOCK, new SigningProfileNormalizer(), properties);
        SysUser user = new SysUser(20L);
        when(userMapper.selectUserById(20L)).thenReturn(user);
        when(profileMapper.selectUserProfileByUserId(20L)).thenReturn(null);

        SysProfileCompletionVo result = service.evaluate(20L);

        assertThat(result.isCompletionRequired()).isTrue();
        assertThat(result.getMissingFields()).hasSize(7);
        verify(userMapper).selectUserById(20L);
        verify(profileMapper).selectUserProfileByUserId(20L);
    }

    @Test
    @DisplayName("普通账号没有基础资料和档案时只返回七项核心缺失字段")
    void nonAdminWithoutProfileShouldRequireAllFields()
    {
        SysUser user = new SysUser(20L);
        when(userMapper.selectUserById(20L)).thenReturn(user);
        when(profileMapper.selectUserProfileByUserId(20L)).thenReturn(null);

        SysProfileCompletionVo result = service.evaluate(20L);

        assertThat(result.isCompletionRequired()).isTrue();
        assertThat(result.getMissingFields())
                .extracting(SysProfileCompletionFieldVo::getKey)
                .containsExactly(
                        "nickName", "phonenumber", "sex", "idType", "idNumber",
                        "registeredResidence", "currentAddress");
    }

    @Test
    @DisplayName("七项核心个人资料完整时允许继续选店")
    void completeUserShouldPass()
    {
        SysUser user = completeUser(20L);
        SysUserProfile profile = completeProfile(20L);
        when(userMapper.selectUserById(20L)).thenReturn(user);
        when(profileMapper.selectUserProfileByUserId(20L)).thenReturn(profile);

        SysProfileCompletionVo result = service.evaluate(20L);

        assertThat(result.isCompletionRequired()).isFalse();
        assertThat(result.getMissingFields()).isEmpty();
        assertThat(result.getCompletedDisplayValues())
                .containsEntry("idNumber", "330***********1234")
                .doesNotContainKey("bankAccount");
    }

    @ParameterizedTest(name = "缺失字段 {0}")
    @MethodSource("requiredFieldKeys")
    @DisplayName("七项核心字段逐项缺失时都能被识别")
    void eachRequiredFieldShouldBeDetected(String fieldKey)
    {
        SysUser user = completeUser(20L);
        SysUserProfile profile = completeProfile(20L);
        clearRequiredField(fieldKey, user, profile);
        when(userMapper.selectUserById(20L)).thenReturn(user);
        when(profileMapper.selectUserProfileByUserId(20L)).thenReturn(profile);

        SysProfileCompletionVo result = service.evaluate(20L);

        assertThat(result.getMissingFields())
                .extracting(SysProfileCompletionFieldVo::getKey)
                .contains(fieldKey);
    }

    @Test
    @DisplayName("已存但格式错误的资料仍视为缺失并允许员工修正")
    void invalidStoredValuesShouldRemainIncomplete()
    {
        SysUser user = completeUser(20L);
        user.setPhonenumber("123");
        SysUserProfile profile = completeProfile(20L);
        profile.setBirthDate(date("2027-01-01"));
        profile.setIdNumber("11010119900101123X");
        profile.setEmergencyContactPhone("ABC");
        profile.setBankAccount("ABC");
        when(userMapper.selectUserById(20L)).thenReturn(user);
        when(profileMapper.selectUserProfileByUserId(20L)).thenReturn(profile);

        SysProfileCompletionVo result = service.evaluate(20L);

        assertThat(result.getMissingFields())
                .extracting(SysProfileCompletionFieldVo::getKey)
                .containsExactly("phonenumber", "idNumber");
        assertThat(result.getCompletedDisplayValues())
                .doesNotContainKeys("idNumber", "bankAccount");
    }

    @Test
    @DisplayName("出生日期婚姻状况和民族缺失时不阻塞登录")
    void optionalPersonalFieldsShouldNotBlockLogin()
    {
        SysUser user = completeUser(20L);
        SysUserProfile profile = completeProfile(20L);
        profile.setBirthDate(null);
        profile.setMaritalStatus(null);
        profile.setEthnicity(null);
        when(userMapper.selectUserById(20L)).thenReturn(user);
        when(profileMapper.selectUserProfileByUserId(20L)).thenReturn(profile);

        SysProfileCompletionVo result = service.evaluate(20L);

        assertThat(result.isCompletionRequired()).isFalse();
        assertThat(result.getMissingFields()).isEmpty();
    }

    @Test
    @DisplayName("历史可选字段异常时仍可只保存七项核心资料")
    void staleInvalidOptionalFieldsShouldNotBlockCoreSave()
    {
        SysUser user = completeUser(20L);
        SysUserProfile profile = completeProfile(20L);
        profile.setBirthDate(date("2027-01-01"));
        profile.setMaritalStatus(String.join("", Collections.nCopies(33, "婚")));
        profile.setEthnicity(String.join("", Collections.nCopies(65, "族")));
        when(userMapper.selectUserById(20L)).thenReturn(user);
        when(profileMapper.selectUserProfileByUserId(20L)).thenReturn(profile);
        when(userMapper.updateHrEmployeeProfileUser(any(SysUser.class))).thenReturn(1);
        when(profileMapper.updateProfileCompletionFields(any(SysUserProfile.class))).thenReturn(1);

        SysProfileCompletionRequest request = completeRequest();
        request.setBirthDate(null);
        request.setMaritalStatus(null);
        request.setEthnicity(null);

        SysProfileCompletionVo result = service.save(20L, request, "employee20");

        assertThat(result.isCompletionRequired()).isFalse();
        verify(profileMapper).updateProfileCompletionFields(any(SysUserProfile.class));
    }

    @Test
    @DisplayName("保存时仅合并个人白名单字段并保留已有HR档案字段")
    void saveShouldMergeAllowlistedFieldsAndPreserveHrProfile()
    {
        SysUser user = new SysUser(20L);
        user.setUserName("employee20");
        user.setNickName("旧姓名");
        user.setPhonenumber("13800000000");
        user.setSex("2");
        user.setDeptId(900L);
        user.setStatus("0");
        SysUserProfile existing = new SysUserProfile();
        existing.setProfileId(88L);
        existing.setUserId(20L);
        existing.setEmployeeStatus("正式");
        existing.setEntryDate(date("2025-01-01"));
        existing.setContractType("无固定期限劳动合同");
        existing.setSocialType("本地社保");
        existing.setSocialSecurityLocation("上海市");
        existing.setEmergencyContact("原紧急联系人");
        existing.setEmergencyContactRelation("家属");
        existing.setEmergencyContactPhone("13900000000");
        existing.setBankName("原开户银行");
        existing.setBankAccount("622202020202025678");
        when(userMapper.selectUserById(20L)).thenReturn(user);
        when(profileMapper.selectUserProfileByUserId(20L)).thenReturn(existing);
        when(userMapper.updateHrEmployeeProfileUser(any(SysUser.class))).thenReturn(1);
        when(profileMapper.updateProfileCompletionFields(any(SysUserProfile.class))).thenReturn(1);

        SysProfileCompletionRequest request = completeRequest();
        request.setEmergencyContact("不应写入的联系人");
        request.setBankAccount("ABC");
        SysProfileCompletionVo result = service.save(20L, request, "employee20");

        ArgumentCaptor<SysUser> userCaptor = ArgumentCaptor.forClass(SysUser.class);
        verify(userMapper).updateHrEmployeeProfileUser(userCaptor.capture());
        assertThat(userCaptor.getValue().getNickName()).isEqualTo("张三");
        assertThat(userCaptor.getValue().getPhonenumber()).isEqualTo("13800000001");
        assertThat(userCaptor.getValue().getSex()).isEqualTo("0");
        assertThat(userCaptor.getValue().getDeptId()).isNull();
        assertThat(userCaptor.getValue().getStatus()).isNull();

        ArgumentCaptor<SysUserProfile> profileCaptor = ArgumentCaptor.forClass(SysUserProfile.class);
        verify(profileMapper).updateProfileCompletionFields(profileCaptor.capture());
        SysUserProfile saved = profileCaptor.getValue();
        assertThat(saved.getUserId()).isEqualTo(20L);
        assertThat(saved.getEmployeeStatus()).isNull();
        assertThat(saved.getEntryDate()).isNull();
        assertThat(saved.getIdNumber()).isEqualTo("330102199001011234");
        assertThat(saved.getEmergencyContact()).isNull();
        assertThat(saved.getBankAccount()).isNull();
        assertThat(existing.getEmergencyContact()).isEqualTo("原紧急联系人");
        assertThat(existing.getBankAccount()).isEqualTo("622202020202025678");
        assertThat(existing.getContractType()).isEqualTo("LABOR_CONTRACT");
        assertThat(existing.getContractTerm()).isEqualTo("OPEN_ENDED");
        assertThat(existing.getSocialType()).isEqualTo("SOCIAL_INSURED");
        assertThat(existing.getSocialSecurityLocation()).isEqualTo("上海市");
        verify(profileMapper, never()).updateUserProfile(any(SysUserProfile.class));
        assertThat(result.isCompletionRequired()).isFalse();
    }

    @Test
    @DisplayName("首次保存时创建员工档案")
    void saveShouldCreateMissingProfile()
    {
        SysUser user = new SysUser(20L);
        user.setUserName("employee20");
        when(userMapper.selectUserById(20L)).thenReturn(user);
        when(profileMapper.selectUserProfileByUserId(20L)).thenReturn(null);
        when(userMapper.updateHrEmployeeProfileUser(any(SysUser.class))).thenReturn(1);
        when(profileMapper.insertUserProfile(any(SysUserProfile.class))).thenReturn(1);

        SysProfileCompletionRequest request = completeRequest();
        request.setEmergencyContact("不应写入的联系人");
        request.setBankAccount("ABC");
        service.save(20L, request, "employee20");

        ArgumentCaptor<SysUserProfile> captor = ArgumentCaptor.forClass(SysUserProfile.class);
        verify(profileMapper).insertUserProfile(captor.capture());
        assertThat(captor.getValue().getUserId()).isEqualTo(20L);
        assertThat(captor.getValue().getCreateBy()).isEqualTo("employee20");
        assertThat(captor.getValue().getEmergencyContact()).isNull();
        assertThat(captor.getValue().getBankAccount()).isNull();
    }

    @Test
    @DisplayName("基础用户资料未写入时不继续保存员工档案")
    void saveShouldStopWhenBaseUserUpdateFails()
    {
        SysUser user = completeUser(20L);
        when(userMapper.selectUserById(20L)).thenReturn(user);
        when(profileMapper.selectUserProfileByUserId(20L)).thenReturn(completeProfile(20L));
        when(userMapper.updateHrEmployeeProfileUser(any(SysUser.class))).thenReturn(0);

        assertThatThrownBy(() -> service.save(20L, completeRequest(), "employee20"))
                .isInstanceOf(ServiceException.class)
                .hasMessage("保存用户基础资料失败");
        verify(profileMapper, never()).updateProfileCompletionFields(any(SysUserProfile.class));
    }

    @Test
    @DisplayName("保存拒绝重复手机号")
    void saveShouldRejectDuplicatePhone()
    {
        SysUser user = completeUser(20L);
        SysUser duplicate = new SysUser(30L);
        when(userMapper.selectUserById(20L)).thenReturn(user);
        when(profileMapper.selectUserProfileByUserId(20L)).thenReturn(completeProfile(20L));
        when(userMapper.checkPhoneUnique("13800000001")).thenReturn(duplicate);

        assertThatThrownBy(() -> service.save(20L, completeRequest(), "employee20"))
                .isInstanceOf(ServiceException.class)
                .hasMessage("手机号已被其他账号使用");
    }

    @Test
    @DisplayName("保存拒绝未知性别、未来生日和无效证件")
    void saveShouldRejectInvalidPersonalFields()
    {
        SysUser user = completeUser(20L);
        when(userMapper.selectUserById(20L)).thenReturn(user);
        when(profileMapper.selectUserProfileByUserId(20L)).thenReturn(completeProfile(20L));

        SysProfileCompletionRequest request = completeRequest();
        request.setSex("2");
        SysProfileCompletionRequest invalidSex = request;
        assertThatThrownBy(() -> service.save(20L, invalidSex, "employee20"))
                .isInstanceOf(ServiceException.class)
                .hasMessage("请选择性别");

        request = completeRequest();
        request.setBirthDate(date("2027-01-01"));
        SysProfileCompletionRequest futureBirth = request;
        assertThatThrownBy(() -> service.save(20L, futureBirth, "employee20"))
                .isInstanceOf(ServiceException.class)
                .hasMessage("出生日期不能晚于今天");

        request = completeRequest();
        request.setIdNumber("11010119900101123X");
        SysProfileCompletionRequest invalidId = request;
        assertThatThrownBy(() -> service.save(20L, invalidId, "employee20"))
                .isInstanceOf(ServiceException.class)
                .hasMessage("请输入正确的居民身份证号码");
    }

    private static SysUser completeUser(Long userId)
    {
        SysUser user = new SysUser(userId);
        user.setUserName("employee" + userId);
        user.setNickName("张三");
        user.setPhonenumber("13800000001");
        user.setSex("0");
        return user;
    }

    private static SysUserProfile completeProfile(Long userId)
    {
        SysUserProfile profile = new SysUserProfile();
        profile.setUserId(userId);
        profile.setBirthDate(date("1990-01-01"));
        profile.setIdType("居民身份证");
        profile.setIdNumber("330102199001011234");
        profile.setRegisteredResidence("浙江省杭州市");
        profile.setCurrentAddress("浙江省杭州市西湖区");
        profile.setMaritalStatus("未婚");
        profile.setEthnicity("汉族");
        return profile;
    }

    private static SysProfileCompletionRequest completeRequest()
    {
        SysProfileCompletionRequest request = new SysProfileCompletionRequest();
        request.setNickName(" 张三 ");
        request.setPhonenumber("13800000001");
        request.setSex("0");
        request.setBirthDate(date("1990-01-01"));
        request.setIdType("居民身份证");
        request.setIdNumber("330102199001011234");
        request.setRegisteredResidence("浙江省杭州市");
        request.setCurrentAddress("浙江省杭州市西湖区");
        request.setMaritalStatus("未婚");
        request.setEthnicity("汉族");
        return request;
    }

    private static Date date(String value)
    {
        return DateUtils.parseDate(value);
    }

    private static Stream<String> requiredFieldKeys()
    {
        return Stream.of(
                "nickName", "phonenumber", "sex", "idType", "idNumber",
                "registeredResidence", "currentAddress");
    }

    private static void clearRequiredField(String fieldKey, SysUser user, SysUserProfile profile)
    {
        switch (fieldKey)
        {
            case "nickName" -> user.setNickName(null);
            case "phonenumber" -> user.setPhonenumber(null);
            case "sex" -> user.setSex("2");
            case "idType" -> profile.setIdType(null);
            case "idNumber" -> profile.setIdNumber(null);
            case "registeredResidence" -> profile.setRegisteredResidence(null);
            case "currentAddress" -> profile.setCurrentAddress(null);
            default -> throw new IllegalArgumentException("未知字段: " + fieldKey);
        }
    }
}
