package app.mockly.domain.interview.dto.response;

import app.mockly.domain.interview.dto.SessionSummaryDto;
import app.mockly.domain.interview.entity.InterviewSession;
import app.mockly.global.common.PaginationInfo;
import org.springframework.data.domain.Page;

import java.util.List;

public record SessionListResponse(
        List<SessionSummaryDto> sessions,
        PaginationInfo pagination
) {
    public static SessionListResponse from(Page<InterviewSession> page) {
        List<SessionSummaryDto> sessions = page.getContent().stream()
                .map(SessionSummaryDto::from)
                .toList();
        return new SessionListResponse(sessions, PaginationInfo.from(page));
    }
}
