package app.mockly.domain.interview.entity;

import app.mockly.domain.auth.entity.User;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class InterviewSessionTest {
    @Test
    void startFeedbackGenerationRecordsInterviewEndedAt() {
        InterviewSession session = InterviewSession.create(mock(User.class), "Backend",
                ExperienceLevel.JUNIOR, InterviewType.TECHNICAL, 3, "intro");

        session.startFeedbackGeneration();

        assertThat(session.getEndedAt()).isNotNull();
    }

    @Test
    void completingFeedbackDoesNotChangeInterviewEndedAt() {
        InterviewSession session = InterviewSession.create(mock(User.class), "Backend",
                ExperienceLevel.JUNIOR, InterviewType.TECHNICAL, 3, "intro");
        session.startFeedbackGeneration();
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
