package com.erp.oa.service.impl;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import org.springframework.beans.BeanUtils;
import com.erp.common.core.web.domain.BaseEntity;
import com.erp.oa.constant.OaSignPackageStatus;
import com.erp.oa.constant.OaSignSigningSequence;
import com.erp.oa.domain.OaSignEvent;
import com.erp.oa.domain.OaSignPackage;
import com.erp.oa.domain.OaSignPackageDocument;
import com.erp.oa.domain.OaSignPlan;
import com.erp.oa.domain.OaSignPlanTemplate;
import com.erp.oa.domain.OaSignTemplate;
import com.erp.oa.domain.OaSignTask;
import com.erp.oa.domain.OaSignTaskEvent;
import com.erp.oa.domain.vo.OaSignTaskDetail;

/** Creates detached business-facing response copies without technical integrity hashes. */
public final class OaSignResponseSanitizer
{
    private OaSignResponseSanitizer()
    {
    }

    public static OaSignTemplate businessTemplate(OaSignTemplate source)
    {
        if (source == null)
        {
            return null;
        }
        OaSignTemplate target = new OaSignTemplate();
        target.setTemplateId(source.getTemplateId());
        target.setTemplateType(source.getTemplateType());
        target.setTemplateName(source.getTemplateName());
        target.setTemplateVersion(source.getTemplateVersion());
        target.setScenario(source.getScenario());
        target.setEmploymentType(source.getEmploymentType());
        target.setSocialType(source.getSocialType());
        target.setPostLevelScope(source.getPostLevelScope());
        target.setSalaryVersion(source.getSalaryVersion());
        target.setFileUrl(source.getFileUrl());
        target.setFileName(source.getFileName());
        target.setFileSize(source.getFileSize());
        target.setRequiredPlaceholders(source.getRequiredPlaceholders());
        target.setOptionalPlaceholders(source.getOptionalPlaceholders());
        target.setEmployeeVisible(source.getEmployeeVisible());
        target.setReadConfirmationRequired(source.getReadConfirmationRequired());
        target.setEmployeeSignRequired(source.getEmployeeSignRequired());
        target.setSignaturePositionJson(source.getSignaturePositionJson());
        target.setCompanySealPositionJson(source.getCompanySealPositionJson());
        target.setCompanySealRequired(source.getCompanySealRequired());
        target.setMatchConditionJson(source.getMatchConditionJson());
        target.setSortOrder(source.getSortOrder());
        target.setStatus(source.getStatus());
        copyBusinessAudit(source, target);
        target.setParams(new HashMap<>());
        return target;
    }

    public static List<OaSignTemplate> businessTemplates(List<OaSignTemplate> source)
    {
        List<OaSignTemplate> target = new ArrayList<>();
        if (source == null)
        {
            return target;
        }
        for (OaSignTemplate template : source)
        {
            target.add(businessTemplate(template));
        }
        return target;
    }

    public static OaSignPlan businessPlan(OaSignPlan source)
    {
        if (source == null)
        {
            return null;
        }
        OaSignPlan target = new OaSignPlan();
        target.setPlanId(source.getPlanId());
        target.setPlanName(source.getPlanName());
        target.setScenario(source.getScenario());
        target.setPostName(source.getPostName());
        target.setEmploymentType(source.getEmploymentType());
        target.setSocialType(source.getSocialType());
        target.setServicePersonType(source.getServicePersonType());
        target.setInsuranceType(source.getInsuranceType());
        target.setPostLevelSnapshot(source.getPostLevelSnapshot());
        target.setSalaryVersion(source.getSalaryVersion());
        target.setEntryDate(source.getEntryDate());
        target.setContractStartDate(source.getContractStartDate());
        target.setContractEndDate(source.getContractEndDate());
        target.setProbationStartDate(source.getProbationStartDate());
        target.setProbationEndDate(source.getProbationEndDate());
        target.setBaseSalary(source.getBaseSalary());
        target.setPostSalary(source.getPostSalary());
        target.setFieldAllowance(source.getFieldAllowance());
        target.setSalaryTotal(source.getSalaryTotal());
        target.setShopDeptId(source.getShopDeptId());
        target.setLegalEntityId(source.getLegalEntityId());
        target.setLegalEntityName(source.getLegalEntityName());
        target.setRuleJson(source.getRuleJson());
        target.setDefaultValuesJson(source.getDefaultValuesJson());
        target.setSignDeadlineDays(source.getSignDeadlineDays());
        target.setReminderPolicyJson(source.getReminderPolicyJson());
        target.setAutoSendConditionJson(source.getAutoSendConditionJson());
        target.setStatus(source.getStatus());
        target.setSortOrder(source.getSortOrder());
        copyBusinessAudit(source, target);
        target.setParams(new HashMap<>());
        target.setTemplates(businessPlanTemplates(source.getTemplates()));
        return target;
    }

