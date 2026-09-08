package com.erp.system.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import com.erp.common.core.exception.ServiceException;
import com.erp.system.api.domain.ReviewedSignProfileSupplement;
import com.erp.system.api.domain.ReviewedSignProfileSupplementResult;
import com.erp.system.api.domain.SysUserProfile;
import com.erp.system.domain.SysSignProfileSupplementAudit;
import com.erp.system.mapper.SysSignProfileSupplementAuditMapper;
import com.erp.system.mapper.SysUserProfileMapper;
import com.erp.system.service.support.SigningProfileFactsHash;

@ExtendWith(MockitoExtension.class)
class SysSigningProfileSupplementServiceImplTest
{
    @Mock
    private SysSignProfileSupplementAuditMapper auditMapper;

    @Mock
    private SysUserProfileMapper profileMapper;

    private SysSigningProfileSupplementServiceImpl service;

    @BeforeEach
    void setUp()
    {
        service = new SysSigningProfileSupplementServiceImpl(auditMapper, profileMapper);
    }

    @Test
    void approvedFactsUseCasWhitelistAndPrivacySafeResult()
    {
        AtomicReference<SysSignProfileSupplementAudit> stored = pendingAudit();
        SysUserProfile before = profile(7L, "旧地址", null, null, null, null);
        SysUserProfile after = profile(7L, "新地址", "NON_STUDENT", null,
                "NOT_RETIRED", "2024-06");
        allowExistingProfile(before, after);
        when(profileMapper.updateReviewedSigningFacts(any(), any())).thenReturn(1);
        completeAudit(stored);

        ReviewedSignProfileSupplement request = request(before);
        request.setExpectedProfileHash(request.getExpectedProfileHash().toUpperCase());
        request.setCurrentAddress("  新地址  ");
        request.setStudentStatus("non_student");
        request.setRetirementStatus("not_retired");
        request.setIncomeStartYearMonth("2024-06");

        ReviewedSignProfileSupplementResult result = service.supplement(request);

        assertThat(result.isApplied()).isTrue();
        assertThat(result.isReplayed()).isFalse();
        assertThat(result.getBeforeHash()).isEqualTo(SigningProfileFactsHash.of(before));
        assertThat(result.getAfterHash()).isEqualTo(SigningProfileFactsHash.of(after));
        assertThat(result.getFieldMask()).isEqualTo(
                "currentAddress,studentStatus,schoolName,incomeStartYearMonth,retirementStatus");
        ArgumentCaptor<ReviewedSignProfileSupplement> update =
                ArgumentCaptor.forClass(ReviewedSignProfileSupplement.class);
        verify(profileMapper).updateReviewedSigningFacts(update.capture(),
                org.mockito.ArgumentMatchers.eq("OA_SIGN_DATA_REVIEW"));
        assertThat(update.getValue().getExpectedProfileHash()).isLowerCase();
        assertThat(update.getValue().getCurrentAddress()).isEqualTo("新地址");
        assertThat(update.getValue().getStudentStatus()).isEqualTo("NON_STUDENT");
        assertThat(update.getValue().getRetirementStatus()).isEqualTo("NOT_RETIRED");
        assertThat(stored.get().getRequestHash()).hasSize(64).doesNotContain("新地址");
        verify(profileMapper, never()).insertUserProfileIfAbsent(anyLong(), any());
        verify(profileMapper, never()).updateUserProfile(any());
        assertClosedContracts();
    }

    @Test
    void completedRequestReturnsReplayWithoutASecondProfileWrite()
    {
        AtomicReference<SysSignProfileSupplementAudit> stored = pendingAudit();
        SysUserProfile before = profile(7L, null, null, null, null, null);
        SysUserProfile after = profile(7L, null, null, null, "RETIRED", null);
        allowExistingProfile(before, after);
        when(profileMapper.updateReviewedSigningFacts(any(), any())).thenReturn(1);
        completeAudit(stored);
        ReviewedSignProfileSupplement request = request(before);
        request.setRetirementStatus("RETIRED");

        ReviewedSignProfileSupplementResult first = service.supplement(request);
        ReviewedSignProfileSupplementResult replay = service.supplement(request);

        assertThat(first.isApplied()).isTrue();
        assertThat(replay.isApplied()).isFalse();
        assertThat(replay.isReplayed()).isTrue();
        assertThat(replay.getBeforeHash()).isEqualTo(first.getBeforeHash());
        assertThat(replay.getAfterHash()).isEqualTo(first.getAfterHash());
        assertThat(replay.getFieldMask()).isEqualTo("retirementStatus");
        verify(profileMapper, times(1)).updateReviewedSigningFacts(any(), any());
        verify(auditMapper, times(1)).lockActiveEmployee(7L);
        verify(auditMapper, times(2)).insertIfAbsent(any());
    }

