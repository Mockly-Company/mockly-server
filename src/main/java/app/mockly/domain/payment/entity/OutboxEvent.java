package app.mockly.domain.payment.entity;

import app.mockly.global.common.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "outbox_event")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class OutboxEvent extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 50)
    private String aggregateType;

    @Column(nullable = false)
    private Long aggregateId;

    @Column(nullable = false, length = 50)
    private String eventType;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String payload;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OutboxEventStatus status;

    @Column(nullable = false)
    private int retryCount;

    @Column(length = 500)
    private String failReason;

    public static OutboxEvent createSchedule(Long subscriptionId, String payload) {
        OutboxEvent event = new OutboxEvent();
        event.aggregateType = "SUBSCRIPTION";
        event.aggregateId = subscriptionId;
        event.eventType = "SCHEDULE_CREATE";
        event.payload = payload;
        event.status = OutboxEventStatus.PENDING;
        event.retryCount = 0;
        return event;
    }

    public void markAsProcessed() {
        this.status = OutboxEventStatus.PROCESSED;
    }

    public void markAsFailed(String reason) {
        this.status = OutboxEventStatus.FAILED;
        this.failReason = truncate(reason, 500);
    }

    public void recordRetryFailure(String reason) {
        this.retryCount++;
        this.failReason = truncate(reason, 500);
    }

    private String truncate(String value, int maxLength) {
        if (value == null) return null;
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }
}
