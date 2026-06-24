package com.promoguard.reward;

import jakarta.validation.constraints.NotBlank;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/promotions/{promotionId}/rewards/{rewardId}")
public class RewardClaimController {

    private final RewardClaimService claimService;

    public RewardClaimController(RewardClaimService claimService) {
        this.claimService = claimService;
    }

    @PostMapping("/claim")
    public RewardClaimService.ClaimRewardResponse claim(
            @PathVariable Long promotionId,
            @PathVariable Long rewardId,
            @RequestHeader("X-User-Id") @NotBlank String userId
    ) {
        return claimService.claim(promotionId, rewardId, userId);
    }
}
