package com.erp.inventory.mapper;

import java.math.BigDecimal;
import java.util.List;
import org.apache.ibatis.annotations.Param;
import com.erp.inventory.domain.InvStock;
import com.erp.inventory.domain.InvTransferReservation;

/** Persistence boundary for transfer-owned stock reservation mutations. */
public interface InvTransferReservationMapper
{
    Long selectWarehouseReplenishmentRouteForUpdate(
            @Param("sourceWarehouseId") Long sourceWarehouseId,
            @Param("targetStoreId") Long targetStoreId);

    InvStock selectStockForUpdate(@Param("itemType") String itemType,
            @Param("itemId") Long itemId,
            @Param("locationDeptId") Long locationDeptId,
            @Param("warehouseReplenishment") boolean warehouseReplenishment);

    InvStock selectStockForPlanning(@Param("itemType") String itemType,
            @Param("itemId") Long itemId,
            @Param("locationDeptId") Long locationDeptId);

    InvStock selectStockByIdForUpdate(@Param("stockId") Long stockId);

    List<InvStock> selectStocksByIdsForUpdate(
            @Param("stockIds") List<Long> stockIds);

    int reserveStock(@Param("stockId") Long stockId,
            @Param("version") Long version,
            @Param("quantity") BigDecimal quantity,
            @Param("updateBy") String updateBy);

    int releaseStock(@Param("stockId") Long stockId,
            @Param("version") Long version,
            @Param("quantity") BigDecimal quantity,
            @Param("updateBy") String updateBy);

    int consumeReservedStockWithCost(@Param("stockId") Long stockId,
            @Param("version") Long version,
            @Param("quantity") BigDecimal quantity,
            @Param("deductCost") BigDecimal deductCost,
            @Param("updateBy") String updateBy);

    List<InvTransferReservation> selectByTransferRoundForUpdate(
            @Param("transferId") Long transferId,
            @Param("reservationRound") Integer reservationRound);

    InvTransferReservation selectByTransferDetailRoundForUpdate(
            @Param("transferId") Long transferId,
            @Param("transferDetailId") Long transferDetailId,
            @Param("reservationRound") Integer reservationRound);

    int insertReservation(InvTransferReservation reservation);

    int increaseReservation(@Param("reservationId") Long reservationId,
            @Param("version") Long version,
            @Param("quantity") BigDecimal quantity,
            @Param("updateBy") String updateBy);

    int consumeReservation(@Param("reservationId") Long reservationId,
            @Param("version") Long version,
            @Param("quantity") BigDecimal quantity,
            @Param("updateBy") String updateBy);

    int releaseReservation(@Param("reservationId") Long reservationId,
            @Param("version") Long version,
            @Param("quantity") BigDecimal quantity,
            @Param("updateBy") String updateBy);
}
