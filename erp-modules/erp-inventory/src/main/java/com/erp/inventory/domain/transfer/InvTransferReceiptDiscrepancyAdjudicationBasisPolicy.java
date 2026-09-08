package com.erp.inventory.domain.transfer;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import com.erp.common.core.exception.ServiceException;
import com.erp.inventory.domain.transfer.InvTransferReceiptDiscrepancyAdjudicationPolicy.Actor;
import com.erp.inventory.domain.transfer.InvTransferReceiptDiscrepancyBoundaryPolicy.Resolved;
import com.erp.inventory.domain.transfer.InvTransferReceiptDiscrepancyConfirmationProjection.Snapshot;

/** Pure issue and verification rules for an opaque adjudication basis token. */
public final class InvTransferReceiptDiscrepancyAdjudicationBasisPolicy
{
    public static final String ISSUED_STATUS = "issued";
    public static final String CONSUMED_STATUS = "consumed";
    public static final Duration TOKEN_TTL = Duration.ofMinutes(5);

    private static final String PURPOSE =
            "INV_TRANSFER_DISCREPANCY_ADJUDICATION_BASIS_V1";
    private static final String TOKEN_PREFIX = "adjb_v1_";
    private static final int TOKEN_ENTROPY_BYTES = 32;
    private static final int MAX_SCOPE_DEPARTMENTS = 10000;
    private static final Set<String> STATES = Set.of(ISSUED_STATUS,
            CONSUMED_STATUS);

    private InvTransferReceiptDiscrepancyAdjudicationBasisPolicy()
    {
    }

    public static IssuedBasis issue(Resolved resolved, Actor actor,
            Long selectedShopDeptId, List<Long> scopeDeptIds,
            Instant issuedAt, byte[] entropy)
    {
        List<Long> scope = requireContext(resolved, actor,
                selectedShopDeptId, scopeDeptIds);
        if (issuedAt == null || entropy == null
                || entropy.length != TOKEN_ENTROPY_BYTES)
        {
            throw invalidIssue();
        }
        Instant expiresAt;
        try
        {
            expiresAt = issuedAt.plus(TOKEN_TTL);
        }
        catch (RuntimeException invalid)
        {
            throw invalidIssue();
        }
        String token = TOKEN_PREFIX + Base64.getUrlEncoder()
                .withoutPadding().encodeToString(entropy.clone());
        InvTransferReceiptDiscrepancyReadFact fact = resolved.fact();
        Snapshot source = resolved.source();
        Snapshot target = resolved.target();
        PreparedBasis prepared = new PreparedBasis(tokenHash(token),
                fact.getDiscrepancyCaseId(), fact.getCaseVersion(),
                fact.getFactFingerprint(), source.eventId(), target.eventId(),
                selectedShopDeptId,
                scopeDigest(selectedShopDeptId, scope),
                InvTransferReceiptDiscrepancyAdjudicationPolicy
                        .REQUIRED_PERMISSION,
                actor.userId(), actor.userName().trim(), issuedAt, expiresAt,
                ISSUED_STATUS, scope);
        return new IssuedBasis(token, prepared);
    }

