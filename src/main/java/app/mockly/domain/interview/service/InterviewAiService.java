package app.mockly.domain.interview.service;

import app.mockly.domain.interview.dto.InterviewFeedbackResult;
import app.mockly.domain.interview.entity.ExperienceLevel;
import app.mockly.domain.interview.entity.InterviewMessage;
import app.mockly.domain.interview.entity.InterviewMessageRole;
import app.mockly.domain.interview.entity.InterviewSession;
import app.mockly.domain.interview.entity.InterviewType;
import app.mockly.domain.product.entity.PlanTier;
import app.mockly.global.common.ApiStatusCode;
import app.mockly.global.config.InterviewAiProperties;
import app.mockly.global.exception.BusinessException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.List;

@Slf4j
@Service
public class InterviewAiService {

    private final ChatClient chatClient;
    private final ObjectMapper objectMapper;
    private final InterviewAiProperties interviewAiProperties;

    public InterviewAiService(ChatClient.Builder chatClientBuilder, ObjectMapper objectMapper,
                              InterviewAiProperties interviewAiProperties) {
        this.chatClient = chatClientBuilder.build();
        this.objectMapper = objectMapper;
        this.interviewAiProperties = interviewAiProperties;
    }

    public Flux<String> generateFirstQuestion(InterviewSession session) {
        String position = session.getPosition();
        ExperienceLevel experienceLevel = session.getExperienceLevel();
        InterviewType interviewType = session.getInterviewType();
        String keyword = session.getFirstQuestionKeyword();
        String systemPrompt = """
                당신은 기업 면접관입니다.
                키워드 [%s]를 바탕으로 면접 첫 질문을 생성하세요.
                출력: 면접관이 실제로 말로 읽을 수 있는 한 문장 질문

                ---
                [질문 유형]
                다음 6가지 유형 중 키워드와 경력 수준에 가장 적합한 하나를 선택하세요.
                예시 문장을 그대로 사용하지 마세요.

                1. 개념 확인형: 특정 기술이나 개념의 정의, 특징, 종류를 묻는다
                   예시 스타일: "~이란 무엇인가요?"
                2. 경험 회상형: 실제 경험과 그 과정, 결과를 묻는다
                   예시 스타일: "~를 경험한 적이 있나요?"
                3. 문제 해결형: 특정 문제를 어떻게 해결했는지 묻는다
                   예시 스타일: "~ 상황을 어떻게 해결하셨나요?"
                4. 설계형: 시스템 또는 구조 설계를 묻는다
                   예시 스타일: "~를 어떻게 구성하셨나요?"
                5. 트레이드오프형: 선택과 판단 기준을 묻는다
                   예시 스타일: "A와 B 중 무엇을 선택하시겠습니까?"
                6. 디버깅형: 문제 원인 분석과 탐색 과정을 묻는다
                   예시 스타일: "문제를 어떤 순서로 파악하셨나요?"

                ---
                [제약]
                - 질문 소재는 키워드 [%s] 도메인에서 찾으세요. 자기소개에서 읽은 세부 내용을 질문 내용으로 삼지 마세요.
                - 하나의 행동만 요구하는 질문을 생성하세요
                  BAD: "Spring Boot와 JPA를 사용해 도메인을 구현한 뒤 조회 성능을 개선하기 위해 어떤 전략을 구성했나요?"
                  GOOD: "JPA에서 N+1 문제를 어떻게 해결하셨나요?"
                - 구체적이고 범위가 명확한 질문을 생성하세요
                """.formatted(keyword, keyword);

        String userPrompt = """
                다음 정보를 바탕으로 첫 번째 면접 질문을 생성하세요.

                - 포지션: %s
                - 경력 수준: %s
                - 면접 유형: %s
                - [참고용] 지원자 자기소개:
                \"\"\"
                %s
                \"\"\"
                - 탐색 키워드: %s
                """.formatted(position, experienceLevel.getDescription(), interviewType.getDescription(),
                        session.getSelfIntroduction(), keyword);

        try {
            return chatClient.prompt()
                    .system(systemPrompt)
                    .user(userPrompt)
                    .stream()
                    .content();
        } catch (Exception e) {
            log.error("AI 첫 질문 생성 실패", e);
            throw new BusinessException(ApiStatusCode.AI_SERVICE_ERROR);
        }
    }

