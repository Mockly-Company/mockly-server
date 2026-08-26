package app.mockly.domain.interview.entity;

import app.mockly.domain.interview.dto.InterviewFeedbackResult;
import app.mockly.global.common.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "feedback_improvement", uniqueConstraints =
        @UniqueConstraint(name = "uk_feedback_improvement_rank", columnNames = {"feedback_id", "rank"}))
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class FeedbackImprovement extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "feedback_id", nullable = false)
    private InterviewFeedback feedback;
    @Column(nullable = false)
    private int rank;
    @Column(nullable = false)
    private int questionNumber;
    @Column(nullable = false, length = 120)
    private String title;
    @Column(nullable = false, length = 200)
    private String summary;
    @Column(columnDefinition = "TEXT")
    private String detail;
    @Column(columnDefinition = "TEXT")
    private String quote;

    static FeedbackImprovement create(InterviewFeedback feedback, InterviewFeedbackResult.Improvement result) {
        return FeedbackImprovement.builder()
                .feedback(feedback)
                .rank(result.rank())
                .questionNumber(result.questionNumber())
                .title(result.title())
                .summary(result.summary())
                .detail(result.detail())
                .quote(result.quote())
                .build();
    }
}
