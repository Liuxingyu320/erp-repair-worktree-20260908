package com.erp.oa.service.impl;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import javax.imageio.ImageIO;

/** Synthetic strokes only; no employee signatures or personal data. */
final class SignatureImageTestFixtures
{
    private SignatureImageTestFixtures() {}

    static byte[] signature(int variant)
    {
        BufferedImage image = new BufferedImage(320, 120, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        try
        {
            graphics.setColor(Color.BLACK);
            graphics.setStroke(new BasicStroke(3f));
            int offset = Math.floorMod(variant, 16);
            graphics.drawLine(20, 85, 100, 25 + offset);
            graphics.drawLine(100, 25 + offset, 180, 90);
            graphics.drawLine(180, 90, 295, 20 + offset);
        }
        finally { graphics.dispose(); }
        return png(image);
    }

    static byte[] png(BufferedImage image)
    {
        try
        {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            if (!ImageIO.write(image, "png", output)) throw new IOException("PNG encoder unavailable");
            return output.toByteArray();
        }
        catch (IOException exception) { throw new IllegalStateException(exception); }
    }
}
