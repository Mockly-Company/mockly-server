package app.mockly.domain.interview.entity;

import app.mockly.domain.product.entity.PlanTier;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "interview_quota")
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class InterviewQuota {

    @Id
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private PlanTier planTier;

    @Column(nullable = false)
    private int dailyLimit;

    @Column(nullable = false)
    private int maxQuestionsPerSession;
}
