package com.sk.onlinemall.storage;

import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ImageCompressorTests {
    /**
     * 验证超大 JPEG 被等比缩放到配置长边以内。
     *
     * @throws Exception 图片编解码失败
     */
    @Test
    void shouldResizeLargeJpeg() throws Exception {
        BufferedImage source = new BufferedImage(800, 400, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageIO.write(source, "jpeg", output);

        byte[] compressed = new ImageCompressor(200, 0.8F).compress(output.toByteArray(), "image/jpeg");
        BufferedImage result = ImageIO.read(new ByteArrayInputStream(compressed));

        assertTrue(result.getWidth() <= 200 && result.getHeight() <= 200);
    }
}
