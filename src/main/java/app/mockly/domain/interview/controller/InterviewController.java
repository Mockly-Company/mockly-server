package app.mockly.domain.interview.controller;

import app.mockly.domain.interview.dto.request.CreateInterviewRequest;
import app.mockly.domain.interview.dto.request.SubmitAnswerRequest;
import app.mockly.domain.interview.dto.response.CreateInterviewResponse;
import app.mockly.domain.interview.dto.response.SubmitAnswerResponse;
import app.mockly.domain.interview.service.InterviewService;
import app.mockly.global.common.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/interviews")
@RequiredArgsConstructor
public class InterviewController {

    private final InterviewService interviewService;

    @PostMapping
    public ResponseEntity<ApiResponse<CreateInterviewResponse>> createSession(
            @AuthenticationPrincipal UUID userId,
            @RequestBody @Valid CreateInterviewRequest request
    ) {
        CreateInterviewResponse response = interviewService.createSession(userId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(response));
    }

    @PostMapping("/{sessionId}/answer")
    public ResponseEntity<ApiResponse<SubmitAnswerResponse>> submitAnswer(
            @AuthenticationPrincipal UUID userId,
            @PathVariable UUID sessionId,
            @RequestBody @Valid SubmitAnswerRequest request
    ) {
        SubmitAnswerResponse response = interviewService.submitAnswer(userId, sessionId, request);
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
