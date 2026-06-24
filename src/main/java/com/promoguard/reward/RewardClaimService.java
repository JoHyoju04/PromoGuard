package com.promoguard.reward;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.promoguard.common.DomainException;
import com.promoguard.lock.ClaimLockService;
import com.promoguard.outbox.OutboxEvent;
import com.promoguard.outbox.OutboxEventRepository;
import com.promoguard.promotion.Promotion;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Instant;

@Service
public class RewardClaimService {

    private final PromotionRewardRepository rewardRepository;
    private final RewardUserCounterRepository userCounterRepository;
    private final RewardClaimRepository claimRepository;
    private final OutboxEventRepository outboxRepository;
    private final ClaimLockService lockService;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final TransactionTemplate transactionTemplate;

    public RewardClaimService(
            PromotionRewardRepository rewardRepository,
            RewardUserCounterRepository userCounterRepository,
            RewardClaimRepository claimRepository,
            OutboxEventRepository outboxRepository,
            ClaimLockService lockService,
            ObjectMapper objectMapper,
            Clock clock,
            TransactionTemplate transactionTemplate
    ) {
        this.rewardRepository = rewardRepository;
        this.userCounterRepository = userCounterRepository;
        this.claimRepository = claimRepository;
        this.outboxRepository = outboxRepository;
        this.lockService = lockService;
        this.objectMapper = objectMapper;
        this.clock = clock;
        this.transactionTemplate = transactionTemplate;
    }

    public ClaimRewardResponse claim(Long promotionId, Long rewardId, String userId) {
        String lockKey = "claim-lock:" + rewardId + ":" + userId;
        ClaimLockService.LockToken lockToken = lockService.tryLock(lockKey)
                .orElseThrow(() -> new DomainException(HttpStatus.CONFLICT, "LOCK_CONFLICT", "Same user claim request is already processing"));
        try {
            return transactionTemplate.execute(status -> claimInTransaction(promotionId, rewardId, userId));
        } finally {
            lockService.unlock(lockToken);
        }
    }

    private ClaimRewardResponse claimInTransaction(Long promotionId, Long rewardId, String userId) {
        Instant now = Instant.now(clock);
        PromotionReward reward = rewardRepository.findById(rewardId)
                .orElseThrow(() -> new DomainException(HttpStatus.NOT_FOUND, "REWARD_NOT_FOUND", "Reward not found"));

        Promotion promotion = reward.getPromotion();
        if (!promotion.id().equals(promotionId)) {
            throw new DomainException(HttpStatus.NOT_FOUND, "REWARD_NOT_FOUND", "Reward does not belong to promotion");
        }
        if (!promotion.isOpenAt(now)) {
            throw new DomainException(HttpStatus.CONFLICT, "PROMOTION_NOT_OPEN", "Promotion is not open");
        }
        if (!reward.isActive()) {
            throw new DomainException(HttpStatus.CONFLICT, "REWARD_NOT_ACTIVE", "Reward is not active");
        }

        int userCounterUpdated = userCounterRepository.increaseWithinLimit(rewardId, userId, reward.getPerUserLimit());
        if (userCounterUpdated == 0) {
            throw new DomainException(HttpStatus.CONFLICT, "USER_LIMIT_EXCEEDED", "User claim limit exceeded");
        }

        int stockUpdated = rewardRepository.increaseClaimedQuantity(rewardId);
        if (stockUpdated == 0) {
            throw new DomainException(HttpStatus.CONFLICT, "SOLD_OUT", "Reward sold out");
        }

        ClaimType claimType = reward.getRewardType() == RewardType.COUPON ? ClaimType.COUPON_ISSUE : ClaimType.RAFFLE_ENTRY;
        RewardClaim claim = claimRepository.save(RewardClaim.builder()
                .promotion(promotion)
                .reward(reward)
                .userId(userId)
                .claimType(claimType)
                .claimedAt(now)
                .build());
        RewardClaimedEvent event = new RewardClaimedEvent(
                claim.id(),
                promotion.id(),
                reward.id(),
                reward.getRewardType(),
                userId,
                now
        );
        outboxRepository.save(OutboxEvent.builder()
                .aggregateType("REWARD_CLAIM")
                .aggregateId(claim.id())
                .eventType("RewardClaimed")
                .payload(toJson(event))
                .nextRetryAt(now)
                .updatedAt(now)
                .build());

        return new ClaimRewardResponse("CLAIMED", claim.id(), reward.getRewardType(), "Reward claimed");
    }

    private String toJson(RewardClaimedEvent event) {
        try {
            return objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to serialize reward claimed event", exception);
        }
    }

    public record ClaimRewardResponse(
            String status,
            Long claimId,
            RewardType rewardType,
            String message
    ) {
    }
}