    public Flux<String> generateNextQuestion(InterviewSession session, List<InterviewMessage> history) {
        String position = session.getPosition();
        ExperienceLevel experienceLevel = session.getExperienceLevel();
        InterviewType interviewType = session.getInterviewType();
        String selfIntroduction = session.getSelfIntroduction();
        String levelContext = switch (experienceLevel) {
            case JUNIOR -> "깊이보다 사고 과정과 학습 의지를 중심으로 평가하세요.";
            case MID -> "실무 경험을 바탕으로 문제를 어떻게 해결했는지를 중심으로 평가하세요.";
            case SENIOR -> "기술적 판단의 근거, 트레이드오프 인식, 조직/팀 관점까지 함께 평가하세요.";
        };

        String systemPrompt = """
                당신은 경험 많은 면접관입니다.
                직전 지원자 답변에 짧고 중립적으로 반응한 뒤 다음 질문을 이어가세요.
                칭찬 표현("잘 하셨습니다", "좋습니다") 금지.
                질문은 하나만. 역할 접두사("면접관:") 금지.
                
                [Step 0: 답변 퀄리티 평가]
                마지막 답변을 1-3점으로 평가하세요:
                - 1점: 무응답, 완전히 모름, 매우 피상적
                - 2점: 기본 개념 이해, 설명 가능하나 깊이·구체성 부족
                - 3점: 명확한 이해 + 구체적 설명 + 실무 연결 또는 트레이드오프 언급
            
                점수는 내부적으로만 사용. 출력하지 않음.
            
                [점수별 전략 조정]
                - 1점: 유형 D 전략 → 더 쉬운 기초 질문 또는 새 주제 전환
                - 2점: 유형 A/B 전략 → 동일 주제 구체화
                - 3점: 유형 C 전략 또는 새 주제 전환 → 심화

                [Step 1: 직전 답변 유형 파악]
                대화 기록에서 지원자의 마지막 답변을 분석하세요.

                - 유형 A (열거/나열형): 항목들을 목록으로 제시 ("A, B, C가 있습니다")
                - 유형 B (설명/정의형): 개념·원리·트레이드오프를 설명
                - 유형 C (경험/사례형): 구체적인 경험, 수치, 결과, 의사결정을 제시
                - 유형 D (피상/회피형): 두루뭉술하거나 모른다고 언급

                [Step 2: 유형별 꼬리질문 전략]

                유형 A (열거형)
                → 나열된 항목 중 인접한 2개를 골라 차이를 질문하세요
                → 패턴: "말씀하신 A와 B는 어떤 차이가 있나요?"

                유형 B (설명형)
                → 답변에서 언급된 원인·메커니즘을 더 깊이 질문하세요
                → 패턴: "왜 그런 현상이 발생하나요?" 또는 "어떤 상황에서 그 문제가 생길 수 있나요?"

                유형 C (경험형)
                → 아래 중 하나를 선택하세요
                → 구체화: "그때 어떤 기준으로 그 결정을 내리셨나요?"
                → 반론: "다른 방법도 있었을 텐데, 왜 그 방법을 선택하셨어요?"
                → 결과: "그 결과가 어떻게 됐나요?"
                → 가정: "만약 그 조건이 달랐다면 어떻게 하셨을 것 같아요?"

                유형 D (피상/회피형)
                → 강요하지 말고 새 주제로 자연스럽게 전환하세요

                [Step 3: 주제 전환 판단]
                현재 주제가 충분히 탐색됐다면 (유형 C 답변이 나왔을 때) 새 주제로 전환할 수 있습니다.
                새 주제 선택 시에만 자기소개나 포지션 맥락을 참고하세요.

                [핵심 규칙]
                ❗ 꼬리질문은 반드시 직전 답변 내용에서 출발합니다
                ❗ 꼬리질문 중 자기소개 내용을 소재로 사용하는 것은 금지입니다
                ❗ "그 상황에서 ~", "재고 문제를 해결할 때 ~"처럼 자기소개 맥락을 꺼내지 마세요
                ❗ 새 주제로 전환하는 경우에만 자기소개 키워드를 참고할 수 있습니다
                """;

        String userPrompt = """
                포지션: %s
                지원자 경력: %s
                면접 유형: %s
                경력 평가 기준: %s

                [지원자 자기소개]
                %s

                [지금까지의 대화]
                %s

                다음 면접 질문을 생성하세요.
                """.formatted(position, experienceLevel.getDescription(), interviewType.getDescription(),
                levelContext, selfIntroduction, formatHistory(history));

        try {
            return chatClient.prompt()
                    .system(systemPrompt)
                    .user(userPrompt)
                    .stream()
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
        List<HistoryEntry> entries = history.stream()
                .map(m -> new HistoryEntry(
                        m.getRole() == InterviewMessageRole.INTERVIEWER ? "interviewer" : "candidate",
                        m.getContent()))
                .toList();
        try {
            return objectMapper.writeValueAsString(entries);
        } catch (Exception e) {
            log.error("대화 기록 직렬화 실패", e);
            return "[]";
        }
    }

    private record HistoryEntry(String role, String content) {}

    private record KeywordCandidates(List<String> keywords) {}

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
                코드 블록 없이 순수 JSON으로만 응답: {"keywords": ["...", ...]}

                포지션: %s
                자기소개: %s
               \s""".formatted(position, selfIntroduction);
        try {
            String raw = chatClient.prompt()
                    .options(OpenAiChatOptions.builder()
                            .model(interviewAiProperties.getKeywordExtractionModel())
                            .temperature(interviewAiProperties.getKeywordExtractionTemperature())
                            .build())
                    .user(prompt)
                    .call()
                    .content();
            log.info("keyword extraction raw response: {}", raw);
            KeywordCandidates result = objectMapper.readValue(raw, KeywordCandidates.class);
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
