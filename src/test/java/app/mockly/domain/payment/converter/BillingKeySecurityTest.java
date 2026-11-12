package app.mockly.domain.payment.converter;

import app.mockly.domain.auth.entity.OAuth2Provider;
import app.mockly.domain.auth.entity.User;
import app.mockly.domain.auth.repository.UserRepository;
import app.mockly.domain.payment.entity.PaymentMethod;
import app.mockly.domain.payment.repository.PaymentMethodRepository;
import app.mockly.global.security.SensitiveDataMasker;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Transactional
class BillingKeySecurityTest {

    @Autowired
    private PaymentMethodRepository paymentMethodRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private EntityManager entityManager;

    @Value("${payment.billing-key-encryption-secret}")
    private String configuredTestSecret;

    @AfterEach
    void restoreTestEncryptionKey() {
        BillingKeyCryptoHolder.initialize(configuredTestSecret);
    }

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
    @DisplayName("JPA 기본 생성자로 생성된 Converter도 PBKDF2 기반 AES-256-GCM(enc:v1:)으로 암호화·복호화한다")
    void converter_noArgConstructorEncryptsAndDecryptsViaStaticHolder() {
        BillingKeyCryptoHolder.initialize("test-32-byte-billing-key-secret!");
        BillingKeyCryptoConverter converter = new BillingKeyCryptoConverter();
        String plainBillingKey = "portone-billing-key-987654321";

        String encrypted1 = converter.convertToDatabaseColumn(plainBillingKey);
        String encrypted2 = converter.convertToDatabaseColumn(plainBillingKey);

        assertThat(encrypted1).startsWith("enc:v1:").doesNotContain(plainBillingKey);
        assertThat(encrypted2).startsWith("enc:v1:").isNotEqualTo(encrypted1);
        assertThat(converter.convertToEntityAttribute(encrypted1)).isEqualTo(plainBillingKey);
        assertThat(converter.convertToEntityAttribute(encrypted2)).isEqualTo(plainBillingKey);
    }

    @Test
    @DisplayName("시크릿이 비어 있거나 초기화되지 않은 상태에서는 평문 폴백 없이 즉시 예외(fail-fast)가 발생한다")
    void converter_failsFastWhenSecretBlankOrUninitialized() {
        assertThatThrownBy(() -> BillingKeyCryptoHolder.initialize(""))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> BillingKeyCryptoHolder.initialize("   "))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> BillingKeyCryptoHolder.initialize(null))
                .isInstanceOf(IllegalStateException.class);

        BillingKeyCryptoHolder.clearForTest();
        BillingKeyCryptoConverter converter = new BillingKeyCryptoConverter();
        assertThatThrownBy(() -> converter.convertToDatabaseColumn("plain_billing_key"))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> converter.convertToEntityAttribute("enc:v1:abc"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("암호화 홀더가 초기화된 상태에서는 기존 레거시 평문 행 읽기는 허용하되 저장은 항상 암호문으로 수행한다")
    void converter_readsLegacyPlaintextRowsDuringMigrationWindow() {
        BillingKeyCryptoHolder.initialize("migration-window-secret");
        BillingKeyCryptoConverter converter = new BillingKeyCryptoConverter();

        assertThat(converter.convertToEntityAttribute("legacy_plain_billing_key"))
                .isEqualTo("legacy_plain_billing_key");
        assertThat(converter.convertToDatabaseColumn("legacy_plain_billing_key"))
                .startsWith("enc:v1:")
                .doesNotContain("legacy_plain_billing_key");
    }

    @Test
    @DisplayName("변조된 암호문이나 잘못된 키로 복호화 시 예외가 발생한다")
    void converter_rejectsTamperedCiphertextOrWrongKey() {
        BillingKeyCryptoHolder.initialize("correct-secret");
        BillingKeyCryptoConverter converter = new BillingKeyCryptoConverter();
        String encrypted = converter.convertToDatabaseColumn("portone-billing-key-secret");

        BillingKeyCryptoHolder.initialize("wrong-secret");
        assertThatThrownBy(() -> converter.convertToEntityAttribute(encrypted))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("JPA PaymentMethod 저장 시 실제 DB billing_key 컬럼에는 평문이 아닌 enc:v1: 암호문이 저장된다")
    void paymentMethodJpaRoundTrip_persistsEncryptedColumnInDatabase() {
        User user = userRepository.saveAndFlush(User.builder()
                .provider(OAuth2Provider.GOOGLE)
                .providerId("crypto-jpa-roundtrip-user")
                .email("crypto-jpa@example.com")
                .name("암호화 검증 유저")
                .build());

        String plainBillingKey = "live_portone_billing_key_raw_value_9999";
        PaymentMethod saved = paymentMethodRepository.saveAndFlush(
                PaymentMethod.create(user, plainBillingKey, "1234****5678", "VISA", true)
        );

        String rawDbValue = jdbcTemplate.queryForObject(
                "SELECT billing_key FROM payment_method WHERE id = ?",
                String.class,
                saved.getId()
        );

        assertThat(rawDbValue)
                .isNotNull()
                .startsWith("enc:v1:")
                .isNotEqualTo(plainBillingKey)
                .doesNotContain(plainBillingKey);

        entityManager.clear();
        PaymentMethod reloaded = paymentMethodRepository.findById(saved.getId()).orElseThrow();
        assertThat(reloaded.getBillingKey()).isEqualTo(plainBillingKey);
    }
}
