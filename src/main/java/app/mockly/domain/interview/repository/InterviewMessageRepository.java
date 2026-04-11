package app.mockly.domain.interview.repository;

import app.mockly.domain.interview.entity.InterviewMessage;
import app.mockly.domain.interview.entity.InterviewMessageRole;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface InterviewMessageRepository extends JpaRepository<InterviewMessage, Long> {

    List<InterviewMessage> findBySessionIdOrderByIdAsc(UUID sessionId);

    Optional<InterviewMessage> findBySessionIdAndQuestionNumberAndRole(UUID sessionId, int questionNumber, InterviewMessageRole role);
}
