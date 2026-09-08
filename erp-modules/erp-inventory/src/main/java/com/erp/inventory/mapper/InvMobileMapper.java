package com.erp.inventory.mapper;

import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Param;
import com.erp.inventory.domain.vo.MobileOption;
import com.erp.inventory.domain.vo.MobileTransferApprovalTodo;

public interface InvMobileMapper
{
    List<MobileOption> selectProductOptions(@Param("keyword") String keyword,
            @Param("params") Map<String, Object> params, @Param("limit") int limit);

    List<MobileOption> selectCustomerOptions(@Param("keyword") String keyword,
            @Param("params") Map<String, Object> params, @Param("limit") int limit);

    List<MobileOption> selectSupplierOptions(@Param("keyword") String keyword,
            @Param("params") Map<String, Object> params, @Param("limit") int limit);

    List<MobileTransferApprovalTodo> selectPendingTransferApprovalTasks(@Param("params") Map<String, Object> params,
            @Param("candidateUserId") Long candidateUserId);
}
