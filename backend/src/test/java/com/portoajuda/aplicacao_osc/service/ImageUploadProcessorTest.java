package com.portoajuda.aplicacao_osc.service;

import static org.junit.jupiter.api.Assertions.*;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

class ImageUploadProcessorTest {
    private final ImageUploadProcessor processor = new ImageUploadProcessor();

    @Test
    void decodesAndNormalizesPngBytesRatherThanTrustingMimeType() throws Exception {
        var result = processor.process(image("png", 3, 2), "image/png");
        assertEquals("image/png", result.mediaType());
        assertEquals("png", result.extension());
        assertEquals(3, result.width());
        assertEquals(2, result.height());
        assertNotNull(ImageIO.read(new java.io.ByteArrayInputStream(result.bytes())));
    }

    @Test
    void rejectsSpoofedSvgMalformedAndMimeMismatch() throws Exception {
        assertThrows(IllegalArgumentException.class,
                () -> processor.process("<svg/>".getBytes(), "image/png"));
        assertThrows(IllegalArgumentException.class,
                () -> processor.process(image("png", 3, 2), "image/jpeg"));
        assertThrows(IllegalArgumentException.class,
                () -> processor.process(new byte[]{(byte) 0xff, (byte) 0xd8, (byte) 0xff}, "image/jpeg"));
    }

    @Test
    void appliesExifOrientationAndStripsTheExifSegmentOnReencode() throws Exception {
        byte[] input = withExifOrientation(image("jpeg", 3, 2), 6);
        var output = processor.process(input, "image/jpeg");
        assertEquals(2, output.width());
        assertEquals(3, output.height());
        assertFalse(new String(output.bytes(), java.nio.charset.StandardCharsets.ISO_8859_1).contains("Exif"));
    }

    @Test
    void rejectsExcessiveDimensionsBeforeDecodingPixels() {
        byte[] pngHeader = new byte[]{(byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a,
                0, 0, 0, 13, 0x49, 0x48, 0x44, 0x52, 0, 0, 0x17, 0x71, 0, 0, 0, 1};
        assertThrows(IllegalArgumentException.class, () -> processor.process(pngHeader, "image/png"));
    }

    @Test
    void rejectsOversizedInput() {
        assertThrows(IllegalArgumentException.class,
                () -> processor.process(new byte[(int) ImageUploadProcessor.MAX_BYTES + 1], "image/png"));
    }

    private byte[] image(String format, int width, int height) throws IOException {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        assertTrue(ImageIO.write(image, format, output));
        return output.toByteArray();
    }

    private byte[] withExifOrientation(byte[] jpeg, int orientation) throws IOException {
        ByteArrayOutputStream exif = new ByteArrayOutputStream();
        exif.write(new byte[]{'E', 'x', 'i', 'f', 0, 0});
        exif.write(new byte[]{'I', 'I', 42, 0, 8, 0, 0, 0});
        exif.write(new byte[]{1, 0});
        exif.write(new byte[]{0x12, 0x01, 3, 0, 1, 0, 0, 0, (byte) orientation, 0, 0, 0});
        exif.write(new byte[]{0, 0, 0, 0});
        byte[] payload = exif.toByteArray();
        int segmentLength = payload.length + 2;
        ByteArrayOutputStream result = new ByteArrayOutputStream();
        result.write(jpeg, 0, 2);
        result.write(new byte[]{(byte) 0xff, (byte) 0xe1, (byte) (segmentLength >>> 8), (byte) segmentLength});
        result.write(payload);
        result.write(jpeg, 2, jpeg.length - 2);
        return result.toByteArray();
    }
}
