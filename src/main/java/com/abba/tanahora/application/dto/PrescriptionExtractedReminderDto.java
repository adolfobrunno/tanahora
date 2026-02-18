package com.abba.tanahora.application.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class PrescriptionExtractedReminderDto {

    @JsonProperty(required = true)
    private String medication;

    @JsonProperty(required = true)
    private String dosage;

    @JsonProperty(required = true)
    private String rrule;

    @JsonProperty(required = true)
    private String patientName;
}
