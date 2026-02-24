package hackthon.fiap.luis.common;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

public final class SensitiveDataProtector {
    private static final String KEY_ENV = "CLIENT_DATA_ENCRYPTION_KEY";
    private static final String PREFIX = "enc:v1";
    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;
    private static final SecureRandom RANDOM = new SecureRandom();

    private SensitiveDataProtector() {
    }

    public static String encrypt(String plaintext) {
        if (plaintext == null || plaintext.isBlank()) {
            throw new IllegalArgumentException("Sensitive value is required");
        }
        try {
            byte[] iv = new byte[IV_BYTES];
            RANDOM.nextBytes(iv);

            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key(), new GCMParameterSpec(TAG_BITS, iv));
            byte[] encrypted = cipher.doFinal(plaintext.getBytes(java.nio.charset.StandardCharsets.UTF_8));

            return PREFIX + ":" +
                    Base64.getEncoder().encodeToString(iv) + ":" +
                    Base64.getEncoder().encodeToString(encrypted);
        } catch (Exception e) {
            throw new IllegalStateException("Could not encrypt sensitive data", e);
        }
    }

    public static String sha256(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Value is required");
        }
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder out = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                out.append(String.format("%02x", b));
            }
            return out.toString();
        } catch (Exception e) {
            throw new IllegalStateException("Could not hash value", e);
        }
    }

    public static String last4(String value) {
        if (value == null) {
            return "";
        }
        String clean = value.replaceAll("\\D", "");
        if (clean.length() <= 4) {
            return clean;
        }
        return clean.substring(clean.length() - 4);
    }

    private static SecretKeySpec key() {
        String raw = System.getenv(KEY_ENV);
        if (raw == null || raw.isBlank()) {
            throw new IllegalStateException(KEY_ENV + " is not configured");
        }
        byte[] decoded = Base64.getDecoder().decode(raw);
        if (decoded.length != 32) {
            throw new IllegalStateException(KEY_ENV + " must decode to 32 bytes (AES-256)");
        }
        return new SecretKeySpec(decoded, "AES");
    }
}
