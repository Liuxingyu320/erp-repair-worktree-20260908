package com.erp.system.service.impl;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.sql.Date;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import com.erp.system.api.domain.SysDept;
import com.erp.system.api.domain.SysUser;
import com.erp.system.api.domain.SysUserProfile;
import com.erp.system.domain.SysPost;

@DisplayName("用户档案自动派生服务")
class SysUserProfileDerivationServiceTest
{
    private static final LocalDate TODAY = LocalDate.of(2026, 7, 10);
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-07-10T00:00:00Z"), ZoneId.of("UTC"));

    private final SysUserProfileDerivationService service = new SysUserProfileDerivationService(null, null, CLOCK);

    @Test
    @DisplayName("真实门店路径从门店向上对齐并取公司下一级负责人")
    void deriveRealStorePathFromGroupAnchor()
    {
        List<SysDept> departments = Arrays.asList(
                dept(100L, 0L, "0", "金英灵韵集团", "GROUP", "集团负责人", "0"),
                dept(101L, 100L, "0,100", "金英灵韵", "COMPANY", "公司负责人", "0"),
                dept(102L, 101L, "0,100,101", "浙江区域", "COMPANY", "浙江区域负责人", "0"),
                dept(103L, 102L, "0,100,101,102", "浙江一区", "COMPANY", "浙江一区负责人", "0"),
                dept(104L, 103L, "0,100,101,102,103", "杭州柏悦", "STORE", "门店负责人", "0"));

        SysUserProfile result = service.derive(user(104L), departments, Collections.emptyList(), TODAY);

        assertThat(result.getCompanyName()).isEqualTo("金英灵韵");
        assertThat(result.getDeptLevel1Name()).isNull();
        assertThat(result.getDeptLevel2Name()).isEqualTo("浙江区域");
        assertThat(result.getDeptLevel3Name()).isEqualTo("浙江一区");
        assertThat(result.getStoreName()).isEqualTo("杭州柏悦");
        assertThat(result.getDepartmentSupervisor()).isEqualTo("浙江区域负责人");
        assertThat(result.getDerivedWarnings()).isEmpty();
    }

    @Test
    @DisplayName("门店与公司之间没有部门层时三级部门保持为空")
    void keepAllDepartmentLevelsEmptyForCompanyDirectStore()
    {
        List<SysDept> departments = Arrays.asList(
                dept(1L, 0L, "0", "星河集团", "GROUP", "集团负责人", "0"),
                dept(2L, 1L, "0,1", "星河公司", "COMPANY", "公司负责人", "0"),
                dept(3L, 2L, "0,1,2", "直属门店", "STORE", "门店负责人", "0"));

        SysUserProfile result = service.derive(user(3L), departments, Collections.emptyList(), TODAY);

        assertThat(result.getCompanyName()).isEqualTo("星河公司");
        assertThat(result.getDeptLevel1Name()).isNull();
        assertThat(result.getDeptLevel2Name()).isNull();
        assertThat(result.getDeptLevel3Name()).isNull();
        assertThat(result.getStoreName()).isEqualTo("直属门店");
        assertThat(result.getDepartmentSupervisor()).isEqualTo("公司负责人");
        assertThat(result.getDerivedWarnings()).isEmpty();
    }

    @Test
    @DisplayName("门店与公司之间正好三级时完整对齐")
    void alignAllDepartmentLevelsForThreeLayerStorePath()
    {
        List<SysDept> departments = Arrays.asList(
                dept(1L, 0L, "0", "星河集团", "GROUP", "集团负责人", "0"),
                dept(2L, 1L, "0,1", "星河公司", "COMPANY", "公司负责人", "0"),
                dept(3L, 2L, "0,1,2", "一级部门", "COMPANY", "最高部门负责人", "0"),
                dept(4L, 3L, "0,1,2,3", "二级部门", "COMPANY", "二级负责人", "0"),
                dept(5L, 4L, "0,1,2,3,4", "三级部门", "COMPANY", "三级负责人", "0"),
                dept(6L, 5L, "0,1,2,3,4,5", "标准门店", "STORE", "门店负责人", "0"));

        SysUserProfile result = service.derive(user(6L), departments, Collections.emptyList(), TODAY);

        assertThat(result.getCompanyName()).isEqualTo("星河公司");
        assertThat(result.getDeptLevel1Name()).isEqualTo("一级部门");
        assertThat(result.getDeptLevel2Name()).isEqualTo("二级部门");
        assertThat(result.getDeptLevel3Name()).isEqualTo("三级部门");
        assertThat(result.getStoreName()).isEqualTo("标准门店");
        assertThat(result.getDepartmentSupervisor()).isEqualTo("最高部门负责人");
        assertThat(result.getDerivedWarnings()).isEmpty();
    }

