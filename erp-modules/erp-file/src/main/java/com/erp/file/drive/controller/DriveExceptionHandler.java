package com.erp.file.drive.controller;

import java.util.Map;
import com.erp.common.core.web.domain.AjaxResult;
import com.erp.file.drive.constant.DriveErrorCodes;
import com.erp.file.drive.exception.DriveException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(basePackages = "com.erp.file.drive.controller")
public class DriveExceptionHandler
{
    private static final Map<String, HttpStatus> STATUS_BY_CODE = Map.ofEntries(
            Map.entry(DriveErrorCodes.DRIVE_ACCESS_DENIED, HttpStatus.FORBIDDEN),
            Map.entry(DriveErrorCodes.DRIVE_SPACE_NOT_FOUND, HttpStatus.NOT_FOUND),
            Map.entry(DriveErrorCodes.DRIVE_NODE_NOT_FOUND, HttpStatus.NOT_FOUND),
            Map.entry(DriveErrorCodes.DRIVE_STORAGE_OBJECT_MISSING, HttpStatus.NOT_FOUND),
            Map.entry(DriveErrorCodes.DRIVE_NAME_CONFLICT, HttpStatus.CONFLICT),
            Map.entry(DriveErrorCodes.DRIVE_INVALID_MOVE, HttpStatus.CONFLICT),
            Map.entry(DriveErrorCodes.DRIVE_CONCURRENT_MODIFICATION, HttpStatus.CONFLICT),
            Map.entry(DriveErrorCodes.DRIVE_IMPACT_STALE, HttpStatus.CONFLICT),
            Map.entry(DriveErrorCodes.DRIVE_POLICY_NOT_FOUND, HttpStatus.NOT_FOUND),
            Map.entry(DriveErrorCodes.DRIVE_ORG_CONFIG_NOT_FOUND, HttpStatus.NOT_FOUND),
            Map.entry(DriveErrorCodes.DRIVE_POLICY_INVALID,
                    HttpStatus.UNPROCESSABLE_ENTITY),
            Map.entry(DriveErrorCodes.DRIVE_FILE_TOO_LARGE, HttpStatus.PAYLOAD_TOO_LARGE),
            Map.entry(DriveErrorCodes.DRIVE_FILE_TYPE_REJECTED,
                    HttpStatus.UNPROCESSABLE_ENTITY),
            Map.entry(DriveErrorCodes.DRIVE_PREVIEW_UNSUPPORTED,
                    HttpStatus.UNPROCESSABLE_ENTITY),
            Map.entry(DriveErrorCodes.DRIVE_QUOTA_EXCEEDED, HttpStatus.INSUFFICIENT_STORAGE),
            Map.entry(DriveErrorCodes.DRIVE_CAPACITY_EXCEEDED,
                    HttpStatus.INSUFFICIENT_STORAGE),
            Map.entry(DriveErrorCodes.DRIVE_ORG_BUDGET_EXCEEDED,
                    HttpStatus.INSUFFICIENT_STORAGE),
            Map.entry(DriveErrorCodes.DRIVE_STORAGE_UNAVAILABLE,
                    HttpStatus.SERVICE_UNAVAILABLE),
            Map.entry(DriveErrorCodes.DRIVE_DISABLED, HttpStatus.SERVICE_UNAVAILABLE));

    @ExceptionHandler(DriveException.class)
    public ResponseEntity<AjaxResult> handleDriveException(DriveException exception)
    {
        AjaxResult body = AjaxResult.error(exception.getMessage())
                .put("businessCode", exception.getBusinessCode());
        return ResponseEntity.status(statusFor(exception.getBusinessCode()))
                .contentType(MediaType.APPLICATION_JSON)
                .body(body);
    }

    private static HttpStatus statusFor(String businessCode)
    {
        return STATUS_BY_CODE.getOrDefault(businessCode, HttpStatus.INTERNAL_SERVER_ERROR);
    }
}
