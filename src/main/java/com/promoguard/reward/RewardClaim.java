package com.promoguard.reward;

import com.promoguard.promotion.Promotion;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Getter
@Entity
@Table(name = "reward_claims")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RewardClaim {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "promotion_id")
    private Promotion promotion;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "reward_id")
    private PromotionReward reward;

    @Column(nullable = false, length = 80)
    private String userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private ClaimType claimType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ClaimStatus status = ClaimStatus.CLAIMED;

    @Column(nullable = false)
    private Instant claimedAt;

    @Builder
    private RewardClaim(
            Promotion promotion,
            PromotionReward reward,
            String userId,
            ClaimType claimType,
            ClaimStatus status,
            Instant claimedAt
    ) {
        this.promotion = promotion;
        this.reward = reward;
        this.userId = userId;
        this.claimType = claimType;
        this.status = status == null ? ClaimStatus.CLAIMED : status;
        this.claimedAt = claimedAt;
    }

    public Long id() {
        return id;
    }

    public Long getPromotionId() {
        return promotion.id();
    }

    public Long getRewardId() {
        return reward.id();
    }
}
