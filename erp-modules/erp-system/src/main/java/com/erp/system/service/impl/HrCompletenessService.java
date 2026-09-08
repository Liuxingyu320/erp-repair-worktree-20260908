package com.erp.system.service.impl;

import com.erp.system.domain.HrOnboarding;
import com.erp.system.domain.vo.HrEmployeeCompletenessVo;
import com.erp.system.domain.vo.HrEmployeeListVo;
import com.erp.system.domain.vo.HrEmployeeQuery;
import com.erp.system.domain.vo.HrOnboardingCompletionVo;
import com.erp.system.domain.vo.HrOnboardingQuery;
import com.erp.system.service.IHrEmployeeProfileService;
import com.erp.common.core.web.page.PageDomain;
import com.erp.common.core.web.page.TableSupport;
import com.erp.common.core.utils.ServletUtils;
import com.github.pagehelper.Page;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

/** Builds the server-paged employee completeness queue without per-row database queries. */
@Service
public class HrCompletenessService
{
    private final IHrEmployeeProfileService employeeService;
    private final HrOnboardingAccessService onboardingAccess;
    private final HrOnboardingRuleService rules;

    public HrCompletenessService(IHrEmployeeProfileService employeeService,
            HrOnboardingAccessService onboardingAccess, HrOnboardingRuleService rules)
    {
        this.employeeService=employeeService;
        this.onboardingAccess=onboardingAccess;
        this.rules=rules;
    }

    public List<HrEmployeeCompletenessVo> employees(HrEmployeeQuery query)
    {
        List<HrEmployeeListVo> source=page(employeeService.completenessEmployees(query));
        List<Long> userIds=source.stream().map(HrEmployeeListVo::getUserId)
                .filter(id->id!=null).distinct().collect(Collectors.toList());
        List<HrOnboarding> linked=new ArrayList<>();
        for(int offset=0;offset<userIds.size();offset+=200)
        {
            int end=Math.min(offset+200,userIds.size());
            linked.addAll(onboardingAccess.listScopedByLinkedUserIds(new HrOnboardingQuery(),
                    new ArrayList<>(userIds.subList(offset,end))));
        }
        Map<Long,HrOnboarding> byUser=new LinkedHashMap<>();
        for(HrOnboarding row:linked)if(row!=null&&row.getLinkedUserId()!=null)byUser.putIfAbsent(row.getLinkedUserId(),row);
        Map<Long,HrOnboardingCompletionVo> completionById=byUser.isEmpty()?Collections.emptyMap():
                rules.evaluateReadyBatch(new ArrayList<>(byUser.values()));
        List<HrEmployeeCompletenessVo> result=copyPage(source);
        for(HrEmployeeListVo employee:source)
        {
            HrOnboarding onboarding=byUser.get(employee.getUserId());
            result.add(enrich(employee,onboarding,onboarding==null?null:completionById.get(onboarding.getOnboardingId())));
        }
        return result;
    }

    private List<HrEmployeeListVo> page(List<HrEmployeeListVo> source)
    {
        if(source instanceof Page<?>)return source;
        PageDomain requested=ServletUtils.getRequest()==null?new PageDomain():TableSupport.buildPageRequest();
        int pageNum=requested.getPageNum()==null||requested.getPageNum()<1?1:requested.getPageNum();
        int pageSize=requested.getPageSize()==null||requested.getPageSize()<1?10:requested.getPageSize();
        long requestedFrom=((long)pageNum-1L)*(long)pageSize;
        int from=(int)Math.min(Math.max(requestedFrom,0L),(long)source.size());
        int to=(int)Math.min((long)from+(long)pageSize,(long)source.size());
        Page<HrEmployeeListVo> result=new Page<>(pageNum,pageSize);result.setTotal(source.size());
        result.addAll(source.subList(from,to));return result;
    }

    private List<HrEmployeeCompletenessVo> copyPage(List<HrEmployeeListVo> source)
    {
        if(source instanceof Page<?> sourcePage)
        {
            Page<HrEmployeeCompletenessVo> result=new Page<>(sourcePage.getPageNum(),sourcePage.getPageSize());
            result.setTotal(sourcePage.getTotal());result.setOrderBy(sourcePage.getOrderBy());return result;
        }
        return new ArrayList<>(source.size());
    }

