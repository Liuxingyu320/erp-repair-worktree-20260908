package com.erp.inventory.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.erp.common.core.context.SecurityContextHolder;
import com.erp.common.core.exception.ServiceException;
import com.erp.inventory.domain.InvTransferCommand;
import com.erp.inventory.domain.InvTransferOrder;
import com.erp.inventory.mapper.InvTransferCommandMapper;

@DisplayName("调拨持久化命令执行器")
class InvTransferCommandExecutorTest
{
    private FakeCommandMapper mapper;
    private InvTransferCommandExecutor executor;

    @BeforeEach
    void setUp()
    {
        mapper = new FakeCommandMapper();
        executor = new InvTransferCommandExecutor(mapper,
                new ObjectMapper());
        SecurityContextHolder.setUserId("9");
        SecurityContextHolder.setUserName("warehouse-operator");
    }

    @AfterEach
    void tearDown()
    {
        SecurityContextHolder.remove();
    }

    @Test
    @DisplayName("重复请求返回首次结果且不再次执行业务")
    void shouldReplayFirstResultWithoutRepeatingAction()
    {
        AtomicInteger calls = new AtomicInteger();
        Map<String, Object> firstPayload = new HashMap<>();
        firstPayload.put("version", 3L);
        firstPayload.put("transferId", 901L);

        InvTransferOrder first = executor.execute(
                "transfer-submit-0001", "TRANSFER_SUBMIT", 201L,
                "transfer:901", firstPayload, InvTransferOrder.class,
                () -> result(calls.incrementAndGet(), "submitted"));

        Map<String, Object> replayPayload = new HashMap<>();
        replayPayload.put("transferId", 901L);
        replayPayload.put("version", 3L);
        InvTransferOrder replayed = executor.execute(
                "transfer-submit-0001", "TRANSFER_SUBMIT", 201L,
                "transfer:901", replayPayload, InvTransferOrder.class,
                () -> result(calls.incrementAndGet(), "wrong-second-result"));

        assertThat(first.getTransferId()).isEqualTo(1L);
        assertThat(replayed.getTransferId()).isEqualTo(1L);
        assertThat(replayed.getStatus()).isEqualTo("submitted");
        assertThat(calls).hasValue(1);
    }

    @Test
    @DisplayName("同一请求标识不得覆盖不同内容或用户")
    void shouldRejectRequestIdReuseWithDifferentIdentityOrPayload()
    {
        executor.execute("transfer-deliver-0001", "TRANSFER_DELIVER",
                201L, "transfer:901", Map.of("quantity", "2"),
                Void.class, () -> null);

        assertThatThrownBy(() -> executor.execute(
                "transfer-deliver-0001", "TRANSFER_DELIVER", 201L,
                "transfer:901", Map.of("quantity", "3"), Void.class,
                () -> null))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("已被不同的调拨命令使用");

        SecurityContextHolder.setUserId("10");
        SecurityContextHolder.setUserName("other-operator");
        assertThatThrownBy(() -> executor.execute(
                "transfer-deliver-0001", "TRANSFER_DELIVER", 201L,
                "transfer:901", Map.of("quantity", "2"), Void.class,
                () -> null))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("已被不同的调拨命令使用");
    }

    @Test
    @DisplayName("请求标识和安全上下文缺失时默认拒绝")
    void shouldFailClosedForInvalidIdentity()
    {
        assertThatThrownBy(() -> executor.execute("bad id",
                "TRANSFER_SUBMIT", 201L, "transfer:901", null,
                Void.class, () -> null))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("X-Request-Id");

        SecurityContextHolder.remove();
        assertThatThrownBy(() -> executor.execute(
                "transfer-submit-0002", "TRANSFER_SUBMIT", 201L,
                "transfer:901", null, Void.class, () -> null))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("登录用户");
    }

    @Test
    @DisplayName("命令执行器只能加入已存在的业务事务")
    void shouldRequireOuterBusinessTransaction() throws Exception
    {
        Method method = InvTransferCommandExecutor.class.getMethod("execute",
                String.class, String.class, Long.class, String.class,
                Object.class, Class.class, java.util.function.Supplier.class);
        Transactional transactional = method.getAnnotation(
                Transactional.class);

        assertThat(transactional).isNotNull();
        assertThat(transactional.propagation())
                .isEqualTo(Propagation.MANDATORY);
        assertThat(transactional.rollbackFor()).contains(Exception.class);
    }

    private static InvTransferOrder result(long id, String status)
    {
        InvTransferOrder order = new InvTransferOrder();
        order.setTransferId(id);
        order.setStatus(status);
        return order;
    }

    private static final class FakeCommandMapper
            implements InvTransferCommandMapper
    {
        private final Map<String, InvTransferCommand> commands =
                new HashMap<>();

        @Override
        public int insertIfAbsent(InvTransferCommand command)
        {
            if (commands.containsKey(command.getRequestId()))
            {
                return 0;
            }
            command.setCommandId((long) commands.size() + 1L);
            commands.put(command.getRequestId(), command);
            return 1;
        }

        @Override
        public InvTransferCommand selectByRequestIdForUpdate(String requestId)
        {
            return commands.get(requestId);
        }

        @Override
        public int completeIfPending(String requestId,
                String requestFingerprint, String resultType,
                String resultPayload)
        {
            InvTransferCommand command = commands.get(requestId);
            if (command == null
                    || !InvTransferCommandExecutor.STATUS_PENDING.equals(
                            command.getStatus())
                    || !requestFingerprint.equals(
                            command.getRequestFingerprint()))
            {
                return 0;
            }
            command.setStatus(InvTransferCommandExecutor.STATUS_SUCCEEDED);
            command.setResultType(resultType);
            command.setResultPayload(resultPayload);
            return 1;
        }
    }
}
