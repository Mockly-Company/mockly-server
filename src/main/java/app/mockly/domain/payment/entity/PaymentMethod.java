package app.mockly.domain.payment.entity;

import app.mockly.domain.auth.entity.User;
import app.mockly.global.common.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "payment_method")
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class PaymentMethod extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "billing_key", nullable = false)
    private String billingKey;

    @Column(name = "card_number", length = 20)
    private String cardNumber;

    @Column(name = "card_brand", length = 20)
    private String cardBrand;

    @Builder.Default
    @Column(name = "is_active", nullable = false)
    private boolean isActive = true;

    @Builder.Default
    @Column(name = "is_default", nullable = false)
    private boolean isDefault = false;

    public void deactivate() {
        this.isActive = false;
    }

    public void setAsDefault() {
        this.isDefault = true;
    }

    public void unsetDefault() {
        this.isDefault = false;
    }

    public static PaymentMethod create(User user, String billingKey, String cardNumber, String cardBrand, boolean isDefault) {
        return PaymentMethod.builder()
                .user(user)
                .billingKey(billingKey)
                .cardNumber(cardNumber)
                .cardBrand(cardBrand)
                .isDefault(isDefault)
                .build();
    }

    public void update(String billingKey, String cardNumber, String cardBrand) {
        this.billingKey = billingKey;
        this.cardNumber = cardNumber;
        this.cardBrand = cardBrand;
        this.isActive = true;
    }
}