    @Test
    void staleExpectedHashFailsBeforeProfileWrite()
    {
        pendingAudit();
        SysUserProfile current = profile(7L, "更新后地址", null, null, null, null);
        allowExistingProfile(current);
        ReviewedSignProfileSupplement request = request(profile(7L, "旧地址", null, null, null, null));
        request.setCurrentAddress("任务中地址");

        assertThatThrownBy(() -> service.supplement(request))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("已更新");

        verify(profileMapper, never()).updateReviewedSigningFacts(any(), any());
        verify(auditMapper, never()).markCompleted(anyLong(), any(), any());
    }

    @Test
    void incomeMonthIsRejectedWhenEffectiveProfileIsStudent()
    {
        pendingAudit();
        SysUserProfile before = profile(7L, null, "STUDENT", "测试大学", null, null);
        allowExistingProfile(before);
        ReviewedSignProfileSupplement request = request(before);
        request.setIncomeStartYearMonth("2024-01");

        assertThatThrownBy(() -> service.supplement(request))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("主要劳动收入起始月");

        verify(profileMapper, never()).updateReviewedSigningFacts(any(), any());
    }

    @Test
    void studentTransitionUsesExistingSchoolAndClearsExistingIncomeMonth()
    {
        AtomicReference<SysSignProfileSupplementAudit> stored = pendingAudit();
        SysUserProfile before = profile(7L, null, "NON_STUDENT", "历史异常学校", null, "2024-01");
        SysUserProfile after = profile(7L, null, "STUDENT", "历史异常学校", null, null);
        allowExistingProfile(before, after);
        when(profileMapper.updateReviewedSigningFacts(any(), any())).thenReturn(1);
        completeAudit(stored);
        ReviewedSignProfileSupplement request = request(before);
        request.setStudentStatus("STUDENT");

        ReviewedSignProfileSupplementResult result = service.supplement(request);

        assertThat(result.isApplied()).isTrue();
        assertThat(result.getFieldMask()).isEqualTo("studentStatus,incomeStartYearMonth");
    }

    @Test
    void nonStudentTransitionClearsSchoolAndReportsTheImplicitWrite()
    {
        AtomicReference<SysSignProfileSupplementAudit> stored = pendingAudit();
        SysUserProfile before = profile(7L, null, "STUDENT", "测试大学", null, null);
        SysUserProfile after = profile(7L, null, "NON_STUDENT", null, null, null);
        allowExistingProfile(before, after);
        when(profileMapper.updateReviewedSigningFacts(any(), any())).thenReturn(1);
        completeAudit(stored);
        ReviewedSignProfileSupplement request = request(before);
        request.setStudentStatus("NON_STUDENT");

        ReviewedSignProfileSupplementResult result = service.supplement(request);

        assertThat(result.isApplied()).isTrue();
        assertThat(result.getFieldMask()).isEqualTo("studentStatus,schoolName");
    }

    @Test
    void studentWithoutEffectiveSchoolIsRejected()
    {
        pendingAudit();
        SysUserProfile before = profile(7L, null, null, null, null, null);
        allowExistingProfile(before);
        ReviewedSignProfileSupplement request = request(before);
        request.setStudentStatus("STUDENT");

        assertThatThrownBy(() -> service.supplement(request))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("学校");
        verify(profileMapper, never()).updateReviewedSigningFacts(any(), any());
    }

    @Test
    void missingEmployeeProfileIsRejectedWithoutCreatingOne()
    {
        pendingAudit();
        when(auditMapper.lockActiveEmployee(7L)).thenReturn(7L);
        when(profileMapper.lockSigningProfileByUserId(7L)).thenReturn(null);
        ReviewedSignProfileSupplement request = request(profile(7L, null, null, null, null, null));
        request.setCurrentAddress("新地址");

        assertThatThrownBy(() -> service.supplement(request))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("未初始化");

        verify(profileMapper, never()).insertUserProfileIfAbsent(anyLong(), any());
        verify(profileMapper, never()).updateReviewedSigningFacts(any(), any());
    }

    @Test
    void addressLongerThanDatabaseColumnIsRejectedBeforeAuditClaim()
    {
        ReviewedSignProfileSupplement request = request(profile(7L, null, null, null, null, null));
        request.setCurrentAddress("x".repeat(256));

        assertThatThrownBy(() -> service.supplement(request))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("长度");
        verify(auditMapper, never()).insertIfAbsent(any());
    }

