package com.erp.oa.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.multipart.MultipartFile;
import com.erp.common.core.web.domain.AjaxResult;
import com.erp.oa.domain.OaReimbursementInvoice;
import com.erp.oa.service.IOaReimbursementService;
import com.erp.oa.service.OaReimbursementExportService;

@DisplayName("OA 报销发票 Controller")
class OaReimbursementControllerTest
{
    @Test
    @DisplayName("幂等重放不调用识别可用性或 OCR")
    void shouldSkipRecognitionForIdempotentReplay()
    {
        IOaReimbursementService service = mock(
                IOaReimbursementService.class);
        OaReimbursementController controller =
                new OaReimbursementController(service,
                        mock(OaReimbursementExportService.class));
        MultipartFile file = mock(MultipartFile.class);
        HttpServletRequest request = mock(HttpServletRequest.class);
        OaReimbursementInvoice replay = new OaReimbursementInvoice();
        replay.setInvoiceId(901L);
        replay.setIdempotentReplay(true);
        when(service.uploadInvoice(77L, file, null)).thenReturn(replay);

        AjaxResult result = controller.uploadInvoice(77L, file, request);

        assertThat(result.get(AjaxResult.DATA_TAG)).isSameAs(replay);
        verify(service).uploadInvoice(77L, file, null);
        verify(service, never()).recognitionAvailability();
        verify(service, never()).recognizeInvoice(anyLong(), anyLong(),
                anyString(), any());
    }
}
