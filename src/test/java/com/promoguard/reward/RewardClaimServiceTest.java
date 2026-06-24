package com.promoguard.reward;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.promoguard.common.DomainException;
import com.promoguard.lock.ClaimLockService;
import com.promoguard.outbox.OutboxEvent;
import com.promoguard.outbox.OutboxEventRepository;
import com.promoguard.promotion.Promotion;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RewardClaimServiceTest {

    private final FakeRewardRepository rewardRepository = new FakeRewardRepository();
    private final FakeUserCounterRepository userCounterRepository = new FakeUserCounterRepository();
    private final FakeClaimRepository claimRepository = new FakeClaimRepository();
    private final FakeOutboxRepository outboxRepository = new FakeOutboxRepository();
    private final FakeClaimLockService lockService = new FakeClaimLockService();
    private final Clock clock = Clock.fixed(Instant.parse("2026-06-17T00:00:00Z"), ZoneOffset.UTC);

    private RewardClaimService claimService;

    @BeforeEach
    void setUp() {
        claimService = new RewardClaimService(
                rewardRepository.proxy(),
                userCounterRepository.proxy(),
                claimRepository.proxy(),
                outboxRepository.proxy(),
                lockService,
                objectMapper(),
                clock,
                new TransactionTemplate(noOpTransactionManager())
        );
    }

    @Test
    void claimsRewardAndCreatesOutboxEvent() {
        rewardRepository.reward = reward(1L, 10L, RewardType.COUPON, 1);
        userCounterRepository.updateResult = 1;
        rewardRepository.stockUpdateResult = 1;

        RewardClaimService.ClaimRewardResponse response = claimService.claim(1L, 10L, "user-1");

        assertThat(response.status()).isEqualTo("CLAIMED");
        assertThat(response.claimId()).isEqualTo(1L);
        assertThat(response.rewardType()).isEqualTo(RewardType.COUPON);
        assertThat(userCounterRepository.called).isTrue();
        assertThat(rewardRepository.stockUpdateCalled).isTrue();
        assertThat(claimRepository.savedClaim).isNotNull();
        assertThat(outboxRepository.savedEvent).isNotNull();
        assertThat(outboxRepository.savedEvent.getEventType()).isEqualTo("RewardClaimed");
        assertThat(outboxRepository.savedEvent.getPayload()).contains("\"claimedAt\"");
        assertThat(lockService.unlocked).isTrue();
    }

    @Test
    void rejectsWhenUserLimitExceeded() {
        rewardRepository.reward = reward(1L, 10L, RewardType.COUPON, 1);
        userCounterRepository.updateResult = 0;

        assertThatThrownBy(() -> claimService.claim(1L, 10L, "user-1"))
                .isInstanceOf(DomainException.class)
                .hasMessage("User claim limit exceeded");

        assertThat(rewardRepository.stockUpdateCalled).isFalse();
        assertThat(claimRepository.savedClaim).isNull();
        assertThat(outboxRepository.savedEvent).isNull();
        assertThat(lockService.unlocked).isTrue();
    }

    @Test
    void rejectsWhenSoldOut() {
        rewardRepository.reward = reward(1L, 10L, RewardType.COUPON, 1);
        userCounterRepository.updateResult = 1;
        rewardRepository.stockUpdateResult = 0;

        assertThatThrownBy(() -> claimService.claim(1L, 10L, "user-1"))
                .isInstanceOf(DomainException.class)
                .hasMessage("Reward sold out");

        assertThat(userCounterRepository.called).isTrue();
        assertThat(claimRepository.savedClaim).isNull();
        assertThat(outboxRepository.savedEvent).isNull();
        assertThat(lockService.unlocked).isTrue();
    }

    @Test
    void rejectsWhenLockCannotBeAcquired() {
        lockService.acquire = false;

        assertThatThrownBy(() -> claimService.claim(1L, 10L, "user-1"))
                .isInstanceOf(DomainException.class)
                .hasMessage("Same user claim request is already processing");

        assertThat(userCounterRepository.called).isFalse();
        assertThat(lockService.unlocked).isFalse();
    }

    private static PromotionReward reward(Long promotionId, Long rewardId, RewardType rewardType, int perUserLimit) {
        Promotion promotion = Promotion.builder()
                .name("launch")
                .startsAt(Instant.parse("2026-06-16T00:00:00Z"))
                .endsAt(Instant.parse("2026-06-18T00:00:00Z"))
                .build();
        setId(promotion, promotionId);

        PromotionReward reward = PromotionReward.builder()
                .promotion(promotion)
                .rewardType(rewardType)
                .name("reward")
                .totalQuantity(100)
                .perUserLimit(perUserLimit)
                .build();
        setId(reward, rewardId);
        return reward;
    }

    private static PlatformTransactionManager noOpTransactionManager() {
        return new PlatformTransactionManager() {
            @Override
            public TransactionStatus getTransaction(TransactionDefinition definition) {
                return new SimpleTransactionStatus();
            }

            @Override
            public void commit(TransactionStatus status) {
            }

            @Override
            public void rollback(TransactionStatus status) {
            }
        };
    }

    private static ObjectMapper objectMapper() {
        return new ObjectMapper().registerModule(new JavaTimeModule());
    }

    private static final class FakeRewardRepository {
        private PromotionReward reward;
        private int stockUpdateResult = 1;
        private boolean stockUpdateCalled;

        PromotionRewardRepository proxy() {
            return (PromotionRewardRepository) Proxy.newProxyInstance(
                    PromotionRewardRepository.class.getClassLoader(),
                    new Class<?>[]{PromotionRewardRepository.class},
                    (proxy, method, args) -> switch (method.getName()) {
                        case "findById" -> Optional.ofNullable(reward);
                        case "increaseClaimedQuantity" -> {
                            stockUpdateCalled = true;
                            yield stockUpdateResult;
                        }
                        case "toString" -> "FakeRewardRepository";
                        default -> throw new UnsupportedOperationException(method.getName());
                    }
            );
        }
    }

    private static final class FakeUserCounterRepository {
        private int updateResult = 1;
        private boolean called;

        RewardUserCounterRepository proxy() {
            return (RewardUserCounterRepository) Proxy.newProxyInstance(
                    RewardUserCounterRepository.class.getClassLoader(),
                    new Class<?>[]{RewardUserCounterRepository.class},
                    (proxy, method, args) -> switch (method.getName()) {
                        case "increaseWithinLimit" -> {
                            called = true;
                            yield updateResult;
                        }
                        case "toString" -> "FakeUserCounterRepository";
                        default -> throw new UnsupportedOperationException(method.getName());
                    }
            );
        }
    }

    private static final class FakeClaimRepository {
        private RewardClaim savedClaim;
        private long sequence = 1L;

        RewardClaimRepository proxy() {
            return (RewardClaimRepository) Proxy.newProxyInstance(
                    RewardClaimRepository.class.getClassLoader(),
                    new Class<?>[]{RewardClaimRepository.class},
                    (proxy, method, args) -> switch (method.getName()) {
                        case "save" -> {
                            savedClaim = (RewardClaim) args[0];
                            setId(savedClaim, sequence++);
                            yield savedClaim;
                        }
                        case "toString" -> "FakeClaimRepository";
                        default -> throw new UnsupportedOperationException(method.getName());
                    }
            );
        }
    }

    private static final class FakeOutboxRepository {
        private OutboxEvent savedEvent;

        OutboxEventRepository proxy() {
            return (OutboxEventRepository) Proxy.newProxyInstance(
                    OutboxEventRepository.class.getClassLoader(),
                    new Class<?>[]{OutboxEventRepository.class},
                    (proxy, method, args) -> switch (method.getName()) {
                        case "save" -> {
                            savedEvent = (OutboxEvent) args[0];
                            yield savedEvent;
                        }
                        case "toString" -> "FakeOutboxRepository";
                        default -> throw new UnsupportedOperationException(method.getName());
                    }
            );
        }
    }

    private static final class FakeClaimLockService implements ClaimLockService {
        private boolean acquire = true;
        private boolean unlocked;

        @Override
        public Optional<LockToken> tryLock(String key) {
            if (!acquire) {
                return Optional.empty();
            }
            return Optional.of(new LockToken(key, "token"));
        }

        @Override
        public void unlock(LockToken lockToken) {
            unlocked = true;
        }
    }

    private static void setId(Object target, Long id) {
        try {
            Field field = target.getClass().getDeclaredField("id");
            field.setAccessible(true);
            field.set(target, id);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
