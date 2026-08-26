package app.mockly.domain.interview.service;

import app.mockly.domain.interview.dto.QuestionStream;
import app.mockly.domain.auth.entity.User;
import app.mockly.domain.auth.repository.UserRepository;
import app.mockly.domain.interview.dto.request.CreateInterviewRequest;
import app.mockly.domain.interview.dto.request.SubmitAnswerRequest;
import app.mockly.domain.interview.dto.response.*;
import app.mockly.domain.interview.event.FeedbackRequestedEvent;
import app.mockly.domain.interview.entity.*;
import app.mockly.domain.interview.repository.InterviewFeedbackRepository;
import app.mockly.domain.interview.repository.InterviewMessageRepository;
import app.mockly.domain.interview.repository.InterviewSessionRepository;
import app.mockly.domain.product.entity.PlanTier;
import app.mockly.domain.product.service.CurrentSubscriptionService;
import app.mockly.global.common.ApiStatusCode;
import app.mockly.global.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import reactor.core.publisher.Flux;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class InterviewService {

    private static final Random RANDOM = new Random();
    private final InterviewSessionRepository interviewSessionRepository;
    private final InterviewMessageRepository interviewMessageRepository;
    private final InterviewFeedbackRepository interviewFeedbackRepository;
    private final UserRepository userRepository;
    private final InterviewAiService interviewAiService;
    private final InterviewCreationService interviewCreationService;
    private final WeeklyQuotaService weeklyQuotaService;
    private final CurrentSubscriptionService currentSubscriptionService;
    private final FeedbackVisibilityService feedbackVisibilityService;
    private final ApplicationEventPublisher eventPublisher;

    public CreateInterviewResponse createSession(UUID userId, CreateInterviewRequest request) {
        List<String> candidates = interviewAiService.extractKeywordCandidates(
                request.selfIntroduction(), request.position());
        log.info("keyword candidates: {}", candidates);
        String keyword = candidates.get(RANDOM.nextInt(candidates.size()));
        log.info("selected keyword: {}", keyword);

        String greeting = RANDOM.nextBoolean()
                ? "안녕하세요, 만나서 반갑습니다."
                : "안녕하세요, 오늘 면접에 참여해 주셔서 감사합니다.";
        return interviewCreationService.create(userId, request, keyword, greeting);
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
            PlanTier generationTier = currentSubscriptionService.getCurrentSubscription(userId)
                    .getSubscriptionPlan()
                    .getProduct()
                    .getPlanTier();
            session.startFeedbackGeneration(generationTier);
            eventPublisher.publishEvent(new FeedbackRequestedEvent(sessionId));
            return SubmitAnswerResponse.feedbackPending(session);
        }

        session.incrementQuestionNumber();
        return SubmitAnswerResponse.inProgress(session);
    }

    @Transactional(readOnly = true)
    public QuestionStream getQuestionStream(UUID sessionId, UUID userId) {
        InterviewSession interviewSession = interviewSessionRepository.findByIdAndUserId(sessionId, userId)
                .orElseThrow(() -> new BusinessException(ApiStatusCode.RESOURCE_NOT_FOUND));
        int questionNumber = interviewSession.getCurrentQuestionNumber();

        Optional<InterviewMessage> existing = interviewMessageRepository.findBySessionIdAndQuestionNumberAndRole(sessionId, questionNumber, InterviewMessageRole.INTERVIEWER);
        if (existing.isPresent()) {
            return new QuestionStream(questionNumber, Flux.just(existing.get().getContent()));
        }

        Flux<String> flux = questionNumber == 1
                ? interviewAiService.generateFirstQuestion(interviewSession)
                : interviewAiService.generateNextQuestion(interviewSession,
                        interviewMessageRepository.findBySessionIdOrderByIdAsc(sessionId));
        return new QuestionStream(questionNumber, flux);
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
                .map(f -> feedbackVisibilityService.toResponse(userId, f))
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

    @Transactional(readOnly = true)
    public GetQuotaResponse getQuota(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ApiStatusCode.USER_NOT_FOUND));
        return weeklyQuotaService.getQuota(user);
    }

    @Transactional(readOnly = true)
    public GetFeedbackResponse getFeedback(UUID userId, UUID sessionId) {
        InterviewSession session = interviewSessionRepository.findByIdAndUserId(sessionId, userId)
                .orElseThrow(() -> new BusinessException(ApiStatusCode.RESOURCE_NOT_FOUND));

        FeedbackStatus feedbackStatus = session.getFeedbackStatus();
        if (feedbackStatus == null) {
            throw new BusinessException(ApiStatusCode.RESOURCE_NOT_FOUND, "아직 피드백이 생성되지 않은 세션입니다.");
        }
        if (feedbackStatus == FeedbackStatus.FAILED) {
            return GetFeedbackResponse.failed(session.getFailReason());
        }
        if (feedbackStatus == FeedbackStatus.PENDING || feedbackStatus == FeedbackStatus.GENERATING) {
            return GetFeedbackResponse.generating();
        }
        FeedbackDto feedback = interviewFeedbackRepository.findBySessionId(sessionId)
                .map(f -> feedbackVisibilityService.toResponse(userId, f))
                .orElseThrow(() -> new BusinessException(ApiStatusCode.RESOURCE_NOT_FOUND, "피드백 데이터를 찾을 수 없습니다."));
        return GetFeedbackResponse.completed(feedback);
    }

    @Transactional
    public GetFeedbackResponse retryFeedback(UUID userId, UUID sessionId) {
        // Assumption: no existing findSessionOrThrow helper was present; using the same ownership lookup pattern as existing methods.
        InterviewSession session = interviewSessionRepository.findByIdAndUserId(sessionId, userId)
                .orElseThrow(() -> new BusinessException(ApiStatusCode.RESOURCE_NOT_FOUND));

        if (session.getFeedbackStatus() != FeedbackStatus.FAILED) {
            throw new BusinessException(ApiStatusCode.VALIDATION_ERROR, "실패한 피드백만 재시도할 수 있습니다.");
        }
        if (session.getStatus() != InterviewSessionStatus.FEEDBACK_PENDING) {
            throw new BusinessException(ApiStatusCode.VALIDATION_ERROR, "피드백 대기 상태인 세션만 재시도할 수 있습니다.");
        }

        session.resetFeedbackStatus();
        eventPublisher.publishEvent(new FeedbackRequestedEvent(sessionId));
        return GetFeedbackResponse.pending();
    }

    @Transactional(readOnly = true)
    public FeedbackStatusInfo getFeedbackStatusInfo(UUID userId, UUID sessionId) {
        InterviewSession session = interviewSessionRepository.findByIdAndUserId(sessionId, userId)
                .orElseThrow(() -> new BusinessException(ApiStatusCode.RESOURCE_NOT_FOUND));
        if (session.getFeedbackStatus() == null) {
            throw new BusinessException(ApiStatusCode.RESOURCE_NOT_FOUND, "피드백 생성이 시작되지 않은 세션입니다.");
        }
        return new FeedbackStatusInfo(session.getFeedbackStatus(), session.getFailReason());
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
