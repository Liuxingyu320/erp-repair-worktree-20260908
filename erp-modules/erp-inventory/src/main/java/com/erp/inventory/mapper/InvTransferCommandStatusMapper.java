package com.erp.inventory.mapper;

import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import com.erp.inventory.domain.InvTransferCommand;

public interface InvTransferCommandStatusMapper
{
    @Select("select request_id requestId, actor_user_id actorUserId, "
            + "selected_dept_id selectedDeptId, status from inv_transfer_command "
            + "where request_id = #{requestId}")
    InvTransferCommand selectStatus(@Param("requestId") String requestId);
}
