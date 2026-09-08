package com.erp.oa.attendance.service;

import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.multipart.MultipartFile;
import com.erp.common.core.exception.ServiceException;
import com.erp.oa.attendance.config.AttendanceV2Properties;
import com.erp.oa.attendance.config.AttendanceRuntimePolicy;
import com.erp.oa.attendance.support.AttendanceExifOrientation;
import com.erp.oa.attendance.support.AttendanceWatermarkFontResolver;

@Service
public class AttendanceEvidenceStorageService
{
    private final AttendanceV2Properties properties;
    private final AttendanceRuntimePolicy runtimePolicy;
    private final AttendanceWatermarkFontResolver fontResolver;
    private final Path root;
    private final Path tempRoot;

    @Autowired
    public AttendanceEvidenceStorageService(AttendanceV2Properties properties,
            AttendanceRuntimePolicy runtimePolicy,
            AttendanceWatermarkFontResolver fontResolver)
    {
        this.properties = properties;
        this.runtimePolicy = runtimePolicy;
        this.fontResolver = fontResolver;
        root = Paths.get(properties.getStorageRoot()).toAbsolutePath()
                .normalize();
        tempRoot = Paths.get(properties.getTempRoot()).toAbsolutePath()
                .normalize();
    }

    AttendanceEvidenceStorageService(AttendanceV2Properties properties)
    {
        this.properties = properties;
        this.runtimePolicy = null;
        this.fontResolver = new AttendanceWatermarkFontResolver();
        root = Paths.get(properties.getStorageRoot()).toAbsolutePath()
                .normalize();
        tempRoot = Paths.get(properties.getTempRoot()).toAbsolutePath()
                .normalize();
    }

    public StoredEvidence store(String eventNo, LocalDate businessDate,
            MultipartFile photo, List<String> watermarkLines)
    {
        if (eventNo == null || !eventNo.matches("[A-Z0-9]{10,40}"))
        {
            throw new ServiceException("ATTENDANCE_EVENT_NO_INVALID");
        }
        if (photo == null || photo.isEmpty())
        {
            throw new ServiceException("PHOTO_REQUIRED");
        }
        if (photo.getSize() <= 0
                || photo.getSize() > maxPhotoBytes())
        {
            throw new ServiceException("PHOTO_SIZE_INVALID");
        }
        byte[] source;
        try
        {
            source = photo.getBytes();
        }
        catch (IOException ex)
        {
            throw failure("PHOTO_READ_FAILED", ex);
        }
        Dimension encodedDimension = inspect(source);
        int orientation = AttendanceExifOrientation.read(source);
        Dimension dimension = AttendanceExifOrientation.swapsDimensions(
                orientation)
                        ? new Dimension(encodedDimension.height,
                                encodedDimension.width)
                        : encodedDimension;
        if (dimension.width < properties.getMinImageWidth()
                || dimension.height < properties.getMinImageHeight()
                || (long) dimension.width * dimension.height
                > properties.getMaxImagePixels())
        {
            throw new ServiceException("PHOTO_DIMENSION_INVALID");
        }
        BufferedImage decoded = decode(source);
        BufferedImage oriented = AttendanceExifOrientation.apply(decoded,
                orientation);
        if (oriented.getWidth() != dimension.width
                || oriented.getHeight() != dimension.height)
            throw new ServiceException("PHOTO_DIMENSION_INVALID");
        BufferedImage clean = flatten(oriented);
        byte[] cleanBytes = encodeJpeg(clean);
        BufferedImage marked = watermark(clean, watermarkLines);
        byte[] markedBytes = encodeJpeg(marked);

        LocalDate date = businessDate == null ? LocalDate.now() : businessDate;
        Path relativeDir = Paths.get(String.valueOf(date.getYear()),
                String.format("%02d", date.getMonthValue()),
                String.format("%02d", date.getDayOfMonth()));
        Path originalRelative = relativeDir.resolve(eventNo + "-original.jpg");
        Path markedRelative = relativeDir.resolve(eventNo + "-watermarked.jpg");
        Path original = target(originalRelative);
        Path watermarked = target(markedRelative);
        Path stagedOriginal = null;
        Path stagedMarked = null;
        try
        {
            Files.createDirectories(tempRoot);
            Files.createDirectories(original.getParent());
            stagedOriginal = Files.createTempFile(tempRoot, "attendance-", ".jpg");
            stagedMarked = Files.createTempFile(tempRoot, "attendance-", ".jpg");
            Files.write(stagedOriginal, cleanBytes, StandardOpenOption.TRUNCATE_EXISTING);
            Files.write(stagedMarked, markedBytes, StandardOpenOption.TRUNCATE_EXISTING);
            move(stagedOriginal, original);
            stagedOriginal = null;
            move(stagedMarked, watermarked);
            stagedMarked = null;
            return new StoredEvidence(safeName(photo.getOriginalFilename()),
                    portable(originalRelative), portable(markedRelative),
                    cleanBytes.length, markedBytes.length, sha256(cleanBytes),
                    sha256(markedBytes), clean.getWidth(), clean.getHeight(),
                    String.join("\n", watermarkLines == null
                            ? List.of() : watermarkLines));
        }
        catch (IOException ex)
        {
            deleteQuietly(stagedOriginal);
            deleteQuietly(stagedMarked);
            deleteQuietly(original);
            deleteQuietly(watermarked);
            throw failure("PHOTO_STORE_FAILED", ex);
        }
    }

