package com.abba.tanahora.application.dto;

import java.time.OffsetDateTime;

public record UpgradeCheckoutResult(
        String checkoutUrl,
        boolean reused,
        boolean alreadyPremium,
        OffsetDateTime checkoutExpiresAt
) {
}
