package com.promoguard.dlq;

import com.promoguard.common.DomainException;
import com.promoguard.notification.NotificationProcessor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping("/api/admin/dlq/events")
public class DeadLetterController {

    private final DeadLetterEventRepository deadLetterRepository;
    private final NotificationProcessor notificationProcessor;
    private final Clock clock;

    public DeadLetterController(
            DeadLetterEventRepository deadLetterRepository,
            NotificationProcessor notificationProcessor,
            Clock clock
    ) {
        this.deadLetterRepository = deadLetterRepository;
        this.notificationProcessor = notificationProcessor;
        this.clock = clock;
    }

    @GetMapping
    public List<DeadLetterResponse> list(@RequestParam(defaultValue = "PENDING") DeadLetterStatus status) {
        return deadLetterRepository.findByStatusOrderByIdAsc(status).stream()
                .map(DeadLetterResponse::from)
                .toList();
    }

    @PostMapping("/{eventId}/retry")
    public DeadLetterResponse retry(@PathVariable Long eventId) {
        DeadLetterEvent event = deadLetterRepository.findById(eventId)
                .orElseThrow(() -> new DomainException(HttpStatus.NOT_FOUND, "DLQ_EVENT_NOT_FOUND", "DLQ event not found"));
        return retry(event);
    }

    @PostMapping("/retry-next")
    public DeadLetterResponse retryNext() {
        DeadLetterEvent event = deadLetterRepository.findFirstByStatusOrderByIdAsc(DeadLetterStatus.PENDING)
                .orElseThrow(() -> new DomainException(HttpStatus.NOT_FOUND, "DLQ_EMPTY", "No pending DLQ event"));
        return retry(event);
    }

    private DeadLetterResponse retry(DeadLetterEvent event) {
        Instant now = Instant.now(clock);
        try {
            notificationProcessor.process(event.getPayload());
            event.markReprocessed(now);
        } catch (RuntimeException exception) {
            event.markFailed(exception.getMessage(), now);
        }
        return DeadLetterResponse.from(deadLetterRepository.save(event));
    }

    public record DeadLetterResponse(
            Long id,
            DeadLetterStatus status,
            int retryCount,
            String failureReason
    ) {
        static DeadLetterResponse from(DeadLetterEvent event) {
            return new DeadLetterResponse(event.id(), event.getStatus(), event.getRetryCount(), event.getFailureReason());
        }
    }
}