    /**
     * Verifies an issued server row without changing it. The caller that later
     * consumes the token must lock and mark that row in the adjudication write
     * transaction.
     */
    public static VerifiedBasis verifyIssued(String token,
            InvTransferReceiptDiscrepancyStoredAdjudicationBasis stored,
            Resolved resolved, Actor actor, Long selectedShopDeptId,
            List<Long> scopeDeptIds, Instant now)
    {
        try
        {
            List<Long> scope = requireContext(resolved, actor,
                    selectedShopDeptId, scopeDeptIds);
            requireToken(token);
            if (stored == null || now == null
                    || !positive(stored.getBasisId())
                    || !hash(stored.getTokenHash())
                    || !positive(stored.getCaseId())
                    || stored.getCaseVersion() == null
                    || stored.getCaseVersion() < 0
                    || !hash(stored.getFactFingerprint())
                    || !positive(stored.getSourceConfirmationEventId())
                    || !positive(stored.getTargetConfirmationEventId())
                    || Objects.equals(stored.getSourceConfirmationEventId(),
                            stored.getTargetConfirmationEventId())
                    || !positive(stored.getSelectedShopDeptId())
                    || !hash(stored.getScopeDigest())
                    || !Objects.equals(stored.getRequiredPermission(),
                            InvTransferReceiptDiscrepancyAdjudicationPolicy
                                    .REQUIRED_PERMISSION)
                    || !positive(stored.getAdjudicatorUserId())
                    || blank(stored.getAdjudicatorName())
                    || stored.getAdjudicatorName().trim().length() > 64
                    || !STATES.contains(stored.getBasisStatus())
                    || !ISSUED_STATUS.equals(stored.getBasisStatus())
                    || stored.getIssuedAt() == null
                    || stored.getExpiresAt() == null
                    || stored.getConsumedAt() != null
                    || stored.getConsumedRequestId() != null
                    || stored.getConsumedAdjudicationId() != null)
            {
                throw invalidToken();
            }
            Instant issuedAt = stored.getIssuedAt().toInstant();
            Instant expiresAt = stored.getExpiresAt().toInstant();
            if (!expiresAt.equals(issuedAt.plus(TOKEN_TTL))
                    || now.isBefore(issuedAt)
                    || !now.isBefore(expiresAt))
            {
                throw invalidToken();
            }
            InvTransferReceiptDiscrepancyReadFact fact = resolved.fact();
            Snapshot source = resolved.source();
            Snapshot target = resolved.target();
            if (!constantEquals(stored.getTokenHash(), tokenHash(token))
                    || !Objects.equals(stored.getCaseId(),
                            fact.getDiscrepancyCaseId())
                    || !Objects.equals(stored.getCaseVersion(),
                            fact.getCaseVersion())
                    || !constantEquals(stored.getFactFingerprint(),
                            fact.getFactFingerprint())
                    || !Objects.equals(
                            stored.getSourceConfirmationEventId(),
                            source.eventId())
                    || !Objects.equals(
                            stored.getTargetConfirmationEventId(),
                            target.eventId())
                    || !Objects.equals(stored.getSelectedShopDeptId(),
                            selectedShopDeptId)
                    || !constantEquals(stored.getScopeDigest(),
                            scopeDigest(selectedShopDeptId, scope))
                    || !Objects.equals(stored.getAdjudicatorUserId(),
                            actor.userId())
                    || !Objects.equals(stored.getAdjudicatorName().trim(),
                            actor.userName().trim()))
            {
                throw invalidToken();
            }
            return verified(stored, issuedAt, expiresAt);
        }
        catch (ServiceException invalid)
        {
            throw invalidToken();
        }
        catch (RuntimeException invalid)
        {
            throw invalidToken();
        }
    }

