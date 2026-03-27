package app.mockly.domain.interview.entity;

import app.mockly.global.common.BaseEntity;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import lombok.*;

@Entity
@Table(name = "interview_feedback")
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class InterviewFeedback extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "session_id", nullable = false, unique = true)
    private InterviewSession session;

    @Column(nullable = false)
    private int overallScore;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private String expertFeedbacks;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String strengths;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String improvements;

    @Column(columnDefinition = "TEXT")
    private String detailedAnalysis;

    public static InterviewFeedback create(InterviewSession session, int overallScore,
                                           String expertFeedbacks, String strengths,
                                           String improvements, String detailedAnalysis) {
        return InterviewFeedback.builder()
                .session(session)
                .overallScore(overallScore)
                .expertFeedbacks(expertFeedbacks)
                .strengths(strengths)
                .improvements(improvements)
                .detailedAnalysis(detailedAnalysis)
                .build();
    }
}
