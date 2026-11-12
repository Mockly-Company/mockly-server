package app.mockly.domain.auth.entity;

import app.mockly.domain.auth.dto.AppleUser;
import app.mockly.domain.auth.dto.GoogleUser;
import app.mockly.global.common.BaseEntity;
import com.fasterxml.uuid.Generators;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "users", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"provider", "provider_id"})
})
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class User extends BaseEntity {
    public static final String WITHDRAWN_USER_NAME = "탈퇴한 회원";
    private static final String WITHDRAWN_EMAIL_DOMAIN = "@withdrawn.mockly.app";
    private static final String DEFAULT_APPLE_USER_NAME = "Mockly 사용자";

    @Id
    @Column(columnDefinition = "UUID")
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OAuth2Provider provider;

    @Column(name = "provider_id", nullable = false)
    private String providerId;

    @Column(unique = true, nullable = false)
    private String email;

    @Column(nullable = false)
    private String name;

    @Column(name = "job_category", length = 30)
    private String jobCategory;

    @Column(name = "job_role", length = 30)
    private String jobRole;

    @Column(name = "experience_years")
    private Integer experienceYears;

    @Column(name = "terms_agreed_at")
    private Instant termsAgreedAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @Builder.Default
    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Session> sessions = new ArrayList<>();

    public static User from(GoogleUser googleUser) {
        return User.builder()
                .provider(OAuth2Provider.GOOGLE)
                .providerId(googleUser.sub())
                .email(googleUser.email())
                .name(googleUser.name())
                .build();
    }

    public static User fromApple(AppleUser appleUser, String fullName) {
        String resolvedEmail = (appleUser.email() != null && !appleUser.email().isBlank())
                ? appleUser.email()
                : appleUser.sub() + "@privaterelay.appleid.com";
        String resolvedName = resolveAppleName(fullName, resolvedEmail);
        return User.builder()
                .provider(OAuth2Provider.APPLE)
                .providerId(appleUser.sub())
                .email(resolvedEmail)
                .name(resolvedName)
                .build();
    }

    private static String resolveAppleName(String fullName, String email) {
        if (fullName != null && !fullName.isBlank()) {
            return fullName.trim();
        }
        int atIndex = email.indexOf('@');
        if (atIndex > 0) {
            return email.substring(0, atIndex);
        }
        return DEFAULT_APPLE_USER_NAME;
    }

    public boolean hasAgreedToTerms() {
        return this.termsAgreedAt != null;
    }

    public boolean isDeleted() {
        return this.deletedAt != null;
    }

    public void agreeToTerms(Instant agreedAt) {
        this.termsAgreedAt = agreedAt;
    }

    public void updateProfile(String name, String jobCategory, String jobRole, Integer experienceYears) {
        this.name = name.trim();
        this.jobCategory = normalizeNullableString(jobCategory);
        this.jobRole = normalizeNullableString(jobRole);
        this.experienceYears = experienceYears;
    }

    public void withdraw(Instant withdrawnAt) {
        this.email = "deleted-" + this.id + WITHDRAWN_EMAIL_DOMAIN;
        this.name = WITHDRAWN_USER_NAME;
        this.providerId = "deleted-" + this.id;
        this.jobCategory = null;
        this.jobRole = null;
        this.experienceYears = null;
        this.deletedAt = withdrawnAt;
    }

    private static String normalizeNullableString(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    @PrePersist
    public void generateId() {
        if (this.id == null) {
            this.id = Generators.timeBasedEpochGenerator().generate();
        }
    }
}
