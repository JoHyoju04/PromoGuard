package com.promoguard.promotion;

import com.promoguard.common.DomainException;
import com.promoguard.reward.PromotionReward;
import com.promoguard.reward.PromotionRewardRepository;
import com.promoguard.reward.RewardType;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PromotionServiceTest {

    private final FakePromotionRepository promotionRepository = new FakePromotionRepository();
    private final FakeRewardRepository rewardRepository = new FakeRewardRepository();
    private final PromotionService promotionService = new PromotionService(
            promotionRepository.proxy(),
            rewardRepository.proxy()
    );

    @Test
    void createsPromotion() {
        Promotion promotion = promotionService.createPromotion(
                "launch",
                Instant.parse("2026-06-17T00:00:00Z"),
                Instant.parse("2026-06-18T00:00:00Z")
        );

        assertThat(promotion.id()).isEqualTo(1L);
        assertThat(promotion.getName()).isEqualTo("launch");
        assertThat(promotion.getStatus()).isEqualTo(PromotionStatus.ACTIVE);
    }

    @Test
    void createsRewardUnderPromotion() {
        Promotion promotion = promotionRepository.save(Promotion.builder()
                .name("launch")
                .startsAt(Instant.parse("2026-06-17T00:00:00Z"))
                .endsAt(Instant.parse("2026-06-18T00:00:00Z"))
                .build());

        PromotionReward reward = promotionService.createReward(
                promotion.id(),
                RewardType.COUPON,
                "10% coupon",
                100,
                1
        );

        assertThat(reward.id()).isEqualTo(1L);
        assertThat(reward.getPromotion().id()).isEqualTo(promotion.id());
        assertThat(reward.getRewardType()).isEqualTo(RewardType.COUPON);
        assertThat(reward.getPerUserLimit()).isEqualTo(1);
    }

    @Test
    void rejectsRewardLookupWhenPromotionDoesNotMatch() {
        Promotion promotion = promotionRepository.save(Promotion.builder()
                .name("launch")
                .startsAt(Instant.parse("2026-06-17T00:00:00Z"))
                .endsAt(Instant.parse("2026-06-18T00:00:00Z"))
                .build());
        PromotionReward reward = rewardRepository.save(PromotionReward.builder()
                .promotion(promotion)
                .rewardType(RewardType.COUPON)
                .name("10% coupon")
                .totalQuantity(100)
                .perUserLimit(1)
                .build());

        assertThatThrownBy(() -> promotionService.getReward(999L, reward.id()))
                .isInstanceOf(DomainException.class)
                .hasMessage("Reward does not belong to promotion");
    }

    private static final class FakePromotionRepository {
        private final Map<Long, Promotion> promotions = new HashMap<>();
        private long sequence = 1L;

        Promotion save(Promotion promotion) {
            setId(promotion, sequence++);
            promotions.put(promotion.id(), promotion);
            return promotion;
        }

        PromotionRepository proxy() {
            return (PromotionRepository) Proxy.newProxyInstance(
                    PromotionRepository.class.getClassLoader(),
                    new Class<?>[]{PromotionRepository.class},
                    (proxy, method, args) -> switch (method.getName()) {
                        case "save" -> save((Promotion) args[0]);
                        case "findById" -> Optional.ofNullable(promotions.get((Long) args[0]));
                        case "toString" -> "FakePromotionRepository";
                        default -> throw new UnsupportedOperationException(method.getName());
                    }
            );
        }
    }

    private static final class FakeRewardRepository {
        private final Map<Long, PromotionReward> rewards = new HashMap<>();
        private long sequence = 1L;

        PromotionReward save(PromotionReward reward) {
            setId(reward, sequence++);
            rewards.put(reward.id(), reward);
            return reward;
        }

        PromotionRewardRepository proxy() {
            return (PromotionRewardRepository) Proxy.newProxyInstance(
                    PromotionRewardRepository.class.getClassLoader(),
                    new Class<?>[]{PromotionRewardRepository.class},
                    (proxy, method, args) -> switch (method.getName()) {
                        case "save" -> save((PromotionReward) args[0]);
                        case "findById" -> Optional.ofNullable(rewards.get((Long) args[0]));
                        case "toString" -> "FakeRewardRepository";
                        default -> throw new UnsupportedOperationException(method.getName());
                    }
            );
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
