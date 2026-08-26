package app.mockly.domain.interview.entity;

import app.mockly.domain.auth.entity.User;
import app.mockly.domain.product.entity.PlanTier;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class InterviewSessionTest {
    @Test
    void startFeedbackGenerationRecordsInterviewEndedAt() {
        InterviewSession session = InterviewSession.create(mock(User.class), "Backend",
                ExperienceLevel.JUNIOR, InterviewType.TECHNICAL, 3, "intro");

        session.startFeedbackGeneration(PlanTier.FREE);

        assertThat(session.getEndedAt()).isNotNull();
    }

    @Test
    void startFeedbackGenerationSnapshotsTheGenerationTier() {
        InterviewSession session = InterviewSession.create(mock(User.class), "Backend",
                ExperienceLevel.JUNIOR, InterviewType.TECHNICAL, 3, "intro");

        session.startFeedbackGeneration(PlanTier.PRO);

        assertThat(session.getFeedbackGenerationTier()).isEqualTo(PlanTier.PRO);
    }

    @Test
    void completingFeedbackDoesNotChangeInterviewEndedAt() {
        InterviewSession session = InterviewSession.create(mock(User.class), "Backend",
                ExperienceLevel.JUNIOR, InterviewType.TECHNICAL, 3, "intro");
        session.startFeedbackGeneration(PlanTier.FREE);
        var interviewEndedAt = session.getEndedAt();

        session.complete();

        assertThat(session.getCompletedAt()).isNotNull();
        assertThat(session.getEndedAt()).isEqualTo(interviewEndedAt);
    }

    @Test
    void abandonRecordsEndedAt() {
        InterviewSession session = InterviewSession.create(mock(User.class), "Backend",
                ExperienceLevel.JUNIOR, InterviewType.TECHNICAL, 3, "intro");

        session.abandon();

        assertThat(session.getEndedAt()).isNotNull();
    }
}
