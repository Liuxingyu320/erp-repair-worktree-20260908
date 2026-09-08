package com.erp.system.support;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.Objects;
import java.util.function.LongSupplier;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import com.erp.common.security.utils.SecurityUtils;
import com.erp.system.api.domain.SysDept;
import com.erp.system.api.domain.SysUser;
import com.erp.system.domain.HrOnboarding;
import com.erp.system.domain.SysPost;
import com.erp.system.domain.vo.HrOnboardingConflictVo;
import com.erp.system.mapper.SysPostMapper;
import com.erp.system.service.IHrOnboardingPositionConfigService;
import com.erp.system.service.impl.HrOnboardingAccessService;
import com.erp.system.service.impl.HrOnboardingConflictService;

/** Scoped preview resolver and conflict classifier used by the production parser. */
@Component
public class HrOnboardingImportPreviewValidator implements HrOnboardingExcelParser.BusinessValidator
{
    private final HrOnboardingAccessService accessService;
    private final SysPostMapper postMapper;
    private final IHrOnboardingPositionConfigService configService;
    private final HrOnboardingConflictService conflictService;
    private final LongSupplier currentUserId;
    private final ThreadLocal<PreviewContext> previewContext=new ThreadLocal<>();

    @Autowired
    public HrOnboardingImportPreviewValidator(HrOnboardingAccessService accessService, SysPostMapper postMapper,
            IHrOnboardingPositionConfigService configService, HrOnboardingConflictService conflictService)
    {
        this(accessService,postMapper,configService,conflictService,SecurityUtils::getUserId);
    }

    HrOnboardingImportPreviewValidator(HrOnboardingAccessService accessService, SysPostMapper postMapper,
            IHrOnboardingPositionConfigService configService, HrOnboardingConflictService conflictService,
            LongSupplier currentUserId)
    {
        this.accessService=accessService;this.postMapper=postMapper;this.configService=configService;
        this.conflictService=conflictService;this.currentUserId=currentUserId;
    }

    @Override
    public HrOnboardingExcelParser.ValidationOutcome validate(HrOnboarding row)
    {
        List<String> warnings=new ArrayList<>(),errors=new ArrayList<>();
        resolveOrganization(row,errors);resolvePost(row,errors);resolveSupervisor(row,errors);
        row.setOwnerUserId(currentUserId.getAsLong());
        validateCreateRequired(row,errors);
        validateDictionaries(row,errors);
        String candidateType=null,summary=null;Long candidateUserId=null,candidateOnboardingId=null;
        if(previewContext.get()!=null)
            return new HrOnboardingExcelParser.ValidationOutcome(warnings,errors,null,null,null,null);
        if(errors.isEmpty())
        {
            List<HrOnboardingConflictVo> conflicts=conflictService.findImportConflicts(row);
            if(conflicts!=null)for(HrOnboardingConflictVo conflict:conflicts)
            {
                if("ONBOARDING".equals(conflict.getSourceType()))
                {
                    if(Boolean.TRUE.equals(conflict.getBlocking())) add(errors,"ACTIVE_ONBOARDING");
                    else add(warnings,"CANCELLED_ONBOARDING");
                    if(candidateOnboardingId==null)candidateOnboardingId=conflict.getCandidateOnboardingId();
                }
                else if(Boolean.TRUE.equals(conflict.getEligibleForBind()))
                {
                    if(candidateUserId==null){candidateType="ACCOUNT";candidateUserId=conflict.getCandidateUserId();
                        summary=safeSummary(conflict);}
                }
                else if(Boolean.TRUE.equals(conflict.getBlocking())) add(errors,"EXISTING_EMPLOYEE_BLOCKING");
            }
            if(candidateType==null && errors.isEmpty() && row.getEmployeeName()!=null)
            {
                PreviewContext context=previewContext.get();
                List<SysUser> users=context==null?accessService.listScopedUsers(new SysUser()):context.users;
                SysUser similar=users.stream().filter(user->sameName(row.getEmployeeName(),user.getNickName()))
                        .findFirst().orElse(null);
                if(similar!=null)
                {
                    candidateType="SIMILAR";candidateUserId=similar.getUserId();
                    String name=trim(similar.getNickName());
                    summary="相似姓名候选: "+(name==null?"-":name.substring(0,1)+"**");
                }
            }
        }
        return new HrOnboardingExcelParser.ValidationOutcome(warnings,errors,candidateType,candidateUserId,
                candidateOnboardingId,summary);
    }

