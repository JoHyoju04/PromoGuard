package com.promoguard.reward;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PromotionRewardRepository extends JpaRepository<PromotionReward, Long> {

    @Modifying
    @Query(value = """
            update promotion_rewards
               set claimed_quantity = claimed_quantity + 1
            where id = :rewardId
              and status = 'ACTIVE'
              and claimed_quantity < total_quantity
            """, nativeQuery = true)
    int increaseClaimedQuantity(@Param("rewardId") Long rewardId);
}