    @Test
    @DisplayName("层级不足时保持后续级别为空且不错位")
    void keepMissingOrganizationLevelsEmpty()
    {
        List<SysDept> departments = Arrays.asList(
                dept(1L, 0L, "0", "星河集团", "GROUP", "集团负责人", "0"),
                dept(2L, 1L, "0,1", "星河公司", "COMPANY", "公司负责人", "0"),
                dept(3L, 2L, "0,1,2", "人事部", "COMPANY", "人事总监", "0"));

        SysUserProfile result = service.derive(user(3L), departments, Collections.emptyList(), TODAY);

        assertThat(result.getCompanyName()).isEqualTo("星河公司");
        assertThat(result.getDeptLevel1Name()).isEqualTo("人事部");
        assertThat(result.getDeptLevel2Name()).isNull();
        assertThat(result.getDeptLevel3Name()).isNull();
        assertThat(result.getStoreName()).isNull();
        assertThat(result.getDepartmentSupervisor()).isEqualTo("人事总监");
        assertThat(result.getDerivedWarnings()).isEmpty();
    }

    @Test
    @DisplayName("归属门店内部部门时从祖先路径识别门店")
    void deriveStoreFromAncestorPath()
    {
        List<SysDept> departments = Arrays.asList(
                dept(1L, 0L, "0", "星河集团", "GROUP", "集团负责人", "0"),
                dept(2L, 1L, "0,1", "星河公司", "COMPANY", "公司负责人", "0"),
                dept(3L, 2L, "0,1,2", "华东区", "COMPANY", "区域主管", "0"),
                dept(4L, 3L, "0,1,2,3", "陆家嘴店", "STORE", "门店店长", "0"),
                dept(5L, 4L, "0,1,2,3,4", "门店运营组", null, "运营组长", "0"));

        SysUserProfile result = service.derive(user(5L), departments, Collections.emptyList(), TODAY);

        assertThat(result.getDeptLevel1Name()).isNull();
        assertThat(result.getDeptLevel2Name()).isNull();
        assertThat(result.getDeptLevel3Name()).isEqualTo("华东区");
        assertThat(result.getStoreName()).isEqualTo("陆家嘴店");
        assertThat(result.getDepartmentSupervisor()).isEqualTo("区域主管");
    }

    @Test
    @DisplayName("仓库叶子节点不会被识别为门店")
    void doNotTreatWarehouseAsStore()
    {
        List<SysDept> departments = Arrays.asList(
                dept(1L, 0L, "0", "星河集团", "GROUP", "集团负责人", "0"),
                dept(2L, 1L, "0,1", "星河公司", "COMPANY", "公司负责人", "0"),
                dept(3L, 2L, "0,1,2", "华东仓", "WAREHOUSE", "仓库主管", "0"));

        SysUserProfile result = service.derive(user(3L), departments, Collections.emptyList(), TODAY);

        assertThat(result.getStoreName()).isNull();
        assertThat(result.getDepartmentSupervisor()).isEqualTo("公司负责人");
        assertThat(result.getDerivedWarnings()).isEmpty();
    }

    @Test
    @DisplayName("公司缺失时清除旧组织文本并返回修复警告")
    void warnAndClearLegacyValuesWhenCompanyIsMissing()
    {
        SysUser user = user(2L);
        user.getProfile().setCompanyName("旧公司");
        user.getProfile().setDeptLevel1Name("旧部门");
        user.getProfile().setStoreName("旧门店");
        List<SysDept> departments = Collections.singletonList(
                dept(1L, 0L, "0", "星河集团", "GROUP", "集团负责人", "0"));

        user.setDeptId(1L);
        SysUserProfile result = service.derive(user, departments, Collections.emptyList(), TODAY);

        assertThat(result.getCompanyName()).isNull();
        assertThat(result.getDeptLevel1Name()).isNull();
        assertThat(result.getStoreName()).isNull();
        assertThat(result.getDerivedWarnings())
                .containsExactly(SysUserProfileDerivationService.COMPANY_WARNING);
    }

