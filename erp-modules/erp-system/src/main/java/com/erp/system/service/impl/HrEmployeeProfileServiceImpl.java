package com.erp.system.service.impl;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Collection;
import java.lang.reflect.Array;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.erp.common.core.exception.ServiceException;
import com.erp.system.api.domain.SysUser;
import com.erp.system.api.domain.SysUserProfile;
import com.erp.system.api.domain.SysDept;
import com.erp.system.domain.HrSensitiveAccessLog;
import com.erp.system.domain.vo.*;
import com.erp.system.mapper.HrSensitiveAccessLogMapper;
import com.erp.system.mapper.SysUserMapper;
import com.erp.system.mapper.SysUserProfileMapper;
import com.erp.system.mapper.SysPostMapper;
import com.erp.system.mapper.SysUserPostMapper;
import com.erp.system.service.IHrEmployeeProfileService;
import com.erp.system.service.IHrOnboardingPositionConfigService;
import com.erp.system.service.ISysUserService;
import com.erp.system.service.IHrHealthCertificateService;
import com.erp.system.domain.SysPost;
import com.erp.system.domain.SysUserPost;
import com.erp.system.support.HrEmployeeFieldRegistry;
import com.erp.system.support.HrEmployeeFieldRegistry.FieldDefinition;
import com.erp.system.support.HrEmployeeFieldRegistry.MaskingClass;
import com.erp.system.support.HrEmployeeCompletenessEvaluator;
import com.erp.system.support.HrEmployeeCompletenessSnapshot;
import com.erp.system.support.HrSensitiveFieldMasker;
import com.erp.system.support.HrEmployeeStatusCatalog;
import com.github.pagehelper.Page;

@Service
public class HrEmployeeProfileServiceImpl implements IHrEmployeeProfileService
{
    private final HrEmployeeAccessService accessService;
    private final SysUserMapper userMapper;
    private final SysUserProfileMapper profileMapper;
    private final HrSensitiveAccessLogMapper auditMapper;
    private final SysUserProfileDerivationService derivationService;
    private final HrEmployeeFieldRegistry registry;
    private final HrEmployeeCompletenessEvaluator completenessEvaluator;
    private final HrSensitiveFieldMasker masker;
    private final SysPostMapper postMapper;
    private final SysUserPostMapper userPostMapper;
    private final IHrOnboardingPositionConfigService onboardingConfigService;
    private final ISysUserService userService;
    private final HrSensitiveAuditService exportAuditService;
    private final HrSensitiveWorkbookGenerator workbookGenerator;
    @Autowired(required=false)
    private IHrHealthCertificateService healthCertificateService;
    private static final int SENSITIVE_EXPORT_ROW_CAP=10000;
    static final int COMPLETENESS_SCAN_ROW_CAP=10000;
    private static final Pattern MASK_PLACEHOLDER = Pattern.compile("[\\*＊•●○◯◎◉◌◍◦∙]");
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Shanghai");
    private static final Set<String> LEGACY_IGNORED_KEYS=Set.of("userId","profileId","status","accountStatus",
            "createBy","createTime","updateBy","updateTime","profileSource","lastProfileUpdateBy",
            "lastProfileUpdateTime","derivedWarnings","params","searchValue","positionNames","directSupervisor",
            "profileCompletionPercent","missingProfileFields","requiredCompletionPercent","coveragePercent",
            "requiredCompletedFieldCount","requiredApplicableFieldCount","missingRequiredFields",
            "missingOptionalFields","missingByResponsibility","fields");

    public HrEmployeeProfileServiceImpl(HrEmployeeAccessService accessService, SysUserMapper userMapper,
            SysUserProfileMapper profileMapper, HrSensitiveAccessLogMapper auditMapper,
            SysUserProfileDerivationService derivationService, HrEmployeeFieldRegistry registry,
            HrSensitiveFieldMasker masker)
    {
        this(accessService,userMapper,profileMapper,auditMapper,derivationService,registry,masker,
                null,null,null,null,null,null);
    }

    public HrEmployeeProfileServiceImpl(HrEmployeeAccessService accessService, SysUserMapper userMapper,
            SysUserProfileMapper profileMapper, HrSensitiveAccessLogMapper auditMapper,
            SysUserProfileDerivationService derivationService, HrEmployeeFieldRegistry registry,
            HrSensitiveFieldMasker masker, SysPostMapper postMapper, SysUserPostMapper userPostMapper,
            IHrOnboardingPositionConfigService onboardingConfigService, ISysUserService userService)
    {
        this(accessService,userMapper,profileMapper,auditMapper,derivationService,registry,masker,postMapper,
                userPostMapper,onboardingConfigService,userService,null,null);
    }

    @Autowired
    public HrEmployeeProfileServiceImpl(HrEmployeeAccessService accessService, SysUserMapper userMapper,
            SysUserProfileMapper profileMapper, HrSensitiveAccessLogMapper auditMapper,
            SysUserProfileDerivationService derivationService, HrEmployeeFieldRegistry registry,
            HrSensitiveFieldMasker masker, SysPostMapper postMapper, SysUserPostMapper userPostMapper,
            IHrOnboardingPositionConfigService onboardingConfigService, ISysUserService userService,
            HrSensitiveAuditService exportAuditService,HrSensitiveWorkbookGenerator workbookGenerator)
    {
        this.accessService=accessService; this.userMapper=userMapper; this.profileMapper=profileMapper;
        this.auditMapper=auditMapper; this.derivationService=derivationService; this.registry=registry;
        this.completenessEvaluator=new HrEmployeeCompletenessEvaluator(registry);
        this.masker=masker; this.postMapper=postMapper; this.userPostMapper=userPostMapper;
        this.onboardingConfigService=onboardingConfigService;
        this.userService=userService;
        this.exportAuditService=exportAuditService;this.workbookGenerator=workbookGenerator;
    }

