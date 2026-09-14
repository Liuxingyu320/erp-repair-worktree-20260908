package com.erp.oa.attendance.leave.balance;

import static com.erp.oa.attendance.leave.balance.AttendanceLeaveBalanceModels.*;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.erp.common.core.exception.ServiceException;
import com.erp.oa.attendance.leave.balance.AttendanceLeaveBalanceRequests.*;
import com.erp.oa.service.BusinessFeatureGate;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

@Service
public class AttendanceLeaveBalanceService
{
    private final AttendanceLeaveBalanceMapper mapper;
    private final AttendanceLeaveBalanceAccess access;
    private final AttendanceLeaveBalanceCalculator calculator;
    private final BusinessFeatureGate featureGate;
    private final ObjectMapper json;
    private final Clock clock;
    private final AttendanceOvertimeTransferSourceGuard overtimeSources;

    @Autowired
    public AttendanceLeaveBalanceService(AttendanceLeaveBalanceMapper mapper,
            AttendanceLeaveBalanceAccess access, AttendanceLeaveBalanceCalculator calculator,
            BusinessFeatureGate featureGate, ObjectMapper json, AttendanceOvertimeTransferSourceGuard overtimeSources)
    { this(mapper, access, calculator, featureGate, json, overtimeSources, Clock.systemDefaultZone()); }

    AttendanceLeaveBalanceService(AttendanceLeaveBalanceMapper mapper,
            AttendanceLeaveBalanceAccess access, AttendanceLeaveBalanceCalculator calculator,
            BusinessFeatureGate featureGate, ObjectMapper json, AttendanceOvertimeTransferSourceGuard overtimeSources, Clock clock)
    {
        this.mapper = mapper; this.access = access; this.calculator = calculator;
        this.featureGate = featureGate; this.json = json; this.clock = clock; this.overtimeSources = Objects.requireNonNull(overtimeSources);
    }

    public Balance my(Long typeId) { return balance(access.actor(), typeId, true); }
    public Balance employee(Long userId, Long typeId) { return balance(userId, typeId, false); }
    private Balance balance(Long userId, Long typeId, boolean self)
    {
        enabled();
        EmployeeContext context = mapper.selectContext(userId);
        access.employee(context, self, self ? "self" : "read");
        Match match = match(context, typeId, false);
        Account account = mapper.selectAccount(userId, typeId, false);
        return view(context, typeId, match, account == null ? List.of() : mapper.selectBuckets(account.accountId, false));
    }

    @Transactional
    public Balance recalculateMy(Long typeId)
    { return recalculate(access.actor(), typeId, true, false); }
    @Transactional
    public Balance recalculateEmployee(Long userId, Long typeId)
    { return recalculate(userId, typeId, false, false); }

    /** Scheduled transaction entry. It has no HTTP route and never fabricates a login. */
    @Transactional
    public Balance recalculateScheduled(Long userId, Long typeId)
    { return recalculate(userId, typeId, false, true); }

