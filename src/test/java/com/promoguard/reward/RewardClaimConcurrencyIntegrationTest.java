package com.promoguard.reward;

import com.promoguard.common.DomainException;
import com.promoguard.promotion.Promotion;
import com.promoguard.promotion.PromotionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(properties = {
        "promoguard.notification.worker-enabled=false",
        "spring.datasource.hikari.maximum-pool-size=30"
})
@ActiveProfiles("local-integration")
class RewardClaimConcurrencyIntegrationTest {

    @Autowired
    private PromotionService promotionService;

    @Autowired
    private RewardClaimService claimService;

    @Autowired
    private PromotionRewardRepository rewardRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @BeforeEach
    void cleanDatabase() {
        jdbcTemplate.execute("""
                truncate table
                    dead_letter_events,
                    notification_deliveries,
                    outbox_events,
                    reward_claims,
                    reward_user_counters,
                    promotion_rewards,
                    promotions
                restart identity cascade
                """);

        Set<String> lockKeys = redisTemplate.keys("claim-lock:*");
        if (lockKeys != null && !lockKeys.isEmpty()) {
            redisTemplate.delete(lockKeys);
        }
    }

    @Test
    void claimsOnlyTotalQuantityWhenManyDifferentUsersRequestConcurrently() throws Exception {
        PromotionReward reward = createReward(100, 1);

        List<String> results = runConcurrentClaims(1_000, 100, index -> "user-" + index, reward);

        PromotionReward persistedReward = rewardRepository.findById(reward.id()).orElseThrow();
        long claimCount = count("reward_claims", "reward_id", reward.id());
        long outboxCount = count("outbox_events");
        Integer maxUserClaimCount = jdbcTemplate.queryForObject(
                "select max(claim_count) from reward_user_counters where reward_id = ?",
                Integer.class,
                reward.id()
        );

        assertThat(results.stream().filter("CLAIMED"::equals).count()).isEqualTo(100);
        assertThat(results.stream().filter("SOLD_OUT"::equals).count()).isEqualTo(900);
        assertThat(persistedReward.getClaimedQuantity()).isEqualTo(100);
        assertThat(claimCount).isEqualTo(100);
        assertThat(outboxCount).isEqualTo(100);
        assertThat(maxUserClaimCount).isEqualTo(1);
    }

    @Test
    void allowsOnlyOneClaimWhenSameUserRequestsConcurrentlyWithLimitOne() throws Exception {
        PromotionReward reward = createReward(100, 1);

        List<String> results = runConcurrentClaims(100, 50, index -> "same-user", reward);

        assertThat(results.stream().filter("CLAIMED"::equals).count()).isEqualTo(1);
        assertThat(results).allSatisfy(result ->
                assertThat(result).isIn("CLAIMED", "LOCK_CONFLICT", "USER_LIMIT_EXCEEDED")
        );

        PromotionReward persistedReward = rewardRepository.findById(reward.id()).orElseThrow();
        long claimCount = count("reward_claims", "reward_id", reward.id());
        Integer userClaimCount = jdbcTemplate.queryForObject(
                "select claim_count from reward_user_counters where reward_id = ? and user_id = ?",
                Integer.class,
                reward.id(),
                "same-user"
        );

        assertThat(persistedReward.getClaimedQuantity()).isEqualTo(1);
        assertThat(claimCount).isEqualTo(1);
        assertThat(userClaimCount).isEqualTo(1);
    }

    @Test
    void allowsUpToPerUserLimitForRaffleEntry() {
        PromotionReward reward = createRaffleReward(100, 5);

        for (int i = 0; i < 5; i++) {
            RewardClaimService.ClaimRewardResponse response = claimService.claim(
                    reward.getPromotion().id(),
                    reward.id(),
                    "raffle-user"
            );
            assertThat(response.status()).isEqualTo("CLAIMED");
        }

        assertThatThrownBy(() -> claimService.claim(reward.getPromotion().id(), reward.id(), "raffle-user"))
                .isInstanceOf(DomainException.class)
                .hasMessage("User claim limit exceeded");

        assertRewardState(reward.id(), 5, 5);
    }

    private PromotionReward createReward(int totalQuantity, int perUserLimit) {
        return createReward(totalQuantity, perUserLimit, RewardType.COUPON);
    }

    private PromotionReward createRaffleReward(int totalQuantity, int perUserLimit) {
        return createReward(totalQuantity, perUserLimit, RewardType.RAFFLE_ENTRY);
    }

    private PromotionReward createReward(int totalQuantity, int perUserLimit, RewardType rewardType) {
        Promotion promotion = promotionService.createPromotion(
                "launch promotion",
                Instant.now().minus(Duration.ofDays(1)),
                Instant.now().plus(Duration.ofDays(1))
        );
        return promotionService.createReward(
                promotion.id(),
                rewardType,
                "reward",
                totalQuantity,
                perUserLimit
        );
    }

    private List<String> runConcurrentClaims(
            int requestCount,
            int threadPoolSize,
            UserIdFactory userIdFactory,
            PromotionReward reward
    ) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(threadPoolSize);
        int concurrentlyStartedWorkers = Math.min(requestCount, threadPoolSize);
        CountDownLatch ready = new CountDownLatch(concurrentlyStartedWorkers);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(requestCount);
        Queue<String> results = new ConcurrentLinkedQueue<>();

        for (int i = 0; i < requestCount; i++) {
            int index = i;
            executor.submit(() -> {
                ready.countDown();
                try {
                    start.await();
                    RewardClaimService.ClaimRewardResponse response = claimService.claim(
                            reward.getPromotion().id(),
                            reward.id(),
                            userIdFactory.userId(index)
                    );
                    results.add(response.status());
                } catch (DomainException exception) {
                    results.add(exception.code());
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    results.add("INTERRUPTED");
                } catch (RuntimeException exception) {
                    results.add(exception.getClass().getSimpleName());
                } finally {
                    done.countDown();
                }
            });
        }

        assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
        start.countDown();
        assertThat(done.await(30, TimeUnit.SECONDS)).isTrue();
        executor.shutdownNow();

        return List.copyOf(results);
    }

    private long count(String tableName) {
        Long count = jdbcTemplate.queryForObject("select count(*) from " + tableName, Long.class);
        return count == null ? 0 : count;
    }

    private long count(String tableName, String columnName, Long id) {
        Long count = jdbcTemplate.queryForObject(
                "select count(*) from " + tableName + " where " + columnName + " = ?",
                Long.class,
                id
        );
        return count == null ? 0 : count;
    }

    private void assertRewardState(Long rewardId, int expectedClaims, int expectedUserClaimCount) {
        PromotionReward persistedReward = rewardRepository.findById(rewardId).orElseThrow();
        long claimCount = count("reward_claims", "reward_id", rewardId);
        Integer userClaimCount = jdbcTemplate.queryForObject(
                "select max(claim_count) from reward_user_counters where reward_id = ?",
                Integer.class,
                rewardId
        );

        assertThat(persistedReward.getClaimedQuantity()).isEqualTo(expectedClaims);
        assertThat(claimCount).isEqualTo(expectedClaims);
        assertThat(userClaimCount).isEqualTo(expectedUserClaimCount);
    }

    @FunctionalInterface
    private interface UserIdFactory {
        String userId(int index);
    }
}
