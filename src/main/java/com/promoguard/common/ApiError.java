package com.promoguard.common;

import java.time.Instant;

public record ApiError(
        Instant timestamp,
        String code,
        String message
) {
    public static ApiError of(String code, String message) {
        return new ApiError(Instant.now(), code, message);
    }
}
