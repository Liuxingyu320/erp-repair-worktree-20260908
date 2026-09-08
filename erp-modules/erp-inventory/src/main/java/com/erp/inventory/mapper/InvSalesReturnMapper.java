package com.erp.inventory.mapper;

import java.util.List;
import com.erp.inventory.domain.InvSalesReturn;

public interface InvSalesReturnMapper
{
    InvSalesReturn selectInvSalesReturnById(Long returnId);
    InvSalesReturn selectInvSalesReturnByIdForUpdate(Long returnId);
    List<InvSalesReturn> selectInvSalesReturnList(InvSalesReturn salesReturn);
    List<InvSalesReturn> selectMyInvSalesReturnList(InvSalesReturn salesReturn);
    int insertInvSalesReturn(InvSalesReturn salesReturn);
    int updateInvSalesReturn(InvSalesReturn salesReturn);
    int deleteInvSalesReturnByIds(Long[] returnIds);
}
