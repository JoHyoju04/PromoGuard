package com.promoguard.notification;

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
@Table(name = "notification_deliveries")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class NotificationDelivery {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long rewardClaimId;

    @Column(nullable = false, length = 80)
    private String userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private NotificationChannel channel;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private NotificationStatus status;

    @Column(columnDefinition = "text")
    private String failureReason;

    private Instant sentAt;

    public static NotificationDelivery sent(Long rewardClaimId, String userId, Instant sentAt) {
        return builder()
                .rewardClaimId(rewardClaimId)
                .userId(userId)
                .channel(NotificationChannel.ALIMTALK)
                .status(NotificationStatus.SENT)
                .sentAt(sentAt)
                .build();
    }

    public static NotificationDelivery failed(Long rewardClaimId, String userId, String failureReason) {
        return builder()
                .rewardClaimId(rewardClaimId)
                .userId(userId)
                .channel(NotificationChannel.ALIMTALK)
                .status(NotificationStatus.FAILED)
                .failureReason(failureReason)
                .build();
    }

    @Builder
    private NotificationDelivery(
            Long rewardClaimId,
            String userId,
            NotificationChannel channel,
            NotificationStatus status,
            String failureReason,
            Instant sentAt
    ) {
        this.rewardClaimId = rewardClaimId;
        this.userId = userId;
        this.channel = channel;
        this.status = status;
        this.failureReason = failureReason;
        this.sentAt = sentAt;
    }
}