    @Override
    public void beginPreview()
    {
        SysPost query=new SysPost();query.setStatus("0");
        previewContext.set(new PreviewContext(accessService.listScopedDepartments(new SysDept()),
                accessService.listScopedUsers(new SysUser()),postMapper.selectPostList(query),configService.options()));
    }

    @Override public void endPreview(){previewContext.remove();}

    @Override
    public void completePreview(List<com.erp.system.domain.HrOnboardingImportRow> rows)
    {
        if(rows==null||rows.isEmpty())return;
        List<HrOnboarding> sources=rows.stream().map(com.erp.system.domain.HrOnboardingImportRow::getPayload)
                .collect(Collectors.toList());
        Map<HrOnboarding,List<HrOnboardingConflictVo>> all=conflictService.findImportConflictsBatch(sources);
        PreviewContext context=previewContext.get();
        for(com.erp.system.domain.HrOnboardingImportRow staged:rows)
        {
            if(!staged.getErrorCodeList().isEmpty())continue;
            HrOnboarding row=staged.getPayload();List<String> warnings=staged.getWarningCodeList();
            List<String> errors=staged.getErrorCodeList();String type=null,summary=null;Long userId=null,onboardingId=null;
            List<HrOnboardingConflictVo> conflicts=all==null||!all.containsKey(row)
                    ?conflictService.findImportConflicts(row):all.get(row);
            if(conflicts!=null)for(HrOnboardingConflictVo conflict:conflicts)
            {
                if("ONBOARDING".equals(conflict.getSourceType()))
                {if(Boolean.TRUE.equals(conflict.getBlocking()))add(errors,"ACTIVE_ONBOARDING");else add(warnings,"CANCELLED_ONBOARDING");
                    if(onboardingId==null)onboardingId=conflict.getCandidateOnboardingId();}
                else if(Boolean.TRUE.equals(conflict.getEligibleForBind()))
                {if(userId==null){type="ACCOUNT";userId=conflict.getCandidateUserId();summary=safeSummary(conflict);}}
                else if(Boolean.TRUE.equals(conflict.getBlocking()))add(errors,"EXISTING_EMPLOYEE_BLOCKING");
            }
            if(type==null&&errors.isEmpty()&&row.getEmployeeName()!=null&&context!=null)
            {
                SysUser similar=context.users.stream().filter(u->sameName(row.getEmployeeName(),u.getNickName()))
                        .findFirst().orElse(null);
                if(similar!=null){type="SIMILAR";userId=similar.getUserId();String name=trim(similar.getNickName());
                    summary="相似姓名候选: "+(name==null?"-":name.substring(0,1)+"**");}
            }
            staged.setWarningCodeList(warnings);staged.setErrorCodeList(errors);staged.setCandidateType(type);
            staged.setCandidateUserId(userId);staged.setCandidateOnboardingId(onboardingId);staged.setCandidateSummary(summary);
            staged.setCategory(!errors.isEmpty()?"INVALID":userId!=null&&"ACCOUNT".equals(type)?"BINDABLE_ACCOUNT":
                    "SIMILAR".equals(type)?"POSSIBLE_DUPLICATE":!warnings.isEmpty()?"WARNING":"IMPORTABLE");
        }
    }

