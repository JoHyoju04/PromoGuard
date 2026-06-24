package com.promoguard.notification;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.promoguard.reward.RewardClaimedEvent;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;

@Service
public class NotificationProcessor {

    private final ObjectMapper objectMapper;
    private final AlimtalkClient alimtalkClient;
    private final NotificationDeliveryRepository deliveryRepository;
    private final Clock clock;

    public NotificationProcessor(
            ObjectMapper objectMapper,
            AlimtalkClient alimtalkClient,
            NotificationDeliveryRepository deliveryRepository,
            Clock clock
    ) {
        this.objectMapper = objectMapper;
        this.alimtalkClient = alimtalkClient;
        this.deliveryRepository = deliveryRepository;
        this.clock = clock;
    }

    public void process(String payload) {
        RewardClaimedEvent event = parse(payload);
        try {
            alimtalkClient.sendRewardClaimed(event);
            deliveryRepository.save(NotificationDelivery.sent(event.claimId(), event.userId(), Instant.now(clock)));
        } catch (RuntimeException exception) {
            deliveryRepository.save(NotificationDelivery.failed(event.claimId(), event.userId(), exception.getMessage()));
            throw exception;
        }
    }

    private RewardClaimedEvent parse(String payload) {
        try {
            return objectMapper.readValue(payload, RewardClaimedEvent.class);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to parse outbox payload", exception);
        }
    }
}
