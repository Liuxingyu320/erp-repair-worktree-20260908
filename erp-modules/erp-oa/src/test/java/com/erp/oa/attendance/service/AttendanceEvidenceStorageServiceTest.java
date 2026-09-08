package com.erp.oa.attendance.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import com.erp.common.core.exception.ServiceException;
import com.erp.oa.attendance.config.AttendanceV2Properties;
import com.erp.oa.attendance.service.AttendanceEvidenceStorageService.StoredEvidence;
import com.erp.oa.attendance.support.AttendanceExifOrientation;

class AttendanceEvidenceStorageServiceTest
{
    @TempDir Path workDir;
    private Path privateRoot;
    private AttendanceEvidenceStorageService service;

    @BeforeEach
    void setUp()
    {
        AttendanceV2Properties properties = new AttendanceV2Properties();
        privateRoot = workDir.resolve("private");
        properties.setStorageRoot(privateRoot.toString());
        properties.setTempRoot(workDir.resolve("temp").toString());
        properties.setMaxPhotoBytes(2 * 1024 * 1024);
        service = new AttendanceEvidenceStorageService(properties);
    }

    @Test
    void reencodesWithoutMetadataAndBurnsServerWatermarkIntoPixels()
            throws Exception
    {
        BufferedImage image = new BufferedImage(640, 480,
                BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setColor(Color.BLUE);
        graphics.fillRect(0, 0, 640, 480);
        graphics.dispose();
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        ImageIO.write(image, "jpg", bytes);
        MockMultipartFile photo = new MockMultipartFile("photo",
                "C:\\fakepath\\capture.jpg", "application/octet-stream",
                bytes.toByteArray());

        StoredEvidence stored = service.store("AP20260820120000ABCDEF",
                LocalDate.of(2026, 8, 20), photo,
                List.of("员工：测试员工", "时间：2026-08-20 12:00:00",
                        "编号：AP20260820120000ABCDEF"));

        assertThat(stored.originalName()).isEqualTo("capture.jpg");
        assertThat(stored.originalSha256()).hasSize(64)
                .isNotEqualTo(stored.watermarkedSha256());
        assertThat(privateRoot.resolve(stored.originalPath())).isRegularFile();
        Path marked = service.resolveWatermarked(stored.watermarkedPath());
        assertThat(marked).isRegularFile();
        BufferedImage result = ImageIO.read(marked.toFile());
        assertThat(result.getWidth()).isEqualTo(640);
        assertThat(result.getRGB(20, 470)).isNotEqualTo(image.getRGB(20, 470));
    }

    @Test
    void appliesPhoneExifOrientationBeforeStrippingMetadataAndWatermarking()
            throws Exception
    {
        BufferedImage image = quadrantImage(640, 480);
        byte[] encoded = jpeg(image);
        byte[] phoneJpeg = withExifOrientation(encoded, 6, true);
        assertThat(AttendanceExifOrientation.read(phoneJpeg)).isEqualTo(6);

        StoredEvidence stored = service.store("AP20260821090000ABCDEF",
                LocalDate.of(2026, 8, 21), new MockMultipartFile("photo",
                        "image.jpg", "image/jpeg", phoneJpeg),
                List.of("员工：测试员工", "时间：2026-08-21 09:00:00"));

        assertThat(stored.width()).isEqualTo(480);
        assertThat(stored.height()).isEqualTo(640);
        Path normalizedPath = privateRoot.resolve(stored.originalPath());
        Path watermarkedPath = service.resolveWatermarked(
                stored.watermarkedPath());
        BufferedImage normalized = ImageIO.read(normalizedPath.toFile());
        BufferedImage watermarked = ImageIO.read(watermarkedPath.toFile());
        assertThat(normalized.getWidth()).isEqualTo(480);
        assertThat(normalized.getHeight()).isEqualTo(640);
        assertThat(watermarked.getWidth()).isEqualTo(480);
        assertThat(watermarked.getHeight()).isEqualTo(640);
        assertDominant(normalized.getRGB(80, 80), Color.BLUE);
        assertDominant(normalized.getRGB(400, 80), Color.RED);
        assertDominant(normalized.getRGB(80, 560), Color.YELLOW);
        assertDominant(normalized.getRGB(400, 560), Color.GREEN);
        assertThat(AttendanceExifOrientation.read(
                Files.readAllBytes(normalizedPath))).isEqualTo(1);
        assertThat(AttendanceExifOrientation.read(
                Files.readAllBytes(watermarkedPath))).isEqualTo(1);
    }

    @Test
    void rejectsDisguisedNonImageAndPathEscape()
    {
        MockMultipartFile fake = new MockMultipartFile("photo", "x.jpg",
                "image/jpeg", "not an image".getBytes(StandardCharsets.UTF_8));
        assertThatThrownBy(() -> service.store("AP20260820120000ABCDEF",
                LocalDate.now(), fake, List.of("x")))
                .isInstanceOf(ServiceException.class)
                .hasMessage("PHOTO_CONTENT_INVALID");
        assertThatThrownBy(() -> service.resolveWatermarked("../outside.jpg"))
                .hasMessage("ATTENDANCE_EVIDENCE_PATH_ESCAPE");
    }

    @Test
    void wrapsEveryOrganizationLevelWithoutElidingTheStoreName()
    {
        BufferedImage canvas = new BufferedImage(480, 640,
                BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = canvas.createGraphics();
        try
        {
            graphics.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 14));
            FontMetrics metrics = graphics.getFontMetrics();
            String path = "门店：金英灵韵 / 北京区域 / 北京区域运营 / "
                    + "华北运营组织 / 北京中心组织 / 北京柏悦";

            List<String> wrapped = service.wrapWatermarkLines(List.of(path),
                    metrics, 180);

            assertThat(wrapped).hasSizeGreaterThan(1);
            assertThat(String.join("", wrapped)).isEqualTo(path)
                    .endsWith("北京柏悦")
                    .doesNotContain("...");
            assertThat(wrapped).allSatisfy(line -> assertThat(
                    metrics.stringWidth(line)).isLessThanOrEqualTo(180));
        }
        finally
        {
            graphics.dispose();
        }
    }

