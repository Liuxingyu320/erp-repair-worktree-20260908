package com.erp.file.drive.controller;

import java.nio.charset.StandardCharsets;
import com.erp.common.security.annotation.RequiresPermissions;
import com.erp.file.drive.constant.DriveConstants;
import com.erp.file.drive.constant.DriveErrorCodes;
import com.erp.file.drive.domain.DriveActor;
import com.erp.file.drive.domain.vo.DriveContent;
import com.erp.file.drive.exception.DriveException;
import com.erp.file.drive.service.DriveActorResolver;
import com.erp.file.drive.service.DriveContentService;
import com.erp.file.drive.service.DriveFeatureGuard;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/drive")
public class DriveContentController
{
    private static final String NOSNIFF = "X-Content-Type-Options";

    private final DriveFeatureGuard featureGuard;
    private final DriveActorResolver actorResolver;
    private final DriveContentService contentService;

    public DriveContentController(DriveFeatureGuard featureGuard,
            DriveActorResolver actorResolver, DriveContentService contentService)
    {
        this.featureGuard = featureGuard;
        this.actorResolver = actorResolver;
        this.contentService = contentService;
    }

    @RequiresPermissions(DriveConstants.PERMISSION_ACCESS)
    @GetMapping("/nodes/{nodeId}/content")
    public ResponseEntity<Resource> content(
            @PathVariable("nodeId") @NotNull @Positive Long nodeId,
            @RequestParam("mode") String mode)
    {
        featureGuard.requireEnabled();
        requireMode(mode);
        DriveActor actor = actorResolver.resolve();
        DriveContent content = contentService.resolve(nodeId, mode, actor);

        ContentDisposition disposition = (content.inline()
                ? ContentDisposition.inline() : ContentDisposition.attachment())
                .filename(safeFileName(content.fileName()), StandardCharsets.UTF_8)
                .build();
        HttpHeaders headers = new HttpHeaders();
        headers.setContentDisposition(disposition);
        headers.setContentLength(content.size());
        headers.setContentType(safeMediaType(content.contentType()));
        headers.setCacheControl("no-store");
        headers.setPragma("no-cache");
        headers.set(NOSNIFF, "nosniff");
        return ResponseEntity.ok().headers(headers).body(content.resource());
    }

    private static void requireMode(String mode)
    {
        if (!"preview".equals(mode) && !"download".equals(mode))
        {
            throw new DriveException(DriveErrorCodes.DRIVE_PREVIEW_UNSUPPORTED,
                    "不支持的文件内容访问模式");
        }
    }

    private static MediaType safeMediaType(String contentType)
    {
        if (contentType == null || contentType.isBlank())
        {
            return MediaType.APPLICATION_OCTET_STREAM;
        }
        try
        {
            return MediaType.parseMediaType(contentType);
        }
        catch (IllegalArgumentException ex)
        {
            return MediaType.APPLICATION_OCTET_STREAM;
        }
    }

    private static String safeFileName(String fileName)
    {
        if (fileName == null || fileName.isBlank())
        {
            return "download";
        }
        StringBuilder safe = new StringBuilder(Math.min(fileName.length(), 255));
        fileName.codePoints().filter(codePoint -> !Character.isISOControl(codePoint))
                .forEach(codePoint -> {
                    if (safe.length() + Character.charCount(codePoint) <= 255)
                    {
                        safe.appendCodePoint(codePoint);
                    }
                });
        return safe.isEmpty() ? "download" : safe.toString();
    }
}
