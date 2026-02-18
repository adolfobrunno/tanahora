package com.abba.tanahora.application.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class PrescriptionExtractionResultDto {

    @JsonProperty(required = true)
    private List<PrescriptionExtractedReminderDto> reminders = new ArrayList<>();
}