    private Balance recalculate(Long userId, Long typeId, boolean self, boolean scheduled)
    {
        enabled();
        if (!scheduled) access.require(self ? "self" : "adjust");
        EmployeeContext context = lockContext(userId);
        if (!scheduled) access.employee(context, self, self ? "self" : "adjust");
        Match match = match(context, typeId, true);
        if (!"READY".equals(match.status)) return view(context, typeId, match, existingBuckets(userId, typeId));
        Account account = lockAccount(userId, typeId);
        List<Bucket> buckets = mapper.selectBuckets(account.accountId, true);
        expire(account, buckets, scheduled ? null : access.actor(), match.asOfDate);
        if (!"SOURCE_ONLY".equals(match.rule.config.calculation))
        {
            String key = "ANNUAL|" + match.periodYear;
            Bucket bucket = buckets.stream().filter(b -> key.equals(b.sourceKey)).findFirst().orElse(null);
            String source = sourceSnapshot(context, match);
            String fingerprint = hash(source);
            if (bucket != null && bucket.ruleGrantedUnits == match.expectedUnits && fingerprint.equals(bucket.contextFingerprint))
                return view(context, typeId, match, buckets);
            String commandKey = "RECALC|" + key + "|" + (bucket == null ? "NEW" : bucket.rowVersion) + "|" + hash(fingerprint + "|" + match.expectedUnits);
            Command previous = mapper.selectCommand(account.accountId, commandKey);
            if (previous == null)
            {
                if (bucket == null)
                {
                    bucket = newBucket(account, context, match, key, "ANNUAL", match.periodYear);
                    bucket.grantedUnits = match.expectedUnits; bucket.ruleGrantedUnits = match.expectedUnits;
                    bucket.expiresOn = LocalDate.of(match.periodYear, 12, 31).plusMonths(match.rule.config.expiryMonthsAfterYear);
                    must(mapper.insertBucket(bucket));
                    buckets.add(bucket);
                    append(account, bucket, commandKey, "GRANT", bucket.grantedUnits, "按已发布规则自动发放", source, scheduled ? null : access.actor());
                }
                else
                {
                    long floor = Math.addExact(Math.addExact(bucket.reservedUnits, bucket.consumedUnits), Math.addExact(bucket.expiredUnits, bucket.carriedUnits));
                    long delta = Math.subtractExact(match.expectedUnits, bucket.ruleGrantedUnits);
                    long newTotal = Math.addExact(bucket.grantedUnits, delta);
                    if (newTotal < floor) throw new ServiceException("新规则额度低于已占用/消耗或已处理额度，请 HR 核对，历史未被覆盖");
                    bucket.grantedUnits = newTotal; bucket.ruleGrantedUnits = match.expectedUnits;
                    applySource(bucket, context, match);
                    // Previously issued expiry is never silently shortened by a new rule.
                    LocalDate configuredExpiry = LocalDate.of(match.periodYear, 12, 31).plusMonths(match.rule.config.expiryMonthsAfterYear);
                    if (configuredExpiry.isAfter(bucket.expiresOn)) bucket.expiresOn = configuredExpiry;
                    update(bucket);
                    append(account, bucket, commandKey, "RECONCILE", delta, "规则或员工来源变更后的同期间差额", source, scheduled ? null : access.actor());
                }
                command(account, commandKey, fingerprint, bucket.bucketId, bucket.grantedUnits);
                must(mapper.bumpAccount(account.accountId));
            }
        }
        return view(context, typeId, match, buckets);
    }