    private HrEmployeeCompletenessVo enrich(HrEmployeeListVo source,HrOnboarding onboarding,
            HrOnboardingCompletionVo completion)
    {
        HrEmployeeCompletenessVo target=copy(source);
        if(source.getUserId()!=null)target.setEmployeeDetailUrl("/hr/employee?userId="+source.getUserId());
        if(onboarding==null)return target;
        target.setOnboardingId(onboarding.getOnboardingId());target.setOnboardingStatus(onboarding.getStatus());
        target.setOnboardingDetailUrl("/hr/onboarding?onboardingId="+onboarding.getOnboardingId());
        if(onboarding.getTargetPostId()!=null&&onboarding.getEmployeeCategory()!=null)
            target.setPositionConfigurationUrl("/hr/position-config?postId="+onboarding.getTargetPostId()+
                    "&employeeCategory="+URLEncoder.encode(onboarding.getEmployeeCategory(),StandardCharsets.UTF_8));
        if(completion!=null)
        {
            target.setOnboardingCompletionPercent(completion.getPercentage());
            target.setPostEntryDueDate(completion.getPostEntryDueDate());
            target.setPostEntryOverdue(completion.getPostEntryOverdue());
            target.setMissingOnboardingFields(missingFields(completion));
        }
        return target;
    }

    private List<HrOnboardingCompletionVo.MissingField> missingFields(HrOnboardingCompletionVo completion)
    {
        Map<String,HrOnboardingCompletionVo.MissingField> fields=new LinkedHashMap<>();
        completion.getGroupedMissingFields().values().forEach(group->group.forEach(field->fields.put(field.getKey(),field)));
        for(String key:completion.getMissingFields())fields.putIfAbsent(key,new HrOnboardingCompletionVo.MissingField(key,key));
        return new ArrayList<>(fields.values());
    }

    private HrEmployeeCompletenessVo copy(HrEmployeeListVo source)
    {
        HrEmployeeCompletenessVo target=new HrEmployeeCompletenessVo();
        target.setUserId(source.getUserId());target.setEmployeeNo(source.getEmployeeNo());
        target.setEmployeeName(source.getEmployeeName());target.setPhoneNumberMasked(source.getPhoneNumberMasked());
        target.setDepartmentName(source.getDepartmentName());target.setPositionName(source.getPositionName());
        target.setEmployeeStatus(source.getEmployeeStatus());target.setEmployeeCategory(source.getEmployeeCategory());
        target.setProfileCompletionPercent(source.getProfileCompletionPercent());
        target.setProfileCompletedFieldCount(source.getProfileCompletedFieldCount());
        target.setProfileApplicableFieldCount(source.getProfileApplicableFieldCount());
        target.setProfileNotApplicableFieldCount(source.getProfileNotApplicableFieldCount());
        target.setProfileTrackedFieldCount(source.getProfileTrackedFieldCount());
        target.setMissingProfileFields(source.getMissingProfileFields());
        target.setRequiredCompletionPercent(source.getRequiredCompletionPercent());
        target.setRequiredCompletedFieldCount(source.getRequiredCompletedFieldCount());
        target.setRequiredApplicableFieldCount(source.getRequiredApplicableFieldCount());
        target.setCoveragePercent(source.getCoveragePercent());
        target.setMissingRequiredFields(source.getMissingRequiredFields());
        target.setMissingOptionalFields(source.getMissingOptionalFields());
        target.setMissingByResponsibility(source.getMissingByResponsibility());
        target.setAccountEnabled(source.getAccountEnabled());
        target.setAccountConfigurationStatus(source.getAccountConfigurationStatus());target.setRemark(source.getRemark());
        target.setAccountConfigurationRiskCodes(new ArrayList<>(source.getAccountConfigurationRiskCodes()));
        target.setDepartmentSupervisor(source.getDepartmentSupervisor());target.setDirectSupervisor(source.getDirectSupervisor());
        target.setContractStartDate(source.getContractStartDate());target.setContractEndDate(source.getContractEndDate());
        target.setContractType(source.getContractType());target.setSocialType(source.getSocialType());
        target.setSocialSecurityLocation(source.getSocialSecurityLocation());target.setHousingFundLocation(source.getHousingFundLocation());
        target.setLegalEntity(source.getLegalEntity());target.setRecruitmentChannel(source.getRecruitmentChannel());
        target.setLeaveDate(source.getLeaveDate());target.setOnboardingDetailUrl(source.getOnboardingDetailUrl());
        target.setEmployeeDetailUrl(source.getEmployeeDetailUrl());target.setAccountConfigurationUrl(source.getAccountConfigurationUrl());
        return target;
    }
}