    /** Verifies that a consumed basis belongs to one exact stored replay. */
    public static VerifiedBasis verifyConsumedReplay(String token,
            InvTransferReceiptDiscrepancyStoredAdjudicationBasis stored,
            InvTransferReceiptDiscrepancyReadFact fact,
            InvTransferReceiptDiscrepancyStoredAdjudication adjudication,
            Actor actor, Long selectedShopDeptId, List<Long> scopeDeptIds,
            String requestId)
    {
        try
        {
            requireToken(token);
            List<Long> scope = normalizedScope(scopeDeptIds);
            if (stored == null || fact == null || adjudication == null
                    || actor == null || !positive(actor.userId())
                    || blank(actor.userName())
                    || actor.userName().trim().length() > 64
                    || actor.permissions() == null
                    || !actor.permissions().contains(
                            InvTransferReceiptDiscrepancyAdjudicationPolicy
                                    .REQUIRED_PERMISSION)
                    || !positive(selectedShopDeptId)
                    || !scope.contains(selectedShopDeptId)
                    || (!scope.contains(fact.getSourceDeptId())
                            && !scope.contains(fact.getTargetDeptId()))
                    || !requestId(requestId)
                    || !positive(stored.getBasisId())
                    || !hash(stored.getTokenHash())
                    || !positive(stored.getCaseId())
                    || stored.getCaseVersion() == null
                    || stored.getCaseVersion() < 0
                    || !hash(stored.getFactFingerprint())
                    || !positive(stored.getSourceConfirmationEventId())
                    || !positive(stored.getTargetConfirmationEventId())
                    || Objects.equals(stored.getSourceConfirmationEventId(),
                            stored.getTargetConfirmationEventId())
                    || !positive(stored.getSelectedShopDeptId())
                    || !hash(stored.getScopeDigest())
                    || !Objects.equals(stored.getRequiredPermission(),
                            InvTransferReceiptDiscrepancyAdjudicationPolicy
                                    .REQUIRED_PERMISSION)
                    || !positive(stored.getAdjudicatorUserId())
                    || blank(stored.getAdjudicatorName())
                    || !CONSUMED_STATUS.equals(stored.getBasisStatus())
                    || stored.getIssuedAt() == null
                    || stored.getExpiresAt() == null
                    || stored.getConsumedAt() == null
                    || !requestId(stored.getConsumedRequestId())
                    || !positive(stored.getConsumedAdjudicationId())
                    || !positive(adjudication.getAdjudicationId()))
            {
                throw invalidToken();
            }
            Instant issuedAt = stored.getIssuedAt().toInstant();
            Instant expiresAt = stored.getExpiresAt().toInstant();
            Instant consumedAt = stored.getConsumedAt().toInstant();
            InvTransferReceiptDiscrepancyReadFact.Confirmation source =
                    fact.getSourceConfirmation();
            InvTransferReceiptDiscrepancyReadFact.Confirmation target =
                    fact.getTargetConfirmation();
            if (!expiresAt.equals(issuedAt.plus(TOKEN_TTL))
                    || consumedAt.isBefore(issuedAt)
                    || !consumedAt.isBefore(expiresAt)
                    || !constantEquals(stored.getTokenHash(),
                            tokenHash(token))
                    || !Objects.equals(stored.getCaseId(),
                            fact.getDiscrepancyCaseId())
                    || !Objects.equals(stored.getCaseId(),
                            adjudication.getCaseId())
                    || !Objects.equals(stored.getCaseVersion(),
                            adjudication.getCaseVersionBefore())
                    || !constantEquals(stored.getFactFingerprint(),
                            fact.getFactFingerprint())
                    || !constantEquals(stored.getFactFingerprint(),
                            adjudication.getFactFingerprint())
                    || source == null || target == null
                    || !Objects.equals(
                            stored.getSourceConfirmationEventId(),
                            source.getEventId())
                    || !Objects.equals(
                            stored.getSourceConfirmationEventId(),
                            adjudication.getSourceConfirmationEventId())
                    || !Objects.equals(
                            stored.getTargetConfirmationEventId(),
                            target.getEventId())
                    || !Objects.equals(
                            stored.getTargetConfirmationEventId(),
                            adjudication.getTargetConfirmationEventId())
                    || !Objects.equals(stored.getSelectedShopDeptId(),
                            selectedShopDeptId)
                    || !constantEquals(stored.getScopeDigest(),
                            scopeDigest(selectedShopDeptId, scope))
                    || !Objects.equals(stored.getAdjudicatorUserId(),
                            actor.userId())
                    || !Objects.equals(stored.getAdjudicatorUserId(),
                            adjudication.getAdjudicatorUserId())
                    || !Objects.equals(stored.getAdjudicatorName().trim(),
                            actor.userName().trim())
                    || !Objects.equals(stored.getAdjudicatorName().trim(),
                            adjudication.getAdjudicatorName().trim())
                    || !Objects.equals(stored.getConsumedRequestId(),
                            requestId)
                    || !Objects.equals(stored.getConsumedRequestId(),
                            adjudication.getRequestId())
                    || !Objects.equals(
                            stored.getConsumedAdjudicationId(),
                            adjudication.getAdjudicationId()))
            {
                throw invalidToken();
            }
            return verified(stored, issuedAt, expiresAt);
        }
        catch (ServiceException invalid)
        {
            throw invalidToken();
        }
        catch (RuntimeException invalid)
        {
            throw invalidToken();
        }
    }

