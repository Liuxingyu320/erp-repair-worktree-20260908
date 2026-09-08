package com.erp.file.drive.controller;

import com.erp.common.core.web.domain.AjaxResult;
import com.erp.common.security.annotation.RequiresPermissions;
import com.erp.file.drive.constant.DriveConstants;
import com.erp.file.drive.domain.DriveActor;
import com.erp.file.drive.domain.dto.DriveQuotaRequest;
import com.erp.file.drive.service.DriveActorResolver;
import com.erp.file.drive.service.DriveFeatureGuard;
import com.erp.file.drive.service.DriveSpaceService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/drive")
public class DriveSpaceController
{
    private final DriveFeatureGuard featureGuard;
    private final DriveActorResolver actorResolver;
    private final DriveSpaceService spaceService;

    public DriveSpaceController(DriveFeatureGuard featureGuard,
            DriveActorResolver actorResolver, DriveSpaceService spaceService)
    {
        this.featureGuard = featureGuard;
        this.actorResolver = actorResolver;
        this.spaceService = spaceService;
    }

    @RequiresPermissions(DriveConstants.PERMISSION_ACCESS)
    @GetMapping("/spaces")
    public AjaxResult spaces()
    {
        featureGuard.requireEnabled();
        DriveActor actor = actorResolver.resolve();
        return AjaxResult.success(spaceService.listVisibleSpaces(actor));
    }

    @RequiresPermissions({DriveConstants.PERMISSION_ACCESS, DriveConstants.PERMISSION_QUOTA_MANAGE})
    @PutMapping("/spaces/{spaceId}/quota")
    public AjaxResult updateQuota(@PathVariable("spaceId") Long spaceId,
            @Valid @RequestBody DriveQuotaRequest request)
    {
        featureGuard.requireEnabled();
        DriveActor actor = actorResolver.resolve();
        return AjaxResult.success(spaceService.updateQuota(spaceId,
                request.getQuotaBytes(), request.getVersion(), actor));
    }
}
