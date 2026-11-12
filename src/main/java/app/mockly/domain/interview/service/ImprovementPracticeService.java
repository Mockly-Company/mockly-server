package app.mockly.domain.interview.service;

import app.mockly.domain.auth.entity.User;
import app.mockly.domain.auth.repository.UserRepository;
import app.mockly.domain.interview.dto.WeeklyQuotaContext;
import app.mockly.domain.interview.dto.request.CreateImprovementPracticeRequest;
import app.mockly.domain.interview.dto.request.SubmitImprovementPracticeAnswerRequest;
import app.mockly.domain.interview.dto.response.CreateImprovementPracticeResponse;
import app.mockly.domain.interview.dto.response.GetImprovementPracticeResponse;
import app.mockly.domain.interview.dto.response.ImprovementPracticeStatusInfo;
import app.mockly.domain.interview.entity.*;
import app.mockly.domain.interview.event.ImprovementPracticeRequestedEvent;
import app.mockly.domain.interview.repository.FeedbackImprovementRepository;
import app.mockly.domain.interview.repository.ImprovementPracticeRepository;
import app.mockly.domain.interview.repository.InterviewMessageRepository;
import app.mockly.global.common.ApiStatusCode;
import app.mockly.global.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ImprovementPracticeService {

    private final UserRepository userRepository;
    private final FeedbackImprovementRepository feedbackImprovementRepository;
    private final ImprovementPracticeRepository improvementPracticeRepository;
    private final InterviewMessageRepository interviewMessageRepository;
    private final WeeklyQuotaService weeklyQuotaService;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public CreateImprovementPracticeResponse createPractice(UUID userId, CreateImprovementPracticeRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ApiStatusCode.USER_NOT_FOUND));

        FeedbackImprovement improvement = feedbackImprovementRepository
                .findByIdWithFeedbackAndSession(request.improvementId())
                .orElseThrow(() -> new BusinessException(ApiStatusCode.RESOURCE_NOT_FOUND, "개선 포인트를 찾을 수 없습니다."));

        InterviewSession session = improvement.getFeedback().getSession();
        if (!session.getUser().getId().equals(userId)) {
            throw new BusinessException(ApiStatusCode.FORBIDDEN, "본인 면접 피드백의 개선 포인트만 연습할 수 있습니다.");
        }

        WeeklyQuotaContext quotaContext = weeklyQuotaService.calculateCurrentQuotaContext(user, Instant.now());
        validateProImprovementPracticeTier(quotaContext);
        if (!weeklyQuotaService.hasRemaining(userId, quotaContext, QuotaType.IMPROVEMENT_PRACTICE)) {
            throw new BusinessException(ApiStatusCode.QUOTA_EXCEEDED);
        }

        int questionNumber = improvement.getQuestionNumber();
        String questionText = interviewMessageRepository
                .findBySessionIdAndQuestionNumberAndRole(session.getId(), questionNumber, InterviewMessageRole.INTERVIEWER)
                .map(InterviewMessage::getContent)
                .orElse("");
        String originalAnswer = interviewMessageRepository
                .findBySessionIdAndQuestionNumberAndRole(session.getId(), questionNumber, InterviewMessageRole.USER)
                .map(InterviewMessage::getContent)
                .orElseGet(() -> improvement.getQuote() != null ? improvement.getQuote() : "");

        ImprovementPractice practice = ImprovementPractice.createPending(
                userId,
                session,
                improvement,
                questionNumber,
                questionText,
                originalAnswer
        );
        ImprovementPractice saved = improvementPracticeRepository.save(practice);
        return CreateImprovementPracticeResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public GetImprovementPracticeResponse getPractice(UUID userId, Long practiceId) {
        ImprovementPractice practice = findOwnedPractice(userId, practiceId);
        return GetImprovementPracticeResponse.from(practice);
    }

    @Transactional
    public CreateImprovementPracticeResponse submitAnswer(
            UUID userId,
            Long practiceId,
            SubmitImprovementPracticeAnswerRequest request
    ) {
        User user = userRepository.findByIdForUpdate(userId)
                .orElseThrow(() -> new BusinessException(ApiStatusCode.USER_NOT_FOUND));

        ImprovementPractice practice = findOwnedPractice(userId, practiceId);
        if (practice.getStatus() != ImprovementPracticeStatus.PENDING) {
            throw new BusinessException(ApiStatusCode.VALIDATION_ERROR, "대기 상태의 연습 건에만 답변을 제출할 수 있습니다.");
        }

        WeeklyQuotaContext quotaContext = weeklyQuotaService.calculateCurrentQuotaContext(user, Instant.now());
        validateProImprovementPracticeTier(quotaContext);
        weeklyQuotaService.consume(userId, quotaContext, QuotaType.IMPROVEMENT_PRACTICE);

        practice.submitAnswer(request.answer().trim());
        improvementPracticeRepository.save(practice);

        eventPublisher.publishEvent(new ImprovementPracticeRequestedEvent(practice.getId()));
        return CreateImprovementPracticeResponse.from(practice);
    }

    @Transactional(readOnly = true)
    public ImprovementPracticeStatusInfo getStatusInfo(UUID userId, Long practiceId) {
        ImprovementPractice practice = findOwnedPractice(userId, practiceId);
        return new ImprovementPracticeStatusInfo(practice.getStatus(), practice.getFailReason());
    }

    @Transactional
    public GetImprovementPracticeResponse retryPractice(UUID userId, Long practiceId) {
        ImprovementPractice practice = findOwnedPractice(userId, practiceId);
        if (practice.getStatus() != ImprovementPracticeStatus.FAILED) {
            throw new BusinessException(ApiStatusCode.VALIDATION_ERROR, "실패 상태의 연습 건만 재시도할 수 있습니다.");
        }

        practice.markRetryGenerating();
        improvementPracticeRepository.save(practice);

        eventPublisher.publishEvent(new ImprovementPracticeRequestedEvent(practice.getId()));
        return GetImprovementPracticeResponse.from(practice);
    }

    private ImprovementPractice findOwnedPractice(UUID userId, Long practiceId) {
        ImprovementPractice practice = improvementPracticeRepository.findById(practiceId)
                .orElseThrow(() -> new BusinessException(ApiStatusCode.RESOURCE_NOT_FOUND, "개선 연습 내역을 찾을 수 없습니다."));
        if (!practice.getUserId().equals(userId)) {
            throw new BusinessException(ApiStatusCode.FORBIDDEN, "해당 개선 연습에 접근할 권한이 없습니다.");
        }
        return practice;
    }

    private void validateProImprovementPracticeTier(WeeklyQuotaContext quotaContext) {
        if (quotaContext.product().getWeeklyImprovementPracticeLimit() <= 0) {
            throw new BusinessException(ApiStatusCode.FORBIDDEN, "답변 개선 연습은 Pro 플랜 전용 기능입니다.");
        }
    }
}
