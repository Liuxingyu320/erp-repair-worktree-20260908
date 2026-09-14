package com.erp.system.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.erp.oa.api.domain.HrEmployeeSigningSnapshot;
import com.erp.system.domain.SysHrLifecycleAction;
import com.erp.system.domain.SysHrSignEventOutbox;
import com.erp.system.mapper.SysHrLifecycleActionMapper;
import com.erp.system.mapper.SysHrSignEventOutboxMapper;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;

@ExtendWith(MockitoExtension.class)
class HrSignEventCompensationScannerTest
{
    @Mock
    private SysHrLifecycleActionMapper actionMapper;
    @Mock
    private SysHrSignEventOutboxMapper outboxMapper;
    @Mock
    private HrSignEventCompensationService compensationService;

    private ObjectMapper objectMapper;
    private HrSignEventPayloadFactory payloadFactory;
    private ListAppender<ILoggingEvent> appender;

    @BeforeEach
    void setUp()
    {
        objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        payloadFactory = new HrSignEventPayloadFactory(objectMapper);
        Logger logger = (Logger) org.slf4j.LoggerFactory.getLogger(
                HrSignEventCompensationScanner.class);
        appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
    }

    @AfterEach
    void tearDown()
    {
        Logger logger = (Logger) org.slf4j.LoggerFactory.getLogger(
                HrSignEventCompensationScanner.class);
        logger.detachAppender(appender);
        appender.stop();
    }

    @Test
    void mapperQueryUsesOnlyTerminalImmutableActionsWithoutOutbox()
            throws Exception
    {
        String xml = resourceText(
                "mapper/system/SysHrLifecycleActionMapper.xml");
        int start = xml.indexOf("<select id=\"selectConfirmedActionsWithoutOutbox\"");
        assertThat(start).isGreaterThanOrEqualTo(0);
        String query = xml.substring(start, xml.indexOf("</select>", start));

        assertThat(query)
                .contains("selectConfirmedActionsWithoutOutbox")
                .contains("a.business_status = 'CONFIRMED'")
                .contains("a.action_type = 'RENEWAL_DECLINED'")
                .contains("a.business_status = 'DECLINED'")
                .contains("'ONBOARD_CONFIRMED'")
                .contains("'REGULARIZATION_CONFIRMED'")
                .contains("'TRANSFER_CONFIRMED'")
                .contains("'RENEWAL_CONFIRMED'")
                .contains("'OFFBOARD_CONFIRMED'")
                .contains("not exists")
                .contains("o.action_id = a.action_id")
                .contains("o.event_version = a.version")
                .contains("order by a.action_id asc")
                .contains("limit #{limit}")
                .doesNotContain("'RENEWAL_DECISION'");
    }

    @Test
    void scannerQueriesAtMostOneHundredActions()
    {
        when(actionMapper.selectConfirmedActionsWithoutOutbox(100))
                .thenReturn(List.of());
        HrSignEventCompensationScanner scanner = scanner();

        scanner.compensateMissingOutboxes();

        verify(actionMapper).selectConfirmedActionsWithoutOutbox(100);
    }

    @Test
    void ensureOutboxInsertsPendingPayloadFromFrozenAction()
    {
        SysHrLifecycleAction action = action(91L, "ONBOARD_CONFIRMED");
        when(outboxMapper.selectByActionAndEventVersion(91L, 1L))
                .thenReturn(null);
        when(outboxMapper.insertOutbox(any())).thenReturn(1);
        HrSignEventCompensationService service = realService();

        SysHrSignEventOutbox result = service.ensureOutbox(action);

        ArgumentCaptor<SysHrSignEventOutbox> captor = ArgumentCaptor.forClass(
                SysHrSignEventOutbox.class);
        verify(outboxMapper).insertOutbox(captor.capture());
        SysHrSignEventOutbox inserted = captor.getValue();
        assertThat(result).isSameAs(inserted);
        assertThat(inserted.getActionId()).isEqualTo(91L);
        assertThat(inserted.getEventVersion()).isEqualTo(1L);
        assertThat(inserted.getStatus()).isEqualTo("PENDING");
        assertThat(inserted.getRetryCount()).isZero();
        assertThat(inserted.getVersion()).isZero();
        assertThat(inserted.getNextRetryTime()).isNull();
        assertThat(inserted.getPayloadJson())
                .contains("\"eventId\":\"HR-ACTION:91:1\"")
                .contains("\"scenario\":\"ONBOARD\"");
    }

    @Test
    void existingActionVersionIsReturnedWithoutSecondInsert()
    {
        SysHrLifecycleAction action = action(91L, "ONBOARD_CONFIRMED");
        SysHrSignEventOutbox existing = new SysHrSignEventOutbox();
        existing.setOutboxId(31L);
        when(outboxMapper.selectByActionAndEventVersion(91L, 1L))
                .thenReturn(existing);

        assertThat(realService().ensureOutbox(action)).isSameAs(existing);

        verify(outboxMapper, never()).insertOutbox(any());
    }

