package com.promoguard.promotion;

import com.promoguard.common.DomainException;
import com.promoguard.reward.PromotionReward;
import com.promoguard.reward.PromotionRewardRepository;
import com.promoguard.reward.RewardType;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
public class PromotionService {

    private final PromotionRepository promotionRepository;
    private final PromotionRewardRepository rewardRepository;

    public PromotionService(PromotionRepository promotionRepository, PromotionRewardRepository rewardRepository) {
        this.promotionRepository = promotionRepository;
        this.rewardRepository = rewardRepository;
    }

    @Transactional
    public Promotion createPromotion(String name, Instant startsAt, Instant endsAt) {
        Promotion promotion = Promotion.builder()
                .name(name)
                .startsAt(startsAt)
                .endsAt(endsAt)
                .build();
        return promotionRepository.save(promotion);
    }

    @Transactional
    public PromotionReward createReward(Long promotionId, RewardType rewardType, String name, int totalQuantity, int perUserLimit) {
        Promotion promotion = promotionRepository.findById(promotionId)
                .orElseThrow(() -> new DomainException(HttpStatus.NOT_FOUND, "PROMOTION_NOT_FOUND", "Promotion not found"));

        PromotionReward reward = PromotionReward.builder()
                .promotion(promotion)
                .rewardType(rewardType)
                .name(name)
                .totalQuantity(totalQuantity)
                .perUserLimit(perUserLimit)
                .build();
        return rewardRepository.save(reward);
    }

    @Transactional(readOnly = true)
    public PromotionReward getReward(Long promotionId, Long rewardId) {
        PromotionReward reward = rewardRepository.findById(rewardId)
                .orElseThrow(() -> new DomainException(HttpStatus.NOT_FOUND, "REWARD_NOT_FOUND", "Reward not found"));
        if (!reward.getPromotion().id().equals(promotionId)) {
            throw new DomainException(HttpStatus.NOT_FOUND, "REWARD_NOT_FOUND", "Reward does not belong to promotion");
        }
        return reward;
    }
}
