package app.mockly.domain.interview.service;

import app.mockly.domain.auth.entity.User;
import app.mockly.domain.auth.repository.UserRepository;
import app.mockly.domain.interview.dto.InterviewFeedbackResult;
import app.mockly.domain.interview.dto.request.CreateInterviewRequest;
import app.mockly.domain.interview.dto.request.SubmitAnswerRequest;
import app.mockly.domain.interview.dto.response.CreateInterviewResponse;
import app.mockly.domain.interview.dto.response.FeedbackDto;
import app.mockly.domain.interview.dto.response.GetQuotaResponse;
import app.mockly.domain.interview.dto.response.GetSessionDetailResponse;
import app.mockly.domain.interview.dto.response.GetSessionListResponse;
import app.mockly.domain.interview.dto.response.SubmitAnswerResponse;
import app.mockly.domain.interview.entity.*;
import app.mockly.domain.interview.repository.InterviewFeedbackRepository;
import app.mockly.domain.interview.repository.InterviewMessageRepository;
import app.mockly.domain.interview.repository.InterviewQuotaRepository;
import app.mockly.domain.interview.repository.InterviewSessionRepository;
import app.mockly.domain.product.entity.PlanTier;
import app.mockly.domain.product.entity.SubscriptionStatus;
import app.mockly.domain.product.repository.SubscriptionRepository;
import app.mockly.global.common.ApiStatusCode;
import app.mockly.global.exception.BusinessException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import reactor.core.publisher.Flux;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class InterviewService {

    private static final List<String> GREETINGS = List.of(
            "안녕하세요, 만나서 반갑습니다.",
            "안녕하세요, 오늘 면접에 참여해 주셔서 감사합니다."
    );
    private static final Random RANDOM = new Random();
    private final InterviewSessionRepository interviewSessionRepository;
    private final InterviewMessageRepository interviewMessageRepository;
    private final InterviewFeedbackRepository interviewFeedbackRepository;
    private final UserRepository userRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final InterviewAiService interviewAiService;
    private final InterviewQuotaRepository interviewQuotaRepository;
    private final ObjectMapper objectMapper;

    @Transactional
    public CreateInterviewResponse createSession(UUID userId, CreateInterviewRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ApiStatusCode.USER_NOT_FOUND));

        PlanTier plan = getUserPlan(userId);
        validateQuota(userId, plan);
        validateQuestionCount(request.totalQuestions(), plan);

        InterviewSession session = InterviewSession.create(
                user, request.position(), request.experienceLevel(), request.interviewType(),
                request.totalQuestions(), request.selfIntroduction());

        List<String> candidates = interviewAiService.extractKeywordCandidates(
                request.selfIntroduction(), request.position());
        log.info("keyword candidates: {}", candidates);
        String keyword = candidates.get(RANDOM.nextInt(candidates.size()));
        log.info("selected keyword: {}", keyword);
        session.setFirstQuestionKeyword(keyword);
        interviewSessionRepository.save(session);

        session.incrementQuestionNumber();
        String greeting = GREETINGS.get(RANDOM.nextInt(GREETINGS.size()));
        return CreateInterviewResponse.from(session, greeting);
    }

    @Transactional
    public SubmitAnswerResponse submitAnswer(UUID userId, UUID sessionId, SubmitAnswerRequest request) {
        InterviewSession session = interviewSessionRepository.findByIdAndUserId(sessionId, userId)
                .orElseThrow(() -> new BusinessException(ApiStatusCode.RESOURCE_NOT_FOUND));

        if (!session.isInProgress()) {
            throw new BusinessException(ApiStatusCode.VALIDATION_ERROR, "이미 종료된 면접 세션입니다.");
        }

        interviewMessageRepository.save(
                InterviewMessage.createUserMessage(session, request.content(), session.getCurrentQuestionNumber()));

        if (session.isAllQuestionsAnswered()) {
            PlanTier plan = getUserPlan(userId);
            List<InterviewMessage> history = interviewMessageRepository.findBySessionIdOrderByIdAsc(sessionId);
            InterviewFeedbackResult feedbackResult = interviewAiService.generateFeedback(
                    history, session.getInterviewType(), plan);

            session.complete();
            interviewFeedbackRepository.save(InterviewFeedback.create(
                    session,
                    feedbackResult.overallScore(),
                    serializeExpertFeedbacks(feedbackResult),
                    feedbackResult.strengths(),
                    feedbackResult.improvements(),
                    feedbackResult.detailedAnalysis()
            ));

            return SubmitAnswerResponse.completed(session, feedbackResult);
        }

        session.incrementQuestionNumber();
        return SubmitAnswerResponse.inProgress(session);
    }

    public Flux<String> prepareQuestion(UUID sessionId, UUID userId) {
        InterviewSession interviewSession = interviewSessionRepository.findByIdAndUserId(sessionId, userId)
                .orElseThrow(() -> new BusinessException(ApiStatusCode.RESOURCE_NOT_FOUND));
        int questionNumber = interviewSession.getCurrentQuestionNumber();

        Optional<InterviewMessage> existing = interviewMessageRepository.findBySessionIdAndQuestionNumberAndRole(sessionId, questionNumber, InterviewMessageRole.INTERVIEWER);
        if (existing.isPresent()) {
            return Flux.just(existing.get().getContent());
        }

        if (questionNumber == 1) {
            return interviewAiService.generateFirstQuestion(interviewSession);
        }
        List<InterviewMessage> history = interviewMessageRepository.findBySessionIdOrderByIdAsc(sessionId);
        return interviewAiService.generateNextQuestion(interviewSession, history);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void saveQuestion(UUID sessionId, int questionNumber, String content) {
        InterviewSession interviewSession = interviewSessionRepository.findById(sessionId)
                .orElseThrow(() -> new BusinessException(ApiStatusCode.RESOURCE_NOT_FOUND));

        InterviewMessage interviewerMessage = InterviewMessage.createInterviewerMessage(interviewSession, content, questionNumber);
        interviewMessageRepository.save(interviewerMessage);
    }

    @Transactional(readOnly = true)
    public GetSessionDetailResponse getSessionDetail(UUID userId, UUID sessionId) {
        InterviewSession session = interviewSessionRepository.findByIdAndUserId(sessionId, userId)
                .orElseThrow(() -> new BusinessException(ApiStatusCode.RESOURCE_NOT_FOUND));
        List<InterviewMessage> messages = interviewMessageRepository.findBySessionIdOrderByIdAsc(sessionId);
        FeedbackDto feedback = interviewFeedbackRepository.findBySessionId(sessionId)
                .map(f -> FeedbackDto.from(f, objectMapper))
                .orElse(null);
        return GetSessionDetailResponse.from(session, messages, feedback);
    }

    @Transactional(readOnly = true)
    public GetSessionListResponse getSessions(UUID userId, int page, int size, InterviewSessionStatus status) {
        Pageable pageable = PageRequest.of(page - 1, size, Sort.by("createdAt").descending());
        Page<InterviewSession> result = status != null
                ? interviewSessionRepository.findByUserIdAndStatus(userId, status, pageable)
                : interviewSessionRepository.findByUserId(userId, pageable);
        return GetSessionListResponse.from(result);
    }

    public int getCurrentQuestionNumber(UUID sessionId, UUID userId) {
        return interviewSessionRepository.findByIdAndUserId(sessionId, userId)
                .orElseThrow(() -> new BusinessException(ApiStatusCode.RESOURCE_NOT_FOUND))
                .getCurrentQuestionNumber();
    }

    private String serializeExpertFeedbacks(InterviewFeedbackResult result) {
        try {
            return objectMapper.writeValueAsString(result.expertFeedbacks());
        } catch (Exception e) {
            return "[]";
        }
    }

    // active subscription이 있으면 PlanTier 반환, 없으면 FREE
    private PlanTier getUserPlan(UUID userId) {
        return subscriptionRepository.findByUserIdAndStatus(userId, SubscriptionStatus.ACTIVE)
                .map(sub -> sub.getSubscriptionPlan().getProduct().getPlanTier())
                .orElse(PlanTier.FREE);
    }

    private InterviewQuota getInterviewQuota(PlanTier plan) {
        return interviewQuotaRepository.findById(plan)
                .orElseThrow(() -> new BusinessException(ApiStatusCode.RESOURCE_NOT_FOUND, "면접 쿼터 설정을 찾을 수 없습니다."));
    }

    // TODO: 추후 KST 기준을 User별 timezone 지원으로 확장
    private void validateQuota(UUID userId, PlanTier plan) {
        int limit = getInterviewQuota(plan).getDailyLimit();

        ZoneId kst = ZoneId.of("Asia/Seoul");
        ZonedDateTime startOfDay = LocalDate.now(kst).atStartOfDay(kst);
        Instant startInstant = startOfDay.toInstant();
        Instant endInstant = startOfDay.plusDays(1).toInstant();

        long todayCount = interviewSessionRepository.countTodaySessions(userId, startInstant, endInstant);
        if (todayCount >= limit) {
            throw new BusinessException(ApiStatusCode.QUOTA_EXCEEDED);
        }
    }

    // preset {3,5,10} 중 플랜의 maxQuestionsPerSession 이하인 값만 허용
    private void validateQuestionCount(int totalQuestions, PlanTier plan) {
        int max = getInterviewQuota(plan).getMaxQuestionsPerSession();
        Set<Integer> allowed = Set.of(3, 5, 10).stream()
                .filter(q -> q <= max)
                .collect(Collectors.toSet());
        if (!allowed.contains(totalQuestions)) {
            throw new BusinessException(ApiStatusCode.VALIDATION_ERROR, "현재 플랜에서 선택할 수 없는 질문 개수입니다.");
        }
    }

    @Transactional(readOnly = true)
    public GetQuotaResponse getQuota(UUID userId) {
        PlanTier plan = getUserPlan(userId);
        InterviewQuota quota = getInterviewQuota(plan);

        ZoneId kst = ZoneId.of("Asia/Seoul");
        ZonedDateTime startOfDay = LocalDate.now(kst).atStartOfDay(kst);
        Instant startInstant = startOfDay.toInstant();
        Instant endInstant = startOfDay.plusDays(1).toInstant();

        long usedToday = interviewSessionRepository.countTodaySessions(userId, startInstant, endInstant);
        return GetQuotaResponse.of(quota.getDailyLimit(), usedToday, quota.getMaxQuestionsPerSession());
    }

    @Transactional(readOnly = true)
    public FeedbackDto getFeedback(UUID userId, UUID sessionId) {
        interviewSessionRepository.findByIdAndUserId(sessionId, userId)
                .orElseThrow(() -> new BusinessException(ApiStatusCode.RESOURCE_NOT_FOUND));
        return interviewFeedbackRepository.findBySessionId(sessionId)
                .map(f -> FeedbackDto.from(f, objectMapper))
                .orElseThrow(() -> new BusinessException(ApiStatusCode.RESOURCE_NOT_FOUND, "아직 피드백이 생성되지 않은 세션입니다."));
    }

    @Transactional
    public void abandonSession(UUID userId, UUID sessionId) {
        InterviewSession session = interviewSessionRepository.findByIdAndUserId(sessionId, userId)
                .orElseThrow(() -> new BusinessException(ApiStatusCode.RESOURCE_NOT_FOUND));
        if (!session.isInProgress()) {
            throw new BusinessException(ApiStatusCode.VALIDATION_ERROR, "이미 종료된 면접 세션입니다.");
        }
        session.abandon();
    }
}
