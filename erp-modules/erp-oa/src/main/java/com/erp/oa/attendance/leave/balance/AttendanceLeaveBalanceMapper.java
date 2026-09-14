package com.erp.oa.attendance.leave.balance;

import java.time.LocalDate;
import java.util.List;
import org.apache.ibatis.annotations.Param;
import com.erp.oa.attendance.leave.balance.AttendanceLeaveBalanceModels.*;

public interface AttendanceLeaveBalanceMapper
{
    EmployeeContext selectContext(@Param("userId") Long userId);
    Long lockUser(@Param("userId") Long userId);
    Long lockProfile(@Param("userId") Long userId);
    int countActiveType(@Param("leaveTypeId") Long leaveTypeId);
    int countDeptPermission(@Param("actor") Long actor, @Param("deptId") Long deptId);
    int countDeptEntity(@Param("deptId") Long deptId, @Param("legalEntityId") Long legalEntityId);
    List<Long> selectAccrualUsers(@Param("afterId") long afterId, @Param("limit") int limit);
    List<Long> selectConfiguredTypes();
    List<Rule> selectRules(@Param("actor") Long actor, @Param("admin") boolean admin);
    Rule selectRule(@Param("ruleId") Long ruleId, @Param("lock") boolean lock);
    List<Tier> selectTiers(@Param("ruleId") Long ruleId);
    int insertRule(Rule rule);
    int initializeFamily(@Param("ruleId") Long ruleId);
    int updateRule(Rule rule);
    int publishRule(@Param("ruleId") Long ruleId, @Param("version") Long version, @Param("actor") Long actor);
    int deleteTiers(@Param("ruleId") Long ruleId);
    int insertTier(Tier tier);
    Integer selectLastFamilyVersion(@Param("familyId") Long familyId);
    List<LocationMapping> selectLocations(@Param("actor") Long actor, @Param("admin") boolean admin);
    LocationMapping selectLocation(@Param("mappingId") Long mappingId, @Param("lock") boolean lock);
    int insertLocation(LocationMapping mapping);
    int updateLocation(LocationMapping mapping);
    List<LocationMapping> matchLocations(@Param("context") EmployeeContext context, @Param("lock") boolean lock);
    List<Rule> matchRules(@Param("context") EmployeeContext context, @Param("leaveTypeId") Long leaveTypeId,
            @Param("locationCode") String locationCode, @Param("asOf") LocalDate asOf, @Param("lock") boolean lock);
    int ensureAccount(Account account);
    Account selectAccount(@Param("userId") Long userId, @Param("leaveTypeId") Long leaveTypeId, @Param("lock") boolean lock);
    int bumpAccount(@Param("accountId") Long accountId);
    List<Bucket> selectBuckets(@Param("accountId") Long accountId, @Param("lock") boolean lock);
    int insertBucket(Bucket bucket);
    int updateBucket(Bucket bucket);
    Command selectCommand(@Param("accountId") Long accountId, @Param("commandKey") String commandKey);
    int insertCommand(Command command);
    int insertLedger(Ledger ledger);
    List<Ledger> selectLedger(@Param("accountId") Long accountId, @Param("beforeId") Long beforeId, @Param("limit") int limit);
}
