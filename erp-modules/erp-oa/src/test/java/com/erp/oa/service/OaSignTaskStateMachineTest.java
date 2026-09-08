package com.erp.oa.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import com.erp.common.core.exception.ServiceException;
import com.erp.oa.constant.OaSignOperatorType;
import com.erp.oa.constant.OaSignPackageStatus;
import com.erp.oa.constant.OaSignScenario;
import com.erp.oa.constant.OaSignTaskStatus;

@DisplayName("签约任务显式状态机")
class OaSignTaskStateMachineTest
{
    private static final Set<String> ALLOWED = Set.of(
            "NEW->VALIDATING",
            "VALIDATING->NEEDS_DATA", "VALIDATING->DRAFT_CREATED", "VALIDATING->NO_ACTION",
            "VALIDATING->FAILED",
            "NEEDS_DATA->VALIDATING", "NEEDS_DATA->CANCELLED",
            "DRAFT_CREATED->WAITING_HR_CONFIRM", "DRAFT_CREATED->READY_TO_SEND",
            "DRAFT_CREATED->FAILED", "DRAFT_CREATED->CANCELLED",
            "WAITING_HR_CONFIRM->READY_TO_SEND", "WAITING_HR_CONFIRM->NEEDS_DATA",
            "WAITING_HR_CONFIRM->FAILED", "WAITING_HR_CONFIRM->CANCELLED",
            "READY_TO_SEND->SENDING", "READY_TO_SEND->WAITING_HR_CONFIRM",
            "READY_TO_SEND->CANCELLED",
            "SENDING->PENDING_SIGN", "SENDING->PENDING_FINAL_CONFIRM", "SENDING->FAILED",
            "FAILED->VALIDATING", "FAILED->READY_TO_SEND", "FAILED->CANCELLED",
            "PENDING_SIGN->VIEWED", "PENDING_SIGN->PENDING_COMPANY",
            "PENDING_SIGN->PENDING_FINAL_CONFIRM", "PENDING_SIGN->REFUSED",
            "PENDING_SIGN->EXPIRED", "PENDING_SIGN->CANCELLED",
            "VIEWED->PENDING_COMPANY", "VIEWED->PENDING_FINAL_CONFIRM",
            "VIEWED->REFUSED", "VIEWED->EXPIRED", "VIEWED->CANCELLED",
            "PENDING_COMPANY->PENDING_FINAL_CONFIRM", "PENDING_COMPANY->CANCELLED",
            "PENDING_FINAL_CONFIRM->SIGNED", "PENDING_FINAL_CONFIRM->REFUSED",
            "PENDING_FINAL_CONFIRM->EXPIRED", "PENDING_FINAL_CONFIRM->CANCELLED");

    private final OaSignTaskStateMachine stateMachine = new OaSignTaskStateMachine();

    @Test
    @DisplayName("仅允许路线图声明的状态转换")
    void shouldAllowOnlyDeclaredTransitions()
    {
        for (OaSignTaskStatus from : OaSignTaskStatus.values())
        {
            for (OaSignTaskStatus to : OaSignTaskStatus.values())
            {
                String transition = from.name() + "->" + to.name();
                if (ALLOWED.contains(transition))
                {
                    stateMachine.assertAllowed(from, to);
                }
                else
                {
                    assertThatThrownBy(() -> stateMachine.assertAllowed(from, to))
                            .isInstanceOf(ServiceException.class)
                            .hasMessage("当前签约任务状态不允许执行此操作");
                }
            }
        }
    }

    @Test
    @DisplayName("已签署终态只能从待最终确认进入")
    void shouldRequireFinalConfirmationBeforeSigned()
    {
        stateMachine.assertAllowed(OaSignTaskStatus.PENDING_FINAL_CONFIRM,
                OaSignTaskStatus.SIGNED);
        assertThatThrownBy(() -> stateMachine.assertAllowed(
                OaSignTaskStatus.PENDING_SIGN, OaSignTaskStatus.SIGNED))
                .isInstanceOf(ServiceException.class);
        assertThatThrownBy(() -> stateMachine.assertAllowed(
                OaSignTaskStatus.VIEWED, OaSignTaskStatus.SIGNED))
                .isInstanceOf(ServiceException.class);
    }

    @Test
    @DisplayName("场景和操作方枚举严格固定")
    void shouldKeepStableScenarioAndOperatorVocabulary()
    {
        assertThat(OaSignScenario.values()).extracting(Enum::name)
                .containsExactly("ONBOARD", "REGULARIZE", "TRANSFER", "OFFBOARD", "RENEWAL");
        assertThat(OaSignOperatorType.values()).extracting(Enum::name)
                .containsExactly("SYSTEM", "HR", "EMPLOYEE", "ADMIN");
    }

    @Test
    @DisplayName("只有业务终态允许并发幂等")
    void shouldDeclareBusinessTerminalStates()
    {
        assertThat(OaSignTaskStatus.values()).filteredOn(OaSignTaskStatus::isTerminal)
                .extracting(Enum::name)
                .containsExactlyInAnyOrder("NO_ACTION", "SIGNED", "REFUSED", "EXPIRED", "CANCELLED");
    }

    @Test
    @DisplayName("拒签和过期包状态词汇固定")
    void shouldDeclareRefusedAndExpiredPackageStatuses()
    {
        assertThat(OaSignPackageStatus.REFUSED).isEqualTo("refused");
        assertThat(OaSignPackageStatus.EXPIRED).isEqualTo("expired");
    }

    @Test
    @DisplayName("任务终态不得存在出边")
    void shouldRejectEveryTransitionFromTerminalStates()
    {
        for (OaSignTaskStatus from : OaSignTaskStatus.values())
        {
            if (!from.isTerminal())
            {
                continue;
            }
            for (OaSignTaskStatus to : OaSignTaskStatus.values())
            {
                assertThatThrownBy(() -> stateMachine.assertAllowed(from, to))
                        .isInstanceOf(ServiceException.class)
                        .hasMessage("当前签约任务状态不允许执行此操作");
            }
        }
    }
}
