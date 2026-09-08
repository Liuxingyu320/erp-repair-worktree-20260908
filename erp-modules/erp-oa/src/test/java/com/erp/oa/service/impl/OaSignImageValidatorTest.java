package com.erp.oa.service.impl;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.zip.CRC32;
import org.junit.jupiter.api.Test;
import com.erp.common.core.exception.ServiceException;

class OaSignImageValidatorTest
{
    @Test
    void acceptsVisibleSyntheticStrokesWithoutChangingBytes()
    {
        byte[] sample = SignatureImageTestFixtures.signature(1);
        byte[] original = sample.clone();
        assertThatCode(() -> OaSignImageValidator.requireSignaturePng(sample))
                .doesNotThrowAnyException();
        org.assertj.core.api.Assertions.assertThat(sample).containsExactly(original);
    }

    @Test
    void rejectsHeaderOnlyAndTruncatedPng()
    {
        byte[] sample = SignatureImageTestFixtures.signature(1);
        for (byte[] malformed : new byte[][] {Arrays.copyOf(sample, 8),
                Arrays.copyOf(sample, sample.length / 2)})
            assertThatThrownBy(() -> OaSignImageValidator.requireSignaturePng(malformed))
                    .isInstanceOf(ServiceException.class);
    }

    @Test
    void rejectsTransparentWhiteSolidAndSinglePixelImages()
    {
        for (Color fill : new Color[] {new Color(0, 0, 0, 0), Color.WHITE, Color.BLACK})
        {
            BufferedImage image = new BufferedImage(320, 120, BufferedImage.TYPE_INT_ARGB);
            Graphics2D graphics = image.createGraphics();
            graphics.setColor(fill);
            graphics.fillRect(0, 0, 320, 120);
            graphics.dispose();
            assertThatThrownBy(() -> OaSignImageValidator.requireSignaturePng(
                    SignatureImageTestFixtures.png(image))).isInstanceOf(ServiceException.class);
        }
        BufferedImage dot = new BufferedImage(320, 120, BufferedImage.TYPE_INT_ARGB);
        dot.setRGB(50, 50, Color.BLACK.getRGB());
        assertThatThrownBy(() -> OaSignImageValidator.requireSignaturePng(
                SignatureImageTestFixtures.png(dot))).isInstanceOf(ServiceException.class);
    }

    @Test
    void rejectsAlmostTransparentInkAfterCompositingOnWhite()
    {
        BufferedImage image = new BufferedImage(320, 120, BufferedImage.TYPE_INT_ARGB);
        for (int x = 20; x < 200; x++)
            for (int y = 20; y < 24; y++) image.setRGB(x, y, 0x01000000);
        assertThatThrownBy(() -> OaSignImageValidator.requireSignaturePng(
                SignatureImageTestFixtures.png(image))).isInstanceOf(ServiceException.class);
    }

    @Test
    void rejectsOversizedRasterMetadataBeforeDecodingAndTinyImage()
    {
        byte[] sample = SignatureImageTestFixtures.signature(1);
        ByteBuffer.wrap(sample).putInt(16, 8192).putInt(20, 8192);
        CRC32 crc = new CRC32();
        crc.update(sample, 12, 17);
        ByteBuffer.wrap(sample).putInt(29, (int) crc.getValue());
        assertThatThrownBy(() -> OaSignImageValidator.requireSignaturePng(sample))
                .isInstanceOf(ServiceException.class);
        assertThatThrownBy(() -> OaSignImageValidator.requireSignaturePng(
                SignatureImageTestFixtures.png(new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB))))
                .isInstanceOf(ServiceException.class);
    }

    @Test
    void acceptsOpaqueWhiteBackgroundWithVisibleStrokes()
    {
        BufferedImage image = new BufferedImage(320, 120, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setColor(Color.WHITE);
        graphics.fillRect(0, 0, 320, 120);
        graphics.setColor(Color.BLACK);
        graphics.drawLine(20, 80, 280, 30);
        graphics.dispose();
        assertThatCode(() -> OaSignImageValidator.requireSignaturePng(
                SignatureImageTestFixtures.png(image))).doesNotThrowAnyException();
    }
}
