package com.abba.tanahora.domain.model;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

@Document(collection = "users")
@CompoundIndex(name = "whatsapp_idx", def = "{'whatsappId': 1}", unique = true)
@Data
public class User {

    @Id
    private String id;
    @Indexed
    private String whatsappId;
    private String name;
    private Plan plan = Plan.FREE;
    private OffsetDateTime proUntil;
    private OffsetDateTime proSince;
    private OffsetDateTime createdAt = OffsetDateTime.now();

    private List<PatientRef> patients = new ArrayList<>();

    public void enablePremium() {
        applyPremiumCycle(1);
    }

    public void applyPremiumCycle(int months) {
        int cycleMonths = Math.max(1, months);
        OffsetDateTime now = OffsetDateTime.now();
        this.plan = Plan.PREMIUM;
        if (this.proSince == null) {
            this.proSince = now;
        }
        OffsetDateTime baseUntil = this.proUntil != null && this.proUntil.isAfter(now) ? this.proUntil : now;
        this.proUntil = baseUntil.plusMonths(cycleMonths);
    }

    public boolean isPremium() {
        return plan == Plan.PREMIUM && proUntil != null && proUntil.isAfter(OffsetDateTime.now());
    }

    public void downgradeToFree() {
        this.plan = Plan.FREE;
        this.proSince = null;
        this.proUntil = null;
    }

}