    @Test
    @DisplayName("路径缺少集团锚点时返回公司识别警告")
    void warnWhenGroupAnchorIsMissing()
    {
        List<SysDept> departments = Arrays.asList(
                dept(1L, 0L, "0", "星河公司", "COMPANY", "公司负责人", "0"),
                dept(2L, 1L, "0,1", "人事部", "COMPANY", "人事总监", "0"));

        SysUserProfile result = service.derive(user(2L), departments, Collections.emptyList(), TODAY);

        assertThat(result.getCompanyName()).isNull();
        assertThat(result.getDerivedWarnings())
                .containsExactly(SysUserProfileDerivationService.COMPANY_WARNING);
    }

    @Test
    @DisplayName("归属部门停用时返回组织修复警告")
    void warnWhenSelectedDepartmentIsDisabled()
    {
        List<SysDept> departments = Arrays.asList(
                dept(1L, 0L, "0", "星河集团", "GROUP", "集团负责人", "0"),
                dept(2L, 1L, "0,1", "星河公司", "COMPANY", "公司负责人", "0"),
                dept(3L, 2L, "0,1,2", "停用部门", "COMPANY", "部门负责人", "1"));

        SysUserProfile result = service.derive(user(3L), departments, Collections.emptyList(), TODAY);

        assertThat(result.getCompanyName()).isNull();
        assertThat(result.getDerivedWarnings())
                .containsExactly(SysUserProfileDerivationService.ORGANIZATION_WARNING);
    }

    @Test
    @DisplayName("祖先部门停用时返回组织修复警告")
    void warnWhenAncestorDepartmentIsDisabled()
    {
        List<SysDept> departments = Arrays.asList(
                dept(1L, 0L, "0", "星河集团", "GROUP", "集团负责人", "0"),
                dept(2L, 1L, "0,1", "停用公司", "COMPANY", "公司负责人", "1"),
                dept(3L, 2L, "0,1,2", "人事部", "COMPANY", "人事总监", "0"));

        SysUserProfile result = service.derive(user(3L), departments, Collections.emptyList(), TODAY);

        assertThat(result.getCompanyName()).isNull();
        assertThat(result.getDerivedWarnings())
                .containsExactly(SysUserProfileDerivationService.ORGANIZATION_WARNING);
    }

    @Test
    @DisplayName("祖先标记包含非法编号时返回组织修复警告")
    void warnWhenAncestorTokenIsMalformed()
    {
        List<SysDept> departments = Arrays.asList(
                dept(1L, 0L, "0", "星河集团", "GROUP", "集团负责人", "0"),
                dept(2L, 1L, "0,1,bad", "错误部门", "COMPANY", "部门负责人", "0"));

        SysUserProfile result = service.derive(user(2L), departments, Collections.emptyList(), TODAY);

        assertThat(result.getCompanyName()).isNull();
        assertThat(result.getDerivedWarnings())
                .containsExactly(SysUserProfileDerivationService.ORGANIZATION_WARNING);
    }

    @Test
    @DisplayName("祖先字符串与父子关系断裂时返回修复警告")
    void warnWhenAncestorChainIsBroken()
    {
        List<SysDept> departments = Arrays.asList(
                dept(1L, 0L, "0", "星河集团", "GROUP", "集团负责人", "0"),
                dept(2L, 1L, "0,1", "星河公司", "COMPANY", "公司负责人", "0"),
                dept(3L, 99L, "0,1,2", "错误部门", null, "", "0"));

        SysUserProfile result = service.derive(user(3L), departments, Collections.emptyList(), TODAY);

        assertThat(result.getCompanyName()).isNull();
        assertThat(result.getDerivedWarnings()).containsExactly("组织结构待修复");
    }

