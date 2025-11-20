package app.mockly.domain.auth.repository;

import app.mockly.domain.auth.entity.RefreshToken;
import app.mockly.domain.auth.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {
    Optional<RefreshToken> findByToken(String token);

    @Query("SELECT rt " +
            "FROM RefreshToken rt " +
            "WHERE rt.user = :user " +
            "AND rt.expiresAt > :now " +
            "ORDER BY rt.createdAt ASC")
    List<RefreshToken> findValidTokensByUser(@Param("user") User user, @Param("now") Instant now);
}
