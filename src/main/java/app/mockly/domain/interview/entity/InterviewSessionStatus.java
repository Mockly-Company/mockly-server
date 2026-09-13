package app.mockly.domain.interview.entity;

public enum InterviewSessionStatus {
    IN_PROGRESS,
    FEEDBACK_PENDING, // 마지막 답변 제출 완료, 피드백 생성 대기
    COMPLETED,
    ABANDONED
}