    public static PreparedConsumption prepareConsumption(
            VerifiedBasis verified, String requestId, Long adjudicationId,
            Instant consumedAt, Actor actor)
    {
        try
        {
            if (verified == null || actor == null
                    || !positive(verified.basisId())
                    || !hash(verified.tokenHash())
                    || !positive(verified.caseId())
                    || verified.caseVersion() == null
                    || verified.caseVersion() < 0
                    || !hash(verified.factFingerprint())
                    || !positive(verified.sourceConfirmationEventId())
                    || !positive(verified.targetConfirmationEventId())
                    || Objects.equals(
                            verified.sourceConfirmationEventId(),
                            verified.targetConfirmationEventId())
                    || !positive(verified.selectedShopDeptId())
                    || !hash(verified.scopeDigest())
                    || !Objects.equals(verified.requiredPermission(),
                            InvTransferReceiptDiscrepancyAdjudicationPolicy
                                    .REQUIRED_PERMISSION)
                    || !positive(verified.adjudicatorUserId())
                    || blank(verified.adjudicatorName())
                    || verified.adjudicatorName().length() > 64
                    || verified.issuedAt() == null
                    || verified.expiresAt() == null
                    || !verified.expiresAt().equals(
                            verified.issuedAt().plus(TOKEN_TTL))
                    || !requestId(requestId)
                    || !positive(adjudicationId)
                    || consumedAt == null || !positive(actor.userId())
                    || blank(actor.userName())
                    || actor.permissions() == null
                    || !actor.permissions().contains(
                            verified.requiredPermission())
                    || !Objects.equals(verified.adjudicatorUserId(),
                            actor.userId())
                    || !Objects.equals(verified.adjudicatorName(),
                            actor.userName().trim())
                    || consumedAt.isBefore(verified.issuedAt())
                    || !consumedAt.isBefore(verified.expiresAt()))
            {
                throw invalidConsumption();
            }
            return new PreparedConsumption(verified.basisId(),
                    verified.tokenHash(), verified.caseId(),
                    verified.caseVersion(), verified.factFingerprint(),
                    verified.sourceConfirmationEventId(),
                    verified.targetConfirmationEventId(),
                    verified.selectedShopDeptId(), verified.scopeDigest(),
                    verified.requiredPermission(),
                    verified.adjudicatorUserId(),
                    verified.adjudicatorName(), verified.issuedAt(),
                    verified.expiresAt(), requestId, adjudicationId,
                    consumedAt);
        }
        catch (ServiceException invalid)
        {
            throw invalidConsumption();
        }
        catch (RuntimeException invalid)
        {
            throw invalidConsumption();
        }
    }

    public static String tokenHash(String token)
    {
        requireToken(token);
        MessageDigest digest = sha256();
        update(digest, PURPOSE);
        update(digest, token);
        return HexFormat.of().formatHex(digest.digest());
    }

    private static VerifiedBasis verified(
            InvTransferReceiptDiscrepancyStoredAdjudicationBasis stored,
            Instant issuedAt, Instant expiresAt)
    {
        return new VerifiedBasis(stored.getBasisId(), stored.getTokenHash(),
                stored.getCaseId(), stored.getCaseVersion(),
                stored.getFactFingerprint(),
                stored.getSourceConfirmationEventId(),
                stored.getTargetConfirmationEventId(),
                stored.getSelectedShopDeptId(), stored.getScopeDigest(),
                stored.getRequiredPermission(),
                stored.getAdjudicatorUserId(),
                stored.getAdjudicatorName().trim(), issuedAt, expiresAt);
    }

