package app.mockly.domain.payment.converter;

import javax.crypto.Cipher;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;

public final class BillingKeyCryptoHolder {

    public static final String ENCRYPTED_PREFIX = "enc:v1:";
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final String KDF_ALGORITHM = "PBKDF2WithHmacSHA256";
    private static final byte[] KDF_SALT = "mockly-billing-key-aes-gcm-v1".getBytes(StandardCharsets.UTF_8);
    private static final int KDF_ITERATIONS = 65_536;
    private static final int KEY_LENGTH_BITS = 256;
    private static final int IV_LENGTH_BYTES = 12;
    private static final int TAG_LENGTH_BITS = 128;

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static volatile SecretKeySpec secretKeySpec;

    private BillingKeyCryptoHolder() {
    }

    public static void initialize(String encryptionSecret) {
        if (encryptionSecret == null || encryptionSecret.isBlank()) {
            throw new IllegalStateException(
                    "빌링키 암호화 시크릿(payment.billing-key-encryption-secret)은 비어 있을 수 없습니다."
            );
        }
        secretKeySpec = deriveAesKey(encryptionSecret);
    }

    static void clearForTest() {
        secretKeySpec = null;
    }

    public static String encrypt(String plainText) {
        if (plainText == null) {
            return null;
        }
        SecretKeySpec key = requireInitializedKey();
        if (plainText.startsWith(ENCRYPTED_PREFIX)) {
            return plainText;
        }

        try {
            byte[] iv = new byte[IV_LENGTH_BYTES];
            SECURE_RANDOM.nextBytes(iv);

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_LENGTH_BITS, iv));
            byte[] cipherText = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));

            ByteBuffer buffer = ByteBuffer.allocate(iv.length + cipherText.length);
            buffer.put(iv);
            buffer.put(cipherText);
            return ENCRYPTED_PREFIX + Base64.getEncoder().encodeToString(buffer.array());
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("빌링키 암호화 중 오류가 발생했습니다.", e);
        }
    }

    public static String decrypt(String dbData) {
        if (dbData == null) {
            return null;
        }
        SecretKeySpec key = requireInitializedKey();
        if (!dbData.startsWith(ENCRYPTED_PREFIX)) {
            return dbData;
        }

        try {
            byte[] decoded = Base64.getDecoder().decode(dbData.substring(ENCRYPTED_PREFIX.length()));
            if (decoded.length <= IV_LENGTH_BYTES) {
                throw new IllegalStateException("손상된 암호화 빌링키 데이터입니다.");
            }
            ByteBuffer buffer = ByteBuffer.wrap(decoded);
            byte[] iv = new byte[IV_LENGTH_BYTES];
            buffer.get(iv);
            byte[] cipherText = new byte[buffer.remaining()];
            buffer.get(cipherText);

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_LENGTH_BITS, iv));
            byte[] plainBytes = cipher.doFinal(cipherText);
            return new String(plainBytes, StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            throw new IllegalStateException("빌링키 복호화 중 오류가 발생했습니다.", e);
        }
    }

    private static SecretKeySpec requireInitializedKey() {
        SecretKeySpec currentKey = secretKeySpec;
        if (currentKey == null) {
            throw new IllegalStateException("빌링키 암호화 홀더가 초기화되지 않았습니다.");
        }
        return currentKey;
    }

    private static SecretKeySpec deriveAesKey(String secret) {
        PBEKeySpec spec = new PBEKeySpec(secret.toCharArray(), KDF_SALT, KDF_ITERATIONS, KEY_LENGTH_BITS);
        try {
            SecretKeyFactory factory = SecretKeyFactory.getInstance(KDF_ALGORITHM);
            byte[] keyBytes = factory.generateSecret(spec).getEncoded();
            return new SecretKeySpec(keyBytes, "AES");
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("PBKDF2 기반 AES-256 키 파생에 실패했습니다.", e);
        } finally {
            spec.clearPassword();
        }
    }
}
