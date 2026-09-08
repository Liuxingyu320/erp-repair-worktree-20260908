# HR Personnel Management Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build the first-phase `人事管理` module for employee profiles, onboarding, completeness checks, and HR Excel import/export around the confirmed Excel field spec.

**Architecture:** Extend the existing `sys_user_profile` employee profile model so profiles can exist before a login account is created, then expose HR-focused endpoints in `erp-system`. The frontend adds a dedicated `hr` view area and API client while leaving `系统管理 -> 用户管理` focused on account and permission work.

**Tech Stack:** Spring Boot 3 / MyBatis XML / Maven tests for backend; Vue 2 / Element UI / existing request wrapper / Node-based static tests for frontend; MySQL migration SQL for schema and menu permissions.

---

## Scope Check

The design spans database, backend, frontend, and Excel workflows, but they are not independent products: all four serve the same HR employee profile module. Implement in the task order below so each task leaves the system testable.

## File Structure

Backend files:

- Modify `erp-api/erp-api-system/src/main/java/com/erp/system/api/domain/SysUserProfile.java`: add HR/onboarding/profile-source fields and Excel annotations.
- Modify `erp-modules/erp-system/src/main/java/com/erp/system/mapper/SysUserProfileMapper.java`: add profile list, profile-id lookup, conflict lookup, and insert/update methods.
- Modify `erp-modules/erp-system/src/main/resources/mapper/system/SysUserProfileMapper.xml`: map new fields and profile list SQL.
- Create `erp-modules/erp-system/src/main/java/com/erp/system/domain/hr/HrProfileQuery.java`: list filters for HR profile and onboarding screens.
- Create `erp-modules/erp-system/src/main/java/com/erp/system/domain/hr/HrProfileSummary.java`: response DTO with profile, completeness, account binding, and missing-field summary.
- Create `erp-modules/erp-system/src/main/java/com/erp/system/domain/hr/HrImportPreview.java`: import preview aggregate.
- Create `erp-modules/erp-system/src/main/java/com/erp/system/domain/hr/HrImportPreviewRow.java`: row-level import preview result.
- Create `erp-modules/erp-system/src/main/java/com/erp/system/service/IHrEmployeeProfileService.java`: HR profile service contract.
- Create `erp-modules/erp-system/src/main/java/com/erp/system/service/impl/HrEmployeeProfileServiceImpl.java`: profile list, save, completeness, onboarding confirm, import preview/confirm.
- Create `erp-modules/erp-system/src/main/java/com/erp/system/support/HrProfileCompletenessCalculator.java`: deterministic completeness and missing-field logic.
- Create `erp-modules/erp-system/src/main/java/com/erp/system/controller/HrEmployeeProfileController.java`: `/hr/employee` endpoints.
- Create `erp-modules/erp-system/src/main/java/com/erp/system/controller/HrOnboardingController.java`: `/hr/onboarding` endpoints.
- Create `erp-modules/erp-system/src/main/java/com/erp/system/controller/HrImportExportController.java`: `/hr/import` and `/hr/export` endpoints.
- Create `sql/erp_hr_personnel_management_20260708.sql`: schema extensions and menu permissions.

Backend tests:

- Create `erp-modules/erp-system/src/test/java/com/erp/system/support/HrProfileCompletenessCalculatorTest.java`.
- Create `erp-modules/erp-system/src/test/java/com/erp/system/service/impl/HrEmployeeProfileServiceImplTest.java`.
- Create `erp-modules/erp-system/src/test/java/com/erp/system/mapper/HrEmployeeProfileMapperBindingTest.java`.
- Create `erp-modules/erp-system/src/test/java/com/erp/system/controller/HrEmployeeProfileControllerTest.java`.

Frontend files:

- Create `erp-ui/src/api/hr/employee.js`: HR employee, onboarding, completeness, and import/export API functions.
- Create `erp-ui/src/views/hr/components/HrProfileSummaryDrawer.vue`: simplified profile drawer.
- Create `erp-ui/src/views/hr/components/HrProfileDetailDrawer.vue`: detailed grouped profile drawer.
- Create `erp-ui/src/views/hr/employee/index.vue`: employee profile list.
- Create `erp-ui/src/views/hr/onboarding/index.vue`: onboarding list and confirm flow.
- Create `erp-ui/src/views/hr/completeness/index.vue`: completeness dashboard/list.
- Create `erp-ui/src/views/hr/importExport/index.vue`: template, preview, confirm import, export screen.
- Modify `erp-ui/src/router/index.js`: add dynamic hidden routes only if needed; primary menu still comes from DB.

Frontend tests:

- Create `erp-ui/test/hrPersonnelManagement.test.js`: static contract test for API functions and HR views.
- Create `erp-ui/test/hrCompletenessRules.test.js`: JS mirror test for required field constants if frontend exposes them.

## Task 1: Schema and Domain Fields

**Files:**
- Modify: `erp-api/erp-api-system/src/main/java/com/erp/system/api/domain/SysUserProfile.java`
- Modify: `erp-modules/erp-system/src/main/java/com/erp/system/mapper/SysUserProfileMapper.java`
- Modify: `erp-modules/erp-system/src/main/resources/mapper/system/SysUserProfileMapper.xml`
- Create: `sql/erp_hr_personnel_management_20260708.sql`
- Test: `erp-modules/erp-system/src/test/java/com/erp/system/mapper/HrEmployeeProfileMapperBindingTest.java`

- [ ] **Step 1: Write the failing mapper binding test**

Create `erp-modules/erp-system/src/test/java/com/erp/system/mapper/HrEmployeeProfileMapperBindingTest.java`:

