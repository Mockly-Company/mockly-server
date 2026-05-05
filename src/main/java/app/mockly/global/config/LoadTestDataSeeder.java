package app.mockly.global.config;

import app.mockly.domain.auth.entity.OAuth2Provider;
import app.mockly.domain.auth.entity.User;
import app.mockly.domain.auth.repository.UserRepository;
import app.mockly.domain.auth.service.JwtService;
import app.mockly.domain.interview.entity.ExperienceLevel;
import app.mockly.domain.interview.entity.InterviewMessage;
import app.mockly.domain.interview.entity.InterviewQuota;
import app.mockly.domain.interview.entity.InterviewSession;
import app.mockly.domain.interview.entity.InterviewType;
import app.mockly.domain.interview.repository.InterviewMessageRepository;
import app.mockly.domain.interview.repository.InterviewQuotaRepository;
import app.mockly.domain.interview.repository.InterviewSessionRepository;
import app.mockly.domain.product.entity.PlanTier;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.io.FileWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
@Profile("loadtest")
@RequiredArgsConstructor
public class LoadTestDataSeeder implements ApplicationRunner {

    private static final int USER_COUNT = 30;
    private static final int SESSIONS_PER_USER = 300;

    private final UserRepository userRepository;
    private final InterviewSessionRepository interviewSessionRepository;
    private final InterviewMessageRepository interviewMessageRepository;
    private final InterviewQuotaRepository interviewQuotaRepository;
    private final JwtService jwtService;
    private final ObjectMapper objectMapper;

    @Override
    public void run(ApplicationArguments args) throws Exception {
        log.info("[LoadTest] 데이터 시딩 시작 — {} 사용자 × {} 세션", USER_COUNT, SESSIONS_PER_USER);

        seedQuota();

        List<Map<String, Object>> fixtures = new ArrayList<>();

        for (int i = 0; i < USER_COUNT; i++) {
            User user = createUser(i);
            String token = jwtService.generateAccessToken(user.getId());

            List<String> sessionIds = new ArrayList<>();
            for (int j = 0; j < SESSIONS_PER_USER; j++) {
                InterviewSession session = createSession(user);
                createMessages(session);
                sessionIds.add(session.getId().toString());
            }

            fixtures.add(Map.of(
                    "userId", user.getId().toString(),
                    "token", token,
                    "sessionIds", sessionIds
            ));
        }

        String json = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(fixtures);
        try (FileWriter writer = new FileWriter("loadtest-fixtures.json")) {
            writer.write(json);
        }

        log.info("[LoadTest] 데이터 시딩 완료 — {} 세션 생성, loadtest-fixtures.json 출력", USER_COUNT * SESSIONS_PER_USER);
    }

    private void seedQuota() {
        if (interviewQuotaRepository.findById(PlanTier.FREE).isEmpty()) {
            interviewQuotaRepository.save(InterviewQuota.builder()
                    .planTier(PlanTier.FREE)
                    .dailyLimit(5)
                    .maxQuestionsPerSession(3)
                    .build());
        }
    }

    private User createUser(int index) {
        User user = User.builder()
                .provider(OAuth2Provider.GOOGLE)
                .providerId("loadtest-" + index)
                .email("loadtest-" + index + "@test.com")
                .name("LoadTestUser" + index)
                .build();
        return userRepository.save(user);
    }

    private InterviewSession createSession(User user) {
        InterviewSession session = InterviewSession.builder()
                .user(user)
                .position("백엔드 개발자")
                .selfIntroduction("Spring Boot와 JPA를 활용한 서버 개발 경험이 있습니다.")
                .experienceLevel(ExperienceLevel.JUNIOR)
                .interviewType(InterviewType.TECHNICAL)
                .totalQuestions(3)
                .currentQuestionNumber(3)
                .status(app.mockly.domain.interview.entity.InterviewSessionStatus.IN_PROGRESS)
                .firstQuestionKeyword("Spring Boot")
                .build();
        return interviewSessionRepository.save(session);
    }

    private void createMessages(InterviewSession session) {
        List<InterviewMessage> messages = List.of(
                InterviewMessage.createInterviewerMessage(session,
                        "Spring Boot에서 의존성 주입이란 무엇인가요?", 1),
                InterviewMessage.createUserMessage(session,
                        "의존성 주입은 객체가 필요한 의존 객체를 외부에서 주입받는 패턴입니다.", 1),
                InterviewMessage.createInterviewerMessage(session,
                        "생성자 주입이 권장되는 이유는 무엇인가요?", 2),
                InterviewMessage.createUserMessage(session,
                        "불변성을 보장하고 순환 참조를 컴파일 타임에 발견할 수 있기 때문입니다.", 2),
                InterviewMessage.createInterviewerMessage(session,
                        "순환 참조를 구조적으로 해결하는 방법은 무엇이 있나요?", 3)
        );
        interviewMessageRepository.saveAll(messages);
    }
}