    public static List<OaSignPlan> businessPlans(List<OaSignPlan> source)
    {
        List<OaSignPlan> target = new ArrayList<>();
        if (source == null)
        {
            return target;
        }
        for (OaSignPlan plan : source)
        {
            target.add(businessPlan(plan));
        }
        return target;
    }

    private static List<OaSignPlanTemplate> businessPlanTemplates(List<OaSignPlanTemplate> source)
    {
        List<OaSignPlanTemplate> target = new ArrayList<>();
        if (source == null)
        {
            return target;
        }
        for (OaSignPlanTemplate binding : source)
        {
            if (binding == null)
            {
                target.add(null);
                continue;
            }
            OaSignPlanTemplate copy = new OaSignPlanTemplate();
            BeanUtils.copyProperties(binding, copy, "template", "params");
            copy.setParams(new HashMap<>());
            copy.setTemplate(businessTemplate(binding.getTemplate()));
            target.add(copy);
        }
        return target;
    }

    static OaSignTaskDetail businessTaskDetail(OaSignTaskDetail source)
    {
        if (source == null)
        {
            return null;
        }
        OaSignTaskDetail target = new OaSignTaskDetail();
        target.setTask(businessTask(source.getTask()));
        target.setSignPackage(businessPackage(source.getSignPackage()));
        target.setEvents(businessTaskEvents(source.getEvents()));
        target.setDocumentVersion(source.getDocumentVersion());
        target.setSnapshotHash(null);
        target.setConfirmationToken(source.getConfirmationToken());
        target.setConfirmationCurrent(source.getConfirmationCurrent());
        target.setHasConfirmation(source.getHasConfirmation());
        target.setBusinessActionsAllowed(source.getBusinessActionsAllowed());
        target.setTechnicalEvidenceView(source.getTechnicalEvidenceView());
        target.setLatestNotificationStatus(source.getLatestNotificationStatus());
        target.setLatestNotificationChannel(source.getLatestNotificationChannel());
        target.setLatestNotificationRetryCount(source.getLatestNotificationRetryCount());
        target.setLatestNotificationTime(source.getLatestNotificationTime());
        target.setLatestNotificationNextRetryTime(source.getLatestNotificationNextRetryTime());
        target.setLatestNotificationBusinessKey(source.getLatestNotificationBusinessKey());
        return target;
    }

    static OaSignPackage businessPackage(OaSignPackage source)
    {
        if (source == null)
        {
            return null;
        }
        OaSignPackage target = new OaSignPackage();
        BeanUtils.copyProperties(source, target, "documents", "events", "params");
        target.setParams(new HashMap<>());
        target.setSealImageHashSnapshot(null);
        target.setDocuments(businessDocuments(source.getDocuments()));
        target.setEvents(businessPackageEvents(source.getEvents()));
        return target;
    }

