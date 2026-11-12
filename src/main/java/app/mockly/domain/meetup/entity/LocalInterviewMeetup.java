package app.mockly.domain.meetup.entity;

import app.mockly.domain.auth.entity.User;
import app.mockly.global.common.BaseEntity;
import com.fasterxml.uuid.Generators;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
        name = "local_interview_meetup",
        indexes = {
                @Index(name = "idx_meetup_lat_lng", columnList = "latitude, longitude"),
                @Index(name = "idx_meetup_starts_at", columnList = "starts_at")
        }
)
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class LocalInterviewMeetup extends BaseEntity {

    @Id
    @Column(columnDefinition = "UUID")
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "host_user_id", nullable = false)
    private User host;

    @Column(nullable = false, length = 100)
    private String title;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "job_category", nullable = false, length = 30)
    private MeetupJobCategory jobCategory;

    @Enumerated(EnumType.STRING)
    @Column(name = "job_role", nullable = false, length = 30)
    private MeetupJobRole jobRole;

    @Enumerated(EnumType.STRING)
    @Column(name = "interview_type", nullable = false, length = 30)
    private MeetupInterviewType interviewType;

    @Enumerated(EnumType.STRING)
    @Column(name = "experience_level", nullable = false, length = 20)
    private MeetupExperienceLevel experienceLevel;

    @Enumerated(EnumType.STRING)
    @Column(name = "location_status", nullable = false, length = 30)
    private MeetupLocationStatus locationStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "meeting_type", nullable = false, length = 20)
    private MeetupMeetingType meetingType;

    @Column(name = "place_name", nullable = false, length = 120)
    private String placeName;

    @Column(name = "address_label", nullable = false, length = 200)
    private String addressLabel;

    @Column(name = "full_address", length = 300)
    private String fullAddress;

    @Column(nullable = false)
    private double latitude;

    @Column(nullable = false)
    private double longitude;

    @Column(name = "starts_at", nullable = false)
    private Instant startsAt;

    @Column(name = "ends_at", nullable = false)
    private Instant endsAt;

    @Column(name = "recruit_deadline", nullable = false)
    private Instant recruitDeadline;

    @Column(name = "current_participants", nullable = false)
    private int currentParticipants;

    @Column(name = "max_participants", nullable = false)
    private int maxParticipants;

    @PrePersist
    public void generateId() {
        if (this.id == null) {
            this.id = Generators.timeBasedEpochGenerator().generate();
        }
    }

    public boolean isFull() {
        return this.currentParticipants >= this.maxParticipants;
    }

    public boolean isHost(UUID userId) {
        return userId != null && this.host.getId().equals(userId);
    }

    public boolean isRecruitmentClosed(Instant now) {
        return !now.isBefore(this.recruitDeadline) || !now.isBefore(this.startsAt);
    }

    public void incrementParticipants() {
        this.currentParticipants += 1;
    }

    public void decrementParticipants() {
        if (this.currentParticipants > 1) {
            this.currentParticipants -= 1;
        }
    }

    public String resolveConfirmedAddress() {
        if (this.fullAddress != null && !this.fullAddress.isBlank()) {
            return this.fullAddress;
        }
        return this.addressLabel;
    }
}
