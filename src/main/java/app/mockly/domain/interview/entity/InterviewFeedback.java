package app.mockly.domain.interview.entity;

import app.mockly.global.common.BaseEntity;
import jakarta.persistence.*;
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

    @Column(nullable = false, columnDefinition = "TEXT")
    private String strengths;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String improvements;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String detailedAnalysis;

    public static InterviewFeedback create(InterviewSession session, int overallScore,
                                           String strengths, String improvements, String detailedAnalysis) {
        return InterviewFeedback.builder()
                .session(session)
                .overallScore(overallScore)
                .strengths(strengths)
                .improvements(improvements)
                .detailedAnalysis(detailedAnalysis)
                .build();
    }
}
