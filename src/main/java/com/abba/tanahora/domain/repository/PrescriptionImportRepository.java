package com.abba.tanahora.domain.repository;

import com.abba.tanahora.domain.model.PrescriptionImport;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

public interface PrescriptionImportRepository extends MongoRepository<PrescriptionImport, String> {

    Optional<PrescriptionImport> findByIdAndWhatsappId(String id, String whatsappId);

}
