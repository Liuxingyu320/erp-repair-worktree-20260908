package com.erp.file.drive.controller;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import com.erp.common.core.domain.R;
import com.erp.common.security.annotation.InnerAuth;
import com.erp.file.drive.constant.DriveErrorCodes;
import com.erp.file.drive.domain.DriveActor;
import com.erp.file.drive.domain.vo.DriveContent;
import com.erp.file.drive.exception.DriveException;
import com.erp.file.drive.service.DriveActorResolver;
import com.erp.file.drive.service.DriveContentService;
import com.erp.file.drive.service.DriveFeatureGuard;
import com.erp.system.api.domain.DriveBusinessFile;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 仅供业务服务二次授权后调用的受控文件入口。
 */
@RestController
@RequestMapping("/drive/inner/nodes")
public class DriveBusinessContentController
{
    private static final String NOSNIFF = "X-Content-Type-Options";
    private static final String HEALTH_CERTIFICATE = "HEALTH_CERTIFICATE";
    private static final String CUSTOMER_PHOTO = "CUSTOMER_PHOTO";

    private final DriveFeatureGuard featureGuard;
    private final DriveActorResolver actorResolver;
    private final DriveContentService contentService;

    public DriveBusinessContentController(DriveFeatureGuard featureGuard,
            DriveActorResolver actorResolver,
            DriveContentService contentService)
    {
        this.featureGuard = featureGuard;
        this.actorResolver = actorResolver;
        this.contentService = contentService;
    }

    @InnerAuth(isUser = true)
    @GetMapping("/{nodeId}/binding")
    public R<DriveBusinessFile> validateBinding(@PathVariable Long nodeId,
            @RequestParam String usage)
    {
        featureGuard.requireEnabled();
        DriveBusinessFile file = contentService.validateBusinessBinding(
                nodeId, actorResolver.resolve());
        requireAllowedUsage(file, usage);
        return R.ok(file);
    }

    @InnerAuth(isUser = true)
    @GetMapping("/{nodeId}/content")
    public ResponseEntity<Resource> content(@PathVariable Long nodeId,
            @RequestParam String mode)
    {
        featureGuard.requireEnabled();
        requireMode(mode);
        DriveActor actor = actorResolver.resolve();
        DriveContent content = contentService.resolveBusiness(nodeId, mode,
                actor);

        ContentDisposition disposition = (content.inline()
                ? ContentDisposition.inline() : ContentDisposition.attachment())
                .filename(safeFileName(content.fileName()),
                        StandardCharsets.UTF_8)
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

    private static void requireAllowedUsage(DriveBusinessFile file,
            String usage)
    {
        String normalized = usage == null ? ""
                : usage.trim().toUpperCase(Locale.ROOT);
        String type = file == null || file.getContentType() == null ? ""
                : file.getContentType().toLowerCase(Locale.ROOT);
        boolean image = type.startsWith("image/")
                && !"image/svg+xml".equals(type);
        boolean allowed = CUSTOMER_PHOTO.equals(normalized) ? image
                : HEALTH_CERTIFICATE.equals(normalized)
                        && (image || "application/pdf".equals(type));
        if (!allowed)
        {
            throw new DriveException(DriveErrorCodes.DRIVE_FILE_TYPE_REJECTED,
                    CUSTOMER_PHOTO.equals(normalized)
                            ? "客户照片只支持安全图片格式"
                            : "健康证附件只支持安全图片或PDF");
        }
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
        try
        {
            return contentType == null || contentType.isBlank()
                    ? MediaType.APPLICATION_OCTET_STREAM
                    : MediaType.parseMediaType(contentType);
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
        StringBuilder safe = new StringBuilder(
                Math.min(fileName.length(), 255));
        fileName.codePoints().filter(value -> !Character.isISOControl(value))
                .forEach(value -> {
                    if (safe.length() + Character.charCount(value) <= 255)
                    {
                        safe.appendCodePoint(value);
                    }
                });
        return safe.isEmpty() ? "download" : safe.toString();
    }
}
