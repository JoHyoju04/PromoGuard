package com.promoguard.reward;

import java.time.Instant;

public record RewardClaimedEvent(
        Long claimId,
        Long promotionId,
        Long rewardId,
        RewardType rewardType,
        String userId,
        Instant claimedAt
) {
}