    @Transactional
    public Command adjust(Long userId, Adjustment body)
    {
        enabled(); access.require("adjust");
        if (body == null || body.leaveTypeId == null || body.bucketId == null || body.amount == null || body.amount.signum() == 0)
            throw new ServiceException("调整缺少假种、来源桶或非零金额");
        Adjustment frozen=new Adjustment();frozen.leaveTypeId=body.leaveTypeId;frozen.bucketId=body.bucketId;frozen.amount=body.amount;
        frozen.clientRequestId=body.clientRequestId;frozen.reason=body.reason;frozen.bucketVersion=body.bucketVersion;frozen.ruleId=body.ruleId;
        frozen.ruleVersion=body.ruleVersion;frozen.displayUnit=body.displayUnit;frozen.minutesPerDay=body.minutesPerDay;body=frozen;
        String requestId = text(body.clientRequestId, 64, "请求身份");
        String reason = text(body.reason, 500, "调整原因");
        EmployeeContext context = lockContext(userId);
        access.employee(context, false, "adjust");
        Account account = lockAccount(userId, body.leaveTypeId);
        List<Bucket> buckets = mapper.selectBuckets(account.accountId, true);
        Long targetBucketId=body.bucketId;
        Bucket bucket = buckets.stream().filter(b -> targetBucketId.equals(b.bucketId)).findFirst().orElseThrow(() -> new ServiceException("调整来源不属于该员工和假种"));
        if ("OVERTIME".equals(bucket.sourceType)) throw new ServiceException("已核定加班来源须通过原核定审计撤销，不能直接调整金额");
        String key = "ADJUST|" + access.actor() + "|" + requestId;
        boolean legacy = body.bucketVersion==null && body.ruleId==null && body.ruleVersion==null && body.displayUnit==null && body.minutesPerDay==null;
        String fingerprint = legacy ? hash(body.bucketId + "|" + body.amount.stripTrailingZeros().toPlainString() + "|" + reason)
                : com.erp.oa.attendance.support.AttendanceClientRequestSupport.fingerprint("BALANCE_ADJUST_V2",body.bucketId,
                    body.amount.stripTrailingZeros().toPlainString(),reason,body.bucketVersion,body.ruleId,body.ruleVersion,body.displayUnit,
                    body.minutesPerDay==null?null:body.minutesPerDay.stripTrailingZeros().toPlainString());
        Command previous = mapper.selectCommand(account.accountId, key);
        if (previous != null)
        {
            if (!fingerprint.equals(previous.fingerprint)) throw new ServiceException("同一请求身份的调整内容不同");
            return previous;
        }
        Rule issuedRule=requireRule(bucket.ruleId,false);
        if (legacy || !Objects.equals(body.bucketVersion,bucket.rowVersion) || !Objects.equals(body.ruleId,bucket.ruleId)
                || !Objects.equals(body.ruleVersion,issuedRule.version) || !Objects.equals(body.displayUnit,bucket.displayUnit)
                || !sameDecimal(body.minutesPerDay,bucket.minutesPerDay))
            throw new ServiceException("调整依据已变化或缺失，请重新读取分桶版本、规则和单位后核对");
        if (bucket.expiresOn.isBefore(today()) || !"OPEN".equals(bucket.expiryState)) throw new ServiceException("已到期来源不能直接调整，请核对原来源");
        long delta = AttendanceLeaveBalanceCalculator.units(body.amount, bucket.displayUnit, bucket.minutesPerDay);
        if (Math.addExact(bucket.availableUnits(), delta) < 0) throw new ServiceException("可用额度不足，不能减少已占用或消耗的历史");
        bucket.grantedUnits = Math.addExact(bucket.grantedUnits, delta);
        update(bucket);
        append(account, bucket, key, "ADJUST", delta, reason, write(context), access.actor());
        Command result = command(account, key, fingerprint, bucket.bucketId, delta);
        must(mapper.bumpAccount(account.accountId));
        return result;
    }

    public List<Ledger> ledger(Long userId, Long typeId, Long beforeId)
    {
        enabled(); access.employee(mapper.selectContext(userId), false, "read");
        Account account = mapper.selectAccount(userId, typeId, false);
        return account == null ? List.of() : mapper.selectLedger(account.accountId, beforeId, 100);
    }

    EmployeeContext lockContext(Long userId)
    {
        if (userId == null || mapper.lockUser(userId) == null) throw new ServiceException("员工不存在");
        mapper.lockProfile(userId);
        return mapper.selectContext(userId);
    }
    Account lockAccount(Long userId, Long typeId)
    {
        if (typeId == null || mapper.countActiveType(typeId) != 1) throw new ServiceException("假种未配置或未启用");
        Account account = new Account(); account.userId = userId; account.leaveTypeId = typeId;
        mapper.ensureAccount(account);
        Account locked = mapper.selectAccount(userId, typeId, true);
        if (locked == null) throw new ServiceException("额度账户初始化失败");
        return locked;
    }
    private List<Bucket> existingBuckets(Long userId, Long typeId)
    {
        Account account = mapper.selectAccount(userId, typeId, false);
        return account == null ? List.of() : mapper.selectBuckets(account.accountId, false);
    }