    public Path resolveWatermarked(String relativePath)
    {
        if (relativePath == null || relativePath.isBlank()
                || relativePath.contains("\\"))
        {
            throw new ServiceException("ATTENDANCE_EVIDENCE_PATH_INVALID");
        }
        Path path = target(Paths.get(relativePath));
        if (!Files.isRegularFile(path))
        {
            throw new ServiceException("ATTENDANCE_EVIDENCE_NOT_FOUND");
        }
        return path;
    }

    public void deleteQuietly(String relativePath)
    {
        if (relativePath == null) return;
        try { Files.deleteIfExists(target(Paths.get(relativePath))); }
        catch (RuntimeException | IOException ignored) { }
    }

    BufferedImage watermark(BufferedImage input, List<String> lines)
    {
        BufferedImage output = new BufferedImage(input.getWidth(),
                input.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D g = output.createGraphics();
        try
        {
            g.drawImage(input, 0, 0, null);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                    RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            int fontSize = Math.max(14, Math.min(32, input.getWidth() / 32));
            String sample = lines == null ? "" : String.join("", lines);
            g.setFont(fontResolver.resolve(sample, Font.BOLD, fontSize));
            FontMetrics metrics = g.getFontMetrics();
            int padding = Math.max(10, fontSize / 2);
            int textWidth = input.getWidth() - padding * 2;
            List<String> renderedLines = wrapWatermarkLines(lines, metrics,
                    textWidth);
            int boxHeight = padding * 2 + Math.max(1, renderedLines.size())
                    * (metrics.getHeight() + 2);
            while (boxHeight > input.getHeight() && fontSize > 10)
            {
                fontSize--;
                g.setFont(fontResolver.resolve(sample, Font.BOLD, fontSize));
                metrics = g.getFontMetrics();
                padding = Math.max(8, fontSize / 2);
                textWidth = input.getWidth() - padding * 2;
                renderedLines = wrapWatermarkLines(lines, metrics, textWidth);
                boxHeight = padding * 2 + Math.max(1,
                        renderedLines.size()) * (metrics.getHeight() + 2);
            }
            if (boxHeight > input.getHeight())
                throw new ServiceException("ATTENDANCE_WATERMARK_TOO_LARGE");
            int y0 = Math.max(0, input.getHeight() - boxHeight);
            g.setComposite(AlphaComposite.SrcOver.derive(0.72f));
            g.setColor(Color.BLACK);
            g.fillRect(0, y0, input.getWidth(), input.getHeight() - y0);
            g.setComposite(AlphaComposite.SrcOver);
            g.setColor(Color.WHITE);
            int y = y0 + padding + metrics.getAscent();
            for (String value : renderedLines)
            {
                g.drawString(value, padding, y);
                y += metrics.getHeight() + 2;
            }
        }
        finally
        {
            g.dispose();
        }
        return output;
    }

    private Dimension inspect(byte[] source)
    {
        if (!isJpeg(source) && !isPng(source))
        {
            throw new ServiceException("PHOTO_CONTENT_INVALID");
        }
        try (ImageInputStream stream = ImageIO.createImageInputStream(
                new ByteArrayInputStream(source)))
        {
            Iterator<ImageReader> readers = ImageIO.getImageReaders(stream);
            if (!readers.hasNext()) throw new ServiceException("PHOTO_CONTENT_INVALID");
            ImageReader reader = readers.next();
            try
            {
                reader.setInput(stream, true, true);
                return new Dimension(reader.getWidth(0), reader.getHeight(0));
            }
            finally { reader.dispose(); }
        }
        catch (IOException ex)
        {
            throw failure("PHOTO_CONTENT_INVALID", ex);
        }
    }

    private BufferedImage decode(byte[] source)
    {
        try
        {
            BufferedImage value = ImageIO.read(new ByteArrayInputStream(source));
            if (value == null) throw new ServiceException("PHOTO_CONTENT_INVALID");
            return value;
        }
        catch (IOException ex) { throw failure("PHOTO_CONTENT_INVALID", ex); }
    }

    private BufferedImage flatten(BufferedImage source)
    {
        BufferedImage value = new BufferedImage(source.getWidth(),
                source.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D g = value.createGraphics();
        try
        {
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, value.getWidth(), value.getHeight());
            g.drawImage(source, 0, 0, null);
        }
        finally { g.dispose(); }
        return value;
    }

    private byte[] encodeJpeg(BufferedImage image)
    {
        try (ByteArrayOutputStream output = new ByteArrayOutputStream())
        {
            if (!ImageIO.write(image, "jpg", output))
                throw new ServiceException("PHOTO_ENCODE_FAILED");
            return output.toByteArray();
        }
        catch (IOException ex) { throw failure("PHOTO_ENCODE_FAILED", ex); }
    }

    private void move(Path source, Path target) throws IOException
    {
        try { Files.move(source, target, StandardCopyOption.ATOMIC_MOVE); }
        catch (java.nio.file.AtomicMoveNotSupportedException ex)
        { Files.move(source, target); }
    }

    private Path target(Path relative)
    {
        Path value = root.resolve(relative).normalize();
        if (!value.startsWith(root))
            throw new ServiceException("ATTENDANCE_EVIDENCE_PATH_ESCAPE");
        return value;
    }

    private String safeName(String value)
    {
        String name = value == null ? "capture.jpg"
                : Paths.get(value.replace('\\', '/')).getFileName().toString();
        String safe = name.replaceAll("[\\p{Cntrl}]", "").trim();
        if (safe.isEmpty() || safe.contains("..")) return "capture.jpg";
        return safe.length() > 180 ? safe.substring(safe.length() - 180) : safe;
    }

    List<String> wrapWatermarkLines(List<String> lines, FontMetrics metrics,
            int width)
    {
        List<String> wrapped = new ArrayList<>();
        if (lines == null || lines.isEmpty()) return wrapped;
        for (String line : lines)
        {
            String value = line == null ? "" : line;
            if (value.isEmpty())
            {
                wrapped.add("");
                continue;
            }
            int start = 0;
            while (start < value.length())
            {
                int end = start;
                int preferredBreak = -1;
                while (end < value.length())
                {
                    int candidate = end + 1;
                    if (metrics.stringWidth(value.substring(start, candidate))
                            > width) break;
                    char current = value.charAt(end);
                    if (Character.isWhitespace(current) || current == '/')
                        preferredBreak = candidate;
                    end = candidate;
                }
                if (end == start) end = start + 1;
                else if (end < value.length() && preferredBreak > start)
                    end = preferredBreak;
                wrapped.add(value.substring(start, end));
                start = end;
            }
        }
        return wrapped;
    }

    private boolean isJpeg(byte[] b)
    { return b.length > 3 && (b[0] & 0xff) == 0xff && (b[1] & 0xff) == 0xd8 && (b[2] & 0xff) == 0xff; }
    private boolean isPng(byte[] b)
    { return b.length > 8 && (b[0] & 0xff) == 0x89 && b[1] == 0x50 && b[2] == 0x4e && b[3] == 0x47; }
    private String portable(Path path) { return path.toString().replace('\\', '/'); }
    private long maxPhotoBytes()
    { return runtimePolicy == null ? properties.getMaxPhotoBytes()
            : runtimePolicy.maxPhotoBytes(); }
    private String sha256(byte[] bytes)
    {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
        catch (NoSuchAlgorithmException ex) { throw new IllegalStateException(ex); }
    }
    private ServiceException failure(String message, Exception cause)
    { return new ServiceException(message + ": " + cause.getClass().getSimpleName()); }
    private void deleteQuietly(Path path)
    { if (path != null) try { Files.deleteIfExists(path); } catch (IOException ignored) { } }

    private record Dimension(int width, int height) { }
    public record StoredEvidence(String originalName, String originalPath,
            String watermarkedPath, long originalSize, long watermarkedSize,
            String originalSha256, String watermarkedSha256, int width,
            int height, String watermarkPayload) { }
}
