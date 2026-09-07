package app.mockly.domain.interview.service;

import app.mockly.domain.interview.dto.InterviewPrompt;
import app.mockly.domain.interview.entity.ExperienceLevel;
import app.mockly.domain.interview.entity.InterviewMessage;
import app.mockly.domain.interview.entity.InterviewMessageRole;
import app.mockly.domain.interview.entity.InterviewSession;
import app.mockly.domain.interview.entity.InterviewType;
import app.mockly.domain.product.entity.PlanTier;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class InterviewPromptFactoryTest {

    private InterviewPromptFactory factory;

    @BeforeEach
    void setUp() {
        factory = new InterviewPromptFactory(new ObjectMapper());
    }

    @Test
    void rendersFirstQuestionContext() {
        InterviewSession session = session("백엔드 개발자", "JPA");

        InterviewPrompt prompt = factory.firstQuestion(session);

        assertThat(prompt.system()).contains("키워드 [JPA]");
        assertThat(prompt.user())
                .contains("포지션: 백엔드 개발자")
                .contains("경력 수준: 주니어 (0~2년)")
                .contains("탐색 키워드: JPA");
    }

    @Test
    void rendersFollowUpHistoryWithStableRoles() {
        InterviewSession session = session("백엔드 개발자", "JPA");
        List<InterviewMessage> history = List.of(
                message(InterviewMessageRole.INTERVIEWER, "JPA의 영속성 컨텍스트란 무엇인가요?"),
                message(InterviewMessageRole.USER, "엔티티를 영구 저장하는 환경입니다."));

        InterviewPrompt prompt = factory.followUpQuestion(session, history);

        assertThat(prompt.user())
                .contains("\"role\":\"interviewer\"")
                .contains("\"role\":\"candidate\"")
                .contains("엔티티를 영구 저장하는 환경입니다.");
    }

    @ParameterizedTest
    @EnumSource(PlanTier.class)
    void rendersTheExistingFeedbackContractForEachTier(PlanTier tier) {
        InterviewPrompt prompt = factory.feedback(
                List.of(message(InterviewMessageRole.USER, "질문의 의도를 먼저 확인했습니다.")),
                InterviewType.TECHNICAL,
                tier);

        assertThat(prompt.system()).contains(expectedTierContract(tier));
        assertThat(prompt.user())
                .contains("면접 유형: 기술 면접")
                .contains("질문의 의도를 먼저 확인했습니다.");
    }

    private InterviewSession session(String position, String keyword) {
        return InterviewSession.builder()
                .position(position)
                .selfIntroduction("서버 개발 경험을 쌓았습니다.")
                .experienceLevel(ExperienceLevel.JUNIOR)
                .interviewType(InterviewType.TECHNICAL)
                .firstQuestionKeyword(keyword)
                .build();
    }

    private InterviewMessage message(InterviewMessageRole role, String content) {
        return InterviewMessage.builder()
                .role(role)
                .content(content)
                .build();
    }

    private String expectedTierContract(PlanTier tier) {
        return switch (tier) {
            case FREE -> "strengths는 빈 배열로 설정합니다.";
            case BASIC -> "nextPracticePoint는 null로 설정합니다.";
            case PRO -> "nextPracticePoint는 다음 면접에서 적용할 행동 한 문장";
        };
    }
}