    private static List<Long> requireContext(Resolved resolved, Actor actor,
            Long selectedShopDeptId, List<Long> scopeDeptIds)
    {
        if (resolved == null || resolved.fact() == null
                || resolved.boundary() == null
                || resolved.projection() == null
                || !resolved.projection().readyForAdjudication()
                || actor == null || !positive(actor.userId())
                || blank(actor.userName())
                || actor.userName().trim().length() > 64
                || actor.permissions() == null
                || !actor.permissions().contains(
                        InvTransferReceiptDiscrepancyAdjudicationPolicy
                                .REQUIRED_PERMISSION)
                || !positive(selectedShopDeptId))
        {
            throw invalidIssue();
        }
        Snapshot source = resolved.source();
        Snapshot target = resolved.target();
        if (source == null || target == null
                || !resolved.projection().source().currentFact()
                || !resolved.projection().target().currentFact()
                || !"confirmed".equals(source.decision())
                || !"confirmed".equals(target.decision())
                || Objects.equals(actor.userId(), source.operatorUserId())
                || Objects.equals(actor.userId(), target.operatorUserId()))
        {
            throw invalidIssue();
        }
        List<Long> scope = normalizedScope(scopeDeptIds);
        InvTransferReceiptDiscrepancyReadFact fact = resolved.fact();
        if (!scope.contains(selectedShopDeptId)
                || !scope.contains(fact.getSourceDeptId())
                        && !scope.contains(fact.getTargetDeptId()))
        {
            throw invalidIssue();
        }
        return scope;
    }

    private static List<Long> normalizedScope(List<Long> values)
    {
        if (values == null || values.isEmpty()
                || values.size() > MAX_SCOPE_DEPARTMENTS)
        {
            throw invalidIssue();
        }
        TreeSet<Long> unique = new TreeSet<>();
        for (Long value : values)
        {
            if (!positive(value))
            {
                throw invalidIssue();
            }
            unique.add(value);
        }
        if (unique.isEmpty())
        {
            throw invalidIssue();
        }
        return List.copyOf(unique);
    }

