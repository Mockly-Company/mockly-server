package app.mockly.domain.payment.scheduler;

import app.mockly.domain.payment.entity.OutboxEventStatus;
import app.mockly.domain.payment.repository.OutboxEventRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class OutboxEventProcessorTest {

    @Test
    @DisplayName("Outbox 스케줄러는 배치 트랜잭션을 열지 않는다")
    void schedulerDoesNotOpenBatchTransaction() throws NoSuchMethodException {
        Transactional transactional = OutboxEventProcessor.class
                .getDeclaredMethod("processOutboxEvents")
                .getAnnotation(Transactional.class);

        assertThat(transactional).isNull();
    }

    @Test
    @DisplayName("Outbox 스케줄러는 구독 서비스를 직접 실행하지 않는다")
    void schedulerDoesNotDependOnSubscriptionService() {
        assertThat(OutboxEventProcessor.class.getDeclaredFields())
                .extracting(field -> field.getType().getName())
                .doesNotContain("app.mockly.domain.product.service.SubscriptionService");
    }

    @Test
    @DisplayName("Outbox repository는 스케줄러에 entity가 아닌 ID를 반환한다")
    void repositoryExposesPendingEventIdProjection() throws NoSuchMethodException {
        assertThat(OutboxEventRepository.class.getMethod(
                "findIdsByStatusOrderByCreatedAtAsc",
                OutboxEventStatus.class,
                Pageable.class
        ).getReturnType()).isEqualTo(java.util.List.class);
    }

    @Test
    @DisplayName("스케줄러는 조회한 이벤트 ID를 worker에 전달한다")
    void schedulerDispatchesEventIdsToWorker() {
        OutboxEventRepository repository = mock(OutboxEventRepository.class);
        OutboxEventWorker worker = mock(OutboxEventWorker.class);
        OutboxEventProcessor processor = new OutboxEventProcessor(repository, worker);
        given(repository.findIdsByStatusOrderByCreatedAtAsc(
                OutboxEventStatus.PENDING,
                PageRequest.of(0, 20)
        )).willReturn(java.util.List.of(1L, 2L));

        processor.processOutboxEvents();

        verify(worker).process(1L);
        verify(worker).process(2L);
    }

    @Test
    @DisplayName("한 이벤트 worker가 실패해도 다음 이벤트를 계속 전달한다")
    void schedulerContinuesAfterWorkerFailure() {
        OutboxEventRepository repository = mock(OutboxEventRepository.class);
        OutboxEventWorker worker = mock(OutboxEventWorker.class);
        OutboxEventProcessor processor = new OutboxEventProcessor(repository, worker);
        given(repository.findIdsByStatusOrderByCreatedAtAsc(
                OutboxEventStatus.PENDING,
                PageRequest.of(0, 20)
        )).willReturn(java.util.List.of(1L, 2L));
        doThrow(new RuntimeException("retry state persistence failed"))
                .when(worker).process(1L);

        assertThatCode(processor::processOutboxEvents).doesNotThrowAnyException();
        verify(worker).process(2L);
    }
}
