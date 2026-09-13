package app.mockly.domain.interview.controller;

import app.mockly.domain.auth.entity.OAuth2Provider;
import app.mockly.domain.auth.entity.User;
import app.mockly.domain.auth.repository.UserRepository;
import app.mockly.domain.auth.service.JwtService;
import app.mockly.domain.auth.service.TokenBlacklistService;
import app.mockly.domain.interview.controller.docs.AbandonSessionDocs;
import app.mockly.domain.interview.controller.docs.CreateInterviewDocs;
import app.mockly.domain.interview.controller.docs.FeedbackDocs;
import app.mockly.domain.interview.controller.docs.InterviewOverviewDocs;
import app.mockly.domain.interview.controller.docs.QuotaDocs;
import app.mockly.domain.interview.controller.docs.RetryFeedbackDocs;
import app.mockly.domain.interview.controller.docs.SessionDetailDocs;
import app.mockly.domain.interview.controller.docs.SessionListDocs;
import app.mockly.domain.interview.controller.docs.StreamQuestionDocs;
import app.mockly.domain.interview.controller.docs.SubmitAnswerDocs;
import app.mockly.domain.interview.dto.InterviewFeedbackResult;
import app.mockly.domain.interview.dto.request.CreateInterviewRequest;
import app.mockly.domain.interview.dto.request.SubmitAnswerRequest;
import app.mockly.domain.interview.entity.ExperienceLevel;
import app.mockly.domain.interview.entity.InterviewFeedback;
import app.mockly.domain.interview.entity.InterviewMessage;
import app.mockly.domain.interview.entity.InterviewSession;
import app.mockly.domain.interview.entity.FeedbackStatus;
import app.mockly.domain.interview.entity.InterviewSessionStatus;
import app.mockly.domain.interview.entity.InterviewType;
import reactor.core.publisher.Flux;
import app.mockly.domain.interview.repository.InterviewFeedbackRepository;
import app.mockly.domain.interview.repository.InterviewMessageRepository;
import app.mockly.domain.interview.repository.InterviewSessionRepository;
import app.mockly.domain.interview.service.FeedbackSseManager;
import app.mockly.domain.interview.service.InterviewAiService;
import app.mockly.domain.product.entity.BillingCycle;
import app.mockly.domain.product.entity.Currency;
import app.mockly.domain.product.entity.PlanTier;
import app.mockly.domain.product.entity.SubscriptionPlan;
import app.mockly.domain.product.entity.SubscriptionProduct;
import app.mockly.domain.product.entity.Subscription;
import app.mockly.domain.product.repository.SubscriptionPlanRepository;
import app.mockly.domain.product.repository.SubscriptionProductRepository;
import app.mockly.domain.product.repository.SubscriptionRepository;
import app.mockly.global.exception.BusinessException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.restdocs.AutoConfigureRestDocs;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import static com.epages.restdocs.apispec.MockMvcRestDocumentationWrapper.document;
import static com.epages.restdocs.apispec.ResourceDocumentation.resource;
import java.util.List;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static app.mockly.domain.interview.FeedbackTestFixtures.feedbackResult;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.get;
import static org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.patch;
import static org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;

