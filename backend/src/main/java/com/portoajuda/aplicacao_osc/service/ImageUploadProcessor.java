package com.portoajuda.aplicacao_osc.service;

import java.awt.Graphics2D;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteOrder;
import java.util.Iterator;
import java.util.Locale;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import org.springframework.stereotype.Component;

/** Validates bytes by decoding them, applies JPEG EXIF orientation, and strips metadata on re-encode. */
@Component
public class ImageUploadProcessor {
    public static final long MAX_BYTES = 10L * 1024 * 1024;
    public static final long MAX_PIXELS = 12_000_000L;
    public static final int MAX_DIMENSION = 6000;

    public ProcessedImage process(byte[] input, String claimedType) {
        if (input == null || input.length == 0) throw new IllegalArgumentException("Selecione uma imagem válida.");
        if (input.length > MAX_BYTES) throw new IllegalArgumentException("A imagem não pode ultrapassar 10 MB.");
        String type = claimedType == null ? "" : claimedType.toLowerCase(Locale.ROOT);
        String format = switch (type) {
            case "image/jpeg" -> "jpeg";
            case "image/png" -> "png";
            default -> throw new IllegalArgumentException("Envie a imagem ajustada em formato JPEG ou PNG.");
        };
        BufferedImage decoded = decode(input, format);
        BufferedImage oriented = applyOrientation(decoded, format.equals("jpeg") ? exifOrientation(input) : 1);
        try {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            String writerFormat = format.equals("jpeg") ? "jpeg" : "png";
            BufferedImage encodable = writerFormat.equals("jpeg") && oriented.getColorModel().hasAlpha()
                    ? flattenOnWhite(oriented) : oriented;
            if (!ImageIO.write(encodable, writerFormat, output)) {
                throw new IllegalArgumentException("Não foi possível processar esta imagem.");
            }
            byte[] normalized = output.toByteArray();
            if (normalized.length == 0 || normalized.length > MAX_BYTES) {
                throw new IllegalArgumentException("A imagem processada excede 10 MB.");
            }
            return new ProcessedImage(normalized, writerFormat.equals("jpeg") ? "image/jpeg" : "image/png",
                    writerFormat.equals("jpeg") ? "jpg" : "png", oriented.getWidth(), oriented.getHeight());
        } catch (IOException exception) {
            throw new IllegalArgumentException("Não foi possível processar esta imagem.");
        } finally {
            if (oriented != decoded) oriented.flush();
            decoded.flush();
        }
    }

    private BufferedImage decode(byte[] bytes, String expected) {
        try (ImageInputStream stream = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
            if (stream == null) throw invalid();
            Iterator<ImageReader> readers = ImageIO.getImageReaders(stream);
            if (!readers.hasNext()) throw invalid();
            ImageReader reader = readers.next();
            try {
                reader.setInput(stream, true, true);
                String actual = reader.getFormatName().toLowerCase(Locale.ROOT);
                boolean matches = expected.equals("jpeg")
                        ? actual.equals("jpeg") || actual.equals("jpg") : actual.equals("png");
                int width = reader.getWidth(0), height = reader.getHeight(0);
                if (!matches || width < 1 || height < 1 || width > MAX_DIMENSION || height > MAX_DIMENSION
                        || (long) width * height > MAX_PIXELS) throw invalid();
                BufferedImage image = reader.read(0);
                if (image == null) throw invalid();
                return image;
            } finally {
                reader.dispose();
            }
        } catch (IOException | RuntimeException exception) {
            if (exception instanceof IllegalArgumentException invalid) throw invalid;
            throw invalid();
        }
    }

    private BufferedImage applyOrientation(BufferedImage input, int orientation) {
        if (orientation < 2 || orientation > 8) return input;
        int width = input.getWidth(), height = input.getHeight();
        boolean swaps = orientation >= 5;
        int outWidth = swaps ? height : width, outHeight = swaps ? width : height;
        AffineTransform transform = switch (orientation) {
            case 2 -> new AffineTransform(-1, 0, 0, 1, width, 0);
            case 3 -> new AffineTransform(-1, 0, 0, -1, width, height);
            case 4 -> new AffineTransform(1, 0, 0, -1, 0, height);
            case 5 -> new AffineTransform(0, 1, 1, 0, 0, 0);
            case 6 -> new AffineTransform(0, 1, -1, 0, height, 0);
            case 7 -> new AffineTransform(0, -1, -1, 0, height, width);
            case 8 -> new AffineTransform(0, -1, 1, 0, 0, width);
            default -> new AffineTransform();
        };
        int imageType = input.getColorModel().hasAlpha() ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB;
        BufferedImage result = new BufferedImage(outWidth, outHeight, imageType);
        Graphics2D graphics = result.createGraphics();
        try { graphics.drawImage(input, transform, null); } finally { graphics.dispose(); }
        return result;
    }

