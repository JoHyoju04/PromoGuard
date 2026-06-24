package com.promoguard.promotion;

import com.promoguard.reward.PromotionReward;
import com.promoguard.reward.RewardType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

@RestController
@RequestMapping("/api/promotions")
public class PromotionAdminController {

    private final PromotionService promotionService;

    public PromotionAdminController(PromotionService promotionService) {
        this.promotionService = promotionService;
    }

    @PostMapping
    public PromotionResponse createPromotion(@Valid @RequestBody CreatePromotionRequest request) {
        Promotion promotion = promotionService.createPromotion(request.name(), request.startsAt(), request.endsAt());
        return PromotionResponse.from(promotion);
    }

    @PostMapping("/{promotionId}/rewards")
    public RewardResponse createReward(@PathVariable Long promotionId, @Valid @RequestBody CreateRewardRequest request) {
        PromotionReward reward = promotionService.createReward(
                promotionId,
                request.rewardType(),
                request.name(),
                request.totalQuantity(),
                request.perUserLimit()
        );
        return RewardResponse.from(reward);
    }

    @GetMapping("/{promotionId}/rewards/{rewardId}")
    public RewardResponse getReward(@PathVariable Long promotionId, @PathVariable Long rewardId) {
        return RewardResponse.from(promotionService.getReward(promotionId, rewardId));
    }

    public record CreatePromotionRequest(
            @NotBlank String name,
            @NotNull Instant startsAt,
            @NotNull @Future Instant endsAt
    ) {
    }

    public record CreateRewardRequest(
            @NotNull RewardType rewardType,
            @NotBlank String name,
            @Min(1) int totalQuantity,
            @Min(1) int perUserLimit
    ) {
    }

    public record PromotionResponse(
            Long id,
            String name,
            PromotionStatus status,
            Instant startsAt,
            Instant endsAt
    ) {
        static PromotionResponse from(Promotion promotion) {
            return new PromotionResponse(
                    promotion.id(),
                    promotion.getName(),
                    promotion.getStatus(),
                    promotion.getStartsAt(),
                    promotion.getEndsAt()
            );
        }
    }

    public record RewardResponse(
            Long id,
            Long promotionId,
            RewardType rewardType,
            String name,
            int totalQuantity,
            int claimedQuantity,
            int perUserLimit
    ) {
        static RewardResponse from(PromotionReward reward) {
            return new RewardResponse(
                    reward.id(),
                    reward.getPromotion().id(),
                    reward.getRewardType(),
                    reward.getName(),
                    reward.getTotalQuantity(),
                    reward.getClaimedQuantity(),
                    reward.getPerUserLimit()
            );
        }
    }
}
