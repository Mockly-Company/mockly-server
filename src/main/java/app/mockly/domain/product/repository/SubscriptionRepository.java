package app.mockly.domain.product.repository;

import app.mockly.domain.product.entity.Subscription;
import app.mockly.domain.product.entity.SubscriptionStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface SubscriptionRepository extends JpaRepository<Subscription, Long> {
    boolean existsByUserIdAndStatus(UUID userId, SubscriptionStatus status);

    Optional<Subscription> findByUserIdAndStatus(UUID userId, SubscriptionStatus status);
}
