package com.promoguard.notification;

import com.promoguard.reward.RewardClaimedEvent;
import org.springframework.stereotype.Component;

@Component
public class FakeAlimtalkClient implements AlimtalkClient {

    @Override
    public void sendRewardClaimed(RewardClaimedEvent event) {
        if (event.userId().startsWith("fail-")) {
            throw new IllegalStateException("Simulated Alimtalk failure for " + event.userId());
        }
    }
}
