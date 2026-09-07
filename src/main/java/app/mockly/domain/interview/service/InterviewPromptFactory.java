package app.mockly.domain.interview.service;

import app.mockly.domain.interview.dto.InterviewPrompt;
import app.mockly.domain.interview.entity.ExperienceLevel;
import app.mockly.domain.interview.entity.InterviewMessage;
import app.mockly.domain.interview.entity.InterviewMessageRole;
import app.mockly.domain.interview.entity.InterviewSession;
import app.mockly.domain.interview.entity.InterviewType;
import app.mockly.domain.product.entity.PlanTier;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

@Slf4j
@Component
public class InterviewPromptFactory {

    private static final String PROMPT_PATH = "prompts/interview/";

    private final ObjectMapper objectMapper;
    private final String firstQuestionSystemTemplate;
    private final String firstQuestionUserTemplate;
    private final String followUpQuestionSystemTemplate;
    private final String followUpQuestionUserTemplate;
    private final String feedbackSystemTemplate;
    private final String feedbackUserTemplate;

    public InterviewPromptFactory(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.firstQuestionSystemTemplate = loadTemplate("first-question-system.txt");
        this.firstQuestionUserTemplate = loadTemplate("first-question-user.txt");
        this.followUpQuestionSystemTemplate = loadTemplate("follow-up-question-system.txt");
        this.followUpQuestionUserTemplate = loadTemplate("follow-up-question-user.txt");
        this.feedbackSystemTemplate = loadTemplate("feedback-system.txt");
        this.feedbackUserTemplate = loadTemplate("feedback-user.txt");
    }

    public InterviewPrompt firstQuestion(InterviewSession session) {
        String keyword = session.getFirstQuestionKeyword();
        String systemPrompt = firstQuestionSystemTemplate.formatted(keyword, keyword);
        String userPrompt = firstQuestionUserTemplate.formatted(
                session.getPosition(),
                session.getExperienceLevel().getDescription(),
                session.getInterviewType().getDescription(),
                session.getSelfIntroduction(),
                keyword);
        return new InterviewPrompt(systemPrompt, userPrompt);
    }

    public InterviewPrompt followUpQuestion(InterviewSession session, List<InterviewMessage> history) {
        String userPrompt = followUpQuestionUserTemplate.formatted(
                session.getPosition(),
                session.getExperienceLevel().getDescription(),
                session.getInterviewType().getDescription(),
                getLevelContext(session.getExperienceLevel()),
                session.getSelfIntroduction(),
                formatHistory(history));
        return new InterviewPrompt(followUpQuestionSystemTemplate, userPrompt);
    }

    public InterviewPrompt feedback(List<InterviewMessage> history, InterviewType interviewType, PlanTier tier) {
        String systemPrompt = feedbackSystemTemplate.formatted(buildStructuredFeedbackGuide(tier));
        String userPrompt = feedbackUserTemplate.formatted(
                interviewType.getDescription(),
                formatHistory(history));
        return new InterviewPrompt(systemPrompt, userPrompt);
    }

    private String getLevelContext(ExperienceLevel experienceLevel) {
        return switch (experienceLevel) {
            case JUNIOR -> "깊이보다 사고 과정과 학습 의지를 중심으로 평가하세요.";
            case MID -> "실무 경험을 바탕으로 문제를 어떻게 해결했는지를 중심으로 평가하세요.";
            case SENIOR -> "기술적 판단의 근거, 트레이드오프 인식, 조직/팀 관점까지 함께 평가하세요.";
        };
    }

    private String buildStructuredFeedbackGuide(PlanTier tier) {
        if (tier == PlanTier.FREE) {
            return """
                    strengths는 빈 배열로 설정합니다.
                    improvements는 rank 1인 항목 한 건만 생성하고 title과 summary만 작성합니다.
                    coachBrief.keyStrength, coachBrief.keyImprovement, improvement.detail, improvement.quote,
                    nextPracticePoint는 모두 null로 설정합니다.
                    """;
        }
        if (tier == PlanTier.BASIC) {
            return """
                    coachBrief의 세 필드를 모두 작성합니다.
                    strengths는 sortOrder 1~3인 세 건, improvements는 rank 1~3인 세 건을 생성합니다.
                    각 항목의 detail과 quote를 모두 작성하고 nextPracticePoint는 null로 설정합니다.
                    """;
        }
        return """
                coachBrief의 세 필드를 모두 작성합니다.
                strengths는 sortOrder 1~3인 세 건, improvements는 rank 1~3인 세 건을 생성합니다.
                각 항목의 detail과 quote를 모두 작성합니다.
                nextPracticePoint는 다음 면접에서 적용할 행동 한 문장으로 60자 이내로 작성합니다.
                """;
    }

    private String formatHistory(List<InterviewMessage> history) {
        List<HistoryEntry> entries = history.stream()
                .map(message -> new HistoryEntry(
                        message.getRole() == InterviewMessageRole.INTERVIEWER ? "interviewer" : "candidate",
                        message.getContent()))
                .toList();
        try {
            return objectMapper.writeValueAsString(entries);
        } catch (Exception exception) {
            log.error("대화 기록 직렬화 실패", exception);
            return "[]";
        }
    }

    private String loadTemplate(String filename) {
        try {
            return new ClassPathResource(PROMPT_PATH + filename)
                    .getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new IllegalStateException("면접 프롬프트 리소스를 읽을 수 없습니다: " + filename, exception);
        }
    }

    private record HistoryEntry(String role, String content) {
    }
}
