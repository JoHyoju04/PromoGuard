package com.promoguard.outbox;

public enum OutboxStatus {
    READY,
    PROCESSING,
    PROCESSED,
    FAILED
}