```java
package com.erp.system.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

@DisplayName("人事员工档案 Mapper 绑定")
class HrEmployeeProfileMapperBindingTest
{
    @Test
    @DisplayName("员工档案SQL应包含入职管理扩展字段")
    void profileMapperShouldExposeHrFields()
            throws Exception
    {
        String xml = new String(new ClassPathResource("mapper/system/SysUserProfileMapper.xml")
                .getInputStream().readAllBytes(), StandardCharsets.UTF_8);

        assertThat(xml).contains("employee_name");
        assertThat(xml).contains("phone_number");
        assertThat(xml).contains("sex");
        assertThat(xml).contains("contract_type");
        assertThat(xml).contains("social_security_type");
        assertThat(xml).contains("onboarding_status");
        assertThat(xml).contains("expected_entry_date");
        assertThat(xml).contains("profile_source");
        assertThat(xml).contains("id_card_portrait_status");
        assertThat(xml).contains("selectUserProfileList");
        assertThat(xml).contains("selectUserProfileByProfileId");
    }
}
```

- [ ] **Step 2: Run the mapper test to verify it fails**

Run:

```bash
mvn -pl erp-modules/erp-system -Dtest=HrEmployeeProfileMapperBindingTest test
```

Expected: FAIL because `HrEmployeeProfileMapperBindingTest` cannot find the new HR columns or list SQL.

- [ ] **Step 3: Add migration SQL**

Create `sql/erp_hr_personnel_management_20260708.sql`:

```sql
-- 人事管理与入职档案一期

ALTER TABLE sys_user_profile
    MODIFY COLUMN user_id bigint(20) NULL COMMENT '用户ID，待入职阶段可为空';

ALTER TABLE sys_user_profile
    ADD COLUMN employee_name varchar(64) DEFAULT NULL COMMENT '员工姓名' AFTER user_id,
    ADD COLUMN phone_number varchar(32) DEFAULT NULL COMMENT '手机号' AFTER employee_name,
    ADD COLUMN sex char(1) DEFAULT NULL COMMENT '性别' AFTER phone_number,
    ADD COLUMN contract_type varchar(64) DEFAULT NULL COMMENT '合同类型' AFTER legal_entity,
    ADD COLUMN social_security_type varchar(64) DEFAULT NULL COMMENT '社保类型' AFTER contract_type,
    ADD COLUMN onboarding_status varchar(32) DEFAULT NULL COMMENT '入职状态' AFTER social_security_type,
    ADD COLUMN expected_entry_date date DEFAULT NULL COMMENT '预计入职日期' AFTER onboarding_status,
    ADD COLUMN onboarding_confirmed_by varchar(64) DEFAULT NULL COMMENT '确认入职人' AFTER expected_entry_date,
    ADD COLUMN onboarding_confirmed_time datetime DEFAULT NULL COMMENT '确认入职时间' AFTER onboarding_confirmed_by,
    ADD COLUMN onboarding_cancel_reason varchar(255) DEFAULT NULL COMMENT '取消入职原因' AFTER onboarding_confirmed_time,
    ADD COLUMN profile_source varchar(64) DEFAULT NULL COMMENT '资料来源' AFTER onboarding_cancel_reason,
    ADD COLUMN last_profile_update_by varchar(64) DEFAULT NULL COMMENT '资料最后更新人' AFTER profile_source,
    ADD COLUMN last_profile_update_time datetime DEFAULT NULL COMMENT '资料最后更新时间' AFTER last_profile_update_by,
    ADD COLUMN id_card_portrait_status varchar(32) DEFAULT NULL COMMENT '身份证人像面状态' AFTER last_profile_update_time,
    ADD COLUMN id_card_emblem_status varchar(32) DEFAULT NULL COMMENT '身份证国徽面状态' AFTER id_card_portrait_status,
    ADD COLUMN education_certificate_status varchar(32) DEFAULT NULL COMMENT '学历证书状态' AFTER id_card_emblem_status,
    ADD COLUMN degree_certificate_status varchar(32) DEFAULT NULL COMMENT '学位证书状态' AFTER education_certificate_status,
    ADD COLUMN resignation_certificate_status varchar(32) DEFAULT NULL COMMENT '前公司离职证明状态' AFTER degree_certificate_status,
    ADD COLUMN employee_photo_status varchar(32) DEFAULT NULL COMMENT '员工照片状态' AFTER resignation_certificate_status;

CREATE INDEX idx_user_profile_onboarding_status ON sys_user_profile (onboarding_status);
CREATE INDEX idx_user_profile_expected_entry_date ON sys_user_profile (expected_entry_date);
CREATE INDEX idx_user_profile_user_nullable ON sys_user_profile (user_id);

ALTER TABLE sys_user_profile DROP INDEX uk_user_profile_user_id;
CREATE UNIQUE INDEX uk_user_profile_user_id ON sys_user_profile (user_id);
```

- [ ] **Step 4: Add domain fields**

Modify `SysUserProfile.java` to add fields, getters, and setters for:

```java
private String employeeName;
private String phoneNumber;
private String sex;
private String contractType;
private String socialSecurityType;
private String onboardingStatus;
@JsonFormat(pattern = "yyyy-MM-dd")
private Date expectedEntryDate;
private String onboardingConfirmedBy;
@JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
private Date onboardingConfirmedTime;
private String onboardingCancelReason;
private String profileSource;
private String lastProfileUpdateBy;
@JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
private Date lastProfileUpdateTime;
private String idCardPortraitStatus;
private String idCardEmblemStatus;
private String educationCertificateStatus;
private String degreeCertificateStatus;
private String resignationCertificateStatus;
private String employeePhotoStatus;
```