    Match match(EmployeeContext context, Long typeId, boolean lock)
    {
        Match result = new Match(); result.asOfDate = today(); result.periodYear = result.asOfDate.getYear();
        if (context == null || context.profileId == null || context.deptId == null || context.legalEntityId == null)
            return pending(result, "MISSING_PROFILE", "员工基础档案、法人主体或组织未配置");
        if (!"0".equals(context.userStatus) || !"0".equals(context.delFlag)
                || context.employeeStatus == null || !List.of("试用", "正式", "在职", "待离职").contains(context.employeeStatus))
            return pending(result, "INACTIVE_EMPLOYEE", "员工不在有效在职状态");
        if (typeId == null || mapper.countActiveType(typeId) != 1)
            return pending(result, "TYPE_NOT_CONFIGURED", "假种未配置或未启用");
        if (context.workLocation == null || context.workLocation.isBlank())
            return pending(result, "MISSING_LOCATION", "HR 尚未配置档案工作所在地");
        List<LocationMapping> mappings = mapper.matchLocations(context, lock);
        if (mappings.size() != 1) return pending(result, mappings.isEmpty() ? "MISSING_MAPPING" : "AMBIGUOUS_MAPPING", "工作所在地映射缺失或有歧义，请 HR 核对");
        result.mapping = mappings.get(0);
        List<Rule> candidates = new ArrayList<>(mapper.matchRules(context, typeId, result.mapping.locationCode, result.asOfDate, lock));
        if (candidates.isEmpty()) return pending(result, "NO_RULE", "没有生效的额度规则");
        candidates.sort(Comparator.comparing((Rule rule) -> rule.priority).reversed());
        if (candidates.size() > 1 && Objects.equals(candidates.get(0).priority, candidates.get(1).priority))
            return pending(result, "AMBIGUOUS_RULE", "同优先级命中多条额度规则，请 HR 核对");
        result.rule = hydrate(candidates.get(0));
        try { result.expectedUnits = calculator.calculate(result.rule, context, result.asOfDate); }
        catch (ServiceException invalid) { return pending(result, "RULE_CONTEXT_INCOMPLETE", invalid.getMessage()); }
        result.status = "READY"; return result;
    }
    private Match pending(Match result, String status, String reason)
    { result.status = status; result.reason = reason; return result; }
    private Balance view(EmployeeContext context, Long typeId, Match match, List<Bucket> buckets)
    {
        Balance result = new Balance(); result.userId = context == null ? null : context.userId; result.leaveTypeId = typeId;
        result.status = match.status; result.reason = match.reason; result.buckets = buckets;
        if (match.rule != null) { result.ruleId = match.rule.ruleId; result.ruleVersion = match.rule.version;
            result.ruleName = match.rule.name; result.displayUnit = match.rule.config.unit; result.minutesPerDay = match.rule.config.minutesPerDay; }
        if ("READY".equals(match.status))
        {
            result.availableUnits = 0L; result.reservedUnits = 0L; result.consumedUnits = 0L;
            for (Bucket bucket : buckets)
            {
                result.reservedUnits = Math.addExact(result.reservedUnits, bucket.reservedUnits);
                result.consumedUnits = Math.addExact(result.consumedUnits, bucket.consumedUnits);
                bucket.ruleVersion = requireRule(bucket.ruleId, false).version;
                bucket.sourceProblem = overtimeSources.problem(bucket, context);
                if (bucket.sourceProblem != null) { result.status = "OVERTIME_SOURCE_REVIEW"; result.reason = bucket.sourceProblem; }
                else if (!bucket.expiresOn.isBefore(match.asOfDate)) result.availableUnits = Math.addExact(result.availableUnits, bucket.availableUnits());
                else if (bucket.reservedUnits > 0) { result.status = "EXPIRY_HAS_RESERVATIONS"; result.reason = "到期来源仍有占用，已保留原桶等待审批结算"; }
            }
        }
        Bucket invalid=buckets.stream().filter(b->b.sourceProblem!=null).findFirst().orElse(null);
        if (invalid!=null) {result.status="OVERTIME_SOURCE_REVIEW";result.reason=invalid.sourceProblem;result.availableUnits=null;}
        return result;
    }

