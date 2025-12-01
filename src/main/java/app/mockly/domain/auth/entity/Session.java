package app.mockly.domain.auth.entity;

import app.mockly.domain.auth.dto.DeviceInfo;
import app.mockly.domain.auth.dto.LocationInfo;
import app.mockly.global.common.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "session")
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class Session extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(mappedBy = "session", cascade = CascadeType.ALL, orphanRemoval = true)
    private RefreshToken refreshToken; // 로그아웃 시 null

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(unique = true, nullable = false)
    private String deviceId;

    @Column(nullable = false)
    private String deviceName;

    private Double latitude;
    private Double longitude;

    private Instant lastAccessedAt;

    public static Session create(User user, DeviceInfo deviceInfo, LocationInfo locationInfo) {
        return Session.builder()
                .user(user)
                .deviceId(deviceInfo.deviceId())
                .deviceName(deviceInfo.deviceName())
                .latitude(locationInfo != null ? locationInfo.latitude() : null)
                .longitude(locationInfo != null ? locationInfo.longitude() : null)
                .lastAccessedAt(Instant.now())
                .build();
    }

    public void updateAccessInfo(LocationInfo locationInfo) {
        this.latitude = locationInfo != null ? locationInfo.latitude() : null;
        this.longitude = locationInfo != null ? locationInfo.longitude() : null;
        this.lastAccessedAt = Instant.now();
    }

    public void updateRefreshToken(RefreshToken refreshToken) {
        this.refreshToken = refreshToken;
        if (refreshToken != null) {
            refreshToken.setSession(this);
        }
    }
}