    private void resolveOrganization(HrOnboarding row,List<String> errors)
    {
        PreviewContext context=previewContext.get();
        List<SysDept> all=context==null?accessService.listScopedDepartments(new SysDept()):context.departments;
        List<String> supplied=new ArrayList<>();
        for(String value:new String[]{row.getCompanyName(),row.getDeptLevel1Name(),row.getDeptLevel2Name(),
                row.getDeptLevel3Name(),row.getStoreName()})if(trim(value)!=null)supplied.add(trim(value));
        String target=supplied.isEmpty()?null:supplied.get(supplied.size()-1);
        if(target==null){add(errors,"ORGANIZATION_UNRESOLVED");return;}
        List<SysDept> named=all.stream().filter(d->target.equals(trim(d.getDeptName()))).collect(Collectors.toList());
        if(row.getStoreName()!=null && named.stream().noneMatch(d->"STORE".equals(d.getDeptType())))
        {add(errors,"STORE_UNRESOLVED");return;}
        List<SysDept> matches=named.stream().filter(d->row.getStoreName()==null||"STORE".equals(d.getDeptType()))
                .collect(Collectors.toList());
        if(matches.isEmpty()){add(errors,"ORGANIZATION_UNRESOLVED");return;}
        Map<Long,SysDept> byId=new LinkedHashMap<>();for(SysDept dept:all)byId.put(dept.getDeptId(),dept);
        List<SysDept> pathMatches=matches.stream().filter(candidate->matchesPath(candidate,supplied,byId))
                .collect(Collectors.toList());
        if(pathMatches.isEmpty()){add(errors,"ORGANIZATION_CONTRADICTORY");return;}
        if(pathMatches.size()>1){add(errors,"ORGANIZATION_AMBIGUOUS");return;}
        SysDept selected=pathMatches.get(0);
        if(row.getStoreName()!=null)
        {
            if(!"STORE".equals(selected.getDeptType())){add(errors,"STORE_UNRESOLVED");return;}
            row.setTargetStoreId(selected.getDeptId());
            SysDept department=deepestSuppliedDepartment(selected,supplied,byId);
            row.setTargetDeptId(department==null?selected.getDeptId():department.getDeptId());
        }
        else row.setTargetDeptId(selected.getDeptId());
    }

    private boolean matchesPath(SysDept selected,List<String> supplied,Map<Long,SysDept> byId)
    {
        List<SysDept> chain=new ArrayList<>();SetGuard guard=new SetGuard();SysDept cursor=selected;
        while(cursor!=null && guard.add(cursor.getDeptId()))
        {chain.add(0,cursor);cursor=byId.get(cursor.getParentId());}
        int index=0;
        for(SysDept node:chain)if(index<supplied.size() && supplied.get(index).equals(trim(node.getDeptName())))index++;
        return index==supplied.size() && supplied.get(supplied.size()-1).equals(trim(selected.getDeptName()));
    }

    private SysDept deepestSuppliedDepartment(SysDept selected,List<String> supplied,Map<Long,SysDept> byId)
    {
        SysDept cursor=byId.get(selected.getParentId());SetGuard guard=new SetGuard();
        List<SysDept> fallback=new ArrayList<>();
        while(cursor!=null && guard.add(cursor.getDeptId()))
        {
            if(!"STORE".equals(cursor.getDeptType()))
            {
                fallback.add(cursor);
                if(supplied.contains(trim(cursor.getDeptName())))return cursor;
            }
            cursor=byId.get(cursor.getParentId());
        }
        return fallback.isEmpty()?null:fallback.get(0);
    }

    private void validateCreateRequired(HrOnboarding row,List<String> errors)
    {
        if(trim(row.getEmployeeName())==null)add(errors,"EMPLOYEE_NAME_REQUIRED");
        if(trim(row.getPhoneNumber())==null)add(errors,"PHONE_NUMBER_REQUIRED");
        if(row.getExpectedEntryDate()==null)add(errors,"EXPECTED_ENTRY_DATE_REQUIRED");
        if(trim(row.getEmployeeCategory())==null)add(errors,"EMPLOYEE_CATEGORY_REQUIRED");
    }

    private void resolvePost(HrOnboarding row,List<String> errors)
    {
        if(row.getPositionName()==null){add(errors,"POST_UNRESOLVED");return;}
        SysPost query=new SysPost();query.setStatus("0");
        PreviewContext context=previewContext.get();
        List<SysPost> rows=context==null?postMapper.selectPostList(query):context.posts;
        List<SysPost> matches=rows==null?Collections.emptyList():rows.stream()
                .filter(p->row.getPositionName().equals(trim(p.getPostName()))).collect(Collectors.toList());
        if(matches.isEmpty())add(errors,"POST_UNRESOLVED");
        else if(matches.size()>1)add(errors,"POST_AMBIGUOUS");
        else row.setTargetPostId(matches.get(0).getPostId());
    }

    private void resolveSupervisor(HrOnboarding row,List<String> errors)
    {
        String name=trim(row.getDepartmentSupervisor());if(name==null)return;
        PreviewContext context=previewContext.get();
        List<SysUser> users=context==null?accessService.listScopedUsers(new SysUser()):context.users;
        List<SysUser> matches=users.stream().filter(u->name.equals(trim(u.getNickName()))).collect(Collectors.toList());
        if(matches.isEmpty())add(errors,"SUPERVISOR_UNRESOLVED");
        else if(matches.size()>1)add(errors,"SUPERVISOR_AMBIGUOUS");
        else row.setDirectSupervisorUserId(matches.get(0).getUserId());
    }