    private void expire(Account account, List<Bucket> buckets, Long actor, LocalDate asOf)
    {
        for (Bucket bucket : new ArrayList<>(buckets))
        {
            if (!bucket.expiresOn.isBefore(asOf) || List.of("CLOSED", "REVERSED").contains(bucket.expiryState)) continue;
            if ("OVERTIME".equals(bucket.sourceType) && overtimeSources.problem(bucket, mapper.selectContext(account.userId)) != null) continue;
            if (bucket.reservedUnits > 0)
            {
                if (!"WAITING_RESERVED".equals(bucket.expiryState)) { bucket.expiryState = "WAITING_RESERVED"; update(bucket); }
                continue;
            }
            Rule sourceRule = hydrate(mapper.selectRule(bucket.ruleId, true));
            long available = bucket.availableUnits();
            if (available < 0) throw new ServiceException("额度来源存在负值，请核对历史流水");
            long carry = "ANNUAL".equals(bucket.sourceType) ? Math.min(available,
                    AttendanceLeaveBalanceCalculator.units(sourceRule.config.carryLimit, bucket.displayUnit, bucket.minutesPerDay)) : 0;
            LocalDate carryExpires = bucket.expiresOn.plusMonths(sourceRule.config.carryExpiryMonths);
            if (carryExpires.isBefore(asOf)) carry = 0;
            if (carry > 0)
            {
                Bucket next = copyCarry(bucket, carry, carryExpires);
                must(mapper.insertBucket(next)); buckets.add(next);
                append(account, next, "CARRY_IN|" + bucket.bucketId, "CARRY_IN", carry, "按原发放规则结转", "{}", actor);
                bucket.carriedUnits = Math.addExact(bucket.carriedUnits, carry);
            }
            long expired = Math.subtractExact(available, carry);
            bucket.expiredUnits = Math.addExact(bucket.expiredUnits, expired); bucket.expiryState = "CLOSED";
            update(bucket);
            append(account, bucket, "EXPIRE|" + bucket.bucketId, "EXPIRE", -expired, "按原发放规则到期，结转部分另有来源桶", "{}", actor);
            if (carry > 0) append(account, bucket, "CARRY_OUT|" + bucket.bucketId, "CARRY_OUT", -carry, "转入对应结转来源桶", "{}", actor);
            must(mapper.bumpAccount(account.accountId));
        }
    }
    private Bucket copyCarry(Bucket old, long units, LocalDate expires)
    {
        Bucket b = new Bucket(); b.accountId = old.accountId; b.userId = old.userId; b.leaveTypeId = old.leaveTypeId;
        b.ruleId = old.ruleId; b.mappingId = old.mappingId; b.mappingVersion = old.mappingVersion;
        b.ownerDeptId = old.ownerDeptId; b.legalEntityId = old.legalEntityId; b.contextFingerprint = old.contextFingerprint;
        b.sourceKey = "CARRY|" + old.bucketId; b.sourceType = "CARRY"; b.periodYear = old.periodYear + 1;
        b.grantedUnits = units; b.expiresOn = expires; b.expiryState = "OPEN"; b.rowVersion = 0L;
        b.displayUnit = old.displayUnit; b.minutesPerDay = old.minutesPerDay; return b;
    }
    private Bucket newBucket(Account account, EmployeeContext context, Match match, String key, String type, int year)
    {
        Bucket b = new Bucket(); b.accountId = account.accountId; b.userId = account.userId; b.leaveTypeId = account.leaveTypeId;
        b.sourceKey = key; b.sourceType = type; b.periodYear = year; b.expiryState = "OPEN"; b.rowVersion = 0L;
        applySource(b, context, match); return b;
    }
    private void applySource(Bucket b, EmployeeContext context, Match match)
    {
        b.ruleId = match.rule.ruleId; b.mappingId = match.mapping.mappingId; b.mappingVersion = match.mapping.rowVersion;
        b.ownerDeptId = context.deptId; b.legalEntityId = context.legalEntityId;
        b.contextFingerprint = hash(sourceSnapshot(context, match)); b.displayUnit = match.rule.config.unit;
        b.minutesPerDay = match.rule.config.minutesPerDay;
    }
    private void update(Bucket bucket) { must(mapper.updateBucket(bucket)); bucket.rowVersion++; }
    private void append(Account account, Bucket bucket, String key, String action, long units,
            String reason, String source, Long actor)
    {
        Ledger ledger = new Ledger(); ledger.accountId = account.accountId; ledger.bucketId = bucket.bucketId;
        ledger.ruleId = bucket.ruleId; ledger.eventKey = key; ledger.action = action; ledger.units = units;
        ledger.reason = reason; ledger.operatorUserId = actor; ledger.contextFingerprint = bucket.contextFingerprint;
        ledger.sourceKey = bucket.sourceKey; ledger.contextJson = source; must(mapper.insertLedger(ledger));
    }
    private Command command(Account account, String key, String fingerprint, Long bucketId, long units)
    {
        Command c = new Command(); c.accountId = account.accountId; c.commandKey = key; c.fingerprint = fingerprint;
        c.bucketId = bucketId; c.resultUnits = units; must(mapper.insertCommand(c)); return c;
    }

