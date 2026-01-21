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

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    @Column(name = "billing_key", nullable = false)
    private String billingKey;

    @Column(name = "card_last4", length = 4)
    private String cardLast4;

    @Column(name = "card_brand", length = 20)
    private String cardBrand;

    @Builder.Default
    @Column(name = "is_active", nullable = false)
    private boolean isActive = true;

    public void deactivate() {
        this.isActive = false;
    }

    public void update(String billingKey, String cardLast4, String cardBrand) {
        this.billingKey = billingKey;
        this.cardLast4 = cardLast4;
        this.cardBrand = cardBrand;
        this.isActive = true;
    }
}