- [ ] **Step 5: Extend mapper interface**

Add methods to `SysUserProfileMapper.java`:

```java
public SysUserProfile selectUserProfileByProfileId(Long profileId);

public java.util.List<SysUserProfile> selectUserProfileList(com.erp.system.domain.hr.HrProfileQuery query);

public java.util.List<SysUserProfile> selectUserProfilesByPhoneOrEmployeeNo(com.erp.system.domain.hr.HrProfileQuery query);
```

- [ ] **Step 6: Extend mapper XML**

Update `SysUserProfileMapper.xml`:

- Add result mappings for every new property.
- Add new columns to `selectUserProfileVo`.
- Add new columns and values to `insertUserProfile`.
- Add new columns to `updateUserProfile`.
- Change `updateUserProfile` `where user_id = #{userId}` to `where profile_id = #{profileId}` when `profileId` is present; keep a fallback update by `user_id` only for existing user-management saves.
- Add `selectUserProfileByProfileId`.
- Add `selectUserProfileList` with filters from `HrProfileQuery`.
- Add `selectUserProfilesByPhoneOrEmployeeNo`.

- [ ] **Step 7: Run the mapper test to verify it passes**

Run:

```bash
mvn -pl erp-modules/erp-system -Dtest=HrEmployeeProfileMapperBindingTest test
```

Expected: PASS.

- [ ] **Step 8: Commit Task 1**

```bash
git add erp-api/erp-api-system/src/main/java/com/erp/system/api/domain/SysUserProfile.java \
  erp-modules/erp-system/src/main/java/com/erp/system/mapper/SysUserProfileMapper.java \
  erp-modules/erp-system/src/main/resources/mapper/system/SysUserProfileMapper.xml \
  erp-modules/erp-system/src/test/java/com/erp/system/mapper/HrEmployeeProfileMapperBindingTest.java \
  sql/erp_hr_personnel_management_20260708.sql
git commit -m "feat: extend employee profile for hr onboarding"
```

## Task 2: Completeness Rules

**Files:**
- Create: `erp-modules/erp-system/src/main/java/com/erp/system/support/HrProfileCompletenessCalculator.java`
- Create: `erp-modules/erp-system/src/main/java/com/erp/system/domain/hr/HrCompletenessResult.java`
- Test: `erp-modules/erp-system/src/test/java/com/erp/system/support/HrProfileCompletenessCalculatorTest.java`

- [ ] **Step 1: Write the failing completeness test**

Create `HrProfileCompletenessCalculatorTest.java`:

```java
package com.erp.system.support;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Date;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import com.erp.system.api.domain.SysUserProfile;
import com.erp.system.domain.hr.HrCompletenessResult;

@DisplayName("人事资料完整度计算")
class HrProfileCompletenessCalculatorTest
{
    private final HrProfileCompletenessCalculator calculator = new HrProfileCompletenessCalculator();

    @Test
    @DisplayName("缺少入职必填项时应返回缺失字段并禁止确认入职")
    void shouldReturnMissingRequiredFields()
    {
        SysUserProfile profile = new SysUserProfile();
        profile.setEmployeeName("张三");
        profile.setEmployeeStatus("待入职");
        profile.setEmployeeCategory("全职");
        profile.setContractType("劳动合同");

        HrCompletenessResult result = calculator.calculate(profile);

        assertThat(result.getOnboardingComplete()).isFalse();
        assertThat(result.getMissingOnboardingFields()).contains("手机号", "证件号码", "银行卡号", "社保类型");
        assertThat(result.getOnboardingCompleteness()).isLessThan(100);
    }

    @Test
    @DisplayName("入职必填项齐全时允许确认入职")
    void shouldMarkOnboardingComplete()
    {
        SysUserProfile profile = completeProfile();

        HrCompletenessResult result = calculator.calculate(profile);

        assertThat(result.getOnboardingComplete()).isTrue();
        assertThat(result.getMissingOnboardingFields()).isEmpty();
        assertThat(result.getOnboardingCompleteness()).isEqualTo(100);
    }

    private static SysUserProfile completeProfile()
    {
        SysUserProfile profile = new SysUserProfile();
        profile.setEmployeeName("李四");
        profile.setEmployeeNo("E1001");
        profile.setCompanyName("金英灵韵");
        profile.setDeptLevel1Name("北京区域");
        profile.setDeptLevel2Name("北京区域运营");
        profile.setDeptLevel3Name("北京区域运营");
        profile.setStoreName("北京柏悦");
        profile.setPositionNames("茶艺师");
        profile.setJobGrade("6");
        profile.setPhoneNumber("13800000000");
        profile.setDepartmentSupervisor("店长");
        profile.setDirectSupervisor("店长");
        profile.setEmployeeStatus("待入职");
        profile.setEmployeeCategory("全职");
        profile.setSex("1");
        profile.setBirthDate(new Date());
        profile.setIdType("居民身份证");
        profile.setIdNumber("110101199001011234");
        profile.setRegisteredResidence("北京市");
        profile.setCurrentAddress("北京市朝阳区");
        profile.setMaritalStatus("未婚");
        profile.setEthnicity("汉族");
        profile.setEmergencyContact("王五");
        profile.setEmergencyContactRelation("父母");
        profile.setEmergencyContactPhone("13900000000");
        profile.setEntryDate(new Date());
        profile.setWorkLocation("北京");
        profile.setWorkCityLevel("一线");
        profile.setBankName("中国银行");
        profile.setBankAccount("6222000000000000");
        profile.setContractType("劳动合同");
        profile.setSocialSecurityType("有");
        profile.setLegalEntity("北京金英灵韵茶业有限公司");
        return profile;
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

```bash
mvn -pl erp-modules/erp-system -Dtest=HrProfileCompletenessCalculatorTest test
```

Expected: FAIL because calculator and result DTO do not exist.

- [ ] **Step 3: Add completeness result DTO**

Create `HrCompletenessResult.java` with:

```java
package com.erp.system.domain.hr;

