package com.erp.system.service.impl;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.IdentityHashMap;
import org.springframework.stereotype.Service;
import com.erp.common.core.exception.ServiceException;
import com.erp.system.api.domain.SysUser;
import com.erp.system.api.domain.SysUserProfile;
import com.erp.system.domain.HrOnboarding;
import com.erp.system.domain.vo.HrOnboardingConflictVo;
import com.erp.system.domain.vo.HrOnboardingQuery;
import com.erp.system.service.ISysUserService;
import com.erp.system.support.HrSensitiveFieldMasker;

@Service
public class HrOnboardingConflictService
{
    private final HrOnboardingAccessService accessService;
    private final HrSensitiveFieldMasker masker;
    private final ISysUserService userService;

    public HrOnboardingConflictService(HrOnboardingAccessService accessService, HrSensitiveFieldMasker masker,
            ISysUserService userService)
    {
        this.accessService = accessService;
        this.masker = masker;
        this.userService = userService;
    }

    public List<HrOnboardingConflictVo> preview(Long onboardingId)
    {
        HrOnboardingQuery query = query(onboardingId);
        return findConflicts(accessService.findScoped(query));
    }

    public List<HrOnboardingConflictVo> findConflicts(HrOnboarding source)
    {
        if (source == null || source.getOnboardingId() == null) return Collections.emptyList();
        HrOnboardingQuery query = query(source.getOnboardingId());
        List<HrOnboardingConflictVo> result = new ArrayList<>();
        List<SysUser> users = accessService.listScopedUserConflicts(query, source.getPhoneNumber(),
                source.getIdNumber(), source.getEmployeeNo());
        for (SysUser user : users) result.add(accountConflict(source, user));
        List<HrOnboarding> onboardings = accessService.listScopedOnboardingConflicts(query,
                source.getPhoneNumber(), source.getIdNumber(), source.getEmployeeNo());
        for (HrOnboarding candidate : onboardings)
        {
            if (candidate != null && !source.getOnboardingId().equals(candidate.getOnboardingId()))
                result.add(onboardingConflict(source, candidate));
        }
        return result;
    }

    /** Latest scoped conflicts for a transient import payload. */
    public List<HrOnboardingConflictVo> findImportConflicts(HrOnboarding source)
    {
        if (source == null) return Collections.emptyList();
        HrOnboardingQuery query = new HrOnboardingQuery();
        query.setTargetDeptId(source.getTargetDeptId());
        List<HrOnboardingConflictVo> result = new ArrayList<>();
        for (SysUser user : accessService.listScopedUserConflicts(query, source.getPhoneNumber(),
                source.getIdNumber(), source.getEmployeeNo())) result.add(accountConflict(source, user));
        for (HrOnboarding candidate : accessService.listScopedOnboardingConflicts(query, source.getPhoneNumber(),
                source.getIdNumber(), source.getEmployeeNo())) result.add(onboardingConflict(source, candidate));
        return result;
    }

    /** Two scoped queries per bounded chunk, then deterministic in-memory identity matching. */
    public Map<HrOnboarding,List<HrOnboardingConflictVo>> findImportConflictsBatch(List<HrOnboarding> sources)
    {
        Map<HrOnboarding,List<HrOnboardingConflictVo>> result=new IdentityHashMap<>();
        if(sources==null)return result;
        for(HrOnboarding source:sources)result.put(source,new ArrayList<>());
        List<HrOnboarding> identifiable=new ArrayList<>();
        for(HrOnboarding source:sources)if(hasIdentity(source))identifiable.add(source);
        for(int start=0;start<identifiable.size();start+=200)
        {
            List<HrOnboarding> chunk=identifiable.subList(start,Math.min(start+200,identifiable.size()));
            HrOnboardingQuery query=new HrOnboardingQuery();
            List<SysUser> users=accessService.listScopedUserConflictsBatch(query,chunk);
            List<HrOnboarding> onboardings=accessService.listScopedOnboardingConflictsBatch(query,chunk);
            for(HrOnboarding source:chunk)
            {
                List<HrOnboardingConflictVo> matches=result.get(source);
                for(SysUser user:users)if(matches(source,user))matches.add(accountConflict(source,user));
                for(HrOnboarding candidate:onboardings)if(matches(source,candidate))
                    matches.add(onboardingConflict(source,candidate));
            }
        }
        return result;
    }

    private boolean hasIdentity(HrOnboarding source)
    {
        return source!=null&&((source.getPhoneNumber()!=null&&!source.getPhoneNumber().isEmpty())
                ||(source.getIdNumber()!=null&&!source.getIdNumber().isEmpty())
                ||(source.getEmployeeNo()!=null&&!source.getEmployeeNo().isEmpty()));
    }

    private boolean matches(HrOnboarding source,SysUser candidate)
    {
        SysUserProfile profile=candidate.getProfile();
        return same(source.getPhoneNumber(),candidate.getPhonenumber())
                ||same(source.getIdNumber(),profile==null?null:profile.getIdNumber())
                ||same(source.getEmployeeNo(),profile==null?null:profile.getEmployeeNo());
    }
    private boolean matches(HrOnboarding source,HrOnboarding candidate)
    {
        return same(source.getPhoneNumber(),candidate.getPhoneNumber())
                ||same(source.getIdNumber(),candidate.getIdNumber())
                ||same(source.getEmployeeNo(),candidate.getEmployeeNo());
    }

