package com.erp.system.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Date;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.erp.oa.api.domain.HrEmployeeSigningSnapshot;
import com.erp.oa.api.domain.HrSignBusinessEvent;
import com.erp.system.domain.SysHrLifecycleAction;

class HrSignEventPayloadFactoryTest
{
    private static final Instant CONFIRMED = Instant.parse(
            "2026-07-13T01:30:00Z");

    private ObjectMapper objectMapper;
    private HrSignEventPayloadFactory factory;

    @BeforeEach
    void setUp()
    {
        objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        factory = new HrSignEventPayloadFactory(objectMapper);
    }

    @ParameterizedTest
    @MethodSource("supportedActions")
    void mapsSupportedActionsToCanonicalScenario(String actionType,
            String scenario)
    {
        SysHrLifecycleAction action = action(actionType);

        HrSignBusinessEvent event = factory.fromAction(action);

        assertThat(event.getScenario()).isEqualTo(scenario);
        assertThat(event.getEventId()).isEqualTo("HR-ACTION:91:1");
        assertThat(event.getSourceType()).isEqualTo("HR_LIFECYCLE_ACTION");
        assertThat(event.getSourceBusinessId()).isEqualTo("91");
        assertThat(event.getSourceEventVersion()).isEqualTo(1L);
        assertThat(event.getEmployeeId()).isEqualTo(7L);
        assertThat(event.getOperatorUserId()).isEqualTo(21L);
        assertThat(event.getOccurredTime()).isEqualTo(Date.from(CONFIRMED));
        assertThat(event.getAttributes())
                .containsEntry("actionType", actionType)
                .containsEntry("sourceActionId", 91L)
                .containsEntry("sourceActionVersion", 1L);
    }

    static Stream<Arguments> supportedActions()
    {
        return Stream.of(
                Arguments.of("ONBOARD_CONFIRMED", "ONBOARD"),
                Arguments.of("REGULARIZATION_CONFIRMED", "REGULARIZE"),
                Arguments.of("TRANSFER_CONFIRMED", "TRANSFER"),
                Arguments.of("RENEWAL_CONFIRMED", "RENEWAL"),
                Arguments.of("RENEWAL_DECLINED", "RENEWAL"),
                Arguments.of("OFFBOARD_CONFIRMED", "OFFBOARD"));
    }

    @Test
    void transferAttributesComeOnlyFromFrozenAction()
    {
        SysHrLifecycleAction action = action("TRANSFER_CONFIRMED");
        action.setEffectiveDate(LocalDate.of(2026, 7, 12));
        action.setRiskLevel("HIGH");
        action.setHistoricalReason("补录调岗");

        HrSignBusinessEvent event = factory.fromAction(action);

        assertThat(event.getAttributes())
                .containsEntry("effectiveDate", "2026-07-12")
                .containsEntry("actualConfirmTime", Date.from(CONFIRMED))
                .containsEntry("historicalSupplement", true)
                .containsEntry("riskLevel", "HIGH")
                .containsEntry("historicalReason", "补录调岗");
    }

    @Test
    void sameDayInShanghaiIsNotHistorical()
    {
        SysHrLifecycleAction action = action("OFFBOARD_CONFIRMED");
        action.setEffectiveDate(LocalDate.of(2026, 7, 13));
        action.setRiskLevel("LOW");

        HrSignBusinessEvent event = factory.fromAction(action);

        assertThat(event.getAttributes())
                .containsEntry("effectiveDate", "2026-07-13")
                .containsEntry("historicalSupplement", false)
                .containsEntry("riskLevel", "LOW")
                .doesNotContainKey("historicalReason");
        assertThat(event.getAfterSnapshot().getLeaveDate())
                .isEqualTo(LocalDate.of(2026, 7, 13));
    }

    @Test
    void renewalAttributesAreDerivedFromBeforeAndAfterSnapshots()
    {
        SysHrLifecycleAction action = action("RENEWAL_CONFIRMED");

        HrSignBusinessEvent event = factory.fromAction(action);

        assertThat(event.getAttributes())
                .containsEntry("decision", "RENEW")
                .containsEntry("oldContractEndDate", "2026-07-31")
                .containsEntry("oldRenewalCount", 2)
                .containsEntry("newRenewalCount", 3);
    }

