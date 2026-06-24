package com.promoguard.lock;

import java.util.Optional;

public interface ClaimLockService {

    Optional<LockToken> tryLock(String key);

    void unlock(LockToken lockToken);

    record LockToken(String key, String token) {
    }
}
