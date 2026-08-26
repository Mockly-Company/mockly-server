package app.mockly.domain.interview.service;

import app.mockly.domain.interview.dto.InterviewFeedbackResult;
import app.mockly.domain.interview.entity.InterviewMessage;
import app.mockly.domain.interview.entity.InterviewType;
import app.mockly.domain.product.entity.PlanTier;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import app.mockly.global.config.InterviewAiProperties;

import java.util.List;

@Slf4j
@Service
@Profile("loadtest")
@Primary
public class MockInterviewAiService extends InterviewAiService {

    private static final long MOCK_DELAY_MS = 10_000;

    public MockInterviewAiService(ChatClient.Builder chatClientBuilder, ObjectMapper objectMapper,
                                  InterviewAiProperties interviewAiProperties, MeterRegistry meterRegistry) {
        super(chatClientBuilder, objectMapper, interviewAiProperties, meterRegistry);
    }

    @Override
    public InterviewFeedbackResult generateFeedback(List<InterviewMessage> history,
                                                    InterviewType interviewType, PlanTier plan) {
        log.info("[MOCK] generateFeedback 시작 — {}ms sleep", MOCK_DELAY_MS);
        try {
            Thread.sleep(MOCK_DELAY_MS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        log.info("[MOCK] generateFeedback 완료");

        return new InterviewFeedbackResult(
                75,
                new InterviewFeedbackResult.CoachBrief(
                        "전반적으로 기술적 이해도가 좋습니다.",
                        plan == PlanTier.FREE ? null : "기본 개념을 잘 이해합니다.",
                        plan == PlanTier.FREE ? null : "구체적인 사례를 더 활용하세요."),
                new InterviewFeedbackResult.Scores(75, 75, 75, 75),
                plan == PlanTier.FREE ? List.of() : List.of(
                        new InterviewFeedbackResult.Strength(1, "강점 1", "상세 1", "인용 1", 1),
                        new InterviewFeedbackResult.Strength(1, "강점 2", "상세 2", "인용 2", 2),
                        new InterviewFeedbackResult.Strength(1, "강점 3", "상세 3", "인용 3", 3)),
                plan == PlanTier.FREE ? List.of(
                        new InterviewFeedbackResult.Improvement(1, 1, "개선점", "한 줄 요약", null, null)) : List.of(
                        new InterviewFeedbackResult.Improvement(1, 1, "개선점 1", "요약 1", "상세 1", "인용 1"),
                        new InterviewFeedbackResult.Improvement(2, 1, "개선점 2", "요약 2", "상세 2", "인용 2"),
                        new InterviewFeedbackResult.Improvement(3, 1, "개선점 3", "요약 3", "상세 3", "인용 3")),
                plan == PlanTier.PRO ? "결론을 먼저 말하기" : null
        );
    }
}