import java.util.ArrayList;
import java.util.List;

public class HrCompletenessResult
{
    private int onboardingCompleteness;
    private int profileCompleteness;
    private Boolean onboardingComplete;
    private List<String> missingOnboardingFields = new ArrayList<>();
    private List<String> missingProfileFields = new ArrayList<>();

    public int getOnboardingCompleteness() { return onboardingCompleteness; }
    public void setOnboardingCompleteness(int onboardingCompleteness) { this.onboardingCompleteness = onboardingCompleteness; }
    public int getProfileCompleteness() { return profileCompleteness; }
    public void setProfileCompleteness(int profileCompleteness) { this.profileCompleteness = profileCompleteness; }
    public Boolean getOnboardingComplete() { return onboardingComplete; }
    public void setOnboardingComplete(Boolean onboardingComplete) { this.onboardingComplete = onboardingComplete; }
    public List<String> getMissingOnboardingFields() { return missingOnboardingFields; }
    public void setMissingOnboardingFields(List<String> missingOnboardingFields) { this.missingOnboardingFields = missingOnboardingFields; }
    public List<String> getMissingProfileFields() { return missingProfileFields; }
    public void setMissingProfileFields(List<String> missingProfileFields) { this.missingProfileFields = missingProfileFields; }
}
```

- [ ] **Step 4: Add calculator**

Create `HrProfileCompletenessCalculator.java` with deterministic required-field rules:

```java
package com.erp.system.support;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import com.erp.common.core.utils.StringUtils;
import com.erp.system.api.domain.SysUserProfile;
import com.erp.system.domain.hr.HrCompletenessResult;

public class HrProfileCompletenessCalculator
{
    private record RequiredField(String label, Predicate<SysUserProfile> present) {}

    private static final List<RequiredField> ONBOARDING_FIELDS = List.of(
            field("姓名", p -> has(p.getEmployeeName())),
            field("工号", p -> has(p.getEmployeeNo())),
            field("所属公司", p -> has(p.getCompanyName())),
            field("1级部门", p -> has(p.getDeptLevel1Name())),
            field("2级部门", p -> has(p.getDeptLevel2Name())),
            field("3级部门", p -> has(p.getDeptLevel3Name())),
            field("4级门店", p -> has(p.getStoreName())),
            field("职位", p -> has(p.getPositionNames())),
            field("职级", p -> has(p.getJobGrade())),
            field("手机号", p -> has(p.getPhoneNumber())),
            field("部门主管", p -> has(p.getDepartmentSupervisor())),
            field("直属主管", p -> has(p.getDirectSupervisor())),
            field("员工状态", p -> has(p.getEmployeeStatus())),
            field("人员类别", p -> has(p.getEmployeeCategory())),
            field("性别", p -> has(p.getSex())),
            field("出生日期", p -> p.getBirthDate() != null),
            field("证件类型", p -> has(p.getIdType())),
            field("证件号码", p -> has(p.getIdNumber())),
            field("户籍地址", p -> has(p.getRegisteredResidence())),
            field("现居住地址", p -> has(p.getCurrentAddress())),
            field("婚姻状况", p -> has(p.getMaritalStatus())),
            field("民族", p -> has(p.getEthnicity())),
            field("紧急联系人", p -> has(p.getEmergencyContact())),
            field("与紧急联系人关系", p -> has(p.getEmergencyContactRelation())),
            field("紧急联系人电话", p -> has(p.getEmergencyContactPhone())),
            field("入职日期", p -> p.getEntryDate() != null),
            field("入职岗位", p -> has(p.getPositionNames())),
            field("岗位职级", p -> has(p.getJobGrade())),
            field("工作所在地", p -> has(p.getWorkLocation())),
            field("工作所在城市级别", p -> has(p.getWorkCityLevel())),
            field("开户银行", p -> has(p.getBankName())),
            field("银行卡号", p -> has(p.getBankAccount())),
            field("合同类型", p -> has(p.getContractType())),
            field("社保类型", p -> has(p.getSocialSecurityType())),
            field("法人单位", p -> has(p.getLegalEntity()))
    );

    public HrCompletenessResult calculate(SysUserProfile profile)
    {
        HrCompletenessResult result = new HrCompletenessResult();
        List<String> missing = missingFields(profile, ONBOARDING_FIELDS);
        result.setMissingOnboardingFields(missing);
        result.setOnboardingCompleteness(percent(ONBOARDING_FIELDS.size() - missing.size(), ONBOARDING_FIELDS.size()));
        result.setOnboardingComplete(missing.isEmpty());
        result.setMissingProfileFields(new ArrayList<>(missing));
        result.setProfileCompleteness(result.getOnboardingCompleteness());
        return result;
    }

    private static RequiredField field(String label, Predicate<SysUserProfile> present)
    {
        return new RequiredField(label, present);
    }

    private static List<String> missingFields(SysUserProfile profile, List<RequiredField> fields)
    {
        List<String> missing = new ArrayList<>();
        for (RequiredField field : fields)
        {
            if (!field.present().test(profile))
            {
                missing.add(field.label());
            }
        }
        return missing;
    }

