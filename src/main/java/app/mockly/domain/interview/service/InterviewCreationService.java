package app.mockly.domain.interview.service;

import app.mockly.domain.auth.entity.User;
import app.mockly.domain.auth.repository.UserRepository;
import app.mockly.domain.interview.dto.request.CreateInterviewRequest;
import app.mockly.domain.interview.dto.response.CreateInterviewResponse;
import app.mockly.domain.interview.dto.WeeklyQuotaContext;
import app.mockly.domain.interview.entity.InterviewSession;
import app.mockly.domain.interview.entity.QuotaType;
import app.mockly.domain.interview.repository.InterviewSessionRepository;
import app.mockly.global.common.ApiStatusCode;
import app.mockly.global.exception.BusinessException;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class InterviewCreationService {

    private static final List<String> GREETINGS = List.of(
            "안녕하세요, 만나서 반갑습니다.",
            "안녕하세요, 오늘 면접에 참여해 주셔서 감사합니다."
    );

    private final UserRepository userRepository;
    private final InterviewSessionRepository interviewSessionRepository;
    private final WeeklyQuotaService weeklyQuotaService;

    @Transactional
    public CreateInterviewResponse create(UUID userId, CreateInterviewRequest request, String keyword, String greeting) {
        User user = userRepository.findByIdForUpdate(userId)
                .orElseThrow(() -> new BusinessException(ApiStatusCode.USER_NOT_FOUND));
        WeeklyQuotaContext context = weeklyQuotaService.calculateCurrentQuotaContext(user, java.time.Instant.now());
        if (request.totalQuestions() > context.product().getMaxQuestions()) {
            throw new BusinessException(ApiStatusCode.VALIDATION_ERROR, "현재 플랜에서 선택할 수 없는 질문 개수입니다.");
        }

        weeklyQuotaService.consume(userId, context, QuotaType.INTERVIEW);
        InterviewSession session = InterviewSession.create(user, request.position(), request.experienceLevel(),
                request.interviewType(), request.totalQuestions(), request.selfIntroduction());
        session.setFirstQuestionKeyword(keyword);
        session.incrementQuestionNumber();
        interviewSessionRepository.save(session);
        return CreateInterviewResponse.from(session, greeting);
    }
}