    Balance prepareForLeave(Long userId,Long typeId)
    {
        requireSourceTransaction();
        if(!Objects.equals(access.actor(),userId)) throw new ServiceException("请假自动额度准备必须属于当前申请人");
        return recalculate(userId,typeId,false,true);
    }

    Balance previewOvertime(EmployeeContext context, Long typeId)
    { return view(context,typeId,match(context,typeId,false),existingBuckets(context.userId,typeId)); }

    /** Called only inside the source service transaction, after period/day/context locks. */
    Bucket grantOvertime(EmployeeContext context, Match match, AttendanceOvertimeTransferModels.Transfer source)
    {
        requireSourceTransaction();
        if (!"READY".equals(match.status) || !"COMPENSATORY".equals(match.rule.config.leaveCategory)
                || !"SOURCE_ONLY".equals(match.rule.config.calculation)) throw new ServiceException("没有匹配的调休来源规则");
        Account account=lockAccount(context.userId,source.leaveTypeId);
        mapper.selectBuckets(account.accountId,true);
        String commandIdentity=hash(source.shopId+"|"+source.operatorUserId+"|"+source.clientRequestId);
        String key="OVERTIME|"+commandIdentity;
        Bucket bucket=newBucket(account,context,match,key,"OVERTIME",source.sourceBusinessDate.getYear());
        bucket.expiresOn=LocalDate.of(bucket.periodYear,12,31).plusMonths(match.rule.config.expiryMonthsAfterYear);
        if (bucket.expiresOn.isBefore(match.asOfDate)) throw new ServiceException("该来源按匹配规则已到期，不能重新发放");
        bucket.grantedUnits=Math.multiplyExact(source.transferMinutes.longValue(),UNITS_PER_MINUTE);
        bucket.ruleGrantedUnits=bucket.grantedUnits;
        must(mapper.insertBucket(bucket));
        source.bucketId=bucket.bucketId;source.ruleId=bucket.ruleId;source.mappingId=bucket.mappingId;source.mappingVersion=bucket.mappingVersion;
        Map<String,Object> snapshot=new LinkedHashMap<>();snapshot.put("source",source);snapshot.put("employee",context);snapshot.put("mapping",match.mapping);snapshot.put("ruleVersion",match.rule.version);
        append(account,bucket,"OT_GRANT|"+commandIdentity,"OVERTIME_GRANT",bucket.grantedUnits,source.reason,write(snapshot),source.operatorUserId);
        command(account,"OT_GRANT|"+commandIdentity,source.requestFingerprint,bucket.bucketId,bucket.grantedUnits);
        must(mapper.bumpAccount(account.accountId));return bucket;
    }
    void reverseOvertime(AttendanceOvertimeTransferModels.Transfer original, AttendanceOvertimeTransferModels.Transfer reversal)
    {
        requireSourceTransaction();
        // The source service already locked the employee. Reversal retains the original company/rule identity.
        Account account=mapper.selectAccount(original.userId,original.leaveTypeId,true);
        if (account==null) throw new ServiceException("原核定额度账户不存在");
        Bucket bucket=mapper.selectBuckets(account.accountId,true).stream().filter(b->original.bucketId.equals(b.bucketId)).findFirst()
                .orElseThrow(()->new ServiceException("原核定额度分桶不存在"));
        long units=Math.multiplyExact(original.transferMinutes.longValue(),UNITS_PER_MINUTE);
        if (!"OVERTIME".equals(bucket.sourceType) || !Objects.equals(bucket.legalEntityId,original.legalEntityId)
                || !Objects.equals(bucket.ruleId,original.ruleId) || bucket.grantedUnits!=units || bucket.ruleGrantedUnits!=units)
            throw new ServiceException("原核定与额度不一致，不能伪造撤销");
        if (bucket.reservedUnits!=0 || bucket.consumedUnits!=0 || bucket.expiredUnits!=0 || bucket.carriedUnits!=0
                || !"OPEN".equals(bucket.expiryState) || bucket.expiresOn.isBefore(today()))
            throw new ServiceException("原额度已占用、消耗、到期或结转，不能撤销后重新分配来源");
        bucket.grantedUnits=0;bucket.ruleGrantedUnits=0;bucket.expiryState="REVERSED";update(bucket);
        append(account,bucket,"OT_REVERSE|"+reversal.requestFingerprint,"OVERTIME_REVERSE",-units,reversal.reason,write(reversal),reversal.operatorUserId);
        command(account,"OT_REVERSE|"+reversal.requestFingerprint,reversal.requestFingerprint,bucket.bucketId,-units);
        must(mapper.bumpAccount(account.accountId));
    }
    private void requireSourceTransaction()
    { if (!org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive()) throw new ServiceException("转休来源与额度必须在同一事务处理"); }

