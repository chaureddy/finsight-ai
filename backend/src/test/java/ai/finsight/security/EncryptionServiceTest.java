package ai.finsight.security;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class EncryptionServiceTest {
    private final EncryptionService service = new EncryptionService("test-secret-key-for-unit-testing-32b");

    @Test
    void encryptsAndDecryptsSuccessfully() {
        String plaintext = "access_token_sample_123456789";
        String encrypted = service.encrypt(plaintext);
        assertNotNull(encrypted);
        assertNotEquals(plaintext, encrypted);

        String decrypted = service.decrypt(encrypted);
        assertEquals(plaintext, decrypted);
    }

    @Test
    void producesDifferentCiphertextsForSamePlaintextDueToRandomIv() {
        String plaintext = "secret_payload";
        String enc1 = service.encrypt(plaintext);
        String enc2 = service.encrypt(plaintext);
        assertNotEquals(enc1, enc2);
        assertEquals(plaintext, service.decrypt(enc1));
        assertEquals(plaintext, service.decrypt(enc2));
    }

    @Test
    void handlesNullAndEmpty() {
        assertNull(service.encrypt(null));
        assertNull(service.decrypt(null));
        assertEquals("", service.encrypt(""));
        assertEquals("", service.decrypt(""));
    }

    @Test
    void throwsOnTamperedCiphertext() {
        String encrypted = service.encrypt("secure_data");
        // Tamper with base64 string
        String tampered = encrypted.substring(0, encrypted.length() - 4) + "AAAA";
        assertThrows(IllegalStateException.class, () -> service.decrypt(tampered));
    }
}