    private int exifOrientation(byte[] jpeg) {
        if (jpeg.length < 4 || (jpeg[0] & 0xff) != 0xff || (jpeg[1] & 0xff) != 0xd8) return 1;
        int offset = 2;
        while (offset + 4 <= jpeg.length && (jpeg[offset] & 0xff) == 0xff) {
            int marker = jpeg[offset + 1] & 0xff;
            if (marker == 0xda || marker == 0xd9) break;
            int length = ((jpeg[offset + 2] & 0xff) << 8) | (jpeg[offset + 3] & 0xff);
            if (length < 2 || offset + 2 + length > jpeg.length) return 1;
            int payload = offset + 4;
            if (marker == 0xe1 && length >= 14 && ascii(jpeg, payload, "Exif\0\0")) {
                try { return readTiffOrientation(jpeg, payload + 6, offset + 2 + length); }
                catch (RuntimeException ignored) { return 1; }
            }
            offset += 2 + length;
        }
        return 1;
    }

    private int readTiffOrientation(byte[] bytes, int base, int end) {
        boolean little = bytes[base] == 'I' && bytes[base + 1] == 'I';
        boolean big = bytes[base] == 'M' && bytes[base + 1] == 'M';
        if (!little && !big) return 1;
        ByteOrder order = little ? ByteOrder.LITTLE_ENDIAN : ByteOrder.BIG_ENDIAN;
        if (u16(bytes, base + 2, order) != 42) return 1;
        long relative = u32(bytes, base + 4, order);
        long ifdLong = base + relative;
        if (ifdLong < base || ifdLong + 2 > end) return 1;
        int ifd = (int) ifdLong;
        int count = u16(bytes, ifd, order);
        for (int i = 0; i < count; i++) {
            int entry = ifd + 2 + i * 12;
            if (entry + 12 > end) return 1;
            if (u16(bytes, entry, order) == 0x0112 && u16(bytes, entry + 2, order) == 3 && u32(bytes, entry + 4, order) == 1) {
                int value = u16(bytes, entry + 8, order);
                return value >= 1 && value <= 8 ? value : 1;
            }
        }
        return 1;
    }

    private int u16(byte[] bytes, int offset, ByteOrder order) {
        if (offset < 0 || offset + 2 > bytes.length) return 0;
        return order == ByteOrder.LITTLE_ENDIAN
                ? (bytes[offset] & 0xff) | ((bytes[offset + 1] & 0xff) << 8)
                : ((bytes[offset] & 0xff) << 8) | (bytes[offset + 1] & 0xff);
    }

    private long u32(byte[] bytes, int offset, ByteOrder order) {
        if (offset < 0 || offset + 4 > bytes.length) return Long.MAX_VALUE;
        long a = bytes[offset] & 0xffL, b = bytes[offset + 1] & 0xffL;
        long c = bytes[offset + 2] & 0xffL, d = bytes[offset + 3] & 0xffL;
        return order == ByteOrder.LITTLE_ENDIAN ? a | (b << 8) | (c << 16) | (d << 24)
                : (a << 24) | (b << 16) | (c << 8) | d;
    }

    private boolean ascii(byte[] bytes, int offset, String expected) {
        if (offset + expected.length() > bytes.length) return false;
        for (int i = 0; i < expected.length(); i++) if (bytes[offset + i] != (byte) expected.charAt(i)) return false;
        return true;
    }

    private BufferedImage flattenOnWhite(BufferedImage image) {
        BufferedImage rgb = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = rgb.createGraphics();
        try { graphics.setColor(java.awt.Color.WHITE); graphics.fillRect(0, 0, rgb.getWidth(), rgb.getHeight()); graphics.drawImage(image, 0, 0, null); }
        finally { graphics.dispose(); }
        return rgb;
    }

    private IllegalArgumentException invalid() { return new IllegalArgumentException("Arquivo de imagem inválido ou muito grande."); }
    public record ProcessedImage(byte[] bytes, String mediaType, String extension, int width, int height) { }
}
