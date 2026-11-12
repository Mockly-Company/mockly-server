package app.mockly.domain.interview.controller;

import app.mockly.domain.interview.dto.request.CreateImprovementPracticeRequest;
import app.mockly.domain.interview.dto.request.SubmitImprovementPracticeAnswerRequest;
import app.mockly.domain.interview.dto.response.CreateImprovementPracticeResponse;
import app.mockly.domain.interview.dto.response.GetImprovementPracticeResponse;
import app.mockly.domain.interview.dto.response.ImprovementPracticeStatusInfo;
import app.mockly.domain.interview.entity.ImprovementPracticeStatus;
import app.mockly.domain.interview.service.ImprovementPracticeService;
import app.mockly.domain.interview.service.ImprovementPracticeSseManager;
import app.mockly.global.common.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.UUID;

@RestController
@RequestMapping("/api/interviews/improvement-practices")
@RequiredArgsConstructor
public class ImprovementPracticeController {

    private static final long SSE_TIMEOUT_MS = 180_000L;

    private final ImprovementPracticeService improvementPracticeService;
    private final ImprovementPracticeSseManager improvementPracticeSseManager;

    @PostMapping
    public ResponseEntity<ApiResponse<CreateImprovementPracticeResponse>> createPractice(
            @AuthenticationPrincipal UUID userId,
            @Valid @RequestBody CreateImprovementPracticeRequest request
    ) {
        CreateImprovementPracticeResponse response = improvementPracticeService.createPractice(userId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(response));
    }

    @GetMapping("/{practiceId}")
    public ResponseEntity<ApiResponse<GetImprovementPracticeResponse>> getPractice(
            @AuthenticationPrincipal UUID userId,
            @PathVariable Long practiceId
    ) {
        GetImprovementPracticeResponse response = improvementPracticeService.getPractice(userId, practiceId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PostMapping("/{practiceId}/answer")
    public ResponseEntity<ApiResponse<CreateImprovementPracticeResponse>> submitAnswer(
            @AuthenticationPrincipal UUID userId,
            @PathVariable Long practiceId,
            @Valid @RequestBody SubmitImprovementPracticeAnswerRequest request
    ) {
        CreateImprovementPracticeResponse response = improvementPracticeService.submitAnswer(userId, practiceId, request);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping(value = "/{practiceId}/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamEvents(
            @AuthenticationPrincipal UUID userId,
            @PathVariable Long practiceId
    ) {
        SseEmitter emitter = improvementPracticeSseManager.connect(practiceId, SSE_TIMEOUT_MS);
        ImprovementPracticeStatusInfo statusInfo;
        try {
            statusInfo = improvementPracticeService.getStatusInfo(userId, practiceId);
        } catch (RuntimeException e) {
            improvementPracticeSseManager.disconnect(practiceId, emitter);
            throw e;
        }

        ImprovementPracticeStatus status = statusInfo.status();
        if (status == ImprovementPracticeStatus.COMPLETED) {
            improvementPracticeSseManager.replayTerminalIfActive(practiceId, emitter, status, null);
        } else if (status == ImprovementPracticeStatus.FAILED) {
            improvementPracticeSseManager.replayTerminalIfActive(
                    practiceId,
                    emitter,
                    status,
                    ImprovementPracticeSseManager.EVALUATION_FAILURE_MESSAGE
            );
        }
        return emitter;
    }

    @PostMapping("/{practiceId}/retry")
    public ResponseEntity<ApiResponse<GetImprovementPracticeResponse>> retryPractice(
            @AuthenticationPrincipal UUID userId,
            @PathVariable Long practiceId
    ) {
        GetImprovementPracticeResponse response = improvementPracticeService.retryPractice(userId, practiceId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
