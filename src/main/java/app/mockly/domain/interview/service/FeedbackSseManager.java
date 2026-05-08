package app.mockly.domain.interview.service;

import app.mockly.domain.interview.entity.FeedbackStatus;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
@RequiredArgsConstructor
public class FeedbackSseManager {

    private final ConcurrentHashMap<UUID, SseEmitter> emitters = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper;

    public SseEmitter connect(UUID sessionId, long timeout) {
        SseEmitter emitter = new SseEmitter(timeout);
        SseEmitter previous = emitters.put(sessionId, emitter);
        if (previous != null) {
            try { previous.complete(); } catch (Exception ignored) {}
        }
        // remove(key, value): 재연결 시 새 emitter를 삭제하는 버그 방지
        emitter.onCompletion(() -> emitters.remove(sessionId, emitter));
        emitter.onTimeout(() -> emitters.remove(sessionId, emitter));
        emitter.onError(e -> emitters.remove(sessionId, emitter));
        return emitter;
    }

    public void send(UUID sessionId, FeedbackStatus status) {
        send(sessionId, status, null);
    }

    public void send(UUID sessionId, FeedbackStatus status, String message) {
        SseEmitter emitter = emitters.get(sessionId);
        if (emitter == null) return;

        try {
            Map<String, Object> payload = message != null
                    ? Map.of("feedbackStatus", status.name(), "message", message)
                    : Map.of("feedbackStatus", status.name());
            emitter.send(SseEmitter.event()
                    .name("status")
                    .data(objectMapper.writeValueAsString(payload)));
        } catch (IOException e) {
            log.warn("SSE 전송 실패 sessionId={}", sessionId, e);
            emitters.remove(sessionId, emitter);
        }
    }

    public void complete(UUID sessionId) {
        SseEmitter emitter = emitters.get(sessionId);
        if (emitter == null) return;
        try {
            emitter.complete();
        } catch (Exception e) {
            log.warn("SSE complete 실패 sessionId={}", sessionId, e);
        }
        emitters.remove(sessionId, emitter);
    }
}
