package com.promoguard.outbox;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class OutboxEventTest {

    @Test
    void marksProcessingProcessedAndRetryStates() {
        Instant now = Instant.parse("2026-06-08T00:00:00Z");
        OutboxEvent event = OutboxEvent.builder()
                .aggregateType("REWARD_CLAIM")
                .aggregateId(1L)
                .eventType("RewardClaimed")
                .payload("{}")
                .nextRetryAt(now)
                .updatedAt(now)
                .build();

        event.markProcessing(now);
        assertThat(event.getStatus()).isEqualTo(OutboxStatus.PROCESSING);

        event.markReadyForRetry(now);
        assertThat(event.getStatus()).isEqualTo(OutboxStatus.READY);
        assertThat(event.getRetryCount()).isEqualTo(1);

        event.markProcessed(now);
        assertThat(event.getStatus()).isEqualTo(OutboxStatus.PROCESSED);
    }

    @Test
    void marksFailedAndIncrementsRetryCount() {
        Instant now = Instant.parse("2026-06-08T00:00:00Z");
        OutboxEvent event = OutboxEvent.builder()
                .aggregateType("REWARD_CLAIM")
                .aggregateId(1L)
                .eventType("RewardClaimed")
                .payload("{}")
                .nextRetryAt(now)
                .updatedAt(now)
                .build();

        event.markFailed(now);

        assertThat(event.getStatus()).isEqualTo(OutboxStatus.FAILED);
        assertThat(event.getRetryCount()).isEqualTo(1);
    }
}
