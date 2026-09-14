package com.erp.oa.attendance.leave.balance;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Objects;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import com.erp.common.core.exception.ServiceException;
import com.erp.common.core.web.domain.AjaxResult;
import com.erp.common.security.annotation.Logical;
import com.erp.common.security.annotation.RequiresPermissions;
import com.erp.common.security.shop.ShopScopeService;
import com.erp.oa.controller.OaBaseController;
import com.erp.oa.service.BusinessFeatureGate;

@RestController
@RequestMapping("/attendance-v2/leave/balance/options")
@Transactional(readOnly=true)
public class AttendanceLeaveBalanceOptionsController extends OaBaseController
{
    private static final String PREFIX=AttendanceLeaveBalanceAccess.PREFIX;
    private final AttendanceLeaveBalanceOptionsMapper mapper;
    private final AttendanceLeaveBalanceAccess access;
    private final ShopScopeService shops;
    private final BusinessFeatureGate gate;
    public AttendanceLeaveBalanceOptionsController(AttendanceLeaveBalanceOptionsMapper mapper,
            AttendanceLeaveBalanceAccess access, ShopScopeService shops, BusinessFeatureGate gate)
    { this.mapper=mapper;this.access=access;this.shops=shops;this.gate=gate; }

    @GetMapping("/types")
    @RequiresPermissions(value={PREFIX+"self",PREFIX+"read",PREFIX+"adjust",PREFIX+"rule",PREFIX+"convert"},logical=Logical.OR)
    public AjaxResult types()
    { requireAny("self","read","adjust","rule","convert");return success(mapper.selectTypes()); }

    @GetMapping("/companies") @RequiresPermissions(PREFIX+"rule")
    public AjaxResult companies(@RequestParam Long ownerDeptId)
    { requireAny("rule");owner(ownerDeptId);return success(mapper.selectCompanies(ownerDeptId)); }

    @GetMapping("/employees") @RequiresPermissions(value={PREFIX+"read",PREFIX+"adjust"},logical=Logical.OR)
    public AjaxResult employees(@RequestParam Long ownerDeptId, @RequestParam(required=false) String keyword,
            @RequestParam(defaultValue="1") Integer pageNum, @RequestParam(defaultValue="20") Integer pageSize)
    {
        requireAny("read","adjust");owner(ownerDeptId);PageBounds page=page(pageNum,pageSize);String term=keyword(keyword);
        int total=mapper.countEmployees(ownerDeptId,term);
        return success(new OptionsPage(page.offset>=total?List.of():mapper.selectEmployees(ownerDeptId,term,page.offset,page.size),total));
    }

    @GetMapping("/overtime-sources") @RequiresPermissions(PREFIX+"convert")
    public AjaxResult overtimeSources(@RequestParam Long ownerDeptId,
            @RequestParam @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate dateTo,
            @RequestParam(required=false) String keyword,
            @RequestParam(defaultValue="1") Integer pageNum, @RequestParam(defaultValue="20") Integer pageSize,
            HttpServletRequest request)
    {
        requireAny("convert");owner(ownerDeptId);
        Long selected=shops.resolveRequiredShopDept(resolveAttendanceShopDeptId(request));
        if (!Objects.equals(ownerDeptId,selected)) throw new ServiceException("加班来源只能查询当前门店",403);
        if (dateFrom==null || dateTo==null || dateTo.isBefore(dateFrom) || ChronoUnit.DAYS.between(dateFrom,dateTo)>=31)
            throw new ServiceException("请选择不超过31天的有效日期范围");
        PageBounds page=page(pageNum,pageSize);String term=keyword(keyword);
        int total=mapper.countOvertimeSources(ownerDeptId,dateFrom,dateTo,term);
        return success(new OptionsPage(page.offset>=total?List.of():mapper.selectOvertimeSources(ownerDeptId,dateFrom,dateTo,term,page.offset,page.size),total));
    }
    private void requireAny(String... actions)
    {
        gate.requireEnabled(BusinessFeatureGate.ATTENDANCE_V2);
        ServiceException rejected=null;
        for (String action:actions) {
            try { access.require(action);return; }
            catch(ServiceException error) { if(!Objects.equals(error.getCode(),403))throw error;rejected=error; }
        }
        throw Objects.requireNonNull(rejected);
    }
    private void owner(Long id)
    { if(id==null || id<=0)throw new ServiceException("请选择有效组织");access.department(id); }
    private static String keyword(String value)
    {
        String result=value==null?null:value.trim();if(result!=null&&result.length()>64)throw new ServiceException("搜索内容不能超过64个字符");
        return result==null||result.isEmpty()?null:result;
    }
    private static PageBounds page(Integer number,Integer size)
    {
        int n=number==null?1:number,s=size==null?20:size;
        if(n<1||s<1||s>100)throw new ServiceException("页码必须为正数，每页条数需在1到100之间");
        return new PageBounds(((long)n-1)*s,s);
    }
    private record PageBounds(long offset,int size) { }
    public record OptionsPage(List<?> rows,Integer total) { }
}
