package app.mockly.domain.interview.service;

import app.mockly.domain.auth.entity.User;
import app.mockly.domain.auth.repository.UserRepository;
import app.mockly.domain.interview.dto.WeeklyQuotaContext;
import app.mockly.domain.interview.dto.response.GetInterviewOverviewResponse;
import app.mockly.domain.interview.entity.InterviewSession;
import app.mockly.domain.interview.repository.InterviewFeedbackRepository;
import app.mockly.domain.interview.repository.InterviewSessionRepository;
import app.mockly.domain.product.entity.PlanTier;
import app.mockly.global.common.ApiStatusCode;
import app.mockly.global.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class InterviewOverviewService {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final PageRequest LATEST_TWO = PageRequest.of(0, 2);
    private static final PageRequest LATEST_ONE = PageRequest.of(0, 1);

    private final UserRepository userRepository;
    private final WeeklyQuotaService weeklyQuotaService;
    private final InterviewSessionRepository interviewSessionRepository;
    private final InterviewFeedbackRepository interviewFeedbackRepository;

    public GetInterviewOverviewResponse getOverview(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ApiStatusCode.USER_NOT_FOUND));
        WeeklyQuotaContext quotaContext = weeklyQuotaService.calculateCurrentQuotaContext(user, Instant.now());

        List<InterviewSession> lastAnswerSubmittedSessions =
                findLastAnswerSubmittedSessionsInCurrentPeriod(userId, quotaContext);
        List<Integer> latestScores = interviewFeedbackRepository.findRecentOverallScores(
                userId, LATEST_TWO);
        List<InterviewSession> recentInterviews = interviewSessionRepository
                .findTop3ByUserIdAndEndedAtIsNotNullOrderByEndedAtDesc(userId);
        String nextPracticePoint = findNextPracticePoint(userId, quotaContext);

        long totalPracticeSeconds = lastAnswerSubmittedSessions.stream()
                .map(InterviewSession::getDurationSeconds)
                .filter(Objects::nonNull)
                .mapToLong(Long::longValue)
                .sum();

        return GetInterviewOverviewResponse.of(
                quotaContext,
                lastAnswerSubmittedSessions.size(),
                totalPracticeSeconds,
                latestScores,
                recentInterviews,
                nextPracticePoint
        );
    }

    private List<InterviewSession> findLastAnswerSubmittedSessionsInCurrentPeriod(
            UUID userId,
            WeeklyQuotaContext quotaContext
    ) {
        Instant periodStart = quotaContext.periodStart().atStartOfDay(KST).toInstant();
        Instant periodEnd = quotaContext.nextResetAt().atStartOfDay(KST).toInstant();
        return interviewSessionRepository.findLastAnswerSubmittedSessionsInPeriod(
                userId, periodStart, periodEnd);
    }

    private String findNextPracticePoint(UUID userId, WeeklyQuotaContext quotaContext) {
        if (quotaContext.product().getPlanTier() != PlanTier.PRO) {
            return null;
        }

        return interviewFeedbackRepository.findRecentPracticePoints(userId, LATEST_ONE)
                .stream()
                .findFirst()
                .orElse(null);
    }
}
