package app.mockly.domain.interview.controller;

import app.mockly.domain.auth.entity.OAuth2Provider;
import app.mockly.domain.auth.entity.User;
import app.mockly.domain.auth.repository.UserRepository;
import app.mockly.domain.auth.service.JwtService;
import app.mockly.domain.auth.service.TokenBlacklistService;
import app.mockly.domain.interview.FeedbackTestFixtures;
import app.mockly.domain.interview.controller.docs.ImprovementPracticeDocs;
import app.mockly.domain.interview.dto.ImprovementPracticeEvaluationResult;
import app.mockly.domain.interview.dto.request.CreateImprovementPracticeRequest;
import app.mockly.domain.interview.dto.request.SubmitImprovementPracticeAnswerRequest;
import app.mockly.domain.interview.entity.*;
import app.mockly.domain.interview.repository.*;
import app.mockly.domain.interview.service.InterviewAiService;
import app.mockly.domain.interview.service.WeeklyQuotaService;
import app.mockly.domain.product.entity.*;
import app.mockly.domain.product.repository.SubscriptionPlanRepository;
import app.mockly.domain.product.repository.SubscriptionProductRepository;
import app.mockly.domain.product.repository.SubscriptionRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

import static com.epages.restdocs.apispec.MockMvcRestDocumentationWrapper.document;
import static com.epages.restdocs.apispec.ResourceDocumentation.resource;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.get;
import static org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@AutoConfigureRestDocs
@Transactional
class ImprovementPracticeControllerTest {

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

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private SubscriptionProductRepository subscriptionProductRepository;

    @Autowired
    private SubscriptionPlanRepository subscriptionPlanRepository;

    @Autowired
    private SubscriptionRepository subscriptionRepository;

    @Autowired
    private InterviewSessionRepository interviewSessionRepository;

    @Autowired
    private InterviewMessageRepository interviewMessageRepository;

    @Autowired
    private InterviewFeedbackRepository interviewFeedbackRepository;

    @Autowired
    private ImprovementPracticeRepository improvementPracticeRepository;

    @Autowired
    private QuotaUsageRepository quotaUsageRepository;

    @Autowired
    private WeeklyQuotaService weeklyQuotaService;

    private User proUser;
    private String proAccessToken;
    private User freeUser;
    private String freeAccessToken;
    private SubscriptionPlan proPlan;
    private SubscriptionPlan freePlan;
    private InterviewSession proSession;
    private FeedbackImprovement targetImprovement;

