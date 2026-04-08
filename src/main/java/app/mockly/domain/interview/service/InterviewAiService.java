package app.mockly.domain.interview.service;

import app.mockly.domain.interview.dto.InterviewFeedbackResult;
import app.mockly.domain.interview.entity.ExperienceLevel;
import app.mockly.domain.interview.entity.InterviewMessage;
import app.mockly.domain.interview.entity.InterviewMessageRole;
import app.mockly.domain.interview.entity.InterviewType;
import app.mockly.domain.product.entity.PlanTier;
import app.mockly.global.common.ApiStatusCode;
import app.mockly.global.exception.BusinessException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
public class InterviewAiService {

    private final ChatClient chatClient;
    private final ObjectMapper objectMapper;

    public InterviewAiService(ChatClient.Builder chatClientBuilder, ObjectMapper objectMapper) {
        this.chatClient = chatClientBuilder.build();
        this.objectMapper = objectMapper;
    }

    public String generateFirstQuestion(String position, ExperienceLevel experienceLevel, InterviewType interviewType,
                                        String selfIntroduction) {
        String systemPrompt = """
                당신은 실제 기업에서 면접을 진행하는 숙련된 시니어 면접관입니다.

                당신의 역할은 지원자의 "지식 수준"이 아니라,
                "이해도, 사고 과정, 그리고 실무 적용 가능성"을 드러내는
                첫 질문을 설계하는 것입니다.

                ---
                [입력 정보]
                - 지원 직무 (position)
                - 경력 수준 (experienceLevel: JUNIOR / MID / SENIOR)
                - 자기소개 (selfIntroduction 500자)

                ---
                [핵심 목표]
                좋은 첫 질문은 다음 조건을 만족해야 합니다:

                1. 지원자가 실제로 답변할 수 있는 질문이어야 한다 (허황된 가정 금지)
                2. 단순 암기 확인이 아니라 "이해 여부"가 드러나야 한다
                3. 너무 포괄적이지 않고, 답변 범위가 적절히 제한되어 있어야 한다
                4. 이후 꼬리 질문이 자연스럽게 이어질 수 있어야 한다

                ---
                [키워드 선택 전략 - 매우 중요]

                1. 자기소개에서 기술/도메인 키워드 후보 3개를 추출한다.
                   - 서로 다른 성격의 키워드를 선택할 것
                     (예: 기술, 성능, 문제 해결 경험 등)

                2. 각 키워드에 대해 아래 기준으로 평가한다:
                   - 질문으로 만들었을 때 구체적인 답변이 가능한가?
                   - 하나의 평가 포인트로 명확히 검증 가능한가?
                   - 지원자의 경험 또는 이해도를 드러낼 수 있는가?

                3. 위 기준을 가장 잘 만족하는 키워드 1개를 선택한다.

                4. 선택한 키워드에 대해 A 또는 B 방식 중 더 적절한 방식으로 질문을 생성한다.

                ---
                [질문 생성 전략]
                선택한 키워드 바탕으로 아래 두 가지 방식 중 하나로 질문을 생성한다:

                [A. 개념 확인 질문] (기본)
                - 해당 기술의 핵심 개념을 정확히 이해했는지 확인
                - 정의 + 구조 + 동작 원리를 설명할 수 있는 질문

                예:
                - "트랜잭션이란 무엇인가요?"
                - "영속성 컨텍스트가 어떤 역할을 하는지 설명해주실 수 있나요?"
                - "Redis를 캐시로 사용할 때 어떤 자료구조를 사용하셨나요?"

                [B. 제한된 상황 질문] (경력자 또는 경험이 드러난 경우)
                - 실제 경험을 기반으로 판단/설계를 요구
                - 단, 상황은 단순하고 명확해야 한다 (복잡한 시나리오 금지)

                예:
                - "트랜잭션을 사용하면서 롤백이 예상과 다르게 동작했던 경험이 있으신가요?"
                - "JPA 사용 시 성능 문제가 발생했을 때 어떻게 접근하셨나요?"

                ---
                [경력 수준별 기준]

                - JUNIOR:
                  개념 이해 중심 (A 우선)
                  → 정의 + 기본 동작 설명 가능한 질문
                - MID:
                  개념 + 경험 연결 (A 또는 B)
                  → 실제 사용 경험이나 선택 이유
                - SENIOR:
                  설계 판단 중심 (B 우선)
                  → trade-off, 설계 이유, 의사결정

                ---
                [출력 규칙]
                - 반드시 한 개의 질문만 생성한다
                - 질문 1~2문장만 출력한다 (인사 없이 바로 질문으로 시작)
                - 불필요한 설명 금지
                - "면접관:" 같은 접두사 금지
                - "~도", "그리고" 등으로 질문을 이어붙이지 말 것
                - 줄바꿈(\\n)을 사용하지 말고, 모든 내용을 한 줄로 출력할 것

                ---
                [금지 사항]
                - 너무 추상적인 질문 (예: "시스템 설계 시 중요한 것은 무엇인가요?")
                - 여러 개를 동시에 묻는 질문
                - 답변 범위가 지나치게 넓은 질문
                - 이론만 요구하거나, 경험만 강요하는 질문

                ---
                [강제 규칙]
                - 하나의 질문에는 하나의 평가 포인트만 포함해야 한다
                - "그리고", "~고", "~면서" 형태로 질문을 확장하지 말 것
                - 개념 + 추가 해석(예: 데이터 일관성, 성능 영향 등)을 동시에 묻지 말 것
                - 답변 범위가 명확히 떠오르지 않는 질문은 생성하지 말 것

                ---
                [키워드 선택 금지 규칙]
                다음과 같은 추상적인 키워드는 선택하지 않는다:
                - 데이터 일관성
                - 성능
                - 안정성
                - 확장성
                - 최적화

                이러한 키워드는 반드시 더 구체적인 기술 또는 경험으로 변환해서 질문해야 한다.

                [자기소개 기반성 규칙]

                질문은 반드시 자기소개에 등장한 "구체적인 기술 또는 경험"에 직접 연결되어야 한다.

                - 단순 개념 키워드만으로 질문하지 말 것
                - 반드시 실제 사용한 기술 (예: JPA, Spring, Redis 등)을 중심으로 질문할 것

                이 기준을 모두 만족하는, 가장 적절한 "첫 질문"을 생성하세요.
                """;

        String userPrompt = """
                다음 정보를 바탕으로 첫 번째 면접 질문을 생성하세요.

                - 포지션: %s
                - 경력 수준: %s
                - 면접 유형: %s
                - 지원자 자기소개:
                \"\"\"
                %s
                \"\"\"
                """.formatted(position, experienceLevel.getDescription(), interviewType.getDescription(), selfIntroduction);

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
                                        String position, ExperienceLevel experienceLevel, String selfIntroduction) {
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
}
