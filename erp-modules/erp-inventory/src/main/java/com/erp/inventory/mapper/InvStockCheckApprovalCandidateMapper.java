package com.erp.inventory.mapper;

import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Param;

public interface InvStockCheckApprovalCandidateMapper
{
    List<Map<String, Object>> selectOperationsDirectorCandidates(@Param("deptId") Long deptId);
}
