package vikoba.service.contribution.service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import org.springframework.web.multipart.MultipartFile;

/** Accept only non-scriptable receipt formats and check their file signatures. */
public final class PaymentProofValidator {
    private PaymentProofValidator() {}

    public static String validate(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Attach a payment receipt or message screenshot as proof");
        }
        if (file.getSize() > 5L * 1024 * 1024) {
            throw new IllegalArgumentException("Payment proof must be 5 MB or smaller");
        }
        String type = file.getContentType();
        if (type == null || !Set.of("image/png", "image/jpeg", "image/webp", "application/pdf").contains(type)) {
            throw new IllegalArgumentException("Payment proof must be PNG, JPEG, WebP or PDF");
        }
        try (var stream = file.getInputStream()) {
            byte[] prefix = stream.readNBytes(12);
            boolean valid = switch (type) {
                case "image/png" -> startsWith(prefix, new byte[]{(byte) 137, 80, 78, 71, 13, 10, 26, 10});
                case "image/jpeg" -> startsWith(prefix, new byte[]{(byte) 255, (byte) 216, (byte) 255});
                case "application/pdf" -> startsWith(prefix, "%PDF-".getBytes(StandardCharsets.US_ASCII));
                case "image/webp" -> prefix.length >= 12
                        && startsWith(prefix, "RIFF".getBytes(StandardCharsets.US_ASCII))
                        && new String(prefix, 8, 4, StandardCharsets.US_ASCII).equals("WEBP");
                default -> false;
            };
            if (!valid) {
                throw new IllegalArgumentException("Payment proof content does not match its file type");
            }
            return type;
        } catch (IOException exception) {
            throw new IllegalArgumentException("Unable to read the proof file");
        }
    }

    private static boolean startsWith(byte[] bytes, byte[] signature) {
        if (bytes.length < signature.length) return false;
        for (int i = 0; i < signature.length; i++) {
            if (bytes[i] != signature[i]) return false;
        }
        return true;
    }
}
