package app.mockly.domain.interview.evaluation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

class InterviewEvaluationDatasetTest {

    private static final Path DATASET_DIR = Path.of("evals/interview/datasets");
    private static final Set<String> ALLOWED_SOURCES = Set.of("ANONYMIZED", "SYNTHETIC");
    private static final Pattern EMAIL = Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");
    private static final Pattern PHONE = Pattern.compile("(?<!\\d)01[016789][- ]?\\d{3,4}[- ]?\\d{4}(?!\\d)");
    private static final Pattern URL = Pattern.compile("https?://|www\\.", Pattern.CASE_INSENSITIVE);
    private static final Pattern UUID = Pattern.compile("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[1-5][0-9a-fA-F]{3}-[89abAB][0-9a-fA-F]{3}-[0-9a-fA-F]{12}");

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void datasetsFollowTheSharedContractAndExpectedCaseCounts() throws IOException {
        Set<String> caseIds = new HashSet<>();
        Set<String> sources = new HashSet<>();

        assertDataset("first-question.jsonl", "FIRST_QUESTION", 15, caseIds, sources);
        assertDataset("follow-up-question.jsonl", "FOLLOW_UP_QUESTION", 25, caseIds, sources);
        assertDataset("feedback.jsonl", "FEEDBACK", 20, caseIds, sources);

        assertThat(sources).contains("ANONYMIZED", "SYNTHETIC");
    }

    private void assertDataset(String filename, String expectedTarget, int expectedCount,
                               Set<String> caseIds, Set<String> sources) throws IOException {
        List<String> lines = Files.readAllLines(DATASET_DIR.resolve(filename)).stream()
                .filter(line -> !line.isBlank())
                .toList();
        assertThat(lines).hasSize(expectedCount);

        for (String line : lines) {
            JsonNode example = objectMapper.readTree(line);
            String caseId = requiredText(example, "caseId");
            String source = requiredText(example, "source");

            assertThat(caseIds.add(caseId)).as("duplicate caseId: %s", caseId).isTrue();
            assertThat(requiredText(example, "target")).isEqualTo(expectedTarget);
            assertThat(source).isIn(ALLOWED_SOURCES);
            assertThat(example.path("input").isObject()).isTrue();
            assertNonEmptyArray(example, "tags");
            assertNonEmptyArray(example, "mustSatisfy");
            assertNonEmptyArray(example, "forbiddenErrors");
            assertNoDirectIdentifier(line, caseId);
            sources.add(source);
        }
    }

    private String requiredText(JsonNode example, String fieldName) {
        String value = example.path(fieldName).asText();
        assertThat(value).as(fieldName).isNotBlank();
        return value;
    }

    private void assertNonEmptyArray(JsonNode example, String fieldName) {
        JsonNode value = example.path(fieldName);
        assertThat(value.isArray()).as(fieldName).isTrue();
        assertThat(value).as(fieldName).isNotEmpty();
        value.forEach(element -> assertThat(element.asText()).isNotBlank());
    }

    private void assertNoDirectIdentifier(String line, String caseId) {
        assertThat(EMAIL.matcher(line).find()).as("email in %s", caseId).isFalse();
        assertThat(PHONE.matcher(line).find()).as("phone in %s", caseId).isFalse();
        assertThat(URL.matcher(line).find()).as("url in %s", caseId).isFalse();
        assertThat(UUID.matcher(line).find()).as("uuid in %s", caseId).isFalse();
    }
}
