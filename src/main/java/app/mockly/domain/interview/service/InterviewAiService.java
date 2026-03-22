package app.mockly.domain.interview.service;

import app.mockly.domain.interview.entity.ExperienceLevel;
import app.mockly.domain.interview.entity.InterviewType;
import app.mockly.global.common.ApiStatusCode;
import app.mockly.global.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class InterviewAiService {

    private final ChatClient chatClient;

    public InterviewAiService(ChatClient.Builder chatClientBuilder) {
        this.chatClient = chatClientBuilder.build();
    }

    public String generateFirstQuestion(String position, ExperienceLevel experienceLevel, InterviewType interviewType) {
        String levelContext = switch (experienceLevel) {
            case JUNIOR -> "깊이보다 사고 과정과 학습 의지를 중심으로 평가하세요. 정답을 모르더라도 어떻게 접근하는지가 중요합니다.";
            case MID -> "실무 경험을 바탕으로 문제를 어떻게 해결했는지를 중심으로 평가하세요.";
            case SENIOR -> "기술적 판단의 근거, 트레이드오프 인식, 조직/팀 관점까지 함께 평가하세요.";
        };

        String typeGuide = switch (interviewType) {
            case TECHNICAL -> "기술 개념, 실무 경험, 또는 설계/문제 해결 관점의 질문을 하세요.";
            case BEHAVIORAL -> "구체적인 상황과 행동 기반의 경험을 묻는 질문을 하세요.";
            case MIXED -> "기술과 인성 중 어느 쪽으로 시작해도 좋습니다. 자연스러운 워밍업 질문을 선택하세요.";
        };

        String systemPrompt = """
                당신은 면접관입니다.
                질문 하나만 출력하세요.
                두 문장을 넘지 마세요.
                "~도 말씀해주세요", "~이고 ~도"처럼 여러 질문을 한 번에 묻지 마세요.
                질문 외에 다른 설명, 인사말, 부연은 포함하지 마세요.
                """;

        String userPrompt = """
                다음 기준에 따라 첫 번째 면접 질문을 생성하세요:
                - 포지션: %s
                - 지원자 경력: %s
                - 면접 유형: %s
                - 경력 평가 기준: %s
                - 질문 방향: %s
                """.formatted(position, experienceLevel.getDescription(), interviewType.getDescription(), levelContext, typeGuide);

        try {
            return chatClient.prompt()
                    .system(systemPrompt)
                    .user(userPrompt)
                    .call()
                    .content();
        } catch (Exception e) {
            log.error("AI 첫 질문 생성 실패", e);
            throw new BusinessException(ApiStatusCode.AI_SERVICE_ERROR);
        }
    }
}