    private static int percent(int present, int total)
    {
        return total == 0 ? 100 : Math.round((present * 100.0f) / total);
    }

    private static boolean has(String value)
    {
        return StringUtils.isNotEmpty(value);
    }
}
```

- [ ] **Step 5: Run completeness tests**

```bash
mvn -pl erp-modules/erp-system -Dtest=HrProfileCompletenessCalculatorTest test
```

Expected: PASS.

- [ ] **Step 6: Commit Task 2**

```bash
git add erp-modules/erp-system/src/main/java/com/erp/system/support/HrProfileCompletenessCalculator.java \
  erp-modules/erp-system/src/main/java/com/erp/system/domain/hr/HrCompletenessResult.java \
  erp-modules/erp-system/src/test/java/com/erp/system/support/HrProfileCompletenessCalculatorTest.java
git commit -m "feat: calculate hr profile completeness"
```

## Task 3: HR Employee and Onboarding Backend

**Files:**
- Create: `erp-modules/erp-system/src/main/java/com/erp/system/domain/hr/HrProfileQuery.java`
- Create: `erp-modules/erp-system/src/main/java/com/erp/system/domain/hr/HrProfileSummary.java`
- Create: `erp-modules/erp-system/src/main/java/com/erp/system/service/IHrEmployeeProfileService.java`
- Create: `erp-modules/erp-system/src/main/java/com/erp/system/service/impl/HrEmployeeProfileServiceImpl.java`
- Create: `erp-modules/erp-system/src/main/java/com/erp/system/controller/HrEmployeeProfileController.java`
- Create: `erp-modules/erp-system/src/main/java/com/erp/system/controller/HrOnboardingController.java`
- Test: `erp-modules/erp-system/src/test/java/com/erp/system/service/impl/HrEmployeeProfileServiceImplTest.java`
- Test: `erp-modules/erp-system/src/test/java/com/erp/system/controller/HrEmployeeProfileControllerTest.java`

- [ ] **Step 1: Write failing service tests**

Create `HrEmployeeProfileServiceImplTest.java` with tests for:

```java
@Test
@DisplayName("资料未齐时不能确认入职")
void confirmOnboardingShouldRejectIncompleteProfile()
```

and:

```java
@Test
@DisplayName("资料齐全时确认入职应绑定账号并更新状态")
void confirmOnboardingShouldBindUserAndUpdateStatus()
```

Use proxy mappers like `SysUserServiceImplTest` in the same package. Assert the service throws `ServiceException` containing `入职资料未齐` for incomplete profiles, and updates `onboardingStatus=已入职`, `employeeStatus=试用`, `userId=<bound id>` for complete profiles.

- [ ] **Step 2: Run service tests to verify they fail**

```bash
mvn -pl erp-modules/erp-system -Dtest=HrEmployeeProfileServiceImplTest test
```

Expected: FAIL because service classes do not exist.

- [ ] **Step 3: Add HR query and summary DTOs**

Create `HrProfileQuery.java` with filter fields:

```java
private Long profileId;
private Long userId;
private String employeeName;
private String phoneNumber;
private String employeeNo;
private String companyName;
private String deptLevel1Name;
private String storeName;
private String positionNames;
private String employeeStatus;
private String employeeCategory;
private String onboardingStatus;
private String completenessStatus;
```

Create `HrProfileSummary.java` with:

```java
private SysUserProfile profile;
private HrCompletenessResult completeness;
private Boolean accountBound;
private Boolean accountEnabled;
private String accountUserName;
```

- [ ] **Step 4: Add service contract**

Create `IHrEmployeeProfileService.java`:

```java
public interface IHrEmployeeProfileService
{
    List<HrProfileSummary> selectEmployeeProfiles(HrProfileQuery query);
    HrProfileSummary selectEmployeeProfile(Long profileId);
    int saveEmployeeProfile(SysUserProfile profile, String operName);
    HrProfileSummary confirmOnboarding(Long profileId, Long userId, String operName);
    int cancelOnboarding(Long profileId, String reason, String operName);
}
```

- [ ] **Step 5: Implement service**

Implement `HrEmployeeProfileServiceImpl`:

- `selectEmployeeProfiles`: calls mapper list and attaches completeness.
- `selectEmployeeProfile`: loads by profile id and attaches completeness.
- `saveEmployeeProfile`: inserts when `profileId == null`, updates otherwise; sets `profileSource` and `lastProfileUpdate*`.
- `confirmOnboarding`: loads profile, runs calculator, rejects incomplete, sets `userId`, `onboardingStatus=已入职`, `employeeStatus=试用` when empty or `待入职`, confirmation fields, and updates.
- `cancelOnboarding`: sets `onboardingStatus=取消入职`, reason, update fields.

- [ ] **Step 6: Add controllers**

Create `HrEmployeeProfileController` with:

```java
@RestController
@RequestMapping("/hr/employee")
public class HrEmployeeProfileController extends BaseController
{
    @Autowired
    private IHrEmployeeProfileService hrEmployeeProfileService;

    @RequiresPermissions("hr:employee:list")
    @GetMapping("/list")
    public TableDataInfo list(HrProfileQuery query)
    {
        startPage();
        return getDataTable(hrEmployeeProfileService.selectEmployeeProfiles(query));
    }

    @RequiresPermissions("hr:employee:query")
    @GetMapping("/{profileId}")
    public AjaxResult getInfo(@PathVariable Long profileId)
    {
        return success(hrEmployeeProfileService.selectEmployeeProfile(profileId));
    }

