package com.erp.oa.attendance.leave.balance;

import java.time.LocalDate;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import com.erp.oa.attendance.leave.balance.AttendanceOvertimeTransferModels.Transfer;

@Mapper
public interface AttendanceOvertimeTransferMapper
{
    Transfer selectTransfer(@Param("id") Long id, @Param("lock") boolean lock);
    Transfer selectRequest(@Param("shopId") Long shopId, @Param("actor") Long actor, @Param("key") String key);
    List<Transfer> selectHistory(@Param("sourceId") Long sourceId);
    List<Transfer> selectBucketSources(@Param("bucketId") Long bucketId);
    int insertTransfer(Transfer transfer);
    int countReversed(@Param("id") Long id);
    int countCurrentOwnership(@Param("userId") Long userId, @Param("shopId") Long shopId, @Param("legalEntityId") Long legalEntityId, @Param("lock") boolean lock);
    int countLaterTransfer(@Param("userId") Long userId, @Param("sourceDate") LocalDate sourceDate);
    int countPublishedSource(@Param("sourceId") Long sourceId);
    int countValidSource(@Param("id") Long id);
}
