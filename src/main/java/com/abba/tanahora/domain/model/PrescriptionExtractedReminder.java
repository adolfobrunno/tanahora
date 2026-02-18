package com.abba.tanahora.domain.model;

import lombok.Data;

@Data
public class PrescriptionExtractedReminder {

    private String medication;
    private String dosage;
    private String rrule;
    private String patientName;

}
