package com.erp.system.api.domain;

import java.util.Date;
import java.util.List;
import jakarta.validation.constraints.*;
import com.fasterxml.jackson.annotation.JsonFormat;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import com.erp.common.core.annotation.Excel;
import com.erp.common.core.annotation.Excel.ColumnType;
import com.erp.common.core.annotation.Excel.Type;
import com.erp.common.core.constant.UserConstants;
import com.erp.common.core.annotation.Excels;
import com.erp.common.core.web.domain.BaseEntity;
import com.erp.common.core.xss.Xss;

/**
 * 用户对象 sys_user
 * 
 * @author erp
 */
public class SysUser extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    public static final String CREDENTIAL_STATE_ACTIVE = "ACTIVE";

    public static final String CREDENTIAL_STATE_TEMPORARY = "TEMPORARY";

    public static final String CREDENTIAL_STATE_CHANGE_REQUIRED = "CHANGE_REQUIRED";

    /** 用户ID */
    @Excel(name = "用户编号", type = Type.EXPORT, cellType = ColumnType.NUMERIC, prompt = "用户编号")
    private Long userId;

    /** 部门ID */
    @Excel(name = "部门编号", type = Type.IMPORT)
    private Long deptId;

    /** 用户账号 */
    @Excel(name = "用户账号")
    private String userName;

    /** 姓名 */
    @Excel(name = "姓名")
    private String nickName;

    /** 邮箱 */
    @Excel(name = "邮箱")
    private String email;

    /** 手机号 */
    @Excel(name = "手机号", cellType = ColumnType.TEXT)
    private String phonenumber;

    /** 性别 */
    @Excel(name = "性别", readConverterExp = "0=男,1=女,2=未知")
    private String sex;

    /** 用户头像 */
    private String avatar;

    /** 密码 */
    private String password;

    /** 账号状态（0正常 1停用） */
    @Excel(name = "账号状态", readConverterExp = "0=正常,1=停用")
    private String status;

    /** 删除标志（0代表存在 2代表删除） */
    private String delFlag;

    /** 备注 */
    @Excel(name = "备注")
    private String remark;

    /** 最后登录IP */
    @Excel(name = "最后登录IP", type = Type.EXPORT)
    private String loginIp;

    /** 最后登录时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Excel(name = "最后登录时间", width = 30, dateFormat = "yyyy-MM-dd HH:mm:ss", type = Type.EXPORT)
    private Date loginDate;

    /** 密码最后更新时间 */
    private Date pwdUpdateDate;

    /** 是否必须修改密码（0否 1是），保留用于兼容旧版凭据流程 */
    private String mustChangePassword;

    /** 凭据状态：ACTIVE、TEMPORARY、CHANGE_REQUIRED */
    private String credentialState;

    /** 临时密码失效时间，仅 TEMPORARY 状态使用 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date temporaryPasswordExpiresAt;

    /** 员工档案 */
    @Excels({
        @Excel(name = "工号", targetAttr = "employeeNo"),
        @Excel(name = "所属公司", targetAttr = "companyName"),
        @Excel(name = "1级部门", targetAttr = "deptLevel1Name"),
        @Excel(name = "2级部门", targetAttr = "deptLevel2Name"),
        @Excel(name = "3级部门", targetAttr = "deptLevel3Name"),
        @Excel(name = "4级门店", targetAttr = "storeName"),
        @Excel(name = "职位", targetAttr = "positionNames"),
        @Excel(name = "职级", targetAttr = "jobGrade"),
        @Excel(name = "部门主管", targetAttr = "departmentSupervisor"),
        @Excel(name = "直属主管", targetAttr = "directSupervisor"),
        @Excel(name = "员工状态", targetAttr = "employeeStatus"),
        @Excel(name = "人员类别", targetAttr = "employeeCategory"),
        @Excel(name = "出生日期", targetAttr = "birthDate", width = 30, dateFormat = "yyyy-MM-dd"),
        @Excel(name = "证件类型", targetAttr = "idType"),
        @Excel(name = "证件号码", targetAttr = "idNumber", cellType = ColumnType.TEXT),
        @Excel(name = "血型", targetAttr = "bloodType"),
        @Excel(name = "户口所在地", targetAttr = "registeredResidence"),
        @Excel(name = "现居住地址", targetAttr = "currentAddress"),
        @Excel(name = "第一学历", targetAttr = "firstEducation"),
        @Excel(name = "第一学位", targetAttr = "firstDegree"),
        @Excel(name = "毕业时间", targetAttr = "firstGraduationDate", width = 30, dateFormat = "yyyy-MM-dd"),
        @Excel(name = "第一学历毕业学校", targetAttr = "firstGraduationSchool"),
        @Excel(name = "第一学历所学专业", targetAttr = "firstMajor"),
        @Excel(name = "最高学历", targetAttr = "highestEducation"),
        @Excel(name = "最高学位", targetAttr = "highestDegree"),
        @Excel(name = "最高学历毕业时间", targetAttr = "highestGraduationDate", width = 30, dateFormat = "yyyy-MM-dd"),
        @Excel(name = "最高学历毕业学校", targetAttr = "highestGraduationSchool"),
        @Excel(name = "最高学历所学专业", targetAttr = "highestMajor"),
        @Excel(name = "政治面貌", targetAttr = "politicalStatus"),
        @Excel(name = "婚姻状况", targetAttr = "maritalStatus"),
        @Excel(name = "国籍", targetAttr = "nationality"),
        @Excel(name = "是否外籍", targetAttr = "foreignNationalFlag"),
        @Excel(name = "民族", targetAttr = "ethnicity"),
        @Excel(name = "健康状况", targetAttr = "healthStatus"),
        @Excel(name = "紧急联系人", targetAttr = "emergencyContact"),
        @Excel(name = "与紧急联系人关系", targetAttr = "emergencyContactRelation"),
        @Excel(name = "紧急联系人电话", targetAttr = "emergencyContactPhone", cellType = ColumnType.TEXT),
        @Excel(name = "招聘渠道", targetAttr = "recruitmentChannel"),
        @Excel(name = "办公电话", targetAttr = "officePhone", cellType = ColumnType.TEXT),
        @Excel(name = "参加工作时间", targetAttr = "workStartDate", width = 30, dateFormat = "yyyy-MM-dd"),
        @Excel(name = "工龄", targetAttr = "workYears"),
        @Excel(name = "入职时间", targetAttr = "entryDate", width = 30, dateFormat = "yyyy-MM-dd"),
        @Excel(name = "试用期", targetAttr = "probationPeriod"),
        @Excel(name = "计划转正日期", targetAttr = "plannedRegularizationDate", width = 30, dateFormat = "yyyy-MM-dd"),
        @Excel(name = "实际转正日期", targetAttr = "actualRegularizationDate", width = 30, dateFormat = "yyyy-MM-dd"),
        @Excel(name = "司龄", targetAttr = "companyYears"),
        @Excel(name = "本岗位任职日期", targetAttr = "currentPositionStartDate", width = 30, dateFormat = "yyyy-MM-dd"),
        @Excel(name = "现合同起始日", targetAttr = "contractStartDate", width = 30, dateFormat = "yyyy-MM-dd"),
        @Excel(name = "现合同到期日", targetAttr = "contractEndDate", width = 30, dateFormat = "yyyy-MM-dd"),
        @Excel(name = "合同类型", targetAttr = "contractType"),
        @Excel(name = "合同期限", targetAttr = "contractTerm"),
        @Excel(name = "续签次数", targetAttr = "renewalCount", cellType = ColumnType.NUMERIC),
        @Excel(name = "工作所在地", targetAttr = "workLocation"),
        @Excel(name = "工作所在城市级别", targetAttr = "workCityLevel"),
        @Excel(name = "考勤方式", targetAttr = "attendanceMethod"),
        @Excel(name = "户口性质", targetAttr = "householdType"),
        @Excel(name = "社保类型", targetAttr = "socialType"),
        @Excel(name = "社保缴纳地", targetAttr = "socialSecurityLocation"),
        @Excel(name = "公积金缴纳地", targetAttr = "housingFundLocation"),
        @Excel(name = "离职时间", targetAttr = "leaveDate", width = 30, dateFormat = "yyyy-MM-dd"),
        @Excel(name = "开户银行", targetAttr = "bankName"),
        @Excel(name = "银行卡号", targetAttr = "bankAccount", cellType = ColumnType.TEXT),
        @Excel(name = "法人单位", targetAttr = "legalEntity")
    })
    private SysUserProfile profile;

    /** 部门对象 */
    @Excels({
        @Excel(name = "部门名称", targetAttr = "deptName", type = Type.EXPORT),
        @Excel(name = "部门负责人", targetAttr = "leader", type = Type.EXPORT)
    })
    private SysDept dept;

    /** 角色对象 */
    private List<SysRole> roles;

    /** 角色组 */
    private Long[] roleIds;

    /** 岗位组 */
    private Long[] postIds;

    /** 岗位ID */
    private Long postId;

    /** 岗位名称 */
    private String postNames;

    /** 角色ID */
    private Long roleId;

    /** 工号查询条件 */
    private String employeeNo;

    /** 员工状态查询条件 */
    private String employeeStatus;

    /** 人员类别查询条件 */
    private String employeeCategory;

    /** 法人单位查询条件 */
    private String legalEntity;

    /** 工作所在地查询条件 */
    private String workLocation;

    /** 仅查询30天内合同到期员工 */
    private Boolean contractDue;

    /** 仅查询离职中但账号仍启用员工 */
    private Boolean offboardAccountOnly;

    /** 已分配角色数量 */
    private Integer roleCount;

    /** 已授权店铺/仓库数量 */
    private Integer shopScopeCount;

    /** 管理范围名称 */
    private String shopScopeNames;

    /** 配置状态：missingRole、missingShopScope、complete */
    private String setupStatus;

    public SysUser()
    {

    }

    public SysUser(Long userId)
    {
        this.userId = userId;
    }

    public Long getUserId()
    {
        return userId;
    }

    public void setUserId(Long userId)
    {
        this.userId = userId;
    }

    public boolean isAdmin()
    {
        return UserConstants.isAdmin(userId);
    }

    public Long getDeptId()
    {
        return deptId;
    }

    public void setDeptId(Long deptId)
    {
        this.deptId = deptId;
    }

    @Xss(message = "用户昵称不能包含脚本字符")
    @Size(min = 0, max = 30, message = "用户昵称长度不能超过30个字符")
    public String getNickName()
    {
        return nickName;
    }

    public void setNickName(String nickName)
    {
        this.nickName = nickName;
    }

    @Xss(message = "用户账号不能包含脚本字符")
    @NotBlank(message = "用户账号不能为空")
    @Size(min = 0, max = 30, message = "用户账号长度不能超过30个字符")
    public String getUserName()
    {
        return userName;
    }

    public void setUserName(String userName)
    {
        this.userName = userName;
    }

    @Email(message = "邮箱格式不正确")
    @Size(min = 0, max = 50, message = "邮箱长度不能超过50个字符")
    public String getEmail()
    {
        return email;
    }

    public void setEmail(String email)
    {
        this.email = email;
    }

    @Size(min = 0, max = 11, message = "手机号码长度不能超过11个字符")
    public String getPhonenumber()
    {
        return phonenumber;
    }

    public void setPhonenumber(String phonenumber)
    {
        this.phonenumber = phonenumber;
    }

    public String getSex()
    {
        return sex;
    }

    public void setSex(String sex)
    {
        this.sex = sex;
    }

    public String getAvatar()
    {
        return avatar;
    }

    public void setAvatar(String avatar)
    {
        this.avatar = avatar;
    }

    public String getPassword()
    {
        return password;
    }

    public void setPassword(String password)
    {
        this.password = password;
    }

    public String getStatus()
    {
        return status;
    }

    public void setStatus(String status)
    {
        this.status = status;
    }

    public String getDelFlag()
    {
        return delFlag;
    }

    public void setDelFlag(String delFlag)
    {
        this.delFlag = delFlag;
    }

    @Override
    public String getRemark()
    {
        return remark;
    }

    @Override
    public void setRemark(String remark)
    {
        this.remark = remark;
        super.setRemark(remark);
    }

    public String getLoginIp()
    {
        return loginIp;
    }

    public void setLoginIp(String loginIp)
    {
        this.loginIp = loginIp;
    }

    public Date getLoginDate()
    {
        return loginDate;
    }

    public void setLoginDate(Date loginDate)
    {
        this.loginDate = loginDate;
    }

    public Date getPwdUpdateDate()
    {
        return pwdUpdateDate;
    }

    public void setPwdUpdateDate(Date pwdUpdateDate)
    {
        this.pwdUpdateDate = pwdUpdateDate;
    }

    public String getMustChangePassword()
    {
        return mustChangePassword;
    }

    public void setMustChangePassword(String mustChangePassword)
    {
        this.mustChangePassword = mustChangePassword;
    }

    public String getCredentialState()
    {
        return credentialState;
    }

    public void setCredentialState(String credentialState)
    {
        this.credentialState = credentialState;
    }

    public Date getTemporaryPasswordExpiresAt()
    {
        return temporaryPasswordExpiresAt;
    }

    public void setTemporaryPasswordExpiresAt(Date temporaryPasswordExpiresAt)
    {
        this.temporaryPasswordExpiresAt = temporaryPasswordExpiresAt;
    }

    public String resolvedCredentialState()
    {
        return credentialState == null || credentialState.isBlank()
                ? CREDENTIAL_STATE_ACTIVE : credentialState;
    }

    public SysUserProfile getProfile()
    {
        if (profile == null)
        {
            profile = new SysUserProfile();
        }
        return profile;
    }

    public void setProfile(SysUserProfile profile)
    {
        this.profile = profile;
    }

    public boolean hasProfile()
    {
        return profile != null;
    }

    public SysDept getDept()
    {
        return dept;
    }

    public void setDept(SysDept dept)
    {
        this.dept = dept;
    }

    public List<SysRole> getRoles()
    {
        return roles;
    }

    public void setRoles(List<SysRole> roles)
    {
        this.roles = roles;
    }

    public Long[] getRoleIds()
    {
        return roleIds;
    }

    public void setRoleIds(Long[] roleIds)
    {
        this.roleIds = roleIds;
    }

    public Long[] getPostIds()
    {
        return postIds;
    }

    public void setPostIds(Long[] postIds)
    {
        this.postIds = postIds;
    }

    public Long getPostId()
    {
        return postId;
    }

    public void setPostId(Long postId)
    {
        this.postId = postId;
    }

    public String getPostNames()
    {
        return postNames;
    }

    public void setPostNames(String postNames)
    {
        this.postNames = postNames;
    }

    public Long getRoleId()
    {
        return roleId;
    }

    public void setRoleId(Long roleId)
    {
        this.roleId = roleId;
    }

    public String getEmployeeNo()
    {
        return employeeNo;
    }

    public void setEmployeeNo(String employeeNo)
    {
        this.employeeNo = employeeNo;
    }

    public String getEmployeeStatus()
    {
        return employeeStatus;
    }

    public void setEmployeeStatus(String employeeStatus)
    {
        this.employeeStatus = employeeStatus;
    }

    public Boolean getContractDue()
    {
        return contractDue;
    }

    public void setContractDue(Boolean contractDue)
    {
        this.contractDue = contractDue;
    }

    public Boolean getOffboardAccountOnly()
    {
        return offboardAccountOnly;
    }

    public void setOffboardAccountOnly(Boolean offboardAccountOnly)
    {
        this.offboardAccountOnly = offboardAccountOnly;
    }

    public String getEmployeeCategory()
    {
        return employeeCategory;
    }

    public void setEmployeeCategory(String employeeCategory)
    {
        this.employeeCategory = employeeCategory;
    }

    public String getLegalEntity()
    {
        return legalEntity;
    }

    public void setLegalEntity(String legalEntity)
    {
        this.legalEntity = legalEntity;
    }

    public String getWorkLocation()
    {
        return workLocation;
    }

    public void setWorkLocation(String workLocation)
    {
        this.workLocation = workLocation;
    }

    public Integer getRoleCount()
    {
        return roleCount;
    }

    public void setRoleCount(Integer roleCount)
    {
        this.roleCount = roleCount;
    }

    public Integer getShopScopeCount()
    {
        return shopScopeCount;
    }

    public void setShopScopeCount(Integer shopScopeCount)
    {
        this.shopScopeCount = shopScopeCount;
    }

    public String getShopScopeNames()
    {
        return shopScopeNames;
    }

    public void setShopScopeNames(String shopScopeNames)
    {
        this.shopScopeNames = shopScopeNames;
    }

    public String getSetupStatus()
    {
        return setupStatus;
    }

    public void setSetupStatus(String setupStatus)
    {
        this.setupStatus = setupStatus;
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this,ToStringStyle.MULTI_LINE_STYLE)
            .append("userId", getUserId())
            .append("deptId", getDeptId())
            .append("userName", getUserName())
            .append("nickName", getNickName())
            .append("email", getEmail())
            .append("phonenumber", getPhonenumber())
            .append("sex", getSex())
            .append("avatar", getAvatar())
            .append("status", getStatus())
            .append("delFlag", getDelFlag())
            .append("loginIp", getLoginIp())
            .append("loginDate", getLoginDate())
            .append("pwdUpdateDate", getPwdUpdateDate())
            .append("mustChangePassword", getMustChangePassword())
            .append("credentialState", resolvedCredentialState())
            .append("temporaryPasswordExpiresAt", getTemporaryPasswordExpiresAt())
            .append("createBy", getCreateBy())
            .append("createTime", getCreateTime())
            .append("updateBy", getUpdateBy())
            .append("updateTime", getUpdateTime())
            .append("remark", getRemark())
            .append("profile", getProfile())
            .append("dept", getDept())
            .append("postId", getPostId())
            .append("postNames", getPostNames())
            .append("roleCount", getRoleCount())
            .append("shopScopeCount", getShopScopeCount())
            .append("shopScopeNames", getShopScopeNames())
            .append("setupStatus", getSetupStatus())
            .toString();
    }
}
