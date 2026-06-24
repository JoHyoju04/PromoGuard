package com.promoguard.outbox;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Duration;
import java.time.Instant;

@Getter
@Entity
@Table(
        name = "outbox_events",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_outbox_events_aggregate_event",
                columnNames = {"aggregate_type", "aggregate_id", "event_type"}
        )
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OutboxEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 50)
    private String aggregateType;

    @Column(nullable = false)
    private Long aggregateId;

    @Column(nullable = false, length = 80)
    private String eventType;

    @Column(nullable = false, columnDefinition = "text")
    private String payload;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OutboxStatus status = OutboxStatus.READY;

    @Column(nullable = false)
    private int retryCount = 0;

    @Column(nullable = false)
    private Instant nextRetryAt;

    private Instant publishedAt;

    @Column(nullable = false)
    private Instant updatedAt;

    @Builder
    private OutboxEvent(
            String aggregateType,
            Long aggregateId,
            String eventType,
            String payload,
            OutboxStatus status,
            int retryCount,
            Instant nextRetryAt,
            Instant updatedAt
    ) {
        this.aggregateType = aggregateType;
        this.aggregateId = aggregateId;
        this.eventType = eventType;
        this.payload = payload;
        this.status = status == null ? OutboxStatus.READY : status;
        this.retryCount = retryCount;
        this.nextRetryAt = nextRetryAt;
        this.updatedAt = updatedAt;
    }

    public Long id() {
        return id;
    }

    public void markProcessing(Instant now) {
        this.status = OutboxStatus.PROCESSING;
        this.updatedAt = now;
    }

    public void markProcessed(Instant now) {
        this.status = OutboxStatus.PROCESSED;
        this.publishedAt = now;
        this.updatedAt = now;
    }

    public void markReadyForRetry(Instant now) {
        this.retryCount++;
        this.status = OutboxStatus.READY;
        this.nextRetryAt = now.plus(Duration.ofSeconds(Math.min(60, 2L * retryCount)));
        this.updatedAt = now;
    }

    public void markFailed(Instant now) {
        this.retryCount++;
        this.status = OutboxStatus.FAILED;
        this.updatedAt = now;
    }
}
