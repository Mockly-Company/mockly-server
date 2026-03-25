package app.mockly.domain.interview.service;

import app.mockly.domain.interview.dto.InterviewFeedbackResult;
import app.mockly.domain.interview.entity.ExperienceLevel;
import app.mockly.domain.interview.entity.InterviewMessage;
import app.mockly.domain.interview.entity.InterviewMessageRole;
import app.mockly.domain.interview.entity.InterviewType;
import app.mockly.domain.product.entity.PlanTier;
import app.mockly.global.common.ApiStatusCode;
import app.mockly.global.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

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
                당신은 경험 많은 면접관입니다.
                면접 시작 시 짧게 인사하고 첫 질문을 자연스럽게 이어가세요.
                인사와 질문을 합쳐 세 문장을 넘지 마세요.
                "~도 말씀해주세요", "~이고 ~도"처럼 여러 질문을 한 번에 묻지 마세요.
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

    public String generateNextQuestion(List<InterviewMessage> history, InterviewType interviewType,
                                        String position, ExperienceLevel experienceLevel) {
        String levelContext = switch (experienceLevel) {
            case JUNIOR -> "깊이보다 사고 과정과 학습 의지를 중심으로 평가하세요.";
            case MID -> "실무 경험을 바탕으로 문제를 어떻게 해결했는지를 중심으로 평가하세요.";
            case SENIOR -> "기술적 판단의 근거, 트레이드오프 인식, 조직/팀 관점까지 함께 평가하세요.";
        };

        String systemPrompt = """
                당신은 경험 많은 면접관입니다.
                이전 답변에 한 문장으로 짧고 중립적으로 반응한 뒤 다음 질문으로 이어가세요.
                반응은 매번 다른 표현을 사용하고, 칭찬("잘 하셨습니다", "좋습니다" 등)은 하지 마세요.
                질문 하나만 하세요. "~도 말씀해주세요"처럼 여러 질문을 한 번에 묻지 마세요.

                [답변 평가 기준]
                - 개념만 짧게 언급했다면: 경험이나 구체적인 예시를 꼬리질문으로 파고드세요.
                - 구체적인 경험/예시까지 설명했다면: 자연스럽게 다음 주제로 넘어가세요.
                - 모르겠다거나 답변이 매우 짧고 불확실하다면: 강요하지 말고 다음 주제로 넘어가세요.

                꼬리질문을 우선하고, 새 주제는 현재 주제가 충분히 탐색됐을 때만 전환하세요.
                """;

        String userPrompt = """
                포지션: %s
                지원자 경력: %s
                면접 유형: %s
                경력 평가 기준: %s

                [지금까지의 대화]
                %s

                다음 면접 질문을 생성하세요.
                """.formatted(position, experienceLevel.getDescription(), interviewType.getDescription(),
                levelContext, formatHistory(history));

        try {
            return chatClient.prompt()
                    .system(systemPrompt)
                    .user(userPrompt)
                    .call()
                    .content();
        } catch (Exception e) {
            log.error("AI 다음 질문 생성 실패", e);
            throw new BusinessException(ApiStatusCode.AI_SERVICE_ERROR);
        }
    }

    public InterviewFeedbackResult generateFeedback(List<InterviewMessage> history, InterviewType interviewType, PlanTier plan) {
        String systemPrompt = """
                당신은 면접 평가 시스템입니다. 면접이 끝난 후 지원자의 답변을 전문가 관점에서 평가합니다.
                반드시 지정된 JSON 형식으로만 응답하세요.

                [종합 점수]
                overallScore는 전체 면접을 종합적으로 평가한 독립 점수입니다 (1~100).
                전문가 점수의 단순 평균이 아니라, 면접 전반의 역량을 종합 판단하세요.

                [전문가 평가]
                아래 전문가별로 각 파라미터를 1~100으로 내부 평가한 뒤, 가중 평균으로 score를 산출하세요.
                evaluation에는 해당 전문가 관점의 구체적인 평가 내용을 2~3문장으로 작성하세요.

                %s

                %s
                """.formatted(buildExpertPrompt(plan), buildDetailedAnalysisGuide(plan));

        String userPrompt = """
                면접 유형: %s

                [면접 대화]
                %s

                위 면접 내용을 평가하여 피드백을 제공하세요.
                """.formatted(interviewType.getDescription(), formatHistory(history));

        try {
            return chatClient.prompt()
                    .system(systemPrompt)
                    .user(userPrompt)
                    .call()
                    .entity(InterviewFeedbackResult.class);
        } catch (Exception e) {
            log.error("AI 피드백 생성 실패", e);
            throw new BusinessException(ApiStatusCode.AI_SERVICE_ERROR);
        }
    }

    private String buildExpertPrompt(PlanTier plan) {
        StringBuilder sb = new StringBuilder();

        sb.append("""
                --- 기술 면접관 (expertRole: "기술 면접관") ---
                지원자의 기술적 역량과 전문 지식 수준을 평가합니다.
                - 기술적 정확성 (40%): 개념 정의, 동작 원리, 한계점의 사실적 정확도. 오개념·구버전 지식 포함 시 감점.
                - 이해의 깊이 (35%): "왜 그런가"를 설명하는 능력. 개념 간 연관성, trade-off 인식, 엣지 케이스 고려.
                - 실무 적용력 (25%): 실제 프로젝트 사례 인용, 프로덕션 제약조건 고려, 기술 선택 근거 제시.
                score = 정확성 × 0.4 + 깊이 × 0.35 + 실무적용력 × 0.25
                """);

        if (plan != PlanTier.FREE) {
            sb.append("""

                    --- 커뮤니케이션 전문가 (expertRole: "커뮤니케이션 전문가") ---
                    지원자의 의사소통 능력과 답변 전달 품질을 평가합니다.
                    - 논리적 구조 (40%): 두괄식/STAR 등 명확한 프레임 사용, 주장→근거→결론 흐름의 자연스러움.
                    - 표현의 명확성 (35%): 전문 용어의 적절한 사용, 모호하지 않은 표현, 핵심을 짚는 간결함.
                    - 설득력 (25%): 면접관에게 신뢰감과 역량을 전달하는 능력. 근거 있는 주장, 적절한 예시 배치.
                    score = 구조 × 0.4 + 명확성 × 0.35 + 설득력 × 0.25
                    """);
        }

        if (plan == PlanTier.PRO) {
            sb.append("""

                    --- 면접 코치 (expertRole: "면접 코치") ---
                    면접 수행 능력 자체를 메타 관점에서 평가합니다.
                    - 답변 구체성 (40%): 구체적 사례·수치·결과 제시 여부. STAR 프레임 활용도.
                    - 상황 대처력 (35%): 모르는 질문이나 꼬리질문에 대한 대응 품질. 솔직한 인정 + 사고 과정 공유.
                    - 성장 가능성 (25%): 자기 인식, 학습 의지, 실패에서 배운 점, 새로운 기술에 대한 호기심.
                    score = 구체성 × 0.4 + 대처력 × 0.35 + 성장가능성 × 0.25
                    """);
        }

        return sb.toString();
    }

    private String buildDetailedAnalysisGuide(PlanTier plan) {
        if (plan == PlanTier.PRO) {
            return "detailedAnalysis에 질문별 상세 분석을 작성하세요. 각 질문에 대해 답변의 강점, 약점, 개선 방향을 구체적으로 분석합니다.";
        }
        return "detailedAnalysis는 반드시 null로 설정하세요.";
    }

    private String formatHistory(List<InterviewMessage> history) {
        return history.stream()
                .map(m -> {
                    String role = m.getRole() == InterviewMessageRole.INTERVIEWER ? "면접관" : "지원자";
                    return role + ": " + m.getContent();
                })
                .collect(Collectors.joining("\n"));
    }
}
