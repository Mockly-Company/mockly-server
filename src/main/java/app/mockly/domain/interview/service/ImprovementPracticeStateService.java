package app.mockly.domain.interview.service;

import app.mockly.domain.interview.dto.ImprovementPracticeEvaluationContext;
import app.mockly.domain.interview.dto.ImprovementPracticeEvaluationResult;
import app.mockly.domain.interview.entity.FeedbackImprovement;
import app.mockly.domain.interview.entity.ImprovementPractice;
import app.mockly.domain.interview.entity.ImprovementPracticeStatus;
import app.mockly.domain.interview.repository.ImprovementPracticeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class ImprovementPracticeStateService {

    private final ImprovementPracticeRepository improvementPracticeRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public Optional<ImprovementPracticeEvaluationContext> loadEvaluationContext(Long practiceId) {
        return improvementPracticeRepository.findByIdWithImprovementAndSession(practiceId)
                .filter(practice -> practice.getStatus() == ImprovementPracticeStatus.GENERATING)
                .map(practice -> {
                    FeedbackImprovement improvement = practice.getImprovement();
                    return new ImprovementPracticeEvaluationContext(
                            practice.getQuestionNumber(),
                            practice.getQuestionText(),
                            practice.getOriginalAnswer(),
                            improvement.getTitle(),
                            improvement.getSummary(),
                            practice.getSubmittedAnswer(),
                            practice.getSession().getInterviewType()
                    );
                });
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean complete(Long practiceId, ImprovementPracticeEvaluationResult result) {
        Optional<ImprovementPractice> optionalPractice = improvementPracticeRepository.findByIdForUpdate(practiceId);
        if (optionalPractice.isEmpty()) {
            return false;
        }
        ImprovementPractice practice = optionalPractice.get();
        if (practice.getStatus() != ImprovementPracticeStatus.GENERATING) {
            return false;
        }
        practice.complete(result);
        improvementPracticeRepository.saveAndFlush(practice);
        return true;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean fail(Long practiceId, String reason) {
        Optional<ImprovementPractice> optionalPractice = improvementPracticeRepository.findByIdForUpdate(practiceId);
        if (optionalPractice.isEmpty()) {
            return false;
        }
        ImprovementPractice practice = optionalPractice.get();
        if (practice.getStatus() != ImprovementPracticeStatus.GENERATING) {
            return false;
        }
        practice.fail(reason);
        improvementPracticeRepository.saveAndFlush(practice);
        return true;
    }
}