    @SuppressWarnings("unchecked")
    private void validateDictionaries(HrOnboarding row,List<String> errors)
    {
        PreviewContext context=previewContext.get();
        Map<String,Object> options=context==null?configService.options():context.options;
        Object raw=options==null?null:options.get("dictionaryDefaults");
        if(!(raw instanceof Map))
        {
            if(hasDictionaryValue(row))add(errors,"DICTIONARY_CONFIGURATION_MISSING");
            return;
        }
        Map<String,Object> dictionaries=(Map<String,Object>)raw;
        row.setEmployeeCategory(normalizeDictionary(dictionaries,"employeeCategory",row.getEmployeeCategory(),errors));
        row.setSex(normalizeDictionary(dictionaries,"sex",row.getSex(),errors));
        row.setIdType(normalizeDictionary(dictionaries,"idType",row.getIdType(),errors));
        row.setMaritalStatus(normalizeDictionary(dictionaries,"maritalStatus",row.getMaritalStatus(),errors));
        row.setEthnicity(normalizeDictionary(dictionaries,"ethnicity",row.getEthnicity(),errors));
        row.setWorkCityLevel(normalizeDictionary(dictionaries,"workCityLevel",row.getWorkCityLevel(),errors));
        row.setContractType(normalizeDictionary(dictionaries,"contractType",row.getContractType(),errors));
        row.setSocialType(normalizeDictionary(dictionaries,"socialType",row.getSocialType(),errors));
        row.setProbationPeriod(normalizeDictionary(dictionaries,"probationPeriod",row.getProbationPeriod(),errors));
    }

    private boolean hasDictionaryValue(HrOnboarding row)
    {
        return row.getEmployeeCategory()!=null||row.getSex()!=null||row.getIdType()!=null
                ||row.getMaritalStatus()!=null||row.getEthnicity()!=null||row.getWorkCityLevel()!=null
                ||row.getContractType()!=null||row.getSocialType()!=null||row.getProbationPeriod()!=null;
    }

    private String normalizeDictionary(Map<String,Object> dictionaries,String key,String value,List<String> errors)
    {
        if(value==null)return null;
        if(!(dictionaries.get(key) instanceof List) || ((List<?>)dictionaries.get(key)).isEmpty())
        {add(errors,"DICTIONARY_CONFIGURATION_MISSING");return value;}
        List<?> values=(List<?>)dictionaries.get(key);
        List<String> matched=values.stream().filter(Map.class::isInstance).map(Map.class::cast)
                .filter(option->Objects.equals(value,String.valueOf(option.get("label")))
                        || Objects.equals(value,String.valueOf(option.get("value"))))
                .map(option->String.valueOf(option.get("value"))).distinct().collect(Collectors.toList());
        if(matched.isEmpty()){add(errors,"DICTIONARY_UNRESOLVED");return value;}
        if(matched.size()>1){add(errors,"DICTIONARY_MAPPING_AMBIGUOUS");return value;}
        return matched.get(0);
    }

    private String safeSummary(HrOnboardingConflictVo value)
    {
        String name=trim(value.getName());if(name!=null && name.length()>1)name=name.substring(0,1)+"**";
        return "候选账号: "+(name==null?"-":name)+" "+(value.getMaskedPhone()==null?"":value.getMaskedPhone());
    }
    private boolean sameName(String left,String right)
    {
        String a=trim(left),b=trim(right);return a!=null && b!=null && a.equalsIgnoreCase(b);
    }
    private String trim(String value){return value==null||value.trim().isEmpty()?null:value.trim();}
    private void add(List<String> values,String value){if(!values.contains(value))values.add(value);}
    private static final class SetGuard
    {
        private final java.util.Set<Long> ids=new java.util.HashSet<>();
        private boolean add(Long id){return id!=null&&ids.add(id);}
    }
    private static final class PreviewContext
    {
        private final List<SysDept> departments;private final List<SysUser> users;private final List<SysPost> posts;
        private final Map<String,Object> options;
        private PreviewContext(List<SysDept> departments,List<SysUser> users,List<SysPost> posts,Map<String,Object> options)
        {
            this.departments=departments==null?Collections.emptyList():departments;
            this.users=users==null?Collections.emptyList():users;
            this.posts=posts==null?Collections.emptyList():posts;
            this.options=options;
        }
    }
}
