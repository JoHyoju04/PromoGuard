package com.promoguard.reward;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(
        name = "reward_user_counters",
        uniqueConstraints = @UniqueConstraint(name = "uk_reward_user_counters_reward_user", columnNames = {"reward_id", "user_id"})
)
public class RewardUserCounter {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long rewardId;

    @Column(nullable = false, length = 80)
    private String userId;

    @Column(nullable = false)
    private int claimCount;

    protected RewardUserCounter() {
    }
}
