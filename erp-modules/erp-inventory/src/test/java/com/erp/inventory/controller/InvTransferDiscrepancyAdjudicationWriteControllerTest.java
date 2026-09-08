package com.erp.inventory.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import com.erp.common.core.utils.ShopHeaderUtils;
import com.erp.common.core.web.domain.AjaxResult;
import com.erp.common.log.annotation.Log;
import com.erp.common.log.enums.BusinessType;
import com.erp.common.security.annotation.RequiresPermissions;
import com.erp.inventory.annotation.PersistentCommand;
import com.erp.inventory.domain.dto.InvTransferReceiptDiscrepancyAdjudicationActionRequest;
import com.erp.inventory.domain.dto.InvTransferReceiptDiscrepancyAdjudicationCreateRequest;
import com.erp.inventory.domain.transfer.InvTransferReceiptDiscrepancyAdjudicationCommand;
import com.erp.inventory.domain.vo.InvTransferReceiptDiscrepancyAdjudicationBasisIssueVo;
import com.erp.inventory.domain.vo.InvTransferReceiptDiscrepancyAdjudicationCreationVo;
import com.erp.inventory.service.impl.InvTransferReceiptDiscrepancyAdjudicationBasisIssueService;
import com.erp.inventory.service.impl.InvTransferReceiptDiscrepancyAdjudicationCreationService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@ExtendWith(MockitoExtension.class)
@DisplayName("V2调拨差异裁决HTTP写边界")
class InvTransferDiscrepancyAdjudicationWriteControllerTest
{
    private static final String TOKEN = "adjb_v1_" + "A".repeat(43);

    @Mock
    private InvTransferReceiptDiscrepancyAdjudicationBasisIssueService
            basisIssueService;
    @Mock
    private InvTransferReceiptDiscrepancyAdjudicationCreationService
            creationService;
    @Mock
    private HttpServletRequest servletRequest;
    @Mock
    private HttpServletResponse servletResponse;

    private InvTransferController controller;

    @BeforeEach
    void setUp() throws Exception
    {
        controller = new InvTransferController();
        setField(controller, "discrepancyAdjudicationBasisIssueService",
                basisIssueService);
        setField(controller, "discrepancyAdjudicationCreationService",
                creationService);
    }

    @Test
    @DisplayName("basis签发是精确权限无正文且不伪装为持久化幂等命令")
    void basisIssueShouldBeNoStoreCapabilityBoundary() throws Exception
    {
        Method method = InvTransferController.class.getMethod(
                "issueDiscrepancyAdjudicationBasis", Long.class,
                HttpServletRequest.class, HttpServletResponse.class);

        assertThat(method.getAnnotation(PostMapping.class).value())
                .containsExactly("/discrepancies/{discrepancyCaseId}"
                        + "/adjudication-basis");
        assertThat(method.getAnnotation(RequiresPermissions.class).value())
                .containsExactly("inv:transfer:discrepancy:adjudicate");
        assertThat(method.getAnnotation(PersistentCommand.class)).isNull();
        Log audit = method.getAnnotation(Log.class);
        assertThat(audit.businessType()).isEqualTo(BusinessType.OTHER);
        assertThat(audit.includeParamNames()).isEmpty();
        assertThat(method.getParameters())
                .noneMatch(parameter -> parameter.isAnnotationPresent(
                        RequestBody.class));
    }

    @Test
    @DisplayName("裁决提交只从头路径正文组装持久化幂等命令")
    void creationShouldBeValidatedPersistentCommandBoundary()
            throws Exception
    {
        Method method = InvTransferController.class.getMethod(
                "createDiscrepancyAdjudication", Long.class,
                InvTransferReceiptDiscrepancyAdjudicationCreateRequest.class,
                String.class, HttpServletRequest.class,
                HttpServletResponse.class);

        assertThat(method.getAnnotation(PostMapping.class).value())
                .containsExactly("/discrepancies/{discrepancyCaseId}"
                        + "/adjudications");
        assertThat(method.getAnnotation(RequiresPermissions.class).value())
                .containsExactly("inv:transfer:discrepancy:adjudicate");
        assertThat(method.getAnnotation(PersistentCommand.class)).isNotNull();
        Log audit = method.getAnnotation(Log.class);
        assertThat(audit.businessType()).isEqualTo(BusinessType.UPDATE);
        assertThat(audit.includeParamNames()).isEmpty();
        assertThat(method.getParameters()[1].getAnnotation(
                RequestBody.class)).isNotNull();
        assertThat(method.getParameters()[1].getAnnotation(
                Validated.class)).isNotNull();
        RequestHeader requestHeader = method.getParameters()[2]
                .getAnnotation(RequestHeader.class);
        assertThat(requestHeader).isNotNull();
        assertThat(requestHeader.name()).isEqualTo("X-Request-Id");
        assertThat(requestHeader.required()).isTrue();
    }

