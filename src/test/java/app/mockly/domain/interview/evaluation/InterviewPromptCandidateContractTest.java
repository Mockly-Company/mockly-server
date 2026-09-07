package app.mockly.domain.interview.evaluation;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class InterviewPromptCandidateContractTest {

    private static final Path CANDIDATE_DIR = Path.of("evals/interview/prompts/candidate");

    private static String firstQuestionSystem;
    private static String firstQuestionUser;
    private static String followUpQuestionSystem;
    private static String followUpQuestionUser;
    private static String feedbackSystem;
    private static String feedbackUser;

    @BeforeAll
    static void loadCandidates() throws IOException {
        firstQuestionSystem = read("first-question-system.txt");
        firstQuestionUser = read("first-question-user.txt");
        followUpQuestionSystem = read("follow-up-question-system.txt");
        followUpQuestionUser = read("follow-up-question-user.txt");
        feedbackSystem = read("feedback-system.txt");
        feedbackUser = read("feedback-user.txt");
    }

    @Test
    void firstQuestionCandidateRequiresOnePlainQuestionWithoutUnsupportedAssumptions() {
        assertThat(firstQuestionSystem)
                .contains("<role>", "<objective>", "<input_rules>")
                .contains("<decision_process>", "<output_contract>", "<verification>")
                .contains("하나의 평가 포인트")
                .contains("한 문장")
                .contains("Markdown")
                .contains("칭찬")
                .contains("역할 접두사")
                .contains("사실로 전제하지 마세요");
        assertThat(firstQuestionUser)
                .contains("<context>", "<candidate_data>", "<task>")
                .contains("<position>%s</position>")
                .contains("<experience_level>%s</experience_level>")
                .contains("<interview_type>%s</interview_type>")
                .contains("<keyword>%s</keyword>");

        String renderedUserPrompt = firstQuestionUser.formatted(
                "backend", "junior", "technical", "self-introduction", "JPA");
        assertThat(renderedUserPrompt)
                .contains("<self_introduction>self-introduction</self_introduction>")
                .contains("<keyword>JPA</keyword>");
    }

    @Test
    void followUpCandidateUsesTheLatestAnswerAndControlsTopicChanges() {
        assertThat(followUpQuestionSystem)
                .contains("<role>", "<objective>", "<input_rules>")
                .contains("<decision_process>", "<output_contract>", "<verification>")
                .contains("직전 답변")
                .contains("질문 중복")
                .contains("주제가 충분히 탐색")
                .contains("새 주제로 전환")
                .contains("Markdown");
        assertThat(followUpQuestionUser)
                .contains("<context>", "<candidate_data>", "<conversation_json>", "<task>")
                .contains("<experience_criteria>%s</experience_criteria>")
                .contains("%s");
    }

    @Test
    void feedbackCandidateRequiresGroundedExactQuotesAndTierContracts() {
        assertThat(feedbackSystem)
                .contains("<role>", "<objective>", "<grounding_rules>")
                .contains("<scoring_rules>", "<tier_contract>")
                .contains("<output_contract>", "<verification>")
                .contains("지원자의 실제 답변")
                .contains("연속된 원문")
                .contains("questionNumber")
                .contains("Free", "Basic", "Pro")
                .contains("점수와 서술")
                .contains("Markdown");
        assertThat(feedbackUser)
                .contains("<context>", "<conversation_json>", "<task>")
                .contains("<interview_type>%s</interview_type>")
                .contains("%s");
    }

    private static String read(String filename) throws IOException {
        return Files.readString(CANDIDATE_DIR.resolve(filename));
    }
}
