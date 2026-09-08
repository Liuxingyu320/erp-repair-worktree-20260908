package com.erp.file.drive.controller;

import com.erp.common.core.web.controller.BaseController;
import com.erp.common.core.web.domain.AjaxResult;
import com.erp.common.core.web.page.TableDataInfo;
import com.erp.common.security.annotation.RequiresPermissions;
import com.erp.file.drive.constant.DriveConstants;
import com.erp.file.drive.constant.DriveErrorCodes;
import com.erp.file.drive.domain.DriveActor;
import com.erp.file.drive.domain.dto.DriveFolderCreateRequest;
import com.erp.file.drive.domain.dto.DriveMoveRequest;
import com.erp.file.drive.domain.dto.DriveRenameRequest;
import com.erp.file.drive.domain.vo.DriveNodeVo;
import com.erp.file.drive.exception.DriveException;
import com.erp.file.drive.service.DriveActorResolver;
import com.erp.file.drive.service.DriveFeatureGuard;
import com.erp.file.drive.service.DriveNodeService;
import com.erp.file.drive.service.DriveTrashService;
import com.erp.file.drive.service.DriveUploadService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.springframework.validation.annotation.Validated;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@Validated
@RestController
@RequestMapping("/drive")
public class DriveNodeController extends BaseController
{
    private final DriveFeatureGuard featureGuard;
    private final DriveActorResolver actorResolver;
    private final DriveNodeService nodeService;
    private final DriveUploadService uploadService;
    private final DriveTrashService trashService;

    public DriveNodeController(DriveFeatureGuard featureGuard, DriveActorResolver actorResolver,
            DriveNodeService nodeService, DriveUploadService uploadService,
            DriveTrashService trashService)
    {
        this.featureGuard = featureGuard;
        this.actorResolver = actorResolver;
        this.nodeService = nodeService;
        this.uploadService = uploadService;
        this.trashService = trashService;
    }

