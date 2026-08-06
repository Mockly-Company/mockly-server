package app.mockly.domain.payment.scheduler;

import app.mockly.domain.payment.repository.OutboxEventRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class OutboxEventTransactionHelperTest {

    @Test
    @DisplayName("이벤트 처리와 실패 기록은 각각 새로운 트랜잭션에서 실행한다")
    void operationsUseRequiresNewTransactions() throws NoSuchMethodException {
        assertRequiresNew("processEvent", Long.class);
        assertRequiresNew("markAsFailed", Long.class, String.class);
        assertRequiresNew("recordRetryFailure", Long.class, String.class, int.class);
    }

    @Test
    @DisplayName("이벤트별 처리 트랜잭션은 대상 이벤트를 잠금 조회한다")
    void repositoryExposesEventLockQuery() {
        assertThatCode(() -> OutboxEventRepository.class.getDeclaredMethod(
                "findByIdForUpdate",
                Long.class
        )).doesNotThrowAnyException();
    }

    private void assertRequiresNew(String methodName, Class<?>... parameterTypes) throws NoSuchMethodException {
        Method method = OutboxEventTransactionHelper.class.getDeclaredMethod(methodName, parameterTypes);
        Transactional transactional = method.getAnnotation(Transactional.class);

        assertThat(transactional).isNotNull();
        assertThat(transactional.propagation()).isEqualTo(Propagation.REQUIRES_NEW);
    }
}
