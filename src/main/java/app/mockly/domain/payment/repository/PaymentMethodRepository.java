package app.mockly.domain.payment.repository;

import app.mockly.domain.payment.entity.PaymentMethod;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface PaymentMethodRepository extends JpaRepository<PaymentMethod, Long> {
    Optional<PaymentMethod> findByUserIdAndIsActiveTrue(UUID userId);
}