    @RequiresPermissions(DriveConstants.PERMISSION_ACCESS)
    @GetMapping("/nodes")
    public TableDataInfo list(
            @RequestParam @NotNull @Positive Long spaceId,
            @RequestParam(defaultValue = "0") @NotNull @Min(0) Long parentId,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "updated") String sortField,
            @RequestParam(defaultValue = "desc") String sortDirection,
            @RequestParam(defaultValue = "1") @NotNull @Min(1) Integer pageNum,
            @RequestParam(defaultValue = "50") @NotNull @Min(1) @Max(100) Integer pageSize)
    {
        featureGuard.requireEnabled();
        DriveActor actor = actorResolver.resolve();
        return getDataTable(nodeService.listPaged(spaceId, parentId, keyword,
                sortField, sortDirection, actor, pageNum, pageSize));
    }

    @RequiresPermissions(DriveConstants.PERMISSION_ACCESS)
    @GetMapping("/nodes/{nodeId}")
    public AjaxResult detail(@PathVariable("nodeId") @NotNull @Positive Long nodeId)
    {
        featureGuard.requireEnabled();
        DriveActor actor = actorResolver.resolve();
        return AjaxResult.success(nodeService.detail(nodeId, actor));
    }

    @RequiresPermissions(DriveConstants.PERMISSION_ACCESS)
    @PostMapping("/folders")
    public AjaxResult createFolder(@Valid @RequestBody DriveFolderCreateRequest request)
    {
        featureGuard.requireEnabled();
        DriveActor actor = actorResolver.resolve();
        return AjaxResult.success(nodeService.createFolder(request, actor));
    }

    @RequiresPermissions(DriveConstants.PERMISSION_ACCESS)
    @PostMapping(value = "/files", consumes = "multipart/form-data")
    public AjaxResult upload(@RequestPart("file") @NotNull MultipartFile file,
            @RequestParam @NotNull @Positive Long spaceId,
            @RequestParam(defaultValue = "0") @NotNull @Min(0) Long parentId)
    {
        featureGuard.requireEnabled();
        DriveActor actor = actorResolver.resolve();
        if (file.isEmpty())
        {
            throw new DriveException(DriveErrorCodes.DRIVE_FILE_TYPE_REJECTED, "文件不能为空");
        }
        return AjaxResult.success(uploadService.upload(file, spaceId, parentId, actor));
    }

    @RequiresPermissions(DriveConstants.PERMISSION_ACCESS)
    @PutMapping("/nodes/{nodeId}/name")
    public AjaxResult rename(@PathVariable("nodeId") @NotNull @Positive Long nodeId,
            @Valid @RequestBody DriveRenameRequest request)
    {
        featureGuard.requireEnabled();
        DriveActor actor = actorResolver.resolve();
        return AjaxResult.success(nodeService.rename(nodeId, request, actor));
    }

    @RequiresPermissions(DriveConstants.PERMISSION_ACCESS)
    @PutMapping("/nodes/{nodeId}/move")
    public AjaxResult move(@PathVariable("nodeId") @NotNull @Positive Long nodeId,
            @Valid @RequestBody DriveMoveRequest request)
    {
        featureGuard.requireEnabled();
        DriveActor actor = actorResolver.resolve();
        return AjaxResult.success(nodeService.move(nodeId, request, actor));
    }

    @RequiresPermissions(DriveConstants.PERMISSION_ACCESS)
    @GetMapping("/recent")
    public AjaxResult recent(
            @RequestParam(defaultValue = "20") @NotNull @Min(1) @Max(50) Integer limit)
    {
        featureGuard.requireEnabled();
        DriveActor actor = actorResolver.resolve();
        return AjaxResult.success(nodeService.recent(actor, limit));
    }

    @RequiresPermissions(DriveConstants.PERMISSION_ACCESS)
    @DeleteMapping("/nodes/{nodeId}")
    public AjaxResult trash(@PathVariable("nodeId") @NotNull @Positive Long nodeId,
            @RequestParam @NotNull @Min(0) Integer version)
    {
        featureGuard.requireEnabled();
        DriveActor actor = actorResolver.resolve();
        trashService.trash(nodeId, version, actor);
        return AjaxResult.success("已移入回收站");
    }

    @RequiresPermissions(DriveConstants.PERMISSION_ACCESS)
    @GetMapping("/trash")
    public AjaxResult listTrash(@RequestParam @NotNull @Positive Long spaceId)
    {
        featureGuard.requireEnabled();
        DriveActor actor = actorResolver.resolve();
        return AjaxResult.success(trashService.listTrash(spaceId, actor));
    }

    @RequiresPermissions(DriveConstants.PERMISSION_ACCESS)
    @PostMapping("/trash/{trashRootId}/restore")
    public AjaxResult restore(
            @PathVariable("trashRootId") @NotNull @Positive Long trashRootId)
    {
        featureGuard.requireEnabled();
        DriveActor actor = actorResolver.resolve();
        DriveNodeVo restored = trashService.restore(trashRootId, actor);
        String message = restored.parentId() != null
                && restored.parentId() == DriveConstants.ROOT_PARENT_ID
                ? "恢复成功，文件已恢复到根目录" : "恢复成功";
        return AjaxResult.success(message, restored);
    }

    @RequiresPermissions(DriveConstants.PERMISSION_ACCESS)
    @DeleteMapping("/trash/{trashRootId}")
    public ResponseEntity<AjaxResult> purge(
            @PathVariable("trashRootId") @NotNull @Positive Long trashRootId)
    {
        featureGuard.requireEnabled();
        DriveActor actor = actorResolver.resolve();
        trashService.requestPurge(trashRootId, actor);
        AjaxResult body = AjaxResult.success("清理请求已受理")
                .put("trashRootId", trashRootId);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(body);
    }

    @RequiresPermissions(DriveConstants.PERMISSION_ACCESS)
    @DeleteMapping(value = "/trash", params = "spaceId")
    public ResponseEntity<AjaxResult> emptyTrash(
            @RequestParam @NotNull @Positive Long spaceId)
    {
        featureGuard.requireEnabled();
        DriveActor actor = actorResolver.resolve();
        int submitted = trashService.requestEmptyTrash(spaceId, actor);
        AjaxResult body = AjaxResult.success("清空回收站请求已受理")
                .put("submitted", submitted);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(body);
    }
}