    /**
     * Strict mobile allowlist.  File URLs, seal/signature storage paths, internal
     * entity identifiers and technical evidence hashes must only be resolved by
     * authenticated download/preview endpoints.
     */
    public static OaSignPackage employeePackage(OaSignPackage source)
    {
        if (source == null)
        {
            return null;
        }
        OaSignPackage target = new OaSignPackage();
        target.setPackageId(source.getPackageId());
        target.setPackageNo(source.getPackageNo());
        target.setEmployeeNameSnapshot(source.getEmployeeNameSnapshot());
        target.setScenario(source.getScenario());
        target.setEmploymentType(source.getEmploymentType());
        target.setSocialType(source.getSocialType());
        target.setServicePersonType(source.getServicePersonType());
        target.setInsuranceType(source.getInsuranceType());
        target.setPostNameSnapshot(source.getPostNameSnapshot());
        target.setPostLevelSnapshot(source.getPostLevelSnapshot());
        target.setSalaryVersion(source.getSalaryVersion());
        target.setEntryDate(source.getEntryDate());
        boolean preparedSignatureFirstFinal = isSignatureFirstPreparedNotSent(source);
        target.setLegalEntityNameSnapshot(preparedSignatureFirstFinal
                ? null : source.getLegalEntityNameSnapshot());
        target.setSealNameSnapshot(preparedSignatureFirstFinal
                ? null : source.getSealNameSnapshot());
        target.setStatus(source.getStatus());
        target.setSentTime(source.getSentTime());
        target.setViewedTime(source.getViewedTime());
        target.setSignedTime(source.getSignedTime());
        target.setInitialSignedTime(source.getInitialSignedTime());
        target.setSigningSequence(source.getSigningSequence());
        target.setSignatureSampleTime(source.getSignatureSampleTime());
        target.setDocumentVersion(preparedSignatureFirstFinal ? null : source.getDocumentVersion());
        target.setFinalDocumentVersion(preparedSignatureFirstFinal
                ? null : source.getFinalDocumentVersion());
        target.setFinalDocumentRootHash(preparedSignatureFirstFinal
                ? null : source.getFinalDocumentRootHash());
        target.setFinalConfirmedTime(source.getFinalConfirmedTime());
        target.setFinalConfirmationStatus(preparedSignatureFirstFinal
                ? "WAITING_COMPANY" : source.getFinalConfirmationStatus());
        target.setSignDeadline(source.getSignDeadline());
        target.setDeadlinePolicySource(source.getDeadlinePolicySource());
        target.setDeadlineDaysSnapshot(source.getDeadlineDaysSnapshot());
        target.setTerminalTime(source.getTerminalTime());
        target.setTerminalReasonCode(source.getTerminalReasonCode());
        target.setTerminalReasonDetail(source.getTerminalReasonDetail());
        target.setResolutionStatus(source.getResolutionStatus());
        target.setDocumentCount(source.getDocumentCount());
        target.setDocuments(preparedSignatureFirstFinal
                ? List.of() : employeeDocuments(source.getDocuments()));
        target.setEvents(null);
        target.setParams(new HashMap<>());
        return target;
    }

    static boolean isSignatureFirstPreparedNotSent(OaSignPackage signPackage)
    {
        return signPackage != null
                && OaSignPackageStatus.PENDING_COMPANY.equals(signPackage.getStatus())
                && OaSignSigningSequence.SIGNATURE_FIRST.equals(signPackage.getSigningSequence())
                && "PREPARED_NOT_SENT".equals(signPackage.getFinalConfirmationStatus());
    }

    public static List<OaSignPackage> employeePackages(List<OaSignPackage> source)
    {
        List<OaSignPackage> target = new ArrayList<>();
        if (source == null)
        {
            return target;
        }
        for (OaSignPackage signPackage : source)
        {
            target.add(employeePackage(signPackage));
        }
        return target;
    }

