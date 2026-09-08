package com.erp.inventory.service.impl;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import org.junit.jupiter.api.*;
import com.erp.common.core.context.SecurityContextHolder;
import com.erp.inventory.domain.InvTransferCommand;
import com.erp.inventory.mapper.InvTransferCommandStatusMapper;

class InvTransferCommandStatusServiceTest
{
    private final InvTransferCommandStatusMapper mapper = mock(InvTransferCommandStatusMapper.class);
    private final InventoryShopScopeService scope = mock(InventoryShopScopeService.class);
    private final InvTransferCommandStatusService service = new InvTransferCommandStatusService(mapper, scope);
    private final String id = "transfer:test-status-1234";
    @BeforeEach void setup() { SecurityContextHolder.setUserId("11"); when(scope.resolveRequiredShopDept(201L)).thenReturn(201L); }
    @AfterEach void clear() { SecurityContextHolder.remove(); }
    private InvTransferCommand command(long actor, long dept, String status)
    {
        InvTransferCommand value = new InvTransferCommand(); value.setActorUserId(actor);
        value.setSelectedDeptId(dept); value.setStatus(status); return value;
    }
    @Test void returnsOnlyOwnedOutcome()
    {
        when(mapper.selectStatus(id)).thenReturn(command(11, 201, "SUCCEEDED"));
        assertThat(service.status(id, 201L).status()).isEqualTo("SUCCEEDED");
    }
    @Test void hidesAnotherUserAndAnotherOrganization()
    {
        when(mapper.selectStatus(id)).thenReturn(command(12, 201, "SUCCEEDED"));
        assertThat(service.status(id, 201L).status()).isEqualTo("NOT_FOUND");
        when(mapper.selectStatus(id)).thenReturn(command(11, 202, "SUCCEEDED"));
        assertThat(service.status(id, 201L).status()).isEqualTo("NOT_FOUND");
    }
    @Test void unknownAndPendingStayDistinct()
    {
        assertThat(service.status(id, 201L).status()).isEqualTo("NOT_FOUND");
        when(mapper.selectStatus(id)).thenReturn(command(11, 201, "PROCESSING"));
        assertThat(service.status(id, 201L).status()).isEqualTo("PENDING");
    }
    @Test void malformedIdsAndOutOfScopeRequestsNeverReadCommands()
    {
        assertThatThrownBy(() -> service.status("bad", 201L)).isInstanceOf(RuntimeException.class);
        when(scope.resolveRequiredShopDept(201L)).thenThrow(new IllegalStateException("scope denied"));
        assertThatThrownBy(() -> service.status(id, 201L)).hasMessageContaining("scope denied");
        verifyNoInteractions(mapper);
    }
}
