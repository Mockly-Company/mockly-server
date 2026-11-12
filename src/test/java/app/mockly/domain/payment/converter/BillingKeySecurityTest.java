package app.mockly.domain.payment.converter;

import app.mockly.global.security.SensitiveDataMasker;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BillingKeySecurityTest {

    @Test
    @DisplayName("빌링키 마스킹은 앞 4자리만 남기고 나머지를 가린다")
    void maskBillingKey_masksAllExceptFirstFourCharacters() {
        assertThat(SensitiveDataMasker.maskBillingKey("billing-key-secret-12345")).isEqualTo("bill****");
        assertThat(SensitiveDataMasker.maskBillingKey("abcd")).isEqualTo("****");
        assertThat(SensitiveDataMasker.maskBillingKey("")).isEqualTo("****");
        assertThat(SensitiveDataMasker.maskBillingKey(null)).isEqualTo("****");
    }

    @Test
    @DisplayName("카드번호 마스킹은 이미 마스킹된 문자열은 유지하고 평문 숫자는 마스킹한다")
    void maskCardNumber_handlesMaskedAndPlainCardNumbers() {
        assertThat(SensitiveDataMasker.maskCardNumber("1234****5678")).isEqualTo("1234****5678");
        assertThat(SensitiveDataMasker.maskCardNumber("1234567890123456")).isEqualTo("1234****3456");
        assertThat(SensitiveDataMasker.maskCardNumber(null)).isNull();
    }

    @Test
    @DisplayName("암호화 키 설정 시 AES-GCM으로 암호화·복호화하며 매번 다른 IV를 사용한다")
    void converter_encryptsAndDecryptsWithRandomIvWhenSecretProvided() {
        BillingKeyCryptoConverter converter = new BillingKeyCryptoConverter("test-32-byte-billing-key-secret!");
        String plainBillingKey = "portone-billing-key-987654321";

        String encrypted1 = converter.convertToDatabaseColumn(plainBillingKey);
        String encrypted2 = converter.convertToDatabaseColumn(plainBillingKey);

        assertThat(encrypted1).startsWith("enc:v1:").doesNotContain(plainBillingKey);
        assertThat(encrypted2).startsWith("enc:v1:").isNotEqualTo(encrypted1);
        assertThat(converter.convertToEntityAttribute(encrypted1)).isEqualTo(plainBillingKey);
        assertThat(converter.convertToEntityAttribute(encrypted2)).isEqualTo(plainBillingKey);
    }

    @Test
    @DisplayName("기존 평문 빌링키 데이터는 암호화 키 유무와 관계없이 하위 호환으로 읽을 수 있다")
    void converter_supportsLegacyPlaintextFallback() {
        BillingKeyCryptoConverter withKey = new BillingKeyCryptoConverter("test-secret");
        BillingKeyCryptoConverter withoutKey = new BillingKeyCryptoConverter("");

        assertThat(withKey.convertToEntityAttribute("legacy_plain_billing_key"))
                .isEqualTo("legacy_plain_billing_key");
        assertThat(withoutKey.convertToDatabaseColumn("plain_billing_key"))
                .isEqualTo("plain_billing_key");
        assertThat(withoutKey.convertToEntityAttribute("plain_billing_key"))
                .isEqualTo("plain_billing_key");
    }

    @Test
    @DisplayName("변조된 암호문이나 잘못된 키로 복호화 시 예외가 발생한다")
    void converter_rejectsTamperedCiphertextOrWrongKey() {
        BillingKeyCryptoConverter encryptor = new BillingKeyCryptoConverter("correct-secret");
        BillingKeyCryptoConverter wrongKeyDecryptor = new BillingKeyCryptoConverter("wrong-secret");

        String encrypted = encryptor.convertToDatabaseColumn("portone-billing-key-secret");

        assertThatThrownBy(() -> wrongKeyDecryptor.convertToEntityAttribute(encrypted))
                .isInstanceOf(IllegalStateException.class);
    }
}
