package com.promoguard.notification;

import com.promoguard.reward.RewardClaimedEvent;

public interface AlimtalkClient {

    void sendRewardClaimed(RewardClaimedEvent event);
}
