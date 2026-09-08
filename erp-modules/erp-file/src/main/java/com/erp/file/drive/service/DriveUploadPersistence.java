package com.erp.file.drive.service;

import java.util.Objects;
import com.erp.file.drive.config.DriveProperties;
import com.erp.file.drive.constant.DriveConstants;
import com.erp.file.drive.constant.DriveErrorCodes;
import com.erp.file.drive.domain.DriveCapacityConfig;
import com.erp.file.drive.domain.DriveNode;
import com.erp.file.drive.domain.DriveUploadReservation;
import com.erp.file.drive.exception.DriveException;
import com.erp.file.drive.mapper.DriveNodeMapper;
import com.erp.file.drive.mapper.DriveUploadReservationMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 上传元数据的独立事务边界，供外层在提交失败后补偿物理对象。
 */
@Service
public class DriveUploadPersistence
{
    private final DriveNodeMapper nodeMapper;
    private final DriveQuotaService quotaService;
    private final DriveUploadReservationMapper reservationMapper;
    private final DriveCapacityService capacityService;
    private final DriveProperties properties;

    public DriveUploadPersistence(DriveNodeMapper nodeMapper, DriveQuotaService quotaService,
            DriveUploadReservationMapper reservationMapper,
            DriveCapacityService capacityService, DriveProperties properties)
    {
        this.nodeMapper = nodeMapper;
        this.quotaService = quotaService;
        this.reservationMapper = reservationMapper;
        this.capacityService = capacityService;
        this.properties = properties;
    }

    @Transactional
    public DriveNode persist(DriveNode node, long bytes)
    {
        return persist(node, bytes, null);
    }

    @Transactional
    public DriveNode persist(DriveNode node, long bytes, String reservationId)
    {
        DriveUploadReservation reservation = null;
        if (reservationId != null)
        {
            DriveCapacityConfig capacity = capacityService.lockForAllocationChange();
            reservation = reservationMapper.selectForUpdate(reservationId);
            requireMatchingReservation(reservation, node, bytes);
            capacityService.requireCurrentAccountedUsageWithinCapacity(capacity);
        }
        else if (properties.isCapacityReservationEnabled())
        {
            throw unavailable("上传容量预占不存在，请重新上传");
        }
        if (node.getParentId() != DriveConstants.ROOT_PARENT_ID)
        {
            DriveNode parent = nodeMapper.selectByIdForUpdate(node.getParentId());
            DriveNodeService.requireActiveFolderInSpace(parent, node.getSpaceId());
            node.setAncestors(DriveNodeService.childAncestors(parent));
        }
        else
        {
            node.setAncestors("0");
        }
        quotaService.reserve(node.getSpaceId(), bytes);
        if (nodeMapper.insertNode(node) != 1)
        {
            throw new DriveException(DriveErrorCodes.DRIVE_CONCURRENT_MODIFICATION,
                    "上传元数据未能保存，请重试");
        }
        if (reservation != null && reservationMapper.deleteExpected(
                reservation.getReservationId(), value(reservation.getVersion()),
                DriveConstants.RESERVATION_RESERVED) != 1)
        {
            throw unavailable("上传容量预占未能提交，请重新上传");
        }
        return node;
    }

    private static void requireMatchingReservation(DriveUploadReservation reservation,
            DriveNode node, long bytes)
    {
        if (reservation == null
                || !DriveConstants.RESERVATION_RESERVED.equals(reservation.getStatus())
                || !Objects.equals(reservation.getSpaceId(), node.getSpaceId())
                || !Objects.equals(reservation.getStorageKey(), node.getStorageKey())
                || reservation.getReservedBytes() == null
                || reservation.getReservedBytes() != bytes)
        {
            throw unavailable("上传容量预占无效，请重新上传");
        }
    }

    private static int value(Integer value)
    {
        return value == null ? 0 : value;
    }

    private static DriveException unavailable(String message)
    {
        return new DriveException(DriveErrorCodes.DRIVE_STORAGE_UNAVAILABLE, message);
    }
}