    private BufferedImage quadrantImage(int width, int height)
    {
        BufferedImage image = new BufferedImage(width, height,
                BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setColor(Color.RED);
        graphics.fillRect(0, 0, width / 2, height / 2);
        graphics.setColor(Color.GREEN);
        graphics.fillRect(width / 2, 0, width / 2, height / 2);
        graphics.setColor(Color.BLUE);
        graphics.fillRect(0, height / 2, width / 2, height / 2);
        graphics.setColor(Color.YELLOW);
        graphics.fillRect(width / 2, height / 2, width / 2, height / 2);
        graphics.dispose();
        return image;
    }

    private byte[] jpeg(BufferedImage image) throws IOException
    {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageIO.write(image, "jpg", output);
        return output.toByteArray();
    }

    private byte[] withExifOrientation(byte[] jpeg, int orientation,
            boolean littleEndian) throws IOException
    {
        ByteArrayOutputStream payload = new ByteArrayOutputStream();
        payload.write(new byte[] {'E', 'x', 'i', 'f', 0, 0});
        payload.write(littleEndian ? new byte[] {'I', 'I'}
                : new byte[] {'M', 'M'});
        writeShort(payload, 42, littleEndian);
        writeInt(payload, 8, littleEndian);
        writeShort(payload, 1, littleEndian);
        writeShort(payload, 0x0112, littleEndian);
        writeShort(payload, 3, littleEndian);
        writeInt(payload, 1, littleEndian);
        writeShort(payload, orientation, littleEndian);
        writeShort(payload, 0, littleEndian);
        writeInt(payload, 0, littleEndian);

        byte[] exif = payload.toByteArray();
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        output.write(jpeg, 0, 2);
        output.write(0xff);
        output.write(0xe1);
        output.write((exif.length + 2) >>> 8);
        output.write((exif.length + 2) & 0xff);
        output.write(exif);
        output.write(jpeg, 2, jpeg.length - 2);
        return output.toByteArray();
    }

    private void writeShort(ByteArrayOutputStream output, int value,
            boolean littleEndian)
    {
        if (littleEndian)
        {
            output.write(value & 0xff);
            output.write(value >>> 8 & 0xff);
        }
        else
        {
            output.write(value >>> 8 & 0xff);
            output.write(value & 0xff);
        }
    }

    private void writeInt(ByteArrayOutputStream output, int value,
            boolean littleEndian)
    {
        for (int index = 0; index < 4; index++)
        {
            int shift = littleEndian ? index * 8 : (3 - index) * 8;
            output.write(value >>> shift & 0xff);
        }
    }

    private void assertDominant(int actualRgb, Color expected)
    {
        Color actual = new Color(actualRgb);
        if (expected == Color.RED)
            assertThat(actual.getRed()).isGreaterThan(200);
        else if (expected == Color.GREEN)
            assertThat(actual.getGreen()).isGreaterThan(100);
        else if (expected == Color.BLUE)
            assertThat(actual.getBlue()).isGreaterThan(200);
        else
        {
            assertThat(actual.getRed()).isGreaterThan(200);
            assertThat(actual.getGreen()).isGreaterThan(200);
        }
    }
}
