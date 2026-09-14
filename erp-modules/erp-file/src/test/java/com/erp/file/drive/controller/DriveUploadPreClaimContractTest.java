package com.erp.file.drive.controller;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import java.io.IOException;
import java.io.InputStream;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import com.erp.file.drive.constant.DriveErrorCodes;
import com.erp.file.drive.domain.DriveActor;
import com.erp.file.drive.exception.DriveException;
import com.erp.file.drive.exception.DriveUploadPreClaimRejectedException;
import com.erp.file.drive.metric.DriveMetrics;
import com.erp.file.drive.service.*;
import com.erp.file.drive.storage.DriveStorageProvider;

/** Actual service pre-claim boundary and HTTP advice; storage/DB collaborators are memory mocks. */
class DriveUploadPreClaimContractTest
{
    private static final String ID="upload_00000000000000000000000000000001";
    private final DriveActor actor=new DriveActor(30L,8L,"Dept","actor",Set.of(),false);
    private DriveUploadService uploads;
    private DriveUploadOperationService operations;
    private DriveStorageProvider storage;
    private DriveUploadPersistence persistence;
    private DriveSpaceService spaces;
    private DriveFilePolicy policy;
    @BeforeEach void setup()
    {
        operations=mock(DriveUploadOperationService.class);storage=mock(DriveStorageProvider.class);
        persistence=mock(DriveUploadPersistence.class);spaces=mock(DriveSpaceService.class);policy=mock(DriveFilePolicy.class);
        uploads=new DriveUploadService(storage,persistence,mock(DriveNodeService.class),spaces,
                mock(DriveQuotaService.class),mock(DriveUploadReservationService.class),policy,
                new DriveNamePolicy(),mock(DriveOperationLogService.class),mock(DriveMetrics.class));
        uploads.setUploadOperations(operations);
        when(operations.receipt(ID,actor)).thenReturn(new DriveUploadOperationService.Receipt(ID,"NOT_OBSERVED",null));
    }
    @ParameterizedTest @ValueSource(strings={"empty","type","permission","hash"})
    void definitePreClaimFailureCarriesOnlyAttemptBoundMarker(String scenario) throws Exception
    {
        MockMultipartFile file=file();
        if(scenario.equals("empty")) file=new MockMultipartFile("file","receipt.pdf","application/pdf",new byte[0]);
        if(scenario.equals("type")) doThrow(new DriveException(DriveErrorCodes.DRIVE_FILE_TYPE_REJECTED,"类型不允许")).when(policy).validate(any(),any(),anyLong());
        if(scenario.equals("permission")) when(spaces.requireWritableSpace(8L,actor)).thenThrow(new DriveException(DriveErrorCodes.DRIVE_ACCESS_DENIED,"权限已变更"));
        if(scenario.equals("hash")) file=new MockMultipartFile("file","receipt.pdf","application/pdf","bytes".getBytes()) {
            @Override public InputStream getInputStream() throws IOException {throw new IOException("incoming file stream unavailable");}
        };
        var selected=file;
        assertThatThrownBy(()->uploads.uploadWithReceipt(selected,8L,0L,actor,ID))
                .isInstanceOf(DriveUploadPreClaimRejectedException.class).satisfies(error->{
                    var response=new DriveExceptionHandler().handleDriveException((DriveException)error);
                    assertThat(response.getBody()).containsKey("uploadAttempt");
                    assertThat(((DriveUploadPreClaimRejectedException)error).getOperationId()).isEqualTo(ID);
                });
        verify(operations,never()).claim(any(),any(),any(),any(),any(),anyLong(),any());
        verifyNoInteractions(storage,persistence);
    }
    @ParameterizedTest @ValueSource(strings={"SUCCEEDED","PROCESSING","FAILED_SAFE","REVIEW_REQUIRED"})
    void existingReceiptWinsOverLaterPreflightFailure(String status)
    {
        var original=new DriveUploadOperationService.Receipt(ID,status,status.equals("SUCCEEDED")?"9007199254740997":null);
        when(operations.receipt(ID,actor)).thenReturn(original);
        doThrow(new DriveException(DriveErrorCodes.DRIVE_FILE_TYPE_REJECTED,"later policy change")).when(policy).validate(any(),any(),anyLong());
        assertThat(uploads.uploadWithReceipt(file(),8L,0L,actor,ID)).isSameAs(original);
        verify(operations,never()).claim(any(),any(),any(),any(),any(),anyLong(),any());
        verifyNoInteractions(storage,persistence);
    }
    @Test void receiptLookupFailureDoesNotGrantAttemptRejectionAuthority()
    {
        doThrow(new DriveException(DriveErrorCodes.DRIVE_FILE_TYPE_REJECTED,"type")).when(policy).validate(any(),any(),anyLong());
        when(operations.receipt(ID,actor)).thenThrow(new DriveException(DriveErrorCodes.DRIVE_ACCESS_DENIED,"old receipt belongs to another account"));
        assertThatThrownBy(()->uploads.uploadWithReceipt(file(),8L,0L,actor,ID))
                .isInstanceOf(DriveException.class).isNotInstanceOf(DriveUploadPreClaimRejectedException.class);
        verifyNoInteractions(storage,persistence);
    }
    @Test void lostClaimCommitIsUnknownEvenThoughNoStorageCallHasStarted()
    {
        when(operations.claim(eq(ID),eq(actor),eq(8L),eq(0L),any(),eq(5L),any()))
                .thenThrow(new DriveException(DriveErrorCodes.DRIVE_STORAGE_UNAVAILABLE,"lost claim commit reply"));
        assertThatThrownBy(()->uploads.uploadWithReceipt(file(),8L,0L,actor,ID))
                .isInstanceOf(DriveException.class).isNotInstanceOf(DriveUploadPreClaimRejectedException.class);
        verify(operations,never()).receipt(any(),any());verifyNoInteractions(storage,persistence);
    }
    @Test void httpEmptyFileUsesServiceBoundaryAndOrdinaryErrorsDoNotAcquireMarker() throws Exception
    {
        var actors=mock(DriveActorResolver.class);when(actors.resolve()).thenReturn(actor);
        var mvc=MockMvcBuilders.standaloneSetup(new DriveNodeController(mock(DriveFeatureGuard.class),actors,
                mock(DriveNodeService.class),uploads,mock(DriveTrashService.class)))
                .setControllerAdvice(new DriveExceptionHandler()).build();
        mvc.perform(multipart("/drive/files").file(new MockMultipartFile("file","empty.pdf","application/pdf",new byte[0]))
                .param("spaceId","8").param("operationId",ID))
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.uploadAttempt.operationId").value(ID))
                .andExpect(jsonPath("$.uploadAttempt.state").value("REJECTED_BEFORE_CLAIM"));
        var ordinary=new DriveExceptionHandler().handleDriveException(new DriveException(DriveErrorCodes.DRIVE_ACCESS_DENIED,"denied"));
        assertThat(ordinary.getBody()).doesNotContainKey("uploadAttempt");
    }
    private static MockMultipartFile file(){return new MockMultipartFile("file","receipt.pdf","application/pdf","bytes".getBytes());}
}
