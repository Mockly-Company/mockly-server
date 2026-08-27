package app.mockly.domain.interview.service;

import app.mockly.domain.auth.entity.OAuth2Provider;
import app.mockly.domain.auth.entity.User;
import app.mockly.domain.auth.repository.UserRepository;
import app.mockly.domain.interview.dto.FeedbackContext;
import app.mockly.domain.interview.entity.ExperienceLevel;
import app.mockly.domain.interview.entity.FeedbackStatus;
import app.mockly.domain.interview.entity.InterviewFeedback;
import app.mockly.domain.interview.entity.InterviewSession;
import app.mockly.domain.interview.entity.InterviewType;
import app.mockly.domain.interview.repository.InterviewFeedbackRepository;
import app.mockly.domain.interview.repository.InterviewSessionRepository;
import app.mockly.domain.product.entity.PlanTier;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.UUID;

import static app.mockly.domain.interview.FeedbackTestFixtures.feedbackResult;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class FeedbackGenerationTransactionIntegrationTest {

    @Autowired
    private FeedbackGenerationStateService stateService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private InterviewSessionRepository interviewSessionRepository;

    @Autowired
    private InterviewFeedbackRepository interviewFeedbackRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Test
    void feedbackSaveFailureRollsBackCompletionAndTaskIdRemoval() {
        TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
        TestIds ids = transactionTemplate.execute(status -> createSessionWithExistingFeedback());
        assertThat(ids).isNotNull();

        try {
            FeedbackContext context = stateService.start(ids.sessionId()).orElseThrow();

            assertThatThrownBy(() -> stateService.complete(
                    ids.sessionId(), context.taskId(), feedbackResult(PlanTier.FREE)))
                    .isInstanceOf(FeedbackGenerationStateService.FeedbackCompletionException.class);

            transactionTemplate.executeWithoutResult(status -> {
                InterviewSession session = interviewSessionRepository.findById(ids.sessionId()).orElseThrow();
                assertThat(session.getFeedbackStatus()).isEqualTo(FeedbackStatus.GENERATING);
                assertThat(session.getFeedbackGenerationTaskId()).isEqualTo(context.taskId());
            });
        } finally {
            transactionTemplate.executeWithoutResult(status -> {
                interviewFeedbackRepository.findBySessionId(ids.sessionId())
                        .ifPresent(interviewFeedbackRepository::delete);
                interviewSessionRepository.deleteById(ids.sessionId());
                userRepository.deleteById(ids.userId());
            });
        }
    }

    private TestIds createSessionWithExistingFeedback() {
        User user = userRepository.save(User.builder()
                .provider(OAuth2Provider.GOOGLE)
                .providerId("feedback-rollback-" + UUID.randomUUID())
                .email(UUID.randomUUID() + "@example.com")
                .name("피드백 롤백 테스트")
                .build());
        InterviewSession session = InterviewSession.create(
                user,
                "백엔드 개발자",
                ExperienceLevel.JUNIOR,
                InterviewType.TECHNICAL,
                3,
                "자기소개");
        session.startFeedbackGeneration(PlanTier.FREE);
        session = interviewSessionRepository.save(session);
        interviewFeedbackRepository.save(InterviewFeedback.create(
                session, feedbackResult(PlanTier.FREE), PlanTier.FREE));
        return new TestIds(user.getId(), session.getId());
    }

    private record TestIds(UUID userId, UUID sessionId) {
    }
}