    @BeforeEach
    void setUp() {
        given(tokenBlacklistService.isBlacklisted(anyString())).willReturn(false);
        given(interviewAiService.evaluateImprovementPractice(any())).willReturn(
                new ImprovementPracticeEvaluationResult(
                        88,
                        "장애 원인과 해결 수치가 명확해져 설득력이 크게 향상되었습니다.",
                        "기존 답변에서 누락되었던 커넥션 풀 병목 수치와 p95 응답시간 개선 결과를 구체적으로 제시하여 실무 역량이 잘 드러납니다.",
                        "HikariCP 풀 사이즈 조정과 슬로우 쿼리 인덱스 개선으로 p95 지연을 820ms에서 140ms로 단축했습니다."
                )
        );

        SubscriptionProduct freeProduct = subscriptionProductRepository.save(SubscriptionProduct.builder()
                .name("Free")
                .planTier(PlanTier.FREE)
                .isActive(true)
                .maxQuestions(3)
                .weeklyInterviewLimit(1)
                .weeklyImprovementPracticeLimit(0)
                .build());
        freePlan = subscriptionPlanRepository.save(SubscriptionPlan.builder()
                .product(freeProduct)
                .price(BigDecimal.ZERO)
                .currency(Currency.KRW)
                .billingCycle(BillingCycle.MONTHLY)
                .build());

        SubscriptionProduct proProduct = subscriptionProductRepository.save(SubscriptionProduct.builder()
                .name("Pro")
                .planTier(PlanTier.PRO)
                .isActive(true)
                .maxQuestions(20)
                .weeklyInterviewLimit(4)
                .weeklyImprovementPracticeLimit(4)
                .build());
        proPlan = subscriptionPlanRepository.save(SubscriptionPlan.builder()
                .product(proProduct)
                .price(new BigDecimal("22900"))
                .currency(Currency.KRW)
                .billingCycle(BillingCycle.MONTHLY)
                .build());

        proUser = userRepository.save(User.builder()
                .email("pro@mockly.app")
                .name("프로지원자")
                .provider(OAuth2Provider.GOOGLE)
                .providerId("google-pro-user")
                .build());
        proAccessToken = jwtService.generateAccessToken(proUser.getId());
        Subscription proSub = Subscription.create(proUser.getId(), proPlan);
        proSub.activate();
        subscriptionRepository.save(proSub);

        freeUser = userRepository.save(User.builder()
                .email("free@mockly.app")
                .name("무료지원자")
                .provider(OAuth2Provider.GOOGLE)
                .providerId("google-free-user")
                .build());
        freeAccessToken = jwtService.generateAccessToken(freeUser.getId());
        Subscription freeSub = Subscription.create(freeUser.getId(), freePlan);
        freeSub.activate();
        subscriptionRepository.save(freeSub);

        proSession = interviewSessionRepository.save(InterviewSession.builder()
                .user(proUser)
                .position("백엔드 개발자")
                .selfIntroduction("Spring Boot와 PostgreSQL 기반 백엔드 개발자입니다.")
                .experienceLevel(ExperienceLevel.JUNIOR)
                .interviewType(InterviewType.TECHNICAL)
                .totalQuestions(3)
                .currentQuestionNumber(3)
                .status(InterviewSessionStatus.COMPLETED)
                .feedbackStatus(FeedbackStatus.COMPLETED)
                .completedAt(Instant.now())
                .build());

        interviewMessageRepository.save(InterviewMessage.createInterviewerMessage(
                proSession,
                "트래픽 급증 상황에서 DB 병목을 해결한 경험을 구체적인 지표와 함께 설명해 주세요.",
                1
        ));
        interviewMessageRepository.save(InterviewMessage.createUserMessage(
                proSession,
                "쿼리가 느려서 인덱스를 걸어서 해결한 적이 있습니다.",
                1
        ));

        InterviewFeedback feedback = InterviewFeedback.create(
                proSession,
                FeedbackTestFixtures.feedbackResult(PlanTier.PRO),
                PlanTier.PRO
        );
        feedback = interviewFeedbackRepository.saveAndFlush(feedback);
        targetImprovement = feedback.getImprovements().getFirst();
    }

