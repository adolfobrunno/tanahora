package com.abba.tanahora.application.service;

import com.abba.tanahora.domain.model.PatientRef;
import com.abba.tanahora.domain.model.User;
import com.abba.tanahora.domain.repository.UserRepository;
import com.abba.tanahora.domain.service.PatientResolverService;
import com.abba.tanahora.domain.utils.Constants;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class PatientResolverServiceImpl implements PatientResolverService {

    private final UserRepository userRepository;

    @Override
    public PatientRef resolve(User user, String patientName, String lastPatientId, boolean createIfMissing) {
        if (user == null) {
            throw new IllegalArgumentException("user cannot be null");
        }

        if (Constants.NOT_INFORMED.equalsIgnoreCase(patientName)) {
            return null;
        }

        List<PatientRef> patients = user.getPatients();
        if (patients == null) {
            patients = new ArrayList<>();
            user.setPatients(patients);
        }

        if (patientName != null && !patientName.isBlank()) {
            String normalized = normalizeName(patientName);
            Optional<PatientRef> match = patients.stream()
                    .filter(patient -> normalizeName(patient.getName()).equals(normalized))
                    .findFirst();

            if (match.isPresent()) {
                return match.get();
            }

            if (!createIfMissing) {
                return null;
            }

            PatientRef created = new PatientRef();
            created.setName(patientName.trim());
            patients.add(created);
            userRepository.save(user);
            return created;
        }

        if (lastPatientId == null || lastPatientId.isBlank()) {
            return null;
        }

        return patients.stream()
                .filter(patient -> lastPatientId.equals(patient.getId()))
                .findFirst()
                .orElse(null);
    }

    private String normalizeName(String name) {
        if (name == null) {
            return "";
        }
        return name.trim().toLowerCase();
    }
}
