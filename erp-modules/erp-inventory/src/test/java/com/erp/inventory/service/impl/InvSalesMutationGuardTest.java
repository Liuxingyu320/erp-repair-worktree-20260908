package com.erp.inventory.service.impl;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.erp.common.core.exception.ServiceException;
import com.erp.inventory.domain.InvSalesOrder;
import com.erp.inventory.mapper.InvSalesOrderMapper;
import org.junit.jupiter.api.Test;

class InvSalesMutationGuardTest
{
    @Test void failedCompareAndSwapLeavesTheLockedSnapshotUnchanged()
    {
        InvSalesOrder locked = new InvSalesOrder(); locked.setOrderId(7L); locked.setVersion(3L); locked.setStatus("submitted");
        InvSalesOrder update = new InvSalesOrder(); update.setOrderId(7L); update.setStatus("cancelled");
        update.getParams().put("expectedStatus", "forged"); update.setVersion(99L);
        InvSalesOrderMapper mapper = mock(InvSalesOrderMapper.class);
        assertThatThrownBy(() -> InvSalesMutationGuard.update(mapper, update, locked)).isInstanceOf(ServiceException.class);
        assertThat(update.getVersion()).isEqualTo(3L); assertThat(update.getParams().get("expectedStatus")).isEqualTo("submitted");
        assertThat(locked.getVersion()).isEqualTo(3L); assertThat(locked.getStatus()).isEqualTo("submitted");
    }
}
