package app.mockly.domain.interview.controller;

import app.mockly.domain.interview.dto.request.CreateInterviewRequest;
import app.mockly.domain.interview.dto.request.SubmitAnswerRequest;
import app.mockly.domain.interview.dto.response.CreateInterviewResponse;
import app.mockly.domain.interview.dto.response.GetSessionDetailResponse;
import app.mockly.domain.interview.dto.response.GetSessionListResponse;
import app.mockly.domain.interview.dto.response.SubmitAnswerResponse;
import app.mockly.domain.interview.entity.InterviewSessionStatus;
import app.mockly.domain.interview.service.InterviewService;
import app.mockly.global.common.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;

@Slf4j
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

    @GetMapping
    public ResponseEntity<ApiResponse<GetSessionListResponse>> getSessions(
            @AuthenticationPrincipal UUID userId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) InterviewSessionStatus status
    ) {
        GetSessionListResponse response = interviewService.getSessions(userId, page, size, status);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/{sessionId}")
    public ResponseEntity<ApiResponse<GetSessionDetailResponse>> getSessionDetail(
            @AuthenticationPrincipal UUID userId,
            @PathVariable UUID sessionId
    ) {
        GetSessionDetailResponse response = interviewService.getSessionDetail(userId, sessionId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping(value = "/{sessionId}/questions/stream",
            produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(
            @AuthenticationPrincipal UUID userId,
            @PathVariable UUID sessionId
    ) {
        SseEmitter emitter = new SseEmitter(60_000L);
        StringBuilder fullText = new StringBuilder();
        int questionNumber = interviewService.getCurrentQuestionNumber(sessionId, userId);

        interviewService.prepareQuestion(sessionId, userId)
            .subscribe(
                token -> {
                    fullText.append(token);
                    try {
                        emitter.send(SseEmitter.event().name("token").data(token));
                    } catch (IOException e) {
                        emitter.completeWithError(e);
                    }
                },
                error -> {
                    try {
                        emitter.send(SseEmitter.event().name("error")
                                .data(Map.of("message", "질문 생성 중 오류가 발생했습니다.")));
                    } catch (IOException ignored) {}
                    emitter.completeWithError(error);
                },
                () -> {
                    try {
                        interviewService.saveQuestion(sessionId, questionNumber, fullText.toString());
                    } catch (Exception e) {
                        log.error("질문 저장 실패 sessionId={} questionNumber={}", sessionId, questionNumber, e);
                    }
                    try {
                        emitter.send(SseEmitter.event().name("done").data("{}"));
                    } catch (IOException ignored) {}
                    emitter.complete();
                });

        return emitter;
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
