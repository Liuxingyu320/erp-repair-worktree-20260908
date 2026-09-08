package com.erp.system.service.impl;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.erp.system.domain.SysNotice;
import com.erp.system.domain.SysNoticeAudience;
import com.erp.system.domain.dto.SysNoticeAudiencePreviewRequest;
import com.erp.system.domain.dto.SysNoticePublishRequest;
import com.erp.system.domain.vo.SysNoticeAudienceOptionsVo;
import com.erp.system.domain.vo.SysNoticeAudiencePreviewVo;
import com.erp.system.domain.vo.SysNoticeAudienceRecipientVo;
import com.erp.system.mapper.SysNoticeAudienceMapper;
import com.erp.system.mapper.SysNoticeMapper;
import com.erp.system.mapper.SysNoticeRecipientMapper;
import com.erp.system.service.BusinessFeatureGate;
import com.erp.system.service.ISysNoticeService;
import com.erp.system.service.support.NoticeHtmlSanitizer;

/**
 * 公告草稿、受众、发布快照和版本状态机。
 */
@Service
public class SysNoticeServiceImpl implements ISysNoticeService
{
    public static final String DRAFT = "DRAFT";
    public static final String SCHEDULED = "SCHEDULED";
    public static final String PUBLISHED = "PUBLISHED";
    public static final String OFFLINE = "OFFLINE";

    private static final Set<String> AUDIENCE_TYPES = Set.of("ALL", "DEPT", "ROLE", "USER", "MIXED");
    private static final Set<String> TARGET_TYPES = Set.of("ALL", "DEPT", "ROLE", "USER");
    private static final int MAX_AUDIENCE_RULES = 500;
    private static final int MAX_SCHEDULE_SCAN = 100;

    private final SysNoticeMapper noticeMapper;
    private final SysNoticeAudienceMapper audienceMapper;
    private final SysNoticeRecipientMapper recipientMapper;
    private final NoticeHtmlSanitizer noticeHtmlSanitizer;
    private final BusinessFeatureGate businessFeatureGate;

    public SysNoticeServiceImpl(SysNoticeMapper noticeMapper,
            SysNoticeAudienceMapper audienceMapper,
            SysNoticeRecipientMapper recipientMapper,
            NoticeHtmlSanitizer noticeHtmlSanitizer,
            BusinessFeatureGate businessFeatureGate)
    {
        this.noticeMapper = noticeMapper;
        this.audienceMapper = audienceMapper;
        this.recipientMapper = recipientMapper;
        this.noticeHtmlSanitizer = noticeHtmlSanitizer;
        this.businessFeatureGate = businessFeatureGate;
    }

    @Override
    public SysNotice selectNoticeById(Long noticeId)
    {
        SysNotice notice = sanitizeNotice(noticeMapper.selectNoticeById(noticeId));
        if (notice != null)
        {
            notice.setAudiences(audienceMapper.selectByNoticeId(noticeId));
        }
        return notice;
    }

    @Override
    public List<SysNotice> selectNoticeList(SysNotice notice)
    {
        return sanitizeNotices(noticeMapper.selectNoticeList(notice));
    }

