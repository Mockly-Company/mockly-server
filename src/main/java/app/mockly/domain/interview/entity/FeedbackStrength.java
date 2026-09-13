package app.mockly.domain.interview.entity;

import app.mockly.domain.interview.dto.InterviewFeedbackResult;
import app.mockly.global.common.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "feedback_strength", uniqueConstraints =
        @UniqueConstraint(name = "uk_feedback_strength_order", columnNames = {"feedback_id", "sort_order"}))
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class FeedbackStrength extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "feedback_id", nullable = false)
    private InterviewFeedback feedback;
    @Column(nullable = false)
    private int questionNumber;
    @Column(nullable = false, length = 120)
    private String title;
    @Column(nullable = false, columnDefinition = "TEXT")
    private String detail;
    @Column(nullable = false, columnDefinition = "TEXT")
    private String quote;
    @Column(nullable = false)
    private int sortOrder;

    static FeedbackStrength create(InterviewFeedback feedback, InterviewFeedbackResult.Strength result) {
        return FeedbackStrength.builder()
                .feedback(feedback)
                .questionNumber(result.questionNumber())
                .title(result.title())
                .detail(result.detail())
                .quote(result.quote())
                .sortOrder(result.sortOrder())
                .build();
    }
}
