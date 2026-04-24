package app.mockly.domain.interview.service;

import app.mockly.domain.auth.entity.User;
import app.mockly.domain.interview.dto.request.CreateInterviewRequest;
import app.mockly.domain.interview.entity.InterviewSession;
import app.mockly.domain.interview.repository.InterviewSessionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class InterviewSessionWriter {

    private final InterviewSessionRepository interviewSessionRepository;

    @Transactional
    public InterviewSession saveNewSession(User user, CreateInterviewRequest request, String keyword) {
        InterviewSession session = InterviewSession.create(
                user, request.position(), request.experienceLevel(), request.interviewType(),
                request.totalQuestions(), request.selfIntroduction());
        session.setFirstQuestionKeyword(keyword);
        interviewSessionRepository.save(session);
        session.incrementQuestionNumber();
        return session;
    }
}
