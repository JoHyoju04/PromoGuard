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

@Getter
@Entity
@Table(name = "promotion_rewards")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PromotionReward {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "promotion_id")
    private Promotion promotion;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private RewardType rewardType;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(nullable = false)
    private int totalQuantity;

    @Column(nullable = false)
    private int claimedQuantity = 0;

    @Column(nullable = false)
    private int perUserLimit;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RewardStatus status = RewardStatus.ACTIVE;

    @Builder
    private PromotionReward(
            Promotion promotion,
            RewardType rewardType,
            String name,
            int totalQuantity,
            int claimedQuantity,
            int perUserLimit,
            RewardStatus status
    ) {
        this.promotion = promotion;
        this.rewardType = rewardType;
        this.name = name;
        this.totalQuantity = totalQuantity;
        this.claimedQuantity = claimedQuantity;
        this.perUserLimit = perUserLimit;
        this.status = status == null ? RewardStatus.ACTIVE : status;
    }

    public Long id() {
        return id;
    }

    public boolean isActive() {
        return status == RewardStatus.ACTIVE;
    }
}
