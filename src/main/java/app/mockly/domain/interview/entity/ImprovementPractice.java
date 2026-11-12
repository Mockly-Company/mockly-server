package app.mockly.domain.interview.entity;

import app.mockly.domain.interview.dto.ImprovementPracticeEvaluationResult;
import app.mockly.global.common.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

@Entity
@Table(name = "improvement_practice")
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class ImprovementPractice extends BaseEntity {

    public static final int MAX_MANUAL_RETRIES = 3;

    private static final int MAX_SUMMARY_LENGTH = 300;
    private static final int MAX_FAIL_REASON_LENGTH = 500;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, columnDefinition = "UUID")
    private UUID userId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "session_id", nullable = false)
    private InterviewSession session;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "improvement_id", nullable = false)
    private FeedbackImprovement improvement;

    @Column(name = "question_number", nullable = false)
    private int questionNumber;

    @Column(name = "question_text", nullable = false, columnDefinition = "TEXT")
    private String questionText;

    @Column(name = "original_answer", nullable = false, columnDefinition = "TEXT")
    private String originalAnswer;

    @Column(name = "submitted_answer", columnDefinition = "TEXT")
    private String submittedAnswer;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ImprovementPracticeStatus status;

    @Column
    private Integer score;

    @Column(length = MAX_SUMMARY_LENGTH)
    private String summary;

    @Column(columnDefinition = "TEXT")
    private String detail;

    @Column(columnDefinition = "TEXT")
    private String quote;

    @Column(name = "fail_reason", length = MAX_FAIL_REASON_LENGTH)
    private String failReason;

    @Builder.Default
    @Column(name = "retry_count", nullable = false)
    private int retryCount = 0;

    public static ImprovementPractice createPending(
            UUID userId,
            InterviewSession session,
            FeedbackImprovement improvement,
            int questionNumber,
            String questionText,
            String originalAnswer
    ) {
        return ImprovementPractice.builder()
                .userId(userId)
                .session(session)
                .improvement(improvement)
                .questionNumber(questionNumber)
                .questionText(questionText)
                .originalAnswer(originalAnswer)
                .status(ImprovementPracticeStatus.PENDING)
                .retryCount(0)
                .build();
    }

    public void submitAnswer(String answer) {
        this.submittedAnswer = answer;
        this.status = ImprovementPracticeStatus.GENERATING;
        this.failReason = null;
    }

    public boolean canRetry() {
        return this.retryCount < MAX_MANUAL_RETRIES;
    }

    public void markRetryGenerating() {
        this.retryCount++;
        this.status = ImprovementPracticeStatus.GENERATING;
        this.score = null;
        this.summary = null;
        this.detail = null;
        this.quote = null;
        this.failReason = null;
    }

    public void complete(ImprovementPracticeEvaluationResult result) {
        this.status = ImprovementPracticeStatus.COMPLETED;
        this.score = result.score();
        this.summary = truncate(result.summary(), MAX_SUMMARY_LENGTH);
        this.detail = result.detail();
        this.quote = result.quote();
        this.failReason = null;
    }

    public void fail(String reason) {
        this.status = ImprovementPracticeStatus.FAILED;
        this.failReason = truncate(reason, MAX_FAIL_REASON_LENGTH);
    }

    private static String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }
}