    private static List<OaSignPackageDocument> employeeDocuments(List<OaSignPackageDocument> source)
    {
        if (source == null)
        {
            return null;
        }
        List<OaSignPackageDocument> target = new ArrayList<>();
        for (OaSignPackageDocument document : source)
        {
            OaSignPackageDocument copy = new OaSignPackageDocument();
            copy.setDocumentId(document.getDocumentId());
            copy.setTemplateType(document.getTemplateType());
            copy.setDocumentName(document.getDocumentName());
            copy.setReviewPdfHash(document.getReviewPdfHash());
            copy.setFinalPdfHash(document.getFinalPdfHash());
            copy.setDocumentVersion(document.getDocumentVersion());
            copy.setFinalDocumentVersion(document.getFinalDocumentVersion());
            copy.setFinalReadConfirmed(document.getFinalReadConfirmed());
            copy.setFinalReadConfirmedTime(document.getFinalReadConfirmedTime());
            copy.setEmployeeVisible(document.getEmployeeVisible());
            copy.setReadConfirmationRequired(document.getReadConfirmationRequired());
            copy.setEmployeeSignRequired(document.getEmployeeSignRequired());
            copy.setReadConfirmed(document.getReadConfirmed());
            copy.setSigned(document.getSigned());
            copy.setSignedFileAvailable(hasText(document.getSignedPdfUrl()));
            copy.setCertificateAvailable(hasText(document.getCertificateFileUrl()));
            copy.setFinalFileAvailable(hasText(document.getFinalArchivePdfUrl()));
            copy.setSortOrder(document.getSortOrder());
            copy.setStatus(document.getStatus());
            copy.setParams(new HashMap<>());
            target.add(copy);
        }
        return target;
    }

    private static boolean hasText(String value)
    {
        return value != null && !value.isBlank();
    }

    static List<OaSignTask> businessTasks(List<OaSignTask> source)
    {
        List<OaSignTask> target = new ArrayList<>();
        if (source == null)
        {
            return target;
        }
        for (OaSignTask task : source)
        {
            target.add(businessTask(task));
        }
        return target;
    }

    private static OaSignTask businessTask(OaSignTask source)
    {
        if (source == null)
        {
            return null;
        }
        OaSignTask target = new OaSignTask();
        BeanUtils.copyProperties(source, target, "params");
        target.setParams(new HashMap<>());
        target.setConfirmedSnapshotHash(null);
        return target;
    }

    private static List<OaSignTaskEvent> businessTaskEvents(List<OaSignTaskEvent> source)
    {
        List<OaSignTaskEvent> target = new ArrayList<>();
        if (source == null)
        {
            return target;
        }
        for (OaSignTaskEvent event : source)
        {
            OaSignTaskEvent copy = new OaSignTaskEvent();
            BeanUtils.copyProperties(event, copy, "params");
            copy.setParams(new HashMap<>());
            copy.setPrevEventHash(null);
            copy.setEventHash(null);
            copy.setRequestId(null);
            copy.setIpAddress(null);
            copy.setUserAgent(null);
            target.add(copy);
        }
        return target;
    }

    private static List<OaSignPackageDocument> businessDocuments(List<OaSignPackageDocument> source)
    {
        if (source == null)
        {
            return null;
        }
        List<OaSignPackageDocument> target = new ArrayList<>();
        for (OaSignPackageDocument document : source)
        {
            OaSignPackageDocument copy = new OaSignPackageDocument();
            BeanUtils.copyProperties(document, copy, "params");
            copy.setParams(new HashMap<>());
            copy.setFileHashBeforeSign(null);
            copy.setFileHashAfterSign(null);
            copy.setReviewPdfHash(null);
            copy.setSignedPdfHash(null);
            copy.setSignatureHash(null);
            copy.setCertificateHash(null);
            copy.setFinalPdfHash(null);
            target.add(copy);
        }
        return target;
    }

    private static List<OaSignEvent> businessPackageEvents(List<OaSignEvent> source)
    {
        if (source == null)
        {
            return null;
        }
        List<OaSignEvent> target = new ArrayList<>();
        for (OaSignEvent event : source)
        {
            OaSignEvent copy = new OaSignEvent();
            BeanUtils.copyProperties(event, copy, "params");
            copy.setParams(new HashMap<>());
            copy.setDocumentHash(null);
            copy.setPrevEventHash(null);
            copy.setEventHash(null);
            copy.setEventPayload(null);
            copy.setRequestId(null);
            copy.setIpAddress(null);
            copy.setUserAgent(null);
            target.add(copy);
        }
        return target;
    }

    private static void copyBusinessAudit(BaseEntity source, BaseEntity target)
    {
        target.setCreateBy(source.getCreateBy());
        target.setCreateTime(source.getCreateTime());
        target.setUpdateBy(source.getUpdateBy());
        target.setUpdateTime(source.getUpdateTime());
        target.setRemark(source.getRemark());
    }
}
