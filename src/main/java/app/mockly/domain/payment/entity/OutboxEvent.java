package app.mockly.domain.payment.entity;

import app.mockly.global.common.BaseEntity;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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

    private static final ObjectMapper objectMapper = new ObjectMapper();

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

    public static OutboxEvent scheduleCreate(Long subscriptionId, String billingKey) {
        try {
            var payloadMap = java.util.Map.of(
                    "subscriptionId", subscriptionId,
                    "billingKey", billingKey
            );
            String payload = objectMapper.writeValueAsString(payloadMap);
            OutboxEvent event = new OutboxEvent();
            event.aggregateType = "SUBSCRIPTION";
            event.aggregateId = subscriptionId;
            event.eventType = "SCHEDULE_CREATE";
            event.payload = payload;
            event.status = OutboxEventStatus.PENDING;
            event.retryCount = 0;
            return event;
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("OutboxEvent payload 직렬화 실패", e);
        }
    }

    public String extractBillingKey() {
        try {
            JsonNode node = objectMapper.readTree(payload);
            return node.get("billingKey").asText();
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Outbox payload 파싱 실패", e);
        }
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
