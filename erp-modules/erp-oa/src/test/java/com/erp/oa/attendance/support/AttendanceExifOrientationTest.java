package com.erp.oa.attendance.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.stream.Stream;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import com.erp.common.core.exception.ServiceException;

class AttendanceExifOrientationTest
{
    @ParameterizedTest
    @MethodSource("orientationCases")
    void mapsEveryExifOrientationToDisplayedPixelOrder(int orientation,
            int[][] expected)
    {
        BufferedImage source = new BufferedImage(3, 2,
                BufferedImage.TYPE_INT_RGB);
        int color = 1;
        for (int y = 0; y < source.getHeight(); y++)
            for (int x = 0; x < source.getWidth(); x++)
                source.setRGB(x, y, color++);

        BufferedImage actual = AttendanceExifOrientation.apply(source,
                orientation);

        assertThat(actual.getHeight()).isEqualTo(expected.length);
        assertThat(actual.getWidth()).isEqualTo(expected[0].length);
        for (int y = 0; y < actual.getHeight(); y++)
            for (int x = 0; x < actual.getWidth(); x++)
                assertThat(actual.getRGB(x, y) & 0x00ffffff)
                        .isEqualTo(expected[y][x]);
    }

    @Test
    void readsLittleAndBigEndianOrientationAndRejectsUnsafeValue()
            throws Exception
    {
        byte[] jpeg = jpeg();
        assertThat(AttendanceExifOrientation.read(
                withExifOrientation(jpeg, 6, true))).isEqualTo(6);
        assertThat(AttendanceExifOrientation.read(
                withExifOrientation(jpeg, 8, false))).isEqualTo(8);
        assertThatThrownBy(() -> AttendanceExifOrientation.read(
                withExifOrientation(jpeg, 9, true)))
                        .isInstanceOf(ServiceException.class)
                        .hasMessage("PHOTO_CONTENT_INVALID");
    }

    private static Stream<Arguments> orientationCases()
    {
        return Stream.of(
                Arguments.of(1, new int[][] {{1, 2, 3}, {4, 5, 6}}),
                Arguments.of(2, new int[][] {{3, 2, 1}, {6, 5, 4}}),
                Arguments.of(3, new int[][] {{6, 5, 4}, {3, 2, 1}}),
                Arguments.of(4, new int[][] {{4, 5, 6}, {1, 2, 3}}),
                Arguments.of(5, new int[][] {{1, 4}, {2, 5}, {3, 6}}),
                Arguments.of(6, new int[][] {{4, 1}, {5, 2}, {6, 3}}),
                Arguments.of(7, new int[][] {{6, 3}, {5, 2}, {4, 1}}),
                Arguments.of(8, new int[][] {{3, 6}, {2, 5}, {1, 4}}));
    }

    private byte[] jpeg() throws IOException
    {
        BufferedImage image = new BufferedImage(8, 8,
                BufferedImage.TYPE_INT_RGB);
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
}
