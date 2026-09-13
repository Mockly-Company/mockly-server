package app.mockly.domain.interview.service;

import app.mockly.domain.interview.dto.InterviewFeedbackResult;
import app.mockly.domain.interview.dto.InterviewPrompt;
import app.mockly.domain.interview.entity.InterviewMessage;
import app.mockly.domain.interview.entity.InterviewSession;
import app.mockly.domain.interview.entity.InterviewType;
import app.mockly.domain.product.entity.PlanTier;
import app.mockly.global.common.ApiStatusCode;
import app.mockly.global.config.InterviewAiProperties;
import app.mockly.global.exception.BusinessException;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.AdvisorParams;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.List;

@Slf4j
@Service
public class InterviewAiService {

    private final ChatClient chatClient;
    private final InterviewPromptFactory interviewPromptFactory;
    private final InterviewAiProperties interviewAiProperties;
    private final MeterRegistry meterRegistry;

    public InterviewAiService(ChatClient.Builder chatClientBuilder, InterviewPromptFactory interviewPromptFactory,
                              InterviewAiProperties interviewAiProperties, MeterRegistry meterRegistry) {
        this.chatClient = chatClientBuilder.build();
        this.interviewPromptFactory = interviewPromptFactory;
        this.interviewAiProperties = interviewAiProperties;
        this.meterRegistry = meterRegistry;
    }

    public Flux<String> generateFirstQuestion(InterviewSession session) {
        InterviewPrompt prompt = interviewPromptFactory.firstQuestion(session);

        try {
            return chatClient.prompt()
                    .system(prompt.system())
                    .user(prompt.user())
                    .stream()
                    .content();
        } catch (Exception e) {
            log.error("AI 첫 질문 생성 실패", e);
            throw new BusinessException(ApiStatusCode.AI_SERVICE_ERROR);
        }
    }

    public Flux<String> generateNextQuestion(InterviewSession session, List<InterviewMessage> history) {
        InterviewPrompt prompt = interviewPromptFactory.followUpQuestion(session, history);

        try {
            return chatClient.prompt()
                    .system(prompt.system())
                    .user(prompt.user())
                    .stream()
                    .content();
        } catch (Exception e) {
            log.error("AI 다음 질문 생성 실패", e);
            throw new BusinessException(ApiStatusCode.AI_SERVICE_ERROR);
        }
    }

    public InterviewFeedbackResult generateFeedback(List<InterviewMessage> history, InterviewType interviewType, PlanTier plan) {
        InterviewPrompt prompt = interviewPromptFactory.feedback(history, interviewType, plan);

        Timer.Sample sample = Timer.start(meterRegistry);
        try {
            InterviewFeedbackResult result = chatClient.prompt()
                    .advisors(AdvisorParams.ENABLE_NATIVE_STRUCTURED_OUTPUT)
                    .system(prompt.system())
                    .user(prompt.user())
                    .call()
                    .entity(InterviewFeedbackResult.class);
            sample.stop(Timer.builder("ai.feedback.duration")
                    .tag("status", "success")
                    .register(meterRegistry));
            return result;
        } catch (Exception e) {
            sample.stop(Timer.builder("ai.feedback.duration")
                    .tag("status", "error")
                    .register(meterRegistry));
            log.error("AI 피드백 생성 실패", e);
            throw new BusinessException(ApiStatusCode.AI_SERVICE_ERROR);
        }
    }

    private record KeywordCandidates(
            @JsonProperty(required = true, value = "keywords") List<String> keywords) {}

    public List<String> extractKeywordCandidates(String selfIntroduction, String position) {
        String prompt = """
                1. 자기소개에서 기술/도메인 키워드 후보 7개를 추출한다.
                   - 서로 다른 성격의 키워드를 선택할 것
                     (예: 기술, 성능, 문제 해결 경험 등)
                   - 기술명은 자기소개에 언급된 그대로 추출한다: "Spring Boot", "JPA", "Redis"
                \s
                BAD (추출 금지):
                - "서비스 운영 중 발생한 문제 해결 경험" → 어떤 문제인지 불명확
                - "이커머스 스타트업에서의 실무 경험" → 맥락 설명일 뿐
                - "주문 도메인" → 기술도 경험도 아님

                2. 각 키워드에 대해 아래 기준으로 평가한다:
                   - 질문으로 만들었을 때 구체적인 답변이 가능한가?
                   - 하나의 평가 포인트로 명확히 검증 가능한가?
                   - 지원자의 경험 또는 이해도를 드러낼 수 있는가?
               \s
                3. 위 기준을 가장 잘 만족하는 키워드 3~5개를 선택한다.
               \s
                포지션: %s
                자기소개: %s
               \s""".formatted(position, selfIntroduction);

        try {
            KeywordCandidates result = chatClient.prompt()
                    .advisors(AdvisorParams.ENABLE_NATIVE_STRUCTURED_OUTPUT)
                    .options(OpenAiChatOptions.builder()
                            .model(interviewAiProperties.getKeywordExtractionModel())
                            .temperature(interviewAiProperties.getKeywordExtractionTemperature())
                            .build())
                    .user(prompt)
                    .call()
                    .entity(KeywordCandidates.class);
            if (result.keywords() == null || result.keywords().isEmpty()) {
                log.error("키워드 후보 추출 결과가 비어있음");
                throw new BusinessException(ApiStatusCode.AI_SERVICE_ERROR);
            }
            return result.keywords();
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("키워드 후보 추출 실패", e);
            throw new BusinessException(ApiStatusCode.AI_SERVICE_ERROR);
        }
    }

}
