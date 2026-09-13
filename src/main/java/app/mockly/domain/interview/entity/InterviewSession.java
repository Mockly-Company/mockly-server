package app.mockly.domain.interview.entity;

import app.mockly.domain.auth.entity.User;
import app.mockly.domain.product.entity.PlanTier;
import app.mockly.global.common.BaseEntity;
import com.fasterxml.uuid.Generators;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.time.Duration;
import java.util.UUID;

@Entity
@Table(name = "interview_session")
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class InterviewSession extends BaseEntity {

    @Id
    @Column(columnDefinition = "UUID")
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false, length = 100)
    private String position;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String selfIntroduction;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ExperienceLevel experienceLevel;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private InterviewType interviewType;

    @Column(nullable = false)
    private int totalQuestions;

    @Column(nullable = false)
    private int currentQuestionNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private InterviewSessionStatus status;

    private Instant completedAt;

    @Column(name = "ended_at")
    private Instant endedAt;

    @Column(length = 100)
    private String firstQuestionKeyword;

    @Enumerated(EnumType.STRING)
    @Column(name = "feedback_generation_tier", length = 16)
    private PlanTier feedbackGenerationTier;

    @Column(name = "feedback_generation_task_id", columnDefinition = "UUID")
    private UUID feedbackGenerationTaskId;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private FeedbackStatus feedbackStatus;

    @Column(length = 500)
    private String failReason;

    @OneToOne(mappedBy = "session", fetch = FetchType.LAZY)
    private InterviewFeedback feedback;

    public static InterviewSession create(User user, String position, ExperienceLevel experienceLevel,
                                          InterviewType interviewType, int totalQuestions, String selfIntroduction) {
        return InterviewSession.builder()
                .user(user)
                .position(position)
                .experienceLevel(experienceLevel)
                .interviewType(interviewType)
                .totalQuestions(totalQuestions)
                .selfIntroduction(selfIntroduction)
                .currentQuestionNumber(0)
                .status(InterviewSessionStatus.IN_PROGRESS)
                .build();
    }

    public void incrementQuestionNumber() {
        this.currentQuestionNumber++;
    }

    public void startFeedbackGeneration(PlanTier generationTier) {
        this.status = InterviewSessionStatus.FEEDBACK_PENDING;
        this.feedbackStatus = FeedbackStatus.PENDING;
        this.feedbackGenerationTier = generationTier;
        if (this.endedAt == null) {
            this.endedAt = Instant.now();
        }
    }

    public void complete() {
        this.status = InterviewSessionStatus.COMPLETED;
        this.feedbackStatus = FeedbackStatus.COMPLETED;
        this.feedbackGenerationTaskId = null;
        this.completedAt = Instant.now();
    }

    public void resetFeedbackStatus() {
        this.feedbackStatus = FeedbackStatus.PENDING;
        this.feedbackGenerationTaskId = null;
        this.failReason = null;
    }

    public void abandon() {
        this.status = InterviewSessionStatus.ABANDONED;
        this.endedAt = Instant.now();
    }

    public void setFirstQuestionKeyword(String keyword) {
        this.firstQuestionKeyword = keyword;
    }

    public boolean isAllQuestionsAnswered() {
        return currentQuestionNumber >= totalQuestions;
    }

    public boolean isInProgress() {
        return status == InterviewSessionStatus.IN_PROGRESS;
    }

    public Long getDurationSeconds() {
        if (getCreatedAt() == null || endedAt == null) {
            return null;
        }
        return Duration.between(getCreatedAt(), endedAt).getSeconds();
    }

    public Integer getOverallScore() {
        return feedback == null ? null : feedback.getOverallScore();
    }

    @PrePersist
    public void generateId() {
        if (this.id == null) {
            this.id = Generators.timeBasedEpochGenerator().generate();
        }
    }
}
