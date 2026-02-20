package app.mockly.domain.interview.entity;

import app.mockly.global.common.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "interview_message")
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class InterviewMessage extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "session_id", nullable = false)
    private InterviewSession session;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private InterviewMessageRole role;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    private Integer questionNumber;

    public static InterviewMessage interviewerMessage(InterviewSession session, String content, int questionNumber) {
        return InterviewMessage.builder()
                .session(session)
                .role(InterviewMessageRole.INTERVIEWER)
                .content(content)
                .questionNumber(questionNumber)
                .build();
    }

    public static InterviewMessage userMessage(InterviewSession session, String content) {
        return InterviewMessage.builder()
                .session(session)
                .role(InterviewMessageRole.USER)
                .content(content)
                .build();
    }
}
