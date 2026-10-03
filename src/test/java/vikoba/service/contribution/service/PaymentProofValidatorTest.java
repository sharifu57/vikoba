package vikoba.service.contribution.service;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import static org.junit.jupiter.api.Assertions.*;

class PaymentProofValidatorTest {
    @Test void rejectsScriptableSvg() {
        var file = new MockMultipartFile("proof", "receipt.svg", "image/svg+xml",
                "<svg onload='alert(1)'/>".getBytes());
        assertThrows(IllegalArgumentException.class, () -> PaymentProofValidator.validate(file));
    }

    @Test void rejectsHtmlDisguisedAsPng() {
        var file = new MockMultipartFile("proof", "receipt.png", "image/png", "<html>attack</html>".getBytes());
        assertThrows(IllegalArgumentException.class, () -> PaymentProofValidator.validate(file));
    }

    @Test void acceptsPngSignature() {
        var file = new MockMultipartFile("proof", "receipt.png", "image/png",
                new byte[]{(byte) 137, 80, 78, 71, 13, 10, 26, 10, 0, 0, 0, 0});
        assertEquals("image/png", PaymentProofValidator.validate(file));
    }

    @Test void acceptsPdfSignature() {
        var file = new MockMultipartFile("proof", "receipt.pdf", "application/pdf", "%PDF-1.7\n".getBytes());
        assertEquals("application/pdf", PaymentProofValidator.validate(file));
    }

    @Test void rejectsOversizedReceipt() {
        var file = new MockMultipartFile("proof", "receipt.png", "image/png", new byte[5 * 1024 * 1024 + 1]);
        assertThrows(IllegalArgumentException.class, () -> PaymentProofValidator.validate(file));
    }

    @Test void rejectsEmptyFile() {
        assertThrows(IllegalArgumentException.class, () -> PaymentProofValidator.validate(
                new MockMultipartFile("proof", new byte[0])));
    }
}
