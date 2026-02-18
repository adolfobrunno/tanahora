package com.abba.tanahora.application.dto;

import java.time.OffsetDateTime;

public record DowngradeResult(
        boolean alreadyFree,
        boolean cancellationRequested,
        boolean cancellationConfirmed,
        OffsetDateTime premiumUntil
) {
}