    @RequiresPermissions("hr:employee:add")
    @PostMapping
    public AjaxResult add(@RequestBody SysUserProfile profile)
    {
        return toAjax(hrEmployeeProfileService.saveEmployeeProfile(profile, SecurityUtils.getUsername()));
    }

    @RequiresPermissions("hr:employee:edit")
    @PutMapping
    public AjaxResult edit(@RequestBody SysUserProfile profile)
    {
        return toAjax(hrEmployeeProfileService.saveEmployeeProfile(profile, SecurityUtils.getUsername()));
    }
}
```

Create `HrOnboardingController` with:

```java
@RestController
@RequestMapping("/hr/onboarding")
public class HrOnboardingController extends BaseController
{
    @Autowired
    private IHrEmployeeProfileService hrEmployeeProfileService;

    @RequiresPermissions("hr:onboarding:list")
    @GetMapping("/list")
    public TableDataInfo list(HrProfileQuery query)
    {
        query.setEmployeeStatus("待入职");
        startPage();
        return getDataTable(hrEmployeeProfileService.selectEmployeeProfiles(query));
    }

    @RequiresPermissions("hr:onboarding:confirm")
    @PutMapping("/{profileId}/confirm")
    public AjaxResult confirm(@PathVariable Long profileId, @RequestBody Map<String, Long> body)
    {
        Long userId = body == null ? null : body.get("userId");
        return success(hrEmployeeProfileService.confirmOnboarding(profileId, userId, SecurityUtils.getUsername()));
    }

    @RequiresPermissions("hr:onboarding:cancel")
    @PutMapping("/{profileId}/cancel")
    public AjaxResult cancel(@PathVariable Long profileId, @RequestBody Map<String, String> body)
    {
        String reason = body == null ? null : body.get("reason");
        return toAjax(hrEmployeeProfileService.cancelOnboarding(profileId, reason, SecurityUtils.getUsername()));
    }
}
```

- [ ] **Step 7: Run backend tests**

```bash
mvn -pl erp-modules/erp-system -Dtest=HrEmployeeProfileServiceImplTest,HrEmployeeProfileControllerTest test
```

Expected: PASS.

- [ ] **Step 8: Commit Task 3**

```bash
git add erp-modules/erp-system/src/main/java/com/erp/system/domain/hr \
  erp-modules/erp-system/src/main/java/com/erp/system/service/IHrEmployeeProfileService.java \
  erp-modules/erp-system/src/main/java/com/erp/system/service/impl/HrEmployeeProfileServiceImpl.java \
  erp-modules/erp-system/src/main/java/com/erp/system/controller/HrEmployeeProfileController.java \
  erp-modules/erp-system/src/main/java/com/erp/system/controller/HrOnboardingController.java \
  erp-modules/erp-system/src/test/java/com/erp/system/service/impl/HrEmployeeProfileServiceImplTest.java \
  erp-modules/erp-system/src/test/java/com/erp/system/controller/HrEmployeeProfileControllerTest.java
git commit -m "feat: add hr employee onboarding backend"
```

## Task 4: HR Import Preview and Export Backend

**Files:**
- Create: `erp-modules/erp-system/src/main/java/com/erp/system/domain/hr/HrImportPreview.java`
- Create: `erp-modules/erp-system/src/main/java/com/erp/system/domain/hr/HrImportPreviewRow.java`
- Modify: `erp-modules/erp-system/src/main/java/com/erp/system/service/IHrEmployeeProfileService.java`
- Modify: `erp-modules/erp-system/src/main/java/com/erp/system/service/impl/HrEmployeeProfileServiceImpl.java`
- Create: `erp-modules/erp-system/src/main/java/com/erp/system/controller/HrImportExportController.java`
- Test: `erp-modules/erp-system/src/test/java/com/erp/system/service/impl/HrEmployeeProfileImportServiceTest.java`

- [ ] **Step 1: Write failing import preview test**

Test requirements:

- Preview returns rows with `action=CREATE`, `action=UPDATE`, or `action=FAILED`.
- Preview does not call `insertUserProfile` or `updateUserProfile`.
- Confirm import writes only rows without failed status.

- [ ] **Step 2: Run import tests to verify they fail**

```bash
mvn -pl erp-modules/erp-system -Dtest=HrEmployeeProfileImportServiceTest test
```

Expected: FAIL because import preview is not implemented.

- [ ] **Step 3: Add preview DTOs**

`HrImportPreviewRow` fields:

```java
private Integer rowNumber;
private String action;
private String status;
private String message;
private SysUserProfile profile;
private Long matchedProfileId;
private Long matchedUserId;
private List<String> missingFields;
```

`HrImportPreview` fields:

```java
private String importType;
private int createCount;
private int updateCount;
private int failedCount;
private List<HrImportPreviewRow> rows;
```

- [ ] **Step 4: Implement preview logic**

Add service methods:

```java
HrImportPreview previewImport(MultipartFile file, String importType) throws Exception;
String confirmImport(HrImportPreview preview, String operName);
```

Rules:

- Match by employee no first.
- Match by phone number second.
- If employee no and phone match different profiles, row fails.
- Missing required fields do not block saving as待补资料, but row message lists missing fields.
- Confirm import inserts or updates only non-failed rows.

- [ ] **Step 5: Add import/export controller**

Create endpoints:

- `POST /hr/import/preview?importType=onboarding` with `MultipartFile file`
- `POST /hr/import/confirm`
- `POST /hr/import/template/onboarding`
- `POST /hr/import/template/profile`
- `POST /hr/export/profile`
- `POST /hr/export/missing`

Use existing `ExcelUtil<SysUserProfile>` for first phase template, import parsing, and export. The preview endpoint parses the uploaded file, builds `HrImportPreview`, and does not write data.

- [ ] **Step 6: Run import tests**

```bash
mvn -pl erp-modules/erp-system -Dtest=HrEmployeeProfileImportServiceTest test
```

Expected: PASS.

- [ ] **Step 7: Commit Task 4**

```bash
git add erp-modules/erp-system/src/main/java/com/erp/system/domain/hr/HrImportPreview.java \
  erp-modules/erp-system/src/main/java/com/erp/system/domain/hr/HrImportPreviewRow.java \
  erp-modules/erp-system/src/main/java/com/erp/system/service/IHrEmployeeProfileService.java \
  erp-modules/erp-system/src/main/java/com/erp/system/service/impl/HrEmployeeProfileServiceImpl.java \
  erp-modules/erp-system/src/main/java/com/erp/system/controller/HrImportExportController.java \
  erp-modules/erp-system/src/test/java/com/erp/system/service/impl/HrEmployeeProfileImportServiceTest.java
