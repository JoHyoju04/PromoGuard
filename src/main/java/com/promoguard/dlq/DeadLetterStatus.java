package com.promoguard.dlq;

public enum DeadLetterStatus {
    PENDING,
    REPROCESSED,
    FAILED
}
