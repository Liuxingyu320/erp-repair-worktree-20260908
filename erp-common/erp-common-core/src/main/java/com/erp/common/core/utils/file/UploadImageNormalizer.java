package com.erp.common.core.utils.file;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.Iterator;
import java.util.Locale;
import java.util.Set;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.plugins.jpeg.JPEGImageWriteParam;
import javax.imageio.stream.MemoryCacheImageInputStream;
import javax.imageio.stream.MemoryCacheImageOutputStream;
import com.erp.common.core.exception.ServiceException;
import org.springframework.web.multipart.MultipartFile;

/** Processes NEW uploads only. Call before the final size/hash/evidence binding. */
public final class UploadImageNormalizer
{
    private static final long MAX_PIXELS = 40_000_000L;
    private static final int MAX_BYTES = 50 * 1024 * 1024;
    private static final Set<String> IMAGES = Set.of("png", "jpg", "jpeg", "gif", "bmp", "webp", "heic", "heif", "tif", "tiff");

    private UploadImageNormalizer() { }

    public static boolean isImage(String name, String contentType)
    {
        return IMAGES.contains(extension(name)) || contentType != null
                && contentType.toLowerCase(Locale.ROOT).startsWith("image/");
    }

    public static MultipartFile normalize(MultipartFile file) throws IOException
    {
        return normalize(file, null);
    }

    public static MultipartFile normalize(MultipartFile file, String expectedSha256) throws IOException
    {
        if (!isImage(file.getOriginalFilename(), file.getContentType())) return file;
        if (file.getSize() > MAX_BYTES) throw invalid("图片不能超过 50 MB");
        byte[] source;
        try (InputStream input = file.getInputStream()) { source = input.readNBytes(MAX_BYTES + 1); }
        if (expectedSha256 != null)
        {
            try
            {
                String originalHash = java.util.HexFormat.of().formatHex(
                        java.security.MessageDigest.getInstance("SHA-256").digest(source));
                if (!expectedSha256.equals(originalHash)) throw new ContentChangedException();
            }
            catch (java.security.NoSuchAlgorithmException ex) { throw new IllegalStateException(ex); }
        }
        byte[] result = normalize(source, file.getOriginalFilename());
        return new ImageFile(file.getName(), file.getOriginalFilename(), mime(extension(file.getOriginalFilename())), result);
    }

    public static byte[] normalize(byte[] source, String filename)
    {
        if (source == null || source.length == 0 || source.length > MAX_BYTES)
            throw invalid("图片大小无效");
        String extension = extension(filename);
        if (Set.of("webp", "heic", "heif").contains(extension))
            return ExtendedUploadImageNormalizer.normalize(source, extension);
        try (MemoryCacheImageInputStream input = new MemoryCacheImageInputStream(new ByteArrayInputStream(source)))
        {
            Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) throw invalid("图片内容无法解码，请使用有效的 JPG、PNG、GIF 或 BMP 图片");
            ImageReader reader = readers.next();
            try
            {
                final boolean[] warning = { false };
                reader.addIIOReadWarningListener((ignored, message) -> warning[0] = true);
                reader.setInput(input, false, false);
                String format = reader.getFormatName().toLowerCase(Locale.ROOT);
                if (!canonical(extension).equals(canonical(format))) throw invalid("图片内容与扩展名不匹配");
                int frames = reader.getNumImages(true);
                if (frames < 1 || frames > 200) throw invalid("图片帧数超出限制");
                long pixels = 0;
                BufferedImage first = null;
                for (int frame = 0; frame < frames; frame++)
                {
                    int width = reader.getWidth(frame), height = reader.getHeight(frame);
                    pixels += (long) width * height;
                    if (width <= 0 || height <= 0 || pixels > MAX_PIXELS) throw invalid("图片总像素不能超过 4000 万");
                    BufferedImage decoded = reader.read(frame);
                    if (decoded == null || warning[0]) throw invalid("图片内容无法解码");
                    if (frame == 0) first = decoded;
                    else decoded.flush();
                }
                // Preserve animated/multi-page images; never silently discard frames.
                if (frames > 1 || !Set.of("png", "jpeg", "jpg").contains(format)) return source;
                BufferedImage normalized = "png".equals(format) ? first
                        : ImageExifOrientation.apply(first, ImageExifOrientation.read(source));
                // PNG orientation and color chunks affect rendering even when pixels are unchanged.
                byte[] candidate = encode(normalized, canonical(format),
                        "png".equals(format) ? reader.getImageMetadata(0) : null);
                // Keeping original bytes also keeps their orientation metadata when re-encoding grows.
                return candidate.length < source.length ? candidate : source;
            }
            finally { reader.dispose(); }
        }
        catch (IOException | IllegalArgumentException ex)
        {
            throw invalid("图片内容损坏或不支持，请重新选择");
        }
    }

    private static byte[] encode(BufferedImage image, String format, IIOMetadata metadata) throws IOException
    {
        ImageWriter writer = ImageIO.getImageWritersByFormatName(format).next();
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (MemoryCacheImageOutputStream output = new MemoryCacheImageOutputStream(bytes))
        {
            writer.setOutput(output);
            ImageWriteParam param = writer.getDefaultWriteParam();
            if ("jpeg".equals(format))
            {
                // Preserve full resolution. This is a conservative, configurable engineering default.
                float quality = Float.parseFloat(System.getProperty("erp.image.jpeg-quality",
                        System.getenv().getOrDefault("ERP_IMAGE_JPEG_QUALITY", "0.92")));
                if (!Float.isFinite(quality) || quality < 0.85f || quality > 1f)
                    throw invalid("JPEG 压缩质量配置必须在 0.85 到 1 之间");
                param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
                param.setCompressionQuality(quality);
                ((JPEGImageWriteParam) param).setOptimizeHuffmanTables(true);
                if (image.getType() != BufferedImage.TYPE_INT_RGB)
                {
                    BufferedImage rgb = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_RGB);
                    Graphics2D graphics = rgb.createGraphics();
                    try { graphics.drawImage(image, 0, 0, null); }
                    finally { graphics.dispose(); }
                    image = rgb;
                }
            }
            else if (param.canWriteCompressed())
            {
                param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
                param.setCompressionQuality(0f); // PNG maximum lossless deflate compression.
            }
            writer.write(null, new IIOImage(image, null, metadata), param);
            output.flush();
            return bytes.toByteArray();
        }
        finally { writer.dispose(); }
    }

    private static String canonical(String value) { return "jpg".equals(value) ? "jpeg" : "tif".equals(value) ? "tiff" : value; }
    private static String extension(String name)
    {
        if (name == null) return "";
        int dot = name.lastIndexOf('.');
        return dot < 0 ? "" : name.substring(dot + 1).toLowerCase(Locale.ROOT);
    }
    private static String mime(String extension) { return "image/" + canonical(extension); }
    private static ServiceException invalid(String message) { return new ServiceException(message); }

    public static final class ContentChangedException extends IOException
    {
        public ContentChangedException() { super("上传内容已变化，请重新选择原文件"); }
    }

    private record ImageFile(String name, String originalFilename, String contentType, byte[] bytes) implements MultipartFile
    {
        public String getName() { return name; }
        public String getOriginalFilename() { return originalFilename; }
        public String getContentType() { return contentType; }
        public boolean isEmpty() { return bytes.length == 0; }
        public long getSize() { return bytes.length; }
        public byte[] getBytes() { return bytes.clone(); }
        public InputStream getInputStream() { return new ByteArrayInputStream(bytes); }
        public void transferTo(File target) throws IOException { Files.write(target.toPath(), bytes); }
    }
}