    @Test
    @DisplayName("公司和门店之间超过三级时显示最近三级并返回层级警告")
    void warnWhenStorePathExceedsThreeDepartmentLevels()
    {
        List<SysDept> departments = Arrays.asList(
                dept(1L, 0L, "0", "星河集团", "GROUP", "集团负责人", "0"),
                dept(2L, 1L, "0,1", "星河公司", "COMPANY", "公司负责人", "0"),
                dept(3L, 2L, "0,1,2", "一级", "COMPANY", "最高部门负责人", "0"),
                dept(4L, 3L, "0,1,2,3", "二级", "COMPANY", "", "0"),
                dept(5L, 4L, "0,1,2,3,4", "三级", "COMPANY", "", "0"),
                dept(6L, 5L, "0,1,2,3,4,5", "四级", "COMPANY", "", "0"),
                dept(7L, 6L, "0,1,2,3,4,5,6", "门店", "STORE", "门店负责人", "0"));

        SysUserProfile result = service.derive(user(7L), departments, Collections.emptyList(), TODAY);

        assertThat(result.getCompanyName()).isEqualTo("星河公司");
        assertThat(result.getDeptLevel1Name()).isEqualTo("二级");
        assertThat(result.getDeptLevel2Name()).isEqualTo("三级");
        assertThat(result.getDeptLevel3Name()).isEqualTo("四级");
        assertThat(result.getStoreName()).isEqualTo("门店");
        assertThat(result.getDepartmentSupervisor()).isEqualTo("最高部门负责人");
        assertThat(result.getDerivedWarnings())
                .containsExactly(SysUserProfileDerivationService.HIERARCHY_WARNING);
    }

    @Test
    @DisplayName("非门店路径超过三级时保留公司向下的前三层")
    void warnWhenNonStorePathExceedsThreeDepartmentLevels()
    {
        List<SysDept> departments = Arrays.asList(
                dept(1L, 0L, "0", "星河集团", "GROUP", "集团负责人", "0"),
                dept(2L, 1L, "0,1", "星河公司", "COMPANY", "公司负责人", "0"),
                dept(3L, 2L, "0,1,2", "一级", "COMPANY", "最高部门负责人", "0"),
                dept(4L, 3L, "0,1,2,3", "二级", "COMPANY", "", "0"),
                dept(5L, 4L, "0,1,2,3,4", "三级", "COMPANY", "", "0"),
                dept(6L, 5L, "0,1,2,3,4,5", "四级", "COMPANY", "", "0"));

        SysUserProfile result = service.derive(user(6L), departments, Collections.emptyList(), TODAY);

        assertThat(result.getCompanyName()).isEqualTo("星河公司");
        assertThat(result.getDeptLevel1Name()).isEqualTo("一级");
        assertThat(result.getDeptLevel2Name()).isEqualTo("二级");
        assertThat(result.getDeptLevel3Name()).isEqualTo("三级");
        assertThat(result.getStoreName()).isNull();
        assertThat(result.getDerivedWarnings())
                .containsExactly(SysUserProfileDerivationService.HIERARCHY_WARNING);
    }

    @Test
    @DisplayName("公司下一级部门未配置负责人时返回主管警告")
    void warnWhenHighestDepartmentLeaderIsMissing()
    {
        List<SysDept> departments = Arrays.asList(
                dept(1L, 0L, "0", "星河集团", "GROUP", "集团负责人", "0"),
                dept(2L, 1L, "0,1", "星河公司", "COMPANY", "公司负责人", "0"),
                dept(3L, 2L, "0,1,2", "华东区域", "COMPANY", "", "0"),
                dept(4L, 3L, "0,1,2,3", "浙江一区", "COMPANY", "分区负责人", "0"));

        SysUserProfile result = service.derive(user(4L), departments, Collections.emptyList(), TODAY);

        assertThat(result.getDepartmentSupervisor()).isNull();
        assertThat(result.getDerivedWarnings())
                .containsExactly(SysUserProfileDerivationService.SUPERVISOR_WARNING);
    }

    @Test
    @DisplayName("仅有历史负责人姓名但没有稳定用户ID时不参与主管派生")
    void legacyLeaderTextWithoutStableUserIdShouldNotBeDerived()
    {
        List<SysDept> departments = managedDepartmentPath();
        departments.get(2).setLeaderUserId(null);

        SysUserProfile result = service.derive(user(3L), departments, Collections.emptyList(), TODAY);

        assertThat(result.getDepartmentSupervisor()).isNull();
        assertThat(result.getDerivedWarnings())
                .containsExactly(SysUserProfileDerivationService.SUPERVISOR_WARNING);
    }

