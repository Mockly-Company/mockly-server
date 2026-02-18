package app.mockly.domain.payment.repository;

import app.mockly.domain.payment.entity.Payment;
import app.mockly.domain.payment.entity.PaymentStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface PaymentRepository extends JpaRepository<Payment, String> {

    @Query("""
            SELECT p FROM Payment p
                JOIN FETCH p.invoice i
                JOIN FETCH i.subscription s
                JOIN FETCH s.subscriptionPlan sp
                JOIN FETCH sp.product
            WHERE p.id = :paymentId AND s.userId = :userId
            """)
    Optional<Payment> findByIdAndUserId(@Param("paymentId") String paymentId, @Param("userId") UUID userId);

    @Query("""
            SELECT p FROM Payment p
                JOIN FETCH p.invoice i
                JOIN FETCH i.subscription s
                JOIN FETCH s.subscriptionPlan sp
                JOIN FETCH sp.product
            WHERE s.userId = :userId
            ORDER BY p.createdAt DESC
            """)
    Page<Payment> findByUserId(@Param("userId") UUID userId, Pageable pageable);

    @Query("""
            SELECT p FROM Payment p
                JOIN FETCH p.invoice i
                JOIN FETCH i.subscription s
                JOIN FETCH s.subscriptionPlan sp
                JOIN FETCH sp.product
            WHERE s.userId = :userId AND p.status = :status
            ORDER BY p.createdAt DESC
            """)
    Page<Payment> findByUserIdAndStatus(
            @Param("userId") UUID userId,
            @Param("status") PaymentStatus status,
            Pageable pageable
    );

    @Query("""
            SELECT p FROM Payment p
                JOIN FETCH p.invoice i
                JOIN FETCH i.subscription s
                JOIN FETCH s.subscriptionPlan sp
                JOIN FETCH sp.product
            WHERE s.userId = :userId
                AND p.createdAt BETWEEN :startDate AND :endDate
            ORDER BY p.createdAt DESC
            """)
    Page<Payment> findByUserIdAndCreatedAtBetween(
            @Param("userId") UUID userId,
            @Param("startDate") Instant startDate,
            @Param("endDate") Instant endDate,
            Pageable pageable
    );

    @Query("""
            SELECT p FROM Payment p
                JOIN FETCH p.invoice i
                JOIN FETCH i.subscription s
                JOIN FETCH s.subscriptionPlan sp
                JOIN FETCH sp.product
            WHERE s.userId = :userId
                AND p.status = :status
                AND p.createdAt BETWEEN :startDate AND :endDate
            ORDER BY p.createdAt DESC
            """)
    Page<Payment> findByUserIdAndStatusAndCreatedAtBetween(
            @Param("userId") UUID userId,
            @Param("status") PaymentStatus status,
            @Param("startDate") Instant startDate,
            @Param("endDate") Instant endDate,
            Pageable pageable
    );
}