    @Test
    void reusedRequestIdWithDifferentPayloadFailsBeforeTouchingEmployee()
    {
        when(auditMapper.insertIfAbsent(any())).thenReturn(0);
        SysSignProfileSupplementAudit existing = new SysSignProfileSupplementAudit();
        existing.setAuditId(9L);
        existing.setRequestId("oa-review-1");
        existing.setEmployeeId(7L);
        existing.setRequestHash("different");
        existing.setStatus("COMPLETED");
        when(auditMapper.selectByRequestIdForUpdate("oa-review-1")).thenReturn(existing);
        ReviewedSignProfileSupplement request = request(profile(7L, null, null, null, null, null));
        request.setCurrentAddress("新地址");

        assertThatThrownBy(() -> service.supplement(request))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("requestId");

        verify(auditMapper, never()).lockActiveEmployee(anyLong());
        verify(profileMapper, never()).updateReviewedSigningFacts(any(), any());
    }

    @Test
    void noReviewedFactIsRejected()
    {
        ReviewedSignProfileSupplement request = request(profile(7L, null, null, null, null, null));

        assertThatThrownBy(() -> service.supplement(request))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("至少需要一项");
        verify(auditMapper, never()).insertIfAbsent(any());
    }

    private void assertClosedContracts()
    {
        assertThat(Arrays.stream(ReviewedSignProfileSupplement.class.getDeclaredFields())
                .map(java.lang.reflect.Field::getName))
                .containsExactlyInAnyOrder("requestId", "employeeId", "expectedProfileHash",
                        "currentAddress", "studentStatus", "schoolName", "retirementStatus",
                        "incomeStartYearMonth")
                .doesNotContain("servicePersonType", "insuranceType", "socialType", "contractType",
                        "salaryTotal", "jobGrade", "legalEntity");
        assertThat(Arrays.stream(ReviewedSignProfileSupplementResult.class.getDeclaredFields())
                .map(java.lang.reflect.Field::getName))
                .containsExactlyInAnyOrder("applied", "replayed", "beforeHash", "afterHash", "fieldMask");
    }

    private void allowExistingProfile(SysUserProfile... reads)
    {
        when(auditMapper.lockActiveEmployee(7L)).thenReturn(7L);
        when(profileMapper.lockSigningProfileByUserId(7L)).thenReturn(11L);
        when(profileMapper.selectUserProfileByUserId(7L)).thenReturn(reads[0],
                Arrays.copyOfRange(reads, 1, reads.length));
    }

    private void completeAudit(AtomicReference<SysSignProfileSupplementAudit> stored)
    {
        when(auditMapper.markCompleted(anyLong(), any(), any())).thenAnswer(invocation -> {
            stored.get().setBeforeHash(invocation.getArgument(1));
            stored.get().setAfterHash(invocation.getArgument(2));
            stored.get().setStatus("COMPLETED");
            return 1;
        });
    }

    private AtomicReference<SysSignProfileSupplementAudit> pendingAudit()
    {
        AtomicReference<SysSignProfileSupplementAudit> stored = new AtomicReference<>();
        when(auditMapper.insertIfAbsent(any())).thenAnswer(invocation -> {
            SysSignProfileSupplementAudit value = invocation.getArgument(0);
            if (stored.get() == null)
            {
                value.setAuditId(11L);
                value.setStatus("PENDING");
                stored.set(value);
                return 1;
            }
            return 0;
        });
        when(auditMapper.selectByRequestIdForUpdate("oa-review-1"))
                .thenAnswer(invocation -> stored.get());
        return stored;
    }

    private ReviewedSignProfileSupplement request(SysUserProfile expectedProfile)
    {
        ReviewedSignProfileSupplement request = new ReviewedSignProfileSupplement();
        request.setRequestId("oa-review-1");
        request.setEmployeeId(7L);
        request.setExpectedProfileHash(SigningProfileFactsHash.of(expectedProfile));
        return request;
    }

    private SysUserProfile profile(Long userId, String address, String studentStatus, String schoolName,
            String retirementStatus, String incomeMonth)
    {
        SysUserProfile profile = new SysUserProfile();
        profile.setUserId(userId);
        profile.setCurrentAddress(address);
        profile.setStudentStatus(studentStatus);
        profile.setSchoolName(schoolName);
        profile.setRetirementStatus(retirementStatus);
        profile.setIncomeStartYearMonth(incomeMonth);
        return profile;
    }
}
