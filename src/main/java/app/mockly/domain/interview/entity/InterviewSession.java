package app.mockly.domain.interview.entity;

import app.mockly.domain.auth.entity.User;
import app.mockly.global.common.BaseEntity;
import com.fasterxml.uuid.Generators;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
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

    @Column(length = 100)
    private String firstQuestionKeyword;

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

    public void complete() {
        this.status = InterviewSessionStatus.COMPLETED;
        this.completedAt = Instant.now();
    }

    public void abandon() {
        this.status = InterviewSessionStatus.ABANDONED;
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

    @PrePersist
    public void generateId() {
        if (this.id == null) {
            this.id = Generators.timeBasedEpochGenerator().generate();
        }
    }
}
