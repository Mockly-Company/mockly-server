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
                List.of(new InterviewFeedbackResult.ExpertFeedback(
                        "기술 면접관", 75, "기본 개념을 잘 이해하고 있으며, 실무 경험을 바탕으로 답변했습니다.")),
                "전반적으로 기술적 이해도가 좋습니다.",
                "구체적인 사례를 더 활용하면 좋겠습니다.",
                null
        );
    }
}
