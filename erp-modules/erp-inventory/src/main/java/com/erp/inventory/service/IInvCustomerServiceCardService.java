package com.erp.inventory.service;

import java.util.List;
import com.erp.inventory.domain.dto.InvCustomerServiceCardArchiveRequest;
import com.erp.inventory.domain.dto.InvCustomerServiceCardSaveRequest;
import com.erp.inventory.domain.dto.InvCustomerServiceRecordRequest;
import com.erp.inventory.domain.vo.InvCustomerOptionVo;
import com.erp.inventory.domain.vo.InvCustomerServiceCardQuery;
import com.erp.inventory.domain.vo.InvCustomerServiceCardVo;
import com.erp.inventory.domain.vo.InvCustomerServiceAuditQuery;
import com.erp.inventory.domain.vo.InvCustomerServiceAuditVo;

public interface IInvCustomerServiceCardService
{
    List<InvCustomerOptionVo> selectOptions(String keyword, Long selectedShopDeptId);
    List<InvCustomerServiceCardVo> selectList(InvCustomerServiceCardQuery query,
            Long selectedShopDeptId, int pageNum, int pageSize);
    boolean isWriteEnabled(Long selectedShopDeptId);
    List<InvCustomerServiceAuditVo> selectAuditList(InvCustomerServiceAuditQuery query,
            Long selectedShopDeptId, int pageNum, int pageSize);
    InvCustomerServiceCardVo selectById(Long customerId, Long selectedShopDeptId);
    InvCustomerServiceCardVo create(InvCustomerServiceCardSaveRequest request, Long selectedShopDeptId);
    InvCustomerServiceCardVo update(Long customerId, InvCustomerServiceCardSaveRequest request,
            Long selectedShopDeptId);
    InvCustomerServiceCardVo addRecord(Long customerId, InvCustomerServiceRecordRequest request,
            Long selectedShopDeptId);
    InvCustomerServiceCardVo archive(Long customerId, InvCustomerServiceCardArchiveRequest request,
            Long selectedShopDeptId);
    Long resolvePhotoNode(Long customerId, Long selectedShopDeptId);
}
