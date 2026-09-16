package com.sk.onlinemall.storage;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Iterator;

@Component
public class ImageCompressor {
    private final int maxDimension;
    private final float jpegQuality;

    /**
     * 创建图片压缩器。
     *
     * @param maxDimension 最大长边像素
     * @param jpegQuality JPEG 压缩质量
     */
    public ImageCompressor(@Value("${app.storage.max-image-dimension:1920}") int maxDimension,
                           @Value("${app.storage.jpeg-quality:0.82}") float jpegQuality) {
        this.maxDimension = maxDimension;
        this.jpegQuality = jpegQuality;
    }

    /**
     * 等比缩放并在结果更小时返回压缩内容。
     *
     * @param content 原始内容
     * @param contentType 图片媒体类型
     * @return 原始或压缩后的内容
     */
    public byte[] compress(byte[] content, String contentType) {
        if (!"image/jpeg".equals(contentType) && !"image/png".equals(contentType)) {
            return content;
        }
        try {
            BufferedImage source = ImageIO.read(new ByteArrayInputStream(content));
            if (source == null) return content;
            double scale = Math.min(1D, (double) maxDimension / Math.max(source.getWidth(), source.getHeight()));
            int width = Math.max(1, (int) Math.round(source.getWidth() * scale));
            int height = Math.max(1, (int) Math.round(source.getHeight() * scale));
            BufferedImage target = new BufferedImage(width, height,
                    "image/jpeg".equals(contentType) ? BufferedImage.TYPE_INT_RGB : BufferedImage.TYPE_INT_ARGB);
            Graphics2D graphics = target.createGraphics();
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            graphics.drawImage(source, 0, 0, width, height, null);
            graphics.dispose();
            byte[] compressed = encode(target, contentType);
            return compressed.length < content.length ? compressed : content;
        } catch (Exception exception) {
            return content;
        }
    }

    /**
     * 按目标格式编码图片。
     *
     * @param image 图片像素
     * @param contentType 媒体类型
     * @return 编码字节
     * @throws Exception 编码失败
     */
    private byte[] encode(BufferedImage image, String contentType) throws Exception {
        String format = "image/jpeg".equals(contentType) ? "jpeg" : "png";
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName(format);
        ImageWriter writer = writers.next();
        try (ImageOutputStream stream = ImageIO.createImageOutputStream(output)) {
            writer.setOutput(stream);
            ImageWriteParam params = writer.getDefaultWriteParam();
            if ("jpeg".equals(format) && params.canWriteCompressed()) {
                params.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
                params.setCompressionQuality(jpegQuality);
            }
            writer.write(null, new IIOImage(image, null, null), params);
        } finally {
            writer.dispose();
        }
        return output.toByteArray();
    }
}
