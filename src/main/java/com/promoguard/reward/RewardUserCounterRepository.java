package com.promoguard.reward;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RewardUserCounterRepository extends JpaRepository<RewardUserCounter, Long> {

    @Modifying
    @Query(value = """
            insert into reward_user_counters (reward_id, user_id, claim_count, created_at, updated_at)
            values (:rewardId, :userId, 1, now(), now())
            on conflict (reward_id, user_id)
            do update
               set claim_count = reward_user_counters.claim_count + 1,
                   updated_at = now()
             where reward_user_counters.claim_count < :perUserLimit
            """, nativeQuery = true)
    int increaseWithinLimit(
            @Param("rewardId") Long rewardId,
            @Param("userId") String userId,
            @Param("perUserLimit") int perUserLimit
    );
}