    @Override public List<HrEmployeeListVo> list(HrEmployeeQuery query)
    {
        return employeeList(query,false);
    }

    private List<HrEmployeeListVo> employeeList(HrEmployeeQuery query,boolean activeGovernanceOnly)
    {
        List<SysUser> rows = scopedEmployees(query,activeGovernanceOnly);
        List<HrEmployeeListVo> result;
        if (rows instanceof Page<?> sourcePage)
        {
            Page<HrEmployeeListVo> mappedPage=new Page<>(sourcePage.getPageNum(),sourcePage.getPageSize());
            mappedPage.setTotal(sourcePage.getTotal());
            mappedPage.setOrderBy(sourcePage.getOrderBy());
            result=mappedPage;
        }
        else result = new ArrayList<>(rows.size());
        Map<Long,HrHealthCertificateVo> health = currentHealth(rows);
        for (SysUser row : rows)
        {
            HrEmployeeListVo item=toListVo(row);
            applyHealth(item,health.get(row.getUserId()));
            result.add(item);
        }
        return result;
    }

    @Override public HrEmployeeProfileVo get(Long userId)
    {
        SysUser row = scoped(userId);
        derivationService.applyToUser(row);
        HrEmployeeProfileVo result=toProfileVo(row);
        applyHealth(result,currentHealth(List.of(row)).get(row.getUserId()));
        return result;
    }

    @Override public HrEmployeeSummaryVo summary(HrEmployeeQuery query)
    {
        List<SysUser> rows = scopedEmployees(query,true);
        HrEmployeeSummaryVo summary = new HrEmployeeSummaryVo();
        summary.setTotalEmployeeCount(rows.size());
        long complete=0, requiredComplete=0, risk=0, coveragePercent=0, requiredPercent=0;
        for (SysUser row : rows)
        {
            HrEmployeeCompletenessSnapshot value = completenessOf(row);
            coveragePercent += value.getCoveragePercent();
            requiredPercent += value.getRequiredCompletionPercent();
            if (value.getCompletionPercent() == 100) complete++;
            if (value.getRequiredCompletionPercent() == 100) requiredComplete++;
            if (!"COMPLETE".equals(accountStatus(row))) risk++;
        }
        summary.setCompleteEmployeeCount(complete);
        summary.setIncompleteEmployeeCount(rows.size()-complete);
        summary.setRequiredCompleteEmployeeCount(requiredComplete);
        summary.setRequiredIncompleteEmployeeCount(rows.size()-requiredComplete);
        summary.setAccountConfigurationRiskCount(risk);
        int averageCoverage=rows.isEmpty() ? 0 : (int)(coveragePercent/rows.size());
        summary.setAverageProfileCompletionPercent(averageCoverage);
        summary.setAverageCoveragePercent(averageCoverage);
        summary.setAverageRequiredCompletionPercent(rows.isEmpty() ? 0 : (int)(requiredPercent/rows.size()));
        return summary;
    }

