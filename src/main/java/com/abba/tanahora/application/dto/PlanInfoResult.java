package com.abba.tanahora.application.dto;

import com.abba.tanahora.domain.model.Plan;

import java.time.OffsetDateTime;

public record PlanInfoResult(
        Plan plan,
        OffsetDateTime premiumUntil,
        String subscriptionStatus
) {
}
