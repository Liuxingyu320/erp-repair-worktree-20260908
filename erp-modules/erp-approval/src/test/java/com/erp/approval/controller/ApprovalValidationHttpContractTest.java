package com.erp.approval.controller;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import com.erp.approval.mapper.ApprovalValidationMapper;
import com.erp.approval.service.ApprovalCoverageValidationService;
import com.erp.common.core.context.SecurityContextHolder;

class ApprovalValidationHttpContractTest
{
    @Test void manualValidationRequiresVersionAndPreservesAnExactLongVersion() throws Exception
    {
        var service=mock(ApprovalCoverageValidationService.class);
        var mvc=MockMvcBuilders.standaloneSetup(new ApprovalValidationController(mock(ApprovalValidationMapper.class),service)).build();
        mvc.perform(post("/validation/run").contentType(MediaType.APPLICATION_JSON).content("{\"templateId\":1,\"ruleId\":2,\"validationType\":\"MANUAL\"}"))
                .andExpect(status().isBadRequest());verifyNoInteractions(service);
        SecurityContextHolder.setUserId("30");SecurityContextHolder.setUserName("validator");
        try
        {
            mvc.perform(post("/validation/run").contentType(MediaType.APPLICATION_JSON).content("{\"versionId\":\"9007199254740997\",\"validationType\":\"MANUAL\"}"))
                    .andExpect(status().isOk());
            verify(service).validate(9007199254740997L,"MANUAL",30L,"validator");
        } finally {SecurityContextHolder.remove();}
    }
}