    @Override public Map<String, Object> formOptions()
    {
        List<Map<String, Object>> fields = new ArrayList<>();
        for (FieldDefinition field : registry.getFields())
        {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("key", field.getKey()); item.put("label", field.getLabel());
            item.put("storageOwner", field.getStorageOwner().name()); item.put("editable", field.isEditable());
            item.put("completenessGroup", field.getCompletenessGroup());
            item.put("sensitive", field.getMaskingClass() != MaskingClass.NONE);
            item.put("requirementTier", field.getRequirementTier().name());
            item.put("responsibility", field.getResponsibility().name());
            item.put("dueStage", field.getDueStage().name());
            item.put("riskLevel", field.getRiskLevel().name());
            fields.add(item);
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("fields", fields);
        result.put("sensitiveRevealFields", registry.getSensitiveRevealKeys());
        List<Map<String,Object>> departments=new ArrayList<>();
        for(SysDept dept:accessService.listScopedDepartments(new SysDept()))
        {
            Map<String,Object> item=new LinkedHashMap<>(); item.put("deptId",dept.getDeptId());
            item.put("deptName",dept.getDeptName()); item.put("deptType",dept.getDeptType()); departments.add(item);
        }
        result.put("departments",departments);
        List<Map<String,Object>> supervisors=new ArrayList<>();
        for(SysUser user:accessService.listScopedUserOptions(new SysUser()))
        {
            Map<String,Object> item=new LinkedHashMap<>(); item.put("userId",user.getUserId());
            item.put("employeeName",user.getNickName()); supervisors.add(item);
        }
        result.put("supervisors",supervisors);
        result.put("posts",postOptions());
        List<String> dictionaryFields=registry.getFields().stream().map(FieldDefinition::getKey).toList();
        result.put("dictionaries",onboardingConfigService==null?Collections.emptyMap():
                onboardingConfigService.loadDictionaries(dictionaryFields));
        Map<String,Object> enums=new LinkedHashMap<>();
        enums.put("employeeStatus",optionValues(HrEmployeeStatusCatalog.values().toArray(String[]::new)));
        enums.put("foreignNationalFlag",optionValues("0","1"));
        result.put("enumOptions",enums);
        return result;
    }

    @Override public HrEmployeeProfileVo derivedPreview(Map<String, Object> values)
    {
        SysUser user = new SysUser();
        if (values != null)
        {
            RelationInput relations=relations(values);
            if(relations.deptPresent)user.setDeptId(relations.deptId);
            if(relations.postsPresent)user.setPostIds(relations.postIds.toArray(Long[]::new));
            for (Map.Entry<String,Object> entry : values.entrySet())
            {
                if(isRelationKey(entry.getKey()))continue;
                FieldDefinition field=registry.getByKey(entry.getKey());
                if(field==null)throw new ServiceException("未知员工档案字段: "+entry.getKey());
                if(field.getStorageOwner()==HrEmployeeFieldRegistry.StorageOwner.DERIVED||!field.isEditable())
                    throw new ServiceException(field.getLabel()+"为只读字段");
                if(field.getMaskingClass()!=MaskingClass.NONE&&isMaskPlaceholder(entry.getValue()))
                    throw new ServiceException(field.getLabel()+"不能提交脱敏占位符");
                registry.write(user, field, entry.getValue());
            }
        }
        user.setProfile(derivationService.preview(user));
        return toProfileVo(user);
    }

    @Override
    @Transactional(rollbackFor=Exception.class)
    public HrEmployeeProfileVo initializeProfile(Long userId,String operator)
    {
        if(userId==null||userId<=0)throw new ServiceException("员工ID不能为空");
        if(operator==null||operator.isBlank())throw new ServiceException("操作人不能为空");
        HrEmployeeQuery query=new HrEmployeeQuery();query.setUserId(userId);
        SysUser employee=accessService.lockActiveScoped(query);
        if(userService==null)throw new ServiceException("员工保护校验不可用");
        userService.checkUserAllowed(employee);

        SysUserProfile profile=profileMapper.selectUserProfileByUserId(userId);
        if(profile==null)
        {
            profileMapper.insertUserProfileIfAbsent(userId,operator);
            profile=profileMapper.selectUserProfileByUserId(userId);
            if(profile==null)throw new ServiceException("员工档案初始化失败");
        }
        employee.setProfile(profile);
        derivationService.applyToUser(employee);
        return toProfileVo(employee);
    }

    @Override @Transactional(rollbackFor = Exception.class)
    public HrEmployeeProfileVo update(Long userId, Map<String, Object> patch, String operator)
    {
        if (patch == null || patch.isEmpty()) throw new ServiceException("员工档案修改内容不能为空");
        HrEmployeeQuery lockQuery=new HrEmployeeQuery();lockQuery.setUserId(userId);
        SysUser existing = accessService.lockScoped(lockQuery);
        if(userService==null)throw new ServiceException("员工保护校验不可用");
        userService.checkUserAllowed(existing);
        ensureProfile(existing);
        RelationInput relations=relations(patch);
        Map<String,Object> userValues=new LinkedHashMap<>();
        Map<String,Object> profileValues=new LinkedHashMap<>();
        if(relations.deptPresent){existing.setDeptId(relations.deptId);userValues.put("deptId",relations.deptId);}
        for (Map.Entry<String,Object> entry : patch.entrySet())
        {
            if(isRelationKey(entry.getKey()))continue;
            FieldDefinition field=registry.getByKey(entry.getKey());
            if (field == null) throw new ServiceException("未知员工档案字段: "+entry.getKey());
            if (field.getStorageOwner() == HrEmployeeFieldRegistry.StorageOwner.DERIVED || !field.isEditable())
                throw new ServiceException(field.getLabel()+"为只读字段");
            if (field.getMaskingClass()!=MaskingClass.NONE && isMaskPlaceholder(entry.getValue()))
                throw new ServiceException(field.getLabel()+"不能提交脱敏占位符");
            if("directSupervisorUserId".equals(field.getKey())&&entry.getValue()!=null)
                scoped(Long.valueOf(String.valueOf(entry.getValue())));
            registry.write(existing, field, entry.getValue());
            Object normalized=registry.read(existing,field);
            if(field.getStorageOwner()==HrEmployeeFieldRegistry.StorageOwner.SYS_USER)
                userValues.put(field.getKey(),normalized);
            else profileValues.put(field.getKey(),normalized);
        }
        existing.setUserId(userId);
        if(patch.containsKey("phoneNumber")&&!userService.checkPhoneUnique(existing))
            throw new ServiceException("手机号已存在");
        if(patch.containsKey("email")&&!userService.checkEmailUnique(existing))
            throw new ServiceException("邮箱已存在");
        if (!userValues.isEmpty()&&userMapper.patchHrEmployeeUser(userId,userValues,operator) != 1)
            throw new ServiceException("员工基础信息更新失败");
        if (!profileValues.isEmpty()&&profileMapper.patchUserProfile(userId,profileValues,operator) != 1)
            throw new ServiceException("员工档案更新失败");
        if(relations.postsPresent)replacePosts(userId,relations.postIds);
        derivationService.applyToUser(existing);
        return toProfileVo(existing);
    }

    @Override @Transactional(rollbackFor=Exception.class)
    public HrEmployeeProfileVo updateLegacy(Map<String,Object> input,String operator)
    {
        if(input==null||!input.containsKey("userId"))throw new ServiceException("员工ID不能为空");
        Long userId=parseLegacyUserId(input.get("userId"));
        Map<String,Object> patch=new LinkedHashMap<>();
        for(Map.Entry<String,Object> entry:input.entrySet())
        {
            if("userId".equals(entry.getKey())||"profile".equals(entry.getKey()))continue;
            normalizeLegacyEntry(entry.getKey(),entry.getValue(),patch);
        }
        Object nested=input.get("profile");
        if(nested!=null)
        {
            if(!(nested instanceof Map<?,?> profile))throw new ServiceException("员工档案格式无效");
            if(profile.containsKey("userId")&&!userId.equals(parseLegacyUserId(profile.get("userId"))))
                throw new ServiceException("员工ID不一致");
            for(Map.Entry<?,?> entry:profile.entrySet())
            {
                String key=String.valueOf(entry.getKey());if("userId".equals(key))continue;
                normalizeLegacyEntry(key,entry.getValue(),patch);
            }
        }
        return update(userId,patch,operator);
    }

    private void normalizeLegacyEntry(String originalKey,Object value,Map<String,Object> patch)
    {
        String key="nickName".equals(originalKey)?"employeeName":
                "phonenumber".equals(originalKey)?"phoneNumber":originalKey;
        if(isRelationKey(key)){patch.put(key,value);return;}
        FieldDefinition field=registry.getByKey(key);
        if(field==null)
        {
            if(LEGACY_IGNORED_KEYS.contains(originalKey)||LEGACY_IGNORED_KEYS.contains(key))return;
            throw new ServiceException("未知员工档案字段: "+originalKey);
        }
        if(!field.isEditable()||field.getStorageOwner()==HrEmployeeFieldRegistry.StorageOwner.DERIVED)return;
        if(field.getMaskingClass()!=MaskingClass.NONE&&isMaskPlaceholder(value))return;
        patch.put(field.getKey(),value);
    }

    private Long parseLegacyUserId(Object value)
    {
        try{return Long.valueOf(String.valueOf(value));}
        catch(RuntimeException invalid){throw new ServiceException("员工ID格式无效");}
    }

    @Override public HrEmployeeProfileVo completeness(Long userId) { return get(userId); }

    @Override public List<HrEmployeeListVo> completenessEmployees(HrEmployeeQuery query)
    {
        return employeeList(query == null ? new HrEmployeeQuery() : query,true);
    }

    @Override public List<Map<String, Object>> completenessDepartments(HrEmployeeQuery query)
    {
        List<SysUser> rows=scopedEmployees(query,true);
        Map<Long, DepartmentAccumulator> grouped=new LinkedHashMap<>();
        for (SysUser row:rows)
        {
            Long deptId=row.getDeptId();
            String name=row.getDept()==null?null:row.getDept().getDeptName();
            DepartmentAccumulator value=grouped.computeIfAbsent(deptId, ignored->new DepartmentAccumulator(deptId,name));
            HrEmployeeCompletenessSnapshot completeness=completenessOf(row);
            value.total++; value.coveragePercent+=completeness.getCoveragePercent();
            value.requiredPercent+=completeness.getRequiredCompletionPercent();
        }
        List<Map<String,Object>> result=new ArrayList<>();
        for(DepartmentAccumulator value:grouped.values())
        {
            Map<String,Object> item=new LinkedHashMap<>();
            item.put("departmentId",value.id); item.put("departmentName",value.name);
            item.put("employeeCount",value.total);
            int coverage=value.total==0?0:(int)(value.coveragePercent/value.total);
            item.put("profileCompletionPercent",coverage);
            item.put("coveragePercent",coverage);
            item.put("requiredCompletionPercent",value.total==0?0:(int)(value.requiredPercent/value.total));
            result.add(item);
        }
        return result;
    }

    @Override @Transactional(rollbackFor = Exception.class)
    public HrSensitiveRevealVo reveal(Long userId, String fieldKey, Long operatorUserId,
            String operatorName, String requestIp)
    {
        requireSensitiveKey(fieldKey);
        SysUser employee=scoped(userId); // Scope before reading or auditing the sensitive value.
        Object value=registry.read(employee,registry.getByKey(fieldKey));
        HrSensitiveAccessLog log=audit("REVEAL",operatorUserId,operatorName,requestIp);
        log.setEmployeeUserId(userId); log.setFieldKey(fieldKey); log.setResult("SUCCESS");
        log.setResultMessage(value==null?"EMPTY":"REVEALED");
        insertAudit(log);
        return new HrSensitiveRevealVo(fieldKey,value);
    }

    @Override
    public HrSensitiveExportArtifact exportSensitive(HrEmployeeQuery query, Set<String> requestedFields,
            Long operatorUserId, String operatorName, String requestIp)
    {
        List<FieldDefinition> requested=orderedSensitiveFields(requestedFields);
        HrEmployeeQuery safeQuery=copyQuery(query);
        normalizeCompletenessStatus(safeQuery);
        safeQuery.setMaxRows(SENSITIVE_EXPORT_ROW_CAP+1);
        String scope=filterSummary(safeQuery,requested,0);
        String failureReason="PREPARATION_FAILED";
        try
        {
            List<SysUser> employees=scopedEmployees(safeQuery);
            scope=filterSummary(safeQuery,requested,employees.size());
            if(employees.size()>SENSITIVE_EXPORT_ROW_CAP)
            {
                failureReason="ROW_LIMIT_EXCEEDED";
                throw new ServiceException("敏感导出单次最多允许10000行，请缩小筛选范围");
            }
            List<String> columns=new ArrayList<>();columns.add("employeeNo");columns.add("employeeName");
            for(FieldDefinition field:requested)columns.add(field.getKey());
            List<Map<String,Object>> rows=new ArrayList<>(employees.size());
            for(SysUser employee:employees)
            {
                Map<String,Object> row=new LinkedHashMap<>();
                row.put("employeeNo",employee.getProfile()==null?null:employee.getProfile().getEmployeeNo());
                row.put("employeeName",employee.getNickName());
                for(FieldDefinition field:requested)row.put(field.getKey(),registry.read(employee,field));
                rows.add(row);
            }
            if(workbookGenerator==null||exportAuditService==null)throw new ServiceException("敏感导出服务不可用");
            failureReason="GENERATION_FAILED";
            byte[] content=workbookGenerator.generate(columns,rows);
            failureReason="SUCCESS_AUDIT_FAILED";
            exportAuditService.recordGenerated(scope,operatorUserId,operatorName,requestIp);
            return new HrSensitiveExportArtifact(content,scope,employees.size());
        }
        catch(RuntimeException failure)
        {
            if(exportAuditService!=null)
            {
                try{exportAuditService.recordFailure(scope,operatorUserId,operatorName,requestIp,failureReason);}
                catch(RuntimeException auditFailure){failure.addSuppressed(auditFailure);}
            }
            throw failure;
        }
    }

    @Override
    public void recordSensitiveExportDeliveryFailure(String scope,Long operatorUserId,String operatorName,String requestIp)
    {
        if(exportAuditService==null)throw new ServiceException("敏感导出审计不可用");
        exportAuditService.recordFailure(scope,operatorUserId,operatorName,requestIp,"DELIVERY_FAILED");
    }

    private HrEmployeeQuery copyQuery(HrEmployeeQuery source)
    {
        HrEmployeeQuery target=new HrEmployeeQuery();if(source==null)return target;
        target.setUserId(source.getUserId());target.setKeyword(source.getKeyword());target.setDeptId(source.getDeptId());
        target.setEmployeeStatus(source.getEmployeeStatus());target.setEmployeeCategory(source.getEmployeeCategory());
        target.setCompletenessStatus(source.getCompletenessStatus());
        target.setCompletenessMetric(source.getCompletenessMetric());
        target.setAccountConfigurationStatus(source.getAccountConfigurationStatus());
        target.setHealthCertificateStatus(source.getHealthCertificateStatus());
        target.setHealthCertificateExpiresFrom(source.getHealthCertificateExpiresFrom());
        target.setHealthCertificateExpiresTo(source.getHealthCertificateExpiresTo());
        return target;
    }

    private Map<Long,HrHealthCertificateVo> currentHealth(List<SysUser> users)
    {
        if(healthCertificateService==null||users==null||users.isEmpty())return Collections.emptyMap();
        return healthCertificateService.selectCurrentProjection(users.stream().map(SysUser::getUserId).toList());
    }

    private void applyHealth(HrEmployeeListVo target,HrHealthCertificateVo health)
    {
        target.setHealthCertificateStatus(health==null?"NOT_SUBMITTED":health.getHealthCertificateStatus());
        target.setHealthCertificateIssuedDate(health==null?null:health.getIssuedDate());
        target.setHealthCertificateExpiresOn(health==null?null:health.getExpiresOn());
        target.setHealthCertificateDaysRemaining(health==null?null:health.getDaysRemaining());
        target.setHealthCertificateAttachmentPresent(health!=null&&Boolean.TRUE.equals(health.getAttachmentPresent()));
    }

    private void applyHealth(HrEmployeeProfileVo target,HrHealthCertificateVo health)
    {
        target.setHealthCertificateStatus(health==null?"NOT_SUBMITTED":health.getHealthCertificateStatus());
        target.setHealthCertificateIssuedDate(health==null?null:health.getIssuedDate());
        target.setHealthCertificateExpiresOn(health==null?null:health.getExpiresOn());
        target.setHealthCertificateDaysRemaining(health==null?null:health.getDaysRemaining());
        target.setHealthCertificateAttachmentPresent(health!=null&&Boolean.TRUE.equals(health.getAttachmentPresent()));
    }

    private SysUser scoped(Long userId)
    {
        HrEmployeeQuery query=new HrEmployeeQuery(); query.setUserId(userId);
        return accessService.findScoped(query);
    }

    private HrEmployeeListVo toListVo(SysUser user)
    {
        HrEmployeeListVo target=new HrEmployeeListVo();
        target.setUserId(user.getUserId());
        target.setProfileInitialized(user.getProfile().getProfileId()!=null);
        target.setEmployeeName(text(registry.read(user,registry.getByKey("employeeName"))));
        target.setPhoneNumberMasked(masker.maskPhone(text(registry.read(user,registry.getByKey("phoneNumber")))));
        target.setRemark(text(registry.read(user,registry.getByKey("remark"))));
        target.setDepartmentName(user.getDept()==null?null:user.getDept().getDeptName());
        target.setEmployeeNo(text(registry.read(user,registry.getByKey("employeeNo"))));
        target.setPositionNo(user.getProfile()==null?null:user.getProfile().getPositionNo());
        target.setPositionName(text(registry.read(user,registry.getByKey("positionName"))));
        target.setEmployeeStatus(HrEmployeeStatusCatalog.normalizeForRead(
                text(registry.read(user,registry.getByKey("employeeStatus")))));
        target.setEmployeeCategory(text(registry.read(user,registry.getByKey("employeeCategory"))));
        target.setDepartmentSupervisor(text(registry.read(user,registry.getByKey("departmentSupervisor"))));
        target.setDirectSupervisor(user.getProfile()==null?null:user.getProfile().getDirectSupervisor());
        target.setContractStartDate(text(clientFieldValue(registry.read(user,registry.getByKey("contractStartDate")))));
        target.setContractEndDate(text(clientFieldValue(registry.read(user,registry.getByKey("contractEndDate")))));
        target.setContractType(text(registry.read(user,registry.getByKey("contractType"))));
        target.setSocialType(text(registry.read(user,registry.getByKey("socialType"))));
        target.setSocialSecurityLocation(text(registry.read(user,registry.getByKey("socialSecurityLocation"))));
        target.setHousingFundLocation(text(registry.read(user,registry.getByKey("housingFundLocation"))));
        target.setLegalEntity(text(registry.read(user,registry.getByKey("legalEntity"))));
        target.setRecruitmentChannel(text(registry.read(user,registry.getByKey("recruitmentChannel"))));
        target.setLeaveDate(text(clientFieldValue(registry.read(user,registry.getByKey("leaveDate")))));
        applyCompleteness(target,completenessOf(user));
        target.setAccountEnabled("0".equals(user.getStatus())&&"0".equals(user.getDelFlag()));
        target.setAccountConfigurationStatus(accountStatus(user));
        target.setAccountConfigurationRiskCodes(accountRisks(user));
        target.setOnboardingDetailUrl("/hr/onboarding?linkedUserId="+user.getUserId());
        target.setEmployeeDetailUrl("/hr/employee/"+user.getUserId());
        target.setAccountConfigurationUrl("/system/user-auth/role/"+user.getUserId());
        return target;
    }

    private HrEmployeeProfileVo toProfileVo(SysUser user)
    {
        HrEmployeeProfileVo target=new HrEmployeeProfileVo();
        target.setUserId(user.getUserId());target.setDeptId(user.getDeptId());
        target.setProfileInitialized(user.getProfile().getProfileId()!=null);
        target.setDepartmentName(user.getDept()==null?null:user.getDept().getDeptName());
        target.setPostIds(user.getUserId()==null||userPostMapper==null?Collections.emptyList():
                userPostMapper.selectPostIdsByUserId(user.getUserId()));
        target.setPostNames(displayPostNames(user));
        target.setEmployeeName(user.getNickName()); target.setRemark(user.getRemark());
        target.setNickName(user.getNickName());target.setPhonenumber(masker.maskPhone(user.getPhonenumber()));
        target.setEmail(user.getEmail());target.setSex(user.getSex());target.setStatus(user.getStatus());
        if(user.getProfile()!=null)
        {
            target.setEmployeeNo(user.getProfile().getEmployeeNo());
            target.setPositionNo(user.getProfile().getPositionNo());
        }
        Map<String,Object> fields=new LinkedHashMap<>();
        for(FieldDefinition field:registry.getFields())
        {
            Object value=clientFieldValue("positionName".equals(field.getKey())
                    ?displayPostNames(user):registry.read(user,field));
            if("employeeStatus".equals(field.getKey()))
                value=HrEmployeeStatusCatalog.normalizeForRead(value==null?null:String.valueOf(value));
            if(field.getMaskingClass()!=MaskingClass.NONE) value=masked(field.getMaskingClass(),value);
            fields.put(field.getKey(),value);
        }
        fields.put("accountCreateBy",user.getCreateBy());fields.put("accountCreateTime",user.getCreateTime());
        fields.put("accountUpdateBy",user.getUpdateBy());fields.put("accountUpdateTime",user.getUpdateTime());
        SysUserProfile profile=user.getProfile();
        fields.put("profileCreateBy",profile==null?null:profile.getCreateBy());
        fields.put("profileCreateTime",profile==null?null:profile.getCreateTime());
        fields.put("profileUpdateBy",profile==null?null:profile.getUpdateBy());
        fields.put("profileUpdateTime",profile==null?null:profile.getUpdateTime());
        target.setFields(fields);
        Map<String,Object> compatibilityProfile=new LinkedHashMap<>();
        compatibilityProfile.put("userId",user.getUserId());
        if(user.getProfile()!=null)compatibilityProfile.put("profileId",user.getProfile().getProfileId());
        compatibilityProfile.put("positionNo",target.getPositionNo());
        for(FieldDefinition field:registry.getFields())
            if(field.getStorageOwner()!=HrEmployeeFieldRegistry.StorageOwner.SYS_USER)
                compatibilityProfile.put(field.getKey(),fields.get(field.getKey()));
        compatibilityProfile.put("positionNames",target.getPostNames());
        compatibilityProfile.put("directSupervisor",user.getProfile()==null?null:user.getProfile().getDirectSupervisor());
        target.setProfile(compatibilityProfile);
        target.setPhoneNumberMasked(masker.maskPhone(user.getPhonenumber()));
        if(user.getProfile()!=null)
        {
            target.setIdNumberMasked(masker.maskIdNumber(user.getProfile().getIdNumber()));
            target.setBankAccountMasked(masker.maskBankAccount(user.getProfile().getBankAccount()));
            target.setRegisteredResidenceMasked(masker.maskAddress(user.getProfile().getRegisteredResidence()));
            target.setCurrentAddressMasked(masker.maskAddress(user.getProfile().getCurrentAddress()));
            target.setEmergencyContactPhoneMasked(masker.maskPhone(user.getProfile().getEmergencyContactPhone()));
            target.setOfficePhoneMasked(masker.maskPhone(user.getProfile().getOfficePhone()));
        }
        HrEmployeeCompletenessSnapshot completeness=completenessOf(user);
        applyCompleteness(target,completeness);
        return target;
    }

    private void applyCompleteness(HrEmployeeListVo target,HrEmployeeCompletenessSnapshot value)
    {
        target.setProfileCompletionPercent(value.getCompletionPercent());
        target.setProfileCompletedFieldCount(value.getCompletedFieldCount());
        target.setProfileApplicableFieldCount(value.getApplicableFieldCount());
        target.setProfileNotApplicableFieldCount(value.getNotApplicableFieldCount());
        target.setProfileTrackedFieldCount(value.getTrackedFieldCount());
        target.setMissingProfileFields(value.getMissingFields());
        target.setRequiredCompletionPercent(value.getRequiredCompletionPercent());
        target.setRequiredCompletedFieldCount(value.getRequiredCompletedFieldCount());
        target.setRequiredApplicableFieldCount(value.getRequiredApplicableFieldCount());
        target.setCoveragePercent(value.getCoveragePercent());
        target.setMissingRequiredFields(value.getMissingRequiredFields());
        target.setMissingOptionalFields(value.getMissingOptionalFields());
        target.setMissingByResponsibility(value.getMissingByResponsibility());
    }

    private void applyCompleteness(HrEmployeeProfileVo target,HrEmployeeCompletenessSnapshot value)
    {
        target.setProfileCompletionPercent(value.getCompletionPercent());
        target.setProfileCompletedFieldCount(value.getCompletedFieldCount());
        target.setProfileApplicableFieldCount(value.getApplicableFieldCount());
        target.setProfileNotApplicableFieldCount(value.getNotApplicableFieldCount());
        target.setProfileTrackedFieldCount(value.getTrackedFieldCount());
        target.setMissingProfileFields(value.getMissingFields());
        target.setRequiredCompletionPercent(value.getRequiredCompletionPercent());
        target.setRequiredCompletedFieldCount(value.getRequiredCompletedFieldCount());
        target.setRequiredApplicableFieldCount(value.getRequiredApplicableFieldCount());
        target.setCoveragePercent(value.getCoveragePercent());
        target.setMissingRequiredFields(value.getMissingRequiredFields());
        target.setMissingOptionalFields(value.getMissingOptionalFields());
        target.setMissingByResponsibility(value.getMissingByResponsibility());
    }

    private Object clientFieldValue(Object value)
    {
        if(value instanceof java.sql.Date date)return date.toLocalDate().format(DateTimeFormatter.ISO_LOCAL_DATE);
        if(value instanceof Date date)
            return DateTimeFormatter.ISO_LOCAL_DATE.format(date.toInstant().atZone(BUSINESS_ZONE).toLocalDate());
        return value;
    }

    private String displayPostNames(SysUser user)
    {
        if(user!=null&&user.getPostNames()!=null&&!user.getPostNames().isBlank())return user.getPostNames();
        return text(registry.read(user,registry.getByKey("positionName")));
    }

    private Object masked(MaskingClass type,Object value)
    {
        String text=value==null?null:String.valueOf(value);
        return switch(type) {
            case PHONE -> masker.maskPhone(text); case ID_NUMBER -> masker.maskIdNumber(text);
            case BANK_ACCOUNT -> masker.maskBankAccount(text); case ADDRESS -> masker.maskAddress(text);
            case SALARY -> value == null ? null : "******";
            default -> value;
        };
    }

    private HrEmployeeCompletenessSnapshot completenessOf(SysUser user)
    { return completenessEvaluator.evaluate(user); }

    private List<SysUser> scopedEmployees(HrEmployeeQuery input)
    {
        return scopedEmployees(input,false);
    }

    private List<SysUser> scopedEmployees(HrEmployeeQuery input,boolean activeGovernanceOnly)
    {
        HrEmployeeQuery query=input==null?new HrEmployeeQuery():input;
        String completenessStatus=normalizeCompletenessStatus(query);
        String completenessMetric=normalizeCompletenessMetric(query);
        if(completenessStatus!=null)query.setMaxRows(COMPLETENESS_SCAN_ROW_CAP+1);
        List<SysUser> rows=activeGovernanceOnly
                ?accessService.listActiveScoped(query):accessService.listScoped(query);
        if(completenessStatus==null){derivationService.applyToUsers(rows);return rows;}
        if(rows.size()>COMPLETENESS_SCAN_ROW_CAP)
            throw new ServiceException("完整度扫描超过10000行，请缩小筛选范围后重试");
        if(rows instanceof Page<?>)throw new ServiceException("完整度筛选必须在数据库分页前执行");
        derivationService.applyToUsers(rows);
        List<SysUser> filtered=new ArrayList<>();
        for(SysUser row:rows)
        {
            HrEmployeeCompletenessSnapshot snapshot=completenessOf(row);
            boolean complete=("REQUIRED".equals(completenessMetric)
                    ?snapshot.getRequiredCompletionPercent():snapshot.getCoveragePercent())==100;
            if(("COMPLETE".equals(completenessStatus)&&complete)||
                    ("INCOMPLETE".equals(completenessStatus)&&!complete))filtered.add(row);
        }
        return filtered;
    }

    private String normalizeCompletenessStatus(HrEmployeeQuery query)
    {
        if(query==null||query.getCompletenessStatus()==null||query.getCompletenessStatus().isBlank())return null;
        String value=query.getCompletenessStatus().trim().toUpperCase(java.util.Locale.ROOT);
        if(!"COMPLETE".equals(value)&&!"INCOMPLETE".equals(value))
            throw new ServiceException("完整度筛选值无效");
        query.setCompletenessStatus(value);
        return value;
    }

    private String normalizeCompletenessMetric(HrEmployeeQuery query)
    {
        if(query==null||query.getCompletenessMetric()==null||query.getCompletenessMetric().isBlank())
            return "COVERAGE";
        String value=query.getCompletenessMetric().trim().toUpperCase(java.util.Locale.ROOT);
        if(!"COVERAGE".equals(value)&&!"REQUIRED".equals(value))
            throw new ServiceException("完整度口径无效");
        query.setCompletenessMetric(value);
        return value;
    }

    private String accountStatus(SysUser user) { return "complete".equals(user.getSetupStatus())?"COMPLETE":"MISSING"; }
    private List<String> accountRisks(SysUser user)
    {
        if(user==null||user.getSetupStatus()==null||"complete".equals(user.getSetupStatus()))return Collections.emptyList();
        List<String> risks=new ArrayList<>();
        if("missingRole".equals(user.getSetupStatus())||"missingRoleAndShopScope".equals(user.getSetupStatus()))
            risks.add("ROLE_CONFIGURATION_MISSING");
        if("missingShopScope".equals(user.getSetupStatus())||"missingRoleAndShopScope".equals(user.getSetupStatus()))
            risks.add("DATA_SCOPE_CONFIGURATION_MISSING");
        if("missingConfiguration".equals(user.getSetupStatus()))risks.add("ACCOUNT_CONFIGURATION_MISSING");
        return risks;
    }
    private void ensureProfile(SysUser user) { if(user.getProfile()==null){SysUserProfile p=new SysUserProfile();p.setUserId(user.getUserId());user.setProfile(p);} }
    private boolean notBlank(String value){return value!=null&&!value.isBlank();}
    private boolean isMaskPlaceholder(Object value)
    {return value instanceof String && MASK_PLACEHOLDER.matcher((String)value).find();}

    private boolean isRelationKey(String key)
    {return "deptId".equals(key)||"postId".equals(key)||"postIds".equals(key);}

    private RelationInput relations(Map<String,Object> values)
    {
        RelationInput result=new RelationInput();
        if(values==null)return result;
        result.deptPresent=values.containsKey("deptId");
        if(result.deptPresent)
        {
            Object raw=values.get("deptId");
            if(raw==null)throw new ServiceException("目标组织不能为空");
            try{result.deptId=Long.valueOf(String.valueOf(raw));}
            catch(RuntimeException invalid){throw new ServiceException("目标组织格式无效");}
            accessService.requireScopedDepartment(result.deptId);
        }
        boolean singular=values.containsKey("postId");
        boolean plural=values.containsKey("postIds");
        if(singular&&plural)throw new ServiceException("岗位参数 postId 与 postIds 不能同时提交");
        result.postsPresent=singular||plural;
        if(!result.postsPresent)return result;
        Object raw=values.get(singular?"postId":"postIds");
        LinkedHashSet<Long> ids=new LinkedHashSet<>();
        if(raw instanceof Collection<?> collection)
            for(Object item:collection)addPostId(ids,item);
        else if(raw!=null&&raw.getClass().isArray())
            for(int i=0;i<Array.getLength(raw);i++)addPostId(ids,Array.get(raw,i));
        else if(raw!=null)addPostId(ids,raw);
        for(Long postId:ids)
        {
            if(postMapper==null)throw new ServiceException("岗位校验不可用");
            SysPost post=postMapper.selectPostById(postId);
            if(post==null||!"0".equals(post.getStatus()))throw new ServiceException("岗位不存在或已停用");
        }
        result.postIds=new ArrayList<>(ids);
        return result;
    }

    private void addPostId(Set<Long> ids,Object raw)
    {
        if(raw==null)throw new ServiceException("岗位不能为空");
        try{ids.add(Long.valueOf(String.valueOf(raw)));}
        catch(RuntimeException invalid){throw new ServiceException("岗位格式无效");}
    }

    private void replacePosts(Long userId,List<Long> postIds)
    {
        if(userPostMapper==null)throw new ServiceException("岗位关系更新不可用");
        userPostMapper.deleteUserPostByUserId(userId);
        if(postIds.isEmpty())return;
        List<SysUserPost> relations=new ArrayList<>();
        for(Long postId:postIds)
        {
            SysUserPost relation=new SysUserPost();relation.setUserId(userId);relation.setPostId(postId);
            relations.add(relation);
        }
        if(userPostMapper.batchUserPost(relations)!=relations.size())
            throw new ServiceException("员工岗位关系更新失败");
    }

    private List<Map<String,Object>> postOptions()
    {
        if(postMapper==null)return Collections.emptyList();
        SysPost query=new SysPost();query.setStatus("0");
        List<SysPost> values=postMapper.selectPostList(query);
        if(values==null)return Collections.emptyList();
        Comparator<SysPost> ordering=Comparator.comparing(SysPost::getPostSort,Comparator.nullsLast(Integer::compareTo))
                .thenComparing(SysPost::getPostName,Comparator.nullsLast(String::compareTo))
                .thenComparing(SysPost::getPostId,Comparator.nullsLast(Long::compareTo));
        List<Map<String,Object>> result=new ArrayList<>();
        values.stream().filter(value->value!=null&&"0".equals(value.getStatus())).sorted(ordering).forEach(value->{
            Map<String,Object> item=new LinkedHashMap<>();item.put("postId",value.getPostId());
            item.put("postCode",value.getPostCode());item.put("postName",value.getPostName());result.add(item);
        });
        return result;
    }

    private List<Map<String,Object>> optionValues(String... values)
    {
        List<Map<String,Object>> result=new ArrayList<>();
        for(String value:values)
        {Map<String,Object> item=new LinkedHashMap<>();item.put("label",value);item.put("value",value);result.add(item);}
        return result;
    }
    private void requireSensitiveKey(String key)
    { if(key==null||!registry.getSensitiveRevealKeys().contains(key))throw new ServiceException("不允许访问该敏感字段"); }

    private List<FieldDefinition> orderedSensitiveFields(Set<String> requested)
    {
        if(requested==null||requested.isEmpty())throw new ServiceException("敏感导出字段不能为空");
        Set<String> unique=new LinkedHashSet<>(requested);
        for(String key:unique)requireSensitiveKey(key);
        List<FieldDefinition> result=new ArrayList<>();
        for(FieldDefinition field:registry.getFields())if(unique.contains(field.getKey()))result.add(field);
        return result;
    }

    private HrSensitiveAccessLog audit(String action,Long operatorId,String operatorName,String ip)
    {
        if(operatorId==null||operatorName==null||operatorName.isBlank())throw new ServiceException("敏感访问操作人不能为空");
        HrSensitiveAccessLog log=new HrSensitiveAccessLog();
        log.setActionType(action); log.setOperatorUserId(operatorId); log.setOperatorName(operatorName);
        log.setRequestIp(ip); log.setEventTime(new Date()); log.setCreateBy(operatorName);
        return log;
    }

    private void insertAudit(HrSensitiveAccessLog log)
    { if(auditMapper.insertSensitiveAccessLog(log)!=1)throw new ServiceException("敏感访问审计写入失败"); }

    private String filterSummary(HrEmployeeQuery query,List<FieldDefinition> fields,int count)
    {
        List<String> keys=new ArrayList<>(); for(FieldDefinition field:fields)keys.add(field.getKey());
        return "fields="+String.join(",",keys)+";keywordPresent="+notBlank(query.getKeyword())+
                ";deptId="+query.getDeptId()+";employeeStatus="+safe(query.getEmployeeStatus())+
                ";employeeCategory="+safe(query.getEmployeeCategory())+
                ";completenessStatus="+safe(query.getCompletenessStatus())+
                ";completenessMetric="+safe(query.getCompletenessMetric())+
                ";accountConfigurationStatus="+safe(query.getAccountConfigurationStatus())+";rowCount="+count;
    }
    private String safe(String value){return value==null?"":value.replaceAll("[^a-zA-Z0-9_\\-\\u4e00-\\u9fa5]","");}
    private String text(Object value){return value==null?null:String.valueOf(value);}
    private static final class DepartmentAccumulator
    {
        final Long id; final String name; int total; long coveragePercent; long requiredPercent;
        DepartmentAccumulator(Long i,String n){id=i;name=n;}
    }
    private static final class RelationInput
    { boolean deptPresent;Long deptId;boolean postsPresent;List<Long> postIds=Collections.emptyList(); }
}
