package com.erp.inventory.mapper;

import java.util.Date;
import java.util.List;
import org.apache.ibatis.annotations.Param;
import com.erp.inventory.domain.InvCustomerServiceChangeLog;
import com.erp.inventory.domain.InvCustomerServiceProfile;
import com.erp.inventory.domain.InvCustomerServiceRecord;
import com.erp.inventory.domain.vo.InvCustomerOptionVo;
import com.erp.inventory.domain.vo.InvCustomerServiceCardQuery;
import com.erp.inventory.domain.vo.InvCustomerServiceCardVo;
import com.erp.inventory.domain.vo.InvCustomerServiceAuditQuery;
import com.erp.inventory.domain.vo.InvCustomerServiceAuditVo;

public interface InvCustomerServiceCardMapper
{
    List<InvCustomerOptionVo> selectOptions(@Param("shopDeptId") Long shopDeptId,
            @Param("keyword") String keyword);
    List<InvCustomerServiceCardVo> selectCardList(InvCustomerServiceCardQuery query);
    List<InvCustomerServiceAuditVo> selectAuditList(InvCustomerServiceAuditQuery query);
    InvCustomerServiceCardVo selectCardById(Long customerId);
    int countProfile(Long customerId);
    int insertProfile(InvCustomerServiceProfile profile);
    int updateProfile(InvCustomerServiceProfile profile);
    int bumpProfileVersion(@Param("customerId") Long customerId,
            @Param("version") Long version, @Param("updateBy") String updateBy);
    int updateCustomerCore(@Param("customerId") Long customerId,
            @Param("customerName") String customerName,
            @Param("customerCode") String customerCode,
            @Param("contactPerson") String contactPerson,
            @Param("contactPhone") String contactPhone,
            @Param("updateBy") String updateBy);
    int archiveCustomer(@Param("customerId") Long customerId,
            @Param("updateBy") String updateBy);
    int insertRecord(InvCustomerServiceRecord record);
    Long selectRecordIdByRequestKey(@Param("customerId") Long customerId,
            @Param("requestKey") String requestKey);
    List<InvCustomerServiceRecord> selectRecords(Long customerId);
    int touchLastVisit(@Param("customerId") Long customerId,
            @Param("serviceDate") Date serviceDate,
            @Param("updateBy") String updateBy);
    Long selectChangeLogIdByRequestKey(@Param("customerId") Long customerId,
            @Param("requestKey") String requestKey);
    Long selectCreatedCustomerIdByRequestKey(
            @Param("shopDeptId") Long shopDeptId,
            @Param("requestKey") String requestKey);
    int insertChangeLog(InvCustomerServiceChangeLog log);
}
