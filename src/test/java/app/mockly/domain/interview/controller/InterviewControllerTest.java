package app.mockly.domain.interview.controller;

import app.mockly.domain.auth.entity.OAuth2Provider;
import app.mockly.domain.auth.entity.User;
import app.mockly.domain.auth.repository.UserRepository;
import app.mockly.domain.auth.service.JwtService;
import app.mockly.domain.auth.service.TokenBlacklistService;
import app.mockly.domain.interview.controller.docs.CreateInterviewDocs;
import app.mockly.domain.interview.dto.request.CreateInterviewRequest;
import app.mockly.domain.interview.entity.ExperienceLevel;
import app.mockly.domain.interview.entity.InterviewQuota;
import app.mockly.domain.interview.entity.InterviewSession;
import app.mockly.domain.interview.entity.InterviewType;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
        given(interviewAiService.generateFirstQuestion(anyString(), any(ExperienceLevel.class), any(InterviewType.class)))
                .willReturn("자기소개를 해주세요.");

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
                3
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
                .andExpect(jsonPath("$.data.currentQuestionNumber").value(1))
                .andExpect(jsonPath("$.data.status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.data.firstQuestion").value("자기소개를 해주세요."))
                .andDo(document("interview-create",
                        resource(CreateInterviewDocs.success())
                ));
    }

    @Test
    @DisplayName("POST /api/interviews - 실패: 일일 쿼터 초과 (429)")
    void createSession_QuotaExceeded() throws Exception {
        // 오늘 세션 1개 미리 생성 (FREE 플랜 한도: 1회)
        interviewSessionRepository.save(InterviewSession.create(
                testUser, "백엔드 개발자", ExperienceLevel.JUNIOR, InterviewType.TECHNICAL, 3
        ));

        CreateInterviewRequest request = new CreateInterviewRequest(
                "프론트엔드 개발자",
                ExperienceLevel.JUNIOR,
                InterviewType.TECHNICAL,
                3
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
                5
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
    @DisplayName("POST /api/interviews - 실패: 인증 없음 (401)")
    void createSession_Unauthorized() throws Exception {
        CreateInterviewRequest request = new CreateInterviewRequest(
                "백엔드 개발자",
                ExperienceLevel.JUNIOR,
                InterviewType.TECHNICAL,
                3
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
}