git commit -m "feat: add hr import preview workflow"
```

## Task 5: Frontend HR Pages

**Files:**
- Create: `erp-ui/src/api/hr/employee.js`
- Create: `erp-ui/src/views/hr/components/HrProfileSummaryDrawer.vue`
- Create: `erp-ui/src/views/hr/components/HrProfileDetailDrawer.vue`
- Create: `erp-ui/src/views/hr/employee/index.vue`
- Create: `erp-ui/src/views/hr/onboarding/index.vue`
- Create: `erp-ui/src/views/hr/completeness/index.vue`
- Create: `erp-ui/src/views/hr/importExport/index.vue`
- Test: `erp-ui/test/hrPersonnelManagement.test.js`

- [ ] **Step 1: Write failing frontend contract test**

Create `erp-ui/test/hrPersonnelManagement.test.js`:

```js
const assert = require('assert')
const fs = require('fs')
const path = require('path')

const apiPath = path.resolve(__dirname, '../src/api/hr/employee.js')
const employeePath = path.resolve(__dirname, '../src/views/hr/employee/index.vue')
const onboardingPath = path.resolve(__dirname, '../src/views/hr/onboarding/index.vue')
const completenessPath = path.resolve(__dirname, '../src/views/hr/completeness/index.vue')
const importExportPath = path.resolve(__dirname, '../src/views/hr/importExport/index.vue')

for (const file of [apiPath, employeePath, onboardingPath, completenessPath, importExportPath]) {
  assert.ok(fs.existsSync(file), `${file} should exist`)
}

const api = fs.readFileSync(apiPath, 'utf8')
assert.ok(api.includes("url: '/system/hr/employee/list'"), 'HR employee list API should exist')
assert.ok(api.includes("url: '/system/hr/onboarding/list'"), 'HR onboarding list API should exist')
assert.ok(api.includes("url: '/system/hr/import/preview'"), 'HR import preview API should exist')

const employeeView = fs.readFileSync(employeePath, 'utf8')
assert.ok(employeeView.includes('员工档案'), 'employee page should render title')
assert.ok(employeeView.includes('简略资料'), 'employee page should expose summary profile')
assert.ok(employeeView.includes('详细资料'), 'employee page should expose detailed profile')
assert.ok(employeeView.includes('资料完整度'), 'employee page should show completeness')

const onboardingView = fs.readFileSync(onboardingPath, 'utf8')
assert.ok(onboardingView.includes('确认入职'), 'onboarding page should support confirm action')
assert.ok(onboardingView.includes('取消入职'), 'onboarding page should support cancel action')

const importView = fs.readFileSync(importExportPath, 'utf8')
assert.ok(importView.includes('预检'), 'import/export page should use preview workflow')
assert.ok(importView.includes('确认导入'), 'import/export page should require confirm import')

console.log('hrPersonnelManagement tests passed')
```

- [ ] **Step 2: Run frontend test to verify it fails**

```bash
cd erp-ui && node test/hrPersonnelManagement.test.js
```

Expected: FAIL because HR frontend files do not exist.

- [ ] **Step 3: Add HR API client**

Create `erp-ui/src/api/hr/employee.js` with functions:

```js
import request from '@/utils/request'

