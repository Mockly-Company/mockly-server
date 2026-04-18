package app.mockly.domain.interview.controller;

import app.mockly.domain.auth.entity.OAuth2Provider;
import app.mockly.domain.auth.entity.User;
import app.mockly.domain.auth.repository.UserRepository;
import app.mockly.domain.auth.service.JwtService;
import app.mockly.domain.auth.service.TokenBlacklistService;
import app.mockly.domain.interview.controller.docs.AbandonSessionDocs;
import app.mockly.domain.interview.controller.docs.CreateInterviewDocs;
import app.mockly.domain.interview.controller.docs.SessionDetailDocs;
import app.mockly.domain.interview.controller.docs.SessionListDocs;
import app.mockly.domain.interview.controller.docs.StreamQuestionDocs;
import app.mockly.domain.interview.controller.docs.SubmitAnswerDocs;
import app.mockly.domain.interview.dto.InterviewFeedbackResult;
import app.mockly.domain.interview.dto.request.CreateInterviewRequest;
import app.mockly.domain.interview.dto.request.SubmitAnswerRequest;
import app.mockly.domain.interview.entity.ExperienceLevel;
import app.mockly.domain.interview.entity.InterviewMessage;
import app.mockly.domain.interview.entity.InterviewQuota;
import app.mockly.domain.interview.entity.InterviewSession;
import app.mockly.domain.interview.entity.InterviewSessionStatus;
import app.mockly.domain.interview.entity.InterviewType;
import reactor.core.publisher.Flux;
import app.mockly.domain.interview.repository.InterviewMessageRepository;
import app.mockly.domain.interview.repository.InterviewQuotaRepository;
import app.mockly.domain.interview.repository.InterviewSessionRepository;
import app.mockly.domain.interview.service.InterviewAiService;
import app.mockly.domain.product.entity.PlanTier;
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
import org.springframework.transaction.annotation.Transactional;

import static com.epages.restdocs.apispec.MockMvcRestDocumentationWrapper.document;
import static com.epages.restdocs.apispec.ResourceDocumentation.resource;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
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

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private InterviewQuotaRepository interviewQuotaRepository;

    @Autowired
    private InterviewSessionRepository interviewSessionRepository;

    @Autowired
    private InterviewMessageRepository interviewMessageRepository;

    private User testUser;
    private String validAccessToken;

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
                .willReturn(new InterviewFeedbackResult(
                        75,
                        List.of(new InterviewFeedbackResult.ExpertFeedback("기술 면접관", 75, "전반적으로 기술적 이해도가 적절합니다.")),
                        "논리적인 답변 구조를 보여주었습니다.",
                        "더 구체적인 실무 경험을 제시하면 좋겠습니다.",
                        null
                ));

        // InterviewQuota 초기 데이터 (FREE 플랜: 일일 1회, 최대 3문항)
        interviewQuotaRepository.save(InterviewQuota.builder()
                .planTier(PlanTier.FREE)
                .dailyLimit(1)
                .maxQuestionsPerSession(3)
                .build());
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
    @DisplayName("POST /api/interviews - 실패: 일일 쿼터 초과 (429)")
    void createSession_QuotaExceeded() throws Exception {
        // 오늘 세션 1개 미리 생성 (FREE 플랜 한도: 1회)
        interviewSessionRepository.save(InterviewSession.create(
                testUser, "백엔드 개발자", ExperienceLevel.JUNIOR, InterviewType.TECHNICAL, 3,
                "1년차 백엔드 개발자입니다."
        ));

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
                .andExpect(jsonPath("$.data.isCompleted").value(false))
                .andExpect(jsonPath("$.data.currentQuestionNumber").value(2))
                .andDo(document("interview-submit-answer",
                        resource(SubmitAnswerDocs.success())
                ));
    }

    @Test
    @DisplayName("POST /api/interviews/:sessionId/answer - 성공: 마지막 답변, 피드백 반환")
    void submitAnswer_completed() throws Exception {
        InterviewSession session = saveSession(3, 3, InterviewSessionStatus.IN_PROGRESS);
        SubmitAnswerRequest request = new SubmitAnswerRequest("마지막 질문에 대한 답변입니다.");

        mockMvc.perform(post("/api/interviews/{sessionId}/answer", session.getId())
                        .header("Authorization", "Bearer " + validAccessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.isCompleted").value(true))

                .andExpect(jsonPath("$.data.feedback.overallScore").value(75))
                .andExpect(jsonPath("$.data.feedback.expertFeedbacks[0].expertRole").value("기술 면접관"))
                .andExpect(jsonPath("$.data.feedback.expertFeedbacks[0].score").value(75))
                .andDo(document("interview-submit-answer-completed",
                        resource(SubmitAnswerDocs.successCompleted())
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
        interviewSessionRepository.save(InterviewSession.create(testUser, "백엔드 개발자", ExperienceLevel.JUNIOR, InterviewType.TECHNICAL, 3, "1년차 백엔드 개발자입니다."));
        interviewSessionRepository.save(InterviewSession.create(testUser, "프론트엔드 개발자", ExperienceLevel.MID, InterviewType.BEHAVIORAL, 3, "3년차 프론트엔드 개발자입니다."));

        mockMvc.perform(get("/api/interviews")
                        .header("Authorization", "Bearer " + validAccessToken))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.sessions").isArray())
                .andExpect(jsonPath("$.data.sessions.length()").value(2))
                .andExpect(jsonPath("$.data.pagination.page").value(1))
                .andExpect(jsonPath("$.data.pagination.totalElements").value(2))
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
                        .build()
        );
    }

    @Test
    @DisplayName("GET /api/interviews/:sessionId - 성공: 세션 상세 조회")
    void getSessionDetail_success() throws Exception {
        InterviewSession session = saveSession(1, 3, InterviewSessionStatus.IN_PROGRESS);
        interviewMessageRepository.save(InterviewMessage.createInterviewerMessage(session, "자기소개를 해주세요.", 1));
        interviewMessageRepository.save(InterviewMessage.createUserMessage(session, "안녕하세요, 저는 백엔드 개발자입니다.", 1));

        mockMvc.perform(get("/api/interviews/{sessionId}", session.getId())
                        .header("Authorization", "Bearer " + validAccessToken))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.sessionId").value(session.getId().toString()))
                .andExpect(jsonPath("$.data.status").value("IN_PROGRESS"))
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
