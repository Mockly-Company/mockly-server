package app.mockly.domain.interview.entity;

import app.mockly.domain.interview.dto.InterviewFeedbackResult;
import app.mockly.domain.product.entity.PlanTier;
import app.mockly.global.common.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.util.ArrayList;
import java.util.List;

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
    @Column(nullable = false, length = 200)
    private String coachBriefSummary;
    @Column(length = 100)
    private String coachBriefKeyStrength;
    @Column(length = 100)
    private String coachBriefKeyImprovement;
    @Column(nullable = false)
    private int scoreStructure;
    @Column(nullable = false)
    private int scoreSpecificity;
    @Column(nullable = false)
    private int scoreJobRelevance;
    @Column(nullable = false)
    private int scoreClarity;
    @Column(length = 80)
    private String nextPracticePoint;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private PlanTier generatedTier;

    @Builder.Default
    @OneToMany(mappedBy = "feedback", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sortOrder ASC")
    private List<FeedbackStrength> strengths = new ArrayList<>();

    @Builder.Default
    @OneToMany(mappedBy = "feedback", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("rank ASC")
    private List<FeedbackImprovement> improvements = new ArrayList<>();

    public static InterviewFeedback create(InterviewSession session, InterviewFeedbackResult result, PlanTier generatedTier) {
        InterviewFeedback feedback = InterviewFeedback.builder()
                .session(session)
                .overallScore(result.overallScore())
                .coachBriefSummary(result.coachBrief().summary())
                .coachBriefKeyStrength(result.coachBrief().keyStrength())
                .coachBriefKeyImprovement(result.coachBrief().keyImprovement())
                .scoreStructure(result.scores().structure())
                .scoreSpecificity(result.scores().specificity())
                .scoreJobRelevance(result.scores().jobRelevance())
                .scoreClarity(result.scores().clarity())
                .nextPracticePoint(result.nextPracticePoint())
                .generatedTier(generatedTier)
                .build();
        result.strengths().stream()
                .map(strength -> FeedbackStrength.create(feedback, strength))
                .forEach(feedback.strengths::add);
        result.improvements().stream()
                .map(improvement -> FeedbackImprovement.create(feedback, improvement))
                .forEach(feedback.improvements::add);
        return feedback;
    }
}