export function listHrEmployees(query) {
  return request({ url: '/system/hr/employee/list', method: 'get', params: query })
}
export function getHrEmployee(profileId) {
  return request({ url: '/system/hr/employee/' + profileId, method: 'get' })
}
export function addHrEmployee(data) {
  return request({ url: '/system/hr/employee', method: 'post', data })
}
export function updateHrEmployee(data) {
  return request({ url: '/system/hr/employee', method: 'put', data })
}
export function listOnboarding(query) {
  return request({ url: '/system/hr/onboarding/list', method: 'get', params: query })
}
export function confirmOnboarding(profileId, userId) {
  return request({ url: '/system/hr/onboarding/' + profileId + '/confirm', method: 'put', data: { userId } })
}
export function cancelOnboarding(profileId, reason) {
  return request({ url: '/system/hr/onboarding/' + profileId + '/cancel', method: 'put', data: { reason } })
}
export function previewHrImport(data, importType) {
  return request({ url: '/system/hr/import/preview', method: 'post', params: { importType }, data })
}
export function confirmHrImport(data) {
  return request({ url: '/system/hr/import/confirm', method: 'post', data })
}
```

- [ ] **Step 4: Add summary and detail drawer components**

`HrProfileSummaryDrawer.vue`:

- Props: `visible`, `summary`.
- Emits: `update:visible`, `detail`, `edit`, `missing`, `account`, `export`.
- Displays identity, work info, onboarding info, missing fields, and account status.

`HrProfileDetailDrawer.vue`:

- Props: `visible`, `summary`.
- Emits: `update:visible`, `edit`.
- Groups fields into basic, organization, identity, education, contract, contact/bank, attachment status.

- [ ] **Step 5: Add employee page**

`erp-ui/src/views/hr/employee/index.vue`:

- Search form for name/phone/employee no/store/status/category/completeness.
- Table columns from the design.
- Row click opens summary drawer.
- Buttons for detail, edit, missing, account, export.

- [ ] **Step 6: Add onboarding page**

`erp-ui/src/views/hr/onboarding/index.vue`:

- Lists profiles with `employeeStatus=待入职`.
- Shows onboarding status and completeness.
- Provides `新增`, `编辑资料`, `确认入职`, `取消入职`.
- Confirm action calls `confirmOnboarding`.

- [ ] **Step 7: Add completeness page**

`erp-ui/src/views/hr/completeness/index.vue`:

- KPI strip for total, missing, onboarding missing, identity missing, bank missing.
- Table by employee first phase.
- Missing-field drawer or popover.

- [ ] **Step 8: Add import/export page**

`erp-ui/src/views/hr/importExport/index.vue`:

- Template buttons.
- Import type selector.
- Preview upload placeholder.
- Preview result table with create/update/failed counts.
- Confirm import button.
- Export buttons.

- [ ] **Step 9: Run frontend test**

```bash
cd erp-ui && node test/hrPersonnelManagement.test.js
```

Expected: PASS.

- [ ] **Step 10: Commit Task 5**

```bash
git add erp-ui/src/api/hr/employee.js \
  erp-ui/src/views/hr \
  erp-ui/test/hrPersonnelManagement.test.js
git commit -m "feat: add hr personnel frontend pages"
```

## Task 6: Menu Permissions and Full Verification

**Files:**
- Modify: `sql/erp_hr_personnel_management_20260708.sql`
- Create: `erp-ui/test/hrMenuPermissions.test.js`

- [ ] **Step 1: Write failing menu SQL test**

Create `erp-ui/test/hrMenuPermissions.test.js`:

```js
const assert = require('assert')
const fs = require('fs')
const path = require('path')

const sqlPath = path.resolve(__dirname, '../../sql/erp_hr_personnel_management_20260708.sql')
const sql = fs.readFileSync(sqlPath, 'utf8')

for (const text of ['人事管理', '员工档案', '入职管理', '资料完整度', '人事导入导出']) {
  assert.ok(sql.includes(text), `menu SQL should include ${text}`)
}

for (const perm of [
  'hr:employee:list',
  'hr:employee:query',
  'hr:employee:add',
  'hr:employee:edit',
  'hr:employee:export',
  'hr:onboarding:list',
  'hr:onboarding:confirm',
  'hr:import:preview',
  'hr:import:confirm',
  'hr:completeness:list'
]) {
  assert.ok(sql.includes(perm), `menu SQL should include ${perm}`)
}

console.log('hrMenuPermissions tests passed')
```

- [ ] **Step 2: Run menu test to verify it fails**

```bash
cd erp-ui && node test/hrMenuPermissions.test.js
```

Expected: FAIL until menu SQL is added.

- [ ] **Step 3: Add menu and permission SQL**

Append idempotent `sys_menu` inserts to `sql/erp_hr_personnel_management_20260708.sql`:

- Parent: `人事管理`, path `hr`, icon `peoples`.
- Child page: `员工档案`, path `employee`, component `hr/employee/index`, permission `hr:employee:list`.
- Child page: `入职管理`, path `onboarding`, component `hr/onboarding/index`, permission `hr:onboarding:list`.
- Child page: `资料完整度`, path `completeness`, component `hr/completeness/index`, permission `hr:completeness:list`.
- Child page: `人事导入导出`, path `import-export`, component `hr/importExport/index`, permission `hr:import:preview`.
- Button permissions listed in the test.

- [ ] **Step 4: Run targeted tests**

Backend:

```bash
mvn -pl erp-modules/erp-system -Dtest=HrEmployeeProfileMapperBindingTest,HrProfileCompletenessCalculatorTest,HrEmployeeProfileServiceImplTest,HrEmployeeProfileImportServiceTest,HrEmployeeProfileControllerTest test
```

Frontend:

```bash
cd erp-ui && node test/hrPersonnelManagement.test.js && node test/hrMenuPermissions.test.js
```

Expected: all PASS.

- [ ] **Step 5: Build sanity checks**

Backend compile:

```bash
mvn -pl erp-modules/erp-system -DskipTests compile
```

Frontend static build if dependencies are installed:

```bash
cd erp-ui && npm run build:stage
```

Expected: backend compile PASS. Frontend build PASS or report dependency/environment failure explicitly.

- [ ] **Step 6: Commit Task 6**

```bash
git add sql/erp_hr_personnel_management_20260708.sql erp-ui/test/hrMenuPermissions.test.js
git commit -m "feat: add hr personnel menus"
```

## Final Verification

Run:

```bash
mvn -pl erp-modules/erp-system -Dtest=HrEmployeeProfileMapperBindingTest,HrProfileCompletenessCalculatorTest,HrEmployeeProfileServiceImplTest,HrEmployeeProfileImportServiceTest,HrEmployeeProfileControllerTest test
cd erp-ui && node test/hrPersonnelManagement.test.js && node test/hrMenuPermissions.test.js
```

Then inspect changed files:

```bash
git status --short
git log --oneline -6
```

Confirm that only HR personnel files, the HR migration SQL, and intended tests were touched by this implementation.
