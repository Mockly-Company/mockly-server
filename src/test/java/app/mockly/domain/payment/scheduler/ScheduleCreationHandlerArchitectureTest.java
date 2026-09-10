package app.mockly.domain.payment.scheduler;

import app.mockly.domain.payment.entity.OutboxEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;

class ScheduleCreationHandlerArchitectureTest {

    @Test
    @DisplayName("스케줄 생성 Outbox 소비 로직은 전용 handler가 담당한다")
    void scheduleCreationHandlerExists() {
        assertThatCode(() -> Class.forName(
                "app.mockly.domain.payment.scheduler.ScheduleCreationHandler"
        )).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("스케줄 생성 handler는 조회된 Outbox 이벤트를 직접 처리한다")
    void handlerAcceptsOutboxEvent() {
        assertThatCode(() -> ScheduleCreationHandler.class.getDeclaredMethod(
                "handle",
                OutboxEvent.class
        )).doesNotThrowAnyException();
    }
}
