package app.mockly.domain.user.repository;

import app.mockly.domain.user.entity.AgreementType;
import app.mockly.domain.user.entity.UserAgreement;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserAgreementRepository extends JpaRepository<UserAgreement, Long> {
    List<UserAgreement> findByUserId(UUID userId);

    Optional<UserAgreement> findByUserIdAndAgreementType(UUID userId, AgreementType agreementType);
}