@SpringBootTest
@AutoConfigureMockMvc
@AutoConfigureRestDocs
@Transactional
class InterviewControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JwtService jwtService;

    @MockitoBean
    private TokenBlacklistService tokenBlacklistService;

    @MockitoBean
    private InterviewAiService interviewAiService;

    @MockitoBean
    private FeedbackSseManager feedbackSseManager;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private InterviewSessionRepository interviewSessionRepository;

    @Autowired
    private InterviewFeedbackRepository interviewFeedbackRepository;

    @Autowired
    private InterviewMessageRepository interviewMessageRepository;

    @Autowired
    private SubscriptionProductRepository subscriptionProductRepository;

    @Autowired
    private SubscriptionPlanRepository subscriptionPlanRepository;

    @Autowired
    private SubscriptionRepository subscriptionRepository;

    @Autowired
    private EntityManager entityManager;

    private User testUser;
    private String validAccessToken;
    private Subscription currentSubscription;

    @BeforeEach
    void setUp() {
        testUser = userRepository.save(User.builder()
                .email("test@example.com")
                .name("테스트 사용자")
                .provider(OAuth2Provider.GOOGLE)
                .providerId("test-google-id")
                .build());

        validAccessToken = jwtService.generateAccessToken(testUser.getId());

        given(tokenBlacklistService.isBlacklisted(anyString())).willReturn(false);
        given(interviewAiService.extractKeywordCandidates(anyString(), anyString()))
                .willReturn(List.of("JPA", "Spring Boot", "REST API"));
        given(interviewAiService.generateFirstQuestion(any(app.mockly.domain.interview.entity.InterviewSession.class)))
                .willReturn(Flux.just("자기소개를 해주세요."));
        given(interviewAiService.generateNextQuestion(any(app.mockly.domain.interview.entity.InterviewSession.class), any()))
                .willReturn(Flux.just("다음 면접 질문을 하겠습니다."));
        given(interviewAiService.generateFeedback(any(), any(InterviewType.class), any(PlanTier.class)))
                .willReturn(feedbackResult(PlanTier.FREE));

        SubscriptionProduct freeProduct = subscriptionProductRepository.save(SubscriptionProduct.builder()
                .name("Free")
                .planTier(PlanTier.FREE)
                .isActive(true)
                .maxQuestions(3)
                .weeklyInterviewLimit(1)
                .weeklyImprovementPracticeLimit(0)
                .build());
        SubscriptionPlan freePlan = subscriptionPlanRepository.save(SubscriptionPlan.builder()
                .product(freeProduct)
                .price(BigDecimal.ZERO)
                .currency(Currency.KRW)
                .billingCycle(BillingCycle.MONTHLY)
                .build());
        currentSubscription = Subscription.create(testUser.getId(), freePlan);
        currentSubscription.activate();
        currentSubscription = subscriptionRepository.save(currentSubscription);

    }

    @Test
    @DisplayName("GET /api/interviews/quota - 실패: 미납 구독은 모든 면접 API 이용 정지 (402)")
    void getQuota_unpaidSubscription() throws Exception {
        currentSubscription.markAsPastDue(Instant.now().minus(8, ChronoUnit.DAYS));
        currentSubscription.markAsUnpaid();
        subscriptionRepository.flush();

        mockMvc.perform(get("/api/interviews/quota")
                        .header("Authorization", "Bearer " + validAccessToken))
                .andDo(print())
                .andExpect(status().isPaymentRequired())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error").value("SUBSCRIPTION_UNPAID"))
                .andDo(document("interview-get-quota-subscription-unpaid",
                        resource(QuotaDocs.subscriptionUnpaid())
                ));
    }

    @Test
    @DisplayName("GET /api/interviews/overview - 성공: 실제 면접·피드백으로 Overview 조회")
    void getOverview_withInterviewHistory() throws Exception {
        activateProSubscription();
        Instant now = Instant.now();
        InterviewSession previousSession = saveOverviewSession(
                now.plus(10, ChronoUnit.MINUTES), 76, PlanTier.PRO);
        InterviewSession latestSession = saveOverviewSession(
                now.plus(20, ChronoUnit.MINUTES), 84, PlanTier.PRO);
        interviewFeedbackRepository.flush();
        entityManager.clear();

        mockMvc.perform(get("/api/interviews/overview")
                        .header("Authorization", "Bearer " + validAccessToken))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.summary.completedCount").value(2))
                .andExpect(jsonPath("$.data.summary.totalPracticeSeconds").isNumber())
                .andExpect(jsonPath("$.data.score.latest").value(84))
                .andExpect(jsonPath("$.data.score.change").value(8))
                .andExpect(jsonPath("$.data.recentInterviews.length()").value(2))
                .andExpect(jsonPath("$.data.recentInterviews[0].sessionId")
                        .value(latestSession.getId().toString()))
                .andExpect(jsonPath("$.data.recentInterviews[0].overallScore").value(84))
                .andExpect(jsonPath("$.data.recentInterviews[1].sessionId")
                        .value(previousSession.getId().toString()))
                .andExpect(jsonPath("$.data.recentInterviews[1].overallScore").value(76))
                .andExpect(jsonPath("$.data.nextPracticePoint").value("결론부터 답변하기"))
                .andDo(document("interview-get-overview",
                        resource(InterviewOverviewDocs.success())
                ));
    }

    @Test
    @DisplayName("GET /api/interviews/overview - 성공: Free는 저장된 Pro 연습 포인트를 노출하지 않음")
    void getOverview_freePlanDoesNotExposeNextPracticePoint() throws Exception {
        saveOverviewSession(Instant.now().plus(10, ChronoUnit.MINUTES), 80, PlanTier.PRO);
        interviewFeedbackRepository.flush();
        entityManager.clear();

        mockMvc.perform(get("/api/interviews/overview")
                        .header("Authorization", "Bearer " + validAccessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.score.latest").value(80))
                .andExpect(jsonPath("$.data.recentInterviews[0].overallScore").value(80))
                .andExpect(jsonPath("$.data.nextPracticePoint").doesNotExist());
    }

    @Test
    @DisplayName("GET /api/interviews/overview - 성공: 면접 이력이 없는 사용자의 Overview 조회")
    void getOverview_withoutInterviewHistory() throws Exception {
        mockMvc.perform(get("/api/interviews/overview")
                        .header("Authorization", "Bearer " + validAccessToken))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.summary.periodStart").isString())
                .andExpect(jsonPath("$.data.summary.nextResetAt").isString())
                .andExpect(jsonPath("$.data.summary.completedCount").value(0))
                .andExpect(jsonPath("$.data.summary.totalPracticeSeconds").value(0))
                .andExpect(jsonPath("$.data.score.latest").doesNotExist())
                .andExpect(jsonPath("$.data.score.change").doesNotExist())
                .andExpect(jsonPath("$.data.recentInterviews").isEmpty())
                .andExpect(jsonPath("$.data.nextPracticePoint").doesNotExist())
                .andDo(document("interview-get-overview-empty",
                        resource(InterviewOverviewDocs.empty())
                ));
    }

    @Test
    @DisplayName("GET /api/interviews/overview - 실패: 미납 구독은 이용 정지 (402)")
    void getOverview_unpaidSubscription() throws Exception {
        currentSubscription.markAsPastDue(Instant.now().minus(8, ChronoUnit.DAYS));
        currentSubscription.markAsUnpaid();
        subscriptionRepository.flush();

        mockMvc.perform(get("/api/interviews/overview")
                        .header("Authorization", "Bearer " + validAccessToken))
                .andExpect(status().isPaymentRequired())
                .andExpect(jsonPath("$.error").value("SUBSCRIPTION_UNPAID"))
                .andDo(document("interview-get-overview-subscription-unpaid",
                        resource(InterviewOverviewDocs.subscriptionUnpaid())
                ));
    }

    @Test
    @DisplayName("GET /api/interviews - 실패: 유예기간이 끝난 PAST_DUE도 즉시 이용 정지 (402)")
    void getSessions_expiredPastDueSubscription() throws Exception {
        currentSubscription.markAsPastDue(Instant.now().minus(8, ChronoUnit.DAYS));
        subscriptionRepository.flush();

        mockMvc.perform(get("/api/interviews")
                        .header("Authorization", "Bearer " + validAccessToken))
                .andExpect(status().isPaymentRequired())
                .andExpect(jsonPath("$.error").value("SUBSCRIPTION_UNPAID"));
    }

    @Test
    @DisplayName("GET /api/interviews/quota - 실패: 결제 확정 전 PENDING 구독에는 권한을 부여하지 않음")
    void getQuota_pendingSubscription() throws Exception {
        currentSubscription.cancel();
        subscriptionRepository.flush();
        subscriptionRepository.save(Subscription.create(testUser.getId(), currentSubscription.getSubscriptionPlan()));

        mockMvc.perform(get("/api/interviews/quota")
                        .header("Authorization", "Bearer " + validAccessToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    @DisplayName("POST /api/interviews - 성공: FREE 플랜 세션 생성")
    void createSession_Success() throws Exception {
        CreateInterviewRequest request = new CreateInterviewRequest(
                "백엔드 개발자",
                ExperienceLevel.JUNIOR,
                InterviewType.TECHNICAL,
                3,
                "1년차 백엔드 개발자로 이커머스 서비스에서 가격 정책과 재고 도메인을 다뤘습니다. Spring Boot와 JPA를 사용해 API를 개발하였고, 데이터 일관성 문제에 관심을 가지고 있습니다."
        );

        mockMvc.perform(post("/api/interviews")
                        .header("Authorization", "Bearer " + validAccessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andDo(print())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.sessionId").exists())
                .andExpect(jsonPath("$.data.position").value("백엔드 개발자"))
                .andExpect(jsonPath("$.data.status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.data.greeting").exists())
                .andDo(document("interview-create",
                        resource(CreateInterviewDocs.success())
                ));
    }

    @Test
    @DisplayName("POST /api/interviews - 실패: 주간 쿼터 초과 (429)")
    void createSession_QuotaExceeded() throws Exception {
        CreateInterviewRequest request = new CreateInterviewRequest(
                "프론트엔드 개발자",
                ExperienceLevel.JUNIOR,
                InterviewType.TECHNICAL,
                3,
                "1년차 프론트엔드 개발자입니다."
        );

        mockMvc.perform(post("/api/interviews")
                        .header("Authorization", "Bearer " + validAccessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/interviews")
                        .header("Authorization", "Bearer " + validAccessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andDo(print())
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error").value("QUOTA_EXCEEDED"))
                .andDo(document("interview-create-quota-exceeded",
                        resource(CreateInterviewDocs.quotaExceeded())
                ));
    }

    @Test
    @DisplayName("POST /api/interviews - 실패: 플랜 허용 범위 외 질문 개수 (400)")
    void createSession_InvalidQuestionCount() throws Exception {
        // FREE 플랜은 최대 3문항 → 5문항 요청 시 실패
        CreateInterviewRequest request = new CreateInterviewRequest(
                "백엔드 개발자",
                ExperienceLevel.JUNIOR,
                InterviewType.TECHNICAL,
                5,
                "1년차 백엔드 개발자입니다."
        );

        mockMvc.perform(post("/api/interviews")
                        .header("Authorization", "Bearer " + validAccessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andDo(print())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"))
                .andDo(document("interview-create-invalid-question-count",
                        resource(CreateInterviewDocs.invalidQuestionCount())
                ));
    }

    @Test
    @DisplayName("POST /api/interviews/:sessionId/answer - 성공: 중간 답변 제출")
    void submitAnswer_withNextQuestion() throws Exception {
        InterviewSession session = saveSession(1, 3, InterviewSessionStatus.IN_PROGRESS);
        SubmitAnswerRequest request = new SubmitAnswerRequest("JVM의 가비지 컬렉션은 메모리를 자동으로 관리합니다.");

        mockMvc.perform(post("/api/interviews/{sessionId}/answer", session.getId())
                        .header("Authorization", "Bearer " + validAccessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.sessionStatus").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.data.currentQuestionNumber").value(2))
                .andDo(document("interview-submit-answer",
                        resource(SubmitAnswerDocs.success())
                ));
    }

    @Test
    @DisplayName("POST /api/interviews/:sessionId/answer - 성공: 마지막 답변, 피드백 대기 상태 반환")
    void submitAnswer_feedbackPending() throws Exception {
        InterviewSession session = saveSession(3, 3, InterviewSessionStatus.IN_PROGRESS);
        SubmitAnswerRequest request = new SubmitAnswerRequest("마지막 질문에 대한 답변입니다.");

        mockMvc.perform(post("/api/interviews/{sessionId}/answer", session.getId())
                        .header("Authorization", "Bearer " + validAccessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.sessionStatus").value("FEEDBACK_PENDING"))
                .andExpect(jsonPath("$.data.feedback").doesNotExist())
                .andDo(document("interview-submit-answer-feedback-pending",
                        resource(SubmitAnswerDocs.feedbackPending())
                ));
    }

    @Test
    @DisplayName("POST /api/interviews/:sessionId/answer - 실패: 이미 완료된 세션 (400)")
    void submitAnswer_alreadyCompleted() throws Exception {
        InterviewSession session = saveSession(3, 3, InterviewSessionStatus.COMPLETED);
        SubmitAnswerRequest request = new SubmitAnswerRequest("답변 내용입니다.");

        mockMvc.perform(post("/api/interviews/{sessionId}/answer", session.getId())
                        .header("Authorization", "Bearer " + validAccessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andDo(print())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"))
                .andDo(document("interview-submit-answer-already-completed",
                        resource(SubmitAnswerDocs.alreadyCompleted())
                ));
    }

    @Test
    @DisplayName("POST /api/interviews/:sessionId/answer - 실패: 세션 없음 (404)")
    void submitAnswer_sessionNotFound() throws Exception {
        SubmitAnswerRequest request = new SubmitAnswerRequest("답변 내용입니다.");

        mockMvc.perform(post("/api/interviews/{sessionId}/answer", java.util.UUID.randomUUID())
                        .header("Authorization", "Bearer " + validAccessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andDo(print())
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error").value("RESOURCE_NOT_FOUND"))
                .andDo(document("interview-submit-answer-not-found",
                        resource(SubmitAnswerDocs.notFound())
                ));
    }

    @Test
    @DisplayName("GET /api/interviews - 성공: 세션 목록 조회")
    void getSessions_success() throws Exception {
        InterviewSession session = interviewSessionRepository.save(InterviewSession.create(
                testUser, "백엔드 개발자", ExperienceLevel.JUNIOR,
                InterviewType.TECHNICAL, 3, "1년차 백엔드 개발자입니다."));
        session.abandon();
        interviewSessionRepository.flush();

        mockMvc.perform(get("/api/interviews")
                        .header("Authorization", "Bearer " + validAccessToken))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.sessions").isArray())
                .andExpect(jsonPath("$.data.sessions.length()").value(1))
                .andExpect(jsonPath("$.data.sessions[0].endedAt").isString())
                .andExpect(jsonPath("$.data.sessions[0].durationSeconds").isNumber())
                .andExpect(jsonPath("$.data.sessions[0].feedbackStatus").doesNotExist())
                .andExpect(jsonPath("$.data.pagination.page").value(1))
                .andExpect(jsonPath("$.data.pagination.totalElements").value(1))
                .andDo(document("interview-get-sessions",
                        resource(SessionListDocs.success())
                ));
    }

    @Test
    @DisplayName("GET /api/interviews - 성공: status 필터 적용")
    void getSessions_withStatusFilter() throws Exception {
        interviewSessionRepository.save(InterviewSession.create(testUser, "백엔드 개발자", ExperienceLevel.JUNIOR, InterviewType.TECHNICAL, 3, "1년차 백엔드 개발자입니다."));
        saveSession(3, 3, InterviewSessionStatus.COMPLETED);

        mockMvc.perform(get("/api/interviews")
                        .param("status", "COMPLETED")
                        .header("Authorization", "Bearer " + validAccessToken))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.sessions.length()").value(1))
                .andExpect(jsonPath("$.data.sessions[0].status").value("COMPLETED"))
                .andDo(document("interview-get-sessions-filtered",
                        resource(SessionListDocs.filtered())
                ));
    }

    private InterviewSession saveSession(int currentQuestionNumber, int totalQuestions, InterviewSessionStatus status) {
        return saveSession(currentQuestionNumber, totalQuestions, status, null);
    }

    private InterviewSession saveSession(int currentQuestionNumber, int totalQuestions, InterviewSessionStatus status, FeedbackStatus feedbackStatus) {
        return interviewSessionRepository.save(
                InterviewSession.builder()
                        .user(testUser)
                        .position("백엔드 개발자")
                        .experienceLevel(ExperienceLevel.JUNIOR)
                        .interviewType(InterviewType.TECHNICAL)
                        .totalQuestions(totalQuestions)
                        .selfIntroduction("1년차 백엔드 개발자로 이커머스 서비스를 개발했습니다.")
                        .currentQuestionNumber(currentQuestionNumber)
                        .status(status)
                        .feedbackStatus(feedbackStatus)
                        .build()
        );
    }

    private InterviewSession saveOverviewSession(Instant endedAt, int overallScore, PlanTier generatedTier) {
        InterviewSession session = interviewSessionRepository.save(InterviewSession.builder()
                .user(testUser)
                .position("백엔드 개발자")
                .experienceLevel(ExperienceLevel.JUNIOR)
                .interviewType(InterviewType.TECHNICAL)
                .totalQuestions(3)
                .selfIntroduction("1년차 백엔드 개발자로 이커머스 서비스를 개발했습니다.")
                .currentQuestionNumber(3)
                .status(InterviewSessionStatus.COMPLETED)
                .feedbackStatus(FeedbackStatus.COMPLETED)
                .endedAt(endedAt)
                .build());
        InterviewFeedbackResult fixture = feedbackResult(generatedTier);
        InterviewFeedbackResult result = new InterviewFeedbackResult(
                overallScore,
                fixture.coachBrief(),
                fixture.scores(),
                fixture.strengths(),
                fixture.improvements(),
                fixture.nextPracticePoint()
        );
        interviewFeedbackRepository.save(InterviewFeedback.create(session, result, generatedTier));
        return session;
    }

    private void activateProSubscription() {
        currentSubscription.cancel();
        subscriptionRepository.flush();
        SubscriptionProduct proProduct = subscriptionProductRepository.save(SubscriptionProduct.builder()
                .name("Pro")
                .planTier(PlanTier.PRO)
                .isActive(true)
                .maxQuestions(10)
                .weeklyInterviewLimit(10)
                .weeklyImprovementPracticeLimit(4)
                .build());
        SubscriptionPlan proPlan = subscriptionPlanRepository.save(SubscriptionPlan.builder()
                .product(proProduct)
                .price(BigDecimal.valueOf(9900))
                .currency(Currency.KRW)
                .billingCycle(BillingCycle.MONTHLY)
                .build());
        Subscription proSubscription = Subscription.create(testUser.getId(), proPlan);
        proSubscription.activate();
        subscriptionRepository.saveAndFlush(proSubscription);
    }

    @Test
    @DisplayName("GET /api/interviews/:sessionId - 성공: 세션 상세 조회")
    void getSessionDetail_success() throws Exception {
        InterviewSession session = saveSession(1, 3, InterviewSessionStatus.IN_PROGRESS);
        interviewMessageRepository.save(InterviewMessage.createInterviewerMessage(session, "자기소개를 해주세요.", 1));
        interviewMessageRepository.save(InterviewMessage.createUserMessage(session, "안녕하세요, 저는 백엔드 개발자입니다.", 1));
        session.abandon();
        interviewSessionRepository.flush();

        mockMvc.perform(get("/api/interviews/{sessionId}", session.getId())
                        .header("Authorization", "Bearer " + validAccessToken))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.sessionId").value(session.getId().toString()))
                .andExpect(jsonPath("$.data.status").value("ABANDONED"))
                .andExpect(jsonPath("$.data.endedAt").isString())
                .andExpect(jsonPath("$.data.durationSeconds").isNumber())
                .andExpect(jsonPath("$.data.messages.length()").value(2))
                .andExpect(jsonPath("$.data.messages[0].role").value("INTERVIEWER"))
                .andExpect(jsonPath("$.data.messages[1].role").value("USER"))
                .andDo(document("interview-get-session-detail",
                        resource(SessionDetailDocs.success())
                ));
    }

    @Test
    @DisplayName("GET /api/interviews/:sessionId - 실패: 세션 없음 (404)")
    void getSessionDetail_notFound() throws Exception {
        mockMvc.perform(get("/api/interviews/{sessionId}", java.util.UUID.randomUUID())
                        .header("Authorization", "Bearer " + validAccessToken))
                .andDo(print())
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error").value("RESOURCE_NOT_FOUND"))
                .andDo(document("interview-get-session-detail-not-found",
                        resource(SessionDetailDocs.notFound())
                ));
    }

    @Test
    @DisplayName("POST /api/interviews - 실패: 인증 없음 (401)")
    void createSession_Unauthorized() throws Exception {
        CreateInterviewRequest request = new CreateInterviewRequest(
                "백엔드 개발자",
                ExperienceLevel.JUNIOR,
                InterviewType.TECHNICAL,
                3,
                "1년차 백엔드 개발자입니다."
        );

        mockMvc.perform(post("/api/interviews")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andDo(print())
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andDo(document("interview-create-unauthorized",
                        resource(CreateInterviewDocs.unauthorized())
                ));
    }

    @Test
    @DisplayName("GET /api/interviews/quota - 성공: 현재 주간 이용기간 쿼터 조회")
    void getQuota_returnsWeeklyUsagePeriod() throws Exception {
        mockMvc.perform(get("/api/interviews/quota")
                        .header("Authorization", "Bearer " + validAccessToken))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.periodStart").isString())
                .andExpect(jsonPath("$.data.nextResetAt").isString())
                .andExpect(jsonPath("$.data.maxQuestions").value(3))
                .andExpect(jsonPath("$.data.interview.limit").value(1))
                .andExpect(jsonPath("$.data.interview.used").value(0))
                .andExpect(jsonPath("$.data.interview.remaining").value(1))
                .andExpect(jsonPath("$.data.improvementPractice.limit").value(0))
                .andExpect(jsonPath("$.data.improvementPractice.used").value(0))
                .andExpect(jsonPath("$.data.improvementPractice.remaining").value(0))
                .andDo(document("interview-get-quota",
                        resource(QuotaDocs.success())
                ));
    }

    @Test
    @DisplayName("GET /api/interviews/quota - 성공: 주간 세션 사용 후 남은 쿼터 조회")
    void getQuota_afterUsed() throws Exception {
        CreateInterviewRequest request = new CreateInterviewRequest(
                "백엔드 개발자", ExperienceLevel.JUNIOR, InterviewType.TECHNICAL, 3,
                "1년차 백엔드 개발자입니다.");
        mockMvc.perform(post("/api/interviews")
                        .header("Authorization", "Bearer " + validAccessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/interviews/quota")
                        .header("Authorization", "Bearer " + validAccessToken))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.interview.used").value(1))
                .andExpect(jsonPath("$.data.interview.remaining").value(0));
    }

    @Test
    @DisplayName("GET /api/interviews/:sessionId/feedback - 성공: 피드백 조회")
    void getFeedback_success() throws Exception {
        InterviewSession session = saveSession(3, 3, InterviewSessionStatus.COMPLETED, FeedbackStatus.COMPLETED);
        interviewFeedbackRepository.save(InterviewFeedback.create(
                session, feedbackResult(PlanTier.BASIC), PlanTier.BASIC));

        mockMvc.perform(get("/api/interviews/{sessionId}/feedback", session.getId())
                        .header("Authorization", "Bearer " + validAccessToken))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.feedbackStatus").value("COMPLETED"))
                .andExpect(jsonPath("$.data.feedback.overallScore").value(80))
                .andExpect(jsonPath("$.data.feedback.generatedTier").value("BASIC"))
                .andExpect(jsonPath("$.data.feedback.coachBrief.summary").value("핵심 요약"))
                .andExpect(jsonPath("$.data.feedback.coachBrief.keyStrength").value("핵심 강점"))
                .andExpect(jsonPath("$.data.feedback.scores.structure").value(78))
                .andExpect(jsonPath("$.data.feedback.strengths.length()").value(3))
                .andExpect(jsonPath("$.data.feedback.improvements[0].title").value("개선점 1"))
                .andExpect(jsonPath("$.data.feedback.improvements[0].detail").value("개선 상세 1"))
                .andExpect(jsonPath("$.data.feedback.improvements[0].practiceAvailable").value(false))
                .andExpect(jsonPath("$.data.feedback.expertFeedbacks").doesNotExist())
                .andExpect(jsonPath("$.data.feedback.detailedAnalysis").doesNotExist())
                .andDo(document("interview-get-feedback",
                        resource(FeedbackDocs.success())
                ));
    }

    @Test
    @DisplayName("과거 Free 피드백의 4축 점수는 이후 유료 구독 이력이 있으면 다운그레이드 후에도 공개한다")
    void getFeedback_freeGeneratedAfterPaidHistory_exposesScoresPermanently() throws Exception {
        InterviewSession session = saveSession(3, 3, InterviewSessionStatus.COMPLETED, FeedbackStatus.COMPLETED);
        interviewFeedbackRepository.saveAndFlush(InterviewFeedback.create(
                session, feedbackResult(PlanTier.FREE), PlanTier.FREE));

        currentSubscription.cancel();
        subscriptionRepository.flush();
        SubscriptionProduct basicProduct = subscriptionProductRepository.save(SubscriptionProduct.builder()
                .name("Basic")
                .planTier(PlanTier.BASIC)
                .isActive(true)
                .maxQuestions(5)
                .weeklyInterviewLimit(4)
                .weeklyImprovementPracticeLimit(0)
                .build());
        SubscriptionPlan basicPlan = subscriptionPlanRepository.save(SubscriptionPlan.builder()
                .product(basicProduct)
                .price(BigDecimal.valueOf(5900))
                .currency(Currency.KRW)
                .billingCycle(BillingCycle.MONTHLY)
                .build());
        Subscription paid = Subscription.create(testUser.getId(), basicPlan);
        paid.activate();
        subscriptionRepository.saveAndFlush(paid);
        paid.cancel();
        subscriptionRepository.flush();
        Subscription newFree = Subscription.create(testUser.getId(), currentSubscription.getSubscriptionPlan());
        newFree.activate();
        subscriptionRepository.saveAndFlush(newFree);

        mockMvc.perform(get("/api/interviews/{sessionId}/feedback", session.getId())
                        .header("Authorization", "Bearer " + validAccessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.feedback.scores.structure").value(78))
                .andExpect(jsonPath("$.data.feedback.strengths").isEmpty())
                .andExpect(jsonPath("$.data.feedback.improvements[0].detail").isEmpty());
    }

    @Test
    @DisplayName("GET /api/interviews/:sessionId/feedback - 실패: 피드백 없음 (404)")
    void getFeedback_notFound() throws Exception {
        InterviewSession session = saveSession(1, 3, InterviewSessionStatus.IN_PROGRESS);

        mockMvc.perform(get("/api/interviews/{sessionId}/feedback", session.getId())
                        .header("Authorization", "Bearer " + validAccessToken))
                .andDo(print())
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error").value("RESOURCE_NOT_FOUND"))
                .andDo(document("interview-get-feedback-not-found",
                        resource(FeedbackDocs.notFound())
                ));
    }

    @Test
    @DisplayName("GET /api/interviews/:sessionId/feedback/events - 실패: 세션 없음이면 SSE 연결을 등록하지 않음")
    void feedbackEvents_sessionNotFound_doesNotConnectEmitter() throws Exception {
        assertThatThrownBy(() -> mockMvc.perform(get("/api/interviews/{sessionId}/feedback/events", java.util.UUID.randomUUID())
                        .header("Authorization", "Bearer " + validAccessToken)
                        .accept(MediaType.TEXT_EVENT_STREAM)))
                .hasRootCauseInstanceOf(BusinessException.class);

        verify(feedbackSseManager, never()).connect(any(), anyLong());
    }

    @Test
    @DisplayName("GET /api/interviews/:sessionId/feedback/events - 성공: 완료 상태면 fallback 이벤트 전송 후 종료")
    void feedbackEvents_completed_sendsFallbackAndCompletes() throws Exception {
        InterviewSession session = saveSession(3, 3, InterviewSessionStatus.COMPLETED, FeedbackStatus.COMPLETED);
        SseEmitter emitter = new SseEmitter(60_000L);
        given(feedbackSseManager.connect(eq(session.getId()), eq(60_000L))).willReturn(emitter);

        mockMvc.perform(get("/api/interviews/{sessionId}/feedback/events", session.getId())
                        .header("Authorization", "Bearer " + validAccessToken)
                        .accept(MediaType.TEXT_EVENT_STREAM))
                .andExpect(request().asyncStarted())
                .andReturn();

        verify(feedbackSseManager).connect(session.getId(), 60_000L);
        verify(feedbackSseManager).send(session.getId(), FeedbackStatus.COMPLETED, null);
        verify(feedbackSseManager).complete(session.getId());
    }

    @Test
    @DisplayName("POST /api/interviews/:sessionId/feedback/retry - 성공: 실패한 피드백 재시도")
    void retryFeedback_success() throws Exception {
        InterviewSession session = saveSession(3, 3, InterviewSessionStatus.FEEDBACK_PENDING, FeedbackStatus.FAILED);

        mockMvc.perform(post("/api/interviews/{sessionId}/feedback/retry", session.getId())
                        .header("Authorization", "Bearer " + validAccessToken))
                .andDo(print())
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.feedbackStatus").value("PENDING"))
                .andExpect(jsonPath("$.data.feedback").doesNotExist())
                .andDo(document("interview-retry-feedback",
                        resource(RetryFeedbackDocs.success())
                ));
    }

    @Test
    @DisplayName("POST /api/interviews/:sessionId/feedback/retry - 실패: 실패 상태가 아닌 피드백 (400)")
    void retryFeedback_notFailed() throws Exception {
        InterviewSession session = saveSession(3, 3, InterviewSessionStatus.FEEDBACK_PENDING, FeedbackStatus.PENDING);

        mockMvc.perform(post("/api/interviews/{sessionId}/feedback/retry", session.getId())
                        .header("Authorization", "Bearer " + validAccessToken))
                .andDo(print())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"))
                .andDo(document("interview-retry-feedback-invalid-status",
                        resource(RetryFeedbackDocs.invalidStatus())
                ));
    }

    @Test
    @DisplayName("POST /api/interviews/:sessionId/feedback/retry - 실패: 세션 없음 (404)")
    void retryFeedback_sessionNotFound() throws Exception {
        mockMvc.perform(post("/api/interviews/{sessionId}/feedback/retry", java.util.UUID.randomUUID())
                        .header("Authorization", "Bearer " + validAccessToken))
                .andDo(print())
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error").value("RESOURCE_NOT_FOUND"))
                .andDo(document("interview-retry-feedback-not-found",
                        resource(RetryFeedbackDocs.notFound())
                ));
    }

    @Test
    @DisplayName("PATCH /api/interviews/:sessionId/abandon - 성공: 세션 포기")
    void abandonSession_success() throws Exception {
        InterviewSession session = saveSession(1, 3, InterviewSessionStatus.IN_PROGRESS);

        mockMvc.perform(patch("/api/interviews/{sessionId}/abandon", session.getId())
                        .header("Authorization", "Bearer " + validAccessToken))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").doesNotExist())
                .andDo(document("interview-abandon-session",
                        resource(AbandonSessionDocs.success())
                ));
    }

    @Test
    @DisplayName("PATCH /api/interviews/:sessionId/abandon - 실패: 이미 완료된 세션 (400)")
    void abandonSession_alreadyCompleted() throws Exception {
        InterviewSession session = saveSession(3, 3, InterviewSessionStatus.COMPLETED);

        mockMvc.perform(patch("/api/interviews/{sessionId}/abandon", session.getId())
                        .header("Authorization", "Bearer " + validAccessToken))
                .andDo(print())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"))
                .andDo(document("interview-abandon-session-already-completed",
                        resource(AbandonSessionDocs.alreadyCompleted())
                ));
    }

    @Test
    @DisplayName("GET /api/interviews/:sessionId/questions/stream - 성공: SSE 스트리밍 시작")
    void streamQuestion_success() throws Exception {
        InterviewSession session = saveSession(1, 3, InterviewSessionStatus.IN_PROGRESS);

        org.springframework.test.web.servlet.MvcResult mvcResult = mockMvc.perform(
                        get("/api/interviews/{sessionId}/questions/stream", session.getId())
                                .header("Authorization", "Bearer " + validAccessToken)
                                .accept(MediaType.TEXT_EVENT_STREAM))
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(asyncDispatch(mvcResult))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_EVENT_STREAM))
                .andDo(document("interview-stream-question",
                        resource(StreamQuestionDocs.success())
                ));
    }

    @Test
    @DisplayName("GET /api/interviews/:sessionId/questions/stream - 실패: 세션 없음 (404)")
    void streamQuestion_sessionNotFound() throws Exception {
        mockMvc.perform(
                        get("/api/interviews/{sessionId}/questions/stream", java.util.UUID.randomUUID())
                                .header("Authorization", "Bearer " + validAccessToken))
                .andDo(print())
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error").value("RESOURCE_NOT_FOUND"))
                .andDo(document("interview-stream-question-not-found",
                        resource(StreamQuestionDocs.notFound())
                ));
    }
}
