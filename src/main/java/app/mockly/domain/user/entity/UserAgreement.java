package app.mockly.domain.user.entity;

import app.mockly.domain.auth.entity.User;
import app.mockly.global.common.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "user_agreement", uniqueConstraints = {
        @UniqueConstraint(name = "uk_user_agreement_user_type", columnNames = {"user_id", "agreement_type"})
})
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class UserAgreement extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(name = "agreement_type", nullable = false, length = 30)
    private AgreementType agreementType;

    @Column(nullable = false)
    private boolean agreed;

    @Column(name = "agreed_at", nullable = false)
    private Instant agreedAt;

    public static UserAgreement create(User user, AgreementType agreementType, boolean agreed, Instant agreedAt) {
        return UserAgreement.builder()
                .user(user)
                .agreementType(agreementType)
                .agreed(agreed)
                .agreedAt(agreedAt)
                .build();
    }

    public void updateAgreement(boolean agreed, Instant agreedAt) {
        this.agreed = agreed;
        this.agreedAt = agreedAt;
    }
}
