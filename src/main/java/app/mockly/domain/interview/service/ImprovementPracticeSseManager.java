package app.mockly.domain.interview.service;

import app.mockly.domain.interview.entity.ImprovementPracticeStatus;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 단일 인스턴스 내 개선 연습 SSE 구독자를 관리한다.
 * 다중 인스턴스 확장 시에는 Redis Pub/Sub 전파 및 클라이언트의 GET 단건 폴링 폴백을 함께 사용한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ImprovementPracticeSseManager {

    public static final String EVALUATION_FAILURE_MESSAGE = "답변 개선 평가 생성에 실패했습니다.";

    private final ConcurrentHashMap<Long, CopyOnWriteArrayList<SseEmitter>> emitters = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper;

    public SseEmitter connect(Long practiceId, long timeout) {
        SseEmitter emitter = new SseEmitter(timeout);
        emitters.computeIfAbsent(practiceId, ignored -> new CopyOnWriteArrayList<>()).add(emitter);
        emitter.onCompletion(() -> disconnect(practiceId, emitter));
        emitter.onTimeout(() -> disconnect(practiceId, emitter));
        emitter.onError(e -> disconnect(practiceId, emitter));
        return emitter;
    }

    public boolean disconnect(Long practiceId, SseEmitter emitter) {
        final boolean[] removed = {false};
        emitters.computeIfPresent(practiceId, (key, list) -> {
            removed[0] = list.remove(emitter);
            return list.isEmpty() ? null : list;
        });
        return removed[0];
    }

    public boolean hasActiveSubscribers(Long practiceId) {
        List<SseEmitter> subscribers = emitters.get(practiceId);
        return subscribers != null && !subscribers.isEmpty();
    }

    public void replayTerminalIfActive(
            Long practiceId,
            SseEmitter emitter,
            ImprovementPracticeStatus status,
            String message
    ) {
        if (!disconnect(practiceId, emitter)) {
            return;
        }
        String serializedPayload = serializePayload(practiceId, status, message);
        if (serializedPayload != null) {
            try {
                emitter.send(SseEmitter.event()
                        .name("status")
                        .data(serializedPayload));
            } catch (IOException e) {
                log.warn("개선 연습 SSE 터미널 상태 즉시 전송 실패 practiceId={}", practiceId, e);
            }
        }
        try {
            emitter.complete();
        } catch (Exception e) {
            log.warn("개선 연습 SSE 초기 complete 실패 practiceId={}", practiceId, e);
        }
    }

    public void send(Long practiceId, ImprovementPracticeStatus status) {
        send(practiceId, status, null);
    }

    public void send(Long practiceId, ImprovementPracticeStatus status, String message) {
        List<SseEmitter> subscribers = emitters.get(practiceId);
        if (subscribers == null || subscribers.isEmpty()) {
            return;
        }

        String serializedPayload = serializePayload(practiceId, status, message);
        if (serializedPayload == null) {
            return;
        }

        for (SseEmitter emitter : subscribers) {
            try {
                emitter.send(SseEmitter.event()
                        .name("status")
                        .data(serializedPayload));
            } catch (IOException e) {
                log.warn("개선 연습 SSE 전송 실패 practiceId={}", practiceId, e);
                disconnect(practiceId, emitter);
            }
        }
    }

    public void complete(Long practiceId) {
        List<SseEmitter> subscribers = emitters.remove(practiceId);
        if (subscribers == null) {
            return;
        }
        for (SseEmitter emitter : subscribers) {
            try {
                emitter.complete();
            } catch (Exception e) {
                log.warn("개선 연습 SSE complete 실패 practiceId={}", practiceId, e);
            }
        }
    }

    private String serializePayload(Long practiceId, ImprovementPracticeStatus status, String message) {
        try {
            Map<String, Object> payload = message != null
                    ? Map.of("practiceStatus", status.toEventValue(), "message", message)
                    : Map.of("practiceStatus", status.toEventValue());
            return objectMapper.writeValueAsString(payload);
        } catch (IOException e) {
            log.warn("개선 연습 SSE 페이로드 직렬화 실패 practiceId={}", practiceId, e);
            return null;
        }
    }
}