    @Test
    @DisplayName("basis签发只传路径事项与请求组织且响应禁止缓存")
    void shouldDelegateBasisIssueWithoutBrowserAuthoredFacts()
    {
        selectedOrganization();
        var issued = new
                InvTransferReceiptDiscrepancyAdjudicationBasisIssueVo(
                        TOKEN, "700", "3", "301",
                        "2026-08-03T00:00:00Z",
                        "2026-08-03T00:05:00Z", "server");
        when(basisIssueService.issue(700L, 301L)).thenReturn(issued);

        AjaxResult result = controller.issueDiscrepancyAdjudicationBasis(
                700L, servletRequest, servletResponse);

        assertThat(result.get(AjaxResult.DATA_TAG)).isSameAs(issued);
        verify(basisIssueService).issue(700L, 301L);
        verify(servletResponse).setHeader("Cache-Control", "no-store");
    }

    @Test
    @DisplayName("裁决提交精确传递幂等头路径事项正文和请求组织")
    void shouldDelegateBrowserSafeCommandAndReturnNoStoreResult()
    {
        selectedOrganization();
        var created = new
                InvTransferReceiptDiscrepancyAdjudicationCreationVo(
                        "901", "700", "adjudication_planned", 1, false,
                        "2026-08-03T00:02:00Z",
                        "2026-08-03T00:02:01Z", "server");
        when(creationService.create(any(), eq(301L))).thenReturn(created);

        AjaxResult result = controller.createDiscrepancyAdjudication(700L,
                validRequest(), "adjudicate-0001", servletRequest,
                servletResponse);

        ArgumentCaptor<InvTransferReceiptDiscrepancyAdjudicationCommand>
                command = ArgumentCaptor.forClass(
                        InvTransferReceiptDiscrepancyAdjudicationCommand.class);
        verify(creationService).create(command.capture(), eq(301L));
        assertThat(command.getValue().requestId())
                .isEqualTo("adjudicate-0001");
        assertThat(command.getValue().caseId()).isEqualTo(700L);
        assertThat(command.getValue().caseVersion()).isEqualTo(3L);
        assertThat(command.getValue().basisToken()).isEqualTo(TOKEN);
        assertThat(command.getValue().actions()).singleElement()
                .satisfies(action -> assertThat(action.quantity())
                        .isEqualByComparingTo("1.0000"));
        assertThat(result.get(AjaxResult.DATA_TAG)).isSameAs(created);
        verify(servletResponse).setHeader("Cache-Control", "no-store");
    }

    private static InvTransferReceiptDiscrepancyAdjudicationCreateRequest
            validRequest()
    {
        var action =
                new InvTransferReceiptDiscrepancyAdjudicationActionRequest();
        action.setSequence(1);
        action.setActionType("reship");
        action.setQuantity(new BigDecimal("1.0000"));
        action.setResponsibleParty("source");
        action.setNote("来源组织补发");

        var request =
                new InvTransferReceiptDiscrepancyAdjudicationCreateRequest();
        request.setBasisToken(TOKEN);
        request.setCaseVersion("3");
        request.setAdjudicationNote("独立裁决");
        request.setEvidenceRefs("evidence-1");
        request.setActions(List.of(action));
        return request;
    }

    private void selectedOrganization()
    {
        when(servletRequest.getHeader(ShopHeaderUtils.SHOP_HEADER))
                .thenReturn("301");
    }

    private static void setField(Object target, String name, Object value)
            throws Exception
    {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }
}