    @Test
    @DisplayName("POST /api/interviews/improvement-practices - 성공: Pro 유저 PENDING 연습 생성 시 쿼터는 아직 차감되지 않음 (201)")
    void createPractice_success() throws Exception {
        CreateImprovementPracticeRequest request = new CreateImprovementPracticeRequest(targetImprovement.getId());

        mockMvc.perform(post("/api/interviews/improvement-practices")
                        .header("Authorization", "Bearer " + proAccessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").isNumber())
                .andExpect(jsonPath("$.data.status").value("PENDING"))
                .andDo(document("improvement-practice-create",
                        resource(ImprovementPracticeDocs.createSuccess())
                ));

        assertThat(weeklyQuotaService.getQuota(proUser).improvementPractice().used()).isEqualTo(0);
    }

    @Test
    @DisplayName("POST /api/interviews/improvement-practices - 실패: Free/Basic 유저는 403 FORBIDDEN")
    void createPractice_forbiddenForNonProUser() throws Exception {
        InterviewSession freeSession = interviewSessionRepository.save(InterviewSession.builder()
                .user(freeUser)
                .position("백엔드 개발자")
                .selfIntroduction("신입 백엔드 지원자입니다.")
                .experienceLevel(ExperienceLevel.JUNIOR)
                .interviewType(InterviewType.TECHNICAL)
                .totalQuestions(3)
                .currentQuestionNumber(3)
                .status(InterviewSessionStatus.COMPLETED)
                .feedbackStatus(FeedbackStatus.COMPLETED)
                .completedAt(Instant.now())
                .build());
        InterviewFeedback freeFeedback = interviewFeedbackRepository.saveAndFlush(
                InterviewFeedback.create(freeSession, FeedbackTestFixtures.feedbackResult(PlanTier.PRO), PlanTier.PRO)
        );

        CreateImprovementPracticeRequest request = new CreateImprovementPracticeRequest(
                freeFeedback.getImprovements().getFirst().getId()
        );

        mockMvc.perform(post("/api/interviews/improvement-practices")
                        .header("Authorization", "Bearer " + freeAccessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error").value("FORBIDDEN"))
                .andDo(document("improvement-practice-create-forbidden",
                        resource(ImprovementPracticeDocs.createForbidden())
                ));
    }

    @Test
    @DisplayName("POST /api/interviews/improvement-practices - 실패: 타인의 개선 포인트로 요청 시 403 FORBIDDEN")
    void createPractice_forbiddenForOtherUsersImprovement() throws Exception {
        User otherProUser = userRepository.save(User.builder()
                .email("other-pro@mockly.app")
                .name("다른프로")
                .provider(OAuth2Provider.GOOGLE)
                .providerId("google-other-pro")
                .build());
        Subscription otherSub = Subscription.create(otherProUser.getId(), proPlan);
        otherSub.activate();
        subscriptionRepository.save(otherSub);
        String otherToken = jwtService.generateAccessToken(otherProUser.getId());

        CreateImprovementPracticeRequest request = new CreateImprovementPracticeRequest(targetImprovement.getId());

        mockMvc.perform(post("/api/interviews/improvement-practices")
                        .header("Authorization", "Bearer " + otherToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("FORBIDDEN"));
    }

    @Test
    @DisplayName("POST /api/interviews/improvement-practices - 실패: 주간 개선 연습 쿼터(4회) 소진 시 429 QUOTA_EXCEEDED")
    void createPractice_quotaExceeded() throws Exception {
        exhaustImprovementPracticeQuota(proUser);

        CreateImprovementPracticeRequest request = new CreateImprovementPracticeRequest(targetImprovement.getId());

        mockMvc.perform(post("/api/interviews/improvement-practices")
                        .header("Authorization", "Bearer " + proAccessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error").value("QUOTA_EXCEEDED"))
                .andDo(document("improvement-practice-create-quota-exceeded",
                        resource(ImprovementPracticeDocs.createQuotaExceeded())
                ));
    }

    @Test
    @DisplayName("GET /api/interviews/improvement-practices/:practiceId - 성공: COMPLETED 연습 결과 조회")
    void getPractice_completed_success() throws Exception {
        ImprovementPractice practice = ImprovementPractice.createPending(
                proUser.getId(),
                proSession,
                targetImprovement,
                1,
                "트래픽 급증 상황에서 DB 병목을 해결한 경험을 설명해 주세요.",
                "쿼리가 느려서 인덱스를 걸어서 해결했습니다."
        );
        practice.submitAnswer("HikariCP 풀 사이즈 조정과 복합 인덱스 추가로 p95 응답시간을 820ms에서 140ms로 단축했습니다.");
        practice.complete(new ImprovementPracticeEvaluationResult(
                88,
                "장애 원인과 해결 수치가 명확해져 설득력이 크게 향상되었습니다.",
                "기존 답변에서 누락되었던 커넥션 풀 병목 수치와 p95 응답시간 개선 결과를 구체적으로 제시하여 실무 역량이 잘 드러납니다.",
                "HikariCP 풀 사이즈 조정과 복합 인덱스 추가로 p95 응답시간을 820ms에서 140ms로 단축했습니다."
        ));
        practice = improvementPracticeRepository.saveAndFlush(practice);

        mockMvc.perform(get("/api/interviews/improvement-practices/{practiceId}", practice.getId())
                        .header("Authorization", "Bearer " + proAccessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(practice.getId()))
                .andExpect(jsonPath("$.data.status").value("COMPLETED"))
                .andExpect(jsonPath("$.data.questionNumber").value(1))
                .andExpect(jsonPath("$.data.questionText").isNotEmpty())
                .andExpect(jsonPath("$.data.originalAnswer").isNotEmpty())
                .andExpect(jsonPath("$.data.submittedAnswer").isNotEmpty())
                .andExpect(jsonPath("$.data.result.score").value(88))
                .andExpect(jsonPath("$.data.result.summary").isNotEmpty())
                .andExpect(jsonPath("$.data.result.detail").isNotEmpty())
                .andExpect(jsonPath("$.data.result.quote").isNotEmpty())
                .andDo(document("improvement-practice-detail",
                        resource(ImprovementPracticeDocs.detailSuccess())
                ));
    }

    @Test
    @DisplayName("GET /api/interviews/improvement-practices/:practiceId - 성공: PENDING 상태면 result는 null")
    void getPractice_pending_returnsNullResult() throws Exception {
        ImprovementPractice practice = improvementPracticeRepository.saveAndFlush(
                ImprovementPractice.createPending(
                        proUser.getId(),
                        proSession,
                        targetImprovement,
                        1,
                        "질문 원문",
                        "기존 답변"
                )
        );

        mockMvc.perform(get("/api/interviews/improvement-practices/{practiceId}", practice.getId())
                        .header("Authorization", "Bearer " + proAccessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PENDING"))
                .andExpect(jsonPath("$.data.result").value(nullValue()));
    }

    @Test
    @DisplayName("GET /api/interviews/improvement-practices/:practiceId - 실패: 존재하지 않는 연습 ID (404)")
    void getPractice_notFound() throws Exception {
        mockMvc.perform(get("/api/interviews/improvement-practices/{practiceId}", 999999L)
                        .header("Authorization", "Bearer " + proAccessToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error").value("RESOURCE_NOT_FOUND"))
                .andDo(document("improvement-practice-detail-not-found",
                        resource(ImprovementPracticeDocs.detailNotFound())
                ));
    }

    @Test
    @DisplayName("POST /api/interviews/improvement-practices/:practiceId/answer - 성공: 주간 쿼터 1회 차감 및 GENERATING 전이")
    void submitAnswer_success() throws Exception {
        ImprovementPractice practice = improvementPracticeRepository.saveAndFlush(
                ImprovementPractice.createPending(
                        proUser.getId(),
                        proSession,
                        targetImprovement,
                        1,
                        "질문 원문",
                        "기존 답변"
                )
        );

        SubmitImprovementPracticeAnswerRequest request = new SubmitImprovementPracticeAnswerRequest(
                "슬로우 쿼리 실행계획을 분석해 복합 인덱스를 적용하고 p95 응답시간을 800ms에서 120ms로 줄였습니다."
        );

        mockMvc.perform(post("/api/interviews/improvement-practices/{practiceId}/answer", practice.getId())
                        .header("Authorization", "Bearer " + proAccessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(practice.getId()))
                .andExpect(jsonPath("$.data.status").value("GENERATING"))
                .andDo(document("improvement-practice-answer",
                        resource(ImprovementPracticeDocs.submitAnswerSuccess())
                ));

        assertThat(weeklyQuotaService.getQuota(proUser).improvementPractice().used()).isEqualTo(1);
    }

    @Test
    @DisplayName("POST /api/interviews/improvement-practices/:practiceId/answer - 실패: 주간 쿼터 소진 시 429 QUOTA_EXCEEDED")
    void submitAnswer_quotaExceeded() throws Exception {
        ImprovementPractice practice = improvementPracticeRepository.saveAndFlush(
                ImprovementPractice.createPending(
                        proUser.getId(),
                        proSession,
                        targetImprovement,
                        1,
                        "질문 원문",
                        "기존 답변"
                )
        );
        exhaustImprovementPracticeQuota(proUser);

        SubmitImprovementPracticeAnswerRequest request = new SubmitImprovementPracticeAnswerRequest("개선된 답변입니다.");

        mockMvc.perform(post("/api/interviews/improvement-practices/{practiceId}/answer", practice.getId())
                        .header("Authorization", "Bearer " + proAccessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error").value("QUOTA_EXCEEDED"))
                .andDo(document("improvement-practice-answer-quota-exceeded",
                        resource(ImprovementPracticeDocs.submitAnswerQuotaExceeded())
                ));
    }

    @Test
    @DisplayName("POST /api/interviews/improvement-practices/:practiceId/answer - 실패: 1500자 초과 시 400 VALIDATION_ERROR")
    void submitAnswer_exceedsMaxLength() throws Exception {
        ImprovementPractice practice = improvementPracticeRepository.saveAndFlush(
                ImprovementPractice.createPending(
                        proUser.getId(),
                        proSession,
                        targetImprovement,
                        1,
                        "질문 원문",
                        "기존 답변"
                )
        );

        String overLimitAnswer = "가".repeat(1501);

        mockMvc.perform(post("/api/interviews/improvement-practices/{practiceId}/answer", practice.getId())
                        .header("Authorization", "Bearer " + proAccessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("answer", overLimitAnswer))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"))
                .andDo(document("improvement-practice-answer-validation-error",
                        resource(ImprovementPracticeDocs.submitAnswerValidationError())
                ));
    }

    @Test
    @DisplayName("GET /api/interviews/improvement-practices/:practiceId/events - 성공: COMPLETED 상태 시 즉시 status 이벤트 전송")
    void streamEvents_completed_emitsImmediately() throws Exception {
        ImprovementPractice practice = ImprovementPractice.createPending(
                proUser.getId(),
                proSession,
                targetImprovement,
                1,
                "질문 원문",
                "기존 답변"
        );
        practice.submitAnswer("개선 답변");
        practice.complete(new ImprovementPracticeEvaluationResult(85, "요약", "상세", "인용"));
        practice = improvementPracticeRepository.saveAndFlush(practice);

        MvcResult mvcResult = mockMvc.perform(
                        get("/api/interviews/improvement-practices/{practiceId}/events", practice.getId())
                                .header("Authorization", "Bearer " + proAccessToken)
                                .accept(MediaType.TEXT_EVENT_STREAM))
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(asyncDispatch(mvcResult))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_EVENT_STREAM))
                .andExpect(content().string(containsString("event:status")))
                .andExpect(content().string(containsString("\"practiceStatus\":\"completed\"")))
                .andDo(document("improvement-practice-events",
                        resource(ImprovementPracticeDocs.eventsSuccess())
                ));
    }

    @Test
    @DisplayName("GET /api/interviews/improvement-practices/:practiceId/events - 성공: FAILED 상태 재연결 시 내부 failReason 대신 고정 안내 문구 전송")
    void streamEvents_failed_emitsSanitizedFailureMessageImmediately() throws Exception {
        ImprovementPractice practice = ImprovementPractice.createPending(
                proUser.getId(),
                proSession,
                targetImprovement,
                1,
                "질문 원문",
                "기존 답변"
        );
        practice.submitAnswer("개선 답변");
        practice.fail("OpenAI internal timeout detail");
        practice = improvementPracticeRepository.saveAndFlush(practice);

        MvcResult mvcResult = mockMvc.perform(
                        get("/api/interviews/improvement-practices/{practiceId}/events", practice.getId())
                                .header("Authorization", "Bearer " + proAccessToken)
                                .accept(MediaType.TEXT_EVENT_STREAM))
                .andExpect(request().asyncStarted())
                .andReturn();

        String sseBody = mockMvc.perform(asyncDispatch(mvcResult))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_EVENT_STREAM))
                .andReturn()
                .getResponse()
                .getContentAsString(java.nio.charset.StandardCharsets.UTF_8);

        assertThat(sseBody)
                .contains("event:status")
                .contains("\"practiceStatus\":\"failed\"")
                .contains("답변 개선 평가 생성에 실패했습니다.")
                .doesNotContain("OpenAI internal timeout detail");
    }

    @Test
    @DisplayName("GET /api/interviews/improvement-practices/:practiceId/events - 실패: 연습 내역 없음 (404)")
    void streamEvents_notFound() throws Exception {
        mockMvc.perform(get("/api/interviews/improvement-practices/{practiceId}/events", 999999L)
                        .header("Authorization", "Bearer " + proAccessToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error").value("RESOURCE_NOT_FOUND"))
                .andDo(document("improvement-practice-events-not-found",
                        resource(ImprovementPracticeDocs.eventsNotFound())
                ));
    }

    @Test
    @DisplayName("POST /api/interviews/improvement-practices/:practiceId/retry - 성공: FAILED 상태에서 추가 쿼터 차감 없이 재시도")
    void retryPractice_successWithoutExtraQuotaDeduction() throws Exception {
        ImprovementPractice practice = ImprovementPractice.createPending(
                proUser.getId(),
                proSession,
                targetImprovement,
                1,
                "질문 원문",
                "기존 답변"
        );
        practice.submitAnswer("개선 답변");
        practice.fail("AI_EVALUATION_FAILED");
        practice = improvementPracticeRepository.saveAndFlush(practice);

        int usedBefore = weeklyQuotaService.getQuota(proUser).improvementPractice().used();

        mockMvc.perform(post("/api/interviews/improvement-practices/{practiceId}/retry", practice.getId())
                        .header("Authorization", "Bearer " + proAccessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(practice.getId()))
                .andExpect(jsonPath("$.data.status").value("GENERATING"))
                .andExpect(jsonPath("$.data.result").value(nullValue()))
                .andDo(document("improvement-practice-retry",
                        resource(ImprovementPracticeDocs.retrySuccess())
                ));

        int usedAfter = weeklyQuotaService.getQuota(proUser).improvementPractice().used();
        assertThat(usedAfter).isEqualTo(usedBefore);
    }

    @Test
    @DisplayName("POST /api/interviews/improvement-practices/:practiceId/retry - 실패: Free로 다운그레이드된 유저는 403 FORBIDDEN")
    void retryPractice_forbiddenWhenDowngradedToFree() throws Exception {
        ImprovementPractice practice = ImprovementPractice.createPending(
                proUser.getId(),
                proSession,
                targetImprovement,
                1,
                "질문 원문",
                "기존 답변"
        );
        practice.submitAnswer("개선 답변");
        practice.fail("AI_EVALUATION_FAILED");
        practice = improvementPracticeRepository.saveAndFlush(practice);

        Subscription currentProSub = subscriptionRepository.findCurrentByUserId(proUser.getId()).orElseThrow();
        currentProSub.cancel();
        subscriptionRepository.saveAndFlush(currentProSub);

        Subscription downgradedFreeSub = Subscription.create(proUser.getId(), freePlan);
        downgradedFreeSub.activate();
        subscriptionRepository.saveAndFlush(downgradedFreeSub);

        mockMvc.perform(post("/api/interviews/improvement-practices/{practiceId}/retry", practice.getId())
                        .header("Authorization", "Bearer " + proAccessToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error").value("FORBIDDEN"));
    }

    @Test
    @DisplayName("POST /api/interviews/improvement-practices/:practiceId/retry - 실패: 수동 재시도 상한(3회) 초과 시 400 VALIDATION_ERROR")
    void retryPractice_exceedsMaxManualRetries() throws Exception {
        ImprovementPractice practice = ImprovementPractice.createPending(
                proUser.getId(),
                proSession,
                targetImprovement,
                1,
                "질문 원문",
                "기존 답변"
        );
        practice.submitAnswer("개선 답변");
        for (int i = 0; i < ImprovementPractice.MAX_MANUAL_RETRIES; i++) {
            practice.fail("AI_EVALUATION_FAILED");
            practice.markRetryGenerating();
        }
        practice.fail("AI_EVALUATION_FAILED");
        practice = improvementPracticeRepository.saveAndFlush(practice);

        mockMvc.perform(post("/api/interviews/improvement-practices/{practiceId}/retry", practice.getId())
                        .header("Authorization", "Bearer " + proAccessToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"));
    }

    @Test
    @DisplayName("POST /api/interviews/improvement-practices/:practiceId/retry - 실패: FAILED가 아닌 상태면 400 VALIDATION_ERROR")
    void retryPractice_invalidStatus() throws Exception {
        ImprovementPractice practice = improvementPracticeRepository.saveAndFlush(
                ImprovementPractice.createPending(
                        proUser.getId(),
                        proSession,
                        targetImprovement,
                        1,
                        "질문 원문",
                        "기존 답변"
                )
        );

        mockMvc.perform(post("/api/interviews/improvement-practices/{practiceId}/retry", practice.getId())
                        .header("Authorization", "Bearer " + proAccessToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"))
                .andDo(document("improvement-practice-retry-invalid-status",
                        resource(ImprovementPracticeDocs.retryInvalidStatus())
                ));
    }

    private void exhaustImprovementPracticeQuota(User user) {
        var context = weeklyQuotaService.calculateCurrentQuotaContext(user, Instant.now());
        for (int i = 0; i < 4; i++) {
            weeklyQuotaService.consume(user.getId(), context, QuotaType.IMPROVEMENT_PRACTICE);
        }
        quotaUsageRepository.flush();
    }
}
