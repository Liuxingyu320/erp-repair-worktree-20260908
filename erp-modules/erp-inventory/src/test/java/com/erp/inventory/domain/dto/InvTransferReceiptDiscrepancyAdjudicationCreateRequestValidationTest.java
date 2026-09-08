package com.erp.inventory.domain.dto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.exc.UnrecognizedPropertyException;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;

@DisplayName("V2调拨差异裁决浏览器写请求契约")
class InvTransferReceiptDiscrepancyAdjudicationCreateRequestValidationTest
{
    private static final String TOKEN = "adjb_v1_" + "A".repeat(43);
    private static final ValidatorFactory FACTORY = Validation
            .buildDefaultValidatorFactory();
    private static final Validator VALIDATOR = FACTORY.getValidator();

    @AfterAll
    static void closeValidatorFactory()
    {
        FACTORY.close();
    }

    @Test
    @DisplayName("合法请求通过结构校验并从头路径与正文纯组装领域命令")
    void shouldValidateAndComposeBrowserSafeCommand()
    {
        var request = validRequest();

        assertThat(VALIDATOR.validate(request)).isEmpty();
        var command = request.toCommand("adjudicate-0001", 700L);

        assertThat(command.requestId()).isEqualTo("adjudicate-0001");
        assertThat(command.caseId()).isEqualTo(700L);
        assertThat(command.caseVersion()).isEqualTo(3L);
        assertThat(command.basisToken()).isEqualTo(TOKEN);
        assertThat(command.actions()).singleElement().satisfies(action -> {
            assertThat(action.sequence()).isEqualTo(1);
            assertThat(action.actionType()).isEqualTo("reship");
            assertThat(action.quantity()).isEqualByComparingTo("1.0000");
            assertThat(action.responsibleParty()).isEqualTo("source");
        });
    }

    @Test
    @DisplayName("正文外壳和嵌套动作拒绝非法结构")
    void shouldRejectInvalidEnvelopeAndAction()
    {
        var request = validRequest();
        request.setBasisToken("raw-client-basis");
        request.setCaseVersion("9223372036854775808");
        request.setAdjudicationNote(" ");
        request.setEvidenceRefs(" ");
        var action = request.getActions().get(0);
        action.setSequence(21);
        action.setActionType("client_override");
        action.setQuantity(new BigDecimal("0.00001"));
        action.setResponsibleParty("unknown");
        action.setNote(" ");

        assertThat(invalidProperties(request)).containsExactlyInAnyOrder(
                "basisToken", "caseVersion", "adjudicationNote",
                "evidenceRefs", "actions[0].sequence",
                "actions[0].actionType", "actions[0].quantity",
                "actions[0].responsibleParty", "actions[0].note");
    }

    @Test
    @DisplayName("动作数量同时限制14位整数和4位小数")
    void shouldRejectQuantityOutsideExactDecimalBoundary()
    {
        var request = validRequest();
        request.getActions().get(0).setQuantity(
                new BigDecimal("123456789012345"));
        assertThat(invalidProperties(request))
                .containsExactly("actions[0].quantity");

        request.getActions().get(0).setQuantity(
                new BigDecimal("1.00001"));
        assertThat(invalidProperties(request))
                .containsExactly("actions[0].quantity");
    }

    @Test
    @DisplayName("正文不重复头路径权威字段且不接受内部字段")
    void shouldExcludeTransportAndInternalAuthorityFields() throws Exception
    {
        assertThat(Arrays.stream(
                InvTransferReceiptDiscrepancyAdjudicationCreateRequest.class
                        .getDeclaredFields())
                .map(Field::getName))
                .containsExactlyInAnyOrder("basisToken", "caseVersion",
                        "adjudicationNote", "evidenceRefs", "actions")
                .doesNotContain("requestId", "caseId",
                        "selectedOrganizationId", "factFingerprint",
                        "sourceConfirmationEventId",
                        "targetConfirmationEventId",
                        "decisionFingerprint", "actionAmount",
                        "executionStatus", "adjudicationId");
        assertThat(Arrays.stream(
                InvTransferReceiptDiscrepancyAdjudicationActionRequest.class
                        .getDeclaredFields())
                .map(Field::getName))
                .containsExactlyInAnyOrder("sequence", "actionType",
                        "quantity", "responsibleParty", "note")
                .doesNotContain("amount", "executionStatus",
                        "coverageKind", "factFingerprint",
                        "decisionFingerprint");

        String json = "{\"basisToken\":\"" + TOKEN
                + "\",\"caseVersion\":\"3\",\"adjudicationNote\":\"裁决\""
                + ",\"evidenceRefs\":\"evidence-1\",\"actions\":[{"
                + "\"sequence\":1,\"actionType\":\"reship\","
                + "\"quantity\":1,\"responsibleParty\":\"source\","
                + "\"note\":\"补发\"}],\"factFingerprint\":\""
                + "a".repeat(64) + "\"}";
        assertThatThrownBy(() -> new ObjectMapper().readValue(json,
                InvTransferReceiptDiscrepancyAdjudicationCreateRequest.class))
                .isInstanceOf(UnrecognizedPropertyException.class);

        String nestedJson = json.replace(
                ",\"factFingerprint\":\"" + "a".repeat(64) + "\"",
                "").replace("\"note\":\"补发\"}",
                        "\"note\":\"补发\",\"coverageKind\":"
                                + "\"resolution\"}");
        assertThatThrownBy(() -> new ObjectMapper().readValue(nestedJson,
                InvTransferReceiptDiscrepancyAdjudicationCreateRequest.class))
                .isInstanceOf(UnrecognizedPropertyException.class);
    }

    @Test
    @DisplayName("列表防御性复制且所有字符串表示隐藏令牌和正文")
    void shouldCopyListsAndRedactSensitiveText()
    {
        var request = validRequest();
        List<InvTransferReceiptDiscrepancyAdjudicationActionRequest> source =
                new ArrayList<>(request.getActions());
        request.setActions(source);
        source.clear();

        assertThat(request.getActions()).hasSize(1);
        assertThatThrownBy(() -> request.getActions().clear())
                .isInstanceOf(UnsupportedOperationException.class);
        assertThat(request.toString())
                .contains("basisToken=[REDACTED]",
                        "adjudicationNote=[REDACTED]",
                        "evidenceRefs=[REDACTED]")
                .doesNotContain(TOKEN, "独立裁决", "evidence-1", "来源补发");
        assertThat(request.getActions().get(0).toString())
                .contains("note=[REDACTED]")
                .doesNotContain("来源补发");
    }

    private static Set<String> invalidProperties(
            InvTransferReceiptDiscrepancyAdjudicationCreateRequest request)
    {
        return VALIDATOR.validate(request).stream()
                .map(ConstraintViolation::getPropertyPath)
                .map(Object::toString)
                .collect(Collectors.toSet());
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
        action.setNote("来源补发");

        var request =
                new InvTransferReceiptDiscrepancyAdjudicationCreateRequest();
        request.setBasisToken(TOKEN);
        request.setCaseVersion("3");
        request.setAdjudicationNote("独立裁决");
        request.setEvidenceRefs("evidence-1");
        request.setActions(List.of(action));
        return request;
    }
}
