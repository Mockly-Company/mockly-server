package app.mockly.domain.auth.repository;

import app.mockly.domain.auth.entity.Session;
import app.mockly.domain.auth.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SessionRepository extends JpaRepository<Session, Long> {
    Optional<Session> findByUserAndDeviceId(User user, String deviceId);

    @Query("SELECT s " +
            "FROM Session s " +
            "WHERE s.user = :user " +
            "AND s.refreshToken IS NOT NULL " +
            "AND s.refreshToken.expiresAt > :now " +
            "ORDER BY s.refreshToken.createdAt ASC")
    List<Session> findActiveSessionsByUser(@Param("user") User user, @Param("now") Instant now);

    List<Session> findByUserIdOrderByLastAccessedAtDesc(UUID userId);
}
