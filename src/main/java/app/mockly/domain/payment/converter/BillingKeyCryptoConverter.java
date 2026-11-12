package app.mockly.domain.payment.converter;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

@Component
@Converter
public class BillingKeyCryptoConverter implements AttributeConverter<String, String> {

    static final String ENCRYPTED_PREFIX = "enc:v1:";
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int IV_LENGTH_BYTES = 12;
    private static final int TAG_LENGTH_BITS = 128;

    private final SecureRandom secureRandom = new SecureRandom();
    private final SecretKeySpec secretKeySpec;

    public BillingKeyCryptoConverter() {
        this("");
    }

    @Autowired
    public BillingKeyCryptoConverter(
            @Value("${payment.billing-key-encryption-secret:}") String encryptionSecret
    ) {
        if (encryptionSecret == null || encryptionSecret.isBlank()) {
            this.secretKeySpec = null;
        } else {
            this.secretKeySpec = deriveAesKey(encryptionSecret);
        }
    }

    @Override
    public String convertToDatabaseColumn(String attribute) {
        if (attribute == null) {
            return null;
        }
        if (secretKeySpec == null || attribute.startsWith(ENCRYPTED_PREFIX)) {
            return attribute;
        }

        try {
            byte[] iv = new byte[IV_LENGTH_BYTES];
            secureRandom.nextBytes(iv);

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, secretKeySpec, new GCMParameterSpec(TAG_LENGTH_BITS, iv));
            byte[] cipherText = cipher.doFinal(attribute.getBytes(StandardCharsets.UTF_8));

            ByteBuffer buffer = ByteBuffer.allocate(iv.length + cipherText.length);
            buffer.put(iv);
            buffer.put(cipherText);
            return ENCRYPTED_PREFIX + Base64.getEncoder().encodeToString(buffer.array());
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("빌링키 암호화 중 오류가 발생했습니다.", e);
        }
    }

    @Override
    public String convertToEntityAttribute(String dbData) {
        if (dbData == null) {
            return null;
        }
        if (!dbData.startsWith(ENCRYPTED_PREFIX)) {
            return dbData;
        }
        if (secretKeySpec == null) {
            throw new IllegalStateException("암호화된 빌링키를 복호화하기 위한 암호화 키가 설정되지 않았습니다.");
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
            cipher.init(Cipher.DECRYPT_MODE, secretKeySpec, new GCMParameterSpec(TAG_LENGTH_BITS, iv));
            byte[] plainBytes = cipher.doFinal(cipherText);
            return new String(plainBytes, StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            throw new IllegalStateException("빌링키 복호화 중 오류가 발생했습니다.", e);
        }
    }

    private static SecretKeySpec deriveAesKey(String secret) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] keyBytes = digest.digest(secret.getBytes(StandardCharsets.UTF_8));
            return new SecretKeySpec(keyBytes, "AES");
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("AES 키 파생 실패", e);
        }
    }
}