    private HrOnboardingConflictVo accountConflict(HrOnboarding source, SysUser candidate)
    {
        SysUserProfile profile = candidate.getProfile();
        HrOnboardingConflictVo result = new HrOnboardingConflictVo();
        result.setConflictType(conflictType(source, candidate.getPhonenumber(),
                profile == null ? null : profile.getIdNumber(), profile == null ? null : profile.getEmployeeNo()));
        result.setSourceType("EMPLOYEE_ACCOUNT");
        result.setCandidateUserId(candidate.getUserId());
        result.setEmployeeNo(profile == null ? null : profile.getEmployeeNo());
        result.setName(candidate.getNickName());
        result.setMaskedPhone(masker.maskPhone(candidate.getPhonenumber()));
        result.setDepartmentLabel(candidate.getDept() == null ? null : candidate.getDept().getDeptName());
        boolean accountActive = "0".equals(candidate.getStatus());
        boolean employeeActive = profile != null && !"离职".equals(profile.getEmployeeStatus());
        boolean departed = profile != null && "离职".equals(profile.getEmployeeStatus());
        boolean userAllowed = isUserAllowed(candidate);
        boolean eligible = accountActive && (profile == null || employeeActive) && userAllowed;
        boolean rehireEligible = !accountActive && departed && userAllowed
                && rehireIdentityMatches(source, candidate);
        result.setEligibleForBind(eligible);
        result.setEligibleForRehire(rehireEligible);
        // Any exact historical account is a duplicate-account boundary. It must be
        // explicitly bound or rehired; CREATE_NEW is never a safe fallback.
        result.setBlocking(true);
        result.setAllowedDecisions(eligible ? Collections.singletonList("BIND_EXISTING")
                : rehireEligible ? Collections.singletonList("REHIRE_EXISTING")
                : Collections.emptyList());
        return result;
    }

    private HrOnboardingConflictVo onboardingConflict(HrOnboarding source, HrOnboarding candidate)
    {
        HrOnboardingConflictVo result = new HrOnboardingConflictVo();
        result.setConflictType(conflictType(source, candidate.getPhoneNumber(),
                candidate.getIdNumber(), candidate.getEmployeeNo()));
        result.setSourceType("ONBOARDING");
        result.setCandidateOnboardingId(candidate.getOnboardingId());
        result.setEmployeeNo(candidate.getEmployeeNo());
        result.setName(candidate.getEmployeeName());
        result.setMaskedPhone(masker.maskPhone(candidate.getPhoneNumber()));
        result.setDepartmentLabel(firstNonBlank(candidate.getStoreName(), candidate.getDeptLevel3Name(),
                candidate.getDeptLevel2Name(), candidate.getDeptLevel1Name(), candidate.getCompanyName()));
        boolean historical = HrOnboarding.STATUS_CANCELLED.equals(candidate.getStatus())
                || HrOnboarding.STATUS_CONFIRMED.equals(candidate.getStatus());
        result.setEligibleForBind(false);
        result.setEligibleForRehire(false);
        result.setBlocking(!historical);
        result.setAllowedDecisions(historical ? Collections.singletonList("CREATE_NEW") : Collections.emptyList());
        return result;
    }

    private boolean isUserAllowed(SysUser candidate)
    {
        try
        {
            userService.checkUserAllowed(candidate);
            return true;
        }
        catch (ServiceException denied)
        {
            return false;
        }
    }

    private boolean rehireIdentityMatches(HrOnboarding source, SysUser candidate)
    {
        if (source == null || candidate == null
                || !sameTrimmed(source.getEmployeeName(), candidate.getNickName())) return false;
        SysUserProfile profile = candidate.getProfile();
        if (profile == null) return false;
        if (notBlank(source.getIdNumber()))
            return sameTrimmed(source.getIdNumber(), profile.getIdNumber());
        if (notBlank(source.getEmployeeNo()))
            return sameTrimmed(source.getEmployeeNo(), profile.getEmployeeNo());
        return sameTrimmed(source.getPhoneNumber(), candidate.getPhonenumber());
    }

    private String conflictType(HrOnboarding source, String phone, String idNumber, String employeeNo)
    {
        if (same(source.getIdNumber(), idNumber)) return "ID_NUMBER";
        if (same(source.getPhoneNumber(), phone)) return "PHONE";
        if (same(source.getEmployeeNo(), employeeNo)) return "EMPLOYEE_NO";
        return "UNKNOWN";
    }

    private boolean same(String left, String right)
    {
        return left != null && !left.isEmpty() && left.equals(right);
    }

    private boolean sameTrimmed(String left, String right)
    {
        return notBlank(left) && notBlank(right) && left.trim().equals(right.trim());
    }

    private boolean notBlank(String value)
    {
        return value != null && !value.trim().isEmpty();
    }

    private String firstNonBlank(String... values)
    {
        for (String value : values) if (value != null && !value.trim().isEmpty()) return value;
        return null;
    }

    private HrOnboardingQuery query(Long onboardingId)
    {
        HrOnboardingQuery query = new HrOnboardingQuery();
        query.setOnboardingId(onboardingId);
        return query;
    }
}