    /**
     * 任何新增调用（包括旧客户端传 status=0）都只能创建草稿。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int insertNotice(SysNotice notice)
    {
        requireNotice(notice);
        List<SysNoticeAudience> audiences = normalizeDraftAudiences(notice.getAudienceType(), notice.getAudiences());
        notice.setLifecycleStatus(DRAFT);
        notice.setStatus("1");
        notice.setScheduledPublishTime(null);
        notice.setPublishedTime(null);
        notice.setExpireTime(normalizeFutureExpireTime(notice.getExpireTime(), new Date()));
        notice.setVersion(1L);
        notice.setPreviousNoticeId(null);
        notice.setAudienceType(normalizeDraftAudienceType(notice.getAudienceType(), audiences));
        sanitizeNotice(notice);
        int rows = noticeMapper.insertNotice(notice);
        replaceAudiences(notice.getNoticeId(), audiences);
        notice.setAudiences(audiences);
        return rows;
    }

    /**
     * 已发布、待发布和下线公告禁止原地覆盖；草稿使用版本号防并发覆盖。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int updateNotice(SysNotice notice)
    {
        requireNotice(notice);
        requirePositiveVersion(notice.getVersion());
        SysNotice current = lockNotice(notice.getNoticeId());
        requireVersion(current, notice.getVersion());
        requireLifecycle(current, DRAFT, "只有草稿可以直接修改，已发布内容请创建新版本");

        List<SysNoticeAudience> audiences = normalizeDraftAudiences(notice.getAudienceType(), notice.getAudiences());
        notice.setLifecycleStatus(DRAFT);
        notice.setStatus("1");
        notice.setScheduledPublishTime(null);
        notice.setPublishedTime(null);
        notice.setAudienceType(normalizeDraftAudienceType(notice.getAudienceType(), audiences));
        notice.setExpireTime(normalizeFutureExpireTime(notice.getExpireTime(), new Date()));
        sanitizeNotice(notice);
        if (noticeMapper.updateNotice(notice) != 1)
        {
            throw conflict("公告已被其他管理员修改，请刷新后重试");
        }
        replaceAudiences(notice.getNoticeId(), audiences);
        notice.setVersion(notice.getVersion() + 1);
        notice.setAudiences(audiences);
        return 1;
    }

    @Override
    public SysNoticeAudiencePreviewVo previewAudience(SysNoticeAudiencePreviewRequest request)
    {
        if (request == null)
        {
            throw invalid("受众预览请求不能为空");
        }
        List<SysNoticeAudience> audiences = normalizeAndValidateAudiences(
                request.getAudienceType(), request.getAudiences(), false);
        return buildAudiencePreview(audiences);
    }

    @Override
    public SysNoticeAudienceOptionsVo selectAudienceOptions(String keyword)
    {
        String normalizedKeyword = keyword == null ? null : keyword.trim();
        if (normalizedKeyword != null && normalizedKeyword.length() > 50)
        {
            throw invalid("受众搜索内容不能超过50个字符");
        }
        SysNoticeAudienceOptionsVo options = new SysNoticeAudienceOptionsVo();
        options.setDepartments(audienceMapper.selectDepartmentOptions());
        options.setRoles(audienceMapper.selectRoleOptions());
        options.setUsers(audienceMapper.selectUserOptions(normalizedKeyword, 50));
        return options;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SysNotice publishNotice(Long noticeId, SysNoticePublishRequest request, String operator)
    {
        requireWorkflowEnabled();
        if (request == null)
        {
            throw invalid("发布请求不能为空");
        }
        requirePositiveVersion(request.getVersion());
        Date now = new Date();
        SysNotice current = lockNotice(noticeId);
        requireVersion(current, request.getVersion());
        requireLifecycle(current, DRAFT, "只有草稿可以发布");

        List<SysNoticeAudience> audiences = normalizeAndValidateAudiences(
                current.getAudienceType(), audienceMapper.selectByNoticeId(noticeId), false);
        List<SysNoticeAudienceRecipientVo> recipients = resolveRecipients(audiences);
        if (recipients.isEmpty())
        {
            throw invalid("受众范围内没有启用账号，不能发布");
        }

        String mode = normalize(request.getPublishMode());
        Date publishTime = now;
        if ("SCHEDULED".equals(mode))
        {
            publishTime = request.getScheduledPublishTime();
            if (publishTime == null || !publishTime.after(now))
            {
                throw invalid("计划发布时间必须晚于当前时间");
            }
        }
        else if (!"IMMEDIATE".equals(mode))
        {
            throw invalid("发布方式无效");
        }
        validateExpireTime(request.getExpireTime(), publishTime);

        current.setUpdateBy(operator);
        current.setExpireTime(request.getExpireTime());
        if ("SCHEDULED".equals(mode))
        {
            current.setStatus("1");
            current.setScheduledPublishTime(publishTime);
            current.setPublishedTime(null);
            updateLifecycle(current, DRAFT, SCHEDULED);
        }
        else
        {
            persistRecipientSnapshot(noticeId, recipients, now, "NORMAL_PUBLISH");
            current.setStatus("0");
            current.setScheduledPublishTime(null);
            current.setPublishedTime(now);
            updateLifecycle(current, DRAFT, PUBLISHED);
        }
        return selectNoticeById(noticeId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SysNotice cancelScheduledNotice(Long noticeId, Long version, String operator)
    {
        requirePositiveVersion(version);
        SysNotice current = lockNotice(noticeId);
        requireVersion(current, version);
        requireLifecycle(current, SCHEDULED, "只有待发布公告可以取消计划");
        current.setStatus("1");
        current.setScheduledPublishTime(null);
        current.setPublishedTime(null);
        current.setUpdateBy(operator);
        updateLifecycle(current, SCHEDULED, DRAFT);
        return selectNoticeById(noticeId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SysNotice offlineNotice(Long noticeId, Long version, String operator)
    {
        requirePositiveVersion(version);
        SysNotice current = lockNotice(noticeId);
        requireVersion(current, version);
        requireLifecycle(current, PUBLISHED, "只有已发布公告可以下线");
        current.setStatus("1");
        current.setUpdateBy(operator);
        updateLifecycle(current, PUBLISHED, OFFLINE);
        return selectNoticeById(noticeId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SysNotice createNewVersion(Long noticeId, Long version, String operator)
    {
        requirePositiveVersion(version);
        SysNotice source = lockNotice(noticeId);
        requireVersion(source, version);
        if (!PUBLISHED.equals(source.getLifecycleStatus()) && !OFFLINE.equals(source.getLifecycleStatus()))
        {
            throw invalid("只有已发布或已下线公告可以创建新版本");
        }
        List<SysNoticeAudience> sourceAudiences = audienceMapper.selectByNoticeId(noticeId);
        SysNotice draft = new SysNotice();
        draft.setNoticeTitle(source.getNoticeTitle());
        draft.setNoticeType(source.getNoticeType());
        draft.setNoticeContent(source.getNoticeContent());
        draft.setStatus("1");
        draft.setLifecycleStatus(DRAFT);
        draft.setAudienceType(source.getAudienceType());
        draft.setExpireTime(null);
        draft.setVersion(source.getVersion() + 1);
        draft.setPreviousNoticeId(source.getNoticeId());
        draft.setCreateBy(operator);
        draft.setRemark(source.getRemark());
        sanitizeNotice(draft);
        noticeMapper.insertNotice(draft);
        replaceAudiences(draft.getNoticeId(), sourceAudiences);
        draft.setAudiences(sourceAudiences);
        return draft;
    }

    @Override
    public List<Long> selectDueScheduledNoticeIds(int limit)
    {
        int safeLimit = Math.max(1, Math.min(limit, MAX_SCHEDULE_SCAN));
        return noticeMapper.selectDueScheduledNoticeIds(new Date(), safeLimit);
    }

    /**
     * 由独立 Scheduler Bean 经 Spring 代理调用，保证每个公告单独事务和行锁。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean publishScheduledNotice(Long noticeId)
    {
        Date now = new Date();
        SysNotice current = lockNotice(noticeId);
        if (!SCHEDULED.equals(current.getLifecycleStatus())
                || current.getScheduledPublishTime() == null
                || current.getScheduledPublishTime().after(now))
        {
            return false;
        }
        if (current.getExpireTime() != null && !current.getExpireTime().after(now))
        {
            current.setStatus("1");
            current.setUpdateBy("notice-scheduler");
            updateLifecycle(current, SCHEDULED, OFFLINE);
            return false;
        }
        List<SysNoticeAudience> audiences = normalizeAndValidateAudiences(
                current.getAudienceType(), audienceMapper.selectByNoticeId(noticeId), false);
        List<SysNoticeAudienceRecipientVo> recipients = resolveRecipients(audiences);
        if (recipients.isEmpty())
        {
            throw invalid("计划发布受众内没有启用账号");
        }
        persistRecipientSnapshot(noticeId, recipients, now, "SCHEDULED_PUBLISH");
        current.setStatus("0");
        current.setPublishedTime(now);
        current.setUpdateBy("notice-scheduler");
        updateLifecycle(current, SCHEDULED, PUBLISHED);
        return true;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int offlineExpiredNotices()
    {
        return noticeMapper.updateExpiredNotices(new Date());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int deleteNoticeById(Long noticeId)
    {
        return deleteNoticeByIds(new Long[] { noticeId });
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int deleteNoticeByIds(Long[] noticeIds)
    {
        if (noticeIds == null || noticeIds.length == 0)
        {
            return 0;
        }
        List<Long> sortedIds = Arrays.stream(noticeIds)
                .filter(Objects::nonNull).distinct().sorted().collect(Collectors.toList());
        for (Long noticeId : sortedIds)
        {
            SysNotice current = lockNotice(noticeId);
            requireLifecycle(current, DRAFT, "只有草稿可以删除；待发布请先取消，已发布请下线");
            audienceMapper.deleteByNoticeId(noticeId);
            recipientMapper.deleteByNoticeId(noticeId);
            if (noticeMapper.deleteNoticeById(noticeId) != 1)
            {
                throw conflict("公告状态已变化，请刷新后重试");
            }
        }
        return sortedIds.size();
    }

    private void replaceAudiences(Long noticeId, List<SysNoticeAudience> audiences)
    {
        if (noticeId == null)
        {
            throw new IllegalStateException("公告ID未生成");
        }
        audienceMapper.deleteByNoticeId(noticeId);
        if (audiences.isEmpty())
        {
            return;
        }
        audiences.forEach(audience -> audience.setNoticeId(noticeId));
        audienceMapper.insertBatch(audiences);
    }

    private SysNoticeAudiencePreviewVo buildAudiencePreview(List<SysNoticeAudience> audiences)
    {
        List<SysNoticeAudienceRecipientVo> recipients = resolveRecipients(audiences);
        Map<String, Integer> organizations = new LinkedHashMap<>();
        recipients.forEach(row -> organizations.merge(
                row.getDeptName() == null || row.getDeptName().isBlank() ? "未分配组织" : row.getDeptName(),
                1, Integer::sum));
        SysNoticeAudiencePreviewVo preview = new SysNoticeAudiencePreviewVo();
        preview.setRecipientCount(recipients.size());
        preview.setRuleCount(audiences.size());
        preview.setOrganizationCounts(organizations);
        if (recipients.isEmpty())
        {
            preview.setWarnings(Collections.singletonList("当前规则没有匹配到启用账号，不能发布"));
        }
        return preview;
    }

    private List<SysNoticeAudienceRecipientVo> resolveRecipients(List<SysNoticeAudience> audiences)
    {
        List<SysNoticeAudienceRecipientVo> rows = audienceMapper.selectResolvedRecipients(audiences);
        if (rows == null)
        {
            return Collections.emptyList();
        }
        Map<Long, SysNoticeAudienceRecipientVo> unique = new LinkedHashMap<>();
        rows.stream().filter(row -> row != null && row.getUserId() != null)
                .forEach(row -> unique.putIfAbsent(row.getUserId(), row));
        return new ArrayList<>(unique.values());
    }

    private void persistRecipientSnapshot(Long noticeId, List<SysNoticeAudienceRecipientVo> recipients,
            Date deliveredTime, String source)
    {
        List<Long> userIds = recipients.stream().map(SysNoticeAudienceRecipientVo::getUserId)
                .distinct().collect(Collectors.toList());
        if (userIds.isEmpty())
        {
            throw invalid("接收人快照不能为空");
        }
        if (recipientMapper.countByNoticeId(noticeId) != 0)
        {
            throw conflict("公告已存在接收人快照，不能重复发布");
        }
        recipientMapper.insertBatch(noticeId, userIds, deliveredTime, source);
        if (recipientMapper.countByNoticeId(noticeId) != userIds.size())
        {
            throw new IllegalStateException("公告接收人快照写入不完整");
        }
    }

    private List<SysNoticeAudience> normalizeDraftAudiences(String audienceType,
            List<SysNoticeAudience> audiences)
    {
        if (audiences == null || audiences.isEmpty())
        {
            return Collections.emptyList();
        }
        return normalizeAndValidateAudiences(audienceType, audiences, true);
    }

    private List<SysNoticeAudience> normalizeAndValidateAudiences(String audienceType,
            List<SysNoticeAudience> audiences, boolean allowEmpty)
    {
        String normalizedAudienceType = normalize(audienceType);
        if (!AUDIENCE_TYPES.contains(normalizedAudienceType))
        {
            throw invalid("受众类型无效");
        }
        if (audiences == null || audiences.isEmpty())
        {
            if (allowEmpty)
            {
                return Collections.emptyList();
            }
            throw invalid("至少选择一项公告受众");
        }
        if (audiences.size() > MAX_AUDIENCE_RULES)
        {
            throw invalid("公告受众规则不能超过500项");
        }
        List<SysNoticeAudience> normalized = new ArrayList<>();
        Set<String> unique = new LinkedHashSet<>();
        for (SysNoticeAudience source : audiences)
        {
            if (source == null)
            {
                throw invalid("受众规则不能为空");
            }
            String targetType = normalize(source.getTargetType());
            Long targetId = source.getTargetId();
            if (!TARGET_TYPES.contains(targetType))
            {
                throw invalid("受众目标类型无效");
            }
            if (("ALL".equals(targetType) && !Long.valueOf(0L).equals(targetId))
                    || (!"ALL".equals(targetType) && (targetId == null || targetId <= 0)))
            {
                throw invalid("受众目标无效");
            }
            String key = targetType + ":" + targetId;
            if (!unique.add(key))
            {
                throw invalid("公告受众规则重复：" + key);
            }
            SysNoticeAudience item = new SysNoticeAudience();
            item.setTargetType(targetType);
            item.setTargetId(targetId);
            item.setIncludeChildren("DEPT".equals(targetType) && source.isIncludeChildren());
            normalized.add(item);
        }
        validateAudienceTypeMatchesRules(normalizedAudienceType, normalized);
        return normalized;
    }

    private void validateAudienceTypeMatchesRules(String audienceType, List<SysNoticeAudience> audiences)
    {
        Set<String> targetTypes = audiences.stream().map(SysNoticeAudience::getTargetType)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if ("ALL".equals(audienceType))
        {
            if (audiences.size() != 1 || !targetTypes.equals(Set.of("ALL")))
            {
                throw invalid("全员受众只能包含一条 ALL 规则");
            }
            return;
        }
        if (targetTypes.contains("ALL"))
        {
            throw invalid("全员规则不能与其他受众混用");
        }
        if (!"MIXED".equals(audienceType) && !targetTypes.equals(Set.of(audienceType)))
        {
            throw invalid("受众类型与目标规则不一致");
        }
    }

    private String normalizeDraftAudienceType(String audienceType, List<SysNoticeAudience> audiences)
    {
        if (!audiences.isEmpty())
        {
            return normalize(audienceType);
        }
        String normalized = normalize(audienceType);
        return AUDIENCE_TYPES.contains(normalized) ? normalized : "MIXED";
    }

    private void updateLifecycle(SysNotice notice, String expected, String target)
    {
        if (noticeMapper.updateNoticeLifecycle(notice, expected, target) != 1)
        {
            throw conflict("公告状态或版本已变化，请刷新后重试");
        }
        notice.setLifecycleStatus(target);
        notice.setVersion(notice.getVersion() + 1);
    }

    private SysNotice lockNotice(Long noticeId)
    {
        if (noticeId == null || noticeId <= 0)
        {
            throw invalid("公告ID无效");
        }
        SysNotice notice = noticeMapper.selectNoticeByIdForUpdate(noticeId);
        if (notice == null)
        {
            throw new SysNoticeWorkflowException(404, "NOTICE_NOT_FOUND", "公告不存在");
        }
        return notice;
    }

    private void requireWorkflowEnabled()
    {
        if (businessFeatureGate == null || !businessFeatureGate.isEnabled(BusinessFeatureGate.NOTICE_WORKFLOW))
        {
            throw new SysNoticeWorkflowException(403, "NOTICE_WORKFLOW_DISABLED",
                    "公告发布工作流尚未启用；当前只能保存草稿");
        }
    }

    private static void validateExpireTime(Date expireTime, Date publishTime)
    {
        if (expireTime != null && (publishTime == null || !expireTime.after(publishTime)))
        {
            throw invalid("到期时间必须晚于发布时间");
        }
    }

    private static Date normalizeFutureExpireTime(Date expireTime, Date now)
    {
        if (expireTime != null && !expireTime.after(now))
        {
            throw invalid("到期时间必须晚于当前时间");
        }
        return expireTime;
    }

    private static void requireNotice(SysNotice notice)
    {
        if (notice == null)
        {
            throw invalid("公告不能为空");
        }
    }

    private static void requirePositiveVersion(Long version)
    {
        if (version == null || version <= 0)
        {
            throw invalid("公告版本无效");
        }
    }

    private static void requireVersion(SysNotice current, Long expected)
    {
        if (!Objects.equals(current.getVersion(), expected))
        {
            throw conflict("公告已被其他管理员处理，请刷新后重试");
        }
    }

    private static void requireLifecycle(SysNotice notice, String expected, String message)
    {
        if (!expected.equals(notice.getLifecycleStatus()))
        {
            throw invalid(message);
        }
    }

    private static String normalize(String value)
    {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }

    private List<SysNotice> sanitizeNotices(List<SysNotice> notices)
    {
        if (notices != null)
        {
            notices.forEach(this::sanitizeNotice);
        }
        return notices;
    }

    private SysNotice sanitizeNotice(SysNotice notice)
    {
        if (notice != null)
        {
            notice.setNoticeContent(noticeHtmlSanitizer.sanitize(notice.getNoticeContent()));
        }
        return notice;
    }

    private static SysNoticeWorkflowException invalid(String message)
    {
        return new SysNoticeWorkflowException(400, "NOTICE_WORKFLOW_INVALID", message);
    }

    private static SysNoticeWorkflowException conflict(String message)
    {
        return new SysNoticeWorkflowException(409, "NOTICE_VERSION_CONFLICT", message);
    }
}
