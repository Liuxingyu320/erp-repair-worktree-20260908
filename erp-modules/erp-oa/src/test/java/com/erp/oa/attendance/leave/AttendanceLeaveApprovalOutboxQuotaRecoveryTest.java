package com.erp.oa.attendance.leave;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.time.*;
import org.junit.jupiter.api.*;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import com.erp.approval.api.RemoteApprovalService;
import com.erp.approval.api.domain.*;
import com.erp.common.core.domain.R;
import com.erp.oa.service.BusinessFeatureGate;
import com.erp.oa.attendance.leave.AttendanceLeaveModels.ApprovalOutbox;
import com.erp.oa.attendance.leave.balance.AttendanceLeaveQuotaServiceTest.Harness;

class AttendanceLeaveApprovalOutboxQuotaRecoveryTest
{
 Harness h; ApprovalOutbox row; AttendanceLeaveApprovalOutboxService outbox;
 RemoteApprovalService remote; AttendanceLeaveApprovalDispatcher dispatcher;
 @BeforeEach void setup() throws Exception {
  TransactionSynchronizationManager.setActualTransactionActive(true);
  TransactionSynchronizationManager.setCurrentTransactionIsolationLevel(java.sql.Connection.TRANSACTION_READ_COMMITTED);
  h=new Harness();h.reserve();h.request.status="SUBMITTING";h.request.rowVersion=3L;
  row=new ApprovalOutbox();row.outboxId=70L;row.leaveRequestId=10L;row.businessRound=1;row.rowVersion=0L;row.requestRowVersion=3L;row.status="PENDING";row.attemptCount=0;row.createBy="synthetic";row.idempotencyKey="OA_ATTENDANCE_LEAVE:10:1";
  ApprovalStartRequest command=new ApprovalStartRequest();command.setBusinessCode("OA_ATTENDANCE_LEAVE");command.setBusinessId("10");command.setBusinessRound(1);command.setApplicantId(11L);command.setAnchorDeptId(20L);command.setIdempotencyKey(row.idempotencyKey);row.requestJson=h.json.writeValueAsString(command);
  when(h.leaves.selectApprovalOutboxById(70L)).thenReturn(row);when(h.leaves.selectApprovalOutboxByIdForUpdate(70L)).thenReturn(row);
  when(h.leaves.claimApprovalOutbox(eq(70L),anyString(),anyLong(),anyString())).thenReturn(1);
  when(h.leaves.markApprovalRetry(eq(70L),anyString(),anyLong(),any(),nullable(Integer.class),anyString(),anyString())).thenReturn(1);
  when(h.leaves.markApprovalFailed(eq(70L),anyString(),anyLong(),nullable(Integer.class),anyString(),anyString())).thenReturn(1);
  when(h.leaves.markApprovalRemoteSucceeded(eq(70L),anyString(),anyLong(),anyLong(),anyString(),anyInt(),anyInt())).thenReturn(1);
  when(h.leaves.finalizeLeaveApprovalStart(eq(10L),eq(1),eq(3L),anyLong(),anyString())).thenAnswer(i->{h.request.status="PENDING";h.request.approvalInstanceId=i.getArgument(3);return 1;});
  when(h.leaves.markApprovalCompleted(eq(70L),anyString(),anyLong())).thenReturn(1);
  outbox=new AttendanceLeaveApprovalOutboxService(h.leaves,h.json,h.service);remote=mock(RemoteApprovalService.class);
  BusinessFeatureGate gate=mock(BusinessFeatureGate.class);when(gate.isEnabled(BusinessFeatureGate.ATTENDANCE_V2)).thenReturn(true);
  dispatcher=new AttendanceLeaveApprovalDispatcher(outbox,remote,gate,h.json,Clock.fixed(Instant.parse("2026-09-12T00:00:00Z"),ZoneOffset.UTC));
 }
 @AfterEach void clear(){TransactionSynchronizationManager.clear();}
 long reserved(){return h.buckets.stream().mapToLong(b->b.reservedUnits).sum();}
 void run(){dispatcher.dispatchOneNow(70L);}
 void retained(String code){assertThat(row.status).isEqualTo("RETRY");assertThat(row.lastErrorCode).isEqualTo(code);assertThat(h.request.quotaStatus).isEqualTo("REVIEW");assertThat(reserved()).isEqualTo(420_000_000L);assertThat(h.allocations).hasSize(2);verify(h.mapper,never()).returnFailedSubmission(anyLong(),anyInt());}
 R<ApprovalStartResponse> success(int round){ApprovalStartResponse response=new ApprovalStartResponse();response.setInstanceId(90L);response.setBusinessRound(round);response.setStatus("RUNNING");return R.ok(response);}
 @Test void firstInvalidSnapshotProvesNoRpcAndReleases(){row.requestJson="invalid-json";run();assertThat(row.status).isEqualTo("FAILED");assertThat(reserved()).isZero();verifyNoInteractions(remote);verify(h.mapper).returnFailedSubmission(10L,1);}
 @Test void invalidSnapshotAfterPriorAttemptCannotProveAbsence(){row.requestJson="invalid-json";row.attemptCount=1;run();assertThat(row.status).isEqualTo("FAILED");assertThat(h.request.quotaStatus).isEqualTo("REVIEW");assertThat(reserved()).isEqualTo(420_000_000L);verify(h.mapper,never()).returnFailedSubmission(anyLong(),anyInt());}
 @Test void nullRemoteResultRetainsOriginalReservation(){when(remote.start(any(),anyString())).thenReturn(null);run();retained("REMOTE_UNAVAILABLE");}
 @Test void timeoutRetainsOriginalReservation(){when(remote.start(any(),anyString())).thenThrow(new RuntimeException(new java.net.SocketTimeoutException()));run();retained("REMOTE_TIMEOUT");}
 @Test void httpBusinessFailureAfterRpcCannotRelease(){when(remote.start(any(),anyString())).thenReturn(R.fail(400,"synthetic rejected response"));run();retained("REMOTE_HTTP_400");}
 @Test void successWithoutInstanceIsUnknown(){when(remote.start(any(),anyString())).thenReturn(R.ok(new ApprovalStartResponse()));run();retained("INVALID_REMOTE_RESPONSE");}
 @Test void responseFromWrongRoundIsUnknown(){when(remote.start(any(),anyString())).thenReturn(success(2));run();retained("REMOTE_ROUND_MISMATCH");}
 @Test void sameKeyRetryLinksWithoutReservingAgain(){when(remote.start(any(),anyString())).thenReturn(null,success(1));run();run();assertThat(row.status).isEqualTo("COMPLETED");assertThat(h.request.status).isEqualTo("PENDING");assertThat(h.request.approvalInstanceId).isEqualTo(90L);assertThat(h.request.quotaStatus).isEqualTo("RESERVED");assertThat(reserved()).isEqualTo(420_000_000L);assertThat(h.allocations).hasSize(2);var calls=mockingDetails(remote).getInvocations();assertThat(calls).hasSize(2);for(var call:calls)assertThat(((ApprovalStartRequest)call.getArgument(0)).getIdempotencyKey()).isEqualTo(row.idempotencyKey);}
 @Test void persistedRemoteSuccessRetriesOnlyLocalLink(){when(remote.start(any(),anyString())).thenReturn(success(1));when(h.leaves.finalizeLeaveApprovalStart(eq(10L),eq(1),eq(3L),anyLong(),anyString())).thenReturn(0,1);run();retained("LOCAL_FINALIZE_RETRY");run();assertThat(row.status).isEqualTo("COMPLETED");verify(remote,times(1)).start(any(),anyString());assertThat(reserved()).isEqualTo(420_000_000L);}
}
