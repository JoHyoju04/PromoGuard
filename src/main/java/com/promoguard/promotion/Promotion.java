package com.promoguard.promotion;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Getter
@Entity
@Table(name = "promotions")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Promotion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 120)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PromotionStatus status = PromotionStatus.ACTIVE;

    @Column(nullable = false)
    private Instant startsAt;

    @Column(nullable = false)
    private Instant endsAt;

    @Builder
    private Promotion(String name, PromotionStatus status, Instant startsAt, Instant endsAt) {
        this.name = name;
        this.status = status == null ? PromotionStatus.ACTIVE : status;
        this.startsAt = startsAt;
        this.endsAt = endsAt;
    }

    public Long id() {
        return id;
    }

    public boolean isOpenAt(Instant now) {
        return status == PromotionStatus.ACTIVE && !now.isBefore(startsAt) && now.isBefore(endsAt);
    }
}
