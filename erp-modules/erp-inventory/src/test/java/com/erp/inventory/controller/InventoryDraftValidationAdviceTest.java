package com.erp.inventory.controller;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import com.erp.common.security.handler.GlobalExceptionHandler;

class InventoryDraftValidationAdviceTest
{
    @Test
    void realDraftControllersReturnDefiniteRejectionForInvalidBodiesBeforeServiceInvocation() throws Exception
    {
        var mvc = MockMvcBuilders.standaloneSetup(new InvPurchaseController(), new InvPurchaseReturnController(), new InvSalesReturnController())
                .setControllerAdvice(new InventoryDraftValidationAdvice(), new GlobalExceptionHandler()).build();
        for (String feature : new String[] {"purchase", "purchaseReturn", "salesReturn"})
        {
            for (String action : new String[] {"save", "submit"})
            {
                for (String body : new String[] {"{}", "{", "{\"orderTitle\":\"   \",\"returnTitle\":\"   \"}"})
                {
                    mvc.perform(post("/" + feature + "/" + action).header("X-Request-Id", "validation-test")
                            .contentType(MediaType.APPLICATION_JSON).content(body))
                            .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(500))
                            .andExpect(jsonPath("$.draftOutcome").value("REJECTED"));
                }
            }
        }
    }
}
