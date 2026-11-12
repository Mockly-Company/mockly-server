package app.mockly.domain.meetup.entity;

import app.mockly.domain.auth.entity.User;
import app.mockly.global.common.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(
        name = "local_interview_meetup_participant",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_meetup_participant_user", columnNames = {"meetup_id", "user_id"})
        }
)
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class LocalInterviewMeetupParticipant extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "meetup_id", nullable = false)
    private LocalInterviewMeetup meetup;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "is_host", nullable = false)
    private boolean host;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MeetupParticipationStatus status;

    @Column(length = 300)
    private String message;

    @Enumerated(EnumType.STRING)
    @Column(name = "job_category", nullable = false, length = 30)
    private MeetupJobCategory jobCategory;

    @Enumerated(EnumType.STRING)
    @Column(name = "job_role", nullable = false, length = 30)
    private MeetupJobRole jobRole;

    @Column(name = "experience_years", nullable = false)
    private int experienceYears;

    @Enumerated(EnumType.STRING)
    @Column(name = "experience_level", nullable = false, length = 20)
    private MeetupExperienceLevel experienceLevel;

    @Column(name = "applied_at", nullable = false)
    private Instant appliedAt;

    @Column(name = "reviewed_at")
    private Instant reviewedAt;

    public record ProfileSnapshot(
            MeetupJobCategory jobCategory,
            MeetupJobRole jobRole,
            int experienceYears,
            MeetupExperienceLevel experienceLevel
    ) {
    }

    public static LocalInterviewMeetupParticipant createHost(
            LocalInterviewMeetup meetup,
            User hostUser,
            ProfileSnapshot profileSnapshot,
            Instant now
    ) {
        return LocalInterviewMeetupParticipant.builder()
                .meetup(meetup)
                .user(hostUser)
                .host(true)
                .status(MeetupParticipationStatus.APPROVED)
                .message(null)
                .jobCategory(profileSnapshot.jobCategory())
                .jobRole(profileSnapshot.jobRole())
                .experienceYears(profileSnapshot.experienceYears())
                .experienceLevel(profileSnapshot.experienceLevel())
                .appliedAt(now)
                .reviewedAt(now)
                .build();
    }

    public static LocalInterviewMeetupParticipant createApplicant(
            LocalInterviewMeetup meetup,
            User applicant,
            String message,
            ProfileSnapshot profileSnapshot,
            Instant now
    ) {
        return LocalInterviewMeetupParticipant.builder()
                .meetup(meetup)
                .user(applicant)
                .host(false)
                .status(MeetupParticipationStatus.PENDING)
                .message(message)
                .jobCategory(profileSnapshot.jobCategory())
                .jobRole(profileSnapshot.jobRole())
                .experienceYears(profileSnapshot.experienceYears())
                .experienceLevel(profileSnapshot.experienceLevel())
                .appliedAt(now)
                .build();
    }

    public void reapply(String message, ProfileSnapshot profileSnapshot, Instant now) {
        this.status = MeetupParticipationStatus.PENDING;
        this.message = message;
        this.jobCategory = profileSnapshot.jobCategory();
        this.jobRole = profileSnapshot.jobRole();
        this.experienceYears = profileSnapshot.experienceYears();
        this.experienceLevel = profileSnapshot.experienceLevel();
        this.appliedAt = now;
        this.reviewedAt = null;
    }

    public void approve(Instant now) {
        this.status = MeetupParticipationStatus.APPROVED;
        this.reviewedAt = now;
    }

    public void reject(Instant now) {
        this.status = MeetupParticipationStatus.REJECTED;
        this.reviewedAt = now;
    }

    public void cancel(Instant now) {
        this.status = MeetupParticipationStatus.CANCELLED;
        this.reviewedAt = now;
    }
}
