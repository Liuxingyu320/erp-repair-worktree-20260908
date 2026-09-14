package com.erp.file.drive.controller;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import com.erp.file.drive.domain.DriveActor;
import com.erp.file.drive.service.*;

class DriveUploadReceiptHttpTest
{
    @Test void multipartRequiresReceiptIdentityAndQueryNeverInvokesUpload() throws Exception
    {
        var guard=mock(DriveFeatureGuard.class);var actors=mock(DriveActorResolver.class);
        var uploads=mock(DriveUploadService.class);var actor=new DriveActor(30L,8L,"Dept","actor",Set.of(),false);
        when(actors.resolve()).thenReturn(actor);
        var mvc=MockMvcBuilders.standaloneSetup(new DriveNodeController(guard,actors,mock(DriveNodeService.class),uploads,mock(DriveTrashService.class))).build();
        var file=new MockMultipartFile("file","receipt.pdf","application/pdf","bytes".getBytes());
        mvc.perform(multipart("/drive/files").file(file).param("spaceId","8")).andExpect(status().isBadRequest());
        verifyNoInteractions(uploads);
        String id="upload_00000000000000000000000000000001";
        var receipt=new DriveUploadOperationService.Receipt(id,"SUCCEEDED","9007199254740997");
        when(uploads.uploadWithReceipt(any(),eq(8L),eq(0L),eq(actor),eq(id))).thenReturn(receipt);
        when(uploads.uploadReceipt(id,actor)).thenReturn(receipt);
        mvc.perform(multipart("/drive/files").file(file).param("spaceId","8").param("operationId",id))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.nodeId").value("9007199254740997"));
        mvc.perform(get("/drive/uploads/"+id)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.operationId").value(id)).andExpect(jsonPath("$.data.status").value("SUCCEEDED"));
        verify(uploads,times(1)).uploadWithReceipt(any(),eq(8L),eq(0L),eq(actor),eq(id));
        verify(uploads).uploadReceipt(id,actor);verify(uploads,never()).upload(any(),any(),any(),any());
    }
}
