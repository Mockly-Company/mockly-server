package app.mockly.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

class SensitiveDataLoggingTest {

    private static final Pattern LOG_CALL = Pattern.compile(
            "log\\.(?:trace|debug|info|warn|error)\\s*\\((?s:.*?)\\);"
    );

    @Test
    @DisplayName("운영 로그에는 빌링키를 기록하지 않는다")
    void productionLogsDoNotContainBillingKeys() throws IOException {
        List<String> filesWithSensitiveLogs = new ArrayList<>();

        try (var paths = Files.walk(Path.of("src/main/java"))) {
            for (Path path : paths.filter(candidate -> candidate.toString().endsWith(".java")).toList()) {
                String source = Files.readString(path);
                Matcher matcher = LOG_CALL.matcher(source);
                while (matcher.find()) {
                    if (matcher.group().contains("billingKey")) {
                        filesWithSensitiveLogs.add(path.toString());
                    }
                }
            }
        }

        assertThat(filesWithSensitiveLogs)
                .as("빌링키를 포함한 로그 호출이 있는 파일")
                .isEmpty();
    }
}