    @Test
    void duplicateInsertCompetitionReturnsCommittedWinner()
    {
        SysHrLifecycleAction action = action(91L, "ONBOARD_CONFIRMED");
        SysHrSignEventOutbox winner = new SysHrSignEventOutbox();
        winner.setOutboxId(31L);
        when(outboxMapper.selectByActionAndEventVersion(91L, 1L))
                .thenReturn(null, winner);
        when(outboxMapper.insertOutbox(any()))
                .thenThrow(new DuplicateKeyException("competition"));

        assertThat(realService().ensureOutbox(action)).isSameAs(winner);
    }

    @Test
    void damagedSnapshotCreatesOneDeadTombstone()
    {
        SysHrLifecycleAction action = action(92L, "TRANSFER_CONFIRMED");
        action.setAfterSnapshotJson("{broken");
        when(outboxMapper.selectByActionAndEventVersion(92L, 1L))
                .thenReturn(null);
        when(outboxMapper.insertOutbox(any())).thenReturn(1);

        SysHrSignEventOutbox result = realService().ensureOutbox(action);

        assertThat(result.getStatus()).isEqualTo("DEAD");
        assertThat(result.getPayloadJson()).isEqualTo("{}");
        assertThat(result.getLastError()).isEqualTo("INVALID_ACTION_PAYLOAD");
        assertThat(result.getRetryCount()).isZero();
        assertThat(result.getVersion()).isZero();
        assertThat(result.getNextRetryTime()).isNull();
    }

    @Test
    void oneFailureDoesNotBlockLaterActionOrLeakExceptionMessage()
    {
        SysHrLifecycleAction first = action(91L, "ONBOARD_CONFIRMED");
        SysHrLifecycleAction second = action(92L, "TRANSFER_CONFIRMED");
        when(actionMapper.selectConfirmedActionsWithoutOutbox(100))
                .thenReturn(List.of(first, second));
        when(compensationService.ensureOutbox(first)).thenThrow(
                new IllegalStateException(
                        "310101199001010019 13800000000"));

        scanner().compensateMissingOutboxes();

        verify(compensationService).ensureOutbox(second);
        String logs = appender.list.stream()
                .map(ILoggingEvent::getFormattedMessage)
                .collect(Collectors.joining("\n"));
        assertThat(logs).doesNotContain("310101199001010019")
                .doesNotContain("13800000000");
    }

    @Test
    void ensureOutboxUsesReadCommittedRequiresNewTransaction()
            throws Exception
    {
        Method method = HrSignEventCompensationService.class.getMethod(
                "ensureOutbox", SysHrLifecycleAction.class);
        Transactional tx = method.getAnnotation(Transactional.class);

        assertThat(tx).isNotNull();
        assertThat(tx.propagation()).isEqualTo(Propagation.REQUIRES_NEW);
        assertThat(tx.isolation()).isEqualTo(Isolation.READ_COMMITTED);
    }

    @Test
    void scannerHasNoEmployeeTableDependency()
    {
        Set<Class<?>> fieldTypes = Arrays.stream(
                HrSignEventCompensationScanner.class.getDeclaredFields())
                .filter(field -> !java.lang.reflect.Modifier.isStatic(
                        field.getModifiers()))
                .map(Field::getType)
                .collect(Collectors.toSet());

        assertThat(fieldTypes).containsExactlyInAnyOrder(
                SysHrLifecycleActionMapper.class,
                HrSignEventCompensationService.class);
        assertThat(fieldTypes.stream().map(Class::getSimpleName))
                .noneMatch(name -> name.contains("User")
                        || name.contains("Employee"));
    }

    private HrSignEventCompensationService realService()
    {
        return new HrSignEventCompensationService(outboxMapper,
                payloadFactory);
    }

    private HrSignEventCompensationScanner scanner()
    {
        return new HrSignEventCompensationScanner(actionMapper,
                compensationService);
    }

    private SysHrLifecycleAction action(Long id, String type)
    {
        HrEmployeeSigningSnapshot before = new HrEmployeeSigningSnapshot();
        before.setEmployeeId(7L);
        before.setContractEndDate(LocalDate.of(2026, 7, 31));
        before.setRenewalCount(1);
        HrEmployeeSigningSnapshot after = new HrEmployeeSigningSnapshot();
        after.setEmployeeId(7L);
        after.setContractEndDate(LocalDate.of(2027, 7, 31));
        after.setRenewalCount(2);

        SysHrLifecycleAction action = new SysHrLifecycleAction();
        action.setActionId(id);
        action.setActionType(type);
        action.setEmployeeId(7L);
        action.setVersion(1L);
        action.setBusinessStatus("CONFIRMED");
        action.setEffectiveDate(LocalDate.of(2026, 7, 13));
        action.setActualConfirmTime(Date.from(Instant.parse(
                "2026-07-13T04:00:00Z")));
        action.setBeforeSnapshotJson(json(before));
        action.setAfterSnapshotJson(json(after));
        return action;
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

    private String resourceText(String resource) throws Exception
    {
        try (InputStream input = Thread.currentThread()
                .getContextClassLoader().getResourceAsStream(resource))
        {
            assertThat(input).isNotNull();
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