    @Test
    @DisplayName("已选启用岗位按排序和名称稳定拼接")
    void deriveSelectedActivePositionNames()
    {
        SysUser user = user(3L);
        user.setPostIds(new Long[] { 3L, 2L, 1L });
        List<SysPost> posts = Arrays.asList(
                post(1L, "培训师", 2, "0"),
                post(2L, "店长", 1, "0"),
                post(3L, "停用岗位", 0, "1"),
                post(4L, "未选岗位", 0, "0"));

        SysUserProfile result = service.derive(user, managedDepartmentPath(), posts, TODAY);

        assertThat(result.getPositionNames()).isEqualTo("店长、培训师");
    }

    @Test
    @DisplayName("工龄和司龄按指定日期计算完整年月")
    void deriveWorkAndCompanyYearsAtFixedDate()
    {
        SysUser user = user(3L);
        user.getProfile().setWorkStartDate(Date.valueOf("2020-01-10"));
        user.getProfile().setEntryDate(Date.valueOf("2023-04-10"));
        user.getProfile().setContractStartDate(Date.valueOf("2025-01-01"));
        user.getProfile().setContractEndDate(Date.valueOf("2027-06-30"));

        SysUserProfile result = service.derive(user, managedDepartmentPath(), Collections.emptyList(), TODAY);

        assertThat(result.getWorkYears()).isEqualTo("6年6个月");
        assertThat(result.getCompanyYears()).isEqualTo("3年3个月");
        assertThat(result.getContractTerm()).isEqualTo("2年5个月");
        assertThat(result.getDerivedWarnings()).isEmpty();
    }

    @Test
    @DisplayName("离职员工司龄截止到离职日期")
    void stopCompanyYearsAtLeaveDate()
    {
        SysUser user = user(3L);
        user.getProfile().setEmployeeStatus("离职");
        user.getProfile().setEntryDate(Date.valueOf("2020-01-15"));
        user.getProfile().setLeaveDate(Date.valueOf("2024-06-14"));

        SysUserProfile result = service.derive(user, managedDepartmentPath(), Collections.emptyList(), TODAY);

        assertThat(result.getCompanyYears()).isEqualTo("4年4个月");
    }

    @Test
    @DisplayName("未来参加工作日期和倒置入离职日期不输出负数年限")
    void warnAndClearDurationsForInvalidDates()
    {
        SysUser user = user(3L);
        user.getProfile().setEmployeeStatus("离职");
        user.getProfile().setWorkStartDate(Date.valueOf("2027-01-01"));
        user.getProfile().setEntryDate(Date.valueOf("2025-01-01"));
        user.getProfile().setLeaveDate(Date.valueOf("2024-01-01"));

        SysUserProfile result = service.derive(user, managedDepartmentPath(), Collections.emptyList(), TODAY);

        assertThat(result.getWorkYears()).isNull();
        assertThat(result.getCompanyYears()).isNull();
        assertThat(result.getDerivedWarnings()).containsExactly("档案日期异常");
    }

    private static List<SysDept> managedDepartmentPath()
    {
        return Arrays.asList(
                dept(1L, 0L, "0", "星河集团", "GROUP", "集团负责人", "0"),
                dept(2L, 1L, "0,1", "星河公司", "COMPANY", "公司负责人", "0"),
                dept(3L, 2L, "0,1,2", "人事部", "COMPANY", "人事总监", "0"));
    }

    private static SysUser user(Long deptId)
    {
        SysUser user = new SysUser();
        user.setDeptId(deptId);
        user.setProfile(new SysUserProfile());
        return user;
    }

    private static SysDept dept(Long id, Long parentId, String ancestors, String name, String type,
            String leader, String status)
    {
        SysDept dept = new SysDept();
        dept.setDeptId(id);
        dept.setParentId(parentId);
        dept.setAncestors(ancestors);
        dept.setDeptName(name);
        dept.setDeptType(type);
        dept.setLeader(leader);
        if (leader != null && !leader.isBlank())
        {
            dept.setLeaderUserId(id + 1000L);
        }
        dept.setStatus(status);
        dept.setDelFlag("0");
        return dept;
    }

    private static SysPost post(Long id, String name, Integer sort, String status)
    {
        SysPost post = new SysPost();
        post.setPostId(id);
        post.setPostName(name);
        post.setPostSort(sort);
        post.setStatus(status);
        return post;
    }
}
