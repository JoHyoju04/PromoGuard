package com.promoguard.dlq;

import com.promoguard.outbox.OutboxEvent;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Getter
@Entity
@Table(name = "dead_letter_events")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DeadLetterEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long outboxEventId;

    @Column(nullable = false, length = 80)
    private String eventType;

    @Column(nullable = false, columnDefinition = "text")
    private String payload;

    @Column(nullable = false, columnDefinition = "text")
    private String failureReason;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private DeadLetterStatus status = DeadLetterStatus.PENDING;

    @Column(nullable = false)
    private int retryCount = 0;

    private Instant lastRetriedAt;

    @Builder
    private DeadLetterEvent(
            OutboxEvent outboxEvent,
            String eventType,
            String payload,
            String failureReason,
            DeadLetterStatus status,
            int retryCount
    ) {
        if (outboxEvent != null) {
            this.outboxEventId = outboxEvent.id();
            this.eventType = outboxEvent.getEventType();
            this.payload = outboxEvent.getPayload();
        } else {
            this.eventType = eventType;
            this.payload = payload;
        }
        this.failureReason = failureReason;
        this.status = status == null ? DeadLetterStatus.PENDING : status;
        this.retryCount = retryCount;
    }

    public Long id() {
        return id;
    }

    public void markReprocessed(Instant now) {
        this.status = DeadLetterStatus.REPROCESSED;
        this.lastRetriedAt = now;
    }

    public void markFailed(String failureReason, Instant now) {
        this.status = DeadLetterStatus.FAILED;
        this.failureReason = failureReason;
        this.retryCount++;
        this.lastRetriedAt = now;
    }
}
