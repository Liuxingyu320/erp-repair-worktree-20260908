package com.erp.inventory.controller;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import java.util.*;
import jakarta.servlet.http.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.method.HandlerMethod;
import com.erp.common.core.context.SecurityContextHolder;
import com.erp.common.core.constant.SecurityConstants;
import com.erp.common.security.aspect.PreAuthorizeAspect;
import com.erp.common.security.handler.GlobalExceptionHandler;
import com.erp.inventory.service.*;
import com.erp.inventory.domain.*;
import com.erp.inventory.domain.vo.*;
import com.erp.system.api.model.LoginUser;
import com.fasterxml.jackson.databind.ObjectMapper;

/** Real MVC argument binding and the production annotation permission checker. */
class InvSpecialistReadControllerTest
{
    IInvPurchaseService purchase=mock(IInvPurchaseService.class);
    IInvPurchaseReturnService purchaseReturn=mock(IInvPurchaseReturnService.class);
    IInvSalesReturnService salesReturn=mock(IInvSalesReturnService.class);
    com.erp.inventory.service.impl.InvDraftCommandService draftCommands=mock(com.erp.inventory.service.impl.InvDraftCommandService.class);
    MockMvc mvc;
    @BeforeEach void setup()
    {
        var p=new InvPurchaseController();var pr=new InvPurchaseReturnController();var sr=new InvSalesReturnController();
        ReflectionTestUtils.setField(p,"draftCommands",draftCommands);ReflectionTestUtils.setField(pr,"draftCommands",draftCommands);ReflectionTestUtils.setField(sr,"draftCommands",draftCommands);
        ReflectionTestUtils.setField(p,"purchaseService",purchase);ReflectionTestUtils.setField(pr,"purchaseReturnService",purchaseReturn);ReflectionTestUtils.setField(sr,"salesReturnService",salesReturn);
        when(purchase.selectPurchaseSuppliers(any(),any())).thenReturn(List.of());
        when(purchase.selectPurchaseProducts(any(),any())).thenReturn(List.of());
        when(purchase.selectPurchaseOeItems(any(),any())).thenReturn(List.of());
        when(purchase.selectPurchaseGifts(any(),any())).thenReturn(List.of());
        when(salesReturn.selectReturnableSourceOrders(any(),any())).thenReturn(List.of());
        when(salesReturn.submitSavedReturn(anyLong(),any(),any())).thenReturn(new InvSalesReturn());
        mvc=MockMvcBuilders.standaloneSetup(p,pr,sr).defaultRequest(get("/").accept("application/json")).setControllerAdvice(new GlobalExceptionHandler())
                .addInterceptors(new HandlerInterceptor() {
                    @Override public boolean preHandle(HttpServletRequest req,HttpServletResponse res,Object handler)
                    { if(handler instanceof HandlerMethod method) new PreAuthorizeAspect().checkMethodAnnotation(method.getMethod());return true; }
                }).build();
    }
    @AfterEach void clear(){SecurityContextHolder.remove();}
    void login(String permission)
    {
        SecurityContextHolder.setUserId("42");LoginUser user=new LoginUser();user.setUserid(42L);
        user.setPermissions(permission.isEmpty()?Set.of():Set.of(permission));SecurityContextHolder.set(SecurityConstants.LOGIN_USER,user);
    }
    @ParameterizedTest @CsvSource({
                "GET,/purchase/action-context/1,inv:purchase:add",
        "GET,/purchase/action-context/1,inv:purchase:submit",
        "GET,/purchase/action-context/1,inv:purchase:receive",
        "GET,/purchase/action-context/1,inv:purchase:qc",
        "GET,/purchase/action-context/1,inv:purchase:remove",
        "GET,/purchaseReturn/action-context/1,inv:purchaseReturn:add",
        "GET,/purchaseReturn/action-context/1,inv:purchaseReturn:submit",
        "GET,/purchaseReturn/action-context/1,inv:purchaseReturn:confirm",
        "GET,/purchaseReturn/action-context/1,inv:purchaseReturn:remove",
        "GET,/salesReturn/action-context/1,inv:salesReturn:add",
        "GET,/salesReturn/action-context/1,inv:salesReturn:submit",
        "GET,/salesReturn/action-context/1,inv:salesReturn:confirm",
        "GET,/salesReturn/action-context/1,inv:salesReturn:remove",
        "GET,/purchase/draft/1,inv:purchase:add", "GET,/purchase/receive-context/1,inv:purchase:receive",
        "GET,/purchaseReturn/draft/1,inv:purchaseReturn:add", "GET,/salesReturn/draft/1,inv:salesReturn:add",
        "GET,/salesReturn/source-orders,inv:salesReturn:add", "GET,/salesReturn/source-orders/1,inv:salesReturn:add",
        "POST,/purchase/submit/1,inv:purchase:submit", "POST,/purchaseReturn/submit/1,inv:purchaseReturn:submit", "POST,/salesReturn/submit/1,inv:salesReturn:submit",
        "GET,/purchase/catalog/suppliers,inv:purchase:add", "GET,/purchase/catalog/products,inv:purchase:add", "GET,/purchase/catalog/oe,inv:purchase:add", "GET,/purchase/catalog/gifts,inv:purchase:add"
    })
    void exactDutyPermissionAllowsAndMissingPermissionDenies(String verb,String url,String permission) throws Exception
    {
        login(permission);
        mvc.perform((verb.equals("GET")?get(url):post(url)).header("Authorization","Bearer local-f5-test").header("Dept-NumId","20").param("version", "0"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(200));
        clearInvocations(purchase,purchaseReturn,salesReturn);login("");
        mvc.perform((verb.equals("GET")?get(url):post(url)).header("Authorization","Bearer local-f5-test").header("Dept-NumId","20").param("version", "0"))
                .andExpect(jsonPath("$.code").value(403));
        verifyNoInteractions(purchase,purchaseReturn,salesReturn);
    }
    @ParameterizedTest @CsvSource({"purchase,add","purchase,receive","purchaseReturn,add","salesReturn,add","salesReturn,submit"})
    void dutyDoesNotGrantOrdinaryQuery(String feature,String duty) throws Exception
    {
        login("inv:"+feature+":"+duty);
        mvc.perform(get("/"+feature+"/1").header("Authorization","Bearer local-f5-test").header("Dept-NumId","20").param("version", "0"))
                .andExpect(jsonPath("$.code").value(403));verifyNoInteractions(purchase,purchaseReturn,salesReturn);
    }
    @ParameterizedTest @CsvSource({"purchase,list","purchase,query","purchaseReturn,list","purchaseReturn,query","salesReturn,list","salesReturn,query"})
    void generalReadPermissionDoesNotGrantSpecialistActionContext(String feature,String permission) throws Exception
    {
        login("inv:"+feature+":"+permission);
        mvc.perform(get("/"+feature+"/action-context/1").header("Authorization","Bearer local-f5-test").header("Dept-NumId","20").param("version", "0"))
                .andExpect(jsonPath("$.code").value(403));verifyNoInteractions(purchase,purchaseReturn,salesReturn);
    }
    @Test void actionContextHeaderIsMinimalNoStoreAndExactIdentity() throws Exception
    {
        InvPurchaseOrder order=new InvPurchaseOrder();order.setVersion(0L);order.setOrderId(Long.MAX_VALUE);order.setOrderNo("PO");order.setStatus("submitted");order.setShopDeptId(20L);
        order.setSupplierName("private supplier");order.setTotalAmount(new java.math.BigDecimal("999"));order.setDetails(List.of(new InvPurchaseDetail()));
        order.setQcStatus("pending");order.setReceivedQuantity(java.math.BigDecimal.ONE);order.setRemainingQuantity(java.math.BigDecimal.TEN);
        when(purchase.getActionContext(Long.MAX_VALUE,20L)).thenReturn(InvSpecialistActionContext.purchase(order));login("inv:purchase:qc");
        mvc.perform(get("/purchase/action-context/9223372036854775807").header("Authorization","Bearer local-f5-test").header("Dept-NumId","20").param("version", "0").param("shopDeptId","99"))
                .andExpect(header().string("Cache-Control","no-store, max-age=0"))
                .andExpect(jsonPath("$.data.orderId").value(Long.toString(Long.MAX_VALUE)))
                .andExpect(jsonPath("$.data.qcStatus").value("pending"))
                .andExpect(jsonPath("$.data._specialistSummaryOnly").value(true))
                .andExpect(jsonPath("$.data.supplierName").doesNotExist()).andExpect(jsonPath("$.data.totalAmount").doesNotExist())
                .andExpect(jsonPath("$.data.details").doesNotExist());
        verify(purchase).getActionContext(Long.MAX_VALUE,20L);
    }
    @Test void savedSubmitReturnsOnlyTaskSummaryAndExactId() throws Exception
    {
        InvSalesReturn saved=new InvSalesReturn();saved.setVersion(0L);saved.setReturnId(Long.MAX_VALUE);saved.setReturnNo("SR-SAVED");
        saved.setStatus("submitted");saved.setShopDeptId(20L);saved.setSalesOrderId(Long.MAX_VALUE-1);
        saved.setCustomerName("private customer");saved.setApplicantName("private applicant");
        saved.setTotalAmount(new java.math.BigDecimal("777.31"));saved.setRemark("private remark");
        saved.setDetails(List.of(new InvSalesReturnDetail()));
        when(salesReturn.submitSavedReturn(Long.MAX_VALUE, 0L, 20L)).thenReturn(InvSpecialistReadVo.salesReturn(saved));
        login("inv:salesReturn:submit");
        var result=mvc.perform(post("/salesReturn/submit/9223372036854775807")
                .header("Authorization","Bearer local-f5-test").header("Dept-NumId","20").param("version", "0"))
                .andExpect(jsonPath("$.code").value(200)).andExpect(header().string("Cache-Control","no-store, max-age=0"))
                .andReturn();
        var data=new ObjectMapper().readTree(result.getResponse().getContentAsString()).get("data");
        java.util.Set<String> fields=new java.util.HashSet<>();data.fieldNames().forEachRemaining(fields::add);
        assertThat(fields).containsExactlyInAnyOrder("returnId","returnNo","status","shopDeptId","version","_specialistSummaryOnly");
        assertThat(data.get("returnId").isTextual()).isTrue();assertThat(data.get("returnId").asText()).isEqualTo(Long.toString(Long.MAX_VALUE));
        assertThat(data.get("status").asText()).isEqualTo("submitted");
        verify(salesReturn).submitSavedReturn(Long.MAX_VALUE, 0L, 20L);
    }
    @ParameterizedTest @CsvSource({"purchase,save,false","purchase,submit,true","purchaseReturn,save,false","purchaseReturn,submit,true","salesReturn,save,false","salesReturn,submit,true"})
    void everyBodyWriteUsesDurableCommandAndPreservesVersion(String feature,String action,boolean submit) throws Exception
    {
        login("inv:"+feature+":add");
        com.erp.common.security.utils.SecurityUtils.getLoginUser().setPermissions(Set.of("inv:"+feature+":add","inv:"+feature+":submit"));
        var body="{\"orderTitle\":\"purchase\",\"returnTitle\":\"return\",\"supplierName\":\"supplier\",\"customerName\":\"customer\",\"purchaseOrderId\":\"10\",\"salesOrderId\":\"10\",\"version\":\"9223372036854775807\",\"returnDate\":\"2026-09-14\",\"returnReason\":\"repair test\",\"responsibility\":\"supplier\",\"details\":[{}]}";
        mvc.perform(post("/"+feature+"/"+action).header("Authorization","Bearer local-f5-test").header("X-Request-Id","draft-route-command").header("Dept-NumId","20")
                .contentType("application/json").content(body)).andExpect(jsonPath("$.code").value(200));
        if(feature.equals("purchase")) verify(draftCommands).purchase(eq("draft-route-command"),argThat(row -> row.getVersion().equals(Long.MAX_VALUE)),eq(20L),eq(submit));
        else if(feature.equals("purchaseReturn")) verify(draftCommands).purchaseReturn(eq("draft-route-command"),argThat(row -> row.getVersion().equals(Long.MAX_VALUE)),eq(20L),eq(submit));
        else verify(draftCommands).salesReturn(eq("draft-route-command"),argThat(row -> row.getVersion().equals(Long.MAX_VALUE)),eq(20L),eq(submit));
        verifyNoInteractions(purchase,purchaseReturn,salesReturn);
    }
    @Test void legacyAddAndSubmitKeepsItsExistingFullResultContract() throws Exception
    {
        InvSalesReturn saved=new InvSalesReturn();saved.setVersion(0L);saved.setReturnId(1L);saved.setCustomerName("saved customer");
        saved.setTotalAmount(new java.math.BigDecimal("12.34"));saved.setDetails(List.of(new InvSalesReturnDetail()));
        when(draftCommands.salesReturn(eq("draft-test-controller"),any(),eq(20L),eq(true))).thenReturn(saved);
        login("inv:salesReturn:add");
        com.erp.common.security.utils.SecurityUtils.getLoginUser().setPermissions(Set.of("inv:salesReturn:add","inv:salesReturn:submit"));
        mvc.perform(post("/salesReturn/submit").header("X-Request-Id", "draft-test-controller").header("Authorization","Bearer local-f5-test").header("Dept-NumId","20").param("version", "0")
                .contentType("application/json").content("{\"returnTitle\":\"return\",\"customerName\":\"customer\",\"details\":[]}"))
                .andExpect(jsonPath("$.code").value(200)).andExpect(jsonPath("$.data.customerName").value("saved customer"))
                .andExpect(jsonPath("$.data.totalAmount").value(12.34)).andExpect(jsonPath("$.data.details").isArray());
    }
    @ParameterizedTest @ValueSource(strings={"purchaseDraft","purchaseReceive","purchaseReturnDraft","salesReturnDraft","sourceDetail","sourceList"})
    void actualMvcPurposeReadsSerializeEveryLongIdentityAsString(String route) throws Exception
    {
        Object source;Object line;String url;String permission;
        if(route.startsWith("purchaseReturn")) {
            InvPurchaseReturn row=new InvPurchaseReturn();row.setVersion(0L);InvPurchaseReturnDetail detail=new InvPurchaseReturnDetail();
            source=row;line=detail;fillLongIdentities(row);fillLongIdentities(detail);row.setDetails(List.of(detail));
            when(purchaseReturn.getReturnDraft(Long.MAX_VALUE,20L)).thenReturn(InvSpecialistReadVo.purchaseReturn(row));
            url="/purchaseReturn/draft/9223372036854775807";permission="inv:purchaseReturn:add";
        } else if(route.startsWith("purchase")) {
            InvPurchaseOrder row=new InvPurchaseOrder();row.setVersion(0L);InvPurchaseDetail detail=new InvPurchaseDetail();
            source=row;line=detail;fillLongIdentities(row);fillLongIdentities(detail);row.setDetails(List.of(detail));
            when(purchase.getPurchaseDraft(Long.MAX_VALUE,20L)).thenReturn(InvSpecialistReadVo.purchase(row));
            when(purchase.getReceiveContext(Long.MAX_VALUE,20L)).thenReturn(InvSpecialistReadVo.purchase(row));
            boolean receive=route.equals("purchaseReceive");url="/purchase/"+(receive?"receive-context":"draft")+"/9223372036854775807";
            permission="inv:purchase:"+(receive?"receive":"add");
        } else if(route.equals("salesReturnDraft")) {
            InvSalesReturn row=new InvSalesReturn();row.setVersion(0L);InvSalesReturnDetail detail=new InvSalesReturnDetail();
            source=row;line=detail;fillLongIdentities(row);fillLongIdentities(detail);row.setDetails(List.of(detail));
            when(salesReturn.getReturnDraft(Long.MAX_VALUE,20L)).thenReturn(InvSpecialistReadVo.salesReturn(row));
            url="/salesReturn/draft/9223372036854775807";permission="inv:salesReturn:add";
        } else {
            InvSalesOrder row=new InvSalesOrder();InvSalesDetail detail=new InvSalesDetail();
            source=row;line=detail;fillLongIdentities(row);fillLongIdentities(detail);row.setDetails(List.of(detail));
            when(salesReturn.getReturnableSourceOrder(Long.MAX_VALUE,20L)).thenReturn(InvSalesReturnSourceOrderVo.from(row));
            when(salesReturn.selectReturnableSourceOrders(any(),eq(20L))).thenReturn(List.of(InvSalesReturnSourceOrderVo.from(row)));
            url="/salesReturn/source-orders"+(route.equals("sourceList")?"":"/9223372036854775807");permission="inv:salesReturn:add";
        }
        login(permission);
        var result=mvc.perform(get(url).header("Authorization","Bearer local-f5-test").header("Dept-NumId","20").param("version", "0"))
                .andExpect(jsonPath("$.code").value(200)).andReturn();
        var json=new ObjectMapper().readTree(result.getResponse().getContentAsString());
        var data=route.equals("sourceList")?json.get("rows").get(0):json.get("data");
        assertLongIdentities(data,source);assertLongIdentities(data.get("details").get(0),line);
        // Use the actual MVC default converter, rather than a directly instantiated Jackson 2 serializer.
        var converters=mvc.getDispatcherServlet().getWebApplicationContext()
                .getBean(org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerAdapter.class).getMessageConverters();
        assertThat(converters.stream().map(value -> value.getClass().getName()).toList())
                .contains("org.springframework.http.converter.json.JacksonJsonHttpMessageConverter");
    }
    private static void fillLongIdentities(Object bean) throws Exception
    {
        for(var property:java.beans.Introspector.getBeanInfo(bean.getClass()).getPropertyDescriptors())
            if(property.getPropertyType()==Long.class&&property.getWriteMethod()!=null)property.getWriteMethod().invoke(bean,Long.MAX_VALUE);
    }
    private static void assertLongIdentities(com.fasterxml.jackson.databind.JsonNode json,Object bean) throws Exception
    {
        for(var property:java.beans.Introspector.getBeanInfo(bean.getClass()).getPropertyDescriptors()) {
            if(property.getPropertyType()!=Long.class||property.getReadMethod()==null)continue;
            var field=json.get(property.getName());assertThat(field).as(property.getName()).isNotNull();
            assertThat(field.isTextual()).as(property.getName()+" must be JSON string").isTrue();
            assertThat(field.asText()).isEqualTo(Long.toString(Long.MAX_VALUE));
        }
    }
    @Test void submitOnlyCannotCreateOrRewriteUsingLegacyBody() throws Exception
    {
        login("inv:salesReturn:submit");
        mvc.perform(post("/salesReturn/submit").header("X-Request-Id", "draft-test-controller").header("Authorization","Bearer local-f5-test").header("Dept-NumId","20").param("version", "0")
                .contentType("application/json").content("{}"))
                .andExpect(jsonPath("$.code").value(403));verifyNoInteractions(salesReturn);
    }
    @Test void sourceQueryCannotOverrideAuthenticatedOrganizationAndExactLongBinding() throws Exception
    {
        login("inv:salesReturn:add");
        mvc.perform(get("/salesReturn/source-orders").header("Authorization","Bearer local-f5-test").header("Dept-NumId","20").param("version", "0")
                .param("shopDeptId","99").param("params.scopeDeptIds","99").param("keyword","客户"))
                .andExpect(jsonPath("$.code").value(200));
        verify(salesReturn).selectReturnableSourceOrders(argThat(q -> "客户".equals(q.getKeyword())),eq(20L));
        mvc.perform(get("/salesReturn/source-orders/9223372036854775807").header("Authorization","Bearer local-f5-test").header("Dept-NumId","20").param("version", "0"));
        verify(salesReturn).getReturnableSourceOrder(Long.MAX_VALUE,20L);
    }
    @ParameterizedTest @CsvSource({"pageSize,0","pageSize,101","pageNum,0","pageNum,2147483648","startDate,2026-02-30","startDate,2026-9-12","endDate,2026-09-12T00:00:00"})
    void malformedQueryNeverReachesService(String field,String value) throws Exception
    {
        login("inv:salesReturn:add");mvc.perform(get("/salesReturn/source-orders").header("Authorization","Bearer local-f5-test").header("Dept-NumId","20").param("version", "0").param(field,value));
        verifyNoInteractions(salesReturn);
    }
    @ParameterizedTest @ValueSource(strings={"purchase","purchaseReturn","salesReturn"})
    void purposeReadVoKeepsLongIdsExactAndOldJsonNumeric(String feature) throws Exception
    {
        Object original,projected;String key;
        if(feature.equals("purchase")) { InvPurchaseOrder row=new InvPurchaseOrder();row.setVersion(0L);row.setOrderId(Long.MAX_VALUE);original=row;projected=InvSpecialistReadVo.purchase(row);key="orderId"; }
        else if(feature.equals("purchaseReturn")) { InvPurchaseReturn row=new InvPurchaseReturn();row.setVersion(0L);row.setReturnId(Long.MAX_VALUE);original=row;projected=InvSpecialistReadVo.purchaseReturn(row);key="returnId"; }
        else { InvSalesReturn row=new InvSalesReturn();row.setVersion(0L);row.setReturnId(Long.MAX_VALUE);original=row;projected=InvSpecialistReadVo.salesReturn(row);key="returnId"; }
        var mapper=new ObjectMapper();assertThat(mapper.readTree(mapper.writeValueAsString(projected)).get(key).asText()).isEqualTo(Long.toString(Long.MAX_VALUE));
        assertThat(mapper.readTree(mapper.writeValueAsString(projected)).get(key).isTextual()).isTrue();
        assertThat(mapper.readTree(mapper.writeValueAsString(original)).get(key).isIntegralNumber()).isTrue();
    }
}