    private static String scopeDigest(Long selectedShopDeptId,
            List<Long> scopeDeptIds)
    {
        MessageDigest digest = sha256();
        update(digest, PURPOSE);
        update(digest, selectedShopDeptId);
        for (Long scopeDeptId : scopeDeptIds)
        {
            update(digest, scopeDeptId);
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    private static void requireToken(String value)
    {
        if (value == null || !value.matches(
                "adjb_v1_[A-Za-z0-9_-]{43}"))
        {
            throw invalidToken();
        }
    }

    private static MessageDigest sha256()
    {
        try
        {
            return MessageDigest.getInstance("SHA-256");
        }
        catch (NoSuchAlgorithmException impossible)
        {
            throw new IllegalStateException("SHA-256 unavailable",
                    impossible);
        }
    }

    private static void update(MessageDigest digest, Object value)
    {
        byte[] bytes = String.valueOf(value)
                .getBytes(StandardCharsets.UTF_8);
        digest.update(ByteBuffer.allocate(Integer.BYTES)
                .putInt(bytes.length).array());
        digest.update(bytes);
    }

    private static boolean constantEquals(String left, String right)
    {
        if (left == null || right == null)
        {
            return false;
        }
        return MessageDigest.isEqual(
                left.getBytes(StandardCharsets.US_ASCII),
                right.getBytes(StandardCharsets.US_ASCII));
    }

    private static boolean hash(String value)
    {
        return value != null && value.matches("[a-f0-9]{64}");
    }

    private static boolean requestId(String value)
    {
        return value != null && value.matches(
                "[A-Za-z0-9][A-Za-z0-9._:-]{7,127}");
    }

    private static boolean positive(Long value)
    {
        return value != null && value > 0;
    }

    private static boolean blank(String value)
    {
        return value == null || value.isBlank();
    }

    private static ServiceException invalidIssue()
    {
        return new ServiceException("差异裁决依据签发上下文不可核验");
    }

    private static ServiceException invalidToken()
    {
        return new ServiceException("差异裁决依据令牌无效或已过期，请重新获取");
    }

    private static ServiceException invalidConsumption()
    {
        return new ServiceException("差异裁决依据消费上下文不可核验");
    }

    public record PreparedBasis(
            String tokenHash,
            Long caseId,
            Long caseVersion,
            String factFingerprint,
            Long sourceConfirmationEventId,
            Long targetConfirmationEventId,
            Long selectedShopDeptId,
            String scopeDigest,
            String requiredPermission,
            Long adjudicatorUserId,
            String adjudicatorName,
            Instant issuedAt,
            Instant expiresAt,
            String basisStatus,
            List<Long> scopeDeptIds)
    {
        public PreparedBasis
        {
            scopeDeptIds = scopeDeptIds == null ? null
                    : List.copyOf(new ArrayList<>(scopeDeptIds));
        }

        @Override
        public String toString()
        {
            return "PreparedBasis[tokenHash=[REDACTED], caseId=" + caseId
                    + ", caseVersion=" + caseVersion
                    + ", factFingerprint=[REDACTED]"
                    + ", sourceConfirmationEventId="
                    + sourceConfirmationEventId
                    + ", targetConfirmationEventId="
                    + targetConfirmationEventId
                    + ", selectedShopDeptId=" + selectedShopDeptId
                    + ", scopeDigest=[REDACTED], requiredPermission="
                    + requiredPermission + ", adjudicatorUserId="
                    + adjudicatorUserId + ", adjudicatorName="
                    + adjudicatorName + ", issuedAt=" + issuedAt
                    + ", expiresAt=" + expiresAt + ", basisStatus="
                    + basisStatus + ", scopeDeptIds=[REDACTED]]";
        }
    }

    public record IssuedBasis(String token, PreparedBasis prepared)
    {
        @Override
        public String toString()
        {
            return "IssuedBasis[token=[REDACTED], prepared=" + prepared
                    + "]";
        }
    }

    public record VerifiedBasis(
            Long basisId,
            String tokenHash,
            Long caseId,
            Long caseVersion,
            String factFingerprint,
            Long sourceConfirmationEventId,
            Long targetConfirmationEventId,
            Long selectedShopDeptId,
            String scopeDigest,
            String requiredPermission,
            Long adjudicatorUserId,
            String adjudicatorName,
            Instant issuedAt,
            Instant expiresAt)
    {
        @Override
        public String toString()
        {
            return "VerifiedBasis[basisId=" + basisId
                    + ", tokenHash=[REDACTED], caseId=" + caseId
                    + ", caseVersion=" + caseVersion
                    + ", factFingerprint=[REDACTED]"
                    + ", sourceConfirmationEventId="
                    + sourceConfirmationEventId
                    + ", targetConfirmationEventId="
                    + targetConfirmationEventId
                    + ", selectedShopDeptId=" + selectedShopDeptId
                    + ", scopeDigest=[REDACTED], requiredPermission="
                    + requiredPermission + ", adjudicatorUserId="
                    + adjudicatorUserId + ", adjudicatorName="
                    + adjudicatorName + ", issuedAt=" + issuedAt
                    + ", expiresAt=" + expiresAt + "]";
        }
    }

    public record PreparedConsumption(
            Long basisId,
            String tokenHash,
            Long caseId,
            Long caseVersion,
            String factFingerprint,
            Long sourceConfirmationEventId,
            Long targetConfirmationEventId,
            Long selectedShopDeptId,
            String scopeDigest,
            String requiredPermission,
            Long adjudicatorUserId,
            String adjudicatorName,
            Instant issuedAt,
            Instant expiresAt,
            String requestId,
            Long adjudicationId,
            Instant consumedAt)
    {
        @Override
        public String toString()
        {
            return "PreparedConsumption[basisId=" + basisId
                    + ", tokenHash=[REDACTED], caseId=" + caseId
                    + ", caseVersion=" + caseVersion
                    + ", factFingerprint=[REDACTED]"
                    + ", sourceConfirmationEventId="
                    + sourceConfirmationEventId
                    + ", targetConfirmationEventId="
                    + targetConfirmationEventId
                    + ", selectedShopDeptId=" + selectedShopDeptId
                    + ", scopeDigest=[REDACTED], requiredPermission="
                    + requiredPermission + ", adjudicatorUserId="
                    + adjudicatorUserId + ", adjudicatorName="
                    + adjudicatorName + ", issuedAt=" + issuedAt
                    + ", expiresAt=" + expiresAt + ", requestId="
                    + requestId + ", adjudicationId=" + adjudicationId
                    + ", consumedAt=" + consumedAt + "]";
        }
    }
}
