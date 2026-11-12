package app.mockly.domain.interview.service;

import app.mockly.domain.interview.entity.ImprovementPracticeStatus;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
@RequiredArgsConstructor
public class ImprovementPracticeSseManager {

    private final ConcurrentHashMap<Long, SseEmitter> emitters = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper;

    public SseEmitter connect(Long practiceId, long timeout) {
        SseEmitter emitter = new SseEmitter(timeout);
        SseEmitter previous = emitters.put(practiceId, emitter);
        if (previous != null) {
            try {
                previous.complete();
            } catch (Exception ignored) {
            }
        }
        emitter.onCompletion(() -> emitters.remove(practiceId, emitter));
        emitter.onTimeout(() -> emitters.remove(practiceId, emitter));
        emitter.onError(e -> emitters.remove(practiceId, emitter));
        return emitter;
    }

    public void send(Long practiceId, ImprovementPracticeStatus status) {
        send(practiceId, status, null);
    }

    public void send(Long practiceId, ImprovementPracticeStatus status, String message) {
        SseEmitter emitter = emitters.get(practiceId);
        if (emitter == null) {
            return;
        }

        try {
            Map<String, Object> payload = message != null
                    ? Map.of("practiceStatus", status.toEventValue(), "message", message)
                    : Map.of("practiceStatus", status.toEventValue());
            emitter.send(SseEmitter.event()
                    .name("status")
                    .data(objectMapper.writeValueAsString(payload)));
        } catch (IOException e) {
            log.warn("개선 연습 SSE 전송 실패 practiceId={}", practiceId, e);
            emitters.remove(practiceId, emitter);
        }
    }

    public void complete(Long practiceId) {
        SseEmitter emitter = emitters.get(practiceId);
        if (emitter == null) {
            return;
        }
        try {
            emitter.complete();
        } catch (Exception e) {
            log.warn("개선 연습 SSE complete 실패 practiceId={}", practiceId, e);
        }
        emitters.remove(practiceId, emitter);
    }
}
