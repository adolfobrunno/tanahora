package com.abba.tanahora.domain.service;

import com.abba.tanahora.domain.model.Patient;
import com.abba.tanahora.domain.model.User;

import java.util.Optional;

public interface PatientResolverService {

    Optional<Patient> resolve(User user, String patientName, String lastPatientId, boolean createIfMissing);
}