    @Test
    void renewalDeclineUsesDeclineDecision()
    {
        SysHrLifecycleAction action = action("RENEWAL_DECLINED");

        assertThat(factory.fromAction(action).getAttributes())
                .containsEntry("decision", "DECLINE");
    }

    @Test
    void occurredTimeFallsBackToImmutableCreateTime()
    {
        SysHrLifecycleAction action = action("ONBOARD_CONFIRMED");
        action.setActualConfirmTime(null);
        Date created = Date.from(Instant.parse("2026-07-12T03:00:00Z"));
        action.setCreateTime(created);

        assertThat(factory.fromAction(action).getOccurredTime())
                .isEqualTo(created);
    }

    @Test
    void renewalDecisionAndUnknownActionsAreRejected()
    {
        assertThatThrownBy(() -> factory.fromAction(action("RENEWAL_DECISION")))
                .isInstanceOf(HrSignEventPayloadFactory.InvalidLifecycleActionException.class);
        assertThatThrownBy(() -> factory.fromAction(action("UNKNOWN")))
                .isInstanceOf(HrSignEventPayloadFactory.InvalidLifecycleActionException.class);
    }

    @Test
    void damagedSnapshotUsesFactorySpecificException()
    {
        SysHrLifecycleAction action = action("TRANSFER_CONFIRMED");
        action.setAfterSnapshotJson("{broken");

        assertThatThrownBy(() -> factory.fromAction(action))
                .isInstanceOf(HrSignEventPayloadFactory.InvalidLifecycleActionException.class)
                .hasMessage("生命周期动作快照无效");
    }

    @Test
    void payloadWriterUsesIsoLocalDates()
    {
        HrSignBusinessEvent event = factory.fromAction(
                action("OFFBOARD_CONFIRMED"));

        String payload = factory.writePayload(event);

        assertThat(payload).contains("\"leaveDate\":\"2026-07-13\"")
                .doesNotContain("\"leaveDate\":[2026,7,13]");
    }

    private SysHrLifecycleAction action(String actionType)
    {
        SysHrLifecycleAction action = new SysHrLifecycleAction();
        action.setActionId(91L);
        action.setActionType(actionType);
        action.setEmployeeId(7L);
        action.setVersion(1L);
        action.setOperatorUserId(21L);
        action.setActualConfirmTime(Date.from(CONFIRMED));
        action.setCreateTime(Date.from(CONFIRMED.minusSeconds(60)));
        action.setEffectiveDate(LocalDate.of(2026, 7, 13));
        action.setRiskLevel("LOW");
        action.setBeforeSnapshotJson(json(beforeSnapshot()));
        action.setAfterSnapshotJson(json(afterSnapshot()));
        return action;
    }

    private HrEmployeeSigningSnapshot beforeSnapshot()
    {
        HrEmployeeSigningSnapshot snapshot = new HrEmployeeSigningSnapshot();
        snapshot.setEmployeeId(7L);
        snapshot.setEmployeeName("员工甲");
        snapshot.setContractEndDate(LocalDate.of(2026, 7, 31));
        snapshot.setRenewalCount(2);
        return snapshot;
    }

    private HrEmployeeSigningSnapshot afterSnapshot()
    {
        HrEmployeeSigningSnapshot snapshot = new HrEmployeeSigningSnapshot();
        snapshot.setEmployeeId(7L);
        snapshot.setEmployeeName("员工甲");
        snapshot.setContractEndDate(LocalDate.of(2027, 7, 31));
        snapshot.setRenewalCount(3);
        snapshot.setLeaveDate(LocalDate.of(2026, 7, 13));
        return snapshot;
    }

    private String json(Object value)
    {
        try
        {
            return objectMapper.writeValueAsString(value);
        }
        catch (Exception exception)
        {
            throw new AssertionError(exception);
        }
    }
}
