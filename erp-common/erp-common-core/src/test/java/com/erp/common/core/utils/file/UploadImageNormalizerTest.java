package com.erp.common.core.utils.file;

import static org.assertj.core.api.Assertions.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Arrays;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import com.erp.common.core.exception.ServiceException;

class UploadImageNormalizerTest
{
    private static byte[] png() throws Exception
    {
        BufferedImage image = new BufferedImage(300, 200, BufferedImage.TYPE_INT_ARGB);
        image.setRGB(20, 20, 0x80ff0000);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "png", out);
        return out.toByteArray();
    }

    @Test void rejectsFakeImageAndExtensionMismatch() throws Exception
    {
        assertThatThrownBy(() -> UploadImageNormalizer.normalize("not a PNG".getBytes(), "a.png"))
                .isInstanceOf(ServiceException.class);
        assertThatThrownBy(() -> UploadImageNormalizer.normalize(png(), "a.jpg"))
                .isInstanceOf(ServiceException.class).hasMessageContaining("扩展名");
    }

    @Test void pngPreservesAllPixelsAndAlphaAndNeverGrows() throws Exception
    {
        byte[] source = png();
        byte[] result = UploadImageNormalizer.normalize(source, "a.png");
        BufferedImage decoded = ImageIO.read(new ByteArrayInputStream(result));
        assertThat(result.length).isLessThanOrEqualTo(source.length);
        assertThat(decoded.getWidth()).isEqualTo(300);
        assertThat(decoded.getHeight()).isEqualTo(200);
        assertThat(decoded.getRGB(0, 0)).isZero();
        assertThat(decoded.getRGB(20, 20)).isEqualTo(0x80ff0000);
    }

    @Test void optimizesOversizedImageWithinDimensionLimitAndBindsFinalSize() throws Exception
    {
        byte[] source = Arrays.copyOf(png(), 1_500_000);
        var result = UploadImageNormalizer.normalize(new MockMultipartFile("file", "a.png", "image/png", source));
        assertThat(result.getSize()).isLessThan(1500000);
        assertThat(result.getBytes()).hasSize((int) result.getSize());
        assertThat(result.getContentType()).isEqualTo("image/png");
        assertThat(result.getOriginalFilename()).isEqualTo("a.png");
    }

    @Test void nonImageAttachmentsRemainByteIdentical() throws Exception
    {
        var pdf = new MockMultipartFile("file", "a.pdf", "application/pdf", "%PDF-1.4".getBytes());
        assertThat(UploadImageNormalizer.normalize(pdf)).isSameAs(pdf);
    }

    @Test void pngOptimizationPreservesOrientationAndColorMetadata() throws Exception
    {
        byte[] original = png();
        byte[] exif = java.util.HexFormat.of().parseHex("49492a0008000000010012010300010000000600000000000000");
        byte[] gamma = java.nio.ByteBuffer.allocate(4).putInt(45455).array();
        var source = new ByteArrayOutputStream();
        source.write(original, 0, 33); // Signature + IHDR.
        writePngChunk(source, "eXIf", exif);
        writePngChunk(source, "gAMA", gamma);
        source.write(original, 33, original.length - 33);
        byte[] padded = Arrays.copyOf(source.toByteArray(), 100_000);
        byte[] result = UploadImageNormalizer.normalize(padded, "oriented.png");
        assertThat(result.length).isLessThan(padded.length);
        assertThat(pngChunk(result, "eXIf")).containsExactly(exif);
        assertThat(pngChunk(result, "gAMA")).containsExactly(gamma);
        var before = ImageIO.read(new ByteArrayInputStream(padded));
        var after = ImageIO.read(new ByteArrayInputStream(result));
        assertThat(after.getRGB(0, 0, 300, 200, null, 0, 300))
                .containsExactly(before.getRGB(0, 0, 300, 200, null, 0, 300));
    }

    private static void writePngChunk(ByteArrayOutputStream target, String type, byte[] bytes) throws Exception
    {
        var output = new java.io.DataOutputStream(target);
        byte[] name = type.getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        output.writeInt(bytes.length); output.write(name); output.write(bytes);
        var crc = new java.util.zip.CRC32(); crc.update(name); crc.update(bytes);
        output.writeInt((int) crc.getValue());
    }

    private static byte[] pngChunk(byte[] bytes, String expected)
    {
        for (int offset = 8; offset + 12 <= bytes.length; )
        {
            int length = java.nio.ByteBuffer.wrap(bytes, offset, 4).getInt();
            String name = new String(bytes, offset + 4, 4, java.nio.charset.StandardCharsets.US_ASCII);
            if (expected.equals(name)) return Arrays.copyOfRange(bytes, offset + 8, offset + 8 + length);
            offset += length + 12;
        }
        return null;
    }

    @Test void rejectsHugeDimensionsBeforeDecode() throws Exception
    {
        byte[] image = png();
        // PNG IHDR width/height; recompute CRC so the dimension check is reached.
        java.nio.ByteBuffer.wrap(image, 16, 8).putInt(100000).putInt(100000);
        var crc = new java.util.zip.CRC32(); crc.update(image, 12, 17);
        java.nio.ByteBuffer.wrap(image, 29, 4).putInt((int) crc.getValue());
        assertThatThrownBy(() -> UploadImageNormalizer.normalize(image, "large.png"))
                .isInstanceOf(ServiceException.class).hasMessageContaining("像素");
    }
}