    public List<Rule> rules() { enabled(); access.require("rule"); return mapper.selectRules(access.actor(), access.admin()).stream().map(this::hydrate).toList(); }
    public Rule rule(Long ruleId) { enabled(); access.require("rule"); Rule r = requireRule(ruleId, false); access.owner(r.ownerDeptId, r.legalEntityId); return hydrate(r); }
    @Transactional
    public Rule saveRule(Long ruleId, RuleDraft input)
    {
        enabled(); access.require("rule");
        if (input == null) throw new ServiceException("规则内容不能为空");
        Rule r = json.copy().disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).convertValue(input, Rule.class);
        r.name = text(r.name, 128, "规则名称"); r.locationCode = text(r.locationCode, 64, "工作地规则码");
        access.owner(r.ownerDeptId, r.legalEntityId);
        if (r.leaveTypeId == null || mapper.countActiveType(r.leaveTypeId) != 1) throw new ServiceException("规则假种未启用");
        calculator.validate(r); if (r.tiers == null) r.tiers = new ArrayList<>(); r.configJson = write(r.config);
        if (ruleId == null)
        {
            r.ruleId = null; r.familyId = null; r.version = 1; r.rowVersion = 0L; r.status = "DRAFT";
            r.createdBy = access.actor(); r.publishedBy = null; r.publishedAt = null;
            if (input.previousRuleId != null)
            {
                Rule previous = requireRule(input.previousRuleId, true);
                access.owner(previous.ownerDeptId, previous.legalEntityId);
                Rule family = requireRule(previous.familyId, true);
                if (!Objects.equals(family.ownerDeptId, r.ownerDeptId) || !Objects.equals(family.legalEntityId, r.legalEntityId)
                        || !Objects.equals(family.leaveTypeId, r.leaveTypeId)) throw new ServiceException("新版本不能改变规则归属或假种");
                r.familyId = family.familyId; r.version = Math.addExact(mapper.selectLastFamilyVersion(r.familyId), 1);
            }
            must(mapper.insertRule(r)); if (r.familyId == null) { must(mapper.initializeFamily(r.ruleId)); r.familyId = r.ruleId; }
        }
        else
        {
            Rule current = requireRule(ruleId, true); access.owner(current.ownerDeptId, current.legalEntityId);
            if (!"DRAFT".equals(current.status) || !Objects.equals(current.rowVersion, input.rowVersion)) throw new ServiceException("规则已发布或版本已变化");
            if (!Objects.equals(current.ownerDeptId, r.ownerDeptId) || !Objects.equals(current.legalEntityId, r.legalEntityId)
                    || !Objects.equals(current.leaveTypeId, r.leaveTypeId)) throw new ServiceException("规则归属和假种不能原位更换");
            r.ruleId = ruleId; r.rowVersion = current.rowVersion; must(mapper.updateRule(r)); mapper.deleteTiers(ruleId);
        }
        for (Tier tier : r.tiers) { tier.ruleId = r.ruleId; must(mapper.insertTier(tier)); }
        return hydrate(requireRule(r.ruleId, false));
    }
    @Transactional
    public Rule publishRule(Long ruleId, Long version)
    {
        enabled(); access.require("rule"); Rule r = hydrate(requireRule(ruleId, true)); access.owner(r.ownerDeptId, r.legalEntityId);
        calculator.validate(r); if (mapper.countActiveType(r.leaveTypeId) != 1) throw new ServiceException("规则假种未启用"); if (version == null) throw new ServiceException("规则版本不能为空");
        must(mapper.publishRule(ruleId, version, access.actor())); return hydrate(requireRule(ruleId, false));
    }
    public List<LocationMapping> locations() { enabled(); access.require("rule"); return mapper.selectLocations(access.actor(), access.admin()); }
    @Transactional
    public LocationMapping saveLocation(Long mappingId, LocationMapping body)
    {
        enabled(); access.require("rule");
        if (body == null) throw new ServiceException("工作地映射不能为空");
        access.owner(body.ownerDeptId, body.legalEntityId);
        body.workLocation = text(body.workLocation, 160, "档案工作地原文"); body.locationCode = text(body.locationCode, 64, "工作地规则码");
        body.reason = text(body.reason, 500, "映射维护原因"); body.updatedBy = access.actor();
        if (mappingId == null) { body.mappingId = null; body.rowVersion = 0L; must(mapper.insertLocation(body)); }
        else
        {
            LocationMapping current = mapper.selectLocation(mappingId, true);
            if (current == null) throw new ServiceException("映射不存在"); access.owner(current.ownerDeptId, current.legalEntityId);
            if (!Objects.equals(current.ownerDeptId, body.ownerDeptId) || !Objects.equals(current.legalEntityId, body.legalEntityId)) throw new ServiceException("映射归属不能原位更换");
            body.mappingId = mappingId; must(mapper.updateLocation(body));
        }
        return mapper.selectLocation(body.mappingId, false);
    }
    private Rule requireRule(Long id, boolean lock)
    { Rule r = mapper.selectRule(id, lock); if (r == null) throw new ServiceException("额度规则不存在"); return r; }
    private Rule hydrate(Rule rule)
    {
        if (rule == null) throw new ServiceException("额度来源规则不存在");
        try { rule.config = json.readerFor(RuleConfig.class).with(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).readValue(rule.configJson); }
        catch (Exception invalid) { throw new ServiceException("已存规则配置无法读取，请 HR 核对"); }
        rule.tiers = mapper.selectTiers(rule.ruleId); return rule;
    }
    private String sourceSnapshot(EmployeeContext context, Match match)
    {
        Map<String,Object> snapshot = new LinkedHashMap<>(); snapshot.put("employee", context);
        snapshot.put("mapping", match.mapping); snapshot.put("ruleId", match.rule.ruleId); snapshot.put("ruleVersion", match.rule.version);
        return write(snapshot);
    }
    private String write(Object value)
    { try { return json.writeValueAsString(value); } catch (Exception failure) { throw new ServiceException("额度来源快照无法保存"); } }
    private static String hash(String value)
    { try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); } catch (Exception failure) { throw new IllegalStateException(failure); } }
    private static boolean sameDecimal(BigDecimal a, BigDecimal b)
    { return a==null ? b==null : b!=null && a.compareTo(b)==0; }
    private static String text(String value, int max, String field)
    { if (value == null || value.isBlank() || value.length() > max) throw new ServiceException(field + "不能为空或过长"); return value.trim(); }
    private static void must(int count) { if (count != 1) throw new ServiceException("额度数据版本已变化，请重新核对"); }
    private LocalDate today() { return LocalDate.now(clock); }
    private void enabled() { featureGate.requireEnabled(BusinessFeatureGate.ATTENDANCE_V2); }
}
