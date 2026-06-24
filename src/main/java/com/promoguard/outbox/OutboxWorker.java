package com.promoguard.outbox;

import com.promoguard.dlq.DeadLetterEvent;
import com.promoguard.dlq.DeadLetterEventRepository;
import com.promoguard.notification.NotificationProcessor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

@Component
@ConditionalOnProperty(name = "promoguard.notification.worker-enabled", havingValue = "true", matchIfMissing = true)
public class OutboxWorker {

    private final OutboxEventRepository outboxRepository;
    private final DeadLetterEventRepository deadLetterRepository;
    private final NotificationProcessor notificationProcessor;
    private final TransactionTemplate transactionTemplate;
    private final Clock clock;
    private final int batchSize;
    private final int maxRetryCount;

    public OutboxWorker(
            OutboxEventRepository outboxRepository,
            DeadLetterEventRepository deadLetterRepository,
            NotificationProcessor notificationProcessor,
            TransactionTemplate transactionTemplate,
            Clock clock,
            @Value("${promoguard.notification.batch-size}") int batchSize,
            @Value("${promoguard.notification.max-retry-count}") int maxRetryCount
    ) {
        this.outboxRepository = outboxRepository;
        this.deadLetterRepository = deadLetterRepository;
        this.notificationProcessor = notificationProcessor;
        this.transactionTemplate = transactionTemplate;
        this.clock = clock;
        this.batchSize = batchSize;
        this.maxRetryCount = maxRetryCount;
    }

    @Scheduled(fixedDelayString = "${promoguard.notification.fixed-delay}")
    public void poll() {
        List<OutboxEvent> events = claimReadyEvents();
        for (OutboxEvent event : events) {
            process(event);
        }
    }

    List<OutboxEvent> claimReadyEvents() {
        return transactionTemplate.execute(status -> {
            Instant now = Instant.now(clock);
            outboxRepository.releaseStaleProcessingEvents(now, now.minusSeconds(60));
            List<OutboxEvent> events = outboxRepository.findReadyForUpdate(now, batchSize);
            events.forEach(event -> event.markProcessing(now));
            return events;
        });
    }

    void process(OutboxEvent event) {
        try {
            notificationProcessor.process(event.getPayload());
            transactionTemplate.executeWithoutResult(status -> {
                event.markProcessed(Instant.now(clock));
                outboxRepository.save(event);
            });
        } catch (RuntimeException exception) {
            transactionTemplate.executeWithoutResult(status -> {
                Instant now = Instant.now(clock);
                if (event.getRetryCount() + 1 >= maxRetryCount) {
                    event.markFailed(now);
                    outboxRepository.save(event);
                    deadLetterRepository.save(DeadLetterEvent.builder()
                            .outboxEvent(event)
                            .failureReason(exception.getMessage())
                            .build());
                } else {
                    event.markReadyForRetry(now);
                    outboxRepository.save(event);
                }
            });
        }
    }
}
